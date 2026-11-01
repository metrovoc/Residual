package io.github.metrovoc.residual.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.jetbrains.annotations.Nullable;

/**
 * Every function a program can call: name, how many arguments it takes, the group it sits in on the palette, and what it
 * does. The ones here need nothing but their arguments; {@link Senses} adds the ones that look at the world.
 */
public final class Ops {
    /** What a function does with its arguments as written; most work them all out first. */
    @FunctionalInterface
    public interface Impl {
        Value call(Eval e, List<Expr> args);
    }

    public record Fn(String name, int least, int most, String group, Impl impl) {
    }

    private static final Map<String, Fn> FNS = new LinkedHashMap<>();

    static {
        math();
        logic();
        text();
        lists();
        Senses.install();
    }

    private Ops() {
    }

    @Nullable
    public static Fn fn(String name) {
        return FNS.get(name);
    }

    /** Every function, in the order they are laid out on the palette. */
    public static List<Fn> all() {
        return List.copyOf(FNS.values());
    }

    /** Adds a function that looks at its arguments as written, as {@code map} does, to work out one of them many times. */
    static void lazy(String group, String name, int least, int most, Impl impl) {
        if (FNS.put(name, new Fn(name, least, most, group, impl)) != null) {
            throw new IllegalStateException(name + " twice");
        }
    }

    /** Adds a function of its worked-out arguments. */
    static void def(String group, String name, int least, int most, BiFunction<Eval, List<Value>, Value> f) {
        lazy(group, name, least, most, (e, args) -> {
            List<Value> values = new ArrayList<>(args.size());
            for (Expr a : args) {
                values.add(e.eval(a));
            }
            return f.apply(e, values);
        });
    }

    private static double n(List<Value> a, int i) {
        return Eval.num(a.get(i));
    }

    private static String t(List<Value> a, int i) {
        return Eval.text(a.get(i));
    }

    private static void math() {
        String g = "math";
        def(g, "abs", 1, 1, (e, a) -> Value.of(Math.abs(n(a, 0))));
        def(g, "floor", 1, 1, (e, a) -> Value.of(Math.floor(n(a, 0))));
        def(g, "ceil", 1, 1, (e, a) -> Value.of(Math.ceil(n(a, 0))));
        def(g, "round", 1, 1, (e, a) -> Value.of(Math.rint(n(a, 0))));
        def(g, "sqrt", 1, 1, (e, a) -> Value.of(Math.sqrt(Math.max(0, n(a, 0)))));
        def(g, "pow", 2, 2, (e, a) -> Value.of(Math.pow(n(a, 0), n(a, 1))));
        def(g, "clamp", 3, 3, (e, a) -> Value.of(Math.max(n(a, 1), Math.min(n(a, 2), n(a, 0)))));
        def(g, "min", 1, 16, (e, a) -> most(e, a, -1));
        def(g, "max", 1, 16, (e, a) -> most(e, a, 1));
        def(g, "random", 0, 3, (e, a) -> a.size() == 3 ? Senses.randomArea(e, a) : Value.of(a.isEmpty() ? Math.random() : Math.floor(Math.random() * n(a, 0)) + 1));
        def(g, "bit_and", 2, 2, (e, a) -> Value.of((long) n(a, 0) & (long) n(a, 1)));
        def(g, "bit_or", 2, 2, (e, a) -> Value.of((long) n(a, 0) | (long) n(a, 1)));
        def(g, "bit_xor", 2, 2, (e, a) -> Value.of((long) n(a, 0) ^ (long) n(a, 1)));
        def(g, "bit_not", 1, 1, (e, a) -> Value.of(~(long) n(a, 0)));
        def(g, "shift", 2, 2, (e, a) -> Value.of(n(a, 1) >= 0 ? (long) n(a, 0) << (long) n(a, 1) : (long) n(a, 0) >> (long) -n(a, 1)));
    }

    /** The least or most of its arguments, or of the list that is its one argument. */
    private static Value most(Eval e, List<Value> a, int sign) {
        List<Value> all = a.size() == 1 ? e.list(a.getFirst()) : a;
        Value best = Value.NONE;
        for (Value v : all) {
            if (best == Value.NONE || Double.compare(Eval.num(v), Eval.num(best)) * sign > 0) {
                best = v;
            }
        }
        return best;
    }

    private static void logic() {
        String g = "logic";
        def(g, "xor", 2, 2, (e, a) -> Value.of(a.get(0).truth() != a.get(1).truth()));
        lazy(g, "choose", 3, 3, (e, args) -> e.eval(e.eval(args.get(0)).truth() ? args.get(1) : args.get(2)));
        def(g, "is_nothing", 1, 1, (e, a) -> Value.of(a.getFirst() instanceof Value.Nothing));
        def(g, "type", 1, 1, (e, a) -> Value.of(switch (a.getFirst()) {
            case Value.Nothing n -> "nothing";
            case Value.Num n -> "number";
            case Value.Text t -> "text";
            case Value.Flag f -> "flag";
            case Value.Stack s -> "item";
            case Value.Solid s -> "block";
            case Value.Liquid l -> "fluid";
            case Value.Place p -> "place";
            case Value.Spot s -> "place";
            case Value.Region r -> "area";
            case Value.Creature c -> "entity";
            case Value.Many m -> "list";
            case Value.Sieve s -> "filter";
        }));
    }

