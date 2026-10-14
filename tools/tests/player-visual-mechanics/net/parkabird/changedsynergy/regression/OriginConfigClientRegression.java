package net.parkabird.changedsynergy.regression;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.client.ChangedSynergyConfigScreen;

/** Exercise the real list widget and save validation in the isolated client. */
public final class OriginConfigClientRegression {
    public static void run() {
        var minecraft = Minecraft.getInstance();
        // A rapid widget regression must not race Forge's asynchronous file watcher.
        // Use real synchronous files, as in the dedicated settings regression.
        isolateSettings(minecraft);
        var option = ChangedSynergyConfig.COMMON.disabledOriginForms;
        var previous = option.get();
        try {
            var screen = new ChangedSynergyConfigScreen(null);
            minecraft.setScreen(screen);
            var field = findList(screen);
            field.setValue("bad ID");
            done(screen);
            check(minecraft.screen == screen && option.get().equals(previous), "Invalid list cannot be saved");
            field.setValue("changed:form_dark_latex_wolf/male, changed:form_dark_latex_wolf/female");
            done(screen);
            check(minecraft.screen == null && option.get().equals(List.of(
                    "changed:form_dark_latex_wolf/male", "changed:form_dark_latex_wolf/female")),
                    "List widget saves complete trimmed form IDs");
            screen = new ChangedSynergyConfigScreen(null);
            minecraft.setScreen(screen);
            findList(screen).setValue("");
            done(screen);
            check(minecraft.screen == null && option.get().isEmpty(), "Empty list widget clears exclusions");
            System.out.println("ORIGIN_CONFIG_CLIENT_PASS");
        } finally {
            option.set(previous);
            ChangedSynergyConfig.SPEC.save();
        }
    }

    private static void isolateSettings(Minecraft minecraft) {
        var directory = minecraft.gameDirectory.toPath().resolve("settings-widget-regression");
        try { java.nio.file.Files.createDirectories(directory); }
        catch (java.io.IOException failure) { throw new AssertionError(failure); }
        var specs = java.util.Map.of("common", ChangedSynergyConfig.SPEC,
                "client", net.parkabird.changedsynergy.ChangedSynergyClientConfig.SPEC);
        specs.forEach((name, spec) -> {
            var file = com.electronwill.nightconfig.core.file.CommentedFileConfig
                    .builder(directory.resolve(name + ".toml")).sync().build();
            file.load();
            spec.setConfig(file);
            spec.save();
        });
    }

    private static EditBox findList(ChangedSynergyConfigScreen screen) {
        String label = Component.translatable("config.changed_synergy.option.disabled_origin_forms").getString();
        for (int page = 0; page < 100; page++) {
            for (var child : screen.children()) {
                if (child instanceof EditBox field && field.getMessage().getString().equals(label)) return field;
            }
            Button next = screen.children().stream().filter(child -> child instanceof Button button
                    && button.active && button.getMessage().getString().equals(">"))
                    .map(child -> (Button)child).findFirst().orElseThrow();
            next.onPress();
        }
        throw new AssertionError("Origin form list widget not found");
    }

    private static void done(ChangedSynergyConfigScreen screen) {
        screen.children().stream().filter(child -> child instanceof Button button
                && button.getMessage().equals(CommonComponents.GUI_DONE)).map(child -> (Button)child)
                .findFirst().orElseThrow().onPress();
    }

    private static void check(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
        System.out.println("ORIGIN_CONFIG_CLIENT_CHECK_PASS: " + message);
    }
}
