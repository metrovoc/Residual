package io.github.metrovoc.residual.agent;

import io.github.metrovoc.residual.learn.Dataset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * What the verbs of a program do. Each act is a small machine, ticked until it is over; none of them asks a model.
 * Things are named the way orders name them ({@link Things}), and stores are the chests and barrels around home.
 */
final class Acts {
    /** How long {@code follow} keeps close, in ticks. */
    static final int FOLLOWING = 2400;
    /** How far around itself it looks for things lying about and for blocks to mine. */
    static final int NEARBY = 8;

    /** An act under way. */
    interface Action {
        /** Does a little more of it: null while it goes on, then how it went. */
        @Nullable
        Outcome tick(ServerLevel level);
    }

    private Acts() {
    }

    /** The act for {@code act}; {@link Running} runs fetch and the routines itself. */
    static Action of(Agent a, Program.Act act) {
        return switch (act.verb()) {
            case COME -> come(a);
            case HOME -> walk(a, a.home(), 1.5, "residual.agent.status.going_home");
            case GO -> act.mark() > a.marks().size() ? level -> Outcome.failed("residual.program.failed.no_mark", act.mark())
                    : walk(a, a.marks().get(act.mark() - 1).above(), 1.8, "residual.agent.status.going_to_mark", act.mark());
            case STAY -> stay(a);
            case FOLLOW -> follow(a);
            case TAKE -> take(a, act.count(), act.thing());
            case PUT -> put(a, act.thing());
            case GIVE -> give(a, act.count(), act.thing());
            case PICKUP -> pickup(a);
            case HARVEST -> harvest(a);
            case PLANT -> plant(a, act.thing());
            case TILL -> till(a);
            case MINE -> mine(a, act.count(), act.thing());
            case PLACE -> place(a, act.thing(), act.mark());
            case WAIT -> waiting(a, act.count());
            case SAY -> level -> {
                a.tell(Component.literal(act.thing()));
                return Outcome.DONE;
            };
            case FETCH, FARM, STORE, SORT, HAUL, BUILD -> throw new IllegalArgumentException(act.verb().word);
        };
    }

    // ---------------------------------------------------------------- moving

    private static Action walk(Agent a, BlockPos to, double reach, String status, Object... args) {
        return level -> {
            a.status(status, args);
            return switch (a.walk(to, reach)) {
                case HERE -> Outcome.DONE;
                case LOST -> Outcome.failed("residual.program.failed.unreachable");
                case ON_THE_WAY -> null;
            };
        };
    }

    private static Action come(Agent a) {
        return level -> {
            ServerPlayer p = a.boss();
            if (p == null) {
                return Outcome.failed("residual.program.failed.no_boss");
            }
            a.status("residual.agent.status.coming", p.getName().getString());
            return switch (a.walk(p.blockPosition(), 2.5)) {
                case HERE -> {
                    a.getLookControl().setLookAt(p);
                    yield Outcome.DONE;
                }
                case LOST -> Outcome.failed("residual.program.failed.unreachable");
                case ON_THE_WAY -> null;
            };
        };
    }

    /** Stands where it is for good. */
    private static Action stay(Agent a) {
        return level -> {
            a.status("residual.agent.status.staying");
            a.getNavigation().stop();
            a.pause(20);
            return null;
        };
    }

    private static Action follow(Agent a) {
        int until = a.tickCount + FOLLOWING;
        return level -> {
            ServerPlayer p = a.boss();
            if (p == null) {
                return Outcome.failed("residual.program.failed.no_boss");
            }
            if (a.tickCount >= until) {
                return Outcome.DONE;
            }
            a.status("residual.agent.status.following", p.getName().getString());
            if (a.walk(p.blockPosition(), 2.5) == Agent.Arrival.HERE) {
                a.getLookControl().setLookAt(p);
            }
            return null;
        };
    }

    private static Action waiting(Agent a, int seconds) {
        return new Action() {
            private boolean waited;

            @Override
            public Outcome tick(ServerLevel level) {
                if (waited) {
                    return Outcome.DONE;
                }
                waited = true;
                a.status("residual.agent.status.waiting", seconds);
                a.pause(seconds * 20);
                return null;
            }
        };
    }

    // ---------------------------------------------------------------- carrying

