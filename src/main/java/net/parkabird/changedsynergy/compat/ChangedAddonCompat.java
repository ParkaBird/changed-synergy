package net.parkabird.changedsynergy.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.data.AccessorySlots;
import net.ltxprogrammer.changed.init.ChangedAccessorySlots;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;

public final class ChangedAddonCompat {
    private static final String OPTIONAL_EVENTS =
            "net.parkabird.changedsynergy.compat.addon.ChangedAddonSocialEvents";
    private static boolean optionalEventsRegistered;
    private static volatile Class<?> optionalEventsClass;
    // Bridge methods have distinct names; retain missing lookups as well.
    private static final Map<String, Optional<Method>> OPTIONAL_METHODS = new ConcurrentHashMap<>();
    private record GrabMethods(Method allowTransfurred, Method safeMode, Method readSafeMode) {}
    private static final ClassValue<GrabMethods> GRAB_METHODS = new ClassValue<>() {
        @Override protected GrabMethods computeValue(Class<?> type) {
            return new GrabMethods(findMethod(type, "setAllowGrabTransfurred", boolean.class),
                    findMethod(type, "setSafeModeAuthoritative", boolean.class), findMethod(type, "isSafeMode"));
        }
    };
    public static final String MOD_ID = "changed_addon";
    public static final ResourceLocation PACIFIED = id("pacified");
    public static final ResourceLocation HAZARD_BODY_SUIT = id("hazard_body_suit");
    public static final ResourceLocation TRANSFUR_TOTEM = id("transfur_totem");
    public static final ResourceLocation TRANSLATOR = id("translator");
    public static final ResourceLocation DARK_LATEX_COAT = id("dark_latex_coat");
    public static final ResourceLocation DARK_LATEX_COAT_CAP = id("dark_latex_coat_cap");
    public static final ResourceLocation CROWBAR = id("crow_bar");
    public static final ResourceLocation ELECTRIC_KATANA = id("electric_katana");
    public static final ResourceLocation CRYSTAL_DAGGER_RED = id("crystal_dagger_red");
    public static final ResourceLocation CRYSTAL_DAGGER_GREEN = id("crystal_dagger_green");
    public static final ResourceLocation CRYSTAL_DAGGER_BLACK = id("crystal_dagger_black");

    private ChangedAddonCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static boolean isSecondaryAbilitySelected(net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance<?> variant,
            net.ltxprogrammer.changed.ability.AbstractAbility<?> ability) {
        return isLoaded() && Boolean.TRUE.equals(invokeOptional("isSecondaryAbilitySelected",
                new Class<?>[]{net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance.class,net.ltxprogrammer.changed.ability.AbstractAbility.class},variant,ability));
    }

    /** Preserve Exp2's native food-taming route before Synergy handles ordinary feeding. */
    public static boolean isNativeTamingFood(ChangedEntity creature, ItemStack stack) {
        return isLoaded() && Boolean.TRUE.equals(invokeOptional("isNativeTamingFood",
                new Class<?>[]{ChangedEntity.class, ItemStack.class}, creature, stack));
    }

    public static double alphaRenderScale(ChangedEntity creature) {
        Object scale = invokeOptional(
                "alphaRenderScale", new Class<?>[]{ChangedEntity.class}, creature);
        return scale instanceof Number number ? number.doubleValue() : 1.0D;
    }

    /** Loads event signatures containing Addon classes only when Addon is present. */
    public static void registerOptionalEvents() {
        if (!isLoaded() || optionalEventsRegistered) {
            return;
        }

        try {
            optionalEventsClass = Class.forName(
                    OPTIONAL_EVENTS, true, ChangedAddonCompat.class.getClassLoader());
            MinecraftForge.EVENT_BUS.register(optionalEventsClass);
            optionalEventsRegistered = true;
            net.parkabird.changedsynergy.ChangedSynergyMod.LOGGER.info(
                    "Enabled Changed Addon pat and universal grab integration");
        } catch (ReflectiveOperationException | LinkageError exception) {
            net.parkabird.changedsynergy.ChangedSynergyMod.LOGGER.error(
                    "Failed to register optional Changed Addon social integration", exception);
        }
    }

