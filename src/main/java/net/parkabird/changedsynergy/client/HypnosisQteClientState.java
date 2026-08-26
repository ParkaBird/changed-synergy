package net.parkabird.changedsynergy.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.ltxprogrammer.changed.util.CameraUtil;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.resources.ResourceKey;
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
import net.parkabird.changedsynergy.ai.HypnosisProfile;
import net.parkabird.changedsynergy.ai.HypnosisQteService;
import net.parkabird.changedsynergy.init.ChangedSynergyMobEffects;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.HypnosisQteInputPacket;
import net.parkabird.changedsynergy.network.HypnosisQteSyncPacket;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class HypnosisQteClientState {
    private static HypnosisQteSyncPacket current;
    private static int localTicksRemaining;
    private static int localTicksUnpressed;
    private static int cachedThemeColor = HypnosisProfile.GENERIC.color();
    private static final Map<UUID, Long> HYPNOSIS_COOLDOWNS = new HashMap<>();
    private static final boolean[] WAS_DOWN = new boolean[4];
    private static UUID activeHypnotistUuid;
    private static ResourceKey<Level> lockedDimension;
    private static float lockedYRot;
    private static float lockedXRot;

    private HypnosisQteClientState() {
    }

    public static void receive(HypnosisQteSyncPacket packet) {
        HypnosisQteSyncPacket previous = current;
        HypnosisQteAnimationState.receive(previous, packet);
        if (packet.state() == HypnosisQteSyncPacket.CLEAR) {
            beginCooldown(packet.hypnotistId());
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
            rememberActiveHypnotist(packet.hypnotistId());
            if (newActiveSession) {
                captureRestraint();
            }
        } else {
            beginCooldown(packet.hypnotistId());
        }
        cachedThemeColor = resolveThemeColor(packet.hypnotistId(), fallbackColor);
        localTicksRemaining = packet.ticksRemaining();
        localTicksUnpressed = packet.ticksUnpressed();
        if (packet.state() != HypnosisQteSyncPacket.ACTIVE) {
            releaseClientMind();
        }
        if (packet.state() == HypnosisQteSyncPacket.FAILED) {
            captureFrozenView();
        }
        if (packet.state() == HypnosisQteSyncPacket.SUCCESS
                || packet.state() == HypnosisQteSyncPacket.INTERRUPTED) {
            clearRestraint();
        }
        if (isControlLocked()) {
            suppressActionKeys(Minecraft.getInstance());
        }
        snapshotKeys();
    }

    public static HypnosisQteSyncPacket current() {
        return current;
    }

    public static int ticksRemaining() {
        return Math.max(0, localTicksRemaining);
    }

    public static int ticksUnpressed() {
        return Math.max(0, localTicksUnpressed);
    }

    public static boolean isControlLocked() {
        if (current != null
                && (current.state() == HypnosisQteSyncPacket.ACTIVE
                        || current.state() == HypnosisQteSyncPacket.FAILED
                                && localTicksRemaining > 0)) {
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
            return 0.45F + 0.45F * current.controlStrength();
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
            snapshotKeys();
            pruneCooldowns(minecraft);
            return;
        }

        if (localTicksRemaining > 0) {
            localTicksRemaining--;
        }
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

        if (current.expectedKey() >= 0) {
            localTicksUnpressed++;
        }
        lockViewOnHypnotist(minecraft);
        KeyMapping[] keys = keys();
        for (int index = 0; index < keys.length; index++) {
            boolean down = keys[index].isDown();
            if (down && !WAS_DOWN[index] && current.expectedKey() >= 0) {
                ChangedSynergyNetwork.CHANNEL.sendToServer(
                        new HypnosisQteInputPacket(current.sessionId(), index));
            }
            WAS_DOWN[index] = down;
        }

        captureRestraint();
        enforceControlLock(minecraft, false);
        pruneCooldowns(minecraft);
    }

    private static void lockViewOnHypnotist(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || current == null) {
            return;
        }
        Entity hypnotist = minecraft.level.getEntity(current.hypnotistId());
        if (!(hypnotist instanceof LivingEntity living)) {
            return;
        }
        activeHypnotistUuid = living.getUUID();
        minecraft.player.lookAt(
                EntityAnchorArgument.Anchor.EYES,
                living.getEyePosition());
        minecraft.player.xRotO = minecraft.player.getXRot();
        minecraft.player.yRotO = minecraft.player.getYRot();
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

    /** Matches GrabEntityAbilityInstance's ordered key list exactly. */
    private static KeyMapping[] keys() {
        var options = Minecraft.getInstance().options;
        return new KeyMapping[] {options.keyUp, options.keyDown, options.keyLeft, options.keyRight};
    }

    private static void snapshotKeys() {
        KeyMapping[] keys = keys();
        for (int index = 0; index < keys.length; index++) {
            WAS_DOWN[index] = keys[index].isDown();
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
        HypnosisQteAnimationState.clear();
        cachedThemeColor = HypnosisProfile.GENERIC.color();
        localTicksRemaining = 0;
        localTicksUnpressed = 0;
        for (int index = 0; index < WAS_DOWN.length; index++) {
            WAS_DOWN[index] = false;
        }
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
