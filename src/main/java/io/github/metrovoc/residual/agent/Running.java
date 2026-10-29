package io.github.metrovoc.residual.agent;

import io.github.metrovoc.residual.learn.Dataset;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Runs a program a step at a time. Blocks and checks take no time; an act takes as long as it takes. A round of a block
 * that took no time at all, like {@code repeat (if ripe (harvest))} with nothing ripe, waits a second before the next,
 * and no more than {@link #BUDGET} steps go by in one tick, so no program can hold the world up.
 */
final class Running extends Work {
    /** Steps that take no time, at most, in one tick. */
    static final int BUDGET = 32;
    /** How close whoever it works for counts as near. */
    static final int NEAR = 8;

    final Program program;
    private final ArrayDeque<Frame> frames = new ArrayDeque<>();
    /** The routines it has run, kept from round to round so that each picks up where it left off. */
    private final Map<Program.Verb, Work> routines = new EnumMap<>(Program.Verb.class);
    @Nullable
    private Acts.Action doing;
    private String doingText = "";
    private boolean over;

    /** A block being run: its steps, the one after the step under way, and the round it is on. */
    private static final class Frame {
        final List<Program.Step> steps;
        @Nullable
        final Program.Step block;
        int next;
        int round;
        int startedAt;

        Frame(List<Program.Step> steps, @Nullable Program.Step block, int now) {
            this.steps = steps;
            this.block = block;
            this.startedAt = now;
        }
    }

    Running(Agent a, Program program) {
        super(a);
        this.program = program;
        frames.push(new Frame(program.steps(), null, a.tickCount));
    }

    /** Whether it has run every step there is. */
    boolean over() {
        return over;
    }

    @Override
    void tick(ServerLevel level) {
        for (int budget = BUDGET; budget > 0; budget--) {
            if (doing != null) {
                Outcome o = doing.tick(level);
                if (o == null) {
                    return;
                }
                doing = null;
                a.trace(o.ok() ? Trace.Kind.DONE : Trace.Kind.FAILED, doingText, o.key(), o.args());
                if (a.busy()) {
                    return;
                }
                continue;
            }
            Program.Step step = next(level);
            if (step == null) {
                return;
            }
            start(level, step);
        }
    }

    private void start(ServerLevel level, Program.Step step) {
        switch (step) {
            case Program.Act act -> act(act);
            case Program.Repeat r -> frames.push(new Frame(r.body(), r, a.tickCount));
            case Program.If i -> frames.push(new Frame(check(level, step, i.check()) ? i.then() : i.otherwise(), null, a.tickCount));
            case Program.Until u -> {
                if (!check(level, step, u.check())) {
                    frames.push(new Frame(u.body(), u, a.tickCount));
                }
            }
            case Program.WaitUntil w -> begin(step, level2 -> {
                if (holds(level2, w.check())) {
                    return Outcome.DONE;
                }
                a.status("residual.agent.status.waiting_until", Program.text(w.check()));
                a.pause(10);
                return null;
            });
        }
    }

    private void act(Program.Act act) {
        switch (act.verb()) {
            // fetching is taking, coming and giving, and the trace shows each
            case FETCH -> frames.push(new Frame(List.of(new Program.Act(Program.Verb.TAKE, act.count(), act.thing(), 0),
                    new Program.Act(Program.Verb.COME), new Program.Act(Program.Verb.GIVE, act.count(), act.thing(), 0)), null, a.tickCount));
            case FARM, STORE, SORT, HAUL, BUILD -> {
                Work w = routines.computeIfAbsent(act.verb(), v -> switch (v) {
                    case FARM -> new Farming(a);
                    case STORE -> new Storing(a);
                    case SORT -> new Sorting(a);
                    case HAUL -> new Hauling(a);
                    default -> new Building(a);
                });
                w.outcome = null;
                begin(act, level -> {
                    w.tick(level);
                    return w.outcome;
                });
            }
            default -> begin(act, Acts.of(a, act));
        }
    }

    private void begin(Program.Step step, Acts.Action action) {
        doing = action;
        doingText = Program.text(step);
        a.trace(Trace.Kind.RUNNING, doingText, "", List.of());
    }

    /**
     * The next step to start, or null: the program is over, or a round of a block went by in no time and it waits a
     * second before the next.
     */
    @Nullable
    private Program.Step next(ServerLevel level) {
        while (!frames.isEmpty()) {
            Frame f = frames.peek();
            if (f.next < f.steps.size()) {
                if (f.next == 0) {
                    f.startedAt = a.tickCount;
                }
                return f.steps.get(f.next++);
            }
            boolean idle = f.startedAt == a.tickCount;
            boolean again = switch (f.block) {
                case Program.Repeat r -> r.times() == 0 || f.round + 1 < r.times();
                case Program.Until u -> !check(level, u, u.check());
                case null, default -> false;
            };
            if (!again) {
                frames.pop();
                continue;
            }
            f.next = 0;
            f.round++;
            f.startedAt = a.tickCount;
            if (idle) {
                a.status("residual.agent.status.nothing_yet");
                a.pause(20);
                return null;
            }
        }
        over = true;
        return null;
    }

    /** Finds out whether {@code check} holds, and notes it in the trace. */
    private boolean check(ServerLevel level, Program.Step step, Program.Check check) {
        boolean yes = holds(level, check);
        a.trace(yes ? Trace.Kind.YES : Trace.Kind.NO, Program.text(step), "", List.of());
        return yes;
    }

    private boolean holds(ServerLevel level, Program.Check c) {
        boolean yes = switch (c.sense()) {
            case HAVE -> Acts.carried(a, kind(c.thing())) >= Math.max(1, c.count());
            case STOCKED -> Acts.stocked(level, a, kind(c.thing())) >= Math.max(1, c.count());
            case RIPE -> Farming.plots(level, a.home()).stream().anyMatch(p -> Farming.state(level, p) == Dataset.RIPE);
            case FULL -> a.full();
            case NIGHT -> level.isDarkOutside();
            case NEAR -> {
                ServerPlayer p = a.boss();
                yield p != null && p.distanceToSqr(a) <= NEAR * NEAR;
            }
            case POWERED -> c.mark() <= a.marks().size() && level.hasNeighborSignal(a.marks().get(c.mark() - 1));
        };
        return yes != c.not();
    }

    private static java.util.function.Predicate<net.minecraft.world.item.ItemStack> kind(String thing) {
        Things.Wanted wanted = Things.of(thing);
        return s -> wanted.exactly(s) || wanted.loosely(s);
    }
}
