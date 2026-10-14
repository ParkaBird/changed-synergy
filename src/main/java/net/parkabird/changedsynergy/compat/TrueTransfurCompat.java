package net.parkabird.changedsynergy.compat;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.ltxprogrammer.changed.network.packet.SyncTransfurPacket;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.PacketDistributor;

/** Optional guard for TrueTransfur's lethal reverse-transfur interception. */
public final class TrueTransfurCompat {
    private TrueTransfurCompat() {}

    /** Physical INSTINCT bodies carry these markers in the author's runtime. */
    public static boolean isInstinctBody(ChangedEntity entity) {
        return entity.getPersistentData().getBoolean("TrueTransfurControlled")
                && entity.getPersistentData().hasUUID("TrueTransfurOwner");
    }

    /** Old TrueTransfur releases drive the player directly and have no hidden shell. */
    public static boolean isInstinctShell(Player player) {
        return player instanceof ServerPlayer server && ModList.get().isLoaded("true_transfur")
                && InstinctApi.body != null && isInstinctControlled(server);
    }

    public static boolean isInstinctControlled(ServerPlayer player) {
        if (!ModList.get().isLoaded("true_transfur") || InstinctApi.mode == null) return false;
        try {
            return Boolean.TRUE.equals(InstinctApi.mode.invoke(null, player));
        } catch (ReflectiveOperationException | LinkageError exception) { return false; }
    }

    /** Resolves the actual controllable body without changing control mode. */
    public static LivingEntity interactionBody(ServerPlayer player) {
        if (isInstinctShell(player)) {
            try {
                Object result = InstinctApi.body.invoke(null, player);
                if (result instanceof Optional<?> optional
                        && optional.orElse(null) instanceof ChangedEntity body
                        && isInstinctBody(body) && body.isAlive() && !body.isRemoved()
                        && player.getUUID().equals(body.getPersistentData().getUUID("TrueTransfurOwner"))) {
                    return body;
                }
            } catch (ReflectiveOperationException | LinkageError exception) { /* Optional API changed. */ }
        }
        return player;
    }

    @Nullable
    public static ServerPlayer bodyOwner(ChangedEntity body) {
        if (body.getUnderlyingPlayer() instanceof ServerPlayer player) return player;
        if (!isInstinctBody(body) || body.getServer() == null) return null;
        UUID ownerId = body.getPersistentData().getUUID("TrueTransfurOwner");
        ServerPlayer owner = body.getServer().getPlayerList().getPlayer(ownerId);
        return owner != null && interactionBody(owner) == body ? owner : null;
    }

    private static final class InstinctApi {
        private static final Method active = method("TransfurController", "isTrueTransfurActive");
        private static final Method mode = method("TransfurController", "isInstinctControlled");
        private static final Method body = method("InstinctRuntime", "getControlledEntity");

        private static Method method(String type, String name) {
            try {
                return Class.forName("com.lugert.true_transfur.transfur." + type)
                        .getMethod(name, ServerPlayer.class);
            } catch (ReflectiveOperationException | LinkageError exception) { return null; }
        }
    }

    public static boolean supportsSeparateInstinct() {
        return ModList.get().isLoaded("true_transfur")&&InstinctApi.body!=null&&InstinctApi.mode!=null;
    }
    /** Only control entry points use this; permanent-body and reversal guards retain their original policy. */
    public static boolean allowsInstinctControl(ServerPlayer player) {
        if(!net.parkabird.changedsynergy.origin.NativeOriginPolicy.allowsInstinct(player))return false;
        if(!net.parkabird.changedsynergy.origin.NativeOriginPolicy.allows(player))
            return ProcessTransfur.getPlayerTransfurVariantSafe(player).map(form->!form.isTemporaryFromSuit()).orElse(false);
        if(InstinctApi.active==null)return false;
        try{return Boolean.TRUE.equals(InstinctApi.active.invoke(null,player));}
        catch(ReflectiveOperationException|LinkageError exception){return false;}
    }

    /** An explicit backend identity, not the conservative reverse-transfur safety fallback. */
    public static boolean isPermanentPlayer(ServerPlayer player) {
        if (!ModList.get().isLoaded("true_transfur") || InstinctApi.active == null
                || !net.parkabird.changedsynergy.origin.NativeOriginPolicy.allows(player)) return false;
        try { return Boolean.TRUE.equals(InstinctApi.active.invoke(null, player))
                && ProcessTransfur.getPlayerTransfurVariantSafe(player)
                    .map(form -> !form.isTemporaryFromSuit()).orElse(false); }
        catch (ReflectiveOperationException | LinkageError exception) { return false; }
    }

