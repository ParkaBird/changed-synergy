package net.parkabird.changedsynergy.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Identity editor reached from the relationship wheel. */
public final class NativeRoleplayScreen extends Screen {
    private final Screen previous;
    private EditBox input;
    public NativeRoleplayScreen(Screen previous) {
        super(Component.translatable("menu.changed_synergy.roleplay.name"));this.previous=previous;
    }
    @Override protected void init() {
        int panel=Math.min(280,width-32),x=(width-panel)/2,y=Math.max(28,height/2-50);
            input=new EditBox(font,x,y,panel,20,title);input.setMaxLength(48);
            var name=NativeRoleplayClient.name(minecraft.player.getUUID());input.setValue(name==null?"":name.getString());addRenderableWidget(input);setInitialFocus(input);
            Button done=addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->{
                if(net.parkabird.changedsynergy.ai.NativeRoleplayService.validName(input.getValue().strip())) {
                    minecraft.player.connection.sendCommand("changedsynergy social name "+input.getValue().strip());onClose();
                }
            }).bounds(x,y+28,panel,20).build());
            input.setResponder(value->done.active=net.parkabird.changedsynergy.ai.NativeRoleplayService.validName(value.strip()));
            done.active=net.parkabird.changedsynergy.ai.NativeRoleplayService.validName(input.getValue().strip());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"),b->onClose()).bounds(x,y+56,panel,20).build());
    }
    @Override public void render(GuiGraphics graphics,int x,int y,float partial) {
        renderBackground(graphics);super.render(graphics,x,y,partial);graphics.drawCenteredString(font,title,width/2,Math.max(14,height/2-74),0xFFFFFF);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(previous);}
}
