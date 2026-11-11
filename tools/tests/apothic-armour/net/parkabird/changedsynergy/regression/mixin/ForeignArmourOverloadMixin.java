package net.parkabird.changedsynergy.regression.mixin;

import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;

/** Reproduces RosmontisLib's same-priority, merged three-argument overload. */
@Mixin(LivingEntity.class)
public abstract class ForeignArmourOverloadMixin {
    private float getDamageAfterArmorAbsorb(DamageSource source, float damage, LivingEntity living) {
        return CombatRules.getDamageAfterAbsorb(damage, living.getArmorValue(),
                (float) living.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
    }
}
