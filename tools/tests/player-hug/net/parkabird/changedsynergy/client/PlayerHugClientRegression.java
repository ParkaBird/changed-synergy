package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.concurrent.CompletableFuture;
import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.client.gui.AbilityRadialScreen;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedAbilities;
import net.ltxprogrammer.changed.init.ChangedRegistry;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.network.*;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import org.joml.Matrix4f;

/** Native multiplayer packets, real wheel events and held-entity rendering in a private world. */
@Mod.EventBusSubscriber(modid="changed_synergy",value=Dist.CLIENT)
public final class PlayerHugClientRegression {
    private static final String[] TARGETS={"changed:dark_latex_wolf_pup","changed:pure_white_latex_wolf_pup","changed:gas_wolf_pup","changed:dark_latex_wolf_pup","changed:pure_white_latex_wolf_pup","changed:gas_wolf_pup","changed:milk_pudding","changed:latex_shark_feral","changed:headless_knight","changed:dark_latex_wolf_male","changed:pure_white_latex_cerberus","changed_synergy:latex_silk_moth","changed_addon:latex_snep"};
    private static int stage,wait,index,checks,targetId;
    private static boolean instinct;
    private static boolean posed;
    private static boolean toggleVerified;
    private static long started=System.nanoTime();
    private static CompletableFuture<Void> task;
    private static AbilityRadialScreen wheel;
    private static final java.util.List<String> visualProblems=new java.util.ArrayList<>();
    private static ResourceLocation id(String name){return ResourceLocation.parse(name);}
    private static void check(boolean value,String description){if(!value)throw new AssertionError(description);checks++;}
    private static net.minecraft.server.level.ServerPlayer owner(Minecraft mc){return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());}
    private static CompletableFuture<Void> server(Minecraft mc,Runnable action){return CompletableFuture.runAsync(action,mc.getSingleplayerServer());}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||stage==99)return;
        var mc=Minecraft.getInstance();
        try{
            org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
            if(task!=null&&task.isCompletedExceptionally())task.join();
            if(System.nanoTime()-started>240_000_000_000L)throw new AssertionError("timeout at stage "+stage);
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                check(mc.gameDirectory.getName().startsWith("author-feedback-client-"),"private fixture only");
                mc.options.pauseOnLostFocus=false;
                var rules=new GameRules();rules.getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(false,null);rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
                mc.createWorldOpenFlows().createFreshLevel("hugs",new LevelSettings("hugs",GameType.SURVIVAL,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(555199,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions());stage=1;
            }
            if(mc.player==null||mc.level==null||mc.getOverlay()!=null)return;
            if(mc.screen instanceof NativeOriginScreen screen){screen.keyPressed(256,0,0);return;}
            if(stage==1&&mc.player.tickCount>25&&mc.screen==null){
                task=server(mc,()->{
                    var p=owner(mc);p.setHealth(p.getMaxHealth());p.setGameMode(GameType.SURVIVAL);
                    ProcessTransfur.setPlayerTransfurVariant(p,ChangedRegistry.TRANSFUR_VARIANT.get().getValue(id("changed_synergy:form_latex_muntjac/male")),null,1,false);
                });stage=2;wait=0;
            }
            if(stage==2&&task.isDone()&&++wait>12&&ProcessTransfur.isPlayerTransfurred(mc.player)){
                task.join();wheel=new AbilityRadialScreen(new net.ltxprogrammer.changed.world.inventory.AbilityRadialMenu(121,mc.player.getInventory()),mc.player.getInventory(),net.minecraft.network.chat.Component.empty());mc.setScreen(wheel);stage=3;wait=0;
            }
            if(stage==3&&++wait>25){
                int slot=wheel.abilities.indexOf(ChangedAbilities.GRAB_ENTITY_ABILITY.get());check(slot>=0,"original grab option exists");
                int[] point=point(wheel,slot);var eventClick=new ScreenEvent.MouseButtonPressed.Pre(wheel,point[0],point[1],1);
                PlayerHugClient.rightClick(eventClick);check(eventClick.isCanceled(),"right click is consumed exactly once");stage=4;wait=0;
            }
            if(stage==4&&PlayerHugService.mode(clientGrab(mc))){
                if(!toggleVerified){
                    if(++wait<12)return;
                    ChangedSynergyNetwork.CHANNEL.sendToServer(new PlayerHugActionPacket(PlayerHugActionPacket.TOGGLE,-1));
                    stage=11;wait=0;return;
                }
                check(wheel.tooltipsFor(wheel.abilities.indexOf(ChangedAbilities.GRAB_ENTITY_ABILITY.get())).stream().anyMatch(c->c.getString().contains("Friendly")),"localized friendly-mode tooltip");
                FriendlyHoldRenderRegression.run(mc);
                if(Boolean.getBoolean("changed_synergy.friendlyRenderAuditOnly")){finish(mc);return;}
                checkSmoothSpecies(mc);captureWheel(mc);mc.setScreen(null);task=server(mc,()->spawn(mc));stage=5;wait=0;
            }
            if(stage==11&&!PlayerHugService.mode(clientGrab(mc))&&++wait>12){
                var grab=clientGrab(mc);
                if(net.parkabird.changedsynergy.compat.ChangedAddonCompat.isLoaded()){
                    check(Boolean.FALSE.equals(grab.getClass().getMethod("allowGrabTransfurred").invoke(grab)),"client ordinary mode revokes transformed-target permission");
                    check(Boolean.FALSE.equals(grab.getClass().getMethod("isSafeMode").invoke(grab)),"client ordinary mode clears Addon safe mode");
                }
                var target=ForgeRegistries.ENTITY_TYPES.getValue(id("changed:pure_white_latex_cerberus")).create(mc.level);
                var previousHit=mc.hitResult;
                try{mc.hitResult=new net.minecraft.world.phys.EntityHitResult(target);check(grab.getHoveredEntity(grab.entity)==null,"ordinary target selection rejects latex after toggling friendly mode off");}
                finally{mc.hitResult=previousHit;target.discard();}
                ChangedSynergyNetwork.CHANNEL.sendToServer(new PlayerHugActionPacket(PlayerHugActionPacket.TOGGLE,-1));
                stage=12;
            }
            if(stage==12&&PlayerHugService.mode(clientGrab(mc))){toggleVerified=true;stage=4;}
            if(stage==5&&task.isDone()&&++wait>12&&mc.level.getEntity(targetId) instanceof net.minecraft.world.entity.LivingEntity target){
                task.join();ChangedSynergyNetwork.CHANNEL.sendToServer(new PlayerHugActionPacket(PlayerHugActionPacket.GRAB,targetId));stage=6;wait=0;posed=false;
            }
            if(stage==6&&++wait>20){
                if(!posed){task=server(mc,()->{var grab=PlayerHugService.ability(owner(mc));((PlayerHugAccess)grab).changedSynergy$setUprightCarry(index>=3);PlayerHugService.sync(grab);});posed=true;wait=10;return;}
                task.join();
                var grab=clientGrab(mc);check(grab.grabbedEntity!=null&&grab.grabbedEntity.getId()==targetId&&PlayerHugService.active(grab),"server grab and friendly state reached client: "+TARGETS[index]);
                var authoritative=PlayerHugService.clientState(grab);
                var priorTarget=grab.grabbedEntity;float priorStrength=grab.grabStrength;
                ((PlayerHugAccess)grab).changedSynergy$setPlayerHug(false);
                PlayerHugService.receive(authoritative);
                check(PlayerHugService.active(grab)&&grab.grabbedEntity==priorTarget&&grab.grabStrength==priorStrength,
                        "ARMS-before-friendly packet promotes the existing hold without resetting it");
                check(((PlayerHugAccess)grab).changedSynergy$isUprightCarry()==(index>=3),"carry style is synchronized and stable");
                check(!grab.suited&&!grab.grabbedHasControl,"client remains harmless arm hold");
                if(TARGETS[index].contains("_pup"))check(PlayerHugPoses.shape(grab.grabbedEntity)==PlayerHugPoses.Shape.PUP,"pup carry profile");
                if(TARGETS[index].contains("latex_snep"))check(PlayerHugPoses.shape(grab.grabbedEntity)==PlayerHugPoses.Shape.SMALL_FERAL,"feral carry profile");
                capturePose(mc,TARGETS[index].substring(TARGETS[index].indexOf(':')+1)+(TARGETS[index].contains("_pup")?(index<3?"-cradle":"-upright"):""));
                checkShoulders(mc);
                if(TARGETS[index].contains("_pup"))checkPaws(mc,grab.grabbedEntity);
                if(index==0){
                    net.ltxprogrammer.changed.init.ChangedKeyMappings.USE_ABILITY.setDown(true);
                    grab.getController().exchangeKeyState(true);grab.getController().activateAbility();
                    var ticks=grab.getController().getClass().getDeclaredField("holdTicks");ticks.setAccessible(true);ticks.setInt(grab.getController(),40);
                    grab.tick();
                }
                else if(index<6)task=server(mc,()->PlayerHugService.ability(owner(mc)).grabStrength=.001F);
                else ChangedSynergyNetwork.CHANNEL.sendToServer(new PlayerHugActionPacket(PlayerHugActionPacket.RELEASE,-1));
                stage=7;wait=0;
            }
            if(stage==7&&++wait>8){
                var grab=clientGrab(mc);check(grab.grabbedEntity==null&&!PlayerHugService.active(grab),"release reaches client without stale pose");
                for(int retry=0;retry<20;retry++)PlayerHugService.updateClient();
                check(grab.grabbedEntity==null,"cached state cannot recreate a released visual hold");
                task=server(mc,()->{
                    var serverGrab=PlayerHugService.ability(owner(mc));
                    check(serverGrab.grabbedEntity==null,"server released the same hold");
                    var old=owner(mc).serverLevel().getEntity(targetId);
                    if(old!=null){
                        check(!old.noPhysics&&((net.ltxprogrammer.changed.entity.LivingEntityDataExtension)old).getGrabbedBy()==null,"released creature has normal physics and no stale holder");
                        if(index<6)check(old.position().distanceToSqr(serverGrab.entity.getEntity().position())<4,"natural release does not teleport the creature away");
                        if(index!=0)old.discard();
                    }
                });stage=8;
            }
            if(stage==8&&task.isDone()){
                if(index==0){
                    net.ltxprogrammer.changed.init.ChangedKeyMappings.USE_ABILITY.setDown(true);
                    var old=mc.level.getEntity(targetId);if(old!=null)mc.hitResult=new net.minecraft.world.phys.EntityHitResult(old);
                    var still=clientGrab(mc);still.getUseType().check(true,true,true,still.getController());
                    check(still.grabbedEntity==null,"held release key never starts a fresh grab");
                }
                if(++wait<65)return;
                if(index==0){
                    check(clientGrab(mc).grabbedEntity==null,"target remains free after the recapture cooldown expires");
                    net.ltxprogrammer.changed.init.ChangedKeyMappings.USE_ABILITY.setDown(false);
                    task=server(mc,()->{check(PlayerHugService.ability(owner(mc)).grabbedEntity==null,"server rejects queued regrab requests after manual release");var old=owner(mc).serverLevel().getEntity(targetId);if(old!=null)old.discard();});
                }
                task.join();index++;if(index>=TARGETS.length||!ForgeRegistries.ENTITY_TYPES.containsKey(id(TARGETS[index]))){
                    if(!instinct&&net.parkabird.changedsynergy.compat.TrueTransfurCompat.supportsSeparateInstinct()){
                        instinct=true;task=server(mc,()->enterInstinct(mc));stage=9;wait=0;return;
                    }
                    finish(mc);return;
                }
                task=server(mc,()->spawn(mc));stage=5;wait=0;
            }
            if(stage==9&&task.isDone()&&++wait>15){
                task.join();ChangedSynergyNetwork.CHANNEL.sendToServer(new PlayerHugActionPacket(PlayerHugActionPacket.SYNC,-1));stage=10;wait=0;
            }
            if(stage==10&&++wait>10){
                var state=PlayerHugService.clientState(ProcessTransfur.getPlayerTransfurVariant(mc.player).getAbilityInstance(ChangedAbilities.GRAB_ENTITY_ABILITY.get()));
                check(state!=null&&state.carrierId()!=mc.player.getId(),"independent TT body state reaches its owner");
                var body=(ChangedEntity)mc.level.getEntity(state.carrierId());check(body!=null&&body.getAbilityInstance(ChangedAbilities.GRAB_ENTITY_ABILITY.get())!=null,"only the existing player capability is mirrored on TT body");
                index=0;task=server(mc,()->spawn(mc));stage=5;wait=0;
            }
        }catch(Throwable failure){stage=99;System.err.println("PLAYER_HUG_CLIENT_FAIL");failure.printStackTrace();mc.stop();}
    }
    private static void checkPaws(Minecraft mc,net.minecraft.world.entity.LivingEntity target){
        var renderer=(net.minecraft.client.renderer.entity.LivingEntityRenderer<?,?>)mc.getEntityRenderDispatcher().getRenderer(target);
        var model=(net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel<?>)renderer.getModel();
        var head=boxes(model.getHead());
        var torso=boxes(model.getTorso(),"/Torso_r1");
        check(head.stream().anyMatch(h->torso.stream().anyMatch(b->b.inflate(.01).intersects(h))),"pup neck remains attached to torso: head="+head+" torso="+torso);
        var tail=boxes(model.getTorso(),"/Tail/TailPrimary/Base_r1");
        check(tail.stream().anyMatch(t->torso.stream().anyMatch(b->b.inflate(.03).intersects(t))),"pup tail root remains attached to hips");
        for(var side:net.minecraft.world.entity.HumanoidArm.values()){
            var paw=model.getArm(side);var pawBoxes=boxes(paw);
            boolean clear=pawBoxes.stream().noneMatch(p->head.stream().anyMatch(face->p.deflate(.001).intersects(face.deflate(.001))));
            if(!clear){
                visualProblems.add("pup "+side);
                System.out.println("PUP_GEOMETRY_OVERLAP "+side+" head="+head+" paw="+pawBoxes+" headPivot="+model.getHead().x+","+model.getHead().y+","+model.getHead().z+" pawPivot="+paw.x+","+paw.y+","+paw.z);
            }else check(true,"upright pup forepaw geometry clears every head/muzzle cube");
        }
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static void checkShoulders(Minecraft mc){
        var source=clientGrab(mc).entity.getEntity();
        var body=source instanceof ChangedEntity c?c:ProcessTransfur.getPlayerTransfurVariant(mc.player).getChangedEntity();
        var renderer=(net.minecraft.client.renderer.entity.LivingEntityRenderer<?,?>)mc.getEntityRenderDispatcher().getRenderer(body);
        var model=(net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel<?>)renderer.getModel();
        for(var side:net.minecraft.world.entity.HumanoidArm.values()){
            var arm=model.getArm(side);check(Math.abs(arm.z-model.getTorso().z)<.01,"friendly shoulders stay at the current torso socket: "+arm.z+" / "+model.getTorso().z);
        }
        boolean lowCarry=TARGETS[index].equals("changed:milk_pudding")||TARGETS[index].equals("changed:headless_knight")||index<3;
        float heldRoll=model.getHead().zRot;
        if(lowCarry){
            var access=(PlayerHugAccess)clientGrab(mc);
            access.changedSynergy$setPlayerHug(false);
            try{
                ((net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel)model).setupAnim(body,0,0,body.tickCount+mc.getFrameTime(),0,0);
                check(Math.abs(heldRoll-model.getHead().zRot)<.03,"low carry retains native head motion: "+TARGETS[index]+" "+heldRoll+" / "+model.getHead().zRot);
            }finally{
                access.changedSynergy$setPlayerHug(true);
                ((net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel)model).setupAnim(body,0,0,body.tickCount+mc.getFrameTime(),0,0);
            }
        }else check(Math.abs(heldRoll-.25F)<.01,"face-covering carry tilts at the neck: "+TARGETS[index]);
    }
    private static java.util.List<net.minecraft.world.phys.AABB> boxes(net.minecraft.client.model.geom.ModelPart part){
        return boxes(part,"");
    }
    private static java.util.List<net.minecraft.world.phys.AABB> boxes(net.minecraft.client.model.geom.ModelPart part,String prefix){
        var result=new java.util.ArrayList<net.minecraft.world.phys.AABB>();
        part.visit(new com.mojang.blaze3d.vertex.PoseStack(),(pose,path,index,cube)->{
            if(!path.startsWith(prefix))return;
            double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
            for(int i=0;i<8;i++){
                var p=new org.joml.Vector3f((i&1)==0?cube.minX/16:cube.maxX/16,(i&2)==0?cube.minY/16:cube.maxY/16,(i&4)==0?cube.minZ/16:cube.maxZ/16).mulPosition(pose.pose());
                minX=Math.min(minX,p.x);minY=Math.min(minY,p.y);minZ=Math.min(minZ,p.z);maxX=Math.max(maxX,p.x);maxY=Math.max(maxY,p.y);maxZ=Math.max(maxZ,p.z);
            }
            result.add(new net.minecraft.world.phys.AABB(minX,minY,minZ,maxX,maxY,maxZ));
        });return result;
    }
    private static GrabEntityAbilityInstance clientGrab(Minecraft mc){
        var grab=ProcessTransfur.getPlayerTransfurVariant(mc.player).getAbilityInstance(ChangedAbilities.GRAB_ENTITY_ABILITY.get());
        var state=PlayerHugService.clientState(grab);
        if(state!=null&&state.carrierId()!=mc.player.getId()&&mc.level.getEntity(state.carrierId()) instanceof ChangedEntity body)return body.getAbilityInstance(ChangedAbilities.GRAB_ENTITY_ABILITY.get());
        return grab;
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void checkSmoothSpecies(Minecraft mc){
        for(var name:new String[]{"latex_swordfish","latex_muntjac","latex_muntjac_female"}){
            var entity=(ChangedEntity)ForgeRegistries.ENTITY_TYPES.getValue(id("changed_synergy:"+name)).create(mc.level);
            var renderer=(net.minecraft.client.renderer.entity.LivingEntityRenderer)mc.getEntityRenderDispatcher().getRenderer(entity);
            var model=(net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel)renderer.getModel();
            var animator=model.getAnimator(entity);
            var step=(net.parkabird.changedsynergy.client.model.SmoothGrabAnimator)animator
                    .getAnimators(net.ltxprogrammer.changed.client.renderer.animate.HumanoidAnimator.AnimateStage.FINAL)
                    .filter(a->a instanceof net.parkabird.changedsynergy.client.model.SmoothGrabAnimator).findFirst().orElseThrow();
            var left=model.getArm(net.minecraft.world.entity.HumanoidArm.LEFT);
            var right=model.getArm(net.minecraft.world.entity.HumanoidArm.RIGHT);
            model.getHead().resetPose();model.getTorso().resetPose();left.resetPose();right.resetPose();
            float base=right.xRot;
            animator.grabState=net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel.GrabState.REACH;
            step.setupAnim(entity,0,0,0,0,0);step.setupAnim(entity,0,0,1,0,0);float entering=right.xRot;
            left.resetPose();right.resetPose();step.setupAnim(entity,0,0,4,0,0);float held=right.xRot;
            check(Math.abs(entering-base)>0&&Math.abs(entering-base)<Math.abs(held-base),"smooth arm raise: "+name);
            animator.grabState=net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel.GrabState.EMPTY;
            left.resetPose();right.resetPose();step.setupAnim(entity,0,0,5,0,0);float leaving=right.xRot;
            check(Math.abs(leaving-base)>0&&Math.abs(leaving-base)<Math.abs(held-base),"smooth arm lowering: "+name);
            left.resetPose();right.resetPose();step.setupAnim(entity,0,0,8,0,0);
            check(right.xRot==base,"arm returns to native animation: "+name);
            entity.discard();
        }
    }
    private static void spawn(Minecraft mc){
        var p=owner(mc);var actor=net.parkabird.changedsynergy.compat.TrueTransfurCompat.interactionBody(p);var npc=(ChangedEntity)ForgeRegistries.ENTITY_TYPES.getValue(id(TARGETS[index])).create(p.serverLevel());
        var direction=actor.getLookAngle().multiply(.4,0,.4);npc.setPos(actor.getX()+direction.x,actor.getY(),actor.getZ()+direction.z);npc.setNoAi(true);p.serverLevel().addFreshEntity(npc);targetId=npc.getId();
    }
    private static int[] point(AbilityRadialScreen screen,int section){
        for(int x=0;x<screen.width;x+=3)for(int y=0;y<screen.height;y+=3)if(screen.getSectionAt(x,y).orElse(-1)==section)return new int[]{x,y};
        throw new AssertionError("no clickable grab section");
    }
    private static void captureWheel(Minecraft mc)throws Exception{
        render(mc,"player-friendly-hug-wheel.png",g->wheel.render(g,point(wheel,wheel.abilities.indexOf(ChangedAbilities.GRAB_ENTITY_ABILITY.get()))[0],point(wheel,wheel.abilities.indexOf(ChangedAbilities.GRAB_ENTITY_ABILITY.get()))[1],1));
        render(mc,"player-friendly-hug-badge.png",g->{
            g.drawString(mc.font,"Friendly hug / wheel and HUD icon",10,10,0xeeeeee,false);
            var grab=clientGrab(mc);
            for(int size:new int[]{16,24,32})net.ltxprogrammer.changed.client.ChangedClient.abilityRenderer.getOrThrow().renderAndDecorateAbility(
                    g,grab.entity.getEntity(),grab,40+size*3,60,size,1,true,0);
        });
    }
    private static void capturePose(Minecraft mc,String name)throws Exception{
        render(mc,"player-hug-"+(instinct?"instinct-":"")+name+".png",g->{
            int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
            g.drawString(mc.font,"Friendly hug / "+name,10,10,0xeeeeee,false);
            var actor=clientGrab(mc).entity.getEntity();
            int scale=(int)(h*(name.equals("latex_shark_feral")||name.equals("headless_knight")||name.equals("pure_white_latex_cerberus")?.26:.38));
            InventoryScreen.renderEntityInInventoryFollowsMouse(g,w/3,h-20,scale,20,-3,actor);
            InventoryScreen.renderEntityInInventoryFollowsMouse(g,w*2/3,h-20,scale,-70,-3,actor);
        });
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static void enterInstinct(Minecraft mc){try{
        var p=owner(mc);var identity=new net.parkabird.changedsynergy.origin.NativeOriginData.Identity();identity.state="native";
        identity.form=identity.current=ProcessTransfur.getPlayerTransfurVariant(p).getParent().getFormId().toString();identity.faction="light";
        net.parkabird.changedsynergy.origin.NativeOriginData.get(p.server).players.put(p.getUUID(),identity);
        p.serverLevel().getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(true,p.server);
        var cap=(net.minecraftforge.common.capabilities.Capability)Class.forName("com.lugert.true_transfur.transfur.TransfurCapabilities").getField("TRANSFUR_DATA").get(null);
        Object data=p.getCapability(cap).orElse(null);check(data!=null,"TrueTransfur capability attached");data.getClass().getMethod("setTransfurred",boolean.class).invoke(data,true);
        Class mode=Class.forName("com.lugert.true_transfur.transfur.ControlMode");
        check(Boolean.TRUE.equals(Class.forName("com.lugert.true_transfur.transfur.TransfurController").getMethod("switchControlMode",net.minecraft.server.level.ServerPlayer.class,mode).invoke(null,p,Enum.valueOf(mode,"INSTINCT"))),"actual client enters TT INSTINCT");
        ((ChangedEntity)net.parkabird.changedsynergy.compat.TrueTransfurCompat.interactionBody(p)).setNoAi(true);
    }catch(Exception e){throw new RuntimeException(e);}}
    private static void render(Minecraft mc,String name,java.util.function.Consumer<GuiGraphics> draw)throws Exception{
        var target=new TextureTarget(mc.getWindow().getWidth(),mc.getWindow().getHeight(),true,Minecraft.ON_OSX);var mv=RenderSystem.getModelViewStack();mv.pushPose();
        try{
            int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();mv.setIdentity();mv.translate(0,0,-11000);RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,w,h,0,1000,21000),VertexSorting.ORTHOGRAPHIC_Z);
            target.setClearColor(.11F,.13F,.15F,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);var g=new GuiGraphics(mc,mc.renderBuffers().bufferSource());draw.accept(g);g.flush();
            try(var image=net.minecraft.client.Screenshot.takeScreenshot(target)){image.writeToFile(mc.gameDirectory.toPath().resolve(name));}
            check(true,"rendered "+name);
        }finally{mv.popPose();RenderSystem.applyModelViewMatrix();target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);}
    }
    private static void finish(Minecraft mc){check(visualProblems.isEmpty(),"visual geometry findings: "+visualProblems);stage=99;System.out.println("PLAYER_HUG_CLIENT_PASS: "+checks+" actual wheel, packet, body render and release checks");mc.stop();}
}
