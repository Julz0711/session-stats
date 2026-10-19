package com.julz.sessionstats.core;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Sorting and CSV export for the session history. */
public final class SessionHistory {
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    private SessionHistory() {
    }

    /** What the history can be sorted by: the date, the length, or any HUD stat. Always largest or newest first. */
    public enum Sort {
        DATE(null), LENGTH(null),
        BLOCKS_MINED(SessionTotals.Stat.BLOCKS_MINED), BLOCKS_PLACED(SessionTotals.Stat.BLOCKS_PLACED),
        ORES_MINED(SessionTotals.Stat.ORES_MINED), MOB_KILLS(SessionTotals.Stat.MOB_KILLS),
        DEATHS(SessionTotals.Stat.DEATHS), DISTANCE(SessionTotals.Stat.DISTANCE), XP(SessionTotals.Stat.XP);

        /** The stat this sorts by, or null for date and length. */
        public final SessionTotals.Stat stat;

        Sort(SessionTotals.Stat stat) {
            this.stat = stat;
        }

        public Sort next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public long value(Session session) {
            return switch (this) {
                case DATE -> session.startedAt;
                case LENGTH -> session.activeMillis;
                default -> session.totals().get(stat);
            };
        }
    }

    public static List<Session> sorted(List<Session> sessions, Sort sort) {
        List<Session> sorted = new ArrayList<>(sessions);
        sorted.sort(Comparator.comparingLong(sort::value).reversed()
                .thenComparing(Comparator.comparingLong((Session s) -> s.startedAt).reversed()));
        return sorted;
    }

    /** All sessions as CSV, one row each, with a rate per hour next to every total. */
    public static String toCsv(List<Session> sessions, ZoneId zone) {
        StringBuilder csv = new StringBuilder();
        csv.append("started,world,active_minutes,finished,blocks_mined,blocks_mined_per_hour,blocks_placed,"
                + "blocks_placed_per_hour,ores_mined,ores_mined_per_hour,mob_kills,mob_kills_per_hour,deaths,"
                + "walked_m,flown_m,ridden_m,distance_m_per_hour,xp,xp_per_hour\n");
        for (Session session : sessions) {
            SessionTotals t = session.totals();
            csv.append(DATE.format(Instant.ofEpochMilli(session.startedAt).atZone(zone))).append(',')
                    .append(quote(session.world)).append(',')
                    .append(session.activeMillis / 60_000).append(',')
                    .append(session.finished).append(',')
                    .append(t.blocksMined()).append(',').append(rate(t, t.blocksMined())).append(',')
                    .append(t.blocksPlaced()).append(',').append(rate(t, t.blocksPlaced())).append(',')
                    .append(t.oresMined()).append(',').append(rate(t, t.oresMined())).append(',')
                    .append(t.mobKills()).append(',').append(rate(t, t.mobKills())).append(',')
                    .append(t.deaths()).append(',')
                    .append(t.walkCm() / 100).append(',').append(t.flyCm() / 100).append(',').append(t.rideCm() / 100).append(',')
                    .append(rate(t, t.distanceCm() / 100)).append(',')
                    .append(t.xp()).append(',').append(rate(t, t.xp()))
                    .append('\n');
        }
        return csv.toString();
    }

    private static String rate(SessionTotals totals, long value) {
        double perHour = totals.perHour(value);
        return perHour < 0 ? "" : String.format(Locale.ROOT, "%.1f", perHour);
    }

    private static String quote(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
