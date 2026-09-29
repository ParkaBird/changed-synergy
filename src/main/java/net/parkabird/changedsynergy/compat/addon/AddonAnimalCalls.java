package net.parkabird.changedsynergy.compat.addon;

import net.foxyas.changedaddon.init.ChangedAddonSoundEvents;
import net.foxyas.changedaddon.variant.TransfurSoundsDetails.TransfurSoundAction;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ai.CreatureIdentity;

/** Reuses Addon's existing animal calls without its player-only cooldown. */
public final class AddonAnimalCalls {
    private static final String NEXT_CALL = "ChangedSynergyNextAnimalCall";

    private AddonAnimalCalls() {}

    public static void play(ChangedEntity creature, String mood) {
        long now = creature.level().getGameTime();
        if (creature.getPersistentData().getLong(NEXT_CALL) > now
                || creature.getRandom().nextFloat() > 0.4F) return;

        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(creature.getType());
        String species = id == null ? "" : id.getPath();
        TransfurSoundAction action = switch (CreatureIdentity.vocalizationProfile(creature)) {
            case "canine" -> species.contains("fox")
                    ? ("hostile".equals(mood) ? TransfurSoundAction.FOX_SCREAM : TransfurSoundAction.CHATTER)
                    : switch (mood) {
                        case "hostile" -> TransfurSoundAction.GROWL;
                        case "sad", "fond" -> TransfurSoundAction.WHINE;
                        case "happy" -> TransfurSoundAction.YIP;
                        default -> TransfurSoundAction.BARK;
                    };
            case "feline" -> species.contains("lion") || species.contains("tiger")
                    ? TransfurSoundAction.ROAR
                    : switch (mood) {
                        case "hostile" -> TransfurSoundAction.HISS;
                        case "fond" -> TransfurSoundAction.PURR;
                        case "sad" -> TransfurSoundAction.PURREOW;
                        default -> TransfurSoundAction.MEOW;
                    };
            case "draconic" -> "hostile".equals(mood)
                    ? TransfurSoundAction.DRAGON_ROAR : TransfurSoundAction.DRAGON_GROWL;
            case "insect" -> species.contains("spider")
                    ? TransfurSoundAction.SPIDER_AMBIENT : null;
            default -> null;
        };
        SoundEvent sound = action != null ? action.sound()
                : species.contains("gecko")
                        ? ChangedAddonSoundEvents.GECKO_BEEP.get() : null;
        if (sound == null) return;
        creature.getPersistentData().putLong(NEXT_CALL, now + 80L);
        creature.level().playSound(null, creature, sound, SoundSource.NEUTRAL,
                0.45F, 0.9F + creature.getRandom().nextFloat() * 0.2F);
    }
}
