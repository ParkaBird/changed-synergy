package net.parkabird.changedsynergy.regression;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.BondedRevivalService;
import net.parkabird.changedsynergy.ai.PatAnimationService;
import net.parkabird.changedsynergy.ai.PlayerOutpostData;
import net.parkabird.changedsynergy.ai.PlayerOutpostGoal;
import net.parkabird.changedsynergy.ai.PlayerOutpostMealGoal;
import net.parkabird.changedsynergy.ai.PlayerOutpostRestGoal;
import net.parkabird.changedsynergy.ai.PlayerOutpostService;
import net.parkabird.changedsynergy.ai.PlayerOutpostWorkGoal;

/** Real drop/container accounting and work-priority checks on the existing camp fixture. */
final class CampHaulFocusChecks {
    private static final String BATCH = "ChangedSynergyPlayerOutpostHaulBatch";

    static void run(ServerPlayer owner, ChangedEntity mob, PlayerOutpostData.Outpost camp, Container chest) throws Exception {
        var level = owner.serverLevel();
        var data = PlayerOutpostData.get(owner.server);
        var member = camp.members.get(mob.getUUID());
        var oldRole = member.role; var oldWork = member.work; var oldBackup = member.backupWork;
        String oldStatus = member.workStatus;
        var oldData = mob.getPersistentData().copy();
        var oldPosition = mob.position(); var oldOwnerPosition = owner.position();
        boolean oldGround = mob.onGround(), oldMeals = camp.meals;
        boolean oldEnabled = ChangedSynergyConfig.COMMON.playerOutposts.get();
        boolean oldHauling = ChangedSynergyConfig.COMMON.playerOutpostHauling.get();
        var stock = new ArrayList<ItemStack>();
        for (int i = 0; i < chest.getContainerSize(); i++) stock.add(chest.getItem(i).copy());
        var hidden = level.getEntitiesOfClass(ItemEntity.class, new AABB(camp.marker).inflate(PlayerOutpostData.RADIUS));
        var positions = new java.util.IdentityHashMap<ItemEntity, net.minecraft.world.phys.Vec3>();
        hidden.forEach(item -> positions.put(item, item.position()));
        var drops = new ArrayList<ItemEntity>();
        var goal = new PlayerOutpostWorkGoal(mob);
        mob.goalSelector.addGoal(1, goal);
        WrappedGoal wrapped = mob.goalSelector.getAvailableGoals().stream()
                .filter(entry -> entry.getGoal() == goal).findFirst().orElseThrow();
        try {
            hidden.forEach(item -> item.setPos(camp.marker.getX() + 100, camp.marker.getY(), camp.marker.getZ()));
            chest.clearContent(); camp.meals = false;
            ChangedSynergyConfig.COMMON.playerOutpostHauling.set(true);
            mob.getPersistentData().remove("ChangedSynergyPlayerOutpostWorkBag");
            mob.getPersistentData().remove("ChangedSynergyPlayerOutpostFarmBatch");
            mob.getPersistentData().remove("ChangedSynergyPlayerOutpostWorkOutput");
            mob.getPersistentData().remove(BATCH);
            PlayerOutpostGoal.setCargo(mob, ItemStack.EMPTY);
            data.setWork(camp, mob.getUUID(), PlayerOutpostData.Work.HAUL, PlayerOutpostData.Work.COOK);
            mob.setPos(6.5D, 90, 3.5D); mob.setOnGround(true); owner.setPos(6.5D, 90, 4.5D);
            var items = new net.minecraft.world.item.Item[]{Items.BONE, Items.ROTTEN_FLESH, Items.STRING, Items.FEATHER, Items.LEATHER};
            for (int i = 0; i < items.length; i++) {
                var drop = new ItemEntity(level, 6.5D + i, 90, 3.5D, new ItemStack(items[i], 3));
                drop.tickCount = 120; level.addFreshEntity(drop); drops.add(drop);
            }
            field(goal, "scanInitialized", true); field(goal, "nextScan", 0L);
            check(!PlayerOutpostService.isWorking(mob) && PatAnimationService.canNpcPat(mob), "Idle armed NPC can still pat");
            PatAnimationService.startFixed(mob, owner, 4);
            PatAnimationService.scheduleFixed(mob, owner, 20, 4);
            check(pats("SESSIONS").containsKey(mob.getUUID()) && pats("PENDING").containsKey(mob.getUUID()),
                    "Idle pat starts and its return stroke can be queued");
            for (int i = 1; i <= 4; i++) {
                check(goal.canUse() && read(goal, "action").toString().equals("PICKUP"), "Next eligible drop extends the batch");
                wrapped.start();
                if (i == 1) {
                    ChangedSynergyConfig.COMMON.playerOutpostHauling.set(false);
                    check(!goal.canContinueToUse(), "Disabling hauling stops an active uncollected drop");
                    ChangedSynergyConfig.COMMON.playerOutpostHauling.set(true);
                    check(goal.canContinueToUse(), "Re-enabling hauling allows its unchanged target");
                    check(PlayerOutpostService.isWorking(mob) && !PatAnimationService.canNpcPat(mob), "Active hauling cannot initiate a pat");
                    PatAnimationService.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, owner.server));
                    check(!pats("SESSIONS").containsKey(mob.getUUID()) && !pats("PENDING").containsKey(mob.getUUID()),
                            "Starting work cancels active and queued NPC pats");
                    PatAnimationService.startFixed(mob, owner, 4); PatAnimationService.scheduleFixed(mob, owner, 20, 4);
                    check(!pats("SESSIONS").containsKey(mob.getUUID()) && !pats("PENDING").containsKey(mob.getUUID()),
                            "Work cannot restart either fixed or delayed pats");
                    check(PatAnimationService.canNpcPat(owner), "Human-initiated patting is unchanged");
                }
                finish(goal, wrapped, mob);
                check(mob.getPersistentData().getInt(BATCH) == i && chest.isEmpty(), "Drops accumulate without an intermediate delivery");
                check(PlayerOutpostService.isWorking(mob) && !PatAnimationService.canNpcPat(mob), "Saved cargo blocks pats between actions");
            }
            check(drops.stream().filter(ItemEntity::isAlive).count() == 1, "Four drops collected; the fifth remains for the next trip");
            ChangedSynergyConfig.COMMON.playerOutpostHauling.set(false);
            check(goal.canUse() && read(goal, "action").toString().equals("DEPOSIT"), "Four collections end the hauling batch");
            var saved = mob.saveWithoutId(new CompoundTag());
            check(saved.getCompound("ForgeData").getInt(BATCH) == 4, "Batch progress is saved with its real cargo");
            var sanitize = BondedRevivalService.class.getDeclaredMethod("sanitizeSnapshot", CompoundTag.class);
            sanitize.setAccessible(true); sanitize.invoke(null, saved);
            check(!saved.getCompound("ForgeData").contains(BATCH), "Revival cannot restore a dropped hauling batch");
            for (int slot = 0; slot < chest.getContainerSize(); slot++) chest.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            wrapped.start(); finish(goal, wrapped, mob);
            check(PlayerOutpostWorkGoal.hasCargo(mob) && member.workStatus.equals("full_storage"), "Full storage retains the batch");
            var recreated = new PlayerOutpostWorkGoal(mob);
            field(recreated, "scanInitialized", true); field(recreated, "nextScan", 0L);
            check(recreated.canUse() && read(recreated, "action").toString().equals("DEPOSIT"), "Recreated AI delivers a saved full batch first");
            recreated.stop();
            chest.clearContent(); field(goal, "nextScan", 0L);
            check(goal.canUse() && read(goal, "action").toString().equals("DEPOSIT"), "Retry after storage space becomes available");
            wrapped.start(); finish(goal, wrapped, mob);
            check(!PlayerOutpostWorkGoal.hasCargo(mob) && !mob.getPersistentData().contains(BATCH), "Delivery clears only the completed batch");
            check(total(chest) == 12, "All twelve collected items arrive exactly once");
            check(!PlayerOutpostService.isWorking(mob) && PatAnimationService.canNpcPat(mob), "Idle NPC patting resumes after delivery");
            field(goal, "nextScan", 0L);
            check(!goal.canUse() && drops.get(4).isAlive(), "Hauling off delivers saved cargo but leaves the next drop untouched");
            ChangedSynergyConfig.COMMON.playerOutpostHauling.set(true);

