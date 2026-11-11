package net.parkabird.changedsynergy.authoring;

import java.util.ArrayList;
import java.nio.file.Files;
import net.ltxprogrammer.changed.init.ChangedGameRules;
import net.ltxprogrammer.changed.world.features.structures.HangingBlockFixerProcessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

/** Authoring-only server build; never included in the release JAR. */
@Mod.EventBusSubscriber(modid="changed_synergy", value=Dist.DEDICATED_SERVER)
public final class StructureEditorWorld {
    private static final boolean TREEHOUSE=Boolean.getBoolean("changed_synergy.treehouseEditor");
    private static final String[] NAMES=TREEHOUSE?new String[]{"forest_treehouse"}:new String[]{"white_lab_tower","red_lab_tower","green_lab","water_tower_changed"};
    private static final String[] LABELS=TREEHOUSE?new String[]{"森林树屋"}:new String[]{"白色实验室塔","红色实验室塔","绿色实验室塔","水下实验室"};
    private static final BlockPos[] ORIGINS=TREEHOUSE?new BlockPos[]{new BlockPos(0,63,0)}:new BlockPos[]{new BlockPos(0,64,0),new BlockPos(160,64,0),new BlockPos(0,64,160),new BlockPos(160,64,160)};
    private static int books,containers,creatures;

