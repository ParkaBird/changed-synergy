package net.parkabird.changedsynergy.client;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import net.ltxprogrammer.changed.entity.beast.PureWhiteLatexWolf;
import net.ltxprogrammer.changed.entity.beast.WhiteLatexWolfMale;
import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.parkabird.changedsynergy.ai.PureWhiteWolfAdaptation;

/** A render-only eyed wolf; it is never spawned or used as the player's form. */
public final class PureWhitePlayerAppearance {
    private static final Map<Player, WeakReference<WhiteLatexWolfMale>> MODELS =
            new WeakHashMap<>();

    private PureWhitePlayerAppearance() {
    }

    public static LivingEntity forRender(LivingEntity source) {
        if (!(source instanceof PureWhiteLatexWolf original)
                || !source.level().isClientSide) {
            return source;
        }
        Player player = original.getUnderlyingPlayer();
        if (!PureWhiteWolfAdaptation.isAdaptedPlayer(player)) {
            return source;
        }
        var reference = MODELS.get(player);
        WhiteLatexWolfMale visual = reference == null ? null : reference.get();
        if (visual == null || visual.level() != source.level()) {
            visual = ChangedEntities.WHITE_LATEX_WOLF_MALE.get()
                    .create(source.level());
            if (visual == null) {
                return source;
            }
            MODELS.put(player, new WeakReference<>(visual));
        }
        TransfurVariantInstance.syncEntityAndPlayer(visual, player);
        TransfurVariantInstance.syncInventory(visual, player, false);
        visual.getBasicPlayerInfo().copyFrom(original.getBasicPlayerInfo());
        visual.setInvisible(original.isInvisible());
        visual.setCustomName(original.getCustomName());
        visual.setCustomNameVisible(original.isCustomNameVisible());
        return visual;
    }
}
