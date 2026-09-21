package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.TamableLatexEntity;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.parkabird.changedsynergy.ai.CreaturePersonality;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps a real relationship entity alive when a world switches to Peaceful. */
@Mixin(Mob.class)
public abstract class RelationshipPeacefulDespawnMixin {
    // This project intentionally ships without a generated refmap, so list both
    // the Mojmap development name and the 1.20.1 SRG production name.
    @Inject(
            method = {"checkDespawn", "m_6043_"},
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void changedSynergy$keepRelationshipsInPeaceful(CallbackInfo ci) {
        Mob self = (Mob)(Object)this;
        if (self.level().getDifficulty() != Difficulty.PEACEFUL
                || !(self instanceof ChangedEntity creature)) {
            return;
        }
        boolean nativePet = creature instanceof TamableLatexEntity pet
                && pet.isTame();
        // Read stored relationships directly.  Turning the friendship gamerule
        // off must pause that system, not make an existing individual eligible
        // for Peaceful deletion.
        boolean relationship = !CreaturePersonality
                .establishedRelationshipPlayerUuids(creature).isEmpty();
        boolean bond = !LatexSocialMemory.bondedPlayerUuids(creature).isEmpty()
                || LatexSocialMemory.petOwnerUuid(creature).isPresent();
        if (nativePet || relationship || bond) {
            creature.setPersistenceRequired();
            ci.cancel();
        }
    }
}
