package net.parkabird.changedsynergy.dialogue;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.Emote;
import net.ltxprogrammer.changed.entity.TamableLatexEntity;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.HunterArchetype;
import net.parkabird.changedsynergy.ai.HunterFaction;
import net.parkabird.changedsynergy.ai.HypnosisProfile;
import net.parkabird.changedsynergy.ai.HumanIntent;
import net.parkabird.changedsynergy.ai.InvoluntaryTransfurNegotiation;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.ai.LatexFusionIntent;
import net.parkabird.changedsynergy.ai.TelepathyService;
import net.parkabird.changedsynergy.ai.TakeoverService;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ai.CreaturePersonality;
import net.parkabird.changedsynergy.ai.CreatureIdentity;
import net.parkabird.changedsynergy.ai.CreaturePersonality.Trait;
import net.parkabird.changedsynergy.ai.CreatureSocialProfile;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.TelepathyDialoguePacket;

public final class NpcDialogue {
    private static final int LINES_PER_CUE = 3;
    private static final int PERSONALITY_LINES_PER_CONTEXT = 2;
    private static final String NEXT_DIALOGUE = "ChangedSynergyNextDialogue";
    private static final String NEXT_PRIORITY_DIALOGUE = "ChangedSynergyNextPriorityDialogue";
    private static final String NEXT_PAT_SPEED_DIALOGUE =
            "ChangedSynergyNextPatSpeedDialogue";
    private static final String LAST_LINE = "ChangedSynergyLastDialogue";
    private static final String DIALOGUE_SUPPRESSED_UNTIL =
            "ChangedSynergyDialogueSuppressedUntil";
    private static final Map<UUID, Long> LISTENER_COOLDOWNS = new ConcurrentHashMap<>();
    private static final TagKey<EntityType<?>> SILENT_HUNTERS = tag("silent_hunters");
    private static final TagKey<EntityType<?>> EMOTELESS_HUNTERS = tag("emoteless_hunters");

    private NpcDialogue() {
    }

