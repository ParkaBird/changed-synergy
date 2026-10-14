package net.parkabird.changedsynergy.regression;

import net.ltxprogrammer.changed.client.renderer.AdvancedHumanoidRenderer;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.PlayerDataExtension;
import net.ltxprogrammer.changed.entity.beast.WhiteLatexWolfMale;
import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.HumanoidArm;
import net.parkabird.changedsynergy.ai.PureWhiteWolfAdaptation;
import net.parkabird.changedsynergy.client.PatAnimationClientState;
import net.parkabird.changedsynergy.client.PureWhitePlayerAppearance;

/** Call once after an isolated client fixture has entered a world. */
public final class PlayerVisualClientRegression {
    public static void run(LocalPlayer player) {
        var previous = ProcessTransfur.getPlayerTransfurVariant(player);
        long time = player.level().getGameTime();
        try {
            for (var form : java.util.List.of(ChangedTransfurVariants.LATEX_SNAKE.get(),
                    ChangedTransfurVariants.LATEX_MERMAID_SHARK.get(),
                    ChangedTransfurVariants.LATEX_HUMAN.get(),
                    ChangedTransfurVariants.WHITE_LATEX_WOLF_MALE.get())) {
                var instance = TransfurVariantInstance.variantFor(form, player);
                ((PlayerDataExtension) player).setTransfurVariant(instance);
                instance.transfurProgressionO = instance.transfurProgression = 1;
                instance.sync(player);
                // Exercise the real H-key branch before rendering, including its lazily loaded enum.
                if (form == ChangedTransfurVariants.LATEX_HUMAN.get()) {
                    var minecraft = Minecraft.getInstance();
                    var screen = minecraft.screen;
                    var hit = minecraft.hitResult;
                    boolean pressed = net.parkabird.changedsynergy.client.SynergyKeyMappings.PAT.isDown();
                    try {
                        minecraft.screen = null;
                        minecraft.hitResult = new net.minecraft.world.phys.EntityHitResult(player);
                        net.parkabird.changedsynergy.client.SynergyKeyMappings.PAT.setDown(true);
                        net.parkabird.changedsynergy.client.PatInputClientEvents.onClientTick(
                                new net.minecraftforge.event.TickEvent.ClientTickEvent(net.minecraftforge.event.TickEvent.Phase.END));
                        check(true, "Latex Human reaches the real held-pat input path without a class-loading error");
                    } finally {
                        net.parkabird.changedsynergy.client.SynergyKeyMappings.PAT.setDown(false);
                        net.parkabird.changedsynergy.client.PatInputClientEvents.onClientTick(
                                new net.minecraftforge.event.TickEvent.ClientTickEvent(net.minecraftforge.event.TickEvent.Phase.END));
                        net.parkabird.changedsynergy.client.SynergyKeyMappings.PAT.setDown(pressed);
                        minecraft.screen = screen;
                        minecraft.hitResult = hit;
                    }
                }
                ChangedEntity body = instance.getChangedEntity();
                check(body.getId() != player.getId(), "Real form surrogate has a distinct entity ID");
                var renderer = (AdvancedHumanoidRenderer) Minecraft.getInstance()
                        .getEntityRenderDispatcher().getRenderer(body);
                var model = renderer.getModel(body);
                PatAnimationClientState.update(player.getId(), true, 40, 10);
                ((ClientLevel) player.level()).setGameTime(time + 5);
                model.prepareMobModel(body, 0, 0, 0);
                model.setupAnim(body, 0, 0, body.tickCount, 0, 0);
                check(model.getArm(HumanoidArm.RIGHT) != null
                                && model.getArm(HumanoidArm.RIGHT).xRot < -1.4F,
                        "Transformed-player pat reaches real right arm: " + form.getFormId());
                PatAnimationClientState.update(player.getId(), false, 0, 10);
                ((ClientLevel) player.level()).setGameTime(time);
            }
            // Exercise absent parts and failing limb lookups while a real pat is active.
            PatAnimationClientState.update(player.getId(), true, 40, 10);
            ((ClientLevel) player.level()).setGameTime(time + 5);
            for (int failure = 0; failure < 5; failure++) {
                final int mode = failure;
                var missingArmModel = new net.ltxprogrammer.changed.client.renderer.model.LatexHumanModel(
                        Minecraft.getInstance().getEntityModels().bakeLayer(
                                net.ltxprogrammer.changed.client.renderer.model.LatexHumanModel.LAYER_LOCATION)) {
                    private boolean ready = true;
                    @Override
                    public net.minecraft.client.model.geom.ModelPart getArm(HumanoidArm arm) {
                        // Changed reads limbs while constructing its animator; fail only at the pat hook.
                        if (!ready) return super.getArm(arm);
                        return switch (mode) {
                            case 1 -> throw new NullPointerException("Absent arm");
                            case 2 -> throw new IllegalArgumentException("Can't find part right_arm");
                            case 3 -> throw new UnsupportedOperationException("No arms");
                            case 4 -> new net.minecraft.client.model.geom.ModelPart(
                                    java.util.List.of(), java.util.Map.of()).getChild("right_arm");
                            default -> null;
                        };
                    }
                };
                check(!PatAnimationClientState.applyModelRightArmPose(player.getId(), 0, missingArmModel),
                        "Missing arm lookup skips only the animation, mode " + mode);
                check(PatAnimationClientState.isActive(player.getId()),
                        "Skipping an absent arm leaves the pat session active");
            }
            var pudding = net.ltxprogrammer.changed.init.ChangedEntities.MILK_PUDDING.get().create(player.level());
            check(pudding != null, "Create actual armless Milk Pudding");
            var puddingRenderer = (net.ltxprogrammer.changed.client.renderer.MilkPuddingRenderer)
                    Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(pudding);
            PatAnimationClientState.update(pudding.getId(), true, 40, 10);
            Minecraft.getInstance().getEntityRenderDispatcher().prepare(player.level(),
                    Minecraft.getInstance().gameRenderer.getMainCamera(), player);
            var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            puddingRenderer.render(pudding, 0, 0, new com.mojang.blaze3d.vertex.PoseStack(), buffers, 15728880);
            buffers.endBatch();
            check(PatAnimationClientState.isActive(pudding.getId()),
                    "Actual armless Milk Pudding renders safely with an active pat session");
            PatAnimationClientState.update(pudding.getId(), false, 0, 10);
            PatAnimationClientState.update(player.getId(), false, 0, 10);
            ((ClientLevel) player.level()).setGameTime(time);
            var pure = TransfurVariantInstance.variantFor(
                    ChangedTransfurVariants.PURE_WHITE_LATEX_WOLF.get(), player);
            ((PlayerDataExtension) player).setTransfurVariant(pure);
            pure.transfurProgressionO = pure.transfurProgression = 1;
            pure.sync(player);
            var body = pure.getChangedEntity();
            CompoundTag adapted = new CompoundTag(); adapted.putBoolean("Adapted", true);
            ((PureWhiteWolfAdaptation.PlayerState) pure).changedSynergy$setPureWhiteAdaptation(adapted);
            var visual = PureWhitePlayerAppearance.forRender(body);
            check(visual instanceof WhiteLatexWolfMale && visual != body,
                    "Adapted pure-white player renders the eyed male model");
            check(java.util.Objects.equals(visual.getCustomName(), body.getCustomName()),
                    "Adapted rendering preserves the original player display name");
            check(ProcessTransfur.getPlayerTransfurVariant(player) == pure
                            && pure.getChangedEntity() == body,
                    "Rendering cannot replace actual player form");
            check(PureWhitePlayerAppearance.forRender(body) == visual,
                    "Visual surrogate is reused across renders");
            pure.setTemporaryForSuit(true);
            check(PureWhitePlayerAppearance.forRender(body) == body,
                    "Temporary wrapping never uses the player's adapted model");
            pure.setTemporaryForSuit(false);
            ((PureWhiteWolfAdaptation.PlayerState) pure).changedSynergy$setPureWhiteAdaptation(new CompoundTag());
            check(PureWhitePlayerAppearance.forRender(body) == body,
                    "Cleared adaptation renders original pure-white model");
            check(!PatAnimationClientState.applyRightArmPose(player.getId(), 0, null),
                    "True armless models stay safe");
            System.out.println("PLAYER_VISUAL_CLIENT_PASS");
        } finally {
            ((PlayerDataExtension) player).setTransfurVariant(previous);
            ((ClientLevel) player.level()).setGameTime(time);
            player.refreshDimensions();
        }
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
        System.out.println("VISUAL_CLIENT_CHECK: " + message);
    }
}
