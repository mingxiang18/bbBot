package com.bb.bot.handler.horseRace.engine;

import com.bb.bot.handler.horseRace.entity.Horse;
import com.bb.bot.handler.horseRace.entity.HorseRaceEvent;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorseRaceEngineTest {

    @Test
    void newGame_seedsDefaultHorseCount() {
        HorseRaceEngine engine = new HorseRaceEngine(new Random(0), 18, 50, 80);
        HorseRaceState state = engine.newGame();
        assertEquals(HorseRaceEngine.DEFAULT_INITIAL_HORSE_COUNT, state.getHorses().size());
        for (Horse h : state.getHorses()) {
            assertEquals(0, h.getNowPosition());
        }
    }

    @Test
    void addHorse_appendsWithSeq() {
        HorseRaceEngine engine = new HorseRaceEngine(new Random(0), 18, 50, 80);
        HorseRaceState state = engine.newGame();
        int before = state.getHorses().size();

        engine.addHorse(state, "🐴");

        assertEquals(before + 1, state.getHorses().size());
        Horse last = state.getHorses().get(before);
        assertEquals(before + 1, last.getSeq());
        assertEquals("🐴", last.getIcon());
    }

    @Test
    void addHorse_rejectsDuringRace() {
        HorseRaceEngine engine = new HorseRaceEngine(new Random(0), 18, 50, 80);
        HorseRaceState state = engine.newGame();
        state.setRaceState(1);
        assertThrows(IllegalArgumentException.class, () -> engine.addHorse(state, "🐴"));
    }

    @Test
    void tick_advancesHorsesAtLeastByMoveLength() {
        HorseRaceEngine engine = new HorseRaceEngine(new Random(123), 50, 100, 100); // event rates 100% 实际不会触发
        HorseRaceState state = engine.newGame();
        engine.tick(state, Collections.emptyList());
        for (Horse h : state.getHorses()) {
            assertEquals(HorseRaceEngine.DEFAULT_HORSE_MOVE_LENGTH, h.getNowPosition());
        }
    }

    @Test
    void tick_setsRaceStateToInProgress() {
        HorseRaceEngine engine = new HorseRaceEngine(new Random(0), 18, 100, 100);
        HorseRaceState state = engine.newGame();
        engine.tick(state, Collections.emptyList());
        assertEquals(1, state.getRaceState());
    }

    @Test
    void tick_neverExceedsSlideMinusOne() {
        HorseRaceEngine engine = new HorseRaceEngine(new Random(0), 5, 100, 100);
        HorseRaceState state = engine.newGame();
        // tick 多次直到饱和
        for (int i = 0; i < 20; i++) {
            engine.tick(state, Collections.emptyList());
        }
        for (Horse h : state.getHorses()) {
            assertTrue(h.getNowPosition() <= 4);
        }
    }

    @Test
    void isFinished_trueWhenAnyHorseReachesEnd() {
        HorseRaceEngine engine = new HorseRaceEngine(new Random(0), 5, 100, 100);
        HorseRaceState state = engine.newGame();
        assertFalse(engine.isFinished(state));
        state.getHorses().get(0).setNowPosition(4);
        assertTrue(engine.isFinished(state));
        assertEquals(2, state.getRaceState());
    }

    @Test
    void winnerSummary_listsAllHorsesAtFinish() {
        HorseRaceEngine engine = new HorseRaceEngine(new Random(0), 5, 100, 100);
        HorseRaceState state = engine.newGame();
        state.getHorses().get(0).setNowPosition(4);
        state.getHorses().get(2).setNowPosition(4);
        String summary = engine.winnerSummary(state);
        assertTrue(summary.contains("1号马"));
        assertTrue(summary.contains("3号马"));
    }

    @Test
    void tick_emitsEventDescriptionWhenIndependentEventTriggers() {
        // 用 0% 阈值确保 random.nextInt(100) > 0 几乎总成立
        HorseRaceEngine engine = new HorseRaceEngine(new Random(7), 18, 100, 0);
        HorseRaceState state = engine.newGame();
        HorseRaceEvent ev = new HorseRaceEvent();
        ev.setEventId(1);
        ev.setEventName("{random_horse}受伤了");
        ev.setEventType(1);
        ev.setEventEffect(-1);

        String description = engine.tick(state, List.of(ev));
        assertTrue(description.contains("号马"));
    }

    @Test
    void renderHorses_includesIconAtPosition() {
        HorseRaceEngine engine = new HorseRaceEngine(new Random(0), 5, 100, 100);
        HorseRaceState state = engine.newGame();
        state.getHorses().get(0).setNowPosition(2);
        String rendered = engine.renderHorses(state);
        assertTrue(rendered.contains(state.getHorses().get(0).getIcon()));
    }
}
