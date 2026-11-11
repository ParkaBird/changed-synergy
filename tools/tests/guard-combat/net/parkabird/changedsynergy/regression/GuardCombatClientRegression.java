package net.parkabird.changedsynergy.regression;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Loads the reobfuscated client and exercises the two cross-mapping field reads. */
@Mod.EventBusSubscriber(modid="changed_synergy",value=Dist.CLIENT)
public final class GuardCombatClientRegression {
    private static boolean done;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        var mc=Minecraft.getInstance();
        if(done||event.phase!=TickEvent.Phase.END||!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null)return;
        done=true;
        try {
            if(!mc.gameDirectory.getName().startsWith("author-feedback-client-"))throw new AssertionError("private fixture only");
            var texture=ResourceLocation.parse("changed:textures/gui/basic_player_info.png");
            var button=new ImageButton(0,0,20,20,0,0,20,texture,20,40,ignored->{});
            if(!button.resourceLocation.equals(texture))throw new AssertionError("native clothing button texture access");
            if(!((TrapDoorBlock)Blocks.OAK_TRAPDOOR).type.canOpenByHand()
                    ||((TrapDoorBlock)Blocks.IRON_TRAPDOOR).type.canOpenByHand())throw new AssertionError("wood/iron trapdoor type access");
            System.out.println("GUARD_COMBAT_CLIENT_PASS: production client initialization and mapped clothing/trapdoor fields");
        } catch(Throwable failure) {System.err.println("GUARD_COMBAT_CLIENT_FAIL");failure.printStackTrace();}
        finally {mc.stop();}
    }
}
