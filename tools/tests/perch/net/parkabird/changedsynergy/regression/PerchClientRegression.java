package net.parkabird.changedsynergy.regression;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="changed_synergy",value=Dist.CLIENT)
public final class PerchClientRegression {
    private static int stage;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||stage==2||!Boolean.getBoolean("changed_synergy.perchClient"))return;
        var mc=Minecraft.getInstance();
        try {
            org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
                mc.options.pauseOnLostFocus=false;
                var rules=new GameRules();rules.getRule(net.parkabird.changedsynergy.init.ChangedSynergyGameRules.NATIVE_ORIGINS).set(false,null);rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
                mc.createWorldOpenFlows().createFreshLevel("perch",new LevelSettings("perch",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(20261104,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions());stage=1;
            }
            if(stage==1&&mc.level!=null&&mc.player!=null&&mc.player.tickCount>30&&mc.getOverlay()==null){PerchSleepClientChecks.run(mc);stage=2;System.out.println("PERCH_CLIENT_PASS");mc.stop();}
        }catch(Throwable error){stage=2;System.err.println("PERCH_CLIENT_FAIL");error.printStackTrace();mc.stop();}
    }
}
