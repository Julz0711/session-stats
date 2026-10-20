package com.julz.sessionstats.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Records one session at a time. Totals come from the player's vanilla statistics, which the
 * server sends on request: the difference between a snapshot when recording starts (or resumes)
 * and one when it pauses (or stops). Between snapshots, events the client sees itself (blocks
 * broken, distance moved) are added on top so the HUD stays live; the next snapshot replaces them.
 * Has no Minecraft dependencies; {@link Host} connects it to the game.
 */
public final class SessionRecorder {
    /** How long to wait for the server's answer when stopping before saving what is known. */
    public static final long STOP_TIMEOUT_MILLIS = 5_000;

    public interface Host {
        /** Asks the server for the player's statistics. The answer arrives in {@link #onStats}. */
        void requestStats();

        /** A stopped session, ready to save and show. */
        void finished(Session session);
    }

    public enum State { IDLE, RECORDING, PAUSED, STOPPING }

    private final Host host;

    private State state = State.IDLE;
    private Session session;
    // Absolute values of every stat the server has sent since joining.
    private final Map<String, Long> known = new HashMap<>();
    // Snapshot the current recording segment started from, or null when no segment is open.
    private Map<String, Long> baseline;
    // Totals of the segments that already ended (recording was paused in between).
    private final Map<String, Long> accumulated = new HashMap<>();
    // Counted on the client since the server last sent stats.
    private final Map<String, Long> live = new HashMap<>();
    private boolean awaitingBaseline;
    private boolean awaitingClose;
    private long lastRequestMillis;
    private long lastTickMillis;
    private int lastXp = -1;

    public SessionRecorder(Host host) {
        this.host = host;
    }

    public State state() {
        return state;
    }

    public boolean isActive() {
        return state != State.IDLE;
    }

    /** True while a snapshot that starts or ends a recording segment has been asked for but not received. */
    public boolean isWaitingForServer() {
        return awaitingBaseline || awaitingClose;
    }

    public void start(String world, long nowMillis) {
        if (state != State.IDLE) {
            return;
        }
        session = new Session();
        session.world = world;
        session.startedAt = nowMillis;
        accumulated.clear();
        live.clear();
        baseline = null;
        awaitingClose = false;
        lastTickMillis = nowMillis;
        state = State.RECORDING;
        openSegment(nowMillis);
    }

    public void pause(long nowMillis) {
        if (state != State.RECORDING) {
            return;
        }
        state = State.PAUSED;
        closeSegment(nowMillis);
    }

    public void resume(long nowMillis) {
        if (state != State.PAUSED) {
            return;
        }
        state = State.RECORDING;
        lastTickMillis = nowMillis;
        openSegment(nowMillis);
    }

    /** Stops recording. The session is finished once the server answers, or after a timeout. */
    public void stop(long nowMillis) {
        if (state == State.RECORDING) {
            closeSegment(nowMillis);
        } else if (state != State.PAUSED) {
            return;
        }
        state = State.STOPPING;
        session.endedAt = nowMillis;
        if (!awaitingClose) {
            finish();
        }
    }

    /**
     * Stops right away with what is known, for when the connection is gone (leaving the world) and
     * the server can no longer answer. Client-counted events since the last snapshot are kept.
     */
    public void stopNow(long nowMillis) {
        if (state == State.IDLE) {
            return;
        }
        if (state != State.STOPPING) {
            session.endedAt = nowMillis;
        }
        if (baseline != null) {
            addDifference(accumulated, known, baseline);
            addAll(accumulated, live);
        }
        baseline = null;
        awaitingBaseline = false;
        awaitingClose = false;
        finish();
    }

    /** The server sent statistics: only the ones that changed since it last sent them, as absolute values. */
    public void onStats(Map<String, Long> changed) {
        known.putAll(changed);
        if (awaitingClose) {
            awaitingClose = false;
            if (baseline != null) {
                addDifference(accumulated, known, baseline);
            }
            baseline = null;
            live.clear();
            if (state == State.STOPPING) {
                finish();
                return;
            }
        }
        if (awaitingBaseline) {
            awaitingBaseline = false;
            baseline = new HashMap<>(known);
        }
        live.clear();
    }

