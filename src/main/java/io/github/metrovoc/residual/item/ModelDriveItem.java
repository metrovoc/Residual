package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.interior.Summary;
import io.github.metrovoc.residual.registry.ModComponents;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.Nullable;

/** A drive that holds one model. It is blank until a Model Core first takes it, and names its model from then on. */
public class ModelDriveItem extends Item {
    public ModelDriveItem(Properties properties) {
        super(properties);
    }

    /** The model on a drive, or null if it is blank. */
    @Nullable
    public static UUID model(ItemStack stack) {
        return stack.get(ModComponents.MODEL.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        if (model(stack) == null) {
            out.accept(Component.translatable("residual.model_drive.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        Summary s = stack.get(ModComponents.MODEL_SUMMARY.get());
        if (s == null) {
            out.accept(Component.translatable("residual.model_drive.new").withStyle(ChatFormatting.GRAY));
            return;
        }
        out.accept(Component.translatable("residual.inside.task", Component.translatable("residual.stage." + s.task())).withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable("residual.model_drive.parameters", String.format(Locale.ROOT, "%,d", s.parameters())).withStyle(ChatFormatting.GRAY));
        if (!Float.isNaN(s.score())) {
            out.accept(Component.translatable("residual.model_drive.score", Math.round(s.score() * 100)).withStyle(ChatFormatting.DARK_GRAY));
        } else if (!Float.isNaN(s.loss())) {
            out.accept(Component.translatable("residual.model_drive.loss", String.format(Locale.ROOT, "%.2f", s.loss())).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
