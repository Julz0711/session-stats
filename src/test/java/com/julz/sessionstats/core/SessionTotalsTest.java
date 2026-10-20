package com.julz.sessionstats.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SessionTotalsTest {
    @Test
    void addsUpBlocksOresAndDistance() {
        Session session = session(Map.of(
                "minecraft:mined/minecraft:stone", 300L,
                "minecraft:mined/minecraft:diamond_ore", 2L,
                "minecraft:mined/minecraft:deepslate_diamond_ore", 5L,
                "minecraft:mined/minecraft:nether_quartz_ore", 4L,
                "session-stats:placed/minecraft:cobblestone", 40L,
                "minecraft:custom/minecraft:sprint_one_cm", 10_000L,
                "minecraft:custom/minecraft:walk_one_cm", 5_000L,
                "minecraft:custom/minecraft:aviate_one_cm", 80_000L,
                "minecraft:custom/minecraft:boat_one_cm", 2_000L,
                "minecraft:custom/minecraft:fall_one_cm", 900L));
        SessionTotals totals = session.totals();
        assertEquals(311, totals.blocksMined());
        assertEquals(40, totals.blocksPlaced());
        assertEquals(11, totals.oresMined());
        assertEquals(Map.of("minecraft:diamond", 7L, "minecraft:quartz", 4L), totals.ores());
        assertEquals(List.of("minecraft:diamond", "minecraft:quartz"), List.copyOf(totals.ores().keySet()));
        assertEquals(15_000, totals.walkCm());
        assertEquals(80_000, totals.flyCm());
        assertEquals(2_000, totals.rideCm());
        assertEquals("minecraft:stone", totals.topMined().getFirst().getKey());
    }

    @Test
    void topMinedKeepsFive() {
        Session session = session(Map.of(
                "minecraft:mined/minecraft:a", 1L, "minecraft:mined/minecraft:b", 2L, "minecraft:mined/minecraft:c", 3L,
                "minecraft:mined/minecraft:d", 4L, "minecraft:mined/minecraft:e", 5L, "minecraft:mined/minecraft:f", 6L));
        List<Map.Entry<String, Long>> top = session.totals().topMined();
        assertEquals(5, top.size());
        assertEquals("minecraft:f", top.getFirst().getKey());
        assertEquals("minecraft:b", top.getLast().getKey());
    }

    @Test
    void ratesNeedAMinute() {
        Session session = session(Map.of("minecraft:mined/minecraft:stone", 26L));
        session.activeMillis = 59_000;
        assertEquals(-1, session.totals().perHour(26));
        session.activeMillis = 5 * 60_000;
        assertEquals(312, session.totals().perHour(26), 0.001);
    }

    @Test
    void oreTypes() {
        assertEquals("minecraft:iron", StatKeys.oreType("minecraft:deepslate_iron_ore"));
        assertEquals("minecraft:gold", StatKeys.oreType("minecraft:nether_gold_ore"));
        assertEquals("minecraft:ancient_debris", StatKeys.oreType("minecraft:ancient_debris"));
        assertEquals("othermod:tin", StatKeys.oreType("othermod:tin_ore"));
        assertNull(StatKeys.oreType("minecraft:stone"));
        assertEquals("minecraft:diamond_ore", StatKeys.oreBlock("minecraft:diamond"));
        assertEquals("minecraft:nether_quartz_ore", StatKeys.oreBlock("minecraft:quartz"));
    }

    @Test
    void formats() {
        assertEquals("12,345", Format.number(12_345));
        assertEquals("340 m", Format.distance(34_000));
        assertEquals("1.2 km", Format.distance(123_456));
        assertEquals("1h 05m", Format.duration(3_900_000));
        assertEquals("12m 05s", Format.duration(725_000));
        assertEquals("1:05:09", Format.timer(3_909_000));
        assertEquals("5:09", Format.timer(309_000));
        assertEquals("-", Format.rate(-1));
        assertEquals("4.5", Format.rate(4.5));
        assertEquals("312", Format.rate(312.4));
    }

    @Test
    void sortsHistoryLargestFirst() {
        Session a = session(Map.of("minecraft:mined/minecraft:stone", 5L));
        a.startedAt = 1;
        Session b = session(Map.of("minecraft:mined/minecraft:stone", 50L));
        b.startedAt = 2;
        Session c = session(Map.of("minecraft:mined/minecraft:stone", 20L));
        c.startedAt = 3;
        assertEquals(List.of(b, c, a), SessionHistory.sorted(List.of(a, b, c), SessionHistory.Sort.BLOCKS_MINED));
        assertEquals(List.of(c, b, a), SessionHistory.sorted(List.of(a, b, c), SessionHistory.Sort.DATE));
    }

    @Test
    void exportsCsv() {
        Session session = session(Map.of("minecraft:mined/minecraft:stone", 60L));
        session.world = "My, World";
        session.startedAt = 0;
        session.activeMillis = 30 * 60_000;
        session.finished = true;
        String[] lines = SessionHistory.toCsv(List.of(session), ZoneOffset.UTC).split("\n");
        assertEquals(2, lines.length);
        assertEquals("1970-01-01 00:00,\"My, World\",30,true,60,120.0,0,0.0,0,0.0,0,0.0,0,0,0,0,0.0,0,0.0", lines[1]);
    }

    @Test
    void savesAndLoadsSessions() throws Exception {
        Path root = Files.createTempDirectory("session-stats-test");
        SessionStore store = new SessionStore(root, ZoneOffset.UTC);
        Session session = session(Map.of("minecraft:mined/minecraft:stone", 7L));
        session.world = "Test";
        session.startedAt = 1_760_000_000_000L;
        String folder = SessionStore.folderName("world", "New World!");
        assertEquals("world_new_world", folder);
        store.save(folder, session);
        session.stats.put("minecraft:mined/minecraft:stone", 9L);
        store.save(folder, session);

        List<Session> loaded = store.load(null);
        assertEquals(1, loaded.size());
        assertEquals(9, loaded.getFirst().stat("minecraft:mined/minecraft:stone"));
        assertEquals(List.of(folder), store.folders());
    }

    private static Session session(Map<String, Long> stats) {
        Session session = new Session();
        session.stats = new java.util.LinkedHashMap<>(new java.util.TreeMap<>(stats));
        return session;
    }
}
