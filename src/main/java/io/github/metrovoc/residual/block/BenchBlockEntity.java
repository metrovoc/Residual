package io.github.metrovoc.residual.block;

import com.mojang.serialization.Codec;
import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.bench.Design;
import io.github.metrovoc.residual.bench.MaskData;
import io.github.metrovoc.residual.bench.Spec;
import io.github.metrovoc.residual.bench.Specs;
import io.github.metrovoc.residual.item.MaskItem;
import io.github.metrovoc.residual.registry.ModAttachments;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.registry.ModItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Keeps the design wired for each spec on this Bench, shared by everyone who works at it. */
public class BenchBlockEntity extends BlockEntity {
    private static final Codec<Map<String, Design>> DESIGNS = Codec.unboundedMap(Codec.STRING, Design.CODEC);
    private final Map<String, Design> designs = new HashMap<>();

    public BenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BENCH.get(), pos, state);
    }

    public Design design(Spec spec) {
        Design d = designs.get(spec.id());
        return d != null && d.fits(spec) ? d : Design.blank(spec);
    }

    /** Stores an edit sent by a player at this Bench. */
    public void edit(Spec spec, Design design) {
        if (!design.fits(spec)) {
            return;
        }
        designs.put(spec.id(), design);
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Checks the design for {@code spec} on every case. If it passes, the player has verified the spec, and a blank
     * photomask from their inventory takes the design.
     */
    public void record(ServerPlayer player, Spec spec) {
        List<String> verified = new ArrayList<>(player.getData(ModAttachments.VERIFIED));
        if (!spec.openTo(verified)) {
            player.sendOverlayMessage(Component.translatable("residual.bench.locked"));
            return;
        }
        Design design = design(spec);
        if (!Spec.passes(spec.check(design))) {
            player.sendOverlayMessage(Component.translatable("residual.bench.not_yet"));
            return;
        }
        Component title = Component.translatable("residual.spec." + spec.id());
        if (!verified.contains(spec.id())) {
            verified.add(spec.id());
            player.setData(ModAttachments.VERIFIED, List.copyOf(verified));
            Awards.grant(player, "bench/" + spec.id());
            if (verified.containsAll(Specs.all().stream().map(Spec::id).toList())) {
                Awards.grant(player, "bench/all");
            }
            level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1f, 1.4f);
        }
        if (spec.unlocks().isEmpty()) {
            player.sendOverlayMessage(Component.translatable("residual.bench.verified", title));
            return;
        }
        ItemStack blank = ItemStack.EMPTY;
        for (ItemStack stack : player.getInventory()) {
            if (stack.is(ModItems.PHOTOMASK.get()) && MaskItem.mask(stack) == null) {
                blank = stack;
                break;
            }
        }
        if (blank.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("residual.bench.no_mask", title));
            return;
        }
        blank.shrink(1);
        ItemStack mask = MaskItem.of(ModItems.PHOTOMASK.get(), MaskData.verified(spec, design, player.getName().getString()));
        if (!player.getInventory().add(mask)) {
            player.drop(mask, false);
        }
        level.playSound(null, worldPosition, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 1f, 1f);
        player.sendOverlayMessage(Component.translatable("residual.bench.recorded", title));
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.store("designs", DESIGNS, designs);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        designs.clear();
        in.read("designs", DESIGNS).ifPresent(designs::putAll);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        out.store("designs", DESIGNS, designs);
        return out.buildResult();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
