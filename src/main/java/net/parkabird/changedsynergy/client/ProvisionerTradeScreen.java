package net.parkabird.changedsynergy.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.parkabird.changedsynergy.ai.ProvisionerTradeService.TradeOffer;
import net.parkabird.changedsynergy.world.inventory.ProvisionerTradeMenu;

/** Provisioner barter presented with Minecraft's native merchant interface. */
public final class ProvisionerTradeScreen
        extends AbstractContainerScreen<ProvisionerTradeMenu> {
    private static final ResourceLocation VILLAGER_TEXTURE =
            ResourceLocation.withDefaultNamespace(
                    "textures/gui/container/villager2.png");
    private static final Component TRADES_LABEL =
            Component.translatable("merchant.trades");
    private final List<Button> tradeButtons = new ArrayList<>(4);
    private int selectedOffer;
    private int seenSyncSerial;
    private boolean tradePending;

    public ProvisionerTradeScreen(
            ProvisionerTradeMenu menu,
            Inventory inventory,
            Component title) {
        super(menu, inventory, title);
        imageWidth = 276;
        imageHeight = 166;
        inventoryLabelX = 107;
        inventoryLabelY = 74;
    }

    @Override
    protected void init() {
        super.init();
        tradeButtons.clear();
        seenSyncSerial = menu.getSyncSerial();
        tradePending = false;
        int rowY = topPos + 18;
        for (int index = 0; index < 4; index++) {
            int offerIndex = index;
            Button button = Button.builder(
                            Component.empty(),
                            ignored -> selectedOffer = offerIndex)
                    .bounds(leftPos + 5, rowY, 89, 20)
                    .build();
            tradeButtons.add(addRenderableWidget(button));
            rowY += 20;
        }
        refreshButtons();
    }

    private void exchangeSelected() {
        if (minecraft != null && minecraft.gameMode != null) {
            tradePending = true;
            minecraft.gameMode.handleInventoryButtonClick(
                    menu.containerId, selectedOffer);
        }
    }

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button) {
        TradeOffer selected = menu.getOffers().get(selectedOffer);
        if (button == 0
                && !tradePending
                && selected.enabled()
                && selected.maxTrades() > 0
                && mouseX >= leftPos + 218
                && mouseX < leftPos + 240
                && mouseY >= topPos + 35
                && mouseY < topPos + 57) {
            exchangeSelected();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (seenSyncSerial != menu.getSyncSerial()) {
            seenSyncSerial = menu.getSyncSerial();
            tradePending = false;
        }
        refreshButtons();
    }

    private void refreshButtons() {
        List<TradeOffer> offers = menu.getOffers();
        selectedOffer = Math.max(0,
                Math.min(selectedOffer, offers.size() - 1));
        for (int index = 0; index < tradeButtons.size(); index++) {
            TradeOffer offer = offers.get(index);
            tradeButtons.get(index).active = offer.enabled()
                    && offer.maxTrades() > 0;
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
        renderOffers(graphics);
        renderOfferTooltip(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(
            GuiGraphics graphics,
            float partialTick,
            int mouseX,
            int mouseY) {
        graphics.blit(
                VILLAGER_TEXTURE,
                leftPos,
                topPos,
                0.0F,
                0.0F,
                imageWidth,
                imageHeight,
                512,
                256);
        TradeOffer selected = menu.getOffers().get(selectedOffer);
        if (tradePending
                || !selected.enabled() || selected.maxTrades() <= 0) {
            graphics.blit(
                    VILLAGER_TEXTURE,
                    leftPos + 182,
                    topPos + 35,
                    311.0F,
                    0.0F,
                    28,
                    21,
                    512,
                    256);
        }
    }

    @Override
    protected void renderLabels(
            GuiGraphics graphics,
            int mouseX,
            int mouseY) {
        String shortTitle = font.plainSubstrByWidth(title.getString(), 160);
        int titleX = 49 + imageWidth / 2 - font.width(shortTitle) / 2;
        graphics.drawString(font, shortTitle, titleX, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle,
                inventoryLabelX, inventoryLabelY, 0x404040, false);
        graphics.drawString(font, TRADES_LABEL,
                48 - font.width(TRADES_LABEL) / 2,
                6, 0x404040, false);

        TradeOffer selected = menu.getOffers().get(selectedOffer);
        Component kind = Component.translatable(
                "menu.changed_synergy.trade.kind."
                        + selected.kind().name().toLowerCase());
        graphics.drawString(font,
                font.plainSubstrByWidth(kind.getString(), 154),
                108, 18, 0x505050, false);
        if (!menu.getStatusKey().isBlank()) {
            Component status = Component.translatable(menu.getStatusKey());
            int color = menu.getStatusKey().endsWith("success")
                    ? 0x397A46 : 0x9A5A24;
            graphics.drawString(font,
                    font.plainSubstrByWidth(status.getString(), 154),
                    108, 65, color, false);
        } else {
            graphics.drawString(font,
                    Component.translatable(
                            selected.payment().isEmpty()
                                    || selected.result().isEmpty()
                                    ? "menu.changed_synergy.trade.no_stock"
                                    : "menu.changed_synergy.trade.click_hint"),
                    108, 65, 0x686868, false);
        }
    }

    private void renderOffers(GuiGraphics graphics) {
        int rowY = topPos + 20;
        for (TradeOffer offer : menu.getOffers()) {
            if (!offer.payment().isEmpty() && !offer.result().isEmpty()) {
                graphics.pose().pushPose();
                graphics.pose().translate(0.0F, 0.0F, 100.0F);
                renderStack(graphics, offer.payment(), leftPos + 10, rowY);
                renderArrow(graphics, leftPos, rowY,
                        !offer.enabled() || offer.maxTrades() <= 0);
                String uses = Integer.toString(offer.maxTrades());
                graphics.drawString(font, uses,
                        leftPos + 72 - font.width(uses),
                        rowY + 9, 0xFFFFFF, false);
                renderStack(graphics, offer.result(), leftPos + 78, rowY);
                graphics.pose().popPose();
            }
            rowY += 20;
        }

        TradeOffer selected = menu.getOffers().get(selectedOffer);
        if (!selected.payment().isEmpty() && !selected.result().isEmpty()) {
            renderStack(graphics, selected.payment(),
                    leftPos + 136, topPos + 37);
            renderArrow(graphics, leftPos + 126, topPos + 37,
                    tradePending || !selected.enabled()
                            || selected.maxTrades() <= 0);
            renderStack(graphics, selected.result(),
                    leftPos + 220, topPos + 37);
            graphics.drawString(font,
                    Component.translatable(
                            "menu.changed_synergy.trade.available",
                            selected.maxTrades()),
                    leftPos + 162,
                    topPos + 28,
                    0x404040,
                    false);
        }
    }

    private void renderStack(
            GuiGraphics graphics,
            ItemStack stack,
            int x,
            int y) {
        graphics.renderItem(stack, x, y);
        graphics.renderItemDecorations(font, stack, x, y);
    }

    private void renderArrow(
            GuiGraphics graphics,
            int baseX,
            int y,
            boolean disabled) {
        graphics.blit(
                VILLAGER_TEXTURE,
                baseX + 60,
                y + 3,
                disabled ? 25.0F : 15.0F,
                171.0F,
                10,
                9,
                512,
                256);
    }

    private void renderOfferTooltip(
            GuiGraphics graphics,
            int mouseX,
            int mouseY) {
        for (int index = 0; index < 4; index++) {
            TradeOffer offer = menu.getOffers().get(index);
            int rowY = topPos + 20 + index * 20;
            if (inside(mouseX, mouseY, leftPos + 10, rowY, 16, 16)) {
                renderItemTooltip(graphics, offer.payment(), mouseX, mouseY);
                return;
            }
            if (inside(mouseX, mouseY, leftPos + 78, rowY, 16, 16)) {
                renderItemTooltip(graphics, offer.result(), mouseX, mouseY);
                return;
            }
            if (inside(mouseX, mouseY,
                    leftPos + 5, topPos + 18 + index * 20, 89, 20)) {
                Component kind = Component.translatable(
                        "menu.changed_synergy.trade.kind."
                                + offer.kind().name().toLowerCase());
                graphics.renderTooltip(font,
                        List.of(
                                kind,
                                Component.translatable(
                                        "menu.changed_synergy.trade.row_hint")),
                        Optional.empty(),
                        mouseX,
                        mouseY);
                return;
            }
        }
        TradeOffer selected = menu.getOffers().get(selectedOffer);
        if (inside(mouseX, mouseY,
                leftPos + 136, topPos + 37, 16, 16)) {
            renderItemTooltip(
                    graphics, selected.payment(), mouseX, mouseY);
        } else if (inside(mouseX, mouseY,
                leftPos + 220, topPos + 37, 16, 16)) {
            renderItemTooltip(
                    graphics, selected.result(), mouseX, mouseY);
        }
    }

    private void renderItemTooltip(
            GuiGraphics graphics,
            ItemStack stack,
            int mouseX,
            int mouseY) {
        if (!stack.isEmpty()) {
            graphics.renderTooltip(font, getTooltipFromContainerItem(stack),
                    stack.getTooltipImage(), stack, mouseX, mouseY);
        }
    }

    private static boolean inside(
            int mouseX,
            int mouseY,
            int x,
            int y,
            int width,
            int height) {
        return mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + height;
    }
}
