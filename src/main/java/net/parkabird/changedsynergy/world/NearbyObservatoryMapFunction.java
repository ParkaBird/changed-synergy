package net.parkabird.changedsynergy.world;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.*;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Bounded, cached landmark searches keep rare-site maps from stalling chest opening. */
public final class NearbyObservatoryMapFunction extends LootItemConditionalFunction {
    public static final DeferredRegister<LootItemFunctionType> TYPES=DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE,"changed_synergy");
    private static final RegistryObject<LootItemFunctionType> TYPE=TYPES.register("nearby_observatory_map",()->new LootItemFunctionType(new Serializer()));
    private record Search(String destination,long chunk) {}
    private record CachedTarget(BlockPos target,long expires) {}
    private static final Map<ServerLevel,Map<Search,CachedTarget>> CACHE=new WeakHashMap<>();
    private final String destination;
    private NearbyObservatoryMapFunction(LootItemCondition[] conditions){this(conditions,"observatory");}
    private NearbyObservatoryMapFunction(LootItemCondition[] conditions,String destination){super(conditions);if(!Set.of("observatory","desert_refuge","forest_treehouse","hyena_hideout").contains(destination))throw new JsonParseException("Unknown nearby landmark "+destination);this.destination=destination;}
    public static LootItemFunction.Builder create(){return ()->new NearbyObservatoryMapFunction(new LootItemCondition[0]);}
    public static LootItemFunction.Builder create(String destination){return ()->new NearbyObservatoryMapFunction(new LootItemCondition[0],destination);}
    @Override public LootItemFunctionType getType(){return TYPE.get();}
    @Override protected ItemStack run(ItemStack stack,LootContext context) {
        var origin=context.getParamOrNull(LootContextParams.ORIGIN);if(origin==null)return ItemStack.EMPTY;
        var level=context.getLevel();var pos=BlockPos.containing(origin);var key=new Search(destination,ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4));
        var cache=CACHE.computeIfAbsent(level,ignored->new HashMap<>());var found=cache.get(key);
        if(found==null||found.expires()<=level.getGameTime()) {
            // Two placement regions bound the native search; nearby chests share its result for one minute.
            var target=level.findNearestMapStructure(TagKey.create(Registries.STRUCTURE,ResourceLocation.parse("changed_synergy:on_"+destination+"_maps")),pos,2,false);
            if(cache.size()>=256)cache.clear();found=new CachedTarget(target,level.getGameTime()+1200);cache.put(key,found);
        }
        if(found.target()==null)return ItemStack.EMPTY;
        var map=MapItem.create(level,found.target().getX(),found.target().getZ(),(byte)2,true,true);
        MapItem.renderBiomePreviewMap(level,map);
        MapItemSavedData.addTargetDecoration(map,found.target(),"+",MapDecoration.Type.MANSION);
        map.setHoverName(Component.translatable("item.changed_synergy."+destination+"_map"));return map;
    }
    public static final class Serializer extends LootItemConditionalFunction.Serializer<NearbyObservatoryMapFunction> {
        @Override public void serialize(JsonObject json,NearbyObservatoryMapFunction value,JsonSerializationContext context){super.serialize(json,value,context);json.addProperty("destination",value.destination);}
        @Override public NearbyObservatoryMapFunction deserialize(JsonObject json,JsonDeserializationContext context,LootItemCondition[] conditions){return new NearbyObservatoryMapFunction(conditions,json.has("destination")?json.get("destination").getAsString():"observatory");}
    }
}
