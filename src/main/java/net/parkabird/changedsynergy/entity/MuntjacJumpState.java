package net.parkabird.changedsynergy.entity;

/** Transient input state shared by prediction and the server; never saved with the player. */
public final class MuntjacJumpState {
    public static final int HOLD_TICKS = 3;
    private Object form;
    private boolean pressed, armed, charging;
    private int chargeStart = -1, boostUntil;
    private float boost = -1;
    private boolean flight, airborne;
    private double takeoffY, peakY;
    private int landedTicks;

    public void bind(Object currentForm) {
        if (form != currentForm) {
            cancel(false);
            flight = airborne = false;
            form = currentForm;
        }
    }

    public boolean bound() { return form != null; }
    public boolean pressed() { return pressed; }
    public boolean holding() { return armed; }
    public boolean charging() { return charging; }

    /** Returns the released jump's charge (zero for a tap), or -1 when no jump is released. */
    public float input(boolean down, int tick, boolean grounded) {
        tick(tick, grounded);
        if (down == pressed) return -1;
        pressed = down;
        if (down) {
            armed = true;
            charging = false;
            chargeStart = grounded ? tick : -1;
        } else if (armed) {
            float power = grounded && chargeStart >= 0 ? charge(tick) : -1;
            charging = false;
            armed = false;
            chargeStart = -1;
            return power;
        }
        return -1;
    }

    public void tick(int tick, boolean grounded) {
        if (!armed) return;
        if (grounded && chargeStart < 0) chargeStart = tick;
        else if (!grounded && chargeStart >= 0) cancel(pressed);
        if (armed && chargeStart >= 0) charging = tick - chargeStart >= HOLD_TICKS;
    }

    public float charge(int tick) {
        if (!charging || chargeStart < 0) return 0;
        int held = Math.max(0, tick - chargeStart - HOLD_TICKS);
        return held < 10 ? held / 10.0F : 0.8F + 0.2F / (held - 9);
    }

    public void queueJump(float power, int tick) {
        boost = Math.max(0, Math.min(1, power));
        boostUntil = tick + 6;
    }

    public float multiplier(int tick) { return boost >= 0 && tick <= boostUntil ? 1 + boost : 1; }
    public void consumeJump() { boost = -1; }

    public void beginFlight(double y, boolean charged) {
        flight = charged;
        airborne = false;
        landedTicks = 0;
        takeoffY = peakY = y;
    }

    public void trackFlight(double y, boolean grounded) {
        if (!flight) return;
        peakY = Math.max(peakY, y);
        if (!grounded) {
            if (landedTicks > 0) flight = false;
            airborne = true;
        } else if (airborne && ++landedTicks >= 3) flight = false;
    }

    /** Remove only this jump's actual rise; a drop below the launch point still hurts. */
    public float landingDistance(float distance, double y) {
        if (!flight) return distance;
        peakY = Math.max(peakY, y);
        flight = false;
        return Math.max(0, distance - (float)Math.max(0, peakY - takeoffY));
    }

    public void cancel(boolean down) {
        pressed = down;
        charging = false;
        armed = false;
        chargeStart = -1;
        consumeJump();
    }
}
