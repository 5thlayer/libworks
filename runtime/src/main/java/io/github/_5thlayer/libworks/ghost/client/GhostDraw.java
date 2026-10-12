// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.ghost.client;

import io.github._5thlayer.libworks.ghost.Ghost;
import io.github._5thlayer.libworks.ghost.Ghosts;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The one look of a ghost for the whole suite. Client-only: nothing in common code may reference it.
 *
 * <p>A {@link Ghost} is in screen coordinates, so draw it in a pass that is too (such as
 * {@code extractBackground}, or after {@code super.render}), not in {@code extractLabels}, which is
 * translated to the panel's corner.
 */
public final class GhostDraw {

    /** Half the slot's own colour over a ghost, so it reads as a placeholder rather than an item. */
    private static final int VEIL = 0x808B8B8B;

    private static final int COUNT_RIGHT = Ghosts.SIZE + 1;
    private static final int COUNT_Y = 9;
    private static final int COUNT_COLOR = 0xFFFFFFFF;

    private GhostDraw() {
    }

    /** Draws the item, the veil over it, then its count at the bottom right when above 1, at the ghost's screen coordinates. */
    public static void draw(GuiGraphicsExtractor graphics, Font font, Ghost ghost) {
        int x = ghost.x();
        int y = ghost.y();
        graphics.item(ghost.stack(), x, y);
        graphics.fill(x, y, x + Ghosts.SIZE, y + Ghosts.SIZE, VEIL);
        if (ghost.stack().getCount() > 1) {
            String count = Integer.toString(ghost.stack().getCount());
            graphics.text(font, count, x + COUNT_RIGHT - font.width(count), y + COUNT_Y, COUNT_COLOR, true);
        }
    }

    /** The ghost's item tooltip, as over a real stack. */
    public static void tooltip(GuiGraphicsExtractor graphics, Font font, Ghost ghost, int mouseX, int mouseY) {
        graphics.setTooltipForNextFrame(font, ghost.stack(), mouseX, mouseY);
    }
}
