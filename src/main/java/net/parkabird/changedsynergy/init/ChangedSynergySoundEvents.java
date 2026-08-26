package net.parkabird.changedsynergy.init;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.parkabird.changedsynergy.ChangedSynergyMod;

/** Sound events used when Synergy needs its own subtitle semantics. */
public final class ChangedSynergySoundEvents {
    public static final DeferredRegister<SoundEvent> REGISTRY =
            DeferredRegister.create(
                    ForgeRegistries.SOUND_EVENTS,
                    ChangedSynergyMod.MOD_ID);

    public static final RegistryObject<SoundEvent> PAT_ORGANIC =
            register("pat_organic");
    public static final RegistryObject<SoundEvent> PAT_LATEX_STICKY =
            register("pat_latex_sticky");
    public static final RegistryObject<SoundEvent> PAT_LATEX_BRUSH =
            register("pat_latex_brush");

    private ChangedSynergySoundEvents() {
    }

    private static RegistryObject<SoundEvent> register(String path) {
        return REGISTRY.register(path, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(
                        ChangedSynergyMod.MOD_ID, path)));
    }
}
