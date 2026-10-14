package net.parkabird.changedsynergy.regression;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.phys.AABB;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Outpost;
import net.parkabird.changedsynergy.ai.PlayerOutpostData;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Work;
import net.parkabird.changedsynergy.ai.PlayerOutpostWorkGoal;
import net.parkabird.changedsynergy.ai.PlayerOutpostService;

/** Tests NPC input/output accounting against actual vanilla devices. */
public final class CampProductionChecks {
    public static void run(ServerLevel level, ChangedEntity mob, Outpost camp,
                           PlayerOutpostWorkGoal goal, Container chest, BlockPos crop) throws Exception {
        PlayerOutpostService.releaseCargo(mob);
        chest.clearContent();
        camp.rawReserve = 2;
        camp.seedReserve = 3;
        chest.setItem(0, new ItemStack(Items.BEEF, 6));
        var fire = (CampfireBlockEntity)level.getBlockEntity(camp.cooking);
        check(fire.getItems().stream().allMatch(ItemStack::isEmpty), "Cooking starts with empty real slots");
        int duration = fire.getCookableRecipe(new ItemStack(Items.BEEF)).orElseThrow().getCookingTime();
        field(goal, "block", camp.storage);
        mob.setPos(camp.storage.getX() + 1.5, camp.storage.getY(), camp.storage.getZ() + 0.5);
        call(goal, "fetchFood", level);
        check(count(chest, Items.BEEF) == 2 && PlayerOutpostWorkGoal.hasCargo(mob),
                "Cook fetches four actual ingredients at the linked container and respects the reserve");
        field(goal, "block", camp.cooking);
        mob.setPos(camp.cooking.getX() + 1.5, camp.cooking.getY(), camp.cooking.getZ() + 0.5);
        call(goal, "cook", level);
        check(count(chest, Items.BEEF) == 2 && fire.getItems().stream().mapToInt(ItemStack::getCount).sum() == 4,
                "Cook preserves raw reserve and fills four real campfire slots");
        call(goal, "cook", level);
        check(count(chest, Items.BEEF) == 2, "Full campfire cannot consume extra stock");
        int before = drops(level, camp.cooking, Items.COOKED_BEEF);
        for (int tick = 0; tick < duration; tick++)
            CampfireBlockEntity.cookTick(level, camp.cooking, level.getBlockState(camp.cooking), fire);
        check(drops(level, camp.cooking, Items.COOKED_BEEF) - before == 4, "Exactly four native cooked outputs");
        for (var drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(camp.cooking).inflate(2),
                item -> item.getItem().is(Items.COOKED_BEEF))) drop.discard();
        call(goal, "chooseOutput", level);
        check(drops(level, camp.cooking, Items.COOKED_BEEF) == 0, "Player taking outputs never creates replacement food");
        PlayerOutpostService.releaseCargo(mob);
        foodRecipeFallback(level, mob, camp, goal, chest, fire);

