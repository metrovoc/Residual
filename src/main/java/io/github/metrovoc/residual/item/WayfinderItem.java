package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.client.ClientHooks;
import io.github.metrovoc.residual.registry.ModComponents;
import io.github.metrovoc.residual.registry.ModItems;
import io.github.metrovoc.residual.world.RuinKinds;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * A Wayfinder: tuned to a kind of ruin its holder has read of, its needle points to the nearest one, and while it is in
 * hand it says how far that is. Use it to choose what to look for; a search reaches {@link #REACH} blocks, a few
 * milliseconds of it a tick, so that a far search never stalls the server.
 */
public class WayfinderItem extends Item {
    public static final int REACH = 10000;
    /** How long the searches under way may look each tick, in nanoseconds. */
    private static final long BUDGET = 5_000_000;
    private static final List<Search> SEARCHES = new ArrayList<>();

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

    /** Starts looking for the nearest ruin of {@code kind} from {@code player}, for the Wayfinder in {@code hand}. */
    public static void find(ServerPlayer player, InteractionHand hand, String kind) {
        ItemStack stack = player.getItemInHand(hand);
        Optional<RuinKinds.Kind> known = RuinKinds.known(player).stream().filter(k -> k.name().equals(kind)).findFirst();
        if (!stack.is(ModItems.WAYFINDER.get()) || known.isEmpty()) {
            return;
        }
        ServerLevel level = player.level();
        Component name = Component.translatable("residual.ruin." + kind);
        stack.set(ModComponents.WAYFINDER_KIND.get(), kind);
        // the needle spins while it looks
        stack.remove(DataComponents.LODESTONE_TRACKER);
        SEARCHES.removeIf(s -> s.player == player);
        Optional<Holder.Reference<Structure>> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(known.get().structure());
        Optional<RandomSpreadStructurePlacement> placement = structure.flatMap(s -> level.getChunkSource().getGeneratorState()
                .getPlacementsForStructure(s).stream().filter(RandomSpreadStructurePlacement.class::isInstance)
                .map(RandomSpreadStructurePlacement.class::cast).findFirst());
        if (placement.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("residual.wayfinder.none", name, String.format("%,d", REACH)));
            return;
        }
        SEARCHES.add(new Search(player, hand, kind, level, structure.get(), placement.get(), rings(level, structure.get())));
        player.sendOverlayMessage(Component.translatable("residual.wayfinder.searching", name));
    }

    /** Carries the searches under way on for at most {@link #BUDGET} a tick, and tunes each Wayfinder whose search ends. */
    public static void tick(ServerTickEvent.Post event) {
        long until = System.nanoTime() + BUDGET;
        for (Iterator<Search> it = SEARCHES.iterator(); it.hasNext() && System.nanoTime() < until; ) {
            Search search = it.next();
            if (search.player.isRemoved() || search.player.level() != search.level) {
                it.remove();
                continue;
            }
            BlockPos found = null;
            boolean more = true;
            while (found == null && System.nanoTime() < until && (more = search.next())) {
                found = search.look();
            }
            if (found != null || !more) {
                it.remove();
                search.end(found);
            }
        }
    }

    /**
     * How many rings of placement cells a search for {@code structure} looks through to reach {@link #REACH} blocks: the
     * search counts rings of its spacing, not chunks.
     */
    public static int rings(ServerLevel level, Holder<Structure> structure) {
        int spacing = level.getChunkSource().getGeneratorState().getPlacementsForStructure(structure).stream()
                .filter(RandomSpreadStructurePlacement.class::isInstance)
                .mapToInt(p -> ((RandomSpreadStructurePlacement) p).spacing()).min().orElse(1);
        return Mth.ceil(REACH / (16.0 * spacing));
    }

    /**
     * A search for the nearest ruin of one kind, ring by ring of placement cells round where it began, as vanilla's
     * {@code findNearestMapStructure} looks, carried on from tick to tick.
     */
    private static final class Search {
        private final ServerPlayer player;
        private final InteractionHand hand;
        private final String kind;
        private final ServerLevel level;
        private final Holder<Structure> structure;
        private final RandomSpreadStructurePlacement placement;
        private final int chunkX, chunkZ, rings;
        /** The cell looked at last, {@code ring} cells out: ({@code dx}, {@code dz}) cells from the first. */
        private int ring, dx, dz = -1;

        private Search(ServerPlayer player, InteractionHand hand, String kind, ServerLevel level, Holder<Structure> structure,
                       RandomSpreadStructurePlacement placement, int rings) {
            this.player = player;
            this.hand = hand;
            this.kind = kind;
            this.level = level;
            this.structure = structure;
            this.placement = placement;
            this.chunkX = SectionPos.blockToSectionCoord(player.getBlockX());
            this.chunkZ = SectionPos.blockToSectionCoord(player.getBlockZ());
            this.rings = rings;
        }

        /** Moves on to the next cell on the edge of a ring; false past the last ring. */
        private boolean next() {
            do {
                if (++dz > ring) {
                    if (++dx > ring) {
                        if (++ring > rings) {
                            return false;
                        }
                        dx = -ring;
                    }
                    dz = -ring;
                }
            } while (Math.abs(dx) != ring && Math.abs(dz) != ring);
            return true;
        }

        /** Where the ruin of the cell looked at lies, or null if it has none, tested as vanilla tests a cell. */
        private @Nullable BlockPos look() {
            ChunkPos chunk = placement.getPotentialStructureChunk(level.getChunkSource().getGeneratorState().getLevelSeed(),
                    chunkX + placement.spacing() * dx, chunkZ + placement.spacing() * dz);
            StructureManager structures = level.structureManager();
            StructureCheckResult seen = structures.checkStructurePresence(chunk, structure.value(), placement, false);
            if (seen != StructureCheckResult.CHUNK_LOAD_NEEDED) {
                return seen == StructureCheckResult.START_PRESENT ? placement.getLocatePos(chunk) : null;
            }
            ChunkAccess access = level.getChunk(chunk.x(), chunk.z(), ChunkStatus.STRUCTURE_STARTS);
            StructureStart start = structures.getStartForStructure(SectionPos.bottomOf(access), structure.value(), access);
            return start != null && start.isValid() ? placement.getLocatePos(start.getChunkPos()) : null;
        }

        /** Tunes the Wayfinder to {@code found}, if it is still in hand and still looking for this kind. */
        private void end(@Nullable BlockPos found) {
            ItemStack stack = player.getItemInHand(hand);
            if (!stack.is(ModItems.WAYFINDER.get()) || !kind.equals(stack.get(ModComponents.WAYFINDER_KIND.get()))) {
                return;
            }
            Component name = Component.translatable("residual.ruin." + kind);
            if (found == null) {
                player.sendOverlayMessage(Component.translatable("residual.wayfinder.none", name, String.format("%,d", REACH)));
                return;
            }
            stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(GlobalPos.of(level.dimension(), found)), false));
            level.playSound(null, player.blockPosition(), SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1f, 1f);
            player.sendOverlayMessage(distance(name, player, found));
            Awards.grant(player, "ruins/wayfinder");
        }
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
