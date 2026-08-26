package net.parkabird.changedsynergy.ai;

import java.util.Comparator;
import java.util.List;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.PacketDistributor;
import net.parkabird.changedsynergy.ai.CreatureLifeMemory.GroupRole;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.ScoutTargetPacket;

/** Small, observable abilities attached to stable community roles. */
public final class CreatureRoleService {
    private static final String NEXT_ROLE_LINE = "ChangedSynergyNextRoleLine";
    private static final double ROLE_RANGE = 10.0D;

    private CreatureRoleService() {
    }

    public static void tick(ChangedEntity creature) {
        if (!(creature.level() instanceof ServerLevel level)
                || !CreatureLifeMemory.enabled(creature)) {
            return;
        }
        GroupRole role = CreatureLifeMemory.role(creature);
        if (role == GroupRole.YOUNGSTER) {
            tickYoungster(creature, level);
            return;
        }
        if (creature.tickCount % 20 != Math.floorMod(
                creature.getId(), 20)) {
            return;
        }
        switch (role) {
            case SCOUT -> tickScout(creature, level);
            case GUARD -> tickGuard(creature, level);
            case PROVISIONER -> tickProvisioner(creature, level);
            case YOUNGSTER -> { }
        }
    }

    /** A stocked cache turns a caretaker's small comfort heal into real aid. */
    public static boolean aidDuringRoutine(
            ChangedEntity caretaker,
            ChangedEntity patient) {
        boolean stocked = CreatureLifeMemory.enabled(caretaker)
                && CreatureSettlementService.consumeFood(caretaker);
        patient.heal(stocked ? 3.0F : 1.0F);
        if (stocked) {
            CreatureLifeMemory.incrementRoleStat(caretaker, 2);
            present(caretaker, Cue.ROLE_CARETAKER_AID);
        }
        return stocked;
    }

    private static void tickScout(
            ChangedEntity creature,
            ServerLevel level) {
        LivingEntity target = creature.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        HunterFaction faction = HunterFaction.of(creature);
        for (ServerPlayer player : level.players()) {
            if (canSeeScoutMark(player, faction)) {
                ChangedSynergyNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new ScoutTargetPacket(target.getId()));
            }
        }
        boolean relayed = false;
        boolean rallied = false;
        for (ChangedEntity peer : peers(creature, level, 12.0D)) {
            if (peer.getTarget() == null) {
                peer.getLookControl().setLookAt(target, 32.0F, 28.0F);
                relayed = true;
            }
            if (peer.getTarget() == target) {
                peer.addEffect(new MobEffectInstance(
                        MobEffects.DAMAGE_BOOST, 35, 0, true, false, true));
                rallied = true;
            }
        }
        CreatureLifeMemory.incrementRoleStat(creature, 0);
        if (relayed) {
            CreatureLifeMemory.incrementRoleStat(creature, 1);
        }
        if (rallied) {
            CreatureLifeMemory.incrementRoleStat(creature, 2);
        }
        present(creature, Cue.ROLE_SCOUT_MARK);
    }

    private static boolean canSeeScoutMark(
            ServerPlayer player,
            HunterFaction scoutFaction) {
        if (!player.isAlive() || player.isSpectator()) {
            return false;
        }
        var variant = ProcessTransfur.getPlayerTransfurVariant(player);
        return variant != null
                && !variant.isTemporaryFromSuit()
                && HunterFaction.of(variant.getParent().getEntityType())
                        == scoutFaction;
    }

    private static void tickGuard(
            ChangedEntity creature,
            ServerLevel level) {
        if (creature.getTarget() == null) {
            return;
        }
        boolean protectedSomeone = false;
        for (ChangedEntity peer : peers(creature, level, 8.0D)) {
            peer.addEffect(new MobEffectInstance(
                    MobEffects.DAMAGE_RESISTANCE, 35, 0, true, false, true));
            protectedSomeone = true;
        }
        creature.addEffect(new MobEffectInstance(
                MobEffects.DAMAGE_RESISTANCE, 35, 0, true, false, true));
        if (protectedSomeone) {
            CreatureLifeMemory.incrementRoleStat(creature, 0);
            present(creature, Cue.ROLE_GUARD_RALLY);
        }
    }

    private static void tickProvisioner(
            ChangedEntity creature,
            ServerLevel level) {
        if (CreatureSettlementService.hasCargo(creature)) {
            creature.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SPEED, 35, 0, true, false, true));
        }
        if (creature.tickCount % 100 != Math.floorMod(creature.getId(), 100)
                || creature.getTarget() != null) {
            return;
        }
        ChangedEntity patient = peers(creature, level, ROLE_RANGE).stream()
                .filter(peer -> peer.getHealth() < peer.getMaxHealth() - 0.5F)
                .min(Comparator.comparingDouble(peer ->
                        peer.getHealth() / peer.getMaxHealth()))
                .orElse(null);
        if (patient != null && CreatureSettlementService.consumeFood(creature)) {
            patient.heal(3.0F);
            CreatureLifeMemory.incrementRoleStat(creature, 2);
            present(creature, Cue.ROLE_CARETAKER_AID);
        }
    }

    private static void tickYoungster(
            ChangedEntity creature,
            ServerLevel level) {
        if (creature.getTarget() == null
                || LatexSocialMemory.hasActiveBond(creature)
                || LatexSocialMemory.petOwnerUuid(creature).isPresent()) {
            return;
        }
        ChangedEntity adult = peers(creature, level, 16.0D).stream()
                .filter(peer -> CreatureLifeMemory.role(peer) != GroupRole.YOUNGSTER)
                .min(Comparator.comparingDouble(creature::distanceToSqr))
                .orElse(null);
        if (adult == null) {
            return;
        }
        creature.setTarget(null);
        HuntMemory.clear(creature);
        creature.getNavigation().moveTo(adult, 0.34D);
        CreatureLifeMemory.incrementRoleStat(creature, 0);
        present(creature, Cue.ROLE_YOUNGSTER_RETREAT);
    }

    private static List<ChangedEntity> peers(
            ChangedEntity creature,
            ServerLevel level,
            double radius) {
        return level.getEntitiesOfClass(
                ChangedEntity.class,
                creature.getBoundingBox().inflate(radius),
                peer -> peer != creature
                        && peer.isAlive()
                        && LatexSocialMemory.isSocialLatex(peer)
                        && CreatureCommunityData.sameCommunity(creature, peer));
    }

    private static void present(ChangedEntity creature, Cue cue) {
        if (!(creature.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (creature.getPersistentData().getLong(NEXT_ROLE_LINE) > now) {
            return;
        }
        ServerPlayer observer = level.players().stream()
                .filter(player -> player.isAlive()
                        && !player.isSpectator()
                        && player.distanceToSqr(creature) <= 18.0D * 18.0D)
                .min(Comparator.comparingDouble(creature::distanceToSqr))
                .orElse(null);
        if (observer != null) {
            NpcDialogue.trigger(creature, observer, cue);
        } else {
            NpcDialogue.emoteOnly(creature, cue);
        }
        creature.getPersistentData().putLong(
                NEXT_ROLE_LINE, now + 360L + creature.getRandom().nextInt(241));
    }
}
