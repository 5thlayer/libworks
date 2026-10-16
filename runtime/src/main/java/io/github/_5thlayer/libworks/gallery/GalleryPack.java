// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.gallery;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * A Module's Gallery pack, {@code gallery_pack/} in its jar: offered in every world's Data Packs screen
 * as "{@code <modid>.gallery.pack}" and off until a player turns it on (ADR 0010).
 */
public final class GalleryPack {

    /** The folder in the Module's jar that holds the pack. */
    public static final String FOLDER = "gallery_pack";

    // Not PackSource.FEATURE, also never added automatically: world creation lists every FEATURE pack
    // as an experiment (ADR 0010).
    private static final PackSource OPTIONAL_BUILT_IN = PackSource.create(PackSource.BUILT_IN::decorate, false);

    private GalleryPack() {
    }

    /** Offers {@code modId}'s Gallery pack, from its jar, on every server. */
    public static void register(IEventBus modBus, String modId) {
        modBus.addListener((AddPackFindersEvent event) -> addTo(event, modId));
    }

    /** As {@link #register}, from a listener the Module already has. */
    public static void addTo(AddPackFindersEvent event, String modId) {
        event.addPackFinders(Identifier.fromNamespaceAndPath(modId, FOLDER), PackType.SERVER_DATA,
                Component.translatable(modId + ".gallery.pack"), OPTIONAL_BUILT_IN, false, Pack.Position.TOP);
    }

    /** The pack's id, as {@code /datapack enable} names it. */
    public static String id(String modId) {
        return "mod/" + modId + ":" + FOLDER;
    }

    /** The function that builds every scene of {@code modId}'s gallery. */
    public static Identifier build(String modId) {
        return Identifier.fromNamespaceAndPath(modId, "gallery/build");
    }

    /**
     * The function that clears one scene's plot, run from where the scene is run, removing what it
     * placed as a player's world would; a pack may leave it out.
     */
    public static Identifier clear(String modId) {
        return Identifier.fromNamespaceAndPath(modId, "gallery/clear");
    }

    /** The function that builds one scene. */
    public static Identifier scene(String modId, String scene) {
        return Identifier.fromNamespaceAndPath(modId, "gallery/" + scene);
    }
}
