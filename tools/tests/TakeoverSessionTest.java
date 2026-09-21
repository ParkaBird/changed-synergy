import java.util.ArrayList;
import java.util.List;
import net.parkabird.changedsynergy.ai.TakeoverSession;
import net.parkabird.changedsynergy.ai.TakeoverSession.*;

/** Java 17 standalone tests; compile together with TakeoverSession.java, then run this main. */
public final class TakeoverSessionTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void rejects(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException | ArithmeticException expected) {
            checks++; return;
        }
        throw new AssertionError(message);
    }
    private static TakeoverSession ordinary(boolean eligible) {
        return TakeoverSession.ordinary(0, 3600, eligible, false);
    }
    private static TakeoverSession success() {
        var s = ordinary(true);
        check(s.startStruggle(0, List.of(0, 1, 2)) == Result.ACCEPTED, "start confirmed challenge");
        check(s.submitQte(0, 0, 0) == Result.ACCEPTED, "first key");
        check(s.submitQte(4, 1, 1) == Result.ACCEPTED, "second key");
        check(s.submitQte(8, 2, 2) == Result.ACCEPTED, "third key");
        return s;
    }
    private static TakeoverSession sleep(boolean eligible) {
        var s = ordinary(eligible);
        s.startStruggle(0, List.of(0, 1));
        s.tick(200);
        check(s.getPhase() == Phase.SLEEPING, "timeout starts sleep at 200");
        s.tick(399);
        check(s.getPhase() == Phase.SLEEPING, "sleep lasts 200 ticks");
        s.tick(400);
        check(s.getPhase() == Phase.RELEASING, "sleep recovery boundary");
        return s;
    }
    private static void borrowing() {
        var s = ordinary(true);
        long end = s.getDeadline(), hard = s.getHardDeadline();
        check(s.requestBorrow(399, Blocker.NONE) == Result.TOO_EARLY, "initial wait");
        check(s.requestBorrow(400, Blocker.COMBAT) == Result.BLOCKED, "safety rejection");
        check(s.requestBorrow(499, Blocker.NONE) == Result.TOO_EARLY, "rejection debounce");
        check(s.requestBorrow(500, Blocker.NONE) == Result.ACCEPTED, "debounce is not full cooldown");
        check(s.hasPlayerControl(), "borrowed control");
        s.tick(839); check(!s.isBorrowEndingSoon(), "warning not early");
        s.tick(840); check(s.isBorrowEndingSoon(), "3 second warning");
        s.tick(900); check(s.getPhase() == Phase.CONTROLLED, "automatic hand back");
        check(s.requestBorrow(2099, Blocker.NONE) == Result.COOLDOWN, "60 second cooldown");
        check(s.requestBorrow(2100, Blocker.NONE) == Result.ACCEPTED, "next borrow");
        check(s.returnControl(2101), "early hand back");
        check(!s.returnControl(2101), "idempotent hand back");
        check(s.hasStruggleOpportunity(), "unsafe borrow abort does not consume QTE");
        check(s.getDeadline() == end && s.getHardDeadline() == hard, "immutable deadlines");
        check(s.requestBorrow(3500, Blocker.NONE) == Result.ACCEPTED, "late borrow allowed");
        s.tick(3600);
        check(s.getPhase() == Phase.SLEEPING && !s.hasPlayerControl(), "expiry starts sleep and preempts borrow");
        s.tick(3800);
        check(s.getReleaseReason() == ReleaseReason.SLEEP, "default sleep releases after waking");
        check(s.requestBorrow(3800, Blocker.NONE) == Result.WRONG_PHASE, "no borrow during release");
        check(!s.claimOranges(true) && !s.claimAchievement(), "no premature reward");
        s.tick(4200); check(s.needsReleaseFallback(), "fixed hard fallback deadline");
        check(s.getPhase() == Phase.RELEASING, "timeout never fabricates placement");
        check(s.completeRelease(4200) && !s.completeRelease(4200), "complete exactly once");
        check(s.claimOranges(true) && !s.claimOranges(true) && !s.claimAchievement(),
            "ordinary sleep grants compensation exactly once");

        s = TakeoverSession.ordinary(100, 3600, false, true);
        check(s.requestBorrow(699, Blocker.NONE) == Result.TOO_EARLY, "strict 30 second wait");
        check(s.requestBorrow(700, Blocker.NONE) == Result.ACCEPTED, "strict first borrow");
        s.tick(900); check(s.getPhase() == Phase.CONTROLLED, "strict first borrow 10 seconds");
        check(s.requestBorrow(2100, Blocker.NONE) == Result.ACCEPTED, "strict later borrow");
        s.tick(2299); check(s.getPhase() == Phase.BORROWED, "reactive borrow lasts 10 seconds");
        s.tick(2300); check(s.getPhase() == Phase.CONTROLLED, "reactive borrow remains shorter");
        for (Blocker blocker : Blocker.values()) if (blocker != Blocker.NONE) {
            s = ordinary(false);
            check(s.requestBorrow(400, blocker) == Result.BLOCKED, "all safety blocker reasons");
        }
    }
    private static void qte() {
        var s = ordinary(true);
        check(s.startStruggle(0, List.of()) == Result.BAD_SEQUENCE, "empty sequence rejected");
        check(s.startStruggle(0, List.of(4)) == Result.BAD_SEQUENCE, "bad direction rejected");
        check(s.hasStruggleOpportunity(), "invalid challenge does not consume opportunity");
        var keys = new ArrayList<>(List.of(0, 1));
        check(s.startStruggle(0, keys) == Result.ACCEPTED, "start");
        keys.set(0, 3);
        check(s.submitQte(0, 1, 1) == Result.BAD_INDEX, "out of order rejected");
        check(s.submitQte(0, 0, 3) == Result.WRONG_KEY, "defensive sequence copy");
        check(s.getQteLength() == 3, "wrong key adds one struggle step");
        check(s.getExpectedKey() != 0 && s.getExpectedKey() != 3,
                "wrong key advances to a distinct prompt");
        check(s.submitQte(0, 0, 0) == Result.TOO_FAST, "wrong key consumes rate slot");
        check(s.submitQte(4, 0, s.getExpectedKey()) == Result.ACCEPTED,
                "correct input remains usable after wrong key");
        check(s.submitQte(4, 0, 0) == Result.BAD_INDEX, "duplicate rejected");
        check(s.submitQte(7, 1, 1) == Result.TOO_FAST, "frequency limited");
        check(s.submitQte(8, 1, 1) == Result.ACCEPTED, "sequence completion");
        check(s.submitQte(12, 2, 0) == Result.ACCEPTED, "penalty step completion");
        check(s.getReleaseReason() == ReleaseReason.BREAKOUT, "server verified breakout");
        check(!s.claimAchievement(), "success alone insufficient for achievement");
        check(s.startStruggle(12, List.of(0)) == Result.USED, "only one attempt");
        s.completeRelease(13);
        check(s.claimAchievement() && !s.claimAchievement(), "achievement once after actual release");
        check(!s.claimOranges(true), "success never gives oranges");

        s = ordinary(true); s.startStruggle(0, List.of(0));
        check(s.submitQte(200, 0, 0) == Result.WRONG_PHASE, "challenge end exclusive");
        check(s.getPhase() == Phase.SLEEPING, "QTE expiry failure");
        s = TakeoverSession.ordinary(0, 50, true, false);
        check(s.startStruggle(0, List.of(0)) == Result.INSUFFICIENT_TIME, "short window rejected");
        check(s.submitQte(50, 0, 0) == Result.WRONG_PHASE, "overall expiry beats same tick input");
        check(s.getPhase() == Phase.SLEEPING, "session expiry enters ordinary sleep");
        s = ordinary(true); s.requestBorrow(400, Blocker.NONE);
        check(s.startStruggle(401, List.of(0)) == Result.ACCEPTED && !s.hasPlayerControl(), "QTE revokes borrow");
    }
    private static void rewardsAndRecovery() {
        var s = sleep(true);
        check(!s.claimOranges(true), "wait for actual waking");
        s.completeRelease(400);
        check(!s.claimOranges(false), "shared player cooldown respected");
        check(s.claimOranges(true) && !s.claimOranges(true), "eligible sleep compensation once");
        check(!s.claimAchievement(), "failed QTE not achievement");
        var restored = TakeoverSession.restore(s.snapshot(), 400);
        check(!restored.claimOranges(true), "claimed reward survives restore");
        s = sleep(false); s.completeRelease(400);
        check(!s.claimOranges(true), "hostile punishment theft pursuit excluded by captured eligibility");
        s = sleep(true); s.cancelCompensation(); s.completeRelease(400);
        check(!s.claimOranges(true), "new true hostility irrevocably cancels eligibility");
        s = sleep(true); s.emergencyRelease(400); s.completeRelease(400);
        check(!s.claimOranges(true), "emergency does not reward sleep");
        s = success();
        check(s.emergencyRelease(8), "emergency takes over success recovery");
        long releaseDeadline = s.snapshot().releaseDeadline();
        check(!s.emergencyRelease(49) && !s.interrupt(49), "recovery priority idempotent");
        check(s.snapshot().releaseDeadline() == releaseDeadline, "recovery never extends deadline");
        s.completeRelease(50);
        check(s.claimAchievement(), "real success survives emergency takeover");
        restored = TakeoverSession.restore(s.snapshot(), 50);
        check(!restored.claimAchievement() && !restored.emergencyRelease(50), "finished remains terminal");
        s = ordinary(true); s.emergencyRelease(3600);
        check(s.getReleaseReason() == ReleaseReason.EMERGENCY, "emergency before same tick expiry");
        s.completeRelease(3600); check(!s.claimAchievement(), "emergency alone no achievement");
    }
    private static void mechanical() {
        var s = TakeoverSession.exoskeleton(100);
        long end = s.getDeadline();
        for (long t = 100; t < 700; t++) {
            check(s.requestBorrow(t, Blocker.NONE) == Result.WRONG_KIND, "mechanical borrow rejected");
            check(s.startStruggle(t, List.of(0)) == Result.WRONG_KIND, "mechanical struggle rejected");
            check(s.submitQte(t, 0, 0) == Result.WRONG_KIND, "mechanical forged QTE rejected");
            check(!s.returnControl(t) && !s.hasStruggleOpportunity(), "no mechanical exchange or chance");
            check(s.getDeadline() == end && s.getPhase() == Phase.CONTROLLED, "input never changes awake timer");
        }
        s.tick(700); check(s.getPhase() == Phase.SLEEPING, "automatic 30 second sleep");
        check(!s.snapshot().struggleUsed(), "mechanical sleep not QTE failure");
        s.tick(899); check(s.getPhase() == Phase.SLEEPING, "mechanical sleep duration");
        s.tick(900); check(s.getReleaseReason() == ReleaseReason.SLEEP, "no expiry release route");
        s.completeRelease(900);
        check(!s.claimAchievement() && !s.claimOranges(true), "no mechanical rewards");
        s = TakeoverSession.exoskeleton(0); s.tick(10000);
        check(s.needsReleaseFallback() && s.snapshot().releaseDeadline() == 1200, "delayed poll cannot extend hard limit");
    }
    private static void persistenceAndLimits() {
        var s = ordinary(true); s.requestBorrow(400, Blocker.NONE);
        var restored = TakeoverSession.restore(s.snapshot(), 450);
        check(restored.getPhase() == Phase.BORROWED && restored.getDeadline() == 3600, "borrow restored without resetting");
        restored = TakeoverSession.restore(s.snapshot(), 1950);
        check(restored.getPhase() == Phase.CONTROLLED && restored.snapshot().nextBorrowAt() == 2000,
            "missed borrow boundary anchors cooldown to actual due tick");
        s = ordinary(true); s.startStruggle(0, List.of(0));
        restored = TakeoverSession.restore(s.snapshot(), 1000);
        check(restored.getReleaseReason() == ReleaseReason.INTERRUPTED && restored.snapshot().struggleUsed()
            && !restored.snapshot().slept(), "QTE reload recovers without failure");
        restored.completeRelease(1000);
        check(!restored.claimOranges(true) && !restored.claimAchievement(), "disconnect no rewards");
        s.tick(200); restored = TakeoverSession.restore(s.snapshot(), 201);
        check(restored.getReleaseReason() == ReleaseReason.INTERRUPTED, "sleep reload skips transition");
        s = TakeoverSession.exoskeleton(0); restored = TakeoverSession.restore(s.snapshot(), 1);
        check(restored.getReleaseReason() == ReleaseReason.INTERRUPTED, "mechanical restart safely releases");
        s = success(); restored = TakeoverSession.restore(s.snapshot(), 9); restored.completeRelease(10);
        check(restored.claimAchievement(), "pending success saved across restart");
        final var clock = ordinary(true); clock.tick(100);
        rejects(() -> clock.tick(99), "backward tick rejected");
        rejects(() -> TakeoverSession.restore(clock.snapshot(), 99), "backward restore rejected");
        rejects(() -> TakeoverSession.ordinary(-1, 100, false, false), "negative start rejected");
        rejects(() -> TakeoverSession.ordinary(0, 6001, false, false), "duration cap");
        rejects(() -> TakeoverSession.ordinary(0, 0, false, false), "zero duration");
        rejects(() -> TakeoverSession.exoskeleton(Long.MAX_VALUE - 10), "overflow rejected");
        s = TakeoverSession.ordinary(Long.MAX_VALUE - 7000, 6000, true, false);
        s.startStruggle(Long.MAX_VALUE - 1100, List.of(0));
        s.tick(Long.MAX_VALUE);
        check(s.needsReleaseFallback(), "long ticks never overflow into renewed control");
        var snap = ordinary(true).snapshot();
        var invalid = new Snapshot(Kind.EXOSKELETON, snap.startedAt(), snap.deadline(), snap.hardDeadline(),
            snap.sleepOutcome(), snap.strictBorrow(), snap.initiallyEligible(), snap.compensationCancelled(), snap.phase(),
            snap.lastTick(), snap.phaseUntil(), snap.releaseDeadline(), snap.nextBorrowAt(), snap.borrowedBefore(),
            snap.struggleUsed(), snap.challengeAt(), snap.challengeUntil(), snap.sequence(), snap.qteIndex(),
            snap.lastInputAt(), snap.breakoutSucceeded(), snap.slept(), snap.releaseReason(),
            snap.achievementClaimed(), snap.orangesClaimed());
        rejects(() -> TakeoverSession.restore(invalid, 0), "cross-branch corrupt snapshot rejected");
        for (int duration = 1; duration <= 6000; duration += 31) {
            s = TakeoverSession.ordinary(10, duration, true, false);
            s.startStruggle(10, List.of(0, 1, 2, 3));
            s.tick(10000);
            check(s.getPhase() == Phase.RELEASING && s.needsReleaseFallback()
                && s.getHardDeadline() == 10 + duration + 600, "hard bound sweep");
        }
    }
    private static void integrationApi() {
        check(Kind.ORDINARY.ordinal() == 0 && Kind.EXOSKELETON.ordinal() == 1, "kind wire order");
        check(List.of(Phase.values()).equals(List.of(Phase.CONTROLLED, Phase.BORROWED,
            Phase.STRUGGLE, Phase.SLEEPING, Phase.RELEASING,
            Phase.FINISHED)), "phase wire order");
        var a = ordinary(true);
        var b = ordinary(true);
        check(a.startStruggle(0, 1234567L, 12) == Result.ACCEPTED, "seed API");
        b.startStruggle(0, 1234567L, 12);
        check(a.snapshot().sequence().equals(b.snapshot().sequence()), "deterministic server seed");
        check(a.getExpectedKey() >= 0, "input is active immediately after confirmation");
        int first = a.getExpectedKey();
        check(first >= 0 && first <= 3 && a.getQteIndex() == 0 && a.getQteLength() == 12,
            "direction mapping and progress getters");
        check(a.submit(0, 0, first) == Result.ACCEPTED && a.getQteIndex() == 1, "submit alias");
        var original = a.snapshot().sequence();
        check(a.startStruggle(0, 999L, 12) == Result.USED
            && original.equals(a.snapshot().sequence()), "seed cannot reroll used attempt");
        for (int i = 1; i < 12; i++) {
            a.tick(4L * i);
            check(a.submit(4L * i, i, a.getExpectedKey()) == Result.ACCEPTED, "seeded progression");
        }
        check(a.getExpectedKey() == -1 && a.isBreakoutSucceeded(), "no wrap after completion");
        check(!a.beginSleep(44), "cannot overwrite successful breakout");
        check(a.finish(44) && !a.finish(44), "finish alias idempotent");
        a = ordinary(true);
        check(!a.beginSleep(0), "ordinary waiting never becomes punishment sleep");
        a.startStruggle(0, 123L, 5);
        check(a.beginSleep(0) && !a.beginSleep(1), "trusted failure enters sleep once");
        check(a.getPhaseUntil() == 200, "repeated beginSleep never resets sleep timer");
        a = TakeoverSession.exoskeleton(0);
        check(!a.beginSleep(599) && a.beginSleep(600), "mechanical sleep deadline guarded");
        a = ordinary(true); a.startStruggle(0, 123L, 5); a.tick(5000);
        check(a.getReleaseReason() == ReleaseReason.SLEEP && a.getReleaseDeadline() == 1000,
            "delayed polling preserves earlier challenge failure and sleep boundaries");
    }
    private static void configurableWindows() {
        for (long duration : new long[] {100, 101, 599, 600, 1199, 1200}) {
            var s = TakeoverSession.exoskeleton(75, duration);
            check(s.getDeadline() == 75 + duration && s.getHardDeadline() == 675 + duration,
                "configured mechanical deadlines");
            var saved = s.snapshot();
            var restored = TakeoverSession.restore(saved, 75);
            check(restored.getDeadline() == s.getDeadline()
                && restored.getReleaseReason() == ReleaseReason.INTERRUPTED, "configured restore accepted");
            s.tick(74 + duration); check(s.getPhase() == Phase.CONTROLLED, "configured awake boundary");
            s.tick(75 + duration); check(s.getPhase() == Phase.SLEEPING, "configured sleep boundary");
            s.tick(275 + duration); s.finish(275 + duration);
            check(!s.claimOranges(true) && !s.claimAchievement(), "configured branch reward isolation");
        }
        rejects(() -> TakeoverSession.exoskeleton(0, 99), "below 5 seconds rejected");
        rejects(() -> TakeoverSession.exoskeleton(0, 1201), "above 60 seconds rejected");
        for (long remaining : new long[] {1, 20, 199}) {
            var s = ordinary(true);
            long now = 3600 - remaining;
            check(s.startStruggle(now, 321L, 10) == Result.INSUFFICIENT_TIME, "seeded late QTE rejected");
            check(s.startStruggle(now, List.of(0)) == Result.INSUFFICIENT_TIME, "explicit late QTE rejected");
            check(!s.isStruggleUsed() && s.getQteLength() == 0 && s.getPhase() == Phase.CONTROLLED,
                "refused attempt consumes no state");
            s.tick(3600); check(s.getPhase() == Phase.SLEEPING && s.hasSlept(),
                "late refusal ends in ordinary sleep");
        }
        var s = ordinary(true); s.requestBorrow(3401, Blocker.NONE);
        check(s.startStruggle(3401, 123L, 10) == Result.INSUFFICIENT_TIME && s.hasPlayerControl(),
            "late QTE does not revoke borrow");
        s = ordinary(true);
        check(s.startStruggle(3400, 123L, 50) == Result.ACCEPTED, "exact full window accepted");
        s.tick(3599); check(s.getPhase() == Phase.STRUGGLE, "full 200 tick challenge available");
        s.tick(3600); check(s.getPhase() == Phase.SLEEPING, "confirmed challenge not swallowed by expiry tie");
        check(s.getDeadline() == 3600 && s.getHardDeadline() == 4200, "no deadline extension for QTE");
        s = ordinary(true); s.startStruggle(3400, 123L, 50);
        for (int i = 0; i < 50; i++) {
            long now = 3400 + i * 4L;
            s.tick(now); check(s.submit(now, i, s.getExpectedKey()) == Result.ACCEPTED, "longest QTE fits window");
        }
        check(s.isBreakoutSucceeded(), "last full-window input succeeds");
    }

    private static void sleepOutcomes() {
        var timed = TakeoverSession.ordinary(0, 600, true, false, SleepOutcome.TRANSFUR);
        check(timed.getHardDeadline() == 1200, "transfur adds no second control timer");
        timed.tick(600);
        check(timed.getPhase() == Phase.SLEEPING, "configured timer ends in sleep");
        timed.tick(800);
        check(timed.getPhase() == Phase.RELEASING
                && timed.getReleaseReason() == ReleaseReason.TRANSFUR,
            "configured sleep resolves to permanent transfur");
        check(timed.completeRelease(800) && !timed.claimOranges(true),
            "permanent transfur never grants sleep compensation");

        var failed = TakeoverSession.ordinary(0, 3600, true, false, SleepOutcome.TRANSFUR);
        failed.startStruggle(0, List.of(0));
        failed.tick(200);
        failed.tick(400);
        check(failed.getPhase() == Phase.RELEASING
                && failed.getReleaseReason() == ReleaseReason.TRANSFUR,
                "failed escape uses the configured sleep outcome");
        check(failed.fallbackTransfur(400)
                && failed.getReleaseReason() == ReleaseReason.SLEEP,
                "failed permanent conversion falls back to safe release");

        var original = TakeoverSession.ordinary(0, 600, true, false, SleepOutcome.RELEASE);
        original.tick(600);
        check(original.getPhase() == Phase.SLEEPING, "default timer expiry sleeps");
        original.tick(800);
        check(original.getPhase() == Phase.RELEASING
                && original.getReleaseReason() == ReleaseReason.SLEEP,
                "disabled option wakes through safe release");
    }

    // Optional real-NBT tests use reflection so the default suite remains pure Java / Minecraft-free.
    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        try { return target.getClass().getMethod(name, types).invoke(target, args); }
        catch (java.lang.reflect.InvocationTargetException ex) {
            if (ex.getCause() instanceof IllegalArgumentException invalid) throw invalid;
            throw ex;
        }
    }
    private static void nbtCodec() throws Exception {
        Class<?> codec = Class.forName("net.parkabird.changedsynergy.ai.TakeoverSessionNbt");
        Class<?> tagType = Class.forName("net.minecraft.nbt.CompoundTag");
        var write = codec.getMethod("write", TakeoverSession.class);
        var read = codec.getMethod("read", tagType, long.class);
        var sessions = new ArrayList<TakeoverSession>();
        sessions.add(ordinary(true));
        var s = ordinary(true); s.requestBorrow(400, Blocker.NONE); sessions.add(s);
        s = ordinary(true); s.startStruggle(0, 321L, 10); s.submit(0, 0, s.getExpectedKey()); sessions.add(s);
        s = ordinary(true); s.startStruggle(0, 321L, 10); s.tick(200); sessions.add(s);
        sessions.add(success());
        s = success(); s.finish(8); s.claimAchievement(); sessions.add(s);
        s = sleep(true); s.finish(400); s.claimOranges(true); sessions.add(s);
        s = TakeoverSession.ordinary(0, 600, true, false, SleepOutcome.TRANSFUR);
        s.tick(600); s.tick(800); sessions.add(s);
        for (long duration : new long[] {100, 600, 1200}) sessions.add(TakeoverSession.exoskeleton(0, duration));
        for (var original : sessions) {
            Object tag = write.invoke(null, original);
            var decoded = (TakeoverSession) read.invoke(null, tag, original.getLastTick());
            check(decoded.snapshot().equals(TakeoverSession.restore(original.snapshot(), original.getLastTick()).snapshot()),
                "NBT roundtrip retains all snapshot fields and restart policy");
        }
        Object valid = write.invoke(null, ordinary(true));
        Object legacy = invoke(valid, "copy", new Class<?>[0]);
        invoke(legacy, "putInt", new Class<?>[] {String.class, int.class}, "version", 1);
        invoke(legacy, "remove", new Class<?>[] {String.class}, "sleepOutcome");
        var legacyDecoded = (TakeoverSession) read.invoke(null, legacy, 0L);
        check(legacyDecoded.getSleepOutcome() == SleepOutcome.RELEASE,
                "version 1 snapshots migrate to ordinary sleep release");
        for (Object key : (java.util.Set<?>) invoke(valid, "getAllKeys", new Class<?>[0])) {
            Object missing = invoke(valid, "copy", new Class<?>[0]);
            invoke(missing, "remove", new Class<?>[] {String.class}, key);
            badNbt(read, missing, 0, "every field required: " + key);
            Object wrongType = invoke(valid, "copy", new Class<?>[0]);
            invoke(wrongType, "putDouble", new Class<?>[] {String.class, double.class}, key, 1.0);
            badNbt(read, wrongType, 0, "exact NBT type required: " + key);
        }
        for (Object[] mutation : new Object[][] {
                {"putInt", "version", int.class, 5}, {"putString", "kind", String.class, "UNKNOWN"},
                {"putString", "phase", String.class, "UNKNOWN"},
                {"putString", "sleepOutcome", String.class, "UNKNOWN"},
                {"putString", "releaseReason", String.class, "UNKNOWN"},
                {"putByte", "struggleUsed", byte.class, (byte) 2},
                {"putInt", "qteIndex", int.class, -1},
                {"putLong", "startedAt", long.class, -1L},
                {"putLong", "phaseUntil", long.class, -2L},
                {"putLong", "phaseUntil", long.class, 20L},
                {"putLong", "releaseDeadline", long.class, -2L},
                {"putLong", "lastInputAt", long.class, 0L},
                {"putLong", "challengeAt", long.class, -2L},
                {"putLong", "nextBorrowAt", long.class, 4201L},
                {"putLong", "hardDeadline", long.class, 4201L},
                {"putIntArray", "sequence", int[].class, new int[51]},
                {"putIntArray", "sequence", int[].class, new int[] {4}}}) {
            Object bad = invoke(valid, "copy", new Class<?>[0]);
            invoke(bad, (String) mutation[0], new Class<?>[] {String.class, (Class<?>) mutation[2]}, mutation[1], mutation[3]);
            badNbt(read, bad, 0, "invalid codec boundary " + mutation[1]);
        }
        for (long duration : new long[] {99, 1201}) {
            Object bad = write.invoke(null, TakeoverSession.exoskeleton(0));
            invoke(bad, "putLong", new Class<?>[] {String.class, long.class}, "deadline", duration);
            invoke(bad, "putLong", new Class<?>[] {String.class, long.class}, "hardDeadline", duration + 600);
            badNbt(read, bad, 0, "snapshot mechanical duration range");
        }
        s = ordinary(true); s.startStruggle(0, 321L, 5);
        Object bad = write.invoke(null, s);
        invoke(bad, "putLong", new Class<?>[] {String.class, long.class}, "challengeAt", 3500L);
        invoke(bad, "putLong", new Class<?>[] {String.class, long.class}, "challengeUntil", 3700L);
        badNbt(read, bad, 0, "saved challenge cannot exceed deadline");
        Object overflow = write.invoke(null, TakeoverSession.ordinary(Long.MAX_VALUE - 7000, 6000, false, false));
        invoke(overflow, "putLong", new Class<?>[] {String.class, long.class}, "deadline", Long.MAX_VALUE);
        badNbt(read, overflow, Long.MAX_VALUE, "overflow normalized to IllegalArgumentException");
        badNbt(read, null, 0, "null rejected");
        badNbt(read, valid, -1, "backward now rejected");
    }
    private static void badNbt(java.lang.reflect.Method read, Object tag, long now, String reason) throws Exception {
        try { read.invoke(null, tag, now); }
        catch (java.lang.reflect.InvocationTargetException ex) {
            check(ex.getCause() instanceof IllegalArgumentException, reason + " exception type"); return;
        }
        throw new AssertionError(reason);
    }
    public static void main(String[] args) throws Exception {
        borrowing(); qte(); rewardsAndRecovery(); mechanical(); persistenceAndLimits(); integrationApi();
        configurableWindows(); sleepOutcomes();
        if (args.length > 0 && args[0].equals("--nbt")) nbtCodec();
        System.out.println("TakeoverSessionTest: " + checks + " checks passed");
    }
}
