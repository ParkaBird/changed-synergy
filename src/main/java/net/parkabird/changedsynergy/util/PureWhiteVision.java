package net.parkabird.changedsynergy.util;

import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;

/** Shared identification for the original pure-white hive forms. */
public final class PureWhiteVision {
    private PureWhiteVision() {
    }

    public static boolean isPureWhiteForm(
            TransfurVariantInstance<?> variant) {
        return variant != null
                && (variant.is(ChangedTransfurVariants.PURE_WHITE_LATEX_WOLF)
                        || variant.is(ChangedTransfurVariants.PURE_WHITE_LATEX_WOLF_PUP)
                        || variant.is(ChangedTransfurVariants.PURE_WHITE_LATEX_CERBERUS)
                        || variant.is(ChangedTransfurVariants.LATEX_MUTANT_BLOODCELL_WOLF));
    }
}
