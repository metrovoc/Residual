package io.github.metrovoc.residual.agent;

import io.github.metrovoc.residual.storage.StorageNetwork;
import io.github.metrovoc.residual.storage.DockBlockEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

/**
 * What the verbs that came after the first ones do: carrying between places, breaking, placing and using what is in reach,
 * striking, moving a block at a time, and the rest of what routers, drones and turtles do. Each is one operation, or as
 * many as its number asks for, of as much as the body's upgrades let an operation move, and takes the ticks its upgrades
 * say. Things reached are walked to by a body with legs, and only reached by one without.
 */
final class Deeds {
    /** How close to a place a body with legs stands to work on it. */
    private static final double HANDS = 2.5;
    /** How far above and around its feet a body with legs reaches with a tool: about as far as a player does. */
    private static final double TOOL = 5.5;
    /** Places looked at in a tick, at most, for a step going through an area. */
    private static final int LOOKS = 512;
    /** How far a body extrudes, at most. */
    static final int LONGEST_EXTRUSION = 64;
    /** Forge Energy a tick a piece of fuel in hand gives for each tick it would burn. */
    private static final int FUEL_ENERGY = 40;

    private Deeds() {
    }

    static Acts.Action of(Body a, Eval e, Program.Act act) {
        return switch (act.verb()) {
            case SEND -> send(a, e, act);
            case PUMP -> pump(a, e, act);
            case CHARGE -> charge(a, e, act);
            case DROP -> drop(a, e, act);
            case FLING -> fling(a, e, act);
            case VOID -> voiding(a, e, act);
            case DRAIN -> drain(a, e, act);
            case DIG -> dig(a, e, act);
            case USE -> use(a, e, act);
            case ATTACK -> attack(a, e, act);
            case EMIT -> emit(a, e, act);
            case PULSE -> pulse(a, e, act);
            case EXTRUDE -> extrude(a, e, act);
            case RETRACT -> retract(a, e, act);
            case FORWARD, BACK, UP, DOWN -> walkOn(a, e, act);
            case TURN -> turn(a, act);
            case TELEPORT -> teleport(a, e, act);
            case SELECT -> select(a, e, act);
            case TRANSFER -> transfer(a, e, act);
            case EQUIP -> equip(a, e, act);
            case REFUEL -> refuel(a, e, act);
            case LIFT -> lift(a, e, act);
            case LOWER -> lower(a);
            case RENAME -> rename(a, e, act);
            case SIGN -> sign(a, e, act);
            case RETIRE -> level -> a.retire() ? Outcome.DONE : Outcome.failed("residual.program.failed.no_legs");
            case PROVIDE, PUSH, ACCEPT, REQUEST -> Logistics.board(a, e, act);
            case DELIVER -> Logistics.deliver(a);
            case SOUND -> sound(a, e, act);
            case PARTICLE -> particle(a, e, act);
            default -> throw new IllegalArgumentException(act.verb().word);
        };
    }

    // ---------------------------------------------------------------- what a step says

    /** The number a step gives, or 0 for none. A variable holding a thing is what it works on instead: see {@link #pick}. */
    static int count(Eval e, Program.Act act) {
        if (act.n() == null) {
            return 0;
        }
        Value v = e.eval(act.n());
        return v instanceof Value.Num || v instanceof Value.Flag || v instanceof Value.Stack ? Math.max(0, (int) Eval.num(v)) : 0;
    }

    static Picker pick(Eval e, Program.Act act) {
        if (act.what().all() && act.n() != null) {
            Value v = e.eval(act.n());
            if (!(v instanceof Value.Num || v instanceof Value.Flag || v instanceof Value.Nothing)) {
                return Picks.of(e, v);
            }
        }
        return Picks.of(e, act.what());
    }

    /** What a clause says, or {@code otherwise}. */
    static Value clause(Body a, Eval e, Program.Act act, String word, String otherwise) {
        Expr x = act.clause(word);
        if (x instanceof Expr.Call c && c.fn().equals("each")) {
            x = c.args().getFirst();
        }
        return x == null ? Places.spot(a, otherwise) : e.eval(x);
    }

    /** The places a value stands for, one or many. */
    static List<Value> places(Eval e, Value v) {
        return v instanceof Value.Spot || v instanceof Value.Place || v instanceof Value.Creature || v instanceof Value.Num ? List.of(v) : e.list(v);
    }

    /** Where to stand to work on a place, or null where no standing is needed: its hands. */
    @Nullable
    static BlockPos standing(Body a, Value v) {
        if (v instanceof Value.Spot s) {
            if (s.word().equals("hands")) {
                return null;
            }
            if (s.word().equals("network")) {
                List<BlockPos> terminals = Stores.terminals(a.world(), a.home());
                return terminals.isEmpty() ? a.place() : terminals.getFirst();
            }
            var owner = Places.owner(a);
            return owner == null ? a.place() : owner.blockPosition();
        }
        Value.Place p = Places.place(a, v);
        return p == null ? a.place() : p.pos();
    }

    /** Whether it can work on a place from where it stands, for a body with no legs. */
    private static boolean reaches(Body a, Value v) {
        if (v instanceof Value.Spot s) {
            return !s.word().startsWith("owner") || Places.owner(a) != null;
        }
        Value.Place p = Places.place(a, v);
        return p != null && (p.dimension() != null && !p.dimension().equals(a.world().dimension()) ? a.relayed() : a.reaches(p.pos()));
    }

    /** Shows it worked on a place, if that is one in this world. */
    private static void reached(Body a, Value where, int n) {
        Value.Place p = n > 0 ? Places.place(a, where) : null;
        if (p != null && (p.dimension() == null || p.dimension().equals(a.world().dimension()))) {
            a.reached(p.pos(), n);
        }
    }

    private static boolean isNetwork(Value v) {
        return v instanceof Value.Spot s && s.word().equals("network");
    }

    /** The network whose links carry a step of a body with no legs, if one end of it is that network. */
    @Nullable
    private static StorageNetwork through(Body a, Value from, Value to) {
        return !a.legs() && (isNetwork(from) || isNetwork(to)) ? Places.network(a) : null;
    }

    /** Waits out what an operation takes. */
    private static void spent(Body a) {
        if (a.interval() > 1) {
            a.pause(a.interval() - 1);
        }
    }

    /** One operation of a step; yields the rest of its tick, so that a loop of it moves once a tick at most. */
    private abstract static class Operation implements Acts.Action {
        final Body a;

        Operation(Body a) {
            this.a = a;
        }

        @Override
        public boolean yields() {
            return true;
        }
    }

    // ---------------------------------------------------------------- items between places

