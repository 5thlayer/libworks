package io.github._5thlayer.libworks.plugin

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion

/**
 * The build every Module shares (ADR 0003), read from the Module's gradle.properties: mod_id,
 * mod_name, mod_description, mod_version, maven_group, archives_name, minecraft_version and
 * neoforge_version.
 */
class ModulePlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.apply('java-library')
        project.pluginManager.apply('maven-publish')
        project.pluginManager.apply('net.neoforged.moddev')

        def p = { String name -> project.property(name) as String }
        project.group = p('maven_group')
        project.version = p('mod_version')
        project.base.archivesName = p('archives_name')
        // Where Consumers resolve the Libraries from, and where the Module publishes (release-train).
        def mavenLocal = new File(project.repositories.mavenLocal().url.path)

        def libworks = project.extensions.create('libworks', LibworksExtension, project)

        project.java.withSourcesJar()
        project.java.toolchain.languageVersion = JavaLanguageVersion.of(25)
        project.tasks.withType(JavaCompile).configureEach { it.options.release = 25 }
        // The Minecraft version goes in the jar's name, not the Module's version: beltworks-26.1.2-0.3.0.jar.
        project.tasks.withType(Jar).configureEach { it.archiveVersion = "${p('minecraft_version')}-${p('mod_version')}" }

        configureNeoForge(project, p)
        configureTests(project)
        configureLicensing(project)
        configureResources(project, p)
        configurePublishing(project, p, mavenLocal)
        configureUpstreams(project, libworks)
        configureScripts(project, libworks)
    }

    private static void configureNeoForge(Project project, Closure<String> p) {
        def gameTestReport = project.layout.buildDirectory.file('reports/gametest.xml').get().asFile
        def modId = p('mod_id')
        project.neoForge {
            version = p('neoforge_version')
            runs {
                // The same player every launch, so a dev save keeps its inventory and position.
                client {
                    client()
                    programArguments.addAll '--username', 'Developer', '--uuid', '123456781234123412341234567890ab'
                    // -PquickPlay=<save> opens that save straight from launch (skillworks:quicklaunch).
                    programArguments.addAll project.providers.gradleProperty('quickPlay').map { ['--quickPlaySingleplayer', it] }.orElse([])
                }
                // Headless, the Module's tests only, in a directory the dev client never touches.
                gameTestServer {
                    type = 'gameTestServer'
                    gameDirectory = project.file('run/gametest')
                    programArguments.addAll '--tests', "${modId}:*", '--report', gameTestReport.absolutePath
                }
            }
            mods {
                "$modId" {
                    sourceSet project.sourceSets.main
                }
            }
        }

        // The report, not the exit code, decides, and names each test: a run that ran none fails.
        project.tasks.named('runGameTestServer') {
            ignoreExitValue = true
            doFirst {
                gameTestReport.delete()
                gameTestReport.parentFile.mkdirs()
            }
            doLast {
                if (!gameTestReport.exists()) throw new GradleException("The game test server wrote no report at $gameTestReport")
                def cases = new groovy.xml.XmlSlurper().parse(gameTestReport).'**'.findAll { it.name() == 'testcase' }
                if (cases.isEmpty()) throw new GradleException('The game test server ran no tests')
                cases.each { project.logger.lifecycle("${it.failure.size() ? 'FAILED' : 'passed'}  ${it.@name}") }
                def failed = cases.count { it.failure.size() }
                if (failed) throw new GradleException("$failed of ${cases.size()} game tests failed")
            }
        }
    }

    private static void configureTests(Project project) {
        project.dependencies {
            testImplementation platform('org.junit:junit-bom:5.10.2')
            testImplementation 'org.junit.jupiter:junit-jupiter'
            testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
        }
        project.tasks.named('test') { useJUnitPlatform() }
    }

    /** The jar carries LICENSE, every file under LICENSES/ and NOTICE if the repo has one (ADR 0003). */
    private static void configureLicensing(Project project) {
        def root = project.rootDir
        def licensing = { ->
            def names = ['LICENSE'] + (new File(root, 'NOTICE').isFile() ? ['NOTICE'] : [])
            def dir = new File(root, 'LICENSES')
            if (dir.isDirectory()) dir.eachFileRecurse(groovy.io.FileType.FILES) { names << root.toPath().relativize(it.toPath()).toString().replace('\\', '/') }
            names.sort()
        }
        project.tasks.named('jar', Jar) { jar ->
            jar.from(root) { include licensing() }
        }
        def check = project.tasks.register('checkJarLicensing') {
            def jarFile = project.tasks.named('jar', Jar).flatMap { it.archiveFile }
            inputs.file jarFile
            doLast {
                def names = new java.util.zip.ZipFile(jarFile.get().asFile).withCloseable { zip -> zip.entries()*.name as Set }
                def expected = licensing()
                def missing = expected.findAll { !names.contains(it) }
                if (missing) throw new GradleException("${jarFile.get().asFile.name} lacks its licensing: ${missing.join(', ')}")
            }
        }
        project.tasks.named('check') { dependsOn check }
    }

    /** neoforge.mods.toml names nothing of its own: it is filled from gradle.properties. */
    private static void configureResources(Project project, Closure<String> p) {
        def props = [
                mod_id: p('mod_id'), mod_name: p('mod_name'), mod_description: p('mod_description'), version: p('mod_version'),
                minecraft_version: p('minecraft_version'), neoforge_version: p('neoforge_version'),
        ]
        project.tasks.named('processResources') {
            inputs.properties props
            filesMatching('META-INF/neoforge.mods.toml') { expand props }
        }
    }

    /**
     * Maven local only. A version there never changes, and nothing publishes against a
     * -PsiblingBuilds checkout: a released Module never requires an unreleased Library.
     */
    private static void configurePublishing(Project project, Closure<String> p, File mavenLocal) {
        project.publishing {
            publications {
                mavenJava(MavenPublication) {
                    artifactId = project.base.archivesName.get()
                    from project.components.java
                }
            }
        }
        def siblings = project.providers.gradleProperty('siblingBuilds').present
        project.tasks.withType(PublishToMavenLocal).configureEach {
            def published = new File(mavenLocal, "${p('maven_group').replace('.', '/')}/${p('archives_name')}/${p('mod_version')}")
            def name = p('mod_name')
            def version = p('mod_version')
            doFirst {
                if (siblings) throw new GradleException(
                        "$name is built against sibling checkouts under -PsiblingBuilds, and nothing publishes under it")
                if (published.exists()) throw new GradleException(
                        "$name $version is already in $published, and a published version never changes: bump mod_version")
            }
        }
    }

    private static void configureUpstreams(Project project, LibworksExtension libworks) {
        // One line per upstream, "<repo> <tag>", the tag of the lowest version in range: what CI builds.
        project.tasks.register('libworksUpstreams') {
            group = 'help'
            description = 'Prints each upstream Library CI builds: repo and tag.'
            doLast {
                libworks.upstreams.each { println "${it.repo} ${it.tagPrefix}${it.lowerBound}" }
            }
        }

        // A sibling checkout outside the range still builds, and is refused at load: the composite
        // substitutes it whatever the range says. -P<name>Dir names one, ~/minecraft_mods/<name> by default.
        project.afterEvaluate {
            if (!project.providers.gradleProperty('siblingBuilds').present) return
            libworks.upstreams.each { upstream ->
                def path = project.providers.gradleProperty("${upstream.name}Dir").getOrElse("~/minecraft_mods/${upstream.name}")
                def dir = new File(path.replaceFirst('^~', System.getProperty('user.home'))).canonicalFile
                def properties = new File(dir, 'gradle.properties')
                if (!properties.isFile()) throw new GradleException(
                        "-PsiblingBuilds: ${upstream.name} has no gradle.properties in $dir; check it out there or name it with -P${upstream.name}Dir")
                def props = new Properties()
                properties.withInputStream { props.load(it) }
                def version = props.getProperty('mod_version')
                if (!version) throw new GradleException("-PsiblingBuilds: ${upstream.name}'s $properties names no mod_version")
                if (!upstream.contains(version)) throw new GradleException(
                        "-PsiblingBuilds: ${upstream.name} $version in $dir is outside ${upstream.range}, which the Module requires" +
                                " and which would refuse it at load. A minor of ${upstream.name} needs this range moved in this checkout too.")
            }
        }
    }

    /**
     * The scripts every Module shares ship in the plugin, are extracted to build/libworks/ and run
     * in the Module's directory: release.sh, upload.py and build-gametest-structures.py (ADR 0003).
     * -PreleaseVersion names the version; -Pupload uploads after a release, -PdryRun and -Psite
     * pass to the upload.
     */
    private static void configureScripts(Project project, LibworksExtension libworks) {
        def dir = project.layout.buildDirectory.dir('libworks')
        def extract = { String name ->
            def file = dir.get().file(name).asFile
            file.parentFile.mkdirs()
            ModulePlugin.getResourceAsStream("scripts/$name").withCloseable { file.bytes = it.bytes }
            file.setExecutable(true)
            file.absolutePath
        }
        def gradleProperty = { String name -> project.providers.gradleProperty(name) }
        def version = { ->
            def v = gradleProperty('releaseVersion').orNull
            if (!v) throw new GradleException('Name the version with -PreleaseVersion=<major.minor.patch>')
            v
        }
        def siblings = gradleProperty('siblingBuilds').present

        project.tasks.register('buildGametestStructures', Exec) {
            group = 'libworks'
            description = 'Writes the stone platform the game tests stand on.'
            workingDir project.projectDir
            executable 'python3'
            argumentProviders.add({ [extract('build-gametest-structures.py')] } as org.gradle.process.CommandLineArgumentProvider)
        }

        project.tasks.register('upload', Exec) {
            group = 'libworks'
            description = 'Uploads a released version to Modrinth and CurseForge.'
            workingDir project.projectDir
            executable 'python3'
            argumentProviders.add({
                def args = [extract('upload.py')]
                if (gradleProperty('dryRun').present) args << '--dry-run'
                if (gradleProperty('site').present) args += ['--site', gradleProperty('site').get()]
                args << version()
            } as org.gradle.process.CommandLineArgumentProvider)
        }

        project.tasks.register('release', Exec) {
            group = 'libworks'
            description = 'Releases a version to the local maven repository and tags it.'
            workingDir project.projectDir
            executable 'bash'
            doFirst {
                if (siblings) throw new GradleException(
                        "${project.property('mod_name')} is built against sibling checkouts under -PsiblingBuilds, and nothing releases under it")
                environment 'LIBWORKS_TAG_PREFIX', libworks.tagPrefix
            }
            argumentProviders.add({
                def args = [extract('release.sh')]
                if (gradleProperty('upload').present) args << '--upload'
                args << version()
            } as org.gradle.process.CommandLineArgumentProvider)
        }
    }
}
