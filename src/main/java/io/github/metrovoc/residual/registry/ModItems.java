package io.github.metrovoc.residual.registry;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.item.DriveItem;
import io.github.metrovoc.residual.item.FieldLogItem;
import io.github.metrovoc.residual.item.ModelDriveItem;
import io.github.metrovoc.residual.item.ShardItem;
import io.github.metrovoc.residual.item.WayfinderItem;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Residual.MOD_ID);

    public static final DeferredItem<BlockItem> CORE = ITEMS.registerSimpleBlockItem(ModBlocks.CORE);
    public static final DeferredItem<ModelDriveItem> MODEL_DRIVE = ITEMS.registerItem("model_drive", ModelDriveItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<DriveItem> ARCHIVE_DRIVE = ITEMS.registerItem("archive_drive", DriveItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<FieldLogItem> FIELD_LOG = ITEMS.registerItem("field_log", FieldLogItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<WayfinderItem> WAYFINDER = ITEMS.registerItem("wayfinder", WayfinderItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<ShardItem> SHARD = ITEMS.registerItem("archive_shard", ShardItem::new, p -> p.stacksTo(1));

    private ModItems() {
    }
}
