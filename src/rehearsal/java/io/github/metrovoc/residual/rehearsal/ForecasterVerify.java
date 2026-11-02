package io.github.metrovoc.residual.rehearsal;

import com.mojang.logging.LogUtils;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.block.Bursts;
import io.github.metrovoc.residual.block.ForecasterBlockEntity;
import io.github.metrovoc.residual.energy.CellBlockEntity;
import io.github.metrovoc.residual.item.CrystalItem;
import io.github.metrovoc.residual.learn.Forecast;
import io.github.metrovoc.residual.model.Checkpoints;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.world.Cooling;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** Measures a UI-started burst with a continuous supply of raw iron and collection of actual furnace outputs. */
@EventBusSubscriber(modid = Residual.MOD_ID)
public final class ForecasterVerify {
    private static final Logger LOG = LogUtils.getLogger();
    private static Measurement measurement;
    private static Interruption interruption;

    private ForecasterVerify() {
    }

    static void run(ServerPlayer player, String[] args) throws ReflectiveOperationException {
        switch (args[0]) {
            case "arm" -> {
                require(measurement == null, "clear the preceding forecast measurement first");
                BlockPos pos = new BlockPos(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
                measurement = new Measurement(player.level(), pos, Integer.parseInt(args[4]), Integer.parseInt(args[5]));
            }
            case "check" -> {
                require(measurement != null, "forecast measurement was not armed");
                measurement.check();
            }
            case "cut" -> {
                require(interruption == null, "clear the preceding forecast interruption first");
                BlockPos pos = new BlockPos(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
                BlockPos supply = new BlockPos(Integer.parseInt(args[5]), Integer.parseInt(args[6]), Integer.parseInt(args[7]));
                ForecasterBlockEntity forecaster = (ForecasterBlockEntity) player.level().getBlockEntity(pos);
                require((int) field(forecaster, "burstLeft") > 0, "burst is not active before interruption");
                Map<?, ?> machines = (Map<?, ?>) field(forecaster, "machines");
                List<BlockPos> targets = machines.keySet().stream().map(k -> BlockPos.of((long) k)).toList();
                require(targets.stream().allMatch(p -> Bursts.next(player.level(), p) > 2), "burst registrations are absent before interruption");
                BlockPos overlap = args[4].equals("overlap") ? targets.getFirst() : null;
                interruption = new Interruption(player.level(), forecaster, targets, forecaster.energy().getAmountAsInt(), args[4], overlap);
                switch (args[4]) {
                    case "device" -> {
                        player.level().setBlockAndUpdate(supply, Blocks.AIR.defaultBlockState());
                        require(Cooling.compute(player.level(), pos, 0, 64).threads() == 28,
                                "device removal must leave the other seven devices powered for exactly 28 compute");
                    }
                    case "power" -> ((CellBlockEntity) player.level().getBlockEntity(supply)).energy().set(1);
                    case "model" -> {
                        Container items = (Container) field(forecaster, "items");
                        require(!items.getItem(ForecasterBlockEntity.MODEL).isEmpty(), "no model to remove");
                        items.setItem(ForecasterBlockEntity.MODEL, ItemStack.EMPTY);
                    }
                    case "overlap" -> {
                        // The same unowned registration used by an Hourglass must survive the Forecaster's cancellation.
                        Bursts.start(player.level(), overlap, 2, 40);
                        ((CellBlockEntity) player.level().getBlockEntity(supply)).energy().set(1);
                    }
                    default -> throw new IllegalArgumentException("unknown forecast interruption " + args[4]);
                }
            }
            case "stopped" -> {
                require(interruption != null, "no burst interruption was armed");
                interruption.check();
            }
            case "clear" -> {
                measurement = null;
                if (interruption != null) {
                    interruption.clear();
                }
                interruption = null;
            }
            default -> throw new IllegalArgumentException("unknown forecast operation " + args[0]);
        }
    }

    @SubscribeEvent
    static void before(ServerTickEvent.Pre event) {
        Measurement m = measurement;
        if (m != null && !m.done && m.failure == null && m.level.getServer() == event.getServer()) {
            try {
                m.before();
            } catch (Exception e) {
                m.failure = e.toString();
            }
        }
    }

    @SubscribeEvent
    static void after(ServerTickEvent.Post event) {
        Measurement m = measurement;
        if (m != null && !m.done && m.failure == null && m.level.getServer() == event.getServer()) {
            try {
                m.after();
            } catch (Exception e) {
                m.failure = e.toString();
            }
        }
        Interruption i = interruption;
        if (i != null && !i.observed && i.level.getServer() == event.getServer() && i.level.getGameTime() > i.cutAt) {
            i.observed = true;
            try {
                i.observe();
            } catch (Exception e) {
                i.failure = e.toString();
            }
        }
    }

    @SubscribeEvent
    static void stopped(ServerStoppedEvent event) {
        measurement = null;
        interruption = null;
    }

    private static final class Interruption {
        final ServerLevel level;
        final ForecasterBlockEntity forecaster;
        final List<BlockPos> targets;
        final int energyBefore;
        final String kind;
        final BlockPos overlap;
        final long cutAt;
        final ItemStack removedModel;
        boolean observed;
        String failure;

        Interruption(ServerLevel level, ForecasterBlockEntity forecaster, List<BlockPos> targets, int energyBefore,
                     String kind, BlockPos overlap) throws ReflectiveOperationException {
            this.level = level;
            this.forecaster = forecaster;
            this.targets = targets;
            this.energyBefore = energyBefore;
            this.kind = kind;
            this.overlap = overlap;
            cutAt = level.getGameTime();
            removedModel = kind.equals("model") ? ((Container) field(forecaster, "items")).getItem(ForecasterBlockEntity.MODEL).copy() : ItemStack.EMPTY;
        }

        void observe() throws ReflectiveOperationException {
            require((int) field(forecaster, "burstLeft") == 0, "burst continued after losing " + kind);
            require(forecaster.energy().getAmountAsInt() == energyBefore, "interrupted burst charged its battery again");
            require(targets.stream().allMatch(p -> Bursts.next(level, p) == (p.equals(overlap) ? 4 : 2)),
                    "interrupted burst left its own work behind or cancelled another source's burst");
            Field registrations = Bursts.class.getDeclaredField("RUNNING");
            registrations.setAccessible(true);
            Map<?, ?> running = (Map<?, ?>) ((Map<?, ?>) registrations.get(null)).get(level);
            if (running != null) {
                for (Object burst : running.values()) {
                    require(!forecaster.getBlockPos().equals(field(burst, "source")), "interrupted burst retained an owned registration");
                }
            }
        }

        void check() {
            require(observed, "the next server tick has not completed after interruption");
            require(failure == null, "forecast interruption failed on its first completed tick: " + failure);
            LOG.info("[forecast-verify] PASS: {} interruption stopped on the first completed tick without another battery charge or any owned registration", kind);
        }

        void clear() throws ReflectiveOperationException {
            if (overlap != null) {
                Bursts.stop(level, overlap, null);
            }
            if (!removedModel.isEmpty()) {
                ((Container) field(forecaster, "items")).setItem(ForecasterBlockEntity.MODEL, removedModel.copy());
            }
        }
    }

    private static final class Measurement {
        final ServerLevel level;
        final ForecasterBlockEntity forecaster;
        final int factor, expectedMachines, energyBefore;
        final List<AbstractFurnaceBlockEntity> furnaces = new ArrayList<>();
        final List<Long> times = new ArrayList<>();
        final Map<Integer, Integer> progress = new TreeMap<>();
        final long[] made;
        final int[] beforeCook;
        long tickBegan, began, ended;
        int workingTicks, paidTicks, ticks;
        boolean started, done;
        String failure;

        Measurement(ServerLevel level, BlockPos pos, int factor, int expectedMachines) throws ReflectiveOperationException {
            this.level = level;
            this.factor = factor;
            this.expectedMachines = expectedMachines;
            forecaster = (ForecasterBlockEntity) level.getBlockEntity(pos);
            require(forecaster != null && forecaster.third(), "no Forecaster III at " + pos);
            Container items = (Container) field(forecaster, "items");
            ModelInfo info = CrystalItem.model(items.getItem(ForecasterBlockEntity.MODEL));
            require(info != null && info.causal(), "a genuine causal checkpoint must be installed");
            var model = Checkpoints.load(level.getServer(), info.id());
            require(model != null, "checkpoint weights are missing");
            String watched = (String) field(forecaster, "watched");
            Forecast.Fit fresh = Forecast.check(model, watched).machines();
            Forecast.Check cached = (Forecast.Check) field(forecaster, "check");
            require(cached != null && forecaster.trusts(Forecast.Kind.MACHINES), "Forecaster has not trusted the machines");
            require(fresh.ahead(Forecast.MOST_II) * 16 >= factor, "fresh model fit does not allow ×" + factor + ": " + fresh.ratio());
            require(cached.machines().ahead(Forecast.MOST_II) * 16 >= factor, "the Forecaster's own check does not allow ×" + factor);
            int compute = Cooling.compute(level, pos, 0, 64).threads();
            require(compute == ForecasterBlockEntity.compute(factor), "expected exact burst compute " + ForecasterBlockEntity.compute(factor) + ", found " + compute);
            Map<?, ?> machines = (Map<?, ?>) field(forecaster, "machines");
            require(machines.size() == expectedMachines, "expected " + expectedMachines + " machines, found " + machines.size());
            for (Object key : machines.keySet()) {
                var block = level.getBlockEntity(BlockPos.of((long) key));
                require(block instanceof AbstractFurnaceBlockEntity, "continuous-supply fixture requires furnaces");
                furnaces.add((AbstractFurnaceBlockEntity) block);
            }
            energyBefore = forecaster.energy().getAmountAsInt();
            made = new long[furnaces.size()];
            beforeCook = new int[furnaces.size()];
            LOG.info("[forecast-verify] armed ×{} on {} furnaces: model {} ({} steps), fresh ratio {}, cached ratio {}, natural growth {}, compute {}, battery {} FE",
                    factor, expectedMachines, info.id(), info.steps(), fresh.ratio(), cached.machines().ratio(), fresh.grown(), compute, energyBefore);
        }

        void before() throws ReflectiveOperationException {
            tickBegan = System.nanoTime();
            for (int i = 0; i < furnaces.size(); i++) {
                AbstractFurnaceBlockEntity furnace = furnaces.get(i);
                furnace.setItem(0, new ItemStack(Items.RAW_IRON, 64));
                if (furnace.getItem(1).isEmpty()) {
                    furnace.setItem(1, new ItemStack(Items.COAL_BLOCK, 64));
                }
                furnace.setItem(2, ItemStack.EMPTY);
                beforeCook[i] = (int) field(furnace, "cookingTimer");
            }
        }

        void after() throws ReflectiveOperationException {
            int spent = energyBefore - forecaster.energy().getAmountAsInt();
            if (!started && spent == 0) {
                return;
            }
            if (!started) {
                started = true;
                began = tickBegan;
            }
            int cost = (factor - 1) * expectedMachines;
            require(spent >= 0 && spent % cost == 0, "battery change is not a whole " + cost + " FE burst tick: " + spent);
            paidTicks = spent / cost;
            require((int) field(forecaster, "burstPace") == factor, "burst pace changed");
            if ((int) field(forecaster, "burstLeft") <= 0 && Bursts.next(level, furnaces.getFirst().getBlockPos()) == 2) {
                ended = System.nanoTime();
                done = true;
                describe();
                return;
            }
            require(Cooling.compute(level, forecaster.getBlockPos(), 0, 64).threads() == ForecasterBlockEntity.compute(factor),
                    "burst lost its required compute");
            int fastest = 0;
            for (int i = 0; i < furnaces.size(); i++) {
                AbstractFurnaceBlockEntity furnace = furnaces.get(i);
                ItemStack out = furnace.getItem(2);
                require(out.isEmpty() || out.is(Items.IRON_INGOT), "unexpected furnace output " + out);
                made[i] += out.getCount();
                int cook = out.getCount() * 200 + (int) field(furnace, "cookingTimer") - beforeCook[i];
                progress.merge(cook, 1, Integer::sum);
                fastest = Math.max(fastest, cook);
            }
            workingTicks += fastest > 1 ? 1 : 0;
            ticks++;
            times.add(System.nanoTime() - tickBegan);
        }

        void describe() {
            long[] sorted = times.stream().mapToLong(Long::longValue).sorted().toArray();
            double seconds = (ended - began) / 1e9;
            LOG.info("[forecast-verify] finished: paid {} × {} FE, {} active burst ticks in {} measured ticks / {} s ({} TPS); output per furnace {}; cooking ticks per furnace per server tick {}",
                    paidTicks, (factor - 1) * expectedMachines, workingTicks, ticks, seconds, ticks / seconds, Arrays.toString(made), progress);
            LOG.info("[forecast-verify] processing: mean {} ms, p95 {} ms, p99 {} ms, max {} ms",
                    Arrays.stream(sorted).average().orElse(0) / 1e6, sorted[sorted.length * 95 / 100] / 1e6,
                    sorted[sorted.length * 99 / 100] / 1e6, sorted[sorted.length - 1] / 1e6);
        }

        void check() {
            require(failure == null, "forecast measurement failed: " + failure);
            require(done, "forecast measurement has not finished");
            require(paidTicks == ForecasterBlockEntity.BURST, "burst paid for " + paidTicks + " ticks, expected " + ForecasterBlockEntity.BURST);
            require(workingTicks == ForecasterBlockEntity.BURST, "burst ran for " + workingTicks + " ticks, expected " + ForecasterBlockEntity.BURST);
            require(progress.size() == 1 && progress.containsKey(factor), "cooking pace differs from ×" + factor + ": " + progress);
            require(times.stream().mapToLong(Long::longValue).average().orElse(0) < 50_000_000, "mean server processing exceeds 50 ms");
            require(ticks / ((ended - began) / 1e9) >= 19.5, "burst did not sustain approximately 20 TPS");
            LOG.info("[forecast-verify] PASS: genuine live fit, compute, ×{}, energy, duration and tick rate", factor);
        }
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(target.getClass().getName() + "." + name);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
