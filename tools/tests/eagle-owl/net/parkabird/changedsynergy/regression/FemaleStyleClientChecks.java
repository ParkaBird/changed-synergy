package net.parkabird.changedsynergy.regression;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.ltxprogrammer.changed.client.renderer.AdvancedHumanoidRenderer;
import net.ltxprogrammer.changed.client.renderer.layers.AccessoryLayer;
import net.ltxprogrammer.changed.client.renderer.model.armor.*;
import net.ltxprogrammer.changed.data.*;
import net.ltxprogrammer.changed.entity.AccessoryEntities;
import net.ltxprogrammer.changed.init.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.parkabird.changedsynergy.client.*;
import net.parkabird.changedsynergy.client.model.*;
import net.parkabird.changedsynergy.init.*;
import net.parkabird.changedsynergy.item.*;
import org.joml.*;

final class FemaleStyleClientChecks {
    private static int checks;
    @SuppressWarnings({"unchecked", "rawtypes"}) static void run(Minecraft mc) throws Exception {
        var types=List.of(ChangedSynergyEntities.LATEX_MUNTJAC_FEMALE.get(),ChangedSynergyEntities.LATEX_SPOTTED_HYENA_FEMALE.get(),ChangedEntities.WHITE_LATEX_WOLF_FEMALE.get());
        var items=List.of(ChangedSynergyItems.HYENA_TRAILCLOTH_OUTFIT.get(),ChangedSynergyItems.EAGLE_OWL_AVIATOR_OUTFIT.get(),ChangedSynergyItems.FOREST_CANOPY_OUTFIT.get(),ChangedItems.WHITE_TSHIRT.get(),ChangedItems.LAB_COAT.get());
        for (int i=0;i<types.size();i++) {
            var mob=types.get(i).create(mc.level);
            check(HyenaTrailclothItem.synergyFemale(mob)==(i<2),"own/legacy female selection");
            var parent=(AdvancedHumanoidRenderer)mc.getEntityRenderDispatcher().getRenderer(mob);
            var set=parent.getArmorLayer().modelPicker.getModelSetForSlot(mob,EquipmentSlot.CHEST);
            var shirt=(LatexHumanoidArmorModel)set.get(ArmorModel.CLOTHING_INNER);
            if(i<2) {
                var arm=shirt.getArm(HumanoidArm.LEFT).getChild("Sleeve");
                check(arm.xScale==(i==0?.75F:1F)&&arm.zScale==arm.xScale,"native sleeves slim vs sturdy");
                int[] cubes={0};shirt.getTorso().visit(new PoseStack(),(pose,path,index,cube)->{cubes[0]++;check(cube.minZ>=-2&&cube.maxZ<=2,"no chest protrusion");});
                check(cubes[0]==3,"curved flat native clothing torso");
                var faces=new VertexCount();shirt.getTorso().render(new PoseStack(),faces,15728880,0);
                check(faces.count==56,"only outer torso caps remain; no trouser-colored interior faces");
                for(var armor:ArmorModel.values())check(set.get(armor)!=null,"all native armor/clothing layers baked");
            }
            for(var item:items) {
                AccessoryEntities.INSTANCE.forceReloadAccessories(mob);
                var slots=AccessorySlots.getForEntity(mob).orElseThrow();slots.getSlotTypes().toList().forEach(slot->slots.setItem(slot,ItemStack.EMPTY));
                check(slots.quickMoveStack(new ItemStack(item)),"native clothing equip "+item);
                var stack=slots.getItems().filter(s->s.is(item)).findFirst().orElseThrow();
                var renderer=AccessoryLayer.getRenderer(item).orElseThrow();
                var context=new AccessorySlotContext(mob,ChangedAccessorySlots.BODY.get(),stack);
                for(float angle:new float[]{0,.7F,1.5F}) {
                    parent.getModel(mob).getArm(HumanoidArm.LEFT).xRot=angle;
                    var pose=new PoseStack();var before=new Matrix4f(pose.last().pose());
                    renderer.render(context,pose,parent,mc.renderBuffers().bufferSource(),15728880,1,.6F,.5F,30,30,20);
                    for(var side:HumanoidArm.values())renderer.renderFirstPersonOnArms(context,pose,parent,mc.renderBuffers().bufferSource(),15728880,side,PartPose.ZERO,.5F);
                    check(before.equals(pose.last().pose()),"balanced third/first-person outfit transforms");
                    if(i==0)check(shirt.getArm(HumanoidArm.LEFT).getChild("Sleeve").xScale==.75F,"slim sleeve fit survives native animations");
                }
                if(item==ChangedItems.LAB_COAT.get()) {
                    var before=stack.copy();ChangedItems.LAB_COAT.get().accessoryInteract(context);
                    check(!ItemStack.isSameItemSameTags(stack,before),"native lab coat style toggle retained");
                }
            }
        }
        mc.renderBuffers().bufferSource().endBatch();
        int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
        var target=new TextureTarget(mc.getWindow().getWidth(),mc.getWindow().getHeight(),true,Minecraft.ON_OSX);
        var mv=RenderSystem.getModelViewStack();mv.pushPose();
        try {
            mv.setIdentity();mv.translate(0,0,-11000);RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,w,h,0,1000,21000),VertexSorting.ORTHOGRAPHIC_Z);
            target.setClearColor(.1F,.12F,.15F,1);
            for(int species=0;species<3;species++) {
                target.clear(Minecraft.ON_OSX);target.bindWrite(true);var g=new GuiGraphics(mc,mc.renderBuffers().bufferSource());
                for(int row=0;row<2;row++)for(int col=0;col<5;col++) {
                    var mob=types.get(species).create(mc.level);AccessoryEntities.INSTANCE.forceReloadAccessories(mob);
                    var slots=AccessorySlots.getForEntity(mob).orElseThrow();slots.getSlotTypes().toList().forEach(slot->slots.setItem(slot,ItemStack.EMPTY));
                    check(slots.quickMoveStack(new ItemStack(items.get(col))),"gallery equip");
                    float yaw=row==0?180:0;mob.tickCount=100;mob.yBodyRot=mob.yBodyRotO=mob.yHeadRot=mob.yHeadRotO=yaw;mob.setYRot(yaw);
                    int x=(col*2+1)*w/10,y=(row+1)*(h-35)/2;
                    InventoryScreen.renderEntityInInventory(g,x,y,h/9,new Quaternionf().rotationZ((float)java.lang.Math.PI).rotateY(.25F),new Quaternionf(),mob);
                    g.drawCenteredString(mc.font,new String[]{"Trailcloth","Aviator","Canopy","Native T-shirt","Native lab coat"}[col],x,y+10,0xffffff);
                }
                g.flush();try(var image=net.minecraft.client.Screenshot.takeScreenshot(target)){image.writeToFile(mc.gameDirectory.toPath().resolve("female-style-"+species+".png"));}
            }
            var legacyTypes=List.of("changed:white_latex_wolf_female","changed:latex_snow_leopard_female","changed:latex_rabbit_female",
                    "changed:latex_shark_female","changed_addon:latex_snow_fox_female","changed_addon:exp_1_female");
            for(int page=0;page<2;page++) {
                target.clear(Minecraft.ON_OSX);target.bindWrite(true);var g=new GuiGraphics(mc,mc.renderBuffers().bufferSource());
                for(int row=0;row<2;row++)for(int col=0;col<3;col++) {
                    String id=legacyTypes.get(page*3+col);var type=net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(net.minecraft.resources.ResourceLocation.parse(id));
                    if(type==null)continue;var mob=(LivingEntity)type.create(mc.level);
                    check(HyenaTrailclothItem.female(mob)&&!HyenaTrailclothItem.synergyFemale(mob),"legacy feminine anatomy: "+id);
                    AccessoryEntities.INSTANCE.forceReloadAccessories(mob);var slots=AccessorySlots.getForEntity(mob).orElseThrow();
                    slots.getSlotTypes().toList().forEach(slot->slots.setItem(slot,ItemStack.EMPTY));
                    check(slots.quickMoveStack(new ItemStack(ChangedSynergyItems.EAGLE_OWL_AVIATOR_OUTFIT.get())),"legacy aviator equip: "+id);
                    float yaw=row==0?180:270;mob.tickCount=100;mob.yBodyRot=mob.yBodyRotO=mob.yHeadRot=mob.yHeadRotO=yaw;mob.setYRot(yaw);
                    if(row==1){mob.setShiftKeyDown(true);mob.setPose(Pose.CROUCHING);}
                    int x=(col*2+1)*w/6,y=(row+1)*(h-35)/2;
                    InventoryScreen.renderEntityInInventory(g,x,y,h/9,new Quaternionf().rotationZ((float)java.lang.Math.PI).rotateY(.15F),new Quaternionf(),mob);
                    g.drawCenteredString(mc.font,id.substring(id.indexOf(':')+1).replace("latex_", ""),x,y+10,0xffffff);
                }
                g.flush();try(var image=net.minecraft.client.Screenshot.takeScreenshot(target)){image.writeToFile(mc.gameDirectory.toPath().resolve("aviator-legacy-female-"+page+".png"));}
            }
            target.clear(Minecraft.ON_OSX);target.bindWrite(true);var g=new GuiGraphics(mc,mc.renderBuffers().bufferSource());
            var feedbackItems=List.of(ChangedItems.BLACK_PANTS.get(),ChangedSynergyItems.HYENA_TRAILCLOTH_OUTFIT.get(),ChangedSynergyItems.FOREST_CANOPY_OUTFIT.get(),ChangedSynergyItems.EAGLE_OWL_AVIATOR_OUTFIT.get());
            for(int row=0;row<2;row++)for(int col=0;col<4;col++) {
                var mob=types.get(row).create(mc.level);AccessoryEntities.INSTANCE.forceReloadAccessories(mob);var slots=AccessorySlots.getForEntity(mob).orElseThrow();
                slots.getSlotTypes().toList().forEach(slot->slots.setItem(slot,ItemStack.EMPTY));
                check(slots.quickMoveStack(new ItemStack(feedbackItems.get(col))),"low-angle feedback outfit equip");
                float yaw=col==2?0:180;mob.tickCount=100;mob.yBodyRot=mob.yBodyRotO=mob.yHeadRot=mob.yHeadRotO=yaw;mob.setYRot(yaw);
                int x=(col*2+1)*w/8,y=(row+1)*(h-35)/2;
                InventoryScreen.renderEntityInInventory(g,x,y,h/9,new Quaternionf().rotationZ((float)java.lang.Math.PI).rotateX(-.45F).rotateY(.2F),new Quaternionf(),mob);
                g.drawCenteredString(mc.font,new String[]{"Pants: no inner caps","Trailcloth: joined","Canopy: ribbon/caps","Aviator: caps"}[col],x,y+10,0xffffff);
            }
            g.flush();try(var image=net.minecraft.client.Screenshot.takeScreenshot(target)){image.writeToFile(mc.gameDirectory.toPath().resolve("female-style-feedback-low.png"));}
        } finally {mv.popPose();RenderSystem.applyModelViewMatrix();target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);}
        System.out.println("FEMALE_STYLE_CLIENT_PASS: "+checks+" own and legacy cuts, native clothing, first-person, poses and style toggle checks");
    }
    private static void check(boolean value,String why) {if(!value)throw new AssertionError(why);checks++;}
    private static final class VertexCount implements VertexConsumer {
        int count;public VertexConsumer vertex(double x,double y,double z){return this;}public VertexConsumer color(int r,int g,int b,int a){return this;}public VertexConsumer uv(float u,float v){return this;}public VertexConsumer overlayCoords(int u,int v){return this;}public VertexConsumer uv2(int u,int v){return this;}public VertexConsumer normal(float x,float y,float z){return this;}public void endVertex(){count++;}public void defaultColor(int r,int g,int b,int a){}public void unsetDefaultColor(){}
    }
}
