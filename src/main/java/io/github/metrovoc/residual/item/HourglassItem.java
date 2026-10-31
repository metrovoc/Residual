package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.block.Bursts;
import io.github.metrovoc.residual.block.ForecasterBlockEntity;
import io.github.metrovoc.residual.learn.Forecast.Kind;
import io.github.metrovoc.residual.registry.ModComponents;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import org.jetbrains.annotations.Nullable;

/**
 * The Hourglass keeps time while it is carried, a second for every second, and spends it in bursts on a block a
 * Forecaster's model is trusted with. Bound to a Forecaster by sneak-using it; used on a machine or a crop of a kind that
 * Forecaster runs ahead, it runs that block twice as fast for thirty seconds, and each use on it while that lasts doubles
 * the pace, up to sixteen times, for twice the kept time each step. The Fabricator makes it from an attention mask.
 */
public class HourglassItem extends Item {
    /** It keeps at most an hour. */
    public static final int MOST = 60 * 60 * 20;
    /** A burst lasts thirty seconds. */
    public static final int BURST = 30 * 20;
    /** The Lab tier's fastest. */
    public static final int FASTEST = 16;

    public HourglassItem(Properties properties) {
        super(properties);
    }

    public static int kept(ItemStack stack) {
        return stack.getOrDefault(ModComponents.KEPT_TIME.get(), 0);
    }

    /** Kept time a burst at {@code factor} costs: thirty seconds at twice as fast, doubling with each step. */
    public static int cost(int factor) {
        return BURST * factor / 2;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        if (level.getGameTime() % 20 == 0 && kept(stack) < MOST) {
            stack.set(ModComponents.KEPT_TIME.get(), Math.min(MOST, kept(stack) + 20));
        }
    }

    /** Acts before the block it is used on, so a furnace runs ahead instead of opening its screen. */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level) || !(ctx.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        BlockPos pos = ctx.getClickedPos();
        if (player.isShiftKeyDown() && level.getBlockEntity(pos) instanceof ForecasterBlockEntity) {
            stack.set(ModComponents.FORECASTER.get(), GlobalPos.of(level.dimension(), pos.immutable()));
            say(player, Component.translatable("residual.hourglass.bound"));
            return InteractionResult.SUCCESS;
        }
        Kind kind = ForecasterBlockEntity.kindOf(level, pos);
        if (kind == null) {
            return InteractionResult.PASS;
        }
        GlobalPos bound = stack.get(ModComponents.FORECASTER.get());
        if (bound == null) {
            return refuse(player, "residual.hourglass.unbound");
        }
        if (!bound.dimension().equals(level.dimension()) || !level.isLoaded(bound.pos())
                || !(level.getBlockEntity(bound.pos()) instanceof ForecasterBlockEntity forecaster)) {
            return refuse(player, "residual.hourglass.no_forecaster");
        }
        if (!forecaster.trusts(kind)) {
            return refuse(player, kind == Kind.CROPS ? "residual.hourglass.untrusted.crops" : "residual.hourglass.untrusted.machines");
        }
        int factor = Bursts.next(level, pos);
        if (factor > FASTEST) {
            return refuse(player, "residual.hourglass.fastest");
        }
        int cost = cost(factor);
        if (kept(stack) < cost) {
            say(player, Component.translatable("residual.hourglass.short", time(cost), time(kept(stack))).withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        stack.set(ModComponents.KEPT_TIME.get(), kept(stack) - cost);
        Bursts.start(level, pos, factor, BURST);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 0.6f + 0.1f * Mth.log2(factor));
        say(player, Component.translatable("residual.hourglass.burst", factor, time(BURST)));
        Awards.grant(player, "forecast/hourglass");
        return InteractionResult.SUCCESS;
    }

    /** Reached when the block did nothing with it either: says what it works on. */
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        return ctx.getPlayer() instanceof ServerPlayer player ? refuse(player, "residual.hourglass.not_a_block") : InteractionResult.PASS;
    }

    private static InteractionResult refuse(ServerPlayer player, String key) {
        say(player, Component.translatable(key).withStyle(ChatFormatting.GRAY));
        return InteractionResult.FAIL;
    }

    private static void say(ServerPlayer player, Component message) {
        player.sendOverlayMessage(message);
    }

    /** Ticks as minutes and seconds. */
    public static String time(int ticks) {
        int s = ticks / 20;
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * kept(stack) / MOST);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x3CD6C6;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("residual.hourglass.kept", time(kept(stack)), time(MOST)).withStyle(ChatFormatting.GRAY));
        GlobalPos bound = stack.get(ModComponents.FORECASTER.get());
        out.accept(bound == null ? Component.translatable("residual.hourglass.tooltip_unbound").withStyle(ChatFormatting.GRAY)
                : Component.translatable("residual.hourglass.tooltip_bound", bound.pos().getX(), bound.pos().getY(), bound.pos().getZ())
                .withStyle(ChatFormatting.GRAY));
    }
}
