package io.github.metrovoc.residual.agent;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.block.SurveyorBlockEntity;
import io.github.metrovoc.residual.storage.CraftJob;
import io.github.metrovoc.residual.storage.CraftJobs;
import io.github.metrovoc.residual.storage.CraftingData;
import io.github.metrovoc.residual.storage.StorageNetwork;
import io.github.metrovoc.residual.storage.TerminalBlockEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Util;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * What the verbs of a program do. Each act is a small machine, ticked until it is over; none of them asks a model.
 * Things are named the way orders name them ({@link Things}), and stores are the chests and barrels around home, then the
 * storage networks of the terminals around home.
 */
final class Acts {
    /** How long {@code follow} keeps close, in ticks. */
    static final int FOLLOWING = 2400;
    /** How far around itself it looks for things lying about and for blocks to mine. */
    static final int NEARBY = 8;
    /** How long a block an agent picked to mine stays its own unless it picks it again, in ticks. */
    private static final int CLAIMED = 40;
    /** The blocks agents are mining, so agents mining near each other, a crew, each take a different one. */
    private static final Map<ServerLevel, Map<BlockPos, Claim>> CLAIMS = new WeakHashMap<>();

    /** A block picked by the agent with id {@code agent}, its own until tick {@code until}. */
    private record Claim(int agent, long until) {
    }

    /** An act under way. */
    interface Action {
        /** Does a little more of it: null while it goes on, then how it went. */
        @Nullable
        Outcome tick(ServerLevel level);

        /** Whether what comes next waits for the next tick, even with the budget for more: the operations that carry things. */
        default boolean yields() {
            return false;
        }
    }

    private Acts() {
    }

    private static Map<BlockPos, Claim> claims(ServerLevel level) {
        return CLAIMS.computeIfAbsent(level, l -> new HashMap<>());
    }

    /** The act for {@code act}, with what it names worked out now; {@link Running} runs fetch and the routines itself. */
    static Action of(Body a, Eval e, Program.Act act) {
        return switch (act.verb()) {
            case COME -> a.legs() ? come(a) : noLegs();
            case HOME -> a.legs() ? walk(a, a.home(), 1.5, "residual.agent.status.going_home") : noLegs();
            case GO -> a.legs() ? go(a, e, act) : noLegs();
            case STAY -> stay(a);
            case FOLLOW -> a.legs() ? follow(a) : noLegs();
            case TAKE -> act.has("at") ? Deeds.move(a, e, act, e.eval(act.clause("at")), new Value.Spot("hands")) : take(a, Deeds.count(e, act), Deeds.pick(e, act));
            case PUT -> act.has("at") ? Deeds.move(a, e, act, new Value.Spot("hands"), e.eval(act.clause("at"))) : put(a, Deeds.pick(e, act));
            case GIVE -> give(a, Deeds.count(e, act), Deeds.pick(e, act));
            case PICKUP -> a.legs() && act.n() == null && act.what().all() && !act.has("at") ? pickup(a) : Deeds.vacuum(a, e, act);
            case HARVEST -> harvest(a, area(e, act));
            case PLANT -> plant(a, Deeds.pick(e, act), area(e, act));
            case TILL -> till(a, area(e, act));
            case MINE -> mine(a, Deeds.count(e, act), Deeds.pick(e, act));
            case PLACE -> act.has("at") ? Deeds.placeAt(a, e, act) : place(a, Deeds.pick(e, act), act.mark());
            case WAIT -> waiting(a, e.eval(act.n()));
            case SAY -> {
                String said = e.interpolate(act.words());
                yield level -> {
                    a.tell(Component.literal(said));
                    return Outcome.DONE;
                };
            }
            case CRAFT -> craft(a, Deeds.count(e, act), Deeds.pick(e, act));
            case SUPPLY -> supply(a);
            case FETCH, FARM, STORE, SORT, HAUL, BUILD, COPY, MOVE, RUN, NOTE -> throw new IllegalArgumentException(act.verb().word);
            default -> Deeds.of(a, e, act);
        };
    }

    /** For a step a body with no legs cannot take. */
    private static Action noLegs() {
        return level -> Outcome.failed("residual.program.failed.no_legs");
    }

    /** What an {@code at} clause names, as an area; null with none. */
    @Nullable
    private static Area area(Eval e, Program.Act act) {
        Expr at = act.clause("at");
        if (at == null) {
            return null;
        }
        Value v = e.eval(at);
        if (v instanceof Value.Region r) {
            // a field is found all at once, so it pays for every place it holds
            e.spend((int) Math.min(r.area().volume(), Eval.FUEL + 1));
            return r.area();
        }
        Value.Place p = Places.place(e.body, v);
        return p == null ? null : Area.box(p.pos(), p.pos(), "filled");
    }

    private static Action go(Body a, Eval e, Program.Act act) {
        Value where = e.eval(act.clause("place"));
        Value.Place p = Places.place(a, where);
        if (p == null) {
            return level -> Outcome.failed("residual.program.failed.no_mark", where.show());
        }
        // a mark's block is what it stands on; a place is where it stands
        return where instanceof Value.Num n ? walk(a, p.pos().above(), 1.8, "residual.agent.status.going_to_mark", (int) n.v())
                : walk(a, p.pos(), 1.8, "residual.agent.status.going_to", p.show());
    }

