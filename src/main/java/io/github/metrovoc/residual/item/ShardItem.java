package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.client.ClientHooks;
import io.github.metrovoc.residual.learn.Archive;
import io.github.metrovoc.residual.model.Recovered;
import io.github.metrovoc.residual.registry.ModComponents;
import java.util.BitSet;
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

    /** A random record that turns up in shards, with a random share of it lost in clumps. */
    public static Recovered roll(RandomSource rng) {
        int count = Archive.records().size();
        int id;
        do {
            id = rng.nextInt(count);
        } while (Archive.heldOut(id));
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

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if (record(stack) == null) {
            stack.set(ModComponents.SHARD.get(), roll(level.getRandom()));
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Recovered r = record(stack);
        if (r == null) {
            if (level instanceof ServerLevel server) {
                stack.set(ModComponents.SHARD.get(), roll(server.getRandom()));
            }
            return InteractionResult.SUCCESS;
        }
        if (level.isClientSide()) {
            ClientHooks.readShard(r);
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
