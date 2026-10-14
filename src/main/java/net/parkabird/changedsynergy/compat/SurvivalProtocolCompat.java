package net.parkabird.changedsynergy.compat;

import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.TakeoverService;

/** Preserve CSP's ordinary infection rules outside an explicitly managed Synergy operation. */
public final class SurvivalProtocolCompat {
    private static final ThreadLocal<java.util.UUID> PROGRESS = new ThreadLocal<>();
    private static final ThreadLocal<java.util.UUID> ORIGIN = new ThreadLocal<>();
    private SurvivalProtocolCompat() {}

    public static boolean nativeProgress(ServerPlayer player) {
        return player.getUUID().equals(PROGRESS.get()) || TakeoverService.active(player);
    }
    public static boolean originChange(ServerPlayer player) {
        return player.getUUID().equals(ORIGIN.get());
    }
    public static void withNativeProgress(ServerPlayer player, Runnable operation) {
        java.util.UUID previous = PROGRESS.get();
        PROGRESS.set(player.getUUID());
        try { operation.run(); }
        finally { if (previous == null) PROGRESS.remove(); else PROGRESS.set(previous); }
    }

    /** Explicit re-selection replaces CSP's previous settled strain, with rollback on rejection. */
    public static boolean changeOrigin(ServerPlayer player, String form, BooleanSupplier operation) {
        if (!ModList.get().isLoaded("changed_survive_protocol")) return operation.getAsBoolean();
        if (Api.get == null || Api.save == null || Api.load == null || Api.reset == null
                || Api.strain == null || Api.settled == null || Api.stabilized == null || Api.sync == null) {
            ChangedSynergyMod.LOGGER.warn("Survival Protocol origin API is unavailable; keeping the previous identity");
            return false;
        }
        Object data;
        CompoundTag saved;
        try {
            data = ((LazyOptional<?>)Api.get.invoke(null, player)).orElse(null);
            if (data == null) return operation.getAsBoolean();
            saved = ((CompoundTag)Api.save.invoke(data)).copy();
            Api.reset.invoke(data);
        } catch (ReflectiveOperationException | LinkageError exception) {
            ChangedSynergyMod.LOGGER.warn("Cannot prepare Survival Protocol origin state", exception);
            return false;
        }
        java.util.UUID previous = ORIGIN.get();
        ORIGIN.set(player.getUUID());
        boolean success = false;
        try {
            success = operation.getAsBoolean();
            if (success && !form.isEmpty()) {
                Api.strain.invoke(data, form);
                Api.settled.invoke(data, form);
                Api.stabilized.invoke(data, true);
            }
            return success;
        } catch (ReflectiveOperationException exception) {
            success = false;
            ChangedSynergyMod.LOGGER.warn("Cannot commit Survival Protocol origin state", exception);
            return false;
        } finally {
            if (previous == null) ORIGIN.remove(); else ORIGIN.set(previous);
            try {
                if (!success) Api.load.invoke(data, saved);
                Api.sync.invoke(null, player, data);
            } catch (ReflectiveOperationException exception) {
                ChangedSynergyMod.LOGGER.warn("Cannot synchronize Survival Protocol origin state", exception);
            }
        }
    }

    private static final class Api {
        static final Method get, save, load, reset, strain, settled, stabilized, sync;
        static {
            Method getter=null, saver=null, loader=null, resetter=null, strainSetter=null, settledSetter=null, stableSetter=null, sender=null;
            try {
                Class<?> data=Class.forName("net.stonenibbler.changed_survive_protocol.common.data.CSPPlayerData");
                getter=Class.forName("net.stonenibbler.changed_survive_protocol.common.data.CSPCapabilities").getMethod("get",Player.class);
                saver=data.getMethod("save");loader=data.getMethod("load",CompoundTag.class);resetter=data.getMethod("reset");
                strainSetter=data.getMethod("setStrainId",String.class);settledSetter=data.getMethod("setSettledStrainId",String.class);
                stableSetter=data.getMethod("setStabilizedLatex",boolean.class);
                sender=Class.forName("net.stonenibbler.changed_survive_protocol.common.network.CSPNetwork").getMethod("sync",ServerPlayer.class,data);
            } catch (ReflectiveOperationException | LinkageError exception) { /* Optional inspected API unavailable. */ }
            get=getter;save=saver;load=loader;reset=resetter;strain=strainSetter;settled=settledSetter;stabilized=stableSetter;sync=sender;
        }
    }
}
