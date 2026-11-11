package net.parkabird.changedsynergy.client;

import java.util.*;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.parkabird.changedsynergy.ChangedSynergyClientConfig;
import net.parkabird.changedsynergy.network.*;
import net.parkabird.changedsynergy.npc.NpcTextStyle;
import com.mojang.math.Axis;
import org.lwjgl.glfw.GLFW;

/** Reserved text geometry keeps word wrapping stable throughout the speaking animation. */
public final class StoryNpcScreen extends Screen {
    private static final Set<UUID> canceledSessions=new LinkedHashSet<>();
    private static final int BODY_TOP=32,BODY_BOTTOM=12,CHOICE_GAP=6,CONTROLS_HEIGHT=16;
    private static final int CHOICE_TEXT=0xF2E6D1;
    private static final NpcTextStyle.Style NORMAL=NpcTextStyle.resolve("neutral","none",1,1);
    private record Span(int start,int end,NpcTextStyle.Style style,boolean mental) {}
    private record Glyph(String text,int index,int x,int row,NpcTextStyle.Style style,boolean mental) {}
    private record TextPage(int firstRow,int rows,long start,long end) {}
    private CompoundTag state;private String full="";private final List<Span> spans=new ArrayList<>();
    private final List<Glyph> glyphs=new ArrayList<>();private final List<Long> timeline=new ArrayList<>();
    private final List<Integer> beats=new ArrayList<>();private final List<TextPage> pages=new ArrayList<>();
    private final List<ChoiceButton> choices=new ArrayList<>();
    private int x,y,w,h,textRows,page,choiceOffset,selected,visibleChoices,rowHeight,textRowHeight,choiceTextRow;private float textScale;
    private long started,totalDuration;private boolean revealed,waiting;
    private long actionsShownAt=-1;private float actionsAlpha;
    private int accent,background,textColor,headerColor,bodySurface;
    private boolean organic;
    private final Map<NpcTextStyle.Style,Integer> textColors=new HashMap<>();
    private final Map<NpcTextStyle.Style,Integer> thoughtColors=new HashMap<>();
    public StoryNpcScreen(CompoundTag state){super(Component.translatable("npc.changed_synergy.dialogue"));update(state);}
    public static void receive(CompoundTag state) {
        var mc=Minecraft.getInstance();
        if(state.isEmpty()){if(mc.screen instanceof StoryNpcScreen)mc.setScreen(null);return;}
        if(canceledSessions.contains(state.getUUID("Session")))return;
        if(mc.screen instanceof StoryNpcScreen screen){screen.update(state);screen.init(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());}
        else mc.setScreen(new StoryNpcScreen(state));
    }
    private void update(CompoundTag state) {
        this.state=state.copy();started=Util.getMillis();page=0;choiceOffset=0;selected=-1;revealed=false;waiting=false;actionsShownAt=-1;actionsAlpha=0;
        full="";spans.clear();timeline.clear();beats.clear();beats.add(0);long time=0,lineTime=0;var textBuilder=new StringBuilder();
        var parts=state.getList("Parts",10);var texts=presentationText(parts);
        for(int i=0;i<parts.size();i++) {
            var part=parts.getCompound(i);boolean mental=part.getString("Channel").equals("mental");
            if(part.getBoolean("Break")){textBuilder.append('\n');timeline.add(time);lineTime=time;}
            if(part.getBoolean("Beat")&&!mental&&textBuilder.length()>0)beats.add(textBuilder.length());
            int start=textBuilder.length();String text=texts.get(i);textBuilder.append(text);
            var style=NpcTextStyle.resolve(part.getString("Emotion"),part.getString("Effect"),part.contains("Speed")?part.getFloat("Speed"):1,part.contains("Intensity")?part.getFloat("Intensity"):1);
            spans.add(new Span(start,textBuilder.length(),style,mental));
            for(int cp:text.codePoints().toArray()){timeline.add(mental?lineTime:time);if(mental)continue;int duration=Character.isIdeographic(cp)?48:28;
                if(",，;；:：".indexOf(cp)>=0)duration+=150;if(".!?。！？".indexOf(cp)>=0)duration+=300;if(cp==0x2026)duration+=450;
                time+=Math.round(duration/style.speed());}
            if(mental)time=Math.max(time,lineTime+220);
            time+=part.getInt("Pause");
        }
        full=textBuilder.toString();totalDuration=time;
        organic=state.getBoolean("Organic");

        background=state.contains("Background")?state.getInt("Background"):0xBFCB8B;
        accent=state.contains("Foreground")?state.getInt("Foreground"):0xFFFFFF;
        bodySurface=0;for(int shift:new int[]{16,8,0})bodySurface|=(background>>shift&255)*162/255<<shift;
        textColor=RelationshipPanel.bodyTextColor(background);headerColor=RelationshipPanel.readableText(accent,background);
        textColors.clear();thoughtColors.clear();

    }
    private static List<String> presentationText(ListTag parts) {
        var texts=new ArrayList<String>();for(var value:parts)texts.add(translated((CompoundTag)value).getString());
        for(int i=0;i<parts.size();i++) {
            if(!parts.getCompound(i).getString("Channel").equals("mental"))continue;
            int end=i;while(end+1<parts.size()&&parts.getCompound(end+1).getString("Channel").equals("mental")&&!parts.getCompound(end+1).getBoolean("Break"))end++;
            int first=i,last=end;while(first<last&&texts.get(first).isEmpty())first++;while(last>first&&texts.get(last).isEmpty())last--;
            String left=texts.get(first),right=texts.get(last);
            if(!left.isEmpty()&&!right.isEmpty()&&(left.startsWith("(")&&right.endsWith(")")||left.startsWith("（")&&right.endsWith("）"))) {
                texts.set(first,left.substring(1));right=texts.get(last);texts.set(last,right.substring(0,right.length()-1));
            }
            i=end;
        }
        return texts;
    }
    private static Component translated(CompoundTag tag){
        var values=tag.getList("Arguments",8);var arguments=new Object[values.size()];
        for(int i=0;i<values.size();i++)arguments[i]=Component.Serializer.fromJson(values.getString(i));
        return Component.translatable(tag.getString("Text"),arguments);
    }
    @Override protected void init() {
        w=Math.min(680,width-24);x=(width-w)/2;
        textScale=width<380?1:1.25F;textRowHeight=width<380?14:18;choiceTextRow=width<380?12:15;
        rowHeight=26;
        for(var value:state.getList("Choices",10))rowHeight=Math.max(rowHeight,font.split(translated((CompoundTag)value),(int)((w-44)/textScale)).size()*choiceTextRow+8);
        int choiceRoom=height-24-CONTROLS_HEIGHT-CHOICE_GAP-BODY_TOP-BODY_BOTTOM-(height<220?2:3)*textRowHeight;
        choiceRoom=Math.min(choiceRoom,height/4);
        visibleChoices=state.getBoolean("Last")?Math.min(state.getList("Choices",10).size(),Math.max(1,Math.min(3,choiceRoom/(rowHeight+3)))):0;
        int choiceHeight=visibleChoices*(rowHeight+3);
        int maxPanel=Math.min(260,height-24-CONTROLS_HEIGHT-choiceHeight-(visibleChoices>0?CHOICE_GAP:0));
        textRows=Math.max(1,(maxPanel-BODY_TOP-BODY_BOTTOM)/textRowHeight);glyphs.clear();choices.clear();pages.clear();
        int wrapWidth=(int)((w-44)/textScale),row=0;var ranges=new ArrayList<int[]>();
        for(int i=0;i<beats.size();i++)paginate(beats.get(i),i+1<beats.size()?beats.get(i+1):full.length(),wrapWidth,ranges);
        for(var range:ranges) {
            while(range[0]<range[1]&&Character.isWhitespace(full.charAt(range[0])))range[0]++;
            while(range[1]>range[0]&&Character.isWhitespace(full.charAt(range[1]-1)))range[1]--;
            int search=range[0],firstRow=row;
            for(var sequence:font.split(Component.literal(full.substring(range[0],range[1])),wrapWidth)) {
                String line=plain(sequence);int start=full.indexOf(line,search);if(start<0)start=search;int charIndex=start,pos=0;
                for(int cp:line.codePoints().toArray()) {
                    String text=new String(Character.toChars(cp));var style=NORMAL;boolean mental=false;
                    for(var span:spans)if(charIndex>=span.start()&&charIndex<span.end()){style=span.style();mental=span.mental();break;}
                    glyphs.add(new Glyph(text,full.codePointCount(0,Math.min(charIndex,full.length())),pos,row,style,mental));
                    pos+=font.width(text);charIndex+=Character.charCount(cp);
                }
                search=Math.min(full.length(),start+line.length());row++;
            }
            pages.add(new TextPage(firstRow,Math.max(1,row-firstRow),timeAt(range[0]),timeAt(range[1])));
        }
        if(pages.isEmpty())pages.add(new TextPage(0,1,0,totalDuration));page=Math.min(page,pages.size()-1);
        fitPanel();
        var values=state.getList("Choices",10);int base=controlsY()+CONTROLS_HEIGHT;
        for(int i=0;i<values.size();i++) {
            var choice=values.getCompound(i);final String id=choice.getString("Id");
            var button=new ChoiceButton(x+12,base,w-24,rowHeight,translated(choice),b->send(id));
            choices.add(addRenderableWidget(button));
        }
        positionChoices();
    }
    private long timeAt(int offset){int index=full.codePointCount(0,offset);return index<timeline.size()?timeline.get(index):totalDuration;}
    private int rows(int start,int end,int width){return font.split(Component.literal(full.substring(start,end).strip()),width).size();}
    private void paginate(int start,int end,int width,List<int[]> ranges) {
        while(start<end&&Character.isWhitespace(full.charAt(start)))start++;
        while(end>start&&Character.isWhitespace(full.charAt(end-1)))end--;
        if(start==end)return;
        if(rows(start,end,width)<=textRows){ranges.add(new int[]{start,end});return;}
        int thought=-1;for(var span:spans)if(span.mental()&&span.start()>=start&&span.start()<end){thought=span.start();break;}
        if(thought>start&&rows(thought,end,width)<=textRows) {
            int tail=thought;
            // Keep the whole aside with the nearest spoken clause whenever the viewport allows it.
            for(int rank=3;rank>0&&tail==thought;rank--)for(int cut=start;cut<thought;cut++)
                if(boundary(cut)==rank&&rows(cut,end,width)<=textRows){tail=cut;break;}
            if(tail>start){paginate(start,tail,width,ranges);ranges.add(new int[]{tail,end});return;}
        }
        int cut=-1;
        for(int rank=3;rank>0&&cut<0;rank--)for(int i=start+1;i<end;i++)
            if(boundary(i)==rank&&rows(start,i,width)<=textRows)cut=i;
        if(cut<0) {
            cut=start;
            while(cut<end) {int next=full.offsetByCodePoints(cut,1);if(rows(start,next,width)>textRows)break;cut=next;}
            if(cut==start)cut=full.offsetByCodePoints(start,1);
        }
        ranges.add(new int[]{start,cut});paginate(cut,end,width,ranges);
    }
    private int boundary(int offset) {
        if(offset<=0)return 0;char previous=full.charAt(offset-1);
        if(".!?。！？".indexOf(previous)>=0&&(offset==full.length()||Character.isWhitespace(full.charAt(offset))||previous>127))return 3;
        if(",;:，；：—".indexOf(previous)>=0)return 2;
        return Character.isWhitespace(previous)?1:0;
    }
    private static String plain(FormattedCharSequence sequence){var b=new StringBuilder();sequence.accept((index,style,code)->{b.appendCodePoint(code);return true;});return b.toString();}
    private boolean reduced(){return ChangedSynergyClientConfig.CLIENT.reducedUiMotion.get();}
    private long pageStart(){return pages.get(page).start();}
    private long pageEnd(){return Math.max(220,pages.get(page).end()-pageStart())+120;}
    private boolean complete(){return revealed||Util.getMillis()-started>=pageEnd();}
    private boolean lastPage(){return page==pages.size()-1;}
    private boolean ready(){return complete()&&lastPage()&&state.getBoolean("Last")&&!waiting;}
    private float actionsOpacity(long now) {
        if(!complete())return 0;
        if(actionsShownAt<0)actionsShownAt=now;
        return reduced()?1:Math.min(1,Math.max(0,(now-actionsShownAt)/180F));
    }
    private static int faded(int color,float opacity){int alpha=color>>>24;return Math.round((alpha==0?255:alpha)*opacity)<<24|color&0xFFFFFF;}
    private void fitPanel() {
        h=BODY_TOP+pages.get(page).rows()*textRowHeight+BODY_BOTTOM;
        y=height-12-h;
    }
    private int controlsY(){return y-CONTROLS_HEIGHT-(visibleChoices>0?CHOICE_GAP+visibleChoices*(rowHeight+3):0);}
    private void positionChoices() {
        int count=choices.size(),base=controlsY()+CONTROLS_HEIGHT;
        for(int i=0;i<count;i++){var b=choices.get(i);b.visible=ready()&&i>=choiceOffset&&i<choiceOffset+visibleChoices;b.active=!waiting;b.setY(base+(i-choiceOffset)*(rowHeight+3));}
    }
    private void send(String action){if(waiting)return;waiting=true;ChangedSynergyNetwork.CHANNEL.sendToServer(new StoryNpcActionPacket(state.getUUID("Session"),state.getInt("Revision"),action));}
    private void advance() {
        if(waiting)return;
        if(!complete()){revealed=true;return;}
        if(!lastPage()){page++;fitPanel();started=Util.getMillis();revealed=false;actionsShownAt=-1;actionsAlpha=0;return;}
        if(!state.getBoolean("Last"))send("advance");
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial) {
        long now=Util.getMillis(),elapsed=now-started;actionsAlpha=actionsOpacity(now);positionChoices();
        RelationshipPanel.drawDialogue(graphics,x,y,w,h,background,organic);
        Component name=Component.Serializer.fromJson(state.getString("Name"));if(name==null)name=Component.empty();
        String role=Component.translatable(state.getString("Title")).getString();
        var standing=state.getBoolean("Chief")?Component.translatable("npc.changed_synergy.standing",state.getInt("Trust")):Component.empty();
        String heading=name.getString().equals(role)?name.getString():name.getString()+" · "+role;
        int standingWidth=font.width(standing),nameWidth=(int)((w-44-(standingWidth>0?standingWidth+12:0))/textScale);
        label(graphics,Component.literal(font.plainSubstrByWidth(heading,Math.max(10,nameWidth))),x+22,y+7,headerColor,textScale);
        if(state.getBoolean("Chief")){
            label(graphics,standing,x+w-22-standingWidth,y+9,headerColor,1);
        }
        graphics.enableScissor(x+14,y+BODY_TOP-5,x+w-14,y+h-BODY_BOTTOM+3);
        long start=timeline.isEmpty()?0:pageStart();
        for(var g:glyphs) {
            if(g.row()<pages.get(page).firstRow()||g.row()>=pages.get(page).firstRow()+pages.get(page).rows())continue;
            long appearance=timeline.isEmpty()?0:timeline.get(Math.min(g.index(),timeline.size()-1))-start;
            if(!revealed&&elapsed<appearance)continue;
            long age=elapsed-appearance;
            float fade=g.mental()?(reduced()?80F:220F):g.style().emotion().fade;
            float alpha=revealed?1:Math.min(1,Math.max(0,age/fade));
            var frame=DialogueTextAnimation.sample(g.style(),elapsed,age,g.index(),reduced(),revealed);
            int opacity=Math.round(alpha*frame.opacity()*255);if(opacity<5)continue;
            int color=(g.mental()?thoughtColors:textColors).computeIfAbsent(g.style(),style-> {
                int palette=g.mental()?DialogueTextAnimation.color(style,0xB5DEE0):style.effect()==NpcTextStyle.Effect.EMPHASIS?0xE9CC7D:
                        style.emotion()!=NpcTextStyle.Emotion.NEUTRAL?style.emotion().color:textColor==0xFFFFFF?0xF2E6D1:0x302B24;
                return RelationshipPanel.readableText(palette,bodySurface);
            });
            float halfWidth=font.width(g.text())*.5F,halfHeight=font.lineHeight*.5F;
            graphics.pose().pushPose();
            graphics.pose().translate(x+22+(g.x()+frame.x()+halfWidth)*textScale,y+BODY_TOP+(g.row()-pages.get(page).firstRow())*textRowHeight+(frame.y()+halfHeight)*textScale,0);
            if(frame.rotation()!=0)graphics.pose().mulPose(Axis.ZP.rotationDegrees(frame.rotation()));
            graphics.pose().scale(textScale*frame.scale(),textScale*frame.scale(),1);
            graphics.pose().translate(-halfWidth,-halfHeight,0);
            graphics.drawString(font,Component.literal(g.text()),0,0,(opacity<<24)|color,false);graphics.pose().popPose();
            if(g.mental()) {
                int strikeY=Math.round(y+BODY_TOP+(g.row()-pages.get(page).firstRow())*textRowHeight+font.lineHeight*.5F*textScale);
                graphics.fill((int)(x+22+g.x()*textScale),strikeY,(int)Math.ceil(x+22+(g.x()+font.width(g.text()))*textScale),strikeY+1,(opacity<<24)|color);
            }
        }
        graphics.disableScissor();
        String hint=ready()?"npc.changed_synergy.choose_hint":"npc.changed_synergy.continue_hint";
        int controlsY=controlsY();
        if(actionsAlpha>=.02F)graphics.drawString(font,Component.translatable(hint),x+22,controlsY,faded(0xD8D4C7,actionsAlpha),true);
        int count=pages.size();String progress=ready()&&choices.size()>visibleChoices?(choiceOffset+1)+"–"+Math.min(choices.size(),choiceOffset+visibleChoices)+"/"+choices.size():count>1?(page+1)+"/"+count:"";
        if(actionsAlpha>=.02F&&!progress.isEmpty())graphics.drawString(font,progress,x+w-22-font.width(progress),controlsY,faded(0xD8D4C7,actionsAlpha),true);
        super.render(graphics,mouseX,mouseY,partial);
    }
    private void label(GuiGraphics graphics,Component text,float xx,float yy,int color,float scale){label(graphics,text.getVisualOrderText(),xx,yy,color,scale);}
    private void label(GuiGraphics graphics,FormattedCharSequence text,float xx,float yy,int color,float scale) {
        graphics.pose().pushPose();graphics.pose().translate(xx,yy,0);graphics.pose().scale(scale,scale,1);
        graphics.drawString(font,text,0,0,color,false);graphics.pose().popPose();
    }
    @Override public boolean mouseClicked(double mx,double my,int button){if(button==0){if(ready()&&super.mouseClicked(mx,my,button))return true;advance();return true;}return super.mouseClicked(mx,my,button);}
    @Override public boolean mouseScrolled(double mx,double my,double delta){if(ready()){choiceOffset=Math.max(0,Math.min(Math.max(0,choices.size()-visibleChoices),choiceOffset+(delta<0?1:-1)));selected=-1;setFocused(null);positionChoices();return true;}return false;}
    @Override public boolean keyPressed(int key,int scan,int mods) {
        if(key==GLFW.GLFW_KEY_ESCAPE){onClose();return true;}
        if(ready()&&(key==GLFW.GLFW_KEY_DOWN||key==GLFW.GLFW_KEY_UP||key==GLFW.GLFW_KEY_TAB)) {
            boolean backwards=key==GLFW.GLFW_KEY_UP||key==GLFW.GLFW_KEY_TAB&&(mods&GLFW.GLFW_MOD_SHIFT)!=0;
            selected=selected<0?(backwards?choices.size()-1:0):Math.floorMod(selected+(backwards?-1:1),choices.size());
            choiceOffset=Math.max(0,Math.min(selected,Math.max(0,choices.size()-visibleChoices)));positionChoices();setFocused(choices.get(selected));return true;
        }
        if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER||key==GLFW.GLFW_KEY_SPACE){if(ready()&&getFocused() instanceof ChoiceButton b)b.onPress();else advance();return true;}
        return super.keyPressed(key,scan,mods);
    }
    private void cancel(){if(!state.isEmpty()&&canceledSessions.add(state.getUUID("Session"))){ChangedSynergyNetwork.CHANNEL.sendToServer(new StoryNpcActionPacket(state.getUUID("Session"),state.getInt("Revision"),"cancel"));if(canceledSessions.size()>64)canceledSessions.remove(canceledSessions.iterator().next());}}
    @Override public void onClose(){cancel();minecraft.setScreen(null);}
    @Override public void removed(){cancel();}
    @Override public boolean isPauseScreen(){return false;}
    private final class ChoiceButton extends Button {
        ChoiceButton(int x,int y,int width,int height,Component message,OnPress action){super(x,y,width,height,message,action,DEFAULT_NARRATION);}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial) {
            if(actionsAlpha<.02F)return;
            boolean focus=isHoveredOrFocused();
            g.fill(getX(),getY(),getX()+width,getY()+height,faded(focus?0xEE303A33:0xC018201D,actionsAlpha));
            if(focus)label(g,Component.literal("›"),getX()-4,getY()+(height-12)/2F,faded(CHOICE_TEXT,actionsAlpha),textScale);
            var lines=font.split(getMessage(),(int)((width-20)/textScale));float yy=getY()+(height-lines.size()*choiceTextRow)/2F+2;
            for(int i=0;i<lines.size();i++)label(g,lines.get(i),getX()+10,yy+i*choiceTextRow,faded(CHOICE_TEXT,actionsAlpha),textScale);
        }
    }
}
