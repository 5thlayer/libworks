// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.machine.jade;

import java.util.function.BiConsumer;
import java.util.function.Function;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

/**
 * What a machine says about itself in Jade, such as its status line: the server reads {@code D} from the
 * block entity and sends it with the Module's codec, and a {@link Client} turns it into lines.
 *
 * <p>The Module registers this with {@code registerBlockDataProvider} for its block entity, and a
 * {@link Client} built from it with {@code registerBlockComponent} for its block. This half names no client
 * type, so a dedicated server never loads Jade's drawing (ADR 0006).
 */
public final class MachineReport<B extends BlockEntity, D> implements StreamServerDataProvider<BlockAccessor, D> {

    private final Identifier uid;
    private final Class<B> type;
    private final Function<B, D> read;
    private final StreamCodec<RegistryFriendlyByteBuf, D> codec;

    public MachineReport(Identifier uid, Class<B> type, Function<B, D> read, StreamCodec<RegistryFriendlyByteBuf, D> codec) {
        this.uid = uid;
        this.type = type;
        this.read = read;
        this.codec = codec;
    }

    @Override
    public @Nullable D streamData(BlockAccessor accessor) {
        return type.isInstance(accessor.getBlockEntity()) ? read.apply(type.cast(accessor.getBlockEntity())) : null;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, D> streamCodec() {
        return codec;
    }

    @Override
    public Identifier getUid() {
        return uid;
    }

    /** Adds the lines a machine's data reads as, when the server sent any. Client-only. */
    public static final class Client<D> implements IBlockComponentProvider {

        private final MachineReport<?, D> report;
        private final BiConsumer<D, ITooltip> lines;

        /** On {@code report}'s uid, decoding with its codec. */
        public Client(MachineReport<?, D> report, BiConsumer<D, ITooltip> lines) {
            this.report = report;
            this.lines = lines;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            report.decodeFromData(accessor).ifPresent(data -> lines.accept(data, tooltip));
        }

        @Override
        public Identifier getUid() {
            return report.getUid();
        }
    }
}
