package net.parkabird.changedsynergy.command;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;
import net.parkabird.changedsynergy.ChangedSynergyConfig;

/** Operator and console access to the server's existing common TOML settings. */
final class ServerConfigCommand {
    private static final int PAGE_SIZE = 8;
    private static final Map<String, ForgeConfigSpec.ConfigValue<?>> OPTIONS = options();

    private ServerConfigCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> create() {
        return Commands.literal("config")
                .requires(source -> source.hasPermission(2))
                .executes(context -> help(context.getSource()))
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource(), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> list(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "page")))))
                .then(Commands.literal("get")
                        .then(Commands.argument("key", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        OPTIONS.keySet(), builder))
                                .executes(context -> get(context.getSource(),
                                        StringArgumentType.getString(context, "key")))))
                .then(Commands.literal("set")
                        .then(Commands.argument("key", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        OPTIONS.keySet(), builder))
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(context -> set(context.getSource(),
                                                StringArgumentType.getString(context, "key"),
                                                StringArgumentType.getString(context, "value"))))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("key", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        OPTIONS.keySet(), builder))
                                .executes(context -> reset(context.getSource(),
                                        StringArgumentType.getString(context, "key")))));
    }

    private static Map<String, ForgeConfigSpec.ConfigValue<?>> options() {
        Map<String, ForgeConfigSpec.ConfigValue<?>> result = new TreeMap<>();
        collect(ChangedSynergyConfig.SPEC.getValues(), result);
        return Map.copyOf(result);
    }

    private static void collect(UnmodifiableConfig node,
            Map<String, ForgeConfigSpec.ConfigValue<?>> result) {
        for (Object child : node.valueMap().values()) {
            if (child instanceof UnmodifiableConfig nested) {
                collect(nested, result);
            } else if (child instanceof ForgeConfigSpec.ConfigValue<?> value) {
                List<String> path = value.getPath();
                if (!"BehaviourConfigRevision".equals(path.get(path.size() - 1))) {
                    result.put(String.join(".", path), value);
                }
            }
        }
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable(
                "command.changed_synergy.config.help"), false);
        return 1;
    }

    private static int list(CommandSourceStack source, int page) {
        List<String> names = new ArrayList<>(OPTIONS.keySet());
        names.sort(String::compareTo);
        int pages = Math.max(1, (names.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page > pages) {
            source.sendFailure(Component.translatable(
                    "command.changed_synergy.config.page_invalid", pages));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                "command.changed_synergy.config.list", page, pages)
                .withStyle(ChatFormatting.GOLD), false);
        for (int i = (page - 1) * PAGE_SIZE;
                i < Math.min(page * PAGE_SIZE, names.size()); i++) {
            String key = names.get(i);
            source.sendSuccess(() -> Component.literal(key + " = " + OPTIONS.get(key).get())
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return names.size();
    }

    private static int get(CommandSourceStack source, String key) {
        ForgeConfigSpec.ConfigValue<?> option = OPTIONS.get(key);
        if (option == null) {
            return unknown(source, key);
        }
        ForgeConfigSpec.ValueSpec spec = ChangedSynergyConfig.SPEC.getSpec()
                .get(option.getPath());
        source.sendSuccess(() -> Component.translatable(
                "command.changed_synergy.config.value", key, option.get(),
                option.getDefault(), spec.getRange() == null
                        ? "-" : spec.getRange().toString()), false);
        return 1;
    }

    private static int set(CommandSourceStack source, String key, String raw) {
        ForgeConfigSpec.ConfigValue<?> option = OPTIONS.get(key);
        if (option == null) {
            return unknown(source, key);
        }
        Object value = parse(raw, option.getDefault());
        ForgeConfigSpec.ValueSpec spec = ChangedSynergyConfig.SPEC.getSpec()
                .get(option.getPath());
        if (value == null || !spec.test(value)) {
            source.sendFailure(Component.translatable(
                    "command.changed_synergy.config.invalid", key, raw,
                    spec.getRange() == null ? "-" : spec.getRange().toString()));
            return 0;
        }
        setValue(option, value);
        ChangedSynergyConfig.SPEC.save();
        source.sendSuccess(() -> Component.translatable(
                "command.changed_synergy.config.saved", key, value), true);
        return 1;
    }

    private static Object parse(String text, Object defaultValue) {
        try {
            if (defaultValue instanceof Boolean) {
                return "true".equalsIgnoreCase(text) ? true
                        : "false".equalsIgnoreCase(text) ? false : null;
            }
            if (defaultValue instanceof Integer) {
                return Integer.valueOf(text);
            }
            if (defaultValue instanceof Double) {
                double value = Double.parseDouble(text);
                return Double.isFinite(value) ? value : null;
            }
        } catch (NumberFormatException ignored) {
            // A malformed or out-of-range value is reported without changing the config.
        }
        return null;
    }

    private static int reset(CommandSourceStack source, String key) {
        ForgeConfigSpec.ConfigValue<?> option = OPTIONS.get(key);
        if (option == null) {
            return unknown(source, key);
        }
        Object value = option.getDefault();
        setValue(option, value);
        ChangedSynergyConfig.SPEC.save();
        source.sendSuccess(() -> Component.translatable(
                "command.changed_synergy.config.saved", key, value), true);
        return 1;
    }

    @SuppressWarnings("unchecked")
    private static void setValue(ForgeConfigSpec.ConfigValue<?> option, Object value) {
        ((ForgeConfigSpec.ConfigValue<Object>) option).set(value);
    }

    private static int unknown(CommandSourceStack source, String key) {
        source.sendFailure(Component.translatable(
                "command.changed_synergy.config.unknown", key));
        return 0;
    }
}