    /** Items from one place to another, up to a number or what an operation moves. A list of places to go to is filled in order, or in turn with {@code to each}. */
    static Acts.Action send(Body a, Eval e, Program.Act act) {
        int want = count(e, act);
        Picker pick = pick(e, act);
        Value from = clause(a, e, act, "from", a.legs() ? "hands" : "front");
        Value to = clause(a, e, act, "to", a.legs() && !(from instanceof Value.Spot s && s.word().equals("hands")) ? "hands" : "network");
        boolean inTurn = act.clause("to") instanceof Expr.Call c && c.fn().equals("each");
        return a.legs() ? haul(a, e, act, want, pick, from, to, inTurn) : directly(a, e, act, want, pick, from, to, inTurn);
    }

    /** {@code take} and {@code put} with a place: the same, between that place and its hands. */
    static Acts.Action move(Body a, Eval e, Program.Act act, Value from, Value to) {
        int want = count(e, act);
        Picker pick = pick(e, act);
        return a.legs() ? haul(a, e, act, want, pick, from, to, false) : directly(a, e, act, want, pick, from, to, false);
    }

    private static Acts.Action directly(Body a, Eval e, Program.Act act, int want, Picker pick, Value from, Value to, boolean inTurn) {
        List<Value> froms = places(e, from), tos = places(e, to);
        return new Operation(a) {
            private int sent;

            @Override
            public Outcome tick(ServerLevel level) {
                int most = want > 0 ? Math.min(want - sent, a.itemsPerRun()) : a.itemsPerRun();
                StorageNetwork links = through(a, from, to);
                if (links != null) {
                    // a full link holds it up until the next tick, as it does anything else
                    most = links.allowance(a.place(), most);
                    if (most <= 0) {
                        return null;
                    }
                }
                int moved = 0;
                boolean anywhere = false;
                int start = inTurn ? e.turn(act, tos.size()) : 0;
                outer:
                for (Value source : froms) {
                    Places.Ends src = reaches(a, source) ? Places.items(a, source) : null;
                    if (src == null) {
                        continue;
                    }
                    for (int i = 0; i < tos.size(); i++) {
                        Value target = tos.get((start + i) % tos.size());
                        Places.Ends dst = reaches(a, target) ? Places.items(a, target) : null;
                        if (dst == null) {
                            continue;
                        }
                        anywhere = true;
                        int went = Places.move(src, dst, pick, most - moved);
                        moved += went;
                        reached(a, target, went);
                        reached(a, source, went);
                        if (moved > 0 && inTurn) {
                            e.turned(act, start + i + 1);
                            break outer;
                        }
                        if (moved >= most) {
                            break outer;
                        }
                    }
                }
                if (!anywhere) {
                    a.dry(false);
                    return Outcome.failed("residual.program.failed.no_way");
                }
                a.dry(moved > 0);
                a.moved(moved, isNetwork(to));
                if (links != null) {
                    links.carry(a.place(), moved);
                }
                sent += moved;
                spent(a);
                return want > sent && moved > 0 ? null : Outcome.done("residual.program.sent", sent);
            }
        };
    }

    /** A body with legs carries what it sends: to where it comes from, into its hands, to where it goes, out of them. */
    private static Acts.Action haul(Body a, Eval e, Program.Act act, int want, Picker pick, Value from, Value to, boolean inTurn) {
        List<Value> froms = places(e, from), tos = places(e, to);
        int most = want > 0 ? want : 64 * a.carry();
        return new Operation(a) {
            private final Places.Ends hands = Places.hands(a);
            private final Places.Ends cargo = Places.cargo(hands);
            private boolean fromHands;
            private int phase;
            private int place;
            private int taken;
            private int given;

            @Override
            public Outcome tick(ServerLevel level) {
                while (phase == 0) {
                    if (place >= froms.size() || taken >= most) {
                        phase = 1;
                        place = inTurn ? e.turn(act, tos.size()) : 0;
                        break;
                    }
                    Value source = froms.get(place);
                    BlockPos stand = standing(a, source);
                    if (stand == null) {
                        // it already holds what it holds
                        fromHands = true;
                        taken = most;
                        continue;
                    }
                    switch (a.walk(stand, HANDS)) {
                        case ON_THE_WAY -> {
                            a.status("residual.agent.status.fetching", pick.thing());
                            return null;
                        }
                        case LOST -> {
                            place++;
                            continue;
                        }
                        case HERE -> {
                        }
                    }
                    Places.Ends src = Places.items(a, source);
                    if (src != null) {
                        int got = Places.move(src, cargo, pick, most - taken);
                        taken += got;
                        if (got > 0) {
                            a.handle(stand);
                        }
                    }
                    place++;
                }
                for (int tried = 0; tried < tos.size() && given < most; tried++) {
                    Value target = tos.get(place % tos.size());
                    BlockPos stand = standing(a, target);
                    if (stand == null) {
                        return Outcome.done("residual.program.took", taken, pick.thing());
                    }
                    switch (a.walk(stand, HANDS)) {
                        case ON_THE_WAY -> {
                            a.status("residual.agent.status.putting_away");
                            return null;
                        }
                        case LOST -> {
                            return Outcome.failed("residual.program.failed.unreachable");
                        }
                        case HERE -> {
                        }
                    }
                    Places.Ends dst = Places.items(a, target);
                    if (dst != null) {
                        int put = Places.move(fromHands ? hands : cargo, dst, pick, most - given);
                        given += put;
                        if (put > 0) {
                            a.handle(stand);
                        }
                        if (put > 0 && inTurn) {
                            e.turned(act, place + 1);
                            break;
                        }
                    }
                    place++;
                }
                spent(a);
                return Outcome.done("residual.program.sent", given);
            }
        };
    }

    // ---------------------------------------------------------------- fluids

