package net.parkabird.changedsynergy.regression;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.ltxprogrammer.changed.client.renderer.layers.AccessoryLayer;
import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.ltxprogrammer.changed.data.*;
import net.ltxprogrammer.changed.entity.*;
import net.ltxprogrammer.changed.init.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.init.*;
import org.joml.*;

final class DesertClientChecks {
    private static int checks;
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    private static final List<String> SPECIES=List.of("changed_synergy:latex_jerboa","changed:latex_fennec_fox","changed_synergy:latex_silk_moth","changed_synergy:latex_muntjac_female","changed_synergy:latex_spotted_hyena_female","changed:white_latex_wolf_female","changed:latex_snake","changed:latex_mermaid_shark","changed:gas_wolf_pup","changed:dark_latex_wolf_pup","changed:milk_pudding","changed:white_latex_centaur");
    @SuppressWarnings({"rawtypes","unchecked"})static void run(Minecraft mc)throws Exception{
        var directory=mc.gameDirectory.toPath().resolve("desert-previews");java.nio.file.Files.createDirectories(directory);
        var mobs=new ArrayList<LivingEntity>();for(String id:SPECIES){var mob=(LivingEntity)ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.parse(id)).create(mc.level);if(mob instanceof ChangedEntity latex){var eyes=new BasicPlayerInfo();eyes.copyFrom(latex.getBasicPlayerInfo());eyes.setEyeStyle(EyeStyle.TALL);eyes.setScleraColor(net.ltxprogrammer.changed.util.Color3.WHITE);eyes.setLeftIrisColor(net.ltxprogrammer.changed.util.Color3.fromInt(0xD84CA2));eyes.setRightIrisColor(net.ltxprogrammer.changed.util.Color3.fromInt(0xD84CA2));latex.getEntityData().set(net.parkabird.changedsynergy.mixin.ChangedEntityAppearanceAccessor.changedSynergy$appearanceData(),eyes);}mobs.add(mob);}
        for(String model:List.of("default","slim"))for(int seed=0;seed<64;seed++){
            var human=new net.minecraft.client.player.RemotePlayer(mc.level,new com.mojang.authlib.GameProfile(new UUID(0,seed),"DesertPreview"));
            if(human.getModelName().equals(model)){mobs.add(human);break;}
        }
        for(var item:List.of(ChangedSynergyItems.DESERT_NOMAD_OUTFIT.get())){
            check(mc.getItemRenderer().getModel(new ItemStack(item),mc.level,mc.player,0)!=mc.getModelManager().getMissingModel(),"real item icon loaded");
            var renderer=AccessoryLayer.getRenderer(item).orElseThrow();
            for(var mob:mobs){equip(mob,item);var parent=(RenderLayerParent)mc.getEntityRenderDispatcher().getRenderer(mob);var slots=AccessorySlots.getForEntity(mob).orElseThrow();
                var slot=slots.getSlotTypes().filter(s->slots.getItem(s).filter(i->i.is(item)).isPresent()).findFirst().orElseThrow();var context=new AccessorySlotContext(mob,slot,slots.getItem(slot).orElseThrow());
                for(float angle:new float[]{0,.7F,1.5F}){
                    if(parent.getModel() instanceof AdvancedHumanoidModel model&&model.getArm(HumanoidArm.LEFT)!=null)model.getArm(HumanoidArm.LEFT).xRot=angle;
                    var pose=new PoseStack();var before=new Matrix4f(pose.last().pose());var count=new CountVertices();renderer.render(context,pose,parent,type->count,15728880,0,.6F,.5F,40,20,-25);
                    check(before.equals(pose.last().pose())&&count.count>0,"balanced visible outfit: "+mob.getType());
                    if(net.parkabird.changedsynergy.item.EagleOwlAviatorItem.juvenile(mob))check(count.count>48,"juvenile renders full authored saddle and bracers");
                    for(var arm:HumanoidArm.values())renderer.renderFirstPersonOnArms(context,pose,parent,mc.renderBuffers().bufferSource(),15728880,arm,PartPose.ZERO,.5F);
                    check(before.equals(pose.last().pose()),"balanced first-person sleeves");
                }
            }
            gallery(mc,mobs,item,directory.resolve("wayfarer-anatomies.png"));
        }
        var jerboa=(ChangedEntity)mobs.get(0);var parent=(RenderLayerParent)mc.getEntityRenderDispatcher().getRenderer(jerboa);var model=(AdvancedHumanoidModel)parent.getModel();
        for(var item:List.of(ChangedSynergyItems.HYENA_TRAILCLOTH_OUTFIT.get(),ChangedSynergyItems.FOREST_CANOPY_OUTFIT.get(),ChangedSynergyItems.EAGLE_OWL_AVIATOR_OUTFIT.get(),ChangedItems.LAB_COAT.get(),ChangedItems.WHITE_TSHIRT.get(),ChangedItems.BLACK_PANTS.get())){
            equip(jerboa,item);check(AccessorySlots.isWearing(jerboa,s->s.is(item)),"existing outfit supports jerboa: "+item);
            var renderer=AccessoryLayer.getRenderer(item).orElseThrow();var context=new AccessorySlotContext(jerboa,item==ChangedItems.BLACK_PANTS.get()?ChangedAccessorySlots.LEGS.get():ChangedAccessorySlots.BODY.get(),new ItemStack(item));
            renderer.render(context,new PoseStack(),parent,mc.renderBuffers().bufferSource(),15728880,0,.4F,.5F,50,20,30);
        }
        equip(jerboa,ChangedSynergyItems.DESERT_NOMAD_OUTFIT.get());gallery(mc,List.of(jerboa),ChangedSynergyItems.DESERT_NOMAD_OUTFIT.get(),directory.resolve("pathfinder-detail.png"));
        System.out.println("DESERT_CLIENT_CHECKS_PASS: "+checks+" real item models, native equipment, all body cuts, first-person poses and existing garments");
    }
    private static void equip(LivingEntity mob,Item item){AccessoryEntities.INSTANCE.forceReloadAccessories(mob);var slots=AccessorySlots.getForEntity(mob).orElseThrow();slots.getSlotTypes().toList().forEach(s->slots.setItem(s,ItemStack.EMPTY));check(slots.quickMoveStack(new ItemStack(item)),"native equip "+mob.getType()+" "+item);}
    private static void gallery(Minecraft mc,List<LivingEntity> mobs,Item item,java.nio.file.Path path)throws Exception{
        var target=new TextureTarget(2560,1440,true,Minecraft.ON_OSX);var mv=RenderSystem.getModelViewStack();mv.pushPose();double scale=mc.getWindow().getGuiScale();mc.getWindow().setGuiScale(2);
        try{mv.setIdentity();mv.translate(0,0,-11000);RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,1280,720,0,1000,21000),VertexSorting.ORTHOGRAPHIC_Z);target.setClearColor(.11F,.12F,.15F,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);var g=new GuiGraphics(mc,mc.renderBuffers().bufferSource());
            if(mobs.size()==1){for(int i=0;i<4;i++){var mob=mobs.get(0);float yaw=i==0?180:i==1?140:i==2?90:0;mob.tickCount=80;mob.yBodyRot=mob.yBodyRotO=mob.yHeadRot=mob.yHeadRotO=yaw;mob.setYRot(yaw);InventoryScreen.renderEntityInInventory(g,160+320*i,530,135,new Quaternionf().rotationZ((float)java.lang.Math.PI),new Quaternionf(),mob);}}
            else for(int i=0;i<mobs.size();i++){var mob=mobs.get(i);mob.tickCount=80;mob.yBodyRot=mob.yBodyRotO=mob.yHeadRot=mob.yHeadRotO=180;mob.setYRot(180);int x=160+320*(i%4),y=145+175*(i/4);InventoryScreen.renderEntityInInventory(g,x,y,45,new Quaternionf().rotationZ((float)java.lang.Math.PI).rotateY(.18F),new Quaternionf(),mob);g.drawCenteredString(mc.font,i<SPECIES.size()?SPECIES.get(i).replace("changed_synergy:","").replace("changed:",""):i==12?"Human classic":"Human slim",x,y+16,0xFFFFFF);}
            g.flush();try(var image=net.minecraft.client.Screenshot.takeScreenshot(target)){image.writeToFile(path);}
        }finally{mv.popPose();RenderSystem.applyModelViewMatrix();target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);mc.getWindow().setGuiScale(scale);}
    }
    private static final class CountVertices implements VertexConsumer{
        int count;public VertexConsumer vertex(double x,double y,double z){return this;}public VertexConsumer color(int r,int g,int b,int a){return this;}public VertexConsumer uv(float u,float v){return this;}public VertexConsumer overlayCoords(int u,int v){return this;}public VertexConsumer uv2(int u,int v){return this;}public VertexConsumer normal(float x,float y,float z){return this;}public void endVertex(){count++;}public void defaultColor(int r,int g,int b,int a){}public void unsetDefaultColor(){}
    }
}
