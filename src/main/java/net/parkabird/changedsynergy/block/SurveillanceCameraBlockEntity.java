package net.parkabird.changedsynergy.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.parkabird.changedsynergy.init.ChangedSynergyBlockEntities;

/** Tracks nearby creatures via damped dual-axis gimbal angles and cycles CRISSCROSS / idle LED states. */
public final class SurveillanceCameraBlockEntity extends BlockEntity {
    public enum LedState {
        IDLE(0x6D747A),
        TECH(0x2C7572),
        SCIENCE(0x08639C),
        SOCIETY(0x9A7835),
        HUMANITY(0x944549);

        public static final LedState[] CRISSCROSS_MODES = {TECH, SCIENCE, SOCIETY, HUMANITY};
        public final int rgb;

        LedState(int rgb) {
            this.rgb = rgb;
        }

        public static LedState byOrdinal(int ord) {
            LedState[] all = values();
            return ord >= 0 && ord < all.length ? all[ord] : IDLE;
        }
    }

    private static final double RANGE = 14.0D;
    private static final float MAX_YAW_DEG = 70.0F;
    private static final float MIN_PITCH_DEG = -15.0F;
    private static final float MAX_PITCH_DEG = 60.0F;
    private static final float MAX_TRACK_SPEED_DEG = 1.9F;
    private static final float MAX_SWEEP_SPEED_DEG = 0.65F;
    private static final float IDLE_REST_PITCH_DEG = 26.0F;
    private static final int CHASSIS_BLACK_RGB = 0x1F2529;

    private int targetEntityId = -1;
    private LedState ledState = LedState.IDLE;
    private int scanCooldown;
    private int aiSwitchTicks;
    private int lostTargetTicks = 140;

    private float defaultYawDeg;
    private float defaultPitchDeg = IDLE_REST_PITCH_DEG;
    private boolean hasInitializedAngles;
    private float yawDeg;
    private float prevYawDeg;
    private float pitchDeg = IDLE_REST_PITCH_DEG;
    private float prevPitchDeg = IDLE_REST_PITCH_DEG;
    private int animTicks;
    private boolean sweepRight = true;
    private int sweepPauseTicks;

    public SurveillanceCameraBlockEntity(BlockPos pos, BlockState state) {
        super(ChangedSynergyBlockEntities.SURVEILLANCE_CAMERA.get(), pos, state);
    }

