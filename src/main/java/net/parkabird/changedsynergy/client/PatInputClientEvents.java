package net.parkabird.changedsynergy.client;

import javax.annotation.Nullable;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.PatAnimationControlPacket;

/**
 * Drives Synergy's own held pat input and mirrors Addon's pat key only for the
 * shared hand animation. Addon's reaction and wording remain Addon's own.
 */
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PatInputClientEvents {
    private static final String ADDON_PAT_KEY = "key.changed_addon.pat_key";
    @Nullable
    private static KeyMapping addonKey;
    private static int activeTarget = -1;
    private static InputSource activeSource;
    private static int nextRefresh;

    private PatInputClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            stop();
            return;
        }
        resolveAddonKey(minecraft);

        InputSource source = SynergyKeyMappings.PAT.isDown()
                ? InputSource.SYNERGY
                : addonKey != null && addonKey.isDown()
                        ? InputSource.ADDON : null;
        if (source == null
                || minecraft.screen != null
                || !(minecraft.hitResult instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof LivingEntity target)
                || minecraft.player.distanceToSqr(target) > 36.0D) {
            stop();
            return;
        }

        int targetId = target.getId();
        boolean newStroke = activeTarget != targetId || activeSource != source;
        if (newStroke || minecraft.player.tickCount >= nextRefresh) {
            activeTarget = targetId;
            activeSource = source;
            nextRefresh = minecraft.player.tickCount + 6;
            ChangedSynergyNetwork.CHANNEL.sendToServer(
                    new PatAnimationControlPacket(
                            targetId,
                            true,
                            newStroke && source == InputSource.SYNERGY));
        }
    }

    private static void resolveAddonKey(Minecraft minecraft) {
        if (addonKey != null
                || !ModList.get().isLoaded(ChangedAddonCompat.MOD_ID)) {
            return;
        }
        for (KeyMapping mapping : minecraft.options.keyMappings) {
            if (ADDON_PAT_KEY.equals(mapping.getName())) {
                addonKey = mapping;
                return;
            }
        }
    }

    private static void stop() {
        if (activeTarget >= 0) {
            ChangedSynergyNetwork.CHANNEL.sendToServer(
                    new PatAnimationControlPacket(
                            activeTarget, false, false));
        }
        activeTarget = -1;
        activeSource = null;
    }

    private enum InputSource {
        SYNERGY,
        ADDON
    }
}
