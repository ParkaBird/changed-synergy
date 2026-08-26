package net.parkabird.changedsynergy.client;

import net.ltxprogrammer.changed.Changed;
import net.parkabird.changedsynergy.world.inventory.CentaurMountConfigMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Changed's compact native taur tack panel. */
public final class CentaurMountConfigScreen
        extends AbstractContainerScreen<CentaurMountConfigMenu> {
    private static final ResourceLocation TEXTURE =
            Changed.modResource("textures/gui/centaur_saddle.png");
    private Button cargoButton;
    private Button rideButton;

    public CentaurMountConfigScreen(
            CentaurMountConfigMenu menu,
            Inventory inventory,
            Component title) {
        super(menu, inventory, title);
        imageWidth = 182;
        imageHeight = 126;
    }

    @Override
    protected void init() {
        super.init();
        cargoButton = Button.builder(
                        Component.translatable(
                                "menu.changed_synergy.centaur_mount.cargo"),
                        ignored -> pressMenuButton(
                                CentaurMountConfigMenu.CARGO_BUTTON_ID))
                .bounds(leftPos + 115, topPos + 4, 58, 15)
                .tooltip(Tooltip.create(Component.translatable(
                        "menu.changed_synergy.centaur_mount.cargo.tooltip")))
                .build();
        rideButton = Button.builder(
                        Component.translatable(
                                "menu.changed_synergy.centaur_mount.ride"),
                        ignored -> pressMenuButton(
                                CentaurMountConfigMenu.RIDE_BUTTON_ID))
                .bounds(leftPos + 115, topPos + 21, 58, 15)
                .tooltip(Tooltip.create(Component.translatable(
                        "menu.changed_synergy.centaur_mount.ride.tooltip")))
                .build();
        addRenderableWidget(cargoButton);
        addRenderableWidget(rideButton);
        refreshButtons();
    }

    private void pressMenuButton(int buttonId) {
        if (minecraft == null
                || minecraft.player == null
                || minecraft.gameMode == null
                || !menu.clickMenuButton(minecraft.player, buttonId)) {
            return;
        }
        minecraft.gameMode.handleInventoryButtonClick(
                menu.containerId,
                buttonId);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshButtons();
    }

    private void refreshButtons() {
        if (cargoButton != null) {
            cargoButton.active = menu.hasChest();
        }
        if (rideButton != null) {
            rideButton.active = menu.hasSaddle() && !menu.isViewerRiding();
        }
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(
            GuiGraphics graphics,
            float partialTick,
            int mouseX,
            int mouseY) {
        graphics.blit(
                TEXTURE,
                leftPos,
                topPos,
                0,
                0,
                imageWidth,
                imageHeight,
                imageWidth,
                imageHeight);
    }

    @Override
    protected void renderLabels(
            GuiGraphics graphics,
            int mouseX,
            int mouseY) {
        graphics.drawString(
                font,
                Component.translatable(
                        "menu.changed_synergy.centaur_mount.title"),
                8,
                5,
                0x404040,
                false);
        graphics.drawString(
                font,
                font.plainSubstrByWidth(title.getString(), 58),
                8,
                22,
                0x606060,
                false);
    }
}
