package net.parkabird.changedsynergy.regression;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.ModList;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.origin.NativeOriginData;
import net.parkabird.changedsynergy.world.inventory.BondedInventoryService;
import net.parkabird.changedsynergy.world.inventory.PlayerOutpostCargoInventory;

/** Real TT 2.0.0, including its hidden shell, replacement body and player capability. */
final class CampCrewTrueTransfurChecks {
    @SuppressWarnings({"rawtypes", "unchecked"})
    static void run(ServerPlayer owner, PlayerOutpostData.Outpost camp, Map<UUID,ServerPlayer> players) throws Exception {
        if (!ModList.get().isLoaded("true_transfur")) return;
        var level = owner.serverLevel();
        var data = PlayerOutpostData.get(owner.server);
        var identities = NativeOriginData.get(owner.server);
        var previousIdentity = identities.players.get(owner.getUUID());
        boolean originRule = level.getGameRules().getBoolean(ChangedSynergyGameRules.NATIVE_ORIGINS);
        var oldPosition = owner.position();
        var ore = new BlockPos(21,90,20);
        var oldBlock = level.getBlockState(ore);
        var mob = ChangedEntities.LATEX_LEAF.get().create(level);
        var controller = Class.forName("com.lugert.true_transfur.transfur.TransfurController");
        Class mode = Class.forName("com.lugert.true_transfur.transfur.ControlMode");
        var changeMode = controller.getMethod("switchControlMode", ServerPlayer.class, mode);
        Object instinct = Enum.valueOf(mode,"INSTINCT"), player = Enum.valueOf(mode,"PLAYER");
        ChangedEntity body = null;
        net.minecraft.world.entity.monster.Zombie enemy = null;
        boolean oldAssist = net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.companionMonsterAssistOnly.get();
        try {
            level.getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(true, owner.server);
            var identity = new NativeOriginData.Identity();
            identity.state = "native"; identity.form = identity.current = ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.getId().toString();
            identity.faction = "dark"; identities.players.put(owner.getUUID(),identity);
            owner.setPos(20.5,90,29.5);
            ProcessTransfur.setPlayerTransfurVariant(owner,ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get(),null,1,false);
            Capability cap = (Capability)Class.forName("com.lugert.true_transfur.transfur.TransfurCapabilities").getField("TRANSFUR_DATA").get(null);
            Object state = owner.getCapability(cap).orElse(null);
            if (state == null) throw new IllegalStateException("Missing TT state");
            state.getClass().getMethod("setTransfurred",boolean.class).invoke(state,true);
            check(Boolean.TRUE.equals(changeMode.invoke(null,owner,instinct)),"TT enters its actual INSTINCT mode");
            body = (ChangedEntity)TrueTransfurCompat.interactionBody(owner);
            check(TrueTransfurCompat.isInstinctShell(owner) && TrueTransfurCompat.isInstinctBody(body),"Physical body is distinct from the technical player");
            body.setNoAi(true); body.setPos(20.5,90,29.5); body.setHealth(body.getMaxHealth());
            owner.setPos(120.5,90,129.5);
            mob.setPos(20.5,90,20.5); mob.setOnGround(true); level.addFreshEntity(mob);
            CreaturePersonality.establishRelationship(mob,owner); CreaturePersonality.setFamiliarity(mob,owner,60);
            check(data.add(camp,mob.getUUID(),"InstinctCrew",camp.dimension,mob.blockPosition(),PlayerOutpostSupplyType.of(mob)),"Crew belongs to the real player");
            data.crewJob(camp,mob.getUUID(),PlayerOutpostData.CrewJob.MINE);
            PlayerOutpostService.applyRole(mob,owner,PlayerOutpostData.Role.CREW);
            ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE); pick.enchant(Enchantments.BLOCK_EFFICIENCY,3);
            BondedInventoryService.inventory(mob,owner).setItem(0,pick);
            level.setBlock(ore,Blocks.IRON_ORE.defaultBlockState(),3);
            CampNavigationChecks.tick(level,owner,List.of(mob),250);
            check(level.getBlockState(ore).isAir() && PlayerOutpostCargoInventory.inventory(mob).workItems().stream()
                    .anyMatch(stack -> stack.is(Items.RAW_IRON)),"Real scheduled mining uses the INSTINCT body rather than the far-away shell");
            check(ProcessTransfur.getPlayerTransfurVariantSafe(owner).isPresent(),"Travelling work preserves the owner's native transfur state");
            mob.goalSelector.getRunningGoals().toList().forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);
            data.crewJob(camp,mob.getUUID(),PlayerOutpostData.CrewJob.COMBAT); PlayerOutpostService.applyRole(mob,owner,PlayerOutpostData.Role.CREW);
            net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.companionMonsterAssistOnly.set(true);
            enemy=net.minecraft.world.entity.EntityType.ZOMBIE.create(level); enemy.setPos(24.5,90,25.5); enemy.setNoAi(true); level.addFreshEntity(enemy);
            enemy.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new ItemStack(Items.CARVED_PUMPKIN));
            body.setLastHurtMob(null); body.setLastHurtByMob(null); owner.setLastHurtMob(enemy);
            var defense=new SocialFriendDefenseGoal(mob);
            boolean ghostSelected=false;
            for(int i=0;i<40;i++) { CampNavigationChecks.beginAiTick(owner); mob.tickCount++; ghostSelected|=defense.canUse(); }
            check(!ghostSelected,"Combat assistance ignores a technical-shell-only fight");
            owner.setLastHurtMob(null); body.setLastHurtMob(enemy);
            boolean defended=false;
            for(int i=0;i<40&&!defended;i++) { CampNavigationChecks.beginAiTick(owner); mob.tickCount++; defended=defense.canUse(); }
            if(!defended)System.out.println("CAMP_CREW_TT_DEFENSE_DIAGNOSTIC bodyResolved="+(TrueTransfurCompat.interactionBody(owner)==body)
                    +" bodyAlive="+body.isAlive()+" ownerAlive="+owner.isAlive()+" bodyPos="+body.position()+" npcPos="+mob.position()
                    +" tier="+CreaturePersonality.relationshipTier(mob,owner)+" target="+mob.getTarget()+" pet="+LatexSocialMemory.petOwnerUuid(mob)
                    +" bonded="+LatexSocialMemory.hasActiveBond(mob)+" audience="+SocialAudienceGoal.isActive(mob)
                    +" busy="+net.parkabird.changedsynergy.compat.ChangedAddonCompat.isGrabberBusy(mob)
                    +" provoked="+LatexSocialMemory.isProvoked(mob,owner)+" mayAttack="+PlayerOutpostService.mayAttackTarget(mob,enemy)
                    +" canAttack="+mob.canAttack(enemy)+" recent="+(body.tickCount-body.getLastHurtMobTimestamp())
                    +" scan="+net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.backgroundScanInterval.get()
                    +" adaptive="+net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.adaptiveAiBudget.get());
            check(defended,"Combat assistance observes the visible INSTINCT body's attack despite a distant shell");
            defense.start();
            check(mob.getTarget()==enemy,"Physical-body combat resolves through the real player's crew");
            float health=enemy.getHealth();
            for(int i=0;i<180&&enemy.getHealth()>=health;i++) {
                enemy.tickCount++; enemy.tick(); CampNavigationChecks.tick(level,owner,List.of(mob),1);
            }
            check(enemy.getHealth()<health || !enemy.isAlive(),"Instinct-mode combat crew actually approaches and hits the threat");
            defense.stop(); enemy.discard(); enemy=null; body.setLastHurtMob(null); body.setLastHurtByMob(null);
            alliedBody(owner,body,players);
            mob.goalSelector.getRunningGoals().toList().forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);
            mob.setPos(20.5,90,20.5); mob.setDeltaMovement(Vec3.ZERO); mob.setOnGround(true);
            var follow = new PlayerOutpostGoal(mob);
            check(follow.canUse(),"Instinct crew can start following its physical body"); follow.start(); follow.tick();
            check(field(follow,"crewTarget") == body,"The follow helper aims at the true physical body");
            players.remove(owner.getUUID());
            check(!follow.canContinueToUse(),"Owner logout stops the proxy-follow task"); follow.stop(); players.put(owner.getUUID(),owner);
            check(Boolean.TRUE.equals(changeMode.invoke(null,owner,player)),"TT returns to PLAYER without an implicit mode change from crew work");
            mob.setPos(owner.getX(),owner.getY(),owner.getZ()-9); mob.setDeltaMovement(Vec3.ZERO);
            check(follow.canUse(),"Following can resume after the control mode changes"); follow.start(); follow.tick();
            check(field(follow,"crewTarget") == owner,"PLAYER mode uses the real player, never the discarded INSTINCT body"); follow.stop();
            check(!PlayerOutpostCargoInventory.inventory(mob).workItems().isEmpty(),"Mode switching and logout retain real collected cargo");
            System.out.println("CAMP_CREW_TRUETRANSFUR_PASS checks=18");
        } finally {
            players.put(owner.getUUID(),owner);
            if(enemy!=null)enemy.discard(); owner.setLastHurtMob(null); owner.setLastHurtByMob(null);
            net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.companionMonsterAssistOnly.set(oldAssist);
            if (TrueTransfurCompat.isInstinctShell(owner)) changeMode.invoke(null,owner,player);
            mob.goalSelector.getRunningGoals().toList().forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);
            data.remove(camp,mob.getUUID()); PlayerOutpostService.clearAssignment(mob); mob.discard();
            if (body != null && !body.isRemoved()) body.discard();
            if (previousIdentity == null) identities.players.remove(owner.getUUID()); else identities.players.put(owner.getUUID(),previousIdentity);
            level.getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(originRule,owner.server);
            level.setBlock(ore,oldBlock,3); owner.setPos(oldPosition);
        }
    }
    private static void alliedBody(ServerPlayer owner,ChangedEntity body,Map<UUID,ServerPlayer> players) {
        var peer=new ServerPlayer(owner.server,owner.serverLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"InstinctCrewAlly"));
        peer.setPos(body.getX()+3,body.getY(),body.getZ()); players.put(peer.getUUID(),peer);
        var score=owner.server.getScoreboard(); var previous=score.getPlayersTeam(owner.getScoreboardName());
        var team=score.addPlayerTeam("ttcrew_"+peer.getUUID().toString().substring(0,8));
        var ally=ChangedEntities.LATEX_LEAF.get().create(owner.serverLevel());
        boolean oldAssist=net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.companionMonsterAssistOnly.get();
        try {
            score.addPlayerToTeam(owner.getScoreboardName(),team); score.addPlayerToTeam(peer.getScoreboardName(),team);
            ally.setPos(body.getX()+2,body.getY(),body.getZ()); owner.serverLevel().addFreshEntity(ally);
            CreaturePersonality.establishRelationship(ally,peer); CreaturePersonality.setFamiliarity(ally,peer,60);
            CreaturePersonality.setSocialFollowing(ally,peer,true);
            check(LatexCreatureCombatRules.sharePlayerAlliance(ally,body),"An INSTINCT body shares its real owner's alliance with another player's crew");
            net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.companionMonsterAssistOnly.set(false);
            peer.setLastHurtMob(body); var defense=new SocialFriendDefenseGoal(ally); boolean selected=false;
            for(int i=0;i<40;i++) { CampNavigationChecks.beginAiTick(owner); ally.tickCount++; selected|=defense.canUse(); }
            check(!selected,"Allied combat crew does not mistake an INSTINCT player body for hostile prey");
        } finally {
            net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.companionMonsterAssistOnly.set(oldAssist);
            peer.setLastHurtMob(null); ally.discard(); players.remove(peer.getUUID()); peer.discard();
            score.removePlayerTeam(team); if(previous!=null)score.addPlayerToTeam(owner.getScoreboardName(),previous);
        }
    }
    private static Object field(Object object,String name) throws Exception {
        var field=object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static void check(boolean condition,String message) { if (!condition)throw new AssertionError(message); }
}
