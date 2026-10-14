package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.parkabird.changedsynergy.ai.HypnosisQteService;
import net.parkabird.changedsynergy.ai.HypnosisProfile;
import net.parkabird.changedsynergy.init.ChangedSynergyMobEffects;
import net.parkabird.changedsynergy.network.HypnosisQteSyncPacket;
import net.parkabird.changedsynergy.network.HypnosisTapPacket;

/** Actual two-player session logic, input validation and cleanup on a dedicated server. */
public final class HypnosisPlayerRegression {
    private static int checks;
    public static void run(MinecraftServer server) throws Exception {
        var level = server.overworld();
        var source = player(server, "HypnotistTest");
        var target = player(server, "HumanHypnosisTest");
        var outsider = player(server, "UnrelatedTest");
        var attacker = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
        long oldTime = level.getGameTime();
        boolean controls = Changed.config.server.playerControllingAbilities.get();
        var playerField = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(net.minecraft.server.players.PlayerList.class, "f_11197_");
        playerField.setAccessible(true);
        @SuppressWarnings("unchecked") var playerMap = (Map<UUID, ServerPlayer>) playerField.get(server.getPlayerList());
        try {
            for (var player : new ServerPlayer[]{source, target, outsider}) {
                playerMap.put(player.getUUID(), player);
                level.addNewPlayer(player);
            }
            source.setPos(0.5, 100, 0.5); target.setPos(0.5, 100, 5.5); outsider.setPos(0.5, 100, 8.5);
            Changed.config.server.playerControllingAbilities.set(true);
            ProcessTransfur.setPlayerTransfurVariant(source, ChangedTransfurVariants.LATEX_HYPNO_CAT.get(), null, 1, false);
            face(source, target); face(target, source);
            check(HypnosisProfile.of(source) == HypnosisProfile.HYPNO_CAT, "Player profile uses actual creature species");
            check(HypnosisQteService.handlePlayerAbilityTick(IAbstractChangedEntity.forPlayer(source)), "Native player ability enters shared contest");
            Object session = session(target);
            check(session != null && (boolean) field(session, "playerDuel"), "One two-player session exists");
            attacker.setPos(1.5, 100, 0.5); level.addFreshEntity(attacker); attacker.setTarget(source);
            net.ltxprogrammer.changed.init.ChangedAbilities.HYPNOSIS.get().tick(IAbstractChangedEntity.forPlayer(source));
            check(attacker.getTarget() == null, "Player hypnosis retains native nearby-monster calming");
            check(!target.hasEffect(net.minecraft.world.effect.MobEffects.CONFUSION)
                            && !target.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),
                    "Native ability cannot overwrite the shared player contest with instant effects");
            check(HypnosisQteService.isPlayerControlLocked(target) && !HypnosisQteService.isPlayerControlLocked(source),
                    "Only human's actions are restrained");
            int id = (int) field(session, "id");
            HypnosisQteService.playerTap(outsider, id, true);
            HypnosisQteService.playerTap(target, id + 1, true);
            check(progress(session) == 0.5F, "Outsider and wrong-session packets rejected");
            HypnosisQteService.playerTap(source, id, true);
            float increased = progress(session);
            check(increased > 0.5F, "Caster tap raises shared control");
            HypnosisQteService.playerTap(source, id, true);
            HypnosisQteService.playerTap(source, id, false);
            HypnosisQteService.playerTap(source, id, true);
            check(progress(session) == increased, "Held key and same-tick spam cannot add control");
            HypnosisQteService.playerTap(target, id, true);
            check(Math.abs(progress(session) - 0.5F) < 0.00001F, "Human tap reduces the same control value");
            check(!HypnosisQteService.reactToPlayerPat(source, outsider), "An unrelated player's pat cannot stun this contest");
            check(HypnosisQteService.reactToPlayerPat(source, target), "Human's close-range pat briefly distracts the caster");
            check(!HypnosisQteService.reactToPlayerPat(source, target), "Continuous patting cannot refresh distraction endlessly");
            ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(level.getGameTime() + 3);
            HypnosisQteService.playerTap(source, id, false);
            float beforeStunnedTap = progress(session);
            HypnosisQteService.playerTap(source, id, true);
            check(progress(session) == beforeStunnedTap, "Distracted caster cannot raise control");
            for (int tap = 0; tap < 28; tap++) {
                ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(level.getGameTime() + 3);
                HypnosisQteService.handlePlayerAbilityTick(IAbstractChangedEntity.forPlayer(source));
                HypnosisQteService.playerTap(target, id, false);
                HypnosisQteService.playerTap(target, id, true);
            }
            HypnosisQteService.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, target));
            check(session(target) == null && !HypnosisQteService.isPlayerControlLocked(target)
                    && !target.hasEffect(ChangedSynergyMobEffects.MESMERIZED.get()), "Human wins at zero and is released");

            ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(level.getGameTime() + HypnosisQteService.HYPNOSIS_COOLDOWN_TICKS + 1);
            face(source, target); face(target, source);
            HypnosisQteService.handlePlayerAbilityTick(IAbstractChangedEntity.forPlayer(source));
            session = session(target); id = (int) field(session, "id");
            for (int tap = 0; tap < 28; tap++) {
                ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(level.getGameTime() + 3);
                HypnosisQteService.handlePlayerAbilityTick(IAbstractChangedEntity.forPlayer(source));
                HypnosisQteService.playerTap(source, id, false);
                HypnosisQteService.playerTap(source, id, true);
            }
            HypnosisQteService.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, target));
            check(session(target) == null && target.hasEffect(ChangedSynergyMobEffects.MESMERIZED.get())
                    && !source.hasEffect(ChangedSynergyMobEffects.MESMERIZED.get()), "Full control briefly mesmerizes human, never caster");
            target.removeEffect(ChangedSynergyMobEffects.MESMERIZED.get());
            ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(level.getGameTime() + HypnosisQteService.HYPNOSIS_COOLDOWN_TICKS + 1);
            face(source, target); face(target, source);
            HypnosisQteService.handlePlayerAbilityTick(IAbstractChangedEntity.forPlayer(source));
            check(session(target) != null, "A later attempt may start after cooldown");
            HypnosisQteService.onLogout(new PlayerEvent.PlayerLoggedOutEvent(source));
            check(session(target) == null && !HypnosisQteService.isPlayerControlLocked(target), "Caster disconnect releases human session");

            var buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            var sync = new HypnosisQteSyncPacket(7, source.getId(), 0, 0.46F, 1, 100, 0, true, true, target.getId(), 60);
            HypnosisQteSyncPacket.encode(sync, buffer);
            check(HypnosisQteSyncPacket.decode(buffer).equals(sync), "Shared progress, role and target survive packet round trip");
            buffer.clear(); var tap = new HypnosisTapPacket(7, false); HypnosisTapPacket.encode(tap, buffer);
            check(HypnosisTapPacket.decode(buffer).equals(tap), "Input edge packet round trip"); buffer.release();
            System.out.println("PLAYER_HYPNOSIS_REGRESSION_PASS: " + checks + " checks");
        } finally {
            for (var player : new ServerPlayer[]{source, target, outsider}) {
                HypnosisQteService.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
                ProcessTransfur.removePlayerTransfurVariant(player);
                playerMap.remove(player.getUUID()); player.discard();
            }
            Changed.config.server.playerControllingAbilities.set(controls);
            attacker.discard();
            ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(oldTime);
        }
    }
    private static ServerPlayer player(MinecraftServer server, String name) {
        var player = new ServerPlayer(server, server.overworld(), new GameProfile(UUID.randomUUID(), name));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player);
        return player;
    }
    private static void face(ServerPlayer first, ServerPlayer second) {
        first.lookAt(EntityAnchorArgument.Anchor.EYES, second.getEyePosition());
    }
    private static Object session(ServerPlayer player) throws Exception {
        var field = HypnosisQteService.class.getDeclaredField("SESSIONS"); field.setAccessible(true);
        return ((Map<?, ?>) field.get(null)).get(player.getUUID());
    }
    private static Object field(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static float progress(Object session) throws Exception { return (float) field(session, "resistance"); }
    private static void check(boolean value, String name) {
        if (!value) throw new AssertionError(name); checks++; System.out.println("HYPNOSIS_PLAYER_PASS: " + name);
    }
}
