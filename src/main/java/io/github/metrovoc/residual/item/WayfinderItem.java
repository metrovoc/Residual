package io.github.metrovoc.residual.item;

import com.mojang.datafixers.util.Pair;
import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.client.ClientHooks;
import io.github.metrovoc.residual.registry.ModComponents;
import io.github.metrovoc.residual.registry.ModItems;
import io.github.metrovoc.residual.world.RuinKinds;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.Nullable;

/**
 * A Wayfinder: tuned to a kind of ruin its holder has read of, its needle points to the nearest one, and while it is in
 * hand it says how far that is. Use it to choose what to look for; a search reaches {@link #REACH} blocks.
 */
public class WayfinderItem extends Item {
    public static final int REACH = 4000;

    public WayfinderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            ClientHooks.openWayfinder(hand);
        }
        return InteractionResult.SUCCESS;
    }

    /** Looks for the nearest ruin of {@code kind} from {@code player}, and tunes the Wayfinder in {@code hand} to it. */
    public static void find(ServerPlayer player, InteractionHand hand, String kind) {
        ItemStack stack = player.getItemInHand(hand);
        Optional<RuinKinds.Kind> known = RuinKinds.known(player).stream().filter(k -> k.name().equals(kind)).findFirst();
        if (!stack.is(ModItems.WAYFINDER.get()) || known.isEmpty()) {
            return;
        }
        ServerLevel level = player.level();
        Optional<Holder.Reference<Structure>> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(known.get().structure());
        Component name = Component.translatable("residual.ruin." + kind);
        stack.set(ModComponents.WAYFINDER_KIND.get(), kind);
        Pair<BlockPos, Holder<Structure>> found = structure.isEmpty() ? null : level.getChunkSource().getGenerator()
                .findNearestMapStructure(level, HolderSet.direct(structure.get()), player.blockPosition(), REACH / 16, false);
        if (found == null) {
            stack.remove(DataComponents.LODESTONE_TRACKER);
            player.sendOverlayMessage(Component.translatable("residual.wayfinder.none", name, String.format("%,d", REACH)));
            return;
        }
        stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(GlobalPos.of(level.dimension(), found.getFirst())), false));
        level.playSound(null, player.blockPosition(), SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1f, 1f);
        player.sendOverlayMessage(distance(name, player, found.getFirst()));
        Awards.grant(player, "ruins/wayfinder");
    }

    private static Component distance(Component name, Player player, BlockPos to) {
        double d = Math.sqrt(player.blockPosition().distToLowCornerSqr(to.getX(), player.getY(), to.getZ()));
        return Component.translatable("residual.wayfinder.distance", name, String.format("%,d", Math.round(d)));
    }

    /** While it is held, says every second how far its ruin lies. */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
        String kind = stack.get(ModComponents.WAYFINDER_KIND.get());
        if (slot != null && slot.getType() == EquipmentSlot.Type.HAND && owner instanceof ServerPlayer player && tracker != null
                && kind != null && tracker.target().isPresent() && tracker.target().get().dimension() == level.dimension()
                && level.getGameTime() % 20 == 0) {
            player.sendOverlayMessage(distance(Component.translatable("residual.ruin." + kind), player, tracker.target().get().pos()));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        String kind = stack.get(ModComponents.WAYFINDER_KIND.get());
        out.accept(kind == null ? Component.translatable("residual.wayfinder.untuned").withStyle(ChatFormatting.GRAY)
                : Component.translatable("residual.wayfinder.tuned", Component.translatable("residual.ruin." + kind)).withStyle(ChatFormatting.AQUA));
    }
}