    public static Optional<MobEffect> pacifiedEffect() {
        return isLoaded() ? Optional.ofNullable(ForgeRegistries.MOB_EFFECTS.getValue(PACIFIED)) : Optional.empty();
    }

    public static Optional<Item> item(ResourceLocation id) {
        return isLoaded() ? Optional.ofNullable(ForgeRegistries.ITEMS.getValue(id)) : Optional.empty();
    }

    public static boolean is(ItemStack stack, ResourceLocation id) {
        return !stack.isEmpty() && id.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    /** The totem is passive: do not consume it or apply its active-use cooldown. */
    public static boolean hasTransfurTotem(net.minecraft.world.entity.player.Player player) {
        return isLoaded() && (player.getInventory().items.stream().anyMatch(stack -> is(stack, TRANSFUR_TOTEM))
                || is(player.getOffhandItem(), TRANSFUR_TOTEM));
    }

    /** Addon's hazard suit occupies Changed's full-body accessory slot. */
    public static boolean wearsHazardBodySuit(LivingEntity entity) {
        return isLoaded() && AccessorySlots.getForEntity(entity)
                .flatMap(slots -> slots.getItem(ChangedAccessorySlots.FULL_BODY.get()))
                .filter(stack -> is(stack, HAZARD_BODY_SUIT))
                .isPresent();
    }

    /** Stable Synergy values, translated from Addon's native settings. */
    public static int[] bondedMenuState(ChangedEntity pet) {
        Object value = invokeOptional(
                "getBondedMenuState",
                new Class<?>[]{ChangedEntity.class},
                pet);
        return value instanceof int[] state && state.length >= 4
                ? state
                : new int[]{0, 1, 2, 0};
    }

    /** True only when Addon's generic pet mixin actually applies to this entity. */
    public static boolean supportsBondedPetBackend(ChangedEntity pet) {
        return Boolean.TRUE.equals(invokeOptional(
                "supportsBondedPetBackend",
                new Class<?>[]{ChangedEntity.class},
                pet));
    }

    /** Runtime-only exclusions for Addon entities whose boss form shares a normal entity type. */
    public static boolean isSocialSystemExcludedBoss(ChangedEntity creature) {
        return Boolean.TRUE.equals(invokeOptional(
                "isSocialSystemExcludedBoss",
                new Class<?>[]{ChangedEntity.class},
                creature));
    }

    public static void initializeBondedCombatCondition(ChangedEntity pet) {
        invokeOptional(
                "initializeBondedCombatCondition",
                new Class<?>[]{ChangedEntity.class},
                pet);
    }

    public static boolean handleBondedMenuCommand(
            ChangedEntity pet,
            ServerPlayer owner,
            String command) {
        return Boolean.TRUE.equals(invokeOptional(
                "handleBondedMenuCommand",
                new Class<?>[]{ChangedEntity.class, ServerPlayer.class, String.class},
                pet, owner, command));
    }

    public static Container prepareBondedInventory(ChangedEntity pet, ServerPlayer owner) {
        Object value = invokeOptional(
                "prepareBondedInventory",
                new Class<?>[]{ChangedEntity.class, ServerPlayer.class},
                pet, owner);
        return value instanceof Container inventory ? inventory : null;
    }

    public static Container bondedInventory(ChangedEntity pet) {
        Object value = invokeOptional("bondedInventory", new Class<?>[]{ChangedEntity.class}, pet);
        return value instanceof Container inventory ? inventory : null;
    }

    public static void clearBondedFavor(ChangedEntity pet) {
        invokeOptional("clearBondedFavor", new Class<?>[]{ChangedEntity.class}, pet);
    }

    /** Re-selects the real rod/pickaxe after a favor is changed. */
    public static void refreshBondedWorkItem(ChangedEntity pet) {
        invokeOptional("refreshBondedWorkItem", new Class<?>[]{ChangedEntity.class}, pet);
    }

    /** Addon's safe-grab API is mixed into Changed's ability only when Addon is present. */
    public static void configureFriendlyGrab(Object ability, boolean safeMode) {
        configureFriendlyGrab(ability, safeMode, true);
    }

    /** Player modes also own their target permission; NPC scripted grabs manage it separately. */
    public static void configureFriendlyGrab(Object ability, boolean safeMode, boolean allowTransfurred) {
        if (!isLoaded() || ability == null) {
            return;
        }
        try {
            var methods = GRAB_METHODS.get(ability.getClass());
            invokeInstance(ability, methods.allowTransfurred(), allowTransfurred);
            invokeInstance(ability, methods.safeMode(), safeMode);
        } catch (LinkageError | RuntimeException exception) {
            net.parkabird.changedsynergy.ChangedSynergyMod.LOGGER.debug(
                    "Could not resolve optional Changed Addon grab API", exception);
        }
    }

    public static boolean isSafeGrab(Object ability) {
        if (!isLoaded() || ability == null) {
            return false;
        }
        try {
            Method getter = GRAB_METHODS.get(ability.getClass()).readSafeMode();
            return getter != null && Boolean.TRUE.equals(getter.invoke(ability));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    /**
     * Addon's generic Changed-entity pet bridge does not mirror the native dark
     * latex owner's control bit to clients.  Synchronize it through Addon's own
     * packet so the pet is rendered as a suit instead of a separate follower.
     */
    public static void syncFriendlySuitControl(
            ChangedEntity pet,
            ServerPlayer owner,
            boolean ownerHasControl) {
        invokeOptional(
                "syncFriendlySuitControl",
                new Class<?>[]{ChangedEntity.class, ServerPlayer.class, boolean.class},
                pet, owner, ownerHasControl);
    }

    /** Mirrors common controller cooldowns to Addon's private grab AI when present. */
    public static void applyGrabCooldown(ChangedEntity creature, int ticks) {
        invokeOptional("applyGrabCooldown", new Class<?>[]{ChangedEntity.class, int.class}, creature, ticks);
    }

    /** Addon grabbers may store their live ability outside Changed's ability registry. */
    public static GrabEntityAbilityInstance grabAbility(ChangedEntity creature) {
        Object value = invokeOptional("grabAbility", new Class<?>[]{ChangedEntity.class}, creature);
        return value instanceof GrabEntityAbilityInstance ability ? ability : null;
    }

    private static Object invokeOptional(String method, Class<?>[] parameters, Object... arguments) {
        if (!isLoaded()) {
            return null;
        }
        try {
            if (optionalEventsClass == null) {
                optionalEventsClass = Class.forName(
                        OPTIONAL_EVENTS, true, ChangedAddonCompat.class.getClassLoader());
            }
            Method resolved = OPTIONAL_METHODS.computeIfAbsent(method,
                    name -> Optional.ofNullable(findMethod(optionalEventsClass, name, parameters))).orElse(null);
            return resolved == null ? null : resolved.invoke(null, arguments);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            net.parkabird.changedsynergy.ChangedSynergyMod.LOGGER.debug(
                    "Changed Addon bridge method {} was unavailable", method, exception);
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameters) {
        try {
            return type.getMethod(name, parameters);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static void invokeInstance(Object instance, Method method, boolean value) {
        if (method == null) return;
        try {
            method.invoke(instance, value);
        } catch (IllegalAccessException | InvocationTargetException | LinkageError | RuntimeException exception) {
            net.parkabird.changedsynergy.ChangedSynergyMod.LOGGER.debug(
                    "Could not update optional Changed Addon grab state", exception);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
