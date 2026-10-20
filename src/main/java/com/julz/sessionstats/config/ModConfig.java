package com.julz.sessionstats.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.julz.sessionstats.client.SessionStatsClient;
import com.julz.sessionstats.core.SessionTotals;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class ModConfig {
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("session-stats.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ModConfig instance;

    // Start a session when joining a world or server. Sessions always stop when leaving.
    public boolean autoStart = false;
    public boolean showHud = true;
    // Any of: BLOCKS_MINED, BLOCKS_PLACED, ORES_MINED, MOB_KILLS, DEATHS, DISTANCE, WALKED, FLOWN, RIDDEN, XP
    public List<String> hudStats = new ArrayList<>(List.of("BLOCKS_MINED", "ORES_MINED", "DISTANCE"));
    // HUD look: size in percent, background opacity in percent, and what else it shows.
    public int hudScale = 100;
    public int hudOpacity = 80;
    public HudColor hudColor = HudColor.TEAL;
    public boolean hudShowTimer = true;
    public boolean hudShowRates = false;
    public boolean hudShowOres = false;
    public boolean hudTextShadow = false;
    // Where the HUD sits: the corner it is attached to, and its distance from that corner in GUI pixels.
    public Corner hudCorner = Corner.TOP_LEFT;
    public int hudOffsetX = 4;
    public int hudOffsetY = 4;
    public boolean showSummaryOnStop = true;
    public boolean showMessages = true;
    // How often fresh stats are asked from the server while the HUD shows them.
    public int refreshSeconds = 30;

    public enum Corner {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

        public boolean right() {
            return this == TOP_RIGHT || this == BOTTOM_RIGHT;
        }

        public boolean bottom() {
            return this == BOTTOM_LEFT || this == BOTTOM_RIGHT;
        }

        public static Corner of(boolean right, boolean bottom) {
            if (bottom) {
                return right ? BOTTOM_RIGHT : BOTTOM_LEFT;
            }
            return right ? TOP_RIGHT : TOP_LEFT;
        }
    }

    /** The color of the HUD's values. */
    public enum HudColor {
        TEAL(0xFF5EEAD4), WHITE(0xFFF2F4F5), GOLD(0xFFFFC857), GREEN(0xFF7EE787), BLUE(0xFF79B8FF), PINK(0xFFFF8AD8);

        public final int argb;

        HudColor(int argb) {
            this.argb = argb;
        }

        public HudColor next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public static ModConfig getInstance() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static void load() {
        if (Files.exists(CONFIG_FILE)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
                instance = GSON.fromJson(reader, ModConfig.class);
            } catch (IOException | JsonParseException e) {
                SessionStatsClient.LOGGER.error("Failed to read {}, using defaults", CONFIG_FILE, e);
            }
        }
        if (instance == null) {
            instance = new ModConfig();
        }
        instance.sanitize();
        // Writes the file on first launch and adds options introduced by newer versions.
        save();
    }

    public static void save() {
        try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
            GSON.toJson(instance, writer);
        } catch (IOException e) {
            SessionStatsClient.LOGGER.error("Failed to save {}", CONFIG_FILE, e);
        }
    }

    /** The HUD stats as enum values, in order, without unknown names or duplicates. */
    public List<SessionTotals.Stat> hudStats() {
        List<SessionTotals.Stat> stats = new ArrayList<>();
        for (String name : hudStats) {
            for (SessionTotals.Stat stat : SessionTotals.Stat.values()) {
                if (stat.name().equals(name)) {
                    stats.add(stat);
                }
            }
        }
        return stats;
    }

    /** Pulls hand-edited values back into range. */
    public void sanitize() {
        if (hudCorner == null) {
            hudCorner = Corner.TOP_LEFT;
        }
        hudOffsetX = Math.clamp(hudOffsetX, 0, 4000);
        hudOffsetY = Math.clamp(hudOffsetY, 0, 4000);
        refreshSeconds = Math.clamp(refreshSeconds, 10, 300);
        hudScale = Math.clamp(hudScale, 50, 200);
        hudOpacity = Math.clamp(hudOpacity, 0, 100);
        if (hudColor == null) {
            hudColor = HudColor.TEAL;
        }
        if (hudStats == null) {
            hudStats = new ArrayList<>();
        }
        hudStats = new ArrayList<>(new LinkedHashSet<>(hudStats().stream().map(Enum::name).toList()));
    }
}
