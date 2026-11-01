package io.github.metrovoc.residual.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** The language itself, run on a body that does nothing but say what it is told: variables, expressions, lists, procedures, events, priorities and the debugger. */
class LanguageTest {
    /** A body with no world: what it says is the test's result. */
    private static final class Fake implements Body {
        final List<String> said = new ArrayList<>();
        final Map<String, Value> vars = new HashMap<>();
        int clock;
        int pause;

        @Override
        public ServerLevel world() {
            return null;
        }

        @Override
        public BlockPos place() {
            return BlockPos.ZERO;
        }

        @Override
        public Vec3 center() {
            return Vec3.ZERO;
        }

        @Override
        public BlockPos home() {
            return BlockPos.ZERO;
        }

        @Override
        public List<BlockPos> marks() {
            return List.of(new BlockPos(1, 2, 3));
        }

        @Override
        public Direction facing() {
            return Direction.NORTH;
        }

        @Override
        public int clock() {
            return clock;
        }

        @Override
        public int id() {
            return 0;
        }

        @Nullable
        @Override
        public ServerPlayer boss() {
            return null;
        }

        @Nullable
        @Override
        public UUID owner() {
            return null;
        }

        @Override
        public Arrival walk(BlockPos target, double reach) {
            return Arrival.HERE;
        }

        @Override
        public void stand() {
        }

        @Override
        public void status(String key, Object... args) {
        }

        @Override
        public void pause(int ticks) {
            pause = Math.max(pause, ticks);
        }

        @Override
        public int unpause() {
            int left = pause;
            pause = 0;
            return left;
        }

        @Override
        public boolean busy() {
            return pause > 0;
        }

        @Override
        public void tell(Component what) {
            said.add(what.getString());
        }

        @Override
        public void trace(Trace.Kind kind, String step, String key, List<String> args) {
        }

        @Override
        public Container items() {
            return null;
        }

        @Override
        public int carry() {
            return 0;
        }

        @Override
        public ItemStack keep(ItemStack stack) {
            return stack;
        }

        @Override
        public boolean full() {
            return false;
        }

        @Override
        public void handle(BlockPos pos) {
        }

        @Override
        public void swing() {
        }

        @Override
        public void look(Vec3 at) {
        }

        @Nullable
        @Override
        public Entity entity() {
            return null;
        }

        @Override
        public int interval() {
            return 1;
        }

        @Override
        public int itemsPerRun() {
            return 64;
        }

        @Override
        public int fluidPerRun() {
            return 1000;
        }

        @Override
        public int energyPerRun() {
            return 64000;
        }

        @Override
        public int reach() {
            return 8;
        }

        @Override
        public boolean reaches(BlockPos target) {
            return true;
        }

        @Override
        public ResourceHandler<FluidResource> tank() {
            return null;
        }

        @Override
        public EnergyHandler battery() {
            return null;
        }

        @Override
        public Map<String, Value> vars() {
            return vars;
        }

        @Override
        public Container upgrades() {
            return null;
        }

        @Override
        public String team() {
            return "";
        }

        @Override
        public List<ItemStack> ghosts() {
            return List.of();
        }

        @Override
        public ItemStack filterModel() {
            return ItemStack.EMPTY;
        }

        @Override
        public void emit(int strength) {
        }

        @Override
        public void dry(boolean moved) {
        }

        @Override
        public int selected() {
            return 0;
        }

        @Override
        public void select(int slot) {
        }

        @Override
        public void face(Direction way) {
        }
    }

    /** What a program says in {@code ticks} ticks. */
    private static List<String> said(String text, int ticks) {
        Fake body = new Fake();
        Program.Parsed p = Program.parse(text);
        assertTrue(p.ok(), text + " → " + p.problem());
        Running r = new Running(body, p.program());
        tick(body, r, ticks);
        return body.said;
    }

    private static void tick(Fake body, Running r, int ticks) {
        for (int i = 0; i < ticks && !r.over(); i++) {
            body.clock++;
            if (body.pause > 0) {
                r.poll(null);
            }
            if (body.pause > 0) {
                body.pause--;
            } else {
                r.tick(null);
            }
        }
    }

    private static String only(String text) {
        List<String> said = said(text, 200);
        assertEquals(1, said.size(), text + " said " + said);
        return said.getFirst();
    }

