package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.ltxprogrammer.changed.init.ChangedBlocks;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.ltxprogrammer.changed.network.packet.SyncTransfurPacket;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.FactionReputation;
import net.parkabird.changedsynergy.ai.HunterFaction;
import net.parkabird.changedsynergy.ai.PureWhiteWolfAdaptation;
import net.parkabird.changedsynergy.event.LatexPupCrystalContact;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.world.LatexTerritoryBiomes;

/** Include this source directory in a private server fixture; call run(server). */
public final class PlayerVisualMechanicsRegression {
    private static int checks;

    public static void run(MinecraftServer server) {
        var level = server.overworld();
        var player = new ServerPlayer(server, level,
                new GameProfile(UUID.randomUUID(), "VisualMechanicsTest"));
        player.connection = new ServerGamePacketListenerImpl(server,
                new Connection(PacketFlow.SERVERBOUND), player);
        long previousTime = level.getGameTime();
        boolean oldConfig = ChangedSynergyConfig.COMMON.pureWhiteWolfAdaptation.get();
        boolean oldLife = level.getGameRules().getBoolean(ChangedSynergyGameRules.CREATURE_LIFE);
        try {
            ChangedSynergyConfig.COMMON.pureWhiteWolfAdaptation.set(true);
            level.getGameRules().getRule(ChangedSynergyGameRules.CREATURE_LIFE).set(true, server);
            var pure = ProcessTransfur.setPlayerTransfurVariant(player,
                    ChangedTransfurVariants.PURE_WHITE_LATEX_WOLF.get(), null, 1, false);
            var originalBody = pure.getChangedEntity();
            check(pure instanceof PureWhiteWolfAdaptation.PlayerState, "Variant adaptation holder installed");
            var holder = (PureWhiteWolfAdaptation.PlayerState) pure;
            player.tickCount = Math.floorMod(player.getId(), 100);
            // BiomeManager samples neighboring quart cells after subtracting
            // two blocks. Keep the player inside the filled chunk rather than
            // on its corner, where vanilla may select an unchanged neighbor.
            BlockPos home = new BlockPos(56, 80, 56);
            var white = level.registryAccess().registryOrThrow(Registries.BIOME)
                    .getHolderOrThrow(LatexTerritoryBiomes.WHITE_LATEX_FOREST);
            var plains = level.registryAccess().registryOrThrow(Registries.BIOME)
                    .getHolderOrThrow(Biomes.PLAINS);
            level.getChunkAt(home).fillBiomesFromNoise((x, y, z, sampler) -> white,
                    level.getChunkSource().randomState().sampler());
            player.setPos(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
            check(level.getBiome(player.blockPosition()).is(LatexTerritoryBiomes.WHITE_LATEX_FOREST),
                    "Fixture home resolves to white territory through vanilla biome sampling");
            ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(3_000);
            tick(player);
            check(holder.changedSynergy$getPureWhiteAdaptation().contains("HomePosition"), "Home biome remembered");
            BlockPos away = home.offset(96, 0, 0);
            level.getChunkAt(away).fillBiomesFromNoise((x, y, z, sampler) -> plains,
                    level.getChunkSource().randomState().sampler());
            player.setPos(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
            tick(player);
            check(!PureWhiteWolfAdaptation.isAdaptedPlayer(player), "Departure retains the grace period");
            ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(3_199);
            tick(player);
            check(!PureWhiteWolfAdaptation.isAdaptedPlayer(player), "No early visual adaptation");
            ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(3_200);
            tick(player);
            check(PureWhiteWolfAdaptation.isAdaptedPlayer(player), "Adaptation begins after the grace period");
            check(ProcessTransfur.getPlayerTransfurVariant(player) == pure
                    && pure.getChangedEntity() == originalBody
                    && pure.is(ChangedTransfurVariants.PURE_WHITE_LATEX_WOLF)
                    && HunterFaction.of(originalBody) == HunterFaction.WHITE,
                    "Adaptation leaves actual species, body, faction and ability owner unchanged");
            var loaded = TransfurVariantInstance.variantFor(pure.getParent(), player);
            loaded.load(pure.save());
            check(PureWhiteWolfAdaptation.isAdaptedPlayer(loaded), "Adaptation survives original variant save/load");
            packet(player);
            var legacy = pure.save();
            legacy.remove(PureWhiteWolfAdaptation.PLAYER_DATA_KEY);
            loaded.load(legacy);
            check(!PureWhiteWolfAdaptation.isAdaptedPlayer(loaded), "Legacy payload cannot retain a stale adapted appearance");
            loaded.load(pure.save());
            var copy = holder.changedSynergy$getPureWhiteAdaptation();
            copy.putBoolean("Adapted", false);
            check(PureWhiteWolfAdaptation.isAdaptedPlayer(player), "Read state cannot mutate authoritative state");
            player.setPos(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
            tick(player);
            check(!PureWhiteWolfAdaptation.isAdaptedPlayer(player), "Returning to white territory restores original appearance");
            holder.changedSynergy$setPureWhiteAdaptation(loaded instanceof PureWhiteWolfAdaptation.PlayerState state
                    ? state.changedSynergy$getPureWhiteAdaptation() : copy);
            ChangedSynergyConfig.COMMON.pureWhiteWolfAdaptation.set(false);
            pure.setTemporaryForSuit(true);
            tick(player);
            check(!PureWhiteWolfAdaptation.isAdaptedPlayer(player)
                            && holder.changedSynergy$getPureWhiteAdaptation().getBoolean("Adapted"),
                    "Temporary wrapping cannot activate or overwrite native adaptation state");
            pure.setTemporaryForSuit(false);
            tick(player);
            check(!PureWhiteWolfAdaptation.isAdaptedPlayer(player), "Disabling the option restores pure appearance");
            ProcessTransfur.removePlayerTransfurVariant(player);
            crystal(server, player);
            System.out.println("PLAYER_VISUAL_MECHANICS_PASS: " + checks);
        } finally {
            ChangedSynergyConfig.COMMON.pureWhiteWolfAdaptation.set(oldConfig);
            level.getGameRules().getRule(ChangedSynergyGameRules.CREATURE_LIFE).set(oldLife, server);
            ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(previousTime);
        }
    }

    private static void packet(ServerPlayer player) {
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            SyncTransfurPacket.Builder.of(player).write(buffer);
            var decoded = new SyncTransfurPacket(buffer);
            var field = SyncTransfurPacket.class.getDeclaredField("changedForms");
            field.setAccessible(true);
            var listings = (java.util.Map<?, ?>) field.get(decoded);
            var listing = listings.get(player.getId());
            var data = listing.getClass().getDeclaredMethod("data");
            data.setAccessible(true);
            var payload = (net.minecraft.nbt.CompoundTag) data.invoke(listing);
            check(payload.getCompound(PureWhiteWolfAdaptation.PLAYER_DATA_KEY).getBoolean("Adapted"),
                    "Original Changed tracking packet round trip includes adaptation state");
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Changed packet structure changed", failure);
        } finally {
            buffer.release();
        }
    }

    private static void crystal(MinecraftServer server, ServerPlayer player) {
        var level = server.overworld();
        BlockPos position = new BlockPos(16, 80, 16);
        level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
        var state = ChangedBlocks.LATEX_PUP_CRYSTAL.get().defaultBlockState();
        level.setBlock(position, state, 3);
        player.setPos(position.getX() + 0.5, position.getY() + 0.25, position.getZ() + 0.5);
        player.horizontalCollision = player.verticalCollision = false;
        ProcessTransfur.setPlayerTransfurProgress(player, 0);
        var dark = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        FactionReputation.set(dark, player, 0);
        ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(4_000);
        player.invulnerableTime = 0;
        check(state.getCollisionShape(level, position).isEmpty()
                        && !state.getInteractionShape(level, position).isEmpty(),
                "Original non-colliding crystal still has its hazard interaction shape");
        check(LatexPupCrystalContact.touch(player, position, state), "Pup crystal top contact works without collision flags");
        float progress = ProcessTransfur.getPlayerTransfurProgress(player);
        check(progress > 0, "Unprotected crystal contact causes original transfur progress");
        LatexPupCrystalContact.touch(player, position, level.getBlockState(position));
        check(ProcessTransfur.getPlayerTransfurProgress(player) == progress, "Native callback and tick fallback cannot double damage");
        FactionReputation.set(dark, player, FactionReputation.RESPECTED_THRESHOLD);
        ProcessTransfur.setPlayerTransfurProgress(player, 0);
        ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(4_001);
        player.invulnerableTime = 0;
        LatexPupCrystalContact.touch(player, position, level.getBlockState(position));
        check(ProcessTransfur.getPlayerTransfurProgress(player) == 0, "Respected dark reputation still protects against the crystal");
        var decision = ((LatexPupCrystalContact.DecisionAccess) state.getBlock())
                .changedSynergy$crystalDecision(player);
        check(decision.transfurProgress() == 8.3F
                        && decision.transfurVariant() == ChangedTransfurVariants.DARK_LATEX_WOLF_PUP.get(),
                "Original crystal amount, pup form and completion callback reused");
        player.setPos(position.getX() + 2, position.getY() + 0.25, position.getZ() + 0.5);
        ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(4_002);
        check(!LatexPupCrystalContact.touch(player, position, level.getBlockState(position)),
                "No crystal damage at a distance");
    }

    private static void tick(ServerPlayer player) {
        PureWhiteWolfAdaptation.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
        checks++;
        System.out.println("VISUAL_MECHANICS_CHECK: " + message);
    }
}
