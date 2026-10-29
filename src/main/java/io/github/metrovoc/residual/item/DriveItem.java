package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.learn.Archive;
import io.github.metrovoc.residual.model.Recovered;
import io.github.metrovoc.residual.registry.ModComponents;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Holds the archive as far as it has been recovered. Use it to file every shard you carry into it; in a Model
 * Core's data slots, its legible text is what a model learns to read on.
 */
public class DriveItem extends Item {
    public DriveItem(Properties properties) {
        super(properties);
    }

    public static List<Recovered> records(ItemStack stack) {
        return stack.getOrDefault(ModComponents.RECORDS.get(), List.of());
    }

    /** Records by id, with the blocks each has lost. */
    public static Map<Integer, BitSet> contents(ItemStack stack) {
        Map<Integer, BitSet> out = new TreeMap<>();
        for (Recovered r : records(stack)) {
            out.put(r.record(), r.lost());
        }
        return out;
    }

    /** Files {@code shard} into the drive; returns whether it added anything. */
    public static boolean file(ItemStack drive, Recovered shard) {
        List<Recovered> list = new ArrayList<>(records(drive));
        for (int i = 0; i < list.size(); i++) {
            Recovered had = list.get(i);
            if (had.record() == shard.record()) {
                Recovered merged = had.merge(shard);
                if (merged.lost().equals(had.lost())) {
                    return false;
                }
                list.set(i, merged);
                drive.set(ModComponents.RECORDS.get(), List.copyOf(list));
                return true;
            }
        }
        list.add(shard);
        list.sort(Comparator.comparingInt(Recovered::record));
        drive.set(ModComponents.RECORDS.get(), List.copyOf(list));
        return true;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack drive = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        int filed = 0;
        int added = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            Recovered shard = ShardItem.record(stack);
            if (stack.getItem() instanceof ShardItem && shard != null) {
                while (!stack.isEmpty()) {
                    added += file(drive, shard) ? 1 : 0;
                    filed++;
                    stack.shrink(1);
                }
            }
        }
        if (filed == 0) {
            player.sendOverlayMessage(Component.translatable("residual.drive.nothing"));
            return InteractionResult.FAIL;
        }
        player.sendOverlayMessage(filed > 1 ? Component.translatable("residual.drive.filed", filed, added)
                : Component.translatable(added > 0 ? "residual.drive.filed_new" : "residual.drive.filed_known"));
        level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PUT, SoundSource.PLAYERS, 0.8f, 1.1f);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        List<Recovered> list = records(stack);
        if (list.isEmpty()) {
            out.accept(Component.translatable("residual.drive.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        long chars = 0;
        long legible = 0;
        int whole = 0;
        for (Recovered r : list) {
            Archive.Record text = r.text();
            if (text != null) {
                chars += text.text().length();
                legible += Math.round(text.text().length() * r.legible());
                whole += r.whole() ? 1 : 0;
            }
        }
        int total = (int) Archive.records().stream().filter(r -> !Archive.heldOut(r.id())).count();
        out.accept(Component.translatable("residual.drive.records", list.size(), total, whole).withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable("residual.drive.characters", String.format(Locale.ROOT, "%,d", legible),
                String.format(Locale.ROOT, "%.0f", chars == 0 ? 0 : 100.0 * legible / chars)).withStyle(ChatFormatting.DARK_GRAY));
    }
}
