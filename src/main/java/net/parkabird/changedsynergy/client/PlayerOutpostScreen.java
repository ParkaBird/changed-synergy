package net.parkabird.changedsynergy.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Role;
import net.parkabird.changedsynergy.world.inventory.PlayerOutpostMenu;
import net.parkabird.changedsynergy.world.inventory.PlayerOutpostMenu.MemberView;

/** Small vanilla-style management page without custom textures or client authority. */
public final class PlayerOutpostScreen extends AbstractContainerScreen<PlayerOutpostMenu> {
    private static final int PAGE_SIZE = 5;
    private final List<Button> rows = new ArrayList<>();
    private int page;
    private int selectedIndex;
    private int pendingRemove = -1;
    private Button previous;
    private Button next;
    private Button remove;

    public PlayerOutpostScreen(PlayerOutpostMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 318;
        imageHeight = 282;
        inventoryLabelY = 1000;
        titleLabelY = 1000;
        selectedIndex = menu.members().isEmpty() ? -1 : menu.selected();
        page = Math.max(0, selectedIndex) / PAGE_SIZE;
    }

    @Override
    protected void init() {
        super.init();
        rows.clear();
        for (int row = 0; row < PAGE_SIZE; row++) {
            final int indexOnPage = row;
            Button button = Button.builder(Component.empty(), ignored -> select(page * PAGE_SIZE + indexOnPage))
                    .bounds(leftPos + 9, topPos + 65 + row * 20, imageWidth - 18, 18).build();
            rows.add(addRenderableWidget(button));
        }
        previous = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> changePage(-1))
                .bounds(leftPos + 9, topPos + 166, 25, 18).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> changePage(1))
                .bounds(leftPos + imageWidth - 34, topPos + 166, 25, 18).build());

        Role[] roles = {Role.RESIDENT, Role.GUARD, Role.SUPPLY, Role.CREW,
                Role.HOLD, Role.RETURN, Role.RETREAT};
        for (int i = 0; i < roles.length; i++) {
            Role role = roles[i];
            int column = i % 4;
            int row = i / 4;
            addRenderableWidget(Button.builder(
                            Component.translatable("command.changed_synergy.outpost.role."
                                    + role.name().toLowerCase(java.util.Locale.ROOT)),
                            ignored -> memberAction(role.ordinal()))
                    .bounds(leftPos + 9 + column * 76, topPos + 202 + row * 22, 72, 20).build());
        }
        remove = addRenderableWidget(Button.builder(
                        Component.translatable("menu.changed_synergy.player_outpost.remove"),
                        ignored -> removeMember())
                .bounds(leftPos + 9 + 3 * 76, topPos + 224, 72, 20).build());
        addTeamButton(0, "follow", Role.CREW);
        addTeamButton(1, "hold", Role.HOLD);
        addTeamButton(2, "return", Role.RETURN);
        addTeamButton(3, "retreat", Role.RETREAT);
        refreshRows();
    }

    private void addTeamButton(int column, String name, Role role) {
        addRenderableWidget(Button.builder(
                        Component.translatable("menu.changed_synergy.player_outpost.team_" + name),
                        ignored -> send(PlayerOutpostMenu.TEAM_BASE + role.ordinal()))
                .bounds(leftPos + 9 + column * 76, topPos + 250, 72, 20).build());
    }

    private void select(int index) {
        if (index < 0 || index >= menu.members().size()) return;
        selectedIndex = index;
        send(index);
        pendingRemove = -1;
        remove.setMessage(Component.translatable("menu.changed_synergy.player_outpost.remove"));
        refreshRows();
    }

    private void changePage(int direction) {
        page = Math.max(0, Math.min(maxPage(), page + direction));
        pendingRemove = -1;
        remove.setMessage(Component.translatable("menu.changed_synergy.player_outpost.remove"));
        refreshRows();
    }

    private int maxPage() {
        return Math.max(0, (menu.members().size() - 1) / PAGE_SIZE);
    }

    private void refreshRows() {
        previous.active = page > 0;
        next.active = page < maxPage();
        for (int row = 0; row < PAGE_SIZE; row++) {
            int index = page * PAGE_SIZE + row;
            Button button = rows.get(row);
            button.visible = index < menu.members().size();
            if (!button.visible) continue;
            MemberView member = menu.members().get(index);
            String name = font.plainSubstrByWidth(member.name(), 112);
            var role = Component.translatable("command.changed_synergy.outpost.role."
                    + member.role().name().toLowerCase(java.util.Locale.ROOT));
            if (member.role() == Role.SUPPLY) {
                role.append(Component.literal(" · ")).append(Component.translatable(
                        "menu.changed_synergy.player_outpost.supply."
                                + member.supplyType().name().toLowerCase(java.util.Locale.ROOT)));
            }
            button.setMessage(Component.literal((index == selectedIndex ? "▶ " : "  ")
                    + (member.loaded() ? "● " : "○ ") + name + "  ·  ")
                    .append(role));
        }
    }

    private void memberAction(int action) {
        if (selectedIndex >= 0 && selectedIndex < menu.members().size()) {
            send(PlayerOutpostMenu.ACTION_BASE + action);
        }
    }

    private void removeMember() {
        if (selectedIndex < 0 || selectedIndex >= menu.members().size()) return;
        if (pendingRemove != selectedIndex) {
            pendingRemove = selectedIndex;
            remove.setMessage(Component.translatable("menu.changed_synergy.player_outpost.confirm"));
            return;
        }
        memberAction(PlayerOutpostMenu.REMOVE);
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF303030);
        graphics.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3,
                topPos + imageHeight - 3, 0xFFC6C6C6);
        graphics.fill(leftPos + 7, topPos + 61, leftPos + imageWidth - 7,
                topPos + 186, 0xFF8B8B8B);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int dark = 0x303030;
        graphics.drawString(font, title, 9, 8, dark, false);
        Component home = Component.translatable(
                menu.active() ? "menu.changed_synergy.player_outpost.active"
                        : "menu.changed_synergy.player_outpost.inactive",
                menu.dimension(), menu.marker().toShortString());
        graphics.drawString(font, font.plainSubstrByWidth(home.getString(), imageWidth - 18),
                9, 22, dark, false);
        Component storage = Component.translatable("menu.changed_synergy.player_outpost.storage",
                menu.storage(), menu.stock() < 0 ? "?" : menu.stock());
        graphics.drawString(font, font.plainSubstrByWidth(storage.getString(), imageWidth - 18),
                9, 35, dark, false);
        Component bed = Component.translatable("menu.changed_synergy.player_outpost.bed", menu.bed());
        graphics.drawString(font, font.plainSubstrByWidth(bed.getString(), imageWidth - 18),
                9, 48, dark, false);
        graphics.drawString(font, Component.literal((page + 1) + "/" + (maxPage() + 1)),
                imageWidth / 2 - 12, 170, dark, false);
        if (selectedIndex >= 0 && selectedIndex < menu.members().size()) {
            MemberView member = menu.members().get(selectedIndex);
            String name = font.plainSubstrByWidth(member.name(), 85);
            Component location = Component.translatable(
                    "menu.changed_synergy.player_outpost.member_location", name,
                    member.dimension(), member.position().toShortString());
            graphics.drawString(font, font.plainSubstrByWidth(location.getString(), imageWidth - 18),
                    9, 190, dark, false);
        } else {
            graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(
                    "menu.changed_synergy.player_outpost.empty").getString(), imageWidth - 18),
                    9, 190, dark, false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
