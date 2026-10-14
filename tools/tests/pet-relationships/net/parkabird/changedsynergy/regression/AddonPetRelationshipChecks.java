package net.parkabird.changedsynergy.regression;

import net.foxyas.changedaddon.ability.UnfuseAbility;
import net.foxyas.changedaddon.init.ChangedAddonEntities;
import net.foxyas.changedaddon.init.ChangedAddonTransfurVariants;
import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.ltxprogrammer.changed.init.ChangedItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.parkabird.changedsynergy.ai.CreaturePersonality;
import net.parkabird.changedsynergy.event.LatexSocialEvents;

/** Loaded only with Addon; uses its actual split ability and native food-taming interaction. */
final class AddonPetRelationshipChecks {
    static void run(ServerPlayer owner, ServerPlayer other) {
        var level = owner.serverLevel();
        for (var form : java.util.List.of(ChangedAddonTransfurVariants.Gendered.EXP2.getMaleVariant(),
                ChangedAddonTransfurVariants.Gendered.EXP2.getFemaleVariant())) {
            ProcessTransfur.setPlayerTransfurVariant(owner, form, null, 1, false);
            var instance = new UnfuseAbility.Instance(new UnfuseAbility(), IAbstractChangedEntity.forPlayer(owner));
            PetRelationshipRegression.check(instance.canUse(), "Real Exp2 transfur exposes Addon's split ability");
            instance.startUsing();
            var pet = level.getEntitiesOfClass(ChangedEntity.class, owner.getBoundingBox().inflate(1),
                    mob -> mob.getType() == form.getEntityType() && mob.getUnderlyingPlayer() == null
                            && !PetRelationshipRegression.ACTORS.contains(mob)).stream().findFirst().orElseThrow();
            PetRelationshipRegression.ACTORS.add(pet); pet.setNoAi(true);
            PetRelationshipRegression.verifyPet(pet, owner, other);
        }
        ProcessTransfur.setPlayerTransfurVariant(owner, null);
        var pet = ChangedAddonEntities.EXP_2_MALE.get().create(level);
        pet.setNoAi(true); pet.setPos(owner.position()); level.addFreshEntity(pet);
        PetRelationshipRegression.ACTORS.add(pet);
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ChangedItems.ORANGE.get(), 64));
        for (int i = 0; i < 64 && !pet.isTame(); i++) {
            var event = new PlayerInteractEvent.EntityInteract(owner, InteractionHand.MAIN_HAND, pet);
            LatexSocialEvents.onBondedPetInteract(event);
            PetRelationshipRegression.check(!event.isCanceled(), "Untamed Exp2 food reaches its native taming route");
            pet.interact(owner, InteractionHand.MAIN_HAND);
        }
        PetRelationshipRegression.check(pet.isTame(), "Actual Exp2 food interaction can still tame the pet");
        PetRelationshipRegression.tick(pet, 45);
        PetRelationshipRegression.verifyPet(pet, owner, other);
        CreaturePersonality.setFamiliarity(pet, owner, 42);
        PetRelationshipRegression.tick(pet, 85);
        PetRelationshipRegression.check(CreaturePersonality.familiarity(pet, owner) == 42,
                "Late native taming is reconciled once, not treated as a recurring affection award");
    }
}
