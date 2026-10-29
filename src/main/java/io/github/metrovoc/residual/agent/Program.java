package io.github.metrovoc.residual.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What an agent does, written the way a model writes it and a player reads it before saying yes:
 * {@code take bread, come, give bread}, or {@code repeat (harvest, plant wheat seeds, put wheat)}. Steps run in
 * order; a block runs its steps again, or only while a check holds.
 */
public record Program(List<Step> steps) {
    /** The longest program an agent keeps, in characters. */
    public static final int LONGEST = 400;
    /** How deep blocks may sit inside one another. */
    public static final int DEEPEST = 4;
    /** Marks are numbered from 1, the way a Survey Rod hands them over. */
    public static final int MARKS = 8;
    /** The longest wait, in seconds. */
    public static final int LONGEST_WAIT = 600;

    /** What follows a verb or a check in a step. */
    public enum Arg {
        /** Nothing. */
        NONE,
        /** A mark's number: {@code go 3}. */
        MARK,
        /** A thing, maybe with how many: {@code take 5 bread}, {@code take bread}. */
        THING,
        /** A thing, or nothing for everything: {@code put wheat}, {@code put}. */
        THING_OR_ALL,
        /** A thing, then a mark's number: {@code place cobblestone 2}. */
        THING_AT_MARK,
        /** Seconds: {@code wait 10}. */
        SECONDS,
        /** Words to the end of the step: {@code say done}. */
        WORDS
    }

    /** Everything an agent can do in one step. */
    public enum Verb {
        COME(Arg.NONE), HOME(Arg.NONE), GO(Arg.MARK), STAY(Arg.NONE), FOLLOW(Arg.NONE),
        TAKE(Arg.THING), PUT(Arg.THING_OR_ALL), GIVE(Arg.THING), FETCH(Arg.THING), PICKUP(Arg.NONE),
        HARVEST(Arg.NONE), PLANT(Arg.THING), TILL(Arg.NONE), MINE(Arg.THING), PLACE(Arg.THING_AT_MARK),
        WAIT(Arg.SECONDS), SAY(Arg.WORDS),
        FARM(Arg.NONE), STORE(Arg.NONE), SORT(Arg.NONE), HAUL(Arg.NONE), BUILD(Arg.NONE);

        public final String word = name().toLowerCase(Locale.ROOT);
        public final Arg arg;

        Verb(Arg arg) {
            this.arg = arg;
        }
    }

    /** What an agent can find out before it decides. */
    public enum Sense {
        HAVE(Arg.THING), STOCKED(Arg.THING), RIPE(Arg.NONE), FULL(Arg.NONE), NIGHT(Arg.NONE), NEAR(Arg.NONE),
        POWERED(Arg.MARK);

        public final String word = name().toLowerCase(Locale.ROOT);
        public final Arg arg;

        Sense(Arg arg) {
            this.arg = arg;
        }
    }

    public sealed interface Step permits Act, Repeat, If, Until, WaitUntil {
    }

    /**
     * One thing to do. {@code count} is 0 when no number was given, {@code thing} empty when no thing, {@code mark}
     * 0 when no mark; a wait keeps its seconds in {@code count}, and {@code say} its words in {@code thing}.
     */
    public record Act(Verb verb, int count, String thing, int mark) implements Step {
        public Act(Verb verb) {
            this(verb, 0, "", 0);
        }
    }

    /** The same steps again, {@code times} times, or for good when {@code times} is 0. */
    public record Repeat(int times, List<Step> body) implements Step {
    }

    public record If(Check check, List<Step> then, List<Step> otherwise) implements Step {
    }

    /** The same steps again until the check holds; checked before each round. */
    public record Until(Check check, List<Step> body) implements Step {
    }

    public record WaitUntil(Check check) implements Step {
    }

    /** Something to find out, the other way round if {@code not}. Arguments as in {@link Act}. */
    public record Check(Sense sense, boolean not, int count, String thing, int mark) {
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
        String s = text.replaceAll("\\s", " ").strip().toLowerCase(Locale.ROOT);
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

