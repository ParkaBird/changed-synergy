package net.parkabird.changedsynergy.ai;

import java.util.EnumSet;
import java.util.List;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.compat.ChangedVanillaCompat;
import net.parkabird.changedsynergy.ai.CreatureSettlementService.FishingSite;
import net.parkabird.changedsynergy.ai.CompanionWorkDialogue.WorkKind;
import net.parkabird.changedsynergy.world.inventory.BondedCreatureInventory;
import net.parkabird.changedsynergy.performance.SynergyPerformanceTracker;
import net.parkabird.changedsynergy.performance.SynergyPerformanceTracker.Feature;

/**
 * Complete Changed-style fishing/caving implementation for bonded creatures
 * that are not covered by either native pet backend.
 */
public final class FallbackBondedWorkGoal extends Goal {
    public static final int PRIORITY = 2;
    private static final double SPEED = 0.30D;
    private static final int SEARCH_RADIUS = 32;
    private static final int VERTICAL_RADIUS = 6;
    private static final int MAX_WORK_TICKS = 600;
    private static final double OWNER_WORK_LEASH = 32.0D;

    private enum Work {
        NONE,
        FISHING,
        MINING,
        TORCHING
    }

    private final ChangedEntity pet;
    private Work work = Work.NONE;
    @Nullable private FishingSite fishingSite;
    @Nullable private BlockPos blockTarget;
    @Nullable private Vec3 destination;
    @Nullable private BondedCreatureInventory inventory;
    private int toolSlot = -1;
    private int actionTicks;
    private int repathTicks;
    private int workTicks;
    private long nextSearchTick;

    public FallbackBondedWorkGoal(ChangedEntity pet) {
        this.pet = pet;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!SynergyPerformanceTracker.featureEnabled(Feature.COMPANION_WORK)
                || !(pet.level() instanceof ServerLevel level)
                || !BondedPetSettings.usesFallbackBackend(pet)
                || !LatexSocialMemory.hasActiveBond(pet)
                || CreatureSettlementService.hasCargo(pet)
                || !movementAvailable()
                || level.getGameTime() < nextSearchTick) {
            return false;
        }
        ServerPlayer owner = LatexSocialMemory.getPetOwner(pet);
        if (owner == null || owner.level() != pet.level()
                || pet.distanceToSqr(owner) > OWNER_WORK_LEASH * OWNER_WORK_LEASH) {
            return false;
        }
        if (!SynergyPerformanceTracker.allowBackground(
                pet,
                Feature.COMPANION_WORK,
                SynergyPerformanceTracker.configuredBackgroundInterval())) {
            return false;
        }

