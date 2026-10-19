package com.julz.sessionstats.client;

import com.julz.sessionstats.config.ModConfig;
import com.julz.sessionstats.core.Session;
import com.julz.sessionstats.core.SessionRecorder;
import com.julz.sessionstats.core.SessionStore;
import com.julz.sessionstats.core.StatKeys;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Connects the {@link SessionRecorder} to the game: asks the server for stats, feeds it the
 * answers and the player's movement, and saves sessions under {@code config/session-stats/}.
 */
public final class SessionController {
    private static final long AUTOSAVE_MILLIS = 60_000;
    /** Without the HUD there is nothing to keep live, so stats are only fetched for the autosave. */
    private static final long BACKGROUND_REFRESH_MILLIS = AUTOSAVE_MILLIS;
    /** Movement faster than this in one tick is a teleport, not distance travelled. */
    private static final double TELEPORT_BLOCKS = 10;

    private static final SessionStore STORE = new SessionStore(
            FabricLoader.getInstance().getConfigDir().resolve("session-stats"), ZoneId.systemDefault());
    private static final SessionRecorder RECORDER = new SessionRecorder(new SessionRecorder.Host() {
        @Override
        public void requestStats() {
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            if (connection != null) {
                connection.send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS));
            }
        }

        @Override
        public void finished(Session session) {
            onFinished(session);
        }
    });

    // Replaced by the game tests to drive time forward.
    private static LongSupplier clock = System::currentTimeMillis;

    private static String worldName = "";
    private static String worldFolder = "unknown";
    // The folder the running session is saved in; the world can change before it is saved.
    private static String sessionFolder = "unknown";
    private static boolean showSummaryWhenFinished = false;
    private static long lastSaveMillis = 0;
    private static Vec3 lastPosition = null;
    private static Session lastFinished = null;

    private SessionController() {
    }

    public static SessionRecorder recorder() {
        return RECORDER;
    }

    public static SessionStore store() {
        return STORE;
    }

    public static String worldFolder() {
        return worldFolder;
    }

    /** The session most recently stopped, e.g. for the game tests. */
    public static Session lastFinished() {
        return lastFinished;
    }

    public static void setClockForTesting(LongSupplier testClock) {
        clock = testClock != null ? testClock : System::currentTimeMillis;
    }

    public static void onJoin(Minecraft client) {
        RECORDER.clearKnownStats();
        lastPosition = null;
        identifyWorld(client);
        if (ModConfig.getInstance().autoStart) {
            start(client);
        }
    }

    public static void onDisconnect() {
        if (RECORDER.isActive()) {
            showSummaryWhenFinished = false;
            RECORDER.stopNow(clock.getAsLong());
        }
        lastPosition = null;
    }

    public static void toggleRecording(Minecraft client) {
        if (RECORDER.isActive()) {
            stop(client);
        } else {
            start(client);
        }
    }

    public static void togglePause(Minecraft client) {
        long now = clock.getAsLong();
        switch (RECORDER.state()) {
            case RECORDING -> {
                RECORDER.pause(now);
                message(client, "message.session-stats.paused");
            }
            case PAUSED -> {
                RECORDER.resume(now);
                message(client, "message.session-stats.resumed");
            }
            default -> message(client, "message.session-stats.not_recording");
        }
    }

    public static void start(Minecraft client) {
        if (RECORDER.isActive() || client.player == null) {
            return;
        }
        sessionFolder = worldFolder;
        lastSaveMillis = clock.getAsLong();
        RECORDER.start(worldName, lastSaveMillis);
        message(client, "message.session-stats.started");
    }

    public static void stop(Minecraft client) {
        if (!RECORDER.isActive()) {
            return;
        }
        showSummaryWhenFinished = ModConfig.getInstance().showSummaryOnStop;
        RECORDER.stop(clock.getAsLong());
        message(client, "message.session-stats.stopped");
    }

    /** Called at the end of every client tick. */
    public static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        long now = clock.getAsLong();
        trackMovement(player);
        long refresh = ModConfig.getInstance().showHud
                ? ModConfig.getInstance().refreshSeconds * 1000L
                : BACKGROUND_REFRESH_MILLIS;
        // Singleplayer pauses the game in menus; that time is not play time.
        boolean counting = player != null && !(client.hasSingleplayerServer() && client.isPaused());
        RECORDER.tick(now, counting, player != null ? player.totalExperience : -1, refresh);

        if (RECORDER.isActive() && now - lastSaveMillis >= AUTOSAVE_MILLIS) {
            lastSaveMillis = now;
            save(RECORDER.current());
        }
    }

    /** The server sent stats, as the answer to a request or on its own. */
    public static void onStatsPacket(Object2IntMap<Stat<?>> stats) {
        Map<String, Long> changed = new HashMap<>();
        for (Object2IntMap.Entry<Stat<?>> entry : stats.object2IntEntrySet()) {
            Stat<?> stat = entry.getKey();
            long value = entry.getIntValue();
            String key = key(stat);
            if (key == null) {
                continue;
            }
            changed.put(key, value);
            // Vanilla counts a placed block as a use of its item.
            if (stat.getType() == Stats.ITEM_USED && stat.getValue() instanceof BlockItem) {
                changed.put(StatKeys.of(StatKeys.PLACED, StatKeys.value(key)), value);
            }
        }
        RECORDER.onStats(changed);
    }

    public static void onBlockBroken(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id != null) {
            RECORDER.onBlockMined(id.toString());
        }
    }

    private static <T> String key(Stat<T> stat) {
        StatType<T> type = stat.getType();
        Identifier typeId = BuiltInRegistries.STAT_TYPE.getKey(type);
        Identifier valueId = type.getRegistry().getKey(stat.getValue());
        if (typeId == null || valueId == null) {
            return null;
        }
        return StatKeys.of(typeId.toString(), valueId.toString());
    }

    private static void trackMovement(LocalPlayer player) {
        if (player == null) {
            lastPosition = null;
            return;
        }
        Vec3 position = new Vec3(player.getX(), player.getY(), player.getZ());
        if (lastPosition != null) {
            double dx = position.x - lastPosition.x;
            double dy = position.y - lastPosition.y;
            double dz = position.z - lastPosition.z;
            double total = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (total < TELEPORT_BLOCKS) {
                StatKeys.Movement movement;
                double distance;
                if (player.isPassenger()) {
                    movement = StatKeys.Movement.RIDE;
                    distance = Math.sqrt(dx * dx + dz * dz);
                } else if (player.isFallFlying() || player.getAbilities().flying) {
                    movement = StatKeys.Movement.FLY;
                    distance = total;
                } else {
                    // Like vanilla, walking counts horizontal distance and falling is not walking.
                    movement = StatKeys.Movement.WALK;
                    distance = Math.sqrt(dx * dx + dz * dz);
                }
                RECORDER.onMoved(movement, Math.round(distance * 100));
            }
        }
        lastPosition = position;
    }

    private static void identifyWorld(Minecraft client) {
        IntegratedServer server = client.getSingleplayerServer();
        if (client.hasSingleplayerServer() && server != null) {
            worldName = server.getWorldData().getLevelName();
            Path worldDir = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            String dirName = worldDir.getFileName() != null ? worldDir.getFileName().toString() : worldName;
            worldFolder = SessionStore.folderName("world", dirName);
            return;
        }
        ServerData data = client.getCurrentServer();
        if (data != null) {
            worldName = data.name != null && !data.name.isBlank() ? data.name : data.ip;
            worldFolder = SessionStore.folderName("server", data.ip != null ? data.ip : worldName);
        } else {
            worldName = "Unknown";
            worldFolder = SessionStore.folderName("server", "unknown");
        }
    }

    private static void onFinished(Session session) {
        lastFinished = session;
        save(session);
        Minecraft client = Minecraft.getInstance();
        if (showSummaryWhenFinished && client.player != null) {
            Ui.open(new SummaryScreen(null, session));
        }
        showSummaryWhenFinished = false;
    }

    private static void save(Session session) {
        if (session == null) {
            return;
        }
        try {
            STORE.save(sessionFolder, session);
        } catch (IOException e) {
            SessionStatsClient.LOGGER.error("Failed to save session to {}", STORE.root(), e);
        }
    }

    private static void message(Minecraft client, String key) {
        if (ModConfig.getInstance().showMessages && client.player != null) {
            client.player.sendOverlayMessage(Component.translatable(key));
        }
    }
}