    /**
     * {@code text} with {@code piece} joined on the end, then with whatever plainly comes next: the parenthesis a block
     * opens with, the mark a {@code place} needs.
     */
    public static String append(String text, String piece, Join join) {
        String end = text.stripTrailing();
        String joined = switch (join) {
            case STEP -> end.isEmpty() || end.endsWith("(") ? end + piece : end + (end.endsWith(",") ? " " : ", ") + piece;
            case WORD -> end.isEmpty() ? piece : end + " " + piece;
            case ON -> end + piece;
        };
        Parsed p = parse(joined);
        if (p.ok() || p.problem.at + p.problem.word.length() < joined.replaceAll("\\s", " ").strip().length()) {
            return joined;
        }
        String word = p.problem.word;
        if (p.problem.key.equals("residual.program.error.open") && word.isEmpty()) {
            return joined.stripTrailing() + " (";
        }
        if (p.problem.key.equals("residual.program.error.mark") && !word.isEmpty() && !Character.isDigit(word.charAt(0))) {
            return joined.stripTrailing() + " 1";
        }
        return joined;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        write(sb, steps);
        return sb.toString();
    }

    /** Whether it goes on for good: a repeat without a count, or a stay, among its own steps. */
    public boolean endless() {
        for (Step s : steps) {
            if (s instanceof Repeat r && r.times() == 0 || s instanceof Act a && a.verb() == Verb.STAY) {
                return true;
            }
        }
        return false;
    }

    /** One step as written, a block only by what starts it: {@code take 5 bread}, {@code repeat 3}, {@code if ripe}. */
    public static String text(Step step) {
        StringBuilder sb = new StringBuilder();
        switch (step) {
            case Repeat r -> sb.append(r.times() > 0 ? "repeat " + r.times() : "repeat");
            case If i -> write(sb.append("if "), i.check());
            case Until u -> write(sb.append("until "), u.check());
            default -> write(sb, step);
        }
        return sb.toString();
    }

    public static String text(Check check) {
        StringBuilder sb = new StringBuilder();
        write(sb, check);
        return sb.toString();
    }

    /** How many steps there are, counting those inside blocks and the blocks themselves. */
    public int size() {
        return size(steps);
    }

