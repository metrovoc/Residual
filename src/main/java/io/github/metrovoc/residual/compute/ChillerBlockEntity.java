package io.github.metrovoc.residual.compute;

import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.energy.PowerBlockEntity;
import io.github.metrovoc.residual.energy.PowerDeviceBlock;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Takes up to {@link #HOLDS} FE a tick of heat from the devices along its cooling line, as long as it has power and
 * somewhere to put the heat: water beside it gives back a unit of cooling for every 0.3 FE, ice or snow for every 0.2,
 * and a cold biome for every 0.1, which is where a hall's PUE of 1.3, 1.2 or 1.1 comes from.
 */
public class ChillerBlockEntity extends PowerBlockEntity {
    public static final int HOLDS = 8192;
    private static final int CAPACITY = 64_000, IN = 8_000;
    /** FE a unit of heat costs: nothing to give the heat to is 0. */
    private double cost;
    private int heat;
    private int lookIn;
    private long spent;

    public ChillerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHILLER.get(), pos, state, CAPACITY, IN, 0);
    }

    @Override
    public Role role() {
        return Role.MACHINE;
    }

    /** {@code fe} of heat for this tick. */
    void carry(int fe) {
        heat += fe;
    }

    /** The heat it can hold to: its all while it has somewhere to put it and power to do so. */
    int room() {
        if (level != null && cost == 0) {
            look();
        }
        return cost > 0 && energy().getAmountAsInt() > 0 ? HOLDS : 0;
    }

    /** All the power it has spent. */
    long spent() {
        return spent;
    }

    public long spentSoFar() {
        return spent;
    }

    private void look() {
        cost = 0;
        BlockPos at = worldPosition;
        if (level.getBiome(at).value().coldEnoughToSnow(at, level.getSeaLevel())) {
            cost = 0.1;
            return;
        }
        for (Direction d : Direction.values()) {
            BlockState s = level.getBlockState(at.relative(d));
            if (s.is(BlockTags.ICE) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW)) {
                cost = 0.2;
                return;
            }
            if (s.getFluidState().is(FluidTags.WATER)) {
                cost = 0.3;
            }
        }
    }

    @Override
    protected void tick(ServerLevel level) {
        super.tick(level);
        if (lookIn-- <= 0) {
            lookIn = 20;
            look();
        }
        boolean working = heat > 0 && cost > 0;
        if (working) {
            int stored = energy().getAmountAsInt();
            int due = Math.min(stored, (int) Math.ceil(heat * cost));
            energy().set(stored - due);
            spent += due;
            heat = 0;
            setChanged();
        }
        if (level.getGameTime() % 20 == 0) {
            BlockState here = getBlockState();
            if (here.getValue(PowerDeviceBlock.WORKING) != working) {
                level.setBlock(worldPosition, here.setValue(PowerDeviceBlock.WORKING, working), Block.UPDATE_CLIENTS);
            }
            if (working) {
                for (ServerPlayer player : level.players()) {
                    if (player.distanceToSqr(worldPosition.getCenter()) < 32 * 32) {
                        Awards.grant(player, "compute/loop");
                    }
                }
            }
        }
    }

    /** One line about it for the action bar. */
    public Component describe() {
        if (cost == 0) {
            look();
        }
        return cost == 0 ? Component.translatable("residual.chiller.no_sink")
                : Component.translatable("residual.chiller.status", HOLDS, String.format(Locale.ROOT, "%.1f", 1 + cost),
                        energy().getAmountAsInt());
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putLong("spent", spent);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        spent = in.getLongOr("spent", 0);
    }
}
