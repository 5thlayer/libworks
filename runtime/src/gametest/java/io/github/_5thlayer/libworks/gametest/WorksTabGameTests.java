// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.gametest;

import java.util.List;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.libworks.Libworks;
import io.github._5thlayer.libworks.WorksTab;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
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
 * The runtime's own game test, from the {@code gametest} source set: it is not in the jar. It
 * checks the Works tab is registered under {@link WorksTab#KEY}.
 */
@EventBusSubscriber(modid = Libworks.MOD_ID)
public final class WorksTabGameTests {

    private static final Identifier ID = Identifier.fromNamespaceAndPath(Libworks.MOD_ID, "works_tab_registered");

    private static final MapCodec<WorksTabTest> CODEC = TestData.CODEC.xmap(WorksTabTest::new, WorksTabTest::data);

    private WorksTabGameTests() {
    }

    @SubscribeEvent
    static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, Identifier.fromNamespaceAndPath(Libworks.MOD_ID, "works_tab"), () -> CODEC);
    }

    @SubscribeEvent
    static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(Libworks.MOD_ID, "default"), new TestEnvironmentDefinition.AllOf(List.of()));
        var platform = Identifier.fromNamespaceAndPath(Libworks.MOD_ID, "gametest/platform");
        event.registerTest(ID, new WorksTabTest(new TestData<>(environment, platform, 1, 0, true, Rotation.NONE)));
    }

    private static final class WorksTabTest extends GameTestInstance {

        WorksTabTest(TestData<net.minecraft.core.Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        TestData<net.minecraft.core.Holder<TestEnvironmentDefinition<?>>> data() {
            return info();
        }

        @Override
        public void run(GameTestHelper helper) {
            var tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(WorksTab.KEY);
            if (tab.isEmpty()) {
                helper.fail("the creative tab " + WorksTab.KEY.identifier() + " is not registered");
                return;
            }
            var title = tab.get().value().getDisplayName();
            if (!(title.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents key)
                    || !key.getKey().equals(WorksTab.TITLE_KEY)) {
                helper.fail("the tab's title is not " + WorksTab.TITLE_KEY + ": " + title);
                return;
            }
            helper.succeed();
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("libworks works tab");
        }
    }
}
