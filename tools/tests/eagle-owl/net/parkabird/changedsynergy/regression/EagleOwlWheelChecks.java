package net.parkabird.changedsynergy.regression;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.parkabird.changedsynergy.ChangedSynergyClientConfig;
import net.parkabird.changedsynergy.client.*;
import net.parkabird.changedsynergy.world.inventory.SocialInteractionMenu;
import net.ltxprogrammer.changed.entity.ChangedEntity;

final class EagleOwlWheelChecks {
    private static int stage,frames,round,checks;private static boolean previousReduced;
    private static SocialInteractionScreen source,target;
    private static PagingWheel paging;
    static boolean tick(Minecraft mc,ChangedEntity creature)throws Exception{
        if(stage==0){previousReduced=UiMotion.reduced();stage=1;}
        if(stage==1){
            ChangedSynergyClientConfig.CLIENT.reducedUiMotion.set(round==1);
            var menu=new SocialInteractionMenu(61+round*2,mc.player.getInventory(),creature,false);
            var field=SocialInteractionMenu.class.getDeclaredField("negotiationAvailable");field.setAccessible(true);field.setBoolean(menu,true);
            source=new SocialInteractionScreen(menu,mc.player.getInventory(),Component.literal("Social"));mc.setScreen(source);frames=0;stage=2;
        }
        if(stage==2&&++frames>20){
            float expected=RadialWheelAnimations.layoutOffset(source.width);
            check(expected>0&&RadialWheelAnimations.horizontalOffset(source)==expected,"source wheel settled on the right");
            var menu=new SocialInteractionMenu(62+round*2,mc.player.getInventory(),creature,false,true);
            target=new SocialInteractionScreen(menu,mc.player.getInventory(),Component.literal("Negotiation"));
            check(RadialWheelAnimations.requestNegotiationSwitch(source,()->mc.setScreen(target)),"negotiation handoff accepted");
            check(mc.screen==target,"negotiation handoff runs immediately");
            check(RadialWheelAnimations.horizontalOffset(source)==expected,"source never departs toward the center");frames=0;stage=3;
        }
        if(stage==3){
            check(mc.screen==target&&target.menu.isNegotiationMode(),"correct destination remains open");
            check(RadialWheelAnimations.instantArrival(target),"destination skips its intro");
            check(RadialWheelAnimations.horizontalOffset(target)==RadialWheelAnimations.layoutOffset(target.width),"destination stays at the same right anchor on every frame");
            check(RadialWheelAnimations.overallAlpha(target)>.999F&&RadialWheelAnimations.panelAlpha(target)>.999F,"first frame is fully visible");
            check(((RadialWheelAnimationAccess)(Object)target).changedSynergy$canConfirm(),"destination immediately accepts its normal controls");
            if(++frames==6)try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(mc.gameDirectory.toPath().resolve("negotiation-direct-"+round+".png"));}
            if(frames>12){if(round++==0)stage=1;else{ChangedSynergyClientConfig.CLIENT.reducedUiMotion.set(previousReduced);System.out.println("NEGOTIATION_DIRECT_CLIENT_PASS: "+checks+" normal/reduced motion immediate right-to-right handoffs");stage=4;}}
        }
        if(stage==4){paging=new PagingWheel(mc);mc.setScreen(paging);frames=0;stage=5;}
        if(stage==5&&++frames>15){
            var scroll=new net.minecraftforge.client.event.ScreenEvent.MouseScrolled.Pre(paging,0,0,-.35);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(scroll);
            check(scroll.isCanceled()&&mc.screen==paging,"ordinary scroll pages a large wheel instead of switching");
            ((RadialWheelAnimationAccess)(Object)paging).changedSynergy$scrollOptions(-.7);frames=0;stage=6;
        }
        if(stage==6&&++frames>15){
            var access=(RadialWheelAnimationAccess)(Object)paging;
            check(java.lang.Math.abs(access.changedSynergy$getScrollOffset()-1.05F)<.002F,"fractional scroll input remains continuous");
            var beforeBoundary=RadialWheelAnimations.optionPosition(3,.9999F);var afterBoundary=RadialWheelAnimations.optionPosition(3,1.0001F);
            check(beforeBoundary.distanceToSqr(afterBoundary)<.01F,"scrolling across an integer does not jump several pixels");
            check(paging.alphas.values().stream().anyMatch(a->a>0&&a<.1F)&&paging.alphas.values().stream().anyMatch(a->a>.9F&&a<1),"entering and leaving options fade at the outside edges");
            float before=access.changedSynergy$getScrollOffset();paging.keyPressed(262,0,0);paging.keyPressed(263,0,0);check(access.changedSynergy$getScrollOffset()==before,"arrow keys no longer page the wheel");
            check(RadialWheelAnimations.scrollHint(paging,"menu.changed_synergy.relationship.switch_to_abilities").getString().contains("Shift"),"large-wheel hint includes Shift for switching");
            mc.setScreen(null);System.out.println("RADIAL_CONTINUOUS_SCROLL_CLIENT_PASS: "+checks+" handoff, fractional input, fade and keyboard checks");return true;
        }
        return false;
    }
    private static final class PagingWheel extends net.ltxprogrammer.changed.client.gui.AbstractRadialScreen<net.minecraft.world.inventory.InventoryMenu>{
        final java.util.Map<Integer,Float> alphas=new java.util.HashMap<>();
        PagingWheel(Minecraft mc){super(mc.player.inventoryMenu,mc.player.getInventory(),Component.literal("Paging"),net.ltxprogrammer.changed.util.Color3.WHITE,net.ltxprogrammer.changed.util.Color3.GRAY);}
        @Override public int getCount(){return 20;}
        @Override public java.util.List<Component> tooltipsFor(int section){return null;}
        @Override public boolean handleClicked(int section,net.ltxprogrammer.changed.util.SingleRunnable close){return false;}
        @Override public void renderSectionBackground(net.minecraft.client.gui.GuiGraphics g,int s,double x,double y,float p,int mx,int my,float r,float green,float b){}
        @Override public void renderSectionForeground(net.minecraft.client.gui.GuiGraphics g,int s,double x,double y,float p,int mx,int my,float r,float green,float b,float a){alphas.put(s,a);g.fill((int)x+width/2-8,(int)y+height/2-8,(int)x+width/2+8,(int)y+height/2+8,Math.round(a*255)<<24|0xFFFFFF);}
    }
    private static void check(boolean value,String text){if(!value)throw new AssertionError(text);checks++;}
}
