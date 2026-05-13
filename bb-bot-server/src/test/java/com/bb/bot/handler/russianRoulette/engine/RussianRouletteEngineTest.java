package com.bb.bot.handler.russianRoulette.engine;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RussianRouletteEngineTest {

    @Test
    void newGame_initializesBulletAndCurrentPosition() {
        RussianRouletteEngine engine = new RussianRouletteEngine(new Random(42));
        RouletteState state = engine.newGame(6);

        assertEquals(6, state.getChamberSize());
        assertTrue(state.getBulletPosition() >= 0 && state.getBulletPosition() < 6);
        assertEquals(0, state.getCurrentPosition());
        assertTrue(state.getParticipants().isEmpty());
    }

    @Test
    void newGame_rejectsTooSmallChamber() {
        RussianRouletteEngine engine = new RussianRouletteEngine(new Random(42));
        assertThrows(IllegalArgumentException.class, () -> engine.newGame(1));
    }

    @Test
    void join_addsParticipant() {
        RussianRouletteEngine engine = new RussianRouletteEngine(new Random(42));
        RouletteState state = engine.newGame(6);
        engine.join(state, "u1");
        engine.join(state, "u2");
        assertEquals(2, state.getParticipants().size());
    }

    @Test
    void spin_rejectsNonParticipant() {
        RussianRouletteEngine engine = new RussianRouletteEngine(new Random(42));
        RouletteState state = engine.newGame(6);
        assertThrows(IllegalArgumentException.class, () -> engine.spin(state, "stranger"));
    }

    @Test
    void pullTrigger_advancesPosition() {
        RussianRouletteEngine engine = new RussianRouletteEngine(new Random(42));
        RouletteState state = engine.newGame(6);
        engine.join(state, "u1");
        int before = state.getCurrentPosition();
        engine.pullTrigger(state, "u1");
        assertEquals((before + 1) % state.getChamberSize(), state.getCurrentPosition());
    }

    @Test
    void pullTrigger_returnsTrueAtBulletPosition() {
        RussianRouletteEngine engine = new RussianRouletteEngine(new Random(42));
        RouletteState state = new RouletteState(6, 2, 2, new java.util.HashSet<>());
        state.getParticipants().add("u1");
        assertTrue(engine.pullTrigger(state, "u1"));
    }

    @Test
    void pullTrigger_returnsFalseAtEmptyChamber() {
        RussianRouletteEngine engine = new RussianRouletteEngine(new Random(42));
        RouletteState state = new RouletteState(6, 5, 0, new java.util.HashSet<>());
        state.getParticipants().add("u1");
        assertFalse(engine.pullTrigger(state, "u1"));
    }

    @Test
    void fullRound_eachChamberPositionEventuallyHits() {
        // 用确定性 state 验证：完整一圈下来，必有且仅有一次命中
        RussianRouletteEngine engine = new RussianRouletteEngine(new Random(0));
        RouletteState state = new RouletteState(4, 2, 0, new java.util.HashSet<>());
        state.getParticipants().add("u1");

        int hits = 0;
        for (int i = 0; i < 4; i++) {
            if (engine.pullTrigger(state, "u1")) {
                hits++;
            }
        }
        assertEquals(1, hits);
    }

    @Test
    void pullTrigger_rejectsNonParticipant() {
        RussianRouletteEngine engine = new RussianRouletteEngine(new Random(42));
        RouletteState state = engine.newGame(6);
        assertThrows(IllegalArgumentException.class, () -> engine.pullTrigger(state, "stranger"));
    }
}
