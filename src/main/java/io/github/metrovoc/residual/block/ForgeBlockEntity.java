package io.github.metrovoc.residual.block;

import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.item.DriveItem;
import io.github.metrovoc.residual.item.TokenizerItem;
import io.github.metrovoc.residual.learn.Archive;
import io.github.metrovoc.residual.learn.Bpe;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.menu.ForgeMenu;
import io.github.metrovoc.residual.model.Recovered;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.registry.ModComponents;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * Learns a vocabulary from the legible text on an Archive Drive, a merge at a time, and writes it onto a Tokenizer as
 * it goes. It carries on from the merges a tokenizer already has, so a bigger vocabulary keeps every token of a
 * smaller one, and a smaller target cuts a tokenizer back to the vocabulary it was on its way through.
 */
public class ForgeBlockEntity extends BlockEntity implements MenuProvider {
    public static final int TEXT = 0, CARD = 1, SLOTS = 2;
    /** The vocabulary sizes it can learn up to. */
    public static final int[] SIZES = {128, 256, 512, 1024, 2048};
    /** What its screen shows, as container data: the target, characters per token in hundredths, and the state. */
    public static final int TARGET = 0, CHARS = 1, STATE = 2, DATA = 3;
    /** Missing a drive or a tokenizer; merging; at the target; out of pairs that occur twice. */
    public static final int IDLE = 0, LEARNING = 1, DONE = 2, EXHAUSTED = 3;
    /** The button that wipes the tokenizer, after one per size. */
    public static final int START_OVER = SIZES.length;
    /** Merges learned per tick. */
    private static final int PACE = 2;

    private final SimpleContainer items = new SimpleContainer(SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            ForgeBlockEntity.this.setChanged();
        }
    };
    private int target = 2;
    private int state = IDLE;
    private double charsPerToken = 1;
    /** What it is learning, and the drive's records it learns from. */
    @Nullable
    private Bpe bpe;
    @Nullable
    private List<Recovered> source;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case TARGET -> target;
                case CHARS -> (int) Math.round(charsPerToken * 100);
                case STATE -> state;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int value) {
        }

        @Override
        public int getCount() {
            return DATA;
        }
    };

    public ForgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FORGE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ForgeBlockEntity be) {
        be.tick((ServerLevel) level);
    }

    private void tick(ServerLevel level) {
        ItemStack drive = items.getItem(TEXT), card = items.getItem(CARD);
        List<Recovered> records = DriveItem.records(drive);
        if (!(drive.getItem() instanceof DriveItem) || records.isEmpty() || !(card.getItem() instanceof TokenizerItem)) {
            bpe = null;
            source = null;
            state = IDLE;
            working(false);
            return;
        }
        int size = SIZES[target];
        Vocab have = TokenizerItem.vocab(card);
        if (have.size() > size) {
            have = have.first(size);
            write(card, have);
        }
        if (bpe == null || !records.equals(source) || !have.equals(bpe.vocab())) {
            bpe = new Bpe(Archive.legible(DriveItem.contents(drive)), have);
            source = records;
        }
        int was = state;
        boolean more = true;
        for (int i = 0; i < PACE && more && bpe.vocab().size() < size; i++) {
            more = bpe.step();
        }
        if (!bpe.vocab().equals(have)) {
            write(card, bpe.vocab());
        }
        state = bpe.vocab().size() >= size ? DONE : more ? LEARNING : EXHAUSTED;
        charsPerToken = bpe.charsPerToken();
        working(state == LEARNING);
        if (was == LEARNING && state != LEARNING) {
            finished(level);
        }
    }

    private void write(ItemStack card, Vocab vocab) {
        if (vocab.merges() == 0) {
            card.remove(ModComponents.VOCAB.get());
        } else {
            card.set(ModComponents.VOCAB.get(), vocab);
        }
        items.setChanged();
    }

    private void finished(ServerLevel level) {
        level.playSound(null, worldPosition, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.7f, 1.2f);
        level.sendParticles(ParticleTypes.ENCHANT, worldPosition.getX() + 0.5, worldPosition.getY() + 1.2,
                worldPosition.getZ() + 0.5, 30, 0.3, 0.2, 0.3, 0.6);
        for (ServerPlayer p : level.players()) {
            if (p.containerMenu instanceof ForgeMenu m && m.pos.equals(worldPosition) && state == DONE) {
                Awards.grant(p, "archive/tokenizer");
            }
        }
    }

    private void working(boolean on) {
        BlockState here = getBlockState();
        if (level != null && here.getValue(ForgeBlock.WORKING) != on) {
            level.setBlock(worldPosition, here.setValue(ForgeBlock.WORKING, on), Block.UPDATE_CLIENTS);
        }
    }

    /** A screen button: a vocabulary size by its place in {@link #SIZES}, or {@link #START_OVER}. */
    public void press(int id) {
        if (id >= 0 && id < SIZES.length) {
            target = id;
            setChanged();
        } else if (id == START_OVER && items.getItem(CARD).getItem() instanceof TokenizerItem) {
            write(items.getItem(CARD), Vocab.WORLD);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.residual.bpe_forge");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ForgeMenu(id, inventory, worldPosition, items, data);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            Containers.dropContents(level, pos, items);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out.child("items"), items.getItems());
        out.putInt("target", target);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        in.child("items").ifPresent(c -> ContainerHelper.loadAllItems(c, items.getItems()));
        target = Math.floorMod(in.getIntOr("target", 2), SIZES.length);
    }
}
