package net.parkabird.changedsynergy.ai;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.ltxprogrammer.changed.block.CardboardBoxTall;
import net.ltxprogrammer.changed.block.BarStool;
import net.ltxprogrammer.changed.block.OfficeChair;
import net.ltxprogrammer.changed.block.Pillow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Outpost;
import net.parkabird.changedsynergy.block.PerchBlock;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.PlayerOutpostBuildStatePacket;

/**
 * Guided facility placement for an owned camp: place from inventory or linked chests,
 * then register with the same rules as {@code /outpost link}.
 */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class PlayerOutpostBuildService {
    public enum Palette {
        STORAGE, BED, REST, CAMPFIRE, COMPOSTER;

        public Palette next() {
            Palette[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        public Palette previous() {
            Palette[] values = values();
            return values[(ordinal() + values.length - 1) % values.length];
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private record Session(Palette palette) {
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private PlayerOutpostBuildService() {
    }

    public static boolean active(ServerPlayer player) {
        return player != null && SESSIONS.containsKey(player.getUUID());
    }

    public static Palette palette(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session == null ? Palette.STORAGE : session.palette();
    }

    public static boolean enter(ServerPlayer player) {
        if (!PlayerOutpostService.enabled(player.level())
                || !player.isAlive()
                || player.isSpectator()
                || TakeoverService.active(player)
                || HypnosisQteService.isPlayerControlLocked(player)
                || net.parkabird.changedsynergy.origin.NativeOriginService.pending(player)) {
            return false;
        }
        Outpost outpost = PlayerOutpostData.get(player.server).byOwner(player.getUUID()).orElse(null);
        if (outpost == null || !outpost.active(player.serverLevel())) {
            player.sendSystemMessage(Component.translatable("command.changed_synergy.outpost.error.inactive"));
            return false;
        }
        SESSIONS.put(player.getUUID(), new Session(Palette.STORAGE));
        player.closeContainer();
        sync(player);
        player.sendSystemMessage(Component.translatable("command.changed_synergy.outpost.build.enter"));
        return true;
    }

    public static void exit(ServerPlayer player) {
        if (!clear(player)) return;
        player.sendSystemMessage(Component.translatable("command.changed_synergy.outpost.build.exit"));
    }

    /** Ends build mode without a chat line (opening the management screen). */
    public static boolean clear(ServerPlayer player) {
        if (player == null || SESSIONS.remove(player.getUUID()) == null) return false;
        sync(player);
        return true;
    }

    public static void cycle(ServerPlayer player, int delta) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        Palette next = delta < 0 ? session.palette().previous() : session.palette().next();
        SESSIONS.put(player.getUUID(), new Session(next));
        sync(player);
    }

    private record TakenItem(ItemStack stack, BlockPos chestPos) {
    }

    public static boolean tryPlace(ServerPlayer player, BlockHitResult hit) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || hit == null) return false;
        if (!player.isAlive()
                || player.isSpectator()
                || TakeoverService.active(player)
                || HypnosisQteService.isPlayerControlLocked(player)
                || net.parkabird.changedsynergy.origin.NativeOriginService.pending(player)) {
            return false;
        }
        Outpost outpost = PlayerOutpostData.get(player.server).byOwner(player.getUUID()).orElse(null);
        if (outpost == null || !outpost.active(player.serverLevel())) {
            exit(player);
            return false;
        }
        ServerLevel level = player.serverLevel();
        if (!outpost.dimension.equals(level.dimension().location().toString())) {
            fail(player, "inactive");
            return false;
        }

        BlockPos clicked = hit.getBlockPos();
        BlockPos candidate = clicked.relative(hit.getDirection());
        double maxReach = player.getBlockReach() + 2.0D;
        Vec3 loc = hit.getLocation();
        if (!Double.isFinite(loc.x) || !Double.isFinite(loc.y) || !Double.isFinite(loc.z)
                || player.distanceToSqr(Vec3.atCenterOf(clicked)) > maxReach * maxReach
                || !level.hasChunkAt(clicked)
                || (!outpost.contains(clicked) && !outpost.contains(candidate))) {
            fail(player, "range");
            return false;
        }

        boolean creative = player.getAbilities().instabuild;
        TakenItem taken = creative
                ? new TakenItem(creativeStack(player, outpost, session.palette()), null)
                : takeMatching(player, outpost, session.palette());
        if (taken.stack().isEmpty()) {
            fail(player, "no_item");
            return false;
        }
        if (!(taken.stack().getItem() instanceof BlockItem blockItem)) {
            if (!creative) refund(player, taken);
            fail(player, "facility");
            return false;
        }

        BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, taken.stack(), hit);
        BlockPos placePos = context.getClickedPos();
        if (!level.hasChunkAt(placePos) || !outpost.contains(placePos) || placePos.equals(outpost.marker)
                || !player.mayBuild() || !level.mayInteract(player, placePos)) {
            if (!creative) refund(player, taken);
            fail(player, "range");
            return false;
        }

        InteractionResult result = blockItem.place(context);
        if (!result.consumesAction()) {
            if (!creative) refund(player, taken);
            fail(player, "place");
            return false;
        }

        if (!registerFacility(player, outpost, placePos, null)) {
            // Block remains; player can link manually. Still report success of placement.
            player.sendSystemMessage(Component.translatable(
                    "command.changed_synergy.outpost.build.placed_unlinked",
                    placePos.toShortString()));
            return true;
        }
        String facility = facilityAt(level, placePos);
        player.sendSystemMessage(Component.translatable(
                "command.changed_synergy.outpost.build.placed",
                Component.translatable("command.changed_synergy.outpost.facility."
                        + (facility == null ? session.palette().key() : facility)),
                placePos.toShortString()));
        return true;
    }

    /** Same registration path as {@code /changedsynergy outpost link}. */
    public static boolean registerFacility(
            ServerPlayer player, Outpost outpost, BlockPos pos, String expectedFacility) {
        ServerLevel level = player.serverLevel();
        if (!outpost.contains(pos)) return false;
        String facility = facilityAt(level, pos);
        if (facility == null) return false;
        if (expectedFacility != null && !expectedFacility.equals(facility)) return false;
        PlayerOutpostData data = PlayerOutpostData.get(player.server);
        return switch (facility) {
            case "storage" -> {
                if (PlayerOutpostData.container(level, pos) == null
                        || CreatureCommunityData.snapshotAtCache(level, pos).isPresent()
                        || NaturalCampService.isStorage(level, pos)) yield false;
                data.setStorage(outpost, pos);
                yield true;
            }
            case "bed", "rest" -> {
                BlockPos resting = PlayerOutpostRestGoal.restingPlace(level, pos);
                if (resting == null || !outpost.contains(resting)) yield false;
                data.refreshRestPlaces(level, outpost);
                if (facility.equals("bed")) data.setBed(outpost, resting);
                else data.addRestPoint(outpost, resting);
                data.refreshRestPlaces(level, outpost);
                yield true;
            }
            case "campfire" -> {
                if (!(level.getBlockEntity(pos) instanceof CampfireBlockEntity)) yield false;
                data.setFacility(outpost, facility, pos);
                yield true;
            }
            case "composter" -> {
                if (!(level.getBlockState(pos).getBlock() instanceof ComposterBlock)) yield false;
                data.setFacility(outpost, facility, pos);
                yield true;
            }
            default -> false;
        };
    }

    public static String facilityAt(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (PlayerOutpostRestGoal.isBed(state)) return "bed";
        if (PlayerOutpostRestGoal.isRestPoint(state)) return "rest";
        if (level.getBlockEntity(pos) instanceof CampfireBlockEntity) return "campfire";
        if (state.getBlock() instanceof ComposterBlock) return "composter";
        return PlayerOutpostData.container(level, pos) != null ? "storage" : null;
    }

    public static boolean matchesPalette(ItemStack stack, Palette palette) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        Block block = blockItem.getBlock();
        return switch (palette) {
            case STORAGE -> isStorageBlock(block);
            case BED -> block instanceof BedBlock || block instanceof PerchBlock;
            case REST -> block.defaultBlockState().is(BlockTags.WOOL_CARPETS)
                    || block instanceof Pillow
                    || block instanceof OfficeChair
                    || block instanceof BarStool
                    || block instanceof CardboardBoxTall;
            case CAMPFIRE -> block instanceof CampfireBlock;
            case COMPOSTER -> block instanceof ComposterBlock;
        };
    }

    private static boolean isStorageBlock(Block block) {
        if (block instanceof ChestBlock || block instanceof BarrelBlock || block instanceof ShulkerBoxBlock) {
            return true;
        }
        var key = ForgeRegistries.BLOCKS.getKey(block);
        if (key == null) return false;
        String path = key.getPath().toLowerCase(Locale.ROOT);
        if (PlayerOutpostData.isProhibitedContainerPath(path) || path.contains("campfire")) {
            return false;
        }
        return path.contains("chest") || path.contains("barrel") || path.contains("crate")
                || path.contains("drawer") || path.contains("cabinet") || path.contains("locker");
    }

    private static ItemStack creativeStack(ServerPlayer player, Outpost outpost, Palette palette) {
        ItemStack sample = peekMatching(player, outpost, palette);
        if (!sample.isEmpty()) return sample.copyWithCount(1);
        return switch (palette) {
            case STORAGE -> new ItemStack(net.minecraft.world.level.block.Blocks.CHEST);
            case BED -> new ItemStack(net.minecraft.world.level.block.Blocks.RED_BED);
            case REST -> new ItemStack(net.minecraft.world.level.block.Blocks.WHITE_CARPET);
            case CAMPFIRE -> new ItemStack(net.minecraft.world.level.block.Blocks.CAMPFIRE);
            case COMPOSTER -> new ItemStack(net.minecraft.world.level.block.Blocks.COMPOSTER);
        };
    }

    private static ItemStack peekMatching(ServerPlayer player, Outpost outpost, Palette palette) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (matchesPalette(stack, palette)) return stack;
        }
        ServerLevel level = player.serverLevel();
        for (BlockPos pos : outpost.storages) {
            if (!level.hasChunkAt(pos)) continue;
            Container container = PlayerOutpostData.container(level, pos);
            if (container == null) continue;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (matchesPalette(stack, palette)) return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static TakenItem takeMatching(ServerPlayer player, Outpost outpost, Palette palette) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!matchesPalette(stack, palette)) continue;
            ItemStack taken = stack.split(1);
            inventory.setChanged();
            return new TakenItem(taken, null);
        }
        ServerLevel level = player.serverLevel();
        for (BlockPos pos : outpost.storages) {
            if (!level.hasChunkAt(pos)) continue;
            Container container = PlayerOutpostData.container(level, pos);
            if (container == null) continue;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (!matchesPalette(stack, palette)) continue;
                ItemStack taken = container.removeItem(slot, 1);
                container.setChanged();
                if (!taken.isEmpty()) return new TakenItem(taken, pos);
            }
        }
        return new TakenItem(ItemStack.EMPTY, null);
    }

    private static void refund(ServerPlayer player, TakenItem taken) {
        ItemStack stack = taken.stack();
        if (stack.isEmpty()) return;
        if (taken.chestPos() != null) {
            ServerLevel level = player.serverLevel();
            if (level.hasChunkAt(taken.chestPos())) {
                Container container = PlayerOutpostData.container(level, taken.chestPos());
                if (container != null) {
                    stack = PlayerOutpostGoal.insert(container, stack);
                    if (stack.isEmpty()) return;
                }
            }
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void fail(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable("command.changed_synergy.outpost.error." + key));
    }

    private static void sync(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        ChangedSynergyNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new PlayerOutpostBuildStatePacket(
                        session != null,
                        session == null ? 0 : session.palette().ordinal()));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SESSIONS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        SESSIONS.clear();
    }
}
