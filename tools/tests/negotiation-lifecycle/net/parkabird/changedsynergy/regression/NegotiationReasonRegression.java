package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.TransfurContext;
import net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.ltxprogrammer.changed.process.TransfurEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.ai.InvoluntaryTransfurNegotiation.Mode;
import net.parkabird.changedsynergy.ai.InvoluntaryTransfurNegotiation.Reason;
import net.parkabird.changedsynergy.ai.InvoluntaryTransfurNegotiation.View;

/** Actual incident cause, fixed capture snapshot, and all reason/mode wire mappings. */
final class NegotiationReasonRegression {
    private static int checks;
    static void run(MinecraftServer server) throws Exception {
        var level=server.overworld();
        var player=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"ReasonCheck"));
        player.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),player);
        player.setPos(192.5,160,192.5);level.getChunkAt(player.blockPosition());
        var identity=new net.parkabird.changedsynergy.origin.NativeOriginData.Identity();identity.state="human";
        net.parkabird.changedsynergy.origin.NativeOriginData.get(server).players.put(player.getUUID(),identity);
        var source=ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        var other=ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        source.setPos(player.position().add(1,0,0));other.setPos(player.position().add(2,0,0));
        level.addFreshEntity(source);level.addFreshEntity(other);
        var intent=new CompoundTag();intent.putInt("Value",HumanIntent.SEEK_HOST.ordinal());
        source.getPersistentData().put("ChangedSynergyHumanIntent",intent);
        FactionReputation.set(source,player,0);
        try {
            check(reason(source,player,Mode.ABSORPTION)==Reason.HOST_SEEKING,"Unprovoked absorption seeks a host");
            check(reason(source,player,Mode.ASSIMILATION)==Reason.COMPANION_SEEKING,"Unprovoked replication seeks a companion");
            FactionHostilityGrace.noteAggression(other,player);
            check(FactionHostilityGrace.wasAggressor(source,player),"The historical attack marker really is shared by the faction");
            check(reason(source,player,Mode.ABSORPTION)==Reason.HOST_SEEKING,
                    "Attacking a different faction member cannot turn this friendly capture into personal retaliation");
            check(!TakeoverService.isRetaliatoryCapture(source,player),"Final absorption routing agrees with the friendly reason");
            personalAttack(source,player);
            check(reason(source,player,Mode.ABSORPTION)==Reason.SELF_DEFENSE,"An actual attack on this individual remains self-defense");
            check(HumanIntent.of(source)==HumanIntent.SEEK_HOST,"First-contact intent stays fixed even after genuine conflict");
            LatexSocialMemory.settleAfterNegotiatedRelease(source,player);
            check(!TakeoverConflictMemory.initiated(source,player),"Successful negotiation closes the individual incident");
            check(reason(source,player,Mode.ABSORPTION)==Reason.HOST_SEEKING,"A later friendly capture cannot inherit the concluded fight");
            check(FactionHostilityGrace.wasAggressor(source,player),"Ending one negotiation does not erase the faction's history");

            var squad=new CompoundTag();squad.putUUID("Target",UUID.randomUUID());squad.putLong("Until",level.getGameTime()+200);
            source.getPersistentData().put("SynergyPursuitSquad",squad);
            check(reason(source,player,Mode.ABSORPTION)==Reason.HOST_SEEKING,"A squad assigned to someone else does not accuse this player");
            squad.putUUID("Target",player.getUUID());
            check(reason(source,player,Mode.ABSORPTION)==Reason.FACTION_RETALIATION,"An active squad targeting this player is faction retaliation");
            squad.putLong("Until",level.getGameTime()-1);
            check(reason(source,player,Mode.ABSORPTION)==Reason.HOST_SEEKING,"Expired squad assignments cannot supply a current capture reason");
            source.getPersistentData().remove("SynergyPursuitSquad");
            FactionReputation.set(source,player,-100);
            check(reason(source,player,Mode.ABSORPTION)==Reason.FACTION_RETALIATION,"Current hostile faction reputation remains faction retaliation");
            FactionReputation.set(source,player,0);

            var classify=InvoluntaryTransfurNegotiation.class.getDeclaredMethod("classifyReason",ChangedEntity.class,ServerPlayer.class,Mode.class,boolean.class);
            classify.setAccessible(true);
            check(classify.invoke(null,source,player,Mode.ABSORPTION,true)==Reason.SPECIAL_COMPLETION,"Incomplete-body completion keeps its dedicated reason");
            var decision=LatexAssimilationDecision.strongReplication(source.getSelfVariant(),TransfurContext.npcLatexAttack(source),100F);
            var event=new TransfurEvents.LatexAssimilationDecisionEvent(player,decision);
            InvoluntaryTransfurNegotiation.observeDecision(event);
            ProcessTransfur.setPlayerTransfurVariant(player,source.getSelfVariant());
            // Change current combat state after staging: completion and reopening must retain the original cause.
            personalAttack(source,player);
            event.getDecision().postTransfurListener().accept(IAbstractChangedEntity.forPlayer(player));
            var view=InvoluntaryTransfurNegotiation.view(player).orElseThrow();
            check(view.reason()==Reason.COMPANION_SEEKING && view.mode()==Mode.ASSIMILATION,
                    "The completion callback preserves its staged friendly reason instead of re-reading later combat");
            check(InvoluntaryTransfurNegotiation.view(player).orElseThrow().reason()==view.reason(),"Reopening the panel keeps the same captured reason");
            roundTrips();
        } finally {source.discard();other.discard();player.discard();}
        System.out.println("NEGOTIATION_REASON_REGRESSION_PASS: "+checks+" checks; current individual incident and all reason/mode packet and NBT mappings");
    }

    private static Reason reason(ChangedEntity mob,ServerPlayer player,Mode mode)throws Exception {
        var method=InvoluntaryTransfurNegotiation.class.getDeclaredMethod("classifyReason",ChangedEntity.class,ServerPlayer.class,Mode.class,boolean.class);
        method.setAccessible(true);return (Reason)method.invoke(null,mob,player,mode,false);
    }
    private static void personalAttack(ChangedEntity mob,ServerPlayer player) {
        var attack=new net.minecraftforge.event.entity.living.LivingAttackEvent(mob,player.damageSources().playerAttack(player),3F);
        TakeoverConflictMemory.beforeAttack(attack);
        TakeoverConflictMemory.afterDamage(new net.minecraftforge.event.entity.living.LivingDamageEvent(mob,attack.getSource(),3F));
    }
    private static void roundTrips()throws Exception {
        var menu=net.parkabird.changedsynergy.world.inventory.SocialInteractionMenu.class;
        var write=menu.getDeclaredMethod("writeNegotiationView",FriendlyByteBuf.class,View.class);write.setAccessible(true);
        var read=menu.getDeclaredMethod("readNegotiationView",FriendlyByteBuf.class);read.setAccessible(true);
        var save=menu.getDeclaredMethod("writeNegotiationView",CompoundTag.class,View.class);save.setAccessible(true);
        var load=menu.getDeclaredMethod("readNegotiationView",CompoundTag.class);load.setAccessible(true);
        for(var mode:Mode.values())for(var reason:Reason.values()) {
            var expected=new View(mode,reason,7,90,1,1,false,false,false);
            var buffer=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {write.invoke(null,buffer,expected);check(expected.equals(read.invoke(null,buffer)),"Packet maps "+mode+" / "+reason);}
            finally {buffer.release();}
            var tag=new CompoundTag();save.invoke(null,tag,expected);check(expected.equals(load.invoke(null,tag)),"NBT maps "+mode+" / "+reason);
        }
    }
    private static void check(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
        checks++;System.out.println("NEGOTIATION_REASON_CHECK: "+message);
    }
}
