package net.parkabird.changedsynergy.authoring;

import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Read a disposable copy of an editing world using the native template serializer. */
@Mod.EventBusSubscriber(modid="changed_synergy", value=Dist.DEDICATED_SERVER)
public final class StructureEditorExport {
    private static MinecraftServer server;
    private static int ticks;
    @SubscribeEvent public static void start(ServerStartedEvent event) {
        if (!Boolean.getBoolean("changed_synergy.exportTreehouse")) return;
        server=event.getServer();
        for(int x=-1;x<=2;x++)for(int z=-1;z<=2;z++) {
            server.overworld().getChunk(x,z);
            server.overworld().setChunkForced(x,z,true);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(server==null || event.phase!=TickEvent.Phase.END || ++ticks<40)return;
        try {
            var template=new StructureTemplate();
            template.fillFromWorld(server.overworld(),new BlockPos(0,63,0),new BlockPos(27,41,24),true,Blocks.STRUCTURE_VOID);
            var tag=template.save(new CompoundTag());
            NbtIo.writeCompressed(tag,Path.of("exported-treehouse.nbt").toFile());
            if(tag.getList("entities",10).size()!=9)throw new AssertionError("Unexpected resident/decorations count: "+tag.getList("entities",10).size());
            System.out.println("TREEHOUSE_EXPORT_PASS: "+tag.getList("blocks",10).size()+" blocks; "+tag.getList("entities",10).size()+" entities");
        }catch(Throwable error){System.err.println("TREEHOUSE_EXPORT_FAIL");error.printStackTrace();}
        finally{server.halt(false);server=null;}
    }
}
