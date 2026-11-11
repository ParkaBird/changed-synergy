package net.parkabird.changedsynergy.mixin;

import com.mojang.math.Axis;
import java.util.Optional;
import net.ltxprogrammer.changed.client.gui.AbstractRadialScreen;
import net.ltxprogrammer.changed.client.gui.AbilityRadialScreen;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedTags;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.ltxprogrammer.changed.util.SingleRunnable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.client.AbsorptionPreviewRenderState;
import net.parkabird.changedsynergy.client.BondedLatexScreen;
import net.parkabird.changedsynergy.client.FriendlySuitClientState;
import net.parkabird.changedsynergy.client.HumanPlayerPreviewRenderState;
import net.parkabird.changedsynergy.client.RadialWheelAnimationAccess;
import net.parkabird.changedsynergy.client.RadialWheelAnimations;
import net.parkabird.changedsynergy.client.PlayerRelationshipScreen;
import net.parkabird.changedsynergy.client.SocialInteractionScreen;
import net.parkabird.changedsynergy.client.UiSpring;
import net.parkabird.changedsynergy.client.UiMotion;
import net.parkabird.changedsynergy.client.AbilityWheelInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Smoothly assembles and dismisses every Changed-style radial wheel. */
@Mixin(value = AbstractRadialScreen.class, remap = false)
public abstract class RadialScreenAnimationMixin
        implements RadialWheelAnimationAccess {
    @Shadow(remap = false)
    private int tickCount;

    @Shadow(remap = false)
    private int viewOffset;

    @Shadow(remap = false)
    private int viewTransitionTicksLeft;

    @Unique
    private long changedSynergy$openedAtNanos = System.nanoTime();
    @Unique
    private long changedSynergy$closingAtNanos;
    @Unique
    private boolean changedSynergy$closing;
    @Unique
    private boolean changedSynergy$switching;
    @Unique
    private boolean changedSynergy$completionRun;
    @Unique
    private Runnable changedSynergy$completion;
    @Unique
    private int changedSynergy$selectedSection = -1;
    @Unique
    private float changedSynergy$layerAlpha = 1.0F;
    @Unique
    private boolean changedSynergy$initialized;
    @Unique
    private boolean changedSynergy$pairedArrival;
    @Unique private boolean changedSynergy$continuousSwitch;
    @Unique private boolean changedSynergy$inheritedSlide;
    @Unique private boolean changedSynergy$instantArrival;
    @Unique private final UiSpring changedSynergy$visibility = new UiSpring(0);
    @Unique private final UiSpring changedSynergy$panels = new UiSpring(1, 30, 1);
    @Unique private final UiSpring changedSynergy$center = new UiSpring(0, 30, 1);
    @Unique private UiSpring changedSynergy$horizontal = new UiSpring(0, 30, 1);
    @Unique private final UiSpring changedSynergy$paging = new UiSpring(0, 32, 1);
    @Unique private double changedSynergy$pageTarget;
    @Unique private final UiSpring changedSynergy$tiltX = new UiSpring(0, 24, 0.9);
    @Unique private final UiSpring changedSynergy$tiltY = new UiSpring(0, 24, 0.9);
    @Unique private boolean changedSynergy$tiltPose;
    @Unique private int changedSynergy$preselectedSlot = -1;
    @Unique private final UiSpring[] changedSynergy$preselection = java.util.stream.IntStream.range(0, 8).mapToObj(i -> new UiSpring(0, 30, 1)).toArray(UiSpring[]::new);
    @Unique private final UiSpring[] changedSynergy$highlights = java.util.stream.IntStream.range(0, 8).mapToObj(i -> new UiSpring(0, 30, 1)).toArray(UiSpring[]::new);
    @Unique private final UiSpring[] changedSynergy$frames = java.util.stream.IntStream.range(0, 8).mapToObj(i -> new UiSpring(0, 30, 1)).toArray(UiSpring[]::new);
    @Unique private final UiSpring[] changedSynergy$icons = java.util.stream.IntStream.range(0, 8).mapToObj(i -> new UiSpring(0)).toArray(UiSpring[]::new);

    @Inject(
            method = {"init", "m_7856_"},
            at = @At("TAIL"),
            remap = false,
            require = 1)
    private void changedSynergy$startOpening(CallbackInfo callback) {
        // The original base reveals one fully formed section per tick.  Let
        // every section render immediately and perform the stagger smoothly.
        tickCount = 64;
        if (!changedSynergy$initialized) {
            changedSynergy$initialized = true;
            changedSynergy$pairedArrival =
                    RadialWheelAnimations.consumeSwitchArrival(
                            changedSynergy$self());
            changedSynergy$openedAtNanos = System.nanoTime();
            if (changedSynergy$instantArrival) changedSynergy$openedAtNanos -= 1_000_000_000L;
            changedSynergy$selectedSection = -1;
            if (changedSynergy$pairedArrival) {
                for (var frame : changedSynergy$frames) frame.snap(1, changedSynergy$openedAtNanos);
                changedSynergy$center.snap(1, changedSynergy$openedAtNanos);
            }
            if (changedSynergy$inheritedSlide) changedSynergy$visibility.snap(1, changedSynergy$openedAtNanos);
            else changedSynergy$horizontal.snap(changedSynergy$restingOffset(), changedSynergy$openedAtNanos);
            if (changedSynergy$instantArrival) {
                changedSynergy$visibility.snap(1, changedSynergy$openedAtNanos);
                for (var icon : changedSynergy$icons) icon.snap(1, changedSynergy$openedAtNanos);
            }
            changedSynergy$paging.snap(viewOffset, changedSynergy$openedAtNanos);
            changedSynergy$pageTarget=viewOffset;
        }
    }

    @Inject(
            method = {"containerTick", "m_181908_"},
            at = @At("TAIL"),
            remap = false,
            require = 1)
    private void changedSynergy$finishClosing(CallbackInfo callback) {
        if (changedSynergy$continuousSwitch) {
            changedSynergy$updateSprings();
            if (!changedSynergy$completionRun && (UiMotion.reduced()
                    || changedSynergy$panels.value(System.nanoTime()) <= 0.03
                    || changedSynergy$closeElapsedMs() >= 200)) {
                changedSynergy$completionRun = true;
                Runnable completion = changedSynergy$completion;
                changedSynergy$completion = null;
                if (completion != null) completion.run();
            }
            if (changedSynergy$closeElapsedMs() >= 3_000) {
                // A rejected menu request leaves the original container usable.
                RadialWheelAnimations.cancelPendingSwitch();
                changedSynergy$continuousSwitch = changedSynergy$closing = changedSynergy$switching = false;
                if (changedSynergy$self() instanceof AbilityRadialScreen ability) AbilityWheelInput.closing(ability);
                changedSynergy$updateSprings();
            }
            return;
        }
        if (!changedSynergy$closing || changedSynergy$completionRun) {
            return;
        }
        changedSynergy$updateSprings();
        long now = System.nanoTime();
        if (changedSynergy$closeElapsedMs() < 350 && !changedSynergy$visibility.settled(now, 0.01)) {
            return;
        }
        changedSynergy$completionRun = true;
        Runnable completion = changedSynergy$completion;
        changedSynergy$completion = null;
        if (completion != null) {
            completion.run();
        }
    }

    @Inject(method = {"render", "m_88315_"}, at = @At("TAIL"), remap = false, require = 1)
    private void changedSynergy$finishRenderedClose(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo callback) {
        // Changed's ability screen overrides tick without delegating to containerTick.
        // Rendering is shared by both native wheels and Synergy's social wheels.
        changedSynergy$finishClosing(callback);
    }

    @Inject(method = {"renderBg", "m_7286_"}, at = @At("HEAD"), remap = false, require = 1)
    private void changedSynergy$tiltWheel(GuiGraphics graphics, float partialTick,
            int mouseX, int mouseY, CallbackInfo callback) {
        var screen = changedSynergy$self();
        boolean active = !UiMotion.reduced() && !changedSynergy$closing;
        changedSynergy$preselectedSlot = !changedSynergy$closing
                ? screen.getSectionAt(mouseX, mouseY).map(section -> Math.floorMod(section - viewOffset, 8)).orElse(-1) : -1;
        long now = System.nanoTime();
        float centerX = screen.width * 0.5F + changedSynergy$getHorizontalOffset();
        changedSynergy$tiltX.target(active ? Math.max(-1, Math.min(1, (mouseY - screen.height * 0.5) / 120.0)) * -8 : 0, now);
        changedSynergy$tiltY.target(active ? Math.max(-1, Math.min(1, (mouseX - centerX) / 120.0)) * 8 : 0, now);
        if (UiMotion.reduced()) { changedSynergy$tiltX.snap(0, now); changedSynergy$tiltY.snap(0, now); }
        float x = (float)changedSynergy$tiltX.value(now), y = (float)changedSynergy$tiltY.value(now);
        changedSynergy$tiltPose = Math.abs(x) + Math.abs(y) > 0.001F;
        if (!changedSynergy$tiltPose) return;
        graphics.flush();
        var view = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        view.pushPose();
        view.translate(centerX + y * 0.4F, screen.height * 0.5F - x * 0.4F, 0);
        double rx = Math.toRadians(x), ry = Math.toRadians(y);
        // Project the tilted UI plane onto XY. Native ability models use large local
        // Z offsets; rotating those depths would throw icons out of their frames.
        view.mulPoseMatrix(new org.joml.Matrix4f()
                .m00((float)Math.cos(ry)).m11((float)Math.cos(rx))
                .m01((float)(Math.sin(rx) * Math.sin(ry))));
        view.translate(-centerX, -screen.height * 0.5F, 0);
        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
    }

    @Inject(method = {"renderBg", "m_7286_"}, at = @At("TAIL"), remap = false, require = 1)
    private void changedSynergy$restoreTilt(GuiGraphics graphics, float partialTick,
            int mouseX, int mouseY, CallbackInfo callback) {
        if (changedSynergy$tiltPose) {
            graphics.flush();
            com.mojang.blaze3d.systems.RenderSystem.getModelViewStack().popPose();
            com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
            changedSynergy$tiltPose = false;
        }
    }

    @Inject(
            method = "getSectionAt",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void changedSynergy$waitForReadableHitboxes(
            int mouseX,
            int mouseY,
            CallbackInfoReturnable<Optional<Integer>> callback) {
        if (changedSynergy$closing || changedSynergy$inheritedSlide && !changedSynergy$horizontal.settled(System.nanoTime(), 0.01)) {
            callback.setReturnValue(Optional.empty());
        } else if (changedSynergy$self() instanceof AbilityRadialScreen ability && AbilityWheelInput.holding(ability)) {
            callback.setReturnValue(AbilityWheelInput.selection(ability, mouseX, mouseY));
        } else if(changedSynergy$self().getCount()>8){
            var screen=changedSynergy$self();float offset=changedSynergy$getScrollOffset();
            var selected=Optional.<Integer>empty();
            for(int section=(int)Math.floor(offset);section<8+(int)Math.ceil(offset)&&section<screen.getCount();section++){
                var point=RadialWheelAnimations.optionPosition(section,offset);
                if(Math.abs(mouseX-screen.width*.5-point.x)<=24&&Math.abs(mouseY-screen.height*.5-point.y)<=24){selected=Optional.of(section);break;}
            }
            callback.setReturnValue(selected);
        }
    }

    @Inject(
            method = {"mouseClicked", "m_6375_"},
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 1)
    private void changedSynergy$captureMouseSelection(
            double mouseX,
            double mouseY,
            int button,
            CallbackInfoReturnable<Boolean> callback) {
        if (changedSynergy$closing) {
            callback.setReturnValue(true);
            return;
        }
        AbstractRadialScreen<?> screen = changedSynergy$self();
        changedSynergy$selectedSection = screen
                .getSectionAt((int)mouseX, (int)mouseY)
                .map(index -> index - viewOffset).orElse(-1);
    }

    @Inject(method = {"scrollUp", "scrollDown"}, at = @At("HEAD"),
            cancellable = true, remap = false, require = 2)
    private void changedSynergy$finishScrollBeforeAnother(CallbackInfo callback) {
        if (changedSynergy$closing) callback.cancel();
    }

    @Inject(method = {"scrollUp", "scrollDown"}, at = @At("TAIL"), remap = false, require = 2)
    private void changedSynergy$retargetPage(CallbackInfo callback) {
        viewTransitionTicksLeft = 0;
        changedSynergy$pageTarget=viewOffset;
        changedSynergy$paging.target(viewOffset, System.nanoTime());
        if (changedSynergy$self() instanceof AbilityRadialScreen ability) AbilityWheelInput.pageChanged(ability);
    }

    @Inject(method = "getViewOffset", at = @At("HEAD"), cancellable = true, remap = false)
    private void changedSynergy$springPage(CallbackInfoReturnable<Float> callback) {
        if (UiMotion.reduced()) changedSynergy$paging.snap(changedSynergy$pageTarget, System.nanoTime());
        callback.setReturnValue((float)changedSynergy$paging.displayValue(System.nanoTime(), 0.001));
    }

    @Inject(
            method = {"keyPressed", "m_7933_"},
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 1)
    private void changedSynergy$animateKeyboardClose(
            int keyCode,
            int scanCode,
            int modifiers,
            CallbackInfoReturnable<Boolean> callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if(keyCode==262||keyCode==263){callback.setReturnValue(true);return;}
        boolean closeKey = keyCode == 256
                || minecraft.options.keyInventory.matches(keyCode, scanCode);
        if (!closeKey && changedSynergy$inheritedSlide && !changedSynergy$horizontal.settled(System.nanoTime(), 0.01)) {
            callback.setReturnValue(true);
            return;
        }
        if (changedSynergy$closing) {
            if (closeKey && changedSynergy$switching && minecraft.player != null) {
                // A rejected or delayed menu-open packet must not trap the user.
                changedSynergy$completion = null;
                changedSynergy$completionRun = true;
                RadialWheelAnimations.cancelSwitchAndArrival();
                minecraft.player.closeContainer();
            }
            callback.setReturnValue(true);
            return;
        }
        if (closeKey && minecraft.player != null) {
            changedSynergy$startClosing(
                    minecraft.player::closeContainer, false);
            callback.setReturnValue(true);
            return;
        }
    }

    @Redirect(
            method = {"mouseClicked", "m_6375_", "keyPressed", "m_7933_"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/ltxprogrammer/changed/util/SingleRunnable;run()V",
                    remap = false),
            remap = false,
            require = 2)
    private void changedSynergy$delayClose(SingleRunnable close) {
        changedSynergy$startClosing(close::run, false);
    }

    @Redirect(
            method = {"renderBg", "m_7286_"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/ltxprogrammer/changed/client/gui/AbstractRadialScreen;renderSectionBackground(Lnet/minecraft/client/gui/GuiGraphics;IDDFIIFFF)V",
                    remap = false),
            remap = false,
            require = 1)
    private void changedSynergy$animateSectionFrame(
            AbstractRadialScreen<?> screen,
            GuiGraphics graphics,
            int section,
            double x,
            double y,
            float partialTick,
            int mouseX,
            int mouseY,
            float red,
            float green,
            float blue) {
        LayerMotion motion = changedSynergy$frameMotion(section);
        if (motion.alpha() <= 0.01F || motion.scale() <= 0.01F) {
            return;
        }
        changedSynergy$layerAlpha = motion.alpha();
        float wheelOffset = changedSynergy$getHorizontalOffset();
        changedSynergy$renderTransformed(
                graphics, x, y, wheelOffset, motion,
                () -> screen.renderSectionBackground(
                        graphics,
                        section,
                        x * motion.radius() + wheelOffset,
                        y * motion.radius(),
                        partialTick,
                        mouseX,
                        mouseY,
                        red,
                        green,
                        blue));
        changedSynergy$layerAlpha = 1.0F;
    }

    @Redirect(
            method = {"renderBg", "m_7286_"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/ltxprogrammer/changed/client/gui/AbstractRadialScreen;renderSectionForeground(Lnet/minecraft/client/gui/GuiGraphics;IDDFIIFFFF)V",
                    remap = false),
            remap = false,
            require = 1)
    private void changedSynergy$animateSectionIcon(
            AbstractRadialScreen<?> screen,
            GuiGraphics graphics,
            int section,
            double x,
            double y,
            float partialTick,
            int mouseX,
            int mouseY,
            float red,
            float green,
            float blue,
            float alpha) {
        LayerMotion motion = changedSynergy$iconMotion(section);
        double offset=changedSynergy$getScrollOffset(),position=section-offset;
        float edgeAlpha=changedSynergy$clamp((float)Math.min(position+1,8-position));
        float visibleAlpha = motion.alpha()*edgeAlpha;
        if (visibleAlpha <= 0.01F) {
            return;
        }
        var point=RadialWheelAnimations.optionPosition(section,(float)offset);
        x=point.x;y=point.y;
        changedSynergy$layerAlpha = visibleAlpha;
        graphics.setColor(1.0F, 1.0F, 1.0F, visibleAlpha);
        float wheelOffset = changedSynergy$getHorizontalOffset();
        LayerMotion frame = changedSynergy$frameMotion(Math.floorMod(section - viewOffset, 8));
        float hoverScale = 1 + 0.045F * changedSynergy$preselectionAmount(section - viewOffset);
        double centerX = screen.width * 0.5 + x * frame.radius() + wheelOffset;
        double centerY = screen.height * 0.5 + y * frame.radius();
        // AbilityRenderer applies its own model scale after the GUI pose. Scale its screen plane instead.
        graphics.flush();
        var iconView = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        iconView.pushPose();
        iconView.translate(centerX, centerY, 0);
        iconView.scale(hoverScale, hoverScale, 1);
        iconView.translate(-centerX, -centerY, 0);
        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        // Native ability models scale GuiGraphics poses; keep alignment in screen pixels.
        screen.renderSectionForeground(
                graphics,
                section,
                x * frame.radius() + wheelOffset + RadialWheelAnimations.iconOffset(screen, section, false),
                y * frame.radius() + RadialWheelAnimations.iconOffset(screen, section, true),
                partialTick,
                mouseX,
                mouseY,
                red,
                green,
                blue,
                visibleAlpha);
        graphics.flush();
        iconView.popPose();
        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        changedSynergy$layerAlpha = 1.0F;
    }

    @Redirect(
            method = {"renderBg", "m_7286_"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;renderEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphics;IIIFFLnet/minecraft/world/entity/LivingEntity;)V",
                    remap = false),
            remap = false,
            require = 0)
    private void changedSynergy$animateCenterNamed(
            GuiGraphics graphics,
            int x,
            int y,
            int scale,
            float mouseX,
            float mouseY,
            LivingEntity entity) {
        changedSynergy$renderAnimatedCenter(
                graphics, x, y, scale, mouseX, mouseY, entity);
    }

    @Redirect(
            method = {"renderBg", "m_7286_"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;m_274545_(Lnet/minecraft/client/gui/GuiGraphics;IIIFFLnet/minecraft/world/entity/LivingEntity;)V",
                    remap = false),
            remap = false,
            require = 0)
    private void changedSynergy$animateCenterObfuscated(
            GuiGraphics graphics,
            int x,
            int y,
            int scale,
            float mouseX,
            float mouseY,
            LivingEntity entity) {
        changedSynergy$renderAnimatedCenter(
                graphics, x, y, scale, mouseX, mouseY, entity);
    }

    @Override
    public boolean changedSynergy$startClosing(Runnable completion, boolean wheelSwitch) {
        if (changedSynergy$closing) return true;
        changedSynergy$updateSprings();
        changedSynergy$closing = true;
        changedSynergy$switching = wheelSwitch;
        changedSynergy$continuousSwitch = wheelSwitch && RadialWheelAnimations.isContinuousPlayerSwitch(changedSynergy$self());
        changedSynergy$completionRun = false;
        changedSynergy$completion = completion;
        changedSynergy$closingAtNanos = System.nanoTime();
        if (wheelSwitch) AbilityWheelInput.switchStarted();
        else if (changedSynergy$self() instanceof AbilityRadialScreen ability) AbilityWheelInput.closing(ability);
        changedSynergy$updateSprings();
        if (changedSynergy$continuousSwitch && !(changedSynergy$self() instanceof PlayerRelationshipScreen)) {
            changedSynergy$completionRun = true;
            changedSynergy$completion = null;
            completion.run();
        }
        return true;
    }

    @Override
    public UiSpring changedSynergy$getHorizontalSpring() { return changedSynergy$horizontal; }

    @Override
    public void changedSynergy$continueSlide(UiSpring horizontal) {
        changedSynergy$horizontal = horizontal;
        changedSynergy$inheritedSlide = true;
    }

    @Override public void changedSynergy$openImmediately() { changedSynergy$instantArrival = true; }

    @Override public boolean changedSynergy$isInstantArrival() { return changedSynergy$instantArrival; }

    @Override
    public float changedSynergy$getLayerAlpha() {
        return changedSynergy$layerAlpha;
    }

    @Override
    public int changedSynergy$getViewOffset() {
        return viewOffset;
    }

    @Override public float changedSynergy$getScrollOffset(){return (float)changedSynergy$paging.displayValue(System.nanoTime(),.001);}

    @Override public void changedSynergy$scrollOptions(double delta){
        if(changedSynergy$closing||!Double.isFinite(delta))return;
        changedSynergy$pageTarget=Math.max(0,Math.min(Math.max(0,changedSynergy$self().getCount()-8),changedSynergy$pageTarget-delta));
        viewOffset=(int)Math.round(changedSynergy$pageTarget);viewTransitionTicksLeft=0;
        changedSynergy$paging.target(changedSynergy$pageTarget,System.nanoTime());
        if(changedSynergy$self() instanceof AbilityRadialScreen ability)AbilityWheelInput.pageChanged(ability);
    }

    @Override
    public boolean changedSynergy$canConfirm() {
        return !changedSynergy$closing && (UiMotion.reduced() || changedSynergy$paging.settled(System.nanoTime(), 0.01)
                && (!changedSynergy$inheritedSlide || changedSynergy$horizontal.settled(System.nanoTime(), 0.01)));
    }

    @Override
    public float changedSynergy$getOverallAlpha() {
        changedSynergy$updateSprings();
        return UiSpring.clamp(changedSynergy$visibility.value(System.nanoTime()));
    }

    @Override
    public float changedSynergy$getFrameHighlight(int slot, boolean selected) {
        long now = System.nanoTime();
        var highlight = changedSynergy$highlights[Math.floorMod(slot, 8)];
        highlight.target(selected ? 1 : 0, now);
        if (UiMotion.reduced()) highlight.snap(selected ? 1 : 0, now);
        return UiSpring.clamp(highlight.displayValue(now, 0.001));
    }

    @Override
    public float changedSynergy$getPanelAlpha() {
        float alpha = changedSynergy$getOverallAlpha();
        return alpha * UiSpring.clamp(changedSynergy$panels.value(System.nanoTime()));
    }

    @Override
    public float changedSynergy$getHorizontalOffset() {
        changedSynergy$updateSprings();
        return (float)changedSynergy$horizontal.displayValue(System.nanoTime(), 0.01);
    }

    @Unique
    private float changedSynergy$restingOffset() {
        var screen = changedSynergy$self();
        return screen instanceof SocialInteractionScreen || screen instanceof PlayerRelationshipScreen
                ? RadialWheelAnimations.layoutOffset(screen.width) : 0.0F;
    }

    @Unique
    private void changedSynergy$updateSprings() {
        long now = System.nanoTime();
        boolean reduced = UiMotion.reduced();
        double elapsed = (now - changedSynergy$openedAtNanos) / 1_000_000_000.0;
        changedSynergy$visibility.target(changedSynergy$closing && !changedSynergy$continuousSwitch ? 0 : 1, now);
        changedSynergy$panels.target(changedSynergy$closing && changedSynergy$continuousSwitch ? 0 : 1, now);
        changedSynergy$center.target(changedSynergy$closing && !changedSynergy$switching ? 0 : 1, now);
        float offset = changedSynergy$restingOffset();
        if (changedSynergy$closing && changedSynergy$switching && !reduced)
            offset = offset == 0 ? RadialWheelAnimations.layoutOffset(changedSynergy$self().width) : 0;
        changedSynergy$horizontal.target(offset, now);
        for (int i = 0; i < 8; i++) {
            double target = changedSynergy$closing && !changedSynergy$switching ? 0
                    : reduced || changedSynergy$pairedArrival || elapsed >= i * 0.012 ? 1 : 0;
            changedSynergy$frames[i].target(target, now);
            double icon = changedSynergy$closing ? 0 : reduced || elapsed >= i * 0.012 + 0.025 ? 1 : 0;
            changedSynergy$icons[i].target(icon, now);
            double lift = !changedSynergy$closing && i == changedSynergy$preselectedSlot ? 6 : 0;
            changedSynergy$preselection[i].target(lift, now);
            if (reduced) changedSynergy$preselection[i].snap(lift, now);
            if (reduced) changedSynergy$frames[i].snap(target, now);
        }
        if (reduced) {
            changedSynergy$panels.snap(changedSynergy$closing && changedSynergy$continuousSwitch ? 0 : 1, now);
            changedSynergy$horizontal.snap(offset, now);
            changedSynergy$center.snap(changedSynergy$closing && !changedSynergy$switching ? 0 : 1, now);
        }
    }

    @Unique
    private void changedSynergy$renderAnimatedCenter(GuiGraphics graphics, int x, int y, int scale,
            float mouseX, float mouseY, LivingEntity entity) {
        if (changedSynergy$hideCenterEntity(entity)) return;
        changedSynergy$updateSprings();
        float value = (float)changedSynergy$center.displayValue(System.nanoTime(), 0.001);
        if (value <= 0.01F) return;
        float offset = changedSynergy$getHorizontalOffset();
        int animatedScale = Math.max(1, Math.round(scale * (UiMotion.reduced() ? 1 : 0.78F + 0.22F * value)));
        int animatedY = y + (UiMotion.reduced() ? 0 : Math.round(10 * (1 - value)));
        changedSynergy$renderCenterContent(graphics, x + Math.round(offset), animatedY, animatedScale,
                mouseX + offset, mouseY, entity);
    }


    @Unique
    private void changedSynergy$renderCenterContent(
            GuiGraphics graphics,
            int x,
            int y,
            int scale,
            float mouseX,
            float mouseY,
            LivingEntity entity) {
        AbstractRadialScreen<?> screen = changedSynergy$self();
        Minecraft minecraft = Minecraft.getInstance();
        if (screen instanceof SocialInteractionScreen social
                && social.menu.isVirtualAbsorptionNegotiation()
                && minecraft.player != null) {
            int pairScale = Math.max(1, Math.round(scale * 0.82F));
            int separation = Math.max(14, Math.round(scale * 0.62F));
            HumanPlayerPreviewRenderState.render(() ->
                    InventoryScreen.renderEntityInInventoryFollowsMouse(
                            graphics, x - separation, y, pairScale,
                            mouseX - separation, mouseY, minecraft.player));
            LivingEntity absorptionPreview =
                    social.getAbsorptionPreviewEntity();
            if (absorptionPreview != null) {
                InventoryScreen.renderEntityInInventoryFollowsMouse(
                        graphics, x + separation, y, pairScale,
                        mouseX + separation, mouseY, absorptionPreview);
            } else {
                AbsorptionPreviewRenderState.render(
                        minecraft.player,
                        social.menu.getNegotiationAppearance(),
                        () -> InventoryScreen.renderEntityInInventoryFollowsMouse(
                                graphics, x + separation, y, pairScale,
                                mouseX + separation, mouseY, minecraft.player));
            }
            return;
        }
        if (screen instanceof SocialInteractionScreen social
                && social.menu.isNegotiationMode()
                && minecraft.player != null
                && entity != minecraft.player) {
            int pairScale = Math.max(1, Math.round(scale * 0.82F));
            int separation = Math.max(14, Math.round(scale * 0.62F));
            HumanPlayerPreviewRenderState.render(() ->
                    InventoryScreen.renderEntityInInventoryFollowsMouse(
                            graphics, x - separation, y, pairScale,
                            mouseX - separation, mouseY, minecraft.player));
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    graphics, x + separation, y, pairScale,
                    mouseX + separation, mouseY, entity);
            return;
        }
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                graphics, x, y, scale, mouseX, mouseY, entity);
    }

    @Unique
    private boolean changedSynergy$hideCenterEntity(LivingEntity entity) {
        AbstractRadialScreen<?> screen = changedSynergy$self();
        if (screen instanceof PlayerRelationshipScreen
                || screen instanceof net.parkabird.changedsynergy.client.NativeEmoteScreen) {
            return false;
        }
        if (screen instanceof BondedLatexScreen bonded
                && bonded.menu.isSuitingOwner()) {
            return true;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || entity != minecraft.player) {
            return false;
        }
        if (FriendlySuitClientState.isOwnerSuited(minecraft.player.getId())) {
            return true;
        }
        var variant = ProcessTransfur.getPlayerTransfurVariant(minecraft.player);
        return variant == null || variant.isTemporaryFromSuit();
    }

    @Unique
    private LayerMotion changedSynergy$frameMotion(int section) {
        changedSynergy$updateSprings();
        int slot = Math.floorMod(section, 8);
        long now = System.nanoTime();
        float value = (float)changedSynergy$frames[slot].displayValue(now, 0.001);
        float lift = (float)changedSynergy$preselection[slot].displayValue(now, 0.01) / 90.0F;
        return new LayerMotion((UiMotion.reduced() ? 1 : 0.16F + 0.84F * value) + lift * UiSpring.clamp(value),
                (UiMotion.reduced() ? 1 : 0.70F + 0.30F * value) * (1 + 0.045F * changedSynergy$preselectionAmount(slot)), UiSpring.clamp(value));
    }

    @Unique
    private float changedSynergy$preselectionAmount(int section) {
        return (float)changedSynergy$preselection[Math.floorMod(section, 8)].displayValue(System.nanoTime(), 0.01) / 6.0F;
    }


    @Unique
    private LayerMotion changedSynergy$iconMotion(int section) {
        changedSynergy$updateSprings();
        int slot = Math.floorMod(section - viewOffset, 8);
        float value = (float)changedSynergy$icons[slot].value(System.nanoTime());
        return new LayerMotion(1, 1, UiSpring.clamp(value));
    }


    @Unique
    private void changedSynergy$renderTransformed(
            GuiGraphics graphics,
            double originalX,
            double originalY,
            float wheelOffset,
            LayerMotion motion,
            Runnable render) {
        AbstractRadialScreen<?> screen = changedSynergy$self();
        double animatedX = originalX * motion.radius();
        double animatedY = originalY * motion.radius();
        float centerX = screen.width * 0.5F + wheelOffset
                + (float)animatedX;
        float centerY = screen.height * 0.5F + (float)animatedY;
        float radialScale = motion.scale();
        float tangentScale = motion.scale();
        if (!changedSynergy$isOrganicWheel() && !changedSynergy$closing) {
            float progress = changedSynergy$clamp(
                    (motion.radius() - 0.16F) / 0.84F);
            float stretch = (float)Math.sin(Math.PI * progress)
                    * (1.0F - progress);
            radialScale *= 1.0F + 0.12F * stretch;
            tangentScale *= 1.0F - 0.05F * stretch;
        }

        float angle = (float)Math.toDegrees(Math.atan2(originalY, originalX));
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(angle));
        graphics.pose().scale(radialScale, tangentScale, 1.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(-angle));
        graphics.pose().translate(-centerX, -centerY, 0.0F);
        render.run();
        graphics.pose().popPose();
    }

    @Unique
    private boolean changedSynergy$isOrganicWheel() {
        AbstractRadialScreen<?> screen = changedSynergy$self();
        if (screen instanceof PlayerRelationshipScreen relationships) {
            return relationships.menu.usesOrganicStyle();
        }
        if (screen instanceof BondedLatexScreen bonded) {
            return bonded.menu.isOrganicPet();
        }
        if (screen instanceof SocialInteractionScreen social) {
            return social.menu.isOrganic();
        }
        if (screen.centerEntity instanceof ChangedEntity creature) {
            return LatexSocialMemory.isOrganic(creature);
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && screen.centerEntity == minecraft.player) {
            var variant = ProcessTransfur.getPlayerTransfurVariant(minecraft.player);
            return variant != null
                    && !variant.getParent().getEntityType()
                            .is(ChangedTags.EntityTypes.LATEX);
        }
        return false;
    }

    @Unique
    private long changedSynergy$closeElapsedMs() {
        return Math.max(0L,
                (System.nanoTime() - changedSynergy$closingAtNanos) / 1_000_000L);
    }


    @Unique
    private AbstractRadialScreen<?> changedSynergy$self() {
        return (AbstractRadialScreen<?>)(Object)this;
    }

    @Unique
    private static float changedSynergy$clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }




    @Unique
    private record LayerMotion(float radius, float scale, float alpha) {
    }
}
