package net.parkabird.changedsynergy.ai;

import javax.annotation.Nullable;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.variant.TransfurVariant;
import net.ltxprogrammer.changed.init.ChangedFusions;
import net.ltxprogrammer.changed.init.ChangedGameRules;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ai.CreaturePersonality.Trait;
import net.parkabird.changedsynergy.ai.InvoluntaryTransfurNegotiation.Reason;

/**
 * Lets Changed's own latex-fusion recipes run as curious social approaches.
 * This does not invent cross-species recipes: a pair is accepted only when it
 * appears in Changed's currently loaded fusion table.
 */
public final class LatexFusionIntent {
    private static final String WHITE_KNIGHT = "white_latex_knight";
    private static final String WHITE_WOLF_MALE = "white_latex_wolf_male";
    private static final String WHITE_WOLF_FEMALE = "white_latex_wolf_female";
    private static final String RELEASE_COOLDOWN_PLAYER =
            "ChangedSynergyFusionCooldownPlayer";
    private static final String RELEASE_COOLDOWN_UNTIL =
            "ChangedSynergyFusionCooldownUntil";
    private static final long RELEASE_COOLDOWN_TICKS = 5L * 60L * 20L;

    private LatexFusionIntent() {
    }

    public static boolean mayInitiate(
            ChangedEntity source,
            ServerPlayer player) {
        if (!nativeFusionAvailable(source, player)
                || !mayUseBodyForFusion(source)
                || CreaturePersonality.has(source, Trait.POLITE)
                || isReleaseCooldownActive(source, player)) {
            return false;
        }
        return ProcessTransfur.getPlayerTransfurVariantSafe(player)
                .map(instance -> !instance.isTemporaryFromSuit())
                .orElse(false);
    }

    /**
     * Prevents an individual that has just separated from a player from
     * immediately starting the same voluntary-looking fusion loop again.
     * The marker is scoped to that exact creature/player pair.
     */
    public static void beginReleaseCooldown(
            ChangedEntity source,
            ServerPlayer player) {
        source.getPersistentData().putUUID(
                RELEASE_COOLDOWN_PLAYER, player.getUUID());
        source.getPersistentData().putLong(
                RELEASE_COOLDOWN_UNTIL,
                source.level().getGameTime() + RELEASE_COOLDOWN_TICKS);
    }

    public static boolean isReleaseCooldownActive(
            ChangedEntity source,
            ServerPlayer player) {
        var data = source.getPersistentData();
        if (!data.hasUUID(RELEASE_COOLDOWN_PLAYER)
                || !player.getUUID().equals(
                        data.getUUID(RELEASE_COOLDOWN_PLAYER))) {
            return false;
        }
        if (data.getLong(RELEASE_COOLDOWN_UNTIL)
                > source.level().getGameTime()) {
            return true;
        }
        data.remove(RELEASE_COOLDOWN_PLAYER);
        data.remove(RELEASE_COOLDOWN_UNTIL);
        return false;
    }

    public static boolean isCompatiblePair(
            ChangedEntity source,
            ServerPlayer player) {
        if (!source.isAlive()
                || !player.isAlive()
                || player.isCreative()
                || player.isSpectator()) {
            return false;
        }
        return ProcessTransfur.getPlayerTransfurVariantSafe(player)
                .map(instance -> hasNativeRecipe(
                        source.getSelfVariant(), instance.getParent()))
                .orElse(false);
    }

    /**
     * Mirrors Changed's current player-side fusion gates. Takeover may wrap an
     * ordinary absorption only after this returns false, including on addon grab
     * paths that ask for an absorption behavior before consulting fusion recipes.
     */
    public static boolean nativeFusionAvailable(
            ChangedEntity source,
            ServerPlayer player) {
        if (!isCompatiblePair(source, player)
                || !player.level().getGameRules().getBoolean(
                        ChangedGameRules.RULE_NPC_WANT_FUSE_PLAYER)) {
            return false;
        }
        int maximumAge = player.level().getGameRules().getInt(
                ChangedGameRules.RULE_FUSABILITY_DURATION_PLAYER);
        return ProcessTransfur.getPlayerTransfurVariantSafe(player)
                .map(instance -> instance.ageAsVariant <= maximumAge)
                .orElse(false);
    }

    /** Used by the post-fusion event, where the player's old form is abstracted. */
    public static boolean mayCapture(
            ChangedEntity source,
            @Nullable TransfurVariant<?> targetVariant) {
        return source.isAlive()
                && mayUseBodyForFusion(source)
                && !CreaturePersonality.has(source, Trait.POLITE)
                && hasNativeRecipe(source.getSelfVariant(), targetVariant);
    }

    public static boolean hasNativeRecipe(
            @Nullable TransfurVariant<?> source,
            @Nullable TransfurVariant<?> target) {
        return source != null
                && target != null
                && ChangedFusions.INSTANCE.getFusionsFor(source, target)
                        .findAny().isPresent();
    }

    private static boolean mayUseBodyForFusion(ChangedEntity source) {
        // A bonded/native pet is an identity the player relies on. Never let a
        // spontaneous fusion consume it or silently replace that relationship.
        return !LatexSocialMemory.hasActiveBond(source)
                && LatexSocialMemory.petOwnerUuid(source).isEmpty();
    }

    public static Reason reasonFor(ChangedEntity source) {
        ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(source.getType());
        if (isIncompleteFusionSource(type)) {
            return Reason.FUSION_COMPLETION;
        }
        if (CreaturePersonality.has(source, Trait.COMPETITIVE)) {
            return Reason.FUSION_STRENGTH;
        }
        if (CreaturePersonality.has(source, Trait.PLAYFUL)
                || CreaturePersonality.has(source, Trait.SHOW_OFF)) {
            return Reason.FUSION_PLAY;
        }
        return Reason.FUSION_CURIOSITY;
    }

    public static boolean isIncompleteFusionSource(
            @Nullable ResourceLocation type) {
        if (type == null || !"changed".equals(type.getNamespace())) {
            return false;
        }
        return switch (type.getPath()) {
            case "milk_pudding", "latex_shark_feral", "headless_knight",
                    "pure_white_latex_wolf_pup" -> true;
            default -> false;
        };
    }

    public static boolean isWhiteKnight(ChangedEntity creature) {
        return isChangedPath(
                ForgeRegistries.ENTITY_TYPES.getKey(creature.getType()),
                WHITE_KNIGHT);
    }

    public static boolean isOrdinaryWhiteLatexWolf(
            @Nullable TransfurVariant<?> variant) {
        if (variant == null) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(
                variant.getEntityType());
        return isChangedPath(id, WHITE_WOLF_MALE)
                || isChangedPath(id, WHITE_WOLF_FEMALE);
    }

    public static boolean isWhiteKnightWolfPair(
            ChangedEntity source,
            ServerPlayer player) {
        return isWhiteKnight(source)
                && ProcessTransfur.getPlayerTransfurVariantSafe(player)
                        .map(instance -> isOrdinaryWhiteLatexWolf(
                                instance.getParent()))
                        .orElse(false);
    }

    private static boolean isChangedPath(
            @Nullable ResourceLocation id,
            String path) {
        return id != null
                && "changed".equals(id.getNamespace())
                && path.equals(id.getPath());
    }
}
