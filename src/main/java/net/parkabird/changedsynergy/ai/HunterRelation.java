package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.latex.LatexType;
import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.server.level.ServerPlayer;

/** Relationship between an NPC hunter and a particular player form. */
public enum HunterRelation {
    HUMAN,
    ALLY,
    RIVAL,
    OUTSIDER;

    public static HunterRelation between(ChangedEntity speaker, ServerPlayer player) {
        TransfurVariantInstance<?> variant = ProcessTransfur.getPlayerTransfurVariant(player);
        if (variant == null) {
            return HUMAN;
        }

        ChangedEntity playerForm = variant.getChangedEntity();
        LatexType speakerType = LatexType.getEntityLatexType(speaker);
        LatexType playerType = LatexType.getEntityLatexType(playerForm);
        if (speakerType != null && playerType != null
                && (speakerType.isHostileTo(playerType) || playerType.isHostileTo(speakerType))) {
            return RIVAL;
        }

        if (speakerType != null && playerType != null
                && (speakerType.isFriendlyTo(playerType) || playerType.isFriendlyTo(speakerType))) {
            return ALLY;
        }

        HunterFaction speakerFaction = HunterFaction.of(speaker);
        HunterFaction playerFaction = HunterFaction.of(playerForm);
        if (speaker.getType() == playerForm.getType()
                || speakerFaction == playerFaction && speakerFaction != HunterFaction.LIGHT) {
            return ALLY;
        }
        return OUTSIDER;
    }
}
