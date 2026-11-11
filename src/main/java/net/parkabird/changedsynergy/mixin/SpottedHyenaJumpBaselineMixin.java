package net.parkabird.changedsynergy.mixin;

import java.util.Map;
import java.util.function.BiFunction;
import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.ltxprogrammer.changed.init.ChangedAttributes;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.parkabird.changedsynergy.entity.LatexSpottedHyena;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TransfurVariantInstance.class, remap = false)
public abstract class SpottedHyenaJumpBaselineMixin {
    @Inject(method = "mapAttributes(Lnet/minecraft/world/entity/player/Player;Ljava/util/Map;Ljava/util/function/BiFunction;Ljava/util/Map;Ljava/util/function/BiFunction;F)V", at = @At("HEAD"))
    private void changedSynergy$includeJumpBaseline(Player player, Map<Attribute, Double> from,
            BiFunction<Attribute, Double, Double> fromScaling, Map<Attribute, Double> to,
            BiFunction<Attribute, Double, Double> toScaling, float progress, CallbackInfo ci) {
        // Changed's instant-origin baseline uses vanilla attributes, omitting its own jump attribute.
        if (((TransfurVariantInstance<?>)(Object)this).getChangedEntity() instanceof LatexSpottedHyena
                && to.containsKey(ChangedAttributes.JUMP_STRENGTH.get()))
            from.putIfAbsent(ChangedAttributes.JUMP_STRENGTH.get(), 1.0);
    }
}
