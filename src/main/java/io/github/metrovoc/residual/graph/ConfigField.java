package io.github.metrovoc.residual.graph;

import java.util.List;

/** One adjustable setting of an operator: a key, its allowed values and the default. */
public record ConfigField(String key, List<String> choices, String fallback) {
    public static final String VOCAB = "vocab";

    public static ConfigField choice(String key, String fallback, String... choices) {
        return new ConfigField(key, List.of(choices), fallback);
    }

    public static ConfigField toggle(String key, boolean fallback) {
        return new ConfigField(key, List.of("false", "true"), Boolean.toString(fallback));
    }

    public static ConfigField width(String key, String fallback, boolean allowVocab) {
        return allowVocab
                ? choice(key, fallback, VOCAB, "4", "8", "16", "24", "32", "48", "64", "96", "128", "192", "256")
                : choice(key, fallback, "4", "8", "16", "24", "32", "48", "64", "96", "128", "192", "256");
    }

    public boolean isToggle() {
        return choices.size() == 2 && choices.contains("true") && choices.contains("false");
    }

    /** The value after {@code current} in the choice list, wrapping around ({@code delta} = +1 or -1). */
    public String cycle(String current, int delta) {
        int i = choices.indexOf(current);
        if (i < 0) {
            return fallback;
        }
        return choices.get(Math.floorMod(i + delta, choices.size()));
    }
}
