// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.gametest;

import java.util.List;
import java.util.function.Function;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.libworks.Libworks;
import io.github._5thlayer.libworks.machine.FluidContainers;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * Drain and fill slots' helper (#21), on vanilla's buckets, which carry NeoForge's fluid item capability as any
 * mod's container does: a container moves whole or not at all, and its slot checks.
 */
@EventBusSubscriber(modid = Libworks.MOD_ID)
public final class FluidContainerGameTests {

    private static final MapCodec<Drains> DRAINS = codec(Drains::new);
    private static final MapCodec<Fills> FILLS = codec(Fills::new);
    private static final MapCodec<Slots> SLOTS = codec(Slots::new);

    private FluidContainerGameTests() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Libworks.MOD_ID, path);
    }

    private static <T extends Base> MapCodec<T> codec(Function<TestData<Holder<TestEnvironmentDefinition<?>>>, T> make) {
        return TestData.CODEC.xmap(make, Base::data);
    }

    @SubscribeEvent
    static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, id("fluid_container_drains"), () -> DRAINS);
        event.register(Registries.TEST_INSTANCE_TYPE, id("fluid_container_fills"), () -> FILLS);
        event.register(Registries.TEST_INSTANCE_TYPE, id("fluid_container_slots"), () -> SLOTS);
    }

    @SubscribeEvent
    static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(id("fluid_containers"), new TestEnvironmentDefinition.AllOf(List.of()));
        var data = new TestData<>(environment, id("gametest/platform"), 1, 0, true, Rotation.NONE);
        event.registerTest(id("fluid_container_drains_whole_or_not_at_all"), new Drains(data));
        event.registerTest(id("fluid_container_fills_whole_or_not_at_all"), new Fills(data));
        event.registerTest(id("fluid_container_slot_checks"), new Slots(data));
    }

    /** Water, made when a test runs: a resource can't be made while the mods load. */
    private static FluidResource water() {
        return FluidResource.of(Fluids.WATER);
    }

    private static FluidStacksResourceHandler tank(int capacity, int water) {
        var tank = new FluidStacksResourceHandler(1, capacity);
        if (water > 0) {
            tank.set(0, water(), water);
        }
        return tank;
    }

    private abstract static class Base extends GameTestInstance {

        private final MapCodec<? extends GameTestInstance> codec;
        private final String description;

        Base(TestData<Holder<TestEnvironmentDefinition<?>>> info, MapCodec<? extends GameTestInstance> codec, String description) {
            super(info);
            this.codec = codec;
            this.description = description;
        }

        TestData<Holder<TestEnvironmentDefinition<?>>> data() {
            return info();
        }

        abstract void check(GameTestHelper helper);

        @Override
        public void run(GameTestHelper helper) {
            check(helper);
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return codec;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal(description);
        }
    }

    private static final class Drains extends Base {

        Drains(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info, DRAINS, "libworks fluid container drains");
        }

        @Override
        void check(GameTestHelper helper) {
            var tank = tank(4000, 0);
            ItemStack output = FluidContainers.drain(new ItemStack(Items.WATER_BUCKET), ItemStack.EMPTY, tank);
            helper.assertTrue(output != null && output.is(Items.BUCKET) && output.getCount() == 1,
                    "a water bucket should drain to an empty bucket, got " + output);
            helper.assertTrue(tank.getAmountAsInt(0) == 1000, "the tank should take 1000 mB, holds " + tank.getAmountAsInt(0));

            ItemStack stacked = FluidContainers.drain(new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.BUCKET, 3), tank);
            helper.assertTrue(stacked != null && stacked.is(Items.BUCKET) && stacked.getCount() == 4,
                    "the empty bucket should stack on the output's, got " + stacked);

            var full = tank(4000, 3500);
            helper.assertTrue(FluidContainers.drain(new ItemStack(Items.WATER_BUCKET), ItemStack.EMPTY, full) == null,
                    "a bucket should wait while the tank has room for only part of it");
            helper.assertTrue(full.getAmountAsInt(0) == 3500, "a refused drain should leave the tank, holds " + full.getAmountAsInt(0));

            var room = tank(4000, 0);
            helper.assertTrue(FluidContainers.drain(new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.COBBLESTONE), room) == null,
                    "a bucket should wait while the output holds something else");
            helper.assertTrue(room.getAmountAsInt(0) == 0, "a refused drain should leave the tank empty");

            helper.assertTrue(FluidContainers.drain(new ItemStack(Items.COBBLESTONE), ItemStack.EMPTY, room) == null,
                    "an item with no fluid capability should not drain");
            helper.assertTrue(FluidContainers.drain(ItemStack.EMPTY, ItemStack.EMPTY, room) == null, "an empty slot should not drain");
        }
    }

    private static final class Fills extends Base {

        Fills(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info, FILLS, "libworks fluid container fills");
        }

        @Override
        void check(GameTestHelper helper) {
            var tank = tank(4000, 2500);
            ItemStack output = FluidContainers.fill(new ItemStack(Items.BUCKET, 16), ItemStack.EMPTY, tank, water());
            helper.assertTrue(output != null && output.is(Items.WATER_BUCKET) && output.getCount() == 1,
                    "one empty bucket should fill to a water bucket, got " + output);
            helper.assertTrue(tank.getAmountAsInt(0) == 1500, "the tank should give 1000 mB, holds " + tank.getAmountAsInt(0));

            var low = tank(4000, 500);
            helper.assertTrue(FluidContainers.fill(new ItemStack(Items.BUCKET), ItemStack.EMPTY, low, water()) == null,
                    "a bucket should wait while the tank holds too little to fill it");
            helper.assertTrue(low.getAmountAsInt(0) == 500, "a refused fill should leave the tank, holds " + low.getAmountAsInt(0));

            var enough = tank(4000, 2000);
            helper.assertTrue(FluidContainers.fill(new ItemStack(Items.BUCKET), new ItemStack(Items.WATER_BUCKET), enough, water()) == null,
                    "a filled bucket should wait while the output holds one, since water buckets do not stack");
            helper.assertTrue(enough.getAmountAsInt(0) == 2000, "a refused fill should leave the tank");

            var lava = new FluidStacksResourceHandler(1, 4000);
            lava.set(0, FluidResource.of(Fluids.LAVA), 2000);
            helper.assertTrue(FluidContainers.fill(new ItemStack(Items.BUCKET), ItemStack.EMPTY, lava, water()) == null,
                    "a fill of water should take nothing from a tank of lava");
        }
    }

    private static final class Slots extends Base {

        Slots(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info, SLOTS, "libworks fluid container slots");
        }

        @Override
        void check(GameTestHelper helper) {
            helper.assertTrue(FluidContainers.holds(new ItemStack(Items.WATER_BUCKET), water()::equals), "a water bucket holds water");
            helper.assertTrue(!FluidContainers.holds(new ItemStack(Items.LAVA_BUCKET), water()::equals), "a lava bucket holds no water");
            helper.assertTrue(!FluidContainers.holds(new ItemStack(Items.BUCKET), fluid -> true), "an empty bucket holds nothing");
            helper.assertTrue(!FluidContainers.holds(new ItemStack(Items.COBBLESTONE), fluid -> true), "cobblestone is no container");

            helper.assertTrue(FluidContainers.takes(new ItemStack(Items.BUCKET), water()), "an empty bucket takes water");
            helper.assertTrue(!FluidContainers.takes(new ItemStack(Items.WATER_BUCKET), water()), "a full bucket takes nothing");
            helper.assertTrue(!FluidContainers.takes(new ItemStack(Items.COBBLESTONE), water()), "cobblestone is no container");
            helper.assertTrue(!FluidContainers.takes(ItemStack.EMPTY, water()), "an empty slot takes nothing");
        }
    }
}
