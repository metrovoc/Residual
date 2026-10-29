package io.github.metrovoc.residual.mind;

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
public sealed interface MindLink {
    /** A mind run on board, from a crystal in the thinker's own mind slot. */
    record Onboard(MindInfo info) implements MindLink {
    }

    /** A mind served by a Model Server in relay range. */
    record Served(ServerBlockEntity server, MindInfo info) implements MindLink {
    }

    /** Why there is no mind to ask right now: a translation key and its arguments. */
    record Missing(String problem, List<String> args) implements MindLink {
        Missing(String problem, String... args) {
            this(problem, List.of(args));
        }
    }

    /** What the mind in {@code slot} is, for something thinking at {@code where}. */
    static MindLink of(ServerLevel level, BlockPos where, ItemStack slot) {
        MindInfo info = CrystalItem.mind(slot);
        if (info != null) {
            long limit = ResidualConfig.ONBOARD_PARAMETERS.get();
            return info.parameters() > limit ? new Missing("residual.mind.too_big")
                    : new Onboard(info);
        }
        if (slot.getItem() instanceof LinkItem) {
            GlobalPos link = slot.get(ModComponents.LINK.get());
            if (link == null) {
                return new Missing("residual.mind.unbound");
            }
            if (!link.dimension().equals(level.dimension()) || !Relays.covers(level, link.pos(), where)) {
                return new Missing("residual.mind.out_of_range");
            }
            // a server answers from its block tick, so one out of the simulation distance would never answer
            if (!level.shouldTickBlocksAt(link.pos()) || !(level.getBlockEntity(link.pos()) instanceof ServerBlockEntity server)) {
                return new Missing("residual.mind.no_server");
            }
            MindInfo served = server.mind();
            return served == null ? new Missing("residual.mind.server_empty") : new Served(server, served);
        }
        return slot.isEmpty() ? new Missing("residual.mind.none") : new Missing("residual.mind.blank");
    }

    /** The mind's description, or null if there is none. */
    @Nullable
    default MindInfo info() {
        return switch (this) {
            case Onboard o -> o.info();
            case Served s -> s.info();
            case Missing m -> null;
        };
    }

    /**
     * Asks for the most likely continuation of {@code prompt}, up to {@code stop} or {@code max} characters.
     * {@code reply} runs on the server thread, with null if the mind could not answer.
     */
    default void ask(ServerLevel level, String prompt, int max, char stop, Consumer<Sampler.@Nullable Answer> reply) {
        switch (this) {
            case Onboard o -> Minds.ask(level.getServer(), o.info().id(), prompt, max, stop, reply);
            case Served s -> s.server().ask(prompt, max, stop, reply);
            case Missing m -> reply.accept(null);
        }
    }
}
