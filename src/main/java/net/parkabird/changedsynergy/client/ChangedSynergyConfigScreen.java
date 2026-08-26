package net.parkabird.changedsynergy.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;
import net.parkabird.changedsynergy.ChangedSynergyClientConfig;
import net.parkabird.changedsynergy.ChangedSynergyClientConfig.DialogueDisplayMode;
import net.parkabird.changedsynergy.ChangedSynergyConfig;

/** Native Forge config screen used by Forge's mod list and menu integrations. */
public final class ChangedSynergyConfigScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final int TOP = 70;

    private final Screen parent;
    private final List<Category> gameplayCategories;
    private final List<Category> clientCategories;
    private final List<RowLabel> rowLabels = new ArrayList<>();
    private Section section = Section.GAMEPLAY;
    private int pageIndex;
    private int contentLeft;
    private int contentWidth;
    private List<Page> pages = List.of();
    private Component status = CommonComponents.EMPTY;

    public ChangedSynergyConfigScreen(Screen parent) {
        super(Component.translatable("config.changed_synergy.title"));
        this.parent = parent;
        this.gameplayCategories = createGameplayCategories();
        this.clientCategories = createClientCategories();
    }

    @Override
    protected void init() {
        buildWidgets();
    }

    private void buildWidgets() {
        rowLabels.clear();
        contentWidth = Math.min(520, width - 24);
        contentLeft = (width - contentWidth) / 2;

        int tabWidth = Math.min(150, (contentWidth - 6) / 2);
        Button gameplay = addRenderableWidget(Button.builder(
                        Component.translatable("config.changed_synergy.section.gameplay"),
                        button -> switchSection(Section.GAMEPLAY))
                .bounds(width / 2 - tabWidth - 3, 24, tabWidth, 20)
                .build());
        gameplay.active = section != Section.GAMEPLAY;
        Button client = addRenderableWidget(Button.builder(
                        Component.translatable("config.changed_synergy.section.client"),
                        button -> switchSection(Section.CLIENT))
                .bounds(width / 2 + 3, 24, tabWidth, 20)
                .build());
        client.active = section != Section.CLIENT;

        int rowsPerPage = Math.max(2, (height - TOP - 42) / ROW_HEIGHT);
        pages = buildPages(section == Section.GAMEPLAY
                ? gameplayCategories : clientCategories, rowsPerPage);
        pageIndex = Math.max(0, Math.min(pageIndex, pages.size() - 1));
        Page page = pages.get(pageIndex);

        Button previous = addRenderableWidget(Button.builder(
                        Component.literal("<"), button -> changePage(-1))
                .bounds(contentLeft, 48, 20, 20)
                .tooltip(Tooltip.create(Component.translatable(
                        "config.changed_synergy.previous_page")))
                .build());
        previous.active = pageIndex > 0;
        Button next = addRenderableWidget(Button.builder(
                        Component.literal(">"), button -> changePage(1))
                .bounds(contentLeft + contentWidth - 20, 48, 20, 20)
                .tooltip(Tooltip.create(Component.translatable(
                        "config.changed_synergy.next_page")))
                .build());
        next.active = pageIndex + 1 < pages.size();

        for (int index = 0; index < page.entries().size(); index++) {
            ConfigEntry entry = page.entries().get(index);
            int rowY = TOP + index * ROW_HEIGHT;
            rowLabels.add(new RowLabel(entry, rowY));
            addEntryWidget(entry, rowY);
        }

        int bottomY = height - 27;
        int buttonWidth = Math.min(120, (contentWidth - 12) / 3);
        addRenderableWidget(Button.builder(
                        Component.translatable("config.changed_synergy.defaults"),
                        button -> resetPage(page))
                .bounds(width / 2 - buttonWidth * 3 / 2 - 6,
                        bottomY, buttonWidth, 20)
                .build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL,
                        button -> onClose())
                .bounds(width / 2 - buttonWidth / 2,
                        bottomY, buttonWidth, 20)
                .build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE,
                        button -> saveAndClose())
                .bounds(width / 2 + buttonWidth / 2 + 6,
                        bottomY, buttonWidth, 20)
                .build());
    }

    private void addEntryWidget(ConfigEntry entry, int rowY) {
        int infoX = contentLeft + contentWidth - 20;
        int controlRight = infoX - 4;
        int controlWidth = Math.min(150, contentWidth / 2);
        int controlX = controlRight - controlWidth;

        Component tooltip = entry.tooltip();
        addRenderableWidget(Button.builder(Component.literal("?"), button -> {
                })
                .bounds(infoX, rowY + 2, 20, 20)
                .tooltip(Tooltip.create(tooltip))
                .build());

        if (entry instanceof BooleanEntry booleanEntry) {
            Button toggle = addRenderableWidget(Button.builder(
                            booleanMessage(booleanEntry.value), button -> {
                                booleanEntry.value = !booleanEntry.value;
                                button.setMessage(booleanMessage(booleanEntry.value));
                            })
                    .bounds(controlX, rowY + 2, controlWidth, 20)
                    .build());
            toggle.setTooltip(Tooltip.create(tooltip));
            return;
        }

        if (entry instanceof DisplayModeEntry modeEntry) {
            Button mode = addRenderableWidget(Button.builder(
                            modeEntry.message(), button -> {
                                modeEntry.next();
                                button.setMessage(modeEntry.message());
                            })
                    .bounds(controlX, rowY + 2, controlWidth, 20)
                    .build());
            mode.setTooltip(Tooltip.create(tooltip));
            return;
        }

        NumericEntry numeric = (NumericEntry) entry;
        int adjustWidth = 20;
        EditBox field = new EditBox(font,
                controlX + adjustWidth + 2, rowY + 2,
                controlWidth - adjustWidth * 2 - 4, 20,
                entry.label());
        field.setValue(numeric.text);
        field.setFilter(ChangedSynergyConfigScreen::isPotentialNumber);
        field.setResponder(value -> {
            numeric.text = value;
            field.setTextColor(numeric.valid() ? 0xE0E0E0 : 0xFF5555);
        });
        field.setTooltip(Tooltip.create(tooltip));
        addRenderableWidget(field);

        addRenderableWidget(Button.builder(Component.literal("-"),
                        button -> {
                            numeric.adjust(-1);
                            field.setValue(numeric.text);
                        })
                .bounds(controlX, rowY + 2, adjustWidth, 20)
                .tooltip(Tooltip.create(tooltip))
                .build());
        addRenderableWidget(Button.builder(Component.literal("+"),
                        button -> {
                            numeric.adjust(1);
                            field.setValue(numeric.text);
                        })
                .bounds(controlRight - adjustWidth, rowY + 2,
                        adjustWidth, 20)
                .tooltip(Tooltip.create(tooltip))
                .build());
    }

    private void switchSection(Section next) {
        if (section == next) {
            return;
        }
        section = next;
        pageIndex = 0;
        status = CommonComponents.EMPTY;
        refreshWidgets();
    }

    private void changePage(int delta) {
        int next = pageIndex + delta;
        if (next < 0 || next >= pages.size()) {
            return;
        }
        pageIndex = next;
        status = CommonComponents.EMPTY;
        refreshWidgets();
    }

    private void resetPage(Page page) {
        page.entries().forEach(ConfigEntry::reset);
        status = Component.translatable("config.changed_synergy.defaults_applied");
        refreshWidgets();
    }

    private void refreshWidgets() {
        clearWidgets();
        setFocused(null);
        buildWidgets();
    }

    private void saveAndClose() {
        List<ConfigEntry> entries = allEntries();
        if (entries.stream().anyMatch(entry -> !entry.valid())) {
            status = Component.translatable("config.changed_synergy.invalid_value");
            return;
        }
        entries.forEach(ConfigEntry::save);
        ChangedSynergyConfig.SPEC.save();
        ChangedSynergyClientConfig.SPEC.save();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private List<ConfigEntry> allEntries() {
        List<ConfigEntry> entries = new ArrayList<>();
        gameplayCategories.forEach(category -> entries.addAll(category.entries()));
        clientCategories.forEach(category -> entries.addAll(category.entries()));
        return entries;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        if (!pages.isEmpty()) {
            Page page = pages.get(pageIndex);
            Component pageTitle = page.partCount() > 1
                    ? Component.translatable("config.changed_synergy.page_part",
                            page.title(), page.partIndex(), page.partCount())
                    : page.title();
            graphics.drawCenteredString(font, pageTitle,
                    width / 2, 54, 0xD8D8D8);
        }
        for (RowLabel row : rowLabels) {
            int background = row.entry().valid() ? 0x66000000 : 0x66880000;
            graphics.fill(contentLeft, row.y() + 1,
                    contentLeft + contentWidth, row.y() + 23, background);
            graphics.drawString(font, row.entry().label(),
                    contentLeft + 6, row.y() + 8, 0xFFFFFF, false);
        }
        if (!status.equals(CommonComponents.EMPTY)) {
            graphics.drawCenteredString(font, status,
                    width / 2, height - 39, 0xFFD36A);
        } else if (section == Section.GAMEPLAY) {
            graphics.drawCenteredString(font,
                    Component.translatable("config.changed_synergy.server_note"),
                    width / 2, height - 39, 0xA0A0A0);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static Component booleanMessage(boolean value) {
        return value ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
    }

    private static boolean isPotentialNumber(String value) {
        return value.isEmpty() || value.matches("[0-9]*\\.?[0-9]*");
    }

    private static List<Page> buildPages(List<Category> categories,
            int rowsPerPage) {
        List<Page> result = new ArrayList<>();
        for (Category category : categories) {
            int partCount = Math.max(1,
                    (category.entries().size() + rowsPerPage - 1) / rowsPerPage);
            for (int part = 0; part < partCount; part++) {
                int from = part * rowsPerPage;
                int to = Math.min(category.entries().size(), from + rowsPerPage);
                result.add(new Page(category.title(),
                        category.entries().subList(from, to), part + 1, partCount));
            }
        }
        return result;
    }

    private static List<Category> createGameplayCategories() {
        ChangedSynergyConfig.Common config = ChangedSynergyConfig.COMMON;
        return List.of(
                category("relationships",
                        bool("respect_pacified", config.respectPacifiedLatexes, true),
                        bool("pacify_pets", config.pacifyTamedCompanions, true),
                        integer("pet_regeneration", config.tamedCompanionRegeneration, 0, 0, 5),
                        decimal("awareness_range", config.npcAwarenessRange, 32.0, 8.0, 96.0, 1.0),
                        bool("polite_interaction", config.politeHumanInteraction, true),
                        decimal("polite_range", config.politeApproachRange, 12.0, 4.0, 32.0, 1.0),
                        decimal("polite_speed", config.politeApproachSpeed, 0.42, 0.05, 1.5, 0.05),
                        integer("polite_wait", config.politeResponseSeconds, 14, 4, 60),
                        integer("polite_attempts", config.politeUnansweredLimit, 2, 1, 6)),
                category("behaviour",
                        decimal("visual_range", config.visualAcquisitionRange, 18.0, 6.0, 48.0, 1.0),
                        decimal("pursuit_range", config.maximumPursuitRange, 28.0, 8.0, 64.0, 1.0),
                        integer("lose_sight", config.huntLoseSightSeconds, 2, 1, 30),
                        integer("search_time", config.huntSearchSeconds, 8, 2, 120),
                        decimal("alert_radius", config.huntAlertRadius, 12.0, 0.0, 96.0, 1.0),
                        decimal("search_speed", config.huntSearchSpeed, 0.45, 0.05, 3.0, 0.05),
                        decimal("gunshot_radius", config.firearmGunshotRadius, 48.0, 0.0, 128.0, 1.0),
                        bool("firearm_evasion", config.firearmEvasion, true),
                        decimal("grab_chance", config.hostileGrabAttemptChance, 0.65, 0.0, 1.0, 0.05),
                        decimal("organic_grab_chance", config.organicHostileGrabAttemptChance, 0.85, 0.0, 1.0, 0.05)),
                category("dialogue",
                        decimal("dialogue_range", config.npcDialogueRange, 32.0, 4.0, 96.0, 1.0),
                        integer("dialogue_cooldown", config.npcDialogueCooldownSeconds, 12, 2, 120),
                        decimal("dialogue_chance", config.npcDialogueChance, 0.72, 0.0, 1.0, 0.05),
                        decimal("personality_chance", config.personalityDialogueChance, 0.68, 0.0, 1.0, 0.05),
                        bool("translator", config.npcDialogueUsesTranslator, true),
                        integer("telepathy_unlock", config.telepathyUnlockTransfurs, 3, 1, 20)));
    }

    private static List<Category> createClientCategories() {
        ChangedSynergyClientConfig.Client config = ChangedSynergyClientConfig.CLIENT;
        return List.of(
                category("legacy_visuals",
                        bool("legacy_screen", config.legacyTransfurScreenEffect, true),
                        bool("legacy_skin", config.legacyTransfurSkinEffect, true),
                        decimal("legacy_opacity", config.legacyTransfurScreenOpacity, 1.0, 0.0, 1.0, 0.05)),
                category("suit_visuals",
                        bool("suit_vignette", config.friendlySuitVignette, true),
                        decimal("suit_opacity", config.friendlySuitVignetteOpacity, 0.25, 0.0, 0.5, 0.05)),
                category("telepathy",
                        bool("danmaku", config.telepathicDanmaku, true),
                        displayMode(
                                "dialogue_display",
                                config.dialogueDisplayMode,
                                DialogueDisplayMode.AUTO),
                        bool("territory_hud", config.territoryHud, true)),
                category("hints",
                        bool("mechanic_hints", config.mechanicHints, true)),
                category("qte",
                        bool("qte_animations", config.qteAnimations, true),
                        bool("reduced_motion", config.reducedQteMotion, false)));
    }

    private static Category category(String key, ConfigEntry... entries) {
        return new Category(Component.translatable(
                "config.changed_synergy.category." + key), List.of(entries));
    }

    private static BooleanEntry bool(String key,
            ForgeConfigSpec.BooleanValue value, boolean defaultValue) {
        return new BooleanEntry(key, value, defaultValue);
    }

    private static IntegerEntry integer(String key,
            ForgeConfigSpec.IntValue value, int defaultValue, int min, int max) {
        return new IntegerEntry(key, value, defaultValue, min, max);
    }

    private static DisplayModeEntry displayMode(
            String key,
            ForgeConfigSpec.EnumValue<DialogueDisplayMode> value,
            DialogueDisplayMode defaultValue) {
        return new DisplayModeEntry(key, value, defaultValue);
    }

    private static DoubleEntry decimal(String key,
            ForgeConfigSpec.DoubleValue value, double defaultValue,
            double min, double max, double step) {
        return new DoubleEntry(key, value, defaultValue, min, max, step);
    }

    private enum Section {
        GAMEPLAY,
        CLIENT
    }

    private record Category(Component title, List<ConfigEntry> entries) {
    }

    private record Page(Component title, List<ConfigEntry> entries,
            int partIndex, int partCount) {
    }

    private record RowLabel(ConfigEntry entry, int y) {
    }

    private abstract static class ConfigEntry {
        protected final String key;

        private ConfigEntry(String key) {
            this.key = key;
        }

        private Component label() {
            return Component.translatable("config.changed_synergy.option." + key);
        }

        protected abstract Component tooltip();

        protected abstract boolean valid();

        protected abstract void save();

        protected abstract void reset();
    }

    private static final class BooleanEntry extends ConfigEntry {
        private final ForgeConfigSpec.BooleanValue config;
        private final boolean defaultValue;
        private boolean value;

        private BooleanEntry(String key, ForgeConfigSpec.BooleanValue config,
                boolean defaultValue) {
            super(key);
            this.config = config;
            this.defaultValue = defaultValue;
            this.value = config.get();
        }

        @Override
        protected Component tooltip() {
            return Component.translatable("config.changed_synergy.option."
                    + key + ".description");
        }

        @Override
        protected boolean valid() {
            return true;
        }

        @Override
        protected void save() {
            config.set(value);
        }

        @Override
        protected void reset() {
            value = defaultValue;
        }
    }

    private static final class DisplayModeEntry extends ConfigEntry {
        private final ForgeConfigSpec.EnumValue<DialogueDisplayMode> config;
        private final DialogueDisplayMode defaultValue;
        private DialogueDisplayMode value;

        private DisplayModeEntry(
                String key,
                ForgeConfigSpec.EnumValue<DialogueDisplayMode> config,
                DialogueDisplayMode defaultValue) {
            super(key);
            this.config = config;
            this.defaultValue = defaultValue;
            this.value = config.get();
        }

        private void next() {
            DialogueDisplayMode[] values = DialogueDisplayMode.values();
            value = values[(value.ordinal() + 1) % values.length];
        }

        private Component message() {
            return Component.translatable(
                    "config.changed_synergy.dialogue_display."
                            + value.name().toLowerCase(Locale.ROOT));
        }

        @Override
        protected Component tooltip() {
            return Component.translatable(
                    "config.changed_synergy.option." + key + ".description");
        }

        @Override
        protected boolean valid() {
            return true;
        }

        @Override
        protected void save() {
            config.set(value);
        }

        @Override
        protected void reset() {
            value = defaultValue;
        }
    }

    private abstract static class NumericEntry extends ConfigEntry {
        protected final double min;
        protected final double max;
        protected final double step;
        protected String text;

        private NumericEntry(String key, double min, double max, double step,
                String initial) {
            super(key);
            this.min = min;
            this.max = max;
            this.step = step;
            this.text = initial;
        }

        protected final double parsed() {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException exception) {
                return Double.NaN;
            }
        }

        @Override
        protected boolean valid() {
            double value = parsed();
            return Double.isFinite(value) && value >= min && value <= max
                    && additionallyValid(value);
        }

        protected boolean additionallyValid(double value) {
            return true;
        }

        protected void adjust(int direction) {
            double current = valid() ? parsed() : min;
            setFromNumber(Math.max(min, Math.min(max,
                    current + step * direction)));
        }

        protected abstract void setFromNumber(double value);

        @Override
        protected Component tooltip() {
            return Component.translatable("config.changed_synergy.numeric_tooltip",
                    Component.translatable("config.changed_synergy.option."
                            + key + ".description"),
                    format(min), format(max));
        }

        protected static String format(double value) {
            if (value == Math.rint(value)) {
                return Long.toString(Math.round(value));
            }
            return String.format(Locale.ROOT, "%.2f", value)
                    .replaceAll("0+$", "").replaceAll("\\.$", "");
        }
    }

    private static final class IntegerEntry extends NumericEntry {
        private final ForgeConfigSpec.IntValue config;
        private final int defaultValue;

        private IntegerEntry(String key, ForgeConfigSpec.IntValue config,
                int defaultValue, int min, int max) {
            super(key, min, max, 1.0, Integer.toString(config.get()));
            this.config = config;
            this.defaultValue = defaultValue;
        }

        @Override
        protected boolean additionallyValid(double value) {
            return value == Math.rint(value);
        }

        @Override
        protected void setFromNumber(double value) {
            text = Integer.toString((int) Math.round(value));
        }

        @Override
        protected void save() {
            config.set((int) Math.round(parsed()));
        }

        @Override
        protected void reset() {
            text = Integer.toString(defaultValue);
        }
    }

    private static final class DoubleEntry extends NumericEntry {
        private final ForgeConfigSpec.DoubleValue config;
        private final double defaultValue;

        private DoubleEntry(String key, ForgeConfigSpec.DoubleValue config,
                double defaultValue, double min, double max, double step) {
            super(key, min, max, step, format(config.get()));
            this.config = config;
            this.defaultValue = defaultValue;
        }

        @Override
        protected void setFromNumber(double value) {
            text = format(value);
        }

        @Override
        protected void save() {
            config.set(parsed());
        }

        @Override
        protected void reset() {
            text = format(defaultValue);
        }
    }
}
