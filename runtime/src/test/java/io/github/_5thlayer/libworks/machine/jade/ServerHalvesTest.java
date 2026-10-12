// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.machine.jade;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The providers a Module registers on the common side name no client type and none of Jade's drawing
 * types, so a dedicated server with Jade installed never loads them (ADR 0006). Read from each class's
 * constant pool, where every type it names is written out.
 */
class ServerHalvesTest {

    private static final List<String> CLIENT_ONLY = List.of(
            "net/minecraft/client/",
            "snownee/jade/api/ITooltip",
            "snownee/jade/api/IBlockComponentProvider",
            "snownee/jade/api/IComponentProvider",
            "snownee/jade/api/view/IClientExtensionProvider",
            "snownee/jade/api/view/ClientViewGroup",
            "snownee/jade/api/view/ItemView",
            "snownee/jade/api/ui/");

    @ParameterizedTest
    @ValueSource(classes = {MachineReport.class, MachineItems.class, MachineTanks.class})
    void namesNoClientType(Class<?> server) throws IOException {
        String pool = constantPool(server);
        for (String type : CLIENT_ONLY) {
            assertFalse(pool.contains(type), server.getSimpleName() + " names " + type);
        }
    }

    /** The client halves do name them, which shows the scan sees a named type. */
    @ParameterizedTest
    @ValueSource(classes = {MachineReport.Client.class, MachineItems.Client.class, MachineTanks.Client.class})
    void clientHalvesNameThem(Class<?> client) throws IOException {
        String pool = constantPool(client);
        assertTrue(CLIENT_ONLY.stream().anyMatch(pool::contains), client.getName() + " names none of them");
    }

    private static String constantPool(Class<?> type) throws IOException {
        String file = type.getName().substring(type.getName().lastIndexOf('.') + 1) + ".class";
        try (InputStream in = type.getResourceAsStream(file)) {
            return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}
