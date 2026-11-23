package net.parkabird.changedsynergy.entity;

import java.util.UUID;
import net.ltxprogrammer.changed.ability.GrabEntityAbility;
import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.ltxprogrammer.changed.init.ChangedAbilities;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ability.GrizzlyClawsAbility;
import net.parkabird.changedsynergy.ai.BondedSuitService;
import net.parkabird.changedsynergy.ai.TakeoverService;

/** Grants iron-tier bare-handed combat and block breaking to Grizzly hosts/controllers, at an exhaustion cost while mining. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class GrizzlyClawsService {
    public static final UUID BARE_ATTACK_MODIFIER_UUID = UUID.fromString("e6f8c6d2-7f9b-4c5a-9b3e-6a8f1d4e9b20");
    public static final String BARE_ATTACK_MODIFIER_NAME = "changed_synergy:grizzly_bare_claws";
    public static final double BARE_ATTACK_BONUS = 5.0D; // 1.0 base + 5.0 = 6.0 (iron sword tier)
    public static final float IRON_BREAK_MULTIPLIER = Tiers.IRON.getSpeed();

    private GrizzlyClawsService() {}

    public static boolean isActiveGrizzly(Player player) {
        if (player == null || !player.isAlive() || player.isSpectator()
                || player instanceof ServerPlayer server && TakeoverService.active(server)) {
            return false;
        }
        var grabber = GrabEntityAbility.getGrabber(player);
        if (grabber != null) {
            var grab = grabber.getAbilityInstance(ChangedAbilities.GRAB_ENTITY_ABILITY.get());
            return grabber.getChangedEntity() instanceof LatexGrizzly && grab != null && grab.suited
                    && grab.grabbedEntity == player
                    && (grab.grabbedHasControl || BondedSuitService.getSuitController(grabber.getChangedEntity()) == player);
        }
        var variant = ProcessTransfur.getPlayerTransfurVariant(player);
        return variant != null && !variant.isTemporaryFromSuit() && variant.getTransfurProgression(1) >= 1
                && variant.getChangedEntity() instanceof LatexGrizzly;
    }

    public static boolean clawsEnabled(Player player) {
        if (player == null) return false;
        var grabber = GrabEntityAbility.getGrabber(player);
        if (grabber != null) {
            var grab = grabber.getAbilityInstance(ChangedAbilities.GRAB_ENTITY_ABILITY.get());
            if (grabber.getChangedEntity() instanceof LatexGrizzly && grab != null && grab.suited
                    && grab.grabbedEntity == player
                    && (grab.grabbedHasControl || BondedSuitService.getSuitController(grabber.getChangedEntity()) == player)) {
                return GrizzlyClawsAbility.isEnabled(grabber);
            }
        }
        var variant = ProcessTransfur.getPlayerTransfurVariant(player);
        return variant != null
                && GrizzlyClawsAbility.isEnabled(net.ltxprogrammer.changed.ability.IAbstractChangedEntity.forPlayerWithVariant(player, variant));
    }

    public static boolean isBareHandedGrizzly(Player player) {
        return isActiveGrizzly(player) && player.getMainHandItem().isEmpty() && clawsEnabled(player);
    }

    public static void refreshModifier(Player player) {
        syncAttackModifier(player);
    }

    public static float bareHandBreakSpeed(float originalSpeed, float hardness) {
        return hardness >= 0.0F ? originalSpeed * IRON_BREAK_MULTIPLIER : originalSpeed;
    }

    public static float exhaustionForBreak(float hardness) {
        if (hardness <= 0.0F) return 0.0F;
        return Mth.clamp(0.35F + hardness * 0.08F, 0.35F, 1.0F);
    }

    private static void syncAttackModifier(Player player) {
        if (player.level().isClientSide()) return;
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack == null) return;
        boolean apply = isBareHandedGrizzly(player);
        AttributeModifier existing = attack.getModifier(BARE_ATTACK_MODIFIER_UUID);
        if (apply) {
            if (existing == null || existing.getAmount() != BARE_ATTACK_BONUS) {
                if (existing != null) attack.removeModifier(BARE_ATTACK_MODIFIER_UUID);
                attack.addTransientModifier(new AttributeModifier(
                        BARE_ATTACK_MODIFIER_UUID, BARE_ATTACK_MODIFIER_NAME,
                        BARE_ATTACK_BONUS, AttributeModifier.Operation.ADDITION));
            }
        } else if (existing != null) {
            attack.removeModifier(BARE_ATTACK_MODIFIER_UUID);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            syncAttackModifier(event.player);
        }
    }

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof Player player && event.getSlot() == EquipmentSlot.MAINHAND) {
            syncAttackModifier(player);
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (!isBareHandedGrizzly(player)) return;
        BlockState state = event.getState();
        float hardness = state.getDestroySpeed(player.level(), event.getPosition().orElse(BlockPos.ZERO));
        if (hardness < 0.0F) return;
        event.setNewSpeed(Math.max(event.getNewSpeed(), bareHandBreakSpeed(event.getOriginalSpeed(), hardness)));
    }

    @SubscribeEvent
    public static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        Player player = event.getEntity();
        if (!isBareHandedGrizzly(player)) return;
        BlockState state = event.getTargetBlock();
        if (state.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) >= 0.0F
                && TierSortingRegistry.isCorrectTierForDrops(Tiers.IRON, state)) {
            event.setCanHarvest(true);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide() || player.isCreative() || player.isSpectator()) return;
        if (!isBareHandedGrizzly(player)) return;
        float exhaustion = exhaustionForBreak(event.getState().getDestroySpeed(event.getLevel(), event.getPos()));
        if (exhaustion > 0.0F) {
            player.causeFoodExhaustion(exhaustion);
        }
    }
}
