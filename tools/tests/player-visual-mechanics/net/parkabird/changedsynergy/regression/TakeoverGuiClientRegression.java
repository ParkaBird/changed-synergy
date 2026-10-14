package net.parkabird.changedsynergy.regression;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.parkabird.changedsynergy.client.TakeoverClientState;
import net.parkabird.changedsynergy.client.TakeoverOverlay;
import net.parkabird.changedsynergy.client.TakeoverScreen;
import net.parkabird.changedsynergy.network.TakeoverStatePacket;

/** Test takeover state, wheel and HUD against the actual client window and projection. */
public final class TakeoverGuiClientRegression {
    public static void run(Minecraft mc) throws Exception {
        int scale = mc.options.guiScale().get(), checks = 0;
        var previous = mc.screen;
        var carrier = (net.ltxprogrammer.changed.entity.ChangedEntity)net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                .getValue(net.minecraft.resources.ResourceLocation.parse("changed:dark_latex_wolf_male")).create(mc.level);
        carrier.setPos(mc.player.position().add(1, 0, 0));
        mc.level.putNonPlayerEntity(210002, carrier);
        var id = java.util.UUID.randomUUID();
        var render = TakeoverOverlay.class.getDeclaredMethod("render", GuiGraphics.class, float.class, int.class, int.class);
        render.setAccessible(true);
        try {
            camera(mc,carrier);checks+=7;
            exoskeletonHeight(mc);checks++;
            for (int option : new int[] {0, 2, 3}) {
                mc.options.guiScale().set(option); mc.resizeDisplay();
                double actualScale = mc.getWindow().getGuiScale();
                int width = mc.getWindow().getGuiScaledWidth(), height = mc.getWindow().getGuiScaledHeight();
                for (int kind : new int[]{TakeoverStatePacket.NORMAL,TakeoverStatePacket.INSTINCT})
                for (int phase : kind==TakeoverStatePacket.INSTINCT?new int[]{0,2,6}:new int[] {0, 2, 3, 5, 6}) {
                    TakeoverClientState.receive(new TakeoverStatePacket(id, kind, phase, carrier.getId(), mc.player.getId(),
                            100, 100, 0, false, false, true, 0, 0, 1, 0, 12, phase == 3 ? .8F : 0, "Carrier"));
                    var graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
                    var projection = new org.joml.Matrix4f(RenderSystem.getProjectionMatrix());
                    render.invoke(null, graphics, 0F, width, height); graphics.flush();
                    if (phase == 0) {
                        var wheel = new TakeoverScreen(); wheel.init(mc, width, height);
                        wheel.render(graphics, width / 2, height / 2, 0); graphics.flush();
                    }
                    if (mc.options.guiScale().get() != option || mc.getWindow().getGuiScale() != actualScale
                            || mc.getWindow().getGuiScaledWidth() != width || mc.getWindow().getGuiScaledHeight() != height
                            || !projection.equals(RenderSystem.getProjectionMatrix(), .00001F))
                        throw new AssertionError("Takeover changes GUI scale/projection: option=" + option + " phase=" + phase);
                    checks++;
                    if(kind==TakeoverStatePacket.INSTINCT&&phase!=TakeoverStatePacket.FINISHED
                            &&!net.parkabird.changedsynergy.ai.BondedSuitService.ability(carrier).suited)
                        throw new AssertionError("Client Instinct takeover must render SUIT");
                }
                countdownPixels(mc,id,carrier.getId(),width,height);checks+=6;
            }
            if (TakeoverClientState.active() || TakeoverClientState.movementLocked()) throw new AssertionError("Finished takeover retains client lock");
            System.out.println("TAKEOVER_GUI_REGRESSION_PASS: " + checks + " actual-window state/wheel/HUD checks");
        } finally {
            TakeoverClientState.clear(); carrier.discard(); mc.level.removeEntity(210002, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            mc.options.guiScale().set(scale); mc.resizeDisplay(); mc.setScreen(previous);
        }
    }
    private static void exoskeletonHeight(Minecraft mc)throws Exception {
        var before=mc.player.position();var id=java.util.UUID.randomUUID();
        var update=TakeoverClientState.class.getDeclaredMethod("applyExoskeletonMotion",net.minecraft.world.entity.player.Player.class);update.setAccessible(true);
        try {
            TakeoverClientState.receive(new TakeoverStatePacket(id,TakeoverStatePacket.EXOSKELETON,0,-1,mc.player.getId(),100,100,0,false,false,false,0,0,0,0,1,0,""));
            TakeoverClientState.receiveExoskeletonMotion(new net.parkabird.changedsynergy.network.ExoskeletonMotionPacket(id,before.x,before.y-1,before.z,.1,-.3,0,0));
            update.invoke(null,mc.player);
            if(Math.abs(mc.player.getY()-(before.y-1))>.0001)throw new AssertionError("Client exoskeleton floats above its authoritative descent height");
        } finally {TakeoverClientState.clear();mc.player.setPos(before);mc.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);}
    }
    private static void camera(Minecraft mc,net.ltxprogrammer.changed.entity.ChangedEntity carrier) {
        var type=mc.options.getCameraType();var previous=mc.getCameraEntity();
        float playerYaw=mc.player.getYRot(),playerPitch=mc.player.getXRot();
        float bodyYaw=carrier.getYRot(),bodyPitch=carrier.getXRot();
        try {
            mc.setCameraEntity(carrier);carrier.setYRot(35);carrier.setXRot(10);
            carrier.yRotO=35;carrier.xRotO=10;
            var id=java.util.UUID.randomUUID();
            TakeoverClientState.receive(new TakeoverStatePacket(id,0,0,carrier.getId(),mc.player.getId(),100,100,0,false,false,true,0,0,1,0,12,0,"Carrier"));
            float capturedYaw=mc.player.getYRot(),capturedPitch=mc.player.getXRot();
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            float start=TakeoverClientState.observationYaw();mc.player.turn(200,100);
            if(Math.abs(TakeoverClientState.observationYaw()-start-30)>.01||Math.abs(TakeoverClientState.observationPitch()-25)>.01)throw new AssertionError("Third-person turn did not reach independent camera state");
            if(mc.player.getYRot()!=capturedYaw||mc.player.getXRot()!=capturedPitch||carrier.getYRot()!=35||carrier.getXRot()!=10)throw new AssertionError("Observation changed controlled body rotations");
            var camera=mc.gameRenderer.getMainCamera();camera.setup(mc.level,carrier,true,false,1);
            if(Math.abs(camera.getYRot()-65)>.01||Math.abs(camera.getXRot()-25)>.01)throw new AssertionError("Rear camera did not use observation rotation");
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);camera.setup(mc.level,carrier,true,true,1);
            if(Math.abs(net.minecraft.util.Mth.wrapDegrees(camera.getYRot()-245))>.01||Math.abs(camera.getXRot()+25)>.01)throw new AssertionError("Front camera lost vanilla mirroring");
            mc.player.turn(0,10000);if(TakeoverClientState.observationPitch()!=90)throw new AssertionError("Observation pitch was not clamped");
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);camera.setup(mc.level,carrier,false,false,1);
            mc.player.turn(500,500);
            if(TakeoverClientState.freeLook()||TakeoverClientState.turnObservation(10,10)
                    ||mc.player.getYRot()!=capturedYaw||mc.player.getXRot()!=capturedPitch
                    ||Math.abs(camera.getYRot()-carrier.getViewYRot(1))>.01||Math.abs(camera.getXRot()-carrier.getViewXRot(1))>.01)throw new AssertionError("First-person observation is unlocked");
            TakeoverClientState.clear();if(TakeoverClientState.freeLook())throw new AssertionError("Release retains observation input");
        } finally {TakeoverClientState.clear();mc.options.setCameraType(type);mc.setCameraEntity(previous);mc.player.setYRot(playerYaw);mc.player.setXRot(playerPitch);carrier.setYRot(bodyYaw);carrier.setXRot(bodyPitch);}
    }
    private static void countdownPixels(Minecraft mc,java.util.UUID id,int carrier,int width,int height) throws Exception {
        var draw=TakeoverOverlay.class.getDeclaredMethod("releaseCountdown",GuiGraphics.class,int.class,int.class,TakeoverStatePacket.class);
        draw.setAccessible(true);
        var projection=new org.joml.Matrix4f(RenderSystem.getProjectionMatrix());var sorting=RenderSystem.getVertexSorting();
        var model=RenderSystem.getModelViewStack();model.pushPose();model.setIdentity();model.translate(0,0,-2000);RenderSystem.applyModelViewMatrix();
        var target=new com.mojang.blaze3d.pipeline.TextureTarget(mc.getWindow().getWidth(),mc.getWindow().getHeight(),true,Minecraft.ON_OSX);
        try {
            RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().setOrtho(0,width,height,0,1000,3000),com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
            target.setClearColor(0,0,0,1);
            for(int outcome=0;outcome<3;outcome++) {
                var state=new TakeoverStatePacket(id,TakeoverStatePacket.NORMAL,TakeoverStatePacket.CONTROLLED,carrier,mc.player.getId(),
                        1200,1200,outcome,false,false,true,0,0,1,0,12,0,"Carrier");
                TakeoverClientState.receive(state);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
                var graphics=new GuiGraphics(mc,mc.renderBuffers().bufferSource());draw.invoke(null,graphics,width,height,state);graphics.flush();
                try(var pixels=net.minecraft.client.Screenshot.takeScreenshot(target)) {
                    double scale=mc.getWindow().getGuiScale();
                    if(nonBlack(pixels,0,6,width,9,scale)<20||nonBlack(pixels,width/2-91,17,182,10,scale)<100)
                        throw new AssertionError("Countdown text or native release texture is absent from the original top position");
                    if(nonBlack(pixels,width/2-91,height-29,182,10,scale)!=0)
                        throw new AssertionError("Human countdown was moved to the bottom experience-bar position");
                    if(mc.options.guiScale().get()==2) {
                        var directory=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(directory);
                        pixels.writeToFile(directory.resolve("takeover-countdown-outcome-"+outcome+".png"));
                    }
                }
            }
        } finally {
            target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);
            model.popPose();RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(projection,sorting);
            TakeoverClientState.clear();
        }
    }
    private static int nonBlack(com.mojang.blaze3d.platform.NativeImage image,int x,int y,int width,int height,double scale) {
        int count=0;for(int py=(int)(y*scale);py<(y+height)*scale&&py<image.getHeight();py++)
            for(int px=(int)(x*scale);px<(x+width)*scale&&px<image.getWidth();px++)if((image.getPixelRGBA(px,py)&0xFFFFFF)!=0)count++;
        return count;
    }
}
