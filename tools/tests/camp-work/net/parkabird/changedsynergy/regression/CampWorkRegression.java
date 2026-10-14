package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.PlayerOutpostData;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Work;
import net.parkabird.changedsynergy.ai.PlayerOutpostGoal;
import net.parkabird.changedsynergy.ai.PlayerOutpostMealGoal;
import net.parkabird.changedsynergy.ai.PlayerOutpostService;
import net.parkabird.changedsynergy.ai.PlayerOutpostSupplyType;
import net.parkabird.changedsynergy.ai.PlayerOutpostWorkGoal;
import net.parkabird.changedsynergy.ai.BondedRevivalService;

/** Real world/container checks; included only by the private init script. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class CampWorkRegression {
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        boolean loadRunning = false;
        try {
            // Many runtime-off checks flip settings in one tick; isolate them from Forge's file watcher.
            ChangedSynergyConfig.SPEC.setConfig(com.electronwill.nightconfig.core.CommentedConfig.inMemory());
            if (Boolean.getBoolean("changed_synergy.nativeRoleplayOnly")) { NativeRoleplayChecks.run(event.getServer()); return; }
            if (Boolean.getBoolean("changed_synergy.crewServerLoad")) {
                CampCrewServerLoadChecks.start(event.getServer()); loadRunning = true; return;
            }
            ServerLevel level = event.getServer().overworld();
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(true, event.getServer());
            level.setDayTime(6000);
            ChangedSynergyConfig.COMMON.playerOutpostWorkWhileOwnerOffline.set(true);
            ChangedSynergyConfig.COMMON.playerOutpostMeals.set(true);
            BlockPos marker = new BlockPos(0, 90, 0);
            for (BlockPos pos : BlockPos.betweenClosed(marker.offset(-12, -1, -12), marker.offset(12, -1, 12)))
                level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(marker, Blocks.CAMPFIRE.defaultBlockState(), 3);
            ServerPlayer owner = new ServerPlayer(event.getServer(), level,
                    new GameProfile(UUID.randomUUID(), "CampTest"));
            PlayerOutpostData data = PlayerOutpostData.get(event.getServer());
            for (var previous : List.copyOf(data.all())) if (previous.marker.equals(marker)) data.disband(previous.owner);
            for (var previous : level.getEntitiesOfClass(ChangedEntity.class, new AABB(marker).inflate(16))) previous.discard();
            for (var previous : level.getEntitiesOfClass(ItemEntity.class, new AABB(marker).inflate(16))) previous.discard();
            check(data.claim(level, owner.getUUID(), marker).equals("claimed"), "Claim real campfire");
            var camp = data.byOwner(owner.getUUID()).orElseThrow();
            camp.storage = marker.offset(4, 0, 0);
            level.setBlock(camp.storage, Blocks.CHEST.defaultBlockState(), 3);
            Container chest = PlayerOutpostData.container(level, camp.storage);
            ChangedEntity mob = (ChangedEntity) ForgeRegistries.ENTITY_TYPES.getValue(
                    ResourceLocation.parse("changed:dark_latex_wolf_male")).create(level);
            mob.setPos(4.5, 90, 2.5);
            level.addFreshEntity(mob);
            check(data.add(camp, mob.getUUID(), "Farmer", camp.dimension, mob.blockPosition(),
                    PlayerOutpostSupplyType.ORANGE), "Recruit");
            data.setWork(camp, mob.getUUID(), Work.FARM, Work.HAUL);
            var member = camp.members.get(mob.getUUID());
            data.setWork(camp, mob.getUUID(), Work.GATHER, Work.FARM);
            PlayerOutpostService.applyRole(mob, owner, PlayerOutpostData.Role.SUPPLY);
            check(priority(mob, PlayerOutpostGoal.class) < priority(mob, PlayerOutpostWorkGoal.class),
                    "Primary gather precedes backup farming");
            data.setWork(camp, mob.getUUID(), Work.FARM, Work.HAUL);
            PlayerOutpostService.applyRole(mob, owner, PlayerOutpostData.Role.SUPPLY);
            check(priority(mob, PlayerOutpostWorkGoal.class) < priority(mob, PlayerOutpostGoal.class),
                    "Primary farming precedes backup gathering");
            ChangedSynergyConfig.COMMON.playerOutpostWorkWhileOwnerOffline.set(false);
            check(!PlayerOutpostWorkGoal.canWork(mob, camp), "Offline owner pauses camp production by default");
            ChangedSynergyConfig.COMMON.playerOutpostWorkWhileOwnerOffline.set(true);
            camp.farmStart = marker.offset(3, 0, 3);
            camp.farmEnd = marker.offset(8, 0, 8);
            camp.huntStart = marker.offset(-12, 0, -12);
            camp.huntEnd = marker.offset(-8, 0, -8);
            camp.rawReserve = 2;
            camp.seedReserve = 3;
            camp.meals = false;
            Method load = PlayerOutpostData.class.getDeclaredMethod("load", CompoundTag.class);
            load.setAccessible(true);
            var saved = data.save(new CompoundTag());
            var restored = (PlayerOutpostData) load.invoke(null, saved);
            var home = restored.byOwner(owner.getUUID()).orElseThrow();
            var worker = home.members.get(mob.getUUID());
            check(worker.work == Work.FARM && worker.backupWork == Work.HAUL
                    && home.rawReserve == 2 && home.seedReserve == 3 && !home.meals,
                    "Assignments and policies survive reload");
            check(home.farmContains(camp.farmStart) && !home.farmContains(marker)
                    && home.huntContains(camp.huntStart), "Work zones remain distinct");
            var old = saved.copy();
            var oldHome = old.getList("Outposts", 10).getCompound(0);
            oldHome.remove("Meals"); oldHome.remove("Cooking");
            var oldMember = oldHome.getList("Members", 10).getCompound(0);
            oldMember.remove("Work"); oldMember.remove("BackupWork");
            var migrated = ((PlayerOutpostData)load.invoke(null, old)).byOwner(owner.getUUID()).orElseThrow();
            check(migrated.meals && migrated.cooking.equals(marker)
                    && migrated.members.get(mob.getUUID()).work == Work.GATHER, "Old camp migration");
            for (String command : List.of("outpost create", "outpost status", "outpost help", "outpost link", "outpost link rest", "outpost link storage", "outpost link bed", "outpost link campfire", "outpost farm corner1",
                    "outpost farm corner2", "outpost hunt clear", "outpost work farm",
                    "outpost link composter", "outpost work " + mob.getUUID() + " farm",
                    "outpost work " + mob.getUUID() + " cook", "outpost backup " + mob.getUUID() + " none",
                    "outpost policy seed_reserve 4", "outpost meal")) {
                var parsed = event.getServer().getCommands().getDispatcher().parse("changedsynergy " + command,
                        owner.createCommandSourceStack());
                check(!parsed.getReader().canRead() && parsed.getExceptions().isEmpty(), "Registered command: " + command);
            }
            var campCommands = event.getServer().getCommands().getDispatcher().getRoot()
                    .getChild("changedsynergy").getChild("outpost");
            var helpMessages = new java.util.ArrayList<net.minecraft.network.chat.Component>();
            var feedback = new net.minecraft.commands.CommandSource() {
                public void sendSystemMessage(net.minecraft.network.chat.Component message) { helpMessages.add(message); }
                public boolean acceptsSuccess() { return true; }
                public boolean acceptsFailure() { return true; }
                public boolean shouldInformAdmins() { return false; }
            };
            event.getServer().getCommands().getDispatcher().execute("changedsynergy outpost help",
                    owner.createCommandSourceStack().withSource(feedback));
            check(helpMessages.size() == 24, "Help includes introduction and all 23 command entries");
            for (var message : helpMessages) {
                var contents = (net.minecraft.network.chat.contents.TranslatableContents) message.getContents();
                check(contents.getKey().startsWith("command.changed_synergy.outpost.help."), "Help uses translatable text");
                var click = message.getStyle().getClickEvent();
                if (click == null) {
                    check(contents.getKey().endsWith(".header"), "Only help introduction is non-clickable");
                    continue;
                }
                check(click.getAction() == net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND,
                        "Help inserts commands without executing destructive actions");
                com.mojang.brigadier.tree.CommandNode<net.minecraft.commands.CommandSourceStack> node
                        = event.getServer().getCommands().getDispatcher().getRoot();
                for (String part : click.getValue().substring(1).trim().split(" +")) {
                    node = node.getChild(part);
                    check(node != null, "Help links to registered command: " + click.getValue());
                }
            }
            System.out.println("CAMP_COMMAND_HELP_REGRESSION_PASS");
            for (String alias : List.of("claim", "storage", "bed", "members", "compost"))
                check(campCommands.getChild(alias) == null, "Removed duplicate command: " + alias);
            for (var branch : List.of(campCommands.getChild("work"),
                    campCommands.getChild("work").getChild("member"), campCommands.getChild("backup").getChild("member")))
                check(branch.getChild("compost") == null && branch.getChild("farm") != null,
                        "Farming is the sole compost work command");
            oldMember.putString("Work", "COMPOST");
            oldMember.putString("BackupWork", "HAUL");
            var oldCompost = ((PlayerOutpostData)load.invoke(null, old)).byOwner(owner.getUUID()).orElseThrow();
            check(oldCompost.members.get(mob.getUUID()).work == Work.FARM
                    && oldCompost.members.get(mob.getUUID()).backupWork == Work.HAUL,
                    "Removed compost commands preserve saved job migration");
            oldMember.putString("Work", "HUNT");
            oldMember.putString("BackupWork", "COMPOST");
            oldCompost = ((PlayerOutpostData)load.invoke(null, old)).byOwner(owner.getUUID()).orElseThrow();
            check(oldCompost.members.get(mob.getUUID()).work == Work.HUNT
                    && oldCompost.members.get(mob.getUUID()).backupWork == Work.FARM,
                    "Old backup compost work also migrates to farming");
            PlayerOutpostWorkGoal goal = new PlayerOutpostWorkGoal(mob);
            field(goal, "camp", camp); field(goal, "member", member); field(goal, "startedWork", Work.FARM);
            BlockPos crop = marker.offset(4, 0, 4);
            level.setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState(), 3);
            CropBlock potatoes = (CropBlock)Blocks.POTATOES;
            level.setBlock(crop, potatoes.getStateForAge(potatoes.getMaxAge()), 3);
            field(goal, "block", crop);
            call(goal, "farm", level);
            check(level.getBlockState(crop).is(Blocks.POTATOES)
                    && !potatoes.isMaxAge(level.getBlockState(crop)), "Harvest replants same crop");
            int harvest = potatoesTotal(level, mob, chest);
            check(harvest > 0, "Real surplus harvest retained");
            CompoundTag revival = mob.saveWithoutId(new CompoundTag());
            Method sanitize = BondedRevivalService.class.getDeclaredMethod("sanitizeSnapshot", CompoundTag.class);
            sanitize.setAccessible(true);
            sanitize.invoke(null, revival);
            check(!revival.getCompound("ForgeData").contains("ChangedSynergyPlayerOutpostWorkBag"),
                    "Revival snapshot cannot duplicate camp cargo");
            call(goal, "farm", level);
            check(potatoesTotal(level, mob, chest) == harvest, "Immature crop cannot be harvested twice");
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, event.getServer());
            level.setBlock(crop, potatoes.getStateForAge(potatoes.getMaxAge()), 3);
            call(goal, "farm", level);
            check(potatoes.isMaxAge(level.getBlockState(crop)), "mobGriefing off preserves mature crop");
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, event.getServer());
            PlayerOutpostService.releaseCargo(mob);
            int drops = level.getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(3)).size();
            PlayerOutpostService.releaseCargo(mob);
            check(level.getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(3)).size() == drops
                    && !PlayerOutpostWorkGoal.hasCargo(mob), "Cargo release is idempotent");
            camp.meals = true;
            camp.mealRequestedAt = level.getGameTime();
            PlayerOutpostGoal.setCargo(mob, new ItemStack(Items.POTATO));
            check(!PlayerOutpostMealGoal.wantsMeal(mob), "Delivery takes priority over meal gathering");
            PlayerOutpostGoal.takeCargo(mob);
            check(PlayerOutpostMealGoal.wantsMeal(mob), "Explicit meal request recognized");
            var fire = (CampfireBlockEntity)level.getBlockEntity(marker);
            check(fire.placeFood(mob, new ItemStack(Items.BEEF), 2), "Native campfire accepts real food");
            CampfireBlockEntity.cookTick(level, marker, level.getBlockState(marker), fire);
            CampfireBlockEntity.cookTick(level, marker, level.getBlockState(marker), fire);
            check(level.getEntitiesOfClass(ItemEntity.class, new AABB(marker).inflate(2),
                    item -> item.getItem().is(Items.COOKED_BEEF)).size() == 1, "Native cooking creates one real output");
            checkFarmerDelight(level, goal, crop);
            CampCropChecks.run(level, mob, camp, goal, crop);
            CampProductionChecks.run(level, mob, camp, goal, chest, crop);
            PlayerOutpostService.releaseCargo(mob);
            CampStorageChecks.run(level, mob);
            CampIdleChecks.run(level, mob, camp);
            CampMealChecks.run(level, mob, camp, chest);
            checkBedAlignment(level, mob, camp);
            // Dismounting imposes vanilla's boarding cooldown; run these after seat-placement checks.
            CampMenuOrderChecks.run(owner, mob, camp);
            CampRestChecks.run(owner, mob, camp);
            CampFinalChecks.run(owner, mob, camp, chest);
            CampHaulFocusChecks.run(owner, mob, camp, chest);
            CampHaulAreaChecks.run(owner, mob, camp, chest);
            CampCargoInventoryChecks.run(owner, mob, camp, chest);
            CampNavigationChecks.run(owner, mob, camp);
            CampSocialGatherChecks.run(owner, mob, camp);
            // Navigation's logout checks discarded its technical test player.
            // Rejoining supplies a new live ServerPlayer with the same real identity.
            var crewOwner = new ServerPlayer(event.getServer(), level, owner.getGameProfile());
            CampCrewChecks.run(crewOwner, mob, camp, chest);
            NativeRoleplayChecks.run(event.getServer());
            CampTradeReturnChecks.run(owner, mob, camp);
            PlayerOutpostService.clearAssignment(mob);
            check(mob.goalSelector.getAvailableGoals().stream().anyMatch(wrapped ->
                            wrapped.getGoal() instanceof net.minecraft.world.entity.ai.goal.RandomStrollGoal
                                    && wrapped.getPriority() == 2),
                    "Leaving the camp restores the creature's original random strolling priority");
            data.disband(owner.getUUID());
            System.out.println("CAMP_WORK_REGRESSION_PASS");
        } catch (Throwable failure) {
            System.err.println("CAMP_WORK_REGRESSION_FAIL");
            failure.printStackTrace();
        } finally { if (!loadRunning) event.getServer().halt(false); }
    }

    private static int potatoesTotal(ServerLevel level, ChangedEntity mob, Container chest) throws Exception {
        Method bag = PlayerOutpostWorkGoal.class.getDeclaredMethod("bag", ChangedEntity.class);
        bag.setAccessible(true);
        int count = ((List<ItemStack>)bag.invoke(null, mob)).stream().filter(s -> s.is(Items.POTATO)).mapToInt(ItemStack::getCount).sum();
        for (int slot = 0; slot < chest.getContainerSize(); slot++) if (chest.getItem(slot).is(Items.POTATO)) count += chest.getItem(slot).getCount();
        return count + level.getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(12),
                item -> item.getItem().is(Items.POTATO)).stream().mapToInt(item -> item.getItem().getCount()).sum();
    }

    private static void checkBedAlignment(ServerLevel level, ChangedEntity mob, PlayerOutpostData.Outpost camp) {
        var oldBeds = new java.util.LinkedHashSet<>(camp.beds);
        var oldPosition = mob.position();
        var member = camp.members.get(mob.getUUID());
        var oldRole = member.role;
        int oldTicks = mob.tickCount;
        boolean oldGround = mob.onGround();
        long oldTime = level.getDayTime();
        boolean oldRest = ChangedSynergyConfig.COMMON.playerOutpostRest.get();
        boolean oldMeals = camp.meals;
        var foot = camp.marker.offset(-4, 0, 4);
        var states = new java.util.LinkedHashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        states.put(foot, level.getBlockState(foot));
        for (var facing : net.minecraft.core.Direction.Plane.HORIZONTAL)
            states.put(foot.relative(facing), level.getBlockState(foot.relative(facing)));
        try {
            ChangedSynergyConfig.COMMON.playerOutpostRest.set(true);
            camp.meals = false;
            level.setDayTime(18000); level.updateSkyBrightness();
            member.role = PlayerOutpostData.Role.RESIDENT;
            mob.tickCount = 0;
            for (var facing : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                var head = foot.relative(facing);
                var base = Blocks.WHITE_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, facing);
                level.setBlock(foot, base.setValue(net.minecraft.world.level.block.BedBlock.PART,
                        net.minecraft.world.level.block.state.properties.BedPart.FOOT), 18);
                level.setBlock(head, base.setValue(net.minecraft.world.level.block.BedBlock.PART,
                        net.minecraft.world.level.block.state.properties.BedPart.HEAD), 18);
                for (var linked : List.of(foot, head)) {
                    camp.beds.clear(); camp.beds.add(linked);
                    mob.setPos(foot.getX() + 0.5, foot.getY(), foot.getZ() + 0.5);
                    mob.setOnGround(true);
                    var rest = new net.parkabird.changedsynergy.ai.PlayerOutpostRestGoal(mob);
                    check(rest.canUse(), "Resident accepts either bed half facing " + facing);
                    rest.start(); rest.tick();
                    check(mob.isSleeping() && mob.getSleepingPos().orElseThrow().equals(head)
                            && mob.getBedOrientation() == facing
                            && mob.position().distanceToSqr(new net.minecraft.world.phys.Vec3(
                                    head.getX() + 0.5, head.getY() + 0.6875, head.getZ() + 0.5)) < 0.000001,
                            "Both bed halves use the native head position and orientation: " + facing);
                    rest.stop();
                    check(!mob.isSleeping() && !level.getBlockState(head).getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED),
                            "Wake-up releases the correctly aligned bed");
                }
                level.setBlock(head, Blocks.AIR.defaultBlockState(), 18);
                check(net.parkabird.changedsynergy.ai.PlayerOutpostRestGoal.restingPlace(level, foot) == null,
                        "An incomplete bed cannot become a sleeping target");
                for (var entry : states.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 18);
            }
            System.out.println("CAMP_SLEEP_ALIGNMENT_REGRESSION_PASS");
        } finally {
            if (mob.isSleeping()) mob.stopSleeping();
            for (var entry : states.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 18);
            camp.beds.clear(); camp.beds.addAll(oldBeds); member.role = oldRole; mob.tickCount = oldTicks;
            mob.setPos(oldPosition.x, oldPosition.y, oldPosition.z);
            mob.setOnGround(oldGround);
            level.setDayTime(oldTime); level.updateSkyBrightness();
            ChangedSynergyConfig.COMMON.playerOutpostRest.set(oldRest);
            camp.meals = oldMeals;
        }
    }

    private static void checkFarmerDelight(ServerLevel level, PlayerOutpostWorkGoal goal, BlockPos crop) throws Exception {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("farmersdelight")) return;
        for (String id : List.of("cabbages", "onions")) {
            CropBlock block = (CropBlock)ForgeRegistries.BLOCKS.getValue(ResourceLocation.parse("farmersdelight:" + id));
            check(block != null, "FD crop exists: " + id);
            level.setBlock(crop, block.getStateForAge(block.getMaxAge()), 3);
            var seed = block.getCloneItemStack(level, crop, level.getBlockState(crop));
            PlayerOutpostGoal.insert(PlayerOutpostData.container(level,
                    ((PlayerOutpostData.Outpost)readField(goal, "camp")).storage), seed.copyWithCount(8));
            field(goal, "block", crop);
            call(goal, "farm", level);
            check(level.getBlockState(crop).is(block) && !block.isMaxAge(level.getBlockState(crop)), "FD same-crop replant: " + id);
        }
        System.out.println("CAMP_FARMERS_DELIGHT_REGRESSION_PASS");
    }

    private static void field(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static Object readField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void call(Object target, String method, ServerLevel level) throws Exception {
        Method action = target.getClass().getDeclaredMethod(method, ServerLevel.class);
        action.setAccessible(true); action.invoke(target, level);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static int priority(ChangedEntity mob, Class<?> goal) {
        return mob.goalSelector.getAvailableGoals().stream().filter(wrapped -> goal.isInstance(wrapped.getGoal()))
                .mapToInt(wrapped -> wrapped.getPriority()).findFirst().orElse(99);
    }
}
