// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Each rule of the plays-alone scan, on files written for it: Beltworks is the Module scanned unless a test names another. */
class PlaysAloneTest {
    private final PlaysAlone belt = new PlaysAlone("beltworks");
    private final PlaysAlone wicked = new PlaysAlone("atouchofwickedness");

    @Test
    void forbidsEveryModuleButItselfAndGroundworksAndTheRetiredPackAndEveryPillar() {
        assertEquals(List.of("atouchof", "atouchofcold", "atouchofdarkness", "atouchofmadness", "atouchofwickedness", "craftworks", "factoryworks",
                "fieldworks", "gearworks", "pipeworks", "voidworks", "wireworks"), belt.getForbidden());
        assertEquals(List.of("atouchof", "atouchofcold", "atouchofdarkness", "atouchofmadness", "atouchofwickedness", "beltworks", "craftworks",
                "factoryworks", "fieldworks", "gearworks", "pipeworks", "voidworks", "wireworks"), new PlaysAlone("groundworks").getForbidden());
    }

    @Test
    void aPillarForbidsTheOtherPillarsTheShelvedColdAndEveryFactoryWorksModuleGroundworksIncluded() {
        assertEquals(List.of("atouchofcold", "atouchofmadness", "atouchofwickedness", "beltworks", "craftworks", "factoryworks", "fieldworks",
                "gearworks", "groundworks", "pipeworks", "voidworks", "wireworks"), new PlaysAlone("atouchofdarkness").getForbidden());
        assertEquals(List.of("atouchofcold", "atouchofdarkness", "atouchofmadness", "beltworks", "craftworks", "factoryworks", "fieldworks",
                "gearworks", "groundworks", "pipeworks", "voidworks", "wireworks"), new PlaysAlone("atouchofwickedness").getForbidden());
        assertEquals(List.of("atouchofcold", "atouchofdarkness", "atouchofmadness", "atouchofwickedness", "beltworks", "craftworks", "factoryworks",
                "fieldworks", "gearworks", "groundworks", "pipeworks", "voidworks", "wireworks"), new PlaysAlone("atouchof").getForbidden());
    }

    @Test
    void aPillarMayNameTheRuntimeButNoFactoryWorksModule(@TempDir Path resources) throws IOException {
        write(resources.resolve("data/atouchofdarkness/recipe/x.json"),
                "{\"a\":\"atouchof:meter\",\"b\":\"pipeworks:pipe\",\"c\":\"atouchofmadness:x\",\"d\":\"groundworks:wrench\"}");
        assertEquals(List.of("data/atouchofdarkness/recipe/x.json names pipeworks:pipe", "data/atouchofdarkness/recipe/x.json names atouchofmadness:x",
                "data/atouchofdarkness/recipe/x.json names groundworks:wrench"), new PlaysAlone("atouchofdarkness").ids(resources));
    }

    @Test
    void aFactoryWorksModuleMayNameNoPillarNorTheRuntime(@TempDir Path resources) throws IOException {
        write(resources.resolve("data/beltworks/recipe/x.json"), "{\"a\":\"atouchof:meter\",\"b\":\"atouchofdarkness:shade\",\"c\":\"groundworks:wrench\"}");
        assertEquals(List.of("data/beltworks/recipe/x.json names atouchof:meter", "data/beltworks/recipe/x.json names atouchofdarkness:shade"),
                belt.ids(resources));
    }

    @Test
    void aModNoSuiteListsForbidsEverySuitesModulesButTheirBases() {
        assertEquals(List.of("atouchofcold", "atouchofdarkness", "atouchofmadness", "atouchofwickedness", "beltworks", "craftworks", "factoryworks",
                "fieldworks", "gearworks", "pipeworks", "voidworks", "wireworks"), new PlaysAlone("examplelib").getForbidden());
    }

    @Test
    void aDataOrAssetFolderInAnotherModulesNamespaceIsReported(@TempDir Path resources) throws IOException {
        write(resources.resolve("data/fieldworks/recipe/x.json"), "{}");
        write(resources.resolve("assets/voidworks/lang/en_us.json"), "{}");
        write(resources.resolve("data/beltworks/recipe/x.json"), "{}");
        write(resources.resolve("data/groundworks/tags/item/dismantles.json"), "{}");
        assertEquals(List.of("assets/voidworks", "data/fieldworks"), belt.namespaces(resources));
    }

    @Test
    void aPillarMayAddItsOwnOptionalEntriesToAnotherPillarsTag(@TempDir Path resources) throws IOException {
        write(resources.resolve("data/atouchofmadness/tags/entity_type/unsettling.json"),
                "{\"replace\": false, \"values\": [{\"id\": \"atouchofwickedness:bailiff\", \"required\": false}]}");
        write(resources.resolve("data/atouchofdarkness/tags/item/x.json"), "{\"values\": [{\"id\": \"#atouchofwickedness:seals\", \"required\": false}]}");
        assertEquals(List.of(), wicked.namespaces(resources));
        assertEquals(List.of(), wicked.otherTags(resources));
    }

