package io.github.metrovoc.residual.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Works out expressions for one running program. Numbers, text and the rest turn into one another as they are asked to, so
 * nothing but exhausting its budget fails: work out too much in one tick and the step fails, which stops any program
 * holding the world up.
 */
public final class Eval {
    /** Values worked out in one tick, at most. */
    public static final int FUEL = 50_000;

    /** A step that cannot go on, as a translation key and its arguments. */
    public static final class Fault extends RuntimeException {
        public final String key;
        public final transient List<String> args;

        public Fault(String key, Object... args) {
            super(key, null, false, false);
            this.key = key;
            this.args = Outcome.strings(args);
        }
    }

    public final Body body;
    /** The program's own variables. */
    public final Map<String, Value> vars = new HashMap<>();
    /** The parameters of the steps called, while it runs them. */
    @Nullable
    Map<String, Value> locals;
    private int fuel = FUEL;
    /** Where each step that fills places in turn has got to. */
    private final Map<Program.Act, Integer> turns = new java.util.IdentityHashMap<>();

    public Eval(Body body) {
        this.body = body;
    }

    /** A new tick, a new budget. */
    public void refuel() {
        fuel = FUEL;
    }

    /** Spends some of the budget; a list or area walked through spends one for each thing in it. */
    public void spend(int n) {
        fuel -= n;
        if (fuel < 0) {
            throw new Fault("residual.program.failed.too_much");
        }
    }

    public Value get(String name) {
        return switch (name.charAt(0)) {
            case '@' -> body.vars().getOrDefault(name, Value.NONE);
            case '#' -> Channels.get(body, name);
            default -> {
                if (locals != null && locals.containsKey(name)) {
                    yield locals.get(name);
                }
                yield vars.getOrDefault(name, Value.NONE);
            }
        };
    }

    public void set(String name, Value v) {
        switch (name.charAt(0)) {
            case '@' -> body.vars().put(name, v);
            case '#' -> Channels.set(body, name, v);
            default -> {
                if (locals != null && locals.containsKey(name)) {
                    locals.put(name, v);
                } else {
                    vars.put(name, v);
                }
            }
        }
    }

    /** The place in a list of {@code size} whose turn it is, for a step that fills them in turn. */
    int turn(Program.Act act, int size) {
        return size == 0 ? 0 : turns.getOrDefault(act, 0) % size;
    }

    void turned(Program.Act act, int next) {
        turns.put(act, next);
    }

    /** Works out {@code e} with {@code name} standing for {@code v}: how map and filter hand each thing to {@code $it}. */
    public Value with(String name, Value v, Expr e) {
        Map<String, Value> saved = locals;
        Map<String, Value> inner = saved == null ? new HashMap<>() : new HashMap<>(saved);
        inner.put(name, v);
        locals = inner;
        try {
            return eval(e);
        } finally {
            locals = saved;
        }
    }

    public Value eval(Expr e) {
        spend(1);
        return switch (e) {
            case Expr.Lit l -> l.value();
            case Expr.Var v -> get(v.name());
            case Expr.Spot s -> Places.spot(body, s.word());
            case Expr.Words w -> Value.of(w.words());
            case Expr.Sense s -> Senses.first(this, s);
            case Expr.Call c -> Ops.fn(c.fn()).impl().call(this, c.args());
            case Expr.Unary u -> u.op().equals("not") ? Value.of(!eval(u.e()).truth()) : Value.of(-num(eval(u.e())));
            case Expr.Binary b -> binary(b);
            case Expr.Items l -> {
                List<Value> items = new ArrayList<>(l.items().size());
                for (Expr item : l.items()) {
                    items.add(eval(item));
                }
                yield new Value.Many(List.copyOf(items));
            }
            case Expr.Pick p -> new Value.Sieve(Picks.of(this, p));
        };
    }

    private Value binary(Expr.Binary b) {
        switch (b.op()) {
            case "and" -> {
                return Value.of(eval(b.a()).truth() && eval(b.b()).truth());
            }
            case "or" -> {
                return Value.of(eval(b.a()).truth() || eval(b.b()).truth());
            }
            default -> {
            }
        }
        Value x = eval(b.a()), y = eval(b.b());
        return switch (b.op()) {
            case "=" -> Value.of(same(x, y));
            case "!=" -> Value.of(!same(x, y));
            case "<" -> Value.of(num(x) < num(y));
            case "<=" -> Value.of(num(x) <= num(y));
            case ">" -> Value.of(num(x) > num(y));
            case ">=" -> Value.of(num(x) >= num(y));
            case "+" -> plus(x, y);
            case "-" -> minus(x, y);
            case "*" -> Value.of(num(x) * num(y));
            case "/" -> Value.of(num(y) == 0 ? 0 : num(x) / num(y));
            default -> Value.of(num(y) == 0 ? 0 : num(x) % num(y));
        };
    }

