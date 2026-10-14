package net.parkabird.changedsynergy.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.ltxprogrammer.changed.ability.GrabEntityAbility;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.CrewJob;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Member;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Outpost;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Role;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import net.parkabird.changedsynergy.world.inventory.PlayerOutpostCargoInventory;

/** A finite travelling task owns MOVE; nearby ore can be finished before crew following resumes. */
public final class PlayerOutpostCrewGatherGoal extends Goal {
    private static final double SPEED = 0.35D;
    private static final double HUNT_SPEED = 0.4D;
    private static final int HUNT_ATTACK_TICKS = 16;
    private static final int HUNT_COOLDOWN_TICKS = 1800;
    private static final String HUNT_OWNER = "ChangedSynergyHuntClaimedBy";
    private static final String HUNT_UNTIL = "ChangedSynergyHuntClaimedUntil";
    private enum Action { MINE, ORANGE, SWEET_BERRIES, GLOW_BERRIES, HUNT, LOOT, DEPOSIT }
    private final ChangedEntity mob;
    private Outpost camp;
    private Member member;
    private Role startedRole;
    private CrewJob startedJob;
    private LivingEntity ownerBody;
    private Action action;
    private BlockPos block;
    private BlockState selectedState;
    private BlockPos stand;
    private Animal prey;
    private final Set<UUID> loot = new HashSet<>();
    private long nextScan;
    private int cursor;
    private int candidateCursor;
    private int pathProbes;
    private int remaining;
    private int actionTicks;
    private int repath;
    private float progress;
    private Vec3 lastMovement;
    private int stalledTicks;
    private final Map<BlockPos, Long> unreachable = new HashMap<>();

