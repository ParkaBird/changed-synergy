package net.parkabird.changedsynergy.client;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;

/** Resolves player skin, model slimness, and layer properties for TrueTransfur Instinct shells. */
@OnlyIn(Dist.CLIENT)
public final class InstinctPlayerSkinClient {
    private static final Map<Class<?>, Optional<Method>> MODEL_PROPERTY_SETTERS = new ConcurrentHashMap<>();

    private InstinctPlayerSkinClient() {}

    @Nullable
    public static AbstractClientPlayer resolveClientPlayer(@Nullable ChangedEntity entity) {
        if (entity == null) return null;
        if (entity.getUnderlyingPlayer() instanceof AbstractClientPlayer direct) return direct;
        UUID ownerId = TrueTransfurCompat.instinctOwnerUuid(entity);
        if (ownerId == null) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && ownerId.equals(mc.player.getUUID())) return mc.player;
        if (mc.level != null && mc.level.getPlayerByUUID(ownerId) instanceof AbstractClientPlayer remote) {
            return remote;
        }
        return null;
    }

    @Nullable
    public static ResourceLocation resolveSkinTexture(@Nullable ChangedEntity entity) {
        AbstractClientPlayer player = resolveClientPlayer(entity);
        if (player != null) return player.getSkinTextureLocation();
        UUID ownerId = TrueTransfurCompat.instinctOwnerUuid(entity);
        if (ownerId == null) return null;
        PlayerInfo info = playerInfo(ownerId);
        return info != null ? info.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin(ownerId);
    }

    @Nullable
    public static String resolveModelName(@Nullable ChangedEntity entity) {
        AbstractClientPlayer player = resolveClientPlayer(entity);
        if (player != null) return player.getModelName();
        UUID ownerId = TrueTransfurCompat.instinctOwnerUuid(entity);
        if (ownerId == null) return null;
        PlayerInfo info = playerInfo(ownerId);
        return info != null ? info.getModelName() : DefaultPlayerSkin.getSkinModelName(ownerId);
    }

    public static void applyModelPropertiesIfInstinct(Object model, ChangedEntity entity) {
        if (model == null || entity == null || entity.getUnderlyingPlayer() != null) return;
        AbstractClientPlayer player = resolveClientPlayer(entity);
        if (player == null) return;
        try {
            Optional<Method> setter = MODEL_PROPERTY_SETTERS.computeIfAbsent(model.getClass(), type -> {
                try {
                    return Optional.of(type.getMethod("setModelProperties", AbstractClientPlayer.class));
                } catch (NoSuchMethodException ignored) {
                    return Optional.empty();
                }
            });
            if (setter.isPresent()) setter.get().invoke(model, player);
        } catch (ReflectiveOperationException ignored) {
            // Retain default model properties if a modded subclass changes the signature.
        }
    }

    @Nullable
    private static PlayerInfo playerInfo(UUID uuid) {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null ? connection.getPlayerInfo(uuid) : null;
    }
}
