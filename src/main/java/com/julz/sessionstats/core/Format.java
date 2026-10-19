package com.julz.sessionstats.core;

import java.util.Locale;

/** Number, distance and duration formatting for the HUD, the screens and the CSV export. */
public final class Format {
    private Format() {
    }

    /** {@code 12,345}. */
    public static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    /** {@code 340 m} or {@code 1.2 km}. */
    public static String distance(long cm) {
        long meters = cm / 100;
        if (meters < 1000) {
            return meters + " m";
        }
        return String.format(Locale.ROOT, "%.1f km", meters / 1000.0);
    }

    /** {@code 1h 05m}, {@code 12m 05s} or {@code 42s}. */
    public static String duration(long millis) {
        long seconds = Math.max(0, millis) / 1000;
        long hours = seconds / 3600;
        long minutes = seconds / 60 % 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%dh %02dm", hours, minutes);
        }
        if (minutes > 0) {
            return String.format(Locale.ROOT, "%dm %02ds", minutes, seconds % 60);
        }
        return seconds + "s";
    }

    /** {@code 1:05:09} or {@code 5:09}, for the recording timer. */
    public static String timer(long millis) {
        long seconds = Math.max(0, millis) / 1000;
        long hours = seconds / 3600;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, seconds / 60 % 60, seconds % 60);
        }
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    /** {@code 312}, {@code 4.5} or {@code -} for a rate per hour (see {@link SessionTotals#perHour}). */
    public static String rate(double perHour) {
        if (perHour < 0) {
            return "-";
        }
        if (perHour < 10 && perHour != Math.rint(perHour)) {
            return String.format(Locale.ROOT, "%.1f", perHour);
        }
        return number(Math.round(perHour));
    }

    /** A stat's value for display: distances in m or km, everything else as a number. */
    public static String stat(SessionTotals.Stat stat, long value) {
        return stat.isDistance() ? distance(value) : number(value);
    }

    /** A per hour rate for display, distances in m or km. */
    public static String statRate(SessionTotals.Stat stat, double perHour) {
        if (stat.isDistance() && perHour >= 0) {
            return distance(Math.round(perHour));
        }
        return rate(perHour);
    }
}
