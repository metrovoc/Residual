package io.github.metrovoc.residual.rehearsal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.agent.Agent;
import io.github.metrovoc.residual.agent.Trace;
import io.github.metrovoc.residual.block.ArenaBlockEntity;
import io.github.metrovoc.residual.block.CoreBlockEntity;
import io.github.metrovoc.residual.block.ForecasterBlockEntity;
import io.github.metrovoc.residual.block.UnitBlock;
import io.github.metrovoc.residual.block.ServerBlock;
import io.github.metrovoc.residual.block.ServerBlockEntity;
import io.github.metrovoc.residual.block.TunerBlockEntity;
import io.github.metrovoc.residual.block.InstrumentBlockEntity;
import io.github.metrovoc.residual.block.WatchPostBlockEntity;
import io.github.metrovoc.residual.item.CrystalItem;
import io.github.metrovoc.residual.item.MobLogItem;
import io.github.metrovoc.residual.net.TunerSync;
import io.github.metrovoc.residual.net.ServerSync;
import io.github.metrovoc.residual.energy.SmelterBlockEntity;
import io.github.metrovoc.residual.energy.PowerNetwork;
import io.github.metrovoc.residual.energy.ReactorBlockEntity;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Imitation;
import io.github.metrovoc.residual.learn.Reckoning;
import io.github.metrovoc.residual.learn.Sampler;
import io.github.metrovoc.residual.compute.ChillerBlockEntity;
import io.github.metrovoc.residual.compute.Cluster;
import io.github.metrovoc.residual.registry.ModEntities;
import io.github.metrovoc.residual.refine.RefineryBlockEntity;
import io.github.metrovoc.residual.refine.TokenSupply;
import io.github.metrovoc.residual.scope.Records;
import io.github.metrovoc.residual.storage.CraftingData;
import io.github.metrovoc.residual.storage.DockBlockEntity;
import io.github.metrovoc.residual.storage.PortBlockEntity;
import io.github.metrovoc.residual.storage.StorageNetwork;
import io.github.metrovoc.residual.world.Relays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.slf4j.Logger;

/** Server-side rehearsal operations and assertions, with an acknowledged reply for the driving client. */
@EventBusSubscriber(modid = Residual.MOD_ID)
public final class ServerVerify {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Map<String, Vec3> WALKED = new HashMap<>();
    private static final Map<String, Set<UUID>> SIGNALS = new HashMap<>();
    private static final Set<String> EXACT = Set.of("jobs", "nomobs", "noblocks", "empty", "netopen", "relaycapacity", "hops", "state", "ticking",
            "running", "working", "racks", "hall", "carries", "status", "rods", "closed", "access", "operator", "menu", "distinct", "reactortrips", "devicecount");

    private ServerVerify() {
    }

