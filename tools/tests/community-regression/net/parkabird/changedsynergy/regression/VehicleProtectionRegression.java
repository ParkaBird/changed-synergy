package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.compat.FirearmCompat;
import net.parkabird.changedsynergy.event.HuntAIEvents;
import net.parkabird.changedsynergy.event.NpcDispositionEvents;

/** Native grab authority and actual pursuit goal, including hidden passengers and normal boats. */
final class VehicleProtectionRegression {
    @SuppressWarnings("unchecked")
    static void run(MinecraftServer server) throws Exception {
        var level = server.overworld();
        var player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "VehicleTest")) {
            @Override public double getEyeY() { return isPassenger() ? getY() : super.getEyeY(); }
        };
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player);
        player.setGameMode(GameType.SURVIVAL);
        var mapField = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(net.minecraft.server.players.PlayerList.class, "f_11197_"); mapField.setAccessible(true);
        var players = (Map<UUID, ServerPlayer>)mapField.get(server.getPlayerList()); players.put(player.getUUID(), player);
        var carrier = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        var vehicle = new com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity(level);
        var boat = new Boat(level, 0, 0, 0);
        var anchor = level.getSharedSpawnPos().above(40);
        var wall = anchor.offset(2, 0, 0); var oldWall = level.getBlockState(wall);
        Difficulty difficulty = level.getDifficulty();
        boolean spawning = level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
        boolean enabled = ChangedSynergyConfig.COMMON.factionPursuit.get();
        double grabChance = ChangedSynergyConfig.COMMON.hostileGrabAttemptChance.get();
        try {
            server.setDifficulty(Difficulty.NORMAL, true);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true, server);
            ChangedSynergyConfig.COMMON.factionPursuit.set(true);
            ChangedSynergyConfig.COMMON.hostileGrabAttemptChance.set(1.0D);
            for (var actor : new net.minecraft.world.entity.Entity[]{carrier, vehicle, boat, player}) {
                actor.setPos(anchor.getX() + 5.5, anchor.getY(), anchor.getZ() + 0.5);
                if (actor != player) level.addFreshEntity(actor);
            }
            carrier.setPos(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
            carrier.lookAt(EntityAnchorArgument.Anchor.EYES, vehicle.getEyePosition());
            FactionReputation.set(carrier, player, -200);
            var available = FactionPursuitService.class.getDeclaredMethod("available", ServerPlayer.class); available.setAccessible(true);
            check((boolean)available.invoke(null, player), "Pursuit allowed on foot");
            check(player.startRiding(boat, true), "Mount ordinary boat");
            check(FirearmCompat.ridingSuperbVehicle(player) == null && !(boolean)available.invoke(null, player),
                    "Ordinary boat keeps its existing behavior");
            player.stopRiding(); check(player.startRiding(vehicle, true), "Mount recognized optional vehicle");
            check(FirearmCompat.ridingSuperbVehicle(player) == vehicle && (boolean)available.invoke(null, player),
                    "Mounted player remains eligible for pursuit squads");
            check(!LatexSocialMemory.mayInitiateHostileGrab(carrier, player), "NPC will not repeatedly attempt a protected passenger grab");
            BondedSuitService.ensureGrabAbility(carrier);
            var ability = BondedSuitService.ability(carrier);
            check(!ability.grabEntity(player) && ability.grabbedEntity == null && player.getVehicle() == vehicle,
                    "Native ability cannot steal a passenger from the vehicle");
            check(!TakeoverService.canPhysicallyCapture(carrier, player), "Direct takeover cannot bypass passenger protection");

            level.setBlock(wall, Blocks.STONE.defaultBlockState(), 2);
            player.setPos(vehicle.getX(), vehicle.getY(), vehicle.getZ());
            check(!carrier.hasLineOfSight(player), "Hidden/scaled passenger's own eye is occluded");
            check(NpcDispositionEvents.hasVisualContact(carrier, player) && NpcDispositionEvents.canTarget(carrier, player),
                    "Visible vehicle still supports visual acquisition of its hostile passenger");
            var squad = new CompoundTag(); squad.putUUID("Target", player.getUUID());
            squad.putLong("Until", level.getGameTime() + 1000L); carrier.getPersistentData().put("SynergyPursuitSquad", squad);
            var goalType = Class.forName(FactionPursuitService.class.getName() + "$PursuitGoal");
            var goalConstructor = goalType.getDeclaredConstructor(ChangedEntity.class); goalConstructor.setAccessible(true);
            var goal = (net.minecraft.world.entity.ai.goal.Goal)goalConstructor.newInstance(carrier);
            check(goal.canUse() && goal.canContinueToUse() && !carrier.isRemoved(), "Boarding does not retire an existing pursuit squad");
            goal.tick();
            check(carrier.getTarget() == player && HuntMemory.targets(carrier, player), "Squad retains the real player as its chase target");
            var pursuit = HuntAIEvents.class.getDeclaredMethod("tickPlayerPursuit", ChangedEntity.class, ServerPlayer.class); pursuit.setAccessible(true);
            HuntMemory.setLostSightTicks(carrier, 1000);
            pursuit.invoke(null, carrier, player);
            check(carrier.getTarget() == player && HuntMemory.getLostSightTicks(carrier) == 0,
                    "A visible vehicle does not count as lost player sight");
            carrier.getPersistentData().remove("SynergyPursuitSquad");
            carrier.setTarget(null); HuntMemory.clear(carrier);
            level.setBlock(wall, oldWall, 2);
            player.stopRiding();
            carrier.setPos(player.getX() + 0.5, player.getY(), player.getZ());
            check(LatexSocialMemory.mayInitiateHostileGrab(carrier, player) && ability.grabEntity(player),
                    "Normal grabbing returns immediately after dismounting");
            ability.releaseEntity(false);
            LatexSocialMemory.addBond(carrier, player);
            check(player.startRiding(vehicle, true) && !BondedSuitService.canStartSuit(carrier, player),
                    "Friendly rescue wrapping also respects vehicle protection");
            player.stopRiding();
            check(BondedSuitService.canStartSuit(carrier, player), "Vehicle protection leaves ordinary friendly wrapping intact");
            System.out.println("VEHICLE_PROTECTION_REGRESSION_PASS");
        } finally {
            player.stopRiding();
            level.setBlock(wall, oldWall, 2);
            server.setDifficulty(difficulty, true);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(spawning, server);
            ChangedSynergyConfig.COMMON.factionPursuit.set(enabled);
            ChangedSynergyConfig.COMMON.hostileGrabAttemptChance.set(grabChance);
            LatexSocialMemory.unregisterBond(carrier, player);
            carrier.discard(); vehicle.discard(); boat.discard(); player.discard();
            players.remove(player.getUUID());
        }
    }
    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
        System.out.println("VEHICLE_PROTECTION_PASS: " + message);
    }
}
