package net.parkabird.changedsynergy.regression;

import java.util.*;
import com.mojang.authlib.GameProfile;
import net.ltxprogrammer.changed.entity.*;
import net.ltxprogrammer.changed.init.*;
import net.minecraft.network.*;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.event.HuntAIEvents;
import net.parkabird.changedsynergy.origin.*;

/** Real native release damage must not become an ally-rally crime. */
@Mod.EventBusSubscriber(modid="changed_synergy",value=net.minecraftforge.api.distmarker.Dist.DEDICATED_SERVER)
public final class EscapeAttributionRegression {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    @SubscribeEvent public static void run(ServerStartedEvent event){
        try{
            var server=event.getServer();var level=server.overworld();
            net.parkabird.changedsynergy.ChangedSynergyConfig.SPEC.setConfig(com.electronwill.nightconfig.core.CommentedConfig.inMemory());
            for(String mode:List.of("hostile","manual","social","chase","rest","addon-safe","suit","rescue","deliberate")){
                if(mode.equals("addon-safe")&&!net.parkabird.changedsynergy.compat.ChangedAddonCompat.isLoaded())continue;
                var player=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"EscapeAudit"));
                player.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),player);
                player.setGameMode(GameType.SURVIVAL);player.setPos(0,100,0);
                var identity=new NativeOriginData.Identity();identity.state="human";NativeOriginData.get(server).players.put(player.getUUID(),identity);
                level.getChunk(player.blockPosition());
                var field=Arrays.stream(ServerLevel.class.getDeclaredFields()).filter(f->net.minecraft.world.level.entity.PersistentEntitySectionManager.class.isAssignableFrom(f.getType())).findFirst().orElseThrow();field.setAccessible(true);
                var manager=(net.minecraft.world.level.entity.PersistentEntitySectionManager<?>)field.get(level);manager.updateChunkStatus(new net.minecraft.world.level.ChunkPos(player.blockPosition()),net.minecraft.server.level.FullChunkStatus.ENTITY_TICKING);manager.tick();
                var carrier=ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);carrier.setPos(0,100,0);carrier.setOnGround(true);level.addFreshEntity(carrier);
                var witness=ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);witness.setPos(2,100,0);witness.setOnGround(true);level.addFreshEntity(witness);
                LatexSocialMemory.beginTruce(witness,player,1000);
                check(HuntAIEvents.isEligibleHunter(witness),"witness is a real eligible hunter");
                check(level.getEntitiesOfClass(ChangedEntity.class,carrier.getBoundingBox().inflate(18)).contains(witness),"witness participates in the real spatial query");
                try{
                    BondedSuitService.ensureGrabAbility(carrier);var grab=BondedSuitService.ability(carrier);
                    int score=FactionReputation.score(carrier,player);float health=carrier.getHealth();
                    if(mode.equals("deliberate")){
                        carrier.hurt(level.damageSources().playerAttack(player),4);
                        check(LatexSocialMemory.isProvoked(witness,player),"deliberate attacks still rally kin");
                        continue;
                    }
                    if(mode.equals("suit")||mode.equals("rescue")){
                        LatexSocialMemory.addBond(carrier,player);
                        check(BondedSuitService.suitOwner(carrier,player,mode.equals("suit")?BondedSuitService.SuitReason.MANUAL:BondedSuitService.SuitReason.TRANSFUR),"actual bonded suit begins: "+mode);
                        score=FactionReputation.score(carrier,player);
                        player.setOnGround(true);
                        check(BondedSuitService.releaseOwner(carrier,player,BondedSuitService.ReleaseReason.MANUAL),"actual suit releases: "+mode);
                    }else{
                        grab.grabbedEntity=player;grab.grabStrength=0;grab.suited=grab.grabbedHasControl=false;
                        ((LivingEntityDataExtension)player).setGrabbedBy(carrier);
                        if(mode.equals("social"))LatexSocialMemory.beginFriendlySocialHug(carrier,player,200);
                        if(mode.equals("rest"))LatexSocialMemory.beginOrganicEvacuation(carrier,player,200);
                        if(mode.equals("addon-safe"))net.parkabird.changedsynergy.compat.ChangedAddonCompat.configureFriendlyGrab(grab,true);
                        if(mode.equals("chase")){
                            Class<?> type=Class.forName(CreaturePeerInteractionService.class.getName()+"$Session");
                            var ctor=type.getDeclaredConstructor(ChangedEntity.class,net.minecraft.world.entity.LivingEntity.class,boolean.class);ctor.setAccessible(true);Object session=ctor.newInstance(carrier,player,true);
                            for(var entry:Map.<String,Object>of("holder",carrier,"hold",grab,"runner",player,"stage",5).entrySet()){var f=type.getDeclaredField(entry.getKey());f.setAccessible(true);f.set(session,entry.getValue());}
                            var reserve=CreaturePeerInteractionService.class.getDeclaredMethod("reserve",type);reserve.setAccessible(true);reserve.invoke(null,session);
                        }
                        grab.releaseEntity(!mode.equals("manual"));
                    }
                    boolean alarm=LatexSocialMemory.isProvoked(witness,player);
                    if(mode.equals("hostile")&&Boolean.getBoolean("changed_synergy.escapeBaseline")){
                        check(alarm,"baseline reproduces kin alarm after native GRAB_ESCAPE damage");
                        check(carrier.getHealth()<health,"baseline retains hostile escape damage; health="+health+"->"+carrier.getHealth()+" effects="+carrier.getActiveEffects());
                        System.out.println("ESCAPE_ATTRIBUTION_BASELINE_REPRODUCED: native release damage rallies an uninvolved witness");return;
                    }
                    check(!alarm&&witness.getTarget()!=player,"escape does not alert unrelated kin: "+mode);
                    check(FactionReputation.score(carrier,player)==score,"escape does not reduce regional reputation: "+mode);
                    if(mode.equals("hostile"))check(carrier.getHealth()<health&&GrabEscapeStunService.isStunned(carrier),"ordinary hostile damage and stun penalties remain");
                    else check(carrier.getHealth()==health&&!carrier.hasEffect(MobEffects.WEAKNESS)&&!GrabEscapeStunService.isStunned(carrier),"safe/manual release stays harmless: "+mode);
                    check(!player.noPhysics&&((LivingEntityDataExtension)player).getGrabbedBy()==null,"native release clears player physics/link: "+mode);
                }finally{CreaturePeerInteractionService.cancel(carrier);carrier.discard();witness.discard();player.discard();}
            }
            System.out.println("ESCAPE_ATTRIBUTION_PASS: "+checks+" hostile/manual/social/chase/carry/bonded-suit/rescue and deliberate-hit checks");
        }catch(Throwable failure){System.err.println("ESCAPE_ATTRIBUTION_FAIL");failure.printStackTrace();}
        finally{event.getServer().halt(false);}
    }
}
