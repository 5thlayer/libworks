// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.gametest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github._5thlayer.libworks.Libworks;
import io.github._5thlayer.libworks.gallery.GalleryPack;
import io.github._5thlayer.libworks.testkit.GalleryCheck;
import io.github._5thlayer.libworks.testkit.GalleryCheck.Scene;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
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
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * {@link GalleryCheck} on the sample gallery, {@code gallery_pack/} here, which the dev client offers
 * too, and on the fixtures in {@code gametest_packs/gallery_fixture/}: a pack on by default, a build
 * whose command fails, and a scene placing a block that does not exist.
 */
@EventBusSubscriber(modid = Libworks.MOD_ID)
public final class GalleryCheckGameTests {

    private static final String FIXTURE = "gallery_fixture";
    private static final String FIXTURE_FOLDER = "gametest_packs/" + FIXTURE;
    private static final String FIXTURE_PACK = "mod/" + Libworks.MOD_ID + ":" + FIXTURE_FOLDER;
    /** Set on the game test server run only: the dev client offers the sample without the fixtures. */
    private static final boolean GAME_TEST_RUN = Boolean.getBoolean("libworks.gametest");

    private static final BlockPos ORIGIN = new BlockPos(0, 1, 8);
    private static final List<Scene> SAMPLE = List.of(
            new Scene("furnace", new BlockPos(13, 0, -6), Blocks.FURNACE),
            new Scene("hopper", new BlockPos(13, 0, -6), Blocks.HOPPER));

    private static final Map<Identifier, Consumer<GameTestHelper>> BODIES = new ConcurrentHashMap<>();
    private static final MapCodec<Check> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("body").forGetter(Check::id),
            TestData.CODEC.forGetter(Check::data)
    ).apply(instance, Check::new));

    private GalleryCheckGameTests() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Libworks.MOD_ID, path);
    }

    @SubscribeEvent
    static void addPackFinders(AddPackFindersEvent event) {
        GalleryPack.addTo(event, Libworks.MOD_ID);
        if (!GAME_TEST_RUN) {
            return;
        }
        event.addPackFinders(id(FIXTURE_FOLDER), PackType.SERVER_DATA, Component.literal(FIXTURE), PackSource.BUILT_IN, false, Pack.Position.TOP);
    }

    @SubscribeEvent
    static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, id("gallery_check"), () -> CODEC);
    }

    @SubscribeEvent
    static void registerTests(RegisterGameTestsEvent event) {
        var fixtures = event.registerEnvironment(id("gallery_check"), new TestEnvironmentDefinition.AllOf(List.of()));
        // Turning a pack on reloads the server's data under every test running with it (ADR 0010).
        var onItsOwn = event.registerEnvironment(id("gallery_check/sample"), new TestEnvironmentDefinition.AllOf(List.of()));
        register(event, onItsOwn, "gallery_check_passes_on_the_sample_gallery", GalleryCheck.TICKS,
                helper -> GalleryCheck.builds(helper, Libworks.MOD_ID, ORIGIN, SAMPLE));
        register(event, fixtures, "gallery_check_fails_on_a_missing_pack", 1,
                helper -> fails(helper, () -> GalleryCheck.assertOfferedAndOff(helper, GalleryPack.id("no_such_mod")), "does not offer"));
        register(event, fixtures, "gallery_check_fails_on_a_pack_on_by_default", 1,
                helper -> fails(helper, () -> GalleryCheck.assertOfferedAndOff(helper, FIXTURE_PACK), "on by default"));
        register(event, fixtures, "gallery_check_fails_on_a_failing_command_naming_it", 1,
                helper -> fails(helper, () -> GalleryCheck.assertRuns(helper, operator(helper), GalleryPack.build(FIXTURE)),
                        "`setblock ~ ~ ~ minecraft:gold_block replace`"));
        register(event, fixtures, "gallery_check_fails_on_a_scene_that_did_not_load_naming_its_line", 1,
                helper -> fails(helper, () -> GalleryCheck.assertLoads(helper, operator(helper), FIXTURE), FIXTURE + ":gallery/renamed: Whilst parsing command on line 2"));
        register(event, fixtures, "gallery_check_fails_on_a_key_block_out_of_place", 1, helper -> {
            var origin = helper.absolutePos(ORIGIN);
            helper.getLevel().setBlockAndUpdate(origin.offset(SAMPLE.getFirst().key()).east(), Blocks.FURNACE.defaultBlockState());
            fails(helper, () -> GalleryCheck.assertKeyBlocks(helper, origin, SAMPLE.subList(0, 1)), "scene furnace's");
        });
        register(event, fixtures, "gallery_check_fails_on_a_key_outside_its_plot", 1,
                helper -> fails(helper, () -> GalleryCheck.assertKeyBlocks(helper, helper.absolutePos(ORIGIN),
                        List.of(new Scene("furnace", new BlockPos(GalleryCheck.FIRST_PLOT_EAST + GalleryCheck.PLOT_WIDTH, 0, -6), Blocks.FURNACE))),
                        "outside its plot"));
    }

    private static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> batch, String name, int maxTicks,
            Consumer<GameTestHelper> body) {
        var id = id(name);
        BODIES.put(id, body);
        event.registerTest(id, new Check(id, new TestData<>(batch, id("gametest/platform"), maxTicks, 0, true, Rotation.NONE)));
    }

    private static void fails(GameTestHelper helper, Runnable check, String saying) {
        try {
            check.run();
        } catch (GameTestAssertException expected) {
            helper.assertTrue(expected.getMessage().contains(saying), "the check failed saying " + expected.getMessage() + ", not " + saying);
            helper.succeed();
            return;
        }
        helper.fail("the check passed");
    }

    private static CommandSourceStack operator(GameTestHelper helper) {
        return GalleryCheck.operator(helper, helper.absolutePos(new BlockPos(1, 1, 1)));
    }

    private static final class Check extends GameTestInstance {

        private final Identifier id;

        Check(Identifier id, TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
            this.id = id;
        }

        Identifier id() {
            return id;
        }

        TestData<Holder<TestEnvironmentDefinition<?>>> data() {
            return info();
        }

        @Override
        public void run(GameTestHelper helper) {
            BODIES.get(id).accept(helper);
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("libworks gallery check");
        }
    }
}
