package com.julz.sessionstats.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** One recorded session, as saved to its JSON file. Fields are public for Gson. */
public final class Session {
    public int version = 1;
    /** World or server name, as shown to the player. */
    public String world = "";
    /** Wall clock times, in epoch milliseconds. */
    public long startedAt;
    public long endedAt;
    /** Time spent recording, without pauses and without the singleplayer pause menu. */
    public long activeMillis;
    /** Experience points gained. Vanilla has no stat for this, so it is counted on the client. */
    public long xp;
    /** False while the session is still recording, or when the game closed before it was stopped. */
    public boolean finished;
    /** Change of every stat during the session, see {@link StatKeys}. Only non-zero values are kept. */
    public Map<String, Long> stats = new LinkedHashMap<>();

    public long stat(String key) {
        return stats == null ? 0 : stats.getOrDefault(key, 0L);
    }

    public SessionTotals totals() {
        return SessionTotals.of(this);
    }
}