    @Test
    void theNewLanguageReadsBackAsItWasWritten() {
        for (String text : List.of(
                "set $n 3", "set @kept $n + 1", "set #team count(front, iron ingot)", "set $x iron ingot",
                "while $n > 0 (set $n $n - 1)", "repeat $n (say hi)", "repeat count($list) (say hi)",
                "for each $p in box(mark(1), mark(2)) (dig at $p)", "for each $i in [1, 2, 3] (say $i)",
                "to row $n $what (repeat $n (place $what at front)), row 3 cobblestone",
                "to two (return 2), two, say $result",
                "on redstone at 1 priority 5 (say up)", "on item iron ingot at front (say more)", "on block at mark(2) (say changed)",
                "on timer 5 (say tick)", "on change #needed (say $x)", "when $n > 3 priority 2 (say big)",
                "send 64 iron ingot from front to network", "send tag(logs) or coal except oak log from 1 to each [mark(2), mark(3)]",
                "send where durability($it) < 50 from front to hands", "pump 1000 water from front to up", "charge 64000 from box(mark(1), mark(2)) to back",
                "dig 4 stone at near(8)", "use shears at entities(4, sheep) sneak", "attack at nearest(entities(8, mob))",
                "fling 8 cobblestone at front speed 1.5 pitch 30 yaw 0", "emit 15", "pulse 15 for 5", "extrude 8 stone at up", "retract 8 at up",
                "forward 3", "back", "up 2", "down", "turn left", "teleport mark(1)", "select 2", "transfer 8 to 3", "equip iron pickaxe", "refuel 4 coal",
                "provide iron ingot at front", "request 64 iron ingot at back", "push at left", "accept at right", "deliver",
                "wait 0.5", "wait $n", "breakpoint", "leave", "next", "stop", "return $x", "note first the sand",
                "sign hello there at front", "sound block note block chime", "particle flame at up",
                "set $lit property(block(front), powered) = true", "select 3, use at front, use iron hoe at pos(1, 2, 3), pickup xp, pickup white wool at near(6)",
                "if not (have 3 bread and ripe) or $n >= 2 (say yes) else (say no)", "go $p", "go mark(2)", "place stone at wall(pos(0, 0, 0), pos(3, 3, 3), y)"
        )) {
            Program.Parsed p = Program.parse(text);
            assertTrue(p.ok(), text + " → " + p.problem());
            assertEquals(text, p.program().toString());
            assertEquals(text, Program.parse(p.program().pretty()).program().toString(), "written over lines");
        }
    }

    @Test
    void aSlipInTheNewLanguageSaysWhat() {
        assertEquals("residual.program.error.variable", Program.parse("set n 3").problem().key());
        assertEquals("residual.program.error.function", Program.parse("set $x sum2(3)").problem().key());
        assertEquals("residual.program.error.args", Program.parse("set $x abs(1, 2)").problem().key());
        assertEquals("residual.program.error.top", Program.parse("repeat (to x (come))").problem().key());
        assertEquals("residual.program.error.event", Program.parse("on sunrise (come)").problem().key());
        assertEquals("residual.program.error.quote", Program.parse("set $x \"open").problem().key());
        assertEquals("residual.program.error.bracket", Program.parse("set $x [1, 2").problem().key());
        assertEquals("residual.program.error.each", Program.parse("for $x in [1] (come)").problem().key());
        assertEquals("residual.program.error.verb", Program.parse("row 3").problem().key());
    }

    @Test
    void expressionsWorkAsArithmeticDoes() {
        assertEquals("7", only("set $x 1 + 2 * 3, say $x"));
        assertEquals("9", only("set $x (1 + 2) * 3, say $x"));
        assertEquals("1", only("set $x 7 % 3, say $x"));
        assertEquals("2.5", only("set $x 5 / 2, say $x"));
        assertEquals("-4", only("set $x -(1 + 3), say $x"));
        assertEquals("true", only("set $x 1 < 2 and not (3 < 2), say $x"));
        assertEquals("false", only("set $x 1 > 2 or 3 = 4, say $x"));
        assertEquals("ab", only("set $x \"a\" + \"b\", say $x"));
        assertEquals("Mixed Case", only("set $x \"Mixed Case\", say $x"));
        assertEquals("6", only("set $x max(1, 6, 3), say $x"));
        assertEquals("4", only("set $x sqrt(16), say $x"));
        assertEquals("yes", only("set $x choose(1 < 2, \"yes\", \"no\"), say $x"));
        assertEquals("true", only("set $x iron ingot, set $y $x = iron ingot, say $y"));
    }

