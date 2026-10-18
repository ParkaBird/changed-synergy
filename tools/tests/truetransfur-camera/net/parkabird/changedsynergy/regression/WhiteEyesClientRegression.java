package net.parkabird.changedsynergy.regression;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.BasicPlayerInfo;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.parkabird.changedsynergy.ai.PureWhiteWolfAdaptation;
import net.parkabird.changedsynergy.client.PureWhiteAdaptedEyesLayer;

final class WhiteEyesClientRegression {
    static void run(Minecraft mc, java.nio.file.Path directory) throws Exception {
        mc.options.guiScale().set(2); mc.resizeDisplay();
        int width=mc.getWindow().getGuiScaledWidth(), height=mc.getWindow().getGuiScaledHeight();
        com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().setOrtho(0,width,height,0,1000,3000),
                com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
        var target = new com.mojang.blaze3d.pipeline.TextureTarget(mc.getWindow().getWidth(),mc.getWindow().getHeight(),true,Minecraft.ON_OSX);
        try {
            target.setClearColor(.10F,.12F,.15F,1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            var graphics = new GuiGraphics(mc,mc.renderBuffers().bufferSource());
            var types=List.of(ChangedEntities.PURE_WHITE_LATEX_WOLF.get(),ChangedEntities.PURE_WHITE_LATEX_WOLF_PUP.get(),
                    ChangedEntities.PURE_WHITE_LATEX_CERBERUS.get(),ChangedEntities.LATEX_MUTANT_BLOODCELL_WOLF.get());
            for(int index=0;index<types.size();index++) {
                ChangedEntity creature=types.get(index).create(mc.level);
                creature.getBasicPlayerInfo().setLeftIrisColor(net.ltxprogrammer.changed.util.Color3.fromInt(0x44DD66));
                creature.getBasicPlayerInfo().setRightIrisColor(net.ltxprogrammer.changed.util.Color3.fromInt(0xDD9944));
                var renderer=(LivingEntityRenderer<?,?>)mc.getEntityRenderDispatcher().getRenderer(creature);
                java.util.List<?> layers=net.minecraftforge.fml.util.ObfuscationReflectionHelper.getPrivateValue(
                        LivingEntityRenderer.class,renderer,"f_115291_");
                var eyes=layers.stream().filter(PureWhiteAdaptedEyesLayer.class::isInstance)
                        .map(layer -> (PureWhiteAdaptedEyesLayer<?>)layer).toList();
                if(eyes.size()!=(index==2?3:1)) throw new AssertionError("Incorrect eye-layer count for "+types.get(index));
                appearanceChecks(creature,eyes);
                var original=(net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel<?>)renderer.getModel();
                var heads=new java.util.HashSet<net.minecraft.client.model.geom.ModelPart>();
                for(var eye:eyes) {
                    var view=eye.getParentModel();
                    var stemField=view.getClass().getDeclaredField("stem");stemField.setAccessible(true);
                    var actualStem=(net.ltxprogrammer.changed.client.ModelPartStem)stemField.get(view);
                    var head=actualStem.getLeaf();
                    if(!heads.add(head))throw new AssertionError("Duplicate eye layer on the same head");
                    var stem=original.getAllParts().filter(part -> part.getLeaf()==head).findFirst().orElseThrow();
                    head.yRot=.35F;head.xRot=-.2F;
                    var expected=new PoseStack();original.scaleForHead(expected);stem.translateAndRotate(expected);
                    var actual=new PoseStack();view.scaleForHead(actual);view.getHead().translateAndRotate(actual);
                    if(!expected.last().pose().equals(actual.last().pose(),.00001F))
                        throw new AssertionError("Eyes do not follow the complete animated head transform");
                }
                for(int row=0;row<2;row++) {
                    PureWhiteWolfAdaptation.setAdaptedForm(creature,row==1);
                    if(PureWhiteWolfAdaptation.isAdaptedForm(creature)!=(row==1))throw new AssertionError("Eye visibility failed");
                    int x=(index*2+1)*width/8, y=(row+1)*height/2-12;
                    InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,x,y,Math.max(18,height/8),0,0,creature);
                    if(row==1) renderAppearanceChecks(mc,creature,eyes);
                    graphics.drawCenteredString(mc.font,index==0?"Pure White":index==1?"Pup":index==2?"Cerberus (3 heads)":"Bloodcell",x,y+2,0xFFFFFF);
                }
            }
            graphics.flush();
            try(var picture=net.minecraft.client.Screenshot.takeScreenshot(target)){picture.writeToFile(directory.resolve("white-eye-adaptation-gallery.png"));}
            target.clear(Minecraft.ON_OSX);target.bindWrite(true);
            var whitePup=ChangedEntities.PURE_WHITE_LATEX_WOLF_PUP.get().create(mc.level);
            var gasPup=ChangedEntities.GAS_WOLF_PUP.get().create(mc.level);
            PureWhiteWolfAdaptation.setAdaptedForm(whitePup,true);
            for(int row=0;row<2;row++) {
                ChangedEntity pup=row==0?whitePup:gasPup;
                pup.getBasicPlayerInfo().setLeftIrisColor(net.ltxprogrammer.changed.util.Color3.fromInt(0x44DD66));
                pup.getBasicPlayerInfo().setRightIrisColor(net.ltxprogrammer.changed.util.Color3.fromInt(0xDD9944));
                var renderer=(LivingEntityRenderer<?,?>)mc.getEntityRenderDispatcher().getRenderer(pup);
                java.util.List<?> layers=net.minecraftforge.fml.util.ObfuscationReflectionHelper.getPrivateValue(LivingEntityRenderer.class,renderer,"f_115291_");
                var eyes=layers.stream().filter(net.ltxprogrammer.changed.client.renderer.layers.CustomEyesLayer.class::isInstance).findFirst().orElseThrow();
                var shape=net.ltxprogrammer.changed.client.renderer.layers.CustomEyesLayer.class.getDeclaredField("headShape");shape.setAccessible(true);
                if(shape.get(eyes)!=net.ltxprogrammer.changed.client.renderer.layers.CustomEyesLayer.HeadShape.PUP)
                    throw new AssertionError("Pure White pup must use the same native eye-head geometry as Gas Wolf Pup");
                for(int angle=0;angle<3;angle++) {
                    int x=(angle*2+1)*width/6,y=(row+1)*height/2-12;
                    InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,x,y,Math.max(36,height/5),angle==0?-40:angle==1?0:40,0,pup);
                    graphics.drawCenteredString(mc.font,row==0?"Pure White Pup":"Native Gas Wolf Pup",x,y+2,0xFFFFFF);
                }
            }
            graphics.flush();
            try(var picture=net.minecraft.client.Screenshot.takeScreenshot(target)){picture.writeToFile(directory.resolve("pup-native-eye-comparison.png"));}
            System.out.println("WHITE_EYES_CLIENT_REGRESSION_PASS: four original models, full animated head transforms, stable individual Cerberus colours/styles, native render snapshots and corrected pup height");
        } finally {target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);}
    }

    private static net.minecraft.nbt.CompoundTag appearance(BasicPlayerInfo info) {
        var tag=new net.minecraft.nbt.CompoundTag();info.save(tag);return tag;
    }

    private static void appearanceChecks(ChangedEntity creature,List<? extends PureWhiteAdaptedEyesLayer<?>> eyes) {
        var original=creature.getBasicPlayerInfo();var saved=appearance(original);
        var styles=new java.util.HashSet<net.ltxprogrammer.changed.entity.EyeStyle>();
        var colours=new java.util.HashSet<Integer>();
        for(var eye:eyes) {
            var derived=eye.appearanceForHead(original);var expected=appearance(derived);
            if(!expected.equals(appearance(eye.appearanceForHead(new BasicPlayerInfo(saved.copy())))))
                throw new AssertionError("A new body or reloaded appearance changes the head's eyes");
            if(!saved.equals(appearance(original)))throw new AssertionError("Eye rendering modified the creature's appearance");
            if(eyes.size()==1 && derived!=original)throw new AssertionError("Single-head creatures must retain their own eyes");
            styles.add(derived.getEyeStyle());colours.add(derived.getLeftIrisColor().toInt());
        }
        if(eyes.size()==3 && (styles.size()<2 || colours.size()<2 || eyes.get(1).appearanceForHead(original)!=original))
            throw new AssertionError("Cerberus needs independent side eyes and the unchanged centre-head appearance");
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private static void renderAppearanceChecks(Minecraft mc,ChangedEntity creature,List<? extends PureWhiteAdaptedEyesLayer<?>> eyes)throws Exception {
        var field=net.ltxprogrammer.changed.client.renderer.layers.CustomEyesLayer.class.getDeclaredField("irisColorLeftFn");
        field.setAccessible(true);
        for(var eye:eyes) {
            var previous=(net.ltxprogrammer.changed.client.renderer.layers.CustomEyesLayer.ColorFunction<ChangedEntity>)field.get(eye);
            var received=new java.util.concurrent.atomic.AtomicReference<net.minecraft.nbt.CompoundTag>();
            var probe=(net.ltxprogrammer.changed.client.renderer.layers.CustomEyesLayer.ColorFunction<ChangedEntity>)(entity,info)->{
                received.set(appearance(info));return previous.getColor(entity,info);
            };
            try {
                field.set(eye,probe);
                ((PureWhiteAdaptedEyesLayer)eye).render(new PoseStack(),mc.renderBuffers().bufferSource(),15728880,creature,0,0,0,0,0,0);
                var expected=eye.appearanceForHead(creature.getBasicPlayerInfo());
                if(net.ltxprogrammer.changed.Changed.config.client.basicPlayerInfo.isOverrideOthersToMatchStyle()) {
                    var copy=new BasicPlayerInfo();copy.copyFrom(expected);expected=copy;
                    expected.setEyeStyle(net.ltxprogrammer.changed.Changed.config.client.basicPlayerInfo.getEyeStyle());
                }
                if(!appearance(expected).equals(received.get()))throw new AssertionError("Native eye renderer did not receive this head's appearance");
            } finally {field.set(eye,previous);}
        }
    }
}
