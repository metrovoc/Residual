package io.github.metrovoc.residual.learn;

import java.util.Arrays;

/** A character-level vocabulary: token id i is the character {@code symbols.charAt(i)}. */
public final class Vocab {
    public final String symbols;
    private final int[] index = new int[65536];

    public Vocab(String symbols) {
        this.symbols = symbols;
        Arrays.fill(index, -1);
        for (int i = 0; i < symbols.length(); i++) {
            index[symbols.charAt(i)] = i;
        }
    }

    /** All distinct characters of {@code text}, sorted. */
    public static Vocab of(String text) {
        boolean[] seen = new boolean[65536];
        for (int i = 0; i < text.length(); i++) {
            seen[text.charAt(i)] = true;
        }
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < seen.length; c++) {
            if (seen[c]) {
                sb.append((char) c);
            }
        }
        return new Vocab(sb.toString());
    }

    public int size() {
        return symbols.length();
    }

    public int id(char c) {
        return index[c];
    }

    public boolean has(char c) {
        return index[c] >= 0;
    }

    public char symbol(int id) {
        return symbols.charAt(id);
    }

    /** Encodes text, skipping characters outside the vocabulary. */
    public int[] encode(String text) {
        int[] out = new int[text.length()];
        int n = 0;
        for (int i = 0; i < text.length(); i++) {
            int id = index[text.charAt(i)];
            if (id >= 0) {
                out[n++] = id;
            }
        }
        return Arrays.copyOf(out, n);
    }

    public String decode(int[] ids) {
        StringBuilder sb = new StringBuilder(ids.length);
        for (int id : ids) {
            sb.append(symbols.charAt(id));
        }
        return sb.toString();
    }

    /** A printable label for a token (newlines and spaces made visible). */
    public String label(int id) {
        char c = symbols.charAt(id);
        return switch (c) {
            case '\n' -> "⏎";
            case ' ' -> "␣";
            default -> String.valueOf(c);
        };
    }
}
