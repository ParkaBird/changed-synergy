package net.parkabird.changedsynergy.world.inventory;

import javax.annotation.Nullable;
import net.ltxprogrammer.changed.entity.ai.DarkLatexFavor;
import net.ltxprogrammer.changed.entity.beast.AbstractDarkLatexEntity;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.ltxprogrammer.changed.world.inventory.TamedDarkLatexInventoryMenu;
import net.ltxprogrammer.changed.world.inventory.UpdateableMenu;
import net.parkabird.changedsynergy.ai.BondedSuitService;
import net.parkabird.changedsynergy.ai.BondedPetSettings;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.ai.InvoluntaryTransfurNegotiation;
import net.parkabird.changedsynergy.ai.SocialAudienceGoal;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.event.LatexSocialEvents;
import net.parkabird.changedsynergy.init.ChangedSynergyMenus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.NetworkHooks;

/** Full Changed/Exp2-style radial menu for ordinary bonded latex creatures. */
public final class BondedLatexMenu extends AbstractContainerMenu implements UpdateableMenu {
    private final Player player;
    @Nullable
    private final ChangedEntity pet;
    private boolean following;
    private boolean shoreWaiting;
    private int targetType;
    private int attackType;
    private int attackCondition;
    private int favor;
    private boolean ownerTransfurred;
    private boolean canReverseOwner;
    private boolean suitingOwner;
    private boolean audienceReleased;

    public BondedLatexMenu(int id, Inventory inventory, ChangedEntity pet) {
        super(ChangedSynergyMenus.BONDED_LATEX.get(), id);
        this.player = inventory.player;
        this.pet = pet;
        this.following = LatexSocialMemory.isFollowingOwner(pet);
        this.shoreWaiting = LatexSocialMemory.isWaitingOnShore(pet);
        int[] state = stateFor(pet);
        this.targetType = state[0];
        this.attackType = state[1];
        this.attackCondition = state[2];
        this.favor = state[3];
        this.ownerTransfurred = ProcessTransfur.isPlayerTransfurred(inventory.player);
        this.canReverseOwner = inventory.player instanceof ServerPlayer serverPlayer
                && InvoluntaryTransfurNegotiation.canBondedReversal(
                        pet, serverPlayer);
        this.suitingOwner = inventory.player instanceof ServerPlayer serverPlayer
                && BondedSuitService.isSuitingOwner(pet, serverPlayer);
    }

    public BondedLatexMenu(int id, Inventory inventory, FriendlyByteBuf extraData) {
        super(ChangedSynergyMenus.BONDED_LATEX.get(), id);
        this.player = inventory.player;
        Entity entity = inventory.player.level().getEntity(extraData.readVarInt());
        this.pet = entity instanceof ChangedEntity changed ? changed : null;
        this.following = extraData.readBoolean();
        this.shoreWaiting = extraData.readBoolean();
        this.targetType = extraData.readVarInt();
        this.attackType = extraData.readVarInt();
        this.attackCondition = extraData.readVarInt();
        this.favor = extraData.readVarInt();
        this.ownerTransfurred = extraData.readBoolean();
        this.canReverseOwner = extraData.readBoolean();
        this.suitingOwner = extraData.readBoolean();
    }

    @Nullable
    public ChangedEntity getPet() {
        return pet;
    }

    public boolean isFollowing() {
        return following;
    }

    public boolean isAquaticPet() {
        return pet instanceof net.ltxprogrammer.changed.entity.beast.AbstractAquaticEntity;
    }

    public boolean isShoreWaiting() {
        return shoreWaiting;
    }

    public int getTargetType() {
        return targetType;
    }

    public int getAttackType() {
        return attackType;
    }

    public int getAttackCondition() {
        return attackCondition;
    }

    public int getFavor() {
        return favor;
    }

    public boolean isOwnerTransfurred() {
        return ownerTransfurred;
    }

    public boolean isSuitingOwner() {
        return suitingOwner;
    }

    public boolean canReverseOwner() {
        return canReverseOwner;
    }

    /** Organic pets expose re-assimilation here, but still cannot start a suit. */
    public boolean isSuitOptionAvailable() {
        return suitingOwner
                || !ownerTransfurred && pet != null
                || canReverseOwner;
    }

    public boolean isOrganicPet() {
        return pet != null && LatexSocialMemory.isOrganic(pet);
    }

    /** Native dark latex and Addon pets store the same four wheel settings separately. */
    public static int[] stateFor(ChangedEntity pet) {
        if (pet instanceof AbstractDarkLatexEntity darkLatex) {
            return new int[]{
                    darkLatex.getTargetType().ordinal(),
                    darkLatex.getAttackType().ordinal(),
                    darkLatex.getAttackCondition().ordinal(),
                    darkLatex.getCurrentFavor().ordinal()
            };
        }
        int[] state = BondedPetSettings.stateFor(pet);
        if (LatexSocialMemory.isOrganic(pet)) {
            state[1] = LatexSocialMemory.organicRescueMode(pet).ordinal();
        }
        return state;
    }

