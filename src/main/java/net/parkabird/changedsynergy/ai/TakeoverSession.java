package net.parkabird.changedsynergy.ai;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.ArrayList;

/**
 * Server-thread-only, Minecraft-independent session. All times are absolute monotonic
 * server ticks (not daylight time). The owner authenticates packets/session identity,
 * computes safety blockers and performs actual placement, equipment and reward transactions.
 * No method here teleports, heals, changes inventory or certifies a safe location.
 */
public final class TakeoverSession {
    public enum Kind { ORDINARY, EXOSKELETON }
    public enum Phase { CONTROLLED, BORROWED, STRUGGLE, SLEEPING, RELEASING, FINISHED }
    public enum SleepOutcome { RELEASE, TRANSFUR }
    public enum Blocker { NONE, COMBAT, AIRBORNE, UNSAFE_WATER, CRITICAL_WORK }
    public enum Result { ACCEPTED, DISABLED, WRONG_KIND, WRONG_PHASE, TOO_EARLY, COOLDOWN,
        BLOCKED, USED, BAD_SEQUENCE, BAD_INDEX, TOO_FAST, WRONG_KEY, INSUFFICIENT_TIME }
    public enum ReleaseReason { NONE, BREAKOUT, SLEEP, TRANSFUR, EMERGENCY, INTERRUPTED }

    public static final long PREPARE_TICKS = 0, CHALLENGE_TICKS = 200,
        SLEEP_TICKS = 200, EYE_CLOSE_TICKS = 40, EYE_OPEN_TICKS = 40,
        EXOSKELETON_AWAKE_TICKS = 600,
        MIN_EXOSKELETON_AWAKE_TICKS = 100, MAX_EXOSKELETON_AWAKE_TICKS = 1200,
        MAX_ORDINARY_TICKS = 6000, RELEASE_WAIT_TICKS = 600,
        BORROW_WAIT_TICKS = 400, PROACTIVE_BORROW_TICKS = 400,
        REACTIVE_BORROW_TICKS = 200, BORROW_COOLDOWN_TICKS = 1200,
        REQUEST_DEBOUNCE_TICKS = 100, QTE_INPUT_INTERVAL_TICKS = 4,
        ORANGE_COOLDOWN_TICKS = 24000;

    /** Immutable DTO for mapping to NBT/etc. Restore accepts trusted server saves only. */
    public record Snapshot(Kind kind, long startedAt, long deadline, long hardDeadline,
            SleepOutcome sleepOutcome,
            boolean strictBorrow, boolean initiallyEligible, boolean compensationCancelled,
            Phase phase, long lastTick, long phaseUntil, long releaseDeadline,
            long nextBorrowAt, boolean borrowedBefore, boolean struggleUsed,
            long challengeAt, long challengeUntil, List<Integer> sequence, int qteIndex,
            long lastInputAt, boolean breakoutSucceeded, boolean slept,
            ReleaseReason releaseReason, boolean achievementClaimed, boolean orangesClaimed) {
        public Snapshot {
            Objects.requireNonNull(kind); Objects.requireNonNull(sleepOutcome); Objects.requireNonNull(phase);
            Objects.requireNonNull(releaseReason);
            sequence = List.copyOf(sequence);
        }
    }

    private final Kind kind;
    private final long startedAt, deadline, hardDeadline;
    private final boolean strictBorrow, initiallyEligible;
    private final SleepOutcome sleepOutcome;
    private boolean compensationCancelled, borrowedBefore, struggleUsed, breakoutSucceeded,
        slept, achievementClaimed, orangesClaimed;
    private Phase phase = Phase.CONTROLLED;
    private ReleaseReason releaseReason = ReleaseReason.NONE;
    private long lastTick, phaseUntil = -1, releaseDeadline = -1, nextBorrowAt,
        challengeAt = -1, challengeUntil = -1, lastInputAt = -1;
    private List<Integer> sequence = List.of();
    private int qteIndex;

