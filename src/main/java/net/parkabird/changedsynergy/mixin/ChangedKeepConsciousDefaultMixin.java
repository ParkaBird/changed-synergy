package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.init.ChangedGameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Makes Changed's keep-conscious rule opt-out for newly created worlds. */
@Mixin(value = ChangedGameRules.class, remap = false)
public abstract class ChangedKeepConsciousDefaultMixin {
    @ModifyArg(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/GameRules$BooleanValue;create(Z)Lnet/minecraft/world/level/GameRules$Type;",
                    ordinal = 0,
                    remap = false),
            index = 0,
            remap = false,
            require = 0)
    private static boolean changedSynergy$keepConsciousByDefault(
            boolean original) {
        return true;
    }

    @ModifyArg(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/GameRules$BooleanValue;m_46250_(Z)Lnet/minecraft/world/level/GameRules$Type;",
                    ordinal = 0,
                    remap = false),
            index = 0,
            remap = false,
            require = 0)
    private static boolean changedSynergy$keepConsciousByDefaultObfuscated(
            boolean original) {
        return true;
    }
}
