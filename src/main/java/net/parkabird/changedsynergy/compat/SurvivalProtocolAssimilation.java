package net.parkabird.changedsynergy.compat;

import java.util.function.Consumer;
import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.ltxprogrammer.changed.entity.ai.AssimilationBehavior;
import net.minecraft.server.level.ServerPlayer;

/** Scope only an authorized takeover's native counter, including appended callbacks. */
public record SurvivalProtocolAssimilation(ServerPlayer player, AssimilationBehavior delegate) implements AssimilationBehavior {
    @Override public void stepAssimilate() { SurvivalProtocolCompat.withNativeProgress(player,delegate::stepAssimilate); }
    @Override public boolean willAssimilate() { return delegate.willAssimilate(); }
    @Override public AssimilationBehavior appendTransfurListener(Consumer<IAbstractChangedEntity> listener) {
        return new SurvivalProtocolAssimilation(player,delegate.appendTransfurListener(listener));
    }
}
