package io.github.metrovoc.residual.agent;

import io.github.metrovoc.residual.item.ProgramCardItem;
import io.github.metrovoc.residual.net.ProgramSync;
import io.github.metrovoc.residual.storage.Flows;
import io.github.metrovoc.residual.storage.PortBlock;
import io.github.metrovoc.residual.storage.PortBlockEntity;
import io.github.metrovoc.residual.storage.PortProgramMenu;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * What runs on a Port: up to {@link #CARDS} Program Cards, each with its own program, and the upgrades, hands, tank and
 * battery they share. A Port with cards in it does what they say instead of what its mode says. Each program runs on the
 * Port's own {@link Hand}: it reaches what it works on from where it stands, and an operation takes the ticks and moves
 * the amounts its upgrades allow.
 */
public final class PortWork {
    public static final int CARDS = 9, HANDS = 9;
    /** Program lines of its trace it keeps. */
    private static final int TRACED = 10;
    /** Ticks between two looks for players watching it. */
    private static final int SYNC_EVERY = 10;
    /** Items a second that make the most of a Port. */
    private static final int FULL_RATE = 1280;

    private final PortBlockEntity port;
    public final SimpleContainer cards = new SimpleContainer(CARDS) {
        @Override
        public void setChanged() {
            super.setChanged();
            port.setChanged();
        }
    };
    public final SimpleContainer upgrades = new SimpleContainer(Upgrades.SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            battery.capacity(Upgrades.battery(this));
            port.setChanged();
        }
    };
    public final SimpleContainer hands = new SimpleContainer(HANDS) {
        @Override
        public void setChanged() {
            super.setChanged();
            port.setChanged();
        }
    };
    private final FluidStacksResourceHandler tank = new FluidStacksResourceHandler(1, Upgrades.TANK);
    private final Battery battery = new Battery();
    private final Map<String, Value> vars = new HashMap<>();
    private final Ownership ownership = new Ownership();
    private final Hand[] running = new Hand[CARDS];
    private int signal;
    private long workedAt = -1000;
    private final Afloat afloat = new Afloat();

    /** The energy a Port holds, as much as its energy upgrades have room for. */
    private static final class Battery extends SimpleEnergyHandler {
        Battery() {
            super(0, Integer.MAX_VALUE, Integer.MAX_VALUE);
        }

        void capacity(int capacity) {
            this.capacity = capacity;
            if (energy > capacity) {
                energy = capacity;
            }
        }
    }

    public PortWork(PortBlockEntity port) {
        this.port = port;
    }

    public Ownership ownership() {
        return ownership;
    }

    public EnergyHandler battery() {
        return battery;
    }

    public ResourceHandler<FluidResource> tank() {
        return tank;
    }

    /** Whether its programs have worked on something in the last second. */
    public boolean worked(long now) {
        return now - workedAt <= 20;
    }

    /** The signal its programs give out. */
    public int signal() {
        return signal;
    }

    /** Runs what its cards say; whether it has any written. */
    public boolean tick(ServerLevel level) {
        boolean any = false;
        for (int i = 0; i < CARDS; i++) {
            String text = ProgramCardItem.program(cards.getItem(i));
            if (text.isEmpty()) {
                running[i] = null;
                continue;
            }
            any = true;
            Hand hand = running[i];
            if (hand == null || !hand.text.equals(text)) {
                hand = new Hand(i, text, hand == null ? new Debug() : hand.debug, hand == null ? new ArrayList<>() : hand.log);
                running[i] = hand;
            }
            hand.tick(level);
        }
        if (level.getGameTime() % Afloat.EVERY == Math.floorMod(port.getBlockPos().asLong(), Afloat.EVERY)) {
            showStep(level);
        }
        if (level.getGameTime() % SYNC_EVERY == 0) {
            sync(level);
            if (any && ownership.owner() != null) {
                award(level);
            }
        }
        return any;
    }

    /** What floats over it: the first of its programs that is working on something, and how many more there are. */
    private void showStep(ServerLevel level) {
        Afloat.Step best = Afloat.Step.NONE;
        int shown = 0;
        for (Hand hand : running) {
            Afloat.Step step = hand == null || hand.stopped ? Afloat.Step.NONE : hand.work.onNow();
            if (!step.text().isEmpty()) {
                shown++;
                if (best.text().isEmpty() || step.kind() < best.kind()) {
                    best = step;
                }
            }
        }
        afloat.update(level, Vec3.atCenterOf(port.getBlockPos()), -1, port.getBlockPos(), best, Math.max(0, shown - 1));
    }

    /** What its owner, if they are here, has earned by what it runs. */
    private void award(ServerLevel level) {
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownership.owner());
        if (owner != null) {
            io.github.metrovoc.residual.Awards.grant(owner, "agent/port_program");
            if (port.movedLastSecond() >= FULL_RATE) {
                io.github.metrovoc.residual.Awards.grant(owner, "agent/full_rate");
            }
        }
    }

    /** The variables every card's program shares with @. */
    public Map<String, Value> vars() {
        return vars;
    }

    /** What a card's program has been doing. */
    public List<Trace> traced(int slot) {
        return running[slot] == null ? List.of() : running[slot].log;
    }

    /** What a variable of a card's program holds, as a player would read it. */
    public String variable(int slot, String name) {
        return running[slot] == null ? "" : running[slot].work.eval.get(name).show();
    }

    private void sync(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (p.containerMenu instanceof PortProgramMenu menu && menu.pos.equals(port.getBlockPos())) {
                Hand hand = running[menu.selected()];
                PacketDistributor.sendToPlayer(p, hand == null ? ProgramSync.none(port.getBlockPos(), menu.selected())
                        : ProgramSync.of(-1, port.getBlockPos(), menu.selected(), hand.work, hand.debug, hand.log, hand.status, hand.statusArgs));
            }
        }
    }

    /** Stops a card's program, or runs it from the top. */
    public void stop(int slot, boolean stop) {
        if (running[slot] != null) {
            running[slot].stopped = stop;
            if (!stop) {
                running[slot].restart();
            }
            running[slot].trace(Trace.Kind.NOTE, "", stop ? "residual.trace.stopped" : "residual.trace.program", List.of());
        }
    }

    /** What the debugger holds for a card's program, if it has one running. */
    @Nullable
    public Debug debugOf(int slot) {
        return running[slot] == null ? null : running[slot].debug;
    }

    public void debug(int slot, Debug.Move move, int number, String text) {
        if (running[slot] != null) {
            running[slot].debug.apply(move, number, text);
        }
    }

    /** Writes a program on the card in a slot, blank or not; false if there is none, or the text is no program. */
    public boolean write(int slot, String text, @Nullable ServerPlayer by) {
        Program.Parsed p = Program.parse(text);
        ItemStack card = cards.getItem(slot);
        if (!p.ok() || !(card.getItem() instanceof ProgramCardItem)) {
            return false;
        }
        ItemStack written = card.copyWithCount(1);
        written.set(io.github.metrovoc.residual.registry.ModComponents.PROGRAM.get(), p.program().toString());
        cards.setItem(slot, written);
        if (by != null) {
            io.github.metrovoc.residual.Awards.grant(by, "agent/port_program");
        }
        return true;
    }

    private void emit(int strength) {
        if (strength != signal && port.getLevel() != null) {
            signal = strength;
            port.getLevel().updateNeighborsAt(port.getBlockPos(), port.getBlockState().getBlock());
            port.setChanged();
        }
    }

    public void dropAll(Level level, BlockPos pos) {
        Containers.dropContents(level, pos, cards);
        Containers.dropContents(level, pos, upgrades);
        Containers.dropContents(level, pos, hands);
    }

    public void save(ValueOutput out) {
        ContainerHelper.saveAllItems(out.child("cards"), cards.getItems());
        ContainerHelper.saveAllItems(out.child("upgrades"), upgrades.getItems());
        ContainerHelper.saveAllItems(out.child("hands"), hands.getItems());
        tank.serialize(out.child("tank"));
        battery.serialize(out.child("battery"));
        Saved.write(out, vars);
        ownership.save(out);
        out.putInt("signal", signal);
    }

    public void load(ValueInput in) {
        in.child("cards").ifPresent(c -> ContainerHelper.loadAllItems(c, cards.getItems()));
        in.child("upgrades").ifPresent(c -> ContainerHelper.loadAllItems(c, upgrades.getItems()));
        in.child("hands").ifPresent(c -> ContainerHelper.loadAllItems(c, hands.getItems()));
        battery.capacity(Upgrades.battery(upgrades));
        in.child("tank").ifPresent(tank::deserialize);
        in.child("battery").ifPresent(battery::deserialize);
        Saved.read(in, vars);
        ownership.load(in);
        signal = in.getIntOr("signal", 0);
    }

    /** One program at work on the Port. */
    private final class Hand implements Body {
        final int slot;
        final String text;
        final Debug debug;
        final List<Trace> log;
        Running work;
        String status = "residual.agent.status.resting";
        List<String> statusArgs = List.of();
        boolean stopped;
        int pause;
        int dried;
        int selected;
        long shownAt;

        Hand(int slot, String text, Debug debug, List<Trace> log) {
            this.slot = slot;
            this.text = text;
            this.debug = debug;
            this.log = log;
            restart();
        }

        void restart() {
            Program.Parsed p = Program.parse(text);
            work = new Running(this, p.ok() ? p.program() : Program.of(), debug);
            if (!p.ok()) {
                trace(Trace.Kind.FAILED, Running.clip(text), p.problem().key(), List.of(p.problem().word(), String.valueOf(Program.LONGEST)));
            }
            pause = 0;
            stopped = false;
        }

        void tick(ServerLevel level) {
            if (stopped) {
                status("residual.agent.status.stopped");
                return;
            }
            if (pause > 0) {
                // what it listens for can cut a pause short
                work.poll(level);
            }
            if (pause > 0) {
                pause--;
                return;
            }
            work.tick(level);
            if (work.over()) {
                stopped = true;
                trace(Trace.Kind.NOTE, "", "residual.trace.finished", List.of());
            }
        }

        @Override
        public ServerLevel world() {
            return (ServerLevel) port.getLevel();
        }

        @Override
        public BlockPos place() {
            return port.getBlockPos();
        }

        @Override
        public Vec3 center() {
            return Vec3.atCenterOf(port.getBlockPos());
        }

        @Override
        public BlockPos home() {
            return port.getBlockPos();
        }

        @Override
        public List<BlockPos> marks() {
            return port.marks();
        }

        @Override
        public Direction facing() {
            return port.getBlockState().getValue(PortBlock.FACING);
        }

        @Override
        public int clock() {
            return (int) world().getGameTime();
        }

        @Override
        public int id() {
            return System.identityHashCode(this);
        }

        @Nullable
        @Override
        public ServerPlayer boss() {
            UUID owner = ownership.owner();
            return owner == null ? null : world().getServer().getPlayerList().getPlayer(owner);
        }

        @Nullable
        @Override
        public UUID owner() {
            return ownership.owner();
        }

        @Override
        public Arrival walk(BlockPos target, double reach) {
            return reaches(target) ? Arrival.HERE : Arrival.LOST;
        }

        @Override
        public void stand() {
        }

        @Override
        public void status(String key, Object... args) {
            status = key;
            statusArgs = Outcome.strings(args);
        }

        @Override
        public void pause(int ticks) {
            pause = Math.max(pause, ticks);
        }

        @Override
        public int unpause() {
            int left = pause;
            pause = 0;
            return left;
        }

        @Override
        public boolean busy() {
            return pause > 0;
        }

        @Override
        public void tell(Component what) {
            Component line = Component.literal("<").append(Component.translatable("block.residual.port")).append("> ").append(what);
            Vec3 at = center();
            for (ServerPlayer p : world().players()) {
                if (p.distanceToSqr(at) < 48 * 48) {
                    p.sendSystemMessage(line);
                }
            }
        }

        @Override
        public void trace(Trace.Kind kind, String step, String key, List<String> args) {
            Trace.note(log, TRACED, kind, step, key, args);
        }

        @Override
        public Container items() {
            return hands;
        }

        @Override
        public int carry() {
            return HANDS;
        }

        @Override
        public ItemStack keep(ItemStack stack) {
            for (int i = 0; i < HANDS && !stack.isEmpty(); i++) {
                ItemStack in = hands.getItem(i);
                if (!in.isEmpty() && ItemStack.isSameItemSameComponents(in, stack) && in.getCount() < in.getMaxStackSize()) {
                    int moved = Math.min(stack.getCount(), in.getMaxStackSize() - in.getCount());
                    in.grow(moved);
                    stack.shrink(moved);
                }
            }
            for (int i = 0; i < HANDS && !stack.isEmpty(); i++) {
                if (hands.getItem(i).isEmpty()) {
                    hands.setItem(i, stack.copy());
                    stack.setCount(0);
                }
            }
            hands.setChanged();
            return stack;
        }

        @Override
        public boolean full() {
            for (int i = 0; i < HANDS; i++) {
                if (hands.getItem(i).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void handle(BlockPos pos) {
        }

        @Override
        public void reached(BlockPos pos, int n) {
            long now = port.getLevel().getGameTime();
            workedAt = now;
            if (now - shownAt >= Flows.EVERY) {
                shownAt = now;
                Flows.send(world(), port.getBlockPos(), pos, true, n, ItemResource.EMPTY, FluidResource.EMPTY, false);
            }
        }

        @Override
        public void swing() {
        }

        @Override
        public void look(Vec3 at) {
        }

        @Nullable
        @Override
        public Entity entity() {
            return null;
        }

        @Override
        public int interval() {
            return Upgrades.interval(upgrades);
        }

        @Override
        public int itemsPerRun() {
            return Upgrades.items(upgrades);
        }

        @Override
        public int fluidPerRun() {
            return Upgrades.fluid(upgrades);
        }

        @Override
        public int energyPerRun() {
            return Upgrades.energy(upgrades);
        }

        @Override
        public int speed() {
            return Upgrades.count(upgrades, Upgrades.Kind.SPEED);
        }

        @Override
        public int reach() {
            return Upgrades.reach(upgrades);
        }

        @Override
        public boolean reaches(BlockPos target) {
            int r = reach();
            if (port.getBlockPos().distSqr(target) <= (double) r * r) {
                return true;
            }
            // the relay network carries what is out of reach, for energy
            return relayed() && Reach.covered(world(), upgrades, target) && Reach.spend(battery, Upgrades.RELAY_COST);
        }

        @Override
        public boolean relayed() {
            return Reach.relayed(world(), port.getBlockPos(), upgrades);
        }

        @Override
        public ResourceHandler<FluidResource> tank() {
            return tank;
        }

        @Override
        public EnergyHandler battery() {
            return battery;
        }

        @Override
        public Map<String, Value> vars() {
            return vars;
        }

        @Override
        public Container upgrades() {
            return upgrades;
        }

        @Override
        public String team() {
            return Teams.of(world().getServer(), ownership.owner());
        }

        @Override
        public List<ItemStack> ghosts() {
            return port.filterStacks();
        }

        @Override
        public ItemStack filterModel() {
            return port.model().getItem(0);
        }

        @Override
        public void emit(int strength) {
            PortWork.this.emit(strength);
        }

        @Override
        public void dry(boolean moved) {
            dried = moved ? 0 : dried + 1;
            if (dried >= 3) {
                pause(10);
            }
        }

        @Override
        public void moved(int n, boolean intoNetwork) {
            port.moved(n, intoNetwork);
        }

        @Override
        public int selected() {
            return selected;
        }

        @Override
        public void select(int slot) {
            selected = Math.clamp(slot, 0, HANDS - 1);
        }

        @Override
        public void face(Direction way) {
            port.getLevel().setBlock(port.getBlockPos(), port.getBlockState().setValue(PortBlock.FACING, way), Block.UPDATE_ALL);
        }

        @Override
        public void rename(String name) {
            vars.put("@name", Value.of(name));
        }

        @Override
        public boolean retire() {
            stopped = true;
            return true;
        }
    }
}
