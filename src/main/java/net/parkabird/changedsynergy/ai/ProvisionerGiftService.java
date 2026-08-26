package net.parkabird.changedsynergy.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ai.CreatureLifeMemory.GroupRole;
import net.parkabird.changedsynergy.ai.CreatureLifeMemory.RoutineState;
import net.parkabird.changedsynergy.ai.CreatureSettlementService.ProvisionSource;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue;

/** Occasional, low-volume gifts matching a provisioner's actual work. */
public final class ProvisionerGiftService {
    private static final ResourceLocation ORANGE =
            ResourceLocation.fromNamespaceAndPath("changed", "orange");
    private static final ResourceLocation ORANGE_JUICE =
            ResourceLocation.fromNamespaceAndPath(
                    ChangedAddonCompat.MOD_ID, "orange_juice");
    private static final ResourceLocation FOXTA =
            ResourceLocation.fromNamespaceAndPath(
                    ChangedAddonCompat.MOD_ID, "foxta");
    private static final ResourceLocation SNEPSI =
            ResourceLocation.fromNamespaceAndPath(
                    ChangedAddonCompat.MOD_ID, "snepsi");
    private static final ResourceLocation OPENED_CANNED_SOUP =
            ResourceLocation.fromNamespaceAndPath(
                    ChangedAddonCompat.MOD_ID, "opened_canned_soup");
    private static final String NEXT_PROVIDER_GIFT =
            "ChangedSynergyNextProvisionerGift";
    private static final String NEXT_PLAYER_GIFT =
            "ChangedSynergyNextGiftFromProvisioner";
    private static final int CHECK_INTERVAL_TICKS = 40;
    private static final int GIFT_CHANCE_DENOMINATOR = 24;
    private static final double GIFT_RANGE_SQR = 4.5D * 4.5D;
    private static final long PROVIDER_COOLDOWN_MIN = 8L * 60L * 20L;
    private static final int PROVIDER_COOLDOWN_VARIATION = 4 * 60 * 20;
    private static final long PLAYER_COOLDOWN = 2L * 60L * 20L;

    private ProvisionerGiftService() {
    }

    public static void tick(ChangedEntity provider) {
        if (!(provider.level() instanceof ServerLevel level)
                || provider.tickCount % CHECK_INTERVAL_TICKS
                        != Math.floorMod(provider.getId(), CHECK_INTERVAL_TICKS)
                || !CreatureLifeMemory.enabled(provider)
                || CreatureLifeMemory.role(provider) != GroupRole.PROVISIONER
                || CreatureSettlementService.hasCargo(provider)
                || provider.getTarget() != null
                || provider.isAggressive()
                || provider.isPassenger()
                || ChangedAddonCompat.isGrabberBusy(provider)
                || !canPauseForGift(CreatureLifeMemory.routine(provider))) {
            return;
        }

        long now = level.getGameTime();
        if (provider.getPersistentData().getLong(NEXT_PROVIDER_GIFT) > now) {
            return;
        }
        Candidate candidate = level.players().stream()
                .filter(player -> eligiblePlayer(provider, player, now))
                .map(player -> new Candidate(
                        player, GiftTier.forPlayer(provider, player)))
                .filter(entry -> entry.tier != null)
                .max(Comparator
                        .comparingInt((Candidate entry) -> entry.tier.rank)
                        .thenComparingDouble(entry ->
                                -provider.distanceToSqr(entry.player)))
                .orElse(null);
        if (candidate == null
                || provider.getRandom().nextInt(GIFT_CHANCE_DENOMINATOR) != 0) {
            return;
        }

        ProvisionSource source =
                CreatureSettlementService.recentProvisionSource(provider);
        ItemStack gift = selectGift(level, provider, candidate.tier, source);
        if (gift.isEmpty()) {
            return;
        }
        give(provider, candidate.player, candidate.tier, gift, now);
    }

