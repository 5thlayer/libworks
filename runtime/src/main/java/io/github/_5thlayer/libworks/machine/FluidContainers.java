// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.machine;

import java.util.function.Predicate;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * A machine's drain and fill slots: a container, a bucket or any item with NeoForge's fluid item capability, emptied
 * into a tank or filled from one, and sent to the slot it goes out by. A <b>drain slot</b> takes filled containers and
 * a <b>fill slot</b> empty ones.
 *
 * <p>A container goes whole or not at all: it waits in its slot while the tank has too little room, or too little
 * fluid, for all of it, or while the output has no room for what it becomes. The move runs on a copy of the two slots
 * inside a transaction, since a machine's own slots need not be transactional, and the caller lands it only once it
 * has gone through: it takes one from the container's slot and sets the output to what the method returns. The move
 * opens a root transaction of its own, so call it outside any, such as from a machine's tick. No client types, so a
 * game test reads it.
 */
public final class FluidContainers {

    private FluidContainers() {
    }

    /**
     * Drains one of {@code container} into {@code tank}, the output slot holding {@code output}. Returns what the
     * output holds after, the drained container added to it, or null when nothing moved.
     */
    public static @Nullable ItemStack drain(ItemStack container, ItemStack output, ResourceHandler<FluidResource> tank) {
        return move(container, output, tank, null);
    }

    /** Fills one of {@code container} with {@code fluid} from {@code tank}; returns as {@link #drain} does. */
    public static @Nullable ItemStack fill(ItemStack container, ItemStack output, ResourceHandler<FluidResource> tank,
            FluidResource fluid) {
        return move(container, output, tank, fluid);
    }

    /** Whether {@code stack} is a container holding a fluid {@code fluid} accepts, which a drain slot for it takes. */
    public static boolean holds(ItemStack stack, Predicate<FluidResource> fluid) {
        ResourceHandler<FluidResource> fluids = fluidsIn(stack);
        if (fluids == null) {
            return false;
        }
        for (int index = 0; index < fluids.size(); index++) {
            if (fluids.getAmountAsLong(index) > 0 && fluid.test(fluids.getResource(index))) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code stack} is an empty container {@code fluid} can go into, which a fill slot for it takes. */
    public static boolean takes(ItemStack stack, FluidResource fluid) {
        ResourceHandler<FluidResource> fluids = fluidsIn(stack);
        if (fluids == null || !ResourceHandlerUtil.isEmpty(fluids)) {
            return false;
        }
        for (int index = 0; index < fluids.size(); index++) {
            if (fluids.getCapacityAsLong(index, fluid) > 0 && fluids.isValid(index, fluid)) {
                return true;
            }
        }
        return false;
    }

    /** @param filling the fluid a fill draws, or null to drain the container into the tank */
    private static @Nullable ItemStack move(ItemStack container, ItemStack output, ResourceHandler<FluidResource> tank,
            @Nullable FluidResource filling) {
        if (container.isEmpty()) {
            return null;
        }
        ItemStacksResourceHandler slots = new ItemStacksResourceHandler(
                NonNullList.of(ItemStack.EMPTY, container.copyWithCount(1), output.copy()));
        ResourceHandler<FluidResource> fluids = new SlotsAccess(slots).getCapability(Capabilities.Fluid.ITEM);
        if (fluids == null) {
            return null;
        }
        // A bucket moves all or nothing by itself; a larger container must too.
        long whole = filling == null ? held(fluids) : room(fluids, filling);
        try (Transaction transaction = Transaction.openRoot()) {
            int moved = filling == null
                    ? ResourceHandlerUtil.move(fluids, tank, resource -> true, Integer.MAX_VALUE, transaction)
                    : ResourceHandlerUtil.move(tank, fluids, filling::equals, Integer.MAX_VALUE, transaction);
            if (moved <= 0 || moved != whole) {
                return null;
            }
            transaction.commit();
        }
        return slots.getResource(1).toStack(slots.getAmountAsInt(1));
    }

    private static long held(ResourceHandler<FluidResource> fluids) {
        long held = 0;
        for (int index = 0; index < fluids.size(); index++) {
            held += fluids.getAmountAsLong(index);
        }
        return held;
    }

    private static long room(ResourceHandler<FluidResource> fluids, FluidResource fluid) {
        long room = 0;
        for (int index = 0; index < fluids.size(); index++) {
            // A slot that refuses the fluid gives no room, or a container with one could never fill whole.
            if (fluids.isValid(index, fluid)) {
                room += fluids.getCapacityAsLong(index, fluid) - fluids.getAmountAsLong(index);
            }
        }
        return room;
    }

    private static @Nullable ResourceHandler<FluidResource> fluidsIn(ItemStack stack) {
        return stack.isEmpty() ? null : ItemAccess.forStack(stack.copyWithCount(1)).getCapability(Capabilities.Fluid.ITEM);
    }

    /** The container being moved, at index 0 of a copy, and the output it goes to, at index 1. */
    private record SlotsAccess(ItemStacksResourceHandler slots) implements ItemAccess {

        @Override
        public ItemResource getResource() {
            return slots.getResource(0);
        }

        @Override
        public int getAmount() {
            return slots.getAmountAsInt(0);
        }

        @Override
        public int insert(ItemResource resource, int amount, TransactionContext transaction) {
            return slots.insert(1, resource, amount, transaction);
        }

        @Override
        public int extract(ItemResource resource, int amount, TransactionContext transaction) {
            return slots.extract(0, resource, amount, transaction);
        }
    }
}
