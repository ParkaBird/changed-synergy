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
                    for (int key=0;key<4;key++) {
                        GrabQteOverlayRenderer.endTakeover();
                        GrabQteOverlayRenderer.renderTakeover(graphics,width,height,id,key,3,12,-1);
                        field(GrabQteOverlayRenderer.class,"takeoverStartedAt").setLong(null,net.minecraft.Util.getMillis()-1000);
                        field(GrabQteOverlayRenderer.class,"takeoverKeyStartedAt").setLong(null,net.minecraft.Util.getMillis()-1000);
                        target.clear(Minecraft.ON_OSX); target.bindWrite(true);
                        GrabQteOverlayRenderer.renderTakeover(graphics,width,height,id,key,3,12,-1); graphics.flush();
                        String name=key==1?"grab-qte-alignment-scale-":"grab-qte-key-"+key+"-scale-";
                        try(var image=net.minecraft.client.Screenshot.takeScreenshot(target)){image.writeToFile(directory.resolve(name+scale+".png"));}
                    }
                    GrabQteOverlayRenderer.endTakeover();
                    TakeoverClientState.receive(new TakeoverStatePacket(id,0,0,-1,mc.player.getId(),100,100,1,false,false,true,0,0,1,0,12,0,"Carrier"));
                    var takeover=new TakeoverScreen(); takeover.init(mc,width,height);
                    for(int section=0;section<takeover.getCount();section++)
                        for(var text:takeover.tooltipsFor(section)) if(text.getString().contains("takeover.changed_synergy."))
                            throw new AssertionError("Permanent takeover warning still displays a translation key");
                    TakeoverClientState.clear();
                    gatheringCards(mc,directory,target,scale);
                } finally { target.destroyBuffers(); mc.getMainRenderTarget().bindWrite(true); }
            }
            var form=net.ltxprogrammer.changed.process.ProcessTransfur.setPlayerTransfurVariant(mc.player,
                    net.ltxprogrammer.changed.init.ChangedTransfurVariants.PURE_WHITE_LATEX_WOLF.get());
            var tag=new net.minecraft.nbt.CompoundTag();tag.putBoolean("Adapted",true);
            ((net.parkabird.changedsynergy.ai.PureWhiteWolfAdaptation.PlayerState)form).changedSynergy$setPureWhiteAdaptation(tag);
            form.getChangedEntity().getBasicPlayerInfo().setLeftIrisColor(net.ltxprogrammer.changed.util.Color3.fromInt(0x44DD66));
            var body = form.getChangedEntity();
            if (!net.parkabird.changedsynergy.ai.PureWhiteWolfAdaptation.isAdaptedForm(body)
                    || body.getType()!=net.ltxprogrammer.changed.init.ChangedEntities.PURE_WHITE_LATEX_WOLF.get()
                    || body.getBasicPlayerInfo().getLeftIrisColor().toInt()!=0x44DD66)
                throw new AssertionError("Eye adaptation changed the original pure-white body or its eyes");
            var renderer = mc.getEntityRenderDispatcher().getRenderer(body);
            java.util.List<?> layers = net.minecraftforge.fml.util.ObfuscationReflectionHelper.getPrivateValue(
                    net.minecraft.client.renderer.entity.LivingEntityRenderer.class,
                    (net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?>)renderer, "f_115291_");
            if(layers.stream().filter(PureWhiteAdaptedEyesLayer.class::isInstance).count()!=1)
                throw new AssertionError("Pure White renderer must have exactly one conditional native eyes layer");
            renderer.render(body,0,1,new com.mojang.blaze3d.vertex.PoseStack(),mc.renderBuffers().bufferSource(),15728880);
            mc.renderBuffers().bufferSource().endBatch();
            WhiteEyesClientRegression.run(mc,directory);
            HeldCreatureClientRegression.run(mc,directory);
            behaviorCard(mc,directory);
            WheelIconClientRegression.run(mc,directory);
            System.out.println("OCTOBER18_UI_REGRESSION_PASS: actual roster/QTE screenshots at GUI scales 2 and 3, translated warning and individual adapted-player eyes");
        } finally {
            mc.setScreen(null); TakeoverClientState.clear(); GrabQteOverlayRenderer.endTakeover();
            mc.options.guiScale().set(oldScale); mc.resizeDisplay();
            model.popPose();RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(projection,sorting);
        }
    }
    private static void gatheringCards(Minecraft mc,java.nio.file.Path directory,
            com.mojang.blaze3d.pipeline.TextureTarget target,int scale)throws Exception {
        var types=net.parkabird.changedsynergy.ai.PlayerOutpostSupplyType.values();
        for(boolean backup:new boolean[]{false,true}) for(int selected=0;selected<types.length;selected++) {
            var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            net.parkabird.changedsynergy.world.inventory.PlayerOutpostMenu menu;
            try {
                buffer.writeUtf("minecraft:overworld");buffer.writeBlockPos(new net.minecraft.core.BlockPos(0,64,0));
                buffer.writeUtf("1, 64, 0");buffer.writeUtf("6 / 6");buffer.writeVarInt(32);buffer.writeBoolean(true);
                buffer.writeUtf("✓ / ✓ / ✓ / ✓ / ✓ / ✓");buffer.writeBoolean(true);buffer.writeVarInt(selected);
                buffer.writeVarInt(1);buffer.writeUtf("minecraft:overworld");buffer.writeVarInt(types.length);
                for(var type:types) {
                    buffer.writeUUID(UUID.randomUUID());buffer.writeUtf("Mica "+type.name());
                    buffer.writeVarInt(net.parkabird.changedsynergy.ai.PlayerOutpostData.Role.SUPPLY.ordinal());
                    buffer.writeVarInt(type.ordinal());buffer.writeUtf("minecraft:overworld");
                    buffer.writeBlockPos(new net.minecraft.core.BlockPos(2,64,2));buffer.writeBoolean(true);
                    buffer.writeVarInt((backup?net.parkabird.changedsynergy.ai.PlayerOutpostData.Work.FARM:
                            net.parkabird.changedsynergy.ai.PlayerOutpostData.Work.GATHER).ordinal());
                    buffer.writeVarInt((backup?net.parkabird.changedsynergy.ai.PlayerOutpostData.Work.GATHER:
                            net.parkabird.changedsynergy.ai.PlayerOutpostData.Work.FARM).ordinal());
                    buffer.writeVarInt(0);buffer.writeVarInt(net.parkabird.changedsynergy.ai.PlayerOutpostData.CrewJob.COMBAT.ordinal());
                }
                menu=new net.parkabird.changedsynergy.world.inventory.PlayerOutpostMenu(8,mc.player.getInventory(),buffer);
            } finally {buffer.release();}
            var screen=new PlayerOutpostScreen(menu,mc.player.getInventory(),
                    net.parkabird.changedsynergy.world.inventory.PlayerOutpostMenu.TITLE);mc.setScreen(screen);
            var gather=(net.minecraft.client.gui.components.Button)field(screen,backup?"backupWork":"mainWork").get(screen);
            var other=(net.minecraft.client.gui.components.Button)field(screen,backup?"mainWork":"backupWork").get(screen);
            String resource=Component.translatable("menu.changed_synergy.player_outpost.supply."
                    +types[selected].name().toLowerCase(java.util.Locale.ROOT)).getString();
            if(!gather.getMessage().getString().contains(resource)||other.getMessage().getString().contains(resource))
                throw new AssertionError("Gathering target missing or applied to non-gathering work: "+types[selected]);
            if(!backup && selected==0) {
                var rows=(java.util.List<?>)field(screen,"rows").get(screen);
                if(!((net.minecraft.client.gui.components.Button)rows.get(0)).getMessage().getString().contains(resource))
                    throw new AssertionError("Roster gathering target missing");
            }
            if(!backup && (selected==0||selected==2)) {
                target.setClearColor(.10F,.12F,.15F,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
                var graphics=new GuiGraphics(mc,mc.renderBuffers().bufferSource());screen.render(graphics,0,0,1);graphics.flush();
                try(var picture=net.minecraft.client.Screenshot.takeScreenshot(target)){
                    picture.writeToFile(directory.resolve("outpost-gather-"+types[selected].name().toLowerCase(java.util.Locale.ROOT)+"-scale-"+scale+".png"));
                }
            }
            mc.setScreen(null);
        }
        System.out.println("OUTPOST_GATHER_TARGET_CLIENT_REGRESSION_PASS: six supply types, main/backup work, roster and unchanged non-gathering labels; GUI scale "+scale);
    }
    private static void behaviorCard(Minecraft mc,java.nio.file.Path directory)throws Exception {
        var creature=net.ltxprogrammer.changed.init.ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(mc.level);
        var menu=new net.parkabird.changedsynergy.world.inventory.SocialInteractionMenu(7,mc.player.getInventory(),creature);
        menu.getProgression().putInt("BehaviorTrait",net.parkabird.changedsynergy.ai.CreaturePersonality.Trait.PLAYFUL.ordinal());
        if(menu.getBehaviorTrait()!=net.parkabird.changedsynergy.ai.CreaturePersonality.Trait.PLAYFUL)
            throw new AssertionError("The separate Playful behavior display is missing");
        var screen=new SocialInteractionScreen(menu,mc.player.getInventory(),Component.literal("Behavior"));mc.setScreen(screen);
        field(screen,"changedSynergy$openedAtNanos").setLong(screen,System.nanoTime()-2_000_000_000L);
        var target=new com.mojang.blaze3d.pipeline.TextureTarget(mc.getWindow().getWidth(),mc.getWindow().getHeight(),true,Minecraft.ON_OSX);
        try {
            target.setClearColor(.10F,.12F,.15F,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
            var graphics=new GuiGraphics(mc,mc.renderBuffers().bufferSource());screen.render(graphics,0,0,1);graphics.flush();
            try(var picture=net.minecraft.client.Screenshot.takeScreenshot(target)){picture.writeToFile(directory.resolve("playful-behavior-information.png"));}
            System.out.println("PLAYFUL_BEHAVIOR_CLIENT_REGRESSION_PASS: relationship information renders the separate Playful behavior trait");
        } finally {mc.setScreen(null);creature.discard();target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);}
    }
    private static java.lang.reflect.Field field(Object target,String name)throws Exception {
        Class<?> type=target instanceof Class<?> c?c:target.getClass();
        while(type!=null){try{var f=type.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){type=type.getSuperclass();}}
        throw new NoSuchFieldException(name);
    }
}
