package net.parkabird.changedsynergy.ai;

import java.util.Locale;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.TamableLatexEntity;
import net.ltxprogrammer.changed.entity.TransfurCause;
import net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision;
import net.ltxprogrammer.changed.entity.variant.TransfurVariant;
import net.ltxprogrammer.changed.init.ChangedParticles;
import net.ltxprogrammer.changed.init.ChangedSounds;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.ltxprogrammer.changed.process.TransfurEvents;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.network.PacketDistributor;
import net.parkabird.changedsynergy.ai.CreaturePersonality.Trait;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue;
import net.parkabird.changedsynergy.event.LatexSocialEvents;
import net.parkabird.changedsynergy.network.AbsorptionNegotiationStatePacket;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;

/**
 * Remembers the individual responsible for an involuntary latex transfur and
 * lets the player negotiate a release with that same individual later.
 * Organic assimilation and voluntary companion interactions are rejected at
 * capture time, rather than being special-cased during release.
 */
public final class InvoluntaryTransfurNegotiation {
    public static final String SOURCE_ROOT = "ChangedSynergyNegotiationSource";
    public static final String RELEASE_ROOT = "ChangedSynergyNegotiatedRelease";
    private static final String PLAYER_ROOT = "ChangedSynergyTransfurNegotiation";
    private static final String SOURCE_UUID = "SourceUuid";
    private static final String SOURCE_DIMENSION = "SourceDimension";
    private static final String SOURCE_TYPE = "SourceType";
    private static final String SOURCE_X = "SourceX";
    private static final String SOURCE_Y = "SourceY";
    private static final String SOURCE_Z = "SourceZ";
    private static final String SOURCE_WAS_RELATED = "SourceWasRelated";
    private static final String ORIGINAL_SOURCE_TYPE = "OriginalSourceType";
    private static final String ORIGINAL_PLAYER_FORM = "OriginalPlayerForm";
    private static final String RESULT_PLAYER_FORM = "ResultPlayerForm";
    private static final String MODE = "Mode";
    private static final String REASON = "Reason";
    private static final String PROGRESS = "Progress";
    private static final String REQUIRED = "Required";
    private static final String ATTEMPTS = "Attempts";
    private static final String USED_APPROACHES = "UsedApproaches";
    private static final String NEGOTIATION_FAILED = "NegotiationFailed";
    private static final String NEGOTIATION_RETRY_AT = "NegotiationRetryAt";
    private static final String NEXT_ATTEMPT = "NextAttempt";
    private static final String OPENED = "Opened";
    private static final String RESPAWN_NOTICE = "RespawnNotice";
    private static final String CAPTURE_PENDING = "CapturePending";
    private static final String SPECIAL_COMPLETION = "SpecialCompletion";
    private static final String FUSION_SPLIT = "FusionSplit";
    private static final String WHITE_KNIGHT_SPLIT = "WhiteKnightSplit";
    private static final String DARK_YUFENG_SPLIT = "DarkYufengSplit";
    private static final String SOURCE_SNAPSHOT = "SourceSnapshot";
    private static final String SOURCE_APPEARANCE = "SourceAppearance";
    private static final String SOURCE_NAME = "SourceName";
    private static final String SOURCE_TRAIT = "SourceTrait";
    private static final String ABSORPTION_RELEASING = "AbsorptionReleasing";
    private static final String EXTERNAL_RELEASE_PENDING =
            "ExternalReleasePending";
    private static final String EXTERNAL_RELEASE_CHECK_AT =
            "ExternalReleaseCheckAt";
    private static final String FORM_RESTORE_PENDING = "FormRestorePending";
    private static final String RELEASE_WHEN_SAFE = "ReleaseWhenSafe";
    private static final String CLAIMED_PLAYER = "ClaimedPlayer";
    private static final String RELEASE_PLAYER = "Player";
    private static final String RELEASE_REVERSE_AT = "ReverseAt";
    private static final String RELEASE_END_AT = "EndAt";
    private static final String RELEASE_REVERSED = "Reversed";
    private static final String RELEASE_BONDED = "BondedReversal";
    private static final String RELEASE_PREVIOUS_PROGRESS = "PreviousProgress";
    private static final String RELEASE_PREVIOUS_USED = "PreviousUsedApproaches";
    private static final String RELEASE_PREVIOUS_ATTEMPTS = "PreviousAttempts";
    private static final int RELEASE_REVERSE_DELAY = 18;
    public static final int RELEASE_DURATION = 46;
    private static final long NEGOTIATION_FAILURE_COOLDOWN_TICKS = 1200L;
    private static final List<Approach> CORE_APPROACHES = List.of(
            Approach.REASON,
            Approach.EMPATHY,
            Approach.APOLOGY,
            Approach.BARGAIN,
            Approach.INSIST);
    private static final int CORE_APPROACHES_MASK = CORE_APPROACHES.stream()
            .mapToInt(InvoluntaryTransfurNegotiation::approachBit)
            .reduce(0, (left, right) -> left | right);
    private static final ResourceLocation ADDON_UNTRANSFUR_SOUND =
            ResourceLocation.fromNamespaceAndPath(
                    ChangedAddonCompat.MOD_ID, "untransfur.sound");
    private static final ResourceKey<Registry<TransfurVariant<?>>>
            TRANSFUR_VARIANT_REGISTRY = ResourceKey.createRegistryKey(
                    ResourceLocation.fromNamespaceAndPath(
                            "changed", "latex_variant"));

    private InvoluntaryTransfurNegotiation() {
    }

    public enum Mode {
        ASSIMILATION,
        ABSORPTION;

        public String translationKey() {
            return "menu.changed_synergy.negotiation.mode."
                    + name().toLowerCase(Locale.ROOT);
        }

        /**
         * Fusion is stored as an absorption internally because it shares the
         * same capture and release pipeline, but it is a distinct event in
         * the negotiation UI.
         */
        public String displayTranslationKey(Reason reason) {
            return switch (reason) {
                case FUSION_STRENGTH, FUSION_CURIOSITY,
                        FUSION_PLAY, FUSION_COMPLETION ->
                        "menu.changed_synergy.negotiation.mode.fusion";
                default -> translationKey();
            };
        }
    }

    public enum Reason {
        COMPANION_SEEKING,
        HOST_SEEKING,
        SELF_DEFENSE,
        FACTION_RETALIATION,
        CACHE_DEFENSE,
        SPECIAL_COMPLETION,
        FUSION_STRENGTH,
        FUSION_CURIOSITY,
        FUSION_PLAY,
        FUSION_COMPLETION;

