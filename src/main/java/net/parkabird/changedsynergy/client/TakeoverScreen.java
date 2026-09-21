package net.parkabird.changedsynergy.client;

import java.util.List;
import java.util.UUID;
import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.client.gui.AbstractRadialScreen;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.util.Color3;
import net.ltxprogrammer.changed.util.SingleRunnable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.network.TakeoverActionPacket;
import net.parkabird.changedsynergy.network.TakeoverStatePacket;

/** Body-control choices presented through Changed's native radial-wheel language. */
public final class TakeoverScreen extends AbstractRadialScreen<TakeoverScreen.TakeoverMenu> {
    private static final Action BORROW = icon(TakeoverActionPacket.REQUEST_CONTROL, "idea");
    private static final Action ESCAPE = icon(TakeoverActionPacket.START_STRUGGLE, "deny");
    private static final Action RETURN = icon(TakeoverActionPacket.RETURN_CONTROL, "pause");
    private final UUID session;
    private final List<Action> actions;
    private boolean escapeConfirmationPending;

    public TakeoverScreen() {
        this(new TakeoverMenu(), Minecraft.getInstance().player.getInventory(),
                TakeoverClientState.current());
    }

    private TakeoverScreen(TakeoverMenu menu, Inventory inventory, TakeoverStatePacket state) {
        super(menu, inventory, TakeoverClientState.text("title"),
                colors(state).background(), colors(state).foreground(), center(state, inventory.player));
        session = state == null ? null : state.sessionId();
        actions = state != null && state.phase() == TakeoverStatePacket.BORROWED
                ? List.of(RETURN) : List.of(BORROW, ESCAPE);
    }

    private static Action icon(int action, String emote) {
        return new Action(action, ResourceLocation.fromNamespaceAndPath(ChangedSynergyMod.MOD_ID,
                "textures/gui/radial/icons/emote/" + emote + "_gray.png"));
    }

    private static LivingEntity center(TakeoverStatePacket state, Player fallback) {
        if (state != null && fallback.level() != null
                && fallback.level().getEntity(state.carrierId()) instanceof LivingEntity living) return living;
        return fallback;
    }

    private static ColorScheme colors(TakeoverStatePacket state) {
        var player = Minecraft.getInstance().player;
        if (state != null && player != null && player.level() != null
                && player.level().getEntity(state.carrierId()) instanceof ChangedEntity carrier
                && carrier.getSelfVariant() != null) {
            var pair = carrier.getSelfVariant().getColors();
            return new ColorScheme(pair.getFirst(), pair.getSecond()).setForegroundToBright();
        }
        return new ColorScheme(Color3.GRAY, Color3.WHITE).setForegroundToBright();
    }

    static int foregroundColor(TakeoverStatePacket state) {
        return colors(state).foreground().toInt() & 0xFFFFFF;
    }

    private boolean valid() {
        return TakeoverClientState.canOpen() && session != null
                && session.equals(TakeoverClientState.current().sessionId());
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public int getCount() { return actions.size(); }

    @Override
    public List<Component> tooltipsFor(int section) {
        if (section < 0 || section >= actions.size()) return null;
        int action = actions.get(section).id();
        if (action == TakeoverActionPacket.REQUEST_CONTROL) {
            return List.of(TakeoverClientState.text("borrow"),
                    TakeoverClientState.cooldownTicks() > 0
                            ? TakeoverClientState.text("cooldown",
                                Math.max(1, (TakeoverClientState.cooldownTicks() + 19) / 20))
                            : TakeoverClientState.text("borrow_hint"));
        }
        if (action == TakeoverActionPacket.START_STRUGGLE) {
            return List.of(TakeoverClientState.text(
                            escapeConfirmationPending ? "confirm" : "escape"),
                    TakeoverClientState.text(TakeoverClientState.current().escapeUsed()
                            ? "used" : escapeConfirmationPending
                                    ? "confirm_warning" : "warning"));
        }
        return List.of(TakeoverClientState.text("return"));
    }

    @Override
    public void renderSectionBackground(GuiGraphics graphics, int section, double x, double y,
            float partialTick, int mouseX, int mouseY, float red, float green, float blue) {
        boolean selected = getSectionAt(mouseX, mouseY).filter(i -> i == section).isPresent();
        ResourceLocation texture = Changed.modResource("textures/gui/radial/goo"
                + (selected ? "_selected/" : "/") + section + ".png");
        graphics.setColor(red, green, blue, 1.0F);
        graphics.blit(texture, (int)x - 32 + leftPos, (int)y - 32 + topPos,
                0.0F, 0.0F, 64, 64, 64, 64);
        graphics.setColor(1, 1, 1, 1);
    }

    @Override
    public void renderSectionForeground(GuiGraphics graphics, int section, double x, double y,
            float partialTick, int mouseX, int mouseY, float red, float green, float blue, float alpha) {
        if (section < 0 || section >= actions.size()) return;
        int centerX = (int)x + leftPos, centerY = (int)y + topPos;
        ResourceLocation icon = actions.get(section).texture();
        graphics.setColor(0, 0, 0, 0.45F * alpha);
        graphics.blit(icon, centerX - 14, centerY - 14, 0, 0, 28, 28, 32, 32);
        graphics.setColor(red, green, blue, alpha);
        graphics.blit(icon, centerX - 16, centerY - 16, 0, 0, 32, 32, 32, 32);
        graphics.setColor(1, 1, 1, 1);
    }

    @Override
    public boolean handleClicked(int section, SingleRunnable close) {
        if (!valid() || section < 0 || section >= actions.size()) return false;
        int action = actions.get(section).id();
        if (action == TakeoverActionPacket.START_STRUGGLE
                && !TakeoverClientState.current().escapeUsed()
                && !escapeConfirmationPending) {
            escapeConfirmationPending = true;
            TakeoverClientState.request(TakeoverActionPacket.CONFIRM_STRUGGLE, session);
            return false;
        }
        return TakeoverClientState.request(action, session);
    }

    @Override public void containerTick() {
        super.containerTick();
        if (!valid()) onClose();
    }

    private record Action(int id, ResourceLocation texture) {}

    public static final class TakeoverMenu extends AbstractContainerMenu {
        private TakeoverMenu() { super(null, 0); }
        @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
        @Override public boolean stillValid(Player player) { return TakeoverClientState.canOpen(); }
    }
}
