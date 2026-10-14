package net.parkabird.changedsynergy.regression;

import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.parkabird.changedsynergy.ai.PlayerOutpostData;
import net.parkabird.changedsynergy.client.PlayerOutpostScreen;
import net.parkabird.changedsynergy.client.PlayerRelationshipScreen;
import net.parkabird.changedsynergy.world.inventory.PlayerOutpostMenu;
import net.parkabird.changedsynergy.world.inventory.PlayerRelationshipMenu;

/** Native rendering of the changed camp screens, kept out of release builds. */
public final class CampUiClientRegression {
    public static Map<String, Screen> prepare() {
        var mc = Minecraft.getInstance();
        Map<String, Screen> previews = new LinkedHashMap<>();
        for (String action : new String[]{"outpost", "create_outpost", "link_outpost"}) {
            var data = new net.minecraft.network.FriendlyByteBuf(Unpooled.buffer());
            try {
                for (int i = 0; i < 4; i++) data.writeBoolean(false);
                data.writeVarInt(0); data.writeVarInt(0); data.writeUtf(action); data.writeVarInt(0);
                var menu = new PlayerRelationshipMenu(1, mc.player.getInventory(), data);
                check(data.readableBytes() == 0 && menu.outpostAction().equals(action), "Contextual wheel payload");
                previews.put(action, new PlayerRelationshipScreen(menu, mc.player.getInventory(), PlayerRelationshipMenu.TITLE));
            } finally { data.release(); }
        }
        for (var job : new PlayerOutpostData.CrewJob[]{null, PlayerOutpostData.CrewJob.MINE, PlayerOutpostData.CrewJob.HUNT}) {
        int count = job == null ? 6 : 64;
        var data = new net.minecraft.network.FriendlyByteBuf(Unpooled.buffer());
        try {
            data.writeUtf("minecraft:overworld"); data.writeBlockPos(new BlockPos(0, 64, 0));
            data.writeUtf("3, 64, 0"); data.writeUtf("3 / 2"); data.writeVarInt(64); data.writeBoolean(true);
            data.writeUtf("✓ / ✓ / ✓ / ✓"); data.writeBoolean(true); data.writeVarInt(0);
            data.writeCollection(java.util.List.of("minecraft:overworld"), (buffer, value) -> buffer.writeUtf(value));
            data.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                data.writeUUID(UUID.randomUUID()); data.writeUtf("Camp worker " + (i + 1));
                data.writeVarInt((job == null ? PlayerOutpostData.Role.SUPPLY : PlayerOutpostData.Role.CREW).ordinal()); data.writeVarInt(0);
                data.writeUtf("minecraft:overworld"); data.writeBlockPos(new BlockPos(i, 64, 2)); data.writeBoolean(true);
                data.writeVarInt(PlayerOutpostData.Work.COOK.ordinal()); data.writeVarInt(PlayerOutpostData.Work.HAUL.ordinal());
                data.writeVarInt(33);
                data.writeVarInt((job == null ? PlayerOutpostData.CrewJob.COMBAT : job).ordinal());
            }
            var menu = new PlayerOutpostMenu(2, mc.player.getInventory(), data);
            check(data.readableBytes() == 0 && menu.members().size() == count && menu.meals(), "Camp payload and live policy");
            check(menu.members().stream().allMatch(member -> member.crewJob() == (job == null ? PlayerOutpostData.CrewJob.COMBAT : job)), "Crew job survives opening and live data");
            previews.put(job == null ? "management" : "crew-" + job.name().toLowerCase(java.util.Locale.ROOT),
                    new PlayerOutpostScreen(menu, mc.player.getInventory(), PlayerOutpostMenu.TITLE));
        } finally { data.release(); }
        }
        RadialUiClientRegression.prepare(previews);
        return previews;
    }

    public static void capture(Screen screen, String name) {
        var mc = Minecraft.getInstance();
        check(mc.screen == screen, "Native frame uses the expected camp screen");
        for (var child : screen.children()) if (child instanceof Button button)
            check(button.getX() >= 0 && button.getY() >= 0 && button.getX() + button.getWidth() <= screen.width
                    && button.getY() + button.getHeight() <= screen.height, "Camp button fits window: " + name);
        if (screen instanceof PlayerOutpostScreen) {
            var tooltip = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> button.visible && button.getMessage().getString().contains("Camp worker 1"))
                    .findFirst().orElseThrow().getTooltip();
            String text = tooltip.toCharSequence(mc).stream().map(sequence -> {
                StringBuilder value = new StringBuilder();
                sequence.accept((index, style, codepoint) -> { value.appendCodePoint(codepoint); return true; });
                return value.toString();
            }).collect(java.util.stream.Collectors.joining(" "));
            check(text.contains("minecraft:overworld") && text.contains("0, 64, 2") && !text.contains("member_current_location"), "Name hover includes translated live dimension and coordinates");
        }
        if (name.startsWith("crew-")) {
            var prefix = net.minecraft.network.chat.Component.translatable("menu.changed_synergy.player_outpost.crew_job", "").getString();
            var buttons = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).toList();
            check(buttons.stream().anyMatch(button -> button.visible && button.active && button.getWidth() > 300
                    && button.getMessage().getString().startsWith(prefix)), "Crew job has a translated full-width active control");
            var backup = net.minecraft.network.chat.Component.translatable("menu.changed_synergy.player_outpost.backup_work", "").getString();
            check(buttons.stream().noneMatch(button -> button.visible && button.getMessage().getString().startsWith(backup)), "Supply backup is hidden for travelling crew");
        }
        var directory = mc.gameDirectory.toPath().resolve("screenshots");
        try (var snapshot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Files.createDirectories(directory);
            snapshot.writeToFile(directory.resolve("camp-" + name + ".png"));
        } catch (java.io.IOException failure) { throw new AssertionError(failure); }
        System.out.println("CAMP_UI_RENDER_PASS: " + name);
    }

    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
