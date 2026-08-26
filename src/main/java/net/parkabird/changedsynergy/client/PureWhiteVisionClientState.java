package net.parkabird.changedsynergy.client;

import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.HunterFaction;
import net.parkabird.changedsynergy.network.TerritorySyncPacket;
import net.parkabird.changedsynergy.util.PureWhiteVision;

/** Smooth client state for pure-white vision entering and leaving hive territory. */
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class PureWhiteVisionClientState {
    private static final ResourceLocation WHITE_LATEX_FOREST =
            ResourceLocation.fromNamespaceAndPath("changed", "white_latex_forest");
    private static final TagKey<Block> WHITE_TERRITORY_BLOCKS = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(
                    ChangedSynergyMod.MOD_ID, "white_territory_blocks"));
    private static final int SAMPLE_INTERVAL_TICKS = 5;

    private static boolean sampledWhiteTerritory;
    private static long nextBlockSampleTick;
    private static float effectStrength;
    private static float territoryBlend;
    private static float pulse;

    private PureWhiteVisionClientState() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            reset();
            return;
        }

        var variant = ProcessTransfur.getPlayerTransfurVariantSafe(player);
        boolean formActive = variant.map(PureWhiteVision::isPureWhiteForm)
                .orElse(false);
        long now = player.level().getGameTime();
        float strengthTarget = formActive ? 1.0F : 0.0F;
        effectStrength += (strengthTarget - effectStrength) * 0.12F;
        if (Math.abs(strengthTarget - effectStrength) < 0.002F) {
            effectStrength = strengthTarget;
        }

        if (formActive && now >= nextBlockSampleTick) {
            sampledWhiteTerritory = hasNearbyWhiteLatex(player);
            nextBlockSampleTick = now + SAMPLE_INTERVAL_TICKS;
        }
        float target = formActive && isWhiteTerritory(player) ? 1.0F : 0.0F;
        territoryBlend += (target - territoryBlend) * 0.075F;
        if (Math.abs(target - territoryBlend) < 0.002F) {
            territoryBlend = target;
        }
        pulse = 0.5F + 0.5F * (float)Math.sin(now * 0.12D);
    }

    public static boolean active() {
        return effectStrength > 0.01F;
    }

    public static float effectStrength() {
        return effectStrength;
    }

    public static float territoryBlend() {
        return territoryBlend;
    }

    public static float pulse() {
        return pulse;
    }

    private static boolean isWhiteTerritory(LocalPlayer player) {
        TerritorySyncPacket territory = TerritoryClientState.current();
        if (territory != null
                && territory.factionOrdinal() == HunterFaction.WHITE.ordinal()) {
            return true;
        }
        boolean whiteBiome = player.level().getBiome(player.blockPosition())
                .unwrapKey()
                .map(key -> WHITE_LATEX_FOREST.equals(key.location()))
                .orElse(false);
        return whiteBiome || sampledWhiteTerritory;
    }

    private static boolean hasNearbyWhiteLatex(LocalPlayer player) {
        BlockPos origin = player.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int matches = 0;
        for (int y = -2; y <= 2; y++) {
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    cursor.set(
                            origin.getX() + x,
                            origin.getY() + y,
                            origin.getZ() + z);
                    if (player.level().hasChunkAt(cursor)
                            && player.level().getBlockState(cursor)
                                    .is(WHITE_TERRITORY_BLOCKS)
                            && ++matches >= 4) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void reset() {
        sampledWhiteTerritory = false;
        nextBlockSampleTick = 0L;
        effectStrength = 0.0F;
        territoryBlend = 0.0F;
        pulse = 0.0F;
    }
}
