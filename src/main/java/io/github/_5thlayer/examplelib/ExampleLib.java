// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.examplelib;

import io.github._5thlayer.examplelib.gametest.ExampleLibGameTests;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * The Library's entry point. It registers the game tests, which exist only when game tests are
 * enabled, and nothing else yet.
 */
@Mod(ExampleLib.MOD_ID)
public final class ExampleLib {

    /** The mod id, which gradle.properties' {@code mod_id} must match. */
    public static final String MOD_ID = "examplelib";

    public ExampleLib(IEventBus modBus) {
        ExampleLibGameTests.register(modBus);
    }
}