    /** Eligibility must already exclude punishment, theft, pursuit and genuine hostility. */
    public static TakeoverSession ordinary(long now, long durationTicks,
            boolean compensationEligible, boolean strictBorrow) {
        return ordinary(now, durationTicks, compensationEligible, strictBorrow, SleepOutcome.RELEASE);
    }

    public static TakeoverSession ordinary(long now, long durationTicks,
            boolean compensationEligible, boolean strictBorrow, SleepOutcome sleepOutcome) {
        if (durationTicks <= 0 || durationTicks > MAX_ORDINARY_TICKS)
            throw new IllegalArgumentException("ordinary duration must be 1..6000 ticks");
        return new TakeoverSession(Kind.ORDINARY, now, durationTicks,
            compensationEligible, strictBorrow, Objects.requireNonNull(sleepOutcome));
    }

    public static TakeoverSession exoskeleton(long now) {
        return exoskeleton(now, EXOSKELETON_AWAKE_TICKS);
    }

    public static TakeoverSession exoskeleton(long now, long durationTicks) {
        if (durationTicks < MIN_EXOSKELETON_AWAKE_TICKS || durationTicks > MAX_EXOSKELETON_AWAKE_TICKS)
            throw new IllegalArgumentException("exoskeleton duration must be 100..1200 ticks");
        return new TakeoverSession(Kind.EXOSKELETON, now, durationTicks, false, false, SleepOutcome.RELEASE);
    }

    private TakeoverSession(Kind kind, long now, long duration, boolean eligible,
            boolean strict, SleepOutcome sleepOutcome) {
        if (now < 0) throw new IllegalArgumentException("negative tick");
        this.kind = kind;
        startedAt = lastTick = now;
        deadline = Math.addExact(now, duration);
        this.sleepOutcome = sleepOutcome;
        hardDeadline = Math.addExact(deadline, RELEASE_WAIT_TICKS);
        initiallyEligible = eligible;
        strictBorrow = strict;
        nextBorrowAt = Math.addExact(now, strict ? 600 : BORROW_WAIT_TICKS);
    }

    private TakeoverSession(Snapshot s) {
        kind = s.kind(); startedAt = s.startedAt(); deadline = s.deadline();
        hardDeadline = s.hardDeadline(); sleepOutcome = s.sleepOutcome();
        strictBorrow = s.strictBorrow();
        initiallyEligible = s.initiallyEligible(); compensationCancelled = s.compensationCancelled();
        phase = s.phase(); lastTick = s.lastTick(); phaseUntil = s.phaseUntil();
        releaseDeadline = s.releaseDeadline(); nextBorrowAt = s.nextBorrowAt();
        borrowedBefore = s.borrowedBefore(); struggleUsed = s.struggleUsed();
        challengeAt = s.challengeAt(); challengeUntil = s.challengeUntil();
        sequence = s.sequence(); qteIndex = s.qteIndex(); lastInputAt = s.lastInputAt();
        breakoutSucceeded = s.breakoutSucceeded(); slept = s.slept();
        releaseReason = s.releaseReason(); achievementClaimed = s.achievementClaimed();
        orangesClaimed = s.orangesClaimed();
        validate();
    }