    /** Takes {@code count} of a thing, or a stack, out of the stores around home. */
    private static Action take(Agent a, int count, String thing) {
        Things.Wanted wanted = Things.of(thing);
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
                if (c != null) {
                    int before = got;
                    takeAll(c, wanted::exactly);
                    if (got == before) {
                        takeAll(c, wanted::loosely);
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

            private void takeAll(Container c, Predicate<ItemStack> what) {
                for (int moved = -1; moved != 0 && got < want; got += moved) {
                    moved = Stores.fetch(c, a, what, want - got);
                }
            }

            private Outcome took() {
                return Outcome.done("residual.program.took", got, wanted.thing());
            }
        };
    }

    /** Puts what it carries of a thing, or everything it carries, into the stores around home. */
    private static Action put(Agent a, String thing) {
        Things.Wanted wanted = thing.isEmpty() ? null : Things.of(thing);
        Predicate<ItemStack> what = s -> !s.isEmpty() && (wanted == null || wanted.exactly(s) || wanted.loosely(s));
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
                ItemStack kind = a.items.getItem(slot).copyWithCount(1);
                if (to == null) {
                    to = Stores.into(level, a.home(), kind, tried);
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
                int before = put;
                for (int i = 0; i < Agent.CARRY && c != null; i++) {
                    ItemStack s = a.items.getItem(i);
                    if (ItemStack.isSameItemSameComponents(s, kind)) {
                        int n = s.getCount();
                        ItemStack rest = Stores.insert(c, s);
                        put += n - rest.getCount();
                        a.items.setItem(i, rest);
                    }
                }
                a.items.setChanged();
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

    /** Hands {@code count} of a thing it carries, or all it carries of it, to whoever it works for. */
    private static Action give(Agent a, int count, String thing) {
        Things.Wanted wanted = Things.of(thing);
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
            for (int i = 0; i < Agent.CARRY && left > 0; i++) {
                ItemStack s = a.items.getItem(i);
                if (what.test(s)) {
                    ItemStack out = s.split(Math.min(left, s.getCount()));
                    left -= out.getCount();
                    given += out.getCount();
                    if (!p.getInventory().add(out)) {
                        p.drop(out, false);
                    }
                }
            }
            a.items.setChanged();
            a.handle(p.blockPosition());
            a.getLookControl().setLookAt(p);
            return Outcome.done("residual.program.gave", given, wanted.thing(), p.getName().getString());
        };
    }

    /** Picks up what lies on the ground around it. */
    private static Action pickup(Agent a) {
        return new Action() {
            private final Set<Integer> skipped = new HashSet<>();
            private int picked;

            @Override
            public Outcome tick(ServerLevel level) {
                ItemEntity nearest = null;
                for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, a.getBoundingBox().inflate(NEARBY),
                        e -> e.isAlive() && !e.hasPickUpDelay() && !skipped.contains(e.getId()))) {
                    if (nearest == null || e.distanceToSqr(a) < nearest.distanceToSqr(a)) {
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
                a.take(nearest, took);
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
    private static Action harvest(Agent a) {
        return new Plots(a, Dataset.RIPE, "residual.agent.status.harvesting") {
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
    private static Action plant(Agent a, String thing) {
        Things.Wanted wanted = Things.of(thing);
        Predicate<ItemStack> which = s -> wanted.exactly(s) || wanted.loosely(s);
        return new Plots(a, Dataset.BARE, "residual.agent.status.planting", wanted.thing()) {
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
        final Agent a;
        private final char state;
        private final String status;
        private final Object[] args;
        private List<BlockPos> plots;
        private int next;
        @Nullable
        private BlockPos plot;
        /** Plots worked so far. */
        int count;

        Plots(Agent a, char state, String status, Object... args) {
            this.a = a;
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
                plots = Farming.plots(level, a.home());
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
            if (Farming.state(level, at) == state) {
                work(level, at);
                count += Farming.state(level, at) != state ? 1 : 0;
                a.pause(4);
            }
            return null;
        }
    }

    /** Turns the grass and dirt around home that water reaches into farmland, row by row. */
    private static Action till(Agent a) {
        return new Action() {
            private List<BlockPos> ground;
            private int next;
            private int tilled;
            @Nullable
            private BlockPos at;

            @Override
            public Outcome tick(ServerLevel level) {
                if (ground == null) {
                    ground = BlockPos.betweenClosedStream(a.home().offset(-Farming.RADIUS, -2, -Farming.RADIUS),
                                    a.home().offset(Farming.RADIUS, 2, Farming.RADIUS))
                            .filter(p -> tillable(level, p)).map(BlockPos::immutable).sorted(Farming.ROWS).toList();
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
                    level.setBlockAndUpdate(at, Blocks.FARMLAND.defaultBlockState());
                    level.playSound(null, at, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1f, 1f);
                    a.swing(InteractionHand.MAIN_HAND);
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

    /** Mines {@code count} blocks of a thing, or one, nearest first; blocks that need a tool need it carried. */
    private static Action mine(Agent a, int count, String thing) {
        Things.Wanted wanted = Things.of(thing);
        return new Action() {
            private final int want = Math.max(1, count);
            private final Set<BlockPos> skipped = new HashSet<>();
            private final Map<Block, Boolean> kinds = new HashMap<>();
            private int mined;
            private int progress;
            @Nullable
            private BlockPos target;

            @Override
            public Outcome tick(ServerLevel level) {
                if (target == null) {
                    if (mined >= want) {
                        return Outcome.done("residual.program.mined", mined, wanted.thing());
                    }
                    target = nearest(level);
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
                if (!matches(state)) {
                    target = null;
                    return null;
                }
                a.status("residual.agent.status.mining", wanted.thing());
                switch (a.walk(target, 3.0)) {
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
                a.getLookControl().setLookAt(Vec3.atCenterOf(target));
                int time = Math.max(8, (int) (state.getDestroySpeed(level, target) * 12));
                if (++progress < time) {
                    level.destroyBlockProgress(a.getId(), target, progress * 10 / time);
                    if (progress % 5 == 0) {
                        a.swing(InteractionHand.MAIN_HAND);
                    }
                    return null;
                }
                level.destroyBlockProgress(a.getId(), target, -1);
                int t = tool(state);
                ItemStack used = t < 0 ? ItemStack.EMPTY : a.items.getItem(t);
                for (ItemStack drop : Block.getDrops(state, level, target, level.getBlockEntity(target), a, used)) {
                    ItemStack rest = a.keep(drop);
                    if (!rest.isEmpty()) {
                        Block.popResource(level, target, rest);
                    }
                }
                level.destroyBlock(target, false, a);
                if (!used.isEmpty()) {
                    used.hurtAndBreak(1, level, a, item -> {
                    });
                    a.items.setChanged();
                }
                mined++;
                target = null;
                return null;
            }

            private boolean matches(BlockState s) {
                return !s.isAir() && kinds.computeIfAbsent(s.getBlock(), b -> {
                    ItemStack as = new ItemStack(b.asItem());
                    return wanted.exactly(as) || wanted.loosely(as);
                });
            }

            /** The nearest block of the thing around it, never the ground under its feet. */
            @Nullable
            private BlockPos nearest(ServerLevel level) {
                BlockPos at = a.blockPosition();
                BlockPos best = null;
                for (BlockPos p : BlockPos.betweenClosed(at.offset(-NEARBY, -NEARBY, -NEARBY), at.offset(NEARBY, NEARBY, NEARBY))) {
                    if (p.getX() == at.getX() && p.getZ() == at.getZ() && p.getY() < at.getY() || skipped.contains(p)
                            || best != null && p.distSqr(at) >= best.distSqr(at)) {
                        continue;
                    }
                    BlockState s = level.getBlockState(p);
                    if (matches(s) && s.getDestroySpeed(level, p) >= 0) {
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
    private static Action place(Agent a, String thing, int mark) {
        Things.Wanted wanted = Things.of(thing);
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
            ItemStack s = a.items.getItem(slot);
            BlockState state = ((BlockItem) s.getItem()).getBlock().defaultBlockState();
            if (!state.canSurvive(level, at)) {
                return Outcome.failed("residual.program.failed.cant_stand", mark);
            }
            level.setBlockAndUpdate(at, state);
            level.playSound(null, at, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1f, 1f);
            a.swing(InteractionHand.MAIN_HAND);
            s.shrink(1);
            a.items.setChanged();
            return Outcome.DONE;
        };
    }

    // ---------------------------------------------------------------- counting

    /** The first carried slot {@code what} allows, or -1. */
    static int slot(Agent a, Predicate<ItemStack> what) {
        for (int i = 0; i < Agent.CARRY; i++) {
            ItemStack s = a.items.getItem(i);
            if (!s.isEmpty() && what.test(s)) {
                return i;
            }
        }
        return -1;
    }

    /** How many it carries of what {@code what} allows. */
    static int carried(Agent a, Predicate<ItemStack> what) {
        int n = 0;
        for (int i = 0; i < Agent.CARRY; i++) {
            ItemStack s = a.items.getItem(i);
            if (!s.isEmpty() && what.test(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** How many of what {@code what} allows the stores around home keep. */
    static int stocked(ServerLevel level, Agent a, Predicate<ItemStack> what) {
        int n = 0;
        for (BlockPos p : Stores.near(level, a.home(), Stores.AROUND, Stores.ANY)) {
            Container c = Stores.at(level, p);
            if (c != null) {
                n += Stores.count(c, what);
            }
        }
        return n;
    }
}
