package net.parkabird.changedsynergy.ai;

import java.util.Optional;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class HuntMemory {
    private static final String ROOT = "ChangedSynergyHunt";
    private static final String STATE = "State";
    private static final String TARGET = "Target";
    private static final String POS_X = "LastX";
    private static final String POS_Y = "LastY";
    private static final String POS_Z = "LastZ";
    private static final String HAS_POSITION = "HasPosition";
    private static final String CONFIRMED = "ConfirmedTarget";
    private static final String UNTIL = "SearchUntil";
    private static final String LOST_SIGHT = "LostSightTicks";

    private HuntMemory() {
    }

    public static HuntState getState(ChangedEntity entity) {
        String value = data(entity).getString(STATE);
        if (value.isBlank()) {
            return HuntState.IDLE;
        }
        try {
            return HuntState.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return HuntState.IDLE;
        }
    }

    public static void setState(ChangedEntity entity, HuntState state) {
        data(entity).putString(STATE, state.name());
    }

    public static void seeTarget(ChangedEntity entity, ServerPlayer player) {
        CompoundTag data = data(entity);
        data.putString(STATE, HuntState.CHASING.name());
        data.putUUID(TARGET, player.getUUID());
        putPosition(data, player.position());
        data.putBoolean(CONFIRMED, true);
        data.putInt(LOST_SIGHT, 0);
        data.remove(UNTIL);
    }

    public static void beginSearch(ChangedEntity entity, long until) {
        CompoundTag data = data(entity);
        if (!data.getBoolean(HAS_POSITION)) {
            return;
        }
        data.putString(STATE, HuntState.SEARCHING.name());
        data.putBoolean(CONFIRMED, true);
        data.putLong(UNTIL, until);
        data.putInt(LOST_SIGHT, 0);
    }

    public static void investigate(ChangedEntity entity, ServerPlayer player, Vec3 position, long until) {
        investigate(entity, player, position, until, false);
    }

    /** Stores a sighting relayed by an ally rather than heard but unverified noise. */
    public static void investigateConfirmed(
            ChangedEntity entity,
            ServerPlayer player,
            Vec3 position,
            long until) {
        investigate(entity, player, position, until, true);
    }

    private static void investigate(
            ChangedEntity entity,
            ServerPlayer player,
            Vec3 position,
            long until,
            boolean confirmed) {
        CompoundTag data = data(entity);
        data.putString(STATE, HuntState.INVESTIGATING.name());
        data.putUUID(TARGET, player.getUUID());
        putPosition(data, position);
        data.putBoolean(CONFIRMED, confirmed);
        data.putLong(UNTIL, until);
        data.putInt(LOST_SIGHT, 0);
    }

    public static Optional<UUID> getTargetId(ChangedEntity entity) {
        CompoundTag data = data(entity);
        return data.hasUUID(TARGET) ? Optional.of(data.getUUID(TARGET)) : Optional.empty();
    }

    public static Optional<ServerPlayer> getTargetPlayer(ChangedEntity entity, ServerLevel level) {
        return getTargetId(entity)
                .map(level::getPlayerByUUID)
                .filter(ServerPlayer.class::isInstance)
                .map(ServerPlayer.class::cast);
    }

    public static boolean targets(ChangedEntity entity, ServerPlayer player) {
        return getTargetId(entity).map(player.getUUID()::equals).orElse(false);
    }

    public static Optional<Vec3> getPosition(ChangedEntity entity) {
        CompoundTag data = data(entity);
        if (!data.getBoolean(HAS_POSITION)) {
            return Optional.empty();
        }
        return Optional.of(new Vec3(data.getDouble(POS_X), data.getDouble(POS_Y), data.getDouble(POS_Z)));
    }

    public static boolean wasConfirmed(ChangedEntity entity) {
        return data(entity).getBoolean(CONFIRMED);
    }

    public static long getUntil(ChangedEntity entity) {
        return data(entity).getLong(UNTIL);
    }

    public static int getLostSightTicks(ChangedEntity entity) {
        return data(entity).getInt(LOST_SIGHT);
    }

    public static void setLostSightTicks(ChangedEntity entity, int ticks) {
        data(entity).putInt(LOST_SIGHT, Math.max(0, ticks));
    }

    public static void clear(ChangedEntity entity) {
        CompoundTag data = data(entity);
        data.putString(STATE, HuntState.IDLE.name());
        data.remove(TARGET);
        data.remove(POS_X);
        data.remove(POS_Y);
        data.remove(POS_Z);
        data.remove(HAS_POSITION);
        data.remove(CONFIRMED);
        data.remove(UNTIL);
        data.remove(LOST_SIGHT);
    }

    private static void putPosition(CompoundTag data, Vec3 position) {
        data.putDouble(POS_X, position.x);
        data.putDouble(POS_Y, position.y);
        data.putDouble(POS_Z, position.z);
        data.putBoolean(HAS_POSITION, true);
    }

    private static CompoundTag data(ChangedEntity entity) {
        CompoundTag persistent = entity.getPersistentData();
        if (!persistent.contains(ROOT, Tag.TAG_COMPOUND)) {
            persistent.put(ROOT, new CompoundTag());
        }
        return persistent.getCompound(ROOT);
    }
}
