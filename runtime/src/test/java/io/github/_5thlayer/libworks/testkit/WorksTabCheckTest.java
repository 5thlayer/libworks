// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class WorksTabCheckTest {

    private static final Identifier TOOLS = Identifier.withDefaultNamespace("tools_and_utilities");

    @Test
    void theExpectedOrderPasses() {
        assertEquals(Optional.empty(), WorksTabCheck.orderFailure(List.of("gear", "belt"), List.of("gear", "belt")));
    }

    @Test
    void anotherOrderFailsNamingBoth() {
        assertEquals(Optional.of("the Works tab shows [belt, gear], not [gear, belt]"),
                WorksTabCheck.orderFailure(List.of("belt", "gear"), List.of("gear", "belt")));
    }

    @Test
    void expectingNothingFailsRatherThanPassOnAnEmptyTab() {
        assertTrue(WorksTabCheck.orderFailure(List.of(), List.of()).isPresent());
    }

    @Test
    void itemsOnlyInTheWorksTabPass() {
        assertEquals(Optional.empty(), WorksTabCheck.otherTabFailure("beltworks", List.of("belt"), Map.of(TOOLS, List.of())));
    }

    @Test
    void anEmptyWorksTabFails() {
        assertEquals(Optional.of("the Works tab shows no beltworks item, so no tab was filled"),
                WorksTabCheck.otherTabFailure("beltworks", List.of(), Map.of(TOOLS, List.of())));
    }

    @Test
    void anotherTabShowingAnItemFailsNamingIt() {
        var others = new LinkedHashMap<Identifier, List<String>>();
        others.put(Identifier.withDefaultNamespace("building_blocks"), List.of());
        others.put(TOOLS, List.of("belt"));
        assertEquals(Optional.of("the tab minecraft:tools_and_utilities shows [belt]"),
                WorksTabCheck.otherTabFailure("beltworks", List.of("belt"), others));
    }
}