    private Value plus(Value x, Value y) {
        if (x instanceof Value.Many m) {
            List<Value> out = new ArrayList<>(m.items());
            if (y instanceof Value.Many n) {
                spend(n.items().size());
                out.addAll(n.items());
            } else {
                out.add(y);
            }
            return new Value.Many(List.copyOf(out));
        }
        if (x instanceof Value.Region a && y instanceof Value.Region c) {
            return Value.Region.of(new Area.Sum(a.area(), c.area()));
        }
        if (x instanceof Value.Place p && y instanceof Value.Place q) {
            return new Value.Place(p.pos().offset(q.pos()), p.face(), p.dimension());
        }
        if (x instanceof Value.Text || y instanceof Value.Text) {
            return Value.of(text(x) + text(y));
        }
        return Value.of(num(x) + num(y));
    }

    private Value minus(Value x, Value y) {
        if (x instanceof Value.Region a && y instanceof Value.Region c) {
            return Value.Region.of(new Area.Less(a.area(), c.area()));
        }
        if (x instanceof Value.Place p && y instanceof Value.Place q) {
            return new Value.Place(p.pos().subtract(q.pos()), p.face(), p.dimension());
        }
        return Value.of(num(x) - num(y));
    }

    /** Whether two values are the same: a thing is what its name names, and a number its text. */
    public boolean same(Value x, Value y) {
        if (x instanceof Value.Text t && !(y instanceof Value.Text)) {
            return named(y, t.v());
        }
        if (y instanceof Value.Text t && !(x instanceof Value.Text)) {
            return named(x, t.v());
        }
        if (x instanceof Value.Num a && y instanceof Value.Num b) {
            return a.v() == b.v();
        }
        if (x instanceof Value.Stack a && y instanceof Value.Stack b) {
            return net.minecraft.world.item.ItemStack.isSameItemSameComponents(a.stack(), b.stack());
        }
        if (x instanceof Value.Solid a && y instanceof Value.Solid b) {
            return a.state().getBlock() == b.state().getBlock();
        }
        if (x instanceof Value.Liquid a && y instanceof Value.Liquid b) {
            return a.fluid().equals(b.fluid());
        }
        if (x instanceof Value.Place a && y instanceof Value.Place b) {
            return a.pos().equals(b.pos());
        }
        if (x instanceof Value.Creature a && y instanceof Value.Creature b) {
            return a.entity() == b.entity();
        }
        if (x instanceof Value.Many a && y instanceof Value.Many b) {
            if (a.items().size() != b.items().size()) {
                return false;
            }
            for (int i = 0; i < a.items().size(); i++) {
                if (!same(a.items().get(i), b.items().get(i))) {
                    return false;
                }
            }
            return true;
        }
        return x.getClass() == y.getClass() && x.equals(y);
    }

    private boolean named(Value x, String name) {
        return switch (x) {
            case Value.Stack s -> Things.of(name).passes(s.stack());
            case Value.Solid s -> Things.of(name).passes(s.state());
            case Value.Liquid l -> Things.of(name).passes(l.fluid());
            case Value.Creature c -> Things.of(name).passes(c.entity());
            case Value.Num n -> n.show().equals(name);
            case Value.Flag f -> f.show().equals(name);
            case Value.Nothing n -> name.isEmpty() || name.equals("nothing");
            default -> x.show().equals(name);
        };
    }

    /** A value as a number: how many of a thing, how long a list, 1 for true, what text says if it says a number. */
    public static double num(Value v) {
        return switch (v) {
            case Value.Num n -> n.v();
            case Value.Flag f -> f.v() ? 1 : 0;
            case Value.Stack s -> s.stack().getCount();
            case Value.Liquid l -> l.amount();
            case Value.Many m -> m.items().size();
            case Value.Text t -> {
                try {
                    yield Double.parseDouble(t.v().strip());
                } catch (NumberFormatException e) {
                    yield 0;
                }
            }
            default -> 0;
        };
    }

    public static String text(Value v) {
        return v.show();
    }

    /** {@code say} and the like: words with a variable anywhere, as in {@code say i have $n}. */
    public String interpolate(String words) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < words.length(); i++) {
            char c = words.charAt(i);
            if (c == '"') {
                continue;
            }
            if ((c == '$' || c == '@' || c == '#') && i + 1 < words.length() && (Character.isLetterOrDigit(words.charAt(i + 1)) || words.charAt(i + 1) == '_')) {
                int end = i + 1;
                while (end < words.length() && (Character.isLetterOrDigit(words.charAt(end)) || words.charAt(end) == '_')) {
                    end++;
                }
                out.append(get(words.substring(i, end)).show());
                i = end - 1;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Its places, for walking through an area, a list or one place; each thing as a value. */
    public java.util.Iterator<Value> each(Value v) {
        return switch (v) {
            case Value.Many m -> m.items().iterator();
            case Value.Region r -> {
                java.util.Iterator<BlockPos> at = r.area().places();
                yield new java.util.Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return at.hasNext();
                    }

                    @Override
                    public Value next() {
                        return new Value.Place(at.next());
                    }
                };
            }
            case Value.Nothing n -> java.util.Collections.emptyIterator();
            default -> List.of(v).iterator();
        };
    }

    /** The things of a value, as a list: a region's places count against the budget, since this walks them all. */
    public List<Value> list(Value v) {
        if (v instanceof Value.Many m) {
            return m.items();
        }
        List<Value> out = new ArrayList<>();
        for (java.util.Iterator<Value> it = each(v); it.hasNext(); ) {
            spend(1);
            out.add(it.next());
        }
        return out;
    }
}
