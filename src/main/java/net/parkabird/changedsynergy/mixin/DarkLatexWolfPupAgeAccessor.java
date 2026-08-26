package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.beast.DarkLatexWolfPup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Narrow bridge for augmenting Changed's native pup feeding without replacing it. */
@Mixin(DarkLatexWolfPup.class)
public interface DarkLatexWolfPupAgeAccessor {
    @Accessor("age")
    int changedSynergy$getAge();

    @Accessor("age")
    void changedSynergy$setAge(int age);
}
