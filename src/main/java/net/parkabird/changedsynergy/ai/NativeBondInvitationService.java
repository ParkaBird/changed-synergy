package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.ability.GrabEntityAbility;
import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.ltxprogrammer.changed.init.ChangedAbilities;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.event.LatexSocialEvents;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.origin.NativeOriginService;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A native creature can choose a companion without changing either body. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class NativeBondInvitationService {
    private static final String CONFIRMATION = "ChangedSynergyNativeBondConfirmation";
    private static final long CONFIRMATION_TICKS = 200L;

    private NativeBondInvitationService() {}

    public static boolean canOffer(ChangedEntity creature, ServerPlayer player) {
        return unavailableReason(creature, player) == null;
    }

    /** As with voluntary transfur, a second deliberate selection confirms the invitation. */
    public static boolean select(ChangedEntity creature, ServerPlayer player) {
        String reason = unavailableReason(creature, player);
        if (reason != null) {
            player.getPersistentData().remove(CONFIRMATION);
            player.displayClientMessage(Component.translatable(
                    "message.changed_synergy.social.native_bond_" + reason), true);
            if (creature.level() == player.level() && player.distanceToSqr(creature) <= 64.0D
                    && player.hasLineOfSight(creature))
                NpcDialogue.context(creature, player, "native_bond.refused." + tone(creature));
            return false;
        }

        long now = player.level().getGameTime();
        String form = ProcessTransfur.getPlayerTransfurVariant(player).getParent().getFormId().toString();
        CompoundTag confirmation = player.getPersistentData().getCompound(CONFIRMATION);
        boolean confirmed = confirmation.hasUUID("Target")
                && creature.getUUID().equals(confirmation.getUUID("Target"))
                && form.equals(confirmation.getString("Form"))
                && confirmation.getLong("Expires") >= now;
        creature.getNavigation().stop();
        creature.getLookControl().setLookAt(player, 30.0F, 30.0F);
        if (!confirmed) {
            confirmation.putUUID("Target", creature.getUUID());
            confirmation.putString("Form", form);
            confirmation.putLong("Expires", now + CONFIRMATION_TICKS);
            player.getPersistentData().put(CONFIRMATION, confirmation);
            player.displayClientMessage(Component.translatable(
                    "message.changed_synergy.social.native_bond_confirm",
                    creature.getDisplayName()), true);
            NpcDialogue.context(creature, player, "native_bond.confirm." + tone(creature));
            return false;
        }

        player.getPersistentData().remove(CONFIRMATION);
        LatexSocialMemory.addBond(creature, player);
        if (!LatexSocialMemory.isBonded(creature, player)) return false;
        net.parkabird.changedsynergy.advancement.SynergyAdvancements.grant(player,
                net.parkabird.changedsynergy.advancement.SynergyAdvancements.NATIVE_BOND);
        LatexSocialEvents.calmTowards(creature, player);
        ((ServerLevel) creature.level()).sendParticles(ParticleTypes.HEART,
                creature.getX(), creature.getY(0.78D), creature.getZ(),
                7, 0.3D, 0.28D, 0.3D, 0.02D);
        player.displayClientMessage(Component.translatable(
                "message.changed_synergy.social.native_bond_complete",
                creature.getDisplayName()), true);
        NpcDialogue.context(creature, player, "native_bond.accepted." + tone(creature));
        player.closeContainer();
        return true;
    }

    private static String unavailableReason(ChangedEntity creature, ServerPlayer player) {
        var form = ProcessTransfur.getPlayerTransfurVariant(player);
        if (!(NativeOriginService.isNative(player) || net.parkabird.changedsynergy.compat.TrueTransfurCompat.isPermanentPlayer(player))
                || form == null || form.isTemporaryFromSuit()
                || !CreatureSocialProfile.allowsPersonalRelationship(form.getChangedEntity())) return "native_only";
        if (!player.isAlive() || player.isSpectator() || NativeOriginService.pending(player)
                || TakeoverService.active(player) || HypnosisQteService.isPlayerControlLocked(player)
                || !creature.isAlive() || creature.isRemoved() || creature.level() != player.level()
                || player.distanceToSqr(creature) > 64.0D || !player.hasLineOfSight(creature)
                || TakeoverService.carrying(creature)
                || GrabEntityAbility.getGrabber(player) != null || GrabEntityAbility.getGrabber(creature) != null
                || holding(IAbstractChangedEntity.forPlayer(player)) || holding(IAbstractChangedEntity.forEntity(creature))
                || !LatexSocialMemory.isSocialLatex(creature)
                || !CreatureSocialProfile.allowsPersonalRelationship(creature)
                || LatexSocialMemory.isProvoked(creature, player)
                || LatexSocialMemory.hasBetrayedPatTruce(creature, player)
                || FactionReputation.isHostile(creature, player)) return "unavailable";
        if (!CreaturePersonality.hasTrustedRelationship(creature, player)
                || CreaturePersonality.relationshipTier(creature, player)
                        != CreaturePersonality.RelationshipTier.CLOSE) return "requires_close";
        if (!ChangedSynergyGameRules.enabled(player.level(), ChangedSynergyGameRules.BOND_SYSTEM)) return "disabled";
        if (LatexSocialMemory.hasActiveBond(creature)
                || LatexSocialMemory.petOwnerUuid(creature).filter(owner -> !owner.equals(player.getUUID())).isPresent())
            return "already_bonded";
        if (!ChangedSynergyConfig.COMMON.allowMultipleBonds.get()
                && !LatexSocialMemory.bondedCreatureUuids(player).isEmpty()) return "bond_limit";
        if (!BondTokenService.ready(creature, player)) return "requires_token";
        return null;
    }

    private static boolean holding(IAbstractChangedEntity participant) {
        return participant.getAbilityInstanceSafe(ChangedAbilities.GRAB_ENTITY_ABILITY.get())
                .map(ability -> ability.grabbedEntity != null).orElse(false);
    }

    @SubscribeEvent public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        event.getEntity().getPersistentData().remove(CONFIRMATION);
    }

    @SubscribeEvent public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) player.getPersistentData().remove(CONFIRMATION);
    }

    @SubscribeEvent public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        event.getEntity().getPersistentData().remove(CONFIRMATION);
    }

    private static String tone(ChangedEntity creature) {
        return switch (CreaturePersonality.dominantTrait(creature)) {
            case CAUTIOUS, CALM, SENSITIVE -> "reserved";
            case PROTECTIVE, PLAYFUL, POLITE -> "warm";
            case CURIOUS, SHOW_OFF, COMPETITIVE -> "bold";
        };
    }
}
