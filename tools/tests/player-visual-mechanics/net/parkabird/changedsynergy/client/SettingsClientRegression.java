package net.parkabird.changedsynergy.client;

import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ChangedSynergyClientConfig;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.network.TakeoverStatePacket;

/** Exercises actual config widgets and snapshot decoding in the private integrated client. */
public final class SettingsClientRegression {
    private static int checks;
    private static boolean oldEmotes;
    public static Map<String, ChangedSynergyConfigScreen> prepare() {
        var minecraft = Minecraft.getInstance();
        var oldSpeed = ChangedSynergyConfig.COMMON.huntSearchSpeed.get();
        var oldHeight = ChangedSynergyClientConfig.CLIENT.danmakuHeightOffset.get();
        var oldBorrow = ChangedSynergyConfig.COMMON.takeoverBorrow.get();
        var oldEscape = ChangedSynergyConfig.COMMON.takeoverEscape.get();
        var oldBlocked = ChangedSynergyConfig.COMMON.playerOutpostHaulingBlockedItems.get();
        var oldBlockedOres = ChangedSynergyConfig.COMMON.playerOutpostCrewBlockedOres.get();
        try {
            var screen = new ChangedSynergyConfigScreen(null);
            minecraft.setScreen(screen);
            inspect(screen, "gameplayCategories", 115, ChangedSynergyConfig.SPEC);
            inspect(screen, "clientCategories", 16, ChangedSynergyClientConfig.SPEC);
            inspect(screen, "worldCategories", 14, null);
            Gson json = new Gson();
            ChangedSynergyConfig.options().forEach((key, config) -> {
                Object fallback = config.getDefault();
                check(decode(config, json.toJson(fallback), fallback).equals(fallback), "Snapshot round trip: " + key);
                for (String invalid : List.of("null", "{}", "[null]", "invalid", "\"invalid\""))
                    check(decode(config, invalid, fallback).equals(fallback), "Invalid snapshot keeps safe default: " + key);
            });
            var blocklist = findField(screen, "player_outpost_hauling_blocked_items");
            blocklist.setValue("bad id"); done(screen);
            check(minecraft.screen == screen, "Pickup blocklist rejects a malformed item ID");
            blocklist.setValue("minecraft:diamond, example:optional_item"); done(screen);
            check(ChangedSynergyConfig.COMMON.playerOutpostHaulingBlockedItems.get().equals(
                    List.of("minecraft:diamond", "example:optional_item")), "Pickup blocklist saves namespaced items");
            screen = new ChangedSynergyConfigScreen(null); minecraft.setScreen(screen);
            findField(screen, "player_outpost_hauling_blocked_items").setValue(""); done(screen);
            check(ChangedSynergyConfig.COMMON.playerOutpostHaulingBlockedItems.get().isEmpty(), "Blank pickup blocklist restores all item types");
            screen = new ChangedSynergyConfigScreen(null); minecraft.setScreen(screen);
            var ores = findField(screen, "player_outpost_crew_blocked_ores");
            ores.setValue("bad id"); done(screen);
            check(minecraft.screen == screen, "Ore blocklist rejects a malformed block ID");
            ores.setValue("minecraft:diamond_ore, example:optional_ore"); done(screen);
            check(ChangedSynergyConfig.COMMON.playerOutpostCrewBlockedOres.get().equals(List.of("minecraft:diamond_ore", "example:optional_ore")), "Ore blocklist saves optional-mod block IDs");
            screen = new ChangedSynergyConfigScreen(null); minecraft.setScreen(screen);
            findField(screen, "player_outpost_crew_blocked_ores").setValue(""); done(screen);
            check(ChangedSynergyConfig.COMMON.playerOutpostCrewBlockedOres.get().isEmpty(), "Blank ore blocklist restores all ore types");
            check(ServerConfigClient.decode(ChangedSynergyConfig.COMMON.bondedKinKillBaseLimit, "1.5", 3) == 3, "Fractional integer rejected");
            check(ServerConfigClient.decode(ChangedSynergyConfig.COMMON.huntSearchSpeed, "1e999", 0.45) == 0.45, "Infinite decimal rejected");
            ChangedSynergyConfig.COMMON.huntSearchSpeed.set(0.12345);
            screen = new ChangedSynergyConfigScreen(null); minecraft.setScreen(screen);
            check(findField(screen, "search_speed").getValue().equals("0.12345"), "Opening screen retains decimal precision");
            done(screen);
            check(ChangedSynergyConfig.COMMON.huntSearchSpeed.get() == 0.12345, "Save without edits preserves precision");
            screen = new ChangedSynergyConfigScreen(null); minecraft.setScreen(screen); tab(screen, "client");
            var height = findField(screen, "danmaku_height");
            height.setValue("-");
            check(height.getValue().equals("-"), "Numeric widget accepts intermediate minus");
            done(screen); check(minecraft.screen == screen, "Intermediate minus cannot be saved");
            height.setValue("-0.123456789"); done(screen);
            check(ChangedSynergyClientConfig.CLIENT.danmakuHeightOffset.get() == -0.123456789, "Negative precise decimal editable and saved");
            ChangedSynergyConfig.COMMON.takeoverBorrow.set(false);
            ChangedSynergyConfig.COMMON.takeoverEscape.set(false);
            var packet = new TakeoverStatePacket(UUID.randomUUID(), TakeoverStatePacket.NORMAL, TakeoverStatePacket.CONTROLLED,
                    minecraft.player.getId(), minecraft.player.getId(), 100, 100, 0, false, false, false, 0, 0, 0, 0, 20, 0, "Test");
            callStatic(TakeoverScreen.class, "actions", new Class<?>[]{TakeoverStatePacket.class}, packet);
            check(((List<?>)callStatic(TakeoverScreen.class, "actions", new Class<?>[]{TakeoverStatePacket.class}, packet)).isEmpty(), "All disabled takeover controls hidden safely");
            ChangedSynergyConfig.COMMON.takeoverEscape.set(true);
            check(((List<?>)callStatic(TakeoverScreen.class, "actions", new Class<?>[]{TakeoverStatePacket.class}, packet)).size() == 1, "Enabled escape control remains");
            ChangedSynergyConfig.COMMON.takeoverBorrow.set(true);
            var exoskeleton = new TakeoverStatePacket(UUID.randomUUID(), TakeoverStatePacket.EXOSKELETON, TakeoverStatePacket.CONTROLLED,
                    minecraft.player.getId(), minecraft.player.getId(), 100, 100, 0, false, false, true, 0, 0, 0, 0, 20, 0, "Test");
            check(((List<?>)callStatic(TakeoverScreen.class, "actions", new Class<?>[]{TakeoverStatePacket.class}, exoskeleton)).isEmpty(),
                    "Exoskeleton takeover cannot offer ordinary borrowing, escape or negotiation");
            var previews = new LinkedHashMap<String, ChangedSynergyConfigScreen>();
            previews.put("server-performance", new ChangedSynergyConfigScreen(null));
            var growth = new ChangedSynergyConfigScreen(null); minecraft.setScreen(growth);
            pageFor(growth, "balanced_relationships"); previews.put("relationship-growth", growth);
            var world = new ChangedSynergyConfigScreen(null); minecraft.setScreen(world); tab(world, "world");
            previews.put("world-rules", world);
            oldEmotes = minecraft.getSingleplayerServer().getGameRules().getBoolean(ChangedSynergyGameRules.NPC_EMOTES);
            var emoteEntry = entries(world, "worldCategories").stream()
                    .filter(entry -> read(entry, "key").equals("changedSynergyNpcEmotes")).findFirst().orElseThrow();
            write(emoteEntry, "value", !oldEmotes); write(emoteEntry, "dirty", true);
            done(world);
            check(minecraft.screen == null, "Operator world edit submitted through Done");
            return previews;
        } finally {
            ChangedSynergyConfig.COMMON.huntSearchSpeed.set(oldSpeed);
            ChangedSynergyClientConfig.CLIENT.danmakuHeightOffset.set(oldHeight);
            ChangedSynergyConfig.COMMON.takeoverBorrow.set(oldBorrow);
            ChangedSynergyConfig.COMMON.takeoverEscape.set(oldEscape);
            ChangedSynergyConfig.COMMON.playerOutpostHaulingBlockedItems.set(oldBlocked);
            ChangedSynergyConfig.COMMON.playerOutpostCrewBlockedOres.set(oldBlockedOres);
            ChangedSynergyConfig.SPEC.save(); ChangedSynergyClientConfig.SPEC.save();
        }
    }
    public static void verifyWorldChange() {
        var minecraft = Minecraft.getInstance();
        check(minecraft.getSingleplayerServer().getGameRules().getBoolean(ChangedSynergyGameRules.NPC_EMOTES) != oldEmotes,
                "World edit reaches actual server gamerule");
        check(ServerConfigClient.ruleValue(ChangedSynergyGameRules.settings().stream().filter(s -> s.key() == ChangedSynergyGameRules.NPC_EMOTES).findFirst().orElseThrow()).equals(!oldEmotes),
                "Rules page reads actual server value");
        minecraft.getConnection().sendCommand("gamerule changedSynergyNpcEmotes " + oldEmotes);
        System.out.println("SETTINGS_CLIENT_REGRESSION_PASS checks=" + checks);
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object decode(ForgeConfigSpec.ConfigValue config, String raw, Object fallback) { return ServerConfigClient.decode(config, raw, fallback); }
    private static List<Object> entries(Object screen, String field) {
        var result = new ArrayList<Object>();
        for (Object category : (List<?>)read(screen, field)) result.addAll((List<?>)invoke(category, "entries"));
        return result;
    }
    private static void inspect(Object screen, String field, int expected, ForgeConfigSpec spec) {
        var entries = entries(screen, field);
        check(entries.size() == expected, field + " complete count");
        Set<String> keys = new HashSet<>();
        Set<Object> configs = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Object entry : entries) {
            check(keys.add((String)read(entry, "key")), "No duplicate widget label");
            var label = (Component)invoke(entry, "label");
            check(!label.getString().startsWith("config.") && !label.getString().startsWith("gamerule."), "Label translated");
            invoke(entry, "reset");
            check((Boolean)invoke(entry, "valid"), "Reset yields valid value");
            if (spec == null) continue;
            var config = (ForgeConfigSpec.ConfigValue<?>)read(entry, "config");
            check(configs.add(config), "Each setting appears once");
            Object value;
            try { value = read(entry, "value"); }
            catch (AssertionError missingValue) {
                String text = (String)read(entry, "text");
                value = config.getDefault() instanceof Number ? Double.parseDouble(text) : config.getDefault();
            }
            check(value instanceof Number a && config.getDefault() instanceof Number b
                    ? a.doubleValue() == b.doubleValue() : value.equals(config.getDefault()), "Reset uses Forge default");
            if (config.getDefault() instanceof Number) {
                ForgeConfigSpec.ValueSpec definition = spec.getSpec().get(config.getPath());
                check(((Number)read(entry, "min")).doubleValue() == ((Number)definition.getRange().getMin()).doubleValue()
                        && ((Number)read(entry, "max")).doubleValue() == ((Number)definition.getRange().getMax()).doubleValue(), "Widget range matches Forge spec");
            }
        }
    }
    private static Field field(Object object, String name) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new AssertionError("Missing field " + name);
    }
    private static Object read(Object object, String name) {
        try { return field(object, name).get(object); } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void write(Object object, String name, Object value) {
        try { field(object, name).set(object, value); } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static Object invoke(Object object, String name) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) try {
            Method method = type.getDeclaredMethod(name); method.setAccessible(true); return method.invoke(object);
        } catch (NoSuchMethodException ignored) { } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        throw new AssertionError("Missing method " + name);
    }
    private static Object callStatic(Class<?> type, String name, Class<?>[] arguments, Object... values) {
        try { Method method = type.getDeclaredMethod(name, arguments); method.setAccessible(true); return method.invoke(null, values); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void tab(ChangedSynergyConfigScreen screen, String name) {
        screen.children().stream().filter(child -> child instanceof Button b && b.getMessage().getString()
                .equals(Component.translatable("config.changed_synergy.section." + name).getString()))
                .map(child -> (Button)child).findFirst().orElseThrow().onPress();
    }
    private static void next(ChangedSynergyConfigScreen screen) {
        screen.children().stream().filter(child -> child instanceof Button b && b.active && b.getMessage().getString().equals(">"))
                .map(child -> (Button)child).findFirst().orElseThrow().onPress();
    }
    private static void pageFor(ChangedSynergyConfigScreen screen, String key) {
        for (int i = 0; i < 100; i++) {
            for (Object row : (List<?>)read(screen, "rowLabels")) if (read(read(row, "entry"), "key").equals(key)) return;
            next(screen);
        }
        throw new AssertionError(key);
    }
    private static EditBox findField(ChangedSynergyConfigScreen screen, String key) {
        pageFor(screen, key);
        String label = Component.translatable("config.changed_synergy.option." + key).getString();
        return screen.children().stream().filter(child -> child instanceof EditBox e && e.getMessage().getString().equals(label))
                .map(child -> (EditBox)child).findFirst().orElseThrow();
    }
    private static void done(ChangedSynergyConfigScreen screen) {
        screen.children().stream().filter(child -> child instanceof Button b && b.getMessage().equals(CommonComponents.GUI_DONE))
                .map(child -> (Button)child).findFirst().orElseThrow().onPress();
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
