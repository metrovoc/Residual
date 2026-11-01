package io.github.metrovoc.residual.block;

import io.github.metrovoc.residual.ResidualConfig;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.item.CrystalItem;
import io.github.metrovoc.residual.learn.Sampler;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.model.Models;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.world.Cooling;
import io.github.metrovoc.residual.world.Relays;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
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
 * Serves the model in its crystal to everything in reach of its relay network, a few answers a second. Accelerators
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
    /** Turns taken in the second before this one, and in this one so far. */
    private int lastSecond, thisSecond;
    private int quiet;
    private int ticks;
    private int revision;

    /** Something waiting its turn: work for the model, or with none only the turn itself. */
    private record Request<T>(@Nullable Function<CompiledGraph, T> work, Consumer<@Nullable T> reply) {
    }

    public ServerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SERVER.get(), pos, state);
    }

    @Nullable
    public ModelInfo model() {
        return CrystalItem.model(crystal);
    }

    public ItemStack crystal() {
        return crystal;
    }

    /** Puts {@code stack} in and hands back what was there. */
    public ItemStack swap(ItemStack stack) {
        ItemStack old = crystal;
        crystal = stack;
        cancelQueued();
        setChanged();
        if (level instanceof ServerLevel server) {
            BlockState s = getBlockState();
            level.setBlock(worldPosition, s.setValue(ServerBlock.LOADED, !crystal.isEmpty()), Block.UPDATE_CLIENTS);
            Relays.served(server, worldPosition, model() != null);
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

    public GlobalPos where() {
        return GlobalPos.of(level.dimension(), worldPosition);
    }

    /** How many questions wait for their turn. */
    public int waiting() {
        return queue.size();
    }

    public int answersLastSecond() {
        return lastSecond;
    }

    /**
     * Takes one turn of what it can answer, with nothing for the model to do: a call from a Terminal, a step of a jump.
     * {@code reply} gets true on the server thread when the turn comes, or false if it is turned away.
     */
    public void call(Consumer<Boolean> reply) {
        if (model() == null || queue.size() >= QUEUE) {
            reply.accept(false);
            return;
        }
        queue.add(new Request<Boolean>(null, turn -> reply.accept(turn != null)));
    }

    /** Runs {@code work} on the model when its turn comes; {@code reply} gets the result (or null) on the server thread. */
    public <T> void submit(Function<CompiledGraph, T> work, Consumer<@Nullable T> reply) {
        if (model() == null || queue.size() >= QUEUE) {
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
        if (ticks % 20 == 0) {
            lastSecond = thisSecond;
            thisSecond = 0;
        }
        credit = Math.min(rate(), credit + rate() / 20f);
        ModelInfo info = model();
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

    @SuppressWarnings("unchecked")
    private <T> void run(MinecraftServer server, ModelInfo info, Request<T> r) {
        thisSecond++;
        if (r.work() == null) {
            answered++;
            setChanged();
            r.reply().accept((T) Boolean.TRUE);
            return;
        }
        inFlight++;
        int at = revision;
        Models.submit(server, info.id(), r.work(), out -> {
            inFlight--;
            boolean current = at == revision && !isRemoved();
            if (current && out != null) {
                answered++;
                setChanged();
            }
            r.reply().accept(current ? out : null);
        });
    }

    private void cancelQueued() {
        revision++;
        var cancelled = new ArrayDeque<>(queue);
        queue.clear();
        cancelled.forEach(r -> r.reply().accept(null));
    }

    /** One line about it for the action bar. */
    public Component describe() {
        if (level instanceof ServerLevel server) {
            // count an accelerator placed a moment ago
            compute = Cooling.compute(server, worldPosition, 1, ResidualConfig.MAX_THREADS.get());
        }
        ModelInfo info = model();
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
        if (level instanceof ServerLevel server) {
            Relays.sign(server, worldPosition, Relays.Kind.SERVER, Relays.SERVER_RANGE);
            Relays.served(server, worldPosition, model() != null);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide()) {
            cancelQueued();
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide()) {
            cancelQueued();
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, crystal);
            Cooling.working(level, pos, false);
        }
        if (level instanceof ServerLevel server) {
            Relays.leave(server, pos);
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
