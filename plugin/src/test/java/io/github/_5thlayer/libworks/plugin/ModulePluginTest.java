package io.github._5thlayer.libworks.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
                tasks.register('declared') {
                    doLast {
                        ['implementation', 'jarJar', 'localRuntime'].each { c ->
                            configurations[c].dependencies.each { d ->
                                println "$c ${d.group}:${d.name} strictly=${d.versionConstraint.strictVersion} prefer=${d.versionConstraint.preferredVersion}"
                            }
                        }
                    }
                }
                """.formatted(libworksBlock));
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
    void requiresEachUpstreamStrictlyInItsRangeAndPrefersTheLowerBound() throws IOException {
        buildScript("upstream('groundworks') { range = '[0.5.4,0.6)' }");
        assertTrue(build("-q", "declared").getOutput().contains(
                "implementation io.github.5thlayer:groundworks strictly=[0.5.4,0.6) prefer=0.5.4"));
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
    void writesTheGametestPlatformIntoTheModulesNamespace() throws IOException {
        buildScript("");
        build("buildGametestStructures");
        Path platform = module.resolve("src/main/resources/data/beltworks/structure/gametest/platform.nbt");
        assertTrue(Files.size(platform) > 0, platform.toString());
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
