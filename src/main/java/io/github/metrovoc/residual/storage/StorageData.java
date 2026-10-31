package io.github.metrovoc.residual.storage;

import com.mojang.serialization.Codec;
import io.github.metrovoc.residual.Residual;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Every Storage Drive's contents, by the drive's id, kept with the world rather than on the items. */
public final class StorageData extends SavedData {
    private static final Codec<StorageData> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Drive.CODEC)
            .xmap(StorageData::new, d -> d.drives);
    public static final SavedDataType<StorageData> TYPE = new SavedDataType<>(Residual.id("storage"), StorageData::new, CODEC);

    private final Map<UUID, Drive> drives = new HashMap<>();

    private StorageData() {
    }

    private StorageData(Map<UUID, Drive> saved) {
        saved.forEach((id, drive) -> drives.put(id, watched(drive)));
    }

    public static StorageData of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** The drive with this id, made empty the first time it is asked for. */
    public Drive drive(UUID id, long capacity) {
        return drives.computeIfAbsent(id, k -> {
            setDirty();
            return watched(new Drive(capacity));
        });
    }

    private Drive watched(Drive drive) {
        drive.changed = this::setDirty;
        return drive;
    }
}
