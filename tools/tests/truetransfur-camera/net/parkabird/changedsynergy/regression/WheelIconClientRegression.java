package net.parkabird.changedsynergy.regression;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.file.Path;
import java.util.List;
import net.ltxprogrammer.changed.client.gui.AbilityRadialScreen;
import net.ltxprogrammer.changed.client.gui.AbstractRadialScreen;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.ltxprogrammer.changed.world.inventory.AbilityRadialMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.parkabird.changedsynergy.client.PlayerRelationshipScreen;
import net.parkabird.changedsynergy.client.RadialWheelAnimations;
import net.parkabird.changedsynergy.world.inventory.PlayerRelationshipMenu;

/** Pixel comparisons exercise the real Changed ability renderer after radial mixins. */
final class WheelIconClientRegression {
    static void run(Minecraft mc, Path directory)throws Exception {
        int count=0;var failures=new java.util.ArrayList<String>();
        var forms=List.of(ChangedTransfurVariants.PURE_WHITE_LATEX_WOLF,
                ChangedTransfurVariants.PURE_WHITE_LATEX_WOLF_PUP,
                ChangedTransfurVariants.PURE_WHITE_LATEX_CERBERUS,
                ChangedTransfurVariants.LATEX_MUTANT_BLOODCELL_WOLF,
                ChangedTransfurVariants.WHITE_LATEX_WOLF_MALE,
                ChangedTransfurVariants.WHITE_LATEX_WOLF_FEMALE,
                ChangedTransfurVariants.DARK_LATEX_WOLF_MALE,
                ChangedTransfurVariants.LATEX_HUMAN);
        for(int scale:new int[]{2,3}) {
            mc.options.guiScale().set(scale);mc.resizeDisplay();
            RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().setOrtho(0,mc.getWindow().getGuiScaledWidth(),
                    mc.getWindow().getGuiScaledHeight(),0,1000,3000),com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
            var target=new TextureTarget(mc.getWindow().getWidth(),mc.getWindow().getHeight(),true,Minecraft.ON_OSX);
            try {
                for(var form:forms) {
                    ProcessTransfur.setPlayerTransfurVariant(mc.player,form.get());
                    var menu=new AbilityRadialMenu(12,mc.player.getInventory());
                    var screen=new Probe(menu,mc);mc.setScreen(screen);opened(screen);
                    if(screen.abilities.isEmpty())throw new AssertionError("No abilities for "+form.getId());
                    screen.visible=-1;
                    try(var background=capture(mc,target,screen)) {
                        for(int section=0;section<Math.min(8,screen.abilities.size());section++) {
                            screen.visible=section;
                            try(var icon=capture(mc,target,screen)) {
                                double angle=(section+.5+(section%2==0?.04:-.04))/8*Math.PI*2;
                                int x=screen.width/2+(int)(Math.sin(angle)*90)+RadialWheelAnimations.iconOffset(screen,section,false);
                                int y=screen.height/2-(int)(Math.cos(angle)*90)+RadialWheelAnimations.iconOffset(screen,section,true);
                                int different=differentPixels(background,icon,x,y,screen.width,screen.height);
                                if(different<12)failures.add(form.getId()+" section "+section+" scale "+scale+" visible pixels="+different);
                                count++;
                            }
                        }
                    }
                    screen.visible=-2;
                    try(var image=capture(mc,target,screen)) {
                        image.writeToFile(directory.resolve("ability-wheel-"+form.getId().getPath().replace('/','-')+"-scale-"+scale+".png"));
                    }
                    mc.setScreen(null);
                }
                var menu=new PlayerRelationshipMenu(13,mc.player.getInventory(),List.of());
                var action=PlayerRelationshipMenu.class.getDeclaredField("outpostAction");action.setAccessible(true);
                var screen=new PlayerRelationshipScreen(menu,mc.player.getInventory(),PlayerRelationshipMenu.TITLE);
                mc.setScreen(screen);opened(screen);
                for(String value:List.of("outpost","create_outpost","link_outpost","invite_outpost")) {
                    action.set(menu,value);
                    var tooltip=screen.tooltipsFor(0);
                    if(tooltip==null||tooltip.size()<2||tooltip.stream().anyMatch(t->t.getString().contains("menu.changed_synergy.")))
                        failures.add("Missing camp action tooltip: "+value);
                }
                action.set(menu,"invite_outpost");
                target.clear(Minecraft.ON_OSX);target.bindWrite(true);
                var graphics=new GuiGraphics(mc,mc.renderBuffers().bufferSource());
                int x=screen.width/2+Math.round(RadialWheelAnimations.horizontalOffset(screen))+40,y=screen.height/2-80;
                if(screen.getSectionAt(x,y).orElse(-1)!=0)throw new AssertionError("Camp invite screenshot must hover the invitation slot");
                screen.render(graphics,x,y,1);graphics.flush();
                try(var picture=Screenshot.takeScreenshot(target)){picture.writeToFile(directory.resolve("camp-invite-tooltip-scale-"+scale+".png"));}
                mc.setScreen(null);
            } finally {target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);}
        }
        if(!failures.isEmpty())throw new AssertionError("RADIAL_ICON_VISIBILITY_FAILURE: "+failures);
        System.out.println("RADIAL_ICON_VISIBILITY_CLIENT_REGRESSION_PASS: "+count+" real ability/frame pixel checks; six White forms, Dark Wolf and Latex Human, GUI scales 2/3; four translated camp actions");
    }
    private static NativeImage capture(Minecraft mc,TextureTarget target,Probe screen) {
        target.setClearColor(.08F,.09F,.10F,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
        var graphics=new GuiGraphics(mc,mc.renderBuffers().bufferSource());screen.render(graphics,0,0,1);graphics.flush();
        return Screenshot.takeScreenshot(target);
    }
    private static int differentPixels(NativeImage before,NativeImage after,int centerX,int centerY,int width,int height) {
        int found=0;
        for(int y=Math.max(0,(centerY-17)*after.getHeight()/height);y<Math.min(after.getHeight(),(centerY+17)*after.getHeight()/height);y++)
            for(int x=Math.max(0,(centerX-17)*after.getWidth()/width);x<Math.min(after.getWidth(),(centerX+17)*after.getWidth()/width);x++) {
                int a=before.getPixelRGBA(x,y),b=after.getPixelRGBA(x,y);
                if(Math.abs((a&255)-(b&255))+Math.abs((a>>8&255)-(b>>8&255))+Math.abs((a>>16&255)-(b>>16&255))>24)found++;
            }
        return found;
    }
    private static void opened(Object screen)throws Exception {
        for(Class<?> type=screen.getClass();type!=null;type=type.getSuperclass())try {
            var field=type.getDeclaredField("changedSynergy$openedAtNanos");field.setAccessible(true);
            field.setLong(screen,System.nanoTime()-2_000_000_000L);return;
        }catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException("radial open timestamp");
    }
    private static final class Probe extends AbilityRadialScreen {
        int visible=-2;
        Probe(AbilityRadialMenu menu,Minecraft mc){super(menu,mc.player.getInventory(),AbilityRadialMenu.CONTAINER_TITLE);}
        @Override public void renderSectionForeground(GuiGraphics g,int section,double x,double y,float partial,int mx,int my,float r,float green,float b,float alpha) {
            if(visible==-2||visible==section)super.renderSectionForeground(g,section,x,y,partial,mx,my,r,green,b,alpha);
        }
    }
}
