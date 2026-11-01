package io.github.metrovoc.residual.block;

import com.mojang.serialization.MapCodec;
import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.energy.PowerNetwork;
import io.github.metrovoc.residual.energy.PowerNode;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.storage.DeviceBlock;
import io.github.metrovoc.residual.world.Relays;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A Relay Link: every face joins the storage and power networks that touch it, and on an open relay network those become
 * one with the ones at every other link on it, however far off. Its faces light while it carries anything.
 */
public class RelayLinkBlock extends DeviceBlock implements PowerNode {
    public static final BooleanProperty WORKING = BooleanProperty.create("working");
    public static final MapCodec<RelayLinkBlock> CODEC = simpleCodec(RelayLinkBlock::new);

    public RelayLinkBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(WORKING, false));
    }

    @Override
    protected MapCodec<RelayLinkBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WORKING);
    }

    @Override
    public boolean joins(BlockState state, Direction face) {
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RelayLinkBlockEntity(pos, state);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server && !oldState.is(this)) {
            PowerNetwork.changed(server);
            Relays.sign(server, pos, Relays.Kind.LINK, 0);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        PowerNetwork.changed(level);
        Relays.leave(level, pos);
    }

    /** Says how it stands: what is in the way, or how much it carries of how much it could. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            Relays.Net net = Relays.of(server.getServer()).netOf(GlobalPos.of(level.dimension(), pos));
            if (net != null && player instanceof ServerPlayer sp && (net.itemsPerSecond() > 0 || net.fePerSecond() > 0)) {
                Awards.grant(sp, "deploy/link");
            }
            player.sendOverlayMessage(net == null || !net.open() ? GateBlock.closed(net)
                    : Component.translatable("residual.relay_link.status",
                            String.format(Locale.ROOT, "%,d", net.itemsPerSecond()),
                            String.format(Locale.ROOT, "%,d", (long) net.capacity() * Relays.ITEMS_A_TICK * 20),
                            String.format(Locale.ROOT, "%,d", net.fePerSecond() / 20),
                            String.format(Locale.ROOT, "%,d", (long) net.capacity() * Relays.FE_A_TICK)));
        }
        return InteractionResult.SUCCESS;
    }
}
