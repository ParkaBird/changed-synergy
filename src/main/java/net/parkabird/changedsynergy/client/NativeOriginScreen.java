package net.parkabird.changedsynergy.client;

import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
    private static final ResourceLocation RANDOM_NOTE = ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/gui/origin_archive/random_note.png");
    private static final ResourceLocation BUTTON_TEX = ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/gui/origin_archive/button.png");
    private static final ResourceLocation PORTRAIT_FRAME = ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/gui/origin_archive/portrait_frame.png");
    private static final ResourceLocation DOLL_FRAME = ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/gui/origin_archive/doll_frame.png");
    private static final ResourceLocation PARAMETER_FRAME = ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/gui/origin_archive/parameter_frame.png");
    private static final ResourceLocation SCROLLBAR_TEX = ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/gui/origin_archive/scrollbar.png");
    private static final ResourceLocation DIALOG_FRAME = ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/gui/origin_archive/dialog_frame.png");
    private static final ResourceLocation TOOLTIP_TEX = ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/gui/origin_archive/tooltip.png");
    private static final ResourceLocation WELCOME_DECORATION = ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/gui/origin_archive/welcome_decoration.png");
    private static final ResourceLocation NAVIGATION_TEX = ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/gui/origin_archive/navigation.png");
    private enum View { WELCOME, DESKTOP, FILE }
    private enum Motion { INTRO, OUTRO, OPEN, CLOSE }
    private record Page(int category, int number, Entry detail) {}
    private static final Page HUMAN_PAGE = new Page(0, 0, null);
    private record FolderPose(double x, double y, double angle, double scale) {
        FolderPose mix(FolderPose other, double amount) {
            double t = UiMotion.transition(amount);
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
        long capturedAt;
        boolean captured;
        Turn(Page from, Page to, int direction, double duration) {
            this.from = from; this.to = to; this.direction = direction; this.duration = duration;
        }
        double elapsed() { return (Util.getMillis() - begun) / 1000.0; }
        double fraction(long now){return captured?Math.max(0,(now-capturedAt)/1000.0/duration):0;}
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
    private double open, desktopAlpha, welcomeAlpha = 1, hoverWeight;
    private double fanLeft, fanRight;
    private final UiSpring folderHover = new UiSpring(0), leftFanMotion = new UiSpring(0), rightFanMotion = new UiSpring(0);
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
    private ArchiveGeometry.Point currentPaperMouse = new ArchiveGeometry.Point(-9999, -9999);
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
    }
    private void updateMotion(double delta) {
        if (UiMotion.reduced() && motion != null) {
            view = switch (motion) { case INTRO, CLOSE -> View.DESKTOP; case OPEN -> View.FILE; case OUTRO -> View.WELCOME; };
            motion = null; open = view == View.FILE ? 1 : 0;
            pose = view == View.FILE ? FOCUS : view == View.DESKTOP ? DESKTOP : HIDDEN;
            welcomeAlpha = view == View.WELCOME ? 1 : 0; desktopAlpha = view == View.DESKTOP ? 1 : 0;
        }
        if (motion == null) {
            if (view == View.DESKTOP) {
                folderHover.target(folderHovered && !UiMotion.reduced() ? 1 : 0, System.nanoTime());
                hoverWeight = folderHover.value(System.nanoTime());
                pose = new FolderPose(DESKTOP.x, DESKTOP.y - 12 * hoverWeight, DESKTOP.angle, DESKTOP.scale);
            }
            return;
        }
        double t = (Util.getMillis() - motionBegan) / 1000.0, end;
        switch (motion) {
            case INTRO -> {
                pose = HIDDEN.mix(DESKTOP, t / .78); open = 0;
                welcomeAlpha = 1 - UiMotion.transition(t / .5);
                desktopAlpha = UiMotion.transition((t - .22) / .56);
                settle(t, .78); end = .92;
            }
            case OUTRO -> {
                pose = DESKTOP.mix(HIDDEN, t / .7); open = 0;
                desktopAlpha = 1 - UiMotion.transition(t / (.7 * .65));
                welcomeAlpha = UiMotion.transition((t / .7 - .22) / .78); end = .7;
            }
            case OPEN -> {
                pose = DESKTOP.mix(FOCUS, t / TRAVEL); welcomeAlpha = 0;
                desktopAlpha = 1 - UiMotion.transition(t / TRAVEL);
                settle(t, TRAVEL); open = UiMotion.transition((t - TRAVEL - .14) / .76); end = TRAVEL + .90;
            }
            case CLOSE -> {
                open = 1 - UiMotion.transition(t / .66);
                pose = FOCUS.mix(DESKTOP, (t - .66) / TRAVEL); welcomeAlpha = 0;
                desktopAlpha = UiMotion.transition((t - .66) / TRAVEL);
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
    private void resetDoll() { yaw = pitch = 0; zoom = 1; previewCrouch = false; detailScroll = 0; }
    private void changePage(Page next, int direction, double duration) {
        if (locked() || next.equals(paper)) return;
        confirmation = false; dragging = false; randomChoice = -1;
        resetDialogScroll();
        if (state.phase() == 2) state = new NativeOriginStatePacket(0, state.nonce(), state.available(), state.canCancel(), Component.empty(), state.entries());
        focusedRecord = -1;
        closeAfterTurn = false;
        if (UiMotion.reduced()) {
            paper = next; papers.finishTransition(); if (next.detail != null || next.category == 0) resetDoll();
        } else turn = new Turn(paper, next, direction, duration);
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
        if (state.canCancel()) { exitSelection(); return; }
        switch (view) {
            case WELCOME -> exitSelection();
            case DESKTOP -> beginMotion(Motion.OUTRO);
            case FILE -> { if (paper.detail != null) returnToRoster(); else closeFolder(); }
        }
    }
    private void exitSelection() {
        if (state.canCancel()) {
            ChangedSynergyNetwork.CHANNEL.sendToServer(new NativeOriginChoosePacket(state.nonce(), null, false, true));
            state = new NativeOriginStatePacket(1, state.nonce(), state.available(), true, text("cancelling"), state.entries());
            return;
        }
        ChangedSynergyNetwork.CHANNEL.sendToServer(new NativeOriginChoosePacket(state.nonce(), null, false, false));
        state = new NativeOriginStatePacket(1, state.nonce(), state.available(), false, text("choosing_human"), state.entries());
    }
    private Component exitLabel() { return text(state.canCancel() ? "cancel_selection" : "default_human"); }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        double delta = Math.min(.05, (now - lastFrame) / 1000.0); lastFrame = now;
        updateMotion(delta);
        var localMouse = local(mouseX, mouseY);
        currentPaperMouse = localMouse;
        slipHovered = view == View.FILE && paper.category == 0 && !locked() && !confirmation && randomSlipAt(localMouse);
        if (view == View.DESKTOP && motion == null) {
            boolean hover = inside(localMouse.x(), localMouse.y(), -24, -26, 866, PAPER_H + 126);
            if (hover && !folderHovered) ArchiveUiSounds.hover();
            folderHovered = hover;
        }
        papers.hoverFolder(folderHovered);
        papers.hoverTab(view == View.FILE && !locked() && !confirmation ? tab(localMouse) : -1);
        boolean canFan = view == View.FILE && !locked() && !confirmation && paper.category != 0;
        boolean leftFan = canFan && (corner(localMouse, -1) || ArchiveGeometry.stackPageAt(localMouse, paper.number, pageCount(paper.category), paper.detail != null, fanLeft, 0) >= 0);
        boolean rightFan = canFan && (corner(localMouse, 1) || ArchiveGeometry.stackPageAt(localMouse, paper.number, pageCount(paper.category), paper.detail != null, 0, fanRight) >= 0);
        long fanNow = System.nanoTime();
        leftFanMotion.target(leftFan ? 1 : 0, fanNow); rightFanMotion.target(rightFan ? 1 : 0, fanNow);
        if (UiMotion.reduced()) { leftFanMotion.snap(leftFan ? 1 : 0, fanNow); rightFanMotion.snap(rightFan ? 1 : 0, fanNow); }
        fanLeft = UiSpring.clamp(leftFanMotion.value(fanNow)); fanRight = UiSpring.clamp(rightFanMotion.value(fanNow));
        int hoverPage = canFan ? ArchiveGeometry.stackPageAt(localMouse, paper.number, pageCount(paper.category), paper.detail != null, fanLeft, fanRight) : -1;
        papers.hoverPage(hoverPage);
        if (turn != null && !turn.captured && turn.elapsed() >= .12) {
            Turn active = turn;
            graphics.flush();
            papers.transition(active.from.category, active.to.category);
            Page reverse = active.direction > 0 ? active.to : active.from;
            papers.capture(minecraft, snapshot -> content(snapshot, active.direction > 0 ? active.from : active.to, false),
                    (reverse.category != 0 && hasLogNotes(reverse)) ? snapshot -> {
                        snapshot.pose().pushPose(); snapshot.pose().translate(PAPER_W, 0, 0);
                        logNotes(snapshot, reverse); snapshot.pose().popPose();
                    } : null);
            active.capturedAt = now = Util.getMillis();
            active.captured = true; ArchiveUiSounds.page();
        }
        double turnFraction=turn==null?0:turn.fraction(now);
        if (turn != null && turn.captured && turnFraction >= 1) {
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
            graphics.pose().pushPose(); graphics.pose().translate(pose.x, pose.y, 0);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees((float)pose.angle));
            graphics.pose().scale((float)pose.scale, (float)pose.scale, 1);
            graphics.pose().translate(-PAPER_W / 2, -PAPER_H / 2, 0);
            Page source = turn == null ? paper : turn.captured ? turn.to : turn.from;
            // The turning sheet supplies the new left page; it is not already resting there.
            Page left = turn != null && turn.captured && turn.direction > 0 ? turn.from : source;
            double turnProgress=ArchiveGeometry.turnProgress(turnFraction);
            papers.progress(turn == null || !turn.captured ? 0 : turnProgress);
            if (!ArchivePaperRenderer.coverInFront(open)) papers.cover(graphics, open, this::insideCover);
            papers.shell(graphics, open, source.category, COLORS, source.number, pageCount(source.category), source.detail != null,
                    left.detail != null ? 1 : left.number, turn == null ? fanLeft : 0, turn == null ? fanRight : 0);
            if (left.category != 0 && hasLogNotes(left)) {
                graphics.pose().pushPose();
                graphics.pose().scale((float)-Math.cos(Math.PI * UiMotion.transition((open - .65) / .35)), 1, 1);
                logNotes(graphics, left); graphics.pose().popPose();
            }
            // Resting paper stays printed; the moving cover provides the occlusion.
            Page shown = turn != null && turn.captured && turn.direction > 0 ? turn.to : paper;
            content(graphics, shown, turn == null && !locked());
            papers.binding(graphics, open);
            if (turn != null && turn.captured) {
                Page back = turn.direction > 0 ? turn.to : turn.from;
                int stock = turn.from.category == turn.to.category || back.detail != null || back.number > 0
                        ? 0xFFFDFBF5 : back.category > 0 ? COLORS[back.category - 1] : 0xFFDAC189;
                papers.curl(graphics, turnProgress,
                        turn.direction, turn.from.category, turn.to.category, COLORS, stock);
            }
            if (ArchivePaperRenderer.coverInFront(open)) papers.cover(graphics, open, this::insideCover);
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
    private static void blitRegion(GuiGraphics g, ResourceLocation tex, double x, double y, double w, double h,
                                   double u, double v, double uw, double vh, double texW, double texH, int tint) {
        if (w <= 0 || h <= 0) return;
        ArchivePaperRenderer.spriteQuad(g, x, y, x + w, y + h, u / texW, v / texH, (u + uw) / texW, (v + vh) / texH,
                tint, ArchivePaperRenderer.textureId(tex));
    }
    private static void blitNineSlice(GuiGraphics g, ResourceLocation tex, double x, double y, double w, double h,
                                      double u, double v, double uw, double vh, double slice, double texW, double texH, int tint) {
        double s = Math.min(slice, Math.min(w * 0.5, h * 0.5));
        double cw = Math.max(0, w - s * 2), ch = Math.max(0, h - s * 2);
        double cuw = uw - slice * 2, cvh = vh - slice * 2;
        blitRegion(g, tex, x, y, s, s, u, v, slice, slice, texW, texH, tint);
        blitRegion(g, tex, x + s, y, cw, s, u + slice, v, cuw, slice, texW, texH, tint);
        blitRegion(g, tex, x + w - s, y, s, s, u + uw - slice, v, slice, slice, texW, texH, tint);
        blitRegion(g, tex, x, y + s, s, ch, u, v + slice, slice, cvh, texW, texH, tint);
        blitRegion(g, tex, x + s, y + s, cw, ch, u + slice, v + slice, cuw, cvh, texW, texH, tint);
        blitRegion(g, tex, x + w - s, y + s, s, ch, u + uw - slice, v + slice, slice, cvh, texW, texH, tint);
        blitRegion(g, tex, x, y + h - s, s, s, u, v + vh - slice, slice, slice, texW, texH, tint);
        blitRegion(g, tex, x + s, y + h - s, cw, s, u + slice, v + vh - slice, cuw, slice, texW, texH, tint);
        blitRegion(g, tex, x + w - s, y + h - s, s, s, u + uw - slice, v + vh - slice, slice, slice, texW, texH, tint);
    }
    private static void blitFrameBorder(GuiGraphics g, ResourceLocation tex, double x, double y, double w, double h,
                                        double u, double v, double uw, double vh, double slice, double texW, double texH, int tint) {
        double s = Math.min(slice, Math.min(w * 0.5, h * 0.5));
        double cw = Math.max(0, w - s * 2), ch = Math.max(0, h - s * 2);
        double cuw = uw - slice * 2, cvh = vh - slice * 2;
        blitRegion(g, tex, x, y, s, s, u, v, slice, slice, texW, texH, tint);
        blitRegion(g, tex, x + s, y, cw, s, u + slice, v, cuw, slice, texW, texH, tint);
        blitRegion(g, tex, x + w - s, y, s, s, u + uw - slice, v, slice, slice, texW, texH, tint);
        blitRegion(g, tex, x, y + s, s, ch, u, v + slice, slice, cvh, texW, texH, tint);
        blitRegion(g, tex, x + w - s, y + s, s, ch, u + uw - slice, v + slice, slice, cvh, texW, texH, tint);
        blitRegion(g, tex, x, y + h - s, s, s, u, v + vh - slice, slice, slice, texW, texH, tint);
        blitRegion(g, tex, x + s, y + h - s, cw, s, u + slice, v + vh - slice, cuw, slice, texW, texH, tint);
        blitRegion(g, tex, x + w - s, y + h - s, s, s, u + uw - slice, v + vh - slice, slice, slice, texW, texH, tint);
    }
    private void welcome(GuiGraphics g, double opacity) {
        double age = (Util.getMillis() - welcomeBegan) / 1000.0;
        double prompt = UiMotion.transition((age - .24) / .45);
        g.pose().pushPose();
        g.pose().translate(0, 18 * (1 - UiMotion.transition(age / .45)) - 70 * (1 - opacity), 0);
        label(g, archive("eyebrow"), 174, 270, 23, alpha(0xFF888888, opacity * UiMotion.transition(age / .45)), 1550);
        label(g, archive("welcome").copy().withStyle(net.minecraft.ChatFormatting.BOLD), 162, 370, 100, alpha(0xFFFFFFFF, opacity * UiMotion.transition((age - .06) / .45)), 1470);
        label(g, archive("subtitle"), 180, 612, 49, alpha(0xFFFFFFFF, opacity * UiMotion.transition((age - .12) / .45)), 1500);
        label(g, archive("intro"), 182, 690, 27, alpha(0xFF949494, opacity * UiMotion.transition((age - .18) / .45)), 1500);
        blitRegion(g, WELCOME_DECORATION, 162, 773, 240 * prompt, 24, 0, 0, 360, 24, 360, 24, alpha(0xFFFFFFFF, opacity * prompt));
        label(g, archive("enter"), 182, 806, 24, alpha(0xFFCCCCCC, opacity * prompt), 1400);
        g.pose().popPose();
    }
    private void desktop(GuiGraphics g, double opacity) {
        g.pose().pushPose(); g.pose().translate(0, 30 * (1 - opacity), 0);
        label(g, Component.literal("ARCHIVE").withStyle(net.minecraft.ChatFormatting.BOLD), 158, 108, 80, alpha(0xFFFFFFFF, opacity), 1000);
        label(g, archive("subtitle"), 162, 210, 30, alpha(0xFFAAAAAA, opacity), 1000);
        label(g, archive("folder_count", state.entries().size()), 1250, 170, 20, alpha(0xFF999999, opacity), 540);
        g.fill(160, 280, 1770, 282, alpha(0xFF404040, opacity));
        blitRegion(g, WELCOME_DECORATION, 785, 269, 360, 24, 0, 0, 360, 24, 360, 24, alpha(0xFFFFFFFF, opacity));
        label(g, archive("open"), 162, 960, 22, alpha(0xFF999999, opacity), 1000);
        g.pose().popPose();
    }
    private void content(GuiGraphics g, Page page, boolean interactive) {
        if (page.category == 0 || page.detail != null) dossier(g, page, interactive); else roster(g, page, interactive);
    }
    private record LogNoteConfig(String prefix, List<String> departments) {}

    private static int departmentColor(String department) {
        return switch (department) {
            case "science" -> 0xFF08639C;
            case "technology" -> 0xFF2C7572;
            case "humanity" -> 0xFF944549;
            case "society" -> 0xFF9A7835;
            default -> 0xFF1F2529;
        };
    }

    private static LogNoteConfig logNoteFor(Page page) {
        if (page == null) return null;
        if (page.category == 0) {
            return new LogNoteConfig("human_notes", List.of("technology", "science", "humanity", "society"));
        }
        if (page.detail == null) return null;
        ResourceLocation form = page.detail.form();
        if (form.equals(net.parkabird.changedsynergy.init.ChangedSynergyTransfurVariants.LATEX_SILK_MOTH.getId())) {
            return new LogNoteConfig("silk_moth_notes", List.of("science", "technology", "humanity", "society"));
        }
        if (form.equals(net.parkabird.changedsynergy.init.ChangedSynergyTransfurVariants.LATEX_SWORDFISH.getId())) {
            return new LogNoteConfig("swordfish_notes", List.of("science", "society", "humanity", "technology"));
        }
        if (form.equals(net.parkabird.changedsynergy.init.ChangedSynergyTransfurVariants.LATEX_EAGLE_OWL.getId())) {
            return new LogNoteConfig("eagle_owl_notes", List.of("science", "technology", "humanity", "society"));
        }
        if (form.equals(net.parkabird.changedsynergy.init.ChangedSynergyTransfurVariants.LATEX_MUNTJAC.getId())) {
            return new LogNoteConfig("muntjac_male_notes", List.of("science", "technology", "humanity", "society"));
        }
        if (form.equals(net.parkabird.changedsynergy.init.ChangedSynergyTransfurVariants.LATEX_MUNTJAC_FEMALE.getId())) {
            return new LogNoteConfig("muntjac_female_notes", List.of("science", "technology", "humanity", "society"));
        }
        if (form.equals(net.parkabird.changedsynergy.init.ChangedSynergyTransfurVariants.LATEX_SPOTTED_HYENA_MALE.getId())) {
            return new LogNoteConfig("spotted_hyena_male_notes", List.of("humanity", "science", "technology", "society"));
        }
        if (form.equals(net.parkabird.changedsynergy.init.ChangedSynergyTransfurVariants.LATEX_SPOTTED_HYENA_FEMALE.getId())) {
            return new LogNoteConfig("spotted_hyena_female_notes", List.of("science", "society", "humanity", "technology"));
        }
        return null;
    }

    private static boolean hasLogNotes(Page page) {
        return logNoteFor(page) != null;
    }

    private void logNotes(GuiGraphics g, Page page) {
        LogNoteConfig note = logNoteFor(page);
        if (note == null) return;
        g.pose().pushPose(); g.pose().translate(-658, 268, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(-2));
        blitRegion(g, ArchivePaperRenderer.PAGE_SHADOW, 5, 7, 550, 442, 0, 0, 64, 64, 128, 64, 0x55FFFFFF);
        blitRegion(g, RANDOM_NOTE, 0, 0, 550, 442, 0, 330, 550, 442, 550, 780, 0xFFFFFFFF);
        blitRegion(g, RANDOM_NOTE, 188, -12, 172, 26, 320, 0, 172, 26, 550, 780, 0xFFFFFFFF);
        label(g, archive("silk_moth_notes.title").copy().withStyle(net.minecraft.ChatFormatting.BOLD),
                29, 40, 24, 0xFF1F2529, 492);
        blitRegion(g, PARAMETER_FRAME, 29, 79, 492, 6, 0, 0, 128, 6, 128, 32, 0xFFFFFFFF);

        renderLogNoteEntries(g, note);

        g.pose().popPose();
    }

    private void renderLogNoteEntries(GuiGraphics g, LogNoteConfig note) {
        int x = 29, startY = 100, bottom = 422, width = 492;
        List<Component> components = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (String dept : note.departments()) {
            components.add(archive(note.prefix() + "." + dept));
            colors.add(departmentColor(dept));
        }

        boolean anyWide = components.stream().anyMatch(NativeOriginScreen::wideGlyphs);
        float size = anyWide ? 24.0F : 21.0F;
        float minSize = anyWide ? 16.0F : 13.5F;
        float lineSpacing = 1.25F;
        float entryGap = 9.0F;

        while (size > minSize) {
            float totalH = 0;
            float scale = size / 9.0F;
            int splitWidth = Math.max(1, (int)(width / scale));
            for (int i = 0; i < components.size(); i++) {
                int lineCount = font.split(components.get(i), splitWidth).size();
                totalH += lineCount * (size * lineSpacing);
                if (i < components.size() - 1) totalH += entryGap;
            }
            if (startY + totalH <= bottom) break;
            size -= 0.5F;
        }

        float scale = size / 9.0F;
        int splitWidth = Math.max(1, (int)(width / scale));
        float currentY = startY;

        for (int i = 0; i < components.size(); i++) {
            Component comp = components.get(i);
            int color = colors.get(i);
            var lines = font.split(comp, splitWidth);
            for (var line : lines) {
                if (currentY + size > bottom) break;
                g.pose().pushPose();
                g.pose().translate(x, (int)currentY, 0);
                g.pose().scale(scale, scale, 1);
                g.drawString(font, line, 0, 0, color, false);
                g.pose().popPose();
                currentY += size * lineSpacing;
            }
            currentY += entryGap;
        }
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
            boolean cellHovered = interactive && !confirmation && (cell == focusedRecord
                    || inside(currentPaperMouse.x(), currentPaperMouse.y(), x, y, 214, 219));
            boolean cellDisabled = !state.available() || (record == 0 && (entries.isEmpty() || page.category == 1));
            int frameV = cellDisabled ? 288 : (cellHovered ? 96 : 0);
            blitNineSlice(g, PORTRAIT_FRAME, x - 2, y - 2, 218, 196, 0, frameV, 96, 96, 12, 96, 384, 0xFFFFFFFF);
            if (record == 0) {
                Component symbol = Component.literal("?");
                float size = 104, scale = size / 9;
                // Center the visible vanilla glyph, excluding its advance and line padding.
                label(g, symbol, x + (214 - (font.width(symbol) - 1) * scale) / 2, y + (192 - 7 * scale) / 2, size,
                        entries.isEmpty() || !state.available() || page.category == 1 ? MUTED : COLORS[page.category], 214);
                if (wideGlyphs(archive("random_category"))) label(g, archive("random_category"), x, y + 196, 26, INK, 214);
                else wrapped(g, archive("random_category"), x, y + 196, 18, INK, 214, y + 241, 1.12F);
                if (cellHovered) blitFrameBorder(g, PORTRAIT_FRAME, x - 3, y - 3, 220, 198, 0, 96, 96, 96, 12, 96, 384, 0xFFFFFFFF);
                continue;
            }
            Entry entry = entries.get(index);
            models.renderPortrait(g, entry, x, y, 214, 192);
            blitFrameBorder(g, PORTRAIT_FRAME, x - 2, y - 2, 218, 196, 0, frameV, 96, 96, 12, 96, 384, 0xFFFFFFFF);
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
            if (page.number > 0) {
                boolean hoverPrev = interactive && corner(currentPaperMouse, -1);
                blitRegion(g, NAVIGATION_TEX, 18, PAGE_ARROW_Y - 2, 36, 24, 0, hoverPrev ? 24 : 0, 36, 24, 72, 72, 0xFFFFFFFF);
            }
            if (page.number + 1 < pageCount(page.category)) {
                boolean hoverNext = interactive && corner(currentPaperMouse, 1);
                blitRegion(g, NAVIGATION_TEX, 714, PAGE_ARROW_Y - 2, 36, 24, 36, hoverNext ? 24 : 0, 36, 24, 72, 72, 0xFFFFFFFF);
            }
        }
    }
    private void dossier(GuiGraphics g, Page page, boolean interactive) {
        boolean human = page.category == 0; Entry entry = human ? null : page.detail;
        boolean active = page.equals(paper);
        label(g, human ? archive("priority") : categoryName(page.category).copy().append(" / ").append(archive("dossier")), 44, 44, 22, MUTED, 686);
        label(g, ArchiveOriginModels.name(entry).copy().withStyle(net.minecraft.ChatFormatting.BOLD), 44, 80, 32, INK, 686);
        blitNineSlice(g, DOLL_FRAME, 42, 133, 419, 431, 0, 0, 128, 128, 16, 128, 128, 0xFFFFFFFF);
        models.renderDoll(g, entry, 44, 135, 415, 427, active ? yaw : 0, active ? pitch : 0, active ? zoom : 1, active && previewCrouch);
        blitFrameBorder(g, DOLL_FRAME, 42, 133, 419, 431, 0, 0, 128, 128, 16, 128, 128, 0xFFFFFFFF);
        label(g, archive(human ? "human_origin" : "native_origin"), 487, 158, 26, INK, 239);
        wrapped(g, archive("model_controls"), 487, 214, 25, MUTED, 239, 385);
        if (entry != null) {
            if (!state.available()) wrapped(g, text("missing_backend"), 487, 480, 18, 0xFF945246, 239, 558);
        }
        blitRegion(g, PARAMETER_FRAME, 44, 582, 686, 6, 0, 0, 128, 6, 128, 32, 0xFFFFFFFF);
        label(g, archive("parameters"), 44, 604, 25, INK, 620);
        List<ArchiveOriginModels.Field> fields = models.fields(entry);
        int top = 651, bottom = 991, rowH = 47, count = (bottom - top) / rowH;
        int maxScroll = Math.max(0, fields.size() - count);
        int first = active ? Mth.clamp(detailScroll, 0, maxScroll) : 0;
        if (interactive) { maxDetailScroll = maxScroll; detailScroll = first; }
        for (int i = first; i < Math.min(fields.size(), first + count); i++) {
            var field = fields.get(i); int y = top + (i - first) * rowH;
            label(g, field.label(), 44, y, 24, MUTED, 240); label(g, field.value(), 291, y, 24, INK, 411);
            if (field.ratio() >= 0) {
                blitRegion(g, PARAMETER_FRAME, 291, y + 27, 414, 6, 0, 8, 128, 8, 128, 32, 0xFFFFFFFF);
                int fillW = (int)(414 * Mth.clamp(field.ratio(), 0, 1));
                if (fillW > 0) blitRegion(g, PARAMETER_FRAME, 291, y + 27, fillW, 6, 0, 18, 128 * Mth.clamp(field.ratio(), 0, 1), 8, 128, 32, field.color());
            } else blitRegion(g, PARAMETER_FRAME, 44, y + 32, 661, 4, 0, 0, 128, 6, 128, 32, 0xFFFFFFFF);
        }
        if (maxScroll > 0) {
            int h = bottom - top, thumb = Math.max(24, h * count / fields.size());
            int sy = top + (h - thumb) * first / maxScroll;
            blitRegion(g, SCROLLBAR_TEX, 724, top, 6, bottom - top, 0, 0, 8, 64, 32, 64, 0xFFFFFFFF);
            blitRegion(g, SCROLLBAR_TEX, 724, sy, 6, thumb, 8, 0, 8, 64, 32, 64, 0xFFFFFFFF);
        }
        button(g, 44, 1021, 686, 42, archive(human ? "choose_human" : "choose_native"), human || state.available());
        if (!human) label(g, archive(state.canCancel() ? "return_register_optional" : "return_register"), 44, 1070, 15, MUTED, 620);
    }
    private void notice(GuiGraphics g) {
        blitNineSlice(g, DIALOG_FRAME, 38, 736, 698, 275, 0, 0, 128, 128, 16, 128, 128, 0xFFFFFFFF);
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
    private void insideCover(GuiGraphics g) {
        randomSlip(g);
        logNotes(g, HUMAN_PAGE);
    }
    /** A folded raffle slip attached to the inside cover, in the same paper space as the archive. */
    private void randomSlip(GuiGraphics g) {
        g.pose().pushPose(); g.pose().translate(-690, 872, 0); g.pose().mulPose(Axis.ZP.rotationDegrees(-4));
        blitRegion(g, ArchivePaperRenderer.PAGE_SHADOW, 4, 6, 310, 158, 0, 0, 64, 64, 128, 64, 0x66FFFFFF);
        blitRegion(g, RANDOM_NOTE, 0, 0, 310, 158, 0, slipHovered ? 160 : 0, 310, 158, 550, 780, 0xFFFFFFFF);
        label(g, archive("random_draw"), 30, 28, 18, MUTED, 210);
        label(g, archive("random_all"), 18, 62, 30, INK, 220);
        label(g, archive("random_all_pool"), 18, 107, 18, MUTED, 270);
        label(g, Component.literal("?"), 258, 41, 44, 0xFF927D59, 42);
        g.pose().popPose();
    }
    private void confirmation(GuiGraphics g) {
        g.fill(22, 22, 752, 1072, 0xD8F7F2E5);
        blitNineSlice(g, DIALOG_FRAME, 48, 288, 678, 554, 0, 0, 128, 128, 16, 128, 128, 0xFFFFFFFF);
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
            int thumbU = draggingDialogScroll ? 24 : (dialogScrollAt(currentPaperMouse) ? 16 : 8);
            blitRegion(g, SCROLLBAR_TEX, x + width - 6, y, 6, bottom - y, 0, 0, 8, 64, 32, 64, 0xFFFFFFFF);
            blitRegion(g, SCROLLBAR_TEX, x + width - 6, sy, 6, thumb, thumbU, 0, 8, 64, 32, 64, 0xFFFFFFFF);
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
        blitNineSlice(g, DIALOG_FRAME, 22, 22, 730, 1050, 0, 0, 128, 128, 16, 128, 128, 0xFFFFFFFF);
        label(g, archive("preparing"), 58, 370, 30, INK, 645);
        wrapped(g, state.message(), 58, 430, 25, MUTED, 645, 730);
        int x = 58 + (int)((Util.getMillis() % 1400) * 536 / 1400);
        blitRegion(g, PARAMETER_FRAME, 58, 749, 645, 6, 0, 8, 128, 8, 128, 32, 0xFFFFFFFF);
        blitRegion(g, PARAMETER_FRAME, x, 749, 100, 6, 0, 18, 32, 8, 128, 32, COLORS[paper.category]);
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
        boolean hovered = enabled && !locked() && inside(currentPaperMouse.x(), currentPaperMouse.y(), x, y, w, h);
        int stateV = !enabled ? 108 : (hovered ? 36 : 0);
        blitNineSlice(g, BUTTON_TEX, x, y, w, h, 0, stateV, 96, 36, 10, 96, 144, 0xFFFFFFFF);
        label(g, name, x + 13, y + 10, 21, 0xFFF9F5E9, w - 26);
    }
    private void tooltip(GuiGraphics g, Component component, int guiX, int guiY) {
        var canvas = fit.logical(guiX, guiY); int w = Math.min(420, (int)(font.width(component) * 2.5) + 26);
        int x = Mth.clamp((int)canvas.x() + 20, 12, 1908 - w), y = Mth.clamp((int)canvas.y() + 15, 12, 1035);
        blitNineSlice(g, TOOLTIP_TEX, x, y, w, 38, 0, 0, 96, 38, 10, 96, 38, 0xFFFFFFFF);
        label(g, component, x + 12, y + 9, 21, 0xFFF9F5E9, w - 24);
    }
    private ArchiveGeometry.Point local(double guiX, double guiY) {
        if (fit == null) fit = ArchiveGeometry.fit(width, height);
        return ArchiveGeometry.canvasToPaper(fit.logical(guiX, guiY), new ArchiveGeometry.Point(pose.x, pose.y), Math.toRadians(pose.angle), pose.scale);
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
        if (button == 0 && guiY >= height - 20 && guiX >= width - font.width(exitLabel()) - 20) { if (!locked()) exitSelection(); return true; }
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
