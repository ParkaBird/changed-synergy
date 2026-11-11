package net.parkabird.changedsynergy.regression;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.ltxprogrammer.changed.client.renderer.layers.AccessoryLayer;
import net.ltxprogrammer.changed.data.*;
import net.ltxprogrammer.changed.entity.AccessoryEntities;
import net.ltxprogrammer.changed.init.ChangedAccessorySlots;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.client.*;
import net.parkabird.changedsynergy.init.ChangedSynergyItems;
import net.parkabird.changedsynergy.item.*;
import org.joml.*;

final class CanopyClientChecks {
    private static int checks;
    @SuppressWarnings({"rawtypes","unchecked"}) static void run(Minecraft mc)throws Exception{
        var canopy=ChangedSynergyItems.FOREST_CANOPY_OUTFIT.get();var renderer=AccessoryLayer.getRenderer(canopy).orElseThrow();
        check(renderer instanceof ForestCanopyRenderer,"Canopy native renderer registered");
        check(mc.getItemRenderer().getModel(new ItemStack(canopy),mc.level,mc.player,0)!=mc.getModelManager().getMissingModel(),"item icon resolves");
        for(var name:List.of("changed_synergy:latex_silk_moth","changed_synergy:latex_muntjac_female","changed_synergy:latex_spotted_hyena_female","changed:white_latex_wolf_female","changed:white_latex_wolf_male","changed:latex_snake",
                "changed:latex_mermaid_shark","changed:milk_pudding","changed:dark_latex_wolf_pup","changed:pure_white_latex_wolf_pup")){
            var mob=(LivingEntity)ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.parse(name)).create(mc.level);equip(mob,canopy);
            var parent=(RenderLayerParent)mc.getEntityRenderDispatcher().getRenderer(mob);var pose=new PoseStack();var before=new Matrix4f(pose.last().pose());
            var slot=EagleOwlAviatorItem.juvenile(mob)?ChangedAccessorySlots.HEAD.get():ChangedAccessorySlots.BODY.get();
            var context=new AccessorySlotContext(mob,slot,new ItemStack(canopy));
            renderer.render(context,pose,parent,mc.renderBuffers().bufferSource(),15728880,0,0,.5F,30,0,0);
            check(before.equals(pose.last().pose()),"balanced render transform: "+name);
            var vertices=new CountVertices();
            renderer.render(context,new PoseStack(),parent,type->vertices,15728880,0,0,.5F,30,0,0);
            if(EagleOwlAviatorItem.juvenile(mob))check(vertices.count==48,"juveniles draw only the two headband pieces");
            check(vertices.count>0,"outfit actually emits geometry: "+name);
            for(var arm:HumanoidArm.values())renderer.renderFirstPersonOnArms(context,pose,parent,mc.renderBuffers().bufferSource(),15728880,arm,PartPose.ZERO,.5F);
            check(before.equals(pose.last().pose()),"balanced first-person transform");mob.discard();
        }
        mc.renderBuffers().bufferSource().endBatch();
        try(var stream=CanopyClientChecks.class.getResourceAsStream("/data/changed_synergy/structures/forest_treehouse.nbt")){
            var nbt=net.minecraft.nbt.NbtIo.readCompressed(stream);int books=0;
            for(var entry:nbt.getList("blocks",10)){
                var book=((net.minecraft.nbt.CompoundTag)entry).getCompound("nbt").getCompound("Book");
                if(book.isEmpty())continue;books++;
                for(var page:book.getCompound("tag").getList("pages",8))
                    check(mc.font.getSplitter().splitLines(page.getAsString(),114,net.minecraft.network.chat.Style.EMPTY).size()*9<=128,"journal page fits native reader");
            }check(books==1,"one canopy lectern journal");
        }
        int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
        var target=new TextureTarget(mc.getWindow().getWidth(),mc.getWindow().getHeight(),true,Minecraft.ON_OSX);var mv=RenderSystem.getModelViewStack();mv.pushPose();
        try{mv.setIdentity();mv.translate(0,0,-11000);RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,w,h,0,1000,21000),VertexSorting.ORTHOGRAPHIC_Z);
            target.setClearColor(.1F,.12F,.15F,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);var g=new GuiGraphics(mc,mc.renderBuffers().bufferSource());
            var names=List.of("changed_synergy:latex_silk_moth","changed_synergy:latex_muntjac_female","changed_synergy:latex_spotted_hyena_female","changed:white_latex_wolf_female",
                    "changed:white_latex_wolf_male","changed:latex_snake","changed:latex_mermaid_shark","changed:milk_pudding",
                    "changed:dark_latex_wolf_pup","changed:pure_white_latex_wolf_pup","changed:white_latex_centaur","changed_synergy:latex_eagle_owl");
            for(int i=0;i<names.size();i++){
                var mob=(LivingEntity)ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.parse(names.get(i))).create(mc.level);equip(mob,canopy);
                int x=(i%4*2+1)*w/8,y=(i/4+1)*(h-35)/3;draw(g,mob,x,y,h/13,180);
                g.drawCenteredString(mc.font,new String[]{"Moth: four arms","Female muntjac","Female hyena","Original female","Wolf: bipedal","Snake: upper only","Mer: upper only","Pudding: upper only","Dark pup: headband","White pup: headband","Taur: upper only","Owl: bipedal"}[i],x,y+12,0xFFFFFF);
            }g.flush();capture(mc,target,"canopy-outfit-anatomies.png");
            target.clear(Minecraft.ON_OSX);target.bindWrite(true);
            for(int row=0;row<2;row++)for(int col=0;col<4;col++){
                net.minecraft.client.player.RemotePlayer human=null;
                for(int seed=0;seed<32;seed++){
                    var probe=new net.minecraft.client.player.RemotePlayer(mc.level,new com.mojang.authlib.GameProfile(new UUID(0,seed),"CanopyPreview"));
                    if(probe.getModelName().equals(col/2==0?"default":"slim")){human=probe;break;}
                }check(human!=null,"classic and slim skin");
                net.minecraft.network.syncher.EntityDataAccessor<Byte> skin=net.minecraftforge.fml.util.ObfuscationReflectionHelper.getPrivateValue(net.minecraft.world.entity.player.Player.class,null,"f_36089_");
                human.getEntityData().set(skin,(byte)127);var item=row==0?canopy:ChangedSynergyItems.EAGLE_OWL_AVIATOR_OUTFIT.get();equip(human,item);
                int x=(col*2+1)*w/8,y=(row+1)*(h-40)/2;draw(g,human,x,y,h/8,col%2==0?180:0);
                g.drawCenteredString(mc.font,(row==0?"Canopy ":"Aviator ")+(col/2==0?"classic":"slim")+(col%2==0?" front":" back"),x,y+12,0xFFFFFF);
            }g.flush();capture(mc,target,"canopy-aviator-humans.png");
        }finally{mv.popPose();RenderSystem.applyModelViewMatrix();target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);}
        System.out.println("CANOPY_CLIENT_PASS: "+checks+" native renderer, first-person and fitting checks");
    }
    private static void equip(LivingEntity mob,Item item){
        if(mob instanceof net.ltxprogrammer.changed.entity.ChangedEntity changed){
            var eyes=new net.ltxprogrammer.changed.entity.BasicPlayerInfo();eyes.copyFrom(changed.getBasicPlayerInfo());eyes.setEyeStyle(net.ltxprogrammer.changed.entity.EyeStyle.TALL);
            changed.getEntityData().set(net.parkabird.changedsynergy.mixin.ChangedEntityAppearanceAccessor.changedSynergy$appearanceData(),eyes);
        }
        AccessoryEntities.INSTANCE.forceReloadAccessories(mob);check(AccessorySlots.getForEntity(mob).orElseThrow().quickMoveStack(new ItemStack(item)),"native outfit equip: "+mob.getType());
    }
    private static void draw(GuiGraphics g,LivingEntity mob,int x,int y,int size,float yaw){mob.tickCount=100;mob.yBodyRot=mob.yBodyRotO=mob.yHeadRot=mob.yHeadRotO=yaw;mob.setYRot(yaw);InventoryScreen.renderEntityInInventory(g,x,y,size,new Quaternionf().rotationZ((float)java.lang.Math.PI).rotateY(.12F),new Quaternionf(),mob);}
    private static void capture(Minecraft mc,TextureTarget target,String name)throws Exception{try(var image=net.minecraft.client.Screenshot.takeScreenshot(target)){image.writeToFile(mc.gameDirectory.toPath().resolve(name));}}
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    private static final class CountVertices implements VertexConsumer {
        int count;public VertexConsumer vertex(double x,double y,double z){return this;}public VertexConsumer color(int r,int g,int b,int a){return this;}public VertexConsumer uv(float u,float v){return this;}public VertexConsumer overlayCoords(int u,int v){return this;}public VertexConsumer uv2(int u,int v){return this;}public VertexConsumer normal(float x,float y,float z){return this;}public void endVertex(){count++;}public void defaultColor(int r,int g,int b,int a){}public void unsetDefaultColor(){}
    }
}
