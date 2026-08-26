package net.parkabird.changedsynergy.client;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.Event.Result;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.parkabird.changedsynergy.ChangedSynergyMod;

@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NameVisibilityClientEvents {
    private static final double NAME_RENDER_DISTANCE_SQR = 10.0D * 10.0D;

    private NameVisibilityClientEvents() {
    }

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof ChangedEntity mob)
                || !mob.hasCustomName()) {
            return;
        }

        Entity cameraEntity = Minecraft.getInstance().getCameraEntity();
        if (cameraEntity != null
                && cameraEntity.distanceToSqr(mob) > NAME_RENDER_DISTANCE_SQR) {
            event.setResult(Result.DENY);
        }
    }
}
