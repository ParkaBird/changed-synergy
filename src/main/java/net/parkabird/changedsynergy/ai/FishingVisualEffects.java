package net.parkabird.changedsynergy.ai;

import java.util.UUID;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.parkabird.changedsynergy.entity.CreatureFishingHookVisual;
import net.parkabird.changedsynergy.compat.ChangedVanillaCompat;
import net.parkabird.changedsynergy.init.ChangedSynergyEntities;

/** Player-style float, fishing line, water wake and reel-in presentation. */
public final class FishingVisualEffects {
    private static final String VISUAL_HOOK =
            "ChangedSynergyFishingVisualHook";
    private static final String CAST_ROD_MODEL =
            "ChangedSynergyCreatureCastRod";
    private static final int CAST_FLIGHT_TICKS = 12;

    private FishingVisualEffects() {
    }

    public static void cast(
            ServerLevel level,
            ChangedEntity fisher,
            BlockPos water) {
        cancel(level, fisher);
        CreatureFishingHookVisual hook = createHook(level, fisher);
        if (hook == null) {
            return;
        }
        hook.setPos(castOrigin(fisher));
    }

    public static void tick(
            ServerLevel level,
            ChangedEntity fisher,
            BlockPos water,
            int actionTicks) {
        CreatureFishingHookVisual hook = findHook(level, fisher);
        if (hook == null) {
            hook = createHook(level, fisher);
            if (hook == null) {
                return;
            }
        }
        hook.setFisher(fisher);

        Vec3 target = hookPosition(water);
        if (actionTicks <= CAST_FLIGHT_TICKS) {
            float progress = Math.min(
                    1.0F, actionTicks / (float)CAST_FLIGHT_TICKS);
            Vec3 start = castOrigin(fisher);
            Vec3 position = start.lerp(target, progress)
                    .add(0.0D,
                            Math.sin(progress * Math.PI) * 1.15D,
                            0.0D);
            hook.setPos(position);
            if (actionTicks == CAST_FLIGHT_TICKS) {
                Vec3 surface = waterSurface(water);
                level.sendParticles(ParticleTypes.SPLASH,
                        surface.x, surface.y, surface.z,
                        7, 0.18D, 0.035D, 0.18D, 0.12D);
                level.sendParticles(ParticleTypes.BUBBLE,
                        surface.x, surface.y - 0.08D, surface.z,
                        5, 0.14D, 0.025D, 0.14D, 0.03D);
            }
            return;
        }

        double bob = Math.sin(
                (level.getGameTime() + fisher.getId() * 3L) * 0.18D)
                * 0.018D;
        double bite = actionTicks > 125 && actionTicks % 22 < 4
                ? -0.16D : 0.0D;
        hook.setPos(target.add(0.0D, bob + bite, 0.0D));

        Vec3 surface = waterSurface(water);
        if (actionTicks % 7 == 0) {
            level.sendParticles(ParticleTypes.FISHING,
                    surface.x, surface.y + 0.015D, surface.z,
                    2, 0.12D, 0.0D, 0.12D, 0.01D);
        }
        if (actionTicks > 105 && actionTicks % 11 == 0) {
            drawApproachingWake(level, fisher, surface);
        }
    }

    public static void retrieve(
            ServerLevel level,
            ChangedEntity fisher,
            BlockPos water,
            @Nullable ItemStack caught) {
        Vec3 surface = waterSurface(water);
        level.sendParticles(ParticleTypes.SPLASH,
                surface.x, surface.y, surface.z,
                10, 0.24D, 0.06D, 0.24D, 0.16D);
        if (caught != null && !caught.isEmpty()) {
            level.sendParticles(
                    new ItemParticleOption(ParticleTypes.ITEM, caught),
                    surface.x, surface.y + 0.22D, surface.z,
                    7, 0.16D, 0.2D, 0.16D, 0.055D);
        }
        cancel(level, fisher);
    }

