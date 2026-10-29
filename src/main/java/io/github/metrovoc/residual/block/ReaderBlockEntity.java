package io.github.metrovoc.residual.block;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.item.ShardItem;
import io.github.metrovoc.residual.learn.Reading;
import io.github.metrovoc.residual.menu.ReaderMenu;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.model.ModelLink;
import io.github.metrovoc.residual.model.Models;
import io.github.metrovoc.residual.model.Recovered;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.registry.ModComponents;
import java.util.BitSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
 * Restores an Archive Shard with a reading model. It goes through the shard's lost blocks one by one, has the model read
 * each from the text before it, and brings back every block whose characters the model gets mostly right.
 */
public class ReaderBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SHARD = 0, MODEL = 1;
    /** What its screen shows, as container data. */
    public static final int STATE = 0, BLOCK = 1, ACCURACY = 2, RESTORED = 3, PROBLEM = 4, DATA = 5;
    /** Why there is no model to read with, in the order {@link #PROBLEM} counts them. */
    public static final List<String> PROBLEMS = List.of("residual.model.none", "residual.model.blank", "residual.model.unbound",
            "residual.model.out_of_range", "residual.model.no_server", "residual.model.server_empty", "residual.reader.too_big",
            "residual.model.failed");

    public enum State { NO_SHARD, NO_MODEL, PEEKS, READING, WHOLE, STUCK }

    private final SimpleContainer items = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            request++;
            busy = false;
            tried.clear();
            ReaderBlockEntity.this.setChanged();
        }
    };
    private State state = State.NO_SHARD;
    private int problem;
    private int block = -1;
    private float accuracy = Float.NaN;
    private int restored;
    private boolean busy;
    /** Which block it waits to hear about, and since when; the answer to one it gave up on is ignored. */
    private int request;
    private int askedAt;
    private final BitSet tried = new BitSet();
    private int triedRecord = -1;
    @Nullable
    private ModelInfo triedModel;
    private int ticks;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case STATE -> state.ordinal();
                case BLOCK -> block;
                case ACCURACY -> Float.isNaN(accuracy) ? -1 : Math.round(accuracy * 1000);
                case RESTORED -> restored;
                case PROBLEM -> problem;
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

    public ReaderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.READER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ReaderBlockEntity be) {
        if (be.ticks++ % 10 == 0) {
            be.step((ServerLevel) level);
        }
    }

    private void step(ServerLevel level) {
        if (busy) {
            if (ticks - askedAt < Models.PATIENCE) {
                return;
            }
            request++;
            busy = false;
        }
        ItemStack shard = items.getItem(SHARD);
        Recovered r = ShardItem.record(shard);
        if (!(shard.getItem() instanceof ShardItem)) {
            show(State.NO_SHARD);
            return;
        }
        if (r == null) {
            shard.set(ModComponents.SHARD.get(), ShardItem.roll(level.getRandom()));
            return;
        }
        if (r.whole() || r.text() == null) {
            show(State.WHOLE);
            return;
        }
        ModelLink link = ModelLink.of(level, worldPosition, items.getItem(MODEL));
        if (link instanceof ModelLink.Missing m) {
            problem = Math.max(0, PROBLEMS.indexOf(m.problem().equals("residual.model.too_big") ? "residual.reader.too_big" : m.problem()));
            show(State.NO_MODEL);
            return;
        }
        ModelInfo info = link.info();
        if (!info.causal()) {
            show(State.PEEKS);
            return;
        }
        if (r.record() != triedRecord || !info.equals(triedModel)) {
            tried.clear();
            triedRecord = r.record();
            triedModel = info;
        }
        int b = r.lost().nextSetBit(0);
        while (b >= 0 && tried.get(b)) {
            b = r.lost().nextSetBit(b + 1);
        }
        if (b < 0) {
            show(State.STUCK);
            return;
        }
        busy = true;
        askedAt = ticks;
        int id = ++request;
        block = b;
        show(State.READING);
        String text = r.text().text();
        int which = b;
        Function<CompiledGraph, Float> read = model -> Reading.accuracy(model, text, which);
        Consumer<@Nullable Float> done = acc -> {
            if (id == request) {
                read(level, which, acc);
            }
        };
        switch (link) {
            case ModelLink.Served s -> s.server().submit(read, done);
            case ModelLink.Onboard o -> Models.submit(level.getServer(), info.id(), read, done);
            case ModelLink.Missing m -> busy = false;
        }
    }

    private void read(ServerLevel level, int which, @Nullable Float acc) {
        busy = false;
        if (isRemoved()) {
            return;
        }
        if (acc == null) {
            problem = PROBLEMS.indexOf("residual.model.failed");
            show(State.NO_MODEL);
            return;
        }
        accuracy = acc;
        tried.set(which);
        ItemStack shard = items.getItem(SHARD);
        Recovered now = ShardItem.record(shard);
        if (acc >= Reading.RESTORE && now != null && now.record() == triedRecord) {
            BitSet one = new BitSet();
            one.set(which);
            shard.set(ModComponents.SHARD.get(), now.restore(one));
            restored++;
            level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.6f, 1.6f);
            level.sendParticles(ParticleTypes.ENCHANT, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1, worldPosition.getZ() + 0.5,
                    12, 0.3, 0.2, 0.3, 0.4);
        }
        setChanged();
    }

    private void show(State s) {
        state = s;
        boolean on = s == State.READING;
        BlockState here = getBlockState();
        if (level != null && here.getValue(ReaderBlock.READING) != on) {
            level.setBlock(worldPosition, here.setValue(ReaderBlock.READING, on), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.residual.reader");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ReaderMenu(id, inventory, worldPosition, items, data);
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
        out.putInt("restored", restored);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        in.child("items").ifPresent(c -> ContainerHelper.loadAllItems(c, items.getItems()));
        restored = in.getIntOr("restored", 0);
    }
}
