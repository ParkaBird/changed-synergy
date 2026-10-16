package net.parkabird.changedsynergy.regression;

import java.util.Map;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.ltxprogrammer.changed.init.ChangedRegistry;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.origin.NativeOriginCatalog;

/** Actual Changed replacements and distant companion movement, never part of the release. */
final class SocialContinuityRegression {
    @SuppressWarnings("unchecked")
    static void run(MinecraftServer server) throws Exception {
        var level = server.overworld();
        check(NativeOriginCatalog.allowed(ChangedTransfurVariants.DARK_LATEX_WOLF_PARTIAL.get()),
                "Partial dark wolf is a selectable origin");
        ChangedRegistry.TRANSFUR_VARIANT.get().getValues().stream()
                .filter(v -> !NativeOriginCatalog.allowed(v))
                .forEach(v -> System.out.println("ORIGIN_EXCLUDED: " + v.getFormId()
                        + " | " + v.getEntityType().getDescription().getString()));
        boolean modded = net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.includeModdedOriginSpecies.get();
        net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.includeModdedOriginSpecies.set(true);
        var snowId = net.minecraft.resources.ResourceLocation.parse("changed_addon:form_latex_snow_leopard_partial");
        var snow = ChangedRegistry.TRANSFUR_VARIANT.get().getValue(snowId);
        if (snow != null) {
            check(NativeOriginCatalog.allowed(snow) && NativeOriginCatalog.find(level.registryAccess(), snowId) != null,
                    "Partial Snow Leopard is selectable when modded origins are enabled");
            var disabled = net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.disabledOriginForms;
            var previous = disabled.get();
            try {
                disabled.set(java.util.List.of(snowId.toString()));
                check(!NativeOriginCatalog.allowed(snow), "Partial Snow Leopard still respects the server form denylist");
            } finally { disabled.set(previous); }
        }
        ChangedRegistry.TRANSFUR_VARIANT.get().getValues().stream().filter(v -> !NativeOriginCatalog.allowed(v))
                .forEach(v -> System.out.println("ORIGIN_ALWAYS_EXCLUDED: " + v.getFormId()
                        + " | " + v.getEntityType().getDescription().getString()));
        net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.includeModdedOriginSpecies.set(modded);
        if (snow != null && !modded) check(!NativeOriginCatalog.allowed(snow),
                "Partial Snow Leopard remains hidden while modded origins are disabled");
        ServerPlayer player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "ContinuityCheck"));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player);
        var players = (Map<UUID, ServerPlayer>)ObfuscationReflectionHelper.findField(PlayerList.class, "f_11197_")
                .get(server.getPlayerList());
        players.put(player.getUUID(), player);
        var base = server.getPlayerList().getPlayers().get(0).blockPosition().offset(48, 0, 48).atY(90);
        var squid = ChangedEntities.LATEX_SQUID_DOG_MALE.get().create(level);
        var wolf = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        try {
            for (var pos : net.minecraft.core.BlockPos.betweenClosed(base.offset(-12, -1, -12), base.offset(12, -1, 12)))
                level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            player.setPos(base.getX() + .5, 90, base.getZ() + .5);
            squid.setPos(base.getX() + 340.5, 90, base.getZ() + .5);
            level.addFreshEntity(squid);
            CreaturePersonality.establishRelationship(squid, player);
            CreaturePersonality.setFamiliarity(squid, player, 60);
            CreaturePersonality.setSocialFollowing(squid, player, true);
            squid.setTarget(null);
            var follow = new SocialFollowGoal(squid);
            check(follow.canUse(), "Distant squid-dog friend can start following");
            follow.start(); follow.tick(); follow.stop();
            check(squid.distanceToSqr(player) < 144, "Distant loaded squid-dog friend safely teleports to a dry owner");
            squid.setPos(base.getX() + 340.5, 90, base.getZ() + .5);
            PlayerRelationshipSettings.recallFollowingContact(player, squid);
            check(squid.distanceToSqr(player) < 144, "Travel invitation recalls loaded but unticked contacts immediately");
            wolf.setPos(base.getX() + 3.5, 90, base.getZ() + .5);
            level.addFreshEntity(wolf);
            CreaturePersonality.establishRelationship(wolf, player);
            CreaturePersonality.setFamiliarity(wolf, player, 43);
            CreaturePersonality.setSocialFollowing(wolf, player, true);
            PlayerRelationshipSettings.rememberContact(player, wolf, false);
            var camps = PlayerOutpostData.get(server);
            level.setBlockAndUpdate(base.below(), Blocks.CAMPFIRE.defaultBlockState());
            check(camps.claim(level, player.getUUID(), base.below()).equals("claimed"), "Create a real camp for morph continuity");
            var camp = camps.byOwner(player.getUUID()).orElseThrow();
            camps.add(camp, wolf.getUUID(), wolf.getDisplayName().getString(), level.dimension().location().toString(), wolf.blockPosition(), PlayerOutpostSupplyType.of(wolf));
            camps.crewJob(camp, wolf.getUUID(), PlayerOutpostData.CrewJob.MINE);
            wolf.getPersistentData().put("ChangedSynergyPlayerOutpostCargo", new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_ORE, 7).save(new net.minecraft.nbt.CompoundTag()));
            var before = wolf.getPersistentData().getCompound("ChangedSynergyPersonality").copy();
            var abstraction = IAbstractChangedEntity.forEntity(wolf);
            abstraction.replaceVariant(ChangedTransfurVariants.DARK_LATEX_WOLF_FEMALE.get());
            var replacement = abstraction.getChangedEntity();
            check(CreaturePersonality.familiarity(replacement, player) == 43
                    && CreaturePersonality.hasEstablishedRelationship(replacement, player),
                    "Stasis/gender replacement retains friendship and exact affection");
            check(before.equals(replacement.getPersistentData().getCompound("ChangedSynergyPersonality")),
                    "Replacement preserves care budgets, cooldowns, traits and following");
            check(PlayerRelationshipSettings.findLoaded(player, wolf.getUUID()) == replacement,
                    "Old contact UUID resolves to the replacement");
            check(CreatureMorphAliasData.get(server).resolveAlias(wolf.getUUID()).equals(replacement.getUUID()),
                    "Offline players have a durable replacement alias");
            CreatureMorphAliasData.get(server).apply(player);
            check(PlayerRelationshipSettings.contacts(player).stream().noneMatch(c -> c.uuid().equals(wolf.getUUID())),
                    "Login alias repair removes the obsolete contact card");
            check(camp.members.containsKey(replacement.getUUID()) && !camp.members.containsKey(wolf.getUUID())
                            && camp.members.get(replacement.getUUID()).crewJob == PlayerOutpostData.CrewJob.MINE,
                    "Camp membership and travelling job follow the replacement body");
            check(net.minecraft.world.item.ItemStack.of(replacement.getPersistentData().getCompound("ChangedSynergyPlayerOutpostCargo")).getCount() == 7,
                    "Replacement retains real work cargo");
            camps.remove(camp, replacement.getUUID()); PlayerOutpostService.clearAssignment(replacement);
            checkCombatRecall(player, camp);
            checkFriendlyHolds(player, replacement);
            checkRescue(player, replacement);
            checkYufengAndGrowth(player);
            replacement.discard();
            System.out.println("SOCIAL_CONTINUITY_REGRESSION_PASS");
        } finally {
            squid.discard(); wolf.discard(); players.remove(player.getUUID());
        }
    }

    private static void checkCombatRecall(ServerPlayer player, PlayerOutpostData.Outpost camp) {
        var level = player.serverLevel();
        var origin = player.position();
        var mob = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        var enemy = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
        var grabber = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        level.addFreshEntity(mob); level.addFreshEntity(enemy); level.addFreshEntity(grabber);
        enemy.setPos(origin.x + 12, origin.y, origin.z);
        var camps = PlayerOutpostData.get(player.server);
        try {
            CreaturePersonality.establishRelationship(mob, player);
            CreaturePersonality.setFamiliarity(mob, player, 60);
            CreaturePersonality.setSocialFollowing(mob, player, true);
            mob.setPos(origin.x + 3, origin.y, origin.z); mob.setTarget(enemy);
            combatRecallTick(mob);
            check(mob.getTarget() == enemy && mob.distanceToSqr(player) < 16,
                    "Nearby combat is not interrupted by follower recall");
            mob.setPos(origin.x + 40, origin.y, origin.z); mob.setTarget(enemy);
            combatRecallTick(mob);
            check(mob.distanceToSqr(player) < 144 && mob.getTarget() == null,
                    "A fighting friend regroups even though its follow goal cannot run");
            mob.setPos(origin.x + 40, origin.y, origin.z); mob.setTarget(enemy);
            var ability = BondedSuitService.ability(mob);
            ability.grabbedEntity = enemy;
            combatRecallTick(mob);
            check(mob.distanceToSqr(player) > 28 * 28, "Recall does not interrupt an active grab");
            ability.grabbedEntity = null;
            BondedSuitService.ability(grabber).grabbedEntity = mob;
            ((net.ltxprogrammer.changed.entity.LivingEntityDataExtension)mob).setGrabbedBy(grabber);
            combatRecallTick(mob);
            check(mob.distanceToSqr(player) > 28 * 28, "A restrained companion is not teleported");
            ((net.ltxprogrammer.changed.entity.LivingEntityDataExtension)mob).setGrabbedBy(null);
            BondedSuitService.ability(grabber).grabbedEntity = null;
            mob.setLeashedTo(player, false);
            combatRecallTick(mob);
            check(mob.distanceToSqr(player) > 28 * 28, "Recall respects a leash");
            mob.dropLeash(false, false);
            CreaturePersonality.setSocialFollowing(mob, player, false);
            combatRecallTick(mob);
            check(mob.distanceToSqr(player) > 28 * 28, "Waiting friends are not recalled from combat");
            CreaturePersonality.setSocialFollowing(mob, player, true);
            mob.setTarget(enemy);
            player.setPos(origin.x, level.getMaxBuildHeight() - 10, origin.z);
            check(BondedTeleportSafety.findSafeLanding(level, mob, player).isEmpty(),
                    "Flying-owner fixture has no safe landing");
            combatRecallTick(mob);
            check(mob.getTarget() == enemy && mob.getX() == origin.x + 40,
                    "Failed safe placement retains the fight and position");
            player.setPos(origin);
            PlayerRelationshipSettings.recallFollowingContact(player, mob);
            check(mob.distanceToSqr(player) < 144 && mob.getTarget() == null,
                    "Explicit travel invitations can also recall fighting loaded contacts");
            LatexSocialMemory.addBond(mob, player);
            LatexSocialMemory.setFollowingOwner(mob, true);
            mob.setLastHurtByMob(enemy);
            mob.setPos(origin.x + 26, origin.y, origin.z); mob.setTarget(enemy);
            check(mob.getTarget() == enemy, "Bonded recall fixture represents permitted self-defense");
            combatRecallTick(mob);
            check(mob.distanceToSqr(player) < 144 && mob.getTarget() == null,
                    "Bonded pets retain their shorter combat-recall distance");
            LatexSocialMemory.setFollowingOwner(mob, false);
            mob.setLastHurtByMob(enemy);
            mob.setPos(origin.x + 40, origin.y, origin.z); mob.setTarget(enemy);
            combatRecallTick(mob);
            check(mob.distanceToSqr(player) > 24 * 24, "Waiting bonded pets stay at their post");
            camps.add(camp, mob.getUUID(), mob.getDisplayName().getString(),
                    level.dimension().location().toString(), mob.blockPosition(), PlayerOutpostSupplyType.of(mob));
            camps.role(camp, mob.getUUID(), PlayerOutpostData.Role.CREW, mob.blockPosition());
            PlayerOutpostService.applyRole(mob, player, PlayerOutpostData.Role.CREW);
            mob.setLastHurtByMob(enemy);
            mob.setTarget(enemy);
            combatRecallTick(mob);
            check(mob.distanceToSqr(player) < 144 && mob.getTarget() == null,
                    "Travelling camp members are not stranded by combat");
            camps.role(camp, mob.getUUID(), PlayerOutpostData.Role.GUARD, mob.blockPosition());
            PlayerOutpostService.applyRole(mob, player, PlayerOutpostData.Role.GUARD);
            mob.setLastHurtByMob(enemy);
            mob.setPos(origin.x + 40, origin.y, origin.z); mob.setTarget(enemy);
            combatRecallTick(mob);
            check(mob.distanceToSqr(player) > 24 * 24, "Camp guards keep their stationary assignment");
            CreaturePersonality.establishRelationship(grabber, player);
            CreaturePersonality.setFamiliarity(grabber, player, 60);
            camps.add(camp, grabber.getUUID(), grabber.getDisplayName().getString(),
                    level.dimension().location().toString(), grabber.blockPosition(), PlayerOutpostSupplyType.of(grabber));
            camps.role(camp, grabber.getUUID(), PlayerOutpostData.Role.CREW, grabber.blockPosition());
            PlayerOutpostService.applyRole(grabber, player, PlayerOutpostData.Role.CREW);
            grabber.setPos(origin.x + 26, origin.y, origin.z); grabber.setTarget(enemy);
            combatRecallTick(grabber);
            check(grabber.distanceToSqr(player) < 144 && grabber.getTarget() == null,
                    "An unbonded camp crew member uses the crew's normal teleport distance");
            System.out.println("COMPANION_COMBAT_RECALL_PASS");
        } finally {
            player.setPos(origin);
            camps.remove(camp, mob.getUUID());
            camps.remove(camp, grabber.getUUID());
            PlayerOutpostService.clearAssignment(mob);
            PlayerOutpostService.clearAssignment(grabber);
            mob.discard(); enemy.discard(); grabber.discard();
        }
    }

    private static void combatRecallTick(ChangedEntity mob) {
        mob.tickCount = 60 + Math.floorMod(mob.getId(), 60);
        net.parkabird.changedsynergy.event.LatexSocialEvents.onLivingTick(
                new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(mob));
    }

    private static void checkFriendlyHolds(ServerPlayer player, ChangedEntity mob) throws Exception {
        var ability = BondedSuitService.ability(mob);
        var ext = (net.ltxprogrammer.changed.entity.LivingEntityDataExtension)player;
        float health = mob.getHealth();
        for (boolean social : new boolean[]{true, false}) {
            if (social) LatexSocialMemory.beginFriendlySocialHug(mob, player, 100);
            else LatexSocialMemory.beginOrganicEvacuation(mob, player, 100);
            ability.grabbedEntity = player; ability.grabbedHasControl = false; ability.suited = false;
            ext.setGrabbedBy(mob);
            ability.grabStrength = .5F;
            if (net.parkabird.changedsynergy.compat.ChangedAddonCompat.isLoaded()) {
                Class.forName("net.parkabird.changedsynergy.compat.addon.ChangedAddonSocialEvents")
                        .getMethod("onLivingTick", net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent.class)
                        .invoke(null, new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(mob));
                check(ability.grabStrength == .5F, "Friendly hold maintenance preserves accumulated escape progress");
            }
            ability.grabStrength = .00001F; ability.ticksGrabbed = 20;
            ability.initializeEscape(410L);
            ability.ticksUnpressed = 20;
            ability.escapeKeys.reset(false);
            ability.escapeKeys.queueKeyState(ability.currentEscapeKey, true);
            ability.tickIdle();
            check(ability.grabbedEntity == null && ext.getGrabbedBy() == null && !player.noPhysics,
                    "Friendly " + (social ? "social" : "transport") + " hold accepts native WASD escape");
            check(mob.getHealth() == health && !GrabEscapeStunService.isStunned(mob)
                            && !player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),
                    "Friendly escape causes no damage, stun or victim slowdown");
            LatexSocialMemory.endOrganicEvacuation(mob);
        }
        LatexSocialMemory.beginFriendlySocialHug(mob, player, 1);
        ability.grabbedEntity = player; ability.grabbedHasControl = false; ext.setGrabbedBy(mob);
        mob.getPersistentData().getCompound("ChangedSynergySocial").putLong("FriendlyHugUntil", 0);
        ability.tickIdle();
        check(ability.grabbedEntity == null && !LatexSocialMemory.hasFriendlySocialHug(mob),
                "Friendly hug deadline releases and clears its marker without Addon AI");
        LatexSocialMemory.beginFriendlySocialHug(mob, player, 100);
        ext.setGrabbedBy(mob); player.noPhysics = true;
        BondedSuitService.tickFriendlySuit(player);
        check(ext.getGrabbedBy() == null && !player.noPhysics && !LatexSocialMemory.hasFriendlySocialHug(mob),
                "Lost friendly ability reference clears the player movement lock");
    }

    private static void checkRescue(ServerPlayer owner, ChangedEntity pet) {
        var attacker = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(owner.level());
        attacker.setPos(owner.position()); owner.serverLevel().addFreshEntity(attacker);
        LatexSocialMemory.addBond(pet, owner); LatexSocialMemory.setFollowingOwner(pet, true);
        pet.setPos(owner.getX() + 1, owner.getY(), owner.getZ());
        owner.setHealth(4);
        try {
            var goal = new BondedEmergencySuitGoal(pet);
            check(goal.canUse(), "Nearby low-health owner starts emergency shelter");
            goal.start(); goal.tick();
            check(BondedSuitService.isSuitingOwner(pet, owner), "Emergency shelter commits on the first contact tick");
            goal.stop();
            owner.setOnGround(true);
            check(BondedSuitService.releaseOwner(pet, owner, BondedSuitService.ReleaseReason.MANUAL), "Grounded owner can release emergency shelter");
            check(!new BondedEmergencySuitGoal(pet).canUse(), "Manual release still prevents immediate rescue wrapping again");
            owner.setHealth(owner.getMaxHealth());
            owner.getPersistentData().putLong("ChangedSynergyRescueGraceUntil", 0);
            check(!BondedSuitService.isRescueGraceActive(owner), "Recovery clears emergency suppression after the grace period");
            pet.getPersistentData().getCompound("ChangedSynergySocial").remove("EmergencyRescueCooldown");
            float tolerance = (float)net.ltxprogrammer.changed.process.ProcessTransfur.getEntityTransfurTolerance(owner);
            net.ltxprogrammer.changed.process.ProcessTransfur.setPlayerTransfurProgress(owner, tolerance * .8F);
            BondedSuitService.protectOrRequestTransfurRescue(owner, tolerance * .01F);
            check(BondedSuitService.isTransfurRescueRequested(pet, owner), "High transfur progress alerts an available bond");
            goal = new BondedEmergencySuitGoal(pet);
            check(goal.canUse(), "Requested transfur rescue starts without an unrelated delay");
            goal.start(); goal.tick();
            check(BondedSuitService.isSuitingOwner(pet, owner), "Transfur rescue wraps on actual bodily contact");
            goal.stop(); owner.setOnGround(true);
            check(BondedSuitService.releaseOwner(pet, owner, BondedSuitService.ReleaseReason.MANUAL), "Transfur rescue releases its temporary suit safely");
            net.ltxprogrammer.changed.process.ProcessTransfur.setPlayerTransfurProgress(owner, 0);
            var grab = BondedSuitService.ability(attacker);
            grab.grabbedEntity = owner; grab.attackDown = true;
            ((net.ltxprogrammer.changed.entity.LivingEntityDataExtension)owner).setGrabbedBy(attacker);
            var dark = (net.ltxprogrammer.changed.entity.beast.AbstractDarkLatexEntity)pet;
            dark.setAttackCondition(net.ltxprogrammer.changed.entity.ai.DarkLatexAttackCondition.ALWAYS);
            dark.setTargetType(net.ltxprogrammer.changed.entity.ai.DarkLatexTargetType.TRANSFURABLE_ENTITIES);
            var rescue = new BondedOwnerGrabRescueGoal(pet);
            check(rescue.canUse(), "A hostile same-faction grab does not block permitted owner rescue");
            dark.setAttackCondition(net.ltxprogrammer.changed.entity.ai.DarkLatexAttackCondition.NEVER);
            check(!rescue.canUse(), "Never attack still prevents rescue attacks");
            dark.setAttackCondition(net.ltxprogrammer.changed.entity.ai.DarkLatexAttackCondition.ALWAYS);
            LatexSocialMemory.setFollowingOwner(pet, false);
            check(!rescue.canUse(), "Waiting still prevents rescue attacks");
            LatexSocialMemory.setFollowingOwner(pet, true);
            check(rescue.canUse(), "Permitted following companion reacquires the actual hostile grab");
            rescue.start(); rescue.tick(); rescue.stop();
            check(grab.grabbedEntity == null && net.ltxprogrammer.changed.ability.GrabEntityAbility.getGrabber(owner) == null,
                    "Same-faction rescue actually releases the owner");
        } finally {
            LatexSocialMemory.unregisterBond(pet, owner); attacker.discard();
            owner.setHealth(owner.getMaxHealth());
        }
    }

    private static void checkYufengAndGrowth(ServerPlayer player) {
        var yufeng = ChangedEntities.DARK_LATEX_YUFENG.get().create(player.level());
        yufeng.setPos(player.getX() + 3, player.getY(), player.getZ()); player.serverLevel().addFreshEntity(yufeng);
        CreaturePersonality.establishRelationship(yufeng, player); CreaturePersonality.setFamiliarity(yufeng, player, 47);
        var abstraction = IAbstractChangedEntity.forEntity(yufeng);
        abstraction.replaceVariant(ChangedTransfurVariants.DARK_LATEX_DOUBLE_YUFENG.get());
        var fused = abstraction.getChangedEntity();
        CreatureMorphContinuity.transfer(yufeng, fused); // The post-fusion event sees the same replacement again.
        check(CreaturePersonality.familiarity(fused, player) == 47
                        && CreaturePersonality.hasEstablishedRelationship(fused, player),
                "Double Yufeng replacement and repeated fusion notification preserve the individual");
        fused.discard();
        var pup = ChangedEntities.DARK_LATEX_WOLF_PUP.get().create(player.level());
        pup.setPos(player.getX() + 3, player.getY(), player.getZ()); player.serverLevel().addFreshEntity(pup);
        pup.tame(player);
        var growth = IAbstractChangedEntity.forEntity(pup);
        growth.replaceVariant(ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get());
        var adult = growth.getChangedEntity();
        check(LatexSocialMemory.isPetOwner(adult, player) && CreaturePersonality.hasEstablishedRelationship(adult, player),
                "Common replacement hook preserves native pup ownership after growth");
        adult.discard();
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        System.out.println("SOCIAL_CONTINUITY_CHECK: " + message);
    }
}
