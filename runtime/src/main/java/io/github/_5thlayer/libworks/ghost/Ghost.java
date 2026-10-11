// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.ghost;

import net.minecraft.world.item.ItemStack;

/**
 * A ghost and where it is drawn, in screen coordinates: the top-left of its 16x16 box. A ghost can
 * stand anywhere, not only in a slot. Its count is the one to show, which is above 1 for an
 * ingredient that needs several.
 */
public record Ghost(ItemStack stack, int x, int y) {
}
