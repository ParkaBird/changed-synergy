package net.parkabird.changedsynergy.regression;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.Merchant;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.world.inventory.BondedInventoryService;

/** Real removal commands, native merchant purchases, saved limits and companion inventory. */
final class CampTradeReturnChecks {
    @SuppressWarnings("unchecked")
    static void run(ServerPlayer owner, ChangedEntity original, PlayerOutpostData.Outpost camp) throws Exception {
        var level = owner.serverLevel();
        var data = PlayerOutpostData.get(owner.server);
        var mapField = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(PlayerList.class, "f_11197_"); mapField.setAccessible(true);
        var players = (Map<UUID, ServerPlayer>)mapField.get(owner.server.getPlayerList());
        var connection = owner.connection;
        var position = owner.position();
        var provider = (ChangedEntity)original.getType().create(level);
        boolean trading = ChangedSynergyConfig.COMMON.provisionerTrading.get();
        boolean life = level.getGameRules().getBoolean(ChangedSynergyGameRules.CREATURE_LIFE);
        try {
            owner.connection = new ServerGamePacketListenerImpl(owner.server, new Connection(PacketFlow.SERVERBOUND), owner);
            players.put(owner.getUUID(), owner);
            owner.setPos(0.5, 90, 6.5);
            provider.setPos(1.5, 90, 6.5); level.addFreshEntity(provider);
            CreaturePersonality.establishRelationship(provider, owner);
            CreaturePersonality.setFamiliarity(provider, owner, 60);
            CreatureLifeMemory.setRole(provider, CreatureLifeMemory.GroupRole.PROVISIONER);
            ChangedSynergyConfig.COMMON.provisionerTrading.set(true);
            check(ProvisionerTradeService.canOpen(provider, owner), "Wild provisioner can trade before recruitment");
            recruit(data, camp, provider, owner);
            check(!ProvisionerTradeService.canOpen(provider, owner), "Camp assignment replaces the original provisioner work");
            remove(owner, provider);
            check(ProvisionerTradeService.canOpen(provider, owner), "Removing an ordinary friend restores trading");

            LatexSocialMemory.addBond(provider, owner);
            check(LatexSocialMemory.isBonded(provider, owner), "Use a real adult companion bond");
            recruit(data, camp, provider, owner);
            remove(owner, provider);
            check(ProvisionerTradeService.canOpen(provider, owner), "Removing a bonded provisioner restores trading");
            var inventory = BondedInventoryService.inventory(provider, owner);
            inventory.clearContent();
            var first = ProvisionerTradeService.offers(provider, owner).get(0);
            owner.getInventory().clearContent();
            owner.getInventory().setItem(0, first.payment().copy());
            check(ProvisionerTradeService.trade(provider, owner, 0) == ProvisionerTradeService.TradeResult.SUCCESS,
                    "Direct purchase succeeds after removal");
            check(count(inventory, first.payment()) == first.payment().getCount()
                            && !CreatureSettlementService.hasTradeReturns(provider),
                    "Owned payments go to real storage instead of a stranded wild-cache delivery");

            var merchantType = Class.forName(ProvisionerTradeService.class.getName() + "$SynergyMerchant");
            var constructor = merchantType.getDeclaredConstructor(ChangedEntity.class, ServerPlayer.class);
            constructor.setAccessible(true);
            Merchant merchant = (Merchant)constructor.newInstance(provider, owner);
            try {
                var offer = merchant.getOffers().get(0);
                int limit = offer.getMaxUses();
                for (int purchase = 0; purchase < limit; purchase++) {
                    check(offer.take(offer.getCostA().copy(), ItemStack.EMPTY), "Native merchant consumes real payment");
                    merchant.notifyTrade(offer);
                }
            } finally { merchant.setTradingPlayer(null); }
            check(ProvisionerTradeService.offers(provider, owner).stream().noneMatch(offer ->
                            offer.enabled() && offer.kind() == ProvisionerTradeService.OfferKind.MATERIAL),
                    "Native purchases exhaust the same saved daily limit");
            recruit(data, camp, provider, owner); remove(owner, provider);
            check(ProvisionerTradeService.offers(provider, owner).stream().noneMatch(offer ->
                            offer.enabled() && offer.kind() == ProvisionerTradeService.OfferKind.MATERIAL),
                    "Recruiting and removing again does not refresh exhausted offers");
            check(!provider.getPersistentData().hasUUID("ChangedSynergyCommunityId")
                            && CreatureCommunityData.snapshot(provider).isEmpty(),
                    "Trading never enrolls the bonded creature in a wild community");
            check(ProvisionerTradeService.offers(provider, owner).stream().noneMatch(offer ->
                            offer.enabled() && offer.kind() == ProvisionerTradeService.OfferKind.BUYBACK),
                    "Owned trader cannot sell another community's reserved stock");

            ChangedSynergyConfig.COMMON.provisionerTrading.set(false);
            check(!ProvisionerTradeService.canOpen(provider, owner), "Trading setting remains authoritative");
            ChangedSynergyConfig.COMMON.provisionerTrading.set(true);
            level.getGameRules().getRule(ChangedSynergyGameRules.CREATURE_LIFE).set(false, owner.server);
            check(!ProvisionerTradeService.canOpen(provider, owner), "Creature-life rule still pauses trading");
            level.getGameRules().getRule(ChangedSynergyGameRules.CREATURE_LIFE).set(true, owner.server);

            for (int slot = 0; slot < 24; slot++) inventory.setItem(slot, new ItemStack(Items.STONE, 64));
            var overflow = ProvisionerTradeService.offers(provider, owner).get(0);
            int dropped = dropped(level, provider, overflow.payment());
            owner.getInventory().setItem(0, overflow.payment().copy());
            check(ProvisionerTradeService.trade(provider, owner, 0) == ProvisionerTradeService.TradeResult.SUCCESS
                            && dropped(level, provider, overflow.payment()) - dropped == overflow.payment().getCount(),
                    "Full personal storage drops the exact payment once");
            recruit(data, camp, provider, owner);
            CompoundTag saved = provider.saveWithoutId(new CompoundTag());
            check(data.remove(camp, provider.getUUID()), "Remove an unloaded member's saved assignment");
            LatexSocialMemory.unregisterBond(provider, owner);
            provider.discard();
            provider = (ChangedEntity)original.getType().create(level); provider.load(saved); level.addFreshEntity(provider);
            check(ProvisionerTradeService.canOpen(provider, owner) && !PlayerOutpostService.assigned(provider),
                    "Reload clears an obsolete camp assignment and restores trading");
            check(ProvisionerTradeService.offers(provider, owner).stream().noneMatch(offer ->
                            offer.enabled() && offer.kind() == ProvisionerTradeService.OfferKind.MATERIAL),
                    "Entity reload preserves exhausted trades");
            recruit(data, camp, provider, owner);
            check(owner.server.getCommands().getDispatcher().execute("changedsynergy outpost disband",
                    owner.createCommandSourceStack()) == 1 && ProvisionerTradeService.canOpen(provider, owner),
                    "Disbanding also restores the original provisioner profession");
            CreatureLifeMemory.setRole(provider, CreatureLifeMemory.GroupRole.SCOUT);
            check(!ProvisionerTradeService.canOpen(provider, owner), "Non-provisioners do not become merchants on dismissal");
            System.out.println("CAMP_TRADE_RETURN_REGRESSION_PASS");
        } finally {
            ChangedSynergyConfig.COMMON.provisionerTrading.set(trading);
            level.getGameRules().getRule(ChangedSynergyGameRules.CREATURE_LIFE).set(life, owner.server);
            LatexSocialMemory.unregisterBond(provider, owner);
            players.remove(owner.getUUID(), owner);
            owner.connection = connection; owner.setPos(position);
            provider.discard();
        }
    }