    private static int size(List<Step> steps) {
        int n = 0;
        for (Step s : steps) {
            n += 1 + switch (s) {
                case Repeat r -> size(r.body());
                case If i -> size(i.then()) + size(i.otherwise());
                case Until u -> size(u.body());
                default -> 0;
            };
        }
        return n;
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
                args(sb, a.verb().arg, a.count(), a.thing(), a.mark());
            }
            case Repeat r -> {
                sb.append("repeat");
                if (r.times() > 0) {
                    sb.append(' ').append(r.times());
                }
                block(sb, r.body());
            }
            case If i -> {
                sb.append("if ");
                write(sb, i.check());
                block(sb, i.then());
                if (!i.otherwise().isEmpty()) {
                    sb.append(" else");
                    block(sb, i.otherwise());
                }
            }
            case Until u -> {
                sb.append("until ");
                write(sb, u.check());
                block(sb, u.body());
            }
            case WaitUntil w -> {
                sb.append("wait until ");
                write(sb, w.check());
            }
        }
    }

    private static void write(StringBuilder sb, Check c) {
        if (c.not()) {
            sb.append("not ");
        }
        sb.append(c.sense().word);
        args(sb, c.sense().arg, c.count(), c.thing(), c.mark());
    }

    private static void block(StringBuilder sb, List<Step> body) {
        sb.append(" (");
        write(sb, body);
        sb.append(')');
    }

    private static void args(StringBuilder sb, Arg arg, int count, String thing, int mark) {
        switch (arg) {
            case NONE -> {
            }
            case MARK -> sb.append(' ').append(mark);
            case SECONDS -> sb.append(' ').append(count);
            case WORDS -> sb.append(' ').append(thing);
            case THING, THING_OR_ALL, THING_AT_MARK -> {
                if (count > 0) {
                    sb.append(' ').append(count);
                }
                if (!thing.isEmpty()) {
                    sb.append(' ').append(thing);
                }
                if (arg == Arg.THING_AT_MARK) {
                    sb.append(' ').append(mark);
                }
            }
        }
    }

    private static final class Slip extends RuntimeException {
        final Problem problem;

        Slip(Problem problem) {
            super(problem.key(), null, false, false);
            this.problem = problem;
        }
    }

    /** Recursive descent over the text, one step at a time. */
    private static final class Reader {
        final String s;
        int at;

        Reader(String s) {
            this.s = s;
        }

        Slip fail(String key, String word) {
            return new Slip(new Problem(key, word, at));
        }

        boolean done() {
            return at >= s.length();
        }

        /** The word starting here, for an error message. */
        String word() {
            int end = at;
            while (end < s.length() && " ,()".indexOf(s.charAt(end)) < 0) {
                end++;
            }
            return s.substring(at, end);
        }

        boolean eat(String token) {
            if (s.startsWith(token, at)) {
                at += token.length();
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
            String w = word();
            if (w.equals("repeat")) {
                at += w.length();
                spaces();
                int times = 0;
                if (!done() && Character.isDigit(s.charAt(at))) {
                    times = number(1, 1000);
                }
                return new Repeat(times, block(depth));
            }
            if (w.equals("if")) {
                at += w.length();
                Check c = check();
                List<Step> then = block(depth);
                int back = at;
                spaces();
                if (eat("else")) {
                    return new If(c, then, block(depth));
                }
                at = back;
                return new If(c, then, List.of());
            }
            if (w.equals("until")) {
                at += w.length();
                Check c = check();
                return new Until(c, block(depth));
            }
            if (w.equals("wait")) {
                int back = at;
                at += w.length();
                spaces();
                if (eat("until")) {
                    return new WaitUntil(check());
                }
                at = back;
            }
            for (Verb v : Verb.values()) {
                if (v.word.equals(w)) {
                    at += w.length();
                    Args a = args(v.arg);
                    return new Act(v, a.count, a.thing, a.mark);
                }
            }
            throw fail(w.isEmpty() ? "residual.program.error.missing" : "residual.program.error.verb", w);
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

        Check check() {
            spaces();
            boolean not = false;
            if (word().equals("not")) {
                at += 3;
                spaces();
                not = true;
            }
            String w = word();
            for (Sense sense : Sense.values()) {
                if (sense.word.equals(w)) {
                    at += w.length();
                    Args a = args(sense.arg);
                    return new Check(sense, not, a.count, a.thing, a.mark);
                }
            }
            throw fail("residual.program.error.sense", w);
        }

        record Args(int count, String thing, int mark) {
        }

        /** The words up to the next comma or parenthesis. */
        String rest() {
            int end = at;
            while (end < s.length() && ",()".indexOf(s.charAt(end)) < 0) {
                end++;
            }
            String words = s.substring(at, end).strip().replaceAll(" +", " ");
            at = end;
            return words;
        }

        Args args(Arg arg) {
            int start = at;
            String words = rest();
            if (arg == Arg.NONE) {
                if (!words.isEmpty()) {
                    at = start;
                    spaces();
                    throw fail("residual.program.error.stray", word());
                }
                return new Args(0, "", 0);
            }
            at = start;
            spaces();
            switch (arg) {
                case MARK -> {
                    return new Args(0, "", number(1, MARKS));
                }
                case SECONDS -> {
                    return new Args(number(1, LONGEST_WAIT), "", 0);
                }
                case WORDS -> {
                    if (words.isEmpty()) {
                        throw fail("residual.program.error.words", "");
                    }
                    at = start;
                    rest();
                    return new Args(0, words, 0);
                }
                default -> {
                    int count = 0;
                    if (!done() && Character.isDigit(s.charAt(at))) {
                        count = number(1, 64 * 36);
                        spaces();
                    }
                    int mark = 0;
                    int from = at;
                    String thing = rest();
                    if (arg == Arg.THING_AT_MARK) {
                        int space = thing.lastIndexOf(' ');
                        String last = thing.substring(space + 1);
                        if (space < 0 || !last.chars().allMatch(Character::isDigit) || last.length() > 1
                                || last.charAt(0) < '1' || last.charAt(0) > '0' + MARKS) {
                            at = s.lastIndexOf(last, at);
                            throw fail("residual.program.error.mark", last);
                        }
                        mark = last.charAt(0) - '0';
                        thing = thing.substring(0, space);
                    }
                    if (thing.isEmpty() && arg != Arg.THING_OR_ALL) {
                        throw fail("residual.program.error.thing", "");
                    }
                    if (!thing.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == ' ')) {
                        at = from;
                        throw fail("residual.program.error.thing", thing);
                    }
                    return new Args(count, thing, mark);
                }
            }
        }

        int number(int least, int most) {
            int end = at;
            while (end < s.length() && Character.isDigit(s.charAt(end))) {
                end++;
            }
            String digits = s.substring(at, end);
            if (digits.isEmpty() || digits.length() > 5) {
                throw fail("residual.program.error.number", word());
            }
            int n = Integer.parseInt(digits);
            if (n < least || n > most) {
                throw fail("residual.program.error.range", digits);
            }
            at = end;
            return n;
        }
    }
}