    private void validate() {
        if (startedAt < 0 || deadline <= startedAt || lastTick < startedAt
                || deadline - startedAt > MAX_ORDINARY_TICKS
                || hardDeadline != Math.addExact(deadline, RELEASE_WAIT_TICKS)
                || nextBorrowAt < startedAt || nextBorrowAt > hardDeadline
                || phaseUntil < -1 || releaseDeadline < -1 || challengeAt < -1
                || challengeUntil < -1 || lastInputAt < -1
                || qteIndex < 0 || qteIndex > sequence.size()
                || !validSequence(sequence, true)) throw new IllegalArgumentException("invalid snapshot");
        if (kind == Kind.EXOSKELETON && (deadline - startedAt < MIN_EXOSKELETON_AWAKE_TICKS
                || deadline - startedAt > MAX_EXOSKELETON_AWAKE_TICKS
                || strictBorrow || initiallyEligible || borrowedBefore || struggleUsed
                || breakoutSucceeded || !sequence.isEmpty() || phase == Phase.BORROWED
                || phase == Phase.STRUGGLE || releaseReason == ReleaseReason.BREAKOUT
                || sleepOutcome != SleepOutcome.RELEASE))
            throw new IllegalArgumentException("ordinary state in exoskeleton snapshot");
        if (struggleUsed != !sequence.isEmpty()
                || (!struggleUsed && (challengeAt != -1 || challengeUntil != -1 || lastInputAt != -1))
                || (struggleUsed && (challengeAt < startedAt
                    || challengeAt > lastTick
                    || challengeUntil != Math.addExact(challengeAt, CHALLENGE_TICKS)
                    || challengeUntil > deadline))
                || (lastInputAt != -1 && (lastInputAt < challengeAt || lastInputAt >= challengeUntil
                    || lastInputAt > lastTick))
                || (qteIndex > 0 && lastInputAt == -1)
                || (struggleUsed && (qteIndex == sequence.size()) != breakoutSucceeded)
                || ((phase == Phase.CONTROLLED || phase == Phase.BORROWED)
                    && (struggleUsed || slept || breakoutSucceeded))
                || (phase == Phase.STRUGGLE && (!struggleUsed || breakoutSucceeded || slept
                    || lastTick >= challengeUntil))
                || (breakoutSucceeded && (!struggleUsed || qteIndex != sequence.size() || slept))
                || (breakoutSucceeded && releaseReason != ReleaseReason.BREAKOUT
                    && releaseReason != ReleaseReason.EMERGENCY && releaseReason != ReleaseReason.INTERRUPTED)
                || (slept && kind == Kind.EXOSKELETON && lastTick < deadline)
                || (phase == Phase.SLEEPING && (!slept || phaseUntil <= lastTick || phaseUntil > hardDeadline))
                || (phase == Phase.BORROWED && (!borrowedBefore || phaseUntil <= lastTick
                    || phaseUntil > deadline))
                || (phase != Phase.BORROWED && phase != Phase.SLEEPING && phaseUntil != -1)
                || ((phase == Phase.RELEASING || phase == Phase.FINISHED)
                    != (releaseReason != ReleaseReason.NONE))
                || (releaseReason == ReleaseReason.NONE && releaseDeadline != -1)
                || ((phase == Phase.RELEASING || phase == Phase.FINISHED)
                    && (releaseDeadline < startedAt || releaseDeadline > hardDeadline))
                || (releaseReason == ReleaseReason.BREAKOUT && !breakoutSucceeded)
                || ((releaseReason == ReleaseReason.SLEEP || releaseReason == ReleaseReason.TRANSFUR) && !slept)
                || (releaseReason == ReleaseReason.TRANSFUR && sleepOutcome != SleepOutcome.TRANSFUR)
                || (achievementClaimed && (phase != Phase.FINISHED || !breakoutSucceeded))
                || (orangesClaimed && (phase != Phase.FINISHED || !initiallyEligible
                    || compensationCancelled || !slept || releaseReason != ReleaseReason.SLEEP)))
            throw new IllegalArgumentException("inconsistent snapshot");
    }

    /** Restart policy: no failed QTE, replayed transition or new mechanical awake period. */
    public static TakeoverSession restore(Snapshot snapshot, long now) {
        TakeoverSession session = new TakeoverSession(Objects.requireNonNull(snapshot));
        session.observe(now);
        if (session.phase != Phase.FINISHED && (session.kind == Kind.EXOSKELETON
                || session.phase == Phase.STRUGGLE || session.phase == Phase.SLEEPING))
            session.interrupt(now);
        else session.tick(now);
        return session;
    }

    private void observe(long now) {
        if (now < lastTick) throw new IllegalArgumentException("tick moved backwards");
        lastTick = now;
    }

