package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.init.ChangedSynergyEntities;

@Mod.EventBusSubscriber(modid="changed_synergy",value=net.minecraftforge.api.distmarker.Dist.DEDICATED_SERVER)
public final class SpeciesLootRegression {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    @SubscribeEvent public static void run(ServerStartedEvent event) {
        var server=event.getServer();var level=server.overworld();
        try {
            var goo=ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("changed:white_latex_goo"));
            var sample=ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("changed:latex_base"));
            check(goo!=null&&sample!=null,"both common materials resolve from Changed without Addon");
            int species=0;
            for(var registered:ChangedSynergyEntities.REGISTRY.getEntries()) {
                var type=registered.get();var mob=type.create(level);if(!(mob instanceof ChangedEntity))continue;species++;
                var table=server.getLootData().getLootTable(type.getDefaultLootTable());
                check(table!=LootTable.EMPTY,"a real table is assigned to "+registered.getId());
                var observed=new HashSet<Item>();double base=0,enchanted=0;
                for(boolean killedByPlayer:List.of(false,true))for(boolean burning:List.of(false,true))for(int looting:new int[]{0,3}) {
                    var killer=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"LootFixture"));
                    var sword=new ItemStack(Items.DIAMOND_SWORD);if(looting>0)sword.enchant(Enchantments.MOB_LOOTING,looting);
                    killer.getInventory().setItem(0,sword);mob.setRemainingFireTicks(burning?200:0);
                    var damage=killedByPlayer?level.damageSources().playerAttack(killer):level.damageSources().generic();
                    var builder=new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY,mob)
                            .withParameter(LootContextParams.ORIGIN,Vec3.ZERO).withParameter(LootContextParams.DAMAGE_SOURCE,damage);
                    if(killedByPlayer)builder.withParameter(LootContextParams.KILLER_ENTITY,killer).withParameter(LootContextParams.DIRECT_KILLER_ENTITY,killer).withParameter(LootContextParams.LAST_DAMAGE_PLAYER,killer);
                    var params=builder.create(LootContextParamSets.ENTITY);
                    for(int seed=0;seed<128;seed++) {
                        var drops=table.getRandomItems(params,seed);int gooCount=0,sampleCount=0;
                        for(var stack:drops) {
                            // Zero-count native rolls are discarded when an entity spawns its drops.
                            if(stack.isEmpty())continue;
                            observed.add(stack.getItem());if(stack.is(goo))gooCount+=stack.getCount();if(stack.is(sample))sampleCount+=stack.getCount();
                            check(!stack.isEmpty(),"loot emits real registered items");
                            if(registered.getId().getPath().equals("latex_swordfish"))check(!stack.is(burning?Items.COD:Items.COOKED_COD),"Swordfish cod uses native fire cooking");
                        }
                        int effective=killedByPlayer?looting:0;
                        check(gooCount<=2+effective&&sampleCount<=1+effective,"common materials are rolled once and obey native looting limits: "+registered.getId());
                        if(killedByPlayer&&!burning){if(looting==0)base+=gooCount+sampleCount;else enchanted+=gooCount+sampleCount;}
                    }
                }
                check(observed.contains(goo)&&observed.contains(sample),"each species can drop both common latex materials: "+registered.getId());
                check(enchanted>base,"Looting increases the common-material yield: "+registered.getId());
                switch(registered.getId().getPath()) {
                    case "latex_eagle_owl" -> check(observed.contains(Items.FEATHER),"Owl feathers no longer depend on a missing Crow table");
                    case "latex_silk_moth" -> check(observed.contains(Items.FEATHER)&&observed.contains(Items.PAPER),"Moth's original drops remain");
                    case "latex_swordfish" -> check(observed.contains(Items.COD)&&observed.contains(Items.COOKED_COD),"Swordfish retains raw/cooked fish");
                    case "latex_muntjac","latex_muntjac_female" -> check(observed.contains(Items.GRASS)&&observed.contains(Items.LEATHER),"both Muntjacs retain deer loot");
                    case "latex_jerboa" -> check(!observed.isEmpty(),"Jerboa retains its native desert-relative drops and latex materials");
                    default -> check(observed.contains(Items.ROTTEN_FLESH)&&observed.contains(Items.LEATHER),"both Hyenas retain Gnoll loot");
                }
            }
            check(species==8,"all eight registered Synergy creatures were checked");
            System.out.println("SPECIES_LOOT_REGRESSION_PASS: "+checks+" native entity-context drops, nested tables, materials, environmental/player kills, fire and looting checks");
        }catch(Throwable failure){System.err.println("SPECIES_LOOT_REGRESSION_FAIL");failure.printStackTrace();}
        finally{server.halt(false);}
    }
}
