package io.github.metrovoc.residual.refine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** What a bad line does to text on its way through the relays. */
public final class Dirt {
    /** How badly a bad line garbles: this share of letters come through wrong, between the two. */
    public static final double GARBLE_LEAST = 0.06, GARBLE_MOST = 0.3;
    private static final String LETTERS = "abcdefghijklmnopqrstuvwxyz";

    private Dirt() {
    }

    /** Each letter comes through as another with chance {@code rate}. */
    public static String garble(String text, double rate, Random rng) {
        StringBuilder out = new StringBuilder(text);
        for (int i = 0; i < out.length(); i++) {
            char c = out.charAt(i);
            if ((c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z') && rng.nextDouble() < rate) {
                char to = LETTERS.charAt(rng.nextInt(LETTERS.length()));
                out.setCharAt(i, c >= 'a' ? to : Character.toUpperCase(to));
            }
        }
        return out.toString();
    }

    /** The words of each line come through in another order, every one of them still a word. */
    public static String scramble(String text, Random rng) {
        StringBuilder out = new StringBuilder(text.length());
        String[] lines = text.split("\n", -1);
        for (int l = 0; l < lines.length; l++) {
            if (l > 0) {
                out.append('\n');
            }
            String[] words = lines[l].split(" ");
            List<String> order = new ArrayList<>(words.length);
            Collections.addAll(order, words);
            Collections.shuffle(order, rng);
            out.append(String.join(" ", order));
        }
        return out.toString();
    }

    /** A few words go the wrong way or come through wrong, as they do when a message is passed on again. */
    public static String nudge(String text, double rate, Random rng) {
        String[] words = text.split(" ");
        for (int i = 0; i < words.length; i++) {
            if (rng.nextDouble() < rate) {
                words[i] = garble(words[i], 0.5, rng);
            }
        }
        return String.join(" ", words);
    }

    /** What a line carries when it carries nothing: pulses, ticks and counts. */
    public static String carrier(int chars, Random rng) {
        String[] beats = {". ", "- ", ".- ", "-. ", "0 ", "1 ", "01 ", "10 ", "... ", "--- "};
        String beat = beats[rng.nextInt(beats.length)], other = beats[rng.nextInt(beats.length)];
        StringBuilder out = new StringBuilder(chars);
        int column = 0;
        while (out.length() < chars) {
            String b = rng.nextFloat() < 0.8f ? beat : other;
            out.append(b);
            column += b.length();
            if (column > 48) {
                out.append('\n');
                column = 0;
            }
        }
        return out.toString();
    }
}
