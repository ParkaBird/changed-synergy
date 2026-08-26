package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import org.lwjgl.glfw.GLFW;

/** Key mappings owned by Synergy rather than optional compatibility mods. */
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SynergyKeyMappings {
    public static final KeyMapping PAT = new KeyMapping(
            "key.changed_synergy.pat",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.categories.changed_synergy");

    private SynergyKeyMappings() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(PAT);
    }
}
