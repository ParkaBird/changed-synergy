package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.ltxprogrammer.changed.init.ChangedItems;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.event.HuntAIEvents;
import net.parkabird.changedsynergy.event.LatexSocialEvents;
import net.parkabird.changedsynergy.event.NpcDispositionEvents;

/** Real conversion, damage, timers and guard orders; excluded from the release JAR. */
final class HostilityChecks {
    private static int checks;

    @SuppressWarnings("unchecked")
    static void run(MinecraftServer server) throws Exception {
        var level = server.overworld();
        var player = player(server, "OrganicContact");
        var other = player(server, "OtherContact");
        var organic = ChangedEntities.GAS_WOLF_MALE.get().create(level);
        var guard = ChangedEntities.GAS_WOLF_MALE.get().create(level);
        var playersField = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(
                net.minecraft.server.players.PlayerList.class, "f_11197_");
        var players = (Map<UUID, ServerPlayer>)playersField.get(server.getPlayerList());
        long originalTime = level.getGameTime();
        int oldGrace = ChangedSynergyConfig.COMMON.postTransfurTruceSeconds.get();
        boolean oldSecondary = ChangedSynergyConfig.COMMON.allowSecondaryTransfur.get();
        try {
            players.put(player.getUUID(), player); players.put(other.getUUID(), other);
            level.addNewPlayer(player); level.addNewPlayer(other);
            player.setPos(level.getSharedSpawnPos().getX() + .5, 90, level.getSharedSpawnPos().getZ() + .5);
            other.setPos(player.getX() + 2, 90, player.getZ());
            for (ChangedEntity mob : new ChangedEntity[]{organic, guard}) {
                mob.setPos(player.getX() + 1, 90, player.getZ());
                level.addFreshEntity(mob);
                FactionReputation.set(mob, player, 0);
                FactionReputation.set(mob, other, 0);
            }
            for (int tick = 0; tick < 61; tick++) { player.tick(); other.tick(); }
            player.setHealth(player.getMaxHealth()); other.setHealth(other.getMaxHealth());
            ChangedSynergyConfig.COMMON.postTransfurTruceSeconds.set(1);
            ChangedSynergyConfig.COMMON.allowSecondaryTransfur.set(true);
            check(LatexSocialMemory.isOrganic(organic) && organic.getSelfVariant() != null,
                    "The reported path uses a real organic wolf and its replication form");

            LatexSocialMemory.markProvoked(organic, player);
            organic.setTarget(player); HuntMemory.seeTarget(organic, player);
            ProcessTransfur.setPlayerTransfurProgress(player, 1);
            float humanHealth = player.getHealth();
            organic.doHurtTarget(player);
            check(ProcessTransfur.getPlayerTransfurProgress(player) > 1 && !ProcessTransfur.isPlayerTransfurred(player),
                    "A real partial organic swipe does not prematurely complete conversion");
            check(player.getHealth() >= humanHealth - 1 && OrganicCombatRules.isHumanTarget(player),
                    "The human's ordinary organic swipe still has the low physical damage cap");
            check(!FactionHostilityGrace.active(organic, player), "Partial progress grants no completion truce");
            ProcessTransfur.setPlayerTransfurProgress(player,
                    (float)ProcessTransfur.getEntityTransfurTolerance(player) - .1F);
            player.invulnerableTime = 0;
            organic.doHurtTarget(player);
            check(ProcessTransfur.isPlayerTransfurred(player),
                    "The actual Changed swipe completes organic replication");
            check(LatexSocialRelation.between(organic, player) == LatexSocialRelation.SAME_SPECIES,
                    "Organic replication makes the player the source's own species");
            check(FactionHostilityGrace.active(organic, player) && organic.getTarget() == null,
                    "Actual completion immediately ends pursuit and grants configured truce");

            server.getWorldData().overworldData().setGameTime(level.getGameTime() + 21);
            check(!FactionHostilityGrace.active(organic, player), "The short fixture truce expires normally");
            float kinHealth = player.getHealth();
            boolean kinDamaged = false;
            for (int attack = 0; attack < 20 && player.isAlive(); attack++) {
                player.invulnerableTime = 0;
                kinDamaged |= player.hurt(player.damageSources().mobAttack(organic), 6);
            }
            System.out.println("HOSTILITY_CONVERSION_SURVIVAL alive=" + player.isAlive() + " health="
                    + player.getHealth() + " initial=" + kinHealth + " hurt=" + kinDamaged);
            check(!kinDamaged && player.isAlive() && player.getHealth() == kinHealth,
                    "Residual organic melee cannot beat unprovoked newly transformed kin to death");
            check(LatexSocialMemory.shouldRemainNeutral(organic, player)
                            && NpcDispositionEvents.mustRejectTarget(organic, player)
                            && !NpcDispositionEvents.hasHostileDisposition(organic, player)
                            && !HuntAIEvents.canReacquire(organic, player, true)
                            && !HuntAIEvents.isHostilePlayerRelation(organic, player),
                    "Same-species neutrality survives truce expiry across damage, acquisition and noise paths");
            var opposite = ChangedTransfurVariants.Gendered.getOpposite(organic.getSelfVariant()).orElseThrow();
            ProcessTransfur.setPlayerTransfurVariant(player, opposite, null, 1, false);
            check(LatexSocialMemory.shouldRemainNeutral(organic, player), "Paired-gender kin remain neutral too");

            ProcessTransfur.setPlayerTransfurVariant(player, ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get(), null, 1, false);
            check(!LatexSocialRelation.between(organic, player).isFriendlyTransformed()
                            && NpcDispositionEvents.hasHostileDisposition(organic, player),
                    "A genuine transformed outsider remains an eligible organic enemy");
            player.setHealth(player.getMaxHealth()); player.invulnerableTime = 0;
            float maximum = player.getHealth();
            var zombie = EntityType.ZOMBIE.create(level);
            player.hurt(player.damageSources().mobAttack(zombie), 6);
            float expected = maximum - player.getHealth();
            player.setHealth(maximum); player.invulnerableTime = 0;
            check(player.hurt(player.damageSources().mobAttack(organic), 6)
                            && expected > 1 && Math.abs(player.getHealth() - (maximum - expected)) < .001F,
                    "Valid creature enemies still receive ordinary damage, including native armor calculation");
            zombie.discard();

            ProcessTransfur.setPlayerTransfurVariant(player, organic.getSelfVariant(), null, 1, false);
            LatexSocialMemory.markProvoked(organic, player);
            long expiry = provocationExpiry(organic, player);
            server.getWorldData().overworldData().setGameTime(level.getGameTime() + 100);
            for (int hit = 0; hit < 4; hit++) {
                var event = new LivingAttackEvent(player, player.damageSources().mobAttack(organic), 3);
                LatexSocialEvents.onSocialCreatureAttacksPlayer(event);
                check(!event.isCanceled(), "A legitimately provoked same-species player remains attackable");
                server.getWorldData().overworldData().setGameTime(level.getGameTime() + 10);
            }
            check(provocationExpiry(organic, player) == expiry, "NPC attacks cannot perpetually renew the player's grudge timer");
            LatexSocialMemory.markProvoked(organic, player);
            check(provocationExpiry(organic, player) > expiry, "A fresh player provocation can still extend the timer");
            server.getWorldData().overworldData().setGameTime(provocationExpiry(organic, player) + 1);
            check(!LatexSocialMemory.isProvoked(organic, player) && LatexSocialMemory.shouldRemainNeutral(organic, player),
                    "An expired grudge restores ordinary kin neutrality");

            LatexSocialMemory.markRelationshipBetrayal(organic, player);
            organic.setTarget(player); organic.setLastHurtByMob(player); organic.setLastHurtMob(player);
            HuntMemory.seeTarget(organic, player);
            check(!LatexSocialMemory.shouldRemainNeutral(organic, player), "Persistent betrayal is a real hostility reason");
            FactionHostilityGrace.begin(organic, player, 40);
            check(LatexSocialMemory.hasRelationshipBetrayal(organic, player)
                            && organic.getTarget() == null && organic.getLastHurtByMob() == null
                            && organic.getLastHurtMob() == null && !HuntMemory.targets(organic, player),
                    "Temporary release peace clears pursuit without erasing betrayal history");
            server.getWorldData().overworldData().setGameTime(level.getGameTime() + 41);
            check(!LatexSocialMemory.shouldRemainNeutral(organic, player), "Betrayal is still remembered after temporary protection ends");
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ChangedItems.ORANGE.get()));
            check(RelationshipFavorService.offerHeldFood(organic, player).isAccepted()
                            && !LatexSocialMemory.hasRelationshipBetrayal(organic, player)
                            && LatexSocialMemory.isTruced(organic, player),
                    "A valid peace offering still reconciles the relationship through the existing rules");

