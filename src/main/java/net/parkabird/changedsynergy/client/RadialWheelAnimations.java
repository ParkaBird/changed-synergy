package net.parkabird.changedsynergy.client;

import net.ltxprogrammer.changed.client.gui.AbilityRadialScreen;
import net.ltxprogrammer.changed.client.gui.AbstractRadialScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** Public helpers used by Synergy screens without coupling them to the mixin. */
public final class RadialWheelAnimations {
    // Optical centres of Changed's eight asymmetric frame interiors, excluding drips.
    private static final int[][] GOO_CENTRES = {{-5,4},{-4,5},{-5,-4},{-5,-3},{4,-3},{4,-5},{4,6},{6,5}};
    private static final int[][] ORGANIC_CENTRES = {{-7,4},{-4,7},{-4,-7},{-7,-4},{7,-4},{4,-7},{4,7},{7,4}};
    private static final long SWITCH_HANDOFF_TIMEOUT_NANOS = 3_000_000_000L;
    private static WheelKind pendingArrival = WheelKind.NONE;
    private static long pendingArrivalNanos;
    private static int pendingEntityId = -1;
    private static UiSpring pendingSlide;
    private static boolean pendingInstantArrival;
    private static WheelKind cancelledArrival = WheelKind.NONE;
    private static int cancelledEntityId = -1;
    private static long cancelledUntil;

    private RadialWheelAnimations() {
    }

    public static boolean requestClose(Object screen, Runnable completion) {
        if (pendingInstantArrival && entityId(screen) == pendingEntityId) cancelSwitchAndArrival();
        return request(screen, completion, false);
    }

    public static float panelAlpha(Object screen) {
        return screen instanceof RadialWheelAnimationAccess access ? access.changedSynergy$getPanelAlpha() : 1.0F;
    }

    public static boolean instantArrival(Object screen) {
        return screen instanceof RadialWheelAnimationAccess access && access.changedSynergy$isInstantArrival();
    }

    public static boolean requestWheelSwitch(Object screen, Runnable completion) {
        WheelKind target = switchTarget(screen);
        return requestWheelSwitch(screen, completion, target);
    }

    /** Smoothly hands any social/ability wheel off to negotiation. */
    public static boolean requestNegotiationSwitch(
            Object screen,
            Runnable completion) {
        if (kindOf(screen) == WheelKind.NEGOTIATION) {
            return false;
        }
        return requestWheelSwitch(
                screen, completion, WheelKind.NEGOTIATION);
    }

