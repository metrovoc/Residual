package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.storage.CraftPlan;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * A plan, as the terminal draws it: the tree flattened into rows in order, each with its depth, and what it adds up to.
 * {@code started} says the job is already running.
 */
public record CraftPlanSync(int menu, ItemResource item, long amount, List<Row> rows, long crafts, int lackingKinds,
                            boolean cut, boolean started) implements CustomPacketPayload {
    /** Rows past this many are left out of what is sent. */
    public static final int MOST_ROWS = 400;

    public record Row(int depth, ItemResource kind, long needed, long stored, long crafts, long missing) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Row> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Row::depth,
                ItemResource.STREAM_CODEC, Row::kind,
                ByteBufCodecs.VAR_LONG, Row::needed,
                ByteBufCodecs.VAR_LONG, Row::stored,
                ByteBufCodecs.VAR_LONG, Row::crafts,
                ByteBufCodecs.VAR_LONG, Row::missing,
                Row::new);
    }

    public static final Type<CraftPlanSync> TYPE = new Type<>(Residual.id("craft_plan"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CraftPlanSync> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CraftPlanSync::menu,
            ItemResource.STREAM_CODEC, CraftPlanSync::item,
            ByteBufCodecs.VAR_LONG, CraftPlanSync::amount,
            Row.STREAM_CODEC.apply(ByteBufCodecs.list()), CraftPlanSync::rows,
            ByteBufCodecs.VAR_LONG, CraftPlanSync::crafts,
            ByteBufCodecs.VAR_INT, CraftPlanSync::lackingKinds,
            ByteBufCodecs.BOOL, CraftPlanSync::cut,
            ByteBufCodecs.BOOL, CraftPlanSync::started,
            CraftPlanSync::new);

    public static CraftPlanSync of(int menu, CraftPlan<ItemResource> plan, boolean started) {
        List<Row> rows = new ArrayList<>();
        flatten(plan.root, 0, rows);
        return new CraftPlanSync(menu, plan.root.kind, plan.root.needed, rows, plan.totalCrafts(), plan.missing.size(),
                plan.cut || rows.size() >= MOST_ROWS, started);
    }

    private static void flatten(CraftPlan.Step<ItemResource> step, int depth, List<Row> rows) {
        if (rows.size() >= MOST_ROWS) {
            return;
        }
        rows.add(new Row(depth, step.kind, step.needed, step.stored, step.crafts, step.missing));
        for (CraftPlan.Step<ItemResource> part : step.parts) {
            flatten(part, depth + 1, rows);
        }
    }

    @Override
    public Type<CraftPlanSync> type() {
        return TYPE;
    }
}
