// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.ghost.client;

import io.github._5thlayer.libworks.ghost.Ghost;
import io.github._5thlayer.libworks.ghost.Ghosts;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The one look of a ghost for the whole suite. Client-only: nothing in common code may reference it.
 */
public final class GhostDraw {

    /** Half the slot's own colour over a ghost, so it reads as a placeholder rather than an item. */
    private static final int VEIL = 0x808B8B8B;

    private GhostDraw() {
    }

    /** Draws the item, the veil over it, then its count at the bottom right when above 1. */
    public static void draw(GuiGraphicsExtractor graphics, Font font, Ghost ghost) {
        int x = ghost.x();
        int y = ghost.y();
        graphics.item(ghost.stack(), x, y);
        graphics.fill(x, y, x + Ghosts.SIZE, y + Ghosts.SIZE, VEIL);
        if (ghost.stack().getCount() > 1) {
            String count = Integer.toString(ghost.stack().getCount());
            graphics.text(font, count, x + 17 - font.width(count), y + 9, 0xFFFFFFFF, true);
        }
    }
}
