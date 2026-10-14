package net.parkabird.changedsynergy.mixin;
import net.minecraft.server.level.ServerPlayer;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

/** TT's toggle packet and reconnect path consult permanent identity before entering control mode. */
@Pseudo
@Mixin(targets={"com.lugert.true_transfur.network.ToggleControlModePacket","com.lugert.true_transfur.transfur.InstinctRuntime"},remap=false)
public abstract class TrueTransfurHumanInstinctEntryMixin {
    @Redirect(method={"lambda$handle$0","onLoggedIn"},at=@At(value="INVOKE",
            target="Lcom/lugert/true_transfur/transfur/TransfurController;isTrueTransfurActive(Lnet/minecraft/server/level/ServerPlayer;)Z"),require=0,remap=false)
    private static boolean changedSynergy$controlEntry(ServerPlayer player) {return TrueTransfurCompat.allowsInstinctControl(player);}
}
