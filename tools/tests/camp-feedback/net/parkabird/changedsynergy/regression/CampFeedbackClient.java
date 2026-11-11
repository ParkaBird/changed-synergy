package net.parkabird.changedsynergy.regression;

import com.mojang.blaze3d.vertex.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.ltxprogrammer.changed.client.renderer.model.armor.*;
import net.ltxprogrammer.changed.client.renderer.layers.AccessoryLayer;
import net.ltxprogrammer.changed.data.*;
import net.ltxprogrammer.changed.entity.*;
import net.ltxprogrammer.changed.init.*;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.client.model.*;
import net.parkabird.changedsynergy.init.*;

@Mod.EventBusSubscriber(modid="changed_synergy",value=Dist.CLIENT)
public final class CampFeedbackClient {
    private static int stage,checks;private static CompletableFuture<Void> work;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||stage==9)return;var mc=Minecraft.getInstance();
        try{
            org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                check(mc.gameDirectory.getCanonicalFile().getName().startsWith("camp-feedback-client-"),"isolated runtime: "+mc.gameDirectory.getCanonicalPath());
                mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(2);mc.options.simulationDistance().set(2);
                var rules=new GameRules();rules.getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(false,null);rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
                mc.createWorldOpenFlows().createFreshLevel("feedback",new LevelSettings("feedback",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(8127,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions());stage=1;
            }
            if(mc.level==null||mc.player==null||mc.player.tickCount<40||mc.getOverlay()!=null)return;
            if(stage==1){
                mc.setScreen(null);armor(mc);
                if(Boolean.getBoolean("changed_synergy.armorPreviewOnly")){stage=9;System.out.println("ARMOR_RENDER_CLIENT_PASS: "+checks+" real animation and geometry checks, plus rendered armor gallery");mc.stop();return;}
                DesertClientChecks.run(mc);PerchSleepClientChecks.run(mc);
                work=CompletableFuture.runAsync(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());ProcessTransfur.setPlayerTransfurVariant(p,ChangedSynergyTransfurVariants.LATEX_EAGLE_OWL.get(),null,1,false);},mc.getSingleplayerServer());stage=2;
            }
            if(stage==2&&work.isDone()){
                work.join();var form=ProcessTransfur.getPlayerTransfurVariant(mc.player);
                if(form!=null&&form.getParent()==ChangedSynergyTransfurVariants.LATEX_EAGLE_OWL.get())stage=3;
            }
            if(stage==3&&EagleOwlLiveReleaseChecks.tick(mc)){
                stage=9;System.out.println("CAMP_FEEDBACK_CLIENT_PASS: "+checks+" armor fitting, final poses and cap geometry checks, plus clothing, perch and real-network release suites");mc.stop();
            }
        }catch(Throwable failure){stage=9;System.err.println("CAMP_FEEDBACK_CLIENT_FAIL");failure.printStackTrace();mc.stop();}
    }
    @SuppressWarnings({"rawtypes","unchecked"})private static void armor(Minecraft mc)throws Exception{
        var previews=new ArrayList<LivingEntity>();
        for(var type:List.of(ChangedSynergyEntities.LATEX_JERBOA.get(),ChangedSynergyEntities.LATEX_EAGLE_OWL.get(),ChangedSynergyEntities.LATEX_MUNTJAC_FEMALE.get(),ChangedSynergyEntities.LATEX_SPOTTED_HYENA_FEMALE.get())){
            var mob=(ChangedEntity)type.create(mc.level);mob.tickCount=90;
            var parent=(RenderLayerParent)mc.getEntityRenderDispatcher().getRenderer(mob);var body=(AdvancedHumanoidModel)parent.getModel();
            ArmorModelSet set=mob instanceof net.parkabird.changedsynergy.entity.LatexJerboa?JerboaArmor.SET:mob instanceof net.parkabird.changedsynergy.entity.LatexEagleOwl?EagleOwlArmor.SET:mob instanceof net.parkabird.changedsynergy.entity.LatexSpottedHyena?SynergyFemaleArmor.HYENA:SynergyFemaleArmor.SLIM;
            var models=(Map<ArmorModel,LatexHumanoidArmorModel>)set.createModels(mc.getEntityModels());
            for(var entry:models.entrySet()){
                var armor=entry.getValue();body.prepareMobModel(mob,.7F,.5F,1);body.setupAnim(mob,.7F,.5F,90,40,15);
                armor.getAnimator(mob).copyProperties(body.getAnimator(mob));armor.prepareMobModel(mob,.7F,.5F,1);armor.setupAnim(mob,.7F,.5F,90,40,15);
                for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)){
                    var stack=new ItemStack(switch(slot){case HEAD->Items.IRON_HELMET;case CHEST->Items.IRON_CHESTPLATE;case LEGS->Items.IRON_LEGGINGS;default->Items.IRON_BOOTS;});
                    var pose=new PoseStack();var vertices=new Vertices();armor.prepareVisibility(slot,stack);armor.renderForSlot(mob,parent,stack,slot,pose,vertices,15728880,0,1,1,1,1);
                    check(vertices.count>0&&pose.clear(),"visible armor and balanced pose stack: "+type+entry.getKey()+slot);
                }
                if(armor instanceof JerboaArmor){
                    var v=new Vertices();armor.getTorso().render(new PoseStack(),v,15728880,0);
                    check(v.maxX-v.minX>.39&&v.maxX-v.minX<.52,"jerboa shell has 80% width rather than standard width");
                    check(java.lang.Math.abs(armor.getLeg(HumanoidArm.LEFT).y-body.getLeg(HumanoidArm.LEFT).y)<.001,"jerboa armor uses actual hip height");
                }
                if(set==SynergyFemaleArmor.SLIM||set==SynergyFemaleArmor.HYENA){
                    var v=new Vertices();armor.getTorso().render(new PoseStack(),v,15728880,0);
                    check(v.count==52,"segmented female torso has no interior or crotch cap: "+v.count);
                }
                if(armor instanceof EagleOwlArmor){
                    PerchSleepPose.apply(body);armor.renderForSlot(mob,parent,new ItemStack(Items.IRON_LEGGINGS),EquipmentSlot.LEGS,new PoseStack(),new Vertices(),15728880,0,1,1,1,1);
                    for(var side:HumanoidArm.values()){
                        String name=side==HumanoidArm.LEFT?"Left":"Right";
                        var a=armor.getLeg(side);var b=body.getLeg(side);
                        for(String joint:new String[]{"LowerLeg","Foot","Pad"}){a=a.getChild(name+joint);b=b.getChild(name+joint);check(samePose(a,b),"owl armor follows final folded "+joint);}
                    }
                }
            }
            if(mob instanceof net.parkabird.changedsynergy.entity.LatexEagleOwl){
                var item=ChangedSynergyItems.EAGLE_OWL_AVIATOR_OUTFIT.get();var renderer=AccessoryLayer.getRenderer(item).orElseThrow();
                renderer.render(new AccessorySlotContext(mob,ChangedAccessorySlots.BODY.get(),new ItemStack(item)),new PoseStack(),parent,t->new Vertices(),15728880,0,0,1,90,0,0);
                var field=renderer.getClass().getDeclaredField("outfit");field.setAccessible(true);var outfit=(ModelPart)field.get(renderer);
                for(var side:HumanoidArm.values()){
                    String name=side==HumanoidArm.LEFT?"Left":"Right";var leg=body.getLeg(side);var cloth=outfit.getChild(name+"Leg");
                    check(samePose(cloth,leg),"owl breeches follow the exact final thigh pivot");
                    var flesh=new Vertices();var fabric=new Vertices();var p=new PoseStack();leg.translateAndRotate(p);leg.getChild(side==HumanoidArm.LEFT?"LeftThigh_r9":"RightThigh_r1").render(p,flesh,15728880,0);cloth.render(new PoseStack(),fabric,15728880,0);
                    check(java.lang.Math.abs(flesh.midY()-fabric.midY())<.001&&java.lang.Math.abs(flesh.midZ()-fabric.midZ())<.001,"folded breeches and thighs share a center");
                }
            }
            for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))mob.setItemSlot(slot,new ItemStack(switch(slot){case HEAD->Items.IRON_HELMET;case CHEST->Items.IRON_CHESTPLATE;case LEGS->Items.IRON_LEGGINGS;default->Items.IRON_BOOTS;}));
            previews.add(mob);
        }
        armorGallery(mc,previews);
    }
    private static void armorGallery(Minecraft mc,List<LivingEntity> mobs)throws Exception{
        var target=new com.mojang.blaze3d.pipeline.TextureTarget(2560,1440,true,Minecraft.ON_OSX);var mv=com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();mv.pushPose();double scale=mc.getWindow().getGuiScale();mc.getWindow().setGuiScale(2);
        try{
            mv.setIdentity();mv.translate(0,0,-11000);com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().setOrtho(0,1280,720,0,1000,21000),VertexSorting.ORTHOGRAPHIC_Z);
            target.setClearColor(.11F,.12F,.15F,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);var g=new net.minecraft.client.gui.GuiGraphics(mc,mc.renderBuffers().bufferSource());
            for(int i=0;i<8;i++){
                var mob=mobs.get(i%4);float yaw=i<4?180:90;mob.yBodyRot=mob.yBodyRotO=mob.yHeadRot=mob.yHeadRotO=yaw;mob.setYRot(yaw);
                int x=160+320*(i%4),y=i<4?320:665;
                net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventory(g,x,y,110,new org.joml.Quaternionf().rotationZ((float)Math.PI),new org.joml.Quaternionf(),mob);
                g.drawCenteredString(mc.font,net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath()+(i<4?" front":" side"),x,y+12,0xFFFFFF);
            }
            g.flush();try(var image=net.minecraft.client.Screenshot.takeScreenshot(target)){image.writeToFile(mc.gameDirectory.toPath().resolve("armor-fit-gallery.png"));}
        }finally{mv.popPose();com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);mc.getWindow().setGuiScale(scale);}
    }
    private static boolean samePose(ModelPart a,ModelPart b){return Math.abs(a.x-b.x)+Math.abs(a.y-b.y)+Math.abs(a.z-b.z)+Math.abs(a.xRot-b.xRot)+Math.abs(a.yRot-b.yRot)+Math.abs(a.zRot-b.zRot)<.00001F;}
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    private static final class Vertices implements VertexConsumer{
        int count;double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,minY=Double.POSITIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY,minZ=Double.POSITIVE_INFINITY,maxZ=Double.NEGATIVE_INFINITY;
        double midY(){return(minY+maxY)/2;}double midZ(){return(minZ+maxZ)/2;}
        public VertexConsumer vertex(double x,double y,double z){minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);return this;}
        public VertexConsumer color(int r,int g,int b,int a){return this;}public VertexConsumer uv(float u,float v){return this;}public VertexConsumer overlayCoords(int u,int v){return this;}public VertexConsumer uv2(int u,int v){return this;}public VertexConsumer normal(float x,float y,float z){return this;}public void endVertex(){count++;}public void defaultColor(int r,int g,int b,int a){}public void unsetDefaultColor(){}
    }
}
