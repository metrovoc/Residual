package io.github.metrovoc.residual.rehearsal;

import com.mojang.logging.LogUtils;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.agent.Agent;
import io.github.metrovoc.residual.block.CoreBlockEntity;
import io.github.metrovoc.residual.block.ArenaBlockEntity;
import io.github.metrovoc.residual.block.ServerBlockEntity;
import io.github.metrovoc.residual.block.TunerBlockEntity;
import io.github.metrovoc.residual.compute.Cluster;
import io.github.metrovoc.residual.compute.Parallelism;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.energy.ReactorBlockEntity;
import io.github.metrovoc.residual.item.CrystalItem;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.registry.ModBlocks;
import io.github.metrovoc.residual.registry.ModItems;
import io.github.metrovoc.residual.storage.PortBlockEntity;
import io.github.metrovoc.residual.world.Relays;
import java.lang.reflect.Field;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * A large base on a dedicated server, for the tick time each release records: {@link #PAIRS} pairs of Ports, one
 * exporting into a chest and one importing from it, so items never stop moving; {@link #AGENTS} agents walking their
 * rounds; {@link #FORECASTERS} Forecasters among working furnaces; a two-block transformer training at full speed; a hall of
 * {@link #PODS} pods, full racks on power and a cooling line, that it trains across with data parallelism; and a relay network of
 * {@link #MASTS} masts, each keeping its chunks loaded, with Ports at its far end whose items all cross a Relay Link. It lets the base
 * settle for a minute, waits for its serving, tuning, Arena and reactor workloads to start, then logs the next minute's
 * tick times and stops the server. Kept models come from {@code residual.models}; {@code residual.stress.moblog} names a
 * recorded Mob Log. {@code residual.stress.reactor=false} explicitly omits the reactor and labels that narrower run.
 */
@EventBusSubscriber(modid = Residual.MOD_ID)
public final class Stress {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int PAIRS = 128, ROW = 16, AGENTS = 256, FORECASTERS = 32, MASTS = 32, FAR_PAIRS = 8;
    private static final int SETTLE = 1200, MEASURE = 1200;
    private static final int PODS = 4, RACKS_A_POD = 8;
    private static final int PROGRAM_PAIRS = 16, READY_LIMIT = 12_000;
    /** The ground of a flat world. */
    private static final int Y = -60;

    private static long[] ticks;
    private static int seen;
    private static final List<PortBlockEntity> PORTS = new ArrayList<>();
    private static final List<PortBlockEntity> PROGRAM_PORTS = new ArrayList<>();
    private static final Map<Agent, Vec3> places = new HashMap<>();
    private static CoreBlockEntity training;
    private static ServerBlockEntity serving;
    private static TunerBlockEntity tuner;
    private static ArenaBlockEntity arena;
    private static ReactorBlockEntity reactor;
    private static int warmTicks, answered, rejected, tuned, tunerSteps, priorTunerSteps;
    private static int arenaTicks, tuningTicks, reactorTicks, trainingTicks;
    private static final int[] reactorStatusTicks = new int[7];
    private static int reactorZeroRateTicks, priorReactorStatus = -1;
    private static int answeredBefore, tunedBefore, tunerStepsBefore, trainingBefore;
    private static long began;

    private Stress() {
    }

    @SubscribeEvent
    static void started(ServerStartedEvent event) {
        if (Boolean.getBoolean("residual.stress")) {
            build(event.getServer());
        }
    }

    private static void command(MinecraftServer server, String command) {
        CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, command);
    }

    private static void build(MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (int cx = -1; cx <= 7; cx++) {
            for (int cz = -1; cz <= 7; cz++) {
                level.setChunkForced(cx, cz, true);
                level.getChunk(cx, cz);
            }
        }
        command(server, "gamerule spawn_mobs false");
        command(server, "tick rate 20");
        // ---- rows of Port pairs along a link, a drive rack on each row
        for (int r = 0; r < PAIRS / ROW; r++) {
            int z = r * 4;
            command(server, "fill 0 %d %d %d %d %d residual:storage_link".formatted(Y, z, ROW * 2 - 1, Y, z));
            command(server, "setblock -1 %d %d residual:drive_rack[facing=north]{drives:{Items:[{Slot:0b,id:\"residual:storage_drive\",count:1}]}}".formatted(Y, z));
            // a rack at each end, so each half of a row's Ports has a link's worth of room to its own
            command(server, "setblock %d %d %d residual:drive_rack[facing=north]{drives:{Items:[{Slot:0b,id:\"residual:storage_drive\",count:1}]}}".formatted(ROW * 2, Y, z));
            for (int i = 0; i < ROW; i++) {
                int x = i * 2;
                command(server, ("setblock %d %d %d residual:port[facing=south,mode=export]"
                        + "{filter:{Items:[{Slot:0b,id:\"minecraft:cobblestone\",count:1}]}}").formatted(x, Y, z + 1));
                command(server, "setblock %d %d %d minecraft:chest[facing=north]".formatted(x, Y, z + 2));
                command(server, "item replace block %d %d %d container.0 with minecraft:cobblestone 64".formatted(x, Y, z + 2));
                command(server, "setblock %d %d %d residual:storage_link".formatted(x + 1, Y, z + 1));
                command(server, "setblock %d %d %d residual:port[facing=west,mode=import]".formatted(x + 1, Y, z + 2));
                if (level.getBlockEntity(new BlockPos(x, Y, z + 1)) instanceof PortBlockEntity export) {
                    PORTS.add(export);
                }
                if (level.getBlockEntity(new BlockPos(x + 1, Y, z + 2)) instanceof PortBlockEntity in) {
                    PORTS.add(in);
                }
            }
        }
        // ---- agents walking their rounds, each between two marks on the ground a dozen blocks apart
        for (int i = 0; i < AGENTS; i++) {
            int x = i % 32 * 3, z = 34 + i / 32 * 3;
            command(server, ("summon residual:agent %d %d %d {home:[I;%d,%d,%d],marks:[[I;%d,%d,%d],[I;%d,%d,%d]],"
                    + "program:\"repeat (go 1, go 2)\"}").formatted(x, Y, z, x, Y, z, x, Y - 1, z, x + 12, Y - 1, z));
        }
        // ---- Forecasters among furnaces at work
        for (int i = 0; i < FORECASTERS; i++) {
            int x = i % 16 * 6, z = 62 + i / 16 * 3;
            command(server, "setblock %d %d %d residual:forecaster[facing=north]".formatted(x + 2, Y, z));
            for (int j : new int[]{0, 1, 3, 4}) {
                command(server, "setblock %d %d %d minecraft:furnace[facing=north]".formatted(x + j, Y, z));
                command(server, "item replace block %d %d %d container.0 with minecraft:raw_iron 64".formatted(x + j, Y, z));
                command(server, "item replace block %d %d %d container.1 with minecraft:coal_block 8".formatted(x + j, Y, z));
            }
        }
        hall(server);
        // ---- a two-block transformer training at full speed, across the hall
        BlockPos core = new BlockPos(0, Y, 72);
        ServerSteps.blueprint(level, "gpt32", core, Direction.EAST);
        command(server, "data merge block 0 %d 72 {Items:[{Slot:0b,id:\"residual:forecast_dataset\",count:1}]}".formatted(Y));
        command(server, "fill -1 %d 72 -1 %d 99 residual:interconnect".formatted(Y, Y));
        if (level.getBlockEntity(core) instanceof CoreBlockEntity be) {
            training = be;
            be.setParallelism(Parallelism.DATA.ordinal());
            be.setSpeed(2);
            be.start(FakePlayerFactory.getMinecraft(level));
        }
        relays(server, level);
        programs(server, level);
        workloads(server, level);
        ticks = new long[MEASURE];
        seen = -SETTLE;
        LOG.info("[stress] {} Ports, {} agents, {} Forecasters and {} furnaces, a training run across {} devices in a hall of {} racks", PORTS.size(),
                level.getEntitiesOfClass(Agent.class, new AABB(-64, -64, -64, 256, 64, 256)).size(), FORECASTERS, FORECASTERS * 4,
                Cluster.at(level, core).devices().size(), Cluster.at(level, core).racks().size());
    }

    /** Rows of racks at z 104 and on, four modules of eight devices under each switch, a line of Interconnect over them to the console at z 100. */
    private static void hall(MinecraftServer server) {
        StringBuilder bays = new StringBuilder();
        for (int bay = 0; bay < 8; bay++) {
            bays.append(bay == 0 ? "" : ",").append("{Slot:").append(bay).append("b,id:\"residual:accelerator_28\",count:1}");
        }
        command(server, "setblock -1 %d 101 residual:interconnect".formatted(Y));
        command(server, "setblock -1 %d 100 residual:hall_console[facing=east]".formatted(Y));
        for (int pod = 0; pod < PODS; pod++) {
            int z = 104 + pod * 4;
            for (int rack = 0; rack < RACKS_A_POD; rack++) {
                int x = rack * 2;
                for (int y = Y; y < Y + 4; y++) {
                    command(server, "setblock %d %d %d residual:rack_module[facing=south]{devices:{Items:[%s]}}".formatted(x, y, z, bays));
                }
                command(server, "setblock %d %d %d residual:rack_switch[facing=south]".formatted(x, Y + 4, z));
            }
            // a Pod Switch and Interconnect along the switches, down to a line along x -1 to the console
            command(server, "fill 0 %d %d %d %d %d residual:interconnect replace minecraft:air".formatted(Y + 4, z, RACKS_A_POD * 2 - 2, Y + 4, z));
            command(server, "setblock 1 %d %d residual:pod_switch[facing=south]".formatted(Y + 4, z));
            command(server, "fill -1 %d %d -1 %d %d residual:interconnect".formatted(Y + 4, z, Y, z));
            command(server, "fill -1 %d 102 -1 %d %d residual:interconnect".formatted(Y, Y, z));
            // a bus bar along the bottom modules' backs, a coolant pipe along their fronts from a Chiller's front round to its back
            command(server, "fill 0 %d %d %d %d %d residual:bus_bar".formatted(Y, z + 1, RACKS_A_POD * 2, Y, z + 1));
            command(server, "fill 0 %d %d %d %d %d residual:coolant_pipe".formatted(Y, z - 1, RACKS_A_POD * 2 + 2, Y, z - 1));
            command(server, "fill %d %d %d %d %d %d residual:coolant_pipe".formatted(RACKS_A_POD * 2 + 2, Y + 1, z - 1, RACKS_A_POD * 2 + 2, Y + 1, z + 1));
            command(server, "setblock %d %d %d residual:coolant_pipe".formatted(RACKS_A_POD * 2 + 2, Y, z + 1));
            command(server, "setblock %d %d %d residual:chiller[facing=north]".formatted(RACKS_A_POD * 2 + 2, Y, z));
            command(server, "setblock %d %d %d minecraft:water".formatted(RACKS_A_POD * 2 + 3, Y, z));
            command(server, "setblock %d %d %d residual:capacitor_bank{energy:{energy:64000000}}".formatted(RACKS_A_POD * 2 + 1, Y, z + 1));
            command(server, "setblock %d %d %d residual:bus_bar".formatted(RACKS_A_POD * 2 + 1, Y, z));
        }
    }

    /** A real kept Orders model opens the masts and the Relay Links used by the distant Port pairs. */
    private static void relays(MinecraftServer server, ServerLevel level) {
        int x = -10, z = -10;
        command(server, "fill %d %d %d %d %d %d minecraft:water".formatted(x - 1, Y - 1, z - 1, x + 1, Y - 1, z + 1));
        command(server, "setblock %d %d %d residual:model_server[facing=north]".formatted(x, Y, z));
        command(server, "setblock %d %d %d residual:accelerator".formatted(x + 1, Y, z));
        command(server, "setblock %d %d %d residual:accelerator".formatted(x - 1, Y, z));
        command(server, "setblock %d %d %d residual:accelerator".formatted(x, Y, z + 1));
        if (level.getBlockEntity(new BlockPos(x, Y, z)) instanceof ServerBlockEntity model) {
            ItemStack checkpoint = kept(server, "orders_full");
            if (!CrystalItem.model(checkpoint).passes(Benchmark.ORDERS)) {
                throw new IllegalStateException("the kept orders_full model does not pass Orders");
            }
            model.swap(checkpoint);
        }
        command(server, "setblock -2 %d 0 residual:relay_link".formatted(Y));
        int mast = x;
        for (int i = 0; i < MASTS; i++) {
            mast += i == 0 ? 272 : 512;
            level.getChunk(new BlockPos(mast, Y, z));
            for (int y = 0; y < 8; y++) {
                level.setBlockAndUpdate(new BlockPos(mast, Y + y, z), ModBlocks.RELAY.get().defaultBlockState());
            }
        }
        BlockPos link = new BlockPos(mast + 8, Y, z);
        for (int cx = link.getX() >> 4; cx <= link.getX() + FAR_PAIRS * 2 >> 4; cx++) {
            level.setChunkForced(cx, z >> 4, true);
            level.getChunk(cx, z >> 4);
        }
        level.setBlockAndUpdate(link, ModBlocks.RELAY_LINK.get().defaultBlockState());
        for (int i = 0; i < FAR_PAIRS; i++) {
            int fx = link.getX() + i * 2;
            command(server, "fill %d %d %d %d %d %d residual:storage_link".formatted(fx, Y, z + 1, fx + 1, Y, z + 1));
            command(server, ("setblock %d %d %d residual:port[facing=south,mode=export]"
                    + "{filter:{Items:[{Slot:0b,id:\"minecraft:cobblestone\",count:1}]}}").formatted(fx, Y, z + 2));
            command(server, "setblock %d %d %d minecraft:chest[facing=north]".formatted(fx, Y, z + 3));
            command(server, "item replace block %d %d %d container.0 with minecraft:cobblestone 64".formatted(fx, Y, z + 3));
            command(server, "setblock %d %d %d residual:storage_link".formatted(fx + 1, Y, z + 2));
            command(server, "setblock %d %d %d residual:port[facing=west,mode=import]".formatted(fx + 1, Y, z + 3));
        }
    }

    private static ItemStack kept(MinecraftServer server, String name) {
        try {
            ItemStack stack = ServerSteps.checkpoint(server, name);
            ModelInfo info = CrystalItem.model(stack);
            if (!info.causal()) {
                throw new IllegalStateException("the kept " + name + " model reads ahead");
            }
            LOG.info("[stress] kept {}: {}, {} steps, {} weights, scores {}", name, info.id(), info.steps(), info.parameters(), info.scores());
            return stack;
        } catch (IOException e) {
            throw new IllegalStateException("missing kept model " + name + "; set residual.models to the verified models directory", e);
        }
    }

    /** Additional pairs run the program interpreter while the original Port load remains in place. */
    private static void programs(MinecraftServer server, ServerLevel level) {
        for (int row = 0; row < PROGRAM_PAIRS / 4; row++) {
            int z = row * 4;
            command(server, "fill 100 %d %d 107 %d %d residual:storage_link".formatted(Y, z, Y, z));
            for (int x : new int[] {99, 108}) {
                command(server, "setblock %d %d %d residual:drive_rack{drives:{Items:[{Slot:0b,id:\"residual:storage_drive\",count:1}]}}".formatted(x, Y, z));
            }
            for (int pair = 0; pair < 4; pair++) {
                int x = 100 + pair * 2;
                command(server, "setblock %d %d %d residual:port[facing=south,mode=export]".formatted(x, Y, z + 1));
                command(server, "setblock %d %d %d minecraft:chest".formatted(x, Y, z + 2));
                command(server, "item replace block %d %d %d container.0 with minecraft:cobblestone 64".formatted(x, Y, z + 2));
                command(server, "setblock %d %d %d residual:storage_link".formatted(x + 1, Y, z + 1));
                command(server, "setblock %d %d %d residual:port[facing=west,mode=import]".formatted(x + 1, Y, z + 2));
                for (int side = 0; side < 2; side++) {
                    BlockPos pos = new BlockPos(x + side, Y, z + 1 + side);
                    String from = side == 0 ? "network" : "front", to = side == 0 ? "front" : "network";
                    command(server, ("data merge block %d %d %d {work:{upgrades:{Items:[{Slot:0b,id:\"residual:speed_upgrade\",count:3},"
                            + "{Slot:1b,id:\"residual:stack_upgrade\",count:3}]},cards:{Items:[{Slot:0b,id:\"residual:program_card\",count:1,"
                            + "components:{\"residual:program\":'repeat (send from %s to %s)'}}]}}}")
                            .formatted(pos.getX(), pos.getY(), pos.getZ(), from, to));
                    PROGRAM_PORTS.add((PortBlockEntity) level.getBlockEntity(pos));
                }
            }
        }
    }

    /** Builds real inference and training workloads from saved models and recorded examples. */
    private static void workloads(MinecraftServer server, ServerLevel level) {
        String log = System.getProperty("residual.stress.moblog", "");
        if (log.isBlank() || !Files.isRegularFile(Path.of(log))) {
            throw new IllegalStateException("residual.stress.moblog must name a real recorded Mob Log");
        }
        command(server, "setblock 104 %d 72 residual:model_server".formatted(Y));
        serving = (ServerBlockEntity) level.getBlockEntity(new BlockPos(104, Y, 72));
        serving.swap(kept(server, "reader"));
        serving.items().setItem(ServerBlockEntity.CACHE, new ItemStack(ModItems.KV_CACHE.get(), 8));
        serving.setBatch(8);
        command(server, "setblock 104 %d 84 residual:tuner".formatted(Y));
        tuner = (TunerBlockEntity) level.getBlockEntity(new BlockPos(104, Y, 84));
        tuner.items().setItem(TunerBlockEntity.BASE, kept(server, "sorting"));
        tuner.items().setItem(TunerBlockEntity.AID, new ItemStack(ModItems.DATASETS.get(Benchmark.SORTING).get()));
        tuner.items().setItem(TunerBlockEntity.OUT, new ItemStack(ModItems.CHECKPOINT.get()));
        tuner.button(35, FakePlayerFactory.getMinecraft(level));
        tuner.button(1, FakePlayerFactory.getMinecraft(level));
        for (int z : new int[] {72, 84}) {
            command(server, "fill 103 %d %d 105 %d %d minecraft:water".formatted(Y - 1, z - 1, Y - 1, z + 1));
            for (BlockPos pos : List.of(new BlockPos(103, Y, z), new BlockPos(105, Y, z), new BlockPos(104, Y, z + 1))) {
                level.setBlockAndUpdate(pos, ModBlocks.ACCELERATOR.get().defaultBlockState());
            }
        }
        command(server, "fill 100 %d 96 107 %d 103 minecraft:glass hollow".formatted(Y - 1, Y + 3));
        command(server, "setblock 104 %d 96 residual:arena[facing=south]{energy:{energy:4000000}}".formatted(Y + 1));
        command(server, "fill 96 %d 95 111 %d 95 minecraft:water".formatted(Y, Y));
        command(server, "fill 96 %d 95 111 %d 95 residual:accelerator".formatted(Y + 1, Y + 1));
        command(server, "setblock 112 %d 95 residual:capacitor_bank{energy:{energy:64000000}}".formatted(Y + 1));
        arena = (ArenaBlockEntity) level.getBlockEntity(new BlockPos(104, Y + 1, 96));
        arena.items().setItem(ArenaBlockEntity.MODEL, kept(server, System.getProperty("residual.stress.arena", "arena_imitation")));
        try {
            arena.items().setItem(ArenaBlockEntity.LOG, ServerSteps.mobLog(Path.of(log)));
        } catch (IOException e) {
            throw new IllegalStateException("cannot load the recorded Mob Log", e);
        }
        if (Boolean.parseBoolean(System.getProperty("residual.stress.reactor", "true"))) {
            ItemStack controller = kept(server, "reactor");
            if (!CrystalItem.model(controller).passes(Benchmark.REACTOR)) {
                throw new IllegalStateException("the kept reactor model does not pass Containment");
            }
            command(server, "fill 96 %d 108 100 %d 112 residual:reactor_casing".formatted(Y, Y + 4));
            command(server, "fill 97 %d 109 99 %d 111 residual:fuel_rod".formatted(Y + 1, Y + 3));
            command(server, "setblock 98 %d 108 residual:reactor_core[facing=north]".formatted(Y + 2));
            command(server, "fill 101 %d 109 101 %d 116 residual:bus_bar".formatted(Y + 2, Y + 2));
            command(server, "fill 102 %d 109 109 %d 116 residual:capacitor_bank".formatted(Y + 2, Y + 2));
            reactor = (ReactorBlockEntity) level.getBlockEntity(new BlockPos(98, Y + 2, 108));
            ReactorVerify.arm(level, reactor.getBlockPos());
            reactor.items().setItem(ReactorBlockEntity.CONTROLLER, controller);
            reactor.items().setItem(ReactorBlockEntity.FUEL, new ItemStack(ModItems.REACTOR_FUEL.get(), 64));
        } else {
            LOG.warn("[stress] reactor omitted by residual.stress.reactor=false; this run does not measure reactor load");
        }
        LOG.info("[stress] added {} program Ports, one batched Model Server, one automatic Tuner, one Arena and {} reactor", PROGRAM_PORTS.size(), reactor == null ? 0 : 1);
    }

    /** Keeps the ordinary workloads supplied and counts their actual progress. */
    private static void work(MinecraftServer server) {
        if (server.getTickCount() % 20 == 0) {
            for (int i = 0; i < 8; i++) {
                serving.ask("> question " + (server.getTickCount() / 20 % 40) + "\n= ", 16, (char) 1, answer -> {
                    if (answer == null) {
                        rejected++;
                    } else {
                        answered++;
                    }
                });
            }
        }
        int steps = tuner.sync(0).steps();
        tunerSteps += Math.max(0, steps - priorTunerSteps);
        priorTunerSteps = steps;
        if (CrystalItem.model(tuner.items().getItem(TunerBlockEntity.OUT)) != null) {
            tuned++;
            tuner.items().setItem(TunerBlockEntity.OUT, new ItemStack(ModItems.CHECKPOINT.get()));
        }
    }

    private static boolean ready() {
        return training.running() && answered > 0 && tuner.running() && arena.running() > 0
                && (reactor == null || reactor.status() == ReactorBlockEntity.RUNNING && reactor.rate() > 0);
    }

    private static int moved() {
        int sum = 0;
        try {
            Field field = PortBlockEntity.class.getDeclaredField("movedLastSecond");
            field.setAccessible(true);
            for (PortBlockEntity port : PORTS) {
                sum += field.getInt(port);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return sum;
    }

    @SubscribeEvent
    static void tick(ServerTickEvent.Post event) {
        if (ticks == null) {
            return;
        }
        MinecraftServer server = event.getServer();
        work(server);
        if (seen == 0) {
            if (!ready()) {
                if (++warmTicks % 200 == 0) {
                    LOG.info("[stress] waiting for workloads: training {}, answers {}, tuning {}, Arena {} with {} fights, reactor {} at {} FE/t",
                            training.running(), answered, tuner.running(), arena.state(), arena.running(),
                            reactor == null ? "omitted" : reactor.status(), reactor == null ? 0 : reactor.rate());
                }
                if (warmTicks >= READY_LIMIT) {
                    throw new IllegalStateException("stress workloads did not become active before measurement");
                }
                return;
            }
            answeredBefore = answered;
            tunedBefore = tuned;
            tunerStepsBefore = tunerSteps;
            trainingBefore = training.snapshot().steps();
            began = System.nanoTime();
            LOG.info("[stress] workloads active; measuring {} ticks at requested 20 TPS", MEASURE);
        }
        long[] times = server.getTickTimesNanos();
        long took = times[(server.getTickCount() - 1) % times.length];
        if (seen >= 0) {
            ticks[seen] = took;
            trainingTicks += training.running() ? 1 : 0;
            tuningTicks += tuner.running() ? 1 : 0;
            arenaTicks += arena.running() > 0 ? 1 : 0;
            reactorTicks += reactor != null && reactor.status() == ReactorBlockEntity.RUNNING && reactor.rate() > 0 ? 1 : 0;
            if (reactor != null) {
                int status = reactor.status();
                reactorStatusTicks[status]++;
                reactorZeroRateTicks += status == ReactorBlockEntity.RUNNING && reactor.rate() <= 0 ? 1 : 0;
                if (status != priorReactorStatus) {
                    LOG.info("[stress] reactor transition at measured tick {}: status {} -> {}, rate {}, fuel {}, energy {}",
                            seen, priorReactorStatus, status, reactor.rate(),
                            reactor.items().getItem(ReactorBlockEntity.FUEL).getCount(), reactor.energy().getAmountAsInt());
                    priorReactorStatus = status;
                }
            }
        }
        seen++;
        if (seen == MEASURE / 2 || seen == MEASURE / 2 + 20) {
            List<Agent> agents = server.overworld().getEntitiesOfClass(Agent.class, new AABB(-64, -64, -64, 256, 64, 256));
            if (seen == MEASURE / 2) {
                places.clear();
                agents.forEach(a -> places.put(a, a.position()));
            } else {
                LOG.info("[stress] in the last second {} items moved through Ports and {} of {} agents moved", moved(),
                        agents.stream().filter(a -> a.position().distanceToSqr(places.get(a)) > 0.25).count(), agents.size());
                LOG.info("[stress] program Ports moved {} items in the last second; Arena tier {}, {} fights on {} compute",
                        PROGRAM_PORTS.stream().mapToInt(PortBlockEntity::moved).sum(), arena.tier(), arena.running(), arena.compute());
                for (Relays.Net net : Relays.of(server).all()) {
                    LOG.info("[stress] a relay network of {} parts, {} answers a second: {} items a second over its links, open {}",
                            net.parts.size(), net.capacity(), net.itemsPerSecond(), net.open());
                }
            }
        }
        if (seen == MEASURE) {
            long[] sorted = ticks.clone();
            Arrays.sort(sorted);
            LOG.info(String.format(Locale.ROOT, "[stress] over %d ticks: mean %.2f ms, median %.2f ms, p95 %.2f ms, p99 %.2f ms, longest %.2f ms",
                    MEASURE, Arrays.stream(sorted).average().orElse(0) / 1e6, sorted[MEASURE / 2] / 1e6, sorted[MEASURE * 95 / 100] / 1e6,
                    sorted[MEASURE * 99 / 100] / 1e6, sorted[MEASURE - 1] / 1e6));
            double seconds = (System.nanoTime() - began) / 1e9;
            int progressed = training.snapshot().steps() - trainingBefore;
            boolean valid = progressed > 0 && answered > answeredBefore && tunerSteps > tunerStepsBefore
                    && trainingTicks >= MEASURE * 9 / 10 && tuningTicks >= MEASURE * 9 / 10 && arenaTicks >= MEASURE * 9 / 10
                    && (reactor == null || reactorTicks >= MEASURE * 9 / 10) && moved() > 0
                    && PROGRAM_PORTS.stream().mapToInt(PortBlockEntity::moved).sum() > 0;
            LOG.info(String.format(Locale.ROOT, "[stress] wall %.2f s, %.2f TPS; training +%d steps, serving %d answers (%d refused total), tuning +%d steps / %d saves",
                    seconds, MEASURE / seconds, progressed, answered - answeredBefore, rejected, tunerSteps - tunerStepsBefore, tuned - tunedBefore));
            LOG.info("[stress] active ticks of {}: training {}, tuning {}, Arena {}, reactor {}; workload valid {}",
                    MEASURE, trainingTicks, tuningTicks, arenaTicks, reactor == null ? "omitted" : reactorTicks, valid);
            if (reactor != null) {
                LOG.info("[stress] reactor status ticks [running, notBuilt, noController, notTrusted, noFuel, tripped, full]={}; running with zero rate={}",
                        Arrays.toString(reactorStatusTicks), reactorZeroRateTicks);
                ReactorVerify.report();
            }
            ticks = null;
            if (!valid) {
                throw new IllegalStateException("stress measurement lacked sustained workload activity; inspect the activity counts");
            }
            server.halt(false);
        }
    }
}