    @SubscribeEvent
    static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("rehearsal_verify")
                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .then(Commands.argument("operation", StringArgumentType.greedyString()).executes(c -> {
                            ServerPlayer player = c.getSource().getPlayerOrException();
                            int id = IntegerArgumentType.getInteger(c, "id");
                            String operation = StringArgumentType.getString(c, "operation");
                            String reply;
                            try {
                                String[] a = operation.split("\\s+");
                                // A second player must be able to verify refusal while genuinely lacking operator privileges.
                                if (!a[0].equals("expect") && !a[0].equals("signal")
                                        && !player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                                    throw new IllegalStateException("operator privileges required for " + a[0]);
                                }
                                run(player, a);
                                reply = "OK";
                            } catch (Exception e) {
                                if (e instanceof IllegalStateException) {
                                    LOG.info("[rehearsal] server check: {}", e.getMessage());
                                } else {
                                    LOG.error("[rehearsal] server operation failed: {}", operation, e);
                                }
                                reply = "FAIL " + e.getClass().getSimpleName() + ": " + e.getMessage();
                            }
                            player.sendSystemMessage(Component.literal("[rehearsal-server " + id + "] " + reply));
                            return reply.equals("OK") ? 1 : 0;
                        }))));
    }

    private static void run(ServerPlayer player, String[] words) throws Exception {
        String[] a = java.util.Arrays.copyOfRange(words, 1, words.length);
        ServerLevel level = player.level();
        switch (words[0]) {
            case "expect" -> check(player, a);
            case "forecast" -> ForecasterVerify.run(player, a);
            case "reactor" -> ReactorVerify.run(player, a);
            case "energy" -> EnergyVerify.run(player, a);
            case "signal" -> SIGNALS.computeIfAbsent(a[0], key -> new HashSet<>()).add(player.getUUID());
            case "blueprint" -> ServerSteps.blueprint(level, a[0], at(a, 1), Direction.byName(a[4]));
            case "drive" -> ServerSteps.drive(player, Integer.parseInt(a[0]));
            case "stock" -> StorageNetwork.at(level, at(a, 0)).insert(kind(a[3]), Long.parseLong(a[4]), false);
            case "kinds" -> {
                StorageNetwork net = StorageNetwork.at(level, at(a, 0));
                for (int i = 0; i < Integer.parseInt(a[3]); i++) {
                    ItemStack stack = new ItemStack(Items.PAPER);
                    stack.set(DataComponents.CUSTOM_NAME, Component.literal("Kind " + i));
                    net.insert(ItemResource.of(stack), 1, false);
                }
            }
            case "mine" -> {
                if (!player.gameMode.destroyBlock(at(a, 0))) {
                    throw new IllegalStateException("could not mine " + at(a, 0));
                }
            }
            case "ask", "sums" -> {
                ServerBlockEntity server = (ServerBlockEntity) level.getBlockEntity(at(a, 0));
                Random rng = new Random(1);
                int tokens = a.length > 4 ? Integer.parseInt(a[4]) : 16;
                for (int i = 0; i < Integer.parseInt(a[3]); i++) {
                    if (words[0].equals("sums")) {
                        server.ask(Reckoning.prompt(Reckoning.sum(rng, true)), Reckoning.WRITTEN, '\n', answer -> {});
                    } else {
                        String prompt = "> question " + (i % 40) + "\n= ";
                        server.submit(model -> Sampler.answer(model, prompt, tokens, (char) 1), answer -> {});
                    }
                }
            }
            case "trace" -> {
                Agent agent = a[0].equals("agent") ? nearest(player) : null;
                List<Trace> trace = a[0].equals("agent") ? agent == null ? List.of() : List.copyOf(agent.traced())
                        : List.copyOf(((PortBlockEntity) level.getBlockEntity(at(a, 0))).work.traced(Integer.parseInt(a[3])));
                for (Trace t : trace) {
                    LOG.info("[rehearsal] trace: {} {} {} {} x{}", t.kind(), t.step(), t.key(), t.args(), t.times());
                }
            }
            case "reset" -> reset(player, a);
            default -> throw new IllegalArgumentException("unknown server operation " + words[0]);
        }
    }

    /** Restores a bounded test area in the flat overworld, or empties a bounded fixture in another dimension. */
    private static void reset(ServerPlayer player, String[] a) {
        ServerLevel level = player.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(a[0])));
        BlockPos low = at(a, 1), high = at(a, 4);
        boolean flat = a[0].equals("minecraft:overworld");
        int changed = 0;
        for (BlockPos pos : BlockPos.betweenClosed(low, high)) {
            BlockState wanted = (flat && pos.getY() == -64 ? Blocks.BEDROCK
                    : flat && (pos.getY() == -63 || pos.getY() == -62) ? Blocks.DIRT
                    : flat && pos.getY() == -61 ? Blocks.GRASS_BLOCK : Blocks.AIR).defaultBlockState();
            if (level.getBlockState(pos) != wanted) {
                level.setBlock(pos, wanted, Block.UPDATE_ALL);
                changed++;
            }
        }
        LOG.info("[rehearsal] reset {} {} to {}: {} blocks", a[0], low, high, changed);
    }

    private static BlockPos at(String[] args, int start) {
        return new BlockPos(Integer.parseInt(args[start]), Integer.parseInt(args[start + 1]), Integer.parseInt(args[start + 2]));
    }

    private static ItemResource kind(String id) {
        return ItemResource.of(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)));
    }

    private static Agent nearest(ServerPlayer player) {
        return player.level().getNearestEntity(Agent.class, TargetingConditions.forNonCombat().ignoreLineOfSight().ignoreInvisibilityTesting(),
                player, player.getX(), player.getY(), player.getZ(), player.getBoundingBox().inflate(256));
    }

    private static double parse(String shown) {
        try {
            return Double.parseDouble(shown);
        } catch (NumberFormatException e) {
            return shown.equals("true") ? 1 : -1;
        }
    }

    private static void check(ServerPlayer player, String[] args) {
        long wanted = Long.parseLong(args[args.length - 1]), found = read(player, args);
        boolean exact = EXACT.contains(args[0]) || wanted == 0 && (args[0].equals("program") || args[0].equals("proposed"));
        if (exact ? found != wanted : args[0].equals("fewer") ? found > wanted : found < wanted) {
            throw new IllegalStateException("expected " + String.join(" ", args) + ", found " + found);
        }
        LOG.info("[rehearsal] expected {}, found {}", String.join(" ", args), found);
    }

    private static long read(ServerPlayer player, String[] args) {
        MinecraftServer server = player.level().getServer();
        ServerLevel level = player.level();
        switch (args[0]) {
            case "signal": return SIGNALS.getOrDefault(args[1], Set.of()).size();
            case "players": return server.getPlayerList().getPlayerCount();
            case "distinct": return !server.getPlayerList().getPlayerByName(args[1]).getUUID()
                    .equals(server.getPlayerList().getPlayerByName(args[2]).getUUID()) ? 1 : 0;
            case "operator": return server.getPlayerList().getPlayerByName(args[1]).permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) ? 1 : 0;
            case "menu": return player.containerMenu.getClass().getSimpleName().equals(args[1]) ? 1 : 0;
            case "moblog": {
                long looks = 0;
                for (ItemStack stack : player.getInventory()) {
                    MobLogItem.Log log = MobLogItem.log(stack);
                    looks += log == null ? 0 : Imitation.lines(log.lines()).stream().mapToInt(l -> (l.length() - 1) / 2).sum();
                }
                return looks;
            }
            case "earned", "made": {
                AdvancementHolder holder = server.getAdvancements().get(Residual.id(args[1]));
                return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone() ? 1 : 0;
            }
            case "asking": {
                Agent agent = nearest(player);
                return agent != null && agent.proposed().contains(args[1]) ? 1 : 0;
            }
            case "agentvar": {
                Agent agent = nearest(player);
                return agent == null ? -1 : (long) Math.floor(parse(agent.variable(args[1])));
            }
            case "program", "proposed": {
                for (Agent agent : level.getEntities(ModEntities.AGENT.get(), agent -> agent.getName().getString().equals(args[1]))) {
                    return args[0].equals("proposed") ? agent.proposed().length() : agent.stopped() ? 0 : agent.program().length();
                }
                return -1;
            }
            case "walked": {
                for (ServerLevel world : server.getAllLevels()) {
                    for (Agent agent : world.getEntities(ModEntities.AGENT.get(), agent -> agent.getName().getString().equals(args[1]))) {
                        Vec3 before = WALKED.put(player.getUUID() + ":" + args[1], agent.position());
                        return before == null ? 0 : Math.round(agent.position().distanceTo(before));
                    }
                }
                return -1;
            }
            case "hops": return Relays.of(server).hops(GlobalPos.of(level.dimension(), at(args, 1)), GlobalPos.of(level.dimension(), at(args, 4)));
            case "board": return Records.of(server).board(Benchmark.byId(args[1])).size();
            case "boardplayer": return Records.of(server).board(Benchmark.byId(args[1])).stream().filter(e -> e.player().equals(args[2])).count();
            case "designs": return Records.of(server).designs(args[1]).size();
            case "designplayer": return Records.of(server).designs(args[1]).stream().filter(e -> e.player().equals(args[2])).count();
            case "kept": return CrystalItem.model(((InstrumentBlockEntity) level.getBlockEntity(at(args, 1))).items().getItem(InstrumentBlockEntity.OUTPUT)) != null ? 1 : 0;
            case "access": return ((PortBlockEntity) level.getBlockEntity(at(args, 1))).work.ownership().allows(server.getPlayerList().getPlayerByName(args[4])) ? 1 : 0;
            default: break;
        }
        BlockPos at = at(args, 1);
        if (args[0].equals("steps")) {
            return (long) ((CoreBlockEntity) level.getBlockEntity(at)).snapshot().steps();
        }
        if (args[0].equals("running")) {
            return ((CoreBlockEntity) level.getBlockEntity(at)).running() ? 1L : 0L;
        }
        if (args[0].equals("working")) {
            return level.getBlockState(at).getValue(UnitBlock.WORKING) ? 1L : 0L;
        }
        StorageNetwork net = StorageNetwork.at(level, at);
        switch (args[0]) {
            case "jobs" -> {
                return (long) CraftingData.of(server).on(level, net).size();
            }
            case "capacity" -> {
                return net.usage()[1];
            }
            case "nodes" -> {
                return (long) net.nodes.size();
            }
            case "kinds" -> {
                return (long) net.contents().size() + net.fluids().size();
            }
            case "linkrate" -> {
                return (long) net.linkRate();
            }
            case "moved" -> {
                return level.getBlockEntity(at) instanceof DockBlockEntity dock ? (long) dock.moved()
                        : level.getBlockEntity(at) instanceof PortBlockEntity port ? (long) port.moved() : 0L;
            }
            case "fluid" -> {
                return net.amount(FluidResource.of(BuiltInRegistries.FLUID.getValue(Identifier.parse(args[4]))));
            }
            case "tank" -> {
                FluidResource fluid = FluidResource.of(BuiltInRegistries.FLUID.getValue(Identifier.parse(args[4])));
                ResourceHandler<FluidResource> tank = level.getCapability(Capabilities.Fluid.BLOCK, at, null);
                long held = 0;
                for (int slot = 0; tank != null && slot < tank.size(); slot++) {
                    held += tank.getResource(slot).equals(fluid) ? tank.getAmountAsLong(slot) : 0;
                }
                return held;
            }
            default -> {
            }
        }
        if (args[0].equals("server")) {
            return serverSays((ServerBlockEntity) level.getBlockEntity(at), args[4]);
        }
        if (args[0].equals("smelter")) {
            SmelterBlockEntity be = (SmelterBlockEntity) level.getBlockEntity(at);
            return switch (args[4]) {
                case "gave" -> (long) be.lastGave();
                case "assessed" -> (long) be.assessed();
                case "assayed" -> (long) be.assayed();
                default -> throw new IllegalArgumentException(args[4]);
            };
        }
        if (args[0].equals("tuner")) {
            TunerBlockEntity be = (TunerBlockEntity) level.getBlockEntity(at);
            TunerSync s = be.sync(0);
            return switch (args[4]) {
                case "running" -> s.running() ? 1L : 0L;
                case "steps" -> (long) s.steps();
                case "saved" -> CrystalItem.model(be.items().getItem(TunerBlockEntity.OUT)) != null ? 1L : 0L;
                case "choices" -> (long) s.choices();
                case "waiting" -> s.first().isEmpty() ? 0L : 1L;
                default -> throw new IllegalArgumentException(args[4]);
            };
        }
        if (args[0].equals("watching")) {
            return (long) ((WatchPostBlockEntity) level.getBlockEntity(at)).rows().size();
        }
        if (args[0].equals("wrong")) {
            return ((WatchPostBlockEntity) level.getBlockEntity(at)).rows().stream().filter(r -> r.verdict() != WatchPostBlockEntity.FINE).count();
        }
        if (args[0].equals("spent")) {
            return (long) ForecasterBlockEntity.POWER - ((ForecasterBlockEntity) level.getBlockEntity(at)).energy().getAmountAsInt();
        }
        if (args[0].equals("tier")) {
            ArenaBlockEntity arena = (ArenaBlockEntity) level.getBlockEntity(at);
            LOG.info("[rehearsal] the Arena's check: {}", arena.check());
            return (long) arena.tier();
        }
        if (args[0].equals("fights") || args[0].equals("fewer") || args[0].equals("closed")) {
            ArenaBlockEntity arena = (ArenaBlockEntity) level.getBlockEntity(at);
            LOG.info("[rehearsal] the Arena's state: {}, on {} compute", arena.state(), arena.compute());
            return args[0].equals("closed") ? (arena.state() == ArenaBlockEntity.State.CLOSED ? 1L : 0L) : (long) arena.running();
        }
        if (args[0].equals("refined") || args[0].equals("tokens")) {
            TokenSupply held = ((RefineryBlockEntity) level.getBlockEntity(at)).supply();
            return held == null ? 0L : args[0].equals("refined") ? held.characters() : held.tokens();
        }
        if (args[0].equals("measured")) {
            return Float.isNaN(((RefineryBlockEntity) level.getBlockEntity(at)).delta()) ? 0L : 1L;
        }
        if (args[0].equals("shows")) {
            return (long) ((RefineryBlockEntity) level.getBlockEntity(at)).rows().size();
        }
        if (args[0].equals("judged")) {
            return (long) ((RefineryBlockEntity) level.getBlockEntity(at)).judged();
        }
        if (args[0].equals("trained")) {
            return (long) ((CoreBlockEntity) level.getBlockEntity(at)).snapshot().steps();
        }
        if (args[0].equals("port")) {
            return (long) ((PortBlockEntity) level.getBlockEntity(at)).expects();
        }
        if (args[0].equals("moved")) {
            return level.getBlockEntity(at) instanceof PortBlockEntity port ? (long) port.movedLastSecond() : -1L;
        }
        if (args[0].equals("energy")) {
            var h = level.getCapability(Capabilities.Energy.BLOCK, at, null);
            return h == null ? -1L : h.getAmountAsLong();
        }
        if (args[0].equals("fluid")) {
            var h = level.getCapability(Capabilities.Fluid.BLOCK, at, null);
            long n = 0;
            for (int slot = 0; h != null && slot < h.size(); slot++) {
                n += h.getAmountAsLong(slot);
            }
            return n;
        }
        if (args[0].equals("held") || args[0].equals("breaks") || args[0].equals("watches")) {
            var debug = level.getBlockEntity(at) instanceof PortBlockEntity port ? port.work.debugOf(Integer.parseInt(args[4])) : null;
            return debug == null ? -1L : args[0].equals("held") ? (debug.paused ? 1L : 0L) : args[0].equals("breaks") ? debug.breaks.size() : debug.watches.size();
        }
        if (args[0].equals("var")) {
            return level.getBlockEntity(at) instanceof PortBlockEntity port ? (long) Math.floor(parse(port.work.variable(Integer.parseInt(args[4]), args[5]))) : -1L;
        }
        if (args[0].equals("empty")) {
            ResourceHandler<ItemResource> inv = level.getCapability(Capabilities.Item.BLOCK, at, null);
            long n = 0;
            for (int slot = 0; inv != null && slot < inv.size(); slot++) {
                n += inv.getAmountAsLong(slot);
            }
            return n;
        }
        if (args[0].equals("mobs") || args[0].equals("nomobs")) {
            var type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(args[5]));
            return (long) level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(at).inflate(Integer.parseInt(args[4])), e -> e.getType() == type).size();
        }
        if (args[0].equals("placed") || args[0].equals("noblocks")) {
            BlockPos to = new BlockPos(Integer.parseInt(args[4]), Integer.parseInt(args[5]), Integer.parseInt(args[6]));
            Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(args[7]));
            long n = 0;
            for (BlockPos q : BlockPos.betweenClosed(at, to)) {
                n += level.getBlockState(q).is(block) ? 1 : 0;
            }
            return n;
        }
        GlobalPos place = GlobalPos.of(level.dimension(), at);
        if (args[0].equals("reach")) {
            return Relays.of(server).part(place) == null ? -1L : (long) Relays.of(server).part(place).reach();
        }
        if (args[0].equals("relaycapacity")) {
            Relays.Net relayNet = Relays.of(server).netOf(place);
            return relayNet == null ? -1L : (long) relayNet.capacity();
        }
        if (args[0].equals("netopen")) {
            return Relays.of(server).netOf(place) != null && Relays.of(server).netOf(place).open() ? 1L : 0L;
        }
        if (args[0].equals("state")) {
            String[] kv = args[4].split("=");
            for (var e : level.getBlockState(at).getValues().toList()) {
                if (e.property().getName().equals(kv[0])) {
                    return e.valueName().equals(kv[1]) ? 1L : 0L;
                }
            }
            return -1L;
        }
        if (args[0].equals("ticking")) {
            return level.shouldTickBlocksAt(at) ? 1L : 0L;
        }
        if (args[0].equals("carries")) {
            return (long) PowerNetwork.at(level, at).carries();
        }
        if (args[0].equals("devices") || args[0].equals("devicecount")) {
            return (long) Cluster.at(level, at).devices().size();
        }
        if (args[0].equals("racks")) {
            return (long) Cluster.at(level, at).racks().size();
        }
        if (args[0].equals("hall")) {
            return Cluster.at(level, at).hall() ? 1L : 0L;
        }
        if (args[0].equals("flops")) {
            return ((CoreBlockEntity) level.getBlockEntity(at)).meter().flops();
        }
        if (args[0].equals("paid")) {
            return ((CoreBlockEntity) level.getBlockEntity(at)).meter().energy();
        }
        if (args[0].equals("chilled")) {
            return ((ChillerBlockEntity) level.getBlockEntity(at)).spentSoFar();
        }
        if (args[0].equals("making")) {
            return (long) ((ReactorBlockEntity) level.getBlockEntity(at)).rate();
        }
        if (args[0].equals("powerstored")) {
            return PowerNetwork.at(level, at).stored()[0];
        }
        if (args[0].equals("reactorcycles") || args[0].equals("reactorages") || args[0].equals("reactortrips")) {
            return ReactorVerify.read(level, at, args[0]);
        }
        if (args[0].equals("status")) {
            return (long) ((ReactorBlockEntity) level.getBlockEntity(at)).status();
        }
        if (args[0].equals("rods")) {
            return (long) ((ReactorBlockEntity) level.getBlockEntity(at)).rods();
        }
        if (!args[0].equals("net") && !args[0].equals("inv")) {
            throw new IllegalArgumentException("unknown server check " + args[0]);
        }
        ItemResource kind = ItemResource.of(BuiltInRegistries.ITEM.getValue(Identifier.parse(args[4])));
        if (args[0].equals("net")) {
            return StorageNetwork.at(level, at).count(kind);
        }
        ResourceHandler<ItemResource> inv = level.getCapability(Capabilities.Item.BLOCK, at, null);
        long n = 0;
        for (int slot = 0; inv != null && slot < inv.size(); slot++) {
            n += inv.getResource(slot).equals(kind) ? inv.getAmountAsLong(slot) : 0;
        }
        return n;
    }

    private static long serverSays(ServerBlockEntity be, String what) {
        ServerSync s = be.sync(0);
        return switch (what) {
            case "rate" -> (long) s.rate();
            case "answered" -> s.answered();
            case "waiting" -> s.waiting();
            case "turned" -> s.turnedAway();
            case "full" -> be.full() ? 1 : 0;
            case "strained" -> be.getBlockState().getValue(ServerBlock.STRAINED) ? 1 : 0;
            case "weights" -> s.weights();
            case "most" -> s.memory() - s.weights();
            case "saved" -> s.saved();
            case "rows" -> s.rows();
            case "batch" -> be.biggestBatch();
            case "proposed" -> s.proposed();
            case "accepted" -> s.accepted();
            case "passes" -> s.scores().stream().filter(ServerSync.Scored::passes).count();
            case "slow" -> Math.round(100 / Math.max(s.rate(), 0.01));
            default -> throw new IllegalArgumentException(what);
        };
    }

}
