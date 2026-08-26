package net.parkabird.changedsynergy.effect;

import net.ltxprogrammer.changed.util.EntityUtil;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Brief loss of coordination after failing a hypnosis escape.
 *
 * <p>This deliberately mirrors Shock's loss-of-control behavior without
 * applying Changed's Shock effect or any of its presentation.</p>
 */
public final class MesmerizedEffect extends MobEffect {
    public MesmerizedEffect() {
        super(MobEffectCategory.HARMFUL, 0xB98AF3);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity living, int amplifier) {
        EntityUtil.setNoControlTicks(living, 2);
    }
}
