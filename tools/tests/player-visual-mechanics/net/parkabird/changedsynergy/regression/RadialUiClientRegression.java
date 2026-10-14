package net.parkabird.changedsynergy.regression;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.client.gui.AbstractRadialScreen;
import net.ltxprogrammer.changed.util.Color3;
import net.ltxprogrammer.changed.util.SingleRunnable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.parkabird.changedsynergy.ai.PlayerRelationshipSettings.Contact;
import net.parkabird.changedsynergy.client.PlayerRelationshipScreen;
import net.parkabird.changedsynergy.client.RadialWheelAnimations;
import net.parkabird.changedsynergy.world.inventory.PlayerRelationshipMenu;

/** Runs the real Changed radial screen after Synergy's client mixins are applied. */
public final class RadialUiClientRegression {
    public static void prepare(Map<String, Screen> previews) {
        var mc = Minecraft.getInstance();
        var contact = new Contact(UUID.randomUUID(), -1, "Mica J8", "changed:dark_latex_wolf_male",
                "minecraft:overworld", true, 0, 64, 0, true, false, true, false, false, 60, 24, 24, true);
        var menu = new PlayerRelationshipMenu(3, mc.player.getInventory(), List.of(contact));
        previews.put("bond-roster", new PlayerRelationshipScreen(menu, mc.player.getInventory(), PlayerRelationshipMenu.TITLE));
        var probe = new WheelProbe(menu);
        mc.setScreen(probe);
        try {
            field(probe, "changedSynergy$openedAtNanos").setLong(probe, System.nanoTime() - 2_000_000_000L);
            verifySlots(probe, 0);
            probe.mouseScrolled(0, 0, -1);
            check(RadialWheelAnimations.viewOffset(probe) == 1, "Mouse wheel advances the action offset");
            int[] point = point(probe, 0);
            check(probe.getSectionAt(point[0], point[1]).isEmpty(), "Moving icons cannot be selected during scrolling");
            probe.mouseClicked(point[0], point[1], 0);
            probe.keyPressed(49, 0, 0);
            probe.keyPressed(262, 0, 0);
            check(probe.clicked == -1 && RadialWheelAnimations.viewOffset(probe) == 1,
                    "Clicks, numeric keys and repeated keyboard scrolls cannot select or restart moving options");
            for (int i = 0; i < 8; i++) probe.containerTick();
            verifySlots(probe, 1);
            point = point(probe, 7);
            probe.mouseClicked(point[0], point[1], 0);
            check(probe.clicked == 8, "Clicking the last visible slot selects the newly revealed action");
            probe.clicked = -1;
            probe.mouseScrolled(0, 0, 1);
            for (int i = 0; i < 8; i++) probe.containerTick();
            verifySlots(probe, 0);
            for (String key : List.of("menu.changed_synergy.relationship.controls.end_bond",
                    "menu.changed_synergy.relationship.info.contacts.previous",
                    "menu.changed_synergy.relationship.info.contacts.next",
                    "menu.changed_synergy.relationship.info.contacts.remove",
                    "command.changed_synergy.relationship.help.header"))
                check(!Component.translatable(key).getString().equals(key), "Client translation exists: " + key);
            System.out.println("RADIAL_UI_CLIENT_REGRESSION_PASS");
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        finally { mc.setScreen(null); }
    }

    private static void verifySlots(WheelProbe screen, int offset) {
        for (int slot = 0; slot < 8; slot++) {
            int[] point = point(screen, slot);
            check(screen.getSectionAt(point[0], point[1]).orElse(-1) == slot + offset,
                    "Action index follows the visible slot after scrolling");
            for (int frame = 0; frame < 8; frame++)
                check(RadialWheelAnimations.isSectionSelected(screen, frame, point[0], point[1]) == (frame == slot),
                        "Only the frame beneath the pointer highlights");
        }
    }

    private static int[] point(Screen screen, int slot) {
        double angle = (slot + 0.5 + (slot % 2 == 0 ? 0.04 : -0.04)) / 8.0 * Math.PI * 2;
        return new int[]{screen.width / 2 + (int)(Math.sin(angle) * 90), screen.height / 2 - (int)(Math.cos(angle) * 90)};
    }

    private static java.lang.reflect.Field field(Object target, String name) throws ReflectiveOperationException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }

    private static final class WheelProbe extends AbstractRadialScreen<PlayerRelationshipMenu> {
        int clicked = -1;
        WheelProbe(PlayerRelationshipMenu menu) {
            super(menu, Minecraft.getInstance().player.getInventory(), Component.literal("Radial regression"), Color3.WHITE, Color3.GRAY);
        }
        @Override public int getCount() { return 12; }
        @Override public List<Component> tooltipsFor(int section) { return List.of(Component.literal("Action " + section)); }
        @Override public boolean handleClicked(int section, SingleRunnable complete) { clicked = section; return false; }
        @Override public void renderSectionBackground(GuiGraphics g, int s, double x, double y, float p, int mx, int my, float r, float b, float a) { }
        @Override public void renderSectionForeground(GuiGraphics g, int s, double x, double y, float p, int mx, int my, float r, float b, float a, float alpha) { }
    }
}
