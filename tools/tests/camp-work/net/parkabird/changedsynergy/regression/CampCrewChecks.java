package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.phys.Vec3;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.CrewJob;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Role;
import net.parkabird.changedsynergy.world.inventory.BondedInventoryService;
import net.parkabird.changedsynergy.world.inventory.PlayerOutpostCargoInventory;
import net.parkabird.changedsynergy.world.inventory.PlayerOutpostMenu;

/** Native loot, tool durability, cancelled work, persistent jobs, and actual menu/command decoding. */
final class CampCrewChecks {
    private static int checks;
    @SuppressWarnings("unchecked")
    static void run(ServerPlayer owner, ChangedEntity mob, PlayerOutpostData.Outpost camp, Container storage) throws Exception {
        var level = owner.serverLevel(); var data = PlayerOutpostData.get(owner.server); var member = camp.members.get(mob.getUUID());
        var role = member.role; var job = member.crewJob; var connection = owner.connection;
        Vec3 ownerPos = owner.position(), mobPos = mob.position(); boolean meals = camp.meals;
        var blocked = ChangedSynergyConfig.COMMON.playerOutpostCrewBlockedOres.get();
        var playerMapField = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(PlayerList.class, "f_11197_"); playerMapField.setAccessible(true);
        var players = (Map<UUID,ServerPlayer>)playerMapField.get(owner.server.getPlayerList());
        var previousPlayer = players.put(owner.getUUID(), owner);
        var listField = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(PlayerList.class, "f_11196_"); listField.setAccessible(true);
        var playerList = (List<ServerPlayer>)listField.get(owner.server.getPlayerList());
        var goal = new PlayerOutpostCrewGatherGoal(mob);
        var animals = new ArrayList<Animal>();
        var dark = (net.ltxprogrammer.changed.entity.beast.AbstractDarkLatexEntity)mob;
        var oldCondition = dark.getAttackCondition();
        var oldTargetType = dark.getTargetType();
        try {
            owner.connection = new ServerGamePacketListenerImpl(owner.server, new Connection(PacketFlow.SERVERBOUND), owner);
            playerList.add(owner); level.addNewPlayer(owner);
            camp.meals = false;
            owner.setHealth(owner.getMaxHealth()); mob.setHealth(mob.getMaxHealth()); mob.setTarget(null);
            CreaturePersonality.establishRelationship(mob, owner); CreaturePersonality.setFamiliarity(mob, owner, 60);
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(13,89,13), new BlockPos(28,89,28))) level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(13,90,13), new BlockPos(28,93,28))) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            owner.setPos(20.5,90,22.5); mob.setPos(20.5,90,20.5); mob.setOnGround(true);
            PlayerOutpostService.releaseCargo(mob);
            ChangedSynergyConfig.COMMON.playerOutpostCrewGathering.set(true);
            ChangedSynergyConfig.COMMON.playerOutpostHunting.set(true);
            ChangedSynergyConfig.COMMON.playerOutpostCrewBlockedOres.set(List.of());
            data.crewJob(camp, mob.getUUID(), CrewJob.MINE); PlayerOutpostService.applyRole(mob, owner, Role.CREW);
            BlockPos ore = new BlockPos(21,90,20);
            level.setBlock(ore, Blocks.IRON_ORE.defaultBlockState(), 3);
            var personal = BondedInventoryService.inventory(mob, owner);
            ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
            pickaxe.enchant(net.minecraft.world.item.enchantment.Enchantments.BLOCK_EFFICIENCY, 3);
            personal.setItem(0, pickaxe);
            // Drive the real goal selector and Forge living ticks, including the crew follow goal.
            // Method-only tests cannot catch a conflicting goal that prevents canUse from being called.
            level.setBlock(ore, Blocks.AIR.defaultBlockState(), 3);
            BlockPos travellingOre = new BlockPos(22,90,25);
            level.setBlock(travellingOre, Blocks.IRON_ORE.defaultBlockState(), 3);
            owner.setPos(20.5,90,30.5);
            CampNavigationChecks.tick(level, owner, List.of(mob), 500);
            if (!level.getBlockState(travellingOre).isAir()) {
                var scheduled = mob.goalSelector.getAvailableGoals().stream()
                        .filter(g -> g.getGoal() instanceof PlayerOutpostCrewGatherGoal).findFirst().orElseThrow().getGoal();
                System.out.println("CAMP_CREW_SELECTOR_DIAGNOSTIC pos="+mob.position()+" owner="+owner.position()
                        +" target="+mob.getTarget()+" status="+member.workStatus+" now="+level.getGameTime()
                        +" nextScan="+get(scheduled,"nextScan")+" cursor="+get(scheduled,"cursor")
                        +" action="+get(scheduled,"action")+" flags="+mob.goalSelector.getAvailableGoals().stream()
                                .map(g->g.getPriority()+":"+g.getGoal().getClass().getSimpleName()+":"+g.isRunning()+":"+g.getFlags()).toList());
            }
            check(level.getBlockState(travellingOre).isAir() && cargo(mob, Items.RAW_IRON) == 1,
                    "Real scheduled mining interrupts crew following and harvests exposed ore");
            PlayerOutpostService.releaseCargo(mob); personal.getItem(0).setDamageValue(0);
            CampCrewSchedulingChecks.run(owner, camp);
            CampCrewMiningChecks.run(owner, camp);
            CampCrewFieldChecks.run(owner, camp);
            CampCrewCombatChecks.run(owner, camp);
            owner.setPos(20.5,90,22.5); mob.setPos(20.5,90,20.5); mob.setDeltaMovement(Vec3.ZERO); mob.setOnGround(true);
            level.setBlock(ore, Blocks.IRON_ORE.defaultBlockState(), 3);
            search(goal);
            check(goal.canUse(), "Travelling miner discovers real exposed ore near its owner");
            goal.start();
            owner.setPos(40.5,90,22.5);
            check(goal.canContinueToUse(), "Miner finishes current ore after the owner moves beyond normal follow distance");
            int ticks = 0; while (ticks++ < 250 && goal.canContinueToUse()) goal.tick();
            goal.stop();
            check(level.getBlockState(ore).isAir() && cargo(mob, Items.RAW_IRON) == 1, "Mining puts the real drop in accessible work inventory");
            check(personal.getItem(0).getDamageValue() == 1, "Stored player pickaxe pays native durability");
            check(ticks < 35, "Efficiency improves the real mining duration");
            owner.setPos(20.5,90,22.5);
            level.setBlock(ore, Blocks.IRON_ORE.defaultBlockState(), 3);
            search(goal); check(goal.canUse(), "A second mining task can be selected"); goal.start();
            ChangedSynergyConfig.COMMON.playerOutpostCrewBlockedOres.set(List.of("minecraft:iron_ore"));
            check(!goal.canContinueToUse(), "A runtime blocklist change cancels mining already in progress");
            goal.tick(); goal.stop();
            check(level.getBlockState(ore).is(Blocks.IRON_ORE) && personal.getItem(0).getDamageValue() == 1, "Blocked mining consumes neither ore nor tool durability");
            search(goal); check(!goal.canUse(), "Blocked ore is not selected on the next search");
            ChangedSynergyConfig.COMMON.playerOutpostCrewBlockedOres.set(List.of());
            search(goal); check(goal.canUse(), "Clearing the list restores mining"); goal.start();
            ChangedSynergyConfig.COMMON.playerOutpostCrewGathering.set(false);
            check(!goal.canContinueToUse(), "Runtime off cancels gathering"); goal.tick(); goal.stop();
            check(cargo(mob, Items.RAW_IRON) == 1, "Runtime off retains previous real cargo");
            ChangedSynergyConfig.COMMON.playerOutpostCrewGathering.set(true);
            dark.setAttackCondition(net.ltxprogrammer.changed.entity.ai.DarkLatexAttackCondition.ALWAYS);
            dark.setTargetType(net.ltxprogrammer.changed.entity.ai.DarkLatexTargetType.MONSTERS);
            for (String interrupt : List.of("combat", "low_health", "owner_health", "offline", "new_job", "mob_griefing")) {
                data.crewJob(camp,mob.getUUID(),CrewJob.MINE);
                search(goal); check(goal.canUse(), "Select mining before interruption: "+interrupt); goal.start();
                switch (interrupt) {
                    case "combat" -> {
                        var attacker = new Zombie(level);
                        attacker.setPos(mob.position().add(2, 0, 0));
                        mob.setLastHurtByMob(attacker); mob.setTarget(attacker);
                        check(mob.getTarget() == attacker, "Native pet settings permit retaliation before testing work interruption");
                    }
                    case "low_health" -> mob.setHealth(1);
                    case "owner_health" -> owner.setHealth(1);
                    case "offline" -> players.remove(owner.getUUID());
                    case "new_job" -> data.crewJob(camp,mob.getUUID(),CrewJob.COMBAT);
                    case "mob_griefing" -> level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false,owner.server);
                }
                check(!goal.canContinueToUse(), "Current work yields safely to: "+interrupt);
                goal.tick(); goal.stop();
                check(level.getBlockState(ore).is(Blocks.IRON_ORE) && cargo(mob,Items.RAW_IRON)==1
                                && personal.getItem(0).getDamageValue()==1,
                        "An interruption neither mines a stale target nor loses saved cargo: "+interrupt);
                players.put(owner.getUUID(),owner); owner.setHealth(owner.getMaxHealth()); mob.setHealth(mob.getMaxHealth());
                mob.setTarget(null); mob.setLastHurtByMob(null);
                level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true,owner.server);
            }
            data.crewJob(camp,mob.getUUID(),CrewJob.MINE);
            search(goal); check(goal.canUse(), "Re-enabled crew can gather"); goal.start();
            data.role(camp, mob.getUUID(), Role.RETURN, mob.blockPosition());
            check(!goal.canContinueToUse(), "Recall immediately interrupts the mining goal"); goal.stop();
            check(cargo(mob, Items.RAW_IRON) == 1, "Recall retains travelling cargo");
            data.crewJob(camp, mob.getUUID(), CrewJob.FORAGE);
            level.setBlock(ore, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3), 3);
            search(goal); check(goal.canUse(), "Forager selects a ripe plant regardless of species' stationary job"); goal.start();
            for (int i=0;i<30 && goal.canContinueToUse();i++) goal.tick(); goal.stop();
            check(cargo(mob, Items.SWEET_BERRIES) >= 2 && level.getBlockState(ore).getValue(SweetBerryBushBlock.AGE) == 1,
                    "Foraging carries actual fruit and retains its regrowth plant");
            var saved = data.save(new CompoundTag());
            var load = PlayerOutpostData.class.getDeclaredMethod("load", CompoundTag.class); load.setAccessible(true);
            var restored = ((PlayerOutpostData)load.invoke(null, saved)).byOwner(owner.getUUID()).orElseThrow();
            check(restored.members.get(mob.getUUID()).crewJob == CrewJob.FORAGE, "Crew jobs persist across save/load");
            var old = saved.copy();
            for (var tag : old.getList("Outposts", 10)) {
                var outpostTag = (CompoundTag)tag;
                if (!outpostTag.getUUID("Owner").equals(owner.getUUID())) continue;
                for (var entry : outpostTag.getList("Members", 10)) {
                    var memberTag = (CompoundTag)entry;
                    if (memberTag.getUUID("Id").equals(mob.getUUID())) memberTag.remove("CrewJob");
                }
            }
            var migrated = ((PlayerOutpostData)load.invoke(null, old)).byOwner(owner.getUUID()).orElseThrow();
            check(migrated.members.get(mob.getUUID()).crewJob == CrewJob.COMBAT, "Old camp members migrate to combat without losing their supply job");
            var constructor = PlayerOutpostMenu.class.getDeclaredConstructor(int.class, net.minecraft.world.entity.player.Inventory.class, PlayerOutpostData.Outpost.class, int.class); constructor.setAccessible(true);
            UUID distant = UUID.randomUUID();
            BlockPos distantPosition = new BlockPos(29_990_001, -64, -29_990_003);
            check(data.add(camp, distant, "Unloaded", "minecraft:the_nether", distantPosition, PlayerOutpostSupplyType.ORANGE),
                    "Unloaded member can retain a distant position in another dimension");
            var menu = constructor.newInstance(1, owner.getInventory(), camp, new ArrayList<>(camp.members.keySet()).indexOf(mob.getUUID()));
            check(menu.clickMenuButton(owner, PlayerOutpostMenu.CREW_JOB_BASE + CrewJob.HUNT.ordinal())
                    && member.crewJob == CrewJob.HUNT && menu.members().stream().anyMatch(m -> m.id().equals(mob.getUUID()) && m.crewJob() == CrewJob.HUNT), "Crew menu validates and updates the server job");
            var write = PlayerOutpostMenu.class.getDeclaredMethod("writeOpeningData", FriendlyByteBuf.class, ServerPlayer.class, PlayerOutpostData.Outpost.class, int.class); write.setAccessible(true);
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                write.invoke(null, buffer, owner, camp, 0);
                var client = new PlayerOutpostMenu(1, owner.getInventory(), buffer);
                check(!buffer.isReadable() && client.members().stream().anyMatch(m -> m.id().equals(mob.getUUID()) && m.crewJob() == CrewJob.HUNT), "Opening packet consumes the entire crew snapshot and preserves job");
                BlockPos position = mob.blockPosition().offset(-2, 0, 1);
                mob.setPos(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
                var update = PlayerOutpostMenu.class.getDeclaredMethod("updateLive", ServerPlayer.class, PlayerOutpostData.Outpost.class);
                member.workStatus = "following";
                update.setAccessible(true); update.invoke(menu, owner, camp);
                var live = (net.minecraft.world.inventory.SimpleContainerData)get(menu, "live");
                for (int slot = 0; slot < live.getCount(); slot++) {
                    var wire = new FriendlyByteBuf(Unpooled.buffer());
                    try {
                        new net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket(1, slot, live.get(slot)).write(wire);
                        var packet = new net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket(wire);
                        client.setData(packet.getId(), packet.getValue());
                    } finally { wire.release(); }
                }
                var current = client.members().stream().filter(m -> m.id().equals(mob.getUUID())).findFirst().orElseThrow();
                check(current.loaded() && current.position().equals(position) && !current.position().equals(member.lastPosition),
                        "Live coordinates follow the entity without waiting for its saved position");
                check(current.workStatus().equals("following"), "Native menu packets preserve the new owner-following status");
                var lastKnown = client.members().stream().filter(m -> m.id().equals(distant)).findFirst().orElseThrow();
                check(!lastKnown.loaded() && lastKnown.position().equals(distantPosition) && lastKnown.dimension().equals("minecraft:the_nether"),
                        "Vanilla short packets preserve distant negative coordinates and unloaded dimension");
            } finally { buffer.release(); }
            data.remove(camp, distant);
            var intruder = new ServerPlayer(owner.server, level, new GameProfile(UUID.randomUUID(), "Intruder"));
            check(!menu.clickMenuButton(intruder, PlayerOutpostMenu.CREW_JOB_BASE), "Another player cannot alter crew through this menu");
            for (String name : List.of("combat", "mine", "forage", "hunt")) for (String target : List.of("", mob.getUUID()+" ")) {
                var parsed = owner.server.getCommands().getDispatcher().parse("changedsynergy outpost crew "+target+name, owner.createCommandSourceStack());
                check(!parsed.getReader().canRead() && parsed.getExceptions().isEmpty(), "Registered travelling command: "+target+name);
            }
            var threat = new Zombie(level);
            check(!PlayerOutpostService.mayAttackTarget(mob, threat), "Gathering crew do not join unrelated monster fights");
            data.crewJob(camp, mob.getUUID(), CrewJob.COMBAT);
            check(PlayerOutpostService.mayAttackTarget(mob, threat), "Combat crew retain configured assistance");
            data.crewJob(camp, mob.getUUID(), CrewJob.HUNT);
            for(int i=0;i<3;i++) { var cow = EntityType.COW.create(level); cow.setPos(22.5+i,90,20.5); level.addFreshEntity(cow); animals.add(cow); }
            search(goal); check(goal.canUse(), "Travelling hunter selects an eligible adult from a sustainable population"); goal.start();
            Animal hunted = (Animal)get(goal, "prey");
            mob.setPos(hunted.getX()-1,90,hunted.getZ());
            hunted.setHealth(1);
            for(int i=0;i<60 && goal.canContinueToUse();i++) goal.tick(); goal.stop();
            check(!hunted.isAlive() && cargo(mob, Items.BEEF) > 0 && cargo(mob, Items.RAW_IRON) == 1, "Hunter collects actual kill loot without losing earlier mining cargo");
            check(mob.getPersistentData().getLong("ChangedSynergyNextHunt") - level.getGameTime() == 1800L,
                    "Travelling hunter retains a persistent ninety-second cooldown");
            // Back at camp, including after a recall, deposit instead of spawning/destroying cargo.
            data.role(camp, mob.getUUID(), Role.RETURN, mob.blockPosition());
            mob.setPos(camp.storage.getX()+1.5, camp.storage.getY(), camp.storage.getZ()+0.5); mob.setOnGround(true);
            storage.clearContent(); search(goal); check(goal.canUse(), "Recalled crew deposits retained cargo near storage"); goal.start();
            for(int i=0;i<30 && goal.canContinueToUse();i++) goal.tick(); goal.stop();
            check(!PlayerOutpostWorkGoal.hasCargo(mob) && storage.getItem(0).is(Items.RAW_IRON), "Crew unloads the actual saved inventory into camp storage");
            for(int slot=0;slot<storage.getContainerSize();slot++)storage.setItem(slot,new ItemStack(Items.COBBLESTONE,64));
            PlayerOutpostWorkGoal.acceptCargo(mob,new ItemStack(Items.DIAMOND,3));
            search(goal);check(goal.canUse(),"Returned crew attempts delivery to a full container");goal.start();
            for(int i=0;i<30&&goal.canContinueToUse();i++)goal.tick();goal.stop();
            check(cargo(mob,Items.DIAMOND)==3&&member.workStatus.equals("full_storage"),
                    "A full container preserves all carried resources and explains the delivery failure");
            storage.clearContent();search(goal);check(goal.canUse(),"Freeing storage makes retained cargo deliverable");goal.start();
            for(int i=0;i<30&&goal.canContinueToUse();i++)goal.tick();goal.stop();
            check(cargo(mob,Items.DIAMOND)==0&&storage.getItem(0).is(Items.DIAMOND)&&storage.getItem(0).getCount()==3,
                    "Retry delivers retained cargo exactly once");
            int savedLimit = ChangedSynergyConfig.COMMON.playerOutpostResidents.get();
            ChangedSynergyConfig.COMMON.playerOutpostResidents.set(64);
            var extra = new ArrayList<UUID>();
            while(camp.members.size()<64) { UUID id=UUID.randomUUID(); check(data.add(camp,id,"Extra",camp.dimension,camp.marker,PlayerOutpostSupplyType.ORANGE), "Configurable member slot is accepted"); extra.add(id); }
            check(!data.add(camp,UUID.randomUUID(),"TooMany",camp.dimension,camp.marker,PlayerOutpostSupplyType.ORANGE), "64-member cap is enforced");
            ChangedSynergyConfig.COMMON.playerOutpostResidents.set(1);
            check(camp.members.size()==64, "Lowering a cap does not delete existing residents");
            extra.forEach(id->data.remove(camp,id)); ChangedSynergyConfig.COMMON.playerOutpostResidents.set(savedLimit);
            CampCrewTrueTransfurChecks.run(owner, camp, players);
            System.out.println("CAMP_CREW_GATHER_REGRESSION_PASS checks="+checks);
        } finally {
            goal.stop(); animals.forEach(Animal::discard);
            dark.setAttackCondition(oldCondition); dark.setTargetType(oldTargetType);
            member.role=role; member.crewJob=job; camp.meals=meals;
            ChangedSynergyConfig.COMMON.playerOutpostCrewBlockedOres.set(blocked);
            owner.setPos(ownerPos); mob.setPos(mobPos);
            if(previousPlayer==null) players.remove(owner.getUUID()); else players.put(owner.getUUID(),previousPlayer);
            playerList.remove(owner); owner.discard();
            PlayerOutpostService.ensureGoal(mob);
        }
    }
    private static int cargo(ChangedEntity mob, net.minecraft.world.item.Item item) {
        return PlayerOutpostCargoInventory.inventory(mob).workItems().stream().filter(s->s.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    private static void search(Object goal) throws Exception { field(goal,"nextScan",0L); field(goal,"cursor",0); }
    private static Object get(Object object,String name) throws Exception { Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object); }
    private static void field(Object object,String name,Object value) throws Exception { Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value); }
    private static void check(boolean value,String message) { checks++; if(!value)throw new AssertionError(message); }
}
