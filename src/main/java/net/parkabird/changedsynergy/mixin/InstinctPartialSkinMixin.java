package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.resources.ResourceLocation;
import net.parkabird.changedsynergy.client.InstinctPlayerSkinClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserves host player skins and slimness on Changed/Addon player-skinned forms in Instinct mode. */
@Pseudo
@Mixin(targets = {
        "net.ltxprogrammer.changed.entity.beast.DarkLatexWolfPartial",
        "net.ltxprogrammer.changed.entity.beast.LatexHuman",
        "net.foxyas.changedaddon.entity.partials.SnowLeopardPartialEntity"
}, remap = false)
public abstract class InstinctPartialSkinMixin {
    @Inject(method = "getSkinTextureLocation()Lnet/minecraft/resources/ResourceLocation;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void changedSynergy$instinctSkinTexture(CallbackInfoReturnable<ResourceLocation> callback) {
        ResourceLocation skin = InstinctPlayerSkinClient.resolveSkinTexture((ChangedEntity) (Object) this);
        if (skin != null) callback.setReturnValue(skin);
    }

    @Inject(method = "getModelName()Ljava/lang/String;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void changedSynergy$instinctModelName(CallbackInfoReturnable<String> callback) {
        String modelName = InstinctPlayerSkinClient.resolveModelName((ChangedEntity) (Object) this);
        if (modelName != null) callback.setReturnValue(modelName);
    }
}
