package net.parkabird.changedsynergy.regression;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import net.ltxprogrammer.changed.block.CardboardBoxTall;
import net.ltxprogrammer.changed.block.OfficeChair;
import net.ltxprogrammer.changed.block.Pillow;
import net.ltxprogrammer.changed.block.entity.CardboardBoxTallBlockEntity;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.SeatEntity;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.*;

/** Actual server beds, seats, command raycasts and work interruption. */
final class CampRestChecks {
    private static int checks;

    static void run(ServerPlayer owner, ChangedEntity mob, PlayerOutpostData.Outpost camp) throws Exception {
        var level = owner.serverLevel();
        var data = PlayerOutpostData.get(owner.server);
        var member = camp.members.get(mob.getUUID());
        var beds = new LinkedHashSet<>(camp.beds);
        var points = new LinkedHashSet<>(camp.restPoints);
        var role = member.role; var work = member.work; var backup = member.backupWork;
        var position = mob.position(); var ownerPosition = owner.position();
        float yaw = owner.getYRot(), pitch = owner.getXRot();
        long time = level.getDayTime(); int ticks = mob.tickCount;
        boolean enabled = ChangedSynergyConfig.COMMON.playerOutposts.get();
        boolean sleep = ChangedSynergyConfig.COMMON.playerOutpostRest.get();
        boolean idle = ChangedSynergyConfig.COMMON.playerOutpostIdleRest.get();
        boolean meals = camp.meals;
        boolean grounded = mob.onGround();
        var connection = owner.connection;
        var states = new LinkedHashMap<BlockPos, BlockState>();
        var second = (ChangedEntity)mob.getType().create(level);
        PlayerOutpostRestGoal firstRest = null, secondRest = null;
        ItemEntity item = null;
        try {
            owner.connection = new ServerGamePacketListenerImpl(owner.server, new Connection(PacketFlow.SERVERBOUND), owner);
            ChangedSynergyConfig.COMMON.playerOutposts.set(true);
            ChangedSynergyConfig.COMMON.playerOutpostRest.set(true);
            ChangedSynergyConfig.COMMON.playerOutpostIdleRest.set(true);
            camp.meals = false;
            camp.beds.clear(); camp.restPoints.clear();
            member.role = PlayerOutpostData.Role.RESIDENT;
            PlayerOutpostService.releaseCargo(mob);
            level.setDayTime(18000); level.updateSkyBrightness(); mob.tickCount = 0;
            BlockPos foot = camp.marker.offset(-5, 0, 5), foot2 = foot.offset(3, 0, 0);
            for (BlockPos p : java.util.List.of(foot, foot2)) {
                var base = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
                place(level, states, p, base.setValue(BedBlock.PART, BedPart.FOOT));
                place(level, states, p.south(), base.setValue(BedBlock.PART, BedPart.HEAD));
                link(owner, p); link(owner, p.south());
            }
            check(camp.beds.size() == 2, "Automatic link retains two beds and deduplicates either half");
            mob.setPos(Vec3.atBottomCenterOf(foot));
            mob.setOnGround(true);
            second.setPos(foot.getX() + 2.0, foot.getY(), foot.getZ() + 1.5);
            second.setOnGround(true); level.addFreshEntity(second);
            data.add(camp, second.getUUID(), "Second sleeper", camp.dimension, second.blockPosition(), PlayerOutpostSupplyType.ORANGE);
            second.tickCount = 0;
            firstRest = new PlayerOutpostRestGoal(mob); secondRest = new PlayerOutpostRestGoal(second);
            check(firstRest.canUse(), "First sleeper finds a bed"); firstRest.start();
            boolean secondReady = secondRest.canUse();
            if (!secondReady) System.out.println("CAMP_SECOND_BED_DIAGNOSTIC: ground=" + second.onGround()
                    + " noAi=" + second.isNoAi() + " target=" + second.getTarget() + " beds=" + camp.beds);
            check(secondReady && field(secondRest, "bed").equals(foot2.south()),
                    "Approaching sleeper reserves the first bed before it becomes occupied");
            secondRest.start(); firstRest.tick();
            second.setPos(Vec3.atBottomCenterOf(foot2)); secondRest.tick();
            check(mob.isSleeping() && second.isSleeping() && !mob.getSleepingPos().equals(second.getSleepingPos()),
                    "Two actual creatures sleep in separate native bed heads");
            check(firstRest.canContinueToUse() && secondRest.canContinueToUse(), "Separate bed assignments remain valid");
            ChangedSynergyConfig.COMMON.playerOutpostRest.set(false);
            check(!firstRest.canContinueToUse(), "Disabling nighttime rest interrupts a sleeping goal");
            firstRest.stop(); secondRest.stop();
            check(!mob.isSleeping() && !second.isSleeping()
                    && !level.getBlockState(foot.south()).getValue(BedBlock.OCCUPIED), "Waking releases occupied bed heads");
            ChangedSynergyConfig.COMMON.playerOutpostRest.set(true);
            // Reproduce a queued running-goal tick after a command has already cleared the bed reservation.
            mob.setPos(Vec3.atBottomCenterOf(foot)); mob.setOnGround(true);
            var queued = new PlayerOutpostRestGoal(mob);
            check(queued.canUse(), "Queued-tick fixture selects its bed");
            queued.start(); queued.tick();
            check(mob.isSleeping(), "Queued-tick fixture enters sleep");
            member.role = PlayerOutpostData.Role.RETURN;
            PlayerOutpostService.applyRole(mob, owner, PlayerOutpostData.Role.RETURN);
            queued.tick();
            check(!mob.isSleeping() && PlayerOutpostRestGoal.free(mob, level, foot.south()),
                    "An interrupted sleep goal cannot reoccupy its released bed during its last queued tick");
            queued.stop(); member.role = PlayerOutpostData.Role.RESIDENT;
            level.setDayTime(6000); level.updateSkyBrightness();
            BlockPos point = camp.marker.offset(-3, 0, 3);
            for (Class<?> kind : java.util.List.of(Pillow.class, OfficeChair.class, CardboardBoxTall.class)) {
                camp.restPoints.clear(); member.role = PlayerOutpostData.Role.RESIDENT;
                Block block = ForgeRegistries.BLOCKS.getValues().stream().filter(kind::isInstance).findFirst().orElseThrow();
                BlockState base = block.defaultBlockState();
                if (kind != Pillow.class) {
                    place(level, states, point, base.setValue(OfficeChair.HALF, DoubleBlockHalf.LOWER));
                    place(level, states, point.above(), base.setValue(OfficeChair.HALF, DoubleBlockHalf.UPPER));
                    if (kind == OfficeChair.class) {
                        // Changed's upper chair block has no clickable outline.
                        check(PlayerOutpostRestGoal.restingPlace(level, point.above()).equals(point), "Upper chair normalizes to its actual seat");
                        link(owner, point);
                    } else link(owner, point.above());
                } else place(level, states, point, base);
                link(owner, point);
                check(camp.restPoints.size() == 1 && camp.restPoints.contains(point) && camp.beds.size() == 2,
                        "Rest point is separate from beds and deduplicates halves: " + kind.getSimpleName());
                mob.setPos(Vec3.atBottomCenterOf(point)); mob.tickCount = 0;
                mob.setOnGround(true);
                // Isolated checks run between world ticks; emulate the elapsed dismount cooldown.
                var cooldown = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(net.minecraft.world.entity.Entity.class, "f_19851_");
                cooldown.setAccessible(true); cooldown.setInt(mob, 0);
                firstRest = new PlayerOutpostRestGoal(mob);
                boolean selected = false;
                for (int attempt = 0; attempt < 64 && !selected; attempt++) {
                    set(firstRest, "nextAttempt", 0L); mob.getRandom().setSeed(attempt);
                    selected = firstRest.canUse();
                }
                check(selected, "Idle member can select linked rest point: " + kind.getSimpleName());
                firstRest.start(); firstRest.tick();
                check(PlayerOutpostRestGoal.isResting(mob) && firstRest.canContinueToUse(),
                        "Native seat stays valid: " + kind.getSimpleName());
                var seat = (SeatEntity)mob.getVehicle();
                if (kind == Pillow.class) {
                    ChangedSynergyConfig.COMMON.playerOutposts.set(false);
                    check(!firstRest.canContinueToUse(), "Disabling camps interrupts owned rest");
                    ChangedSynergyConfig.COMMON.playerOutposts.set(true);
                    camp.meals = true; camp.mealRequestedAt = level.getGameTime();
                    check(PlayerOutpostMealGoal.wantsMeal(mob), "An explicit shared meal can interrupt a camp-owned seat");
                    camp.meals = false;
                    data.setWork(camp, mob.getUUID(), PlayerOutpostData.Work.HAUL, null);
                    item = new ItemEntity(level, camp.storage.getX() + 0.5, camp.storage.getY(),
                            camp.storage.getZ() + 1.5, new ItemStack(Items.OAK_LOG));
                    item.tickCount = 120; level.addFreshEntity(item);
                    var hauling = new PlayerOutpostWorkGoal(mob);
                    set(hauling, "scanInitialized", true); set(hauling, "nextScan", 0L);
                    check(hauling.canUse(), "A real available haul task can preempt an owned rest seat");
                    firstRest.stop(); hauling.start();
                    check(!mob.isPassenger() && seat.isRemoved(), "Work start detaches the native seat");
                    hauling.stop(); item.discard(); item = null;
                } else if (kind == OfficeChair.class) {
                    ChangedSynergyConfig.COMMON.playerOutpostIdleRest.set(false);
                    check(!firstRest.canContinueToUse(), "Idle-rest setting can be disabled at runtime");
                    mob.stopRiding(); seat.discard();
                    var floor = point.east().below();
                    var foreign = SeatEntity.createFor(level, level.getBlockState(floor), floor, false, false, true);
                    check(foreign != null && mob.startRiding(foreign, true), "Foreign interaction replaces an interrupted seat");
                    firstRest.stop();
                    check(mob.getVehicle() == foreign && !foreign.isRemoved(), "Rest cleanup does not discard another interaction's seat");
                    mob.stopRiding(); foreign.discard(); ChangedSynergyConfig.COMMON.playerOutpostIdleRest.set(true);
                } else {
                    data.role(camp, mob.getUUID(), PlayerOutpostData.Role.RETURN, null);
                    PlayerOutpostService.applyRole(mob, owner, PlayerOutpostData.Role.RETURN);
                    check(!mob.isPassenger() && seat.isRemoved()
                            && ((CardboardBoxTallBlockEntity)level.getBlockEntity(point.above())).getEntityHolder() == null,
                            "Return order clears a tall-box seat and its holder");
                    firstRest.stop();
                }
                check(!mob.isPassenger() && seat.isRemoved(), "Rest stop releases native seat: " + kind.getSimpleName());
                if (kind != Pillow.class) level.setBlock(point.above(), Blocks.AIR.defaultBlockState(), 18);
                level.setBlock(point, Blocks.AIR.defaultBlockState(), 18);
            }
            var load = PlayerOutpostData.class.getDeclaredMethod("load", CompoundTag.class); load.setAccessible(true);
            var restored = (PlayerOutpostData)load.invoke(null, data.save(new CompoundTag()));
            var home = restored.byOwner(camp.owner).orElseThrow();
            check(home.beds.equals(camp.beds) && home.restPoints.equals(camp.restPoints), "Bed and rest-point lists survive save/reload");
            Block pillow = ForgeRegistries.BLOCKS.getValues().stream().filter(Pillow.class::isInstance).findFirst().orElseThrow();
            place(level, states, point, pillow.defaultBlockState());
            var root = data.save(new CompoundTag());
            for (var entry : root.getList("Outposts", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                var tag = (CompoundTag)entry;
                if (!tag.getUUID("Owner").equals(camp.owner)) continue;
                tag.remove("Beds"); tag.remove("RestPoints"); tag.putLong("Bed", point.asLong());
            }
            restored = (PlayerOutpostData)load.invoke(null, root); home = restored.byOwner(camp.owner).orElseThrow();
            restored.refreshRestPlaces(level, home);
            check(home.beds.isEmpty() && home.restPoints.contains(point), "Legacy pillow binding migrates into a rest point");
            var beeType = ForgeRegistries.ENTITY_TYPES.getValue(net.minecraft.resources.ResourceLocation.parse("changed:latex_bee"));
            var bee = (ChangedEntity)beeType.create(level);
            var yellow = ForgeRegistries.BLOCKS.getValue(net.minecraft.resources.ResourceLocation.parse("changed:yellow_pillow"));
            place(level, states, point, yellow.defaultBlockState());
            var savedBeds = new LinkedHashSet<>(camp.beds);
            camp.beds.clear(); camp.restPoints.clear(); data.addRestPoint(camp, point);
            bee.setPos(Vec3.atBottomCenterOf(point)); bee.setOnGround(true); level.addFreshEntity(bee);
            data.add(camp, bee.getUUID(), "Hive sleeper", camp.dimension, bee.blockPosition(), PlayerOutpostSupplyType.NECTAR);
            level.setDayTime(18000); level.updateSkyBrightness(); bee.tickCount = 0;
            var beeRest = new PlayerOutpostRestGoal(bee);
            try {
                check(beeRest.canUse(), "Latex bee selects its linked hive pillow at night");
                beeRest.start(); beeRest.tick();
                ChangedSynergyConfig.COMMON.playerOutpostIdleRest.set(false);
                check(PlayerOutpostRestGoal.isResting(bee) && beeRest.canContinueToUse(),
                        "Night hive sleep uses the night setting independently of idle rests");
                PlayerOutpostRestGoal.cleanupAllForServer(owner.server);
                check(!bee.isPassenger() && !beeRest.canContinueToUse(), "Server shutdown cleanup releases active rest seats");
            } finally {
                beeRest.stop(); data.remove(camp, bee.getUUID()); bee.discard();
                ChangedSynergyConfig.COMMON.playerOutpostIdleRest.set(true);
                camp.beds.addAll(savedBeds); level.setDayTime(6000); level.updateSkyBrightness();
            }
            BlockPos oldMarker = home.marker; home.marker = home.marker.offset(120000, 0, 120000);
            BlockPos unloaded = home.marker.east(); home.restPoints.clear(); home.beds.add(unloaded);
            check(!level.hasChunkAt(unloaded), "Unloaded registration fixture stays outside generated chunks");
            restored.refreshRestPlaces(level, home);
            check(home.beds.contains(unloaded) && !level.hasChunkAt(unloaded), "Refresh preserves unloaded registrations without loading chunks");
            home.marker = oldMarker;
            data.removeDestroyedMarker(level, foot);
            check(camp.beds.size() == 1 && !camp.beds.contains(foot.south()), "Breaking a foot clears the registered head");
            level.setBlock(foot2.south(), Blocks.AIR.defaultBlockState(), 18);
            data.refreshRestPlaces(level, camp);
            check(camp.beds.isEmpty(), "Loaded incomplete beds are pruned after non-player block removal");
            System.out.println("CAMP_MULTI_BED_REST_POINTS_REGRESSION_PASS checks=" + checks);
        } finally {
            if (firstRest != null) firstRest.stop(); if (secondRest != null) secondRest.stop();
            PlayerOutpostRestGoal.cleanup(mob); PlayerOutpostRestGoal.cleanup(second);
            data.remove(camp, second.getUUID()); second.discard(); if (item != null) item.discard();
            for (var entry : states.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 18);
            camp.beds.clear(); camp.beds.addAll(beds); camp.restPoints.clear(); camp.restPoints.addAll(points);
            member.role = role; member.work = work; member.backupWork = backup; camp.meals = meals;
            mob.setPos(position); mob.tickCount = ticks; owner.setPos(ownerPosition); owner.setYRot(yaw); owner.setXRot(pitch);
            mob.setOnGround(grounded);
            owner.connection = connection; level.setDayTime(time); level.updateSkyBrightness();
            ChangedSynergyConfig.COMMON.playerOutposts.set(enabled);
            ChangedSynergyConfig.COMMON.playerOutpostRest.set(sleep); ChangedSynergyConfig.COMMON.playerOutpostIdleRest.set(idle);
            PlayerOutpostService.ensureGoal(mob);
        }
    }

    private static void link(ServerPlayer owner, BlockPos pos) throws Exception {
        owner.setPos(pos.getX() + 2.5, pos.getY(), pos.getZ() + 0.5);
        var shape = owner.level().getBlockState(pos).getShape(owner.level(), pos);
        // A chair's overall bounds include empty space; aim inside an actual voxel piece.
        owner.lookAt(EntityAnchorArgument.Anchor.EYES, shape.isEmpty() ? Vec3.atCenterOf(pos)
                : shape.toAabbs().get(0).getCenter().add(Vec3.atLowerCornerOf(pos)));
        int result = owner.server.getCommands().getDispatcher().execute("changedsynergy outpost link",
                owner.createCommandSourceStack().withSuppressedOutput());
        if (result != 1) System.out.println("CAMP_LINK_DIAGNOSTIC: eye=" + owner.getEyePosition()
                + " look=" + owner.getLookAngle() + " hit=" + owner.pick(6.0, 1.0F, false).getType()
                + " pos=" + ((net.minecraft.world.phys.BlockHitResult)owner.pick(6.0, 1.0F, false)).getBlockPos()
                + " state=" + owner.level().getBlockState(pos));
        check(result == 1, "Automatic link succeeds using the actual server raycast at " + pos);
    }

    private static void place(net.minecraft.server.level.ServerLevel level, java.util.Map<BlockPos, BlockState> states,
                              BlockPos pos, BlockState state) {
        states.putIfAbsent(pos, level.getBlockState(pos)); level.setBlock(pos, state, 18);
    }
    private static Object field(Object goal, String name) throws Exception {
        var field = goal.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(goal);
    }
    private static void set(Object goal, String name, Object value) throws Exception {
        var field = goal.getClass().getDeclaredField(name); field.setAccessible(true); field.set(goal, value);
    }
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
}
