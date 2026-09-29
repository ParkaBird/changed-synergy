package net.parkabird.changedsynergy.ai;

import java.util.Comparator;
import java.util.EnumSet;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Member;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Outpost;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Role;
import net.parkabird.changedsynergy.ai.CreatureSettlementService.FishingSite;
import net.parkabird.changedsynergy.ai.CreatureSettlementService.GlowBerrySite;

/** Follows the owner's job order and delivers real foraged resources to the linked chest. */
public final class PlayerOutpostGoal extends Goal {
    private static final String CARGO = "ChangedSynergyPlayerOutpostCargo";
    private static final double SPEED = 0.35D;
    private static final String NEXT_NECTAR = "ChangedSynergyPlayerOutpostNextNectar";
    private enum Harvest { ORANGE, ORE, GLOW_BERRIES, SWEET_BERRIES, FISH, SEA_FISH, NECTAR }
    private final ChangedEntity mob;
    private Outpost outpost;
    private Member member;
    private Role startedRole;
    private ItemEntity pickup;
    private Harvest harvest;
    private BlockPos harvestBlock;
    private FishingSite fishingSite;
    private AbstractFish fish;
    private BlockState openedIce;
    private int harvestTicks;
    private BlockPos destination;
    private ServerPlayer crewOwner;
    private int repath;
    private int remainingTicks;
    private long nextAttemptTick;

