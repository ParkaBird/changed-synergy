package net.parkabird.changedsynergy.event;

import java.util.Set;
import net.minecraft.advancements.critereon.LocationPredicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.ExplorationMapFunction;
import net.minecraft.world.level.storage.loot.functions.SetNameFunction;
import net.minecraft.world.level.storage.loot.predicates.AnyOfCondition;
import net.minecraft.world.level.storage.loot.predicates.LocationCheck;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Additive pools leave vanilla and other mods' chest contents intact. */
@Mod.EventBusSubscriber(modid="changed_synergy")
public final class LandmarkMapLoot {
    private static final Set<String> VANILLA = Set.of("chests/simple_dungeon", "chests/abandoned_mineshaft",
            "chests/ruined_portal", "chests/jungle_temple", "chests/pillager_outpost",
            "chests/village/village_savanna_house", "chests/village/village_fletcher",
            "chests/village/village_shepherd");
    private static final Set<String> MOUNTAIN_CHESTS=Set.of("chests/ancient_city","chests/igloo_chest",
            "chests/village/village_snowy_house","chests/village/village_taiga_house");
    private static final Set<String> DESERT_CHESTS=Set.of("chests/desert_pyramid","chests/village/village_desert_house","chests/abandoned_mineshaft","chests/ruined_portal","chests/simple_dungeon","chests/pillager_outpost");

    @SubscribeEvent public static void load(LootTableLoadEvent event) {
        var name=event.getName();
        boolean vanilla=name.getNamespace().equals("minecraft") && VANILLA.contains(name.getPath());
        boolean tower=name.getNamespace().equals("changed_synergy") && name.getPath().startsWith("chests/forest_watchtower_");
        boolean camp=name.equals(id("chests/hyena_camp_tropical"));
        boolean desertCamp=name.equals(id("chests/desert_camp_oasis"))||name.equals(id("chests/desert_camp_muddy"));
        if(desertCamp||name.getNamespace().equals("minecraft")&&DESERT_CHESTS.contains(name.getPath())){
            var pool=pool("desert_refuge",desertCamp?.25F:.05F);
            if(!desertCamp)pool.when(AnyOfCondition.anyOf(
                    LocationCheck.checkLocation(LocationPredicate.Builder.location().setBiome(Biomes.DESERT)),
                    LocationCheck.checkLocation(LocationPredicate.Builder.location().setBiome(Biomes.BADLANDS)),
                    LocationCheck.checkLocation(LocationPredicate.Builder.location().setBiome(Biomes.WOODED_BADLANDS)),
                    LocationCheck.checkLocation(LocationPredicate.Builder.location().setBiome(Biomes.ERODED_BADLANDS))));
            event.getTable().addPool(pool.build());
        }
        if(vanilla || tower) {
            var pool=pool("forest_treehouse", tower ? .25F : .05F);
            pool.when(AnyOfCondition.anyOf(
                    LocationCheck.checkLocation(LocationPredicate.Builder.location().setBiome(Biomes.FOREST)),
                    LocationCheck.checkLocation(LocationPredicate.Builder.location().setBiome(Biomes.BIRCH_FOREST)),
                    LocationCheck.checkLocation(LocationPredicate.Builder.location().setBiome(Biomes.OLD_GROWTH_BIRCH_FOREST))));
            event.getTable().addPool(pool.build());
        }
        if(vanilla || camp) {
            var pool=pool("hyena_hideout", camp ? .25F : .05F);
            pool.when(AnyOfCondition.anyOf(
                    LocationCheck.checkLocation(LocationPredicate.Builder.location().setBiome(Biomes.SAVANNA)),
                    LocationCheck.checkLocation(LocationPredicate.Builder.location().setBiome(Biomes.SAVANNA_PLATEAU)),
                    LocationCheck.checkLocation(LocationPredicate.Builder.location().setBiome(Biomes.WINDSWEPT_SAVANNA)),
                    LocationCheck.checkLocation(LocationPredicate.Builder.location().setBiome(Biomes.JUNGLE)),
                    LocationCheck.checkLocation(LocationPredicate.Builder.location().setBiome(Biomes.SPARSE_JUNGLE)),
                    LocationCheck.checkLocation(LocationPredicate.Builder.location().setBiome(Biomes.BAMBOO_JUNGLE))));
            event.getTable().addPool(pool.build());
        }
        if(tower || vanilla || name.getNamespace().equals("minecraft")&&MOUNTAIN_CHESTS.contains(name.getPath())) {
            var pool=pool("observatory",tower?.15F:.05F);
            // Watchtowers carry travelers' maps; ancient cities are mountain-associated underground sites.
            if(!tower&&!name.getPath().equals("chests/ancient_city"))pool.when(AnyOfCondition.anyOf(
                    LocationCheck.checkLocation(LocationPredicate.Builder.location().setBiome(Biomes.SNOWY_SLOPES)),
                    LocationCheck.checkLocation(LocationPredicate.Builder.location().setBiome(Biomes.GROVE)),
                    LocationCheck.checkLocation(LocationPredicate.Builder.location().setBiome(Biomes.FROZEN_PEAKS)),
                    LocationCheck.checkLocation(LocationPredicate.Builder.location().setBiome(Biomes.JAGGED_PEAKS)),
                    LocationCheck.checkLocation(LocationPredicate.Builder.location().setBiome(Biomes.STONY_PEAKS)),
                    LocationCheck.checkLocation(LocationPredicate.Builder.location().setBiome(Biomes.WINDSWEPT_HILLS)),
                    LocationCheck.checkLocation(LocationPredicate.Builder.location().setBiome(Biomes.WINDSWEPT_GRAVELLY_HILLS)),
                    LocationCheck.checkLocation(LocationPredicate.Builder.location().setBiome(Biomes.WINDSWEPT_FOREST)),
                    LocationCheck.checkLocation(LocationPredicate.Builder.location().setBiome(Biomes.MEADOW)),
                    LocationCheck.checkLocation(LocationPredicate.Builder.location().setBiome(Biomes.CHERRY_GROVE))));
            event.getTable().addPool(pool.build());
        }
    }

    private static LootPool.Builder pool(String destination, float chance) {
        var item=LootItem.lootTableItem(Items.MAP);
        if(destination.equals("observatory")||destination.equals("desert_refuge"))item.apply(net.parkabird.changedsynergy.world.NearbyObservatoryMapFunction.create(destination));
        else item.apply(ExplorationMapFunction.makeExplorationMap()
                        .setDestination(TagKey.create(Registries.STRUCTURE,id("on_"+destination+"_maps")))
                        .setMapDecoration(MapDecoration.Type.MANSION).setZoom((byte)2).setSkipKnownStructures(false))
                .apply(SetNameFunction.setName(Component.translatable("item.changed_synergy."+destination+"_map")));
        return LootPool.lootPool().name("changed_synergy_"+destination+"_map").setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(chance))
                .add(item);
    }
    private static ResourceLocation id(String path){return ResourceLocation.parse("changed_synergy:"+path);}
}
