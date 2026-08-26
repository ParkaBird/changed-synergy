package net.parkabird.changedsynergy.client;

import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/** Temporarily applies the absorber's appearance to the negotiation preview. */
public final class AbsorptionPreviewRenderState {
    private AbsorptionPreviewRenderState() {
    }

    public static void render(
            Player player,
            CompoundTag appearance,
            Runnable renderer) {
        var variant = ProcessTransfur.getPlayerTransfurVariant(player);
        if (variant == null || appearance == null || appearance.isEmpty()) {
            renderer.run();
            return;
        }

        var info = variant.getChangedEntity().getBasicPlayerInfo();
        CompoundTag previous = new CompoundTag();
        info.save(previous);
        try {
            info.load(appearance);
            renderer.run();
        } finally {
            info.load(previous);
        }
    }
}
