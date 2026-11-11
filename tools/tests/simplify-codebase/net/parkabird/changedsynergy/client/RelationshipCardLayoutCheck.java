package net.parkabird.changedsynergy.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/** Headless layout contract: wrapped lines keep their order, identity and snapshot lifetime. */
public final class RelationshipCardLayoutCheck {
    public static void main(String[] args) {
        var title = Component.literal("Status");
        var first = Component.literal("First paragraph").withStyle(ChatFormatting.RED);
        var second = Component.literal("Second paragraph").withStyle(ChatFormatting.BLUE);
        var a = first.getVisualOrderText();
        var b = Component.literal("Wrapped continuation").getVisualOrderText();
        var c = second.getVisualOrderText();
        var font = new Font(id -> null, false) {
            @Override public List<FormattedCharSequence> split(FormattedText text, int width) {
                check(width == 12, "caller width reaches the font unchanged");
                if (text == first) return List.of(a, b);
                if (text == second) return List.of(c);
                throw new AssertionError("Unexpected paragraph");
            }
        };
        var source = new ArrayList<Component>(List.of(first, second));
        var card = RelationshipPanel.prepareCard(font, new RelationshipPanel.InfoCard(title, source), 12);
        check(card.title() == title, "localized title identity is preserved");
        check(card.lines().equals(List.of(a, b, c)), "paragraph and wrapped-line order is preserved");
        check(card.height() == RelationshipPanel.GEL_TEXT_PADDING + 27, "three rows fit the existing artwork padding");
        source.clear();
        check(card.lines().size() == 3, "preparation owns its rendered-line snapshot");
        try { card.lines().clear(); throw new AssertionError("Mutable prepared lines"); }
        catch (UnsupportedOperationException expected) { }
        var empty = RelationshipPanel.prepareCard(font, new RelationshipPanel.InfoCard(title, List.of()), 12);
        check(empty.lines().isEmpty() && empty.height() == RelationshipPanel.GEL_TEXT_PADDING + 9,
                "empty cards retain one row of space");
        System.out.println("RELATIONSHIP_CARD_LAYOUT_PASS: wrapping, row height, title and immutable snapshots");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
