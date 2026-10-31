package io.github.metrovoc.residual.agent;

import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.learn.Dataset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * Copies or moves a build: the box between the first two marks, to where its first corner stands on top of the third.
 * First it reads the build: its model is shown the build's slices, a different letter each, then the copy's order so
 * far, and names the slice that comes next, the copy half of the Induction test. The first slice it takes by itself,
 * since nothing yet says where a copy starts. Unsure, or naming a slice twice, it stops before it touches anything.
 * Then each slice goes to its own place in the copy, in that order. A copy is laid from the chests around home or a
 * storage terminal there, its chests and machines empty; a move takes the whole build up, contents and all, and lays it
 * down again.
 */
final class Copying extends Laying {
    private final boolean move;
    private final Random rng = new Random();
    /** The order its model read the build in. */
    private final List<Integer> order = new ArrayList<>();
    private String alphabet = "";
    /** The marks that order was read for: new marks read the build again. */
    private List<BlockPos> readFor = List.of();

    Copying(Agent a, boolean move) {
        super(a);
        this.move = move;
    }

    @Override
    void tick(ServerLevel level) {
        List<BlockPos> m = a.marks();
        if (m.size() < 3) {
            fail("residual.agent.status.no_box");
            a.pause(100);
            return;
        }
        Slices build = Slices.between(m.get(0), m.get(1));
        Slices copy = build.movedTo(m.get(2).above().offset(build.origin().subtract(m.get(0))));
        if (build.length() > Slices.MOST) {
            fail("residual.agent.status.too_long", Slices.MOST);
            a.pause(100);
            return;
        }
        if (!build.fits()) {
            fail("residual.agent.status.too_big", Slices.MOST, Slices.MOST);
            a.pause(100);
            return;
        }
        if (m.get(0).distSqr(m.get(2)) > Slices.RANGE * Slices.RANGE) {
            fail("residual.agent.status.too_far", Slices.RANGE);
            a.pause(100);
            return;
        }
        if (copy.overlaps(build)) {
            fail("residual.agent.status.overlap");
            a.pause(100);
            return;
        }
        if (!m.equals(readFor)) {
            readFor = m;
            order.clear();
        }
        if (move) {
            boolean left = false;
            for (int k = 0; k < build.length() && !left; k++) {
                left = !none(level, build, k);
            }
            if (!left) {
                if (a.held().isEmpty()) {
                    finish("residual.agent.status.nothing_to_move");
                } else {
                    // what it holds goes down again however it was read, so nothing is left in its hands
                    layHeld(level, build, copy);
                }
                return;
            }
            // once it has taken something up it carries on: it read the build before it started
            if (a.held().isEmpty() && order.size() < build.length()) {
                read(build.length());
                return;
            }
            takeUp(level, build, copy);
            return;
        }
        if (order.size() < build.length()) {
            read(build.length());
            return;
        }
        layCopy(level, build, copy);
    }

    /** Reads the build's next slice: its model names it, after the first. */
    private void read(int length) {
        if (order.isEmpty()) {
            int[] pool = Dataset.distinct(rng, Dataset.LETTERS.length(), length);
            StringBuilder letters = new StringBuilder();
            for (int i = 0; i < length; i++) {
                letters.append(Dataset.LETTERS.charAt(pool[i]));
            }
            alphabet = letters.toString();
            order.add(0);
            return;
        }
        a.status("residual.agent.status.reading", order.size() + 1, length);
        StringBuilder seen = new StringBuilder(alphabet);
        for (int j : order) {
            seen.append(alphabet.charAt(j));
        }
        ask(seen.toString(), alphabet, (letter, rule) -> {
            if (!sure(letter)) {
                order.clear();
                return;
            }
            if (order.contains(letter)) {
                fail("residual.agent.status.named_twice", letter + 1);
                order.clear();
                a.pause(100);
                return;
            }
            order.add(letter);
            decided(letter);
        });
    }

    /**
     * Lays the copy a slice at a time in the order read, each at its own place, fetching for the slices still to come on
     * the same trips.
     */
    private void layCopy(ServerLevel level, Slices build, Slices copy) {
        List<Map<BlockPos, BlockState>> slices = new ArrayList<>();
        for (int k : order) {
            Map<BlockPos, BlockState> wanted = new LinkedHashMap<>();
            List<BlockState> content = build.read(level, k);
            List<BlockPos> places = copy.slice(k);
            for (int i = 0; i < places.size(); i++) {
                wanted.put(places.get(i), content.get(i));
            }
            slices.add(wanted);
        }
        for (int n = 0; n < slices.size(); n++) {
            if (needs(level, slices.get(n)).isEmpty()) {
                continue;
            }
            Map<Item, Integer> ahead = new LinkedHashMap<>();
            for (Map<BlockPos, BlockState> later : slices.subList(n + 1, slices.size())) {
                needs(level, later).forEach((item, c) -> ahead.merge(item, c, Integer::sum));
            }
            a.status("residual.agent.status.copying", n + 1, order.size());
            int k = order.get(n);
            if (!lay(level, slices.get(n), copy.at(k, copy.width() / 2, 0), ahead)) {
                return;
            }
        }
        settle(level);
        award("agent/copy");
        finish("residual.agent.status.copy_done");
    }

