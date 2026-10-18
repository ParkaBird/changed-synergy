package net.parkabird.changedsynergy.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.parkabird.changedsynergy.ai.PlayerOutpostSupplyType;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Role;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Work;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.CrewJob;
import net.parkabird.changedsynergy.world.inventory.PlayerOutpostMenu;
import net.parkabird.changedsynergy.world.inventory.PlayerOutpostMenu.MemberView;

/** Small vanilla-style management page without custom textures or client authority. */
public final class PlayerOutpostScreen extends AbstractContainerScreen<PlayerOutpostMenu> {
    private static final int PAGE_SIZE = 5;
    private int pageSize = PAGE_SIZE;
    private final List<Button> rows = new ArrayList<>();
    private int page;
    private int selectedIndex;
    private int pendingRemove = -1;
    private Button previous;
    private Button next;
    private Button remove;
    private Button mainWork;
    private Button backupWork;
    private Button meals;
    private final List<Button> memberOrders = new ArrayList<>();

    public PlayerOutpostScreen(PlayerOutpostMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 360;
        imageHeight = 323;
        inventoryLabelY = 1000;
        titleLabelY = 1000;
        selectedIndex = menu.members().isEmpty() ? -1 : menu.selected();
        page = Math.max(0, selectedIndex) / PAGE_SIZE;
    }

