package net.parkabird.changedsynergy.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;

/** Shortcut hint for the player's claimed bell, using the server-synced marker. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PlayerOutpostHintOverlay {
    private PlayerOutpostHintOverlay() {}

    @SubscribeEvent
    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("player_outpost_hint", PlayerOutpostHintOverlay::render);
    }

    private static void render(ForgeGui gui, GuiGraphics graphics, float partialTick,
                               int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        var state = TerritoryClientState.current();
        if (minecraft.options.hideGui || minecraft.screen != null
                || minecraft.player == null || minecraft.level == null
                || !minecraft.player.isAlive() || minecraft.player.isSpectator()
                || state == null || state.outpostMarker() == null
                || !(minecraft.hitResult instanceof BlockHitResult hit)
                || !state.outpostMarker().equals(hit.getBlockPos())
                || !minecraft.level.getBlockState(hit.getBlockPos()).is(Blocks.BELL)) return;

        Component hint = Component.translatable("overlay.changed_synergy.player_outpost.hint",
                minecraft.options.keyShift.getTranslatedKeyMessage(),
                minecraft.options.keyUse.getTranslatedKeyMessage());
        int width = minecraft.font.width(hint);
        int x = (screenWidth - width) / 2;
        int y = screenHeight - 58;
        graphics.fill(x - 5, y - 4, x + width + 5, y + 12, 0x9008080B);
        graphics.drawString(minecraft.font, hint, x, y, 0xFFE8E3C5, true);
    }
}
