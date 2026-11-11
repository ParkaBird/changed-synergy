package net.parkabird.changedsynergy.ai;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.event.LatexSocialEvents;
import net.parkabird.changedsynergy.network.ChasePlayInputPacket;
import net.parkabird.changedsynergy.network.ChasePlayStatePacket;
import static net.parkabird.changedsynergy.network.ChasePlayStatePacket.*;

/** Production Forge entities, native grab mixins and real persistence; no release test hooks. */
public final class PeerPlayRegression {
    private static final Class<?> SERVICE = CreaturePeerInteractionService.class;
    private static int checks;
    private static final List<ChangedEntity> CREATURES = new ArrayList<>();
    private static double X, Z;
    private static int hugSounds;
    private static final double Y = 181;

    @SuppressWarnings("unchecked")
    public static void run(MinecraftServer server) throws Exception {
        ServerLevel level = server.overworld();
        X=level.getSharedSpawnPos().getX()+0.5;Z=level.getSharedSpawnPos().getZ()+0.5;
        var config = ChangedSynergyConfig.COMMON;
        config.ambientPeerInteractions.set(true);
        config.chasePlayGames.set(true);
        config.chasePlayShortHugs.set(true);
        var players = (Map<UUID, ServerPlayer>)ObfuscationReflectionHelper.findField(
                net.minecraft.server.players.PlayerList.class, "f_11197_").get(server.getPlayerList());
        var player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "PeerPlayer"));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {
                if (packet instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket sound
                        && sound.getSound().value()==net.ltxprogrammer.changed.init.ChangedSounds.LATEX_GRAB_ENTITY.get()) hugSounds++;
                super.send(packet);
            }
        };
        ObfuscationReflectionHelper.setPrivateValue(ServerPlayer.class, player, 0, "f_8921_");
        player.setPos(X + 1, Y, Z);
        player.setOnGround(true);
        level.getChunkAt(player.blockPosition());
        players.put(player.getUUID(), player);
        level.addNewPlayer(player);
        var online = (List<ServerPlayer>)ObfuscationReflectionHelper.findField(
                net.minecraft.server.players.PlayerList.class, "f_11196_").get(server.getPlayerList());
        online.add(player);
        for (int dx = -26; dx <= 26; dx++) for (int dz = -26; dz <= 26; dz++) {
            level.setBlock(BlockPos.containing(X + dx, Y - 1, Z + dz), Blocks.STONE.defaultBlockState(), 3);
            for (int dy = 0; dy < 4; dy++) level.setBlock(BlockPos.containing(X + dx, Y + dy, Z + dz), Blocks.AIR.defaultBlockState(), 3);
        }
        ChangedEntity first = creature(level, ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level), 0);
        ChangedEntity second = creature(level, ChangedEntities.PURE_WHITE_LATEX_WOLF.get().create(level), 1);
        try {
            network();
            LatexSocialMemory.addBond(first, player);
            long hugTime=level.getGameTime();
            LatexSocialMemory.beginFriendlySocialHug(first,player,10);
            check(LatexSocialMemory.isFriendlySocialHugActive(first,player),"Original friendly hugs start normally");
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(hugTime+11);
            check(LatexSocialMemory.isFriendlySocialHugTarget(first,player)&&!LatexSocialMemory.isFriendlySocialHugActive(first,player),
                    "Original hug expiry remains distinct from cleanup ownership");
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(hugTime);
            LatexSocialMemory.endFriendlySocialHug(first);
            check(ready(first), "Idle social creature can play");
            SocialAudienceGoal.begin(first, player);
            check(CreaturePeerInteractionService.invitePlayer(first, player), "Player fallback creates a chase invitation");
            Object session = session(first);
            check(session == session(player) && stage(session) == INVITED, "Both participants have one shared invitation");
            int id = (int)get(session, "id");
            CreaturePeerInteractionService.gesture(player, id + 1, 0);
            check(stage(session) == INVITED, "Stale session input is ignored");
            CreaturePeerInteractionService.gesture(player, id, 3);
            check(stage(session) == INVITED, "Invalid gesture is ignored");
            CreaturePeerInteractionService.gesture(player, id, ChasePlayInputPacket.CROUCH);
            check(get(session, "chaser") == player && get(session, "runner") == first, "Crouch assigns player chaser and NPC runner");
            CreaturePeerInteractionService.gesture(player, id, ChasePlayInputPacket.JUMP);
            check(get(session, "chaser") == player, "Duplicate role packets cannot change accepted roles");
            set(session, "nextStageAt", level.getGameTime());
            step(session);
            check(stage(session)==HEAD_START && (long)get(session,"deadline")-level.getGameTime()==60,
                    "Runner immediately starts its three-second head start");
            check(!first.getNavigation().isDone(), "NPC runner has a path as soon as the countdown begins");
            set(session, "deadline", level.getGameTime());
            step(session);
            check(stage(session) == CHASING, "Head-start expiry begins the chase, rather than ending it");
            step(session);
            check(CreaturePeerInteractionService.active(first), "Proximity alone never catches an NPC for a player chaser");
            var click = new net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract(
                    player,net.minecraft.world.InteractionHand.MAIN_HAND,first);
            LatexSocialEvents.onBondedPetInteract(click);
            check(click.isCanceled() && !CreaturePeerInteractionService.active(first), "Right-click catches the nearby runner before opening a social menu");
            check(CreaturePeerMemory.knows(first, player.getUUID()), "Successful play remembers the partner");
            float health = first.getHealth();
            check(BondedSuitService.ability(first) == null || BondedSuitService.ability(first).grabbedEntity == null,
                    "Touch victory leaves no hold");
            check(first.getHealth() == health && ProcessTransfur.getPlayerTransfurVariantSafe(player).isEmpty(),
                    "Touch victory neither hurts nor transforms either participant");

            session = startPlayer(first, player, ChasePlayInputPacket.JUMP);
            check(get(session, "chaser") == first && get(session, "runner") == player,
                    "Jump assigns player runner and NPC chaser");
            check(first.getPose() == Pose.CROUCHING && first.isShiftKeyDown(), "NPC chaser visibly crouches");
            forceChase(session);
            first.setPose(Pose.STANDING);
            int soundsBefore=hugSounds;
            step(session);
            check(hugSounds==soundsBefore+1,"A player catch broadcasts one native grab sound");
            check(stage(session) == HUG && CreaturePeerInteractionService.isPlayHold(first, player), "NPC catch starts a game-only hold");
            String caughtLine=first.getPersistentData().getString("ChangedSynergyLastDialogue");
            var pat=LatexSocialEvents.class.getDeclaredMethod("tryPatPlayer",ServerPlayer.class);pat.setAccessible(true);pat.invoke(null,player);
            check(!hasPat(first)&&caughtLine.equals(first.getPersistentData().getString("ChangedSynergyLastDialogue")),
                    "Ambient patting cannot add friendly-hug chatter to a chase catch");
            var hold = BondedSuitService.ability(first);
            check(hold.grabbedEntity == player && !hold.suited && !hold.grabbedHasControl, "Native ability holds without suiting or borrowing control");
            check(LatexSocialMemory.isFriendlyArmHoldActive(first, player), "Existing friendly-hold safety recognizes the game");
            hold.attackDown = hold.useDown = true;
            Method decision = hold.getClass().getDeclaredMethod("makeAssimilationDecision");
            decision.setAccessible(true);
            check(decision.invoke(hold) == null, "Native assimilation decision is blocked for play");
            hold.tickIdle();
            check(hugSounds==soundsBefore+1,"Holding does not repeat the catch sound");
            check(!hold.attackDown && !hold.useDown && !hold.suited, "Native hold tick cannot turn input into assimilation");
            first.tick();
            check(hold.grabbedEntity == player && !hold.suited && !hold.grabbedHasControl
                            && BondedSuitService.getSuitController(first) == null,
                    "Native pet ownership cannot reinterpret a game hug as a suit");
            check(!hold.suitEntity(player), "External suit attempts cannot promote a play hold");
            // The generic Addon pet writes these bits at its entity-tick tail.
            hold.suited = hold.grabbedHasControl = true;
            check(BondedSuitService.getSuitController(first) == null, "An explicit arm hold never grants pet-suit control");
            hold.tickIdle();
            check(!hold.suited && !hold.grabbedHasControl && hold.suitTransition == 0,
                    "A stale native/Addon owner inference is cleared before hold physics and assimilation");
            CompoundTag saved = new CompoundTag();
            hold.saveData(saved);
            check(!saved.hasUUID("GrabbedEntity") && !saved.getBoolean("Suited"), "A world save never persists a half-finished play hold");
            set(session, "deadline", level.getGameTime());
            step(session);
            check(!CreaturePeerInteractionService.active(first) && hold.grabbedEntity == null && !player.noPhysics,
                    "Two-second expiry reliably releases the passenger and physics");
            check(first.getHealth() == health && !first.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),
                    "Game release causes no damage or escape stun");

            session = startPlayer(first, player, ChasePlayInputPacket.JUMP);
            forceChase(session);
            CreaturePeerInteractionService.gesture(player, (int)get(session, "id"), ChasePlayInputPacket.CROUCH);
            check(CreaturePeerInteractionService.active(player), "One crouch does not leave play");
            CreaturePeerInteractionService.gesture(player, (int)get(session, "id"), ChasePlayInputPacket.CROUCH);
            check(CreaturePeerInteractionService.active(player), "Two crouches do not leave play");
            CreaturePeerInteractionService.gesture(player, (int)get(session, "id"), ChasePlayInputPacket.CROUCH);
            check(!CreaturePeerInteractionService.active(player), "Three quick crouch presses leave play");
            playerRules(first,player,level);
            rangeAndStart(first,player,level);

            session = startPlayer(first, player, ChasePlayInputPacket.JUMP);
            forceChase(session);
            config.chasePlayShortHugs.set(false);
            step(session);
            check(!CreaturePeerInteractionService.active(first) && hold.grabbedEntity == null, "Disabling short hugs uses touch victory");
            config.chasePlayShortHugs.set(true);

            session = startPlayer(first, player, ChasePlayInputPacket.JUMP);
            forceChase(session);
            player.setPos(X + 7, Y, Z);
            set(session, "deadline", level.getGameTime());
            step(session);
            check(stage(session) == COMFORT && !getBoolean(session, "patStarted"), "Missed chase approaches for comfort");
            player.setPos(X + 1, Y, Z);
            step(session);
            check(getBoolean(session, "patStarted"), "Player winner is offered a voluntary comfort opportunity");
            check(!hasPat(player), "Comfort never starts a forced player pat animation");
            CreaturePeerInteractionService.cancel(first);

            session = startPlayer(first, player, ChasePlayInputPacket.CROUCH);
            forceChase(session);
            player.setPos(X + 7, Y, Z);
            set(session, "deadline", level.getGameTime());
            step(session);
            player.setPos(X + 1, Y, Z);
            step(session);
            check(hasPat(first), "NPC winner may pat the unsuccessful player chaser");
            CreaturePeerInteractionService.cancel(first);
            check(!hasPat(first), "Cancellation clears the NPC pat animation");

            session = startPlayer(first, player, ChasePlayInputPacket.CROUCH);
            first.setLastHurtByMob(player);
            check(!valid(session), "Combat takes priority over a game");
            first.setLastHurtByMob(null);
            SocialAudienceGoal.begin(first, player);
            check(!CreaturePeerInteractionService.active(first), "Opening a command wheel cancels play");
            SocialAudienceGoal.end(first, player);
            session = startPlayer(first, player, ChasePlayInputPacket.CROUCH);
            config.chasePlayGames.set(false);
            check(!valid(session), "Game toggle invalidates an active session");
            CreaturePeerInteractionService.cancel(first);
            check(!CreaturePeerInteractionService.invitePlayer(first, player), "Disabled chase returns control to the old ordinary feedback");
            config.chasePlayGames.set(true);

            session = inviteOnly(first, player);
            set(session, "deadline", level.getGameTime());
            step(session);
            check(!CreaturePeerInteractionService.active(first), "Unanswered invitation expires");

            peerGames(first, second, player);
            ambient(first, second);
            crowd(level);
            LatexSocialMemory.addBond(second, player);
            movement(first, second, level);
            eligibility(first, second, player);
            memory(first, second, level);
            interruptions(first, second, player, level);
            for (int i = 0; i < 20; i++) LatexSocialEvents.ensureSocialGoals(first);
            check(first.goalSelector.getAvailableGoals().stream().filter(w -> w.getGoal() instanceof CreaturePeerInteractionGoal).count() == 1,
                    "Repeated AI repair installs exactly one peer goal");
            System.out.println("PEER_PLAY_REGRESSION_PASS: " + checks + " checks; real native holds, gesture roles, harmless expiry, memory, eligibility and interrupts");
        } finally {
            CreaturePeerInteractionService.cancel(first);
            CreaturePeerInteractionService.cancel(second);
            config.ambientPeerInteractions.set(true);
            config.chasePlayGames.set(true);
            config.chasePlayShortHugs.set(true);
            for (ChangedEntity creature : CREATURES) creature.discard();
            CREATURES.clear();
            players.remove(player.getUUID());
            online.remove(player);
            player.discard();
        }
    }

    private static void peerGames(ChangedEntity first, ChangedEntity second, ServerPlayer player) throws Exception {
        first.setPose(Pose.STANDING);first.setShiftKeyDown(false);player.setPos(X + 1, Y, Z);
        Object session = npcSession(first, second, true);
        step(session);
        check(get(session, "chaser") != get(session, "runner"), "NPC invitation chooses complementary roles");
        boolean firstChased = get(session, "chaser") == first;
        set(session, "nextStageAt", first.level().getGameTime());
        step(session);
        check((int)get(session, "signalStep") == 2, "Second NPC confirms the opposite gesture");
        set(session, "nextStageAt", first.level().getGameTime());
        first.setOnGround(true);second.setOnGround(true);
        step(session);
        check(stage(session) == HEAD_START, "NPC complementary gestures start the runner immediately");
        forceChase(session);
        invoke("missed", session);
        CreaturePeerInteractionService.cancel(first);
        check(CreaturePeerMemory.nextChases(first, second.getUUID()) != firstChased, "Next game prefers exchanged roles");

        first.setPose(Pose.STANDING);first.setShiftKeyDown(false);second.setPose(Pose.STANDING);second.setShiftKeyDown(false);
        session = npcSession(first, second, false);
        step(session);step(session);
        check(hasPat(first), "Short NPC interaction starts a pat");
        set(session, "nextStageAt", first.level().getGameTime());
        step(session);
        check(hasPat(second), "The other NPC can return the pat");
        CreaturePeerInteractionService.cancel(first);
        check(!hasPat(first) && !hasPat(second), "Pair cleanup clears both NPC animations");
    }

    private static void playerRules(ChangedEntity npc, ServerPlayer player, ServerLevel level) throws Exception {
        long time=level.getGameTime();
        try {
            Object session=startPlayer(npc,player,ChasePlayInputPacket.CROUCH);forceChase(session);
            player.setPos(X+5,Y,Z);
            check(CreaturePeerInteractionService.playerTouch(player,npc)&&CreaturePeerInteractionService.active(player),
                    "A distant right-click is consumed without granting a catch");
            player.setPos(X+1,Y,Z);
            LatexSocialEvents.onPatted(npc,player);
            check(CreaturePeerInteractionService.active(player),"Patting a nearby runner never grants a catch");
            CreaturePeerInteractionService.playerTouch(player,npc);
            check(!CreaturePeerInteractionService.active(player),"Right-click remains the sole player tagging input");

            session=startPlayer(npc,player,ChasePlayInputPacket.CROUCH);
            set(session,"nextStageAt",level.getGameTime());step(session);
            check(stage(session)==HEAD_START,"Only the chaser waits through the three-second head start");
            player.setYRot(180);step(session);
            check(stage(session)==HEAD_START,"Looking around cannot false-start");
            player.setPos(X+1.1,Y,Z);step(session);
            check(stage(session)==HEAD_START,"Small incidental movement is tolerated");
            player.setPos(X+.25,Y,Z);step(session);
            check(stage(session)==FALSE_START&&!getBoolean(session,"remembered"),"Moving toward the runner early voids the round without a win");
            check(!hasPat(npc)&&BondedSuitService.ability(npc).grabbedEntity==null,"False starts stop without a hug or forced pat");
            String foulLine=npc.getPersistentData().getString("ChangedSynergyLastDialogue");
            set(session,"deadline",level.getGameTime());step(session);
            check(!CreaturePeerInteractionService.active(player),"False starts safely finish without a second ending line");
            check(foulLine.equals(npc.getPersistentData().getString("ChangedSynergyLastDialogue")),"False-start feedback is not overwritten by a generic ending");
            player.setPos(X+1,Y,Z);

            session=startPlayer(npc,player,ChasePlayInputPacket.CROUCH);
            set(session,"nextStageAt",level.getGameTime());step(session);
            CreaturePeerInteractionService.playerTouch(player,npc);
            check(stage(session)==FALSE_START,"Trying to tag during the head start also counts as a false start");
            CreaturePeerInteractionService.cancel(npc);

            session=startPlayer(npc,player,ChasePlayInputPacket.JUMP);forceChase(session);
            int id=(int)get(session,"id");
            CreaturePeerInteractionService.gesture(player,id,0);
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(time+31);
            CreaturePeerInteractionService.gesture(player,id,0);
            CreaturePeerInteractionService.gesture(player,id,0);
            check(CreaturePeerInteractionService.active(player),"Separated crouches do not form an accidental triple tap");
            CreaturePeerInteractionService.gesture(player,id,0);
            check(!CreaturePeerInteractionService.active(player),"A new complete triple tap leaves after the old sequence expires");
        } finally {
            CreaturePeerInteractionService.cancel(npc);player.setPos(X+1,Y,Z);
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(time);
        }
    }

    private static void eligibility(ChangedEntity first, ChangedEntity second, ServerPlayer player) throws Exception {
        first.setPose(Pose.STANDING);first.setShiftKeyDown(false);
        first.setNoAi(true);check(!ready(first), "No-AI entities cannot join");first.setNoAi(false);
        first.setLastHurtByMob(player);check(!ready(first), "Recent combat prevents joining");first.setLastHurtByMob(null);
        var boss = creature((ServerLevel)first.level(), (ChangedEntity)net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                .getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("changed", "latex_benign_wolf")).create(first.level()), 4);
        check(!ready(boss), "Socially excluded creatures remain excluded");
        var pup = creature((ServerLevel)first.level(), ChangedEntities.PURE_WHITE_LATEX_WOLF_PUP.get().create(first.level()), 4);
        check(ready(pup), "Juveniles are eligible for appropriate social play");
        check(!(boolean)method("canHug", ChangedEntity.class, LivingEntity.class).invoke(null, pup, player),
                "Bodies excluded from grabs fall back to touch celebration");
        second.setLastHurtByMob(player);
        check(!ready(second), "Candidate safety is checked on both participants");
        second.setLastHurtByMob(null);
        var config = ChangedSynergyConfig.COMMON;
        check(config.chasePlayDurationSeconds.get() == 15 && config.chasePlayCooldownSeconds.get() == 180,
                "Defaults retain 15-second games and three-minute NPC chase cooldown");
    }

    private static void rangeAndStart(ChangedEntity npc, ServerPlayer player, ServerLevel level) throws Exception {
        long now=level.getGameTime();
        try {
            player.setPos(X+1,Y,Z);npc.setPos(X,Y,Z);
            Object session=startPlayer(npc,player,ChasePlayInputPacket.CROUCH);forceChase(session);
            player.setPos(X+64,Y,Z);level.getChunkAt(player.blockPosition());
            check(valid(session),"A 64-block gap does not cancel a loaded chase");
            npc.setPos(X+64,Y,Z);level.getChunkAt(npc.blockPosition());player.setPos(X+65,Y,Z);
            check(valid(session),"Moving both participants away from the starting point preserves play");
            for(int x=52;x<=76;x++)for(int z=-12;z<=12;z++) {
                level.setBlock(BlockPos.containing(X+x,Y-1,Z+z),Blocks.STONE.defaultBlockState(),3);
                for(int y=0;y<4;y++)level.setBlock(BlockPos.containing(X+x,Y+y,Z+z),Blocks.AIR.defaultBlockState(),3);
            }
            check(method("fleePoint",session.getClass(),ChangedEntity.class,LivingEntity.class).invoke(null,session,npc,player)!=null,
                    "Runner destinations remain available beyond the old origin radius");
            player.setPos(X+194,Y,Z);
            check(!valid(session),"Extreme separation still safely abandons a chase");
            CreaturePeerInteractionService.cancel(npc);npc.setPos(X,Y,Z);player.setPos(X+1,Y,Z);
            session=startPlayer(npc,player,ChasePlayInputPacket.JUMP);
            method("restorePose",ChangedEntity.class).invoke(null,npc);
            set(session,"nextStageAt",now);step(session);
            check(stage(session)==HEAD_START,"Player runner starts at the beginning of the head start");
            player.setPos(X+7,Y,Z);set(session,"nextChaserRepath",now+200);set(session,"deadline",now);step(session);
            check(stage(session)==CHASING&&!npc.getNavigation().isDone(),"NPC chaser navigates on the exact expiry tick without a repath delay");
            player.setPos(X+64,Y,Z);set(session,"nextChaserRepath",now);CreaturePeerInteractionService.move(npc);
            BlockPos target=npc.getNavigation().getTargetPos();
            check(target!=null&&target.distSqr(npc.blockPosition())<=13*13&&!npc.getNavigation().isDone(),
                    "A distant runner is pursued through a bounded local waypoint");
        } finally {
            CreaturePeerInteractionService.cancel(npc);npc.setPos(X,Y,Z);player.setPos(X+1,Y,Z);
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(now);
        }
    }

    private static void movement(ChangedEntity first, ChangedEntity second, ServerLevel level) throws Exception {
        first.setPose(Pose.STANDING);first.setShiftKeyDown(false);second.setPose(Pose.STANDING);second.setShiftKeyDown(false);
        first.setPos(X,Y,Z);second.setPos(X+8,Y,Z);first.setOnGround(true);second.setOnGround(true);
        LatexSocialMemory.setFollowingOwner(first,false);
        LatexSocialEvents.ensureSocialGoals(first);LatexSocialEvents.ensureSocialGoals(second);
        Object session=npcSession(first,second,true);
        set(session,"chaser",first);set(session,"runner",second);set(session,"rolesChosen",true);forceChase(session);
        long original=level.getGameTime();
        double firstStart=first.getX(),secondStart=second.getX();
        int ticks=0;
        try {
            for(int i=1;i<=100&&CreaturePeerInteractionService.active(first);i++) {
                ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(original+i);
                first.tick();second.tick();
                if(CreaturePeerInteractionService.active(first)) {
                    if(!valid(session)) for(ChangedEntity mob:List.of(first,second)) System.err.println("PEER_PLAY_MOVEMENT_STATE: "+mob.getType()
                            +" target="+mob.getTarget()+" pose="+mob.getPose()+" hurt="+mob.getLastHurtByMob()+" pos="+mob.position()
                            +" available="+method("available",ChangedEntity.class,session.getClass()).invoke(null,mob,session)
                            +" busy="+net.parkabird.changedsynergy.ai.CreatureGrabService.isGrabberBusy(mob)
                            +" hold="+(BondedSuitService.ability(mob)==null?null:BondedSuitService.ability(mob).grabbedEntity)
                            +" held="+net.ltxprogrammer.changed.ability.GrabEntityAbility.getGrabberSafe(mob).isPresent()
                            +" cargo="+CreatureSettlementService.hasCargo(mob)+"/"+PlayerOutpostGoal.hasCargo(mob)+"/"+PlayerOutpostWorkGoal.hasCargo(mob)
                            +" working="+PlayerOutpostService.isWorking(mob)+" audience="+SocialAudienceGoal.isActive(mob)
                            +" follow="+method("followedPlayer",ChangedEntity.class).invoke(null,mob)
                            +" aggressive="+CreatureSocialProfile.isAggressive(mob)+" rest="+mob.getPersistentData().hasUUID("ChangedSynergyRestOwner")
                            +" goals="+mob.goalSelector.getRunningGoals().map(w->w.getGoal().getClass().getSimpleName()+":"+w.getPriority()).toList());
                    check(valid(session),"Native AI leaves the running game valid at tick "+i);
                    step(session);ticks++;
                }
            }
            check(ticks>=20||getBoolean(session,"remembered"),"Native priorities do not immediately cancel the chase");
            check(Math.abs(first.getX()-firstStart)>0.5,"The NPC chaser actually moves with native navigation");
            check(second.position().distanceToSqr(new net.minecraft.world.phys.Vec3(secondStart,Y,Z))>0.25,
                    "The NPC runner actually flees using native navigation");
            check(first.getTarget()==null&&second.getTarget()==null,"Chase navigation never installs a combat target");
            System.out.println("PEER_PLAY_NATIVE_MOVEMENT_PASS: "+ticks+" native AI/physics ticks; chaser="+first.position()+" runner="+second.position());
        } finally {
            CreaturePeerInteractionService.cancel(first);CreaturePeerInteractionService.cancel(second);
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(original);
            first.setPos(X,Y,Z);second.setPos(X+1,Y,Z);first.setOnGround(true);second.setOnGround(true);
        }
    }

    private static void memory(ChangedEntity first, ChangedEntity second, ServerLevel level) throws Exception {
        for (int i = 0; i < 15; i++) CreaturePeerMemory.remember(first, UUID.randomUUID(), i % 2 == 0);
        check(CreaturePeerMemory.data(first).getList("Playmates", 10).size() == 8, "Playmate memory remains bounded");
        UUID old = UUID.randomUUID();
        CreaturePeerMemory.remember(first, old, true);
        CreatureMorphAliasData.get(level.getServer()).record(old, second.getUUID());
        check(CreaturePeerMemory.knows(first, second.getUUID()), "Morph aliases resolve old playmate identities lazily");
        CompoundTag saved = first.saveWithoutId(new CompoundTag());
        var restored = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        restored.load(saved);
        check(CreaturePeerMemory.knows(restored, second.getUUID()), "Save and reload preserves playmate preferences");
        check(!CreaturePeerInteractionService.active(restored), "No active game is restored from the save");
    }

    private static void interruptions(ChangedEntity first, ChangedEntity second, ServerPlayer player, ServerLevel level) throws Exception {
        first.setPose(Pose.STANDING);first.setShiftKeyDown(false);second.setPose(Pose.STANDING);second.setShiftKeyDown(false);
        first.setPos(X,Y,Z);second.setPos(X+1,Y,Z);player.setPos(X+1,Y,Z);
        LatexSocialMemory.setFollowingOwner(second,true);
        Object session=npcSession(first,second,true);
        var oldVelocity=player.getDeltaMovement();
        player.setDeltaMovement(0.12,0,0);
        check(valid(session),"A momentary bump of a stationary owner does not end an existing game");
        player.setDeltaMovement(oldVelocity);
        player.setPos(X+4,Y,Z);
        check(!valid(session),"A following companion leaves play when its owner walks away");
        CreaturePeerInteractionService.cancel(first);player.setPos(X+1,Y,Z);
        session=inviteOnly(first,player);
        var zombie=net.minecraft.world.entity.EntityType.ZOMBIE.create(level);zombie.setPos(X+3,Y,Z);level.addFreshEntity(zombie);
        check(valid(session)&&!(boolean)method("monsterCombat",ChangedEntity.class).invoke(null,first),"A nearby monster alone does not add a new interruption condition");
        // Exercise the committed combat state, independently of pet target-admission rules.
        ObfuscationReflectionHelper.setPrivateValue(net.minecraft.world.entity.Mob.class,first,zombie,"f_21362_");
        check(!valid(session)&&(boolean)method("monsterCombat",ChangedEntity.class).invoke(null,first),"Existing monster combat interruption is classified for spoken warning");
        CreaturePeerInteractionService.cancel(first);check(!CreaturePeerInteractionService.active(first)&&!CreaturePeerInteractionService.active(player),"Monster interruption uses normal complete session cleanup");
        first.setTarget(null);first.setLastHurtByMob(zombie);
        check((boolean)method("monsterCombat",ChangedEntity.class).invoke(null,first),"Existing recent monster damage gets the same warning");
        first.setLastHurtByMob(null);first.setLastHurtMob(null);zombie.discard();
        first.setTarget(second);check(!(boolean)method("monsterCombat",ChangedEntity.class).invoke(null,first),"Latex combat is not mislabeled as a monster interruption");first.setTarget(null);
        session=inviteOnly(first,player);
        first.setHealth(0);
        check(!valid(session),"Death interrupts the pair");
        first.setHealth(first.getMaxHealth());CreaturePeerInteractionService.cancel(first);

        session=inviteOnly(first,player);
        long failureStart=level.getGameTime();
        for(int i=0;i<6;i++) invoke("pathFailed",session);
        check(CreaturePeerInteractionService.active(first),"Brief path failures do not end a game");
        ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(failureStart+100);
        invoke("pathFailed",session);
        check(!CreaturePeerInteractionService.active(first),"Persistent path failures still end safely");
        ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(failureStart);
        int unloadedX=1000000,unloadedZ=1000000;
        check(!level.getChunkSource().hasChunk(unloadedX>>4,unloadedZ>>4),"Far navigation probe starts in unloaded terrain");
        check(method("groundPoint",ChangedEntity.class,net.minecraft.world.phys.Vec3.class).invoke(null,first,
                new net.minecraft.world.phys.Vec3(unloadedX,Y,unloadedZ))==null,"Flee destinations in unloaded terrain are rejected");
        check(!level.getChunkSource().hasChunk(unloadedX>>4,unloadedZ>>4),"Navigation probing does not load the rejected chunk");
        player.setPos(X+0.9,Y,Z);
        BlockPos wall=BlockPos.containing(X+1,Y+1,Z);
        level.setBlock(wall,Blocks.STONE.defaultBlockState(),3);
        check(!(boolean)method("touching",LivingEntity.class,LivingEntity.class).invoke(null,first,player),"Nearby participants cannot catch through a wall");
        level.setBlock(wall,Blocks.AIR.defaultBlockState(),3);player.setPos(X+1,Y,Z);

        // Earlier cases release an airborne hold, then rewind the simulated clock.
        // Let its real recapture cooldown expire before testing a new catch.
        long regrabAfter=first.getPersistentData().getLong(EagleOwlTraits.REGRAB_AFTER);
        if(regrabAfter>level.getGameTime())((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(regrabAfter);
        var peer=creature(level,ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level),1);
        session=npcSession(first,peer,true);set(session,"chaser",first);set(session,"runner",peer);forceChase(session);
        int soundsBefore=hugSounds;step(session);
        check(hugSounds==soundsBefore+1,"An NPC-to-NPC catch broadcasts the same native grab sound");
        check(stage(session)==HUG,"Suitable NPC playmates can gently grab one another");
        var hold=BondedSuitService.ability(first);float hp=first.getHealth();
        hold.tickIdle();
        check(CreaturePeerInteractionService.isHeld(peer),"Native NPC goal stopping does not cancel its own safe hold");
        hold.releaseEntity(true);
        check(first.getHealth()==hp&&!first.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),"Native escape from a game hold cannot hurt or stun the grabber");
        step(session);
        check(!CreaturePeerInteractionService.active(peer)&&!peer.noPhysics,"Early native release clears both NPCs and physics");
        peer.discard();

        session=inviteOnly(first,player);
        var replacement=creature(level,ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level),3);
        CreatureMorphContinuity.transferForced(first,replacement);
        check(!CreaturePeerInteractionService.active(first),"Morph continuity cancels the old body's active game");
        check(CreaturePeerMemory.knows(replacement,second.getUUID()),"A morph carries the existing playmate memory into the replacement");
        replacement.discard();
    }

    private static void ambient(ChangedEntity first, ChangedEntity second) throws Exception {
        var config=ChangedSynergyConfig.COMMON;
        boolean stagger=config.staggerBackgroundAi.get(),budget=config.adaptiveAiBudget.get(),distant=config.distantAiThrottling.get();
        int oldTick=first.tickCount;
        try {
            config.staggerBackgroundAi.set(false);config.adaptiveAiBudget.set(false);config.distantAiThrottling.set(false);
            first.setPose(Pose.STANDING);first.setShiftKeyDown(false);second.setPose(Pose.STANDING);second.setShiftKeyDown(false);
            first.tickCount=80;
            long now=first.level().getGameTime();
            for(int attempt=0;attempt<30&&!CreaturePeerInteractionService.active(first);attempt++) {
                CreaturePeerMemory.data(first).putLong("NextPeerInteraction",now-1);
                CreaturePeerMemory.data(second).putLong("NextPeerInteraction",now-1);
                CreaturePeerInteractionService.ambient(new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(first));
            }
            check(CreaturePeerInteractionService.active(first)&&session(first)==session(second),"Real background decision pairs nearby non-hostile NPCs");
            check(CreaturePeerMemory.data(first).getLong("NextPeerInteraction")>=now+900
                    &&CreaturePeerMemory.data(second).getLong("NextPeerInteraction")>=now+900,"Both automatic participants receive the short-interaction cooldown");
            CreaturePeerInteractionService.cancel(first);
            CreaturePeerInteractionService.ambient(new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(first));
            check(!CreaturePeerInteractionService.active(first),"Background cooldown prevents immediate repeated invitations");
        } finally {
            CreaturePeerInteractionService.cancel(first);first.tickCount=oldTick;
            config.staggerBackgroundAi.set(stagger);config.adaptiveAiBudget.set(budget);config.distantAiThrottling.set(distant);
        }
    }

    private static void crowd(ServerLevel level) throws Exception {
        var config=ChangedSynergyConfig.COMMON;
        boolean stagger=config.staggerBackgroundAi.get(),budget=config.adaptiveAiBudget.get(),distant=config.distantAiThrottling.get();
        var crowd=new ArrayList<ChangedEntity>();
        Field sessions=SERVICE.getDeclaredField("SESSIONS");sessions.setAccessible(true);
        try {
            config.staggerBackgroundAi.set(false);config.adaptiveAiBudget.set(false);config.distantAiThrottling.set(false);
            for(int i=0;i<64;i++) {
                var npc=creature(level,ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level),i%8-3);
                npc.setPos(npc.getX(),Y,Z+i/8-3);npc.tickCount=80;crowd.add(npc);
            }
            int maximum=0;
            for(int round=0;round<4;round++) for(var npc:crowd) {
                if(!CreaturePeerInteractionService.active(npc)) CreaturePeerMemory.data(npc).putLong("NextPeerInteraction",level.getGameTime()-1);
                CreaturePeerInteractionService.ambient(new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(npc));
                int count=((java.util.Set<?>)sessions.get(null)).size();maximum=Math.max(maximum,count);
                check(count<=4,"A dense gathering respects the four local pair budget");
            }
            check(maximum==4,"A dense gathering can still fill its bounded social budget");
            check(crowd.stream().filter(CreaturePeerInteractionService::active).count()==8,"Each active pair reserves two unique participants");
            System.out.println("PEER_PLAY_CROWD_PASS: 64 real NPCs; 256 background decisions; at most four local pairs");
        } finally {
            for(var npc:crowd) {CreaturePeerInteractionService.cancel(npc);npc.discard();}
            config.staggerBackgroundAi.set(stagger);config.adaptiveAiBudget.set(budget);config.distantAiThrottling.set(distant);
        }
    }

    private static ChangedEntity creature(ServerLevel level, ChangedEntity creature, int dx) {
        creature.setPos(X + dx, Y, Z);creature.setOnGround(true);level.addFreshEntity(creature);CREATURES.add(creature);return creature;
    }
    private static Object inviteOnly(ChangedEntity first, ServerPlayer player) throws Exception {
        CreaturePeerInteractionService.cancel(first);first.setPose(Pose.STANDING);first.setShiftKeyDown(false);
        check(CreaturePeerInteractionService.invitePlayer(first, player), "Invitation branch handled");
        Object session = session(first);check(session != null, "Invitation reserves the pair");return session;
    }
    private static Object startPlayer(ChangedEntity first, ServerPlayer player, int gesture) throws Exception {
        first.setOnGround(true);player.setOnGround(true);
        Object session = inviteOnly(first, player);
        CreaturePeerInteractionService.gesture(player, (int)get(session, "id"), gesture);return session;
    }
    private static Object npcSession(ChangedEntity first, ChangedEntity second, boolean chase) throws Exception {
        var constructor = Class.forName(SERVICE.getName() + "$Session").getDeclaredConstructor(ChangedEntity.class, LivingEntity.class, boolean.class);
        constructor.setAccessible(true);Object session = constructor.newInstance(first, second, chase);
        invoke("reserve", session);return session;
    }
    private static void forceChase(Object session) throws Exception { set(session, "stage", CHASING);set(session, "deadline", ((ChangedEntity)get(session,"first")).level().getGameTime() + 300); }
    private static void step(Object session) throws Exception { invoke("advance", session); }
    private static boolean valid(Object session) throws Exception { return (boolean)invoke("valid", session); }
    private static boolean ready(ChangedEntity mob) throws Exception { return (boolean)method("available",ChangedEntity.class,Class.forName(SERVICE.getName()+"$Session")).invoke(null,mob,null); }
    private static Object invoke(String name,Object session) throws Exception { return method(name,session.getClass()).invoke(null,session); }
    private static Method method(String name,Class<?>... types) throws Exception { Method m=SERVICE.getDeclaredMethod(name,types);m.setAccessible(true);return m; }
    private static int stage(Object session) throws Exception { return (int)get(session,"stage"); }
    private static boolean getBoolean(Object session,String key) throws Exception { return (boolean)get(session,key); }
    private static Object get(Object object,String name) throws Exception { Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object); }
    private static void set(Object object,String name,Object value) throws Exception { Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value); }
    private static Object session(LivingEntity entity) throws Exception { Field f=SERVICE.getDeclaredField("PARTICIPANTS");f.setAccessible(true);return ((Map<?,?>)f.get(null)).get(entity.getUUID()); }
    private static boolean hasPat(LivingEntity entity) throws Exception { Field f=PatAnimationService.class.getDeclaredField("SESSIONS");f.setAccessible(true);return ((Map<?,?>)f.get(null)).containsKey(entity.getUUID()); }
    private static void network() {
        var input = new ChasePlayInputPacket(42, 1);var data = new FriendlyByteBuf(Unpooled.buffer());
        ChasePlayInputPacket.encode(input,data);check(input.equals(ChasePlayInputPacket.decode(data)),"Input packet roundtrip");data.release();
        var state = new ChasePlayStatePacket(42,CHASING,300,true,1);data = new FriendlyByteBuf(Unpooled.buffer());
        ChasePlayStatePacket.encode(state,data);check(state.equals(ChasePlayStatePacket.decode(data)),"State packet roundtrip");data.release();
    }
    private static void check(boolean condition,String message) { checks++;if(!condition)throw new AssertionError(message); }
}