    private long boundedAfter(long at, long duration) {
        return at >= hardDeadline || duration >= hardDeadline - at ? hardDeadline : at + duration;
    }

    /** Timers catch up from their scheduled boundary, never from a delayed poll. */
    public void tick(long now) {
        observe(now);
        if (phase == Phase.FINISHED || phase == Phase.RELEASING) return;
        // An accepted challenge owns its full window, including timeout at the session deadline.
        if (phase == Phase.STRUGGLE && challengeUntil <= deadline && now >= challengeUntil)
            sleep(challengeUntil);
        if (phase != Phase.SLEEPING && now >= deadline) {
            if (kind == Kind.EXOSKELETON) sleep(deadline);
            else sleep(deadline);
        }
        if (phase == Phase.SLEEPING && now >= phaseUntil) {
            release(phaseUntil, kind == Kind.ORDINARY && sleepOutcome == SleepOutcome.TRANSFUR
                    ? ReleaseReason.TRANSFUR : ReleaseReason.SLEEP);
        }
        if (phase == Phase.BORROWED && now >= phaseUntil) handBack(phaseUntil);
        if (now >= hardDeadline && phase != Phase.RELEASING) release(hardDeadline, ReleaseReason.INTERRUPTED);
    }

    public Result requestBorrow(long now, Blocker blocker) {
        Objects.requireNonNull(blocker);
        tick(now);
        if (kind != Kind.ORDINARY) return Result.WRONG_KIND;
        if (phase != Phase.CONTROLLED) return Result.WRONG_PHASE;
        if (now < nextBorrowAt) return borrowedBefore ? Result.COOLDOWN : Result.TOO_EARLY;
        if (blocker != Blocker.NONE) {
            nextBorrowAt = boundedAfter(now, REQUEST_DEBOUNCE_TICKS);
            return Result.BLOCKED;
        }
        phase = Phase.BORROWED;
        phaseUntil = Math.min(deadline, boundedAfter(now,
                strictBorrow ? REACTIVE_BORROW_TICKS : PROACTIVE_BORROW_TICKS));
        borrowedBefore = true;
        return Result.ACCEPTED;
    }

    /** Also used by the owner to abort unsafe borrowed actions, without consuming QTE. */
    public boolean returnControl(long now) {
        tick(now);
        if (phase != Phase.BORROWED) return false;
        handBack(now);
        return true;
    }

    private void handBack(long at) {
        phase = Phase.CONTROLLED;
        phaseUntil = -1;
        nextBorrowAt = boundedAfter(at, BORROW_COOLDOWN_TICKS);
    }

    private static boolean validSequence(List<Integer> keys, boolean allowEmpty) {
        return keys != null && (allowEmpty || !keys.isEmpty()) && keys.size() <= 50
            && keys.stream().allMatch(k -> k != null && k >= 0 && k <= 3);
    }

    /** Call only after explicit confirmation. Keys 0..3 represent server-selected directions. */
    public Result startStruggle(long now, List<Integer> keys) {
        tick(now);
        if (kind != Kind.ORDINARY) return Result.WRONG_KIND;
        if (struggleUsed) return Result.USED;
        if (phase != Phase.CONTROLLED && phase != Phase.BORROWED) return Result.WRONG_PHASE;
        if (!validSequence(keys, false)) return Result.BAD_SEQUENCE;
        if (deadline - now < PREPARE_TICKS + CHALLENGE_TICKS) return Result.INSUFFICIENT_TIME;
        sequence = List.copyOf(keys);
        struggleUsed = true;
        phase = Phase.STRUGGLE;
        phaseUntil = -1;
        challengeAt = Math.addExact(now, PREPARE_TICKS);
        challengeUntil = Math.addExact(challengeAt, CHALLENGE_TICKS);
        return Result.ACCEPTED;
    }

