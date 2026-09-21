package net.parkabird.changedsynergy.ai;

import java.util.EnumSet;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.event.LatexSocialEvents;

/** Bounded, persistent warrants; no chunk generation and no cloned inventories. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class FactionPursuitService {
    private static final String WARRANTS = "SynergyPursuitWarrants";
    private static final String SQUAD = "SynergyPursuitSquad";
    private static final long COOLDOWN = 12000L;
    private static final long LIFETIME = 3600L;
    private static final double PURSUIT_SPEED = 0.72D;
    private FactionPursuitService() {}

    public static boolean isPursuer(ChangedEntity mob) { return mob.getPersistentData().contains(SQUAD); }

    private static CompoundTag persisted(ServerPlayer player) {
        var root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static boolean eligible(ChangedEntity mob) {
        return LatexSocialMemory.isSocialLatex(mob) && CreatureSocialProfile.allowsSynergySystems(mob)
                && !CreatureSocialProfile.isJuvenile(mob) && !CreatureSocialProfile.isPermanentlyExcluded(mob)
                && mob.getMaxHealth() <= 80.0F && !mob.isNoAi();
    }

    public static void record(ChangedEntity source, ServerPlayer player) {
        if (!eligible(source) || isPursuer(source)) return;
        var root = persisted(player);
        if (!root.contains(WARRANTS)) root.put(WARRANTS, new CompoundTag());
        String group = FactionReputation.groupId(source);
        var warrants = root.getCompound(WARRANTS);
        if (warrants.contains(group)) return;
        var entry = new CompoundTag();
        entry.putString("Type", ForgeRegistries.ENTITY_TYPES.getKey(source.getType()).toString());
        entry.putLong("Next", player.level().getGameTime() + 1200L);
        warrants.put(group, entry);
    }

    private static boolean available(ServerPlayer player) {
        return ChangedSynergyConfig.COMMON.factionPursuit.get() && player.isAlive()
                && !player.isCreative() && !player.isSpectator() && !player.isSleeping()
                && !player.isPassenger() && !player.isChangingDimension()
                && player.level().getDifficulty() != Difficulty.PEACEFUL
                && player.level().getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 100 != 0) return;
        var root = persisted(player);
        var warrants = root.getCompound(WARRANTS);
        if (!available(player)) {
            for (String group : warrants.getAllKeys()) warrants.getCompound(group).remove("Warning");
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        // Discover old saves already at the floor without creating sample registry entities every tick.
        if (player.tickCount % 400 == 0) {
            for (ChangedEntity mob : level.getEntitiesOfClass(ChangedEntity.class, player.getBoundingBox().inflate(48)))
                if (FactionReputation.score(mob, player) <= -100) record(mob, player);
            warrants = root.getCompound(WARRANTS);
        }
        if (root.getLong("SynergyNextSquad") > now) return;
        if (level.getEntitiesOfClass(ChangedEntity.class, player.getBoundingBox().inflate(96),
                creature -> creature.isAlive() && isPursuer(creature)).size() > 3) return;
        for (String group : java.util.List.copyOf(warrants.getAllKeys())) {
            var entry = warrants.getCompound(group);
            if (entry.getLong("Next") > now) continue;
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("Type"));
            boolean waterSquad = player.isInWaterOrBubble()
                    && id != null && id.getPath().equals("latex_shark_feral");
            if (!waterSquad && id != null
                    && id.getPath().equals("latex_shark_feral")) {
                id = ResourceLocation.fromNamespaceAndPath("changed", "latex_shark");
            }
            var type = id == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(id);
            Entity sample = type == null ? null : type.create(level);
            if (!(sample instanceof ChangedEntity mob)) { warrants.remove(group); continue; }
            mob.moveTo(player.getX(), player.getY(), player.getZ());
            if (group.startsWith("light:")) mob.getPersistentData().putString(
                    "ChangedSynergyLightReputationGroup", group.substring(6));
            if (!eligible(mob) || !group.equals(FactionReputation.groupId(mob))
                    || FactionReputation.score(mob, player) > -100) {
                warrants.remove(group); mob.discard(); continue;
            }
            if (FactionHostilityGrace.active(mob, player)) {
                entry.remove("Warning"); entry.putLong("Next", now + 1200L); mob.discard(); continue;
            }
            if (!entry.contains("Warning")) {
                player.sendSystemMessage(Component.translatable("message.changed_synergy.pursuit.warning",
                        Component.translatable(FactionReputation.displayTranslationKey(mob))));
                entry.putLong("Warning", now + 200L);
                entry.putString("Dimension", level.dimension().location().toString());
                mob.discard(); return;
            }
            if (entry.getLong("Warning") > now) { mob.discard(); return; }
            if (!entry.getString("Dimension").equals(level.dimension().location().toString())) {
                entry.remove("Warning"); mob.discard(); return;
            }
            mob.discard();
            int spawned = 0;
            int wanted = 2 + player.getRandom().nextInt(2);
            for (int attempt = 0; attempt < 24 && spawned < wanted; attempt++) {
                Entity created = type.create(level);
                if (!(created instanceof ChangedEntity hunter)) break;
                double angle = player.getRandom().nextDouble() * Math.PI * 2;
                int radius = 24 + player.getRandom().nextInt(17);
                BlockPos column = player.blockPosition().offset((int)(Math.cos(angle) * radius), 0,
                        (int)(Math.sin(angle) * radius));
                boolean placed = false;
                for (int dy = 5; dy >= -6; dy--) {
                    BlockPos pos = column.offset(0, dy, 0);
                    if (!level.hasChunksAt(pos.offset(-2, -1, -2), pos.offset(2, 5, 2))
                            || !level.getWorldBorder().isWithinBounds(pos)) continue;
                    if (waterSquad) {
                        if (!level.getFluidState(pos).is(FluidTags.WATER)) continue;
                    } else if (!level.getBlockState(pos.below()).isFaceSturdy(
                            level, pos.below(), net.minecraft.core.Direction.UP)
                            || BondedTeleportSafety.isDangerous(
                                    level.getBlockState(pos.below()))) continue;
                    hunter.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot(), 0);
                    if (!level.noCollision(hunter, hunter.getBoundingBox())
                            || !waterSquad && level.containsAnyLiquid(hunter.getBoundingBox())
                            || level.players().stream().anyMatch(p -> p.distanceToSqr(hunter) < 400)) continue;
                    placed = true; break;
                }
                if (!placed) { hunter.discard(); continue; }
                hunter.finalizeSpawn(level, level.getCurrentDifficultyAt(hunter.blockPosition()), MobSpawnType.EVENT, null, null);
                hunter.refreshDimensions();
                if (!eligible(hunter) || !level.noCollision(hunter, hunter.getBoundingBox())
                        || !waterSquad && level.containsAnyLiquid(hunter.getBoundingBox())) {
                    hunter.discard(); continue;
                }
                if (group.startsWith("light:")) hunter.getPersistentData().putString(
                        "ChangedSynergyLightReputationGroup", group.substring(6));
                var squad = new CompoundTag();
                squad.putUUID("Target", player.getUUID()); squad.putLong("Until", now + LIFETIME);
                squad.putBoolean("Leader", spawned == 0);
                hunter.getPersistentData().put(SQUAD, squad);
                hunter.setPersistenceRequired();
                if (level.addFreshEntity(hunter)) {
                    ensurePursuitGoal(hunter);
                    LatexSocialMemory.markProvoked(hunter, player);
                    hunter.setTarget(player);
                    if (spawned++ == 0) NpcDialogue.context(hunter, player,
                            "pursuit.spawn." + HunterFaction.of(hunter).id());
                } else hunter.discard();
            }
            entry.remove("Warning");
            entry.putLong("Next", now + (spawned > 0 ? COOLDOWN : 1200L));
            root.putLong("SynergyNextSquad", now + (spawned > 0 ? COOLDOWN : 1200L));
            return;
        }
    }

    public static void ensurePursuitGoal(ChangedEntity mob) {
        SafeEntityMutationQueue.queue(mob, "install-pursuit", () -> {
            if (mob.goalSelector.getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof PursuitGoal))
                mob.goalSelector.addGoal(-3, new PursuitGoal(mob));
        });
    }
    @SubscribeEvent public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ChangedEntity mob && isPursuer(mob)) ensurePursuitGoal(mob);
    }
    @SubscribeEvent public static void onDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof ChangedEntity mob && isPursuer(mob)) event.setCanceled(true);
    }
    @SubscribeEvent public static void onXp(LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof ChangedEntity mob && isPursuer(mob)) event.setDroppedExperience(0);
    }

    @SubscribeEvent public static void onLivingTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ChangedEntity mob) || mob.level().isClientSide
                || !mob.isAlive() || !isPursuer(mob) || mob.tickCount % 20 != 0) return;
        // Maintenance must still run while a higher-priority goal owns movement.
        new PursuitGoal(mob).canUse();
    }

    private static final class PursuitGoal extends Goal {
        private final ChangedEntity mob;
        private ServerPlayer player;
        private int repath;
        private PursuitGoal(ChangedEntity mob) { this.mob = mob; setFlags(EnumSet.of(Flag.MOVE)); }
        @Override public boolean canUse() {
            if (!isPursuer(mob) || !mob.isAlive()) return false;
            var data = mob.getPersistentData().getCompound(SQUAD);
            player = data.hasUUID("Target") ? mob.getServer().getPlayerList().getPlayer(data.getUUID("Target")) : null;
            if (player != null && LatexSocialMemory.isPetOwner(mob, player)) {
                mob.getPersistentData().remove(SQUAD);
                LatexSocialEvents.calmTowards(mob, player);
                SafeEntityMutationQueue.queue(mob, "retire-pursuit-goal", () ->
                        java.util.List.copyOf(mob.goalSelector.getAvailableGoals()).stream()
                                .filter(g -> g.getGoal() instanceof PursuitGoal)
                                .forEach(g -> mob.goalSelector.removeGoal(g.getGoal())));
                return false;
            }
            if (player == null || player.level() != mob.level() || !available(player)
                    || data.getLong("Until") <= mob.level().getGameTime()
                    || FactionReputation.score(mob, player) > -100 || mob.distanceToSqr(player) > 128 * 128
                    || FactionHostilityGrace.active(mob, player)) {
                if (player != null && player.level() == mob.level() && mob.distanceToSqr(player) < 48 * 48) {
                    if (data.getBoolean("Leader") && !data.getBoolean("StandDownSaid")) {
                        NpcDialogue.context(mob, player, FactionHostilityGrace.active(mob, player)
                                ? "pursuit.captured" : "pursuit.withdraw");
                        data.putBoolean("StandDownSaid", true);
                    }
                    LatexSocialEvents.calmTowards(mob, player);
                }
                if (!ChangedAddonCompat.isGrabberBusy(mob) && !mob.isVehicle()) mob.discard();
                return false;
            }
            return !ChangedAddonCompat.isGrabberBusy(mob) && !mob.isPassenger()
                    && mob.distanceToSqr(player) > 9.0D;
        }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public void tick() {
            mob.setTarget(player);
            HuntMemory.seeTarget(mob, player);
            if (--repath <= 0 && mob.distanceToSqr(player) > 9) {
                repath = 10;
                mob.getNavigation().moveTo(player, PURSUIT_SPEED);
            }
        }
        @Override public void stop() { mob.getNavigation().stop(); }
    }
}
