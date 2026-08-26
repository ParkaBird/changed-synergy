package net.parkabird.changedsynergy.client;

import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.parkabird.changedsynergy.ai.FactionReputation;
import net.parkabird.changedsynergy.ai.HunterFaction;
import net.parkabird.changedsynergy.network.TerritorySyncPacket;

/** Current facility HUD data and the animated territory subtitle. */
public final class TerritoryClientState {
    private static final long SUBTITLE_MILLIS = 4_000L;
    private static final long SUBTITLE_COOLDOWN_MILLIS = 6_000L;
    private static final long PANEL_EXPAND_MILLIS = 340L;
    private static final long PANEL_FADE_MILLIS = 140L;
    private static final long TEXT_FADE_MILLIS = 260L;
    private static final long EXIT_FADE_MILLIS = 700L;
    private static final long CONTENT_TRANSITION_MILLIS = 560L;

    private static TerritorySyncPacket current;
    private static TerritorySyncPacket pendingSubtitle;
    private static TerritorySyncPacket lastDisplayedSubtitle;
    private static TerritorySyncPacket displayedSubtitle;
    private static TerritorySyncPacket incomingSubtitle;

    private static Component subtitleTitle = Component.empty();
    private static Component subtitleDetail = Component.empty();
    private static Component incomingTitle = Component.empty();
    private static Component incomingDetail = Component.empty();

    private static long subtitleOpenedAt;
    private static long subtitleExpiresAt;
    private static long nextSubtitleAllowed;
    private static long contentTransitionStarted;
    private static int transitionAccentFrom = 0xB8C0C8;

    private TerritoryClientState() {
    }

    public static void receive(TerritorySyncPacket packet) {
        long now = Util.getMillis();
        updateContentTransition(now);
        if (packet.equals(current)) {
            return;
        }

        TerritorySyncPacket previous = current;
        current = packet;
        if (previous != null && sameArea(previous, packet)) {
            updateMatchingPackets(packet);
            return;
        }

        if (!hasTerritorySubtitle(packet)) {
            pendingSubtitle = null;
            incomingSubtitle = null;
            incomingTitle = Component.empty();
            incomingDetail = Component.empty();
            contentTransitionStarted = 0L;
            if (isSubtitleVisible(now)) {
                subtitleExpiresAt = Math.min(
                        subtitleExpiresAt,
                        now + EXIT_FADE_MILLIS);
            } else {
                clearPresentation();
            }
            return;
        }

        if (isSubtitleVisible(now)) {
            beginContentTransition(packet, now);
            return;
        }

        if (now >= nextSubtitleAllowed) {
            beginSubtitle(packet, now);
        } else if (lastDisplayedSubtitle != null
                && sameArea(packet, lastDisplayedSubtitle)) {
            // A quick step across a border and back must not queue the same
            // territory for another full entrance animation.
            pendingSubtitle = null;
        } else {
            pendingSubtitle = packet;
        }
    }

    public static TerritorySyncPacket current() {
        return current;
    }

    /**
     * Current location data is independent from the short-lived subtitle
     * presentation.  Persistent screens must use these accessors instead of
     * subtitleTitle/subtitleDetail, which are deliberately cleared after the
     * entrance animation expires.
     */
    public static Component currentAreaTitle() {
        TerritorySyncPacket state = current;
        if (state == null) {
            return Component.translatable(
                    "overlay.changed_synergy.territory.unknown");
        }
        if (state.facility()) {
            return Component.translatable(
                    "overlay.changed_synergy.facility.site",
                    state.facilityCode());
        }
        return state.biomeId().isBlank()
                ? Component.translatable(
                        "overlay.changed_synergy.territory.unknown")
                : biomeName(state.biomeId());
    }

    public static Component currentAreaDetail() {
        TerritorySyncPacket state = current;
        if (state == null) {
            return Component.empty();
        }
        if (state.facility()) {
            return zoneName(state.zoneId());
        }
        return state.populationRegion().isBlank()
                ? Component.empty()
                : populationName(
                        state.populationRegion(), state.biomeId());
    }

