package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.block.OperatorBlock;
import io.github.metrovoc.residual.graph.Op;
import net.minecraft.world.item.BlockItem;

/** A machine as an item. */
public class MachineItem extends BlockItem {
    private final OperatorBlock block;

    public MachineItem(OperatorBlock block, Properties properties) {
        super(block, properties);
        this.block = block;
    }

    public Op op() {
        return block.op;
    }
}