    /**
     * Called every client tick.
     *
     * @param counting       false while time should not count, e.g. in the singleplayer pause menu
     * @param totalXp        the player's total experience points, or -1 without a player
     * @param refreshMillis  how often to ask the server for fresh stats while recording
     */
    public void tick(long nowMillis, boolean counting, int totalXp, long refreshMillis) {
        if (state == State.RECORDING) {
            if (counting) {
                session.activeMillis += Math.max(0, nowMillis - lastTickMillis);
            }
            // Dying resets experience to 0, and spending levels lowers it: only gains count.
            if (lastXp >= 0 && totalXp > lastXp) {
                session.xp += totalXp - lastXp;
            }
            if (!awaitingBaseline && !awaitingClose && nowMillis - lastRequestMillis >= refreshMillis) {
                request(nowMillis);
            }
        } else if (state == State.STOPPING && nowMillis - lastRequestMillis >= STOP_TIMEOUT_MILLIS) {
            stopNow(nowMillis);
        }
        lastTickMillis = nowMillis;
        lastXp = totalXp;
    }

    /** A block the player broke, counted until the server's next snapshot includes it. */
    public void onBlockMined(String blockId) {
        countLive(StatKeys.of(StatKeys.MINED, blockId), 1);
    }

    public void onMoved(StatKeys.Movement movement, long cm) {
        String key = switch (movement) {
            case WALK -> StatKeys.WALK;
            case FLY -> StatKeys.FLY;
            case RIDE -> StatKeys.RIDE;
            case NONE -> null;
        };
        if (key != null && cm > 0) {
            countLive(key, cm);
        }
    }

    /** Forgets everything the server sent, e.g. after joining another world or server. */
    public void clearKnownStats() {
        known.clear();
        lastXp = -1;
    }

    /** The session so far, with live counts. Null while idle. */
    public Session current() {
        if (session == null) {
            return null;
        }
        Map<String, Long> totals = new HashMap<>(accumulated);
        if (baseline != null) {
            addDifference(totals, known, baseline);
            addAll(totals, live);
        }
        session.stats = nonZeroSorted(totals);
        return session;
    }

    private void openSegment(long nowMillis) {
        awaitingBaseline = true;
        request(nowMillis);
    }

    private void closeSegment(long nowMillis) {
        if (awaitingBaseline) {
            // The segment never started: nothing to count.
            awaitingBaseline = false;
            return;
        }
        if (baseline != null) {
            awaitingClose = true;
            request(nowMillis);
        }
    }

    private void request(long nowMillis) {
        lastRequestMillis = nowMillis;
        host.requestStats();
    }

    private void countLive(String key, long amount) {
        if (state == State.RECORDING && baseline != null) {
            live.merge(key, amount, Long::sum);
        }
    }

    private void finish() {
        Session done = current();
        done.finished = true;
        state = State.IDLE;
        session = null;
        baseline = null;
        accumulated.clear();
        live.clear();
        host.finished(done);
    }

    private static void addDifference(Map<String, Long> into, Map<String, Long> now, Map<String, Long> before) {
        for (Map.Entry<String, Long> entry : now.entrySet()) {
            long difference = entry.getValue() - before.getOrDefault(entry.getKey(), 0L);
            if (difference != 0) {
                into.merge(entry.getKey(), difference, Long::sum);
            }
        }
    }

    private static void addAll(Map<String, Long> into, Map<String, Long> values) {
        values.forEach((key, value) -> into.merge(key, value, Long::sum));
    }

    private static Map<String, Long> nonZeroSorted(Map<String, Long> values) {
        Map<String, Long> sorted = new java.util.TreeMap<>();
        values.forEach((key, value) -> {
            if (value != 0) {
                sorted.put(key, value);
            }
        });
        return sorted;
    }
}
