package net.parkabird.changedsynergy.regression;

import java.util.*;
import net.ltxprogrammer.changed.entity.Gender;
import net.ltxprogrammer.changed.init.ChangedRegistry;
import net.ltxprogrammer.changed.init.ChangedItems;
import net.ltxprogrammer.changed.item.Syringe;
import net.ltxprogrammer.changed.recipe.InfuserRecipe;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.*;
import net.parkabird.changedsynergy.init.ChangedSynergyTransfurVariants;
import net.parkabird.changedsynergy.origin.NativeOriginData;

final class MuntjacFormChecks {
    private static int checks;
    static void run(MinecraftServer server) {
        var registry=ChangedRegistry.TRANSFUR_VARIANT.get();
        var recipe=(InfuserRecipe)server.getRecipeManager().byKey(id("form_latex_muntjac")).orElseThrow();
        var variants=List.of(ChangedSynergyTransfurVariants.LATEX_MUNTJAC.get(),ChangedSynergyTransfurVariants.LATEX_MUNTJAC_FEMALE.get());
        for(int i=0;i<2;i++) {
            var gender=i==0?Gender.MALE:Gender.FEMALE;var form=variants.get(i);
            var old=id(i==0?"form_latex_muntjac":"form_latex_muntjac_female");
            check(form.getFormId().equals(id("form_latex_muntjac/"+gender.getSerializedName())),"native gendered ID");
            check(registry.getValue(old)==form,"old form resolves to same instance in fresh registries");
            check(!registry.getKeys().contains(old)&&registry.getKeys().contains(form.getFormId()),"command/catalog lists canonical IDs only");
            var item=new ItemStack(ChangedItems.LATEX_SYRINGE.get());item.getOrCreateTag().putString("form",old.toString());
            check(Syringe.getVariant(item)==form,"old syringe still resolves");
            check(recipe.processItem(new ItemStack(ChangedItems.LATEX_SYRINGE.get()),gender).getOrCreateTag().getString("form").equals(form.getFormId().toString()),"native infuser produces canonical gendered item");
            check(ChangedSynergyTransfurVariants.canonicalFormId(old.toString()).equals(form.getFormId().toString()),"saved form comparison canonicalized");
            var root=new CompoundTag();var players=new ListTag();var player=new CompoundTag();var uuid=UUID.randomUUID();
            player.putUUID("Id",uuid);player.putString("State","native");player.putString("Form",old.toString());player.putString("Current",old.toString());players.add(player);root.put("Players",players);
            var restored=NativeOriginData.load(root).players.get(uuid);
            check(restored.form.equals(form.getFormId().toString())&&restored.current.equals(restored.form),"old native identity preserved");
        }
        check(ChangedSynergyTransfurVariants.canonicalFormId("changed:form_white_latex_wolf/male").equals("changed:form_white_latex_wolf/male"),"unrelated forms untouched");
        System.out.println("MUNTJAC_FORM_SERVER_PASS: "+checks+" canonical IDs, old aliases, syringe, synthesizer and origin migration checks");
    }
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("changed_synergy",path);}
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
}
