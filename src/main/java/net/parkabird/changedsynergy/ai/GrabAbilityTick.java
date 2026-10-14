package net.parkabird.changedsynergy.ai;

/** Scripted abilities must tick even when Changed/Addon does not own their idle update. */
public interface GrabAbilityTick {
    void changedSynergy$tickHoldIfNeeded();
}
