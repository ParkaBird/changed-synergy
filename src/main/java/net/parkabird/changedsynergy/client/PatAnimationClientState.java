package net.parkabird.changedsynergy.client;

import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;

/** Client-only brush-like left-right pat motion. */
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PatAnimationClientState {
    private static final float BLEND_TICKS = 4.0F;
    private static final Map<Integer, State> ACTIVE = new HashMap<>();

    private PatAnimationClientState() {
    }

    public static void update(
            int actorId,
            boolean active,
            int durationTicks,
            float cycleTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            ACTIVE.remove(actorId);
            return;
        }
        long now = minecraft.level.getGameTime();
        State current = ACTIVE.get(actorId);
        if (!active) {
            if (current != null) {
                ACTIVE.put(actorId, current.fadeOut(now));
            }
            return;
        }
        float safeCycle = Math.max(2.0F, cycleTicks);
        float phaseOffset = current != null && current.visible(now)
                ? current.phase(now) : 0.0F;
        float currentBlend = current != null && current.visible(now)
                ? current.blend(now) : 0.0F;
        ACTIVE.put(actorId, new State(
                now,
                now + Math.max(1, durationTicks),
                safeCycle,
                phaseOffset,
                now - Math.round(currentBlend * BLEND_TICKS),
                -1L,
                1.0F));
    }

    public static boolean isActive(int actorId) {
        Minecraft minecraft = Minecraft.getInstance();
        State state = ACTIVE.get(actorId);
        if (minecraft.level == null || state == null
                || !state.visible(minecraft.level.getGameTime())) {
            ACTIVE.remove(actorId);
            return false;
        }
        return state.interactionActive(minecraft.level.getGameTime());
    }

    private static Pose pose(int actorId, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        State state = ACTIVE.get(actorId);
        if (minecraft.level == null || state == null) {
            return null;
        }
        double now = minecraft.level.getGameTime() + partialTick;
        if (!state.visible(now)) {
            ACTIVE.remove(actorId);
            return null;
        }
        return new Pose(state.phase(now), state.blend(now));
    }

    /** Applies the shared shoulder-height third-person stroke to any armed model. */
    public static boolean applyRightArmPose(
            int actorId,
            float partialTick,
            ModelPart arm) {
        Pose pose = pose(actorId, partialTick);
        if (pose == null) {
            return false;
        }
        float sweep = (float)Math.sin(pose.phase * Math.PI * 2.0D);
        arm.xRot = lerp(pose.blend, arm.xRot, -1.52F + 0.07F * sweep);
        arm.yRot = lerp(pose.blend, arm.yRot, -0.14F + 0.10F * sweep);
        arm.zRot = lerp(pose.blend, arm.zRot, 0.08F * sweep);
        return true;
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.level == null
                || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Pose pose = pose(minecraft.player.getId(), event.getPartialTick());
        if (pose == null) {
            return;
        }
        float sweep = (float)Math.sin(pose.phase * Math.PI * 2.0D);
        float lift = 0.5F - 0.5F
                * (float)Math.cos(pose.phase * Math.PI * 2.0D);
        event.getPoseStack().translate(
                pose.blend * 0.036F * sweep,
                pose.blend * -0.012F * lift,
                pose.blend * -0.040F * lift);
        event.getPoseStack().mulPose(Axis.XP.rotationDegrees(
                pose.blend * -3.0F * lift));
        event.getPoseStack().mulPose(Axis.YP.rotationDegrees(
                pose.blend * 2.2F * sweep));
        event.getPoseStack().mulPose(Axis.ZP.rotationDegrees(
                pose.blend * 6.0F * sweep));
    }

    private static float lerp(float amount, float from, float to) {
        return from + amount * (to - from);
    }

    private static float smooth(float value) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private record Pose(float phase, float blend) {
    }

    private record State(
            long phaseStartedAt,
            long activeUntil,
            float cycleTicks,
            float phaseOffset,
            long blendStartedAt,
            long fadeStartedAt,
            float fadeStartBlend) {
        private float phase(double now) {
            double phase = phaseOffset
                    + (now - phaseStartedAt) / cycleTicks;
            return (float)(phase - Math.floor(phase));
        }

        private float blend(double now) {
            float blendIn = smooth((float)((now - blendStartedAt) / BLEND_TICKS));
            if (fadeStartedAt >= 0L) {
                float fade = 1.0F - smooth(
                        (float)((now - fadeStartedAt) / BLEND_TICKS));
                return Math.min(blendIn, fadeStartBlend * fade);
            }
            if (now > activeUntil) {
                return blendIn * (1.0F - smooth(
                        (float)((now - activeUntil) / BLEND_TICKS)));
            }
            return blendIn;
        }

        private boolean interactionActive(long now) {
            return fadeStartedAt < 0L && now < activeUntil;
        }

        private boolean visible(double now) {
            long fadeBase = fadeStartedAt >= 0L ? fadeStartedAt : activeUntil;
            return now < fadeBase + BLEND_TICKS;
        }

        private State fadeOut(long now) {
            if (fadeStartedAt >= 0L) {
                return this;
            }
            return new State(
                    phaseStartedAt,
                    now,
                    cycleTicks,
                    phaseOffset,
                    blendStartedAt,
                    now,
                    blend(now));
        }
    }
}