    @Test
    void anOptionalTagFileThatRequiresReplacesOrAddsAnotherIdIsReported(@TempDir Path resources) throws IOException {
        write(resources.resolve("data/atouchofmadness/tags/entity_type/a.json"), "{\"values\": [\"atouchofwickedness:bailiff\"]}");
        write(resources.resolve("data/atouchofmadness/tags/entity_type/b.json"), "{\"values\": [{\"id\": \"atouchofwickedness:bailiff\"}]}");
        write(resources.resolve("data/atouchofmadness/tags/entity_type/c.json"),
                "{\"values\": [{\"id\": \"atouchofwickedness:bailiff\", \"required\": true}]}");
        write(resources.resolve("data/atouchofmadness/tags/entity_type/d.json"),
                "{\"replace\": true, \"values\": [{\"id\": \"atouchofwickedness:bailiff\", \"required\": false}]}");
        write(resources.resolve("data/atouchofmadness/tags/entity_type/e.json"), "{\"values\": [{\"id\": \"minecraft:zombie\", \"required\": false}]}");
        write(resources.resolve("data/atouchofmadness/tags/entity_type/f.json"), "[]");
        write(resources.resolve("data/atouchofmadness/tags/entity_type/g.json"), "{\"values\": [], \"remove\": [\"atouchofmadness:x\"]}");
        write(resources.resolve("data/atouchofmadness/tags/entity_type/i.json"), "{\"values\": [], \"remove\": []}");
        write(resources.resolve("data/atouchofmadness/tags/entity_type/j.txt"), "atouchofwickedness:bailiff");
        write(resources.resolve("data/atouchofmadness/tags/h.json"), "{\"values\": [{\"id\": \"atouchofwickedness:bailiff\", \"required\": false}]}");
        assertEquals(List.of("data/atouchofmadness/tags/entity_type/a.json requires atouchofwickedness:bailiff",
                "data/atouchofmadness/tags/entity_type/b.json requires atouchofwickedness:bailiff",
                "data/atouchofmadness/tags/entity_type/c.json requires atouchofwickedness:bailiff",
                "data/atouchofmadness/tags/entity_type/d.json replaces the tag",
                "data/atouchofmadness/tags/entity_type/e.json adds minecraft:zombie, not atouchofwickedness's own",
                "data/atouchofmadness/tags/entity_type/f.json is not a tag",
                "data/atouchofmadness/tags/entity_type/g.json removes from the tag",
                "data/atouchofmadness/tags/entity_type/i.json removes from the tag",
                "data/atouchofmadness/tags/entity_type/j.txt is not a tag",
                "data/atouchofmadness/tags/h.json is not a tag"), wicked.otherTags(resources));
    }

    @Test
    void anotherPillarsAssetsOrDataOutsideItsTagsAreReported(@TempDir Path resources) throws IOException {
        write(resources.resolve("data/atouchofmadness/tags/entity_type/unsettling.json"),
                "{\"values\": [{\"id\": \"atouchofwickedness:bailiff\", \"required\": false}]}");
        write(resources.resolve("data/atouchofmadness/recipe/x.json"), "{}");
        write(resources.resolve("assets/atouchofmadness/lang/en_us.json"), "{}");
        assertEquals(List.of("assets/atouchofmadness"), wicked.namespaces(resources));
        assertEquals(List.of("data/atouchofmadness/recipe/x.json is outside tags/"), wicked.otherTags(resources));
    }

    @Test
    void theRuntimeMayAddNoEntryToAPillarsTag(@TempDir Path resources) throws IOException {
        write(resources.resolve("data/atouchofmadness/tags/item/x.json"), "{\"values\": [{\"id\": \"atouchof:x\", \"required\": false}]}");
        PlaysAlone runtime = new PlaysAlone("atouchof");
        assertEquals(List.of("data/atouchofmadness"), runtime.namespaces(resources));
        assertEquals(List.of(), runtime.otherTags(resources));
    }

    @Test
    void optionalTagEntriesAreNoExceptionForTheShelvedColdAnotherSuiteOrFactoryWorks(@TempDir Path resources) throws IOException {
        String optional = "{\"values\": [{\"id\": \"%s:x\", \"required\": false}]}";
        write(resources.resolve("data/atouchofcold/tags/item/x.json"), optional.formatted("atouchofwickedness"));
        write(resources.resolve("data/pipeworks/tags/item/x.json"), optional.formatted("atouchofwickedness"));
        assertEquals(List.of("data/atouchofcold", "data/pipeworks"), wicked.namespaces(resources));
        assertEquals(List.of(), wicked.otherTags(resources));
        write(resources.resolve("data/fieldworks/tags/item/x.json"), optional.formatted("beltworks"));
        write(resources.resolve("data/atouchofmadness/tags/item/x.json"), optional.formatted("beltworks"));
        assertEquals(List.of("data/atouchofcold", "data/atouchofmadness", "data/fieldworks", "data/pipeworks"), belt.namespaces(resources));
        assertEquals(List.of(), belt.otherTags(resources));
    }

