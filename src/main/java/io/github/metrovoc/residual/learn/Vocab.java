package io.github.metrovoc.residual.learn;

import java.util.Arrays;

/** A character-level vocabulary: token id i is the character {@code symbols.charAt(i)}. */
public final class Vocab {
    /** The vocabulary of every model in the world, so any model can be tested on anything. */
    public static final Vocab WORLD = new Vocab("\n !\"'(),-.0123456789:;=>?ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz|");
    /** The longest stretch of text any model reads at once. */
    public static final int CONTEXT = 64;

    public final String symbols;
    private final int[] index = new int[65536];

    public Vocab(String symbols) {
        this.symbols = symbols;
        Arrays.fill(index, -1);
        for (int i = 0; i < symbols.length(); i++) {
            index[symbols.charAt(i)] = i;
        }
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
