package net.parkabird.changedsynergy.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.CreatureLifeMemory.GroupRole;
import net.parkabird.changedsynergy.ai.CreaturePersonality.RelationshipTier;
import net.parkabird.changedsynergy.ai.CreatureSettlementService.ProvisionSource;

/** Species-aware barter using Minecraft's complete merchant interaction. */
public final class ProvisionerTradeService {
    private static final String PROFILE = "ChangedSynergyProvisionerTrade";
    private static final String USE_DAY = "UseDay";
    private static final String USES = "Uses";
    private static final double TRADE_DISTANCE_SQR = 8.0D * 8.0D;
    private static final Set<UUID> ACTIVE_TRADERS = new HashSet<>();

    public enum OfferKind {
        MATERIAL,
        SPECIALTY,
        BUYBACK,
        COLLECTIBLE
    }

    public enum TradeResult {
        SUCCESS("message.changed_synergy.trade.success"),
        UNAVAILABLE("message.changed_synergy.trade.unavailable"),
        BUSY("message.changed_synergy.trade.busy"),
        OUT_OF_STOCK("message.changed_synergy.trade.out_of_stock"),
        RESERVE("message.changed_synergy.trade.reserve"),
        NEED_PAYMENT("message.changed_synergy.trade.need_payment"),
        INVENTORY_FULL("message.changed_synergy.trade.inventory_full"),
        CACHE_FULL("message.changed_synergy.trade.cache_full");

        private final String key;

