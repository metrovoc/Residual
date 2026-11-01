package io.github.metrovoc.residual.agent;

import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/** Whose a body is, and who may open it, order it and change its program: anyone, its owner's team, or only its owner. */
public final class Ownership {
    public enum Access { ANYONE, TEAM, OWNER }

    @Nullable
    private UUID owner;
    private Access access = Access.ANYONE;

    @Nullable
    public UUID owner() {
        return owner;
    }

    /** It belongs to whoever put it down first. */
    public void claim(UUID player) {
        if (owner == null) {
            owner = player;
        }
    }

    public Access access() {
        return access;
    }

    /** Sets who may use it, for its owner to say, or anyone while it has no owner. */
    public void setAccess(ServerPlayer by, Access access) {
        if (owner == null || owner.equals(by.getUUID()) || by.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            this.access = access;
        }
    }

    public boolean allows(Player player) {
        if (owner == null || access == Access.ANYONE || owner.equals(player.getUUID()) || !(player instanceof ServerPlayer sp)) {
            return true;
        }
        if (sp.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            return true;
        }
        return access == Access.TEAM && Teams.together(sp.level().getServer(), owner, sp.getUUID());
    }

    /** Tells a player they may not, if so; true if they may. */
    public boolean check(Player player) {
        if (allows(player)) {
            return true;
        }
        player.sendOverlayMessage(Component.translatable("residual.owned.refused"));
        return false;
    }

    public void save(ValueOutput out) {
        if (owner != null) {
            out.store("owner", UUIDUtil.CODEC, owner);
        }
        out.putInt("access", access.ordinal());
    }

    public void load(ValueInput in) {
        owner = in.read("owner", UUIDUtil.CODEC).orElse(null);
        access = Access.values()[Math.floorMod(in.getIntOr("access", 0), Access.values().length)];
    }
}
