// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.plugin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The plugin applied to a fixture Module, run through Gradle TestKit. */
class ModulePluginTest {
    @TempDir
    Path module;

    @TempDir
    Path elsewhere;

    @BeforeEach
    void writeFixtureModule() throws IOException {
        Files.writeString(module.resolve("settings.gradle"), """
                pluginManagement {
                    repositories {
                        maven { url = 'https://maven.neoforged.net/releases' }
                        gradlePluginPortal()
                    }
                }
                rootProject.name = 'beltworks'
                """);
        Files.writeString(module.resolve("gradle.properties"), """
                mod_id = beltworks
                mod_name = Beltworks
                mod_description = Belts.
                mod_version = 0.3.0
                maven_group = io.github.5thlayer
                archives_name = beltworks
                minecraft_version = 26.1.2
                neoforge_version = 26.1.2.77
                """);
    }

    private void buildScript(String libworksBlock) throws IOException {
        Files.writeString(module.resolve("build.gradle"), """
                plugins { id 'io.github.5thlayer.libworks.module' }
                libworks {
                %s
                }
                // What the Module declares, read without resolving anything.
                tasks.register('extendsLocalRuntime') {
                    doLast { println configurations.runtimeClasspath.extendsFrom*.name.contains('localRuntime') }
                }
                tasks.register('gametestReaches') {
                    doLast { println(['compileOnly', 'gametestCompileOnly', 'localRuntime'].collect { configurations[it].extendsFrom*.name.contains('libworksGametest') }.join(' ')) }
                }
                tasks.register('declared') {
                    doLast {
                        ['implementation', 'jarJar', 'libworksGametest', 'localRuntime'].each { c ->
                            configurations[c].dependencies.each { d ->
                                println "$c ${d.group}:${d.name} strictly=${d.versionConstraint.strictVersion} prefer=${d.versionConstraint.preferredVersion}"
                            }
                        }
                    }
                }
                """.formatted(libworksBlock));
    }

    private void appendBuildScript(String more) throws IOException {
        Files.writeString(module.resolve("build.gradle"), Files.readString(module.resolve("build.gradle")) + more);
    }

    private GradleRunner runner(String... args) {
        List<String> all = new ArrayList<>(List.of(args));
        all.add("--stacktrace");
        return GradleRunner.create().withProjectDir(module.toFile()).withPluginClasspath().withArguments(all);
    }

    private BuildResult build(String... args) {
        return runner(args).build();
    }

    private BuildResult fail(String... args) {
        return runner(args).buildAndFail();
    }

    @Test
    void listsEachUpstreamForCi() throws IOException {
        buildScript("""
                upstream('groundworks') { range = '[0.5.4,0.6)' }
                upstream('craftworks') { range = '[0.2.0,0.3)'; tagPrefix = 'v' }
                """);
        String out = build("-q", "libworksUpstreams").getOutput();
        assertEquals("""
                5thlayer/groundworks groundworks-v0.5.4
                5thlayer/craftworks v0.2.0
                """, out.replace("\r", ""));
    }

    @Test
    void listsTheRuntimeForCiAsTheLibworksRuntimeProject() throws IOException {
        buildScript("""
                upstream('groundworks') { range = '[0.5.4,0.6)' }
                runtime = '0.1.0'
                """);
        assertEquals("""
                5thlayer/groundworks groundworks-v0.5.4
                5thlayer/libworks runtime-v0.1.0 :runtime
                """, build("-q", "libworksUpstreams").getOutput().replace("\r", ""));
    }

    @Test
    void requiresEachUpstreamStrictlyInItsRangeAndPrefersTheLowerBound() throws IOException {
        buildScript("upstream('groundworks') { range = '[0.5.4,0.6)' }");
        assertTrue(build("-q", "declared").getOutput().contains(
                "implementation io.github.5thlayer:groundworks strictly=[0.5.4,0.6) prefer=0.5.4"));
    }

    @Test
    void addsTheGametestSourceSetToTheMod() throws IOException {
        buildScript("");
        appendBuildScript("""
                tasks.register('modSourceSets') {
                    doLast { println neoForge.mods.beltworks.modSourceSets.get()*.name }
                }
                """);
        assertEquals("[main, gametest]\n", build("-q", "modSourceSets").getOutput());
    }