    private static ItemStack selectGift(
            ServerLevel level,
            ChangedEntity provider,
            GiftTier tier,
            ProvisionSource source) {
        return switch (source) {
            case ORANGE -> orangeGift(provider, tier);
            case SWEET_BERRIES -> new ItemStack(
                    Items.SWEET_BERRIES, ordinaryCount(provider, tier));
            case GLOW_BERRIES -> new ItemStack(
                    Items.GLOW_BERRIES, ordinaryCount(provider, tier));
            case MINERAL -> mineralGift(provider, tier);
            case NEARSHORE_FISH -> nearshoreFishGift(provider, tier);
            case OPEN_OCEAN_FISH -> openOceanGift(level, provider, tier);
            case BADLANDS_MINECART -> badlandsMinecartGift(
                    level, provider, tier);
            case FORAGED_FOOD -> rememberedGift(
                    provider, tier, Items.APPLE, true);
            case MATERIAL -> rememberedGift(
                    provider, tier, Items.COBBLESTONE, false);
        };
    }

    private static ItemStack orangeGift(
            ChangedEntity provider,
            GiftTier tier) {
        Item orange = ForgeRegistries.ITEMS.getValue(ORANGE);
        if (orange == null) {
            return ItemStack.EMPTY;
        }
        List<Item> drinks = new ArrayList<>();
        ChangedAddonCompat.item(ORANGE_JUICE).ifPresent(drinks::add);
        if (CreatureSettlementService.isFacilityCommunity(provider)) {
            ChangedAddonCompat.item(FOXTA).ifPresent(drinks::add);
            ChangedAddonCompat.item(SNEPSI).ifPresent(drinks::add);
            ChangedAddonCompat.item(OPENED_CANNED_SOUP).ifPresent(drinks::add);
        }
        boolean giveDrink = !drinks.isEmpty()
                && provider.getRandom().nextDouble()
                        < (CreatureSettlementService.isFacilityCommunity(provider)
                                ? 0.24D + tier.rank * 0.06D
                                : 0.14D + tier.rank * 0.07D);
        if (giveDrink) {
            int count = tier.rank >= 3
                    && provider.getRandom().nextInt(4) == 0 ? 2 : 1;
            Item drink = drinks.get(provider.getRandom().nextInt(drinks.size()));
            return new ItemStack(drink, count);
        }
        return new ItemStack(orange, ordinaryCount(provider, tier));
    }

    /** Nearshore providers can share cooked fish as well as their raw catch. */
    private static ItemStack nearshoreFishGift(
            ChangedEntity provider,
            GiftTier tier) {
        boolean salmon = provider.getRandom().nextInt(3) == 0;
        boolean cooked = provider.getRandom().nextDouble()
                < 0.22D + tier.rank * 0.09D;
        Item item = salmon
                ? cooked ? Items.COOKED_SALMON : Items.SALMON
                : cooked ? Items.COOKED_COD : Items.COD;
        return new ItemStack(item, fishCount(provider, tier));
    }

    /** Open-ocean providers share raw catch and only rarely recovered treasure. */
    private static ItemStack openOceanGift(
            ServerLevel level,
            ChangedEntity provider,
            GiftTier tier) {
        if (tier.rank >= GiftTier.ALLIED.rank
                && provider.getRandom().nextDouble()
                        < 0.03D + tier.rank * 0.015D) {
            ItemStack treasure = ruinTreasure(level, provider);
            if (!treasure.isEmpty()) {
                return treasure;
            }
        }
        Item item = switch (provider.getRandom().nextInt(10)) {
            case 0 -> Items.PUFFERFISH;
            case 1, 2 -> Items.TROPICAL_FISH;
            case 3, 4, 5 -> Items.SALMON;
            default -> Items.COD;
        };
        return new ItemStack(item, fishCount(provider, tier));
    }

