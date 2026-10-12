// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.ghost.emi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import io.github._5thlayer.libworks.ghost.Ghost;
import io.github._5thlayer.libworks.ghost.GhostScreen;
import io.github._5thlayer.libworks.ghost.Ghosts;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.Test;

/**
 * What EMI is handed for the mouse over a screen. An item stack can't be built here, where item
 * components are never bound, so the hovered stack is a fluid's, which EMI treats alike.
 */
class GhostsEmiPluginTest {

    @Test
    void aHoveredGhostIsNotClickable() {
        EmiStack stack = EmiStack.of(Fluids.WATER, 1000);
        EmiStackInteraction hovered = GhostsEmiPlugin.hovered(stack);
        assertFalse(hovered.isEmpty());
        assertSame(stack, hovered.getStack());
        assertFalse(hovered.isClickable(), "EMI would take the left click, and the slot never get it");
    }

    @Test
    void offEveryGhostIsNothing() {
        GhostScreen screen = Ghosts::new;
        assertSame(EmiStackInteraction.EMPTY, GhostsEmiPlugin.at(screen, 30, 40));
    }

    /** A screen with a ghost beyond its {@code ghosts()}, such as one drawn as the item itself, answers through its own. */
    @Test
    void itAsksTheScreensGhostAt() {
        List<String> asked = new ArrayList<>();
        GhostScreen screen = new GhostScreen() {
            @Override
            public Ghosts ghosts() {
                return new Ghosts();
            }

            @Override
            public Optional<Ghost> ghostAt(double mouseX, double mouseY) {
                asked.add(mouseX + "," + mouseY);
                return Optional.empty();
            }
        };
        assertSame(EmiStackInteraction.EMPTY, GhostsEmiPlugin.at(screen, 30, 40));
        assertEquals(List.of("30.0,40.0"), asked);
    }

    @Test
    void aScreenThatIsNoGhostScreenIsNothing() {
        assertSame(EmiStackInteraction.EMPTY, GhostsEmiPlugin.at(new Object(), 30, 40));
    }

    /** A Module's screen implements it whether or not EMI is installed, so it names no EMI type. */
    @Test
    void theInterfaceNamesNoEmiType() throws IOException {
        assertFalse(constantPool(GhostScreen.class).contains("dev/emi/"));
    }

    /** The plugin does name them, which shows the scan sees a named type. */
    @Test
    void thePluginNamesThem() throws IOException {
        assertTrue(constantPool(GhostsEmiPlugin.class).contains("dev/emi/"));
    }

    /** As {@code ServerHalvesTest}'s: every type a class names is written out in its constant pool. */
    private static String constantPool(Class<?> type) throws IOException {
        String file = type.getName().substring(type.getName().lastIndexOf('.') + 1) + ".class";
        try (InputStream in = type.getResourceAsStream(file)) {
            return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}
