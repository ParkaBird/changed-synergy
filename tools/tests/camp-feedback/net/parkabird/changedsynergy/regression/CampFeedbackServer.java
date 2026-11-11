package net.parkabird.changedsynergy.regression;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="changed_synergy",value=Dist.DEDICATED_SERVER)
public final class CampFeedbackServer {
    @SubscribeEvent public static void run(ServerStartedEvent event){
        var server=event.getServer();
        try{
            net.parkabird.changedsynergy.ChangedSynergyConfig.SPEC.setConfig(com.electronwill.nightconfig.core.CommentedConfig.inMemory());
            DesertCommunityChecks.run(server);
            EagleOwlReleaseChecks.run(server);
            var biomes=server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
            var tag=net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME,net.minecraft.resources.ResourceLocation.parse("changed_synergy:has_structure/hyena_camp_open"));
            var actual=biomes.holders().filter(b->b.is(tag)).map(b->b.key().location().toString()).collect(java.util.stream.Collectors.toSet());
            if(!actual.equals(java.util.Set.of("minecraft:plains","minecraft:sunflower_plains")))throw new AssertionError("open camp biomes: "+actual);
            System.out.println("CAMP_FEEDBACK_SERVER_PASS: camps, clothing probability, supplies, eyes, open-camp biomes and release penalties");
        }catch(Throwable failure){System.err.println("CAMP_FEEDBACK_SERVER_FAIL");failure.printStackTrace();}
        finally{server.halt(false);}
    }
}
