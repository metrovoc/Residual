package io.github.metrovoc.residual.block;

import io.github.metrovoc.residual.graph.Config;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.menu.OperatorMenu;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.train.Jobs;
import io.github.metrovoc.residual.view.Heatmap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * Holds a machine's settings and learned weights. Weights stay on the server; clients get the settings, the last
 * check's findings and two small pictures: the machine's output and its main weight matrix.
 */
public class OperatorBlockEntity extends BlockEntity implements MenuProvider {
    public static final int NAME_LENGTH = 24;

    /** What the last check of the model found out about this machine. */
    public record Status(String shape, String error, int features, int vocab, int context, long parameters,
                         float gradNorm, boolean active) {
        public static final Status NONE = new Status("", "", -1, 0, 0, 0, -1, false);

        void write(ValueOutput out) {
            out.putString("shape", shape);
            out.putString("error", error);
            out.putInt("features", features);
            out.putInt("vocab", vocab);
            out.putInt("context", context);
            out.putLong("parameters", parameters);
            out.putFloat("grad_norm", gradNorm);
            out.putBoolean("active", active);
        }

        static Status read(ValueInput in) {
            return new Status(in.getStringOr("shape", ""), in.getStringOr("error", ""), in.getIntOr("features", -1),
                    in.getIntOr("vocab", 0), in.getIntOr("context", 0), in.getLongOr("parameters", 0),
                    in.getFloatOr("grad_norm", -1), in.getBooleanOr("active", false));
        }

        public Status withGradNorm(float norm) {
            return new Status(shape, error, features, vocab, context, parameters, norm, active);
        }
    }

    private final Config config;
    private String name = "";
    private Map<String, Tensor> params = new LinkedHashMap<>();
    @Nullable
    private Heatmap output;
    @Nullable
    private Heatmap weights;
    private Status status = Status.NONE;
    /** Built by a case rather than crafted: breaking it gives nothing back. */
    private boolean provided;

    public OperatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OPERATOR.get(), pos, state);
        this.config = new Config(((OperatorBlock) state.getBlock()).op);
    }

    public Op op() {
        return config.op();
    }

    public Config config() {
        return config;
    }

    public String name() {
        return name;
    }

    public Status status() {
        return status;
    }

    @Nullable
    public Heatmap output() {
        return output;
    }

    @Nullable
    public Heatmap weights() {
        return weights;
    }

    public Map<String, Tensor> params() {
        return params;
    }

    public boolean provided() {
        return provided;
    }

    public void markProvided() {
        provided = true;
        changed(true);
    }

    // ---------------------------------------------------------------- server-side changes

    public boolean configure(String key, String value) {
        if (!config.set(key, value)) {
            return false;
        }
        changed(true);
        if (level instanceof ServerLevel server) {
            Jobs.changed(server, worldPosition);
        }
        return true;
    }

    public void rename(String newName) {
        String clean = newName.strip();
        if (clean.length() > NAME_LENGTH) {
            clean = clean.substring(0, NAME_LENGTH);
        }
        if (!clean.equals(name)) {
            name = clean;
            changed(true);
        }
    }

    /** Stores trained weights (they are not sent to clients). */
    public void commit(Map<String, Tensor> trained) {
        params = trained;
        setChanged();
    }

    public void show(@Nullable Heatmap newOutput, @Nullable Heatmap newWeights, float gradNorm) {
        output = newOutput;
        weights = newWeights;
        status = status.withGradNorm(gradNorm);
        changed(true);
    }

    public void setStatus(Status newStatus) {
        if (!newStatus.equals(status)) {
            status = newStatus;
            changed(true);
        }
    }

    /** Forgets everything learned; the next training run starts from fresh random weights. */
    public void resetWeights() {
        params = new LinkedHashMap<>();
        output = null;
        weights = null;
        status = status.withGradNorm(-1);
        changed(true);
    }

    private void changed(boolean sync) {
        setChanged();
        if (sync && level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ---------------------------------------------------------------- persistence and sync

    private void writeView(ValueOutput out) {
        ValueOutput c = out.child("config");
        config.asMap().forEach(c::putString);
        out.putString("name", name);
        if (output != null) {
            output.write(out.child("output"));
        }
        if (weights != null) {
            weights.write(out.child("weights"));
        }
        status.write(out.child("status"));
        out.putBoolean("provided", provided);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        writeView(out);
        ValueOutput.ValueOutputList list = out.childrenList("params");
        for (Map.Entry<String, Tensor> e : params.entrySet()) {
            ValueOutput p = list.addChild();
            p.putString("name", e.getKey());
            p.putIntArray("shape", e.getValue().shape);
            float[] data = e.getValue().data;
            int[] bits = new int[data.length];
            for (int i = 0; i < bits.length; i++) {
                bits[i] = Float.floatToRawIntBits(data[i]);
            }
            p.putIntArray("data", bits);
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        in.child("config").ifPresent(c -> {
            for (String key : config.asMap().keySet()) {
                c.getString(key).ifPresent(v -> config.set(key, v));
            }
        });
        name = in.getStringOr("name", "");
        output = in.child("output").map(Heatmap::read).orElse(null);
        weights = in.child("weights").map(Heatmap::read).orElse(null);
        status = in.child("status").map(Status::read).orElse(Status.NONE);
        provided = in.getBooleanOr("provided", false);
        // update packets carry no weights; only a full load replaces them
        in.childrenList("params").ifPresent(list -> {
            Map<String, Tensor> loaded = new LinkedHashMap<>();
            list.stream().forEach(p -> {
                int[] shape = p.getIntArray("shape").orElse(new int[0]);
                int[] bits = p.getIntArray("data").orElse(new int[0]);
                float[] data = new float[bits.length];
                for (int i = 0; i < bits.length; i++) {
                    data[i] = Float.intBitsToFloat(bits[i]);
                }
                Tensor t = Tensor.of(shape, data);
                if (t.size() == data.length) {
                    loaded.put(p.getStringOr("name", ""), t);
                }
            });
            params = loaded;
        });
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        writeView(out);
        return out.buildResult();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ---------------------------------------------------------------- menu

    @Override
    public Component getDisplayName() {
        return name.isEmpty() ? getBlockState().getBlock().getName() : Component.literal(name);
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new OperatorMenu(id, worldPosition);
    }
}
