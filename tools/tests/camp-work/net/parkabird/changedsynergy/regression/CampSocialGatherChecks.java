package net.parkabird.changedsynergy.regression;

import java.lang.reflect.Field;
import java.util.Map;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.ModList;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.command.PlayerOutpostCommand;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.event.LatexSocialEvents;

/** Real beds, marked resources and server-side wheel invitations. */
final class CampSocialGatherChecks {
    private static int checks;
    static void run(ServerPlayer owner, ChangedEntity original, PlayerOutpostData.Outpost camp) throws Exception {
        var level = owner.serverLevel();
        var data = PlayerOutpostData.get(owner.server);
        var npc = (ChangedEntity)original.getType().create(level);
        var before = new java.util.LinkedHashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        var savedPosition = owner.position(); var savedYaw = owner.getYRot(); var savedPitch = owner.getXRot();
        var connection = owner.connection;
        BlockPos oldStart = camp.gatherStart, oldEnd = camp.gatherEnd;
        long time = level.getDayTime();
        boolean offline = ChangedSynergyConfig.COMMON.playerOutpostWorkWhileOwnerOffline.get(), meals = camp.meals;
        try {
            owner.connection = new ServerGamePacketListenerImpl(owner.server, new Connection(PacketFlow.SERVERBOUND), owner);
            camp.meals = false; level.setDayTime(6000); level.updateSkyBrightness();
            ChangedSynergyConfig.COMMON.playerOutpostWorkWhileOwnerOffline.set(true);
            npc.setPos(Vec3.atBottomCenterOf(camp.marker.offset(1, 0, 6))); npc.setOnGround(true); level.addFreshEntity(npc);
            owner.setPos(npc.getX(), npc.getY(), npc.getZ() + 2);
            check(PatAnimationService.canNpcPat(npc), "Awake NPC can pat");
            PatAnimationService.startFixed(npc, owner, 4);
            PatAnimationService.scheduleFixed(npc, owner, 20, 4);
            BlockPos bed = npc.blockPosition();
            for (BlockPos p : java.util.List.of(bed, bed.south())) before.put(p, level.getBlockState(p));
            var state = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
            level.setBlock(bed, state.setValue(BedBlock.PART, BedPart.FOOT), 3);
            level.setBlock(bed.south(), state.setValue(BedBlock.PART, BedPart.HEAD), 3);
            npc.startSleeping(bed.south());
            check(!PatAnimationService.canNpcPat(npc), "Sleeping NPC cannot pat");
            PatAnimationService.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, owner.server));
            for (String name : java.util.List.of("SESSIONS", "PENDING")) {
                Field field = PatAnimationService.class.getDeclaredField(name); field.setAccessible(true);
                check(!((Map<?, ?>)field.get(null)).containsKey(npc.getUUID()), "Sleep clears " + name);
            }
            CompoundTag memory = npc.getPersistentData().copy();
            LatexSocialEvents.onPatted(npc, owner);
            check(memory.equals(npc.getPersistentData()), "Sleeping pat does not change affection or enqueue a response");
            check(!NpcDialogue.trigger(npc, owner, NpcDialogue.Cue.PAT_BONDED), "Sleeping NPC does not speak");
            check(!net.parkabird.changedsynergy.dialogue.NpcEmoteState.show(level, npc, npc,
                    net.ltxprogrammer.changed.entity.Emote.HEART, 2, 40, false), "Sleeping NPC cannot display social emotes");
            NpcDialogue.context(npc, owner, "gift_cooldown");
            check(memory.equals(npc.getPersistentData()), "Sleeping contextual feedback stays silent");
            if (ModList.get().isLoaded("changed_addon")) {
                Class<?> eventType = Class.forName("net.foxyas.changedaddon.process.features.ProcessPatFeature$GlobalPatReactionEvent");
                var constructor = eventType.getConstructor(LevelAccessor.class, LivingEntity.class, InteractionHand.class, LivingEntity.class, Vec3.class);
                for (boolean actorSleeps : new boolean[]{true, false}) {
                    Event event = (Event)constructor.newInstance(level, actorSleeps ? npc : owner,
                            InteractionHand.MAIN_HAND, actorSleeps ? owner : npc, null);
                    check(MinecraftForge.EVENT_BUS.post(event), "Sleeping Addon pat cancels before native reactions");
                }
            }
            npc.stopSleeping(); check(PatAnimationService.canNpcPat(npc), "Waking restores normal pats");

