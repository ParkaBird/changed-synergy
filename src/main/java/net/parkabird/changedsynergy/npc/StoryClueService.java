package net.parkabird.changedsynergy.npc;

import net.ltxprogrammer.changed.block.entity.TextBlockEntity;
import net.ltxprogrammer.changed.computers.DiscData;
import net.ltxprogrammer.changed.computers.Folder;
import net.ltxprogrammer.changed.world.inventory.ComputerMenu;
import net.ltxprogrammer.changed.world.inventory.TextMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Mod.EventBusSubscriber(modid = "changed_synergy")
public final class StoryClueService {
    private static final Pattern AEC_WORD = Pattern.compile("\\baec\\b");

    private StoryClueService() {}

    public static boolean hasClue(Player player, String condition) {
        if (!(player instanceof ServerPlayer sp) || condition == null || !condition.startsWith("clue:")) {
            return false;
        }
        String clue = condition.substring(5);
        Set<String> found = StoryNpcData.get(sp.server).clues.get(sp.getUUID());
        return found != null && found.contains(clue);
    }

    public static void unlock(ServerPlayer player, String clue) {
        if (player == null || clue == null || clue.isBlank()) {
            return;
        }
        var data = StoryNpcData.get(player.server);
        var set = data.clues.computeIfAbsent(player.getUUID(), id -> new HashSet<>());
        if (set.add(clue)) {
            data.setDirty();
        }
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        scanContainer(player, event.getContainer());
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        scanContainer(player, event.getContainer());
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        scanItem(player, event.getItemStack());
    }