        camp.composter = camp.marker.offset(2, 0, -2);
        level.setBlock(camp.composter, Blocks.COMPOSTER.defaultBlockState(), 3);
        field(goal, "block", camp.composter);
        chest.clearContent();
        chest.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 4));
        call(goal, "compost", level);
        check(count(chest, Items.WHEAT_SEEDS) == 3
                        && level.getBlockState(camp.composter).getValue(ComposterBlock.LEVEL) == 1,
                "One real seed is consumed and the first compost layer actually increases");
        call(goal, "compost", level);
        check(count(chest, Items.WHEAT_SEEDS) == 3, "Seed reserve cannot be composted");
        level.setBlock(camp.composter, Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, 8), 3);
        before = drops(level, camp.composter, Items.BONE_MEAL) + cargo(mob, Items.BONE_MEAL);
        call(goal, "compost", level);
        call(goal, "compost", level);
        check(drops(level, camp.composter, Items.BONE_MEAL) + cargo(mob, Items.BONE_MEAL) - before == 1,
                "Ready composter produces one real bone meal without duplication");
        check(cargo(mob, Items.BONE_MEAL) == 1
                        && level.getBlockState(camp.composter).getValue(ComposterBlock.LEVEL) == 0,
                "The farmer collects the native output before starting another task");
        var beforeDelivery = mob.position();
        field(goal, "block", camp.storage);
        mob.setPos(camp.storage.getX()+1.5, camp.storage.getY(), camp.storage.getZ()+0.5);
        call(goal, "deposit", level);
        check(cargo(mob, Items.BONE_MEAL) == 0 && count(chest, Items.BONE_MEAL) == 1,
                "The collected bone meal reaches linked storage without a hauler");
        chest.setItem(1, ItemStack.EMPTY);
        mob.setPos(beforeDelivery);
        compostLifecycle(level, mob, camp, chest);
        compostDeliveryAndUse(level, mob, camp, chest);
        chest.setItem(1, new ItemStack(Items.BONE_MEAL));
        CropBlock potatoes = (CropBlock)Blocks.POTATOES;
        level.setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState(), 3);
        level.setBlock(crop, potatoes.getStateForAge(0), 3);
        field(goal, "block", crop);
        call(goal, "fertilize", level);
        check(count(chest, Items.BONE_MEAL) == 0 && level.getBlockState(crop).getValue(CropBlock.AGE) > 0,
                "Fertilization consumes actual bone meal and advances the actual crop");
        level.setBlock(crop, potatoes.getStateForAge(potatoes.getMaxAge()), 3);
        BlockPos oldStart = camp.farmStart, oldEnd = camp.farmEnd;
        camp.farmStart = camp.marker.offset(7, 0, 7);
        camp.farmEnd = camp.marker.offset(8, 0, 8);
        call(goal, "farm", level);
        check(potatoes.isMaxAge(level.getBlockState(crop)), "Changing the farm cancels the old harvest target");
        camp.farmStart = oldStart; camp.farmEnd = oldEnd;
        integratedFarming(level, mob, camp, goal, chest, crop);
        farmBatches(level, mob, camp, chest, crop);
        CampSearchBudgetChecks.run(level, mob, camp, chest);
        System.out.println("CAMP_PRODUCTION_ACCOUNTING_PASS");
    }

    private static void compostLifecycle(ServerLevel level, ChangedEntity mob, Outpost camp,
                                         Container chest) throws Exception {
        var config = net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON;
        var data = PlayerOutpostData.get(level.getServer());
        var member = camp.members.get(mob.getUUID());
        Work oldWork = member.work, oldBackup = member.backupWork;
        var oldData = mob.getPersistentData().copy();
        var oldPosition = mob.position();
        var oldState = level.getBlockState(camp.composter);
        boolean oldGround = mob.onGround(), oldMeals = camp.meals;
        boolean oldComposting = config.playerOutpostComposting.get(), oldFarming = config.playerOutpostFarming.get();
        int oldReserve = camp.seedReserve;
        BlockPos oldStart = camp.farmStart, oldEnd = camp.farmEnd;
        var oldStock = new java.util.ArrayList<ItemStack>();
        for (int i = 0; i < chest.getContainerSize(); i++) oldStock.add(chest.getItem(i).copy());
        float chance = ComposterBlock.COMPOSTABLES.getFloat(Items.WHEAT_SEEDS);
        PlayerOutpostWorkGoal goal = new PlayerOutpostWorkGoal(mob);
        try {
            data.setWork(camp, mob.getUUID(), Work.FARM, null);
            camp.farmStart = null; camp.farmEnd = null; camp.meals = false; camp.seedReserve = 3;
            mob.setPos(camp.composter.getX() + 0.5D, camp.composter.getY(), camp.composter.getZ() - 0.5D);
            mob.setOnGround(true);
            mob.getPersistentData().remove("ChangedSynergyPlayerOutpostWorkBag");
            mob.getPersistentData().remove("ChangedSynergyPlayerOutpostWorkOutput");
            config.playerOutpostFarming.set(true); config.playerOutpostComposting.set(true);
            chest.clearContent(); chest.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 6));
            level.setBlock(camp.composter, Blocks.COMPOSTER.defaultBlockState(), 3);
            field(goal, "scanInitialized", true); field(goal, "nextScan", 0L);
            check(goal.canUse() && read(goal, "action").toString().equals("COMPOST"), "Select real surplus-seed work");
            goal.start();
            for (int i = 0; i < 12; i++) goal.tick();
            check(count(chest, Items.WHEAT_SEEDS) == 5
                            && level.getBlockState(camp.composter).getValue(ComposterBlock.LEVEL) == 1,
                    "One work action consumes exactly one real seed and updates the native block");
            goal.tick();
            check(count(chest, Items.WHEAT_SEEDS) == 5,
                    "A stale tick after completion cannot perform a second compost operation");
            goal.stop();

            // Make native failure deterministic, then restore the vanilla item's probability.
            ComposterBlock.COMPOSTABLES.put(Items.WHEAT_SEEDS, 0.0F);
            field(goal, "block", camp.composter);
            call(goal, "compost", level);
            check(count(chest, Items.WHEAT_SEEDS) == 4
                            && level.getBlockState(camp.composter).getValue(ComposterBlock.LEVEL) == 1,
                    "Unsuccessful native composting consumes a real seed without inventing a layer");
            ComposterBlock.COMPOSTABLES.put(Items.WHEAT_SEEDS, chance);

            for (boolean reserveChanged : new boolean[]{false, true}) {
                chest.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 6)); camp.seedReserve = 3;
                field(goal, "nextScan", 0L);
                check(goal.canUse() && read(goal, "action").toString().equals("COMPOST"), "Select before storage changes");
                goal.start();
                if (reserveChanged) camp.seedReserve = 6; else chest.clearContent();
                check(!goal.canContinueToUse(), "Losing surplus during the approach cancels composting");
                for (int i = 0; i < 13; i++) goal.tick();
                check((int)read(goal, "actionTicks") == 0
                                && level.getBlockState(camp.composter).getValue(ComposterBlock.LEVEL) == 1,
                        "No surplus means no fake swing, compost operation or block progress");
                goal.stop();
            }
            camp.seedReserve = 3; chest.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 6));
            level.setBlock(camp.composter, Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, 7), 3);
            field(goal, "nextScan", 0L);
            check(!goal.canUse(), "A ripening composter is not an input task even when seeds remain");

            // A full bag must leave the real output in the world, then recover and deliver it once.
            var cargoInventory = net.parkabird.changedsynergy.world.inventory.PlayerOutpostCargoInventory.inventory(mob);
            cargoInventory.setWorkItems(java.util.List.of(new ItemStack(Items.STONE,64), new ItemStack(Items.DIRT,64),
                    new ItemStack(Items.COBBLESTONE,64), new ItemStack(Items.COAL,64),
                    new ItemStack(Items.APPLE,64), new ItemStack(Items.WHEAT,64)));
            chest.clearContent();
            level.setBlock(camp.composter, Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL,8),3);
            field(goal,"block",camp.composter);
            int existingBoneMeal = drops(level,camp.composter,Items.BONE_MEAL);
            call(goal,"compost",level); call(goal,"compost",level);
            check(drops(level,camp.composter,Items.BONE_MEAL) == existingBoneMeal+1 && cargo(mob,Items.BONE_MEAL)==0,
                    "A full farmer bag leaves one native output without phantom or duplicate cargo");
            cargoInventory.setWorkItems(java.util.List.of());
            field(goal,"nextScan",0L);
            check(goal.canUse() && read(goal,"action").toString().equals("PICKUP"),
                    "The farmer recovers its own pending bone meal when carrying space becomes available");
            goal.start(); for (int tick=0;tick<12;tick++)goal.tick(); goal.stop();
            check(cargo(mob,Items.BONE_MEAL)==1 && drops(level,camp.composter,Items.BONE_MEAL)==existingBoneMeal
                            && !mob.getPersistentData().contains("ChangedSynergyPlayerOutpostWorkOutput"),
                    "Recovery collects the real output and clears the pending record exactly once");
            field(goal,"nextScan",0L);
            check(goal.canUse() && read(goal,"action").toString().equals("DEPOSIT"),
                    "Recovery hands bone meal to the existing delivery chain before more farming");
            goal.start(); mob.setPos(camp.storage.getX()+1.5,camp.storage.getY(),camp.storage.getZ()+0.5);
            for (int tick=0;tick<12;tick++)goal.tick(); goal.stop();
            check(cargo(mob,Items.BONE_MEAL)==0 && count(chest,Items.BONE_MEAL)==1,
                    "Recovered bone meal reaches storage without loss or duplication");
            mob.setPos(camp.composter.getX()+0.5,camp.composter.getY(),camp.composter.getZ()-0.5);
            chest.clearContent(); chest.setItem(0,new ItemStack(Items.WHEAT_SEEDS,6));

            for (boolean disableFarming : new boolean[]{false, true}) {
                config.playerOutpostFarming.set(true); config.playerOutpostComposting.set(true);
                level.setBlock(camp.composter, Blocks.COMPOSTER.defaultBlockState(), 3);
                field(goal, "nextScan", 0L);
                check(goal.canUse(), "Select work before runtime switch is disabled");
                goal.start();
                if (disableFarming) config.playerOutpostFarming.set(false); else config.playerOutpostComposting.set(false);
                for (int i = 0; i < 13; i++) goal.tick();
                check(count(chest, Items.WHEAT_SEEDS) == 6
                                && level.getBlockState(camp.composter).getValue(ComposterBlock.LEVEL) == 0,
                        "Disabling farming or composting prevents already-queued consumption");
                goal.stop();
            }
            System.out.println("CAMP_COMPOST_LIFECYCLE_REGRESSION_PASS");
        } finally {
            goal.stop(); ComposterBlock.COMPOSTABLES.put(Items.WHEAT_SEEDS, chance);
            camp.farmStart = oldStart; camp.farmEnd = oldEnd; camp.meals = oldMeals; camp.seedReserve = oldReserve;
            data.setWork(camp, mob.getUUID(), oldWork, oldBackup);
            config.playerOutpostFarming.set(oldFarming); config.playerOutpostComposting.set(oldComposting);
            level.setBlock(camp.composter, oldState, 3); chest.clearContent();
            for (int i = 0; i < oldStock.size(); i++) chest.setItem(i, oldStock.get(i));
            mob.getPersistentData().getAllKeys().stream().toList().forEach(mob.getPersistentData()::remove);
            mob.getPersistentData().merge(oldData);
            mob.setPos(oldPosition.x, oldPosition.y, oldPosition.z); mob.setOnGround(oldGround);
        }
    }

    private static void compostDeliveryAndUse(ServerLevel level, ChangedEntity mob, Outpost camp,
                                              Container chest) throws Exception {
        var data=PlayerOutpostData.get(level.getServer());
        var member=camp.members.get(mob.getUUID());
        var oldWork=member.work;var oldBackup=member.backupWork;
        var oldData=mob.getPersistentData().copy();var oldPosition=mob.position();
        var oldStart=camp.farmStart;var oldEnd=camp.farmEnd;boolean oldMeals=camp.meals;
        var crop=camp.marker.offset(7,0,7);var oldCrop=level.getBlockState(crop);var oldSoil=level.getBlockState(crop.below());
        var oldComposter=level.getBlockState(camp.composter);
        var stock=new java.util.ArrayList<ItemStack>();
        for(int i=0;i<chest.getContainerSize();i++)stock.add(chest.getItem(i).copy());
        var goal=new PlayerOutpostWorkGoal(mob);
        try {
            data.setWork(camp,mob.getUUID(),Work.FARM,null);camp.meals=false;
            camp.farmStart=crop;camp.farmEnd=crop;
            level.setBlock(crop.below(),Blocks.FARMLAND.defaultBlockState(),3);
            level.setBlock(crop,((CropBlock)Blocks.WHEAT).getStateForAge(0),3);
            mob.getPersistentData().remove("ChangedSynergyPlayerOutpostWorkBag");
            mob.getPersistentData().remove("ChangedSynergyPlayerOutpostWorkOutput");
            chest.clearContent();mob.setOnGround(true);
            level.setBlock(camp.composter,Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL,8),3);
            field(goal,"scanInitialized",true);field(goal,"nextScan",0L);
            check(goal.canUse()&&read(goal,"action").toString().equals("COMPOST"),"Ready compost is selected with an immature field and no stored fertilizer");
            completeAtTarget(goal,mob);
            check(cargo(mob,Items.BONE_MEAL)==1&&count(chest,Items.BONE_MEAL)==0,"Produced bone meal enters cargo before any fertilization");
            for(int i=0;i<chest.getContainerSize();i++)chest.setItem(i,new ItemStack(Items.STONE,64));
            field(goal,"nextScan",0L);
            check(goal.canUse()&&read(goal,"action").toString().equals("DEPOSIT"),"Bone meal delivery takes precedence over immature crops");
            completeAtTarget(goal,mob);
            check(cargo(mob,Items.BONE_MEAL)==1&&count(chest,Items.BONE_MEAL)==0,"A full container retains the real bone meal in cargo");
            chest.setItem(0,ItemStack.EMPTY);field(goal,"nextScan",0L);
            check(goal.canUse()&&read(goal,"action").toString().equals("DEPOSIT"),"Delivery retries once storage is available");
            completeAtTarget(goal,mob);
            check(cargo(mob,Items.BONE_MEAL)==0&&count(chest,Items.BONE_MEAL)==1,"Compost bone meal is actually deposited exactly once");
            field(goal,"nextScan",0L);
            check(goal.canUse()&&read(goal,"action").toString().equals("FERTILIZE"),"Automatic farming separately fetches the deposited bone meal");
            check(count(chest,Items.BONE_MEAL)==1&&member.workStatus.equals("fertilizing"),"Approaching the crop does not consume bone meal and reports fertilization");
            completeAtTarget(goal,mob);
            check(count(chest,Items.BONE_MEAL)==0&&cargo(mob,Items.BONE_MEAL)==0
                    &&level.getBlockState(crop).getValue(CropBlock.AGE)>0,"The apparent disappearance is actual crop fertilization after storage delivery");
            chest.setItem(0,new ItemStack(Items.BONE_MEAL));
            level.setBlock(crop,((CropBlock)Blocks.WHEAT).getStateForAge(7),3);
            field(goal,"nextScan",0L);
            goal.canUse();
            check(read(goal,"action")==null||!read(goal,"action").toString().equals("FERTILIZE"),"Mature crops do not select fertilizer consumption");
            goal.stop();check(count(chest,Items.BONE_MEAL)==1,"Bone meal remains stored when no immature crop can use it");
            System.out.println("CAMP_COMPOST_DELIVERY_AND_FERTILIZATION_PASS");
        } finally {
            goal.stop();data.setWork(camp,mob.getUUID(),oldWork,oldBackup);
            camp.farmStart=oldStart;camp.farmEnd=oldEnd;camp.meals=oldMeals;
            level.setBlock(crop,oldCrop,3);level.setBlock(crop.below(),oldSoil,3);level.setBlock(camp.composter,oldComposter,3);
            chest.clearContent();for(int i=0;i<stock.size();i++)chest.setItem(i,stock.get(i));
            mob.getPersistentData().getAllKeys().stream().toList().forEach(mob.getPersistentData()::remove);
            mob.getPersistentData().merge(oldData);mob.setPos(oldPosition.x,oldPosition.y,oldPosition.z);
        }
    }

    private static void completeAtTarget(PlayerOutpostWorkGoal goal,ChangedEntity mob) throws Exception {
        var stand=(BlockPos)read(goal,"stand");goal.start();
        mob.setPos(stand.getX()+0.5,stand.getY(),stand.getZ()+0.5);mob.setOnGround(true);
        for(int i=0;i<12;i++)goal.tick();goal.stop();
    }

    private static void farmBatches(ServerLevel level, ChangedEntity mob, Outpost camp,
                                    Container chest, BlockPos crop) throws Exception {
        final String batchKey = "ChangedSynergyPlayerOutpostFarmBatch";
        var member = camp.members.get(mob.getUUID());
        var data = PlayerOutpostData.get(level.getServer());
        Work oldWork = member.work, oldBackup = member.backupWork;
        BlockPos oldStart = camp.farmStart, oldEnd = camp.farmEnd;
        var oldPosition = mob.position();
        boolean oldGround = mob.onGround(), oldMeals = camp.meals;
        boolean oldFarming = net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.playerOutpostFarming.get();
        var oldData = mob.getPersistentData().copy();
        var oldBlocks = new java.util.LinkedHashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        var oldStock = new java.util.ArrayList<ItemStack>();
        for (int i = 0; i < chest.getContainerSize(); i++) oldStock.add(chest.getItem(i).copy());
        PlayerOutpostWorkGoal goal = new PlayerOutpostWorkGoal(mob);
        try {
            chest.clearContent(); PlayerOutpostService.releaseCargo(mob);
            camp.meals = false; mob.setOnGround(true);
            net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.playerOutpostFarming.set(true);
            data.setWork(camp, mob.getUUID(), Work.FARM, Work.COOK);
            camp.farmStart = crop; camp.farmEnd = crop.east(4);
            CropBlock potatoes = (CropBlock)Blocks.POTATOES;
            for (int i = 0; i < 5; i++) {
                BlockPos pos = crop.east(i);
                oldBlocks.put(pos, level.getBlockState(pos));
                oldBlocks.put(pos.below(), level.getBlockState(pos.below()));
                level.setBlock(pos.below(), Blocks.FARMLAND.defaultBlockState(), 3);
                level.setBlock(pos, potatoes.getStateForAge(potatoes.getMaxAge()), 3);
            }
            field(goal, "scanInitialized", true); field(goal, "nextScan", 0L);
            for (int i = 1; i <= 4; i++) {
                check(goal.canUse() && read(goal, "action").toString().equals("FARM"),
                        "Farmer chooses crop " + i + " without a chest trip");
                perform(goal, mob);
                check(mob.getPersistentData().getInt(batchKey) == i && count(chest, Items.POTATO) == 0,
                        "Each harvested plant advances the saved batch without early delivery");
                check((long)read(goal, "nextScan") == level.getGameTime(),
                        "Next crop scan is ready without the ordinary work delay");
            }
            check(goal.canUse() && read(goal, "action").toString().equals("DEPOSIT")
                            && potatoes.isMaxAge(level.getBlockState(crop.east(4))),
                    "Four harvested plants trigger delivery while another mature crop remains");
            for (int i = 0; i < chest.getContainerSize(); i++) chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            perform(goal, mob);
            check(PlayerOutpostWorkGoal.hasCargo(mob) && member.workStatus.equals("full_storage")
                            && mob.getPersistentData().getInt(batchKey) == 4,
                    "Full storage retains the closed harvest batch without resuming harvesting");
            check(!new net.parkabird.changedsynergy.ai.PlayerOutpostGoal.Idle(mob).canUse(),
                    "Farmer cannot enter idle wandering while a harvest is waiting for storage");
            var saved = mob.saveWithoutId(new CompoundTag()).getCompound("ForgeData");
            check(saved.getInt(batchKey) == 4 && !saved.getList("ChangedSynergyPlayerOutpostWorkBag", 10).isEmpty(),
                    "Harvest count and real work cargo survive entity serialization");
            goal = new PlayerOutpostWorkGoal(mob);
            field(goal, "scanInitialized", true); field(goal, "nextScan", 0L);
            check(goal.canUse() && read(goal, "action").toString().equals("DEPOSIT"),
                    "Recreated AI honours the saved closed batch rather than harvesting more");
            chest.clearContent();
            Method bag = PlayerOutpostWorkGoal.class.getDeclaredMethod("bag", ChangedEntity.class);
            bag.setAccessible(true);
            int carried = ((java.util.List<ItemStack>)bag.invoke(null, mob)).stream()
                    .filter(item -> item.is(Items.POTATO)).mapToInt(ItemStack::getCount).sum();
            perform(goal, mob);
            check(count(chest, Items.POTATO) == carried && !PlayerOutpostWorkGoal.hasCargo(mob)
                            && !mob.getPersistentData().contains(batchKey),
                    "Successful delivery stores real produce exactly once and starts a fresh batch");

            field(goal, "nextScan", 0L);
            check(goal.canUse() && read(goal, "action").toString().equals("FARM"), "Next batch harvests the last plant");
            perform(goal, mob);
            check(mob.getPersistentData().getInt(batchKey) == 1, "Last plant leaves a partial batch");
            chest.setItem(20, new ItemStack(Items.BONE_MEAL, 8));
            chest.setItem(21, new ItemStack(Items.WHEAT_SEEDS, camp.seedReserve + 8));
            check(goal.canUse() && read(goal, "action").toString().equals("DEPOSIT"),
                    "No mature crop forces partial delivery before fertilizer, composting or cooking backup");
            camp.meals = true; camp.mealRequestedAt = level.getGameTime();
            check(!net.parkabird.changedsynergy.ai.PlayerOutpostMealGoal.wantsMeal(mob),
                    "A meal waits for the partial harvest to be unloaded");
            perform(goal, mob);
            check(net.parkabird.changedsynergy.ai.PlayerOutpostMealGoal.wantsMeal(mob),
                    "Meal request becomes available once the partial harvest is unloaded");
            camp.meals = false;

            level.setBlock(crop, potatoes.getStateForAge(potatoes.getMaxAge()), 3);
            level.setBlock(crop.east(), potatoes.getStateForAge(potatoes.getMaxAge()), 3);
            field(goal, "nextScan", 0L);
            check(goal.canUse() && read(goal, "action").toString().equals("FARM"), "Start another farming batch");
            perform(goal, mob);
            check(goal.canUse() && read(goal, "action").toString().equals("FARM"), "Continue that batch before interruption");
            goal.start(); field(goal, "remaining", 0); goal.stop(); field(goal, "nextScan", 0L);
            check(goal.canUse() && read(goal, "action").toString().equals("DEPOSIT"),
                    "Timed-out or interrupted farm returns its partial cargo before harvesting again");
            goal.stop();
            data.setWork(camp, mob.getUUID(), Work.COOK, null);
            field(goal, "nextScan", 0L);
            check(goal.canUse() && read(goal, "action").toString().equals("DEPOSIT"),
                    "Switching to cooking cannot consume a saved harvest batch as cooking inputs");
            goal.stop();
            net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.playerOutpostFarming.set(false);
            field(goal, "nextScan", 0L);
            check(goal.canUse() && read(goal, "action").toString().equals("DEPOSIT"),
                    "Disabling farming still permits delivery of crops already collected");
            perform(goal, mob);
            System.out.println("CAMP_FARM_BATCH_REGRESSION_PASS");
        } finally {
            goal.stop();
            for (var entry : oldBlocks.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 3);
            camp.farmStart = oldStart; camp.farmEnd = oldEnd; camp.meals = oldMeals;
            data.setWork(camp, mob.getUUID(), oldWork, oldBackup);
            chest.clearContent();
            for (int i = 0; i < oldStock.size(); i++) chest.setItem(i, oldStock.get(i));
            mob.getPersistentData().getAllKeys().stream().toList().forEach(mob.getPersistentData()::remove);
            mob.getPersistentData().merge(oldData);
            mob.setPos(oldPosition.x, oldPosition.y, oldPosition.z); mob.setOnGround(oldGround);
            net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.playerOutpostFarming.set(oldFarming);
        }
    }

    private static void perform(PlayerOutpostWorkGoal goal, ChangedEntity mob) throws Exception {
        goal.start();
        BlockPos stand = (BlockPos)read(goal, "stand");
        mob.setPos(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D);
        for (int i = 0; i < 12; i++) goal.tick();
        check(!goal.canContinueToUse(), "One work action ends before selecting the next destination");
        goal.stop();
    }

    private static void foodRecipeFallback(ServerLevel level, ChangedEntity mob, Outpost camp,
                                          PlayerOutpostWorkGoal goal, Container chest, CampfireBlockEntity fire) throws Exception {
        var recipes = java.util.List.copyOf(level.getRecipeManager().getRecipes());
        // Farmer's Delight supplies a native kelp recipe; isolate the actual furnace-only case as well.
        level.getRecipeManager().replaceRecipes(recipes.stream().filter(recipe ->
                recipe.getType() != RecipeType.CAMPFIRE_COOKING || recipe.getIngredients().stream()
                        .noneMatch(ingredient -> ingredient.test(new ItemStack(Items.KELP)))).toList());
        try {
        check(PlayerOutpostWorkGoal.campfireRecipe(level, new ItemStack(Items.RAW_IRON)).isEmpty(),
                "Food fallback rejects ores");
        check(fire.getCookableRecipe(new ItemStack(Items.KELP)).isEmpty(),
                "Ordinary player campfire insertion is unchanged for furnace-only food");
        check(fire.placeFood(mob, new ItemStack(Items.KELP), 1), "Fixture inserts one real kelp into ordinary fire");
        CampfireBlockEntity.cookTick(level, camp.cooking, level.getBlockState(camp.cooking), fire);
        check(drops(level, camp.cooking, Items.DRIED_KELP) == 0,
                "An unmarked ordinary fire does not gain Synergy cooking recipes");
        for (var item : level.getEntitiesOfClass(ItemEntity.class, new AABB(camp.cooking).inflate(3))) item.discard();
        chest.clearContent();
        chest.setItem(0, new ItemStack(Items.KELP, 6));
        field(goal, "block", camp.storage);
        call(goal, "fetchFood", level);
        field(goal, "block", camp.cooking);
        call(goal, "cook", level);
        var saved = fire.saveWithFullMetadata();
        fire.load(saved);
        check(fire.getPersistentData().getBoolean("ChangedSynergyCampFood"),
                "Real in-progress cooking fallback survives block-entity save/load");
        int duration = PlayerOutpostWorkGoal.campfireRecipe(level, new ItemStack(Items.KELP)).orElseThrow().getCookingTime();
        for (int tick = 0; tick < duration; tick++)
            CampfireBlockEntity.cookTick(level, camp.cooking, level.getBlockState(camp.cooking), fire);
        check(count(chest, Items.KELP) == 2 && drops(level, camp.cooking, Items.DRIED_KELP) == 4,
                "Four real furnace-only foods cook and drop exactly once through native campfire ticks");
        check(!fire.getPersistentData().getBoolean("ChangedSynergyCampFood"),
                "An empty fire clears its cooking fallback marker");
        for (var item : level.getEntitiesOfClass(ItemEntity.class, new AABB(camp.cooking).inflate(3))) item.discard();
        call(goal, "chooseOutput", level);
        check(!mob.getPersistentData().contains("ChangedSynergyPlayerOutpostWorkOutput"),
                "Taking a finished batch clears its collection record without producing replacement items");
        System.out.println("CAMP_FOOD_SMELTING_FALLBACK_PASS");
        } finally { level.getRecipeManager().replaceRecipes(recipes); }
    }

    private static void integratedFarming(ServerLevel level, ChangedEntity mob, Outpost camp,
                                         PlayerOutpostWorkGoal goal, Container chest, BlockPos crop) throws Exception {
        BlockPos first = camp.farmStart, second = camp.farmEnd;
        var member = camp.members.get(mob.getUUID());
        Work work = member.work, backup = member.backupWork;
        var data = PlayerOutpostData.get(level.getServer());
        var oldPosition = mob.position();
        BlockPos approach = crop.north();
        var oldFloor = level.getBlockState(approach.below());
        var oldFeet = level.getBlockState(approach);
        var oldHead = level.getBlockState(approach.above());
        try {
            camp.farmStart = crop; camp.farmEnd = crop;
            chest.clearContent();
            chest.setItem(0, new ItemStack(Items.WHEAT_SEEDS, camp.seedReserve + 4));
            level.setBlock(camp.composter, Blocks.COMPOSTER.defaultBlockState(), 3);
            CropBlock potatoes = (CropBlock)Blocks.POTATOES;
            level.setBlock(crop, potatoes.getStateForAge(0), 3);
            Method choose = PlayerOutpostWorkGoal.class.getDeclaredMethod("chooseWork", ServerLevel.class, Work.class);
            choose.setAccessible(true);
            check((boolean)choose.invoke(goal, level, Work.FARM)
                            && read(goal, "action").toString().equals("COMPOST")
                            && read(goal, "block").equals(camp.composter),
                    "Farming uses the real linked composter when no crop is ready and no fertilizer is available");
            level.setBlock(crop, potatoes.getStateForAge(potatoes.getMaxAge()), 3);
            // Compare two executable jobs, independently of the optional-crop fixture's terrain/path cache.
            level.setBlock(approach.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(approach, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(approach.above(), Blocks.AIR.defaultBlockState(), 3);
            mob.setPos(approach.getX() + 0.5, approach.getY(), approach.getZ() + 0.5);
            check((boolean)choose.invoke(goal, level, Work.FARM)
                            && read(goal, "action").toString().equals("FARM")
                            && read(goal, "block").equals(crop),
                    "A mature crop takes priority over composting even when compostable surplus exists");
            check(Work.parse("compost").orElseThrow() == Work.FARM,
                    "Legacy compost assignment parses into integrated farming");
            data.setWork(camp, mob.getUUID(), Work.COMPOST, Work.COMPOST);
            check(member.work == Work.FARM && member.backupWork == null,
                    "Assignment API normalizes legacy compost work and removes duplicate backup work");
            CompoundTag saved = data.save(new CompoundTag());
            CompoundTag savedMember = null;
            for (var entry : saved.getList("Outposts", 10)) {
                CompoundTag home = (CompoundTag)entry;
                if (!home.getUUID("Owner").equals(camp.owner)) continue;
                for (var resident : home.getList("Members", 10)) {
                    CompoundTag candidate = (CompoundTag)resident;
                    if (candidate.getUUID("Id").equals(mob.getUUID())) savedMember = candidate;
                }
            }
            check(savedMember != null, "Legacy migration fixture contains its real member record");
            savedMember.putString("Work", "COMPOST");
            savedMember.putString("BackupWork", "FARM");
            Method load = PlayerOutpostData.class.getDeclaredMethod("load", CompoundTag.class);
            load.setAccessible(true);
            var loaded = ((PlayerOutpostData)load.invoke(null, saved)).byOwner(camp.owner).orElseThrow()
                    .members.get(mob.getUUID());
            check(loaded.work == Work.FARM && loaded.backupWork == null,
                    "Old saved compost main work migrates to farming and collapses an identical backup");
            savedMember.putString("Work", "HUNT");
            savedMember.putString("BackupWork", "COMPOST");
            loaded = ((PlayerOutpostData)load.invoke(null, saved)).byOwner(camp.owner).orElseThrow()
                    .members.get(mob.getUUID());
            check(loaded.work == Work.HUNT && loaded.backupWork == Work.FARM,
                    "Old saved compost backup work migrates without changing its hunting main work");
            System.out.println("CAMP_INTEGRATED_FARM_COMPOST_REGRESSION_PASS");
        } finally {
            camp.farmStart = first; camp.farmEnd = second;
            data.setWork(camp, mob.getUUID(), work, backup);
            level.setBlock(approach.below(), oldFloor, 3);
            level.setBlock(approach, oldFeet, 3);
            level.setBlock(approach.above(), oldHead, 3);
            mob.setPos(oldPosition.x, oldPosition.y, oldPosition.z);
        }
    }
    private static int count(Container chest, Item item) {
        int count = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) if (chest.getItem(slot).is(item)) count += chest.getItem(slot).getCount();
        return count;
    }
    private static int drops(ServerLevel level, BlockPos pos, Item item) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3),
                entity -> entity.getItem().is(item)).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
    }
    private static int cargo(ChangedEntity mob, Item item) {
        return net.parkabird.changedsynergy.world.inventory.PlayerOutpostCargoInventory.inventory(mob)
                .workItems().stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    private static void field(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static Object read(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void call(Object target, String name, ServerLevel level) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, ServerLevel.class); method.setAccessible(true); method.invoke(target, level);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
