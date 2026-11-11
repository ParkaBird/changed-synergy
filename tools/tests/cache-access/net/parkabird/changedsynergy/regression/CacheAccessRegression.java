package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.advancement.SynergyAdvancements;
import net.parkabird.changedsynergy.ai.CreatureCacheGuardService;
import net.parkabird.changedsynergy.ai.CreatureCommunityData;
import net.parkabird.changedsynergy.ai.CreatureLifeMemory;
import net.parkabird.changedsynergy.ai.FactionReputation;
import net.parkabird.changedsynergy.event.CreatureCacheGuardEvents;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

@Mod.EventBusSubscriber(modid = "changed_synergy")
public final class CacheAccessRegression {
    private static final BlockPos CACHE = new BlockPos(8, 90, 8);
    private static int checks;

    @SubscribeEvent public static void run(ServerStartedEvent event) {
        var server = event.getServer();
        var level = server.overworld();
        try {
            ChangedSynergyConfig.SPEC.setConfig(com.electronwill.nightconfig.core.CommentedConfig.inMemory());
            level.getGameRules().getRule(ChangedSynergyGameRules.CREATURE_LIFE).set(true, server);
            level.getGameRules().getRule(ChangedSynergyGameRules.FACTION_REPUTATION).set(true, server);
            server.getWorldData().overworldData().setGameTime(100);
            for (int score : new int[]{-60, -20, 0, 25, 50, 80}) {
                access(level, score, false);
                access(level, score, true);
            }
            exceptions(level);
            CacheWitnessChecks.run(server);
            System.out.println("COMMUNITY_REGRESSION_PASS: " + checks
                    + " wild-cache access, real inventory transfer, warnings, guards and advancement checks");
        } catch (Throwable failure) {
            System.err.println("CACHE_ACCESS_REGRESSION_FAIL");
            failure.printStackTrace();
        } finally {
            server.halt(false);
        }
    }

