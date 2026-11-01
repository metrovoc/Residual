package io.github.metrovoc.residual.refine;

import io.github.metrovoc.residual.learn.Kinds;
import io.github.metrovoc.residual.learn.Orders;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Writing no one wrote: text a Program Press makes up by rule, as much as it is asked for. What a rule writes is right by
 * construction and costs nothing to find, and it knows only what the rule knows. Orders and sorting lines draw only on what those
 * benchmarks' practice names, so nothing of a test gets in unless a book of templates puts it there.
 */
public final class Programs {
    public enum Program {
        SUMS, ORDERS, SORTING, BOOK;

        public final String id = name().toLowerCase(Locale.ROOT);
    }

    /** What each doc a program writes comes to, at most. */
    private static final int DOC = 1500;
    private static final String[][] SUMS = {
            {"%1$d carts carry %2$d sacks each. How many sacks is that?", "Working: %1$d times %2$d is %3$d.", "Answer: %3$d sacks."},
            {"The store has %1$d jars on each of %2$d shelves. How many jars?", "Working: %1$d times %2$d is %3$d.", "Answer: %3$d jars."},
            {"%6$s has %4$d emeralds and earns %2$d more at the market. How many now?", "Working: %4$d and %2$d is %5$d.", "Answer: %5$d emeralds."},
            {"There were %5$d loaves on the board and %2$d were sold. How many are left?", "Working: take %2$d from %5$d. That leaves %4$d.", "Answer: %4$d loaves."}};
    private static final String[] NAMES = {"Oren", "Mira", "Tam", "Wren", "Corin", "Hesk", "Nessa", "Pell", "Dace", "Bel"};

    private Programs() {
    }

    /** About {@code chars} characters of {@code program}'s writing; a book writes from its {@code pages}. */
    public static List<Doc> write(Program program, long chars, List<String> pages, long seed) {
        Random rng = new Random(seed);
        List<Doc> out = new ArrayList<>();
        long written = 0;
        while (written < chars) {
            String text = doc(program, pages, rng);
            if (text.isEmpty()) {
                break;
            }
            out.add(new Doc(text, Domain.PROGRAMS));
            written += text.length();
        }
        return out;
    }

    private static String doc(Program program, List<String> pages, Random rng) {
        StringBuilder out = new StringBuilder();
        int n = 0;
        // a book can have nothing on its pages
        for (int tries = 0; out.length() < DOC && tries < DOC; tries++) {
            String piece = switch (program) {
                case SUMS -> sum(rng, ++n);
                case ORDERS -> {
                    Orders.Example ex = Orders.example(rng, false);
                    yield Orders.prompt(ex.said()) + ex.meant();
                }
                case SORTING -> {
                    Kinds.Example ex = Kinds.example(rng, false);
                    yield Kinds.prompt(ex.name()) + ex.kind().letter;
                }
                case BOOK -> pages.isEmpty() ? "" : expand(pages.get(rng.nextInt(pages.size())), rng);
            };
            if (!piece.isEmpty()) {
                out.append(out.isEmpty() ? "" : "\n").append(piece);
            }
        }
        return out.toString();
    }

    private static String sum(Random rng, int number) {
        int a = 2 + rng.nextInt(12), b = 2 + rng.nextInt(12), c = 2 + rng.nextInt(60);
        String[] form = SUMS[rng.nextInt(SUMS.length)];
        return number + ". " + String.join("\n", form).formatted(a, b, a * b, c, c + b, NAMES[rng.nextInt(NAMES.length)]);
    }

    /** A template with its choices made: {@code {a|b|c}} comes out as one of them, and choices can hold choices. */
    public static String expand(String template, Random rng) {
        StringBuilder out = new StringBuilder(template.length());
        expand(template, 0, template.length(), rng, out);
        return out.toString();
    }

    private static void expand(String s, int from, int to, Random rng, StringBuilder out) {
        int i = from;
        while (i < to) {
            char c = s.charAt(i);
            if (c != '{') {
                out.append(c);
                i++;
                continue;
            }
            int close = close(s, i, to);
            if (close < 0) {
                out.append(s, i, to);
                return;
            }
            // the choices at this depth
            List<int[]> choices = new ArrayList<>();
            int start = i + 1, depth = 0;
            for (int k = i + 1; k < close; k++) {
                char d = s.charAt(k);
                depth += d == '{' ? 1 : d == '}' ? -1 : 0;
                if (d == '|' && depth == 0) {
                    choices.add(new int[]{start, k});
                    start = k + 1;
                }
            }
            choices.add(new int[]{start, close});
            int[] pick = choices.get(rng.nextInt(choices.size()));
            expand(s, pick[0], pick[1], rng, out);
            i = close + 1;
        }
    }

    private static int close(String s, int open, int to) {
        int depth = 0;
        for (int k = open; k < to; k++) {
            char d = s.charAt(k);
            depth += d == '{' ? 1 : d == '}' ? -1 : 0;
            if (depth == 0) {
                return k;
            }
        }
        return -1;
    }
}
