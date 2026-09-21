package net.parkabird.changedsynergy.ai;

import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.parkabird.changedsynergy.ai.TakeoverSession.Kind;
import net.parkabird.changedsynergy.ai.TakeoverSession.Phase;
import net.parkabird.changedsynergy.ai.TakeoverSession.ReleaseReason;
import net.parkabird.changedsynergy.ai.TakeoverSession.SleepOutcome;
import net.parkabird.changedsynergy.ai.TakeoverSession.Snapshot;

/** Strict, versioned codec for the session payload only; identity and world data belong to the owner.
 * Never send this tag to clients: it contains the complete server QTE sequence and reward state.
 * Unknown extra fields are tolerated, but missing/wrongly typed fields never silently default.
 */
public final class TakeoverSessionNbt {
    private static final int VERSION = 4;
    private TakeoverSessionNbt() { }

    public static CompoundTag write(TakeoverSession session) {
        if (session == null) throw new IllegalArgumentException("null takeover session");
        Snapshot s = session.snapshot();
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", VERSION);
        tag.putString("kind", s.kind().name());
        tag.putLong("startedAt", s.startedAt());
        tag.putLong("deadline", s.deadline());
        tag.putLong("hardDeadline", s.hardDeadline());
        tag.putString("sleepOutcome", s.sleepOutcome().name());
        tag.putBoolean("strictBorrow", s.strictBorrow());
        tag.putBoolean("initiallyEligible", s.initiallyEligible());
        tag.putBoolean("compensationCancelled", s.compensationCancelled());
        tag.putString("phase", s.phase().name());
        tag.putLong("lastTick", s.lastTick());
        tag.putLong("phaseUntil", s.phaseUntil());
        tag.putLong("releaseDeadline", s.releaseDeadline());
        tag.putLong("nextBorrowAt", s.nextBorrowAt());
        tag.putBoolean("borrowedBefore", s.borrowedBefore());
        tag.putBoolean("struggleUsed", s.struggleUsed());
        tag.putLong("challengeAt", s.challengeAt());
        tag.putLong("challengeUntil", s.challengeUntil());
        tag.putIntArray("sequence", s.sequence().stream().mapToInt(Integer::intValue).toArray());
        tag.putInt("qteIndex", s.qteIndex());
        tag.putLong("lastInputAt", s.lastInputAt());
        tag.putBoolean("breakoutSucceeded", s.breakoutSucceeded());
        tag.putBoolean("slept", s.slept());
        tag.putString("releaseReason", s.releaseReason().name());
        tag.putBoolean("achievementClaimed", s.achievementClaimed());
        tag.putBoolean("orangesClaimed", s.orangesClaimed());
        return tag;
    }

    /** Uses restore's restart policy, not a raw resume. now must share the saved monotonic timebase.
     * Invalid payloads (including arithmetic overflow) throw IllegalArgumentException so the owner
     * can perform safe recovery; never create a fresh session as a decoding fallback.
     */
    public static TakeoverSession read(CompoundTag tag, long now) {
        if (tag == null) throw new IllegalArgumentException("null takeover tag");
        try {
            int version = integer(tag, "version");
            if (version != 1 && version != 3 && version != VERSION)
                throw new IllegalArgumentException("unsupported takeover snapshot version");
            require(tag, "sequence", Tag.TAG_INT_ARRAY);
            int[] sequence = tag.getIntArray("sequence");
            if (sequence.length > 50) throw new IllegalArgumentException("oversized QTE sequence");
            Kind kind = Kind.valueOf(string(tag, "kind"));
            String storedPhase = string(tag, "phase");
            if (version < VERSION && storedPhase.equals("RETAINED")) {
                return recovery(kind, now);
            }
            long deadline = number(tag, "deadline");
            long hardDeadline = version == VERSION
                    ? number(tag, "hardDeadline")
                    : Math.addExact(deadline, TakeoverSession.RELEASE_WAIT_TICKS);
            long lastTick = number(tag, "lastTick");
            long nextBorrowAt = number(tag, "nextBorrowAt");
            if (version < VERSION && (lastTick > hardDeadline || nextBorrowAt > hardDeadline)) {
                return recovery(kind, now);
            }
            String storedReason = string(tag, "releaseReason");
            boolean legacyExpired = version < VERSION && storedReason.equals("EXPIRED");
            Snapshot s = new Snapshot(
                kind, number(tag, "startedAt"), deadline,
                hardDeadline, version == VERSION
                        ? SleepOutcome.valueOf(string(tag, "sleepOutcome"))
                        : SleepOutcome.RELEASE,
                bool(tag, "strictBorrow"), bool(tag, "initiallyEligible"),
                legacyExpired || bool(tag, "compensationCancelled"), Phase.valueOf(storedPhase),
                lastTick, number(tag, "phaseUntil"), number(tag, "releaseDeadline"),
                nextBorrowAt, bool(tag, "borrowedBefore"), bool(tag, "struggleUsed"),
                number(tag, "challengeAt"), number(tag, "challengeUntil"),
                Arrays.stream(sequence).boxed().toList(), integer(tag, "qteIndex"),
                number(tag, "lastInputAt"), bool(tag, "breakoutSucceeded"), bool(tag, "slept"),
                legacyExpired ? ReleaseReason.INTERRUPTED : ReleaseReason.valueOf(storedReason),
                bool(tag, "achievementClaimed"), bool(tag, "orangesClaimed"));
            return TakeoverSession.restore(s, now);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("overflow in takeover snapshot", ex);
        }
    }

    private static TakeoverSession recovery(Kind kind, long now) {
        TakeoverSession session = kind == Kind.EXOSKELETON
                ? TakeoverSession.exoskeleton(now)
                : TakeoverSession.ordinary(now, 600L, false, false);
        session.interrupt(now);
        return session;
    }

    private static void require(CompoundTag tag, String key, int type) {
        if (!tag.contains(key, type)) throw new IllegalArgumentException("missing/wrong takeover field: " + key);
    }
    private static long number(CompoundTag tag, String key) {
        require(tag, key, Tag.TAG_LONG);
        return tag.getLong(key);
    }
    private static int integer(CompoundTag tag, String key) {
        require(tag, key, Tag.TAG_INT);
        return tag.getInt(key);
    }
    private static String string(CompoundTag tag, String key) {
        require(tag, key, Tag.TAG_STRING);
        return tag.getString(key);
    }
    private static boolean bool(CompoundTag tag, String key) {
        require(tag, key, Tag.TAG_BYTE);
        byte value = tag.getByte(key);
        if (value != 0 && value != 1) throw new IllegalArgumentException("invalid takeover boolean: " + key);
        return value == 1;
    }
}
