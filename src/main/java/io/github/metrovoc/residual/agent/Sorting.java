package io.github.metrovoc.residual.agent;

import io.github.metrovoc.residual.learn.Kinds;
import io.github.metrovoc.residual.model.ModelLink;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jetbrains.annotations.Nullable;

/**
 * Empties the barrels around home into the chests labelled with each thing's kind: Food, Plants, Wood, Stone, Ores or
 * Tools, as a chest's name or on a sign fixed to it. Its filter model is asked what kind each thing is by its name,
 * the way the sorting test asks. Whatever it is not sure of ({@link Kinds#SURE}), or calls other, goes in a chest
 * labelled Other, or else in one with no label at all.
 */
final class Sorting extends Work {
    /** The chest chosen for what it carries, or null while it has not chosen. */
    @Nullable
    private BlockPos chosen;

    Sorting(Agent a) {
        super(a);
    }

    @Override
    void tick(ServerLevel level) {
        Map<Kinds.Kind, List<BlockPos>> labelled = new EnumMap<>(Kinds.Kind.class);
        List<BlockPos> unlabelled = new ArrayList<>();
        for (BlockPos p : Stores.near(level, a.home(), Storing.RADIUS, Stores.CHESTS)) {
            String label = label(level, p);
            Kinds.Kind kind = Kinds.Kind.labelled(label);
            if (kind != null) {
                labelled.computeIfAbsent(kind, k -> new ArrayList<>()).add(p);
            } else if (label.isEmpty()) {
                unlabelled.add(p);
            }
        }
        if (labelled.keySet().stream().allMatch(k -> k == Kinds.Kind.OTHER)) {
            fail("residual.agent.status.no_labels");
            a.pause(100);
            return;
        }
        int slot = Storing.carried(a);
        if (slot < 0) {
            if (!Storing.collect(a, level)) {
                finish("residual.program.barrels_empty");
            }
            return;
        }
        ItemStack stack = a.items.getItem(slot);
        if (chosen == null) {
            ModelLink filter = a.filter();
            if (filter instanceof ModelLink.Missing m) {
                fail(m.problem().equals("residual.model.none") ? "residual.agent.status.no_filter" : m.problem(), m.args().toArray());
                a.pause(100);
                return;
            }
            String name = Things.name(stack);
            Kinds.Kind known = Kinds.known(name);
            a.ask(Agent.FILTER, Kinds.prompt(name), 1, '\n', known == null ? null : String.valueOf(known.letter), answer -> {
                Kinds.Kind kind = answer.isEmpty() ? null : Kinds.Kind.of(answer.charAt(0));
                boolean sure = kind != null && a.confidence() >= Kinds.SURE;
                List<BlockPos> to = new ArrayList<>(sure ? labelled.getOrDefault(kind, List.of()) : List.of());
                to.addAll(labelled.getOrDefault(Kinds.Kind.OTHER, List.of()));
                to.addAll(unlabelled);
                a.show(a.decided(Component.literal(kind == null ? "?" : sure ? kind.id : kind.id + "?")));
                for (BlockPos p : to) {
                    Container c = Stores.at(level, p);
                    if (c != null && Stores.room(c, stack)) {
                        chosen = p;
                        return;
                    }
                }
                fail("residual.agent.status.nowhere", name);
                a.pause(60);
            });
            return;
        }
        a.status("residual.agent.status.sorting", labelled.values().stream().mapToInt(List::size).sum());
        switch (a.walk(chosen, 2.2)) {
            case ON_THE_WAY -> {
                return;
            }
            case LOST -> {
                chosen = null;
                a.pause(60);
                return;
            }
            case HERE -> {
            }
        }
        Container c = Stores.at(level, chosen);
        if (c != null) {
            int count = stack.getCount();
            String name = Things.name(stack);
            ItemStack rest = Stores.insert(c, stack);
            a.items.setItem(slot, rest);
            a.handle(chosen);
            if (rest.getCount() < count) {
                String label = label(level, chosen);
                a.trace(Trace.Kind.DONE, Program.Verb.SORT.word, label.isEmpty() ? "residual.program.sorted_unlabelled"
                        : "residual.program.sorted", List.of(String.valueOf(count - rest.getCount()), name, label));
            }
        }
        chosen = null;
    }

    /** What a chest says it is for: its name, or the words on a sign fixed to it; empty if neither. */
    static String label(ServerLevel level, BlockPos chest) {
        List<BlockPos> halves = new ArrayList<>(List.of(chest));
        BlockState state = level.getBlockState(chest);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            halves.add(chest.relative(ChestBlock.getConnectedDirection(state)));
        }
        for (BlockPos half : halves) {
            if (level.getBlockEntity(half) instanceof BaseContainerBlockEntity named && named.getCustomName() != null) {
                return named.getCustomName().getString();
            }
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos p = half.relative(d);
                BlockState sign = level.getBlockState(p);
                if (sign.getBlock() instanceof WallSignBlock && sign.getValue(WallSignBlock.FACING) == d
                        && level.getBlockEntity(p) instanceof SignBlockEntity text) {
                    StringBuilder words = new StringBuilder();
                    for (Component line : text.getFrontText().getMessages(false)) {
                        words.append(' ').append(line.getString());
                    }
                    if (!words.toString().isBlank()) {
                        return words.toString().strip();
                    }
                }
            }
        }
        return "";
    }
}
