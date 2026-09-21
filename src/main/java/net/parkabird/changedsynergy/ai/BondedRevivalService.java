package net.parkabird.changedsynergy.ai;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.ltxprogrammer.changed.init.ChangedItems;
import net.ltxprogrammer.changed.item.Syringe;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.init.ChangedSynergyItems;

/** Death-to-mask-to-body reconstruction for bonded dark latex creatures. */
public final class BondedRevivalService {
    public static final String REVIVAL_ENTITY_TOKEN = "ChangedSynergyRevivalToken";
    private static final String MASK_ROOT = "ChangedSynergyBondRevival";
    private static final String TOKEN = "Token";
    private static final String OWNER = "Owner";
    private static final String BOND_NAME = "BondName";
    private static final String FORM = "Form";
    private static final String STATE_BROKEN = "broken";
    private static final String STATE_REPAIRED = "repaired";
    private static final String STATE_REBUILDING = "rebuilding";
    private static final String STATE_WRAPPED = "wrapped";
    private static final String STATE_COMPLETE = "complete";
    private static final String PENDING_ITEM = "ChangedSynergyPendingRevivalItem";

    private BondedRevivalService() {
    }

    /** Captures identity before ordinary death cleanup removes bonds and cargo. */
    public static boolean dropBrokenMask(ChangedEntity creature) {
        if (!(creature.level() instanceof ServerLevel level)
                || isProvisional(creature)
                || creature.getType() == ChangedEntities.DARK_LATEX_WOLF_PARTIAL.get()
                || LatexSocialMemory.isOrganic(creature)
                || creature.getSelfVariant() == null) {
            return false;
        }
        Set<UUID> bonds = new LinkedHashSet<>(
                LatexSocialMemory.bondedPlayerUuids(creature));
        if (bonds.isEmpty()) {
            return false;
        }

        BondedRevivalData data = BondedRevivalData.get(level.getServer());
        if (data.hasOldEntity(creature.getUUID())) {
            return false;
        }
        UUID owner = LatexSocialMemory.petOwnerUuid(creature)
                .filter(bonds::contains)
                .orElseGet(() -> bonds.iterator().next());
        String name = creature.getDisplayName().getString();
        String form = creature.getSelfVariant().getFormId().toString();
        boolean darkMask = HunterFaction.of(creature) == HunterFaction.DARK;

        CompoundTag snapshot = creature.saveWithoutId(new CompoundTag());
        snapshot.putString("id", EntityType.getKey(creature.getType()).toString());
        sanitizeSnapshot(snapshot);

        CompoundTag record = new CompoundTag();
        record.putString("State", STATE_BROKEN);
        record.putUUID("Owner", owner);
        record.putUUID("OldEntity", creature.getUUID());
        record.putString("BondName", name);
        record.putString("Form", form);
        record.putBoolean("LatexSample", !darkMask);
        record.put("Snapshot", snapshot);
        ListTag storedBonds = new ListTag();
        bonds.forEach(uuid -> storedBonds.add(StringTag.valueOf(uuid.toString())));
        record.put("Bonds", storedBonds);
        UUID token = data.create(record);

        ItemStack mask = darkMask
                ? brokenMask(token, owner, name, form)
                : revivalSample(token, owner, name, form);
        ItemEntity drop = new ItemEntity(
                level, creature.getX(), creature.getY() + 0.3D, creature.getZ(), mask);
        drop.setDefaultPickUpDelay();
        drop.setUnlimitedLifetime();
        drop.setGlowingTag(true);
        level.addFreshEntity(drop);

        ServerPlayer onlineOwner = level.getServer().getPlayerList().getPlayer(owner);
        if (onlineOwner != null) {
            onlineOwner.sendSystemMessage(Component.translatable(
                    darkMask ? "message.changed_synergy.revival.mask_shattered"
                            : "message.changed_synergy.revival.sample_dropped", name));
        }
        return true;
    }

    public static boolean hasRevivalToken(ItemStack stack) {
        CompoundTag root = maskData(stack);
        return root != null && root.hasUUID(TOKEN);
    }

