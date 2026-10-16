// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.plugin

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
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

        def property = { String name -> project.property(name) as String }
        project.group = property('maven_group')
        project.version = property('mod_version')
        project.base.archivesName = property('archives_name')
        // Where Consumers resolve the Modules from, and where the Module publishes (release-train).
        // Every io.github.5thlayer artifact resolves from ~/.m2 and no other repository, and ~/.m2 serves
        // nothing else (libworks#18). By group, so an upstream's own upstreams and dev-only Modules pass too.
        def m2 = null
        project.repositories.exclusiveContent {
            it.forRepository { m2 = project.repositories.mavenLocal { it.name = 'libworksM2' } }
            it.filter { it.includeGroup 'io.github.5thlayer' }
        }
        def mavenLocal = new File(m2.url.path)
        // A Module's own exclusive ~/.m2 shuts this one out and is shut out by it, so Gradle finds the
        // Modules nowhere and says only that; a filtered one is refused too, so there is one form.
        project.afterEvaluate {
            def modId = property('mod_id')
            if (project.repositories.withType(MavenArtifactRepository).any { it != m2 && it.url == m2.url }) throw new GradleException(
                    "$modId declares its own ~/.m2 repository, which the plugin declares itself (libworks#18): delete its `mavenLocal { … }` or `exclusiveContent { forRepository { mavenLocal() } … }` block from repositories")
        }

        // Optional upstreams: on the dev runs' classpath, never in the published jar, pom or module metadata.
        def localRuntime = project.configurations.maybeCreate('localRuntime')
        project.configurations.named('runtimeClasspath') { it.extendsFrom localRuntime }
        // What only the Module's own runs load, and the jar leaves out: its dev and game-test packs (ADR 0004),
        // and its game-test code and test-only resources (ADR 0011).
        def gametest = project.sourceSets.maybeCreate('gametest')
        // libworks-gametest (ADR 0008): compiled against by the gametest source set alone, so main never names
        // it (ADR 0011), and on the dev runs, never published. A run on a source set of its own, without
        // localRuntime, extends it too.
        def libworksGametest = project.configurations.create('libworksGametest') {
            it.canBeConsumed = false
            it.canBeResolved = false
        }
        project.configurations.named(gametest.compileOnlyConfigurationName) { it.extendsFrom libworksGametest }
        localRuntime.extendsFrom libworksGametest
        def libworks = project.extensions.create('libworks', LibworksExtension, project)

        project.java.withSourcesJar()
        project.java.toolchain.languageVersion = JavaLanguageVersion.of(25)
        project.tasks.withType(JavaCompile).configureEach { it.options.release = 25 }
        // The Minecraft version goes in the jar's name, not the Module's version: beltworks-26.1.2-0.3.0.jar.
        project.tasks.withType(Jar).configureEach { it.archiveVersion = "${property('minecraft_version')}-${property('mod_version')}" }

        configureNeoForge(project, property, gametest)
        configureTests(project)
        configureLicensing(project)
        configurePlaysAlone(project, property)
        configureResources(project, property, libworks)
        configurePublishing(project, property, mavenLocal)
        configureUpstreams(project, libworks)
        configureScripts(project, libworks)
    }

    private static void configureNeoForge(Project project, Closure<String> property, SourceSet gametest) {
        def gameTestReport = project.layout.buildDirectory.file('reports/gametest.xml').get().asFile
        def modId = property('mod_id')
        // The gametest source set's code builds on the Module's own and the Module's dependencies.
        project.configurations.named(gametest.implementationConfigurationName) { it.extendsFrom project.configurations.implementation }
        project.dependencies.add(gametest.implementationConfigurationName, project.sourceSets.main.output)
        project.neoForge {
            version = property('neoforge_version')
            runs {
                // Which run this is, by its name: the template's DevPacks switches on dev_pack/ in client and
                // gametest_pack/ in gameTestServer (ADR 0004).
                configureEach { run -> run.systemProperty 'libworks.run', run.name }
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
            addModdingDependenciesTo gametest
            mods {
                "$modId" {
                    sourceSet project.sourceSets.main
                    sourceSet gametest
                }
            }
        }

        // ModDevGradle refuses a source set listed twice only once a run starts; this names the line to delete.
        project.afterEvaluate {
            if (project.neoForge.mods.getByName(modId).modSourceSets.get().count { it.name == gametest.name } > 1) throw new GradleException(
                    "$modId adds the gametest source set to its mod, which the plugin does itself (libworks ADR 0004): delete `sourceSet sourceSets.gametest` from its neoForge.mods block")
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

        // The game test server's whole command line, one argument a line, to start it again without Gradle
        // from run/gametest (skillworks' gametest-loop): ModDevGradle's args files leave out its classpath,
        // which it sets only as the run starts. writeGameTestServerCommand writes it with nothing run, and
        // every runGameTestServer again, so it never lags the args files beside it.
        def serverCommand = project.layout.buildDirectory.file('moddev/gameTestServerRunCommand.txt').get().asFile
        def gameTestRun = project.tasks.named('runGameTestServer', JavaExec)
        def writeServerCommand = {
            def run = gameTestRun.get()
            def provided = { List providers -> providers.collectMany { it.asArguments() as List } }
            // In the order Gradle launches it: the argument providers' JVM arguments, the run's own, then
            // Gradle's, such as file.encoding; the classpath is the one ModDevGradle sets as the run starts.
            def jvm = provided(run.jvmArgumentProviders) + run.jvmArgs
            def gradles = new ArrayList(run.allJvmArgs)
            jvm.each { gradles.remove((Object) it) }
            def args = [run.javaLauncher.get().executablePath.asFile.absolutePath] + jvm + gradles
            args += ['-cp', run.classpath.plus(run.classpathProvider).asPath, run.mainClass.get()] + run.args
            args += provided(run.argumentProviders)
            def multiline = args.find { it.contains('\n') }
            if (multiline) throw new GradleException("The game test server's argument \"$multiline\" spans lines, which $serverCommand cannot hold")
            serverCommand.parentFile.mkdirs()
            serverCommand.text = args.join('\n') + '\n'
        }
        gameTestRun.configure { it.doFirst { writeServerCommand() } }
        project.tasks.register('writeGameTestServerCommand') { task ->
            task.description = "Writes the game test server's command line, classpath included, to $serverCommand."
            task.dependsOn { gameTestRun.get().taskDependencies }
            task.outputs.file serverCommand
            // Cheap, and its inputs are the run's whole configuration: always rewritten.
            task.outputs.upToDateWhen { false }
            task.doLast { writeServerCommand() }
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

    /**
     * The Module plays alone on Groundworks (FactoryWorks ADR-0127): nothing it ships names another Module's
     * id or namespace, or requires another Module (ADR 0005). The resources are read as processed, so
     * generated ones and the expanded mods.toml count.
     */
    private static void configurePlaysAlone(Project project, Closure<String> property) {
        def check = project.tasks.register('checkPlaysAlone') {
            group = 'verification'
            description = 'Fails on what the Module ships that names another Module or requires one.'
            def processed = project.tasks.named('processResources').map { it.destinationDir }
            // Read when the task runs, so a source folder a Module adds later counts.
            def code = project.sourceSets.main.java.sourceDirectories
            def modId = property('mod_id')
            // Files, not a dir: a Module with no resources leaves no processed folder.
            inputs.files processed
            inputs.files code
            inputs.property 'modId', modId
            doLast {
                def rules = new PlaysAlone(modId)
                def projectDir = project.projectDir.toPath()
                def underRoot = { File root, List<String> found -> found.collect { "${PlaysAlone.relative(projectDir, root.toPath())}/$it" } }
                def found = underRoot(processed.get(), rules.resources(processed.get().toPath()))
                code.files.each { found += underRoot(it, rules.code(it.toPath())) }
                if (found) throw new GradleException(
                        "$modId ships what names another Module (FactoryWorks ADR-0127, libworks ADR 0005), whose namespaces are ${rules.forbidden.join(', ')}:\n  ${found.sort().join('\n  ')}")
            }
        }
        project.tasks.named('check') { dependsOn check }
    }

    /** neoforge.mods.toml names nothing of its own: it is filled from gradle.properties. */
    /** The mods.toml also names each upstream's range as {@code ${<name>_range}}, read once the libworks block has run. */
    private static void configureResources(Project project, Closure<String> property, LibworksExtension libworks) {
        def props = [
                mod_id: property('mod_id'), mod_name: property('mod_name'), mod_description: property('mod_description'), version: property('mod_version'),
                minecraft_version: property('minecraft_version'), neoforge_version: property('neoforge_version'),
        ]
        project.tasks.named('processResources') {
            def ranges = {
                def r = libworks.upstreams.collectEntries { ["${it.name}_range".toString(), it.range] }
                if (libworks.runtimeRange) r.libworks_runtime_range = libworks.runtimeRange
                r
            }
            inputs.properties props
            inputs.property 'upstreamRanges', project.provider { ranges() }
            filesMatching('META-INF/neoforge.mods.toml') { expand props + ranges() }
        }
    }

    /**
     * Maven local only. A version there never changes, and nothing publishes against a
     * -PsiblingBuilds checkout: a released Module never requires an unreleased Module.
     */
    private static void configurePublishing(Project project, Closure<String> property, File mavenLocal) {
        project.publishing {
            publications {
                mavenJava(MavenPublication) {
                    artifactId = project.base.archivesName.get()
                    from project.components.java
                }
            }
        }
        def siblings = siblingBuilds(project)
        project.tasks.withType(PublishToMavenLocal).configureEach {
            def published = new File(mavenLocal, "${property('maven_group').replace('.', '/')}/${property('archives_name')}/${property('mod_version')}")
            def name = property('mod_name')
            def version = property('mod_version')
            doFirst {
                if (siblings) refuseUnderSiblingBuilds(name, 'publishes')
                if (published.exists()) throw new GradleException(
                        "$name $version is already in $published, and a published version never changes: bump mod_version")
            }
        }
    }

    /** Whether the build substitutes sibling checkouts for the upstreams, under -PsiblingBuilds. */
    private static boolean siblingBuilds(Project project) {
        project.providers.gradleProperty('siblingBuilds').present
    }

    /** Nothing leaves a -PsiblingBuilds build: a released Module never requires an unreleased Module. */
    private static void refuseUnderSiblingBuilds(Object modName, String what) {
        throw new GradleException("$modName is built against sibling checkouts under -PsiblingBuilds, and nothing $what under it")
    }

    private static void configureUpstreams(Project project, LibworksExtension libworks) {
        // One line per upstream, "<repo> <tag>", the tag of the lowest version in range: what CI builds.
        // The runtime, when set, adds "5thlayer/libworks runtime-v<version> :runtime": a third field
        // names the one project CI publishes from that checkout, which it does not recurse into.
        project.tasks.register('libworksUpstreams') {
            group = 'help'
            description = 'Prints each upstream Module CI builds: repo and tag.'
            doLast {
                libworks.upstreams.each { println "${it.repo} ${it.tagPrefix}${it.lowerBound}" }
                if (libworks.runtime) println "5thlayer/libworks runtime-v${libworks.runtime} :runtime"
            }
        }

        // A sibling checkout outside the range still builds, and is refused at load: the composite
        // substitutes it whatever the range says. -P<name>Dir names one, ~/minecraft_mods/<name> by default.
        project.afterEvaluate {
            if (!siblingBuilds(project)) return
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
        def siblings = siblingBuilds(project)
        def name = project.property('mod_name')

        project.tasks.register('buildGametestStructures', Exec) {
            group = 'libworks'
            description = 'Writes the stone platform the game tests stand on, refusing to overwrite a Module\'s own.'
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
                if (siblings) refuseUnderSiblingBuilds(name, 'releases')
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
