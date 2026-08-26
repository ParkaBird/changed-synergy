package net.parkabird.changedsynergy.client;

import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
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
            int cycleTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!active || minecraft.level == null) {
            ACTIVE.remove(actorId);
            return;
        }
        long now = minecraft.level.getGameTime();
        int safeCycle = Math.max(2, cycleTicks);
        State current = ACTIVE.get(actorId);
        long startedAt = current != null
                && now < current.expiresAt
                && current.cycleTicks == safeCycle
                        ? current.startedAt : now;
        ACTIVE.put(actorId, new State(
                startedAt,
                now + Math.max(1, durationTicks),
                safeCycle));
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
        float elapsed = (now - state.startedAt) + event.getPartialTick();
        float phase = (elapsed % state.cycleTicks) / state.cycleTicks;
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

    private record State(long startedAt, long expiresAt, int cycleTicks) {
    }
}
