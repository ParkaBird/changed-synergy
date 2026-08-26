package net.parkabird.changedsynergy.event;

import java.util.Comparator;
import java.util.List;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.advancement.SynergyAdvancements;
import net.parkabird.changedsynergy.ai.CreaturePersonality;
import net.parkabird.changedsynergy.ai.BondedOwnerDefenseGoal;
import net.parkabird.changedsynergy.ai.HunterFaction;
import net.parkabird.changedsynergy.ai.LatexCreatureCombatRules;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.dialogue.LatexTerritory;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.WhiteHiveTargetsPacket;
import net.parkabird.changedsynergy.util.PureWhiteVision;

/** Territory defense and target sharing for original pure-white forms. */
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PureWhiteHiveEvents {
    private static final double SUPPORT_RANGE = 48.0D;
    private static final double TARGET_SHARE_RANGE = 56.0D;
    private static final int TERRITORY_EDGE_RANGE = 20;
    private static final long DIALOGUE_COOLDOWN = 240L;
    private static final String DIALOGUE_LOCK =
            "ChangedSynergyWhiteHiveCombatDialogueUntil";

    private PureWhiteHiveEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPureWhitePlayerDamaged(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || event.getAmount() <= 0.0F
                || !PureWhiteVision.isPureWhiteForm(
                        ProcessTransfur.getPlayerTransfurVariant(player))
                || !LatexTerritory.isInOrNearFactionTerritory(
                        level, player.blockPosition(),
                        HunterFaction.WHITE, TERRITORY_EDGE_RANGE)) {
            return;
        }

        LivingEntity threat = event.getSource().getEntity()
                        instanceof LivingEntity source
                ? source
                : event.getSource().getDirectEntity()
                        instanceof LivingEntity direct
                        ? direct
                        : player.getLastHurtByMob();
        if (threat == null || threat == player || !threat.isAlive()) {
            return;
        }

        List<ChangedEntity> responders = level.getEntitiesOfClass(
                        ChangedEntity.class,
                        player.getBoundingBox().inflate(SUPPORT_RANGE),
                        creature -> eligibleResponder(
                                creature, player, threat))
                .stream()
                .filter(creature -> LatexTerritory
                        .isInOrNearFactionTerritory(
                                level, creature.blockPosition(),
                                HunterFaction.WHITE, 8))
                .sorted(Comparator.comparingDouble(
                        creature -> creature.distanceToSqr(player)))
                .toList();
        if (responders.isEmpty()) {
            return;
        }

        for (ChangedEntity responder : responders) {
            LatexSocialMemory.authorizePetDefense(
                    responder, threat, 300L);
            responder.setTarget(threat);
            responder.setAggressive(true);
        }
        SynergyAdvancements.grant(
                player, SynergyAdvancements.WHITE_HIVE_CONSENSUS);

        long now = level.getGameTime();
        if (hasActiveDialogueLock(player, now)) {
            return;
        }
        ChangedEntity speaker = responders.get(0);
        if (NpcDialogue.trigger(
                speaker, player,
                NpcDialogue.Cue.WHITE_TERRITORY_ASSIST)) {
            lockDialogue(player, now);
            responders.forEach(creature -> lockDialogue(creature, now));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onWhiteCreatureDamaged(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ChangedEntity victim)
                || HunterFaction.of(victim) != HunterFaction.WHITE
                || !(victim.level() instanceof ServerLevel level)
                || event.getAmount() <= 0.0F
                || event.getAmount() >= victim.getHealth()
                || !(event.getSource().getEntity() instanceof LivingEntity)) {
            return;
        }
        long now = level.getGameTime();
        if (hasActiveDialogueLock(victim, now)) {
            return;
        }
        ServerPlayer listener = level.players().stream()
                .filter(player -> player.isAlive() && !player.isSpectator())
                .filter(player -> player.distanceToSqr(victim) <= 32.0D * 32.0D)
                .filter(player -> !hasActiveDialogueLock(player, now))
                .min(Comparator.comparingDouble(victim::distanceToSqr))
                .orElse(null);
        if (listener == null) {
            return;
        }
        if (NpcDialogue.trigger(
                victim, listener,
                NpcDialogue.Cue.WHITE_HIVE_HURT)) {
            lockDialogue(victim, now);
            lockDialogue(listener, now);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 10 != 0) {
            return;
        }
        List<Integer> targets = List.of();
        if (PureWhiteVision.isPureWhiteForm(
                ProcessTransfur.getPlayerTransfurVariant(player))) {
            AABB area = player.getBoundingBox().inflate(TARGET_SHARE_RANGE);
            targets = player.level().getEntitiesOfClass(
                            ChangedEntity.class,
                            area,
                            creature -> creature.isAlive()
                                    && HunterFaction.of(creature)
                                            == HunterFaction.WHITE
                                    && creature.getTarget() != null
                                    && creature.getTarget().isAlive()
                                    && creature.getTarget() != player
                                    && creature.getTarget().distanceToSqr(player)
                                            <= TARGET_SHARE_RANGE
                                                    * TARGET_SHARE_RANGE)
                    .stream()
                    .map(ChangedEntity::getTarget)
                    .distinct()
                    .limit(64)
                    .map(LivingEntity::getId)
                    .toList();
        }
        List<Integer> synchronizedTargets = targets;
        ChangedSynergyNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WhiteHiveTargetsPacket(synchronizedTargets));
    }

    public static boolean hasActiveDialogueLock(
            LivingEntity entity,
            long now) {
        return entity.getPersistentData().getLong(DIALOGUE_LOCK) > now;
    }

    private static void lockDialogue(LivingEntity entity, long now) {
        entity.getPersistentData().putLong(
                DIALOGUE_LOCK, now + DIALOGUE_COOLDOWN);
    }

    private static boolean eligibleResponder(
            ChangedEntity creature,
            ServerPlayer protectedPlayer,
            LivingEntity threat) {
        if (!creature.isAlive()
                || HunterFaction.of(creature) != HunterFaction.WHITE
                || !HuntAIEvents.isEligibleHunter(creature)
                || creature == threat
                || creature.isAlliedTo(threat)
                || !creature.canAttack(threat)) {
            return false;
        }
        if (LatexSocialMemory.hasActiveBond(creature)
                && (!LatexSocialMemory.isPetOwner(
                                creature, protectedPlayer)
                        || !BondedOwnerDefenseGoal.allowsConfiguredDefense(
                                creature, protectedPlayer, threat))) {
            return false;
        }
        if (threat instanceof Player player
                && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        if (threat instanceof ServerPlayer player
                && (LatexSocialMemory.isBonded(creature, player)
                        || LatexSocialMemory.isPetOwner(creature, player)
                        || CreaturePersonality.hasTrustedRelationship(
                                creature, player))) {
            return false;
        }
        return !(threat instanceof ChangedEntity other)
                || !LatexCreatureCombatRules.areCompatriots(
                        creature, other)
                        && !LatexSocialMemory.isBonded(
                                other, protectedPlayer)
                        && !LatexSocialMemory.isPetOwner(
                                other, protectedPlayer)
                        && !CreaturePersonality.hasTrustedRelationship(
                                other, protectedPlayer);
    }
}
