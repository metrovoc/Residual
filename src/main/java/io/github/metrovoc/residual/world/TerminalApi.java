package io.github.metrovoc.residual.world;

import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.agent.Agent;
import io.github.metrovoc.residual.agent.Program;
import io.github.metrovoc.residual.block.CoreBlockEntity;
import io.github.metrovoc.residual.block.FabricatorBlockEntity;
import io.github.metrovoc.residual.block.ServerBlockEntity;
import io.github.metrovoc.residual.block.SurveyorBlockEntity;
import io.github.metrovoc.residual.energy.CellBlockEntity;
import io.github.metrovoc.residual.energy.GeneratorBlockEntity;
import io.github.metrovoc.residual.energy.PowerBlockEntity;
import io.github.metrovoc.residual.item.ProgramCardItem;
import io.github.metrovoc.residual.item.TerminalItem;
import io.github.metrovoc.residual.model.ModelLink;
import io.github.metrovoc.residual.net.RelaySync;
import io.github.metrovoc.residual.net.TerminalAgentsSync;
import io.github.metrovoc.residual.net.TerminalBaseSync;
import io.github.metrovoc.residual.net.TerminalCall;
import io.github.metrovoc.residual.registry.ModEntities;
import io.github.metrovoc.residual.storage.PortBlockEntity;
import io.github.metrovoc.residual.storage.RackBlockEntity;
import io.github.metrovoc.residual.storage.StorageNetwork;
import io.github.metrovoc.residual.storage.TerminalBlockEntity;
import io.github.metrovoc.residual.storage.TerminalHost;
import io.github.metrovoc.residual.storage.TerminalMenu;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * What a Terminal can ask of the base from anywhere its relays reach, each answered when its turn at the Model Server
 * comes: what the base holds, what its machines are doing, what its agents are doing, and telling them what to do. What
 * only a loaded chunk can say is read from there; a relay on a live network keeps the chunks around it loaded.
 */
public final class TerminalApi {
    /** How far around each part of a network, in chunks, a call looks for machines. */
    private static final int LOOKS = 8;
    /** The most machines a call brings back. */
    private static final int MOST = 256;
    /** How far from a gate a player can use it, as with any block's screen. */
    private static final double GATE_REACH = 8;

    private TerminalApi() {
    }

    public static void call(ServerPlayer player, TerminalCall call) {
        MinecraftServer server = player.level().getServer();
        switch (call.kind()) {
            case MAP -> PacketDistributor.sendToPlayer(player, RelaySync.of(Relays.of(server), true));
            case GATE_JUMP -> {
                GlobalPos from = call.where().orElse(null), to = place(call.text());
                if (from != null && to != null && near(player, from)) {
                    Gates.start(player, from, to);
                }
            }
            case GATE_NAME -> {
                GlobalPos gate = call.where().orElse(null);
                if (gate != null && near(player, gate) && call.text().length() <= 32) {
                    Relays relays = Relays.of(server);
                    if (relays.part(gate) != null && relays.part(gate).kind() == Relays.Kind.GATE) {
                        relays.rename(gate, call.text().strip());
                        relays.tell(player);
                    }
                }
            }
            default -> {
                ItemStack terminal = TerminalItem.held(player);
                if (terminal == null) {
                    return;
                }
                ModelLink link = ModelLink.of(player.level(), player.blockPosition(), terminal);
                if (!(link instanceof ModelLink.Served served)) {
                    fail(player, call, link instanceof ModelLink.Missing m ? m.problem() : "residual.model.none");
                    return;
                }
                long asked = player.level().getGameTime();
                served.server().call(turn -> {
                    int waited = (int) (player.level().getGameTime() - asked);
                    if (!player.isAlive() || player.hasDisconnected()) {
                        return;
                    }
                    if (!turn) {
                        fail(player, call, "residual.terminal.busy");
                    } else {
                        answer(player, call, served, waited);
                    }
                });
            }
        }
    }

