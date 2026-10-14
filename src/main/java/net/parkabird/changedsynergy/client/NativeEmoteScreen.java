package net.parkabird.changedsynergy.client;

import java.util.List;
import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.client.gui.AbstractRadialScreen;
import net.ltxprogrammer.changed.util.SingleRunnable;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** The same geometry, icons and hover selection as the social wheel; actions remain server-authoritative. */
public final class NativeEmoteScreen extends AbstractRadialScreen<NativeEmoteScreen.EmoteMenu> {
    private static final List<String> EMOTES=List.of("casual","confused","deny","angry","nervous","heart");
    private final Screen previous;

    public NativeEmoteScreen(Screen previous) {
        super(new EmoteMenu(),Minecraft.getInstance().player.getInventory(),Component.translatable("menu.changed_synergy.roleplay.emotes"),
                PlayerRelationshipScreen.readableColors(ProcessTransfur.getPlayerTransfurVariant(Minecraft.getInstance().player)).background(),
                PlayerRelationshipScreen.readableColors(ProcessTransfur.getPlayerTransfurVariant(Minecraft.getInstance().player)).foreground(),center());
        this.previous=previous;
    }
    private static LivingEntity center() {
        var mc=Minecraft.getInstance();
        if(mc.getCameraEntity() instanceof LivingEntity body&&PlayerSocialClient.belongsTo(body,mc.player.getUUID()))return body;
        return mc.player;
    }
    @Override public int getCount(){return EMOTES.size();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public List<Component> tooltipsFor(int section) {
        return section<0||section>=EMOTES.size()?null:List.of(Component.translatable("menu.changed_synergy.roleplay.emote."+EMOTES.get(section)));
    }
    @Override public void renderSectionBackground(GuiGraphics graphics,int section,double x,double y,float partial,int mouseX,int mouseY,float red,float green,float blue) {
        boolean selected=RadialWheelAnimations.isSectionSelected(this,section,mouseX,mouseY);
        ResourceLocation texture=Changed.modResource("textures/gui/radial/goo"+(selected?"_selected/":"/")+section+".png");
        graphics.setColor(red,green,blue,RadialWheelAnimations.layerAlpha(this));
        graphics.blit(texture,(int)x-32+leftPos,(int)y-32+topPos,0,0,64,64,64,64);
        graphics.setColor(1,1,1,1);
    }
    @Override public void renderSectionForeground(GuiGraphics graphics,int section,double x,double y,float partial,int mouseX,int mouseY,float red,float green,float blue,float alpha) {
        if(section<0||section>=EMOTES.size())return;
        ResourceLocation texture=Changed.modResource("textures/particle/emote/"+EMOTES.get(section)+".png");
        int px=(int)x+leftPos,py=(int)y+topPos;
        graphics.setColor(0,0,0,.45F*alpha);graphics.blit(texture,px-14,py-14,0,0,32,32,32,32);
        graphics.setColor(red,green,blue,alpha);graphics.blit(texture,px-16,py-16,0,0,32,32,32,32);graphics.setColor(1,1,1,1);
    }
    @Override public boolean handleClicked(int section,SingleRunnable close) {
        if(!NativeRoleplayClient.active()||section<0||section>=EMOTES.size())return false;
        minecraft.player.connection.sendCommand("changedsynergy social emote "+EMOTES.get(section));
        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK,1));
        onClose();return false;
    }
    @Override public void onClose(){minecraft.setScreen(previous);}
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(key==256){onClose();return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void containerTick(){super.containerTick();if(!NativeRoleplayClient.active()||centerEntity.isRemoved()||centerEntity!=center())onClose();}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial) {
        super.render(graphics,mouseX,mouseY,partial);
        graphics.drawCenteredString(font,title,width/2,18,0xFFFFFF);
        graphics.drawCenteredString(font,Component.translatable("menu.changed_synergy.roleplay.emote_back"),width/2,height-18,0xC6C6C6);
    }
    public static final class EmoteMenu extends AbstractContainerMenu {
        private EmoteMenu(){super(null,0);}
        @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
        @Override public boolean stillValid(Player player){return NativeRoleplayClient.active();}
    }
}
