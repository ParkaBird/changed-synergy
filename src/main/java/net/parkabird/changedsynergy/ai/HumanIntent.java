package net.parkabird.changedsynergy.ai;

import java.util.Locale;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ai.CreaturePersonality.Trait;

/**
 * Stable first-contact intent toward an unfamiliar human. It describes why an
 * individual approaches, not a temporary combat state.
 */
public enum HumanIntent {
    GREET,
    SEEK_HOST,
    ASSIMILATE;

    private static final String ROOT = "ChangedSynergyHumanIntent";
    private static final String VALUE = "Value";

    public static HumanIntent of(ChangedEntity mob) {
        CompoundTag data = data(mob);
        // Organic creatures never seek a host. Reconcile identities generated
        // before this rule existed instead of replaying their stale intent.
        if (LatexSocialMemory.isOrganic(mob)) {
            data.putInt(VALUE, GREET.ordinal());
            return GREET;
        }
        if (data.contains(VALUE, Tag.TAG_INT)) {
            int value = data.getInt(VALUE);
            if (value >= 0 && value < values().length) {
                return values()[value];
            }
        }

        HumanIntent selected = choose(mob);
        data.putInt(VALUE, selected.ordinal());
        return selected;
    }

    public String dialogueKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    private static HumanIntent choose(ChangedEntity mob) {
        // Organic creatures keep their established unprovoked-human
        // neutrality. Their curiosity is expressed through greeting contact.
        if (CreaturePersonality.has(mob, Trait.POLITE)) {
            return GREET;
        }

        int roll = (int)Long.remainderUnsigned(
                mix64(seed(mob) ^ 0xE7037ED1A0B428DBL), 100L);
        return switch (CreaturePersonality.dominantTrait(mob)) {
            case CALM -> roll < 62 ? GREET : roll < 78 ? SEEK_HOST : ASSIMILATE;
            case CAUTIOUS, SENSITIVE ->
                    roll < 52 ? GREET : roll < 68 ? SEEK_HOST : ASSIMILATE;
            case CURIOUS, PLAYFUL ->
                    roll < 38 ? GREET : roll < 70 ? SEEK_HOST : ASSIMILATE;
            case SHOW_OFF ->
                    roll < 28 ? GREET : roll < 72 ? SEEK_HOST : ASSIMILATE;
            case PROTECTIVE ->
                    roll < 26 ? GREET : roll < 43 ? SEEK_HOST : ASSIMILATE;
            case COMPETITIVE ->
                    roll < 22 ? GREET : roll < 58 ? SEEK_HOST : ASSIMILATE;
            case POLITE -> GREET;
        };
    }

    private static long seed(ChangedEntity mob) {
        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        long typeSalt = String.valueOf(typeId).hashCode() * 0x9E3779B97F4A7C15L;
        return mob.getUUID().getMostSignificantBits()
                ^ Long.rotateLeft(mob.getUUID().getLeastSignificantBits(), 17)
                ^ typeSalt
                ^ 0xA0761D6478BD642FL
                        * (CreaturePersonality.dominantTrait(mob).ordinal() + 1L);
    }

    private static CompoundTag data(ChangedEntity mob) {
        CompoundTag persistent = mob.getPersistentData();
        if (!persistent.contains(ROOT, Tag.TAG_COMPOUND)) {
            persistent.put(ROOT, new CompoundTag());
        }
        return persistent.getCompound(ROOT);
    }

    private static long mix64(long value) {
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
}
