package net.parkabird.changedsynergy.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Properties;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.parkabird.changedsynergy.ChangedSynergyClientConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.CreatureSocialProfile;
import net.parkabird.changedsynergy.ai.RelationshipFavorService;

/**
 * Contextual, one-time hints rendered through Minecraft's ordinary top-right
 * tutorial toast. Seen flags are client-wide, matching vanilla's tutorial
 * behaviour instead of repeating once per world or creature.
 */
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MechanicHintClientEvents {
    private static final int OBSERVE_TICKS = 18;
    private static final int BETWEEN_HINTS_TICKS = 240;
    private static final Path STATE_FILE = FMLPaths.CONFIGDIR.get()
            .resolve("changed_synergy-hints.properties");
    private static final EnumSet<Hint> SEEN = EnumSet.noneOf(Hint.class);

    private static boolean loaded;
    private static int observedEntity = -1;
    private static int observedTicks;
    private static int nextHintTick;

    private MechanicHintClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || !ChangedSynergyClientConfig.CLIENT.mechanicHints.get()) {
            resetObservation();
            return;
        }
        loadSeenHints();
        if (SEEN.size() == Hint.values().length
                || minecraft.screen != null
                || minecraft.isPaused()
                || minecraft.player.isSpectator()) {
            resetObservation();
            return;
        }

        ChangedEntity creature = lookedAtCreature(minecraft);
        if (creature == null) {
            resetObservation();
            return;
        }
        if (observedEntity != creature.getId()) {
            observedEntity = creature.getId();
            observedTicks = 0;
        }
        if (++observedTicks < OBSERVE_TICKS
                || minecraft.player.tickCount < nextHintTick) {
            return;
        }

        Hint hint = chooseHint(minecraft, creature);
        if (hint == null) {
            return;
        }
        showHint(minecraft, hint);
        markSeen(hint);
        observedTicks = 0;
        nextHintTick = minecraft.player.tickCount + BETWEEN_HINTS_TICKS;
    }

    @Nullable
    private static ChangedEntity lookedAtCreature(Minecraft minecraft) {
        if (!(minecraft.hitResult instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof ChangedEntity creature)
                || !creature.isAlive()
                || minecraft.player.distanceToSqr(creature) > 36.0D
                || !CreatureSocialProfile.allowsSocialWheel(creature)) {
            return null;
        }
        return creature;
    }

    @Nullable
    private static Hint chooseHint(
            Minecraft minecraft,
            ChangedEntity creature) {
        if (!SEEN.contains(Hint.ORANGE_GIFT)
                && RelationshipFavorService.isOrange(
                        minecraft.player.getMainHandItem())
                && RelationshipFavorService.acceptsOrange(creature)) {
            return Hint.ORANGE_GIFT;
        }
        if (!SEEN.contains(Hint.PAT)) {
            return Hint.PAT;
        }
        if (!SEEN.contains(Hint.SOCIAL)) {
            return Hint.SOCIAL;
        }
        return null;
    }

    private static void showHint(Minecraft minecraft, Hint hint) {
        Component title = Component.translatable(hint.titleKey);
        Component message = switch (hint) {
            case PAT -> Component.translatable(
                    hint.messageKey,
                    SynergyKeyMappings.PAT.getTranslatedKeyMessage());
            case SOCIAL, ORANGE_GIFT -> Component.translatable(
                    hint.messageKey,
                    minecraft.options.keyUse.getTranslatedKeyMessage());
        };
        minecraft.getToasts().addToast(SystemToast.multiline(
                minecraft,
                SystemToast.SystemToastIds.TUTORIAL_HINT,
                title,
                message));
    }

    private static void loadSeenHints() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!Files.isRegularFile(STATE_FILE)) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(STATE_FILE)) {
            properties.load(input);
            for (Hint hint : Hint.values()) {
                if (Boolean.parseBoolean(properties.getProperty(hint.key, "false"))) {
                    SEEN.add(hint);
                }
            }
        } catch (IOException exception) {
            ChangedSynergyMod.LOGGER.warn(
                    "Could not read Synergy mechanic-hint state", exception);
        }
    }

    private static void markSeen(Hint hint) {
        if (!SEEN.add(hint)) {
            return;
        }
        Properties properties = new Properties();
        for (Hint value : Hint.values()) {
            properties.setProperty(value.key,
                    Boolean.toString(SEEN.contains(value)));
        }
        try {
            Files.createDirectories(STATE_FILE.getParent());
            try (OutputStream output = Files.newOutputStream(STATE_FILE)) {
                properties.store(output,
                        "Changed: Synergy one-time mechanic hints");
            }
        } catch (IOException exception) {
            ChangedSynergyMod.LOGGER.warn(
                    "Could not save Synergy mechanic-hint state", exception);
        }
    }

    private static void resetObservation() {
        observedEntity = -1;
        observedTicks = 0;
    }

    private enum Hint {
        PAT("pat", "hint.changed_synergy.pat.title",
                "hint.changed_synergy.pat.message"),
        SOCIAL("social", "hint.changed_synergy.social.title",
                "hint.changed_synergy.social.message"),
        ORANGE_GIFT("orange_gift", "hint.changed_synergy.orange_gift.title",
                "hint.changed_synergy.orange_gift.message");

        private final String key;
        private final String titleKey;
        private final String messageKey;

        Hint(String key, String titleKey, String messageKey) {
            this.key = key;
            this.titleKey = titleKey;
            this.messageKey = messageKey;
        }
    }
}