    // ---------------------------------------------------------------- moving

    private static Action walk(Body a, BlockPos to, double reach, String status, Object... args) {
        return level -> {
            a.status(status, args);
            return switch (a.walk(to, reach)) {
                case HERE -> Outcome.DONE;
                case LOST -> Outcome.failed("residual.program.failed.unreachable");
                case ON_THE_WAY -> null;
            };
        };
    }

    private static Action come(Body a) {
        return level -> {
            ServerPlayer p = a.boss();
            if (p == null) {
                return Outcome.failed("residual.program.failed.no_boss");
            }
            a.status("residual.agent.status.coming", p.getName().getString());
            return switch (a.walk(p.blockPosition(), 2.5)) {
                case HERE -> {
                    a.look(p.position());
                    yield Outcome.DONE;
                }
                case LOST -> Outcome.failed("residual.program.failed.unreachable");
                case ON_THE_WAY -> null;
            };
        };
    }

    /** Stands where it is for good. */
    private static Action stay(Body a) {
        return level -> {
            a.status("residual.agent.status.staying");
            a.stand();
            a.pause(20);
            return null;
        };
    }

    private static Action follow(Body a) {
        int until = a.clock() + FOLLOWING;
        return level -> {
            ServerPlayer p = a.boss();
            if (p == null) {
                return Outcome.failed("residual.program.failed.no_boss");
            }
            if (a.clock() >= until) {
                return Outcome.DONE;
            }
            a.status("residual.agent.status.following", p.getName().getString());
            if (a.walk(p.blockPosition(), 2.5) == Body.Arrival.HERE) {
                a.look(p.position());
            }
            return null;
        };
    }

    private static Action waiting(Body a, Value howLong) {
        double seconds = Eval.num(howLong);
        return new Action() {
            private boolean waited;

            @Override
            public Outcome tick(ServerLevel level) {
                if (waited) {
                    return Outcome.DONE;
                }
                waited = true;
                a.status(seconds == 1 ? "residual.agent.status.waiting_one" : "residual.agent.status.waiting", Value.number(seconds));
                a.pause((int) Math.round(seconds * 20));
                return null;
            }
        };
    }

    // ---------------------------------------------------------------- carrying

    /** Takes {@code count} of a thing, or a stack, out of the stores around home. */
    private static Action take(Body a, int count, Picker wanted) {
        Predicate<ItemStack> any = s -> wanted.exactly(s) || wanted.loosely(s);
        return new Action() {
            private final int want = count > 0 ? count : 64;
            private final Set<BlockPos> tried = new HashSet<>();
            private int got;
            @Nullable
            private BlockPos from;

            @Override
            public Outcome tick(ServerLevel level) {
                if (from == null) {
                    from = Stores.find(level, a.home(), wanted, tried);
                    if (from == null) {
                        from = Stores.terminalWith(level, a.home(), any, tried);
                    }
                    if (from == null) {
                        return got > 0 ? took() : Outcome.failed("residual.program.failed.none_stocked", wanted.thing());
                    }
                }
                a.status("residual.agent.status.fetching", wanted.thing());
                switch (a.walk(from, 2.2)) {
                    case ON_THE_WAY -> {
                        return null;
                    }
                    case LOST -> {
                        tried.add(from);
                        from = null;
                        return null;
                    }
                    case HERE -> {
                    }
                }
                Container c = Stores.at(level, from);
                tried.add(from);
                if (c != null || level.getBlockEntity(from) instanceof TerminalBlockEntity) {
                    StorageNetwork net = c == null ? StorageNetwork.at(level, from) : null;
                    int before = got;
                    takeAll(c, net, wanted::exactly);
                    if (got == before) {
                        takeAll(c, net, wanted::loosely);
                    }
                    if (got > before) {
                        a.handle(from);
                    } else if (a.full()) {
                        return got > 0 ? took() : Outcome.failed("residual.program.failed.hands_full");
                    }
                }
                from = null;
                return got >= want || a.full() ? took() : null;
            }

            private void takeAll(@Nullable Container c, @Nullable StorageNetwork net, Predicate<ItemStack> what) {
                for (int moved = -1; moved != 0 && got < want; got += moved) {
                    moved = c != null ? Stores.fetch(c, a, what, want - got) : Stores.fetch(net, a, what, want - got);
                }
            }

            private Outcome took() {
                return Outcome.done("residual.program.took", got, wanted.thing());
            }
        };
    }

