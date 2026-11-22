package io.github.metrovoc.residual.interior;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.graph.CompileException;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.model.Checkpoints;
import io.github.metrovoc.residual.tensor.Tensor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The halls of a world: a place for every model, in a dimension of their own, each built from its model and rebuilt
 * when the model changes. A hall is awake while somebody stands in it or a Core holds its drive; its model trains only
 * then.
 */
public final class Halls extends SavedData {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, Residual.id("interior"));
    /** Halls stand this far apart, in rows of {@link #ROW}. */
    private static final int APART = 1024, ROW = 256;
    private static final int FLOOR = 64;
    /** Ticks a hall stays awake with nobody in it. */
    private static final int LINGER = 200;
    /** How far from a model's middle a player arrives, and how far out they may drift before being brought back. */
    public static final int VIEW = 40, EDGE = 300;
    private static final Identifier FLOATING = Residual.id("inside");

    /** One model's hall. */
    public static final class Hall {
        public final UUID id;
        final int cell;
        Arch arch;
        Stage stage;
        float rate;
        int pace;
        long seed, example;
        int steps;

        @Nullable
        Run run;
        /** Every part's weights by its node's name, parts that are switched off too. */
        final Map<String, Map<String, Tensor>> bank = new HashMap<>();
        @Nullable
        HallPlan built;
        @Nullable
        Snapshot last;
        @Nullable
        Snapshot.Tables tables;
        final Set<UUID> told = new HashSet<>();
        int empty;

        Hall(UUID id, int cell, Arch arch, Stage stage, float rate, int pace, long seed, long example, int steps) {
            this.id = id;
            this.cell = cell;
            this.arch = arch;
            this.stage = stage;
            this.rate = rate;
            this.pace = pace;
            this.seed = seed;
            this.example = example;
            this.steps = steps;
        }

        public BlockPos origin() {
            return new BlockPos(cell % ROW * APART, FLOOR, cell / ROW * APART);
        }

        public Arch arch() {
            return arch;
        }

        HallPlan plan() {
            return HallPlan.of(arch, stage.lanes());
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", id.toString());
            tag.putInt("cell", cell);
            tag.put("arch", arch.save());
            tag.putString("stage", stage.name());
            tag.putFloat("rate", rate);
            tag.putInt("pace", pace);
            tag.putLong("seed", seed);
            tag.putLong("example", example);
            tag.putInt("steps", run == null ? steps : run.steps());
            return tag;
        }

        static Hall load(CompoundTag tag) {
            Stage stage = Stage.TABLE;
            for (Stage s : Stage.values()) {
                if (s.name().equals(tag.getStringOr("stage", ""))) {
                    stage = s;
                }
            }
            return new Hall(UUID.fromString(tag.getStringOr("id", "")), tag.getIntOr("cell", 0), Arch.load(tag.getCompoundOrEmpty("arch")), stage,
                    tag.getFloatOr("rate", 0.003f), tag.getIntOr("pace", Run.PACES[1]), tag.getLongOr("seed", 1), tag.getLongOr("example", 1),
                    tag.getIntOr("steps", 0));
        }
    }

    /** Where a player was before stepping into a hall. */
    private record Return(String level, double x, double y, double z, float yRot, float xRot) {
        static final Codec<Return> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("level").forGetter(Return::level),
                Codec.DOUBLE.fieldOf("x").forGetter(Return::x),
                Codec.DOUBLE.fieldOf("y").forGetter(Return::y),
                Codec.DOUBLE.fieldOf("z").forGetter(Return::z),
                Codec.FLOAT.fieldOf("yRot").forGetter(Return::yRot),
                Codec.FLOAT.fieldOf("xRot").forGetter(Return::xRot)
        ).apply(i, Return::new));
    }

    private record Saved(int next, List<CompoundTag> halls, Map<String, Return> returns) {
        static final Codec<Saved> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("next").forGetter(Saved::next),
                CompoundTag.CODEC.listOf().fieldOf("halls").forGetter(Saved::halls),
                Codec.unboundedMap(Codec.STRING, Return.CODEC).fieldOf("returns").forGetter(Saved::returns)
        ).apply(i, Saved::new));
    }

    private static final Codec<Halls> CODEC = Saved.CODEC.xmap(Halls::new, Halls::saved);
    public static final SavedDataType<Halls> TYPE = new SavedDataType<>(Residual.id("halls"), Halls::new, CODEC);

    private final Map<UUID, Hall> halls = new LinkedHashMap<>();
    private final Map<Integer, Hall> byCell = new HashMap<>();
    private final Map<UUID, Return> returns = new HashMap<>();
    private int next;

    private Halls() {
    }

    private Halls(Saved saved) {
        next = saved.next;
        for (CompoundTag tag : saved.halls) {
            try {
                Hall hall = Hall.load(tag);
                halls.put(hall.id, hall);
                byCell.put(hall.cell, hall);
            } catch (IllegalArgumentException e) {
                Residual.LOG.error("A hall could not be read: {}", tag, e);
            }
        }
        saved.returns.forEach((player, back) -> returns.put(UUID.fromString(player), back));
    }

    private Saved saved() {
        List<CompoundTag> list = new ArrayList<>();
        halls.values().forEach(h -> list.add(h.save()));
        Map<String, Return> back = new HashMap<>();
        returns.forEach((player, r) -> back.put(player.toString(), r));
        return new Saved(next, list, back);
    }

    public static Halls get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** The hall of model {@code id}, made new, as a table, if there is none. */
    public Hall hall(UUID id) {
        Hall hall = halls.get(id);
        if (hall == null) {
            hall = new Hall(id, next++, Arch.table(), Stage.TABLE, 0.003f, Run.PACES[2], 1, 1, 0);
            halls.put(id, hall);
            byCell.put(hall.cell, hall);
            setDirty();
        }
        return hall;
    }

    @Nullable
    private Hall at(BlockPos pos) {
        int cx = Math.floorDiv(pos.getX() + APART / 2, APART), cz = Math.floorDiv(pos.getZ() + APART / 2, APART);
        return cx < 0 || cx >= ROW || cz < 0 ? null : byCell.get(cz * ROW + cx);
    }

    /** Takes a player into the hall of model {@code id}; false if the halls' dimension is missing. */
    public boolean enter(ServerPlayer player, UUID id) {
        ServerLevel level = player.level().getServer().getLevel(LEVEL);
        if (level == null) {
            return false;
        }
        Hall hall = hall(id);
        wake(level, hall);
        if (player.level().dimension() != LEVEL) {
            returns.put(player.getUUID(), new Return(player.level().dimension().identifier().toString(), player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot()));
            setDirty();
        }
        arrive(player, level, hall);
        return true;
    }

    /** Puts a player where the whole model is in view, looking at its middle. */
    private static void arrive(ServerPlayer player, ServerLevel level, Hall hall) {
        BlockPos o = hall.origin();
        player.teleport(new TeleportTransition(level, new Vec3(o.getX() + 0.5, o.getY() + 6, o.getZ() + 0.5 - VIEW), Vec3.ZERO, 0, 6, TeleportTransition.DO_NOTHING));
        floating(player, true);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
    }

    /** Inside a model nothing falls: a player there flies, and loses that on the way out. */
    private static void floating(ServerPlayer player, boolean on) {
        AttributeInstance fly = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT), gravity = player.getAttribute(Attributes.GRAVITY);
        if (fly == null || gravity == null || fly.hasModifier(FLOATING) == on) {
            return;
        }
        if (on) {
            fly.addTransientModifier(new AttributeModifier(FLOATING, 1, AttributeModifier.Operation.ADD_VALUE));
            gravity.addTransientModifier(new AttributeModifier(FLOATING, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else {
            fly.removeModifier(FLOATING);
            gravity.removeModifier(FLOATING);
        }
    }

    /** Takes a player back to where they stepped in from. */
    public void leave(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        Return back = returns.remove(player.getUUID());
        setDirty();
        ServerLevel to = null;
        if (back != null) {
            for (ServerLevel l : server.getAllLevels()) {
                if (l.dimension().identifier().toString().equals(back.level)) {
                    to = l;
                }
            }
        }
        if (to == null || back == null) {
            ServerLevel overworld = server.overworld();
            BlockPos spawn = overworld.getRespawnData().pos();
            player.teleport(new TeleportTransition(overworld, Vec3.atBottomCenterOf(spawn), Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
        } else {
            player.teleport(new TeleportTransition(to, new Vec3(back.x, back.y, back.z), Vec3.ZERO, back.yRot, back.xRot, TeleportTransition.DO_NOTHING));
        }
    }

    /** Loads a hall's model, lays the hall out and starts its run, paused. */
    private void wake(ServerLevel level, Hall hall) {
        hall.empty = 0;
        if (hall.run != null) {
            return;
        }
        CompiledGraph saved = Checkpoints.load(level.getServer(), hall.id);
        if (saved != null) {
            Arch.keep(saved, hall.bank);
        }
        // a hall that was built here before models were shown as space
        HallBuilder.clear(level, hall.origin(), hall.plan());
        rebuild(level, hall, false, false);
    }

    /** A loaded Core holds the drive of model {@code id}: its hall wakes if it slept, and stays awake with nobody in it. */
    public void keepAwake(MinecraftServer server, UUID id) {
        ServerLevel level = server.getLevel(LEVEL);
        if (level != null) {
            wake(level, hall(id));
        }
    }

    /** Saves a hall's model and stops its run. */
    private void sleep(MinecraftServer server, Hall hall) {
        if (hall.run == null) {
            return;
        }
        hall.run.close();
        hall.run.keep(hall.bank);
        hall.steps = hall.run.steps();
        hall.run = null;
        hall.last = null;
        hall.told.clear();
        write(server, hall);
        setDirty();
    }

    /** Writes every part's weights, those of parts switched off too. */
    private void write(MinecraftServer server, Hall hall) {
        try {
            Arch whole = hall.arch;
            for (Arch.Part p : hall.arch.parts()) {
                if (!p.on()) {
                    whole = whole.toggle(p.id());
                }
            }
            GraphSpec spec = whole.spec();
            Checkpoints.save(server, hall.id, CompiledGraph.compile(spec, new Env(Vocab.WORLD, Vocab.CONTEXT), Arch.carried(spec, hall.bank), hall.seed));
        } catch (IOException | CompileException e) {
            Residual.LOG.error("Could not save the model of hall {}", hall.id, e);
        }
    }

    /**
     * Puts a hall's model together again from its parts as they now are, and its blocks if the layout changed.
     *
     * @param fresh   with new weights and a run that starts from nothing
     * @param running whether the new run goes on at once
     */
    private void rebuild(ServerLevel level, Hall hall, boolean fresh, boolean running) {
        List<Snapshot.Point> curve = List.of();
        if (hall.run != null) {
            hall.run.close();
            hall.run.keep(hall.bank);
            hall.steps = hall.run.steps();
            curve = hall.run.curve();
        }
        if (fresh) {
            hall.bank.clear();
            hall.steps = 0;
            hall.seed++;
            curve = List.of();
        }
        GraphSpec spec = hall.arch.spec();
        CompiledGraph model;
        try {
            model = CompiledGraph.compile(spec, new Env(Vocab.WORLD, Vocab.CONTEXT), Arch.carried(spec, hall.bank), hall.seed);
        } catch (CompileException e) {
            throw new IllegalStateException("a hall's model did not compile: " + hall.arch, e);
        }
        hall.run = new Run(hall.arch, model, hall.stage.practice(), hall.stage.test(), hall.seed, hall.rate, hall.example, hall.stage.lanes(), hall.steps,
                curve, Math.clamp(Runtime.getRuntime().availableProcessors() / 2, 1, 4));
        hall.run.lossGoal(hall.stage.loss);
        hall.run.pace(hall.pace);
        hall.run.running(running);
        hall.run.start();
        hall.told.clear();
        setDirty();
    }

    /** A player inside a model does something to it. */
    public void act(ServerPlayer player, SpaceAct act) {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != LEVEL) {
            return;
        }
        Hall hall = at(player.blockPosition());
        if (hall == null || hall.run == null) {
            return;
        }
        if (act.role() == SpaceAct.HOME) {
            arrive(player, level, hall);
            return;
        }
        if (act.role() < 0 || act.role() >= HallPlan.Role.values().length) {
            return;
        }
        HallPlan.Role role = HallPlan.Role.values()[act.role()];
        boolean back = act.back();
        int part = act.part();
        Run run = hall.run;
        Arch arch = hall.arch;
        switch (role) {
            case EXIT -> leave(player);
            case RUN -> run.running(!run.running());
            case STEP -> run.step(back ? 10 : 1);
            case PACE -> run.pace(hall.pace = turn(Run.PACES, hall.pace, back));
            case RATE -> run.rate(hall.rate = turn(Run.RATES, hall.rate, back));
            case EXAMPLE -> run.example(hall.example = Math.max(1, hall.example + (back ? -1 : 1)));
            case RESTART -> rebuild(level, hall, true, false);
            case TASK -> {
                Stage[] all = Stage.values();
                hall.stage = all[Math.floorMod(hall.stage.ordinal() + (back ? -1 : 1), all.length)];
                hall.arch = hall.stage.fit(hall.arch);
                // what was learned on one task is no start for another
                rebuild(level, hall, true, false);
            }
            case WIDTH -> change(level, hall, arch.withWidth(turn(Arch.WIDTHS, arch.width(), back)), true);
            case HEADS -> {
                int heads = arch.heads();
                do {
                    heads = turn(Arch.HEADS, heads, back);
                } while (arch.width() % heads != 0);
                change(level, hall, arch.withHeads(heads), false);
            }
            case POSITIONS -> change(level, hall, arch.withPositions(!arch.positions()), false);
            case NORM -> change(level, hall, arch.withNorm(!arch.norm()), false);
            case ADD_ATTENTION -> {
                if (hall.stage.allowsAttention(arch)) {
                    change(level, hall, arch.insert(part, Arch.Kind.ATTENTION), false);
                } else {
                    player.sendOverlayMessage(Component.translatable("residual.inside.limit.attention", hall.stage.attention));
                }
            }
            case ADD_FEED_FORWARD -> change(level, hall, arch.insert(part, Arch.Kind.FEED_FORWARD), false);
            case SWITCH -> change(level, hall, arch.toggle(part), false);
            case REMOVE -> {
                hall.bank.remove(Arch.part(part));
                change(level, hall, arch.remove(part), false);
            }
        }
        setDirty();
    }

    private void change(ServerLevel level, Hall hall, @Nullable Arch arch, boolean fresh) {
        if (arch != null && !arch.equals(hall.arch)) {
            boolean running = hall.run != null && hall.run.running();
            hall.arch = arch;
            rebuild(level, hall, fresh, running && !fresh);
        }
    }

    private static int turn(int[] stops, int now, boolean back) {
        for (int i = 0; i < stops.length; i++) {
            if (stops[i] == now) {
                return stops[Math.floorMod(i + (back ? -1 : 1), stops.length)];
            }
        }
        return stops[0];
    }

    private static float turn(float[] stops, float now, boolean back) {
        for (int i = 0; i < stops.length; i++) {
            if (stops[i] == now) {
                return stops[Math.clamp(i + (back ? -1 : 1), 0, stops.length - 1)];
            }
        }
        return stops[2];
    }

    public static void tick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().getLevel(LEVEL);
        if (level == null) {
            return;
        }
        Halls halls = get(event.getServer());
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (player.level() != level) {
                floating(player, false);
            }
        }
        Map<Hall, List<ServerPlayer>> inside = new HashMap<>();
        for (ServerPlayer player : List.copyOf(level.players())) {
            Hall hall = halls.at(player.blockPosition());
            if (hall == null) {
                halls.leave(player);
                continue;
            }
            halls.wake(level, hall);
            floating(player, true);
            if (!hall.origin().closerToCenterThan(player.position(), EDGE)) {
                arrive(player, level, hall);
            }
            inside.computeIfAbsent(hall, h -> new ArrayList<>()).add(player);
        }
        for (Hall hall : halls.halls.values()) {
            if (hall.run == null) {
                continue;
            }
            List<ServerPlayer> players = inside.getOrDefault(hall, List.of());
            if (players.isEmpty() && ++hall.empty > LINGER) {
                halls.sleep(event.getServer(), hall);
                continue;
            }
            Snapshot shot = hall.run.poll();
            if (shot != null) {
                if (shot.tables() != null) {
                    hall.tables = shot.tables();
                }
                hall.last = shot;
            }
            if (hall.last == null) {
                continue;
            }
            for (ServerPlayer player : players) {
                boolean first = hall.told.add(player.getUUID());
                if (first || shot != null) {
                    Snapshot send = first && hall.last.tables() == null && hall.tables != null ? withTables(hall.last, hall.tables) : hall.last;
                    PacketDistributor.sendToPlayer(player, new HallSync(hall.origin(), hall.arch, hall.stage, send));
                }
            }
            hall.told.removeIf(id -> players.stream().noneMatch(p -> p.getUUID().equals(id)));
        }
    }

    private static Snapshot withTables(Snapshot s, Snapshot.Tables tables) {
        return new Snapshot(s.step(), s.state(), s.pace(), s.rate(), s.trainLoss(), s.testLoss(), s.score(), s.passed(), s.parameters(), s.stepMillis(),
                s.tokens(), s.targets(), s.scored(), s.embed(), s.position(), s.parts(), s.vocab(), s.chances(), s.laneLoss(), tables, s.curve());
    }

    public static void stopping(ServerStoppingEvent event) {
        Halls halls = get(event.getServer());
        halls.halls.values().forEach(h -> halls.sleep(event.getServer(), h));
    }

    /** Nothing in a hall is dug or built by hand. */
    public static void breaking(net.neoforged.neoforge.event.level.block.BreakBlockEvent event) {
        if (event.getPlayer().level().dimension() == LEVEL && !event.getPlayer().isCreative()) {
            event.setCanceled(true);
        }
    }

    public static void placing(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && p.level().dimension() == LEVEL && !p.isCreative()) {
            event.setCanceled(true);
        }
    }
}
