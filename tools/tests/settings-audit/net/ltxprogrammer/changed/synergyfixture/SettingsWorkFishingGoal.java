package net.ltxprogrammer.changed.synergyfixture;

import net.minecraft.world.entity.ai.goal.Goal;

/** Unique private fixture package: avoids exporting a package already owned by Changed. */
public final class SettingsWorkFishingGoal extends Goal {
    public int starts, stops, ticks;
    public SettingsWorkFishingGoal() { setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK)); }
    @Override public boolean canUse() { return true; }
    @Override public boolean canContinueToUse() { return true; }
    @Override public void start() { starts++; }
    @Override public void stop() { stops++; }
    @Override public void tick() { ticks++; }
}
