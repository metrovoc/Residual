package io.github.metrovoc.residual.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * What a body does, written the way a model writes it and a player reads it before saying yes:
 * {@code take bread, come, give bread}, or {@code repeat (harvest, plant wheat seeds, put wheat)}. Steps run in order; a
 * block runs its steps again, or only while a check holds. A program can hold values in variables and work them out
 * ({@code set $n count(front) + 1}), name its own steps ({@code to row $n (…)}), and answer events
 * ({@code on redstone at 1 priority 2 (…)}). The text is canonical: {@link #toString()} writes every program one way.
 */
public record Program(List<Step> steps) {
    /** The longest program a body keeps, in characters. */
    public static final int LONGEST = 4000;
    /** How deep blocks may sit inside one another. */
    public static final int DEEPEST = 8;
    /** Marks are numbered from 1, the way a Survey Rod hands them over. */
    public static final int MARKS = 8;
    /** The longest wait, in seconds. */
    public static final int LONGEST_WAIT = 600;

    /** The words that name a place without saying where: its own sides, and what is no place in the world. */
    public static final Set<String> SPOTS = Set.of("front", "back", "left", "right", "up", "down", "here", "home", "network",
            "hands", "owner");
    /** Words a thing's name stops at, since a step goes on after them. */
    private static final Set<String> STOPS = Set.of("and", "or", "except", "where", "from", "to", "at", "speed", "pitch", "yaw",
            "for", "priority", "sneak");
    /** Words no procedure may be called. */
    private static final Set<String> RESERVED = Set.of("repeat", "if", "else", "until", "while", "for", "wait", "set", "to", "on", "when",
            "return", "leave", "next", "stop", "breakpoint", "not", "and", "or", "true", "false", "nothing");

    /**
     * Everything a body can do in one step. {@code shape} says what follows the verb, in order: {@code n} a number,
     * {@code what} what it works on ({@code what?} if it may be left out), {@code place} where it goes, {@code mark} a mark's
     * number at the end, {@code secs} seconds, {@code words} words to the end of the step; the rest are the clauses a step
     * may have: {@code from to at speed pitch yaw for sneak}.
     */
    public enum Verb {
        COME(""), HOME(""), GO("place"), STAY(""), FOLLOW(""),
        TAKE("n what at"), PUT("what? at"), GIVE("n what"), FETCH("n what"), PICKUP("n what? at"),
        HARVEST("at"), PLANT("what at"), TILL("at"), MINE("n what"), PLACE("what mark at"),
        WAIT("secs"), SAY("words"),
        FARM(""), STORE(""), SORT(""), HAUL(""), BUILD(""), COPY(""), MOVE(""),
        CRAFT("n what"), SUPPLY(""),
        SEND("n what? from to"), PUMP("n what? from to"), CHARGE("n from to"),
        DROP("n what? at"), FLING("n what? at speed pitch yaw"), VOID("n what?"), DRAIN("n what?"),
        DIG("n what? at"), USE("what? at sneak"), ATTACK("at"), EMIT("n"), PULSE("n for"),
        EXTRUDE("n what at"), RETRACT("n at"),
        FORWARD("n"), BACK("n"), UP("n"), DOWN("n"), TURN("words"), TELEPORT("place"), SELECT("n"), TRANSFER("n to"), EQUIP("what?"),
        REFUEL("n what?"), LIFT("at"), LOWER(""), RENAME("words"), SIGN("words at"), RETIRE(""), RUN("at"),
        PROVIDE("what? at"), PUSH("what? at"), ACCEPT("what? at"), REQUEST("n what at"), DELIVER(""),
        SOUND("words"), PARTICLE("words at"), NOTE("words");

        public final String word = name().toLowerCase(Locale.ROOT);
        public final List<String> shape;

        Verb(String shape) {
            this.shape = shape.isEmpty() ? List.of() : List.of(shape.split(" "));
        }

        public boolean takes(String part) {
            return shape.contains(part);
        }

        @Nullable
        public static Verb of(String word) {
            for (Verb v : values()) {
                if (v.word.equals(word)) {
                    return v;
                }
            }
            return null;
        }
    }

    /** What follows one of the first checks. */
    public enum Arg {
        NONE, THING, MARK
    }

    /** The first things a body could find out, read without parentheses. */
    public enum Sense {
        HAVE(Arg.THING), STOCKED(Arg.THING), RIPE(Arg.NONE), FULL(Arg.NONE), NIGHT(Arg.NONE), NEAR(Arg.NONE),
        POWERED(Arg.MARK);

        public final String word = name().toLowerCase(Locale.ROOT);
        public final Arg arg;

        Sense(Arg arg) {
            this.arg = arg;
        }
    }

    public sealed interface Step permits Act, Repeat, If, Until, While, WaitUntil, ForEach, Assign, To, Invoke, Return, Leave, Next, Stop, Pause, On {
    }

    /**
     * One thing to do. {@code n} is null when no number was given, {@code what} {@link Expr.Pick#ALL} when no thing,
     * {@code mark} 0 when no mark, {@code words} empty when no words.
     */
    public record Act(Verb verb, @Nullable Expr n, Expr.Pick what, int mark, String words, List<Clause> clauses) implements Step {
        public Act(Verb verb) {
            this(verb, 0, "", 0);
        }

        /** An act as the first programs had them: a number, a thing, a mark. A wait keeps its seconds in {@code count}, a say its words in {@code thing}. */
        public Act(Verb verb, int count, String thing, int mark) {
            this(verb, count > 0 ? new Expr.Lit(Value.of(count)) : null, verb.takes("words") ? Expr.Pick.ALL : Expr.Pick.of(thing), mark,
                    verb.takes("words") ? thing : "", List.of());
        }

        /** The number given, if it is written out. */
        public int count() {
            return n instanceof Expr.Lit l && l.value() instanceof Value.Num v ? (int) v.v() : 0;
        }

        /** The thing named in plain words, or the words of a {@code say}. */
        public String thing() {
            return !words.isEmpty() ? words : what.any().size() == 1 && what.any().getFirst() instanceof Expr.Words w ? w.words() : "";
        }

        @Nullable
        public Expr clause(String word) {
            for (Clause c : clauses) {
                if (c.word().equals(word)) {
                    return c.e();
                }
            }
            return null;
        }

        public boolean has(String word) {
            return clauses.stream().anyMatch(c -> c.word().equals(word));
        }
    }

    /** {@code from front}, {@code to each($furnaces)}, {@code sneak}. */
    public record Clause(String word, @Nullable Expr e) {
    }

    /** The same steps again, {@code times} times, or for good when it is null. */
    public record Repeat(@Nullable Expr times, List<Step> body) implements Step {
    }

    public record If(Expr check, List<Step> then, List<Step> otherwise) implements Step {
    }

    /** The same steps again until the check holds; checked before each round. */
    public record Until(Expr check, List<Step> body) implements Step {
    }

    /** The same steps again while the check holds; checked before each round. */
    public record While(Expr check, List<Step> body) implements Step {
    }

    public record WaitUntil(Expr check) implements Step {
    }

    /** {@code for each $x in $list (…)}: the same steps once for each thing in a list, or each place in an area. */
    public record ForEach(String name, Expr list, List<Step> body) implements Step {
    }

    /** {@code set $x …}. */
    public record Assign(String name, Expr value) implements Step {
    }

    /** {@code to name $a $b (…)}: steps with a name of their own, run only when called. */
    public record To(String name, List<String> params, List<Step> body) implements Step {
    }

    /** Runs the steps called {@code name}: {@code name 3 $x}. */
    public record Invoke(String name, List<Expr> args) implements Step {
    }

    /** Ends the steps called, leaving {@code value} in {@code $result}. */
    public record Return(@Nullable Expr value) implements Step {
    }

    /** Leaves the loop it is in. */
    public record Leave() implements Step {
    }

    /** Goes on to the next round of the loop it is in. */
    public record Next() implements Step {
    }

    /** Ends the program. */
    public record Stop() implements Step {
    }

    /** A breakpoint written into the program: it pauses here for the debugger. */
    public record Pause() implements Step {
    }

    /** What an event listens for. */
    public enum Event {
        REDSTONE, ITEM, BLOCK, TIMER, CHANGE, WHEN;

        public final String word = name().toLowerCase(Locale.ROOT);
    }

    /**
     * Steps run when something happens: {@code on redstone at 1 (…)}, {@code on item iron ingot at front (…)},
     * {@code on block at $p (…)}, {@code on timer 5 (…)}, {@code on change #x (…)}, {@code when $n > 3 (…)}. A handler with
     * a higher {@code priority} than what is running interrupts it, which goes on where it was afterwards.
     */
    public record On(Event event, Expr.Pick what, @Nullable Expr where, @Nullable Expr arg, int priority, List<Step> body) implements Step {
    }

    /** One step on one line of a program's listing: what the debugger shows and sets breakpoints on. */
    public record Line(Step step, int depth, String text) {
    }

    /** Why a text is not a program: a translation key, the word it stumbled on, and where. */
    public record Problem(String key, String word, int at) {
    }

    /** A program or what is wrong with the text. */
    public record Parsed(Program program, Problem problem) {
        public boolean ok() {
            return program != null;
        }
    }

    /** How a piece joins the end of a program: as the next step, after a space, or straight on. */
    public enum Join { STEP, WORD, ON }

    public static Program of(Step... steps) {
        return new Program(List.of(steps));
    }

    /** Reads a program, over as many lines as it likes. Blank text is an empty program; anything after a slip is not read. */
    public static Parsed parse(String text) {
        String s = plain(text);
        if (s.length() > LONGEST) {
            return new Parsed(null, new Problem("residual.program.error.long", "", LONGEST));
        }
        Reader r = new Reader(s);
        try {
            List<Step> steps = r.sequence(0);
            if (r.at < s.length()) {
                throw r.fail("residual.program.error.stray", r.word());
            }
            return new Parsed(new Program(List.copyOf(steps)), null);
        } catch (Slip slip) {
            return new Parsed(null, slip.problem);
        }
    }

    /** Reads an expression alone, as a watched value or a saved one is written; null if it is not one. */
    @Nullable
    public static Expr expression(String text) {
        Reader r = new Reader(plain(text));
        try {
            Expr e = r.expr(true);
            r.spaces();
            return r.done() ? e : null;
        } catch (Slip slip) {
            return null;
        }
    }

    /** One line, in lower case and with one space where there are many, except inside quotes. */
    private static String plain(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            }
            if (quoted) {
                sb.append(c);
            } else if (!Character.isWhitespace(c)) {
                sb.append(Character.toLowerCase(c));
            } else if (sb.isEmpty() || sb.charAt(sb.length() - 1) != ' ') {
                sb.append(' ');
            }
        }
        return sb.toString().strip();
    }

    /**
     * {@code text} with {@code piece} joined on the end, then with whatever plainly comes next: the parenthesis a block
     * opens with, the mark a {@code place} needs.
     */
    public static String append(String text, String piece, Join join) {
        String end = text.stripTrailing();
        String joined = switch (join) {
            case STEP -> end.isEmpty() || end.endsWith("(") ? end + piece : end + (end.endsWith(",") ? " " : ", ") + piece;
            case WORD -> end.isEmpty() ? piece : end.endsWith("(") || end.endsWith("[") ? end + piece : end + " " + piece;
            case ON -> end + piece;
        };
        Parsed p = parse(joined);
        if (p.ok() || p.problem.at + p.problem.word.length() < plain(joined).length()) {
            return joined;
        }
        String word = p.problem.word;
        if (p.problem.key.equals("residual.program.error.open") && word.isEmpty() && endsInCheck(plain(joined))) {
            return joined.stripTrailing() + " (";
        }
        if (p.problem.key.equals("residual.program.error.mark") && !word.isEmpty() && !Character.isDigit(word.charAt(0))) {
            return joined.stripTrailing() + " 1";
        }
        return joined;
    }

    /** Whether a block's check is one of the first checks, which nothing can go on from: {@code if not ripe}. */
    private static boolean endsInCheck(String text) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:^|[ ,(])(?:if|until|while) (?!.*(?:^|[ ,(])(?:if|until|while) )(.*)$").matcher(text);
        if (!m.find()) {
            return false;
        }
        Expr check = expression(m.group(1));
        while (check instanceof Expr.Unary u && u.op().equals("not")) {
            check = u.e();
        }
        return check instanceof Expr.Sense;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        write(sb, steps);
        return sb.toString();
    }

    /** Whether it goes on for good: a repeat without a count, a stay or a handler waiting for its event, among its own steps. */
    public boolean endless() {
        for (Step s : steps) {
            if (s instanceof Repeat r && r.times() == null || s instanceof Act a && a.verb() == Verb.STAY || s instanceof On) {
                return true;
            }
        }
        return false;
    }

    /** One step as written, a block only by what starts it: {@code take 5 bread}, {@code repeat 3}, {@code if ripe}. */
    public static String text(Step step) {
        StringBuilder sb = new StringBuilder();
        switch (step) {
            case Repeat r -> {
                sb.append("repeat");
                if (r.times() != null) {
                    sb.append(' ').append(number(r.times()));
                }
            }
            case If i -> sb.append("if ").append(i.check().text());
            case Until u -> sb.append("until ").append(u.check().text());
            case While w -> sb.append("while ").append(w.check().text());
            case ForEach f -> sb.append("for each ").append(f.name()).append(" in ").append(f.list().text());
            case To t -> {
                sb.append("to ").append(t.name());
                t.params().forEach(p -> sb.append(' ').append(p));
            }
            case On o -> head(sb, o);
            default -> write(sb, step);
        }
        return sb.toString();
    }

    public static String text(Expr check) {
        return check.text();
    }

    /** How many steps there are, counting those inside blocks and the blocks themselves. */
    public int size() {
        return size(steps);
    }

    private static int size(List<Step> steps) {
        int n = 0;
        for (Step s : steps) {
            n += 1 + body(s).stream().mapToInt(Program::size).sum();
        }
        return n;
    }

    /** The lists of steps inside {@code s}. */
    private static List<List<Step>> body(Step s) {
        return switch (s) {
            case Repeat r -> List.of(r.body());
            case If i -> List.of(i.then(), i.otherwise());
            case Until u -> List.of(u.body());
            case While w -> List.of(w.body());
            case ForEach f -> List.of(f.body());
            case To t -> List.of(t.body());
            case On o -> List.of(o.body());
            default -> List.of();
        };
    }

    /** Every step in the order it is written, one to a line; a step's place in this list is its number. */
    public List<Line> lines() {
        List<Line> out = new ArrayList<>();
        lines(out, steps, 0);
        return out;
    }

    private static void lines(List<Line> out, List<Step> steps, int depth) {
        for (Step s : steps) {
            out.add(new Line(s, depth, text(s)));
            for (List<Step> inner : body(s)) {
                lines(out, inner, depth + 1);
            }
        }
    }

    /** The program written out over lines, each step on its own and what a block holds indented: easier to read than the one line it is kept in. */
    public String pretty() {
        StringBuilder sb = new StringBuilder();
        pretty(sb, steps, 0);
        return sb.toString();
    }

    private static void pretty(StringBuilder sb, List<Step> steps, int depth) {
        String indent = "  ".repeat(depth);
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            sb.append(indent);
            List<List<Step>> inner = body(s);
            if (inner.isEmpty()) {
                write(sb, s);
            } else {
                sb.append(text(s)).append(" (\n");
                pretty(sb, inner.getFirst(), depth + 1);
                sb.append('\n').append(indent).append(')');
                if (s instanceof If o && !o.otherwise().isEmpty()) {
                    sb.append(" else (\n");
                    pretty(sb, o.otherwise(), depth + 1);
                    sb.append('\n').append(indent).append(')');
                }
            }
            if (i + 1 < steps.size()) {
                sb.append(",\n");
            }
        }
    }

    private static void write(StringBuilder sb, List<Step> steps) {
        for (int i = 0; i < steps.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            write(sb, steps.get(i));
        }
    }

    private static void write(StringBuilder sb, Step step) {
        switch (step) {
            case Act a -> {
                sb.append(a.verb().word);
                if (a.n() != null) {
                    sb.append(' ').append(number(a.n()));
                }
                if (!a.what().all()) {
                    sb.append(' ').append(a.what().text());
                }
                if (a.mark() > 0) {
                    sb.append(' ').append(a.mark());
                }
                if (!a.words().isEmpty()) {
                    sb.append(' ').append(a.words());
                }
                for (Clause c : a.clauses()) {
                    if (!c.word().equals("place")) {
                        sb.append(' ').append(c.word());
                    }
                    if (c.e() != null) {
                        sb.append(' ').append(c.e() instanceof Expr.Call each && each.fn().equals("each") ? "each " + each.args().getFirst().text() : c.e().text());
                    }
                }
            }
            case Repeat r -> {
                sb.append(text(r));
                block(sb, r.body());
            }
            case If i -> {
                sb.append(text(i));
                block(sb, i.then());
                if (!i.otherwise().isEmpty()) {
                    sb.append(" else");
                    block(sb, i.otherwise());
                }
            }
            case Until u -> {
                sb.append(text(u));
                block(sb, u.body());
            }
            case While w -> {
                sb.append(text(w));
                block(sb, w.body());
            }
            case ForEach f -> {
                sb.append(text(f));
                block(sb, f.body());
            }
            case To t -> {
                sb.append(text(t));
                block(sb, t.body());
            }
            case On o -> {
                sb.append(text(o));
                block(sb, o.body());
            }
            case WaitUntil w -> sb.append("wait until ").append(w.check().text());
            case Assign a -> sb.append("set ").append(a.name()).append(' ').append(a.value().text());
            case Invoke c -> {
                sb.append(c.name());
                c.args().forEach(e -> sb.append(' ').append(number(e)));
            }
            case Return r -> sb.append(r.value() == null ? "return" : "return " + r.value().text());
            case Leave l -> sb.append("leave");
            case Next n -> sb.append("next");
            case Stop s -> sb.append("stop");
            case Pause p -> sb.append("breakpoint");
        }
    }

    /** An expression where one word or number is expected: anything longer goes in parentheses. */
    private static String number(Expr e) {
        return e instanceof Expr.Binary || e instanceof Expr.Unary ? "(" + e.text() + ")" : e.text();
    }

    private static void head(StringBuilder sb, On o) {
        sb.append(o.event() == Event.WHEN ? "when" : "on " + o.event().word);
        if (o.arg() != null) {
            sb.append(' ').append(o.event() == Event.TIMER ? number(o.arg()) : o.arg().text());
        }
        if (!o.what().all()) {
            sb.append(' ').append(o.what().text());
        }
        if (o.where() != null) {
            sb.append(" at ").append(o.where().text());
        }
        if (o.priority() != 1) {
            sb.append(" priority ").append(o.priority());
        }
    }

    private static void block(StringBuilder sb, List<Step> body) {
        sb.append(" (");
        write(sb, body);
        sb.append(')');
    }

    private static final class Slip extends RuntimeException {
        final Problem problem;

        Slip(Problem problem) {
            super(problem.key(), null, false, false);
            this.problem = problem;
        }
    }

    /** Recursive descent over the text, one step at a time, and precedence climbing inside expressions. */
    private static final class Reader {
        final String s;
        final Set<String> defined = new java.util.HashSet<>();
        int at;

        Reader(String s) {
            this.s = s;
            // a procedure may be called before it is written
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:^|[ ,(])to ([a-z_][a-z0-9_]*)").matcher(s);
            while (m.find()) {
                defined.add(m.group(1));
            }
        }

        Slip fail(String key, String word) {
            return new Slip(new Problem(key, word, at));
        }

        boolean done() {
            return at >= s.length();
        }

        char here() {
            return done() ? 0 : s.charAt(at);
        }

        /** The word starting here, for an error message. */
        String word() {
            int end = at;
            while (end < s.length() && " ,()".indexOf(s.charAt(end)) < 0) {
                end++;
            }
            return s.substring(at, end);
        }

        /** The word starting here when it is a word of letters, digits and underscores, or nothing. */
        String name() {
            int end = at;
            while (end < s.length() && isNameChar(s.charAt(end))) {
                end++;
            }
            return s.substring(at, end);
        }

        static boolean isNameChar(char c) {
            return Character.isLetterOrDigit(c) || c == '_';
        }

        boolean eat(String token) {
            if (s.startsWith(token, at)) {
                at += token.length();
                return true;
            }
            return false;
        }

        /** Whether the whole word {@code word} starts here. */
        boolean at(String word) {
            return s.startsWith(word, at) && (at + word.length() >= s.length() || !isNameChar(s.charAt(at + word.length())));
        }

        boolean eatWord(String word) {
            if (at(word)) {
                at += word.length();
                return true;
            }
            return false;
        }

        void spaces() {
            while (!done() && s.charAt(at) == ' ') {
                at++;
            }
        }

        /** Steps separated by commas, up to the end or a closing parenthesis. */
        List<Step> sequence(int depth) {
            List<Step> steps = new ArrayList<>();
            spaces();
            if (done() || s.charAt(at) == ')') {
                return steps;
            }
            while (true) {
                steps.add(step(depth));
                spaces();
                if (!eat(",")) {
                    return steps;
                }
                spaces();
            }
        }

        Step step(int depth) {
            String w = name();
            switch (w) {
                case "repeat" -> {
                    at += w.length();
                    spaces();
                    Expr times = here() == '(' ? null : count();
                    return new Repeat(times, block(depth));
                }
                case "if" -> {
                    at += w.length();
                    Expr c = expr(false);
                    List<Step> then = block(depth);
                    int back = at;
                    spaces();
                    if (eatWord("else")) {
                        return new If(c, then, block(depth));
                    }
                    at = back;
                    return new If(c, then, List.of());
                }
                case "until" -> {
                    at += w.length();
                    Expr c = expr(false);
                    return new Until(c, block(depth));
                }
                case "while" -> {
                    at += w.length();
                    Expr c = expr(false);
                    return new While(c, block(depth));
                }
                case "wait" -> {
                    int back = at;
                    at += w.length();
                    spaces();
                    if (eatWord("until")) {
                        return new WaitUntil(expr(false));
                    }
                    at = back;
                }
                case "for" -> {
                    at += w.length();
                    spaces();
                    if (!eatWord("each")) {
                        throw fail("residual.program.error.each", word());
                    }
                    spaces();
                    String name = variable();
                    spaces();
                    if (!eatWord("in")) {
                        throw fail("residual.program.error.in", word());
                    }
                    Expr list = expr(false);
                    return new ForEach(name, list, block(depth));
                }
                case "set" -> {
                    at += w.length();
                    spaces();
                    String name = variable();
                    return new Assign(name, expr(true));
                }
                case "to" -> {
                    return procedure(depth, w);
                }
                case "on", "when" -> {
                    return handler(depth, w);
                }
                case "return" -> {
                    at += w.length();
                    spaces();
                    return new Return(done() || ",)".indexOf(here()) >= 0 ? null : expr(true));
                }
                case "leave" -> {
                    at += w.length();
                    return new Leave();
                }
                case "next" -> {
                    at += w.length();
                    return new Next();
                }
                case "stop" -> {
                    at += w.length();
                    return new Stop();
                }
                case "breakpoint" -> {
                    at += w.length();
                    return new Pause();
                }
                default -> {
                }
            }
            Verb v = Verb.of(w);
            if (v != null) {
                at += w.length();
                return act(v);
            }
            if (defined.contains(w)) {
                at += w.length();
                List<Expr> args = new ArrayList<>();
                spaces();
                while (!done() && ",)".indexOf(here()) < 0) {
                    args.add(argument());
                    spaces();
                }
                return new Invoke(w, List.copyOf(args));
            }
            String shown = word();
            throw fail(shown.isEmpty() ? "residual.program.error.missing" : "residual.program.error.verb", shown);
        }

        Step procedure(int depth, String w) {
            if (depth > 0) {
                throw fail("residual.program.error.top", w);
            }
            at += w.length();
            spaces();
            String name = name();
            if (name.isEmpty() || RESERVED.contains(name) || Verb.of(name) != null || Character.isDigit(name.charAt(0))) {
                throw fail("residual.program.error.procedure", name);
            }
            at += name.length();
            List<String> params = new ArrayList<>();
            spaces();
            while (here() == '$') {
                params.add(variable());
                spaces();
            }
            return new To(name, List.copyOf(params), block(depth));
        }

        Step handler(int depth, String w) {
            if (depth > 0) {
                throw fail("residual.program.error.top", w);
            }
            at += w.length();
            spaces();
            Event event = Event.WHEN;
            if (w.equals("on")) {
                String name = name();
                event = null;
                for (Event e : Event.values()) {
                    if (e != Event.WHEN && e.word.equals(name)) {
                        event = e;
                    }
                }
                if (event == null) {
                    throw fail("residual.program.error.event", name);
                }
                at += name.length();
                spaces();
            }
            Expr.Pick what = Expr.Pick.ALL;
            Expr where = null;
            Expr arg = null;
            switch (event) {
                case TIMER -> arg = count();
                case CHANGE -> arg = new Expr.Var(variable());
                case WHEN -> arg = expr(false);
                case ITEM -> what = at("at") || at("priority") || here() == '(' ? Expr.Pick.ALL : pick(false);
                default -> {
                }
            }
            spaces();
            if (event != Event.TIMER && event != Event.CHANGE && event != Event.WHEN && eatWord("at")) {
                where = expr(false);
                spaces();
            }
            int priority = 1;
            if (eatWord("priority")) {
                spaces();
                priority = number(0, 99);
            }
            return new On(event, what, where, arg, priority, block(depth));
        }

        /** A variable's name, with what starts it. */
        String variable() {
            char c = here();
            if (c != '$' && c != '@' && c != '#') {
                throw fail("residual.program.error.variable", word());
            }
            at++;
            String name = name();
            if (name.isEmpty()) {
                throw fail("residual.program.error.variable", word());
            }
            at += name.length();
            return c + name;
        }

        Act act(Verb v) {
            Expr n = null;
            Expr.Pick what = Expr.Pick.ALL;
            int mark = 0;
            String words = "";
            Expr place = null;
            for (String part : v.shape) {
                switch (part) {
                    case "n" -> {
                        spaces();
                        // with nothing else to read it as, a function's value is how many; a step that takes a thing reads it as the thing
                        if (Character.isDigit(here()) || isVariable() || here() == '(' || !v.takes("what") && !v.takes("what?") && callHere()) {
                            n = count();
                        }
                    }
                    case "what", "what?" -> {
                        spaces();
                        // a variable alone is what the step works on, not how many
                        if (part.equals("what") && n instanceof Expr.Var && (done() || ",)".indexOf(here()) >= 0 || clauseHere(v))) {
                            what = new Expr.Pick(List.of(n), List.of(), null);
                            n = null;
                        } else if (part.equals("what")) {
                            if (done()) {
                                throw fail("residual.program.error.thing", "");
                            }
                            what = pick(true);
                        } else if (!done() && ",)".indexOf(here()) < 0 && !clauseHere(v)) {
                            what = pick(false);
                        }
                    }
                    case "mark" -> {
                        // the old form: the last word of the thing is a mark, unless the step says where with at
                        spaces();
                        if (!at("at")) {
                            mark = lastWordMark(what);
                            what = withoutLast(what);
                        }
                    }
                    case "place" -> {
                        spaces();
                        place = place();
                    }
                    case "secs" -> {
                        spaces();
                        n = seconds();
                    }
                    case "words" -> {
                        spaces();
                        words = words(v);
                        if (words.isEmpty()) {
                            throw fail("residual.program.error.words", "");
                        }
                    }
                    default -> {
                    }
                }
            }
            List<Clause> clauses = new ArrayList<>();
            while (true) {
                int back = at;
                spaces();
                String w = name();
                if (w.isEmpty() || !v.takes(w)) {
                    at = back;
                    break;
                }
                at += w.length();
                clauses.add(clause(w));
            }
            if (place != null) {
                clauses.add(new Clause("place", place));
            }
            int back = at;
            spaces();
            if (!done() && ",)(".indexOf(here()) < 0) {
                throw fail("residual.program.error.stray", word());
            }
            at = back;
            clauses.sort(java.util.Comparator.comparingInt(c -> v.shape.indexOf(c.word())));
            return new Act(v, n, what, mark, words, List.copyOf(clauses));
        }

        boolean callHere() {
            String w = name();
            return !w.isEmpty() && s.startsWith("(", at + w.length()) && Ops.fn(w) != null;
        }

        boolean clauseHere(Verb v) {
            String w = name();
            return !w.isEmpty() && v.takes(w) && !w.equals("n") && !w.equals("what");
        }

        Clause clause(String w) {
            if (w.equals("sneak")) {
                return new Clause(w, null);
            }
            spaces();
            if (w.equals("to") && eatWord("each")) {
                spaces();
                return new Clause("to", new Expr.Call("each", List.of(expr(false))));
            }
            return new Clause(w, expr(false));
        }

        /** What a {@code go} or {@code teleport} goes to: a mark's number, a variable or a position worked out. */
        Expr place() {
            if (Character.isDigit(here())) {
                return new Expr.Lit(Value.of(number(1, MARKS)));
            }
            if (isVariable()) {
                return new Expr.Var(variable());
            }
            String w = name();
            if (!w.isEmpty() && s.startsWith("(", at + w.length()) && Ops.fn(w) != null) {
                return call(w);
            }
            throw fail("residual.program.error.number", word());
        }

        /** Seconds to wait: a plain number from a twentieth of a second up to the longest wait, or one worked out. */
        Expr seconds() {
            if (isVariable() || here() == '(') {
                return count();
            }
            int start = at;
            double v = decimal();
            if (v < 0.05 || v > LONGEST_WAIT) {
                String digits = s.substring(start, at);
                at = start;
                throw fail("residual.program.error.range", digits);
            }
            return new Expr.Lit(Value.of(v));
        }

        boolean isVariable() {
            char c = here();
            return c == '$' || c == '@' || c == '#';
        }

        /** A number, a variable, a function's value, or anything else in parentheses, where a step wants how many. */
        Expr count() {
            spaces();
            if (Character.isDigit(here())) {
                return new Expr.Lit(Value.of(decimal()));
            }
            if (isVariable()) {
                return new Expr.Var(variable());
            }
            if (here() == '(') {
                at++;
                Expr e = expr(true);
                spaces();
                if (!eat(")")) {
                    throw fail("residual.program.error.close", word());
                }
                return e;
            }
            String w = name();
            if (!w.isEmpty() && s.startsWith("(", at + w.length()) && Ops.fn(w) != null) {
                return call(w);
            }
            throw fail("residual.program.error.number", word());
        }

        /** An argument of a procedure call: one number, text, variable, list, function or word, or anything in parentheses. */
        Expr argument() {
            char c = here();
            if (Character.isDigit(c) || isVariable() || c == '(') {
                return count();
            }
            if (c == '"' || c == '[') {
                return primary(true);
            }
            String w = name();
            if (w.isEmpty()) {
                throw fail("residual.program.error.stray", word());
            }
            if (s.startsWith("(", at + w.length()) && Ops.fn(w) != null) {
                return call(w);
            }
            at += w.length();
            return w.equals("true") || w.equals("false") || w.equals("nothing") ? literal(w)
                    : Program.SPOTS.contains(w) ? new Expr.Spot(w) : new Expr.Words(w);
        }

        Expr literal(String w) {
            return new Expr.Lit(w.equals("true") ? Value.TRUE : w.equals("false") ? Value.FALSE : Value.NONE);
        }

        /** The words up to the next comma or parenthesis, and for a step with clauses, up to its first clause. */
        String words(Verb v) {
            int end = at;
            while (end < s.length() && ",()".indexOf(s.charAt(end)) < 0) {
                if (s.charAt(end) == '"') {
                    int close = s.indexOf('"', end + 1);
                    end = close < 0 ? s.length() - 1 : close;
                }
                if (s.charAt(end) == ' ' && end + 1 < s.length()) {
                    int stop = end + 1;
                    while (stop < s.length() && isNameChar(s.charAt(stop))) {
                        stop++;
                    }
                    String next = s.substring(end + 1, stop);
                    if (!next.isEmpty() && !next.equals("words") && v.takes(next)) {
                        break;
                    }
                }
                end++;
            }
            String words = s.substring(at, end).strip().replaceAll(" +", " ");
            at = end;
            return words;
        }

        /** The words of a thing's name, up to anything a name cannot hold or a word a step goes on after. */
        String thingWords() {
            StringBuilder out = new StringBuilder();
            while (true) {
                int back = at;
                spaces();
                String w = name();
                if (w.isEmpty() || STOPS.contains(w)) {
                    // a colon joins a mod to its name
                    if (w.isEmpty() && here() == ':' && !out.isEmpty()) {
                        out.append(':');
                        at++;
                        continue;
                    }
                    at = back;
                    break;
                }
                at += w.length();
                if (!out.isEmpty() && out.charAt(out.length() - 1) != ':') {
                    out.append(' ');
                }
                out.append(w);
            }
            return out.toString();
        }

        /** What a step works on: {@code a or b except c where …}. */
        Expr.Pick pick(boolean required) {
            List<Expr> any = new ArrayList<>();
            List<Expr> but = new ArrayList<>();
            Expr where = null;
            spaces();
            if (!at("except") && !at("where")) {
                any.add(term(required));
                spaces();
                while (eatWord("or")) {
                    any.add(term(true));
                    spaces();
                }
            }
            if (eatWord("except")) {
                but.add(term(true));
                spaces();
                while (eatWord("or")) {
                    but.add(term(true));
                    spaces();
                }
            }
            if (eatWord("where")) {
                where = expr(false);
            }
            return new Expr.Pick(List.copyOf(any), List.copyOf(but), where);
        }

        Expr term(boolean required) {
            spaces();
            char c = here();
            if (isVariable()) {
                return new Expr.Var(variable());
            }
            if (c == '[' || c == '"') {
                return primary(true);
            }
            String w = name();
            if (!w.isEmpty() && s.startsWith("(", at + w.length()) && Ops.fn(w) != null) {
                return call(w);
            }
            int from = at;
            String words = thingWords();
            if (words.isEmpty()) {
                throw fail("residual.program.error.thing", required ? "" : word());
            }
            if (!words.chars().allMatch(ch -> Character.isLetterOrDigit(ch) || ch == ' ' || ch == '_' || ch == ':')) {
                at = from;
                throw fail("residual.program.error.thing", words);
            }
            return new Expr.Words(words);
        }

        int lastWordMark(Expr.Pick what) {
            String thing = what.any().size() == 1 && what.any().getFirst() instanceof Expr.Words w ? w.words() : "";
            int space = thing.lastIndexOf(' ');
            String last = thing.substring(space + 1);
            if (space < 0 || !last.chars().allMatch(Character::isDigit) || last.length() > 1
                    || last.charAt(0) < '1' || last.charAt(0) > '0' + MARKS) {
                at = s.lastIndexOf(last, at);
                throw fail("residual.program.error.mark", last);
            }
            return last.charAt(0) - '0';
        }

        static Expr.Pick withoutLast(Expr.Pick what) {
            String thing = ((Expr.Words) what.any().getFirst()).words();
            return new Expr.Pick(List.of(new Expr.Words(thing.substring(0, thing.lastIndexOf(' ')))), what.but(), what.where());
        }

        List<Step> block(int depth) {
            if (depth + 1 > DEEPEST) {
                throw fail("residual.program.error.deep", "");
            }
            spaces();
            if (!eat("(")) {
                throw fail("residual.program.error.open", word());
            }
            List<Step> body = sequence(depth + 1);
            spaces();
            if (!eat(")")) {
                throw fail("residual.program.error.close", word());
            }
            return List.copyOf(body);
        }

        // ------------------------------------------------------------ expressions

        /** {@code words}: whether plain words may stand for a thing here, as they may inside a call. */
        Expr expr(boolean words) {
            spaces();
            Expr a = and(words);
            while (true) {
                int back = at;
                spaces();
                if (!eatWord("or")) {
                    at = back;
                    return a;
                }
                a = new Expr.Binary("or", a, and(words));
            }
        }

        Expr and(boolean words) {
            Expr a = not(words);
            while (true) {
                int back = at;
                spaces();
                if (!eatWord("and")) {
                    at = back;
                    return a;
                }
                a = new Expr.Binary("and", a, not(words));
            }
        }

        Expr not(boolean words) {
            spaces();
            if (eatWord("not")) {
                return new Expr.Unary("not", not(words));
            }
            return compare(words);
        }

        Expr compare(boolean words) {
            Expr a = sum(words);
            int back = at;
            spaces();
            for (String op : List.of("!=", "<=", ">=", "=", "<", ">")) {
                if (eat(op)) {
                    // what a thing is compared with may be plain words: $x = iron ingot
                    return new Expr.Binary(op, a, sum(true));
                }
            }
            at = back;
            return a;
        }

        Expr sum(boolean words) {
            Expr a = product(words);
            while (true) {
                int back = at;
                spaces();
                if (here() == '+' || here() == '-') {
                    String op = String.valueOf(here());
                    at++;
                    a = new Expr.Binary(op, a, product(words));
                } else {
                    at = back;
                    return a;
                }
            }
        }

        Expr product(boolean words) {
            Expr a = unary(words);
            while (true) {
                int back = at;
                spaces();
                if (here() == '*' || here() == '/' || here() == '%') {
                    String op = String.valueOf(here());
                    at++;
                    a = new Expr.Binary(op, a, unary(words));
                } else {
                    at = back;
                    return a;
                }
            }
        }

        Expr unary(boolean words) {
            spaces();
            if (here() == '-') {
                at++;
                return new Expr.Unary("-", unary(words));
            }
            return primary(words);
        }

        Expr primary(boolean words) {
            spaces();
            char c = here();
            if (Character.isDigit(c)) {
                return new Expr.Lit(Value.of(decimal()));
            }
            if (isVariable()) {
                return new Expr.Var(variable());
            }
            if (c == '"') {
                int end = s.indexOf('"', at + 1);
                if (end < 0) {
                    at = s.length();
                    throw fail("residual.program.error.quote", "");
                }
                String text = s.substring(at + 1, end);
                at = end + 1;
                return new Expr.Lit(Value.of(text));
            }
            if (c == '[') {
                at++;
                List<Expr> items = new ArrayList<>();
                spaces();
                if (here() != ']') {
                    while (true) {
                        items.add(expr(true));
                        spaces();
                        if (!eat(",")) {
                            break;
                        }
                    }
                }
                if (!eat("]")) {
                    throw fail("residual.program.error.bracket", word());
                }
                return new Expr.Items(List.copyOf(items));
            }
            if (c == '(') {
                at++;
                Expr e = expr(words);
                spaces();
                if (!eat(")")) {
                    throw fail("residual.program.error.close", word());
                }
                return e;
            }
            String w = name();
            if (!w.isEmpty() && s.startsWith("(", at + w.length())) {
                if (Ops.fn(w) != null) {
                    return call(w);
                }
                // an old check goes straight on to its block: if ripe(harvest)
                Sense sense = sense(w);
                if (sense == null || sense.arg != Arg.NONE) {
                    throw fail("residual.program.error.function", w);
                }
            }
            if (w.equals("true") || w.equals("false") || w.equals("nothing")) {
                at += w.length();
                return literal(w);
            }
            Sense sense = sense(w);
            // among a function's values, a first check's word with nothing after it is only a word: property(front, powered)
            if (sense != null && !(words && sense.arg != Arg.NONE && wordEndsHere(w))) {
                at += w.length();
                return senseArgs(sense);
            }
            if (Program.SPOTS.contains(w)) {
                at += w.length();
                return new Expr.Spot(w);
            }
            if (words && !w.isEmpty()) {
                String thing = thingWords();
                if (!thing.isEmpty()) {
                    return new Expr.Words(thing);
                }
                // a clause's word with nothing after it is only a word: upgrades(speed)
                if (STOPS.contains(w) && !Set.of("and", "or", "except", "where").contains(w) && wordEndsHere(w)) {
                    at += w.length();
                    return new Expr.Words(w);
                }
            }
            throw fail(words ? "residual.program.error.missing" : "residual.program.error.sense", w.isEmpty() ? word() : w);
        }

        /** Whether nothing but a closing or a comma follows the word starting here. */
        boolean wordEndsHere(String w) {
            int after = at + w.length();
            while (after < s.length() && s.charAt(after) == ' ') {
                after++;
            }
            return after >= s.length() || ",)]".indexOf(s.charAt(after)) >= 0;
        }

        @Nullable
        static Sense sense(String w) {
            for (Sense sense : Sense.values()) {
                if (sense.word.equals(w)) {
                    return sense;
                }
            }
            return null;
        }

        Expr senseArgs(Sense sense) {
            switch (sense.arg) {
                case NONE -> {
                    return new Expr.Sense(sense, null, "", null);
                }
                case MARK -> {
                    spaces();
                    return new Expr.Sense(sense, null, "", isVariable() ? new Expr.Var(variable()) : new Expr.Lit(Value.of(number(1, MARKS))));
                }
                default -> {
                    spaces();
                    Expr n = null;
                    if (Character.isDigit(here())) {
                        n = new Expr.Lit(Value.of(number(0, 1 << 30)));
                        spaces();
                    } else if (isVariable()) {
                        n = new Expr.Var(variable());
                        spaces();
                    }
                    String thing = thingWords();
                    if (thing.isEmpty()) {
                        throw fail("residual.program.error.thing", "");
                    }
                    return new Expr.Sense(sense, n, thing, null);
                }
            }
        }

        /** A function's value: {@code count(front, iron ingot)}. Its arguments may be plain words. */
        Expr call(String w) {
            Ops.Fn fn = Ops.fn(w);
            int start = at;
            at += w.length() + 1;
            List<Expr> args = new ArrayList<>();
            spaces();
            if (here() != ')') {
                while (true) {
                    args.add(expr(true));
                    spaces();
                    if (!eat(",")) {
                        break;
                    }
                }
            }
            spaces();
            if (!eat(")")) {
                throw fail("residual.program.error.close", word());
            }
            if (args.size() < fn.least() || args.size() > fn.most()) {
                at = start;
                throw fail("residual.program.error.args", w);
            }
            return new Expr.Call(w, List.copyOf(args));
        }

        double decimal() {
            int end = at;
            while (end < s.length() && (Character.isDigit(s.charAt(end)) || s.charAt(end) == '.' && end + 1 < s.length() && Character.isDigit(s.charAt(end + 1)))) {
                end++;
            }
            String digits = s.substring(at, end);
            if (digits.isEmpty() || digits.length() > 12) {
                throw fail("residual.program.error.number", word());
            }
            at = end;
            return Double.parseDouble(digits);
        }

        int number(int least, int most) {
            int end = at;
            while (end < s.length() && Character.isDigit(s.charAt(end))) {
                end++;
            }
            String digits = s.substring(at, end);
            if (digits.isEmpty() || digits.length() > 10) {
                throw fail("residual.program.error.number", word());
            }
            long n = Long.parseLong(digits);
            if (n < least || n > most) {
                throw fail("residual.program.error.range", digits);
            }
            at = end;
            return (int) n;
        }
    }
}