    private static void recruit(PlayerOutpostData data, PlayerOutpostData.Outpost camp, ChangedEntity provider, ServerPlayer owner) {
        check(data.add(camp, provider.getUUID(), "Trade check", camp.dimension, provider.blockPosition(), PlayerOutpostSupplyType.ORANGE), "Recruit provider");
        CreatureCommunityData.detach(provider);
        PlayerOutpostService.applyRole(provider, owner, PlayerOutpostData.Role.SUPPLY);
    }
    private static void remove(ServerPlayer owner, ChangedEntity provider) throws Exception {
        check(owner.server.getCommands().getDispatcher().execute("changedsynergy outpost remove " + provider.getUUID(),
                owner.createCommandSourceStack()) == 1, "Real camp dismissal command succeeds");
    }
    private static int count(Container inventory, ItemStack sample) {
        int count = 0;
        for (int slot = 0; slot < 24; slot++) if (ItemStack.isSameItemSameTags(inventory.getItem(slot), sample)) count += inventory.getItem(slot).getCount();
        return count;
    }
    private static int dropped(net.minecraft.server.level.ServerLevel level, ChangedEntity provider, ItemStack sample) {
        return level.getEntitiesOfClass(ItemEntity.class, provider.getBoundingBox().inflate(3),
                item -> ItemStack.isSameItemSameTags(item.getItem(), sample)).stream().mapToInt(item -> item.getItem().getCount()).sum();
    }
    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
        System.out.println("CAMP_TRADE_RETURN_PASS: " + message);
    }
}
