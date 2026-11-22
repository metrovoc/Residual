package io.github.metrovoc.residual.block;

import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.interior.Halls;
import io.github.metrovoc.residual.interior.Summary;
import io.github.metrovoc.residual.item.ModelDriveItem;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.registry.ModComponents;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A Model Core's drive. While the Core is loaded and holds one, it keeps that model's hall awake. */
public class CoreBlockEntity extends BlockEntity {
    /** Ticks between two calls that keep the hall awake; a hall with nobody in it sleeps after 200. */
    private static final int EVERY = 20;
    private ItemStack drive = ItemStack.EMPTY;

    public CoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CORE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CoreBlockEntity core) {
        UUID model = ModelDriveItem.model(core.drive);
        if (model != null && level instanceof ServerLevel server && level.getGameTime() % EVERY == 0) {
            Halls.get(server.getServer()).keepAwake(server.getServer(), model);
        }
    }

    /** Takes {@code stack}, a model drive, which gets a model of its own if it is blank. */
    void insert(ServerPlayer player, ItemStack stack) {
        if (ModelDriveItem.model(stack) == null) {
            stack.set(ModComponents.MODEL.get(), UUID.randomUUID());
        }
        drive = stack;
        changed(true);
        level.playSound(null, worldPosition, SoundEvents.VAULT_INSERT_ITEM, SoundSource.BLOCKS, 0.8f, 1.2f);
        Awards.grant(player, "model/drive");
    }

    /** Hands the drive to {@code player}, whose main hand is empty. */
    void eject(ServerPlayer player) {
        player.setItemInHand(InteractionHand.MAIN_HAND, take());
        changed(false);
        level.playSound(null, worldPosition, SoundEvents.VAULT_EJECT_ITEM, SoundSource.BLOCKS, 0.8f, 1.2f);
    }

    /** Takes {@code player} into the hall of the model on the drive. */
    void enter(ServerPlayer player) {
        UUID model = ModelDriveItem.model(drive);
        if (model != null && Halls.get(player.level().getServer()).enter(player, model)) {
            Awards.grant(player, "model/inside");
        }
    }

    /** The drive, with what its model now is written on it; the Core is left without one. */
    private ItemStack take() {
        ItemStack out = drive;
        drive = ItemStack.EMPTY;
        UUID model = ModelDriveItem.model(out);
        if (model != null && level instanceof ServerLevel server) {
            Summary summary = Summary.of(Halls.get(server.getServer()), model);
            if (summary != null) {
                out.set(ModComponents.MODEL_SUMMARY.get(), summary);
            }
        }
        return out;
    }

    private void changed(boolean holds) {
        setChanged();
        level.setBlock(worldPosition, getBlockState().setValue(CoreBlock.DRIVE, holds), 3);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (!drive.isEmpty() && level != null) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), take());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        drive = input.read("drive", ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!drive.isEmpty()) {
            output.store("drive", ItemStack.CODEC, drive);
        }
    }
}
