package io.github.metrovoc.residual.refine;

import java.util.List;

/**
 * One copy of a piece of writing on its way through a Refinery: the stretches of it that can be read, and the kind of writing
 * it is. Practice never reads across from one stretch to the next.
 */
public record Doc(List<String> parts, Domain domain) {
    public Doc(String text, Domain domain) {
        this(List.of(text), domain);
    }

    public long chars() {
        long n = 0;
        for (String p : parts) {
            n += p.length();
        }
        return n;
    }

    /** The doc with the same writing in other stretches. */
    public Doc with(List<String> stretches) {
        return new Doc(stretches, domain);
    }

    public static long chars(List<Doc> docs) {
        long n = 0;
        for (Doc d : docs) {
            n += d.chars();
        }
        return n;
    }
}
