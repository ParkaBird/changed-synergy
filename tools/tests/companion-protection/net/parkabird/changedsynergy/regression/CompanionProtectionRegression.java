package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import net.ltxprogrammer.changed.ability.GrabEntityAbility;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.LivingEntityDataExtension;
import net.ltxprogrammer.changed.entity.ai.DarkLatexAttackCondition;
import net.ltxprogrammer.changed.entity.beast.AbstractDarkLatexEntity;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.event.HuntAIEvents;

/** Exercises real server entities and grab references; excluded from normal builds. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class CompanionProtectionRegression {
    private static int checks;

    @SubscribeEvent public static void run(ServerStartedEvent event) {
        try {
            AuthorFeedbackChecks.run(event.getServer());
            checkProtection(event.getServer());
            System.out.println("COMPANION_PROTECTION_REGRESSION_PASS: " + checks + " checks");
        } catch (Throwable failure) {
            System.err.println("COMPANION_PROTECTION_REGRESSION_FAIL");
            failure.printStackTrace();
        } finally { event.getServer().halt(false); }
    }

    private static void checkProtection(MinecraftServer server) throws Exception {
        var level = server.overworld();
        var owner = player(server, "ProtectOwner");
        var carrier = player(server, "FriendlyCarrier");
        var field = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(net.minecraft.server.players.PlayerList.class, "f_11197_");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") var players = (Map<UUID, ServerPlayer>)field.get(server.getPlayerList());
        players.put(owner.getUUID(), owner);
        players.put(carrier.getUUID(), carrier);
        level.addNewPlayer(owner); level.addNewPlayer(carrier);
        var spawn = level.getSharedSpawnPos();
        owner.setPos(spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5);
        carrier.setPos(owner.position());
        owner.tickCount = carrier.tickCount = 1000;
        owner.setOnGround(true);
        var dark = (AbstractDarkLatexEntity)creature(server, "changed:dark_latex_wolf_male", owner);
        var ordinary = creature(server, "changed:latex_hypno_cat", owner);
        var alpha = ForgeRegistries.ENTITY_TYPES.containsKey(ResourceLocation.parse("changed_addon:experiment_009"))
                ? creature(server, "changed_addon:experiment_009", owner) : null;
        try {
            LatexSocialMemory.addBond(dark, owner);
            LatexSocialMemory.addBond(ordinary, owner);
            check(BondedSuitService.ability(ordinary) != null,
                    "Ordinary adult species receive the native hold ability on joining the level");
            if (alpha != null) LatexSocialMemory.addBond(alpha, owner);
            ProcessTransfur.setPlayerTransfurVariant(carrier, ChangedTransfurVariants.LATEX_HYPNO_CAT.get(), null, 1, false);
            var abstraction = net.ltxprogrammer.changed.ability.IAbstractChangedEntity.forPlayer(carrier);
            var hold = abstraction.getAbilityInstanceSafe(net.ltxprogrammer.changed.init.ChangedAbilities.GRAB_ENTITY_ABILITY.get()).orElseThrow();
            hold.grabbedEntity = owner;
            ((LivingEntityDataExtension)owner).setGrabbedBy(carrier);
            dark.setAttackCondition(DarkLatexAttackCondition.OWNER_IS_HOSTILE);
            var rescue = new BondedOwnerGrabRescueGoal(dark);
            check(GrabEntityAbility.getGrabber(owner).getEntity() == carrier, "Real player grab reference resolves");
            check(!rescue.canUse(), "Player transport grab does not start owner defense");
            check(!BondedSuitService.canStartSuit(dark, owner), "Low-health rescue cannot replace player transport");
            hold.attackDown = true;
            check(!BondedOwnerGrabRescueGoal.isFriendlyGrab(owner, carrier, hold), "Attacking player hold is hostile");
            check(BondedOwnerDefenseGoal.hasRecentConflict(dark, owner, carrier), "Hostile grab qualifies as a conflict before damage");
            dark.setAttackCondition(DarkLatexAttackCondition.NEVER);
            check(!rescue.canUse(), "Never attack prevents rescue attacks for human owners");
            LatexSocialMemory.authorizeForcedPetDefense(dark, carrier, 600);
            check(!BondedOwnerDefenseGoal.allowsConfiguredDefense(dark, owner, carrier), "Old forced-defense state cannot bypass native Never");
            dark.setTarget(carrier);
            check(dark.getTarget() == null, "Native target guard respects Never");
            LatexSocialMemory.clearPetDefense(dark, null);
            dark.setAttackCondition(DarkLatexAttackCondition.ALWAYS);
            LatexSocialMemory.setFollowingOwner(dark, false);
            check(!rescue.canUse(), "Waiting prevents hostile grab rescue");
            LatexSocialMemory.setFollowingOwner(dark, true);
            dark.setPos(owner.getX() + 80, owner.getY(), owner.getZ());
            check(!rescue.canUse(), "Distant hostile grab does not activate rescue");
            dark.setPos(owner.position());
            check(rescue.canUse(), "Nearby hostile grab still activates permitted rescue");
            rescue.start();
            LatexSocialMemory.setFollowingOwner(dark, false);
            check(!rescue.canContinueToUse(), "Wait cancels an already-running rescue");
            rescue.stop();
            check(dark.getTarget() == null, "Cancelled rescue does not restore its target");
            hold.attackDown = false; hold.grabbedEntity = null;
            ((LivingEntityDataExtension)owner).setGrabbedBy(null);
            var friendHold = BondedSuitService.ability(ordinary);
            friendHold.grabbedEntity = owner;
            LatexSocialMemory.beginFriendlySocialHug(ordinary, owner, 100);
            ((LivingEntityDataExtension)owner).setGrabbedBy(ordinary);
            check(!rescue.canUse(), "Scripted friendly NPC hug is not a hostile grab");
            check(!BondedSuitService.canStartSuit(dark, owner), "Emergency suit preserves friendly NPC hug");
            LatexSocialMemory.endFriendlySocialHug(ordinary);
            CreaturePersonality.setFamiliarity(ordinary, owner, 0);
            check(BondedOwnerGrabRescueGoal.isFriendlyGrab(owner, ordinary, friendHold), "A new trusted friendship is sufficient for peaceful NPC carrying");
            friendHold.grabbedEntity = null;
            ((LivingEntityDataExtension)owner).setGrabbedBy(null);
            owner.setHealth(4);
            for (var pet : alpha == null ? new ChangedEntity[]{dark, ordinary} : new ChangedEntity[]{dark, ordinary, alpha}) {
                pet.setPos(owner.position());
                LatexSocialMemory.setFollowingOwner(pet, false);
                check(!new BondedEmergencySuitGoal(pet).canUse(), "Waiting blocks automatic wrapping: " + pet.getType());
                check(!BondedSuitService.suitOwner(pet, owner, BondedSuitService.SuitReason.EMERGENCY), "Commit point respects wait: " + pet.getType());
                LatexSocialMemory.setFollowingOwner(pet, true);
                pet.setPos(owner.getX() + 80, owner.getY(), owner.getZ());
                check(!BondedSuitService.needsEmergencyRescue(pet, owner), "Distant low HP is not a remote rescue signal: " + pet.getType());
                pet.getPersistentData().putUUID("ChangedSynergyTransfurRescueOwner", owner.getUUID());
                pet.getPersistentData().putLong("ChangedSynergyTransfurRescueUntil", level.getGameTime() + 240);
                ProcessTransfur.setPlayerTransfurProgress(owner, (float)ProcessTransfur.getEntityTransfurTolerance(owner) * .9F);
                check(!BondedSuitService.isTransfurRescueRequested(pet, owner), "Out-of-range queued rescue is invalidated: " + pet.getType());
                pet.setPos(owner.position());
                check(new BondedEmergencySuitGoal(pet).canUse(), "Nearby following companion still rescues: " + pet.getType());
                pet.setPos(owner.getX() + 5, owner.getY(), owner.getZ());
                check(!BondedSuitService.suitOwner(pet, owner, BondedSuitService.SuitReason.EMERGENCY), "Automatic wrap waits for physical reach: " + pet.getType());
                pet.setPos(owner.position());
                if (pet instanceof AbstractDarkLatexEntity nativePet) nativePet.setAttackCondition(DarkLatexAttackCondition.NEVER);
                else if (BondedPetSettings.usesFallbackBackend(pet)) {
                    while (BondedPetSettings.stateFor(pet)[2] != BondedPetSettings.CONDITION_NEVER)
                        BondedPetSettings.handleCommand(pet, "cycle_attack_condition");
                } else {
                    while (BondedPetSettings.stateFor(pet)[2] != BondedPetSettings.CONDITION_NEVER)
                        ChangedAddonCompat.handleBondedMenuCommand(pet, owner, "cycle_attack_condition");
                }
                LatexSocialMemory.authorizeForcedPetDefense(pet, carrier, 600);
                check(!BondedOwnerDefenseGoal.allowsConfiguredDefense(pet, owner, carrier), "Never honored across backend: " + pet.getType());
                LatexSocialMemory.clearPetDefense(pet, null);
                check(BondedSuitService.needsEmergencyRescue(pet, owner), "Never attack still allows local nonviolent shelter: " + pet.getType());
                BondedOwnerGrabRescueGoal.alertBondedCreatures(owner, carrier);
                check(pet.getTarget() == null && !LatexSocialMemory.isPetDefenseAuthorized(pet, carrier), "Damage alert cannot bypass Never: " + pet.getType());
            }
            dark.setAttackCondition(DarkLatexAttackCondition.ALWAYS);
            owner.setLastHurtByMob(carrier);
            LatexSocialMemory.setFollowingOwner(dark, false);
            BondedOwnerGrabRescueGoal.alertBondedCreatures(owner, carrier);
            check(dark.getTarget() == null, "Damage alerts respect wait orders");
            check(!new BondedOwnerDefenseGoal(dark, BondedOwnerDefenseGoal.Mode.OWNER_HURT_BY).canUse(), "Ordinary owner-defense goal respects wait");
            LatexSocialMemory.setFollowingOwner(dark, true);
            dark.setPos(owner.getX() + 80, owner.getY(), owner.getZ());
            BondedOwnerGrabRescueGoal.alertBondedCreatures(owner, carrier);
            check(dark.getTarget() == null, "Damage alerts do not mobilize distant companions");
            check(!new BondedOwnerDefenseGoal(dark, BondedOwnerDefenseGoal.Mode.OWNER_HURT_BY).canUse(), "Ordinary owner-defense goal respects distance");
            dark.setTarget(carrier);
            check(dark.getTarget() == null, "Native target assignment cannot bypass remote owner-defense scope");
            owner.setLastHurtByMob(null);
            dark.setPos(owner.position());
            LatexSocialMemory.setFollowingOwner(dark, false);
            check(BondedSuitService.suitOwner(dark, owner, BondedSuitService.SuitReason.MANUAL), "Manual wrapping remains available while waiting");
            BondedSuitService.ability(dark).releaseEntity(false);
            checkCaptureReach(server, owner, dark, alpha);
            NativeCarrierRegression.run(server, owner, carrier);
            NativeRescueRegression.run(server, carrier);
            GrabQteRegression.runServer(owner);
            ProcessTransfur.removePlayerTransfurVariant(owner);
            ProcessTransfur.removePlayerTransfurVariant(carrier);
        } finally {
            ((LivingEntityDataExtension)owner).setGrabbedBy(null);
            ProcessTransfur.removePlayerTransfurVariant(owner);
            ProcessTransfur.removePlayerTransfurVariant(carrier);
            dark.discard(); ordinary.discard(); if (alpha != null) alpha.discard();
            owner.discard(); carrier.discard();
            players.remove(owner.getUUID()); players.remove(carrier.getUUID());
        }
    }

    private static void checkCaptureReach(MinecraftServer server, ServerPlayer owner, ChangedEntity dark, ChangedEntity alpha) {
        var level = server.overworld();
        var origin = level.getSharedSpawnPos().above(20);
        Map<BlockPos, BlockState> previous = new HashMap<>();
        ChangedEntity kin = null;
        for (int x = -1; x <= 5; x++) for (int y = 0; y <= 4; y++) for (int z = -1; z <= 1; z++) {
            var pos = origin.offset(x, y, z);
            previous.put(pos, level.getBlockState(pos));
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        try {
            ProcessTransfur.removePlayerTransfurVariant(owner);
            ProcessTransfur.setPlayerTransfurProgress(owner, 0);
            owner.setHealth(owner.getMaxHealth());
            owner.setLastHurtByMob(null);
            owner.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
            BondedSuitService.ensureGrabAbility(dark);
            dark.setPos(owner.getX(), owner.getY() + 2.5, owner.getZ());
            level.setBlockAndUpdate(origin.above(2), Blocks.STONE.defaultBlockState());
            check(!TakeoverService.beginBondedConfinement(dark, owner), "Bonded confinement cannot capture through a floor within the old distance limit");
            check(!TakeoverService.active(owner) && !TakeoverService.blocksPlayerInput(owner)
                    && GrabEntityAbility.getGrabber(owner) == null && ProcessTransfur.getPlayerTransfurVariant(owner) == null,
                    "Blocked confinement leaves form, grab and input state untouched");
            level.setBlockAndUpdate(origin.above(2), Blocks.AIR.defaultBlockState());
            dark.setPos(owner.getX() + 1.5, owner.getY(), owner.getZ());
            var wall = origin.east();
            level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(wall.above(), Blocks.STONE.defaultBlockState());
            check(!TakeoverService.beginBondedConfinement(dark, owner), "Bonded confinement cannot capture through a solid wall");
            kin = creature(server, "changed:dark_latex_wolf_female", owner);
            int witnessed = witnessedKills(dark, owner);
            check(!dark.hasLineOfSight(owner) && !dark.hasLineOfSight(kin), "The solid wall hides both the killer and the victim");
            for (int i = 0; i < 8; i++) HuntAIEvents.onHunterDeath(new LivingDeathEvent(kin, owner.damageSources().playerAttack(owner)));
            check(witnessedKills(dark, owner) == witnessed
                    && !dark.getPersistentData().hasUUID("ChangedSynergyBondConfinementOwner"),
                    "Unseen kin kills do not add witnessed offences or queue confinement");
            level.setBlockAndUpdate(wall, Blocks.GLASS.defaultBlockState());
            level.setBlockAndUpdate(wall.above(), Blocks.GLASS.defaultBlockState());
            check(!TakeoverService.canPhysicallyCapture(dark, owner), "Transparent collision shapes still block bodily capture");
            level.setBlockAndUpdate(wall.above(), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            check(dark.hasLineOfSight(owner) && !TakeoverService.canPhysicallyCapture(dark, owner),
                    "Eye-level visibility over a low obstacle does not imply bodily reach");
            level.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
            HuntAIEvents.onHunterDeath(new LivingDeathEvent(kin, owner.damageSources().playerAttack(owner)));
            check(witnessedKills(dark, owner) == witnessed + 1, "A visible kin kill still records a bonded witness");
            kin.discard(); kin = null;
            dark.setPos(owner.getX() + 5, owner.getY(), owner.getZ());
            check(!TakeoverService.canPhysicallyCapture(dark, owner), "Unobstructed but distant creatures must approach before capture");
            dark.setPos(owner.getX() + 1.5, owner.getY(), owner.getZ());
            check(TakeoverService.canPhysicallyCapture(dark, owner) && TakeoverService.beginBondedConfinement(dark, owner),
                    "Unobstructed nearby bonded confinement still starts normally");
            check(TakeoverService.active(owner) && TakeoverService.blocksPlayerInput(owner), "Successful confinement owns the input lock");
            check(TakeoverService.forceReleaseByCommand(owner) && !TakeoverService.blocksPlayerInput(owner)
                    && GrabEntityAbility.getGrabber(owner) == null && !owner.noPhysics,
                    "Forced release clears the capture and input lock");
            owner.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
            dark.setPos(owner.getX() + 1.5, owner.getY(), owner.getZ());
            check(TakeoverService.beginBondedConfinement(dark, owner), "A later valid confinement remains available");
            dark.discard();
            TakeoverService.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server));
            check(!TakeoverService.active(owner) && !TakeoverService.blocksPlayerInput(owner)
                    && GrabEntityAbility.getGrabber(owner) == null && !owner.noPhysics,
                    "A removed carrier ends confinement and clears all input locks on the next server tick");
            if (alpha != null) {
                alpha.setPos(owner.getX() + 2, owner.getY(), owner.getZ());
                check(TakeoverService.canPhysicallyCapture(alpha, owner), "Large Alpha body can still reach an unobstructed nearby player");
                alpha.setPos(owner.getX(), owner.getY() + owner.getBbHeight() + .5, owner.getZ());
                check(!TakeoverService.canPhysicallyCapture(alpha, owner), "An Alpha above a separate floor cannot capture through it");
            }
        } finally {
            TakeoverService.forceReleaseByCommand(owner);
            if (kin != null) kin.discard();
            for (var entry : previous.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
        }
    }

    private static int witnessedKills(ChangedEntity creature, ServerPlayer player) {
        return creature.getPersistentData().getCompound("ChangedSynergyPersonality")
                .getCompound("PlayerMemories").getCompound(player.getStringUUID()).getInt("WitnessedKinKills");
    }

    private static ServerPlayer player(MinecraftServer server, String name) {
        var player = new ServerPlayer(server, server.overworld(), new GameProfile(UUID.randomUUID(), name));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player);
        return player;
    }

    private static ChangedEntity creature(MinecraftServer server, String id, ServerPlayer owner) {
        var mob = (ChangedEntity)ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.parse(id)).create(server.overworld());
        mob.setNoAi(true); mob.setPos(owner.position());
        server.overworld().addFreshEntity(mob);
        return mob;
    }

    private static void check(boolean value, String description) {
        if (!value) throw new AssertionError(description);
        checks++; System.out.println("PROTECTION_PASS: " + description);
    }
}
