package net.parkabird.changedsynergy.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.compat.DomesticationInnovationCompat;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Member;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Outpost;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Role;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Work;
import net.parkabird.changedsynergy.world.inventory.PlayerOutpostCargoInventory;

/** Finite, server-owned work using real blocks, native loot and a saved resource bag. */
public final class PlayerOutpostWorkGoal extends Goal {
    private static final String FARM_BATCH = "ChangedSynergyPlayerOutpostFarmBatch";
    private static final String HAUL_BATCH = "ChangedSynergyPlayerOutpostHaulBatch";
    private static final String OUTPUT = "ChangedSynergyPlayerOutpostWorkOutput";
    private static final String HUNT_OWNER = "ChangedSynergyHuntClaimedBy";
    private static final String HUNT_UNTIL = "ChangedSynergyHuntClaimedUntil";
    private static final int BAG_SLOTS = PlayerOutpostCargoInventory.WORK_SLOTS;
    private static final int BATCH_SIZE = 4;
    private static final int MAX_PATH_PROBES = 16;
    private static final double SPEED = 0.35D;
    // Loaded levels own short-lived claims; no chunk tickets or saved phantom inventory.
    private static final Map<ServerLevel, Map<BlockPos, Claim>> CLAIMS = new WeakHashMap<>();
    private record Claim(UUID worker, long until) {}
    private enum Action { DEPOSIT, PICKUP, FARM, HUNT, LOOT, FETCH_FOOD, COOK, COMPOST, FERTILIZE }

    private final ChangedEntity mob;
    private Outpost camp;
    private Member member;
    private Work startedWork;
    private Work startedBackup;
    private Action action;
    private BlockPos block;
    private BlockPos stand;
    private Animal prey;
    private ItemEntity pickup;
    private long nextScan;
    private boolean scanInitialized;
    private int remaining;
    private int repath;
    private int haulCursor;
    private int actionTicks;
    private int farmCursor;
    private int fertilizerCursor;
    private int pathProbes;
    private boolean outputPickup;

