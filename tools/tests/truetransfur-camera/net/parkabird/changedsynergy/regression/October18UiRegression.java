package net.parkabird.changedsynergy.regression;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.parkabird.changedsynergy.client.*;
import net.parkabird.changedsynergy.network.TakeoverStatePacket;
import net.parkabird.changedsynergy.ai.PlayerRelationshipSettings.Contact;
import net.parkabird.changedsynergy.world.inventory.PlayerRelationshipMenu;

final class October18UiRegression {
    static void run(Minecraft mc) throws Exception {
        var directory = mc.gameDirectory.toPath().resolve("screenshots"); java.nio.file.Files.createDirectories(directory);
        var projection = new org.joml.Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting(); var model = RenderSystem.getModelViewStack();
        model.pushPose(); model.setIdentity(); model.translate(0,0,-2000); RenderSystem.applyModelViewMatrix();
        int oldScale = mc.options.guiScale().get();
        try {
            for (int scale : new int[]{2,3}) {
                mc.options.guiScale().set(scale); mc.resizeDisplay();
                int width=mc.getWindow().getGuiScaledWidth(), height=mc.getWindow().getGuiScaledHeight();
                RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().setOrtho(0,width,height,0,1000,3000),
                        com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
                var target = new com.mojang.blaze3d.pipeline.TextureTarget(mc.getWindow().getWidth(), mc.getWindow().getHeight(), true, Minecraft.ON_OSX);
                try {
                    var contact = new Contact(UUID.randomUUID(),-1,"Mica J8","changed:dark_latex_wolf_male",
                            "minecraft:overworld",true,0,64,0,true,false,true,false,false,60,24,24,true);
                    var menu = new PlayerRelationshipMenu(3, mc.player.getInventory(), List.of(contact));
                    var screen = new PlayerRelationshipScreen(menu, mc.player.getInventory(), PlayerRelationshipMenu.TITLE);
                    mc.setScreen(screen); for(int i=0;i<20;i++)screen.containerTick();
                    var field=field(screen,"changedSynergy$openedAtNanos"); field.setLong(screen,System.nanoTime()-2_000_000_000L);
                    target.setClearColor(.08F,.08F,.08F,1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
                    var graphics=new GuiGraphics(mc,mc.renderBuffers().bufferSource()); screen.render(graphics,0,0,1); graphics.flush();
                    try(var image=net.minecraft.client.Screenshot.takeScreenshot(target)){image.writeToFile(directory.resolve("relationship-alignment-scale-"+scale+".png"));}
                    mc.setScreen(null);
                    UUID id=UUID.randomUUID();
                    GrabQteOverlayRenderer.renderTakeover(graphics,width,height,id,1,3,12,-1);
                    field(GrabQteOverlayRenderer.class,"takeoverStartedAt").setLong(null,net.minecraft.Util.getMillis()-1000);
                    field(GrabQteOverlayRenderer.class,"takeoverKeyStartedAt").setLong(null,net.minecraft.Util.getMillis()-1000);
                    target.clear(Minecraft.ON_OSX); target.bindWrite(true);
                    GrabQteOverlayRenderer.renderTakeover(graphics,width,height,id,1,3,12,-1); graphics.flush();
                    try(var image=net.minecraft.client.Screenshot.takeScreenshot(target)){image.writeToFile(directory.resolve("grab-qte-alignment-scale-"+scale+".png"));}
                    GrabQteOverlayRenderer.endTakeover();
                    TakeoverClientState.receive(new TakeoverStatePacket(id,0,0,-1,mc.player.getId(),100,100,1,false,false,true,0,0,1,0,12,0,"Carrier"));
                    var takeover=new TakeoverScreen(); takeover.init(mc,width,height);
                    for(int section=0;section<takeover.getCount();section++)
                        for(var text:takeover.tooltipsFor(section)) if(text.getString().contains("takeover.changed_synergy."))
                            throw new AssertionError("Permanent takeover warning still displays a translation key");
                    TakeoverClientState.clear();
                } finally { target.destroyBuffers(); mc.getMainRenderTarget().bindWrite(true); }
            }
            var form=net.ltxprogrammer.changed.process.ProcessTransfur.setPlayerTransfurVariant(mc.player,
                    net.ltxprogrammer.changed.init.ChangedTransfurVariants.PURE_WHITE_LATEX_WOLF.get());
            var tag=new net.minecraft.nbt.CompoundTag();tag.putBoolean("Adapted",true);
            ((net.parkabird.changedsynergy.ai.PureWhiteWolfAdaptation.PlayerState)form).changedSynergy$setPureWhiteAdaptation(tag);
            form.getChangedEntity().getBasicPlayerInfo().setLeftIrisColor(net.ltxprogrammer.changed.util.Color3.fromInt(0x44DD66));
            var visual=(net.ltxprogrammer.changed.entity.ChangedEntity)PureWhitePlayerAppearance.forRender(form.getChangedEntity());
            if(visual==form.getChangedEntity()||visual.getBasicPlayerInfo().getLeftIrisColor().toInt()!=0x44DD66)
                throw new AssertionError("Adapted player render model loses the player's individual eyes");
            System.out.println("OCTOBER18_UI_REGRESSION_PASS: actual roster/QTE screenshots at GUI scales 2 and 3, translated warning and individual adapted-player eyes");
        } finally {
            mc.setScreen(null); TakeoverClientState.clear(); GrabQteOverlayRenderer.endTakeover();
            mc.options.guiScale().set(oldScale); mc.resizeDisplay();
            model.popPose();RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(projection,sorting);
        }
    }
    private static java.lang.reflect.Field field(Object target,String name)throws Exception {
        Class<?> type=target instanceof Class<?> c?c:target.getClass();
        while(type!=null){try{var f=type.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){type=type.getSuperclass();}}
        throw new NoSuchFieldException(name);
    }
}
