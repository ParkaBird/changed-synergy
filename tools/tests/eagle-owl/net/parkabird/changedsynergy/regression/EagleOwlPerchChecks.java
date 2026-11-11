package net.parkabird.changedsynergy.regression;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.ltxprogrammer.changed.init.*;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.parkabird.changedsynergy.ai.PerchSleepService;
import net.parkabird.changedsynergy.block.PerchBlock;
import net.parkabird.changedsynergy.init.*;

final class EagleOwlPerchChecks {
    private static int checks;
    static void verify(net.minecraft.server.MinecraftServer server){
        var level=server.overworld();long time=level.getDayTime();level.setDayTime(18000);level.updateSkyBrightness();
        var a=ChangedSynergyEntities.LATEX_EAGLE_OWL.get().create(level);var b=ChangedEntities.LATEX_CROW.get().create(level);
        try{
            for(var bird:List.of(a,b)){bird.setNoAi(true);bird.setPos(200,101,200);level.getChunk(bird.blockPosition());level.addFreshEntity(bird);}
            var base=new BlockPos(200,100,200);
            for(var p:BlockPos.betweenClosed(base.offset(-4,-1,-4),base.offset(4,-1,4)))level.setBlock(p,Blocks.STONE.defaultBlockState(),3);
            for(var block:List.of(ChangedSynergyBlocks.EAGLE_OWL_PERCH_STAND.get(),ChangedSynergyBlocks.EAGLE_OWL_PERCH_RAIL.get()))for(var facing:Direction.Plane.HORIZONTAL){
                var state=block.defaultBlockState().setValue(PerchBlock.FACING,facing);level.setBlock(base,state,3);block.setPlacedBy(level,base,state,null,ItemStack.EMPTY);
                var mate=block.vertical()?base.above():base.relative(facing.getClockWise());
                check(PerchSleepService.free(level,base,a),"first sleeping place free");a.startSleeping(base);
                check(a.isSleeping()&&a.position().distanceTo(PerchSleepService.position(level,base))<.001,"native sleeping position on beam");
                var support=block.vertical()?base.above():base;
                double supportY=support.getY()+level.getBlockState(support).getCollisionShape(level,support).max(Direction.Axis.Y);
                check(java.lang.Math.abs(a.getY()-supportY)<.00001,"sleep anchor equals collision support instead of starting inside the beam");
                for(int tick=0;tick<80;tick++) {
                    net.parkabird.changedsynergy.performance.SynergyPerformanceTracker.onServerTickStart(new net.minecraftforge.event.TickEvent.ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.START,()->true,server));
                    a.tick();
                }
                net.parkabird.changedsynergy.performance.SynergyPerformanceTracker.onServerTickStart(new net.minecraftforge.event.TickEvent.ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.START,()->true,server));
                check(a.isSleeping()&&a.position().distanceTo(PerchSleepService.position(level,base))<.001,"sleeping NPC stays on beam after four seconds of native physics");
                check(net.minecraftforge.event.ForgeEventFactory.fireSleepingLocationCheck(a,base),"Forge retains bird sleep");
                check(level.getBlockState(base).getValue(PerchBlock.OCCUPIED),"occupied state follows sleep");
                check(!PerchSleepService.free(level,base,b),"another bird cannot take occupied place");
                if(block.vertical())check(!PerchSleepService.free(level,mate,b),"upper and lower stand halves share one place");
                else{
                    check(PerchSleepService.free(level,mate,b),"second rail end remains independent");b.startSleeping(mate);
                    check(Math.abs(a.position().distanceTo(b.position())-1.25)<.001,"two rail sleepers sit inward with independent space");
                    a.stopSleeping();check(!level.getBlockState(base).getValue(PerchBlock.OCCUPIED)&&level.getBlockState(mate).getValue(PerchBlock.OCCUPIED),"waking one rail sleeper preserves the other");b.stopSleeping();
                }
                a.stopSleeping();check(!a.isSleeping()&&!level.getBlockState(base).getValue(PerchBlock.OCCUPIED),"waking clears occupancy");
                level.destroyBlock(base,false);check(level.getBlockState(mate).isAir(),"paired removal remains intact");
            }
            EagleOwlCampPerchChecks.run(server);
            a.discard();b.discard();
            var player=new ServerPlayer(server,level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"PerchSleeper"));
            player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(server,new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player);
            player.setPos(200.5,100,201.5);check(!PerchSleepService.bird(player),"ordinary human cannot use bird perch");
            var form=ProcessTransfur.setPlayerTransfurVariant(player,ChangedSynergyTransfurVariants.LATEX_EAGLE_OWL.get(),null,1,false);
            check(PerchSleepService.bird(player),"transformed player can use bird perch");
            var block=ChangedSynergyBlocks.EAGLE_OWL_PERCH_STAND.get();var state=block.defaultBlockState();level.setBlock(base,state,3);block.setPlacedBy(level,base,state,null,ItemStack.EMPTY);
            var result=player.startSleepInBed(base);check(result.right().isPresent(),"real player sleep interaction succeeds: "+result);
            check(player.isSleeping()&&player.position().distanceTo(PerchSleepService.position(level,base))<.001,"player shares native perch positioning");
            player.stopSleeping();player.setOnGround(false);player.startFallFlying();
            var shared=ChangedSynergyAbilities.wingFlap();
            if(shared!=null){var flap=form.getAbilityInstance(shared);check(flap!=null,"owl uses the existing registered flight ability");
                player.stopFallFlying();player.getAbilities().instabuild=true;player.getAbilities().flying=true;var before=player.getDeltaMovement();flap.startUsing();
                check(player.getDeltaMovement().distanceToSqr(before)>1,"the original Addon flap supplies its impulse");
                check(((net.parkabird.changedsynergy.entity.LatexEagleOwl)form.getChangedEntity()).wingFlapTime(0)==0,"original flap retains the owl animation");
            }
            var oldAbilities=form.saveAbilities();net.ltxprogrammer.changed.util.TagUtil.putResourceLocation(oldAbilities,"selectedAbility",net.minecraft.resources.ResourceLocation.parse("changed_synergy:wing_flap"));
            form.loadAbilities(oldAbilities);check(form.selectedAbility!=null,"retired selection data safely falls back when loading an older form");
            ProcessTransfur.removePlayerTransfurVariant(player);player.discard();level.destroyBlock(base,false);
            System.out.println("EAGLE_OWL_PERCH_SERVER_PASS: "+checks+" capacity, occupancy, four directions, player sleeping and wake checks");
        }finally{a.discard();b.discard();level.setDayTime(time);level.updateSkyBrightness();}
    }
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
}
