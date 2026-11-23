package net.parkabird.changedsynergy.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ability.ManulCrouchRestAbility;

/** Clears WASD/jump while a manul crouch-rest is active so client prediction cannot slide. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, value = Dist.CLIENT)
public final class ManulCrouchRestClient {
    private ManulCrouchRestClient() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void lockInput(MovementInputUpdateEvent event) {
        var player = Minecraft.getInstance().player;
        if (player == null || event.getEntity() != player) {
            return;
        }
        ManulCrouchRestAbility.Instance instance = ManulCrouchRestAbility.instanceOf(player);
        if (instance == null || !instance.isResting()) {
            return;
        }
        Input input = event.getInput();
        input.leftImpulse = 0.0F;
        input.forwardImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        // Keep shift so wantRest() stays true while rooted.
    }
}