    private static ServerPlayer player(ServerLevel level) {
        var player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "CacheVisitor"));
        player.connection = new ServerGamePacketListenerImpl(level.getServer(),
                new Connection(PacketFlow.SERVERBOUND), player);
        player.setPos(Vec3.atBottomCenterOf(CACHE.east()));
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static ChangedEntity guard(ServerLevel level) {
        var guard = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        guard.moveTo(Vec3.atBottomCenterOf(CACHE.north()));
        level.addFreshEntity(guard);
        CreatureLifeMemory.setRole(guard, CreatureLifeMemory.GroupRole.GUARD);
        level.setBlock(CACHE, Blocks.BARREL.defaultBlockState(), 3);
        CreatureCommunityData.markCache(guard, CACHE);
        check(CreatureCommunityData.snapshotAtCache(level, CACHE).isPresent(), "real wild cache registered");
        return guard;
    }

    private static void attempt(ServerPlayer player, BlockPos pos, InteractionHand hand) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        var event = new PlayerInteractEvent.RightClickBlock(player, hand, pos, hit);
        CreatureCacheGuardEvents.onCacheAccess(event);
        check(!event.isCanceled(), "cache click is never blocked by faction standing");
    }

    private static void use(ServerPlayer player, BlockPos pos, InteractionHand hand) {
        player.closeContainer();
        attempt(player,pos,hand);
        if(hand==InteractionHand.MAIN_HAND) {
            var state=player.serverLevel().getBlockState(pos);
            state.getBlock().use(state,player.serverLevel(),pos,player,hand,
                    new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
        }
    }

    private static void access(ServerLevel level, int score, boolean witnessed) {
        var guard = guard(level);
        guard.setNoAi(!witnessed);
        var player = player(level);
        try {
            FactionReputation.set(guard, player, score);
            check(FactionReputation.score(guard, player) == score, "exact test standing installed");
            var standing = FactionReputation.standing(guard, player);
            var container = (Container) level.getBlockEntity(CACHE);
            container.setItem(0, new ItemStack(Items.BREAD, 3));
            var advancement = level.getServer().getAdvancements().getAdvancement(
                    ResourceLocation.fromNamespaceAndPath("changed_synergy", SynergyAdvancements.MYSTERIOUS_FORAGING_SPOT));
            player.getAdvancements().revoke(advancement, "complete");
            attempt(player, CACHE, InteractionHand.MAIN_HAND);
            int expected = score - (witnessed && standing.ordinal() < FactionReputation.Standing.RESPECTED.ordinal() ? 2 : 0);
            check(FactionReputation.score(guard, player) == score, "attempt alone has no offence");
            check(!player.getAdvancements().getOrStartProgress(advancement).isDone(), "attempt alone earns no advancement");
            var state = level.getBlockState(CACHE);
            state.getBlock().use(state, level, CACHE, player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(CACHE), Direction.UP, CACHE, false));
            check(FactionReputation.score(guard, player) == expected, "witnessed reputation consequences retained");
            check(CreatureCacheGuardService.isDefendingAgainst(guard, player)
                            == (witnessed && standing.ordinal() < FactionReputation.Standing.RESPECTED.ordinal()),
                    "guards retaliate at the original standings, without closing storage");
            check(player.containerMenu instanceof ChestMenu, "real barrel menu opens at score " + score + ", witness=" + witnessed);
            player.containerMenu.clicked(0, 0, ClickType.QUICK_MOVE, player);
            check(container.getItem(0).isEmpty() && player.getInventory().countItem(Items.BREAD) == 3,
                    "storage contents can actually be taken without item loss or duplication");
            check(player.getAdvancements().getOrStartProgress(advancement).isDone(), "actual opening earns advancement");
            player.closeContainer();
            use(player, CACHE, InteractionHand.MAIN_HAND);
            check(FactionReputation.score(guard, player) == expected, "repeated use retains reputation cooldown");
            if (witnessed && standing == FactionReputation.Standing.RESPECTED) {
                check(!CreatureCacheGuardService.isDefendingAgainst(guard, player), "second respected visit is a warning");
                use(player, CACHE, InteractionHand.MAIN_HAND);
                check(FactionReputation.score(guard, player) == score - 1
                                && CreatureCacheGuardService.isDefendingAgainst(guard, player),
                        "third respected visit still provokes guards and costs reputation");
            }
            if (witnessed) {
                int before = FactionReputation.score(guard, player);
                CreatureCacheGuardService.handleCacheDestroyed(level, player, CACHE);
                check(FactionReputation.score(guard, player) == before - 10,
                        "breaking storage remains a separate offence, including for allies");
            }
        } finally {
            player.closeContainer();
            CreatureCacheGuardService.forget(guard);
            guard.discard();
            player.discard();
        }
    }

    private static void exceptions(ServerLevel level) {
        var guard = guard(level);
        var player = player(level);
        try {
            FactionReputation.set(guard, player, 0);
            use(player, CACHE, InteractionHand.OFF_HAND);
            check(FactionReputation.score(guard, player) == 0, "offhand does not double-count access");
            for (var mode : new GameType[]{GameType.CREATIVE, GameType.SPECTATOR}) {
                player.setGameMode(mode);
                use(player, CACHE, InteractionHand.MAIN_HAND);
                check(FactionReputation.score(guard, player) == 0
                                && CreatureCacheGuardService.assignment(guard).isEmpty(),
                        "creative/spectator access has no guard offence");
            }
            player.setGameMode(GameType.SURVIVAL);
            level.getGameRules().getRule(ChangedSynergyGameRules.FACTION_REPUTATION).set(false, level.getServer());
            use(player, CACHE, InteractionHand.MAIN_HAND);
            check(CreatureCacheGuardService.assignment(guard).isEmpty(), "disabled reputation causes no guard offence");
            level.getGameRules().getRule(ChangedSynergyGameRules.FACTION_REPUTATION).set(true, level.getServer());
            var unrelated = CACHE.east(5);
            level.setBlock(unrelated, Blocks.BARREL.defaultBlockState(), 3);
            use(player, unrelated, InteractionHand.MAIN_HAND);
            check(FactionReputation.score(guard, player) == 0, "unrelated storage is untouched");
        } finally {
            CreatureCacheGuardService.forget(guard);
            guard.discard();
            player.discard();
        }
    }

    private static void check(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
        checks++;
    }
}