    /** Server seed only. Generate once, then persist the actual sequence, never reroll on input.
     * Keys: 0 forward, 1 backward, 2 left, 3 right. java.util.Random has a specified algorithm.
     */
    public Result startStruggle(long now, long serverSeed, int steps) {
        if (steps < 1 || steps > 50) {
            tick(now);
            return kind == Kind.EXOSKELETON ? Result.WRONG_KIND : Result.BAD_SEQUENCE;
        }
        Random random = new Random(serverSeed);
        List<Integer> keys = new ArrayList<>(steps);
        for (int i = 0; i < steps; i++) keys.add(random.nextInt(4));
        return startStruggle(now, keys);
    }

    public Result submit(long now, int sequenceIndex, int key) {
        return submitQte(now, sequenceIndex, key);
    }

    /** Half-open challenge window; duplicates/out-of-order/spam never advance progress.
     * A wrong direction mirrors Changed's grab struggle penalty: the prompt changes and
     * one additional successful input is required, while the challenge remains usable.
     */
    public Result submitQte(long now, int index, int key) {
        tick(now);
        if (kind != Kind.ORDINARY) return Result.WRONG_KIND;
        if (phase != Phase.STRUGGLE) return Result.WRONG_PHASE;
        if (now < challengeAt) return Result.TOO_EARLY;
        if (index != qteIndex) return Result.BAD_INDEX;
        if (lastInputAt >= 0 && now - lastInputAt < QTE_INPUT_INTERVAL_TICKS) return Result.TOO_FAST;
        lastInputAt = now;
        if (key != sequence.get(qteIndex)) {
            int missed = sequence.get(qteIndex);
            ArrayList<Integer> penalized = new ArrayList<>(sequence);
            // Native grabbing moves on to another direction after a mistake. Keep the
            // replacement distinct from both the missed prompt and the submitted key.
            int replacement = (missed + 1) & 3;
            while (replacement == key) replacement = (replacement + 1) & 3;
            penalized.set(qteIndex, replacement);
            if (penalized.size() < 50) penalized.add(missed);
            sequence = List.copyOf(penalized);
            return Result.WRONG_KEY;
        }
        if (++qteIndex == sequence.size()) {
            breakoutSucceeded = true;
            release(now, ReleaseReason.BREAKOUT);
        }
        return Result.ACCEPTED;
    }

    private void sleep(long at) {
        slept = true;
        phase = Phase.SLEEPING;
        phaseUntil = boundedAfter(at, SLEEP_TICKS);
    }

    /** Trusted server failure callback, not a client-request route. Emergency recovery uses
     * emergencyRelease instead. Mechanical sleep cannot be triggered before its deadline.
     */
    public boolean beginSleep(long now) {
        Phase before = phase;
        tick(now);
        if (phase == Phase.SLEEPING) return before != Phase.SLEEPING;
        if (kind != Kind.ORDINARY || phase != Phase.STRUGGLE || breakoutSucceeded) return false;
        sleep(now);
        return true;
    }

    private void release(long at, ReleaseReason reason) {
        phase = Phase.RELEASING;
        phaseUntil = -1;
        releaseReason = reason;
        long limit = boundedAfter(at, RELEASE_WAIT_TICKS);
        releaseDeadline = releaseDeadline < 0 ? limit : Math.min(releaseDeadline, limit);
    }

    /** Invoke before tick/input processing when an emergency shares the same tick. */
    public boolean emergencyRelease(long now) { return recover(now, ReleaseReason.EMERGENCY); }
    /** Disconnect, mismatch, disable, admin recovery: no failure sleep or oranges. */
    public boolean interrupt(long now) { return recover(now, ReleaseReason.INTERRUPTED); }

    /** Converts a failed permanent outcome into a safe, non-rewarding wake-up. */
    public boolean fallbackTransfur(long now) {
        observe(now);
        if (phase != Phase.RELEASING || releaseReason != ReleaseReason.TRANSFUR) return false;
        compensationCancelled = true;
        releaseReason = ReleaseReason.SLEEP;
        return true;
    }

