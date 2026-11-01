package io.github.metrovoc.residual.agent;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jetbrains.annotations.Nullable;

/**
 * What a program holds in its variables and works out in its expressions: numbers, text, true and false, items with
 * counts and components, blocks, fluids, positions, areas, entities, lists, and filters.
 */
public sealed interface Value {
    Nothing NONE = new Nothing();
    Flag TRUE = new Flag(true);
    Flag FALSE = new Flag(false);

    record Nothing() implements Value {
    }

    record Num(double v) implements Value {
    }

    record Text(String v) implements Value {
    }

    record Flag(boolean v) implements Value {
    }

    /** Items: a kind with its components, and how many. */
    record Stack(ItemStack stack) implements Value {
    }

    record Solid(BlockState state) implements Value {
    }

    record Liquid(FluidResource fluid, long amount) implements Value {
    }

    /**
     * A block's place: where it is, the face things go through (null for none in particular), and its dimension (null for
     * the one the body is in).
     */
    record Place(BlockPos pos, @Nullable Direction face, @Nullable ResourceKey<Level> dimension) implements Value {
        public Place(BlockPos pos) {
            this(pos, null, null);
        }
    }

    /** A word for where things are that is no place in the world: the network, its own hands, whoever owns it. */
    record Spot(String word) implements Value {
    }

    record Region(Area area) implements Value {
        /** An area as a value; one too big to hold is a step's failure. */
        static Value of(Area area) {
            if (area.volume() > Area.MOST) {
                throw new Eval.Fault("residual.program.failed.area_too_big", area.volume(), Area.MOST);
            }
            return new Region(area);
        }
    }

    record Creature(Entity entity) implements Value {
    }

    record Many(List<Value> items) implements Value {
    }

    /** A filter: what passes through it. */
    record Sieve(Picker picker) implements Value {
    }

    static Value of(boolean v) {
        return v ? TRUE : FALSE;
    }

    static Value of(double v) {
        return new Num(v);
    }

    static Value of(String v) {
        return new Text(v);
    }

    /** Whether it counts as true in an {@code if}: anything but false, nothing, zero and what is empty. */
    default boolean truth() {
        return switch (this) {
            case Nothing n -> false;
            case Flag f -> f.v();
            case Num n -> n.v() != 0;
            case Text t -> !t.v().isEmpty();
            case Many m -> !m.items().isEmpty();
            case Stack s -> !s.stack().isEmpty();
            case Liquid l -> l.amount() > 0;
            default -> true;
        };
    }

    /** How it reads to a player: {@code 3}, {@code hello}, {@code 5 iron ingot}. */
    default String show() {
        return switch (this) {
            case Nothing n -> "nothing";
            case Num n -> number(n.v());
            case Text t -> t.v();
            case Flag f -> f.v() ? "true" : "false";
            case Stack s -> s.stack().isEmpty() ? "nothing" : s.stack().getCount() + " " + Things.name(s.stack());
            case Solid s -> BuiltInRegistries.BLOCK.getKey(s.state().getBlock()).getPath().replace('_', ' ');
            case Liquid l -> l.amount() + " mb " + BuiltInRegistries.FLUID.getKey(l.fluid().getFluid()).getPath().replace('_', ' ');
            case Place p -> "pos(" + p.pos().getX() + ", " + p.pos().getY() + ", " + p.pos().getZ() + ")";
            case Spot s -> s.word();
            case Region r -> r.area().toString();
            case Creature c -> c.entity().getName().getString().toLowerCase(Locale.ROOT);
            case Many m -> m.items().stream().map(Value::show).collect(Collectors.joining(", ", "[", "]"));
            case Sieve s -> s.picker().thing();
        };
    }

    /**
     * Written so that a program could read it back, which is how a body's variables and a team's channels are saved: an
     * entity or a filter comes back as nothing, and an item as its kind and count.
     */
    default String source() {
        return switch (this) {
            case Text t -> '"' + t.v().replace("\"", "'") + '"';
            case Stack s -> s.stack().isEmpty() ? "nothing" : "item(" + Things.name(s.stack()) + ", " + s.stack().getCount() + ")";
            case Place p -> "pos(" + p.pos().getX() + ", " + p.pos().getY() + ", " + p.pos().getZ() + ")";
            case Many m -> m.items().stream().map(Value::source).collect(Collectors.joining(", ", "[", "]"));
            case Region r -> r.area().toString();
            case Creature c -> "nothing";
            case Sieve s -> "nothing";
            case Solid s -> "nothing";
            case Liquid l -> "nothing";
            default -> show();
        };
    }

    /** {@code 3} rather than {@code 3.0}, and at most four places after the point. */
    static String number(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) {
            return Long.toString((long) v);
        }
        String s = String.format(Locale.ROOT, "%.4f", v);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