    public PlayerOutpostCrewGatherGoal(ChangedEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override public boolean canUse() {
        if (!(mob.level() instanceof ServerLevel level)) return false;
        if (member != null && member.role == Role.CREW
                && (startedRole != member.role || startedJob != member.crewJob)) nextScan = 0;
        if (level.getGameTime() < nextScan) return false;
        int interval = Math.max(40, ChangedSynergyConfig.COMMON.playerOutpostWorkScanInterval.get());
        nextScan = level.getGameTime() + interval + Math.floorMod(mob.getId(), 20);
        camp = PlayerOutpostData.get(level.getServer()).byMember(mob.getUUID()).orElse(null);
        member = camp == null ? null : camp.members.get(mob.getUUID());
        if (!available(level)) return false;
        startedRole = member.role;
        startedJob = member.crewJob;
        action = null; block = null; prey = null; loot.clear(); pathProbes = 0;
        unreachable.entrySet().removeIf(entry -> entry.getValue() <= level.getGameTime());
        // Travelling cargo is retained across recall, restart and job changes.
        // Deposit when back at camp; never abandon the owner to make a remote delivery.
        if (PlayerOutpostWorkGoal.hasCargo(mob) && member.role != Role.SUPPLY && member.role != Role.HOLD
                && (level.getServer().getPlayerList().getPlayer(camp.owner) != null
                        || ChangedSynergyConfig.COMMON.playerOutpostWorkWhileOwnerOffline.get())
                && camp.active(level) && camp.storage != null && camp.storage.distSqr(mob.blockPosition()) <= 64
                && PlayerOutpostData.container(level, camp.storage) != null
                && approach(level, camp.storage)) {
            action = Action.DEPOSIT; block = camp.storage;
            member.workStatus = "returning";
            return true;
        }
        if (member.role != Role.CREW || startedJob == CrewJob.COMBAT || !gatheringEnabled()
                || !PlayerOutpostWorkGoal.workerSlotAvailable(mob, camp)
                || !safeOwner(level, 16)) return false;
        if (!hasRoom()) { member.workStatus = "full_cargo"; return false; }
        if (!ForgeEventFactory.getMobGriefingEvent(level, mob)) return false;
        if (startedJob == CrewJob.HUNT) return chooseHunt(level);
        return chooseResource(level);
    }

    private boolean available(ServerLevel level) {
        return member != null && camp != null && camp.members.get(mob.getUUID()) == member
                && PlayerOutpostService.operational(mob) && mob.isAlive() && !mob.isNoAi()
                && mob.getTarget() == null && !mob.isSleeping() && !mob.isLeashed()
                && (!mob.isPassenger() || PlayerOutpostRestGoal.isResting(mob)) && !mob.isVehicle()
                && !TakeoverService.carrying(mob) && !ChangedAddonCompat.isGrabberBusy(mob)
                && GrabEntityAbility.getGrabber(mob) == null && !CreatureSettlementService.hasCargo(mob)
                && !SocialAudienceGoal.isActive(mob) && HypnosisQteService.getActiveVictim(mob) == null
                && !mob.getPersistentData().hasUUID("ChangedSynergyRestOwner")
                && !PlayerOutpostMealGoal.wantsMeal(mob) && !PlayerOutpostGoal.onCampfire(mob);
    }

    private boolean gatheringEnabled() {
        return ChangedSynergyConfig.COMMON.playerOutpostCrewGathering.get()
                && (startedJob != CrewJob.HUNT || ChangedSynergyConfig.COMMON.playerOutpostHunting.get());
    }

    private boolean safeOwner(ServerLevel level, int radius) {
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(camp.owner);
        if (owner == null || !owner.isAlive() || owner.isSpectator() || !PlayerOutpostService.authorized(mob, owner)) return false;
        ownerBody = TrueTransfurCompat.interactionBody(owner);
        if (ownerBody == null || !ownerBody.isAlive() || ownerBody.isRemoved() || ownerBody.level() != level
                || ownerBody == owner && TrueTransfurCompat.isInstinctShell(owner)) return false;
        return mob.distanceToSqr(ownerBody) <= radius * radius && mob.getHealth() > mob.getMaxHealth() * 0.4F
                && ownerBody.getHealth() > ownerBody.getMaxHealth() * 0.4F
                && !(ownerBody.getLastHurtByMob() != null
                        && ownerBody.tickCount - ownerBody.getLastHurtByMobTimestamp() < 100);
    }

    private boolean hasRoom() {
        return PlayerOutpostCargoInventory.inventory(mob).hasWorkSpace();
    }

    private boolean chooseResource(ServerLevel level) {
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos origin = mob.blockPosition();
        int nearRadius = startedJob == CrewJob.FORAGE ? 10 : 8;
        int nearHeight = startedJob == CrewJob.FORAGE ? 5 : 2;
        int below = startedJob == CrewJob.FORAGE ? -3 : -2;
        // A complete local pass, like wild provisioners, cannot skip the tree beside
        // a moving follower. Only the additional high/low terrain rotates between scans.
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-nearRadius, below, -nearRadius),
                origin.offset(nearRadius, nearHeight, nearRadius))) {
            if (level.hasChunkAt(pos) && pos.distSqr(ownerBody.blockPosition()) <= 16 * 16
                    && resource(level, pos) != null) candidates.add(pos.immutable());
        }
        int width = nearRadius * 2 + 1, area = width * width;
        for (int i = 0; i < 512; i++) {
            int index = Math.floorMod(cursor++, area * 17);
            int layer = index / area;
            int dy = (layer + 1) / 2 * (layer % 2 == 0 ? 1 : -1);
            BlockPos pos = origin.offset(index % width - nearRadius, dy, index / width % width - nearRadius);
            if (dy >= below && dy <= nearHeight) continue;
            if (level.hasChunkAt(pos) && pos.distSqr(ownerBody.blockPosition()) <= 16 * 16
                    && resource(level, pos) != null) candidates.add(pos);
        }
        candidates.sort(Comparator.comparingDouble(origin::distSqr));
        // Reachable fruit on the exposed edge wins over a closer, buried canopy block.
        for (BlockPos pos : candidates)
            if (inHarvestReach(pos, mob.position()) && visible(level, pos) && selectResource(level, pos)) return true;
        // A plantation can contain hundreds of fruit blocks. Bound the expensive
        // footing/collision checks as well as pathfinding, and rotate so obstructed
        // nearest blocks cannot permanently starve later candidates.
        for (int checked = 0; checked < Math.min(32, candidates.size()) && pathProbes < 16; checked++) {
            BlockPos pos = candidates.get(Math.floorMod(candidateCursor++, candidates.size()));
            if (unreachable.containsKey(pos)) continue;
            if (selectResource(level, pos)) return true;
            if (unreachable.size() < 128) unreachable.put(pos, level.getGameTime() + 100);
        }
        member.workStatus = candidates.isEmpty() ? "no_resources" : "unreachable";
        return false;
    }

    private boolean selectResource(ServerLevel level, BlockPos pos) {
        Action found = resource(level, pos);
        if (found == null || !roomForFruit(found)) return false;
        if (inHarvestReach(pos, mob.position()) && visible(level, pos))
            stand = mob.blockPosition();
        else if (!approach(level, pos, true)) return false;
        block = pos; selectedState = level.getBlockState(pos); action = found;
        member.workStatus = "harvesting";
        return action != null;
    }

    private boolean roomForFruit(Action found) {
        ItemStack fruit = switch (found) {
            case ORANGE -> new ItemStack(net.ltxprogrammer.changed.init.ChangedItems.ORANGE.get());
            case SWEET_BERRIES -> new ItemStack(Items.SWEET_BERRIES);
            case GLOW_BERRIES -> new ItemStack(Items.GLOW_BERRIES);
            default -> ItemStack.EMPTY;
        };
        return fruit.isEmpty() || PlayerOutpostCargoInventory.inventory(mob).workSpaceFor(fruit) > 0;
    }

    private boolean continueVein(ServerLevel level) {
        if (remaining <= 0 || !available(level) || member.role != startedRole || member.crewJob != startedJob
                || !gatheringEnabled() || !hasRoom() || !safeOwner(level, 16)
                || !PlayerOutpostWorkGoal.workerSlotAvailable(mob, camp)
                || !ForgeEventFactory.getMobGriefingEvent(level, mob)) return false;
        if (camp.active(level) && camp.storage != null && camp.storage.distSqr(mob.blockPosition()) <= 64
                && PlayerOutpostData.container(level, camp.storage) != null) return false;
        // Continue a finite nearby vein/fruit patch without surrendering MOVE to following.
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(block.offset(-2, -2, -2), block.offset(2, 2, 2))) {
            if (level.hasChunkAt(pos) && pos.distSqr(ownerBody.blockPosition()) <= 16 * 16
                    && resource(level, pos) != null) candidates.add(pos.immutable());
        }
        candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(mob.blockPosition())));
        pathProbes = 0;
        for (BlockPos pos : candidates) {
            if (pathProbes >= 16) break;
            if (!selectResource(level, pos)) continue;
            actionTicks = 0; progress = 0; repath = 0;
            if (action == Action.MINE) showPickaxe();
            return true;
        }
        return false;
    }

    private Action resource(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (startedJob == CrewJob.MINE) {
            var id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
            return CreatureSettlementService.isExposedOre(level, pos, state) && id != null
                    && !ChangedSynergyConfig.COMMON.playerOutpostCrewBlockedOres.get().contains(id.toString())
                    && CompanionToolEffects.gatheringPickaxe(mob, state).stack().isCorrectToolForDrops(state) ? Action.MINE : null;
        }
        if (startedJob != CrewJob.FORAGE) return null;
        if (state.is(ChangedBlocks.ORANGE_TREE_LEAVES.get())) return Action.ORANGE;
        if (state.getBlock() instanceof SweetBerryBushBlock && state.getValue(SweetBerryBushBlock.AGE) >= 2) return Action.SWEET_BERRIES;
        if (state.hasProperty(CaveVines.BERRIES) && state.getValue(CaveVines.BERRIES)) return Action.GLOW_BERRIES;
        return null;
    }

    private boolean chooseHunt(ServerLevel level) {
        if (mob.getPersistentData().getLong("ChangedSynergyNextHunt") > level.getGameTime()) {
            member.workStatus = "hunt_cooldown"; return false;
        }
        long now = level.getGameTime();
        // Filter protected/reserved animals before the bounded candidate list;
        // a nearby herd must not hide every eligible animal behind it.
        var animals = level.getEntitiesOfClass(Animal.class, mob.getBoundingBox().inflate(12, 8, 12), candidate -> {
            if (!PlayerOutpostWorkGoal.huntable(candidate) || candidate.distanceToSqr(ownerBody) > 16 * 16
                    || unreachable.containsKey(candidate.blockPosition())
                    || camp.dimension.equals(level.dimension().location().toString())
                        && (camp.farmContains(candidate.blockPosition()) || candidate.blockPosition().distSqr(camp.marker) < 64)) return false;
            var claim = candidate.getPersistentData();
            return claim.getLong(HUNT_UNTIL) <= now || !claim.hasUUID(HUNT_OWNER)
                    || claim.getUUID(HUNT_OWNER).equals(mob.getUUID());
        });
        boolean eligible = false;
        for (Animal candidate : animals.stream().sorted(Comparator.comparingDouble(mob::distanceToSqr)).limit(16).toList()) {
            if (pathProbes >= 16) break;
            var data = candidate.getPersistentData();
            eligible = true;
            // A player-assigned travelling hunter can take a solitary wild adult.
            // Approach the living target with native navigation, not a plant's standing square.
            if (!(mob.distanceToSqr(candidate) <= 6.25 && mob.hasLineOfSight(candidate))) {
                pathProbes++;
                var route = mob.getNavigation().createPath(candidate, 1);
                if (route == null || !route.canReach()) {
                    if (unreachable.size() < 128) unreachable.put(candidate.blockPosition(), now + 100);
                    continue;
                }
            }
            prey = candidate; block = candidate.blockPosition(); stand = block; action = Action.HUNT;
            data.putUUID(HUNT_OWNER, mob.getUUID()); data.putLong(HUNT_UNTIL, level.getGameTime() + 640);
            member.workStatus = "hunting";
            return true;
        }
        member.workStatus = eligible ? "unreachable" : "no_prey";
        return false;
    }

    private boolean approach(ServerLevel level, BlockPos target) {
        return approach(level, target, false);
    }

    private boolean approach(ServerLevel level, BlockPos target, boolean harvesting) {
        // Prefer nearby ground beside/below the resource, rather than probing the
        // canopy top first. A wide creature may need to stand two blocks from a trunk.
        int lowest = harvesting && startedJob == CrewJob.FORAGE ? -5 : -2;
        int radius = harvesting && startedJob == CrewJob.FORAGE ? 2 : 1;
        List<BlockPos> footing = new ArrayList<>();
        for (BlockPos position : BlockPos.betweenClosed(target.offset(-radius, lowest, -radius), target.offset(radius, 1, radius))) {
            BlockPos pos = position.immutable();
            if (!level.hasChunkAt(pos) || level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()
                    || PlayerOutpostGoal.Idle.unsafe(level.getBlockState(pos.below()))
                    || !level.noCollision(mob, mob.getBoundingBox().move(Vec3.atBottomCenterOf(pos).subtract(mob.position())))) continue;
            if (harvesting && (!inHarvestReach(target, Vec3.atBottomCenterOf(pos))
                    || !visible(level, target, Vec3.atBottomCenterOf(pos).add(0, mob.getEyeHeight(), 0)))) continue;
            footing.add(pos);
        }
        footing.sort(Comparator.comparingDouble(pos -> mob.distanceToSqr(Vec3.atBottomCenterOf(pos))));
        int attempts = 0;
        for (BlockPos pos : footing) {
            if (mob.distanceToSqr(Vec3.atBottomCenterOf(pos)) < 2 || ++pathProbes <= 16
                    && PlayerOutpostGoal.workRoute(mob, pos, mob.getNavigation().createPath(pos, 0))) {
                stand = pos; return true;
            }
            if (++attempts >= 4 || pathProbes >= 16) return false;
        }
        return false;
    }

    private boolean inHarvestReach(BlockPos target, Vec3 feet) {
        return startedJob == CrewJob.FORAGE
                ? feet.add(0, mob.getEyeHeight(), 0).distanceToSqr(Vec3.atCenterOf(target)) <= 4.5 * 4.5
                : feet.distanceToSqr(Vec3.atCenterOf(target)) <= 9;
    }

    @Override public boolean canContinueToUse() {
        if (!(mob.level() instanceof ServerLevel level) || !available(level) || action == null || remaining <= 0
                || member.role != startedRole || member.crewJob != startedJob || stand == null || !level.hasChunkAt(stand)) return false;
        if (action == Action.DEPOSIT) return camp.active(level) && block.equals(camp.storage) && PlayerOutpostWorkGoal.hasCargo(mob);
        if (!gatheringEnabled() || !safeOwner(level, 32) || !PlayerOutpostWorkGoal.workerSlotAvailable(mob, camp)
                || !ForgeEventFactory.getMobGriefingEvent(level, mob)) return false;
        if (action == Action.LOOT) return true;
        if (action == Action.HUNT) return prey != null && prey.isAlive() && PlayerOutpostWorkGoal.huntable(prey)
                && prey.distanceToSqr(ownerBody) < 32 * 32 && prey.getPersistentData().hasUUID(HUNT_OWNER)
                && prey.getPersistentData().getUUID(HUNT_OWNER).equals(mob.getUUID())
                && !(camp.dimension.equals(level.dimension().location().toString())
                        && (camp.farmContains(prey.blockPosition()) || prey.blockPosition().distSqr(camp.marker) < 64));
        return level.hasChunkAt(block) && level.getBlockState(block).equals(selectedState) && resource(level, block) == action;
    }

    @Override public void start() {
        PlayerOutpostRestGoal.cleanup(mob);
        remaining = 600; actionTicks = 0; progress = 0; repath = 0;
        lastMovement = mob.position(); stalledTicks = 0;
        mob.getNavigation().stop();
        if (action == Action.MINE) showPickaxe();
    }

    private void showPickaxe() {
        if (!net.parkabird.changedsynergy.compat.ChangedVanillaCompat.equipmentChangeRebuildsGoals(mob))
            GatheringToolPresentation.show(mob, CompanionToolEffects.gatheringPickaxe(mob, selectedState).stack());
    }

    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override public void tick() {
        if (!canContinueToUse() || !(mob.level() instanceof ServerLevel level)) { remaining = 0; return; }
        remaining--; GatheringToolPresentation.heartbeat(mob);
        if (action == Action.HUNT) { hunt(level); return; }
        if (action == Action.LOOT) { collectLoot(level); return; }
        boolean arrived = action == Action.DEPOSIT
                ? mob.distanceToSqr(Vec3.atCenterOf(block)) <= 9 : inHarvestReach(block, mob.position());
        boolean accessible = arrived && (action == Action.DEPOSIT || visible(level, block));
        if (accessible) {
            stalledTicks = 0;
            if (action != Action.DEPOSIT) member.workStatus = "harvesting";
            mob.getNavigation().stop();
            mob.getLookControl().setLookAt(Vec3.atCenterOf(block));
            actionTicks++;
            if (actionTicks % 8 == 1) mob.swing(InteractionHand.MAIN_HAND);
            if (action == Action.MINE) {
                float hardness = selectedState.getDestroySpeed(level, block);
                progress += hardness == 0 ? 1 : CompanionToolEffects.miningSpeed(mob,
                        CompanionToolEffects.gatheringPickaxe(mob, selectedState).stack(), selectedState) / hardness / adjustedTickDelay(30);
                level.destroyBlockProgress(mob.getId(), block, Math.min(9, (int)(progress * 10)));
                if (progress >= 1) finishHarvest(level);
            } else if (actionTicks >= 12) {
                if (action == Action.DEPOSIT) deposit(level);
                else finishHarvest(level);
            }
        } else if (--repath <= 0) {
            repath = 10;
            boolean routed = PlayerOutpostGoal.walkTo(mob, stand, stand.getY(), SPEED);
            member.workStatus = !routed ? "unreachable" : action == Action.DEPOSIT ? "returning" : "travelling";
        }
        if (!accessible) checkStalled(level);
    }

    private void checkStalled(ServerLevel level) {
        if (lastMovement.distanceToSqr(mob.position()) >= 0.25) {
            lastMovement = mob.position(); stalledTicks = 0;
        } else if (++stalledTicks >= 80) {
            // A door closing or a moving animal must not occupy MOVE for the full task timeout.
            if (block != null && unreachable.size() < 128) unreachable.put(block, level.getGameTime() + 100);
            member.workStatus = "unreachable"; remaining = 0;
        }
    }

    private boolean visible(ServerLevel level, BlockPos target) {
        return visible(level, target, mob.getEyePosition());
    }

    private boolean visible(ServerLevel level, BlockPos target, Vec3 eye) {
        var hit = level.clip(new ClipContext(eye, Vec3.atCenterOf(target),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target);
    }

    private void finishHarvest(ServerLevel level) {
        if (!hasRoom() || !ForgeEventFactory.onEntityDestroyBlock(mob, block, level.getBlockState(block))) { remaining = 0; return; }
        if (!roomForFruit(action)) { member.workStatus = "full_cargo"; remaining = 0; return; }
        boolean harvested = switch (action) {
            case MINE -> CreatureSettlementService.mineOre(mob, block,
                    item -> {
                        boolean fits = PlayerOutpostCargoInventory.inventory(mob).workSpaceFor(item) > 0;
                        if (!fits) member.workStatus = "full_cargo";
                        return fits;
                    });
            case ORANGE -> CreatureSettlementService.harvestOrangeLeaves(mob, block);
            case SWEET_BERRIES -> CreatureSettlementService.harvestSweetBerries(mob, block);
            case GLOW_BERRIES -> CreatureSettlementService.harvestGlowBerries(mob, block);
            default -> false;
        };
        if (harvested) {
            ItemStack remainder = PlayerOutpostWorkGoal.acceptCargo(mob, CreatureSettlementService.takeCargo(mob));
            if (!remainder.isEmpty()) mob.spawnAtLocation(remainder);
            if (action == Action.MINE) level.destroyBlockProgress(mob.getId(), block, -1);
            nextScan = level.getGameTime() + 5;
            if (continueVein(level)) return;
        }
        remaining = 0;
    }

    private void hunt(ServerLevel level) {
        block = prey.blockPosition(); stand = block;
        mob.getLookControl().setLookAt(prey, 30, 30);
        if (mob.distanceToSqr(prey) <= 6.25 && mob.hasLineOfSight(prey)) {
            stalledTicks = 0;
            mob.getNavigation().stop();
            if (++actionTicks < adjustedTickDelay(HUNT_ATTACK_TICKS)) return;
            actionTicks = 0;
            Set<UUID> existing = new HashSet<>();
            level.getEntitiesOfClass(ItemEntity.class, new AABB(block).inflate(3)).forEach(item -> existing.add(item.getUUID()));
            mob.swing(InteractionHand.MAIN_HAND);
            // Never call Changed's transfur attack on a food animal.
            prey.hurt(mob.damageSources().mobAttack(mob), (float)Math.max(1, mob.getAttributeValue(Attributes.ATTACK_DAMAGE)));
            if (!prey.isAlive()) {
                level.getEntitiesOfClass(ItemEntity.class, new AABB(block).inflate(3), item -> item.isAlive()
                        && !existing.contains(item.getUUID()) && PlayerOutpostWorkGoal.unownedDrop(item))
                        .forEach(item -> loot.add(item.getUUID()));
                action = Action.LOOT; actionTicks = 0;
                mob.getPersistentData().putLong("ChangedSynergyNextHunt", level.getGameTime() + HUNT_COOLDOWN_TICKS);
            }
        } else {
            if (--repath <= 0 && PlayerOutpostGoal.canNavigate(mob)) {
                repath = 10;
                boolean routed = mob.getNavigation().moveTo(prey, HUNT_SPEED);
                member.workStatus = routed ? "hunting" : "unreachable";
            }
            checkStalled(level);
        }
    }

    private void collectLoot(ServerLevel level) {
        ItemEntity nearest = null;
        for (var iterator = loot.iterator(); iterator.hasNext();) {
            UUID id = iterator.next();
            if (!(level.getEntity(id) instanceof ItemEntity item) || !item.isAlive()
                    || !PlayerOutpostWorkGoal.unownedDrop(item)
                    || PlayerOutpostCargoInventory.inventory(mob).workSpaceFor(item.getItem()) <= 0) {
                iterator.remove(); continue;
            }
            if (mob.distanceToSqr(item) <= 6.25) {
                ItemStack remainder = PlayerOutpostWorkGoal.acceptCargo(mob, item.getItem());
                if (remainder.isEmpty()) item.discard(); else item.setItem(remainder);
                iterator.remove();
            } else if (nearest == null || mob.distanceToSqr(item) < mob.distanceToSqr(nearest)) nearest = item;
        }
        if (nearest == null) { remaining = 0; return; }
        member.workStatus = "hauling";
        block = nearest.blockPosition(); stand = block;
        if (--repath <= 0 && PlayerOutpostGoal.canNavigate(mob)) {
            repath = 10; mob.getNavigation().moveTo(nearest, SPEED);
        }
        checkStalled(level);
    }

    private void deposit(ServerLevel level) {
        Container storage = PlayerOutpostData.container(level, block);
        if (storage == null) { remaining = 0; return; }
        var inventory = PlayerOutpostCargoInventory.inventory(mob);
        List<ItemStack> remainingItems = new ArrayList<>();
        for (ItemStack item : inventory.workItems()) {
            ItemStack rest = PlayerOutpostGoal.insert(storage, item);
            if (rest.getCount() < item.getCount()) PlayerOutpostStorageAccess.touch(mob, block);
            if (!rest.isEmpty()) remainingItems.add(rest);
        }
        inventory.setWorkItems(remainingItems);
        member.workStatus = remainingItems.isEmpty() ? "idle" : "full_storage";
        remaining = 0;
    }

    @Override public void stop() {
        mob.getNavigation().stop();
        if (action == Action.MINE && block != null && mob.level() instanceof ServerLevel level) level.destroyBlockProgress(mob.getId(), block, -1);
        GatheringToolPresentation.restore(mob);
        CreatureSettlementService.releaseHuntClaim(mob, prey);
        if (member != null && member.role == Role.CREW
                && (member.workStatus.equals("harvesting") || member.workStatus.equals("hunting")
                        || member.workStatus.equals("travelling") || member.workStatus.equals("hauling"))) member.workStatus = "waiting";
        loot.clear(); action = null; block = null; stand = null; prey = null;
        // Only successful harvests get a short retry; failed or idle searches stay throttled.
    }
}
