package net.parkabird.changedsynergy.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.client.gui.AbstractRadialScreen;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.util.Color3;
import net.ltxprogrammer.changed.util.SingleRunnable;
import net.parkabird.changedsynergy.world.inventory.BondedLatexMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;

/** Full Changed-style interaction wheel with contextual owner-suiting controls. */
public final class BondedLatexScreen extends AbstractRadialScreen<BondedLatexMenu> {
    private static final Component ACTIVE = Component.translatable("changed.tamed_dark_latex.active");
    private static final Component INACTIVE = Component.translatable("changed.tamed_dark_latex.inactive");
    private final List<Interaction> interactions;

    public BondedLatexScreen(BondedLatexMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, colors(menu.getPet()).background(),
                colors(menu.getPet()).foreground(), center(menu, inventory));
        List<Interaction> entries = new ArrayList<>(List.of(
                interaction("view_inventory", () -> List.of(Component.translatable(
                        "changed.tamed_dark_latex.title.view_inventory")), () -> false),
                interaction("cycle_follow", () -> List.of(
                        Component.translatable("changed.tamed_dark_latex.title.cycle_follow"),
                        Component.translatable(menu.isFollowing()
                                ? "changed.tamed_dark_latex.follow"
                                : "changed.tamed_dark_latex.wander")), () -> false),
                interaction("cycle_target_type", () -> List.of(
                        Component.translatable("changed.tamed_dark_latex.title.cycle_target_type"),
                        Component.translatable(targetTypeKey(menu.getTargetType()))), () -> false),
                interaction("cycle_attack_type", () -> List.of(
                        Component.translatable(menu.isOrganicPet()
                                ? "menu.changed_synergy.bonded_latex.title.organic_rescue"
                                : "changed.tamed_dark_latex.title.cycle_attack_type"),
                        Component.translatable(menu.isOrganicPet()
                                ? organicRescueKey(menu.getAttackType())
                                : attackTypeKey(menu.getAttackType()))), () -> false),
                interaction("cycle_attack_condition", () -> List.of(
                        Component.translatable("changed.tamed_dark_latex.title.cycle_attack_condition"),
                        Component.translatable(attackConditionKey(menu.getAttackCondition()))), () -> false),
                interaction("favor_fishing", () -> List.of(
                        Component.translatable("changed.tamed_dark_latex.title.favor_fishing"),
                        menu.getFavor() == 1 ? ACTIVE : INACTIVE), () -> menu.getFavor() == 1),
                interaction("favor_caving", () -> List.of(
                        Component.translatable("changed.tamed_dark_latex.title.favor_caving"),
                        menu.getFavor() == 2 ? ACTIVE : INACTIVE), () -> menu.getFavor() == 2)));
        if (menu.isSuitOptionAvailable()) {
            ResourceLocation suitIcon = menu.isOrganicPet()
                    ? Changed.modResource(
                            "textures/abilities/"
                                    + "switch_transfur_mode_replication.png")
                    : Changed.modResource(
                            "textures/gui/tamed_dl_interactions/"
                                    + "favor_suit_owner.png");
            entries.add(interaction(
                    "favor_suit_owner",
                    suitIcon,
                    this::suitTooltips,
                    menu::isSuitingOwner));
        }
        interactions = List.copyOf(entries);
    }

    private static LivingEntity center(BondedLatexMenu menu, Inventory inventory) {
        return menu.getPet() != null ? menu.getPet() : inventory.player;
    }

    private static ColorScheme colors(@Nullable ChangedEntity pet) {
        if (pet == null || pet.getSelfVariant() == null) {
            return new ColorScheme(Color3.GRAY, Color3.WHITE).setForegroundToBright();
        }
        var pair = pet.getSelfVariant().getColors();
        return new ColorScheme(pair.getFirst(), pair.getSecond()).setForegroundToBright();
    }

    private static Interaction interaction(
            String command,
            Supplier<List<Component>> tooltips,
            Supplier<Boolean> highlight) {
        return interaction(
                command,
                Changed.modResource("textures/gui/tamed_dl_interactions/" + command + ".png"),
                tooltips,
                highlight);
    }

    private static Interaction interaction(
            String command,
            ResourceLocation texture,
            Supplier<List<Component>> tooltips,
            Supplier<Boolean> highlight) {
        return new Interaction(command, texture, tooltips, highlight);
    }

    private List<Component> suitTooltips() {
        Component title = Component.translatable(
                "menu.changed_synergy.bonded_latex.title.host_interaction");
        if (menu.isSuitingOwner()) {
            return List.of(title, Component.translatable(
                    "menu.changed_synergy.bonded_latex.release_host"));
        }
        if (menu.isOwnerTransfurred()) {
            return menu.canReverseOwner()
                    ? List.of(
                            Component.translatable(
                                    "menu.changed_synergy.bonded_latex.title.reverse_transfur"),
                            Component.translatable(
                                    "menu.changed_synergy.bonded_latex.reverse_transfur"))
                    : List.of(title, Component.translatable(
                            "menu.changed_synergy.bonded_latex.wrap_transfurred"));
        }
        if (menu.isOrganicPet()) {
            return List.of(
                    title,
                    Component.translatable(
                            "menu.changed_synergy.bonded_latex.organic_reassimilate"));
        }
        return List.of(
                title,
                Component.translatable("menu.changed_synergy.bonded_latex.left_wrap"),
                Component.translatable("menu.changed_synergy.bonded_latex.right_reassimilate"));
    }

    private static String targetTypeKey(int ordinal) {
        return "changed.tamed_dark_latex.targeting." + switch (Math.floorMod(ordinal, 3)) {
            case 1 -> "monsters";
            case 2 -> "hostile_to_owner";
            default -> "transfurable_entities";
        };
    }

    private static String attackTypeKey(int ordinal) {
        return "changed.tamed_dark_latex.attacking."
                + (Math.floorMod(ordinal, 2) == 0 ? "always_kill" : "try_transfur");
    }

    private static String organicRescueKey(int ordinal) {
        var modes = net.parkabird.changedsynergy.ai.LatexSocialMemory
                .OrganicRescueMode.values();
        return modes[Math.floorMod(ordinal, modes.length)].translationKey();
    }

    private static String attackConditionKey(int ordinal) {
        return "changed.tamed_dark_latex.attack_condition." + switch (Math.floorMod(ordinal, 3)) {
            case 1 -> "always";
            case 2 -> "owner_is_hostile";
            default -> "never";
        };
    }

    @Override
    public int getCount() {
        return interactions.size();
    }

    @Override
    public Optional<Integer> getSectionAt(int mouseX, int mouseY) {
        int wheelOffset = Math.round(
                RadialWheelAnimations.horizontalOffset(this));
        return super.getSectionAt(mouseX - wheelOffset, mouseY);
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Component hint = Component.translatable(
                "menu.changed_synergy.wheel.switch_to_social");
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

    @Nullable
    @Override
    public List<Component> tooltipsFor(int section) {
        return section >= 0 && section < interactions.size()
                ? interactions.get(section).tooltips().get()
                : null;
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
        Optional<Integer> hovered = getSectionAt(mouseX, mouseY);
        boolean thisHovered = hovered.filter(index -> index == section).isPresent();
        boolean active = section >= 0 && section < interactions.size()
                && interactions.get(section).highlight().get();
        String wheelStyle = menu.isOrganicPet() ? "organic" : "goo";
        ResourceLocation texture = Changed.modResource(
                "textures/gui/radial/" + wheelStyle
                        + (thisHovered || hovered.isEmpty() && active ? "_selected/" : "/")
                        + section + ".png");
        graphics.setColor(
                red, green, blue,
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
        if (section < 0 || section >= interactions.size()) {
            return;
        }
        ResourceLocation icon = interactions.get(section).texture();
        graphics.setColor(0.0F, 0.0F, 0.0F, 0.5F * alpha);
        graphics.blit(icon, (int)x - 21 + leftPos, (int)y - 21 + topPos,
                0.0F, 0.0F, 48, 48, 48, 48);
        graphics.setColor(red, green, blue, alpha);
        graphics.blit(icon, (int)x - 24 + leftPos, (int)y - 24 + topPos,
                0.0F, 0.0F, 48, 48, 48, 48);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public boolean handleClicked(int section, SingleRunnable close) {
        if (section < 0 || section >= interactions.size()) {
            return false;
        }
        if (section == suitSection()) {
            sendCommand(contextualSuitCommand(false));
            return true;
        }

        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        String command = interactions.get(section).command();
        if ("view_inventory".equals(command)) {
            // Use Changed's own UpdateableMenu path, just like its native pet wheel.
            // This keeps the request tied to the currently verified server menu instead
            // of looking the pet up again from a client-side entity id.
            sendCommand(command);
            return false;
        }
        menu.applyLocalCommand(command);
        sendCommand(command);
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Optional<Integer> section = getSectionAt((int)mouseX, (int)mouseY);
        int suitSection = suitSection();
        if (button == 1 && suitSection >= 0
                && section.filter(value -> value == suitSection).isPresent()) {
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            sendCommand(contextualSuitCommand(true));
            if (Minecraft.getInstance().player != null) {
                Runnable close = Minecraft.getInstance().player::closeContainer;
                if (!RadialWheelAnimations.requestClose(this, close)) {
                    close.run();
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private String contextualSuitCommand(boolean rightClick) {
        if (menu.isSuitingOwner()) {
            return "release_owner";
        }
        if (menu.canReverseOwner()) {
            return "reverse_owner";
        }
        if (menu.isOrganicPet() && !menu.isOwnerTransfurred()) {
            return "reassimilate_owner";
        }
        if (rightClick && !menu.isOwnerTransfurred()) {
            return "reassimilate_owner";
        }
        return "suit_owner";
    }

    private int suitSection() {
        for (int index = 0; index < interactions.size(); index++) {
            if ("favor_suit_owner".equals(interactions.get(index).command())) {
                return index;
            }
        }
        return -1;
    }

    private void sendCommand(String command) {
        CompoundTag payload = new CompoundTag();
        payload.putString("command", command);
        menu.setDirty(payload);
    }

    private record Interaction(
            String command,
            ResourceLocation texture,
            Supplier<List<Component>> tooltips,
            Supplier<Boolean> highlight) {
    }
}
