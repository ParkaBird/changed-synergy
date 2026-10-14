package net.parkabird.changedsynergy.regression;

import java.util.ArrayList;
import java.util.List;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.ai.DarkLatexAttackCondition;
import net.ltxprogrammer.changed.entity.ai.DarkLatexTargetType;
import net.ltxprogrammer.changed.entity.beast.AbstractDarkLatexEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.event.FactionReputationEvents;
import net.parkabird.changedsynergy.event.HuntAIEvents;

/** Real entities and damage events for the TT author's reports, with both config paths. */
final class AuthorFeedbackChecks {
    private static int checks;
    private static final List<ChangedEntity> mobs = new ArrayList<>();

    @SuppressWarnings("unchecked")
    static void run(net.minecraft.server.MinecraftServer server) throws Exception {
        var owner = player(server, "FeedbackOwner");
        var other = player(server, "FeedbackAlly");
        var mapField = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(net.minecraft.server.players.PlayerList.class, "f_11197_");
        mapField.setAccessible(true);
        var players = (java.util.Map<java.util.UUID, ServerPlayer>)mapField.get(server.getPlayerList());
        var listField = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(net.minecraft.server.players.PlayerList.class, "f_11196_");
        listField.setAccessible(true);
        var list = (List<ServerPlayer>)listField.get(server.getPlayerList());
        for (var player : List.of(owner, other)) {
            players.put(player.getUUID(), player); list.add(player); server.overworld().addNewPlayer(player);
        }
        try { run(owner, other); }
        finally {
            for (var player : List.of(owner, other)) {
                players.remove(player.getUUID()); list.remove(player); player.discard();
            }
        }
    }