    public void initializeDefaultOrientation(BlockState state, Vec3 towardPos) {
        float[] angles = computeLocalAngles(worldPosition, state.getValue(SurveillanceCameraBlock.FACING), towardPos);
        if (angles == null) return;
        defaultYawDeg = Mth.clamp(angles[0], -MAX_YAW_DEG, MAX_YAW_DEG);
        defaultPitchDeg = Mth.clamp(angles[1], MIN_PITCH_DEG, MAX_PITCH_DEG);
        yawDeg = prevYawDeg = defaultYawDeg;
        pitchDeg = prevPitchDeg = defaultPitchDeg;
        hasInitializedAngles = true;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public LedState getLedState() {
        return ledState;
    }

    public float getYawDeg(float partialTick) {
        return Mth.lerp(partialTick, prevYawDeg, yawDeg);
    }

    public float getPitchDeg(float partialTick) {
        return Mth.lerp(partialTick, prevPitchDeg, pitchDeg);
    }

    /** Returns packed tint colors and whether fullbright emission is active for status_led. */
    public void fillLedVisual(float partialTick, float[] outRgbAndBright) {
        float time = animTicks + partialTick;
        if (ledState == LedState.IDLE) {
            int cycle = animTicks % 60;
            boolean pulse = cycle < 5;
            int hex = pulse ? LedState.IDLE.rgb : CHASSIS_BLACK_RGB;
            float scale = pulse ? 0.9F : 1.0F;
            outRgbAndBright[0] = ((hex >> 16) & 0xFF) / 255.0F * scale;
            outRgbAndBright[1] = ((hex >> 8) & 0xFF) / 255.0F * scale;
            outRgbAndBright[2] = (hex & 0xFF) / 255.0F * scale;
            outRgbAndBright[3] = pulse ? 1.0F : 0.0F;
            return;
        }
        float breath = 0.78F + 0.22F * Mth.sin(time * 0.14F);
        int hex = ledState.rgb;
        outRgbAndBright[0] = ((hex >> 16) & 0xFF) / 255.0F * breath;
        outRgbAndBright[1] = ((hex >> 8) & 0xFF) / 255.0F * breath;
        outRgbAndBright[2] = (hex & 0xFF) / 255.0F * breath;
        outRgbAndBright[3] = 1.0F;
    }

    public void serverTick(Level level, BlockPos pos, BlockState state) {
        boolean dirtySync = false;
        if (--scanCooldown <= 0) {
            scanCooldown = (targetEntityId == -1 && ledState == LedState.IDLE)
                    ? (15 + Math.floorMod(pos.hashCode(), 5))
                    : 5;
            int nextTarget = findBestTargetId(level, pos, state);
            if (nextTarget != targetEntityId) {
                targetEntityId = nextTarget;
                dirtySync = true;
            }
        } else if (targetEntityId != -1) {
            Entity current = level.getEntity(targetEntityId);
            if (!(current instanceof LivingEntity living) || !canTrack(level, pos, state, living)) {
                targetEntityId = -1;
                dirtySync = true;
            }
        }

        if (targetEntityId != -1) {
            lostTargetTicks = 0;
            if (ledState == LedState.IDLE) {
                ledState = pickNextCrisscross(level.random, LedState.IDLE);
                aiSwitchTicks = nextSwitchInterval(level.random);
                dirtySync = true;
            } else if (--aiSwitchTicks <= 0) {
                ledState = pickNextCrisscross(level.random, ledState);
                aiSwitchTicks = nextSwitchInterval(level.random);
                dirtySync = true;
            }
        } else {
            if (lostTargetTicks < 140) {
                lostTargetTicks++;
                if (ledState != LedState.IDLE && --aiSwitchTicks <= 0) {
                    ledState = pickNextCrisscross(level.random, ledState);
                    aiSwitchTicks = nextSwitchInterval(level.random);
                    dirtySync = true;
                }
            } else if (ledState != LedState.IDLE) {
                ledState = LedState.IDLE;
                dirtySync = true;
            }
        }

        if (dirtySync) {
            setChanged();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public void clientTick(Level level, BlockPos pos, BlockState state) {
        prevYawDeg = yawDeg;
        prevPitchDeg = pitchDeg;
        animTicks++;

        Entity target = targetEntityId != -1 ? level.getEntity(targetEntityId) : null;
        if (target instanceof LivingEntity living && living.isAlive()) {
            float[] desired = computeLocalAngles(pos, state.getValue(SurveillanceCameraBlock.FACING), living.getEyePosition());
            if (desired != null) {
                float targetYaw = Mth.clamp(desired[0], -MAX_YAW_DEG, MAX_YAW_DEG);
                float targetPitch = Mth.clamp(desired[1], MIN_PITCH_DEG, MAX_PITCH_DEG);
                yawDeg = stepDamped(yawDeg, targetYaw, MAX_TRACK_SPEED_DEG);
                pitchDeg = stepDamped(pitchDeg, targetPitch, MAX_TRACK_SPEED_DEG);
                return;
            }
        }

        if (ledState != LedState.IDLE) {
            if (sweepPauseTicks > 0) {
                sweepPauseTicks--;
            } else {
                float sweepCenter = Mth.clamp(defaultYawDeg, -MAX_YAW_DEG + 34.0F, MAX_YAW_DEG - 34.0F);
                float sweepTarget = Mth.clamp(sweepCenter + (sweepRight ? 34.0F : -34.0F), -MAX_YAW_DEG, MAX_YAW_DEG);
                yawDeg = stepLinear(yawDeg, sweepTarget, MAX_SWEEP_SPEED_DEG);
                if (Math.abs(yawDeg - sweepTarget) < 0.25F) {
                    sweepRight = !sweepRight;
                    sweepPauseTicks = 28;
                }
            }
            pitchDeg = stepLinear(pitchDeg, Mth.clamp(defaultPitchDeg, MIN_PITCH_DEG, MAX_PITCH_DEG), MAX_SWEEP_SPEED_DEG);
        } else {
            yawDeg = stepLinear(yawDeg, defaultYawDeg, 0.45F);
            pitchDeg = stepLinear(pitchDeg, defaultPitchDeg, 0.45F);
        }
    }

    private int findBestTargetId(Level level, BlockPos pos, BlockState state) {
        AABB box = new AABB(pos).inflate(RANGE);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!canTrack(level, pos, state, candidate)) continue;
            double distSq = candidate.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 13.5D / 16.0D, pos.getZ() + 0.5D);
            double score = candidate.getId() == targetEntityId ? distSq * 0.72D : distSq;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best != null ? best.getId() : -1;
    }

    private static boolean canTrack(Level level, BlockPos pos, BlockState state, LivingEntity target) {
        if (!target.isAlive() || target.isSpectator() || target.isInvisible()) return false;
        Direction facing = state.getValue(SurveillanceCameraBlock.FACING);
        Vec3 pivot = pivotWorld(pos, facing);
        Vec3 eye = target.getEyePosition();
        if (pivot.distanceToSqr(eye) > RANGE * RANGE) return false;
        float[] angles = computeLocalAngles(pos, facing, eye);
        if (angles == null || Math.abs(angles[0]) > 82.0F || angles[1] < -25.0F || angles[1] > 72.0F) {
            return false;
        }
        Vec3 delta = eye.subtract(pivot);
        double dist = delta.length();
        Vec3 rayStart = pivot;
        if (dist > 1.0E-4D) {
            Vec3 dir = delta.scale(1.0D / dist);
            // Advance past the camera's own voxel bounds so level.clip() never self-occludes at t=0.
            AABB bounds = state.getVisualShape(level, pos, CollisionContext.empty()).bounds().move(pos);
            double exitT = rayExitDistance(pivot, dir, bounds);
            if (exitT + 0.02D >= dist) return true;
            if (exitT > 0.0D) {
                rayStart = pivot.add(dir.scale(exitT + 0.02D));
            }
        }
        var hit = level.clip(new ClipContext(rayStart, eye, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, target));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(pivot) >= pivot.distanceToSqr(eye) - 0.35D;
    }

    private static double rayExitDistance(Vec3 origin, Vec3 dir, AABB box) {
        double best = Double.MAX_VALUE;
        if (Math.abs(dir.x) > 1.0E-6D) {
            double t = ((dir.x > 0.0D ? box.maxX : box.minX) - origin.x) / dir.x;
            if (t > 0.0D) best = Math.min(best, t);
        }
        if (Math.abs(dir.y) > 1.0E-6D) {
            double t = ((dir.y > 0.0D ? box.maxY : box.minY) - origin.y) / dir.y;
            if (t > 0.0D) best = Math.min(best, t);
        }
        if (Math.abs(dir.z) > 1.0E-6D) {
            double t = ((dir.z > 0.0D ? box.maxZ : box.minZ) - origin.z) / dir.z;
            if (t > 0.0D) best = Math.min(best, t);
        }
        return best == Double.MAX_VALUE ? 0.0D : best;
    }

    private static Vec3 pivotWorld(BlockPos pos, Direction facing) {
        // Head pitch pivot sits at local (8, 13.5, 7) when facing NORTH; Z=7 is 1 px away from the wall (+facing).
        return new Vec3(
                pos.getX() + 0.5D + facing.getStepX() / 16.0D,
                pos.getY() + 13.5D / 16.0D,
                pos.getZ() + 0.5D + facing.getStepZ() / 16.0D
        );
    }

    @Nullable
    private static float[] computeLocalAngles(BlockPos pos, Direction facing, Vec3 targetPos) {
        Vec3 pivot = pivotWorld(pos, facing);
        double dx = targetPos.x - pivot.x;
        double dy = targetPos.y - pivot.y;
        double dz = targetPos.z - pivot.z;
        double fx = facing.getStepX();
        double fz = facing.getStepZ();
        double rx = -fz;
        double rz = fx;
        double localForward = dx * fx + dz * fz;
        double localRight = dx * rx + dz * rz;
        double horiz = Math.hypot(localForward, localRight);
        if (horiz < 1.0E-4D && Math.abs(dy) < 1.0E-4D) return null;
        float relYawDeg = (float) (Math.atan2(localRight, localForward) * Mth.RAD_TO_DEG);
        float pitchDeg = (float) (-Math.atan2(dy, horiz) * Mth.RAD_TO_DEG);
        return new float[]{relYawDeg, pitchDeg};
    }

    private static float stepDamped(float current, float target, float maxStep) {
        float diff = target - current;
        if (Math.abs(diff) < 0.05F) return target;
        float step = Mth.clamp(diff * 0.25F, -maxStep, maxStep);
        if (Math.abs(step) < 0.15F && Math.abs(diff) > 0.15F) {
            step = Math.copySign(0.15F, diff);
        }
        return current + step;
    }

    private static float stepLinear(float current, float target, float maxStep) {
        float diff = target - current;
        if (Math.abs(diff) <= maxStep) return target;
        return current + Math.copySign(maxStep, diff);
    }

    private static LedState pickNextCrisscross(RandomSource random, LedState current) {
        LedState[] modes = LedState.CRISSCROSS_MODES;
        if (current == LedState.IDLE) {
            return modes[random.nextInt(modes.length)];
        }
        int pick = random.nextInt(modes.length - 1);
        int seen = 0;
        for (LedState candidate : modes) {
            if (candidate == current) continue;
            if (seen == pick) return candidate;
            seen++;
        }
        return LedState.TECH;
    }

    private static int nextSwitchInterval(RandomSource random) {
        return 80 + random.nextInt(161);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(
                worldPosition.getX() - 0.1D, worldPosition.getY(), worldPosition.getZ() - 0.1D,
                worldPosition.getX() + 1.1D, worldPosition.getY() + 1.25D, worldPosition.getZ() + 1.1D
        );
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("TargetId", targetEntityId);
        tag.putInt("LedState", ledState.ordinal());
        tag.putFloat("DefaultYaw", defaultYawDeg);
        tag.putFloat("DefaultPitch", defaultPitchDeg);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        targetEntityId = tag.contains("TargetId") ? tag.getInt("TargetId") : -1;
        ledState = LedState.byOrdinal(tag.getInt("LedState"));
        defaultYawDeg = tag.contains("DefaultYaw") ? Mth.clamp(tag.getFloat("DefaultYaw"), -MAX_YAW_DEG, MAX_YAW_DEG) : 0.0F;
        defaultPitchDeg = tag.contains("DefaultPitch") ? Mth.clamp(tag.getFloat("DefaultPitch"), MIN_PITCH_DEG, MAX_PITCH_DEG) : IDLE_REST_PITCH_DEG;
        if (!hasInitializedAngles) {
            yawDeg = prevYawDeg = defaultYawDeg;
            pitchDeg = prevPitchDeg = defaultPitchDeg;
            hasInitializedAngles = true;
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
