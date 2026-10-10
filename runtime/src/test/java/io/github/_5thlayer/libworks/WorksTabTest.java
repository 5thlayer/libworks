// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WorksTabTest {

    @Test
    void keyIsLibworksWorks() {
        assertEquals("libworks:works", WorksTab.KEY.identifier().toString());
    }

    @Test
    void titleKeyFollowsTheTabId() {
        assertEquals("itemGroup.libworks.works", WorksTab.TITLE_KEY);
    }
}
