package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.beast.AbstractLatexWolf;
import net.ltxprogrammer.changed.entity.beast.AquaticEntity;
import net.ltxprogrammer.changed.entity.variant.TransfurVariant;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
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
        DIET_EXISTING;

        public boolean isAccepted() {
            return switch (this) {
                case BUILDING, ESTABLISHED, EXISTING,
                        DIET_BUILDING, DIET_ESTABLISHED,
                        DIET_EXISTING -> true;
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
        return !stack.isEmpty()
                && ORANGE.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));
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
        boolean dedicated = isDedicatedDietFood(creature, stack);
        if (!orange && !dedicated) {
            return Result.NOT_APPLICABLE;
        }
        if (isGiftRejected(creature, player)) {
            showNegativeFeedback(creature, 3);
            return Result.REJECTED;
        }
        if (orange && isFeline(creature)) {
            showScentRejection(creature);
            return Result.CAT_ORANGE_REFUSED;
        }

        int favor = dedicated
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
        if (!CreaturePersonality.hasTrustedRelationship(creature, player)
                && !LatexSocialMemory.isPetOwner(creature, player)) {
            return Result.REJECTED;
        }
        if (isGiftRejected(creature, player)) {
            showNegativeFeedback(creature, 3);
            return Result.REJECTED;
        }
        if (isOrange(stack) && isFeline(creature)) {
            showScentRejection(creature);
            return Result.CAT_ORANGE_REFUSED;
        }
        boolean dedicated = isDedicatedDietFood(creature, stack);
        if (CreatureSocialProfile.isJuvenile(creature)
                && !stack.isEdible()
                && !isOrange(stack)
                && !dedicated) {
            return Result.UNSUITABLE;
        }
        int favor = dedicated ? 14
                : isOrange(stack) ? 8
                : stack.isEdible() ? 4 : 2;
        return acceptGift(
                creature, player, stack, favor, false, dedicated);
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

        CreaturePersonality.rememberGiftReceived(creature, player, favor);
        healFromFood(creature, stack, dedicated);
        consumeOne(player, stack);
        FactionReputation.adjustFromInteraction(
                creature, player, dedicated ? 2 : 1);
        LatexSocialMemory.beginPatTruce(
                creature, player, dedicated ? 1200L : 800L);
        showPositiveFeedback(
                creature, favor >= 12 ? 7 : favor >= 8 ? 5 : 3);

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
                || LatexSocialMemory.hasBetrayedPatTruce(creature, player);
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
