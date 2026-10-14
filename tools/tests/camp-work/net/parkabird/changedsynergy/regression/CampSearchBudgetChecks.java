package net.parkabird.changedsynergy.regression;

import java.util.LinkedHashMap;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.PlayerOutpostData;
import net.parkabird.changedsynergy.ai.PlayerOutpostService;
import net.parkabird.changedsynergy.ai.PlayerOutpostRestGoal;
import net.parkabird.changedsynergy.ai.PlayerOutpostWorkGoal;

/** Unreachable mature fields must not multiply the per-search pathfinding budget. */
final class CampSearchBudgetChecks {
    static void run(ServerLevel level, ChangedEntity mob, PlayerOutpostData.Outpost camp,
                    Container chest) throws Exception {
        var member = camp.members.get(mob.getUUID());
        var oldRole = member.role; var oldWork = member.work; var oldBackup = member.backupWork;
        var first = camp.farmStart; var last = camp.farmEnd; var composter = camp.composter;
        var position = mob.position(); boolean ground = mob.onGround(), meals = camp.meals;
        long time = level.getDayTime();
        var beds = new java.util.LinkedHashSet<>(camp.beds);
        var restPoints = new java.util.LinkedHashSet<>(camp.restPoints);
        boolean farming = ChangedSynergyConfig.COMMON.playerOutpostFarming.get();
        boolean resting = ChangedSynergyConfig.COMMON.playerOutpostRest.get();
        var mobData = mob.getPersistentData().copy();
        var states = new LinkedHashMap<BlockPos, BlockState>();
        var stock = new java.util.ArrayList<net.minecraft.world.item.ItemStack>();
        for (int slot = 0; slot < chest.getContainerSize(); slot++) stock.add(chest.getItem(slot).copy());
        var navigationField = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(Mob.class, "f_21344_"); navigationField.setAccessible(true);
        var original = mob.getNavigation();
        var navigation = new CountingNavigation(mob, level);
        var goal = new PlayerOutpostWorkGoal(mob);
        PlayerOutpostRestGoal rest = null;
        try {
            PlayerOutpostService.releaseCargo(mob); chest.clearContent(); camp.meals = false; camp.composter = null;
            member.role = PlayerOutpostData.Role.SUPPLY; member.work = PlayerOutpostData.Work.FARM; member.backupWork = null;
            ChangedSynergyConfig.COMMON.playerOutpostFarming.set(true);
            camp.farmStart = camp.marker.offset(-4, 0, 8); camp.farmEnd = camp.farmStart.east(7);
            var potatoes = (CropBlock)Blocks.POTATOES;
            for (int index = 0; index < 8; index++) {
                var crop = camp.farmStart.east(index);
                states.put(crop, level.getBlockState(crop)); states.put(crop.below(), level.getBlockState(crop.below()));
                level.setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState(), 3);
                level.setBlock(crop, potatoes.getStateForAge(potatoes.getMaxAge()), 3);
            }
            mob.setPos(Vec3.atBottomCenterOf(camp.marker.offset(-10, 0, -10))); mob.setOnGround(true);
            navigationField.set(mob, navigation);
            set(goal, "scanInitialized", true); set(goal, "nextScan", 0L);
            check(!goal.canUse() && navigation.calls == 16,
                    "Eight mature unreachable crops share sixteen path probes in one complete work search");
            int cursor = (int)get(goal, "farmCursor");
            check(cursor > 0 && member.workStatus.equals("unreachable"),
                    "Budget exhaustion keeps the rotating crop cursor and reports a blocked route,"
                            + " even after missing composter fallback: cursor=" + cursor + " status=" + member.workStatus);
            navigation.reject = false; navigation.calls = 0; set(goal, "nextScan", 0L);
            check(goal.canUse() && get(goal, "action").toString().equals("FARM")
                            && navigation.calls <= 16 && (int)get(goal, "farmCursor") > cursor,
                    "The next work search refreshes its budget and reaches a later crop instead of retrying only the first");
            check(level.getBlockState(camp.farmStart).getValue(CropBlock.AGE) == potatoes.getMaxAge(),
                    "Route probes never harvest, consume stock or alter an unreachable crop");
            goal.stop();
            camp.beds.clear(); camp.restPoints.clear(); member.role = PlayerOutpostData.Role.RESIDENT;
            ChangedSynergyConfig.COMMON.playerOutpostRest.set(true);
            level.setDayTime(18000); level.updateSkyBrightness();
            for (int index = 0; index < 9; index++) {
                var head = camp.marker.offset(-12 + index * 3, 0, 5);
                var foot = head.south();
                states.putIfAbsent(head, level.getBlockState(head)); states.putIfAbsent(foot, level.getBlockState(foot));
                var base = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
                level.setBlock(foot, base.setValue(BedBlock.PART, BedPart.FOOT), 3);
                level.setBlock(head, base.setValue(BedBlock.PART, BedPart.HEAD), 3);
                camp.beds.add(head);
            }
            navigation.calls = 0; navigation.reject = true;
            rest = new PlayerOutpostRestGoal(mob);
            check(!rest.canUse() && navigation.calls == 8,
                    "Nine blocked linked beds share eight path probes per sleeping attempt");
            check((int)get(rest, "restCursor") == 8,
                    "Blocked sleeping search remembers the untried ninth bed");
            navigation.calls = 0; navigation.reject = false; set(rest, "nextAttempt", 0L);
            check(rest.canUse() && navigation.calls <= 8 && (int)get(rest, "restCursor") == 0,
                    "The next sleeping attempt reaches a later bed and restores normal nearest-bed preference");
            System.out.println("CAMP_BOUNDED_WORK_SEARCH_PASS: 7 checks");
        } finally {
            if (rest != null) rest.stop();
            goal.stop(); navigationField.set(mob, original);
            for (var entry : states.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 3);
            camp.farmStart = first; camp.farmEnd = last; camp.composter = composter; camp.meals = meals;
            camp.beds.clear(); camp.beds.addAll(beds); camp.restPoints.clear(); camp.restPoints.addAll(restPoints);
            member.role = oldRole; member.work = oldWork; member.backupWork = oldBackup;
            ChangedSynergyConfig.COMMON.playerOutpostFarming.set(farming);
            ChangedSynergyConfig.COMMON.playerOutpostRest.set(resting);
            level.setDayTime(time); level.updateSkyBrightness();
            chest.clearContent(); for (int slot = 0; slot < stock.size(); slot++) chest.setItem(slot, stock.get(slot));
            mob.getPersistentData().getAllKeys().stream().toList().forEach(mob.getPersistentData()::remove);
            mob.getPersistentData().merge(mobData); mob.setPos(position); mob.setOnGround(ground);
        }
    }

    private static final class CountingNavigation extends GroundPathNavigation {
        private int calls;
        private boolean reject = true;
        private CountingNavigation(Mob mob, ServerLevel level) { super(mob, level); }
        @Override public Path createPath(BlockPos pos, int reach) {
            calls++;
            return reject ? null : super.createPath(pos, reach);
        }
    }
    private static void set(Object value, String field, Object content) throws Exception {
        var member = value.getClass().getDeclaredField(field); member.setAccessible(true); member.set(value, content);
    }
    private static Object get(Object value, String field) throws Exception {
        var member = value.getClass().getDeclaredField(field); member.setAccessible(true); return member.get(value);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