        public String translationKey() {
            return "menu.changed_synergy.negotiation.reason."
                    + name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Approach {
        REASON,
        EMPATHY,
        APOLOGY,
        BARGAIN,
        INSIST,
        FOOD_BRIBE;

        public static Optional<Approach> fromCommand(String command) {
            if (command == null || !command.startsWith("negotiation_")) {
                return Optional.empty();
            }
            try {
                return Optional.of(valueOf(command.substring(12)
                        .toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }
    }

    public enum ReleaseHoldStep {
        HOLDING,
        REVERSED,
        COMPLETE,
        ABORT
    }

    private enum ReleaseBlockReason {
        NONE,
        AIRBORNE,
        HOSTILES
    }

    public record View(
            Mode mode,
            Reason reason,
            int progress,
            int required,
            int attempts,
            int usedApproaches,
            boolean failed,
            boolean specialCompletion,
            boolean whiteKnightSplit) {
        public int percent() {
            return Mth.clamp(Math.round(progress * 100.0F
                    / Math.max(1, required)), 0, 100);
        }

        public String difficultyKey() {
            int remaining = Math.max(0, required - progress);
            String level = remaining <= 24 ? "close"
                    : required <= 78 ? "easy"
                    : required <= 102 ? "normal"
                    : required <= 124 ? "hard"
                    : "severe";
            return "menu.changed_synergy.negotiation.difficulty." + level;
        }

        public boolean used(Approach approach) {
            return (usedApproaches & approachBit(approach)) != 0;
        }

        public int remainingApproaches() {
            return CORE_APPROACHES.size()
                    - Integer.bitCount(
                            usedApproaches & CORE_APPROACHES_MASK);
        }

        public Optional<Approach> suggestedApproach() {
            int step = Integer.bitCount(
                    usedApproaches & CORE_APPROACHES_MASK);
            if (failed || step >= CORE_APPROACHES.size()) {
                return Optional.empty();
            }
            List<Approach> sequence = preferredSequence(reason);
            for (int index = step; index < sequence.size(); index++) {
                Approach candidate = sequence.get(index);
                if (!used(candidate)) {
                    return Optional.of(candidate);
                }
            }
            return sequence.stream().filter(candidate -> !used(candidate)).findFirst();
        }
    }

    /** Called after every other decision modifier has selected the final method. */
    public static void observeDecision(
            TransfurEvents.LatexAssimilationDecisionEvent event) {
        if (event.isCanceled()
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getSourceEntity() instanceof ChangedEntity source)) {
            return;
        }

        LatexAssimilationDecision<?> decision = event.getDecision();
        if (decision == null) {
            return;
        }
        if (VoluntaryBondTransfurService.isCompleting(source, player)) {
            return;
        }
        event.appendTransfurListener(ignored -> FactionHostilityGrace.begin(
                source, currentPlayerEntity(player)));
        ResourceLocation originalType = ForgeRegistries.ENTITY_TYPES
                .getKey(source.getType());
        Mode mode = decision.method() == LatexAssimilationDecision.Method.ABSORPTION
                ? Mode.ABSORPTION : Mode.ASSIMILATION;
        boolean whiteKnightSplit = mode == Mode.ABSORPTION
                && isChangedType(originalType, "white_latex_knight")
                && isOrdinaryWhiteLatexWolf(player);
        if (ProcessTransfur.isPlayerTransfurred(player) && !whiteKnightSplit) {
            event.appendTransfurListener(
                    ignored -> abandonForSecondaryTransfur(player));
            return;
        }
        if (!eligibleSource(source, player)) {
            if (existingPlayerData(player) != null) {
                event.appendTransfurListener(ignored -> abandonClaim(player));
            }
            return;
        }
        ResourceLocation originalPlayerForm = currentForm(player);
        boolean specialCompletion = isIncompleteSpecial(originalType);
        boolean darkYufengSplit = mode == Mode.ABSORPTION
                && isChangedType(originalType, "dark_latex_yufeng");
        Reason reason = classifyReason(source, player, mode, specialCompletion);
        boolean sourceWasRelated = hasPriorRelationship(source, player);

        // Changed kills an absorbed player before invoking postTransfurListener.
        // Stage the claim now so PlayerEvent.Clone can carry it to the respawned
        // player; the listener below confirms it only after absorption succeeds.
        if (mode == Mode.ABSORPTION) {
            stageAbsorptionCapture(
                    player,
                    source,
                    reason,
                    originalType,
                    originalPlayerForm,
                    specialCompletion,
                    whiteKnightSplit,
                    darkYufengSplit,
                    sourceWasRelated);
        }

        event.appendTransfurListener(result -> completeCapture(
                player,
                source,
                result,
                mode,
                reason,
                originalType,
                originalPlayerForm,
                specialCompletion,
                false,
                whiteKnightSplit,
                darkYufengSplit,
                sourceWasRelated));
    }

    public static boolean eligibleSource(
            ChangedEntity source,
            ServerPlayer player) {
        ResourceLocation sourceType = ForgeRegistries.ENTITY_TYPES
                .getKey(source.getType());
        boolean incompleteCompletion = isIncompleteSpecial(sourceType);
        return source.isAlive()
                && !LatexSocialMemory.isOrganic(source)
                && (LatexSocialMemory.isSocialLatex(source)
                        || incompleteCompletion)
                && (!CreatureSocialProfile.isPermanentlyExcluded(source)
                        || incompleteCompletion)
                && !VoluntaryBondTransfurService.isCompleting(source, player)
                && !isVoluntaryCompanion(source, player);
    }

    /**
     * Changed resolves the native white-knight/white-wolf recipe through its
     * fusion event rather than the ordinary assimilation decision event.
     * Record that absorption here so a later release restores both originals.
     */
    public static void completeFusionCapture(
            ChangedEntity source,
            ServerPlayer player,
            IAbstractChangedEntity result,
            @Nullable TransfurVariant<?> targetVariant) {
        ResourceLocation originalPlayerForm = targetVariant == null
                ? null : targetVariant.getFormId();
        if (!LatexFusionIntent.mayCapture(source, targetVariant)
                || LatexSocialMemory.isOrganic(source)
                || VoluntaryBondTransfurService.isCompleting(source, player)
                || isVoluntaryCompanion(source, player)) {
            return;
        }
        ResourceLocation originalType = ForgeRegistries.ENTITY_TYPES
                .getKey(source.getType());
        boolean specialCompletion = isIncompleteSpecial(originalType);
        boolean whiteKnightSplit = LatexFusionIntent.isWhiteKnight(source)
                && LatexFusionIntent.isOrdinaryWhiteLatexWolf(targetVariant);
        Reason reason = LatexFusionIntent.reasonFor(source);
        boolean sourceWasRelated = hasPriorRelationship(source, player);
        // Fusion can arrive while an older absorption claim is still stored on
        // the player. Always retire that snapshot before recording this exact
        // knight, otherwise its name and appearance leak into the new claim.
        abandonClaim(currentPlayerEntity(player));
        completeCapture(
                player,
                source,
                result,
                Mode.ABSORPTION,
                reason,
                originalType,
                originalPlayerForm,
                specialCompletion,
                true,
                whiteKnightSplit,
                isChangedType(originalType, "dark_latex_yufeng"),
                sourceWasRelated);
        FactionHostilityGrace.begin(source, currentPlayerEntity(player));
    }

    public static boolean canNegotiate(
            ServerPlayer player,
            ChangedEntity source) {
        CompoundTag data = existingPlayerData(player);
        refreshFailureCooldown(player, data);
        if (data == null
                || data.getBoolean(CAPTURE_PENDING)
                || data.getBoolean(NEGOTIATION_FAILED)
                || Mode.ABSORPTION.name().equals(data.getString(MODE))
                || !data.hasUUID(SOURCE_UUID)
                || !source.isAlive()
                || source.isRemoved()
                || !player.getUUID().equals(sourceClaimant(source))) {
            return false;
        }

        ChangedEntity resolved = resolveSource(player, data);
        return resolved == source;
    }

    public static boolean canNegotiateAbsorption(ServerPlayer player) {
        CompoundTag data = existingPlayerData(player);
        refreshFailureCooldown(player, data);
        return player.isAlive()
                && !player.isSpectator()
                && ProcessTransfur.isPlayerTransfurred(player)
                && data != null
                && !data.getBoolean(NEGOTIATION_FAILED)
                && !data.getBoolean(RELEASE_WHEN_SAFE)
                && isAbsorptionClaim(data);
    }

    public static boolean requiresGroundedRelease(ServerPlayer player) {
        CompoundTag data = existingPlayerData(player);
        boolean temporarySuit = ProcessTransfur.getPlayerTransfurVariantSafe(player)
                .map(instance -> instance.isTemporaryFromSuit())
                .orElse(false);
        return temporarySuit || isAbsorptionClaim(data)
                || data != null && data.getBoolean(EXTERNAL_RELEASE_PENDING);
    }

    public static boolean canReleaseAtCurrentPosition(ServerPlayer player) {
        return !requiresGroundedRelease(player) || player.onGround();
    }

    public static void warnAirborneRelease(ServerPlayer player) {
        player.displayClientMessage(Component.translatable(
                "message.changed_synergy.negotiation.airborne_release_blocked",
                absorptionSourceName(player)),
                true);
    }

    private static ReleaseBlockReason absorptionReleaseBlock(
            ServerPlayer player) {
        if (!player.onGround()) {
            return ReleaseBlockReason.AIRBORNE;
        }
        boolean hostileNearby = !player.serverLevel().getEntitiesOfClass(
                Mob.class,
                player.getBoundingBox().inflate(8.0D, 5.0D, 8.0D),
                mob -> mob.isAlive()
                        && mob instanceof Enemy
                        && !(mob instanceof ChangedEntity)).isEmpty();
        return hostileNearby
                ? ReleaseBlockReason.HOSTILES
                : ReleaseBlockReason.NONE;
    }

    private static void warnBlockedAbsorptionRelease(
            ServerPlayer player,
            ReleaseBlockReason reason) {
        if (reason == ReleaseBlockReason.AIRBORNE) {
            warnAirborneRelease(player);
        } else if (reason == ReleaseBlockReason.HOSTILES) {
            player.displayClientMessage(Component.translatable(
                    "message.changed_synergy.negotiation.hostile_release_blocked",
                    absorptionSourceName(player)), true);
        }
    }

    /** Remaining failed-negotiation lockout, in ticks. */
    public static long negotiationCooldownTicks(ServerPlayer player) {
        CompoundTag data = existingPlayerData(player);
        refreshFailureCooldown(player, data);
        if (data == null || !data.getBoolean(NEGOTIATION_FAILED)) {
            return 0L;
        }
        return Math.max(0L, data.getLong(NEGOTIATION_RETRY_AT)
                - player.level().getGameTime());
    }

    /** True for the exact assimilator even while its retry cooldown is active. */
    public static boolean isNegotiationSourceFor(
            ServerPlayer player,
            ChangedEntity source) {
        CompoundTag data = existingPlayerData(player);
        if (data == null
                || data.getBoolean(CAPTURE_PENDING)
                || Mode.ABSORPTION.name().equals(data.getString(MODE))
                || !data.hasUUID(SOURCE_UUID)) {
            return false;
        }
        ChangedEntity resolved = resolveSource(player, data);
        return resolved == source;
    }

    public static Optional<View> view(ServerPlayer player) {
        CompoundTag data = existingPlayerData(player);
        if (data == null
                || data.getBoolean(CAPTURE_PENDING)
                || !data.hasUUID(SOURCE_UUID)) {
            return Optional.empty();
        }
        return Optional.of(readView(data));
    }

    public static boolean claimOpeningLine(
            ServerPlayer player,
            ChangedEntity source) {
        if (!canNegotiate(player, source)) {
            return false;
        }
        CompoundTag data = playerData(player);
        if (data.getBoolean(OPENED)) {
            return false;
        }
        data.putBoolean(OPENED, true);
        View view = readView(data);
        NpcDialogue.trigger(source, player,
                view.specialCompletion()
                        ? Cue.NEGOTIATION_OPEN_COMPLETION
                        : view.mode() == Mode.ABSORPTION
                                ? Cue.NEGOTIATION_OPEN_ABSORPTION
                                : Cue.NEGOTIATION_OPEN_ASSIMILATION);
        return true;
    }

    /** Opens the voice of the latex creature currently surrounding the player. */
    public static boolean claimAbsorptionOpeningLine(ServerPlayer player) {
        if (!canNegotiateAbsorption(player)) {
            return false;
        }
        CompoundTag data = playerData(player);
        if (data.getBoolean(OPENED)) {
            return false;
        }
        data.putBoolean(OPENED, true);
        sendAbsorptionLine(player, data, "open");
        return true;
    }

    public static void attempt(
            ServerPlayer player,
            ChangedEntity source,
            Approach approach) {
        if (!canNegotiate(player, source)) {
            return;
        }
        CompoundTag data = playerData(player);
        long now = source.level().getGameTime();
        if (data.getLong(NEXT_ATTEMPT) > now) {
            return;
        }
        data.putLong(NEXT_ATTEMPT, now + 8L);

        View before = readView(data);
        if (before.used(approach)) {
            return;
        }
        if (approach == Approach.FOOD_BRIBE
                && !consumeFoodBribe(player, source, data)) {
            return;
        }
        Trait trait = CreaturePersonality.dominantTrait(source);
        int usedApproaches = before.usedApproaches()
                | approachBit(approach);
        int gain = approach == Approach.FOOD_BRIBE
                ? foodBribeGain(before.reason(), trait)
                : Math.max(2, approachGain(
                        approach, before.reason(), trait,
                        before.specialCompletion())
                        + sequenceModifier(
                                before.reason(), approach,
                                Integer.bitCount(before.usedApproaches()
                                        & CORE_APPROACHES_MASK)));
        int progress = Math.min(before.required(), before.progress() + gain);
        data.putInt(USED_APPROACHES, usedApproaches);
        data.putInt(PROGRESS, progress);
        data.putInt(ATTEMPTS, before.attempts() + 1);

        if (progress >= before.required()) {
            beginRelease(player, source, readView(data), before);
            return;
        }

        if ((usedApproaches & CORE_APPROACHES_MASK)
                == CORE_APPROACHES_MASK) {
            beginFailureCooldown(data, now);
            NpcDialogue.trigger(
                    source, player, Cue.NEGOTIATION_FAILED);
            return;
        }

        boolean poorFit = gain <= 7;
        NpcDialogue.trigger(
                source,
                player,
                poorFit ? Cue.NEGOTIATION_RESIST : switch (approach) {
                    case REASON -> Cue.NEGOTIATION_REASON;
                    case EMPATHY -> Cue.NEGOTIATION_EMPATHY;
                    case APOLOGY -> Cue.NEGOTIATION_APOLOGY;
                    case BARGAIN -> Cue.NEGOTIATION_BARGAIN;
                    case INSIST -> Cue.NEGOTIATION_INSIST;
                    case FOOD_BRIBE -> Cue.NEGOTIATION_FOOD_BRIBE;
                });
    }

    /**
     * Advances an absorption negotiation without materialising a duplicate
     * creature beside the player.  The original individual's snapshot stays
     * on the player until a successful separation restores the correct body.
     */
    public static void attemptAbsorption(
            ServerPlayer player,
            Approach approach) {
        if (!canNegotiateAbsorption(player)) {
            return;
        }
        CompoundTag data = playerData(player);
        long now = player.level().getGameTime();
        if (data.getLong(NEXT_ATTEMPT) > now) {
            return;
        }
        data.putLong(NEXT_ATTEMPT, now + 8L);

        View before = readView(data);
        if (before.used(approach)) {
            return;
        }
        int foodBribeSlot = approach == Approach.FOOD_BRIBE
                ? findAbsorptionFoodBribeSlot(player, data) : -1;
        if (approach == Approach.FOOD_BRIBE && foodBribeSlot < 0) {
            player.displayClientMessage(Component.translatable(
                    "message.changed_synergy.negotiation.no_bribe_food"),
                    true);
            return;
        }
        Trait trait = sourceTrait(data);
        int usedApproaches = before.usedApproaches()
                | approachBit(approach);
        int gain = approach == Approach.FOOD_BRIBE
                ? foodBribeGain(before.reason(), trait)
                : Math.max(2, approachGain(
                        approach, before.reason(), trait,
                        before.specialCompletion())
                        + sequenceModifier(
                                before.reason(), approach,
                                Integer.bitCount(before.usedApproaches()
                                        & CORE_APPROACHES_MASK)));
        data.putInt(USED_APPROACHES, usedApproaches);
        data.putInt(PROGRESS,
                Math.min(before.required(), before.progress() + gain));
        data.putInt(ATTEMPTS, before.attempts() + 1);

        if (data.getInt(PROGRESS) >= before.required()) {
            ReleaseBlockReason block = absorptionReleaseBlock(player);
            if (block != ReleaseBlockReason.NONE) {
                if (approach == Approach.FOOD_BRIBE) {
                    consumeFoodBribeSlot(player, foodBribeSlot);
                }
                queueReleaseWhenSafe(player, data, now);
                warnBlockedAbsorptionRelease(player, block);
                return;
            }
            if (!completeAbsorptionRelease(player, data.copy())) {
                CompoundTag live = existingPlayerData(player);
                if (live != null) {
                    if (approach == Approach.FOOD_BRIBE) {
                        consumeFoodBribeSlot(player, foodBribeSlot);
                    }
                    queueReleaseWhenSafe(player, live, now);
                }
            } else if (approach == Approach.FOOD_BRIBE) {
                consumeFoodBribeSlot(player, foodBribeSlot);
            }
            return;
        }

        if (approach == Approach.FOOD_BRIBE) {
            consumeFoodBribeSlot(player, foodBribeSlot);
        }

        if ((usedApproaches & CORE_APPROACHES_MASK)
                == CORE_APPROACHES_MASK) {
            beginFailureCooldown(data, now);
            sendAbsorptionLine(player, data, "failed");
            syncAbsorptionState(player, true);
            return;
        }

        sendAbsorptionLine(
                player,
                data,
                gain <= 7 ? "resist" : switch (approach) {
                    case REASON -> "reason";
                    case EMPATHY -> "empathy";
                    case APOLOGY -> "apology";
                    case BARGAIN -> "bargain";
                    case INSIST -> "insist";
                    case FOOD_BRIBE -> "food_bribe";
                });
    }

    public static Trait absorptionSourceTrait(ServerPlayer player) {
        CompoundTag data = existingPlayerData(player);
        return data == null ? Trait.CURIOUS : sourceTrait(data);
    }

    public static boolean canOfferFoodBribe(
            ServerPlayer player,
            @Nullable ChangedEntity source) {
        CompoundTag data = existingPlayerData(player);
        if (data == null) {
            return false;
        }
        ChangedEntity target = source;
        boolean temporary = false;
        if (target == null) {
            target = createFoodProfile(player, data);
            temporary = target != null;
        }
        if (target == null) {
            return false;
        }
        boolean available = findFoodBribeSlot(player, target) >= 0;
        if (temporary) {
            target.discard();
        }
        return available;
    }

    /** Whether this negotiation uses cooked fish instead of an orange offer. */
    public static boolean usesFelineFoodBribe(
            ServerPlayer player,
            @Nullable ChangedEntity source) {
        CompoundTag data = existingPlayerData(player);
        if (data == null) {
            return false;
        }
        ChangedEntity target = source;
        boolean temporary = false;
        if (target == null) {
            target = createFoodProfile(player, data);
            temporary = target != null;
        }
        boolean feline = target != null
                && RelationshipFavorService.isFeline(target);
        if (temporary) {
            target.discard();
        }
        return feline;
    }

    public static Component absorptionSourceName(ServerPlayer player) {
        CompoundTag data = existingPlayerData(player);
        if (data == null || data.getString(SOURCE_NAME).isBlank()) {
            return Component.translatable(
                    "menu.changed_synergy.negotiation.surrounding_creature");
        }
        return Component.literal(data.getString(SOURCE_NAME));
    }

    public static CompoundTag absorptionSourceAppearance(ServerPlayer player) {
        CompoundTag data = existingPlayerData(player);
        return data != null && data.contains(SOURCE_APPEARANCE, Tag.TAG_COMPOUND)
                ? data.getCompound(SOURCE_APPEARANCE).copy()
                : new CompoundTag();
    }

    public static boolean isNegotiationSource(ChangedEntity source) {
        if (!source.getPersistentData().contains(
                SOURCE_ROOT, Tag.TAG_COMPOUND)) {
            return false;
        }
        CompoundTag marker = source.getPersistentData().getCompound(SOURCE_ROOT);
        return marker.hasUUID(CLAIMED_PLAYER)
                && !marker.getBoolean(CAPTURE_PENDING);
    }

    public static boolean hasReleaseHold(ChangedEntity source) {
        return source.getPersistentData().contains(RELEASE_ROOT, Tag.TAG_COMPOUND)
                && source.getPersistentData().getCompound(RELEASE_ROOT)
                        .hasUUID(RELEASE_PLAYER);
    }

    public static boolean isReleaseHoldTarget(
            ChangedEntity source,
            LivingEntity target) {
        CompoundTag release = source.getPersistentData()
                .getCompound(RELEASE_ROOT);
        return release.hasUUID(RELEASE_PLAYER)
                && target.getUUID().equals(release.getUUID(RELEASE_PLAYER));
    }

    public static boolean isReleaseHoldActive(
            ChangedEntity source,
            LivingEntity target) {
        return isReleaseHoldTarget(source, target)
                && source.getPersistentData().getCompound(RELEASE_ROOT)
                        .getLong(RELEASE_END_AT) >= source.level().getGameTime();
    }

    @Nullable
    public static ServerPlayer releaseHoldPlayer(ChangedEntity source) {
        if (!(source.level() instanceof ServerLevel level)
                || !hasReleaseHold(source)) {
            return null;
        }
        return level.getServer().getPlayerList().getPlayer(
                source.getPersistentData().getCompound(RELEASE_ROOT)
                        .getUUID(RELEASE_PLAYER));
    }

    public static boolean isReleaseParticipant(LivingEntity entity) {
        if (entity instanceof ChangedEntity source) {
            return hasReleaseHold(source)
                    && source.getPersistentData().getCompound(RELEASE_ROOT)
                            .getLong(RELEASE_END_AT) >= source.level().getGameTime();
        }
        if (!(entity instanceof ServerPlayer player)) {
            return false;
        }
        CompoundTag data = existingPlayerData(player);
        if (data != null && data.hasUUID(SOURCE_UUID)) {
            ChangedEntity source = resolveSource(player, data);
            if (source != null && isReleaseHoldTarget(source, player)) {
                return true;
            }
        }
        // A manual bonded reversal has no involuntary negotiation claim. Find
        // its short-lived holder marker instead of requiring player claim NBT.
        for (ServerLevel level : player.server.getAllLevels()) {
            for (Entity candidate : level.getAllEntities()) {
                if (candidate instanceof ChangedEntity source
                        && isReleaseHoldActive(source, player)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether this companion may retract a non-organic latex form from its owner. */
    public static boolean canBondedReversal(
            ChangedEntity source,
            ServerPlayer player) {
        return source.isAlive()
                && !source.isRemoved()
                && player.isAlive()
                && !player.isSpectator()
                && source.level() == player.level()
                && player.distanceToSqr(source) <= 64.0D
                && LatexSocialMemory.isPetOwner(source, player)
                && !LatexSocialMemory.isOrganic(source)
                && !hasAbsorptionClaim(player)
                && !isReleaseParticipant(player)
                && ProcessTransfur.isPlayerTransfurred(player)
                && !isOrganicPlayerForm(player)
                && ProcessTransfur.getPlayerTransfurVariantSafe(player)
                        .map(instance -> !instance.isTemporaryFromSuit())
                        .orElse(false);
    }

    private static boolean canContinueBondedReversal(
            ChangedEntity source,
            ServerPlayer player,
            CompoundTag release) {
        if (!source.isAlive()
                || source.isRemoved()
                || !player.isAlive()
                || source.level() != player.level()
                || !LatexSocialMemory.isPetOwner(source, player)
                || LatexSocialMemory.isOrganic(source)) {
            return false;
        }
        if (release.getBoolean(RELEASE_REVERSED)) {
            return true;
        }
        return !player.isSpectator()
                && player.distanceToSqr(source) <= 64.0D
                && !hasAbsorptionClaim(player)
                && ProcessTransfur.isPlayerTransfurred(player)
                && !isOrganicPlayerForm(player)
                && ProcessTransfur.getPlayerTransfurVariantSafe(player)
                        .map(instance -> !instance.isTemporaryFromSuit())
                        .orElse(false);
    }

    /** Starts the same secure, QTE-free hold used after a successful negotiation. */
    public static boolean beginBondedReversal(
            ChangedEntity source,
            ServerPlayer player) {
        if (hasReleaseHold(source)
                || !canBondedReversal(source, player)) {
            return false;
        }
        long now = source.level().getGameTime();
        CompoundTag release = new CompoundTag();
        release.putUUID(RELEASE_PLAYER, player.getUUID());
        release.putLong(RELEASE_REVERSE_AT, now + RELEASE_REVERSE_DELAY);
        release.putLong(RELEASE_END_AT, now + RELEASE_DURATION);
        release.putBoolean(RELEASE_REVERSED, false);
        release.putBoolean(RELEASE_BONDED, true);
        source.getPersistentData().put(RELEASE_ROOT, release);
        player.closeContainer();

        if (ChangedAddonCompat.tryStartNegotiatedRelease(
                source, player, RELEASE_DURATION)) {
            NpcDialogue.trigger(source, player, Cue.BOND_REVERSE_HOLD);
            return true;
        }

        applyPlayerReversal(player, false);
        release.putBoolean(RELEASE_REVERSED, true);
        source.getPersistentData().put(RELEASE_ROOT, release);
        finishReleaseHold(source, player);
        return true;
    }

    public static ReleaseHoldStep advanceReleaseHold(
            ChangedEntity source,
            ServerPlayer player) {
        CompoundTag release = source.getPersistentData()
                .getCompound(RELEASE_ROOT);
        boolean bondedReversal = release.getBoolean(RELEASE_BONDED);
        if (!source.isAlive()
                || !player.isAlive()
                || source.level() != player.level()
                || (bondedReversal
                        ? !canContinueBondedReversal(source, player, release)
                        : !canNegotiate(player, source))
                || !isReleaseHoldTarget(source, player)) {
            return ReleaseHoldStep.ABORT;
        }
        long now = source.level().getGameTime();
        boolean reversedNow = false;
        if (!release.getBoolean(RELEASE_REVERSED)
                && now >= release.getLong(RELEASE_REVERSE_AT)) {
            applyPlayerReversal(
                    player,
                    !bondedReversal
                            && readView(playerData(player)).whiteKnightSplit());
            release.putBoolean(RELEASE_REVERSED, true);
            reversedNow = true;
        }
        if (now >= release.getLong(RELEASE_END_AT)) {
            return ReleaseHoldStep.COMPLETE;
        }
        return reversedNow ? ReleaseHoldStep.REVERSED
                : ReleaseHoldStep.HOLDING;
    }

    public static void finishReleaseHold(
            ChangedEntity source,
            ServerPlayer player) {
        CompoundTag release = source.getPersistentData()
                .getCompound(RELEASE_ROOT);
        boolean bondedReversal = release.getBoolean(RELEASE_BONDED);
        if (!isReleaseHoldTarget(source, player)
                || (bondedReversal
                        ? !canContinueBondedReversal(source, player, release)
                        : !canNegotiate(player, source))) {
            abortReleaseHold(source, player);
            return;
        }
        completeReleaseHold(source, player, release, true);
    }

    private static void completeReleaseHold(
            ChangedEntity source,
            ServerPlayer player,
            CompoundTag release,
            boolean playDialogue) {
        boolean bondedReversal = release.getBoolean(RELEASE_BONDED);
        if (bondedReversal) {
            if (!release.getBoolean(RELEASE_REVERSED)) {
                applyPlayerReversal(player, false);
            }
            source.getPersistentData().remove(RELEASE_ROOT);
            LatexSocialMemory.clearHostilityToward(source, player);
            abandonClaim(player);
            if (playDialogue && source.level() == player.level()) {
                NpcDialogue.trigger(source, player, Cue.BOND_REVERSE_COMPLETE);
            }
            return;
        }

        CompoundTag data = existingPlayerData(player);
        if (data == null) {
            source.getPersistentData().remove(RELEASE_ROOT);
            source.getPersistentData().remove(SOURCE_ROOT);
            return;
        }
        View view = readView(data);
        boolean fusionSplit = data.getBoolean(FUSION_SPLIT);
        if (!release.getBoolean(RELEASE_REVERSED)) {
            applyPlayerReversal(player, view.whiteKnightSplit());
        }
        source.getPersistentData().remove(RELEASE_ROOT);
        source.getPersistentData().remove(SOURCE_ROOT);

        LatexSocialMemory.settleAfterNegotiatedRelease(source, player);
        FactionHostilityGrace.begin(source, player);
        LatexSocialEvents.clearPendingCombatReactions(source, player);
        CreaturePersonality.establishRelationship(source, player);
        source.setPersistenceRequired();
        if (fusionSplit) {
            LatexFusionIntent.beginReleaseCooldown(source, player);
        }
        if (playDialogue && source.level() == player.level()) {
            NpcDialogue.trigger(source, player, releaseCue(view));
        }
        net.parkabird.changedsynergy.advancement.SynergyAdvancements.grant(
                player,
                net.parkabird.changedsynergy.advancement.SynergyAdvancements
                        .NEGOTIATED_RELEASE);
        if (fusionSplit) {
            net.parkabird.changedsynergy.advancement.SynergyAdvancements.grant(
                    player,
                    net.parkabird.changedsynergy.advancement.SynergyAdvancements
                            .FUSION_SEPARATION);
        }
        clearPlayerData(player);
    }

    private static void completeInterruptedRelease(
            ChangedEntity source,
            ServerPlayer player,
            boolean bondedReversal) {
        CompoundTag release = new CompoundTag();
        release.putBoolean(RELEASE_BONDED, bondedReversal);
        release.putBoolean(RELEASE_REVERSED, true);
        completeReleaseHold(source, player, release, false);
    }

    public static void abortReleaseHold(
            ChangedEntity source,
            @Nullable ServerPlayer player) {
        CompoundTag release = source.getPersistentData()
                .getCompound(RELEASE_ROOT).copy();
        boolean bondedReversal = release.getBoolean(RELEASE_BONDED);
        source.getPersistentData().remove(RELEASE_ROOT);
        if (player != null && release.getBoolean(RELEASE_REVERSED)) {
            completeInterruptedRelease(source, player, bondedReversal);
            return;
        }
        if (player != null && !bondedReversal) {
            CompoundTag data = existingPlayerData(player);
            if (data != null) {
                data.putInt(PROGRESS, release.getInt(RELEASE_PREVIOUS_PROGRESS));
                data.putInt(USED_APPROACHES, release.getInt(RELEASE_PREVIOUS_USED));
                data.putInt(ATTEMPTS, release.getInt(RELEASE_PREVIOUS_ATTEMPTS));
                data.putLong(NEXT_ATTEMPT, source.level().getGameTime() + 10L);
            }
        }
    }

    /** Recovers an expired hold after reload or when the optional Addon bridge is absent. */
    public static void tickReleaseHoldRecovery(ChangedEntity source) {
        if (!hasReleaseHold(source)) {
            return;
        }
        CompoundTag release = source.getPersistentData().getCompound(RELEASE_ROOT);
        if (release.getLong(RELEASE_END_AT) >= source.level().getGameTime()) {
            return;
        }
        ServerPlayer player = releaseHoldPlayer(source);
        if (player == null) {
            // Keep the expired marker as inert recovery data. It no longer grants
            // damage immunity and can be resolved when the player returns.
            return;
        }
        if (release.getBoolean(RELEASE_REVERSED)) {
            completeInterruptedRelease(
                    source, player, release.getBoolean(RELEASE_BONDED));
            source.getPersistentData().remove(RELEASE_ROOT);
        } else {
            abortReleaseHold(source, player);
        }
    }

    public static void copySourceMarker(
            ChangedEntity previous,
            ChangedEntity replacement) {
        CompoundTag from = previous.getPersistentData();
        if (from.contains(SOURCE_ROOT, Tag.TAG_COMPOUND)) {
            replacement.getPersistentData().put(
                    SOURCE_ROOT,
                    from.getCompound(SOURCE_ROOT).copy());
        }
        if (from.contains(RELEASE_ROOT, Tag.TAG_COMPOUND)) {
            replacement.getPersistentData().put(
                    RELEASE_ROOT,
                    from.getCompound(RELEASE_ROOT).copy());
        }
    }

    public static void replaceSourceReference(
            ServerPlayer player,
            UUID previous,
            UUID replacement) {
        CompoundTag data = existingPlayerData(player);
        if (data != null && data.hasUUID(SOURCE_UUID)
                && previous.equals(data.getUUID(SOURCE_UUID))) {
            data.putUUID(SOURCE_UUID, replacement);
        }
    }

    public static void copyPlayerData(
            ServerPlayer original,
            ServerPlayer clone) {
        CompoundTag originalPersisted = persisted(original);
        if (originalPersisted.contains(PLAYER_ROOT, Tag.TAG_COMPOUND)) {
            persisted(clone).put(
                    PLAYER_ROOT,
                    originalPersisted.getCompound(PLAYER_ROOT).copy());
        }
    }

    public static void onPlayerReady(ServerPlayer player) {
        CompoundTag data = existingPlayerData(player);
        if (data != null && data.getBoolean(CAPTURE_PENDING)) {
            // Only the Changed post-transfur listener may confirm a staged
            // absorption. A pending record on login/respawn means the attempt
            // never reached its successful completion callback.
            abandonClaim(player);
            return;
        }
        if (data != null && data.getBoolean(FORM_RESTORE_PENDING)) {
            if (!ProcessTransfur.isPlayerTransfurred(player)) {
                if (!restoreCapturedResultForm(player, data)) {
                    abandonClaim(player);
                    return;
                }
            }
            data.putBoolean(FORM_RESTORE_PENDING, false);
        }
        // Death, commands and third-party reversal paths do not all emit the
        // same Changed event. Never expose a stale release claim to a human,
        // and retire saves from the removed secondary-transfur negotiation.
        if (data != null && !ProcessTransfur.isPlayerTransfurred(player)) {
            if (Mode.ABSORPTION.name().equals(data.getString(MODE))) {
                tickPlayer(player);
            } else {
                abandonClaim(player);
            }
            return;
        }
        syncAbsorptionState(player,
                isAbsorptionClaim(data)
                        && !data.getBoolean(RELEASE_WHEN_SAFE));
        if (data == null
                || Mode.ABSORPTION.name().equals(data.getString(MODE))
                || !data.hasUUID(SOURCE_UUID)
                || data.getBoolean(RESPAWN_NOTICE)) {
            return;
        }
        ChangedEntity source = resolveSource(player, data);
        if (source == null) {
            return;
        }
        data.putBoolean(RESPAWN_NOTICE, true);
        player.sendSystemMessage(Component.translatable(
                "message.changed_synergy.negotiation.source_waiting",
                source.getDisplayName()));
    }

    public static void sourceDied(ChangedEntity source) {
        if (!(source.level() instanceof ServerLevel level)) {
            return;
        }
        UUID sourceId = source.getUUID();
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            CompoundTag data = existingPlayerData(player);
            if (data != null && data.hasUUID(SOURCE_UUID)
                    && sourceId.equals(data.getUUID(SOURCE_UUID))) {
                // Absorption deliberately discards the external source.  Its
                // complete state lives in the player claim until separation.
                if (Mode.ABSORPTION.name().equals(data.getString(MODE))) {
                    continue;
                }
                clearPlayerData(player);
                player.sendSystemMessage(Component.translatable(
                        "message.changed_synergy.negotiation.source_lost"));
            }
        }
    }

    /** A deliberate friend transfur replaces any older involuntary claim. */
    public static void abandonForVoluntaryBond(ServerPlayer player) {
        abandonClaim(player);
    }

    /** A secondary transfur supersedes, rather than creates, a release claim. */
    public static void abandonForSecondaryTransfur(ServerPlayer player) {
        abandonClaim(player);
    }

    /**
     * Reversal items, commands and Addon medicine restore the player first;
     * the swallowed individual is materialised on the following server tick.
     */
    public static void onExternalUntransfur(ServerPlayer player) {
        CompoundTag data = existingPlayerData(player);
        if (isReleaseParticipant(player)
                || data != null && data.getBoolean(ABSORPTION_RELEASING)) {
            return;
        }
        if (isAbsorptionClaim(data)) {
            data.putBoolean(EXTERNAL_RELEASE_PENDING, true);
            data.putLong(EXTERNAL_RELEASE_CHECK_AT,
                    player.level().getGameTime() + 1L);
            syncAbsorptionState(player, false);
        } else {
            abandonClaim(player);
        }
    }

    /** Completes a non-negotiated separation after Changed has removed the form. */
    public static void tickPlayer(ServerPlayer player) {
        CompoundTag data = existingPlayerData(player);
        if (data == null || !player.isAlive()) {
            return;
        }
        if (data.getBoolean(FORM_RESTORE_PENDING)) {
            if (ProcessTransfur.isPlayerTransfurred(player)) {
                data.putBoolean(FORM_RESTORE_PENDING, false);
            } else {
                if (restoreCapturedResultForm(player, data)) {
                    data.putBoolean(FORM_RESTORE_PENDING, false);
                }
                return;
            }
        }
        if (data.getBoolean(RELEASE_WHEN_SAFE)
                && ProcessTransfur.isPlayerTransfurred(player)) {
            tickQueuedRelease(player, data);
            return;
        }
        if (!data.getBoolean(EXTERNAL_RELEASE_PENDING)) {
            if (isAbsorptionClaim(data)
                    && !ProcessTransfur.isPlayerTransfurred(player)) {
                if (!player.onGround()) {
                    restoreCapturedResultForm(player, data);
                    warnAirborneRelease(player);
                    return;
                }
                data.putBoolean(EXTERNAL_RELEASE_PENDING, true);
                data.putLong(EXTERNAL_RELEASE_CHECK_AT,
                        player.level().getGameTime());
                syncAbsorptionState(player, false);
            } else {
                return;
            }
        }
        if (ProcessTransfur.isPlayerTransfurred(player)) {
            if (player.level().getGameTime()
                    >= data.getLong(EXTERNAL_RELEASE_CHECK_AT)) {
                data.putBoolean(EXTERNAL_RELEASE_PENDING, false);
                data.remove(EXTERNAL_RELEASE_CHECK_AT);
                syncAbsorptionState(player,
                        !data.getBoolean(RELEASE_WHEN_SAFE));
            }
            return;
        }
        ReleaseBlockReason block = absorptionReleaseBlock(player);
        if (block != ReleaseBlockReason.NONE) {
            restoreCapturedResultForm(player, data);
            data.putBoolean(EXTERNAL_RELEASE_PENDING, false);
            data.remove(EXTERNAL_RELEASE_CHECK_AT);
            syncAbsorptionState(player,
                    !data.getBoolean(RELEASE_WHEN_SAFE));
            warnBlockedAbsorptionRelease(player, block);
            return;
        }
        CompoundTag claim = data.copy();
        ChangedEntity released = createReleasedAbsorber(player, claim);
        if (released == null) {
            return;
        }
        LatexSocialMemory.settleAfterNegotiatedRelease(released, player);
        FactionHostilityGrace.begin(released, player);
        LatexSocialEvents.clearPendingCombatReactions(released, player);
        LatexSocialEvents.calmTowards(released, player);
        if (claim.getBoolean(FUSION_SPLIT)
                || claim.getBoolean(WHITE_KNIGHT_SPLIT)) {
            restoreOriginalPlayerForm(player, claim);
        }
        if (claim.getBoolean(FUSION_SPLIT)) {
            LatexFusionIntent.beginReleaseCooldown(released, player);
        }
        released.setPersistenceRequired();
        clearPlayerData(player);
    }

    public static boolean hasAbsorptionClaim(ServerPlayer player) {
        return isAbsorptionClaim(existingPlayerData(player));
    }

    private static void completeCapture(
            ServerPlayer player,
            ChangedEntity originalSource,
            @Nullable IAbstractChangedEntity result,
            Mode mode,
            Reason reason,
            @Nullable ResourceLocation originalType,
            @Nullable ResourceLocation originalPlayerForm,
            boolean specialCompletion,
            boolean fusionSplit,
            boolean whiteKnightSplit,
            boolean darkYufengSplit,
            boolean sourceWasRelated) {
        ServerPlayer recipient = currentPlayerEntity(player);
        ChangedEntity source = originalSource;
        if (mode != Mode.ABSORPTION
                && (!source.isAlive() || source.isRemoved())) {
            return;
        }

        if (mode != Mode.ABSORPTION) {
            clearPreviousClaim(recipient);
        }
        CompoundTag data = playerData(recipient);
        if (!data.contains(SOURCE_SNAPSHOT, Tag.TAG_COMPOUND)) {
            captureSourceState(data, source, specialCompletion);
        }
        data.putUUID(SOURCE_UUID, source.getUUID());
        data.putString(SOURCE_DIMENSION, source.level().dimension().location().toString());
        rememberSource(data, source);
        putLocation(data, ORIGINAL_SOURCE_TYPE, originalType);
        putLocation(data, ORIGINAL_PLAYER_FORM, originalPlayerForm);
        ResourceLocation resultForm = result != null
                && result.getSelfVariant() != null
                        ? result.getSelfVariant().getFormId()
                        : currentForm(recipient);
        putLocation(data, RESULT_PLAYER_FORM, resultForm);
        data.putString(MODE, mode.name());
        data.putString(REASON, reason.name());
        data.putBoolean(SPECIAL_COMPLETION, specialCompletion);
        data.putBoolean(FUSION_SPLIT, fusionSplit);
        data.putBoolean(WHITE_KNIGHT_SPLIT, whiteKnightSplit);
        data.putBoolean(DARK_YUFENG_SPLIT, darkYufengSplit);
        data.putBoolean(SOURCE_WAS_RELATED, sourceWasRelated);
        int required = data.contains(REQUIRED, Tag.TAG_INT)
                ? data.getInt(REQUIRED)
                : requiredProgress(source, recipient, mode, reason);
        data.putInt(REQUIRED, required);
        data.putInt(PROGRESS,
                specialCompletion ? Math.max(18, required / 3) : 0);
        data.putInt(ATTEMPTS, 0);
        data.putInt(USED_APPROACHES, 0);
        data.putBoolean(NEGOTIATION_FAILED, false);
        data.remove(NEGOTIATION_RETRY_AT);
        data.putBoolean(EXTERNAL_RELEASE_PENDING, false);
        data.putBoolean(RELEASE_WHEN_SAFE, false);
        data.putBoolean(OPENED, false);
        data.putBoolean(CAPTURE_PENDING, false);
        data.putBoolean(FORM_RESTORE_PENDING,
                mode == Mode.ABSORPTION
                        && !recipient.isAlive()
                        && resultForm != null);
        boolean playerCanAct = recipient.isAlive();
        data.putBoolean(RESPAWN_NOTICE, playerCanAct);

        if (mode != Mode.ABSORPTION) {
            CompoundTag marker = new CompoundTag();
            marker.putUUID(CLAIMED_PLAYER, recipient.getUUID());
            putLocation(marker, ORIGINAL_SOURCE_TYPE, originalType);
            marker.putString(MODE, mode.name());
            marker.putBoolean(CAPTURE_PENDING, false);
            source.getPersistentData().put(SOURCE_ROOT, marker);
            source.setPersistenceRequired();
            CreaturePersonality.ensure(source);
            CreatureIdentity.ensure(source);
            CreatureLifeMemory.ensure(source);
            CreatureCommunityData.bind(source);
            source.setPersistenceRequired();
        }

        // Assimilation creates a direct personal connection regardless of why
        // it happened.  A hostile incident starts at a lower familiarity, but
        // stale combat targets must not make the new friend attack immediately.
        if (mode == Mode.ASSIMILATION) {
            LatexSocialEvents.clearPendingCombatReactions(source, recipient);
            LatexSocialEvents.calmTowards(source, recipient);
            CreaturePersonality.establishRelationship(source, recipient);
            CreaturePersonality.setFamiliarity(
                    source,
                    recipient,
                    Math.max(isPeacefulReason(reason) ? 14 : 8,
                            CreaturePersonality.familiarity(
                            source, recipient)));
        }

        if (playerCanAct) {
            recipient.sendSystemMessage(Component.translatable(
                    mode == Mode.ABSORPTION
                            ? "message.changed_synergy.negotiation.absorption_available"
                            : "message.changed_synergy.negotiation.available",
                    mode == Mode.ABSORPTION
                            ? absorptionSourceName(recipient)
                            : source.getDisplayName()));
        }
        syncAbsorptionState(recipient, mode == Mode.ABSORPTION);
    }

    /** Writes only clone-safe metadata; pending claims are never exposed in UI. */
    private static void stageAbsorptionCapture(
            ServerPlayer player,
            ChangedEntity source,
            Reason reason,
            @Nullable ResourceLocation originalType,
            @Nullable ResourceLocation originalPlayerForm,
            boolean specialCompletion,
            boolean whiteKnightSplit,
            boolean darkYufengSplit,
            boolean sourceWasRelated) {
        clearPreviousClaim(player);
        CompoundTag data = playerData(player);
        captureSourceState(data, source, specialCompletion);
        data.putUUID(SOURCE_UUID, source.getUUID());
        rememberSource(data, source);
        putLocation(data, ORIGINAL_SOURCE_TYPE, originalType);
        putLocation(data, ORIGINAL_PLAYER_FORM, originalPlayerForm);
        data.remove(RESULT_PLAYER_FORM);
        data.putString(MODE, Mode.ABSORPTION.name());
        data.putString(REASON, reason.name());
        data.putBoolean(SPECIAL_COMPLETION, specialCompletion);
        data.putBoolean(FUSION_SPLIT, false);
        data.putBoolean(WHITE_KNIGHT_SPLIT, whiteKnightSplit);
        data.putBoolean(DARK_YUFENG_SPLIT, darkYufengSplit);
        data.putBoolean(SOURCE_WAS_RELATED, sourceWasRelated);
        data.putInt(REQUIRED, requiredProgress(
                source, player, Mode.ABSORPTION, reason));
        data.putInt(PROGRESS, specialCompletion
                ? Math.max(18, data.getInt(REQUIRED) / 3) : 0);
        data.putInt(ATTEMPTS, 0);
        data.putInt(USED_APPROACHES, 0);
        data.putBoolean(NEGOTIATION_FAILED, false);
        data.remove(NEGOTIATION_RETRY_AT);
        data.putBoolean(EXTERNAL_RELEASE_PENDING, false);
        data.putBoolean(RELEASE_WHEN_SAFE, false);
        data.putBoolean(OPENED, false);
        data.putBoolean(RESPAWN_NOTICE, false);
        data.putBoolean(CAPTURE_PENDING, true);

        syncAbsorptionState(player, false);
    }

    private static ServerPlayer currentPlayerEntity(ServerPlayer original) {
        ServerPlayer current = original.server.getPlayerList()
                .getPlayer(original.getUUID());
        return current == null ? original : current;
    }

    private static boolean completeAbsorptionRelease(
            ServerPlayer player,
            CompoundTag claim) {
        return completeAbsorptionRelease(player, claim, true);
    }

    private static boolean completeAbsorptionRelease(
            ServerPlayer player,
            CompoundTag claim,
            boolean notifyNoSpace) {
        ReleaseBlockReason block = absorptionReleaseBlock(player);
        if (block != ReleaseBlockReason.NONE) {
            warnBlockedAbsorptionRelease(player, block);
            return false;
        }
        ChangedEntity released = createReleasedAbsorber(player, claim);
        if (released == null) {
            if (notifyNoSpace) {
                player.displayClientMessage(Component.translatable(
                        "message.changed_synergy.negotiation.no_release_space"),
                        true);
            }
            return false;
        }

        CompoundTag live = existingPlayerData(player);
        if (live != null) {
            live.putBoolean(ABSORPTION_RELEASING, true);
        }
        releasePlayerFromAbsorption(player, claim);

        LatexSocialMemory.settleAfterNegotiatedRelease(released, player);
        FactionHostilityGrace.begin(released, player);
        LatexSocialEvents.clearPendingCombatReactions(released, player);
        LatexSocialEvents.calmTowards(released, player);
        CreaturePersonality.establishRelationship(released, player);
        CreaturePersonality.setFamiliarity(
                released,
                player,
                Math.max(claim.getBoolean(SPECIAL_COMPLETION) ? 34 : 24,
                        CreaturePersonality.familiarity(released, player)));
        released.setPersistenceRequired();
        if (claim.getBoolean(FUSION_SPLIT)) {
            LatexFusionIntent.beginReleaseCooldown(released, player);
        }

        Cue cue = claim.getBoolean(WHITE_KNIGHT_SPLIT)
                ? Cue.NEGOTIATION_RELEASE_WHITE_KNIGHT
                : isChangedType(
                        readLocation(claim, ORIGINAL_SOURCE_TYPE),
                        "white_latex_knight")
                        ? Cue.NEGOTIATION_RELEASE_WHITE_KNIGHT_HUMAN
                : claim.getBoolean(DARK_YUFENG_SPLIT)
                        ? Cue.NEGOTIATION_RELEASE_DARK_YUFENG
                        : claim.getBoolean(SPECIAL_COMPLETION)
                                ? Cue.NEGOTIATION_RELEASE_COMPLETION
                                : Cue.NEGOTIATION_RELEASE_ABSORPTION;
        net.parkabird.changedsynergy.advancement.SynergyAdvancements.grant(
                player,
                net.parkabird.changedsynergy.advancement.SynergyAdvancements
                        .NEGOTIATED_RELEASE);
        if (claim.getBoolean(FUSION_SPLIT)) {
            net.parkabird.changedsynergy.advancement.SynergyAdvancements.grant(
                    player,
                    net.parkabird.changedsynergy.advancement.SynergyAdvancements
                            .FUSION_SEPARATION);
        }
        clearPlayerData(player);
        NpcDialogue.trigger(released, player, cue);
        return true;
    }

    private static void queueReleaseWhenSafe(
            ServerPlayer player,
            CompoundTag data,
            long now) {
        data.putBoolean(RELEASE_WHEN_SAFE, true);
        data.putLong(NEXT_ATTEMPT, now + 10L);
        player.closeContainer();
        syncAbsorptionState(player, false);
    }

    private static void tickQueuedRelease(
            ServerPlayer player,
            CompoundTag data) {
        long now = player.level().getGameTime();
        if (data.getLong(NEXT_ATTEMPT) > now) {
            return;
        }
        data.putLong(NEXT_ATTEMPT, now + 10L);
        if (absorptionReleaseBlock(player) != ReleaseBlockReason.NONE) {
            return;
        }
        if (!completeAbsorptionRelease(player, data.copy(), false)) {
            // A clear floor can still lack room for the restored creature's
            // full body. Keep the agreement queued while the player relocates.
            data.putLong(NEXT_ATTEMPT, now + 20L);
        }
    }

    @Nullable
    private static ChangedEntity createReleasedAbsorber(
            ServerPlayer player,
            CompoundTag claim) {
        boolean completedBody = claim.getBoolean(SPECIAL_COMPLETION);
        ResourceLocation typeId = completedBody
                ? null : readLocation(claim, ORIGINAL_SOURCE_TYPE);
        EntityType<?> type = typeId == null
                ? null : ForgeRegistries.ENTITY_TYPES.getValue(typeId);
        if (type == null) {
            type = entityTypeForForm(
                    player.server,
                    readLocation(claim, RESULT_PLAYER_FORM));
        }
        Entity created = type == null ? null : type.create(player.serverLevel());
        if (!(created instanceof ChangedEntity released)) {
            return null;
        }

        if (!completedBody
                && claim.contains(SOURCE_SNAPSHOT, Tag.TAG_COMPOUND)) {
            UUID freshId = released.getUUID();
            released.load(claim.getCompound(SOURCE_SNAPSHOT).copy());
            released.setUUID(freshId);
        }
        if (claim.contains(SOURCE_APPEARANCE, Tag.TAG_COMPOUND)) {
            released.getBasicPlayerInfo().load(
                    claim.getCompound(SOURCE_APPEARANCE));
        }

        Optional<Vec3> safe = BondedTeleportSafety.findSafeLanding(
                player.serverLevel(), released, player);
        if (safe.isEmpty()) {
            released.discard();
            return null;
        }
        Vec3 landing = safe.get();
        released.moveTo(
                landing.x, landing.y, landing.z,
                player.getYRot() + 180.0F, 0.0F);
        released.getPersistentData().remove(SOURCE_ROOT);
        released.getPersistentData().remove(RELEASE_ROOT);
        released.setPersistenceRequired();
        CreaturePersonality.ensure(released);
        CreatureIdentity.ensure(released);
        CreatureLifeMemory.ensure(released);
        CreatureCommunityData.bind(released);
        if (!player.serverLevel().addFreshEntity(released)) {
            released.discard();
            return null;
        }
        if (claim.hasUUID(SOURCE_UUID)) {
            UUID previousId = claim.getUUID(SOURCE_UUID);
            CreatureMorphAliasData.get(player.server)
                    .record(previousId, released.getUUID());
            PlayerRelationshipSettings.replaceContactReference(
                    player,
                    previousId,
                    released,
                    claim.getBoolean(SOURCE_WAS_RELATED));
        }
        return released;
    }

    private static void releasePlayerFromAbsorption(
            ServerPlayer player,
            CompoundTag claim) {
        ProcessTransfur.getPlayerTransfurVariantSafe(player).ifPresent(instance -> {
            if (player.level() instanceof ServerLevel level) {
                ChangedEntity appearance = instance.getChangedEntity();
                level.sendParticles(
                        ChangedParticles.drippingLatex(
                                appearance.getTransfurColor(
                                        TransfurCause.DEFAULT)),
                        player.getX(), player.getY() + 1.0D, player.getZ(),
                        28, 0.25D, 0.45D, 0.25D, 0.0D);
            }
        });
        if (claim.getBoolean(FUSION_SPLIT)
                || claim.getBoolean(WHITE_KNIGHT_SPLIT)) {
            restoreOriginalPlayerForm(player, claim);
        } else if (ProcessTransfur.isPlayerTransfurred(player)) {
            ProcessTransfur.removePlayerTransfurVariant(player);
        }
        ProcessTransfur.setPlayerTransfurProgress(player, 0.0F);
        playReverseTransfurSound(player);
    }

    private static void captureSourceState(
            CompoundTag data,
            ChangedEntity source,
            boolean incompleteCompletion) {
        CreaturePersonality.ensure(source);
        if (!incompleteCompletion
                && CreatureSocialProfile.allowsPersonalRelationship(source)) {
            CreatureIdentity.ensure(source);
        }
        data.putString(SOURCE_TRAIT,
                CreaturePersonality.dominantTrait(source).name());
        data.putString(SOURCE_NAME, source.getDisplayName().getString());

        CompoundTag appearance = new CompoundTag();
        source.getBasicPlayerInfo().save(appearance);
        data.put(SOURCE_APPEARANCE, appearance);

        CompoundTag snapshot = new CompoundTag();
        source.saveWithoutId(snapshot);
        for (String volatileKey : new String[] {
                "UUID", "UUIDMost", "UUIDLeast", "Pos", "Motion",
                "Rotation", "FallDistance", "Fire", "Air", "OnGround",
                "PortalCooldown", "Passengers", "Leash", "DeathTime"}) {
            snapshot.remove(volatileKey);
        }
        data.put(SOURCE_SNAPSHOT, snapshot);
    }

    private static Trait sourceTrait(CompoundTag data) {
        return readEnum(
                data.getString(SOURCE_TRAIT), Trait.class, Trait.CURIOUS);
    }

    private static void sendAbsorptionLine(
            ServerPlayer player,
            CompoundTag data,
            String group) {
        String branch = readView(data).reason().name()
                .toLowerCase(Locale.ROOT);
        if ("open".equals(group)) {
            if (data.getBoolean(WHITE_KNIGHT_SPLIT)) {
                branch = "white_knight_fusion";
            } else if (isChangedType(
                    readLocation(data, ORIGINAL_SOURCE_TYPE),
                    "white_latex_knight")) {
                branch = "white_knight_human";
            } else if (data.getBoolean(DARK_YUFENG_SPLIT)) {
                branch = "dark_yufeng_fusion";
            }
        }
        Component line = Component.translatable(
                "dialogue.changed_synergy.negotiation."
                        + branch + "." + group + ".0");
        player.sendSystemMessage(Component.translatable(
                "message.changed_synergy.negotiation.inner_voice",
                absorptionSourceName(player),
                line));
    }

    private static void beginRelease(
            ServerPlayer player,
            ChangedEntity source,
            View view,
            View before) {
        if (hasReleaseHold(source)) {
            return;
        }

        long now = source.level().getGameTime();
        CompoundTag release = new CompoundTag();
        release.putUUID(RELEASE_PLAYER, player.getUUID());
        release.putLong(RELEASE_REVERSE_AT, now + RELEASE_REVERSE_DELAY);
        release.putLong(RELEASE_END_AT, now + RELEASE_DURATION);
        release.putBoolean(RELEASE_REVERSED, false);
        release.putInt(RELEASE_PREVIOUS_PROGRESS, before.progress());
        release.putInt(RELEASE_PREVIOUS_USED, before.usedApproaches());
        release.putInt(RELEASE_PREVIOUS_ATTEMPTS, before.attempts());
        source.getPersistentData().put(RELEASE_ROOT, release);
        player.closeContainer();

        if (ChangedAddonCompat.tryStartNegotiatedRelease(
                source, player, RELEASE_DURATION)) {
            NpcDialogue.trigger(source, player, releaseHoldCue(view));
            return;
        }

        // Addon is optional. Never leave a completed negotiation stuck when
        // its presentation layer is unavailable.
        source.getPersistentData().remove(RELEASE_ROOT);
        applyPlayerReversal(player, view.whiteKnightSplit());
        release.putBoolean(RELEASE_REVERSED, true);
        source.getPersistentData().put(RELEASE_ROOT, release);
        finishReleaseHold(source, player);
    }

    private static void applyPlayerReversal(
            ServerPlayer player,
            boolean whiteKnightSplit) {
        ProcessTransfur.getPlayerTransfurVariantSafe(player).ifPresent(instance -> {
            if (player.level() instanceof ServerLevel level) {
                ChangedEntity appearance = instance.getChangedEntity();
                level.sendParticles(
                        ChangedParticles.drippingLatex(
                                appearance.getTransfurColor(
                                        TransfurCause.DEFAULT)),
                        player.getX(), player.getY() + 1.0D, player.getZ(),
                        40, 0.2D, 0.5D, 0.2D, 0.0D);
            }
        });
        if (whiteKnightSplit) {
            restoreOriginalPlayerForm(player);
        } else if (ProcessTransfur.isPlayerTransfurred(player)) {
            ProcessTransfur.removePlayerTransfurVariant(player);
        }
        ProcessTransfur.setPlayerTransfurProgress(player, 0.0F);
        playReverseTransfurSound(player);
    }

    private static void playReverseTransfurSound(ServerPlayer player) {
        SoundEvent addonSound = ForgeRegistries.SOUND_EVENTS
                .getValue(ADDON_UNTRANSFUR_SOUND);
        if (addonSound != null) {
            player.level().playSound(
                    null,
                    player.getX(), player.getY(), player.getZ(),
                    addonSound,
                    SoundSource.PLAYERS,
                    1.0F,
                    1.0F);
        } else {
            ChangedSounds.broadcastSound(
                    player,
                    ChangedSounds.TRANSFUR_BY_NOT_LATEX,
                    1.0F,
                    1.0F);
        }
    }

    private static boolean isOrganicPlayerForm(ServerPlayer player) {
        return ProcessTransfur.getPlayerTransfurVariantSafe(player)
                .map(instance -> LatexSocialMemory.isOrganic(
                        instance.getChangedEntity()))
                .orElse(false);
    }

    private static Cue releaseHoldCue(View view) {
        // Physical release holds belong to assimilation only. Absorption is
        // separated directly from the player's current body after agreement.
        return Cue.NEGOTIATION_RELEASE_HOLD_ASSIMILATION;
    }

    private static Cue releaseCue(View view) {
        return Cue.NEGOTIATION_RELEASE_ASSIMILATION;
    }

    private static void restoreOriginalPlayerForm(
            ServerPlayer player,
            CompoundTag claim) {
        ResourceLocation formId = readLocation(claim, ORIGINAL_PLAYER_FORM);
        if (formId == null) {
            if (ProcessTransfur.isPlayerTransfurred(player)) {
                ProcessTransfur.removePlayerTransfurVariant(player);
            }
            return;
        }
        Optional<Registry<TransfurVariant<?>>> registry = player.level().registryAccess()
                .registry(TRANSFUR_VARIANT_REGISTRY);
        TransfurVariant<?> variant = registry.map(value -> value.get(formId))
                .orElse(null);
        if (variant == null) {
            if (ProcessTransfur.isPlayerTransfurred(player)) {
                ProcessTransfur.removePlayerTransfurVariant(player);
            }
            return;
        }
        ProcessTransfur.setPlayerTransfurVariant(player, variant);
    }

    private static void restoreOriginalPlayerForm(ServerPlayer player) {
        CompoundTag claim = existingPlayerData(player);
        if (claim == null) {
            if (ProcessTransfur.isPlayerTransfurred(player)) {
                ProcessTransfur.removePlayerTransfurVariant(player);
            }
            return;
        }
        restoreOriginalPlayerForm(player, claim);
    }

    private static boolean restoreCapturedResultForm(
            ServerPlayer player,
            CompoundTag claim) {
        ResourceLocation formId = readLocation(claim, RESULT_PLAYER_FORM);
        if (formId == null) {
            return false;
        }
        Optional<Registry<TransfurVariant<?>>> registry = player.level().registryAccess()
                .registry(TRANSFUR_VARIANT_REGISTRY);
        TransfurVariant<?> variant = registry.map(value -> value.get(formId))
                .orElse(null);
        if (variant == null) {
            return false;
        }
        ProcessTransfur.setPlayerTransfurVariant(player, variant);
        ProcessTransfur.setPlayerTransfurProgress(player, 0.0F);
        return ProcessTransfur.isPlayerTransfurred(player);
    }

    private static int requiredProgress(
            ChangedEntity source,
            ServerPlayer player,
            Mode mode,
            Reason reason) {
        int required = mode == Mode.ABSORPTION ? 96 : 86;
        required += switch (reason) {
            case SELF_DEFENSE -> 30;
            case FACTION_RETALIATION -> 22;
            case CACHE_DEFENSE -> 26;
            case SPECIAL_COMPLETION -> -24;
            case FUSION_STRENGTH -> 10;
            case FUSION_CURIOSITY -> -8;
            case FUSION_PLAY -> -12;
            case FUSION_COMPLETION -> -28;
            default -> 0;
        };
        required += switch (CreaturePersonality.dominantTrait(source)) {
            case POLITE -> -16;
            case SENSITIVE -> -10;
            case CURIOUS, PLAYFUL -> -6;
            case CALM -> -3;
            case SHOW_OFF -> -2;
            case CAUTIOUS -> 8;
            case PROTECTIVE -> 12;
            case COMPETITIVE -> 15;
        };
        required -= Math.min(18, Math.max(0,
                CreaturePersonality.familiarity(source, player) / 4));
        int reputation = FactionReputation.score(source, player);
        required -= Mth.clamp(reputation / 12, -12, 12);
        return Mth.clamp(required, 58, 145);
    }

    private static int approachGain(
            Approach approach,
            Reason reason,
            Trait trait,
            boolean specialCompletion) {
        int gain = switch (approach) {
            case REASON -> 17;
            case EMPATHY -> 16;
            case APOLOGY -> reason == Reason.SELF_DEFENSE
                            || reason == Reason.CACHE_DEFENSE
                            || reason == Reason.FACTION_RETALIATION
                    ? 27 : 7;
            case BARGAIN -> 18;
            case INSIST -> 13;
            case FOOD_BRIBE -> 0;
        };
        gain += reasonApproachModifier(reason, approach);
        gain += switch (trait) {
            case POLITE -> approach == Approach.REASON ? 9 : 4;
            case SENSITIVE -> approach == Approach.EMPATHY
                    || approach == Approach.APOLOGY ? 8 : -1;
            case CURIOUS -> approach == Approach.REASON
                    || approach == Approach.BARGAIN ? 6 : 0;
            case PLAYFUL -> approach == Approach.BARGAIN ? 7 : 1;
            case SHOW_OFF -> approach == Approach.BARGAIN ? 9 : 0;
            case CALM -> approach == Approach.REASON ? 5 : 1;
            case CAUTIOUS -> approach == Approach.APOLOGY ? 6
                    : approach == Approach.INSIST ? -6 : 0;
            case PROTECTIVE -> approach == Approach.EMPATHY
                    || approach == Approach.APOLOGY ? 5
                    : approach == Approach.INSIST ? -5 : 0;
            case COMPETITIVE -> approach == Approach.INSIST ? 8
                    : approach == Approach.APOLOGY ? -3 : 0;
        };
        if (specialCompletion) {
            gain += approach == Approach.BARGAIN
                    || approach == Approach.EMPATHY ? 14 : 8;
        }
        return gain;
    }

    private static int approachBit(Approach approach) {
        return 1 << approach.ordinal();
    }

    private static List<Approach> preferredSequence(Reason reason) {
        return switch (reason) {
            case COMPANION_SEEKING -> List.of(
                    Approach.EMPATHY, Approach.REASON, Approach.BARGAIN,
                    Approach.APOLOGY, Approach.INSIST);
            case HOST_SEEKING -> List.of(
                    Approach.REASON, Approach.BARGAIN, Approach.EMPATHY,
                    Approach.INSIST, Approach.APOLOGY);
            case SELF_DEFENSE -> List.of(
                    Approach.APOLOGY, Approach.EMPATHY, Approach.REASON,
                    Approach.BARGAIN, Approach.INSIST);
            case FACTION_RETALIATION -> List.of(
                    Approach.APOLOGY, Approach.REASON, Approach.EMPATHY,
                    Approach.BARGAIN, Approach.INSIST);
            case CACHE_DEFENSE -> List.of(
                    Approach.APOLOGY, Approach.BARGAIN, Approach.REASON,
                    Approach.EMPATHY, Approach.INSIST);
            case SPECIAL_COMPLETION -> List.of(
                    Approach.EMPATHY, Approach.BARGAIN, Approach.REASON,
                    Approach.INSIST, Approach.APOLOGY);
            case FUSION_STRENGTH -> List.of(
                    Approach.INSIST, Approach.BARGAIN, Approach.REASON,
                    Approach.EMPATHY, Approach.APOLOGY);
            case FUSION_CURIOSITY -> List.of(
                    Approach.REASON, Approach.EMPATHY, Approach.BARGAIN,
                    Approach.INSIST, Approach.APOLOGY);
            case FUSION_PLAY -> List.of(
                    Approach.BARGAIN, Approach.EMPATHY, Approach.REASON,
                    Approach.INSIST, Approach.APOLOGY);
            case FUSION_COMPLETION -> List.of(
                    Approach.EMPATHY, Approach.BARGAIN, Approach.REASON,
                    Approach.APOLOGY, Approach.INSIST);
        };
    }

    private static int sequenceModifier(
            Reason reason,
            Approach approach,
            int step) {
        List<Approach> sequence = preferredSequence(reason);
        int expected = Mth.clamp(step, 0, sequence.size() - 1);
        int selected = sequence.indexOf(approach);
        if (selected == expected) {
            return 14;
        }
        return selected > expected
                ? -4 * (selected - expected)
                : -8 - 2 * (expected - selected);
    }

    private static int reasonApproachModifier(
            Reason reason,
            Approach approach) {
        if (approach == Approach.FOOD_BRIBE) {
            return 0;
        }
        return switch (reason) {
            case COMPANION_SEEKING -> switch (approach) {
                case EMPATHY -> 9;
                case REASON -> 6;
                case INSIST -> -5;
                default -> 0;
            };
            case HOST_SEEKING -> switch (approach) {
                case BARGAIN -> 10;
                case EMPATHY -> 6;
                case APOLOGY -> -4;
                default -> 0;
            };
            case SELF_DEFENSE -> approach == Approach.APOLOGY ? 8
                    : approach == Approach.INSIST ? -9 : 0;
            case FACTION_RETALIATION -> switch (approach) {
                case APOLOGY -> 7;
                case REASON -> 5;
                case INSIST -> -7;
                default -> 0;
            };
            case CACHE_DEFENSE -> switch (approach) {
                case APOLOGY -> 8;
                case BARGAIN -> 5;
                case INSIST -> -8;
                default -> 0;
            };
            case SPECIAL_COMPLETION -> switch (approach) {
                case EMPATHY, BARGAIN -> 8;
                case INSIST -> -5;
                default -> 0;
            };
            case FUSION_STRENGTH -> switch (approach) {
                case INSIST -> 10;
                case BARGAIN -> 7;
                case APOLOGY -> -5;
                default -> 0;
            };
            case FUSION_CURIOSITY -> switch (approach) {
                case REASON -> 10;
                case EMPATHY -> 7;
                case INSIST -> -4;
                default -> 0;
            };
            case FUSION_PLAY -> switch (approach) {
                case BARGAIN -> 11;
                case EMPATHY -> 6;
                case APOLOGY -> -3;
                default -> 0;
            };
            case FUSION_COMPLETION -> switch (approach) {
                case EMPATHY, BARGAIN -> 10;
                case INSIST -> -6;
                default -> 0;
            };
        };
    }

    private static boolean isPeacefulReason(Reason reason) {
        return reason == Reason.COMPANION_SEEKING
                || reason == Reason.HOST_SEEKING
                || reason == Reason.SPECIAL_COMPLETION
                || reason == Reason.FUSION_STRENGTH
                || reason == Reason.FUSION_CURIOSITY
                || reason == Reason.FUSION_PLAY
                || reason == Reason.FUSION_COMPLETION;
    }

    private static Reason classifyReason(
            ChangedEntity source,
            ServerPlayer player,
            Mode mode,
            boolean specialCompletion) {
        if (specialCompletion) {
            return Reason.SPECIAL_COMPLETION;
        }
        if (CreatureCacheGuardService.isDefendingAgainst(source, player)) {
            return Reason.CACHE_DEFENSE;
        }
        if (LatexSocialMemory.isProvoked(source, player)) {
            return Reason.SELF_DEFENSE;
        }
        if (FactionReputation.isHostile(source, player)) {
            return Reason.FACTION_RETALIATION;
        }
        return mode == Mode.ABSORPTION
                ? Reason.HOST_SEEKING : Reason.COMPANION_SEEKING;
    }

    private static boolean isVoluntaryCompanion(
            ChangedEntity source,
            ServerPlayer player) {
        if (LatexSocialMemory.bondedPlayerUuids(source)
                .contains(player.getUUID())) {
            return true;
        }
        CompoundTag social = source.getPersistentData()
                .getCompound("ChangedSynergySocial");
        if (social.hasUUID("PetOwner")
                && player.getUUID().equals(social.getUUID("PetOwner"))) {
            return true;
        }
        return source instanceof TamableLatexEntity pet
                && pet.isTame()
                && player.getUUID().equals(pet.getOwnerUUID());
    }

    private static boolean isIncompleteSpecial(@Nullable ResourceLocation type) {
        if (type == null || !"changed".equals(type.getNamespace())) {
            return false;
        }
        return LatexFusionIntent.isIncompleteFusionSource(type);
    }

    private static int foodBribeGain(Reason reason, Trait trait) {
        int gain = switch (reason) {
            case SELF_DEFENSE, FACTION_RETALIATION, CACHE_DEFENSE -> 29;
            case FUSION_COMPLETION, SPECIAL_COMPLETION -> 39;
            default -> 35;
        };
        gain += switch (trait) {
            case PLAYFUL, CURIOUS, SENSITIVE -> 4;
            case CAUTIOUS -> -3;
            default -> 0;
        };
        return gain;
    }

    private static boolean consumeFoodBribe(
            ServerPlayer player,
            @Nullable ChangedEntity source,
            CompoundTag data) {
        ChangedEntity target = source;
        boolean temporary = false;
        if (target == null) {
            target = createFoodProfile(player, data);
            temporary = target != null;
        }
        if (target == null) {
            return false;
        }
        int slot = findFoodBribeSlot(player, target);
        if (temporary) {
            target.discard();
        }
        if (slot < 0) {
            player.displayClientMessage(Component.translatable(
                    "message.changed_synergy.negotiation.no_bribe_food"),
                    true);
            return false;
        }
        consumeFoodBribeSlot(player, slot);
        return true;
    }

    private static int findAbsorptionFoodBribeSlot(
            ServerPlayer player,
            CompoundTag data) {
        ChangedEntity target = createFoodProfile(player, data);
        if (target == null) {
            return -1;
        }
        try {
            return findFoodBribeSlot(player, target);
        } finally {
            target.discard();
        }
    }

    private static void consumeFoodBribeSlot(
            ServerPlayer player,
            int slot) {
        if (!player.isCreative()
                && slot >= 0
                && slot < player.getInventory().getContainerSize()) {
            player.getInventory().getItem(slot).shrink(1);
            player.getInventory().setChanged();
        }
        net.parkabird.changedsynergy.advancement.SynergyAdvancements.grant(
                player,
                net.parkabird.changedsynergy.advancement.SynergyAdvancements
                        .FOOD_BRIBE);
    }

    private static int findFoodBribeSlot(
            ServerPlayer player,
            ChangedEntity source) {
        if (RelationshipFavorService.isFeline(source)) {
            for (int slot = 0;
                    slot < player.getInventory().getContainerSize();
                    slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.is(Items.COOKED_COD)
                        || stack.is(Items.COOKED_SALMON)) {
                    return slot;
                }
            }
            return -1;
        }
        int orangeSlot = -1;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (RelationshipFavorService.isDedicatedDietFood(source, stack)) {
                return slot;
            }
            if (orangeSlot < 0
                    && RelationshipFavorService.isOrange(stack)
                    && RelationshipFavorService.acceptsOrange(source)) {
                orangeSlot = slot;
            }
        }
        return orangeSlot;
    }

    @Nullable
    private static ChangedEntity createFoodProfile(
            ServerPlayer player,
            CompoundTag data) {
        ResourceLocation typeId = readLocation(data, ORIGINAL_SOURCE_TYPE);
        EntityType<?> type = typeId == null
                ? null : ForgeRegistries.ENTITY_TYPES.getValue(typeId);
        Entity created = type == null ? null : type.create(player.serverLevel());
        if (!(created instanceof ChangedEntity source)) {
            return null;
        }
        source.moveTo(player.getX(), player.getY(), player.getZ());
        return source;
    }

    private static boolean isChangedType(
            @Nullable ResourceLocation type,
            String path) {
        return type != null
                && "changed".equals(type.getNamespace())
                && path.equals(type.getPath());
    }

    private static boolean isOrdinaryWhiteLatexWolf(ServerPlayer player) {
        return ProcessTransfur.getPlayerTransfurVariantSafe(player)
                .map(instance -> ForgeRegistries.ENTITY_TYPES.getKey(
                        instance.getParent().getEntityType()))
                .map(id -> "changed".equals(id.getNamespace())
                        && ("white_latex_wolf_male".equals(id.getPath())
                                || "white_latex_wolf_female".equals(id.getPath())))
                .orElse(false);
    }

    @Nullable
    private static ResourceLocation currentForm(ServerPlayer player) {
        return ProcessTransfur.getPlayerTransfurVariantSafe(player)
                .map(instance -> instance.getParent().getFormId())
                .orElse(null);
    }

    private static View readView(CompoundTag data) {
        Mode mode = readEnum(data.getString(MODE), Mode.class, Mode.ASSIMILATION);
        Reason reason = readEnum(
                data.getString(REASON), Reason.class, Reason.COMPANION_SEEKING);
        return new View(
                mode,
                reason,
                data.getInt(PROGRESS),
                Math.max(1, data.getInt(REQUIRED)),
                data.getInt(ATTEMPTS),
                data.getInt(USED_APPROACHES),
                data.getBoolean(NEGOTIATION_FAILED),
                data.getBoolean(SPECIAL_COMPLETION),
                data.getBoolean(WHITE_KNIGHT_SPLIT));
    }

    private static <E extends Enum<E>> E readEnum(
            String name,
            Class<E> type,
            E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    @Nullable
    private static UUID sourceClaimant(ChangedEntity source) {
        CompoundTag marker = source.getPersistentData().getCompound(SOURCE_ROOT);
        return marker.hasUUID(CLAIMED_PLAYER)
                ? marker.getUUID(CLAIMED_PLAYER) : null;
    }

    private static void clearPreviousClaim(ServerPlayer player) {
        CompoundTag old = existingPlayerData(player);
        if (old == null || !old.hasUUID(SOURCE_UUID)) {
            return;
        }
        ChangedEntity previous = resolveSource(player, old);
        if (previous != null && player.getUUID().equals(sourceClaimant(previous))) {
            previous.getPersistentData().remove(SOURCE_ROOT);
        }
    }

    private static void abandonClaim(ServerPlayer player) {
        clearPreviousClaim(player);
        clearPlayerData(player);
    }

    @Nullable
    private static ChangedEntity findSource(
            MinecraftServer server,
            UUID sourceId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(sourceId);
            if (entity instanceof ChangedEntity source) {
                return source;
            }
        }
        return null;
    }

    /**
     * Resolves Changed's entity replacement and repairs stale player-side
     * references. Absorption kills the player before replacing the absorber,
     * so either half of that transition may otherwise retain the old UUID.
     */
    @Nullable
    private static ChangedEntity resolveSource(
            ServerPlayer player,
            CompoundTag data) {
        if (!data.hasUUID(SOURCE_UUID)) {
            return null;
        }

        UUID stored = data.getUUID(SOURCE_UUID);
        ChangedEntity exact = findSource(player.server, stored);
        if (exact != null
                && player.getUUID().equals(sourceClaimant(exact))) {
            return exact;
        }

        UUID aliased = CreatureMorphAliasData.get(player.server)
                .resolveAlias(stored);
        if (!aliased.equals(stored)) {
            ChangedEntity replacement = findSource(player.server, aliased);
            if (replacement != null
                    && player.getUUID().equals(sourceClaimant(replacement))) {
                repairSourceReference(data, replacement);
                return replacement;
            }
        }

        // A freshly loaded replacement can exist before an offline player's
        // saved UUID is repaired. The claimant marker identifies that body.
        for (ServerLevel level : player.server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof ChangedEntity candidate
                        && candidate.isAlive()
                        && !candidate.isRemoved()
                        && player.getUUID().equals(sourceClaimant(candidate))) {
                    repairSourceReference(data, candidate);
                    return candidate;
                }
            }
        }
        return null;
    }

    private static void repairSourceReference(
            CompoundTag data,
            ChangedEntity source) {
        data.putUUID(SOURCE_UUID, source.getUUID());
        rememberSource(data, source);
    }

    private static void rememberSource(
            CompoundTag data,
            ChangedEntity source) {
        ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(source.getType());
        data.putString(
                SOURCE_DIMENSION,
                source.level().dimension().location().toString());
        putLocation(data, SOURCE_TYPE, type);
        data.putDouble(SOURCE_X, source.getX());
        data.putDouble(SOURCE_Y, source.getY());
        data.putDouble(SOURCE_Z, source.getZ());
    }

    private static boolean hasPriorRelationship(
            ChangedEntity source,
            ServerPlayer player) {
        return LatexSocialMemory.isBonded(source, player)
                || LatexSocialMemory.isPetOwner(source, player)
                || CreaturePersonality.hasEstablishedRelationship(source, player);
    }

    @Nullable
    private static EntityType<?> entityTypeForForm(
            MinecraftServer server,
            @Nullable ResourceLocation formId) {
        if (formId == null) {
            return null;
        }
        Optional<Registry<TransfurVariant<?>>> registry =
                server.registryAccess().registry(TRANSFUR_VARIANT_REGISTRY);
        TransfurVariant<?> variant = registry
                .map(value -> value.get(formId))
                .orElse(null);
        return variant == null ? null : variant.getEntityType();
    }

    private static void clearPlayerData(ServerPlayer player) {
        persisted(player).remove(PLAYER_ROOT);
        syncAbsorptionState(player, false);
    }

    private static boolean isAbsorptionClaim(@Nullable CompoundTag data) {
        return data != null
                && data.hasUUID(SOURCE_UUID)
                && !data.getBoolean(CAPTURE_PENDING)
                && !data.getBoolean(EXTERNAL_RELEASE_PENDING)
                && Mode.ABSORPTION.name().equals(data.getString(MODE));
    }

    private static void beginFailureCooldown(CompoundTag data, long now) {
        data.putBoolean(NEGOTIATION_FAILED, true);
        data.putLong(NEGOTIATION_RETRY_AT,
                now + NEGOTIATION_FAILURE_COOLDOWN_TICKS);
    }

    private static void refreshFailureCooldown(
            ServerPlayer player,
            @Nullable CompoundTag data) {
        if (data == null || !data.getBoolean(NEGOTIATION_FAILED)
                || data.getLong(NEGOTIATION_RETRY_AT)
                        > player.level().getGameTime()) {
            return;
        }
        int required = Math.max(1, data.getInt(REQUIRED));
        data.putBoolean(NEGOTIATION_FAILED, false);
        data.remove(NEGOTIATION_RETRY_AT);
        data.putInt(PROGRESS, data.getBoolean(SPECIAL_COMPLETION)
                ? Math.max(18, required / 3) : 0);
        data.putInt(ATTEMPTS, 0);
        data.putInt(USED_APPROACHES, 0);
        data.putBoolean(OPENED, false);
        data.putLong(NEXT_ATTEMPT, 0L);
        if (Mode.ABSORPTION.name().equals(data.getString(MODE))) {
            syncAbsorptionState(player, true);
        }
    }

    private static void syncAbsorptionState(
            ServerPlayer player,
            boolean active) {
        ChangedSynergyNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new AbsorptionNegotiationStatePacket(active));
    }

    @Nullable
    private static CompoundTag existingPlayerData(ServerPlayer player) {
        CompoundTag persisted = persisted(player);
        return persisted.contains(PLAYER_ROOT, Tag.TAG_COMPOUND)
                ? persisted.getCompound(PLAYER_ROOT) : null;
    }

    private static CompoundTag playerData(ServerPlayer player) {
        CompoundTag persisted = persisted(player);
        if (!persisted.contains(PLAYER_ROOT, Tag.TAG_COMPOUND)) {
            persisted.put(PLAYER_ROOT, new CompoundTag());
        }
        return persisted.getCompound(PLAYER_ROOT);
    }

    private static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static void putLocation(
            CompoundTag tag,
            String key,
            @Nullable ResourceLocation value) {
        if (value == null) {
            tag.remove(key);
        } else {
            tag.putString(key, value.toString());
        }
    }

    @Nullable
    private static ResourceLocation readLocation(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_STRING)
                ? ResourceLocation.tryParse(tag.getString(key)) : null;
    }
}
