// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.transfer;

import net.neoforged.neoforge.transfer.DelegatingResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A {@link DelegatingResourceHandler} whose slot-less insert and extract go back through its own
 * slots.
 *
 * <p>{@code DelegatingResourceHandler} forwards the slot-less pair straight to the delegate, whose
 * loop never consults a subclass's per-slot refusals, so a pipe that does not name a slot walks
 * past them. Looping through {@code this} restores the rule; a handler that refuses nothing
 * behaves as before (ADR-0128, research/transfer-api-guarded-faces.md in 5thlayer/factoryworks).
 */
public class GuardedResourceHandler<T extends Resource> extends DelegatingResourceHandler<T> {

    public GuardedResourceHandler(ResourceHandler<T> delegate) {
        super(delegate);
    }

    @Override
    public int insert(T resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        int inserted = 0;
        int size = size();
        for (int index = 0; index < size && inserted < amount; index++) {
            inserted += insert(index, resource, amount - inserted, transaction);
        }
        return inserted;
    }

    @Override
    public int extract(T resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        int extracted = 0;
        int size = size();
        for (int index = 0; index < size && extracted < amount; index++) {
            extracted += extract(index, resource, amount - extracted, transaction);
        }
        return extracted;
    }
}
