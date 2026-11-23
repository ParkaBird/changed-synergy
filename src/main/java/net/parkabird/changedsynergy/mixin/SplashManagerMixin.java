package net.parkabird.changedsynergy.mixin;

import java.util.List;
import net.minecraft.client.resources.SplashManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Appends Changed: Synergy title splashes without overwriting vanilla splashes. */
@Mixin(SplashManager.class)
public abstract class SplashManagerMixin {
    @Inject(
            method = {
                    "apply(Ljava/util/List;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
                    "m_5787_(Ljava/util/List;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V"
            },
            at = @At("HEAD"),
            remap = false
    )
    private void changedSynergy$addSplash(
            List<String> loaded,
            ResourceManager manager,
            ProfilerFiller profiler,
            CallbackInfo ci) {
        if (!loaded.contains("Moth.")) {
            loaded.add("Moth.");
        }
    }
}
