package net.parkabird.changedsynergy.regression;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;

/** Actual TT 2.0 client mixins, a real local world and real control-mode switches. */
@Mod.EventBusSubscriber(modid="changed_synergy", value=Dist.CLIENT)
public final class TrueTransfurCameraRegression {
    private static int stage, frames;
    private static boolean finished;
    private static CompletableFuture<Void> switchTask;
    private static final long START=System.nanoTime();
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(!Boolean.getBoolean("truetransfur.camera.regression")||finished||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        try {
            if(System.nanoTime()-START>240_000_000_000L)throw new AssertionError("Camera fixture timed out");
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
                Path directory=mc.gameDirectory.toPath().toAbsolutePath().normalize();
                if(!directory.getParent().getFileName().toString().equals(".codex-tmp")
                        ||!directory.getFileName().toString().startsWith("author-feedback-client-"))
                    throw new AssertionError("Not a private camera fixture directory");
                mc.options.pauseOnLostFocus=false;
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
                var rules=new GameRules();rules.getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(false,null);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
                var settings=new LevelSettings("tt-camera-regression",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("tt-camera-regression",settings,new WorldOptions(73451L,false,false),
                        registry->registry.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions());
                stage=1;
            }
            if(mc.level==null||mc.player==null||!mc.hasSingleplayerServer())return;
            if(stage==1&&mc.player.tickCount>=60) {
                var id=mc.player.getUUID();
                switchTask=mc.getSingleplayerServer().submit(()->{
                    try {
                        var player=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                        player.setGameMode(GameType.SURVIVAL);
                        ProcessTransfur.setPlayerTransfurVariant(player,ChangedTransfurVariants.LATEX_HUMAN.get(),null,1,false);
                        var cap=(net.minecraftforge.common.capabilities.Capability)Class.forName("com.lugert.true_transfur.transfur.TransfurCapabilities").getField("TRANSFUR_DATA").get(null);
                        Object data=player.getCapability(cap).orElse(null);
                        data.getClass().getMethod("setTransfurred",boolean.class).invoke(data,true);
                        Class mode=Class.forName("com.lugert.true_transfur.transfur.ControlMode");
                        Class.forName("com.lugert.true_transfur.transfur.TransfurController").getMethod("switchControlMode",net.minecraft.server.level.ServerPlayer.class,mode)
                                .invoke(null,player,Enum.valueOf(mode,"INSTINCT"));
                    }catch(Exception failure){throw new RuntimeException(failure);}
                });stage=2;
            }
            if(stage==2&&switchTask.isDone()) {
                switchTask.join();
                boolean instinct=(boolean)Class.forName("com.lugert.true_transfur.client.TransfurClientState").getMethod("isInstinct").invoke(null);
                if(!instinct||mc.getCameraEntity()==null||mc.getCameraEntity()==mc.player)return;
                for(var type:CameraType.values()) {
                    mc.options.setCameraType(type);
                    mc.gameRenderer.getMainCamera().setup(mc.level,mc.getCameraEntity(),!type.isFirstPerson(),type==CameraType.THIRD_PERSON_FRONT,1);
                }
                if(++frames<20)return;
                System.out.println("TT_CAMERA_INSTINCT_PASS: Latex Human actual INSTINCT body, all three cameras, 20 complete client ticks");
                var id=mc.player.getUUID();
                switchTask=mc.getSingleplayerServer().submit(()->net.parkabird.changedsynergy.compat.TrueTransfurCompat.ensurePlayerControl(
                        mc.getSingleplayerServer().getPlayerList().getPlayer(id)));
                stage=3;
            }
            if(stage==3&&switchTask.isDone()) {
                switchTask.join();
                if((boolean)Class.forName("com.lugert.true_transfur.client.TransfurClientState").getMethod("isInstinct").invoke(null))return;
                TakeoverGuiClientRegression.run(mc);
                System.out.println("TT_CAMERA_REGRESSION_PASS: actual TT camera arguments preserved and takeover observation retained");
                finished=true;mc.stop();
            }
        }catch(Throwable failure){System.err.println("TT_CAMERA_REGRESSION_FAIL");failure.printStackTrace();finished=true;mc.stop();}
    }
}