    public PlayerOutpostWorkGoal(ChangedEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public static boolean allowsGather(Member member) {
        return member.work == Work.GATHER || member.backupWork == Work.GATHER;
    }

    /** Shared by the old gather goal: supplies pause offline, at meals and under the worker cap. */
    public static boolean canWork(ChangedEntity mob, Outpost camp) {
        if (!PlayerOutpostService.operational(mob) || !(mob.level() instanceof ServerLevel level) || camp == null
                || PlayerOutpostData.get(level.getServer()).byMember(mob.getUUID()).orElse(null) != camp) return false;
        Member own = camp.members.get(mob.getUUID());
        if (!ChangedSynergyConfig.COMMON.playerOutpostSupplyWork.get()) {
            if (own != null) own.workStatus = "disabled";
            return false;
        }
        if (!ChangedSynergyConfig.COMMON.playerOutpostWorkWhileOwnerOffline.get()
                && level.getServer().getPlayerList().getPlayer(camp.owner) == null) {
            if (own != null) own.workStatus = "offline";
            return false;
        }
        if (PlayerOutpostMealGoal.wantsMeal(mob)) return false;
        return workerSlotAvailable(mob, camp);
    }

    /** Bound each group separately so a loaded home camp cannot starve its travelling crew. */
    static boolean workerSlotAvailable(ChangedEntity mob, Outpost camp) {
        if (!(mob.level() instanceof ServerLevel level)) return false;
        Member own = camp.members.get(mob.getUUID());
        Role group = own != null && own.role == Role.CREW ? Role.CREW : Role.SUPPLY;
        int limit = ChangedSynergyConfig.COMMON.playerOutpostActiveWorkers.get();
        int active = 0;
        for (Member resident : camp.members.values()) {
            if (resident.id.equals(mob.getUUID()) || resident.role != group) continue;
            if (level.getEntity(resident.id) instanceof ChangedEntity other
                    && other.goalSelector.getRunningGoals().anyMatch(goal ->
                            group == Role.CREW ? goal.getGoal() instanceof PlayerOutpostCrewGatherGoal
                                    : goal.getGoal() instanceof PlayerOutpostGoal
                                            || goal.getGoal() instanceof PlayerOutpostWorkGoal)) {
                if (++active >= limit) return false;
            }
        }
        return active < limit;
    }

    @Override
    public boolean canUse() {
        if (!(mob.level() instanceof ServerLevel level)) return false;
        int interval = Math.max(20, ChangedSynergyConfig.COMMON.playerOutpostWorkScanInterval.get());
        if (!scanInitialized) {
            scanInitialized = true;
            nextScan = level.getGameTime() + Math.floorMod(mob.getId(), interval);
        }
        if (level.getGameTime() < nextScan) return false;
        nextScan = level.getGameTime() + interval;
        camp = PlayerOutpostData.get(level.getServer()).byMember(mob.getUUID()).orElse(null);
        member = camp == null ? null : camp.members.get(mob.getUUID());
        if (!available(level) || !canWork(mob, camp)) return false;
        clearTarget();
        startedWork = member.work;
        startedBackup = member.backupWork;
        if (PlayerOutpostGoal.hasCargo(mob)) {
            List<ItemStack> bag = bag(mob);
            ItemStack remainder = add(bag, PlayerOutpostGoal.cargo(mob));
            saveBag(mob, bag);
            PlayerOutpostGoal.setCargo(mob, remainder);
        }
        if (hasCargo(mob)) {
            int harvested = mob.getPersistentData().getInt(FARM_BATCH);
            if (harvested > 0) {
                // Only another harvest can extend this batch; unload before switching jobs.
                if (harvested < BATCH_SIZE && bag(mob).size() < BAG_SLOTS
                        && ChangedSynergyConfig.COMMON.playerOutpostFarming.get()
                        && (member.work == Work.FARM || member.backupWork == Work.FARM)
                        && !mob.getPersistentData().contains(OUTPUT) && chooseFarm(level, false)) return true;
                mob.getPersistentData().putInt(FARM_BATCH, BATCH_SIZE);
                return chooseDeposit(level);
            }
            int hauled = mob.getPersistentData().getInt(HAUL_BATCH);
            if (hauled > 0) {
                if (hauled < BATCH_SIZE && bag(mob).size() < BAG_SLOTS
                        && (member.work == Work.HAUL || member.backupWork == Work.HAUL)
                        && !mob.getPersistentData().contains(OUTPUT) && chooseHaul(level)) return true;
                mob.getPersistentData().putInt(HAUL_BATCH, BATCH_SIZE);
                return chooseDeposit(level);
            }
            if (ChangedSynergyConfig.COMMON.playerOutpostCooking.get()
                    && (member.work == Work.COOK || member.backupWork == Work.COOK)
                    && !mob.getPersistentData().contains(OUTPUT) && chooseCook(level)) return true;
            return chooseDeposit(level);
        }
        mob.getPersistentData().remove(FARM_BATCH);
        mob.getPersistentData().remove(HAUL_BATCH);
        if (chooseOutput(level)) return true;
        if (chooseWork(level, member.work)) return true;
        if (member.backupWork != null && member.backupWork != member.work
                && chooseWork(level, member.backupWork)) return true;
        // An unavailable fallback must not hide the blocked route that exhausted this search.
        if (pathProbes >= MAX_PATH_PROBES) member.workStatus = "unreachable";
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (!(mob.level() instanceof ServerLevel level) || !available(level)
                || member.work != startedWork || member.backupWork != startedBackup
                || remaining <= 0 || action == null
                || !level.hasChunkAt(stand) || !currentTarget(level)) return false;
        if (!canWork(mob, camp)) return false;
        return switch (action) {
            case FARM, FERTILIZE -> ChangedSynergyConfig.COMMON.playerOutpostFarming.get();
            case COOK, FETCH_FOOD -> ChangedSynergyConfig.COMMON.playerOutpostCooking.get();
            case HUNT, LOOT -> ChangedSynergyConfig.COMMON.playerOutpostHunting.get();
            case COMPOST -> ChangedSynergyConfig.COMMON.playerOutpostFarming.get()
                    && ChangedSynergyConfig.COMMON.playerOutpostComposting.get();
            case PICKUP -> (outputPickup || ChangedSynergyConfig.COMMON.playerOutpostHauling.get())
                    && pickup != null && pickup.isAlive();
            default -> true;
        };
    }

    @Override
    public void start() {
        PlayerOutpostRestGoal.cleanup(mob);
        remaining = 600;
        actionTicks = 0;
        repath = 0;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (!(mob.level() instanceof ServerLevel level) || stand == null) return;
        // Reject stale callbacks after completion, interruption or a runtime setting change.
        if (!canContinueToUse()) { remaining = 0; return; }
        remaining--;
        if (action == Action.HUNT) {
            if (prey == null || !prey.isAlive()) {
                if (prey != null) stand = prey.blockPosition();
                action = Action.LOOT;
                actionTicks = 0;
            } else {
                if (!huntable(prey) || !camp.huntContains(prey.blockPosition())
                        || camp.farmContains(prey.blockPosition())
                        || prey.blockPosition().distSqr(camp.marker) < 64.0D
                        || !prey.getPersistentData().hasUUID(HUNT_OWNER)
                        || !mob.getUUID().equals(prey.getPersistentData().getUUID(HUNT_OWNER))
                        || !mayModify(level, prey.blockPosition())) { remaining = 0; return; }
                stand = prey.blockPosition();
                mob.getLookControl().setLookAt(prey, 30.0F, 30.0F);
                if (mob.distanceToSqr(prey) < 6.25D && ++actionTicks >= 20) {
                    actionTicks = 0;
                    mob.swing(InteractionHand.MAIN_HAND);
                    // Direct vanilla damage avoids Changed's assimilation/absorption attack path.
                    prey.hurt(mob.damageSources().mobAttack(mob),
                            (float)Math.max(1.0D, mob.getAttributeValue(Attributes.ATTACK_DAMAGE)));
                }
            }
        }
        boolean arrived = action == Action.PICKUP ? pickup != null && mob.distanceToSqr(pickup) <= 9.0D
                : mob.distanceToSqr(Vec3.atCenterOf(stand)) < 8.0D;
        if (arrived && action != Action.HUNT) {
            mob.getNavigation().stop();
            mob.getLookControl().setLookAt(Vec3.atCenterOf(block == null ? stand : block));
            if (++actionTicks == 5) mob.swing(InteractionHand.MAIN_HAND);
            if (actionTicks >= 12) {
                switch (action) {
                    case DEPOSIT -> deposit(level);
                    case PICKUP -> collectPickup(level);
                    case FARM -> {
                        if (!farm(level) && hasCargo(mob))
                            mob.getPersistentData().putInt(FARM_BATCH, BATCH_SIZE);
                    }
                    case COOK -> cook(level);
                    case FETCH_FOOD -> fetchFood(level);
                    case COMPOST -> compost(level);
                    case FERTILIZE -> fertilize(level);
                    case LOOT -> { if (actionTicks % 10 == 0) collectHuntLoot(level); }
                    default -> {}
                }
                if (action != Action.LOOT) remaining = 0;
            }
            return;
        }
        if (--repath <= 0) {
            repath = 20;
            PlayerOutpostGoal.walkTo(mob, stand, stand.getY(), SPEED);
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        // Interrupted batches return their saved cargo before starting another harvest.
        if (remaining > 0 || actionTicks < 12) {
            if (mob.getPersistentData().contains(FARM_BATCH)) mob.getPersistentData().putInt(FARM_BATCH, BATCH_SIZE);
            if (mob.getPersistentData().contains(HAUL_BATCH)) mob.getPersistentData().putInt(HAUL_BATCH, BATCH_SIZE);
        }
        if (mob.level() instanceof ServerLevel level && block != null) {
            Map<BlockPos, Claim> claims = CLAIMS.get(level);
            if (claims != null && claims.get(block) != null
                    && claims.get(block).worker.equals(mob.getUUID())) claims.remove(block);
        }
        CreatureSettlementService.releaseHuntClaim(mob, prey);
        clearTarget();
    }

    private boolean available(ServerLevel level) {
        return PlayerOutpostService.operational(mob) && camp != null && member != null
                && member.role == Role.SUPPLY && camp.active(level) && mob.isAlive() && !mob.isNoAi()
                && camp.members.get(mob.getUUID()) == member
                && mob.getTarget() == null && !mob.isSleeping()
                && (!mob.isPassenger() || PlayerOutpostRestGoal.isResting(mob)) && !mob.isLeashed()
                && !mob.isVehicle() && !TakeoverService.carrying(mob)
                && !net.parkabird.changedsynergy.compat.ChangedAddonCompat.isGrabberBusy(mob)
                && net.ltxprogrammer.changed.ability.GrabEntityAbility.getGrabber(mob) == null
                && !SocialAudienceGoal.isActive(mob) && HypnosisQteService.getActiveVictim(mob) == null
                && !mob.getPersistentData().hasUUID("ChangedSynergyRestOwner")
                && !PlayerOutpostGoal.onCampfire(mob)
                && !CreatureSettlementService.hasCargo(mob);
    }

    private void clearTarget() { action = null; block = null; stand = null; prey = null; pickup = null; outputPickup = false; pathProbes = 0; }

    private boolean chooseWork(ServerLevel level, Work work) {
        return switch (work) {
            case FARM, COMPOST -> ChangedSynergyConfig.COMMON.playerOutpostFarming.get()
                    && (chooseFarm(level, false)
                            || count(PlayerOutpostData.container(level, camp.storage), new ItemStack(Items.BONE_MEAL)) > 0
                                    && chooseFarm(level, true)
                            || ChangedSynergyConfig.COMMON.playerOutpostComposting.get() && chooseCompost(level));
            case HUNT -> ChangedSynergyConfig.COMMON.playerOutpostHunting.get() && chooseHunt(level);
            case COOK -> ChangedSynergyConfig.COMMON.playerOutpostCooking.get() && chooseCook(level);
            case HAUL -> chooseHaul(level);
            case GATHER -> false;
        };
    }

    private boolean chooseDeposit(ServerLevel level) {
        if (PlayerOutpostData.container(level, camp.storage) == null) { member.workStatus = "no_storage"; return false; }
        return target(level, camp.storage, Action.DEPOSIT, "returning");
    }

    private void deposit(ServerLevel level) {
        if (block == null || !block.equals(camp.storage) || !level.hasChunkAt(block)
                || !camp.contains(block) || mob.distanceToSqr(Vec3.atCenterOf(block)) > 16.0D) return;
        Container chest = PlayerOutpostData.container(level, camp.storage);
        if (chest == null) { member.workStatus = "no_storage"; return; }
        List<ItemStack> remaining = new ArrayList<>();
        boolean delivered = false;
        for (ItemStack stack : bag(mob)) {
            ItemStack remainder = PlayerOutpostGoal.insert(chest, stack);
            if (remainder.getCount() < stack.getCount()) {
                delivered = true;
                PlayerOutpostStorageAccess.touch(mob, camp.storage);
            }
            if (!remainder.isEmpty()) remaining.add(remainder);
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(camp.owner);
        if (delivered && owner != null) net.parkabird.changedsynergy.advancement.SynergyAdvancements.grant(owner,
                net.parkabird.changedsynergy.advancement.SynergyAdvancements.CAMP_DELIVERY);
        saveBag(mob, remaining);
        member.workStatus = remaining.isEmpty() ? "idle" : "full_storage";
        if (!remaining.isEmpty()) nextScan = level.getGameTime() + 200L;
    }

    private boolean chooseHaul(ServerLevel level) {
        if (!ChangedSynergyConfig.COMMON.playerOutpostHauling.get()) { member.workStatus = "disabled"; return false; }
        if (PlayerOutpostData.container(level, camp.storage) == null) { member.workStatus = "no_storage"; return false; }
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, camp.haulBounds(), this::haulableDrop)
                .stream().sorted(Comparator.comparingDouble(mob::distanceToSqr)).toList();
        if (drops.isEmpty()) { haulCursor = 0; member.workStatus = "no_resources"; return false; }
        int start = Math.floorMod(haulCursor, drops.size());
        for (int offset = 0; offset < Math.min(32, drops.size()) && pathProbes < MAX_PATH_PROBES; offset++) {
            pickup = drops.get((start + offset) % drops.size());
            haulCursor = (start + offset + 1) % drops.size();
            if (target(level, pickup.blockPosition(), Action.PICKUP, "hauling")) { haulCursor = 0; return true; }
        }
        pickup = null;
        return false;
    }

    private boolean haulableDrop(ItemEntity item) {
        return item.isAlive() && item.tickCount >= 100 && !item.hasPickUpDelay()
                && camp.haulContains(item.blockPosition()) && !item.getItem().isEmpty()
                && !ChangedSynergyConfig.COMMON.playerOutpostHaulingBlockedItems.get()
                        .contains(ForgeRegistries.ITEMS.getKey(item.getItem().getItem()).toString());
    }

    private void collectPickup(ServerLevel level) {
        if (pickup == null || !pickup.isAlive() || mob.distanceToSqr(pickup) > 9.0D
                || !outputPickup && !haulableDrop(pickup)) return;
        List<ItemStack> bag = bag(mob);
        ItemStack source = pickup.getItem();
        int allowed = outputPickup && mob.getPersistentData().contains(OUTPUT)
                ? Math.min(source.getCount(), mob.getPersistentData().getCompound(OUTPUT).getInt("Count"))
                : source.getCount();
        ItemStack offered = source.copyWithCount(allowed);
        ItemStack remainder = add(bag, offered);
        int moved = allowed - remainder.getCount();
        if (moved == 0) return;
        saveBag(mob, bag);
        ItemStack left = source.copy();
        left.shrink(moved);
        if (left.isEmpty()) pickup.discard(); else pickup.setItem(left);
        if (outputPickup && mob.getPersistentData().contains(OUTPUT)) {
            CompoundTag output = mob.getPersistentData().getCompound(OUTPUT);
            output.putInt("Count", Math.max(0, output.getInt("Count") - moved));
            if (output.getInt("Count") <= 0) mob.getPersistentData().remove(OUTPUT);
        }
        if (!outputPickup) {
            mob.getPersistentData().putInt(HAUL_BATCH,
                    Math.min(BATCH_SIZE, mob.getPersistentData().getInt(HAUL_BATCH) + 1));
            nextScan = level.getGameTime();
        }
        member.workStatus = outputPickup ? "returning" : "hauling";
    }

    private boolean chooseFarm(ServerLevel level, boolean fertilizer) {
        if (camp.farmStart == null || camp.farmEnd == null) { member.workStatus = "no_farm"; return false; }
        if (!ForgeEventFactory.getMobGriefingEvent(level, mob)) { member.workStatus = "protected"; return false; }
        int radius = PlayerOutpostData.WORK_RADIUS;
        int minX = Math.max(camp.marker.getX() - radius, Math.min(camp.farmStart.getX(), camp.farmEnd.getX()));
        int minY = Math.max(camp.marker.getY() - radius, Math.min(camp.farmStart.getY(), camp.farmEnd.getY()) - 1);
        int minZ = Math.max(camp.marker.getZ() - radius, Math.min(camp.farmStart.getZ(), camp.farmEnd.getZ()));
        int sizeX = Math.min(camp.marker.getX() + radius, Math.max(camp.farmStart.getX(), camp.farmEnd.getX())) - minX + 1;
        int sizeY = Math.min(camp.marker.getY() + radius, Math.max(camp.farmStart.getY(), camp.farmEnd.getY()) + 3) - minY + 1;
        int sizeZ = Math.min(camp.marker.getZ() + radius, Math.max(camp.farmStart.getZ(), camp.farmEnd.getZ())) - minZ + 1;
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) return false;
        int total = sizeX * sizeY * sizeZ;
        // Bounded rotating scans, including large farms, avoid a server-wide spike.
        for (int scan = 0; scan < Math.min(512, total) && pathProbes < MAX_PATH_PROBES; scan++) {
            int index = Math.floorMod(fertilizer ? fertilizerCursor++ : farmCursor++, total);
            BlockPos cursor = new BlockPos(minX + index % sizeX,
                    minY + index / sizeX % sizeY, minZ + index / (sizeX * sizeY));
            if (!level.hasChunkAt(cursor) || !camp.farmContains(cursor)) continue;
            BlockState state = level.getBlockState(cursor);
            if (fertilizer) {
                if (!(state.getBlock() instanceof BonemealableBlock growable)
                        || !growable.isValidBonemealTarget(level, cursor, state, false)
                        || !(state.getBlock() instanceof CropBlock) && !fdBlock(state, "rice")
                                && !fdBlock(state, "budding_tomatoes")) continue;
            } else if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) continue;
            if (target(level, cursor.immutable(), fertilizer ? Action.FERTILIZE : Action.FARM,
                    fertilizer ? "fertilizing" : "harvesting")) return true;
        }
        member.workStatus = pathProbes >= MAX_PATH_PROBES ? "unreachable" : "waiting_crops";
        return false;
    }