    /** The inspected 2.0 backend persists these three non-interactive rebirth stages. */
    public static boolean isRebirthing(ServerPlayer player) {
        if (!ModList.get().isLoaded("true_transfur")
                || !net.parkabird.changedsynergy.origin.NativeOriginPolicy.allows(player)) return false;
        return switch (player.getPersistentData().getCompound("TrueTransfurReborn").getString("Phase")) {
            case "FIGHTING", "FALLING_ASLEEP", "SLEEPING" -> true;
            default -> false;
        };
    }

    public static boolean blocksReversal(ServerPlayer player) {
        if (net.parkabird.changedsynergy.origin.NativeOriginService.isNative(player)
                && net.parkabird.changedsynergy.origin.NativeOriginService.enabled(player)) return true;
        if (!net.parkabird.changedsynergy.origin.NativeOriginPolicy.allows(player)) return false;
        if (!ModList.get().isLoaded("true_transfur")) return false;
        // TrueTransfur 1.0.1 explicitly exempts temporary suit variants from
        // its lethal reverse-transfur hook, even when its player flag is set.
        if (ProcessTransfur.getPlayerTransfurVariantSafe(player)
                .map(TransfurVariantInstance::isTemporaryFromSuit)
                .orElse(false)) return false;
        try {
            Class<?> config = Class.forName(
                    "com.lugert.true_transfur.config.TrueTransfurConfig");
            Object deathSetting = config.getField("ENABLE_REVERSE_TRANSFUR_DEATH").get(null);
            if (!(deathSetting instanceof ForgeConfigSpec.BooleanValue flag))
                return ProcessTransfur.isPlayerTransfurred(player);
            if (!flag.get()) return false;
            Class<?> controller = Class.forName(
                    "com.lugert.true_transfur.transfur.TransfurController");
            return Boolean.TRUE.equals(controller
                    .getMethod("isTrueTransfurActive", ServerPlayer.class)
                    .invoke(null, player));
        } catch (ReflectiveOperationException | LinkageError exception) {
            // A changed TrueTransfur API must never turn a safe release into death.
            return ProcessTransfur.isPlayerTransfurred(player);
        }
    }

    /** Only the inspected backend API enables new irreversible origin choices. */
    public static boolean supportsNativeOrigin() {
        if (!ModList.get().isLoaded("true_transfur")) return false;
        try {
            Class<?> controller = Class.forName("com.lugert.true_transfur.transfur.TransfurController");
            controller.getMethod("isTrueTransfurActive", ServerPlayer.class);
            controller.getMethod("getControlMode", ServerPlayer.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) { return false; }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void ensurePlayerControl(ServerPlayer player) {
        if (!supportsNativeOrigin()) return;
        try {
            Class<?> controller = Class.forName("com.lugert.true_transfur.transfur.TransfurController");
            Class mode = Class.forName("com.lugert.true_transfur.transfur.ControlMode");
            if (Boolean.TRUE.equals(controller.getMethod("isInstinctControlled", ServerPlayer.class).invoke(null, player))) {
                Method changeMode;
                try { changeMode = controller.getMethod("switchControlMode", ServerPlayer.class, mode); }
                catch (NoSuchMethodException exception) {
                    changeMode = controller.getMethod("setControlMode", ServerPlayer.class, mode);
                }
                changeMode.invoke(null, player, Enum.valueOf(mode, "PLAYER"));
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            // Unknown APIs never drive the player from this optional bridge.
        }
    }

    /** TrueTransfur 1.0.1 allows temporary suits to end normally. */
    public static void clearTemporarySuit(ServerPlayer player) {
        TransfurVariantInstance<?> current = ProcessTransfur.getPlayerTransfurVariant(player);
        if (current == null || !current.isTemporaryFromSuit()) return;
        if (blocksReversal(player)) keepForm(player, current);
        else ProcessTransfur.removePlayerTransfurVariant(player);
    }

    /** Used only when no prior form can be restored safely. */
    public static void removeOrKeepForm(ServerPlayer player) {
        if (blocksReversal(player)) {
            ProcessTransfur.getPlayerTransfurVariantSafe(player)
                    .ifPresent(current -> keepForm(player, current));
        } else if (ProcessTransfur.isPlayerTransfurred(player)) {
            ProcessTransfur.removePlayerTransfurVariant(player);
        }
    }

    private static void keepForm(ServerPlayer player, TransfurVariantInstance<?> current) {
        if (!current.isTemporaryFromSuit()) return;
        current.setTemporaryForSuit(false);
        Changed.PACKET_HANDLER.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                SyncTransfurPacket.Builder.of(player));
    }
}