    private static ItemStack ruinTreasure(
            ServerLevel level,
            ChangedEntity provider) {
        ResourceLocation tableId = provider.getRandom().nextBoolean()
                ? BuiltInLootTables.UNDERWATER_RUIN_BIG
                : BuiltInLootTables.UNDERWATER_RUIN_SMALL;
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, provider.position())
                .create(LootContextParamSets.CHEST);
        LootTable table = level.getServer().getLootData().getLootTable(tableId);
        List<ItemStack> valuables = table.getRandomItems(params).stream()
                .filter(ProvisionerGiftService::isValuableRuinTreasure)
                .toList();
        if (valuables.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = valuables.get(
                provider.getRandom().nextInt(valuables.size())).copy();
        result.setCount(1);
        return result;
    }

    private static boolean isValuableRuinTreasure(ItemStack stack) {
        return stack.is(Items.EMERALD)
                || stack.is(Items.GOLD_INGOT)
                || stack.is(Items.GOLD_BLOCK)
                || stack.is(Items.GOLDEN_APPLE)
                || stack.is(Items.ENCHANTED_BOOK)
                || stack.is(Items.FILLED_MAP)
                || stack.is(Items.FISHING_ROD) && stack.isEnchanted();
    }

    /** Badlands workers sometimes share a rare find from an exposed minecart. */
    private static ItemStack badlandsMinecartGift(
            ServerLevel level,
            ChangedEntity provider,
            GiftTier tier) {
        if (tier.rank >= GiftTier.ALLIED.rank
                && provider.getRandom().nextDouble()
                        < 0.035D + tier.rank * 0.015D) {
            ItemStack treasure = mineshaftTreasure(level, provider);
            if (!treasure.isEmpty()) {
                return treasure;
            }
        }
        return rememberedGift(provider, tier, Items.BREAD, true);
    }

    private static ItemStack mineshaftTreasure(
            ServerLevel level,
            ChangedEntity provider) {
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, provider.position())
                .create(LootContextParamSets.CHEST);
        LootTable table = level.getServer().getLootData()
                .getLootTable(BuiltInLootTables.ABANDONED_MINESHAFT);
        List<ItemStack> valuables = table.getRandomItems(params).stream()
                .filter(ProvisionerGiftService::isValuableMineshaftTreasure)
                .toList();
        if (valuables.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = valuables.get(
                provider.getRandom().nextInt(valuables.size())).copy();
        result.setCount(1);
        return result;
    }

    private static boolean isValuableMineshaftTreasure(ItemStack stack) {
        return stack.is(Items.DIAMOND)
                || stack.is(Items.GOLDEN_APPLE)
                || stack.is(Items.ENCHANTED_GOLDEN_APPLE)
                || stack.is(Items.ENCHANTED_BOOK)
                || stack.is(Items.NAME_TAG)
                || stack.is(Items.GOLD_INGOT)
                || stack.is(Items.IRON_INGOT);
    }

    private static ItemStack mineralGift(
            ChangedEntity provider,
            GiftTier tier) {
        Item item = CreatureSettlementService.recentProvisionItem(provider)
                .filter(candidate -> candidate != Items.AIR)
                .orElse(Items.COAL);
        if (isRareMineral(item) && tier.rank < GiftTier.CLOSE.rank) {
            item = Items.RAW_IRON;
        }
        int count = isRareMineral(item)
                ? 1 : ordinaryCount(provider, tier);
        return new ItemStack(item, count);
    }

    private static boolean isRareMineral(Item item) {
        return item == Items.DIAMOND
                || item == Items.EMERALD
                || item == Items.RAW_GOLD
                || item == Items.GOLD_INGOT;
    }

    private static ItemStack rememberedGift(
            ChangedEntity provider,
            GiftTier tier,
            Item fallback,
            boolean requireEdible) {
        Item item = CreatureSettlementService.recentProvisionItem(provider)
                .filter(candidate -> candidate != Items.AIR)
                .filter(candidate -> !requireEdible
                        || new ItemStack(candidate).isEdible())
                .orElse(fallback);
        return new ItemStack(item, ordinaryCount(provider, tier));
    }

    private static int ordinaryCount(
            ChangedEntity provider,
            GiftTier tier) {
        return switch (tier) {
            case RESPECTED -> 1;
            case ALLIED -> 1 + provider.getRandom().nextInt(2);
            case FRIEND -> 2;
            case CLOSE -> 2 + provider.getRandom().nextInt(2);
            case BONDED -> 3;
        };
    }

