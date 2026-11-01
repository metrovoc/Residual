package io.github.metrovoc.residual.agent;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.metrovoc.residual.Residual;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

/**
 * Who works together: a player's team, by name, kept with the world. Whoever makes a team invites others, and whoever is
 * invited can join. A body's team is its owner's; bodies of one team share channels and logistics, and a body whose access
 * is set to its team opens for any of them.
 */
public final class Teams extends SavedData {
    /** A team: who is on it, and who was asked. */
    private record Team(Set<UUID> members, Set<UUID> invited) {
        static final Codec<Team> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.listOf().fieldOf("members").forGetter(t -> List.copyOf(t.members)),
                UUIDUtil.STRING_CODEC.listOf().fieldOf("invited").forGetter(t -> List.copyOf(t.invited))
        ).apply(i, (m, n) -> new Team(new HashSet<>(m), new HashSet<>(n))));
    }

    private static final Codec<Teams> CODEC = Codec.unboundedMap(Codec.STRING, Team.CODEC).xmap(Teams::new, t -> t.teams);
    public static final SavedDataType<Teams> TYPE = new SavedDataType<>(Residual.id("teams"), Teams::new, CODEC);

    private final Map<String, Team> teams = new HashMap<>();
    private final Map<UUID, String> byPlayer = new HashMap<>();

    private Teams() {
    }

    private Teams(Map<String, Team> saved) {
        saved.forEach((name, team) -> {
            teams.put(name, new Team(new HashSet<>(team.members()), new HashSet<>(team.invited())));
            team.members().forEach(m -> byPlayer.put(m, name));
        });
    }

    private static Teams of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** What a player's channels are kept under: their team, or their own id if they are on none. */
    static String of(MinecraftServer server, @Nullable UUID player) {
        if (player == null) {
            return "";
        }
        String team = of(server).byPlayer.get(player);
        return team != null ? team : player.toString();
    }

    static boolean together(MinecraftServer server, UUID a, UUID b) {
        return a.equals(b) || of(server, a).equals(of(server, b));
    }

    // ---------------------------------------------------------------- /residual team

    /** {@code /residual team make|invite|join|leave}. */
    public static LiteralArgumentBuilder<CommandSourceStack> commands() {
        return Commands.literal("team")
                .then(Commands.literal("make").then(Commands.argument("name", StringArgumentType.word()).executes(Teams::make)))
                .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player()).executes(Teams::invite)))
                .then(Commands.literal("join").then(Commands.argument("name", StringArgumentType.word()).executes(Teams::join)))
                .then(Commands.literal("leave").executes(Teams::leave));
    }

    private static void say(ServerPlayer p, String key, Object... args) {
        p.sendSystemMessage(Component.translatable(key, args));
    }

    private static int make(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(c, "name");
        Teams t = of(p.level().getServer());
        if (t.teams.containsKey(name)) {
            say(p, "residual.team.taken", name);
            return 0;
        }
        t.drop(p.getUUID());
        t.teams.put(name, new Team(new HashSet<>(Set.of(p.getUUID())), new HashSet<>()));
        t.byPlayer.put(p.getUUID(), name);
        t.setDirty();
        say(p, "residual.team.made", name);
        return 1;
    }

    private static int invite(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerPlayer other = EntityArgument.getPlayer(c, "player");
        Teams t = of(p.level().getServer());
        String name = t.byPlayer.get(p.getUUID());
        if (name == null) {
            say(p, "residual.team.none");
            return 0;
        }
        t.teams.get(name).invited().add(other.getUUID());
        t.setDirty();
        say(p, "residual.team.invited", other.getName().getString());
        say(other, "residual.team.invite", p.getName().getString(), name);
        return 1;
    }

    private static int join(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(c, "name");
        Teams t = of(p.level().getServer());
        Team team = t.teams.get(name);
        if (team == null || !team.invited().contains(p.getUUID())) {
            say(p, "residual.team.not_invited", name);
            return 0;
        }
        t.drop(p.getUUID());
        team.invited().remove(p.getUUID());
        team.members().add(p.getUUID());
        t.byPlayer.put(p.getUUID(), name);
        t.setDirty();
        say(p, "residual.team.joined", name);
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Teams t = of(p.level().getServer());
        t.drop(p.getUUID());
        t.setDirty();
        say(p, "residual.team.left");
        return 1;
    }

    /** Takes a player off their team; a team with no one on it goes. */
    private void drop(UUID player) {
        String name = byPlayer.remove(player);
        Team team = name == null ? null : teams.get(name);
        if (team != null) {
            team.members().remove(player);
            if (team.members().isEmpty()) {
                teams.remove(name);
            }
        }
    }
}