    @Override
    protected void init() {
        imageWidth = Math.min(360, width - 8);
        pageSize = Math.max(1, Math.min(PAGE_SIZE, (height - 223) / 20));
        imageHeight = 219 + pageSize * 20;
        if (selectedIndex >= 0) page = selectedIndex / pageSize;
        super.init();
        rows.clear();
        memberOrders.clear();
        for (int row = 0; row < pageSize; row++) {
            final int indexOnPage = row;
            Button button = Button.builder(Component.empty(), ignored -> select(page * pageSize + indexOnPage))
                    .bounds(leftPos + 9, topPos + 78 + row * 20, imageWidth - 18, 18).build();
            rows.add(addRenderableWidget(button));
        }
        previous = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> changePage(-1))
                .bounds(leftPos + 9, topPos + lowerY(179), 25, 18).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> changePage(1))
                .bounds(leftPos + imageWidth - 34, topPos + lowerY(179), 25, 18).build());

        mainWork = addRenderableWidget(Button.builder(Component.empty(), ignored -> cycleWork(false))
                .bounds(leftPos + 9, topPos + lowerY(211), workButtonWidth(), 20).build());
        backupWork = addRenderableWidget(Button.builder(Component.empty(), ignored -> cycleWork(true))
                .bounds(leftPos + 19 + workButtonWidth(), topPos + lowerY(211), workButtonWidth(), 20).build());
        meals = addRenderableWidget(Button.builder(Component.empty(), ignored -> send(PlayerOutpostMenu.MEALS))
                .bounds(leftPos + imageWidth - 126, topPos + 5, 58, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("menu.changed_synergy.player_outpost.refresh"),
                        ignored -> send(PlayerOutpostMenu.REFRESH))
                .bounds(leftPos + imageWidth - 64, topPos + 5, 55, 18)
                .tooltip(Tooltip.create(Component.translatable("menu.changed_synergy.player_outpost.facilities_hint"))).build());
        addRenderableWidget(Button.builder(Component.translatable("menu.changed_synergy.player_outpost.meal_call"),
                        ignored -> send(PlayerOutpostMenu.MEAL_CALL))
                .bounds(leftPos + imageWidth - 64, topPos + 55, 55, 18)
                .tooltip(Tooltip.create(Component.translatable("menu.changed_synergy.player_outpost.meal_call_hint"))).build());

        Role[] roles = {Role.RESIDENT, Role.GUARD, Role.SUPPLY, Role.CREW,
                Role.HOLD, Role.RETURN, Role.RETREAT};
        for (int i = 0; i < roles.length; i++) {
            Role role = roles[i];
            int column = i % 4;
            int row = i / 4;
            memberOrders.add(addRenderableWidget(Button.builder(
                            Component.translatable("command.changed_synergy.outpost.role."
                                    + role.name().toLowerCase(java.util.Locale.ROOT)),
                            ignored -> memberAction(role.ordinal()))
                    .bounds(leftPos + 9 + column * columnWidth(), topPos + lowerY(245) + row * 22, columnWidth() - 4, 20).build()));
        }
        remove = addRenderableWidget(Button.builder(
                        Component.translatable("menu.changed_synergy.player_outpost.remove"),
                        ignored -> removeMember())
                .bounds(leftPos + 9 + 3 * columnWidth(), topPos + lowerY(267), columnWidth() - 4, 20).build());
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
                .bounds(leftPos + 9 + column * columnWidth(), topPos + lowerY(295), columnWidth() - 4, 20).build());
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
        return Math.max(0, (menu.members().size() - 1) / pageSize);
    }

    private int lowerY(int y) { return y - (PAGE_SIZE - pageSize) * 20; }
    private int columnWidth() { return (imageWidth - 16) / 4; }
    private int workButtonWidth() { return (imageWidth - 28) / 2; }

    private void refreshRows() {
        var roster = menu.members();
        page = Math.max(0, Math.min(page, maxPage()));
        if (selectedIndex >= roster.size()) selectedIndex = roster.size() - 1;
        previous.active = page > 0;
        next.active = page < maxPage();
        for (int row = 0; row < pageSize; row++) {
            int index = page * pageSize + row;
            Button button = rows.get(row);
            button.visible = index < roster.size();
            if (!button.visible) continue;
            MemberView member = roster.get(index);
            String name = font.plainSubstrByWidth(member.name(), 105);
            var role = Component.translatable("command.changed_synergy.outpost.role."
                    + member.role().name().toLowerCase(java.util.Locale.ROOT));
            if (member.role() == Role.SUPPLY) {
                role.append(Component.literal(" · ")).append(workName(member.work(), member.supplyType()));
            }
            button.setMessage(Component.literal((index == selectedIndex ? "▶ " : "  ")
                    + (member.loaded() ? "● " : "○ ") + name + "  ·  ")
                    .append(member.role() == Role.CREW ? role.append(Component.literal(" · ")).append(crewName(member.crewJob())) : role));
            button.setTooltip(Tooltip.create(Component.translatable(
                    "menu.changed_synergy.player_outpost.preference",
                    Component.translatable("menu.changed_synergy.player_outpost.supply."
                            + member.supplyType().name().toLowerCase(java.util.Locale.ROOT)))
                    .append(Component.literal("\n")).append(Component.translatable(
                            "menu.changed_synergy.player_outpost.status." + member.workStatus()))
                    .append(Component.literal("\n")).append(Component.translatable(member.loaded()
                                    ? "menu.changed_synergy.player_outpost.member_current_location"
                                    : "menu.changed_synergy.player_outpost.member_location",
                            member.name(), member.dimension(), member.position().toShortString()))));
        }
        MemberView selected = selectedIndex >= 0 && selectedIndex < roster.size() ? roster.get(selectedIndex) : null;
        boolean crew = selected != null && selected.role() == Role.CREW;
        mainWork.active = selected != null && (selected.role() == Role.SUPPLY || crew);
        backupWork.active = selected != null && selected.role() == Role.SUPPLY;
        mainWork.setMessage(Component.translatable(crew ? "menu.changed_synergy.player_outpost.crew_job"
                        : "menu.changed_synergy.player_outpost.main_work",
                selected == null ? Component.literal("—") : crew ? crewName(selected.crewJob()) : workName(selected.work(), selected.supplyType())));
        backupWork.setMessage(Component.translatable("menu.changed_synergy.player_outpost.backup_work",
                selected == null || selected.backupWork() == null
                        ? Component.translatable("menu.changed_synergy.player_outpost.no_backup") : workName(selected.backupWork(), selected.supplyType())));
        mainWork.setTooltip(Tooltip.create(mainWork.getMessage().copy().append("\n").append(Component.translatable(crew ? "menu.changed_synergy.player_outpost.crew_hint"
                : "menu.changed_synergy.player_outpost.work_hint"))));
        backupWork.visible = !crew;
        mainWork.setWidth(crew ? imageWidth - 18 : workButtonWidth());
        backupWork.setTooltip(Tooltip.create(backupWork.getMessage().copy().append("\n").append(
                Component.translatable("menu.changed_synergy.player_outpost.backup_hint"))));
        for (Button order : memberOrders) order.active = selected != null;
        remove.active = selected != null;
        meals.setMessage(Component.translatable("menu.changed_synergy.player_outpost.meals",
                Component.translatable(menu.meals() ? "options.on" : "options.off")));
        meals.setTooltip(Tooltip.create(Component.translatable("menu.changed_synergy.player_outpost.meals_hint")));
    }

    private static Component workName(Work work, PlayerOutpostSupplyType supplyType) {
        if (work == Work.GATHER) return Component.translatable("menu.changed_synergy.player_outpost.gather_target",
                Component.translatable("menu.changed_synergy.player_outpost.supply."
                        + supplyType.name().toLowerCase(java.util.Locale.ROOT)));
        return Component.translatable("menu.changed_synergy.player_outpost.work." + work.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static Component crewName(CrewJob job) {
        return Component.translatable("menu.changed_synergy.player_outpost.crew." + job.name().toLowerCase(java.util.Locale.ROOT));
    }

    private void cycleWork(boolean backup) {
        var roster = menu.members();
        if (selectedIndex < 0 || selectedIndex >= roster.size()) return;
        MemberView member = roster.get(selectedIndex);
        if (member.role() == Role.CREW && !backup) {
            send(PlayerOutpostMenu.CREW_JOB_BASE + (member.crewJob().ordinal() + 1) % CrewJob.values().length);
            return;
        }
        if (member.role() != Role.SUPPLY) return;
        if (!backup) {
            int next = (member.work().ordinal() + 1) % Work.values().length;
            if (Work.values()[next] == Work.COMPOST) next = 0;
            send(PlayerOutpostMenu.MAIN_WORK_BASE + next);
            return;
        }
        int next = member.backupWork() == null ? 0 : member.backupWork().ordinal() + 1;
        while (next < Work.values().length
                && (Work.values()[next] == member.work() || Work.values()[next] == Work.COMPOST)) next++;
        send(PlayerOutpostMenu.BACKUP_WORK_BASE + next);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshRows();
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
        graphics.fill(leftPos + 7, topPos + 74, leftPos + imageWidth - 7,
                topPos + lowerY(177), 0xFF8B8B8B);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int dark = 0x303030;
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), imageWidth - 143), 9, 8, dark, false);
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
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(
                        "menu.changed_synergy.player_outpost.facilities", menu.facilities()).getString(), imageWidth - 78),
                9, 61, dark, false);
        graphics.drawString(font, Component.literal((page + 1) + "/" + (maxPage() + 1)),
                imageWidth / 2 - 12, lowerY(184), dark, false);
        if (selectedIndex >= 0 && selectedIndex < menu.members().size()) {
            MemberView member = menu.members().get(selectedIndex);
            String name = font.plainSubstrByWidth(member.name(), 85);
            Component location = Component.translatable(
                    member.loaded() ? "menu.changed_synergy.player_outpost.member_current_location"
                            : "menu.changed_synergy.player_outpost.member_location", name,
                    member.dimension(), member.position().toShortString());
            graphics.drawString(font, font.plainSubstrByWidth(location.getString(), imageWidth - 18),
                    9, lowerY(199), dark, false);
            graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(
                            "menu.changed_synergy.player_outpost.member_status", Component.translatable(
                                    "menu.changed_synergy.player_outpost.status." + member.workStatus())).getString(), imageWidth - 18),
                    9, lowerY(234), dark, false);
        } else {
            graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(
                    "menu.changed_synergy.player_outpost.empty").getString(), imageWidth - 18),
                    9, lowerY(199), dark, false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