        TradeResult(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    /** Kept as the small wire model used by older saved screens. */
    public record TradeOffer(
            ItemStack payment,
            ItemStack result,
            OfferKind kind,
            int maxTrades,
            boolean enabled) {
        public TradeOffer {
            payment = payment.copy();
            result = result.copy();
            maxTrades = Math.max(0, maxTrades);
        }

        public static TradeOffer empty(OfferKind kind) {
            return new TradeOffer(
                    ItemStack.EMPTY, ItemStack.EMPTY, kind, 0, false);
        }
    }

    private record Template(
            ItemStack payment,
            ItemStack result,
            OfferKind kind,
            int maxTrades,
            boolean stockBound) {
    }

    private ProvisionerTradeService() {
    }

    public static boolean canOpen(ChangedEntity provider, ServerPlayer player) {
        return ChangedSynergyConfig.COMMON.provisionerTrading.get()
                && provider.isAlive() && !provider.isRemoved()
                && CreatureSocialProfile.allowsSocialWheel(provider)
                && provider.level() == player.level()
                && !ACTIVE_TRADERS.contains(provider.getUUID())
                && player.distanceToSqr(provider) <= TRADE_DISTANCE_SQR
                && CreatureLifeMemory.role(provider) == GroupRole.PROVISIONER
                && CreatureCommunityData.snapshot(provider).isPresent()
                && !LatexSocialMemory.isProvoked(provider, player)
                && CreaturePersonality.relationshipTier(provider, player)
                        != RelationshipTier.STRAINED;
    }

    public static boolean isBusy(ChangedEntity provider) {
        return provider.getTarget() != null && provider.getTarget().isAlive()
                || provider.getLastHurtByMob() != null
                        && provider.tickCount
                                - provider.getLastHurtByMobTimestamp() <= 100;
    }

    public static boolean isTrading(ChangedEntity provider) {
        return ACTIVE_TRADERS.contains(provider.getUUID());
    }

    public static void holdStillWhileTrading(ChangedEntity provider) {
        if (!isTrading(provider)) {
            return;
        }
        provider.getNavigation().stop();
        provider.setDeltaMovement(
                0.0D, provider.getDeltaMovement().y, 0.0D);
    }

    public static void open(ServerPlayer player, ChangedEntity provider) {
        if (!canOpen(provider, player) || isBusy(provider)) {
            player.displayClientMessage(Component.translatable(
                    isBusy(provider)
                            ? TradeResult.BUSY.key()
                            : TradeResult.UNAVAILABLE.key()), true);
            return;
        }
        SynergyMerchant merchant = new SynergyMerchant(provider, player);
        if (merchant.getOffers().isEmpty()) {
            merchant.setTradingPlayer(null);
            player.displayClientMessage(Component.translatable(
                    "message.changed_synergy.trade.no_surplus",
                    provider.getDisplayName()), false);
            return;
        }
        merchant.openTradingScreen(
                player,
                Component.translatable(
                        "menu.changed_synergy.trade.title",
                        provider.getDisplayName()),
                0);
    }

    public static void recordDelivery(
            ChangedEntity provider,
            ItemStack delivered,
            ProvisionSource source) {
        // Trade selection is species/region based. Community stock revisions
        // already make newly returned currency visible on the next opening.
    }

    public static void copyProfile(
            ChangedEntity source,
            ChangedEntity target) {
        CompoundTag persistent = source.getPersistentData();
        if (persistent.contains(PROFILE, Tag.TAG_COMPOUND)) {
            target.getPersistentData().put(
                    PROFILE, persistent.getCompound(PROFILE).copy());
        }
    }

    public static List<TradeOffer> offers(
            ChangedEntity provider,
            ServerPlayer player) {
        List<TradeOffer> result = new ArrayList<>();
        for (Template template : templates(provider, player)) {
            result.add(new TradeOffer(
                    template.payment(), template.result(), template.kind(),
                    template.maxTrades(), template.maxTrades() > 0));
        }
        while (result.size() < 4) {
            result.add(TradeOffer.empty(
                    OfferKind.values()[result.size()]));
        }
        return List.copyOf(result.subList(0, 4));
    }

    /** Compatibility path for the retired button-only screen. */
    public static TradeResult trade(
            ChangedEntity provider,
            ServerPlayer player,
            int offerIndex) {
        if (!canOpen(provider, player) || isBusy(provider)) {
            return TradeResult.UNAVAILABLE;
        }
        List<Template> current = templates(provider, player);
        if (offerIndex < 0 || offerIndex >= current.size()) {
            return TradeResult.UNAVAILABLE;
        }
        Template template = current.get(offerIndex);
        if (countPlayerItem(player, template.payment())
                < template.payment().getCount()) {
            return TradeResult.NEED_PAYMENT;
        }
        ItemStack withdrawn = template.stockBound()
                ? withdrawStock(provider, template.result())
                : template.result().copy();
        if (withdrawn.isEmpty()) {
            return TradeResult.OUT_OF_STOCK;
        }
        if (!removePlayerItem(player, template.payment())) {
            if (template.stockBound()) {
                returnStock(provider, withdrawn);
            }
            return TradeResult.NEED_PAYMENT;
        }
        player.getInventory().placeItemBackInInventory(withdrawn);
        CreatureSettlementService.queueTradeReturn(
                provider, template.payment());
        if (!template.stockBound()) {
            recordUse(provider, template.kind());
        }
        return TradeResult.SUCCESS;
    }

    private static List<Template> templates(
            ChangedEntity provider,
            ServerPlayer player) {
        List<Template> templates = new ArrayList<>(4);
        Item currency = preferredCurrency(provider);
        RelationshipTier tier = CreaturePersonality.relationshipTier(
                provider, player);
        double discount = tier == RelationshipTier.CLOSE ? 0.88D
                : tier == RelationshipTier.FAMILIAR ? 0.94D : 1.0D;

        Item material = select(provider, ordinaryMaterials(provider), 11);
        int materialCount = material == Items.IRON_INGOT ? 2 : 4;
        templates.add(virtualOffer(
                provider, currency, material, materialCount,
                OfferKind.MATERIAL, 8, discount));

        Item specialty = select(provider, specialtyMaterials(provider), 29);
        int specialtyCount = specialty == Items.NAUTILUS_SHELL
                || specialty == Items.DIAMOND ? 1 : 3;
        templates.add(virtualOffer(
                provider, currency, specialty, specialtyCount,
                OfferKind.SPECIALTY, 4, discount));

        Item stockedCurrency = currencyCandidates(provider).stream()
                .filter(item -> availableUnits(
                        provider, new ItemStack(item)) > 0)
                .findFirst()
                .orElse(currency);
        Item wanted = select(provider, wantedGoods(provider), 47);
        int currencyAvailable = availableUnits(
                provider, new ItemStack(stockedCurrency));
        int paymentCount = Mth.clamp((int)Math.ceil(
                itemValue(new ItemStack(stockedCurrency))
                        / (double)Math.max(1, itemValue(new ItemStack(wanted)))),
                1, 16);
        templates.add(new Template(
                new ItemStack(wanted, paymentCount),
                new ItemStack(stockedCurrency),
                OfferKind.BUYBACK,
                currencyAvailable,
                true));

        if (tier == RelationshipTier.FAMILIAR
                || tier == RelationshipTier.CLOSE) {
            Item collectible = select(provider, collectibles(), 83);
            int price = Mth.clamp((int)Math.ceil(
                    Math.max(48, itemValue(new ItemStack(collectible)) * 6)
                            / (double)Math.max(1,
                                    itemValue(new ItemStack(currency)))
                            * discount), 4, 64);
            templates.add(new Template(
                    new ItemStack(currency, price),
                    new ItemStack(collectible),
                    OfferKind.COLLECTIBLE,
                    remainingUses(provider, OfferKind.COLLECTIBLE, 1),
                    false));
        }
        return templates.stream()
                .filter(template -> !template.payment().isEmpty()
                        && !template.result().isEmpty()
                        && template.maxTrades() > 0)
                .toList();
    }

    private static Template virtualOffer(
            ChangedEntity provider,
            Item currency,
            Item result,
            int resultCount,
            OfferKind kind,
            int dailyLimit,
            double discount) {
        int price = Mth.clamp((int)Math.ceil(
                itemValue(new ItemStack(result)) * resultCount
                        / (double)Math.max(1,
                                itemValue(new ItemStack(currency)))
                        * discount), 1, 64);
        return new Template(
                new ItemStack(currency, price),
                new ItemStack(result, resultCount),
                kind,
                remainingUses(provider, kind, dailyLimit),
                false);
    }

    private static List<Item> currencyCandidates(ChangedEntity provider) {
        List<Item> result = new ArrayList<>();
        List<Item> foods = List.of(
                Items.COD, Items.SALMON, Items.TROPICAL_FISH,
                Items.SWEET_BERRIES, Items.GLOW_BERRIES);
        if (HunterArchetype.of(provider) == HunterArchetype.AQUATIC) {
            addDistinct(result, Items.COD);
            addDistinct(result, Items.SALMON);
        }
        if (CreatureSettlementService.isTaigaCommunity(provider)) {
            addDistinct(result, Items.SWEET_BERRIES);
        }
        if (CreatureSettlementService.isCaveCommunity(provider)) {
            addDistinct(result, Items.GLOW_BERRIES);
        }
        for (Item food : foods) {
            if (RelationshipFavorService.isDedicatedDietFood(
                    provider, new ItemStack(food))) {
                addDistinct(result, food);
            }
        }
        Item orange = item("changed:orange", Items.SWEET_BERRIES);
        if (RelationshipFavorService.acceptsOrange(provider)) {
            addDistinct(result, orange);
        }
        if (result.isEmpty()) {
            addDistinct(result,
                    HunterArchetype.of(provider) == HunterArchetype.FELINE
                            ? Items.SWEET_BERRIES : orange);
        }
        return List.copyOf(result);
    }

    private static Item preferredCurrency(ChangedEntity provider) {
        return currencyCandidates(provider).get(0);
    }

    private static List<Item> ordinaryMaterials(ChangedEntity provider) {
        if (HunterArchetype.of(provider) == HunterArchetype.AQUATIC) {
            return List.of(Items.PRISMARINE_SHARD, Items.STRING,
                    Items.GLASS, Items.IRON_NUGGET);
        }
        if (CreatureSettlementService.isCaveCommunity(provider)) {
            return List.of(Items.TORCH, Items.COAL, Items.RAW_IRON,
                    Items.REDSTONE);
        }
        if (CreatureSettlementService.isTaigaCommunity(provider)) {
            return List.of(Items.SPRUCE_LOG, Items.LEATHER,
                    Items.STRING, Items.COAL);
        }
        return List.of(Items.IRON_INGOT, Items.COAL,
                Items.GLASS, Items.REDSTONE);
    }

    private static List<Item> specialtyMaterials(ChangedEntity provider) {
        if (HunterArchetype.of(provider) == HunterArchetype.AQUATIC) {
            return List.of(Items.PRISMARINE_CRYSTALS,
                    Items.PRISMARINE_SHARD, Items.NAUTILUS_SHELL);
        }
        if (HunterFaction.of(provider) == HunterFaction.DARK) {
            return List.of(
                    item("changed:dark_latex_crystal_fragment", Items.QUARTZ),
                    Items.OBSIDIAN, Items.QUARTZ);
        }
        if (HunterFaction.of(provider) == HunterFaction.WHITE) {
            return List.of(
                    item("changed:white_latex_goo", Items.QUARTZ),
                    Items.QUARTZ, Items.GLASS);
        }
        if (CreatureSettlementService.isBadlandsCommunity(provider)) {
            return List.of(Items.GOLD_INGOT, Items.RED_TERRACOTTA,
                    Items.COPPER_INGOT);
        }
        if (CreatureSettlementService.isDesertCommunity(provider)) {
            return List.of(Items.GOLD_INGOT, Items.SANDSTONE, Items.CACTUS);
        }
        if (CreatureSettlementService.isSnowyCommunity(provider)) {
            return List.of(Items.PACKED_ICE, Items.LEATHER, Items.IRON_INGOT);
        }
        if (HunterArchetype.of(provider) == HunterArchetype.INSECT) {
            return List.of(Items.HONEYCOMB, Items.HONEY_BOTTLE, Items.STRING);
        }
        return List.of(Items.LAPIS_LAZULI, Items.GOLD_INGOT,
                Items.ENDER_PEARL);
    }

    private static List<Item> wantedGoods(ChangedEntity provider) {
        if (HunterArchetype.of(provider) == HunterArchetype.AQUATIC) {
            return List.of(Items.STRING, Items.OAK_LOG,
                    Items.GLASS_BOTTLE, Items.COAL);
        }
        if (CreatureSettlementService.isCaveCommunity(provider)) {
            return List.of(Items.OAK_LOG, Items.BREAD,
                    Items.TORCH, Items.BONE_MEAL);
        }
        if (CreatureSettlementService.isTaigaCommunity(provider)) {
            return List.of(Items.BONE_MEAL, Items.COAL,
                    Items.IRON_INGOT, Items.STRING);
        }
        return List.of(Items.OAK_LOG, Items.BONE_MEAL,
                Items.GLASS_BOTTLE, Items.COAL);
    }

    /** Items drawn from Changed facility and decayed-lab chest pools. */
    private static List<Item> collectibles() {
        return List.of(
                item("changed:outside_the_tower_record", Items.MUSIC_DISC_13),
                item("changed:laboratory_record", Items.MUSIC_DISC_CAT),
                item("changed:wolf_gas_canister", Items.NAME_TAG),
                item("changed:tsc_baton", Items.DIAMOND),
                item("changed:latex_syringe", Items.DIAMOND),
                item("changed:pink_shorts", Items.DIAMOND),
                Items.DIAMOND);
    }

    private static Item select(
            ChangedEntity provider, List<Item> items, int salt) {
        long day = provider.level().getDayTime() / 24000L;
        long mixed = provider.getUUID().getMostSignificantBits()
                ^ Long.rotateLeft(
                        provider.getUUID().getLeastSignificantBits(), 17)
                ^ day * 31L ^ salt;
        return items.get(Math.floorMod((int)(mixed ^ mixed >>> 32),
                items.size()));
    }

    private static void addDistinct(List<Item> items, Item candidate) {
        if (!items.contains(candidate)) {
            items.add(candidate);
        }
    }

    private static Item item(String id, Item fallback) {
        Item value = ForgeRegistries.ITEMS.getValue(
                net.minecraft.resources.ResourceLocation.tryParse(id));
        return value == null || value == Items.AIR ? fallback : value;
    }

    private static int remainingUses(
            ChangedEntity provider, OfferKind kind, int limit) {
        CompoundTag profile = dailyProfile(provider);
        return Math.max(0,
                limit - profile.getCompound(USES).getInt(kind.name()));
    }

    private static void recordUse(
            ChangedEntity provider, OfferKind kind) {
        CompoundTag profile = dailyProfile(provider);
        CompoundTag uses = profile.getCompound(USES);
        uses.putInt(kind.name(), uses.getInt(kind.name()) + 1);
        profile.put(USES, uses);
    }

    private static CompoundTag dailyProfile(ChangedEntity provider) {
        CompoundTag persistent = provider.getPersistentData();
        if (!persistent.contains(PROFILE, Tag.TAG_COMPOUND)) {
            persistent.put(PROFILE, new CompoundTag());
        }
        CompoundTag profile = persistent.getCompound(PROFILE);
        long day = provider.level().getDayTime() / 24000L;
        if (profile.getLong(USE_DAY) != day) {
            profile.putLong(USE_DAY, day);
            profile.put(USES, new CompoundTag());
        } else if (!profile.contains(USES, Tag.TAG_COMPOUND)) {
            profile.put(USES, new CompoundTag());
        }
        return profile;
    }

    private static int availableUnits(
            ChangedEntity provider,
            ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        int credit = CreatureCommunityData.tradeCredit(provider, stack);
        int physical;
        if (isConsensus(provider)) {
            physical = credit;
        } else {
            physical = CreatureSettlementService.storedCount(provider, stack);
            credit = CreatureCommunityData.capTradeCredit(
                    provider, stack, physical);
        }
        int raw = Math.min(credit, physical);
        if (raw <= 0) {
            return 0;
        }
        boolean food = isFood(stack);
        int total = CreatureCommunityData.tradeStock(provider).stream()
                .filter(entry -> isFood(entry.stack()) == food)
                .mapToInt(entry -> Math.min(
                        entry.count(), isConsensus(provider)
                                ? entry.count()
                                : CreatureSettlementService.storedCount(
                                        provider, entry.stack())))
                .sum();
        double reserveRatio = 0.70D * ChangedSynergyConfig.COMMON
                .provisionerTradeReserveMultiplier.get();
        if (food) {
            reserveRatio += Math.min(
                    0.20D, injuredCommunityMembers(provider) * 0.05D);
        }
        int reserve = total < 2 ? total : Math.min(
                total - 1,
                (int)Math.ceil(total * Mth.clamp(
                        reserveRatio, 0.0D, 0.95D)));
        int reserveFromStack = Math.max(0, reserve - (total - raw));
        return Math.max(0, raw - reserveFromStack);
    }

    private static int injuredCommunityMembers(ChangedEntity provider) {
        if (!(provider.level() instanceof ServerLevel level)) {
            return 0;
        }
        return (int)level.getEntitiesOfClass(
                        ChangedEntity.class,
                        new AABB(provider.blockPosition()).inflate(32.0D),
                        other -> other.isAlive()
                                && other.getHealth() < other.getMaxHealth()
                                && CreatureCommunityData.sameCommunity(
                                        provider, other))
                .stream().limit(8).count();
    }

    private static ItemStack withdrawStock(
            ChangedEntity provider, ItemStack result) {
        if (availableUnits(provider, result) < result.getCount()
                || !CreatureCommunityData.consumeTradeCredit(
                        provider, result, result.getCount())) {
            return ItemStack.EMPTY;
        }
        if (isConsensus(provider)) {
            return result.copy();
        }
        ItemStack withdrawn = CreatureSettlementService.extractStored(
                provider, result, result.getCount());
        if (withdrawn.isEmpty()) {
            CreatureCommunityData.restoreTradeCredit(
                    provider, result, result.getCount());
        }
        return withdrawn;
    }

    private static void returnStock(
            ChangedEntity provider, ItemStack stack) {
        if (!isConsensus(provider)) {
            CreatureSettlementService.storeCommunityPayment(provider, stack);
        }
        CreatureCommunityData.restoreTradeCredit(
                provider, stack, stack.getCount());
    }

    private static boolean isConsensus(ChangedEntity provider) {
        return HunterFaction.of(provider) == HunterFaction.WHITE
                && !CreatureSettlementService.isFacilityCommunity(provider)
                && CreatureSettlementService.communityContainer(provider) == null;
    }

    private static boolean isFood(ItemStack stack) {
        return stack.isEdible() || RelationshipFavorService.isOrange(stack);
    }

    private static int itemValue(ItemStack stack) {
        if (stack.is(Items.DIAMOND) || stack.is(Items.ENCHANTED_BOOK)) {
            return 24;
        }
        if (stack.is(Items.NAUTILUS_SHELL)) {
            return 12;
        }
        if (stack.is(Items.GOLD_INGOT) || stack.is(Items.RAW_GOLD)) {
            return 8;
        }
        if (stack.is(Items.IRON_INGOT) || stack.is(Items.RAW_IRON)
                || stack.is(Items.ENDER_PEARL)) {
            return 4;
        }
        if (stack.is(Items.COPPER_INGOT) || stack.is(Items.RAW_COPPER)
                || stack.is(Items.COAL) || stack.is(Items.STRING)
                || stack.is(Items.PRISMARINE_CRYSTALS)) {
            return 2;
        }
        if (RelationshipFavorService.isOrange(stack)) {
            return RelationshipFavorService.isGoldenOrange(stack) ? 12 : 4;
        }
        if (stack.isEdible() && stack.getFoodProperties(null) != null) {
            return Math.max(1,
                    stack.getFoodProperties(null).getNutrition() / 2);
        }
        return stack.getRarity() == Rarity.UNCOMMON ? 3
                : stack.getRarity() == Rarity.RARE ? 8
                : stack.getRarity() == Rarity.EPIC ? 16 : 1;
    }

    private static int countPlayerItem(
            ServerPlayer player, ItemStack sample) {
        return player.getInventory().items.stream()
                .filter(stack -> ItemStack.isSameItemSameTags(stack, sample))
                .mapToInt(ItemStack::getCount).sum();
    }

    private static boolean removePlayerItem(
            ServerPlayer player, ItemStack payment) {
        int remaining = payment.getCount();
        for (ItemStack stack : player.getInventory().items) {
            if (remaining <= 0) {
                break;
            }
            if (ItemStack.isSameItemSameTags(stack, payment)) {
                int removed = Math.min(remaining, stack.getCount());
                stack.shrink(removed);
                remaining -= removed;
            }
        }
        player.getInventory().setChanged();
        return remaining == 0;
    }

    private static final class SynergyMerchant implements Merchant {
        private final ChangedEntity provider;
        private final ServerPlayer customer;
        private final List<Template> templates;
        private final int[] recordedUses;
        private MerchantOffers offers;
        @Nullable
        private Player tradingPlayer;

        private SynergyMerchant(
                ChangedEntity provider, ServerPlayer customer) {
            this.provider = provider;
            this.customer = customer;
            this.tradingPlayer = customer;
            this.templates = templates(provider, customer);
            this.recordedUses = new int[templates.size()];
            this.offers = merchantOffers(provider, templates);
            ACTIVE_TRADERS.add(provider.getUUID());
            provider.getNavigation().stop();
        }

        @Override
        public void setTradingPlayer(@Nullable Player player) {
            if (player == null) {
                syncUses();
            }
            tradingPlayer = player;
            if (player == null) {
                ACTIVE_TRADERS.remove(provider.getUUID());
                CreatureLifeMemory.scheduleNextDecision(
                        provider, provider.level().getGameTime() + 20L);
            } else {
                ACTIVE_TRADERS.add(provider.getUUID());
            }
        }

        @Nullable
        @Override
        public Player getTradingPlayer() {
            return tradingPlayer;
        }

        @Override
        public MerchantOffers getOffers() {
            return offers;
        }

        @Override
        public void overrideOffers(MerchantOffers offers) {
            syncUses();
            this.offers = offers;
            java.util.Arrays.fill(recordedUses, 0);
        }

        @Override
        public void notifyTrade(MerchantOffer offer) {
            offer.increaseUses();
            syncUses();
            provider.getLookControl().setLookAt(customer, 30.0F, 30.0F);
            CreatureLifeMemory.scheduleNextDecision(
                    provider, provider.level().getGameTime() + 10L);
        }

        @Override
        public void notifyTradeUpdated(ItemStack stack) {
        }

        @Override
        public int getVillagerXp() {
            return 0;
        }

        @Override
        public void overrideXp(int xp) {
        }

        @Override
        public boolean showProgressBar() {
            return false;
        }

        @Override
        public SoundEvent getNotifyTradeSound() {
            return SoundEvents.VILLAGER_YES;
        }

        @Override
        public boolean isClientSide() {
            return false;
        }

        private void syncUses() {
            for (int index = 0; index < Math.min(templates.size(), offers.size()); index++) {
                Template template = templates.get(index);
                int uses = offers.get(index).getUses();
                if (!template.stockBound()) {
                    for (int count = recordedUses[index]; count < uses; count++) {
                        recordUse(provider, template.kind());
                    }
                }
                recordedUses[index] = uses;
            }
        }
    }

    private static MerchantOffers merchantOffers(
            ChangedEntity provider, List<Template> templates) {
        MerchantOffers offers = new MerchantOffers();
        for (Template template : templates) {
            offers.add(new SettlementMerchantOffer(provider, template));
        }
        return offers;
    }

    private static final class SettlementMerchantOffer extends MerchantOffer {
        private final ChangedEntity provider;
        private final Template template;

        private SettlementMerchantOffer(
                ChangedEntity provider, Template template) {
            super(template.payment(), template.result(),
                    template.maxTrades(), 0, 0.0F);
            this.provider = provider;
            this.template = template;
        }

        @Override
        public boolean take(ItemStack first, ItemStack second) {
            if (!satisfiedBy(first, second)
                    || !provider.isAlive()
                    || isBusy(provider)) {
                return false;
            }
            ItemStack withdrawn = template.stockBound()
                    ? withdrawStock(provider, template.result())
                    : template.result().copy();
            if (withdrawn.isEmpty()) {
                return false;
            }
            if (!super.take(first, second)) {
                if (template.stockBound()) {
                    returnStock(provider, withdrawn);
                }
                return false;
            }
            CreatureSettlementService.queueTradeReturn(
                    provider, template.payment());
            return true;
        }
    }
}
