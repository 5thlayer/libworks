// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.gametest;

import java.util.List;
import java.util.function.Function;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.libworks.Libworks;
import io.github._5thlayer.libworks.ghost.Ghost;
import io.github._5thlayer.libworks.ghost.Ghosts;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * The ghost helper's game tests, from the {@code gametest} source set: what a filtered slot
 * accepts, that an ingredient's count reaches its ghost, and that a slot holding an item shows
 * none. They need the item registry, which a plain JVM test has not.
 */
@EventBusSubscriber(modid = Libworks.MOD_ID)
public final class GhostGameTests {

    private static final MapCodec<Accepted> ACCEPTED = codec(Accepted::new);
    private static final MapCodec<Counted> COUNTED = codec(Counted::new);
    private static final MapCodec<Occupied> OCCUPIED = codec(Occupied::new);

    private GhostGameTests() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Libworks.MOD_ID, path);
    }

    private static <T extends Base> MapCodec<T> codec(Function<TestData<Holder<TestEnvironmentDefinition<?>>>, T> make) {
        return TestData.CODEC.xmap(make, Base::data);
    }

    @SubscribeEvent
    static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, id("ghost_accepted"), () -> ACCEPTED);
        event.register(Registries.TEST_INSTANCE_TYPE, id("ghost_counted"), () -> COUNTED);
        event.register(Registries.TEST_INSTANCE_TYPE, id("ghost_occupied"), () -> OCCUPIED);
    }

    @SubscribeEvent
    static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(id("ghosts"), new TestEnvironmentDefinition.AllOf(List.of()));
        var data = new TestData<>(environment, id("gametest/platform"), 1, 0, true, Rotation.NONE);
        event.registerTest(id("ghost_accepts_what_the_slot_takes"), new Accepted(data));
        event.registerTest(id("ghost_of_ingredient_carries_count"), new Counted(data));
        event.registerTest(id("ghost_hidden_over_an_item"), new Occupied(data));
    }

    /** A slot at (7, 9) that takes only coal. */
    private static Slot coalSlot(SimpleContainer container) {
        return new Slot(container, 0, 7, 9) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.COAL);
            }
        };
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

    private static final class Accepted extends Base {

        Accepted(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info, ACCEPTED, "libworks ghost accepted");
        }

        @Override
        void check(GameTestHelper helper) {
            List<ItemStack> accepted = Ghosts.accepted(coalSlot(new SimpleContainer(1)));
            if (accepted.size() != 1 || !accepted.getFirst().is(Items.COAL)) {
                helper.fail("a coal-only slot should accept exactly coal, got " + accepted);
            }
        }
    }

    private static final class Counted extends Base {

        Counted(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info, COUNTED, "libworks ghost count");
        }

        @Override
        void check(GameTestHelper helper) {
            var ingredient = SizedIngredient.of(Items.COAL, 3);
            List<ItemStack> members = Ghosts.of(ingredient);
            if (members.size() != 1 || !members.getFirst().is(Items.COAL) || members.getFirst().getCount() != 3) {
                helper.fail("the ingredient's ghost should be 3 coal, got " + members);
            }
            var ghosts = new Ghosts();
            ghosts.add(20, 30, () -> members); // corner 0: screen coordinates
            List<Ghost> shown = ghosts.shown(0);
            if (shown.size() != 1 || shown.getFirst().stack().getCount() != 3 || shown.getFirst().x() != 20) {
                helper.fail("the registered ghost should show 3 coal at x 20, got " + shown);
            }
            if (ghosts.at(35.5, 45, 0).isEmpty() || ghosts.at(36, 45, 0).isPresent()) {
                helper.fail("the ghost's 16x16 box should end at x 36");
            }
        }
    }

    private static final class Occupied extends Base {

        Occupied(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info, OCCUPIED, "libworks ghost occupied");
        }

        @Override
        void check(GameTestHelper helper) {
            var container = new SimpleContainer(1);
            var slot = coalSlot(container);
            int[] corner = {100, 50};
            var ghosts = new Ghosts(() -> corner[0], () -> corner[1]);
            ghosts.add(slot);
            List<Ghost> empty = ghosts.shown(0);
            if (empty.size() != 1 || empty.getFirst().x() != 107 || empty.getFirst().y() != 59) {
                helper.fail("an empty slot should show its ghost at the slot plus the corner, got " + empty);
            }
            corner[0] = 200;
            if (ghosts.shown(0).getFirst().x() != 207) {
                helper.fail("the ghost should follow the screen when its corner moves");
            }
            ghosts.add(3, 4, () -> List.of(new ItemStack(Items.COAL)));
            corner[1] = 70;
            Ghost icon = ghosts.shown(0).getLast();
            if (icon.x() != 203 || icon.y() != 74) {
                helper.fail("a position not in a slot should stand from the corner too, got " + icon);
            }
            container.setItem(0, new ItemStack(Items.COAL));
            if (ghosts.shown(0).size() != 1 || ghosts.at(222, 94, 0).isPresent()) {
                helper.fail("a slot holding an item should show no ghost, only the icon");
            }
        }
    }
}