    /** Fluid between tanks, its own, and the world: from a source block, onto a place it can stand. */
    static Acts.Action pump(Body a, Eval e, Program.Act act) {
        int want = count(e, act);
        Picker pick = pick(e, act);
        Value from = clause(a, e, act, "from", a.legs() ? "hands" : "front");
        Value to = clause(a, e, act, "to", "hands");
        List<Value> froms = places(e, from), tos = places(e, to);
        return new Operation(a) {
            private int pumped;

            @Override
            public Outcome tick(ServerLevel level) {
                int most = want > 0 ? Math.min(want - pumped, a.fluidPerRun()) : a.fluidPerRun();
                int moved = 0;
                for (Value source : froms) {
                    if (moved >= most) {
                        break;
                    }
                    if (a.legs() && !walked(source)) {
                        return null;
                    }
                    if (!a.legs() && !reaches(a, source)) {
                        continue;
                    }
                    for (Value target : tos) {
                        if (moved >= most) {
                            break;
                        }
                        if (a.legs() && !walked(target)) {
                            return null;
                        }
                        if (a.legs() || reaches(a, target)) {
                            int went = pour(level, a, source, target, pick, most - moved);
                            moved += went;
                            reached(a, target, went);
                            reached(a, source, went);
                        }
                    }
                }
                a.dry(moved > 0);
                pumped += moved;
                spent(a);
                return want > pumped && moved > 0 ? null : Outcome.done("residual.program.pumped", pumped);
            }

            private boolean walked(Value place) {
                BlockPos stand = standing(a, place);
                return stand == null || a.walk(stand, HANDS) != Body.Arrival.ON_THE_WAY;
            }
        };
    }

    /** Pours up to {@code most} from one place to another; returns how much went. */
    private static int pour(ServerLevel level, Body a, Value source, Value target, Picker pick, int most) {
        if (isNetwork(source) || isNetwork(target)) {
            StorageNetwork net = Places.network(a);
            ResourceHandler<FluidResource> tank = Places.fluids(a, isNetwork(source) ? target : source);
            if (net == null || tank == null) {
                return 0;
            }
            int room = !a.legs() ? net.allowance(a.place(), most / DockBlockEntity.FLUID_UNIT) * DockBlockEntity.FLUID_UNIT : most;
            int went = isNetwork(source) ? outOf(net, tank, pick, room) : into(net, tank, pick, room);
            if (!a.legs()) {
                net.carry(a.place(), went / DockBlockEntity.FLUID_UNIT);
            }
            return went;
        }
        ResourceHandler<FluidResource> from = Places.fluids(a, source), to = Places.fluids(a, target);
        if (from != null && to != null) {
            return flow(from, to, pick, most);
        }
        if (from == null && to != null) {
            return lift(level, a, source, to, pick, most);
        }
        if (from != null) {
            return lay(level, a, from, target, pick, most);
        }
        return 0;
    }

    /** From a tank into what a network keeps. */
    private static int into(StorageNetwork net, ResourceHandler<FluidResource> from, Picker pick, int most) {
        int moved = 0;
        for (int slot = 0; slot < from.size() && moved < most; slot++) {
            FluidResource kind = from.getResource(slot);
            if (kind.isEmpty() || !pick.passes(kind)) {
                continue;
            }
            int fits;
            try (Transaction tx = Transaction.openRoot()) {
                fits = (int) net.insert(kind, from.extract(slot, kind, most - moved, tx), true);
            }
            if (fits > 0) {
                try (Transaction tx = Transaction.openRoot()) {
                    if (from.extract(slot, kind, fits, tx) == fits) {
                        net.insert(kind, fits, false);
                        tx.commit();
                        moved += fits;
                    }
                }
            }
        }
        return moved;
    }

    /** Out of what a network keeps, into a tank. */
    private static int outOf(StorageNetwork net, ResourceHandler<FluidResource> to, Picker pick, int most) {
        int moved = 0;
        for (FluidResource kind : List.copyOf(net.fluids().keySet())) {
            if (moved >= most) {
                break;
            }
            if (pick.passes(kind)) {
                try (Transaction tx = Transaction.openRoot()) {
                    int fits = to.insert(kind, (int) net.extract(kind, most - moved, true), tx);
                    if (fits > 0 && net.extract(kind, fits, false) == fits) {
                        tx.commit();
                        moved += fits;
                    }
                }
            }
        }
        return moved;
    }

    private static int flow(ResourceHandler<FluidResource> from, ResourceHandler<FluidResource> to, Picker pick, int most) {
        int moved = 0;
        for (int slot = 0; slot < from.size() && moved < most; slot++) {
            FluidResource kind = from.getResource(slot);
            if (kind.isEmpty() || !pick.passes(kind)) {
                continue;
            }
            // how much would go, tried and undone, then exactly that
            int fits;
            try (Transaction tx = Transaction.openRoot()) {
                int took = from.extract(slot, kind, most - moved, tx);
                fits = took <= 0 ? 0 : to.insert(kind, took, tx);
            }
            if (fits > 0) {
                try (Transaction tx = Transaction.openRoot()) {
                    int took = from.extract(slot, kind, fits, tx);
                    if (took == fits && to.insert(kind, took, tx) == fits) {
                        tx.commit();
                        moved += fits;
                    }
                }
            }
        }
        return moved;
    }

    /** A source block at {@code source} into a tank, a bucketful at a time. */
    private static int lift(ServerLevel level, Body a, Value source, ResourceHandler<FluidResource> to, Picker pick, int most) {
        Value.Place p = Places.place(a, source);
        if (p == null || most < 1000 || !level.isLoaded(p.pos())) {
            return 0;
        }
        BlockState state = level.getBlockState(p.pos());
        FluidState fluid = level.getFluidState(p.pos());
        if (!fluid.isSource() || !(state.getBlock() instanceof BucketPickup pickup)) {
            return 0;
        }
        FluidResource kind = FluidResource.of(fluid.getType());
        if (!pick.passes(kind)) {
            return 0;
        }
        try (Transaction tx = Transaction.openRoot()) {
            if (to.insert(kind, 1000, tx) < 1000) {
                return 0;
            }
            if (pickup.pickupBlock(null, level, p.pos(), state).isEmpty()) {
                return 0;
            }
            tx.commit();
            return 1000;
        }
    }

    /** A bucketful from a tank as a source block at {@code target}. */
    private static int lay(ServerLevel level, Body a, ResourceHandler<FluidResource> from, Value target, Picker pick, int most) {
        Value.Place p = Places.place(a, target);
        if (p == null || most < 1000 || !level.isLoaded(p.pos()) || !level.getBlockState(p.pos()).canBeReplaced()) {
            return 0;
        }
        for (int slot = 0; slot < from.size(); slot++) {
            FluidResource kind = from.getResource(slot);
            if (kind.isEmpty() || !pick.passes(kind)) {
                continue;
            }
            try (Transaction tx = Transaction.openRoot()) {
                if (from.extract(slot, kind, 1000, tx) == 1000) {
                    level.setBlockAndUpdate(p.pos(), kind.getFluid().defaultFluidState().createLegacyBlock());
                    tx.commit();
                    return 1000;
                }
            }
        }
        return 0;
    }