        clearTargets();
        inventory = new BondedCreatureInventory(pet);
        int favor = BondedPetSettings.favor(pet);
        if (favor == BondedPetSettings.FAVOR_FISHING) {
            toolSlot = inventory.findFishingRod();
            fishingSite = toolSlot < 0 ? null
                    : CreatureSettlementService.findCompanionFishingSite(
                            pet, SEARCH_RADIUS, VERTICAL_RADIUS).orElse(null);
            if (fishingSite != null) {
                work = Work.FISHING;
                destination = CreatureSettlementService.fishingApproach(
                        pet, fishingSite);
            }
        } else if (favor == BondedPetSettings.FAVOR_CAVING
                && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            blockTarget = findOre(level);
            if (blockTarget != null) {
                toolSlot = inventory.findMiningTool(level.getBlockState(blockTarget));
                work = Work.MINING;
                destination = Vec3.atCenterOf(blockTarget);
            } else {
                toolSlot = inventory.findItem(Items.TORCH);
                blockTarget = toolSlot < 0 ? null : findTorchPosition(level);
                if (blockTarget != null) {
                    work = Work.TORCHING;
                    destination = Vec3.atBottomCenterOf(blockTarget);
                }
            }
        }
        nextSearchTick = level.getGameTime() + (work == Work.NONE ? 40L : 10L);
        return work != Work.NONE && destination != null;
    }

    @Override
    public boolean canContinueToUse() {
        return SynergyPerformanceTracker.featureEnabled(Feature.COMPANION_WORK)
                && work != Work.NONE && destination != null
                && !CreatureSettlementService.hasCargo(pet)
                && movementAvailable()
                && BondedPetSettings.favor(pet)
                        == (work == Work.FISHING
                                ? BondedPetSettings.FAVOR_FISHING
                                : BondedPetSettings.FAVOR_CAVING);
    }

    @Override
    public void start() {
        actionTicks = 0;
        repathTicks = 0;
        workTicks = 0;
        showTool();
        ServerPlayer owner = LatexSocialMemory.getPetOwner(pet);
        if (owner != null) {
            CompanionWorkDialogue.announceStart(
                    pet, owner,
                    work == Work.FISHING ? WorkKind.FISHING : WorkKind.MINING);
        }
        moveToTarget();
    }

    @Override
    public void tick() {
        GatheringToolPresentation.heartbeat(pet);
        if (destination == null || ++workTicks > MAX_WORK_TICKS) {
            stopWork();
            return;
        }
        double arrivalSqr = work == Work.MINING ? 3.2D * 3.2D : 2.0D * 2.0D;
        if (pet.distanceToSqr(destination) > arrivalSqr) {
            if (--repathTicks <= 0 || pet.getNavigation().isDone()) {
                repathTicks = 12;
                moveToTarget();
            }
            return;
        }
        pet.getNavigation().stop();
        switch (work) {
            case FISHING -> tickFishing();
            case MINING -> tickMining();
            case TORCHING -> placeTorch();
            default -> stopWork();
        }
    }

    @Override
    public void stop() {
        pet.getNavigation().stop();
        if (pet.level() instanceof ServerLevel level) {
            FishingVisualEffects.cancel(level, pet);
        }
        clearBreakingAnimation();
        restoreTool();
        clearTargets();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private void tickFishing() {
        if (!(pet.level() instanceof ServerLevel level)
                || fishingSite == null || inventory == null
                || inventory.findFishingRod() < 0
                || !level.getBlockState(fishingSite.water()).is(Blocks.WATER)) {
            stopWork();
            return;
        }
        pet.getLookControl().setLookAt(Vec3.atCenterOf(fishingSite.water()));
        if (actionTicks++ == 0) {
            pet.swing(InteractionHand.MAIN_HAND);
            level.playSound(null, pet.blockPosition(),
                    SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL,
                    0.55F, 0.9F + pet.getRandom().nextFloat() * 0.2F);
            FishingVisualEffects.cast(level, pet, fishingSite.water());
            FishingVisualEffects.setRodCastModel(pet, true);
        }
        FishingVisualEffects.tick(level, pet, fishingSite.water(), actionTicks);
        if (actionTicks < 160) {
            return;
        }
        ItemStack rod = inventory.storageItem(toolSlot);
        int fishingLuck = EnchantmentHelper.getFishingLuckBonus(rod);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(fishingSite.water()))
                .withParameter(LootContextParams.TOOL, rod)
                .withParameter(LootContextParams.THIS_ENTITY, pet)
                .withLuck(fishingLuck)
                .create(LootContextParamSets.FISHING);
        LootTable table = level.getServer().getLootData()
                .getLootTable(BuiltInLootTables.FISHING);
        List<ItemStack> caught = table.getRandomItems(params);
        insertOrDrop(level, caught, pet.blockPosition());
        FishingVisualEffects.retrieve(
                level, pet, fishingSite.water(),
                caught.stream().filter(stack -> !stack.isEmpty())
                        .findFirst().orElse(ItemStack.EMPTY));
        FishingVisualEffects.setRodCastModel(pet, false);
        inventory.damageStoredTool(toolSlot, 1);
        pet.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, pet.blockPosition(),
                SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL,
                0.8F, 0.9F + pet.getRandom().nextFloat() * 0.2F);
        announceWorkSuccess(WorkKind.FISHING);
        stopWork();
    }

    private void tickMining() {
        if (!(pet.level() instanceof ServerLevel level)
                || blockTarget == null || inventory == null) {
            stopWork();
            return;
        }
        BlockState state = level.getBlockState(blockTarget);
        ItemStack tool = inventory.storageItem(toolSlot);
        if (!state.is(Tags.Blocks.ORES) || !isExposed(level, blockTarget)
                || tool.isEmpty() || !tool.isCorrectToolForDrops(state)) {
            stopWork();
            return;
        }
        pet.getLookControl().setLookAt(Vec3.atCenterOf(blockTarget));
        actionTicks++;
        float hardness = Math.max(0.1F, state.getDestroySpeed(level, blockTarget));
        float speed = Math.max(1.0F, tool.getDestroySpeed(state));
        int requiredTicks = Math.max(12, Math.min(200,
                (int)Math.ceil(hardness * 30.0F / speed * 20.0F)));
        int stage = Math.min(9, actionTicks * 10 / requiredTicks);
        level.destroyBlockProgress(pet.getId(), blockTarget, stage);
        if (actionTicks % 8 == 1) {
            pet.swing(InteractionHand.MAIN_HAND);
        }
        if (actionTicks < requiredTicks) {
            return;
        }
        List<ItemStack> drops = Block.getDrops(
                state, level, blockTarget, level.getBlockEntity(blockTarget), pet, tool);
        insertOrDrop(level, drops, blockTarget);
        level.setBlock(blockTarget, state.getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
        level.levelEvent(2001, blockTarget, Block.getId(state));
        inventory.damageStoredTool(toolSlot, 1);
        clearBreakingAnimation();
        announceWorkSuccess(WorkKind.MINING);
        stopWork();
    }

    private void placeTorch() {
        if (!(pet.level() instanceof ServerLevel level)
                || blockTarget == null || inventory == null
                || !level.isEmptyBlock(blockTarget)
                || !Blocks.TORCH.defaultBlockState().canSurvive(level, blockTarget)
                || !inventory.consumeOne(toolSlot)) {
            stopWork();
            return;
        }
        pet.getLookControl().setLookAt(Vec3.atCenterOf(blockTarget));
        pet.swing(InteractionHand.MAIN_HAND);
        level.setBlock(blockTarget, Blocks.TORCH.defaultBlockState(), Block.UPDATE_ALL);
        level.playSound(null, blockTarget, SoundEvents.WOOD_PLACE,
                SoundSource.BLOCKS, 0.7F, 1.0F);
        announceWorkSuccess(WorkKind.MINING);
        stopWork();
    }

    @Nullable
    private BlockPos findOre(ServerLevel level) {
        BlockPos origin = pet.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.betweenClosed(
                origin.offset(-SEARCH_RADIUS, -VERTICAL_RADIUS, -SEARCH_RADIUS),
                origin.offset(SEARCH_RADIUS, VERTICAL_RADIUS, SEARCH_RADIUS))) {
            BlockState state = level.getBlockState(candidate);
            if (!state.is(Tags.Blocks.ORES)
                    || state.getDestroySpeed(level, candidate) < 0.0F
                    || inventory == null || inventory.findMiningTool(state) < 0
                    || !isExposed(level, candidate)) {
                continue;
            }
            double distance = candidate.distSqr(origin);
            if (distance < bestDistance) {
                best = candidate.immutable();
                bestDistance = distance;
            }
        }
        return best;
    }

    @Nullable
    private BlockPos findTorchPosition(ServerLevel level) {
        BlockPos origin = pet.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.betweenClosed(
                origin.offset(-16, -VERTICAL_RADIUS, -16),
                origin.offset(16, VERTICAL_RADIUS, 16))) {
            if (!level.isEmptyBlock(candidate)
                    || level.getBrightness(LightLayer.BLOCK, candidate) >= 7
                    || !Blocks.TORCH.defaultBlockState().canSurvive(level, candidate)) {
                continue;
            }
            double distance = candidate.distSqr(origin);
            if (distance < bestDistance) {
                best = candidate.immutable();
                bestDistance = distance;
            }
        }
        return best;
    }

    private void insertOrDrop(
            ServerLevel level,
            List<ItemStack> stacks,
            BlockPos dropPosition) {
        if (inventory == null) {
            return;
        }
        for (ItemStack stack : stacks) {
            ItemStack remainder = inventory.insert(stack);
            if (!remainder.isEmpty()) {
                Block.popResource(level, dropPosition, remainder);
            }
        }
    }

    private void showTool() {
        if (inventory == null || toolSlot < 0
                || ChangedVanillaCompat.equipmentChangeRebuildsGoals(pet)) {
            return;
        }
        ItemStack tool = inventory.storageItem(toolSlot);
        if (tool.isEmpty()) {
            return;
        }
        ItemStack visual = tool.copy();
        visual.setCount(1);
        GatheringToolPresentation.show(pet, visual);
    }

    private void restoreTool() {
        GatheringToolPresentation.restore(pet);
    }

    private void moveToTarget() {
        if (destination != null) {
            pet.getNavigation().moveTo(
                    destination.x, destination.y, destination.z, SPEED);
        }
    }

    private void stopWork() {
        if (work == Work.FISHING
                && pet.level() instanceof ServerLevel level) {
            FishingVisualEffects.cancel(level, pet);
            FishingVisualEffects.setRodCastModel(pet, false);
        }
        clearBreakingAnimation();
        restoreTool();
        work = Work.NONE;
        pet.getNavigation().stop();
    }

    private void announceWorkSuccess(WorkKind kind) {
        ServerPlayer owner = LatexSocialMemory.getPetOwner(pet);
        if (owner != null) {
            CompanionWorkDialogue.announceSuccess(pet, owner, kind);
        }
    }

    private void clearTargets() {
        work = Work.NONE;
        fishingSite = null;
        blockTarget = null;
        destination = null;
        inventory = null;
        toolSlot = -1;
        actionTicks = 0;
        repathTicks = 0;
        workTicks = 0;
    }

    private void clearBreakingAnimation() {
        if (blockTarget != null && pet.level() instanceof ServerLevel level) {
            level.destroyBlockProgress(pet.getId(), blockTarget, -1);
        }
    }

    private boolean movementAvailable() {
        ServerPlayer owner = LatexSocialMemory.getPetOwner(pet);
        return owner != null && owner.isAlive() && !owner.isSpectator()
                && pet.isAlive() && !pet.isNoAi() && pet.getTarget() == null
                && !pet.isPassenger() && !pet.isLeashed()
                && !SocialAudienceGoal.isActive(pet)
                && !BondedSuitService.isSuitingOwner(pet, owner)
                && !ChangedAddonCompat.isGrabberBusy(pet)
                && HypnosisQteService.getActiveVictim(pet) == null;
    }

    private static boolean isExposed(ServerLevel level, BlockPos position) {
        for (Direction direction : Direction.values()) {
            BlockPos adjacent = position.relative(direction);
            if (level.isEmptyBlock(adjacent)
                    || !level.getFluidState(adjacent).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