    private static ServerPlayer player(net.minecraft.server.MinecraftServer server, String name) {
        var player = new ServerPlayer(server, server.overworld(), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), name));
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(server,
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player);
        return player;
    }

    private static void run(ServerPlayer owner, ServerPlayer other) throws Exception {
        var config = ChangedSynergyConfig.COMMON;
        boolean previousProtection = config.selfDefensePenaltyProtection.get();
        boolean previousAssist = config.companionMonsterAssistOnly.get();
        boolean previousPvp = owner.server.isPvpAllowed();
        BlockPos origin = owner.serverLevel().getSharedSpawnPos().above(32);
        try {
            config.selfDefensePenaltyProtection.set(true);
            config.companionMonsterAssistOnly.set(false);
            owner.server.setPvpAllowed(true);
            owner.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
            other.setPos(owner.getX() + 2, owner.getY(), owner.getZ());
            owner.tickCount = other.tickCount = 2000;
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            owner.setLastHurtByMob(null);

            var cat = npc(owner, "changed:latex_hypno_cat");
            check(RelationshipFavorService.isDedicatedDietFood(cat, new ItemStack(Items.COD)), "Base cats accept raw cod without optional diet tags");
            check(RelationshipFavorService.isDedicatedDietFood(cat, new ItemStack(Items.COOKED_SALMON)), "Base cats recognise cooked salmon");
            CreaturePersonality.establishRelationship(cat, owner);
            CreaturePersonality.setFamiliarity(cat, owner, 32);
            LatexSocialMemory.markRelationshipBetrayal(cat, owner);
            LatexSocialMemory.markProvoked(cat, owner);
            cat.setLastHurtByMob(owner);
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COD, 3));
            check(RelationshipFavorService.offerHeldFood(cat, owner) == RelationshipFavorService.Result.REJECTED
                    && owner.getMainHandItem().getCount() == 3, "Fresh violence rejects peace food without consumption");
            cat.tickCount += 201;
            check(RelationshipFavorService.offerHeldFood(cat, owner) == RelationshipFavorService.Result.DIET_EXISTING
                    && owner.getMainHandItem().getCount() == 2, "After cooling down, valid fish restores contact and consumes one fish");
            check(!LatexSocialMemory.hasRelationshipBetrayal(cat, owner) && !LatexSocialMemory.isProvoked(cat, owner)
                    && cat.getTarget() == null && RelationshipFavorService.canOpenBasicWheel(cat, owner), "Food clears permanent hostility and restores the wheel");
            check(CreaturePersonality.hasEstablishedRelationship(cat, owner), "Reconciliation preserves the existing named friendship");

            var pet = (AbstractDarkLatexEntity)npc(owner, "changed:dark_latex_wolf_male");
            LatexSocialMemory.addBond(pet, owner);
            pet.setAttackCondition(DarkLatexAttackCondition.ALWAYS);
            pet.setTargetType(DarkLatexTargetType.MONSTERS);
            var piglin = EntityType.ZOMBIFIED_PIGLIN.create(owner.level());
            check(!BondedOwnerDefenseGoal.allowsConfiguredDefense(pet, owner, piglin), "Always/Monsters does not attack a peaceful zombified piglin");
            piglin.setPersistentAngerTarget(owner.getUUID());
            check(BondedOwnerDefenseGoal.allowsConfiguredDefense(pet, owner, piglin), "An angry piglin threatening the owner remains a valid threat");
            check(BondedOwnerDefenseGoal.allowsConfiguredDefense(pet, owner, EntityType.ZOMBIE.create(owner.level())), "Ordinary hostile monsters remain valid threats");

            var ally = npc(owner, "changed:white_latex_wolf_male");
            CreaturePersonality.establishRelationship(ally, other);
            CreaturePersonality.setFamiliarity(ally, other, 60);
            CreaturePersonality.setSocialFollowing(ally, other, true);
            var board = owner.server.getScoreboard();
            var team = board.addPlayerTeam("feedback_allies");
            board.addPlayerToTeam(owner.getScoreboardName(), team);
            board.addPlayerToTeam(other.getScoreboardName(), team);
            check(LatexCreatureCombatRules.mustRejectTarget(pet, ally), "Different allied owners' unlike squads do not fight");
            board.removePlayerTeam(team);
            check(!LatexCreatureCombatRules.sharePlayerAlliance(pet, ally), "Unallied owners are not granted blanket alliance");

            cat.setNoAi(false);
            cat.setTarget(null);
            check(SocialAudienceGoal.beginResponse(cat, owner, net.ltxprogrammer.changed.entity.Emote.HEART, 60, false), "A group signal starts an interruptible response");
            check(new SocialAudienceGoal(cat).canUse(), "The response owns a real scheduled AI goal");
            SocialAudienceGoal.end(cat, owner);
            cat.startSleeping(origin);
            check(!SocialAudienceGoal.beginResponse(cat, owner, net.ltxprogrammer.changed.entity.Emote.HEART, 60, false), "Sleeping members are not distracted by group signals");
            cat.stopSleeping();

            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            var attacker = npc(owner, "changed:dark_latex_wolf_male");
            LatexSocialMemory.markProvoked(attacker, owner);
            attacker.setTarget(owner);
            check(attacker.getTarget() == owner, "Self-defence fixture has an actual hostile target");
            int reputation = FactionReputation.score(attacker, owner);
            check(attacker.hurt(owner.damageSources().playerAttack(owner), 3), "Real player damage is accepted against an attacker");
            check(FactionReputation.score(attacker, owner) == reputation, "Self-defence damage does not cost reputation");
            FactionReputationEvents.onCreatureKilled(new LivingDeathEvent(attacker, owner.damageSources().playerAttack(owner)));
            check(FactionReputation.score(attacker, owner) == reputation, "Self-defence kill does not cost reputation");
            int witnessed = pet.getPersistentData().getCompound("ChangedSynergyPersonality").getCompound("PlayerMemories")
                    .getCompound(owner.getStringUUID()).getInt("WitnessedKinKills");
            HuntAIEvents.onHunterDeath(new LivingDeathEvent(attacker, owner.damageSources().playerAttack(owner)));
            check(pet.getPersistentData().getCompound("ChangedSynergyPersonality").getCompound("PlayerMemories")
                    .getCompound(owner.getStringUUID()).getInt("WitnessedKinKills") == witnessed, "Kin witnesses do not punish a defensive kill");
            config.selfDefensePenaltyProtection.set(false);
            FactionReputationEvents.onCreatureKilled(new LivingDeathEvent(attacker, owner.damageSources().playerAttack(owner)));
            check(FactionReputation.score(attacker, owner) == reputation - 15, "Disabling the setting restores kill penalties");
            config.selfDefensePenaltyProtection.set(true);
            var peaceful = npc(owner, "changed:dark_latex_wolf_male");
            reputation = FactionReputation.score(peaceful, owner);
            peaceful.hurt(owner.damageSources().playerAttack(owner), 3);
            check(FactionReputation.score(peaceful, owner) < reputation && TakeoverConflictMemory.initiated(peaceful, owner), "Attacking first records responsibility and still costs reputation");
            peaceful.setTarget(owner); peaceful.invulnerableTime = 0;
            check(!TakeoverConflictMemory.protectsSelfDefense(peaceful, owner), "The victim's retaliation does not turn an initiating player into a defender");

            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-4, -1, -4), origin.offset(5, -1, 4)))
                owner.level().setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
            owner.setLastHurtByMob(null);
            owner.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
            pet.setPos(owner.getX() + 1.5, owner.getY(), owner.getZ());
            pet.setTarget(null);
            owner.setRespawnPosition(owner.level().dimension(), origin.offset(100, 0, 100), 0, true, false);
            check(TakeoverService.beginBondedConfinement(pet, owner), "Awake confinement starts with valid physical reach");
            var entries = TakeoverService.class.getDeclaredField("ACTIVE");
            entries.setAccessible(true);
            Object entry = ((java.util.Map<?, ?>)entries.get(null)).get(owner.getUUID());
            var sessionField = entry.getClass().getDeclaredField("session"); sessionField.setAccessible(true);
            TakeoverSession session = (TakeoverSession)sessionField.get(entry);
            long deadline = session.snapshot().deadline();
            ((ServerLevelData)owner.server.overworld().getLevelData()).setGameTime(deadline + 1);
            for (int i = 0; i < 160 && TakeoverService.active(owner); i++) {
                ((ServerLevelData)owner.server.overworld().getLevelData()).setGameTime(deadline + i + 1);
                TakeoverService.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, owner.server));
            }
            check(!TakeoverService.active(owner) && !TakeoverService.blocksPlayerInput(owner)
                    && owner.distanceToSqr(pet) < 81, "Confinement timeout releases locally with all input restored");
            CompanionToolChecks.run(owner);
            System.out.println("AUTHOR_FEEDBACK_REGRESSION_PASS: " + checks + " checks");
        } finally {
            TakeoverService.forceReleaseByCommand(owner);
            config.selfDefensePenaltyProtection.set(previousProtection);
            config.companionMonsterAssistOnly.set(previousAssist);
            owner.server.setPvpAllowed(previousPvp);
            owner.getInventory().clearContent();
            for (var mob : mobs) { LatexSocialMemory.unregisterBond(mob, owner); mob.discard(); }
            mobs.clear();
        }
    }

    private static ChangedEntity npc(ServerPlayer owner, String id) {
        var npc = (ChangedEntity)ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.parse(id)).create(owner.level());
        npc.setPos(owner.getX() + 1.5, owner.getY(), owner.getZ());
        npc.setNoAi(true); npc.tickCount = 2000;
        owner.serverLevel().addFreshEntity(npc);
        mobs.add(npc); FactionReputation.set(npc, owner, 0);
        return npc;
    }
    private static void check(boolean success, String message) {
        if (!success) throw new AssertionError(message);
        checks++; System.out.println("AUTHOR_FEEDBACK_PASS: " + message);
    }
}
