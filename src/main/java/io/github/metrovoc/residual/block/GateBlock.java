package io.github.metrovoc.residual.block;

import com.mojang.serialization.MapCodec;
import io.github.metrovoc.residual.client.ClientHooks;
import io.github.metrovoc.residual.registry.ModComponents;
import io.github.metrovoc.residual.registry.ModItems;
import io.github.metrovoc.residual.world.Relays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A Relay Gate: a pad to stand on that sends whoever uses it to any other gate on its relay network, once that network is
 * open. A Relay Card carries one gate's place to another and tunes the two to each other, which joins their networks at
 * any distance and between dimensions.
 */
public class GateBlock extends BaseEntityBlock {
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final MapCodec<GateBlock> CODEC = simpleCodec(GateBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 4, 16);

    public GateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OPEN, false));
    }

    @Override
    protected MapCodec<GateBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GateBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    /** Why a network's gates and links are shut, for the action bar. */
    public static Component closed(@Nullable Relays.Net net) {
        if (net == null || !net.live()) {
            return Component.translatable("residual.relay_gate.no_model");
        }
        return net.reads() ? Component.translatable("residual.relay_gate.too_slow", net.capacity(), Relays.OPEN_AT)
                : Component.translatable("residual.relay_gate.cannot_read");
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (level instanceof ServerLevel server && stack.has(DataComponents.CUSTOM_NAME)) {
            Relays.sign(server, pos, Relays.Kind.GATE, 0);
            Relays.of(server.getServer()).rename(GlobalPos.of(level.dimension(), pos), stack.getHoverName().getString());
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server && !oldState.is(this)) {
            Relays.sign(server, pos, Relays.Kind.GATE, 0);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        Relays.leave(level, pos);
    }

    /** A Relay Card takes this gate's place, and then tunes the gate it is next used on to that one. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModItems.RELAY_CARD.get())) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level instanceof ServerLevel server) {
            Relays relays = Relays.of(server.getServer());
            GlobalPos here = GlobalPos.of(level.dimension(), pos);
            GlobalPos carried = stack.get(ModComponents.GATE.get());
            if (carried != null && !carried.equals(here) && relays.part(carried) != null && relays.part(carried).kind() == Relays.Kind.GATE) {
                relays.tune(here, carried);
                stack.remove(ModComponents.GATE.get());
                player.sendOverlayMessage(Component.translatable("residual.relay_gate.tuned"));
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1f, 1.4f);
            } else {
                stack.set(ModComponents.GATE.get(), here);
                player.sendOverlayMessage(Component.translatable("residual.relay_gate.carried"));
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1f, 1f);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            ClientHooks.openGate(GlobalPos.of(level.dimension(), pos));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(OPEN) && random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.15 + 0.7 * random.nextDouble(), pos.getY() + 0.3,
                    pos.getZ() + 0.15 + 0.7 * random.nextDouble(), 0, 0.03, 0);
        }
    }
}