    @Test
    void setsLibworksRunToEachRunsName() throws IOException {
        buildScript("");
        appendBuildScript("""
                neoForge { runs { server { server() } } }
                tasks.register('runNames') {
                    doLast { neoForge.runs.each { println "${it.name} ${it.systemProperties.get()['libworks.run']}" } }
                }
                """);
        assertEquals("""
                client client
                gameTestServer gameTestServer
                server server
                """, build("-q", "runNames").getOutput().replace("\r", ""));
    }

    @Test
    void writesTheGameTestServersWholeCommandLine() throws IOException {
        buildScript("");
        // The command line alone: what the run needs built, Minecraft's assets among it, is ModDevGradle's.
        appendBuildScript("tasks.named('writeGameTestServerCommand') { dependsOn.clear() }\n");
        build("writeGameTestServerCommand");
        Path moddev = module.toRealPath().resolve("build/moddev");
        List<String> command = Files.readAllLines(moddev.resolve("gameTestServerRunCommand.txt"));
        assertTrue(command.getFirst().endsWith("/bin/java"), command.getFirst());
        assertTrue(command.stream().anyMatch(a -> a.startsWith("-Dfml.modFolders=beltworks%%")), command.toString());
        assertEquals("@" + moddev.resolve("gameTestServerRunVmArgs.txt"), command.get(2), command.toString());
        String classpath = command.get(command.indexOf("-cp") + 1);
        assertTrue(classpath.contains(module.toRealPath().resolve("build/classes/java/main").toString()), classpath);
        assertEquals("net.neoforged.devlaunch.Main", command.get(command.indexOf("-cp") + 2));
        assertEquals("@" + moddev.resolve("gameTestServerRunProgramArgs.txt"), command.getLast());
    }

    @Test
    void compilesGametestCodeAgainstTheModuleAndNeoForge() throws IOException {
        buildScript("");
        Path main = Files.createDirectories(module.resolve("src/main/java/belt"));
        Files.writeString(main.resolve("Belt.java"), "package belt; public final class Belt { public static final String ID = \"beltworks\"; }\n");
        Path gametest = Files.createDirectories(module.resolve("src/gametest/java/belt"));
        Files.writeString(gametest.resolve("DevPacks.java"), """
                package belt;
                final class DevPacks {
                    static net.minecraft.resources.Identifier id() { return net.minecraft.resources.Identifier.fromNamespaceAndPath(Belt.ID, "dev_pack"); }
                }
                """);
        build("compileGametestJava");
        assertTrue(Files.isRegularFile(module.resolve("build/classes/java/gametest/belt/DevPacks.class")));
    }

