// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.neoforged.neoforge.transfer.DelegatingResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import org.junit.jupiter.api.Test;

/** A subclass's per-slot refusal holds for a caller that names no slot (ADR-0128). */
class GuardedResourceHandlerTest {

    record Coal() implements Resource {
        public boolean isEmpty() {
            return false;
        }
    }

    /** Slots of plain counts; changes apply at once, so a test never needs a rollback. */
    static final class Slots implements ResourceHandler<Coal> {
        final int[] amounts;

        Slots(int... amounts) {
            this.amounts = amounts;
        }

        public int size() {
            return amounts.length;
        }

        public Coal getResource(int index) {
            return new Coal();
        }

        public long getAmountAsLong(int index) {
            return amounts[index];
        }

        public long getCapacityAsLong(int index, Coal resource) {
            return 64;
        }

        public boolean isValid(int index, Coal resource) {
            return true;
        }

        public int insert(int index, Coal resource, int amount, TransactionContext transaction) {
            int moved = Math.min(amount, 64 - amounts[index]);
            amounts[index] += moved;
            return moved;
        }

        public int extract(int index, Coal resource, int amount, TransactionContext transaction) {
            int moved = Math.min(amount, amounts[index]);
            amounts[index] -= moved;
            return moved;
        }
    }

    /** Slot 0 is closed both ways; the others pass through. */
    static ResourceHandler<Coal> slotZeroClosed(ResourceHandler<Coal> delegate, boolean guarded) {
        return guarded ? new GuardedResourceHandler<>(delegate) {
            @Override
            public int insert(int index, Coal resource, int amount, TransactionContext tx) {
                return index == 0 ? 0 : super.insert(index, resource, amount, tx);
            }

            @Override
            public int extract(int index, Coal resource, int amount, TransactionContext tx) {
                return index == 0 ? 0 : super.extract(index, resource, amount, tx);
            }
        } : new DelegatingResourceHandler<>(delegate) {
            @Override
            public int insert(int index, Coal resource, int amount, TransactionContext tx) {
                return index == 0 ? 0 : super.insert(index, resource, amount, tx);
            }

            @Override
            public int extract(int index, Coal resource, int amount, TransactionContext tx) {
                return index == 0 ? 0 : super.extract(index, resource, amount, tx);
            }
        };
    }

    @Test
    void plainDelegatingHandlerLeaksSlotZero() {
        var slots = new Slots(10, 10);
        try (var tx = Transaction.openRoot()) {
            assertEquals(20, slotZeroClosed(slots, false).extract(new Coal(), 20, tx));
        }
    }

    @Test
    void slotlessExtractSkipsARefusedSlot() {
        var slots = new Slots(10, 10);
        try (var tx = Transaction.openRoot()) {
            assertEquals(10, slotZeroClosed(slots, true).extract(new Coal(), 20, tx));
        }
        assertEquals(10, slots.amounts[0]);
        assertEquals(0, slots.amounts[1]);
    }

    @Test
    void slotlessInsertSkipsARefusedSlot() {
        var slots = new Slots(0, 0);
        try (var tx = Transaction.openRoot()) {
            assertEquals(64, slotZeroClosed(slots, true).insert(new Coal(), 100, tx));
        }
        assertEquals(0, slots.amounts[0]);
        assertEquals(64, slots.amounts[1]);
    }

    @Test
    void aHandlerThatRefusesNothingMovesAsTheDelegateWould() {
        var guarded = new Slots(5, 5);
        var plain = new Slots(5, 5);
        try (var tx = Transaction.openRoot()) {
            assertEquals(plain.extract(new Coal(), 7, tx), new GuardedResourceHandler<>(guarded).extract(new Coal(), 7, tx));
            assertEquals(plain.insert(new Coal(), 130, tx), new GuardedResourceHandler<>(guarded).insert(new Coal(), 130, tx));
        }
        assertEquals(plain.amounts[0], guarded.amounts[0]);
        assertEquals(plain.amounts[1], guarded.amounts[1]);
    }
}
