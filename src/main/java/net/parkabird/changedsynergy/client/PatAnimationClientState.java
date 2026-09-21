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
    private static final Map<Integer, State> ACTIVE = new HashMap<>();

    private PatAnimationClientState() {
    }

    public static void update(
            int actorId,
            boolean active,
            int durationTicks,
            float cycleTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!active || minecraft.level == null) {
            ACTIVE.remove(actorId);
            return;
        }
        long now = minecraft.level.getGameTime();
        float safeCycle = Math.max(2.0F, cycleTicks);
        State current = ACTIVE.get(actorId);
        float phaseOffset = current != null && now < current.expiresAt
                ? current.phase(now) : 0.0F;
        ACTIVE.put(actorId, new State(
                now,
                now + Math.max(1, durationTicks),
                safeCycle,
                phaseOffset));
    }

    public static boolean isActive(int actorId) {
        Minecraft minecraft = Minecraft.getInstance();
        State state = ACTIVE.get(actorId);
        if (minecraft.level == null || state == null
                || minecraft.level.getGameTime() >= state.expiresAt) {
            ACTIVE.remove(actorId);
            return false;
        }
        return true;
    }

    public static float phase(int actorId, float partialTick) {
        if (!isActive(actorId)) {
            return -1.0F;
        }
        Minecraft minecraft = Minecraft.getInstance();
        return ACTIVE.get(actorId).phase(minecraft.level.getGameTime() + partialTick);
    }

    /** Applies the shared shoulder-height third-person stroke to any armed model. */
    public static boolean applyRightArmPose(
            int actorId,
            float partialTick,
            ModelPart arm) {
        float phase = phase(actorId, partialTick);
        if (phase < 0.0F) {
            return false;
        }
        float sweep = (float)Math.sin(phase * Math.PI * 2.0D);
        arm.xRot = -1.52F + 0.07F * sweep;
        arm.yRot = -0.14F + 0.10F * sweep;
        arm.zRot = 0.08F * sweep;
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
        State state = ACTIVE.get(minecraft.player.getId());
        long now = minecraft.level.getGameTime();
        if (state == null || now >= state.expiresAt) {
            ACTIVE.remove(minecraft.player.getId());
            return;
        }
        float phase = state.phase(now + event.getPartialTick());
        float sweep = (float)Math.sin(phase * Math.PI * 2.0D);
        float lift = 0.5F - 0.5F
                * (float)Math.cos(phase * Math.PI * 2.0D);
        event.getPoseStack().translate(
                0.036F * sweep,
                -0.012F * lift,
                -0.040F * lift);
        event.getPoseStack().mulPose(Axis.XP.rotationDegrees(-3.0F * lift));
        event.getPoseStack().mulPose(Axis.YP.rotationDegrees(2.2F * sweep));
        event.getPoseStack().mulPose(Axis.ZP.rotationDegrees(6.0F * sweep));
    }

    private record State(
            long phaseStartedAt,
            long expiresAt,
            float cycleTicks,
            float phaseOffset) {
        private float phase(double now) {
            double phase = phaseOffset
                    + (now - phaseStartedAt) / cycleTicks;
            return (float)(phase - Math.floor(phase));
        }
    }
}
