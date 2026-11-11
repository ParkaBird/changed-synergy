package net.parkabird.changedsynergy.regression;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.ltxprogrammer.changed.init.ChangedAbilities;
import net.ltxprogrammer.changed.init.ChangedItems;
import net.ltxprogrammer.changed.item.Syringe;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.client.LatexSpottedHyenaRenderer;
import net.parkabird.changedsynergy.client.archive.ArchiveOriginModels;
import net.parkabird.changedsynergy.client.model.SpottedHyenaEarChecks;
import net.parkabird.changedsynergy.entity.LatexSpottedHyena;
import net.parkabird.changedsynergy.init.*;
import net.parkabird.changedsynergy.origin.NativeOriginCatalog;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

@Mod.EventBusSubscriber(modid="changed_synergy", value=Dist.CLIENT)
public final class SpottedHyenaClientRegression {
    private static int stage, sex, wait, npcId, checks;
    private static CompletableFuture<Void> task;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == 9) return;
        var mc = Minecraft.getInstance();
        try {
            org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
            if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                check(mc.gameDirectory.getName().startsWith("author-feedback-client-"), "private test world");
                mc.options.pauseOnLostFocus=false; mc.options.renderDistance().set(2); mc.options.simulationDistance().set(2); mc.options.guiScale().set(2);
                var rules = new GameRules(); rules.getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(false,null); rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
                mc.createWorldOpenFlows().createFreshLevel("hyena", new LevelSettings("hyena",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),
                        new WorldOptions(73451,false,false),r -> r.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions()); stage=1;
            }
            if (mc.level == null || mc.player == null || mc.player.tickCount < 40 || mc.getOverlay()!=null) return;
            if (stage == 1) {
                task=CompletableFuture.runAsync(() -> prepare(mc), mc.getSingleplayerServer()); stage=2; wait=0;
            }
            if (stage == 2 && task.isDone()) {
                task.join(); var form=ProcessTransfur.getPlayerTransfurVariant(mc.player);
                var expected=sex==0 ? ChangedSynergyTransfurVariants.LATEX_SPOTTED_HYENA_MALE.get() : ChangedSynergyTransfurVariants.LATEX_SPOTTED_HYENA_FEMALE.get();
                if (form==null || form.getParent()!=expected || !(mc.level.getEntity(npcId) instanceof LatexSpottedHyena)
                        || !TrailclothClientChecks.equipped(mc.player) || !TrailclothClientChecks.equipped((net.minecraft.world.entity.LivingEntity)mc.level.getEntity(npcId))) {
                    if (++wait>300) throw new AssertionError("client/server hyena form synchronization"); return;
                }
                mc.setScreen(null); verify(mc,(LatexSpottedHyena)mc.level.getEntity(npcId));
                if (sex++==0) stage=1;
                else stage=3;
            }
            if(stage==3 && CuriosClothingClientChecks.tick(mc)) {
                stage=9; System.out.println("SPOTTED_HYENA_CLIENT_PASS: "+checks+" male/female NPC/player, infuser, creative items, models, armour, archive and ear-animation checks");
                    System.out.println("MUNTJAC_CLIENT_REGRESSION_PASS: hyena client helper completed"); mc.stop(); }
        } catch(Throwable failure) { stage=9; System.err.println("SPOTTED_HYENA_CLIENT_FAIL"); failure.printStackTrace();
            System.err.println("MUNTJAC_CLIENT_REGRESSION_FAIL"); mc.stop(); }
    }
    private static void prepare(Minecraft mc) {
        var owner=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()); var level=owner.serverLevel();
        var type=sex==0 ? ChangedSynergyEntities.LATEX_SPOTTED_HYENA_MALE.get() : ChangedSynergyEntities.LATEX_SPOTTED_HYENA_FEMALE.get();
        var form=sex==0 ? ChangedSynergyTransfurVariants.LATEX_SPOTTED_HYENA_MALE.get() : ChangedSynergyTransfurVariants.LATEX_SPOTTED_HYENA_FEMALE.get();
        var npc=type.create(level); npc.setNoAi(true); npc.moveTo(owner.getX()+2,owner.getY(),owner.getZ()); level.addFreshEntity(npc); npcId=npc.getId();
        var instance=ProcessTransfur.setPlayerTransfurVariant(owner,form,null,1,false);
        instance.tick();
        check(owner.getMaxHealth()==(sex==0?24:26),"transformed player gendered health");
        check(Math.abs(owner.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
                -.1*(sex==0?1.075:1.13))<.00001,"transformed player gendered movement");
        check(Math.abs(owner.getAttributeValue(net.ltxprogrammer.changed.init.ChangedAttributes.JUMP_STRENGTH.get())
                -(sex==0?1:1.05))<.00001,"transformed player gendered jump: sex="+sex+", actual="
                +owner.getAttributeValue(net.ltxprogrammer.changed.init.ChangedAttributes.JUMP_STRENGTH.get()));
        instance.unhookAll(owner);
        check(Math.abs(owner.getAttributeValue(net.ltxprogrammer.changed.init.ChangedAttributes.JUMP_STRENGTH.get())-1)<.00001,
                "human jump restored after leaving hyena form");
        instance.refreshAttributes();instance.tick();
        TrailclothClientChecks.equip(npc); TrailclothClientChecks.equip(owner);
        check(instance.getAbilityInstance(ChangedAbilities.GRAB_ENTITY_ABILITY.get())!=null, "native humanoid grab ability");
        check(instance.getAbilityInstance(ChangedAbilities.SWITCH_GENDER.get())==null, "gender selected by synthesizer only");
        var menu=new net.ltxprogrammer.changed.world.inventory.InfuserMenu(155,owner.getInventory(),null);
        var ingredients=List.of(ChangedItems.LATEX_BASE.get(),ChangedItems.WHITE_LATEX_GOO.get(),Items.ACACIA_LEAVES,Items.BONE,Items.RABBIT_HIDE);
        for(int i=0;i<ingredients.size();i++) menu.get().get(i+1).set(new ItemStack(ingredients.get(i)));
        for(var base:net.ltxprogrammer.changed.recipe.InfuserRecipe.getAllowedInputs()) {
            menu.get().get(10).set(base.copy());
            for(boolean female:new boolean[]{false,true,false}) {
                menu.guiState.put("changed:male_female_switch",female);menu.slotsChanged(null);menu.stillValid(owner);
                var expected=female?ChangedSynergyTransfurVariants.LATEX_SPOTTED_HYENA_FEMALE.get():ChangedSynergyTransfurVariants.LATEX_SPOTTED_HYENA_MALE.get();
                check(Syringe.getVariant(menu.getResultSlot().getItem())==expected,"real infuser selector produces matching gender");
            }
        }
    }
    private static void verify(Minecraft mc,LatexSpottedHyena npc)throws Exception {
        var form=sex==0 ? ChangedSynergyTransfurVariants.LATEX_SPOTTED_HYENA_MALE.get() : ChangedSynergyTransfurVariants.LATEX_SPOTTED_HYENA_FEMALE.get();
        var renderer=(LatexSpottedHyenaRenderer)mc.getEntityRenderDispatcher().getRenderer(npc); var model=renderer.getModel();
        check(mc.getResourceManager().getResource(renderer.getTextureLocation(npc)).isPresent(),"texture resolved");
        check(!model.getHead().hasChild("BBPreviewEyes_NOT_EXPORTED"),"editor-only eyes omitted");
        check(model.getHead().getChild("LeftEar").hasChild("LeftEarPivot"),"authored ear pivot retained");
        check(model.getAnimator(npc).getAnimators(net.ltxprogrammer.changed.client.renderer.animate.HumanoidAnimator.AnimateStage.FINAL)
                .anyMatch(a -> a instanceof net.parkabird.changedsynergy.client.model.MuntjacEarsAnimator),"shared follow-through connected to species animation");
        float bind=model.getHead().getChild("LeftEar").getChild("LeftEarPivot").zRot;
        float maxYaw=0;
        for(int i=0;i<120;i++) {
            npc.tickCount++;npc.yHeadRot=i<30?i*6:180;
            model.prepareMobModel(npc,i*.1F,.4F,1);model.setupAnim(npc,i*.1F,.4F,npc.tickCount,30,10);
            var ear=model.getHead().getChild("LeftEar");
            check(Float.isFinite(ear.xRot)&&Float.isFinite(ear.yRot),"finite animation"); maxYaw=Math.max(maxYaw,Math.abs(ear.yRot));
            check(ear.getChild("LeftEarPivot").zRot==bind,"authored ear angle not overwritten");
        }
        check(maxYaw>.01F,"quick head turns visibly move ears");
        SpottedHyenaEarChecks.verify();
        if(sex==1)SpottedHyenaEarChecks.verifyEyes(model);
        var instance=ProcessTransfur.getPlayerTransfurVariant(mc.player);
        check(net.parkabird.changedsynergy.entity.SpottedHyenaDiet.canEatBone(mc.player), "client recognizes bone-eating hyena body");
        check(new ItemStack(Items.BONE).getUseAnimation()==net.minecraft.world.item.UseAnim.EAT
                &&new ItemStack(Items.BONE).getUseDuration()==32,"client bone chewing animation and duration");
        var humanRenderer=(net.minecraft.client.renderer.entity.player.PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(mc.player);
        var humanModel=humanRenderer.getModel();var buffer=mc.renderBuffers().bufferSource();
        float old=instance.transfurProgression,oldO=instance.transfurProgressionO;
        try {
            for(float progress:new float[]{.1F,.45F,.55F,.65F,.75F,.9F,1}) {
                instance.transfurProgressionO=instance.transfurProgression=progress;
                for(boolean right:new boolean[]{true,false}) {
                    check(net.ltxprogrammer.changed.client.FormRenderHandler.maybeRenderHand(humanRenderer,new com.mojang.blaze3d.vertex.PoseStack(),buffer,15728880,mc.player,
                            right?humanModel.rightArm:humanModel.leftArm,right?humanModel.rightSleeve:humanModel.leftSleeve),"native first-person arm renders through transfur transition");
                    buffer.endBatch();
                }
            }
        }finally{instance.transfurProgression=old;instance.transfurProgressionO=oldO;}
        net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(),true,mc.level.registryAccess());
        TrailclothClientChecks.verify(mc, npc);
        var ordered=new java.util.ArrayList<>(net.ltxprogrammer.changed.init.ChangedTabs.TAB_CHANGED_ITEMS.get().getDisplayItems());
        for(var item:List.of(ChangedItems.LATEX_SYRINGE.get(),ChangedItems.LATEX_FLASK.get(),ChangedItems.LATEX_TIPPED_ARROW.get())) {
            int found=-1;for(int i=0;i<ordered.size();i++)if(ordered.get(i).is(item)&&Syringe.getVariant(ordered.get(i))==form)found=i;
            check(found>0&&ordered.get(found-1).is(item),"correct creative group");
            var stack=ordered.get(found);check(!stack.getHoverName().getString().contains("entity.changed_synergy"),"translated item name");
            check(mc.getItemRenderer().getModel(stack,mc.level,mc.player,0)!=mc.getModelManager().getMissingModel(),"item model baked");
            check((mc.getItemColors().getColor(stack,0)&0xFFFFFF)==0xB18B58 && (mc.getItemColors().getColor(stack,1)&0xFFFFFF)==0x3D302A,"hyena item colors");
        }
        int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
        var target=new TextureTarget(mc.getWindow().getWidth(),mc.getWindow().getHeight(),true,Minecraft.ON_OSX);var mv=RenderSystem.getModelViewStack();mv.pushPose();
        try {
            mv.setIdentity();mv.translate(0,0,-11000);RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,w,h,0,1000,21000),VertexSorting.ORTHOGRAPHIC_Z);
            target.setClearColor(.1F,.12F,.15F,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);var g=new GuiGraphics(mc,mc.renderBuffers().bufferSource());
            for(int row=0;row<2;row++)for(int col=0;col<3;col++) {
                var subject=row==0?npc:mc.player;float yaw=new float[]{180,270,0}[col];subject.yBodyRot=subject.yBodyRotO=subject.yHeadRot=subject.yHeadRotO=yaw;subject.setYRot(yaw);
                int x=(col*2+1)*w/6,y=(row+1)*(h-60)/2-5;
                InventoryScreen.renderEntityInInventory(g,x,y,h/9,new Quaternionf().rotationZ((float)Math.PI),new Quaternionf(),subject);
                g.drawCenteredString(mc.font,(row==0?"NPC ":"Player ")+new String[]{"front","side","back"}[col],x,y+8,0xFFFFFF);
            }
            int x=w/2-48;for(var item:List.of(ChangedItems.LATEX_SYRINGE.get(),ChangedItems.LATEX_FLASK.get(),ChangedItems.LATEX_TIPPED_ARROW.get())){g.renderItem(Syringe.setPureVariant(new ItemStack(item),form.getFormId()),x,h-28);x+=32;}
            g.flush();capture(mc,target,"hyena-"+sex+"-gallery.png");
            if(sex==1) {
                target.clear(Minecraft.ON_OSX);target.bindWrite(true);
                TrailclothClientChecks.gallery(mc,g,w,h);g.flush();capture(mc,target,"hyena-trailcloth-adaptations.png");
                target.clear(Minecraft.ON_OSX);target.bindWrite(true);
                TrailclothClientChecks.humans(mc,g,w,h);g.flush();capture(mc,target,"hyena-trailcloth-humans.png");
            }
            if(sex==1) {
                target.clear(Minecraft.ON_OSX);target.bindWrite(true);
                for(int col=0;col<3;col++) {
                    float pitch=new float[]{0,40,70}[col];npc.setXRot(pitch);npc.xRotO=pitch;
                    npc.yBodyRot=npc.yBodyRotO=npc.yHeadRot=npc.yHeadRotO=180;npc.setYRot(180);
                    InventoryScreen.renderEntityInInventory(g,(col*2+1)*w/6,h-45,h/5,new Quaternionf().rotationZ((float)Math.PI),new Quaternionf(),npc);
                    g.drawCenteredString(mc.font,"Head pitch "+(int)pitch,(col*2+1)*w/6,h-25,0xFFFFFF);
                }
                g.flush();capture(mc,target,"hyena-female-eye-pitches.png");npc.setXRot(0);npc.xRotO=0;
            }
            target.clear(Minecraft.ON_OSX);target.bindWrite(true);
            new ArchiveOriginModels().renderPortrait(g,NativeOriginCatalog.find(mc.level.registryAccess(),form.getFormId()),w/2-120,h/2-115,240,230);
            g.flush();capture(mc,target,"hyena-"+sex+"-portrait.png");
            npc.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.IRON_CHESTPLATE));npc.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));
            target.clear(Minecraft.ON_OSX);target.bindWrite(true);InventoryScreen.renderEntityInInventory(g,w/2,h-30,h/4,new Quaternionf().rotationZ((float)Math.PI).rotateY(.35F),new Quaternionf(),npc);
            g.flush();capture(mc,target,"hyena-"+sex+"-armour.png");
        } finally {mv.popPose();RenderSystem.applyModelViewMatrix();target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);}
    }
    private static void capture(Minecraft mc,TextureTarget target,String name)throws Exception {try(var img=net.minecraft.client.Screenshot.takeScreenshot(target)){img.writeToFile(mc.gameDirectory.toPath().resolve(name));}}
    private static void check(boolean value,String text){if(!value)throw new AssertionError(text);checks++;}
}
