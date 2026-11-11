package net.parkabird.changedsynergy.client;

import net.ltxprogrammer.changed.Changed;
import net.parkabird.changedsynergy.world.inventory.BondedCreatureInventoryMenu;
import net.parkabird.changedsynergy.world.inventory.PlayerOutpostCargoInventory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Inventory screen shared by native and generic bonded creatures. */
public final class BondedCreatureInventoryScreen
        extends AbstractContainerScreen<BondedCreatureInventoryMenu> {
    private static final ResourceLocation TEXTURE =
            Changed.modResource("textures/gui/tamed_dl_inventory.png");
    private static final ResourceLocation CURIOS_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    "curios", "textures/gui/inventory.png");
    private static final ResourceLocation CHANGED_APPAREL_TEXTURE =
            Changed.modResource("textures/gui/basic_player_info.png");
    private float mouseX;
    private float mouseY;

    public BondedCreatureInventoryScreen(
            BondedCreatureInventoryMenu menu,
            Inventory inventory,
            Component title) {
        super(menu, inventory, title);
        imageWidth = 212;
        imageHeight = menu.hasWorkCargo() ? BondedCreatureInventoryMenu.WORK_SLOT_Y + 24 : 166;
    }

    @Override
    protected void init() {
        super.init();
        if (menu.hasCurios()) {
            ImageButton curiosButton = new ImageButton(
                    leftPos + 27,
                    topPos + 7,
                    14,
                    14,
                    50,
                    0,
                    14,
                    CURIOS_TEXTURE,
                    256,
                    256,
                    ignored -> toggleCurios());
            addRenderableWidget(curiosButton);
        }
        if (menu.hasClothing() && !menu.integratedClothing()) {
            // Match Changed's player inventory: the apparel button uses the
            // native 20x20 two-state icon and sits directly above the hand slot.
            addRenderableWidget(new ImageButton(
                    leftPos + 75,
                    topPos + 40,
                    20,
                    20,
                    0,
                    0,
                    20,
                    CHANGED_APPAREL_TEXTURE,
                    20,
                    40,
                    ignored -> toggleClothing()));
        }
    }

    @Override protected void slotClicked(net.minecraft.world.inventory.Slot slot,int index,int button,net.minecraft.world.inventory.ClickType click) {
        if(!ClothingInteractionClient.interact(slot,button,click,menu.getPet(),menu.clothingIdentifier(index)))super.slotClicked(slot,index,button,click);
    }
    private void toggleCurios() {
        if (minecraft == null
                || minecraft.player == null
                || minecraft.gameMode == null
                || !menu.clickMenuButton(
                        minecraft.player,
                        BondedCreatureInventoryMenu.CURIOS_BUTTON_ID)) {
            return;
        }
        minecraft.gameMode.handleInventoryButtonClick(
                menu.containerId,
                BondedCreatureInventoryMenu.CURIOS_BUTTON_ID);
    }

    private void toggleClothing() {
        if (minecraft == null
                || minecraft.player == null
                || minecraft.gameMode == null
                || !menu.clickMenuButton(
                        minecraft.player,
                        BondedCreatureInventoryMenu.CLOTHING_BUTTON_ID)) {
            return;
        }
        minecraft.gameMode.handleInventoryButtonClick(
                menu.containerId,
                BondedCreatureInventoryMenu.CLOTHING_BUTTON_ID);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (menu.hasWorkCargo()) {
            renderWorkCargoLabel(graphics, Component.translatable("menu.changed_synergy.inventory.work_cargo"));
        }
    }

    private void renderWorkCargoLabel(GuiGraphics graphics, Component label) {
        int left = BondedCreatureInventoryMenu.WORK_SLOT_X + PlayerOutpostCargoInventory.SIZE * 18 + 8;
        int available = imageWidth - left - 8;
        int textWidth = font.width(label);
        float scale = Math.min(1, available / (float)Math.max(1, textWidth));
        graphics.pose().pushPose();
        graphics.pose().translate(left + Math.round((available - textWidth * scale) / 2),
                BondedCreatureInventoryMenu.WORK_SLOT_Y + Math.round((16 - font.lineHeight * scale) / 2), 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, label, 0, 0, 0x404040, false);
        graphics.pose().popPose();
    }

    @Override
    protected void renderBg(
            GuiGraphics graphics,
            float partialTick,
            int mouseX,
            int mouseY) {
        if (menu.hasWorkCargo()) {
            InventoryFrame.draw(graphics, leftPos, topPos, imageWidth, imageHeight);
            graphics.blit(TEXTURE, leftPos + 4, topPos + 4, 4, 4, 204, 158, 212, 166);
            renderSlots(graphics, BondedCreatureInventoryMenu.WORK_SLOT_X,
                    BondedCreatureInventoryMenu.WORK_SLOT_Y, PlayerOutpostCargoInventory.SIZE,
                    PlayerOutpostCargoInventory.SIZE);
        } else {
            graphics.blit(TEXTURE, leftPos, topPos, 0.0F, 0.0F, 212, 166, 212, 166);
        }
        if (menu.clothingVisible() && !menu.integratedClothing()) {
            graphics.fill(
                    leftPos + 94,
                    topPos + 4,
                    leftPos + 208,
                    topPos + 80,
                    0xFFC6C6C6);
            renderSlots(
                    graphics,
                    BondedCreatureInventoryMenu.CLOTHING_SLOT_X,
                    BondedCreatureInventoryMenu.CLOTHING_SLOT_Y,
                    BondedCreatureInventoryMenu.CLOTHING_COLUMNS,
                    menu.clothingSlotCount());
        }
        if (menu.getPet() != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    graphics,
                    leftPos + 51,
                    topPos + 75,
                    30,
                    leftPos + 51 - this.mouseX,
                    topPos + 25 - this.mouseY,
                    menu.getPet());
        }
        if (menu.curiosVisible()) {
            renderSlotPanel(
                    graphics,
                    menu.curioSlotX(),
                    BondedCreatureInventoryMenu.CURIO_SLOT_Y,
                    menu.curioColumns(),
                    menu.equipmentSlotCount());
        }
    }

    private void renderSlotPanel(
            GuiGraphics graphics,
            int relativeX,
            int relativeY,
            int columns,
            int slotCount) {
        int usedColumns = Math.min(columns, slotCount);
        int rows = (slotCount + columns - 1) / columns;
        int panelX = leftPos + relativeX - 4;
        int panelY = topPos + relativeY - 4;
        int panelWidth = usedColumns * 18 + 8;
        int panelHeight = rows * 18 + 8;
        InventoryFrame.draw(graphics, panelX, panelY, panelWidth, panelHeight);
        renderSlots(graphics, relativeX, relativeY, columns, slotCount);
    }

    private void renderSlots(GuiGraphics graphics, int relativeX, int relativeY, int columns, int slotCount) {
        for (int index = 0; index < slotCount; index++) {
            int slotX = leftPos + relativeX + index % columns * 18;
            int slotY = topPos + relativeY + index / columns * 18;
            InventoryFrame.slot(graphics, slotX, slotY);
        }
    }
}
