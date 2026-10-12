// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.machine.jade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import io.github._5thlayer.libworks.machine.Tank;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.Test;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.ViewGroup;

/** How a machine's tanks become Jade's groups: one each, titled when the tank names a title. */
class MachineTanksTest {

    @Test
    void eachTankIsItsOwnGroup() {
        List<ViewGroup<FluidView.Data>> groups = MachineTanks.groups(List.of(
                new Tank("water", FluidStack.EMPTY, 4000, "wireworks.boiler.tank.water"),
                new Tank("steam", FluidStack.EMPTY, 8000, "wireworks.boiler.tank.steam")));

        assertEquals(2, groups.size());
        assertEquals("water", groups.get(0).id);
        assertEquals("steam", groups.get(1).id);
        assertEquals(1, groups.get(0).views.size());
        assertEquals(4000, groups.get(0).views.getFirst().capacity());
        assertEquals(8000, groups.get(1).views.getFirst().capacity());
    }

    @Test
    void aTitledTankCarriesItsTitleKey() {
        ViewGroup<FluidView.Data> group = MachineTanks.groups(List.of(
                new Tank("water", FluidStack.EMPTY, 4000, "wireworks.boiler.tank.water"))).getFirst();

        assertEquals("wireworks.boiler.tank.water", MachineTanks.titleKey(group).orElseThrow());
    }

    @Test
    void anUntitledTankHasNoTitle() {
        ViewGroup<FluidView.Data> group = MachineTanks.groups(List.of(
                new Tank("box_0", FluidStack.EMPTY, 1000, null))).getFirst();

        assertFalse(MachineTanks.titleKey(group).isPresent());
    }

    @Test
    void noTanksNoGroups() {
        assertTrue(MachineTanks.groups(List.of()).isEmpty());
    }
}
