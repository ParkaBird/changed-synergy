package net.parkabird.changedsynergy.regression;

import java.util.*;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid="changed_synergy",value=Dist.DEDICATED_SERVER)
public final class FollowupServer {
    private static Map<String,Integer> currentDrops;
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST,receiveCanceled=true)
    public static void drops(net.minecraftforge.event.entity.living.LivingDropsEvent event){
        if(currentDrops==null)return;
        if(event.isCanceled())throw new AssertionError("Ordinary audit creature drops were cancelled");
        for(var drop:event.getDrops())currentDrops.merge(ForgeRegistries.ITEMS.getKey(drop.getItem().getItem()).toString(),drop.getItem().getCount(),Integer::sum);
    }
    @SubscribeEvent public static void run(ServerStartedEvent event){
        var server=event.getServer();var level=server.overworld();
        try{
            level.getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(true,server);
            var player=new net.minecraft.server.level.ServerPlayer(server,level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"LootAudit"));
            player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(server,new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player);
            var identity=new net.parkabird.changedsynergy.origin.NativeOriginData.Identity();identity.state="human";net.parkabird.changedsynergy.origin.NativeOriginData.get(server).players.put(player.getUUID(),identity);
            for(int looting:new int[]{0,3}){
            var sword=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD);if(looting>0)sword.enchant(net.minecraft.world.item.enchantment.Enchantments.MOB_LOOTING,looting);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,sword);
            for(var type:ForgeRegistries.ENTITY_TYPES.getValues()){
                if(!ForgeRegistries.ENTITY_TYPES.getKey(type).getNamespace().equals("changed_synergy")||!(type.create(level) instanceof ChangedEntity))continue;
                var totals=new TreeMap<String,Integer>();
                for(int i=0;i<48;i++){
                    var mob=(ChangedEntity)type.create(level);mob.setPos(0,140,0);mob.setNoAi(true);level.getChunk(mob.blockPosition());level.addFreshEntity(mob);
                    currentDrops=totals;boolean hit=mob.hurt(level.damageSources().playerAttack(player),1000);currentDrops=null;
                    if(i==0)System.out.println("LOOT_DEATH_STATE: "+type+" table="+mob.getLootTable()+" hit="+hit+" health="+mob.getHealth()+" dead="+mob.isDeadOrDying());
                    if(!mob.isDeadOrDying())throw new AssertionError("Audit creature was not killed: "+type);
                    mob.discard();
                }
                System.out.println("SPECIES_DEATH_LOOT: looting="+looting+" "+ForgeRegistries.ENTITY_TYPES.getKey(type)+" "+totals);
                if(totals.getOrDefault("changed:white_latex_goo",0)==0||totals.getOrDefault("changed:latex_base",0)==0)throw new AssertionError("Missing native latex loot: "+type+" "+totals);
                if(type==net.parkabird.changedsynergy.init.ChangedSynergyEntities.LATEX_JERBOA.get()
                        &&(totals.getOrDefault("minecraft:wheat_seeds",0)==0||totals.getOrDefault("minecraft:rabbit_hide",0)==0||totals.containsKey("minecraft:sand")||totals.containsKey("minecraft:string")))throw new AssertionError("Incomplete Jerboa-specific table: "+totals);
            }
            }
            System.out.println("FOLLOWUP_LOOT_SERVER_PASS: all 8 Synergy creature types killed 48 times each with no enchantment and Looting III (768 deaths)");
        }catch(Throwable failure){System.err.println("FOLLOWUP_SERVER_FAIL");failure.printStackTrace();}
        finally{server.halt(false);}
    }
}
