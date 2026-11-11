package net.parkabird.changedsynergy.regression;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.client.StoryNpcScreen;
import net.parkabird.changedsynergy.client.DialogueTextAnimation;
import net.parkabird.changedsynergy.npc.*;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid="changed_synergy",value=Dist.CLIENT)
public final class StoryNpcClientRegression {
    private static int stage,language,checks;private static CompletableFuture<?> reload;
    private static final String[] LANGUAGES={"en_us","zh_cn","ru_ru"};
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||stage==9||!Boolean.getBoolean("changed_synergy.storyNpcClient"))return;
        var mc=Minecraft.getInstance();
        try {
            GLFW.glfwHideWindow(mc.getWindow().getWindow());
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
                mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(2);mc.options.simulationDistance().set(2);mc.options.guiScale().set(2);
                var rules=new GameRules();rules.getRule(net.parkabird.changedsynergy.init.ChangedSynergyGameRules.NATIVE_ORIGINS).set(false,null);rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
                mc.createWorldOpenFlows().createFreshLevel("npc-ui",new LevelSettings("npc-ui",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(20261103,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions());stage=1;
            }
            if(mc.level==null||mc.player==null||mc.player.tickCount<30||mc.getOverlay()!=null)return;
            if(Boolean.getBoolean("changed_synergy.desertUi")){DesertClientChecks.run(mc);stage=9;System.out.println("DESERT_CONTENT_CLIENT_PASS: anatomy, native clothing and rendered previews");mc.stop();return;}
            if(Boolean.getBoolean("changed_synergy.hypnosisUi")){hypnosisHud(mc);stage=9;System.out.println("HYPNOSIS_CLIENT_PASS: native icon is brighter while held than while latched");mc.stop();return;}
            if(stage==1){mc.getLanguageManager().setSelected(LANGUAGES[language]);mc.options.languageCode=LANGUAGES[language];reload=mc.reloadResourcePacks();stage=2;}
            if(stage==2&&reload.isDone()&&mc.getOverlay()==null) {
                reload.join();gallery(mc,LANGUAGES[language]);effects(mc,LANGUAGES[language]);performances(mc,LANGUAGES[language]);if(Boolean.getBoolean("changed_synergy.campUi"))campGallery(mc,LANGUAGES[language]);if(Boolean.getBoolean("changed_synergy.communityUi"))campRoster(mc,LANGUAGES[language]);
                if(++language<LANGUAGES.length)stage=1;
                else{stage=9;System.out.println("STORY_NPC_CLIENT_PASS: "+checks+" native layout, keyboard, text pagination, motion and three-language rendering checks");mc.stop();}
            }
        }catch(Throwable failure){stage=9;System.err.println("STORY_NPC_CLIENT_FAIL");failure.printStackTrace();mc.stop();}
    }
    private static void hypnosisHud(Minecraft mc)throws Exception {
        net.ltxprogrammer.changed.process.ProcessTransfur.setPlayerTransfurVariant(mc.player,net.ltxprogrammer.changed.init.ChangedTransfurVariants.LATEX_HYPNO_CAT.get(),null,1,false);
        var variant=net.ltxprogrammer.changed.process.ProcessTransfur.getPlayerTransfurVariant(mc.player);
        var selected=variant.getAbilityInstance(net.ltxprogrammer.changed.init.ChangedAbilities.HYPNOSIS.get());var controller=selected.getController();
        var directory=mc.gameDirectory.toPath().resolve("hypnosis-ui");java.nio.file.Files.createDirectories(directory);
        var screen=new net.minecraft.client.gui.screens.Screen(Component.literal("Hypnosis icon check")){
            @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
                net.ltxprogrammer.changed.client.ChangedClient.abilityRenderer.getOrThrow().renderAndDecorateAbility(g,mc.player,selected,128,60,64,1F,true,0);
            }
        };screen.init(mc,320,180);
        net.ltxprogrammer.changed.ability.AbstractAbility.UseType.HOLD.check(true,controller.exchangeKeyState(true),true,controller);
        render(mc,screen,new int[]{320,180},directory.resolve("held.png"));
        net.ltxprogrammer.changed.ability.AbstractAbility.UseType.HOLD.check(false,controller.exchangeKeyState(false),true,controller);
        check(controller.getProgressActive()==1,"client release retains activation");
        render(mc,screen,new int[]{320,180},directory.resolve("latched.png"));
        var held=javax.imageio.ImageIO.read(directory.resolve("held.png").toFile());var latched=javax.imageio.ImageIO.read(directory.resolve("latched.png").toFile());
        long bright=0,dim=0;int changed=0;
        for(int y=240;y<496;y++)for(int x=512;x<768;x++){
            int a=held.getRGB(x,y),b=latched.getRGB(x,y);bright+=(a>>16&255)+(a>>8&255)+(a&255);dim+=(b>>16&255)+(b>>8&255)+(b&255);if(a!=b)changed++;
        }
        check(changed>1000&&dim<bright*.95&&dim>bright*.4,"real rendered icon has a clear, moderate brightness difference; changed="+changed+", ratio="+((double)dim/bright));
        System.out.println("HYPNOSIS_ICON_PIXELS: "+changed+" changed pixels; latched/held luminance="+((double)dim/bright));
    }
    private static void campGallery(Minecraft mc,String lang)throws Exception {
        var path=mc.gameDirectory.toPath().resolve("camp-dialogue-samples.json");
        var samples=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path)).getAsJsonObject();check(samples.size()>=21,"real server supplies all chief and resident camp sample states");
        var directory=mc.gameDirectory.toPath().resolve("npc-ui/camp");java.nio.file.Files.createDirectories(directory);
        var y=StoryNpcScreen.class.getDeclaredField("y");y.setAccessible(true);var revealed=StoryNpcScreen.class.getDeclaredField("revealed");revealed.setAccessible(true);
        for(var entry:samples.entrySet())for(int[] size:List.of(new int[]{640,360},new int[]{426,240},new int[]{320,180})){
            var state=TagParser.parseTag(entry.getValue().getAsString());var screen=new StoryNpcScreen(state);screen.init(mc,size[0],size[1]);revealed.setBoolean(screen,true);
            check(y.getInt(screen)>=12,"camp dialogue fits: "+lang+entry.getKey());
            for(int step=0;step<=state.getList("Choices",10).size();step++){
                screen.keyPressed(GLFW.GLFW_KEY_DOWN,0,0);
                for(var child:screen.children())if(child instanceof AbstractWidget widget&&widget.visible){
                    check(widget.getY()>=12&&widget.getY()+widget.getHeight()<y.getInt(screen),"camp choices remain outside the body frame");
                    check(!widget.getMessage().getString().contains("%s")&&!widget.getMessage().getString().contains("camp.changed_synergy."),"dynamic quantities and item names are localized");
                    check(mc.font.split(widget.getMessage(),(int)((widget.getWidth()-20)/(size[0]<380?1F:1.25F))).size()*(size[0]<380?12:15)+8<=widget.getHeight(),"dynamic camp choice label wraps without truncation");
                }
            }
            if(size[0]==426&&(entry.getKey().endsWith("-request")||entry.getKey().endsWith("-donate")||entry.getKey().endsWith("-resident")||entry.getKey().endsWith("-outfit")))render(mc,screen,size,directory.resolve(entry.getKey()+"-"+lang+".png"));
        }
        System.out.println("CAMP_UI_LANGUAGE_PASS: "+lang+" "+samples.size()+" real server states at three GUI sizes");
    }
    private static void gallery(Minecraft mc,String lang)throws Exception {
        var directory=mc.gameDirectory.toPath().resolve("npc-ui");java.nio.file.Files.createDirectories(directory);
        controlsFade(mc,lang,directory);
        try(var input=StoryNpcClientRegression.class.getResourceAsStream("/data/changed_synergy/structures/observatory.nbt")) {
            var template=NbtIo.readCompressed(input);
            for(var value:template.getList("blocks",10)) {
                var book=((CompoundTag)value).getCompound("nbt").getCompound("Book");
                if(!book.getString("id").equals("minecraft:writable_book"))continue;
                for(var page:book.getCompound("tag").getList("pages",8))check(mc.font.split(Component.literal(page.getAsString()),114).size()*mc.font.lineHeight<=128,"observatory's authored journal fits vanilla pages without shortening");
            }
        }
        for(var profile:NpcDefinitions.all())for(var node:profile.nodes().values()) {
            for(var line:node.lines())for(var part:line.parts())check(net.minecraft.client.resources.language.I18n.exists(part.text()),"every conversation line is translated in "+lang);
            for(var choice:node.choices())check(net.minecraft.client.resources.language.I18n.exists(choice.text()),"every choice is translated in "+lang);
        }
        for(var profile:NpcDefinitions.all().stream().filter(NpcDefinitions.Profile::dialogue).toList())for(int[] size:List.of(new int[]{640,360},new int[]{426,240},new int[]{320,180})) {
            var node=profile.nodes().get("root");var state=state(profile,node,"root",true);var screen=new StoryNpcScreen(state);screen.init(mc,size[0],size[1]);
            check(!screen.isPauseScreen(),"world remains live");
            var started=StoryNpcScreen.class.getDeclaredField("started");started.setAccessible(true);started.setLong(screen,net.minecraft.Util.getMillis()-450);
            if(profile.chief())check(mc.font.width(Component.translatable("npc.changed_synergy.standing",-100))+80<=Math.min(680,size[0]-24),"trust fits the title strip even at the smallest GUI size");
            render(mc,screen,size,directory.resolve(profile.id().split(":")[1]+"-"+lang+"-"+size[0]+"-typing.png"));
            screen.mouseClicked(2,2,0);
            // Advance through locally paginated text without asking the server for a different line.
            var pageField=StoryNpcScreen.class.getDeclaredField("page");pageField.setAccessible(true);
            var revealed=StoryNpcScreen.class.getDeclaredField("revealed");revealed.setAccessible(true);
            var lastPage=StoryNpcScreen.class.getDeclaredMethod("lastPage");lastPage.setAccessible(true);
            int pages=0;while(!(boolean)lastPage.invoke(screen)&&pages++<100){screen.mouseClicked(2,2,0);screen.mouseClicked(2,2,0);}check(pages<100,"long text pagination terminates");revealed.setBoolean(screen,true);
            render(mc,screen,size,directory.resolve(profile.id().split(":")[1]+"-"+lang+"-"+size[0]+"-choices.png"));
            var yf=StoryNpcScreen.class.getDeclaredField("y");yf.setAccessible(true);var hf=StoryNpcScreen.class.getDeclaredField("h");hf.setAccessible(true);int bottom=yf.getInt(screen)+hf.getInt(screen);
            check(yf.getInt(screen)>=12&&bottom==size[1]-12,"dialogue frame stays anchored at the bottom");
            var controls=StoryNpcScreen.class.getDeclaredMethod("controlsY");controls.setAccessible(true);int controlsY=(int)controls.invoke(screen);
            check(controlsY>=12,"controls remain inside the viewport above the reply list");
            for(var child:screen.children())if(child instanceof AbstractWidget widget&&widget.visible) {
                check(widget.getX()>=0&&widget.getY()>=12&&widget.getX()+widget.getWidth()<=size[0]&&widget.getY()+widget.getHeight()<=yf.getInt(screen)-6,"choice is outside and above the bottom dialogue frame");
                check(controlsY+mc.font.lineHeight<widget.getY(),"the control row is above the replies with a clear gap");
                check(mc.font.split(widget.getMessage(),(int)((widget.getWidth()-20)/(size[0]<380?1F:1.25F))).size()*(size[0]<380?12:15)+8<=widget.getHeight(),"choice label is not truncated");
            }
            screen.keyPressed(GLFW.GLFW_KEY_DOWN,0,0);check(screen.getFocused()!=null,"keyboard focus is available");
            for(int n=0;n<node.choices().size()+2;n++){screen.keyPressed(GLFW.GLFW_KEY_DOWN,0,0);check(screen.getFocused() instanceof AbstractWidget widget&&widget.visible,"keyboard scrolling keeps focus visible");}
            boolean reduced=net.parkabird.changedsynergy.ChangedSynergyClientConfig.CLIENT.reducedUiMotion.get();net.parkabird.changedsynergy.ChangedSynergyClientConfig.CLIENT.reducedUiMotion.set(true);
            render(mc,screen,size,directory.resolve(profile.id().split(":")[1]+"-"+lang+"-"+size[0]+"-reduced.png"));net.parkabird.changedsynergy.ChangedSynergyClientConfig.CLIENT.reducedUiMotion.set(reduced);
            var mental=new StoryNpcScreen(state(profile,node,"root",true));mental.init(mc,size[0],size[1]);started.setLong(mental,net.minecraft.Util.getMillis()-110);render(mc,mental,size,directory.resolve(profile.id().split(":")[1]+"-"+lang+"-"+size[0]+"-mental.png"));
            revealed.setBoolean(mental,true);render(mc,mental,size,directory.resolve(profile.id().split(":")[1]+"-"+lang+"-"+size[0]+"-mental-complete.png"));
        }
        var glyphField=StoryNpcScreen.class.getDeclaredField("glyphs");glyphField.setAccessible(true);
        var timelineField=StoryNpcScreen.class.getDeclaredField("timeline");timelineField.setAccessible(true);
        for(var profile:NpcDefinitions.all())for(var entry:profile.nodes().entrySet())for(int[] size:List.of(new int[]{640,360},new int[]{426,240},new int[]{320,180})) {
            var screen=new StoryNpcScreen(state(profile,entry.getValue(),entry.getKey(),true));screen.init(mc,size[0],size[1]);
            var y=StoryNpcScreen.class.getDeclaredField("y");y.setAccessible(true);check(y.getInt(screen)>=12,"all node layouts fit the smallest supported viewport: "+entry.getKey());
            var glyphs=(List<?>)glyphField.get(screen);var timeline=(List<?>)timelineField.get(screen);boolean spoken=false,thought=false;long thoughtTime=-1;
            for(var glyph:glyphs) {
                var mental=glyph.getClass().getDeclaredMethod("mental");mental.setAccessible(true);
                if((boolean)mental.invoke(glyph)) {
                    thought=true;var index=glyph.getClass().getDeclaredMethod("index");index.setAccessible(true);long time=(long)timeline.get((int)index.invoke(glyph));
                    check(thoughtTime<0||time==thoughtTime,"an entire thought fades together rather than typing character by character");thoughtTime=time;
                }else spoken=true;
            }
            if(entry.getValue().lines().stream().anyMatch(l->l.channel().equals("mental")))check(spoken&&thought,"speech and its thought share one client passage");
            var last=StoryNpcScreen.class.getDeclaredMethod("lastPage");last.setAccessible(true);
            var reveal=StoryNpcScreen.class.getDeclaredField("revealed");reveal.setAccessible(true);
            int pageCount=0;
            while(!(boolean)last.invoke(screen)&&pageCount++<100){screen.mouseClicked(2,2,0);screen.mouseClicked(2,2,0);}
            check(pageCount<100,"every translated passage finishes pagination: "+lang+"/"+entry.getKey());
            reveal.setBoolean(screen,true);
            for(int choice=0;choice<=entry.getValue().choices().size();choice++) {
                screen.keyPressed(GLFW.GLFW_KEY_DOWN,0,0);
                for(var child:screen.children())if(child instanceof AbstractWidget widget&&widget.visible) {
                    check(widget.getY()>=12&&widget.getY()+widget.getHeight()<=y.getInt(screen)-6,"all translated replies stay above the frame: "+lang+"/"+entry.getKey());
                    check(mc.font.split(widget.getMessage(),(int)((widget.getWidth()-20)/(size[0]<380?1F:1.25F))).size()*(size[0]<380?12:15)+8<=widget.getHeight(),"all translated reply labels fit: "+lang+"/"+entry.getKey());
                }
            }
            if(lang.equals("ru_ru")&&Set.of("enrol","explain_form","confirm_permanent").contains(entry.getKey()))
                render(mc,screen,size,directory.resolve(profile.id().split(":")[1]+"-"+entry.getKey()+"-"+lang+"-"+size[0]+".png"));
        }
    }
    private static void controlsFade(Minecraft mc,String lang,java.nio.file.Path directory)throws Exception {
        var profile=NpcDefinitions.get("changed_synergy:canopy_chief");var screen=new StoryNpcScreen(state(profile,profile.nodes().get("root"),"root",true));
        int[] size={426,240};screen.init(mc,size[0],size[1]);
        var reveal=StoryNpcScreen.class.getDeclaredField("revealed");reveal.setAccessible(true);
        var shown=StoryNpcScreen.class.getDeclaredField("actionsShownAt");shown.setAccessible(true);
        var y=StoryNpcScreen.class.getDeclaredField("y");y.setAccessible(true);
        var controls=StoryNpcScreen.class.getDeclaredMethod("controlsY");controls.setAccessible(true);
        boolean old=net.parkabird.changedsynergy.ChangedSynergyClientConfig.CLIENT.reducedUiMotion.get();
        net.parkabird.changedsynergy.ChangedSynergyClientConfig.CLIENT.reducedUiMotion.set(false);
        try {
            render(mc,screen,size,directory.resolve("controls-"+lang+"-typing.png"));
            reveal.setBoolean(screen,true);
            for(String step:List.of("hidden","half","full")) {
                shown.setLong(screen,step.equals("hidden")?-1:net.minecraft.Util.getMillis()-(step.equals("half")?90:250));
                render(mc,screen,size,directory.resolve("controls-"+lang+"-"+step+".png"));
            }
            var hidden=javax.imageio.ImageIO.read(directory.resolve("controls-"+lang+"-hidden.png").toFile());
            var half=javax.imageio.ImageIO.read(directory.resolve("controls-"+lang+"-half.png").toFile());
            var full=javax.imageio.ImageIO.read(directory.resolve("controls-"+lang+"-full.png").toFile());
            int cy=(int)controls.invoke(screen),by=y.getInt(screen);
            for(int[] region:List.of(new int[]{cy,cy+16},new int[]{cy+16,by-6})){
                long middle=0,end=0;int changes=0;
                for(int yy=region[0]*3;yy<region[1]*3;yy++)for(int xx=70;xx<1210;xx++){
                    int a=hidden.getRGB(xx,yy),b=half.getRGB(xx,yy),c=full.getRGB(xx,yy);
                    for(int shift:new int[]{0,8,16}){middle+=Math.abs((a>>shift&255)-(b>>shift&255));end+=Math.abs((a>>shift&255)-(c>>shift&255));}
                    if(a!=c)changes++;
                }
                check(changes>50&&middle>0&&middle<end*.8,"real controls and reply pixels share the restrained fade: "+lang+Arrays.toString(region));
            }
            net.parkabird.changedsynergy.ChangedSynergyClientConfig.CLIENT.reducedUiMotion.set(true);shown.setLong(screen,-1);
            render(mc,screen,size,directory.resolve("controls-"+lang+"-reduced.png"));
            var reduced=javax.imageio.ImageIO.read(directory.resolve("controls-"+lang+"-reduced.png").toFile());
            check(reduced.getRGB(100,cy*3)==full.getRGB(100,cy*3),"reduced motion retains the finished controls without an extra transition");
        } finally {net.parkabird.changedsynergy.ChangedSynergyClientConfig.CLIENT.reducedUiMotion.set(old);}
    }
    private static CompoundTag state(NpcDefinitions.Profile profile,NpcDefinitions.Node node,String id,boolean mental) {
        var state=new CompoundTag();state.putUUID("Session",UUID.randomUUID());state.putInt("Revision",1);state.putString("Profile",profile.id());state.putString("Name",Component.Serializer.toJson(Component.translatable("npc."+profile.id().replace(':','.')+".title")));state.putString("Title","npc."+profile.id().replace(':','.')+".title");state.putString("Node",id);state.putString("Channel",mental?"mental":"spoken");state.putBoolean("Last",true);state.putBoolean("Chief",profile.chief());state.putInt("Trust",25);
        var creature=(net.ltxprogrammer.changed.entity.ChangedEntity)net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(net.minecraft.resources.ResourceLocation.parse(profile.species())).create(Minecraft.getInstance().level);
        var colors=creature.getSelfVariant().getColors();state.putInt("Background",colors.getFirst().toInt());state.putInt("Foreground",colors.getSecond().toInt());
        var parts=new ListTag();var line=node.lines().stream().filter(l->!l.channel().equals("mental")).reduce((a,b)->b).orElseThrow();var passage=new ArrayList<NpcDefinitions.Line>();passage.add(line);
        int following=node.lines().indexOf(line)+1;
        if(mental&&following<node.lines().size()&&node.lines().get(following).channel().equals("mental"))passage.add(node.lines().get(following));
        for(var content:passage)for(int i=0;i<content.parts().size();i++){var p=content.parts().get(i);var part=new CompoundTag();part.putString("Text",p.text());part.putString("Channel",content.channel());part.putBoolean("Beat",p.beat());part.putBoolean("Break",i==0&&!parts.isEmpty());part.putString("Effect",p.effect());part.putInt("Pause",p.pause());part.putString("Emotion",p.emotion());part.putFloat("Speed",p.speed());part.putFloat("Intensity",p.intensity());parts.add(part);}state.put("Parts",parts);
        var choices=new ListTag();for(var c:node.choices()){var choice=new CompoundTag();choice.putString("Id",c.id());choice.putString("Text",c.text());choices.add(choice);}state.put("Choices",choices);return state;
    }
    private static void effects(Minecraft mc,String lang)throws Exception {
        var directory=mc.gameDirectory.toPath().resolve("npc-ui/effects");java.nio.file.Files.createDirectories(directory);
        var started=StoryNpcScreen.class.getDeclaredField("started");started.setAccessible(true);
        var revealed=StoryNpcScreen.class.getDeclaredField("revealed");revealed.setAccessible(true);
        var glyphs=StoryNpcScreen.class.getDeclaredField("glyphs");glyphs.setAccessible(true);
        var duration=StoryNpcScreen.class.getDeclaredField("totalDuration");duration.setAccessible(true);
        var panelHeight=StoryNpcScreen.class.getDeclaredField("h");panelHeight.setAccessible(true);
        var brief=new StoryNpcScreen(demo("neutral","A short answer."));brief.init(mc,426,240);
        var lengthy=new StoryNpcScreen(demo("neutral","A longer answer needs more room while keeping its words intact. ".repeat(18)));lengthy.init(mc,426,240);
        check(panelHeight.getInt(brief)<panelHeight.getInt(lengthy),"short dialogue uses a smaller frame than a full paragraph");
        var colors=StoryNpcScreen.class.getDeclaredField("textColors");colors.setAccessible(true);var palette=new HashSet<Integer>();
        for(String mood:List.of("neutral","happy","shy","sad","curious")) {
            var screen=new StoryNpcScreen(demo(mood,"Words keep their emotional color."));screen.init(mc,426,240);revealed.setBoolean(screen,true);
            render(mc,screen,new int[]{426,240},directory.resolve(lang+"-palette-"+mood+".png"));
            palette.addAll(((Map<net.parkabird.changedsynergy.npc.NpcTextStyle.Style,Integer>)colors.get(screen)).values());
        }
        check(palette.size()>=4,"dialogue emotion presets produce distinct readable colors instead of all-white text");
        for(var effect:NpcTextStyle.Effect.values())for(float intensity:new float[]{0,1,2})for(long time:new long[]{0,40,180,600,10000}) {
            var style=NpcTextStyle.resolve("neutral",effect.name().toLowerCase(Locale.ROOT),1,intensity);
            var sample=DialogueTextAnimation.sample(style,time,time,7,false,false);
            check(Float.isFinite(sample.x())&&Float.isFinite(sample.y())&&Math.abs(sample.y())<=2.1F&&sample.scale()>.8F&&sample.scale()<1.2F&&Math.abs(sample.rotation())<6,"glyph poses stay finite inside the reserved line space");
            var still=DialogueTextAnimation.sample(style,time,time,7,true,false);
            check(still.x()==0&&still.y()==0&&still.scale()==1&&still.rotation()==0,"Reduced UI Motion removes every spatial effect");
        }
        String[][] examples={
            {"neutral","It's clear tonight.","今晚天很晴。"},
            {"calm","Take your time. I'm listening.","慢慢来，我在听。"},
            {"curious","Is that what you saw up there?","那就是你在上面看到的吗？"},
            {"happy","You're back! Come and sit with us.","你回来了！过来一起坐吧。"},
            {"excited","I knew you could do it!","我就知道你能做到！"},
            {"angry","Stop. Leave them alone.","住手。离他们远点。"},
            {"fearful","Wait... did you hear that?","等等……你听到了吗？"},
            {"sad","I thought they would come back.","我以为他们还会回来的。"},
            {"shy","I... may have left another pawprint.","我……可能又按了个爪印。"},
            {"whisper","Quiet. Someone is sleeping.","轻一点，有人在睡觉。"},
            {"surprised","Hoo! You startled me.","咕！你吓了我一跳。"},
            {"determined","Stay behind me. I'll handle this.","到我身后来，这里交给我。"},
            {"dreamy","They look like little campfires.","它们看起来像一簇簇小篝火。"}};
        for(var example:examples) {
            var data=demo(example[0],example[lang.equals("zh_cn")?2:1]);
            var screen=new StoryNpcScreen(data);screen.init(mc,426,240);var layout=List.copyOf((List<?>)glyphs.get(screen));
            int frames=lang.equals("zh_cn")?36:1;
            for(int i=0;i<frames;i++) {
                started.setLong(screen,net.minecraft.Util.getMillis()-(frames==1?450:i*80));
                render(mc,screen,new int[]{426,240},directory.resolve(lang+"-"+example[0]+"-"+String.format(Locale.ROOT,"%02d",i)+".png"));
                check(layout.equals(glyphs.get(screen)),"animation never changes wrapping or glyph anchors");
            }
            revealed.setBoolean(screen,true);render(mc,screen,new int[]{426,240},directory.resolve(lang+"-"+example[0]+"-complete.png"));
            var motion=net.parkabird.changedsynergy.ChangedSynergyClientConfig.CLIENT.reducedUiMotion;boolean old=motion.get();motion.set(true);
            render(mc,screen,new int[]{426,240},directory.resolve(lang+"-"+example[0]+"-reduced.png"));motion.set(old);
        }
        var normal=new StoryNpcScreen(demo("neutral","abcdefghijklmnop"));var quick=new StoryNpcScreen(demo("excited","abcdefghijklmnop"));var slow=new StoryNpcScreen(demo("whisper","abcdefghijklmnop"));
        check(duration.getLong(quick)<duration.getLong(normal)&&duration.getLong(normal)<duration.getLong(slow),"emotion controls speaking speed");
        var multipart=demo("neutral","");var parts=multipart.getList("Parts",10);parts.clear();
        for(String text:List.of("A ","😀"," 又一个爪印。")){var p=new CompoundTag();p.putString("Text",text);p.putString("Emotion","shy");p.putString("Effect","auto");p.putFloat("Speed",1);p.putFloat("Intensity",1);parts.add(p);}
        var mixed=new StoryNpcScreen(multipart);mixed.init(mc,320,180);check(((List<?>)glyphs.get(mixed)).size()>5,"mixed UTF-16 and CJK spans render without losing characters");
        var beats=demo("calm","One complete idea.");var beatParts=beats.getList("Parts",10);
        var next=new CompoundTag();next.putString("Text","A different idea.");next.putBoolean("Beat",true);beatParts.add(next);
        var aside=new CompoundTag();aside.putString("Text","(Nobody saw that.)");aside.putString("Channel","mental");aside.putBoolean("Break",true);beatParts.add(aside);
        var paced=new StoryNpcScreen(beats);paced.init(mc,640,360);
        var pagesField=StoryNpcScreen.class.getDeclaredField("pages");pagesField.setAccessible(true);var pages=(List<?>)pagesField.get(paced);
        check(pages.size()==2,"an authored change of idea turns the page even when all words fit one frame");
        var fullField=StoryNpcScreen.class.getDeclaredField("full");fullField.setAccessible(true);check(((String)fullField.get(paced)).contains("A different idea.\nNobody saw that."),"the aside remains attached to its spoken idea without wrapper parentheses");
        var pageField=StoryNpcScreen.class.getDeclaredField("page");pageField.setAccessible(true);pageField.setInt(paced,1);
        var fit=StoryNpcScreen.class.getDeclaredMethod("fitPanel");fit.setAccessible(true);fit.invoke(paced);revealed.setBoolean(paced,true);
        render(mc,paced,new int[]{640,360},directory.resolve(lang+"-authored-beat-and-thought.png"));
        var wrapping=demo("neutral","Keep (spoken parentheses).");var wrappingParts=wrapping.getList("Parts",10);
        for(String text:List.of("（","Only (these words)","）")){var part=new CompoundTag();part.putString("Text",text);part.putString("Channel","mental");part.putBoolean("Break",wrappingParts.size()==1);wrappingParts.add(part);}
        var brackets=new StoryNpcScreen(wrapping);brackets.init(mc,640,360);
        check(fullField.get(brackets).equals("Keep (spoken parentheses).\nOnly (these words)"),"only matched outer thought brackets are removed, across fragment boundaries and in both alphabets");
        var accented=NpcTextStyle.resolve("excited","shout",1,.95F);var quiet=NpcTextStyle.resolve("calm","none",1,0);
        var hit=DialogueTextAnimation.sample(accented,100,100,0,false,false);var still=DialogueTextAnimation.sample(quiet,100,100,0,false,false);
        check(hit.scale()>1.05F&&Math.abs(hit.y())>.5F&&still.scale()==1&&still.y()==0,"a local exclamation has a visible bounded accent while quiet narration remains steady");
    }
    private static void performances(Minecraft mc,String lang)throws Exception {
        var directory=mc.gameDirectory.toPath().resolve("npc-ui/performances");java.nio.file.Files.createDirectories(directory);
        var page=StoryNpcScreen.class.getDeclaredField("page");page.setAccessible(true);
        var pages=StoryNpcScreen.class.getDeclaredField("pages");pages.setAccessible(true);
        var fit=StoryNpcScreen.class.getDeclaredMethod("fitPanel");fit.setAccessible(true);
        var started=StoryNpcScreen.class.getDeclaredField("started");started.setAccessible(true);
        var end=StoryNpcScreen.class.getDeclaredMethod("pageEnd");end.setAccessible(true);
        var thoughtColors=StoryNpcScreen.class.getDeclaredField("thoughtColors");thoughtColors.setAccessible(true);
        for(String[] example:List.of(new String[]{"canopy_chief","welcome_copy"},new String[]{"hyena_chief","past"},new String[]{"observatory_chief","root"},new String[]{"lab_researcher","root"},new String[]{"lab_researcher","crystal"})) {
            var profile=NpcDefinitions.get("changed_synergy:"+example[0]);var node=profile.nodes().get(example[1]);
            if(example[1].equals("root"))node=new NpcDefinitions.Node(node.lines().subList(0,2),node.choices());
            var screen=new StoryNpcScreen(state(profile,node,example[1],true));screen.init(mc,640,360);
            page.setInt(screen,((List<?>)pages.get(screen)).size()-1);fit.invoke(screen);
            long finish=(long)end.invoke(screen);
            for(int frame=0;frame<24;frame++) {
                started.setLong(screen,net.minecraft.Util.getMillis()-Math.max(0,finish-520+frame*80));
                render(mc,screen,new int[]{640,360},directory.resolve(example[0]+"-"+example[1]+"-"+lang+"-"+String.format(Locale.ROOT,"%02d",frame)+".png"));
            }
            var colors=new HashSet<>(((Map<?,?>)thoughtColors.get(screen)).values());
            if(example[0].equals("lab_researcher")&&example[1].equals("root"))check(colors.size()>=3,"the researcher's excited, self-conscious and restrained thoughts retain distinct teal-based emotion colors");
        }
    }
    private static CompoundTag demo(String emotion,String text) {
        var data=new CompoundTag();data.putUUID("Session",UUID.randomUUID());data.putInt("Revision",1);data.putString("Profile","changed_synergy:observatory_chief");
        data.putString("Name",Component.Serializer.toJson(Component.literal(emotion)));data.putString("Title","npc.changed_synergy.dialogue");data.putString("Channel","spoken");data.putBoolean("Last",true);
        var part=new CompoundTag();part.putString("Text",text);part.putString("Effect","auto");part.putString("Emotion",emotion);part.putFloat("Speed",1);part.putFloat("Intensity",1);
        var parts=new ListTag();parts.add(part);data.put("Parts",parts);data.put("Choices",new ListTag());return data;
    }
    private static void campRoster(Minecraft mc,String lang)throws Exception {
        var directory=mc.gameDirectory.toPath().resolve("npc-ui/community");java.nio.file.Files.createDirectories(directory);
        check(net.minecraft.client.resources.language.I18n.exists("menu.changed_synergy.relationship.camp_badge"),"camp badge is translated");
        for(int kind:List.of(0,1,2))for(int[] size:List.of(new int[]{640,360},new int[]{426,240},new int[]{320,180})) {
            boolean bond=kind>0,campmate=kind!=1;
            var contact=new net.parkabird.changedsynergy.ai.PlayerRelationshipSettings.Contact(UUID.randomUUID(),-1,"Canopy Neighbor","changed_synergy:latex_silk_moth","minecraft:overworld",true,120,64,120,bond,false,true,false,false,bond?60:1,24,24,bond,campmate);
            var menu=new net.parkabird.changedsynergy.world.inventory.PlayerRelationshipMenu(3,mc.player.getInventory(),List.of(contact));
            var screen=new net.parkabird.changedsynergy.client.PlayerRelationshipScreen(menu,mc.player.getInventory(),menu.TITLE);mc.setScreen(screen);screen.init(mc,size[0],size[1]);
            for(Class<?> type=screen.getClass();type!=null;type=type.getSuperclass())try{var field=type.getDeclaredField("changedSynergy$openedAtNanos");field.setAccessible(true);field.setLong(screen,System.nanoTime()-2_000_000_000L);break;}catch(NoSuchFieldException ignored){}
            var entrance=screen.getClass().getDeclaredField("panelsOpenedAt");entrance.setAccessible(true);entrance.setLong(screen,System.nanoTime()-2_000_000_000L);
            for(Class<?> type=screen.getClass();type!=null;type=type.getSuperclass())for(var field:type.getDeclaredFields()){
                if(field.getType()!=net.parkabird.changedsynergy.client.UiSpring.class&&!field.getType().equals(net.parkabird.changedsynergy.client.UiSpring[].class))continue;
                field.setAccessible(true);Object value=field.get(screen);
                if(value instanceof net.parkabird.changedsynergy.client.UiSpring spring&&(field.getName().endsWith("visibility")||field.getName().endsWith("panels")||field.getName().endsWith("center")))spring.snap(1,System.nanoTime());
                if(value instanceof net.parkabird.changedsynergy.client.UiSpring[] springs)for(var spring:springs)spring.snap(1,System.nanoTime());
            }
            var path=directory.resolve((kind==2?"bonded-campmate":bond?"bonded":"campmate")+"-"+lang+"-"+size[0]+".png");render(mc,screen,size,path);
            var raster=javax.imageio.ImageIO.read(path.toFile());var colors=new HashSet<Integer>();for(int px=0;px<1280;px+=8)for(int py=0;py<720;py+=8)colors.add(raster.getRGB(px,py));check(colors.size()>12,"native roster actually renders past its opening fade");
            mc.setScreen(null);
        }
        System.out.println("COMMUNITY_UI_LANGUAGE_PASS: "+lang+" campmate and bond roster at three GUI sizes");
    }
    private static void render(Minecraft mc,net.minecraft.client.gui.screens.Screen screen,int[] size,java.nio.file.Path path)throws Exception {
        double oldScale=mc.getWindow().getGuiScale();mc.getWindow().setGuiScale(1280D/size[0]);
        var target=new TextureTarget(1280,720,true,Minecraft.ON_OSX);var mv=RenderSystem.getModelViewStack();mv.pushPose();
        try {
            mv.setIdentity();mv.translate(0,0,-11000);RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,size[0],size[1],0,1000,21000),VertexSorting.ORTHOGRAPHIC_Z);
            target.setClearColor(.10F,.14F,.12F,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
            var g=new GuiGraphics(mc,mc.renderBuffers().bufferSource());screen.render(g,-10,-10,0);g.flush();
            try(var image=net.minecraft.client.Screenshot.takeScreenshot(target)){image.writeToFile(path);}
        }finally{mv.popPose();RenderSystem.applyModelViewMatrix();target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);mc.getWindow().setGuiScale(oldScale);}
    }
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
}
