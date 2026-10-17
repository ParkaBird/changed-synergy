package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.BasicPlayerInfo;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ChangedEntity.class, remap = false)
public interface ChangedEntityAppearanceAccessor {
    @Accessor("DATA_LOCAL_VARIANT_INFO")
    static EntityDataAccessor<BasicPlayerInfo> changedSynergy$appearanceData() { throw new AssertionError(); }
}
