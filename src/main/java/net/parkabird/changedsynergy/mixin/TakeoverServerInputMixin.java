package net.parkabird.changedsynergy.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.parkabird.changedsynergy.ai.TakeoverService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Server-side authority for gameplay packets while the carrier owns movement. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class TakeoverServerInputMixin {
    // Direct bytecode field access is remapped by reobfJar. This project uses
    // explicit Mojmap/SRG selectors rather than a generated Mixin refmap.
    private ServerPlayer changedSynergy$player() {
        return ((ServerGamePacketListenerImpl)(Object)this).player;
    }

    private boolean changedSynergy$locked() {
        // Packet handlers first enter on Netty and are reinvoked after
        // ensureRunningOnSameThread. Never read the session map off-thread.
        ServerPlayer player = changedSynergy$player();
        return player.server.isSameThread() && TakeoverService.blocksPlayerInput(player);
    }

    @Inject(method = {
            "handleContainerClick", "m_5914_",
            "handleSetCreativeModeSlot", "m_5964_",
            "handlePlayerAction", "m_7502_",
            "handleUseItem", "m_5760_",
            "handleInteract", "m_6946_",
            "handleSetCarriedItem"
    }, at = @At("HEAD"), cancellable = true, remap = false)
    private void changedSynergy$blockActions(CallbackInfo callback) {
        ServerPlayer player = changedSynergy$player();
        if (player.server.isSameThread() && TakeoverService.active(player)) callback.cancel();
    }

    @Inject(method = {"handleMovePlayer", "m_7185_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void changedSynergy$blockMovement(CallbackInfo callback) {
        if (changedSynergy$locked()) callback.cancel();
    }
}
