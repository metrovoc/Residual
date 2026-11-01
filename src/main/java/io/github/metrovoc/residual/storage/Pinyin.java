package io.github.metrovoc.residual.storage;

import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/**
 * Chinese names found by how they sound. Letters typed match a character by any of its readings: the whole syllable, its
 * first letters, or just the first, so 铁矿石 is {@code tiekuangshi}, {@code tkshi} and {@code tks}. Letters still match
 * themselves, and a name with no Chinese in it is only searched for as written.
 */
public final class Pinyin {
    private static final Char2ObjectOpenHashMap<String[]> READINGS = load();

    private Pinyin() {
    }

    /** {@code assets/residual/pinyin.txt}: a line for each syllable, then the characters that read so (tools/pinyin_table.py). */
    private static Char2ObjectOpenHashMap<String[]> load() {
        Char2ObjectOpenHashMap<String[]> out = new Char2ObjectOpenHashMap<>(24_000);
        try (InputStream in = Pinyin.class.getResourceAsStream("/assets/residual/pinyin.txt");
             BufferedReader lines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String line = lines.readLine(); line != null; line = lines.readLine()) {
                int at = 0;
                while (at < line.length() && line.charAt(at) < 128) {
                    at++;
                }
                String syllable = line.substring(0, at);
                for (; at < line.length(); at++) {
                    String[] known = out.get(line.charAt(at));
                    if (known == null) {
                        out.put(line.charAt(at), new String[]{syllable});
                    } else {
                        String[] more = Arrays.copyOf(known, known.length + 1);
                        more[known.length] = syllable;
                        out.put(line.charAt(at), more);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    /** Whether {@code text} holds {@code typed}, as written or by the sound of its Chinese. */
    public static boolean holds(String text, String typed) {
        String lower = text.toLowerCase(Locale.ROOT), want = typed.toLowerCase(Locale.ROOT);
        if (lower.contains(want)) {
            return true;
        }
        int n = want.length();
        if (n == 0 || !want.chars().allMatch(c -> c < 128) || lower.chars().allMatch(c -> c < 128)) {
            return false;
        }
        // how much of what was typed can be read, ending at each character of the name, starting at any
        boolean[] read = new boolean[n + 1];
        for (int i = 0; i < lower.length(); i++) {
            read[0] = true;
            boolean[] next = new boolean[n + 1];
            char c = lower.charAt(i);
            String[] readings = READINGS.get(c);
            for (int j = 0; j < n; j++) {
                if (!read[j]) {
                    continue;
                }
                if (want.charAt(j) == c) {
                    next[j + 1] = true;
                }
                if (readings != null) {
                    for (String reading : readings) {
                        for (int len = 1; len <= reading.length() && j + len <= n && want.charAt(j + len - 1) == reading.charAt(len - 1); len++) {
                            next[j + len] = true;
                        }
                    }
                }
            }
            if (next[n]) {
                return true;
            }
            read = next;
        }
        return false;
    }
}
