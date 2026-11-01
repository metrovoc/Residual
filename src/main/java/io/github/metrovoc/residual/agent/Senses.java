package io.github.metrovoc.residual.agent;

import io.github.metrovoc.residual.block.FabricatorBlockEntity;
import io.github.metrovoc.residual.energy.PowerNetwork;
import io.github.metrovoc.residual.energy.PowerNode;
import io.github.metrovoc.residual.energy.SmelterBlockEntity;
import io.github.metrovoc.residual.storage.CraftJob;
import io.github.metrovoc.residual.storage.CraftJobs;
import io.github.metrovoc.residual.storage.CraftingData;
import io.github.metrovoc.residual.storage.Node;
import io.github.metrovoc.residual.storage.PortBlockEntity;
import io.github.metrovoc.residual.storage.StorageNetwork;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.level.NoteBlockEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

/**
 * What a program can find out about the world and work with in it: places and areas, filters, the properties of items,
 * blocks, fluids and entities, and the inventories, tanks, energy, redstone, machines, network and world around it. All
 * of them only look.
 */
public final class Senses {
    private Senses() {
    }

    /** Asks for its functions to be added to the language's list, in the order the palette shows them. */
    static void install() {
        places();
        areas();
        filters();
        inventories();
        tanks();
        energy();
        redstone();
        blocks();
        entities();
        items();
        fluids();
        machines();
        world();
        network();
        self();
    }

    private static double n(List<Value> a, int i) {
        return Eval.num(a.get(i));
    }

    private static String t(List<Value> a, int i) {
        return Eval.text(a.get(i));
    }

    /** The word in argument {@code i}, if there are that many, or {@code otherwise}. */
    private static String word(List<Value> a, int i, String otherwise) {
        return a.size() > i ? t(a, i) : otherwise;
    }

    @Nullable
    private static Value.Place place(Eval e, Value v) {
        return Places.place(e.body, v);
    }

    /** The place in argument {@code i}, or where the body stands. */
    private static BlockPos at(Eval e, List<Value> a, int i) {
        Value.Place p = a.size() > i ? place(e, a.get(i)) : null;
        return p == null ? e.body.place() : p.pos();
    }

    /** The level a value's place is in. */
    private static ServerLevel levelOf(Eval e, @Nullable Value.Place p) {
        ServerLevel level = p == null ? null : Places.level(e.body, p);
        return level == null ? e.body.world() : level;
    }

    /** A place's level, once we know it is loaded, else null. */
    @Nullable
    private static ServerLevel loaded(Eval e, @Nullable Value.Place p) {
        ServerLevel level = p == null ? null : Places.level(e.body, p);
        return level != null && level.isLoaded(p.pos()) ? level : null;
    }

    /** The direction an argument names, as a word as it is written: {@code front}, {@code north}. */
    private static Direction direction(Eval e, Expr x) {
        String word = switch (x) {
            case Expr.Spot s -> s.word();
            case Expr.Words w -> w.words();
            default -> Eval.text(e.eval(x));
        };
        return Places.side(e.body.facing(), word);
    }

    private static double distance(Value.Place a, BlockPos b) {
        return Math.sqrt(a.pos().distSqr(b));
    }

    private static void places() {
        String g = "places";
        Ops.def(g, "pos", 3, 4, (e, a) -> {
            ResourceKey<Level> dimension = null;
            if (a.size() > 3) {
                Identifier id = Identifier.tryParse(t(a, 3).replace(' ', '_'));
                dimension = id == null ? null : ResourceKey.create(Registries.DIMENSION, id);
            }
            return new Value.Place(BlockPos.containing(n(a, 0), n(a, 1), n(a, 2)), null, dimension);
        });
        Ops.def(g, "mark", 1, 1, (e, a) -> {
            Value.Place p = place(e, Value.of(n(a, 0)));
            return p == null ? Value.NONE : p;
        });
        Ops.def(g, "marks", 0, 0, (e, a) -> new Value.Many(e.body.marks().stream().map(p -> (Value) new Value.Place(p)).toList()));
        Ops.def(g, "x", 1, 1, (e, a) -> Value.of(at(e, a, 0).getX()));
        Ops.def(g, "y", 1, 1, (e, a) -> Value.of(at(e, a, 0).getY()));
        Ops.def(g, "z", 1, 1, (e, a) -> Value.of(at(e, a, 0).getZ()));
        Ops.lazy(g, "offset", 2, 3, (e, args) -> {
            Value v = e.eval(args.get(0));
            Value.Place p = place(e, v);
            if (p == null) {
                return Value.NONE;
            }
            int by = args.size() > 2 ? (int) Eval.num(e.eval(args.get(2))) : 1;
            return new Value.Place(p.pos().relative(direction(e, args.get(1)), by), p.face(), p.dimension());
        });
        Ops.lazy(g, "side", 2, 2, (e, args) -> {
            Value.Place p = place(e, e.eval(args.get(0)));
            return p == null ? Value.NONE : new Value.Place(p.pos(), direction(e, args.get(1)), p.dimension());
        });
        Ops.def(g, "distance", 1, 2, (e, a) -> {
            Value.Place p = place(e, a.getFirst());
            return p == null ? Value.of(-1) : Value.of(distance(p, at(e, a, 1)));
        });
        Ops.def(g, "nearest", 1, 1, (e, a) -> byDistance(e, a.getFirst(), 1));
        Ops.def(g, "furthest", 1, 1, (e, a) -> byDistance(e, a.getFirst(), -1));
        Ops.def(g, "section", 1, 1, (e, a) -> new Value.Spot("owner:" + t(a, 0)));
    }

    private static Value byDistance(Eval e, Value list, int sign) {
        List<Value> out = new ArrayList<>(e.list(list));
        BlockPos at = e.body.place();
        out.sort(Comparator.comparingDouble(v -> {
            Value.Place p = place(e, v);
            return p == null ? Double.MAX_VALUE : sign * p.pos().distSqr(at);
        }));
        return new Value.Many(List.copyOf(out));
    }

    private static void areas() {
        String g = "areas";
        Ops.def(g, "box", 2, 3, (e, a) -> Value.Region.of(Area.box(at(e, a, 0), at(e, a, 1), word(a, 2, "filled"))));
        Ops.def(g, "cylinder", 2, 4, (e, a) -> Value.Region.of(Area.cylinder(at(e, a, 0), at(e, a, 1), word(a, 2, "filled"), word(a, 3, "y").charAt(0))));
        Ops.def(g, "grid", 3, 3, (e, a) -> Value.Region.of(Area.grid(at(e, a, 0), at(e, a, 1), (int) n(a, 2))));
        Ops.def(g, "line", 2, 2, (e, a) -> Value.Region.of(Area.line(at(e, a, 0), at(e, a, 1))));
        Ops.def(g, "pyramid", 2, 4, (e, a) -> Value.Region.of(Area.pyramid(at(e, a, 0), at(e, a, 1), word(a, 2, "y").charAt(0), word(a, 3, "filled"))));
        Ops.def(g, "sphere", 2, 3, (e, a) -> Value.Region.of(Area.sphere(at(e, a, 0), at(e, a, 1), word(a, 2, "filled"))));
        Ops.def(g, "torus", 2, 4, (e, a) -> Value.Region.of(Area.torus(at(e, a, 0), at(e, a, 1), word(a, 2, "y").charAt(0), word(a, 3, "filled"))));
        Ops.def(g, "wall", 2, 3, (e, a) -> Value.Region.of(Area.wall(at(e, a, 0), at(e, a, 1), word(a, 2, "y").charAt(0))));
        Ops.def(g, "near", 1, 2, (e, a) -> {
            int r = (int) n(a, 0);
            BlockPos c = at(e, a, 1);
            return Value.Region.of(Area.box(c.offset(-r, -r, -r), c.offset(r, r, r), "filled"));
        });
    }

