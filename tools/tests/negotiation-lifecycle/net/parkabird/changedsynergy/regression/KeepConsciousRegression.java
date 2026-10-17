package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.entity.TransfurCause;
import net.ltxprogrammer.changed.entity.TransfurContext;
import net.ltxprogrammer.changed.entity.ai.ImmediateTransfurDecision;
import net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision;
import net.ltxprogrammer.changed.entity.ai.NonLatexAssimilationDecision;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.ltxprogrammer.changed.init.ChangedGameRules;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.*;

/** Exercises actual native death transitions, not just the survival flag. */
public final class KeepConsciousRegression {
    public static void run(MinecraftServer server) throws Exception {
        var rules = server.overworld().getGameRules();
        var config = ChangedSynergyConfig.COMMON;
        boolean kept = rules.getBoolean(ChangedGameRules.RULE_KEEP_BRAIN);
        boolean animation = rules.getBoolean(ChangedGameRules.RULE_DO_TRANSFUR_ANIMATION);
        boolean enabled = config.takeoverEnabled.get(), tokens = config.bondTokensRequired.get();
        var method = config.creatureTransfurMethod.get();
        var override = new ConsciousOverride();
        MinecraftForge.EVENT_BUS.register(override);
        try {
            config.takeoverEnabled.set(true);
            config.bondTokensRequired.set(false);
            rules.getRule(ChangedGameRules.RULE_KEEP_BRAIN).set(false, server);
            var probePlayer = player(server);
            var consciousEvent = new ProcessTransfur.KeepConsciousEvent(probePlayer,
                    ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get(), TransfurContext.hazard(TransfurCause.GRAB_ABSORB), false);
            Changed.postModEvent(consciousEvent);
            check(!consciousEvent.shouldKeepConscious, "Final consciousness event overrides a competing listener");
            probePlayer.discard();
            for (boolean animate : new boolean[]{false, true}) {
                rules.getRule(ChangedGameRules.RULE_DO_TRANSFUR_ANIMATION).set(animate, server);
                for (var mode : ChangedSynergyConfig.CreatureTransfurMethod.values()) {
                    config.creatureTransfurMethod.set(mode);
                    for (var nativeMethod : LatexAssimilationDecision.Method.values()) {
                        var player = player(server);
                        var source = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(server.overworld());
                        source.setPos(player.position().add(1, 0, 0));
                        try {
                            check(!TakeoverService.authorizeOrdinary(source, player,
                                    InvoluntaryTransfurNegotiation.Reason.SELF_DEFENSE), "Disabled rule rejects authorization: " + mode);
                            var decision = LatexAssimilationDecision.strong(nativeMethod, source.getSelfVariant(),
                                    TransfurContext.npcLatexHazard(source, nativeMethod == LatexAssimilationDecision.Method.ABSORPTION
                                            ? TransfurCause.GRAB_ABSORB : TransfurCause.GRAB_REPLICATE), 100F);
                            check(ProcessTransfur.progressTransfur(player, decision), "Native forced exposure is accepted");
                            check(!TakeoverService.active(player) && !InvoluntaryTransfurNegotiation.hasAbsorptionClaim(player),
                                    "Forced conversion cannot become takeover/respawn claim: " + mode + "/" + nativeMethod);
                            complete(player);
                            var result = ProcessTransfur.getPlayerTransfurVariant(player);
                            check(!player.isAlive(), "Native forced conversion kills: " + mode + "/" + nativeMethod + "/animation=" + animate
                                    + "; health=" + player.getHealth() + "; form=" + (result == null ? "none" : result.getFormId())
                                    + "; survives=" + (result != null && result.willSurviveTransfur));
                        } finally { source.discard(); player.discard(); }
                    }
                }
                for (boolean latex : new boolean[]{false, true}) {
                    var player = player(server);
                    try {
                        var form = ChangedTransfurVariants.WHITE_LATEX_WOLF_MALE.get();
                        if (latex) ProcessTransfur.progressTransfur(player, LatexAssimilationDecision.strong(
                                LatexAssimilationDecision.Method.REPLICATION, form, TransfurContext.hazard(TransfurCause.LATEX_SYRINGE_FLOOR), 100F));
                        else ProcessTransfur.progressTransfur(player, NonLatexAssimilationDecision.fromBlockOrItem(
                                form, TransfurCause.LATEX_PUDDLE, 100F, 0));
                        complete(player);
                        check(!player.isAlive() && !TakeoverService.active(player), "Environmental conversion kills: latex=" + latex);
                    } finally { player.discard(); }
                }
            }
            rules.getRule(ChangedGameRules.RULE_DO_TRANSFUR_ANIMATION).set(true, server);
            for (boolean safe : new boolean[]{false, true}) {
                var player = player(server);
                try {
                    var form = ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get();
                    ProcessTransfur.transfur(player, safe ? ImmediateTransfurDecision.safe(form, TransfurCause.SYRINGE)
                            : ImmediateTransfurDecision.unsafe(form, TransfurCause.CRYSTAL));
                    complete(player);
                    check(player.isAlive() == safe, "Explicit safe conversion survives; unsafe conversion dies: " + safe);
                } finally { player.discard(); }
            }
            var willing = player(server);
            try {
                var item = net.ltxprogrammer.changed.init.ChangedItems.LATEX_SYRINGE.get();
                var stack = new net.minecraft.world.item.ItemStack(item);
                stack.getOrCreateTag().putString("form", ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get().getFormId().toString());
                willing.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
                willing.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
                item.finishUsingItem(stack, willing.level(), willing); complete(willing);
                check(willing.isAlive() && ProcessTransfur.getPlayerTransfurVariantSafe(willing).orElseThrow().willSurviveTransfur,
                        "An actual self-administered syringe is voluntary; floor syringes remain lethal");
                ProcessTransfur.removePlayerTransfurVariant(willing);
                var apply = net.parkabird.changedsynergy.origin.NativeOriginService.class.getDeclaredMethod("apply",
                        ServerPlayer.class, net.ltxprogrammer.changed.entity.variant.TransfurVariant.class);
                apply.setAccessible(true);
                check((Boolean)apply.invoke(null, willing, ChangedTransfurVariants.WHITE_LATEX_WOLF_MALE.get()), "Explicit starting origin remains available");
                complete(willing);
                check(willing.isAlive() && ProcessTransfur.getPlayerTransfurVariantSafe(willing).orElseThrow().willSurviveTransfur,
                        "Chosen starting origin survives with the rule disabled");
            } finally { willing.discard(); }
            if (net.minecraftforge.fml.ModList.get().isLoaded("changed_addon")) secondaryConversions(server);
            var player = player(server);
            var source = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(server.overworld());
            try {
                source.setPos(player.position().add(1, 0, 0)); server.overworld().addFreshEntity(source);
                CreaturePersonality.establishRelationship(source, player);
                CreaturePersonality.setFamiliarity(source, player, 60);
                FactionReputation.set(source, player, 0);
                check(VoluntaryBondTransfurService.canOffer(source, player), "Confirmed bond transfur remains available with the rule off");
                check(!VoluntaryBondTransfurService.select(source, player)
                        && VoluntaryBondTransfurService.select(source, player), "Bond transfur still requires confirmation");
                complete(player);
                check(player.isAlive() && LatexSocialMemory.isBonded(source, player)
                        && ProcessTransfur.getPlayerTransfurVariantSafe(player).orElseThrow().willSurviveTransfur,
                        "Confirmed bond conversion survives and establishes the bond");
                check(!TakeoverService.beginBondedConfinement(source, player), "Bond confinement cannot bypass the disabled rule");
                ProcessTransfur.removePlayerTransfurVariant(player);
                BondedSuitService.ensureGrabAbility(source);
                var hold = BondedSuitService.ability(source);
                check(hold.suitEntity(player), "Friendly wrapping is still available with the rule off");
                complete(player);
                check(player.isAlive() && ProcessTransfur.getPlayerTransfurVariantSafe(player).orElseThrow().isTemporaryFromSuit(),
                        "Temporary friendly suit is not a lethal transfur");
                hold.releaseEntity(false);
                // A mid-conversion rule change must invalidate even a cached behavior and authorization.
                rules.getRule(ChangedGameRules.RULE_KEEP_BRAIN).set(true, server);
                var probe = player(server);
                try {
                    config.creatureTransfurMethod.set(ChangedSynergyConfig.CreatureTransfurMethod.TAKEOVER);
                    check(TakeoverService.authorizeOrdinary(source, probe, InvoluntaryTransfurNegotiation.Reason.SELF_DEFENSE),
                            "Re-enabling the rule restores takeover authorization");
                    var behavior = ProcessTransfur.computeAssimilationBehavior(probe, LatexAssimilationDecision.strong(
                            LatexAssimilationDecision.Method.ABSORPTION, source.getSelfVariant(),
                            TransfurContext.npcLatexHazard(source, TransfurCause.GRAB_ABSORB), 100F));
                    rules.getRule(ChangedGameRules.RULE_KEEP_BRAIN).set(false, server);
                    check(!TakeoverService.beginOrdinary(source, probe), "Cached takeover intent is checked again at completion");
                    behavior.stepAssimilate(); complete(probe);
                    check(!probe.isAlive() && !TakeoverService.active(probe), "Cached assimilation falls through to native death after disabling the rule");
                } finally { probe.discard(); }
            } finally { source.discard(); player.discard(); }
            System.out.println("KEEP_CONSCIOUS_REGRESSION_PASS: forced NPC and environmental conversion, actual death, safe conversion, voluntary bond, friendly suit and cached authorization");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(override);
            rules.getRule(ChangedGameRules.RULE_KEEP_BRAIN).set(kept, server);
            rules.getRule(ChangedGameRules.RULE_DO_TRANSFUR_ANIMATION).set(animation, server);
            config.takeoverEnabled.set(enabled); config.bondTokensRequired.set(tokens); config.creatureTransfurMethod.set(method);
        }
    }

