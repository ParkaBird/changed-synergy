package net.parkabird.changedsynergy.regression;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ai.PerchSleepService;
import net.parkabird.changedsynergy.block.PerchBlock;
import net.parkabird.changedsynergy.init.*;

@Mod.EventBusSubscriber(modid="changed_synergy",value=Dist.DEDICATED_SERVER)
public final class PerchRegression {
    private static int checks;
    @SubscribeEvent public static void run(ServerStartedEvent event) {
        var server=event.getServer();var level=server.overworld();
        try {
            var base=new BlockPos(500,100,500);level.getChunk(base);level.getChunk(base.offset(-16,0,-16));
            var area=new AABB(base).inflate(5);
            var player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);player.setPos(500.5,100,502.5);
            level.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(true,server);
            int failures=0;
            for(var block:List.of(ChangedSynergyBlocks.EAGLE_OWL_PERCH_STAND.get(),ChangedSynergyBlocks.EAGLE_OWL_PERCH_RAIL.get()))for(var facing:Direction.Plane.HORIZONTAL) {
                var state=block.defaultBlockState().setValue(PerchBlock.FACING,facing);var second=block.other(base,state);
                for(var mode:List.of(GameType.SURVIVAL,GameType.CREATIVE))for(var broken:List.of(base,second)) {
                    player.setGameMode(mode);
                    level.setBlock(base.below(),Blocks.STONE.defaultBlockState(),3);
                    if(!block.vertical())level.setBlock(second.below(),Blocks.STONE.defaultBlockState(),3);
                    level.setBlock(base,state,3);block.setPlacedBy(level,base,state,null,ItemStack.EMPTY);
                    level.getEntitiesOfClass(ItemEntity.class,area).forEach(Entity::discard);
                    check(player.gameMode.destroyBlock(broken),"real player mining: "+mode+block+facing+broken);
                    int count=level.getEntitiesOfClass(ItemEntity.class,area).stream().filter(e->e.getItem().is(block.asItem())).mapToInt(e->e.getItem().getCount()).sum();
                    int expected=mode==GameType.SURVIVAL?1:0;
                    if(count!=expected){failures++;System.out.println("PERCH_DROP_MISMATCH: "+block+" "+facing+" "+mode+" half="+(broken.equals(base)?"lower":"upper")+" actual="+count);}
                    check(level.getBlockState(base).isAir()&&level.getBlockState(second).isAir(),"both halves removed");
                    level.getEntitiesOfClass(ItemEntity.class,area).forEach(Entity::discard);
                }
                level.setBlock(base,state,3);block.setPlacedBy(level,base,state,null,ItemStack.EMPTY);
                var first=ChangedSynergyEntities.LATEX_EAGLE_OWL.get().create(level);var other=ChangedSynergyEntities.LATEX_EAGLE_OWL.get().create(level);
                for(var bird:List.of(first,other)){bird.setNoAi(true);bird.setPos(500.5,101,500.5);level.addFreshEntity(bird);}
                first.startSleeping(base);check(first.position().distanceTo(PerchSleepService.position(level,base))<.001,"native sleep position");
                if(!block.vertical()) {
                    other.startSleeping(second);check(other.isSleeping(),"second owl sleeps independently");
                    check(Math.abs(first.position().distanceTo(other.position())-1.25)<.001,"rail centers 1.25 blocks apart");
                    check(!first.getBoundingBox().intersects(other.getBoundingBox()),"sleeping entity boxes do not overlap");
                }
                for(var bird:List.of(first,other)){bird.stopSleeping();bird.discard();}
                level.destroyBlock(base,false);
            }
            check(failures==0,"all native mining drop counts; mismatches="+failures);
            System.out.println("PERCH_REGRESSION_PASS: "+checks+" native survival/creative mining, directions and dual-owl sleep checks");
        } catch(Throwable failure){System.err.println("PERCH_REGRESSION_FAIL");failure.printStackTrace();}
        finally {server.halt(false);}
    }
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
}
