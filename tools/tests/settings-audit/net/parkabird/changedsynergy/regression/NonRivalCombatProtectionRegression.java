package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.LatexCreatureCombatRules;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.event.LatexSocialEvents;
import net.parkabird.changedsynergy.network.ServerConfigSnapshotPacket;

final class NonRivalCombatProtectionRegression {
    static void run(MinecraftServer server)throws Exception {
        var config=ChangedSynergyConfig.COMMON.nonRivalCombatProtection;
        boolean previous=config.get();
        var level=server.overworld();
        var target=ChangedEntities.GAS_WOLF_MALE.get().create(level);
        var attacker=ChangedEntities.POOLTOY_WOLF.get().create(level);
        var compatriot=ChangedEntities.GAS_WOLF_MALE.get().create(level);
        var owner=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"CombatSafety"));
        owner.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),owner);
        try {
            check(config.getDefault(),"Existing worlds keep non-rival protection by default");
            check(!LatexCreatureCombatRules.areRivals(attacker,target)
                    && !LatexCreatureCombatRules.areCompatriots(attacker,target),"Fixture pair is non-rival and not compatriot");
            set(server,true);
            target.setLastHurtByMob(attacker);attacker.setLastHurtByMob(target);
            float floor=Math.max(1,target.getMaxHealth()*.2F);
            check(Math.abs(damage(target,attacker,1000)-(target.getHealth()-floor))<.001F,
                    "Enabled protection keeps the ordinary safety floor on the real damage-event path");
            check(target.getLastHurtByMob()==null && attacker.getLastHurtByMob()==null,"Enabled protection ends both sides' combat memory");
            LatexCreatureCombatRules.flushPendingDisengagements(server);
            set(server,false);
            check(ServerConfigSnapshotPacket.capture().getString("RELATIONSHIPS.NonRivalCombatProtection").equals("false"),
                    "Remote clients receive the authoritative disabled value");
            target.setLastHurtByMob(attacker);attacker.setLastHurtByMob(target);
            check(damage(target,attacker,1000)==1000 && target.getLastHurtByMob()==attacker && attacker.getLastHurtByMob()==target,
                    "Disabled protection permits lethal damage and does not force disengagement");
            check(damage(target,compatriot,1000)==0,"Compatriot friendly-fire prevention stays independent");
            LatexSocialMemory.addBond(target,owner);
            float bondedFloor=target.getMaxHealth()*LatexCreatureCombatRules.BONDED_SAFETY_HEALTH_RATIO;
            check(Math.abs(damage(target,attacker,1000)-(target.getHealth()-bondedFloor))<.001F,
                    "Bonded-companion safety stays independent of this switch");
            set(server,true);
            check(ServerConfigSnapshotPacket.capture().getString("RELATIONSHIPS.NonRivalCombatProtection").equals("true"),
                    "Protection can be re-enabled live without restarting");
            System.out.println("NON_RIVAL_COMBAT_CONFIG_REGRESSION_PASS: default, live operator toggle, lethal damage, disengagement, compatriot/bond isolation and remote snapshot");
        } finally {
            config.set(previous);target.discard();attacker.discard();compatriot.discard();owner.discard();
            LatexCreatureCombatRules.flushPendingDisengagements(server);
        }
    }
    private static float damage(ChangedEntity target,ChangedEntity attacker,float amount) {
        var event=new LivingDamageEvent(target,target.damageSources().mobAttack(attacker),amount);
        LatexSocialEvents.onMonsterDamagesBondedCreature(event);return event.getAmount();
    }
    private static void set(MinecraftServer server,boolean value)throws Exception {
        check(server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4),
                "changedsynergy config set RELATIONSHIPS.NonRivalCombatProtection "+value)==1,"Operator command applies protection="+value);
    }
    private static void check(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
        System.out.println("NON_RIVAL_COMBAT_CONFIG_CHECK: "+message);
    }
}
