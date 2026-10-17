package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.LivingEntityDataExtension;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.parkabird.changedsynergy.ai.BondedSuitService;

/** Real rockets must survive contact with their own wrapping carrier on every boost. */
final class WrappedFlightRegression {
    private static net.ltxprogrammer.changed.entity.ChangedEntity clientCarrier;
    private static Vec3 returnPosition;
    private static int clientTicks;
    private static volatile boolean shortRocket;

    static boolean clientTick(net.minecraft.client.Minecraft mc) throws Exception {
        if (clientTicks >= 125) return true;
        if (clientTicks++ == 0) {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.EntityLeaveLevelEvent event) -> {
                if (event.getEntity() instanceof FireworkRocketEntity rocket && !event.getLevel().isClientSide()) {
                    shortRocket |= rocket.tickCount < 10;
                    System.out.println("WRAPPED_ROCKET_END: ticks=" + rocket.tickCount);
                }
            });
            mc.getSingleplayerServer().submit(() -> {
                var owner = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                returnPosition = owner.position();
                clientCarrier = ChangedEntities.LATEX_RED_DRAGON.get().create(owner.level());
                clientCarrier.setPos(owner.position().add(1, 0, 0));
                owner.serverLevel().addFreshEntity(clientCarrier);
                net.parkabird.changedsynergy.ai.CreaturePersonality.establishRelationship(clientCarrier, owner);
                net.parkabird.changedsynergy.ai.LatexSocialMemory.addBond(clientCarrier, owner);
                if (!BondedSuitService.suitOwner(clientCarrier, owner, BondedSuitService.SuitReason.MANUAL))
                    throw new AssertionError("Actual client bonded red-dragon suit failed");
                owner.teleportTo(owner.serverLevel(), owner.getX(), 200, owner.getZ(), 0, 0);
                clientCarrier.setPos(owner.position());
                owner.getAbilities().invulnerable = true;
                owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.FIREWORK_ROCKET, 10));
                owner.inventoryMenu.broadcastChanges();
            }).get();
        }
        if (clientTicks < 25) return false;
        if (clientTicks == 25) {
            System.out.println("WRAPPED_FORM: " + net.ltxprogrammer.changed.process.ProcessTransfur.getPlayerTransfurVariantSafe(mc.player)
                    .map(v -> v.getFormId() + "; glide=" + v.canElytraGlide() + "; temp=" + v.isTemporaryFromSuit()));
            mc.player.getAbilities().flying = false;
            mc.player.setOnGround(false);
            mc.player.setYRot(0); mc.player.setXRot(-10);
            mc.player.startFallFlying();
            mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,
                    net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
        }
        if (clientTicks == 28 || clientTicks == 65 || clientTicks == 102) {
            System.out.println("WRAPPED_CLIENT_USE: tick=" + clientTicks + "; item=" + mc.player.getMainHandItem()
                    + "; flight=" + mc.player.isFallFlying());
            if (mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND)
                    != net.minecraft.world.InteractionResult.SUCCESS) throw new AssertionError("Actual wrapped rocket use rejected");
        }
        if (clientTicks == 35 || clientTicks == 72 || clientTicks == 109) {
            if (!mc.player.isFallFlying() || mc.player.getDeltaMovement().horizontalDistance() < 1.35)
                throw new AssertionError("Repeated red-dragon boost lost propulsion: " + mc.player.getDeltaMovement());
            System.out.println("WRAPPED_RED_DRAGON_BOOST: tick=" + clientTicks + "; motion=" + mc.player.getDeltaMovement());
        }
        if (clientTicks < 125) return false;
        if (shortRocket || mc.player.getMainHandItem().getCount() != 7)
            throw new AssertionError("Boost rockets must last normally and consume exactly three items");
        mc.getSingleplayerServer().submit(() -> {
            var owner = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            owner.setOnGround(true);
            BondedSuitService.releaseOwner(clientCarrier, owner, BondedSuitService.ReleaseReason.MANUAL);
            clientCarrier.discard();
            owner.stopFallFlying();
            owner.setDeltaMovement(Vec3.ZERO);
            owner.getAbilities().invulnerable = false;
            owner.teleportTo(owner.serverLevel(), returnPosition.x, returnPosition.y, returnPosition.z, 0, 0);
            owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            owner.inventoryMenu.broadcastChanges();
        }).get();
        System.out.println("WRAPPED_RED_DRAGON_REPEATED_BOOST_PASS");
        return true;
    }

    static void run(MinecraftServer server) {
        var level = server.overworld();
        var player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "FlightCheck"));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player);
        var carrier = ChangedEntities.DARK_LATEX_YUFENG.get().create(level);
        var base = server.getPlayerList().getPlayers().get(0).position().add(120, 100, 120);
        player.setPos(base); carrier.setPos(base);
        player.setOnGround(false); carrier.setOnGround(false);
        level.addFreshEntity(carrier);
        BondedSuitService.ensureGrabAbility(carrier);
        var grab = BondedSuitService.ability(carrier);
        try {
            if (!grab.suitEntity(player)) throw new AssertionError("Real flying suit did not start");
            grab.grabbedHasControl = true;
            for (int boost = 0; boost < 3; boost++) {
                player.setDeltaMovement(new Vec3(0, 0, .2));
                player.setYRot(0); player.setXRot(-15); player.startFallFlying();
                var rocket = new FireworkRocketEntity(level, new ItemStack(Items.FIREWORK_ROCKET), player);
                level.addFreshEntity(rocket);
                try {
                    var wallHit = new net.minecraftforge.event.entity.ProjectileImpactEvent(rocket,
                            new net.minecraft.world.phys.BlockHitResult(player.position(), net.minecraft.core.Direction.UP,
                                    player.blockPosition(), false));
                    net.parkabird.changedsynergy.event.WrappedFireworkEvents.onImpact(wallHit);
                    var other = ChangedEntities.LATEX_RED_DRAGON.get().create(level);
                    var unrelatedHit = new net.minecraftforge.event.entity.ProjectileImpactEvent(rocket,
                            new net.minecraft.world.phys.EntityHitResult(other));
                    net.parkabird.changedsynergy.event.WrappedFireworkEvents.onImpact(unrelatedHit);
                    if (wallHit.getImpactResult() != net.minecraftforge.event.entity.ProjectileImpactEvent.ImpactResult.DEFAULT
                            || unrelatedHit.getImpactResult() != net.minecraftforge.event.entity.ProjectileImpactEvent.ImpactResult.DEFAULT)
                        throw new AssertionError("Wrapped rocket protection altered wall or unrelated-creature impacts");
                    for (int tick = 0; tick < 8; tick++) {
                        grab.tickIdle();
                        // Addon can leave the world carrier collidable between
                        // hold updates even though it occupies the host's body.
                        carrier.noPhysics = false;
                        rocket.tick();
                        if (rocket.isRemoved()) throw new AssertionError("Boost rocket hit its own wrapping carrier");
                    }
                    if (player.getDeltaMovement().length() < 1) throw new AssertionError("Repeated wrapped boost lost propulsion");
                } finally { rocket.discard(); }
            }
            System.out.println("WRAPPED_FIREWORK_FLIGHT_PASS");
        } finally {
            grab.releaseEntity(false);
            ((LivingEntityDataExtension)player).setGrabbedBy(null);
            carrier.discard(); player.discard();
        }
    }
}
