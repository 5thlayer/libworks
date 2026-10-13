// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.gametest;

import java.util.List;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.libworks.Libworks;
import io.github._5thlayer.libworks.testkit.WorksTabCheck;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * {@link WorksTabCheck} on the real tabs. The runtime adds no item, so both checks must fail for its
 * namespace: an empty Works tab never passes.
 */
@EventBusSubscriber(modid = Libworks.MOD_ID)
public final class WorksTabCheckGameTests {

    private static final MapCodec<EmptyTab> EMPTY_TAB = TestData.CODEC.xmap(EmptyTab::new, EmptyTab::data);

    private WorksTabCheckGameTests() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Libworks.MOD_ID, path);
    }

    @SubscribeEvent
    static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, id("works_tab_check"), () -> EMPTY_TAB);
    }

    @SubscribeEvent
    static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(id("works_tab_check"), new TestEnvironmentDefinition.AllOf(List.of()));
        var data = new TestData<>(environment, id("gametest/platform"), 1, 0, true, Rotation.NONE);
        event.registerTest(id("works_tab_check_fails_on_an_empty_tab"), new EmptyTab(data));
    }

    private static final class EmptyTab extends GameTestInstance {

        EmptyTab(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        TestData<Holder<TestEnvironmentDefinition<?>>> data() {
            return info();
        }

        @Override
        public void run(GameTestHelper helper) {
            if (passes(() -> WorksTabCheck.inOrder(helper, Libworks.MOD_ID, List.of("gear")))) {
                helper.fail("inOrder passed on a Works tab with no libworks item");
            }
            if (passes(() -> WorksTabCheck.noOtherTab(helper, Libworks.MOD_ID))) {
                helper.fail("noOtherTab passed on a Works tab with no libworks item");
            }
            helper.succeed();
        }

        private static boolean passes(Runnable check) {
            try {
                check.run();
                return true;
            } catch (GameTestAssertException expected) {
                return false;
            }
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return EMPTY_TAB;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("libworks works tab check");
        }
    }
}
