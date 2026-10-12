// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.machine.jade;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import io.github._5thlayer.libworks.machine.Tank;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;

/**
 * A machine's tanks in Jade, each its own group, where Jade's own reading of the fluid capability
 * would merge them into one. A tank with a title key shows it as the group's title; one without gets
 * Jade's untitled view.
 *
 * <p>The Module registers this with {@code registerFluidStorage} for its block entity, and a
 * {@link Client} on the same uid with {@code registerFluidStorageClient}. This half names no client
 * type, so a dedicated server never loads Jade's drawing (ADR 0006).
 */
public final class MachineTanks<B extends BlockEntity> implements IServerExtensionProvider<FluidView.Data> {

    private static final String TITLE = "libworks_title";

    private final Identifier uid;
    private final Class<B> type;
    private final Function<B, List<Tank>> tanks;

    public MachineTanks(Identifier uid, Class<B> type, Function<B, List<Tank>> tanks) {
        this.uid = uid;
        this.type = type;
        this.tanks = tanks;
    }

    @Override
    public @Nullable List<ViewGroup<FluidView.Data>> getGroups(Accessor<?> accessor) {
        if (!(accessor instanceof BlockAccessor block) || !type.isInstance(block.getBlockEntity())) {
            return null;
        }
        return groups(tanks.apply(type.cast(block.getBlockEntity())));
    }

    @Override
    public Identifier getUid() {
        return uid;
    }

    /** One group a tank, in order, each with the tank's id and, when it has one, its title key. */
    static List<ViewGroup<FluidView.Data>> groups(List<Tank> tanks) {
        return tanks.stream().map(MachineTanks::group).toList();
    }

    private static ViewGroup<FluidView.Data> group(Tank tank) {
        JadeFluidObject shown = tank.fluid().isEmpty()
                ? JadeFluidObject.empty()
                : JadeFluidObject.of(tank.fluid().getFluid(), tank.fluid().getAmount(), tank.fluid().getComponentsPatch());
        Optional<CompoundTag> extra = Optional.ofNullable(tank.titleKey()).map(key -> {
            CompoundTag tag = new CompoundTag();
            tag.putString(TITLE, key);
            return tag;
        });
        return new ViewGroup<>(List.of(new FluidView.Data(shown, tank.capacity())), Optional.of(tank.id()), extra);
    }

    /** The title key a group was given, if its tank had one. */
    static Optional<String> titleKey(ViewGroup<?> group) {
        return group.getExtraData().getString(TITLE);
    }

    /** Draws each tank as Jade's own fluid bar, under its title when it has one. Client-only. */
    public static final class Client implements IClientExtensionProvider<FluidView.Data, FluidView> {

        private final Identifier uid;

        /** On the uid of the {@link MachineTanks} it draws. */
        public Client(Identifier uid) {
            this.uid = uid;
        }

        @Override
        public List<ClientViewGroup<FluidView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<FluidView.Data>> groups) {
            return ClientViewGroup.map(groups, FluidView::readDefault, (group, shown) ->
                    titleKey(group).ifPresent(key -> shown.title = Component.translatable(key)));
        }

        @Override
        public Identifier getUid() {
            return uid;
        }
    }
}
