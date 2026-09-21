package net.parkabird.changedsynergy.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.CreaturePersonality;
import net.parkabird.changedsynergy.ai.FactionPursuitService;
import net.parkabird.changedsynergy.ai.LatexCreatureCombatRules;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.ai.SafeEntityMutationQueue;
import net.parkabird.changedsynergy.ai.SocialAudienceGoal;
import net.parkabird.changedsynergy.ai.TakeoverService;
import net.parkabird.changedsynergy.event.HuntAIEvents;
import net.parkabird.changedsynergy.event.LatexSocialEvents;
import net.parkabird.changedsynergy.event.NpcDispositionEvents;

/**
 * Optional arbitration for Changed Extras' smart-latex AI.
 *
 * <p>Changed Extras deliberately replaces native Changed goals and drives
 * navigation from its own per-tick brain. Synergy leaves that brain in charge
 * of unrelated wild creatures, but restores the native goal set when a
 * creature enters a relationship, audience, takeover, or pursuit state owned
 * by Synergy.</p>
 */
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChangedExtrasCompat {
    public static final String MOD_ID = "changedextras";
    private static final String GAME_RULES_CLASS =
            "com.katt.changedextras.common.ChangedExtrasGameRules";
    private static final String AI_HANDLER_CLASS =
            "com.katt.changedextras.common.ai.LatexMobAIHandler";
    private static final String MIND_STORE_CLASS =
            "com.katt.changedextras.common.ai.LatexMindStore";

    private static boolean ruleResolved;
    private static boolean handlerResolved;
    private static boolean resolutionFailureLogged;
    @Nullable
    private static GameRules.Key<GameRules.BooleanValue> smartAiRule;
    @Nullable
    private static Field installedMobsField;
    @Nullable
    private static Method restoreNativeGoals;
    @Nullable
    private static Method forgetMind;

    private ChangedExtrasCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /** True while Changed Extras' opt-in smart AI gamerule is enabled. */
    public static boolean smartAiEnabled(ChangedEntity mob) {
        if (!isLoaded() || mob == null) {
            return false;
        }
        resolveSmartAiRule();
        return smartAiRule != null
                && mob.level().getGameRules().getBoolean(smartAiRule);
    }

    /**
     * States whose movement and target lifecycle must remain under Synergy's
     * control rather than the independent Changed Extras brain.
     */
    public static boolean shouldYieldSmartAi(ChangedEntity mob) {
        return mob != null
                && (LatexSocialMemory.hasActiveBond(mob)
                        || LatexSocialMemory.petOwnerUuid(mob).isPresent()
                        || CreaturePersonality.hasAnyEstablishedRelationship(mob)
                        || SocialAudienceGoal.isActive(mob)
                        || TakeoverService.carrying(mob)
                        || FactionPursuitService.isPursuer(mob));
    }

    /** Wild AI is owned by Changed Extras only outside Synergy-managed states. */
    public static boolean ownsWildAi(ChangedEntity mob) {
        return smartAiEnabled(mob) && !shouldYieldSmartAi(mob);
    }

    /** Shared by optional brain mixins so its target scan respects Synergy. */
    public static boolean rejectsTarget(
            ChangedEntity mob,
            LivingEntity target) {
        if (mob == null || target == null) {
            return false;
        }
        if (target instanceof ServerPlayer player) {
            return NpcDispositionEvents.mustRejectTarget(mob, player);
        }
        return target instanceof ChangedEntity other
                && LatexCreatureCombatRules.mustRejectTarget(mob, other);
    }

    /** Restores goals after an already-running creature becomes a companion. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ChangedEntity mob)
                || mob.level().isClientSide
                || !smartAiEnabled(mob)
                || !shouldYieldSmartAi(mob)
                || !externalAiInstalled(mob)) {
            return;
        }
        SafeEntityMutationQueue.queue(
                mob,
                "changed-extras-ai-yield",
                () -> restoreSynergyOwnedGoals(mob));
    }

    public static void logIntegration() {
        ModList.get().getModContainerById(MOD_ID).ifPresent(container ->
                ChangedSynergyMod.LOGGER.info(
                        "Enabled Changed Extras {} smart-AI arbitration",
                        container.getModInfo().getVersion()));
    }

    @SuppressWarnings("unchecked")
    private static synchronized void resolveSmartAiRule() {
        if (ruleResolved) {
            return;
        }
        ruleResolved = true;
        try {
            Class<?> rules = Class.forName(
                    GAME_RULES_CLASS,
                    false,
                    ChangedExtrasCompat.class.getClassLoader());
            Object value = rules.getField("SMART_LATEX_AI_ENABLED").get(null);
            smartAiRule = (GameRules.Key<GameRules.BooleanValue>)value;
        } catch (ReflectiveOperationException | LinkageError exception) {
            logResolutionFailure("gamerule", exception);
        }
    }

    private static synchronized boolean resolveHandler() {
        if (handlerResolved) {
            return installedMobsField != null
                    && restoreNativeGoals != null
                    && forgetMind != null;
        }
        handlerResolved = true;
        try {
            ClassLoader loader = ChangedExtrasCompat.class.getClassLoader();
            Class<?> handler = Class.forName(AI_HANDLER_CLASS, false, loader);
            installedMobsField = handler.getDeclaredField("INSTALLED_MOBS");
            installedMobsField.setAccessible(true);
            restoreNativeGoals = handler.getDeclaredMethod(
                    "restoreNativeGoals", ChangedEntity.class);
            restoreNativeGoals.setAccessible(true);
            Class<?> minds = Class.forName(MIND_STORE_CLASS, false, loader);
            forgetMind = minds.getMethod("forget", LivingEntity.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            logResolutionFailure("AI handler", exception);
            return false;
        }
    }

    private static boolean externalAiInstalled(ChangedEntity mob) {
        if (!resolveHandler()) {
            return false;
        }
        try {
            Object value = installedMobsField.get(null);
            return value instanceof Set<?> installed && installed.contains(mob);
        } catch (ReflectiveOperationException | LinkageError exception) {
            logResolutionFailure("installed-creature registry", exception);
            return false;
        }
    }

    private static void restoreSynergyOwnedGoals(ChangedEntity mob) {
        if (mob.isRemoved() || !smartAiEnabled(mob)
                || !shouldYieldSmartAi(mob) || !resolveHandler()) {
            return;
        }
        try {
            Object value = installedMobsField.get(null);
            if (!(value instanceof Set<?> installed) || !installed.remove(mob)) {
                return;
            }
            forgetMind.invoke(null, mob);
            restoreNativeGoals.invoke(null, mob);
            mob.setTarget(null);
            LatexSocialEvents.ensureSocialGoals(mob);
            HuntAIEvents.ensureHuntGoals(mob);
            if (FactionPursuitService.isPursuer(mob)) {
                FactionPursuitService.ensurePursuitGoal(mob);
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            logResolutionFailure("goal restoration", exception);
        }
    }

    private static void logResolutionFailure(
            String part,
            Throwable exception) {
        if (resolutionFailureLogged) {
            return;
        }
        resolutionFailureLogged = true;
        ChangedSynergyMod.LOGGER.error(
                "Changed Extras compatibility could not resolve its {}",
                part,
                exception);
    }
}
