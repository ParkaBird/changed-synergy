package net.parkabird.changedsynergy.world.inventory;

import javax.annotation.Nullable;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.world.inventory.UpdateableMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.LogicalSide;
import net.parkabird.changedsynergy.ai.CreaturePersonality;
import net.parkabird.changedsynergy.ai.CreaturePersonality.RelationshipTier;
import net.parkabird.changedsynergy.ai.CreaturePersonality.MemorySummary;
import net.parkabird.changedsynergy.ai.CreaturePersonality.Trait;
import net.parkabird.changedsynergy.ai.CreatureLifeMemory;
import net.parkabird.changedsynergy.ai.CreatureLifeMemory.GroupRole;
import net.parkabird.changedsynergy.ai.CreatureLifeMemory.RoutineState;
import net.parkabird.changedsynergy.ai.CreatureCommunityData;
import net.parkabird.changedsynergy.ai.CreatureSettlementService;
import net.parkabird.changedsynergy.ai.FactionReputation;
import net.parkabird.changedsynergy.ai.FactionReputation.Standing;
import net.parkabird.changedsynergy.ai.HunterFaction;
import net.parkabird.changedsynergy.ai.CreatureSocialProfile;
import net.parkabird.changedsynergy.ai.HumanIntent;
import net.parkabird.changedsynergy.ai.HypnosisProfile;
import net.parkabird.changedsynergy.ai.HypnosisQteService;
import net.parkabird.changedsynergy.ai.InvoluntaryTransfurNegotiation;
import net.parkabird.changedsynergy.ai.InvoluntaryTransfurNegotiation.Approach;
import net.parkabird.changedsynergy.ai.InvoluntaryTransfurNegotiation.Mode;
import net.parkabird.changedsynergy.ai.InvoluntaryTransfurNegotiation.Reason;
import net.parkabird.changedsynergy.ai.InvoluntaryTransfurNegotiation.View;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.ai.SynergyPatService;
import net.parkabird.changedsynergy.ai.RelationshipFavorService;
import net.parkabird.changedsynergy.ai.RelationshipFavorService.Result;
import net.parkabird.changedsynergy.ai.SocialAudienceGoal;
import net.parkabird.changedsynergy.ai.VoluntaryBondTransfurService;
import net.parkabird.changedsynergy.advancement.SynergyAdvancements;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue;
import net.parkabird.changedsynergy.event.LatexSocialEvents;
import net.parkabird.changedsynergy.init.ChangedSynergyMenus;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

