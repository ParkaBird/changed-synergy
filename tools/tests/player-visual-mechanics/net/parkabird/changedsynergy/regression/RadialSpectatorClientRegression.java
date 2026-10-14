package net.parkabird.changedsynergy.regression;

import java.lang.reflect.Field;
import net.ltxprogrammer.changed.client.gui.AbilityRadialScreen;
import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.ltxprogrammer.changed.entity.PlayerDataExtension;
import net.ltxprogrammer.changed.world.inventory.AbilityRadialMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;
import net.minecraftforge.client.event.ScreenEvent;
import net.parkabird.changedsynergy.client.PlayerRelationshipClientEvents;
import net.parkabird.changedsynergy.client.RadialWheelAnimations;

/** Exercises actual injected screens and input handlers, including rejected handoffs. */
public final class RadialSpectatorClientRegression {
    private static int checks;
    public static void run(Minecraft mc) {
        var previousVariant = ProcessTransfur.getPlayerTransfurVariant(mc.player);
        var previousMode = mc.gameMode.getPlayerMode();
        var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        if (info == null) throw new AssertionError("Local player info was not received");
        var previousInfoMode = info.getGameMode();
        var previousMenu = mc.player.containerMenu;
        var previousScreen = mc.screen;
        try {
            ((PlayerDataExtension)mc.player).setTransfurVariant(TransfurVariantInstance.variantFor(
                    ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get(), mc.player));
            mc.gameMode.setLocalMode(GameType.SPECTATOR);
            set(info, "gameMode", GameType.SPECTATOR);
            check(mc.player.isSpectator(), "Fixture is a transformed spectator");
            var ability = open(mc);
            for (double direction : new double[]{-1, 1}) {
                var scroll = new ScreenEvent.MouseScrolled.Pre(ability, 0, 0, direction);
                PlayerRelationshipClientEvents.onMouseScrolled(scroll);
                check(!scroll.isCanceled() && !(boolean)field(ability, "changedSynergy$closing"),
                        "Spectator scroll leaves native ability paging available without an invalid handoff");
            }
            ability.keyPressed(256, 0, 0);
            ageClose(ability); ability.tick();
            check(mc.screen == null, "Spectator can dismiss the native wheel with Escape");

            mc.gameMode.setLocalMode(GameType.CREATIVE);
            set(info, "gameMode", GameType.CREATIVE);
            ability = open(mc);
            var scroll = new ScreenEvent.MouseScrolled.Pre(ability, 0, 0, 1);
            PlayerRelationshipClientEvents.onMouseScrolled(scroll);
            check(scroll.isCanceled() && (boolean)field(ability, "changedSynergy$closing"),
                    "Normal transformed players can still request the relationship wheel");
            ability.keyPressed(256, 0, 0);
            check(mc.screen == null, "Escape cancels a handoff before its packet is sent");
            check(String.valueOf(field(RadialWheelAnimations.class, "pendingArrival")).equals("NONE"),
                    "Cancellation clears the paired-wheel token");
            ageClose(ability); ability.tick();
            check(mc.screen == null && field(ability, "changedSynergy$completion") == null,
                    "A cancelled close cannot later reopen a menu");

            ability = open(mc);
            int[] requests = {0};
            check(RadialWheelAnimations.requestWheelSwitch(ability, () -> requests[0]++),
                    "A second switch is available immediately after cancellation");
            ageClose(ability); ability.tick();
            check(requests[0] == 1 && mc.screen == ability,
                    "Fixture simulates a sent request that the server rejects");
            ability.keyPressed(256, 0, 0);
            check(mc.screen == null, "Escape recovers after the server rejects a completed wheel handoff");
            System.out.println("RADIAL_SPECTATOR_CLIENT_REGRESSION_PASS: " + checks + " assertions");
        } finally {
            RadialWheelAnimations.cancelPendingSwitch();
            ((PlayerDataExtension)mc.player).setTransfurVariant(previousVariant);
            set(info, "gameMode", previousInfoMode);
            mc.gameMode.setLocalMode(previousMode); mc.player.containerMenu = previousMenu; mc.setScreen(previousScreen);
        }
    }
    private static AbilityRadialScreen open(Minecraft mc) {
        var menu = new AbilityRadialMenu(81, mc.player.getInventory()); mc.player.containerMenu = menu;
        var screen = new AbilityRadialScreen(menu, mc.player.getInventory(), AbilityRadialMenu.CONTAINER_TITLE);
        mc.setScreen(screen); return screen;
    }
    private static void ageClose(Object screen) { set(screen, "changedSynergy$closingAtNanos", System.nanoTime() - 2_000_000_000L); }
    private static Field find(Object object, String key) {
        Class<?> type = object instanceof Class<?> value ? value : object.getClass();
        for (; type != null; type = type.getSuperclass()) try {
            Field field = type.getDeclaredField(key); field.setAccessible(true); return field;
        } catch (NoSuchFieldException ignored) { }
        throw new AssertionError("Missing fixture field: " + key);
    }
    private static Object field(Object object, String key) {
        try { return find(object, key).get(object instanceof Class<?> ? null : object); }
        catch (IllegalAccessException failure) { throw new AssertionError(failure); }
    }
    private static void set(Object object, String key, Object value) {
        try { find(object, key).set(object, value); }
        catch (IllegalAccessException failure) { throw new AssertionError(failure); }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message); checks++; System.out.println("RADIAL_SPECTATOR_PASS: " + message);
    }
}
