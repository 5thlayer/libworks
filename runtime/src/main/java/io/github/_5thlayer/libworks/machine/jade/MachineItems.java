// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.machine.jade;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ui.IDisplayHelper;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ItemView;
import snownee.jade.api.view.ViewGroup;

/**
 * What a machine's item capability holds, slot by slot, skipping empty slots.
 *
 * <p>The Module registers this with {@code registerItemStorage} for its block entity, and a {@link Client}
 * on the same uid with {@code registerItemStorageClient}. This half names no client type, so a dedicated
 * server never loads Jade's drawing (ADR 0006).
 */
public final class MachineItems implements IServerExtensionProvider<ItemStack> {

    private final Identifier uid;

    public MachineItems(Identifier uid) {
        this.uid = uid;
    }

    @Override
    public @Nullable List<ViewGroup<ItemStack>> getGroups(Accessor<?> accessor) {
        if (!(accessor instanceof BlockAccessor block)) {
            return null;
        }
        ResourceHandler<ItemResource> items = block.getLevel().getCapability(Capabilities.Item.BLOCK, block.getPosition(), null);
        if (items == null) {
            return null;
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            if (!items.getResource(index).isEmpty()) {
                stacks.add(items.getResource(index).toStack(items.getAmountAsInt(index)));
            }
        }
        return List.of(new ViewGroup<>(stacks));
    }

    @Override
    public Identifier getUid() {
        return uid;
    }

    /** Gives each stack its count as amount text, which makes Jade draw icons where a few stacks would get their names. Client-only. */
    public static final class Client implements IClientExtensionProvider<ItemStack, ItemView> {

        private final Identifier uid;

        /** On the uid of the {@link MachineItems} it draws. */
        public Client(Identifier uid) {
            this.uid = uid;
        }

        @Override
        public List<ClientViewGroup<ItemView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<ItemStack>> groups) {
            return ClientViewGroup.map(groups, stack -> new ItemView(stack)
                    .amountText(IDisplayHelper.get().humanReadableNumber(stack.getCount(), "", false, null)), null);
        }

        @Override
        public Identifier getUid() {
            return uid;
        }
    }
}