    /** Invisible item-frame discs (Burrow / desert / labs) only unlock when the frame is inspected. */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getTarget() instanceof ItemFrame frame && !frame.getItem().isEmpty()) {
            scanItem(player, frame.getItem());
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getTarget() instanceof ItemFrame frame && !frame.getItem().isEmpty()) {
            scanItem(player, frame.getItem());
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
        if (be instanceof TextBlockEntity textBe) {
            inspectText(player, textBe.getText());
        } else if (be instanceof LecternBlockEntity lectern && lectern.hasBook()) {
            scanItem(player, lectern.getBook());
        } else if (be != null && be.getClass().getName().toLowerCase(Locale.ROOT).contains("computer")) {
            scanTag(player, be.saveWithoutMetadata(), 0);
        }
        ItemStack held = event.getItemStack();
        if (!held.isEmpty() && !held.is(Items.WRITTEN_BOOK) && !held.is(Items.WRITABLE_BOOK)) {
            scanItem(player, held);
        }
    }

    private static void scanContainer(ServerPlayer player, Object container) {
        if (container instanceof TextMenu textMenu) {
            inspectText(player, textMenu.textCopy);
            if (textMenu instanceof ComputerMenu computerMenu) {
                scanDisc(player, computerMenu.getData());
            }
        } else if (container instanceof LecternMenu lecternMenu) {
            scanItem(player, lecternMenu.getBook());
        }
    }

    private static void scanDisc(ServerPlayer player, DiscData data) {
        if (data == null) {
            return;
        }
        data.getFolderSafe(Path.of("/")).ifPresent(root -> scanFolder(player, root, Collections.newSetFromMap(new java.util.IdentityHashMap<>()), 0));
    }

    private static void scanFolder(ServerPlayer player, Folder folder, Set<Folder> seen, int depth) {
        if (folder == null || depth > 16 || !seen.add(folder)) {
            return;
        }
        if (folder.files != null) {
            folder.files.forEach((name, file) -> {
                inspectText(player, name);
                if (file != null) {
                    inspectText(player, file.content);
                }
            });
        }
        if (folder.folders != null) {
            folder.folders.forEach((name, sub) -> {
                inspectText(player, name);
                scanFolder(player, sub, seen, depth + 1);
            });
        }
    }

    private static void scanItem(ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return;
        }
        if (stack.is(Items.WRITTEN_BOOK) || stack.is(Items.WRITABLE_BOOK)) {
            StringBuilder sb = new StringBuilder();
            appendBookText(tag, sb);
            inspectText(player, sb.toString());
            return;
        }
        if (tag.contains("Data", Tag.TAG_COMPOUND)) {
            scanDisc(player, new DiscData(tag.getCompound("Data")));
        }
        if (tag.contains("Text", Tag.TAG_STRING)) {
            inspectText(player, tag.getString("Text"));
        }
        if (tag.contains("BlockEntityTag", Tag.TAG_COMPOUND)) {
            CompoundTag bet = tag.getCompound("BlockEntityTag");
            if (bet.contains("Text", Tag.TAG_STRING)) {
                inspectText(player, bet.getString("Text"));
            }
            if (bet.contains("Data", Tag.TAG_COMPOUND)) {
                scanDisc(player, new DiscData(bet.getCompound("Data")));
            }
        }
        scanTag(player, tag, 0);
    }

    private static void appendBookText(CompoundTag tag, StringBuilder sb) {
        if (tag.contains("title", Tag.TAG_STRING)) {
            sb.append(tag.getString("title")).append('\n');
        }
        if (tag.contains("author", Tag.TAG_STRING)) {
            sb.append(tag.getString("author")).append('\n');
        }
        if (tag.contains("filtered_title", Tag.TAG_STRING)) {
            sb.append(tag.getString("filtered_title")).append('\n');
        }
        if (tag.contains("pages", Tag.TAG_LIST)) {
            ListTag pages = tag.getList("pages", Tag.TAG_STRING);
            for (int i = 0; i < pages.size(); i++) {
                String raw = pages.getString(i);
                sb.append(raw).append('\n');
                try {
                    Component comp = Component.Serializer.fromJson(raw);
                    if (comp != null) {
                        sb.append(comp.getString()).append('\n');
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static void scanTag(ServerPlayer player, Tag tag, int depth) {
        if (tag == null || depth > 8) {
            return;
        }
        if (tag instanceof StringTag str) {
            inspectText(player, str.getAsString());
        } else if (tag instanceof CompoundTag cmp) {
            for (String key : cmp.getAllKeys()) {
                scanTag(player, cmp.get(key), depth + 1);
            }
        } else if (tag instanceof ListTag list) {
            for (Tag elem : list) {
                scanTag(player, elem, depth + 1);
            }
        }
    }

    public static void inspectText(ServerPlayer player, String rawText) {
        if (player == null || rawText == null || rawText.isBlank()) {
            return;
        }
        String text = rawText.toLowerCase(Locale.ROOT);
        if (text.contains("crisscross")) {
            unlock(player, "crisscross");
        }
        if (text.contains("oroshiagito")
                || text.contains("by dr. o")
                || text.contains("dr. o")
                || text.contains("dr.o")
                || text.contains("new latexes")
                || text.contains("newly discovered species")
                || text.contains("latex manul")) {
            unlock(player, "dr_o");
            unlock(player, "archives");
        }
        if (text.contains("alternative energy core")
                || text.contains("aec lecture")
                || text.contains("dr. s")
                || text.contains("dr.s")
                || text.contains("point prometheus")
                || text.contains("reclamation crew")
                || AEC_WORD.matcher(text).find()) {
            unlock(player, "aec");
            unlock(player, "archives");
        }
        if (text.contains("3579")
                || text.contains("researcher violet")) {
            unlock(player, "violet_code");
            unlock(player, "notes");
            unlock(player, "archives");
        }
        if (text.contains("uses for boxes")
                || text.contains("1000 uses")) {
            unlock(player, "box_note");
            unlock(player, "notes");
            unlock(player, "archives");
        }
        if (text.contains("field coat")
                || text.contains("calories")
                || text.contains("rumored human")
                || text.contains("laser field")
                || text.contains("safety equipment")) {
            unlock(player, "notes");
            unlock(player, "archives");
        }
        if (text.contains("568712")) {
            unlock(player, "desert_568712");
        }
        if (text.contains("gas latex strain")
                || text.contains("a sleek new lupine body")
                || text.contains("wolf paws: 10/10")
                || text.contains("writing with paws")
                || text.contains("a doctor's note")) {
            unlock(player, "green_doctors_note");
        }
        if (text.contains("farewell note")
                || text.contains("count the number of domes")
                || text.contains("bridge the gap between us")) {
            unlock(player, "green_farewell");
        }
        if (text.contains("report no. 205678")
                || text.contains("latex muntjac")
                || text.contains("field observation log")) {
            unlock(player, "green_muntjac");
            unlock(player, "green_reports");
        }
        if (text.contains("crystal status")
                || text.contains("integrty: ok")
                || text.contains("shiny gem")) {
            unlock(player, "green_crystal");
            unlock(player, "green_reports");
        }
        if (text.contains("give water twice a day")
                || text.contains("affect plants")
                || text.contains("increase in grown")
                || text.contains("botanical sync log")
                || text.contains("23-b")) {
            unlock(player, "green_botany");
            unlock(player, "green_reports");
        }
        if (text.contains("warm-air heating is running steady")
                || text.contains("暖风运转稳定")
                || text.contains("обогреватели работают ровно")
                || text.contains("move_in.txt")
                || text.contains("kick off the blankets")
                || text.contains("老踢被子")) {
            unlock(player, "burrow_terminal");
        }
        if (text.contains("house rules")
                || text.contains("室内规矩")
                || text.contains("wipe snow off your boots")
                || text.contains("蹭干净靴子")
                || text.contains("cardboard box room")
                || text.contains("纸箱室")
                || text.contains("i'll handle the door")) {
            unlock(player, "burrow_rules");
        }
    }
}
