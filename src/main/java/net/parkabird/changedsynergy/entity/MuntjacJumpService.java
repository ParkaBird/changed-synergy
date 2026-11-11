package net.parkabird.changedsynergy.entity;

import net.ltxprogrammer.changed.ability.GrabEntityAbility;
import net.ltxprogrammer.changed.init.ChangedAbilities;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.BondedSuitService;
import net.parkabird.changedsynergy.ai.TakeoverService;

/** A species passive, including a human controlling a temporary suit variant. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class MuntjacJumpService {
    private MuntjacJumpService() {}

    public static MuntjacJumpState state(Player player) {
        return ((MuntjacJumpAccess)player).changedSynergy$muntjacJump();
    }

    private static boolean chargedJumper(Object body) {
        return body instanceof LatexMuntjac || body instanceof LatexMuntjacFemale || body instanceof LatexJerboa;
    }

    private static Object form(Player player) {
        if (!player.isAlive() || player.isSpectator() || player.isPassenger() || player.isSleeping()
                || player.getAbilities().flying || player.isFallFlying() || player.isInWaterOrBubble()
                || player.isInLava() || player.onClimbable()
                || player instanceof ServerPlayer server && TakeoverService.active(server)) return null;
        var grabber = GrabEntityAbility.getGrabber(player);
        if (grabber != null) {
            var grab = grabber.getAbilityInstance(ChangedAbilities.GRAB_ENTITY_ABILITY.get());
            return chargedJumper(grabber.getChangedEntity()) && grab != null && grab.suited
                    && grab.grabbedEntity == player
                    && (grab.grabbedHasControl || BondedSuitService.getSuitController(grabber.getChangedEntity()) == player)
                    ? grabber.getEntity() : null;
        }
        var variant = ProcessTransfur.getPlayerTransfurVariant(player);
        return variant != null && !variant.isTemporaryFromSuit() && variant.getTransfurProgression(1) >= 1
                && chargedJumper(variant.getChangedEntity()) ? variant : null;
    }

    public static boolean refresh(Player player) {
        var state = state(player);
        state.bind(form(player));
        return state.bound();
    }

    public static float input(Player player, boolean down) {
        if (!refresh(player)) return -1;
        var state = state(player);
        float power = state.input(down, player.tickCount, player.onGround());
        if (power >= 0) state.queueJump(power, player.tickCount);
        return power;
    }

    public static boolean charging(Player player) {
        return state(player).charging() && refresh(player) && state(player).charging();
    }

    public static boolean holding(Player player) {
        return state(player).holding() && refresh(player) && state(player).holding();
    }

    public static float multiplier(Player player) {
        return state(player).bound() && refresh(player) ? state(player).multiplier(player.tickCount) : 1;
    }

    public static void jumped(Player player) {
        if (state(player).bound() && refresh(player))
            state(player).beginFlight(player.getY(), state(player).multiplier(player.tickCount) > 1);
        state(player).consumeJump();
    }

    @SubscribeEvent public static void landed(net.minecraftforge.event.entity.living.LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && state(player).bound() && refresh(player))
            event.setDistance(state(player).landingDistance(event.getDistance(), player.getY()));
    }

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && state(event.player).bound() && refresh(event.player)) {
            state(event.player).tick(event.player.tickCount, event.player.onGround());
            state(event.player).trackFlight(event.player.getY(), event.player.onGround());
        }
    }
}
