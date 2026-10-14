package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.beast.AbstractDarkLatexEntity;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.parkabird.changedsynergy.ai.BondedCreatureLifecycle;
import net.parkabird.changedsynergy.ai.CreatureCacheGuardService;
import net.parkabird.changedsynergy.ai.CreatureCommunityData;
import net.parkabird.changedsynergy.ai.CreatureLifeMemory;
import net.parkabird.changedsynergy.ai.CreatureLifeMemory.GroupRole;
import net.parkabird.changedsynergy.ai.CreatureRoutineGoal;
import net.parkabird.changedsynergy.ai.CreatureSettlementService;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

/** Actual native tame state and social bonds, with saved community UUIDs checked directly. */
public final class PetCommunityIsolationRegression {
    private static int checks;

    public static void run(MinecraftServer server) throws ReflectiveOperationException {
        checks = 0;
        var level = server.overworld();
        var data = CreatureCommunityData.get(server);
        var savedCommunities = data.save(new CompoundTag());
        var migrationField = CreatureCommunityData.class.getDeclaredField("migratedDimensions");
        migrationField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var migrationState = new HashSet<>((Set<String>) migrationField.get(data));
        boolean life = level.getGameRules().getBoolean(ChangedSynergyGameRules.CREATURE_LIFE);
        boolean npc = level.getGameRules().getBoolean(ChangedSynergyGameRules.NPC_AI);
        boolean bonds = level.getGameRules().getBoolean(ChangedSynergyGameRules.BOND_SYSTEM);
        var owner = player(server, "PetIsolation");
        var intruder = player(server, "CacheIntruder");
        var wild = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        var adult = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        var pup = ChangedEntities.DARK_LATEX_WOLF_PUP.get().create(level);
        var white = ChangedEntities.PURE_WHITE_LATEX_WOLF.get().create(level);
        var actors = new ChangedEntity[]{wild, adult, pup, white};
        var spawn = level.getSharedSpawnPos();
        var cache = new BlockPos((spawn.getX() >> 4) * 16 + 6,
                level.getMaxBuildHeight() - 16, (spawn.getZ() >> 4) * 16 + 6);
        level.getChunkAt(cache);
        var oldBlock = level.getBlockState(cache);
        var oldBlockEntity = level.getBlockEntity(cache);
        var oldBlockEntityTag = oldBlockEntity == null ? null : oldBlockEntity.saveWithFullMetadata();
        try {
            level.getGameRules().getRule(ChangedSynergyGameRules.CREATURE_LIFE).set(true, server);
            level.getGameRules().getRule(ChangedSynergyGameRules.NPC_AI).set(true, server);
            level.getGameRules().getRule(ChangedSynergyGameRules.BOND_SYSTEM).set(true, server);
            level.setBlock(cache, Blocks.BARREL.defaultBlockState(), 2);
            var storage = (Container) level.getBlockEntity(cache);
            storage.clearContent();
            owner.setPos(cache.getX() + 1.5, cache.getY(), cache.getZ() + 0.5);
            intruder.setPos(cache.getX() + 2.5, cache.getY(), cache.getZ() + 0.5);
            for (ChangedEntity creature : actors) {
                creature.setPos(cache.getX() + 0.5, cache.getY(), cache.getZ() + 0.5);
                level.addFreshEntity(creature);
            }
            CreatureCommunityData.markCache(wild, cache);
            var home = CreatureCommunityData.bind(wild).orElseThrow().id();
            check(CreatureCommunityData.bind(adult).orElseThrow().id().equals(home)
                    && CreatureCommunityData.bind(pup).orElseThrow().id().equals(home),
                    "Untamed adults and pups may share their original wild home");
            check(membership(data, pup.getUUID(), home), "Wild pup UUID is initially recorded");

            // A pet may return resources collected before taming, without being a wild member.
            var cargo = new CompoundTag();
            cargo.put("Stack", new ItemStack(Items.BREAD, 2).save(new CompoundTag()));
            cargo.putString("Kind", "food");
            pup.getPersistentData().put("ChangedSynergyCarriedResource", cargo);
            var nativePup = (AbstractDarkLatexEntity) pup;
            nativePup.setOwnerUUID(owner.getUUID());
            nativePup.setTame(true);
            check(CreatureCommunityData.isPlayerCompanion(pup), "Real tamed native pup is a player companion");
            check(CreatureCommunityData.bind(pup).orElseThrow().id().equals(home),
                    "Pre-taming cargo retains its original delivery destination");
            check(!membership(data, pup.getUUID(), home), "Delivery does not enroll the owned pup");
            check(CreatureSettlementService.ensureCachePosition(pup).orElseThrow().equals(cache),
                    "Owned pending cargo can use its known cache");
            check(CreatureSettlementService.depositCargo(pup), "Pre-taming cargo can actually be delivered");
            int bread = 0;
            for (int slot = 0; slot < storage.getContainerSize(); slot++) {
                if (storage.getItem(slot).is(Items.BREAD)) bread += storage.getItem(slot).getCount();
            }
            check(bread == 2 && !CreatureSettlementService.hasCargo(pup),
                    "Delivery preserves the two resources without seeding a new outpost");
            check(CreatureCommunityData.bind(pup).isEmpty()
                    && !pup.getPersistentData().hasUUID("ChangedSynergyCommunityId")
                    && memberships(data, pup.getUUID()) == 0,
                    "After delivery the native pet leaves every wild member list");
            check(CreatureSettlementService.ensureCachePosition(pup).isEmpty()
                    && CreatureCommunityData.compatibleCaches(pup).isEmpty(),
                    "An idle pet cannot adopt or construct a nearby wild cache");

            // Old guard jobs must cease when that same entity becomes a bonded companion.
            CreatureLifeMemory.setRole(adult, GroupRole.GUARD);
            CreatureCacheGuardService.begin(adult, intruder, cache);
            check(CreatureCacheGuardService.assignment(adult).isPresent(), "Wild guard receives an assignment");
            LatexSocialMemory.addBond(adult, owner);
            check(LatexSocialMemory.isBonded(adult, owner), "Real adult social bond is established");
            check(CreatureCacheGuardService.assignment(adult).isEmpty() && adult.getTarget() == null,
                    "Taming a guard cancels its stale intruder assignment and combat target");
            CreatureCacheGuardService.begin(adult, intruder, cache);
            check(CreatureCacheGuardService.assignment(adult).isEmpty(),
                    "A bonded companion cannot be recruited for new wild guard work");
            check(CreatureCommunityData.bind(adult).isEmpty()
                    && memberships(data, adult.getUUID()) == 0,
                    "Adult bond also detaches its exact UUID from wild communities");

            var witnesses = CreatureCacheGuardService.class.getDeclaredMethod("membersAtCache",
                    ServerLevel.class, ServerPlayer.class, BlockPos.class);
            witnesses.setAccessible(true);
            @SuppressWarnings("unchecked")
            var members = (List<ChangedEntity>) witnesses.invoke(null, level, owner, cache);
            check(members.contains(wild) && !members.contains(pup) && !members.contains(adult),
                    "Owned pets are not community-theft witnesses against their owner");

            level.getGameRules().getRule(ChangedSynergyGameRules.BOND_SYSTEM).set(false, server);
            ((AbstractDarkLatexEntity) adult).setTame(false);
            ((AbstractDarkLatexEntity) adult).setOwnerUUID(null);
            check(CreatureCommunityData.isPlayerCompanion(pup)
                    && CreatureCommunityData.isPlayerCompanion(adult),
                    "Disabling bonds preserves both native pet ownership and stored adult bonds");
            var movement = CreatureRoutineGoal.class.getDeclaredMethod("movementAvailable");
            movement.setAccessible(true);
            check(!(boolean) movement.invoke(new CreatureRoutineGoal(adult))
                    && !(boolean) movement.invoke(new CreatureRoutineGoal(pup)),
                    "Disabled companion behavior cannot reactivate wild routine movement");
            int records = data.save(new CompoundTag()).getList("Records", Tag.TAG_COMPOUND).size();
            for (int attempt = 0; attempt < 32; attempt++) {
                CreatureCommunityData.bind(pup);
                CreatureCommunityData.bind(adult);
                CreatureCommunityData.tradeStock(pup);
                CreatureCommunityData.tradeCredit(adult, new ItemStack(Items.BREAD));
            }
            check(data.save(new CompoundTag()).getList("Records", Tag.TAG_COMPOUND).size() == records,
                    "Repeated pet information reads do not create phantom communities");

            // Mobile white consensus really rebinds across distance; the former record must lose it.
            white.setPos(cache.getX() + 0.5, cache.getY(), cache.getZ() + 0.5);
            var before = CreatureCommunityData.bind(white).orElseThrow().id();
            white.setPos(cache.getX() + 200.5, cache.getY(), cache.getZ() + 0.5);
            var after = CreatureCommunityData.bind(white).orElseThrow().id();
            check(!before.equals(after) && !membership(data, white.getUUID(), before)
                    && membership(data, white.getUUID(), after),
                    "Actual consensus move removes the UUID from its old community");
            CreatureCommunityData.detach(white);
            check(memberships(data, white.getUUID()) == 0 && membership(data, wild.getUUID(), home),
                    "Detach removes only the requested creature, preserving other wild members");
            System.out.println("PET_COMMUNITY_ISOLATION_REGRESSION_PASS: " + checks + " checks");
        } finally {
            for (ChangedEntity creature : actors) {
                CreatureCacheGuardService.finish(creature, false);
                BondedCreatureLifecycle.untrack(creature);
                CreatureCommunityData.detach(creature);
                creature.getNavigation().stop();
                creature.discard();
            }
            owner.discard(); intruder.discard();
            level.setBlock(cache, oldBlock, 2);
            if (oldBlockEntityTag != null && level.getBlockEntity(cache) != null) {
                level.getBlockEntity(cache).load(oldBlockEntityTag);
            }
            restoreCommunities(data, savedCommunities, migrationState);
            level.getGameRules().getRule(ChangedSynergyGameRules.CREATURE_LIFE).set(life, server);
            level.getGameRules().getRule(ChangedSynergyGameRules.NPC_AI).set(npc, server);
            level.getGameRules().getRule(ChangedSynergyGameRules.BOND_SYSTEM).set(bonds, server);
        }
    }

