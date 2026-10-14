package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.beast.AbstractLatexWolf;
import net.ltxprogrammer.changed.entity.beast.AquaticEntity;
import net.ltxprogrammer.changed.entity.variant.TransfurVariant;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ai.CreaturePersonality.RelationshipProgress;

/** Handles tangible gifts without creating a second relationship score. */
public final class RelationshipFavorService {
    private static final ResourceLocation ORANGE =
            ResourceLocation.fromNamespaceAndPath("changed", "orange");
    private static final ResourceLocation CHANGED_ADDITIONS_GOLDEN_ORANGE =
            ResourceLocation.fromNamespaceAndPath(
                    "changed_additions", "golden_orange");
    private static final ResourceLocation CHANGED_ADDON_GOLDEN_ORANGE =
            ResourceLocation.fromNamespaceAndPath(
                    "changed_addon", "golden_orange");
    private static final ResourceKey<Registry<TransfurVariant<?>>>
            TRANSFUR_VARIANT_REGISTRY = ResourceKey.createRegistryKey(
                    ResourceLocation.fromNamespaceAndPath(
                            "changed", "latex_variant"));
    private static final TagKey<TransfurVariant<?>> NO_DIET =
            variantTag("no_diet");

    private RelationshipFavorService() {
    }

    public enum Result {
        NOT_APPLICABLE,
        NO_ITEM,
        UNSUITABLE,
        REJECTED,
        CAT_ORANGE_REFUSED,
        BUILDING,
        ESTABLISHED,
        EXISTING,
        DIET_BUILDING,
        DIET_ESTABLISHED,
        DIET_EXISTING,
        COOLDOWN,
        NEEDS_FAMILIARITY,
        TOKEN_ACCEPTED;

        public boolean isAccepted() {
            return switch (this) {
                case BUILDING, ESTABLISHED, EXISTING,
                        DIET_BUILDING, DIET_ESTABLISHED,
                        DIET_EXISTING, TOKEN_ACCEPTED -> true;
                default -> false;
            };
        }

        public boolean isDedicatedDiet() {
            return switch (this) {
                case DIET_BUILDING, DIET_ESTABLISHED, DIET_EXISTING -> true;
                default -> false;
            };
        }
    }

    public static boolean isOrange(ItemStack stack) {
        return isOrdinaryOrange(stack) || isGoldenOrange(stack);
    }

    public static boolean isOrdinaryOrange(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return ORANGE.equals(id);
    }

    public static boolean isGoldenOrange(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return CHANGED_ADDON_GOLDEN_ORANGE.equals(id)
                || CHANGED_ADDITIONS_GOLDEN_ORANGE.equals(id);
    }

    public static boolean acceptsOrange(ChangedEntity creature) {
        return !isFeline(creature);
    }

    /**
     * Orange is a modest universal peace offering. A food listed by Changed
     * Addon's creature-diet tags is a stronger, species-aware offering.
     */
    public static Result offerHeldFood(
            ChangedEntity creature,
            ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!LatexSocialMemory.isSocialLatex(creature)
                || !CreatureSocialProfile.allowsPersonalRelationship(creature)) {
            return Result.NOT_APPLICABLE;
        }

        boolean orange = isOrange(stack);
        boolean dedicated = !orange
                && isDedicatedDietFood(creature, stack);
        if (!orange && !dedicated) {
            return Result.NOT_APPLICABLE;
        }
        if (orange && isFeline(creature)) {
            showScentRejection(creature);
            return Result.CAT_ORANGE_REFUSED;
        }
        if (isGiftRejected(creature, player)) {
            if (!canMakePeace(creature, player)) {
                showNegativeFeedback(creature, 3);
                return Result.REJECTED;
            }
            LatexSocialMemory.reconcileRelationship(creature, player);
            net.parkabird.changedsynergy.event.LatexSocialEvents.clearPendingCombatReactions(creature, player);
            TakeoverConflictMemory.clear(creature, player);
        }

