package net.parkabird.changedsynergy.ai;

import java.util.EnumSet;
import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.ability.GrabEntityAbility;
import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.network.packet.GrabEntityPacket;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraftforge.network.PacketDistributor;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import net.parkabird.changedsynergy.event.LatexSocialEvents;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.origin.NativeOriginService;

/** A bounded, consensually interruptible prank; friendship itself never becomes hostility. */
public final class PlayfulFriendTransfurGoal extends Goal {
    private static final String ROOT = "ChangedSynergyPlayfulFriend";
    private final ChangedEntity mob;
    private ServerPlayer player;
    private GrabEntityAbilityInstance ability;
    private int repath;
    private boolean grabbed;

    public PlayfulFriendTransfurGoal(ChangedEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private static CompoundTag state(ChangedEntity mob) {
        if (!mob.getPersistentData().contains(ROOT)) mob.getPersistentData().put(ROOT, new CompoundTag());
        return mob.getPersistentData().getCompound(ROOT);
    }

    public static boolean active(ChangedEntity mob, ServerPlayer player) {
        CompoundTag data = mob.getPersistentData().getCompound(ROOT);
        return ChangedSynergyConfig.COMMON.playfulFriendTransfur.get()
                && ChangedSynergyGameRules.enabled(mob.level(), ChangedSynergyGameRules.NPC_AI)
                && CreaturePersonality.hasTrustedRelationship(mob, player)
                && CreaturePersonality.has(mob, CreaturePersonality.Trait.PLAYFUL)
                && !CreaturePersonality.has(mob, CreaturePersonality.Trait.POLITE)
                && data.hasUUID("Player") && player.getUUID().equals(data.getUUID("Player"))
                && data.getLong("Until") > CreaturePersonality.now(mob)
                && !TrueTransfurCompat.isInstinctShell(player) && !NativeOriginService.isNative(player)
                && !ProcessTransfur.isPlayerTransfurred(player);
    }

    public static boolean isDefense(ChangedEntity mob, ServerPlayer player) {
        CompoundTag data = mob.getPersistentData().getCompound(ROOT);
        return active(mob, player) || data.hasUUID("DefensePlayer")
                && player.getUUID().equals(data.getUUID("DefensePlayer"))
                && data.getLong("DefenseAt") == CreaturePersonality.now(mob);
    }

    public static double chance(int affection) { return Math.max(0, 0.25D * (28 - Math.max(0, affection)) / 28.0D); }

    public static boolean eligible(ChangedEntity mob, ServerPlayer player) {
        var hold = GrabEntityAbility.getGrabber(player);
        return ChangedSynergyConfig.COMMON.playfulFriendTransfur.get()
                && ChangedSynergyGameRules.enabled(mob.level(), ChangedSynergyGameRules.NPC_AI)
                && CreatureSocialProfile.allowsPersonalRelationship(mob)
                && !CreatureSocialProfile.isGrabMechanicExcluded(mob)
                && !LatexSocialMemory.isOrganic(mob) && !LatexSocialMemory.hasActiveBond(mob)
                && !LatexSocialMemory.isPetOwner(mob, player)
                && CreaturePersonality.has(mob, CreaturePersonality.Trait.PLAYFUL)
                && !CreaturePersonality.has(mob, CreaturePersonality.Trait.POLITE)
                && CreaturePersonality.hasTrustedRelationship(mob, player)
                && CreaturePersonality.familiarity(mob, player) < 28
                && mob.isAlive() && !mob.isRemoved() && mob.getTarget() == null
                && !PlayerOutpostService.isWorking(mob)
                && !mob.isSleeping() && !mob.isPassenger() && !mob.isVehicle()
                && !ChangedAddonCompat.isGrabberBusy(mob) && !TakeoverService.carrying(mob)
                && player.isAlive() && !player.isCreative() && !player.isSpectator()
                && player.level() == mob.level() && mob.distanceToSqr(player) <= 36 && mob.hasLineOfSight(player)
                && !TrueTransfurCompat.isInstinctShell(player) && !NativeOriginService.pending(player)
                && !NativeOriginService.isNative(player) && !ProcessTransfur.isPlayerTransfurred(player)
                && hold == null && !TakeoverService.active(player) && !HypnosisQteService.isPlayerControlLocked(player)
                && !BondedSuitService.isRescueGraceActive(player) && !GrabEscapeStunService.isEscapeProtected(player)
                && !LatexSocialMemory.isGrabTemporarilyBlocked(mob, player) && !FactionHostilityGrace.active(mob, player)
                && player.getHealth() >= player.getMaxHealth() * 0.7F
                && ProcessTransfur.getPlayerTransfurProgress(player) <= ProcessTransfur.getEntityTransfurTolerance(player) * 0.15F
                && (player.getLastHurtByMob() == null || player.tickCount - player.getLastHurtByMobTimestamp() > 100)
                && (player.getLastHurtMob() == null || player.tickCount - player.getLastHurtMobTimestamp() > 100)
                && state(mob).getCompound("NextAttempts").getLong(player.getUUID().toString()) <= CreaturePersonality.now(mob);
    }

    @Override public boolean canUse() {
        if (!ChangedSynergyConfig.COMMON.playfulFriendTransfur.get() || !CreatureSocialProfile.allowsPersonalRelationship(mob)
                || !CreaturePersonality.has(mob, CreaturePersonality.Trait.PLAYFUL)
                || CreaturePersonality.has(mob, CreaturePersonality.Trait.POLITE)) return false;
        long now = CreaturePersonality.now(mob);
        if (state(mob).getLong("NextCheck") > now) return false;
        state(mob).putLong("NextCheck", now + 1200);
        player = null;
        for (var nearby : mob.level().players()) {
            if (nearby instanceof ServerPlayer candidate && eligible(mob, candidate)
                    && mob.getRandom().nextDouble() < chance(CreaturePersonality.familiarity(mob, candidate))) {
                player = candidate;
                return true;
            }
        }
        return false;
    }

    /** Kept separate from the random scheduling so server regression can exercise a real attempt. */
    public static boolean beginAttempt(ChangedEntity mob, ServerPlayer player) {
        if (!eligible(mob, player)) return false;
        CompoundTag data = state(mob);
        CompoundTag next = data.getCompound("NextAttempts");
        for (String id : new java.util.ArrayList<>(next.getAllKeys()))
            if (next.getLong(id) <= CreaturePersonality.now(mob)) next.remove(id);
        next.putLong(player.getUUID().toString(), CreaturePersonality.now(mob) + 6000);
        data.put("NextAttempts", next);
        data.putUUID("Player", player.getUUID());
        data.putLong("Until", CreaturePersonality.now(mob) + 400);
        return true;
    }

    @Override public void start() {
        if (player == null || !beginAttempt(mob, player)) { player = null; return; }
        ability = BondedSuitService.ability(mob);
        grabbed = false;
        repath = 0;
    }

    @Override public boolean canContinueToUse() {
        return player != null && player.isAlive() && player.level() == mob.level() && active(mob, player)
                && CreaturePersonality.familiarity(mob, player) < 28 && !TakeoverService.active(player)
                && mob.getTarget() == null && player.distanceToSqr(mob) <= 100
                && player.getHealth() >= player.getMaxHealth() * 0.7F
                && (player.getLastHurtByMob() == null || player.getLastHurtByMob() == mob
                    || player.tickCount - player.getLastHurtByMobTimestamp() > 100)
                && (ability == null || ability.grabbedEntity == null || ability.grabbedEntity == player)
                && (!grabbed || ability != null && ability.grabbedEntity == player);
    }

    @Override public void tick() {
        if (player == null) return;
        mob.getLookControl().setLookAt(player, 30, 30);
        if (mob.distanceToSqr(player) > 4) {
            if (--repath <= 0) { repath = 20; mob.getNavigation().moveTo(player, 0.55D); }
            return;
        }
        mob.getNavigation().stop();
        if (ability != null) {
            if (!grabbed) {
                if (GrabEntityAbility.getGrabber(player) != null || !ability.grabEntity(player)) { stop(); return; }
                grabbed = true;
                Changed.PACKET_HANDLER.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> mob),
                        new GrabEntityPacket(mob, player, GrabEntityPacket.GrabType.ARMS));
            }
            // Addon's normal pulse owns its ability tick. Native NPCs need that tick here.
            if (ChangedAddonCompat.grabAbility(mob) == null) {
                ability.attackDown = true;
                ability.tickIdle();
            }
        } else if (--repath <= 0) {
            repath = 20;
            mob.doHurtTarget(player);
        }
    }

    public static void interrupt(ChangedEntity mob, ServerPlayer player, boolean warning) {
        if (warning && active(mob, player)) {
            net.parkabird.changedsynergy.advancement.SynergyAdvancements.grant(player,
                    net.parkabird.changedsynergy.advancement.SynergyAdvancements.PLAYFUL_WARNING);
        }
        CompoundTag data = state(mob);
        data.putUUID("DefensePlayer", player.getUUID());
        data.putLong("DefenseAt", CreaturePersonality.now(mob));
        data.remove("Until");
        CompoundTag next = data.getCompound("NextAttempts");
        next.putLong(player.getUUID().toString(), CreaturePersonality.now(mob) + 6000);
        data.put("NextAttempts", next);
        var grab = BondedSuitService.ability(mob);
        if (grab != null && grab.grabbedEntity == player && !grab.suited) grab.releaseEntity(false);
        LatexSocialEvents.calmTowards(mob, player);
        LatexSocialMemory.blockGrabAgainst(mob, player, 6000);
    }

    @Override public void stop() {
        if (player != null) {
            state(mob).remove("Until");
            if (ability != null && ability.grabbedEntity == player && !ability.suited) ability.releaseEntity(false);
        }
        mob.getNavigation().stop();
        player = null;
        ability = null;
    }
}
