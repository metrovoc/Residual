package io.github.metrovoc.residual.storage;

import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.agent.Things;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Retrieval;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.model.ModelLink;
import io.github.metrovoc.residual.net.StorageMeaningSync;
import io.github.metrovoc.residual.net.StorageSync;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * A search by meaning: the model in a terminal's search slot places the query in its space of uses, as the Retrieval test asks,
 * and each thing the network keeps too, a batch at a time off the server thread, keeping where each thing goes so it is placed
 * once. The things that agree with the query as far as {@link Retrieval#SURE} come back to the screen, best first. With no model
 * that has passed Retrieval, the screen goes by the words of the query alone.
 */
final class Meaning {
    /** Things placed in one go: one request to a Model Server. */
    private static final int BATCH = 32;
    /** Models whose places are kept, the last asked most recently. */
    private static final int MODELS = 8;
    /** Where each model has placed each thing, by the thing's name. */
    private static final Map<UUID, Map<String, float[]>> PLACED = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Map<String, float[]>> eldest) {
            return size() > MODELS;
        }
    };

    private final ServerPlayer player;
    private final int menu;
    private final String query;
    private final List<ItemResource> kinds;
    private final ModelLink link;
    private int asked;
    private boolean stopped;
    private Map<String, float[]> placed = Map.of();
    private float[] wanted = new float[0];

    /** Starts asking, or says why it can't. */
    static Meaning start(ServerPlayer player, int menu, String query, List<ItemResource> kinds, ModelLink link) {
        Meaning meaning = new Meaning(player, menu, query, kinds, link);
        ModelInfo info = link.info();
        if (info == null) {
            meaning.send(List.of(), link instanceof ModelLink.Missing m ? m.problem() : "residual.search.no_model");
        } else if (!info.passes(Benchmark.RETRIEVAL)) {
            meaning.send(List.of(), "residual.search.not_passed");
        } else {
            meaning.placed = PLACED.computeIfAbsent(info.id(), id -> new HashMap<>());
            meaning.placeQuery();
        }
        return meaning;
    }

    private Meaning(ServerPlayer player, int menu, String query, List<ItemResource> kinds, ModelLink link) {
        this.player = player;
        this.menu = menu;
        this.query = query;
        this.kinds = kinds;
        this.link = link;
    }

    /** The screen moved on: an answer on its way is dropped. */
    void stop() {
        stopped = true;
    }

    private void placeQuery() {
        link.submit((ServerLevel) player.level(), model -> Retrieval.place(model, query), at -> {
            if (!stopped && at == null) {
                send(List.of(), "residual.model.failed");
            } else if (!stopped) {
                wanted = at;
                next();
            }
        });
    }

    /** What is placed already fits or not at once; then the next batch of what is not. */
    private void next() {
        List<StorageMeaningSync.Hit> hits = new ArrayList<>();
        List<ItemResource> batch = new ArrayList<>();
        int looked = asked;
        while (looked < kinds.size() && batch.size() < BATCH) {
            ItemResource kind = kinds.get(looked++);
            float[] at = placed.get(name(kind));
            if (at != null) {
                note(hits, kind, at);
            } else {
                batch.add(kind);
            }
        }
        asked = looked - batch.size();
        send(hits, "");
        if (batch.isEmpty()) {
            return;
        }
        List<String> names = batch.stream().map(Meaning::name).toList();
        link.submit((ServerLevel) player.level(), model -> {
            float[][] at = new float[names.size()][];
            for (int i = 0; i < at.length; i++) {
                at[i] = Retrieval.place(model, names.get(i));
            }
            return at;
        }, at -> {
            if (stopped) {
                return;
            }
            if (at == null) {
                send(List.of(), "residual.model.failed");
                return;
            }
            List<StorageMeaningSync.Hit> found = new ArrayList<>();
            for (int i = 0; i < at.length; i++) {
                placed.put(names.get(i), at[i]);
                note(found, batch.get(i), at[i]);
            }
            asked += at.length;
            send(found, "");
            next();
        });
    }

    private static String name(ItemResource kind) {
        return Things.name(kind.toStack());
    }

    /** A thing agreeing with the query as far as it must comes back with how far. */
    private void note(List<StorageMeaningSync.Hit> hits, ItemResource kind, float[] at) {
        float fit = Retrieval.fit(wanted, at);
        if (fit >= Retrieval.SURE) {
            hits.add(new StorageMeaningSync.Hit(kind, fit));
            Awards.grant(player, "storage/search");
        }
    }

    /** What it found comes in pieces no bigger than a packet may be, however many things fit. */
    private void send(List<StorageMeaningSync.Hit> hits, String problem) {
        int at = 0;
        do {
            List<StorageMeaningSync.Hit> some = List.copyOf(hits.subList(at, Math.min(hits.size(), at + StorageSync.MOST)));
            at += some.size();
            PacketDistributor.sendToPlayer(player, new StorageMeaningSync(menu, query, some, asked, kinds.size(), problem));
        } while (at < hits.size());
    }
}