    /**
     * Takes the build up a slice at a time, contents and all, once nothing stands where the copy goes; nothing it holds
     * drops or tells its neighbours, as a moved block never does.
     */
    private void takeUp(ServerLevel level, Slices build, Slices copy) {
        int k = 0;
        while (none(level, build, k)) {
            k++;
        }
        if (a.held().isEmpty()) {
            for (int s = 0; s < build.length(); s++) {
                List<BlockPos> from = build.slice(s), to = copy.slice(s);
                for (int i = 0; i < from.size(); i++) {
                    if (movable(level, from.get(i)) && !level.getBlockState(to.get(i)).canBeReplaced()) {
                        fail("residual.agent.status.in_the_way", to.get(i).toShortString());
                        a.pause(100);
                        return;
                    }
                }
            }
        }
        a.status("residual.agent.status.taking_up", k + 1, build.length());
        switch (a.walk(build.at(k, build.width() / 2, 0), REACH)) {
            case ON_THE_WAY -> {
                return;
            }
            case LOST -> {
                a.pause(60);
                return;
            }
            case HERE -> {
            }
        }
        List<CompoundTag> held = new ArrayList<>(a.held());
        List<BlockPos> taken = new ArrayList<>();
        for (int j = 0; j < build.height(); j++) {
            for (int i = 0; i < build.width(); i++) {
                BlockPos p = build.at(k, i, j);
                if (!movable(level, p)) {
                    continue;
                }
                CompoundTag t = new CompoundTag();
                t.putInt("k", k);
                t.putInt("i", i);
                t.putInt("j", j);
                t.put("state", NbtUtils.writeBlockState(level.getBlockState(p)));
                BlockEntity be = level.getBlockEntity(p);
                if (be != null) {
                    t.put("be", be.saveWithoutMetadata(level.registryAccess()));
                }
                held.add(t);
                taken.add(p);
            }
        }
        for (BlockPos p : taken) {
            // a barrier first, so no chest spills and nothing drops, as /clone moves blocks
            level.setBlock(p, Blocks.BARRIER.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
        }
        a.hold(held);
        level.playSound(null, build.at(k, build.width() / 2, 0), SoundEvents.ENDER_EYE_DEATH, SoundSource.NEUTRAL, 0.6f, 1.4f);
        a.swing(InteractionHand.MAIN_HAND);
        a.pause(4);
    }

    /** Lays what it holds at the copy, a slice at a time, in the order read; then tells the neighbours at both ends. */
    private void layHeld(ServerLevel level, Slices build, Slices copy) {
        List<CompoundTag> held = a.held();
        int k = -1;
        for (int s : order.size() == build.length() ? order : List.<Integer>of()) {
            if (held.stream().anyMatch(t -> t.getIntOr("k", -1) == s)) {
                k = s;
                break;
            }
        }
        if (k < 0) {
            // read before a restart, or in an order that is gone: the slices left go down in theirs
            k = held.stream().mapToInt(t -> t.getIntOr("k", 0)).min().orElse(0);
        }
        a.status("residual.agent.status.moving", build.length() - (int) held.stream().mapToInt(t -> t.getIntOr("k", 0)).distinct().count() + 1,
                build.length());
        switch (a.walk(copy.at(k, copy.width() / 2, 0), REACH)) {
            case ON_THE_WAY -> {
                return;
            }
            case LOST -> {
                a.pause(60);
                return;
            }
            case HERE -> {
            }
        }
        List<CompoundTag> rest = new ArrayList<>();
        for (CompoundTag t : held) {
            if (t.getIntOr("k", -1) != k) {
                rest.add(t);
                continue;
            }
            BlockPos p = copy.at(k, t.getIntOr("i", 0), t.getIntOr("j", 0));
            BlockState s = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), t.getCompoundOrEmpty("state"));
            level.setBlock(p, s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            t.getCompound("be").ifPresent(tag -> {
                BlockEntity be = level.getBlockEntity(p);
                if (be != null) {
                    be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
                    be.setChanged();
                }
            });
            placed.add(p);
        }
        a.hold(rest);
        level.playSound(null, copy.at(k, copy.width() / 2, 0), SoundEvents.ENDER_EYE_DEATH, SoundSource.NEUTRAL, 0.6f, 0.9f);
        a.swing(InteractionHand.MAIN_HAND);
        a.pause(4);
        if (rest.isEmpty()) {
            // the place it left: what leaned on the build from outside falls now
            for (int s = 0; s < build.length(); s++) {
                for (BlockPos p : build.slice(s)) {
                    tell(level, p, Blocks.STONE.defaultBlockState());
                }
            }
            settle(level);
            award("agent/move");
            finish("residual.agent.status.move_done");
        }
    }

    /** Whether slice {@code k} holds nothing it would move. */
    private static boolean none(ServerLevel level, Slices build, int k) {
        for (BlockPos p : build.slice(k)) {
            if (movable(level, p)) {
                return false;
            }
        }
        return true;
    }

    /** What a move takes along: any block but what can be built over, and what nothing breaks (bedrock). */
    private static boolean movable(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return !s.canBeReplaced() && s.getDestroySpeed(level, p) >= 0;
    }

    private void award(String path) {
        ServerPlayer boss = a.boss();
        if (boss != null) {
            Awards.grant(boss, path);
        }
    }
}
