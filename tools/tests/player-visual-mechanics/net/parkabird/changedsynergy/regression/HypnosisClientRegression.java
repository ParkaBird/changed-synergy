package net.parkabird.changedsynergy.regression;

import net.minecraft.client.player.LocalPlayer;
import net.parkabird.changedsynergy.client.HypnosisQteClientState;
import net.parkabird.changedsynergy.network.HypnosisQteSyncPacket;

/** Client role and out-of-order termination checks in the hidden world fixture. */
public final class HypnosisClientRegression {
    public static void run(LocalPlayer player) {
        int source = player.getId(), victim = source + 10000;
        check(java.util.Arrays.stream(net.minecraft.client.Minecraft.getInstance().options.keyMappings)
                .noneMatch(key -> key.getName().equals("key.changed_synergy.hypnosis_contest")),
                "Obsolete contest binding absent from Controls");
        checkMovementInput(source, victim);
        HypnosisQteClientState.receive(packet(701, source, victim, HypnosisQteSyncPacket.ACTIVE, true));
        check(!HypnosisQteClientState.isControlLocked() && !HypnosisQteClientState.isViewFrozen(),
                "Caster retains control and view");
        float casterIntensity = HypnosisQteClientState.visualIntensity(0F);
        check(casterIntensity > 0F, "Caster receives the shared active QTE visuals");
        HypnosisQteClientState.receive(new HypnosisQteSyncPacket(
                701, source, 0, .5F, .25F, 240, HypnosisQteSyncPacket.ACTIVE, true, true, victim, 0));
        check(HypnosisQteClientState.gazeAlignment() == .25F,
                "Shared visual pressure follows updated server packets");
        HypnosisQteClientState.receive(packet(701, source, victim, HypnosisQteSyncPacket.FAILED, true));
        check(HypnosisQteClientState.visualIntensity(0F) == 0F
                        && !HypnosisQteClientState.isControlLocked() && !HypnosisQteClientState.isViewFrozen(),
                "Contest outcome does not mesmerize the caster");
        HypnosisQteClientState.receive(packet(701, source, victim, HypnosisQteSyncPacket.CLEAR, true));
        HypnosisQteClientState.receive(packet(702, victim, source, HypnosisQteSyncPacket.ACTIVE, false));
        check(HypnosisQteClientState.isControlLocked() && !HypnosisQteClientState.isViewFrozen(),
                "Human is restrained but can still look around");
        check(HypnosisQteClientState.visualIntensity(0F) == casterIntensity,
                "Both roles use the same active QTE visual intensity");
        HypnosisQteClientState.receive(packet(701, source, victim, HypnosisQteSyncPacket.CLEAR, true));
        check(HypnosisQteClientState.current() != null && HypnosisQteClientState.current().sessionId() == 702,
                "An old termination packet cannot clear a newer contest");
        HypnosisQteClientState.receive(packet(702, victim, source, HypnosisQteSyncPacket.CLEAR, false));
        check(HypnosisQteClientState.current() == null && !HypnosisQteClientState.isControlLocked(),
                "Matching termination restores human controls");
        System.out.println("PLAYER_HYPNOSIS_CLIENT_PASS");
    }

    private static void checkMovementInput(int source, int victim) {
        try {
            var held = HypnosisQteClientState.class.getDeclaredField("contestKeysDown");
            held.setAccessible(true);
            for (boolean caster : new boolean[]{true, false}) {
                HypnosisQteClientState.receive(packet(700, source, victim, HypnosisQteSyncPacket.ACTIVE, caster));
                for (int key : new int[]{87, 65, 83, 68}) {
                    check(HypnosisQteClientState.handleContestKey(key, 0, 1) && held.getInt(null) != 0,
                            "Movement-key press accepted for " + (caster ? "caster" : "human"));
                    int mask = held.getInt(null);
                    HypnosisQteClientState.handleContestKey(key, 0, 2);
                    check(held.getInt(null) == mask, "OS key repeat does not start another stroke");
                    HypnosisQteClientState.handleContestKey(key, 0, 0);
                    check(held.getInt(null) == 0, "Key release permits the next stroke");
                }
                check(!HypnosisQteClientState.handleContestKey(82, 0, 1), "Old R binding no longer controls the QTE");
                HypnosisQteClientState.handleContestKey(87, 0, 1);
                HypnosisQteClientState.handleContestKey(65, 0, 1);
                HypnosisQteClientState.handleContestKey(87, 0, 0);
                check(held.getInt(null) != 0, "Releasing one overlapping key does not reset the stroke");
                HypnosisQteClientState.handleContestKey(65, 0, 0);
                check(held.getInt(null) == 0, "Releasing all keys resets the stroke");
                HypnosisQteClientState.receive(packet(700, source, victim, HypnosisQteSyncPacket.CLEAR, caster));
            }
            var forward = net.minecraft.client.Minecraft.getInstance().options.keyUp;
            var previous = forward.getKey();
            try {
                forward.setKey(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(73));
                HypnosisQteClientState.receive(packet(700, source, victim, HypnosisQteSyncPacket.ACTIVE, true));
                check(HypnosisQteClientState.handleContestKey(73, 0, 1)
                                && !HypnosisQteClientState.handleContestKey(87, 0, 1),
                        "QTE follows rebound movement keys instead of hard-coded WASD");
                HypnosisQteClientState.receive(packet(700, source, victim, HypnosisQteSyncPacket.CLEAR, true));
                check(held.getInt(null) == 0, "Ending a contest clears held inputs");
            } finally { forward.setKey(previous); }
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static HypnosisQteSyncPacket packet(int session, int source, int victim, int state, boolean caster) {
        return new HypnosisQteSyncPacket(session, source, 0, .5F, 1, 240, state, true, caster, victim, 0);
    }

    private static void check(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
        System.out.println("HYPNOSIS_CLIENT_CHECK_PASS: " + message);
    }
}
