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
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.OpenAbsorptionNegotiationPacket;
import net.parkabird.changedsynergy.world.inventory.PlayerRelationshipMenu;

/** Player-side relationship hub, rendered with Changed's radial vocabulary. */
public final class PlayerRelationshipScreen
        extends AbstractRadialScreen<PlayerRelationshipMenu> {
    private static final Color3 INVENTORY_SHADOW = Color3.fromInt(0x8B8B8B);
    private static final Color3 INVENTORY_FACE = Color3.fromInt(0xC6C6C6);
    private static final List<Action> ACTIONS = List.of(
            emote("contacts", "heart"),
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

    public PlayerRelationshipScreen(
            PlayerRelationshipMenu menu,
            Inventory inventory,
            Component title) {
        super(menu, inventory, title,
                colors(menu, inventory).background(),
                colors(menu, inventory).foreground(),
                inventory.player);
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

    private static ColorScheme readableColors(
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
        return ACTIONS.size();
    }

    @Override
    public Optional<Integer> getSectionAt(int mouseX, int mouseY) {
        int wheelOffset = Math.round(RadialWheelAnimations.horizontalOffset(this));
        return super.getSectionAt(mouseX - wheelOffset, mouseY);
    }

    @Nullable
    @Override
    public List<Component> tooltipsFor(int section) {
        if (section < 0 || section >= ACTIONS.size()) {
            return null;
        }
        String key = "menu.changed_synergy.relationship.action."
                + actionFor(section).command();
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable(key));
        tooltip.add(Component.translatable(key + ".hint"));
        if (section == 0 && currentContact() != null) {
            tooltip.add(Component.translatable(
                    "menu.changed_synergy.relationship.action.contacts.current",
                    currentContact().name()));
        } else if (section == 1) {
            tooltip.add(Component.translatable(menu.getFormation().translationKey()));
        } else if (section == 7) {
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
        renderManagerCards(graphics);
        if (menu.isTransfurred()) {
            renderSwitchHint(graphics, Component.translatable(
                    "menu.changed_synergy.relationship.switch_to_abilities"));
        }
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
        String style = menu.usesOrganicStyle() ? "organic" : "goo";
        ResourceLocation texture = Changed.modResource(
                "textures/gui/radial/" + style
                        + (selected ? "_selected/" : "/") + section + ".png");
        graphics.setColor(red, green, blue,
                RadialWheelAnimations.layerAlpha(this));
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
        if (section < 0 || section >= ACTIONS.size()) {
            return;
        }
        Action action = actionFor(section);
        ResourceLocation icon = action.texture();
        int centerX = (int)x + leftPos;
        int centerY = (int)y + topPos;
        graphics.setColor(0.0F, 0.0F, 0.0F, 0.45F * alpha);
        blitScaledIcon(graphics, icon, centerX + 2, centerY + 2,
                34, action.textureSize());
        graphics.setColor(red, green, blue, alpha);
        blitScaledIcon(graphics, icon, centerX, centerY,
                32, action.textureSize());
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public boolean handleClicked(int section, SingleRunnable close) {
        if (section < 0 || section >= ACTIONS.size()) {
            return false;
        }
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
            cycleContact(1);
            return false;
        }
        CompoundTag payload = new CompoundTag();
        payload.putString("command", switch (section) {
            case 1 -> "cycle_formation";
            case 2 -> "toggle_group_follow";
            case 3 -> "shared_activity";
            case 4 -> "social_signal";
            case 5 -> "request_help";
            case 6 -> "mediate";
            case 7 -> "cycle_damage_filter";
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
        return section == 4 && menu.usesAbsorptionNegotiationSlot()
                ? ABSORPTION_NEGOTIATION : ACTIONS.get(section);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 2) {
            Optional<Integer> section = getSectionAt((int)mouseX, (int)mouseY);
            Contact contact = currentContact();
            if (section.isPresent() && section.get() == 0 && contact != null) {
                playClick();
                CompoundTag payload = new CompoundTag();
                payload.putString("command", "cancel_relationship");
                payload.putUUID("contact", contact.uuid());
                menu.setDirty(payload);
                return true;
            }
        }
        if (button == 1) {
            Optional<Integer> section = getSectionAt((int)mouseX, (int)mouseY);
            if (section.isPresent() && section.get() == 0) {
                if (!menu.getContacts().isEmpty()) {
                    playClick();
                    cycleContact(-1);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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

    private void renderManagerCards(GuiGraphics graphics) {
        float wheelOffset = RadialWheelAnimations.horizontalOffset(this);
        float socialOffset = socialWheelOffset();
        float panelProgress = socialOffset <= 0.0F
                ? 1.0F : clamp01(wheelOffset / socialOffset);
        float alpha = RadialWheelAnimations.overallAlpha(this)
                * smoothStep(panelProgress);
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
            contactLines.add(Component.translatable(
                    "menu.changed_synergy.relationship.info.selected",
                    contact.name().isBlank()
                            ? Component.translatable(
                                    "menu.changed_synergy.relationship.unknown_contact")
                            : Component.literal(contact.name())));
            contactLines.add(Component.translatable(
                    "menu.changed_synergy.relationship.info.relation",
                    Component.translatable(contact.bonded()
                            ? "menu.changed_synergy.relationship.bonded"
                            : "menu.changed_synergy.relationship.friend"),
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
        int availableWidth = Math.max(108, wheelLeft - x - 12);
        int cardWidth = Math.min(width >= 800 ? 260 : 214, availableWidth);
        int textWidth = Math.max(90, cardWidth - 18);
        List<PreparedCard> prepared = cards.stream()
                .map(card -> prepareCard(card, textWidth))
                .toList();
        int gap = 6;
        int totalHeight = prepared.stream().mapToInt(PreparedCard::height).sum()
                + gap * (prepared.size() - 1);
        int y = Math.max(10, (height - totalHeight) / 2);
        int accent = panelAccent();
        for (PreparedCard card : prepared) {
            renderInfoCard(graphics, card, x, y, cardWidth, accent, alpha);
            y += card.height() + gap;
        }
    }

    private float socialWheelOffset() {
        float desired = Math.min(220.0F, width * 0.22F);
        float roomAtRight = Math.max(0.0F, width * 0.5F - 136.0F);
        return Math.min(desired, roomAtRight);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float smoothStep(float value) {
        float clamped = clamp01(value);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private Component currentForm() {
        var player = Minecraft.getInstance().player;
        var variant = player == null ? null
                : ProcessTransfur.getPlayerTransfurVariant(player);
        if (variant == null || variant.isTemporaryFromSuit()) {
            return Component.translatable(
                    "menu.changed_synergy.relationship.form.human");
        }
        return variant.getParent().getEntityType().getDescription();
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
                : Component.translatable(faction.translationKey());
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
        graphics.fill(x, y, x + cardWidth, y + card.height(),
                withAlpha(0x373737, Math.round(0xD0 * alpha)));
        graphics.fill(x, y, x + 3, y + card.height(),
                withAlpha(accent, Math.round(0xF0 * alpha)));
        graphics.fill(x + 3, y, x + cardWidth, y + 1,
                withAlpha(0xFFFFFF, Math.round(0x88 * alpha)));
        graphics.drawString(font, card.title(), x + 9, y + 7,
                withAlpha(accent, Math.round(0xFF * alpha)), true);
        graphics.fill(x + 9, y + 19, x + cardWidth - 8, y + 20,
                withAlpha(0x8B8B8B, Math.round(0xA0 * alpha)));
        int lineY = y + 23;
        for (FormattedCharSequence line : card.lines()) {
            graphics.drawString(font, line, x + 9, lineY,
                    withAlpha(0xFFFFFF, Math.round(0xEC * alpha)), false);
            lineY += 9;
        }
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

    private void renderSwitchHint(GuiGraphics graphics, Component hint) {
        int textWidth = font.width(hint);
        int x = (width - textWidth) / 2;
        int y = height - 18;
        float alpha = RadialWheelAnimations.overallAlpha(this);
        graphics.fill(x - 5, y - 3, x + textWidth + 5, y + 11,
                Math.round(0x78 * alpha) << 24);
        graphics.drawString(font, hint, x, y,
                Math.round(0xF0 * alpha) << 24 | 0x00FFFFFF, true);
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

    private record InfoCard(Component title, List<Component> lines) {
    }

    private record PreparedCard(
            Component title,
            List<FormattedCharSequence> lines,
            int height) {
    }
}