    /** Fluid out of its own tank, gone. */
    private static Acts.Action drain(Body a, Eval e, Program.Act act) {
        int want = count(e, act);
        Picker pick = pick(e, act);
        return level -> {
            int most = want > 0 ? want : Integer.MAX_VALUE;
            int gone = 0;
            ResourceHandler<FluidResource> tank = a.tank();
            for (int slot = 0; slot < tank.size() && gone < most; slot++) {
                FluidResource kind = tank.getResource(slot);
                if (!kind.isEmpty() && pick.passes(kind)) {
                    try (Transaction tx = Transaction.openRoot()) {
                        gone += tank.extract(slot, kind, most - gone, tx);
                        tx.commit();
                    }
                }
            }
            return Outcome.done("residual.program.pumped", gone);
        };
    }

    // ---------------------------------------------------------------- energy

    /** Energy between places: from every source to every target, up to what an operation moves. */
    static Acts.Action charge(Body a, Eval e, Program.Act act) {
        int want = count(e, act);
        Value from = clause(a, e, act, "from", a.legs() ? "hands" : "front");
        Value to = clause(a, e, act, "to", "hands");
        List<Value> froms = places(e, from), tos = places(e, to);
        return new Operation(a) {
            private long charged;

            @Override
            public Outcome tick(ServerLevel level) {
                int most = want > 0 ? (int) Math.min(want - charged, a.energyPerRun()) : a.energyPerRun();
                int moved = 0;
                for (Value source : froms) {
                    if (moved >= most) {
                        break;
                    }
                    if (a.legs()) {
                        BlockPos stand = standing(a, source);
                        if (stand != null && a.walk(stand, HANDS) == Body.Arrival.ON_THE_WAY) {
                            return null;
                        }
                    } else if (!reaches(a, source)) {
                        continue;
                    }
                    EnergyHandler src = Places.energy(a, source);
                    if (src == null) {
                        continue;
                    }
                    for (Value target : tos) {
                        if (moved >= most) {
                            break;
                        }
                        if (!a.legs() && !reaches(a, target)) {
                            continue;
                        }
                        EnergyHandler dst = Places.energy(a, target);
                        if (dst != null && dst != src) {
                            try (Transaction tx = Transaction.openRoot()) {
                                int went = EnergyHandlerUtil.move(src, dst, most - moved, tx);
                                moved += went;
                                tx.commit();
                                reached(a, target, went);
                            }
                        }
                    }
                }
                a.dry(moved > 0);
                charged += moved;
                spent(a);
                return want > charged && moved > 0 ? null : Outcome.done("residual.program.charged", charged);
            }
        };
    }

    // ---------------------------------------------------------------- dropping

    /** Takes up to {@code most} of what {@code pick} passes out of its hands, one kind. */
    private static ItemStack fromHands(Body a, Picker pick, int most) {
        for (int i = 0; i < a.carry(); i++) {
            ItemStack s = a.items().getItem(i);
            if (!s.isEmpty() && pick.passes(s)) {
                ItemStack out = s.split(Math.min(most, s.getCount()));
                a.items().setChanged();
                return out;
            }
        }
        return ItemStack.EMPTY;
    }

