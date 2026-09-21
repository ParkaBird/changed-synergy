package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.LivingEntityDataExtension;
import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.init.ChangedAbilities;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.ltxprogrammer.changed.init.ChangedKeyMappings;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.TakeoverSession;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.ExoskeletonMotionPacket;
import net.parkabird.changedsynergy.network.TakeoverActionPacket;
import net.parkabird.changedsynergy.network.TakeoverStatePacket;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, value = Dist.CLIENT)
public final class TakeoverClientState {
    private static TakeoverStatePacket current;
    private static int sentSequence;
    private static boolean sequenceSent;
    private static int age;
    private static int sinceSync;
    public static final String CLIENT_ACTIVE = "SynergyTakeoverClient";
    private static final String CLIENT_SESSION = "SynergyTakeoverClientSession";
    private static final Map<UUID, TakeoverStatePacket> TRACKED = new HashMap<>();
    private static ClientLevel trackedLevel;
    private static Entity observedCamera;
    private static Entity previousCamera;
    private static Vec3 exoskeletonWalkPosition;
    private static UUID exoskeletonWalkSession;
    private static Float exoskeletonFacingYaw;
    private static ExoskeletonMotionPacket exoskeletonMotion;
    private static int exoskeletonMotionAge;
    private static int wakeFadeTicks;

    private TakeoverClientState() {}

