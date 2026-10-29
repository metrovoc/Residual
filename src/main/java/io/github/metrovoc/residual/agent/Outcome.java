package io.github.metrovoc.residual.agent;

import java.util.ArrayList;
import java.util.List;

/** How an act went: done, perhaps with a word on how, or failed and why. Keys are translation keys. */
record Outcome(boolean ok, String key, List<String> args) {
    static final Outcome DONE = new Outcome(true, "", List.of());

    static Outcome done(String key, Object... args) {
        return new Outcome(true, key, strings(args));
    }

    static Outcome failed(String key, Object... args) {
        return new Outcome(false, key, strings(args));
    }

    static List<String> strings(Object... args) {
        List<String> out = new ArrayList<>();
        for (Object a : args) {
            out.add(String.valueOf(a));
        }
        return List.copyOf(out);
    }
}