    /** {@code random(a, b, n)}, which shares its name with the random number. */
    static Value randomArea(Eval e, List<Value> a) {
        return Value.Region.of(Area.random(at(e, a, 0), at(e, a, 1), (int) n(a, 2), new java.util.Random()));
    }

    private static void filters() {
        String g = "filters";
        Ops.def(g, "tag", 1, 1, (e, a) -> new Value.Sieve(Picks.tag(t(a, 0))));
        Ops.def(g, "mod", 1, 1, (e, a) -> new Value.Sieve(Picks.mod(t(a, 0))));
        Ops.def(g, "regex", 1, 1, (e, a) -> new Value.Sieve(Picks.regex(t(a, 0))));
        Ops.def(g, "kind", 1, 1, (e, a) -> new Value.Sieve(Picks.kind(e.body, t(a, 0))));
        Ops.def(g, "ghosts", 0, 0, (e, a) -> {
            List<Picker> ghosts = new ArrayList<>();
            for (ItemStack s : e.body.ghosts()) {
                if (!s.isEmpty()) {
                    ghosts.add(Picks.same(s));
                }
            }
            return new Value.Sieve(ghosts.isEmpty() ? Picks.ALL : Picks.anyOf(ghosts, "ghosts()"));
        });
        Ops.def(g, "item", 1, 2, (e, a) -> {
            ItemStack s = stack(a.getFirst());
            return new Value.Stack(s.copyWithCount(a.size() > 1 ? (int) n(a, 1) : Math.max(1, s.getCount())));
        });
        Ops.def(g, "other_than", 1, 1, (e, a) -> new Value.Sieve(Picks.not(Picks.of(e, a.getFirst()))));
        Ops.def(g, "passes", 2, 2, (e, a) -> {
            Picker p = Picks.of(e, a.getFirst());
            return Value.of(switch (a.get(1)) {
                case Value.Stack s -> p.passes(s.stack());
                case Value.Solid s -> p.passes(s.state());
                case Value.Liquid l -> p.passes(l.fluid());
                case Value.Creature c -> p.passes(c.entity());
                default -> p.passes(stack(a.get(1)));
            });
        });
    }

    // ------------------------------------------------------------ inventories

    private static void inventories() {
        String g = "inventories";
        Ops.lazy(g, "count", 1, 2, (e, args) -> {
            Value first = e.eval(args.getFirst());
            if (first instanceof Value.Many m) {
                return Value.of(countList(e, m.items(), args.size() > 1 ? args.get(1) : null));
            }
            Places.Ends ends = Places.items(e.body, first);
            Picker pick = args.size() > 1 ? Picks.of(e, e.eval(args.get(1))) : Picks.ALL;
            return Value.of(ends == null ? 0 : count(ends, pick));
        });
        Ops.def(g, "items", 1, 1, (e, a) -> {
            Places.Ends ends = Places.items(e.body, a.getFirst());
            List<Value> out = new ArrayList<>();
            if (ends != null) {
                ends.forEach((kind, n) -> out.add(new Value.Stack(kind.toStack((int) Math.min(n, Integer.MAX_VALUE)))));
            }
            return new Value.Many(List.copyOf(out));
        });
        Ops.def(g, "slot", 2, 2, (e, a) -> {
            Places.Ends ends = Places.items(e.body, a.getFirst());
            int i = (int) n(a, 1) - 1;
            return ends == null || i < 0 || i >= ends.slots() ? Value.NONE : new Value.Stack(ends.slot(i).copy());
        });
        Ops.def(g, "slots", 1, 1, (e, a) -> {
            Places.Ends ends = Places.items(e.body, a.getFirst());
            return Value.of(ends == null ? 0 : Math.max(0, ends.slots()));
        });
        Ops.def(g, "slots_used", 1, 1, (e, a) -> {
            Places.Ends ends = Places.items(e.body, a.getFirst());
            return Value.of(ends == null ? 0 : used(ends));
        });
        Ops.def(g, "fill", 1, 1, (e, a) -> {
            if (a.getFirst() instanceof Value.Stack s) {
                return Value.of(fluidFill(s.stack()));
            }
            Places.Ends ends = Places.items(e.body, a.getFirst());
            int slots = ends == null ? 0 : ends.slots();
            return Value.of(slots <= 0 ? 0 : 100.0 * used(ends) / slots);
        });
        Ops.def(g, "is_inventory", 1, 1, (e, a) -> Value.of(Places.items(e.body, a.getFirst()) != null));
        Ops.def(g, "inventory_full", 1, 1, (e, a) -> {
            Places.Ends ends = Places.items(e.body, a.getFirst());
            return Value.of(ends != null && ends.slots() > 0 && used(ends) >= ends.slots());
        });
        Ops.def(g, "inventory_empty", 1, 1, (e, a) -> {
            Places.Ends ends = Places.items(e.body, a.getFirst());
            return Value.of(ends != null && used(ends) == 0);
        });
    }

    private static double countList(Eval e, List<Value> list, @Nullable Expr where) {
        if (where == null) {
            return list.size();
        }
        int n = 0;
        for (Value v : list) {
            if (e.with("$it", v, where).truth()) {
                n++;
            }
        }
        return n;
    }

    private static double count(Places.Ends ends, Picker pick) {
        double[] n = {0};
        ends.forEach((kind, amount) -> {
            if (pick.passes(kind.toStack())) {
                n[0] += amount;
            }
        });
        return n[0];
    }

    private static int used(Places.Ends ends) {
        int slots = ends.slots();
        if (slots < 0) {
            int[] n = {0};
            ends.forEach((kind, amount) -> n[0]++);
            return n[0];
        }
        int n = 0;
        for (int i = 0; i < slots; i++) {
            n += ends.slot(i).isEmpty() ? 0 : 1;
        }
        return n;
    }

    // ------------------------------------------------------------ tanks

