package io.github.metrovoc.residual.model;

import io.github.metrovoc.residual.ResidualConfig;
import io.github.metrovoc.residual.block.ServerBlockEntity;
import io.github.metrovoc.residual.item.CrystalItem;
import io.github.metrovoc.residual.item.LinkItem;
import io.github.metrovoc.residual.learn.Sampler;
import io.github.metrovoc.residual.registry.ModComponents;
import io.github.metrovoc.residual.world.Relays;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Where something that thinks gets its answers: a crystal of its own, small enough to run on board, or a Model
 * Server that a Relay Card or Terminal reaches through the relay network.
 */
public sealed interface ModelLink {
    /** A model run on board, from a crystal in the thinker's own model slot. */
    record Onboard(ModelInfo info) implements ModelLink {
    }

    /** A model served by a Model Server in relay range. */
    record Served(ServerBlockEntity server, ModelInfo info) implements ModelLink {
    }

    /** Why there is no model to ask right now: a translation key and its arguments. */
    record Missing(String problem, List<String> args) implements ModelLink {
        Missing(String problem, String... args) {
            this(problem, List.of(args));
        }
    }

    /** What the model in {@code slot} is, for something thinking at {@code where}. */
    static ModelLink of(ServerLevel level, BlockPos where, ItemStack slot) {
        ModelInfo info = CrystalItem.model(slot);
        if (info != null) {
            long limit = ResidualConfig.ONBOARD_PARAMETERS.get();
            return info.parameters() > limit ? new Missing("residual.model.too_big")
                    : new Onboard(info);
        }
        if (slot.getItem() instanceof LinkItem) {
            GlobalPos link = slot.get(ModComponents.LINK.get());
            if (link == null) {
                return new Missing("residual.model.unbound");
            }
            if (!link.dimension().equals(level.dimension()) || !Relays.covers(level, link.pos(), where)) {
                return new Missing("residual.model.out_of_range");
            }
            // a server answers from its block tick, so one out of the simulation distance would never answer
            if (!level.shouldTickBlocksAt(link.pos()) || !(level.getBlockEntity(link.pos()) instanceof ServerBlockEntity server)) {
                return new Missing("residual.model.no_server");
            }
            ModelInfo served = server.model();
            return served == null ? new Missing("residual.model.server_empty") : new Served(server, served);
        }
        return slot.isEmpty() ? new Missing("residual.model.none") : new Missing("residual.model.blank");
    }

    /** The model's description, or null if there is none. */
    @Nullable
    default ModelInfo info() {
        return switch (this) {
            case Onboard o -> o.info();
            case Served s -> s.info();
            case Missing m -> null;
        };
    }

    /**
     * Asks for the most likely continuation of {@code prompt}, up to {@code stop} or {@code max} characters.
     * {@code reply} runs on the server thread, with null if the model could not answer.
     */
    default void ask(ServerLevel level, String prompt, int max, char stop, Consumer<Sampler.@Nullable Answer> reply) {
        switch (this) {
            case Onboard o -> Models.ask(level.getServer(), o.info().id(), prompt, max, stop, reply);
            case Served s -> s.server().ask(prompt, max, stop, reply);
            case Missing m -> reply.accept(null);
        }
    }
}
