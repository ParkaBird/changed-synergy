package net.parkabird.changedsynergy.regression;

import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.fml.ModList;
import net.parkabird.changedsynergy.ai.PlayerOutpostData;
import net.parkabird.changedsynergy.ai.PlayerOutpostGoal;
import net.parkabird.changedsynergy.ai.PlayerOutpostService;
import net.parkabird.changedsynergy.ai.PlayerOutpostWorkGoal;
import net.parkabird.changedsynergy.command.PlayerOutpostCommand;
import net.parkabird.changedsynergy.world.inventory.PlayerOutpostMenu;

/** Final camp checks against server commands, native menus, real drops and navigation. */
final class CampFinalChecks {
    static void run(ServerPlayer owner, ChangedEntity mob, PlayerOutpostData.Outpost camp, Container chest) throws Exception {
        var level = owner.serverLevel();
        var data = PlayerOutpostData.get(owner.server);
        var member = camp.members.get(mob.getUUID());
        var role = member.role; var work = member.work; var backup = member.backupWork;
        var ownerPosition = owner.position(); var mobPosition = mob.position();
        float yaw = owner.getYRot(), pitch = owner.getXRot();
        var connection = owner.connection;
        var target = camp.marker.offset(-4, 0, 1);
        var oldState = level.getBlockState(target);
        var oldStorage = camp.storage;
        ItemEntity bones = null, fresh = null, thrown = null;
        try {
            owner.connection = new ServerGamePacketListenerImpl(owner.server, new Connection(PacketFlow.SERVERBOUND), owner);
            level.setBlock(target, Blocks.CHEST.defaultBlockState(), 3);
            lookAt(owner, target);
            check(PlayerOutpostCommand.wheelAction(owner).equals("link_outpost"), "Targeted unbound storage becomes Link Facility");
            owner.setYRot(180); owner.setXRot(0);
            check(!PlayerOutpostCommand.wheelAction(owner, "link_outpost", target) && camp.storage.equals(oldStorage),
                    "A stale wheel target cannot link another block");
            lookAt(owner, target);
            check(PlayerOutpostCommand.wheelAction(owner, "link_outpost", target) && camp.storage.equals(target),
                    "Wheel shortcut runs the real server link command");
            check(PlayerOutpostCommand.wheelAction(owner).equals("outpost"), "Already-linked facility returns to management");
            camp.storage = oldStorage;

            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                var write = PlayerOutpostMenu.class.getDeclaredMethod("writeOpeningData", FriendlyByteBuf.class,
                        ServerPlayer.class, PlayerOutpostData.Outpost.class, int.class);
                write.setAccessible(true); write.invoke(null, buffer, owner, camp, 0);
                var client = new PlayerOutpostMenu(1, owner.getInventory(), buffer);
                check(buffer.readableBytes() == 0 && client.members().size() == camp.members.size()
                                && client.meals() == camp.meals && client.storage().equals(camp.storage.toShortString()),
                        "New camp menu opening payload round-trips exactly with removed fish policy");
            } finally { buffer.release(); }

            camp.meals = false;
            data.setWork(camp, mob.getUUID(), PlayerOutpostData.Work.HAUL, null);
            PlayerOutpostService.applyRole(mob, owner, PlayerOutpostData.Role.SUPPLY);
            bones = new ItemEntity(level, 5.5, 90, 3.5, new ItemStack(Items.BONE, 3)); bones.tickCount = 120;
            fresh = new ItemEntity(level, 5.5, 90, 3.5, new ItemStack(Items.IRON_INGOT));
            thrown = new ItemEntity(level, 5.5, 90, 3.5, new ItemStack(Items.GOLD_INGOT));
            thrown.tickCount = 120; thrown.setThrower(owner.getUUID());
            level.addFreshEntity(bones); level.addFreshEntity(fresh); level.addFreshEntity(thrown);
            mob.setPos(6.5, 90, 3.5); mob.setOnGround(true);
            var haul = new PlayerOutpostWorkGoal(mob);
            field(haul, "camp", camp); field(haul, "member", member);
            check((boolean)call(haul, "chooseHaul", level) && read(haul, "pickup") == bones,
                    "Hauling accepts guard loot but leaves new drops to their producer briefly: " + member.workStatus);
            check((boolean)call(haul, "haulableDrop", thrown), "Player-thrown drops are also eligible for hauling");
            call(haul, "collectPickup", level);
            check(PlayerOutpostWorkGoal.hasCargo(mob) && !bones.isAlive(), "Hauler collects the real loot once");
            field(haul, "block", camp.storage);
            mob.setPos(camp.storage.getX() + 1.5, camp.storage.getY(), camp.storage.getZ() + 0.5);
            int before = count(chest, Items.BONE);
            call(haul, "deposit", level);
            check(count(chest, Items.BONE) == before + 3 && !PlayerOutpostWorkGoal.hasCargo(mob),
                    "Hauler delivers the collected loot before another job");
            haul.stop();

            mob.setPos(camp.marker.getX() + 0.5, camp.marker.getY() + 0.44, camp.marker.getZ() + 0.5);
            mob.setOnGround(true);
            var escape = new PlayerOutpostGoal.FireEscape(mob);
            check(PlayerOutpostGoal.onCampfire(mob) && escape.canUse(), "A recruited member detects contact with the hot campfire");
            var destination = (net.minecraft.core.BlockPos)read(escape, "destination");
            escape.start(); escape.tick();
            check(mob.getNavigation().getPath() != null && !destination.equals(camp.marker),
                    "Fire escape creates an ordinary navigation path away from the fire");
            escape.stop();
            mob.setPos(mobPosition);

            for (String name : new String[]{"player_camp", "camp_delivery", "camp_meal"})
                check(owner.server.getAdvancements().getAdvancement(ResourceLocation.parse("changed_synergy:" + name)) != null,
                        "Camp advancement loads: " + name);
            for (String name : new String[]{"native_origin", "native_bond"})
                check((owner.server.getAdvancements().getAdvancement(ResourceLocation.parse("changed_synergy:" + name)) != null)
                                == ModList.get().isLoaded("true_transfur"),
                        "TrueTransfur advancement presence matches the optional mod: " + name);
            System.out.println("CAMP_WHEEL_HAUL_FIRE_ADVANCEMENTS_PASS");
        } finally {
            if (bones != null) bones.discard(); if (fresh != null) fresh.discard(); if (thrown != null) thrown.discard();
            PlayerOutpostService.releaseCargo(mob);
            level.setBlock(target, oldState, 3); camp.storage = oldStorage;
            member.role = role; member.work = work; member.backupWork = backup;
            owner.connection = connection; owner.setPos(ownerPosition); owner.setYRot(yaw); owner.setXRot(pitch);
            mob.setPos(mobPosition); PlayerOutpostService.ensureGoal(mob);
        }
    }

    private static void lookAt(ServerPlayer player, net.minecraft.core.BlockPos pos) {
        player.setPos(pos.getX() + 0.5, pos.getY() + 2, pos.getZ() + 0.5);
        player.setYRot(0); player.setXRot(90);
    }
    private static int count(Container container, net.minecraft.world.item.Item item) {
        int sum = 0; for (int slot = 0; slot < container.getContainerSize(); slot++)
            if (container.getItem(slot).is(item)) sum += container.getItem(slot).getCount(); return sum;
    }
    private static void field(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static Object read(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static Object call(Object target, String name, Object argument) throws Exception {
        var method = target.getClass().getDeclaredMethod(name, argument.getClass()); method.setAccessible(true); return method.invoke(target, argument);
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