    private boolean recover(long now, ReleaseReason reason) {
        observe(now);
        if (phase == Phase.FINISHED) return false;
        compensationCancelled = true;
        if (releaseReason == ReleaseReason.EMERGENCY) return false;
        if (phase == Phase.RELEASING && releaseReason == reason) return false;
        release(now, reason);
        return true;
    }

    /** Irreversible real-hostility veto; temporary peace must never call an eligibility setter. */
    public void cancelCompensation() {
        if (!orangesClaimed) compensationCancelled = true;
    }

    /** Host calls only AFTER actual safe separation and independent control restoration. */
    public boolean completeRelease(long now) {
        tick(now);
        if (phase != Phase.RELEASING) return false;
        phase = Phase.FINISHED;
        return true;
    }

    public boolean finish(long now) { return completeRelease(now); }

    public boolean claimAchievement() {
        if (phase != Phase.FINISHED || kind != Kind.ORDINARY || !breakoutSucceeded || achievementClaimed)
            return false;
        achievementClaimed = true;
        return true;
    }

    /** Host checks shared player cooldown and persists this claim in its reward transaction. */
    public boolean claimOranges(boolean playerCooldownReady) {
        if (!playerCooldownReady || phase != Phase.FINISHED || kind != Kind.ORDINARY
                || !initiallyEligible || compensationCancelled || !slept
                || releaseReason != ReleaseReason.SLEEP || orangesClaimed) return false;
        orangesClaimed = true;
        return true;
    }

    public Snapshot snapshot() {
        return new Snapshot(kind, startedAt, deadline, hardDeadline, sleepOutcome,
            strictBorrow, initiallyEligible,
            compensationCancelled, phase, lastTick, phaseUntil, releaseDeadline, nextBorrowAt,
            borrowedBefore, struggleUsed, challengeAt, challengeUntil, sequence, qteIndex,
            lastInputAt, breakoutSucceeded, slept, releaseReason, achievementClaimed, orangesClaimed);
    }

    public Kind getKind() { return kind; }
    public Phase getPhase() { return phase; }
    public long getStartedAt() { return startedAt; }
    public long getLastTick() { return lastTick; }
    public long getDeadline() { return deadline; }
    public long getHardDeadline() { return hardDeadline; }
    public SleepOutcome getSleepOutcome() { return sleepOutcome; }
    public long getPhaseUntil() { return phaseUntil; }
    public long getReleaseDeadline() { return releaseDeadline; }
    public long getNextBorrowAt() { return nextBorrowAt; }
    public long getChallengeAt() { return challengeAt; }
    public long getChallengeUntil() { return challengeUntil; }
    public int getQteIndex() { return qteIndex; }
    public int getQteLength() { return sequence.size(); }
    /** -1 outside a live challenge (including preparation); never wrap a finished sequence. */
    public int getExpectedKey() { return phase == Phase.STRUGGLE && lastTick >= challengeAt
        && lastTick < challengeUntil && qteIndex < sequence.size() ? sequence.get(qteIndex) : -1; }
    public boolean isStruggleUsed() { return struggleUsed; }
    public boolean isStrictBorrow() { return strictBorrow; }
    public boolean isBreakoutSucceeded() { return breakoutSucceeded; }
    public boolean hasSlept() { return slept; }
    public boolean isCompensationEligible() { return kind == Kind.ORDINARY
        && initiallyEligible && !compensationCancelled; }
    public ReleaseReason getReleaseReason() { return releaseReason; }
    public boolean hasStruggleOpportunity() { return kind == Kind.ORDINARY && !struggleUsed
        && (phase == Phase.CONTROLLED || phase == Phase.BORROWED); }
    public boolean hasPlayerControl() { return phase == Phase.BORROWED || phase == Phase.FINISHED; }
    public boolean isBorrowEndingSoon() { return phase == Phase.BORROWED && phaseUntil - lastTick <= 60; }
    /** Stop navigation waiting; owner must clear overlays/locks and execute protected fallback. */
    public boolean needsReleaseFallback() { return phase == Phase.RELEASING && lastTick >= releaseDeadline; }
}