            CreatureCacheGuardService.begin(guard, player, player.blockPosition());
            guard.setLastHurtByMob(player); HuntMemory.seeTarget(guard, player);
            check(CreatureCacheGuardService.isDefendingAgainst(guard, player), "A real cache intrusion has an active defense order");
            LatexSocialMemory.beginTruce(guard, player, 80);
            CreatureCacheGuardService.tick(guard);
            check(!CreatureCacheGuardService.isDefendingAgainst(guard, player)
                            && guard.getTarget() == null && guard.getLastHurtByMob() == null && !HuntMemory.targets(guard, player),
                    "Accepted truce ends the old cache pursuit and cannot be immediately overridden by the guard tick");
            check(CreatureCacheGuardService.assignment(guard).isPresent(), "Peace preserves the guard's return-post assignment");
            CreatureLifeMemory.setRole(guard, CreatureLifeMemory.GroupRole.GUARD);
            var returnGoal = new CacheGuardReturnGoal(guard);
            check(returnGoal.canUse(), "A peaceful cache guard can return to its post normally");
            returnGoal.start();
            LatexSocialMemory.markProvoked(guard, other);
            guard.setLastHurtByMob(other); guard.setTarget(other); HuntMemory.seeTarget(guard, other);
            guard.setAggressive(true);
            check(guard.getTarget() == other, "A different player's fresh provocation can start a valid fight");
            CreatureCacheGuardService.tick(guard);
            returnGoal.tick();
            check(guard.getTarget() == other && guard.getLastHurtByMob() == other
                            && HuntMemory.targets(guard, other) && guard.isAggressive()
                            && !returnGoal.canUse() && !returnGoal.canContinueToUse(),
                    "A completed cache defense does not interrupt a new fight or start its return route during combat");
            players.remove(player.getUUID());
            CreatureCacheGuardService.tick(guard);
            check(guard.getTarget() == other && guard.getLastHurtByMob() == other
                            && HuntMemory.targets(guard, other) && guard.isAggressive(),
                    "The old intruder's logout does not cancel a different player's fight");
            players.put(player.getUUID(), player);
            returnGoal.stop();
            CreatureCacheGuardService.begin(guard, other, other.blockPosition());
            guard.setLastHurtByMob(other); guard.setTarget(other);
            HuntMemory.seeTarget(guard, other); guard.setAggressive(true);
            LatexSocialEvents.calmTowards(guard, player);
            check(guard.getTarget() == other && guard.getLastHurtByMob() == other
                            && HuntMemory.targets(guard, other) && guard.isAggressive()
                            && CreatureCacheGuardService.isDefendingAgainst(guard, other),
                    "Calming toward one player preserves combat and defense orders against a different player");
            LatexSocialMemory.settleAfterNegotiatedRelease(guard, other);
            check(guard.getTarget() == null && !HuntMemory.targets(guard, other)
                            && !CreatureCacheGuardService.isDefendingAgainst(guard, other),
                    "Negotiated release uses the same complete hostility cleanup");
            System.out.println("HOSTILITY_REGRESSION_PASS: " + checks + " checks");
        } finally {
            ChangedSynergyConfig.COMMON.postTransfurTruceSeconds.set(oldGrace);
            ChangedSynergyConfig.COMMON.allowSecondaryTransfur.set(oldSecondary);
            CreatureCacheGuardService.finish(guard, false);
            organic.discard(); guard.discard();
            players.remove(player.getUUID()); players.remove(other.getUUID());
            player.discard(); other.discard();
            server.getWorldData().overworldData().setGameTime(originalTime);
        }
    }

    private static long provocationExpiry(ChangedEntity mob, ServerPlayer player) {
        return mob.getPersistentData().getCompound("ChangedSynergySocial")
                .getCompound("ProvokedPlayers").getLong(player.getStringUUID());
    }

    private static ServerPlayer player(MinecraftServer server, String name) {
        var player = new ServerPlayer(server, server.overworld(), new GameProfile(UUID.randomUUID(), name));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player);
        return player;
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks++; System.out.println("HOSTILITY_PASS: " + description);
    }
}