        if (CreaturePersonality.balanced()) {
            int gained = CreaturePersonality.rewardCare(creature, player, 2);
            healFromFood(creature, stack, dedicated);
            applyGoldenOrangeBenefits(creature, stack);
            LatexSocialMemory.beginTruce(creature, player, dedicated || isGoldenOrange(stack) ? 1200L : 800L);
            consumeOne(player, stack);
            if (gained > 0) {
                FactionReputation.adjustFromInteraction(creature, player, dedicated ? 2 : 1);
                showPositiveFeedback(creature, 3);
            }
            return dedicated ? Result.DIET_EXISTING : Result.EXISTING;
        }

        int favor = isGoldenOrange(stack)
                ? CreatureSocialProfile.isJuvenile(creature) ? 18 : 16
                : dedicated
                ? CreatureSocialProfile.isJuvenile(creature) ? 16 : 14
                : CreatureSocialProfile.isJuvenile(creature) ? 10 : 8;
        return acceptGift(
                creature, player, stack, favor, true, dedicated);
    }

    /** Established acquaintances can accept the item currently held by the player. */
    public static Result offerHeldRelationshipGift(
            ChangedEntity creature,
            ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            return Result.NO_ITEM;
        }
        if (!canOpenBasicWheel(creature, player)) {
            return Result.REJECTED;
        }
        if (isGiftRejected(creature, player)) {
            showNegativeFeedback(creature, 3);
            return Result.REJECTED;
        }
        if (CreaturePersonality.giftCooldownRemaining(creature, player) > 0) return Result.COOLDOWN;
        if (CreaturePersonality.balanced() && !CreaturePersonality.hasEstablishedRelationship(creature, player)
                && CreaturePersonality.familiarity(creature, player) < CreaturePersonality.relationshipThreshold(creature))
            return Result.NEEDS_FAMILIARITY;
        if (net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.bondTokensRequired.get()
                && stack.is(BondTokenService.required(creature).getItem())) {
            return BondTokenService.offer(creature, player) ? Result.TOKEN_ACCEPTED : Result.REJECTED;
        }
        if (isOrange(stack) && isFeline(creature)) {
            showScentRejection(creature);
            return Result.CAT_ORANGE_REFUSED;
        }
        boolean orange = isOrange(stack);
        boolean dedicated = !orange
                && isDedicatedDietFood(creature, stack);
        if (CreatureSocialProfile.isJuvenile(creature)
                && !stack.isEdible()
                && !orange
                && !dedicated) {
            return Result.UNSUITABLE;
        }
        int favor = CreaturePersonality.balanced() ? isGoldenOrange(stack) ? 6 : dedicated ? 4 : 2
                : isGoldenOrange(stack) ? 16
                : dedicated ? 14
                : orange ? 8
                : stack.isEdible() ? 4 : 2;
        return acceptGift(
                creature, player, stack, favor, CreaturePersonality.balanced(), dedicated);
    }

    public static boolean canOpenBasicWheel(ChangedEntity creature, ServerPlayer player) {
        return net.parkabird.changedsynergy.init.ChangedSynergyGameRules.enabled(creature.level(), net.parkabird.changedsynergy.init.ChangedSynergyGameRules.FRIENDSHIP_SYSTEM)
                && CreatureSocialProfile.allowsPersonalRelationship(creature)
                && !net.parkabird.changedsynergy.compat.TrueTransfurCompat.isInstinctShell(player)
                && !isGiftRejected(creature, player)
                && (CreaturePersonality.hasTrustedRelationship(creature, player)
                    || LatexSocialMemory.isPetOwner(creature, player)
                    || CreaturePersonality.balanced() && (!FactionReputation.isHostile(creature, player)
                            || LatexSocialMemory.isTruced(creature, player))
                        && (LatexSocialMemory.isTruced(creature, player)
                            || PoliteHumanInteraction.isAcquainted(creature, player)
                            || creature.getTarget() != player && CreaturePersonality.familiarity(creature, player) > 0));
    }

    public static boolean isDedicatedDietFood(
            ChangedEntity creature,
            ItemStack stack) {
        if (stack.is(Items.SWEET_BERRIES)
                && CreatureSettlementService.isTaigaCommunity(creature)) {
            return true;
        }
        TransfurVariant<?> variant = creature.getSelfVariant();
        if (stack.isEmpty()
                || variant == null
                || variant.is(NO_DIET)) {
            return false;
        }
        // Base Changed cats must be feedable without Addon's optional diet tags.
        if (isFeline(creature) && (stack.is(Items.COD) || stack.is(Items.SALMON)
                || stack.is(Items.COOKED_COD) || stack.is(Items.COOKED_SALMON))) return true;
        for (Diet diet : Diet.values()) {
            if (diet.appliesTo(creature, variant)
                    && stack.is(diet.itemTag)) {
                return true;
            }
        }
        return false;
    }

    public static void showNegativeFeedback(ChangedEntity creature, int count) {
        if (creature.level() instanceof ServerLevel level) {
            level.sendParticles(
                    ParticleTypes.ANGRY_VILLAGER,
                    creature.getX(),
                    creature.getY(0.75D),
                    creature.getZ(),
                    Math.max(1, count),
                    0.25D,
                    0.22D,
                    0.25D,
                    0.01D);
        }
    }

    private static Result acceptGift(
            ChangedEntity creature,
            ServerPlayer player,
            ItemStack stack,
            int favor,
            boolean mayEstablish,
            boolean dedicated) {
        if (isGiftRejected(creature, player)) {
            showNegativeFeedback(creature, 3);
            return Result.REJECTED;
        }

        boolean goldenOrange = isGoldenOrange(stack);
        boolean established = mayEstablish && CreaturePersonality.balanced()
                && !CreaturePersonality.hasEstablishedRelationship(creature, player)
                && CreaturePersonality.establishRelationship(creature, player);
        int gained = CreaturePersonality.rememberGiftReceived(creature, player, favor);
        healFromFood(creature, stack, dedicated);
        applyGoldenOrangeBenefits(creature, stack);
        consumeOne(player, stack);
        if (gained > 0) FactionReputation.adjustFromInteraction(
                creature, player,
                dedicated || goldenOrange ? 2 : 1);
        LatexSocialMemory.beginTruce(
                creature, player,
                dedicated || goldenOrange ? 1200L : 800L);
        if (gained > 0) showPositiveFeedback(creature, 3);
        if (established) return dedicated ? Result.DIET_ESTABLISHED : Result.ESTABLISHED;

        if (!mayEstablish) {
            return dedicated ? Result.DIET_EXISTING : Result.EXISTING;
        }
        RelationshipProgress progress =
                CreaturePersonality.advanceRelationship(creature, player);
        Result ordinary = switch (progress) {
            case ESTABLISHED -> Result.ESTABLISHED;
            case EXISTING -> Result.EXISTING;
            case BUILDING -> Result.BUILDING;
            case INELIGIBLE -> Result.REJECTED;
        };
        if (!dedicated) {
            return ordinary;
        }
        return switch (ordinary) {
            case ESTABLISHED -> Result.DIET_ESTABLISHED;
            case EXISTING -> Result.DIET_EXISTING;
            case BUILDING -> Result.DIET_BUILDING;
            default -> ordinary;
        };
    }

    public static boolean isFeline(ChangedEntity creature) {
        TransfurVariant<?> variant = creature.getSelfVariant();
        return HunterArchetype.of(creature) == HunterArchetype.FELINE
                || variant != null && variant.is(Diet.CAT.variantTag);
    }

    private static boolean isGiftRejected(
            ChangedEntity creature,
            ServerPlayer player) {
        return LatexSocialMemory.isProvoked(creature, player)
                || LatexSocialMemory.hasBetrayedPatTruce(creature, player)
                || LatexSocialMemory.hasRelationshipBetrayal(creature, player);
    }

    /** A suitable peace offering is accepted after ten seconds without a new player attack. */
    public static boolean canMakePeace(ChangedEntity creature, ServerPlayer player) {
        var body = net.parkabird.changedsynergy.compat.TrueTransfurCompat.interactionBody(player);
        if (!creature.isAlive() || !body.isAlive() || body.isRemoved() || body.level() != creature.level()
                || body == player && net.parkabird.changedsynergy.compat.TrueTransfurCompat.isInstinctShell(player)
                || net.parkabird.changedsynergy.compat.ChangedAddonCompat.isGrabberBusy(creature)
                || TakeoverService.active(player)) return false;
        return !(creature.getLastHurtByMob() == body && creature.tickCount - creature.getLastHurtByMobTimestamp() < 200
                || body.getLastHurtMob() == creature && body.tickCount - body.getLastHurtMobTimestamp() < 200);
    }

    private static void showPositiveFeedback(ChangedEntity creature, int count) {
        if (creature.level() instanceof ServerLevel level) {
            level.sendParticles(
                    ParticleTypes.HEART,
                    creature.getX(),
                    creature.getY(0.75D),
                    creature.getZ(),
                    count,
                    0.25D,
                    0.22D,
                    0.25D,
                    0.02D);
        }
    }

    private static void healFromFood(
            ChangedEntity creature,
            ItemStack stack,
            boolean dedicated) {
        float healing = dedicated
                ? 6.0F
                : isOrange(stack)
                        ? 4.0F
                        : stack.isEdible() ? 2.0F : 0.0F;
        if (healing > 0.0F && creature.getHealth() < creature.getMaxHealth()) {
            creature.heal(healing);
        }
    }

    /** Mirrors the effects supplied by both compatible Golden Orange items. */
    public static void applyGoldenOrangeBenefits(
            ChangedEntity creature,
            ItemStack stack) {
        if (!isGoldenOrange(stack)) {
            return;
        }
        creature.addEffect(new MobEffectInstance(
                MobEffects.REGENERATION, 100, 1));
        creature.addEffect(new MobEffectInstance(
                MobEffects.ABSORPTION, 2400, 0));
        creature.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SPEED, 100, 0));
    }

    private static void showScentRejection(ChangedEntity creature) {
        if (creature.level() instanceof ServerLevel level) {
            level.sendParticles(
                    ParticleTypes.SNEEZE,
                    creature.getX(),
                    creature.getY(0.82D),
                    creature.getZ(),
                    4,
                    0.20D,
                    0.16D,
                    0.20D,
                    0.01D);
        }
    }

    private static void consumeOne(ServerPlayer player, ItemStack stack) {
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
            player.getInventory().setChanged();
        }
    }

    private static TagKey<TransfurVariant<?>> variantTag(String path) {
        return TagKey.create(
                TRANSFUR_VARIANT_REGISTRY,
                ResourceLocation.fromNamespaceAndPath(
                        "changed_addon", path));
    }

    private static TagKey<Item> itemTag(String path) {
        return TagKey.create(
                Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(
                        "changed_addon", path));
    }

    private enum Diet {
        AQUATIC("aquatic_diet", "aquatic_diet_list"),
        SHARK("shark_diet", "shark_diet_list"),
        CAT("cat_diet", "cat_diet_list"),
        DRAGON("dragon_diet", "dragon_diet_list"),
        FOX("fox_diet", "fox_diet_list"),
        SWEET("sweet_tooth", "sweet_tooth_list"),
        WOLF("wolf_diet", "wolf_diet_list"),
        SPECIAL("special_diet", "special_diet_list");

        private final TagKey<TransfurVariant<?>> variantTag;
        private final TagKey<Item> itemTag;

        Diet(String variantPath, String itemPath) {
            this.variantTag = variantTag(variantPath);
            this.itemTag = itemTag(itemPath);
        }

        private boolean appliesTo(
                ChangedEntity creature,
                TransfurVariant<?> variant) {
            return switch (this) {
                case AQUATIC ->
                        creature instanceof AquaticEntity
                                && !variant.is(SHARK.variantTag)
                                || variant.is(variantTag);
                case WOLF ->
                        creature instanceof AbstractLatexWolf
                                || variant.is(variantTag);
                default -> variant.is(variantTag);
            };
        }
    }
}