    public static boolean isRevivalMask(ItemStack stack) {
        return hasRevivalToken(stack)
                && (stack.is(ChangedSynergyItems.BROKEN_DARK_LATEX_MASK.get())
                        || isRepairedMask(stack));
    }

    public static boolean isRepairedMask(ItemStack stack) {
        return stack.is(ChangedSynergyItems.REPAIRED_DARK_LATEX_MASK.get())
                || stack.is(ChangedItems.DARK_LATEX_MASK.get()) && hasRevivalToken(stack);
    }

    public static boolean isRevivalSample(ItemStack stack) {
        return stack.is(ChangedItems.LATEX_BASE.get()) && hasRevivalToken(stack);
    }

    public static boolean isRevivalVessel(ItemStack stack) {
        return (stack.is(ChangedItems.LATEX_SYRINGE.get())
                || stack.is(ChangedItems.LATEX_FLASK.get())) && hasRevivalToken(stack);
    }

    /** Validates the original Infuser result against the deceased individual's form. */
    public static boolean bindInfuserOutput(
            ItemStack sample, ItemStack vessel, ServerPlayer crafter) {
        if (!isRevivalSample(sample) || vessel.isEmpty()
                || !isRevivalVesselType(vessel)) {
            return false;
        }
        CompoundTag root = maskData(sample);
        UUID token = root.getUUID(TOKEN);
        CompoundTag record = BondedRevivalData.get(crafter.server).record(token);
        if (record == null || !record.getBoolean("LatexSample")
                || !STATE_BROKEN.equals(record.getString("State"))
                || !root.getString(FORM).equals(vessel.getOrCreateTag().getString("form"))) {
            return false;
        }
        vessel.getOrCreateTag().put(MASK_ROOT, root.copy());
        return true;
    }

    private static boolean isRevivalVesselType(ItemStack stack) {
        return stack.is(ChangedItems.LATEX_SYRINGE.get())
                || stack.is(ChangedItems.LATEX_FLASK.get());
    }

    public static void prepareVesselUse(ItemStack stack, ServerPlayer player) {
        if (!isRevivalVessel(stack) || !belongsTo(stack, player)) {
            return;
        }
        CompoundTag pending = maskData(stack).copy();
        pending.putLong("Expires", player.level().getGameTime() + 200L);
        pending.putString("Vessel", stack.is(ChangedItems.LATEX_FLASK.get())
                ? "flask" : "syringe");
        player.getPersistentData().put(PENDING_ITEM, pending);
    }

    public static void onVesselTransfurCompleted(ServerPlayer player) {
        CompoundTag pending = player.getPersistentData().getCompound(PENDING_ITEM);
        if (!pending.hasUUID(TOKEN)) {
            return;
        }
        player.getPersistentData().remove(PENDING_ITEM);
        if (pending.getLong("Expires") < player.level().getGameTime()
                || !player.getUUID().equals(pending.getUUID(OWNER))) {
            return;
        }
        UUID token = pending.getUUID(TOKEN);
        var current = ProcessTransfur.getPlayerTransfurVariant(player);
        if (current == null || !pending.getString(FORM).equals(
                current.getParent().getFormId().toString())) {
            return;
        }
        player.server.execute(() -> beginReconstruction(player, token));
    }

    /** Updates repaired masks created by an older build to the fixed display name. */
    public static void normalizeDisplayName(ItemStack stack) {
        if (isRepairedMask(stack) && hasRevivalToken(stack)) {
            Component fixed = Component.translatable(
                    "item.changed_synergy.repaired_dark_latex_mask");
            if (!fixed.equals(stack.getHoverName())) {
                stack.setHoverName(fixed);
            }
        }
    }

    /** Converts repaired masks from builds that reused Changed's base item. */
    public static ItemStack upgradeLegacyRepairedMask(ItemStack stack) {
        if (!stack.is(ChangedItems.DARK_LATEX_MASK.get()) || !hasRevivalToken(stack)) {
            return stack;
        }
        ItemStack upgraded = new ItemStack(
                ChangedSynergyItems.REPAIRED_DARK_LATEX_MASK.get(), stack.getCount());
        if (stack.hasTag()) {
            upgraded.setTag(stack.getTag().copy());
        }
        normalizeDisplayName(upgraded);
        return upgraded;
    }

