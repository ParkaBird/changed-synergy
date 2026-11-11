package net.parkabird.changedsynergy.regression;

import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.init.*;

@Mod.EventBusSubscriber(modid="changed_synergy",value=Dist.CLIENT)
public final class FollowupClient {
    private static int stage,ticks,seatedId;private static CompletableFuture<Void> work;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||stage==9)return;var mc=Minecraft.getInstance();
        try{
            org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                if(!mc.gameDirectory.getCanonicalFile().getName().startsWith("followup-client-"))throw new AssertionError("isolated runtime");
                mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(2);mc.options.simulationDistance().set(2);mc.options.guiScale().set(2);
                var rules=new GameRules();rules.getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(false,null);rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
                mc.createWorldOpenFlows().createFreshLevel("followup",new LevelSettings("followup",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(7982,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions());stage=1;
            }
            if(mc.level==null||mc.player==null||mc.player.tickCount<40||mc.getOverlay()!=null)return;
            if(stage==1){
                mc.setScreen(null);FollowupRenderChecks.perchCloseup(mc);DesertClientChecks.run(mc);PerchSleepClientChecks.run(mc);
                work=CompletableFuture.runAsync(()->{
                    var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var level=player.serverLevel();var pos=player.blockPosition().offset(2,0,0);
                    level.setBlock(pos.below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
                    var pillow=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(net.minecraft.resources.ResourceLocation.parse("changed:blue_pillow"));level.setBlock(pos,pillow.defaultBlockState(),3);
                    var mob=ChangedSynergyEntities.LATEX_JERBOA.get().create(level);mob.setNoAi(true);mob.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);level.addFreshEntity(mob);seatedId=mob.getId();
                    if(!((net.ltxprogrammer.changed.block.entity.PillowBlockEntity)level.getBlockEntity(pos)).sitEntity(mob))throw new AssertionError("native pillow accepts Jerboa");
                    net.ltxprogrammer.changed.entity.AccessoryEntities.INSTANCE.forceReloadAccessories(mob);net.ltxprogrammer.changed.data.AccessorySlots.getForEntity(mob).orElseThrow().quickMoveStack(new net.minecraft.world.item.ItemStack(ChangedSynergyItems.DESERT_NOMAD_OUTFIT.get()));
                    net.ltxprogrammer.changed.process.ProcessTransfur.setPlayerTransfurVariant(player,ChangedSynergyTransfurVariants.LATEX_EAGLE_OWL.get(),null,1,false);
                },mc.getSingleplayerServer());stage=2;
            }
            if(stage==2&&work.isDone()){
                work.join();var form=net.ltxprogrammer.changed.process.ProcessTransfur.getPlayerTransfurVariant(mc.player);ticks++;
                if(ticks==10){
                    var server=mc.getSingleplayerServer();var seat=server.overworld().getEntity(seatedId).getVehicle();boolean missing=seat!=null&&mc.level.getEntity(seat.getId())==null;
                    server.execute(()->{var p=server.getPlayerList().getPlayer(mc.player.getUUID());if(seat!=null){if(missing)p.connection.send(seat.getAddEntityPacket());var data=seat.getEntityData().getNonDefaultValues();if(data!=null)p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(seat.getId(),data));p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetPassengersPacket(seat));}});
                }
                if(form==null||!(mc.level.getEntity(seatedId) instanceof net.parkabird.changedsynergy.entity.LatexJerboa mob)||!mob.isPassenger()){
                    if(ticks%40==0)System.out.println("SEAT_WAIT: client="+mc.level.getEntity(seatedId)+" server="+mc.getSingleplayerServer().overworld().getEntity(seatedId));
                    if(ticks>200)throw new AssertionError("Jerboa cushion synchronization timeout");return;
                }
                if(ticks<20)return;FollowupRenderChecks.seat(mc,mob);
                if(Boolean.getBoolean("changed_synergy.followupVisualOnly")){stage=9;System.out.println("FOLLOWUP_VISUAL_PASS: front/side clothed perch, native cushion and tall-eye outfit views");mc.stop();return;}stage=3;
            }
            if(stage==3&&FollowupContactChecks.tick(mc)&&EagleOwlLiveReleaseChecks.tick(mc)){
                stage=9;System.out.println("FOLLOWUP_CLIENT_PASS: tall-eye garments, real Jerboa seat and native-key aerial grabs/releases");mc.stop();
            }
        }catch(Throwable failure){stage=9;System.err.println("FOLLOWUP_CLIENT_FAIL");failure.printStackTrace();net.ltxprogrammer.changed.init.ChangedKeyMappings.USE_ABILITY.setDown(false);mc.stop();}
    }
}
