package net.parkabird.changedsynergy.client.archive;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** Built-in Minecraft sounds for the archive's folder and paper interactions. */
public final class ArchiveUiSounds {
    private ArchiveUiSounds() {
    }

    public static void hover() {
        play(SoundEvents.UI_BUTTON_CLICK.value(), 1.15F, 0.12F);
    }

    public static void click() {
        play(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.4F);
    }

    public static void settle() {
        play(SoundEvents.BOOK_PUT, 1.0F, 0.6F);
    }

    public static void page() {
        play(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.7F);
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(sound, pitch, volume));
    }
}
