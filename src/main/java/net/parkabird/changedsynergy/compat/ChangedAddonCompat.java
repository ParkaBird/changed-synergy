package net.parkabird.changedsynergy.compat;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import net.ltxprogrammer.changed.entity.ChangedEntity;
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
    private static Class<?> optionalEventsClass;
    public static final String MOD_ID = "changed_addon";
    public static final ResourceLocation PACIFIED = id("pacified");
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

    /** target type, attack type, attack condition and favor ordinals. */
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

    public static void clearBondedFavor(ChangedEntity pet) {
        invokeOptional("clearBondedFavor", new Class<?>[]{ChangedEntity.class}, pet);
    }

    /** Re-selects the real rod/pickaxe after a favor is changed. */
    public static void refreshBondedWorkItem(ChangedEntity pet) {
        invokeOptional("refreshBondedWorkItem", new Class<?>[]{ChangedEntity.class}, pet);
    }

    public static boolean allowsBondedDefense(ChangedEntity pet, LivingEntity target) {
        Object value = invokeOptional(
                "allowsBondedDefense",
                new Class<?>[]{ChangedEntity.class, LivingEntity.class},
                pet, target);
        return !(value instanceof Boolean allowed) || allowed;
    }

    /** True when a generic bonded Addon pet is configured for physical kills. */
    public static boolean suppressBondedTransfurAttack(ChangedEntity pet) {
        return Boolean.TRUE.equals(invokeOptional(
                "suppressBondedTransfurAttack",
                new Class<?>[]{ChangedEntity.class},
                pet));
    }

    /** Keeps movement helpers from pulling an entity while Addon owns an active grab. */
    public static boolean isGrabberBusy(ChangedEntity entity) {
        return Boolean.TRUE.equals(invokeOptional(
                "isGrabberBusy",
                new Class<?>[]{ChangedEntity.class},
                entity));
    }

    /**
     * Starts Addon's arm-grab presentation as a timed, harmless social hug.
     * The optional implementation owns release and safety cleanup.
     */
    public static boolean tryStartSocialHug(
            ChangedEntity creature,
            ServerPlayer player,
            int durationTicks) {
        return Boolean.TRUE.equals(invokeOptional(
                "tryStartSocialHug",
                new Class<?>[]{
                        ChangedEntity.class,
                        ServerPlayer.class,
                        int.class
                },
                creature, player, durationTicks));
    }

    /** Starts the scripted, no-QTE arm hold used after a successful negotiation. */
    public static boolean tryStartNegotiatedRelease(
            ChangedEntity creature,
            ServerPlayer player,
            int durationTicks) {
        return Boolean.TRUE.equals(invokeOptional(
                "tryStartNegotiatedRelease",
                new Class<?>[]{
                        ChangedEntity.class,
                        ServerPlayer.class,
                        int.class
                },
                creature, player, durationTicks));
    }

    /** Returns an enemy pinned by this player's bonded or trusted creature. */
    public static Optional<LivingEntity> socialCombatGrabTarget(
            ChangedEntity creature,
            ServerPlayer player) {
        Object value = invokeOptional(
                "getSocialCombatGrabTarget",
                new Class<?>[]{ChangedEntity.class, ServerPlayer.class},
                creature, player);
        return value instanceof LivingEntity target
                ? Optional.of(target)
                : Optional.empty();
    }

    /** Addon's safe-grab API is mixed into Changed's ability only when Addon is present. */
    public static void configureFriendlyGrab(Object ability, boolean safeMode) {
        if (!isLoaded() || ability == null) {
            return;
        }
        invokeInstance(ability, "setAllowGrabTransfurred", true);
        invokeInstance(ability, "setSafeModeAuthoritative", safeMode);
    }

    public static boolean isSafeGrab(Object ability) {
        if (!isLoaded() || ability == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(ability.getClass().getMethod("isSafeMode").invoke(ability));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
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

    private static Object invokeOptional(String method, Class<?>[] parameters, Object... arguments) {
        if (!isLoaded()) {
            return null;
        }
        try {
            if (optionalEventsClass == null) {
                optionalEventsClass = Class.forName(
                        OPTIONAL_EVENTS, true, ChangedAddonCompat.class.getClassLoader());
            }
            return optionalEventsClass.getMethod(method, parameters).invoke(null, arguments);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            net.parkabird.changedsynergy.ChangedSynergyMod.LOGGER.debug(
                    "Changed Addon bridge method {} was unavailable", method, exception);
            return null;
        }
    }

    private static void invokeInstance(Object instance, String method, boolean value) {
        try {
            instance.getClass().getMethod(method, boolean.class).invoke(instance, value);
        } catch (NoSuchMethodException ignored) {
            // Running without the optional mixin is a supported configuration.
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            net.parkabird.changedsynergy.ChangedSynergyMod.LOGGER.debug(
                    "Could not update optional Changed Addon grab state", exception);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
