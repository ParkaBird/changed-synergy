package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedAbilities;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ai.BondedSuitService;
import net.parkabird.changedsynergy.entity.MuntjacJumpService;
import net.parkabird.changedsynergy.entity.MuntjacJumpState;
import net.parkabird.changedsynergy.init.ChangedSynergyEntities;

@Mod.EventBusSubscriber(modid = "changed_synergy")
public final class MuntjacJumpRegression {
    private static int checks;
    @SubscribeEvent public static void start(ServerStartedEvent event) {
        var server = event.getServer();
        if (!server.isDedicatedServer()) return;
        try {
            var player = new ServerPlayer(server, server.overworld(), new GameProfile(UUID.randomUUID(), "MuntjacJump"));
            player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player);
            player.moveTo(0, 120, 0);
            for (var type : List.of(ChangedSynergyEntities.LATEX_MUNTJAC.get(), ChangedSynergyEntities.LATEX_MUNTJAC_FEMALE.get(),ChangedSynergyEntities.LATEX_JERBOA.get())) {
                var body = type.create(server.overworld());
                near(body.getAttributeBaseValue(net.ltxprogrammer.changed.init.ChangedAttributes.JUMP_STRENGTH.get()),
                        ChangedEntities.LATEX_MING_CAT.get().create(server.overworld()).getAttributeBaseValue(net.ltxprogrammer.changed.init.ChangedAttributes.JUMP_STRENGTH.get()),"base jump matches the native feline preset");
                ProcessTransfur.setPlayerTransfurVariant(player, body.getSelfVariant(), null, 1, false);
                check(MuntjacJumpService.refresh(player), "male/female native form eligible");
                ordinaryAndCharged(player);
                landings(player);
                var strength = player.getAttribute(net.ltxprogrammer.changed.init.ChangedAttributes.JUMP_STRENGTH.get());
                double oldStrength = strength.getBaseValue();
                for (double factor : new double[]{1, 1.5}) {
                    strength.setBaseValue(factor);
                    player.addEffect(new MobEffectInstance(MobEffects.JUMP, 200, 1));
                    double boosted = ordinary(player);
                    charge(player, true);
                    player.tickCount += 10;
                    MuntjacJumpService.input(player, false);
                    player.jumpFromGround();
                    near(player.getDeltaMovement().y, (boosted - .2 * factor) * 2 + .2 * factor, "native jump boost remains additive at strength " + factor);
                    player.removeEffect(MobEffects.JUMP);
                }
                strength.setBaseValue(oldStrength);
                charge(player, true);
                player.getAbilities().flying = true;
                check(!MuntjacJumpService.refresh(player) && !MuntjacJumpService.state(player).charging(), "flight cancels charge");
                player.getAbilities().flying = false;
                ProcessTransfur.removePlayerTransfurVariant(player);
                check(!MuntjacJumpService.refresh(player), "human loses species passive");

                BondedSuitService.ensureGrabAbility(body);
                var grab = body.getAbilityInstance(ChangedAbilities.GRAB_ENTITY_ABILITY.get());
                check(grab.suitEntity(player), "real native suit wraps a human");
                ((net.ltxprogrammer.changed.entity.LivingEntityDataExtension)player).setGrabbedBy(body);
                grab.grabbedHasControl = true;
                check(ProcessTransfur.getPlayerTransfurVariant(player).isTemporaryFromSuit(), "suit has temporary form");
                check(MuntjacJumpService.refresh(player), "controlling human receives passive despite temporary suit");
                ordinaryAndCharged(player);
                landings(player);
                charge(player, true);
                grab.grabbedHasControl = false;
                net.parkabird.changedsynergy.ai.LatexSocialMemory.endFriendlySuit(body, player);
                check(!MuntjacJumpService.refresh(player), "NPC control cannot be bypassed by charging");
                grab.grabbedHasControl = true;
                grab.releaseEntity(false);
                check(!MuntjacJumpService.refresh(player) && !MuntjacJumpService.state(player).charging(), "suit release clears gesture and pending boost");
            }
            var deer = ChangedEntities.LATEX_DEER.get().create(server.overworld());
            ProcessTransfur.setPlayerTransfurVariant(player, deer.getSelfVariant());
            check(!MuntjacJumpService.refresh(player), "original deer retains original jump");
            var saved = player.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            check(!saved.toString().contains("muntjacCharge"), "runtime gesture is not saved");
            gestures();
            System.out.println("MUNTJAC_JUMP_REGRESSION_PASS: " + checks + " tap/hold, native velocity, potion and human-suit checks");
        } catch (Throwable failure) {
            System.err.println("MUNTJAC_JUMP_REGRESSION_FAIL");
            failure.printStackTrace();
        } finally { server.halt(false); }
    }

    private static double ordinary(ServerPlayer player) {
        MuntjacJumpService.state(player).cancel(false);
        player.setOnGround(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.jumpFromGround();
        return player.getDeltaMovement().y;
    }

    private static void charge(ServerPlayer player, boolean grounded) {
        MuntjacJumpService.state(player).cancel(false);
        player.setOnGround(grounded);
        MuntjacJumpService.input(player, true);
        check(MuntjacJumpService.state(player).holding() && !MuntjacJumpService.state(player).charging(), "first press waits for short/long decision");
        player.tickCount += MuntjacJumpState.HOLD_TICKS;
        MuntjacJumpService.state(player).tick(player.tickCount, grounded);
        check(MuntjacJumpService.state(player).charging() == grounded, "holding on the ground starts charge; airborne hold waits");
    }

    private static void ordinaryAndCharged(ServerPlayer player) {
        double normal = ordinary(player);
        MuntjacJumpService.input(player, true);
        player.tickCount++;
        near(MuntjacJumpService.input(player, false), 0, "short tap releases a normal jump");
        player.jumpFromGround();
        near(player.getDeltaMovement().y, normal, "short tap retains normal takeoff velocity");
        charge(player, true);
        player.setDeltaMovement(Vec3.ZERO);
        player.jumpFromGround();
        near(player.getDeltaMovement().y, 0, "held charge suppresses normal takeoff");
        player.tickCount += 10;
        near(MuntjacJumpService.input(player, false), 1, "ten ground ticks reach horse peak");
        player.jumpFromGround();
        near(player.getDeltaMovement().y, normal * 2, "charged jump uses twice normal base velocity");
        player.setDeltaMovement(Vec3.ZERO);
        player.jumpFromGround();
        near(player.getDeltaMovement().y, normal, "boost is consumed after one jump");
        charge(player, false);
        player.tickCount += 10;
        check(MuntjacJumpService.input(player, false) < 0, "airborne release cannot double jump");
        charge(player, false);
        player.setOnGround(true);
        MuntjacJumpService.state(player).tick(player.tickCount, true);
        player.tickCount += MuntjacJumpState.HOLD_TICKS + 10;
        near(MuntjacJumpService.input(player, false), 1, "airborne hold charges only after landing");
        MuntjacJumpService.state(player).cancel(false);
    }

    private static void landings(ServerPlayer player) {
        charge(player,true);player.tickCount+=10;MuntjacJumpService.input(player,false);player.jumpFromGround();
        var state=MuntjacJumpService.state(player);double y=player.getY();state.trackFlight(y+6,false);
        var landing=new net.minecraftforge.event.entity.living.LivingFallEvent(player,6,1);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(landing);near(landing.getDistance(),0,"normal charged landing removes only achieved rise");
        var repeated=new net.minecraftforge.event.entity.living.LivingFallEvent(player,10,1);net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(repeated);near(repeated.getDistance(),10,"landing protection is consumed once");
        charge(player,true);player.tickCount+=10;MuntjacJumpService.input(player,false);player.jumpFromGround();state.trackFlight(y+6,false);
        landing=new net.minecraftforge.event.entity.living.LivingFallEvent(player,16,1);net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(landing);near(landing.getDistance(),10,"ten-block drop below launch retains ten-block fall");
        state.beginFlight(y,true);state.trackFlight(y+1,false);near(state.landingDistance(11,y-10),10,"low ceiling cannot grant unused jump height");
        state.beginFlight(y,false);state.trackFlight(y+6,false);near(state.landingDistance(16,y-10),16,"ordinary jump does not grant charged protection");
        state.beginFlight(y,true);state.trackFlight(y+6,false);state.trackFlight(y,true);state.trackFlight(y,true);state.trackFlight(y,true);near(state.landingDistance(10,y-10),10,"safe landing clears leftover protection");
    }

    private static void gestures() {
        var s = new MuntjacJumpState();
        Object form = new Object();
        s.bind(form);
        for (int t = 0; t < 6; t += 2) {
            s.input(true, t, true);
            near(s.input(false, t + 1, true), 0, "rapid short taps remain separate ordinary jumps");
        }
        check(!s.charging(), "multiple short taps never activate a special gesture");
        s.cancel(false);
        s.input(true, 10, true);
        for (int i = 11; i <= 23; i++) s.input(true, i, true);
        near(s.charge(23), 1, "repeated down messages do not restart a held charge");
        near(s.input(false, 23, true), 1, "one long press reaches full charge");
        check(s.input(false, 24, true) < 0, "duplicate release cannot jump twice");
        s.cancel(false);
        s.input(true, 0, true); s.tick(MuntjacJumpState.HOLD_TICKS, true);
        s.bind(form);
        check(s.charging(), "stable body preserves charge");
        s.bind(new Object());
        check(!s.charging() && s.multiplier(10) == 1, "changing bodies resets all state");
        s.input(true, 20, true); s.tick(24, false);
        check(!s.holding() && s.input(false, 25, false) < 0, "leaving the ground cancels without a jump");
        s.queueJump(1, 10);
        near(s.multiplier(17), 1, "abandoned release cannot leave a stored jump boost");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); checks++; }
    private static void near(double actual, double expected, String message) { check(Math.abs(actual - expected) < .0001, message + ": " + actual + " vs " + expected); }
}
