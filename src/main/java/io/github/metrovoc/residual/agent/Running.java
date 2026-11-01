package io.github.metrovoc.residual.agent;

import io.github.metrovoc.residual.net.ProgramSync;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Runs a program a step at a time. Blocks and checks take no time; an act takes as long as it takes. A round of a loop
 * that does nothing at all, like {@code repeat (if ripe (harvest))} with nothing ripe, waits a second before the next, and no
 * more than {@link #BUDGET} steps go by in one tick, so no program can hold the world up.
 *
 * <p>The steps under way are a strand: the program's own, and one for each handler its events start. A handler with a
 * higher priority than the strand under way goes on top of it, and when it is over that strand goes on where it was.
 */
final class Running implements ProgramView {
    /** Steps that take no time, at most, in one tick. */
    static final int BUDGET = 32;
    /** How close whoever it works for counts as near. */
    static final int NEAR = 8;
    /** How deep steps may call steps, at most. */
    static final int DEEPEST_CALL = 64;
    /** Ticks between two looks at what its events listen for. */
    static final int LISTEN_EVERY = 2;

    final Body a;
    final Program program;
    final Eval eval;
    final Debug debug;
    private final Map<String, Program.To> procedures = new HashMap<>();
    private final List<Handler> handlers = new ArrayList<>();
    private final Map<Program.Step, Integer> numbers = new IdentityHashMap<>();
    private final ArrayDeque<Strand> strands = new ArrayDeque<>();
    /** The routines it has run, kept from round to round so that each picks up where it left off. */
    private final Map<Program.Verb, Work> routines = new EnumMap<>(Program.Verb.class);
    /** A breakpoint's step, to start when it goes on again. */
    @Nullable
    private Program.Step held;
    private boolean over;

    /** A list of steps being run: where it is, and the round a loop is on. */
    private static final class Frame {
        final List<Program.Step> steps;
        @Nullable
        final Program.Step block;
        int next;
        int round;
        int startedAt;
        /** Whether a round of this loop did something, so it need not wait before the next. */
        boolean worked;
        /** How many rounds a {@code repeat} takes, or -1 for good. */
        int times = -1;
        /** What a {@code for each} has yet to go through. */
        @Nullable
        Iterator<Value> each;
        /** What steps called were given; set on the frame of a call. */
        @Nullable
        Map<String, Value> locals;

        Frame(List<Program.Step> steps, @Nullable Program.Step block, int now) {
            this.steps = steps;
            this.block = block;
            this.startedAt = now;
        }
    }

    /** Steps under way at one priority: the act in hand, and what comes after it. */
    private static final class Strand {
        final ArrayDeque<Frame> frames = new ArrayDeque<>();
        final int priority;
        @Nullable
        Acts.Action doing;
        @Nullable
        Program.Step doingStep;
        String doingText = "";
        @Nullable
        Map<String, Value> locals;
        int calls;
        /** The ticks of a pause something cut short. */
        int pausedFor;

        Strand(int priority) {
            this.priority = priority;
        }
    }

    /** A handler, with what it last saw of what it listens for. */
    private static final class Handler {
        final Program.On on;
        boolean pending;
        boolean started;
        double seen;
        @Nullable
        BlockState seenBlock;
        @Nullable
        Value seenValue;
        long nextTimer;

        Handler(Program.On on) {
            this.on = on;
        }
    }

    Running(Body a, Program program) {
        this(a, program, new Debug());
    }

    Running(Body a, Program program, Debug debug) {
        this.a = a;
        this.program = program;
        this.debug = debug;
        this.eval = new Eval(a);
        int number = 0;
        for (Program.Line line : program.lines()) {
            numbers.put(line.step(), number++);
        }
        for (Program.Step s : program.steps()) {
            if (s instanceof Program.To t) {
                procedures.put(t.name(), t);
            } else if (s instanceof Program.On on) {
                handlers.add(new Handler(on));
            }
        }
        Strand main = new Strand(0);
        main.frames.push(new Frame(program.steps(), null, a.clock()));
        strands.push(main);
    }

    /** Whether it has run every step there is, with nothing it still listens for. */
    boolean over() {
        return over;
    }

    /** Ends it, handlers and all. */
    void end() {
        strands.clear();
        handlers.clear();
        over = true;
    }

    /** Looks at what it listens for, and starts what has come due, cutting short the pause of what it interrupts. */
    void poll(ServerLevel level) {
        eval.refuel();
        if (!handlers.isEmpty() && a.clock() % LISTEN_EVERY == 0) {
            listen(level);
        }
        interrupt();
    }

    void tick(ServerLevel level) {
        poll(level);
        for (int budget = BUDGET; budget > 0; budget--) {
            Strand s = strands.peek();
            if (s == null) {
                over = handlers.isEmpty();
                if (!over) {
                    a.status("residual.agent.status.listening");
                }
                return;
            }
            eval.locals = s.locals;
            if (s.pausedFor > 0) {
                // what cut in is over: the rest of its pause
                a.pause(s.pausedFor);
                s.pausedFor = 0;
                return;
            }
            if (s.doing != null) {
                Outcome o;
                try {
                    o = s.doing.tick(level);
                } catch (Eval.Fault f) {
                    o = Outcome.failed(f.key, f.args.toArray());
                }
                if (o == null) {
                    return;
                }
                boolean yields = s.doing.yields();
                s.doing = null;
                s.doingStep = null;
                a.trace(o.ok() ? Trace.Kind.DONE : Trace.Kind.FAILED, s.doingText, o.key(), o.args());
                if (a.busy() || yields) {
                    return;
                }
                continue;
            }
            if (debug.paused && !debug.stepping) {
                a.status("residual.agent.status.paused");
                return;
            }
            Program.Step step = held;
            held = null;
            if (step == null) {
                step = next(level, s);
                if (step == null) {
                    if (s.frames.isEmpty()) {
                        strands.remove(s);
                        continue;
                    }
                    return;
                }
                Integer number = numbers.get(step);
                if (number != null && (debug.breaks.contains(number) || step instanceof Program.Pause) && !debug.stepping) {
                    debug.paused = true;
                    debug.at = number;
                    held = step;
                    a.status("residual.agent.status.paused");
                    a.trace(Trace.Kind.NOTE, Program.text(step), "residual.trace.paused", List.of());
                    return;
                }
            }
            debug.stepping = false;
            Integer number = numbers.get(step);
            debug.at = number == null ? -1 : number;
            start(level, s, step);
        }
    }

    // ---------------------------------------------------------------- events

    /** Looks at what each handler listens for, and marks those it has happened to. */
    private void listen(ServerLevel level) {
        for (Handler h : handlers) {
            try {
                if (happened(level, h)) {
                    h.pending = true;
                }
            } catch (Eval.Fault f) {
                a.trace(Trace.Kind.FAILED, Program.text(h.on), f.key, f.args);
            }
        }
    }

    private boolean happened(ServerLevel level, Handler h) {
        Program.On on = h.on;
        BlockPos at = on.where() == null ? a.place() : placeOf(on.where());
        boolean first = !h.started;
        h.started = true;
        switch (on.event()) {
            case REDSTONE -> {
                double signal = at != null && level.hasNeighborSignal(at) ? 1 : 0;
                boolean rose = !first && signal > h.seen;
                h.seen = signal;
                return rose;
            }
            case ITEM -> {
                Places.Ends ends = Places.items(a, on.where() == null ? Places.spot(a, "front") : eval.eval(on.where()));
                Picker pick = Picks.of(eval, on.what());
                double[] n = {0};
                if (ends != null) {
                    ends.forEach((kind, amount) -> {
                        if (pick.passes(kind.toStack())) {
                            n[0] += amount;
                        }
                    });
                }
                boolean arrived = !first && n[0] > h.seen;
                h.seen = n[0];
                return arrived;
            }
            case BLOCK -> {
                BlockState state = at == null ? null : level.getBlockState(at);
                boolean changed = !first && state != h.seenBlock;
                h.seenBlock = state;
                return changed;
            }
            case TIMER -> {
                long now = a.clock();
                if (first) {
                    h.nextTimer = now + every(on);
                    return false;
                }
                if (now >= h.nextTimer) {
                    h.nextTimer = now + every(on);
                    return true;
                }
                return false;
            }
            case CHANGE -> {
                Value v = eval.eval(on.arg());
                boolean changed = !first && (h.seenValue == null || !eval.same(v, h.seenValue));
                h.seenValue = v;
                return changed;
            }
            case WHEN -> {
                double now = eval.eval(on.arg()).truth() ? 1 : 0;
                boolean rose = now > h.seen;
                h.seen = now;
                return rose;
            }
        }
        return false;
    }

    private long every(Program.On on) {
        return Math.max(1, Math.round(Eval.num(eval.eval(on.arg())) * 20));
    }

    @Nullable
    private BlockPos placeOf(Expr where) {
        Value.Place p = Places.place(a, eval.eval(where));
        return p == null ? null : p.pos();
    }

    /** Starts the first handler due whose priority is higher than what is under way. */
    private void interrupt() {
        Strand under = strands.peek();
        Handler best = null;
        for (Handler h : handlers) {
            if (h.pending && (under == null || h.on.priority() > under.priority) && (best == null || h.on.priority() > best.on.priority())) {
                best = h;
            }
        }
        if (best == null) {
            return;
        }
        best.pending = false;
        if (under != null) {
            under.pausedFor = a.unpause();
        }
        Strand s = new Strand(best.on.priority());
        s.frames.push(new Frame(best.on.body(), null, a.clock()));
        strands.push(s);
        a.trace(Trace.Kind.NOTE, Program.text(best.on), "residual.trace.event", List.of());
    }

    // ---------------------------------------------------------------- steps

    private void start(ServerLevel level, Strand s, Program.Step step) {
        try {
            switch (step) {
                case Program.Act act -> act(level, s, act);
                case Program.Repeat r -> {
                    Frame f = new Frame(r.body(), r, a.clock());
                    f.times = r.times() == null ? -1 : (int) Eval.num(eval.eval(r.times()));
                    if (f.times != 0) {
                        s.frames.push(f);
                    }
                }
                case Program.If i -> s.frames.push(new Frame(check(step, i.check()) ? i.then() : i.otherwise(), null, a.clock()));
                case Program.Until u -> {
                    if (!check(step, u.check())) {
                        s.frames.push(new Frame(u.body(), u, a.clock()));
                    }
                }
                case Program.While w -> {
                    if (check(step, w.check())) {
                        s.frames.push(new Frame(w.body(), w, a.clock()));
                    }
                }
                case Program.WaitUntil w -> begin(s, step, level2 -> {
                    if (eval.eval(w.check()).truth()) {
                        return Outcome.DONE;
                    }
                    a.status("residual.agent.status.waiting_until", w.check().text());
                    a.pause(10);
                    return null;
                });
                case Program.ForEach f -> {
                    Iterator<Value> each = eval.each(eval.eval(f.list()));
                    if (each.hasNext()) {
                        eval.set(f.name(), each.next());
                        Frame frame = new Frame(f.body(), f, a.clock());
                        frame.each = each;
                        s.frames.push(frame);
                    }
                }
                case Program.Assign set -> {
                    Value v = eval.eval(set.value());
                    eval.set(set.name(), v);
                    Frame top = s.frames.peek();
                    if (top != null) {
                        top.worked = true;
                    }
                    a.trace(Trace.Kind.DONE, Program.text(step), "residual.program.now", List.of(clip(v.show())));
                }
                case Program.Invoke c -> invoke(s, c);
                case Program.Return r -> {
                    Value v = r.value() == null ? Value.NONE : eval.eval(r.value());
                    eval.vars.put("$result", v);
                    unwind(s, true);
                }
                case Program.Leave l -> unwind(s, false);
                case Program.Next n -> {
                    while (!s.frames.isEmpty() && !isLoop(s.frames.peek())) {
                        if (s.frames.peek().locals != null) {
                            break;
                        }
                        s.frames.pop();
                    }
                    Frame top = s.frames.peek();
                    if (top != null) {
                        top.next = top.steps.size();
                        top.worked = true;
                    }
                }
                case Program.Stop st -> end();
                case Program.Pause p -> {
                }
                case Program.To t -> {
                }
                case Program.On on -> {
                }
            }
        } catch (Eval.Fault f) {
            a.trace(Trace.Kind.FAILED, Program.text(step), f.key, f.args);
        }
    }

    private static boolean isLoop(Frame f) {
        return f.block != null;
    }

    /** Leaves the loop under way, or with {@code call} the steps called. */
    private void unwind(Strand s, boolean call) {
        while (!s.frames.isEmpty()) {
            Frame f = s.frames.pop();
            if (call ? f.locals != null : isLoop(f)) {
                if (f.locals != null) {
                    leaveCall(s);
                }
                return;
            }
            if (f.locals != null) {
                leaveCall(s);
                return;
            }
        }
    }

    private void leaveCall(Strand s) {
        s.calls--;
        s.locals = null;
        for (Frame f : s.frames) {
            if (f.locals != null) {
                s.locals = f.locals;
                break;
            }
        }
        eval.locals = s.locals;
    }

    private void invoke(Strand s, Program.Invoke c) {
        Program.To to = procedures.get(c.name());
        if (to == null) {
            throw new Eval.Fault("residual.program.failed.no_such_steps", c.name());
        }
        if (s.calls >= DEEPEST_CALL) {
            throw new Eval.Fault("residual.program.failed.too_deep", DEEPEST_CALL);
        }
        Map<String, Value> locals = new HashMap<>();
        for (int i = 0; i < to.params().size(); i++) {
            locals.put(to.params().get(i), i < c.args().size() ? eval.eval(c.args().get(i)) : Value.NONE);
        }
        Frame f = new Frame(to.body(), null, a.clock());
        f.locals = locals;
        s.frames.push(f);
        s.locals = locals;
        s.calls++;
    }

    private void act(ServerLevel level, Strand s, Program.Act act) {
        switch (act.verb()) {
            // fetching is taking, coming and giving, and the trace shows each
            case FETCH -> s.frames.push(new Frame(List.of(new Program.Act(Program.Verb.TAKE, act.n(), act.what(), 0, "", List.of()),
                    new Program.Act(Program.Verb.COME), new Program.Act(Program.Verb.GIVE, act.n(), act.what(), 0, "", List.of())), null, a.clock()));
            case FARM, STORE, SORT, HAUL, BUILD, COPY, MOVE -> {
                if (!(a instanceof Agent agent)) {
                    begin(s, act, level2 -> Outcome.failed("residual.program.failed.no_legs"));
                    return;
                }
                Work w = routines.computeIfAbsent(act.verb(), v -> switch (v) {
                    case FARM -> new Farming(agent);
                    case STORE -> new Storing(agent);
                    case SORT -> new Sorting(agent);
                    case HAUL -> new Hauling(agent);
                    case COPY -> new Copying(agent, false);
                    case MOVE -> new Copying(agent, true);
                    default -> new Building(agent);
                });
                w.outcome = null;
                begin(s, act, level2 -> {
                    w.tick(level2);
                    return w.outcome;
                });
            }
            case RUN -> {
                String text = card(act);
                Program.Parsed p = Program.parse(text);
                if (text.isEmpty() || !p.ok()) {
                    begin(s, act, level2 -> Outcome.failed("residual.program.failed.no_card"));
                    return;
                }
                a.trace(Trace.Kind.NOTE, text, "residual.trace.card", List.of());
                for (Program.Step step : p.program().steps()) {
                    if (step instanceof Program.To t) {
                        procedures.put(t.name(), t);
                    }
                }
                s.frames.push(new Frame(p.program().steps(), null, a.clock()));
            }
            case NOTE -> {
            }
            default -> begin(s, act, Acts.of(a, eval, act));
        }
    }

    /** What the first written Program Card in the place a {@code run} names, or in its own hands, says. */
    private String card(Program.Act act) {
        Places.Ends ends = Places.items(a, act.clause("at") == null ? new Value.Spot("hands") : eval.eval(act.clause("at")));
        String[] found = {""};
        if (ends != null) {
            ends.forEach((kind, n) -> {
                String written = found[0].isEmpty() ? io.github.metrovoc.residual.item.ProgramCardItem.program(kind.toStack()) : "";
                if (!written.isEmpty()) {
                    found[0] = written;
                }
            });
        }
        return found[0];
    }

    private void begin(Strand s, Program.Step step, Acts.Action action) {
        s.doing = action;
        s.doingStep = step;
        s.doingText = Program.text(step);
        a.trace(Trace.Kind.RUNNING, s.doingText, "", List.of());
    }

    /**
     * The next step to start, or null: the strand is over, or a round of a loop went by doing nothing and it waits a
     * second before the next.
     */
    @Nullable
    private Program.Step next(ServerLevel level, Strand s) {
        while (!s.frames.isEmpty()) {
            Frame f = s.frames.peek();
            if (f.next < f.steps.size()) {
                if (f.next == 0) {
                    f.startedAt = a.clock();
                    f.worked = false;
                }
                return f.steps.get(f.next++);
            }
            boolean idle = f.startedAt == a.clock() && !f.worked;
            boolean again;
            boolean endless = false;
            try {
                again = switch (f.block) {
                    case Program.Repeat r -> {
                        endless = f.times < 0;
                        yield f.times < 0 || f.round + 1 < f.times;
                    }
                    case Program.Until u -> {
                        endless = true;
                        yield !check(u, u.check());
                    }
                    case Program.While w -> {
                        endless = true;
                        yield check(w, w.check());
                    }
                    case Program.ForEach e -> {
                        boolean more = f.each != null && f.each.hasNext();
                        if (more) {
                            eval.set(e.name(), f.each.next());
                        }
                        yield more;
                    }
                    case null, default -> false;
                };
            } catch (Eval.Fault fault) {
                a.trace(Trace.Kind.FAILED, Program.text(f.block), fault.key, fault.args);
                again = false;
            }
            if (!again) {
                s.frames.pop();
                if (f.locals != null) {
                    leaveCall(s);
                }
                Frame outer = s.frames.peek();
                if (outer != null) {
                    outer.worked |= f.worked;
                }
                continue;
            }
            f.next = 0;
            f.round++;
            f.startedAt = a.clock();
            if (idle && endless) {
                f.worked = false;
                a.status("residual.agent.status.nothing_yet");
                a.pause(20);
                return null;
            }
            f.worked = false;
        }
        return null;
    }

    /** Finds out whether {@code check} holds, and notes it in the trace. */
    private boolean check(Program.Step step, Expr check) {
        boolean yes = eval.eval(check).truth();
        a.trace(yes ? Trace.Kind.YES : Trace.Kind.NO, Program.text(step), "", List.of());
        return yes;
    }

    static String clip(String text) {
        return text.length() > 60 ? text.substring(0, 59) + "…" : text;
    }

    // ---------------------------------------------------------------- what the debugger sees

    /** Every variable it holds, for the screen: the program's, those of the steps called, its own, and its team's. */
    @Override
    public List<ProgramSync.Value> seen() {
        List<ProgramSync.Value> out = new ArrayList<>();
        Strand s = strands.peek();
        if (s != null && s.locals != null) {
            s.locals.forEach((k, v) -> out.add(new ProgramSync.Value(k, clip(v.show()))));
        }
        eval.vars.forEach((k, v) -> {
            if (out.stream().noneMatch(x -> x.name().equals(k))) {
                out.add(new ProgramSync.Value(k, clip(v.show())));
            }
        });
        a.vars().forEach((k, v) -> out.add(new ProgramSync.Value(k, clip(v.show()))));
        Channels.all(a).forEach((k, v) -> out.add(new ProgramSync.Value(k, clip(v.show()))));
        out.sort(java.util.Comparator.comparing(ProgramSync.Value::name));
        return out.size() > 32 ? List.copyOf(out.subList(0, 32)) : out;
    }

    /** What each watched expression works out to right now. */
    @Override
    public List<ProgramSync.Value> watched() {
        List<ProgramSync.Value> out = new ArrayList<>();
        Map<String, Value> saved = eval.locals;
        Strand s = strands.peek();
        eval.locals = s == null ? null : s.locals;
        for (String text : debug.watches) {
            Expr e = Program.expression(text);
            String value;
            if (e == null) {
                value = "?";
            } else {
                try {
                    eval.refuel();
                    value = eval.eval(e).show();
                } catch (Eval.Fault f) {
                    value = "?";
                }
            }
            out.add(new ProgramSync.Value(text, clip(value)));
        }
        eval.locals = saved;
        return out;
    }

    /** For {@code use} and {@code attack}: the item to hold, from its hands. */
    static int held(Body a, Picker pick) {
        for (int i = 0; i < a.carry(); i++) {
            ItemStack s = a.items().getItem(i);
            if (!s.isEmpty() && pick.passes(s)) {
                return i;
            }
        }
        return -1;
    }
}
