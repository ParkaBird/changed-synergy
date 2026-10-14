package net.parkabird.changedsynergy.regression;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Role;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Work;

/** Run complete mob ticks, native/Addon goals and the actual Forge event handlers. */
final class CampNavigationChecks {
    private static int checks;

    @SuppressWarnings("unchecked")
    static void run(ServerPlayer owner, ChangedEntity mob, PlayerOutpostData.Outpost camp) throws Exception {
        var level = owner.serverLevel();
        var data = PlayerOutpostData.get(owner.server);
        var member = camp.members.get(mob.getUUID());
        var playersField = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(PlayerList.class, "f_11197_");
        playersField.setAccessible(true);
        var players = (Map<UUID, ServerPlayer>)playersField.get(owner.server.getPlayerList());
        var listField = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(PlayerList.class, "f_11196_");
        listField.setAccessible(true);
        var playerList = (List<ServerPlayer>)listField.get(owner.server.getPlayerList());
        var others = new ArrayList<ChangedEntity>();
        try {
            owner.connection = new ServerGamePacketListenerImpl(owner.server, new Connection(PacketFlow.SERVERBOUND), owner);
            players.put(owner.getUUID(), owner);
            playerList.add(owner);
            level.addNewPlayer(owner);
            owner.setPos(0.5, 90, 6.5);
            ChangedSynergyConfig.COMMON.playerOutposts.set(true);
            ChangedSynergyConfig.COMMON.playerOutpostIdleRest.set(false);
            ChangedSynergyConfig.COMMON.playerOutpostWorkWhileOwnerOffline.set(false);
            ChangedSynergyConfig.COMMON.playerOutpostFarming.set(true);
            ChangedSynergyConfig.COMMON.playerOutpostSupplyWork.set(true);
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(true, owner.server);
            camp.meals = false;
            level.setDayTime(6000); level.updateSkyBrightness();
            CreaturePersonality.establishRelationship(mob, owner);
            CreaturePersonality.setFamiliarity(mob, owner, 60);
            LatexSocialMemory.addBond(mob, owner);
            PlayerOutpostService.clearAssignment(mob);
            net.parkabird.changedsynergy.compat.ChangedExtrasCompat.onLivingTick(new LivingEvent.LivingTickEvent(mob));
            SafeEntityMutationQueue.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, owner.server));
            var originals = mob.goalSelector.getAvailableGoals().stream()
                    .filter(g -> g.getGoal().getClass().getSimpleName().contains("SleepGoal")
                            || g.getGoal().getClass().getSimpleName().equals("SleepingWithOwnerGoal"))
                    .toList();

