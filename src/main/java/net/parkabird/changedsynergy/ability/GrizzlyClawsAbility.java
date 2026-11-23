package net.parkabird.changedsynergy.ability;

import java.util.Collection;
import java.util.List;
import net.ltxprogrammer.changed.ability.AbstractAbility;
import net.ltxprogrammer.changed.ability.AbstractAbilityInstance;
import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.parkabird.changedsynergy.entity.GrizzlyClawsService;
import net.parkabird.changedsynergy.entity.LatexGrizzly;
import net.parkabird.changedsynergy.init.ChangedSynergyAbilities;

/** Toggles whether a Grizzly host's bare-handed iron-tier claws and boosted mining are active. */
public final class GrizzlyClawsAbility extends AbstractAbility<GrizzlyClawsAbility.Instance> {
    public GrizzlyClawsAbility() {
        super(Instance::new);
    }

    @Override
    public UseType getUseType(IAbstractChangedEntity entity) {
        return UseType.INSTANT;
    }

    @Override
    public int getCoolDown(IAbstractChangedEntity entity) {
        return 5;
    }

    @Override
    public Component getAbilityName(IAbstractChangedEntity entity) {
        boolean on = isEnabled(entity);
        return Component.translatable(on
                ? "ability.changed_synergy.grizzly_claws.mode_on"
                : "ability.changed_synergy.grizzly_claws.mode_off");
    }

    @Override
    public Collection<Component> getAbilityDescription(IAbstractChangedEntity entity) {
        return List.of(Component.translatable("ability.changed_synergy.grizzly_claws.desc"));
    }

    public static boolean isEnabled(IAbstractChangedEntity entity) {
        if (entity == null) return true;
        Instance instance = entity.getAbilityInstance(ChangedSynergyAbilities.GRIZZLY_CLAWS.get());
        return instance == null || instance.isEnabled();
    }

    public static final class Instance extends AbstractAbilityInstance {
        private boolean enabled = true;

        public Instance(AbstractAbility<?> ability, IAbstractChangedEntity entity) {
            super(ability, entity);
        }

        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public boolean canUse() {
            LivingEntity living = entity.getEntity();
            return entity.getChangedEntity() instanceof LatexGrizzly && living.isAlive() && !living.isSpectator();
        }

        @Override
        public boolean canKeepUsing() {
            return false;
        }

        @Override
        public void startUsing() {
            if (entity.getLevel().isClientSide || !canUse()) return;
            enabled = !enabled;
            sync();
            LivingEntity living = entity.getEntity();
            if (living instanceof Player player) {
                GrizzlyClawsService.refreshModifier(player);
                player.displayClientMessage(Component.translatable(enabled
                        ? "ability.changed_synergy.grizzly_claws.enabled"
                        : "ability.changed_synergy.grizzly_claws.disabled"), true);
            }
        }

        @Override
        public void tick() {}

        @Override
        public void stopUsing() {}

        @Override
        public void saveData(CompoundTag tag) {
            super.saveData(tag);
            tag.putBoolean("Enabled", enabled);
        }

        @Override
        public void readData(CompoundTag tag) {
            super.readData(tag);
            if (tag.contains("Enabled")) {
                enabled = tag.getBoolean("Enabled");
            }
        }

        private void sync() {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("Enabled", enabled);
            sendPayload(tag);
        }

        @Override
        public void acceptPayload(CompoundTag tag) {
            if (!entity.getLevel().isClientSide) return;
            if (tag.contains("Enabled")) {
                enabled = tag.getBoolean("Enabled");
            }
        }
    }
}
