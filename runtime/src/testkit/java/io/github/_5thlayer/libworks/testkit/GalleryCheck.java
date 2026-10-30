// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.testkit;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import com.mojang.authlib.GameProfile;
import io.github._5thlayer.libworks.gallery.GalleryPack;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.TraceCallbacks;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionLibrary;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * The Gallery pack game test every Module runs (ADR 0010): the pack is offered and off by default, its
 * build runs every command without failing, and each scene's key block stands where the scene puts
 * it. It looks at the world, never at how the functions are written. It ships in libworks-gametest,
 * which a Module runs its game tests with and never nests.
 *
 * <p>{@link #builds} and {@link #withPack} turn the pack on, which reloads the server's data under
 * every test running with it, so the Module registers each such test in a batch of its own.
 */
public final class GalleryCheck {

    /** A scene's plot unless its Module declares another size, along its row: from 2 blocks east of where it is run. */
    public static final int PLOT_WIDTH = 24;
    /** A scene's plot unless its Module declares another size, from its sign's row at the origin's z to 12 blocks north. */
    public static final int PLOT_DEPTH = 13;
    /** A scene's plot, above its stone floor. */
    public static final int PLOT_HEIGHT = 16;
    /** A scene's plot starts this many blocks east of where it is run. */
    public static final int FIRST_PLOT_EAST = 2;
    /**
     * The most scenes a gallery has, which bounds the plots the check force-loads and clears, with
     * room above the largest gallery. The gallery is built beside the test's structure, not in it.
     */
    public static final int MAX_SCENES = 24;

    /** Two reloads and the build, with room to spare. A Module gives its gallery test this many ticks. */
    public static final int TICKS = 1_200;

    private static final FileToIdConverter FUNCTIONS =
            new FileToIdConverter(Registries.elementsDirPath(ServerFunctionLibrary.TYPE_KEY), ".mcfunction");

    /**
     * One scene of a gallery, in the order {@code build} runs them: its function's name, under
     * {@code <modid>:gallery/}, and a block the scene places, at {@code key} from where the scene is run.
     */
    public record Scene(String name, BlockPos key, Block block) {
    }

    /**
     * The size of a scene's plot, {@link #PLOT_HEIGHT} high above its floor: {@code width} blocks along
     * its row from {@link #FIRST_PLOT_EAST} east of where the scene is run, {@code depth} blocks from
     * its sign's row at the origin's z north, and, past its west and east ends, the blocks the scene
     * reaches too, such as space its lines keep clear, which {@code gallery/clear} clears with the plot.
     * A row's plots are {@link #spacing} apart, so one plot's reach never lies over the next. The
     * reach west stops at the origin's column, where the player stands.
     */
    public record PlotSize(int width, int depth, int reachWest, int reachEast) {

        /** 24 wide by 13 deep, reaching nowhere past its ends: every plot unless its Module declares another. */
        public static final PlotSize DEFAULT = new PlotSize(PLOT_WIDTH, PLOT_DEPTH, 0, 0);

        public PlotSize {
            if (width < 1 || depth < 1) {
                throw new IllegalArgumentException("a plot is at least 1 by 1, not " + width + " by " + depth);
            }
            if (reachWest < 0 || reachWest > FIRST_PLOT_EAST - 1 || reachEast < 0) {
                throw new IllegalArgumentException("a plot reaches 0 to " + (FIRST_PLOT_EAST - 1) + " blocks west and 0 or more east, not "
                        + reachWest + " west and " + reachEast + " east");
            }
        }

        /** How far east of the last a row's next plot is: its width and its reach either way, so no two plots overlap. */
        public int spacing() {
            return width + reachWest + reachEast;
        }

        /** The plot of a scene run from {@code at}, its floor and its reach past either end included. */
        public BoundingBox at(BlockPos at) {
            return new BoundingBox(at.getX() + FIRST_PLOT_EAST - reachWest, at.getY() - 1, at.getZ() - depth + 1,
                    at.getX() + FIRST_PLOT_EAST + width - 1 + reachEast, at.getY() + PLOT_HEIGHT - 1, at.getZ());
        }
    }

    /**
     * How a gallery lays its scenes, in the order {@code build} runs them: {@code perRow} plots a row,
     * each {@code size.spacing()} east of the last, and each row {@code rowDepth} blocks north of the one
     * before. A row depth under {@code size.depth()} would lay a row's plots over the last's.
     */
    public record Layout(int perRow, int rowDepth, PlotSize size) {

        /** Every scene a plot east of the last, in one row: the layout {@code build} follows unless a Module says otherwise. */
        public static final Layout ONE_ROW = oneRow(PlotSize.DEFAULT);

        public Layout {
            if (perRow < 1) {
                throw new IllegalArgumentException("a row holds at least one scene, not " + perRow);
            }
            if (rowDepth < size.depth()) {
                throw new IllegalArgumentException("rows " + rowDepth + " blocks apart overlap, since a plot is " + size.depth() + " deep");
            }
        }

        /** Rows of {@link PlotSize#DEFAULT} plots. */
        public Layout(int perRow, int rowDepth) {
            this(perRow, rowDepth, PlotSize.DEFAULT);
        }

        /** Every scene a plot of {@code size} east of the last, in one row. */
        public static Layout oneRow(PlotSize size) {
            return new Layout(MAX_SCENES, size.depth(), size);
        }

        /** Where scene {@code index} of a gallery run from {@code origin} is run from. */
        public BlockPos sceneOrigin(BlockPos origin, int index) {
            return origin.north(rowDepth * (index / perRow)).east(size.spacing() * (index % perRow));
        }

        /** The plot of a scene run from {@code at}, its floor and its reach included. */
        public BoundingBox plot(BlockPos at) {
            return size.at(at);
        }

        /** The area the first {@code plots} plots of a gallery run from {@code origin} cover, their floors and reach included. */
        public BoundingBox area(BlockPos origin, int plots) {
            int lastRow = (plots - 1) / perRow;
            int columns = Math.min(plots, perRow);
            var farthest = origin.north(rowDepth * lastRow).east(size.spacing() * (columns - 1));
            return BoundingBox.encapsulatingBoxes(List.of(plot(origin), plot(farthest))).orElseThrow();
        }
    }

    private GalleryCheck() {
    }

    /**
     * Turns {@code modId}'s Gallery pack on, runs its build as an operator player standing at
     * {@code origin}, relative to the test, checks every scene, then clears the plots and turns the
     * pack off, passing or failing. Succeeds the test when every check holds.
     */
    public static void builds(GameTestHelper helper, String modId, BlockPos origin, List<Scene> scenes) {
        builds(helper, modId, origin, Layout.ONE_ROW, scenes);
    }

    /** As {@link #builds(GameTestHelper, String, BlockPos, List)}, for a gallery that lays its scenes by {@code layout}. */
    public static void builds(GameTestHelper helper, String modId, BlockPos origin, Layout layout, List<Scene> scenes) {
        helper.assertTrue(!scenes.isEmpty() && scenes.size() <= MAX_SCENES,
                "a gallery has 1 to " + MAX_SCENES + " scenes, not " + scenes.size());
        var server = helper.getLevel().getServer();
        withPack(helper, modId, origin, layout, scenes.size(), (source, steps) -> steps.thenExecute(() -> {
            assertLoads(helper, source, modId);
            for (var scene : scenes) {
                helper.assertTrue(server.getFunctions().get(GalleryPack.scene(modId, scene.name())).isPresent(),
                        "there is no scene function " + GalleryPack.scene(modId, scene.name()));
            }
            assertRuns(helper, source, GalleryPack.build(modId));
            assertKeyBlocks(helper, helper.absolutePos(origin), layout, scenes);
        })).thenSucceed();
    }

    /**
     * Turns {@code modId}'s Gallery pack on over the gallery's first {@code plots} plots, laid out by
     * {@code layout} from {@code origin}, relative to the test; once it is on and the plots loaded, runs
     * the test's own {@code steps}, which {@code steps} adds to the sequence it is given, with an
     * operator player's source standing at {@code origin} to build from; then clears the plots away and
     * turns the pack off, passing or failing. A scene run on its own from {@code origin} stands on the
     * first plot.
     *
     * <p>Clearing runs the pack's {@code <modid>:gallery/clear} from each plot when it has one, which
     * removes what the scene placed as a player's world would, then sets the plots to air with their
     * entities gone. The pack is turned on, which reloads the server's data under every test running
     * with it, so the test is registered in a batch of its own.
     *
     * @return the test's sequence, done once the pack is off: the caller ends it, as {@code .thenSucceed()}
     */
    public static GameTestSequence withPack(GameTestHelper helper, String modId, BlockPos origin, Layout layout, int plots,
            BiConsumer<CommandSourceStack, GameTestSequence> steps) {
        var server = helper.getLevel().getServer();
        var pack = GalleryPack.id(modId);
        assertOfferedAndOff(helper, pack);
        helper.assertTrue(plots >= 1 && plots <= MAX_SCENES, "a gallery has 1 to " + MAX_SCENES + " plots, not " + plots);
        var at = helper.absolutePos(origin);
        var run = new Run(helper.getLevel(), modId, at, layout, plots, operator(helper, at));
        forceLoad(helper.getLevel(), run.area());
        var on = select(server, pack, true);
        helper.testInfo.addListener(new ClearAwayIfFailed(run, on));
        var off = new AtomicReference<CompletableFuture<Void>>();
        var sequence = helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(on.isDone(), pack + " is not on yet"))
                .thenWaitUntil(() -> helper.assertTrue(loaded(helper.getLevel(), run.area()), "the gallery's plots are not loaded yet"));
        steps.accept(run.source(), sequence);
        return sequence
                .thenExecute(() -> off.set(run.clearAway()))
                .thenWaitUntil(() -> helper.assertTrue(off.get() != null && off.get().isDone(), pack + " is not off yet"));
    }

    /**
     * Turns {@code modId}'s Gallery pack on and proves its {@code <modid>:gallery/clear} empties the
     * first plot of a gallery laid out by {@code layout} from {@code origin}, relative to the test,
     * as {@link #assertClears} does, onto a stone floor. Succeeds the test when it does. Like
     * {@link #builds}, it turns the pack on, so the test is registered in a batch of its own.
     */
    public static void clears(GameTestHelper helper, String modId, BlockPos origin, Layout layout) {
        clears(helper, modId, origin, layout, Blocks.STONE);
    }

    /** As {@link #clears(GameTestHelper, String, BlockPos, Layout)}, for a gallery whose plots stand on {@code floor}. */
    public static void clears(GameTestHelper helper, String modId, BlockPos origin, Layout layout, Block floor) {
        withPack(helper, modId, origin, layout, 1, (source, steps) -> steps.thenExecute(
                () -> assertClears(helper, source, modId, layout.plot(helper.absolutePos(origin)), floor))).thenSucceed();
    }

    /**
     * Asserts {@code <modid>:gallery/clear}, run as {@code source}, empties {@code plot}, absolute,
     * the plot of a scene run from where {@code source} stands: with every block of it, its floor
     * too, set to glass, which is neither a floor nor air and drops nothing when broken, the function runs without failing and leaves {@code floor}
     * across the plot's bottom layer and air above it. A block left anywhere fails it, named with how
     * many more there are.
     */
    public static void assertClears(GameTestHelper helper, CommandSourceStack source, String modId, BoundingBox plot, Block floor) {
        var level = helper.getLevel();
        BlockPos.betweenClosed(plot.minX(), plot.minY(), plot.minZ(), plot.maxX(), plot.maxY(), plot.maxZ())
                .forEach(pos -> level.setBlock(pos, Blocks.GLASS.defaultBlockState(), Block.UPDATE_CLIENTS));
        var clear = GalleryPack.clear(modId);
        assertRuns(helper, source, clear);
        var left = new ArrayList<String>();
        for (var pos : BlockPos.betweenClosed(plot.minX(), plot.minY(), plot.minZ(), plot.maxX(), plot.maxY(), plot.maxZ())) {
            var expected = pos.getY() == plot.minY() ? floor : Blocks.AIR;
            var found = level.getBlockState(pos);
            if (!found.is(expected)) {
                left.add(found.getBlock() + " at " + pos.toShortString() + ", not " + expected);
            }
        }
        helper.assertTrue(left.isEmpty(), clear + " left " + (left.isEmpty() ? "" : left.getFirst())
                + (left.size() > 1 ? " and " + (left.size() - 1) + " more" : "") + " in its plot " + plot);
    }

    /**
     * Asserts the server offers {@code pack}, visible, and that it is off by default: neither required
     * nor added to a world on its own. It asks the pack, not whether it is on now, which a failed test
     * may have left it.
     */
    public static void assertOfferedAndOff(GameTestHelper helper, String pack) {
        var repository = helper.getLevel().getServer().getPackRepository();
        var offered = repository.getPack(pack);
        helper.assertTrue(offered != null && !offered.isHidden(), "the server does not offer " + pack + ": " + repository.getAvailableIds());
        helper.assertFalse(offered.isRequired() || offered.getPackSource().shouldAddAutomatically(), pack + " is on by default");
    }

    /**
     * Asserts every function under {@code <namespace>:gallery/} loaded. One that did not, such as one
     * placing a block that no longer exists, is named with the line that failed to parse.
     */
    public static void assertLoads(GameTestHelper helper, CommandSourceStack source, String namespace) {
        var failures = loadFailures(source, namespace);
        helper.assertTrue(failures.isEmpty(), "gallery functions failed to load: " + String.join("; ", failures));
    }

    /** Asserts {@code function}, run as {@code source}, runs every command it calls without failing. */
    public static void assertRuns(GameTestHelper helper, CommandSourceStack source, Identifier function) {
        var failures = failures(source, function);
        helper.assertTrue(failures.isEmpty(), function + " failed: " + String.join("; ", failures));
    }

    /** Asserts each scene's key block stands in its plot, the scenes' plots running east from {@code origin}, absolute. */
    public static void assertKeyBlocks(GameTestHelper helper, BlockPos origin, List<Scene> scenes) {
        assertKeyBlocks(helper, origin, Layout.ONE_ROW, scenes);
    }

    /** Asserts each scene's key block stands in its plot, the scenes' plots laid out by {@code layout} from {@code origin}, absolute. */
    public static void assertKeyBlocks(GameTestHelper helper, BlockPos origin, Layout layout, List<Scene> scenes) {
        for (int i = 0; i < scenes.size(); i++) {
            var scene = scenes.get(i);
            helper.assertTrue(layout.plot(BlockPos.ZERO).isInside(scene.key()),
                    "scene " + scene.name() + "'s key " + scene.key().toShortString() + " is outside its plot");
            var pos = layout.sceneOrigin(origin, i).offset(scene.key());
            var found = helper.getLevel().getBlockState(pos);
            helper.assertTrue(found.is(scene.block()),
                    "scene " + scene.name() + "'s " + scene.block() + " is not at " + pos.toShortString() + ", where stands " + found);
        }
    }

    /** Turns {@code pack} on or off, as {@code /datapack} does. @return the reload, done once the pack is on or off */
    public static CompletableFuture<Void> select(MinecraftServer server, String pack, boolean on) {
        var selected = new ArrayList<>(server.getPackRepository().getSelectedIds());
        selected.remove(pack);
        if (on) {
            selected.add(pack);
        }
        return server.reloadResources(selected);
    }

    /** An operator player of its own, standing at {@code feet}, absolute, whose source runs a gallery from there. */
    public static CommandSourceStack operator(GameTestHelper helper, BlockPos feet) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "gallery-visitor"));
        var at = Vec3.atBottomCenterOf(feet);
        player.setPos(at.x, at.y, at.z);
        return player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS).withPosition(at);
    }

    /** Where scene {@code index} of a gallery run from {@code origin} is run from: each a plot east of the last. */
    public static BlockPos sceneOrigin(BlockPos origin, int index) {
        return Layout.ONE_ROW.sceneOrigin(origin, index);
    }

    /** The {@link PlotSize#DEFAULT} plot of a scene run from {@code at}, its floor included. */
    public static BoundingBox plot(BlockPos at) {
        return PlotSize.DEFAULT.at(at);
    }

    /** Every plot of a gallery of {@code scenes} run from {@code origin}, their floors included. */
    public static BoundingBox strip(BlockPos origin, int scenes) {
        return Layout.ONE_ROW.area(origin, scenes);
    }

    /**
     * Runs {@code function} as {@code source}, traced as {@code /debug function} traces it, so a command
     * that fails anywhere in it, in a function it calls too, is named. A command fails only by throwing,
     * as vanilla's do: one that reports a failure and returns 0 passes unseen.
     *
     * @return each failing command with what it said, in the order they ran; one entry when there is no such function
     */
    static List<String> failures(CommandSourceStack source, Identifier function) {
        var functions = source.getServer().getFunctions();
        var found = functions.get(function);
        if (found.isEmpty()) {
            return List.of("there is no function " + function);
        }
        var failures = new ArrayList<String>();
        var tracer = new TraceCallbacks() {
            private String command = "";

            @Override
            public void onCommand(int depth, String command) {
                this.command = command;
            }

            @Override
            public void onReturn(int depth, String command, int result) {
            }

            @Override
            public void onError(String message) {
                failures.add("`" + command + "`: " + message);
            }

            @Override
            public void onCall(int depth, Identifier function, int size) {
            }

            @Override
            public void close() {
            }
        };
        Commands.executeCommandInContext(source, context -> {
            context.tracer(tracer);
            try {
                ExecutionContext.queueInitialFunctionCall(context, found.get().instantiate(null, functions.getDispatcher()), source, CommandResultCallback.EMPTY);
            } catch (FunctionInstantiationException e) {
                failures.add(e.messageComponent().getString());
            }
        });
        return failures;
    }

    // The server drops a function that fails to parse with only a log line, so it is parsed again here
    // to say why (ADR 0010).
    static List<String> loadFailures(CommandSourceStack source, String namespace) {
        var server = source.getServer();
        var failures = new ArrayList<String>();
        FUNCTIONS.listMatchingResources(server.getResourceManager()).forEach((file, resource) -> {
            var id = FUNCTIONS.fileToId(file);
            if (id.getNamespace().equals(namespace) && id.getPath().startsWith("gallery/") && server.getFunctions().get(id).isEmpty()) {
                failures.add(id + ": " + parseFailure(source, id, resource).orElse("not loaded"));
            }
        });
        return failures;
    }

    private static Optional<String> parseFailure(CommandSourceStack source, Identifier id, Resource resource) {
        try (BufferedReader reader = resource.openAsReader()) {
            CommandFunction.fromLines(id, source.getServer().getFunctions().getDispatcher(), source, reader.lines().toList());
            return Optional.empty();
        } catch (IOException | IllegalArgumentException e) {
            return Optional.of(e.getMessage());
        }
    }

    private static void forceLoad(ServerLevel level, BoundingBox box) {
        box.intersectingChunks().forEach(chunk -> level.setChunkForced(chunk.x(), chunk.z(), true));
    }

    private static boolean loaded(ServerLevel level, BoundingBox box) {
        return box.intersectingChunks().allMatch(chunk -> level.hasChunk(chunk.x(), chunk.z()));
    }

    /**
     * A test's run of a gallery: its first {@code plots} plots, laid out by {@code layout} from
     * {@code origin}, absolute, built by {@code source}, standing at {@code origin}.
     */
    private record Run(ServerLevel level, String modId, BlockPos origin, Layout layout, int plots, CommandSourceStack source) {

        BoundingBox area() {
            return layout.area(origin, plots);
        }

        /**
         * The pack's own clear on each plot, then air where the plots stood, their floors too, their
         * entities gone, and the pack off. The runner's clear sets blocks with no removal, which would
         * leave a Module's records of what it placed for the next test's scene to find. The runner
         * releases the forced chunks once the batch ends.
         *
         * @return the reload, done once the pack is off
         */
        CompletableFuture<Void> clearAway() {
            var server = level.getServer();
            var clear = GalleryPack.clear(modId);
            if (server.getFunctions().get(clear).isPresent()) {
                for (int plot = 0; plot < plots; plot++) {
                    // Prefixed, so a failing command is logged, never thrown: clearing away fails no test.
                    server.getCommands().performPrefixedCommand(
                            source.withPosition(Vec3.atBottomCenterOf(layout.sceneOrigin(origin, plot))), "function " + clear);
                }
            }
            StructureUtils.clearSpaceForStructure(area(), level);
            return select(server, GalleryPack.id(modId), false);
        }
    }

    /**
     * A failed test skips its own last steps, which would leave the plots standing and the pack on
     * under the batches after it (ADR 0010). It clears away only once turning the pack on is done,
     * since two reloads race.
     */
    private record ClearAwayIfFailed(Run run, CompletableFuture<Void> on) implements GameTestListener {

        @Override
        public void testStructureLoaded(GameTestInfo testInfo) {
        }

        @Override
        public void testPassed(GameTestInfo testInfo, GameTestRunner runner) {
        }

        @Override
        public void testFailed(GameTestInfo testInfo, GameTestRunner runner) {
            var server = run.level().getServer();
            on.whenComplete((done, failed) -> server.execute(run::clearAway));
        }

        @Override
        public void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner) {
        }
    }
}