    private static Acts.Action drop(Body a, Eval e, Program.Act act) {
        int want = count(e, act);
        Picker pick = pick(e, act);
        Value where = clause(a, e, act, "at", "front");
        return new Operation(a) {
            @Override
            public Outcome tick(ServerLevel level) {
                Value.Place p = Places.place(a, where);
                if (p == null || !a.reaches(p.pos()) && !a.legs()) {
                    return Outcome.failed("residual.program.failed.no_way");
                }
                ItemStack out = fromHands(a, pick, want > 0 ? want : a.itemsPerRun());
                if (out.isEmpty()) {
                    a.dry(false);
                    return Outcome.done("residual.program.dropped", 0);
                }
                int n = out.getCount();
                Vec3 at = Vec3.atCenterOf(p.pos());
                ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, out);
                item.setDeltaMovement(Vec3.ZERO);
                item.setDefaultPickUpDelay();
                level.addFreshEntity(item);
                a.swing();
                a.dry(true);
                spent(a);
                return Outcome.done("residual.program.dropped", n);
            }
        };
    }

    private static Acts.Action fling(Body a, Eval e, Program.Act act) {
        int want = count(e, act);
        Picker pick = pick(e, act);
        double speed = act.clause("speed") == null ? 0.5 : Math.clamp(Eval.num(e.eval(act.clause("speed"))), 0, 20);
        double pitch = act.clause("pitch") == null ? 0 : Math.clamp(Eval.num(e.eval(act.clause("pitch"))), -90, 90);
        double yaw = act.clause("yaw") == null ? 0 : Math.clamp(Eval.num(e.eval(act.clause("yaw"))), -60, 60);
        Value where = clause(a, e, act, "at", "front");
        return new Operation(a) {
            @Override
            public Outcome tick(ServerLevel level) {
                Value.Place p = Places.place(a, where);
                if (p == null) {
                    return Outcome.failed("residual.program.failed.no_way");
                }
                ItemStack out = fromHands(a, pick, want > 0 ? want : a.itemsPerRun());
                if (out.isEmpty()) {
                    a.dry(false);
                    return Outcome.done("residual.program.dropped", 0);
                }
                int n = out.getCount();
                Direction d = a.facing();
                double base = Math.atan2(d.getStepZ(), d.getStepX()) + Math.toRadians(yaw);
                double up = Math.toRadians(pitch);
                Vec3 at = Vec3.atCenterOf(p.pos());
                ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, out);
                item.setDeltaMovement(Math.cos(base) * Math.cos(up) * speed, Math.sin(up) * speed, Math.sin(base) * Math.cos(up) * speed);
                item.setDefaultPickUpDelay();
                level.addFreshEntity(item);
                a.swing();
                a.dry(true);
                spent(a);
                return Outcome.done("residual.program.flung", n);
            }
        };
    }

    private static Acts.Action voiding(Body a, Eval e, Program.Act act) {
        int want = count(e, act);
        Picker pick = pick(e, act);
        return new Operation(a) {
            @Override
            public Outcome tick(ServerLevel level) {
                ItemStack out = fromHands(a, pick, want > 0 ? want : a.itemsPerRun());
                a.dry(!out.isEmpty());
                spent(a);
                return Outcome.done("residual.program.voided", out.getCount());
            }
        };
    }

    // ---------------------------------------------------------------- blocks

    /** Top down, so a body working through an area from above does not take the floor from under what it has yet to reach. */
    private static Iterator<BlockPos> topDown(Eval e, Value where) {
        if (where instanceof Value.Region r) {
            return r.area().downward();
        }
        List<BlockPos> out = new ArrayList<>();
        for (Value v : places(e, where)) {
            Value.Place p = Places.place(e.body, v);
            if (p != null) {
                out.add(p.pos());
            }
        }
        return out.iterator();
    }

    private static boolean breakable(ServerLevel level, BlockPos p, BlockState s) {
        return !s.isAir() && s.getDestroySpeed(level, p) >= 0 && (s.getFluidState().isEmpty() || !s.getFluidState().isSource() || !s.liquid());
    }

    /** Whether a block passes: as itself, or by what it drops. */
    private static boolean passes(ServerLevel level, Body a, BlockPos p, BlockState s, Picker pick) {
        if (pick == Picks.ALL || pick.passes(s)) {
            return true;
        }
        for (ItemStack drop : Block.getDrops(s, level, p, level.getBlockEntity(p), a.entity(), ItemStack.EMPTY)) {
            if (pick.passes(drop)) {
                return true;
            }
        }
        return false;
    }

    /** Breaks blocks: up to a number, or all that pass in what {@code at} names, the block in front if nothing. */
    static Acts.Action dig(Body a, Eval e, Program.Act act) {
        int want = count(e, act);
        Picker pick = pick(e, act);
        Value where = clause(a, e, act, "at", "front");
        Iterator<BlockPos> targets = topDown(e, where);
        return new Operation(a) {
            @Nullable
            private BlockPos target;
            private int progress;
            private int dug;
            private final Set<BlockPos> skipped = new HashSet<>();

            @Override
            public Outcome tick(ServerLevel level) {
                if (target == null) {
                    for (int looks = 0; target == null; looks++) {
                        if (!targets.hasNext()) {
                            a.dry(dug > 0);
                            return Outcome.done("residual.program.dug", dug);
                        }
                        if (looks >= LOOKS) {
                            return null;
                        }
                        BlockPos p = targets.next();
                        BlockState s = level.isLoaded(p) ? level.getBlockState(p) : null;
                        if (s != null && breakable(level, p, s) && !p.equals(a.place()) && !skipped.contains(p) && passes(level, a, p, s, pick)) {
                            target = p;
                        }
                    }
                    progress = 0;
                }
                BlockState state = level.getBlockState(target);
                if (!breakable(level, target, state)) {
                    target = null;
                    return null;
                }
                a.status("residual.agent.status.digging", target.toShortString());
                switch (a.walk(target, TOOL)) {
                    case ON_THE_WAY -> {
                        return null;
                    }
                    case LOST -> {
                        skipped.add(target);
                        target = null;
                        return null;
                    }
                    case HERE -> {
                    }
                }
                int tool = Acts.slot(a, s -> s.isCorrectToolForDrops(state));
                if (state.requiresCorrectToolForDrops() && tool < 0) {
                    return Outcome.failed("residual.program.failed.needs_tool", state.getBlock().getName().getString());
                }
                a.look(Vec3.atCenterOf(target));
                // a body with legs hews it out with its hands; a Port's operation takes the block whole
                if (a.legs()) {
                    int time = Math.max(a.interval(), (int) (state.getDestroySpeed(level, target) * 12) >> a.speed());
                    if (++progress < time) {
                        level.destroyBlockProgress(a.id(), target, progress * 10 / time);
                        if (progress % 5 == 0) {
                            a.swing();
                        }
                        return null;
                    }
                    level.destroyBlockProgress(a.id(), target, -1);
                }
                ItemStack used = tool < 0 ? ItemStack.EMPTY : a.items().getItem(tool);
                for (ItemStack drop : Block.getDrops(state, level, target, level.getBlockEntity(target), a.entity(), used)) {
                    ItemStack rest = a.keep(drop);
                    if (!rest.isEmpty()) {
                        Block.popResource(level, target, rest);
                    }
                }
                level.destroyBlock(target, false, a.entity());
                if (!used.isEmpty()) {
                    used.hurtAndBreak(1, level, a.entity() instanceof LivingEntity l ? l : null, item -> {
                    });
                    a.items().setChanged();
                }
                a.reached(target, 1);
                a.swing();
                target = null;
                dug++;
                if (!a.legs()) {
                    spent(a);
                }
                return want > 0 && dug >= want ? Outcome.done("residual.program.dug", dug) : null;
            }
        };
    }

    /** Places a block of a thing in every free place of what {@code at} names. */
    static Acts.Action placeAt(Body a, Eval e, Program.Act act) {
        Picker pick = pick(e, act);
        Value where = clause(a, e, act, "at", "front");
        Iterator<BlockPos> targets = bottomUp(e, where);
        return new Operation(a) {
            @Nullable
            private BlockPos target;
            private int placed;

            @Override
            public Outcome tick(ServerLevel level) {
                if (target == null) {
                    for (int looks = 0; target == null; looks++) {
                        if (!targets.hasNext()) {
                            a.dry(placed > 0);
                            return Outcome.done("residual.program.placed", placed);
                        }
                        if (looks >= LOOKS) {
                            return null;
                        }
                        BlockPos p = targets.next();
                        if (level.isLoaded(p) && level.getBlockState(p).canBeReplaced() && !p.equals(a.place())) {
                            target = p;
                        }
                    }
                }
                int slot = Acts.slot(a, s -> s.getItem() instanceof BlockItem && pick.passes(s));
                if (slot < 0) {
                    return placed > 0 ? Outcome.done("residual.program.placed", placed)
                            : Outcome.failed("residual.program.failed.not_carrying", pick.thing());
                }
                a.status("residual.agent.status.placing_at", pick.thing(), target.toShortString());
                switch (a.walk(target, TOOL)) {
                    case ON_THE_WAY -> {
                        return null;
                    }
                    case LOST -> {
                        target = null;
                        return null;
                    }
                    case HERE -> {
                    }
                }
                ItemStack s = a.items().getItem(slot);
                BlockItem item = (BlockItem) s.getItem();
                Direction facing = a.facing();
                a.reached(target, 1);
                boolean done = item.place(new DirectionalPlaceContext(level, target, facing.getAxis().isVertical() ? Direction.NORTH : facing, s, Direction.UP)).consumesAction();
                a.items().setChanged();
                a.swing();
                target = null;
                placed += done ? 1 : 0;
                spent(a);
                return null;
            }
        };
    }

    private static Iterator<BlockPos> bottomUp(Eval e, Value where) {
        if (where instanceof Value.Region r) {
            return r.area().places();
        }
        return topDown(e, where);
    }

    /** A right click as a player would, with what it names in hand, on a block or an entity. */
    private static Acts.Action use(Body a, Eval e, Program.Act act) {
        Picker pick = act.what().all() ? null : pick(e, act);
        Value where = clause(a, e, act, "at", "front");
        boolean sneak = act.has("sneak");
        Iterator<Value> targets = places(e, where).iterator();
        return new Operation(a) {
            @Nullable
            private Value target;
            private int used;

            @Override
            public Outcome tick(ServerLevel level) {
                if (target == null) {
                    if (!targets.hasNext()) {
                        return Outcome.done("residual.program.used", used);
                    }
                    target = targets.next();
                }
                BlockPos stand = standing(a, target);
                if (stand == null) {
                    return Outcome.failed("residual.program.failed.no_way");
                }
                a.status("residual.agent.status.using", stand.toShortString());
                switch (a.walk(stand, a.legs() ? 4.5 : 0)) {
                    case ON_THE_WAY -> {
                        return null;
                    }
                    case LOST -> {
                        target = null;
                        return null;
                    }
                    case HERE -> {
                    }
                }
                int slot = pick == null ? a.selected() : Running.held(a, pick);
                if (pick != null && slot < 0) {
                    return Outcome.failed("residual.program.failed.not_carrying", pick.thing());
                }
                if (slot >= 0 && a.items().getItem(slot).isEmpty()) {
                    slot = -1;
                }
                Value.Place p = Places.place(a, target);
                a.reached(target instanceof Value.Creature c ? c.entity().blockPosition() : p != null ? p.pos() : a.place(), 1);
                boolean did = target instanceof Value.Creature c ? Hands.use(a, slot, c.entity(), sneak)
                        : p != null && Hands.use(a, slot, p.pos(), p.face(), sneak);
                a.swing();
                used += did ? 1 : 0;
                target = null;
                spent(a);
                return null;
            }
        };
    }

    /** A blow at what is at a place, or at the entities named, with what it holds. */
    private static Acts.Action attack(Body a, Eval e, Program.Act act) {
        Value where = clause(a, e, act, "at", "front");
        return new Operation(a) {
            private int struck;

            @Override
            public Outcome tick(ServerLevel level) {
                List<Entity> targets = new ArrayList<>();
                for (Value v : places(e, where)) {
                    if (v instanceof Value.Creature c) {
                        targets.add(c.entity());
                    } else {
                        Value.Place p = Places.place(a, v);
                        if (p != null) {
                            targets.addAll(level.getEntities((Entity) null, new AABB(p.pos()), Hands::alive));
                        }
                    }
                }
                Entity target = null;
                for (Entity x : targets) {
                    if (Hands.alive(x) && x != a.entity()) {
                        target = x;
                        break;
                    }
                }
                if (target == null) {
                    return struck > 0 ? Outcome.done("residual.program.struck", struck) : Outcome.done("residual.program.struck", 0);
                }
                switch (a.walk(target.blockPosition(), a.legs() ? 2.5 : 0)) {
                    case ON_THE_WAY -> {
                        return null;
                    }
                    case LOST -> {
                        return Outcome.failed("residual.program.failed.out_of_reach");
                    }
                    case HERE -> {
                    }
                }
                int slot = a.items().getItem(a.selected()).isEmpty() ? -1 : a.selected();
                Hands.attack(a, slot, target);
                a.reached(target.blockPosition(), 1);
                a.swing();
                spent(a);
                return Outcome.done("residual.program.struck", ++struck);
            }
        };
    }

    private static Acts.Action emit(Body a, Eval e, Program.Act act) {
        int strength = Math.clamp(act.n() == null ? 15 : (int) Eval.num(e.eval(act.n())), 0, 15);
        return level -> {
            a.emit(strength);
            return Outcome.DONE;
        };
    }

    /** A signal for a moment. */
    private static Acts.Action pulse(Body a, Eval e, Program.Act act) {
        int strength = Math.clamp(act.n() == null ? 15 : (int) Eval.num(e.eval(act.n())), 0, 15);
        int ticks = act.clause("for") == null ? 2 : Math.max(1, (int) Eval.num(e.eval(act.clause("for"))));
        return new Acts.Action() {
            private boolean on;

            @Override
            public Outcome tick(ServerLevel level) {
                if (on) {
                    a.emit(0);
                    return Outcome.DONE;
                }
                on = true;
                a.emit(strength);
                a.pause(ticks);
                return null;
            }
        };
    }

    /** The direction a clause names, as a word for one of its sides, or the way it faces. */
    private static Direction way(Body a, Program.Act act) {
        Expr x = act.clause("at");
        return x instanceof Expr.Spot s && !s.word().equals("here") ? Places.side(a.facing(), s.word()) : a.facing();
    }

    /** A line of blocks out from it, one an operation; as a bridge reaches out, and comes back with {@link #retract}. */
    /** Where a line laid out from a body begins: above a body's head, if it goes up. */
    private static BlockPos lineFrom(Body a, Direction d) {
        return d == Direction.UP && a.entity() != null ? a.place().above((int) Math.ceil(a.entity().getBbHeight()) - 1) : a.place();
    }

    private static Acts.Action extrude(Body a, Eval e, Program.Act act) {
        int want = Math.max(1, count(e, act));
        Picker pick = pick(e, act);
        Direction d = way(a, act);
        String record = "@extruded_" + d.getName();
        return new Operation(a) {
            private int laid;

            @Override
            public Outcome tick(ServerLevel level) {
                int out = (int) Eval.num(e.get(record));
                if (laid >= want || out >= Math.min(LONGEST_EXTRUSION, a.reach() + 16)) {
                    return Outcome.done("residual.program.placed", laid);
                }
                BlockPos at = lineFrom(a, d).relative(d, out + 1);
                int slot = Acts.slot(a, s -> s.getItem() instanceof BlockItem && pick.passes(s));
                if (slot < 0) {
                    return laid > 0 ? Outcome.done("residual.program.placed", laid) : Outcome.failed("residual.program.failed.not_carrying", pick.thing());
                }
                if (!level.getBlockState(at).canBeReplaced()) {
                    return Outcome.failed("residual.program.failed.in_the_way", at.toShortString());
                }
                ItemStack s = a.items().getItem(slot);
                BlockItem item = (BlockItem) s.getItem();
                if (!item.place(new DirectionalPlaceContext(level, at, d, s, Direction.UP)).consumesAction()) {
                    return Outcome.failed("residual.program.failed.in_the_way", at.toShortString());
                }
                a.items().setChanged();
                e.set(record, Value.of(out + 1));
                a.reached(at, 1);
                a.swing();
                laid++;
                spent(a);
                return null;
            }
        };
    }

    private static Acts.Action retract(Body a, Eval e, Program.Act act) {
        int want = count(e, act);
        Direction d = way(a, act);
        String record = "@extruded_" + d.getName();
        return new Operation(a) {
            private int taken;

            @Override
            public Outcome tick(ServerLevel level) {
                int out = (int) Eval.num(e.get(record));
                if (out <= 0 || want > 0 && taken >= want) {
                    return Outcome.done("residual.program.dug", taken);
                }
                BlockPos at = lineFrom(a, d).relative(d, out);
                BlockState state = level.getBlockState(at);
                if (!state.isAir() && state.getDestroySpeed(level, at) >= 0) {
                    ItemStack back = new ItemStack(state.getBlock().asItem());
                    level.destroyBlock(at, false, a.entity());
                    ItemStack rest = a.keep(back);
                    if (!rest.isEmpty()) {
                        Block.popResource(level, a.place(), rest);
                    }
                }
                e.set(record, Value.of(out - 1));
                a.swing();
                taken++;
                spent(a);
                return null;
            }
        };
    }

    // ---------------------------------------------------------------- the body

    /** A number of blocks ahead, back, up or down, turning no way, as a turtle goes. */
    private static Acts.Action walkOn(Body a, Eval e, Program.Act act) {
        int want = Math.max(1, count(e, act));
        Direction facing = a.facing();
        return new Operation(a) {
            private int gone;
            @Nullable
            private BlockPos to;

            @Override
            public Outcome tick(ServerLevel level) {
                if (!a.legs()) {
                    return Outcome.failed("residual.program.failed.no_legs");
                }
                if (to == null) {
                    if (gone >= want) {
                        a.face(facing);
                        return Outcome.DONE;
                    }
                    Direction d = switch (act.verb()) {
                        case FORWARD -> facing;
                        case BACK -> facing.getOpposite();
                        case UP -> Direction.UP;
                        default -> Direction.DOWN;
                    };
                    if (d.getAxis().isVertical() && !a.flying()) {
                        return Outcome.failed("residual.program.failed.no_wings");
                    }
                    to = a.place().relative(d);
                    if (!level.getBlockState(to).getCollisionShape(level, to).isEmpty()) {
                        return gone > 0 ? Outcome.DONE : Outcome.failed("residual.program.failed.in_the_way", to.toShortString());
                    }
                    a.stepInto(to);
                }
                if (a.place().equals(to)) {
                    to = null;
                    gone++;
                    spent(a);
                    return null;
                }
                a.stepInto(to);
                if (a.walk(to, 0.6) == Body.Arrival.LOST) {
                    return gone > 0 ? Outcome.DONE : Outcome.failed("residual.program.failed.unreachable");
                }
                return null;
            }
        };
    }

    private static Acts.Action turn(Body a, Program.Act act) {
        String word = act.words();
        return level -> {
            Direction d = a.facing();
            Direction flat = d.getAxis().isVertical() ? Direction.NORTH : d;
            a.face(switch (word) {
                case "left" -> flat.getCounterClockWise();
                case "right" -> flat.getClockWise();
                case "around" -> flat.getOpposite();
                default -> {
                    Direction named = Direction.byName(word);
                    yield named == null ? d : named;
                }
            });
            return Outcome.DONE;
        };
    }

    private static Acts.Action teleport(Body a, Eval e, Program.Act act) {
        Value where = e.eval(act.clause("place"));
        return level -> {
            Value.Place p = Places.place(a, where);
            if (p == null) {
                return Outcome.failed("residual.program.failed.no_mark", where.show());
            }
            if (!a.teleport(p.pos())) {
                return Outcome.failed("residual.program.failed.no_teleport");
            }
            return Outcome.DONE;
        };
    }

    private static Acts.Action select(Body a, Eval e, Program.Act act) {
        int slot = count(e, act) - 1;
        return level -> {
            if (slot < 0 || slot >= a.carry()) {
                return Outcome.failed("residual.program.failed.no_slot", slot + 1, a.carry());
            }
            a.select(slot);
            return Outcome.DONE;
        };
    }

    private static Acts.Action transfer(Body a, Eval e, Program.Act act) {
        int want = count(e, act);
        int to = (int) Eval.num(e.eval(act.clause("to"))) - 1;
        return new Operation(a) {
            @Override
            public Outcome tick(ServerLevel level) {
                if (to < 0 || to >= a.carry()) {
                    return Outcome.failed("residual.program.failed.no_slot", to + 1, a.carry());
                }
                ItemStack from = a.items().getItem(a.selected());
                ItemStack into = a.items().getItem(to);
                int moved = 0;
                if (!from.isEmpty() && a.selected() != to) {
                    if (into.isEmpty()) {
                        moved = Math.min(want > 0 ? want : 64, from.getCount());
                        a.items().setItem(to, from.split(moved));
                    } else if (ItemStack.isSameItemSameComponents(from, into)) {
                        moved = Math.min(Math.min(want > 0 ? want : 64, from.getCount()), into.getMaxStackSize() - into.getCount());
                        into.grow(moved);
                        from.shrink(moved);
                    }
                }
                a.items().setChanged();
                spent(a);
                return Outcome.done("residual.program.moved", moved);
            }
        };
    }

    private static Acts.Action equip(Body a, Eval e, Program.Act act) {
        Picker pick = pick(e, act);
        return level -> {
            if (pick == Picks.ALL) {
                a.select(0);
                return Outcome.DONE;
            }
            int slot = Running.held(a, pick);
            if (slot < 0) {
                return Outcome.failed("residual.program.failed.not_carrying", pick.thing());
            }
            a.select(slot);
            return Outcome.DONE;
        };
    }

    private static Acts.Action refuel(Body a, Eval e, Program.Act act) {
        int want = count(e, act);
        Picker pick = pick(e, act);
        return new Operation(a) {
            @Override
            public Outcome tick(ServerLevel level) {
                long burned = 0;
                int left = want > 0 ? want : Integer.MAX_VALUE;
                EnergyHandler battery = a.battery();
                for (int i = 0; i < a.carry() && left > 0; i++) {
                    ItemStack s = a.items().getItem(i);
                    int burn = s.isEmpty() || !pick.passes(s) ? 0 : level.fuelValues().burnDuration(s);
                    while (burn > 0 && left > 0 && !s.isEmpty()) {
                        int energy = burn * FUEL_ENERGY;
                        if (battery.getCapacityAsLong() - battery.getAmountAsLong() < energy) {
                            left = 0;
                            break;
                        }
                        try (Transaction tx = Transaction.openRoot()) {
                            if (battery.insert(energy, tx) < energy) {
                                left = 0;
                                break;
                            }
                            tx.commit();
                        }
                        s.shrink(1);
                        burned += energy;
                        left--;
                    }
                }
                a.items().setChanged();
                spent(a);
                return burned > 0 ? Outcome.done("residual.program.charged", burned) : Outcome.failed("residual.program.failed.no_fuel");
            }
        };
    }

    /** Takes up an entity to carry on its back, as a drone does. */
    private static Acts.Action lift(Body a, Eval e, Program.Act act) {
        Value where = clause(a, e, act, "at", "front");
        return level -> {
            if (!(a.entity() instanceof LivingEntity self)) {
                return Outcome.failed("residual.program.failed.no_legs");
            }
            for (Value v : places(e, where)) {
                List<Entity> near = v instanceof Value.Creature c ? List.of(c.entity()) : List.of();
                Value.Place p = v instanceof Value.Creature ? null : Places.place(a, v);
                if (p != null) {
                    near = level.getEntities((Entity) null, new AABB(p.pos()), x -> x != self && !(x instanceof net.minecraft.world.entity.player.Player));
                }
                for (Entity x : near) {
                    if (x != self && x.startRiding(self, true, true)) {
                        return Outcome.DONE;
                    }
                }
            }
            return Outcome.failed("residual.program.failed.nothing_there");
        };
    }

    private static Acts.Action lower(Body a) {
        return level -> {
            if (a.entity() != null) {
                a.entity().ejectPassengers();
            }
            return Outcome.DONE;
        };
    }

    private static Acts.Action rename(Body a, Eval e, Program.Act act) {
        String name = e.interpolate(act.words());
        return level -> {
            a.rename(name);
            return Outcome.DONE;
        };
    }

    private static Acts.Action sign(Body a, Eval e, Program.Act act) {
        String text = e.interpolate(act.words());
        Value where = clause(a, e, act, "at", "front");
        return level -> {
            Value.Place p = Places.place(a, where);
            if (p == null || !level.isLoaded(p.pos()) || !(level.getBlockEntity(p.pos()) instanceof SignBlockEntity sign)) {
                return Outcome.failed("residual.program.failed.no_sign");
            }
            switch (a.walk(p.pos(), a.legs() ? 4.5 : 0)) {
                case ON_THE_WAY -> {
                    return null;
                }
                case LOST -> {
                    return Outcome.failed("residual.program.failed.out_of_reach");
                }
                case HERE -> {
                }
            }
            SignText lines = new SignText();
            String[] words = text.split("/", 4);
            for (int i = 0; i < words.length; i++) {
                lines = lines.setMessage(i, Component.literal(words[i].strip()));
            }
            sign.setText(lines, true);
            return Outcome.DONE;
        };
    }

    private static Acts.Action sound(Body a, Eval e, Program.Act act) {
        String name = e.interpolate(act.words()).strip().replace(' ', '_');
        return level -> {
            Identifier id = Identifier.tryParse(name);
            SoundEvent sound = id == null ? null : BuiltInRegistries.SOUND_EVENT.getValue(id);
            if (sound == null) {
                sound = SoundEvents.NOTE_BLOCK_CHIME.value();
            }
            level.playSound(null, a.place(), sound, SoundSource.BLOCKS, 1f, 1f);
            return Outcome.DONE;
        };
    }

    private static Acts.Action particle(Body a, Eval e, Program.Act act) {
        String name = e.interpolate(act.words()).strip().replace(' ', '_');
        Value where = clause(a, e, act, "at", "here");
        return level -> {
            Identifier id = Identifier.tryParse(name);
            var type = id == null ? null : level.registryAccess().lookupOrThrow(Registries.PARTICLE_TYPE).getValue(id);
            Value.Place p = Places.place(a, where);
            if (type instanceof net.minecraft.core.particles.SimpleParticleType simple && p != null) {
                Vec3 at = Vec3.atCenterOf(p.pos());
                level.sendParticles(simple, at.x, at.y, at.z, 4, 0.2, 0.2, 0.2, 0.02);
            }
            return Outcome.DONE;
        };
    }

    /** Takes up what lies on the ground in reach, up to a number, of what passes. {@code pickup xp} takes orbs, and bottles them, as a router's vacuum does. */
    static Acts.Action vacuum(Body a, Eval e, Program.Act act) {
        int want = count(e, act);
        boolean orbs = "xp".equals(act.thing());
        Picker pick = orbs ? Picks.ALL : pick(e, act);
        Value where = act.clause("at") == null ? null : e.eval(act.clause("at"));
        return new Operation(a) {
            @Override
            public Outcome tick(ServerLevel level) {
                AABB box = box(a, where);
                int most = want > 0 ? want : a.itemsPerRun();
                int took = 0;
                if (orbs) {
                    int xp = 0;
                    for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, box)) {
                        xp += orb.getValue();
                        orb.discard();
                    }
                    if (xp > 0) {
                        // a bottle holds about seven points, as what it gives back does
                        int bottles = Math.max(1, Math.round(xp / 7f));
                        ItemStack rest = a.keep(new ItemStack(Items.EXPERIENCE_BOTTLE, bottles));
                        if (!rest.isEmpty()) {
                            Block.popResource(level, a.place(), rest);
                        }
                        took = bottles;
                    }
                } else {
                    for (ItemEntity lying : level.getEntitiesOfClass(ItemEntity.class, box, x -> x.isAlive() && !x.hasPickUpDelay() && pick.passes(x.getItem()))) {
                        if (took >= most) {
                            break;
                        }
                        ItemStack stack = lying.getItem();
                        ItemStack rest = a.keep(stack.copyWithCount(Math.min(stack.getCount(), most - took)));
                        int moved = Math.min(stack.getCount(), most - took) - rest.getCount();
                        if (moved == 0) {
                            continue;
                        }
                        a.picked(lying, moved);
                        took += moved;
                        if (moved == stack.getCount()) {
                            lying.discard();
                        } else {
                            lying.setItem(stack.copyWithCount(stack.getCount() - moved));
                        }
                    }
                }
                a.dry(took > 0);
                spent(a);
                return Outcome.done("residual.program.picked_up", took);
            }
        };
    }

    /** What an area's bounding box covers, or around the body as far as it reaches. */
    private static AABB box(Body a, @Nullable Value where) {
        if (where instanceof Value.Region r) {
            Area x = r.area();
            return new AABB(x.minX(), x.minY(), x.minZ(), x.maxX() + 1, x.maxY() + 1, x.maxZ() + 1);
        }
        if (where != null) {
            Value.Place p = Places.place(a, where);
            if (p != null) {
                return new AABB(p.pos());
            }
        }
        return new AABB(a.place()).inflate(Math.min(a.reach(), 24));
    }
}
