// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.ghost.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import io.github._5thlayer.libworks.ghost.GhostScreen;

/**
 * Every {@link GhostScreen}'s ghosts in EMI: the ghost under the mouse is EMI's hovered stack, so the
 * recipe and usage keys work on it as on a real stack. It is not clickable, so a click on its slot
 * reaches the slot: EMI otherwise takes the left click to show recipes, and a stack in hand could never
 * be put in an empty slot. JEI needs no such care, as it never takes a click on an ingredient a screen's
 * handler names.
 *
 * <p>EMI finds this by its annotation and loads it only when EMI is installed; nothing outside this
 * package names an EMI type, so the runtime loads without it (ADR 0007). One provider for every screen,
 * where EMI asks a screen's own providers first, so a Module's provider for the same screen still answers.
 */
@EmiEntrypoint
public final class GhostsEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.addGenericStackProvider(GhostsEmiPlugin::at);
    }

    /**
     * The ghost {@code screen}'s {@link GhostScreen#ghostAt} finds at ({@code x}, {@code y}), as one of its item, or
     * {@link EmiStackInteraction#EMPTY} off every ghost or on a screen that is no {@link GhostScreen}, so
     * EMI's own slot lookup answers for a real stack. One item, whatever count the ghost is drawn with:
     * the keys ask about the item.
     */
    static EmiStackInteraction at(Object screen, int x, int y) {
        if (!(screen instanceof GhostScreen ghostScreen)) {
            return EmiStackInteraction.EMPTY;
        }
        return ghostScreen.ghostAt(x, y)
                .map(ghost -> hovered(EmiStack.of(ghost.stack().copyWithCount(1))))
                .orElse(EmiStackInteraction.EMPTY);
    }

    /** {@code stack} as EMI's hovered stack, which the keys answer and a click goes past. */
    static EmiStackInteraction hovered(EmiStack stack) {
        return new EmiStackInteraction(stack, null, false);
    }
}
