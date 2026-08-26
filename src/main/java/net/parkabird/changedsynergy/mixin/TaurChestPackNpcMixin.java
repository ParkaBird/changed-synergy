package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.client.renderer.layers.TaurChestPackLayer;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.parkabird.changedsynergy.world.inventory.CentaurMountService;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The native pack layer normally hides itself on NPC taurs; allow configured friends too. */
@Mixin(value = TaurChestPackLayer.class, remap = false)
public abstract class TaurChestPackNpcMixin {
    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/ltxprogrammer/changed/entity/ChangedEntity;getUnderlyingPlayer()Lnet/minecraft/world/entity/player/Player;"),
            remap = false)
    private Player changedSynergy$showNpcChestPack(ChangedEntity entity) {
        Player underlying = entity.getUnderlyingPlayer();
        if (underlying != null) {
            return underlying;
        }
        return CentaurMountService.isCentaur(entity)
                        && CentaurMountService.hasChest(entity)
                ? Minecraft.getInstance().player
                : null;
    }
}
