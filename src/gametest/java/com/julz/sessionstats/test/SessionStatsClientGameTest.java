package com.julz.sessionstats.test;

import com.julz.sessionstats.client.HistoryScreen;
import com.julz.sessionstats.client.HudEditorScreen;
import com.julz.sessionstats.client.SessionController;
import com.julz.sessionstats.client.SessionStatsConfigScreen;
import com.julz.sessionstats.client.SummaryScreen;
import com.julz.sessionstats.config.ModConfig;
import com.julz.sessionstats.core.Session;
import com.julz.sessionstats.core.SessionRecorder;
import com.julz.sessionstats.core.StatKeys;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Records a session in a real singleplayer world. Stats are awarded on the server, as if the
 * player had mined, placed and fought, so the test covers the whole round trip: the stats request,
 * the server's answer, pausing, and the saved file.
 */
public class SessionStatsClientGameTest implements FabricClientGameTest {
    private static final String STONE = StatKeys.of(StatKeys.MINED, "minecraft:stone");
    private static final String DIAMOND = StatKeys.of(StatKeys.MINED, "minecraft:diamond_ore");
    private static final String PLACED_DIRT = StatKeys.of(StatKeys.PLACED, "minecraft:dirt");

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            ModConfig config = ModConfig.getInstance();
            config.autoStart = false;
            config.showHud = true;
            config.showSummaryOnStop = true;
        });
        context.setScreen(() -> new SessionStatsConfigScreen(null));
        context.waitForScreen(SessionStatsConfigScreen.class);
        context.takeScreenshot("session-stats-config-screen");
        context.setScreen(() -> null);

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getConnection().waitForChunksRender();

            // Stats from before the session must not count
            award(singleplayer, player -> player.awardStat(Stats.BLOCK_MINED.get(Blocks.STONE), 100));

            context.getInput().pressKey(options -> key(options, "key.session-stats.record"));
            waitForServer(context);
            assertEquals(SessionRecorder.State.RECORDING, state(context), "state after pressing the record key");

            award(singleplayer, player -> {
                player.awardStat(Stats.BLOCK_MINED.get(Blocks.STONE), 12);
                player.awardStat(Stats.BLOCK_MINED.get(Blocks.DIAMOND_ORE), 3);
                player.awardStat(Stats.ITEM_USED.get(Items.DIRT), 5);
                player.awardStat(Stats.ENTITY_KILLED.get(EntityType.ZOMBIE), 2);
                player.awardStat(Stats.CUSTOM.get(Stats.MOB_KILLS), 2);
                player.giveExperiencePoints(30);
            });
            context.waitTicks(5);
            context.takeScreenshot("session-stats-hud");

            // Paused: nothing counts
            context.getInput().pressKey(options -> key(options, "key.session-stats.pause"));
            waitForServer(context);
            assertEquals(SessionRecorder.State.PAUSED, state(context), "state after pressing the pause key");
            award(singleplayer, player -> player.awardStat(Stats.BLOCK_MINED.get(Blocks.STONE), 50));
            context.waitTicks(2);
            context.getInput().pressKey(options -> key(options, "key.session-stats.pause"));
            waitForServer(context);

            award(singleplayer, player -> player.awardStat(Stats.BLOCK_MINED.get(Blocks.STONE), 1));
            context.waitTicks(2);
            context.getInput().pressKey(options -> key(options, "key.session-stats.record"));
            context.waitFor(client -> !SessionController.recorder().isActive());

            Session session = context.computeOnClient(client -> SessionController.lastFinished());
            assertEquals(13L, session.stat(STONE), "stone mined");
            assertEquals(3L, session.stat(DIAMOND), "diamond ore mined");
            assertEquals(5L, session.stat(PLACED_DIRT), "dirt placed");
            assertEquals(2L, session.totals().mobKills(), "mob kills");
            assertEquals(30L, session.xp, "xp gained");
            assertEquals(3L, session.totals().oresMined(), "ores mined");

            // The summary opens on stop
            context.waitForScreen(SummaryScreen.class);
            context.takeScreenshot("session-stats-summary");

            // And the session is in the history
            List<Session> saved = context.computeOnClient(client -> {
                try {
                    return SessionController.store().load(SessionController.worldFolder());
                } catch (IOException e) {
                    throw new AssertionError("Could not read the saved sessions", e);
                }
            });
            assertEquals(1, saved.size(), "sessions saved");
            assertEquals(13L, saved.getFirst().stat(STONE), "stone mined in the saved file");
            context.setScreen(() -> new HistoryScreen(null));
            context.waitForScreen(HistoryScreen.class);
            context.takeScreenshot("session-stats-history");

            context.setScreen(() -> new HudEditorScreen(null));
            context.waitForScreen(HudEditorScreen.class);
            context.takeScreenshot("session-stats-hud-editor");
            context.setScreen(() -> null);
        }
    }

    private interface PlayerAction {
        void run(ServerPlayer player);
    }

    private static void award(TestSingleplayerContext singleplayer, PlayerAction action) {
        singleplayer.getServer().runOnServer(server -> action.run(server.getPlayerList().getPlayers().getFirst()));
    }

    private static void waitForServer(ClientGameTestContext context) {
        context.waitFor(client -> !SessionController.recorder().isWaitingForServer());
    }

    private static SessionRecorder.State state(ClientGameTestContext context) {
        return context.computeOnClient((Minecraft client) -> SessionController.recorder().state());
    }

    private static KeyMapping key(Options options, String name) {
        return Arrays.stream(options.keyMappings)
                .filter(mapping -> mapping.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Key mapping not registered: " + name));
    }

    private static void assertEquals(Object expected, Object actual, String what) {
        if (!expected.equals(actual)) {
            throw new AssertionError(what + ": expected " + expected + " but was " + actual);
        }
    }
}
