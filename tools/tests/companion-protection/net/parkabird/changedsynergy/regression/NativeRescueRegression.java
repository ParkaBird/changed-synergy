package net.parkabird.changedsynergy.regression;

import java.util.HashMap;
import java.util.Map;
import net.ltxprogrammer.changed.ability.GrabEntityAbility;
import net.ltxprogrammer.changed.entity.LivingEntityDataExtension;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.*;

/** Executes the same organic evacuation with Changed alone and with Addon installed. */
final class NativeRescueRegression {
    private static int checks;

    static void run(MinecraftServer server, ServerPlayer owner) {
        var level = server.overworld();
        var pet = ChangedEntities.BEIFENG.get().create(level);
        var threat = EntityType.ZOMBIE.create(level);
        var origin = level.getSharedSpawnPos().above(30);
        Map<BlockPos, BlockState> old = new HashMap<>();
        boolean enabled = ChangedSynergyConfig.COMMON.bondedEmergencyRescue.get();
        OrganicOwnerEvacuationGoal goal = null;
        try {
            for (int x = -2; x <= 3; x++) for (int y = -1; y <= 3; y++) for (int z = -2; z <= 2; z++) {
                var pos = origin.offset(x, y, z);
                old.put(pos, level.getBlockState(pos));
                level.setBlock(pos, (y == -1 ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 2);
            }
            owner.setPos(Vec3.atBottomCenterOf(origin));
            pet.setPos(owner.getX() + 1.2, owner.getY(), owner.getZ());
            threat.setPos(owner.getX() - 1, owner.getY(), owner.getZ());
            pet.setNoAi(true); threat.setNoAi(true);
            level.addFreshEntity(pet); level.addFreshEntity(threat);
            check(LatexSocialMemory.isOrganic(pet) && BondedSuitService.ability(pet) != null,
                    "Organic adult receives a native hold capability without requiring Addon");
            var goals = pet.goalSelector.getAvailableGoals().stream().map(wrapped -> wrapped.getGoal())
                    .filter(candidate -> candidate instanceof OrganicOwnerEvacuationGoal).toList();
            check(goals.size() == 1, "Base and optional registration install exactly one evacuation goal");
            goal = (OrganicOwnerEvacuationGoal)goals.get(0);
            LatexSocialMemory.addBond(pet, owner);
            LatexSocialMemory.setFollowingOwner(pet, true);
            while (LatexSocialMemory.organicRescueMode(pet) != LatexSocialMemory.OrganicRescueMode.LOW_HEALTH)
                LatexSocialMemory.cycleOrganicRescueMode(pet);
            ChangedSynergyConfig.COMMON.bondedEmergencyRescue.set(true);
            ProcessTransfur.setPlayerTransfurVariant(owner, ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get(), null, 1, false);
            var form = ProcessTransfur.getPlayerTransfurVariant(owner);
            owner.setHealth(4); owner.setLastHurtByMob(threat);
            check(goal.canUse(), "Nearby wounded transformed owner can enter organic evacuation");
            goal.start(); goal.tick();
            var hold = BondedSuitService.ability(pet);
            check(hold.grabbedEntity == owner && !hold.suited
                            && LatexSocialMemory.isOrganicEvacuationActive(pet, owner),
                    "Organic rescue begins a physical arm hold instead of transforming its owner");
            hold.attackDown = hold.useDown = true;
            hold.tickIdle();
            check(hold.grabbedEntity == owner && !hold.attackDown && !hold.useDown,
                    "Native safety retains a permanent-form passenger and clears dangerous input");
            check(ProcessTransfur.getPlayerTransfurVariant(owner) == form && owner.isAlive(),
                    "Evacuation preserves the owner's existing form and life");
            ChangedSynergyConfig.COMMON.bondedEmergencyRescue.set(false);
            check(!goal.canContinueToUse(), "Disabling rescue stops a running evacuation");
            goal.stop();
            check(hold.grabbedEntity == null && GrabEntityAbility.getGrabber(owner) == null && !owner.noPhysics,
                    "Stopping evacuation clears both native references and the movement lock");
            check(ProcessTransfur.getPlayerTransfurVariant(owner) == form && owner.isAlive(),
                    "Release does not reverse or kill a permanently transformed passenger");
            ChangedSynergyConfig.COMMON.bondedEmergencyRescue.set(true);
            check(BondedSuitService.isRescueGraceActive(owner) && !goal.canUse(),
                    "Release grace prevents immediate repeated organic rescue");
            System.out.println("NATIVE_RESCUE_REGRESSION_PASS: " + checks + " checks");
        } finally {
            if (goal != null) goal.stop();
            ChangedSynergyConfig.COMMON.bondedEmergencyRescue.set(enabled);
            ((LivingEntityDataExtension)owner).setGrabbedBy(null); owner.noPhysics = false;
            ProcessTransfur.removePlayerTransfurVariant(owner); owner.setHealth(owner.getMaxHealth());
            owner.setLastHurtByMob(null); pet.discard(); threat.discard();
            for (var entry : old.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 2);
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
        System.out.println("NATIVE_RESCUE_PASS: " + message);
    }
}
