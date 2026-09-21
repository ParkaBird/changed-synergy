package net.parkabird.changedsynergy.world.inventory;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import net.parkabird.changedsynergy.ai.ProvisionerTradeService;
import net.parkabird.changedsynergy.ai.ProvisionerTradeService.OfferKind;
import net.parkabird.changedsynergy.ai.ProvisionerTradeService.TradeOffer;
import net.parkabird.changedsynergy.ai.ProvisionerTradeService.TradeResult;
import net.parkabird.changedsynergy.init.ChangedSynergyMenus;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.ProvisionerTradeSyncPacket;

public final class ProvisionerTradeMenu extends AbstractContainerMenu {
    private final Player player;
    @Nullable
    private final ChangedEntity provider;
    private List<TradeOffer> offers;
    private String statusKey = "";
    private int syncSerial;

    public ProvisionerTradeMenu(
            int id,
            Inventory inventory,
            ChangedEntity provider,
            List<TradeOffer> offers) {
        super(ChangedSynergyMenus.PROVISIONER_TRADE.get(), id);
        this.player = inventory.player;
        this.provider = provider;
        this.offers = normalizeOffers(offers);
        addPlayerInventory(inventory);
    }

    public ProvisionerTradeMenu(
            int id,
            Inventory inventory,
            FriendlyByteBuf buffer) {
        super(ChangedSynergyMenus.PROVISIONER_TRADE.get(), id);
        this.player = inventory.player;
        Entity entity = inventory.player.level().getEntity(buffer.readVarInt());
        this.provider = entity instanceof ChangedEntity changed ? changed : null;
        this.offers = readOffers(buffer);
        addPlayerInventory(inventory);
    }

    public static void writeOpeningData(
            FriendlyByteBuf buffer,
            ChangedEntity provider,
            List<TradeOffer> offers) {
        buffer.writeVarInt(provider.getId());
        writeOffers(buffer, offers);
    }

    public List<TradeOffer> getOffers() {
        return offers;
    }

    @Nullable
    public ChangedEntity getProvider() {
        return provider;
    }

    public String getStatusKey() {
        return statusKey;
    }

    public int getSyncSerial() {
        return syncSerial;
    }

    public void applySync(List<TradeOffer> offers, String statusKey) {
        this.offers = normalizeOffers(offers);
        this.statusKey = statusKey == null ? "" : statusKey;
        syncSerial++;
    }

    @Override
    public boolean clickMenuButton(Player viewer, int buttonId) {
        if (!(viewer instanceof ServerPlayer serverPlayer)
                || provider == null || buttonId < 0 || buttonId >= 4) {
            return false;
        }
        TradeResult result = ProvisionerTradeService.trade(
                provider, serverPlayer, buttonId);
        offers = ProvisionerTradeService.offers(provider, serverPlayer);
        statusKey = result.key();
        serverPlayer.displayClientMessage(
                net.minecraft.network.chat.Component.translatable(statusKey),
                true);
        ChangedSynergyNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> serverPlayer),
                new ProvisionerTradeSyncPacket(
                        containerId, offers, statusKey));
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        if (slot < 0 || slot >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot source = slots.get(slot);
        if (!source.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = source.getItem();
        ItemStack original = stack.copy();
        if (slot < 27) {
            if (!moveItemStackTo(stack, 27, 36, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, 27, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            source.set(ItemStack.EMPTY);
        } else {
            source.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        source.onTake(player, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player viewer) {
        return provider != null
                && (!(viewer instanceof ServerPlayer serverPlayer)
                        || ProvisionerTradeService.canOpen(
                                provider, serverPlayer));
    }

    public static void writeOffers(
            FriendlyByteBuf buffer,
            List<TradeOffer> offers) {
        List<TradeOffer> normalized = normalizeOffers(offers);
        buffer.writeVarInt(normalized.size());
        for (TradeOffer offer : normalized) {
            buffer.writeItem(offer.payment());
            buffer.writeItem(offer.result());
            buffer.writeVarInt(offer.kind().ordinal());
            buffer.writeVarInt(offer.maxTrades());
            buffer.writeBoolean(offer.enabled());
        }
    }

    public static List<TradeOffer> readOffers(FriendlyByteBuf buffer) {
        int count = Math.min(4, Math.max(0, buffer.readVarInt()));
        List<TradeOffer> offers = new ArrayList<>(4);
        for (int i = 0; i < count; i++) {
            ItemStack payment = buffer.readItem();
            ItemStack result = buffer.readItem();
            int kind = Math.min(
                    OfferKind.values().length - 1,
                    Math.max(0, buffer.readVarInt()));
            int maxTrades = Math.max(0, buffer.readVarInt());
            boolean enabled = buffer.readBoolean();
            offers.add(new TradeOffer(
                    payment, result, OfferKind.values()[kind],
                    maxTrades, enabled));
        }
        return normalizeOffers(offers);
    }

    private static List<TradeOffer> normalizeOffers(
            List<TradeOffer> offers) {
        List<TradeOffer> normalized = new ArrayList<>(4);
        if (offers != null) {
            normalized.addAll(offers.stream().limit(4).toList());
        }
        while (normalized.size() < 4) {
            normalized.add(TradeOffer.empty(
                    OfferKind.values()[normalized.size()]));
        }
        return List.copyOf(normalized);
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        inventory,
                        column + row * 9 + 9,
                        108 + column * 18,
                        84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(
                    inventory, column, 108 + column * 18, 142));
        }
    }
}