    private static void tanks() {
        String g = "tanks";
        Ops.def(g, "fluid_amount", 1, 2, (e, a) -> {
            ResourceHandler<FluidResource> h = Places.fluids(e.body, a.getFirst());
            if (h == null) {
                return Value.of(0);
            }
            if (a.size() > 1) {
                int i = (int) n(a, 1) - 1;
                return Value.of(i < 0 || i >= h.size() ? 0 : h.getAmountAsLong(i));
            }
            double sum = 0;
            for (int i = 0; i < h.size(); i++) {
                sum += h.getAmountAsLong(i);
            }
            return Value.of(sum);
        });
        Ops.def(g, "fluid_capacity", 1, 2, (e, a) -> {
            ResourceHandler<FluidResource> h = Places.fluids(e.body, a.getFirst());
            if (h == null) {
                return Value.of(0);
            }
            if (a.size() > 1) {
                int i = (int) n(a, 1) - 1;
                return Value.of(i < 0 || i >= h.size() ? 0 : h.getCapacityAsLong(i, h.getResource(i)));
            }
            double sum = 0;
            for (int i = 0; i < h.size(); i++) {
                sum += h.getCapacityAsLong(i, h.getResource(i));
            }
            return Value.of(sum);
        });
        Ops.def(g, "fluid_fill", 1, 2, (e, a) -> {
            ResourceHandler<FluidResource> h = Places.fluids(e.body, a.getFirst());
            if (h == null || h.size() == 0) {
                return Value.of(0);
            }
            double have = 0, room = 0;
            int only = a.size() > 1 ? (int) n(a, 1) - 1 : -1;
            for (int i = 0; i < h.size(); i++) {
                if (only < 0 || i == only) {
                    have += h.getAmountAsLong(i);
                    room += h.getCapacityAsLong(i, h.getResource(i));
                }
            }
            return Value.of(room <= 0 ? 0 : 100 * have / room);
        });
        Ops.def(g, "tanks", 1, 1, (e, a) -> {
            ResourceHandler<FluidResource> h = Places.fluids(e.body, a.getFirst());
            return Value.of(h == null ? 0 : h.size());
        });
        Ops.def(g, "fluids", 1, 1, (e, a) -> {
            ResourceHandler<FluidResource> h = Places.fluids(e.body, a.getFirst());
            List<Value> out = new ArrayList<>();
            for (int i = 0; h != null && i < h.size(); i++) {
                if (!h.getResource(i).isEmpty()) {
                    out.add(new Value.Liquid(h.getResource(i), h.getAmountAsLong(i)));
                }
            }
            return new Value.Many(List.copyOf(out));
        });
        Ops.def(g, "fluid", 1, 2, (e, a) -> {
            ResourceHandler<FluidResource> h = Places.fluids(e.body, a.getFirst());
            int i = a.size() > 1 ? (int) n(a, 1) - 1 : 0;
            return h == null || i < 0 || i >= h.size() || h.getResource(i).isEmpty() ? Value.NONE : new Value.Liquid(h.getResource(i), h.getAmountAsLong(i));
        });
        Ops.def(g, "block_fluid", 1, 1, (e, a) -> {
            Value.Place p = place(e, a.getFirst());
            ServerLevel level = loaded(e, p);
            FluidState f = level == null ? null : level.getFluidState(p.pos());
            return f == null || f.isEmpty() ? Value.NONE : new Value.Liquid(FluidResource.of(f.getType()), f.isSource() ? 1000 : 0);
        });
        Ops.def(g, "is_tank", 1, 1, (e, a) -> Value.of(Places.fluids(e.body, a.getFirst()) != null));
        Ops.def(g, "tank_full", 1, 1, (e, a) -> {
            ResourceHandler<FluidResource> h = Places.fluids(e.body, a.getFirst());
            if (h == null) {
                return Value.FALSE;
            }
            for (int i = 0; i < h.size(); i++) {
                if (h.getAmountAsLong(i) < h.getCapacityAsLong(i, h.getResource(i))) {
                    return Value.FALSE;
                }
            }
            return Value.TRUE;
        });
        Ops.def(g, "tank_empty", 1, 1, (e, a) -> {
            ResourceHandler<FluidResource> h = Places.fluids(e.body, a.getFirst());
            if (h == null) {
                return Value.FALSE;
            }
            for (int i = 0; i < h.size(); i++) {
                if (h.getAmountAsLong(i) > 0) {
                    return Value.FALSE;
                }
            }
            return Value.TRUE;
        });
    }

    // ------------------------------------------------------------ energy

    private static void energy() {
        String g = "energy";
        Ops.def(g, "energy", 1, 1, (e, a) -> {
            EnergyHandler h = Places.energy(e.body, a.getFirst());
            return Value.of(h == null ? 0 : h.getAmountAsLong());
        });
        Ops.def(g, "energy_capacity", 1, 1, (e, a) -> {
            EnergyHandler h = Places.energy(e.body, a.getFirst());
            return Value.of(h == null ? 0 : h.getCapacityAsLong());
        });
        Ops.def(g, "energy_fill", 1, 1, (e, a) -> {
            if (a.getFirst() instanceof Value.Stack s) {
                return Value.of(energyFill(s.stack()));
            }
            EnergyHandler h = Places.energy(e.body, a.getFirst());
            return Value.of(h == null || h.getCapacityAsLong() == 0 ? 0 : 100.0 * h.getAmountAsLong() / h.getCapacityAsLong());
        });
        Ops.def(g, "is_energy", 1, 1, (e, a) -> Value.of(Places.energy(e.body, a.getFirst()) != null));
        Ops.def(g, "energy_full", 1, 1, (e, a) -> {
            EnergyHandler h = Places.energy(e.body, a.getFirst());
            return Value.of(h != null && h.getAmountAsLong() >= h.getCapacityAsLong());
        });
        Ops.def(g, "energy_empty", 1, 1, (e, a) -> {
            EnergyHandler h = Places.energy(e.body, a.getFirst());
            return Value.of(h != null && h.getAmountAsLong() == 0);
        });
        Ops.def(g, "can_extract", 1, 1, (e, a) -> {
            EnergyHandler h = Places.energy(e.body, a.getFirst());
            if (h == null) {
                return Value.FALSE;
            }
            try (Transaction tx = Transaction.openRoot()) {
                return Value.of(h.extract(1, tx) > 0);
            }
        });
        Ops.def(g, "can_insert", 1, 1, (e, a) -> {
            EnergyHandler h = Places.energy(e.body, a.getFirst());
            if (h == null) {
                return Value.FALSE;
            }
            try (Transaction tx = Transaction.openRoot()) {
                return Value.of(h.insert(1, tx) > 0);
            }
        });
        Ops.def(g, "power_stored", 1, 1, (e, a) -> power(e, a.getFirst(), 0));
        Ops.def(g, "power_capacity", 1, 1, (e, a) -> power(e, a.getFirst(), 1));
        Ops.def(g, "power_cells", 1, 1, (e, a) -> power(e, a.getFirst(), 2));
        Ops.def(g, "power_drawn", 1, 1, (e, a) -> power(e, a.getFirst(), 3));
    }

    /** What a power network holds and could, among its cells; how many cells it has; what its machines drew last tick. */
    private static Value power(Eval e, Value v, int which) {
        Value.Place p = place(e, v);
        ServerLevel level = loaded(e, p);
        if (level == null || !(level.getBlockState(p.pos()).getBlock() instanceof PowerNode)) {
            return Value.of(0);
        }
        return Value.of(PowerNetwork.at(level, p.pos()).stored()[which]);
    }

    // ------------------------------------------------------------ redstone

    private static void redstone() {
        String g = "redstone";
        Ops.def(g, "redstone", 1, 1, (e, a) -> {
            ServerLevel level = loaded(e, place(e, a.getFirst()));
            return Value.of(level == null ? 0 : level.getBestNeighborSignal(at(e, a, 0)));
        });
        Ops.def(g, "signal", 1, 1, (e, a) -> {
            ServerLevel level = loaded(e, place(e, a.getFirst()));
            return Value.of(level != null && level.hasNeighborSignal(at(e, a, 0)));
        });
        Ops.def(g, "signal_high", 1, 1, (e, a) -> {
            ServerLevel level = loaded(e, place(e, a.getFirst()));
            return Value.of(level != null && level.getBestNeighborSignal(at(e, a, 0)) >= 15);
        });
        Ops.def(g, "signal_low", 1, 1, (e, a) -> {
            ServerLevel level = loaded(e, place(e, a.getFirst()));
            return Value.of(level == null || level.getBestNeighborSignal(at(e, a, 0)) == 0);
        });
        Ops.def(g, "comparator", 1, 1, (e, a) -> {
            Value.Place p = place(e, a.getFirst());
            ServerLevel level = loaded(e, p);
            if (level == null) {
                return Value.of(0);
            }
            BlockState s = level.getBlockState(p.pos());
            return Value.of(s.hasAnalogOutputSignal() ? s.getAnalogOutputSignal(level, p.pos(), Direction.NORTH) : 0);
        });
        Ops.def(g, "clock", 1, 3, (e, a) -> {
            int every = Math.max(1, (int) n(a, 0)), length = a.size() > 1 ? (int) n(a, 1) : 1, offset = a.size() > 2 ? (int) n(a, 2) : 0;
            return Value.of(Math.floorMod(e.body.world().getGameTime() - offset, every) < length);
        });
    }

    // ------------------------------------------------------------ blocks

