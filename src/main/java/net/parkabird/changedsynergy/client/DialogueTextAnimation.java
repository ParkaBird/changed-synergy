package net.parkabird.changedsynergy.client;

import net.parkabird.changedsynergy.npc.NpcTextStyle;

/** Glyph-local animation only: layout and wrapping never depend on a frame's pose. */
public final class DialogueTextAnimation {
    public record Frame(float x,float y,float scale,float rotation,float opacity) {}
    private DialogueTextAnimation() {}
    public static Frame sample(NpcTextStyle.Style style,long elapsed,long age,int index,boolean reduced,boolean settled) {
        float opacity=style.effect()==NpcTextStyle.Effect.WHISPER?.9F:1;
        if(reduced)return new Frame(0,0,1,0,opacity);
        float amount=style.intensity();double phase=index*.45;
        float entry=settled?0:(float)Math.exp(-Math.max(0,age)/110D);
        float sustain=.35F+.65F*(float)Math.exp(-Math.max(0,age)/1800D);
        float motion=amount*sustain;
        float x=0,y=-.9F*entry*amount,scale=1,rotation=0;
        switch(style.effect()) {
            case EMPHASIS -> scale+=(.035F+.09F*entry)*amount;
            case WAVE -> y+=(float)Math.sin(elapsed/230D+phase)*1.65F*motion;
            case SHAKE -> {x=(float)Math.sin(elapsed/31D+index*13.3)*.9F*motion;y+=(float)Math.sin(elapsed/43D+index*7.7)*.65F*motion;}
            case TREMBLE -> {x=(float)Math.sin(elapsed/42D+index*8.1)*.65F*motion;y+=(float)Math.sin(elapsed/57D+index*4.3)*.38F*motion;}
            case BOUNCE -> y-=(float)Math.abs(Math.sin(elapsed/190D+index*.24))*2.1F*motion;
            case FLOAT -> {x=(float)Math.sin(elapsed/1000D+index*.2)*.2F*motion;y+=(float)Math.sin(elapsed/650D+index*.25)*1.1F*motion;}
            case PULSE -> scale+=(.02F+(float)Math.sin(elapsed/320D+index*.15)*.035F)*motion;
            case WOBBLE -> {rotation=(float)Math.sin(elapsed/330D+index*.22)*3.6F*motion;y+=(float)Math.sin(elapsed/410D+phase)*.25F*motion;}
            case DROOP -> y=(1F+(float)Math.sin(elapsed/1000D+index*.15)*.25F-.4F*entry)*amount;
            case WHISPER -> {y=0;scale-=.035F*amount;}
            case SHOUT, IMPACT -> {
                float hit=settled?0:(float)Math.exp(-Math.max(0,age)/200D);
                scale+=(style.effect()==NpcTextStyle.Effect.SHOUT?.045F:0)*amount+.13F*hit*amount;
                y=-1.5F*hit*amount;
                if(style.effect()==NpcTextStyle.Effect.SHOUT)x+=(float)Math.sin(elapsed/60D+index*1.7)*.2F*motion;
            }
            default -> { }
        }
        // The entrance and persistent bounce share the same small line-spacing allowance.
        y=Math.max(-2.1F,Math.min(2.1F,y));
        return new Frame(x,y,Math.max(.92F,Math.min(1.16F,scale)),rotation,opacity);
    }
    public static int color(NpcTextStyle.Style style,int base) {
        var emotion=style.emotion();int color=0;
        for(int shift=0;shift<=16;shift+=8)color|=Math.round(((base>>shift)&255)*(1-emotion.tint)+((emotion.color>>shift)&255)*emotion.tint)<<shift;
        return color;
    }
}
