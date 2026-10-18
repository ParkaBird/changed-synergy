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
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

/** Native Forge config screen used by Forge's mod list and menu integrations. */
public final class ChangedSynergyConfigScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final int TOP = 70;

    private final Screen parent;
    private List<Category> gameplayCategories;
    private final List<Category> clientCategories;
    private final List<Category> worldCategories;
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
        this.worldCategories = createWorldCategories();
    }

    @Override
    protected void init() {
        buildWidgets();
    }

    private void buildWidgets() {
        rowLabels.clear();
        contentWidth = Math.min(520, width - 24);
        contentLeft = (width - contentWidth) / 2;

        int tabWidth = Math.min(160, (contentWidth - 12) / 3);
        Button gameplay = addRenderableWidget(Button.builder(
                        Component.translatable("config.changed_synergy.section.gameplay"),
                        button -> switchSection(Section.GAMEPLAY))
                .bounds(width / 2 - tabWidth * 3 / 2 - 6, 24, tabWidth, 20)
                .build());
        gameplay.active = section != Section.GAMEPLAY;
        Button world = addRenderableWidget(Button.builder(
                        Component.translatable("config.changed_synergy.section.world"), button -> switchSection(Section.WORLD))
                .bounds(width / 2 - tabWidth / 2, 24, tabWidth, 20).build());
        world.active = section != Section.WORLD;
        Button client = addRenderableWidget(Button.builder(
                        Component.translatable("config.changed_synergy.section.client"),
                        button -> switchSection(Section.CLIENT))
                .bounds(width / 2 + tabWidth / 2 + 6, 24, tabWidth, 20)
                .build());
        client.active = section != Section.CLIENT;

        // Reserve the live diagnostics and server note above the action buttons.
        int rowsPerPage = Math.max(2, (height - TOP - 88) / ROW_HEIGHT);
        pages = section != Section.CLIENT && isRemoteServer() && !ServerConfigClient.received()
                ? List.of(new Page(Component.translatable(
                        "config.changed_synergy.remote_title"),
                        List.of(), 1, 1, false))
                : buildPages(switch (section) {
                    case GAMEPLAY -> gameplayCategories;
                    case WORLD -> worldCategories;
                    case CLIENT -> clientCategories;
                }, rowsPerPage);
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
        Button defaults = addRenderableWidget(Button.builder(
                        Component.translatable("config.changed_synergy.defaults"),
                        button -> resetPage(page))
                .bounds(width / 2 - buttonWidth * 3 / 2 - 6,
                        bottomY, buttonWidth, 20)
                .build());
        defaults.active = !readOnlySection();
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

    public void refreshServerValues() {
        if (isRemoteServer()) gameplayCategories = createGameplayCategories();
        worldCategories.forEach(category -> category.entries().forEach(entry -> ((RuleEntry)entry).refresh()));
        if (minecraft != null) refreshWidgets();
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

        if (entry instanceof RuleEntry rule) {
            if (rule.value instanceof Boolean enabled) {
                Button toggle = addRenderableWidget(Button.builder(booleanMessage(enabled), button -> {
                    rule.value = !(Boolean)rule.value;
                    rule.dirty = true;
                    button.setMessage(booleanMessage((Boolean)rule.value));
                }).bounds(controlX, rowY + 2, controlWidth, 20).tooltip(Tooltip.create(tooltip)).build());
                toggle.active = !readOnlySection();
            } else {
                EditBox field = new EditBox(font, controlX, rowY + 2, controlWidth, 20, rule.label());
                field.setValue(rule.value.toString());
                field.setFilter(ChangedSynergyConfigScreen::isPotentialNumber);
                field.setResponder(text -> { rule.value = text; rule.dirty = true; field.setTextColor(rule.valid() ? 0xE0E0E0 : 0xFF5555); });
                field.setTooltip(Tooltip.create(tooltip));
                field.setEditable(!readOnlySection());
                field.active = !readOnlySection();
                addRenderableWidget(field);
            }
            return;
        }

        if (entry instanceof BooleanEntry booleanEntry) {
            Button toggle = addRenderableWidget(Button.builder(
                            booleanMessage(booleanEntry.value), button -> {
                                booleanEntry.value = !booleanEntry.value;
                                button.setMessage(booleanMessage(booleanEntry.value));
                            })
                    .bounds(controlX, rowY + 2, controlWidth, 20)
                    .build());
            toggle.active = !readOnlySection();
            toggle.setTooltip(Tooltip.create(tooltip));
            return;
        }

        if (entry instanceof EnumEntry<?> modeEntry) {
            Button mode = addRenderableWidget(Button.builder(
                            modeEntry.message(), button -> {
                                modeEntry.next();
                                button.setMessage(modeEntry.message());
                            })
                    .bounds(controlX, rowY + 2, controlWidth, 20)
                    .build());
            mode.active = !readOnlySection();
            mode.setTooltip(Tooltip.create(tooltip));
            return;
        }

        if (entry instanceof FormListEntry formList) {
            EditBox field = new EditBox(font, controlX, rowY + 2, controlWidth, 20, entry.label());
            field.setMaxLength(32767);
            field.setValue(formList.text);
            field.setResponder(value -> {
                formList.text = value;
                field.setTextColor(formList.valid() ? 0xE0E0E0 : 0xFF5555);
            });
            field.setTooltip(Tooltip.create(tooltip));
            field.setEditable(!readOnlySection());
            field.active = !readOnlySection();
            addRenderableWidget(field);
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
        field.setEditable(!readOnlySection());
        field.active = !readOnlySection();
        addRenderableWidget(field);

        Button decrease = addRenderableWidget(Button.builder(Component.literal("-"),
                        button -> {
                            numeric.adjust(-1);
                            field.setValue(numeric.text);
                        })
                .bounds(controlX, rowY + 2, adjustWidth, 20)
                .tooltip(Tooltip.create(tooltip))
                .build());
        decrease.active = !readOnlySection();
        Button increase = addRenderableWidget(Button.builder(Component.literal("+"),
                        button -> {
                            numeric.adjust(1);
                            field.setValue(numeric.text);
                        })
                .bounds(controlRight - adjustWidth, rowY + 2,
                        adjustWidth, 20)
                .tooltip(Tooltip.create(tooltip))
                .build());
        increase.active = !readOnlySection();
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
        boolean remote = isRemoteServer();
        List<ConfigEntry> entries = new ArrayList<>(remote
                ? clientCategories.stream().flatMap(category -> category.entries().stream()).toList()
                : allEntries());
        if (canEditWorld()) worldCategories.forEach(category -> category.entries().stream()
                .filter(entry -> ((RuleEntry)entry).dirty).forEach(entries::add));
        if (entries.stream().anyMatch(entry -> !entry.valid())) {
            status = Component.translatable("config.changed_synergy.invalid_value");
            return;
        }
        entries.forEach(ConfigEntry::save);
        if (!remote) {
            ChangedSynergyConfig.SPEC.save();
        }
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
            graphics.drawString(font, font.plainSubstrByWidth(row.entry().label().getString(),
                            contentWidth - Math.min(150, contentWidth / 2) - 38),
                    contentLeft + 6, row.y() + 8, 0xFFFFFF, false);
        }
        if (remoteGameplay() && !ServerConfigClient.received()) {
            graphics.drawCenteredString(font,
                    Component.translatable("config.changed_synergy.remote_line_1"),
                    width / 2, TOP + 28, 0xFFFFFF);
            graphics.drawCenteredString(font,
                    Component.translatable("config.changed_synergy.remote_line_2"),
                    width / 2, TOP + 48, 0xA0A0A0);
        }
        if (showsPerformanceDiagnostics()) {
            graphics.drawCenteredString(font,
                    PerformanceDiagnosticsClient.primarySummary(),
                    width / 2, height - 72,
                    PerformanceDiagnosticsClient.color());
            graphics.drawCenteredString(font,
                    PerformanceDiagnosticsClient.secondarySummary(),
                    width / 2, height - 61,
                    PerformanceDiagnosticsClient.color());
            graphics.drawCenteredString(font,
                    PerformanceDiagnosticsClient.tertiarySummary(),
                    width / 2, height - 50,
                    PerformanceDiagnosticsClient.color());
        }
        if (!status.equals(CommonComponents.EMPTY)) {
            graphics.drawCenteredString(font, status,
                    width / 2, height - 39, 0xFFD36A);
        } else if (section == Section.WORLD) {
            graphics.drawCenteredString(font, Component.translatable(canEditWorld()
                    ? "config.changed_synergy.world_note" : "config.changed_synergy.world_read_only"),
                    width / 2, height - 39, 0xA0A0A0);
        } else if (section == Section.GAMEPLAY) {
            graphics.drawCenteredString(font,
                    Component.translatable(remoteGameplay() ? "config.changed_synergy.server_read_only" : "config.changed_synergy.server_note"),
                    width / 2, height - 39, 0xA0A0A0);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static Component booleanMessage(boolean value) {
        return value ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
    }

    private static boolean isPotentialNumber(String value) {
        return value.matches("-?[0-9]*\\.?[0-9]*");
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
                        category.entries().subList(from, to), part + 1, partCount,
                        category.performance()));
            }
        }
        return result;
    }

    private static List<Category> createGameplayCategories() {
        ChangedSynergyConfig.Common config = ChangedSynergyConfig.COMMON;
        return List.of(
                category("performance",
                        bool("performance_diagnostics", config.performanceDiagnostics),
                        bool("stagger_background_ai", config.staggerBackgroundAi),
                        integer("background_scan_interval", config.backgroundScanInterval),
                        bool("distant_ai_throttling", config.distantAiThrottling),
                        decimal("distant_ai_range", config.distantAiRange, 4.0),
                        integer("distant_ai_multiplier", config.distantAiIntervalMultiplier),
                        bool("adaptive_ai_budget", config.adaptiveAiBudget),
                        decimal("latex_ai_budget", config.latexAiBudgetMs, 1.0)),
                category("ai_systems",
                        bool("community_ai", config.communityAi),
                        bool("companion_work_ai", config.companionWorkAi),
                        bool("ambient_social_ai", config.ambientSocialAi),
                        bool("enhanced_hunt_ai", config.enhancedHuntAi)),
                category("relationship_growth",
                        bool("balanced_relationships", config.balancedRelationships),
                        integer("stranger_affection_limit", config.strangerAffectionLimit),
                        integer("care_affection_allowance", config.careAffectionAllowance),
                        integer("care_affection_cooldown_seconds", config.careAffectionCooldownSeconds),
                        integer("gift_cooldown_seconds", config.giftCooldownSeconds),
                        bool("bond_tokens_required", config.bondTokensRequired),
                        new FormListEntry("bond_tokens", config.bondTokens, ";")),
                category("relationships",
                        integer("pet_regeneration", config.tamedCompanionRegeneration),
                        bool("ordinary_transfur_reversal", config.ordinaryTransfurReversal),
                        bool("organic_sleep_reversal", config.organicSleepReversal),
                        bool("bond_protective_release_requests", config.bondProtectiveReleaseRequests),
                        bool("bonded_emergency_rescue", config.bondedEmergencyRescue),
                        bool("aquatic_friend_drowning_rescue", config.aquaticFriendDrowningRescue),
                        bool("bonded_kin_confinement", config.bondedKinConfinement),
                        integer("bonded_kin_kill_base_limit", config.bondedKinKillBaseLimit),
                        bool("changed_vanilla_relationship_animal_protection", config.changedVanillaRelationshipAnimalProtection),
                        bool("companion_monster_assist_only", config.companionMonsterAssistOnly),
                        bool("self_defense_penalty_protection", config.selfDefensePenaltyProtection),
                        bool("allow_multiple_bonds", config.allowMultipleBonds)),
                category("factions",
                        bool("faction_pursuit", config.factionPursuit),
                        bool("faction_pursuit_overworld_only", config.factionPursuitOverworldOnly),
                        bool("independent_faction_reputation", config.independentFactionReputation),
                        integer("post_transfur_truce", config.postTransfurTruceSeconds),
                        bool("non_rival_combat_protection", config.nonRivalCombatProtection),
                        bool("peaceful_village_relations", config.peacefulVillageRelations)),
                category("social_behavior",
                        decimal("awareness_range", config.npcAwarenessRange, 1.0),
                        bool("pat_pacification", config.patPacification),
                        bool("playful_friend_transfur", config.playfulFriendTransfur),
                        bool("polite_interaction", config.politeHumanInteraction),
                        decimal("polite_range", config.politeApproachRange, 1.0),
                        decimal("polite_speed", config.politeApproachSpeed, 0.05),
                        integer("polite_wait", config.politeResponseSeconds),
                        integer("polite_attempts", config.politeUnansweredLimit)),
                category("player_roleplay",
                        bool("player_role_speech",config.playerRoleSpeech),
                        decimal("player_role_speech_cooldown",config.playerRoleSpeechCooldown,0.5),
                        bool("instinct_confinement",config.instinctConfinement),
                        integer("instinct_confinement_seconds",config.instinctConfinementSeconds)),
                category("player_outposts",
                        bool("player_outposts", config.playerOutposts),
                        integer("player_outpost_residents", config.playerOutpostResidents),
                        integer("player_outpost_crew", config.playerOutpostCrew),
                        bool("player_outpost_crew_gathering", config.playerOutpostCrewGathering),
                        new FormListEntry("player_outpost_crew_blocked_ores", config.playerOutpostCrewBlockedOres),
                        bool("player_outpost_work_while_owner_offline", config.playerOutpostWorkWhileOwnerOffline),
                        bool("player_outpost_guard_pvp", config.playerOutpostGuardPvp)),
                category("outpost_work",
                        bool("player_outpost_supply_work", config.playerOutpostSupplyWork),
                        bool("player_outpost_harvesting", config.playerOutpostHarvesting),
                        bool("player_outpost_hauling", config.playerOutpostHauling),
                        new FormListEntry("player_outpost_hauling_blocked_items", config.playerOutpostHaulingBlockedItems),
                        bool("player_outpost_hunting", config.playerOutpostHunting),
                        bool("player_outpost_farming", config.playerOutpostFarming),
                        bool("player_outpost_cooking", config.playerOutpostCooking),
                        bool("player_outpost_composting", config.playerOutpostComposting),
                        integer("player_outpost_work_scan_interval", config.playerOutpostWorkScanInterval),
                        integer("player_outpost_active_workers", config.playerOutpostActiveWorkers)),
                category("outpost_rest",
                        bool("player_outpost_rest", config.playerOutpostRest),
                        bool("player_outpost_idle_rest", config.playerOutpostIdleRest),
                        bool("player_outpost_meals", config.playerOutpostMeals)),
                category("communities",
                        integer("outpost_spacing", config.settlementMinimumSpacing),
                        integer("player_wild_outpost_spacing", config.playerWildOutpostMinimumSpacing),
                        bool("creature_outposts", config.creatureOutposts),
                        bool("changed_structure_outposts", config.useChangedStructureOutposts),
                        bool("latex_bee_hive_outposts", config.latexBeeHiveOutposts),
                        integer("structure_outpost_search_radius", config.structureOutpostSearchRadiusChunks)),
                category("trading",
                        bool("provisioner_trade", config.provisionerTrading),
                        decimal("provisioner_trade_reserve", config.provisionerTradeReserveMultiplier, 0.25)),
                category("ecology",
                        bool("pure_white_wolf_adaptation", config.pureWhiteWolfAdaptation)),
                category("origins",
                        bool("modded_origin_species", config.includeModdedOriginSpecies),
                        bool("human_origin_instinct", config.allowHumanOriginInstinct),
                        new FormListEntry("disabled_origin_forms", config.disabledOriginForms)),
                category("behaviour",
                        decimal("visual_range", config.visualAcquisitionRange, 1.0),
                        decimal("pursuit_range", config.maximumPursuitRange, 1.0),
                        integer("lose_sight", config.huntLoseSightSeconds),
                        integer("search_time", config.huntSearchSeconds),
                        decimal("alert_radius", config.huntAlertRadius, 1.0),
                        decimal("search_speed", config.huntSearchSpeed, 0.05),
                        decimal("gunshot_radius", config.firearmGunshotRadius, 1.0),
                        bool("firearm_evasion", config.firearmEvasion)),
                category("transfur",
                        decimal("grab_chance", config.hostileGrabAttemptChance, 0.05),
                        decimal("organic_grab_chance", config.organicHostileGrabAttemptChance, 0.05),
                        bool("mindless_mob_transfur", config.allowMindlessMobTransfur),
                        bool("secondary_transfur", config.allowSecondaryTransfur),
                        enumMode("creature_transfur_method",
                                config.creatureTransfurMethod,
                                "config.changed_synergy.transfur_method.")),
                category("takeover",
                        bool("takeover_enabled", config.takeoverEnabled),
                        bool("takeover_punitive", config.takeoverPunitive),
                        bool("takeover_competitive", config.takeoverCompetitive),
                        bool("takeover_exoskeleton", config.takeoverExoskeleton),
                        bool("exoskeleton_sleep", config.exoskeletonSleep),
                        bool("takeover_borrow", config.takeoverBorrow),
                        bool("takeover_escape", config.takeoverEscape),
                        bool("takeover_wake_prone", config.takeoverWakeProne),
                        bool("takeover_negotiation", config.takeoverNegotiation),
                        bool("takeover_hostile_finale", config.takeoverHostileFinale),
                        bool("takeover_oranges", config.takeoverOranges),
                        integer("takeover_seconds", config.takeoverSeconds),
                        integer("takeover_punitive_seconds", config.takeoverPunitiveSeconds),
                        integer("takeover_exoskeleton_seconds", config.takeoverExoskeletonSeconds)),
                category("dialogue",
                        decimal("dialogue_range", config.npcDialogueRange, 1.0),
                        integer("dialogue_cooldown", config.npcDialogueCooldownSeconds),
                        decimal("dialogue_chance", config.npcDialogueChance, 0.05),
                        decimal("personality_chance", config.personalityDialogueChance, 0.05)),
                category("understanding",
                        bool("translator", config.npcDialogueUsesTranslator),
                        bool("addon_animal_calls", config.addonAnimalCalls),
                        bool("telepathy_enabled", config.telepathyEnabled),
                        integer("telepathy_unlock", config.telepathyUnlockTransfurs)),
                category("changed_addon",
                        bool("respect_pacified", config.respectPacifiedLatexes),
                        bool("pacify_pets", config.pacifyTamedCompanions)));
    }

    private static List<Category> createClientCategories() {
        ChangedSynergyClientConfig.Client config = ChangedSynergyClientConfig.CLIENT;
        return List.of(
                category("legacy_visuals",
                        bool("legacy_screen", config.legacyTransfurScreenEffect),
                        bool("legacy_skin", config.legacyTransfurSkinEffect),
                        decimal("legacy_opacity", config.legacyTransfurScreenOpacity, 0.05)),
                category("pure_white_visuals",
                        bool("disable_pure_white_overlays", config.disablePureWhiteVisionOverlays)),
                category("suit_visuals",
                        bool("suit_vignette", config.friendlySuitVignette),
                        decimal("suit_opacity", config.friendlySuitVignetteOpacity, 0.05)),
                category("telepathy",
                        bool("danmaku", config.telepathicDanmaku),
                        decimal("popup_height", config.popupHeightOffset, 0.05),
                        decimal("danmaku_height", config.danmakuHeightOffset, 0.05),
                        displayMode(
                                "dialogue_display",
                                config.dialogueDisplayMode),
                        bool("territory_hud", config.territoryHud)),
                category("hints",
                        bool("mechanic_hints", config.mechanicHints),
                        bool("negotiation_next_step_hint", config.negotiationNextStepHint)),
                category("qte",
                        bool("qte_animations", config.qteAnimations),
                        bool("reduced_motion", config.reducedQteMotion),
                        bool("exoskeleton_hypnosis_visual", config.exoskeletonHypnosisVisual)));
    }

    private static List<Category> createWorldCategories() {
        var grouped = new java.util.LinkedHashMap<String, List<ConfigEntry>>();
        for (var setting : ChangedSynergyGameRules.settings()) {
            String name = setting.name();
            String category = name.contains("Native") ? "world_origins"
                    : name.contains("Qte") ? "world_qte"
                    : name.contains("Dialogue") || name.contains("Emotes") || name.contains("TerritoryDisplay") ? "world_presentation"
                    : name.contains("NpcAI") || name.contains("CreatureLife") ? "world_ai" : "world_social";
            grouped.computeIfAbsent(category, ignored -> new ArrayList<>()).add(new RuleEntry(setting));
        }
        return grouped.entrySet().stream().map(entry -> category(entry.getKey(), entry.getValue().toArray(ConfigEntry[]::new))).toList();
    }

    private static Category category(String key, ConfigEntry... entries) {
        return new Category(Component.translatable(
                "config.changed_synergy.category." + key), List.of(entries),
                "performance".equals(key));
    }

    private boolean showsPerformanceDiagnostics() {
        return section == Section.GAMEPLAY
                && !pages.isEmpty()
                && pages.get(pageIndex).performance();
    }

    private boolean isRemoteServer() {
        return minecraft != null && minecraft.getConnection() != null
                && minecraft.getSingleplayerServer() == null;
    }

    private boolean remoteGameplay() {
        return section == Section.GAMEPLAY && isRemoteServer();
    }

    private boolean canEditWorld() {
        return minecraft != null && minecraft.player != null && minecraft.getConnection() != null
                && minecraft.player.hasPermissions(2) && (!isRemoteServer() || ServerConfigClient.received());
    }

    private boolean readOnlySection() {
        return remoteGameplay() || section == Section.WORLD && !canEditWorld();
    }

    private static ForgeConfigSpec.ValueSpec spec(ForgeConfigSpec.ConfigValue<?> value) {
        ForgeConfigSpec owner = ChangedSynergyConfig.SPEC.getValues().get(value.getPath()) == value
                ? ChangedSynergyConfig.SPEC : ChangedSynergyClientConfig.SPEC;
        return owner.getSpec().get(value.getPath());
    }

    private static BooleanEntry bool(String key, ForgeConfigSpec.BooleanValue value) {
        return new BooleanEntry(key, value);
    }

    private static IntegerEntry integer(String key, ForgeConfigSpec.IntValue value) {
        return new IntegerEntry(key, value);
    }

    private static EnumEntry<DialogueDisplayMode> displayMode(String key,
            ForgeConfigSpec.EnumValue<DialogueDisplayMode> value) {
        return enumMode(key, value, "config.changed_synergy.dialogue_display.");
    }

    private static <E extends Enum<E>> EnumEntry<E> enumMode(String key,
            ForgeConfigSpec.EnumValue<E> value, String translationPrefix) {
        return new EnumEntry<>(key, value, translationPrefix);
    }

    private static DoubleEntry decimal(String key, ForgeConfigSpec.DoubleValue value, double step) {
        return new DoubleEntry(key, value, step);
    }

    private enum Section {
        GAMEPLAY,
        WORLD,
        CLIENT
    }

    private record Category(Component title, List<ConfigEntry> entries,
            boolean performance) {
    }

    private record Page(Component title, List<ConfigEntry> entries,
            int partIndex, int partCount, boolean performance) {
    }

    private record RowLabel(ConfigEntry entry, int y) {
    }

    private abstract static class ConfigEntry {
        protected final String key;

        private ConfigEntry(String key) {
            this.key = key;
        }

        protected Component label() {
            return Component.translatable("config.changed_synergy.option." + key);
        }

        protected abstract Component tooltip();

        protected abstract boolean valid();

        protected abstract void save();

        protected abstract void reset();
    }

    private static final class RuleEntry extends ConfigEntry {
        private final ChangedSynergyGameRules.Setting<?> rule;
        private Object value;
        private boolean dirty;
        private RuleEntry(ChangedSynergyGameRules.Setting<?> rule) {
            super(rule.name());
            this.rule = rule;
            this.value = ServerConfigClient.ruleValue(rule);
        }
        private void refresh() { if (!dirty) value = ServerConfigClient.ruleValue(rule); }
        @Override protected Component label() { return Component.translatable("gamerule." + key); }
        @Override protected Component tooltip() {
            return Component.translatable("gamerule." + key + ".description").copy()
                    .append(Component.literal("\n/gamerule " + key + " " + value + "\n"))
                    .append(Component.translatable("config.changed_synergy.world_default", rule.defaultValue()));
        }
        @Override protected boolean valid() {
            if (rule.defaultValue() instanceof Boolean) return value instanceof Boolean;
            try { int number = Integer.parseInt(value.toString()); return number >= -100 && number <= 100; }
            catch (NumberFormatException invalid) { return false; }
        }
        @Override protected void save() {
            var client = net.minecraft.client.Minecraft.getInstance();
            if (dirty && client.player != null && client.player.hasPermissions(2) && client.getConnection() != null)
                client.getConnection().sendCommand("gamerule " + key + " " + value);
        }
        @Override protected void reset() { value = rule.defaultValue(); dirty = true; }
    }

    private static final class FormListEntry extends ConfigEntry {
        private final ForgeConfigSpec.ConfigValue<List<? extends String>> config;
        private final String separator;
        private String text;

        private FormListEntry(String key, ForgeConfigSpec.ConfigValue<List<? extends String>> config) {
            this(key, config, ",");
        }

        private FormListEntry(String key, ForgeConfigSpec.ConfigValue<List<? extends String>> config, String separator) {
            super(key);
            this.config = config;
            this.separator = separator;
            this.text = String.join(separator + " ", ServerConfigClient.value(config));
        }

        private List<String> forms() {
            return text.isBlank() ? List.of()
                    : java.util.Arrays.stream(text.split(separator, -1)).map(String::trim).toList();
        }

        @Override protected Component tooltip() {
            return label().copy().append("\n").append(Component.translatable("config.changed_synergy.option." + key + ".description"));
        }
        @Override protected boolean valid() {
            return ChangedSynergyConfig.SPEC.getSpec().<ForgeConfigSpec.ValueSpec>get(config.getPath()).test(forms());
        }
        @Override protected void save() { config.set(forms()); }
        @Override protected void reset() { text = String.join(separator + " ", config.getDefault()); }
    }

    private static final class BooleanEntry extends ConfigEntry {
        private final ForgeConfigSpec.BooleanValue config;
        private boolean value;

        private BooleanEntry(String key, ForgeConfigSpec.BooleanValue config) {
            super(key);
            this.config = config;
            this.value = ServerConfigClient.value(config);
        }

        @Override
        protected Component tooltip() {
            return label().copy().append("\n").append(Component.translatable("config.changed_synergy.option."
                    + key + ".description"));
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
            value = config.getDefault();
        }
    }

    private static final class EnumEntry<E extends Enum<E>> extends ConfigEntry {
        private final ForgeConfigSpec.EnumValue<E> config;
        private final String translationPrefix;
        private E value;

        private EnumEntry(
                String key,
                ForgeConfigSpec.EnumValue<E> config,
                String translationPrefix) {
            super(key);
            this.config = config;
            this.translationPrefix = translationPrefix;
            this.value = ServerConfigClient.value(config);
        }

        private void next() {
            E[] values = value.getDeclaringClass().getEnumConstants();
            value = values[(value.ordinal() + 1) % values.length];
        }

        private Component message() {
            return Component.translatable(
                    translationPrefix + value.name().toLowerCase(Locale.ROOT));
        }

        @Override
        protected Component tooltip() {
            return label().copy().append("\n").append(Component.translatable(
                    "config.changed_synergy.option." + key + ".description"));
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
            value = config.getDefault();
        }
    }

    private abstract static class NumericEntry extends ConfigEntry {
        protected final double min;
        protected final double max;
        protected final double step;
        protected String text;

        private NumericEntry(String key, ForgeConfigSpec.ValueSpec definition, double step,
                String initial) {
            super(key);
            this.min = ((Number)definition.getRange().getMin()).doubleValue();
            this.max = ((Number)definition.getRange().getMax()).doubleValue();
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
                    java.math.BigDecimal.valueOf(current).add(java.math.BigDecimal.valueOf(step)
                            .multiply(java.math.BigDecimal.valueOf(direction))).doubleValue())));
        }

        protected abstract void setFromNumber(double value);

        @Override
        protected Component tooltip() {
            return label().copy().append("\n").append(Component.translatable("config.changed_synergy.numeric_tooltip",
                    Component.translatable("config.changed_synergy.option."
                            + key + ".description"),
                    format(min), format(max)));
        }

        protected static String format(double value) {
            return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
        }
    }

    private static final class IntegerEntry extends NumericEntry {
        private final ForgeConfigSpec.IntValue config;

        private IntegerEntry(String key, ForgeConfigSpec.IntValue config) {
            super(key, spec(config), 1.0, Integer.toString(ServerConfigClient.value(config)));
            this.config = config;
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
            text = Integer.toString(config.getDefault());
        }
    }

    private static final class DoubleEntry extends NumericEntry {
        private final ForgeConfigSpec.DoubleValue config;

        private DoubleEntry(String key, ForgeConfigSpec.DoubleValue config, double step) {
            super(key, spec(config), step, format(ServerConfigClient.value(config)));
            this.config = config;
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
            text = format(config.getDefault());
        }
    }
}
