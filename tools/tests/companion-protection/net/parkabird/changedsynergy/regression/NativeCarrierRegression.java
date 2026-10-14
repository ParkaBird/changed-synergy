package net.parkabird.changedsynergy.regression;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.ability.GrabEntityAbility;
import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.LivingEntityDataExtension;
import net.ltxprogrammer.changed.entity.ai.DarkLatexAttackCondition;
import net.ltxprogrammer.changed.entity.beast.AbstractDarkLatexEntity;
import net.ltxprogrammer.changed.init.ChangedAbilities;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.parkabird.changedsynergy.ai.BondedSuitService;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.ai.TakeoverService;
import net.parkabird.changedsynergy.event.LatexSocialEvents;

/** Native grab inputs and the original carrier's melee/look controls, in both dependency sets. */
public final class NativeCarrierRegression {
    private static int checks;

    public static void run(MinecraftServer server, ServerPlayer owner, ServerPlayer controller) throws Exception {
        var level = server.overworld();
        BlockPos origin = level.getSharedSpawnPos().above(30);
        Map<BlockPos, BlockState> old = new HashMap<>();
        for (int x = -3; x <= 5; x++) for (int z = -3; z <= 3; z++) for (int y = -1; y <= 3; y++) {
            BlockPos pos = origin.offset(x, y, z);
            old.put(pos, level.getBlockState(pos));
            level.setBlock(pos, (y == -1 ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 2);
        }
        var carrier = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        var observer = ChangedEntities.LATEX_HYPNO_CAT.get().create(level);
        var zombie = EntityType.ZOMBIE.create(level);
        var villager = EntityType.VILLAGER.create(level);
        try {
            owner.setPos(Vec3.atBottomCenterOf(origin));
            controller.setPos(owner.position());
            owner.setHealth(owner.getMaxHealth());
            ProcessTransfur.removePlayerTransfurVariant(owner);
            for (var entity : new net.minecraft.world.entity.Mob[]{carrier, observer, zombie, villager}) {
                entity.setPos(owner.getX() + 1.2, owner.getY(), owner.getZ());
                entity.setNoAi(true);
                level.addFreshEntity(entity);
            }

            // Reproduce the native hold-key path; no direct field assignment substitutes for grab/suit.
            ProcessTransfur.setPlayerTransfurVariant(controller, ChangedTransfurVariants.LATEX_HYPNO_CAT.get(), null, 1, false);
            var hold = IAbstractChangedEntity.forPlayer(controller).getAbilityInstanceSafe(
                    ChangedAbilities.GRAB_ENTITY_ABILITY.get()).orElseThrow();
            check(hold.grabEntity(villager) && hold.suitEntity(villager) && hold.suited,
                    "Player can natively grab and suit a non-player creature");
            var ticks = hold.getController().getClass().getDeclaredField("holdTicks");
            ticks.setAccessible(true);
            ticks.setInt(hold.getController(), 40);
            hold.tick();
            check(!hold.suited && hold.grabbedEntity == villager,
                    "First native long-press unwraps to arms instead of being rejected as a duplicate grab");
            ticks.setInt(hold.getController(), 40);
            hold.tick();
            check(hold.grabbedEntity == null && GrabEntityAbility.getGrabber(villager) == null,
                    "Second native long-press releases immediately without waiting for strength decay");
            ProcessTransfur.removePlayerTransfurVariant(controller);

            LatexSocialMemory.addBond(carrier, owner);
            LatexSocialMemory.setFollowingOwner(carrier, true);
            // Let the ownership transition finish at the real selector-safe tick boundary.
            net.parkabird.changedsynergy.compat.ChangedExtrasCompat.onLivingTick(new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(carrier));
            net.parkabird.changedsynergy.ai.SafeEntityMutationQueue.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server));
            ((AbstractDarkLatexEntity)carrier).setAttackCondition(DarkLatexAttackCondition.ALWAYS);
            BondedSuitService.ensureGrabAbility(carrier);
            var goals = carrier.goalSelector.getAvailableGoals().stream().map(value -> value.getGoal()).toList();
            double movementSpeed = carrier.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            check(TakeoverService.beginBondedConfinement(carrier, owner), "A valid carrier starts an ordinary takeover");
            check(TakeoverService.autonomousCarrier(carrier) && TakeoverService.hidesPlayer(owner),
                    "Ordinary contained host is hidden while its carrier retains autonomy");
            var activeHold=BondedSuitService.ability(carrier);
            var heading=carrier.position();owner.setPos(heading.add(2,0,0));
            activeHold.grabbedHasControl=true;activeHold.tickIdle();
            check(!activeHold.grabbedHasControl&&carrier.position().distanceToSqr(heading)<.001
                            &&owner.position().distanceToSqr(heading)<.001&&owner.noPhysics&&!carrier.noPhysics,
                    "Native tame-owner control reset cannot pull a takeover carrier back to its hidden host");
            LatexSocialMemory.beginFriendlySuit(carrier,owner,false,false);
            BondedSuitService.tickFriendlySuit(owner);
            check(!activeHold.grabbedHasControl,"Old friendly-suit maintenance yields to the active takeover controller");
            LatexSocialMemory.endFriendlySuit(carrier,owner);
            check(!TargetingConditions.forNonCombat().ignoreLineOfSight().test(carrier, owner)
                            && !TargetingConditions.forNonCombat().ignoreLineOfSight().test(observer, owner),
                    "Carrier and bystanders do not look at the hidden host as a second body");
            observer.setTarget(owner);
            check(observer.getTarget() == null, "A bystander cannot directly target a contained host");

            var attack = new LivingAttackEvent(carrier, carrier.damageSources().mobAttack(zombie), 1);
            LatexSocialEvents.onSuitedCreatureAttacked(attack);
            check(!attack.isCanceled(), "Takeover carrier retains native hurt and retaliation instead of companion damage forwarding");
            carrier.setLastHurtByMob(zombie);
            zombie.setPos(carrier.getX() + .8, carrier.getY(), carrier.getZ());
            carrier.setTarget(zombie);
            check(carrier.getTarget() == zombie, "Existing pet policy accepts the carrier's actual attacker");
            var melee = goals.stream().filter(goal -> goal.getClass() == MeleeAttackGoal.class)
                    .map(goal -> (MeleeAttackGoal)goal).findFirst().orElseThrow();
            var last = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(MeleeAttackGoal.class, "f_25550_");
            last.setAccessible(true); last.setLong(melee, level.getGameTime() - 20);
            check(melee.canUse(), "Original melee goal starts despite the takeover's occupied grab slot");
            check(!melee.canUse(), "Native melee admission still retains its 20-tick search throttle");
            float health = zombie.getHealth();
            melee.start(); melee.tick();
            check(zombie.getHealth() < health || zombie.isRemoved(),
                    "Original melee goal actually attacks the attacker while carrying the player");
            carrier.getLookControl().setLookAt(carrier.getX() - 10, carrier.getEyeY(), carrier.getZ(), 20, 20);
            double desired = carrier.getLookControl().getWantedX();
            carrier.setDeltaMovement(new Vec3(.15, 0, 0));
            TakeoverService.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server));
            check(carrier.getLookControl().getWantedX() == desired,
                    "Takeover maintenance does not overwrite the native goal's look direction with velocity");
            check(carrier.goalSelector.getAvailableGoals().stream().map(value -> value.getGoal()).toList().equals(goals)
                            && carrier.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) == movementSpeed,
                    "Takeover preserves the original goals and movement speed");
            melee.stop();
            check(TakeoverService.forceReleaseByCommand(owner) && !TakeoverService.hidesPlayer(owner)
                            && !TakeoverService.autonomousCarrier(carrier), "Release restores the normal visibility and AI gates");

            // External transformations must clean an orphaned old link without erasing a newer grab.
            for (boolean newer : new boolean[]{false, true}) {
                owner.setPos(Vec3.atBottomCenterOf(origin));
                carrier.setPos(owner.getX() + 1.2, owner.getY(), owner.getZ());
                carrier.setTarget(null);
                check(TakeoverService.beginBondedConfinement(carrier, owner), "External replacement case starts normally");
                var active = TakeoverService.class.getDeclaredField("ACTIVE");
                active.setAccessible(true);
                Object entry = ((Map<?, ?>)active.get(null)).get(owner.getUUID());
                carrier.discard();
                ((LivingEntityDataExtension)owner).setGrabbedBy(newer ? observer : carrier);
                owner.noPhysics = true;
                var interrupt = TakeoverService.class.getDeclaredMethod("interruptForExternalTransfur", ServerPlayer.class, entry.getClass());
                interrupt.setAccessible(true); interrupt.invoke(null, owner, entry);
                check(!TakeoverService.active(owner) && !TakeoverService.blocksPlayerInput(owner),
                        "External transformation removes the orphaned session's input lock");
                check(((LivingEntityDataExtension)owner).getGrabbedBy() == (newer ? observer : null)
                                && owner.noPhysics == newer,
                        "Orphan cleanup clears only the old carrier and preserves a newer creature's hold");
                ((LivingEntityDataExtension)owner).setGrabbedBy(null); owner.noPhysics = false;
                ProcessTransfur.removePlayerTransfurVariant(owner);
                if (!newer) {
                    carrier = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
                    carrier.setNoAi(true); carrier.setPos(owner.getX() + 1.2, owner.getY(), owner.getZ());
                    level.addFreshEntity(carrier);
                    LatexSocialMemory.addBond(carrier, owner);
                    BondedSuitService.ensureGrabAbility(carrier);
                }
            }
            System.out.println("NATIVE_CARRIER_REGRESSION_PASS: " + checks + " checks");
        } finally {
            TakeoverService.forceReleaseByCommand(owner);
            ((LivingEntityDataExtension)owner).setGrabbedBy(null); owner.noPhysics = false;
            ProcessTransfur.removePlayerTransfurVariant(owner);
            ProcessTransfur.removePlayerTransfurVariant(controller);
            carrier.discard(); observer.discard(); zombie.discard(); villager.discard();
            for (var state : old.entrySet()) level.setBlock(state.getKey(), state.getValue(), 2);
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
        System.out.println("NATIVE_CARRIER_PASS: " + message);
    }
}