    @Test
    void anIdInDataIsReported(@TempDir Path resources) throws IOException {
        write(resources.resolve("data/beltworks/recipe/x.json"), "{\"key\":{\"a\":\"gearworks:gear\"}}");
        assertEquals(List.of("data/beltworks/recipe/x.json names gearworks:gear"), belt.ids(resources));
    }

    @Test
    void aTagOfTheRetiredPackInARecipeIsReported(@TempDir Path resources) throws IOException {
        write(resources.resolve("data/beltworks/recipe/x.json"), "{\"key\":{\"a\":\"#factoryworks:belts\"}}");
        assertEquals(List.of("data/beltworks/recipe/x.json names factoryworks:belts"), belt.ids(resources));
    }

    @Test
    void anIdInJavaSourceIsReported(@TempDir Path code) throws IOException {
        write(code.resolve("x/Pipes.java"), "class Pipes { String id = \"pipeworks:pipe\"; }");
        assertEquals(List.of("x/Pipes.java names pipeworks:pipe"), belt.ids(code));
    }

    @Test
    void anIdInTheModsTomlIsReported(@TempDir Path resources) throws IOException {
        write(resources.resolve("META-INF/neoforge.mods.toml"), "description = \"fills wireworks:cable\"");
        assertEquals(List.of("META-INF/neoforge.mods.toml names wireworks:cable"), belt.ids(resources));
    }

    @Test
    void anIdInAStructureIsReportedWhenItsLengthByteIsALetter(@TempDir Path resources) throws IOException {
        String id = "fieldworks:" + "o".repeat('A' - "fieldworks:".length());
        Path nbt = resources.resolve("data/beltworks/structure/x.nbt");
        Files.createDirectories(nbt.getParent());
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(nbt))) {
            out.write(("\u0008\u0000\u0004Name\u0000" + (char) id.length() + id).getBytes(StandardCharsets.ISO_8859_1));
        }
        assertEquals(List.of("data/beltworks/structure/x.nbt names " + id), belt.ids(resources));
    }

    @Test
    void anotherModulesNamespaceInJavaSourceIsReported(@TempDir Path code) throws IOException {
        write(code.resolve("x/Pipes.java"), "class Pipes { Identifier id = Identifier.fromNamespaceAndPath(\"pipeworks\", \"pipe\"); }");
        write(code.resolve("x/Own.java"), "class Own { String ns = \"beltworks\"; String gw = \"groundworks\"; }");
        assertEquals(List.of("x/Pipes.java names \"pipeworks\""), belt.namespaceStrings(code));
    }

    @Test
    void aRequiredDependencyOnAnotherModuleIsReported(@TempDir Path resources) throws IOException {
        write(resources.resolve("META-INF/neoforge.mods.toml"), """
                [[mods]]
                modId = "beltworks"

                [[dependencies.beltworks]]
                modId = "groundworks"
                type = "required"

                [[dependencies.beltworks]]
                modId = "craftworks"
                type = "required"

                [[dependencies.beltworks]]
                modId="wireworks"
                """);
        assertEquals(List.of("META-INF/neoforge.mods.toml requires craftworks", "META-INF/neoforge.mods.toml requires wireworks"),
                belt.required(resources));
    }

    @Test
    void anOptionalDependencyOnAnotherModuleIsAllowed(@TempDir Path resources) throws IOException {
        write(resources.resolve("META-INF/neoforge.mods.toml"), """
                [[dependencies.beltworks]]
                modId = "pipeworks"
                type = "optional"
                """);
        assertEquals(List.of(), belt.required(resources));
    }

    @Test
    void theModsOwnIdsGroundworksAndTheSharedNamespacesAreAllowed(@TempDir Path resources) throws IOException {
        write(resources.resolve("assets/beltworks/models/x.json"),
                "{\"a\":\"beltworks:block/x\",\"b\":\"minecraft:glass\",\"c\":\"#c:glass_blocks\",\"d\":\"groundworks:x\",\"e\":\"neoforge:y\"}");
        assertEquals(List.of(), belt.ids(resources));
    }

    @Test
    void aMissingFolderHasNothingToReport(@TempDir Path dir) {
        assertEquals(List.of(), belt.ids(dir.resolve("none")));
        assertEquals(List.of(), belt.namespaceStrings(dir.resolve("none")));
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }
}
