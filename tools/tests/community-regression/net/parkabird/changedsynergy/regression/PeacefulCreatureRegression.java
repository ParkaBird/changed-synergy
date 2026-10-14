package net.parkabird.changedsynergy.regression;

import net.ltxprogrammer.changed.init.ChangedEntities;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;

public final class PeacefulCreatureRegression {
    public static void run(MinecraftServer server) {
        var level = server.overworld();
        var wolf = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        var white = ChangedEntities.PURE_WHITE_LATEX_WOLF.get().create(level);
        var zombie = EntityType.ZOMBIE.create(level);
        var oldDifficulty = level.getDifficulty();
        try {
            server.setDifficulty(Difficulty.PEACEFUL, true);
            wolf.checkDespawn(); white.checkDespawn(); zombie.checkDespawn();
            check(!wolf.isRemoved() && !white.isRemoved(), "Unrelated ordinary creatures survive Peaceful");
            check(!wolf.isPersistenceRequired() && !white.isPersistenceRequired(),
                    "Peaceful survival does not disable distance despawning");
            check(zombie.isRemoved(), "Ordinary hostile monsters retain Peaceful despawning");
            System.out.println("PEACEFUL_CREATURE_REGRESSION_PASS");
        } finally {
            server.setDifficulty(oldDifficulty, true);
            wolf.discard(); white.discard(); zombie.discard();
        }
    }
    private static void check(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
        System.out.println("PEACEFUL_CREATURE_CHECK_PASS: " + message);
    }
}
