package net.parkabird.changedsynergy.regression;

import java.util.List;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.*;
import net.parkabird.changedsynergy.world.inventory.PlayerOutpostCargoInventory;

/** Verify actual combat and work transitions instead of only target-permission booleans. */
final class CampCrewCombatChecks {
    private static int checks;
    static void run(ServerPlayer owner, Outpost camp) throws Exception {
        var level=owner.serverLevel(); var data=PlayerOutpostData.get(owner.server);
        var mob=(ChangedEntity)ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("changed:latex_leaf")).create(level);
        Zombie enemy=null;
        boolean oldAssist=ChangedSynergyConfig.COMMON.companionMonsterAssistOnly.get();
        var oldOwner=owner.position(); BlockPos ore=new BlockPos(25,90,20); var oldOre=level.getBlockState(ore);
        try {
            ChangedSynergyConfig.COMMON.companionMonsterAssistOnly.set(true);
            owner.setPos(20.5,90,22.5); mob.setPos(20.5,90,20.5); mob.setOnGround(true); level.addFreshEntity(mob);
            CreaturePersonality.establishRelationship(mob,owner); CreaturePersonality.setFamiliarity(mob,owner,60);
            check(data.add(camp,mob.getUUID(),"CombatAuditor",camp.dimension,mob.blockPosition(),PlayerOutpostSupplyType.of(mob)),"Combat friend joins the camp");
            data.crewJob(camp,mob.getUUID(),CrewJob.COMBAT); PlayerOutpostService.applyRole(mob,owner,Role.CREW);
            enemy=EntityType.ZOMBIE.create(level); enemy.setPos(24.5,90,21.5); enemy.setNoAi(true); level.addFreshEntity(enemy);
            enemy.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new net.minecraft.world.item.ItemStack(Items.CARVED_PUMPKIN));
            owner.setLastHurtByMob(enemy);
            check(fights(level,owner,mob,enemy),"Combat friend actually approaches and attacks the owner's attacker");
            stop(mob); owner.setLastHurtByMob(null); enemy.setHealth(enemy.getMaxHealth());
            mob.setPos(20.5,90,20.5); mob.setOnGround(true); owner.setLastHurtMob(enemy);
            check(fights(level,owner,mob,enemy),"Combat friend also joins a monster fight initiated by the owner");
            stop(mob); owner.setLastHurtMob(null); owner.setLastHurtByMob(null);
            alliedMember(owner,mob);

