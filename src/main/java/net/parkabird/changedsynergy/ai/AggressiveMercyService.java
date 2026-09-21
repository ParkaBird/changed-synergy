package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.Emote;
import net.ltxprogrammer.changed.process.TransfurEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;

/** A brief, personal hesitation from creatures that can listen but never befriend. */
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AggressiveMercyService {
    private static final String PLAYER = "ChangedSynergyAggressiveMercyPlayer";
    private static final String MERCY_UNTIL = "ChangedSynergyAggressiveMercyUntil";
    private static final String HESITATE_UNTIL = "ChangedSynergyAggressiveHesitateUntil";
    private static final String COOLDOWN_UNTIL = "ChangedSynergyAggressiveMercyCooldown";
    private static final long HESITATE_TICKS = 30L;
    private static final long MERCY_TICKS = 240L;
    private static final long COOLDOWN_TICKS = 600L;
    private static final float DAMAGE_MULTIPLIER = 0.65F;

    private AggressiveMercyService() {
    }

    public static void appeal(ChangedEntity creature, ServerPlayer player) {
        if (!CreatureSocialProfile.isAggressive(creature)
                || !creature.isAlive()
                || creature.level() != player.level()) {
            return;
        }
        long now = creature.level().getGameTime();
        var data = creature.getPersistentData();
        if (data.getLong(COOLDOWN_UNTIL) > now) {
            player.displayClientMessage(Component.translatable(
                    "message.changed_synergy.aggressive_mercy.refused",
                    creature.getDisplayName()), true);
            NpcDialogue.emoteOnly(creature, Emote.DENY);
            return;
        }

        data.putUUID(PLAYER, player.getUUID());
        data.putLong(HESITATE_UNTIL, now + HESITATE_TICKS);
        data.putLong(MERCY_UNTIL, now + MERCY_TICKS);
        data.putLong(COOLDOWN_UNTIL, now + COOLDOWN_TICKS);
        creature.getNavigation().stop();
        creature.getLookControl().setLookAt(player, 30.0F, 30.0F);
        NpcDialogue.emoteOnly(creature, Emote.CONFUSED);
        player.displayClientMessage(Component.translatable(
                "message.changed_synergy.aggressive_mercy.accepted",
                creature.getDisplayName()), true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getSource().getEntity() instanceof ChangedEntity creature
                && applies(creature, player, HESITATE_UNTIL)) {
            event.setCanceled(true);
            creature.getNavigation().stop();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getSource().getEntity() instanceof ChangedEntity creature
                && applies(creature, player, MERCY_UNTIL)) {
            event.setAmount(event.getAmount() * DAMAGE_MULTIPLIER);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onTransfurDamage(
            TransfurEvents.LatexAssimilationDecisionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getSourceEntity() instanceof ChangedEntity creature
                && event.getDecision() != null
                && applies(creature, player, MERCY_UNTIL)) {
            event.setDecision(event.getDecision().withTransfurProgress(
                    event.getDecision().transfurProgress()
                            * DAMAGE_MULTIPLIER));
        }
    }

    private static boolean applies(
            ChangedEntity creature,
            ServerPlayer player,
            String untilKey) {
        var data = creature.getPersistentData();
        return CreatureSocialProfile.isAggressive(creature)
                && data.hasUUID(PLAYER)
                && player.getUUID().equals(data.getUUID(PLAYER))
                && data.getLong(untilKey) > creature.level().getGameTime();
    }
}
