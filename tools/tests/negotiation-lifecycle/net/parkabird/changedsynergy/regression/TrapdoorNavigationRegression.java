package net.parkabird.changedsynergy.regression;

import java.util.HashMap;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.ltxprogrammer.changed.init.ChangedBlocks;
import net.ltxprogrammer.changed.block.AbstractLabDoor;
import net.ltxprogrammer.changed.block.AbstractLargeLabDoor;
import net.ltxprogrammer.changed.block.QuarterSection;
import net.ltxprogrammer.changed.block.NineSection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.parkabird.changedsynergy.ai.CompanionFollowNavigation;
import net.parkabird.changedsynergy.ai.CompanionOpenDoorGoal;

final class TrapdoorNavigationRegression {
    private static int checks;
    static void run(MinecraftServer server) throws Exception {
        var level=server.overworld();var original=new HashMap<BlockPos,BlockState>();
        BlockPos origin=new BlockPos(768,160,768);level.getChunkAt(origin);
        try {
            for(var pos:BlockPos.betweenClosed(origin.offset(-6,-1,-6),origin.offset(6,3,6))) {
                original.put(pos.immutable(),level.getBlockState(pos));
                level.setBlock(pos,pos.getY()==159?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
            }
            for(boolean axisX:new boolean[]{true,false}) for(boolean upper:new boolean[]{true,false})
                for(boolean companion:new boolean[]{true,false}) {
                    for(var pos:BlockPos.betweenClosed(origin.offset(-5,0,-5),origin.offset(5,2,5))) {
                        int side=axisX?pos.getZ()-origin.getZ():pos.getX()-origin.getX();
                        level.setBlock(pos,Math.abs(side)==1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
                    }
                    BlockPos panel=upper?origin.above():origin;
                    var trapdoor=Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN,true)
                            .setValue(TrapDoorBlock.FACING,axisX?Direction.NORTH:Direction.EAST);
                    level.setBlock(panel,trapdoor,2);
                    var mob=ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
                    mob.setPos(origin.getX()+.5-(axisX?4:0),160,origin.getZ()+.5-(axisX?0:4));
                    level.addFreshEntity(mob);
                    for(var goal:mob.goalSelector.getAvailableGoals().stream().toList())mob.goalSelector.removeGoal(goal.getGoal());
                    for(var goal:mob.targetSelector.getAvailableGoals().stream().toList())mob.targetSelector.removeGoal(goal.getGoal());
                    mob.setOnGround(true);var follow=new CompanionFollowNavigation(mob);
                    Vec3 destination=origin.getCenter().add(axisX?4:0,-.5,axisX?0:4);
                    var ticks=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(MinecraftServer.class,"f_129766_");
                    boolean crossed=false;
                    try {
                        for(int t=0;t<220;t++) {
                            ticks.setInt(server,server.getTickCount()+1);
                            if(t%10==0)mob.getNavigation().moveTo(destination.x,destination.y,destination.z,.35);
                            if(companion)follow.tickProgress(true);
                            Vec3 before=mob.position();level.tickNonPassenger(mob);
                            check(mob.position().subtract(before).horizontalDistanceSqr()<1.5*1.5,"Trapdoor recovery uses ordinary movement rather than teleporting: "+before+" -> "+mob.position());
                            if((axisX?mob.getX()-origin.getX():mob.getZ()-origin.getZ())>2) {crossed=true;break;}
                        }
                        System.out.println("TRAPDOOR_ROUTE: axisX="+axisX+" upper="+upper+" companion="+companion+" crossed="+crossed+" pos="+mob.position()+" width="+mob.getBbWidth()+" box="+mob.getBoundingBox()+" collide="+mob.horizontalCollision+" motion="+mob.getDeltaMovement()+" next="+(mob.getNavigation().isDone()?"done":mob.getNavigation().getPath().getNextNodePos()));
                        check(crossed,"Actual navigation crosses the upright trapdoor opening, including head-level panels and native movement goals");
                        check(level.getBlockState(panel).equals(trapdoor),"Decorative trapdoor remains in its original state");
                    } finally {mob.discard();}
            }
            facilityDoors(server,origin);
            operablePassages(server,origin);
            System.out.println("TRAPDOOR_NAVIGATION_REGRESSION_PASS: "+checks+" native movement, upper/lower trapdoors, all eight facility doors, both corridor axes and pose recovery");
        } finally {original.forEach((pos,state)->level.setBlock(pos,state,2));}
    }
    private static void operablePassages(MinecraftServer server,BlockPos origin) throws Exception {
        var level=server.overworld();
        var ticks=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(MinecraftServer.class,"f_129766_");
        var panels=new java.util.ArrayList<BlockState>();
        for(var gate:java.util.List.of(Blocks.OAK_FENCE_GATE,Blocks.BAMBOO_FENCE_GATE,Blocks.CRIMSON_FENCE_GATE))panels.add(gate.defaultBlockState());
        for(var half:net.minecraft.world.level.block.state.properties.Half.values())panels.add(Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF,half));
        for(boolean alongX:new boolean[]{true,false})for(int sign:new int[]{1,-1})for(var initial:panels)
            for(int height=0;height<(initial.getBlock() instanceof TrapDoorBlock?2:1);height++) {
                for(var pos:BlockPos.betweenClosed(origin.offset(-5,0,-5),origin.offset(5,3,5))) {
                    int side=alongX?pos.getZ()-origin.getZ():pos.getX()-origin.getX();
                    level.setBlock(pos,Math.abs(side)==1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
                }
                var panel=origin.above(height);
                var state=initial.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        initial.getBlock() instanceof TrapDoorBlock?(alongX?Direction.NORTH:Direction.EAST):(alongX?Direction.EAST:Direction.SOUTH));
                level.setBlock(panel,state,2);
                var mob=ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
                mob.moveTo(origin.getX()+.5-(alongX?4*sign:0),origin.getY(),origin.getZ()+.5-(alongX?0:4*sign));
                level.addFreshEntity(mob);mob.goalSelector.removeAllGoals(g->true);mob.targetSelector.removeAllGoals(g->true);
                mob.goalSelector.addGoal(4,new CompanionOpenDoorGoal(mob));mob.setOnGround(true);
                var destination=Vec3.atBottomCenterOf(origin).add(alongX?4*sign:0,0,alongX?0:4*sign);
                boolean opened=false,crossed=false;
                try {
                    var path=mob.getNavigation().createPath(BlockPos.containing(destination),1);
                    check(path!=null&&path.canReach(),"closed passage is included in a complete ground route: "+state+" height="+height);
                    for(int t=0;t<260;t++) {
                        ticks.setInt(server,server.getTickCount()+1);
                        if(t%10==0)mob.getNavigation().moveTo(destination.x,destination.y,destination.z,.35);
                        var before=mob.position();level.tickNonPassenger(mob);
                        check(mob.position().subtract(before).horizontalDistanceSqr()<1.5*1.5,"passage crossing does not teleport");
                        opened|=level.getBlockState(panel).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN);
                        if((alongX?mob.getX()-origin.getX()-.5:mob.getZ()-origin.getZ()-.5)*sign>2) {crossed=true;break;}
                    }
                    check(opened&&crossed,"walker opens and crosses "+state+" height="+height+" axisX="+alongX+" sign="+sign+" at "+mob.position());
                    for(int t=0;t<45;t++){ticks.setInt(server,server.getTickCount()+1);level.tickNonPassenger(mob);}
                    check(!level.getBlockState(panel).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN),"opened passage closes after its walker clears it");
                } finally {mob.discard();}
            }
        for(var pos:BlockPos.betweenClosed(origin.offset(-5,0,-5),origin.offset(5,3,5)))level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
        var mob=ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);mob.moveTo(origin.getX()-1,origin.getY(),origin.getZ()+.5);
        level.addFreshEntity(mob);mob.goalSelector.removeAllGoals(g->true);mob.targetSelector.removeAllGoals(g->true);
        var goal=new CompanionOpenDoorGoal(mob);mob.goalSelector.addGoal(4,goal);
        try {
            level.setBlock(origin,Blocks.IRON_TRAPDOOR.defaultBlockState(),2);
            check(!CompanionOpenDoorGoal.openablePassage(level,origin,level.getBlockState(origin),mob),"iron trapdoor remains redstone-only");
            var hatch=origin.offset(0,0,3);level.setBlock(hatch,Blocks.OAK_TRAPDOOR.defaultBlockState(),2);
            for(int depth=1;depth<5;depth++)level.setBlock(hatch.below(depth),Blocks.AIR.defaultBlockState(),2);
            check(!CompanionOpenDoorGoal.openablePassage(level,hatch,level.getBlockState(hatch),mob),"unsupported floor hatch is not opened into a fall");
            level.setBlock(origin,Blocks.OAK_FENCE_GATE.defaultBlockState(),2);mob.getNavigation().stop();
            check(!goal.canUse(),"nearby gates stay untouched without a navigation route");
            var path=new net.minecraft.world.level.pathfinder.Path(java.util.List.of(new net.minecraft.world.level.pathfinder.Node(origin.getX(),origin.getY(),origin.getZ()),new net.minecraft.world.level.pathfinder.Node(origin.getX()+3,origin.getY(),origin.getZ())),origin.east(3),true);
            mob.getNavigation().moveTo(path,.35);check(goal.canUse(),"gate can open before colliding");goal.start();
            var other=ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);other.moveTo(Vec3.atBottomCenterOf(origin));level.addFreshEntity(other);other.setNoAi(true);
            try {goal.stop();check(level.getBlockState(origin).getValue(net.minecraft.world.level.block.FenceGateBlock.OPEN),"occupied gate is not closed onto another walker");}finally{other.discard();}
            level.setBlock(origin,level.getBlockState(origin).setValue(net.minecraft.world.level.block.FenceGateBlock.POWERED,true),2);
            goal.stop();check(level.getBlockState(origin).getValue(net.minecraft.world.level.block.FenceGateBlock.OPEN),"powered gate remains open");
            check(!goal.canUse(),"already open gate is left alone");
        } finally {mob.discard();}
        System.out.println("OPERABLE_PASSAGES_REGRESSION_PASS: wooden gates, foot/head-level trapdoors, both directions and axes, native closing, occupancy, redstone and floor safety");
    }
    private static void facilityDoors(MinecraftServer server,BlockPos origin) {
        var level=server.overworld();
        var ticks=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(MinecraftServer.class,"f_129766_");
        for(var block:java.util.List.of(ChangedBlocks.LAB_DOOR.get(),ChangedBlocks.BLUE_LAB_DOOR.get(),
                ChangedBlocks.MAINTENANCE_DOOR.get(),ChangedBlocks.LIBRARY_DOOR.get(),ChangedBlocks.LARGE_LAB_DOOR.get(),
                ChangedBlocks.LARGE_BLUE_LAB_DOOR.get(),ChangedBlocks.LARGE_MAINTENANCE_DOOR.get(),ChangedBlocks.LARGE_LIBRARY_DOOR.get()))
            for(var facing:java.util.List.of(Direction.NORTH,Direction.EAST)) {
                boolean large=block instanceof AbstractLargeLabDoor,alongX=facing==Direction.EAST;
                for(var pos:BlockPos.betweenClosed(origin.offset(-6,0,-6),origin.offset(6,3,6)))
                    level.setBlock(pos,(alongX?pos.getX():pos.getZ())==(alongX?origin.getX():origin.getZ())?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
                if(large) for(var section:NineSection.values())
                    level.setBlock(NineSection.BOTTOM_LEFT.getRelative(origin,facing,section),block.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,facing)
                            .setValue(AbstractLargeLabDoor.SECTION,section).setValue(AbstractLargeLabDoor.OPEN,true),2);
                else for(var section:QuarterSection.values())
                    level.setBlock(QuarterSection.BOTTOM_LEFT.getRelative(origin,facing,section),block.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,facing)
                            .setValue(AbstractLabDoor.SECTION,section).setValue(AbstractLabDoor.OPEN,true),2);
                var mob=ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
                mob.moveTo(origin.getX()+.5-(alongX?1.25:0),origin.getY(),origin.getZ()+.5-(alongX?0:1.25));
                level.addFreshEntity(mob);
                mob.goalSelector.removeAllGoals(goal->true);mob.targetSelector.removeAllGoals(goal->true);
                try {
                    mob.setOnGround(true);mob.horizontalCollision=true;
                    var path=new net.minecraft.world.level.pathfinder.Path(java.util.List.of(
                            new net.minecraft.world.level.pathfinder.Node(origin.getX(),origin.getY(),origin.getZ()),
                            new net.minecraft.world.level.pathfinder.Node(origin.getX()+(alongX?3:0),origin.getY(),origin.getZ()+(alongX?0:3))),origin,true);
                    mob.getNavigation().moveTo(path,.35);
                    var goal=new CompanionOpenDoorGoal(mob);
                    check(goal.canUse()!=large,"Only the 2x2 door family triggers ducking even when touching a large frame: "+block+" "+facing);
                    if(!large) {goal.start();check(mob.getPose()==net.minecraft.world.entity.Pose.CROUCHING,"Small-door crouch begins");goal.stop();}
                    check(mob.getPose()==net.minecraft.world.entity.Pose.STANDING,"Large door stays upright; interrupted small-door crouch recovers");
                    mob.moveTo(origin.getX()+.5-(alongX?4:0),origin.getY(),origin.getZ()+.5-(alongX?0:4));
                    mob.horizontalCollision=false;mob.setOnGround(true);mob.goalSelector.addGoal(4,new CompanionOpenDoorGoal(mob));
                    Vec3 destination=Vec3.atBottomCenterOf(origin).add(alongX?4:0,0,alongX?0:4);
                    boolean crossed=false,ducked=false;
                    for(int tick=0;tick<220;tick++) {
                        ticks.setInt(server,server.getTickCount()+1);
                        if(tick%10==0)mob.getNavigation().moveTo(destination.x,destination.y,destination.z,.35);
                        level.tickNonPassenger(mob);
                        ducked|=mob.getPose()==net.minecraft.world.entity.Pose.CROUCHING;
                        if((alongX?mob.getX()-origin.getX():mob.getZ()-origin.getZ())>2) {crossed=true;break;}
                    }
                    check(crossed,"Actual navigation crosses "+block+" "+facing+" at "+mob.position());
                    check(ducked!=large,"Actual traversal crouches only for 2x2 doors: "+block+" "+facing);
                    for(int tick=0;tick<45;tick++) {ticks.setInt(server,server.getTickCount()+1);level.tickNonPassenger(mob);}
                    check(mob.getPose()==net.minecraft.world.entity.Pose.STANDING,"Standing restored beyond the door");
                } catch(IllegalAccessException failure) {throw new AssertionError(failure);}
                finally {mob.discard();}
            }
        System.out.println("FACILITY_DOOR_SIZE_REGRESSION_PASS: four 2x2 and four 3x3 doors, collision-triggered pose, actual crossing on both axes and standing recovery");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
}
