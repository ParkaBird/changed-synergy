package net.parkabird.changedsynergy.world.inventory;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.beast.AbstractDarkLatexEntity;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkHooks;

/** Opens a synchronized view of the real inventory behind a bonded creature. */
public final class BondedInventoryService {
    private BondedInventoryService() {
    }

    public static boolean canAccess(ServerPlayer player, ChangedEntity pet) {
        if (!pet.isAlive() || pet.isRemoved() || pet.level() != player.level()
                || player.isSpectator() || player.distanceToSqr(pet) > 64.0D) return false;
        if (LatexSocialMemory.isPetOwner(pet, player)) return true;
        return LatexSocialMemory.petOwnerUuid(pet).isEmpty()
                && pet.level().getGameRules().getBoolean(net.parkabird.changedsynergy.init.ChangedSynergyGameRules.FRIENDSHIP_SYSTEM)
                && !net.parkabird.changedsynergy.ai.FactionPursuitService.isPursuer(pet)
                && net.parkabird.changedsynergy.ai.CreatureSocialProfile.allowsPersonalRelationship(pet)
                && net.parkabird.changedsynergy.ai.CreaturePersonality.hasTrustedRelationship(pet, player)
                && net.parkabird.changedsynergy.ai.CreaturePersonality.relationshipTier(pet, player)
                    == net.parkabird.changedsynergy.ai.CreaturePersonality.RelationshipTier.CLOSE
                && !LatexSocialMemory.isProvoked(pet, player);
    }

    public static boolean open(ServerPlayer owner, ChangedEntity pet) {
        if (!pet.isAlive()
                || pet.level() != owner.level()
                || !canAccess(owner, pet)
                || owner.distanceToSqr(pet) > 64.0D) {
            return false;
        }

        // The fallback container has a per-open storage view: serialize viewers to
        // avoid stale snapshots duplicating items. This also protects native APIs.
        for (ServerPlayer other : owner.serverLevel().players()) {
            if (other != owner && other.containerMenu instanceof BondedCreatureInventoryMenu menu
                    && menu.getPet() == pet) {
                owner.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.changed_synergy.inventory.busy"), true);
                return false;
            }
        }
        boolean owned = LatexSocialMemory.isPetOwner(pet, owner);
        if (owned) LatexSocialMemory.promoteNativePet(pet, owner);
        Container creatureInventory = null;
        if (pet.getPersistentData().contains("ChangedSynergyBondedInventory")) {
            creatureInventory = new BondedCreatureInventory(pet);
        } else if (pet instanceof AbstractDarkLatexEntity darkLatex) {
            if (owned) {
                darkLatex.setOwnerUUID(owner.getUUID());
                darkLatex.setTame(true);
            }
            creatureInventory = darkLatex.getInventory();
        } else if (owned) {
            creatureInventory = ChangedAddonCompat.prepareBondedInventory(pet, owner);
        }

        // Changed Addon does not necessarily apply its taming mixin to every
        // base Changed creature.  Such creatures still need a real persistent
        // inventory after becoming bonded, so provide one directly.
        if (creatureInventory == null) {
            creatureInventory = new BondedCreatureInventory(pet);
        }
        if (creatureInventory.getContainerSize()
                < BondedCreatureInventoryMenu.PET_INVENTORY_SIZE) {
            ChangedSynergyMod.LOGGER.error(
                    "Bonded inventory for {} only exposed {} slots",
                    pet.getType(), creatureInventory.getContainerSize());
            return false;
        }

        Container synchronizedInventory = creatureInventory;
        NetworkHooks.openScreen(
                owner,
                new SimpleMenuProvider(
                        (id, playerInventory, viewer) -> new BondedCreatureInventoryMenu(
                                id, playerInventory, pet, synchronizedInventory),
                        pet.getDisplayName()),
                extra -> BondedCreatureInventoryMenu.writeOpenData(
                        extra, owner, pet));
        ChangedSynergyMod.LOGGER.info(
                "Opened bonded inventory for player {} and entity {} ({}) using {}",
                owner.getGameProfile().getName(),
                pet.getUUID(),
                pet.getType(),
                synchronizedInventory.getClass().getSimpleName());
        return true;
    }
}