    private static boolean requestWheelSwitch(
            Object screen,
            Runnable completion,
            WheelKind target) {
        if (target == WheelKind.NONE || hasPendingSwitch()) {
            return false;
        }
        clearCancelledArrival();
        pendingArrival = target;
        pendingArrivalNanos = System.nanoTime();
        pendingInstantArrival = target == WheelKind.NEGOTIATION && screen instanceof SocialInteractionScreen;
        pendingSlide = isContinuousPlayerSwitch(screen) && screen instanceof RadialWheelAnimationAccess access
                ? access.changedSynergy$getHorizontalSpring() : null;
        // Absorption negotiation starts from the player's ability wheel but
        // opens a synthetic creature preview, so there is no shared entity id
        // to compare across that particular hand-off.
        pendingEntityId = target == WheelKind.NEGOTIATION
                        && screen instanceof AbilityRadialScreen
                ? -1 : entityId(screen);
        // Both NPC pages already use the right-hand anchor; no departure motion is needed.
        boolean accepted = !pendingInstantArrival && request(screen, completion, true);
        if (!accepted) {
            // The fallback opens the target immediately, so keep the hand-off
            // token available for the target screen's first init pass.
            completion.run();
        }
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(
                        SoundEvents.UI_LOOM_SELECT_PATTERN,
                        0.9F));
        return true;
    }

    public static float layerAlpha(Object screen) {
        return screen instanceof RadialWheelAnimationAccess access
                ? access.changedSynergy$getLayerAlpha()
                : 1.0F;
    }

    /** Retain the native idle artwork, blending its highlight only while selected. */
    public static void drawFrame(Object screen, net.minecraft.client.gui.GuiGraphics graphics,
            net.minecraft.resources.ResourceLocation texture, int x, int y, float u, float v,
            int width, int height, int textureWidth, int textureHeight) {
        String path = texture.getPath();
        int slash = path.lastIndexOf('/');
        if (!(screen instanceof RadialWheelAnimationAccess access) || !path.startsWith("textures/gui/radial/")
                || !path.endsWith(".png") || slash != path.length() - 6 || path.charAt(slash + 1) < '0' || path.charAt(slash + 1) > '7') {
            graphics.blit(texture, x, y, u, v, width, height, textureWidth, textureHeight);
            return;
        }
        float blend = access.changedSynergy$getFrameHighlight(path.charAt(slash + 1) - '0', path.contains("_selected/"));
        String normalPath = path.replace("_selected/", "/");
        var normal = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(texture.getNamespace(), normalPath);
        var selected = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(texture.getNamespace(),
                normalPath.substring(0, normalPath.lastIndexOf('/')) + "_selected/" + normalPath.substring(normalPath.lastIndexOf('/') + 1));
        float[] color = com.mojang.blaze3d.systems.RenderSystem.getShaderColor();
        float red = color[0], green = color[1], blue = color[2], alpha = color[3];
        float selectedAlpha = alpha * blend;
        // Compensate source-over blending so the shared frame interior keeps its opening opacity.
        float normalAlpha = selectedAlpha >= 1 ? 0 : alpha * (1 - blend) / (1 - selectedAlpha);
        if (normalAlpha > 0) {
            graphics.setColor(red, green, blue, normalAlpha);
            graphics.blit(normal, x, y, u, v, width, height, textureWidth, textureHeight);
        }
        if (selectedAlpha > 0) {
            graphics.setColor(red, green, blue, selectedAlpha);
            graphics.blit(selected, x, y, u, v, width, height, textureWidth, textureHeight);
        }
        graphics.setColor(red, green, blue, alpha);
    }

    public static net.minecraft.network.chat.Component scrollHint(AbstractRadialScreen<?> screen,String switchKey){
        var text=net.minecraft.network.chat.Component.empty();
        if(screen instanceof AbilityRadialScreen ability&&AbilityWheelInput.showHoldHint(ability))text.append(net.minecraft.network.chat.Component.translatable("menu.changed_synergy.ability.hold_hint")).append(" · ");
        if(screen.getCount()>8)text.append(net.minecraft.network.chat.Component.translatable("menu.changed_synergy.relationship.more_options")).append(" · ");
        if(screen.getCount()>8)text.append(net.minecraft.network.chat.Component.translatable(switchKey+".shift"));
        else text.append(net.minecraft.network.chat.Component.translatable(switchKey));
        return text;
    }

    public static float overallAlpha(Object screen) {
        return screen instanceof RadialWheelAnimationAccess access
                ? access.changedSynergy$getOverallAlpha()
                : 1.0F;
    }

    public static float horizontalOffset(Object screen) {
        return screen instanceof RadialWheelAnimationAccess access
                ? access.changedSynergy$getHorizontalOffset()
                : 0.0F;
    }

    /** Final layout anchor, independent of the wheel's animated position. */
    public static float layoutOffset(int screenWidth) {
        return Math.round(Math.min(Math.min(220.0F, screenWidth * 0.22F), Math.max(0.0F, screenWidth * 0.5F - 136.0F)));
    }

    /** Frames stay in eight fixed slots while the scrolled action indices advance. */
    public static int viewOffset(Object screen) {
        return screen instanceof RadialWheelAnimationAccess access ? access.changedSynergy$getViewOffset() : 0;
    }

    public static int iconOffset(AbstractRadialScreen<?> screen, int section, boolean vertical) {
        boolean organic;
        if (screen instanceof SocialInteractionScreen social) organic = social.menu.isOrganic();
        else if (screen instanceof PlayerRelationshipScreen relationships) organic = relationships.menu.usesOrganicStyle();
        else if (screen instanceof BondedLatexScreen bonded) organic = bonded.menu.isOrganicPet();
        else if (screen instanceof TakeoverScreen || screen instanceof NativeEmoteScreen) organic = false;
        else {
            var form = screen.centerEntity instanceof net.minecraft.world.entity.player.Player player
                    ? net.ltxprogrammer.changed.process.ProcessTransfur.getPlayerTransfurVariant(player) : null;
            organic = form != null && !form.getParent().getEntityType().is(net.ltxprogrammer.changed.init.ChangedTags.EntityTypes.LATEX);
        }
        float offset=screen instanceof RadialWheelAnimationAccess access?access.changedSynergy$getScrollOffset():viewOffset(screen);
        float slot = Math.max(0,Math.min(7,section-offset));
        int first=(int)Math.floor(slot),last=Math.min(7,first+1),axis=vertical?1:0;
        var centers=organic?ORGANIC_CENTRES:GOO_CENTRES;
        return Math.round(net.minecraft.util.Mth.lerp(slot-first,centers[first][axis],centers[last][axis]));
    }

    /** Preserve the asymmetric resting slots without jumping when a scroll crosses an integer. */
    public static net.minecraft.world.phys.Vec2 optionPosition(int section,float offset){
        double position=section-offset,slot=Math.max(0,Math.min(7,position));
        double angle=(slot+.5+.04*Math.cos(slot*Math.PI))*Math.PI/4;
        double outward=1+Math.max(0,Math.max(-position,position-7));
        return new net.minecraft.world.phys.Vec2((float)(Math.sin(angle)*90),(float)(-Math.cos(angle)*90*outward));
    }

    public static boolean isSectionSelected(AbstractRadialScreen<?> screen, int slot, int mouseX, int mouseY) {
        return screen.getSectionAt(mouseX, mouseY).filter(index -> index == slot + viewOffset(screen)).isPresent();
    }

    /** Consumed once by the newly opened half of a paired wheel switch. */
    public static boolean consumeSwitchArrival(Object screen) {
        if (pendingArrival == WheelKind.NONE) {
            return false;
        }
        if (System.nanoTime() - pendingArrivalNanos
                > SWITCH_HANDOFF_TIMEOUT_NANOS) {
            cancelPendingSwitch();
            return false;
        }
        if (kindOf(screen) != pendingArrival
                || pendingEntityId >= 0
                        && entityId(screen) != pendingEntityId) {
            return false;
        }
        if (pendingSlide != null && screen instanceof RadialWheelAnimationAccess access)
            access.changedSynergy$continueSlide(pendingSlide);
        if (pendingInstantArrival && screen instanceof RadialWheelAnimationAccess access)
            access.changedSynergy$openImmediately();
        cancelPendingSwitch();
        return true;
    }

    public static boolean isContinuousPlayerSwitch(Object screen) {
        return screen instanceof AbilityRadialScreen && pendingArrival == WheelKind.PLAYER_RELATIONSHIPS
                || screen instanceof PlayerRelationshipScreen && pendingArrival == WheelKind.ABILITY;
    }

    private static boolean hasPendingSwitch() {
        if (pendingArrival == WheelKind.NONE) {
            return false;
        }
        if (System.nanoTime() - pendingArrivalNanos
                <= SWITCH_HANDOFF_TIMEOUT_NANOS) {
            return true;
        }
        cancelPendingSwitch();
        return false;
    }

    public static void cancelPendingSwitch() {
        pendingArrival = WheelKind.NONE;
        pendingEntityId = -1;
        pendingSlide = null;
        pendingInstantArrival = false;
    }

    public static void cancelSwitchAndArrival() {
        cancelledArrival = pendingArrival;
        cancelledEntityId = pendingEntityId;
        cancelledUntil = System.nanoTime() + SWITCH_HANDOFF_TIMEOUT_NANOS;
        cancelPendingSwitch();
    }

    public static void clearCancelledArrival() { cancelledArrival = WheelKind.NONE; }

    public static boolean rejectCancelledArrival(Object screen) {
        return cancelledArrival != WheelKind.NONE && System.nanoTime() < cancelledUntil
                && kindOf(screen) == cancelledArrival
                && (cancelledEntityId < 0 || entityId(screen) == cancelledEntityId);
    }

    private static boolean request(
            Object screen,
            Runnable completion,
            boolean wheelSwitch) {
        return screen instanceof RadialWheelAnimationAccess access
                && access.changedSynergy$startClosing(completion, wheelSwitch);
    }

    private static WheelKind switchTarget(Object screen) {
        if (screen instanceof AbilityRadialScreen) {
            return WheelKind.PLAYER_RELATIONSHIPS;
        }
        if (screen instanceof PlayerRelationshipScreen relationships
                && relationships.menu.isTransfurred()) {
            return WheelKind.ABILITY;
        }
        if (screen instanceof BondedLatexScreen) {
            return WheelKind.SOCIAL;
        }
        if (screen instanceof SocialInteractionScreen social
                && social.menu.isBondedMode()) {
            return WheelKind.FUNCTION;
        }
        return WheelKind.NONE;
    }

    private static WheelKind kindOf(Object screen) {
        if (screen instanceof AbilityRadialScreen) {
            return WheelKind.ABILITY;
        }
        if (screen instanceof PlayerRelationshipScreen) {
            return WheelKind.PLAYER_RELATIONSHIPS;
        }
        if (screen instanceof BondedLatexScreen) {
            return WheelKind.FUNCTION;
        }
        if (screen instanceof SocialInteractionScreen social
                && social.menu.isNegotiationMode()) {
            return WheelKind.NEGOTIATION;
        }
        if (screen instanceof SocialInteractionScreen social
                && social.menu.isBondedMode()) {
            return WheelKind.SOCIAL;
        }
        return WheelKind.NONE;
    }

    private static int entityId(Object screen) {
        if (screen instanceof AbilityRadialScreen ability) {
            return ability.menu.player.getId();
        }
        if (screen instanceof PlayerRelationshipScreen relationships) {
            return relationships.menu.getPlayer().getId();
        }
        if (screen instanceof BondedLatexScreen bonded
                && bonded.menu.getPet() != null) {
            return bonded.menu.getPet().getId();
        }
        if (screen instanceof SocialInteractionScreen social
                && social.menu.getCreature() != null) {
            return social.menu.getCreature().getId();
        }
        return -1;
    }

    private enum WheelKind {
        NONE,
        ABILITY,
        PLAYER_RELATIONSHIPS,
        FUNCTION,
        SOCIAL,
        NEGOTIATION
    }
}
