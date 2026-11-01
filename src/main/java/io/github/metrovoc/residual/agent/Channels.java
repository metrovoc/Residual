package io.github.metrovoc.residual.agent;

import com.mojang.serialization.Codec;
import io.github.metrovoc.residual.Residual;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Team channels: variables all of a team's bodies share, wherever they stand, kept with the world. A value is saved as a
 * program would write it, so what it holds is what a program can read back.
 */
public final class Channels extends SavedData {
    private static final Codec<Channels> CODEC = Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.STRING))
            .xmap(Channels::new, Channels::written);
    public static final SavedDataType<Channels> TYPE = new SavedDataType<>(Residual.id("channels"), Channels::new, CODEC);

    /** By team, the value of each channel. */
    private final Map<String, Map<String, Value>> teams = new HashMap<>();

    private Channels() {
    }

    private Channels(Map<String, Map<String, String>> saved) {
        saved.forEach((team, channels) -> {
            Map<String, Value> values = new HashMap<>();
            channels.forEach((name, text) -> values.put(name, Saved.read(text)));
            teams.put(team, values);
        });
    }

    private Map<String, Map<String, String>> written() {
        Map<String, Map<String, String>> out = new HashMap<>();
        teams.forEach((team, channels) -> {
            Map<String, String> texts = new HashMap<>();
            channels.forEach((name, v) -> texts.put(name, v.source()));
            out.put(team, texts);
        });
        return out;
    }

    private static Channels of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    static Value get(Body a, String name) {
        Map<String, Value> team = of(a.world().getServer()).teams.get(a.team());
        return team == null ? Value.NONE : team.getOrDefault(name, Value.NONE);
    }

    static void set(Body a, String name, Value v) {
        Channels c = of(a.world().getServer());
        c.teams.computeIfAbsent(a.team(), t -> new HashMap<>()).put(name, v);
        c.setDirty();
    }

    /** Every channel of {@code a}'s team, for the debugger. */
    static Map<String, Value> all(Body a) {
        return of(a.world().getServer()).teams.getOrDefault(a.team(), Map.of());
    }
}
