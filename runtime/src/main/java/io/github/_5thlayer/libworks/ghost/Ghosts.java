// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.ghost;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * The ghosts of one screen: the faded items in its empty slots that show what goes there. A screen
 * owns one instance and registers each position once, with a supplier of the candidates it shows;
 * a position with several candidates cycles through them, one every {@link #CYCLE_MILLIS}. The
 * screen draws {@link #shown} (with {@code ghost.client.GhostDraw}) and its recipe-viewer glue asks
 * {@link #at} what is under the mouse.
 *
 * <p>No client type, so a game test reads it. The time is always passed in, never read, so a test
 * can pin the candidate shown. What a slot takes is the Module's to decide: this only shows it.
 */
public final class Ghosts {

    /** How long a position shows each of its candidates. */
    public static final long CYCLE_MILLIS = 1000;

    /** A ghost's side, in pixels. */
    public static final int SIZE = 16;

    private record Position(IntSupplier x, IntSupplier y, BooleanSupplier visible, Supplier<List<ItemStack>> candidates) {
    }

    private final IntSupplier left;
    private final IntSupplier top;
    private final List<Position> positions = new ArrayList<>();

    /** For a screen whose positions are already in screen coordinates: the corner is 0. */
    public Ghosts() {
        this(() -> 0, () -> 0);
    }

    /**
     * For a screen whose slots are relative to its panel. {@code left} and {@code top} are read each
     * time a slot's ghost is placed, so a screen that moves on resize passes {@code () -> leftPos}
     * and {@code () -> topPos} once, and the ghosts follow it.
     */
    public Ghosts(IntSupplier left, IntSupplier top) {
        this.left = left;
        this.top = top;
    }

    /**
     * Registers a ghost standing at ({@code x}, {@code y}) from the panel's corner, like a slot's, so it
     * follows the screen on resize. It is always shown while it has a candidate. With the corner at 0
     * these are screen coordinates.
     */
    public void add(int x, int y, Supplier<List<ItemStack>> candidates) {
        positions.add(new Position(() -> left.getAsInt() + x, () -> top.getAsInt() + y, () -> true, candidates));
    }

    /**
     * Registers a ghost in {@code slot}, placed at the slot's position plus the panel's corner. It shows
     * while the slot is active and empty, and never over an item.
     */
    public void add(Slot slot, Supplier<List<ItemStack>> candidates) {
        positions.add(new Position(() -> left.getAsInt() + slot.x, () -> top.getAsInt() + slot.y,
                () -> slot.isActive() && !slot.hasItem(), candidates));
    }

    /** Registers a ghost in {@code slot} for every item it {@linkplain #accepted accepts}, scanned once. */
    public void add(Slot slot) {
        add(slot, cached(() -> accepted(slot)));
    }

    /** Every ghost to draw at {@code millis}: each visible position's candidate for that moment. */
    public List<Ghost> shown(long millis) {
        List<Ghost> shown = new ArrayList<>();
        for (Position position : positions) {
            if (!position.visible().getAsBoolean()) {
                continue;
            }
            pick(position.candidates().get(), millis).filter(stack -> !stack.isEmpty())
                    .ifPresent(stack -> shown.add(new Ghost(stack, position.x().getAsInt(), position.y().getAsInt())));
        }
        return shown;
    }

    /** The ghost whose box holds ({@code mouseX}, {@code mouseY}) at {@code millis}, or empty. */
    public Optional<Ghost> at(double mouseX, double mouseY, long millis) {
        for (Ghost ghost : shown(millis)) {
            if (covers(ghost.x(), ghost.y(), mouseX, mouseY)) {
                return Optional.of(ghost);
            }
        }
        return Optional.empty();
    }

    /** Whether ({@code mouseX}, {@code mouseY}) is in the {@link #SIZE} box whose top-left is ({@code x}, {@code y}). */
    static boolean covers(int x, int y, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + SIZE && mouseY >= y && mouseY < y + SIZE;
    }

    /**
     * Every registered item {@code slot}'s {@code mayPlace} accepts, one stack of each, so a pack's own
     * items show too. It scans the whole item registry: {@link #cached} it, or use {@link #add(Slot)}.
     */
    public static List<ItemStack> accepted(Slot slot) {
        return BuiltInRegistries.ITEM.stream().map(ItemStack::new)
                .filter(stack -> !stack.isEmpty() && slot.mayPlace(stack)).toList();
    }

    /** The members of {@code ingredient}, each at the ingredient's count. */
    public static List<ItemStack> of(SizedIngredient ingredient) {
        return ingredient.ingredient().items().map(item -> new ItemStack(item, ingredient.count())).toList();
    }

    /** The member shown at {@code millis}, which changes every {@link #CYCLE_MILLIS}; empty for no members. */
    public static <T> Optional<T> pick(List<T> members, long millis) {
        if (members.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(members.get((int) (Math.floorMod(millis / CYCLE_MILLIS, (long) members.size()))));
    }

    /** {@code candidates}, asked once on first use and remembered, for a list that costs a scan like {@link #accepted}. */
    public static Supplier<List<ItemStack>> cached(Supplier<List<ItemStack>> candidates) {
        return new Supplier<>() {
            private List<ItemStack> list;

            @Override
            public List<ItemStack> get() {
                if (list == null) {
                    list = candidates.get();
                }
                return list;
            }
        };
    }
}
