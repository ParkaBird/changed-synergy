package net.parkabird.changedsynergy.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.parkabird.changedsynergy.ChangedSynergyClientConfig;
import net.parkabird.changedsynergy.ChangedSynergyClientConfig.DialogueDisplayMode;

/** Client-side queue for short, non-chat telepathic dialogue lines. */
public final class TelepathyDanmakuState {
    private static final int MAX_LINES = 10;
    private static final int MAX_ENGLISH_POPUPS = 6;
    private static final int SCROLLING_LANES = 5;
    private static final long SCROLLING_LIFETIME_MILLIS = 18_000L;
    private static final long ENGLISH_POPUP_LIFETIME_MILLIS = 8_200L;
    private static final List<Line> LINES = new ArrayList<>();
    private static int nextScrollingLane;
    private static long nextLineId;

    private TelepathyDanmakuState() {
    }

    public static void receive(
            Component message,
            Component speaker,
            Component dialogue,
            int accentColor) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ChangedSynergyClientConfig.CLIENT.telepathicDanmaku.get()) {
            minecraft.gui.getChat().addMessage(message);
            return;
        }
        long now = Util.getMillis();
        prune(now);
        boolean englishPopup = usesPopups(minecraft);
        if (englishPopup) {
            while (LINES.stream().filter(Line::englishPopup).count()
                    >= MAX_ENGLISH_POPUPS) {
                int oldest = -1;
                for (int index = 0; index < LINES.size(); index++) {
                    if (LINES.get(index).englishPopup()) {
                        oldest = index;
                        break;
                    }
                }
                if (oldest < 0) {
                    break;
                }
                LINES.remove(oldest);
            }
        }
        while (LINES.size() >= MAX_LINES) {
            LINES.remove(0);
        }
        int lane = englishPopup ? 0 : nextScrollingLane++ % SCROLLING_LANES;
        LINES.add(new Line(
                ++nextLineId,
                message.copy(),
                speaker.copy(),
                dialogue.copy(),
                accentColor,
                lane,
                now,
                englishPopup ? 0.0D : 125.0D + lane * 4.5D,
                englishPopup,
                englishPopup
                        ? ENGLISH_POPUP_LIFETIME_MILLIS
                        : SCROLLING_LIFETIME_MILLIS));
    }

    public static List<Line> activeLines() {
        prune(Util.getMillis());
        return List.copyOf(LINES);
    }

    private static void prune(long now) {
        Iterator<Line> iterator = LINES.iterator();
        while (iterator.hasNext()) {
            Line line = iterator.next();
            if (now - line.createdAtMillis() >= line.lifetimeMillis()) {
                iterator.remove();
            }
        }
    }

    private static boolean usesPopups(Minecraft minecraft) {
        DialogueDisplayMode mode =
                ChangedSynergyClientConfig.CLIENT.dialogueDisplayMode.get();
        if (mode == DialogueDisplayMode.POPUP) {
            return true;
        }
        if (mode == DialogueDisplayMode.DANMAKU) {
            return false;
        }
        String language = minecraft.getLanguageManager().getSelected();
        if (language == null) {
            return false;
        }
        String locale = language.toLowerCase(Locale.ROOT);
        return locale.startsWith("en_")
                || locale.startsWith("de_")
                || locale.startsWith("es_");
    }

    public record Line(
            long id,
            Component message,
            Component speaker,
            Component dialogue,
            int accentColor,
            int lane,
            long createdAtMillis,
            double pixelsPerSecond,
            boolean englishPopup,
            long lifetimeMillis) {
    }
}
