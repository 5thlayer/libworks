// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.examplelib.gametest;

import java.util.List;

import io.github._5thlayer.examplelib.ExampleLib;
import io.github._5thlayer.libworks.testkit.WorksTabCheck;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

/**
 * Every item of the Module is in the suite's Works tab, in {@code WorksTabContents}' order, and in no
 * other tab. Until the Module has an item there is nothing to check, so it checks that it has none:
 * the first item fails that test until it is listed here.
 */
final class CreativeTabTests {

    /** The paths of the Module's items, as the Works tab shows them. */
    private static final List<String> WORKS_TAB_ORDER = List.of();

    private CreativeTabTests() {
    }

    static void register(ExampleLibGameTests.Registrar tests) {
        if (WORKS_TAB_ORDER.isEmpty()) {
            tests.test("registers_no_item_until_the_works_tab_lists_one", 1, CreativeTabTests::noItem);
            return;
        }
        tests.test("works_tab_holds_every_item_in_order", 1,
                helper -> WorksTabCheck.inOrder(helper, ExampleLib.MOD_ID, WORKS_TAB_ORDER));
        tests.test("no_other_tab_holds_a_module_item", 1, helper -> WorksTabCheck.noOtherTab(helper, ExampleLib.MOD_ID));
    }

    private static void noItem(GameTestHelper helper) {
        var items = BuiltInRegistries.ITEM.keySet().stream()
                .filter(id -> id.getNamespace().equals(ExampleLib.MOD_ID))
                .map(Identifier::getPath)
                .toList();
        if (!items.isEmpty()) {
            helper.fail("the Module registers " + items + ": list them in CreativeTabTests' WORKS_TAB_ORDER");
            return;
        }
        helper.succeed();
    }
}
