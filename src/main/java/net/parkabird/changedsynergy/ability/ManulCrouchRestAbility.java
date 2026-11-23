package net.parkabird.changedsynergy.ability;

import java.util.Collection;
import java.util.List;
import net.ltxprogrammer.changed.ability.AbstractAbility;
import net.ltxprogrammer.changed.ability.AbstractAbilityInstance;
import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.parkabird.changedsynergy.entity.AbstractLatexManul;
import net.parkabird.changedsynergy.init.ChangedSynergyAbilities;

/**
 * Toggle that arms a manul crouch-rest. While armed and crouching on solid ground the
 * host roots in place, slowly regenerates, and blends the Tailwrap foot-coil pose.
 * Standing, taking damage, or toggling off smoothly unwinds the wrap.
 */
public final class ManulCrouchRestAbility extends AbstractAbility<ManulCrouchRestAbility.Instance> {
    /** Ticks to fully coil the tail once resting. */
    public static final float WRAP_IN_SPEED = 1.0F / 14.0F;
    /** Faster unwind so the pose can be interrupted cleanly. */
    public static final float WRAP_OUT_SPEED = 1.0F / 9.0F;
    /** One half-heart every two seconds while fully settled. */
    public static final int HEAL_INTERVAL_TICKS = 40;
    public static final float HEAL_AMOUNT = 1.0F;
    private static final UUID ROOT_SPEED_ID = UUID.fromString("6b7e2c91-4a5d-4f3a-9c8e-1d2f3a4b5c6d");
    private static final AttributeModifier ROOT_SPEED = new AttributeModifier(
            ROOT_SPEED_ID, "changed_synergy:manul_crouch_rest", -1.0D, AttributeModifier.Operation.MULTIPLY_TOTAL);

    public ManulCrouchRestAbility() {
        super(Instance::new);
    }

    @Override
    public UseType getUseType(IAbstractChangedEntity entity) {
        return UseType.INSTANT;
    }

    @Override
    public int getCoolDown(IAbstractChangedEntity entity) {
        return 10;
    }

    @Override
    public Component getAbilityName(IAbstractChangedEntity entity) {
        Instance instance = entity.getAbilityInstance(ChangedSynergyAbilities.MANUL_CROUCH_REST.get());
        if (instance != null && instance.isResting()) {
            return Component.translatable("ability.changed_synergy.manul_crouch_rest.mode_resting");
        }
        boolean armed = instance != null && instance.isArmed();
        return Component.translatable(armed
                ? "ability.changed_synergy.manul_crouch_rest.mode_armed"
                : "ability.changed_synergy.manul_crouch_rest.mode_idle");
    }

    @Override
    public Collection<Component> getAbilityDescription(IAbstractChangedEntity entity) {
        return List.of(Component.translatable("ability.changed_synergy.manul_crouch_rest.desc"));
    }

    public static Instance instanceOf(LivingEntity living) {
        if (living == null) {
            return null;
        }
        if (living instanceof AbstractLatexManul manul) {
            return manul.getAbilityInstance(ChangedSynergyAbilities.MANUL_CROUCH_REST.get());
        }
        return AbstractAbility.getAbilityInstance(living, ChangedSynergyAbilities.MANUL_CROUCH_REST.get());
    }

    public static float wrapAmount(LivingEntity living, float partialTicks) {
        Instance instance = instanceOf(living);
        return instance == null ? 0.0F : instance.wrapAmount(partialTicks);
    }

    public static final class Instance extends AbstractAbilityInstance {
        private boolean armed;
        private float wrap;
        private float previousWrap;
        private int healTicks;
        private boolean wasResting;

        public Instance(AbstractAbility<?> ability, IAbstractChangedEntity entity) {
            super(ability, entity);
        }

        public boolean isArmed() {
            return armed;
        }

        public boolean isResting() {
            return wantRest();
        }

        public float wrapAmount(float partialTicks) {
            return Mth.lerp(partialTicks, previousWrap, wrap);
        }

        public float wrapAmount() {
            return wrap;
        }

        private boolean wantRest() {
            if (!armed) {
                return false;
            }
            LivingEntity living = entity.getEntity();
            boolean crouching = living.isShiftKeyDown()
                    || living.isCrouching()
                    || living.getPose() == Pose.CROUCHING;
            return living.isAlive()
                    && !living.isSpectator()
                    && living.onGround()
                    && crouching
                    && !living.isInWaterOrBubble()
                    && !living.isPassenger()
                    && !living.isSleeping()
                    && entity.getChangedEntity() instanceof AbstractLatexManul;
        }