            data.setWork(camp, mob.getUUID(), Work.FARM, null);
            member.role = Role.SUPPLY;
            camp.farmStart = camp.marker.offset(9, 0, 9);
            camp.farmEnd = camp.marker.offset(10, 0, 10);
            camp.composter = null;
            BlockPos crop = camp.farmStart;
            var potatoes = (CropBlock)Blocks.POTATOES;
            level.setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState(), 3);
            level.setBlock(crop, potatoes.getStateForAge(potatoes.getMaxAge()), 3);
            reset(mob, new Vec3(-8.5, 90, -8.5));
            PlayerOutpostService.applyRole(mob, owner, Role.SUPPLY);
            int goalCount = mob.goalSelector.getAvailableGoals().size();
            for (int i = 0; i < 3; i++) PlayerOutpostService.ensureGoal(mob);
            check(mob.goalSelector.getAvailableGoals().size() == goalCount,
                    "Repeated camp reconciliation does not duplicate goals");
            tick(level, owner, List.of(mob), 300);
            if (level.getBlockState(crop).getValue(CropBlock.AGE) == potatoes.getMaxAge())
                System.out.println("CAMP_WORK_PATH_DIAGNOSTIC pos=" + mob.position() + " ticks=" + mob.tickCount
                        + " target=" + mob.getNavigation().getTargetPos() + " done=" + mob.getNavigation().isDone()
                        + " grounded=" + mob.onGround() + " status=" + member.workStatus
                        + " role=" + member.role + " running=" + mob.goalSelector.getRunningGoals()
                                .map(g -> g.getGoal().getClass().getSimpleName()).toList());
            check(level.getBlockState(crop).getValue(CropBlock.AGE) < potatoes.getMaxAge(),
                    "Full AI reaches and harvests a distant crop at ordinary walking speed");
            PlayerOutpostService.releaseCargo(mob);

            // Reproduced collision: Addon BipedSleepGoal has no MOVE flag and ran with camp work.
            BlockPos ownerBed = camp.marker.offset(0, 0, 6);
            bed(level, ownerBed);
            owner.startSleeping(ownerBed);
            level.setBlock(crop, potatoes.getStateForAge(potatoes.getMaxAge()), 3);
            reset(mob, new Vec3(0.5, 90, 3.5));
            tick(level, owner, List.of(mob), 300);
            if (potatoes.isMaxAge(level.getBlockState(crop)) || mob.isSleeping()) {
                System.out.println("CAMP_OWNER_SLEEP_STATE: crop=" + level.getBlockState(crop) + " sleeping=" + mob.isSleeping()
                        + " bed=" + mob.getSleepingPos() + " pos=" + mob.position() + " assigned=" + PlayerOutpostService.assigned(mob)
                        + " working=" + member.workStatus + " target=" + mob.getNavigation().getTargetPos()
                        + " gravity=" + !mob.isNoGravity() + " physics=" + !mob.noPhysics + " motion=" + mob.getDeltaMovement()
                        + " noAi=" + mob.isNoAi() + " underlying=" + mob.getUnderlyingPlayer() + " vehicle=" + mob.getVehicle()
                        + " passengers=" + mob.getPassengers() + " pose=" + mob.getPose() + " collision=" + !level.noCollision(mob)
                        + " grabber=" + net.ltxprogrammer.changed.ability.GrabEntityAbility.getGrabber(mob) + " alive=" + mob.isAlive()
                        + " ground=" + mob.onGround() + " grabbed=" + (BondedSuitService.ability(mob) == null ? "none" : BondedSuitService.ability(mob).grabbedEntity)
                        + " role=" + member.role + " day=" + level.isDay()
                        + " running=" + mob.goalSelector.getRunningGoals().map(g -> g.getGoal().getClass().getSimpleName()
                                + ":" + PlayerOutpostService.underlyingCompanionTask(g.getGoal()).getClass().getSimpleName()).toList());
            }
            check(level.getBlockState(crop).getValue(CropBlock.AGE) < potatoes.getMaxAge()
                            && !mob.isSleeping(),
                    "Sleeping owner does not redirect working camp member to an unlinked bed");
            check(originals.stream().noneMatch(g -> mob.goalSelector.getRunningGoals()
                            .anyMatch(active -> active.getGoal() == g.getGoal())),
                    "Native companion sleep is inactive during camp duties");
            PlayerOutpostService.releaseCargo(mob);

            // A requested meal must also keep its own route while the owner is sleeping.
            camp.meals = true;
            ChangedSynergyConfig.COMMON.playerOutpostMeals.set(true);
            reset(mob, new Vec3(-8.5, 90, -8.5));
            PlayerOutpostMealGoal.request(owner, camp);
            boolean mealSeated = false;
            for (int i = 0; i < 500 && !mealSeated; i++) {
                tick(level, owner, List.of(mob), 1);
                mealSeated = mob.isPassenger() && mealRunning(mob);
            }
            if (!mealSeated) System.out.println("CAMP_FULL_MEAL_DIAGNOSTIC pos=" + mob.position()
                    + " state=" + member.workStatus + " wants=" + PlayerOutpostMealGoal.wantsMeal(mob)
                    + " sleeping=" + mob.isSleeping() + " riding=" + mob.isPassenger()
                    + " cargo=" + PlayerOutpostWorkGoal.hasCargo(mob) + " using=" + mob.isUsingItem()
                    + " running=" + mob.goalSelector.getRunningGoals().map(g -> g.getGoal().getClass().getSimpleName()).toList());
            check(mealSeated && !mob.isSleeping(), "Full AI reaches a meal while the owner sleeps");
            member.role = Role.RETURN;
            PlayerOutpostService.applyRole(mob, owner, Role.RETURN);
            tick(level, owner, List.of(mob), 10);
            check(!mob.isPassenger() && !mealRunning(mob),
                    "Return order interrupts meal seat and releases its navigation");
            System.out.println("CAMP_FULL_AI_WORK_MEAL_PASS");

            // Different entity IDs must both start a scheduled meal despite alternate-tick goal selection.
            var mealPartner = (ChangedEntity)mob.getType().create(level);
            others.add(mealPartner); level.addFreshEntity(mealPartner);
            CreaturePersonality.establishRelationship(mealPartner, owner);
            CreaturePersonality.setFamiliarity(mealPartner, owner, 60);
            data.add(camp, mealPartner.getUUID(), "Meal partner", camp.dimension,
                    camp.marker, PlayerOutpostSupplyType.ORANGE);
            camp.mealRequestedAt = -1;
            level.setDayTime(35020); level.updateSkyBrightness();
            var diners = List.of(mob, mealPartner);
            for (int i = 0; i < diners.size(); i++) {
                ChangedEntity diner = diners.get(i);
                camp.members.get(diner.getUUID()).role = Role.RESIDENT;
                reset(diner, new Vec3(-7.5 + i * 2, 90, -5.5));
                PlayerOutpostService.applyRole(diner, owner, Role.RESIDENT);
                PlayerOutpostGoal.insert(net.parkabird.changedsynergy.world.inventory.BondedInventoryService
                        .inventory(diner, owner), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD));
            }
            for (int i = 0; i < 200 && diners.stream().anyMatch(d -> !d.isPassenger() || !mealRunning(d)); i++)
                tick(level, owner, diners, 1);
            check(diners.stream().allMatch(d -> d.isPassenger() && mealRunning(d)),
                    "Scheduled meals start for both entity IDs through the native alternating selector");
            for (ChangedEntity diner : diners) PlayerOutpostMealGoal.cleanup(diner);
            data.remove(camp, mealPartner.getUUID()); PlayerOutpostService.clearAssignment(mealPartner);
            mealPartner.discard(); others.remove(mealPartner);
            System.out.println("CAMP_SCHEDULED_MEAL_AI_PASS");

            // Four registered native beds, four complete mob AI loops, and a sleeping nearby owner.
            camp.meals = false; camp.beds.clear(); camp.restPoints.clear();
            ChangedSynergyConfig.COMMON.playerOutpostRest.set(true);
            level.setDayTime(18000); level.updateSkyBrightness();
            List<BlockPos> heads = List.of(camp.marker.offset(-7, 0, 7), camp.marker.offset(-3, 0, 7),
                    camp.marker.offset(3, 0, 7), camp.marker.offset(7, 0, 7));
            for (BlockPos head : heads) { bed(level, head); data.setBed(camp, head); }
            var sleepers = new ArrayList<ChangedEntity>(); sleepers.add(mob);
            for (int i = 0; i < 3; i++) {
                var other = (ChangedEntity)mob.getType().create(level);
                others.add(other); level.addFreshEntity(other);
                CreaturePersonality.establishRelationship(other, owner);
                CreaturePersonality.setFamiliarity(other, owner, 60);
                if (i % 2 == 0) LatexSocialMemory.addBond(other, owner);
                data.add(camp, other.getUUID(), "Sleeper " + i, camp.dimension,
                        camp.marker, PlayerOutpostSupplyType.ORANGE);
                sleepers.add(other);
            }
            for (int i = 0; i < sleepers.size(); i++) {
                ChangedEntity sleeper = sleepers.get(i);
                camp.members.get(sleeper.getUUID()).role = Role.RESIDENT;
                reset(sleeper, new Vec3(i * 2.0 - 2.5, 90, -5.5));
                PlayerOutpostService.applyRole(sleeper, owner, Role.RESIDENT);
            }
            for (int i = 0; i < 600 && sleepers.stream().anyMatch(s -> !s.isSleeping()); i++)
                tick(level, owner, sleepers, 1);
            var used = new HashSet<BlockPos>();
            for (ChangedEntity sleeper : sleepers) {
                if (!sleeper.isSleeping()) System.out.println("CAMP_BEDS_DIAGNOSTIC " + sleeper.getUUID()
                        + " pos=" + sleeper.position() + " target=" + sleeper.getTarget()
                        + " state=" + camp.members.get(sleeper.getUUID()).workStatus
                        + " running=" + sleeper.goalSelector.getRunningGoals()
                                .map(g -> g.getGoal().getClass().getSimpleName()).toList());
                check(sleeper.isSleeping(), "Each camp member reaches a bed with all installed AI enabled");
                check(sleeper.getSleepingPos().filter(heads::contains).isPresent(),
                        "Sleep uses a registered native bed head, not the owner's unlinked bed");
                check(used.add(sleeper.getSleepingPos().orElseThrow()), "Each sleeper occupies a separate bed");
            }
            check(used.size() == 4 && camp.beds.size() == 4, "All four registered beds are used");
            tick(level, owner, sleepers, 160);
            check(sleepers.stream().allMatch(ChangedEntity::isSleeping),
                    "Independent bed reservations remain stable through maintenance ticks");
            System.out.println("CAMP_FOUR_NATIVE_BEDS_AI_PASS");
            BlockPos releasedBed = mob.getSleepingPos().orElseThrow();
            member.role = Role.RETURN;
            PlayerOutpostService.applyRole(mob, owner, Role.RETURN);
            check(!mob.isSleeping() && PlayerOutpostRestGoal.free(mob, level, releasedBed),
                    "Return immediately wakes the member and releases its exact bed");
            tick(level, owner, sleepers, 10);
            if (mob.isSleeping() || !PlayerOutpostRestGoal.free(mob, level, releasedBed))
                System.out.println("CAMP_RETURN_BED_DIAGNOSTIC sleeping=" + mob.isSleeping()
                        + " returnedBed=" + releasedBed + " current=" + mob.getSleepingPos()
                        + " role=" + camp.members.get(mob.getUUID()).role + " position=" + mob.position()
                        + " running=" + mob.goalSelector.getRunningGoals()
                                .map(g -> g.getGoal().getClass().getSimpleName()).toList()
                        + " bedState=" + level.getBlockState(releasedBed));
            check(!mob.isSleeping() && PlayerOutpostRestGoal.free(mob, level, releasedBed),
                    "Return order frees the sleeping member's bed reservation");
            for (ChangedEntity other : others) {
                data.remove(camp, other.getUUID()); PlayerOutpostService.clearAssignment(other); other.discard();
            }

            owner.stopSleeping(); level.setDayTime(6000); level.updateSkyBrightness();
            reset(mob, new Vec3(-8.5, 90, -8.5));
            combat(owner, mob, camp);
            doorPassage(owner, mob, camp);

            // Turning camps off and leaving must restore exact native goal objects and priorities.
            ChangedSynergyConfig.COMMON.playerOutposts.set(false);
            mob.tickCount = 100;
            PlayerOutpostService.onTick(new LivingEvent.LivingTickEvent(mob));
            check(originals.stream().allMatch(original -> mob.goalSelector.getAvailableGoals().stream()
                            .anyMatch(g -> g.getGoal() == original.getGoal() && g.getPriority() == original.getPriority())),
                    "Runtime-off restores original companion sleep goals and priorities");
            ChangedSynergyConfig.COMMON.playerOutposts.set(true);
            PlayerOutpostService.ensureGoal(mob);
            check(originals.stream().noneMatch(original -> mob.goalSelector.getAvailableGoals().stream()
                            .anyMatch(g -> g.getGoal() == original.getGoal())),
                    "Re-enabling camps makes native companion sleep yield again");
            data.remove(camp, mob.getUUID()); PlayerOutpostService.clearAssignment(mob);
            check(originals.stream().allMatch(original -> mob.goalSelector.getAvailableGoals().stream()
                            .anyMatch(g -> g.getGoal() == original.getGoal() && g.getPriority() == original.getPriority())),
                    "Leaving camp restores native companion behavior without losing its state");
            data.add(camp, mob.getUUID(), "Farmer", camp.dimension, mob.blockPosition(), PlayerOutpostSupplyType.ORANGE);
            PlayerOutpostService.applyRole(mob, owner, Role.RESIDENT);
            System.out.println("CAMP_FULL_AI_NAVIGATION_REGRESSION_PASS: " + checks + " checks");
        } finally {
            for (ChangedEntity other : others) {
                data.remove(camp, other.getUUID()); PlayerOutpostService.clearAssignment(other); other.discard();
            }
            owner.stopSleeping(); playerList.remove(owner); players.remove(owner.getUUID()); owner.discard();
            PlayerOutpostMealGoal.cleanup(mob); PlayerOutpostRestGoal.cleanup(mob);
        }
    }

    private static void combat(ServerPlayer owner, ChangedEntity mob, PlayerOutpostData.Outpost camp) {
        var member = camp.members.get(mob.getUUID());
        var zombie = EntityType.ZOMBIE.create(owner.serverLevel());
        zombie.setPos(mob.position().add(2, 0, 0));
        var dark = (net.ltxprogrammer.changed.entity.beast.AbstractDarkLatexEntity)mob;
        var oldCondition = dark.getAttackCondition();
        var oldTargetType = dark.getTargetType();
        // Allow the native pet combat policy so these checks exercise the camp restrictions.
        dark.setAttackCondition(net.ltxprogrammer.changed.entity.ai.DarkLatexAttackCondition.ALWAYS);
        dark.setTargetType(net.ltxprogrammer.changed.entity.ai.DarkLatexTargetType.MONSTERS);
        owner.setLastHurtByMob(zombie);
        var oldCrewJob = member.crewJob;
        for (Role role : Role.values()) {
            if (role == Role.GUARD) continue;
            member.role = role;
            member.crewJob = PlayerOutpostData.CrewJob.MINE;
            PlayerOutpostService.applyRole(mob, owner, role);
            mob.getNavigation().moveTo(9.5, 90, -5.5, 0.35);
            var route = mob.getNavigation().getPath();
            mob.setTarget(zombie);
            check(mob.getTarget() == null && mob.getNavigation().getPath() == route,
                    "Non-guard rejects proactive combat without interrupting its route: " + role);
            check(!BondedPetSettings.allowsDefense(mob, owner, zombie),
                    "Owner-only aggression does not divert a non-guard: " + role);
            check(!BondedOwnerDefenseGoal.allowsConfiguredDefense(mob, owner, zombie),
                    "Native Dark-latex defense policy also respects the non-guard role: " + role);
        }
        member.crewJob = oldCrewJob;
        var friend = (ChangedEntity)mob.getType().create(owner.serverLevel());
        CreaturePersonality.establishRelationship(friend, owner);
        CreaturePersonality.setFamiliarity(friend, owner, 60);
        mob.getNavigation().moveTo(9.5, 90, -5.5, 0.35);
        var route = mob.getNavigation().getPath();
        LatexCreatureCombatRules.disengage(mob, friend);
        check(mob.getNavigation().getPath() == route,
                "Rejected friendly encounter cannot stop an unrelated camp route on either side");
        var aquatic = (ChangedEntity)net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(
                net.minecraft.resources.ResourceLocation.parse("changed:latex_shark_male")).create(owner.serverLevel());
        aquatic.setPos(7.5, 90, -5.5); aquatic.setOnGround(true); owner.serverLevel().addFreshEntity(aquatic);
        CreaturePersonality.establishRelationship(aquatic, owner);
        CreaturePersonality.setFamiliarity(aquatic, owner, 60);
        LatexSocialMemory.addBond(aquatic, owner);
        var data = PlayerOutpostData.get(owner.server);
        data.add(camp, aquatic.getUUID(), "Aquatic", camp.dimension, aquatic.blockPosition(), PlayerOutpostSupplyType.FISH);
        PlayerOutpostService.applyRole(aquatic, owner, Role.RESIDENT);
        LatexSocialMemory.setWaitingOnShore(aquatic, owner);
        check(!new BondedShoreWaitGoal(aquatic).canUse(), "Old shore-wait order yields to camp duties");
        ChangedSynergyConfig.COMMON.playerOutposts.set(false);
        check(new BondedShoreWaitGoal(aquatic).canUse(), "Runtime-off restores the retained shore-wait order");
        ChangedSynergyConfig.COMMON.playerOutposts.set(true);
        data.remove(camp, aquatic.getUUID()); PlayerOutpostService.clearAssignment(aquatic); aquatic.discard();
        friend.discard();
        owner.setLastHurtByMob(null);
        member.role = Role.GUARD;
        PlayerOutpostService.applyRole(mob, owner, Role.GUARD);
        mob.setTarget(zombie);
        check(mob.getTarget() == zombie, "Guard keeps proactive monster targeting");
        member.role = Role.SUPPLY;
        PlayerOutpostService.applyRole(mob, owner, Role.SUPPLY);
        check(mob.hurt(owner.serverLevel().damageSources().mobAttack(zombie), 1),
                "Monster actually damages a non-guard member");
        mob.setTarget(zombie);
        check(mob.getLastHurtByMob() == zombie && mob.getTarget() == zombie,
                "An attacked non-guard may retaliate against its actual attacker");
        check(BondedOwnerDefenseGoal.allowsConfiguredDefense(mob, owner, zombie),
                "The Dark-latex defense gate permits actual self-defense");
        mob.tickCount += 601;
        PlayerOutpostService.onTick(new LivingEvent.LivingTickEvent(mob));
        check(mob.getTarget() == null, "Old retaliation does not become permanent proactive combat");
        ChangedSynergyConfig.COMMON.playerOutposts.set(false);
        mob.setTarget(zombie);
        check(mob.getTarget() == zombie, "Disabling camps restores ordinary monster targeting");
        ChangedSynergyConfig.COMMON.playerOutposts.set(true);
        mob.setTarget(null); mob.setLastHurtByMob(null); owner.setLastHurtByMob(null);
        dark.setAttackCondition(oldCondition); dark.setTargetType(oldTargetType);
        mob.setHealth(mob.getMaxHealth()); zombie.discard();
    }

    private static boolean mealRunning(ChangedEntity mob) {
        return mob.goalSelector.getRunningGoals().anyMatch(g -> g.getGoal() instanceof PlayerOutpostMealGoal);
    }

    private static void doorPassage(ServerPlayer owner, ChangedEntity mob, PlayerOutpostData.Outpost camp) throws Exception {
        var level = owner.serverLevel();
        var changed = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        BlockPos door = new BlockPos(0, 90, -6);
        try {
            for (BlockPos pos : BlockPos.betweenClosed(door.offset(-9, 0, -1), door.offset(9, 2, 1))) {
                changed.put(pos.immutable(), level.getBlockState(pos));
                level.setBlock(pos, pos.getZ() != door.getZ() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
            }
            var base = Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, Direction.EAST);
            level.setBlock(door, base, 3);
            level.setBlock(door.above(), base.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 3);
            owner.setPos(6.5, 90, door.getZ() + .5);
            reset(mob, new Vec3(-6.5, 90, door.getZ() + .5));
            camp.members.get(mob.getUUID()).role = Role.CREW;
            PlayerOutpostService.applyRole(mob, owner, Role.CREW);
            tick(level, owner, List.of(mob), 300);
            System.out.println("CAMP_DOOR_DIAGNOSTIC pos=" + mob.position() + " role=" + camp.members.get(mob.getUUID()).role
                    + " width=" + mob.getBbWidth() + " door=" + level.getBlockState(door) + " target=" + mob.getNavigation().getTargetPos()
                    + " running=" + mob.goalSelector.getRunningGoals().map(g -> g.getGoal().getClass().getSimpleName()).toList());
            check(mob.getX() > 2 && Math.abs(mob.getZ() - owner.getZ()) < 2,
                    "Full AI follows through a closed door without repeated opening or closing on its body");
            check(mob.goalSelector.getAvailableGoals().stream().anyMatch(g -> g.getGoal() instanceof CompanionOpenDoorGoal),
                    "Native door goal is replaced with crossing-aware closing");
            System.out.println("CAMP_DOOR_PASS: crossed at " + mob.position());
            level.setBlock(door.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(door, Blocks.OAK_TRAPDOOR.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, true)
                    .setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, Direction.NORTH), 3);
            reset(mob, new Vec3(-6.5, 90, door.getZ() + .5));
            camp.members.get(mob.getUUID()).role = Role.CREW;
            PlayerOutpostService.applyRole(mob, owner, Role.CREW);
            tick(level, owner, List.of(mob), 300);
            System.out.println("CAMP_TRAPDOOR_DIAGNOSTIC pos=" + mob.position() + " target=" + mob.getNavigation().getTargetPos());
            check(mob.getX() > 2 && Math.abs(mob.getZ() - owner.getZ()) < 2,
                    "Full AI crosses the free opening beside an upright trapdoor without changing its state");
        } finally {
            for (var entry : changed.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 3);
            camp.members.get(mob.getUUID()).role = Role.RESIDENT;
            PlayerOutpostService.applyRole(mob, owner, Role.RESIDENT);
        }
    }

    static void tick(ServerLevel level, ServerPlayer owner, List<ChangedEntity> mobs, int count) throws Exception {
        var serverTicks = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(net.minecraft.server.MinecraftServer.class, "f_129766_");
        serverTicks.setAccessible(true);
        for (int i = 0; i < count; i++) {
            beginAiTick(owner);
            // Mob's alternating selector updates use the server counter, not only its entity counter.
            serverTicks.setInt(owner.server, owner.server.getTickCount() + 1);
            ((ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime() + 1);
            // ServerLevel.tickNonPassenger increments this before calling Entity.tick.
            for (ChangedEntity mob : mobs) { mob.tickCount++; mob.tick(); }
            owner.tickCount++;
            MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, owner));
            SafeEntityMutationQueue.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, owner.server));
        }
    }

    static void beginAiTick(ServerPlayer owner) {
        // The real server gives every tick a fresh optional-AI budget, not one budget for the whole fixture.
        net.parkabird.changedsynergy.performance.SynergyPerformanceTracker.onServerTickStart(
                new TickEvent.ServerTickEvent(TickEvent.Phase.START, () -> true, owner.server));
    }

    private static void bed(ServerLevel level, BlockPos head) {
        var base = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        level.setBlock(head.south(), base.setValue(BedBlock.PART, BedPart.FOOT), 3);
        level.setBlock(head, base.setValue(BedBlock.PART, BedPart.HEAD), 3);
    }

    private static void reset(ChangedEntity mob, Vec3 pos) {
        PlayerOutpostMealGoal.cleanup(mob); PlayerOutpostRestGoal.cleanup(mob);
        mob.stopRiding(); mob.stopSleeping(); mob.setTarget(null);
        mob.setPos(pos); mob.setDeltaMovement(Vec3.ZERO); mob.setOnGround(true);
        mob.getNavigation().stop();
    }

    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
}
