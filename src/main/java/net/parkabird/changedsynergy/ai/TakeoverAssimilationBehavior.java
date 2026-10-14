package net.parkabird.changedsynergy.ai;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.ai.AssimilationBehavior;
import net.minecraft.server.level.ServerPlayer;

/**
 * Wraps Changed's absorption behavior without living in the Mixin package.
 * Mixin package classes may not be referenced directly by transformed targets.
 */
public final class TakeoverAssimilationBehavior implements AssimilationBehavior {
    private final AssimilationBehavior original;
    private final ChangedEntity carrier;
    private final ServerPlayer player;
    private final IAbstractChangedEntity source;
    private final Consumer<IAbstractChangedEntity> takeoverListeners;
    private final AtomicBoolean completed;
    private final boolean instinctPlayer;

    public TakeoverAssimilationBehavior(AssimilationBehavior original, ChangedEntity carrier,
            ServerPlayer player, IAbstractChangedEntity source) {
        this(original, carrier, player, source, ignored -> {}, new AtomicBoolean());
    }

    private TakeoverAssimilationBehavior(AssimilationBehavior original, ChangedEntity carrier,
            ServerPlayer player, IAbstractChangedEntity source,
            Consumer<IAbstractChangedEntity> takeoverListeners, AtomicBoolean completed) {
        this.original = Objects.requireNonNull(original);
        this.carrier = Objects.requireNonNull(carrier);
        this.player = Objects.requireNonNull(player);
        this.source = Objects.requireNonNull(source);
        this.takeoverListeners = Objects.requireNonNull(takeoverListeners);
        this.completed = Objects.requireNonNull(completed);
        this.instinctPlayer = net.parkabird.changedsynergy.compat.TrueTransfurCompat.isInstinctBody(carrier);
    }

    @Override
    public void stepAssimilate() {
        if (completed.get() || TakeoverService.active(player) || TakeoverService.carrying(carrier)) {
            return;
        }
        if (instinctPlayer) {
            if (!InstinctConfinementService.canTarget(carrier, player)) return;
            if (original.willAssimilate()) {
                // Preserve Changed's shield, armour and hit-delay checks. Only
                // replace its final irreversible conversion with a temporary suit.
                float previous = net.ltxprogrammer.changed.process.ProcessTransfur.getPlayerTransfurProgress(player);
                net.ltxprogrammer.changed.process.ProcessTransfur.setPlayerTransfurProgress(player,
                        (float)net.ltxprogrammer.changed.process.ProcessTransfur.getEntityTransfurTolerance(player));
                if (InstinctConfinementService.begin(carrier, player)) completed.set(true);
                else net.ltxprogrammer.changed.process.ProcessTransfur.setPlayerTransfurProgress(player, previous);
                // A blocked completion must never fall through to replication or
                // absorption, which can erase the human or TT's controlled body.
                return;
            }
            original.stepAssimilate();
            return;
        }
        if (original.willAssimilate()) {
            // Some Changed/Add-on grab paths replace GRAB_REPLICATE with the
            // creature's native absorption only after the decision event has
            // returned. Re-authorize from this final, proven absorption rather
            // than silently losing an otherwise valid takeover.
            TakeoverService.ensureFinalAbsorptionAuthorization(carrier, player);
            if (TakeoverService.beginOrdinary(carrier, player)) {
                completed.set(true);
                takeoverListeners.accept(source);
                return;
            }
        }
        original.stepAssimilate();
    }

    public boolean managesNativeProgress() {
        return instinctPlayer
                ? net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.instinctConfinement.get()
                    && net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.takeoverEnabled.get()
                    && InstinctConfinementService.canTarget(carrier, player)
                : TakeoverService.canBeginOrdinary(carrier, player);
    }

    @Override
    public boolean willAssimilate() {
        return !completed.get() && !TakeoverService.active(player)
                && !TakeoverService.carrying(carrier) && original.willAssimilate();
    }

    @Override
    public AssimilationBehavior appendTransfurListener(Consumer<IAbstractChangedEntity> listener) {
        Objects.requireNonNull(listener);
        return new TakeoverAssimilationBehavior(original.appendTransfurListener(listener),
                carrier, player, source, takeoverListeners.andThen(listener), completed);
    }
}
