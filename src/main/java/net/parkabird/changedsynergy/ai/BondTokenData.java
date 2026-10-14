package net.parkabird.changedsynergy.ai;

import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/** The escrow is independent of loaded chunks and of a player's control-mode body. */
public final class BondTokenData extends SavedData {
    private CompoundTag offers = new CompoundTag();
    private CompoundTag refunds = new CompoundTag();

    public static BondTokenData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(BondTokenData::load,
                BondTokenData::new, "changed_synergy_bond_tokens");
    }

    public static BondTokenData load(CompoundTag tag) {
        BondTokenData data = new BondTokenData();
        data.offers = tag.getCompound("Offers").copy();
        data.refunds = tag.getCompound("Refunds").copy();
        return data;
    }

    public CompoundTag offer(UUID creature) { return offers.getCompound(creature.toString()); }
    public boolean hasOffer(UUID creature) { return offers.contains(creature.toString(), Tag.TAG_COMPOUND); }
    public boolean belongsTo(UUID creature, UUID player) {
        CompoundTag offer = offer(creature);
        return offer.hasUUID("Owner") && player.equals(offer.getUUID("Owner"));
    }

    public boolean reserve(UUID creature, UUID owner, ItemStack stack) {
        if (hasOffer(creature) || stack.isEmpty()) return false;
        CompoundTag offer = new CompoundTag();
        offer.putUUID("Owner", owner);
        offer.put("Item", stack.save(new CompoundTag()));
        offers.put(creature.toString(), offer);
        setDirty();
        return true;
    }

    public void complete(UUID creature, UUID player) {
        if (!belongsTo(creature, player)) return;
        offers.remove(creature.toString());
        setDirty();
    }

    public void refund(UUID creature) {
        CompoundTag offer = offer(creature);
        if (!offer.hasUUID("Owner")) return;
        String owner = offer.getUUID("Owner").toString();
        ListTag items = refunds.getList(owner, Tag.TAG_COMPOUND);
        items.add(offer.getCompound("Item").copy());
        refunds.put(owner, items);
        offers.remove(creature.toString());
        setDirty();
    }

    public void refundAll() {
        for (String key : new ArrayList<>(offers.getAllKeys())) {
            try { refund(UUID.fromString(key)); }
            catch (IllegalArgumentException ignored) { offers.remove(key); setDirty(); }
        }
    }

    public void replace(UUID oldId, UUID newId) {
        if (!hasOffer(oldId) || oldId.equals(newId)) return;
        if (hasOffer(newId)) { refund(oldId); return; }
        offers.put(newId.toString(), offer(oldId).copy());
        offers.remove(oldId.toString());
        setDirty();
    }

    public void queueRefund(UUID owner, ItemStack stack) {
        if (stack.isEmpty()) return;
        ListTag items = refunds.getList(owner.toString(), Tag.TAG_COMPOUND);
        items.add(stack.save(new CompoundTag()));
        refunds.put(owner.toString(), items);
        setDirty();
    }

    public boolean hasRefund(UUID owner) { return refunds.contains(owner.toString()); }
    public ListTag takeRefunds(UUID owner) {
        ListTag items = refunds.getList(owner.toString(), Tag.TAG_COMPOUND).copy();
        refunds.remove(owner.toString());
        setDirty();
        return items;
    }

    @Override public CompoundTag save(CompoundTag tag) {
        tag.put("Offers", offers.copy());
        tag.put("Refunds", refunds.copy());
        return tag;
    }
}
