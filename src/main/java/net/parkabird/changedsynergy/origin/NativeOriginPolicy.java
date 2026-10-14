package net.parkabird.changedsynergy.origin;

import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Server-derived policy also used by optional TrueTransfur presentation hooks. */
public final class NativeOriginPolicy {
    private NativeOriginPolicy() {}
    public static boolean clientSelective;
    public static Set<UUID> clientNatives = Set.of();
    public static Set<UUID> clientInstincts = Set.of();
    public static boolean allows(Player player) {
        if (player instanceof ServerPlayer server)
            return !NativeOriginService.enabled(server) || NativeOriginService.isNative(server);
        return player != null && (!clientSelective || clientNatives.contains(player.getUUID()));
    }
    /** Control permission is independent of permanent identity and reversal/rebirth permission. */
    public static boolean allowsInstinct(Player player) {
        if(player instanceof ServerPlayer server) {
            var identity=NativeOriginService.identity(server);
            return allows(server)||net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.allowHumanOriginInstinct.get()
                    &&identity!=null&&identity.state.equals("human")
                    &&net.parkabird.changedsynergy.compat.TrueTransfurCompat.supportsSeparateInstinct();
        }
        return player!=null&&(!clientSelective||clientInstincts.contains(player.getUUID()));
    }
}
