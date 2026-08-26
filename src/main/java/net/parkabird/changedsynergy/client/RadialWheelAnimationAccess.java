package net.parkabird.changedsynergy.client;

/** Client-side bridge mixed into Changed's shared radial-screen base. */
public interface RadialWheelAnimationAccess {
    boolean changedSynergy$startClosing(Runnable completion, boolean wheelSwitch);

    float changedSynergy$getLayerAlpha();

    float changedSynergy$getOverallAlpha();

    float changedSynergy$getHorizontalOffset();
}
