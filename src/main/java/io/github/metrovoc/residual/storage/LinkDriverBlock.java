package io.github.metrovoc.residual.storage;

import io.github.metrovoc.residual.energy.PowerDeviceBlock;
import io.github.metrovoc.residual.energy.PowerMenu;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * A Link Driver: joins a storage network and a power network, and while it has power the storage network's links carry four
 * times as much and its Access Points reach four times as far. Nothing stops without it.
 */
public class LinkDriverBlock extends PowerDeviceBlock implements Node {
    public static final MapCodec<LinkDriverBlock> CODEC = simpleCodec(LinkDriverBlock::new);

    public LinkDriverBlock(Properties properties) {
        super(properties, ModBlockEntities.LINK_DRIVER, LinkDriverBlockEntity::new, PowerMenu.Kind.GENERATOR);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** What it has and what its network's links carry, said over the hotbar: it needs no screen for one number. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof LinkDriverBlockEntity driver) {
            player.sendOverlayMessage(Component.translatable("residual.link_driver.status",
                    String.format(Locale.ROOT, "%,d", driver.energy().getAmountAsInt()),
                    String.format(Locale.ROOT, "%,d", driver.energy().getCapacityAsInt()),
                    String.format(Locale.ROOT, "%,d", StorageNetwork.at(server, pos).linkRate())));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server && !oldState.is(this)) {
            StorageNetwork.changed(server);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        StorageNetwork.changed(level);
    }
}
