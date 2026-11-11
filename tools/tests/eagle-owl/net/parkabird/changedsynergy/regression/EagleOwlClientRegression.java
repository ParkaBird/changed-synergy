package net.parkabird.changedsynergy.regression;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.ltxprogrammer.changed.data.*;
import net.ltxprogrammer.changed.entity.*;
import net.ltxprogrammer.changed.init.*;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.client.*;
import net.parkabird.changedsynergy.client.model.*;
import net.parkabird.changedsynergy.entity.LatexEagleOwl;
import net.parkabird.changedsynergy.init.*;
import net.parkabird.changedsynergy.item.EagleOwlAviatorItem;
import org.joml.*;

@Mod.EventBusSubscriber(modid="changed_synergy",value=Dist.CLIENT)
public final class EagleOwlClientRegression {
    private static int stage,wait,npcId,checks;private static CompletableFuture<Void> task;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||stage==9)return;var mc=Minecraft.getInstance();
        try{
            org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                check(mc.gameDirectory.getName().startsWith("author-feedback-client-"),"isolated test world");mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(2);mc.options.simulationDistance().set(2);mc.options.guiScale().set(2);
                var rules=new GameRules();rules.getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(false,null);rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
                mc.createWorldOpenFlows().createFreshLevel("owl",new LevelSettings("owl",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(78125,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions());stage=1;
            }
            if(mc.level==null||mc.player==null||mc.player.tickCount<40||mc.getOverlay()!=null)return;
            if(stage==1){task=CompletableFuture.runAsync(()->prepare(mc),mc.getSingleplayerServer());stage=2;}
            if(stage==2&&task.isDone()){
                task.join();var form=ProcessTransfur.getPlayerTransfurVariant(mc.player);
                if(form==null||form.getParent()!=ChangedSynergyTransfurVariants.LATEX_EAGLE_OWL.get()||!(mc.level.getEntity(npcId) instanceof LatexEagleOwl)||!AccessorySlots.isWearing((LivingEntity)mc.level.getEntity(npcId),s->s.is(ChangedSynergyItems.EAGLE_OWL_AVIATOR_OUTFIT.get()))){if(++wait>300)throw new AssertionError("owl and clothing synchronization");return;}
                mc.setScreen(null);stage=3;wait=0;
            }
            if(stage==3&&++wait>15){
                if(Boolean.getBoolean("changed_synergy.rangerOnly")){CanopyClientChecks.run(mc);FemaleStyleClientChecks.run(mc);stage=9;System.out.println("MUNTJAC_CLIENT_REGRESSION_PASS: Ranger clothing");mc.stop();return;}
                if(Boolean.getBoolean("changed_synergy.perchSleepOnly")){PerchSleepClientChecks.run(mc);stage=9;System.out.println("MUNTJAC_CLIENT_REGRESSION_PASS: stable perch sleep");mc.stop();return;}
                if(Boolean.getBoolean("changed_synergy.femaleStyleOnly")){FemaleStyleClientChecks.run(mc);stage=9;System.out.println("MUNTJAC_CLIENT_REGRESSION_PASS: female clothing");mc.stop();return;}
                if(Boolean.getBoolean("changed_synergy.neckProbe")){EagleOwlNeckChecks.run(mc);stage=9;System.out.println("MUNTJAC_CLIENT_REGRESSION_PASS: neck probe");mc.stop();return;}
                verify(mc,(LatexEagleOwl)mc.level.getEntity(npcId));stage=4;
            }
            if(stage==4&&EagleOwlLiveReleaseChecks.tick(mc)&&EagleOwlWheelChecks.tick(mc,(ChangedEntity)mc.level.getEntity(npcId)))stage=5;
            if(stage==5&&ArchivePageTurnChecks.tick(mc)){stage=9;System.out.println("EAGLE_OWL_CLIENT_PASS: "+checks+" models, eyes, ear motion, aerial poses, clothing, perches, shader and native night vision");System.out.println("MUNTJAC_CLIENT_REGRESSION_PASS: owl client helper completed");mc.stop();}
        }catch(Throwable failure){stage=9;System.err.println("EAGLE_OWL_CLIENT_FAIL");failure.printStackTrace();System.err.println("MUNTJAC_CLIENT_REGRESSION_FAIL");mc.stop();}
    }
    private static void prepare(Minecraft mc){var owner=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var level=owner.serverLevel();var npc=ChangedSynergyEntities.LATEX_EAGLE_OWL.get().create(level);npc.setNoAi(true);npc.setPersistenceRequired();npc.moveTo(owner.getX()+2,owner.getY(),owner.getZ());level.addFreshEntity(npc);npcId=npc.getId();equip(npc);
        var form=ProcessTransfur.setPlayerTransfurVariant(owner,ChangedSynergyTransfurVariants.LATEX_EAGLE_OWL.get(),null,1,false);form.tick();check(form.getAbilityInstance(ChangedAbilities.TOGGLE_NIGHT_VISION.get())!=null,"night vision ability registered");
    }
    private static void equip(LivingEntity wearer){AccessoryEntities.INSTANCE.forceReloadAccessories(wearer);var slots=AccessorySlots.getForEntity(wearer).orElseThrow();check(slots.quickMoveStack(new ItemStack(ChangedSynergyItems.EAGLE_OWL_AVIATOR_OUTFIT.get())),"equip native apparel "+wearer.getType());}
    private static void verify(Minecraft mc,LatexEagleOwl npc)throws Exception{
        EagleOwlFeedbackChecks.firstPerson(mc);
        var renderer=(LatexEagleOwlRenderer)mc.getEntityRenderDispatcher().getRenderer(npc);var model=renderer.getModel();check(mc.getResourceManager().getResource(renderer.getTextureLocation(npc)).isPresent(),"body texture exists");
        check(EagleOwlEyesLayer.style(EyeStyle.V2)==EyeStyle.V1&&EagleOwlEyesLayer.style(EyeStyle.TALL)==EyeStyle.TALL,"restricted eye styles");
        float earMotion=0;for(int i=0;i<90;i++){npc.tickCount++;npc.yHeadRot=i<30?i*5:135;model.prepareMobModel(npc,0,0,1);model.setupAnim(npc,0,0,npc.tickCount,135,20);var ear=model.getHead().getChild("Feathers").getChild("LeftEarTuftPivot");check(Float.isFinite(ear.xRot)&&Float.isFinite(ear.yRot),"finite ear animation");earMotion=java.lang.Math.max(earMotion,java.lang.Math.abs(ear.yRot));}check(earMotion>.01F,"ear tufts follow rapid head motion");check(java.lang.Math.abs(model.getHead().yRot-135*net.minecraft.util.Mth.DEG_TO_RAD)<.001F,"head reaches 135 degrees");
        var form=ProcessTransfur.getPlayerTransfurVariant(mc.player);var night=ChangedAbilities.TOGGLE_NIGHT_VISION.get();night.startUsing(net.ltxprogrammer.changed.ability.IAbstractChangedEntity.forPlayer(mc.player));check(form.visionType==VisionType.NORMAL,"native night vision switches off");night.startUsing(net.ltxprogrammer.changed.ability.IAbstractChangedEntity.forPlayer(mc.player));check(form.visionType==VisionType.NIGHT_VISION,"native night vision switches on");
        check(EagleOwlVisionClient.active(),"owl grayscale active without goggles");var chainField=EagleOwlVisionClient.class.getDeclaredField("chain");chainField.setAccessible(true);check(chainField.get(null)!=null,"real render loop constructed grayscale shader");
        var main=mc.getMainRenderTarget();main.setClearColor(.9F,.2F,.1F,1);main.clear(Minecraft.ON_OSX);((net.minecraft.client.renderer.PostChain)chainField.get(null)).process(1);main.bindWrite(true);
        try(var img=net.minecraft.client.Screenshot.takeScreenshot(main)){int pixel=img.getPixelRGBA(img.getWidth()/2,img.getHeight()/2);int red=pixel&255,green=pixel>>8&255,blue=pixel>>16&255;check(java.lang.Math.abs(red-green)<=2&&java.lang.Math.abs(red-blue)<=2&&red>0,"GPU produces grayscale pixels");}
        equip(mc.player);var slot=AccessorySlots.getForEntity(mc.player).orElseThrow();var stack=slot.getItems().filter(s->s.is(ChangedSynergyItems.EAGLE_OWL_AVIATOR_OUTFIT.get())).findFirst().orElseThrow();var outfit=ChangedSynergyItems.EAGLE_OWL_AVIATOR_OUTFIT.get();outfit.accessoryInteract(new AccessorySlotContext<>(mc.player,ChangedAccessorySlots.BODY.get(),stack));check(!EagleOwlVisionClient.active(),"lowered goggles restore color");outfit.accessoryInteract(new AccessorySlotContext<>(mc.player,ChangedAccessorySlots.BODY.get(),stack));check(EagleOwlVisionClient.active(),"raised goggles restore grayscale");
        for(var block:List.of(ChangedSynergyBlocks.EAGLE_OWL_PERCH_STAND.get(),ChangedSynergyBlocks.EAGLE_OWL_PERCH_RAIL.get()))for(var state:block.getStateDefinition().getPossibleStates())check(mc.getBlockRenderer().getBlockModel(state)!=mc.getModelManager().getMissingModel(),"all perch directions and halves baked");
        net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(),true,mc.level.registryAccess());
        for(var type:List.of(ChangedSynergyEntities.LATEX_SPOTTED_HYENA_MALE.get(),ChangedSynergyEntities.LATEX_SPOTTED_HYENA_FEMALE.get())){var hyena=type.create(mc.level);TrailclothClientChecks.equip(hyena);TrailclothClientChecks.verify(mc,hyena);}
        gallery(mc,npc,model);
        EagleOwlFeedbackChecks.verify(mc,npc,model);
        CanopyClientChecks.run(mc);
        FemaleStyleClientChecks.run(mc);
        EagleOwlNeckChecks.run(mc);
    }
    private static void gallery(Minecraft mc,LatexEagleOwl npc,LatexEagleOwlModel model)throws Exception{
        int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();var target=new TextureTarget(mc.getWindow().getWidth(),mc.getWindow().getHeight(),true,Minecraft.ON_OSX);var mv=RenderSystem.getModelViewStack();mv.pushPose();
        try{mv.setIdentity();mv.translate(0,0,-11000);RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,w,h,0,1000,21000),VertexSorting.ORTHOGRAPHIC_Z);target.setClearColor(.1F,.12F,.15F,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);var g=new GuiGraphics(mc,mc.renderBuffers().bufferSource());
            for(int row=0;row<2;row++)for(int col=0;col<3;col++){npc.getBasicPlayerInfo().setEyeStyle(row==0?EyeStyle.V1:EyeStyle.TALL);var slots=AccessorySlots.getForEntity(npc).orElseThrow();var stack=slots.getItems().filter(s->s.is(ChangedSynergyItems.EAGLE_OWL_AVIATOR_OUTFIT.get())).findFirst().orElseThrow();if(EagleOwlAviatorItem.lowered(stack)!=(row==1))ChangedSynergyItems.EAGLE_OWL_AVIATOR_OUTFIT.get().accessoryInteract(new AccessorySlotContext<>(npc,ChangedAccessorySlots.BODY.get(),stack));
                draw(g,npc,(col*2+1)*w/6,(row+1)*(h-40)/2,h/9,new float[]{180,270,0}[col]);g.drawCenteredString(mc.font,(row==0?"Goggles raised ":"Goggles lowered ")+new String[]{"front","side","back"}[col],(col*2+1)*w/6,(row+1)*(h-40)/2+10,0xFFFFFF);}
            g.flush();capture(mc,target,"owl-outfit-gallery.png");target.clear(Minecraft.ON_OSX);target.bindWrite(true);
            var types=List.of(ChangedEntities.WHITE_LATEX_WOLF_MALE.get(),ChangedEntities.LATEX_SNAKE.get(),ChangedEntities.LATEX_MERMAID_SHARK.get(),ChangedEntities.MILK_PUDDING.get(),ChangedEntities.DARK_LATEX_WOLF_PUP.get(),ChangedEntities.PURE_WHITE_LATEX_WOLF_PUP.get());
            for(int i=0;i<types.size();i++){var subject=(ChangedEntity)types.get(i).create(mc.level);equip(subject);draw(g,subject,(i%3*2+1)*w/6,(i/3+1)*(h-40)/2,h/10,180);g.drawCenteredString(mc.font,subject.getType().getDescription().getString(),(i%3*2+1)*w/6,(i/3+1)*(h-40)/2+10,0xFFFFFF);}g.flush();capture(mc,target,"owl-outfit-anatomies.png");
            target.clear(Minecraft.ON_OSX);target.bindWrite(true);var owlSlots=AccessorySlots.getForEntity(npc).orElseThrow();var savedOutfit=owlSlots.getItem(ChangedAccessorySlots.BODY.get()).orElse(ItemStack.EMPTY);owlSlots.setItem(ChangedAccessorySlots.BODY.get(),ItemStack.EMPTY);
            npc.getBasicPlayerInfo().setLeftIrisColor(net.ltxprogrammer.changed.util.Color3.fromInt(0x398CFF));npc.getBasicPlayerInfo().setRightIrisColor(net.ltxprogrammer.changed.util.Color3.fromInt(0xFFB030));
            for(int col=0;col<3;col++){npc.getBasicPlayerInfo().setEyeStyle(new EyeStyle[]{EyeStyle.V1,EyeStyle.TALL,EyeStyle.V2}[col]);draw(g,npc,(col*2+1)*w/6,h-35,h/3,180);g.drawCenteredString(mc.font,new String[]{"Original","Tall","Other style fallback"}[col],(col*2+1)*w/6,h-20,0xFFFFFF);}g.flush();capture(mc,target,"owl-eye-styles.png");owlSlots.setItem(ChangedAccessorySlots.BODY.get(),savedOutfit);
            target.clear(Minecraft.ON_OSX);target.bindWrite(true);BondedSuitService.ensureGrabAbility(npc);var grab=BondedSuitService.ability(npc);var prey=ChangedEntities.LATEX_HUMAN.get().create(mc.level);grab.grabbedEntity=prey;
            for(int col=0;col<3;col++){npc.setOnGround(col==0);for(int t=0;t<8;t++){npc.tickCount++;model.prepareMobModel(npc,0,0,1);model.setupAnim(npc,0,0,npc.tickCount,0,0);}check(model.aerialBlend(npc)==(col==0?0:1),"air-to-ground pose transition");var pose=new PoseStack();check(EagleOwlCarryClient.position(pose,npc,grab,1)==(col!=0),"talon offset used only airborne");check(Float.isFinite(pose.last().pose().m31()),"finite held target transform");draw(g,npc,(col*2+1)*w/6,h-45,h/7,col==2?270:180);g.drawCenteredString(mc.font,new String[]{"Ground hold","Air talon hold","Air side"}[col],(col*2+1)*w/6,h-25,0xFFFFFF);}
            grab.grabbedEntity=null;npc.setOnGround(true);for(int i=0;i<8;i++){npc.tickCount++;model.setupAnim(npc,0,0,npc.tickCount,0,0);}check(model.aerialBlend(npc)==0,"release clears flight pose");g.flush();capture(mc,target,"owl-talon-grab.png");
            target.clear(Minecraft.ON_OSX);target.bindWrite(true);float forward=0;
            for(int col=0;col<3;col++){
                npc.setOnGround(col==2);grab.grabbedEntity=col==0?null:prey;grab.getController().exchangeKeyState(col==0);
                for(int t=0;t<8;t++){npc.tickCount++;model.prepareMobModel(npc,0,0,1);model.setupAnim(npc,0,0,npc.tickCount,0,0);}
                if(col==0){forward=model.clawPosition().z;check(model.getLeg(HumanoidArm.LEFT).xRot<-.9F,"talons reach forward on activation");}
                if(col==1){check(model.getLeg(HumanoidArm.LEFT).xRot>.2F&&model.clawPosition().z>forward,"caught prey sweeps talons back beneath body");var p=new PoseStack();p.translate(-.0625,0,-.28125);p.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-11));EagleOwlCarryClient.position(p,npc,grab,1);var down=p.last().pose().transformDirection(new Vector3f(0,0,-1));check(down.y>.9F,"prey faces downward");}
                draw(g,npc,(col*2+1)*w/6,h-65,h/6,270);g.drawCenteredString(mc.font,new String[]{"Reach forward","Carry behind","Ground transition"}[col],(col*2+1)*w/6,h-30,0xFFFFFF);
            }grab.grabbedEntity=null;grab.getController().exchangeKeyState(false);g.flush();capture(mc,target,"owl-talon-stages.png");
            npc.setOnGround(false);grab.getController().exchangeKeyState(true);float previous=model.getLeg(HumanoidArm.LEFT).xRot;
            float sampleAge=npc.tickCount;for(int frame=0;frame<84;frame++){
                if(frame==28)grab.grabbedEntity=prey;if(frame==56){grab.grabbedEntity=null;grab.getController().exchangeKeyState(false);}
                sampleAge+=.25F;model.prepareMobModel(npc,0,0,.25F);model.setupAnim(npc,0,0,sampleAge,0,0);float pitch=model.getLeg(HumanoidArm.LEFT).xRot;
                check(java.lang.Math.abs(pitch-previous)<.32F,"continuous reach, caught and cancelled leg poses");previous=pitch;
            }check(model.aerialBlend(npc)==0,"cancelled acquisition returns completely to idle");
            target.clear(Minecraft.ON_OSX);target.bindWrite(true);TrailclothClientChecks.gallery(mc,g,w,h);g.flush();capture(mc,target,"trailcloth-adaptations.png");
        }finally{mv.popPose();RenderSystem.applyModelViewMatrix();target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);}
    }
    private static void draw(GuiGraphics g,LivingEntity entity,int x,int y,int size,float yaw){entity.yBodyRot=entity.yBodyRotO=entity.yHeadRot=entity.yHeadRotO=yaw;entity.setYRot(yaw);InventoryScreen.renderEntityInInventory(g,x,y,size,new Quaternionf().rotationZ((float)java.lang.Math.PI),new Quaternionf(),entity);}
    private static void capture(Minecraft mc,TextureTarget target,String name)throws Exception{try(var image=net.minecraft.client.Screenshot.takeScreenshot(target)){image.writeToFile(mc.gameDirectory.toPath().resolve(name));}}
    private static void check(boolean value,String text){if(!value)throw new AssertionError(text);checks++;}
}
