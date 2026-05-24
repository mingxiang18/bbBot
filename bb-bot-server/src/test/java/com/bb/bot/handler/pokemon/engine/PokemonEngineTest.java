package com.bb.bot.handler.pokemon.engine;

import com.bb.bot.handler.pokemon.entity.PokemonData;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PokemonEngineTest {

    @Test
    void capture_addsRandomIndexUntilFull() {
        PokemonEngine engine = new PokemonEngine();
        ReflectionTestUtils.setField(engine, "all", List.of(makeData(1, "A"), makeData(2, "B"), makeData(3, "C")));

        Random rng = new Random(0);
        PokemonEngine.Outcome out1 = engine.capture(new ArrayList<>(), 2, rng);
        assertSame(PokemonEngine.Outcome.Type.CAPTURED, out1.getType());
        assertEquals(1, out1.getUpdatedCollection().size());
        assertNotNull(out1.getCaptured());

        PokemonEngine.Outcome out2 = engine.capture(out1.getUpdatedCollection(), 2, rng);
        assertSame(PokemonEngine.Outcome.Type.CAPTURED, out2.getType());
        assertEquals(2, out2.getUpdatedCollection().size());

        PokemonEngine.Outcome out3 = engine.capture(out2.getUpdatedCollection(), 2, rng);
        assertSame(PokemonEngine.Outcome.Type.FULL, out3.getType());
        assertNull(out3.getUpdatedCollection());
    }

    @Test
    void capture_returnsUnavailableWhenDataMissing() {
        PokemonEngine engine = new PokemonEngine();
        ReflectionTestUtils.setField(engine, "all", Collections.emptyList());
        PokemonEngine.Outcome out = engine.capture(new ArrayList<>(), 2, new Random(0));
        assertSame(PokemonEngine.Outcome.Type.UNAVAILABLE, out.getType());
    }

    @Test
    void breed_requiresExactlyTwo() {
        PokemonEngine engine = new PokemonEngine();
        ReflectionTestUtils.setField(engine, "beforeNames", List.of(makeData(1, "X"), makeData(2, "Y")));
        ReflectionTestUtils.setField(engine, "afterNames", List.of(makeData(1, "P"), makeData(2, "Q")));

        assertSame(PokemonEngine.Outcome.Type.NOT_ENOUGH, engine.breed(Collections.emptyList()).getType());
        assertSame(PokemonEngine.Outcome.Type.NOT_ENOUGH, engine.breed(List.of(0)).getType());
        assertSame(PokemonEngine.Outcome.Type.NOT_ENOUGH, engine.breed(List.of(0, 1, 0)).getType());
    }

    @Test
    void breed_combinesIndexesUsingBeforeAndAfterLists() {
        PokemonEngine engine = new PokemonEngine();
        ReflectionTestUtils.setField(engine, "beforeNames", List.of(makeData(10, "Foo"), makeData(11, "Bar")));
        ReflectionTestUtils.setField(engine, "afterNames", List.of(makeData(20, "Baz"), makeData(21, "Qux")));

        PokemonEngine.Outcome out = engine.breed(List.of(0, 1));
        assertSame(PokemonEngine.Outcome.Type.BRED, out.getType());
        assertEquals("Foo", out.getBreedFrom().getName());
        assertEquals("Qux", out.getBreedTo().getName());
        assertTrue(out.getUpdatedCollection().isEmpty(), "breed should clear collection");
    }

    @Test
    void breed_unavailableWhenIndexOutOfRange() {
        PokemonEngine engine = new PokemonEngine();
        ReflectionTestUtils.setField(engine, "beforeNames", List.of(makeData(10, "Foo")));
        ReflectionTestUtils.setField(engine, "afterNames", List.of(makeData(20, "Bar")));
        PokemonEngine.Outcome out = engine.breed(Arrays.asList(5, 0));
        assertSame(PokemonEngine.Outcome.Type.UNAVAILABLE, out.getType());
    }

    @Test
    void isAvailable_falseWhenAllListEmpty() {
        PokemonEngine engine = new PokemonEngine();
        ReflectionTestUtils.setField(engine, "all", Collections.emptyList());
        assertTrue(!engine.isAvailable());
    }

    private static PokemonData makeData(int id, String name) {
        return new PokemonData(id, name);
    }
}
