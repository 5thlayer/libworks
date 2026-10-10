// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The runtime's mod entry. A Module nests this jar, and FML loads the newest nested copy once, so
 * the Works tab is registered once however many Modules are installed (ADR-0128).
 */
@Mod(Libworks.MOD_ID)
public final class Libworks {

    /** The mod id. Must match {@code META-INF/neoforge.mods.toml}. */
    public static final String MOD_ID = "libworks";

    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    static {
        // A vanilla item, so the icon exists without any Module.
        TABS.register(WorksTab.KEY.identifier().getPath(), () -> CreativeModeTab.builder()
                .title(Component.translatable(WorksTab.TITLE_KEY))
                .icon(() -> new ItemStack(Items.IRON_PICKAXE))
                .build());
    }

    public Libworks(IEventBus modBus) {
        TABS.register(modBus);
    }
}
