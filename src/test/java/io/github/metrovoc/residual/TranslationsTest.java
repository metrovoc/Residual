package io.github.metrovoc.residual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Every translation says everything the English says: the same keys and arguments, the same guide pages and links. */
class TranslationsTest {
    private static final Path ASSETS = Path.of(System.getProperty("residual.resources"), "assets/residual");
    private static final Path LANG = ASSETS.resolve("lang");
    private static final Path GUIDE = ASSETS.resolve("guides/residual/guide");
    private static final List<String> LANGUAGES = List.of("zh_cn");
    private static final Pattern ARG = Pattern.compile("%(?:(\\d+)\\$)?([s%])");
    /** What a page must keep: its tags and the targets of its links and images. */
    private static final Pattern STRUCTURE = Pattern.compile("<[A-Za-z][^>]*>|\\]\\([^)]*\\)");

    @Test
    void everyLanguageHasEveryKeyWithItsArguments() throws IOException {
        JsonObject en = read(LANG.resolve("en_us.json"));
        for (String language : LANGUAGES) {
            JsonObject other = read(LANG.resolve(language + ".json"));
            assertEquals(en.keySet(), other.keySet(), language);
            for (String key : en.keySet()) {
                assertEquals(arguments(en.get(key).getAsString()), arguments(other.get(key).getAsString()), language + " " + key);
            }
        }
    }

    @Test
    void everyLanguageHasEveryGuidePageWithItsLinks() throws IOException {
        List<Path> pages;
        try (Stream<Path> files = Files.walk(GUIDE)) {
            pages = files.filter(p -> p.toString().endsWith(".md") && !GUIDE.relativize(p).toString().startsWith("_")).toList();
        }
        assertTrue(pages.size() > 30, "pages found: " + pages.size());
        for (String language : LANGUAGES) {
            for (Path page : pages) {
                Path translated = GUIDE.resolve("_" + language).resolve(GUIDE.relativize(page));
                assertTrue(Files.exists(translated), "missing " + translated);
                assertEquals(structure(Files.readString(page)), structure(Files.readString(translated)), translated.toString());
            }
        }
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    /** How often each argument is used, by position; {@code %%} counts as position 0. */
    private static Map<Integer, Integer> arguments(String text) {
        Map<Integer, Integer> used = new TreeMap<>();
        Matcher m = ARG.matcher(text);
        int next = 1;
        while (m.find()) {
            int at = m.group(2).equals("%") ? 0 : m.group(1) != null ? Integer.parseInt(m.group(1)) : next++;
            used.merge(at, 1, Integer::sum);
        }
        return used;
    }

    private static List<String> structure(String page) {
        List<String> found = new ArrayList<>();
        Matcher m = STRUCTURE.matcher(page.replaceAll("\\s+", " "));
        while (m.find()) {
            found.add(m.group());
        }
        found.sort(null);
        return found;
    }
}
