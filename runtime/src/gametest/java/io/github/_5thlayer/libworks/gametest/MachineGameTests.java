// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.gametest;

import java.util.List;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.libworks.Libworks;
import io.github._5thlayer.libworks.machine.Tank;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * The machine helpers' game test, on a server with no Jade (ADR 0006): that the server starts with
 * {@code libworks.machine} in the jar, and that a {@link Tank}, which names no Jade type, is built and read
 * there as a Module's block entity builds it.
 */
@EventBusSubscriber(modid = Libworks.MOD_ID)
public final class MachineGameTests {

    private static final MapCodec<Tanked> TANKED = TestData.CODEC.xmap(Tanked::new, Tanked::data);

    private MachineGameTests() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Libworks.MOD_ID, path);
    }

    @SubscribeEvent
    static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, id("machine_tank"), () -> TANKED);
    }

    @SubscribeEvent
    static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(id("machines"), new TestEnvironmentDefinition.AllOf(List.of()));
        var data = new TestData<>(environment, id("gametest/platform"), 1, 0, true, Rotation.NONE);
        event.registerTest(id("machine_tank_without_jade"), new Tanked(data));
    }

    private static final class Tanked extends GameTestInstance {

        Tanked(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        TestData<Holder<TestEnvironmentDefinition<?>>> data() {
            return info();
        }

        @Override
        public void run(GameTestHelper helper) {
            var water = new Tank("water", new FluidStack(Fluids.WATER, 1000), 4000, "libworks.test.water");
            if (!water.fluid().is(Fluids.WATER) || water.fluid().getAmount() != 1000 || water.capacity() != 4000) {
                helper.fail("a tank should hold what it was built with, got " + water);
            }
            var box = new Tank("box_0", FluidStack.EMPTY, 1000, null);
            if (!box.fluid().isEmpty() || box.titleKey() != null) {
                helper.fail("an untitled empty tank should stay so, got " + box);
            }
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return TANKED;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("libworks machine tank");
        }
    }
}
