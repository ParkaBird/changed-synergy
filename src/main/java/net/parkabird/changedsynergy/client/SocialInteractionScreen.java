package net.parkabird.changedsynergy.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.client.gui.AbstractRadialScreen;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.ltxprogrammer.changed.util.Color3;
import net.ltxprogrammer.changed.util.SingleRunnable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.HunterFaction;
import net.parkabird.changedsynergy.ai.InvoluntaryTransfurNegotiation.Approach;
import net.parkabird.changedsynergy.world.inventory.SocialInteractionMenu.CommunityStorage;
import net.parkabird.changedsynergy.world.inventory.SocialInteractionMenu;

/** Social wheel using Changed's native radial geometry and faction colors. */
public final class SocialInteractionScreen
        extends AbstractRadialScreen<SocialInteractionMenu> {
    private static final List<Action> RELATIONSHIP_ACTIONS = List.of(
            emote("social_talk", "pause"),
            emote("social_pat", "heart"),
            synergyIcon("social_gift", "orange_gray.png", 16),
            emote("social_play", "idea"),
            changedIcon("social_follow",
                    "textures/gui/tamed_dl_interactions/cycle_follow.png", 16));
    private static final Action VOLUNTARY_TRANSFUR = new Action(
            "social_voluntary_transfur",
            Changed.modResource(
                    "textures/abilities/switch_transfur_mode_replication.png"),
            32);
    private static final Action NEGOTIATION_ENTRY =
            emote("social_negotiation", "pause");
    private static final List<Action> BONDED_ACTIONS = List.of(
            emote("social_talk", "pause"),
            emote("social_pat", "heart"),
            synergyIcon("social_gift", "orange_gray.png", 16),
            emote("social_play", "idea"),
            emote("social_rest", "sleepy"));
    private static final List<Action> NEGOTIATION_ACTIONS = List.of(
            emote("negotiation_reason", "pause"),
            emote("negotiation_empathy", "heart"),
            emote("negotiation_apology", "confused"),
            emote("negotiation_bargain", "idea"),
            emote("negotiation_insist", "deny"));
    private static final Action NEGOTIATION_FOOD_BRIBE = synergyIcon(
            "negotiation_food_bribe", "orange_gray.png", 16);
    private static final Action NEGOTIATION_FOOD_BRIBE_FELINE = synergyIcon(
            "negotiation_food_bribe", "cooked_fish_gray.png", 16);
    private final List<Action> actions;
    @Nullable
    private final LivingEntity absorptionPreviewEntity;
    private boolean manualGoodbyeSent;
    private boolean voluntaryConfirmationArmed;

    public SocialInteractionScreen(
            SocialInteractionMenu menu,
            Inventory inventory,
            Component title) {
        super(menu, inventory, title, colors(menu, inventory).background(),
                colors(menu, inventory).foreground(), center(menu, inventory));
        this.absorptionPreviewEntity = createAbsorptionPreview(menu);
        if (menu.isNegotiationMode()) {
            List<Action> entries = new ArrayList<>(NEGOTIATION_ACTIONS);
            if (menu.isFoodBribeAvailable()) {
                entries.add(menu.isFelineFoodBribe()
                        ? NEGOTIATION_FOOD_BRIBE_FELINE
                        : NEGOTIATION_FOOD_BRIBE);
            }
            actions = List.copyOf(entries);
        } else if (menu.isBondedMode()) {
            actions = BONDED_ACTIONS;
        } else {
            List<Action> entries = new ArrayList<>();
            if (menu.hasTrustedRelationship()) {
                entries.addAll(RELATIONSHIP_ACTIONS);
            }
            if (menu.isVoluntaryBondAvailable()) {
                entries.add(VOLUNTARY_TRANSFUR);
            }
            if (menu.isNegotiationAvailable()) {
                entries.add(NEGOTIATION_ENTRY);
            }
            actions = List.copyOf(entries);
        }
    }

    private static LivingEntity center(
            SocialInteractionMenu menu,
            Inventory inventory) {
        return menu.getCreature() != null ? menu.getCreature() : inventory.player;
    }

    @Nullable
    private static LivingEntity createAbsorptionPreview(
            SocialInteractionMenu menu) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!menu.isVirtualAbsorptionNegotiation()
                || !menu.isWhiteKnightSplitNegotiation()
                || minecraft.level == null) {
            return null;
        }
        var type = ForgeRegistries.ENTITY_TYPES.getValue(
                ResourceLocation.fromNamespaceAndPath(
                        "changed", "white_latex_knight"));
        Entity created = type == null ? null : type.create(minecraft.level);
        if (!(created instanceof ChangedEntity knight)) {
            return null;
        }
        CompoundTag appearance = menu.getNegotiationAppearance();
        if (!appearance.isEmpty()) {
            knight.getBasicPlayerInfo().load(appearance);
        }
        return knight;
    }

    @Nullable
    public LivingEntity getAbsorptionPreviewEntity() {
        return absorptionPreviewEntity;
    }

    private static Action emote(String command, String name) {
        return synergyIcon(command, "emote/" + name + "_gray.png", 32);
    }

    private static Action synergyIcon(
            String command,
            String path,
            int size) {
        return new Action(
                command,
                ResourceLocation.fromNamespaceAndPath(
                        ChangedSynergyMod.MOD_ID,
                        "textures/gui/radial/icons/" + path),
                size);
    }

    private static Action changedIcon(
            String command,
            String path,
            int size) {
        return new Action(command, Changed.modResource(path), size);
    }

    private static ColorScheme colors(
            SocialInteractionMenu menu,
            Inventory inventory) {
        ChangedEntity creature = menu.getCreature();
        if (menu.isVirtualAbsorptionNegotiation()) {
            var variant = ProcessTransfur.getPlayerTransfurVariant(
                    inventory.player);
            if (variant != null) {
                return readableColors(AbstractRadialScreen.getColors(variant));
            }
        }
        if (creature == null || creature.getSelfVariant() == null) {
            return new ColorScheme(Color3.GRAY, Color3.WHITE).setForegroundToBright();
        }
        var pair = creature.getSelfVariant().getColors();
        return readableColors(new ColorScheme(
                pair.getFirst(), pair.getSecond()).setForegroundToBright());
    }

    private static ColorScheme readableColors(ColorScheme source) {
        Color3 background = source.background();
        Color3 foreground = source.foreground();
        if (background.brightness() < 0.44F
                && foreground.brightness() < 0.64F) {
            float amount = (0.68F - foreground.brightness())
                    / Math.max(0.001F, 1.0F - foreground.brightness());
            foreground = foreground.lerp(
                    Math.min(0.84F, Math.max(0.0F, amount)),
                    Color3.WHITE).clamp();
        }
        return new ColorScheme(background, foreground);
    }

    @Override
    public int getCount() {
        return actions.size();
    }

    @Override
    public Optional<Integer> getSectionAt(int mouseX, int mouseY) {
        int wheelOffset = Math.round(
                RadialWheelAnimations.horizontalOffset(this));
        return super.getSectionAt(mouseX - wheelOffset, mouseY);
    }

    @Nullable
    @Override
    public List<Component> tooltipsFor(int section) {
        if (section < 0 || section >= actions.size()) {
            return null;
        }
        Action action = actions.get(section);
        if ("social_follow".equals(action.command())) {
            if (!menu.canFriendFollow()) {
                return List.of(
                        Component.translatable(
                                "menu.changed_synergy.social.social_follow"),
                        Component.translatable(
                                "menu.changed_synergy.social.follow_locked"));
            }
            return List.of(
                    Component.translatable("menu.changed_synergy.social.social_follow"),
                    Component.translatable(menu.isFollowing()
                            ? "menu.changed_synergy.social.wait"
                            : "menu.changed_synergy.social.walk"));
        }
        if ("social_gift".equals(action.command())) {
            return List.of(
                    Component.translatable("menu.changed_synergy.social.social_gift"),
                    Component.translatable("menu.changed_synergy.social.social_gift_hint"));
        }
        if ("social_voluntary_transfur".equals(action.command())) {
            return List.of(
                    Component.translatable(
                            "menu.changed_synergy.social.social_voluntary_transfur"),
                    Component.translatable(voluntaryConfirmationArmed
                            ? "menu.changed_synergy.social.social_voluntary_transfur_confirm"
                            : "menu.changed_synergy.social.social_voluntary_transfur_hint"));
        }
        if ("social_negotiation".equals(action.command())) {
            return List.of(
                    Component.translatable(
                            "menu.changed_synergy.social.social_negotiation"),
                    Component.translatable(
                            "menu.changed_synergy.social.social_negotiation_hint"));
        }
        if (menu.isNegotiationMode()) {
            String key = "menu.changed_synergy.social." + action.command();
            Approach approach = Approach.fromCommand(action.command())
                    .orElse(null);
            if (approach != null
                    && menu.isNegotiationApproachUsed(approach)) {
                return List.of(
                        Component.translatable(key),
                        Component.translatable(
                                "menu.changed_synergy.negotiation.option_used"));
            }
            String hintKey = approach == Approach.FOOD_BRIBE
                    && menu.isFelineFoodBribe()
                            ? key + ".hint.feline" : key + ".hint";
            return List.of(Component.translatable(key),
                    Component.translatable(hintKey));
        }
        return List.of(Component.translatable(
                "menu.changed_synergy.social." + action.command()));
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
        float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderRelationshipCards(graphics);
        if (menu.isBondedMode() && !menu.isNegotiationMode()) {
            renderSwitchHint(
                    graphics,
                    Component.translatable(
                            "menu.changed_synergy.wheel.switch_to_functions"));
        }
    }

    private void renderRelationshipCards(GuiGraphics graphics) {
        ChangedEntity creature = menu.getCreature();
        float alpha = RadialWheelAnimations.overallAlpha(this);
        if (alpha <= 0.01F) {
            return;
        }

        if (menu.isNegotiationMode()) {
            renderNegotiationCards(
                    graphics,
                    creature == null
                            ? menu.getNegotiationSpeakerName()
                            : creature.getDisplayName(),
                    alpha);
            return;
        }
        if (creature == null) {
            return;
        }

        List<Component> relationship = new ArrayList<>();
        relationship.add(menu.isBondedMode()
                ? Component.translatable(
                        "menu.changed_synergy.social.info.bond",
                        menu.getFamiliarity())
                : Component.translatable(
                        "menu.changed_synergy.social.info.relationship",
                        Component.translatable(menu.getTier().translationKey()),
                        menu.getFamiliarity()));
        relationship.add(Component.translatable(
                "menu.changed_synergy.social.info.trait",
                Component.translatable(
                        "personality.changed_synergy."
                                + menu.getDominantTrait().dialogueKey())));
        if (!menu.isBondedMode()) {
            relationship.add(Component.translatable(
                    "menu.changed_synergy.social.info.intent",
                    Component.translatable(
                            "intent.changed_synergy."
                                    + menu.getHumanIntent().dialogueKey())));
        }

        List<Component> currentState = new ArrayList<>();
        currentState.add(Component.translatable(
                "menu.changed_synergy.social.info.health",
                formatHealth(creature.getHealth()),
                formatHealth(creature.getMaxHealth())));
        currentState.add(Component.translatable(
                menu.isLifeEnabled()
                        ? "menu.changed_synergy.social.info.life"
                        : "menu.changed_synergy.social.info.life_paused",
                Component.translatable(
                        "routine.changed_synergy.role." + menu.getRole().id()),
                Component.translatable(
                        "routine.changed_synergy.state." + menu.getRoutine().id())));
        for (int index = 0; index < 3; index++) {
            currentState.add(Component.translatable(
                    "menu.changed_synergy.social.info.role_stat",
                    Component.translatable(
                            "routine.changed_synergy.stat."
                                    + menu.getRole().id() + "." + index),
                    menu.getRoleStat(index)));
        }

        List<Component> connections = new ArrayList<>();
        connections.add(Component.translatable(
                "menu.changed_synergy.social.info.faction",
                Component.translatable(
                        menu.getReputationGroupTranslationKey()),
                Component.translatable(menu.getFactionStanding().translationKey()),
                menu.getReputation()));
        connections.add(Component.translatable(
                "menu.changed_synergy.social.info.memories",
                menu.getEncounters(), menu.getPats(), menu.getPlays()));
        if (menu.hasCommunity()) {
            connections.add(Component.translatable(
                    "menu.changed_synergy.social.info.community",
                    menu.getStoredItems(),
                    menu.getFoodDelivered(),
                    menu.getMaterialsDelivered(),
                    Component.translatable(storageKey(menu.getCommunityStorage()))));
        }

        List<InfoCard> cards = List.of(
                new InfoCard(creature.getDisplayName(), relationship),
                new InfoCard(Component.translatable(
                        "menu.changed_synergy.social.info.section_now"),
                        currentState),
                new InfoCard(Component.translatable(
                        "menu.changed_synergy.social.info.section_connections"),
                        connections));

        int x = 14;
        int wheelLeft = Math.round(width * 0.5F
                + RadialWheelAnimations.horizontalOffset(this) - 128.0F);
        int availableWidth = Math.max(104, wheelLeft - x - 12);
        int desiredWidth = isEnglishLanguage()
                ? width >= 800 ? 284 : width >= 620 ? 244 : 190
                : width >= 800 ? 252 : width >= 620 ? 218 : 178;
        int cardWidth = Math.min(desiredWidth, availableWidth);
        int textWidth = Math.max(86, cardWidth - 18);
        List<PreparedCard> prepared = cards.stream()
                .map(card -> prepareCard(card, textWidth))
                .toList();
        int gap = 6;
        int totalHeight = prepared.stream()
                .mapToInt(PreparedCard::height)
                .sum() + gap * (prepared.size() - 1);
        float panelScale = Math.min(
                1.0F,
                Math.max(0.01F, (height - 20.0F) / totalHeight));
        int scaledHeight = Math.round(totalHeight * panelScale);
        int y = Math.max(10, (height - scaledHeight) / 2);
        int accent = panelAccent();
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(panelScale, panelScale, 1.0F);
        int cardY = 0;
        for (PreparedCard card : prepared) {
            renderInfoCard(graphics, card, 0, cardY, cardWidth, accent, alpha);
            cardY += card.height() + gap;
        }
        graphics.pose().popPose();
    }

    private void renderNegotiationCards(
            GuiGraphics graphics,
            Component speakerName,
            float alpha) {
        List<Component> circumstances = new ArrayList<>();
        circumstances.add(Component.translatable(
                "menu.changed_synergy.negotiation.info.method",
                Component.translatable(
                        menu.getNegotiationMode().displayTranslationKey(
                                menu.getNegotiationReason()))));
        circumstances.add(Component.translatable(
                "menu.changed_synergy.negotiation.info.reason",
                Component.translatable(
                        menu.getNegotiationReason().translationKey())));
        circumstances.add(Component.translatable(
                "menu.changed_synergy.negotiation.info.trait",
                Component.translatable(
                        "personality.changed_synergy."
                                + menu.getDominantTrait().dialogueKey())));

        List<Component> progress = new ArrayList<>();
        progress.add(Component.translatable(
                "menu.changed_synergy.negotiation.info.progress",
                menu.getNegotiationPercent()));
        progress.add(Component.translatable(
                "menu.changed_synergy.negotiation.info.difficulty",
                Component.translatable(menu.getNegotiationDifficultyKey())));
        progress.add(Component.translatable(
                "menu.changed_synergy.negotiation.info.remaining",
                menu.getNegotiationRemainingApproaches()));

        List<Component> guidance = new ArrayList<>();
        guidance.add(Component.translatable(
                "menu.changed_synergy.negotiation.advice."
                        + menu.getNegotiationReason().name()
                                .toLowerCase(Locale.ROOT)));
        guidance.add(Component.translatable(
                "menu.changed_synergy.negotiation.info.single_use_hint"));
        Approach next = menu.getSuggestedNegotiationApproach();
        guidance.add(Component.translatable(
                "menu.changed_synergy.negotiation.info.next",
                Component.translatable(
                        "menu.changed_synergy.social.negotiation_"
                                + next.name().toLowerCase(Locale.ROOT))));

        List<InfoCard> cards = List.of(
                new InfoCard(speakerName, circumstances),
                new InfoCard(Component.translatable(
                        "menu.changed_synergy.negotiation.info.section_progress"),
                        progress),
                new InfoCard(Component.translatable(
                        "menu.changed_synergy.negotiation.info.section_advice"),
                        guidance));
        renderCards(graphics, cards, alpha);
    }

    private void renderCards(
            GuiGraphics graphics,
            List<InfoCard> cards,
            float alpha) {
        int x = 14;
        int wheelLeft = Math.round(width * 0.5F
                + RadialWheelAnimations.horizontalOffset(this) - 128.0F);
        int availableWidth = Math.max(104, wheelLeft - x - 12);
        int desiredWidth = isEnglishLanguage()
                ? width >= 800 ? 284 : width >= 620 ? 244 : 190
                : width >= 800 ? 252 : width >= 620 ? 218 : 178;
        int cardWidth = Math.min(desiredWidth, availableWidth);
        int textWidth = Math.max(86, cardWidth - 18);
        List<PreparedCard> prepared = cards.stream()
                .map(card -> prepareCard(card, textWidth))
                .toList();
        int gap = 6;
        int totalHeight = prepared.stream()
                .mapToInt(PreparedCard::height)
                .sum() + gap * (prepared.size() - 1);
        float panelScale = Math.min(
                1.0F,
                Math.max(0.01F, (height - 20.0F) / totalHeight));
        int scaledHeight = Math.round(totalHeight * panelScale);
        int y = Math.max(10, (height - scaledHeight) / 2);
        int accent = panelAccent();
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(panelScale, panelScale, 1.0F);
        int cardY = 0;
        for (PreparedCard card : prepared) {
            renderInfoCard(graphics, card, 0, cardY, cardWidth, accent, alpha);
            cardY += card.height() + gap;
        }
        graphics.pose().popPose();
    }

    private PreparedCard prepareCard(InfoCard card, int textWidth) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component line : card.lines()) {
            lines.addAll(font.split(line, textWidth));
        }
        return new PreparedCard(card.title(), List.copyOf(lines),
                27 + lines.size() * 9);
    }

    private void renderInfoCard(
            GuiGraphics graphics,
            PreparedCard card,
            int x,
            int y,
            int cardWidth,
            int accent,
            float alpha) {
        int backgroundAlpha = Math.round(0xB8 * alpha);
        int borderAlpha = Math.round(0xDC * alpha);
        int titleAlpha = Math.round(0xF4 * alpha);
        int textAlpha = Math.round(0xE8 * alpha);
        graphics.fill(x, y, x + cardWidth, y + card.height(),
                withAlpha(0x10151C, backgroundAlpha));
        graphics.fill(x, y, x + 3, y + card.height(),
                withAlpha(accent, borderAlpha));
        graphics.fill(x + 3, y, x + cardWidth, y + 1,
                withAlpha(accent, Math.round(0x86 * alpha)));
        graphics.drawString(font, card.title(), x + 9, y + 7,
                withAlpha(accent, titleAlpha), true);
        graphics.fill(x + 9, y + 19, x + cardWidth - 8, y + 20,
                withAlpha(accent, Math.round(0x36 * alpha)));
        int lineY = y + 23;
        for (FormattedCharSequence line : card.lines()) {
            graphics.drawString(font, line, x + 9, lineY,
                    withAlpha(0xF0F2F5, textAlpha), false);
            lineY += 9;
        }
    }

    private int panelAccent() {
        if (menu.isOrganic()) {
            return 0xC65A4A;
        }
        return switch (menu.getFaction()) {
            case WHITE -> 0xF2F5FF;
            case DARK -> 0x9B59D0;
            case AQUATIC -> 0x4FD6E8;
            case LIGHT -> 0xE5A84B;
        };
    }

    private static String storageKey(CommunityStorage storage) {
        return "menu.changed_synergy.social.info.storage."
                + storage.name().toLowerCase(Locale.ROOT);
    }

    private boolean isEnglishLanguage() {
        String language = minecraft.getLanguageManager().getSelected();
        return language != null
                && language.toLowerCase(Locale.ROOT).startsWith("en_");
    }

    private static String formatHealth(float health) {
        return String.format(Locale.ROOT, "%.1f", health);
    }

    private static int withAlpha(int rgb, int alpha) {
        return Math.max(0, Math.min(255, alpha)) << 24 | rgb & 0x00FFFFFF;
    }

    private void renderSwitchHint(GuiGraphics graphics, Component hint) {
        int textWidth = font.width(hint);
        int x = (width - textWidth) / 2;
        int y = height - 18;
        float alpha = RadialWheelAnimations.overallAlpha(this);
        int backgroundAlpha = Math.round(0x76 * alpha);
        int textAlpha = Math.round(0xE8 * alpha);
        graphics.fill(x - 5, y - 3, x + textWidth + 5, y + 11,
                backgroundAlpha << 24);
        graphics.drawString(font, hint, x, y,
                textAlpha << 24 | 0x00FFFFFF, true);
    }

    @Override
    public void renderSectionBackground(
            GuiGraphics graphics,
            int section,
            double x,
            double y,
            float partialTick,
            int mouseX,
            int mouseY,
            float red,
            float green,
            float blue) {
        boolean selected = getSectionAt(mouseX, mouseY)
                .filter(index -> index == section)
                .isPresent();
        String style = menu.isOrganic() ? "organic" : "goo";
        ResourceLocation texture = Changed.modResource(
                "textures/gui/radial/" + style
                        + (selected ? "_selected/" : "/") + section + ".png");
        boolean used = isUsedNegotiationSection(section);
        graphics.setColor(red, green, blue,
                RadialWheelAnimations.layerAlpha(this)
                        * (used ? 0.42F : 1.0F));
        graphics.blit(texture, (int)x - 32 + leftPos, (int)y - 32 + topPos,
                0.0F, 0.0F, 64, 64, 64, 64);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public void renderSectionForeground(
            GuiGraphics graphics,
            int section,
            double x,
            double y,
            float partialTick,
            int mouseX,
            int mouseY,
            float red,
            float green,
            float blue,
            float alpha) {
        if (section < 0 || section >= actions.size()) {
            return;
        }
        Action action = actions.get(section);
        ResourceLocation icon = action.texture();
        int centerX = (int)x + leftPos;
        int centerY = (int)y + topPos;
        float iconAlpha = isUsedNegotiationSection(section)
                ? alpha * 0.3F : alpha;
        graphics.setColor(0.0F, 0.0F, 0.0F, 0.45F * iconAlpha);
        blitScaledIcon(graphics, icon, centerX + 2, centerY + 2,
                34, action.textureSize());
        graphics.setColor(red, green, blue, iconAlpha);
        blitScaledIcon(graphics, icon, centerX, centerY,
                32, action.textureSize());
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Draws only the icon's native pixels, then scales the pose. Sampling a
     * 38x38 region from a 16x16 or 32x32 texture makes Minecraft wrap the UVs,
     * which is what produced the repeated, garbled-looking wheel icons.
     */
    private static void blitScaledIcon(
            GuiGraphics graphics,
            ResourceLocation icon,
            int centerX,
            int centerY,
            int targetSize,
            int sourceSize) {
        float scale = (float)targetSize / (float)sourceSize;
        graphics.pose().pushPose();
        graphics.pose().translate(
                centerX - targetSize * 0.5F,
                centerY - targetSize * 0.5F,
                0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.blit(icon, 0, 0, 0.0F, 0.0F,
                sourceSize, sourceSize, sourceSize, sourceSize);
        graphics.pose().popPose();
    }

    @Override
    public boolean handleClicked(int section, SingleRunnable close) {
        if (section < 0 || section >= actions.size()) {
            return false;
        }
        if (isUsedNegotiationSection(section)) {
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(
                            SoundEvents.UI_BUTTON_CLICK, 0.65F));
            return false;
        }
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        String command = actions.get(section).command();
        if ("social_voluntary_transfur".equals(command)) {
            voluntaryConfirmationArmed = true;
        }
        if ("social_follow".equals(command)) {
            if (!menu.canFriendFollow()) {
                CompoundTag payload = new CompoundTag();
                payload.putString("command", command);
                menu.setDirty(payload);
                return true;
            }
            menu.toggleLocalFollowing();
        }
        CompoundTag payload = new CompoundTag();
        payload.putString("command", command);
        if ("social_negotiation".equals(command)) {
            RadialWheelAnimations.requestNegotiationSwitch(
                    this, () -> menu.setDirty(payload));
            return false;
        }
        menu.setDirty(payload);
        // Each argument is one conversational turn. Close normally after the
        // choice; the player can reopen the negotiation for the next turn.
        return true;
    }

    private boolean isUsedNegotiationSection(int section) {
        if (!menu.isNegotiationMode()
                || section < 0 || section >= actions.size()) {
            return false;
        }
        return Approach.fromCommand(actions.get(section).command())
                .map(menu::isNegotiationApproachUsed)
                .orElse(false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean manualClose = keyCode == 256
                || minecraft.options.keyInventory.matches(keyCode, scanCode);
        if (manualClose
                && !menu.isBondedMode()
                && !menu.isNegotiationMode()
                && menu.hasTrustedRelationship()
                && !manualGoodbyeSent) {
            manualGoodbyeSent = true;
            CompoundTag payload = new CompoundTag();
            payload.putString("command", "social_goodbye");
            menu.setDirty(payload);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private record Action(
            String command,
            ResourceLocation texture,
            int textureSize) {
    }

    private record InfoCard(Component title, List<Component> lines) {
    }

    private record PreparedCard(
            Component title,
            List<FormattedCharSequence> lines,
            int height) {
    }
}
