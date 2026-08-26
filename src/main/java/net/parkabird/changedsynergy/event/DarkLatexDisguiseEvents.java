package net.parkabird.changedsynergy.event;

import java.util.Comparator;
import java.util.List;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.TamableLatexEntity;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.DarkLatexDisguise;
import net.parkabird.changedsynergy.ai.DarkLatexDisguise.Observation;
import net.parkabird.changedsynergy.ai.HuntMemory;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Observation, inspection and faction reactions for the dark-latex coat. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DarkLatexDisguiseEvents {
    private static final double OBSERVATION_RADIUS = 24.0D;
    private static final double DARK_GREETING_RADIUS_SQR = 14.0D * 14.0D;
    private static final double ALERT_RADIUS = 14.0D;

    private DarkLatexDisguiseEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 10 != 0
                || !player.isAlive()
                || player.isCreative()
                || player.isSpectator()
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        boolean impersonating = DarkLatexDisguise.isImpersonatingDarkLatex(player);
        boolean actuallyDark = DarkLatexDisguise.isActuallyDarkLatex(player);
        List<ChangedEntity> observers = level.getEntitiesOfClass(
                ChangedEntity.class,
                player.getBoundingBox().inflate(OBSERVATION_RADIUS),
                mob -> mob.isAlive() && LatexSocialMemory.isSocialLatex(mob));
        for (ChangedEntity mob : observers) {
            if (!mob.hasLineOfSight(player) || isProtectedPet(mob, player)) {
                continue;
            }

            if (DarkLatexDisguise.isDarkLatex(mob)) {
                if (impersonating && mob.distanceToSqr(player) <= DARK_GREETING_RADIUS_SQR) {
                    handleDarkObservation(mob, player);
                } else if (!actuallyDark
                        && DarkLatexDisguise.wasAccepted(mob, player)
                        && !DarkLatexDisguise.isRevealed(mob, player)
                        && mob.distanceToSqr(player) <= DARK_GREETING_RADIUS_SQR) {
                    revealAndAlert(mob, player, Cue.DISGUISE_DARK_DROPPED);
                }
                continue;
            }

            if (impersonating
                    && DarkLatexDisguise.appearsAsDarkRivalToWhite(mob, player)
                    && NpcDispositionEvents.canTarget(mob, player)) {
                if (DarkLatexDisguise.shouldNoticeWhiteRival(mob, player)) {
                    NpcDialogue.trigger(mob, player, Cue.DISGUISE_WHITE_RIVAL);
                }
                mob.setTarget(player);
            }
        }
    }

    private static void handleDarkObservation(ChangedEntity mob, ServerPlayer player) {
        Observation observation = DarkLatexDisguise.observeDark(mob, player);
        switch (observation) {
            case ACCEPTED -> {
                LatexSocialEvents.calmTowards(mob, player);
                NpcDialogue.trigger(mob, player, Cue.DISGUISE_DARK_ACCEPTED);
            }
            case INSPECTED_AND_ACCEPTED -> {
                LatexSocialEvents.calmTowards(mob, player);
                NpcDialogue.trigger(mob, player, Cue.DISGUISE_DARK_INSPECTED);
            }
            case REVEALED -> revealAndAlert(mob, player, Cue.DISGUISE_DARK_REVEALED);
            default -> {
            }
        }
    }

    /** Reveals an impostor to one observer and any nearby dark latex that saw the incident. */
    public static void revealAndAlert(
            ChangedEntity source,
            ServerPlayer player,
            Cue cue) {
        if (!(source.level() instanceof ServerLevel level)) {
            return;
        }

        List<ChangedEntity> witnesses = level.getEntitiesOfClass(
                        ChangedEntity.class,
                        source.getBoundingBox().inflate(ALERT_RADIUS),
                        mob -> mob.isAlive()
                                && DarkLatexDisguise.isDarkLatex(mob)
                                && !isProtectedPet(mob, player)
                                && (mob == source
                                        || mob.hasLineOfSight(player)
                                        || mob.hasLineOfSight(source)))
                .stream()
                .sorted(Comparator.comparingDouble(mob -> mob.distanceToSqr(source)))
                .limit(8)
                .toList();
        if (witnesses.isEmpty() && !isProtectedPet(source, player)) {
            witnesses = List.of(source);
        }

        boolean announced = false;
        for (ChangedEntity witness : witnesses) {
            DarkLatexDisguise.reveal(witness, player);
            LatexSocialMemory.clearTruce(witness, player);
            LatexSocialMemory.clearWarningGrace(witness, player);
            LatexSocialMemory.markProvoked(witness, player);
            HuntMemory.seeTarget(witness, player);
            witness.setTarget(player);
            if (!announced) {
                NpcDialogue.trigger(witness, player, cue);
                announced = true;
            } else {
                NpcDialogue.emoteOnly(witness, cue);
            }
        }
    }

    private static boolean isProtectedPet(ChangedEntity mob, ServerPlayer player) {
        return LatexSocialMemory.isBonded(mob, player)
                || LatexSocialMemory.isPetOwner(mob, player)
                || LatexSocialMemory.petOwnerUuid(mob).isPresent()
                || mob instanceof TamableLatexEntity tamable && tamable.isTame();
    }
}