    public PlayerOutpostGoal(ChangedEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mob.tickCount % 20 != Math.floorMod(mob.getId(), 20)
                || !(mob.level() instanceof ServerLevel level)) return false;
        outpost = PlayerOutpostData.get(level.getServer()).byMember(mob.getUUID()).orElse(null);
        if (outpost == null) return false;
        member = outpost.members.get(mob.getUUID());
        if (member == null || !available()) return false;
        if (member.role == Role.SUPPLY && level.getGameTime() < nextAttemptTick) return false;
        startedRole = member.role;
        crewOwner = null;
        if (member.role == Role.CREW) {
            crewOwner = level.getServer().getPlayerList().getPlayer(outpost.owner);
            if (crewOwner == null || crewOwner.level() != mob.level() || !crewOwner.isAlive()
                    || !PlayerOutpostService.authorized(mob, crewOwner)) return false;
            destination = crewOwner.blockPosition();
            return mob.distanceToSqr(crewOwner) > 16.0D;
        }
        if (!outpost.active(level)) return false;
        pickup = null;
        clearHarvest();
        destination = null;
        if (member.role == Role.SUPPLY && ChangedSynergyConfig.COMMON.playerOutpostSupplyWork.get()
                && validStorage(level) && !mob.isPassenger()) {
            if (!cargo(mob).isEmpty()) destination = outpost.storage;
            else pickup = nearbySupply(level);
            if (pickup != null) destination = pickup.blockPosition();
            else if (destination == null && (level.isDay() || !outpost.contains(mob.blockPosition()))) {
                if (outpost.marker.distSqr(mob.blockPosition()) > 18 * 18) {
                    destination = homeStand(level);
                } else if (ChangedSynergyConfig.COMMON.playerOutpostHarvesting.get()) chooseHarvest(level);
            }
        }
        if (destination == null) {
            if (member.role == Role.HOLD && member.holdPosition != null) destination = member.holdPosition;
            else if (member.role == Role.RETURN || member.role == Role.RETREAT || member.role == Role.GUARD
                    || !outpost.contains(mob.blockPosition())) destination = homeStand(level);
        }
        if (destination == null) {
            if (member.role == Role.SUPPLY) nextAttemptTick = level.getGameTime() + 100L;
            return false;
        }
        if (member.role == Role.RETURN && mob.distanceToSqr(Vec3.atCenterOf(destination)) < 16.0D) {
            PlayerOutpostData.get(level.getServer()).role(outpost, member.id, Role.RESIDENT, mob.blockPosition());
            return false;
        }
        return mob.distanceToSqr(Vec3.atCenterOf(destination)) > 5.0D || pickup != null
                || harvest != null
                || !cargo(mob).isEmpty();
    }

    @Override
    public boolean canContinueToUse() {
        return available() && member != null && outpost != null && member.role == startedRole
                && (harvest == null || ChangedSynergyConfig.COMMON.playerOutpostHarvesting.get())
                && (member.role != Role.SUPPLY || ChangedSynergyConfig.COMMON.playerOutpostSupplyWork.get()
                        || pickup == null && harvest == null && cargo(mob).isEmpty())
                && (member.role == Role.CREW || remainingTicks > 0)
                && destination != null && mob.level() instanceof ServerLevel level
                && (member.role == Role.CREW
                        ? crewOwner != null && crewOwner.isAlive() && crewOwner.level() == mob.level()
                                && PlayerOutpostService.authorized(mob, crewOwner)
                                && mob.distanceToSqr(crewOwner) > 4.0D
                        : outpost.active(level))
                && (pickup == null || pickup.isAlive())
                && (fish == null || fish.isAlive() && outpost.contains(fish.blockPosition()))
                && (member.role == Role.CREW
                        || mob.distanceToSqr(Vec3.atCenterOf(destination)) > 5.0D
                        || pickup != null || harvest != null || !cargo(mob).isEmpty());
    }

    @Override
    public void start() {
        repath = 0;
        remainingTicks = 400;
        harvestTicks = 0;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (!(mob.level() instanceof ServerLevel level) || destination == null) return;
        if (member.role != Role.CREW) remainingTicks--;
        if (member.role == Role.CREW && crewOwner != null) {
            destination = crewOwner.blockPosition();
            if (mob.distanceToSqr(crewOwner) > 24.0D * 24.0D
                    && BondedTeleportSafety.teleportNearOwner(level, mob, crewOwner)) return;
        }
        if (fish != null) destination = fish.blockPosition();
        if (pickup != null && mob.distanceToSqr(pickup) < 4.0D) {
            ItemStack stack = pickup.getItem();
            int count = Math.min(16, stack.getCount());
            ItemStack carried = stack.copyWithCount(count);
            stack.shrink(count);
            if (stack.isEmpty()) pickup.discard();
            else pickup.setItem(stack);
            setCargo(mob, carried);
            pickup = null;
            destination = outpost.storage;
            if (destination == null) {
                remainingTicks = 0;
                return;
            }
            repath = 0;
        }
        if (harvest != null && mob.distanceToSqr(Vec3.atCenterOf(destination)) < 8.0D) {
            if (++harvestTicks == 5) mob.swing(InteractionHand.MAIN_HAND);
            if (harvestTicks >= (harvest == Harvest.FISH ? 160 : 12)) {
                boolean gathered = harvest(level);
                if (fish != null) CreatureSettlementService.releaseSeaFishClaim(mob, fish);
                clearHarvest();
                if (!gathered) {
                    remainingTicks = 0;
                    return;
                }
                if (cargo(mob).isEmpty()) {
                    setCargo(mob, CreatureSettlementService.takeCargo(mob));
                }
                destination = outpost.storage;
                if (destination == null) {
                    remainingTicks = 0;
                    return;
                }
                remainingTicks = Math.max(remainingTicks, 300);
                repath = 0;
            }
        }
        if (member.role == Role.SUPPLY && !cargo(mob).isEmpty() && outpost.storage != null
                && outpost.dimension.equals(level.dimension().location().toString())
                && level.hasChunkAt(outpost.storage)
                && mob.distanceToSqr(Vec3.atCenterOf(outpost.storage)) < 7.0D) {
            Container container = PlayerOutpostData.container(level, outpost.storage);
            if (container != null) {
                ItemStack remainder = insert(container, cargo(mob));
                setCargo(mob, remainder);
                if (!remainder.isEmpty()) {
                    // Keep real cargo on the creature when the linked chest is full.
                    destination = null;
                    nextAttemptTick = level.getGameTime() + 200L;
                    mob.getNavigation().stop();
                    return;
                }
            }
            destination = homeStand(level);
            nextAttemptTick = level.getGameTime() + 100L;
        }
        if (member.role == Role.RETURN
                && mob.distanceToSqr(Vec3.atCenterOf(destination)) < 16.0D) {
            PlayerOutpostData.get(level.getServer()).role(outpost, member.id, Role.RESIDENT, mob.blockPosition());
            return;
        }
        if (--repath <= 0 || mob.getNavigation().isDone()) {
            repath = 10;
            mob.getNavigation().moveTo(destination.getX() + 0.5D, destination.getY(),
                    destination.getZ() + 0.5D, SPEED);
        }
    }

    @Override
    public void stop() {
        if (remainingTicks <= 0 && mob.level() instanceof ServerLevel level) {
            nextAttemptTick = level.getGameTime() + 200L;
        }
        mob.getNavigation().stop();
        if (fish != null) CreatureSettlementService.releaseSeaFishClaim(mob, fish);
        if (openedIce != null && mob.level() instanceof ServerLevel level) {
            CreatureSettlementService.restoreIceFishingHole(level, fishingSite, openedIce);
        }
        clearHarvest();
        pickup = null;
        destination = null;
        crewOwner = null;
        startedRole = null;
    }

    private boolean available() {
        return ChangedSynergyConfig.COMMON.playerOutposts.get() && mob.isAlive()
                && !mob.isNoAi()
                && (mob.getTarget() == null || member != null && member.role == Role.RETREAT)
                && !mob.isPassenger()
                && !mob.isLeashed() && !CreatureSettlementService.hasCargo(mob);
    }

    private boolean validStorage(ServerLevel level) {
        return PlayerOutpostData.container(level, outpost.storage) != null;
    }

    private void clearHarvest() {
        harvest = null;
        harvestBlock = null;
        fishingSite = null;
        fish = null;
        harvestTicks = 0;
    }

    private void chooseHarvest(ServerLevel level) {
        PlayerOutpostSupplyType type = PlayerOutpostSupplyType.of(mob);
        if (type == PlayerOutpostSupplyType.CAVE) {
            if (mob.getRandom().nextInt(5) != 0
                    && setHarvest(CreatureSettlementService.findExposedOre(mob, 8, 4)
                            .orElse(null), Harvest.ORE, null)) return;
            GlowBerrySite berries = CreatureSettlementService.findGlowBerrySite(mob, 8, 6, 4)
                    .orElse(null);
            if (berries != null && setHarvest(berries.berries(), Harvest.GLOW_BERRIES,
                    berries.stand())) return;
            setHarvest(CreatureSettlementService.findExposedOre(mob, 8, 4)
                    .orElse(null), Harvest.ORE, null);
        } else if (type == PlayerOutpostSupplyType.BERRIES) {
            setHarvest(CreatureSettlementService.findSweetBerryBush(mob, 10, 4)
                    .orElse(null), Harvest.SWEET_BERRIES, null);
        } else if (type == PlayerOutpostSupplyType.FISH) {
            if (CreatureSettlementService.usesOpenOceanHarvest(mob)) {
                fish = CreatureSettlementService.findSeaFish(mob, 12.0D).orElse(null);
                if (fish != null && outpost.contains(fish.blockPosition())) {
                    harvest = Harvest.SEA_FISH;
                    destination = fish.blockPosition();
                    return;
                }
                CreatureSettlementService.releaseSeaFishClaim(mob, fish);
                fish = null;
            }
            FishingSite site = CreatureSettlementService.findFishingSite(mob, 12, 4)
                    .orElse(null);
            if (site != null && outpost.contains(site.water())
                    && outpost.contains(site.stand())) {
                fishingSite = site;
                harvest = Harvest.FISH;
                destination = BlockPos.containing(CreatureSettlementService.fishingApproach(mob, site));
            }
        } else if (type == PlayerOutpostSupplyType.NECTAR) {
            if (level.getGameTime() < mob.getPersistentData().getLong(NEXT_NECTAR)) return;
            BlockPos origin = mob.blockPosition();
            BlockPos nearest = null;
            double best = Double.MAX_VALUE;
            for (BlockPos cursor : BlockPos.betweenClosed(origin.offset(-8, -3, -8),
                    origin.offset(8, 3, 8))) {
                if (level.hasChunkAt(cursor) && outpost.contains(cursor)
                        && level.getBlockState(cursor).is(BlockTags.FLOWERS)
                        && cursor.distSqr(origin) < best) {
                    nearest = cursor.immutable();
                    best = cursor.distSqr(origin);
                }
            }
            setHarvest(nearest, Harvest.NECTAR, null);
        } else if (type == PlayerOutpostSupplyType.FORAGE) {
            if (setHarvest(CreatureSettlementService.findSweetBerryBush(mob, 10, 4)
                    .orElse(null), Harvest.SWEET_BERRIES, null)) return;
            setHarvest(CreatureSettlementService.findOrangeLeaves(mob, 10, 4)
                    .orElse(null), Harvest.ORANGE, null);
        } else {
            setHarvest(CreatureSettlementService.findOrangeLeaves(mob, 10, 4)
                    .orElse(null), Harvest.ORANGE, null);
        }
    }

    private boolean setHarvest(BlockPos block, Harvest action, BlockPos stand) {
        if (block == null || !outpost.contains(block)) return false;
        BlockPos center = stand == null ? block : stand;
        BlockPos[] approaches = stand != null ? new BlockPos[]{center}
                : new BlockPos[]{center, center.below(), center.north(), center.south(),
                        center.east(), center.west(), center.above()};
        for (BlockPos approach : approaches) {
            if (!outpost.contains(approach)) continue;
            if (mob.distanceToSqr(Vec3.atCenterOf(approach)) > 8.0D) {
                var path = mob.getNavigation().createPath(approach, 1);
                if (path == null || !path.canReach()) continue;
            }
            harvest = action;
            harvestBlock = block;
            destination = approach;
            return true;
        }
        return false;
    }

    private boolean harvest(ServerLevel level) {
        if (harvest == Harvest.SEA_FISH) return fish != null
                && outpost.contains(fish.blockPosition())
                && CreatureSettlementService.captureSeaFish(mob, fish);
        if (harvest == Harvest.FISH) {
            if (fishingSite == null) return false;
            if (fishingSite.iceCovered()) {
                openedIce = CreatureSettlementService.openIceFishingHole(level, fishingSite);
                if (openedIce == null) return false;
            }
            boolean caught = CreatureSettlementService.catchFish(mob, fishingSite.water());
            CreatureSettlementService.restoreIceFishingHole(level, fishingSite, openedIce);
            openedIce = null;
            return caught;
        }
        if (harvestBlock == null || !outpost.contains(harvestBlock)) return false;
        return switch (harvest) {
            case ORANGE -> CreatureSettlementService.harvestOrangeLeaves(mob, harvestBlock);
            case ORE -> CreatureSettlementService.mineOre(mob, harvestBlock);
            case GLOW_BERRIES -> CreatureSettlementService.harvestGlowBerries(mob, harvestBlock);
            case SWEET_BERRIES -> CreatureSettlementService.harvestSweetBerries(mob, harvestBlock);
            case NECTAR -> {
                if (!level.getBlockState(harvestBlock).is(BlockTags.FLOWERS)) yield false;
                mob.getPersistentData().putLong(NEXT_NECTAR, level.getGameTime() + 2400L);
                level.playSound(null, harvestBlock, SoundEvents.BEE_POLLINATE,
                        SoundSource.NEUTRAL, 0.7F, 1.0F);
                level.sendParticles(ParticleTypes.COMPOSTER, harvestBlock.getX() + 0.5D,
                        harvestBlock.getY() + 0.5D, harvestBlock.getZ() + 0.5D,
                        5, 0.2D, 0.2D, 0.2D, 0.02D);
                setCargo(mob, new ItemStack(Items.HONEYCOMB));
                yield true;
            }
            default -> false;
        };
    }

    private BlockPos homeStand(ServerLevel level) {
        BlockPos marker = outpost.marker;
        BlockPos best = marker;
        double bestDistance = Double.MAX_VALUE;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos pos = marker.offset(dx, dy, dz);
                    if (!level.hasChunkAt(pos)
                            || !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                            || !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                            || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) continue;
                    double distance = mob.blockPosition().distSqr(pos);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    private ItemEntity nearbySupply(ServerLevel level) {
        AABB area = new AABB(outpost.marker).inflate(PlayerOutpostData.RADIUS, 8.0D, PlayerOutpostData.RADIUS);
        return level.getEntitiesOfClass(ItemEntity.class, area,
                        entity -> entity.isAlive() && entity.tickCount > 100
                                && outpost.contains(entity.blockPosition())
                                && accepted(entity.getItem()))
                .stream().min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
    }

    private static boolean accepted(ItemStack stack) {
        var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return stack.isEdible() || stack.is(ItemTags.LOGS) || stack.is(Items.STRING)
                || stack.is(Items.COAL) || stack.is(Items.COPPER_INGOT)
                || stack.is(Items.IRON_INGOT) || stack.is(Items.STICK)
                || key != null && key.getPath().equals("orange");
    }

    private static ItemStack insert(Container container, ItemStack source) {
        ItemStack remainder = source.copy();
        for (int pass = 0; pass < 2 && !remainder.isEmpty(); pass++) {
            for (int slot = 0; slot < container.getContainerSize() && !remainder.isEmpty(); slot++) {
                ItemStack stored = container.getItem(slot);
                if (!container.canPlaceItem(slot, remainder)
                        || pass == 0 && (stored.isEmpty() || !ItemStack.isSameItemSameTags(stored, remainder))
                        || pass == 1 && !stored.isEmpty()) continue;
                int free = Math.min(container.getMaxStackSize(), remainder.getMaxStackSize()) - stored.getCount();
                if (free <= 0) continue;
                int added = Math.min(free, remainder.getCount());
                if (stored.isEmpty()) container.setItem(slot, remainder.copyWithCount(added));
                else stored.grow(added);
                remainder.shrink(added);
            }
        }
        if (remainder.getCount() != source.getCount()) container.setChanged();
        return remainder;
    }

    private static ItemStack cargo(ChangedEntity mob) {
        return mob.getPersistentData().contains(CARGO)
                ? ItemStack.of(mob.getPersistentData().getCompound(CARGO)) : ItemStack.EMPTY;
    }

    private static void setCargo(ChangedEntity mob, ItemStack stack) {
        if (stack.isEmpty()) mob.getPersistentData().remove(CARGO);
        else mob.getPersistentData().put(CARGO, stack.save(new CompoundTag()));
    }

    public static ItemStack takeCargo(ChangedEntity mob) {
        ItemStack stack = cargo(mob);
        setCargo(mob, ItemStack.EMPTY);
        return stack;
    }

    public static boolean hasCargo(ChangedEntity mob) {
        return !cargo(mob).isEmpty();
    }
}
