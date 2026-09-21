package net.parkabird.changedsynergy.mixin;

import java.util.function.Predicate;
import net.ltxprogrammer.changed.ability.AbstractAbilityInstance;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ChangedEntity.class, remap = false)
public interface ChangedEntityAbilityInvoker {
    @Invoker("registerAbility")
    <A extends AbstractAbilityInstance> A changedSynergy$registerAbility(
            Predicate<A> available, A instance);
}
