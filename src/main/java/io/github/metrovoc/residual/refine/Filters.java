package io.github.metrovoc.residual.refine;

import java.util.ArrayList;
import java.util.List;

/**
 * Rules about what text looks like, cheap enough to run on everything, each of which throws a doc away on its own. None of them
 * reads: text that looks right passes whatever it says.
 */
public final class Filters {
    public enum Rule {
        /** Too few words to be worth a window. */
        SHORT,
        /** More marks and digits than writing has. */
        SYMBOLS,
        /** Words too long or too short on average to be words. */
        WORDS,
        /** A few lines said over and over. */
        REPEATS,
        /** Lines that hardly ever end the way a sentence or a verse does. */
        ENDINGS;

        public final String id = name().toLowerCase(java.util.Locale.ROOT);
    }

    static final int FEWEST_WORDS = 12;
    static final double MOST_SYMBOLS = 0.2, SHORTEST_WORD = 2.5, LONGEST_WORD = 9, MOST_REPEATED = 0.5, FEWEST_ENDINGS = 0.25;

    /**
     * What a pass did.
     *
     * @param kept    what goes on
     * @param docs    docs each rule threw away; a doc goes to the first rule it fails
     * @param chars   and their characters
     * @param example the start of the first thing each rule threw away, or empty
     */
    public record Result(List<Doc> kept, int[] docs, long[] chars, String[] example) {
    }

    private Filters() {
    }

    /** Runs the rules whose bit is set in {@code on}. */
    public static Result run(List<Doc> docs, int on) {
        int n = Rule.values().length;
        int[] count = new int[n];
        long[] chars = new long[n];
        String[] example = new String[n];
        java.util.Arrays.fill(example, "");
        List<Doc> kept = new ArrayList<>(docs.size());
        for (Doc d : docs) {
            Rule failed = fails(d, on);
            if (failed == null) {
                kept.add(d);
            } else {
                int r = failed.ordinal();
                count[r]++;
                chars[r] += d.chars();
                if (example[r].isEmpty() && !d.parts().isEmpty()) {
                    String p = d.parts().getFirst();
                    example[r] = p.substring(0, Math.min(p.length(), 120));
                }
            }
        }
        return new Result(kept, count, chars, example);
    }

    /** The first rule in {@code on} that {@code d} fails, or null. */
    static Rule fails(Doc d, int on) {
        long letters = 0, symbols = 0, wordChars = 0;
        int words = 0, lines = 0, endings = 0;
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        int top = 0;
        for (String part : d.parts()) {
            for (String line : part.split("\n")) {
                String l = line.strip();
                if (l.isEmpty()) {
                    continue;
                }
                lines++;
                char last = l.charAt(l.length() - 1);
                endings += last == '.' || last == '!' || last == '?' || last == ',' || last == ';' || last == ':' || last == '"'
                        || last == '\'' || last == ')' ? 1 : 0;
                top = Math.max(top, counts.merge(l, 1, Integer::sum));
                boolean in = false;
                for (int i = 0; i < l.length(); i++) {
                    char c = l.charAt(i);
                    boolean letter = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
                    if (letter) {
                        letters++;
                        wordChars++;
                        words += in ? 0 : 1;
                        in = true;
                    } else {
                        in = c == '\'' && in;
                        symbols += c == ' ' ? 0 : 1;
                    }
                }
            }
        }
        if (on(on, Rule.SHORT) && words < FEWEST_WORDS) {
            return Rule.SHORT;
        }
        if (on(on, Rule.SYMBOLS) && symbols > MOST_SYMBOLS * (symbols + letters)) {
            return Rule.SYMBOLS;
        }
        double mean = words == 0 ? 0 : (double) wordChars / words;
        if (on(on, Rule.WORDS) && (mean < SHORTEST_WORD || mean > LONGEST_WORD)) {
            return Rule.WORDS;
        }
        if (on(on, Rule.REPEATS) && lines >= 6 && top > MOST_REPEATED * lines) {
            return Rule.REPEATS;
        }
        if (on(on, Rule.ENDINGS) && lines >= 6 && endings < FEWEST_ENDINGS * lines) {
            return Rule.ENDINGS;
        }
        return null;
    }

    private static boolean on(int on, Rule r) {
        return (on >> r.ordinal() & 1) != 0;
    }
}
