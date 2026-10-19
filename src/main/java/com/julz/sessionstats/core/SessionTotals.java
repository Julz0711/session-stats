package com.julz.sessionstats.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The numbers the HUD and the summary show, added up from a session's raw stats.
 *
 * @param ores      ore type (see {@link StatKeys#oreType}) to blocks mined, most mined first
 * @param topMined  block id to count, most mined first, at most {@link #TOP_COUNT}
 * @param walkCm    distance on foot, including sprinting, swimming and climbing
 * @param flyCm     distance flying in creative and with elytra
 * @param rideCm    distance in minecarts, boats and on mounts
 */
public record SessionTotals(long activeMillis, long blocksMined, long blocksPlaced, long oresMined,
                            Map<String, Long> ores, List<Map.Entry<String, Long>> topMined,
                            long mobKills, long deaths, long walkCm, long flyCm, long rideCm, long xp) {
    public static final int TOP_COUNT = 5;

    public static SessionTotals of(Session session) {
        long mined = 0;
        long placed = 0;
        long oresMined = 0;
        long walk = 0;
        long fly = 0;
        long ride = 0;
        Map<String, Long> ores = new LinkedHashMap<>();
        Map<String, Long> blocks = new LinkedHashMap<>();

        for (Map.Entry<String, Long> entry : session.stats.entrySet()) {
            String key = entry.getKey();
            long value = entry.getValue();
            switch (StatKeys.type(key)) {
                case StatKeys.MINED -> {
                    String block = StatKeys.value(key);
                    mined += value;
                    blocks.merge(block, value, Long::sum);
                    String ore = StatKeys.oreType(block);
                    if (ore != null) {
                        oresMined += value;
                        ores.merge(ore, value, Long::sum);
                    }
                }
                case StatKeys.PLACED -> placed += value;
                case StatKeys.CUSTOM -> {
                    switch (StatKeys.movement(key)) {
                        case WALK -> walk += value;
                        case FLY -> fly += value;
                        case RIDE -> ride += value;
                        case NONE -> {
                        }
                    }
                }
                default -> {
                }
            }
        }

        return new SessionTotals(session.activeMillis, mined, placed, oresMined, sortedByValue(ores, Integer.MAX_VALUE),
                List.copyOf(sortedByValue(blocks, TOP_COUNT).entrySet()),
                session.stat(StatKeys.MOB_KILLS), session.stat(StatKeys.DEATHS), walk, fly, ride, session.xp);
    }

    public long distanceCm() {
        return walkCm + flyCm + rideCm;
    }

    /** Value of a stat the HUD and the history can show. */
    public long get(Stat stat) {
        return switch (stat) {
            case BLOCKS_MINED -> blocksMined;
            case BLOCKS_PLACED -> blocksPlaced;
            case ORES_MINED -> oresMined;
            case MOB_KILLS -> mobKills;
            case DEATHS -> deaths;
            case DISTANCE -> distanceCm();
            case XP -> xp;
        };
    }

    /** Per hour of active time, or -1 while the session is too short for a meaningful rate. */
    public double perHour(long value) {
        if (activeMillis < 60_000) {
            return -1;
        }
        return value * 3_600_000.0 / activeMillis;
    }

    /** The stats a player can pick for the HUD or sort the history by. */
    public enum Stat {
        BLOCKS_MINED, BLOCKS_PLACED, ORES_MINED, MOB_KILLS, DEATHS, DISTANCE, XP;

        public boolean isDistance() {
            return this == DISTANCE;
        }
    }

    private static Map<String, Long> sortedByValue(Map<String, Long> map, int limit) {
        List<Map.Entry<String, Long>> entries = new ArrayList<>(map.entrySet());
        entries.sort(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry.comparingByKey()));
        Map<String, Long> sorted = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : entries) {
            if (sorted.size() >= limit) {
                break;
            }
            sorted.put(entry.getKey(), entry.getValue());
        }
        return sorted;
    }
}
