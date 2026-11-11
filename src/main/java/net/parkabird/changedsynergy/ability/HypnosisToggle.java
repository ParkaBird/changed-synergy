package net.parkabird.changedsynergy.ability;

/** Transient input latch on Changed's existing ability controller, on both logical sides. */
public interface HypnosisToggle {
    boolean changedSynergy$isPlayerHypnosis();
    boolean changedSynergy$isLatched();
    void changedSynergy$input(boolean down, boolean wasDown);
    void changedSynergy$clearHypnosis();
    float changedSynergy$hypnosisBrightness();
}