    /** {@code dimension;x;y;z} back into a place. */
    @Nullable
    public static GlobalPos place(String text) {
        String[] parts = text.split(";");
        if (parts.length != 4) {
            return null;
        }
        try {
            ResourceKey<Level> dimension = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, net.minecraft.resources.Identifier.parse(parts[0]));
            return GlobalPos.of(dimension, new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static String place(GlobalPos at) {
        return at.dimension().identifier() + ";" + at.pos().getX() + ";" + at.pos().getY() + ";" + at.pos().getZ();
    }

    private static boolean near(ServerPlayer player, GlobalPos gate) {
        return player.level().dimension().equals(gate.dimension()) && player.distanceToSqr(gate.pos().getCenter()) < GATE_REACH * GATE_REACH;
    }

    private static void fail(ServerPlayer player, TerminalCall call, String problem) {
        switch (call.kind()) {
            case BASE, STORAGE -> PacketDistributor.sendToPlayer(player, TerminalBaseSync.failed(problem));
            case AGENTS, CARD, STOP, RUN, ORDER, ANSWER -> PacketDistributor.sendToPlayer(player, new TerminalAgentsSync(0, problem, List.of()));
            default -> player.sendOverlayMessage(Component.translatable(problem));
        }
    }

    private static void answer(ServerPlayer player, TerminalCall call, ModelLink.Served served, int waited) {
        MinecraftServer server = player.level().getServer();
        Relays relays = Relays.of(server);
        Relays.Net net = relays.netOf(served.where());
        if (net == null) {
            fail(player, call, "residual.model.no_server");
            return;
        }
        switch (call.kind()) {
            case BASE -> PacketDistributor.sendToPlayer(player, base(server, net, waited));
            case AGENTS -> PacketDistributor.sendToPlayer(player, agents(player, net, waited));
            case CARD, STOP, RUN, ORDER, ANSWER -> {
                Agent agent = call.agent().map(id -> agent(server, net, id)).orElse(null);
                if (agent != null && agent.ownership().check(player)) {
                    tell(player, call, agent, served);
                }
                PacketDistributor.sendToPlayer(player, agents(player, net, waited));
            }
            case STORAGE -> call.where().ifPresent(at -> storage(player, net, at));
            case JUMP -> call.where().ifPresent(to -> Gates.start(player, null, to));
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- steering

    private static void tell(ServerPlayer player, TerminalCall call, Agent agent, ModelLink.Served served) {
        switch (call.kind()) {
            case CARD -> {
                if (call.number() >= 0 && call.number() < player.getInventory().getContainerSize()) {
                    String program = ProgramCardItem.program(player.getInventory().getItem(call.number()));
                    if (program.isEmpty() || agent.setProgram(program, player) != null) {
                        player.sendOverlayMessage(Component.translatable("residual.card.unreadable"));
                    } else {
                        afar(player, agent);
                    }
                }
            }
            case STOP -> agent.stop();
            case RUN -> {
                agent.run();
                afar(player, agent);
            }
            case ORDER -> {
                // a program written out needs no model to write it
                Program.Parsed written = Program.parse(call.text());
                if (written.ok() && !written.program().steps().isEmpty()) {
                    agent.carryOut(player, written.program());
                } else if (!call.text().isBlank()) {
                    agent.order(player, call.text().strip(), served);
                }
            }
            case ANSWER -> agent.answer(player, call.number() == 1);
            default -> {
            }
        }
    }

    /** Sets something going far from the base, as the Terminal is meant for. */
    private static void afar(ServerPlayer player, Agent agent) {
        if (player.level() != agent.level() || player.distanceToSqr(agent) > 64 * 64) {
            Awards.grant(player, "deploy/api");
        }
    }

    // ---------------------------------------------------------------- reading

    @Nullable
    private static Agent agent(MinecraftServer server, Relays.Net net, UUID id) {
        for (ResourceKey<Level> dimension : dimensions(net)) {
            ServerLevel level = server.getLevel(dimension);
            if (level != null && level.getEntity(id) instanceof Agent a && net.covers(dimension, a.blockPosition())) {
                return a;
            }
        }
        return null;
    }

    private static Set<ResourceKey<Level>> dimensions(Relays.Net net) {
        Set<ResourceKey<Level>> out = new HashSet<>();
        net.parts.forEach(p -> out.add(p.at().dimension()));
        return out;
    }

    /** The agents the network reaches that {@code player} may use. */
    private static TerminalAgentsSync agents(ServerPlayer player, Relays.Net net, int waited) {
        List<TerminalAgentsSync.Row> rows = new ArrayList<>();
        for (ResourceKey<Level> dimension : dimensions(net)) {
            ServerLevel level = player.level().getServer().getLevel(dimension);
            if (level == null) {
                continue;
            }
            for (Agent a : level.getEntities(ModEntities.AGENT.get(), a -> net.covers(dimension, a.blockPosition()) && a.ownership().allows(player))) {
                rows.add(new TerminalAgentsSync.Row(a.getUUID(), a.getName().getString(), GlobalPos.of(dimension, a.blockPosition()), a.program(),
                        a.stopped(), a.statusKey(), a.statusArgs(), a.proposed()));
            }
        }
        rows.sort(Comparator.comparing(TerminalAgentsSync.Row::name, String.CASE_INSENSITIVE_ORDER).thenComparing(TerminalAgentsSync.Row::id));
        return new TerminalAgentsSync(waited, "", rows);
    }

    private static TerminalBaseSync base(MinecraftServer server, Relays.Net net, int waited) {
        List<TerminalBaseSync.Storage> storage = new ArrayList<>();
        List<TerminalBaseSync.Machine> machines = new ArrayList<>();
        Set<StorageNetwork> counted = new HashSet<>();
        for (ResourceKey<Level> dimension : dimensions(net)) {
            ServerLevel level = server.getLevel(dimension);
            if (level == null) {
                continue;
            }
            LongSet looked = new LongOpenHashSet();
            for (Relays.Part p : net.parts) {
                if (!p.at().dimension().equals(dimension)) {
                    continue;
                }
                ChunkPos c = ChunkPos.containing(p.at().pos());
                int radius = Math.min(LOOKS, (p.reach() >> 4) + 1);
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        LevelChunk chunk = looked.add(ChunkPos.pack(c.x() + dx, c.z() + dz)) ? level.getChunkSource().getChunkNow(c.x() + dx, c.z() + dz) : null;
                        if (chunk == null) {
                            continue;
                        }
                        for (BlockEntity be : chunk.getBlockEntities().values()) {
                            if (!net.covers(dimension, be.getBlockPos())) {
                                continue;
                            }
                            if (be instanceof TerminalBlockEntity) {
                                StorageNetwork found = StorageNetwork.at(level, be.getBlockPos());
                                if (counted.add(found)) {
                                    long[] usage = found.usage();
                                    storage.add(new TerminalBaseSync.Storage(GlobalPos.of(dimension, be.getBlockPos()), usage[0], usage[1], found.contents().size()));
                                }
                            }
                            TerminalBaseSync.Machine m = machines.size() < MOST ? describe(dimension, be) : null;
                            if (m != null) {
                                machines.add(m);
                            }
                        }
                    }
                }
            }
        }
        // the ones at work first, then waiting, then idle, each kept together by kind
        machines.sort(Comparator.comparingInt((TerminalBaseSync.Machine m) -> m.state() == TerminalBaseSync.WORKING ? 0 : m.state() == TerminalBaseSync.WAITING ? 1 : 2)
                .thenComparing(m -> m.icon().getItem().getDescriptionId()));
        return new TerminalBaseSync(waited, "", storage, machines);
    }

    /** One line about a machine, read from the block itself: what its screen would show first. Null for what is no machine. */
    @Nullable
    private static TerminalBaseSync.Machine describe(ResourceKey<Level> dimension, BlockEntity be) {
        BlockState state = be.getBlockState();
        if (be instanceof AbstractFurnaceBlockEntity) {
            boolean burning = state.getValue(AbstractFurnaceBlock.LIT);
            return new TerminalBaseSync.Machine(new ItemStack(state.getBlock().asItem()), GlobalPos.of(dimension, be.getBlockPos()),
                    burning ? TerminalBaseSync.WORKING : TerminalBaseSync.IDLE, Component.translatable(burning ? "residual.base.working" : "residual.base.idle"));
        }
        if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(Residual.MOD_ID)) {
            return null;
        }
        boolean working = lit(state);
        Component doing = null;
        int kind = working ? TerminalBaseSync.WORKING : TerminalBaseSync.IDLE;
        switch (be) {
            case ServerBlockEntity s -> {
                doing = s.describe();
                // questions waiting: it is busy, and whoever asks waits behind them
                kind = s.waiting() > 0 ? TerminalBaseSync.WAITING : kind;
            }
            case SurveyorBlockEntity s -> doing = Component.translatable("residual.base.found", s.shown().size());
            case CoreBlockEntity c -> {
                if (c.running()) {
                    doing = Component.translatable("residual.base.training", String.format(Locale.ROOT, "%,d", c.steps()), String.format(Locale.ROOT, "%.3f", c.trainLoss()));
                    kind = TerminalBaseSync.WORKING;
                } else {
                    doing = Component.translatable("residual.base.not_training");
                }
            }
            case FabricatorBlockEntity f -> doing = f.progress() == 0 ? Component.translatable("residual.base.idle")
                    : Component.translatable("residual.base.making", 100 * f.progress() / Math.max(1, f.total()));
            case CellBlockEntity cell -> doing = Component.translatable("residual.base.cell", String.format(Locale.ROOT, "%,d", cell.energy().getAmountAsLong()),
                    String.format(Locale.ROOT, "%,d", cell.energy().getCapacityAsLong()));
            case GeneratorBlockEntity g -> doing = Component.translatable(working ? "residual.base.burning" : "residual.base.no_fuel", String.format(Locale.ROOT, "%,d", g.energy().getAmountAsLong()));
            case PowerBlockEntity machine -> doing = Component.translatable(working ? "residual.base.working_on" : "residual.base.idle_on", String.format(Locale.ROOT, "%,d", machine.energy().getAmountAsLong()));
            case PortBlockEntity port -> doing = Component.translatable("residual.port.mode." + port.mode().id);
            case RackBlockEntity rack -> {
                long[] used = new long[2];
                rack.usage(used);
                doing = Component.translatable("residual.base.rack", String.format(Locale.ROOT, "%,d", used[0]), String.format(Locale.ROOT, "%,d", used[1]));
            }
            default -> {
                // machines with a light for working, and only those
                if (!hasLight(state)) {
                    return null;
                }
                doing = Component.translatable(working ? "residual.base.working" : "residual.base.idle");
            }
        }
        return new TerminalBaseSync.Machine(new ItemStack(state.getBlock().asItem()), GlobalPos.of(dimension, be.getBlockPos()), kind, doing);
    }

    /** The properties blocks light by while they work. */
    private static boolean lit(BlockState state) {
        for (Property<?> p : state.getProperties()) {
            if (p instanceof BooleanProperty b && isLight(p.getName()) && state.getValue(b)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLight(BlockState state) {
        return state.getProperties().stream().anyMatch(p -> p instanceof BooleanProperty && isLight(p.getName()));
    }

    private static boolean isLight(String name) {
        return switch (name) {
            case "working", "active", "running", "reading", "serving" -> true;
            default -> false;
        };
    }

    // ---------------------------------------------------------------- the storage network from afar

    /** A Storage Terminal's screen worked from afar: all of it the terminal's own, but for how the player reaches it. */
    private record Afar(TerminalBlockEntity terminal, ServerPlayer player, GlobalPos at) implements TerminalHost {
        @Override
        public StorageNetwork network() {
            return terminal.network();
        }

        @Override
        public List<ItemResource> pins() {
            return terminal.pins();
        }

        @Override
        public void pin(ItemResource kind) {
            terminal.pin(kind);
        }

        @Override
        public Container searchModel() {
            return terminal.searchModel();
        }

        @Override
        public boolean reaches(Player who) {
            return who == player && !terminal.isRemoved() && TerminalItem.reaches(player, at);
        }
    }

    private static void storage(ServerPlayer player, Relays.Net net, GlobalPos at) {
        ServerLevel level = player.level().getServer().getLevel(at.dimension());
        if (level == null || !net.covers(at.dimension(), at.pos()) || !level.isLoaded(at.pos()) || !(level.getBlockEntity(at.pos()) instanceof TerminalBlockEntity terminal)) {
            return;
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new TerminalMenu(id, inventory, at.pos(), new Afar(terminal, player, at)),
                terminal.getDisplayName()), buf -> buf.writeBlockPos(at.pos()));
        if (player.level() != level || player.distanceToSqr(at.pos().getCenter()) > 64 * 64) {
            Awards.grant(player, "deploy/api");
        }
    }
}
