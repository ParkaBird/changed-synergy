package net.parkabird.changedsynergy.client;

import com.google.gson.JsonParser;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, value = Dist.CLIENT)
public final class ServerConfigClient {
    private static CompoundTag values = new CompoundTag();
    private ServerConfigClient() {}
    public static boolean received() { return !values.isEmpty(); }
    public static void receive(CompoundTag snapshot) {
        values = snapshot.copy();
        if (Minecraft.getInstance().screen instanceof ChangedSynergyConfigScreen screen) screen.refreshServerValues();
    }
    @SubscribeEvent public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { values = new CompoundTag(); }

    public static Object ruleValue(ChangedSynergyGameRules.Setting<?> setting) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        String raw = server != null ? setting.value(server.getGameRules())
                : values.getString("gamerule." + setting.name());
        try {
            if (setting.defaultValue() instanceof Boolean) {
                if ("true".equals(raw)) return true;
                if ("false".equals(raw)) return false;
            } else return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) { }
        return setting.defaultValue();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> T value(ForgeConfigSpec.ConfigValue<T> config) {
        T local = config.get();
        Minecraft client = Minecraft.getInstance();
        String key = String.join(".", config.getPath());
        if (client.getConnection() == null || client.getSingleplayerServer() != null
                || ChangedSynergyConfig.SPEC.getValues().get(config.getPath()) != config || !values.contains(key)) return local;
        return decode(config, values.getString(key), local);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static <T> T decode(ForgeConfigSpec.ConfigValue<T> config, String raw, T fallback) {
        try {
            var json = JsonParser.parseString(raw);
            if (json == null || json.isJsonNull()) return fallback;
            Object remote;
            if (fallback instanceof Boolean) {
                if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isBoolean()) return fallback;
                remote = json.getAsBoolean();
            } else if (fallback instanceof Integer) {
                if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isNumber()) return fallback;
                remote = json.getAsBigDecimal().intValueExact();
            } else if (fallback instanceof Double) {
                if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isNumber()) return fallback;
                remote = json.getAsDouble();
            } else if (fallback instanceof List<?>) {
                if (!json.isJsonArray()) return fallback;
                var list = new java.util.ArrayList<String>();
                for (var item : json.getAsJsonArray()) {
                    if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) return fallback;
                    list.add(item.getAsString());
                }
                remote = List.copyOf(list);
            } else {
                if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) return fallback;
                remote = fallback instanceof Enum<?> mode
                        ? Enum.valueOf((Class)mode.getDeclaringClass(), json.getAsString()) : json.getAsString();
            }
            ForgeConfigSpec.ValueSpec spec = ChangedSynergyConfig.SPEC.getSpec().get(config.getPath());
            return spec.test(remote) ? (T)remote : fallback;
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException | com.google.gson.JsonParseException invalid) {
            return fallback;
        }
    }
}
