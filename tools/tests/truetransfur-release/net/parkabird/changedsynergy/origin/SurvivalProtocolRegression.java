package net.parkabird.changedsynergy.origin;

import java.lang.reflect.Method;
import net.ltxprogrammer.changed.entity.TransfurContext;
import net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.LazyOptional;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.compat.SurvivalProtocolCompat;

/** Exercises the supplied CSP distributable, without compile-time linking or editing it. */
final class SurvivalProtocolRegression {
    private static int checks;
    static void run(MinecraftServer server) throws Exception {
        checks=0;
        var player=TrueTransfurReleaseRegression.player(server,"SurvivalProtocolCheck");
        TrueTransfurReleaseRegression.identity(player,false);
        var level=server.overworld();
        var body=ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        try {
            Class<?> caps=Class.forName("net.stonenibbler.changed_survive_protocol.common.data.CSPCapabilities");
            Object data=((LazyOptional<?>)caps.getMethod("get",net.minecraft.world.entity.player.Player.class).invoke(null,player)).orElseThrow(()->new AssertionError("Missing CSP capability"));
            Class<?> type=data.getClass();Method reset=type.getMethod("reset"),settled=type.getMethod("setSettledStrainId",String.class);
            reset.invoke(data);
            ProcessTransfur.setPlayerTransfurProgress(player,1);
            check(ProcessTransfur.getPlayerTransfurProgress(player)==0&&(double)type.getMethod("getCoverage").invoke(data)>0,
                    "Unmanaged exposure still uses Survival Protocol's infection meter");
            reset.invoke(data);
            settled.invoke(data,ChangedTransfurVariants.DARK_LATEX_WOLF_FEMALE.get().getFormId().toString());
            Method apply=NativeOriginService.class.getDeclaredMethod("apply",ServerPlayer.class,net.ltxprogrammer.changed.entity.variant.TransfurVariant.class);apply.setAccessible(true);
            check((boolean)apply.invoke(null,player,ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get())
                    &&ProcessTransfur.getPlayerTransfurVariant(player).getParent()==ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get(),
                    "Choosing a native origin replaces an old settled strain with the selected species");
            String selected=(String)type.getMethod("getSettledStrainId").invoke(data);
            check(selected.equals(ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get().getFormId().toString()),
                    "CSP and the chosen native identity persist the same form");
            check(!SurvivalProtocolCompat.changeOrigin(player,"",()->false)
                    &&selected.equals(type.getMethod("getSettledStrainId").invoke(data)),"Rejected origin transaction restores previous CSP data");
            Method human=NativeOriginService.class.getDeclaredMethod("finishHuman",ServerPlayer.class,boolean.class);human.setAccessible(true);human.invoke(null,player,true);
            check(!ProcessTransfur.isPlayerTransfurred(player)&&!((boolean)type.getMethod("hasSettledStrain").invoke(data)),
                    "Human re-selection clears settled-form restoration instead of immediately re-transforming");
            check(!SurvivalProtocolCompat.nativeProgress(player)&&!SurvivalProtocolCompat.originChange(player),"Scoped origin and progress permissions do not leak");
            player.setPos(.5,180,.5);body.setPos(1.3,180,.5);level.setBlockAndUpdate(new BlockPos(0,179,0),Blocks.STONE.defaultBlockState());
            level.addFreshEntity(body);BondedSuitService.ensureGrabAbility(body);
            ChangedSynergyConfig.COMMON.takeoverEnabled.set(true);ChangedSynergyConfig.COMMON.takeoverPunitive.set(true);
            ChangedSynergyConfig.COMMON.creatureTransfurMethod.set(ChangedSynergyConfig.CreatureTransfurMethod.TAKEOVER);
            check(TakeoverService.authorizeOrdinary(body,player,InvoluntaryTransfurNegotiation.Reason.SELF_DEFENSE),"NPC absorption is explicitly authorized for takeover");
            reset.invoke(data);
            var decision=LatexAssimilationDecision.weakAbsorption(body.getSelfVariant(),TransfurContext.npcLatexAttack(body),1);
            for(int hit=0;hit<80&&!TakeoverService.active(player);hit++) {
                player.invulnerableTime=0;player.hurtTime=0;
                var behavior=ProcessTransfur.computeAssimilationBehavior(player,decision);
                check(behavior instanceof net.parkabird.changedsynergy.compat.SurvivalProtocolAssimilation,
                        "Managed absorption uses the scoped bridge, got "+(behavior==null?"null":behavior.getClass().getName()));
                behavior.stepAssimilate();
                if(hit==0||hit==12)System.out.println("CSP_CAPTURE_SAMPLE hit="+hit+" progress="+ProcessTransfur.getPlayerTransfurProgress(player)
                        +" coverage="+type.getMethod("getCoverage").invoke(data)+" canBegin="+TakeoverService.canBeginOrdinary(body,player)
                        +" contact="+TakeoverService.canPhysicallyCapture(body,player)+" permanent="+ProcessTransfur.isPlayerTransfurred(player));
            }
            check(TakeoverService.active(player)&&TakeoverService.contains(body,player),"CSP permits native progress to complete into Synergy's temporary takeover");
            check((double)type.getMethod("getInfectionPercent").invoke(data)==0,"Managed capture does not add duplicate permanent infection");
            type.getMethod("setInfectionPercent",double.class).invoke(data,100.0);
            Class.forName("net.stonenibbler.changed_survive_protocol.common.event.CSPTransfurEvents")
                    .getMethod("forceUncontrolledTransfur",ServerPlayer.class,type).invoke(null,player,data);
            check(TakeoverService.active(player)&&ProcessTransfur.getPlayerTransfurVariant(player).isTemporaryFromSuit(),
                    "A scheduled CSP infection completion cannot erase an active temporary suit");
            check(TakeoverService.forceReleaseByCommand(player)&&!ProcessTransfur.isPlayerTransfurred(player)&&!player.noPhysics,
                    "Temporary takeover releases safely to human under CSP");

            var nether=server.getLevel(Level.NETHER);nether.getChunk(1,1);
            player.teleportTo(nether,20.5,80,20.5,0,0);
            for(BlockPos at:BlockPos.betweenClosed(19,79,19,21,82,21))nether.setBlockAndUpdate(at,at.getY()==79?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
            for(BlockPos at:BlockPos.betweenClosed(19,122,19,21,133,21))nether.setBlockAndUpdate(at,at.getY()==127?Blocks.BEDROCK.defaultBlockState():Blocks.AIR.defaultBlockState());
            var surface=TakeoverSafety.findSurface(player,player.position(),null,1,false,.6,1.8);
            check(surface.isPresent()&&surface.get().y==80,"Nether surface fallback chooses the local cavern floor instead of the bedrock roof");
            check(TakeoverSafety.find(player,new Vec3(20.5,128,20.5),null,1,false).isEmpty(),
                    "An explicit roof candidate cannot move a below-roof player above the ceiling");
            System.out.println("SURVIVAL_PROTOCOL_REGRESSION_PASS: "+checks+" checks");
        } finally {
            if(TakeoverService.active(player))TakeoverService.forceReleaseByCommand(player);
            body.discard();server.getPlayerList().remove(player);
        }
    }
    private static void check(boolean condition,String text) {checks++;if(!condition)throw new AssertionError(text);}
}
