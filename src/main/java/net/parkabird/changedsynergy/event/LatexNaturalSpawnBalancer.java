package net.parkabird.changedsynergy.event;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.dialogue.LatexTerritory;

/** Preserves only explicit no-latex biomes; all other population balancing is
 * left to Changed, Changed Addon and Minecraft's native spawn tables/caps. */
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LatexNaturalSpawnBalancer {
    private LatexNaturalSpawnBalancer() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFinalizeNaturalSpawn(
            MobSpawnEvent.FinalizeSpawn event) {
        if (!(event.getEntity() instanceof ChangedEntity creature)
                || !(creature.level() instanceof ServerLevel level)
                || event.isSpawnCancelled()
                || !isNatural(event.getSpawnType())) {
            return;
        }

        Holder<Biome> biome = level.getBiome(creature.blockPosition());
        if (biome.is(LatexTerritory.LATEX_FREE)) {
            event.setSpawnCancelled(true);
        }
    }

    private static boolean isNatural(MobSpawnType type) {
        return type == MobSpawnType.NATURAL
                || type == MobSpawnType.CHUNK_GENERATION;
    }

}