    public static boolean hasCurrentArea() {
        TerritorySyncPacket state = current;
        return state != null
                && (state.facility() || !state.biomeId().isBlank());
    }

    public static Component subtitleTitle() {
        refreshAnimationState();
        return subtitleTitle;
    }

    public static Component subtitleDetail() {
        refreshAnimationState();
        return subtitleDetail;
    }

    public static Component incomingTitle() {
        refreshAnimationState();
        return incomingTitle;
    }

    public static Component incomingDetail() {
        refreshAnimationState();
        return incomingDetail;
    }

    public static boolean subtitleActive() {
        long now = Util.getMillis();
        updateContentTransition(now);
        if (displayedSubtitle != null && now >= subtitleExpiresAt) {
            clearPresentation();
        }
        beginPendingSubtitleIfReady(now);
        return isSubtitleVisible(now);
    }

    public static boolean subtitleTransitioning() {
        refreshAnimationState();
        return incomingSubtitle != null;
    }

    public static int subtitleFactionOrdinal() {
        refreshAnimationState();
        return displayedSubtitle == null
                ? -1
                : displayedSubtitle.factionOrdinal();
    }

    public static int subtitleReputationScore() {
        refreshAnimationState();
        return displayedSubtitle == null
                ? 0
                : displayedSubtitle.reputationScore();
    }

    public static int incomingFactionOrdinal() {
        refreshAnimationState();
        return incomingSubtitle == null
                ? -1
                : incomingSubtitle.factionOrdinal();
    }

    public static int incomingReputationScore() {
        refreshAnimationState();
        return incomingSubtitle == null
                ? 0
                : incomingSubtitle.reputationScore();
    }

    /** Horizontal reveal of the subtitle panel, from its centre outward. */
    public static float panelExpansion() {
        if (displayedSubtitle == null) {
            return 0.0F;
        }
        return smooth((Util.getMillis() - subtitleOpenedAt)
                / (float)PANEL_EXPAND_MILLIS);
    }

    public static float backgroundAlpha() {
        if (displayedSubtitle == null) {
            return 0.0F;
        }
        long now = Util.getMillis();
        float entrance = smooth((now - subtitleOpenedAt)
                / (float)PANEL_FADE_MILLIS);
        return entrance * exitAlpha(now);
    }

    public static float outgoingTextAlpha() {
        long now = Util.getMillis();
        float base = baseTextAlpha(now);
        if (incomingSubtitle == null) {
            return base;
        }
        float raw = transitionRaw(now);
        return base * (1.0F - smooth(raw / 0.48F));
    }

    public static float incomingTextAlpha() {
        if (incomingSubtitle == null) {
            return 0.0F;
        }
        long now = Util.getMillis();
        float raw = transitionRaw(now);
        return baseTextAlpha(now)
                * smooth((raw - 0.52F) / 0.48F);
    }

    /** Smooth progress used for panel resizing while subtitle text changes. */
    public static float contentTransitionProgress() {
        refreshAnimationState();
        return incomingSubtitle == null
                ? 0.0F
                : smooth(transitionRaw(Util.getMillis()));
    }

    /**
     * The ornament always represents territorial faction only. Reputation
     * must never recolour it.
     */
    public static int subtitleOrnamentColor() {
        long now = Util.getMillis();
        updateContentTransition(now);
        if (displayedSubtitle == null) {
            return color(-1);
        }
        if (incomingSubtitle == null) {
            return color(displayedSubtitle.factionOrdinal());
        }
        return blend(
                transitionAccentFrom,
                color(incomingSubtitle.factionOrdinal()),
                smooth(transitionRaw(now)));
    }

    public static int color(int factionOrdinal) {
        HunterFaction[] factions = HunterFaction.values();
        if (factionOrdinal < 0 || factionOrdinal >= factions.length) {
            return 0xB8C0C8;
        }
        return switch (factions[factionOrdinal]) {
            case WHITE -> 0xF2F5FF;
            case DARK -> 0x9B59D0;
            case AQUATIC -> 0x4FD6E8;
            case LIGHT -> 0xE5A84B;
        };
    }