            stop(mob); owner.setLastHurtMob(null); owner.setLastHurtByMob(null); enemy.setHealth(enemy.getMaxHealth());
            mob.setPos(20.5,90,20.5); mob.setOnGround(true);
            data.crewJob(camp,mob.getUUID(),CrewJob.MINE); PlayerOutpostService.applyRole(mob,owner,Role.CREW);
            mob.setTarget(enemy);
            check(mob.getTarget()==null,"Switching to mining rejects proactive combat");
            level.setBlock(ore,Blocks.IRON_ORE.defaultBlockState(),3);
            CampNavigationChecks.tick(level,owner,List.of(mob),240);
            check(level.getBlockState(ore).isAir() && PlayerOutpostCargoInventory.inventory(mob).workItems().stream().anyMatch(s->s.is(Items.RAW_IRON)),
                    "Work resumes after combat without losing cargo or a stuck combat route");
            System.out.println("CAMP_CREW_COMBAT_PASS checks="+checks);
        } finally {
            stop(mob); if(enemy!=null)enemy.discard(); data.remove(camp,mob.getUUID()); PlayerOutpostService.clearAssignment(mob); mob.discard();
            owner.setLastHurtMob(null); owner.setLastHurtByMob(null); owner.setPos(oldOwner); level.setBlock(ore,oldOre,3);
            ChangedSynergyConfig.COMMON.companionMonsterAssistOnly.set(oldAssist);
        }
    }
    private static boolean fights(net.minecraft.server.level.ServerLevel level,ServerPlayer owner,ChangedEntity mob,Zombie enemy) throws Exception {
        float health=enemy.getHealth();
        for(int tick=0;tick<180;tick++) {
            enemy.tickCount++; enemy.tick(); // Includes native damage immunity expiry.
            CampNavigationChecks.tick(level,owner,List.of(mob),1);
            if(enemy.getHealth()<health || !enemy.isAlive()) return true;
        }
        var grab=BondedSuitService.ability(mob);
        System.out.println("CAMP_CREW_COMBAT_DIAGNOSTIC target="+mob.getTarget()+" pos="+mob.position()+" health="+enemy.getHealth()
                +" busy="+net.parkabird.changedsynergy.compat.ChangedAddonCompat.isGrabberBusy(mob)
                +" nativeHold="+(grab==null?null:grab.grabbedEntity)+" aggressive="+mob.isAggressive()
                +" goals="+mob.goalSelector.getAvailableGoals().stream().map(g->g.getGoal().getClass().getSimpleName()+":"+g.isRunning()).toList()
                +" targets="+mob.targetSelector.getAvailableGoals().stream().map(g->g.getGoal().getClass().getSimpleName()+":"+g.isRunning()).toList());
        return false;
    }
    @SuppressWarnings("unchecked")
    private static void alliedMember(ServerPlayer owner,ChangedEntity mob) throws Exception {
        java.util.Map<java.util.UUID,ServerPlayer> players=null;
        for(var field:net.minecraft.server.players.PlayerList.class.getDeclaredFields()) {
            if(java.util.Map.class.isAssignableFrom(field.getType()) && field.getGenericType().getTypeName().contains("UUID")
                    && field.getGenericType().getTypeName().contains("ServerPlayer")) {
                field.setAccessible(true); players=(java.util.Map<java.util.UUID,ServerPlayer>)field.get(owner.server.getPlayerList()); break;
            }
        }
        if(players==null)throw new AssertionError("Missing online-player index");
        var peer=new ServerPlayer(owner.server,owner.serverLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"CrewAllyPeer"));
        players.put(peer.getUUID(),peer);
        var score=owner.server.getScoreboard(); var previous=score.getPlayersTeam(owner.getScoreboardName());
        var team=score.addPlayerTeam("crew_"+peer.getUUID().toString().substring(0,8));
        var ally=net.ltxprogrammer.changed.init.ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(owner.serverLevel());
        try {
            score.addPlayerToTeam(owner.getScoreboardName(),team); score.addPlayerToTeam(peer.getScoreboardName(),team);
            ally.setPos(mob.getX()+2,mob.getY(),mob.getZ()); owner.serverLevel().addFreshEntity(ally);
            CreaturePersonality.establishRelationship(ally,peer); CreaturePersonality.setFamiliarity(ally,peer,60);
            CreaturePersonality.setSocialFollowing(ally,peer,true);
            owner.setLastHurtMob(ally);
            var defense=new SocialFriendDefenseGoal(mob); boolean selected=false;
            for(int i=0;i<40;i++) { CampNavigationChecks.beginAiTick(owner); mob.tickCount++; selected|=defense.canUse(); }
            check(!selected,"Combat crew does not attack a different-faction creature belonging to an allied player");
        } finally {
            owner.setLastHurtMob(null); ally.discard(); players.remove(peer.getUUID()); peer.discard();
            score.removePlayerTeam(team); if(previous!=null)score.addPlayerToTeam(owner.getScoreboardName(),previous);
        }
    }
    private static void stop(ChangedEntity mob) {
        mob.goalSelector.getRunningGoals().toList().forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);
        mob.targetSelector.getRunningGoals().toList().forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);
        mob.setTarget(null); mob.getNavigation().stop(); mob.setLastHurtByMob(null);
    }
    private static void check(boolean result,String message) { if(!result)throw new AssertionError(message); checks++; }
}