    /** Removes an interrupted cast so no visual float is left behind. */
    public static void cancel(ServerLevel level, ChangedEntity fisher) {
        CreatureFishingHookVisual hook = findHook(level, fisher);
        if (hook != null) {
            hook.discard();
        }
        fisher.getPersistentData().remove(VISUAL_HOOK);
    }

    /** Selects vanilla's cast/no-loose-line model on the temporary held copy. */
    public static void setRodCastModel(
            ChangedEntity fisher,
            boolean cast) {
        if (ChangedVanillaCompat.equipmentChangeRebuildsGoals(fisher)) {
            return;
        }
        ItemStack equipped = fisher.getMainHandItem();
        if (!equipped.is(Items.FISHING_ROD)) {
            return;
        }
        ItemStack updated = equipped.copy();
        if (cast) {
            updated.getOrCreateTag().putBoolean(CAST_ROD_MODEL, true);
        } else if (updated.hasTag()) {
            updated.getTag().remove(CAST_ROD_MODEL);
        }
        fisher.setItemSlot(EquipmentSlot.MAINHAND, updated);
    }

    /** Client item-property hook; the marker exists only on display copies. */
    public static boolean usesCastRodModel(ItemStack stack) {
        return stack.hasTag()
                && stack.getTag().getBoolean(CAST_ROD_MODEL);
    }

    @Nullable
    private static CreatureFishingHookVisual createHook(
            ServerLevel level,
            ChangedEntity fisher) {
        CreatureFishingHookVisual hook =
                ChangedSynergyEntities.CREATURE_FISHING_HOOK.get().create(level);
        if (hook == null) {
            return null;
        }
        hook.setFisher(fisher);
        hook.setPos(castOrigin(fisher));
        if (!level.addFreshEntity(hook)) {
            return null;
        }
        fisher.getPersistentData().putUUID(VISUAL_HOOK, hook.getUUID());
        return hook;
    }

    @Nullable
    private static CreatureFishingHookVisual findHook(
            ServerLevel level,
            ChangedEntity fisher) {
        if (!fisher.getPersistentData().hasUUID(VISUAL_HOOK)) {
            return null;
        }
        UUID hookId = fisher.getPersistentData().getUUID(VISUAL_HOOK);
        Entity entity = level.getEntity(hookId);
        if (entity instanceof CreatureFishingHookVisual hook
                && !hook.isRemoved()) {
            return hook;
        }
        fisher.getPersistentData().remove(VISUAL_HOOK);
        return null;
    }

    private static void drawApproachingWake(
            ServerLevel level,
            ChangedEntity fisher,
            Vec3 floatPos) {
        double angle = fisher.getRandom().nextDouble() * Math.PI * 2.0D;
        double radius = 1.3D + fisher.getRandom().nextDouble() * 1.2D;
        Vec3 start = floatPos.add(
                Math.cos(angle) * radius, 0.005D, Math.sin(angle) * radius);
        for (int index = 0; index < 5; index++) {
            Vec3 point = start.lerp(floatPos, index / 5.0D);
            level.sendParticles(ParticleTypes.FISHING,
                    point.x, point.y, point.z,
                    1, 0.04D, 0.0D, 0.04D, 0.005D);
        }
        level.sendParticles(ParticleTypes.SPLASH,
                floatPos.x, floatPos.y, floatPos.z,
                2, 0.11D, 0.02D, 0.11D, 0.04D);
    }

    private static Vec3 castOrigin(ChangedEntity fisher) {
        return fisher.position()
                .add(0.0D, fisher.getEyeHeight() - 0.35D, 0.0D)
                .add(fisher.getLookAngle().scale(0.45D));
    }

    private static Vec3 hookPosition(BlockPos water) {
        return new Vec3(
                water.getX() + 0.5D,
                water.getY() + 0.9D,
                water.getZ() + 0.5D);
    }

    private static Vec3 waterSurface(BlockPos water) {
        return new Vec3(
                water.getX() + 0.5D,
                water.getY() + 1.01D,
                water.getZ() + 0.5D);
    }
}
