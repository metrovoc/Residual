package io.github.metrovoc.residual.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProgramTest {
    @Test
    void programsReadBackAsTheyWereWritten() {
        for (String text : List.of("take bread, come, give bread", "take 5 iron ingots, come, give 5 iron ingots",
                "repeat (harvest, plant wheat seeds, put wheat)", "repeat 3 (go 2, wait 10)", "put",
                "if ripe (harvest) else (wait 20)", "if not have 3 bread (take 3 bread)", "until full (pickup, wait 2)",
                "wait until night, home", "place cobblestone 4", "say all done", "repeat (if powered 1 (store) else (farm))",
                "repeat (pickup, sort)",
                "")) {
            Program.Parsed p = Program.parse(text);
            assertTrue(p.ok(), text + " → " + p.problem());
            assertEquals(text, p.program().toString());
        }
    }

    @Test
    void readingIsForgivingAboutSpacesAndCase() {
        assertEquals("take 2 bread, come", Program.parse("  Take 2  bread ,come ").program().toString());
        assertEquals("repeat (harvest)", Program.parse("repeat(harvest)").program().toString());
        assertEquals("repeat (harvest, put wheat)", Program.parse("repeat (\n  harvest,\n\tput wheat\n)").program().toString());
    }

    @Test
    void aProgramThatGoesOnForGoodSaysSo() {
        assertTrue(Program.parse("repeat (farm)").program().endless());
        assertTrue(Program.parse("come, stay").program().endless());
        assertFalse(Program.parse("repeat 3 (farm)").program().endless());
        assertFalse(Program.parse("if ripe (repeat (harvest))").program().endless());
        assertFalse(Program.parse("").program().endless());
    }

    @Test
    void aStepReadsAloneByWhatStartsIt() {
        Program p = Program.parse("repeat 2 (if not have 3 bread (fetch 3 bread)), until full (pickup), wait until powered 1").program();
        assertEquals("repeat 2", Program.text(p.steps().get(0)));
        assertEquals("if not have 3 bread", Program.text(((Program.Repeat) p.steps().get(0)).body().getFirst()));
        assertEquals("until full", Program.text(p.steps().get(1)));
        assertEquals("wait until powered 1", Program.text(p.steps().get(2)));
        assertEquals("fetch 3 bread", Program.text(((Program.If) ((Program.Repeat) p.steps().get(0)).body().getFirst()).then().getFirst()));
    }

    @Test
    void aSlipSaysWhatAndWhere() {
        assertProblem("dance", "residual.program.error.verb", "dance", 0);
        assertProblem("take bread, jump", "residual.program.error.verb", "jump", 12);
        assertProblem("repeat (harvest", "residual.program.error.close", "", 15);
        assertProblem("go 9", "residual.program.error.range", "9", 3);
        assertProblem("go home", "residual.program.error.number", "home", 3);
        assertProblem("take", "residual.program.error.thing", "", 4);
        assertProblem("place stone", "residual.program.error.mark", "stone", 6);
        assertProblem("if hungry (come)", "residual.program.error.sense", "hungry", 3);
        assertProblem("come here", "residual.program.error.stray", "here", 5);
        assertProblem("take bread,", "residual.program.error.missing", "", 11);
        assertProblem("repeat (repeat (repeat (repeat (repeat (come)))))", "residual.program.error.deep", "", 39);
        assertFalse(Program.parse("say " + "a".repeat(Program.LONGEST)).ok());
    }

    @Test
    void piecesJoinTheEndAsTheyMust() {
        assertEquals("harvest", Program.append("", "harvest", Program.Join.STEP));
        assertEquals("harvest, plant ", Program.append("harvest ", "plant ", Program.Join.STEP));
        assertEquals("harvest, plant wheat seeds", Program.append("harvest, plant ", "wheat seeds", Program.Join.WORD));
        assertEquals("repeat (harvest", Program.append("repeat (", "harvest", Program.Join.STEP));
        assertEquals("come, go 1", Program.append("come,", "go 1", Program.Join.STEP));
        assertEquals("if ripe (harvest) else (", Program.append("if ripe (harvest)", " else (", Program.Join.ON));
        assertEquals("repeat (harvest)", Program.append("repeat (harvest", ")", Program.Join.ON));
        // what plainly comes next comes by itself
        assertEquals("if not ripe (", Program.append("if not ", "ripe", Program.Join.WORD));
        assertEquals("until have bread (", Program.append("until have ", "bread", Program.Join.WORD));
        assertEquals("place oak planks 1", Program.append("place ", "oak planks", Program.Join.WORD));
        // but not when the slip is elsewhere, or the piece is not done
        assertEquals("if ripe , harvest", Program.append("if ripe ,", "harvest", Program.Join.WORD));
        assertEquals("if have ", Program.append("if ", "have ", Program.Join.WORD));
        assertEquals("place oak planks 9", Program.append("place oak planks", "9", Program.Join.WORD));
    }

    @Test
    void sizeCountsBlocksAndWhatIsInThem() {
        assertEquals(5, Program.parse("repeat (harvest, if ripe (harvest) else (wait 5))").program().size());
    }

    private static void assertProblem(String text, String key, String word, int at) {
        Program.Parsed p = Program.parse(text);
        assertFalse(p.ok(), text);
        assertEquals(new Program.Problem(key, word, at), p.problem(), text);
    }
}