    public static final class ConsciousOverride {
        @SubscribeEvent public void onKeep(ProcessTransfur.KeepConsciousEvent event) { event.shouldKeepConscious = true; }
    }

    private static void secondaryConversions(MinecraftServer server) throws Exception {
        var api = Class.forName("net.foxyas.changedaddon.entity.api.IGrabberEntity");
        for (String name : new String[]{"HostileTransfurredGrabGoal", "OrganicAssimilationGrabGoal"}) {
            var player = player(server);
            var source = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(server.overworld());
            try {
                source.setPos(player.position().add(1, 0, 0)); server.overworld().addFreshEntity(source);
                ProcessTransfur.setPlayerTransfurVariant(player, ChangedTransfurVariants.WHITE_LATEX_WOLF_MALE.get(), null, 1F);
                BondedSuitService.ensureGrabAbility(source);
                var type = Class.forName("net.parkabird.changedsynergy.compat.addon." + name);
                var goal = type.getConstructor(net.ltxprogrammer.changed.entity.ChangedEntity.class, api).newInstance(source, source);
                var target = type.getDeclaredField("target"); target.setAccessible(true); target.set(goal, player);
                var ability = type.getDeclaredField("ability"); ability.setAccessible(true); ability.set(goal, BondedSuitService.ability(source));
                var finish = type.getDeclaredMethod(name.startsWith("Organic") ? "completeSecondaryAssimilation" : "completeSecondaryTransfur");
                finish.setAccessible(true); finish.invoke(goal);
                check(!ProcessTransfur.getPlayerTransfurVariantSafe(player).orElseThrow().willSurviveTransfur,
                        "Secondary conversion resets the inherited survival flag: " + name);
                complete(player);
                check(!player.isAlive(), "Secondary conversion completes native death: " + name);
            } finally { source.discard(); player.discard(); }
        }
    }

    private static ServerPlayer player(MinecraftServer server) {
        var player = new ServerPlayer(server, server.overworld(), new GameProfile(UUID.randomUUID(), "ConsciousCheck"));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player);
        net.minecraftforge.fml.util.ObfuscationReflectionHelper.setPrivateValue(ServerPlayer.class, player, 0, "f_8921_");
        player.setPos(128.5, 160, 128.5);
        var identity = new net.parkabird.changedsynergy.origin.NativeOriginData.Identity(); identity.state = "human";
        net.parkabird.changedsynergy.origin.NativeOriginData.get(server).players.put(player.getUUID(), identity);
        return player;
    }

    private static void complete(ServerPlayer player) throws Exception {
        var instance = ProcessTransfur.getPlayerTransfurVariant(player);
        if (instance == null) return;
        var tick = instance.getClass().getDeclaredMethod("tickTransfurProgress"); tick.setAccessible(true);
        for (int i = 0; i < 800 && instance.transfurProgression < 1 && player.isAlive(); i++) tick.invoke(instance);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        System.out.println("KEEP_CONSCIOUS_CHECK: " + message);
    }
}
