package net.parkabird.changedsynergy.regression;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.PlayerOutpostData;
import net.parkabird.changedsynergy.ai.PlayerOutpostGoal;
import net.parkabird.changedsynergy.ai.PlayerOutpostWorkGoal;
import net.parkabird.changedsynergy.world.inventory.PlayerOutpostCargoInventory;

/** Real drop ownership, item tags, area commands and interrupted delivery. */
final class CampHaulAreaChecks {
    private static int checks;

    static void run(ServerPlayer owner, ChangedEntity mob, PlayerOutpostData.Outpost camp, Container chest) throws Exception {
        var level = owner.serverLevel();
        var data = PlayerOutpostData.get(owner.server);
        var member = camp.members.get(mob.getUUID());
        var oldRole = member.role; var oldWork = member.work; var oldBackup = member.backupWork;
        var position = mob.position(); var playerPosition = owner.position();
        float yaw = owner.getYRot(), pitch = owner.getXRot();
        boolean ground = mob.onGround(), meals = camp.meals;
        var memory = mob.getPersistentData().copy();
        var connection = owner.connection;
        var oldBlocked = ChangedSynergyConfig.COMMON.playerOutpostHaulingBlockedItems.get();
        var corners = List.of("farmStart", "farmEnd", "huntStart", "huntEnd", "gatherStart", "gatherEnd", "haulStart", "haulEnd");
        Map<String, Object> areas = new LinkedHashMap<>();
        for (String key : corners) areas.put(key, read(camp, key));
        List<ItemStack> stock = new ArrayList<>();
        for (int i = 0; i < chest.getContainerSize(); i++) stock.add(chest.getItem(i).copy());
        var hidden = level.getEntitiesOfClass(ItemEntity.class, new AABB(camp.marker).inflate(64));
        Map<ItemEntity, Vec3> positions = new java.util.IdentityHashMap<>();
        hidden.forEach(item -> positions.put(item, item.position()));
        List<ItemEntity> drops = new ArrayList<>();
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        var goal = new PlayerOutpostWorkGoal(mob);
        var feedback = new CommandSource() {
            public void sendSystemMessage(Component text) { }
            public boolean acceptsSuccess() { return false; }
            public boolean acceptsFailure() { return false; }
            public boolean shouldInformAdmins() { return false; }
        };
        var source = owner.createCommandSourceStack().withSource(feedback).withPermission(4);
        try {
            owner.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(owner.server,
                    new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), owner);
            hidden.forEach(item -> item.setPos(camp.marker.getX() + 100, camp.marker.getY(), camp.marker.getZ()));
            chest.clearContent(); camp.meals = false;
            PlayerOutpostCargoInventory.inventory(mob).setWorkItems(List.of());
            PlayerOutpostGoal.setCargo(mob, ItemStack.EMPTY);
            ChangedSynergyConfig.COMMON.playerOutpostHaulingBlockedItems.set(List.of());
            data.setWork(camp, mob.getUUID(), PlayerOutpostData.Work.HAUL, null);
            field(goal, "camp", camp); field(goal, "member", member);
            camp.haulStart = camp.haulEnd = null;
            mob.setPos(Vec3.atBottomCenterOf(camp.marker.offset(6, 0, 3))); mob.setOnGround(true);
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.setHoverName(Component.literal("Saved weapon")); sword.setDamageValue(27);
            sword.getOrCreateTag().putString("CustomPayload", "keep me");
            var thrown = drop(level, mob.blockPosition(), sword, drops);
            thrown.setThrower(owner.getUUID()); thrown.setTarget(owner.getUUID());
            check((boolean)call(goal, "chooseHaul", level) && read(goal, "pickup") == thrown,
                    "Hauling accepts a player's tagged and owned equipment, not only supplies");
            call(goal, "collectPickup", level);
            check(!thrown.isAlive() && ItemStack.matches(PlayerOutpostCargoInventory.inventory(mob).workItems().get(0), sword),
                    "Collected equipment keeps its name, damage and custom data");
            goal.stop(); field(goal, "block", camp.storage);
            mob.setPos(Vec3.atBottomCenterOf(camp.storage.east())); call(goal, "deposit", level);
            check(!PlayerOutpostWorkGoal.hasCargo(mob) && ItemStack.matches(chest.getItem(0), sword),
                    "Actual tagged equipment is delivered once");

            var blocked = drop(level, camp.marker.offset(6, 0, 3), new ItemStack(Items.DIAMOND, 7), drops);
            ChangedSynergyConfig.COMMON.playerOutpostHaulingBlockedItems.set(List.of("minecraft:diamond"));
            check(!(boolean)call(goal, "haulableDrop", blocked), "Blacklist excludes a specific item ID");
            ChangedSynergyConfig.COMMON.playerOutpostHaulingBlockedItems.set(List.of());
            var delayed = drop(level, camp.marker.offset(6, 0, 3), new ItemStack(Items.GOLD_INGOT), drops);
            delayed.setPickUpDelay(200);
            check(!(boolean)call(goal, "haulableDrop", delayed), "Native pickup delay is respected");
            delayed.discard(); mob.setPos(Vec3.atBottomCenterOf(blocked.blockPosition().east()));
            field(goal, "scanInitialized", true); field(goal, "nextScan", 0L);
            check(goal.canUse(), "Unblocked drop can be selected again");
            goal.start(); ChangedSynergyConfig.COMMON.playerOutpostHaulingBlockedItems.set(List.of("minecraft:diamond"));
            check(!goal.canContinueToUse(), "Changing the blacklist interrupts a selected pickup");
            call(goal, "collectPickup", level);
            check(blocked.isAlive() && blocked.getItem().getCount() == 7 && !PlayerOutpostWorkGoal.hasCargo(mob),
                    "A stale collection callback cannot consume a newly blacklisted drop");
            goal.stop(); blocked.discard(); ChangedSynergyConfig.COMMON.playerOutpostHaulingBlockedItems.set(List.of());

            var outside = drop(level, camp.marker.offset(40, 0, 0), new ItemStack(Items.IRON_INGOT), drops);
            check(!camp.haulContains(outside.blockPosition()) && !(boolean)call(goal, "haulableDrop", outside),
                    "Unmarked hauling stays within the default 32-block radius");
            for (BlockPos p : BlockPos.betweenClosed(camp.marker.offset(0, -1, -2), camp.marker.offset(64, -1, 5))) {
                states.put(p.immutable(), level.getBlockState(p)); level.setBlock(p, Blocks.STONE.defaultBlockState(), 18);
            }
            for (String area : List.of("farm", "hunt", "gather", "haul")) {
                owner.server.getCommands().getDispatcher().execute("changedsynergy outpost " + area + " clear", source);
                for (int corner = 1; corner <= 2; corner++) {
                    BlockPos p = camp.marker.offset(corner == 1 ? 40 : 60, -1, 0);
                    owner.setPos(Vec3.atBottomCenterOf(p).add(0, 1, 2));
                    owner.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(p));
                    int result = owner.server.getCommands().getDispatcher().execute(
                            "changedsynergy outpost " + area + " corner" + corner,
                            owner.createCommandSourceStack().withSource(feedback).withPermission(4));
                    check(result == 1, area + " corner " + corner + " can be assigned beyond 32 and within 64 blocks");
                }
            }
            check(camp.haulContains(outside.blockPosition()) && camp.farmContains(outside.blockPosition())
                            && camp.huntContains(outside.blockPosition()) && camp.gatherContains(outside.blockPosition()),
                    "All marked work areas use the extended radius");
            check(!camp.contains(outside.blockPosition()) && !camp.workContains(camp.marker.offset(65, 0, 0)),
                    "Linked facilities keep their camp range and work does not exceed 64");
            var near = drop(level, camp.marker.offset(6, 0, 3), new ItemStack(Items.EMERALD), drops);
            mob.setPos(Vec3.atBottomCenterOf(camp.marker.offset(2, 0, 3)));
            field(goal, "nextScan", 0L);
            check(goal.canUse() && read(goal, "pickup") == outside,
                    "A remote hauling area can be selected beyond the creature's native pathfinding range");
            goal.start(); goal.tick();
            var outbound = mob.getNavigation().getPath();
            check(outbound != null && outbound.getEndNode() != null
                            && outbound.getEndNode().asBlockPos().distSqr(outside.blockPosition())
                            < mob.blockPosition().distSqr(outside.blockPosition()),
                    "Remote pickup starts a loaded, forward-progressing native route");
            goal.stop();
            mob.setPos(Vec3.atBottomCenterOf(outside.blockPosition().east()));
            check((boolean)call(goal, "chooseHaul", level) && read(goal, "pickup") == outside,
                    "A closer item outside the marked hauling area does not hide an eligible drop");
            goal.start(); call(goal, "collectPickup", level); goal.stop();
            check(near.isAlive() && !outside.isAlive() && PlayerOutpostWorkGoal.hasCargo(mob),
                    "Transport collects the remote item without crossing its pickup boundary");
            check(PlayerOutpostGoal.workArea(camp, member).contains(Vec3.atCenterOf(camp.haulStart)),
                    "Idle haulers wait near their own work area");
            var load = PlayerOutpostData.class.getDeclaredMethod("load", CompoundTag.class); load.setAccessible(true);
            var restored = ((PlayerOutpostData)load.invoke(null, data.save(new CompoundTag()))).byOwner(camp.owner).orElseThrow();
            check(restored.haulStart.equals(camp.haulStart) && restored.haulEnd.equals(camp.haulEnd),
                    "Hauling boundaries persist across a server reload");
            owner.server.getCommands().getDispatcher().execute("changedsynergy outpost haul clear", source);
            check(camp.haulStart == null && camp.haulEnd == null && camp.haulContains(near.blockPosition())
                            && !camp.haulContains(camp.marker.offset(40, 0, 0)), "Clearing hauling restores its 32-block default");
            near.discard();
            field(goal, "scanInitialized", true); field(goal, "nextScan", 0L);
            boolean returns = goal.canUse();
            check(returns && String.valueOf(read(goal, "action")).equals("DEPOSIT"),
                    "Changing or clearing an area returns existing cargo before starting another batch: "
                            + member.workStatus + ", action=" + read(goal, "action") + ", follows="
                            + mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE));
            goal.start(); goal.tick();
            var inbound = mob.getNavigation().getPath();
            check(inbound != null && inbound.getEndNode() != null
                            && inbound.getEndNode().asBlockPos().distSqr(camp.storage)
                            < mob.blockPosition().distSqr(camp.storage),
                    "Distant cargo delivery starts a native return segment at the ordinary walking speed");
            goal.stop(); field(goal, "block", camp.storage); mob.setPos(Vec3.atBottomCenterOf(camp.storage.east()));
            call(goal, "deposit", level);
            check(!PlayerOutpostWorkGoal.hasCargo(mob), "Remote cargo can be recovered through the normal storage chain");
            var spec = ChangedSynergyConfig.SPEC.getSpec().<net.minecraftforge.common.ForgeConfigSpec.ValueSpec>
                    get(ChangedSynergyConfig.COMMON.playerOutpostHaulingBlockedItems.getPath());
            check(spec.test(List.of("minecraft:diamond", "example:optional_item")) && spec.test(List.of())
                            && !spec.test(List.of("bad id")) && !spec.test(List.of("diamond")),
                    "Blacklist accepts namespaced optional IDs and an empty list, but rejects malformed entries");
            System.out.println("CAMP_HAUL_AREA_REGRESSION_PASS: " + checks + " assertions");
        } finally {
            goal.stop(); ChangedSynergyConfig.COMMON.playerOutpostHaulingBlockedItems.set(oldBlocked);
            drops.forEach(ItemEntity::discard); hidden.forEach(item -> item.setPos(positions.get(item)));
            for (var entry : states.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 18);
            for (var entry : areas.entrySet()) field(camp, entry.getKey(), entry.getValue());
            chest.clearContent(); for (int i = 0; i < stock.size(); i++) chest.setItem(i, stock.get(i));
            member.role = oldRole; member.work = oldWork; member.backupWork = oldBackup; camp.meals = meals;
            mob.getPersistentData().getAllKeys().stream().toList().forEach(mob.getPersistentData()::remove);
            mob.getPersistentData().merge(memory); mob.setPos(position); mob.setOnGround(ground);
            owner.setPos(playerPosition); owner.setYRot(yaw); owner.setXRot(pitch);
            owner.connection = connection;
        }
    }
    private static ItemEntity drop(net.minecraft.server.level.ServerLevel level, BlockPos pos, ItemStack stack, List<ItemEntity> drops) {
        var item = new ItemEntity(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, stack.copy());
        item.tickCount = 120; item.setNoPickUpDelay(); level.addFreshEntity(item); drops.add(item); return item;
    }
    private static Object read(Object object, String key) throws Exception {
        Field field = object.getClass().getDeclaredField(key); field.setAccessible(true); return field.get(object);
    }
    private static void field(Object object, String key, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(key); field.setAccessible(true); field.set(object, value);
    }
    private static Object call(Object object, String key, Object argument) throws Exception {
        for (var method : object.getClass().getDeclaredMethods()) if (method.getName().equals(key)
                && method.getParameterCount() == 1 && method.getParameterTypes()[0].isInstance(argument)) {
            method.setAccessible(true); return method.invoke(object, argument);
        }
        throw new NoSuchMethodException(key);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message); checks++; System.out.println("CAMP_HAUL_AREA_PASS: " + message);
    }
}
