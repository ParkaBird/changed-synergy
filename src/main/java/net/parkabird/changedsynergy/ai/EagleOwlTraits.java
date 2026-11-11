package net.parkabird.changedsynergy.ai;

import javax.annotation.Nullable;
import net.ltxprogrammer.changed.ability.*;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedAbilities;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.parkabird.changedsynergy.entity.LatexEagleOwl;

public final class EagleOwlTraits {
    public static final String REGRAB_AFTER="ChangedSynergyOwlRegrabAfter";
    private EagleOwlTraits(){}
    @Nullable public static LatexEagleOwl body(LivingEntity entity){
        if(entity instanceof LatexEagleOwl owl)return owl;
        if(!(entity instanceof Player))return null;
        var grabber=GrabEntityAbility.getGrabberSafe(entity).orElse(null);
        if(grabber!=null&&grabber.getChangedEntity() instanceof LatexEagleOwl owl&&BondedSuitService.getSuitController(owl)==entity)return owl;
        var nativeBody=IAbstractChangedEntity.forEitherSafe(entity).map(IAbstractChangedEntity::getChangedEntity).orElse(null);
        if(nativeBody instanceof LatexEagleOwl owl)return owl;
        return null;
    }
    public static boolean airborne(LivingEntity entity){return body(entity)!=null&&!entity.onGround()&&!entity.isInWaterOrBubble()&&!entity.isPassenger();}
    public static AABB reach(LivingEntity entity){return entity.getBoundingBox().inflate(1.1,.75,1.1).expandTowards(0,-.75,0);}
    public static boolean regrabBlocked(LivingEntity entity){return entity.getPersistentData().getLong(REGRAB_AFTER)>entity.level().getGameTime();}
    public static boolean canReach(LivingEntity entity,LivingEntity target){return reach(entity).intersects(target.getBoundingBox())&&entity.hasLineOfSight(target);}
    public static boolean validContact(GrabEntityAbilityInstance grab,LivingEntity target){
        var actor=grab.entity.getEntity();
        if(regrabBlocked(actor))return false;
        if(target==actor||!target.isAlive()||target.isRemoved()||target.isSpectator()||!canReach(actor,target))return false;
        if(PlayerHugService.mode(grab))return PlayerHugService.validTarget(grab,target);
        return target instanceof Player player?!net.ltxprogrammer.changed.process.ProcessTransfur.isPlayerTransfurred(player)
                &&net.ltxprogrammer.changed.Changed.config.server.isGrabEnabled.get()
                :!(target instanceof ChangedEntity)&&target.getType().is(net.ltxprogrammer.changed.init.ChangedTags.EntityTypes.HUMANOIDS);
    }
    @Nullable public static LivingEntity contactTarget(GrabEntityAbilityInstance grab){
        var actor=grab.entity.getEntity();
        return actor.level().getEntitiesOfClass(LivingEntity.class,reach(actor),target->validContact(grab,target))
                .stream().min(java.util.Comparator.comparingDouble(actor::distanceToSqr)).orElse(null);
    }
    public static LivingEntity clothingWearer(LivingEntity entity){var owl=body(entity);return owl!=null&&BondedSuitService.getSuitController(owl)==entity?owl:entity;}
}
