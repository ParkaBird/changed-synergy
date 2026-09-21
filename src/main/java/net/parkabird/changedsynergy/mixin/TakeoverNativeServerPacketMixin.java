package net.parkabird.changedsynergy.mixin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.ltxprogrammer.changed.network.VariantAbilityActivate;
import net.ltxprogrammer.changed.network.packet.ChangedPacket;
import net.ltxprogrammer.changed.network.packet.GrabEntityPacket;
import net.ltxprogrammer.changed.entity.LivingEntityDataExtension;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;
import net.parkabird.changedsynergy.ai.TakeoverService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Validate native ability requests on the server, including custom clients. */
@Mixin(value = {VariantAbilityActivate.class, GrabEntityPacket.class,
        GrabEntityPacket.EscapeKeyState.class}, remap = false)
public abstract class TakeoverNativeServerPacketMixin {
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, require = 1)
    private void synergy$validate(NetworkEvent.Context context, CompletableFuture<Level> level,
            Executor executor, CallbackInfoReturnable<CompletableFuture<Void>> callback) {
        var player = context.getSender();
        if (player == null) return;
        if (!player.server.isSameThread()) {
            // Re-enter on the logical server so eligibility cannot be checked before
            // a queued takeover begins. Preserve the native handler for ordinary use.
            CompletableFuture<Void> result = new CompletableFuture<>();
            context.enqueueWork(() -> {
                try {
                    ((ChangedPacket)(Object)this).handle(context,
                            CompletableFuture.completedFuture(player.level()), player.server)
                            .whenComplete((unused, failure) -> {
                                if (failure == null) result.complete(null);
                                else result.completeExceptionally(failure);
                            });
                } catch (Throwable failure) { result.completeExceptionally(failure); }
            });
            callback.setReturnValue(result);
        } else if (TakeoverService.active(player)
                || (GrabEntityPacket.EscapeKeyState.class.isInstance(this)
                    && player instanceof LivingEntityDataExtension extension
                    && extension.getGrabbedBy() == null)) {
            // Key-state packets already in transit can arrive just after a
            // successful release. Changed treats that harmless race as an
            // IllegalStateException; consume it before it can poison the next QTE.
            context.setPacketHandled(true);
            callback.setReturnValue(CompletableFuture.completedFuture(null));
        }
    }
}
