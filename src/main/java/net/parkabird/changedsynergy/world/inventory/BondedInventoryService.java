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

    public static boolean open(ServerPlayer owner, ChangedEntity pet) {
        if (!pet.isAlive()
                || pet.level() != owner.level()
                || !LatexSocialMemory.isPetOwner(pet, owner)
                || owner.distanceToSqr(pet) > 64.0D) {
            return false;
        }

        LatexSocialMemory.promoteNativePet(pet, owner);
        Container creatureInventory = null;
        if (pet instanceof AbstractDarkLatexEntity darkLatex) {
            darkLatex.setOwnerUUID(owner.getUUID());
            darkLatex.setTame(true);
            creatureInventory = darkLatex.getInventory();
        } else {
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
