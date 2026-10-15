package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.block.AbstractLabDoor;
import net.ltxprogrammer.changed.block.AbstractLargeLabDoor;
import net.ltxprogrammer.changed.block.QuarterSection;
import net.ltxprogrammer.changed.block.entity.LabDoorOpenerEntity;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.LivingEntityDataExtension;
import net.ltxprogrammer.changed.init.ChangedBlocks;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.*;

/** Real merchant clicks and real Changed doors on the isolated integrated server. */
final class TradeDoorRegression {
    @SuppressWarnings("unchecked")
    static void run(MinecraftServer server) throws Exception {
        var level = server.overworld();
        ServerPlayer player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "TradeDoorCheck"));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player);
        var field = ObfuscationReflectionHelper.findField(PlayerList.class, "f_11197_");
        var players = (Map<UUID, ServerPlayer>)field.get(server.getPlayerList());
        players.put(player.getUUID(), player);
        var mob = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        var base = server.getPlayerList().getPlayers().get(0).blockPosition().offset(24, 0, 24).atY(90);
        boolean trading = ChangedSynergyConfig.COMMON.provisionerTrading.get();
        try {
            player.setPos(base.getX() + .5, 90, base.getZ() + 4.5);
            mob.setPos(base.getX() + .5, 90, base.getZ() + 3.5);
            mob.setNoAi(true); level.addFreshEntity(mob);
            CreaturePersonality.establishRelationship(mob, player);
            CreaturePersonality.setFamiliarity(mob, player, 60);
            CreatureCommunityData.bind(mob);
            CreatureLifeMemory.setRole(mob, CreatureLifeMemory.GroupRole.PROVISIONER);
            ChangedSynergyConfig.COMMON.provisionerTrading.set(true);
            check(ProvisionerTradeService.canOpen(mob, player), "Provisioner opens normally");
            for (String scenario : java.util.List.of("normal", "combat", "captured", "stock", "removed")) {
                player.getInventory().clearContent();
                player.getInventory().setItem(0, new ItemStack(Items.APPLE, 26));
                ProvisionerTradeService.open(player, mob);
                check(player.containerMenu instanceof MerchantMenu, "Real vanilla trade screen opens: " + scenario);
                var menu = (MerchantMenu)player.containerMenu;
                menu.setOffers(offers(mob, scenario.equals("stock")));
                menu.setSelectionHint(0); menu.tryMoveItems(0);
                check(menu.getSlot(2).hasItem(), "Real result preview exists before invalidation: " + scenario);
                if (scenario.equals("combat")) {
                    mob.setLastHurtByMob(net.minecraft.world.entity.EntityType.ZOMBIE.create(level));
                    check(ProvisionerTradeService.isBusy(mob), "Real combat state is active before the result click");
                }
                if (scenario.equals("captured")) {
                    ((LivingEntityDataExtension)player).setGrabbedBy(mob);
                    BondedSuitService.ability(mob).grabbedEntity = player;
                }
                if (scenario.equals("removed")) ChangedSynergyConfig.COMMON.provisionerTrading.set(false);
                menu.clicked(2, 0, ClickType.QUICK_MOVE, player);
                check(count(player, Items.GOLD_INGOT) == (scenario.equals("normal") ? 4 : 0),
                        "Batch output is paid: " + scenario + "; actual gold=" + count(player, Items.GOLD_INGOT));
                if (scenario.equals("combat") || scenario.equals("captured") || scenario.equals("removed"))
                    check(!menu.stillValid(player), "Interrupted merchant session becomes invalid: " + scenario);
                if (!scenario.equals("normal")) {
                    for (ClickType type : java.util.List.of(ClickType.PICKUP, ClickType.THROW, ClickType.SWAP))
                        menu.clicked(2, 0, type, player);
                    check(menu.getCarried().isEmpty(), "Rejected result cannot be taken onto cursor: " + scenario);
                }
                if (!scenario.equals("normal")) check(menu.quickMoveStack(player, 2).isEmpty(),
                        "Direct repeated quick-move also rejects invalid result: " + scenario);
                player.closeContainer();
                check(count(player, Items.GOLD_INGOT) == (scenario.equals("normal") ? 4 : 0)
                                && count(player, Items.APPLE) == (scenario.equals("normal") ? 2 : 26),
                        "26 payment items at price 6 yield exactly 4 gold, or no sale and full refund: " + scenario);
                check(!ProvisionerTradeService.isTrading(mob), "Merchant lock clears on close: " + scenario);
                ((LivingEntityDataExtension)player).setGrabbedBy(null);
                BondedSuitService.ability(mob).grabbedEntity = null;
                mob.setTarget(null); mob.setLastHurtByMob(null);
                ChangedSynergyConfig.COMMON.provisionerTrading.set(true);
            }
            System.out.println("PROVISIONER_TRADE_EXPLOIT_REGRESSION_PASS");
            LatexSocialMemory.addBond(mob, player);
            doors(server, player, mob, base);
        } finally {
            if (player.containerMenu != player.inventoryMenu) player.closeContainer();
            ((LivingEntityDataExtension)player).setGrabbedBy(null);
            ChangedSynergyConfig.COMMON.provisionerTrading.set(trading);
            LatexSocialMemory.unregisterBond(mob, player); mob.discard();
            players.remove(player.getUUID());
        }
    }

    private static MerchantOffers offers(ChangedEntity mob, boolean stockBound) throws Exception {
        var template = Class.forName(ProvisionerTradeService.class.getName() + "$Template");
        var create = template.getDeclaredConstructors()[0]; create.setAccessible(true);
        Object sample = create.newInstance(new ItemStack(Items.APPLE, 6), new ItemStack(Items.GOLD_INGOT),
                ProvisionerTradeService.OfferKind.MATERIAL, 2000, stockBound);
        var offer = Class.forName(ProvisionerTradeService.class.getName() + "$SettlementMerchantOffer")
                .getDeclaredConstructor(ChangedEntity.class, template); offer.setAccessible(true);
        var result = new MerchantOffers(); result.add((MerchantOffer)offer.newInstance(mob, sample)); return result;
    }

    private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
        return player.getInventory().items.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static void doors(MinecraftServer server, ServerPlayer player, ChangedEntity mob, BlockPos base) throws Exception {
        var level = server.overworld();
        for (var pos : BlockPos.betweenClosed(base.offset(-7, -1, -7), base.offset(7, 3, 7)))
            level.setBlock(pos, pos.getY() == 89 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        for (Block door : java.util.List.of(ChangedBlocks.LAB_DOOR.get(), ChangedBlocks.BLUE_LAB_DOOR.get(),
                ChangedBlocks.MAINTENANCE_DOOR.get(), ChangedBlocks.LIBRARY_DOOR.get(), ChangedBlocks.LARGE_LAB_DOOR.get())) {
            var doorPos = base;
            var state = door.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
            boolean large = door instanceof AbstractLargeLabDoor;
            var powered = large ? AbstractLargeLabDoor.POWERED : AbstractLabDoor.POWERED;
            for (boolean on : new boolean[] {false, true}) {
                state = state.setValue(powered, on);
                level.setBlock(doorPos, state, 2);
                var evaluator = new WalkNodeEvaluator(); evaluator.setCanPassDoors(true); evaluator.setCanOpenDoors(true);
                evaluator.prepare(new PathNavigationRegion(level, base.offset(-8, -2, -8), base.offset(8, 4, 8)), mob);
                try {
                    check(evaluator.getBlockPathType(level, doorPos.getX(), doorPos.getY(), doorPos.getZ())
                                    == (on ? BlockPathTypes.WALKABLE_DOOR : BlockPathTypes.DOOR_IRON_CLOSED),
                            "Facility door respects power and creature permission: " + door + " powered=" + on);
                } finally { evaluator.done(); }
            }
            level.setBlock(doorPos, Blocks.AIR.defaultBlockState(), 2);
        }
        // A wall forces the actual companion route through a normal powered two-wide door.
        for (var pos : BlockPos.betweenClosed(base.offset(-7, 0, 0), base.offset(7, 3, 0)))
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(base.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        for (var section : QuarterSection.values()) {
            BlockPos pos = QuarterSection.BOTTOM_LEFT.getRelative(base, Direction.NORTH, section);
            level.setBlock(pos, ChangedBlocks.LAB_DOOR.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                    .setValue(AbstractLabDoor.SECTION, section).setValue(AbstractLabDoor.POWERED, true), 2);
        }
        player.setPos(base.getX() + .5, 90, base.getZ() + 5.5);
        mob.setPos(base.getX() + .5, 90, base.getZ() - 5.5);
        for (var goal : mob.goalSelector.getAvailableGoals().stream().toList()) mob.goalSelector.removeGoal(goal.getGoal());
        for (var goal : mob.targetSelector.getAvailableGoals().stream().toList()) mob.targetSelector.removeGoal(goal.getGoal());
        LatexSocialMemory.setFollowingOwner(mob, true);
        var follow = new BondedFollowGoal(mob);
        mob.goalSelector.addGoal(BondedFollowGoal.PRIORITY, follow);
        mob.goalSelector.addGoal(4, new CompanionOpenDoorGoal(mob));
        mob.setNoAi(false); mob.setOnGround(true);
        check(follow.canUse(), "Real bonded follow can start before the powered door");
        var tickCounter = ObfuscationReflectionHelper.findField(MinecraftServer.class, "f_129766_");
        double last = mob.getZ(); boolean crossed = false;
        for (int tick = 0; tick < 120; tick++) {
            for (var section : QuarterSection.values()) {
                BlockPos pos = QuarterSection.BOTTOM_LEFT.getRelative(base, Direction.NORTH, section);
                if (level.getBlockEntity(pos) instanceof LabDoorOpenerEntity opener)
                    LabDoorOpenerEntity.serverTick(level, pos, level.getBlockState(pos), opener);
            }
            tickCounter.setInt(server, server.getTickCount() + 1);
            level.tickNonPassenger(mob);
            if (tick % 20 == 0) System.out.println("FACILITY_DOOR_TRACE: tick=" + tick + " pos=" + mob.position()
                    + " door=" + level.getBlockState(base) + " path=" + mob.getNavigation().getPath()
                    + " next=" + (mob.getNavigation().isDone() ? "done" : mob.getNavigation().getPath().getNextNodePos())
                    + " pose=" + mob.getPose() + " dimensions=" + mob.getBbWidth() + "/" + mob.getBbHeight()
                    + " collision=" + mob.horizontalCollision + " canDuck=" + new CompanionOpenDoorGoal(mob).canUse()
                    + " goals=" + mob.goalSelector.getRunningGoals().map(g -> g.getGoal().getClass().getSimpleName()).toList());
            if (Math.abs(mob.getZ() - last) >= .75) throw new AssertionError("Door traversal teleported: tick=" + tick);
            last = mob.getZ();
            if (mob.getZ() > base.getZ() + 1.5) { crossed = true; break; }
        }
        check(crossed, "Real bonded follow walks through the facility door");
        for (int tick = 0; tick < 60; tick++) {
            tickCounter.setInt(server, server.getTickCount() + 1);
            level.tickNonPassenger(mob);
        }
        check(mob.getPose() == net.minecraft.world.entity.Pose.STANDING,
                "Companion stands normally after passing the low door frame");
        System.out.println("FACILITY_DOOR_COMPANION_REGRESSION_PASS");
    }

    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
        System.out.println("TRADE_DOOR_PASS: " + message);
    }
}
