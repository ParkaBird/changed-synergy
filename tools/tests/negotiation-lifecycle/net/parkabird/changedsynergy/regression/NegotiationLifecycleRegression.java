package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.TransfurContext;
import net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision;
import net.ltxprogrammer.changed.entity.variant.TransfurVariant;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.ltxprogrammer.changed.process.TransfurEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.InvoluntaryTransfurNegotiation;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

/** Exercises delayed native callbacks and persistence on a real Forge server. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class NegotiationLifecycleRegression {
    private static final String ROOT = "ChangedSynergyTransfurNegotiation";
    private static int checks;

    @SubscribeEvent public static void run(ServerStartedEvent event) {
        try {
            ChangedSynergyConfig.SPEC.setConfig(com.electronwill.nightconfig.core.CommentedConfig.inMemory());
            ChangedSynergyConfig.COMMON.takeoverEnabled.set(false);
            event.getServer().overworld().getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(true, event.getServer());
            PlayerPersistenceRegression.run(event.getServer());
            NegotiationReasonRegression.run(event.getServer());
            OrganicSleepReversalRegression.run(event.getServer());
            MountedArmorRegression.run(event.getServer());
            AuthorIdentityRegression.run(event.getServer());
            KeepConsciousRegression.run(event.getServer());
            October18FeedbackRegression.run(event.getServer());
            loginBeforeConnection(event.getServer());
            lifecycle(event.getServer());
            humanoidGrab(event.getServer());
            if (ModList.get().isLoaded("true_transfur")) permanentBody(event.getServer());
            System.out.println("NEGOTIATION_LIFECYCLE_REGRESSION_PASS: " + checks
                    + " checks; addon=" + ModList.get().isLoaded("changed_addon")
                    + "; TT=" + ModList.get().isLoaded("true_transfur"));
        } catch (Throwable failure) {
            System.err.println("NEGOTIATION_LIFECYCLE_REGRESSION_FAIL"); failure.printStackTrace();
        } finally { event.getServer().halt(false); }
    }

    private static void loginBeforeConnection(MinecraftServer server) throws Exception {
        ServerPlayer savedPlayer = player(server, UUID.randomUUID());
        var variant = ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get();
        ProcessTransfur.setPlayerTransfurVariant(savedPlayer, variant, null, 1F, false);
        CompoundTag claim = new CompoundTag();
        claim.putString("Mode", "ABSORPTION");
        claim.putUUID("SourceUuid", UUID.randomUUID());
        claim.putString("ResultPlayerForm", variant.getFormId().toString());
        for (String scenario : List.of("pending", "mismatched", "absorption", "none")) {
            CompoundTag expected = claim.copy();
            expected.putBoolean("CapturePending", scenario.equals("pending"));
            if (scenario.equals("mismatched")) expected.putString("ResultPlayerForm",
                    ChangedTransfurVariants.DARK_LATEX_WOLF_FEMALE.get().getFormId().toString());
            set(savedPlayer, expected);
            if (scenario.equals("none")) savedPlayer.getPersistentData()
                    .getCompound(Player.PERSISTED_NBT_TAG).remove(ROOT);
            CompoundTag saved = savedPlayer.saveWithoutId(new CompoundTag());
            ServerPlayer loading = new ServerPlayer(server, server.overworld(), savedPlayer.getGameProfile());
            check(loading.connection == null, "Login fixture starts before the connection exists: " + scenario);
            loading.load(saved);
            check(ProcessTransfur.getPlayerTransfurVariantSafe(loading).orElseThrow().getParent() == variant,
                    "Saved Changed form restores before login without sending through a missing connection: " + scenario);
            check(scenario.equals("none") ? data(loading) == null : expected.equals(data(loading)),
                    "Saved negotiation is not validated against a partially loaded player: " + scenario);
            if (scenario.equals("none")) {
                InvoluntaryTransfurNegotiation.abandonForSecondaryTransfur(loading);
                check(data(loading) == null, "Shared claim cleanup also tolerates an absent connection");
            }
            loading.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), loading);
            InvoluntaryTransfurNegotiation.onPlayerReady(loading);
            check(scenario.equals("absorption") ? expected.equals(data(loading)) : data(loading) == null,
                    "Post-login validation preserves valid absorption and retires stale claims: " + scenario);
            check(ProcessTransfur.getPlayerTransfurVariantSafe(loading).orElseThrow().getParent() == variant,
                    "Post-login validation does not erase the saved Changed form: " + scenario);
            loading.discard();
        }
        savedPlayer.discard();
    }

    private static void lifecycle(MinecraftServer server) throws Exception {
        ServerPlayer player = player(server, UUID.randomUUID());
        var variant = ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get();
        var other = ChangedTransfurVariants.DARK_LATEX_WOLF_FEMALE.get();
        ChangedEntity source = variant.getEntityType().create(server.overworld());
        ChangedEntity nextSource = other.getEntityType().create(server.overworld());
        source.moveTo(0, 80, 0); nextSource.moveTo(2, 80, 0); player.moveTo(1, 80, 0); player.setOnGround(true);
        server.overworld().addFreshEntity(source); server.overworld().addFreshEntity(nextSource);

        CompoundTag legacy = new CompoundTag();
        legacy.putString("Mode", "ABSORPTION"); legacy.putBoolean("CapturePending", true);
        legacy.putBoolean("FormRestorePending", true); legacy.putString("ResultPlayerForm", variant.getFormId().toString());
        legacy.putUUID("SourceUuid", source.getUUID()); set(player, legacy);
        var probeClone = new ServerPlayer(server, server.overworld(), new GameProfile(player.getUUID(), "CloneProbe"));
        set(probeClone, legacy.copy());
        InvoluntaryTransfurNegotiation.copyPlayerData(player, probeClone);
        check(data(probeClone) == null, "Clone does not retain legacy pending metadata even if Forge already copied it");
        InvoluntaryTransfurNegotiation.tickPlayer(player);
        check(data(player) == null && !ProcessTransfur.isPlayerTransfurred(player),
                "Legacy pending metadata is cleared before it can restore a form");

        var expired = stage(player, source, variant, true);
        data(player).putLong("CapturePendingAt", server.overworld().getGameTime() - 200L);
        set(probeClone, data(player).copy()); InvoluntaryTransfurNegotiation.copyPlayerData(player, probeClone);
        check(data(probeClone) == null, "Expired modern pending state also cannot survive a clone");
        InvoluntaryTransfurNegotiation.tickPlayer(player);
        expired.getDecision().postTransfurListener().accept(IAbstractChangedEntity.forEntity(source));
        check(data(player) == null, "Timed-out capture and its late completion stay retired");

        var old = stage(player, source, variant, true);
        data(player).putBoolean("FormRestorePending", true); data(player).putBoolean("ExternalReleasePending", true);
        var current = stage(player, nextSource, other, true);
        UUID id = data(player).getUUID("CaptureId");
        check(!data(player).getBoolean("FormRestorePending") && !data(player).getBoolean("ExternalReleasePending"),
                "A new capture starts with fresh transient flags");
        old.getDecision().postTransfurListener().accept(IAbstractChangedEntity.forEntity(source));
        check(id.equals(data(player).getUUID("CaptureId")) && data(player).getBoolean("CapturePending"),
                "An older completion cannot confirm or overwrite the next capture");
        ProcessTransfur.setPlayerTransfurVariant(player, other);
        current.getDecision().postTransfurListener().accept(IAbstractChangedEntity.forPlayer(player));
        check(!data(player).getBoolean("CapturePending") && InvoluntaryTransfurNegotiation.hasAbsorptionClaim(player),
                "The matching callback confirms a genuine absorption");
        data(player).putInt("Progress", 21);
        current.getDecision().postTransfurListener().accept(IAbstractChangedEntity.forPlayer(player));
        check(data(player).getInt("Progress") == 21, "Duplicate completion cannot reset negotiation progress");

        var secondary = stage(player, source, variant, false);
        reset(player);
        var later = stage(player, nextSource, other, false);
        UUID laterId = data(player).getUUID("CaptureId");
        secondary.getDecision().postTransfurListener().accept(IAbstractChangedEntity.forEntity(source));
        check(laterId.equals(data(player).getUUID("CaptureId")), "Old secondary cleanup cannot delete a later capture");
        ProcessTransfur.setPlayerTransfurVariant(player, other);
        later.getDecision().postTransfurListener().accept(IAbstractChangedEntity.forPlayer(player));
        check(InvoluntaryTransfurNegotiation.canNegotiate(player, nextSource), "Ordinary assimilation remains negotiable");

        reset(player);
        var interrupted = stage(player, source, variant, true);
        ProcessTransfur.setPlayerTransfurVariant(player, variant);
        ProcessTransfur.removePlayerTransfurVariant(player);
        interrupted.getDecision().postTransfurListener().accept(IAbstractChangedEntity.forEntity(source));
        check(data(player) == null && !ProcessTransfur.isPlayerTransfurred(player),
                "Direct TF then UNTF invalidates a pending capture before its callback");

        var mismatch = stage(player, source, variant, true);
        ProcessTransfur.setPlayerTransfurVariant(player, other);
        mismatch.getDecision().postTransfurListener().accept(IAbstractChangedEntity.forEntity(source));
        check(data(player) == null && ProcessTransfur.getPlayerTransfurVariantSafe(player).orElseThrow().getParent() == other,
                "A different command form is preserved and cannot acquire an old capture");

        reset(player);
        var absorption = stage(player, source, variant, true);
        ProcessTransfur.setPlayerTransfurVariant(player, variant);
        absorption.getDecision().postTransfurListener().accept(IAbstractChangedEntity.forPlayer(player));
        ProcessTransfur.removePlayerTransfurVariant(player);
        check(data(player) != null && data(player).getBoolean("ExternalReleasePending"),
                "A confirmed absorption keeps its ordinary external-separation recovery");
        ProcessTransfur.setPlayerTransfurVariant(player, variant);
        check(data(player) == null, "Even a same-species new TF cannot resurrect the removed capture");

        reset(player);
        var cloneAttempt = stage(player, source, variant, true);
        player.setHealth(0); // Native absorption kills before its completion callback.
        ServerPlayer clone = player(server, player.getUUID());
        InvoluntaryTransfurNegotiation.copyPlayerData(player, clone);
        cloneAttempt.getDecision().postTransfurListener().accept(IAbstractChangedEntity.forEntity(source));
        check(data(clone) != null && !data(clone).getBoolean("CapturePending") && data(clone).getBoolean("FormRestorePending"),
                "The exact pending transaction may complete on its respawn clone");
        InvoluntaryTransfurNegotiation.onPlayerReady(clone);
        check(ProcessTransfur.isPlayerTransfurred(clone) && InvoluntaryTransfurNegotiation.hasAbsorptionClaim(clone),
                "Confirmed absorption restores its captured form after respawn");

        reset(clone);
        var normal = stage(clone, nextSource, other, false);
        ProcessTransfur.setPlayerTransfurVariant(clone, other);
        normal.getDecision().postTransfurListener().accept(IAbstractChangedEntity.forPlayer(clone));
        var view = InvoluntaryTransfurNegotiation.view(clone).orElseThrow();
        net.parkabird.changedsynergy.ai.CreaturePersonality.setFamiliarity(nextSource, clone, -30);
        net.parkabird.changedsynergy.ai.LatexSocialMemory.markPatTruceBetrayal(nextSource, clone);
        net.parkabird.changedsynergy.ai.LatexSocialMemory.markRelationshipBetrayal(nextSource, clone);
        var begin = InvoluntaryTransfurNegotiation.class.getDeclaredMethod("beginRelease", ServerPlayer.class,
                ChangedEntity.class, InvoluntaryTransfurNegotiation.View.class, InvoluntaryTransfurNegotiation.View.class);
        begin.setAccessible(true); begin.invoke(null, clone, nextSource, view, view);
        if (InvoluntaryTransfurNegotiation.hasReleaseHold(nextSource)) {
            InvoluntaryTransfurNegotiation.finishReleaseHold(nextSource, clone);
        }
        check(!ProcessTransfur.isPlayerTransfurred(clone) && data(clone) == null, "Successful negotiation still releases and clears its own claim");
        check(net.parkabird.changedsynergy.ai.CreaturePersonality.hasEstablishedRelationship(nextSource, clone),
                "Successful negotiated restoration introduces both participants in balanced social mode");
        check(net.parkabird.changedsynergy.ai.RelationshipFavorService.canOpenBasicWheel(nextSource, clone),
                "Successful restoration permits direct right-click interaction even after the concluded hostile incident");
        check(net.parkabird.changedsynergy.ai.CreaturePersonality.familiarity(nextSource, clone)==0,
                "Negotiated acquaintance does not grant advanced friendship or farm positive familiarity");

        var former = stage(clone, nextSource, other, false);
        ProcessTransfur.setPlayerTransfurVariant(clone, other);
        former.getDecision().postTransfurListener().accept(IAbstractChangedEntity.forPlayer(clone));
        CompoundTag oldRelease = new CompoundTag(); oldRelease.putUUID("Player", clone.getUUID());
        oldRelease.putUUID("CaptureId", data(clone).getUUID("CaptureId")); oldRelease.putBoolean("Reversed", true);
        reset(clone);
        var newer = stage(clone, nextSource, other, false);
        ProcessTransfur.setPlayerTransfurVariant(clone, other);
        newer.getDecision().postTransfurListener().accept(IAbstractChangedEntity.forPlayer(clone));
        data(clone).putInt("Progress", 63);
        nextSource.getPersistentData().put(InvoluntaryTransfurNegotiation.RELEASE_ROOT, oldRelease);
        InvoluntaryTransfurNegotiation.abortReleaseHold(nextSource, clone);
        check(ProcessTransfur.isPlayerTransfurred(clone) && data(clone).getInt("Progress") == 63
                        && InvoluntaryTransfurNegotiation.canNegotiate(clone, nextSource),
                "An old release from the same NPC cannot erase or reverse the newer negotiation");
        reset(clone);

        var staged = stage(clone, source, variant, true);
        InvoluntaryTransfurNegotiation.onPlayerReady(clone);
        staged.getDecision().postTransfurListener().accept(IAbstractChangedEntity.forEntity(source));
        check(data(clone) == null, "Login/respawn retires unconfirmed state and rejects its subsequent callback");
        source.discard(); nextSource.discard(); player.discard(); clone.discard(); probeClone.discard();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static TransfurEvents.LatexAssimilationDecisionEvent stage(ServerPlayer player, ChangedEntity source,
            TransfurVariant<?> variant, boolean absorption) {
        var decision = LatexAssimilationDecision.strong(absorption ? LatexAssimilationDecision.Method.ABSORPTION
                : LatexAssimilationDecision.Method.REPLICATION, (TransfurVariant)variant, TransfurContext.npcLatexAttack(source), 100F);
        var event = new TransfurEvents.LatexAssimilationDecisionEvent(player, decision);
        InvoluntaryTransfurNegotiation.observeDecision(event);
        return event;
    }
    private static void reset(ServerPlayer player) {
        InvoluntaryTransfurNegotiation.abandonForSecondaryTransfur(player);
        ProcessTransfur.removePlayerTransfurVariant(player); player.setHealth(player.getMaxHealth()); player.setOnGround(true);
    }

    private static void humanoidGrab(MinecraftServer server) throws Exception {
        var player = player(server, UUID.randomUUID());
        player.moveTo(0, 80, 0); player.setOnGround(true);
        ProcessTransfur.setPlayerTransfurVariant(player, ChangedTransfurVariants.LATEX_HYPNO_CAT.get(), null, 1, false);
        var hold = IAbstractChangedEntity.forPlayer(player).getAbilityInstanceSafe(
                net.ltxprogrammer.changed.init.ChangedAbilities.GRAB_ENTITY_ABILITY.get()).orElseThrow();
        var pig = net.minecraft.world.entity.EntityType.PIG.create(server.overworld());
        pig.moveTo(1, 80, 0); server.overworld().addFreshEntity(pig);
        check(pig.getType().is(net.ltxprogrammer.changed.init.ChangedTags.EntityTypes.HUMANOIDS),
                "Issue 8 fixture adds pigs to the actual Changed humanoids tag");
        check(hold.grabEntity(pig), "Native grab accepts a datapack-added humanoid target");
        float health = pig.getHealth(); hold.attackDown = true; hold.useDown = false; hold.tickIdle();
        check(pig.getHealth() < health || pig.isRemoved(), "Attack input progresses assimilation on the extra humanoid");
        hold.releaseEntity(false); pig.discard();

        pig = net.minecraft.world.entity.EntityType.PIG.create(server.overworld());
        pig.moveTo(1, 80, 0); server.overworld().addFreshEntity(pig);
        check(hold.grabEntity(pig) && hold.suitEntity(pig), "Extra humanoid can enter the native absorption suit");
        health = pig.getHealth(); hold.attackDown = true; hold.useDown = true; hold.tickIdle();
        check(pig.getHealth() < health || pig.isRemoved(), "Combined attack/use input progresses absorption");
        hold.attackDown = hold.useDown = false;
        Field ticks = hold.getController().getClass().getDeclaredField("holdTicks"); ticks.setAccessible(true);
        ticks.setInt(hold.getController(), 40); hold.tick();
        check(!hold.suited && hold.grabbedEntity == pig, "Native hold-key action unwraps the extra humanoid into arms");
        ticks.setInt(hold.getController(), 40); hold.tick();
        check(hold.grabbedEntity == null && net.ltxprogrammer.changed.ability.GrabEntityAbility.getGrabber(pig) == null,
                "Native hold-key action releases the extra humanoid without a timer");
        pig.discard();

        var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(server.overworld());
        var npc = ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get().getEntityType().create(server.overworld());
        var decision = LatexAssimilationDecision.strongReplication(ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get(),
                TransfurContext.npcLatexAttack(npc), 3F);
        var event = new TransfurEvents.LatexAssimilationDecisionEvent(zombie, decision);
        ChangedSynergyConfig.COMMON.allowMindlessMobTransfur.set(false);
        net.parkabird.changedsynergy.event.AssimilationEcologyEvents.onLatexAssimilation(event);
        check(!event.isCanceled() && event.getDecision().method() == LatexAssimilationDecision.Method.ABSORPTION,
                "NPC mindless-target performance policy remains absorption by default");
        ChangedSynergyConfig.COMMON.allowMindlessMobTransfur.set(true);
        event = new TransfurEvents.LatexAssimilationDecisionEvent(zombie, decision);
        net.parkabird.changedsynergy.event.AssimilationEcologyEvents.onLatexAssimilation(event);
        check(!event.isCanceled() && event.getDecision().method() == LatexAssimilationDecision.Method.REPLICATION,
                "Mindless-target config still enables the native replication method");
        npc.discard(); zombie.discard(); reset(player); player.discard();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void permanentBody(MinecraftServer server) throws Exception {
        var player = player(server, UUID.randomUUID());
        var variant = ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get();
        var identity = net.parkabird.changedsynergy.origin.NativeOriginData.get(server).players.get(player.getUUID());
        identity.state = "native"; identity.form = identity.current = variant.getFormId().toString(); identity.faction = "dark";
        ProcessTransfur.setPlayerTransfurVariant(player, variant, null, 1F, false);
        var cap = (net.minecraftforge.common.capabilities.Capability)Class.forName(
                "com.lugert.true_transfur.transfur.TransfurCapabilities").getField("TRANSFUR_DATA").get(null);
        Object state = java.util.Objects.requireNonNull(player.getCapability(cap).orElse(null), "TrueTransfur capability");
        state.getClass().getMethod("setTransfurred", boolean.class).invoke(state, true);
        ProcessTransfur.removePlayerTransfurVariant(player);
        check(player.isAlive() && ProcessTransfur.getPlayerTransfurVariantSafe(player).orElseThrow().getParent() == variant,
                "TrueTransfur native reversal protection still retains the living permanent body");
        check(net.parkabird.changedsynergy.compat.TrueTransfurCompat.blocksReversal(player) && data(player) == null,
                "A rejected permanent reversal does not create or restore a negotiation claim");
        identity.state = "human"; reset(player); player.discard();
    }
    private static CompoundTag data(ServerPlayer player) {
        var persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        return persisted.contains(ROOT) ? persisted.getCompound(ROOT) : null;
    }
    private static void set(ServerPlayer player, CompoundTag claim) {
        var root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        root.getCompound(Player.PERSISTED_NBT_TAG).put(ROOT, claim);
    }
    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
        checks++; System.out.println("NEGOTIATION_CHECK: " + message);
    }
    @SuppressWarnings("unchecked")
    private static ServerPlayer player(MinecraftServer server, UUID id) throws Exception {
        var player = new ServerPlayer(server, server.overworld(), new GameProfile(id, "NegotiationTest"));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player);
        boolean registered = false;
        for (Field field : net.minecraft.server.players.PlayerList.class.getDeclaredFields()) {
            field.setAccessible(true);
            String type = field.getGenericType().getTypeName();
            if (Map.class.isAssignableFrom(field.getType()) && type.contains("UUID") && type.contains("ServerPlayer")) {
                ((Map<UUID, ServerPlayer>)field.get(server.getPlayerList())).put(id, player);
            } else if (!registered && List.class.isAssignableFrom(field.getType()) && type.contains("ServerPlayer")) {
                ((List<ServerPlayer>)field.get(server.getPlayerList())).add(player);
                registered = true;
            }
        }
        var identity = new net.parkabird.changedsynergy.origin.NativeOriginData.Identity(); identity.state = "human";
        net.parkabird.changedsynergy.origin.NativeOriginData.get(server).players.put(id, identity);
        return player;
    }
}
