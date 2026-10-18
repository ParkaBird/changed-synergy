package net.parkabird.changedsynergy.regression;

import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.Path;
import java.util.List;
import net.ltxprogrammer.changed.client.renderer.layers.LatexHeldEntityLayer;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedAbilities;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/** Exercises the injected native renderer rather than a separate offset formula. */
final class HeldCreatureClientRegression {
    @SuppressWarnings({"rawtypes","unchecked"})
    static void run(Minecraft mc,Path directory)throws Exception {
        mc.options.guiScale().set(2);mc.resizeDisplay();
        int width=mc.getWindow().getGuiScaledWidth(),height=mc.getWindow().getGuiScaledHeight();
        com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().setOrtho(0,width,height,0,1000,3000),
                com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
        var target=new com.mojang.blaze3d.pipeline.TextureTarget(mc.getWindow().getWidth(),mc.getWindow().getHeight(),true,Minecraft.ON_OSX);
        var holders=List.of(ChangedEntities.PURE_WHITE_LATEX_CERBERUS.get(),ChangedEntities.DARK_LATEX_WOLF_MALE.get(),ChangedEntities.GAS_WOLF_MALE.get());
        try {
            target.setClearColor(.10F,.12F,.15F,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
            var graphics=new GuiGraphics(mc,mc.renderBuffers().bufferSource());
            for(int column=0;column<holders.size();column++) {
                ChangedEntity holder=holders.get(column).create(mc.level);
                net.parkabird.changedsynergy.ai.BondedSuitService.ensureGrabAbility(holder);
                var grab=holder.getAbilityInstance(ChangedAbilities.GRAB_ENTITY_ABILITY.get());
                if(grab==null)throw new AssertionError("Missing native grab ability");
                for(int row=0;row<3;row++) {
                    LivingEntity held;
                    if(row==2){var cow=EntityType.COW.create(mc.level);cow.setBaby(true);held=cow;}
                    else {var zombie=EntityType.ZOMBIE.create(mc.level);zombie.setBaby(row==1);held=zombie;}
                    grab.grabbedEntity=held;grab.suited=false;
                    int x=(column*2+1)*width/6,y=(row+1)*height/3-16;
                    InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,x,y,Math.max(18,height/11),-25,0,holder);
                    graphics.drawCenteredString(mc.font,(column==0?"Cerberus":column==1?"Dark Wolf":"Gas Wolf")+" / "+(row==0?"Zombie":row==1?"Baby Zombie":"Calf"),x,y+3,0xFFFFFF);
                }
                grab.grabbedEntity=null;
            }
            graphics.flush();
            try(var picture=net.minecraft.client.Screenshot.takeScreenshot(target)){picture.writeToFile(directory.resolve("held-creature-alignment.png"));}
            var holder=ChangedEntities.PURE_WHITE_LATEX_CERBERUS.get().create(mc.level);
            net.parkabird.changedsynergy.ai.BondedSuitService.ensureGrabAbility(holder);
            var renderer=(LivingEntityRenderer)mc.getEntityRenderDispatcher().getRenderer(holder);
            List<?> layers=net.minecraftforge.fml.util.ObfuscationReflectionHelper.getPrivateValue(LivingEntityRenderer.class,renderer,"f_115291_");
            Object layer=layers.stream().filter(LatexHeldEntityLayer.class::isInstance).findFirst().orElseThrow();
            var correction=java.util.Arrays.stream(LatexHeldEntityLayer.class.getDeclaredMethods())
                    .filter(method->method.getName().endsWith("$liftSmallHeldCreature")).findFirst().orElseThrow();
            correction.setAccessible(true);
            var grab=holder.getAbilityInstance(ChangedAbilities.GRAB_ENTITY_ABILITY.get());
            var adult=EntityType.ZOMBIE.create(mc.level);var baby=EntityType.ZOMBIE.create(mc.level);baby.setBaby(true);
            float adultLift=lift(mc,correction,layer,holder,adult,false);
            float babyLift=lift(mc,correction,layer,holder,baby,false);
            if(babyLift<=0 || babyLift<=adultLift)throw new AssertionError("Small held mobs are pushed DOWN instead of lifted to the holder's arms; baby="+babyLift+", adult="+adultLift);
            if(lift(mc,correction,layer,holder,baby,true)!=0)throw new AssertionError("Wrapped creatures must retain their own positioning");
            var tall=EntityType.IRON_GOLEM.create(mc.level);
            if(lift(mc,correction,layer,holder,tall,false)!=0)throw new AssertionError("A taller held creature must not be lifted");
            if(lift(mc,correction,layer,holder,null,false)!=0)throw new AssertionError("An empty grab must not change the pose");
            grab.grabbedEntity=null;grab.suited=false;
            System.out.println("HELD_CREATURE_CLIENT_REGRESSION_PASS: real native grab renders for three holders; adult/baby zombie and calf gallery; upward correction, wrapped/tall/empty exclusions");
        } finally {target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);}
    }

    private static float lift(Minecraft mc,java.lang.reflect.Method correction,Object layer,ChangedEntity holder,LivingEntity held,boolean suited)throws Exception {
        var grab=holder.getAbilityInstance(ChangedAbilities.GRAB_ENTITY_ABILITY.get());grab.grabbedEntity=held;grab.suited=suited;
        var pose=new PoseStack();pose.scale(-1,-1,1);
        correction.invoke(layer,pose,mc.renderBuffers().bufferSource(),15728880,holder,0F,0F,0F,0F,0F,0F,
                new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("held-regression",false));
        return pose.last().pose().m31();
    }
}
