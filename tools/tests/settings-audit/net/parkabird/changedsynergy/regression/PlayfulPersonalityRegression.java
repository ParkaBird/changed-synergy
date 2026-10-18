package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.ai.CreaturePersonality.Trait;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.origin.NativeOriginData;
import net.parkabird.changedsynergy.world.inventory.SocialInteractionMenu;

final class PlayfulPersonalityRegression {
    static void run(MinecraftServer server)throws Exception {
        var level=server.overworld();var rules=level.getGameRules();
        boolean enabled=rules.getBoolean(ChangedSynergyGameRules.PERSONALITY_SYSTEM);
        boolean ai=rules.getBoolean(ChangedSynergyGameRules.NPC_AI);
        var player=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"PlayfulCheck"));
        player.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),player);
        player.setPos(192.5,160,192.5);level.getChunkAt(player.blockPosition());
        var human=new NativeOriginData.Identity();human.state="human";
        NativeOriginData.get(server).players.put(player.getUUID(),human);
        ChangedEntity actor=null;
        try {
            rules.getRule(ChangedSynergyGameRules.PERSONALITY_SYSTEM).set(true,server);
            rules.getRule(ChangedSynergyGameRules.NPC_AI).set(true,server);
            var types=List.of(ChangedEntities.DARK_LATEX_WOLF_MALE.get(),ChangedEntities.WHITE_LATEX_WOLF_MALE.get(),
                    ChangedEntities.POOLTOY_WOLF.get(),ChangedEntities.GAS_WOLF_MALE.get());
            int total=0;
            for(int kind=0;kind<types.size();kind++) {
                int playful=0,polite=0;
                for(int i=0;i<128;i++) {
                    ChangedEntity mob=types.get(kind).create(level);
                    mob.setUUID(new UUID(0x195BCAFEBABEL,((long)kind<<32)+i+1));
                    CreaturePersonality.ensure(mob);
                    var traits=CreaturePersonality.traits(mob);var primary=CreaturePersonality.dominantTrait(mob);
                    check(traits.size()>=2 && traits.size()<=3 && !primary.behaviorOnly(),"Natural personality structure");
                    check(!(traits.contains(Trait.PLAYFUL)&&traits.contains(Trait.POLITE)),"Mutually exclusive behavior traits");
                    if(traits.contains(Trait.PLAYFUL)) {
                        playful++;var saved=mob.saveWithoutId(new CompoundTag());
                        ChangedEntity restored=types.get(kind).create(level);restored.load(saved);
                        try {check(CreaturePersonality.traits(restored).equals(traits) && CreaturePersonality.dominantTrait(restored)==primary,
                                "Playful survives real save/load without becoming the dialogue personality");}
                        finally {restored.discard();}
                        if(kind==0 && actor==null)actor=mob;
                    }
                    if(traits.contains(Trait.POLITE))polite++;
                    if(mob!=actor)mob.discard();
                }
                check(playful>0 && polite>0,"Both behavior traits occur naturally");total+=playful;
                System.out.println("PLAYFUL_PERSONALITY_SAMPLE: "+types.get(kind)+"; playful="+playful+"; polite="+polite+"; samples=128");
            }
            check(actor!=null,"A naturally Playful Dark Wolf exists");
            actor.setPos(player.position().add(1,0,0));level.addFreshEntity(actor);
            CreaturePersonality.establishRelationship(actor,player);CreaturePersonality.setFamiliarity(actor,player,18);
            var buffer=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                SocialInteractionMenu.writeOpeningData(buffer,player,actor,false,false);
                var menu=new SocialInteractionMenu(0,player.getInventory(),buffer);
                check(menu.getBehaviorTrait()==Trait.PLAYFUL && !menu.getDominantTrait().behaviorOnly(),
                        "Menu packet exposes Playful separately from the main personality");
            } finally {buffer.release();}
            var mask=actor.getPersistentData().getCompound("ChangedSynergyPersonality").copy();
            rules.getRule(ChangedSynergyGameRules.PERSONALITY_SYSTEM).set(false,server);
            check(!CreaturePersonality.has(actor,Trait.PLAYFUL),"Disabling the personality rule pauses traits");
            rules.getRule(ChangedSynergyGameRules.PERSONALITY_SYSTEM).set(true,server);
            check(CreaturePersonality.has(actor,Trait.PLAYFUL) && mask.equals(actor.getPersistentData().getCompound("ChangedSynergyPersonality")),
                    "Re-enabling the rule restores the same saved trait");
            check(PlayfulFriendTransfurGoal.eligible(actor,player) && PlayfulFriendTransfurGoal.beginAttempt(actor,player)
                    && PlayfulFriendTransfurGoal.active(actor,player),"A natural Playful friend can start its existing behavior");
            PlayfulFriendTransfurGoal.interrupt(actor,player,false);
            CreaturePersonality.setDominantTrait(actor,Trait.PLAYFUL);
            var saved=actor.saveWithoutId(new CompoundTag());var restored=ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);restored.load(saved);
            try {check(CreaturePersonality.dominantTrait(restored)==Trait.PLAYFUL,"Explicit primary Playful remains valid after reload");}
            finally {restored.discard();}
            System.out.println("PLAYFUL_PERSONALITY_REGRESSION_PASS: 512 deterministic fresh assignments; playful="+total+"; save/load, menu packet, rule pause/resume, actual attempt and explicit primary retention");
        } finally {
            if(actor!=null)actor.discard();player.discard();NativeOriginData.get(server).players.remove(player.getUUID());
            rules.getRule(ChangedSynergyGameRules.PERSONALITY_SYSTEM).set(enabled,server);
            rules.getRule(ChangedSynergyGameRules.NPC_AI).set(ai,server);
        }
    }
    private static void check(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
}
