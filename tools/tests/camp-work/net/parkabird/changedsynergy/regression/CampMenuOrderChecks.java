package net.parkabird.changedsynergy.regression;

import io.netty.buffer.Unpooled;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.SeatEntity;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Inventory;
import net.parkabird.changedsynergy.ai.PlayerOutpostData;
import net.parkabird.changedsynergy.ai.PlayerOutpostGoal;
import net.parkabird.changedsynergy.ai.SharedRestGoal;
import net.parkabird.changedsynergy.world.inventory.PlayerOutpostMenu;

/** Checks the actual vanilla packet and server menu, rather than direct role setters. */
final class CampMenuOrderChecks {
    static void run(ServerPlayer owner, ChangedEntity mob, PlayerOutpostData.Outpost camp) throws Exception {
        var level = owner.serverLevel();
        var member = camp.members.get(mob.getUUID());
        var role = member.role;
        var hold = member.holdPosition;
        var position = mob.position();
        var data = mob.getPersistentData().copy();
        var connection = owner.connection;
        boolean ground = mob.onGround(), meals = camp.meals;
        long requested = camp.mealRequestedAt;
        try {
            owner.connection = new ServerGamePacketListenerImpl(owner.server,
                    new Connection(PacketFlow.SERVERBOUND), owner);
            var constructor = PlayerOutpostMenu.class.getDeclaredConstructor(int.class,
                    Inventory.class, PlayerOutpostData.Outpost.class, int.class);
            constructor.setAccessible(true);
            var menu = constructor.newInstance(1, owner.getInventory(), camp, 0);
            check(roundTrip(131) == -125, "Old meal-call ID is corrupted by the vanilla signed-byte packet");
            int[] buttons = {PlayerOutpostMenu.MEALS,
                    PlayerOutpostMenu.REFRESH, PlayerOutpostMenu.MEAL_CALL};
            for (int button : buttons) check(roundTrip(button) == button, "Camp utility button survives serialization: " + button);
            check(!menu.clickMenuButton(owner, roundTrip(120)), "Retired fish-policy button is rejected");
            check(menu.clickMenuButton(owner, roundTrip(PlayerOutpostMenu.MEALS))
                    && camp.meals != meals, "Meals button changes server policy");
            camp.meals = true;
            camp.mealRequestedAt = -1;
            owner.setPos(camp.marker.getX(), camp.marker.getY(), camp.marker.getZ());
            check(menu.clickMenuButton(owner, roundTrip(PlayerOutpostMenu.MEAL_CALL))
                    && camp.mealRequestedAt == level.getGameTime(), "Meal-call button reaches the actual server command");
            camp.meals = false;
            for (var order : new PlayerOutpostData.Role[] {PlayerOutpostData.Role.RETURN, PlayerOutpostData.Role.RETREAT}) {
                mob.setPos(camp.marker.getX() + 0.5, camp.marker.getY(), camp.marker.getZ() - 9.5);
                mob.setOnGround(true);
                var floor = mob.blockPosition().below();
                var seat = SeatEntity.createFor(level, level.getBlockState(floor), floor, false, false, true);
                check(seat != null && mob.startRiding(seat, true), "Order fixture starts in an actual ambient seat");
                int button = PlayerOutpostMenu.ACTION_BASE + order.ordinal();
                check(menu.clickMenuButton(owner, roundTrip(button)) && member.role == order,
                        "Member movement order reaches SavedData: " + order);
                check(!mob.isPassenger() && seat.isRemoved(), "Movement order releases ambient seating: " + order);
                mob.setOnGround(true);
                var goal = mob.goalSelector.getAvailableGoals().stream()
                        .filter(w -> w.getGoal() instanceof PlayerOutpostGoal).findFirst().orElseThrow();
                check(goal.getPriority() == 0, "Movement order takes priority over ordinary work");
                var movement = goal.getGoal();
                check(movement.canUse(), "Released member can execute movement order: " + order);
                movement.start(); movement.tick();
                check(mob.getNavigation().getPath() != null && !mob.getNavigation().isDone(),
                        "Movement order creates a real route toward camp: " + order);
                movement.stop();
                SharedRestGoal.begin(mob, owner);
                check(mob.getPersistentData().hasUUID("ChangedSynergyRestOwner"), "Shared-rest fixture is active");
                check(menu.clickMenuButton(owner, roundTrip(button))
                        && !mob.getPersistentData().hasUUID("ChangedSynergyRestOwner"),
                        "Movement order cancels the competing shared-rest task: " + order);
            }
            System.out.println("CAMP_MENU_PACKET_ORDERS_REGRESSION_PASS");
        } finally {
            SharedRestGoal.cancel(mob);
            mob.getNavigation().stop();
            mob.setPos(position.x, position.y, position.z);
            mob.setOnGround(ground);
            mob.getPersistentData().getAllKeys().stream().toList().forEach(mob.getPersistentData()::remove);
            mob.getPersistentData().merge(data);
            owner.connection = connection;
            member.role = role; member.holdPosition = hold;
            camp.meals = meals; camp.mealRequestedAt = requested;
            net.parkabird.changedsynergy.ai.PlayerOutpostService.ensureGoal(mob);
        }
    }

    private static int roundTrip(int id) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new ServerboundContainerButtonClickPacket(1, id).write(buffer);
            return new ServerboundContainerButtonClickPacket(buffer).getButtonId();
        } finally { buffer.release(); }
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