    /** Puts what it carries of a thing, or everything it carries, into the stores around home. */
    private static Action put(Body a, Picker wanted) {
        Predicate<ItemStack> what = s -> !s.isEmpty() && wanted.passes(s);
        return new Action() {
            private final Set<BlockPos> tried = new HashSet<>();
            private int put;
            @Nullable
            private BlockPos to;

            @Override
            public Outcome tick(ServerLevel level) {
                int slot = slot(a, what);
                if (slot < 0) {
                    // with none to put away, there was nothing to do
                    return Outcome.done("residual.program.put", put);
                }
                ItemStack kind = a.items().getItem(slot).copyWithCount(1);
                if (to == null) {
                    to = Stores.into(level, a.home(), kind, tried);
                    if (to == null) {
                        to = Stores.terminalFor(level, a.home(), kind, tried);
                    }
                    if (to == null) {
                        return Outcome.failed("residual.program.failed.no_room");
                    }
                }
                a.status("residual.agent.status.putting_away");
                switch (a.walk(to, 2.2)) {
                    case ON_THE_WAY -> {
                        return null;
                    }
                    case LOST -> {
                        tried.add(to);
                        to = null;
                        return null;
                    }
                    case HERE -> {
                    }
                }
                Container c = Stores.at(level, to);
                StorageNetwork net = c == null && level.getBlockEntity(to) instanceof TerminalBlockEntity ? StorageNetwork.at(level, to) : null;
                int before = put;
                for (int i = 0; i < a.carry() && (c != null || net != null); i++) {
                    ItemStack s = a.items().getItem(i);
                    if (ItemStack.isSameItemSameComponents(s, kind)) {
                        int n = s.getCount();
                        ItemStack rest = c != null ? Stores.insert(c, s) : Stores.insert(net, s);
                        put += n - rest.getCount();
                        a.items().setItem(i, rest);
                    }
                }
                a.items().setChanged();
                if (put > before) {
                    a.handle(to);
                } else {
                    tried.add(to);
                }
                to = null;
                return null;
            }
        };
    }

    /**
     * Makes a thing there and then if it has what that takes in its hands. What is still wanted of the {@code count} asked
     * for, or of one, it has made: it walks to the nearest terminal whose network can make it, asks and waits until the job
     * is done. What the job lacks it waits for like any other.
     */
    private static Action craft(Body a, int count, Picker wanted) {
        return new Action() {
            private int want = Math.max(1, count);
            private int inHand = -1;
            @Nullable
            private BlockPos at;
            private boolean asked;
            @Nullable
            private UUID job;
            @Nullable
            private Outcome over;

            @Override
            public Outcome tick(ServerLevel level) {
                if (over != null) {
                    return over;
                }
                if (inHand < 0) {
                    inHand = Crafts.make(a, level, wanted::exactly, want);
                    inHand += Crafts.make(a, level, wanted::loosely, want - inHand);
                    want -= inHand;
                    if (want <= 0) {
                        a.swing();
                        return Outcome.done("residual.program.made", inHand, wanted.thing());
                    }
                }
                if (job != null) {
                    a.status("residual.agent.status.waiting_for_craft", want, wanted.thing());
                    a.pause(10);
                    return CraftingData.of(level.getServer()).jobs().stream().anyMatch(j -> j.id.equals(job)) ? null
                            : Outcome.done("residual.program.crafted", want, wanted.thing());
                }
                if (asked) {
                    return null;
                }
                if (at == null) {
                    List<BlockPos> terminals = Stores.terminals(level, a.home());
                    if (terminals.isEmpty()) {
                        return inHand > 0 ? Outcome.done("residual.program.made", inHand, wanted.thing()) : Outcome.failed("residual.program.failed.no_terminal");
                    }
                    for (BlockPos p : terminals) {
                        if (makes(StorageNetwork.at(level, p)) != null) {
                            at = p;
                            break;
                        }
                    }
                    if (at == null) {
                        return inHand > 0 ? Outcome.done("residual.program.made", inHand, wanted.thing()) : Outcome.failed("residual.program.failed.cannot_make", wanted.thing());
                    }
                }
                a.status("residual.agent.status.asking_network", wanted.thing());
                switch (a.walk(at, 2.5)) {
                    case ON_THE_WAY -> {
                        return null;
                    }
                    case LOST -> {
                        return Outcome.failed("residual.program.failed.unreachable");
                    }
                    case HERE -> {
                    }
                }
                StorageNetwork net = StorageNetwork.at(level, at);
                ItemResource kind = makes(net);
                if (kind == null) {
                    return Outcome.failed("residual.program.failed.cannot_make", wanted.thing());
                }
                asked = true;
                a.handle(at);
                CraftJobs.plan(level, net, kind, want, planned -> {
                    if (planned.plan().root.recipe == null) {
                        over = Outcome.failed("residual.program.failed.cannot_make", wanted.thing());
                    } else {
                        job = CraftJobs.start(level, Util.NIL_UUID, at, planned).id;
                    }
                });
                return null;
            }

            /** What of the thing a network can make, the exact kind first. */
            @Nullable
            private ItemResource makes(StorageNetwork net) {
                ItemResource loose = null;
                for (ItemResource kind : CraftJobs.craftable(net)) {
                    if (wanted.exactly(kind.toStack())) {
                        return kind;
                    }
                    if (loose == null && wanted.loosely(kind.toStack())) {
                        loose = kind;
                    }
                }
                return loose;
            }
        };
    }