    private static ServerPlayer player(MinecraftServer server, String name) {
        var player = new ServerPlayer(server, server.overworld(), new GameProfile(UUID.randomUUID(), name));
        player.connection = new ServerGamePacketListenerImpl(server,
                new Connection(PacketFlow.SERVERBOUND), player);
        return player;
    }

    private static boolean membership(CreatureCommunityData data, UUID creature, UUID community) {
        var records = data.save(new CompoundTag()).getList("Records", Tag.TAG_COMPOUND);
        for (int i = 0; i < records.size(); i++) {
            var record = records.getCompound(i);
            if (!record.getUUID("Id").equals(community)) continue;
            var members = record.getList("Members", Tag.TAG_COMPOUND);
            for (int member = 0; member < members.size(); member++) {
                if (members.getCompound(member).getUUID("Id").equals(creature)) return true;
            }
        }
        return false;
    }

    private static int memberships(CreatureCommunityData data, UUID creature) {
        var records = data.save(new CompoundTag()).getList("Records", Tag.TAG_COMPOUND);
        int count = 0;
        for (int i = 0; i < records.size(); i++) {
            var members = records.getCompound(i).getList("Members", Tag.TAG_COMPOUND);
            for (int member = 0; member < members.size(); member++) {
                if (members.getCompound(member).getUUID("Id").equals(creature)) count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private static void restoreCommunities(CreatureCommunityData target, CompoundTag saved,
            Set<String> migrationState) throws ReflectiveOperationException {
        var load = CreatureCommunityData.class.getDeclaredMethod("load", CompoundTag.class);
        load.setAccessible(true);
        var restored = (CreatureCommunityData) load.invoke(null, saved);
        for (String name : List.of("communities", "aliases")) {
            var field = CreatureCommunityData.class.getDeclaredField(name);
            field.setAccessible(true);
            var destination = (Map<Object, Object>) field.get(target);
            destination.clear(); destination.putAll((Map<Object, Object>) field.get(restored));
        }
        var field = CreatureCommunityData.class.getDeclaredField("migratedDimensions");
        field.setAccessible(true);
        var destination = (Set<String>) field.get(target);
        destination.clear(); destination.addAll(migrationState);
        target.setDirty();
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
        checks++;
        System.out.println("PET_COMMUNITY_CHECK_PASS: " + message);
    }
}
