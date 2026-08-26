package net.parkabird.changedsynergy.client;

import net.ltxprogrammer.changed.ability.AbstractAbilityInstance.KeyReference;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import net.parkabird.changedsynergy.ChangedSynergyClientConfig;

/** Shared timing, easing and colour helpers for QTE presentation. */
public final class QteAnimationUtil {
    private QteAnimationUtil() {
    }

    public static boolean enabled() {
        return ChangedSynergyClientConfig.CLIENT.qteAnimations.get();
    }

    public static boolean reducedMotion() {
        return ChangedSynergyClientConfig.CLIENT.reducedQteMotion.get();
    }

    public static long now() {
        return Util.getMillis();
    }

    public static float progress(long startedAt, long durationMillis) {
        if (startedAt <= 0L || durationMillis <= 0L) {
            return 1.0F;
        }
        return clamp((now() - startedAt) / (float)durationMillis);
    }

    public static float smooth(float value) {
        float clamped = clamp(value);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    public static float easeOutCubic(float value) {
        float remaining = 1.0F - clamp(value);
        return 1.0F - remaining * remaining * remaining;
    }

    public static float easeOutBack(float value) {
        float shifted = clamp(value) - 1.0F;
        float overshoot = 1.70158F;
        return 1.0F + (overshoot + 1.0F) * shifted * shifted * shifted
                + overshoot * shifted * shifted;
    }

    public static float clamp(float value) {
        return Mth.clamp(value, 0.0F, 1.0F);
    }

    public static int directionX(KeyReference key) {
        return key == KeyReference.MOVE_LEFT
                ? -1
                : key == KeyReference.MOVE_RIGHT ? 1 : 0;
    }

    public static int directionY(KeyReference key) {
        return key == KeyReference.MOVE_FORWARD
                ? -1
                : key == KeyReference.MOVE_BACKWARD ? 1 : 0;
    }

    public static int withAlpha(int rgb, float alpha) {
        return Math.round(clamp(alpha) * 255.0F) << 24
                | rgb & 0x00FFFFFF;
    }

    public static int scaleRgb(int rgb, float scale) {
        float clamped = Math.max(0.0F, scale);
        int red = Mth.clamp(
                Math.round(((rgb >> 16) & 0xFF) * clamped), 0, 255);
        int green = Mth.clamp(
                Math.round(((rgb >> 8) & 0xFF) * clamped), 0, 255);
        int blue = Mth.clamp(
                Math.round((rgb & 0xFF) * clamped), 0, 255);
        return red << 16 | green << 8 | blue;
    }

    public static int brightenRgb(int rgb, float amount) {
        float clamped = clamp(amount);
        int red = Mth.lerpInt(clamped, (rgb >> 16) & 0xFF, 255);
        int green = Mth.lerpInt(clamped, (rgb >> 8) & 0xFF, 255);
        int blue = Mth.lerpInt(clamped, rgb & 0xFF, 255);
        return red << 16 | green << 8 | blue;
    }
}
