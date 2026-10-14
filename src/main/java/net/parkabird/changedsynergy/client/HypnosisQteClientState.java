package net.parkabird.changedsynergy.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.ltxprogrammer.changed.util.CameraUtil;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.HypnosisGazeContest;
import net.parkabird.changedsynergy.ai.HypnosisProfile;
import net.parkabird.changedsynergy.ai.HypnosisQteService;
import net.parkabird.changedsynergy.init.ChangedSynergyMobEffects;
import net.parkabird.changedsynergy.network.HypnosisQteSyncPacket;
import net.parkabird.changedsynergy.network.HypnosisTapPacket;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class HypnosisQteClientState {
    private static HypnosisQteSyncPacket current;
    private static int localTicksRemaining;
    private static int localStunTicks;
    private static int localOffGazeTicks;
    private static float smoothedGazeAlignment = 1.0F;
    private static float targetGazeAlignment = 1.0F;
    private static int cachedThemeColor = HypnosisProfile.GENERIC.color();
    private static final Map<UUID, Long> HYPNOSIS_COOLDOWNS = new HashMap<>();
    private static UUID activeHypnotistUuid;
    private static ResourceKey<Level> lockedDimension;
    private static float lockedYRot;
    private static float lockedXRot;
    private static int contestKeysDown;

    private HypnosisQteClientState() {
    }

    public static void receive(HypnosisQteSyncPacket packet) {
        HypnosisQteSyncPacket previous = current;
        if (previous != null && packet.state() != HypnosisQteSyncPacket.ACTIVE
                && previous.sessionId() != packet.sessionId()) return;
        HypnosisQteAnimationState.receive(previous, packet);
        if (packet.state() == HypnosisQteSyncPacket.CLEAR) {
            if (!packet.hypnotist()) beginCooldown(packet.hypnotistId());
            clearOverlay();
            clearRestraint();
            return;
        }
        int previousHypnotistId = current == null ? -1 : current.hypnotistId();
        int fallbackColor = previousHypnotistId == packet.hypnotistId()
                ? cachedThemeColor : HypnosisProfile.GENERIC.color();
        boolean newActiveSession = packet.state() == HypnosisQteSyncPacket.ACTIVE
                && (current == null
                        || current.state() != HypnosisQteSyncPacket.ACTIVE
                        || current.sessionId() != packet.sessionId());
        current = packet;
        if (packet.state() == HypnosisQteSyncPacket.ACTIVE) {
            if (!packet.hypnotist()) rememberActiveHypnotist(packet.hypnotistId());
            if (newActiveSession) {
                contestKeysDown = 0;
                if (packet.playerDuel()) {
                    for (KeyMapping key : contestMovementKeys()) key.setDown(false);
                }
                if (!packet.hypnotist()) captureRestraint();
                smoothedGazeAlignment = packet.gazeAlignment();
            }
            targetGazeAlignment = packet.gazeAlignment();
        } else {
            if (!packet.hypnotist()) beginCooldown(packet.hypnotistId());
        }
        cachedThemeColor = resolveThemeColor(packet.hypnotistId(), fallbackColor);
        localTicksRemaining = packet.ticksRemaining();
        localStunTicks = packet.hypnotistStunTicks();
        localOffGazeTicks = packet.offGazeTicks();
        if (!packet.hypnotist() && packet.state() != HypnosisQteSyncPacket.ACTIVE) {
            releaseClientMind();
        }
        if (!packet.hypnotist() && packet.state() == HypnosisQteSyncPacket.FAILED) {
            captureFrozenView();
        }
        if (packet.state() == HypnosisQteSyncPacket.SUCCESS
                || packet.state() == HypnosisQteSyncPacket.INTERRUPTED) {
            clearRestraint();
        }
        if (isControlLocked()) {
            suppressActionKeys(Minecraft.getInstance());
        }
    }

    public static HypnosisQteSyncPacket current() {
        return current;
    }

    public static int ticksRemaining() {
        return Math.max(0, localTicksRemaining);
    }

    public static int offGazeTicks() {
        return Math.max(0, localOffGazeTicks);
    }

    public static boolean hypnotistDistracted() {
        return current != null && current.hypnotist() && localStunTicks > 0;
    }

    public static float resistance() {
        return current == null ? 0.0F : Mth.clamp(current.resistance(), 0.0F, 1.0F);
    }

    public static float gazeAlignment() {
        // Both players see the same pressure in a shared contest.
        if (current != null && current.playerDuel()) {
            return Mth.clamp(current.gazeAlignment(), 0.0F, 1.0F);
        }
        return Mth.clamp(smoothedGazeAlignment, 0.0F, 1.0F);
    }

    public static boolean isControlLocked() {
        if (current != null && !current.hypnotist()
                && (current.state() == HypnosisQteSyncPacket.ACTIVE
                        || current.state() == HypnosisQteSyncPacket.FAILED
                                && localTicksRemaining > 0)) {
            return true;
        }
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null
                && minecraft.player.hasEffect(ChangedSynergyMobEffects.MESMERIZED.get());
    }

    /** Mouse look remains available during resistance, then freezes after failure. */
    public static boolean isViewFrozen() {
        if (current != null && !current.hypnotist()
                && current.state() == HypnosisQteSyncPacket.FAILED
                && localTicksRemaining > 0) {
            return true;
        }
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null
                && minecraft.player.hasEffect(ChangedSynergyMobEffects.MESMERIZED.get());
    }

    public static boolean shouldBlockKeyInput(int keyCode, int scanCode) {
        if (!isControlLocked()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.options.keyAttack.matches(keyCode, scanCode)
                || minecraft.options.keyUse.matches(keyCode, scanCode)
                || TaczClientInputBlocker.matches(keyCode, scanCode);
    }

    /** Real press/release edges only; holding a key or OS repeat cannot add progress. */
    public static boolean handleContestKey(int keyCode, int scanCode, int action) {
        if (current == null || !current.playerDuel() || current.state() != HypnosisQteSyncPacket.ACTIVE) return false;
        List<KeyMapping> keys = contestMovementKeys();
        for (int index = 0; index < keys.size(); index++) {
            KeyMapping key = keys.get(index);
            if (!key.matches(keyCode, scanCode)) continue;
            key.setDown(false);
            int previous = contestKeysDown;
            if (action == GLFW.GLFW_PRESS) contestKeysDown |= 1 << index;
            else if (action == GLFW.GLFW_RELEASE) contestKeysDown &= ~(1 << index);
            // Overlapping keys form one stroke; release them before tapping again.
            if ((previous == 0) != (contestKeysDown == 0)) {
                ChangedSynergyNetwork.CHANNEL.sendToServer(new HypnosisTapPacket(current.sessionId(), contestKeysDown != 0));
            }
            return true;
        }
        return false;
    }

    public static List<KeyMapping> contestMovementKeys() {
        var options = Minecraft.getInstance().options;
        return List.of(options.keyUp, options.keyLeft, options.keyDown, options.keyRight);
    }

    public static boolean shouldBlockCameraTug(LivingEntity source) {
        if (current != null
                && current.state() == HypnosisQteSyncPacket.ACTIVE
                && current.hypnotistId() == source.getId()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return false;
        }
        long now = minecraft.level.getGameTime();
        Long endTick = HYPNOSIS_COOLDOWNS.get(source.getUUID());
        if (endTick == null) {
            return false;
        }
        if (endTick <= now) {
            HYPNOSIS_COOLDOWNS.remove(source.getUUID());
            return false;
        }
        return true;
    }

    public static boolean shouldBlockEffect(
            LivingEntity source,
            MobEffectInstance effect) {
        return shouldBlockCameraTug(source)
                && effect.getAmplifier() == 2
                && (effect.getEffect() == MobEffects.CONFUSION && effect.getDuration() == 120
                        || effect.getEffect() == MobEffects.MOVEMENT_SLOWDOWN
                                && effect.getDuration() == 5);
    }

    public static int themeColor() {
        if (current == null) {
            return HypnosisProfile.GENERIC.color();
        }
        cachedThemeColor = resolveThemeColor(current.hypnotistId(), cachedThemeColor);
        return cachedThemeColor;
    }

    private static int resolveThemeColor(int hypnotistId, int fallback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return fallback;
        }
        Entity hypnotist = minecraft.level.getEntity(hypnotistId);
        if (hypnotist instanceof LivingEntity living) {
            int accent = ChangedEntities.getEntityColor(living).getSecond().toInt()
                    & 0x00FFFFFF;
            if (accent != 0xF0F0F0) {
                return accent;
            }
        }
        return hypnotist == null
                ? fallback
                : HypnosisProfile.of(hypnotist).color();
    }

    public static float visualIntensity(float partialTick) {
        if (current == null) {
            return 0.0F;
        }
        if (current.state() == HypnosisQteSyncPacket.ACTIVE) {
            return 0.30F + 0.60F * gazeAlignment();
        }
        // Sharing the contest visuals must not give the caster a mesmerized aftermath.
        if (current.hypnotist()) {
            return 0.0F;
        }
        float fade = Math.min(1.0F, (localTicksRemaining + partialTick) / 30.0F);
        return current.state() == HypnosisQteSyncPacket.FAILED ? 0.8F * fade : 0.35F * fade;
    }

    /**
     * Releases held action keys before TACZ and similar mods poll them at the
     * end of the client tick.
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onClientTickInputGuard(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && isControlLocked()) {
            suppressActionKeys(minecraft);
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || minecraft.player == null) {
            return;
        }

        if (current == null) {
            if (minecraft.player.hasEffect(ChangedSynergyMobEffects.MESMERIZED.get())) {
                captureRestraint();
                enforceControlLock(minecraft, true);
            } else {
                clearRestraint();
            }
            pruneCooldowns(minecraft);
            return;
        }

        if (localTicksRemaining > 0) {
            localTicksRemaining--;
        }
        if (localStunTicks > 0) localStunTicks--;
        if (current.state() != HypnosisQteSyncPacket.ACTIVE) {
            if (isControlLocked()) {
                captureRestraint();
                enforceControlLock(minecraft, true);
            } else {
                clearRestraint();
            }
            if (localTicksRemaining <= 0) {
                clearOverlay();
            }
            pruneCooldowns(minecraft);
            return;
        }

        if (current.playerDuel() && minecraft.screen != null && contestKeysDown != 0) {
            contestKeysDown = 0;
            ChangedSynergyNetwork.CHANNEL.sendToServer(new HypnosisTapPacket(current.sessionId(), false));
        }
        if (current.hypnotist()) {
            pruneCooldowns(minecraft);
            return;
        }

        updateLocalGazeAlignment(minecraft);

        captureRestraint();
        enforceControlLock(minecraft, false);
        pruneCooldowns(minecraft);
    }

    private static void updateLocalGazeAlignment(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || current == null) {
            return;
        }
        Entity hypnotist = minecraft.level.getEntity(current.hypnotistId());
        if (!(hypnotist instanceof LivingEntity living)) {
            smoothedGazeAlignment = Mth.lerp(
                    0.2F, smoothedGazeAlignment, targetGazeAlignment);
            return;
        }
        activeHypnotistUuid = living.getUUID();
        var towardEyes = living.getEyePosition().subtract(
                minecraft.player.getEyePosition());
        float localAlignment = towardEyes.lengthSqr() < 1.0E-4D
                ? 1.0F
                : HypnosisGazeContest.alignmentFromDot(
                        minecraft.player.getLookAngle().dot(towardEyes.normalize()));
        targetGazeAlignment = Mth.lerp(0.35F, targetGazeAlignment, localAlignment);
        smoothedGazeAlignment = Mth.lerp(
                0.28F, smoothedGazeAlignment, targetGazeAlignment);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    /** Prevents client-side block/item/entity interactions while hypnotized or mesmerized. */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent event) {
        if (event.getEntity().level().isClientSide() && isControlLocked()) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
        }
    }

    private static void rememberActiveHypnotist(int hypnotistId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Entity source = minecraft.level.getEntity(hypnotistId);
        if (source instanceof LivingEntity living) {
            activeHypnotistUuid = living.getUUID();
        }
    }

    private static void beginCooldown(int hypnotistId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            activeHypnotistUuid = null;
            return;
        }
        Entity source = minecraft.level.getEntity(hypnotistId);
        UUID sourceUuid = source instanceof LivingEntity living
                ? living.getUUID() : activeHypnotistUuid;
        if (sourceUuid != null) {
            HYPNOSIS_COOLDOWNS.put(
                    sourceUuid,
                    minecraft.level.getGameTime() + HypnosisQteService.HYPNOSIS_COOLDOWN_TICKS);
        }
        activeHypnotistUuid = null;
    }

    private static void captureRestraint() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null
                && minecraft.level != null
                && (lockedDimension == null
                        || !minecraft.level.dimension().equals(lockedDimension))) {
            lockedDimension = minecraft.level.dimension();
            lockedYRot = minecraft.player.getYRot();
            lockedXRot = minecraft.player.getXRot();
        }
    }

    private static void captureFrozenView() {
        Minecraft minecraft = Minecraft.getInstance();
        captureRestraint();
        if (minecraft.player != null) {
            lockedYRot = minecraft.player.getYRot();
            lockedXRot = minecraft.player.getXRot();
        }
    }

    private static void enforceControlLock(Minecraft minecraft, boolean freezeView) {
        if (minecraft.player == null || lockedDimension == null) {
            return;
        }
        minecraft.player.setSprinting(false);
        minecraft.player.stopUsingItem();
        if (freezeView) {
            minecraft.player.setYRot(lockedYRot);
            minecraft.player.setXRot(lockedXRot);
            minecraft.player.setYHeadRot(lockedYRot);
            minecraft.player.setYBodyRot(lockedYRot);
            minecraft.player.yRotO = lockedYRot;
            minecraft.player.xRotO = lockedXRot;
        }
        suppressActionKeys(minecraft);
    }

    private static void suppressActionKeys(Minecraft minecraft) {
        releaseKey(minecraft.options.keyAttack);
        releaseKey(minecraft.options.keyUse);
        TaczClientInputBlocker.suppress();
    }

    private static void releaseKey(KeyMapping key) {
        key.setDown(false);
        while (key.consumeClick()) {
            // Drain clicks queued before the control lock began.
        }
    }

    private static void pruneCooldowns(Minecraft minecraft) {
        if (minecraft.level != null && minecraft.player.tickCount % 20 == 0) {
            long now = minecraft.level.getGameTime();
            HYPNOSIS_COOLDOWNS.entrySet().removeIf(entry -> entry.getValue() <= now);
        }
    }

    private static void releaseClientMind() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.removeEffect(MobEffects.CONFUSION);
            minecraft.player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            CameraUtil.resetTugData(minecraft.player);
        }
    }

    private static void clearOverlay() {
        current = null;
        contestKeysDown = 0;
        HypnosisQteAnimationState.clear();
        cachedThemeColor = HypnosisProfile.GENERIC.color();
        localTicksRemaining = 0;
        localStunTicks = 0;
        localOffGazeTicks = 0;
        smoothedGazeAlignment = 1.0F;
        targetGazeAlignment = 1.0F;
    }

    private static void clearRestraint() {
        lockedDimension = null;
        lockedYRot = 0.0F;
        lockedXRot = 0.0F;
    }

    private static void reset() {
        clearOverlay();
        clearRestraint();
        HYPNOSIS_COOLDOWNS.clear();
        activeHypnotistUuid = null;
    }
}