    public void applyLocalCommand(String command) {
        switch (command) {
            case "cycle_follow" -> {
                following = !following;
                shoreWaiting = false;
            }
            case "wait_on_shore" -> {
                following = false;
                shoreWaiting = true;
            }
            case "cycle_target_type" -> targetType = (targetType + 1) % 3;
            case "cycle_attack_type" ->
                    attackType = isOrganicPet()
                            ? (attackType + 1)
                                    % LatexSocialMemory.OrganicRescueMode.values().length
                            : (attackType + 1) % 2;
            case "cycle_attack_condition" -> attackCondition = (attackCondition + 1) % 3;
            case "favor_fishing" -> favor = favor == 1 ? 0 : 1;
            case "favor_caving" -> favor = favor == 2 ? 0 : 2;
            default -> {
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player viewer, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player viewer) {
        return pet != null && pet.isAlive()
                && LatexSocialMemory.petOwnerUuid(pet)
                        .map(viewer.getUUID()::equals)
                        .orElse(false)
                && viewer.distanceToSqr(pet) <= 64.0;
    }

    @Override
    public int getId() {
        return containerId;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void update(CompoundTag payload, LogicalSide receiver, @Nullable ServerPlayer origin) {
        if (receiver != LogicalSide.SERVER || origin == null || pet == null
                || !stillValid(origin)
                || !LatexSocialMemory.isPetOwner(pet, origin)) {
            return;
        }
        releaseAudience(origin);

        String command = payload.getString("command");
        switch (command) {
            case "view_inventory" -> BondedInventoryService.open(origin, pet);
            case "open_social_wheel" ->
                    LatexSocialEvents.openBondedSocialMenu(origin, pet);
            case "cycle_follow" -> {
                following = !LatexSocialMemory.isFollowingOwner(pet);
                LatexSocialMemory.setFollowingOwner(pet, following);
                shoreWaiting = false;
                pet.getNavigation().stop();
            }
            case "wait_on_shore" -> {
                LatexSocialMemory.setWaitingOnShore(pet, origin);
                following = false;
                shoreWaiting = LatexSocialMemory.isWaitingOnShore(pet);
            }
            case "suit_owner" -> BondedSuitService.suitOwner(
                    pet, origin, BondedSuitService.SuitReason.MANUAL);
            case "reassimilate_owner" -> BondedSuitService.reassimilateOwner(pet, origin);
            case "release_owner" -> BondedSuitService.requestOwnerRelease(
                    pet, origin);
            case "reverse_owner" ->
                    InvoluntaryTransfurNegotiation.beginBondedReversal(
                            pet, origin);
            case "cycle_attack_type" -> {
                if (isOrganicPet()) {
                    attackType = LatexSocialMemory
                            .cycleOrganicRescueMode(pet).ordinal();
                    pet.setTarget(null);
                } else if (!handleNativeCommand(pet, origin, command)) {
                    if (!ChangedAddonCompat.handleBondedMenuCommand(
                            pet, origin, command)) {
                        BondedPetSettings.handleCommand(pet, command);
                    }
                }
            }
            default -> {
                if (!handleNativeCommand(pet, origin, command)) {
                    if (!ChangedAddonCompat.handleBondedMenuCommand(
                            pet, origin, command)) {
                        BondedPetSettings.handleCommand(pet, command);
                    }
                }
            }
        }
    }

    @Override
    public void removed(Player viewer) {
        if (viewer instanceof ServerPlayer serverPlayer) {
            releaseAudience(serverPlayer);
        }
        super.removed(viewer);
    }

    private void releaseAudience(ServerPlayer viewer) {
        if (!audienceReleased && pet != null) {
            audienceReleased = true;
            SocialAudienceGoal.end(pet, viewer);
        }
    }

    private static boolean handleNativeCommand(
            ChangedEntity pet,
            ServerPlayer owner,
            String command) {
        if (!(pet instanceof AbstractDarkLatexEntity darkLatex)) {
            return false;
        }
        switch (command) {
            case "cycle_target_type" -> {
                darkLatex.setTargetType(darkLatex.getTargetType().cycle());
                darkLatex.setTarget(null);
            }
            case "cycle_attack_type" -> {
                darkLatex.setAttackType(darkLatex.getAttackType().cycle());
                darkLatex.updateHeldItemChoice();
            }
            case "cycle_attack_condition" -> {
                darkLatex.setAttackCondition(darkLatex.getAttackCondition().cycle());
                darkLatex.setTarget(null);
            }
            case "favor_fishing" -> darkLatex.setFavor(
                    darkLatex.getCurrentFavor() == DarkLatexFavor.FISHING
                            ? DarkLatexFavor.NONE : DarkLatexFavor.FISHING);
            case "favor_caving" -> darkLatex.setFavor(
                    darkLatex.getCurrentFavor() == DarkLatexFavor.CAVING
                            ? DarkLatexFavor.NONE : DarkLatexFavor.CAVING);
            default -> {
                return false;
            }
        }
        return true;
    }
}
