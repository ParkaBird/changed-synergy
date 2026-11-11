package net.parkabird.changedsynergy.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.ltxprogrammer.changed.ability.GrabEntityAbility;
import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.Emote;
import net.ltxprogrammer.changed.entity.TamableLatexEntity;
import net.ltxprogrammer.changed.entity.beast.AquaticEntity;
import net.ltxprogrammer.changed.entity.variant.EntityShape;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.ltxprogrammer.changed.init.ChangedSounds;
import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.network.packet.GrabEntityPacket;
import net.ltxprogrammer.changed.network.packet.GrabEntityPacket.GrabType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.compat.ChangedExtrasCompat;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue;
import net.parkabird.changedsynergy.performance.SynergyPerformanceTracker;
import net.parkabird.changedsynergy.performance.SynergyPerformanceTracker.Feature;
import net.parkabird.changedsynergy.network.ChasePlayStatePacket;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.FriendlySocialHugSyncPacket;
import static net.parkabird.changedsynergy.network.ChasePlayStatePacket.*;

/** Loaded, temporary pairs. Hostility and player relationship data never serve as game state. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class CreaturePeerInteractionService {
    private static final Map<UUID, Session> PARTICIPANTS = new HashMap<>();
    private static final Set<Session> SESSIONS = new LinkedHashSet<>();
    private static final Map<ChangedEntity, Crouch> CROUCHES = new HashMap<>();
    private static final String NEXT_SOCIAL = "NextPeerInteraction", NEXT_CHASE = "NextPeerChase";
    private static int nextId;
    private CreaturePeerInteractionService() {}

    public static boolean active(LivingEntity entity) {
        return !entity.level().isClientSide && PARTICIPANTS.containsKey(entity.getUUID());
    }

    public static boolean isHeld(LivingEntity entity) {
        if (entity.level().isClientSide) return false;
        Session session = PARTICIPANTS.get(entity.getUUID());
        return session != null && session.holder != null && session.runner == entity
                && session.hold != null && session.hold.grabbedEntity == entity;
    }

    public static boolean isPlayHold(ChangedEntity carrier, LivingEntity target) {
        if (carrier.level().isClientSide) return false;
        Session session = PARTICIPANTS.get(carrier.getUUID());
        return session != null && session.holder == carrier && session.runner == target;
    }

    public static void cancel(LivingEntity participant) {
        if (participant == null || participant.level().isClientSide) return;
        Session session = PARTICIPANTS.get(participant.getUUID());
        if (session != null) end(session, true);
    }

    /** Called only after the old hypnosis and random-hug branches have declined. */
    public static boolean invitePlayer(ChangedEntity creature, ServerPlayer player) {
        if (!ChangedSynergyConfig.COMMON.chasePlayGames.get()) return false;
        SocialAudienceGoal.end(creature, player);
        if (active(player) || !available(creature, null) || !playerAvailable(player)
                || creature.distanceToSqr(player) > 64.0D || !canChaseTogether(creature, player)
                || !(LatexSocialMemory.isBonded(creature, player)
                        || CreaturePersonality.hasTrustedRelationship(creature, player))) {
            NpcDialogue.trigger(creature, player, Cue.CHASE_PLAY_UNAVAILABLE);
            return true;
        }
        Session session = new Session(creature, player, true);
        reserve(session);
        session.stage = INVITED;
        session.deadline = session.now() + 200;
        sync(session);
        NpcDialogue.trigger(creature, player, Cue.CHASE_PLAY_INVITE);
        return true;
    }

    public static void gesture(ServerPlayer player, int id, int gesture) {
        Session session = PARTICIPANTS.get(player.getUUID());
        if (session == null || session.id != id || session.player != player || gesture < 0 || gesture > 1
                || !valid(session) || session.now() > session.deadline) return;
        long now = session.now();
        if (session.stage == INVITED) {
            session.chaser = gesture == 0 ? player : session.first;
            session.runner = gesture == 0 ? session.first : player;
            session.rolesChosen = true;
            showRole(session.first, session.chaser == session.first, now);
            session.stage = READY;
            session.nextStageAt = now + 16;
            session.deadline = now + 160;
            sync(session);
        } else if (gesture == 0 && session.stage >= READY) {
            if (session.exitPresses == 0 || now - session.exitSequenceAt > 30) {
                session.exitPresses = 0;
                session.exitSequenceAt = now;
            }
            if (++session.exitPresses == 3) {
                end(session, true);
            }
        }
    }

    /** Consume playmate touches before gifts, pet menus or normal pat rewards. */
    public static boolean playerTouch(ServerPlayer player, LivingEntity target) {
        Session session = PARTICIPANTS.get(player.getUUID());
        if (!isPlaymate(player, target)) return false;
        if (!valid(session)) { end(session, true); return true; }
        if (session.chaser == player && touching(player, target)) {
            if (session.stage == HEAD_START && session.now() < session.deadline) falseStart(session);
            else if (session.stage == CHASING) caught(session);
        }
        return true;
    }

    public static boolean isPlaymate(ServerPlayer player, LivingEntity target) {
        Session session = PARTICIPANTS.get(player.getUUID());
        return session != null && session.player == player && session.first == target;
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void interactAt(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getHand() == InteractionHand.MAIN_HAND && event.getEntity() instanceof ServerPlayer player
                && event.getTarget() instanceof LivingEntity target && playerTouch(player, target)) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent public static void ambient(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ChangedEntity creature) || creature.level().isClientSide
                || !ChangedSynergyConfig.COMMON.ambientPeerInteractions.get()
                || !CreatureLifeMemory.enabled(creature) || active(creature)
                || !SynergyPerformanceTracker.featureEnabled(Feature.SOCIAL)) return;
        long now = creature.level().getGameTime();
        var life = CreaturePeerMemory.data(creature);
        if (!life.contains(NEXT_SOCIAL)) {
            life.putLong(NEXT_SOCIAL, now + 900 + creature.getRandom().nextInt(401));
            return;
        }
        if (now < life.getLong(NEXT_SOCIAL) || !SynergyPerformanceTracker.allowBackground(creature, Feature.SOCIAL, 80)
                || !available(creature, null)) return;
        life.putLong(NEXT_SOCIAL, now + 900 + creature.getRandom().nextInt(401));
        if (SESSIONS.stream().filter(s -> s.first.level() == creature.level()
                && s.first.distanceToSqr(creature) < 256).count() >= 4) return;
        List<ChangedEntity> peers = new ArrayList<>(creature.level().getEntitiesOfClass(ChangedEntity.class,
                creature.getBoundingBox().inflate(8, 3, 8), other -> other != creature
                        && available(other, null) && peaceful(creature, other)
                        && other.hasLineOfSight(creature)
                        && now >= CreaturePeerMemory.data(other).getLong(NEXT_SOCIAL)));
        peers.sort(Comparator.<ChangedEntity>comparingInt(other -> CreaturePeerMemory.knows(creature, other.getUUID()) ? 0 : 1)
                .thenComparingDouble(creature::distanceToSqr));
        for (ChangedEntity other : peers) {
            if (creature.getRandom().nextDouble() > willingness(creature, other)
                    || other.getRandom().nextDouble() > willingness(other, creature)) {
                NpcDialogue.emoteOnly(creature, Emote.IDEA);
                NpcDialogue.emoteOnly(other, Emote.DENY);
                CreaturePeerMemory.data(other).putLong(NEXT_SOCIAL, now + 900);
                break;
            }
            boolean chase = ChangedSynergyConfig.COMMON.chasePlayGames.get()
                    && canChaseTogether(creature, other)
                    && now >= life.getLong(NEXT_CHASE)
                    && now >= CreaturePeerMemory.data(other).getLong(NEXT_CHASE)
                    && creature.getRandom().nextDouble() < chasePreference(creature);
            Session session = new Session(creature, other, chase);
            reserve(session);
            NpcDialogue.emoteOnly(creature, Emote.IDEA);
            NpcDialogue.emoteOnly(other, Emote.CASUAL);
            break;
        }
    }

    private static double willingness(ChangedEntity creature, ChangedEntity other) {
        if (CreaturePersonality.has(creature, CreaturePersonality.Trait.PLAYFUL)
                || CreaturePersonality.has(creature, CreaturePersonality.Trait.COMPETITIVE)
                || CreaturePersonality.has(creature, CreaturePersonality.Trait.SHOW_OFF)) return 0.9;
        if (CreaturePersonality.has(creature, CreaturePersonality.Trait.CAUTIOUS)
                || CreaturePersonality.has(creature, CreaturePersonality.Trait.SENSITIVE)) {
            return CreaturePeerMemory.knows(creature, other.getUUID()) ? 0.8 : 0.4;
        }
        return 0.65;
    }

    private static double chasePreference(ChangedEntity creature) {
        if (CreaturePersonality.has(creature, CreaturePersonality.Trait.PLAYFUL)
                || CreaturePersonality.has(creature, CreaturePersonality.Trait.COMPETITIVE)
                || CreaturePersonality.has(creature, CreaturePersonality.Trait.SHOW_OFF)) return 0.7;
        return CreaturePersonality.has(creature, CreaturePersonality.Trait.CALM) ? 0.2 : 0.4;
    }

    private static void reserve(Session session) {
        SESSIONS.add(session);
        PARTICIPANTS.put(session.first.getUUID(), session);
        PARTICIPANTS.put(session.second.getUUID(), session);
        for (ChangedEntity creature : session.npcs()) {
            creature.getNavigation().stop();
            var life = CreaturePeerMemory.data(creature);
            life.putLong(NEXT_SOCIAL, session.now() + 900);
            if (session.chase && session.player == null) life.putLong(NEXT_CHASE,
                    session.now() + ChangedSynergyConfig.COMMON.chasePlayCooldownSeconds.get() * 20L);
            ServerPlayer owner = followedPlayer(creature);
            if (owner != null && owner != session.player) session.owners.put(owner, TrueTransfurCompat.interactionBody(owner).position());
        }
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (Session session : List.copyOf(SESSIONS)) {
            if (!valid(session)) { end(session, true); continue; }
            advance(session);
            if (SESSIONS.contains(session) && session.player != null && session.now() % 20 == 0) sync(session);
        }
        for (var entry : List.copyOf(CROUCHES.entrySet())) {
            ChangedEntity creature = entry.getKey();
            if (!creature.isAlive() || creature.isRemoved()) {
                creature.setShiftKeyDown(entry.getValue().shift);
                creature.setPose(entry.getValue().pose);
                CROUCHES.remove(creature);
            } else if (creature.level().getGameTime() >= entry.getValue().until) restorePose(creature);
        }
    }

    private static void advance(Session session) {
        long now = session.now();
        if (session.stage == HEAD_START && session.chaser == session.player && now < session.deadline
                && session.chaserStart != null) {
            Vec3 moved = session.player.position().subtract(session.chaserStart);
            Vec3 toward = session.runner.position().subtract(session.chaserStart);
            if (!session.water) { moved = moved.multiply(1, 0, 1); toward = toward.multiply(1, 0, 1); }
            if (moved.lengthSqr() > 0.45 * 0.45 && moved.dot(toward.normalize()) > 0.35) {
                falseStart(session);
                return;
            }
        }
        if (now >= session.deadline) {
            if (session.stage == HEAD_START) {
                session.stage = CHASING;
                session.deadline = now + ChangedSynergyConfig.COMMON.chasePlayDurationSeconds.get() * 20L;
                session.lastProgress[0] = session.lastProgress[1] = now;
                session.nextChaserRepath = now;
                sync(session);
                if (session.chaser instanceof ChangedEntity creature) {
                    if (touching(creature, session.runner)) caught(session);
                    else move(creature);
                }
            } else if (session.stage == CHASING) missed(session);
            else {
                end(session, session.stage == INVITED || session.stage == READY);
            }
            return;
        }
        if (session.stage == INVITED) return;
        if (session.stage == READY) {
            if (session.first.distanceToSqr(session.second) > 6.25 || !session.first.hasLineOfSight(session.second)) return;
            if (!session.chase) {
                session.runner = session.first;
                session.chaser = session.second;
                comfort(session, false);
            } else if (!session.rolesChosen) {
                boolean firstChases = CreaturePeerMemory.nextChases(session.first, session.second.getUUID());
                session.chaser = firstChases ? session.first : session.second;
                session.runner = firstChases ? session.second : session.first;
                session.rolesChosen = true;
                showRole(session.first, firstChases, now);
                session.nextStageAt = now + 14;
                session.signalStep = 1;
            } else if (now >= session.nextStageAt) {
                if (session.player == null && session.signalStep == 1) {
                    showRole((ChangedEntity)session.second, session.chaser == session.second, now);
                    session.signalStep = 2;
                    session.nextStageAt = now + 16;
                } else if (session.water || session.first.onGround() && session.second.onGround()) {
                    session.stage = HEAD_START;
                    session.deadline = now + HEAD_START_TICKS;
                    session.chaserStart = session.chaser.position();
                    session.lastProgress[0] = session.lastProgress[1] = now;
                    session.nextRepath = now;
                    if (session.runner instanceof ChangedEntity creature) move(creature);
                    NpcDialogue.emoteOnly(session.first, Emote.HEART);
                    if (session.player != null) NpcDialogue.trigger(session.first, session.player, Cue.CHASE_PLAY_START);
                    sync(session);
                }
            }
        } else if (session.stage == HEAD_START) {
            // The head-start deadline transitions instead of expiring the whole game.
        } else if (session.stage == CHASING && session.chaser != session.player && touching(session.chaser, session.runner)) {
            caught(session);
        } else if (session.stage == HUG) {
            if (!ChangedSynergyConfig.COMMON.chasePlayShortHugs.get()
                    || session.hold == null || session.hold.grabbedEntity != session.runner) end(session, false);
            else {
                session.hold.attackDown = session.hold.useDown = false;
                session.hold.suited = session.hold.grabbedHasControl = false;
                ((GrabAbilityTick)session.hold).changedSynergy$tickHoldIfNeeded();
            }
        } else if (session.stage == COMFORT && !session.patStarted
                && touching(session.runner, session.chaser)) {
            session.patStarted = true;
            if (session.runner instanceof ChangedEntity actor && PatAnimationService.canNpcPat(actor)) {
                PatAnimationService.startFixed(actor, session.chaser, 3);
            }
            NpcDialogue.emoteTarget(session.first, session.chaser, Emote.HEART);
            session.deadline = now + (session.runner instanceof ServerPlayer ? 80 : session.chase ? 40 : 65);
            session.nextStageAt = now + 32;
        } else if (session.stage == COMFORT && !session.chase && session.patStarted
                && !session.returnedPat && now >= session.nextStageAt && touching(session.runner, session.chaser)) {
            session.returnedPat = true;
            if (session.chaser instanceof ChangedEntity actor && PatAnimationService.canNpcPat(actor))
                PatAnimationService.startFixed(actor, session.runner, 3);
        }
    }

    private static void caught(Session session) {
        if (session.player != null) NpcDialogue.trigger(session.first, session.player, Cue.CHASE_PLAY_CAUGHT);
        NpcDialogue.emoteTarget(session.first, session.runner, Emote.HEART);
        remember(session);
        if (ChangedSynergyConfig.COMMON.chasePlayShortHugs.get()
                && session.chaser instanceof ChangedEntity carrier && canHug(carrier, session.runner)) {
            BondedSuitService.ensureGrabAbility(carrier);
            var ability = BondedSuitService.ability(carrier);
            session.holder = carrier;
            session.hold = ability;
            session.stage = HUG;
            session.deadline = session.now() + 40;
            ChangedAddonCompat.configureFriendlyGrab(ability, true);
            if (ability.grabEntity(session.runner)) {
                ability.attackDown = ability.useDown = false;
                ability.suited = ability.grabbedHasControl = false;
                ChangedSounds.broadcastSound(carrier, ChangedSounds.LATEX_GRAB_ENTITY, 0.7F, 1.12F);
                ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> carrier),
                        new FriendlySocialHugSyncPacket(carrier.getId(), session.runner.getId(), true, 40));
                Changed.PACKET_HANDLER.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> carrier),
                        new GrabEntityPacket(carrier, session.runner, GrabType.ARMS));
                sync(session);
                return;
            }
            ChangedAddonCompat.configureFriendlyGrab(ability, false);
            session.holder = null;
            session.hold = null;
        }
        end(session, false);
    }

    private static void falseStart(Session session) {
        session.stage = FALSE_START;
        session.deadline = session.now() + 60;
        for (ChangedEntity creature : session.npcs()) creature.getNavigation().stop();
        NpcDialogue.trigger(session.first, session.player, Cue.CHASE_PLAY_FALSE_START);
        sync(session);
    }

    private static boolean canHug(ChangedEntity carrier, LivingEntity runner) {
        return !CreatureSocialProfile.isGrabMechanicExcluded(carrier) && PatAnimationService.canNpcPat(carrier)
                && !CreatureSocialProfile.isJuvenile(carrier)
                && carrier.getEntityShape() != EntityShape.FERAL
                && EntityShape.getShapeOf(runner).orElse(EntityShape.ANTHRO) == EntityShape.ANTHRO
                && !(runner instanceof ChangedEntity peer && CreatureSocialProfile.isJuvenile(peer))
                && runner.getBbHeight() >= carrier.getBbHeight() * 0.6F
                && runner.getBbHeight() <= carrier.getBbHeight() * 1.5F
                && GrabEntityAbility.getGrabberSafe(runner).isEmpty()
                && (BondedSuitService.ability(carrier) == null || BondedSuitService.ability(carrier).grabbedEntity == null);
    }

    private static void missed(Session session) {
        remember(session);
        NpcDialogue.emoteTarget(session.first, session.chaser, Emote.NERVOUS);
        if (session.player != null) NpcDialogue.trigger(session.first, session.player, Cue.CHASE_PLAY_MISSED);
        comfort(session, true);
    }

    private static void comfort(Session session, boolean afterChase) {
        session.stage = COMFORT;
        session.deadline = session.now() + 160;
        session.nextRepath = 0;
        session.fleeTarget = null;
        for (ChangedEntity creature : session.npcs()) creature.getNavigation().stop();
        if (!afterChase) remember(session);
        if (session.runner instanceof ServerPlayer player) {
            NpcDialogue.trigger(session.first, player, Cue.CHASE_PLAY_COMFORT);
        }
        sync(session);
    }

    private static void remember(Session session) {
        if (session.remembered) return;
        session.remembered = true;
        if (session.chase && session.player != null) {
            net.parkabird.changedsynergy.advancement.SynergyAdvancements.grant(session.player,
                    net.parkabird.changedsynergy.advancement.SynergyAdvancements.CHASE_GAME);
            if (session.water) net.parkabird.changedsynergy.advancement.SynergyAdvancements.grant(session.player,
                    net.parkabird.changedsynergy.advancement.SynergyAdvancements.AQUATIC_CHASE);
        }
        for (ChangedEntity creature : session.npcs()) CreaturePeerMemory.remember(creature,
                session.other(creature).getUUID(), session.chase ? creature == session.chaser : null);
    }

    /** Movement is called only while the participant's goal owns MOVE/LOOK/JUMP. */
    static void move(ChangedEntity creature) {
        Session session = PARTICIPANTS.get(creature.getUUID());
        if (session == null || isHeld(creature)) return;
        LivingEntity other = session.other(creature);
        creature.getLookControl().setLookAt(other, 30, 30);
        long now = session.now();
        if (session.stage == HUG || session.stage == FALSE_START || CROUCHES.containsKey(creature)) { creature.getNavigation().stop(); return; }
        if (session.stage == READY || session.stage == INVITED || session.stage == COMFORT) {
            if (session.chase && !session.water && !creature.onGround()) {
                if (now % 10 == 0) navigate(session, creature, creature.position(), 0.27);
                return;
            }
            if (touching(creature, other) || session.stage == READY && creature.distanceToSqr(other) <= 6.25) {
                creature.getNavigation().stop();
            } else if (now % 10 == 0) navigate(session, creature, other.position(), 0.27);
            return;
        }
        if (session.stage == HEAD_START && creature == session.chaser) { creature.getNavigation().stop(); return; }
        if (creature == session.chaser) {
            if (now >= session.nextChaserRepath) {
                session.nextChaserRepath = now + 8;
                navigate(session, creature, other.position(), 0.4);
            }
        } else if (creature == session.runner && now >= session.nextRepath) {
            session.nextRepath = now + 20;
            session.fleeTarget = fleePoint(session, creature, other);
            if (session.fleeTarget == null) pathFailed(session);
            else navigate(session, creature, session.fleeTarget, 0.4);
        }
    }

    private static void navigate(Session session, ChangedEntity creature, Vec3 target, double speed) {
        // Long chases use local waypoints within native navigation's search range.
        Vec3 offset = target.subtract(creature.position());
        double distance = session.water ? offset.lengthSqr() : offset.horizontalDistanceSqr();
        if (session.chase && distance > 12 * 12)
            target = creature.position().add(offset.scale(12 / Math.sqrt(distance)));
        Vec3 destination = !session.chase ? target : session.water
                ? waterPoint(creature, target, creature == session.chaser)
                : groundPoint(creature, target, creature == session.chaser);
        if (destination == null || !creature.getNavigation().moveTo(destination.x, destination.y, destination.z, speed)) {
            pathFailed(session);
        } else {
            int index = creature == session.first ? 0 : 1;
            if (creature.position().distanceToSqr(session.lastPositions[index]) > 0.04) {
                session.pathFailures = 0;
                session.lastPositions[index] = creature.position();
                session.lastProgress[index] = session.now();
            } else if (session.now() - session.lastProgress[index] > 100) end(session, true);
        }
    }

    private static void pathFailed(Session session) {
        if (session.pathFailures++ == 0) session.pathFailureSince = session.now();
        if (session.pathFailures >= 6 && session.now() - session.pathFailureSince >= 100) end(session, true);
    }

    private static Vec3 fleePoint(Session session, ChangedEntity creature, LivingEntity other) {
        double away = Math.atan2(creature.getZ() - other.getZ(), creature.getX() - other.getX());
        for (int attempt = 0; attempt < 6; attempt++) {
            double angle = away + (creature.getRandom().nextDouble() - 0.5) * Math.PI;
            double distance = 6 + creature.getRandom().nextDouble() * 4;
            double vertical = session.water ? Math.max(-1, Math.min(1, creature.getY() - other.getY()))
                    + creature.getRandom().nextDouble() * 2 - 1 : 0;
            Vec3 target = creature.position().add(Math.cos(angle) * distance, vertical, Math.sin(angle) * distance);
            Vec3 destination = session.water ? waterPoint(creature, target, false) : groundPoint(creature, target);
            if (destination != null) return destination;
        }
        return null;
    }

    private static Vec3 groundPoint(ChangedEntity creature, Vec3 target) {
        return groundPoint(creature, target, false);
    }

    private static Vec3 waterPoint(ChangedEntity creature, Vec3 target, boolean approachingPlaymate) {
        BlockPos start = BlockPos.containing(target);
        for (int y : new int[]{0, 1, -1, 2, -2, 3, -3}) {
            BlockPos feet = start.offset(0, y, 0);
            Vec3 position = Vec3.atBottomCenterOf(feet);
            // Native aquatic navigation requires standing-height water even while the rendered body swims.
            var clearance = creature.getDimensions(Pose.STANDING).makeBoundingBox(position).deflate(0.02);
            if (!BlockPos.betweenClosedStream(clearance).allMatch(pos -> creature.level().hasChunkAt(pos)
                    && creature.level().getFluidState(pos).is(FluidTags.WATER))) continue;
            var body = creature.getBoundingBox().move(position.subtract(creature.position()));
            if (approachingPlaymate ? !creature.level().getBlockCollisions(creature, body).iterator().hasNext()
                    : creature.level().noCollision(creature, body)) return position;
        }
        return null;
    }

    private static Vec3 groundPoint(ChangedEntity creature, Vec3 target, boolean approachingPlaymate) {
        BlockPos start = BlockPos.containing(target);
        for (int y = 2; y >= -12; y--) {
            BlockPos feet = start.offset(0, y, 0);
            if (!creature.level().hasChunkAt(feet)) continue;
            var body = creature.getBoundingBox().move(feet.getX() + 0.5 - creature.getX(),
                    feet.getY() - creature.getY(), feet.getZ() + 0.5 - creature.getZ());
            if (creature.level().getFluidState(feet).isEmpty()
                    && creature.level().getBlockState(feet.below()).isSolidRender(creature.level(), feet.below())
                    // The runner occupies the destination; native movement still resolves entity collision.
                    && (approachingPlaymate ? !creature.level().getBlockCollisions(creature, body).iterator().hasNext()
                            : creature.level().noCollision(creature, body))) {
                return Vec3.atBottomCenterOf(feet);
            }
        }
        return null;
    }

    private static boolean touching(LivingEntity first, LivingEntity second) {
        return first.getBoundingBox().inflate(0.4, 0.2, 0.4).intersects(second.getBoundingBox())
                && first.hasLineOfSight(second);
    }

    private static boolean peaceful(ChangedEntity first, ChangedEntity second) {
        return !LatexCreatureCombatRules.areRivals(first, second)
                && !first.isAggressive() && !second.isAggressive();
    }

    private static boolean canChaseTogether(ChangedEntity first, LivingEntity second) {
        boolean water = first.isInWaterOrBubble() || second.isInWaterOrBubble();
        return canChaseIn(first, water) && canChaseIn(second, water);
    }

    private static boolean canChaseIn(LivingEntity participant, boolean water) {
        if (participant.isInLava() || participant.isInWaterOrBubble() != water) return false;
        return !water || ((!participant.isEyeInFluid(FluidTags.WATER) || participant.getAirSupply() > 60
                || participant.canBreatheUnderwater())
                && (!(participant instanceof ChangedEntity creature) || creature instanceof AquaticEntity
                        || creature.getNavigation() instanceof WaterBoundPathNavigation));
    }

    /** Native surface/depth adjustment is ordinary idle movement, not work or rescue. */
    private static boolean aquaticIdle(Goal goal) {
        String name = goal.getClass().getName();
        return name.equals("net.ltxprogrammer.changed.entity.beast.AbstractAquaticEntity$SinkFromSurfaceGoal")
                || name.equals("net.ltxprogrammer.changed.entity.beast.AbstractAquaticEntity$RiseToSurfaceGoal");
    }

    private static boolean available(ChangedEntity creature, Session own) {
        Session current = PARTICIPANTS.get(creature.getUUID());
        if (current != null && current != own) return false;
        if (!(creature.level() instanceof ServerLevel) || !creature.isAlive() || creature.isRemoved()
                || creature.isNoAi() || creature.isSleeping() || creature.isPassenger() || creature.isVehicle()
                || creature.isLeashed() || creature.getUnderlyingPlayer() != null
                || !LatexSocialMemory.isSocialLatex(creature) || !CreatureSocialProfile.allowsSynergySystems(creature)
                || CreatureSocialProfile.isAggressive(creature) || ChangedExtrasCompat.ownsWildAi(creature)
                || creature.getTarget() != null || recentlyHurt(creature) || SocialAudienceGoal.isActive(creature)
                || CreatureSettlementService.hasCargo(creature) || PlayerOutpostGoal.hasCargo(creature)
                || PlayerOutpostWorkGoal.hasCargo(creature) || PlayerOutpostService.isWorking(creature)
                || campDuty(creature) || ProvisionerTradeService.isTrading(creature)
                || creature.getPersistentData().hasUUID("ChangedSynergyRestOwner")
                || HypnosisQteService.getActiveVictim(creature) != null) return false;
        boolean gameHold = own != null && own.holder != null && (own.holder == creature || own.runner == creature);
        if (!gameHold && (CreatureGrabService.isGrabberBusy(creature)
                || GrabEntityAbility.getGrabberSafe(creature).isPresent()
                || BondedSuitService.ability(creature) != null && BondedSuitService.ability(creature).grabbedEntity != null)) return false;
        if (own == null && creature.getPose() != Pose.STANDING
                && !(creature.getPose() == Pose.SWIMMING && canChaseIn(creature, true))) return false;
        if (creature.goalSelector.getRunningGoals().anyMatch(wrapped -> {
            Goal goal = wrapped.getGoal();
            if (!goal.getFlags().contains(Goal.Flag.MOVE) || goal instanceof CreaturePeerInteractionGoal
                    || goal instanceof RandomStrollGoal || goal instanceof BondedFollowGoal
                    || goal instanceof SocialFollowGoal || goal instanceof FollowOwnerGoal
                    || aquaticIdle(goal)
                    || goal instanceof SocialAudienceGoal && !SocialAudienceGoal.isActive(creature)) return false;
            if (goal instanceof CreatureRoutineGoal) {
                var state = CreatureLifeMemory.snapshot(creature).routine();
                return state != CreatureLifeMemory.RoutineState.IDLE && state != CreatureLifeMemory.RoutineState.PLAYING;
            }
            return true;
        })) return false;
        ServerPlayer owner = followedPlayer(creature);
        if (owner == null || own != null && own.player == owner) return true;
        LivingEntity body = TrueTransfurCompat.interactionBody(owner);
        return owner.isAlive() && body.isAlive() && body.level() == creature.level()
                && (own != null || body.getDeltaMovement().horizontalDistanceSqr() < 0.005)
                && body.distanceToSqr(creature) < (own == null ? 64 : 1024);
    }

    private static ServerPlayer followedPlayer(ChangedEntity creature) {
        ServerPlayer owner = LatexSocialMemory.isFollowingOwner(creature) ? LatexSocialMemory.getPetOwner(creature) : null;
        if (owner == null) owner = CreaturePersonality.socialPartner(creature);
        if (owner == null && creature instanceof TamableLatexEntity pet && pet.isTame() && pet.isFollowingOwner()
                && pet.getOwner() instanceof ServerPlayer player) owner = player;
        return owner;
    }

    private static boolean campDuty(ChangedEntity creature) {
        if (!PlayerOutpostService.assigned(creature) || !(creature.level() instanceof ServerLevel level)) return false;
        var camp = PlayerOutpostData.get(level.getServer()).byMember(creature.getUUID()).orElse(null);
        var member = camp == null ? null : camp.members.get(creature.getUUID());
        if (member == null) return false;
        return switch (member.role) {
            case HOLD, RETURN, RETREAT -> true;
            case SUPPLY -> camp.active(level);
            case CREW -> member.crewJob != PlayerOutpostData.CrewJob.COMBAT;
            default -> false;
        };
    }

    private static boolean recentlyHurt(LivingEntity entity) {
        return entity.getLastHurtByMob() != null && entity.tickCount - entity.getLastHurtByMobTimestamp() < 100
                || entity.getLastHurtMob() != null && entity.tickCount - entity.getLastHurtMobTimestamp() < 100;
    }

    private static boolean playerAvailable(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator() && !player.isPassenger()
                && !player.isSleeping() && !player.isFallFlying() && !player.isInLava()
                && !recentlyHurt(player) && !TakeoverService.active(player) && !TrueTransfurCompat.isInstinctShell(player)
                && GrabEntityAbility.getGrabberSafe(player).isEmpty();
    }

    private static boolean valid(Session session) {
        if (!SESSIONS.contains(session) || session.first.level() != session.second.level()
                || session.second.isRemoved() || !session.second.isAlive()
                || session.first.distanceToSqr(session.second) > (session.chase ? 128 * 128 : 32 * 32)
                || !session.chase && session.first.distanceToSqr(session.origin) > 32 * 32
                || session.chase && !ChangedSynergyConfig.COMMON.chasePlayGames.get()
                || session.player == null && (!ChangedSynergyConfig.COMMON.ambientPeerInteractions.get()
                        || !CreatureLifeMemory.enabled(session.first)
                        || !SynergyPerformanceTracker.featureEnabled(Feature.SOCIAL))) return false;
        for (ChangedEntity creature : session.npcs()) {
            if (!available(creature, session)
                    || ((ServerLevel)creature.level()).getEntity(creature.getUUID()) != creature
                    || !creature.level().hasChunkAt(creature.blockPosition())
                    || session.chase && !canChaseIn(creature, session.water)) return false;
        }
        for (var owner : session.owners.entrySet()) {
            LivingEntity body = TrueTransfurCompat.interactionBody(owner.getKey());
            if (!owner.getKey().isAlive() || owner.getKey().isRemoved()
                    || !body.isAlive() || body.level() != session.first.level()
                    || body.position().distanceToSqr(owner.getValue()) > 2.25) return false;
        }
        if (session.player != null) {
            ServerPlayer player = session.player;
            if (session.chase && !canChaseIn(player, session.water)) return false;
            if (session.stage == HUG) {
                if (recentlyHurt(player) || !player.isAlive() || player.isSpectator() || TakeoverService.active(player)) return false;
            } else if (!playerAvailable(player)) return false;
            if (ProcessTransfur.getPlayerTransfurVariantSafe(player).map(form -> form.getParent()).orElse(null) != session.playerForm
                    || !(LatexSocialMemory.isBonded(session.first, player)
                            || CreaturePersonality.hasTrustedRelationship(session.first, player))) return false;
        } else if (!peaceful(session.first, (ChangedEntity)session.second)) return false;
        return true;
    }

    private static void showRole(ChangedEntity creature, boolean chases, long now) {
        creature.getNavigation().stop();
        if (chases) {
            CROUCHES.put(creature, new Crouch(creature.getPose(), creature.isShiftKeyDown(), now + 12));
            creature.setShiftKeyDown(true);
            if (!creature.isInWaterOrBubble()) creature.setPose(Pose.CROUCHING);
        } else if (creature.onGround() || creature.isInWaterOrBubble()) creature.getJumpControl().jump();
        NpcDialogue.emoteOnly(creature, Emote.IDEA);
    }

    private static void restorePose(ChangedEntity creature) {
        Crouch crouch = CROUCHES.get(creature);
        if (crouch == null) return;
        if (creature.getPose() == Pose.CROUCHING
                && !creature.level().noCollision(creature, creature.getDimensions(crouch.pose).makeBoundingBox(creature.position()))) return;
        CROUCHES.remove(creature);
        if (creature.isShiftKeyDown()) creature.setShiftKeyDown(crouch.shift);
        if (creature.getPose() == Pose.CROUCHING) creature.setPose(crouch.pose);
    }

    private static void sync(Session session) {
        if (session.player != null && session.player.connection != null) ChangedSynergyNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> session.player), new ChasePlayStatePacket(session.id, session.stage,
                        (int)Math.max(0, session.deadline - session.now()), session.chaser == session.player, session.first.getId()));
    }

    private static void end(Session session, boolean interrupted) {
        if (!SESSIONS.contains(session)) return;
        boolean closingLine = interrupted && session.stage != HUG && session.stage != COMFORT && session.stage != FALSE_START;
        boolean monsterWarning = interrupted && session.chase && session.npcs().stream().anyMatch(CreaturePeerInteractionService::monsterCombat);
        // Keep the safe-hold identity alive through every native release callback.
        if (session.hold != null) {
            if (session.hold.grabbedEntity == session.runner) session.hold.releaseEntity(false);
            ChangedAddonCompat.configureFriendlyGrab(session.hold, false);
            if (session.holder != null) ChangedSynergyNetwork.CHANNEL.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> session.holder),
                    new FriendlySocialHugSyncPacket(session.holder.getId(), session.runner.getId(), false, 0));
            if (session.holder != null) Changed.PACKET_HANDLER.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> session.holder),
                    new GrabEntityPacket(session.holder, session.runner, GrabType.RELEASE));
        }
        SESSIONS.remove(session);
        PARTICIPANTS.remove(session.first.getUUID(), session);
        PARTICIPANTS.remove(session.second.getUUID(), session);
        for (ChangedEntity creature : session.npcs()) {
            creature.getNavigation().stop();
            PatAnimationService.stop(creature);
            restorePose(creature);
            CreaturePeerMemory.data(creature).putLong(NEXT_SOCIAL, session.now() + 900);
        }
        if (session.player != null) {
            session.stage = CLEAR;
            sync(session);
            if ((closingLine || monsterWarning) && session.player.isAlive() && session.player.connection != null && session.first.isAlive())
                NpcDialogue.trigger(session.first, session.player, monsterWarning ? Cue.CHASE_PLAY_MONSTER : Cue.CHASE_PLAY_ENDED);
        }
    }

    /** Classifies an existing combat interruption without adding a nearby-monster scan. */
    private static boolean monsterCombat(ChangedEntity creature) {
        return monster(creature.getTarget())
                || monster(creature.getLastHurtByMob()) && creature.tickCount - creature.getLastHurtByMobTimestamp() < 100
                || monster(creature.getLastHurtMob()) && creature.tickCount - creature.getLastHurtMobTimestamp() < 100;
    }

    private static boolean monster(LivingEntity entity) {
        return entity != null && entity.isAlive() && entity instanceof net.minecraft.world.entity.monster.Enemy
                && !(entity instanceof ChangedEntity);
    }

    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof LivingEntity living) cancel(living);
    }

    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session session = PARTICIPANTS.get(event.getTarget().getUUID());
        if (session == null || session.holder == null || session.hold == null
                || session.hold.grabbedEntity != session.runner) return;
        ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new FriendlySocialHugSyncPacket(
                session.holder.getId(), session.runner.getId(), true, (int)Math.max(1, session.deadline - session.now())));
        Changed.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player),
                new GrabEntityPacket(session.holder, session.runner, GrabType.ARMS));
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        for (Session session : List.copyOf(SESSIONS)) end(session, true);
        for (ChangedEntity creature : List.copyOf(CROUCHES.keySet())) restorePose(creature);
        CROUCHES.clear();
        PARTICIPANTS.clear();
        nextId = 0;
    }

    private record Crouch(Pose pose, boolean shift, long until) {}

    private static final class Session {
        final int id = ++nextId;
        final ChangedEntity first;
        final LivingEntity second;
        final ServerPlayer player;
        final Object playerForm;
        final boolean chase;
        final boolean water;
        final Vec3 origin;
        final Map<ServerPlayer, Vec3> owners = new HashMap<>();
        final Vec3[] lastPositions;
        final long[] lastProgress;
        LivingEntity chaser, runner;
        ChangedEntity holder;
        GrabEntityAbilityInstance hold;
        Vec3 fleeTarget, chaserStart;
        int stage = READY, signalStep, pathFailures, exitPresses;
        long deadline, nextStageAt, nextRepath, nextChaserRepath, pathFailureSince, exitSequenceAt;
        boolean rolesChosen, patStarted, returnedPat, remembered;

        Session(ChangedEntity first, LivingEntity second, boolean chase) {
            this.first = first;
            this.second = second;
            this.player = second instanceof ServerPlayer p ? p : null;
            this.playerForm = player == null ? null : ProcessTransfur.getPlayerTransfurVariantSafe(player)
                    .map(form -> form.getParent()).orElse(null);
            this.chase = chase;
            this.water = first.isInWaterOrBubble() && second.isInWaterOrBubble();
            this.origin = first.position();
            this.deadline = now() + (chase ? 200 : 160);
            this.lastPositions = new Vec3[]{first.position(), second.position()};
            this.lastProgress = new long[]{now(), now()};
        }
        long now() { return first.level().getGameTime(); }
        LivingEntity other(LivingEntity entity) { return entity == first ? second : first; }
        List<ChangedEntity> npcs() { return second instanceof ChangedEntity peer ? List.of(first, peer) : List.of(first); }
    }
}
