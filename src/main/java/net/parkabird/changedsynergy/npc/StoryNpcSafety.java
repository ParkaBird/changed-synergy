package net.parkabird.changedsynergy.npc;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision;
import net.ltxprogrammer.changed.process.TransfurEvents;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="changed_synergy")
public final class StoryNpcSafety {
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void fusion(TransfurEvents.LatexFusionDecisionEvent event) {
        if(event.getSourceEntity() instanceof ChangedEntity source&&StoryNpcService.keepsIdentityOnDeath(source)
                ||event.getTargetEntity() instanceof ChangedEntity target&&StoryNpcService.keepsIdentityOnDeath(target))event.setCanceled(true);
    }
    @SubscribeEvent public static void defaultEquipment(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if(event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity drop&&StoryNpcService.bound(drop.getItem()))event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void assimilation(TransfurEvents.LatexAssimilationDecisionEvent event) {
        if(event.getEntity() instanceof ChangedEntity target&&StoryNpcService.keepsIdentityOnDeath(target)){event.setCanceled(true);return;}
        if(event.getSourceEntity() instanceof ChangedEntity source&&StoryNpcService.isNpc(source))replicate(event);
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static void replicate(TransfurEvents.LatexAssimilationDecisionEvent event) {
        var d=event.getDecision();if(d==null||d.method()==LatexAssimilationDecision.Method.REPLICATION)return;
        event.setDecision(new LatexAssimilationDecision(d.decisionStrength(),LatexAssimilationDecision.Method.REPLICATION,d.transfurVariant(),d.context(),d.transfurProgress(),d.postTransfurListener()));
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){NpcConversations.clear();}
}
