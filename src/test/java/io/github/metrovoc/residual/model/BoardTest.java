package io.github.metrovoc.residual.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.item.SuitAbility;
import io.github.metrovoc.residual.learn.Blueprints;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardTest {
    private static final long MEMORY = 160_000;
    private static final UUID ID = UUID.fromString("d291c174-4f75-4d7e-a8d3-771071be5b71");

    /** A model of this size whose pilot holds every flight in both forms, passing Survey as asked. */
    private static Board holding(long parameters, int eight, int four, boolean survey) {
        float s = survey ? 1f : 0.1f;
        return new Board(ID, 1000, parameters, new Board.Form(8, eight, 1f, s), new Board.Form(4, four, 1f, s));
    }

    @Test
    void computeIsTheWeightsCarriedTimesTheShareOfFlightsHeld() {
        Board board = new Board(ID, 1000, 100_000, new Board.Form(8, 100_000, 0.96875f, 1f), new Board.Form(4, 50_000, 0.9375f, 1f));
        assertEquals(96_875, board.compute(MEMORY));
        assertSame(board.int8(), board.running(MEMORY));
    }

    @Test
    void itRunsThePreciseFormWhenItFitsAndDoesAsWell() {
        Board board = holding(100_000, 106_000, 56_000, true);
        assertEquals(8, board.running(MEMORY).bits());
    }

    @Test
    void itRunsTheSmallFormWhenItAloneFits() {
        Board board = holding(244_302, 259_572, 137_421, true);
        assertEquals(4, board.running(MEMORY).bits());
        assertEquals(244_302, board.compute(MEMORY));
    }

    @Test
    void itRunsTheSmallFormWhenItDoesBetter() {
        Board board = new Board(ID, 1000, 100_000, new Board.Form(8, 106_000, 0.90625f, 1f), new Board.Form(4, 56_000, 1f, 1f));
        assertEquals(4, board.running(MEMORY).bits());
    }

    @Test
    void aFormThatFailsPilotOrDoesNotFitGivesNothing() {
        Board failing = new Board(ID, 1000, 100_000, new Board.Form(8, 106_000, 0.875f, 1f), new Board.Form(4, 56_000, 0.5f, 1f));
        assertEquals(0, failing.compute(MEMORY));
        assertNull(failing.running(MEMORY));
        Board huge = holding(1_000_000, 1_062_500, 562_500, true);
        assertEquals(0, huge.compute(MEMORY));
        assertNull(huge.running(MEMORY));
    }

    @Test
    void eachWidthCarriesWhatTheFieldNotesSay() {
        // two-block transformers that pass Pilot and Survey, their real sizes from QuantizedTest and the calibration
        assertEquals(EnumSet.of(SuitAbility.NIGHT_VISION, SuitAbility.STEP_ASSIST), SuitAbility.running(32_334, 0, true));
        assertEquals(EnumSet.of(SuitAbility.NIGHT_VISION, SuitAbility.STEP_ASSIST, SuitAbility.VEIN_MINING),
                SuitAbility.running(66_894, off(SuitAbility.REACH, SuitAbility.SHIELD), true));
        assertEquals(EnumSet.complementOf(EnumSet.of(SuitAbility.FLIGHT)), SuitAbility.running(113_742, 0, true));
        assertTrue(SuitAbility.FLIGHT.compute > 113_742);
        // three blocks 64 wide, at 4 bits: the flight with room beside it
        assertEquals(EnumSet.of(SuitAbility.REACH, SuitAbility.FLIGHT), SuitAbility.running(163_470,
                off(SuitAbility.NIGHT_VISION, SuitAbility.STEP_ASSIST, SuitAbility.SHIELD, SuitAbility.VEIN_MINING), true));
        assertEquals(EnumSet.allOf(SuitAbility.class), SuitAbility.running(244_302, 0, true));
        assertFalse(SuitAbility.running(239_999, 0, true).contains(SuitAbility.FLIGHT));
    }

    @Test
    void whatRunsIsCheapestFirstAsFarAsItGoes() {
        assertEquals(EnumSet.of(SuitAbility.NIGHT_VISION, SuitAbility.STEP_ASSIST, SuitAbility.REACH, SuitAbility.SHIELD), SuitAbility.running(66_894, 0, true));
        // turning two off frees enough for vein mining
        assertEquals(EnumSet.of(SuitAbility.NIGHT_VISION, SuitAbility.STEP_ASSIST, SuitAbility.VEIN_MINING), SuitAbility.running(66_894, off(SuitAbility.REACH, SuitAbility.SHIELD), true));
        assertTrue(SuitAbility.running(7_999, 0, true).isEmpty());
    }

    @Test
    void veinMiningTakesAModelThatPassesSurvey() {
        int cheap = off(SuitAbility.NIGHT_VISION, SuitAbility.STEP_ASSIST, SuitAbility.REACH, SuitAbility.SHIELD, SuitAbility.FLIGHT);
        assertEquals(EnumSet.of(SuitAbility.VEIN_MINING), SuitAbility.running(244_302, cheap, true));
        assertTrue(SuitAbility.running(244_302, cheap, false).isEmpty());
        // what it leaves goes to the flight
        assertEquals(EnumSet.complementOf(EnumSet.of(SuitAbility.VEIN_MINING)), SuitAbility.running(244_302, 0, false));
    }

    private static int off(SuitAbility... abilities) {
        int off = 0;
        for (SuitAbility a : abilities) {
            off |= 1 << a.ordinal();
        }
        return off;
    }

    @Test
    void anUntrainedModelIsMeasuredAndGivesNothing() throws Exception {
        CompiledGraph model = CompiledGraph.compile(Blueprints.gpt(8, 1, 1), Env.WORLD, null, 2);
        Board board = Board.measure(new ModelInfo(ID, model.parameterCount(), 0, Map.of(), true), model);
        assertEquals(model.parameterCount(), board.parameters());
        assertEquals(Quantized.of(model, 8).bytes(), board.int8().bytes());
        assertEquals(Quantized.of(model, 4).bytes(), board.int4().bytes());
        assertTrue(board.int4().bytes() < board.int8().bytes());
        assertEquals(0, board.compute(MEMORY));
    }

    @Test
    void itSurvivesASave() {
        Board board = holding(244_302, 259_572, 137_421, true);
        Board back = Board.CODEC.parse(JsonOps.INSTANCE, Board.CODEC.encodeStart(JsonOps.INSTANCE, board).getOrThrow()).getOrThrow();
        assertEquals(board, back);
    }
}