    @SubscribeEvent public static void build(ServerStartedEvent event) {
        var server=event.getServer();
        try {
            if(!Boolean.getBoolean("changed_synergy.buildEditorWorld"))return;
            var level=server.overworld();var rules=level.getGameRules();
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
            rules.getRule(GameRules.RULE_MOBGRIEFING).set(false,server);
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false,server);
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false,server);
            rules.getRule(GameRules.RULE_DOFIRETICK).set(false,server);
            rules.getRule(GameRules.RULE_RANDOMTICKING).set(0,server);
            rules.getRule(GameRules.RULE_KEEPINVENTORY).set(true,server);
            rules.getRule(GameRules.RULE_SPAWN_RADIUS).set(0,server);
            rules.getRule(ChangedGameRules.RULE_LATEX_GROWTH_RATE).set(0,server);
            rules.getRule(ChangedGameRules.RULE_NPC_WANT_FUSE_PLAYER).set(false,server);
            rules.getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(false,server);
            level.setDayTime(6000);level.setWeatherParameters(999999,0,false,false);
            level.setDefaultSpawnPos(new BlockPos(-12,64,-12),-45);
            var metadata=server.getWorldPath(LevelResource.ROOT).resolve("synergy_editing/source-templates");Files.createDirectories(metadata);
            for(int i=0;i<NAMES.length;i++) {
                var id=ResourceLocation.parse("changed_synergy:"+NAMES[i]);
                CompoundTag raw;
                try(var stream=server.getResourceManager().open(ResourceLocation.parse("changed_synergy:structures/"+NAMES[i]+".nbt"))) {raw=NbtIo.readCompressed(stream);}
                NbtIo.writeCompressed(raw,metadata.resolve(NAMES[i]+".nbt").toFile());
                var origin=ORIGINS[i];var template=server.getStructureManager().get(id).orElseThrow();var size=template.getSize();
                // Native entity loading validates wall anchors before the template applies its final move.
                for(var value:raw.getList("entities",Tag.TAG_COMPOUND)) {
                    var record=(CompoundTag)value;var tag=record.getCompound("nbt");
                    if(tag.contains("TileX")) {
                        var local=record.getList("blockPos",Tag.TAG_INT);tag.putInt("TileX",origin.getX()+local.getInt(0));
                        tag.putInt("TileY",origin.getY()+local.getInt(1));tag.putInt("TileZ",origin.getZ()+local.getInt(2));
                    }
                }
                template.load(BuiltInRegistries.BLOCK.asLookup(),raw);
                for(int x=(origin.getX()-4)>>4;x<=(origin.getX()+size.getX()+4)>>4;x++)
                    for(int z=(origin.getZ()-4)>>4;z<=(origin.getZ()+size.getZ()+4)>>4;z++)level.getChunk(x,z);
                var settings=new StructurePlaceSettings().setRotation(Rotation.NONE).setKeepLiquids(false).setKnownShape(true).setIgnoreEntities(false)
                        .addProcessor(HangingBlockFixerProcessor.INSTANCE);
                if(!template.placeInWorld(level,origin,origin,settings,level.random,18))throw new AssertionError("Template placement failed: "+id);
                int siteBooks=0,siteContainers=0;
                for(int x=0;x<size.getX();x++)for(int y=0;y<size.getY();y++)for(int z=0;z<size.getZ();z++) {
                    var pos=origin.offset(x,y,z);var be=level.getBlockEntity(pos);if(be==null)continue;
                    if(be instanceof RandomizableContainerBlockEntity chest)chest.unpackLootTable(null);
                    var data=be.saveWithFullMetadata();int count=editableBooks(data);
                    if(count>0){be.load(data);be.setChanged();siteBooks+=count;}
                    if(be instanceof Container)siteContainers++;
                }
                var area=new AABB(origin,origin.offset(size.getX(),size.getY(),size.getZ()));
                for(var entity:level.getEntities(null,area)) {
                    if(entity instanceof HangingEntity hanging && !area.contains(net.minecraft.world.phys.Vec3.atCenterOf(hanging.getPos())))
                        throw new AssertionError("Decoration anchor outside the structure: "+entity.getType()+" "+hanging.getPos());
                    if(entity instanceof LivingEntity) {
                        var original=new CompoundTag();original.putBoolean("Invulnerable",entity.isInvulnerable());original.putBoolean("NoGravity",entity.isNoGravity());
                        if(entity instanceof Mob mob){original.putBoolean("NoAI",mob.isNoAi());mob.setNoAi(true);mob.setPersistenceRequired();}
                        entity.getPersistentData().put("SynergyEditorOriginalFlags",original);entity.setInvulnerable(true);entity.setNoGravity(true);creatures++;
                    }
                    var saved=entity.saveWithoutId(new CompoundTag());int count=editableBooks(saved);
                    if(count>0){entity.load(saved);siteBooks+=count;}
                }
                books+=siteBooks;containers+=siteContainers;
                for(int x=-2;x<=size.getX()+1;x++)for(int z:new int[]{-2,size.getZ()+1})level.setBlock(origin.offset(x,TREEHOUSE?0:-1,z),Blocks.YELLOW_CONCRETE.defaultBlockState(),2);
                for(int z=-2;z<=size.getZ()+1;z++)for(int x:new int[]{-2,size.getX()+1})level.setBlock(origin.offset(x,TREEHOUSE?0:-1,z),Blocks.YELLOW_CONCRETE.defaultBlockState(),2);
                var board=origin.offset(size.getX()/2,TREEHOUSE?1:0,-5);level.setBlock(board,Blocks.OAK_SIGN.defaultBlockState(),3);
                if(level.getBlockEntity(board) instanceof SignBlockEntity sign) {
                    var text=new SignText().setMessage(0,Component.literal(LABELS[i])).setMessage(1,Component.literal("方块／书籍／光盘可编辑"))
                            .setMessage(2,Component.literal(i==3?"原海平面 Y=91":"改完后保存并退出"))
                            .setMessage(3,Component.literal("范围角点 "+origin.toShortString()));
                    sign.setText(text,true);sign.setText(text,false);sign.setChanged();
                }
                if(siteContainers==0)throw new AssertionError("No editable containers: "+id);
                System.out.println("EDITOR_SITE_READY "+NAMES[i]+" origin="+origin+" size="+size+" books="+siteBooks+" containers="+siteContainers);
            }
            if(books==0)throw new AssertionError("Authored books were not made editable");
            level.setBlock(new BlockPos(-12,64,-10),Blocks.CHEST.defaultBlockState(),3);
            var chest=(Container)level.getBlockEntity(new BlockPos(-12,64,-10));
            chest.setItem(0,guide());chest.setItem(1,new ItemStack(Items.WRITABLE_BOOK,8));chest.setItem(2,new ItemStack(Items.ENDER_PEARL,16));chest.setItem(3,new ItemStack(Items.MILK_BUCKET));
            chest.setChanged();
            Files.writeString(server.getWorldPath(LevelResource.ROOT).resolve("synergy_editing/generation.txt"),"Editable books="+books+"\nContainers="+containers+"\nFrozen creatures="+creatures+"\nSource templates use Rotation.NONE.\n");
            System.out.println("STRUCTURE_EDITOR_WORLD_PASS: structures="+NAMES.length+"; editable books="+books+"; containers="+containers+"; frozen creatures="+creatures);
        } catch(Throwable failure) {System.err.println("STRUCTURE_EDITOR_WORLD_FAIL");failure.printStackTrace();}
        finally {if(Boolean.getBoolean("changed_synergy.buildEditorWorld"))server.halt(false);}
    }

    private static int editableBooks(Tag value) {
        if(value instanceof CompoundTag data) {
            if(data.getString("id").equals("minecraft:writable_book")&&data.contains("Count"))return 1;
            if(data.getString("id").equals("minecraft:written_book")&&data.contains("Count")) {
                var original=data.copy();var tag=data.getCompound("tag");var pages=tag.getList("pages",Tag.TAG_STRING);var editable=new ListTag();
                for(int i=0;i<pages.size();i++) {var component=Component.Serializer.fromJson(pages.getString(i));editable.add(StringTag.valueOf(component==null?"":component.getString()));}
                data.putString("id","minecraft:writable_book");tag.put("pages",editable);tag.put("SynergyEditorOriginal",original);tag.remove("resolved");
                if(!tag.getCompound("display").contains("Name")) {var display=tag.getCompound("display");display.putString("Name",Component.Serializer.toJson(Component.literal(tag.getString("title")+"（可编辑）")));tag.put("display",display);}
                data.put("tag",tag);return 1;
            }
            int count=0;for(var key:new ArrayList<>(data.getAllKeys()))if(!key.equals("SynergyEditorOriginal"))count+=editableBooks(data.get(key));return count;
        }
        if(value instanceof ListTag list){int count=0;for(var child:list)count+=editableBooks(child);return count;}return 0;
    }
    private static ItemStack guide() {
        var book=new ItemStack(Items.WRITTEN_BOOK);var tag=book.getOrCreateTag();tag.putString("title","结构编辑说明");tag.putString("author","Changed: Synergy");
        var pages=new ListTag();
        String[] pagesText=TREEHOUSE?new String[]{"树屋编辑地图\n\n创造模式，允许指令。\n\n讲台上的原书已转为可编辑书本。取出后右键改字，完成后放回原位置。可以不签名。",
                "树屋位置\n\n/tp @s 13 64 -6\n\n原点：0 63 0\n范围：27 × 41 × 24\n\n黄色边线为辅助标记。NPC 静止，刷怪和随机刻已关闭。",
                "编辑后保存并退出，再告诉我存档路径。我会提取树屋及其文字内容。\n\n如需扩建到黄色边线外，请说明范围。保留 synergy_editing 文件夹中的坐标与原始模板。"}:new String[]{"结构编辑地图\n\n创造模式，允许指令。\n\n书架、讲台、箱子里的原书已转为可编辑书本。取出后右键改字，完成后放回原位置。可以不签名。",
                "光盘\n\n从箱子取出，拿在手里右键电脑，修改文字后点击完成，再放回原箱子。\n\n水下研究光盘在：\n203 98 193 附近。",
                "传送位置\n\n白塔：18 65 -6\n红塔：182 65 -6\n绿塔：32 65 154\n水下设施：196 65 154\n\n用 /tp @s x y z 传送。",
                "保存与交回\n\n编辑后保存并退出，告诉我存档路径即可。无需操作 NBT，也无需自己使用结构方块。\n\n黄色边线外是编辑辅助区域，不会导入建筑。如需向外扩建，请说明。",
                "编辑专用设置\n\nNPC 已静止并设为无敌。随机刻、自然刷怪、蔓延已关闭。\n\n水下设施外部为干燥施工区，内部水体保留。原海平面 Y=91；导回后仍按原水线生成。"};
        for(String text:pagesText)pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(text))));
        tag.put("pages",pages);return book;
    }
}