    private static int fishCount(
            ChangedEntity provider,
            GiftTier tier) {
        if (tier.rank < GiftTier.FRIEND.rank) {
            return 1;
        }
        return tier == GiftTier.BONDED
                && provider.getRandom().nextInt(3) == 0 ? 3 : 2;
    }

    private static boolean canPauseForGift(RoutineState routine) {
        return routine == RoutineState.IDLE
                || routine == RoutineState.TENDING
                || routine == RoutineState.PLAYING;
    }

    private static boolean eligiblePlayer(
            ChangedEntity provider,
            ServerPlayer player,
            long now) {
        return player.isAlive()
                && !player.isSpectator()
                && provider.distanceToSqr(player) <= GIFT_RANGE_SQR
                && player.getPersistentData().getLong(NEXT_PLAYER_GIFT) <= now
                && !LatexSocialMemory.isProvoked(provider, player)
                && !LatexSocialMemory.hasHostilityToward(provider, player)
                && !LatexSocialMemory.hasBetrayedPatTruce(provider, player);
    }

    private static void give(
            ChangedEntity provider,
            ServerPlayer player,
            GiftTier tier,
            ItemStack gift,
            long now) {
        ItemStack shownGift = gift.copy();
        player.getInventory().add(gift);
        if (!gift.isEmpty()) {
            player.drop(gift, false);
        }
        provider.swing(InteractionHand.MAIN_HAND);
        if (provider.level() instanceof ServerLevel level) {
            level.sendParticles(
                    new ItemParticleOption(ParticleTypes.ITEM, shownGift),
                    player.getX(),
                    player.getY(0.65D),
                    player.getZ(),
                    Math.min(7, 3 + shownGift.getCount()),
                    0.22D, 0.28D, 0.22D, 0.025D);
            level.playSound(
                    null, player.blockPosition(), SoundEvents.ITEM_PICKUP,
                    SoundSource.PLAYERS, 0.55F,
                    1.05F + provider.getRandom().nextFloat() * 0.12F);
        }
        provider.getPersistentData().putLong(
                NEXT_PROVIDER_GIFT,
                now + PROVIDER_COOLDOWN_MIN
                        + provider.getRandom().nextInt(
                                PROVIDER_COOLDOWN_VARIATION + 1));
        player.getPersistentData().putLong(
                NEXT_PLAYER_GIFT, now + PLAYER_COOLDOWN);
        NpcDialogue.trigger(
                provider, player, tier.cue, shownGift.getHoverName());
    }

    private record Candidate(ServerPlayer player, GiftTier tier) {
    }

    private enum GiftTier {
        RESPECTED(0, Cue.ROLE_PROVISIONER_GIFT_RESPECTED),
        ALLIED(1, Cue.ROLE_PROVISIONER_GIFT_ALLIED),
        FRIEND(2, Cue.ROLE_PROVISIONER_GIFT_FRIEND),
        CLOSE(3, Cue.ROLE_PROVISIONER_GIFT_CLOSE),
        BONDED(4, Cue.ROLE_PROVISIONER_GIFT_BONDED);

        private final int rank;
        private final Cue cue;

        GiftTier(int rank, Cue cue) {
            this.rank = rank;
            this.cue = cue;
        }

        private static GiftTier forPlayer(
                ChangedEntity provider,
                ServerPlayer player) {
            if (LatexSocialMemory.isBonded(provider, player)
                    || LatexSocialMemory.isPetOwner(provider, player)) {
                return BONDED;
            }
            if (CreaturePersonality.hasTrustedRelationship(
                    provider, player)) {
                return CreaturePersonality.relationshipTier(provider, player)
                                == CreaturePersonality.RelationshipTier.CLOSE
                        ? CLOSE : FRIEND;
            }
            return switch (FactionReputation.standing(provider, player)) {
                case ALLIED -> ALLIED;
                case RESPECTED -> RESPECTED;
                default -> null;
            };
        }
    }
}
