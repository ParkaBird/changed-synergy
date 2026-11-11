package net.parkabird.changedsynergy.npc;

import java.util.*;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ai.NaturalCampService;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;

/** Credit witnessed harvesting only after the player replants the same crop. */
@Mod.EventBusSubscriber(modid="changed_synergy")
public final class CampLifeEvents {
    private record Harvest(ServerLevel level,BlockPos pos,net.minecraft.world.level.block.Block crop,String home,long until){}
    private static final Map<UUID,Harvest> harvests=new HashMap<>();
    private static ChangedEntity witness(ServerLevel level,BlockPos pos,ServerPlayer player){var body=TrueTransfurCompat.interactionBody(player);return level.getEntitiesOfClass(ChangedEntity.class,new AABB(pos).inflate(16),m->CampLifeService.mainCamp(m)&&m.isAlive()&&!m.isSleeping()&&m.getTarget()==null&&NaturalCampService.bounds(m).contains(net.minecraft.world.phys.Vec3.atCenterOf(pos))&&m.hasLineOfSight(body)).stream().min(Comparator.comparingDouble(m->m.distanceToSqr(body))).orElse(null);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void harvested(BlockEvent.BreakEvent event){
        if(event.isCanceled()||!(event.getLevel() instanceof ServerLevel level)||!(event.getPlayer() instanceof ServerPlayer player)||player.isCreative()||player.isSpectator())return;
        if(event.getState().getBlock() instanceof CropBlock crop&&crop.isMaxAge(event.getState())){var npc=witness(level,event.getPos(),player);if(npc!=null&&CampLifeService.canHelp(npc,player))harvests.put(player.getUUID(),new Harvest(level,event.getPos().immutable(),crop,StoryNpcService.homeKey(npc),level.getGameTime()+200));}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void replanted(BlockEvent.EntityPlaceEvent event){
        if(event.isCanceled()||!(event.getEntity() instanceof ServerPlayer player))return;var remembered=harvests.remove(player.getUUID());if(remembered==null)return;
        if(remembered.level()!=event.getLevel()||!remembered.pos().equals(event.getPos())||remembered.until()<remembered.level().getGameTime()||event.getPlacedBlock().getBlock()!=remembered.crop())return;
        var npc=witness(remembered.level(),event.getPos(),player);if(npc!=null&&StoryNpcService.homeKey(npc).equals(remembered.home())&&CampLifeService.reward(npc,player,1)>0)NpcDialogue.context(npc,player,"camp_help_thanks");
    }
    @SubscribeEvent public static void loggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event){harvests.remove(event.getEntity().getUUID());if(event.getEntity() instanceof ServerPlayer player)CampApproachGoal.dismiss(player);}
    @SubscribeEvent public static void left(net.minecraftforge.event.entity.EntityLeaveLevelEvent event){if(event.getEntity() instanceof ChangedEntity npc)CampApproachGoal.left(npc);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void sleep(net.minecraftforge.event.entity.player.PlayerSleepInBedEvent event){
        if(event.getEntity() instanceof ServerPlayer player&&!player.isCreative()&&!player.isSpectator()&&event.getPos()!=null&&event.getResultStatus()==null&&!CampLifeService.canSleep(player,event.getPos())){
            event.setResult(net.minecraft.world.entity.player.Player.BedSleepingProblem.OTHER_PROBLEM);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("camp.changed_synergy.sleep_permission"),true);
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){harvests.clear();CampApproachGoal.clear();}
}
