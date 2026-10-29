package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.agent.Agent;
import io.github.metrovoc.residual.agent.Program;
import io.github.metrovoc.residual.registry.ModComponents;
import io.github.metrovoc.residual.registry.ModItems;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A program on a card, carried from agent to agent. Use a written card on an agent to hand it the program; use a blank
 * one, or sneak-use a written one, to copy the agent's program onto it. In an agent's screen, the program box reads a
 * card clicked on it, or writes onto it.
 */
public class ProgramCardItem extends Item {
    /** How wide its program is written in the tooltip, in characters. */
    private static final int LINE = 32;

    public ProgramCardItem(Properties properties) {
        super(properties);
    }

    /** The program on {@code stack}, or empty for a blank card. */
    public static String program(ItemStack stack) {
        return stack.getOrDefault(ModComponents.PROGRAM.get(), "");
    }

    /**
     * Writes {@code program} on the card {@code player} holds in a screen, if it is a card and the program reads. Of a
     * stack, the card written on stays held and the rest go back into the inventory.
     */
    public static void write(Player player, ItemStack held, String program) {
        if (!held.is(ModItems.PROGRAM_CARD.get()) || !Program.parse(program).ok()) {
            return;
        }
        ItemStack rest = held.split(held.getCount() - 1);
        held.set(ModComponents.PROGRAM.get(), program);
        if (!rest.isEmpty() && !player.getInventory().add(rest)) {
            player.drop(rest, false);
        }
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Agent a)) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        String written = program(stack);
        if (written.isEmpty() || player.isSecondaryUseActive()) {
            if (a.program().isEmpty()) {
                player.sendOverlayMessage(Component.translatable("residual.card.nothing", a.getDisplayName()));
                return InteractionResult.FAIL;
            }
            // one card of a stack is written on and the rest stay as they were; in creative, where what is held only
            // stands for itself, a new card is written
            ItemStack card = stack.getCount() > 1 || player.hasInfiniteMaterials() ? stack.split(1) : stack;
            card.set(ModComponents.PROGRAM.get(), a.program());
            if (card != stack && !player.getInventory().add(card)) {
                player.drop(card, false);
            }
            player.sendOverlayMessage(Component.translatable("residual.card.copied", a.getDisplayName()));
            player.level().playSound(null, a.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1.2f);
            return InteractionResult.SUCCESS;
        }
        if (a.setProgram(written, player) != null) {
            player.sendOverlayMessage(Component.translatable("residual.card.unreadable"));
            return InteractionResult.FAIL;
        }
        player.sendOverlayMessage(Component.translatable("residual.card.handed", a.getDisplayName()));
        player.level().playSound(null, a.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.2f);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        String written = program(stack);
        if (written.isEmpty()) {
            out.accept(Component.translatable("residual.card.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        StringBuilder line = new StringBuilder();
        for (String word : written.strip().split("\\s+")) {
            if (!line.isEmpty() && line.length() + 1 + word.length() > LINE) {
                out.accept(Component.literal(line.toString()).withStyle(ChatFormatting.GRAY));
                line.setLength(0);
            }
            line.append(line.isEmpty() ? "" : " ").append(word);
        }
        out.accept(Component.literal(line.toString()).withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable("residual.card.help").withStyle(ChatFormatting.DARK_GRAY));
    }
}
