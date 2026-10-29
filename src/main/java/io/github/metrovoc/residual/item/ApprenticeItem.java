package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.apprentice.Apprentice;
import io.github.metrovoc.residual.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;

/** A folded-up apprentice. Set it down where it should live; that spot is its home. */
public class ApprenticeItem extends Item {
    public ApprenticeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = new BlockPlaceContext(ctx).getClickedPos();
        Apprentice a = ModEntities.APPRENTICE.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (a == null) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = ctx.getItemInHand();
        a.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, ctx.getRotation() + 180, 0);
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        a.setCustomName(name != null ? name : Component.literal(Apprentice.NAMES.get(level.getRandom().nextInt(Apprentice.NAMES.size()))));
        a.setHome(pos);
        level.addFreshEntity(a);
        level.playSound(null, pos, SoundEvents.COPPER_PLACE, SoundSource.NEUTRAL, 1f, 1.4f);
        stack.consume(1, ctx.getPlayer());
        return InteractionResult.SUCCESS;
    }
}
