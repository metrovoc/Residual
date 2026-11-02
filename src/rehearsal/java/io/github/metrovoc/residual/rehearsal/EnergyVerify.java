package io.github.metrovoc.residual.rehearsal;

import com.mojang.logging.LogUtils;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.compute.Cluster;
import io.github.metrovoc.residual.compute.ProcessNode;
import io.github.metrovoc.residual.energy.CellBlockEntity;
import io.github.metrovoc.residual.registry.ModBlocks;
import io.github.metrovoc.residual.world.Cooling;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** Measures rated compute draw through a real Energy Cell across active ticks, idle time and a restart. */
@EventBusSubscriber(modid = Residual.MOD_ID)
public final class EnergyVerify {
    private static final Logger LOG = LogUtils.getLogger();
    private static Measurement measurement;

    private EnergyVerify() {
    }

    static void run(ServerPlayer player, String[] args) {
        switch (args[0]) {
            case "arm" -> {
                require(measurement == null, "clear the preceding energy measurement first");
                BlockPos host = new BlockPos(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
                BlockPos cell = new BlockPos(Integer.parseInt(args[4]), Integer.parseInt(args[5]), Integer.parseInt(args[6]));
                measurement = new Measurement(player.level(), host, cell);
            }
            case "check" -> {
                require(measurement != null, "energy measurement was not armed");
                measurement.check();
            }
            case "clear" -> measurement = null;
            default -> throw new IllegalArgumentException("unknown energy operation " + args[0]);
        }
    }

    @SubscribeEvent
    static void after(ServerTickEvent.Post event) {
        Measurement m = measurement;
        if (m != null && m.tick < 240 && m.failure == null && m.level.getServer() == event.getServer()) {
            try {
                m.after();
            } catch (RuntimeException e) {
                m.failure = e.getMessage();
                LOG.error("[energy-verify] measurement failed", e);
            }
        }
    }

    @SubscribeEvent
    static void stopped(ServerStoppedEvent event) {
        measurement = null;
    }

    private static final class Measurement {
        final ServerLevel level;
        final BlockPos host;
        final CellBlockEntity cell;
        final int initial;
        int tick;
        String failure;

        Measurement(ServerLevel level, BlockPos host, BlockPos cellPos) {
            this.level = level;
            this.host = host;
            require(level.getBlockState(cellPos).is(ModBlocks.ENERGY_CELL.get()), "probe requires an Energy Cell, rated at 2,000 FE/t");
            cell = (CellBlockEntity) level.getBlockEntity(cellPos);
            require(cell != null, "no Energy Cell at " + cellPos);
            Cluster cluster = Cluster.at(level, host);
            require(cluster.devices().size() == 8, "probe requires eight devices");
            for (int i = 0; i < 8; i++) {
                var device = cluster.devices().get(i);
                require(device.bare() && device.node().equals(ProcessNode.N90), "probe requires eight bare 90 nm Accelerators");
                require(cluster.free(i) == (i < 4), "probe requires four free and four metered devices");
            }
            initial = cell.energy().getAmountAsInt();
            require(initial >= 12_800, "probe needs at least 12,800 FE");
            LOG.info("[energy-verify] armed eight bare 90 nm devices, four metered at 320 FE/t, cell={} FE", initial);
        }

        void after() {
            require(!cell.isRemoved(), "probe Energy Cell was removed");
            tick++;
            int before = cell.energy().getAmountAsInt();
            boolean active = tick <= 20 || tick > 220;
            if (active) {
                require(Cooling.payRated(level, host), "rated tick was not fully funded at tick " + tick);
                require(Cooling.payRated(level, host), "duplicate call lost the funded result at tick " + tick);
            }
            int spent = before - cell.energy().getAmountAsInt();
            require(spent == (active ? 320 : 0), "tick " + tick + " expected " + (active ? 320 : 0) + " FE, drew " + spent);
            int total = initial - cell.energy().getAmountAsInt();
            int expected = (Math.min(tick, 20) + Math.max(0, tick - 220)) * 320;
            require(total == expected, "tick " + tick + " expected total " + expected + " FE, drew " + total);
            if (tick == 20 || tick == 220 || tick == 221 || tick == 240) {
                LOG.info("[energy-verify] tick={} active={} draw={} FE total={} FE", tick, active, spent, total);
            }
        }

        void check() {
            require(failure == null, "energy measurement failed: " + failure);
            require(tick == 240, "energy measurement has completed " + tick + " of 240 ticks");
            require(initial - cell.energy().getAmountAsInt() == 12_800, "energy changed after the measurement");
            LOG.info("[energy-verify] passed: 6,400 FE in 20 active ticks; 200 idle ticks at zero draw; restart 320 FE; second 20 active ticks 6,400 FE; same-tick calls charged once");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