/** Changed-style radial menu for an established non-bonded relationship. */
public final class SocialInteractionMenu extends AbstractContainerMenu
        implements UpdateableMenu {
    private static final String NEXT_SHARED_REST = "ChangedSynergyNextSharedRest";
    private final Player player;
    @Nullable
    private final ChangedEntity creature;
    private boolean following;
    private final int familiarity;
    private final RelationshipTier tier;
    private final Trait dominantTrait;
    private final HumanIntent humanIntent;
    private final boolean organic;
    private final boolean bonded;
    private final boolean negotiation;
    private final boolean virtualAbsorptionNegotiation;
    private final boolean negotiationAvailable;
    private final boolean trustedRelationship;
    private final boolean voluntaryBondAvailable;
    private final boolean foodBribeAvailable;
    private final boolean felineFoodBribe;
    @Nullable
    private View negotiationView;
    private final PanelData panelData;
    private final Component negotiationSpeakerName;
    private final CompoundTag negotiationAppearance;
    private boolean audienceReleased;

    public SocialInteractionMenu(
            int id,
            Inventory inventory,
            ChangedEntity creature) {
        this(id, inventory, creature, false, false);
    }

    public SocialInteractionMenu(
            int id,
            Inventory inventory,
            ChangedEntity creature,
            boolean bonded) {
        this(id, inventory, creature, bonded, false);
    }

    public SocialInteractionMenu(
            int id,
            Inventory inventory,
            ChangedEntity creature,
            boolean bonded,
            boolean negotiation) {
        super(ChangedSynergyMenus.SOCIAL_INTERACTION.get(), id);
        this.player = inventory.player;
        this.creature = creature;
        ServerPlayer serverPlayer = inventory.player instanceof ServerPlayer server
                ? server : null;
        this.following = serverPlayer != null
                && (bonded
                        ? LatexSocialMemory.isFollowingOwner(creature)
                        : CreaturePersonality.isSocialFollowing(
                                creature, serverPlayer));
        this.familiarity = serverPlayer == null
                ? 0 : CreaturePersonality.familiarity(creature, serverPlayer);
        this.tier = serverPlayer == null
                ? RelationshipTier.STRANGER
                : CreaturePersonality.relationshipTier(creature, serverPlayer);
        this.dominantTrait = CreaturePersonality.dominantTrait(creature);
        this.humanIntent = HumanIntent.of(creature);
        this.organic = LatexSocialMemory.isOrganic(creature);
        this.bonded = bonded;
        this.negotiation = negotiation;
        this.virtualAbsorptionNegotiation = false;
        this.negotiationView = serverPlayer != null && negotiation
                ? InvoluntaryTransfurNegotiation.view(serverPlayer).orElse(null)
                : null;
        this.negotiationAvailable = serverPlayer != null
                && !negotiation
                && InvoluntaryTransfurNegotiation.canNegotiate(
                        serverPlayer, creature);
        this.trustedRelationship = serverPlayer != null
                && CreaturePersonality.hasTrustedRelationship(
                        creature, serverPlayer);
        this.voluntaryBondAvailable = serverPlayer != null
                && !bonded
                && !negotiation
                && VoluntaryBondTransfurService.canOffer(
                        creature, serverPlayer);
        this.foodBribeAvailable = serverPlayer != null
                && negotiation
                && InvoluntaryTransfurNegotiation.canOfferFoodBribe(
                        serverPlayer, creature);
        this.felineFoodBribe = serverPlayer != null
                && negotiation
                && InvoluntaryTransfurNegotiation.usesFelineFoodBribe(
                        serverPlayer, creature);
        this.panelData = PanelData.of(creature, serverPlayer);
        this.negotiationSpeakerName = creature.getDisplayName();
        this.negotiationAppearance = new CompoundTag();
    }

    /** Server-side menu for the voice/body currently surrounding the player. */
    public SocialInteractionMenu(
            int id,
            Inventory inventory,
            boolean virtualAbsorptionNegotiation) {
        super(ChangedSynergyMenus.SOCIAL_INTERACTION.get(), id);
        this.player = inventory.player;
        this.creature = null;
        ServerPlayer serverPlayer = inventory.player instanceof ServerPlayer server
                ? server : null;
        this.following = false;
        this.familiarity = 0;
        this.tier = RelationshipTier.STRANGER;
        this.dominantTrait = serverPlayer == null
                ? Trait.CURIOUS
                : InvoluntaryTransfurNegotiation
                        .absorptionSourceTrait(serverPlayer);
        this.humanIntent = HumanIntent.GREET;
        this.organic = false;
        this.bonded = false;
        this.negotiation = true;
        this.virtualAbsorptionNegotiation = virtualAbsorptionNegotiation;
        this.negotiationView = serverPlayer == null
                ? null
                : InvoluntaryTransfurNegotiation.view(serverPlayer)
                        .orElse(null);
        this.negotiationAvailable = false;
        this.trustedRelationship = false;
        this.voluntaryBondAvailable = false;
        this.foodBribeAvailable = serverPlayer != null
                && InvoluntaryTransfurNegotiation.canOfferFoodBribe(
                        serverPlayer, null);
        this.felineFoodBribe = serverPlayer != null
                && InvoluntaryTransfurNegotiation.usesFelineFoodBribe(
                        serverPlayer, null);
        this.panelData = PanelData.empty();
        this.negotiationSpeakerName = serverPlayer == null
                ? Component.translatable(
                        "menu.changed_synergy.negotiation.surrounding_creature")
                : InvoluntaryTransfurNegotiation
                        .absorptionSourceName(serverPlayer);
        this.negotiationAppearance = serverPlayer == null
                ? new CompoundTag()
                : InvoluntaryTransfurNegotiation
                        .absorptionSourceAppearance(serverPlayer);
    }

    public SocialInteractionMenu(
            int id,
            Inventory inventory,
            FriendlyByteBuf extraData) {
        super(ChangedSynergyMenus.SOCIAL_INTERACTION.get(), id);
        this.player = inventory.player;
        Entity entity = inventory.player.level().getEntity(extraData.readVarInt());
        this.creature = entity instanceof ChangedEntity changed ? changed : null;
        this.following = extraData.readBoolean();
        this.familiarity = extraData.readVarInt();
        this.tier = enumByOrdinal(
                RelationshipTier.values(), extraData.readVarInt(), RelationshipTier.STRANGER);
        this.dominantTrait = enumByOrdinal(
                Trait.values(), extraData.readVarInt(), Trait.CURIOUS);
        this.humanIntent = enumByOrdinal(
                HumanIntent.values(), extraData.readVarInt(), HumanIntent.GREET);
        this.organic = extraData.readBoolean();
        this.bonded = extraData.readBoolean();
        this.negotiation = extraData.readBoolean();
        this.virtualAbsorptionNegotiation = extraData.readBoolean();
        this.negotiationView = negotiation
                ? readNegotiationView(extraData) : null;
        this.negotiationAvailable = extraData.readBoolean();
        this.trustedRelationship = extraData.readBoolean();
        this.voluntaryBondAvailable = extraData.readBoolean();
        this.foodBribeAvailable = extraData.readBoolean();
        this.felineFoodBribe = extraData.readBoolean();
        this.panelData = PanelData.read(extraData);
        if (virtualAbsorptionNegotiation) {
            this.negotiationSpeakerName = extraData.readComponent();
            CompoundTag appearance = extraData.readNbt();
            this.negotiationAppearance = appearance == null
                    ? new CompoundTag() : appearance;
        } else {
            this.negotiationSpeakerName = creature == null
                    ? Component.translatable(
                            "menu.changed_synergy.negotiation.surrounding_creature")
                    : creature.getDisplayName();
            this.negotiationAppearance = new CompoundTag();
        }
    }

    @Nullable
    public ChangedEntity getCreature() {
        return creature;
    }

    public boolean isFollowing() {
        return following;
    }

    public int getFamiliarity() {
        return familiarity;
    }

    public RelationshipTier getTier() {
        return tier;
    }

    public Trait getDominantTrait() {
        return dominantTrait;
    }

    public HumanIntent getHumanIntent() {
        return humanIntent;
    }

    public boolean isOrganic() {
        return organic;
    }

    public boolean isBondedMode() {
        return bonded;
    }

    public boolean isNegotiationMode() {
        return negotiation;
    }

    public boolean isVirtualAbsorptionNegotiation() {
        return virtualAbsorptionNegotiation;
    }

    public Component getNegotiationSpeakerName() {
        return negotiationSpeakerName;
    }

    public CompoundTag getNegotiationAppearance() {
        return negotiationAppearance;
    }

    public boolean isVoluntaryBondAvailable() {
        return voluntaryBondAvailable;
    }

    public boolean isFoodBribeAvailable() {
        return foodBribeAvailable;
    }

    public boolean isFelineFoodBribe() {
        return felineFoodBribe;
    }

    public boolean isNegotiationAvailable() {
        return negotiationAvailable;
    }

    public boolean hasTrustedRelationship() {
        return trustedRelationship;
    }

    public Mode getNegotiationMode() {
        return negotiationView != null
                ? negotiationView.mode() : Mode.ASSIMILATION;
    }

    public Reason getNegotiationReason() {
        return negotiationView != null
                ? negotiationView.reason() : Reason.COMPANION_SEEKING;
    }

    public int getNegotiationProgress() {
        return negotiationView != null ? negotiationView.progress() : 0;
    }

    public int getNegotiationRequired() {
        return negotiationView != null ? negotiationView.required() : 1;
    }

    public int getNegotiationPercent() {
        return negotiationView != null ? negotiationView.percent() : 0;
    }

    public int getNegotiationAttempts() {
        return negotiationView != null ? negotiationView.attempts() : 0;
    }

    public int getNegotiationRemainingApproaches() {
        return negotiationView != null
                ? negotiationView.remainingApproaches() : 0;
    }

    public boolean isNegotiationApproachUsed(Approach approach) {
        return negotiationView != null && negotiationView.used(approach);
    }

    public Approach getSuggestedNegotiationApproach() {
        return negotiationView == null
                ? Approach.REASON
                : negotiationView.suggestedApproach()
                        .orElse(Approach.REASON);
    }

    public String getNegotiationDifficultyKey() {
        return negotiationView != null
                ? negotiationView.difficultyKey()
                : "menu.changed_synergy.negotiation.difficulty.normal";
    }

    public boolean isSpecialCompletionNegotiation() {
        return negotiationView != null && negotiationView.specialCompletion();
    }

    public boolean isWhiteKnightSplitNegotiation() {
        return negotiationView != null && negotiationView.whiteKnightSplit();
    }

    public HunterFaction getFaction() {
        return panelData.faction();
    }

    public String getReputationGroupTranslationKey() {
        return panelData.reputationGroupTranslationKey();
    }

    public Standing getFactionStanding() {
        return panelData.standing();
    }

    public int getReputation() {
        return panelData.reputation();
    }

    public GroupRole getRole() {
        return panelData.role();
    }

    public RoutineState getRoutine() {
        return panelData.routine();
    }

    public boolean isLifeEnabled() {
        return panelData.lifeEnabled();
    }

    public int getEncounters() {
        return panelData.encounters();
    }

    public int getPats() {
        return panelData.pats();
    }

    public int getPlays() {
        return panelData.plays();
    }

    public boolean hasCommunity() {
        return panelData.communityStorage() != CommunityStorage.NONE;
    }

    public int getStoredItems() {
        return panelData.storedItems();
    }

    public int getFoodDelivered() {
        return panelData.foodDelivered();
    }

    public int getMaterialsDelivered() {
        return panelData.materialsDelivered();
    }

    public int getRoleStat(int index) {
        return switch (index) {
            case 0 -> panelData.roleStat0();
            case 1 -> panelData.roleStat1();
            case 2 -> panelData.roleStat2();
            default -> 0;
        };
    }

    public CommunityStorage getCommunityStorage() {
        return panelData.communityStorage();
    }

    public boolean canFriendFollow() {
        return bonded || tier == RelationshipTier.FAMILIAR
                || tier == RelationshipTier.CLOSE;
    }

    public void toggleLocalFollowing() {
        following = !following;
    }

    @Override
    public ItemStack quickMoveStack(Player viewer, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player viewer) {
        if (virtualAbsorptionNegotiation) {
            return !(viewer instanceof ServerPlayer serverPlayer)
                    || InvoluntaryTransfurNegotiation
                            .canNegotiateAbsorption(serverPlayer);
        }
        if (creature == null
                || !creature.isAlive()
                || viewer.distanceToSqr(creature) > 64.0D) {
            return false;
        }
        return !(viewer instanceof ServerPlayer serverPlayer)
                || (negotiation
                        ? InvoluntaryTransfurNegotiation.canNegotiate(
                                serverPlayer, creature)
                        : validRelationship(creature, serverPlayer, bonded));
    }

    @Override
    public int getId() {
        return containerId;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void update(
            CompoundTag payload,
            LogicalSide receiver,
            @Nullable ServerPlayer origin) {
        String command = payload.getString("command");
        if (receiver == LogicalSide.CLIENT) {
            if ("negotiation_state".equals(command)) {
                negotiationView = readNegotiationView(payload);
            }
            return;
        }
        if (origin == null
                || (!virtualAbsorptionNegotiation && creature == null)
                || !(virtualAbsorptionNegotiation
                        ? InvoluntaryTransfurNegotiation
                                .canNegotiateAbsorption(origin)
                        : negotiation
                        ? InvoluntaryTransfurNegotiation.canNegotiate(
                                origin, creature)
                        : validRelationship(creature, origin, bonded))) {
            return;
        }
        if (virtualAbsorptionNegotiation) {
            Approach.fromCommand(command).ifPresent(approach ->
                    InvoluntaryTransfurNegotiation.attemptAbsorption(
                            origin, approach));
            return;
        }
        if (!negotiation && !bonded
                && "social_voluntary_transfur".equals(command)) {
            VoluntaryBondTransfurService.select(creature, origin);
            return;
        }
        if (!negotiation && "social_negotiation".equals(command)) {
            if (InvoluntaryTransfurNegotiation.canNegotiate(
                    origin, creature)) {
                releaseAudience(origin);
                InvoluntaryTransfurNegotiation.claimOpeningLine(
                        origin, creature);
                LatexSocialEvents.openNegotiationMenu(origin, creature);
            }
            return;
        }
        if (negotiation) {
            Approach.fromCommand(command)
                    .ifPresent(approach -> InvoluntaryTransfurNegotiation.attempt(
                            origin, creature, approach));
            if (InvoluntaryTransfurNegotiation.canNegotiate(origin, creature)) {
                InvoluntaryTransfurNegotiation.view(origin).ifPresent(view -> {
                    CompoundTag sync = new CompoundTag();
                    sync.putString("command", "negotiation_state");
                    writeNegotiationView(sync, view);
                    setDirty(sync);
                });
            }
            return;
        }
        releaseAudience(origin);
        if (bonded) {
            SynergyAdvancements.grant(
                    origin, SynergyAdvancements.FIRST_CONTACT);
            SynergyAdvancements.grant(
                    origin, SynergyAdvancements.BONDED_COMPANION);
        }

        switch (command) {
            case "social_talk" -> {
                CreaturePersonality.rememberEncounter(creature, origin);
                creature.getLookControl().setLookAt(origin, 30.0F, 30.0F);
                NpcDialogue.trigger(creature, origin, conversationCue(origin));
            }
            case "social_pat" -> {
                creature.getLookControl().setLookAt(origin, 30.0F, 30.0F);
                SynergyPatService.perform(origin, creature, true);
            }
            case "social_gift" -> {
                Result result = RelationshipFavorService
                        .offerHeldRelationshipGift(creature, origin);
                if (result == Result.NO_ITEM) {
                    origin.sendSystemMessage(Component.translatable(
                            "message.changed_synergy.social.no_gift"));
                    return;
                }
                if (result == Result.UNSUITABLE) {
                    NpcDialogue.trigger(
                            creature, origin, Cue.SOCIAL_GIFT_UNSUITABLE);
                    return;
                }
                if (result == Result.CAT_ORANGE_REFUSED) {
                    NpcDialogue.trigger(
                            creature, origin, Cue.CAT_ORANGE_REFUSED);
                    return;
                }
                if (result == Result.REJECTED) {
                    NpcDialogue.trigger(
                            creature, origin, Cue.SOCIAL_GIFT_REFUSED);
                    return;
                }
                if (!result.isAccepted()) {
                    return;
                }
                LatexSocialEvents.calmTowards(creature, origin);
                creature.getNavigation().stop();
                creature.getLookControl().setLookAt(origin, 30.0F, 30.0F);
                origin.swing(InteractionHand.MAIN_HAND, true);
                NpcDialogue.trigger(
                        creature,
                        origin,
                        result == Result.DIET_EXISTING
                                ? Cue.SOCIAL_DIET_GIFT
                                : Cue.SOCIAL_GIFT);
            }
            case "social_play" -> playTogether(origin);
            case "social_follow" -> {
                if (bonded) {
                    return;
                }
                if (!CreaturePersonality.canFriendFollow(creature, origin)) {
                    origin.sendSystemMessage(Component.translatable(
                            "message.changed_synergy.social.follow_requires_familiar"));
                    return;
                }
                boolean newState =
                        !CreaturePersonality.isSocialFollowing(creature, origin);
                CreaturePersonality.setSocialFollowing(creature, origin, newState);
                creature.getNavigation().stop();
                NpcDialogue.trigger(
                        creature,
                        origin,
                        newState ? Cue.SOCIAL_FOLLOW : Cue.SOCIAL_WAIT);
            }
            case "social_rest" -> sharedRest(origin);
            case "social_goodbye" ->
                    NpcDialogue.trigger(creature, origin, Cue.SOCIAL_GOODBYE);
            case "open_function_wheel" -> {
                if (bonded && LatexSocialMemory.isPetOwner(creature, origin)) {
                    LatexSocialEvents.openBondedPetMenu(origin, creature);
                }
            }
            default -> {
            }
        }
    }

    @Override
    public void removed(Player viewer) {
        if (viewer instanceof ServerPlayer serverPlayer) {
            releaseAudience(serverPlayer);
        }
        super.removed(viewer);
    }

    private void releaseAudience(ServerPlayer viewer) {
        if (!audienceReleased && creature != null) {
            audienceReleased = true;
            SocialAudienceGoal.end(creature, viewer);
        }
    }

    private static boolean validRelationship(
            ChangedEntity creature,
            ServerPlayer player,
            boolean bonded) {
        if (!creature.isAlive()
                || creature.isRemoved()
                || creature.level() != player.level()
                || player.distanceToSqr(creature) > 64.0D) {
            return false;
        }
        if (!bonded
                && InvoluntaryTransfurNegotiation.canNegotiate(
                        player, creature)) {
            return true;
        }
        if (!ChangedSynergyGameRules.enabled(
                player.level(), bonded
                        ? ChangedSynergyGameRules.BOND_SYSTEM
                        : ChangedSynergyGameRules.FRIENDSHIP_SYSTEM)) {
            return false;
        }
        boolean relationship = bonded
                ? LatexSocialMemory.isPetOwner(creature, player)
                : CreaturePersonality.hasTrustedRelationship(creature, player)
                        && !LatexSocialMemory.isProvoked(creature, player)
                        && !LatexSocialMemory.hasBetrayedPatTruce(creature, player);
        return CreatureSocialProfile.allowsSocialWheel(creature)
                && relationship;
    }

    private void sharedRest(ServerPlayer origin) {
        if (!bonded) {
            return;
        }
        long now = creature.level().getGameTime();
        if (creature.getTarget() != null || origin.getLastHurtByMob() != null) {
            origin.sendSystemMessage(Component.translatable(
                    "message.changed_synergy.social.rest_busy"));
            return;
        }
        long next = creature.getPersistentData().getLong(NEXT_SHARED_REST);
        if (next > now) {
            long seconds = Math.max(1L, (next - now + 19L) / 20L);
            origin.sendSystemMessage(Component.translatable(
                    "message.changed_synergy.social.rest_cooldown", seconds));
            return;
        }
        creature.getPersistentData().putLong(
                NEXT_SHARED_REST,
                now + CreaturePersonality.sharedRestCooldown(creature, origin));
        creature.getNavigation().stop();
        float healing = CreaturePersonality.sharedRestHealing(creature, origin);
        creature.heal(healing);
        origin.heal(healing);
        int regenerationAmplifier = CreaturePersonality.relationshipTier(
                creature, origin) == RelationshipTier.CLOSE ? 1 : 0;
        creature.addEffect(new MobEffectInstance(
                MobEffects.REGENERATION, 100, regenerationAmplifier,
                false, false, true));
        origin.addEffect(new MobEffectInstance(
                MobEffects.REGENERATION, 100, regenerationAmplifier,
                false, false, true));
        NpcDialogue.trigger(creature, origin, Cue.SOCIAL_REST);
    }

    private Cue conversationCue(ServerPlayer origin) {
        if (creature.getHealth() <= creature.getMaxHealth() * 0.4F) {
            return Cue.SOCIAL_TALK_HURT;
        }
        if (origin.getHealth() <= origin.getMaxHealth() * 0.4F) {
            return Cue.SOCIAL_TALK_PLAYER_HURT;
        }
        if (creature.level().isRainingAt(creature.blockPosition())) {
            return Cue.SOCIAL_TALK_RAIN;
        }
        if (!creature.level().isDay()) {
            return Cue.SOCIAL_TALK_NIGHT;
        }
        return switch (CreaturePersonality.relationshipTier(creature, origin)) {
            case CLOSE -> Cue.SOCIAL_TALK_CLOSE;
            case FAMILIAR -> Cue.SOCIAL_TALK_FAMILIAR;
            default -> Cue.SOCIAL_TALK_NEW;
        };
    }

    private Cue patCue(ServerPlayer origin) {
        return switch (CreaturePersonality.relationshipTier(creature, origin)) {
            case CLOSE -> Cue.SOCIAL_PAT_CLOSE;
            case FAMILIAR -> Cue.SOCIAL_PAT_FAMILIAR;
            default -> Cue.SOCIAL_PAT_NEW;
        };
    }

    private void playTogether(ServerPlayer origin) {
        long remaining = CreaturePersonality.playCooldownRemaining(
                creature,
                origin,
                CreaturePersonality.socialPlayCooldown(
                        creature, origin, bonded));
        if (remaining > 0L) {
            origin.sendSystemMessage(Component.translatable(
                    "message.changed_synergy.social.play_cooldown",
                    Math.max(1L, (remaining + 19L) / 20L)));
            return;
        }
        Entity recentAttacker = origin.getLastHurtByMob();
        if (creature.getTarget() != null
                || recentAttacker != null
                        && recentAttacker.isAlive()
                        && origin.tickCount
                                - origin.getLastHurtByMobTimestamp() <= 100) {
            origin.sendSystemMessage(Component.translatable(
                    "message.changed_synergy.social.play_busy"));
            return;
        }

        CreaturePersonality.rememberPlayedTogether(creature, origin);
        LatexSocialEvents.calmTowards(creature, origin);
        creature.getNavigation().stop();
        creature.getLookControl().setLookAt(origin, 30.0F, 30.0F);

        if (HypnosisProfile.isHypnoticCreature(creature)
                && HypnosisQteService.startFriendlyPractice(
                        creature, origin)) {
            return;
        }

        Trait trait = CreaturePersonality.dominantTrait(creature);
        if (tryFriendlyHug(origin, trait)) {
            if (creature.level() instanceof ServerLevel level) {
                level.sendParticles(
                        ParticleTypes.HEART,
                        creature.getX(),
                        creature.getY(0.8D),
                        creature.getZ(),
                        4,
                        0.22D,
                        0.28D,
                        0.22D,
                        0.02D);
            }
            NpcDialogue.trigger(
                    creature,
                    origin,
                    bonded
                            ? Cue.SOCIAL_PLAY_HUG_BONDED
                            : Cue.SOCIAL_PLAY_HUG_FRIEND);
            return;
        }

        double currentAngle = Math.atan2(
                creature.getZ() - origin.getZ(),
                creature.getX() - origin.getX());
        double turn = creature.getRandom().nextBoolean() ? 1.15D : -1.15D;
        double radius = switch (trait) {
            case CAUTIOUS, SENSITIVE -> 2.6D;
            case CALM, POLITE -> 2.2D;
            default -> 1.8D;
        };
        double angle = currentAngle + turn;
        double speed = switch (trait) {
            case COMPETITIVE, PLAYFUL, SHOW_OFF -> 1.05D;
            case CAUTIOUS, CALM -> 0.72D;
            default -> 0.85D;
        };
        creature.getNavigation().moveTo(
                origin.getX() + Math.cos(angle) * radius,
                origin.getY(),
                origin.getZ() + Math.sin(angle) * radius,
                speed);
        if (trait == Trait.PLAYFUL
                || trait == Trait.COMPETITIVE
                || trait == Trait.SHOW_OFF) {
            creature.getJumpControl().jump();
        }
        if (creature.level() instanceof ServerLevel level) {
            level.sendParticles(
                    ParticleTypes.HAPPY_VILLAGER,
                    creature.getX(),
                    creature.getY(0.7D),
                    creature.getZ(),
                    5,
                    0.28D,
                    0.2D,
                    0.28D,
                    0.02D);
        }
        NpcDialogue.trigger(creature, origin, Cue.SOCIAL_PLAY);
    }

    private boolean tryFriendlyHug(ServerPlayer origin, Trait trait) {
        if (!ChangedAddonCompat.isLoaded()
                || creature.distanceToSqr(origin) > 3.0D * 3.0D) {
            return false;
        }

        double chance = bonded ? 0.27D : 0.17D;
        if (CreaturePersonality.relationshipTier(
                creature, origin) == RelationshipTier.CLOSE) {
            chance += 0.08D;
        }
        chance += switch (trait) {
            case PLAYFUL -> 0.14D;
            case PROTECTIVE -> 0.09D;
            case SHOW_OFF, CURIOUS -> 0.05D;
            case CAUTIOUS, SENSITIVE -> -0.07D;
            default -> 0.0D;
        };
        if (CreatureSocialProfile.isJuvenile(creature)) {
            chance += 0.10D;
        }
        chance = Math.max(0.08D, Math.min(0.50D, chance));
        if (creature.getRandom().nextDouble() >= chance) {
            return false;
        }

        int duration = 48 + creature.getRandom().nextInt(21);
        if (CreatureSocialProfile.isJuvenile(creature)) {
            duration += 6;
        }
        return ChangedAddonCompat.tryStartSocialHug(
                creature, origin, duration);
    }

    /** Writes the complete relationship card beside the wheel in one packet. */
    public static void writeOpeningData(
            FriendlyByteBuf buffer,
            ServerPlayer player,
            ChangedEntity creature,
            boolean bonded) {
        writeOpeningData(buffer, player, creature, bonded, false);
    }

    public static void writeOpeningData(
            FriendlyByteBuf buffer,
            ServerPlayer player,
            ChangedEntity creature,
            boolean bonded,
            boolean negotiation) {
        buffer.writeVarInt(creature.getId());
        buffer.writeBoolean(bonded
                ? LatexSocialMemory.isFollowingOwner(creature)
                : CreaturePersonality.isSocialFollowing(creature, player));
        buffer.writeVarInt(CreaturePersonality.familiarity(creature, player));
        buffer.writeVarInt(
                CreaturePersonality.relationshipTier(creature, player).ordinal());
        buffer.writeVarInt(CreaturePersonality.dominantTrait(creature).ordinal());
        buffer.writeVarInt(HumanIntent.of(creature).ordinal());
        buffer.writeBoolean(LatexSocialMemory.isOrganic(creature));
        buffer.writeBoolean(bonded);
        buffer.writeBoolean(negotiation);
        buffer.writeBoolean(false);
        if (negotiation) {
            writeNegotiationView(
                    buffer,
                    InvoluntaryTransfurNegotiation.view(player).orElse(
                             new View(
                                     Mode.ASSIMILATION,
                                     Reason.COMPANION_SEEKING,
                                     0, 1, 0, 0, false,
                                     false, false)));
        }
        buffer.writeBoolean(!negotiation
                && InvoluntaryTransfurNegotiation.canNegotiate(
                        player, creature));
        buffer.writeBoolean(CreaturePersonality.hasTrustedRelationship(
                creature, player));
        buffer.writeBoolean(!bonded
                && !negotiation
                && VoluntaryBondTransfurService.canOffer(creature, player));
        buffer.writeBoolean(negotiation
                && InvoluntaryTransfurNegotiation.canOfferFoodBribe(
                        player, creature));
        buffer.writeBoolean(negotiation
                && InvoluntaryTransfurNegotiation.usesFelineFoodBribe(
                        player, creature));
        PanelData.of(creature, player).write(buffer);
    }

    /**
     * Writes an absorption negotiation that has no separate world entity yet.
     * The creature is still the body surrounding the transformed player and
     * is only materialised when both sides agree to separate.
     */
    public static void writeAbsorptionOpeningData(
            FriendlyByteBuf buffer,
            ServerPlayer player) {
        buffer.writeVarInt(player.getId());
        buffer.writeBoolean(false);
        buffer.writeVarInt(0);
        buffer.writeVarInt(RelationshipTier.STRANGER.ordinal());
        buffer.writeVarInt(InvoluntaryTransfurNegotiation
                .absorptionSourceTrait(player).ordinal());
        buffer.writeVarInt(HumanIntent.GREET.ordinal());
        buffer.writeBoolean(false);
        buffer.writeBoolean(false);
        buffer.writeBoolean(true);
        buffer.writeBoolean(true);
        writeNegotiationView(
                buffer,
                InvoluntaryTransfurNegotiation.view(player).orElse(
                         new View(
                                 Mode.ABSORPTION,
                                 Reason.HOST_SEEKING,
                                 0, 1, 0, 0, false,
                                 false, false)));
        buffer.writeBoolean(false);
        buffer.writeBoolean(false);
        buffer.writeBoolean(false);
        buffer.writeBoolean(InvoluntaryTransfurNegotiation
                .canOfferFoodBribe(player, null));
        buffer.writeBoolean(InvoluntaryTransfurNegotiation
                .usesFelineFoodBribe(player, null));
        PanelData.empty().write(buffer);
        buffer.writeComponent(InvoluntaryTransfurNegotiation
                .absorptionSourceName(player));
        buffer.writeNbt(InvoluntaryTransfurNegotiation
                .absorptionSourceAppearance(player));
    }

    private static void writeNegotiationView(
            FriendlyByteBuf buffer,
            View view) {
        buffer.writeVarInt(view.mode().ordinal());
        buffer.writeVarInt(view.reason().ordinal());
        buffer.writeVarInt(view.progress());
        buffer.writeVarInt(view.required());
        buffer.writeVarInt(view.attempts());
        buffer.writeVarInt(view.usedApproaches());
        buffer.writeBoolean(view.failed());
        buffer.writeBoolean(view.specialCompletion());
        buffer.writeBoolean(view.whiteKnightSplit());
    }

    private static View readNegotiationView(FriendlyByteBuf buffer) {
        return new View(
                enumByOrdinal(
                        Mode.values(), buffer.readVarInt(), Mode.ASSIMILATION),
                enumByOrdinal(
                        Reason.values(), buffer.readVarInt(),
                        Reason.COMPANION_SEEKING),
                buffer.readVarInt(),
                Math.max(1, buffer.readVarInt()),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean());
    }

    private static void writeNegotiationView(
            CompoundTag tag,
            View view) {
        tag.putInt("mode", view.mode().ordinal());
        tag.putInt("reason", view.reason().ordinal());
        tag.putInt("progress", view.progress());
        tag.putInt("required", view.required());
        tag.putInt("attempts", view.attempts());
        tag.putInt("usedApproaches", view.usedApproaches());
        tag.putBoolean("failed", view.failed());
        tag.putBoolean("specialCompletion", view.specialCompletion());
        tag.putBoolean("whiteKnightSplit", view.whiteKnightSplit());
    }

    private static View readNegotiationView(CompoundTag tag) {
        return new View(
                enumByOrdinal(
                        Mode.values(), tag.getInt("mode"), Mode.ASSIMILATION),
                enumByOrdinal(
                        Reason.values(), tag.getInt("reason"),
                        Reason.COMPANION_SEEKING),
                tag.getInt("progress"),
                Math.max(1, tag.getInt("required")),
                tag.getInt("attempts"),
                tag.getInt("usedApproaches"),
                tag.getBoolean("failed"),
                tag.getBoolean("specialCompletion"),
                tag.getBoolean("whiteKnightSplit"));
    }

    public enum CommunityStorage {
        NONE,
        CONSENSUS,
        CACHE_READY,
        CACHE_MISSING
    }

    private record PanelData(
            HunterFaction faction,
            String reputationGroupTranslationKey,
            Standing standing,
            int reputation,
            GroupRole role,
            RoutineState routine,
            boolean lifeEnabled,
            int roleStat0,
            int roleStat1,
            int roleStat2,
            int encounters,
            int pats,
            int plays,
            CommunityStorage communityStorage,
            int storedItems,
            int foodDelivered,
            int materialsDelivered) {
        private static PanelData empty() {
            return new PanelData(
                    HunterFaction.LIGHT,
                    "faction.changed_synergy.light",
                    Standing.NEUTRAL,
                    0,
                    GroupRole.SCOUT,
                    RoutineState.IDLE,
                    false,
                    0, 0, 0,
                    0, 0, 0,
                    CommunityStorage.NONE,
                    0, 0, 0);
        }

        private static PanelData of(
                ChangedEntity creature,
                @Nullable ServerPlayer player) {
            HunterFaction faction = HunterFaction.of(creature);
            Standing standing = player == null
                    ? Standing.NEUTRAL
                    : FactionReputation.standing(creature, player);
            int reputation = player == null
                    ? 0 : FactionReputation.score(creature, player);
            CreatureLifeMemory.Snapshot life =
                    CreatureLifeMemory.snapshot(creature);
            CreatureLifeMemory.RoleStats roleStats =
                    CreatureLifeMemory.roleStats(creature);
            MemorySummary memory = player == null
                    ? new MemorySummary(0, 0, 0)
                    : CreaturePersonality.memorySummary(creature, player);
            var community = CreatureCommunityData.snapshot(creature);
            CommunityStorage storage = community
                    .map(snapshot -> snapshot.faction() == HunterFaction.WHITE
                            ? CommunityStorage.CONSENSUS
                            : snapshot.cache().isPresent()
                                    ? CommunityStorage.CACHE_READY
                                    : CommunityStorage.CACHE_MISSING)
                    .orElse(CommunityStorage.NONE);
            return new PanelData(
                    faction,
                    FactionReputation.displayTranslationKey(creature),
                    standing,
                    reputation,
                    life.role(),
                    life.routine(),
                    CreatureLifeMemory.enabled(creature),
                    roleStats.first(),
                    roleStats.second(),
                    roleStats.third(),
                    memory.encounters(),
                    memory.pats(),
                    memory.plays(),
                    storage,
                    community.isPresent()
                            ? CreatureSettlementService.storedItemCount(creature) : 0,
                    community.map(CreatureCommunityData.Snapshot::foodDelivered)
                            .orElse(0),
                    community.map(CreatureCommunityData.Snapshot::materialsDelivered)
                            .orElse(0));
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(faction.ordinal());
            buffer.writeUtf(reputationGroupTranslationKey, 96);
            buffer.writeVarInt(standing.ordinal());
            buffer.writeInt(reputation);
            buffer.writeVarInt(role.ordinal());
            buffer.writeVarInt(routine.ordinal());
            buffer.writeBoolean(lifeEnabled);
            buffer.writeVarInt(roleStat0);
            buffer.writeVarInt(roleStat1);
            buffer.writeVarInt(roleStat2);
            buffer.writeVarInt(encounters);
            buffer.writeVarInt(pats);
            buffer.writeVarInt(plays);
            buffer.writeVarInt(communityStorage.ordinal());
            buffer.writeVarInt(storedItems);
            buffer.writeVarInt(foodDelivered);
            buffer.writeVarInt(materialsDelivered);
        }

        private static PanelData read(FriendlyByteBuf buffer) {
            return new PanelData(
                    enumByOrdinal(
                            HunterFaction.values(), buffer.readVarInt(),
                            HunterFaction.LIGHT),
                    buffer.readUtf(96),
                    enumByOrdinal(
                            Standing.values(), buffer.readVarInt(),
                            Standing.NEUTRAL),
                    buffer.readInt(),
                    enumByOrdinal(
                            GroupRole.values(), buffer.readVarInt(),
                            GroupRole.SCOUT),
                    enumByOrdinal(
                            RoutineState.values(), buffer.readVarInt(),
                            RoutineState.IDLE),
                    buffer.readBoolean(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    enumByOrdinal(
                            CommunityStorage.values(), buffer.readVarInt(),
                            CommunityStorage.NONE),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt());
        }
    }

    private static <T> T enumByOrdinal(T[] values, int ordinal, T fallback) {
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : fallback;
    }
}
