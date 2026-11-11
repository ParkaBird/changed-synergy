package net.parkabird.changedsynergy.client;

/** Client-side bridge mixed into Changed's shared radial-screen base. */
public interface RadialWheelAnimationAccess {
    boolean changedSynergy$startClosing(Runnable completion, boolean wheelSwitch);

    float changedSynergy$getLayerAlpha();

    float changedSynergy$getFrameHighlight(int slot, boolean selected);

    float changedSynergy$getOverallAlpha();

    float changedSynergy$getPanelAlpha();

    float changedSynergy$getHorizontalOffset();

    int changedSynergy$getViewOffset();

    float changedSynergy$getScrollOffset();

    void changedSynergy$scrollOptions(double delta);

    boolean changedSynergy$canConfirm();

    UiSpring changedSynergy$getHorizontalSpring();

    void changedSynergy$continueSlide(UiSpring horizontal);

    void changedSynergy$openImmediately();

    boolean changedSynergy$isInstantArrival();
}