    /**
     * Goes after what a job on a network around home lacks: out of the chests and barrels around home if they have it, or
     * mined if it can be, and into the network. With nothing lacking there is nothing to do.
     */
    private static Action supply(Body a) {
        return new Action() {
            @Nullable
            private ItemResource kind;
            @Nullable
            private BlockPos terminal;
            @Nullable
            private Action gathering;
            private String name = "";

            @Override
            public Outcome tick(ServerLevel level) {
                if (kind == null && !choose(level)) {
                    return Outcome.done("residual.program.nothing_lacking");
                }
                if (gathering != null) {
                    Outcome how = gathering.tick(level);
                    if (how == null) {
                        return null;
                    }
                    gathering = null;
                    if (!how.ok() && carried(a, kind::matches) == 0) {
                        return Outcome.failed("residual.program.failed.cannot_supply", name);
                    }
                }
                a.status("residual.agent.status.supplying", name);
                switch (a.walk(terminal, 2.5)) {
                    case ON_THE_WAY -> {
                        return null;
                    }
                    case LOST -> {
                        return Outcome.failed("residual.program.failed.unreachable");
                    }
                    case HERE -> {
                    }
                }
                StorageNetwork net = StorageNetwork.at(level, terminal);
                int filed = 0;
                for (int i = 0; i < a.carry(); i++) {
                    ItemStack s = a.items().getItem(i);
                    if (kind.matches(s)) {
                        ItemStack rest = Stores.insert(net, s);
                        filed += s.getCount() - rest.getCount();
                        a.items().setItem(i, rest);
                    }
                }
                a.items().setChanged();
                a.handle(terminal);
                return filed > 0 ? Outcome.done("residual.program.supplied", filed, name) : Outcome.failed("residual.program.failed.no_room");
            }

            /** Picks the oldest lack among the jobs around home, and how to get it, unless it already carries some. */
            private boolean choose(ServerLevel level) {
                for (BlockPos p : Stores.terminals(level, a.home())) {
                    for (CraftJob job : CraftingData.of(level.getServer()).on(level, StorageNetwork.at(level, p))) {
                        ItemResource lacking = job.lacking();
                        if (lacking != null) {
                            kind = lacking;
                            terminal = p;
                            name = Things.name(lacking.toStack());
                            int want = (int) Math.min(job.lackingCount(), lacking.getMaxStackSize());
                            if (carried(a, lacking::matches) < want) {
                                Things.Wanted wanted = Things.of(name);
                                gathering = Stores.find(level, a.home(), wanted, Set.of()) != null ? take(a, want, wanted) : mine(a, want, Things.of(source(name)));
                            }
                            return true;
                        }
                    }
                }
                return false;
            }

            /** What to mine for a thing that comes out of the ground as something else. */
            private static String source(String thing) {
                return thing.equals("cobblestone") ? "stone" : thing.startsWith("raw ") ? thing.substring(4) : thing;
            }
        };
    }

    /** Hands {@code count} of a thing it carries, or all it carries of it, to whoever it works for. */
    private static Action give(Body a, int count, Picker wanted) {
        Predicate<ItemStack> what = s -> wanted.exactly(s) || wanted.loosely(s);
        return level -> {
            ServerPlayer p = a.boss();
            if (p == null) {
                return Outcome.failed("residual.program.failed.no_boss");
            }
            if (carried(a, what) == 0) {
                return Outcome.failed("residual.program.failed.not_carrying", wanted.thing());
            }
            a.status("residual.agent.status.bringing", wanted.thing(), p.getName().getString());
            switch (a.walk(p.blockPosition(), 2.5)) {
                case ON_THE_WAY -> {
                    return null;
                }
                case LOST -> {
                    return Outcome.failed("residual.program.failed.unreachable");
                }
                case HERE -> {
                }
            }
            int left = count > 0 ? count : Integer.MAX_VALUE;
            int given = 0;
            for (int i = 0; i < a.carry() && left > 0; i++) {
                ItemStack s = a.items().getItem(i);
                if (what.test(s)) {
                    ItemStack out = s.split(Math.min(left, s.getCount()));
                    left -= out.getCount();
                    given += out.getCount();
                    if (!p.getInventory().add(out)) {
                        p.drop(out, false);
                    }
                }
            }
            a.items().setChanged();
            a.handle(p.blockPosition());
            a.look(p.position());
            return Outcome.done("residual.program.gave", given, wanted.thing(), p.getName().getString());
        };
    }

