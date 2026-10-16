// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github._5thlayer.libworks.testkit.GalleryCheck.Layout;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

class GalleryCheckLayoutTest {

    private static final BlockPos ORIGIN = new BlockPos(100, 64, 200);
    private static final Layout THREES = new Layout(3, 16);

    @Test
    void oneRowLaysEveryScenePlotEastOfTheLast() {
        assertEquals(ORIGIN.east(5 * GalleryCheck.PLOT_WIDTH), Layout.ONE_ROW.sceneOrigin(ORIGIN, 5));
        assertEquals(ORIGIN.east(5 * GalleryCheck.PLOT_WIDTH), GalleryCheck.sceneOrigin(ORIGIN, 5));
    }

    @Test
    void oneRowHoldsTheLargestGallery() {
        int last = GalleryCheck.MAX_SCENES - 1;
        assertEquals(ORIGIN.east(last * GalleryCheck.PLOT_WIDTH), Layout.ONE_ROW.sceneOrigin(ORIGIN, last));
    }

    @Test
    void rowsStartAgainAtTheWestEndFurtherNorth() {
        assertEquals(ORIGIN.east(2 * GalleryCheck.PLOT_WIDTH), THREES.sceneOrigin(ORIGIN, 2));
        assertEquals(ORIGIN.north(16), THREES.sceneOrigin(ORIGIN, 3));
        assertEquals(ORIGIN.north(32).east(GalleryCheck.PLOT_WIDTH), THREES.sceneOrigin(ORIGIN, 7));
    }

    @Test
    void theAreaCoversEveryPlotOfAFullAndAPartRow() {
        var area = THREES.area(ORIGIN, 4);
        for (int scene = 0; scene < 4; scene++) {
            var plot = GalleryCheck.plot(THREES.sceneOrigin(ORIGIN, scene));
            assertTrue(contains(area, plot), "scene " + scene + "'s plot " + plot + " is outside " + area);
        }
        assertEquals(GalleryCheck.plot(ORIGIN).minX(), area.minX());
        assertEquals(GalleryCheck.plot(ORIGIN.east(2 * GalleryCheck.PLOT_WIDTH)).maxX(), area.maxX());
        assertEquals(GalleryCheck.plot(ORIGIN.north(16)).minZ(), area.minZ());
        assertEquals(ORIGIN.getZ(), area.maxZ());
    }

    @Test
    void oneRowsAreaIsTheStrip() {
        assertEquals(GalleryCheck.strip(ORIGIN, 9), Layout.ONE_ROW.area(ORIGIN, 9));
        assertEquals(GalleryCheck.plot(ORIGIN.east(8 * GalleryCheck.PLOT_WIDTH)).maxX(), Layout.ONE_ROW.area(ORIGIN, 9).maxX());
    }

    @Test
    void rowsCloserThanAPlotIsDeepAreRefused() {
        var refused = assertThrows(IllegalArgumentException.class, () -> new Layout(3, GalleryCheck.PLOT_DEPTH - 1));
        assertTrue(refused.getMessage().contains("overlap"), refused.getMessage());
    }

    @Test
    void aRowOfNoScenesIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Layout(0, 16));
    }

    private static boolean contains(BoundingBox outer, BoundingBox inner) {
        return outer.isInside(inner.minX(), inner.minY(), inner.minZ()) && outer.isInside(inner.maxX(), inner.maxY(), inner.maxZ());
    }
}
