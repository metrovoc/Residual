package io.github.metrovoc.residual.agent;

import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Something a program works out, as it is written: {@code $n + 1}, {@code count(front, iron ingot) > 8}, {@code ripe}.
 * Every expression writes itself back the one way a program is written ({@link #text()}).
 */
public sealed interface Expr {
    /** A number, text in quotes, true, false or nothing. */
    record Lit(Value value) implements Expr {
    }

    /** A variable, with what starts its name saying whose it is: {@code $x} the program's, {@code @x} the body's, {@code #x} the team's. */
    record Var(String name) implements Expr {
    }

    /** A place by a word: {@code front}, {@code up}, {@code here}, {@code home}, {@code network}, {@code hands}, {@code owner}. */
    record Spot(String word) implements Expr {
    }

    /** A thing, or any text, by the plain words that name it: {@code iron ingot}. */
    record Words(String words) implements Expr {
    }

    /** One of the first checks, read as it always was: {@code have 3 bread}, {@code ripe}, {@code powered 2}. */
    record Sense(Program.Sense sense, @Nullable Expr count, String thing, @Nullable Expr mark) implements Expr {
    }

    record Call(String fn, List<Expr> args) implements Expr {
    }

    /** {@code not} or {@code -}. */
    record Unary(String op, Expr e) implements Expr {
    }

    /** {@code or and = != < <= > >= + - * / %}. */
    record Binary(String op, Expr a, Expr b) implements Expr {
    }

    /** {@code [a, b, c]}. */
    record Items(List<Expr> items) implements Expr {
    }

    /**
     * What a step works on: any of these things, none of those, and only what passes a check on {@code $it}, in that
     * order: {@code tag(logs) or coal except oak log where count($it) > 1}.
     */
    record Pick(List<Expr> any, List<Expr> but, @Nullable Expr where) implements Expr {
        public static final Pick ALL = new Pick(List.of(), List.of(), null);

        public static Pick of(String thing) {
            return thing.isEmpty() ? ALL : new Pick(List.of(new Words(thing)), List.of(), null);
        }

        public boolean all() {
            return any.isEmpty() && but.isEmpty() && where == null;
        }
    }

    default String text() {
        StringBuilder sb = new StringBuilder();
        write(sb, this, 0);
        return sb.toString();
    }

    /** The thing a first check names, or nothing. */
    default String thing() {
        return switch (this) {
            case Sense s -> s.thing();
            case Unary u -> u.e().thing();
            default -> "";
        };
    }

    private static int strength(Expr e) {
        return switch (e) {
            case Binary b -> strength(b.op());
            case Unary u -> u.op().equals("not") ? 3 : 7;
            case Pick p -> 0;
            default -> 8;
        };
    }

    static int strength(String op) {
        return switch (op) {
            case "or" -> 1;
            case "and" -> 2;
            case "=", "!=", "<", "<=", ">", ">=" -> 4;
            case "+", "-" -> 5;
            default -> 6;
        };
    }

    private static void write(StringBuilder sb, Expr e, int least) {
        boolean wrap = strength(e) < least;
        if (wrap) {
            sb.append('(');
        }
        switch (e) {
            case Lit l -> sb.append(l.value().source());
            case Var v -> sb.append(v.name());
            case Spot s -> sb.append(s.word());
            case Words w -> sb.append(w.words());
            case Sense s -> {
                sb.append(s.sense().word);
                if (s.count() != null) {
                    sb.append(' ');
                    write(sb, s.count(), 8);
                }
                if (!s.thing().isEmpty()) {
                    sb.append(' ').append(s.thing());
                }
                if (s.mark() != null) {
                    sb.append(' ');
                    write(sb, s.mark(), 8);
                }
            }
            case Call c -> {
                sb.append(c.fn()).append('(');
                for (int i = 0; i < c.args().size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    write(sb, c.args().get(i), 0);
                }
                sb.append(')');
            }
            case Unary u -> {
                sb.append(u.op().equals("not") ? "not " : u.op());
                write(sb, u.e(), strength(u));
            }
            case Binary b -> {
                int s = strength(b.op());
                write(sb, b.a(), s);
                sb.append(' ').append(b.op()).append(' ');
                write(sb, b.b(), s + 1);
            }
            case Items l -> {
                sb.append('[');
                for (int i = 0; i < l.items().size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    write(sb, l.items().get(i), 0);
                }
                sb.append(']');
            }
            case Pick p -> {
                writeAny(sb, p.any());
                if (!p.but().isEmpty()) {
                    sb.append(p.any().isEmpty() ? "except " : " except ");
                    writeAny(sb, p.but());
                }
                if (p.where() != null) {
                    sb.append(p.all() || p.any().isEmpty() && p.but().isEmpty() ? "where " : " where ");
                    write(sb, p.where(), 0);
                }
            }
        }
        if (wrap) {
            sb.append(')');
        }
    }

    private static void writeAny(StringBuilder sb, List<Expr> things) {
        for (int i = 0; i < things.size(); i++) {
            if (i > 0) {
                sb.append(" or ");
            }
            write(sb, things.get(i), 8);
        }
    }
}
