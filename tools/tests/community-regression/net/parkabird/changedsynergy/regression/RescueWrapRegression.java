package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Method;
import java.util.UUID;
import net.ltxprogrammer.changed.ability.GrabEntityAbility;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.LivingEntityDataExtension;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ai.BondedSuitService;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.compat.addon.ChangedAddonSocialEvents;
import net.parkabird.changedsynergy.mixin.ChangedEntityAbilityInvoker;
import net.foxyas.changedaddon.entity.api.IGrabberEntity;

/** Called by the isolated community fixture, never included in ordinary builds. */
public final class RescueWrapRegression {
    private static int checks;

    public static void run(MinecraftServer server) throws ReflectiveOperationException {
        var level = server.overworld();
        ServerPlayer owner = new ServerPlayer(server, level,
                new GameProfile(UUID.randomUUID(), "WrapTest"));
        owner.connection = new ServerGamePacketListenerImpl(server,
                new Connection(PacketFlow.SERVERBOUND), owner);
        var spawn = level.getSharedSpawnPos();
        owner.setPos(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        var playerMapField = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(net.minecraft.server.players.PlayerList.class, "f_11197_");
        playerMapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var playerMap = (java.util.Map<UUID, ServerPlayer>)playerMapField.get(server.getPlayerList());
        playerMap.put(owner.getUUID(), owner);
        level.addNewPlayer(owner);
        owner.setOnGround(true);
        ChangedEntity first = creature(server, "changed:dark_latex_wolf_male");
        ChangedEntity second = creature(server, "changed_addon:experiment_009");
        try {
            LatexSocialMemory.addBond(first, owner);
            LatexSocialMemory.addBond(second, owner);
            owner.setHealth(4.0F);
            ProcessTransfur.setPlayerTransfurProgress(owner,
                    (float)ProcessTransfur.getEntityTransfurTolerance(owner) * 0.9F);
            check(BondedSuitService.suitOwner(first, owner,
                    BondedSuitService.SuitReason.EMERGENCY), "Emergency suit starts normally");
            check(BondedSuitService.isSuitingOwner(first, owner), "Native and social references agree");
            second.getPersistentData().putUUID("ChangedSynergyTransfurRescueOwner", owner.getUUID());
            second.getPersistentData().putLong("ChangedSynergyTransfurRescueUntil", level.getGameTime() + 240);
            BondedSuitService.ability(first).releaseEntity(false);
            check(BondedSuitService.isRescueGraceActive(owner), "Direct native release records recovery suppression");
            check(!LatexSocialMemory.isFriendlySuitActive(first, owner), "Direct release clears friendly session");
            check(GrabEntityAbility.getGrabber(owner) == null
                    && !ProcessTransfur.isPlayerTransfurred(owner), "Direct release clears temporary body and player lock");
            check(!second.getPersistentData().hasUUID("ChangedSynergyTransfurRescueOwner"), "Queued rescue from another bond is cancelled");
            check(!BondedSuitService.suitOwner(second, owner,
                    BondedSuitService.SuitReason.EMERGENCY), "Already-running automatic rescue cannot recommit");
            check(BondedSuitService.suitOwner(second, owner,
                    BondedSuitService.SuitReason.MANUAL), "Player may still request wrapping during rescue grace");
            var secondAbility = BondedSuitService.ability(second);
            secondAbility.suited = secondAbility.grabbedHasControl = false;
            ChangedAddonSocialEvents.onLivingTick(
                    new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(second));
            check(secondAbility.suited && secondAbility.grabbedHasControl,
                    "Addon social safety gate repairs friendly control bits before hostile-release cleanup");
            BondedSuitService.tickFriendlySuit(owner);
            check(secondAbility.suited && secondAbility.grabbedHasControl
                    && BondedSuitService.isSuitingOwner(second, owner),
                    "Temporary control-bit disagreement is repaired without release/re-wrap");

            // A delayed release of an old carrier must not remove this new suit.
            var firstAbility = BondedSuitService.ability(first);
            firstAbility.grabbedEntity = owner;
            firstAbility.suited = firstAbility.grabbedHasControl = true;
            LatexSocialMemory.beginFriendlySuit(first, owner, true, false);
            var currentBody = ProcessTransfur.getPlayerTransfurVariant(owner);
            check(!BondedSuitService.releaseOwner(first, owner,
                    BondedSuitService.ReleaseReason.MANUAL), "Old wrapper cannot accept new wrapper's release request");
            check(GrabEntityAbility.getGrabber(owner).getEntity() == second
                    && ProcessTransfur.getPlayerTransfurVariant(owner) == currentBody
                    && BondedSuitService.isSuitingOwner(second, owner), "Old release preserves current carrier, temporary form and session");
            BondedSuitService.ability(second).releaseEntity(false);

            // Discharged danger must invalidate a queued rescue before it starts.
            second.getPersistentData().putUUID("ChangedSynergyTransfurRescueOwner", owner.getUUID());
            second.getPersistentData().putLong("ChangedSynergyTransfurRescueUntil", level.getGameTime() + 240);
            ProcessTransfur.setPlayerTransfurProgress(owner, 0);
            check(!BondedSuitService.isTransfurRescueRequested(second, owner)
                    && !second.getPersistentData().hasUUID("ChangedSynergyTransfurRescueOwner"), "Recovered transfur progress cancels pending rescue");

            // Test a real mismatch between the native pet field and Addon's field.
            if (second instanceof IGrabberEntity grabber) {
                var canonical = BondedSuitService.ability(second);
                ((ChangedEntityAbilityInvoker)second).changedSynergy$registerAbility(
                        ignored -> true, canonical);
                var split = grabber.createGrabAbility();
                split.grabbedEntity = owner;
                split.suited = split.grabbedHasControl = true;
                split.grabStrength = 0.8F;
                for (Class<?> type = second.getClass(); type != null; type = type.getSuperclass()) {
                    for (var field : type.getDeclaredFields()) {
                        if (type.getName().equals("net.ltxprogrammer.changed.entity.ChangedEntity")
                                && net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance.class.isAssignableFrom(field.getType())) {
                            field.setAccessible(true);
                            field.set(second, split);
                        }
                    }
                }
                Method reconcile = ChangedAddonSocialEvents.class.getDeclaredMethod(
                        "ensureGrabAbility", ChangedEntity.class, IGrabberEntity.class);
                reconcile.setAccessible(true);
                reconcile.invoke(null, second, grabber);
                check(BondedSuitService.ability(second) == canonical
                        && grabber.getGrabAbilityInstance() == canonical, "Changed and Addon native grab aliases converge");
                check(canonical.grabbedEntity == owner && canonical.suited
                        && canonical.grabbedHasControl && canonical.grabStrength == 0.8F,
                        "Reconciliation preserves existing native hold without restarting it");
            }
            System.out.println("RESCUE_WRAP_REGRESSION_PASS: " + checks + " checks");
        } finally {
            if (owner instanceof LivingEntityDataExtension extension) extension.setGrabbedBy(null);
            ProcessTransfur.removePlayerTransfurVariant(owner);
            first.discard(); second.discard();
            owner.discard();
            playerMap.remove(owner.getUUID());
        }
    }

    private static ChangedEntity creature(MinecraftServer server, String id) {
        ChangedEntity creature = (ChangedEntity)ForgeRegistries.ENTITY_TYPES.getValue(
                ResourceLocation.parse(id)).create(server.overworld());
        creature.setNoAi(true);
        var spawn = server.overworld().getSharedSpawnPos();
        creature.setPos(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        server.overworld().addFreshEntity(creature);
        return creature;
    }

    private static void check(boolean passed, String description) {
        if (!passed) throw new AssertionError(description);
        checks++;
        System.out.println("WRAP_PASS: " + description);
    }
}
