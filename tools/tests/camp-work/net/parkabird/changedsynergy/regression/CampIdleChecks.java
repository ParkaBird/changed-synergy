package net.parkabird.changedsynergy.regression;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.parkabird.changedsynergy.ai.PlayerOutpostData;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Role;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Work;
import net.parkabird.changedsynergy.ai.PlayerOutpostGoal;
import net.parkabird.changedsynergy.ai.PlayerOutpostWorkGoal;

/** Real navigation checks, compiled only into the isolated server fixture. */
final class CampIdleChecks {
    static void run(ServerLevel level, ChangedEntity mob, PlayerOutpostData.Outpost camp) throws Exception {
        var member = camp.members.get(mob.getUUID());
        Role role = member.role;
        Work work = member.work;
        Work backup = member.backupWork;
        boolean meals = camp.meals;
        BlockPos farmStart = camp.farmStart, farmEnd = camp.farmEnd;
        BlockPos huntStart = camp.huntStart, huntEnd = camp.huntEnd;
        BlockPos composter = camp.composter;
        BlockPos hold = member.holdPosition;
        Vec3 position = mob.position();
        boolean grounded = mob.onGround();
        int tickCount = mob.tickCount;
        var target = mob.getTarget();
        try {
            member.role = Role.SUPPLY;
            member.work = Work.FARM;
            member.backupWork = Work.HAUL;
            camp.meals = false;
            camp.farmStart = camp.marker.offset(7, 0, 7);
            camp.farmEnd = camp.marker.offset(9, 0, 9);
            mob.setPos(camp.marker.getX() + 0.5, camp.marker.getY(), camp.marker.getZ() - 9.5);
            check(level.getBlockState(mob.blockPosition().below()).isFaceSturdy(
                    level, mob.blockPosition().below(), Direction.UP), "Idle fixture starts on actual ground");
            // ServerStarted runs before the entity's first physics tick; ground navigation needs this bit.
            mob.setOnGround(true);
            mob.setTarget(null);
            var idle = new PlayerOutpostGoal.Idle(mob);
            boolean ready = idle.canUse();
            if (!ready) {
                Method available = idle.getClass().getDeclaredMethod("available", ServerLevel.class);
                available.setAccessible(true);
                System.out.println("CAMP_IDLE_DIAGNOSTIC: available=" + available.invoke(idle, level)
                        + ", ground=" + mob.onGround() + ", position=" + mob.position()
                        + ", passenger=" + mob.isPassenger() + ", noAi=" + mob.isNoAi()
                        + ", area=" + call(idle, "area") + ", navigation=" + mob.getNavigation().getClass().getName());
            }
            check(ready, "Farmer can return to its marked work area while idle");
            AABB area = (AABB)call(idle, "area");
            BlockPos stand = (BlockPos)read(idle, "destination");
            check(area.contains(Vec3.atCenterOf(stand)) && camp.contains(stand),
                    "Farmer's idle destination stays beside the assigned field");
            check(level.getBlockState(stand.below()).isFaceSturdy(level, stand.below(), Direction.UP),
                    "Idle destination has actual supporting ground");
            check(mob.goalSelector.getAvailableGoals().stream().anyMatch(g -> g.getGoal() instanceof RandomStrollGoal)
                    && mob.goalSelector.getAvailableGoals().stream().noneMatch(g ->
                            g.getGoal() instanceof RandomStrollGoal && g.getPriority() <= 4),
                    "Native random stroll cannot preempt assigned work, beds or idle anchoring");
            idle.start();
            idle.tick();
            check(idle.canContinueToUse(), "Idle anchoring remains interruptible while waiting for work");
            var attacker = EntityType.ZOMBIE.create(level);
            mob.setLastHurtByMob(attacker);
            mob.setTarget(attacker);
            check(!idle.canContinueToUse(), "Idle anchoring yields immediately to a combat target");
            mob.setTarget(null);
            mob.setLastHurtByMob(null);
            for (Role order : new Role[] { Role.CREW, Role.RETURN, Role.RETREAT, Role.HOLD }) {
                member.role = order;
                check(!idle.canContinueToUse(), "Idle anchoring yields to owner order: " + order);
            }
            idle.stop();
            // Owner movement orders must not wait for the production scan or a tick modulo.
            mob.tickCount = mob.getId() + 1;
            member.holdPosition = camp.marker.offset(0, 0, 3);
            for (Role order : new Role[] { Role.RETURN, Role.RETREAT, Role.HOLD }) {
                member.role = order;
                var ordered = new PlayerOutpostGoal(mob);
                check(ordered.canUse(), "Immediate movement-order response: " + order);
                ordered.start();
                ordered.stop();
            }
            var gathering = new PlayerOutpostGoal(mob);
            field(gathering, "outpost", camp);
            field(gathering, "member", member);
            field(gathering, "startedRole", Role.SUPPLY);
            field(gathering, "destination", camp.storage);
            field(gathering, "remainingTicks", 400);
            member.role = Role.RETURN;
            gathering.tick();
            check((int)read(gathering, "remainingTicks") == 0,
                    "An old gather task ends before executing work after a new owner order");
            var production = new PlayerOutpostWorkGoal(mob);
            field(production, "camp", camp);
            field(production, "member", member);
            check(!production.canContinueToUse(), "Owner orders interrupt custom camp production too");
            member.role = Role.SUPPLY;
            member.work = Work.HUNT;
            camp.huntStart = camp.marker.offset(-10, 0, -10);
            camp.huntEnd = camp.marker.offset(-7, 0, -7);
            idle = new PlayerOutpostGoal.Idle(mob);
            check(idle.canUse(), "Hunter finds a reachable waiting spot");
            check(((AABB)call(idle, "area")).contains(Vec3.atCenterOf((BlockPos)read(idle, "destination"))),
                    "Hunter waits near the marked hunting ground");
            member.work = Work.FARM;
            camp.composter = camp.marker.offset(7, 0, -5);
            idle = new PlayerOutpostGoal.Idle(mob);
            check(idle.canUse(), "Farmer with a linked composter finds a safe waiting spot");
            area = (AABB)call(idle, "area");
            check(area.contains(Vec3.atCenterOf(camp.farmStart))
                    && area.contains(Vec3.atCenterOf((BlockPos)read(idle, "destination")))
                    && !area.contains(Vec3.atCenterOf(camp.composter)),
                    "Integrated composting does not pull an idle farmer away from its marked field");
            // An unsafe or floating marked area must not become a permanent standing target.
            member.work = Work.FARM;
            camp.farmStart = camp.marker.offset(19, 5, 0);
            camp.farmEnd = camp.marker.offset(21, 5, 2);
            idle = new PlayerOutpostGoal.Idle(mob);
            check(idle.canUse(), "Missing work-area ground falls back to safe camp ground");
            stand = (BlockPos)read(idle, "destination");
            check(!((AABB)call(idle, "area")).contains(Vec3.atCenterOf(stand))
                    && level.getBlockState(stand.below()).isFaceSturdy(level, stand.below(), Direction.UP),
                    "Unreachable work area uses actual safe fallback ground");
            System.out.println("CAMP_IDLE_ORDERS_REGRESSION_PASS");
        } finally {
            mob.getNavigation().stop();
            mob.setPos(position.x, position.y, position.z);
            mob.setOnGround(grounded);
            mob.tickCount = tickCount;
            mob.setTarget(target);
            member.role = role; member.work = work; member.backupWork = backup; member.holdPosition = hold;
            camp.meals = meals;
            camp.farmStart = farmStart; camp.farmEnd = farmEnd;
            camp.huntStart = huntStart; camp.huntEnd = huntEnd; camp.composter = composter;
        }
    }

    private static Object read(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
    private static void field(Object owner, String name, Object value) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }
    private static Object call(Object owner, String name) throws Exception {
        Method method = owner.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(owner);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
