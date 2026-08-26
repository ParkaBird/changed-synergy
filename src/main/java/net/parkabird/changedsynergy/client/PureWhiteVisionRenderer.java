package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;

/** Runs Synergy's independent pure-white colour and consensus post-process. */
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class PureWhiteVisionRenderer {
    private static final ResourceLocation EFFECT =
            ResourceLocation.fromNamespaceAndPath(
                    ChangedSynergyMod.MOD_ID,
                    "shaders/post/pure_white_consensus.json");

    private static PostChain chain;
    private static EffectInstance consensusEffect;
    private static boolean reloadRequested;
    private static boolean loadFailed;
    private static boolean historyReady;
    private static int width = -1;
    private static int height = -1;

    private PureWhiteVisionRenderer() {
    }

    /**
     * Apply at one stable world-render stage whether the HUD is visible or hidden.
     * Running this chain from RenderGuiEvent made the depth buffer unreliable and
     * caused the shader's distant-field blend to become an opaque white veil.
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL
                && minecraft.screen == null) {
            render(event.getPartialTick());
        }
    }

    public static void render(float partialTick) {
        if (!PureWhiteVisionClientState.active()) {
            historyReady = false;
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !ensureChain(minecraft)) {
            return;
        }

        int currentWidth = minecraft.getWindow().getWidth();
        int currentHeight = minecraft.getWindow().getHeight();
        if (currentWidth != width || currentHeight != height) {
            chain.resize(currentWidth, currentHeight);
            width = currentWidth;
            height = currentHeight;
            historyReady = false;
        }

        if (consensusEffect != null) {
            set(consensusEffect, "TerritoryBlend",
                    PureWhiteVisionClientState.territoryBlend());
            set(consensusEffect, "Strength",
                    PureWhiteVisionClientState.effectStrength());
            set(consensusEffect, "Pulse",
                    PureWhiteVisionClientState.pulse());
            set(consensusEffect, "DepthAvailable",
                    minecraft.getMainRenderTarget().getDepthTextureId() > 0
                            ? 1.0F : 0.0F);
            set(consensusEffect, "HistoryReady", historyReady ? 1.0F : 0.0F);
        }

        try {
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.resetTextureMatrix();
            chain.process(partialTick);
            historyReady = true;
        } catch (RuntimeException exception) {
            ChangedSynergyMod.LOGGER.error(
                    "Disabling pure-white consensus vision after a render failure",
                    exception);
            closeChain();
            loadFailed = true;
        } finally {
            minecraft.getMainRenderTarget().bindWrite(true);
        }
    }

    public static void requestReload() {
        reloadRequested = true;
    }

    private static boolean ensureChain(Minecraft minecraft) {
        if (reloadRequested) {
            closeChain();
            reloadRequested = false;
            loadFailed = false;
        }
        if (chain != null) {
            return true;
        }
        if (loadFailed) {
            return false;
        }
        try {
            chain = new PostChain(
                    minecraft.getTextureManager(),
                    minecraft.getResourceManager(),
                    minecraft.getMainRenderTarget(),
                    EFFECT);
            var swapTarget = chain.getTempTarget("swap");
            var historyTarget = chain.getTempTarget("history");
            if (swapTarget == null) {
                throw new IOException("Pure-white vision swap target was not created");
            }
            if (historyTarget == null) {
                throw new IOException("Pure-white vision history target was not created");
            }
            var mainTarget = minecraft.getMainRenderTarget();
            PostPass consensusPass = chain.addPass(
                    "changed_synergy/pure_white_consensus",
                    mainTarget,
                    swapTarget);
            int initialWidth = minecraft.getWindow().getWidth();
            int initialHeight = minecraft.getWindow().getHeight();
            consensusPass.addAuxAsset(
                    "DepthSampler",
                    () -> {
                        int depthTexture = mainTarget.getDepthTextureId();
                        return depthTexture > 0
                                ? depthTexture
                                : mainTarget.getColorTextureId();
                    },
                    initialWidth,
                    initialHeight);
            consensusPass.addAuxAsset(
                    "HistorySampler",
                    historyTarget::getColorTextureId,
                    initialWidth,
                    initialHeight);
            chain.addPass(
                    "blit",
                    swapTarget,
                    mainTarget);
            chain.addPass(
                    "blit",
                    swapTarget,
                    historyTarget);
            consensusEffect = consensusPass.getEffect();
            width = -1;
            height = -1;
            return true;
        } catch (IOException | RuntimeException exception) {
            loadFailed = true;
            ChangedSynergyMod.LOGGER.error(
                    "Unable to load pure-white consensus vision", exception);
            return false;
        }
    }

    private static void set(
            EffectInstance effect,
            String name,
            float value) {
        Uniform uniform = effect.getUniform(name);
        if (uniform != null) {
            uniform.set(value);
        }
    }

    private static void closeChain() {
        if (chain != null) {
            chain.close();
            chain = null;
        }
        consensusEffect = null;
        historyReady = false;
        width = -1;
        height = -1;
    }
}
