package io.github.metrovoc.residual.rehearsal;

import com.mojang.logging.LogUtils;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.energy.ReactorBlockEntity;
import io.github.metrovoc.residual.learn.Reactor;
import java.lang.reflect.Field;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** Observes completed live reactor cycles and trips without changing the reactor or its controller. */
@EventBusSubscriber(modid = Residual.MOD_ID)
public final class ReactorVerify {
    private static final Logger LOG = LogUtils.getLogger();
    private static Measurement measurement;

    private ReactorVerify() {
    }

    static void run(ServerPlayer player, String[] args) throws ReflectiveOperationException {
        switch (args[0]) {
            case "arm" -> arm(player.level(), new BlockPos(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3])));
            case "report" -> report();
            case "clear" -> measurement = null;
            default -> throw new IllegalArgumentException("unknown reactor operation " + args[0]);
        }
    }

    static void arm(ServerLevel level, BlockPos pos) {
        require(measurement == null, "clear the preceding reactor measurement first");
        try {
            measurement = new Measurement(level, pos);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot observe reactor", e);
        }
    }

    static void report() {
        require(measurement != null, "reactor measurement was not armed");
        measurement.report();
    }

    static long read(ServerLevel level, BlockPos pos, String kind) {
        Measurement m = measurement;
        require(m != null && m.level == level && m.reactor.getBlockPos().equals(pos), "no reactor measurement at " + pos);
        require(m.failure == null, "reactor measurement failed: " + m.failure);
        return switch (kind) {
            case "reactorcycles" -> Arrays.stream(m.completed).sum();
            case "reactorages" -> Arrays.stream(m.completed).filter(n -> n > 0).count();
            case "reactortrips" -> m.trips;
            default -> throw new IllegalArgumentException("unknown reactor measurement " + kind);
        };
    }

    @SubscribeEvent
    static void after(ServerTickEvent.Post event) {
        Measurement m = measurement;
        if (m != null && m.failure == null && m.level.getServer() == event.getServer()) {
            try {
                m.after();
            } catch (Exception e) {
                m.failure = e.toString();
                LOG.error("[rehearsal] reactor observation failed", e);
            }
        }
    }

    @SubscribeEvent
    static void stopped(ServerStoppedEvent event) {
        measurement = null;
    }

    private static final class Measurement {
        final ServerLevel level;
        final ReactorBlockEntity reactor;
        final Field cycleField = field("cycle"), tripField = field("tripped"), waitingField = field("waiting"),
                unsureField = field("unsure"), sureField = field("lastSure");
        final int[] completed = new int[Reactor.AGES.length()], held = new int[Reactor.AGES.length()];
        Reactor.Cycle previous;
        int previousTrip, trips;
        String failure;

        Measurement(ServerLevel level, BlockPos pos) throws ReflectiveOperationException {
            this.level = level;
            require(level.getBlockEntity(pos) instanceof ReactorBlockEntity, "no reactor at " + pos);
            reactor = (ReactorBlockEntity) level.getBlockEntity(pos);
            require(cycleField.get(reactor) == null && tripField.getInt(reactor) == 0, "arm the reactor before fueling it");
            LOG.info("[rehearsal] reactor observation armed at {}", pos);
        }

        void after() throws ReflectiveOperationException {
            require(!reactor.isRemoved() && level.getBlockEntity(reactor.getBlockPos()) == reactor, "observed reactor was removed");
            Reactor.Cycle current = (Reactor.Cycle) cycleField.get(reactor);
            int trip = tripField.getInt(reactor);
            if (trip > 0 && previousTrip == 0) {
                trips++;
                LOG.info("[rehearsal] reactor trip {} after completed={} held={} previousPower={} waiting={} unsure={} lastSure={}",
                        trips, Arrays.toString(completed), Arrays.toString(held), previous == null ? -1 : previous.power(),
                        waitingField.getInt(reactor), unsureField.getInt(reactor), sureField.getFloat(reactor));
            }
            if (previous != null && current == null && trip == 0 && previous.over()) {
                completed[previous.kind]++;
                if (previous.held()) {
                    held[previous.kind]++;
                }
                LOG.info("[rehearsal] reactor completed age={} score={} completed={} held={} trips={}",
                        Reactor.AGES.charAt(previous.kind), previous.score(), Arrays.toString(completed), Arrays.toString(held), trips);
            }
            previous = current;
            previousTrip = trip;
        }

        void report() {
            require(failure == null, "reactor measurement failed: " + failure);
            LOG.info("[rehearsal] reactor observation completed={} held={} trips={} status={} rate={}",
                    Arrays.toString(completed), Arrays.toString(held), trips, reactor.status(), reactor.rate());
        }
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = ReactorBlockEntity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
