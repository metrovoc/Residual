package io.github.metrovoc.residual.refine;

import com.mojang.serialization.MapCodec;
import io.github.metrovoc.residual.energy.PowerDeviceBlock;
import io.github.metrovoc.residual.energy.PowerMenu;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * One machine of a Refinery. Text comes in anywhere and goes out of its front, across the air to the next machine in line up to
 * {@link RefineryBlockEntity#REACH} blocks off, so a Refinery is a hall the stream runs through.
 */
public class RefineryBlock extends PowerDeviceBlock {
    public final Stage stage;

    public RefineryBlock(Stage stage, BlockBehaviour.Properties properties) {
        super(properties, ModBlockEntities.REFINERY, RefineryBlockEntity::new, PowerMenu.Kind.SMELTER);
        this.stage = stage;
    }

    @Override
    protected MapCodec<RefineryBlock> codec() {
        return simpleCodec(p -> new RefineryBlock(stage, p));
    }

    /** Its front points away from whoever places it, so a line goes up the way its builder walks. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown() && hit.getDirection().getAxis().isHorizontal()) {
            // sneaking, a machine turns its front to the face used, as a machine of a model does
            level.setBlock(pos, state.setValue(FACING, hit.getDirection()), Block.UPDATE_ALL);
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MenuProvider be) {
            player.openMenu(be, buf -> {
                buf.writeBlockPos(pos);
                buf.writeEnum(stage);
            });
        }
        return InteractionResult.SUCCESS;
    }

    /** Letters drift off a machine at work, and off a stage the ones it has thrown away. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(WORKING)) {
            level.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.1,
                    pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0.4 + random.nextDouble() * 0.3, 0);
        }
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return stage == Stage.TANK;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return stage == Stage.TANK ? 1f : super.getShadeBrightness(state, level, pos);
    }
}
