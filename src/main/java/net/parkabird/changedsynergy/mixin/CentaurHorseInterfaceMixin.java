package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.parkabird.changedsynergy.world.inventory.CentaurMountService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

/** Exposes vanilla horse jump charging and the mounted inventory shortcut. */
@Mixin(value = ChangedEntity.class, remap = false)
public abstract class CentaurHorseInterfaceMixin
        implements PlayerRideableJumping, HasCustomInventoryScreen {
    private ChangedEntity changedSynergy$self() {
        return (ChangedEntity)(Object)this;
    }

    @Override
    public void onPlayerJump(int charge) {
        CentaurMountService.queueChargedJump(changedSynergy$self(), charge);
    }

    @Override
    public boolean canJump() {
        return CentaurMountService.canUseHorseJump(changedSynergy$self());
    }

    @Override
    public void handleStartJump(int charge) {
        ChangedEntity centaur = changedSynergy$self();
        if (CentaurMountService.canUseHorseJump(centaur)) {
            centaur.playSound(SoundEvents.HORSE_JUMP, 0.4F, 1.0F);
        }
    }

    @Override
    public void handleStopJump() {
    }

    @Override
    public void openCustomInventoryScreen(Player player) {
        ChangedEntity centaur = changedSynergy$self();
        if (player instanceof ServerPlayer serverPlayer
                && centaur.getFirstPassenger() == player) {
            CentaurMountService.openConfiguration(serverPlayer, centaur);
        }
    }
}
