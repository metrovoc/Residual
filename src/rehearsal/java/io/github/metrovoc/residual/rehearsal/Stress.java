package io.github.metrovoc.residual.rehearsal;

import com.mojang.logging.LogUtils;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.agent.Agent;
import io.github.metrovoc.residual.block.CoreBlockEntity;
import io.github.metrovoc.residual.storage.PortBlockEntity;
import java.lang.reflect.Field;
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
 * rounds; {@link #FORECASTERS} Forecasters among working furnaces; and a two-block transformer training at full speed.
 * It lets the base settle for a minute, logs the next minute's tick times and stops the server. The {@code stress} run
 * turns it on.
 */
@EventBusSubscriber(modid = Residual.MOD_ID)
public final class Stress {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int PAIRS = 128, ROW = 16, AGENTS = 256, FORECASTERS = 32;
    private static final int SETTLE = 1200, MEASURE = 1200;
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
        // ---- a two-block transformer training at full speed
        BlockPos core = new BlockPos(0, Y, 72);
        ServerSteps.blueprint(level, "gpt32", core, Direction.EAST);
        command(server, "data merge block 0 %d 72 {Items:[{Slot:0b,id:\"residual:forecast_dataset\",count:1}]}".formatted(Y));
        if (level.getBlockEntity(core) instanceof CoreBlockEntity be) {
            be.setSpeed(2);
            be.start(FakePlayerFactory.getMinecraft(level));
        }
        ticks = new long[MEASURE];
        seen = -SETTLE;
        LOG.info("[stress] {} Ports, {} agents, {} Forecasters and {} furnaces, a training run", PORTS.size(),
                level.getEntitiesOfClass(Agent.class, new AABB(-64, -64, -64, 256, 64, 256)).size(), FORECASTERS, FORECASTERS * 4);
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
