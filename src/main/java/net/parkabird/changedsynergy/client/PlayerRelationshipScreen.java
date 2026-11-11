package net.parkabird.changedsynergy.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.client.gui.AbstractRadialScreen;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.ltxprogrammer.changed.util.Color3;
import net.ltxprogrammer.changed.util.SingleRunnable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.HunterFaction;
import net.parkabird.changedsynergy.ai.PlayerRelationshipSettings.Contact;
import net.parkabird.changedsynergy.client.RelationshipPanel.InfoCard;
import net.parkabird.changedsynergy.client.RelationshipPanel.PreparedCard;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.OpenAbsorptionNegotiationPacket;
import net.parkabird.changedsynergy.world.inventory.PlayerRelationshipMenu;

/** Player-side relationship hub, rendered with Changed's radial vocabulary. */
public final class PlayerRelationshipScreen
        extends AbstractRadialScreen<PlayerRelationshipMenu> {
    private static final Color3 INVENTORY_SHADOW = Color3.fromInt(0x8B8B8B);
    private static final Color3 INVENTORY_FACE = Color3.fromInt(0xC6C6C6);
    private static final ResourceLocation PLUS_ICON = ResourceLocation.fromNamespaceAndPath(
            ChangedSynergyMod.MOD_ID, "textures/gui/radial/icons/plus_gray.png");
    private static final List<Action> ACTIONS = List.of(
            new Action("outpost", ResourceLocation.fromNamespaceAndPath(
                    ChangedSynergyMod.MOD_ID,
                    "textures/gui/radial/icons/campfire_gray.png"), 16),
            changedIcon("formation",
                    "textures/gui/tamed_dl_interactions/cycle_follow.png", 16),
            changedIcon("group_follow",
                    "textures/gui/tamed_dl_interactions/cycle_target_type.png", 16),
            emote("activity", "idea"),
            emote("signal", "casual"),
            emote("help", "startled"),
            emote("mediate", "pause"),
            changedIcon("damage_filter",
                    "textures/gui/tamed_dl_interactions/cycle_attack_condition.png", 16));
    private static final Action ABSORPTION_NEGOTIATION =
            emote("absorption_negotiation", "confused");
    private int selectedContact;
    private int contactHeaderX = -1;
    private int contactHeaderY;
    private int contactHeaderWidth;
    private float contactHeaderScale = 1;

    private final long panelsOpenedAt = System.nanoTime();

    public PlayerRelationshipScreen(
            PlayerRelationshipMenu menu,
            Inventory inventory,
            Component title) {
        super(menu, inventory, title,
                colors(menu, inventory).background(),
                colors(menu, inventory).foreground(),
                PlayerSocialClient.visibleBody(inventory.player));
    }

    private static ColorScheme colors(
            PlayerRelationshipMenu menu,
            Inventory inventory) {
        if (!menu.isTransfurred()) {
            return new ColorScheme(INVENTORY_SHADOW, INVENTORY_FACE)
                    .setForegroundToBright();
        }
        return readableColors(ProcessTransfur.getPlayerTransfurVariant(
                inventory.player));
    }

    static ColorScheme readableColors(
            net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance<?> variant) {
        ColorScheme source = AbstractRadialScreen.getColors(variant)
                .setForegroundToBright();
        Color3 background = source.background();
        Color3 foreground = source.foreground();
        if (background.brightness() < 0.44F) {
            float targetBrightness = Math.max(
                    0.64F, background.brightness() + 0.40F);
            if (foreground.brightness() < targetBrightness) {
                float amount = (targetBrightness - foreground.brightness())
                        / Math.max(0.001F, 1.0F - foreground.brightness());
                foreground = foreground.lerp(
                        Math.min(0.82F, amount), Color3.WHITE).clamp();
            }
        }
        return new ColorScheme(background, foreground);
    }

    @Override
    public int getCount() {
        return baseActionCount()+(NativeRoleplayClient.active()?2:0);
    }

    private int baseActionCount() { return ACTIONS.size()-(menu.isTransfurred()?1:0); }

    @Override
    public Optional<Integer> getSectionAt(int mouseX, int mouseY) {
        int wheelOffset = Math.round(RadialWheelAnimations.horizontalOffset(this));
        return super.getSectionAt(mouseX - wheelOffset, mouseY);
    }

    @Nullable
    @Override
    public List<Component> tooltipsFor(int section) {
        if (section < 0 || section >= getCount()) {
            return null;
        }
        String key = "menu.changed_synergy.relationship.action."
                + (section == 0 ? menu.outpostAction() : actionFor(section).command());
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable(key));
        tooltip.add(Component.translatable(key + ".hint"));
        if (actionFor(section).command().equals("formation")) {
            tooltip.add(Component.translatable(menu.getFormation().translationKey()));
        } else if (actionFor(section).command().equals("damage_filter")) {
            tooltip.add(Component.translatable(menu.getDamageFilter().translationKey()));
        }
        return tooltip;
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderManagerCards(graphics, mouseX, mouseY);
        Component hint = menu.isTransfurred()?RadialWheelAnimations.scrollHint(this,"menu.changed_synergy.relationship.switch_to_abilities")
                :getCount()>8?Component.translatable("menu.changed_synergy.relationship.more_options"):Component.empty();
        if (!hint.getString().isEmpty()) renderSwitchHint(graphics, hint, height - 18);
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
        boolean selected = RadialWheelAnimations.isSectionSelected(this, section, mouseX, mouseY);
        String style = menu.usesOrganicStyle() ? "organic" : "goo";
        ResourceLocation texture = Changed.modResource(
                "textures/gui/radial/" + style
                        + (selected ? "_selected/" : "/") + section + ".png");
        graphics.setColor(red, green, blue,
                RadialWheelAnimations.layerAlpha(this));
        RadialWheelAnimations.drawFrame(this, graphics, texture, (int)x - 32 + leftPos, (int)y - 32 + topPos,
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
        if (section < 0 || section >= getCount()) {
            return;
        }
        Action action = actionFor(section);
        ResourceLocation icon = action.texture();
        if (action.command().equals("rally_boarding")) icon = CompanionSeatIcons.resolve(icon);
        int centerX = (int)x + leftPos;
        int centerY = (int)y + topPos;
        if (section == 0 && menu.outpostAction().equals("link_outpost")) {
            graphics.setColor(0.0F, 0.0F, 0.0F, 0.45F * alpha);
            blitScaledIcon(graphics, PLUS_ICON, centerX + 2, centerY + 2, 24, 16);
            graphics.setColor(red, green, blue, alpha);
            blitScaledIcon(graphics, PLUS_ICON, centerX, centerY, 22, 16);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            return;
        }
        graphics.setColor(0.0F, 0.0F, 0.0F, 0.45F * alpha);
        blitScaledIcon(graphics, icon, centerX + 2, centerY + 2,
                34, action.textureSize());
        graphics.setColor(red, green, blue, alpha);
        blitScaledIcon(graphics, icon, centerX, centerY,
                32, action.textureSize());
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        if (section == 0 && (menu.outpostAction().equals("create_outpost")
                || menu.outpostAction().equals("invite_outpost")))
            drawPlus(graphics, centerX + 12, centerY - 12, 10, alpha);
    }

    private static void drawPlus(GuiGraphics graphics, int x, int y, int size, float alpha) {
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        blitScaledIcon(graphics, PLUS_ICON, x, y, size, 16);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public boolean handleClicked(int section, SingleRunnable close) {
        if (section < 0 || section >= getCount()) {
            return false;
        }
        if(section>=baseActionCount()) {minecraft.setScreen(section==baseActionCount()?new NativeRoleplayScreen(this):new NativeEmoteScreen(this));return false;}
        playClick();
        if (section == 4 && menu.usesAbsorptionNegotiationSlot()) {
            Runnable open = () -> {
                close.run();
                ChangedSynergyNetwork.CHANNEL.sendToServer(
                        new OpenAbsorptionNegotiationPacket());
            };
            if (!RadialWheelAnimations.requestClose(this, open)) {
                open.run();
            }
            return false;
        }
        if (section == 0) {
            CompoundTag payload = new CompoundTag();
            payload.putString("command", "open_outpost");
            menu.setDirty(payload);
            return false;
        }
        CompoundTag payload = new CompoundTag();
        payload.putString("command", switch (actionFor(section).command()) {
            case "formation" -> "cycle_formation";
            case "group_follow" -> "toggle_group_follow";
            case "activity" -> "shared_activity";
            case "signal" -> "social_signal";
            case "help" -> "request_help";
            case "mediate" -> "mediate";
            case "damage_filter" -> "cycle_damage_filter";
            case "rally_boarding" -> "rally_boarding";
            default -> "";
        });
        Contact contact = currentContact();
        if (contact != null) {
            payload.putUUID("contact", contact.uuid());
        }
        menu.setDirty(payload);
        return false;
    }

    private Action actionFor(int section) {
        if(section>=baseActionCount())return emote(section==baseActionCount()?"role_name":"role_emotes",section==baseActionCount()?"idea":"heart");
        if(menu.canRallyBoarding() && section==2)return new Action("rally_boarding",
                CompanionSeatIcons.id(true),CompanionSeatIcons.SIZE);
        int index=menu.isTransfurred()&&section>=6?section+1:section;
        return section == 4 && menu.usesAbsorptionNegotiationSlot()
                ? ABSORPTION_NEGOTIATION : ACTIONS.get(index);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int contactButton = contactButtonAt(mouseX, mouseY);
        if (button == 0 && contactButton >= 0) {
                Contact contact = currentContact();
                if (contact != null) {
                    playClick();
                    switch (contactButton) {
                        case 0 -> cycleContact(-1);
                        case 1 -> cycleContact(1);
                        case 2 -> {
                            CompoundTag payload = new CompoundTag();
                            payload.putString("command", "cancel_relationship");
                            payload.putUUID("contact", contact.uuid());
                            menu.setDirty(payload);
                        }
                        default -> { }
                    }
                }
                return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int contactButtonAt(double mouseX, double mouseY) {
        if (contactHeaderX < 0 || mouseY < contactHeaderY + 3 * contactHeaderScale
                || mouseY >= contactHeaderY + 18 * contactHeaderScale) return -1;
        double offset = (mouseX - contactHeaderX - contactHeaderWidth) / contactHeaderScale + 56;
        return offset >= 0 && offset < 52 && offset % 18 < 16 ? (int)offset / 18 : -1;
    }

    private void cycleContact(int direction) {
        List<Contact> contacts = menu.getContacts();
        if (contacts.isEmpty()) {
            return;
        }
        selectedContact = Math.floorMod(selectedContact + direction, contacts.size());
    }

    @Nullable
    private Contact currentContact() {
        List<Contact> contacts = menu.getContacts();
        if (contacts.isEmpty()) {
            selectedContact = 0;
            return null;
        }
        selectedContact = Math.floorMod(selectedContact, contacts.size());
        return contacts.get(selectedContact);
    }

    private void renderManagerCards(GuiGraphics graphics, int mouseX, int mouseY) {
        contactHeaderX = -1;
        float socialOffset = socialWheelOffset();
        float alpha = RadialWheelAnimations.panelAlpha(this);
        if (alpha <= 0.01F) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        List<Component> playerLines = new ArrayList<>();
        Component form = currentForm();
        Component formFaction = currentFormFaction();
        playerLines.add(formFaction == null
                ? Component.translatable(
                        "menu.changed_synergy.relationship.info.form",
                        form)
                : Component.translatable(
                        "menu.changed_synergy.relationship.info.form_with_faction",
                        form,
                        formFaction));
        playerLines.add(Component.translatable(
                "menu.changed_synergy.relationship.info.formation",
                Component.translatable(menu.getFormation().translationKey())));
        playerLines.add(Component.translatable(
                "menu.changed_synergy.relationship.info.damage_filter",
                Component.translatable(menu.getDamageFilter().translationKey())));

        List<Component> contactLines = new ArrayList<>();
        List<Contact> contacts = menu.getContacts();
        long loaded = contacts.stream().filter(Contact::loaded).count();
        long following = contacts.stream().filter(Contact::following).count();
        contactLines.add(Component.translatable(
                "menu.changed_synergy.relationship.info.contact_count",
                contacts.size(), loaded, following));
        Contact contact = currentContact();
        if (contact == null) {
            contactLines.add(Component.translatable(
                    "menu.changed_synergy.relationship.info.no_contacts"));
        } else {
            Component contactName=contact.name().isBlank()?Component.translatable("menu.changed_synergy.relationship.unknown_contact"):
                    Component.translatable(contact.bonded()?"menu.changed_synergy.relationship.bonded_name":contact.typeId().equals("minecraft:player")?"menu.changed_synergy.relationship.player_name":"menu.changed_synergy.relationship.contact_name",contact.name());
            if(contact.campmate())contactName=Component.empty().append(contactName).append(" ").append(Component.translatable("menu.changed_synergy.relationship.camp_badge").withStyle(net.minecraft.ChatFormatting.BOLD));
            contactLines.add(Component.translatable(
                    "menu.changed_synergy.relationship.info.selected",
                    contactName));
            contactLines.add(Component.translatable(
                    "menu.changed_synergy.relationship.info.relation",
                    Component.translatable(contact.bonded()
                            ? "menu.changed_synergy.relationship.bonded"
                            : contact.campmate()?"menu.changed_synergy.relationship.campmate":"menu.changed_synergy.relationship.friend"),
                    contact.familiarity()));
            contactLines.add(Component.translatable(
                    "menu.changed_synergy.relationship.info.species",
                    entityName(contact.typeId())));
            if (contact.pureWhiteAdapted()) {
                contactLines.add(Component.translatable(
                        "menu.changed_synergy.relationship.info.pure_white_adapted"));
            }
            contactLines.add(Component.translatable(
                    "menu.changed_synergy.relationship.info.presence",
                    Component.translatable(contact.loaded()
                            ? contact.sameDimension()
                                    ? "menu.changed_synergy.relationship.same_dimension"
                                    : "menu.changed_synergy.relationship.other_dimension"
                            : "menu.changed_synergy.relationship.unloaded")));
            if (contact.hasLastLocation()) {
                contactLines.add(Component.translatable(
                        "menu.changed_synergy.relationship.info.last_location",
                        contact.dimension(), contact.lastX(),
                        contact.lastY(), contact.lastZ()));
            }
            if (contact.loaded()) {
                contactLines.add(Component.translatable(
                        "menu.changed_synergy.relationship.info.contact_state",
                        Component.translatable(contact.following()
                                ? "menu.changed_synergy.relationship.following"
                                : "menu.changed_synergy.relationship.waiting"),
                        format(contact.health()), format(contact.maxHealth())));
            }
        }

        var territory = TerritoryClientState.current();
        List<Component> localLines = new ArrayList<>();
        if (TerritoryClientState.hasCurrentArea()) {
            localLines.add(Component.translatable(
                    "menu.changed_synergy.relationship.info.territory",
                    TerritoryClientState.currentAreaTitle(),
                    TerritoryClientState.currentAreaDetail()));
            if (territory != null && territory.factionOrdinal() >= 0) {
                localLines.add(Component.translatable(
                        "menu.changed_synergy.relationship.info.local_reputation",
                        territory.reputationScore()));
            }
        } else {
            localLines.add(Component.translatable(
                    "menu.changed_synergy.relationship.info.no_territory"));
        }

        List<InfoCard> cards = List.of(
                new InfoCard(Component.translatable(
                        "menu.changed_synergy.relationship.info.player"), playerLines),
                new InfoCard(Component.translatable(
                        "menu.changed_synergy.relationship.info.contacts"), contactLines),
                new InfoCard(Component.translatable(
                        "menu.changed_synergy.relationship.info.local"), localLines));

        int x = 14;
        int wheelLeft = Math.round(width * 0.5F
                + socialOffset - 128.0F);
        int availableWidth = Math.max(1, wheelLeft - x - 12);
        int cardWidth = RelationshipPanel.CARD_WIDTH;
        int textWidth = cardWidth - 26;
        List<PreparedCard> prepared = new ArrayList<>();
        for (int index = 0; index < cards.size(); index++) {
            prepared.add(RelationshipPanel.prepareCard(font, cards.get(index), textWidth));
        }
        int gap = 10;
        int totalHeight = prepared.stream().mapToInt(PreparedCard::height).sum()
                + gap * (prepared.size() - 1);
        float panelScale = Math.min(1, Math.max(0.01F,
                // Leave clearance for the bottom switch hint and native key badges.
                Math.min((height - 56F) / totalHeight, availableWidth / (float)cardWidth)));
        int y = Math.max(10, (height - Math.round(totalHeight * panelScale)) / 2);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(panelScale, panelScale, 1);
        int cardY = 0;
        for (int index = 0; index < prepared.size(); index++) {
            PreparedCard card = prepared.get(index);
            float entrance = UiMotion.entrance(panelsOpenedAt, index);
            float cardAlpha = alpha * UiSpring.clamp(entrance);
            int slide = UiMotion.reduced() ? 0 : Math.round(-12 * (1 - entrance));
            int cardX = slide;
            RelationshipPanel.CardStyle style = switch (index) {
                case 0 -> RelationshipPanel.CardStyle.STATUS;
                case 1 -> RelationshipPanel.CardStyle.CONTACTS;
                default -> RelationshipPanel.CardStyle.LOCATION;
            };
            renderInfoCard(graphics, card, cardX, cardY, cardWidth, cardAlpha, style);
            if (index == 1 && cardAlpha > 0.1F) {
                contactHeaderX = x + Math.round(cardX * panelScale);
                contactHeaderY = y + Math.round(cardY * panelScale);
                contactHeaderWidth = Math.round(cardWidth * panelScale);
                contactHeaderScale = panelScale;
                renderContactControls(graphics, cardX, cardY, cardWidth, cardAlpha,
                        Math.round((mouseX - x) / panelScale), Math.round((mouseY - y) / panelScale));
            }
            cardY += card.height() + gap;
        }
        graphics.pose().popPose();
        int contactButton = contactButtonAt(mouseX, mouseY);
        if (contactButton >= 0) {
                String key = switch (contactButton) {
                    case 0 -> "menu.changed_synergy.relationship.info.contacts.previous";
                    case 1 -> "menu.changed_synergy.relationship.info.contacts.next";
                    default -> currentContact() != null && currentContact().bonded()
                            ? "menu.changed_synergy.relationship.controls.end_bond"
                            : "menu.changed_synergy.relationship.info.contacts.remove";
                };
                graphics.renderTooltip(font, Component.translatable(key), mouseX, mouseY);
        }
    }

    private void renderContactControls(GuiGraphics graphics, int x, int y,
                                       int cardWidth, float alpha, int mouseX, int mouseY) {
        int start = x + cardWidth - 56;
        boolean enabled = currentContact() != null;
        int normalColor = panelTitleColor();
        int hoverColor = Color3.fromInt(panelBackground()).brightness() <= 0.5F
                ? 0xFFF2A0 : 0x675000;
        String[] labels = {"<", ">", "x"};
        for (int index = 0; index < labels.length; index++) {
            int buttonX = start + index * 18;
            boolean hovered = mouseX >= buttonX && mouseX < buttonX + 16
                    && mouseY >= y + 3 && mouseY < y + 18;
            // The lowercase x has a shorter glyph than the arrows; align their visible centres.
            graphics.drawString(font, labels[index], buttonX + (16 - font.width(labels[index])) / 2, y + (index == 2 ? 6 : 7),
                    withAlpha(!enabled ? 0x888888 : hovered ? hoverColor : normalColor,
                            Math.round(0xF0 * alpha)), false);
        }
    }

    private float socialWheelOffset() {
        return RadialWheelAnimations.layoutOffset(width);
    }

    private Component currentForm() {
        var player = Minecraft.getInstance().player;
        var variant = player == null ? null
                : ProcessTransfur.getPlayerTransfurVariant(player);
        if (variant == null || variant.isTemporaryFromSuit()) {
            return Component.translatable(
                    "menu.changed_synergy.relationship.form.human");
        }
        Component name = variant.getParent().getEntityType().getDescription();
        return net.parkabird.changedsynergy.ai.PureWhiteWolfAdaptation
                        .isAdaptedPlayer(variant)
                ? Component.translatable(
                        "menu.changed_synergy.relationship.form.adapted", name)
                : name;
    }

    @Nullable
    private Component currentFormFaction() {
        var player = Minecraft.getInstance().player;
        var variant = player == null ? null
                : ProcessTransfur.getPlayerTransfurVariant(player);
        if (variant == null || variant.isTemporaryFromSuit()) {
            return null;
        }
        HunterFaction faction = HunterFaction.of(
                variant.getParent().getEntityType());
        return faction == null ? null
                : Component.translatable(faction.translationKey()).withStyle(style -> style.withColor(UiMotion.factionColor(faction)));
    }

    private static Component entityName(String typeId) {
        ResourceLocation id = ResourceLocation.tryParse(typeId);
        if (id == null) {
            return Component.translatable(
                    "menu.changed_synergy.relationship.unknown_species");
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        return type == null ? Component.literal(typeId) : type.getDescription();
    }

    private void renderInfoCard(
            GuiGraphics graphics,
            PreparedCard card,
            int x,
            int y,
            int cardWidth,
            float alpha,
            RelationshipPanel.CardStyle style) {
        if (alpha <= 0.02F) return;
        boolean contactControls = style == RelationshipPanel.CardStyle.CONTACTS;
        int foreground = panelTitleColor();
        RelationshipPanel.drawCard(graphics, x, y, style, menu.usesOrganicStyle(),
                card.height(), panelBackground(), foreground, alpha);
        graphics.drawString(font, font.plainSubstrByWidth(
                        card.title().getString(),
                        Math.max(1, cardWidth - (contactControls ? 82 : 34))),
                x + 25, y + 7,
                withAlpha(foreground, Math.round(0xFF * alpha)), false);
        int padding = RelationshipPanel.GEL_TEXT_PADDING;
        int bodyForeground = menu.usesOrganicStyle() ? 0x000000 : RelationshipPanel.bodyTextColor(panelBackground());
        float textScale = Math.min(1, (card.height() - padding) / (Math.max(1, card.lines().size()) * 9F));
        graphics.pose().pushPose();
        graphics.pose().translate(x + 13, y + RelationshipPanel.GEL_TOP, 0);
        graphics.pose().scale(textScale, textScale, 1);
        int lineY = 0;
        for (FormattedCharSequence line : card.lines()) {
            graphics.drawString(font, line, 0, lineY,
                    withAlpha(bodyForeground, Math.round(0xFF * alpha)), false);
            lineY += 9;
        }
        graphics.pose().popPose();
    }

    private int panelBackground() {
        if (menu.usesOrganicStyle()) return INVENTORY_FACE.toInt();
        return AbstractRadialScreen.getColors(ProcessTransfur.getPlayerTransfurVariant(
                minecraft.player)).background().toInt();
    }

    private int panelTitleColor() {
        if (menu.usesOrganicStyle()) return 0x404040;
        return Color3.fromInt(panelBackground()).brightness() > 0.5F
                ? 0x222222 : panelAccent();
    }

    private int panelAccent() {
        if (!menu.isTransfurred()) {
            return INVENTORY_FACE.toInt();
        }
        var player = Minecraft.getInstance().player;
        var variant = player == null ? null
                : ProcessTransfur.getPlayerTransfurVariant(player);
        return variant == null
                ? INVENTORY_FACE.toInt()
                : readableColors(variant).foreground().toInt();
    }

    private void renderSwitchHint(GuiGraphics graphics, Component hint, int y) {
        int textWidth = font.width(hint);
        float scale = Math.min(1.0F, (width - 20.0F) / Math.max(1,textWidth));
        float alpha = RadialWheelAnimations.overallAlpha(this);
        if (alpha <= 0.02F) return;
        graphics.pose().pushPose();
        graphics.pose().translate((width - textWidth * scale) / 2.0F,y,0);
        graphics.pose().scale(scale,scale,1);
        graphics.drawString(font, hint, 0, 0,
                Math.round(0xF0 * alpha) << 24 | 0x00FFFFFF, true);
        graphics.pose().popPose();
    }

    private static void blitScaledIcon(
            GuiGraphics graphics,
            ResourceLocation icon,
            int centerX,
            int centerY,
            int targetSize,
            int sourceSize) {
        float scale = (float)targetSize / sourceSize;
        graphics.pose().pushPose();
        graphics.pose().translate(centerX - targetSize * 0.5F,
                centerY - targetSize * 0.5F, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.blit(icon, 0, 0, 0.0F, 0.0F,
                sourceSize, sourceSize, sourceSize, sourceSize);
        graphics.pose().popPose();
    }

    private static String format(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static int withAlpha(int rgb, int alpha) {
        return Math.max(0, Math.min(255, alpha)) << 24
                | rgb & 0x00FFFFFF;
    }

    private static void playClick() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private static Action emote(String command, String name) {
        return new Action(
                command,
                ResourceLocation.fromNamespaceAndPath(
                        ChangedSynergyMod.MOD_ID,
                        "textures/gui/radial/icons/emote/"
                                + name + "_gray.png"),
                32);
    }

    private static Action changedIcon(
            String command,
            String path,
            int size) {
        return new Action(command, Changed.modResource(path), size);
    }

    private record Action(
            String command,
            ResourceLocation texture,
            int textureSize) {
    }

}
