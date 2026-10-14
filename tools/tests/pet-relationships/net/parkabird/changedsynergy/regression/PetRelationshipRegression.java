package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.beast.AbstractDarkLatexEntity;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.mixin.DarkLatexWolfPupAgeAccessor;
import net.parkabird.changedsynergy.world.inventory.SocialInteractionMenu;

/** Native growth, taming, saved entities and actual TT control changes, not mock owner flags. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class PetRelationshipRegression {
    static final List<ChangedEntity> ACTORS = new ArrayList<>();
    private static int checks;

    @SubscribeEvent public static void run(ServerStartedEvent event) {
        try { run(event.getServer()); System.out.println("PET_RELATIONSHIP_REGRESSION_PASS: " + checks + " checks"); }
        catch (Throwable failure) { System.err.println("PET_RELATIONSHIP_REGRESSION_FAIL"); failure.printStackTrace(); }
        finally { event.getServer().halt(false); }
    }

    private static void run(MinecraftServer server) throws Exception {
        var level = server.overworld();
        boolean friendships = level.getGameRules().getBoolean(ChangedSynergyGameRules.FRIENDSHIP_SYSTEM);
        boolean bonds = level.getGameRules().getBoolean(ChangedSynergyGameRules.BOND_SYSTEM);
        boolean origins = level.getGameRules().getBoolean(ChangedSynergyGameRules.NATIVE_ORIGINS);
        boolean balance = ChangedSynergyConfig.COMMON.balancedRelationships.get();
        boolean tokens = ChangedSynergyConfig.COMMON.bondTokensRequired.get();
        var owner = player(server, "PetOwner"); var other = player(server, "PetVisitor");
        try {
            level.getGameRules().getRule(ChangedSynergyGameRules.FRIENDSHIP_SYSTEM).set(true, server);
            level.getGameRules().getRule(ChangedSynergyGameRules.BOND_SYSTEM).set(true, server);
            level.getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(false, server);
            ChangedSynergyConfig.COMMON.balancedRelationships.set(true);
            ChangedSynergyConfig.COMMON.bondTokensRequired.set(true);
            owner.getInventory().clearContent();
            var pup = ChangedEntities.DARK_LATEX_WOLF_PUP.get().create(level);
            pup.setNoAi(true); pup.setPos(owner.position()); level.addFreshEntity(pup); ACTORS.add(pup);
            pup.tame(owner); pup.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
            pup.setFollowOwner(false);
            tick(pup, 45);
            check(LatexSocialMemory.isPetOwner(pup, owner), "Native pup remains a pet without gifts");
            check(!CreaturePersonality.hasEstablishedRelationship(pup, owner)
                    && !LatexSocialMemory.isBonded(pup, owner), "A juvenile does not acquire an adult friendship or bond");
            ((DarkLatexWolfPupAgeAccessor)pup).changedSynergy$setAge(72000);
            pup.variantTick(level);
            var adult = level.getEntitiesOfClass(AbstractDarkLatexEntity.class, pup.getBoundingBox().inflate(1),
                    mob -> mob != pup && mob.tickCount <= 1).stream().findFirst().orElseThrow();
            ACTORS.add(adult);
            check(pup.isRemoved() && adult.isTame() && owner.getUUID().equals(adult.getOwnerUUID()),
                    "Changed really replaces the pup with an adult and preserves its native owner");
            verifyPet(adult, owner, other);
            check(adult.getInventory().getItem(0).is(Items.DIAMOND)
                    && adult.getInventory().getItem(0).getCount() == 3 && !adult.isFollowingOwner(),
                    "Growth retains native inventory and the owner's wait instruction");
            check(!PlayerRelationshipSettings.hasRememberedContact(owner, pup.getUUID())
                    && PlayerRelationshipSettings.hasRememberedContact(owner, adult.getUUID()),
                    "The roster references the adult instead of the removed pup");
            var saved = new CompoundTag(); adult.save(saved);
            CreaturePersonality.setFamiliarity(adult, owner, 47);
            CreaturePersonality.setCareCooldown(adult, owner, 90);
            CreaturePersonality.setGiftCooldown(adult, owner, 600);
            var before = CreaturePersonality.growthSnapshot(adult, owner);
            tick(adult, 85);
            check(CreaturePersonality.growthSnapshot(adult, owner).equals(before),
                    "Ordinary native ticks do not refill affection, budgets or cooldowns");
            adult.save(saved);
            adult.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            var loaded = (AbstractDarkLatexEntity)adult.getType().create(level); loaded.load(saved);
            level.addFreshEntity(loaded); ACTORS.add(loaded);
            check(CreaturePersonality.growthSnapshot(loaded, owner).equals(before),
                    "Saved native pet recognition survives an entity reload without refilling affection");
            var wild = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
            wild.setNoAi(true); wild.setPos(owner.position()); level.addFreshEntity(wild); ACTORS.add(wild);
            for (int i = 0; i < 10; i++) { CreaturePersonality.setCareCooldown(wild, owner, 0); CreaturePersonality.rewardCare(wild, owner, 2); }
            CreaturePersonality.reconcileNativePetRelationship(wild);
            check(CreaturePersonality.familiarity(wild, owner) == ChangedSynergyConfig.COMMON.strangerAffectionLimit.get()
                    && !CreaturePersonality.hasEstablishedRelationship(wild, owner),
                    "Untamed strangers still stop at the social cap and require a first gift");
            level.getGameRules().getRule(ChangedSynergyGameRules.FRIENDSHIP_SYSTEM).set(false, server);
            wild.tame(owner); CreaturePersonality.reconcileNativePetRelationship(wild);
            check(wild.isTame() && !CreaturePersonality.hasEstablishedRelationship(wild, owner),
                    "Pausing friendship does not cancel native ownership or reactivate growth");
            level.getGameRules().getRule(ChangedSynergyGameRules.FRIENDSHIP_SYSTEM).set(true, server);
            tick(wild, 45);
            verifyPet(wild, owner, other);
            if (ModList.get().isLoaded("changed_addon")) AddonPetRelationshipChecks.run(owner, other);
            if (ModList.get().isLoaded("true_transfur")) instinct(owner, other, loaded);
            System.out.println("PET_BACKENDS: addon=" + ModList.get().isLoaded("changed_addon")
                    + ", TrueTransfur=" + ModList.get().isLoaded("true_transfur"));
        } finally {
            for (var mob : ACTORS) { CreatureCommunityData.detach(mob); mob.discard(); }
            owner.discard(); other.discard();
            level.getGameRules().getRule(ChangedSynergyGameRules.FRIENDSHIP_SYSTEM).set(friendships, server);
            level.getGameRules().getRule(ChangedSynergyGameRules.BOND_SYSTEM).set(bonds, server);
            level.getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(origins, server);
            ChangedSynergyConfig.COMMON.balancedRelationships.set(balance);
            ChangedSynergyConfig.COMMON.bondTokensRequired.set(tokens);
        }
    }

    static void verifyPet(ChangedEntity mob, ServerPlayer owner, ServerPlayer other) {
        check(LatexSocialMemory.isPetOwner(mob, owner) && !LatexSocialMemory.isPetOwner(mob, other),
                "Native ownership is scoped to the real owner");
        check(CreaturePersonality.familiarity(mob, owner) == 60
                && CreaturePersonality.relationshipTier(mob, owner) == CreaturePersonality.RelationshipTier.CLOSE,
                "Native pet owner is recognised at full affection despite the stranger cap");
        check(!CreaturePersonality.hasEstablishedRelationship(mob, other)
                && !BondTokenService.reservedFor(mob, owner) && !LatexSocialMemory.isBonded(mob, owner),
                "Recognition needs no token and does not give strangers a relationship or invent a voluntary bond");
        var menu = new SocialInteractionMenu(0, owner.getInventory(), mob, true);
        check(menu.getTier() == CreaturePersonality.RelationshipTier.CLOSE && menu.getFamiliarity() == 60,
                "The actual owner social menu no longer displays Stranger");
        check(PlayerRelationshipSettings.hasRememberedContact(owner, mob.getUUID()), "The pet is present in its owner's roster");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void instinct(ServerPlayer owner, ServerPlayer other, ChangedEntity pet) throws Exception {
        var level = owner.serverLevel();
        var identity = new net.parkabird.changedsynergy.origin.NativeOriginData.Identity();
        identity.state = "native"; identity.form = identity.current = ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.getId().toString();
        identity.faction = "dark";
        net.parkabird.changedsynergy.origin.NativeOriginData.get(owner.server).players.put(owner.getUUID(), identity);
        ProcessTransfur.setPlayerTransfurVariant(owner, ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get(), null, 1, false);
        Capability cap = (Capability)Class.forName("com.lugert.true_transfur.transfur.TransfurCapabilities").getField("TRANSFUR_DATA").get(null);
        Object state = owner.getCapability(cap).orElse(null);
        if (state == null) throw new IllegalStateException("Missing TrueTransfur capability");
        state.getClass().getMethod("setTransfurred", boolean.class).invoke(state, true);
        var controller = Class.forName("com.lugert.true_transfur.transfur.TransfurController");
        Class mode = Class.forName("com.lugert.true_transfur.transfur.ControlMode");
        Object instinct = Enum.valueOf(mode, "INSTINCT"), player = Enum.valueOf(mode, "PLAYER");
        var snapshot = CreaturePersonality.growthSnapshot(pet, owner);
        check(Boolean.TRUE.equals(controller.getMethod("switchControlMode", ServerPlayer.class, mode).invoke(null, owner, instinct)),
                "Actual TrueTransfur 2.0.0 enters INSTINCT");
        var body = (ChangedEntity)TrueTransfurCompat.interactionBody(owner);
        var nativeBody = (AbstractDarkLatexEntity)body;
        nativeBody.tame(other);
        CreaturePersonality.reconcileNativePetRelationship(body);
        check(!CreaturePersonality.hasEstablishedRelationship(body, other)
                && !body.getPersistentData().contains("ChangedSynergyPersonality"),
                "Even a tamed Instinct proxy cannot acquire NPC relationship records");
        tick(pet, 45);
        check(CreaturePersonality.growthSnapshot(pet, owner).equals(snapshot), "INSTINCT preserves pet affection and cooldowns");
        var fresh = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        fresh.setNoAi(true); fresh.setPos(body.position()); level.addFreshEntity(fresh); ACTORS.add(fresh);
        fresh.tame(owner);
        tick(fresh, 45);
        verifyPet(fresh, owner, other);
        check(Boolean.TRUE.equals(controller.getMethod("switchControlMode", ServerPlayer.class, mode).invoke(null, owner, player)),
                "Native pet recognition does not prevent returning to PLAYER");
        check(CreaturePersonality.growthSnapshot(pet, owner).equals(snapshot), "Returning to PLAYER never refills pet affection");
    }

    @SuppressWarnings("unchecked")
    private static ServerPlayer player(MinecraftServer server, String name) throws Exception {
        var player = new ServerPlayer(server, server.overworld(), new GameProfile(UUID.randomUUID(), name));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player);
        var ids = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(net.minecraft.server.players.PlayerList.class, "f_11197_"); ids.setAccessible(true);
        ((Map<UUID, ServerPlayer>)ids.get(server.getPlayerList())).put(player.getUUID(), player);
        var list = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(net.minecraft.server.players.PlayerList.class, "f_11196_"); list.setAccessible(true);
        ((List<ServerPlayer>)list.get(server.getPlayerList())).add(player);
        player.setPos(0.5, 220, 0.5); server.overworld().addNewPlayer(player);
        return player;
    }

    static void tick(ChangedEntity mob, int count) {
        // Level.tickNonPassenger increments the entity age before calling tick.
        for (int i = 0; i < count; i++) { mob.tickCount++; mob.tick(); }
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++; System.out.println("PET_RELATIONSHIP_CHECK_PASS: " + message);
    }
}
