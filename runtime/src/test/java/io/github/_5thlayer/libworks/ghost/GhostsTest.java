// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.ghost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/** Which candidate a ghost shows when, and which pixels hit it. The stacks are the game test's. */
class GhostsTest {

    private static final List<String> MEMBERS = List.of("a", "b", "c");

    @Test
    void pickShowsOneMemberAtATime() {
        assertEquals(Optional.of("a"), Ghosts.pick(MEMBERS, 0));
        assertEquals(Optional.of("a"), Ghosts.pick(MEMBERS, Ghosts.CYCLE_MILLIS - 1));
        assertEquals(Optional.of("b"), Ghosts.pick(MEMBERS, Ghosts.CYCLE_MILLIS));
        assertEquals(Optional.of("c"), Ghosts.pick(MEMBERS, 2 * Ghosts.CYCLE_MILLIS));
    }

    @Test
    void pickWrapsAround() {
        assertEquals(Optional.of("a"), Ghosts.pick(MEMBERS, 3 * Ghosts.CYCLE_MILLIS));
        assertEquals(Optional.of("b"), Ghosts.pick(MEMBERS, 1_000_000 * Ghosts.CYCLE_MILLIS));
        assertEquals(Optional.of("a"), Ghosts.pick(MEMBERS, Long.MAX_VALUE - Long.MAX_VALUE % (3 * Ghosts.CYCLE_MILLIS)));
    }

    @Test
    void pickOfNothingIsEmpty() {
        assertEquals(Optional.empty(), Ghosts.pick(List.of(), 0));
        assertEquals(Optional.empty(), Ghosts.pick(List.of(), 5 * Ghosts.CYCLE_MILLIS));
    }

    @Test
    void pickOfOneMemberAlwaysShowsIt() {
        assertEquals(Optional.of("a"), Ghosts.pick(List.of("a"), 123_456_789));
    }

    @Test
    void theBoxIsSixteenSquareFromItsCorner() {
        assertTrue(Ghosts.covers(10, 20, 10, 20));
        assertTrue(Ghosts.covers(10, 20, 25, 35));
        assertTrue(Ghosts.covers(10, 20, 25.9, 35.9));
        assertFalse(Ghosts.covers(10, 20, 26, 30));
        assertFalse(Ghosts.covers(10, 20, 20, 36));
        assertFalse(Ghosts.covers(10, 20, 9.9, 30));
        assertFalse(Ghosts.covers(10, 20, 20, 19.9));
    }

    @Test
    void cachedAsksOnce() {
        AtomicInteger asked = new AtomicInteger();
        var cached = Ghosts.cached(() -> {
            asked.incrementAndGet();
            return List.of();
        });
        assertSame(cached.get(), cached.get());
        assertEquals(1, asked.get());
    }
}