    public static String bondName(ItemStack stack) {
        CompoundTag root = maskData(stack);
        return root == null ? "" : root.getString(BOND_NAME);
    }

    public static boolean belongsTo(ItemStack stack, ServerPlayer player) {
        CompoundTag root = maskData(stack);
        return root != null
                && root.hasUUID(OWNER)
                && player.getUUID().equals(root.getUUID(OWNER));
    }

    /** Returns true only for the first owner pickup of this physical mask. */
    public static boolean markOwnerPickup(ItemStack stack) {
        CompoundTag root = maskData(stack);
        if (root == null || root.getBoolean("OwnerPickupNotified")) {
            return false;
        }
        root.putBoolean("OwnerPickupNotified", true);
        return true;
    }

    /** Returns true only when this repaired mask is first worn by its owner. */
    public static boolean markWorn(ItemStack stack) {
        CompoundTag root = maskData(stack);
        if (root == null || root.getBoolean("WornNotified")) {
            return false;
        }
        root.putBoolean("WornNotified", true);
        return true;
    }

    public static ItemStack repairMask(ItemStack broken) {
        CompoundTag root = maskData(broken);
        if (root == null || !root.hasUUID(TOKEN)) {
            return ItemStack.EMPTY;
        }
        ResourceLocation form = ResourceLocation.tryParse(root.getString(FORM));
        if (form == null) {
            return ItemStack.EMPTY;
        }
        ItemStack repaired = Syringe.setPureVariant(
                new ItemStack(ChangedSynergyItems.REPAIRED_DARK_LATEX_MASK.get()), form);
        repaired.getOrCreateTag().put(MASK_ROOT, root.copy());
        repaired.setHoverName(Component.translatable(
                "item.changed_synergy.repaired_dark_latex_mask"));
        return repaired;
    }

    public static void markRepaired(ItemStack stack, ServerPlayer crafter) {
        CompoundTag root = maskData(stack);
        if (root == null || !root.hasUUID(TOKEN)) {
            return;
        }
        UUID token = root.getUUID(TOKEN);
        if (BondedRevivalData.get(crafter.server)
                .transition(token, STATE_BROKEN, STATE_REPAIRED)) {
            crafter.sendSystemMessage(Component.translatable(
                    "message.changed_synergy.revival.mask_repaired",
                    root.getString(BOND_NAME)));
        }
    }

