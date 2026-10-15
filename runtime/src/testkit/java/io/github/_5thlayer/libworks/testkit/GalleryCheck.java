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
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionLibrary;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * The Gallery pack game test every Module runs (ADR 0010): the pack is offered and off by default, its
 * build runs every command without failing, and each scene's key block stands where the scene puts
 * it. It looks at the world, never at how the functions are written. It ships in libworks-gametest,
 * which a Module runs its game tests with and never nests.
 *
 * <p>{@link #builds} turns the pack on, which reloads the server's data under every test running
 * with it, so the Module registers the test in a batch of its own.
 */
public final class GalleryCheck {

    /** A scene's plot, along the strip: from 2 blocks east of where it is run. */
    public static final int PLOT_WIDTH = 24;
    /** A scene's plot, from its sign's row at the origin's z to 12 blocks north. */
    public static final int PLOT_DEPTH = 13;
    /** A scene's plot, above its stone floor. */
    public static final int PLOT_HEIGHT = 16;
    /** A scene's plot starts this many blocks east of where it is run. */
    public static final int FIRST_PLOT_EAST = 2;
    /**
     * The most scenes a gallery has, which bounds the strip the check force-loads and clears: the
     * largest README lists 9 features. The gallery is built beside the test's structure, not in it.
     */
    public static final int MAX_SCENES = 12;

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

    private GalleryCheck() {
    }

    /**
     * Turns {@code modId}'s Gallery pack on, runs its build as an operator player standing at
     * {@code origin}, relative to the test, checks every scene, then clears the strip and turns the
     * pack off, passing or failing. Succeeds the test when every check holds.
     */
    public static void builds(GameTestHelper helper, String modId, BlockPos origin, List<Scene> scenes) {
        var server = helper.getLevel().getServer();
        var pack = GalleryPack.id(modId);
        assertOfferedAndOff(helper, pack);
        helper.assertTrue(!scenes.isEmpty() && scenes.size() <= MAX_SCENES,
                "a gallery has 1 to " + MAX_SCENES + " scenes, not " + scenes.size());
        var at = helper.absolutePos(origin);
        var strip = strip(at, scenes.size());
        var source = operator(helper, at);
        forceLoad(helper.getLevel(), strip);
        var on = select(server, pack, true);
        helper.testInfo.addListener(new ClearAwayIfFailed(helper.getLevel(), strip, pack, on));
        var off = new AtomicReference<CompletableFuture<Void>>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(on.isDone(), pack + " is not on yet"))
                .thenWaitUntil(() -> helper.assertTrue(loaded(helper.getLevel(), strip), "the gallery's strip is not loaded yet"))
                .thenExecute(() -> {
                    assertLoads(helper, source, modId);
                    for (var scene : scenes) {
                        helper.assertTrue(server.getFunctions().get(GalleryPack.scene(modId, scene.name())).isPresent(),
                                "there is no scene function " + GalleryPack.scene(modId, scene.name()));
                    }
                    assertRuns(helper, source, GalleryPack.build(modId));
                    assertKeyBlocks(helper, at, scenes);
                    clear(helper.getLevel(), strip);
                    off.set(select(server, pack, false));
                })
                .thenWaitUntil(() -> helper.assertTrue(off.get() != null && off.get().isDone(), pack + " is not off yet"))
                .thenSucceed();
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
        for (int i = 0; i < scenes.size(); i++) {
            var scene = scenes.get(i);
            helper.assertTrue(plot(BlockPos.ZERO).isInside(scene.key()),
                    "scene " + scene.name() + "'s key " + scene.key().toShortString() + " is outside its plot");
            var pos = sceneOrigin(origin, i).offset(scene.key());
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
        return origin.east(index * PLOT_WIDTH);
    }

    /** The plot of a scene run from {@code at}, its floor included. */
    public static BoundingBox plot(BlockPos at) {
        return new BoundingBox(at.getX() + FIRST_PLOT_EAST, at.getY() - 1, at.getZ() - PLOT_DEPTH + 1,
                at.getX() + FIRST_PLOT_EAST + PLOT_WIDTH - 1, at.getY() + PLOT_HEIGHT - 1, at.getZ());
    }

    /** Every plot of a gallery of {@code scenes} run from {@code origin}, their floors included. */
    public static BoundingBox strip(BlockPos origin, int scenes) {
        return BoundingBox.encapsulatingBoxes(List.of(plot(origin), plot(sceneOrigin(origin, scenes - 1)))).orElseThrow();
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

    /** Air where the strip stood, its floor too, its entities gone. The runner releases the forced chunks once the batch ends. */
    private static void clear(ServerLevel level, BoundingBox strip) {
        StructureUtils.clearSpaceForStructure(strip, level);
    }

    /**
     * A failed test skips its own last steps, which would leave the pack on under the batches after it
     * (ADR 0010). It turns the pack off only once turning it on is done, since two reloads race.
     */
    private record ClearAwayIfFailed(ServerLevel level, BoundingBox strip, String pack, CompletableFuture<Void> on)
            implements GameTestListener {

        @Override
        public void testStructureLoaded(GameTestInfo testInfo) {
        }

        @Override
        public void testPassed(GameTestInfo testInfo, GameTestRunner runner) {
        }

        @Override
        public void testFailed(GameTestInfo testInfo, GameTestRunner runner) {
            clear(level, strip);
            var server = level.getServer();
            on.whenComplete((done, failed) -> server.execute(() -> select(server, pack, false)));
        }

        @Override
        public void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner) {
        }
    }
}
