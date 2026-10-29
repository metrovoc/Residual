package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.agent.Agent;
import io.github.metrovoc.residual.registry.ModComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Marks blocks for an agent: the two ends of a row to build, or the waymarks of a haul. Use it on blocks to mark
 * or unmark them, on an agent to hand it the marks, and sneak-use it in the air to clear them.
 */
public class SurveyRodItem extends Item {
    public static final int MOST = 8;

    public SurveyRodItem(Properties properties) {
        super(properties);
    }

    public static List<BlockPos> marks(ItemStack stack) {
        return stack.getOrDefault(ModComponents.MARKS.get(), List.of());
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (ctx.getLevel().isClientSide() || player == null) {
            return InteractionResult.SUCCESS;
        }
        List<BlockPos> marks = new ArrayList<>(marks(ctx.getItemInHand()));
        BlockPos pos = ctx.getClickedPos();
        if (marks.remove(pos)) {
            player.sendOverlayMessage(Component.translatable("residual.rod.unmarked", pos.toShortString()));
        } else if (marks.size() >= MOST) {
            player.sendOverlayMessage(Component.translatable("residual.rod.full", MOST));
            return InteractionResult.FAIL;
        } else {
            marks.add(pos.immutable());
            player.sendOverlayMessage(Component.translatable("residual.rod.marked", marks.size(), pos.toShortString()));
        }
        ctx.getItemInHand().set(ModComponents.MARKS.get(), List.copyOf(marks));
        ctx.getLevel().playSound(null, pos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.8f, 0.8f + 0.1f * marks.size());
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Agent a)) {
            return InteractionResult.PASS;
        }
        if (!player.level().isClientSide()) {
            a.setMarks(marks(stack));
            player.sendOverlayMessage(Component.translatable("residual.rod.handed", a.getDisplayName()));
            player.level().playSound(null, a.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.2f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            player.getItemInHand(hand).remove(ModComponents.MARKS.get());
            player.sendOverlayMessage(Component.translatable("residual.rod.cleared"));
        }
        return InteractionResult.SUCCESS;
    }

    /** Shows the marks while the rod is held. */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        if (slot == EquipmentSlot.MAINHAND && level.getGameTime() % 10 == 0 && entity instanceof Player player) {
            for (BlockPos p : marks(stack)) {
                level.sendParticles(player instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null, ParticleTypes.END_ROD,
                        true, false, p.getX() + 0.5, p.getY() + 1.2, p.getZ() + 0.5, 1, 0, 0.1, 0, 0);
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        List<BlockPos> marks = marks(stack);
        if (marks.isEmpty()) {
            out.accept(Component.translatable("residual.rod.none").withStyle(ChatFormatting.GRAY));
        }
        for (int i = 0; i < marks.size(); i++) {
            out.accept(Component.literal((i + 1) + ". " + marks.get(i).toShortString()).withStyle(ChatFormatting.GRAY));
        }
    }
}
