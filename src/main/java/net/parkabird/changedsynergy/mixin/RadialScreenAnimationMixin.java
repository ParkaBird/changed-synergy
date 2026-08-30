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
    @Unique
    private static final long FRAME_DURATION_MS = 205L;
    @Unique
    private static final long ICON_DURATION_MS = 145L;
    @Unique
    private static final long CENTER_DURATION_MS = 170L;
    @Unique
    private static final long SECTION_STAGGER_MS = 22L;
    @Unique
    private static final long INPUT_DELAY_MS = 240L;
    @Unique
    private static final long CLOSE_DURATION_MS = 255L;
    @Unique
    private static final long SWITCH_DURATION_MS = 245L;
    @Unique
    private static final long FULL_OPEN_DURATION_MS = FRAME_DURATION_MS
            + ICON_DURATION_MS + SECTION_STAGGER_MS * 7L + 8L;

    @Shadow(remap = false)
    private int tickCount;

    @Unique
    private long changedSynergy$openedAtNanos = System.nanoTime();
    @Unique
    private long changedSynergy$closingAtNanos;
    @Unique
    private long changedSynergy$closingOpenElapsedMs;
    @Unique
    private long changedSynergy$closingDurationMs = CLOSE_DURATION_MS;
    @Unique
    private boolean changedSynergy$reversingOpening;
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
            changedSynergy$selectedSection = -1;
        }
    }

    @Inject(
            method = {"containerTick", "m_181908_"},
            at = @At("TAIL"),
            remap = false,
            require = 1)
    private void changedSynergy$finishClosing(CallbackInfo callback) {
        if (!changedSynergy$closing || changedSynergy$completionRun) {
            return;
        }
        if (changedSynergy$closeElapsedMs()
                < changedSynergy$closingDurationMs) {
            return;
        }
        changedSynergy$completionRun = true;
        Runnable completion = changedSynergy$completion;
        changedSynergy$completion = null;
        if (completion != null) {
            completion.run();
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
        if (changedSynergy$closing
                || changedSynergy$openElapsedMs() < INPUT_DELAY_MS) {
            callback.setReturnValue(Optional.empty());
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
        if (changedSynergy$closing
                || changedSynergy$openElapsedMs() < INPUT_DELAY_MS) {
            callback.setReturnValue(true);
            return;
        }
        AbstractRadialScreen<?> screen = changedSynergy$self();
        changedSynergy$selectedSection = screen
                .getSectionAt((int)mouseX, (int)mouseY)
                .orElse(-1);
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
        if (changedSynergy$closing) {
            callback.setReturnValue(true);
            return;
        }
        boolean closeKey = keyCode == 256
                || minecraft.options.keyInventory.matches(keyCode, scanCode);
        if (closeKey && minecraft.player != null) {
            changedSynergy$startClosing(
                    minecraft.player::closeContainer, false);
            callback.setReturnValue(true);
            return;
        }
        if (keyCode >= 49 && keyCode <= 57
                && changedSynergy$openElapsedMs() < INPUT_DELAY_MS) {
            callback.setReturnValue(true);
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
        float visibleAlpha = motion.alpha();
        if (visibleAlpha <= 0.01F) {
            return;
        }
        changedSynergy$layerAlpha = visibleAlpha;
        graphics.setColor(1.0F, 1.0F, 1.0F, visibleAlpha);
        float wheelOffset = changedSynergy$getHorizontalOffset();
        screen.renderSectionForeground(
                graphics,
                section,
                x + wheelOffset,
                y,
                partialTick,
                mouseX,
                mouseY,
                red,
                green,
                blue,
                alpha * visibleAlpha);
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
    public boolean changedSynergy$startClosing(
            Runnable completion,
            boolean wheelSwitch) {
        if (changedSynergy$closing) {
            return true;
        }
        changedSynergy$closing = true;
        changedSynergy$switching = wheelSwitch;
        changedSynergy$completionRun = false;
        changedSynergy$completion = completion;
        changedSynergy$closingAtNanos = System.nanoTime();
        changedSynergy$closingOpenElapsedMs = Math.min(
                changedSynergy$openElapsedMs(), FULL_OPEN_DURATION_MS);
        changedSynergy$reversingOpening = !wheelSwitch
                && changedSynergy$closingOpenElapsedMs
                        < FULL_OPEN_DURATION_MS;
        long normalDuration = wheelSwitch
                ? SWITCH_DURATION_MS : CLOSE_DURATION_MS;
        changedSynergy$closingDurationMs = changedSynergy$reversingOpening
                ? Math.max(40L, Math.round(
                        normalDuration
                                * changedSynergy$closingOpenElapsedMs
                                / (double)FULL_OPEN_DURATION_MS))
                : normalDuration;
        return true;
    }

    @Override
    public float changedSynergy$getLayerAlpha() {
        return changedSynergy$layerAlpha;
    }

    @Override
    public float changedSynergy$getOverallAlpha() {
        if (changedSynergy$closing) {
            if (changedSynergy$reversingOpening) {
                return changedSynergy$smoothStep(
                        changedSynergy$reverseOpenElapsedMs() / 180.0F);
            }
            long duration = changedSynergy$closingDurationMs;
            return 1.0F - changedSynergy$smoothStep(
                    changedSynergy$closeElapsedMs() / (float)duration);
        }
        return changedSynergy$smoothStep(
                changedSynergy$openElapsedMs() / 180.0F);
    }

    @Override
    public float changedSynergy$getHorizontalOffset() {
        AbstractRadialScreen<?> screen = changedSynergy$self();
        float socialOffset = changedSynergy$socialWheelOffset(screen.width);
        if (changedSynergy$closing && changedSynergy$switching) {
            float progress = changedSynergy$smoothStep(
                    changedSynergy$closeElapsedMs()
                            / (float)changedSynergy$closingDurationMs);
            if (screen instanceof BondedLatexScreen) {
                return socialOffset * progress;
            }
            if (screen instanceof AbilityRadialScreen) {
                return socialOffset * progress;
            }
            if (screen instanceof PlayerRelationshipScreen) {
                return socialOffset * (1.0F - progress);
            }
            if (screen instanceof SocialInteractionScreen social
                    && social.menu.isBondedMode()) {
                return socialOffset * (1.0F - progress);
            }
        }
        return screen instanceof SocialInteractionScreen
                || screen instanceof PlayerRelationshipScreen
                ? socialOffset : 0.0F;
    }

    @Unique
    private void changedSynergy$renderAnimatedCenter(
            GuiGraphics graphics,
            int x,
            int y,
            int scale,
            float mouseX,
            float mouseY,
            LivingEntity entity) {
        if (changedSynergy$hideCenterEntity(entity)) {
            return;
        }
        float wheelOffset = changedSynergy$getHorizontalOffset();
        int shiftedX = x + Math.round(wheelOffset);
        float shiftedMouseX = mouseX + wheelOffset;
        if ((!changedSynergy$closing && changedSynergy$pairedArrival)
                || (changedSynergy$closing && changedSynergy$switching)) {
            changedSynergy$renderCenterContent(
                    graphics, shiftedX, y, scale,
                    shiftedMouseX, mouseY, entity);
            return;
        }
        float progress;
        if (changedSynergy$closing) {
            if (changedSynergy$reversingOpening) {
                changedSynergy$renderOpeningCenter(
                        graphics, shiftedX, y, scale,
                        shiftedMouseX, mouseY, entity,
                        changedSynergy$reverseOpenElapsedMs());
                return;
            }
            progress = changedSynergy$clamp(
                    changedSynergy$closeElapsedMs() / 110.0F);
            if (progress >= 0.99F) {
                return;
            }
            float eased = progress * progress * progress;
            int animatedScale = Math.max(1, Math.round(scale * (1.0F - 0.12F * eased)));
            int animatedY = y + Math.round(8.0F * eased);
            changedSynergy$renderCenterContent(
                    graphics, shiftedX, animatedY, animatedScale,
                    shiftedMouseX, mouseY, entity);
            return;
        }
        changedSynergy$renderOpeningCenter(
                graphics, shiftedX, y, scale,
                shiftedMouseX, mouseY, entity,
                changedSynergy$openElapsedMs());
    }

    @Unique
    private void changedSynergy$renderOpeningCenter(
            GuiGraphics graphics,
            int x,
            int y,
            int scale,
            float mouseX,
            float mouseY,
            LivingEntity entity,
            long openElapsedMs) {
        float progress = changedSynergy$clamp(
                (openElapsedMs - 55L) / (float)CENTER_DURATION_MS);
        if (progress <= 0.0F) {
            return;
        }
        float eased = changedSynergy$easeOutBack(progress, 1.15F);
        int animatedScale = Math.max(1, Math.round(
                scale * (0.78F + 0.22F * eased)));
        int animatedY = y + Math.round(10.0F
                * (1.0F - changedSynergy$easeOutCubic(progress)));
        changedSynergy$renderCenterContent(
                graphics, x, animatedY, animatedScale,
                mouseX, mouseY, entity);
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
        if (screen instanceof PlayerRelationshipScreen) {
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
        int slot = Math.floorMod(section, 8);
        if (changedSynergy$closing
                && changedSynergy$reversingOpening) {
            return changedSynergy$openingFrameMotion(
                    slot, changedSynergy$reverseOpenElapsedMs());
        }
        if ((!changedSynergy$closing && changedSynergy$pairedArrival)
                || (changedSynergy$closing && changedSynergy$switching)) {
            return new LayerMotion(1.0F, 1.0F, 1.0F);
        }
        if (changedSynergy$closing) {
            int reverse = 7 - slot;
            float progress = changedSynergy$clamp(
                    (changedSynergy$closeElapsedMs() - 82L - reverse * 6L)
                            / 112.0F);
            float eased = progress * progress * progress;
            float selectedPulse = section == changedSynergy$selectedSection
                    && changedSynergy$closeElapsedMs() < 45L
                    ? 0.07F * (float)Math.sin(
                            Math.PI * changedSynergy$closeElapsedMs() / 45.0D)
                    : 0.0F;
            return new LayerMotion(
                    1.0F - 0.78F * eased,
                    1.0F - 0.30F * eased + selectedPulse,
                    1.0F - changedSynergy$smoothStep(progress));
        }
        return changedSynergy$openingFrameMotion(
                slot, changedSynergy$openElapsedMs());
    }

    @Unique
    private LayerMotion changedSynergy$openingFrameMotion(
            int slot,
            long openElapsedMs) {
        float progress = changedSynergy$clamp(
                (openElapsedMs - slot * SECTION_STAGGER_MS)
                        / (float)FRAME_DURATION_MS);
        return new LayerMotion(
                0.16F + 0.84F * changedSynergy$easeOutCubic(progress),
                0.70F + 0.30F * changedSynergy$easeOutBack(progress, 1.35F),
                changedSynergy$smoothStep(progress));
    }

    @Unique
    private LayerMotion changedSynergy$iconMotion(int section) {
        int slot = Math.floorMod(section, 8);
        if (changedSynergy$closing) {
            if (changedSynergy$reversingOpening) {
                return changedSynergy$openingIconMotion(
                        slot, changedSynergy$reverseOpenElapsedMs());
            }
            if (changedSynergy$switching) {
                float progress = changedSynergy$clamp(
                        (changedSynergy$closeElapsedMs() - slot * 16L)
                                / 92.0F);
                return new LayerMotion(
                        1.0F,
                        1.0F,
                        1.0F - changedSynergy$smoothStep(progress));
            }
            int reverse = 7 - slot;
            float progress = changedSynergy$clamp(
                    (changedSynergy$closeElapsedMs() - reverse * 6L) / 78.0F);
            return new LayerMotion(
                    1.0F,
                    1.0F,
                    1.0F - changedSynergy$smoothStep(progress));
        }
        return changedSynergy$openingIconMotion(
                slot, changedSynergy$openElapsedMs());
    }

    @Unique
    private LayerMotion changedSynergy$openingIconMotion(
            int slot,
            long openElapsedMs) {
        if (changedSynergy$pairedArrival) {
            float progress = changedSynergy$clamp(
                    (openElapsedMs - slot * 18L) / 120.0F);
            return new LayerMotion(
                    1.0F,
                    1.0F,
                    changedSynergy$smoothStep(progress));
        }
        float progress = changedSynergy$clamp(
                (openElapsedMs
                        - slot * SECTION_STAGGER_MS
                        - FRAME_DURATION_MS - 8L)
                        / (float)ICON_DURATION_MS);
        return new LayerMotion(
                1.0F,
                1.0F,
                changedSynergy$smoothStep(progress));
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
    private long changedSynergy$openElapsedMs() {
        return Math.max(0L,
                (System.nanoTime() - changedSynergy$openedAtNanos) / 1_000_000L);
    }

    @Unique
    private long changedSynergy$closeElapsedMs() {
        return Math.max(0L,
                (System.nanoTime() - changedSynergy$closingAtNanos) / 1_000_000L);
    }

    @Unique
    private long changedSynergy$reverseOpenElapsedMs() {
        if (changedSynergy$closingDurationMs <= 0L) {
            return 0L;
        }
        float remaining = 1.0F - changedSynergy$clamp(
                changedSynergy$closeElapsedMs()
                        / (float)changedSynergy$closingDurationMs);
        return Math.max(0L, Math.round(
                changedSynergy$closingOpenElapsedMs * remaining));
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
    private static float changedSynergy$easeOutCubic(float value) {
        float inverse = 1.0F - changedSynergy$clamp(value);
        return 1.0F - inverse * inverse * inverse;
    }

    @Unique
    private static float changedSynergy$easeOutBack(float value, float strength) {
        float shifted = changedSynergy$clamp(value) - 1.0F;
        return 1.0F + (strength + 1.0F) * shifted * shifted * shifted
                + strength * shifted * shifted;
    }

    @Unique
    private static float changedSynergy$smoothStep(float value) {
        float clamped = changedSynergy$clamp(value);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    @Unique
    private static float changedSynergy$socialWheelOffset(int screenWidth) {
        float desired = Math.min(220.0F, screenWidth * 0.22F);
        float roomAtRight = Math.max(0.0F, screenWidth * 0.5F - 136.0F);
        return Math.min(desired, roomAtRight);
    }

    @Unique
    private record LayerMotion(float radius, float scale, float alpha) {
    }
}