    public static TakeoverStatePacket current() { return current; }
    public static boolean active() { return current != null; }
    public static boolean normal() { return active() && current.kind() == TakeoverStatePacket.NORMAL; }
    public static boolean movementLocked() {
        return active() && !(normal() && current.phase() == TakeoverStatePacket.BORROWED);
    }
    public static boolean canOpen() {
        return normal() && (current.phase() == TakeoverStatePacket.CONTROLLED
                || current.phase() == TakeoverStatePacket.BORROWED);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void blockHotbarScroll(InputEvent.MouseScrollingEvent event) {
        if (active()) event.setCanceled(true);
    }
    public static int releaseRemainingTicks() {
        return active() ? Math.max(0, current.releaseRemainingTicks() - sinceSync) : 0;
    }
    public static int releaseTotalTicks() {
        return active() ? Math.max(1, current.releaseTotalTicks()) : 1;
    }
    public static int cooldownTicks() {
        return active() ? Math.max(0, current.borrowCooldownTicks() - sinceSync) : 0;
    }
    public static int age() { return age; }
    public static float fade() {
        if (active() && Float.isFinite(current.fade()))
            return Math.max(0, Math.min(1, current.fade()));
        return wakeFadeTicks / (float)TakeoverSession.EYE_OPEN_TICKS;
    }
    public static float fade(float partial) {
        float value = fade();
        if (active() && current.phase() == TakeoverStatePacket.SLEEPING) {
            // Interpolate one normal synchronization interval, never predict through a network stall.
            value += Math.min(5, sinceSync + Math.max(0, Math.min(1, partial)))
                    / (float)TakeoverSession.EYE_CLOSE_TICKS;
        } else if (!active() && wakeFadeTicks > 0) {
            value = (wakeFadeTicks - Math.max(0, Math.min(1, partial)))
                    / (float)TakeoverSession.EYE_OPEN_TICKS;
        }
        return Mth.clamp(value, 0.0F, 1.0F);
    }
    public static Component text(String key, Object... args) {
        return Component.translatable("takeover.changed_synergy." + key, args);
    }
    public static KeyMapping[] movementKeys() {
        var o = Minecraft.getInstance().options;
        return new KeyMapping[] {o.keyUp, o.keyDown, o.keyLeft, o.keyRight};
    }

    public static void receive(TakeoverStatePacket packet) {
        if (packet.kind() < 0 || packet.kind() > 1 || !validPhase(packet.phase())) return;
        trackReceive(packet);
        var player = Minecraft.getInstance().player;
        if (player == null || packet.playerId() != player.getId()) return;
        receiveLocal(packet);
    }

    public static void receiveExoskeletonMotion(ExoskeletonMotionPacket packet) {
        if (!active() || current.kind() != TakeoverStatePacket.EXOSKELETON
                || !current.sessionId().equals(packet.sessionId())
                || !Double.isFinite(packet.x()) || !Double.isFinite(packet.y())
                || !Double.isFinite(packet.z()) || !Float.isFinite(packet.yaw())) return;
        exoskeletonMotion = packet;
        exoskeletonMotionAge = 0;
    }

    private static void receiveLocal(TakeoverStatePacket packet) {
        if (packet.phase() == TakeoverStatePacket.FINISHED) {
            // A late finish for an old carrier must not clear a newer session.
            if (current != null && current.sessionId().equals(packet.sessionId())) {
                clearLocal();
                if (packet.fade() > 0.5F)
                    wakeFadeTicks = (int)TakeoverSession.EYE_OPEN_TICKS;
            }
            return;
        }
        if (packet.kind() < 0 || packet.kind() > 1 || !validActivePhase(packet.phase())) return;
        if (current == null || !current.sessionId().equals(packet.sessionId())) {
            age = 0;
            sequenceSent = false;
            wakeFadeTicks = 0;
        }
        current = packet;
        // Every authoritative struggle update acknowledges the last QTE input,
        // including a wrong key.  Keeping this latch set after a wrong key left
        // all later movement presses permanently blocked on the client.
        if (packet.phase() == TakeoverStatePacket.STRUGGLE) sequenceSent = false;
        if (packet.kind() != TakeoverStatePacket.EXOSKELETON
                || !packet.sessionId().equals(exoskeletonWalkSession)) {
            exoskeletonWalkPosition = null;
            exoskeletonFacingYaw = null;
            exoskeletonMotion = null;
            exoskeletonMotionAge = 0;
            exoskeletonWalkSession = packet.kind() == TakeoverStatePacket.EXOSKELETON
                    ? packet.sessionId() : null;
        }
        AbsorptionNegotiationClientState.receive(false);
        sinceSync = 0;
        suppressActions();
        if (movementLocked()) {
            for (var key : movementKeys()) release(key);
            var o = Minecraft.getInstance().options;
            release(o.keyJump);
            release(o.keyShift);
            release(o.keySprint);
        }
        if (!canOpen() && Minecraft.getInstance().screen instanceof TakeoverScreen) {
            Minecraft.getInstance().setScreen(null);
        }
    }

    private static boolean validPhase(int phase) {
        return validActivePhase(phase) || phase == TakeoverStatePacket.FINISHED;
    }

    private static boolean validActivePhase(int phase) {
        return phase >= TakeoverStatePacket.CONTROLLED
                && phase <= TakeoverStatePacket.SLEEPING
                || phase == TakeoverStatePacket.RELEASING;
    }

    private static void clearLocal() {
        restoreCamera();
        current = null;
        sequenceSent = false;
        age = sinceSync = 0;
        exoskeletonWalkPosition = null;
        exoskeletonWalkSession = null;
        exoskeletonFacingYaw = null;
        exoskeletonMotion = null;
        exoskeletonMotionAge = 0;
        if (Minecraft.getInstance().screen instanceof TakeoverScreen) Minecraft.getInstance().setScreen(null);
    }

    public static void clear() {
        for (var packet : TRACKED.values()) applyTracked(trackedLevel, packet, false);
        TRACKED.clear();
        trackedLevel = null;
        clearLocal();
        wakeFadeTicks = 0;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }

    private static void release(KeyMapping key) {
        key.setDown(false);
        while (key.consumeClick()) {}
    }

    private static void suppressActions() {
        var mc = Minecraft.getInstance();
        var o = mc.options;
        release(o.keyAttack);
        if (!allowsCurrentBedUse()) release(o.keyUse);
        release(o.keyDrop);
        release(o.keySwapOffhand);
        release(o.keyPickItem);
        release(o.keyInventory);
        release(ChangedKeyMappings.SELECT_ABILITY);
        release(ChangedKeyMappings.USE_ABILITY);
        TaczClientInputBlocker.suppress();
        if (!allowsCurrentBedUse() && mc.player != null && mc.player.isUsingItem() && mc.gameMode != null) {
            mc.gameMode.releaseUsingItem(mc.player);
        }
    }

    public static boolean allowsCurrentBedUse() {
        var mc = Minecraft.getInstance();
        return normal() && current.phase() == TakeoverStatePacket.BORROWED
                && mc.level != null && mc.hitResult instanceof BlockHitResult hit
                && mc.level.getBlockState(hit.getBlockPos()).getBlock() instanceof BedBlock;
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void inputGuard(TickEvent.ClientTickEvent event) {
        if (active()) suppressActions();
    }

    @SubscribeEvent public static void containerMouse(ScreenEvent.MouseButtonPressed.Pre event) {
        // Changed's radial base class is also an AbstractContainerScreen. Keep ordinary
        // inventories locked without swallowing the takeover wheel's own selections.
        if (active() && event.getScreen() instanceof AbstractContainerScreen<?>
                && !(event.getScreen() instanceof TakeoverScreen)) event.setCanceled(true);
    }

    @SubscribeEvent public static void containerKey(ScreenEvent.KeyPressed.Pre event) {
        if (active() && event.getScreen() instanceof AbstractContainerScreen<?>
                && !(event.getScreen() instanceof TakeoverScreen)
                && event.getKeyCode() != GLFW.GLFW_KEY_ESCAPE) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        trackTick();
        if (!mc.isPaused() && wakeFadeTicks > 0) wakeFadeTicks--;
        if (mc.player == null || mc.level == null) { if (active()) clearLocal(); return; }
        if (active() && current.playerId() != mc.player.getId()) clearLocal();
        if (!active()) {
            for (var packet : TRACKED.values()) {
                if (packet.playerId() == mc.player.getId()) { receiveLocal(packet); break; }
            }
        }
        if (!active()) return;
        updateCamera();
        applyExoskeletonMotion(mc.player);
        updateExoskeletonWalkAnimation(mc.player);
        if (!mc.isPaused()) { age++; sinceSync++; }
        // Observation is the default. Open communication only on an explicit key press.
    }

    private static void applyExoskeletonMotion(Player player) {
        if (current.kind() != TakeoverStatePacket.EXOSKELETON || !movementLocked()
                || exoskeletonMotion == null
                || !current.sessionId().equals(exoskeletonMotion.sessionId())) return;
        exoskeletonMotionAge++;
        Vec3 target = new Vec3(exoskeletonMotion.x(), exoskeletonMotion.y(), exoskeletonMotion.z());
        Vec3 error = target.subtract(player.position());
        if (error.lengthSqr() > 6.25D) {
            player.setPos(target);
        } else {
            // Keep a small prediction window between server frames, then converge
            // without teleport resets that destroy ordinary render interpolation.
            Vec3 velocity = new Vec3(exoskeletonMotion.velocityX(),
                    exoskeletonMotion.velocityY(), exoskeletonMotion.velocityZ());
            double prediction = exoskeletonMotionAge <= 3 ? 0.45D : 0.0D;
            Vec3 rendered = player.position().add(error.scale(0.48D))
                    .add(velocity.scale(prediction));
            player.setPos(rendered);
        }
        boolean freshMotion = exoskeletonMotionAge <= 3;
        player.setDeltaMovement(freshMotion ? exoskeletonMotion.velocityX() : 0.0D,
                freshMotion ? exoskeletonMotion.velocityY() : 0.0D,
                freshMotion ? exoskeletonMotion.velocityZ() : 0.0D);
        exoskeletonFacingYaw = Mth.rotLerp(0.28F, player.getYRot(), exoskeletonMotion.yaw());
        player.setYRot(exoskeletonFacingYaw);
        player.setYBodyRot(exoskeletonFacingYaw);
        player.setYHeadRot(exoskeletonFacingYaw);
        player.setXRot(Mth.rotLerp(0.3F, player.getXRot(), 0.0F));
    }

    /** Network teleports do not always advance the local transformed model's limb state. */
    private static void updateExoskeletonWalkAnimation(Player player) {
        if (current.kind() != TakeoverStatePacket.EXOSKELETON || !movementLocked()) {
            exoskeletonWalkPosition = null;
            exoskeletonFacingYaw = null;
            return;
        }
        Vec3 position = player.position();
        if (exoskeletonWalkPosition == null) {
            exoskeletonWalkPosition = position;
            return;
        }
        double distance = position.subtract(exoskeletonWalkPosition).horizontalDistance();
        Vec3 moved = position.subtract(exoskeletonWalkPosition);
        exoskeletonWalkPosition = position;
        if (exoskeletonMotion == null && distance <= 1.0D
                && moved.horizontalDistanceSqr() > 1.0E-6D) {
            exoskeletonFacingYaw = (float)(Math.atan2(moved.z, moved.x)
                    * 180.0D / Math.PI) - 90.0F;
        }
        if (exoskeletonFacingYaw != null) {
            // The worn exoskeleton owns both locomotion and observation. Pin the
            // local camera/body to its last real movement heading every client
            // tick, including pauses between path nodes.
            player.setYRot(exoskeletonFacingYaw);
            player.setYBodyRot(exoskeletonFacingYaw);
            player.setYHeadRot(exoskeletonFacingYaw);
            player.setXRot(0.0F);
        }
        float desired = distance <= 1.0D && distance > 0.0025D
                ? Math.min(1.0F, (float)distance * 4.0F) : 0.0F;
        player.walkAnimation.update(desired, 0.4F);
    }

    public static boolean request(int action, UUID screenSession) {
        if (!normal() || current == null || screenSession == null
                || !current.sessionId().equals(screenSession)) return false;
        if (action != TakeoverActionPacket.REQUEST_CONTROL
                && action != TakeoverActionPacket.START_STRUGGLE
                && action != TakeoverActionPacket.CONFIRM_STRUGGLE
                && action != TakeoverActionPacket.RETURN_CONTROL) return false;
        // Do not duplicate phase, cooldown, safety or one-use checks on the
        // client. A packet can arrive between drawing and clicking the wheel;
        // the authoritative server accepts it or supplies contextual refusal
        // dialogue instead of making the option appear inert.
        ChangedSynergyNetwork.CHANNEL.sendToServer(new TakeoverActionPacket(
                current.sessionId(), action, -1, current.sequence()));
        return true;
    }

    /** Matches Changed's actual, possibly rebound, ability-wheel input. */
    public static boolean isSharedWheelInput(int keyCode, int scanCode, int mouseButton) {
        KeyMapping mapping = ChangedKeyMappings.SELECT_ABILITY;
        InputConstants.Key input = mouseButton >= 0
                ? InputConstants.Type.MOUSE.getOrCreate(mouseButton)
                : keyCode == GLFW.GLFW_KEY_UNKNOWN
                        ? InputConstants.Type.SCANCODE.getOrCreate(scanCode)
                        : InputConstants.Type.KEYSYM.getOrCreate(keyCode);
        // Changed's select-ability mapping has custom setDown behaviour. Compare
        // the resolved binding as well as using Forge's active-context matcher so
        // takeover input remains reliable for both default and rebound keys.
        return mapping.getKey().equals(input) || mapping.isActiveAndMatches(input);
    }

    /** Opens takeover choices through the same Forge input path as other wheels. */
    public static boolean tryOpenSharedWheel(int keyCode, int scanCode, int mouseButton) {
        var mc = Minecraft.getInstance();
        if (!canOpen() || mc.screen != null
                || !isSharedWheelInput(keyCode, scanCode, mouseButton)) return false;
        mc.setScreen(new TakeoverScreen());
        return true;
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void openWheelFromMouse(InputEvent.MouseButton.Pre event) {
        if (event.getAction() == InputConstants.PRESS
                && tryOpenSharedWheel(-1, -1, event.getButton())) {
            event.setCanceled(true);
        }
    }

    /** Called only on an actual GLFW press, never repeat/tick polling. */
    public static void press(int keyCode, int scanCode, int mouseButton) {
        var mc = Minecraft.getInstance();
        if (!normal() || current.phase() != TakeoverStatePacket.STRUGGLE
                || current.expectedKey() < 0 || current.expectedKey() > 3
                || mc.screen != null || !mc.isWindowActive()
                || (sequenceSent && sentSequence == current.sequence())) return;
        var keys = movementKeys();
        for (int i = 0; i < keys.length; i++) {
            boolean match = mouseButton >= 0 ? keys[i].matchesMouse(mouseButton) : keys[i].matches(keyCode, scanCode);
            if (!match) continue;
            sentSequence = current.sequence();
            sequenceSent = true;
            ChangedSynergyNetwork.CHANNEL.sendToServer(new TakeoverActionPacket(
                    current.sessionId(), TakeoverActionPacket.QTE_INPUT, i, current.sequence()));
            return;
        }
    }

    /** Allow observation, chat and vanilla menu shortcuts; deny other gameplay bindings. */
    public static boolean blockKey(int key, int scan) {
        if (!active() || Minecraft.getInstance().screen != null) return false;
        var o = Minecraft.getInstance().options;
        if (ChangedKeyMappings.SELECT_ABILITY.matches(key, scan)
                || ChangedKeyMappings.USE_ABILITY.matches(key, scan)
                || TaczClientInputBlocker.matches(key, scan)) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE || (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F12)
                || o.keyChat.matches(key, scan) || o.keyCommand.matches(key, scan)
                || o.keyPlayerList.matches(key, scan) || o.keyTogglePerspective.matches(key, scan)) return false;
        if (!movementLocked()) {
            for (var mapping : movementKeys()) if (mapping.matches(key, scan)) return false;
            if (o.keyJump.matches(key, scan) || o.keyShift.matches(key, scan)
                    || o.keySprint.matches(key, scan)) return false;
            if (o.keyUse.matches(key, scan) && allowsCurrentBedUse()) return false;
        }
        return true;
    }

    private static void trackReceive(TakeoverStatePacket packet) {
        var level = Minecraft.getInstance().level;
        if (trackedLevel != null && trackedLevel != level) clear();
        trackedLevel = level;
        if (packet.phase() == TakeoverStatePacket.FINISHED) {
            var old = TRACKED.remove(packet.sessionId());
            applyTracked(level, old != null ? old : packet, false);
            return;
        }
        // A player/carrier cannot belong to two sessions. Clear old ownership before replacing it.
        var iterator = TRACKED.values().iterator();
        while (iterator.hasNext()) {
            var old = iterator.next();
            if (!old.sessionId().equals(packet.sessionId())
                    && (old.playerId() == packet.playerId()
                        || packet.carrierId() >= 0 && old.carrierId() == packet.carrierId())) {
                applyTracked(level, old, false);
                iterator.remove();
            }
        }
        var old = TRACKED.put(packet.sessionId(), packet);
        if (old != null && (old.carrierId() != packet.carrierId() || old.playerId() != packet.playerId())) {
            applyTracked(level, old, false);
        }
        applyTracked(level, packet, true);
    }

    /** The camera follows the actual controlling creature, as in native player grabs.
     * Do not change game mode, inventory, or server spectator state. */
    private static void updateCamera() {
        var mc = Minecraft.getInstance();
        Entity carrier = normal() && movementLocked() && mc.level != null
                ? mc.level.getEntity(current.carrierId()) : null;
        if (carrier == null || !carrier.isAlive() || carrier.isRemoved()) {
            restoreCamera();
            return;
        }
        if (observedCamera == null) previousCamera = mc.getCameraEntity();
        observedCamera = carrier;
        if (mc.getCameraEntity() != carrier) mc.setCameraEntity(carrier);
    }

    private static void restoreCamera() {
        var mc = Minecraft.getInstance();
        if (observedCamera != null && mc.getCameraEntity() == observedCamera) {
            Entity restored = previousCamera != null && previousCamera.isAlive()
                    && !previousCamera.isRemoved() && previousCamera.level() == mc.level
                    ? previousCamera : mc.player;
            mc.setCameraEntity(restored);
        }
        observedCamera = previousCamera = null;
    }

    private static void trackTick() {
        var level = Minecraft.getInstance().level;
        if (trackedLevel != null && trackedLevel != level) { clear(); return; }
        if (level == null) return;
        trackedLevel = level;
        for (var packet : TRACKED.values()) applyTracked(level, packet, true);
    }

    private static boolean owned(Entity entity, UUID session) {
        return entity != null && entity.getPersistentData().hasUUID(CLIENT_SESSION)
                && session.equals(entity.getPersistentData().getUUID(CLIENT_SESSION));
    }

    private static void mark(Entity entity, UUID session, boolean active) {
        if (entity == null) return;
        var data = entity.getPersistentData();
        if (active) {
            data.putBoolean(CLIENT_ACTIVE, true);
            data.putUUID(CLIENT_SESSION, session);
        } else if (owned(entity, session)) {
            data.putBoolean(CLIENT_ACTIVE, false);
            data.remove(CLIENT_SESSION);
        }
    }

    /** Retry when tracking entities arrive, and repair Addon RELEASE without executing suit/release side effects. */
    private static void applyTracked(ClientLevel level, TakeoverStatePacket packet, boolean active) {
        if (level == null) return;
        Entity source = level.getEntity(packet.carrierId());
        Entity target = level.getEntity(packet.playerId());
        boolean ownedSource = owned(source, packet.sessionId());
        boolean ownedPlayer = owned(target, packet.sessionId());
        mark(source, packet.sessionId(), active);
        mark(target, packet.sessionId(), active);
        if (packet.kind() != TakeoverStatePacket.NORMAL) return;
        GrabEntityAbilityInstance ability = source instanceof ChangedEntity carrier
                ? IAbstractChangedEntity.forEntity(carrier)
                    .getAbilityInstanceSafe(ChangedAbilities.GRAB_ENTITY_ABILITY.get()).orElse(null)
                : null;
        if (!active) {
            if (ownedSource && source != null) source.noPhysics = false;
            if (ownedSource && ability != null && ability.grabbedEntity != null
                    && ability.grabbedEntity.getId() == packet.playerId()) {
                ability.grabbedEntity = null;
                ability.suited = ability.grabbedHasControl = false;
                ability.attackDown = ability.useDown = false;
                ability.suitTransition = ability.suitTransitionO = 0;
                ability.currentEscapeKey = ability.lastEscapeKey = null;
                ability.escapeKeys.reset(false);
            }
            if (ownedPlayer && target instanceof Player player) {
                if (player instanceof LivingEntityDataExtension extension) {
                    LivingEntity grabber = extension.getGrabbedBy();
                    if (grabber != null && grabber.getId() == packet.carrierId()) extension.setGrabbedBy(null);
                }
                player.noPhysics = false;
            }
            return;
        }
        if (!(source instanceof ChangedEntity carrier) || !(target instanceof Player player) || ability == null) return;
        FriendlySuitClientState.forgetForTakeover(packet.playerId(), packet.carrierId());
        carrier.setInvisible(false);
        ability.grabbedEntity = player;
        ability.suited = true;
        ability.grabbedHasControl = packet.phase() == TakeoverStatePacket.BORROWED;
        ability.grabStrength = ability.grabStrengthO = 1;
        ability.suitTransition = ability.suitTransitionO = GrabEntityAbilityInstance.SUIT_TRANSITION_MAX;
        ability.attackDown = ability.useDown = false;
        ability.currentEscapeKey = ability.lastEscapeKey = null;
        ability.ticksUnpressed = 0;
        ability.escapeKeys.reset(false);
        if (player instanceof LivingEntityDataExtension extension) extension.setGrabbedBy(carrier);
        player.noPhysics = !ability.grabbedHasControl;
        carrier.noPhysics = ability.grabbedHasControl;
    }
}
