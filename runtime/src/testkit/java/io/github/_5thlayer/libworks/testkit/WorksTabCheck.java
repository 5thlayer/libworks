// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.testkit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github._5thlayer.libworks.WorksTab;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

/**
 * The Works tab game test every Module runs (#25): its items are in {@link WorksTab#KEY}, in the order
 * it adds them, and in no other tab. Each call rebuilds the tabs, then succeeds or fails the test. It
 * ships in libworks-gametest, which a Module runs its game tests with and never nests.
 */
public final class WorksTabCheck {

    private WorksTabCheck() {
    }

    /** The Works tab shows exactly {@code expected}, the paths of {@code namespace}'s items, in order. */
    public static void inOrder(GameTestHelper helper, String namespace, List<String> expected) {
        rebuild(helper);
        finish(helper, orderFailure(pathsIn(worksTab(), namespace), expected));
    }

    /** No tab but the Works tab and the search tab shows a {@code namespace} item, and the Works tab shows one. */
    public static void noOtherTab(GameTestHelper helper, String namespace) {
        rebuild(helper);
        var others = new LinkedHashMap<Identifier, List<String>>();
        for (var entry : BuiltInRegistries.CREATIVE_MODE_TAB.entrySet()) {
            // The search tab lists every other tab's items.
            if (!entry.getKey().equals(WorksTab.KEY) && entry.getValue().getType() != CreativeModeTab.Type.SEARCH) {
                others.put(entry.getKey().identifier(), pathsIn(entry.getValue(), namespace));
            }
        }
        finish(helper, otherTabFailure(namespace, pathsIn(worksTab(), namespace), others));
    }

    static Optional<String> orderFailure(List<String> shown, List<String> expected) {
        if (expected.isEmpty()) {
            return Optional.of("no item is expected in the Works tab, so the check would pass on an empty tab");
        }
        return shown.equals(expected) ? Optional.empty()
                : Optional.of("the Works tab shows " + shown + ", not " + expected);
    }

    static Optional<String> otherTabFailure(String namespace, List<String> works, Map<Identifier, List<String>> others) {
        // With no item in the Works tab, every other tab empty would pass without the items reaching any tab.
        if (works.isEmpty()) {
            return Optional.of("the Works tab shows no " + namespace + " item, so no tab was filled");
        }
        return others.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .findFirst()
                .map(e -> "the tab " + e.getKey() + " shows " + e.getValue());
    }

    private static void finish(GameTestHelper helper, Optional<String> failure) {
        if (failure.isPresent()) {
            helper.fail(failure.get());
            return;
        }
        helper.succeed();
    }

    private static void rebuild(GameTestHelper helper) {
        CreativeModeTabs.tryRebuildTabContents(helper.getLevel().enabledFeatures(), true,
                helper.getLevel().registryAccess());
    }

    private static CreativeModeTab worksTab() {
        return BuiltInRegistries.CREATIVE_MODE_TAB.getValueOrThrow(WorksTab.KEY);
    }

    private static List<String> pathsIn(CreativeModeTab tab, String namespace) {
        return tab.getDisplayItems().stream()
                .map(ItemStack::getItem)
                .map(BuiltInRegistries.ITEM::getKey)
                .filter(id -> id.getNamespace().equals(namespace))
                .map(Identifier::getPath)
                .toList();
    }
}