            // A nearer resource outside the zone must not hide an eligible resource inside it.
            BlockPos inside = camp.marker.offset(7, 0, 7), outside = camp.marker.offset(4, 0, 7);
            camp.gatherStart = inside; camp.gatherEnd = inside.offset(2, 0, 2);
            check(camp.gatherContains(inside) && !camp.gatherContains(outside), "Gather bounds are independent from farm/hunt");
            npc.setPos(Vec3.atBottomCenterOf(outside)); npc.setOnGround(true);
            var leaves = net.ltxprogrammer.changed.init.ChangedBlocks.ORANGE_TREE_LEAVES.get();
            for (BlockPos p : java.util.List.of(inside, outside)) {
                before.putIfAbsent(p, level.getBlockState(p)); level.setBlock(p, leaves.defaultBlockState(), 3);
            }
            check(CreatureSettlementService.findOrangeLeaves(npc, 10, 4, camp::gatherContains).orElseThrow().equals(inside),
                    "Orange search filters before selecting nearest");
            var berry = Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3);
            for (BlockPos p : java.util.List.of(inside, outside)) {
                before.putIfAbsent(p.below(), level.getBlockState(p.below()));
                level.setBlock(p.below(), Blocks.DIRT.defaultBlockState(), 3);
                level.setBlock(p, berry, 3);
                check(level.getBlockState(p).equals(berry), "Test berry has valid soil");
            }
            check(CreatureSettlementService.findSweetBerryBush(npc, 10, 4, camp::gatherContains).orElseThrow().equals(inside),
                    "Berry search filters before selecting nearest");
            data.add(camp, npc.getUUID(), "Gather zone check", camp.dimension, npc.blockPosition(), PlayerOutpostSupplyType.ORANGE);
            data.setWork(camp, npc.getUUID(), PlayerOutpostData.Work.GATHER, null);
            check(PlayerOutpostGoal.workArea(camp, camp.members.get(npc.getUUID())).contains(Vec3.atCenterOf(inside)), "Idle gathering uses marked area");
            var load = PlayerOutpostData.class.getDeclaredMethod("load", CompoundTag.class); load.setAccessible(true);
            var restored = ((PlayerOutpostData)load.invoke(null, data.save(new CompoundTag()))).byOwner(camp.owner).orElseThrow();
            check(restored.gatherStart.equals(inside) && restored.gatherEnd.equals(camp.gatherEnd), "Gather bounds persist across reload");
            for (String command : java.util.List.of("gather corner1", "gather corner2", "gather clear")) {
                var parsed = owner.server.getCommands().getDispatcher().parse("changedsynergy outpost " + command, owner.createCommandSourceStack());
                check(!parsed.getReader().canRead() && parsed.getExceptions().isEmpty(), "Gather command: " + command);
            }
            owner.server.getCommands().getDispatcher().execute("changedsynergy outpost gather clear", owner.createCommandSourceStack());
            check(camp.gatherStart == null && camp.gatherEnd == null && camp.gatherContains(outside), "Clearing restores normal gathering");
            data.remove(camp, npc.getUUID());
            suppliedMining(owner, camp);

            npc.setPos(Vec3.atBottomCenterOf(camp.marker.offset(4, 0, -4)));
            owner.setPos(npc.getX(), npc.getY(), npc.getZ() + 3);
            owner.lookAt(EntityAnchorArgument.Anchor.EYES, npc.getBoundingBox().getCenter());
            CreaturePersonality.establishRelationship(npc, owner); CreaturePersonality.setFamiliarity(npc, owner, 27);
            check(!PlayerOutpostCommand.wheelAction(owner).equals("invite_outpost"), "Below Close cannot invite");
            CreaturePersonality.setFamiliarity(npc, owner, 28);
            check(PlayerOutpostCommand.wheelAction(owner).equals("invite_outpost"), "Aimed Close adult offers invitation");
            owner.setYRot(owner.getYRot() + 180);
            owner.setYHeadRot(owner.getYRot());
            check(!PlayerOutpostCommand.wheelInvite(owner, npc.getUUID()) && data.byMember(npc.getUUID()).isEmpty(), "Changed aim does not recruit");
            owner.lookAt(EntityAnchorArgument.Anchor.EYES, npc.getBoundingBox().getCenter());
            check(PlayerOutpostCommand.wheelInvite(owner, npc.getUUID()) && data.byMember(npc.getUUID()).isPresent(), "Wheel invitation uses the real recruitment chain");
            check(PlayerOutpostCommand.wheelAction(owner).equals("outpost"), "Recruited member returns wheel to management");
            System.out.println("CAMP_SOCIAL_GATHER_REGRESSION_PASS: " + checks + " assertions");
        } finally {
            PatAnimationService.stop(npc); npc.stopSleeping(); data.remove(camp, npc.getUUID()); npc.discard();
            for (var entry : before.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 3);
            camp.gatherStart = oldStart; camp.gatherEnd = oldEnd; camp.meals = meals;
            ChangedSynergyConfig.COMMON.playerOutpostWorkWhileOwnerOffline.set(offline);
            level.setDayTime(time); level.updateSkyBrightness(); owner.connection = connection;
            owner.setPos(savedPosition); owner.setYRot(savedYaw); owner.setXRot(savedPitch);
        }
    }

    private static void suppliedMining(ServerPlayer owner, PlayerOutpostData.Outpost camp) throws Exception {
        var level = owner.serverLevel();
        var data = PlayerOutpostData.get(owner.server);
        var miner = (ChangedEntity)net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                .getValue(net.minecraft.resources.ResourceLocation.parse("changed:latex_kobold")).create(level);
        BlockPos ore = camp.marker.offset(-4, 0, 5);
        var oldBlock = level.getBlockState(ore);
        BlockPos oldStart = camp.gatherStart, oldEnd = camp.gatherEnd;
        try {
            miner.setPos(Vec3.atBottomCenterOf(ore.west())); miner.setOnGround(true); level.addFreshEntity(miner);
            CreaturePersonality.establishRelationship(miner, owner); CreaturePersonality.setFamiliarity(miner, owner, 60);
            check(PlayerOutpostSupplyType.of(miner) == PlayerOutpostSupplyType.CAVE, "Real cave gatherer fixture");
            data.add(camp, miner.getUUID(), "Supplied miner", camp.dimension, miner.blockPosition(), PlayerOutpostSupplyType.CAVE);
            data.setWork(camp, miner.getUUID(), PlayerOutpostData.Work.GATHER, null);
            PlayerOutpostService.applyRole(miner, owner, PlayerOutpostData.Role.SUPPLY);
            camp.gatherStart = ore; camp.gatherEnd = ore;
            var inventory = new net.parkabird.changedsynergy.world.inventory.BondedCreatureInventory(miner);
            var tool = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE);
            tool.enchant(net.minecraft.world.item.enchantment.Enchantments.BLOCK_EFFICIENCY, 5);
            tool.enchant(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH, 1);
            inventory.setItem(0, tool);
            level.setBlock(ore, Blocks.DIAMOND_ORE.defaultBlockState(), 3);
            var goal = new PlayerOutpostGoal(miner);
            boolean started = false;
            for (int i = 0; i < 4 && !started; i++) {
                ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime() + 100);
                started = goal.canUse();
            }
            check(started, "Camp can assign an exposed ore beyond its default iron-tool tier when supplied with diamond");
            goal.start();
            int ticks = 0;
            while (level.getBlockState(ore).is(Blocks.DIAMOND_ORE) && ++ticks <= 100) goal.tick();
            check(ticks == 2, "Actual camp harvesting applies supplied Efficiency V with the two-tick goal cadence");
            goal.stop();
            check(new net.parkabird.changedsynergy.world.inventory.BondedCreatureInventory(miner).storageItem(0).getDamageValue() == 1,
                    "Camp gathering wears the real supplied pickaxe");
            var cargo = new net.parkabird.changedsynergy.world.inventory.PlayerOutpostCargoInventory(miner);
            check(java.util.stream.IntStream.range(0, cargo.getContainerSize())
                    .anyMatch(slot -> cargo.getItem(slot).is(net.minecraft.world.item.Items.DIAMOND_ORE)),
                    "Silk Touch camp output enters accessible Work Cargo for normal delivery");
        } finally {
            data.remove(camp, miner.getUUID()); PlayerOutpostService.clearAssignment(miner); miner.discard();
            level.setBlock(ore, oldBlock, 3); camp.gatherStart = oldStart; camp.gatherEnd = oldEnd;
        }
    }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); checks++; }
}
