package net.parkabird.changedsynergy.mixin;

import java.util.List;
import net.ltxprogrammer.changed.ability.AbstractAbility;
import net.ltxprogrammer.changed.client.gui.AbilityRadialScreen;
import net.ltxprogrammer.changed.util.SingleRunnable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.client.AbsorptionNegotiationClientState;
import net.parkabird.changedsynergy.client.RadialWheelAnimations;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.OpenAbsorptionNegotiationPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds one server-authorized negotiation action without altering Changed abilities. */
@Mixin(value = AbilityRadialScreen.class, remap = false)
public abstract class AbilityRadialNegotiationMixin {
    private static final ResourceLocation NEGOTIATION_ICON =
            ResourceLocation.fromNamespaceAndPath(
                    ChangedSynergyMod.MOD_ID,
                    "textures/gui/radial/icons/emote/confused_gray.png");

    @Shadow(remap = false)
    @Final
    public List<AbstractAbility<?>> abilities;

    @Inject(method = "getCount", at = @At("RETURN"), cancellable = true, remap = false)
    private void changedSynergy$appendNegotiationSlot(
            CallbackInfoReturnable<Integer> callback) {
        if (changedSynergy$hasNegotiationSlot()) {
            callback.setReturnValue(abilities.size() + 1);
        }
    }

    @Inject(method = "tooltipsFor", at = @At("HEAD"), cancellable = true, remap = false)
    private void changedSynergy$negotiationTooltip(
            int section,
            CallbackInfoReturnable<List<Component>> callback) {
        if (changedSynergy$isNegotiationSection(section)) {
            callback.setReturnValue(List.of(
                    Component.translatable(
                            "menu.changed_synergy.relationship.action.absorption_negotiation"),
                    Component.translatable(
                            "menu.changed_synergy.relationship.action.absorption_negotiation.hint")));
        }
    }

    @Inject(
            method = "renderSectionForeground",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void changedSynergy$renderNegotiationIcon(
            GuiGraphics graphics,
            int section,
            double x,
            double y,
            float partialTicks,
            int mouseX,
            int mouseY,
            float red,
            float green,
            float blue,
            float alpha,
            CallbackInfo callback) {
        if (!changedSynergy$isNegotiationSection(section)) {
            return;
        }
        AbilityRadialScreen screen = (AbilityRadialScreen)(Object)this;
        int centerX = (int)x + screen.width / 2;
        int centerY = (int)y + screen.height / 2;
        graphics.setColor(0.0F, 0.0F, 0.0F, 0.45F * alpha);
        graphics.blit(NEGOTIATION_ICON, centerX - 15, centerY - 15,
                0.0F, 0.0F, 32, 32, 32, 32);
        graphics.setColor(red, green, blue, alpha);
        graphics.blit(NEGOTIATION_ICON, centerX - 16, centerY - 16,
                0.0F, 0.0F, 32, 32, 32, 32);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        callback.cancel();
    }

    @Inject(method = "handleClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private void changedSynergy$openNegotiation(
            int section,
            SingleRunnable close,
            CallbackInfoReturnable<Boolean> callback) {
        if (!changedSynergy$isNegotiationSection(section)) {
            return;
        }
        AbilityRadialScreen screen = (AbilityRadialScreen)(Object)this;
        Runnable open = () -> {
            close.run();
            ChangedSynergyNetwork.CHANNEL.sendToServer(
                    new OpenAbsorptionNegotiationPacket());
        };
        if (!RadialWheelAnimations.requestNegotiationSwitch(screen, open)) {
            open.run();
        }
        callback.setReturnValue(false);
    }

    @Inject(method = "isSelected", at = @At("HEAD"), cancellable = true, remap = false)
    private void changedSynergy$negotiationIsNotAnAbility(
            int section,
            CallbackInfoReturnable<Boolean> callback) {
        if (changedSynergy$isNegotiationSection(section)) {
            callback.setReturnValue(false);
        }
    }

    private boolean changedSynergy$hasNegotiationSlot() {
        return abilities.size() < 8
                && AbsorptionNegotiationClientState.isActive();
    }

    private boolean changedSynergy$isNegotiationSection(int section) {
        return changedSynergy$hasNegotiationSlot()
                && section == abilities.size();
    }
}