    @Test
    void listsDoWhatFunctionalOperatorsDo() {
        assertEquals("[2, 4, 6]", only("set $x map([1, 2, 3], $it * 2), say $x"));
        assertEquals("[2, 3]", only("set $x filter([1, 2, 3], $it > 1), say $x"));
        assertEquals("6", only("set $x sum([1, 2, 3]), say $x"));
        assertEquals("12", only("set $x sum([1, 2, 3], $it * 2), say $x"));
        assertEquals("[3, 2, 1]", only("set $x sort([1, 2, 3], -$it), say $x"));
        assertEquals("2", only("set $x first([1, 2, 3], $it > 1), say $x"));
        assertEquals("3", only("set $x count([1, 2, 3]), say $x"));
        assertEquals("2", only("set $x count([1, 2, 3], $it > 1), say $x"));
        assertEquals("10", only("set $x reduce([1, 2, 3, 4], 0, $acc + $it), say $x"));
        assertEquals("[1, 2, 3, 4]", only("set $x [1, 2] + [3, 4], say $x"));
        assertEquals("[1, 2, 3]", only("set $x range(3), say $x"));
        assertEquals("2", only("set $x get([1, 2, 3], 2), say $x"));
        assertEquals("[b, c]", only("set $x slice([\"a\", \"b\", \"c\"], 2, 3), say $x"));
        assertEquals("a-b", only("set $x join([\"a\", \"b\"], \"-\"), say $x"));
        assertEquals("[a, b]", only("set $x split(\"a,b\", \",\"), say $x"));
        assertEquals("true", only("set $x contains([1, 2], 2), say $x"));
        assertEquals("[1, 2]", only("set $x unique([1, 2, 1]), say $x"));
    }

    @Test
    void controlFlowRunsTheWayItReads() {
        assertEquals(List.of("0", "1", "2"), said("set $i 0, while $i < 3 (say $i, set $i $i + 1)", 200));
        assertEquals(List.of("a", "b"), said("for each $x in [\"a\", \"b\"] (say $x)", 200));
        assertEquals(List.of("1", "3"), said("for each $x in [1, 2, 3] (if $x = 2 (next), say $x)", 200));
        assertEquals(List.of("1"), said("for each $x in [1, 2, 3] (if $x = 2 (leave), say $x)", 200));
        assertEquals(List.of("x", "x", "x"), said("repeat 3 (say x)", 200));
        assertEquals(List.of("x", "x"), said("set $n 2, repeat $n (say x)", 200));
        assertEquals(List.of("before"), said("say before, stop, say after", 200));
        assertEquals(List.of("no"), said("if 1 > 2 (say yes) else (say no)", 200));
        assertEquals(List.of("0", "1"), said("set $i 0, until $i = 2 (say $i, set $i $i + 1)", 200));
    }

    @Test
    void procedureTakesParametersAndGivesBackAResult() {
        assertEquals(List.of("hi 1", "hi 2"), said("to greet $n (say hi $n), greet 1, greet 2", 200));
        // called before it is written, and recursion goes as deep as it may
        assertEquals("120", only("fact 5, say $result, to fact $n (if $n <= 1 (return 1), fact ($n - 1), return $n * $result)"));
        // a parameter is its own, and a program's variable is not
        assertEquals(List.of("1", "kept"), said("set $a \"kept\", to f $a (say $a), f 1, say $a", 200));
        assertEquals(List.of("in"), said("to f (say in, return, say after), f", 200));
        // a call that never ends stops at the deepest it may go and the program goes on
        assertEquals(List.of("done"), said("to f (f), f, say done", 2000));
    }

    @Test
    void variablesHaveTheirOwnScopes() {
        Fake body = new Fake();
        Running r = new Running(body, Program.parse("set @kept 5, set $gone 6").program());
        tick(body, r, 20);
        assertEquals(new Value.Num(5), body.vars.get("@kept"));
        // a body's variable outlasts the program, a program's goes with it
        Running again = new Running(body, Program.parse("say @kept $gone").program());
        tick(body, again, 20);
        assertEquals("5 nothing", body.said.getFirst());
    }

    @Test
    void eventsInterruptWhatRunsAndItGoesOnAfterwards() {
        // the program counts; when it reaches 3 the handler runs, then the count goes on from where it was
        List<String> said = said("when $n = 3 (say event), set $n 0, repeat 5 (set $n $n + 1, say $n, wait 1)", 400);
        assertEquals(List.of("1", "2", "3", "event", "4", "5"), said);
        assertEquals(List.of("tick", "tick", "tick"), said("on timer 1 (say tick), wait 3.2", 80).subList(0, 3));
        assertEquals(List.of("changed"), said("on change $x (say changed), set $x 0, wait 1, set $x 1, wait 1", 120));
    }

    @Test
    void aHigherPriorityGoesFirstAndALowerWaits() {
        // both come due at once: the high one first, the low one after it
        List<String> order = said("when $go priority 1 (say low), when $go priority 5 (say high), set $go 1, wait 1", 200);
        assertEquals(List.of("high", "low"), order);
        // a handler of no higher priority than what runs waits for it to end
        List<String> held = said("when $a priority 2 (say a1, set $b 1, wait 1, say a2), when $b priority 2 (say b), set $a 1, wait 3", 400);
        assertEquals(List.of("a1", "a2", "b"), held);
        // one of higher priority cuts in
        List<String> cut = said("when $a priority 2 (say a1, set $b 1, wait 1, say a2), when $b priority 3 (say b), set $a 1, wait 3", 400);
        assertEquals(List.of("a1", "b", "a2"), cut);
    }

