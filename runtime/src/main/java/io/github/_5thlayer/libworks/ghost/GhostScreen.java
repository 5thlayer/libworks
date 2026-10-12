// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.ghost;

import java.util.Optional;

import net.minecraft.util.Util;

/**
 * A screen that shows {@link Ghosts}. A Module's screen implements it, and the runtime offers the ghost
 * under the mouse to EMI, when EMI is installed, as the hovered stack: the recipe and usage keys work on
 * it, and a click goes past it to the slot (ADR 0007). The Module registers no EMI stack provider of its
 * own for these ghosts.
 *
 * <p>Names no EMI or client type, so a screen implements it whether or not EMI is installed.
 */
public interface GhostScreen {

    /** The screen's own ghosts, the ones it draws. */
    Ghosts ghosts();

    /**
     * The ghost under ({@code mouseX}, {@code mouseY}) now, which EMI is handed: {@link #ghosts}' by default.
     * A screen with a ghost beyond them, such as one it draws as the item itself, overrides this to find it too.
     */
    default Optional<Ghost> ghostAt(double mouseX, double mouseY) {
        return ghosts().at(mouseX, mouseY, Util.getMillis());
    }
}