    private static void text() {
        String g = "text";
        def(g, "text", 1, 1, (e, a) -> Value.of(t(a, 0)));
        def(g, "number", 1, 1, (e, a) -> Value.of(n(a, 0)));
        def(g, "upper", 1, 1, (e, a) -> Value.of(t(a, 0).toUpperCase(Locale.ROOT)));
        def(g, "lower", 1, 1, (e, a) -> Value.of(t(a, 0).toLowerCase(Locale.ROOT)));
        def(g, "trim", 1, 1, (e, a) -> Value.of(t(a, 0).strip()));
        def(g, "starts", 2, 2, (e, a) -> Value.of(t(a, 0).startsWith(t(a, 1))));
        def(g, "ends", 2, 2, (e, a) -> Value.of(t(a, 0).endsWith(t(a, 1))));
        def(g, "replace", 3, 3, (e, a) -> Value.of(t(a, 0).replace(t(a, 1), t(a, 2))));
        def(g, "split", 2, 2, (e, a) -> {
            List<Value> out = new ArrayList<>();
            for (String piece : t(a, 0).split(Pattern.quote(t(a, 1)))) {
                out.add(Value.of(piece));
            }
            return new Value.Many(List.copyOf(out));
        });
        def(g, "join", 1, 2, (e, a) -> {
            List<String> pieces = new ArrayList<>();
            for (Value v : e.list(a.getFirst())) {
                pieces.add(Eval.text(v));
            }
            return Value.of(String.join(a.size() > 1 ? t(a, 1) : "", pieces));
        });
        def(g, "matches", 2, 2, (e, a) -> {
            try {
                return Value.of(Pattern.compile(t(a, 1), Pattern.CASE_INSENSITIVE).matcher(t(a, 0)).find());
            } catch (PatternSyntaxException x) {
                return Value.FALSE;
            }
        });
    }

    /** A function of a list and something worked out for each thing in it, which it calls {@code $it}. */
    private interface Walk {
        Value call(Eval e, List<Value> list, @Nullable Expr each);
    }

    private static void walk(String name, int least, Walk w) {
        lazy("lists", name, least, 2, (e, args) -> w.call(e, e.list(e.eval(args.getFirst())), args.size() > 1 ? args.get(1) : null));
    }

