package net.parkabird.changedsynergy.regression;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Measures the actual Minecraft font, including written books nested in contributed structures. */
@Mod.EventBusSubscriber(modid = "changed_synergy", value = Dist.CLIENT)
public final class ResearchBookPaginationRegression {
    private static boolean done;
    private static int bookCount, pageCount, overflow;
    private static final int WIDTH = 114, HEIGHT = 128;

    private static int lines(String text) { return Minecraft.getInstance().font.split(Component.literal(text), WIDTH).size(); }
    private static int capacity() { return HEIGHT / Minecraft.getInstance().font.lineHeight; }

    private static JsonArray paginate(String original) {
        var pages = new JsonArray();
        String rest = original;
        while (lines(rest) > capacity()) {
            int lo = 1, hi = rest.length();
            while (lo < hi) {
                int mid = (lo + hi + 1) / 2;
                if (lines(rest.substring(0, mid)) <= capacity()) lo = mid; else hi = mid - 1;
            }
            String fits = rest.substring(0, lo);
            int cut = fits.lastIndexOf("\n\n") + 2;
            if (cut < lo * .55) {
                var sentences = Pattern.compile("[.!?][\\\"']?\\s+").matcher(fits);
                cut = 0;
                while (sentences.find()) cut = sentences.end();
            }
            if (cut < lo * .55) cut = fits.lastIndexOf(' ') + 1;
            if (cut <= 0) cut = lo;
            pages.add(rest.substring(0, cut));
            rest = rest.substring(cut);
        }
        pages.add(rest);
        return pages;
    }

    private static void inspect(Tag value) {
        if (value instanceof CompoundTag tag) {
            if (tag.getString("id").equals("minecraft:written_book")) {
                bookCount++;
                var book = tag.getCompound("tag"); var pages = book.getList("pages", Tag.TAG_STRING);
                for (int i = 0; i < pages.size(); i++) {
                    pageCount++;
                    var text = Component.Serializer.fromJson(pages.getString(i));
                    int count = Minecraft.getInstance().font.split(text, WIDTH).size();
                    if (count > capacity()) { overflow++; System.out.println("BOOK_PAGE_OVERFLOW title=" + book.getString("title") + " page=" + (i + 1) + " lines=" + count); }
                }
            }
            for (String key : tag.getAllKeys()) inspect(tag.get(key));
        } else if (value instanceof ListTag list) for (Tag item : list) inspect(item);
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        var mc = Minecraft.getInstance();
        if (done || event.phase != TickEvent.Phase.END || !(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
        done = true;
        try {
            if (!mc.gameDirectory.getName().startsWith("author-feedback-client-")) throw new AssertionError("private fixture only");
            org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
            var root = mc.gameDirectory.toPath().getParent().getParent();
            var source = JsonParser.parseString(Files.readString(root.resolve("tools/research_structure_documents.json"))).getAsJsonObject();
            var plan = new JsonArray();
            for (var entry : source.getAsJsonArray("documents")) {
                var doc = entry.getAsJsonObject(); if (!doc.has("title")) continue;
                var pages = new JsonArray(); var joined = new StringBuilder();
                for (var original : doc.getAsJsonArray("pages")) {
                    joined.append(original.getAsString());
                    for (var page : paginate(original.getAsString())) {
                        if (lines(page.getAsString()) > capacity()) throw new AssertionError("pagination overflow");
                        pages.add(page);
                    }
                }
                var rebuilt = new StringBuilder(); for (var page : pages) rebuilt.append(page.getAsString());
                if (!joined.toString().equals(rebuilt.toString())) throw new AssertionError("prose changed");
                var row = new JsonObject(); row.add("id", doc.get("id")); row.add("pages", pages);
                row.addProperty("text_sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(joined.toString().getBytes(StandardCharsets.UTF_8))));
                plan.add(row);
            }
            Files.writeString(root.resolve(".codex-tmp/research-book-pagination.json"), new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(plan));
            for (String name : new String[]{"white_lab_tower", "red_lab_tower", "green_lab", "water_tower_changed", "hyena_hideout"}) {
                try (var input = ResearchBookPaginationRegression.class.getResourceAsStream("/data/changed_synergy/structures/" + name + ".nbt")) {
                    if (input == null) throw new AssertionError("missing bundled template " + name);
                    inspect(NbtIo.readCompressed(input));
                }
            }
            if (bookCount != 6) throw new AssertionError("unexpected book count " + bookCount);
            if (Boolean.getBoolean("changed_synergy.verifyBookPagination") && overflow != 0) throw new AssertionError("remaining cut-off pages " + overflow);
            System.out.println("RESEARCH_BOOK_PAGINATION_PASS: books=" + bookCount + " pages=" + pageCount + " overflow=" + overflow + " maxLines=" + capacity() + "; lossless pagination plan saved");
        } catch (Throwable failure) { System.err.println("RESEARCH_BOOK_PAGINATION_FAIL"); failure.printStackTrace(); }
        mc.stop();
    }
}
