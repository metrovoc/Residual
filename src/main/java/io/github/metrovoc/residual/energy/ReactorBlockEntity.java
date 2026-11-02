package io.github.metrovoc.residual.energy;

import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.item.CrystalItem;
import io.github.metrovoc.residual.item.LinkItem;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Played;
import io.github.metrovoc.residual.learn.Reactor;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.model.ModelLink;
import io.github.metrovoc.residual.model.Models;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.registry.ModItems;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * The Reactor Core: the controller block of a shell of Reactor Casing and Reactor Glass around Fuel Rods, from 3 to 5
 * blocks each way. It makes {@link #ROD_FE} FE a tick for each rod while the power is in its band, less off it, and burns a
 * Reactor Fuel in {@link #FUEL_LIFE} rod-ticks. A cycle of {@link Reactor#LOOKS} looks runs one after another, a look every
 * {@link #LOOK_EVERY} ticks, and at each look it waits for its controller to say what to do with the rods.
 *
 * <p>Only a controller that passes Containment is trusted to start it, and it stops itself rather than go on without one:
 * the rods go in, for {@link #TRIP_TICKS} ticks, when its gauge reads an end, when {@link #UNSURE_LOOKS} looks running its
 * controller has been under {@link #SURE} sure of what it chose or has not answered, or when its controller or its shell goes.
 * That is its fallback, a rule that runs nothing.
 */
public class ReactorBlockEntity extends PowerBlockEntity implements MenuProvider {
    public static final int ROD_FE = 20_000, LOOK_EVERY = 10, TRIP_TICKS = 600, FUEL_LIFE = 4_800;
    public static final int CAPACITY = 16_000_000, OUT = 1_048_576;
    public static final int FUEL = 0, CONTROLLER = 1, SLOTS = 2;
    /** Under this sure of its choice, a controller is left to the rule. */
    public static final float SURE = 0.4f;
    private static final int UNSURE_LOOKS = 3;
    /** The smallest and largest shell each way. */
    private static final int SMALLEST = 3, LARGEST = 5;
    /** Cycles its fuel stays at an age. */
    private static final int CYCLES_AN_AGE = 4;
    /** Why it is not running: {@link #RUNNING} when it is. */
    public static final int RUNNING = 0, NOT_BUILT = 1, NO_CONTROLLER = 2, NOT_TRUSTED = 3, NO_FUEL = 4, TRIPPED = 5, FULL = 6;

    private final Items items = new Items();
    private final Random rng = new Random();
    private int rods;
    /** Rod-ticks left in the fuel it took, and what share of a fuel's life is burnt. */
    private int burning;
    @Nullable
    private Reactor.Cycle cycle;
    private int untilLook;
    private boolean asking;
    private int request;
    private int unsure;
    private int waiting;
    private int cycles;
    private int tripped;
    /** Its shell's two far corners, once found. */
    @Nullable
    private BlockPos[] inside;
    private int status = NOT_BUILT;
    private int rate;
    private int lastHeld = -1;
    private float lastSure;
    private boolean shownBurning;
    private final ContainerData data = PowerMenu.data(this, () -> burning, () -> FUEL_LIFE, () -> rate / PowerMenu.REACTOR_RATE_UNIT, this::meltData, PowerMenu.REACTOR_DATA);

    public ReactorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REACTOR.get(), pos, state, CAPACITY, 0, OUT);
    }

    @Override
    public Role role() {
        return Role.GENERATOR;
    }

    public WorldlyContainer items() {
        return items;
    }

    public int status() {
        return status;
    }

    public int rods() {
        return rods;
    }

    public int rate() {
        return rate;
    }

    /** Finds the shell it is set in, and how many rods fill it: 0 if the shell is not whole. */
    private int findRods(ServerLevel level) {
        BlockPos at = worldPosition;
        Direction front = getBlockState().getValue(PowerDeviceBlock.FACING);
        // straight back across the inside to the far wall
        int depth = 0;
        BlockPos probe = at;
        while (true) {
            if (++depth >= LARGEST) {
                return 0;
            }
            probe = probe.relative(front.getOpposite());
            BlockState s = level.getBlockState(probe);
            if (s.getBlock() instanceof ReactorPartBlock) {
                break;
            }
            if (!(s.getBlock() instanceof ReactorRodBlock) && !s.isAir()) {
                return 0;
            }
        }
        // along the wall it is set in, each way, for as far as the wall goes
        int[] lo = {at.getX(), at.getY(), at.getZ()}, hi = lo.clone();
        for (Direction d : Direction.values()) {
            if (d.getAxis() == front.getAxis()) {
                continue;
            }
            int reach = 0;
            while (reach < LARGEST && level.getBlockState(at.relative(d, reach + 1)).getBlock() instanceof ReactorPartBlock) {
                reach++;
            }
            int i = d.getAxis().ordinal();
            lo[i] = Math.min(lo[i], at.get(d.getAxis()) + d.getAxisDirection().getStep() * reach);
            hi[i] = Math.max(hi[i], at.get(d.getAxis()) + d.getAxisDirection().getStep() * reach);
        }
        int across = front.getAxis().ordinal();
        lo[across] = Math.min(at.get(front.getAxis()), probe.get(front.getAxis()));
        hi[across] = Math.max(at.get(front.getAxis()), probe.get(front.getAxis()));
        for (int d = 0; d < 3; d++) {
            int size = hi[d] - lo[d] + 1;
            if (size < SMALLEST || size > LARGEST) {
                return 0;
            }
        }
        int found = 0;
        for (BlockPos p : BlockPos.betweenClosed(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2])) {
            boolean outer = p.getX() == lo[0] || p.getX() == hi[0] || p.getY() == lo[1] || p.getY() == hi[1] || p.getZ() == lo[2] || p.getZ() == hi[2];
            BlockState s = level.getBlockState(p);
            if (outer) {
                if (!p.equals(at) && !(s.getBlock() instanceof ReactorPartBlock)) {
                    return 0;
                }
            } else if (s.getBlock() instanceof ReactorRodBlock) {
                found++;
            } else if (!s.isAir()) {
                return 0;
            }
        }
        inside = new BlockPos[] {new BlockPos(lo[0], lo[1], lo[2]), new BlockPos(hi[0], hi[1], hi[2])};
        return found;
    }

    @Override
    protected void tick(ServerLevel level) {
        super.tick(level);
        if (Math.floorMod(level.getGameTime() + worldPosition.asLong(), 40) == 0) {
            rods = findRods(level);
            if (rods == 0 && cycle != null) {
                trip(level);
            }
        }
        if (tripped > 0) {
            tripped--;
            halt(TRIPPED);
            return;
        }
        if (rods == 0) {
            halt(NOT_BUILT);
            return;
        }
        ModelLink link = ModelLink.of(level, worldPosition, items.getItem(CONTROLLER));
        ModelInfo controller = link.info();
        if (controller == null) {
            // not there to ask: the rods go in, if it was running
            if (cycle != null) {
                trip(level);
            }
            halt(NO_CONTROLLER);
            return;
        }
        if (!controller.causal() || !controller.passes(Benchmark.REACTOR)) {
            halt(NOT_TRUSTED);
            return;
        }
        int power = rods * ROD_FE;
        if (energy().getAmountAsInt() > CAPACITY - power) {
            // nowhere for what it would make: it waits, and burns nothing meanwhile
            halt(FULL);
            return;
        }
        if (cycle == null) {
            if (burning <= 0 && !refuel()) {
                halt(NO_FUEL);
                return;
            }
            // its fuel ages from cycle to cycle, and then fresh fuel is drawn down the rods
            cycle = Reactor.cycle((cycles++ / CYCLES_AN_AGE) % Reactor.AGES.length(), rng);
            untilLook = 0;
            unsure = 0;
            waiting = 0;
        }
        if (asking && ++waiting > LOOK_EVERY * UNSURE_LOOKS) {
            // it has been asked and has not answered
            trip(level);
            halt(TRIPPED);
            return;
        }
        if (--untilLook <= 0 && !asking) {
            if (cycle.over()) {
                lastHeld = (int) Math.round(100 * cycle.score());
                grant(level);
                cycle = null;
                return;
            }
            waiting = 0;
            ask(level, link);
        }
        if (cycle == null) {
            return;
        }
        rate = power * share(cycle.power()) / 8;
        burning -= rods;
        if (burning <= 0) {
            refuel();
        }
        energy().set(energy().getAmountAsInt() + rate);
        status = RUNNING;
        show(level, true);
    }

    /** Eighths of its full power it makes at each reading of its gauge. */
    private static int share(int power) {
        return switch (power) {
            case 3, 4 -> 8;
            case 2, 5 -> 4;
            case 1, 6 -> 1;
            default -> 0;
        };
    }

    private boolean refuel() {
        ItemStack fuel = items.getItem(FUEL);
        if (fuel.isEmpty()) {
            return false;
        }
        fuel.shrink(1);
        burning += FUEL_LIFE;
        items.setChanged();
        return true;
    }

    private void ask(ServerLevel level, ModelLink link) {
        asking = true;
        int id = ++request;
        String prompt = Played.prompt(cycle);
        Function<CompiledGraph, Played.Choice> work = model -> Played.choose(model, prompt);
        Consumer<Played.@Nullable Choice> reply = choice -> {
            if (id != request || isRemoved()) {
                return;
            }
            asking = false;
            untilLook = LOOK_EVERY;
            if (cycle == null) {
                return;
            }
            if (choice == null) {
                unsure++;
            } else {
                lastSure = choice.sure();
                unsure = choice.sure() < SURE ? unsure + 1 : 0;
                cycle.act(choice.power(), rng);
            }
            if (unsure >= UNSURE_LOOKS || cycle.power() == 0 || cycle.power() == 7) {
                trip(level);
            }
        };
        link.submit(level, work, reply);
    }

    /** The rods go in: it stops at once, and waits out the poison before it can start again. */
    private void trip(ServerLevel level) {
        request++;
        asking = false;
        cycle = null;
        lastHeld = 0;
        tripped = TRIP_TICKS;
        level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1f, 0.6f);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1, worldPosition.getZ() + 0.5, 30, 0.6, 0.3, 0.6, 0.02);
    }

    private void halt(int why) {
        status = why;
        rate = 0;
        if (level instanceof ServerLevel server) {
            show(server, false);
        }
    }

    /** Lights its front and the rods behind the glass while it runs. */
    private void show(ServerLevel level, boolean on) {
        BlockState here = getBlockState();
        if (here.getValue(PowerDeviceBlock.WORKING) != on) {
            level.setBlock(worldPosition, here.setValue(PowerDeviceBlock.WORKING, on), Block.UPDATE_CLIENTS);
        }
        if (on != shownBurning && inside != null) {
            shownBurning = on;
            for (BlockPos p : BlockPos.betweenClosed(inside[0], inside[1])) {
                BlockState s = level.getBlockState(p);
                if (s.getBlock() instanceof ReactorRodBlock && s.getValue(ReactorRodBlock.BURNING) != on) {
                    level.setBlock(p, s.setValue(ReactorRodBlock.BURNING, on), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private void grant(ServerLevel level) {
        if (lastHeld >= 100 * Played.HELD) {
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(worldPosition.getCenter()) < 32 * 32) {
                    Awards.grant(player, "power/reactor");
                }
            }
        }
    }

    /** The cycle for its screen, as a Smelter's melt: looks taken, the last cycle's share held (+1, 0 for none), then the readings so far, five 3-bit bands to a slot, then why it is not running. */
    private int meltData(int i) {
        if (i == 0) {
            return cycle == null ? 0 : cycle.look();
        }
        if (i == 1) {
            return lastHeld + 1;
        }
        if (i == PowerMenu.MELT_DATA) {
            return status;
        }
        if (i == PowerMenu.MELT_DATA + 1) {
            return Math.round(lastSure * 100);
        }
        int packed = 0;
        if (cycle != null) {
            for (int k = 0; k < 5; k++) {
                int look = (i - 2) * 5 + k;
                if (look < cycle.look()) {
                    packed |= cycle.band(look) << (3 * k);
                }
            }
        }
        return packed;
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new PowerMenu(id, inventory, worldPosition, PowerMenu.Kind.REACTOR, items, data);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            Containers.dropContents(level, pos, items);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out.child("items"), items.getItems());
        out.putInt("burning", burning);
        out.putInt("tripped", tripped);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        in.child("items").ifPresent(c -> ContainerHelper.loadAllItems(c, items.getItems()));
        burning = in.getIntOr("burning", 0);
        tripped = in.getIntOr("tripped", 0);
    }

    /** Fuel goes in from any side; nothing comes out. */
    private final class Items extends SimpleContainer implements WorldlyContainer {
        Items() {
            super(SLOTS);
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return slot == FUEL ? stack.is(ModItems.REACTOR_FUEL.get()) : stack.getItem() instanceof CrystalItem || stack.getItem() instanceof LinkItem;
        }

        @Override
        public int[] getSlotsForFace(Direction side) {
            return new int[] {FUEL};
        }

        @Override
        public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
            return slot == FUEL && canPlaceItem(slot, stack);
        }

        @Override
        public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
            return false;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            ReactorBlockEntity.this.setChanged();
        }
    }
}
