// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github._5thlayer.libworks.testkit.GalleryCheck.Layout;
import io.github._5thlayer.libworks.testkit.GalleryCheck.PlotSize;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

class GalleryCheckLayoutTest {

    private static final BlockPos ORIGIN = new BlockPos(100, 64, 200);
    private static final Layout THREES = new Layout(3, 16);
    /** Railworks' plots: 40 by 15, reaching a block west and two east past their ends. */
    private static final PlotSize WIDE = new PlotSize(40, 15, 1, 2);
    private static final Layout WIDE_THREES = new Layout(3, 16, WIDE);

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

    @Test
    void theDefaultSizeIsTodaysPlot() {
        assertEquals(PlotSize.DEFAULT, Layout.ONE_ROW.size());
        assertEquals(PlotSize.DEFAULT, THREES.size());
        assertEquals(new BoundingBox(102, 63, 188, 125, 79, 200), GalleryCheck.plot(ORIGIN));
        assertEquals(GalleryCheck.plot(ORIGIN), THREES.plot(ORIGIN));
    }

    @Test
    void aDeclaredPlotReachesPastItsEnds() {
        assertEquals(new BoundingBox(101, 63, 186, 143, 79, 200), WIDE_THREES.plot(ORIGIN));
    }

    @Test
    void aRowsPlotsAreTheirWidthAndReachApart() {
        assertEquals(43, WIDE.spacing());
        assertEquals(ORIGIN.east(86), WIDE_THREES.sceneOrigin(ORIGIN, 2));
        assertEquals(ORIGIN.north(16).east(43), WIDE_THREES.sceneOrigin(ORIGIN, 4));
        assertEquals(ORIGIN.east(5 * 43), Layout.oneRow(WIDE).sceneOrigin(ORIGIN, 5));
    }

    @Test
    void neighbouringPlotsMeetWithoutOverlapping() {
        var first = WIDE_THREES.plot(WIDE_THREES.sceneOrigin(ORIGIN, 0));
        var second = WIDE_THREES.plot(WIDE_THREES.sceneOrigin(ORIGIN, 1));
        assertEquals(first.maxX() + 1, second.minX());
    }

    @Test
    void theAreaCoversEveryDeclaredPlotAndItsReach() {
        var area = WIDE_THREES.area(ORIGIN, 4);
        for (int scene = 0; scene < 4; scene++) {
            var plot = WIDE_THREES.plot(WIDE_THREES.sceneOrigin(ORIGIN, scene));
            assertTrue(contains(area, plot), "scene " + scene + "'s plot " + plot + " is outside " + area);
        }
        assertEquals(101, area.minX());
        assertEquals(100 + 86 + 2 + 40 - 1 + 2, area.maxX());
        assertEquals(200 - 16 - 15 + 1, area.minZ());
    }

    @Test
    void rowsCloserThanADeclaredPlotIsDeepAreRefused() {
        new Layout(3, 15, WIDE);
        var refused = assertThrows(IllegalArgumentException.class, () -> new Layout(3, 14, WIDE));
        assertTrue(refused.getMessage().contains("15 deep"), refused.getMessage());
    }

    @Test
    void aPlotOfNoSizeOrReachingBackwardsIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new PlotSize(0, 13, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PlotSize(24, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PlotSize(24, 13, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new PlotSize(24, 13, 0, -1));
    }

    @Test
    void aPlotReachesWestNoFurtherThanTheOriginsColumn() {
        assertEquals(ORIGIN.getX() + 1, new PlotSize(24, 13, GalleryCheck.FIRST_PLOT_EAST - 1, 0).plotAt(ORIGIN).minX());
        assertThrows(IllegalArgumentException.class, () -> new PlotSize(24, 13, GalleryCheck.FIRST_PLOT_EAST, 0));
    }

    private static boolean contains(BoundingBox outer, BoundingBox inner) {
        return outer.isInside(inner.minX(), inner.minY(), inner.minZ()) && outer.isInside(inner.maxX(), inner.maxY(), inner.maxZ());
    }
}
