// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.examplelib;

import io.github._5thlayer.libworks.WorksTab;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Every item of the Module, in the suite's Works tab and no other (FactoryWorks ADR-0128). One
 * listener, in the order the tab shows them, which {@code CreativeTabTests} lists too.
 */
final class WorksTabContents {

    private WorksTabContents() {
    }

    static void add(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != WorksTab.KEY) {
            return;
        }
        // event.accept(MyItems.FIRST.get()), one line per item, in order.
    }
}
