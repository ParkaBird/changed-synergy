package net.parkabird.changedsynergy.ai;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.Emote;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;

/**
 * Holds a social creature near the point in front of a player while one of its
 * radial menus is open.
 */
public final class SocialAudienceGoal extends Goal {
    public static final int PRIORITY = -2;
    private static final long AUDIENCE_TICKS = 300L;
    private static final double STAND_DISTANCE = 1.65D;
    private static final double ARRIVAL_DISTANCE_SQR = 0.85D * 0.85D;
    private static final double WALK_SPEED = 0.55D;
    private static final Map<ChangedEntity, Audience> AUDIENCES =
            new WeakHashMap<>();

    private final ChangedEntity mob;
    private final CompanionFollowNavigation followNavigation;
    private ServerPlayer player;
    private int repathTicks;

    public SocialAudienceGoal(ChangedEntity mob) {
        this.mob = mob;
        this.followNavigation = new CompanionFollowNavigation(mob);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public static void begin(ChangedEntity mob, ServerPlayer player) {
        if (mob.level() == player.level() && mob.isAlive()) {
            Audience previous = validAudience(mob);
            boolean newlyStarted = previous == null
                    || !previous.playerUuid().equals(player.getUUID());
            AUDIENCES.put(
                    mob,
                    new Audience(
                            player.getUUID(),
                            mob.level().getGameTime() + AUDIENCE_TICKS));
            if (newlyStarted) {
                player.displayClientMessage(
                        Component.translatable(
                                "message.changed_synergy.social.audience_hint"),
                        true);
            }
        }
    }

    public static boolean isActive(ChangedEntity mob) {
        Audience audience = validAudience(mob);
        return audience != null;
    }

    /** Releases only this player's active wheel audience, if one exists. */
    public static void end(ChangedEntity mob, ServerPlayer player) {
        Audience audience = AUDIENCES.get(mob);
        if (audience == null
                || !audience.playerUuid().equals(player.getUUID())) {
            return;
        }
        AUDIENCES.remove(mob);
        mob.getNavigation().stop();

        double yaw = Math.toRadians(mob.getYRot());
        mob.getLookControl().setLookAt(
                mob.getX() - Math.sin(yaw) * 4.0D,
                mob.getEyeY(),
                mob.getZ() + Math.cos(yaw) * 4.0D,
                30.0F,
                30.0F);
    }

    @Override
    public boolean canUse() {
        Audience audience = validAudience(mob);
        if (audience == null
                || !(mob.level() instanceof ServerLevel level)
                || mob.getTarget() != null
                || !movementAvailable()) {
            player = null;
            return false;
        }
        player = level.getServer().getPlayerList().getPlayer(audience.playerUuid());
        return player != null
                && player.isAlive()
                && !TakeoverService.active(player)
                && !player.isSpectator()
                && player.level() == mob.level();
    }

    @Override
    public boolean canContinueToUse() {
        return player != null
                && player.isAlive()
                && !TakeoverService.active(player)
                && !player.isSpectator()
                && player.level() == mob.level()
                && validAudience(mob) != null
                && mob.getTarget() == null
                && movementAvailable();
    }

    @Override
    public void start() {
        repathTicks = 0;
        mob.getNavigation().stop();
        followNavigation.reset();
        NpcDialogue.emoteOnly(mob, Emote.CASUAL);
    }

    @Override
    public void tick() {
        if (player == null) {
            return;
        }
        mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        if (horizontal.lengthSqr() < 0.0001D) {
            horizontal = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            horizontal = horizontal.normalize();
        }
        Vec3 standAt = player.position().add(horizontal.scale(STAND_DISTANCE));
        double distanceSqr = mob.distanceToSqr(
                standAt.x, standAt.y, standAt.z);
        if (distanceSqr <= ARRIVAL_DISTANCE_SQR) {
            mob.getNavigation().stop();
            return;
        }
        if (--repathTicks <= 0 || mob.getNavigation().isDone()) {
            repathTicks = 8;
            followNavigation.moveTowardPosition(player, standAt, WALK_SPEED);
        }
    }

    @Override
    public void stop() {
        player = null;
        mob.getNavigation().stop();
        followNavigation.reset();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private boolean movementAvailable() {
        return mob.isAlive()
                && !mob.isNoAi()
                && !mob.isPassenger()
                && !mob.isLeashed()
                && !ChangedAddonCompat.isGrabberBusy(mob)
                && HypnosisQteService.getActiveVictim(mob) == null;
    }

    private static Audience validAudience(ChangedEntity mob) {
        Audience audience = AUDIENCES.get(mob);
        if (audience == null) {
            return null;
        }
        if (!mob.isAlive()
                || mob.isRemoved()
                || audience.untilTick() <= mob.level().getGameTime()) {
            AUDIENCES.remove(mob);
            return null;
        }
        return audience;
    }

    private record Audience(UUID playerUuid, long untilTick) {
    }
}