    /** Called while the original mask is still in the head slot. */
    public static void onMaskTransfurCompleted(ServerPlayer player) {
        ItemStack worn = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!isRepairedMask(worn) || !hasRevivalToken(worn)) {
            return;
        }
        CompoundTag root = maskData(worn);
        UUID token = root.getUUID(TOKEN);
        consumeTokenMasks(player, token);
        player.server.execute(() -> beginReconstruction(player, token));
    }

    private static void beginReconstruction(ServerPlayer player, UUID token) {
        BondedRevivalData data = BondedRevivalData.get(player.server);
        CompoundTag record = data.record(token);
        if (record == null || !record.hasUUID("Owner")) {
            failAfterConsumedMask(player, null, token, null, false, false);
            return;
        }
        String name = record.getString("BondName");
        boolean reusable = STATE_BROKEN.equals(record.getString("State"))
                || STATE_REPAIRED.equals(record.getString("State"));
        if (!reusable) {
            player.sendSystemMessage(Component.translatable(
                    "message.changed_synergy.revival.mask_spent", name));
            failAfterConsumedMask(player, record, token, null, false, false);
            return;
        }
        if (!player.getUUID().equals(record.getUUID("Owner"))) {
            player.sendSystemMessage(Component.translatable(
                    "message.changed_synergy.revival.wrong_owner", name));
            failAfterConsumedMask(
                    player, record, token, null, false, true);
            return;
        }
        var current = ProcessTransfur.getPlayerTransfurVariant(player);
        if (!player.isAlive()
                || player.isSpectator()
                || current == null
                || !record.getString("Form").equals(
                        current.getParent().getFormId().toString())) {
            player.sendSystemMessage(Component.translatable(
                    "message.changed_synergy.revival.cannot_rebuild", name));
            failAfterConsumedMask(
                    player, record, token, null, false, true);
            return;
        }
        if (!data.transitionFromEither(
                token, STATE_BROKEN, STATE_REPAIRED, STATE_REBUILDING)) {
            player.sendSystemMessage(Component.translatable(
                    "message.changed_synergy.revival.mask_spent", name));
            failAfterConsumedMask(player, record, token, null, false, false);
            return;
        }

        UUID newEntityId = UUID.randomUUID();
        data.putUuid(token, "NewEntity", newEntityId);
        record.putUUID("NewEntity", newEntityId);
        CompoundTag snapshot = record.getCompound("Snapshot").copy();
        snapshot.putUUID("UUID", newEntityId);
        Entity created = EntityType.create(snapshot, player.serverLevel()).orElse(null);
        if (!(created instanceof ChangedEntity revived)) {
            failAfterConsumedMask(player, record, token, null, true, true);
            return;
        }

        revived.setUUID(newEntityId);
        revived.setPos(player.getX(), player.getY(), player.getZ());
        revived.setYRot(player.getYRot());
        revived.setYHeadRot(player.getYHeadRot());
        revived.setHealth(revived.getMaxHealth());
        revived.removeAllEffects();
        revived.setSecondsOnFire(0);
        revived.setDeltaMovement(Vec3.ZERO);
        revived.setTarget(null);
        revived.getNavigation().stop();
        revived.setPersistenceRequired();
        revived.setInvulnerable(true);
        revived.getPersistentData().putUUID(REVIVAL_ENTITY_TOKEN, token);
        applyMemoryDamage(revived);
        if (!player.serverLevel().addFreshEntity(revived)) {
            failAfterConsumedMask(player, record, token, revived, true, true);
            return;
        }
        if (BondedSuitService.ability(revived) == null) {
            revived.getPersistentData().putBoolean("ChangedSynergyRevivalGrabAbility", true);
            BondedSuitService.ensureRevivalGrabAbility(revived);
            net.parkabird.changedsynergy.network.ChangedSynergyNetwork.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> revived),
                    new net.parkabird.changedsynergy.network.RevivalGrabAbilityPacket(revived.getId()));
        }
        if (!BondedSuitService.beginRevivalSuit(revived, player, token)) {
            if (!revived.isRemoved()) {
                revived.discard();
            }
            failAfterConsumedMask(player, record, token, revived, true, true);
            return;
        }
        data.setState(token, STATE_WRAPPED);
        player.sendSystemMessage(Component.translatable(
                "message.changed_synergy.revival.body_rebuilt", name));
    }

    public static void complete(
            ChangedEntity revived, ServerPlayer owner, UUID token) {
        BondedRevivalData data = BondedRevivalData.get(owner.server);
        CompoundTag record = data.record(token);
        if (record == null
                || !STATE_WRAPPED.equals(record.getString("State"))
                || !record.hasUUID("NewEntity")
                || !revived.getUUID().equals(record.getUUID("NewEntity"))) {
            return;
        }
        data.setState(token, STATE_COMPLETE);
        revived.getPersistentData().remove(REVIVAL_ENTITY_TOKEN);
        revived.setInvulnerable(false);

        UUID oldEntity = record.getUUID("OldEntity");
        CreatureMorphAliasData.get(owner.server).record(oldEntity, revived.getUUID());
        for (UUID playerId : storedBonds(record)) {
            ServerPlayer bondedPlayer = owner.server.getPlayerList().getPlayer(playerId);
            if (bondedPlayer != null) {
                LatexSocialMemory.restorePlayerBondReference(
                        bondedPlayer, oldEntity, revived);
            }
        }
        CreatureIdentity.ensure(revived);
        CreaturePersonality.ensure(revived);
        CreatureLifeMemory.ensure(revived);
        CreatureCommunityData.bind(revived);
        BondedCreatureLifecycle.track(revived);
        CreaturePersonality.setFamiliarity(
                revived,
                owner,
                Math.max(36, CreaturePersonality.familiarity(revived, owner) + 20));
        owner.sendSystemMessage(Component.translatable(
                "message.changed_synergy.revival.complete",
                record.getString("BondName")));
    }

    public static void applyCompletedReferences(ServerPlayer player) {
        BondedRevivalData data = BondedRevivalData.get(player.server);
        for (CompoundTag record : data.records()) {
            if (!STATE_COMPLETE.equals(record.getString("State"))
                    || !record.hasUUID("OldEntity")
                    || !record.hasUUID("NewEntity")
                    || !storedBonds(record).contains(player.getUUID())) {
                continue;
            }
            Entity entity = findEntity(player, record.getUUID("NewEntity"));
            LatexSocialMemory.restorePlayerBondReference(
                    player,
                    record.getUUID("OldEntity"),
                    entity instanceof ChangedEntity changed ? changed : null,
                    record.getUUID("NewEntity"));
        }
    }

    public static boolean isProvisional(ChangedEntity creature) {
        return creature.getPersistentData().hasUUID(REVIVAL_ENTITY_TOKEN);
    }

    @Nullable
    public static UUID token(ChangedEntity creature) {
        return isProvisional(creature)
                ? creature.getPersistentData().getUUID(REVIVAL_ENTITY_TOKEN)
                : null;
    }

    private static void failAfterConsumedMask(
            ServerPlayer player,
            @Nullable CompoundTag record,
            UUID token,
            @Nullable ChangedEntity provisional,
            boolean rollbackState,
            boolean returnMask) {
        BondedRevivalData data = BondedRevivalData.get(player.server);
        if (record != null && rollbackState) {
            data.setState(token, STATE_REPAIRED);
            if (record.hasUUID("NewEntity")) {
                UUID failedEntity = record.getUUID("NewEntity");
                for (UUID playerId : storedBonds(record)) {
                    ServerPlayer bondedPlayer = player.server.getPlayerList()
                            .getPlayer(playerId);
                    if (bondedPlayer != null) {
                        LatexSocialMemory.forgetMissingBond(
                                bondedPlayer, failedEntity);
                        BondedCreatureLifecycle.forget(
                                bondedPlayer, failedEntity);
                        PlayerRelationshipSettings.forgetContact(
                                bondedPlayer, failedEntity);
                    }
                }
            }
        }
        if (record != null && returnMask) {
            ItemStack returned = repairedMask(record, token);
            if (!returned.isEmpty() && !player.getInventory().add(returned)) {
                player.drop(returned, false);
            }
        }
        if (ProcessTransfur.getPlayerTransfurVariant(player) != null) {
            ProcessTransfur.removePlayerTransfurVariant(player);
        }
        player.sendSystemMessage(Component.translatable(
                "message.changed_synergy.revival.failed"));
        ChangedSynergyMod.LOGGER.warn(
                "Could not reconstruct bonded creature for revival token {}{}",
                token,
                provisional == null ? "" : " after spawning provisional entity");
    }

    private static ItemStack repairedMask(CompoundTag record, UUID token) {
        if (record.getBoolean("LatexSample")) {
            CompoundTag root = revivalRoot(token, record.getUUID("Owner"),
                    record.getString("BondName"), record.getString("Form"));
            ItemStack vessel = Syringe.setPureVariant(
                    new ItemStack(ChangedItems.LATEX_SYRINGE.get()),
                    ResourceLocation.parse(record.getString("Form")));
            vessel.getOrCreateTag().put(MASK_ROOT, root);
            return vessel;
        }
        CompoundTag root = new CompoundTag();
        root.putUUID(TOKEN, token);
        root.putUUID(OWNER, record.getUUID("Owner"));
        root.putString(BOND_NAME, record.getString("BondName"));
        root.putString(FORM, record.getString("Form"));
        ItemStack broken = new ItemStack(ChangedSynergyItems.BROKEN_DARK_LATEX_MASK.get());
        broken.getOrCreateTag().put(MASK_ROOT, root);
        return repairMask(broken);
    }

    private static ItemStack brokenMask(
            UUID token, UUID owner, String name, String form) {
        ItemStack stack = new ItemStack(ChangedSynergyItems.BROKEN_DARK_LATEX_MASK.get());
        stack.getOrCreateTag().put(MASK_ROOT, revivalRoot(token, owner, name, form));
        return stack;
    }

    private static ItemStack revivalSample(
            UUID token, UUID owner, String name, String form) {
        ItemStack stack = new ItemStack(ChangedItems.LATEX_BASE.get());
        stack.getOrCreateTag().put(MASK_ROOT, revivalRoot(token, owner, name, form));
        stack.setHoverName(Component.translatable("item.changed_synergy.bonded_latex_sample"));
        return stack;
    }

    private static CompoundTag revivalRoot(UUID token, UUID owner, String name, String form) {
        CompoundTag root = new CompoundTag();
        root.putUUID(TOKEN, token);
        root.putUUID(OWNER, owner);
        root.putString(BOND_NAME, name);
        root.putString(FORM, form);
        return root;
    }

    /** The completed mask is a one-use frame, including copied stacks of its token. */
    private static void consumeTokenMasks(ServerPlayer player, UUID token) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            CompoundTag root = maskData(stack);
            if (root != null
                    && root.hasUUID(TOKEN)
                    && token.equals(root.getUUID(TOKEN))
                    && isRepairedMask(stack)) {
                stack.setCount(0);
            }
        }
        player.inventoryMenu.broadcastChanges();
    }

    @Nullable
    private static CompoundTag maskData(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(MASK_ROOT, Tag.TAG_COMPOUND)
                ? tag.getCompound(MASK_ROOT)
                : null;
    }

    private static Set<UUID> storedBonds(CompoundTag record) {
        Set<UUID> bonds = new LinkedHashSet<>();
        ListTag stored = record.getList("Bonds", Tag.TAG_STRING);
        for (int index = 0; index < stored.size(); index++) {
            try {
                bonds.add(UUID.fromString(stored.getString(index)));
            } catch (IllegalArgumentException ignored) {
                // Ignore a malformed secondary owner and preserve the record.
            }
        }
        return bonds;
    }

    @Nullable
    private static Entity findEntity(ServerPlayer player, UUID uuid) {
        for (ServerLevel level : player.server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private static void sanitizeSnapshot(CompoundTag snapshot) {
        for (String key : new String[]{
                "UUID", "Pos", "Motion", "Rotation", "Health", "HurtTime",
                "HurtByTimestamp", "DeathTime", "Fire", "Air", "OnGround",
                "FallDistance", "PortalCooldown", "Passengers", "Leash",
                "HandItems", "ArmorItems", "HandDropChances", "ArmorDropChances",
                "ActiveEffects", "Brain"}) {
            snapshot.remove(key);
        }
        if (snapshot.contains("ForgeData", Tag.TAG_COMPOUND)) {
            snapshot.getCompound("ForgeData").remove("ChangedSynergyCarriedResource");
        }
    }

    private static void applyMemoryDamage(ChangedEntity revived) {
        CompoundTag root = revived.getPersistentData().getCompound(
                "ChangedSynergyPersonality");
        CompoundTag memories = root.getCompound("PlayerMemories");
        for (String playerId : memories.getAllKeys()) {
            if (!memories.contains(playerId, Tag.TAG_COMPOUND)) {
                continue;
            }
            CompoundTag memory = memories.getCompound(playerId);
            for (String key : new String[]{
                    "Encounters", "PatsReceived", "PatsGiven", "GiftFavor",
                    "PlaysTogether", "RescueConcern"}) {
                memory.putInt(key, Math.max(0, memory.getInt(key) * 4 / 5));
            }
            memory.remove("LastPlayed");
            memory.remove("LastCountedEncounter");
            memory.remove("LastInteraction");
            memory.remove("LastRescueConcern");
            memory.remove("RescueConcernUpdated");
        }
    }
}