    private static void lists() {
        String g = "lists";
        def(g, "list", 0, 64, (e, a) -> new Value.Many(List.copyOf(a)));
        def(g, "size", 1, 1, (e, a) -> Value.of(switch (a.getFirst()) {
            case Value.Many m -> m.items().size();
            case Value.Text x -> x.v().length();
            case Value.Region r -> {
                e.spend((int) Math.min(r.area().volume(), Eval.FUEL + 1));
                yield r.area().size();
            }
            case Value.Stack s -> s.stack().getCount();
            default -> 0;
        }));
        walk("map", 2, (e, list, each) -> {
            List<Value> out = new ArrayList<>(list.size());
            for (Value v : list) {
                out.add(e.with("$it", v, each));
            }
            return new Value.Many(List.copyOf(out));
        });
        walk("filter", 2, (e, list, each) -> {
            List<Value> out = new ArrayList<>();
            for (Value v : list) {
                if (e.with("$it", v, each).truth()) {
                    out.add(v);
                }
            }
            return new Value.Many(List.copyOf(out));
        });
        walk("sum", 1, (e, list, each) -> {
            double sum = 0;
            for (Value v : list) {
                e.spend(1);
                sum += Eval.num(each == null ? v : e.with("$it", v, each));
            }
            return Value.of(sum);
        });
        walk("sort", 1, (e, list, each) -> {
            List<Value> keys = new ArrayList<>(list.size());
            for (Value v : list) {
                e.spend(1);
                keys.add(each == null ? v : e.with("$it", v, each));
            }
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                order.add(i);
            }
            order.sort(Comparator.comparing(i -> keys.get(i), Ops::compare));
            List<Value> out = new ArrayList<>(list.size());
            for (int i : order) {
                out.add(list.get(i));
            }
            return new Value.Many(List.copyOf(out));
        });
        walk("first", 1, (e, list, each) -> {
            for (Value v : list) {
                if (each == null || e.with("$it", v, each).truth()) {
                    return v;
                }
            }
            return Value.NONE;
        });
        walk("any", 2, (e, list, each) -> {
            for (Value v : list) {
                if (e.with("$it", v, each).truth()) {
                    return Value.TRUE;
                }
            }
            return Value.FALSE;
        });
        walk("all", 2, (e, list, each) -> {
            for (Value v : list) {
                if (!e.with("$it", v, each).truth()) {
                    return Value.FALSE;
                }
            }
            return Value.TRUE;
        });
        lazy(g, "reduce", 3, 3, (e, args) -> {
            Value acc = e.eval(args.get(1));
            for (Value v : e.list(e.eval(args.getFirst()))) {
                Map<String, Value> saved = e.locals;
                Map<String, Value> inner = saved == null ? new java.util.HashMap<>() : new java.util.HashMap<>(saved);
                inner.put("$acc", acc);
                e.locals = inner;
                try {
                    acc = e.with("$it", v, args.get(2));
                } finally {
                    e.locals = saved;
                }
            }
            return acc;
        });
        def(g, "last", 1, 1, (e, a) -> {
            List<Value> list = e.list(a.getFirst());
            return list.isEmpty() ? Value.NONE : list.getLast();
        });
        def(g, "get", 2, 2, (e, a) -> {
            List<Value> list = e.list(a.getFirst());
            int i = (int) n(a, 1);
            return i < 1 || i > list.size() ? Value.NONE : list.get(i - 1);
        });
        def(g, "append", 2, 2, (e, a) -> {
            List<Value> out = new ArrayList<>(e.list(a.getFirst()));
            out.add(a.get(1));
            return new Value.Many(List.copyOf(out));
        });
        def(g, "reverse", 1, 1, (e, a) -> {
            List<Value> out = new ArrayList<>(e.list(a.getFirst()));
            Collections.reverse(out);
            return new Value.Many(List.copyOf(out));
        });
        def(g, "shuffle", 1, 1, (e, a) -> {
            List<Value> out = new ArrayList<>(e.list(a.getFirst()));
            Collections.shuffle(out);
            return new Value.Many(List.copyOf(out));
        });
        def(g, "unique", 1, 1, (e, a) -> {
            List<Value> out = new ArrayList<>();
            for (Value v : e.list(a.getFirst())) {
                e.spend(out.size());
                if (out.stream().noneMatch(o -> e.same(o, v))) {
                    out.add(v);
                }
            }
            return new Value.Many(List.copyOf(out));
        });
        def(g, "slice", 2, 3, (e, a) -> {
            int from = Math.max(1, (int) n(a, 1));
            if (a.getFirst() instanceof Value.Text x) {
                int to = a.size() > 2 ? Math.min(x.v().length(), (int) n(a, 2)) : x.v().length();
                return Value.of(from > to ? "" : x.v().substring(from - 1, to));
            }
            List<Value> list = e.list(a.getFirst());
            int to = a.size() > 2 ? Math.min(list.size(), (int) n(a, 2)) : list.size();
            return new Value.Many(from > to ? List.of() : List.copyOf(list.subList(from - 1, to)));
        });
        def(g, "contains", 2, 2, (e, a) -> {
            if (a.getFirst() instanceof Value.Text x) {
                return Value.of(x.v().contains(t(a, 1)));
            }
            if (a.getFirst() instanceof Value.Region r && a.get(1) instanceof Value.Place p) {
                return Value.of(r.area().contains(p.pos()));
            }
            for (Value v : e.list(a.getFirst())) {
                e.spend(1);
                if (e.same(v, a.get(1))) {
                    return Value.TRUE;
                }
            }
            return Value.FALSE;
        });
        def(g, "index_of", 2, 2, (e, a) -> {
            if (a.getFirst() instanceof Value.Text x) {
                return Value.of(x.v().indexOf(t(a, 1)) + 1);
            }
            List<Value> list = e.list(a.getFirst());
            for (int i = 0; i < list.size(); i++) {
                e.spend(1);
                if (e.same(list.get(i), a.get(1))) {
                    return Value.of(i + 1);
                }
            }
            return Value.of(0);
        });
        def(g, "range", 1, 3, (e, a) -> {
            double from = a.size() > 1 ? n(a, 0) : 1, to = a.size() > 1 ? n(a, 1) : n(a, 0), by = a.size() > 2 ? n(a, 2) : 1;
            List<Value> out = new ArrayList<>();
            for (double v = from; by > 0 ? v <= to : by < 0 && v >= to; v += by) {
                e.spend(1);
                out.add(Value.of(v));
            }
            return new Value.Many(List.copyOf(out));
        });
    }

    /** Order among values: numbers by size, anything else by how it reads. */
    private static int compare(Value a, Value b) {
        if (a instanceof Value.Text || b instanceof Value.Text || a instanceof Value.Place || b instanceof Value.Place) {
            return a.show().compareTo(b.show());
        }
        return Double.compare(Eval.num(a), Eval.num(b));
    }
}