    private boolean farm(ServerLevel level) {
        if (block == null || !camp.farmContains(block) || !level.hasChunkAt(block)) return false;
        BlockState state = level.getBlockState(block);
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state) || !mayModify(level, block)) return false;
        if (fdBlock(state, "tomatoes") || fdBlock(state, "tomatoes_on_rope")) {
            harvestTomatoes(level, state);
            if (level.getBlockState(block).equals(state)) return false;
            harvested(level);
            return true;
        }
        List<ItemStack> drops = new ArrayList<>(Block.getDrops(state, level, block,
                level.getBlockEntity(block), mob, mob.getMainHandItem()));
        if (fdBlock(state, "rice_panicles")) {
            if (!fdBlock(level.getBlockState(block.below()), "rice") || !mayModify(level, block.below())) return false;
            // Farmer's Delight retains its waterlogged root and grows a new panicle naturally.
            if (!level.setBlock(block, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)) return false;
            for (ItemStack stack : drops) carryOrDrop(level, stack, block);
            level.levelEvent(2001, block, Block.getId(state));
            harvested(level);
            return true;
        }
        ItemStack seed = crop.getCloneItemStack(level, block, state);
        if (seed.isEmpty() || !crop.getStateForAge(0).canSurvive(level, block)) return false;
        Container chest = PlayerOutpostData.container(level, camp.storage);
        int seedDrop = find(drops, seed);
        int seedSlot = seedDrop < 0 ? find(chest, seed, 0) : -1;
        if (seedDrop < 0 && seedSlot < 0) { member.workStatus = "no_seeds"; return false; }
        // Reserve actual planting material before altering a block. A failed set keeps every item.
        ItemStack planting = seedDrop >= 0 ? drops.get(seedDrop).split(1) : chest.removeItem(seedSlot, 1);
        if (planting.isEmpty()) return false;
        if (seedDrop < 0) PlayerOutpostStorageAccess.touch(mob, camp.storage);
        if (!level.setBlock(block, crop.getStateForAge(0), Block.UPDATE_ALL)) {
            if (seedDrop < 0) carryOrDrop(level, planting, block);
            return false;
        }
        if (seedDrop < 0) chest.setChanged();
        for (ItemStack stack : drops) carryOrDrop(level, stack, block);
        level.levelEvent(2001, block, Block.getId(state));
        harvested(level);
        return true;
    }

    private void harvested(ServerLevel level) {
        if (hasCargo(mob)) mob.getPersistentData().putInt(FARM_BATCH,
                Math.min(BATCH_SIZE, mob.getPersistentData().getInt(FARM_BATCH) + 1));
        member.workStatus = "harvesting";
        // Run the bounded crop scan at the next goal evaluation rather than waiting a full work interval.
        nextScan = level.getGameTime();
    }

    private void harvestTomatoes(ServerLevel level, BlockState state) {
        var player = FakePlayerFactory.getMinecraft(level);
        ItemStack previous = player.getMainHandItem();
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            state.use(level, player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(block), Direction.UP, block, false));
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
        }
        // The native action retains ropes/vines, rolls its own drops and plays the native sound.
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(block).inflate(1.5D),
                entity -> entity.isAlive() && entity.tickCount == 0 && unownedDrop(entity))) {
            List<ItemStack> bag = bag(mob);
            ItemStack remainder = add(bag, item.getItem());
            saveBag(mob, bag);
            if (remainder.isEmpty()) item.discard(); else item.setItem(remainder);
        }
    }

    private static boolean fdBlock(BlockState state, String path) {
        var key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return key != null && key.getNamespace().equals("farmersdelight") && key.getPath().equals(path);
    }

    private boolean chooseHunt(ServerLevel level) {
        if (camp.huntStart == null || camp.huntEnd == null) { member.workStatus = "no_hunting"; return false; }
        if (!ForgeEventFactory.getMobGriefingEvent(level, mob)) { member.workStatus = "protected"; return false; }
        if (mob.getPersistentData().getLong("ChangedSynergyNextHunt") > level.getGameTime()) return false;
        List<Animal> animals = level.getEntitiesOfClass(Animal.class,
                new AABB(camp.huntStart).minmax(new AABB(camp.huntEnd)).inflate(0, 3, 0), PlayerOutpostWorkGoal::huntable);
        for (Animal candidate : animals.stream().sorted(Comparator.comparingDouble(mob::distanceToSqr)).limit(32).toList()) {
            if (!camp.huntContains(candidate.blockPosition()) || camp.farmContains(candidate.blockPosition())
                    || candidate.blockPosition().distSqr(camp.marker) < 64.0D
                    || animals.stream().filter(other -> other.getType() == candidate.getType()
                            && other.distanceToSqr(candidate) < 24.0D * 24.0D).count() < 3) continue;
            CompoundTag data = candidate.getPersistentData();
            if (data.getLong(HUNT_UNTIL) > level.getGameTime() && data.hasUUID(HUNT_OWNER)
                    && !data.getUUID(HUNT_OWNER).equals(mob.getUUID())) continue;
            if (!target(level, candidate.blockPosition(), Action.HUNT, "hunting")) continue;
            prey = candidate;
            data.putUUID(HUNT_OWNER, mob.getUUID());
            data.putLong(HUNT_UNTIL, level.getGameTime() + 640L);
            return true;
        }
        member.workStatus = "no_prey";
        return false;
    }

    static boolean huntable(Animal animal) {
        EntityType<?> type = animal.getType();
        return (type == EntityType.COW || type == EntityType.SHEEP || type == EntityType.PIG
                || type == EntityType.RABBIT || type == EntityType.CHICKEN) && animal.isAlive()
                && !animal.isBaby() && !animal.hasCustomName() && !animal.isLeashed()
                && !animal.isPassenger() && !animal.isInvulnerable() && !animal.isInLove()
                && DomesticationInnovationCompat.ownerUuid(animal) == null
                && (!(animal instanceof TamableAnimal tame) || !tame.isTame());
    }

    private void collectHuntLoot(ServerLevel level) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(stand).inflate(3.0D),
                entity -> entity.isAlive() && entity.tickCount < 100 && unownedDrop(entity)
                        && !entity.getItem().isEmpty())) {
            List<ItemStack> bag = bag(mob);
            ItemStack remainder = add(bag, item.getItem());
            saveBag(mob, bag);
            if (remainder.isEmpty()) item.discard(); else item.setItem(remainder);
        }
        if (actionTicks >= 40) {
            mob.getPersistentData().putLong("ChangedSynergyNextHunt", level.getGameTime() + 2400L);
            member.workStatus = "returning";
            remaining = 0;
        }
    }

    private boolean chooseCook(ServerLevel level) {
        if (mob.getPersistentData().contains(OUTPUT)) { member.workStatus = "waiting_food"; return false; }
        if (!ForgeEventFactory.getMobGriefingEvent(level, mob)) { member.workStatus = "protected"; return false; }
        if (camp.cooking == null || !level.hasChunkAt(camp.cooking)
                || !(level.getBlockEntity(camp.cooking) instanceof CampfireBlockEntity fire)) {
            member.workStatus = "no_campfire";
            return false;
        }
        BlockState state = level.getBlockState(camp.cooking);
        if (!state.hasProperty(CampfireBlock.LIT) || !state.getValue(CampfireBlock.LIT)) {
            member.workStatus = "fire_unlit";
            return false;
        }
        if (fire.getItems().stream().noneMatch(ItemStack::isEmpty)) {
            member.workStatus = "fire_full";
            return false;
        }
        boolean found = bag(mob).stream().anyMatch(stack -> cookingRecipe(fire, stack) != null);
        if (!found) {
            Container chest = PlayerOutpostData.container(level, camp.storage);
            if (chest != null) for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                ItemStack stack = chest.getItem(slot);
                if (cookingRecipe(fire, stack) != null && count(chest, stack) > camp.rawReserve)
                    return target(level, camp.storage, Action.FETCH_FOOD, "fetching_food");
            }
        }
        if (!found) { member.workStatus = "no_resources"; return false; }
        return target(level, camp.cooking, Action.COOK, "cooking");
    }

    private void fetchFood(ServerLevel level) {
        if (!block.equals(camp.storage)) return;
        Container chest = PlayerOutpostData.container(level, block);
        if (chest == null) return;
        List<ItemStack> items = bag(mob);
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack source = chest.getItem(slot);
            if (campfireRecipe(level, source).isEmpty() || count(chest, source) <= camp.rawReserve) continue;
            int amount = Math.min(4, Math.min(source.getCount(), count(chest, source) - camp.rawReserve));
            ItemStack remainder = add(items, source.copyWithCount(amount));
            int moved = amount - remainder.getCount();
            if (moved > 0) {
                chest.removeItem(slot, moved);
                chest.setChanged();
                saveBag(mob, items);
                PlayerOutpostStorageAccess.touch(mob, block);
            }
            return;
        }
    }

    private CampfireCookingRecipe cookingRecipe(CampfireBlockEntity fire, ItemStack stack) {
        return campfireRecipe(mob.level(), stack).orElse(null);
    }

    /** Food-only smelting recipes can finish in a cook's real campfire slots. */
    public static java.util.Optional<CampfireCookingRecipe> campfireRecipe(Level level, ItemStack stack) {
        if (stack.isEmpty()) return java.util.Optional.empty();
        var input = new SimpleContainer(stack);
        var nativeRecipe = level.getRecipeManager().getRecipeFor(RecipeType.CAMPFIRE_COOKING, input, level)
                .filter(recipe -> recipe.getResultItem(level.registryAccess()).isEdible());
        if (nativeRecipe.isPresent()) return nativeRecipe;
        return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, input, level)
                .filter(recipe -> recipe.getResultItem(level.registryAccess()).isEdible()
                        && !ItemStack.isSameItemSameTags(stack, recipe.getResultItem(level.registryAccess())))
                .map(recipe -> new CampfireCookingRecipe(recipe.getId(), recipe.getGroup(), CookingBookCategory.FOOD,
                        Ingredient.of(stack.getItem()), recipe.assemble(input, level.registryAccess()),
                        0.0F, Math.max(1, recipe.getCookingTime()) * 3));
    }

    private void cook(ServerLevel level) {
        if (block == null || !block.equals(camp.cooking) || !camp.contains(block) || !level.hasChunkAt(block)) return;
        if (!(level.getBlockEntity(block) instanceof CampfireBlockEntity fire)
                || !level.getBlockState(block).getValue(CampfireBlock.LIT) || !mayModify(level, block)) return;
        List<ItemStack> bag = bag(mob);
        int slot = -1;
        for (int i = 0; i < bag.size(); i++) if (cookingRecipe(fire, bag.get(i)) != null) { slot = i; break; }
        if (slot < 0) return;
        ItemStack source = bag.get(slot);
        CampfireCookingRecipe recipe = cookingRecipe(fire, source);
        int placed = 0;
        if (recipe != null) for (int i = 0; i < 4 && !source.isEmpty(); i++) {
            if (!fire.placeFood(mob, source, recipe.getCookingTime())) break;
            fire.getPersistentData().putBoolean("ChangedSynergyCampFood", true);
            placed++;
        }
        if (placed > 0) {
            ItemStack result = recipe.getResultItem(level.registryAccess());
            rememberOutput(level, block, result.copyWithCount(result.getCount() * placed), recipe.getCookingTime() + 1200);
            member.workStatus = "waiting_food";
        }
        saveBag(mob, bag);
    }

    private boolean chooseCompost(ServerLevel level) {
        if (!ForgeEventFactory.getMobGriefingEvent(level, mob)) { member.workStatus = "protected"; return false; }
        Container chest = PlayerOutpostData.container(level, camp.storage);
        if (chest == null) { member.workStatus = "no_storage"; return false; }
        if (camp.composter == null || !level.hasChunkAt(camp.composter)
                || !level.getBlockState(camp.composter).is(Blocks.COMPOSTER)) { member.workStatus = "no_composter"; return false; }
        if (compostReady(level)) return target(level, camp.composter, Action.COMPOST, "composting");
        member.workStatus = "no_resources";
        return false;
    }

    private boolean compostReady(ServerLevel level) {
        if (camp.composter == null || !level.hasChunkAt(camp.composter)) return false;
        BlockState state = level.getBlockState(camp.composter);
        if (!state.is(Blocks.COMPOSTER)) return false;
        int fill = state.getValue(ComposterBlock.LEVEL);
        // Level seven is ripening, not accepting input. Recheck surplus on arrival as well as selection.
        return fill == 8 || fill < 7 && compostSlot(PlayerOutpostData.container(level, camp.storage)) >= 0;
    }

    private int compostSlot(Container chest) {
        if (chest == null) return -1;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (stack.isEmpty() || !ComposterBlock.COMPOSTABLES.containsKey(stack.getItem())) continue;
            // Only seed surplus is disposable by default; preserve edible reserves and valuables.
            String path = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).getPath();
            if ((path.endsWith("_seeds") || path.equals("seeds")) && count(chest, stack) > camp.seedReserve) return slot;
        }
        return -1;
    }

    private void compost(ServerLevel level) {
        if (block == null || !block.equals(camp.composter) || !camp.contains(block) || !level.hasChunkAt(block)
                || mob.distanceToSqr(Vec3.atCenterOf(block)) > 16.0D || !compostReady(level)) return;
        BlockState state = level.getBlockState(block);
        if (!state.is(Blocks.COMPOSTER) || !mayModify(level, block)) return;
        if (state.getValue(ComposterBlock.LEVEL) == 8) {
            AABB area = new AABB(block).inflate(2);
            var existing = new java.util.HashSet<UUID>();
            level.getEntitiesOfClass(ItemEntity.class, area).forEach(item -> existing.add(item.getUUID()));
            ComposterBlock.extractProduce(mob, state, level, block);
            int left = 0;
            // Take only this extraction's real output, before it scatters or a search expires.
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area,
                    drop -> drop.isAlive() && !existing.contains(drop.getUUID()) && drop.getItem().is(Items.BONE_MEAL))) {
                ItemStack remainder = acceptCargo(mob, item.getItem());
                if (remainder.isEmpty()) item.discard();
                else { item.setItem(remainder); left += remainder.getCount(); }
            }
            if (left > 0) rememberOutput(level, block, new ItemStack(Items.BONE_MEAL, left), 1200);
            nextScan = level.getGameTime();
            member.workStatus = hasCargo(mob) ? "returning" : "composting";
            return;
        }
        if (state.getValue(ComposterBlock.LEVEL) >= 7) return;
        Container chest = PlayerOutpostData.container(level, camp.storage);
        int slot = compostSlot(chest);
        if (slot < 0) return;
        ItemStack ingredient = chest.removeItem(slot, 1);
        if (ingredient.isEmpty()) return;
        PlayerOutpostStorageAccess.touch(mob, camp.storage);
        int before = ingredient.getCount();
        BlockState changed = ComposterBlock.insertItem(mob, state, level, ingredient, block);
        // Vanilla may consume a real seed without raising the fill level; declined insertions emit no effect.
        if (ingredient.getCount() < before) level.levelEvent(1500, block, changed != state ? 1 : 0);
        chest.setChanged();
        if (!ingredient.isEmpty()) carryOrDrop(level, ingredient, block);
    }

    private void fertilize(ServerLevel level) {
        if (block == null || !camp.farmContains(block) || !level.hasChunkAt(block)) return;
        if (!mayModify(level, block)) return;
        Container chest = PlayerOutpostData.container(level, camp.storage);
        int slot = find(chest, new ItemStack(Items.BONE_MEAL), 0);
        if (slot < 0) return;
        ItemStack fertilizer = chest.removeItem(slot, 1);
        if (fertilizer.isEmpty()) return;
        PlayerOutpostStorageAccess.touch(mob, camp.storage);
        if (BoneMealItem.growCrop(fertilizer, level, block)) level.levelEvent(1505, block, 0);
        chest.setChanged();
        if (!fertilizer.isEmpty()) carryOrDrop(level, fertilizer, block);
    }

    private void rememberOutput(ServerLevel level, BlockPos position, ItemStack item, int ticks) {
        CompoundTag output = new CompoundTag();
        output.putLong("Pos", position.asLong());
        output.putString("Dimension", level.dimension().location().toString());
        output.put("Item", item.copyWithCount(1).save(new CompoundTag()));
        output.putInt("Count", item.getCount());
        output.putLong("Until", level.getGameTime() + Math.max(200, ticks));
        mob.getPersistentData().put(OUTPUT, output);
    }

    private boolean chooseOutput(ServerLevel level) {
        if (!mob.getPersistentData().contains(OUTPUT)) return false;
        CompoundTag output = mob.getPersistentData().getCompound(OUTPUT);
        if (output.getLong("Until") <= level.getGameTime()) { mob.getPersistentData().remove(OUTPUT); return false; }
        if (!output.getString("Dimension").equals(level.dimension().location().toString())) return false;
        BlockPos origin = BlockPos.of(output.getLong("Pos"));
        ItemStack expected = ItemStack.of(output.getCompound("Item"));
        if (!level.hasChunkAt(origin)) return false;
        pickup = level.getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(2.0D),
                        item -> item.isAlive() && unownedDrop(item)
                                && ItemStack.isSameItemSameTags(expected, item.getItem()))
                .stream().min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
        if (pickup == null) {
            // A hauler/player may have taken the real output; never wait for phantom food.
            if (!(level.getBlockEntity(origin) instanceof CampfireBlockEntity fire)
                    || fire.getItems().stream().allMatch(ItemStack::isEmpty)) mob.getPersistentData().remove(OUTPUT);
            return false;
        }
        if (target(level, pickup.blockPosition(), Action.PICKUP, "hauling")) {
            outputPickup = true;
            return true;
        }
        pickup = null;
        return false;
    }

    private boolean target(ServerLevel level, BlockPos position, Action type, String status) {
        if (position == null || !level.hasChunkAt(position) || !camp.workContains(position)) return false;
        Map<BlockPos, Claim> claims = CLAIMS.computeIfAbsent(level, ignored -> new java.util.HashMap<>());
        claims.entrySet().removeIf(entry -> entry.getValue().until <= level.getGameTime());
        Claim existing = claims.get(position);
        if (existing != null && !existing.worker.equals(mob.getUUID())) return false;
        if (claims.size() >= 1024) return false;
        BlockPos approach = approach(level, position);
        if (approach == null) { member.workStatus = "unreachable"; return false; }
        block = position.immutable();
        stand = approach;
        action = type;
        member.workStatus = status;
        claims.put(block, new Claim(mob.getUUID(), level.getGameTime() + 640L));
        return true;
    }

    private BlockPos approach(ServerLevel level, BlockPos position) {
        return approach(mob, camp, level, position, this);
    }

    public static BlockPos approach(ChangedEntity mob, Outpost camp, ServerLevel level, BlockPos position) {
        return approach(mob, camp, level, position, null);
    }

    private static BlockPos approach(ChangedEntity mob, Outpost camp, ServerLevel level, BlockPos position,
                                     PlayerOutpostWorkGoal search) {
        if (position == null) return null;
        for (int dy = 0; dy >= -3; dy--) for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = position.relative(direction).offset(0, dy, 0);
            if (!level.hasChunkAt(candidate) || !camp.workContains(candidate)
                    || !level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty()
                    || !level.getBlockState(candidate.above()).getCollisionShape(level, candidate.above()).isEmpty()
                    || level.getBlockState(candidate.below()).getCollisionShape(level, candidate.below()).isEmpty()
                    || PlayerOutpostGoal.onCampfire(level, mob.getBoundingBox().move(Vec3.atBottomCenterOf(candidate).subtract(mob.position())))) continue;
            if (mob.distanceToSqr(Vec3.atCenterOf(candidate)) < 8.0D) return candidate;
            // Share the budget across every crop/prey candidate in this work search, not per block.
            if (search != null) {
                if (search.pathProbes >= MAX_PATH_PROBES) return null;
                search.pathProbes++;
            }
            var path = mob.getNavigation().createPath(candidate, 0);
            if (PlayerOutpostGoal.workRoute(mob, candidate, path)) return candidate;
        }
        return null;
    }

    private boolean mayModify(ServerLevel level, BlockPos position) {
        return level.hasChunkAt(position) && ForgeEventFactory.getMobGriefingEvent(level, mob)
                && ForgeEventFactory.onEntityDestroyBlock(mob, position, level.getBlockState(position));
    }

    private boolean currentTarget(ServerLevel level) {
        if (block == null || action == null || !level.hasChunkAt(block) || !camp.workContains(block)) return false;
        return switch (action) {
            case DEPOSIT, FETCH_FOOD -> block.equals(camp.storage);
            case COOK -> block.equals(camp.cooking) && level.getBlockEntity(block) instanceof CampfireBlockEntity;
            case COMPOST -> block.equals(camp.composter) && compostReady(level);
            case FARM, FERTILIZE -> camp.farmContains(block);
            case HUNT -> prey != null && (!prey.isAlive() || camp.huntContains(prey.blockPosition())
                    && !camp.farmContains(prey.blockPosition()) && prey.blockPosition().distSqr(camp.marker) >= 64.0D);
            case LOOT -> stand != null && camp.huntContains(stand);
            case PICKUP -> pickup != null && (outputPickup ? pickup.isAlive() && camp.contains(pickup.blockPosition())
                    : haulableDrop(pickup));
        };
    }

    private void carryOrDrop(ServerLevel level, ItemStack source, BlockPos position) {
        if (source.isEmpty()) return;
        List<ItemStack> bag = bag(mob);
        ItemStack remainder = add(bag, source);
        saveBag(mob, bag);
        if (!remainder.isEmpty()) Block.popResource(level, position, remainder);
    }

    private static int count(Container container, ItemStack item) {
        int count = 0;
        if (container != null) for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stored = container.getItem(slot);
            if (ItemStack.isSameItemSameTags(stored, item)) count += stored.getCount();
        }
        return count;
    }

    private static int find(Container container, ItemStack item, int reserve) {
        if (container == null || count(container, item) <= reserve) return -1;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stored = container.getItem(slot);
            if (!stored.isEmpty() && ItemStack.isSameItemSameTags(stored, item)) return slot;
        }
        return -1;
    }

    private static int find(List<ItemStack> list, ItemStack item) {
        for (int i = 0; i < list.size(); i++) if (!list.get(i).isEmpty()
                && ItemStack.isSameItemSameTags(list.get(i), item)) return i;
        return -1;
    }

    private static List<ItemStack> bag(ChangedEntity mob) {
        return PlayerOutpostCargoInventory.inventory(mob).workItems();
    }

    private static void saveBag(ChangedEntity mob, List<ItemStack> items) {
        PlayerOutpostCargoInventory.inventory(mob).setWorkItems(items);
    }

    private static ItemStack add(List<ItemStack> bag, ItemStack source) {
        ItemStack remaining = source.copy();
        for (ItemStack item : bag) if (ItemStack.isSameItemSameTags(item, remaining)) {
            int moved = Math.min(remaining.getCount(), Math.max(0, item.getMaxStackSize() - item.getCount()));
            item.grow(moved);
            remaining.shrink(moved);
        }
        while (!remaining.isEmpty() && bag.size() < BAG_SLOTS) bag.add(remaining.split(remaining.getMaxStackSize()));
        return remaining;
    }

    public static boolean hasCargo(ChangedEntity mob) { return PlayerOutpostCargoInventory.hasWorkItems(mob); }

    /** Transfers an interrupted wild delivery into the camp's recoverable work inventory. */
    public static ItemStack acceptCargo(ChangedEntity mob, ItemStack source) {
        List<ItemStack> items = bag(mob);
        ItemStack remaining = add(items, source);
        saveBag(mob, items);
        return remaining;
    }

    static boolean unownedDrop(ItemEntity item) {
        if (item.getOwner() != null) return false;
        CompoundTag tag = new CompoundTag();
        item.addAdditionalSaveData(tag);
        return !tag.hasUUID("Thrower") && !tag.hasUUID("Owner");
    }

    public static void releaseCargo(ChangedEntity mob) {
        List<ItemStack> items = bag(mob);
        saveBag(mob, List.of());
        mob.getPersistentData().remove(OUTPUT);
        for (ItemStack item : items) mob.spawnAtLocation(item);
    }
}
