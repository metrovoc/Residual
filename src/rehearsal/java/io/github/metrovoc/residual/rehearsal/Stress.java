package io.github.metrovoc.residual.rehearsal;

import com.mojang.logging.LogUtils;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.agent.Agent;
import io.github.metrovoc.residual.block.CoreBlockEntity;
import io.github.metrovoc.residual.block.ServerBlockEntity;
import io.github.metrovoc.residual.compute.Cluster;
import io.github.metrovoc.residual.compute.Parallelism;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.registry.ModBlocks;
import io.github.metrovoc.residual.registry.ModComponents;
import io.github.metrovoc.residual.registry.ModItems;
import io.github.metrovoc.residual.storage.PortBlockEntity;
import io.github.metrovoc.residual.world.Relays;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
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
 * settle for a minute, logs the next minute's tick times and stops the server. The {@code stress} run turns it on.
 */
@EventBusSubscriber(modid = Residual.MOD_ID)
public final class Stress {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int PAIRS = 128, ROW = 16, AGENTS = 256, FORECASTERS = 32, MASTS = 32, FAR_PAIRS = 8;
    private static final int SETTLE = 1200, MEASURE = 1200;
    private static final int PODS = 4, RACKS_A_POD = 8;
    /** The ground of a flat world. */
    private static final int Y = -60;

    private static long[] ticks;
    private static int seen;
    private static final List<PortBlockEntity> PORTS = new ArrayList<>();
    private static final Map<Agent, Vec3> places = new HashMap<>();

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
            be.setParallelism(Parallelism.DATA.ordinal());
            be.setSpeed(2);
            be.start(FakePlayerFactory.getMinecraft(level));
        }
        relays(server, level);
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

    /**
     * A served model that opens its network, masts out along a line, a Relay Link on the first row's rack, and at the far end
     * another with Port pairs on it. No one asks the model anything, so the checkpoint need hold no real one.
     */
    private static void relays(MinecraftServer server, ServerLevel level) {
        int x = -10, z = -10;
        command(server, "fill %d %d %d %d %d %d minecraft:water".formatted(x - 1, Y - 1, z - 1, x + 1, Y - 1, z + 1));
        command(server, "setblock %d %d %d residual:model_server[facing=north]".formatted(x, Y, z));
        command(server, "setblock %d %d %d residual:accelerator".formatted(x + 1, Y, z));
        command(server, "setblock %d %d %d residual:accelerator".formatted(x - 1, Y, z));
        command(server, "setblock %d %d %d residual:accelerator".formatted(x, Y, z + 1));
        if (level.getBlockEntity(new BlockPos(x, Y, z)) instanceof ServerBlockEntity model) {
            ItemStack checkpoint = new ItemStack(ModItems.CHECKPOINT.get());
            checkpoint.set(ModComponents.MODEL.get(), new ModelInfo(UUID.randomUUID(), 113_742, 1, Map.of(Benchmark.ORDERS.id, 1f), true));
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
        long[] times = server.getTickTimesNanos();
        long took = times[(server.getTickCount() - 1) % times.length];
        if (seen >= 0) {
            ticks[seen] = took;
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
            ticks = null;
            server.halt(false);
        }
    }
}
