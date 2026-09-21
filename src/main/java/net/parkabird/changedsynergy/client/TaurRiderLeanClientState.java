package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.world.inventory.CentaurMountService;

/** Smooth, automatic side lean for players riding supported taur creatures. */
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TaurRiderLeanClientState {
    private static final float TARGET_LEAN_DEGREES = 15.0F;
    private static final float LEAN_STEP_PER_TICK = 3.75F;
    // The viewpoint must share the full pose angle. A reduced roll makes the
    // rendered rider and the player's visual orientation disagree.
    private static final float CAMERA_ROLL_SCALE = 1.0F;
    private static final double CAMERA_SIDE_OFFSET = 0.40D;
    private static final double CAMERA_HEIGHT_OFFSET = 0.10D;
    private static final double CAMERA_COLLISION_MARGIN = 0.05D;
    private static final Map<UUID, LeanState> STATES = new HashMap<>();
    private static Method cameraSetPosition;
    private static boolean cameraSetterSearched;
    private static boolean cameraSetterWarningLogged;

    private TaurRiderLeanClientState() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            STATES.clear();
            return;
        }

        Set<UUID> present = new HashSet<>();
        for (Player player : minecraft.level.players()) {
            UUID id = player.getUUID();
            present.add(id);
            LeanState state = STATES.computeIfAbsent(id, ignored -> new LeanState());
            state.previous = state.current;
            float target = isRidingTaur(player) ? TARGET_LEAN_DEGREES : 0.0F;
            state.current = Mth.approach(
                    state.current, target, LEAN_STEP_PER_TICK);
        }
        STATES.keySet().removeIf(id -> !present.contains(id));
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(
            ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) {
            return;
        }
        float partialTick = (float)event.getPartialTick();
        float lean = interpolatedLean(player, partialTick);
        if (Math.abs(lean) > 0.01F) {
            offsetCameraBesideMount(
                    minecraft, player, partialTick);
            event.setRoll(event.getRoll() + lean * CAMERA_ROLL_SCALE);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        float lean = interpolatedLean(player, event.getPartialTick());
        if (Math.abs(lean) <= 0.01F) {
            return;
        }

        float bodyYaw = Mth.rotLerp(
                event.getPartialTick(), player.yBodyRotO, player.yBodyRot);
        float ratio = lean / TARGET_LEAN_DEGREES;
        PoseStack pose = event.getPoseStack();
        pose.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        pose.mulPose(Axis.ZP.rotationDegrees(lean));
        pose.translate(0.12F * ratio, 0.0F, 0.0F);
        pose.mulPose(Axis.YP.rotationDegrees(bodyYaw));
    }

    private static boolean isRidingTaur(Player player) {
        if (!player.isPassenger()) {
            return false;
        }
        Entity vehicle = player.getVehicle();
        if (CentaurMountService.isCentaur(vehicle)) {
            return true;
        }
        // Changed mounts passengers on the underlying player when the taur is
        // itself a transfurred player. Resolve that rendered form as well as
        // ordinary ChangedEntity taur mounts.
        if (vehicle instanceof Player taurPlayer) {
            var variant = ProcessTransfur.getPlayerTransfurVariant(taurPlayer);
            return variant != null
                    && CentaurMountService.isCentaur(
                            variant.getChangedEntity());
        }
        return false;
    }

    private static float interpolatedLean(Player player, float partialTick) {
        LeanState state = STATES.get(player.getUUID());
        if (state == null) {
            return isRidingTaur(player) ? TARGET_LEAN_DEGREES : 0.0F;
        }
        return Mth.lerp(partialTick, state.previous, state.current);
    }

    /** Signed screen-space offset used by the camera mixin. */
    public static double cameraSideOffset(Player player, float partialTick) {
        float lean = interpolatedLean(player, partialTick);
        if (Math.abs(lean) <= 0.01F) {
            return 0.0D;
        }
        // Positive model roll leans toward screen-left, so move the viewpoint
        // in the same direction instead of leaving it behind the taur's head.
        return -CAMERA_SIDE_OFFSET * lean / TARGET_LEAN_DEGREES;
    }

    /**
     * Moves the completed vanilla camera rather than shadowing mapped Camera
     * members from a mixin. Production jars do not ship a Synergy refmap, so
     * descriptor-based reflection keeps this client-only adjustment stable in
     * both the Mojmap development runtime and the obfuscated game runtime.
     */
    private static void offsetCameraBesideMount(
            Minecraft minecraft,
            Player player,
            float partialTick) {
        if (minecraft.level == null) {
            return;
        }
        Camera camera = minecraft.gameRenderer.getMainCamera();
        double sideOffset = cameraSideOffset(player, partialTick);
        if (Math.abs(sideOffset) <= 0.001D) {
            return;
        }

        double yaw = Math.toRadians(camera.getYRot());
        Vec3 lateral = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw))
                .scale(sideOffset);
        Vec3 start = camera.getPosition();
        Vec3 desired = start.add(lateral)
                .add(0.0D, CAMERA_HEIGHT_OFFSET, 0.0D);
        HitResult obstruction = minecraft.level.clip(new ClipContext(
                start,
                desired,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player));
        if (obstruction.getType() != HitResult.Type.MISS) {
            double available = Math.max(
                    0.0D,
                    start.distanceTo(obstruction.getLocation())
                            - CAMERA_COLLISION_MARGIN);
            desired = start.add(lateral.normalize().scale(
                    Math.min(available, lateral.length())));
        }
        setCameraPosition(camera, desired);
    }

    private static void setCameraPosition(Camera camera, Vec3 position) {
        try {
            if (!cameraSetterSearched) {
                cameraSetterSearched = true;
                for (Method method : Camera.class.getDeclaredMethods()) {
                    Class<?>[] parameters = method.getParameterTypes();
                    if (method.getReturnType() == void.class
                            && parameters.length == 1
                            && parameters[0] == Vec3.class) {
                        method.setAccessible(true);
                        cameraSetPosition = method;
                        break;
                    }
                }
            }
            if (cameraSetPosition != null) {
                cameraSetPosition.invoke(camera, position);
            } else if (!cameraSetterWarningLogged) {
                cameraSetterWarningLogged = true;
                ChangedSynergyMod.LOGGER.warn(
                        "Could not locate Camera position setter; taur camera offset is disabled");
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!cameraSetterWarningLogged) {
                cameraSetterWarningLogged = true;
                ChangedSynergyMod.LOGGER.warn(
                        "Could not apply taur camera offset; continuing without it",
                        exception);
            }
            cameraSetPosition = null;
        }
    }

    private static final class LeanState {
        private float previous;
        private float current;
    }
}