    /**
     * Colours the population/faction label from faction-wide standing. The
     * biome label and ornament use the owning faction's theme colour instead.
     */
    public static int factionStandingTextColor(
            int factionReputationScore) {
        return switch (FactionReputation.Standing.forScore(
                factionReputationScore)) {
            case HOSTILE -> 0xF05252;
            case DISTRUSTED -> 0xF0A454;
            case NEUTRAL -> 0xD5D9DE;
            case RECOGNIZED -> 0x9CCFAE;
            case RESPECTED -> 0x6ED99A;
            case ALLIED -> 0x52E58B;
        };
    }

    public static int blend(int from, int to, float amount) {
        float clamped = clamp(amount);
        int red = Math.round(((from >> 16) & 0xFF) * (1.0F - clamped)
                + ((to >> 16) & 0xFF) * clamped);
        int green = Math.round(((from >> 8) & 0xFF) * (1.0F - clamped)
                + ((to >> 8) & 0xFF) * clamped);
        int blue = Math.round((from & 0xFF) * (1.0F - clamped)
                + (to & 0xFF) * clamped);
        return red << 16 | green << 8 | blue;
    }

    public static Component zoneName(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            return Component.translatable(
                    "overlay.changed_synergy.facility.unknown_zone");
        }
        return Component.translatable("facility.zone." + zoneId);
    }

    private static Component biomeName(String biomeId) {
        ResourceLocation id = ResourceLocation.tryParse(biomeId);
        return id == null
                ? Component.translatable(
                        "overlay.changed_synergy.territory.unknown")
                : Component.translatable(id.toLanguageKey("biome"));
    }

    private static Component populationName(
            String populationRegion,
            String biomeId) {
        if ("biome".equals(populationRegion)) {
            return Component.translatable(
                    "overlay.changed_synergy.territory.population.biome",
                    biomeName(biomeId));
        }
        return Component.translatable(
                "overlay.changed_synergy.territory.population."
                        + populationRegion);
    }

    private static void beginPendingSubtitleIfReady(long now) {
        if (pendingSubtitle == null
                || !pendingSubtitle.equals(current)
                || now < nextSubtitleAllowed) {
            return;
        }
        beginSubtitle(pendingSubtitle, now);
    }

    private static void beginSubtitle(
            TerritorySyncPacket packet,
            long now) {
        pendingSubtitle = null;
        setDisplayedSubtitle(packet);
        incomingSubtitle = null;
        incomingTitle = Component.empty();
        incomingDetail = Component.empty();
        contentTransitionStarted = 0L;
        transitionAccentFrom = color(packet.factionOrdinal());
        lastDisplayedSubtitle = packet;
        subtitleOpenedAt = now;
        subtitleExpiresAt = now + SUBTITLE_MILLIS;
        nextSubtitleAllowed = now + SUBTITLE_COOLDOWN_MILLIS;
    }

    private static void beginContentTransition(
            TerritorySyncPacket packet,
            long now) {
        updateContentTransition(now);
        if (displayedSubtitle == null) {
            beginSubtitle(packet, now);
            return;
        }
        if (sameArea(displayedSubtitle, packet)) {
            setDisplayedSubtitle(packet);
            lastDisplayedSubtitle = packet;
            return;
        }

        int currentAccent = subtitleOrnamentColor();
        if (incomingSubtitle != null
                && transitionRaw(now) >= 0.5F) {
            setDisplayedSubtitle(incomingSubtitle);
        }
        incomingSubtitle = packet;
        incomingTitle = biomeName(packet.biomeId());
        incomingDetail = populationName(
                packet.populationRegion(),
                packet.biomeId());
        transitionAccentFrom = currentAccent;
        contentTransitionStarted = now;
        lastDisplayedSubtitle = packet;
        subtitleExpiresAt = now + SUBTITLE_MILLIS;
        nextSubtitleAllowed = now + SUBTITLE_COOLDOWN_MILLIS;
    }

    private static void updateContentTransition(long now) {
        if (incomingSubtitle == null
                || transitionRaw(now) < 1.0F) {
            return;
        }
        setDisplayedSubtitle(incomingSubtitle);
        transitionAccentFrom = color(
                incomingSubtitle.factionOrdinal());
        incomingSubtitle = null;
        incomingTitle = Component.empty();
        incomingDetail = Component.empty();
        contentTransitionStarted = 0L;
    }

    private static void updateMatchingPackets(
            TerritorySyncPacket packet) {
        if (pendingSubtitle != null
                && sameArea(pendingSubtitle, packet)) {
            pendingSubtitle = packet;
        }
        if (displayedSubtitle != null
                && sameArea(displayedSubtitle, packet)) {
            setDisplayedSubtitle(packet);
        }
        if (incomingSubtitle != null
                && sameArea(incomingSubtitle, packet)) {
            incomingSubtitle = packet;
            incomingTitle = biomeName(packet.biomeId());
            incomingDetail = populationName(
                    packet.populationRegion(),
                    packet.biomeId());
        }
        if (lastDisplayedSubtitle != null
                && sameArea(lastDisplayedSubtitle, packet)) {
            lastDisplayedSubtitle = packet;
        }
    }

    private static void setDisplayedSubtitle(
            TerritorySyncPacket packet) {
        displayedSubtitle = packet;
        subtitleTitle = biomeName(packet.biomeId());
        subtitleDetail = populationName(
                packet.populationRegion(),
                packet.biomeId());
    }

    private static float baseTextAlpha(long now) {
        if (displayedSubtitle == null) {
            return 0.0F;
        }
        float entrance = smooth((now - subtitleOpenedAt
                - PANEL_EXPAND_MILLIS) / (float)TEXT_FADE_MILLIS);
        return entrance * exitAlpha(now);
    }

    private static float exitAlpha(long now) {
        if (subtitleExpiresAt <= now) {
            return 0.0F;
        }
        return smooth((subtitleExpiresAt - now)
                / (float)EXIT_FADE_MILLIS);
    }

    private static float transitionRaw(long now) {
        if (incomingSubtitle == null || contentTransitionStarted <= 0L) {
            return 0.0F;
        }
        return clamp((now - contentTransitionStarted)
                / (float)CONTENT_TRANSITION_MILLIS);
    }

    private static void refreshAnimationState() {
        updateContentTransition(Util.getMillis());
    }

    private static boolean isSubtitleVisible(long now) {
        return displayedSubtitle != null
                && subtitleOpenedAt > 0L
                && now < subtitleExpiresAt;
    }

    private static boolean hasTerritorySubtitle(
            TerritorySyncPacket packet) {
        return !packet.facility()
                && !packet.biomeId().isBlank()
                && !packet.populationRegion().isBlank();
    }

    private static void clearPresentation() {
        displayedSubtitle = null;
        incomingSubtitle = null;
        subtitleTitle = Component.empty();
        subtitleDetail = Component.empty();
        incomingTitle = Component.empty();
        incomingDetail = Component.empty();
        subtitleOpenedAt = 0L;
        subtitleExpiresAt = 0L;
        contentTransitionStarted = 0L;
        transitionAccentFrom = color(-1);
    }

    private static float smooth(float value) {
        float clamped = clamp(value);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static boolean sameArea(
            TerritorySyncPacket first,
            TerritorySyncPacket second) {
        return first.facility() == second.facility()
                && first.facilityCode().equals(second.facilityCode())
                && first.zoneId().equals(second.zoneId())
                && first.templateId().equals(second.templateId())
                && first.biomeId().equals(second.biomeId())
                && first.factionOrdinal() == second.factionOrdinal()
                && first.populationRegion().equals(second.populationRegion());
    }
}
