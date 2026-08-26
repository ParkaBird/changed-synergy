package net.parkabird.changedsynergy.event;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.PlayerDataExtension;
import net.ltxprogrammer.changed.entity.PlayerMover;
import net.ltxprogrammer.changed.entity.TransfurCause;
import net.ltxprogrammer.changed.init.ChangedDamageSources;
import net.ltxprogrammer.changed.init.ChangedTags;
import net.ltxprogrammer.changed.network.packet.SyncMoversPacket;
import net.ltxprogrammer.changed.network.packet.SyncTransfurPacket;
import net.ltxprogrammer.changed.network.packet.SyncTransfurProgressPacket;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.ltxprogrammer.changed.process.TransfurEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.FactionReputation;
import net.parkabird.changedsynergy.ai.FactionHostilityGrace;
import net.parkabird.changedsynergy.ai.HunterFaction;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.ai.LightFactionGroup;
import net.parkabird.changedsynergy.dialogue.LatexTerritory;

/** Reputation loss, persistence and source-based faction hazard protection. */
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FactionReputationEvents {
    private static final TagKey<Block> WHITE_FACTION_HAZARDS = blockTag(
            "white_territory_blocks");
    private static final TagKey<Block> DARK_FACTION_HAZARDS = blockTag(
            "dark_territory_blocks");
    private static final String LAST_REPUTATION_ATTACKER =
            "ChangedSynergyLastReputationAttacker";
    private static final String LAST_REPUTATION_ATTACK_TICK =
            "ChangedSynergyLastReputationAttackTick";
    private static final Map<UUID, Long> PENDING_AUTHORITATIVE_SYNC =
            new HashMap<>();
    private static final Map<UUID, Long> NEXT_AUTHORITATIVE_SYNC =
            new HashMap<>();
    private static final Map<UUID, PendingLightGroupAssignment>
            PENDING_LIGHT_GROUP_ASSIGNMENTS = new HashMap<>();
    private static final int LIGHT_GROUP_ASSIGNMENT_MAX_ATTEMPTS = 200;
    private FactionReputationEvents() {
    }

    /** Any deliberate hit immediately ends that faction's post-transfur truce. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerReopensHostilities(LivingAttackEvent event) {
        if (!event.getEntity().level().isClientSide
                && event.getEntity() instanceof ChangedEntity creature
                && event.getSource().getEntity() instanceof ServerPlayer player) {
            FactionHostilityGrace.clear(creature, player);
        }
    }

    @SubscribeEvent
    public static void onCreatureJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()
                && event.getEntity() instanceof ChangedEntity creature
                && HunterFaction.of(creature) == HunterFaction.LIGHT
                && !LightFactionGroup.isAssigned(creature)) {
            PENDING_LIGHT_GROUP_ASSIGNMENTS.put(
                    creature.getUUID(),
                    new PendingLightGroupAssignment(
                            creature.level().dimension(), 0));
        }
    }

    /**
     * Resolves regional Light identity only after entity and chunk loading has
     * completed. getChunkNow is deliberately used so this bookkeeping can
     * never force generation or wait on the chunk task currently calling us.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || PENDING_LIGHT_GROUP_ASSIGNMENTS.isEmpty()) {
            return;
        }
        for (var entry : List.copyOf(
                PENDING_LIGHT_GROUP_ASSIGNMENTS.entrySet())) {
            UUID creatureId = entry.getKey();
            PendingLightGroupAssignment pending = entry.getValue();
            ServerLevel level = event.getServer().getLevel(
                    pending.dimension());
            Entity entity = level == null ? null : level.getEntity(creatureId);
            if (entity instanceof ChangedEntity creature
                    && creature.isAlive()
                    && LightFactionGroup.canResolveAt(
                            level, creature.blockPosition())) {
                LightFactionGroup.of(creature);
                PENDING_LIGHT_GROUP_ASSIGNMENTS.remove(creatureId);
                continue;
            }
            int attempts = pending.attempts() + 1;
            if (attempts >= LIGHT_GROUP_ASSIGNMENT_MAX_ATTEMPTS) {
                PENDING_LIGHT_GROUP_ASSIGNMENTS.remove(creatureId);
            } else {
                PENDING_LIGHT_GROUP_ASSIGNMENTS.put(
                        creatureId,
                        new PendingLightGroupAssignment(
                                pending.dimension(), attempts));
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCreatureKilled(LivingDeathEvent event) {
        if (event.getEntity() instanceof ChangedEntity creature
                && event.getSource().getEntity() instanceof ServerPlayer player
                && LatexSocialMemory.isSocialLatex(creature)) {
            FactionReputation.adjust(creature, player, -15);
            return;
        }
        if (event.getEntity() instanceof Mob defeated
                && !(defeated instanceof ChangedEntity)
                && event.getSource().getEntity() instanceof ServerPlayer player
                && defeated.getTarget() instanceof ChangedEntity defended
                && LatexSocialMemory.isSocialLatex(defended)
                && defended.isAlive()) {
            FactionReputation.adjust(defended, player, 2);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCreatureHurt(LivingHurtEvent event) {
        if (event.isCanceled()
                || event.getAmount() <= 0.0F
                || !(event.getEntity() instanceof ChangedEntity creature)
                || !(event.getSource().getEntity() instanceof ServerPlayer player)
                || !LatexSocialMemory.isSocialLatex(creature)) {
            return;
        }
        long now = creature.level().getGameTime();
        var memory = creature.getPersistentData();
        if (memory.hasUUID(LAST_REPUTATION_ATTACKER)
                && player.getUUID().equals(
                        memory.getUUID(LAST_REPUTATION_ATTACKER))
                && now - memory.getLong(LAST_REPUTATION_ATTACK_TICK) < 40L) {
            return;
        }
        memory.putUUID(LAST_REPUTATION_ATTACKER, player.getUUID());
        memory.putLong(LAST_REPUTATION_ATTACK_TICK, now);
        FactionReputation.adjust(creature, player, -2);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer original
                && event.getEntity() instanceof ServerPlayer clone) {
            FactionReputation.copyPlayerData(original, clone);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FactionReputation.restoreAdvancements(player);
            // Also repairs a client-only form left by an older build.
            queueAuthoritativeSync(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING_AUTHORITATIVE_SYNC.remove(event.getEntity().getUUID());
        NEXT_AUTHORITATIVE_SYNC.remove(event.getEntity().getUUID());
    }

    /**
     * Reasserts the server's complete Changed state after an environmental
     * decision is rejected. This repairs clients that predicted a form or a
     * white-latex mover before learning that reputation protection won.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (clearProtectedWhiteLatexMover(player)) {
            queueAuthoritativeSync(player);
        }
        Long due = PENDING_AUTHORITATIVE_SYNC.get(player.getUUID());
        if (due == null || player.level().getGameTime() < due) {
            return;
        }
        PENDING_AUTHORITATIVE_SYNC.remove(player.getUUID());
        syncAuthoritativeState(player);
        NEXT_AUTHORITATIVE_SYNC.put(
                player.getUUID(), player.level().getGameTime() + 20L);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLatexEnvironmentalHazard(
            TransfurEvents.LatexAssimilationDecisionEvent event) {
        if (event.getEntity() instanceof Player player
                && event.getTransfurVariant() != null
                && isEnvironmental(event.getTransfurCause())
                && shouldCancelEnvironmentalHazard(
                        player,
                        event.getTransfurCause(),
                        event.getTransfurVariant().getEntityType())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onNonLatexEnvironmentalHazard(
            TransfurEvents.NonLatexAssimilationDecisionEvent event) {
        if (event.getEntity() instanceof Player player
                && event.getTransfurVariant() != null
                && isEnvironmental(event.getTransfurCause())
                && shouldCancelEnvironmentalHazard(
                        player,
                        event.getTransfurCause(),
                        event.getTransfurVariant().getEntityType())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onImmediateEnvironmentalHazard(
            TransfurEvents.ImmediateTransfurDecisionEvent event) {
        if (event.getEntity() instanceof Player player
                && event.getTransfurVariant() != null
                && isEnvironmental(event.getTransfurCause())
                && shouldCancelEnvironmentalHazard(
                        player,
                        event.getTransfurCause(),
                        event.getTransfurVariant().getEntityType())) {
            event.setCanceled(true);
        }
    }

    /** Covers real health damage from identifiable faction latex hazards. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTerritoryLatexDamage(LivingAttackEvent event) {
        if (event.isCanceled()
                || !(event.getEntity() instanceof ServerPlayer player)
                || !isProtectedTerritoryDamage(player, event.getSource())) {
            return;
        }
        event.setCanceled(true);
    }

    /** Some integrations enter at the hurt stage and never post LivingAttackEvent. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTerritoryLatexHurt(LivingHurtEvent event) {
        if (event.isCanceled()
                || !(event.getEntity() instanceof ServerPlayer player)
                || !isProtectedTerritoryDamage(player, event.getSource())) {
            return;
        }
        event.setCanceled(true);
    }

    /** Last-stage protection for sources that skip or recreate earlier events. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTerritoryLatexFinalDamage(LivingDamageEvent event) {
        if (event.getAmount() > 0.0F
                && event.getEntity() instanceof ServerPlayer player
                && isProtectedTerritoryDamage(player, event.getSource())) {
            event.setAmount(0.0F);
        }
    }

    private static boolean isProtectedTerritoryDamage(
            ServerPlayer player,
            DamageSource source) {
        if (source.getEntity() != null
                || !(player.level() instanceof ServerLevel)) {
            return false;
        }
        HunterFaction hazardFaction;
        if (source.is(ChangedDamageSources.WHITE_LATEX.key())) {
            hazardFaction = HunterFaction.WHITE;
        } else if (source.is(ChangedDamageSources.LATEX_FLUID.key())) {
            hazardFaction = resolveNearbyHazardFaction(player);
        } else {
            return false;
        }
        return protectedFrom(player, hazardFaction);
    }

    private static boolean shouldCancelEnvironmentalHazard(
            Player player,
            TransfurCause cause,
            EntityType<?> hazardType) {
        // Changed is server-authoritative. Client-side progress decisions are
        // prediction only; allowing them to create a local form is the source
        // of the UI/model-only "fake transfur" state.
        if (player.level().isClientSide) {
            return true;
        }
        if (!(player instanceof ServerPlayer serverPlayer)
                || !protectedFromEnvironmentalHazard(
                        serverPlayer, cause, hazardType)) {
            return false;
        }
        queueAuthoritativeSync(serverPlayer);
        return true;
    }

    private static boolean protectedFromEnvironmentalHazard(
            ServerPlayer player,
            TransfurCause cause,
            EntityType<?> hazardType) {
        HunterFaction faction = cause == TransfurCause.WHITE_LATEX
                ? HunterFaction.WHITE
                : resolveNearbyHazardFaction(player);
        return protectedFrom(
                player,
                faction == null ? HunterFaction.of(hazardType) : faction);
    }

    private static boolean protectedFrom(
            ServerPlayer player,
            HunterFaction faction) {
        return faction != null
                && FactionReputation.isRespectedAt(faction, player);
    }

    /** Uses the actual collision volume, then local territory as a fallback. */
    private static HunterFaction resolveNearbyHazardFaction(
            ServerPlayer player) {
        HunterFaction nearby = nearestHazardFaction(player);
        if (nearby != null) {
            return nearby;
        }
        return player.level() instanceof ServerLevel level
                ? LatexTerritory.dominantFactionAt(
                        level, player.blockPosition())
                : null;
    }

    private static HunterFaction nearestHazardFaction(ServerPlayer player) {
        AABB scan = player.getBoundingBox().inflate(0.80D);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        HunterFaction nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int x = (int)Math.floor(scan.minX);
                x <= (int)Math.floor(scan.maxX); x++) {
            for (int y = (int)Math.floor(scan.minY);
                    y <= (int)Math.floor(scan.maxY); y++) {
                for (int z = (int)Math.floor(scan.minZ);
                        z <= (int)Math.floor(scan.maxZ); z++) {
                    cursor.set(x, y, z);
                    var state = player.level().getBlockState(cursor);
                    HunterFaction candidate = state.is(WHITE_FACTION_HAZARDS)
                            ? HunterFaction.WHITE
                            : state.is(DARK_FACTION_HAZARDS)
                                    ? HunterFaction.DARK
                                    : null;
                    if (candidate == null) {
                        continue;
                    }
                    double distance = player.distanceToSqr(
                            x + 0.5D, y + 0.5D, z + 0.5D);
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearest = candidate;
                    }
                }
            }
        }
        return nearest;
    }

    /** Called at WhiteLatexTransportInterface's entry point by a mixin. */
    public static boolean shouldBlockWhiteLatexEntry(
            ServerPlayer player,
            BlockPos sourcePosition) {
        if (!protectedFromAt(player, HunterFaction.WHITE, sourcePosition)) {
            return false;
        }
        if (hasWhiteLatexSwimmingForm(player)) {
            return false;
        }
        if (player instanceof PlayerDataExtension extension
                && extension.isPlayerMover(PlayerMover.LATEX_SWIM.get())) {
            extension.setPlayerMover(null);
        }
        queueAuthoritativeSync(player);
        return true;
    }

    private static boolean clearProtectedWhiteLatexMover(
            ServerPlayer player) {
        if (!(player instanceof PlayerDataExtension extension)
                || !extension.isPlayerMover(PlayerMover.LATEX_SWIM.get())
                || !protectedFrom(player, HunterFaction.WHITE)
                || hasWhiteLatexSwimmingForm(player)) {
            return false;
        }
        extension.setPlayerMover(null);
        return true;
    }

    private static boolean hasWhiteLatexSwimmingForm(ServerPlayer player) {
        var current = ProcessTransfur.getPlayerTransfurVariant(player);
        return current != null
                && current.getParent().getEntityType().is(
                        ChangedTags.EntityTypes.WHITE_LATEX_SWIMMING);
    }

    private static boolean protectedFromAt(
            ServerPlayer player,
            HunterFaction faction,
            BlockPos position) {
        return faction != null
                && FactionReputation.scoreAt(
                        faction, player, player.level(), position)
                        >= FactionReputation.RESPECTED_THRESHOLD;
    }

    private static void queueAuthoritativeSync(ServerPlayer player) {
        long now = player.level().getGameTime();
        if (PENDING_AUTHORITATIVE_SYNC.containsKey(player.getUUID())
                || NEXT_AUTHORITATIVE_SYNC.getOrDefault(
                        player.getUUID(), 0L) > now) {
            return;
        }
        long due = now + 1L;
        PENDING_AUTHORITATIVE_SYNC.merge(
                player.getUUID(), due, Math::min);
    }

    private static void syncAuthoritativeState(ServerPlayer player) {
        var distributor = PacketDistributor.TRACKING_ENTITY_AND_SELF
                .with(() -> player);
        Changed.PACKET_HANDLER.send(
                distributor,
                SyncTransfurPacket.Builder.of(player));
        Changed.PACKET_HANDLER.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new SyncTransfurProgressPacket(
                        player.getId(),
                        ProcessTransfur.getPlayerTransfurProgress(player)));
        Changed.PACKET_HANDLER.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                SyncMoversPacket.Builder.of(player, false));
    }

    private static TagKey<Block> blockTag(String path) {
        return TagKey.create(
                Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(
                        ChangedSynergyMod.MOD_ID, path));
    }

    private static boolean isEnvironmental(TransfurCause cause) {
        return switch (cause) {
            case FOOT_HAZARD_LEFT, FOOT_HAZARD_RIGHT,
                    WALL_HAZARD_LEFT, WALL_HAZARD_RIGHT,
                    CEILING_HAZARD, WAIST_HAZARD,
                    FLOOR_HAZARD, FACE_HAZARD,
                    CRYSTAL, LATEX_PUDDLE,
                    LATEX_SYRINGE_FLOOR, LATEX_WALL_SPLOTCH,
                    LATEX_CONTAINER_FELL, WHITE_LATEX -> true;
            default -> false;
        };
    }

    private record PendingLightGroupAssignment(
            ResourceKey<Level> dimension,
            int attempts) {
    }
}