    @Nullable
    private static BlockState solid(Eval e, Value v) {
        if (v instanceof Value.Solid s) {
            return s.state();
        }
        Value.Place p = place(e, v);
        ServerLevel level = loaded(e, p);
        return level == null ? null : level.getBlockState(p.pos());
    }

    private static void blocks() {
        String g = "blocks";
        Ops.def(g, "block", 1, 1, (e, a) -> {
            BlockState s = solid(e, a.getFirst());
            return s == null ? Value.NONE : new Value.Solid(s);
        });
        Ops.def(g, "has_block", 1, 1, (e, a) -> {
            BlockState s = solid(e, a.getFirst());
            return Value.of(s != null && !s.isAir());
        });
        Ops.def(g, "detect", 1, 1, (e, a) -> {
            BlockState s = solid(e, a.getFirst());
            return Value.of(s != null && !s.isAir() && s.getFluidState().isEmpty());
        });
        Ops.def(g, "compare", 1, 2, (e, a) -> {
            BlockState s = solid(e, a.getFirst());
            ItemStack held = a.size() > 1 ? stack(a.get(1)) : selected(e.body);
            return Value.of(s != null && !held.isEmpty() && s.getBlock().asItem() == held.getItem());
        });
        Ops.def(g, "biome", 1, 1, (e, a) -> {
            Value.Place p = place(e, a.getFirst());
            ServerLevel level = loaded(e, p);
            return level == null ? Value.NONE : Value.of(level.getBiome(p.pos()).unwrapKey().map(k -> k.identifier().getPath()).orElse(""));
        });
        Ops.def(g, "light", 1, 1, (e, a) -> {
            Value.Place p = place(e, a.getFirst());
            ServerLevel level = loaded(e, p);
            return Value.of(level == null ? 0 : level.getMaxLocalRawBrightness(p.pos()));
        });
        Ops.def(g, "block_data", 1, 2, (e, a) -> {
            Value.Place p = place(e, a.getFirst());
            ServerLevel level = loaded(e, p);
            BlockEntity be = level == null ? null : level.getBlockEntity(p.pos());
            if (be == null) {
                return Value.NONE;
            }
            CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
            if (a.size() < 2) {
                return Value.of(tag.toString());
            }
            Tag inner = tag.get(t(a, 1));
            return inner == null ? Value.NONE : inner.asNumber().<Value>map(x -> Value.of(x.doubleValue())).orElseGet(() -> Value.of(inner.toString()));
        });
        Ops.def(g, "opaque", 1, 1, (e, a) -> {
            BlockState s = solid(e, a.getFirst());
            return Value.of(s != null && s.canOcclude());
        });
        Ops.def(g, "hardness", 1, 1, (e, a) -> {
            BlockState s = solid(e, a.getFirst());
            return Value.of(s == null ? -1 : s.getDestroySpeed(e.body.world(), e.body.place()));
        });
        Ops.def(g, "age", 1, 1, (e, a) -> {
            if (a.getFirst() instanceof Value.Creature c) {
                return Value.of(c.entity() instanceof AgeableMob m ? m.getAge() : c.entity().tickCount);
            }
            BlockState s = solid(e, a.getFirst());
            return Value.of(s != null && s.getBlock() instanceof CropBlock crop ? crop.getAge(s) : -1);
        });
        Ops.def(g, "ripe_at", 1, 1, (e, a) -> {
            BlockState s = solid(e, a.getFirst());
            return Value.of(s != null && s.getBlock() instanceof CropBlock crop && crop.isMaxAge(s));
        });
        Ops.def(g, "property", 2, 2, (e, a) -> {
            BlockState s = solid(e, a.getFirst());
            if (s != null) {
                for (Property<?> prop : s.getProperties()) {
                    if (prop.getName().equals(t(a, 1))) {
                        return Value.of(s.getValue(prop).toString().toLowerCase(Locale.ROOT));
                    }
                }
            }
            return Value.NONE;
        });
    }

    // ------------------------------------------------------------ entities

    /** The entities a value names: inside an area, within a distance, or in one block. */
    private static List<Entity> entitiesIn(Eval e, Value v) {
        AABB box;
        Area area = null;
        switch (v) {
            case Value.Num r -> box = new AABB(e.body.place()).inflate(r.v());
            case Value.Region region -> {
                area = region.area();
                box = new AABB(area.minX(), area.minY(), area.minZ(), area.maxX() + 1, area.maxY() + 1, area.maxZ() + 1);
            }
            case Value.Many m -> {
                List<Entity> out = new ArrayList<>();
                for (Value x : m.items()) {
                    if (x instanceof Value.Creature c) {
                        out.add(c.entity());
                    } else {
                        out.addAll(entitiesIn(e, x));
                    }
                }
                return out;
            }
            case Value.Creature c -> {
                return List.of(c.entity());
            }
            default -> {
                Value.Place p = place(e, v);
                if (p == null) {
                    return List.of();
                }
                box = new AABB(p.pos());
            }
        }
        Area inside = area;
        return e.body.world().getEntities((Entity) null, box, x -> x.isAlive() && (inside == null || inside.contains(x.blockPosition())));
    }

    private static Value many(List<Entity> es) {
        List<Value> out = new ArrayList<>(es.size());
        es.forEach(x -> out.add(new Value.Creature(x)));
        return new Value.Many(List.copyOf(out));
    }

    @Nullable
    private static Entity entity(Value v) {
        return v instanceof Value.Creature c ? c.entity() : null;
    }

