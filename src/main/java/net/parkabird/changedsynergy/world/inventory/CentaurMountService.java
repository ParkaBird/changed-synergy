package net.parkabird.changedsynergy.world.inventory;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.data.AccessorySlots;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.beast.LatexTaur;
import net.ltxprogrammer.changed.init.ChangedAttributes;
import net.ltxprogrammer.changed.init.ChangedAccessorySlots;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.CreaturePersonality;
import net.parkabird.changedsynergy.ai.CreaturePersonality.RelationshipTier;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

/** Bridges Synergy relationships to Changed's native taur saddle and pack slots. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CentaurMountService {
    private static final double HORSE_RIDDEN_SPEED = 0.225D;
    private static final double HORSE_MAX_RIDDEN_SPEED = 0.3375D;
    private static final double HORSE_JUMP_STRENGTH = 0.7D;
    private static final Map<ChangedEntity, Float> CHARGED_JUMPS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private CentaurMountService() {
    }

    public static boolean isCentaur(Entity entity) {
        if (!(entity instanceof ChangedEntity)
                || !(entity instanceof LatexTaur<?>)) {
            return false;
        }
        ResourceLocation type = ForgeRegistries.ENTITY_TYPES
                .getKey(entity.getType());
        // The headless knight happens to share Changed's taur base class, but
        // it is not a rideable double-bodied species in Synergy.
        return type == null || !"headless_knight".equals(type.getPath());
    }

    public static boolean hasSaddle(ChangedEntity centaur) {
        return centaur instanceof Saddleable saddleable && saddleable.isSaddled();
    }

    public static boolean hasChest(ChangedEntity centaur) {
        return AccessorySlots.getForEntity(centaur)
                .flatMap(slots -> slots.getItem(ChangedAccessorySlots.LOWER_BODY_SIDE.get()))
                .filter(stack -> stack.is(Items.CHEST))
                .isPresent();
    }

    public static boolean hasAllowedRelationship(
            ChangedEntity centaur,
            ServerPlayer player) {
        if (!isCentaur(centaur)
                || !centaur.isAlive()
                || centaur.isRemoved()
                || centaur.level() != player.level()
                || LatexSocialMemory.isProvoked(centaur, player)
                || LatexSocialMemory.hasRelationshipBetrayal(centaur, player)) {
            return false;
        }
        if (LatexSocialMemory.isPetOwner(centaur, player)
                || LatexSocialMemory.isBonded(centaur, player)) {
            return true;
        }
        return CreaturePersonality.hasTrustedRelationship(centaur, player)
                && CreaturePersonality.relationshipTier(centaur, player)
                        == RelationshipTier.CLOSE;
    }

    public static boolean isDefaultState(ChangedEntity centaur) {
        return centaur.getTarget() == null
                && !centaur.isAggressive()
                && !centaur.isPassenger()
                && !centaur.isVehicle();
    }

    public static boolean canConfigure(
            ServerPlayer player,
            ChangedEntity centaur) {
        return hasAllowedRelationship(centaur, player)
                && player.distanceToSqr(centaur) <= 64.0D
                && (isDefaultState(centaur)
                        || centaur.getFirstPassenger() == player);
    }

    public static boolean canAccessCargo(Player player, ChangedEntity centaur) {
        if (!hasChest(centaur)
                || !centaur.isAlive()
                || centaur.isRemoved()
                || centaur.level() != player.level()
                || player.distanceToSqr(centaur) > 64.0D) {
            return false;
        }
        return player.level().isClientSide
                || player instanceof ServerPlayer serverPlayer
                        && hasAllowedRelationship(centaur, serverPlayer);
    }

    public static boolean openConfiguration(
            ServerPlayer player,
            ChangedEntity centaur) {
        if (!canConfigure(player, centaur)) {
            return false;
        }
        NetworkHooks.openScreen(
                player,
                new SimpleMenuProvider(
                        (id, inventory, viewer) -> new CentaurMountConfigMenu(
                                id, inventory, centaur),
                        centaur.getDisplayName()),
                buffer -> CentaurMountConfigMenu.writeOpenData(buffer, centaur));
        NpcDialogue.trigger(centaur, player, Cue.CENTAUR_TACK_OPEN);
        return true;
    }

    public static boolean openCargo(
            ServerPlayer player,
            ChangedEntity centaur) {
        if (!canAccessCargo(player, centaur)) {
            return false;
        }
        CentaurMountCargo cargo = new CentaurMountCargo(centaur);
        NetworkHooks.openScreen(
                player,
                new SimpleMenuProvider(
                        (id, inventory, viewer) -> new ChestMenu(
                                MenuType.GENERIC_9x2,
                                id,
                                inventory,
                                cargo,
                                2),
                        centaur.getDisplayName()));
        return true;
    }

    public static boolean mount(
            ServerPlayer player,
            ChangedEntity centaur) {
        if (!hasAllowedRelationship(centaur, player)
                || player.distanceToSqr(centaur) > 64.0D
                || !hasSaddle(centaur)
                || centaur.isVehicle()
                || centaur.isPassenger()) {
            return false;
        }
        centaur.setTarget(null);
        centaur.setAggressive(false);
        centaur.getNavigation().stop();
        player.closeContainer();
        player.setYRot(centaur.getYRot());
        player.setXRot(centaur.getXRot());
        boolean mounted = player.startRiding(centaur, true);
        if (mounted) {
            centaur.level().playSound(
                    null,
                    centaur,
                    SoundEvents.HORSE_SADDLE,
                    SoundSource.NEUTRAL,
                    0.55F,
                    1.0F);
            NpcDialogue.trigger(centaur, player, Cue.CENTAUR_RIDE_START);
        }
        return mounted;
    }

    public static float riddenSpeed(ChangedEntity centaur) {
        double base = Math.max(
                0.001D,
                centaur.getAttributeBaseValue(Attributes.MOVEMENT_SPEED));
        double attributeScale = centaur.getAttributeValue(
                Attributes.MOVEMENT_SPEED) / base;
        return (float)Mth.clamp(
                HORSE_RIDDEN_SPEED * attributeScale,
                0.05D,
                HORSE_MAX_RIDDEN_SPEED);
    }

    public static boolean canUseHorseJump(ChangedEntity centaur) {
        return isCentaur(centaur)
                && hasSaddle(centaur)
                && centaur.getFirstPassenger() instanceof Player;
    }

    public static void queueChargedJump(
            ChangedEntity centaur,
            int charge) {
        if (!centaur.level().isClientSide || !canUseHorseJump(centaur)) {
            return;
        }
        int safeCharge = Mth.clamp(charge, 0, 100);
        float scale = safeCharge >= 90
                ? 1.0F
                : 0.4F + 0.4F * safeCharge / 90.0F;
        CHARGED_JUMPS.put(centaur, scale);
    }

    @Nullable
    public static Float consumeChargedJump(ChangedEntity centaur) {
        return CHARGED_JUMPS.remove(centaur);
    }

    public static double riddenJumpStrength(ChangedEntity centaur) {
        var attribute = centaur.getAttribute(
                ChangedAttributes.JUMP_STRENGTH.get());
        if (attribute == null) {
            return HORSE_JUMP_STRENGTH;
        }
        double base = Math.max(0.001D, attribute.getBaseValue());
        return HORSE_JUMP_STRENGTH * attribute.getValue() / base;
    }

    @SubscribeEvent
    public static void onDismount(EntityMountEvent event) {
        if (event.isMounting()
                || !(event.getEntityMounting() instanceof ServerPlayer player)
                || !(event.getEntityBeingMounted() instanceof ChangedEntity centaur)
                || !isCentaur(centaur)
                || centaur.level().isClientSide
                || !hasAllowedRelationship(centaur, player)) {
            return;
        }
        NpcDialogue.trigger(centaur, player, Cue.CENTAUR_RIDE_END);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ChangedEntity centaur)
                || !isCentaur(centaur)) {
            return;
        }
        Entity passenger = centaur.getFirstPassenger();
        if (!(passenger instanceof Player rider)) {
            CHARGED_JUMPS.remove(centaur);
            return;
        }
        if (!centaur.level().isClientSide
                && (!(rider instanceof ServerPlayer serverPlayer)
                        || !hasSaddle(centaur)
                        || !hasAllowedRelationship(centaur, serverPlayer))) {
            rider.stopRiding();
            CHARGED_JUMPS.remove(centaur);
            return;
        }
        centaur.setTarget(null);
        centaur.setAggressive(false);
        centaur.getNavigation().stop();
    }

    @SubscribeEvent
    public static void onCentaurDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ChangedEntity centaur)
                || !isCentaur(centaur)
                || centaur.level().isClientSide) {
            return;
        }
        new CentaurMountCargo(centaur).dropContents();
        CHARGED_JUMPS.remove(centaur);
    }
}
