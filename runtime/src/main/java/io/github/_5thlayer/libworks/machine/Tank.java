// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.machine;

import java.util.Objects;

import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

/**
 * One of a machine's tanks, as Jade shows it: what it holds, how much it can, and the lang key of its
 * title, or {@code null} for Jade's untitled view. {@code id} tells a machine's tanks apart. No Jade or
 * client types, so a game test reads it.
 */
public record Tank(String id, FluidStack fluid, int capacity, @Nullable String titleKey) {

    public Tank {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(fluid, "fluid");
    }
}