    private static void entities() {
        String g = "entities";
        Ops.def(g, "entities", 1, 2, (e, a) -> {
            List<Entity> es = entitiesIn(e, a.getFirst());
            e.spend(es.size());
            if (a.size() < 2) {
                return many(es);
            }
            Picker pick = Picks.of(e, a.get(1));
            return many(es.stream().filter(pick::passes).toList());
        });
        Ops.def(g, "players", 0, 1, (e, a) -> many(a.isEmpty() ? List.copyOf(e.body.world().players())
                : entitiesIn(e, a.getFirst()).stream().filter(x -> x instanceof Player).toList()));
        Ops.def(g, "entity", 1, 2, (e, a) -> {
            List<Entity> es = entitiesIn(e, a.getFirst());
            int i = a.size() > 1 ? (int) n(a, 1) - 1 : 0;
            return i < 0 || i >= es.size() ? Value.NONE : new Value.Creature(es.get(i));
        });
        Ops.def(g, "frame_item", 1, 1, (e, a) -> {
            for (Entity x : entitiesIn(e, a.getFirst())) {
                if (x instanceof ItemFrame f) {
                    return new Value.Stack(f.getItem().copy());
                }
            }
            return Value.NONE;
        });
        Ops.def(g, "frame_rotation", 1, 1, (e, a) -> {
            for (Entity x : entitiesIn(e, a.getFirst())) {
                if (x instanceof ItemFrame f) {
                    return Value.of(f.getRotation());
                }
            }
            return Value.of(-1);
        });
        flag(g, "is_mob", x -> x instanceof Enemy);
        flag(g, "is_animal", x -> x instanceof Animal);
        flag(g, "is_player", x -> x instanceof Player);
        flag(g, "is_item", x -> x instanceof ItemEntity);
        flag(g, "is_minecart", x -> x instanceof AbstractMinecart);
        flag(g, "is_baby", x -> x instanceof LivingEntity l && l.isBaby());
        flag(g, "on_fire", Entity::isOnFire);
        flag(g, "is_wet", Entity::isInWaterOrRain);
        flag(g, "is_sneaking", Entity::isCrouching);
        flag(g, "is_eating", x -> x instanceof LivingEntity l && l.isUsingItem());
        flag(g, "alive", Entity::isAlive);
        flag(g, "is_shearable", x -> x instanceof Shearable s && s.readyForShearing());
        flag(g, "can_breed", x -> x instanceof Animal m && m.canFallInLove());
        flag(g, "in_love", x -> x instanceof Animal m && m.isInLove());
        number(g, "health", x -> x instanceof LivingEntity l ? l.getHealth() : 0);
        number(g, "max_health", x -> x instanceof LivingEntity l ? l.getMaxHealth() : 0);
        number(g, "width", x -> x.getBbWidth());
        number(g, "height", x -> x.getBbHeight());
        Ops.def(g, "held", 1, 2, (e, a) -> a.getFirst() instanceof Value.Creature c && c.entity() instanceof LivingEntity l
                ? new Value.Stack(l.getItemBySlot(a.size() > 1 && n(a, 1) == 2 ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND).copy()) : Value.NONE);
        Ops.def(g, "armor", 1, 1, (e, a) -> {
            List<Value> out = new ArrayList<>();
            if (entity(a.getFirst()) instanceof LivingEntity l) {
                for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                    out.add(new Value.Stack(l.getItemBySlot(slot).copy()));
                }
            }
            return new Value.Many(List.copyOf(out));
        });
        Ops.def(g, "dropped", 1, 1, (e, a) -> entity(a.getFirst()) instanceof ItemEntity i ? new Value.Stack(i.getItem().copy()) : Value.NONE);
        Ops.def(g, "target", 1, 1, (e, a) -> entity(a.getFirst()) instanceof Mob m && m.getTarget() != null ? new Value.Creature(m.getTarget()) : Value.NONE);
        Ops.def(g, "riding", 1, 1, (e, a) -> {
            Entity x = entity(a.getFirst());
            return x != null && x.getVehicle() != null ? new Value.Creature(x.getVehicle()) : Value.NONE;
        });
        Ops.def(g, "riders", 1, 1, (e, a) -> {
            Entity x = entity(a.getFirst());
            return x == null ? new Value.Many(List.of()) : many(x.getPassengers());
        });
    }

    private static void flag(String g, String name, java.util.function.Predicate<Entity> test) {
        Ops.def(g, name, 1, 1, (e, a) -> {
            Entity x = entity(a.getFirst());
            return Value.of(x != null && test.test(x));
        });
    }

    private static void number(String g, String name, java.util.function.ToDoubleFunction<Entity> f) {
        Ops.def(g, name, 1, 1, (e, a) -> {
            Entity x = entity(a.getFirst());
            return Value.of(x == null ? 0 : f.applyAsDouble(x));
        });
    }

    // ------------------------------------------------------------ items

    /** A value as an item: an item, a block's item, or the item a name names. */
    static ItemStack stack(Value v) {
        return switch (v) {
            case Value.Stack s -> s.stack();
            case Value.Solid s -> new ItemStack(s.state().getBlock().asItem());
            case Value.Nothing n -> ItemStack.EMPTY;
            default -> {
                String name = v.show().strip().replace(' ', '_');
                Identifier id = Identifier.tryParse(name);
                Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
                yield item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
            }
        };
    }

    private static ItemStack selected(Body a) {
        return a.items().getItem(a.selected());
    }

    private static void items() {
        String g = "items";
        Ops.def(g, "name", 1, 1, (e, a) -> Value.of(switch (a.getFirst()) {
            case Value.Creature c -> BuiltInRegistries.ENTITY_TYPE.getKey(c.entity().getType()).getPath().replace('_', ' ');
            case Value.Liquid l -> BuiltInRegistries.FLUID.getKey(l.fluid().getFluid()).getPath().replace('_', ' ');
            case Value.Solid s -> BuiltInRegistries.BLOCK.getKey(s.state().getBlock()).getPath().replace('_', ' ');
            case Value.Stack s -> Things.name(s.stack());
            default -> t(a, 0);
        }));
        Ops.def(g, "shown_name", 1, 1, (e, a) -> Value.of(switch (a.getFirst()) {
            case Value.Creature c -> c.entity().getName().getString();
            case Value.Stack s -> s.stack().getHoverName().getString();
            case Value.Solid s -> s.state().getBlock().getName().getString();
            default -> t(a, 0);
        }));
        Ops.def(g, "mod_of", 1, 1, (e, a) -> Value.of(switch (a.getFirst()) {
            case Value.Creature c -> BuiltInRegistries.ENTITY_TYPE.getKey(c.entity().getType()).getNamespace();
            case Value.Liquid l -> BuiltInRegistries.FLUID.getKey(l.fluid().getFluid()).getNamespace();
            case Value.Solid s -> BuiltInRegistries.BLOCK.getKey(s.state().getBlock()).getNamespace();
            default -> BuiltInRegistries.ITEM.getKey(stack(a.getFirst()).getItem()).getNamespace();
        }));
        Ops.def(g, "tags", 1, 1, (e, a) -> {
            List<String> tags = switch (a.getFirst()) {
                case Value.Solid s -> s.state().getBlock().builtInRegistryHolder().tags().map(k -> k.location().toString()).toList();
                case Value.Creature c -> c.entity().getType().builtInRegistryHolder().tags().map(k -> k.location().toString()).toList();
                case Value.Liquid l -> l.fluid().getFluid().builtInRegistryHolder().tags().map(k -> k.location().toString()).toList();
                default -> stack(a.getFirst()).getItem().builtInRegistryHolder().tags().map(k -> k.location().toString()).toList();
            };
            return new Value.Many(tags.stream().map(s -> (Value) Value.of(s)).toList());
        });
        Ops.def(g, "has_tag", 2, 2, (e, a) -> {
            Picker tag = Picks.tag(t(a, 1));
            return Value.of(switch (a.getFirst()) {
                case Value.Creature c -> tag.passes(c.entity());
                case Value.Liquid l -> tag.passes(l.fluid());
                default -> tag.passes(stack(a.getFirst()));
            });
        });
        item(g, "max_size", s -> Value.of(s.getMaxStackSize()));
        item(g, "stackable", s -> Value.of(s.isStackable()));
        item(g, "damageable", s -> Value.of(s.isDamageableItem()));
        item(g, "damage", s -> Value.of(s.getDamageValue()));
        item(g, "max_damage", s -> Value.of(s.getMaxDamage()));
        // what is left of it, in percent; -1 for what never wears, as inspection filters read it
        item(g, "durability", s -> Value.of(s.isDamageableItem() ? Math.floor(100.0 * (s.getMaxDamage() - s.getDamageValue()) / s.getMaxDamage()) : -1));
        item(g, "enchanted", s -> Value.of(s.isEnchanted()));
        item(g, "enchant_level", s -> {
            ItemEnchantments all = s.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            int best = 0;
            for (var entry : all.entrySet()) {
                best = Math.max(best, entry.getIntValue());
            }
            return Value.of(best);
        });
        item(g, "rarity", s -> Value.of(s.getRarity().name().toLowerCase(Locale.ROOT)));
        Ops.def(g, "burn_time", 1, 1, (e, a) -> Value.of(e.body.world().fuelValues().burnDuration(stack(a.getFirst()))));
        Ops.def(g, "is_fuel", 1, 1, (e, a) -> Value.of(e.body.world().fuelValues().isFuel(stack(a.getFirst()))));
        item(g, "food", s -> {
            FoodProperties food = s.get(DataComponents.FOOD);
            return Value.of(food == null ? -1 : food.nutrition());
        });
        item(g, "is_block", s -> Value.of(s.getItem() instanceof BlockItem));
        item(g, "block_of", s -> s.getItem() instanceof BlockItem b ? new Value.Solid(b.getBlock().defaultBlockState()) : Value.NONE);
        Ops.def(g, "with_count", 2, 2, (e, a) -> new Value.Stack(stack(a.getFirst()).copyWithCount((int) n(a, 1))));
        Ops.def(g, "can_harvest", 2, 2, (e, a) -> {
            BlockState s = solid(e, a.get(1));
            return Value.of(s != null && (!s.requiresCorrectToolForDrops() || stack(a.getFirst()).isCorrectToolForDrops(s)));
        });
        Ops.def(g, "mine_speed", 2, 2, (e, a) -> {
            BlockState s = solid(e, a.get(1));
            return Value.of(s == null ? 0 : stack(a.getFirst()).getDestroySpeed(s));
        });
        Ops.def(g, "recipe_inputs", 1, 2, (e, a) -> {
            ItemStack thing = stack(a.getFirst());
            for (Crafts.Made m : recipes(e, a, 1)) {
                if (ItemStack.isSameItem(m.result(), thing)) {
                    return stacks(m.inputs());
                }
            }
            return new Value.Many(List.of());
        });
        Ops.def(g, "recipe_output", 1, 2, (e, a) -> {
            ItemStack thing = stack(a.getFirst());
            for (Crafts.Made m : recipes(e, a, 1)) {
                if (m.uses(thing)) {
                    return new Value.Stack(m.result().copy());
                }
            }
            return Value.NONE;
        });
        Ops.def(g, "recipes_using", 1, 2, (e, a) -> {
            ItemStack thing = stack(a.getFirst());
            List<Value> out = new ArrayList<>();
            for (Crafts.Made m : recipes(e, a, 1)) {
                if (m.uses(thing)) {
                    out.add(new Value.Stack(m.result().copy()));
                }
            }
            return new Value.Many(List.copyOf(out));
        });
        Ops.def(g, "recipes_making", 1, 2, (e, a) -> {
            ItemStack thing = stack(a.getFirst());
            List<Value> out = new ArrayList<>();
            for (Crafts.Made m : recipes(e, a, 1)) {
                if (ItemStack.isSameItem(m.result(), thing)) {
                    out.add(stacks(m.inputs()));
                }
            }
            return new Value.Many(List.copyOf(out));
        });
    }

    /** The recipes of the machine named at argument {@code i}, or crafting's with none named. */
    private static List<Crafts.Made> recipes(Eval e, List<Value> a, int i) {
        Value.Place p = a.size() > i ? place(e, a.get(i)) : null;
        ServerLevel level = p == null ? null : loaded(e, p);
        List<Crafts.Made> all = Crafts.made(e.body.world(), level == null ? null : level.getBlockEntity(p.pos()));
        e.spend(all.size() / 16);
        return all;
    }

    private static Value stacks(List<ItemStack> items) {
        return new Value.Many(items.stream().map(s -> (Value) new Value.Stack(s.copy())).toList());
    }

    private static void item(String g, String name, java.util.function.Function<ItemStack, Value> f) {
        Ops.def(g, name, 1, 1, (e, a) -> f.apply(stack(a.getFirst())));
    }

    /** How full an item that holds fluid is, in percent; -1 for one that does not. */
    private static double fluidFill(ItemStack s) {
        ResourceHandler<FluidResource> h = s.getCapability(Capabilities.Fluid.ITEM, ItemAccess.forStack(s));
        if (h == null || h.size() == 0) {
            return -1;
        }
        long cap = h.getCapacityAsLong(0, h.getResource(0));
        return cap == 0 ? 0 : Math.floor(100.0 * h.getAmountAsLong(0) / cap);
    }

    /** How charged an item that holds energy is, in percent; -1 for one that does not. */
    private static double energyFill(ItemStack s) {
        EnergyHandler h = s.getCapability(Capabilities.Energy.ITEM, ItemAccess.forStack(s));
        return h == null ? -1 : h.getCapacityAsLong() == 0 ? 0 : Math.floor(100.0 * h.getAmountAsLong() / h.getCapacityAsLong());
    }

    // ------------------------------------------------------------ fluids

    private static void fluids() {
        String g = "fluids";
        Ops.def(g, "liquid", 1, 2, (e, a) -> {
            Identifier id = Identifier.tryParse(t(a, 0).strip().replace(' ', '_'));
            Fluid fluid = id == null ? null : BuiltInRegistries.FLUID.getValue(id);
            return fluid == null ? Value.NONE : new Value.Liquid(FluidResource.of(fluid), a.size() > 1 ? (long) n(a, 1) : 1000);
        });
        Ops.def(g, "amount", 1, 1, (e, a) -> Value.of(a.getFirst() instanceof Value.Liquid l ? l.amount() : 0));
        fluid(g, "density", f -> f.getFluidType().getDensity());
        fluid(g, "viscosity", f -> f.getFluidType().getViscosity());
        fluid(g, "fluid_temperature", f -> f.getFluidType().getTemperature());
        fluid(g, "fluid_light", f -> f.getFluidType().getLightLevel());
        Ops.def(g, "is_lighter", 1, 1, (e, a) -> Value.of(a.getFirst() instanceof Value.Liquid l && l.fluid().getFluidType().isLighterThanAir()));
    }

    private static void fluid(String g, String name, java.util.function.ToDoubleFunction<FluidResource> f) {
        Ops.def(g, name, 1, 1, (e, a) -> Value.of(a.getFirst() instanceof Value.Liquid l ? f.applyAsDouble(l.fluid()) : 0));
    }

    // ------------------------------------------------------------ machines

    /** What a machine can be asked: working on something, whether it has what it needs, how far along. */
    private record Worker(boolean working, boolean hasWork, boolean canWork, int progress, int total, double temperature, double hottest) {
    }

    @Nullable
    private static Worker worker(Eval e, Value v) {
        Value.Place p = place(e, v);
        ServerLevel level = loaded(e, p);
        BlockEntity be = level == null ? null : level.getBlockEntity(p.pos());
        return switch (be) {
            case AbstractFurnaceBlockEntity f -> {
                int lit = f.dataAccess.get(AbstractFurnaceBlockEntity.DATA_LIT_TIME), cook = f.dataAccess.get(AbstractFurnaceBlockEntity.DATA_COOKING_PROGRESS),
                        total = f.dataAccess.get(AbstractFurnaceBlockEntity.DATA_COOKING_TOTAL_TIME);
                yield new Worker(lit > 0, !f.getItem(0).isEmpty(), lit > 0 || !f.getItem(1).isEmpty(), cook, total, lit > 0 ? 1 : 0, 1);
            }
            case BrewingStandBlockEntity b -> {
                int left = b.dataAccess.get(BrewingStandBlockEntity.DATA_BREW_TIME);
                yield new Worker(left > 0, !b.getItem(3).isEmpty(), b.dataAccess.get(BrewingStandBlockEntity.DATA_FUEL_USES) > 0, left > 0 ? 400 - left : 0, 400, -1, -1);
            }
            case FabricatorBlockEntity f -> new Worker(f.progress() > 0, f.total() > 0, true, f.progress(), f.total(), -1, -1);
            case SmelterBlockEntity s -> new Worker(s.progress() > 0, !s.items().getItem(SmelterBlockEntity.IN).isEmpty(), s.energy().getAmountAsLong() > 0, s.progress(), SmelterBlockEntity.TIME, s.temperature(), 7);
            case null, default -> null;
        };
    }

    private static void machines() {
        String g = "machines";
        Ops.def(g, "is_worker", 1, 1, (e, a) -> Value.of(worker(e, a.getFirst()) != null));
        Ops.def(g, "working", 1, 1, (e, a) -> {
            Worker w = worker(e, a.getFirst());
            return Value.of(w != null && w.working());
        });
        Ops.def(g, "has_work", 1, 1, (e, a) -> {
            Worker w = worker(e, a.getFirst());
            return Value.of(w != null && w.hasWork());
        });
        Ops.def(g, "can_work", 1, 1, (e, a) -> {
            Worker w = worker(e, a.getFirst());
            return Value.of(w != null && w.canWork());
        });
        Ops.def(g, "progress", 1, 1, (e, a) -> {
            Worker w = worker(e, a.getFirst());
            return Value.of(w == null || w.total() == 0 ? 0 : 100.0 * w.progress() / w.total());
        });
        Ops.def(g, "temperature", 1, 1, (e, a) -> {
            Worker w = worker(e, a.getFirst());
            return Value.of(w == null ? -1 : w.temperature());
        });
        Ops.def(g, "has_temperature", 1, 1, (e, a) -> {
            Worker w = worker(e, a.getFirst());
            return Value.of(w != null && w.hottest() >= 0);
        });
        Ops.def(g, "max_temperature", 1, 1, (e, a) -> {
            Worker w = worker(e, a.getFirst());
            return Value.of(w == null ? -1 : w.hottest());
        });
        Ops.def(g, "min_temperature", 1, 1, (e, a) -> {
            Worker w = worker(e, a.getFirst());
            return Value.of(w == null || w.hottest() < 0 ? -1 : 0);
        });
        Ops.def(g, "recipes", 0, 1, (e, a) -> {
            List<Value> out = new ArrayList<>();
            for (Crafts.Made made : recipes(e, a, 0)) {
                e.spend(1);
                out.add(new Value.Stack(made.result().copy()));
            }
            return new Value.Many(List.copyOf(out));
        });
    }

    // ------------------------------------------------------------ the world

    private static void world() {
        String g = "world";
        fact(g, "is_clear", e -> Value.of(!e.body.world().isRaining()));
        fact(g, "is_rain", e -> Value.of(e.body.world().isRaining()));
        fact(g, "is_thunder", e -> Value.of(e.body.world().isThundering()));
        fact(g, "is_day", e -> Value.of(e.body.world().isBrightOutside()));
        fact(g, "is_night", e -> Value.of(e.body.world().isDarkOutside()));
        fact(g, "rain_time", e -> Value.of(e.body.world().getServer().getWeatherData().getRainTime()));
        fact(g, "time", e -> Value.of(e.body.world().getDefaultClockTime() % 24000));
        fact(g, "total_time", e -> Value.of(e.body.world().getGameTime()));
        fact(g, "tick_time", e -> Value.of(e.body.world().getServer().getAverageTickTimeNanos() / 1e6));
        fact(g, "tps", e -> Value.of(Math.min(20, 1e9 / Math.max(1, e.body.world().getServer().getAverageTickTimeNanos()))));
        Ops.def(g, "world_name", 0, 1, (e, a) -> {
            Value.Place p = a.isEmpty() ? null : place(e, a.getFirst());
            return Value.of((p != null && p.dimension() != null ? p.dimension() : e.body.world().dimension()).identifier().getPath());
        });
        fact(g, "player_count", e -> Value.of(e.body.world().getServer().getPlayerCount()));
        fact(g, "moon", e -> Value.of(e.body.world().getMoonBrightness(e.body.place())));
        Ops.def(g, "heard", 0, 2, (e, a) -> {
            String wanted = a.isEmpty() ? "" : t(a, 0).strip().replace(' ', '_');
            double range = a.size() > 1 ? n(a, 1) : HEARING;
            long now = e.body.world().getGameTime();
            for (Note note : NOTES.getOrDefault(e.body.world(), new ArrayDeque<>())) {
                if (now - note.at() <= NOTE_LASTS && (wanted.isEmpty() || note.instrument().equals(wanted)) && note.pos().closerToCenterThan(e.body.center(), range)) {
                    return Value.of(note.id());
                }
            }
            return Value.of(-1);
        });
    }

    /** A note block's note, as a body nearby hears it. */
    private record Note(BlockPos pos, String instrument, int id, long at) {
    }

    private static final Map<ServerLevel, ArrayDeque<Note>> NOTES = new WeakHashMap<>();
    /** How far a body hears a note with no distance named, and how long it can still say it did, in ticks. */
    private static final double HEARING = 16;
    private static final int NOTE_LASTS = 20, NOTES_KEPT = 64;

    /** Keeps what note blocks play, newest first, for {@code heard}. */
    public static void played(NoteBlockEvent.Play event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ArrayDeque<Note> notes = NOTES.computeIfAbsent(level, l -> new ArrayDeque<>());
            notes.addFirst(new Note(event.getPos().immutable(), event.getInstrument().getSerializedName(), event.getVanillaNoteId(), level.getGameTime()));
            if (notes.size() > NOTES_KEPT) {
                notes.removeLast();
            }
        }
    }

    private static void fact(String g, String name, java.util.function.Function<Eval, Value> f) {
        Ops.def(g, name, 0, 0, (e, a) -> f.apply(e));
    }

    // ------------------------------------------------------------ the network

    @Nullable
    private static StorageNetwork net(Eval e, List<Value> a, int i) {
        Value.Place p = a.size() > i ? place(e, a.get(i)) : null;
        if (p != null) {
            ServerLevel level = loaded(e, p);
            return level != null && level.getBlockState(p.pos()).getBlock() instanceof Node ? StorageNetwork.at(level, p.pos()) : null;
        }
        return Places.network(e.body);
    }

    private static void network() {
        String g = "network";
        Ops.def(g, "is_network", 0, 1, (e, a) -> Value.of(net(e, a, 0) != null));
        Ops.def(g, "network_blocks", 0, 1, (e, a) -> {
            StorageNetwork net = net(e, a, 0);
            return Value.of(net == null ? 0 : net.nodes.size());
        });
        Ops.def(g, "network_used", 0, 1, (e, a) -> {
            StorageNetwork net = net(e, a, 0);
            return Value.of(net == null ? 0 : net.usage()[0]);
        });
        Ops.def(g, "network_capacity", 0, 1, (e, a) -> {
            StorageNetwork net = net(e, a, 0);
            return Value.of(net == null ? 0 : net.usage()[1]);
        });
        Ops.def(g, "network_kinds", 0, 1, (e, a) -> {
            StorageNetwork net = net(e, a, 0);
            return Value.of(net == null ? 0 : net.contents().size());
        });
        Ops.def(g, "network_items", 0, 1, (e, a) -> {
            StorageNetwork net = net(e, a, 0);
            List<Value> out = new ArrayList<>();
            if (net != null) {
                net.contents().object2LongEntrySet().forEach(x -> out.add(new Value.Stack(x.getKey().toStack((int) Math.min(x.getLongValue(), Integer.MAX_VALUE)))));
            }
            e.spend(out.size());
            return new Value.Many(List.copyOf(out));
        });
        Ops.def(g, "network_count", 0, 2, (e, a) -> {
            StorageNetwork net = net(e, a, 1);
            if (net == null) {
                return Value.of(0);
            }
            Picker pick = a.isEmpty() ? Picks.ALL : Picks.of(e, a.getFirst());
            double[] n = {0};
            net.contents().object2LongEntrySet().forEach(x -> {
                if (pick.passes(x.getKey().toStack())) {
                    n[0] += x.getLongValue();
                }
            });
            return Value.of(n[0]);
        });
        Ops.def(g, "craftable", 0, 2, (e, a) -> {
            StorageNetwork net = net(e, a, 1);
            if (net == null) {
                return a.isEmpty() ? new Value.Many(List.of()) : Value.FALSE;
            }
            if (a.isEmpty()) {
                List<Value> out = new ArrayList<>();
                for (ItemResource kind : CraftJobs.craftable(net)) {
                    out.add(new Value.Stack(kind.toStack()));
                }
                return new Value.Many(List.copyOf(out));
            }
            Picker pick = Picks.of(e, a.getFirst());
            for (ItemResource kind : CraftJobs.craftable(net)) {
                if (pick.passes(kind.toStack())) {
                    return Value.TRUE;
                }
            }
            return Value.FALSE;
        });
        Ops.def(g, "network_fluids", 0, 1, (e, a) -> {
            StorageNetwork net = net(e, a, 0);
            List<Value> out = new ArrayList<>();
            if (net != null) {
                net.fluids().object2LongEntrySet().forEach(x -> out.add(new Value.Liquid(x.getKey(), x.getLongValue())));
            }
            e.spend(out.size());
            return new Value.Many(List.copyOf(out));
        });
        Ops.def(g, "network_fluid_used", 0, 1, (e, a) -> {
            StorageNetwork net = net(e, a, 0);
            return Value.of(net == null ? 0 : net.fluids().values().longStream().sum());
        });
        Ops.def(g, "network_fluid_capacity", 0, 1, (e, a) -> {
            StorageNetwork net = net(e, a, 0);
            return Value.of(net == null ? 0 : net.fluids().values().longStream().sum() + net.insert(FluidResource.of(Fluids.WATER), Long.MAX_VALUE / 2, true));
        });
        Ops.def(g, "network_fluid_count", 0, 2, (e, a) -> {
            StorageNetwork net = net(e, a, 1);
            if (net == null) {
                return Value.of(0);
            }
            Picker pick = a.isEmpty() ? Picks.ALL : Picks.of(e, a.getFirst());
            double[] n = {0};
            net.fluids().object2LongEntrySet().forEach(x -> {
                if (pick.passes(x.getKey())) {
                    n[0] += x.getLongValue();
                }
            });
            return Value.of(n[0]);
        });
        Ops.def(g, "network_ports", 0, 1, (e, a) -> {
            StorageNetwork net = net(e, a, 0);
            return Value.of(net == null ? 0 : net.ports().size());
        });
        Ops.def(g, "jobs", 0, 1, (e, a) -> {
            StorageNetwork net = net(e, a, 0);
            List<Value> out = new ArrayList<>();
            if (net != null) {
                for (CraftJob job : CraftingData.of(e.body.world().getServer()).on(e.body.world(), net)) {
                    out.add(new Value.Stack(job.target.toStack((int) Math.min(job.amount, Integer.MAX_VALUE))));
                }
            }
            return new Value.Many(List.copyOf(out));
        });
        Ops.def(g, "network_lacks", 0, 1, (e, a) -> {
            StorageNetwork net = net(e, a, 0);
            List<Value> out = new ArrayList<>();
            if (net != null) {
                for (CraftJob job : CraftingData.of(e.body.world().getServer()).on(e.body.world(), net)) {
                    ItemResource lacking = job.lacking();
                    if (lacking != null) {
                        out.add(new Value.Stack(lacking.toStack((int) Math.min(job.lackingCount(), Integer.MAX_VALUE))));
                    }
                }
            }
            return new Value.Many(List.copyOf(out));
        });
        Ops.def(g, "read_var", 2, 2, (e, a) -> {
            String name = "@" + t(a, 1).strip().replace("@", "");
            if (a.getFirst() instanceof Value.Creature c) {
                return c.entity() instanceof Agent agent ? agent.vars().getOrDefault(name, Value.NONE) : Value.NONE;
            }
            ServerLevel level = loaded(e, place(e, a.getFirst()));
            return level != null && level.getBlockEntity(place(e, a.getFirst()).pos()) instanceof PortBlockEntity port ? port.work.vars().getOrDefault(name, Value.NONE) : Value.NONE;
        });
    }

    // ------------------------------------------------------------ itself

    private static void self() {
        String g = "self";
        fact(g, "facing", e -> Value.of(e.body.facing().getName()));
        fact(g, "reach", e -> Value.of(e.body.reach()));
        fact(g, "run_ticks", e -> Value.of(e.body.interval()));
        fact(g, "run_items", e -> Value.of(e.body.itemsPerRun()));
        fact(g, "run_fluid", e -> Value.of(e.body.fluidPerRun()));
        fact(g, "run_energy", e -> Value.of(e.body.energyPerRun()));
        fact(g, "fuel", e -> Value.of(e.body.battery().getAmountAsLong()));
        fact(g, "fuel_limit", e -> Value.of(e.body.battery().getCapacityAsLong()));
        fact(g, "selected", e -> Value.of(e.body.selected() + 1));
        fact(g, "equipped", e -> new Value.Stack(selected(e.body).copy()));
        fact(g, "hands_room", e -> {
            int free = 0;
            for (int i = 0; i < e.body.carry(); i++) {
                free += e.body.items().getItem(i).isEmpty() ? 1 : 0;
            }
            return Value.of(free);
        });
        Ops.def(g, "upgrades", 0, 1, (e, a) -> {
            String kind = a.isEmpty() ? "" : t(a, 0).strip().replace(' ', '_').replace("_upgrade", "");
            int n = 0;
            for (Upgrades.Kind k : Upgrades.Kind.values()) {
                if (kind.isEmpty() || k.name().equalsIgnoreCase(kind)) {
                    n += Upgrades.count(e.body.upgrades(), k);
                }
            }
            return Value.of(n);
        });
        fact(g, "carrying", e -> Value.of(e.body.entity() == null ? 0 : e.body.entity().getPassengers().size()));
        fact(g, "owner_name", e -> {
            ServerPlayer p = e.body.owner() == null ? null : e.body.world().getServer().getPlayerList().getPlayer(e.body.owner());
            return p == null ? Value.NONE : Value.of(p.getName().getString());
        });
        Ops.def(g, "slot_count", 0, 1, (e, a) -> Value.of(handSlot(e, a).getCount()));
        Ops.def(g, "slot_space", 0, 1, (e, a) -> {
            ItemStack s = handSlot(e, a);
            return Value.of(s.isEmpty() ? 64 : s.getMaxStackSize() - s.getCount());
        });
        Ops.def(g, "slot_item", 0, 1, (e, a) -> new Value.Stack(handSlot(e, a).copy()));
        Ops.def(g, "compare_slots", 1, 2, (e, a) -> {
            ItemStack x = a.size() > 1 ? slotOf(e, (int) n(a, 1)) : selected(e.body);
            return Value.of(ItemStack.isSameItemSameComponents(slotOf(e, (int) n(a, 0)), x));
        });
    }

    private static ItemStack handSlot(Eval e, List<Value> a) {
        return a.isEmpty() ? selected(e.body) : slotOf(e, (int) n(a, 0));
    }

    private static ItemStack slotOf(Eval e, int slot) {
        return slot >= 1 && slot <= e.body.carry() ? e.body.items().getItem(slot - 1) : ItemStack.EMPTY;
    }

    // ------------------------------------------------------------ the first checks

    static Value first(Eval e, Expr.Sense s) {
        Body a = e.body;
        ServerLevel level = a.world();
        return Value.of(switch (s.sense()) {
            case HAVE -> Acts.carried(a, Things.of(s.thing())::passes) >= least(e, s);
            case STOCKED -> Acts.stocked(level, a, Things.of(s.thing())::passes) >= least(e, s);
            case RIPE -> Farming.plots(level, a.home()).stream().anyMatch(p -> Farming.state(level, p) == Farming.RIPE);
            case FULL -> a.full();
            case NIGHT -> level.isDarkOutside();
            case NEAR -> {
                ServerPlayer p = a.boss();
                yield p != null && p.distanceToSqr(a.center()) <= Running.NEAR * Running.NEAR;
            }
            case POWERED -> {
                Value.Place p = place(e, e.eval(s.mark()));
                yield p != null && level.hasNeighborSignal(p.pos());
            }
        });
    }

    private static int least(Eval e, Expr.Sense s) {
        return s.count() == null ? 1 : Math.max(1, (int) Eval.num(e.eval(s.count())));
    }
}
