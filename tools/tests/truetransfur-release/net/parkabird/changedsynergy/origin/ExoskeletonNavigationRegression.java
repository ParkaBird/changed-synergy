package net.parkabird.changedsynergy.origin;

import java.util.UUID;
import net.ltxprogrammer.changed.data.AccessorySlots;
import net.ltxprogrammer.changed.init.*;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import net.parkabird.changedsynergy.ai.ExoskeletonTakeoverAdapter;

/** Actual worn equipment, vanilla collisions and deterministic routes in production Forge. */
final class ExoskeletonNavigationRegression {
    static void run(ServerPlayer player) {
        var level=player.serverLevel();var session=UUID.randomUUID();
        ProcessTransfur.setPlayerTransfurVariant(player,ChangedTransfurVariants.LATEX_BENIGN_WOLF.get(),null,1,false);
        var source=ChangedEntities.EXOSKELETON.get().create(level);source.setCharge(1);
        var stack=source.getDropItem();ExoskeletonTakeoverAdapter.markForcedStack(player,source,stack);
        var slots=AccessorySlots.getForEntity(player).orElseThrow();slots.setItem(ChangedAccessorySlots.FULL_BODY.get(),stack);source.discard();
        var state=ExoskeletonTakeoverAdapter.capture(source,player);
        try {
            if(state.isEmpty())throw new AssertionError("Failed to attach actual marked exoskeleton");
            for(var pos:BlockPos.betweenClosed(new BlockPos(30,188,30),new BlockPos(42,188,34)))level.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
            for(var pos:BlockPos.betweenClosed(new BlockPos(30,189,30),new BlockPos(42,193,34)))level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
            player.noPhysics=false;player.setNoGravity(false);player.setPos(30.5,189,32.5);player.setOnGround(true);player.setDeltaMovement(Vec3.ZERO);
            route(state,30.5,189,32.5,8);int pauses=0;
            for(int tick=0;tick<48;tick++)if(tick(player,state,session)==ExoskeletonTakeoverAdapter.Motion.PARKED)pauses++;
            if(pauses!=0||player.getX()<35.4||Math.abs(player.getY()-189)>.01)throw new AssertionError("Flat route stutters or floats: pauses="+pauses+" pos="+player.position());
            System.out.println("EXOSKELETON_NAVIGATION_PASS: 48 continuous flat movement ticks across path nodes");
            player.setPos(30.5,190,32.5);player.setOnGround(true);state.remove("ControlPath");state.putLong("NextControlPath",Long.MAX_VALUE);state.putDouble("ControlVerticalSpeed",0);
            for(int i=0;i<20;i++)tick(player,state,session);
            if(Math.abs(player.getY()-189)>.01||!player.onGround())throw new AssertionError("Unsupported controller still floats: "+player.position());
            System.out.println("EXOSKELETON_NAVIGATION_PASS: stale grounded flag cannot prevent a one-block descent");
            for(var pos:BlockPos.betweenClosed(new BlockPos(33,189,30),new BlockPos(42,189,34)))level.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
            player.setPos(32.5,189,32.5);player.setDeltaMovement(Vec3.ZERO);state.putDouble("ControlVerticalSpeed",0);route(state,32.5,190,32.5,6);
            for(int i=0;i<58;i++)tick(player,state,session);
            if(player.getX()<36||Math.abs(player.getY()-190)>.01)throw new AssertionError("Native jump cannot traverse a one-block step: "+player.position());
            System.out.println("EXOSKELETON_NAVIGATION_PASS: native jump and collision handling reach the raised route");
            if(source.isAlive()||!source.isRemoved()||!slots.getItem(ChangedAccessorySlots.FULL_BODY.get()).orElseThrow().hasTag())throw new AssertionError("Movement recreates or loses the controller");
            System.out.println("EXOSKELETON_NAVIGATION_PASS: actual equipment remains the sole controller");
        } finally {slots.setItem(ChangedAccessorySlots.FULL_BODY.get(),net.minecraft.world.item.ItemStack.EMPTY);ProcessTransfur.removePlayerTransfurVariant(player);player.noPhysics=false;}
    }
    private static void route(CompoundTag state,double x,double y,double z,int count) {
        var path=new ListTag();for(int i=0;i<count;i++){var node=new CompoundTag();node.putDouble("X",x+i);node.putDouble("Y",y);node.putDouble("Z",z);path.add(node);}
        state.put("ControlPath",path);state.putInt("ControlPathIndex",0);state.remove("LastProgressTick");state.remove("MotionX");state.remove("MotionZ");state.putLong("NextControlPath",Long.MAX_VALUE);
    }
    private static ExoskeletonTakeoverAdapter.Motion tick(ServerPlayer player,CompoundTag state,UUID session) {
        var level=player.serverLevel();((ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+1);
        return ExoskeletonTakeoverAdapter.tick(player,state,session);
    }
}
