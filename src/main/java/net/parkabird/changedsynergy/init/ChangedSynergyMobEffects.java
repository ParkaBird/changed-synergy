package net.parkabird.changedsynergy.init;

import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.effect.MesmerizedEffect;

public final class ChangedSynergyMobEffects {
    public static final DeferredRegister<MobEffect> REGISTRY = DeferredRegister.create(
            ForgeRegistries.MOB_EFFECTS, ChangedSynergyMod.MOD_ID);

    public static final RegistryObject<MobEffect> MESMERIZED = REGISTRY.register(
            "mesmerized", MesmerizedEffect::new);

    private ChangedSynergyMobEffects() {
    }
}
