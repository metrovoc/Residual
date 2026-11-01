package io.github.metrovoc.residual.world;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.registry.ModAttachments;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * The kinds of ruin a Wayfinder leads to, as {@code tools/ruins.py} lists them: each one's structure, and the words a
 * record that tells of it uses. A player learns of a kind by reading a shard that tells of it; of the relay towers
 * everyone knows.
 */
public final class RuinKinds {
    public record Kind(String name, List<String> words) {
        public ResourceKey<Structure> structure() {
            return ResourceKey.create(Registries.STRUCTURE, Residual.id(name));
        }

        /** Whether {@code text} tells of this kind. */
        public boolean toldIn(String text) {
            String lower = text.toLowerCase(Locale.ROOT);
            return words.stream().anyMatch(w -> lower.contains(w.toLowerCase(Locale.ROOT)));
        }
    }

    /** The kind every player knows of: the lamp towers stood along every road. */
    public static final String KNOWN = "relay_tower";
    private static List<Kind> kinds;

    private RuinKinds() {
    }

    public static synchronized List<Kind> all() {
        if (kinds == null) {
            List<Kind> out = new ArrayList<>();
            try (InputStream in = RuinKinds.class.getResourceAsStream("/data/residual/ruin_kinds.json")) {
                if (in == null) {
                    throw new IOException("missing ruin_kinds.json");
                }
                for (JsonElement e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray()) {
                    JsonObject o = e.getAsJsonObject();
                    List<String> words = new ArrayList<>();
                    o.getAsJsonArray("words").forEach(w -> words.add(w.getAsString()));
                    out.add(new Kind(o.get("name").getAsString(), List.copyOf(words)));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            kinds = List.copyOf(out);
        }
        return kinds;
    }

    public static Optional<Kind> get(String name) {
        return all().stream().filter(k -> k.name().equals(name)).findFirst();
    }

    /** The kinds {@code player} knows of, in the list's order: the relay towers, and every kind a shard they read told of. */
    public static List<Kind> known(Player player) {
        List<String> read = player.getData(ModAttachments.KNOWN_RUINS.get());
        return all().stream().filter(k -> k.name().equals(KNOWN) || read.contains(k.name())).toList();
    }

    /** Adds the kinds {@code text} tells of to what {@code player} knows. */
    public static void learn(Player player, String text) {
        List<String> read = player.getData(ModAttachments.KNOWN_RUINS.get());
        List<String> more = new ArrayList<>(read);
        for (Kind k : all()) {
            if (!more.contains(k.name()) && k.toldIn(text)) {
                more.add(k.name());
            }
        }
        if (more.size() > read.size()) {
            player.setData(ModAttachments.KNOWN_RUINS.get(), List.copyOf(more));
        }
    }
}
