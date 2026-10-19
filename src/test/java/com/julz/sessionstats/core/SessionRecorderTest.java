package com.julz.sessionstats.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionRecorderTest {
    private static final String DIAMOND = StatKeys.of(StatKeys.MINED, "minecraft:diamond_ore");
    private static final String STONE = StatKeys.of(StatKeys.MINED, "minecraft:stone");
    private static final long REFRESH = 30_000;

    private int requests = 0;
    private final List<Session> finished = new ArrayList<>();
    private final SessionRecorder recorder = new SessionRecorder(new SessionRecorder.Host() {
        @Override
        public void requestStats() {
            requests++;
        }

        @Override
        public void finished(Session session) {
            finished.add(session);
        }
    });
    private final Map<String, Long> server = new HashMap<>();
    private long now = 1_000_000;

    @Test
    void countsTheDifferenceBetweenStartAndStop() {
        server.put(STONE, 500L);
        recorder.start("World", now);
        assertEquals(1, requests);
        answer();

        server.put(STONE, 620L);
        server.put(DIAMOND, 3L);
        tick(60_000);
        recorder.stop(now);
        assertTrue(finished.isEmpty(), "waits for the server");
        answer();

        Session session = finished.getFirst();
        assertEquals(120, session.stat(STONE));
        assertEquals(3, session.stat(DIAMOND));
        assertEquals(60_000, session.activeMillis);
        assertTrue(session.finished);
        assertEquals(SessionRecorder.State.IDLE, recorder.state());
    }

    @Test
    void pausedTimeAndStatsDoNotCount() {
        recorder.start("World", now);
        answer();
        server.put(STONE, 10L);
        tick(10_000);
        recorder.pause(now);
        answer();

        // Mining while paused
        server.put(STONE, 50L);
        tick(20_000);
        recorder.resume(now);
        answer();

        server.put(STONE, 55L);
        tick(5_000);
        recorder.stop(now);
        answer();

        Session session = finished.getFirst();
        assertEquals(15, session.stat(STONE));
        assertEquals(15_000, session.activeMillis);
    }

    @Test
    void liveCountsShowUntilTheServerAnswers() {
        recorder.start("World", now);
        answer();
        recorder.onBlockMined("minecraft:stone");
        recorder.onBlockMined("minecraft:stone");
        assertEquals(2, recorder.current().stat(STONE));

        // The server's numbers replace the client's guess
        server.put(STONE, 3L);
        answer();
        assertEquals(3, recorder.current().stat(STONE));
    }

    @Test
    void ignoresEventsBeforeTheBaselineArrives() {
        recorder.start("World", now);
        recorder.onBlockMined("minecraft:stone");
        assertEquals(0, recorder.current().stat(STONE));
    }

    @Test
    void refreshesStatsPeriodically() {
        recorder.start("World", now);
        answer();
        tick(REFRESH - 50);
        assertEquals(1, requests);
        tick(50);
        assertEquals(2, requests);
    }

    @Test
    void stopsAfterTimeoutWhenTheServerNeverAnswers() {
        recorder.start("World", now);
        answer();
        recorder.onBlockMined("minecraft:stone");
        recorder.stop(now);
        tick(SessionRecorder.STOP_TIMEOUT_MILLIS);
        assertEquals(1, finished.size());
        assertEquals(1, finished.getFirst().stat(STONE));
    }

    @Test
    void stopNowKeepsWhatIsKnown() {
        server.put(STONE, 5L);
        recorder.start("World", now);
        answer();
        server.put(STONE, 9L);
        answer();
        recorder.onBlockMined("minecraft:diamond_ore");
        recorder.stopNow(now);
        Session session = finished.getFirst();
        assertEquals(4, session.stat(STONE));
        assertEquals(1, session.stat(DIAMOND));
        assertNull(recorder.current());
    }

    @Test
    void stoppingWhilePausedFinishesRightAway() {
        recorder.start("World", now);
        answer();
        recorder.pause(now);
        answer();
        recorder.stop(now);
        assertEquals(1, finished.size());
    }

    @Test
    void resumingBeforeThePauseAnswerStillCountsBothParts() {
        recorder.start("World", now);
        answer();
        server.put(STONE, 4L);
        recorder.pause(now);
        recorder.resume(now);
        answer();
        server.put(STONE, 6L);
        recorder.stop(now);
        answer();
        assertEquals(6, finished.getFirst().stat(STONE));
    }

    @Test
    void countsOnlyExperienceGained() {
        recorder.start("World", now);
        answer();
        recorder.tick(now, true, 100, REFRESH);
        recorder.tick(now, true, 130, REFRESH);
        recorder.tick(now, true, 0, REFRESH);   // died
        recorder.tick(now, true, 20, REFRESH);
        recorder.tick(now, true, 10, REFRESH);  // enchanted
        assertEquals(50, recorder.current().xp);
    }

    @Test
    void timeDoesNotCountWhileTheGameIsPaused() {
        recorder.start("World", now);
        answer();
        now += 10_000;
        recorder.tick(now, false, -1, REFRESH);
        now += 1_000;
        recorder.tick(now, true, -1, REFRESH);
        assertEquals(1_000, recorder.current().activeMillis);
    }

    @Test
    void liveDistanceUsesTheMovementKind() {
        recorder.start("World", now);
        answer();
        recorder.onMoved(StatKeys.Movement.WALK, 250);
        recorder.onMoved(StatKeys.Movement.RIDE, 1000);
        SessionTotals totals = recorder.current().totals();
        assertEquals(250, totals.walkCm());
        assertEquals(1000, totals.rideCm());
    }

    /** The server answers a stats request with the stats that changed since its last answer. */
    private final Map<String, Long> lastSent = new HashMap<>();

    private void answer() {
        Map<String, Long> changed = new HashMap<>();
        server.forEach((key, value) -> {
            if (!value.equals(lastSent.get(key))) {
                changed.put(key, value);
            }
        });
        lastSent.putAll(server);
        recorder.onStats(changed);
    }

    private void tick(long millis) {
        now += millis;
        recorder.tick(now, true, -1, REFRESH);
    }
}