    /** Exact situation feedback, without replacing it with unrelated personality chatter. */
    public static void context(ChangedEntity speaker, ServerPlayer listener, String suffix) {
        if (!speaker.isAlive() || listener.isSpectator()
                || TakeoverService.active(listener)
                || !CreatureSocialProfile.allowsDialogue(speaker)
                || speaker.getType().is(SILENT_HUNTERS)
                || !speaker.level().getGameRules().getBoolean(ChangedSynergyGameRules.NPC_DIALOGUE)) return;
        long now = speaker.level().getGameTime();
        if (speaker.getPersistentData().getLong("SynergyContextLineAt") > now) return;
        speaker.getPersistentData().putLong("SynergyContextLineAt", now + 20L);
        speaker.getPersistentData().putLong(NEXT_PRIORITY_DIALOGUE, now + 60L);
        speaker.getPersistentData().putLong(NEXT_DIALOGUE, now + 60L);
        DialogueVoice voice = DialogueVoice.of(speaker, HunterFaction.of(speaker));
        Component line = Component.translatable(canUnderstand(speaker, listener)
                ? "dialogue.changed_synergy.context." + suffix
                : vocalizationKey(speaker, listener, null,
                        speaker.getRandom().nextInt(LINES_PER_CUE)));
        Component message = Component.translatable("message.changed_synergy.npc_dialogue",
                speaker.getDisplayName(), line).withStyle(ChatFormatting.ITALIC);
        if (usesRelationshipChat(speaker, listener)) listener.sendSystemMessage(message);
        else ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> listener),
                new TelepathyDialoguePacket(message, speaker.getDisplayName(), line, factionColor(voice)));
    }

    public static boolean trigger(ChangedEntity speaker, ServerPlayer focus, Cue cue) {
        return trigger(speaker, focus, cue, new Object[0]);
    }

    public static boolean trigger(
            ChangedEntity speaker,
            ServerPlayer focus,
            Cue cue,
            Object... lineArguments) {
        return trigger(speaker, focus, cue, false, lineArguments);
    }

    /** Uses the dominant-trait line for state-machine dialogue that defines a personality decision. */
    public static boolean triggerPersonality(
            ChangedEntity speaker,
            ServerPlayer focus,
            Cue cue,
            Object... lineArguments) {
        return trigger(speaker, focus, cue, true, lineArguments);
    }

    private static boolean trigger(
            ChangedEntity speaker,
            ServerPlayer focus,
            Cue cue,
            boolean forcePersonality,
            Object... lineArguments) {
        if (!(speaker.level() instanceof ServerLevel level)
                || !speaker.isAlive()
                || !CreatureSocialProfile.allowsDialogue(speaker)
                || TakeoverService.active(focus) && !isTakeoverCue(cue)
                || dialogueSuppressed(speaker, level)) {
            return false;
        }

        boolean humanIntentCue = cue == Cue.SPOTTED
                && !ProcessTransfur.isPlayerTransfurred(focus);
        Object[] visibleArguments = usesSocialAddress(cue)
                ? withSocialAddress(speaker, focus, lineArguments)
                : lineArguments;
        PersonalityContext personalityContext = personalityContext(speaker, focus, cue);
        boolean juvenileCue = CreatureSocialProfile.isJuvenile(speaker);
        Trait personalityTrait = ChangedSynergyGameRules.enabled(
                                level, ChangedSynergyGameRules.PERSONALITY_SYSTEM)
                        && (forcePersonality || !juvenileCue)
                        && personalityContext != null
                        && !humanIntentCue
                        && !isNegotiationCue(cue)
                        && (forcePersonality
                                || speaker.getRandom().nextDouble()
                                        < ChangedSynergyConfig.COMMON.personalityDialogueChance.get())
                ? CreaturePersonality.dominantTrait(speaker)
                : null;
        showEmote(level, speaker, personalityEmote(
                personalityTrait, personalityContext,
                humanIntentCue
                        ? humanIntentEmote(speaker)
                        : selectEmote(speaker, cue)),
                emotePriority(cue), emoteDuration(cue),
                forcesEmoteTransition(cue));
        if (speaker.getType().is(SILENT_HUNTERS)
                || !level.getGameRules().getBoolean(ChangedSynergyGameRules.NPC_DIALOGUE)) {
            return false;
        }

        long now = level.getGameTime();
        String speakerCooldown = isPatSpeedCue(cue)
                ? NEXT_PAT_SPEED_DIALOGUE
                : cue.important ? NEXT_PRIORITY_DIALOGUE : NEXT_DIALOGUE;
        if (!cue.bypassPriorityCooldown
                && speaker.getPersistentData().getLong(speakerCooldown) > now) {
            return false;
        }

        double chance = cue.important ? 1.0 : Math.min(1.0,
                ChangedSynergyConfig.COMMON.npcDialogueChance.get() * cue.chanceMultiplier);
        if (speaker.getRandom().nextDouble() > chance) {
            return false;
        }

        HunterFaction faction = HunterFaction.of(speaker);
        DialogueVoice voice = DialogueVoice.of(speaker, faction);
        LatexTerritory territory = !humanIntentCue
                        && usesTerritoryTone(cue)
                        && (faction == HunterFaction.WHITE || faction == HunterFaction.DARK)
                ? LatexTerritory.around(speaker, faction)
                : null;
        int lineIndex = speaker.getRandom().nextInt(cue.lineCount);
        String lineKey = lineKey(speaker, voice, cue, focus, territory, lineIndex);
        int personalityIndex = personalityTrait == null
                ? -1 : speaker.getRandom().nextInt(PERSONALITY_LINES_PER_CONTEXT);
        String personalityKey = personalityTrait == null
                ? null : personalityLineKey(personalityTrait, personalityContext, personalityIndex);
        String lineIdentity = lineIdentity(lineKey, personalityKey);
        String previous = speaker.getPersistentData().getString(LAST_LINE);
        if (lineIdentity.equals(previous)) {
            lineIndex = (lineIndex + 1) % cue.lineCount;
            lineKey = lineKey(speaker, voice, cue, focus, territory, lineIndex);
            if (personalityKey != null) {
                personalityIndex =
                        (personalityIndex + 1) % PERSONALITY_LINES_PER_CONTEXT;
                personalityKey = personalityLineKey(
                        personalityTrait, personalityContext, personalityIndex);
            }
            lineIdentity = lineIdentity(lineKey, personalityKey);
        }

        double range = ChangedSynergyConfig.COMMON.npcDialogueRange.get();
        double rangeSqr = range * range;
        boolean delivered = false;
        for (ServerPlayer listener : level.players()) {
            if (isPrivateInteractionCue(cue) && listener != focus) {
                continue;
            }
            boolean priorityFocus = cue.important && listener == focus;
            if ((!listener.isAlive() && !priorityFocus) || listener.isSpectator()
                    || listener.distanceToSqr(speaker) > rangeSqr
                    || !cue.important && !isPatSpeedCue(cue)
                            && LISTENER_COOLDOWNS.getOrDefault(
                                    listener.getUUID(), 0L) > now) {
                continue;
            }

            boolean understands = (isTakeoverCue(cue) && listener == focus)
                    || canUnderstand(speaker, listener);
            String visibleKey = understands
                    ? personalityKey != null ? personalityKey : lineKey
                    : vocalizationKey(speaker, listener, cue,
                            speaker.getRandom().nextInt(LINES_PER_CUE));
            MutableComponent visibleLine = Component.translatable(
                    visibleKey, visibleArguments);
            Component message = Component.translatable(
                    "message.changed_synergy.npc_dialogue",
                    speaker.getDisplayName(), visibleLine)
                    .withStyle(style -> style.withColor(color(voice)).withItalic(true));
            if (isTakeoverCue(cue) || usesRelationshipChat(speaker, listener)) {
                listener.sendSystemMessage(message);
            } else {
                ChangedSynergyNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> listener),
                        new TelepathyDialoguePacket(
                                message,
                                speaker.getDisplayName(),
                                visibleLine,
                                factionColor(voice)));
            }
            if (!isPatSpeedCue(cue)) {
                LISTENER_COOLDOWNS.put(listener.getUUID(), now + cooldownTicks());
            }
            delivered = true;
        }

        if (delivered) {
            speaker.getPersistentData().putLong(speakerCooldown,
                    now + (cue.important ? 80L : cooldownTicks()));
            if (cue.important) {
                speaker.getPersistentData().putLong(NEXT_DIALOGUE, now + cooldownTicks());
            }
            speaker.getPersistentData().putString(LAST_LINE, lineIdentity);
            CreaturePersonality.rememberEncounter(speaker, focus);
            if (LISTENER_COOLDOWNS.size() > 256) {
                LISTENER_COOLDOWNS.entrySet().removeIf(entry -> entry.getValue() <= now);
            }
        }
        return delivered;
    }

    /**
     * Prevents damage and targeting callbacks later in the current server tick
     * from making a creature speak after a hit that is already known to be
     * lethal.
     */
    public static void suppressForCurrentTick(ChangedEntity speaker) {
        if (speaker.level() instanceof ServerLevel level) {
            speaker.getPersistentData().putLong(
                    DIALOGUE_SUPPRESSED_UNTIL, level.getGameTime() + 1L);
        }
    }

    /**
     * Delivers a bonded creature's final line directly to its owner.  Death has
     * already made the speaker non-living at this point, so this deliberately
     * bypasses range, chance, ordinary cooldowns and the alive check.
     */
    public static boolean triggerBondFarewell(
            ChangedEntity speaker,
            ServerPlayer focus,
            Cue cue) {
        if (!(speaker.level() instanceof ServerLevel level)
                || !CreatureSocialProfile.allowsDialogue(speaker)
                || speaker.getType().is(SILENT_HUNTERS)
                || !level.getGameRules().getBoolean(ChangedSynergyGameRules.NPC_DIALOGUE)) {
            return false;
        }

        HunterFaction faction = HunterFaction.of(speaker);
        DialogueVoice voice = DialogueVoice.of(speaker, faction);
        int lineIndex = speaker.getRandom().nextInt(cue.lineCount);
        String lineKey = lineKey(speaker, voice, cue, focus, null, lineIndex);
        String previous = speaker.getPersistentData().getString(LAST_LINE);
        if (lineKey.equals(previous)) {
            lineIndex = (lineIndex + 1) % cue.lineCount;
            lineKey = lineKey(speaker, voice, cue, focus, null, lineIndex);
        }

        MutableComponent visibleLine = Component.translatable(lineKey);
        Component message = Component.translatable(
                "message.changed_synergy.npc_dialogue",
                speaker.getDisplayName(), visibleLine)
                .withStyle(style -> style.withColor(color(voice)).withItalic(true));
        focus.sendSystemMessage(message);
        speaker.getPersistentData().putString(LAST_LINE, lineKey);
        return true;
    }

    /** Shows the cue's species-specific emotion without adding another chat line. */
    public static void emoteOnly(ChangedEntity speaker, Cue cue) {
        if (speaker.level() instanceof ServerLevel level
                && speaker.isAlive()
                && CreatureSocialProfile.allowsDialogue(speaker)
                && !dialogueSuppressed(speaker, level)) {
            showEmote(
                    level,
                    speaker,
                    selectEmote(speaker, cue),
                    emotePriority(cue),
                    emoteDuration(cue),
                    forcesEmoteTransition(cue));
        }
    }

    public static void emoteOnly(ChangedEntity speaker, Emote emote) {
        if (speaker.level() instanceof ServerLevel level
                && speaker.isAlive()
                && CreatureSocialProfile.allowsDialogue(speaker)
                && !dialogueSuppressed(speaker, level)) {
            showEmote(level, speaker, emote, 2, 50, false);
        }
    }

    /** Uses the speaker's cooldown/rules while placing the bubble above another living entity. */
    public static void emoteTarget(ChangedEntity speaker, LivingEntity target, Emote emote) {
        if (speaker.level() instanceof ServerLevel level
                && speaker.isAlive()
                && target.isAlive()
                && CreatureSocialProfile.allowsDialogue(speaker)
                && !dialogueSuppressed(speaker, level)) {
            showEmote(level, speaker, target, emote, 3, 55, true);
        }
    }

    private static boolean dialogueSuppressed(
            ChangedEntity speaker,
            ServerLevel level) {
        return speaker.getPersistentData().getLong(DIALOGUE_SUPPRESSED_UNTIL)
                > level.getGameTime();
    }

    private static void showEmote(
            ServerLevel level,
            ChangedEntity speaker,
            Emote emote,
            int priority,
            int durationTicks,
            boolean forceTransition) {
        showEmote(
                level,
                speaker,
                speaker,
                emote,
                priority,
                durationTicks,
                forceTransition);
    }

    private static void showEmote(
            ServerLevel level,
            ChangedEntity speaker,
            LivingEntity target,
            Emote emote,
            int priority,
            int durationTicks,
            boolean forceTransition) {
        if (speaker.getType().is(EMOTELESS_HUNTERS)
                || !level.getGameRules().getBoolean(ChangedSynergyGameRules.NPC_EMOTES)) {
            return;
        }
        NpcEmoteState.show(
                level,
                speaker,
                target,
                emote,
                priority,
                durationTicks,
                forceTransition);
    }

    private static boolean canUnderstand(ChangedEntity speaker, ServerPlayer player) {
        // This speaker can communicate directly; do not unlock the listener's telepathy.
        if (HypnosisProfile.isHypnoticCreature(speaker)
                || ProcessTransfur.isPlayerTransfurred(player)
                || TelepathyService.canUnderstand(player)
                || !ChangedSynergyConfig.COMMON.npcDialogueUsesTranslator.get()) {
            return true;
        }

        if (ChangedAddonCompat.isLoaded()) {
            for (ItemStack stack : player.getInventory().items) {
                if (stack.isEmpty()
                        || !ChangedAddonCompat.TRANSLATOR.equals(
                                ForgeRegistries.ITEMS.getKey(stack.getItem()))) {
                    continue;
                }
                if (!stack.hasTag() || stack.getTag().getBoolean("Enabled")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean usesRelationshipChat(
            ChangedEntity speaker,
            ServerPlayer listener) {
        if (LatexSocialMemory.isBonded(speaker, listener)
                || LatexSocialMemory.isPetOwner(speaker, listener)
                || CreaturePersonality.hasTrustedRelationship(
                        speaker, listener)) {
            return true;
        }
        return speaker instanceof TamableLatexEntity nativePet
                && nativePet.isTame()
                && listener.getUUID().equals(nativePet.getOwnerUUID());
    }

    private static long cooldownTicks() {
        return ChangedSynergyConfig.COMMON.npcDialogueCooldownSeconds.get() * 20L;
    }

    private static String lineKey(
            ChangedEntity speaker,
            DialogueVoice voice,
            Cue cue,
            ServerPlayer focus,
            LatexTerritory territory,
            int index) {
        String cueName = cue.name().toLowerCase(Locale.ROOT);
        if (cue == Cue.FUSION_APPROACH || cue == Cue.FUSION_COMPLETE) {
            return "dialogue.changed_synergy.fusion."
                    + LatexFusionIntent.reasonFor(speaker).name()
                            .toLowerCase(Locale.ROOT)
                    + (cue == Cue.FUSION_APPROACH
                            ? ".approach." : ".complete.")
                    + index;
        }
        if (isCentaurCue(cue)) {
            ResourceLocation typeId =
                    ForgeRegistries.ENTITY_TYPES.getKey(speaker.getType());
            String species = typeId != null
                            && "latex_gnoll_taur".equals(typeId.getPath())
                    ? "gnoll_taur"
                    : "white_centaur";
            String relationship = LatexSocialMemory.isPetOwner(speaker, focus)
                            || LatexSocialMemory.isBonded(speaker, focus)
                    ? "bonded"
                    : "friend";
            return "dialogue.changed_synergy.centaur."
                    + species + "." + cueName + "_" + relationship
                    + "." + index;
        }
        if (CreatureSocialProfile.isJuvenile(speaker)) {
            return "dialogue.changed_synergy.juvenile_voice."
                    + juvenileVoice(speaker) + "." + juvenileMood(cue)
                    + "." + index;
        }
        if (isPatSpeedCue(cue)) {
            return "dialogue.changed_synergy.pat_speed."
                    + cueName.substring("pat_speed_".length())
                    + "." + index;
        }
        if (isHypnosisCue(cue)) {
            return "dialogue.changed_synergy.hypnosis."
                    + HypnosisProfile.of(speaker).dialogueKey()
                    + "." + cueName + "." + index;
        }
        if (isRoleCue(cue)) {
            return "dialogue.changed_synergy.role."
                    + cueName + "." + index;
        }
        if (isNegotiationCue(cue)) {
            String reason = InvoluntaryTransfurNegotiation.view(focus)
                    .map(view -> view.reason().name()
                            .toLowerCase(Locale.ROOT))
                    .orElse("companion_seeking");
            String group = negotiationCueGroup(cue);
            return "dialogue.changed_synergy.negotiation."
                    + (group.startsWith("release_") ? "common" : reason)
                    + "." + group
                    + "." + index;
        }
        if (isTakeoverCue(cue)) {
            return "dialogue.changed_synergy.takeover." + cueName + "." + index;
        }
        if (cue == Cue.SPOTTED && !ProcessTransfur.isPlayerTransfurred(focus)) {
            cueName += "_human_" + HumanIntent.of(speaker).dialogueKey();
        } else if (usesAudienceTone(cue)) {
            cueName += ProcessTransfur.isPlayerTransfurred(focus)
                    ? "_transfurred"
                    : "_human";
        }
        if (territory != null) {
            cueName += "_" + territory.suffix();
        }
        return "dialogue.changed_synergy." + voice.id
                + "." + cueName + "." + index;
    }

    private static boolean isHypnosisCue(Cue cue) {
        return switch (cue) {
            case HYPNOSIS_START, HYPNOSIS_ESCAPE,
                    HYPNOSIS_FAILED, HYPNOSIS_INTERRUPTED,
                    HYPNOSIS_PAT_SURPRISED, HYPNOSIS_PAT_PLEASED,
                    HYPNOSIS_WELCOME, HYPNOSIS_PLAY_START,
                    HYPNOSIS_PLAY_SUCCESS, HYPNOSIS_PLAY_FAILED -> true;
            default -> false;
        };
    }

    private static boolean isPatSpeedCue(Cue cue) {
        return switch (cue) {
            case PAT_SPEED_VERY_SLOW, PAT_SPEED_SLOW,
                    PAT_SPEED_GENTLE, PAT_SPEED_FAST,
                    PAT_SPEED_VERY_FAST -> true;
            default -> false;
        };
    }

    private static boolean isPrivateInteractionCue(Cue cue) {
        String name = cue.name();
        return name.startsWith("PAT_")
                || name.startsWith("SOCIAL_")
                || name.startsWith("RELATIONSHIP_")
                || name.startsWith("NEGOTIATION_")
                || name.startsWith("TAKEOVER_")
                || name.startsWith("HYPNOSIS_")
                || name.startsWith("BOND_")
                || name.startsWith("ROLE_PROVISIONER_GIFT_")
                || name.startsWith("LOW_REPUTATION_")
                || name.startsWith("CENTAUR_")
                || cue == Cue.CAT_ORANGE_REFUSED
                || cue == Cue.FRIEND_BETRAYAL_PAT_REFUSED;
    }

    private static boolean isNegotiationCue(Cue cue) {
        return switch (cue) {
            case NEGOTIATION_OPEN_ASSIMILATION,
                    NEGOTIATION_OPEN_ABSORPTION,
                    NEGOTIATION_OPEN_COMPLETION,
                    NEGOTIATION_REASON,
                    NEGOTIATION_EMPATHY,
                    NEGOTIATION_APOLOGY,
                    NEGOTIATION_BARGAIN,
                    NEGOTIATION_INSIST,
                    NEGOTIATION_FOOD_BRIBE,
                    NEGOTIATION_RESIST,
                    NEGOTIATION_FAILED,
                    NEGOTIATION_RELEASE_HOLD_ASSIMILATION,
                    NEGOTIATION_RELEASE_ASSIMILATION,
                    NEGOTIATION_RELEASE_ABSORPTION,
                    NEGOTIATION_RELEASE_COMPLETION,
                    NEGOTIATION_RELEASE_WHITE_KNIGHT,
                    NEGOTIATION_RELEASE_WHITE_KNIGHT_HUMAN,
                    NEGOTIATION_RELEASE_DARK_YUFENG -> true;
            default -> false;
        };
    }

    private static boolean isTakeoverCue(Cue cue) {
        return switch (cue) {
            case TAKEOVER_PROACTIVE_START, TAKEOVER_REACTIVE_START,
                    TAKEOVER_PROACTIVE_AMBIENT, TAKEOVER_REACTIVE_AMBIENT,
                    TAKEOVER_BYSTANDER,
                    TAKEOVER_BORROW_GRANTED_PROACTIVE, TAKEOVER_BORROW_GRANTED_REACTIVE,
                    TAKEOVER_BORROW_EARLY_PROACTIVE, TAKEOVER_BORROW_EARLY_REACTIVE,
                    TAKEOVER_BORROW_COOLDOWN_PROACTIVE, TAKEOVER_BORROW_COOLDOWN_REACTIVE,
                    TAKEOVER_BORROW_AIRBORNE, TAKEOVER_BORROW_DANGER,
                    TAKEOVER_BORROW_BUSY,
                    TAKEOVER_CONTROL_RETURNED_PROACTIVE, TAKEOVER_CONTROL_RETURNED_REACTIVE,
                    TAKEOVER_ESCAPE_CONFIRM_PROACTIVE, TAKEOVER_ESCAPE_CONFIRM_REACTIVE,
                    TAKEOVER_STRUGGLE_PROACTIVE, TAKEOVER_STRUGGLE_REACTIVE,
                    TAKEOVER_SLEEP_PROACTIVE, TAKEOVER_SLEEP_REACTIVE,
                    TAKEOVER_BED_SLEEP_PROACTIVE, TAKEOVER_BED_SLEEP_REACTIVE,
                    TAKEOVER_TRANSFUR_SLEEP_FAILED_PROACTIVE,
                    TAKEOVER_TRANSFUR_SLEEP_FAILED_REACTIVE,
                    TAKEOVER_TRANSFUR_SLEEP_EXPIRED_PROACTIVE,
                    TAKEOVER_TRANSFUR_SLEEP_EXPIRED_REACTIVE -> true;
            default -> false;
        };
    }

    private static String negotiationCueGroup(Cue cue) {
        return switch (cue) {
            case NEGOTIATION_OPEN_ASSIMILATION,
                    NEGOTIATION_OPEN_ABSORPTION,
                    NEGOTIATION_OPEN_COMPLETION -> "open";
            case NEGOTIATION_REASON -> "reason";
            case NEGOTIATION_EMPATHY -> "empathy";
            case NEGOTIATION_APOLOGY -> "apology";
            case NEGOTIATION_BARGAIN -> "bargain";
            case NEGOTIATION_INSIST -> "insist";
            case NEGOTIATION_FOOD_BRIBE -> "food_bribe";
            case NEGOTIATION_RESIST -> "resist";
            case NEGOTIATION_FAILED -> "failed";
            case NEGOTIATION_RELEASE_HOLD_ASSIMILATION ->
                    "release_hold_assimilation";
            case NEGOTIATION_RELEASE_ASSIMILATION -> "release_assimilation";
            case NEGOTIATION_RELEASE_ABSORPTION -> "release_absorption";
            case NEGOTIATION_RELEASE_COMPLETION -> "release_completion";
            case NEGOTIATION_RELEASE_WHITE_KNIGHT -> "release_white_knight";
            case NEGOTIATION_RELEASE_WHITE_KNIGHT_HUMAN ->
                    "release_white_knight_human";
            case NEGOTIATION_RELEASE_DARK_YUFENG -> "release_dark_yufeng";
            default -> "open";
        };
    }

    private static boolean isCentaurCue(Cue cue) {
        return switch (cue) {
            case CENTAUR_TACK_OPEN, CENTAUR_SADDLE_EQUIP,
                    CENTAUR_PACK_EQUIP, CENTAUR_PACK_OPEN,
                    CENTAUR_RIDE_START, CENTAUR_RIDE_END,
                    BOND_FISHING_START, BOND_FISHING_SUCCESS,
                    BOND_MINING_START, BOND_MINING_SUCCESS -> true;
            default -> false;
        };
    }

    private static boolean isRoleCue(Cue cue) {
        return switch (cue) {
            case ROLE_SCOUT_MARK, ROLE_GUARD_RALLY,
                    ROLE_FORAGER_FOUND, ROLE_FORAGER_STORE,
                    ROLE_PROVISIONER_GIFT_RESPECTED,
                    ROLE_PROVISIONER_GIFT_ALLIED,
                    ROLE_PROVISIONER_GIFT_FRIEND,
                    ROLE_PROVISIONER_GIFT_CLOSE,
                    ROLE_PROVISIONER_GIFT_BONDED,
                    ROLE_FISHING_FOCUS_DISTRUSTED,
                    ROLE_FISHING_FOCUS_NEUTRAL,
                    ROLE_FISHING_FOCUS_RECOGNIZED,
                    ROLE_FISHING_FOCUS_RESPECTED,
                    ROLE_FISHING_FOCUS_ALLIED,
                    ROLE_FISHING_FOCUS_FRIEND,
                    ROLE_FISHING_FOCUS_CLOSE,
                    ROLE_CARETAKER_AID, ROLE_COURIER_CARRY,
                    ROLE_LOOKOUT_WARNING, ROLE_COORDINATOR_RALLY,
                    ROLE_WANDERER_DISCOVERY, ROLE_YOUNGSTER_RETREAT,
                    COMFORT_BOX_DISCOVERED -> true;
            default -> false;
        };
    }

    private static boolean usesTerritoryTone(Cue cue) {
        return switch (cue) {
            case SPOTTED, MEET_SPECIES, MEET_CATEGORY, MEET_FRIENDLY,
                    MEET_RIVAL, MEET_OUTSIDER -> true;
            default -> false;
        };
    }

    private static boolean usesAudienceTone(Cue cue) {
        return switch (cue) {
            case SPOTTED, ALERT, HEARD, LOST, REACQUIRED, GIVE_UP,
                    GUNSHOT_HOSTILE, GUNSHOT_FRIENDLY,
                    ORGANIC_GRAPPLE_BITE, ORGANIC_GRAPPLE_CLAW, ORGANIC_GRAPPLE_PIN -> true;
            default -> false;
        };
    }

    private static String juvenileVoice(ChangedEntity speaker) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(speaker.getType());
        String path = id == null ? "" : id.getPath();
        return switch (path) {
            case "dark_latex_wolf_pup" -> "dark_pup";
            case "pure_white_latex_wolf_pup" -> "white_pup";
            case "gas_wolf_pup" -> "gas_pup";
            default -> HunterFaction.isOrganic(speaker)
                    ? "gas_pup"
                    : switch (HunterFaction.of(speaker)) {
                        case WHITE -> "white_pup";
                        case DARK -> "dark_pup";
                        default -> "gas_pup";
                    };
        };
    }

    private static String juvenileMood(Cue cue) {
        return switch (cue.emote) {
            case HEART, CASUAL -> "friendly";
            case IDEA, CONFUSED -> "curious";
            case ANGRY, DENY -> "warning";
            case NERVOUS, STARTLED -> "distress";
            default -> "quiet";
        };
    }

    private static boolean usesSocialAddress(Cue cue) {
        return switch (cue) {
            case RELATIONSHIP_ESTABLISHED, RELATIONSHIP_DIET_ESTABLISHED,
                    SOCIAL_TALK, SOCIAL_TALK_NEW, SOCIAL_TALK_FAMILIAR,
                    SOCIAL_PAT, SOCIAL_PAT_NEW, SOCIAL_PAT_FAMILIAR,
                    SOCIAL_PAT_CLOSE, SOCIAL_GIFT, SOCIAL_DIET_GIFT,
                    SOCIAL_GIFT_REFUSED, SOCIAL_GIFT_UNSUITABLE,
                    SOCIAL_PLAY, SOCIAL_PLAY_HUG_BONDED,
                    SOCIAL_PLAY_HUG_FRIEND,
                    SOCIAL_PLAY_HUG_RELEASE_BONDED,
                    SOCIAL_PLAY_HUG_RELEASE_FRIEND,
                    SOCIAL_REST,
                    VOLUNTARY_BOND_CONFIRM, VOLUNTARY_BOND_COMPLETE,
                    SOCIAL_FOLLOW, SOCIAL_WAIT, SOCIAL_GOODBYE,
                    SOCIAL_TALK_HURT, SOCIAL_TALK_PLAYER_HURT,
                    SOCIAL_TALK_RAIN, SOCIAL_TALK_NIGHT,
                    SOCIAL_TALK_CLOSE, BOND_COMBAT_GRAB_ASSIST,
                    BOND_WITNESS_COMPATRIOT_ATTACK,
                    FRIEND_WITNESS_COMPATRIOT_ATTACK,
                    FRIEND_COMBAT_ASSIST,
                    FRIEND_KIN_KILL_WARNING,
                    FRIEND_KIN_KILL_BETRAYAL,
                    FRIEND_BETRAYAL_PAT_REFUSED,
                    ORGANIC_EVACUATION_START_NEW,
                    ORGANIC_EVACUATION_START_FAMILIAR,
                    ORGANIC_EVACUATION_START_CLOSE,
                    ORGANIC_EVACUATION_RELEASE_NEW,
                    ORGANIC_EVACUATION_RELEASE_FAMILIAR,
                    ORGANIC_EVACUATION_RELEASE_CLOSE,
                    CENTAUR_TACK_OPEN, CENTAUR_SADDLE_EQUIP,
                    CENTAUR_PACK_EQUIP, CENTAUR_PACK_OPEN,
                    CENTAUR_RIDE_START, CENTAUR_RIDE_END -> true;
            default -> false;
        };
    }

    /**
     * Social lines receive the relationship title as %1$s, the player's ID as
     * %2$s, then any cue-specific arguments. Relationship-establishment lines
     * therefore use %3$s for the creature's own generated name.
     */
    private static Object[] withSocialAddress(
            ChangedEntity speaker,
            ServerPlayer focus,
            Object[] lineArguments) {
        Object[] addressed = new Object[lineArguments.length + 2];
        String addressKey;
        if (LatexSocialMemory.isPetOwner(speaker, focus)
                && LatexSocialMemory.usesNativePetMenu(speaker)) {
            addressKey = "address.changed_synergy.master";
        } else if (LatexSocialMemory.isBonded(speaker, focus)) {
            addressKey = HunterFaction.of(speaker) == HunterFaction.DARK
                    ? "address.changed_synergy.dark_partner"
                    : "address.changed_synergy.partner";
        } else if (LatexSocialMemory.isPetOwner(speaker, focus)) {
            addressKey = "address.changed_synergy.master";
        } else {
            addressKey = "address.changed_synergy.friend";
        }
        addressed[0] = Component.translatable(addressKey);
        addressed[1] = focus.getDisplayName();
        System.arraycopy(
                lineArguments, 0, addressed, 2, lineArguments.length);
        return addressed;
    }

    private static String vocalizationKey(
            ChangedEntity speaker,
            ServerPlayer listener,
            Cue cue,
            int index) {
        return "dialogue.changed_synergy.vocalization."
                + CreatureIdentity.vocalizationProfile(speaker)
                + "." + vocalMood(speaker, listener, cue)
                + "." + index;
    }

    private static String vocalMood(
            ChangedEntity speaker,
            ServerPlayer listener,
            Cue cue) {
        if (cue != null && (cue.emote == Emote.ANGRY
                || cue.emote == Emote.DENY)) {
            return "hostile";
        }
        if (cue == Cue.LOST || cue == Cue.GIVE_UP
                || cue == Cue.ALLY_FALLEN
                || cue == Cue.BOND_DEATH_DIRECT
                || cue == Cue.BOND_DEATH_WRAPPING) {
            return "sad";
        }
        if (cue == Cue.SPOTTED
                && !ProcessTransfur.isPlayerTransfurred(listener)
                && HumanIntent.of(speaker) != HumanIntent.GREET) {
            return "happy";
        }
        CreaturePersonality.RelationshipTier tier =
                CreaturePersonality.relationshipTier(speaker, listener);
        if (LatexSocialMemory.isBonded(speaker, listener)
                || tier == CreaturePersonality.RelationshipTier.CLOSE) {
            return "fond";
        }
        if (cue != null && (cue.emote == Emote.HEART
                || cue.emote == Emote.CASUAL
                || cue.emote == Emote.IDEA)) {
            return "happy";
        }
        return "neutral";
    }

    private static PersonalityContext personalityContext(
            ChangedEntity speaker,
            ServerPlayer focus,
            Cue cue) {
        return switch (cue) {
            case SPOTTED, ALERT, REACQUIRED -> PersonalityContext.HUNT;
            case LOST, GIVE_UP -> PersonalityContext.SEARCH;
            case MEET_ALLY, MEET_SPECIES, MEET_CATEGORY, MEET_FRIENDLY,
                    FORMER_BOND_WELCOME, FORMER_RESPECT_WELCOME,
                    FRIEND_RESPECT_WELCOME,
                    MEET_OUTSIDER ->
                    CreaturePersonality.remembersFondly(speaker, focus)
                            ? PersonalityContext.FAMILIAR
                            : PersonalityContext.SOCIAL;
            case SOCIAL_TALK, SOCIAL_TALK_CLOSE ->
                    PersonalityContext.FAMILIAR;
            // Hugs keep their faction/relationship-specific lines. Replacing
            // them with a generic personality play line makes the spoken line
            // stop matching the action on screen.
            case SOCIAL_PLAY -> PersonalityContext.PLAY;
            case PAT_BONDED, PAT_KIN, PAT_CATEGORY, PAT_FRIEND,
                    PAT_FORMER_BONDED, PAT_FORMER_RESPECT,
                    PAT_FRIEND_RESPECT,
                    PAT_OUTSIDER, PAT_HUMAN, PAT_RIVAL,
                    PAT_BONDED_NEW_FORM,
                    PAT_DISGUISED_DARK, PAT_DISGUISED_DARK_INSPECTED,
                    PAT_DISGUISED_DARK_CAUGHT, PAT_DISGUISED_WHITE_RIVAL ->
                    PersonalityContext.PAT_RECEIVED;
            case PAT_PLAYER_BONDED, PAT_PLAYER_KIN, PAT_PLAYER_CATEGORY,
                    PAT_PLAYER_FRIEND, PAT_PLAYER_OTHER,
                    PAT_PLAYER_FORMER_BONDED, PAT_PLAYER_FORMER_RESPECT,
                    PAT_PLAYER_FRIEND_RESPECT,
                    PAT_PLAYER_BONDED_NEW_FORM, PAT_PLAYER_DISGUISED_DARK,
                    PAT_PLAYER_HUMAN_FRIEND ->
                    PersonalityContext.PAT_GIVEN;
            case POLITE_RESPONSE, RELATIONSHIP_PROGRESS,
                    SOCIAL_PAT -> PersonalityContext.PAT_RECEIVED;
            case HIT_CONFUSED, HIT_WARNING -> PersonalityContext.HURT;
            case BETRAYED -> PersonalityContext.TRUCE_BETRAYAL;
            case PAT_TRUCE_REFUSED -> PersonalityContext.PAT_REFUSED;
            case NEGOTIATION_RELEASE_HOLD_ASSIMILATION,
                    BOND_REVERSE_HOLD, BOND_REVERSE_COMPLETE ->
                    PersonalityContext.RELEASE;
            case BOND_SAFETY_HOLD_READY -> PersonalityContext.SAFETY_HOLD_READY;
            case BOND_SAFETY_RELEASE_REFUSE_FIRST ->
                    PersonalityContext.SAFETY_RELEASE_REFUSE_FIRST;
            case BOND_SAFETY_RELEASE_REFUSE_REPEAT ->
                    PersonalityContext.SAFETY_RELEASE_REFUSE_REPEAT;
            case BOND_SAFETY_RELEASE_ACCEPT ->
                    PersonalityContext.SAFETY_RELEASE_ACCEPT;
            default -> null;
        };
    }

    private static String personalityLineKey(
            Trait trait,
            PersonalityContext context,
            int index) {
        return "dialogue.changed_synergy.personality."
                + trait.dialogueKey() + "." + context.key + "." + index;
    }

    private static String lineIdentity(String lineKey, String personalityKey) {
        return personalityKey == null ? lineKey : personalityKey;
    }

    private static Emote personalityEmote(
            Trait trait,
            PersonalityContext context,
            Emote fallback) {
        if (trait == null || context == null) {
            return fallback;
        }
        return switch (trait) {
            case CURIOUS -> Emote.IDEA;
            case CAUTIOUS -> context == PersonalityContext.HURT
                            || context == PersonalityContext.TRUCE_BETRAYAL
                            || context == PersonalityContext.PAT_REFUSED
                            || context == PersonalityContext.HUNT
                    ? Emote.STARTLED : Emote.CONFUSED;
            case PROTECTIVE -> context == PersonalityContext.PAT_GIVEN
                            || context == PersonalityContext.PAT_RECEIVED
                            || context == PersonalityContext.FAMILIAR
                            || context == PersonalityContext.PLAY
                    ? Emote.HEART : Emote.DENY;
            case SHOW_OFF -> Emote.CASUAL;
            case CALM -> Emote.CASUAL;
            case COMPETITIVE -> context == PersonalityContext.HURT
                            || context == PersonalityContext.TRUCE_BETRAYAL
                            || context == PersonalityContext.PAT_REFUSED
                    ? Emote.ANGRY : Emote.IDEA;
            case PLAYFUL -> context == PersonalityContext.TRUCE_BETRAYAL
                            || context == PersonalityContext.PAT_REFUSED
                    ? Emote.DENY : Emote.HEART;
            case SENSITIVE -> Emote.NERVOUS;
            case POLITE -> context == PersonalityContext.HURT
                            || context == PersonalityContext.TRUCE_BETRAYAL
                            || context == PersonalityContext.PAT_REFUSED
                    ? Emote.CONFUSED : Emote.CASUAL;
        };
    }

    private static Emote selectEmote(ChangedEntity speaker, Cue cue) {
        HunterArchetype archetype = HunterArchetype.of(speaker);
        return switch (cue) {
            case SUCCESS_ASSIMILATE, SUCCESS_ABSORB,
                    BOND_WELCOME_REPLICATE, BOND_WELCOME_ABSORB,
                    VOLUNTARY_BOND_COMPLETE,
                    HYPNOSIS_WELCOME,
                    SECONDARY_TRANSFUR,
                    BOND_WRAP_REVERTED, BOND_WRAP_TRANSFURRED,
                    BOND_WRAP_NEW_FORM, BOND_REASSIMILATE,
                    BOND_RELEASE_REVERTED, BOND_RELEASE_TRANSFURRED,
                    BOND_EMERGENCY_WRAP, BOND_COMBAT_WRAP,
                    BOND_EMERGENCY_RECOVERED,
                    BOND_DROWNING_RECOVERED,
                    BOND_TRANSFUR_RECOVERED,
                    ORGANIC_SECONDARY_TRANSFUR,
                    ORGANIC_BOND_CONFLICT_ASSIMILATION,
                    BOND_NEW_FORM_WELCOME,
                    MEET_SPECIES, MEET_CATEGORY, MEET_FRIENDLY,
                    FORMER_BOND_WELCOME, FORMER_RESPECT_WELCOME,
                    FRIEND_RESPECT_WELCOME,
                    PAT_BONDED, PAT_KIN, PAT_CATEGORY, PAT_FRIEND,
                    PAT_FORMER_BONDED, PAT_FORMER_RESPECT, PAT_FRIEND_RESPECT,
                    PAT_PLAYER_BONDED, PAT_PLAYER_KIN, PAT_PLAYER_CATEGORY,
                    PAT_PLAYER_FRIEND, PAT_PLAYER_OTHER,
                    PAT_PLAYER_FRIEND_RESPECT,
                    PAT_PLAYER_HUMAN_FRIEND, POLITE_RESPONSE,
                    RELATIONSHIP_PROGRESS, RELATIONSHIP_DIET_GIFT,
                    RELATIONSHIP_DIET_ESTABLISHED, SOCIAL_DIET_GIFT,
                    SOCIAL_PLAY_HUG_BONDED, SOCIAL_PLAY_HUG_FRIEND,
                    SOCIAL_PLAY_HUG_RELEASE_BONDED,
                    SOCIAL_PLAY_HUG_RELEASE_FRIEND,
                    PAT_PLAYER_FORMER_BONDED, PAT_PLAYER_FORMER_RESPECT,
                    PAT_BONDED_NEW_FORM, PAT_PLAYER_BONDED_NEW_FORM -> switch (archetype) {
                case SOLDIER, AVIAN, INSECT -> Emote.IDEA;
                case FELINE, DRACONIC, ROYAL -> Emote.CASUAL;
                default -> Emote.HEART;
            };
            case ORGANIC_GRAPPLE_BITE -> Emote.CASUAL;
            case ORGANIC_GRAPPLE_CLAW -> Emote.IDEA;
            case ORGANIC_GRAPPLE_PIN -> Emote.HEART;
            case PAT_HUMAN, PAT_OUTSIDER -> switch (archetype) {
                case FELINE, DRACONIC, ROYAL -> Emote.CASUAL;
                default -> Emote.CONFUSED;
            };
            case LOW_REPUTATION_PAT -> switch (archetype) {
                case FELINE, DRACONIC, ROYAL -> Emote.CASUAL;
                default -> Emote.CONFUSED;
            };
            case LOW_REPUTATION_FOOD -> switch (archetype) {
                case SOLDIER, INSECT -> Emote.IDEA;
                default -> Emote.CASUAL;
            };
            case LOW_REPUTATION_DIET_FOOD -> switch (archetype) {
                case SOLDIER, INSECT -> Emote.CASUAL;
                default -> Emote.HEART;
            };
            case POLITE_NOTICE -> Emote.IDEA;
            case POLITE_PROBE -> switch (archetype) {
                case FELINE, DRACONIC, ROYAL -> Emote.CASUAL;
                default -> Emote.CONFUSED;
            };
            case POLITE_NO_RESPONSE -> Emote.PAUSE;
            case HIT_CONFUSED -> switch (archetype) {
                case CRITTER -> Emote.NERVOUS;
                case AVIAN -> Emote.STARTLED;
                default -> Emote.CONFUSED;
            };
            case HIT_WARNING, PAT_RIVAL, BETRAYED, PAT_TRUCE_REFUSED -> switch (archetype) {
                case CRITTER -> Emote.NERVOUS;
                case AVIAN -> Emote.STARTLED;
                default -> Emote.DENY;
            };
            case GUNSHOT_HOSTILE -> switch (archetype) {
                case CRITTER -> Emote.NERVOUS;
                case AVIAN -> Emote.STARTLED;
                default -> Emote.ANGRY;
            };
            case GUNSHOT_FRIENDLY -> switch (archetype) {
                case SOLDIER, INSECT -> Emote.IDEA;
                default -> Emote.STARTLED;
            };
            case FIREARM_NOTICED -> switch (archetype) {
                case SOLDIER, INSECT -> Emote.IDEA;
                default -> Emote.CONFUSED;
            };
            case HYPNOSIS_START -> switch (archetype) {
                case FELINE, DRACONIC, ROYAL -> Emote.CASUAL;
                default -> Emote.IDEA;
            };
            case HYPNOSIS_ESCAPE -> switch (archetype) {
                case CRITTER -> Emote.NERVOUS;
                default -> Emote.STARTLED;
            };
            case HYPNOSIS_FAILED -> switch (archetype) {
                case SOLDIER, INSECT -> Emote.IDEA;
                case FELINE, DRACONIC, ROYAL -> Emote.CASUAL;
                default -> Emote.HEART;
            };
            case HYPNOSIS_PLAY_START -> Emote.IDEA;
            case HYPNOSIS_PLAY_SUCCESS -> Emote.STARTLED;
            case HYPNOSIS_PLAY_FAILED -> Emote.HEART;
            case HYPNOSIS_INTERRUPTED -> Emote.DENY;
            case HYPNOSIS_PAT_SURPRISED -> Emote.CONFUSED;
            case HYPNOSIS_PAT_PLEASED -> Emote.HEART;
            case DISGUISE_DARK_ACCEPTED, DISGUISE_DARK_INSPECTED,
                    PAT_DISGUISED_DARK, PAT_DISGUISED_DARK_INSPECTED,
                    PAT_PLAYER_DISGUISED_DARK -> switch (archetype) {
                case SOLDIER, INSECT -> Emote.IDEA;
                case FELINE, DRACONIC, ROYAL -> Emote.CASUAL;
                default -> Emote.CONFUSED;
            };
            case DISGUISE_DARK_REVEALED, DISGUISE_DARK_BETRAYAL,
                    DISGUISE_DARK_DROPPED, DISGUISE_WHITE_RIVAL,
                    PAT_DISGUISED_DARK_CAUGHT, PAT_DISGUISED_WHITE_RIVAL ->
                    switch (archetype) {
                        case CRITTER -> Emote.NERVOUS;
                        case AVIAN -> Emote.STARTLED;
                        default -> Emote.DENY;
                    };
            case BOND_JEALOUS_NEW_FORM -> switch (archetype) {
                case CRITTER -> Emote.NERVOUS;
                case FELINE, DRACONIC, ROYAL -> Emote.DENY;
                default -> Emote.CONFUSED;
            };
            case BOND_RIVAL_ABSORPTION -> switch (archetype) {
                case SOLDIER, INSECT -> Emote.IDEA;
                case FELINE, DRACONIC, ROYAL -> Emote.CASUAL;
                default -> Emote.HEART;
            };
            case HOSTILITY_CONFIRMED, ATTACKED_BY_RIVAL,
                    COMPATRIOT_DEFENSE,
                    FRIEND_WITNESS_COMPATRIOT_ATTACK,
                    FRIEND_KIN_KILL_BETRAYAL -> switch (archetype) {
                case CRITTER -> Emote.NERVOUS;
                case AVIAN -> Emote.STARTLED;
                default -> Emote.ANGRY;
            };
            case FRIEND_KIN_KILL_WARNING,
                    FRIEND_BETRAYAL_PAT_REFUSED -> switch (archetype) {
                case CRITTER -> Emote.NERVOUS;
                case AVIAN -> Emote.STARTLED;
                default -> Emote.DENY;
            };
            case MEET_ALLY -> switch (archetype) {
                case SOLDIER -> Emote.PAUSE;
                case FELINE, DRACONIC, ROYAL -> Emote.CASUAL;
                default -> Emote.HEART;
            };
            case MEET_RIVAL -> switch (archetype) {
                case FELINE -> Emote.DENY;
                case CRITTER -> Emote.NERVOUS;
                case AVIAN -> Emote.STARTLED;
                default -> Emote.ANGRY;
            };
            case MEET_OUTSIDER -> switch (archetype) {
                case CANINE, CRITTER -> Emote.STARTLED;
                case FELINE, DRACONIC, ROYAL -> Emote.CONFUSED;
                default -> Emote.IDEA;
            };
            case ALLY_FALLEN -> switch (archetype) {
                case SOLDIER, ROYAL, DRACONIC -> Emote.ANGRY;
                default -> Emote.NERVOUS;
            };
            default -> cue.emote;
        };
    }

    private static Emote humanIntentEmote(ChangedEntity speaker) {
        return switch (HumanIntent.of(speaker)) {
            case GREET -> Emote.IDEA;
            case SEEK_HOST -> Emote.CASUAL;
            case ASSIMILATE -> Emote.CONFUSED;
        };
    }

    private static int emotePriority(Cue cue) {
        return switch (cue) {
            case ROUTINE_REST, ROUTINE_ROAM, ROUTINE_PATROL,
                    ROUTINE_FORAGE, ROUTINE_SOCIALIZE, ROUTINE_WATCH,
                    ROUTINE_RETURN_CENTER, ROUTINE_CONSENSUS -> 1;
            case ROLE_SCOUT_MARK, ROLE_GUARD_RALLY,
                    ROLE_FORAGER_FOUND, ROLE_FORAGER_STORE,
                    ROLE_CARETAKER_AID, ROLE_COURIER_CARRY,
                    ROLE_LOOKOUT_WARNING, ROLE_COORDINATOR_RALLY,
                    ROLE_WANDERER_DISCOVERY, ROLE_YOUNGSTER_RETREAT,
                    COMMUNITY_MIGRATION, WHITE_REFORMATION -> 2;
            case BOND_DEATH_DIRECT, BOND_DEATH_WRAPPING, BOND_DEATH_TOGETHER,
                    BOND_EMERGENCY_WRAP, BOND_COMBAT_WRAP,
                    BOND_COMBAT_GRAB_ASSIST,
                    ORGANIC_EVACUATION_START_NEW,
                    ORGANIC_EVACUATION_START_FAMILIAR,
                    ORGANIC_EVACUATION_START_CLOSE,
                    BOND_DROWNING_RESCUE, BOND_TRANSFUR_RESCUE,
                    FRIEND_KIN_KILL_BETRAYAL, HYPNOSIS_FAILED -> 6;
            case HOSTILITY_CONFIRMED, ATTACKED_BY_RIVAL, BETRAYED,
                    PAT_TRUCE_REFUSED, GUNSHOT_HOSTILE, COMPATRIOT_DEFENSE,
                    BOND_WITNESS_COMPATRIOT_ATTACK,
                    FRIEND_WITNESS_COMPATRIOT_ATTACK,
                    FRIEND_KIN_KILL_WARNING,
                    FRIEND_BETRAYAL_PAT_REFUSED,
                    DISGUISE_DARK_REVEALED, DISGUISE_DARK_BETRAYAL,
                    RELATIONSHIP_ESTABLISHED, RELATIONSHIP_DIET_ESTABLISHED,
                    SOCIAL_GIFT_REFUSED -> 5;
            case HIT_CONFUSED, HIT_WARNING, HYPNOSIS_START, HYPNOSIS_ESCAPE,
                    HYPNOSIS_INTERRUPTED, POLITE_RESPONSE,
                    RELATIONSHIP_PROGRESS, RELATIONSHIP_GIFT,
                    RELATIONSHIP_DIET_GIFT,
                    LOW_REPUTATION_PAT, LOW_REPUTATION_FOOD,
                    LOW_REPUTATION_DIET_FOOD,
                    SOCIAL_PAT, SOCIAL_PAT_NEW, SOCIAL_PAT_FAMILIAR,
                    SOCIAL_PAT_CLOSE, SOCIAL_GIFT, SOCIAL_DIET_GIFT,
                    CAT_ORANGE_REFUSED, SOCIAL_GIFT_UNSUITABLE,
                    SOCIAL_PLAY, SOCIAL_PLAY_HUG_BONDED,
                    SOCIAL_PLAY_HUG_FRIEND,
                    SOCIAL_PLAY_HUG_RELEASE_BONDED,
                    SOCIAL_PLAY_HUG_RELEASE_FRIEND,
                    SOCIAL_REST,
                    SOCIAL_FOLLOW, SOCIAL_WAIT,
                    FRIEND_COMBAT_ASSIST,
                    FACTION_COMBAT_ASSIST -> 4;
            case SPOTTED, ALERT, HEARD, LOST, REACQUIRED,
                    FIREARM_NOTICED,
                    POLITE_NOTICE, POLITE_PROBE, POLITE_NO_RESPONSE,
                    SOCIAL_TALK, SOCIAL_TALK_NEW, SOCIAL_TALK_FAMILIAR,
                    SOCIAL_TALK_HURT,
                    SOCIAL_TALK_PLAYER_HURT, SOCIAL_TALK_RAIN,
                    SOCIAL_TALK_NIGHT, SOCIAL_TALK_CLOSE,
                    SOCIAL_GOODBYE,
                    ORGANIC_EVACUATION_RELEASE_NEW,
                    ORGANIC_EVACUATION_RELEASE_FAMILIAR,
                    ORGANIC_EVACUATION_RELEASE_CLOSE -> 2;
            default -> cue.important ? 4 : 3;
        };
    }

    private static int emoteDuration(Cue cue) {
        int priority = emotePriority(cue);
        return priority >= 6 ? 76 : priority >= 5 ? 70
                : priority >= 4 ? 62 : priority >= 3 ? 54 : 46;
    }

    private static boolean forcesEmoteTransition(Cue cue) {
        return switch (cue) {
            case LOST, REACQUIRED, GIVE_UP,
                    WHITE_KNIGHT_FUSION_APPROACH,
                    WHITE_KNIGHT_FUSION_COMPLETE,
                    FUSION_APPROACH, FUSION_COMPLETE,
                    NEGOTIATION_OPEN_ASSIMILATION,
                    NEGOTIATION_OPEN_ABSORPTION,
                    NEGOTIATION_OPEN_COMPLETION,
                    NEGOTIATION_REASON, NEGOTIATION_EMPATHY,
                    NEGOTIATION_APOLOGY, NEGOTIATION_BARGAIN,
                    NEGOTIATION_INSIST, NEGOTIATION_FOOD_BRIBE,
                    NEGOTIATION_RESIST,
                    NEGOTIATION_FAILED,
                    NEGOTIATION_RELEASE_HOLD_ASSIMILATION,
                    NEGOTIATION_RELEASE_ASSIMILATION,
                    NEGOTIATION_RELEASE_ABSORPTION,
                    NEGOTIATION_RELEASE_COMPLETION,
                    NEGOTIATION_RELEASE_WHITE_KNIGHT,
                    NEGOTIATION_RELEASE_WHITE_KNIGHT_HUMAN,
                    NEGOTIATION_RELEASE_DARK_YUFENG,
                    BOND_REVERSE_HOLD, BOND_REVERSE_COMPLETE,
                    RELATIONSHIP_ESTABLISHED, RELATIONSHIP_DIET_ESTABLISHED,
                    POLITE_RESPONSE, RELATIONSHIP_PROGRESS,
                    RELATIONSHIP_GIFT, RELATIONSHIP_DIET_GIFT,
                    LOW_REPUTATION_PAT, LOW_REPUTATION_FOOD,
                    LOW_REPUTATION_DIET_FOOD,
                    POLITE_NO_RESPONSE, SOCIAL_PAT, SOCIAL_PAT_NEW,
                    SOCIAL_PAT_FAMILIAR, SOCIAL_PAT_CLOSE, SOCIAL_FOLLOW,
                    SOCIAL_GIFT, SOCIAL_DIET_GIFT, SOCIAL_GIFT_REFUSED,
                    CAT_ORANGE_REFUSED, SOCIAL_PLAY,
                    SOCIAL_PLAY_HUG_BONDED, SOCIAL_PLAY_HUG_FRIEND,
                    SOCIAL_PLAY_HUG_RELEASE_BONDED,
                    SOCIAL_PLAY_HUG_RELEASE_FRIEND,
                    SOCIAL_REST,
                    VOLUNTARY_BOND_CONFIRM, VOLUNTARY_BOND_COMPLETE,
                    SOCIAL_GIFT_UNSUITABLE,
                    ROLE_PROVISIONER_GIFT_RESPECTED,
                    ROLE_PROVISIONER_GIFT_ALLIED,
                    ROLE_PROVISIONER_GIFT_FRIEND,
                    ROLE_PROVISIONER_GIFT_CLOSE,
                    ROLE_PROVISIONER_GIFT_BONDED,
                    ROLE_FISHING_FOCUS_DISTRUSTED,
                    ROLE_FISHING_FOCUS_NEUTRAL,
                    ROLE_FISHING_FOCUS_RECOGNIZED,
                    ROLE_FISHING_FOCUS_RESPECTED,
                    ROLE_FISHING_FOCUS_ALLIED,
                    ROLE_FISHING_FOCUS_FRIEND,
                    ROLE_FISHING_FOCUS_CLOSE,
                    SOCIAL_WAIT, SOCIAL_GOODBYE,
                    CENTAUR_TACK_OPEN, CENTAUR_SADDLE_EQUIP,
                    CENTAUR_PACK_EQUIP, CENTAUR_PACK_OPEN,
                    CENTAUR_RIDE_START, CENTAUR_RIDE_END,
                    HIT_CONFUSED, HIT_WARNING, HOSTILITY_CONFIRMED,
                    COMPATRIOT_DEFENSE, BOND_WITNESS_COMPATRIOT_ATTACK,
                    FRIEND_WITNESS_COMPATRIOT_ATTACK,
                    FRIEND_COMBAT_ASSIST,
                    FACTION_COMBAT_ASSIST,
                    FRIEND_KIN_KILL_WARNING,
                    FRIEND_KIN_KILL_BETRAYAL,
                    FRIEND_BETRAYAL_PAT_REFUSED,
                    HYPNOSIS_ESCAPE, HYPNOSIS_FAILED, HYPNOSIS_INTERRUPTED,
                    HYPNOSIS_PLAY_SUCCESS, HYPNOSIS_PLAY_FAILED,
                    BOND_EMERGENCY_RECOVERED, BOND_DROWNING_RECOVERED,
                    BOND_TRANSFUR_RESCUE, BOND_TRANSFUR_RECOVERED,
                    BOND_COMBAT_GRAB_ASSIST,
                    BOND_RELEASE_REVERTED, BOND_RELEASE_TRANSFURRED,
                    ORGANIC_EVACUATION_START_NEW,
                    ORGANIC_EVACUATION_START_FAMILIAR,
                    ORGANIC_EVACUATION_START_CLOSE,
                    ORGANIC_EVACUATION_RELEASE_NEW,
                    ORGANIC_EVACUATION_RELEASE_FAMILIAR,
                    ORGANIC_EVACUATION_RELEASE_CLOSE -> true;
            default -> false;
        };
    }

    private static ChatFormatting color(DialogueVoice voice) {
        return switch (voice) {
            case WHITE -> ChatFormatting.WHITE;
            case DARK -> ChatFormatting.DARK_PURPLE;
            case ORGANIC -> ChatFormatting.DARK_RED;
            case AQUATIC -> ChatFormatting.AQUA;
            case LIGHT -> ChatFormatting.GOLD;
        };
    }

    private static int factionColor(DialogueVoice voice) {
        return switch (voice) {
            case WHITE -> 0xF2F5FF;
            case DARK -> 0x9B59D0;
            case ORGANIC -> 0xC65A4A;
            case AQUATIC -> 0x4FD6E8;
            case LIGHT -> 0xE5A84B;
        };
    }

    private static TagKey<EntityType<?>> tag(String path) {
        return TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath("changed_synergy", path));
    }

    private enum PersonalityContext {
        HUNT("hunt"),
        SEARCH("search"),
        SOCIAL("social"),
        FAMILIAR("familiar"),
        PLAY("play"),
        PAT_RECEIVED("pat_received"),
        PAT_GIVEN("pat_given"),
        HURT("hurt"),
        TRUCE_BETRAYAL("truce_betrayal"),
        PAT_REFUSED("pat_refused"),
        RELEASE("release"),
        SAFETY_HOLD_READY("safety_hold_ready"),
        SAFETY_RELEASE_REFUSE_FIRST("safety_release_refuse_first"),
        SAFETY_RELEASE_REFUSE_REPEAT("safety_release_refuse_repeat"),
        SAFETY_RELEASE_ACCEPT("safety_release_accept");

        private final String key;

        PersonalityContext(String key) {
            this.key = key;
        }
    }

    /** Speech and presentation style, independent from political allegiance. */
    private enum DialogueVoice {
        WHITE("white"),
        DARK("dark"),
        ORGANIC("organic"),
        AQUATIC("aquatic"),
        LIGHT("light");

        private final String id;

        DialogueVoice(String id) {
            this.id = id;
        }

        private static DialogueVoice of(
                ChangedEntity speaker,
                HunterFaction faction) {
            if (HunterFaction.isOrganic(speaker)) {
                return ORGANIC;
            }
            return switch (faction) {
                case WHITE -> WHITE;
                case DARK -> DARK;
                case AQUATIC -> AQUATIC;
                case LIGHT -> LIGHT;
            };
        }
    }

    public enum Cue {
        TAKEOVER_PROACTIVE_START(Emote.CASUAL, 1.0, true, 3, true),
        TAKEOVER_REACTIVE_START(Emote.DENY, 1.0, true, 3, true),
        TAKEOVER_PROACTIVE_AMBIENT(Emote.CASUAL, 1.0, true, 3, true),
        TAKEOVER_REACTIVE_AMBIENT(Emote.PAUSE, 1.0, true, 3, true),
        TAKEOVER_BYSTANDER(Emote.CONFUSED, 1.0, true, 3, true),
        TAKEOVER_BORROW_GRANTED_PROACTIVE(Emote.HEART, 1.0, true, 3, true),
        TAKEOVER_BORROW_GRANTED_REACTIVE(Emote.CASUAL, 1.0, true, 3, true),
        TAKEOVER_BORROW_EARLY_PROACTIVE(Emote.PAUSE, 1.0, true, 2, true),
        TAKEOVER_BORROW_EARLY_REACTIVE(Emote.DENY, 1.0, true, 2, true),
        TAKEOVER_BORROW_COOLDOWN_PROACTIVE(Emote.PAUSE, 1.0, true, 2, true),
        TAKEOVER_BORROW_COOLDOWN_REACTIVE(Emote.DENY, 1.0, true, 2, true),
        TAKEOVER_BORROW_AIRBORNE(Emote.STARTLED, 1.0, true, 2, true),
        TAKEOVER_BORROW_DANGER(Emote.DENY, 1.0, true, 2, true),
        TAKEOVER_BORROW_BUSY(Emote.PAUSE, 1.0, true, 2, true),
        TAKEOVER_CONTROL_RETURNED_PROACTIVE(Emote.CASUAL, 1.0, true, 2, true),
        TAKEOVER_CONTROL_RETURNED_REACTIVE(Emote.PAUSE, 1.0, true, 2, true),
        TAKEOVER_ESCAPE_CONFIRM_PROACTIVE(Emote.IDEA, 1.0, true, 2, true),
        TAKEOVER_ESCAPE_CONFIRM_REACTIVE(Emote.DENY, 1.0, true, 2, true),
        TAKEOVER_STRUGGLE_PROACTIVE(Emote.STARTLED, 1.0, true, 2, true),
        TAKEOVER_STRUGGLE_REACTIVE(Emote.DENY, 1.0, true, 2, true),
        TAKEOVER_SLEEP_PROACTIVE(Emote.CASUAL, 1.0, true, 2, true),
        TAKEOVER_SLEEP_REACTIVE(Emote.PAUSE, 1.0, true, 2, true),
        TAKEOVER_BED_SLEEP_PROACTIVE(Emote.HEART, 1.0, true, 2, true),
        TAKEOVER_BED_SLEEP_REACTIVE(Emote.PAUSE, 1.0, true, 2, true),
        TAKEOVER_TRANSFUR_SLEEP_FAILED_PROACTIVE(Emote.HEART, 1.0, true, 2, true),
        TAKEOVER_TRANSFUR_SLEEP_FAILED_REACTIVE(Emote.DENY, 1.0, true, 2, true),
        TAKEOVER_TRANSFUR_SLEEP_EXPIRED_PROACTIVE(Emote.CASUAL, 1.0, true, 2, true),
        TAKEOVER_TRANSFUR_SLEEP_EXPIRED_REACTIVE(Emote.PAUSE, 1.0, true, 2, true),
        SPOTTED(Emote.STARTLED, 1.25, false),
        ALERT(Emote.IDEA, 0.9, false),
        HEARD(Emote.IDEA, 0.9, false),
        FIREARM_NOTICED(Emote.CONFUSED, 0.8, false, 2),
        GUNSHOT_HOSTILE(Emote.ANGRY, 1.25, false, 2),
        GUNSHOT_FRIENDLY(Emote.STARTLED, 1.15, false, 2),
        LOST(Emote.CONFUSED, 1.1, false),
        REACQUIRED(Emote.HEART, 1.2, false),
        GIVE_UP(Emote.DENY, 0.85, false),
        SUCCESS_ASSIMILATE(Emote.HEART, 1.0, true),
        SUCCESS_ABSORB(Emote.CASUAL, 1.0, true),
        WHITE_KNIGHT_FUSION_APPROACH(Emote.IDEA, 1.0, true, 3, true),
        WHITE_KNIGHT_FUSION_COMPLETE(Emote.HEART, 1.0, true, 3, true),
        FUSION_APPROACH(Emote.IDEA, 1.0, true, 3, true),
        FUSION_COMPLETE(Emote.HEART, 1.0, true, 3, true),
        NEGOTIATION_OPEN_ASSIMILATION(Emote.CONFUSED, 1.0, true, 1, true),
        NEGOTIATION_OPEN_ABSORPTION(Emote.CONFUSED, 1.0, true, 1, true),
        NEGOTIATION_OPEN_COMPLETION(Emote.STARTLED, 1.0, true, 1, true),
        NEGOTIATION_REASON(Emote.PAUSE, 1.0, true, 1, true),
        NEGOTIATION_EMPATHY(Emote.HEART, 1.0, true, 1, true),
        NEGOTIATION_APOLOGY(Emote.CONFUSED, 1.0, true, 1, true),
        NEGOTIATION_BARGAIN(Emote.IDEA, 1.0, true, 1, true),
        NEGOTIATION_INSIST(Emote.DENY, 1.0, true, 1, true),
        NEGOTIATION_FOOD_BRIBE(Emote.IDEA, 1.0, true, 1, true),
        NEGOTIATION_RESIST(Emote.DENY, 1.0, true, 1, true),
        NEGOTIATION_FAILED(Emote.DENY, 1.0, true, 1, true),
        NEGOTIATION_RELEASE_HOLD_ASSIMILATION(Emote.CASUAL, 1.0, true, 1, true),
        NEGOTIATION_RELEASE_ASSIMILATION(Emote.CASUAL, 1.0, true, 1, true),
        NEGOTIATION_RELEASE_ABSORPTION(Emote.CASUAL, 1.0, true, 1, true),
        NEGOTIATION_RELEASE_COMPLETION(Emote.HEART, 1.0, true, 1, true),
        NEGOTIATION_RELEASE_WHITE_KNIGHT(Emote.HEART, 1.0, true, 1, true),
        NEGOTIATION_RELEASE_WHITE_KNIGHT_HUMAN(Emote.HEART, 1.0, true, 1, true),
        NEGOTIATION_RELEASE_DARK_YUFENG(Emote.HEART, 1.0, true, 1, true),
        BOND_WELCOME_REPLICATE(Emote.HEART, 1.0, true, 2),
        BOND_WELCOME_ABSORB(Emote.CASUAL, 1.0, true, 2),
        VOLUNTARY_BOND_CONFIRM(Emote.CONFUSED, 1.0, true, 2, true),
        VOLUNTARY_BOND_COMPLETE(Emote.HEART, 1.0, true, 2, true),
        REPUTATION_HOSTILE(Emote.ANGRY, 1.2, false, 2),
        REPUTATION_DISTRUSTED(Emote.DENY, 1.1, false, 2),
        REPUTATION_RECOGNIZED(Emote.CASUAL, 1.15, false, 2),
        REPUTATION_RESPECTED(Emote.HEART, 1.2, false, 2),
        REPUTATION_ALLIED(Emote.HEART, 1.25, false, 2),
        ROUTINE_REST(Emote.PAUSE, 0.30, false, 2),
        ROUTINE_ROAM(Emote.CASUAL, 0.26, false, 2),
        ROUTINE_PATROL(Emote.IDEA, 0.32, false, 2),
        ROUTINE_FORAGE(Emote.CASUAL, 0.30, false, 2),
        ROUTINE_SOCIALIZE(Emote.HEART, 0.34, false, 2),
        ROUTINE_WATCH(Emote.IDEA, 0.30, false, 2),
        ROUTINE_RETURN_CENTER(Emote.PAUSE, 0.28, false, 2),
        ROUTINE_CONSENSUS(Emote.CASUAL, 0.38, false, 2),
        ROLE_SCOUT_MARK(Emote.IDEA, 0.45, false, 2),
        ROLE_GUARD_RALLY(Emote.DENY, 0.45, false, 2),
        ROLE_FORAGER_FOUND(Emote.IDEA, 0.60, false, 2),
        ROLE_FORAGER_STORE(Emote.CASUAL, 0.60, false, 2),
        ROLE_PROVISIONER_GIFT_RESPECTED(Emote.CASUAL, 1.0, true, 2, true),
        ROLE_PROVISIONER_GIFT_ALLIED(Emote.HEART, 1.0, true, 2, true),
        ROLE_PROVISIONER_GIFT_FRIEND(Emote.HEART, 1.0, true, 2, true),
        ROLE_PROVISIONER_GIFT_CLOSE(Emote.HEART, 1.0, true, 2, true),
        ROLE_PROVISIONER_GIFT_BONDED(Emote.HEART, 1.0, true, 2, true),
        ROLE_FISHING_FOCUS_DISTRUSTED(Emote.DENY, 1.0, true, 2, true),
        ROLE_FISHING_FOCUS_NEUTRAL(Emote.PAUSE, 1.0, true, 2, true),
        ROLE_FISHING_FOCUS_RECOGNIZED(Emote.CASUAL, 1.0, true, 2, true),
        ROLE_FISHING_FOCUS_RESPECTED(Emote.CASUAL, 1.0, true, 2, true),
        ROLE_FISHING_FOCUS_ALLIED(Emote.HEART, 1.0, true, 2, true),
        ROLE_FISHING_FOCUS_FRIEND(Emote.HEART, 1.0, true, 2, true),
        ROLE_FISHING_FOCUS_CLOSE(Emote.HEART, 1.0, true, 2, true),
        ROLE_CARETAKER_AID(Emote.HEART, 0.55, false, 2),
        ROLE_COURIER_CARRY(Emote.CASUAL, 0.42, false, 2),
        ROLE_LOOKOUT_WARNING(Emote.STARTLED, 0.45, false, 2),
        ROLE_COORDINATOR_RALLY(Emote.IDEA, 0.45, false, 2),
        ROLE_WANDERER_DISCOVERY(Emote.CONFUSED, 0.40, false, 2),
        ROLE_YOUNGSTER_RETREAT(Emote.NERVOUS, 0.55, false, 2),
        COMFORT_BOX_DISCOVERED(Emote.STARTLED, 1.0, true, 2, true),
        CACHE_INTRUSION(Emote.DENY, 1.0, true, 2, true),
        CACHE_TOLERATED(Emote.CASUAL, 1.0, true, 2, true),
        CACHE_FINAL_WARNING(Emote.DENY, 1.0, true, 2, true),
        CACHE_OVERUSED(Emote.ANGRY, 1.0, true, 2, true),
        CACHE_ALLIED_ACCESS(Emote.HEART, 1.0, true, 2, true),
        CACHE_DESTROYED(Emote.ANGRY, 1.0, true, 2, true),
        CACHE_DESTROYED_TRUSTED(Emote.DENY, 1.0, true, 2, true),
        BOND_FISHING_START(Emote.IDEA, 1.0, true, 2, true),
        BOND_FISHING_SUCCESS(Emote.HEART, 1.0, true, 2, true),
        BOND_MINING_START(Emote.IDEA, 1.0, true, 2, true),
        BOND_MINING_SUCCESS(Emote.HEART, 1.0, true, 2, true),
        COMMUNITY_MIGRATION(Emote.IDEA, 0.75, false, 2),
        WHITE_REFORMATION(Emote.CASUAL, 0.55, false, 2),
        MEET_ALLY(Emote.HEART, 1.2, false),
        MEET_SPECIES(Emote.HEART, 1.2, false, 2),
        MEET_CATEGORY(Emote.CASUAL, 1.2, false, 2),
        MEET_FRIENDLY(Emote.IDEA, 1.15, false, 2),
        FORMER_BOND_WELCOME(Emote.HEART, 1.0, true, 2),
        FORMER_RESPECT_WELCOME(Emote.CASUAL, 1.0, true, 2),
        FRIEND_RESPECT_WELCOME(Emote.CASUAL, 1.0, true, 2),
        MEET_RIVAL(Emote.ANGRY, 1.15, false),
        MEET_OUTSIDER(Emote.IDEA, 1.1, false),
        ALLY_FALLEN(Emote.NERVOUS, 1.0, false),
        WHITE_TERRITORY_ASSIST(Emote.ANGRY, 1.0, true, 2, true),
        WHITE_HIVE_HURT(Emote.NERVOUS, 1.0, true, 2, true),
        COMPATRIOT_DEFENSE(Emote.ANGRY, 1.0, true, 2, true),
        BOND_WITNESS_COMPATRIOT_ATTACK(Emote.DENY, 1.0, true, 2, true),
        FRIEND_WITNESS_COMPATRIOT_ATTACK(Emote.ANGRY, 1.0, true, 2, true),
        FRIEND_COMBAT_ASSIST(Emote.ANGRY, 1.0, true, 2, true),
        FACTION_COMBAT_ASSIST(Emote.ANGRY, 1.0, true, 2, true),
        FRIEND_KIN_KILL_WARNING(Emote.DENY, 1.0, true, 2, true),
        FRIEND_KIN_KILL_BETRAYAL(Emote.ANGRY, 1.0, true, 2, true),
        FRIEND_BETRAYAL_PAT_REFUSED(Emote.DENY, 1.0, true, 2, true),
        PAT_BONDED(Emote.HEART, 1.0, true, 2),
        PAT_KIN(Emote.HEART, 1.0, true, 2),
        PAT_CATEGORY(Emote.CASUAL, 1.0, true, 2),
        PAT_FRIEND(Emote.CASUAL, 1.0, true, 2),
        PAT_FORMER_BONDED(Emote.HEART, 1.0, true, 2),
        PAT_FORMER_RESPECT(Emote.CASUAL, 1.0, true, 2),
        PAT_FRIEND_RESPECT(Emote.CASUAL, 1.0, true, 2),
        PAT_OUTSIDER(Emote.CONFUSED, 1.0, true, 2),
        PAT_HUMAN(Emote.CONFUSED, 1.0, true, 2),
        PAT_RIVAL(Emote.DENY, 1.0, true, 2),
        PAT_PLAYER_BONDED(Emote.HEART, 1.0, true, 2),
        PAT_PLAYER_KIN(Emote.HEART, 1.0, true, 2),
        PAT_PLAYER_CATEGORY(Emote.CASUAL, 1.0, true, 2),
        PAT_PLAYER_FRIEND(Emote.HEART, 1.0, true, 2),
        PAT_PLAYER_OTHER(Emote.CASUAL, 1.0, true, 2),
        PAT_PLAYER_HUMAN_FRIEND(Emote.CASUAL, 1.0, true, 2),
        PAT_PLAYER_FORMER_BONDED(Emote.HEART, 1.0, true, 2),
        PAT_PLAYER_FORMER_RESPECT(Emote.CASUAL, 1.0, true, 2),
        PAT_PLAYER_FRIEND_RESPECT(Emote.CASUAL, 1.0, true, 2),
        PAT_SPEED_VERY_SLOW(Emote.CONFUSED, 0.22, false, 2, true),
        PAT_SPEED_SLOW(Emote.CASUAL, 0.22, false, 2, true),
        PAT_SPEED_GENTLE(Emote.HEART, 0.22, false, 2, true),
        PAT_SPEED_FAST(Emote.IDEA, 0.22, false, 2, true),
        PAT_SPEED_VERY_FAST(Emote.STARTLED, 0.22, false, 2, true),
        HIT_CONFUSED(Emote.CONFUSED, 1.0, true, 2, true),
        HIT_WARNING(Emote.DENY, 1.0, true, 2, true),
        HOSTILITY_CONFIRMED(Emote.ANGRY, 1.0, true, 2, true),
        SECONDARY_TRANSFUR(Emote.HEART, 1.0, true, 2),
        ORGANIC_GRAPPLE_BITE(Emote.CASUAL, 1.0, true, 2, true),
        ORGANIC_GRAPPLE_CLAW(Emote.IDEA, 1.0, true, 2, true),
        ORGANIC_GRAPPLE_PIN(Emote.HEART, 1.0, true, 2, true),
        ORGANIC_SECONDARY_TRANSFUR(Emote.HEART, 1.0, true, 2, true),
        ORGANIC_BOND_CONFLICT_ASSIMILATION(Emote.CASUAL, 1.0, true, 2, true),
        BOND_WRAP_REVERTED(Emote.HEART, 1.0, true, 2, true),
        BOND_WRAP_MANUAL_READY(Emote.IDEA, 1.0, true, 2, true),
        BOND_WRAP_MANUAL_CURIOUS(Emote.CASUAL, 1.0, true, 2, true),
        BOND_WRAP_TRANSFURRED(Emote.CASUAL, 1.0, true, 2, true),
        BOND_WRAP_SLEEP(Emote.HEART, 1.0, true, 2, true),
        BOND_WRAP_NEW_FORM(Emote.CONFUSED, 1.0, true, 2, true),
        BOND_REASSIMILATE(Emote.HEART, 1.0, true, 2, true),
        BOND_RELEASE_REVERTED(Emote.CASUAL, 1.0, true, 2, true),
        BOND_RELEASE_TRANSFURRED(Emote.CASUAL, 1.0, true, 2, true),
        BOND_EMERGENCY_WRAP(Emote.NERVOUS, 1.0, true, 2, true),
        BOND_COMBAT_WRAP(Emote.NERVOUS, 1.0, true, 2, true),
        BOND_COMBAT_GRAB_ASSIST(Emote.IDEA, 1.0, true, 2, true),
        BOND_EMERGENCY_RECOVERED(Emote.HEART, 1.0, true, 2, true),
        BOND_DROWNING_RESCUE(Emote.STARTLED, 1.0, true, 2, true),
        BOND_DROWNING_RECOVERED(Emote.HEART, 1.0, true, 2, true),
        BOND_TRANSFUR_RESCUE(Emote.STARTLED, 1.0, true, 2, true),
        BOND_TRANSFUR_RECOVERED(Emote.HEART, 1.0, true, 2, true),
        BOND_SAFETY_HOLD_READY(Emote.DENY, 1.0, true, 2, true),
        BOND_SAFETY_RELEASE_REFUSE_FIRST(Emote.DENY, 1.0, true, 2, true),
        BOND_SAFETY_RELEASE_REFUSE_REPEAT(Emote.NERVOUS, 1.0, true, 2, true),
        BOND_SAFETY_RELEASE_ACCEPT(Emote.HEART, 1.0, true, 2, true),
        BOND_SAFETY_RELEASE_NOT_READY(Emote.NERVOUS, 1.0, true, 2, true),
        // Each faction voice currently defines one exact reversal line.  A
        // line count of two made index 1 render as a raw translation key.
        BOND_REVERSE_HOLD(Emote.CASUAL, 1.0, true, 1, true),
        BOND_REVERSE_COMPLETE(Emote.HEART, 1.0, true, 1, true),
        BOND_RIVAL_ABSORPTION(Emote.IDEA, 1.0, true, 2, true),
        BOND_JEALOUS_NEW_FORM(Emote.DENY, 1.0, true, 2, true),
        BOND_NEW_FORM_WELCOME(Emote.CONFUSED, 1.1, false, 2),
        BOND_DEATH_DIRECT(Emote.NERVOUS, 1.0, true, 2, true),
        BOND_DEATH_WRAPPING(Emote.NERVOUS, 1.0, true, 2, true),
        BOND_DEATH_TOGETHER(Emote.HEART, 1.0, true, 2, true),
        BOND_MANUAL_RELEASE(Emote.CASUAL, 1.0, true, 2, true),
        BOND_MANUAL_RELEASE_WRAPPING(Emote.HEART, 1.0, true, 2, true),
        PAT_BONDED_NEW_FORM(Emote.CASUAL, 1.0, true, 2),
        PAT_PLAYER_BONDED_NEW_FORM(Emote.CASUAL, 1.0, true, 2),
        BETRAYED(Emote.DENY, 1.0, true, 2, true),
        PAT_TRUCE_REFUSED(Emote.DENY, 1.0, true, 2, true),
        POLITE_NOTICE(Emote.IDEA, 1.0, false, 2),
        POLITE_PROBE(Emote.CONFUSED, 1.0, true, 2),
        POLITE_RESPONSE(Emote.CASUAL, 1.0, true, 2, true),
        POLITE_NO_RESPONSE(Emote.PAUSE, 1.0, true, 2),
        RELATIONSHIP_PROGRESS(Emote.HEART, 1.0, true, 2, true),
        RELATIONSHIP_GIFT(Emote.HEART, 1.0, true, 2, true),
        RELATIONSHIP_DIET_GIFT(Emote.HEART, 1.0, true, 2, true),
        LOW_REPUTATION_PAT(Emote.CONFUSED, 1.0, true, 2, true),
        LOW_REPUTATION_FOOD(Emote.CASUAL, 1.0, true, 2, true),
        LOW_REPUTATION_DIET_FOOD(Emote.HEART, 1.0, true, 2, true),
        RELATIONSHIP_ESTABLISHED(Emote.HEART, 1.0, true, 3, true),
        RELATIONSHIP_DIET_ESTABLISHED(Emote.HEART, 1.0, true, 2, true),
        SOCIAL_TALK(Emote.CASUAL, 1.0, true, 3, true),
        SOCIAL_TALK_NEW(Emote.CONFUSED, 1.0, true, 2, true),
        SOCIAL_TALK_FAMILIAR(Emote.CASUAL, 1.0, true, 2, true),
        SOCIAL_PAT(Emote.HEART, 1.0, true, 2, true),
        SOCIAL_PAT_NEW(Emote.CONFUSED, 1.0, true, 2, true),
        SOCIAL_PAT_FAMILIAR(Emote.CASUAL, 1.0, true, 2, true),
        SOCIAL_PAT_CLOSE(Emote.HEART, 1.0, true, 2, true),
        SOCIAL_GIFT(Emote.HEART, 1.0, true, 2, true),
        SOCIAL_DIET_GIFT(Emote.HEART, 1.0, true, 2, true),
        CAT_ORANGE_REFUSED(Emote.DENY, 1.0, true, 2, true),
        SOCIAL_GIFT_REFUSED(Emote.DENY, 1.0, true, 2, true),
        SOCIAL_GIFT_UNSUITABLE(Emote.CONFUSED, 1.0, true, 2, true),
        SOCIAL_PLAY(Emote.IDEA, 1.0, true, 3, true),
        SOCIAL_PLAY_HUG_BONDED(Emote.HEART, 1.0, true, 2, true),
        SOCIAL_PLAY_HUG_FRIEND(Emote.HEART, 1.0, true, 2, true),
        SOCIAL_PLAY_HUG_RELEASE_BONDED(Emote.CASUAL, 1.0, true, 2, true),
        SOCIAL_PLAY_HUG_RELEASE_FRIEND(Emote.CASUAL, 1.0, true, 2, true),
        SOCIAL_REST(Emote.HEART, 1.0, true, 2, true),
        SOCIAL_FOLLOW(Emote.CASUAL, 1.0, true, 2, true),
        SOCIAL_WAIT(Emote.PAUSE, 1.0, true, 2, true),
        SOCIAL_GOODBYE(Emote.CASUAL, 1.0, true, 2, true),
        SOCIAL_TALK_HURT(Emote.NERVOUS, 1.0, true, 2, true),
        SOCIAL_TALK_PLAYER_HURT(Emote.NERVOUS, 1.0, true, 2, true),
        SOCIAL_TALK_RAIN(Emote.CASUAL, 1.0, true, 2, true),
        SOCIAL_TALK_NIGHT(Emote.CASUAL, 1.0, true, 2, true),
        SOCIAL_TALK_CLOSE(Emote.HEART, 1.0, true, 2, true),
        CENTAUR_TACK_OPEN(Emote.IDEA, 0.65, false, 2),
        CENTAUR_SADDLE_EQUIP(Emote.CASUAL, 1.0, true, 2, true),
        CENTAUR_PACK_EQUIP(Emote.IDEA, 1.0, true, 2, true),
        CENTAUR_PACK_OPEN(Emote.CASUAL, 0.7, false, 2),
        CENTAUR_RIDE_START(Emote.CASUAL, 1.0, true, 2, true),
        CENTAUR_RIDE_END(Emote.HEART, 1.0, true, 2, true),
        ORGANIC_EVACUATION_START_NEW(Emote.STARTLED, 1.0, true, 2, true),
        ORGANIC_EVACUATION_START_FAMILIAR(Emote.NERVOUS, 1.0, true, 2, true),
        ORGANIC_EVACUATION_START_CLOSE(Emote.NERVOUS, 1.0, true, 2, true),
        ORGANIC_EVACUATION_RELEASE_NEW(Emote.CASUAL, 1.0, true, 2, true),
        ORGANIC_EVACUATION_RELEASE_FAMILIAR(Emote.CASUAL, 1.0, true, 2, true),
        ORGANIC_EVACUATION_RELEASE_CLOSE(Emote.HEART, 1.0, true, 2, true),
        ATTACKED_BY_RIVAL(Emote.ANGRY, 1.0, true, 2),
        HYPNOSIS_START(Emote.IDEA, 1.0, true, 2, true),
        HYPNOSIS_ESCAPE(Emote.STARTLED, 1.0, true, 2, true),
        HYPNOSIS_FAILED(Emote.CASUAL, 1.0, true, 2, true),
        HYPNOSIS_INTERRUPTED(Emote.DENY, 1.0, true, 2, true),
        HYPNOSIS_PAT_SURPRISED(Emote.CONFUSED, 1.0, true, 2, true),
        HYPNOSIS_PAT_PLEASED(Emote.HEART, 1.0, true, 2, true),
        HYPNOSIS_WELCOME(Emote.HEART, 1.0, true, 2, true),
        HYPNOSIS_PLAY_START(Emote.IDEA, 1.0, true, 2, true),
        HYPNOSIS_PLAY_SUCCESS(Emote.STARTLED, 1.0, true, 2, true),
        HYPNOSIS_PLAY_FAILED(Emote.HEART, 1.0, true, 2, true),
        DISGUISE_DARK_ACCEPTED(Emote.CONFUSED, 1.0, true, 3, true),
        DISGUISE_DARK_INSPECTED(Emote.CASUAL, 1.0, true, 3, true),
        DISGUISE_DARK_REVEALED(Emote.DENY, 1.0, true, 3, true),
        DISGUISE_DARK_BETRAYAL(Emote.ANGRY, 1.0, true, 3, true),
        DISGUISE_DARK_DROPPED(Emote.STARTLED, 1.0, true, 3, true),
        DISGUISE_WHITE_RIVAL(Emote.DENY, 1.0, true, 3, true),
        PAT_DISGUISED_DARK(Emote.CONFUSED, 1.0, true, 2, true),
        PAT_DISGUISED_DARK_INSPECTED(Emote.CASUAL, 1.0, true, 2, true),
        PAT_DISGUISED_DARK_CAUGHT(Emote.DENY, 1.0, true, 2, true),
        PAT_DISGUISED_WHITE_RIVAL(Emote.DENY, 1.0, true, 2, true),
        PAT_PLAYER_DISGUISED_DARK(Emote.CASUAL, 1.0, true, 2, true);

        private final Emote emote;
        private final double chanceMultiplier;
        private final boolean important;
        private final int lineCount;
        private final boolean bypassPriorityCooldown;

        Cue(Emote emote, double chanceMultiplier, boolean important) {
            this(emote, chanceMultiplier, important, LINES_PER_CUE, false);
        }

        Cue(Emote emote, double chanceMultiplier, boolean important, int lineCount) {
            this(emote, chanceMultiplier, important, lineCount, false);
        }

        Cue(
                Emote emote,
                double chanceMultiplier,
                boolean important,
                int lineCount,
                boolean bypassPriorityCooldown) {
            this.emote = emote;
            this.chanceMultiplier = chanceMultiplier;
            this.important = important;
            this.lineCount = lineCount;
            this.bypassPriorityCooldown = bypassPriorityCooldown;
        }
    }
}
