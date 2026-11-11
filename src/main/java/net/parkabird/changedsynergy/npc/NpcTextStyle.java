package net.parkabird.changedsynergy.npc;

import java.util.Locale;

/** Shared authoring vocabulary; contains no client classes or world state. */
public final class NpcTextStyle {
    public enum Effect { NONE, EMPHASIS, WAVE, SHAKE, TREMBLE, BOUNCE, FLOAT, PULSE, WOBBLE, DROOP, SHOUT, WHISPER, IMPACT }
    public enum Emotion {
        NEUTRAL(Effect.NONE,1,1,0xEEEFE6,0,100),
        CALM(Effect.NONE,.94F,.65F,0xBACDBF,.18F,150),
        CURIOUS(Effect.WAVE,1.03F,.65F,0xE8D3A0,.25F,110),
        HAPPY(Effect.BOUNCE,1.12F,.65F,0xEAD58C,.35F,100),
        EXCITED(Effect.BOUNCE,1.35F,1,0xF0CB84,.5F,85),
        ANGRY(Effect.SHAKE,1.15F,1,0xF0AD9D,.55F,85),
        FEARFUL(Effect.TREMBLE,.85F,.9F,0xBCD0DE,.4F,150),
        SAD(Effect.DROOP,.75F,.8F,0xB5C7D6,.5F,180),
        SHY(Effect.WOBBLE,.85F,.55F,0xE1BDCA,.35F,160),
        WHISPER(Effect.WHISPER,.72F,.6F,0xBDCFC4,.45F,210),
        SURPRISED(Effect.IMPACT,1.2F,1,0xF2D6A3,.45F,90),
        DETERMINED(Effect.PULSE,1.02F,.75F,0xE9C79A,.35F,110),
        DREAMY(Effect.FLOAT,.85F,.8F,0xC8C6E3,.35F,180);

        public final Effect effect;
        public final float speed, intensity, tint;
        public final int color, fade;
        Emotion(Effect effect,float speed,float intensity,int color,float tint,int fade) {
            this.effect=effect;this.speed=speed;this.intensity=intensity;this.color=color;this.tint=tint;this.fade=fade;
        }
    }
    public record Style(Effect effect,Emotion emotion,float speed,float intensity) {}
    private NpcTextStyle() {}
    public static boolean supportsEffect(String value) {
        if("auto".equals(value))return true;
        for(var effect:Effect.values())if(effect.name().toLowerCase(Locale.ROOT).equals(value))return true;
        return false;
    }
    public static boolean supportsEmotion(String value) {
        for(var emotion:Emotion.values())if(emotion.name().toLowerCase(Locale.ROOT).equals(value))return true;
        return false;
    }
    public static Style resolve(String emotionName,String effectName,float speed,float intensity) {
        Emotion emotion;
        try{emotion=Emotion.valueOf(emotionName.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException e){emotion=Emotion.NEUTRAL;}
        Effect effect=emotion.effect;
        if(!effectName.isEmpty()&&!effectName.equals("auto")) {
            try{effect=Effect.valueOf(effectName.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException e){effect=Effect.NONE;}
        }
        return new Style(effect,emotion,clamp(speed*emotion.speed,.25F,3),clamp(intensity*emotion.intensity,0,1.4F));
    }
    private static float clamp(float value,float min,float max){return Float.isFinite(value)?Math.max(min,Math.min(max,value)):min;}
}
