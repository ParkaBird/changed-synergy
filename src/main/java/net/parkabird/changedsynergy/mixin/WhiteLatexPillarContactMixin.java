package net.parkabird.changedsynergy.mixin;

import java.util.Optional;
import net.ltxprogrammer.changed.block.WhiteLatexPillar;
import net.ltxprogrammer.changed.block.WhiteLatexTransportInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.event.TickEvent;
import net.parkabird.changedsynergy.event.FactionReputationEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restores side contact for extended pillars and applies reputation first. */
@Mixin(value = WhiteLatexTransportInterface.EventSubscriber.class, remap = false)
public abstract class WhiteLatexPillarContactMixin {
    private static final double CONTACT_MARGIN = 0.04D;

    @Inject(
            method = "onPlayerTick",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void changedSynergy$protectRespectedPlayers(
            TickEvent.PlayerTickEvent event,
            CallbackInfo callback) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        contactPosition(player).ifPresent(position -> {
            if (FactionReputationEvents.shouldBlockWhiteLatexEntry(
                    player, position)) {
                callback.cancel();
            }
        });
    }

    @Inject(method = "onPlayerTick", at = @At("TAIL"), remap = false)
    private static void changedSynergy$handlePillarSideContact(
            TickEvent.PlayerTickEvent event,
            CallbackInfo callback) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)
                || WhiteLatexTransportInterface.isEntityInWhiteLatex(player)
                || WhiteLatexTransportInterface
                        .isBoundingBoxInWhiteLatex(player).isPresent()) {
            return;
        }
        touchingExtendedPillar(player).ifPresent(position -> {
            if (!FactionReputationEvents.shouldBlockWhiteLatexEntry(
                    player, position)) {
                WhiteLatexTransportInterface.entityEnterLatex(
                        player, position);
            }
        });
    }

    private static Optional<BlockPos> contactPosition(ServerPlayer player) {
        Optional<BlockPos> inside = WhiteLatexTransportInterface
                .isBoundingBoxInWhiteLatex(player);
        return inside.isPresent() ? inside : touchingExtendedPillar(player);
    }

    private static Optional<BlockPos> touchingExtendedPillar(
            ServerPlayer player) {
        AABB contactBox = player.getBoundingBox().inflate(CONTACT_MARGIN);
        for (BlockPos candidate : BlockPos.betweenClosed(
                BlockPos.containing(
                        contactBox.minX, contactBox.minY, contactBox.minZ),
                BlockPos.containing(
                        contactBox.maxX, contactBox.maxY, contactBox.maxZ))) {
            var state = player.level().getBlockState(candidate);
            if (!(state.getBlock() instanceof WhiteLatexPillar)
                    || !state.getValue(WhiteLatexPillar.EXTENDED)) {
                continue;
            }
            boolean touching = state.getCollisionShape(
                            player.level(), candidate,
                            CollisionContext.of(player))
                    .toAabbs().stream()
                    .map(shape -> shape.move(
                            candidate.getX(),
                            candidate.getY(),
                            candidate.getZ()))
                    .anyMatch(contactBox::intersects);
            if (touching) {
                return Optional.of(candidate.immutable());
            }
        }
        return Optional.empty();
    }
}
