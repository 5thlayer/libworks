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

/** Each rule of the plays-alone scan, on files written for it: Beltworks is the Module scanned. */
class PlaysAloneTest {
    private final PlaysAlone belt = new PlaysAlone("beltworks");

    @Test
    void forbidsEveryModuleButItselfAndGroundworksAndTheRetiredPack() {
        assertEquals(List.of("craftworks", "factoryworks", "fieldworks", "gearworks", "pipeworks", "voidworks", "wireworks"), belt.getForbidden());
        assertEquals(List.of("beltworks", "craftworks", "factoryworks", "fieldworks", "gearworks", "pipeworks", "voidworks", "wireworks"),
                new PlaysAlone("groundworks").getForbidden());
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
