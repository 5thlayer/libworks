// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;

/**
 * The creative tab every -works Module shows its items in. libworks registers the tab; a Module
 * only adds to it, in a {@code BuildCreativeModeTabContentsEvent} listener that checks
 * {@code event.getTabKey() == WorksTab.KEY}. Tab order between Modules is their load order.
 */
public final class WorksTab {

    /** {@code libworks:works}. */
    public static final ResourceKey<CreativeModeTab> KEY = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(Libworks.MOD_ID, "works"));

    /** The tab's title key, which libworks' en_us.json translates to "Works". */
    public static final String TITLE_KEY = "itemGroup." + Libworks.MOD_ID + ".works";

    private WorksTab() {
    }
}
