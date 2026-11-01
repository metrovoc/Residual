package io.github.metrovoc.residual.agent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jetbrains.annotations.Nullable;

/**
 * What a program runs on: an agent, which walks to what it works on, or a program on a Port, which reaches it from where
 * it stands. Every act and check of the language goes through this, so one language runs on both.
 */
public interface Body {
    enum Arrival { HERE, ON_THE_WAY, LOST }

    ServerLevel world();

    /** The block it is at. */
    BlockPos place();

    Vec3 center();

    BlockPos home();

    List<BlockPos> marks();

    /** The way it faces. */
    Direction facing();

    /** A count of ticks that goes on while it is loaded. */
    int clock();

    /** Unique among bodies at work, to tell whose claim a block is. */
    int id();

    /** Whoever it works for, if they are here to come to. */
    @Nullable
    ServerPlayer boss();

    /** Who placed it, and so whose team it is on. */
    @Nullable
    UUID owner();

    /** Goes towards {@code target} until within {@code reach} blocks; a body with no legs is there if it reaches that far, and lost if not. */
    Arrival walk(BlockPos target, double reach);

    /** Stops where it is. */
    void stand();

    /** A step straight into the block next to it, as a tunnel is walked; nothing for a body with no legs. */
    default void stepInto(BlockPos next) {
    }

    /** Whether it can dig its way to what is out of reach. */
    default boolean tunnels() {
        return false;
    }

    /** Sets what its screen says it is doing. */
    void status(String key, Object... args);

    /** Takes no further step for a while. */
    void pause(int ticks);

    /** Stops pausing, to go on at once; returns the ticks it had left. */
    int unpause();

    /** Thinking or pausing: not free to take another step. */
    boolean busy();

    /** Says {@code what} to whoever is near. */
    void tell(Component what);

    /** Notes a line in its trace. */
    void trace(Trace.Kind kind, String step, String key, List<String> args);

    /** What it carries, in the first {@link #carry()} slots. */
    Container items();

    int carry();

    /** Puts {@code stack} into its hands; returns what didn't fit. */
    ItemStack keep(ItemStack stack);

    boolean full();

    /** Shows it worked on a place without going there, {@code n} of something moved. */
    default void reached(BlockPos pos, int n) {
    }

    /** Shows it did something with its hands at {@code pos}. */
    void handle(BlockPos pos);

    void swing();

    void look(Vec3 at);

    /** Shows it took {@code n} from what lay on the ground. */
    default void picked(ItemEntity lying, int n) {
    }

    /** The entity that acts, if it is one. */
    @Nullable
    Entity entity();

    /** Ticks an operation takes: a transfer of items, fluid or energy, or a block broken or placed by a {@code dig} or {@code place}. */
    int interval();

    /** Items an operation moves at most. */
    int itemsPerRun();

    /** Millibuckets an operation moves at most. */
    int fluidPerRun();

    /** Forge Energy an operation moves at most. */
    int energyPerRun();

    /** How far it reaches without walking, in blocks. */
    int reach();

    /** Whether it can act on a place, as far away as it may be: within its reach, or through the relay network. */
    boolean reaches(BlockPos target);

    /** Whether the relay network carries what it does, across dimensions too. */
    default boolean relayed() {
        return false;
    }

    /** The fluid it carries. */
    ResourceHandler<FluidResource> tank();

    /** The energy it holds. */
    EnergyHandler battery();

    /** Its own variables, kept as long as it stands. */
    Map<String, Value> vars();

    /** What its team's channels are kept under. */
    String team();

    /** The upgrades in it. */
    Container upgrades();

    /** What its filter's ghost slots show, if it has them. */
    List<ItemStack> ghosts();

    /** The model in its filter slot. */
    ItemStack filterModel();

    /** Gives out a redstone signal of this strength, for as long as it says so again, or 0 to stop. */
    void emit(int strength);

    /** Whether this operation found nothing to do, for a body that should not look again every tick. */
    void dry(boolean moved);

    /** Whether it can go to what it works on. */
    default boolean legs() {
        return false;
    }

    /** Whether it is in the air, and can go up and down. */
    default boolean flying() {
        return false;
    }

    /** Notes items moved, for what shows it at work. */
    default void moved(int n, boolean intoNetwork) {
    }

    /** How many doublings of speed its upgrades give what its hands do. */
    default int speed() {
        return 0;
    }

    /** The slot its hand is on. */
    int selected();

    void select(int slot);

    /** Turns to face a way. */
    void face(Direction way);

    /** Goes straight to a place, if it can. */
    default boolean teleport(BlockPos to) {
        return false;
    }

    /** Takes a new name. */
    default void rename(String name) {
    }

    /** Goes back to being an item, for a body that can. */
    default boolean retire() {
        return false;
    }
}