    @Test
    void noProgramHoldsTheWorldUp() {
        Fake body = new Fake();
        // no act, nothing to wait for: a tick still ends after the budget
        Running r = new Running(body, Program.parse("set $n 0, repeat (set $n $n + 1)").program());
        tick(body, r, 1);
        assertTrue(Eval.num(r.eval.get("$n")) <= Running.BUDGET, "steps in a tick");
        // a round that does nothing waits a second before the next
        Fake idle = new Fake();
        Running nothing = new Running(idle, Program.parse("repeat (if 1 > 2 (say x))").program());
        tick(idle, nothing, 3);
        assertTrue(idle.pause > 0, "it waits");
        // too much worked out in one tick fails the step, and the program goes on
        assertEquals(List.of("after"), said("set $x sum(range(1000000)), say after", 200));
    }

    @Test
    void theDebuggerStopsStepsAndWatches() {
        Fake body = new Fake();
        Program program = Program.parse("say a, say b, say c").program();
        Debug debug = new Debug();
        debug.apply(Debug.Move.BREAK, 1, "");
        Running r = new Running(body, program, debug);
        tick(body, r, 10);
        assertEquals(List.of("a"), body.said, "it holds at the breakpoint");
        assertTrue(debug.paused);
        assertEquals(1, debug.at);
        debug.apply(Debug.Move.STEP, 0, "");
        tick(body, r, 10);
        assertEquals(List.of("a", "b"), body.said, "one step");
        assertTrue(debug.paused);
        debug.apply(Debug.Move.RESUME, 0, "");
        tick(body, r, 10);
        assertEquals(List.of("a", "b", "c"), body.said);
        assertFalse(debug.paused);

        // a breakpoint can be taken off, and written into the program too
        Fake other = new Fake();
        Debug second = new Debug();
        second.apply(Debug.Move.BREAK, 0, "");
        second.apply(Debug.Move.BREAK, 0, "");
        Running written = new Running(other, Program.parse("say a, breakpoint, say b").program(), second);
        tick(other, written, 10);
        assertEquals(List.of("a"), other.said);
        assertTrue(second.paused);
        second.apply(Debug.Move.RESUME, 0, "");
        tick(other, written, 10);
        assertEquals(List.of("a", "b"), other.said);

        // watched values are worked out on demand, and what cannot be read is refused
        second.apply(Debug.Move.WATCH, 0, "1 + 2");
        second.apply(Debug.Move.WATCH, 0, "1 +");
        assertEquals(1, second.watches.size());
    }

    @Test
    void theListingNumbersEveryStep() {
        Program p = Program.parse("repeat 2 (say a, if ripe (say b) else (say c)), say d").program();
        List<Program.Line> lines = p.lines();
        assertEquals(List.of("repeat 2", "say a", "if ripe", "say b", "say c", "say d"), lines.stream().map(Program.Line::text).toList());
        assertEquals(List.of(0, 1, 1, 2, 2, 0), lines.stream().map(Program.Line::depth).toList());
        assertEquals(6, p.size());
        assertNotNull(p.pretty());
    }

    @Test
    void whatIsSavedComesBackAsItWas() {
        for (Value v : List.of(Value.of(3.5), Value.of(-2), Value.of("two words"), Value.TRUE, Value.FALSE, new Value.Place(new net.minecraft.core.BlockPos(1, -2, 3)),
                new Value.Many(List.of(Value.of(1), Value.of("x"))))) {
            assertEquals(v.show(), Saved.read(v.source()).show(), v.source());
        }
    }

    /** A rough cost of the interpreter: how many steps and values a tick of a server can run. */
    @Test
    void stepsCostNextToNothing() {
        Fake body = new Fake();
        Running r = new Running(body, Program.parse("set $n 0, repeat (set $n $n + 1, if $n % 2 = 0 (set $m $n * 2) else (set $m max($n, 3)))").program());
        // warmed up, then timed
        tick(body, r, 200_000);
        long start = System.nanoTime();
        int ticks = 100_000;
        tick(body, r, ticks);
        double perTick = (System.nanoTime() - start) / 1e3 / ticks;
        System.out.printf("[language] %.2f µs a tick at the full budget of %d steps%n", perTick, Running.BUDGET);
        assertTrue(perTick < 2000, perTick + " µs a tick");
    }
}