    /** A stand-in libworks-gametest: a jar holding {@code testkit.Check}, on {@code libworksGametest}. */
    private void addStandInGametestJar() throws IOException {
        Path source = Files.createDirectories(elsewhere.resolve("testkit")).resolve("Check.java");
        Files.writeString(source, "package testkit; public final class Check { public static void run() {} }\n");
        Path classes = Files.createDirectories(elsewhere.resolve("classes"));
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", classes.toString(), source.toString()));
        Path jar = elsewhere.resolve("libworks-gametest.jar");
        try (var out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("testkit/Check.class"));
            out.write(Files.readAllBytes(classes.resolve("testkit/Check.class")));
            out.closeEntry();
        }
        appendBuildScript("dependencies { libworksGametest files('%s') }\n".formatted(jar.toString().replace("\\", "/")));
    }

    @Test
    void compilesOnlyTheGametestSourceSetAgainstLibworksGametest() throws IOException {
        buildScript("");
        addStandInGametestJar();
        write("src/gametest/java/belt/Tests.java", "package belt; final class Tests { static void run() { testkit.Check.run(); } }\n");
        build("compileGametestJava");
        assertTrue(Files.isRegularFile(module.resolve("build/classes/java/gametest/belt/Tests.class")));
        // Game-test code never ships (ADR 0011): main that names it does not compile.
        write("src/main/java/belt/Belt.java", "package belt; public final class Belt { static void run() { testkit.Check.run(); } }\n");
        String out = fail("compileJava").getOutput();
        assertTrue(out.contains("package testkit does not exist"), out);
    }

    @Test
    void leavesTheGametestSourceSetOutOfTheJar() throws IOException {
        buildScript("");
        Path pack = Files.createDirectories(module.resolve("src/gametest/resources/gametest_pack"));
        Files.writeString(pack.resolve("pack.mcmeta"), "{}\n");
        Files.writeString(module.resolve("LICENSE"), "MIT\n");
        build("jar");
        Path jar = module.resolve("build/libs/beltworks-26.1.2-0.3.0.jar");
        try (var zip = new java.util.zip.ZipFile(jar.toFile())) {
            assertTrue(zip.stream().noneMatch(e -> e.getName().startsWith("gametest_pack")), jar.toString());
        }
    }

    @Test
    void refusesAModuleThatAddsTheGametestSourceSetToItsModItself() throws IOException {
        buildScript("");
        appendBuildScript("""
                sourceSets { gametest }
                neoForge { mods { beltworks { sourceSet sourceSets.gametest } } }
                """);
        String out = fail("help").getOutput();
        assertTrue(out.contains("beltworks adds the gametest source set to its mod, which the plugin does itself (libworks ADR 0004)"), out);
    }

    private String processedModsToml() throws IOException {
        build("processResources");
        return Files.readString(module.resolve("build/resources/main/META-INF/neoforge.mods.toml"));
    }

    @Test
    void expandsEachUpstreamsRangeInTheModsToml() throws IOException {
        Path toml = Files.createDirectories(module.resolve("src/main/resources/META-INF")).resolve("neoforge.mods.toml");
        Files.writeString(toml, "versionRange = \"${groundworks_range}\"\n");
        buildScript("upstream('groundworks') { range = '[0.5.4,0.6)' }");
        assertEquals("versionRange = \"[0.5.4,0.6)\"\n", processedModsToml());
        buildScript("upstream('groundworks') { range = '[0.5.5,0.6)' }");
        assertEquals("versionRange = \"[0.5.5,0.6)\"\n", processedModsToml());
    }

    @Test
    void expandsTheRuntimesRangeInTheModsToml() throws IOException {
        Path toml = Files.createDirectories(module.resolve("src/main/resources/META-INF")).resolve("neoforge.mods.toml");
        Files.writeString(toml, "versionRange = \"${libworks_runtime_range}\"\n");
        buildScript("runtime = '0.1.0'");
        assertEquals("versionRange = \"[0.1.0,0.2)\"\n", processedModsToml());
        buildScript("runtime = '0.1.3'");
        assertEquals("versionRange = \"[0.1.3,0.2)\"\n", processedModsToml());
    }

    /** A jar and its pom for group:name:1.0 in the Maven repository at repo. */
    private static void publish(Path repo, String group, String name) throws IOException {
        Path dir = Files.createDirectories(repo.resolve(group.replace('.', '/') + "/" + name + "/1.0"));
        Files.writeString(dir.resolve(name + "-1.0.pom"), """
                <project><modelVersion>4.0.0</modelVersion><groupId>%s</groupId><artifactId>%s</artifactId><version>1.0</version></project>
                """.formatted(group, name));
        Files.write(dir.resolve(name + "-1.0.jar"), new byte[0]);
    }

    /**
     * Resolves each coordinate on its own, with ~/.m2 at elsewhere/m2 and the Module's own ordinary repository
     * at elsewhere/other, unfiltered as cursemaven is: prints the repository each came from, or none.
     */
    private String resolvedFrom(String... coordinates) throws IOException {
        Path m2 = elsewhere.resolve("m2");
        Path other = elsewhere.resolve("other");
        publish(m2, "io.github.5thlayer", "groundworks");
        publish(m2, "org.example", "local-only");
        publish(other, "io.github.5thlayer", "wireworks");
        publish(other, "org.example", "elsewhere");
        buildScript("");
        appendBuildScript("""
                repositories { maven { url = '%s' } }
                tasks.register('resolvedFrom') {
                    doLast {
                        %s.each { c ->
                            def files = configurations.detachedConfiguration(dependencies.create(c)).incoming.artifactView { lenient = true }.files.files
                            def from = 'none'
                            if (files) from = files.first().path.contains('/m2/') ? 'm2' : 'other'
                            println "$c $from"
                        }
                    }
                }
                """.formatted(other.toUri(), List.of(coordinates).stream().map(c -> "'" + c + "'").toList()));
        return build("-q", "-Dmaven.repo.local=" + m2, "resolvedFrom").getOutput().replace("\r", "");
    }

    @Test
    void resolvesThe5thlayerGroupFromMavenLocalAloneAndNothingElseFromIt() throws IOException {
        assertEquals("""
                io.github.5thlayer:groundworks:1.0 m2
                io.github.5thlayer:wireworks:1.0 none
                org.example:local-only:1.0 none
                org.example:elsewhere:1.0 other
                """, resolvedFrom("io.github.5thlayer:groundworks:1.0", "io.github.5thlayer:wireworks:1.0",
                "org.example:local-only:1.0", "org.example:elsewhere:1.0"));
    }

    private void refusesAModulesOwnMavenLocal(String block) throws IOException {
        buildScript("");
        appendBuildScript("repositories { " + block + " }\n");
        String out = fail("help").getOutput();
        assertTrue(out.contains("beltworks declares its own ~/.m2 repository, which the plugin declares itself (libworks#18)"), out);
    }

    @Test
    void refusesAModulesOwnFilteredMavenLocal() throws IOException {
        refusesAModulesOwnMavenLocal("mavenLocal { content { includeModule 'io.github.5thlayer', 'groundworks' } }");
    }

    @Test
    void refusesAModulesOwnExclusiveMavenLocal() throws IOException {
        refusesAModulesOwnMavenLocal("exclusiveContent { forRepository { mavenLocal() }; filter { includeModule 'io.github.5thlayer', 'groundworks' } }");
    }

    @Test
    void putsAnOptionalUpstreamOnLocalRuntimeOnly() throws IOException {
        buildScript("upstream('groundworks') { range = '[0.5.4,0.6)'; optional = true }");
        String out = build("-q", "declared").getOutput();
        assertTrue(out.contains("localRuntime io.github.5thlayer:groundworks strictly=[0.5.4,0.6) prefer=0.5.4"), out);
        assertFalse(out.contains("implementation io.github.5thlayer:groundworks"), out);
        assertEquals("true\n", build("-q", "extendsLocalRuntime").getOutput());
        assertEquals("5thlayer/groundworks groundworks-v0.5.4\n", build("-q", "libworksUpstreams").getOutput());
    }

    @Test
    void keepsAModulesOwnLocalRuntime() throws IOException {
        buildScript("upstream('groundworks') { range = '[0.5.4,0.6)'; optional = true }\n}\nconfigurations { localRuntime }\nlibworks {");
        assertTrue(build("-q", "declared").getOutput().contains("localRuntime io.github.5thlayer:groundworks"));
    }

    @Test
    void refusesASiblingOfAnOptionalUpstreamOutsideTheRange() throws IOException {
        buildScript("upstream('groundworks') { range = '[0.5.4,0.6)'; optional = true }");
        Path checkout = siblingAt("0.6.0");
        assertTrue(fail("-PsiblingBuilds", "-PgroundworksDir=" + checkout, "help").getOutput().contains("outside [0.5.4,0.6)"));
    }

    @Test
    void refusesARangeWithoutAnInclusiveLowerBound() throws IOException {
        buildScript("upstream('groundworks') { range = '(0.5.4,0.6)' }");
        String out = fail("help").getOutput();
        assertTrue(out.contains("upstream 'groundworks' range '(0.5.4,0.6)' is not a range with an inclusive lower bound"), out);
    }

    @Test
    void refusesARuntimeThatIsNotAVersion() throws IOException {
        buildScript("runtime = 'latest'");
        String out = fail("help").getOutput();
        assertTrue(out.contains("'latest' is not a version"), out);
    }

    @Test
    void nestsTheRuntimeInItsRange() throws IOException {
        buildScript("runtime = '0.1.2'");
        String out = build("-q", "declared").getOutput();
        assertTrue(out.contains("implementation io.github.5thlayer:libworks-runtime strictly=[0.1.2,0.2) prefer=0.1.2"), out);
        assertTrue(out.contains("jarJar io.github.5thlayer:libworks-runtime strictly=[0.1.2,0.2) prefer=0.1.2"), out);
        assertFalse(out.contains("libworks-gametest"), out);
    }

    @Test
    void addsTheGametestJarToDevRunsFromTheFirstRuntimeWithIt() throws IOException {
        buildScript("runtime = '0.1.8'");
        String out = build("-q", "declared").getOutput();
        assertTrue(out.contains("libworksGametest io.github.5thlayer:libworks-gametest strictly=[0.1.8,0.2) prefer=0.1.8"), out);
        assertFalse(out.contains("jarJar io.github.5thlayer:libworks-gametest"), out);
        assertEquals("false true true\n", build("-q", "gametestReaches").getOutput());
    }

    @Test
    void tagsTheModuleByItsArtifactUnlessTold() throws IOException {
        Files.writeString(module.resolve("build.gradle"), """
                plugins { id 'io.github.5thlayer.libworks.module' }
                tasks.register('prefix') { doLast { println libworks.tagPrefix } }
                """);
        assertEquals("beltworks-v\n", build("-q", "prefix").getOutput());
        Files.writeString(module.resolve("build.gradle"), """
                plugins { id 'io.github.5thlayer.libworks.module' }
                libworks { tagPrefix = 'v' }
                tasks.register('prefix') { doLast { println libworks.tagPrefix } }
                """);
        assertEquals("v\n", build("-q", "prefix").getOutput());
    }

    private void write(String path, String text) throws IOException {
        Path file = module.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    @Test
    void checkRunsCheckPlaysAlone() throws IOException {
        buildScript("");
        appendBuildScript("""
                tasks.register('checkDependsOn') {
                    doLast { println tasks.check.taskDependencies.getDependencies(tasks.check)*.name.contains('checkPlaysAlone') }
                }
                """);
        assertEquals("true\n", build("-q", "checkDependsOn").getOutput());
    }

    @Test
    void checkPlaysAloneListsWhatNamesAnotherModuleInTheProcessedResourcesAndTheCode() throws IOException {
        buildScript("upstream('groundworks') { range = '[0.5.4,0.6)' }");
        write("src/main/java/belt/Pipes.java", "package belt; class Pipes { String ns = \"pipeworks\"; String id = \"pipeworks:pipe\"; String own = \"beltworks:belt\"; }\n");
        write("src/main/resources/data/gearworks/recipe/x.json", "{}\n");
        write("src/main/resources/META-INF/neoforge.mods.toml", """
                [[dependencies.beltworks]]
                modId = "craftworks"
                versionRange = "${groundworks_range}"
                """);
        // A generated resource ships too, so it is read where processResources puts it.
        write("build/generated/pack/data/beltworks/recipe/y.json", "{\"a\":\"voidworks:void\"}\n");
        appendBuildScript("sourceSets.main.resources.srcDir('build/generated/pack')\n");
        String out = fail("checkPlaysAlone").getOutput();
        assertTrue(out.contains("""
                  build/resources/main/META-INF/neoforge.mods.toml requires craftworks
                  build/resources/main/data/beltworks/recipe/y.json names voidworks:void
                  build/resources/main/data/gearworks
                  src/main/java/belt/Pipes.java names "pipeworks"
                  src/main/java/belt/Pipes.java names pipeworks:pipe
                """), out);
    }

    @Test
    void checkPlaysAloneAllowsTheModulesOwnAndGroundworks() throws IOException {
        buildScript("");
        write("src/main/java/belt/Belt.java", "package belt; class Belt { String ns = \"beltworks\"; String gw = \"groundworks:dismantles\"; }\n");
        write("src/main/resources/data/beltworks/tags/item/x.json", "{\"values\":[\"groundworks:wrench\",\"minecraft:stick\"]}\n");
        write("src/main/resources/data/groundworks/tags/item/dismantles.json", "{}\n");
        build("checkPlaysAlone");
    }

    @Test
    void checkPlaysAlonePassesAModuleWithNoResources() throws IOException {
        buildScript("");
        write("src/main/java/belt/Belt.java", "package belt; class Belt { }\n");
        build("checkPlaysAlone");
    }

    private Path siblingAt(String version) throws IOException {
        Path checkout = Files.createDirectories(elsewhere.resolve("groundworks"));
        Files.writeString(checkout.resolve("gradle.properties"), "mod_version = " + version + "\n");
        return checkout;
    }

    @Test
    void refusesASiblingCheckoutOutsideTheRange() throws IOException {
        buildScript("upstream('groundworks') { range = '[0.5.4,0.6)' }");
        Path checkout = siblingAt("0.6.0");
        String out = fail("-PsiblingBuilds", "-PgroundworksDir=" + checkout, "help").getOutput();
        assertTrue(out.contains("groundworks 0.6.0"), out);
        assertTrue(out.contains("outside [0.5.4,0.6)"), out);
    }

    @Test
    void acceptsASiblingCheckoutInTheRange() throws IOException {
        buildScript("upstream('groundworks') { range = '[0.5.4,0.6)' }");
        Path checkout = siblingAt("0.5.9");
        build("-PsiblingBuilds", "-PgroundworksDir=" + checkout, "help");
    }

    @Test
    void refusesAMissingSiblingCheckout() throws IOException {
        buildScript("upstream('groundworks') { range = '[0.5.4,0.6)' }");
        String out = fail("-PsiblingBuilds", "-PgroundworksDir=" + elsewhere.resolve("nowhere"), "help").getOutput();
        assertTrue(out.contains("no gradle.properties"), out);
    }

    @Test
    void refusesASiblingCheckoutWithoutAVersion() throws IOException {
        buildScript("upstream('groundworks') { range = '[0.5.4,0.6)' }");
        Path checkout = Files.createDirectories(elsewhere.resolve("groundworks"));
        Files.writeString(checkout.resolve("gradle.properties"), "mod_id = groundworks\n");
        String out = fail("-PsiblingBuilds", "-PgroundworksDir=" + checkout, "help").getOutput();
        assertTrue(out.contains("names no mod_version"), out);
    }

    @Test
    void refusesAVersionAlreadyInMavenLocal() throws IOException {
        buildScript("");
        Path repo = elsewhere.resolve("m2");
        Files.createDirectories(repo.resolve("io/github/5thlayer/beltworks/0.3.0"));
        String out = fail("-Dmaven.repo.local=" + repo, "publishMavenJavaPublicationToMavenLocal",
                "-x", "jar", "-x", "sourcesJar", "-x", "compileJava", "-x", "processResources", "-x", "generateMetadataFileForMavenJavaPublication").getOutput();
        assertTrue(out.contains("Beltworks 0.3.0 is already in"), out);
    }

    @Test
    void refusesToPublishUnderSiblingBuilds() throws IOException {
        buildScript("");
        String out = fail("-Dmaven.repo.local=" + elsewhere.resolve("m2"), "-PsiblingBuilds", "publishMavenJavaPublicationToMavenLocal",
                "-x", "jar", "-x", "sourcesJar", "-x", "compileJava", "-x", "processResources", "-x", "generateMetadataFileForMavenJavaPublication").getOutput();
        assertTrue(out.contains("nothing publishes under it"), out);
    }

    @Test
    void writesTheGametestPlatformIntoTheGametestSourceSet() throws IOException {
        buildScript("");
        build("buildGametestStructures");
        Path platform = module.resolve("src/gametest/resources/data/beltworks/structure/gametest/platform.nbt");
        assertTrue(Files.size(platform) > 0, platform.toString());
        // In the gametest source set, which never ships (ADR 0011).
        assertFalse(Files.exists(module.resolve("src/main/resources")));
    }

    @Test
    void leavesAnIdenticalGametestPlatformAndRefusesToOverwriteAModulesOwn() throws IOException {
        buildScript("");
        build("buildGametestStructures");
        Path platform = module.resolve("src/gametest/resources/data/beltworks/structure/gametest/platform.nbt");
        byte[] generated = Files.readAllBytes(platform);
        build("buildGametestStructures");
        assertArrayEquals(generated, Files.readAllBytes(platform));
        Files.write(platform, new byte[] {1, 2, 3});
        String out = fail("buildGametestStructures").getOutput();
        assertTrue(out.contains("platform.nbt"), out);
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(platform));
    }

    private void git(String... args) throws IOException, InterruptedException {
        List<String> all = new ArrayList<>(List.of("git", "-c", "user.name=t", "-c", "user.email=t@t", "-c", "commit.gpgsign=false"));
        all.addAll(List.of(args));
        assertEquals(0, new ProcessBuilder(all).directory(module.toFile()).inheritIO().start().waitFor());
    }

    @Test
    void releaseTagsByTheModulesPrefix() throws Exception {
        buildScript("tagPrefix = 'belt-v'");
        Files.writeString(module.resolve(".gitignore"), ".gradle/\nbuild/\n");
        git("init", "-q");
        git("add", ".");
        git("commit", "-q", "-m", "init");
        git("tag", "belt-v0.4.0");
        String out = fail("release", "-PreleaseVersion=0.4.0").getOutput();
        assertTrue(out.contains("belt-v0.4.0 already exists"), out);
    }

    @Test
    void refusesToReleaseUnderSiblingBuilds() throws IOException {
        buildScript("");
        String out = fail("-PsiblingBuilds", "release", "-PreleaseVersion=0.4.0").getOutput();
        assertTrue(out.contains("nothing releases under it"), out);
    }

    @Test
    void uploadRunsTheBundledScriptInTheModule() throws IOException {
        buildScript("");
        String out = fail("upload", "-PreleaseVersion=0.3.0", "-PdryRun").getOutput();
        assertTrue(out.contains("goes to neither site"), out);
    }
}
