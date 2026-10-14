package net.parkabird.changedsynergy.client;

import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.parkabird.changedsynergy.client.archive.ArchiveGeometry;
import net.parkabird.changedsynergy.client.archive.ArchiveOriginModels;
import net.parkabird.changedsynergy.client.archive.ArchivePaperRenderer;
import net.parkabird.changedsynergy.client.archive.ArchiveUiSounds;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.NativeOriginChoosePacket;
import net.parkabird.changedsynergy.network.NativeOriginStatePacket;
import net.parkabird.changedsynergy.origin.NativeOriginCatalog.Entry;
import net.parkabird.changedsynergy.origin.NativeOriginCatalog;

/** Archive presentation; origin validation and birth placement remain server-owned. */
public final class NativeOriginScreen extends Screen {
    private static final double PAPER_W = 774, PAPER_H = PAPER_W * 297 / 210;
    private static final int PAGE_ARROW_Y = 1064;
    private static final int INK = 0xFF242323, MUTED = 0xFF76746E;
    private static final int[] COLORS = {0xFF9B9C99, 0xFFF4F4F2, 0xFF71659C,
            0xFFDCCB73, 0xFFA2524F, 0xFF4C88B2};
    private static final String[] CATEGORY_NAMES = {"human", "white", "dark", "variant", "organic", "aquatic"};
    private enum View { WELCOME, DESKTOP, FILE }
    private enum Motion { INTRO, OUTRO, OPEN, CLOSE }
    private record Page(int category, int number, Entry detail) {}
    private record FolderPose(double x, double y, double angle, double scale) {
        FolderPose mix(FolderPose other, double amount) {
            double t = ArchiveGeometry.smooth(amount);
            return new FolderPose(Mth.lerp(t, x, other.x), Mth.lerp(t, y, other.y),
                    Mth.lerp(t, angle, other.angle), Mth.lerp(t, scale, other.scale));
        }
    }
    private static final FolderPose DESKTOP = new FolderPose(960, 632, -5, .45);
    private static final FolderPose HIDDEN = new FolderPose(960, 1580, 9, .43);
    private static final FolderPose FOCUS = new FolderPose(960 + PAPER_W * .79 / 2, 72 + PAPER_H * .79 / 2, 0, .79);
    private static final double TRAVEL = .46 + Math.hypot(FOCUS.x - DESKTOP.x, FOCUS.y - DESKTOP.y) / 1920 * .12;
    private static final class Turn {
        final Page from, to;
        final int direction;
        final double duration;
        final long begun = Util.getMillis();
        boolean captured;
        Turn(Page from, Page to, int direction, double duration) {
            this.from = from; this.to = to; this.direction = direction; this.duration = duration;
        }
        double elapsed() { return (Util.getMillis() - begun) / 1000.0; }
    }

    private NativeOriginStatePacket state;
    private final List<List<Entry>> categories = new ArrayList<>();
    private final int[] savedPages = new int[6];
    private final ArchiveOriginModels models = new ArchiveOriginModels();
    private final ArchivePaperRenderer papers = new ArchivePaperRenderer();
    private View view = View.WELCOME;
    private Page paper = new Page(0, 0, null);
    private Motion motion;
    private long motionBegan;
    private boolean settled;
    private Turn turn;
    private FolderPose pose = HIDDEN;
    private double open, desktopAlpha, welcomeAlpha = 1, recoil, hoverWeight;
    private double fanLeft, fanRight;
    private long lastFrame = Util.getMillis();
    private long welcomeBegan = lastFrame;
    private boolean folderHovered, dragging, previewCrouch, confirmation, closeAfterTurn;
    private float yaw, pitch, zoom = 1;
    private int detailScroll, maxDetailScroll, focusedRecord = -1;
    private int dialogScroll, maxDialogScroll, dialogLines, dialogVisibleLines;
    private int dialogTextX, dialogTextY, dialogTextWidth, dialogTextBottom;
    private boolean draggingDialogScroll;
    private int randomChoice = -1;
    private boolean slipHovered;
    private ArchiveGeometry.Fit fit;

