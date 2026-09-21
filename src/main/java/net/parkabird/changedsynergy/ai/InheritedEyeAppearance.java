package net.parkabird.changedsynergy.ai;

import java.util.Arrays;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.entity.BasicPlayerInfo;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.EyeStyle;
import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.ltxprogrammer.changed.util.Color3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Eye-only appearance inherited by one permanent transfur instance. */
public final class InheritedEyeAppearance {
    public static final String NBT_KEY = "SynergyInheritedEyes";
    private static final String LEFT = "IrisLeft";
    private static final String RIGHT = "IrisRight";
    private static final String SCLERA = "Sclera";
    private static final String STYLE = "Style";
    private static final String DARK_OVERRIDE = "DarkLatexOverride";
    private static final String MATCH_STYLE = "MatchStyle";

    private InheritedEyeAppearance() {
    }

    public static CompoundTag capture(ChangedEntity source) {
        BasicPlayerInfo info = source.getBasicPlayerInfo();
        CompoundTag tag = new CompoundTag();
        tag.putInt(LEFT, info.getLeftIrisColor().toInt());
        tag.putInt(RIGHT, info.getRightIrisColor().toInt());
        tag.putInt(SCLERA, info.getScleraColor().toInt());
        tag.putString(STYLE, info.getEyeStyle().getId().toString());
        tag.putBoolean(DARK_OVERRIDE, info.isOverrideIrisOnDarkLatex());
        tag.putBoolean(MATCH_STYLE, info.isOverrideOthersToMatchStyle());
        return tag;
    }

    public static boolean isValid(@Nullable CompoundTag tag) {
        return tag != null
                && tag.contains(LEFT, Tag.TAG_INT)
                && tag.contains(RIGHT, Tag.TAG_INT)
                && tag.contains(SCLERA, Tag.TAG_INT)
                && tag.contains(STYLE, Tag.TAG_STRING)
                && tag.contains(DARK_OVERRIDE, Tag.TAG_BYTE)
                && tag.contains(MATCH_STYLE, Tag.TAG_BYTE)
                && style(tag) != null;
    }

    public static boolean attach(
            TransfurVariantInstance<?> instance,
            CompoundTag appearance) {
        if (!(instance instanceof Holder holder) || !isValid(appearance)) {
            return false;
        }
        holder.changedSynergy$setInheritedEyes(appearance.copy());
        return true;
    }

    @Nullable
    public static BasicPlayerInfo merge(
            BasicPlayerInfo base,
            TransfurVariantInstance<?> instance) {
        if (!(instance instanceof Holder holder)) {
            return null;
        }
        CompoundTag tag = holder.changedSynergy$getInheritedEyes();
        EyeStyle style = style(tag);
        if (!isValid(tag) || style == null) {
            return null;
        }
        BasicPlayerInfo merged = new BasicPlayerInfo();
        merged.copyFrom(base);
        merged.setLeftIrisColor(Color3.fromInt(tag.getInt(LEFT)));
        merged.setRightIrisColor(Color3.fromInt(tag.getInt(RIGHT)));
        merged.setScleraColor(Color3.fromInt(tag.getInt(SCLERA)));
        merged.setEyeStyle(style);
        merged.setOverrideIrisOnDarkLatex(tag.getBoolean(DARK_OVERRIDE));
        merged.setOverrideOthersToMatchStyle(tag.getBoolean(MATCH_STYLE));
        return merged;
    }

    @Nullable
    private static EyeStyle style(@Nullable CompoundTag tag) {
        if (tag == null || !tag.contains(STYLE, Tag.TAG_STRING)) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(STYLE));
        return id == null ? null : Arrays.stream(EyeStyle.values())
                .filter(candidate -> id.equals(candidate.getId()))
                .findFirst()
                .orElse(null);
    }

    public interface Holder {
        @Nullable CompoundTag changedSynergy$getInheritedEyes();
        void changedSynergy$setInheritedEyes(@Nullable CompoundTag appearance);
    }
}