        @Override
        public boolean canUse() {
            LivingEntity living = entity.getEntity();
            return entity.getChangedEntity() instanceof AbstractLatexManul
                    && living.isAlive()
                    && !living.isSpectator();
        }

        @Override
        public boolean canKeepUsing() {
            return false;
        }

        @Override
        public void startUsing() {
            if (entity.getLevel().isClientSide || !canUse()) {
                return;
            }
            armed = !armed;
            if (!armed) {
                healTicks = 0;
            }
            sync();
            LivingEntity living = entity.getEntity();
            if (living instanceof Player player) {
                player.displayClientMessage(Component.translatable(armed
                        ? "ability.changed_synergy.manul_crouch_rest.armed"
                        : "ability.changed_synergy.manul_crouch_rest.disarmed"), true);
            }
        }

        @Override
        public void tick() {}

        @Override
        public void stopUsing() {}

        @Override
        public void onRemove() {
            applyRootAttribute(entity.getEntity(), false);
            armed = false;
            wrap = 0.0F;
            previousWrap = 0.0F;
        }

        @Override
        public void tickIdle() {
            previousWrap = wrap;
            boolean resting = wantRest();
            float target = resting ? 1.0F : 0.0F;
            float speed = resting ? WRAP_IN_SPEED : WRAP_OUT_SPEED;
            if (wrap < target) {
                wrap = Math.min(target, wrap + speed);
            } else if (wrap > target) {
                wrap = Math.max(target, wrap - speed);
            }

            LivingEntity living = entity.getEntity();
            if (resting) {
                rootInPlace(living);
                applyRootAttribute(living, true);
                if (!entity.getLevel().isClientSide && wrap >= 0.85F && living.getHealth() < living.getMaxHealth()) {
                    healTicks++;
                    if (healTicks >= HEAL_INTERVAL_TICKS) {
                        healTicks = 0;
                        living.heal(HEAL_AMOUNT);
                    }
                }
            } else {
                healTicks = 0;
                applyRootAttribute(living, false);
            }

            if (!entity.getLevel().isClientSide && resting != wasResting) {
                wasResting = resting;
                sync();
            }
        }

        /** Damage or forced cancel: drop the arm and let the wrap unwind. */
        public void interrupt() {
            if (!armed && wrap <= 0.0F) {
                return;
            }
            armed = false;
            healTicks = 0;
            wasResting = false;
            applyRootAttribute(entity.getEntity(), false);
            if (!entity.getLevel().isClientSide) {
                sync();
            }
        }

        private static void rootInPlace(LivingEntity living) {
            living.setDeltaMovement(new Vec3(0.0D, Math.min(living.getDeltaMovement().y, 0.0D), 0.0D));
            living.hasImpulse = true;
            living.hurtMarked = true;
            living.setSprinting(false);
            living.setJumping(false);
            if (living instanceof Player player) {
                player.setDeltaMovement(0.0D, Math.min(player.getDeltaMovement().y, 0.0D), 0.0D);
            }
        }

        private static void applyRootAttribute(LivingEntity living, boolean rooted) {
            AttributeInstance speed = living.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed == null) {
                return;
            }
            if (rooted) {
                if (!speed.hasModifier(ROOT_SPEED)) {
                    speed.addTransientModifier(ROOT_SPEED);
                }
            } else if (speed.hasModifier(ROOT_SPEED)) {
                speed.removeModifier(ROOT_SPEED);
            }
        }

        @Override
        public void saveData(CompoundTag tag) {
            super.saveData(tag);
            tag.putBoolean("Armed", armed);
            tag.putFloat("Wrap", wrap);
        }

        @Override
        public void readData(CompoundTag tag) {
            super.readData(tag);
            armed = tag.getBoolean("Armed");
            wrap = Mth.clamp(tag.getFloat("Wrap"), 0.0F, 1.0F);
            previousWrap = wrap;
        }

        private void sync() {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("Armed", armed);
            tag.putFloat("Wrap", wrap);
            tag.putBoolean("Resting", wasResting);
            sendPayload(tag);
        }

        @Override
        public void acceptPayload(CompoundTag tag) {
            if (!entity.getLevel().isClientSide) {
                return;
            }
            if (tag.contains("Armed")) {
                armed = tag.getBoolean("Armed");
            }
            // Keep local wrap lerp; only snap if far from server hint.
            if (tag.contains("Wrap")) {
                float serverWrap = tag.getFloat("Wrap");
                if (Math.abs(serverWrap - wrap) > 0.35F) {
                    wrap = serverWrap;
                    previousWrap = serverWrap;
                }
            }
        }
    }
}
