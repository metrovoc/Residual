package io.github.metrovoc.residual.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The palette clicks together whatever the language can say, and says what each piece does. */
class PiecesTest {
    private static JsonObject lang() throws IOException {
        return JsonParser.parseString(Files.readString(Path.of(System.getProperty("residual.resources"), "assets/residual/lang/en_us.json"))).getAsJsonObject();
    }

    @Test
    void everyVerbAndFunctionHasAPiece() {
        Set<String> words = Pieces.words();
        List<String> missing = new ArrayList<>();
        for (Program.Verb v : Program.Verb.values()) {
            if (!words.contains(v.word)) {
                missing.add(v.word);
            }
        }
        for (Ops.Fn fn : Ops.all()) {
            if (!words.contains(fn.name())) {
                missing.add(fn.name());
            }
        }
        for (Program.Sense s : Program.Sense.values()) {
            if (!words.contains(s.word)) {
                missing.add(s.word);
            }
        }
        for (String word : Program.SPOTS) {
            if (!words.contains(word)) {
                missing.add(word);
            }
        }
        for (Program.Event e : Program.Event.values()) {
            if (!words.contains(e == Program.Event.WHEN ? "when" : "on " + e.word)) {
                missing.add(e.word);
            }
        }
        for (String word : List.of("repeat", "if", "else", "while", "until", "for each", "wait until", "set $x", "$x", "@x", "#x", "$it", "return", "leave", "next", "stop", "breakpoint", "priority",
                "from", "to", "each", "at", "except", "where", "sneak", "speed", "pitch", "yaw", "for", "and", "or", "not", "true", "false", "nothing", "+", "-", "*", "/", "%", "=", "!=", "<", "<=", ">", ">=", ",", "[", "]")) {
            if (words.stream().noneMatch(w -> w.equals(word) || w.startsWith(word + " "))) {
                missing.add(word);
            }
        }
        assertEquals(List.of(), missing, "no piece for these");
    }

    @Test
    void everyPieceSaysWhatItDoes() throws IOException {
        JsonObject lang = lang();
        List<String> missing = new ArrayList<>();
        for (Pieces.Page page : Pieces.PAGES) {
            if (!lang.has(page.title())) {
                missing.add(page.title());
            }
            for (Pieces.Group group : page.groups()) {
                if (!lang.has(group.title())) {
                    missing.add(group.title());
                }
                for (Pieces.Piece piece : group.pieces()) {
                    if (!lang.has(piece.help())) {
                        missing.add(piece.help());
                    }
                }
            }
        }
        assertEquals(List.of(), missing);
    }

    @Test
    void piecesClickedTogetherMakeProgramsThatRead() {
        // a player with no code at all: only chips, joined the way the palette joins them
        String p = "";
        for (String[] click : new String[][]{{"set $x ", "STEP"}, {"count(", "WORD"}, {"front", "WORD"}, {",", "ON"}, {"coal", "WORD"}, {")", "ON"},
                {"if ", "STEP"}, {"$x ", "WORD"}, {"> ", "WORD"}, {"3", "WORD"}, {"(", "WORD"}, {"send ", "STEP"}, {"from ", "WORD"}, {"front", "WORD"}, {"to ", "WORD"}, {"network", "WORD"}, {")", "ON"}}) {
            p = Program.append(p, click[0], Program.Join.valueOf(click[1]));
        }
        Program.Parsed parsed = Program.parse(p);
        assertTrue(parsed.ok(), p + " → " + parsed.problem());
        assertEquals("set $x count(front, coal), if $x > 3 (send from front to network)", parsed.program().toString());
    }
}