    /** Picks up what lies on the ground around it. */
    private static Action pickup(Body a) {
        return new Action() {
            private final Set<Integer> skipped = new HashSet<>();
            private int picked;

            @Override
            public Outcome tick(ServerLevel level) {
                ItemEntity nearest = null;
                for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(a.place()).inflate(NEARBY),
                        e -> e.isAlive() && !e.hasPickUpDelay() && !skipped.contains(e.getId()))) {
                    if (nearest == null || e.distanceToSqr(a.center()) < nearest.distanceToSqr(a.center())) {
                        nearest = e;
                    }
                }
                if (nearest == null || a.full()) {
                    return Outcome.done("residual.program.picked_up", picked);
                }
                a.status("residual.agent.status.picking_up");
                switch (a.walk(nearest.blockPosition(), 1.2)) {
                    case ON_THE_WAY -> {
                        return null;
                    }
                    case LOST -> {
                        skipped.add(nearest.getId());
                        return null;
                    }
                    case HERE -> {
                    }
                }
                ItemStack lying = nearest.getItem();
                ItemStack rest = a.keep(lying.copy());
                int took = lying.getCount() - rest.getCount();
                if (took == 0) {
                    skipped.add(nearest.getId());
                    return null;
                }
                a.picked(nearest, took);
                picked += took;
                if (rest.isEmpty()) {
                    nearest.discard();
                } else {
                    nearest.setItem(rest);
                }
                return null;
            }
        };
    }

    // ---------------------------------------------------------------- fields

    /** Harvests every ripe crop on the farmland around home, row by row. */
    private static Action harvest(Body a, @Nullable Area area) {
        return new Plots(a, area, Farming.RIPE, "residual.agent.status.harvesting") {
            @Override
            boolean ready() {
                return !a.full();
            }

            @Override
            void work(ServerLevel level, BlockPos plot) {
                Farming.reap(level, a, plot.above());
            }

            @Override
            Outcome over(boolean stopped) {
                return stopped ? Outcome.failed("residual.program.failed.hands_full") : Outcome.done("residual.program.harvested", count);
            }
        };
    }

    /** Plants a thing on every bare plot around home while it has any. */
    private static Action plant(Body a, Picker wanted, @Nullable Area area) {
        Predicate<ItemStack> which = s -> wanted.exactly(s) || wanted.loosely(s);
        return new Plots(a, area, Farming.BARE, "residual.agent.status.planting", wanted.thing()) {
            @Override
            boolean ready() {
                return carried(a, s -> Farming.seed(s) && which.test(s)) > 0;
            }

            @Override
            void work(ServerLevel level, BlockPos plot) {
                Farming.sow(level, a, plot.above(), which);
            }

            @Override
            Outcome over(boolean stopped) {
                return stopped && count == 0 ? Outcome.failed("residual.program.failed.no_seeds_of", wanted.thing())
                        : Outcome.done("residual.program.planted", count, wanted.thing());
            }
        };
    }

    /** Walks the farmland around home row by row, working the plots in one state. */
    private abstract static class Plots implements Action {
        final Body a;
        @Nullable
        private final Area area;
        private final char state;
        private final String status;
        private final Object[] args;
        private List<BlockPos> plots;
        private int next;
        @Nullable
        private BlockPos plot;
        /** Plots worked so far. */
        int count;

        Plots(Body a, @Nullable Area area, char state, String status, Object... args) {
            this.a = a;
            this.area = area;
            this.state = state;
            this.status = status;
            this.args = args;
        }

        /** Whether it can work another plot. */
        abstract boolean ready();

        /** Works a plot it stands by. */
        abstract void work(ServerLevel level, BlockPos plot);

        /** How it went, {@code stopped} short of the last plot. */
        abstract Outcome over(boolean stopped);

        @Override
        public Outcome tick(ServerLevel level) {
            if (plots == null) {
                plots = area == null ? Farming.plots(level, a.home()) : plotsIn(level, area);
            }
            if (!ready()) {
                return over(true);
            }
            if (plot == null) {
                while (next < plots.size() && Farming.state(level, plots.get(next)) != state) {
                    next++;
                }
                if (next >= plots.size()) {
                    return over(false);
                }
                plot = plots.get(next++);
            }
            a.status(status, args);
            switch (a.walk(plot.above(), 1.6)) {
                case ON_THE_WAY -> {
                    return null;
                }
                case LOST -> {
                    plot = null;
                    return null;
                }
                case HERE -> {
                }
            }
            BlockPos at = plot;
            plot = null;
            a.reached(at, 1);
            if (Farming.state(level, at) == state) {
                work(level, at);
                count += Farming.state(level, at) != state ? 1 : 0;
                a.pause(4);
            }
            return null;
        }
    }

    /** The farmland in an area, or under it, in the order it walks the rows. */
    private static List<BlockPos> plotsIn(ServerLevel level, Area area) {
        Set<BlockPos> plots = new HashSet<>();
        for (java.util.Iterator<BlockPos> it = area.places(); it.hasNext(); ) {
            BlockPos p = it.next();
            if (level.isLoaded(p)) {
                if (Farming.state(level, p) != 0) {
                    plots.add(p);
                } else if (Farming.state(level, p.below()) != 0) {
                    plots.add(p.below());
                }
            }
        }
        List<BlockPos> out = new ArrayList<>(plots);
        out.sort(Farming.ROWS);
        return out;
    }

    /** Turns the grass and dirt around home, or in an area, that water reaches into farmland, row by row. */
    private static Action till(Body a, @Nullable Area area) {
        return new Action() {
            private List<BlockPos> ground;
            private int next;
            private int tilled;
            @Nullable
            private BlockPos at;

            @Override
            public Outcome tick(ServerLevel level) {
                if (ground == null) {
                    if (area != null) {
                        List<BlockPos> inside = new ArrayList<>();
                        area.places().forEachRemaining(p -> {
                            if (tillable(level, p)) {
                                inside.add(p);
                            }
                        });
                        inside.sort(Farming.ROWS);
                        ground = inside;
                    } else {
                        ground = BlockPos.betweenClosedStream(a.home().offset(-Farming.RADIUS, -2, -Farming.RADIUS),
                                        a.home().offset(Farming.RADIUS, 2, Farming.RADIUS))
                                .filter(p -> tillable(level, p)).map(BlockPos::immutable).sorted(Farming.ROWS).toList();
                    }
                }
                if (at == null) {
                    while (next < ground.size() && !tillable(level, ground.get(next))) {
                        next++;
                    }
                    if (next >= ground.size()) {
                        return Outcome.done("residual.program.tilled", tilled);
                    }
                    at = ground.get(next++);
                }
                a.status("residual.agent.status.tilling");
                switch (a.walk(at.above(), 1.6)) {
                    case ON_THE_WAY -> {
                        return null;
                    }
                    case LOST -> {
                        at = null;
                        return null;
                    }
                    case HERE -> {
                    }
                }
                if (tillable(level, at)) {
                    a.reached(at, 1);
                    level.setBlockAndUpdate(at, Blocks.FARMLAND.defaultBlockState());
                    level.playSound(null, at, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1f, 1f);
                    a.swing();
                    a.pause(4);
                    tilled++;
                }
                at = null;
                return null;
            }
        };
    }

    /** Grass, dirt or a path with air above and water within four blocks, as farmland needs. */
    private static boolean tillable(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (!level.isLoaded(p) || !(s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.DIRT_PATH))
                || !level.getBlockState(p.above()).isAir()) {
            return false;
        }
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-4, 0, -4), p.offset(4, 1, 4))) {
            if (level.getFluidState(q).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- blocks

    /**
     * Mines {@code count} blocks of a thing, or one, nearest first; blocks that need a tool need it carried. Caches are
     * mined for what they hold. Agents mining near each other each take a block no other has picked.
     */
    private static Action mine(Body a, int count, Picker wanted) {
        boolean caches = wanted instanceof Things.Wanted w && w.names().contains("cache");
        // to mine iron is to mine iron ore: the ore blocks of a name count as well
        List<TagKey<Block>> ores = !(wanted instanceof Things.Wanted named) ? List.of() : named.names().stream()
                .map(n -> TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "ores/" + n.replace(' ', '_')))).toList();
        return new Action() {
            private final int want = Math.max(1, count);
            private final Set<BlockPos> skipped = new HashSet<>();
            private final Map<Block, Boolean> kinds = new HashMap<>();
            private int mined;
            private int progress;
            @Nullable
            private BlockPos target;
            /** Whether the target is buried, or one a Surveyor found out of sight: it digs its way there. */
            private boolean digging;
            /** The block in the way it is digging through, how far into it, and how long it has stood still. */
            @Nullable
            private BlockPos through;
            private int dug;
            private int still;
            @Nullable
            private BlockPos stood;

            @Override
            public Outcome tick(ServerLevel level) {
                if (target == null) {
                    if (mined >= want) {
                        return Outcome.done("residual.program.mined", mined, wanted.thing());
                    }
                    target = nearest(level);
                    if (target == null) {
                        target = SurveyorBlockEntity.nearestFound(level, a.place(), p -> matches(level, p) && free(level, p), skipped);
                    }
                    digging = target != null && a.tunnels() && buried(level, target);
                    if (target == null) {
                        return mined > 0 ? Outcome.done("residual.program.mined", mined, wanted.thing())
                                : Outcome.failed("residual.program.failed.none_near", wanted.thing(), NEARBY);
                    }
                    BlockState state = level.getBlockState(target);
                    if (state.requiresCorrectToolForDrops() && tool(state) < 0) {
                        return Outcome.failed("residual.program.failed.needs_tool", state.getBlock().getName().getString());
                    }
                    progress = 0;
                }
                BlockState state = level.getBlockState(target);
                if (!matches(level, target)) {
                    target = null;
                    return null;
                }
                claims(level).put(target, new Claim(a.id(), level.getGameTime() + CLAIMED));
                a.status("residual.agent.status.mining", wanted.thing());
                if (digging && a.center().distanceToSqr(Vec3.atBottomCenterOf(target)) > 3.0 * 3.0) {
                    dig(level);
                    return null;
                }
                switch (a.walk(target, 3.0)) {
                    case ON_THE_WAY -> {
                        return null;
                    }
                    case LOST -> {
                        skipped.add(target);
                        release(level);
                        return null;
                    }
                    case HERE -> {
                    }
                }
                a.look(Vec3.atCenterOf(target));
                int time = Math.max(a.interval(), (int) (state.getDestroySpeed(level, target) * 12) >> a.speed());
                if (++progress < time) {
                    level.destroyBlockProgress(a.id(), target, progress * 10 / time);
                    if (progress % 5 == 0) {
                        a.swing();
                    }
                    return null;
                }
                level.destroyBlockProgress(a.id(), target, -1);
                if (caches && level.getBlockEntity(target) instanceof RandomizableContainerBlockEntity cache) {
                    // what the old world left in it, rolled now, as opening it would
                    cache.unpackLootTable(null);
                    for (int i = 0; i < cache.getContainerSize(); i++) {
                        ItemStack rest = a.keep(cache.removeItemNoUpdate(i));
                        if (!rest.isEmpty()) {
                            Block.popResource(level, target, rest);
                        }
                    }
                }
                int t = tool(state);
                ItemStack used = t < 0 ? ItemStack.EMPTY : a.items().getItem(t);
                for (ItemStack drop : Block.getDrops(state, level, target, level.getBlockEntity(target), a.entity(), used)) {
                    ItemStack rest = a.keep(drop);
                    if (!rest.isEmpty()) {
                        Block.popResource(level, target, rest);
                    }
                }
                level.destroyBlock(target, false, a.entity());
                if (!used.isEmpty()) {
                    used.hurtAndBreak(1, level, a.entity() instanceof net.minecraft.world.entity.LivingEntity l ? l : null, item -> {
                    });
                    a.items().setChanged();
                }
                mined++;
                release(level);
                return null;
            }

            /** Lets go of the block it was after, for another agent to take. */
            private void release(ServerLevel level) {
                Claim c = claims(level).get(target);
                if (c != null && c.agent() == a.id()) {
                    claims(level).remove(target);
                }
                target = null;
            }

            /** Not picked by another agent still mining; a claim left by an agent that stopped is let go of here. */
            private boolean free(ServerLevel level, BlockPos p) {
                Claim c = claims(level).get(p);
                if (c != null && c.until() < level.getGameTime()) {
                    claims(level).remove(p);
                    return true;
                }
                return c == null || c.agent() == a.id();
            }

            /** Shut in on every side, so no walking gets near it. */
            private boolean buried(ServerLevel level, BlockPos p) {
                for (Direction d : Direction.values()) {
                    BlockPos n = p.relative(d);
                    if (level.getBlockState(n).getCollisionShape(level, n).isEmpty()) {
                        return false;
                    }
                }
                return true;
            }

            /**
             * A step of a tunnel to a buried target: down where it lies at least as far below as aside and the ground
             * under the next block holds, otherwise along the longer way, two blocks high for its body. A tunnel that would
             * let in water or lava, or meets a block it cannot break, is given up for another target.
             */
            private void dig(ServerLevel level) {
                BlockPos at = a.place();
                if (through == null) {
                    int dx = target.getX() - at.getX(), dy = target.getY() - at.getY(), dz = target.getZ() - at.getZ();
                    boolean xFirst = Math.abs(dx) >= Math.abs(dz);
                    BlockPos along = xFirst ? at.offset(Integer.signum(dx), 0, 0) : at.offset(0, 0, Integer.signum(dz));
                    BlockPos across = xFirst ? at.offset(0, 0, Integer.signum(dz)) : at.offset(Integer.signum(dx), 0, 0);
                    boolean down = dy < 0 && -dy >= Math.max(Math.abs(dx), Math.abs(dz)) && level.getBlockState(at.below(2)).isSolid();
                    // the way it would go, then round what it may not dig through: the other way toward the target
                    List<List<BlockPos>> ways = new ArrayList<>();
                    if (down) {
                        ways.add(List.of(at.below()));
                    }
                    ways.add(List.of(along, along.above()));
                    if (!across.equals(at)) {
                        ways.add(List.of(across, across.above()));
                    }
                    for (List<BlockPos> way : ways) {
                        BlockPos block = way.stream().filter(p -> !level.getBlockState(p).getCollisionShape(level, p).isEmpty()).findFirst()
                                .orElse(null);
                        if (block == null) {
                            // the way is open: step into it, and give up a target it cannot get nearer to
                            still = at.equals(stood) ? still + 1 : 0;
                            stood = at;
                            if (still > 60) {
                                giveUp();
                                return;
                            }
                            // one block over, so straight there, with no path to find
                            BlockPos into = way.getFirst();
                            a.stepInto(into);
                            return;
                        }
                        if (diggable(level, block)) {
                            through = block;
                            break;
                        }
                    }
                    if (through == null) {
                        giveUp();
                        return;
                    }
                    dug = 0;
                }
                BlockState s = level.getBlockState(through);
                if (s.getCollisionShape(level, through).isEmpty()) {
                    through = null;
                    return;
                }
                a.look(Vec3.atCenterOf(through));
                int time = Math.max(a.interval(), (int) (s.getDestroySpeed(level, through) * 12) >> a.speed());
                if (++dug < time) {
                    level.destroyBlockProgress(a.id(), through, dug * 10 / time);
                    if (dug % 5 == 0) {
                        a.swing();
                    }
                    return;
                }
                level.destroyBlockProgress(a.id(), through, -1);
                int t = tool(s);
                ItemStack used = t < 0 ? ItemStack.EMPTY : a.items().getItem(t);
                for (ItemStack drop : Block.getDrops(s, level, through, level.getBlockEntity(through), a.entity(), used)) {
                    ItemStack rest = a.keep(drop);
                    if (!rest.isEmpty()) {
                        Block.popResource(level, through, rest);
                    }
                }
                level.destroyBlock(through, false, a.entity());
                through = null;
            }

            /**
             * Ground it may tunnel through: rock and earth that a pickaxe or shovel mines, never a machine, a container or
             * anything else holding a block entity, and with no water or lava beside it to let in.
             */
            private boolean diggable(ServerLevel level, BlockPos p) {
                BlockState s = level.getBlockState(p);
                if (s.getDestroySpeed(level, p) < 0 || level.getBlockEntity(p) != null
                        || BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace().equals(Residual.MOD_ID)
                        || !s.is(BlockTags.MINEABLE_WITH_PICKAXE) && !s.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
                    return false;
                }
                for (Direction d : Direction.values()) {
                    if (!level.getFluidState(p.relative(d)).isEmpty()) {
                        return false;
                    }
                }
                return true;
            }

            private void giveUp() {
                skipped.add(target);
                release(a.world());
                through = null;
                still = 0;
            }

            /** A block of the thing: a cache when told to mine caches, otherwise a block whose item is the thing. */
            private boolean matches(ServerLevel level, BlockPos p) {
                if (caches) {
                    return SurveyorBlockEntity.cache(level, p);
                }
                BlockState s = level.getBlockState(p);
                return !s.isAir() && kinds.computeIfAbsent(s.getBlock(), b -> {
                    ItemStack as = new ItemStack(b.asItem());
                    return wanted.exactly(as) || wanted.loosely(as) || ores.stream().anyMatch(s::is);
                });
            }

            /** The nearest block of the thing around it, never the ground under its feet. */
            @Nullable
            private BlockPos nearest(ServerLevel level) {
                BlockPos at = a.place();
                BlockPos best = null;
                for (BlockPos p : BlockPos.betweenClosed(at.offset(-NEARBY, -NEARBY, -NEARBY), at.offset(NEARBY, NEARBY, NEARBY))) {
                    if (p.getX() == at.getX() && p.getZ() == at.getZ() && p.getY() < at.getY() || skipped.contains(p)
                            || best != null && p.distSqr(at) >= best.distSqr(at)) {
                        continue;
                    }
                    if (matches(level, p) && free(level, p) && level.getBlockState(p).getDestroySpeed(level, p) >= 0) {
                        best = p.immutable();
                    }
                }
                return best;
            }

            /** The carried slot of a tool that gets {@code state}'s drops, or -1. */
            private int tool(BlockState state) {
                return slot(a, s -> s.isCorrectToolForDrops(state));
            }
        };
    }

    /** Places a block of a thing on top of a marked block. */
    private static Action place(Body a, Picker wanted, int mark) {
        Predicate<ItemStack> what = s -> s.getItem() instanceof BlockItem && (wanted.exactly(s) || wanted.loosely(s));
        return level -> {
            if (mark > a.marks().size()) {
                return Outcome.failed("residual.program.failed.no_mark", mark);
            }
            BlockPos at = a.marks().get(mark - 1).above();
            int slot = slot(a, what);
            if (slot < 0) {
                return Outcome.failed("residual.program.failed.not_carrying", wanted.thing());
            }
            if (!level.getBlockState(at).canBeReplaced()) {
                return Outcome.failed("residual.program.failed.in_the_way", mark);
            }
            a.status("residual.agent.status.placing", wanted.thing(), mark);
            switch (a.walk(at, 3.0)) {
                case ON_THE_WAY -> {
                    return null;
                }
                case LOST -> {
                    return Outcome.failed("residual.program.failed.unreachable");
                }
                case HERE -> {
                }
            }
            ItemStack s = a.items().getItem(slot);
            BlockState state = ((BlockItem) s.getItem()).getBlock().defaultBlockState();
            if (!state.canSurvive(level, at)) {
                return Outcome.failed("residual.program.failed.cant_stand", mark);
            }
            level.setBlockAndUpdate(at, state);
            level.playSound(null, at, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1f, 1f);
            a.swing();
            s.shrink(1);
            a.items().setChanged();
            return Outcome.DONE;
        };
    }

    // ---------------------------------------------------------------- counting

    /** The first carried slot {@code what} allows, or -1. */
    static int slot(Body a, Predicate<ItemStack> what) {
        for (int i = 0; i < a.carry(); i++) {
            ItemStack s = a.items().getItem(i);
            if (!s.isEmpty() && what.test(s)) {
                return i;
            }
        }
        return -1;
    }

    /** How many it carries of what {@code what} allows. */
    static int carried(Body a, Predicate<ItemStack> what) {
        int n = 0;
        for (int i = 0; i < a.carry(); i++) {
            ItemStack s = a.items().getItem(i);
            if (!s.isEmpty() && what.test(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** How many of what {@code what} allows the stores around home keep. */
    static int stocked(ServerLevel level, Body a, Predicate<ItemStack> what) {
        int n = 0;
        for (BlockPos p : Stores.near(level, a.home(), Stores.AROUND, Stores.ANY)) {
            Container c = Stores.at(level, p);
            if (c != null) {
                n += Stores.count(c, what);
            }
        }
        for (BlockPos p : Stores.terminals(level, a.home())) {
            n = (int) Math.min(Integer.MAX_VALUE, n + Stores.count(StorageNetwork.at(level, p), what));
        }
        return n;
    }
}
