package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.client.ClientHooks;
import io.github.metrovoc.residual.learn.Archive;
import io.github.metrovoc.residual.model.Recovered;
import io.github.metrovoc.residual.registry.ModComponents;
import io.github.metrovoc.residual.world.RuinKinds;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * A piece of the archive dug out of a ruin: one record, with some of its text lost. Read it, file it into an Archive
 * Drive, or have a Reader restore what is missing. A shard learns which record it is the first time it is held.
 */
public class ShardItem extends Item {
    public ShardItem(Properties properties) {
        super(properties);
    }

    @Nullable
    public static Recovered record(ItemStack stack) {
        return stack.get(ModComponents.SHARD.get());
    }

    /** How often a shard a ruin left words on carries a record that mentions them. */
    private static final float ABOUT_SHARE = 0.75f;

    /** A random record that turns up in shards, with a random share of it lost in clumps. */
    public static Recovered roll(RandomSource rng) {
        int count = Archive.records().size();
        int id;
        do {
            id = rng.nextInt(count);
        } while (Archive.heldOut(id));
        return lose(rng, id);
    }

    /** As {@link #roll(RandomSource)}, but most often a record that mentions one of {@code words}. */
    public static Recovered roll(RandomSource rng, List<String> words) {
        List<Integer> about = Archive.about(words);
        return about.isEmpty() || rng.nextFloat() >= ABOUT_SHARE ? roll(rng) : lose(rng, about.get(rng.nextInt(about.size())));
    }

    /** Record {@code id} with a random share of it lost in clumps. */
    private static Recovered lose(RandomSource rng, int id) {
        int blocks = Archive.get(id).blocks();
        BitSet lost = new BitSet(blocks);
        if (rng.nextFloat() >= 0.2f) {
            int target = Math.round(blocks * (0.1f + 0.45f * rng.nextFloat()));
            while (lost.cardinality() < target) {
                int start = rng.nextInt(blocks);
                lost.set(start, Math.min(blocks, start + 1 + rng.nextInt(3)));
            }
        }
        return new Recovered(id, lost);
    }

    /** Gives a shard its record: one that mentions the words its ruin left on it, if it left any. */
    private static void learn(ItemStack stack, RandomSource rng) {
        List<String> about = stack.remove(ModComponents.SHARD_ABOUT.get());
        stack.set(ModComponents.SHARD.get(), about == null ? roll(rng) : roll(rng, about));
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if (record(stack) == null) {
            learn(stack, level.getRandom());
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Recovered r = record(stack);
        if (r == null) {
            if (level instanceof ServerLevel server) {
                learn(stack, server.getRandom());
            }
            return InteractionResult.SUCCESS;
        }
        if (level.isClientSide()) {
            ClientHooks.readShard(r);
        } else if (r.text() != null) {
            RuinKinds.learn(player, r.text().text());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        Recovered r = record(stack);
        if (r == null || r.text() == null) {
            out.accept(Component.translatable("residual.shard.unread").withStyle(ChatFormatting.GRAY));
            return;
        }
        out.accept(Component.literal(r.text().title()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        out.accept(Component.translatable("residual.shard.legible", String.format(Locale.ROOT, "%.0f", 100 * r.legible()))
                .withStyle(r.whole() ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
    }
}
