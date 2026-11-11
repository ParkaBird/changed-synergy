package net.parkabird.changedsynergy.regression;

import net.parkabird.changedsynergy.ai.CreatureGrabService;

import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.*;

@Mod.EventBusSubscriber(modid = "changed_synergy")
public final class PursuitTakeoverRegression {
    private static int checks;
    @SuppressWarnings("unchecked")
    @SubscribeEvent public static void start(ServerStartedEvent event) {
        var server = event.getServer();
        if (!server.isDedicatedServer()) return;
        ServerPlayer player = null;
        ChangedEntity carrier = null, other = null;
        try {
            var level = server.overworld();
            server.setDifficulty(Difficulty.NORMAL, true);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true, server);
            var config = ChangedSynergyConfig.COMMON;
            config.factionPursuit.set(true); config.takeoverEnabled.set(true);
            config.takeoverPunitive.set(true); config.takeoverHostileFinale.set(true);
            config.creatureTransfurMethod.set(ChangedSynergyConfig.CreatureTransfurMethod.TAKEOVER);
            player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "PursuitRelease"));
            player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(8, 100, 8); player.setOnGround(true);
            var map = (Map<UUID, ServerPlayer>)net.minecraftforge.fml.util.ObfuscationReflectionHelper.getPrivateValue(
                    net.minecraft.server.players.PlayerList.class, server.getPlayerList(), "f_11197_");
            map.put(player.getUUID(), player);
            carrier = ChangedEntities.WHITE_LATEX_WOLF_MALE.get().create(level);
            other = ChangedEntities.WHITE_LATEX_WOLF_MALE.get().create(level);
            for (var mob : java.util.List.of(carrier, other)) {
                mob.moveTo(8.5, 100, 8); mob.setNoAi(true); level.addFreshEntity(mob);
                mob.getPersistentData().putString("ChangedSynergyLightReputationGroup", "cave");
                var squad = new CompoundTag(); squad.putUUID("Target", player.getUUID());
                squad.putLong("Until", level.getGameTime() + 3600);
                mob.getPersistentData().put("SynergyPursuitSquad", squad);
                FactionReputation.set(mob, player, -100);
            }
            check(TakeoverService.authorizeOrdinary(carrier, player, InvoluntaryTransfurNegotiation.Reason.FACTION_RETALIATION), "punitive capture authorized");
            check(TakeoverService.beginOrdinary(carrier, player), "actual punitive takeover starts");
            check(FactionHostilityGrace.active(carrier, player), "capture begins the squad stand-down grace");
            var type = Class.forName(FactionPursuitService.class.getName() + "$PursuitGoal");
            var constructor = type.getDeclaredConstructor(ChangedEntity.class); constructor.setAccessible(true);
            var goal = (Goal)constructor.newInstance(carrier);
            System.out.println("PURSUIT_CAPTURE_BUSY: addon=" + net.minecraftforge.fml.ModList.get().isLoaded("changed_addon")
                    + " busy=" + CreatureGrabService.isGrabberBusy(carrier));
            check(!goal.canUse(), "capturing creature stops chasing");
            check(carrier.isAlive() && !carrier.isRemoved(), "stand-down must retain the active carrier");
            check(CreatureGrabService.isGrabberBusy(carrier), "native grab is busy with or without Addon");
            var otherGoal = (Goal)constructor.newInstance(other);
            check(!otherGoal.canUse() && other.isRemoved(), "unoccupied squad members can still withdraw");
            for (int i = 0; i < 5; i++) {
                goal.canContinueToUse();
                TakeoverService.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server));
                check(TakeoverService.active(player) && TakeoverService.contains(carrier, player)
                        && carrier.isAlive() && !carrier.isRemoved(), "maintenance retains the actual takeover");
            }
            check(TakeoverService.forceReleaseByCommand(player), "explicit release remains functional");
            check(!CreatureGrabService.isGrabberBusy(carrier), "released creature is no longer busy");
            goal.canUse();
            check(carrier.isRemoved(), "released pursuit carrier can retire normally");
            System.out.println("PURSUIT_TAKEOVER_PASS: " + checks + " real cave-faction capture, maintenance, squad retirement and release checks");
        } catch (Throwable failure) {
            System.err.println("PURSUIT_TAKEOVER_FAIL"); failure.printStackTrace();
        } finally {
            if (player != null && TakeoverService.active(player)) TakeoverService.forceReleaseByCommand(player);
            if (carrier != null) carrier.discard();
            if (other != null) other.discard();
            server.halt(false);
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
}
