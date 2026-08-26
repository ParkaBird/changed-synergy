package net.parkabird.changedsynergy.compat;

import java.lang.reflect.Method;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.FirearmThreatService;
import net.parkabird.changedsynergy.ai.HypnosisQteService;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/** Optional TACZ and Superb Warfare bridge with no hard class references. */
public final class FirearmCompat {
    private static final String TACZ = "tacz";
    private static final String SUPERB_WARFARE = "superbwarfare";
    private static final TagKey<Item> FIREARMS = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(ChangedSynergyMod.MOD_ID, "firearms"));
    private static Method taczGunGetter;
    private static Method taczOperatorFromLivingEntity;
    private static Method taczOperatorGetCacheProperty;
    private static Method taczCacheGetByName;
    private static Method fastutilPairLeft;
    private static Method fastutilPairRight;
    private static Class<?> superbGunItem;
    private static boolean registered;

    private FirearmCompat() {
    }

    public static void registerOptionalEvents() {
        if (registered) {
            return;
        }
        registered = true;
        if (ModList.get().isLoaded(TACZ)) {
            registerShootEvent(
                    TACZ,
                    "com.tacz.guns.api.event.common.GunShootEvent",
                    "com.tacz.guns.api.item.IGun",
                    "getIGunOrNull");
            registerControlEvent(
                    TACZ,
                    "com.tacz.guns.api.event.common.GunMeleeEvent",
                    "getShooter");
            registerControlEvent(
                    TACZ,
                    "com.tacz.guns.api.event.common.GunReloadEvent",
                    "getEntity");
            registerControlEvent(
                    TACZ,
                    "com.tacz.guns.api.event.common.GunFireSelectEvent",
                    "getShooter");
        }
        if (ModList.get().isLoaded(SUPERB_WARFARE)) {
            registerShootEvent(
                    SUPERB_WARFARE,
                    "com.atsuishio.superbwarfare.api.event.ShootEvent$Post",
                    "com.atsuishio.superbwarfare.item.gun.GunItem",
                    null);
        }
    }

    public static boolean isHoldingFirearm(LivingEntity entity) {
        return isFirearm(entity.getMainHandItem()) || isFirearm(entity.getOffhandItem());
    }

    public static boolean isFirearm(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(FIREARMS)) {
            return true;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id != null && (TACZ.equals(id.getNamespace()) || SUPERB_WARFARE.equals(id.getNamespace()))) {
            return true;
        }
        try {
            if (taczGunGetter != null && taczGunGetter.invoke(null, stack) != null) {
                return true;
            }
            return superbGunItem != null && superbGunItem.isInstance(stack.getItem());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            ChangedSynergyMod.LOGGER.debug("Optional firearm recognition failed", exception);
            return false;
        }
    }

    private static void registerShootEvent(
            String modId,
            String eventClassName,
            String firearmClassName,
            String firearmGetterName) {
        try {
            ClassLoader loader = FirearmCompat.class.getClassLoader();
            Class<? extends Event> eventClass = Class.forName(eventClassName, false, loader)
                    .asSubclass(Event.class);
            Method shooterGetter = eventClass.getMethod("getShooter");
            Class<?> firearmClass = Class.forName(firearmClassName, false, loader);
            Method gunStackGetter = null;
            if (TACZ.equals(modId)) {
                taczGunGetter = firearmClass.getMethod(firearmGetterName, ItemStack.class);
                gunStackGetter = eventClass.getMethod("getGunItemStack");
                initializeTaczSoundAccess(loader);
            } else {
                superbGunItem = firearmClass;
            }
            addControlListener(eventClass, shooterGetter);
            addShootListener(eventClass, shooterGetter, modId, gunStackGetter);
            ChangedSynergyMod.LOGGER.info("Enabled optional {} firearm integration", modId);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            ChangedSynergyMod.LOGGER.error(
                    "Could not enable optional {} firearm integration", modId, exception);
        }
    }

    private static void initializeTaczSoundAccess(ClassLoader loader)
            throws ReflectiveOperationException {
        Class<?> operatorClass = Class.forName(
                "com.tacz.guns.api.entity.IGunOperator", false, loader);
        Class<?> cacheClass = Class.forName(
                "com.tacz.guns.resource.modifier.AttachmentCacheProperty", false, loader);
        Class<?> pairClass = Class.forName(
                "it.unimi.dsi.fastutil.Pair", false, loader);
        taczOperatorFromLivingEntity =
                operatorClass.getMethod("fromLivingEntity", LivingEntity.class);
        taczOperatorGetCacheProperty =
                operatorClass.getMethod("getCacheProperty");
        taczCacheGetByName = cacheClass.getMethod("getCache", String.class);
        fastutilPairLeft = pairClass.getMethod("left");
        fastutilPairRight = pairClass.getMethod("right");
    }

    private static void registerControlEvent(
            String modId,
            String eventClassName,
            String actorGetterName) {
        try {
            Class<? extends Event> eventClass = Class.forName(
                            eventClassName,
                            false,
                            FirearmCompat.class.getClassLoader())
                    .asSubclass(Event.class);
            addControlListener(eventClass, eventClass.getMethod(actorGetterName));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            ChangedSynergyMod.LOGGER.error(
                    "Could not guard optional {} action event {}",
                    modId,
                    eventClassName,
                    exception);
        }
    }

    private static <T extends Event> void addControlListener(
            Class<T> eventClass,
            Method actorGetter) {
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.HIGHEST,
                false,
                eventClass,
                event -> blockLockedFirearmAction(event, actorGetter));
    }

    private static <T extends Event> void addShootListener(
            Class<T> eventClass,
            Method shooterGetter,
            String modId,
            Method gunStackGetter) {
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.LOWEST,
                false,
                eventClass,
                event -> handleShotEvent(event, shooterGetter, modId, gunStackGetter));
    }

    private static void blockLockedFirearmAction(Event event, Method actorGetter) {
        try {
            ServerPlayer player = resolveServerPlayer(actorGetter.invoke(event));
            if (player != null
                    && HypnosisQteService.isPlayerControlLocked(player)
                    && event.isCancelable()) {
                event.setCanceled(true);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            ChangedSynergyMod.LOGGER.debug("Could not guard an optional firearm action", exception);
        }
    }

    private static void handleShotEvent(
            Event event,
            Method shooterGetter,
            String modId,
            Method gunStackGetter) {
        try {
            ServerPlayer player = resolveServerPlayer(shooterGetter.invoke(event));
            if (player != null && !player.level().isClientSide()) {
                TaczSoundProfile profile = TACZ.equals(modId)
                        ? readTaczSoundProfile(event, gunStackGetter, player)
                        : null;
                if (profile != null) {
                    FirearmThreatService.onGunshot(
                            player, profile.radius(), profile.suppressed());
                } else {
                    FirearmThreatService.onGunshot(player);
                }
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            ChangedSynergyMod.LOGGER.debug("Could not read optional firearm shot event", exception);
        }
    }

    /**
     * TACZ already computes a SILENCE cache value for the exact gun and muzzle
     * setup. Its left value is the sound radius and its right value selects the
     * suppressed shot sound, so Synergy follows the same attachment mechanism.
     */
    private static TaczSoundProfile readTaczSoundProfile(
            Event event,
            Method gunStackGetter,
            ServerPlayer shooter) {
        if (gunStackGetter == null
                || taczGunGetter == null
                || taczOperatorFromLivingEntity == null
                || taczOperatorGetCacheProperty == null
                || taczCacheGetByName == null
                || fastutilPairLeft == null
                || fastutilPairRight == null) {
            return null;
        }
        try {
            Object stackValue = gunStackGetter.invoke(event);
            if (!(stackValue instanceof ItemStack stack)
                    || stack.isEmpty()
                    || taczGunGetter.invoke(null, stack) == null) {
                return null;
            }
            Object operator = taczOperatorFromLivingEntity.invoke(null, shooter);
            Object cache = taczOperatorGetCacheProperty.invoke(operator);
            if (cache == null) {
                return null;
            }
            Object silence = taczCacheGetByName.invoke(cache, "silence");
            if (silence == null) {
                return null;
            }
            Object distance = fastutilPairLeft.invoke(silence);
            Object useSilenceSound = fastutilPairRight.invoke(silence);
            if (!(distance instanceof Number number)) {
                return null;
            }
            return new TaczSoundProfile(
                    Math.max(0.0D, number.doubleValue()),
                    Boolean.TRUE.equals(useSilenceSound));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            ChangedSynergyMod.LOGGER.debug(
                    "Could not read TACZ's attachment-adjusted gunshot range", exception);
            return null;
        }
    }

    private static ServerPlayer resolveServerPlayer(Object value) {
        if (value instanceof ServerPlayer player) {
            return player;
        }
        if (value instanceof Entity entity
                && entity.getControllingPassenger() instanceof ServerPlayer player) {
            return player;
        }
        return null;
    }

    private record TaczSoundProfile(double radius, boolean suppressed) {
    }
}
