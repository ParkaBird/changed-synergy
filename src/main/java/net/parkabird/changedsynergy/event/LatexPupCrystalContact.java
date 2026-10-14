package net.parkabird.changedsynergy.event;

import net.ltxprogrammer.changed.block.LatexPupCrystal;
import net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;

/** Restores contact with the crystal's original interaction shape. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class LatexPupCrystalContact {
    private static final double CONTACT_MARGIN = 0.025D;
    private static final String LAST_CONTACT_TICK = "SynergyPupCrystalContactTick";

    private LatexPupCrystalContact() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)
                || !eligible(player)) {
            return;
        }
        AABB box = player.getBoundingBox().inflate(CONTACT_MARGIN);
        for (BlockPos position : BlockPos.betweenClosed(
                BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            BlockState state = player.level().getBlockState(position);
            if (touch(player, position, state)) {
                return;
            }
        }
    }

    public static boolean eligible(ServerPlayer player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator()
                && !ProcessTransfur.isPlayerTransfurred(player);
    }

    /** Reuses Changed's original decision and its six-pup completion callback. */
    public static boolean touch(
            ServerPlayer player, BlockPos position, BlockState state) {
        if (!eligible(player)
                || !(state.getBlock() instanceof LatexPupCrystal crystal)
                || !(crystal instanceof DecisionAccess access)
                // Changed registers this block without collision. Its visible
                // interaction shape, also used by the original hazard, is valid.
                || !state.getInteractionShape(player.level(), position)
                        .toAabbs().stream()
                        .map(shape -> shape.move(position.getX(),
                                position.getY(), position.getZ()))
                        .anyMatch(player.getBoundingBox()
                                .inflate(CONTACT_MARGIN)::intersects)) {
            return false;
        }
        long now = player.level().getGameTime();
        var data = player.getPersistentData();
        if (data.contains(LAST_CONTACT_TICK, Tag.TAG_LONG)
                && data.getLong(LAST_CONTACT_TICK) == now) {
            return true;
        }
        data.putLong(LAST_CONTACT_TICK, now);
        player.makeStuckInBlock(state, new Vec3(0.8D, 0.75D, 0.8D));
        crystal.extend(state, player.level(), position);
        // Going through ProcessTransfur keeps armour and faction immunity intact.
        ProcessTransfur.progressTransfur(player,
                access.changedSynergy$crystalDecision(player));
        return true;
    }

    public interface DecisionAccess {
        LatexAssimilationDecision<?> changedSynergy$crystalDecision(
                LivingEntity target);
    }
}