            field(goal, "nextScan", 0L);
            check(goal.canUse() && read(goal, "action").toString().equals("PICKUP"), "Start a partial last batch");
            wrapped.start(); finish(goal, wrapped, mob);
            check(mob.getPersistentData().getInt(BATCH) == 1, "Last drop starts a partial batch");
            check(goal.canUse() && read(goal, "action").toString().equals("DEPOSIT"), "No further drop means unload before idle or backup cooking");
            camp.meals = true; camp.mealRequestedAt = level.getGameTime();
            check(!PlayerOutpostMealGoal.wantsMeal(mob) && !new PlayerOutpostGoal.Idle(mob).canUse()
                            && !new PlayerOutpostRestGoal(mob).canUse(), "Meal, idle and sleep all wait for partial delivery");
            wrapped.start(); finish(goal, wrapped, mob);
            check(total(chest) == 15 && !PlayerOutpostWorkGoal.hasCargo(mob) && PlayerOutpostMealGoal.wantsMeal(mob),
                    "Partial batch is delivered once before the meal becomes available");
            camp.meals = false;

            for (int i = 0; i < 3; i++) {
                var drop = new ItemEntity(level, 6.5D + i, 90, 3.5D, new ItemStack(Items.BEEF));
                drop.tickCount = 120; level.addFreshEntity(drop); drops.add(drop);
            }
            field(goal, "nextScan", 0L);
            check(goal.canUse(), "Another batch starts"); wrapped.start(); finish(goal, wrapped, mob);
            check(goal.canUse() && read(goal, "action").toString().equals("PICKUP"), "Another collection can follow");
            wrapped.start(); wrapped.stop(); field(goal, "nextScan", 0L);
            check(goal.canUse() && read(goal, "action").toString().equals("DEPOSIT"), "Interrupted hauling closes its partial batch before another pickup");
            goal.stop(); data.setWork(camp, mob.getUUID(), PlayerOutpostData.Work.COOK, null);
            field(goal, "nextScan", 0L);
            check(goal.canUse() && read(goal, "action").toString().equals("DEPOSIT"), "Changing to cooking cannot consume an undelivered haul batch");
            wrapped.start(); finish(goal, wrapped, mob);
            check(total(chest) == 16 && !PlayerOutpostWorkGoal.hasCargo(mob), "Interrupted cargo remains conserved");
            PlayerOutpostGoal.setCargo(mob, new ItemStack(Items.POTATO));
            check(PlayerOutpostService.isWorking(mob) && !PatAnimationService.canNpcPat(mob), "Original gathering cargo also suppresses automatic pats");
            ChangedSynergyConfig.COMMON.playerOutposts.set(false);
            check(!PlayerOutpostService.isWorking(mob) && PatAnimationService.canNpcPat(mob), "Disabling camp mechanics restores normal NPC pat behavior");
            System.out.println("CAMP_HAUL_BATCH_AND_WORK_FOCUS_PASS");
        } finally {
            ChangedSynergyConfig.COMMON.playerOutposts.set(oldEnabled);
            ChangedSynergyConfig.COMMON.playerOutpostHauling.set(oldHauling);
            wrapped.stop(); mob.goalSelector.removeGoal(goal); PatAnimationService.stop(mob); pats("PENDING").remove(mob.getUUID());
            drops.forEach(ItemEntity::discard); hidden.forEach(item -> item.setPos(positions.get(item)));
            chest.clearContent(); for (int i = 0; i < stock.size(); i++) chest.setItem(i, stock.get(i));
            member.role = oldRole; member.work = oldWork; member.backupWork = oldBackup; member.workStatus = oldStatus;
            camp.meals = oldMeals;
            mob.getPersistentData().getAllKeys().stream().toList().forEach(mob.getPersistentData()::remove);
            mob.getPersistentData().merge(oldData);
            mob.setPos(oldPosition); mob.setOnGround(oldGround); owner.setPos(oldOwnerPosition);
        }
    }

    private static void finish(PlayerOutpostWorkGoal goal, WrappedGoal wrapped, ChangedEntity mob) throws Exception {
        BlockPos stand = (BlockPos)read(goal, "stand");
        mob.setPos(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D);
        for (int tick = 0; tick < 12; tick++) goal.tick();
        check(!goal.canContinueToUse(), "One collection/delivery completes before the next goal action");
        wrapped.stop();
    }
    private static int total(Container chest) {
        int total = 0; for (int i = 0; i < chest.getContainerSize(); i++) total += chest.getItem(i).getCount(); return total;
    }
    private static Map<?, ?> pats(String name) throws Exception {
        Field field = PatAnimationService.class.getDeclaredField(name); field.setAccessible(true); return (Map<?, ?>)field.get(null);
    }
    private static void field(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
    private static Object read(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
