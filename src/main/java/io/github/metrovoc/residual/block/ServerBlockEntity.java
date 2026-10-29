package io.github.metrovoc.residual.block;

import io.github.metrovoc.residual.ResidualConfig;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.item.CrystalItem;
import io.github.metrovoc.residual.learn.Sampler;
import io.github.metrovoc.residual.mind.Checkpoints;
import io.github.metrovoc.residual.mind.MindInfo;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.train.Jobs;
import io.github.metrovoc.residual.world.Cooling;
import io.github.metrovoc.residual.world.Relays;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * Serves the mind in its crystal to everything in reach of its relay network, a few answers a second. Accelerators
 * touching it answer more at once, and run hot unless something cools them.
 */
public class ServerBlockEntity extends BlockEntity {
    /** Questions waiting beyond this many are turned away. */
    private static final int QUEUE = 64;

    private ItemStack crystal = ItemStack.EMPTY;
    private final ArrayDeque<Request<?>> queue = new ArrayDeque<>();
    private Cooling.Compute compute = new Cooling.Compute(1, 0);
    private float credit;
    private int inFlight;
    private int answered;
    private int quiet;
    private int ticks;

    private record Request<T>(Function<CompiledGraph, T> work, Consumer<@Nullable T> reply) {
    }

    public ServerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SERVER.get(), pos, state);
    }

    @Nullable
    public MindInfo mind() {
        return CrystalItem.mind(crystal);
    }

    public ItemStack crystal() {
        return crystal;
    }

    /** Puts {@code stack} in and hands back what was there. */
    public ItemStack swap(ItemStack stack) {
        ItemStack old = crystal;
        crystal = stack;
        queue.forEach(r -> r.reply().accept(null));
        queue.clear();
        setChanged();
        if (level != null) {
            BlockState s = getBlockState();
            level.setBlock(worldPosition, s.setValue(ServerBlock.LOADED, !crystal.isEmpty()), Block.UPDATE_CLIENTS);
        }
        return old;
    }

    /** Answers per second, with every accelerator it has. */
    public float rate() {
        return ResidualConfig.SERVER_ANSWERS.get() * compute.threads();
    }

    public void ask(String prompt, int max, char stop, Consumer<Sampler.@Nullable Answer> reply) {
        submit(m -> Sampler.answer(m, prompt, max, stop), reply);
    }

    /** Runs {@code work} on the mind when its turn comes; {@code reply} gets the result (or null) on the server thread. */
    public <T> void submit(Function<CompiledGraph, T> work, Consumer<@Nullable T> reply) {
        if (mind() == null || queue.size() >= QUEUE) {
            reply.accept(null);
            return;
        }
        queue.add(new Request<>(work, reply));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ServerBlockEntity be) {
        be.tick((ServerLevel) level);
    }

    private void tick(ServerLevel level) {
        if (ticks++ % 100 == 0) {
            compute = Cooling.compute(level, worldPosition, 1, ResidualConfig.MAX_THREADS.get());
            Cooling.working(level, worldPosition, quiet < 100);
        }
        credit = Math.min(rate(), credit + rate() / 20f);
        MindInfo info = mind();
        while (info != null && credit >= 1 && !queue.isEmpty() && inFlight < 2 * compute.threads()) {
            credit--;
            run(level.getServer(), info, queue.poll());
        }
        quiet = queue.isEmpty() && inFlight == 0 ? quiet + 1 : 0;
        boolean serving = quiet < 20;
        if (state().getValue(ServerBlock.SERVING) != serving) {
            level.setBlock(worldPosition, state().setValue(ServerBlock.SERVING, serving), Block.UPDATE_CLIENTS);
        }
    }

    private BlockState state() {
        return getBlockState();
    }

    private <T> void run(MinecraftServer server, MindInfo info, Request<T> r) {
        inFlight++;
        Jobs.EXECUTOR.execute(() -> {
            CompiledGraph model = Checkpoints.load(server, info.id());
            T out = model == null ? null : r.work().apply(model);
            Jobs.back(server, () -> {
                inFlight--;
                answered++;
                r.reply().accept(out);
            });
        });
    }

    /** One line about it for the action bar. */
    public Component describe() {
        if (level instanceof ServerLevel server) {
            // count an accelerator placed a moment ago
            compute = Cooling.compute(server, worldPosition, 1, ResidualConfig.MAX_THREADS.get());
        }
        MindInfo info = mind();
        if (info == null) {
            return Component.translatable("residual.server.empty");
        }
        Component line = Component.translatable("residual.server.status", info.tag(), String.format(Locale.ROOT, "%,d", info.parameters()),
                String.format(Locale.ROOT, "%.0f", rate()), queue.size(), String.format(Locale.ROOT, "%,d", answered));
        return compute.hot() == 0 ? line : line.copy().append(Component.translatable("residual.server.hot", compute.hot()));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            Relays.server(level, worldPosition, true);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide()) {
            Relays.server(level, worldPosition, false);
            queue.forEach(r -> r.reply().accept(null));
            queue.clear();
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide()) {
            Relays.server(level, worldPosition, false);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, crystal);
            Cooling.working(level, pos, false);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.store("crystal", ItemStack.OPTIONAL_CODEC, crystal);
        out.putInt("answered", answered);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        crystal = in.read("crystal", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        answered = in.getIntOr("answered", 0);
    }
}