    public NativeOriginScreen(NativeOriginStatePacket state) {
        super(text("title")); this.state = state;
        for (int i = 0; i < 6; i++) categories.add(new ArrayList<>());
        rebuildCatalog();
    }
    private static Component text(String key, Object... args) {
        return Component.translatable("origin.changed_synergy." + key, args);
    }
    private static Component archive(String key, Object... args) { return text("archive." + key, args); }
    private static Component categoryName(int index) { return archive("category." + CATEGORY_NAMES[index]); }
    private void rebuildCatalog() {
        categories.forEach(List::clear);
        for (Entry entry : state.entries()) categories.get(ArchiveOriginModels.categoryOf(entry)).add(entry);
        for (int i = 0; i < 6; i++) savedPages[i] = Math.min(savedPages[i], pageCount(i) - 1);
    }
    public void receive(NativeOriginStatePacket packet) {
        state = packet; rebuildCatalog(); confirmation = false; closeAfterTurn = false;
        resetDialogScroll();
        if (state.phase() == 2) {
            view = View.FILE; pose = FOCUS; open = 1; motion = null;
            if (turn != null) { paper = turn.to; turn = null; papers.finishTransition(); }
        }
    }
    @Override protected void init() {
        if (fit == null) welcomeBegan = Util.getMillis();
        fit = ArchiveGeometry.fit(width, height);
        // Keep semantic navigation across GUI scale/window changes, rebuild GPU resources.
        if (turn != null) { paper = turn.to; turn = null; papers.finishTransition(); }
        papers.close(); models.clear(); dragging = draggingDialogScroll = false;
        if (closeAfterTurn) { closeAfterTurn = false; beginMotion(Motion.CLOSE); }
    }
    @Override public void removed() { papers.close(); models.clear(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public void onClose() { if (!locked()) escape(); }
    private boolean locked() { return state.phase() == 1 || motion != null || turn != null; }
    private int pageCount(int category) { return Math.max(1, (categories.get(category).size() + (category == 0 ? 11 : 12)) / 12); }
    private void beginMotion(Motion next) {
        if (locked()) return;
        motion = next; motionBegan = Util.getMillis(); settled = false;
        folderHovered = false; dragging = false; confirmation = false;
        resetDialogScroll();
        if (next == Motion.OPEN) { paper = new Page(0, 0, null); resetDoll(); }
        ArchiveUiSounds.click();
    }
    private void settle(double elapsed, double when) {
        if (elapsed < when) return;
        if (!settled) { ArchiveUiSounds.settle(); settled = true; }
        double t = elapsed - when;
        recoil = t < .14 ? -5 * Math.sin(t * Math.PI * 34) * Math.exp(-26 * t) * (1 - t / .14) : 0;
    }
    private void updateMotion(double delta) {
        recoil = 0;
        if (motion == null) {
            if (view == View.DESKTOP) {
                hoverWeight = approach(hoverWeight, folderHovered ? 1 : 0, delta / .16);
                pose = new FolderPose(DESKTOP.x, DESKTOP.y - 12 * ArchiveGeometry.smooth(hoverWeight), DESKTOP.angle, DESKTOP.scale);
            }
            return;
        }
        double t = (Util.getMillis() - motionBegan) / 1000.0, end;
        switch (motion) {
            case INTRO -> {
                pose = HIDDEN.mix(DESKTOP, t / .78); open = 0;
                welcomeAlpha = 1 - ArchiveGeometry.smooth(t / .5);
                desktopAlpha = ArchiveGeometry.smooth((t - .22) / .56);
                settle(t, .78); end = .92;
            }
            case OUTRO -> {
                pose = DESKTOP.mix(HIDDEN, t / .7); open = 0;
                desktopAlpha = 1 - ArchiveGeometry.smooth(t / (.7 * .65));
                welcomeAlpha = ArchiveGeometry.smooth((t / .7 - .22) / .78); end = .7;
            }
            case OPEN -> {
                pose = DESKTOP.mix(FOCUS, t / TRAVEL); welcomeAlpha = 0;
                desktopAlpha = 1 - ArchiveGeometry.smooth(t / TRAVEL);
                settle(t, TRAVEL); open = Mth.clamp((t - TRAVEL - .14) / .76, 0, 1); end = TRAVEL + .90;
            }
            case CLOSE -> {
                open = 1 - Mth.clamp(t / .66, 0, 1);
                pose = FOCUS.mix(DESKTOP, (t - .66) / TRAVEL); welcomeAlpha = 0;
                desktopAlpha = ArchiveGeometry.smooth((t - .66) / TRAVEL);
                settle(t, .66 + TRAVEL); end = .66 + TRAVEL + .14;
            }
            default -> throw new IllegalStateException();
        }
        if (t >= end) {
            view = switch (motion) { case INTRO, CLOSE -> View.DESKTOP; case OPEN -> View.FILE; case OUTRO -> View.WELCOME; };
            motion = null; open = view == View.FILE ? 1 : 0;
            pose = view == View.FILE ? FOCUS : view == View.DESKTOP ? DESKTOP : HIDDEN;
            welcomeAlpha = view == View.WELCOME ? 1 : 0; desktopAlpha = view == View.DESKTOP ? 1 : 0;
        }
    }
    private static double approach(double value, double target, double step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }
    private void resetDoll() { yaw = pitch = 0; zoom = 1; previewCrouch = false; detailScroll = 0; }
    private void changePage(Page next, int direction, double duration) {
        if (locked() || next.equals(paper)) return;
        confirmation = false; dragging = false; randomChoice = -1;
        resetDialogScroll();
        if (state.phase() == 2) state = new NativeOriginStatePacket(0, state.nonce(), state.available(), state.canCancel(), Component.empty(), state.entries());
        focusedRecord = -1;
        closeAfterTurn = false;
        turn = new Turn(paper, next, direction, duration);
        savedPages[next.category] = next.number;
    }
    private void category(int index) {
        index = Math.floorMod(index, 6);
        if (index == paper.category) return;
        int distance = Math.abs(index - paper.category);
        changePage(new Page(index, savedPages[index], null), index > paper.category ? 1 : -1,
                .64 + Math.min(distance - 1, 4) * .055);
    }
    private void page(int number) {
        if (paper.category == 0 || paper.detail != null) return;
        number = Mth.clamp(number, 0, pageCount(paper.category) - 1);
        int distance = Math.abs(number - paper.number);
        if (distance > 0) changePage(new Page(paper.category, number, null), number > paper.number ? 1 : -1,
                .60 + Math.min(distance - 1, 3) * .055);
    }
    private void returnToRoster() { if (paper.detail != null) changePage(new Page(paper.category, paper.number, null), -1, .60); }
    private void closeFolder() {
        if (paper.category == 0) beginMotion(Motion.CLOSE);
        else {
            // Gather the opened leaves with an opaque, printed turn before moving the cover.
            changePage(new Page(0, 0, null), -1, .64 + Math.min(paper.category - 1, 4) * .055);
            closeAfterTurn = true;
        }
    }
    private boolean canChoose() {
        return randomChoice >= 0 ? randomChoice == 0 || state.available() && !categories.get(randomChoice).isEmpty()
                : paper.category == 0 || paper.detail != null && state.available();
    }
    private void openConfirmation(int choice) {
        randomChoice = choice;
        if (canChoose()) { confirmation = true; dragging = false; resetDialogScroll(); ArchiveUiSounds.click(); }
        else randomChoice = -1;
    }
    private void openRecord(int index) {
        if (index == 0) { openConfirmation(paper.category); return; }
        var entries = categories.get(paper.category);
        if (index - 1 < entries.size()) changePage(new Page(paper.category, paper.number, entries.get(index - 1)), 1, .64);
    }
    private void submit(boolean useFallback) {
        if (locked() || !canChoose()) return;
        ArchiveUiSounds.click();
        Entry entry = paper.category == 0 ? null : paper.detail;
        ChangedSynergyNetwork.CHANNEL.sendToServer(new NativeOriginChoosePacket(state.nonce(), randomChoice >= 0
                ? NativeOriginCatalog.randomRequest(randomChoice) : entry == null ? null : entry.form(), useFallback, false));
        state = new NativeOriginStatePacket(1, state.nonce(), state.available(), state.canCancel(), text("searching"), state.entries());
        confirmation = false;
    }
    private boolean noHabitat() {
        return state.phase() == 2 && state.message().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents c
                && c.getKey().equals("origin.changed_synergy.no_habitat");
    }
    private void escape() {
        if (confirmation) { confirmation = false; randomChoice = -1; resetDialogScroll(); ArchiveUiSounds.click(); return; }
        if (state.canCancel()) { disconnect(); return; }
        switch (view) {
            case WELCOME -> disconnect();
            case DESKTOP -> beginMotion(Motion.OUTRO);
            case FILE -> { if (paper.detail != null) returnToRoster(); else closeFolder(); }
        }
    }
    private void disconnect() {
        if (state.canCancel()) {
            ChangedSynergyNetwork.CHANNEL.sendToServer(new NativeOriginChoosePacket(state.nonce(), null, false, true));
            state = new NativeOriginStatePacket(1, state.nonce(), state.available(), true, text("cancelling"), state.entries());
            return;
        }
        if (minecraft.getConnection() != null) minecraft.getConnection().getConnection().disconnect(text("left_selection"));
    }
    private Component exitLabel() { return text(state.canCancel() ? "cancel_selection" : "disconnect"); }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        double delta = Math.min(.05, (now - lastFrame) / 1000.0); lastFrame = now;
        updateMotion(delta);
        var localMouse = local(mouseX, mouseY);
        slipHovered = view == View.FILE && paper.category == 0 && !locked() && !confirmation && randomSlipAt(localMouse);
        if (view == View.DESKTOP && motion == null) {
            boolean hover = inside(localMouse.x(), localMouse.y(), -24, -26, 866, PAPER_H + 126);
            if (hover && !folderHovered) ArchiveUiSounds.hover();
            folderHovered = hover;
        }
        boolean canFan = view == View.FILE && !locked() && !confirmation && paper.category != 0;
        boolean leftFan = canFan && (corner(localMouse, -1) || ArchiveGeometry.stackPageAt(localMouse, paper.number, pageCount(paper.category), paper.detail != null, fanLeft, 0) >= 0);
        boolean rightFan = canFan && (corner(localMouse, 1) || ArchiveGeometry.stackPageAt(localMouse, paper.number, pageCount(paper.category), paper.detail != null, 0, fanRight) >= 0);
        fanLeft = approach(fanLeft, leftFan ? 1 : 0, delta / .16);
        fanRight = approach(fanRight, rightFan ? 1 : 0, delta / .16);
        int hoverPage = canFan ? ArchiveGeometry.stackPageAt(localMouse, paper.number, pageCount(paper.category), paper.detail != null, fanLeft, fanRight) : -1;
        papers.hoverPage(hoverPage);
        if (turn != null && !turn.captured && turn.elapsed() >= .12) {
            Turn active = turn;
            graphics.flush();
            papers.transition(active.from.category, active.to.category);
            papers.capture(minecraft, snapshot -> content(snapshot, active.direction > 0 ? active.from : active.to, false));
            active.captured = true; ArchiveUiSounds.page();
        }
        if (turn != null && turn.elapsed() >= .12 + turn.duration) {
            if (turn.to.detail != null || turn.to.category == 0) resetDoll();
            paper = turn.to; turn = null; papers.finishTransition();
            if (closeAfterTurn) { closeAfterTurn = false; beginMotion(Motion.CLOSE); }
        }
        graphics.fill(0, 0, width, height, 0xFF000000);
        graphics.pose().pushPose(); graphics.pose().translate(fit.x(), fit.y(), 0);
        graphics.pose().scale((float)fit.scale(), (float)fit.scale(), 1);
        if (welcomeAlpha > 0) welcome(graphics, welcomeAlpha);
        if (desktopAlpha > 0) desktop(graphics, desktopAlpha);
        if (view != View.WELCOME || motion != null) {
            graphics.pose().pushPose(); graphics.pose().translate(pose.x, pose.y + recoil, 0);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees((float)pose.angle));
            graphics.pose().scale((float)pose.scale, (float)pose.scale, 1);
            graphics.pose().translate(-PAPER_W / 2, -PAPER_H / 2, 0);
            Page source = turn == null ? paper : turn.captured ? turn.to : turn.from;
            // The turning sheet supplies the new left page; it is not already resting there.
            Page left = turn != null && turn.captured && turn.direction > 0 ? turn.from : source;
            papers.progress(turn == null || !turn.captured ? 0 : ArchiveGeometry.smooth((turn.elapsed() - .12) / turn.duration));
            if (!ArchivePaperRenderer.coverInFront(open)) papers.cover(graphics, open, this::randomSlip);
            papers.shell(graphics, open, source.category, COLORS, source.number, pageCount(source.category), source.detail != null,
                    left.detail != null ? 1 : left.number, turn == null ? fanLeft : 0, turn == null ? fanRight : 0);
            // Resting paper stays printed; the moving cover provides the occlusion.
            Page shown = turn != null && turn.captured && turn.direction > 0 ? turn.to : paper;
            content(graphics, shown, turn == null && !locked());
            papers.binding(graphics, open);
            if (turn != null && turn.captured) {
                Page back = turn.direction > 0 ? turn.to : turn.from;
                int stock = turn.from.category == turn.to.category || back.detail != null || back.number > 0
                        ? 0xFFFDFBF5 : back.category > 0 ? COLORS[back.category - 1] : 0xFFDAC189;
                papers.curl(graphics, ArchiveGeometry.smooth((turn.elapsed() - .12) / turn.duration),
                        turn.direction, turn.from.category, turn.to.category, COLORS, stock);
            }
            if (ArchivePaperRenderer.coverInFront(open)) papers.cover(graphics, open, this::randomSlip);
            if (view == View.FILE && motion == null && turn == null) {
                if (state.phase() == 2 && !confirmation) notice(graphics);
                if (confirmation) confirmation(graphics);
                if (state.phase() == 1) status(graphics);
            }
            graphics.pose().popPose();
            if (view == View.FILE && !locked()) {
                if (paper.category != 0 && paper.detail == null)
                    label(graphics, archive("keyboard_controls"), 90, 1023, 23, MUTED, 1550);
                int hovered = tab(localMouse);
                if (hovered >= 0) tooltip(graphics, categoryName(hovered), mouseX, mouseY);
            }
        }
        graphics.pose().popPose();
        graphics.drawString(font, exitLabel(), width - font.width(exitLabel()) - 10, height - 14, 0xFF9F9F9F, false);
    }
    private static int alpha(int color, double opacity) { return (color & 0xFFFFFF) | ((int)(Mth.clamp(opacity, 0, 1) * 255) << 24); }
    private void welcome(GuiGraphics g, double opacity) {
        double age = (Util.getMillis() - welcomeBegan) / 1000.0;
        double prompt = ArchiveGeometry.smooth((age - .24) / .45);
        g.pose().pushPose();
        g.pose().translate(0, 18 * (1 - ArchiveGeometry.smooth(age / .45)) - 70 * (1 - opacity), 0);
        label(g, archive("eyebrow"), 174, 270, 23, alpha(0xFF888888, opacity * ArchiveGeometry.smooth(age / .45)), 1550);
        label(g, archive("welcome").copy().withStyle(net.minecraft.ChatFormatting.BOLD), 162, 370, 100, alpha(0xFFFFFFFF, opacity * ArchiveGeometry.smooth((age - .06) / .45)), 1470);
        label(g, archive("subtitle"), 180, 612, 49, alpha(0xFFFFFFFF, opacity * ArchiveGeometry.smooth((age - .12) / .45)), 1500);
        label(g, archive("intro"), 182, 690, 27, alpha(0xFF949494, opacity * ArchiveGeometry.smooth((age - .18) / .45)), 1500);
        g.fill(182, 784, 182 + (int)(52 * prompt), 786, alpha(0xFFFFFFFF, opacity * prompt));
        label(g, archive("enter"), 182, 806, 24, alpha(0xFFCCCCCC, opacity * prompt), 1400);
        g.pose().popPose();
    }
    private void desktop(GuiGraphics g, double opacity) {
        g.pose().pushPose(); g.pose().translate(0, 30 * (1 - opacity), 0);
        label(g, Component.literal("ARCHIVE").withStyle(net.minecraft.ChatFormatting.BOLD), 158, 108, 80, alpha(0xFFFFFFFF, opacity), 1000);
        label(g, archive("subtitle"), 162, 210, 30, alpha(0xFFAAAAAA, opacity), 1000);
        label(g, archive("folder_count", state.entries().size()), 1250, 170, 20, alpha(0xFF999999, opacity), 540);
        g.fill(160, 280, 1770, 282, alpha(0xFF404040, opacity));
        label(g, archive("open"), 162, 960, 22, alpha(0xFF999999, opacity), 1000);
        g.pose().popPose();
    }
    private void content(GuiGraphics g, Page page, boolean interactive) {
        if (page.category == 0 || page.detail != null) dossier(g, page, interactive); else roster(g, page, interactive);
    }
    private void roster(GuiGraphics g, Page page, boolean interactive) {
        var entries = categories.get(page.category);
        label(g, categoryName(page.category).copy().append(" / ").append(archive("register")), 44, 44, 32, INK, 530);
        label(g, Component.literal(String.format(java.util.Locale.ROOT, "%02d / %02d", page.number + 1, pageCount(page.category))), 590, 48, 23, INK, 155);
        label(g, archive("records", entries.size()), 44, 84, 21, MUTED, 640);
        for (int cell = 0; cell < 12; cell++) {
            int record = page.number * 12 + cell;
            int index = record - 1;
            if (index >= entries.size()) break;
            int x = 44 + cell % 3 * 233, y = 122 + cell / 3 * 236;
            g.fill(x - 2, y - 2, x + 216, y + 194, INK); g.fill(x, y, x + 214, y + 192, 0xFFE6E4DE);
            if (record == 0) {
                Component symbol = Component.literal("?");
                float size = 104, scale = size / 9;
                // Center the visible vanilla glyph, excluding its advance and line padding.
                label(g, symbol, x + (214 - (font.width(symbol) - 1) * scale) / 2, y + (192 - 7 * scale) / 2, size,
                        entries.isEmpty() || !state.available() || page.category == 1 ? MUTED : COLORS[page.category], 214);
                if (wideGlyphs(archive("random_category"))) label(g, archive("random_category"), x, y + 196, 26, INK, 214);
                else wrapped(g, archive("random_category"), x, y + 196, 18, INK, 214, y + 241, 1.12F);
                if (interactive && cell == focusedRecord) g.renderOutline(x - 3, y - 3, 220, 198, 0xFF967751);
                continue;
            }
            Entry entry = entries.get(index);
            models.renderPortrait(g, entry, x, y, 214, 192);
            if (interactive && cell == focusedRecord) g.renderOutline(x - 3, y - 3, 220, 198, 0xFF967751);
            label(g, Component.literal(String.format(java.util.Locale.ROOT, "%02d", index + 1)), x, y + 196, 18, MUTED, 30);
            Component name = ArchiveOriginModels.name(entry);
            if (wideGlyphs(name)) label(g, name, x + 35, y + 195, 26, INK, 179);
            else {
                float nameSize = 18;
                while (nameSize > 12 && font.split(name, (int)(179 * 9 / nameSize)).size() > 2) nameSize -= .5f;
                wrapped(g, name, x + 35, y + 195, nameSize, INK, 179, y + 239, 1.12F);
            }
        }
        if (entries.isEmpty()) label(g, text("no_matches"), 44, 405, 26, MUTED, 686);
        if (!state.available()) label(g, text("missing_backend"), 44, 1065, 16, MUTED, 686);
        if (pageCount(page.category) > 1) {
            label(g, archive("page_controls"), 250, 1067, 19, MUTED, 310);
            if (page.number > 0) label(g, Component.literal("‹"), 24, PAGE_ARROW_Y, 28, MUTED, 40);
            if (page.number + 1 < pageCount(page.category)) label(g, Component.literal("›"), 720, PAGE_ARROW_Y, 28, MUTED, 40);
        }
    }
    private void dossier(GuiGraphics g, Page page, boolean interactive) {
        boolean human = page.category == 0; Entry entry = human ? null : page.detail;
        boolean active = page.equals(paper);
        label(g, human ? archive("priority") : categoryName(page.category).copy().append(" / ").append(archive("dossier")), 44, 44, 22, MUTED, 686);
        label(g, ArchiveOriginModels.name(entry).copy().withStyle(net.minecraft.ChatFormatting.BOLD), 44, 80, 32, INK, 686);
        g.fill(43, 134, 460, 563, INK); g.fill(44, 135, 459, 562, 0xFFE6E4DE);
        models.renderDoll(g, entry, 44, 135, 415, 427, active ? yaw : 0, active ? pitch : 0, active ? zoom : 1, active && previewCrouch);
        label(g, archive(human ? "human_origin" : "native_origin"), 487, 158, 26, INK, 239);
        wrapped(g, archive("model_controls"), 487, 214, 25, MUTED, 239, 385);
        if (entry != null) {
            if (!state.available()) wrapped(g, text("missing_backend"), 487, 480, 18, 0xFF945246, 239, 558);
        }
        g.fill(44, 584, 730, 586, 0xFFAAA79F); label(g, archive("parameters"), 44, 604, 25, INK, 620);
        List<ArchiveOriginModels.Field> fields = models.fields(entry);
        int top = 651, bottom = 991, rowH = 47, count = (bottom - top) / rowH;
        int maxScroll = Math.max(0, fields.size() - count);
        int first = active ? Mth.clamp(detailScroll, 0, maxScroll) : 0;
        if (interactive) { maxDetailScroll = maxScroll; detailScroll = first; }
        for (int i = first; i < Math.min(fields.size(), first + count); i++) {
            var field = fields.get(i); int y = top + (i - first) * rowH;
            label(g, field.label(), 44, y, 24, MUTED, 240); label(g, field.value(), 291, y, 24, INK, 411);
            if (field.ratio() >= 0) {
                g.fill(291, y + 28, 705, y + 32, 0xFFD5D1C6);
                g.fill(291, y + 28, 291 + (int)(414 * Mth.clamp(field.ratio(), 0, 1)), y + 32, field.color());
            } else g.fill(44, y + 33, 705, y + 34, 0xFFC9C5B9);
        }
        if (maxScroll > 0) {
            int h = bottom - top, thumb = Math.max(24, h * count / fields.size());
            int sy = top + (h - thumb) * first / maxScroll;
            g.fill(725, top, 729, bottom, 0xFFD7D3C8); g.fill(725, sy, 729, sy + thumb, MUTED);
        }
        button(g, 44, 1021, 686, 42, archive(human ? "choose_human" : "choose_native"), human || state.available());
        if (!human) label(g, archive(state.canCancel() ? "return_register_optional" : "return_register"), 44, 1070, 15, MUTED, 620);
    }
    private void notice(GuiGraphics g) {
        g.fill(38, 736, 736, 1011, 0xF4F7F2E5);
        scrollableText(g, state.message(), 59, 758, 24, 0xFF945246, 645, noHabitat() ? 915 : 965);
        if (noHabitat()) button(g, 59, 958, 644, 42, text("fallback"), canChoose());
    }
    private boolean randomSlipAt(ArchiveGeometry.Point point) {
        // Undo the open cover's hinge transform before testing the attached note.
        double coverAngle = Math.toRadians(173), x = point.x() / -Math.cos(coverAngle);
        double dx = x + 690, dy = point.y() + Math.sin(coverAngle) * .022 * x - 872, angle = Math.toRadians(4);
        return inside(Math.cos(angle) * dx - Math.sin(angle) * dy,
                Math.sin(angle) * dx + Math.cos(angle) * dy, 0, 0, 310, 158);
    }
    /** A folded raffle slip attached to the inside cover, in the same paper space as the archive. */
    private void randomSlip(GuiGraphics g) {
        g.pose().pushPose(); g.pose().translate(-690, 872, 0); g.pose().mulPose(Axis.ZP.rotationDegrees(-4));
        g.fill(4, 6, 314, 164, 0x33000000);
        g.fill(0, 0, 310, 158, slipHovered ? 0xFFFFFCED : 0xFFF3ECD7);
        g.renderOutline(0, 0, 310, 158, 0xFFA18D69);
        g.fill(15, 20, 295, 21, 0xFFB5A386);
        label(g, archive("random_draw"), 18, 32, 18, MUTED, 220);
        label(g, archive("random_all"), 18, 62, 30, INK, 220);
        label(g, archive("random_all_pool"), 18, 107, 18, MUTED, 270);
        label(g, Component.literal("?"), 258, 41, 44, 0xFF927D59, 42);
        for (int i = 0; i < 20; i++) g.fill(290 + i, 0, 310, 20 - i, 0xFFD4C6A8);
        g.pose().popPose();
    }
    private void confirmation(GuiGraphics g) {
        g.fill(22, 22, 752, 1072, 0xD8F7F2E5); g.fill(48, 288, 726, 842, 0xFFFDFBF5); g.renderOutline(48, 288, 678, 554, INK);
        label(g, archive("confirm_title"), 74, 318, 29, INK, 620);
        label(g, randomChoice >= 0 ? randomChoice == 0 ? archive("random_all") : categoryName(randomChoice).copy().append(" / ").append(archive("random_draw"))
                : ArchiveOriginModels.name(paper.category == 0 ? null : paper.detail), 74, 378, 27, INK, 620);
        scrollableText(g, randomChoice >= 0 ? archive(randomChoice == 0 ? "random_all_confirm" : "random_category_confirm")
                : text(paper.category == 0 ? "human_confirm" : "native_confirm"), 74, 442, 24, MUTED, 620, 718);
        button(g, 74, 758, 296, 42, text("back"), true); button(g, 393, 758, 307, 42, text("confirm"), true);
    }
    /** Whole-line scrolling keeps text inside the transformed paper without clipping model rendering. */
    private void scrollableText(GuiGraphics g, Component component, int x, int y, float size, int color, int width, int bottom) {
        if (wideGlyphs(component)) size = Math.max(26, size);
        float scale = size / 9;
        int lineHeight = Math.max(1, (int)(size * 1.35F));
        var lines = font.split(component, (int)((width - 16) / scale));
        dialogTextX = x; dialogTextY = y; dialogTextWidth = width; dialogTextBottom = bottom;
        dialogLines = lines.size();
        dialogVisibleLines = Math.max(1, (int)((bottom - y - size) / lineHeight) + 1);
        maxDialogScroll = Math.max(0, dialogLines - dialogVisibleLines);
        dialogScroll = Mth.clamp(dialogScroll, 0, maxDialogScroll);
        for (int i = dialogScroll; i < Math.min(dialogLines, dialogScroll + dialogVisibleLines); i++) {
            g.pose().pushPose(); g.pose().translate(x, y + (i - dialogScroll) * lineHeight, 0); g.pose().scale(scale, scale, 1);
            g.drawString(font, lines.get(i), 0, 0, color, false); g.pose().popPose();
        }
        if (maxDialogScroll > 0) {
            int h = bottom - y, thumb = Math.max(24, h * dialogVisibleLines / dialogLines);
            int sy = y + (h - thumb) * dialogScroll / maxDialogScroll;
            g.fill(x + width - 5, y, x + width - 1, bottom, 0xFFD7D3C8);
            g.fill(x + width - 5, sy, x + width - 1, sy + thumb, MUTED);
            label(g, archive("scroll_help"), x, bottom + 10, 18, MUTED, width);
        }
    }
    private void resetDialogScroll() {
        dialogScroll = maxDialogScroll = 0; draggingDialogScroll = false;
    }
    private boolean scrollDialogKey(int key) {
        int step = Math.max(1, dialogVisibleLines - 1);
        int next = switch (key) {
            case 264 -> dialogScroll + 1;
            case 265 -> dialogScroll - 1;
            case 266 -> dialogScroll - step;
            case 267 -> dialogScroll + step;
            case 268 -> 0;
            case 269 -> maxDialogScroll;
            default -> -1;
        };
        if (key < 264 || key > 269) return false;
        dialogScroll = Mth.clamp(next, 0, maxDialogScroll); return true;
    }
    private boolean dialogScrollAt(ArchiveGeometry.Point p) {
        return maxDialogScroll > 0 && inside(p.x(), p.y(), dialogTextX + dialogTextWidth - 13,
                dialogTextY, 16, dialogTextBottom - dialogTextY);
    }
    private void seekDialogScroll(double y) {
        int h = dialogTextBottom - dialogTextY, thumb = Math.max(24, h * dialogVisibleLines / dialogLines);
        dialogScroll = Mth.clamp((int)Math.round((y - dialogTextY - thumb / 2.0) * maxDialogScroll / Math.max(1, h - thumb)), 0, maxDialogScroll);
    }
    private void status(GuiGraphics g) {
        g.fill(22, 22, 752, 1072, 0xECF7F2E5); label(g, archive("preparing"), 58, 370, 30, INK, 645);
        wrapped(g, state.message(), 58, 430, 25, MUTED, 645, 730);
        int x = 58 + (int)((Util.getMillis() % 1400) * 536 / 1400);
        g.fill(58, 750, 703, 754, 0xFFD3CDBD); g.fill(x, 750, x + 100, 754, COLORS[paper.category]);
    }
    private void label(GuiGraphics g, Component component, double x, double y, float size, int color, double maxWidth) {
        if ((color >>> 24) < 4) return;
        if (wideGlyphs(component)) size = Math.max(26, size);
        float scale = size / 9;
        float shrink = Math.min(1, (float)maxWidth / Math.max(1, font.width(component) * scale));
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(scale * shrink, scale * shrink, 1);
        g.drawString(font, component, 0, 0, color, false); g.pose().popPose();
    }
    private void wrapped(GuiGraphics g, Component component, int x, int y, float size, int color, int width, int bottom) {
        wrapped(g, component, x, y, size, color, width, bottom, 1.35F);
    }
    private void wrapped(GuiGraphics g, Component component, int x, int y, float size, int color, int width, int bottom, float lineSpacing) {
        if (wideGlyphs(component)) size = Math.max(26, size);
        float scale = size / 9;
        var lines = font.split(component, (int)(width / scale));
        float minimum = Math.min(size, wideGlyphs(component) ? 20 : 14);
        // Brief model instructions and card captions fit their allotted space;
        // long explanatory paragraphs use the separate scrollable viewport.
        while (size > minimum && y + Math.max(0, lines.size() - 1) * (int)(size * lineSpacing) + size > bottom) {
            size -= .5F; scale = size / 9; lines = font.split(component, (int)(width / scale));
        }
        for (var line : lines) {
            if (y + size > bottom) break;
            g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(scale, scale, 1);
            g.drawString(font, line, 0, 0, color, false); g.pose().popPose(); y += (int)(size * lineSpacing);
        }
    }
    private static boolean wideGlyphs(Component component) {
        return component.getString().codePoints().anyMatch(c -> c >= 0x2E80 && c <= 0x9FFF || c >= 0xAC00 && c <= 0xD7AF);
    }
    private void button(GuiGraphics g, int x, int y, int w, int h, Component name, boolean enabled) {
        g.fill(x, y, x + w, y + h, enabled ? INK : MUTED); label(g, name, x + 13, y + 10, 21, 0xFFF9F5E9, w - 26);
    }
    private void tooltip(GuiGraphics g, Component component, int guiX, int guiY) {
        var canvas = fit.logical(guiX, guiY); int w = Math.min(420, (int)(font.width(component) * 2.5) + 26);
        int x = Mth.clamp((int)canvas.x() + 20, 12, 1908 - w), y = Mth.clamp((int)canvas.y() + 15, 12, 1035);
        g.fill(x, y, x + w, y + 38, 0xFF242323); label(g, component, x + 12, y + 9, 21, 0xFFF9F5E9, w - 24);
    }
    private ArchiveGeometry.Point local(double guiX, double guiY) {
        if (fit == null) fit = ArchiveGeometry.fit(width, height);
        return ArchiveGeometry.canvasToPaper(fit.logical(guiX, guiY), new ArchiveGeometry.Point(pose.x, pose.y + recoil), Math.toRadians(pose.angle), pose.scale);
    }
    private static boolean inside(double x, double y, double left, double top, double w, double h) {
        return x >= left && x < left + w && y >= top && y < top + h;
    }
    private int tab(ArchiveGeometry.Point point) {
        for (int i = 0; i < 6; i++) {
            int side = i < paper.category ? -1 : 1; double x = side > 0 ? PAPER_W : -PAPER_W - (i == 0 ? 58 : 54);
            if (inside(point.x(), point.y(), x, i == 0 ? 82 : 300 + (i - 1) * 142, i == 0 ? 58 : 54, i == 0 ? 146 : 120)) return i;
        }
        return -1;
    }
    private boolean corner(ArchiveGeometry.Point p, int side) {
        if (paper.category == 0) return false;
        if (paper.detail != null) { if (side > 0) return false; }
        else if (side < 0 && paper.number == 0 || side > 0 && paper.number + 1 >= pageCount(paper.category)) return false;
        double x = side > 0 ? PAPER_W - 200 : -PAPER_W;
        return paper.detail == null && inside(p.x(), p.y(), side < 0 ? 16 : 712, PAGE_ARROW_Y - 4, 48, 35)
                || inside(p.x(), p.y(), x, PAPER_H - 66, 200, 90)
                || inside(p.x(), p.y(), side > 0 ? PAPER_W - 50 : -PAPER_W, PAPER_H - 120, 50, 120);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (locked()) return true;
        if (key == 256) { escape(); return true; }
        if (confirmation) { if (key == 257 || key == 335) submit(false); else scrollDialogKey(key); return true; }
        if (state.phase() == 2 && scrollDialogKey(key)) return true;
        if (view == View.WELCOME) { beginMotion(Motion.INTRO); return true; }
        if (view == View.DESKTOP && (key == 257 || key == 335 || key == 32)) { beginMotion(Motion.OPEN); return true; }
        if (view == View.FILE) {
            if (key == 258) { category(paper.category + ((modifiers & 1) == 0 ? 1 : -1)); return true; }
            if (key == 263) { page(paper.number - 1); return true; }
            if (key == 262) { page(paper.number + 1); return true; }
            if ((key == 264 || key == 265) && paper.category != 0 && paper.detail == null) {
                int count = Math.min(12, categories.get(paper.category).size() + 1 - paper.number * 12);
                if (count > 0) focusedRecord = focusedRecord < 0 ? 0 : Math.floorMod(focusedRecord + (key == 264 ? 3 : -3), count);
                return true;
            }
            if ((key == 257 || key == 335) && paper.category != 0 && paper.detail == null) {
                int index = paper.number * 12 + Math.max(0, focusedRecord);
                openRecord(index);
                return true;
            }
            if ((key == 257 || key == 335) && canChoose()) { openConfirmation(-1); return true; }
            if (key == 32 && (paper.category == 0 || paper.detail != null)) { previewCrouch = !previewCrouch; ArchiveUiSounds.click(); return true; }
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean mouseClicked(double guiX, double guiY, int button) {
        if (button == 0 && guiY >= height - 20 && guiX >= width - font.width(exitLabel()) - 20) { disconnect(); return true; }
        if (locked()) return true;
        var p = local(guiX, guiY);
        if (view == View.WELCOME) { if (button == 0) beginMotion(Motion.INTRO); return true; }
        if (view == View.DESKTOP) {
            if (button == 0 && inside(p.x(), p.y(), -24, -26, 866, PAPER_H + 126)) beginMotion(Motion.OPEN);
            return true;
        }
        if (confirmation) {
            if (button == 0 && dialogScrollAt(p)) { draggingDialogScroll = true; seekDialogScroll(p.y()); return true; }
            if (button == 0 && inside(p.x(), p.y(), 74, 758, 296, 42)) { confirmation = false; randomChoice = -1; resetDialogScroll(); ArchiveUiSounds.click(); }
            else if (button == 0 && inside(p.x(), p.y(), 393, 758, 307, 42)) submit(false);
            return true;
        }
        if (button == 0 && paper.category == 0 && randomSlipAt(p)) { openConfirmation(0); return true; }
        if (noHabitat() && button == 0 && inside(p.x(), p.y(), 59, 958, 644, 42)) { submit(true); return true; }
        if (state.phase() == 2 && button == 0 && dialogScrollAt(p)) { draggingDialogScroll = true; seekDialogScroll(p.y()); return true; }
        if (paper.category == 0 || paper.detail != null) {
            if (inside(p.x(), p.y(), 44, 135, 415, 427)) {
                if (button == 1) { previewCrouch = !previewCrouch; ArchiveUiSounds.click(); } else if (button == 0) dragging = true;
                return true;
            }
            if (button == 0 && inside(p.x(), p.y(), 44, 1021, 686, 42)) { openConfirmation(-1); return true; }
            if (button == 0 && paper.detail != null && inside(p.x(), p.y(), 44, 1070, 686, 25)) { returnToRoster(); return true; }
        }
        int index = tab(p);
        if (button == 0 && index >= 0) { category(index); return true; }
        int edgePage = ArchiveGeometry.stackPageAt(p, paper.number, pageCount(paper.category), paper.detail != null, fanLeft, fanRight);
        if (button == 0 && edgePage >= 0) { if (paper.detail != null) returnToRoster(); else page(edgePage); return true; }
        if (button == 0 && corner(p, -1)) { if (paper.detail != null) returnToRoster(); else page(paper.number - 1); return true; }
        if (button == 0 && corner(p, 1)) { page(paper.number + 1); return true; }
        if (button == 0 && paper.category != 0 && paper.detail == null) {
            var entries = categories.get(paper.category);
            for (int i = 0; i < 12; i++) if (inside(p.x(), p.y(), 44 + i % 3 * 233, 122 + i / 3 * 236, 214, 219)) {
                int n = paper.number * 12 + i;
                if (n < entries.size() + 1) openRecord(n);
                return true;
            }
        }
        if (button == 0 && !inside(p.x(), p.y(), -835, -45, 1675, PAPER_H + 155)) closeFolder();
        return true;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (draggingDialogScroll && button == 0 && !locked()) { seekDialogScroll(local(x, y).y()); return true; }
        if (dragging && button == 0 && !locked()) {
            double scale = Math.max(.05, fit.scale() * pose.scale);
            yaw += (float)(dx / scale * .25); pitch = Mth.clamp(pitch + (float)(dy / scale * .20), -45, 45); return true;
        }
        return locked() || super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) { dragging = draggingDialogScroll = false; return true; }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (locked() || view != View.FILE) return true;
        var p = local(x, y);
        if (confirmation || state.phase() == 2 && inside(p.x(), p.y(), 38, 736, 698, 275)) {
            if (inside(p.x(), p.y(), dialogTextX, dialogTextY, dialogTextWidth, dialogTextBottom - dialogTextY))
                dialogScroll = Mth.clamp(dialogScroll - (int)Math.signum(delta) * 2, 0, maxDialogScroll);
            return true;
        }
        if (paper.category == 0 || paper.detail != null) {
            if (inside(p.x(), p.y(), 44, 135, 415, 427)) zoom = Mth.clamp(zoom + (float)delta * .08f, .5f, 2.25f);
            else if (inside(p.x(), p.y(), 44, 640, 686, 362)) detailScroll = Mth.clamp(detailScroll - (int)Math.signum(delta), 0, maxDetailScroll);
        } else page(paper.number + (delta < 0 ? 1 : -1));
        return true;
    }
}
