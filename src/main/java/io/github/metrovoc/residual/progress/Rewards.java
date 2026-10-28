package io.github.metrovoc.residual.progress;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.learn.Curriculum;
import io.github.metrovoc.residual.registry.ModAttachments;
import io.github.metrovoc.residual.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/** What a player gets for passing a lesson or solving a case. */
public final class Rewards {
    private Rewards() {
    }

    public static void complete(ServerPlayer player, Curriculum.Lesson lesson) {
        Progress progress = player.getData(ModAttachments.PROGRESS);
        boolean first = !progress.has(lesson.id());
        Component title = Component.translatable("residual.lesson." + lesson.id() + ".title");
        if (first) {
            player.setData(ModAttachments.PROGRESS, progress.with(lesson.id()));
            player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6f, 1f);
        }
        // passing again replaces an Insight that was lost
        if (lesson.insight() != null) {
            ItemStack insight = new ItemStack(ModItems.INSIGHTS.get(lesson.insight()).get());
            if (first || !player.getInventory().contains(insight)) {
                if (!player.getInventory().add(insight)) {
                    player.drop(insight, false);
                }
            }
        }
        AdvancementHolder advancement = player.level().getServer().getAdvancements().get(Residual.id("lesson/" + lesson.id()));
        if (advancement != null) {
            player.getAdvancements().award(advancement, "done");
        }
        String key = lesson.kind() == Curriculum.Kind.CASE ? "residual.reward.case" : "residual.reward.lesson";
        player.sendSystemMessage(Component.translatable(first ? key : "residual.reward.again", title).withStyle(ChatFormatting.GREEN));
        if (first) {
            Curriculum.ALL.stream()
                    .filter(next -> !player.getData(ModAttachments.PROGRESS).has(next.id()))
                    .findFirst()
                    .ifPresent(next -> player.sendSystemMessage(Component.translatable("residual.reward.next",
                            Component.translatable("residual.lesson." + next.id() + ".title")).withStyle(ChatFormatting.GRAY)));
        }
    }
}
