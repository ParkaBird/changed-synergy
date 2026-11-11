package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.ltxprogrammer.changed.ability.AbstractAbility;
import net.ltxprogrammer.changed.ability.GrabEntityAbility;
import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.ltxprogrammer.changed.client.renderer.model.DoubleArmedModel;
import net.ltxprogrammer.changed.client.renderer.model.DoubleHeadedModel;
import net.ltxprogrammer.changed.client.renderer.model.TripleArmedModel;
import net.ltxprogrammer.changed.client.renderer.model.TripleHeadedModel;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.beast.DoubleHeadedEntity;
import net.ltxprogrammer.changed.entity.beast.TripleHeadedEntity;
import net.ltxprogrammer.changed.init.ChangedAbilities;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ai.PlayerHugService;
import net.parkabird.changedsynergy.ai.PlayerHugAccess;

/** Held-body transforms use the native grab link, so bodies are never rendered twice. */
public final class PlayerHugPoses {
    public enum Shape { HUMANOID, PUP, SMALL_FERAL, SOFT, FISH, TAUR }
    private PlayerHugPoses() {}

    public static Shape shape(LivingEntity target) {
        ChangedEntity body = target instanceof ChangedEntity creature ? creature : target instanceof Player player
                ? ProcessTransfur.getPlayerTransfurVariantSafe(player).map(form -> form.getChangedEntity()).orElse(null) : null;
        if (body == null) return Shape.HUMANOID;
        var id = ForgeRegistries.ENTITY_TYPES.getKey(body.getType());
        if (id == null) return Shape.HUMANOID;
        String name = id.getPath();
        if (name.endsWith("_pup") || name.endsWith("_cub")) return Shape.PUP;
        // Addon has separate forms/models for this shared snow-leopard entity family.
        if (name.equals("latex_snep") || name.contains("feral_snep") || name.contains("snep_feral")) return Shape.SMALL_FERAL;
        return switch (name) {
            case "milk_pudding" -> Shape.SOFT;
            case "latex_shark_feral" -> Shape.FISH;
            case "headless_knight" -> Shape.TAUR;
            default -> Shape.HUMANOID;
        };
    }
    public static LivingEntity physical(ChangedEntity creature) {
        return creature.getUnderlyingPlayer() == null ? creature : creature.getUnderlyingPlayer();
    }
    public static GrabEntityAbilityInstance heldBy(LivingEntity target) {
        return GrabEntityAbility.getGrabberSafe(target).map(body -> body.getAbilityInstance(ChangedAbilities.GRAB_ENTITY_ABILITY.get()))
                .filter(grab -> grab.grabbedEntity == target || PlayerHugService.escapePlayer(grab) == target).orElse(null);
    }

    /** Called after native torso + offset + roll, before the held entity's direct render. */
    public static void position(PoseStack pose, LivingEntity holder, GrabEntityAbilityInstance grab, float partial) {
        LivingEntity held = grab.grabbedEntity;
        var kind = shape(held);
        pose.mulPose(Axis.ZP.rotationDegrees(11));
        pose.translate(.0625, 0, .28125);
        switch (kind) {
            case PUP, SMALL_FERAL -> {
                double lift = Math.max(.25, holder.getBbHeight() * .56 - held.getBbHeight() * .5);
                pose.translate(0, -lift, -.5);
                if (kind == Shape.SMALL_FERAL || ((PlayerHugAccess)grab).changedSynergy$isUprightCarry()) {
                    pose.translate(0, .9, 0);
                    pose.mulPose(Axis.XP.rotationDegrees(-90));
                    pose.translate(0, -.9, 0);
                } else {
                    // Cradle across the chest, belly up and head a little higher than the hips.
                    pose.translate(0, -.1, -.1);
                    pose.translate(0, .95, .15);
                    pose.mulPose(Axis.ZP.rotationDegrees(32));
                    pose.mulPose(Axis.YP.rotationDegrees(90));
                    pose.mulPose(Axis.ZP.rotationDegrees(200));
                    pose.translate(0, -.95, -.15);
                }
            }
            case SOFT -> pose.translate(0, -Math.max(.2, holder.getBbHeight() * .55 - held.getBbHeight() * .45), -.48);
            case FISH -> {
                // Nose up, belly supported; rotate about the fish's body, not its feet.
                pose.translate(0, -1.5, -.62);
                pose.translate(0, 1.3125, .125);
                pose.mulPose(Axis.YP.rotationDegrees(90));
                pose.mulPose(Axis.ZP.rotationDegrees(15));
                pose.mulPose(Axis.XP.rotationDegrees(-80));
                pose.translate(0, -1.3125, -.125);
            }
            // Lift the forequarters while the longer hindquarters hang clear of the floor.
            case TAUR -> {
                pose.translate(0, -.65, -.35);
                pose.translate(0, .75, 0);
                pose.mulPose(Axis.XP.rotationDegrees(-65));
                pose.translate(0, -.75, 0);
            }
            case HUMANOID -> pose.translate(0, -Math.min(.25, Math.max(0, holder.getBbHeight() - held.getBbHeight()) * .35), -.38);
        }
    }

    public static void animate(ChangedEntity creature, AdvancedHumanoidModel<?> model, float age) {
        var entity = physical(creature);
        var holding = AbstractAbility.getAbilityInstance(entity, ChangedAbilities.GRAB_ENTITY_ABILITY.get());
        if (holding != null && PlayerHugService.active(holding) && holding.grabbedEntity != null
                && !net.parkabird.changedsynergy.ai.EagleOwlTraits.airborne(entity)) {
            float blend = Mth.clamp((holding.ticksGrabbed + age - creature.tickCount) / 8F, 0, 1);
            Shape heldShape = shape(holding.grabbedEntity);
            float pitch = switch (heldShape) {
                case SOFT -> -.9F;
                case PUP -> ((PlayerHugAccess)holding).changedSynergy$isUprightCarry() ? -1.5F : -1.05F;
                case SMALL_FERAL -> -1.05F;
                case FISH -> -1.3F;
                case TAUR -> -1.75F;
                default -> -1.15F;
            };
            for (HumanoidArm side : HumanoidArm.values()) {
                if (model instanceof DoubleArmedModel<?> multi) {
                    turn(multi.getOtherArm(side), pitch + .15F, side == HumanoidArm.LEFT ? .2F : -.2F,
                            side == HumanoidArm.LEFT ? .06F : -.06F, blend);
                    if (model instanceof TripleArmedModel<?> triple)
                        turn(triple.getMiddleArm(side), pitch, side == HumanoidArm.LEFT ? .16F : -.16F, 0, blend);
                }
                var arm = arm(model, side);
                if (arm == null) continue;
                float yaw = heldShape == Shape.FISH || heldShape == Shape.TAUR ? .05F : heldShape == Shape.HUMANOID ? .23F : .1F;
                float roll = heldShape == Shape.FISH || heldShape == Shape.TAUR ? .03F : .08F;
                float armPitch = pitch;
                if (heldShape == Shape.FISH) { armPitch = side == HumanoidArm.LEFT ? -1.4F : -.95F; yaw = .12F; roll = .05F; }
                if (heldShape == Shape.PUP) {
                    if (((PlayerHugAccess)holding).changedSynergy$isUprightCarry()) yaw = .28F;
                    else {
                        armPitch = side == HumanoidArm.LEFT ? -.8F : -1.2F;
                        turn(arm, armPitch, side == HumanoidArm.LEFT ? .12F : -.12F, .35F, blend);
                        continue;
                    }
                }
                turn(arm, armPitch, side == HumanoidArm.LEFT ? yaw : -yaw,
                        side == HumanoidArm.LEFT ? roll : -roll, blend);
            }
            boolean coversFace = switch (heldShape) {
                case SOFT, TAUR -> false;
                case PUP -> ((PlayerHugAccess)holding).changedSynergy$isUprightCarry();
                case FISH, SMALL_FERAL -> true;
                case HUMANOID -> holding.grabbedEntity.getBbHeight()
                        + Math.min(.25, Math.max(0, entity.getBbHeight() - holding.grabbedEntity.getBbHeight()) * .35)
                        >= entity.getBbHeight() * .85;
            };
            if (coversFace) {
                // Tilt only for face-level carries, preserving the neck and normal look direction.
                var head = model.getHead();
                head.zRot = Mth.lerp(blend, head.zRot, .25F);
            }
        }
        var grab = heldBy(entity);
        if (grab == null || !PlayerHugService.active(grab)) return;
        float blend = Mth.clamp((grab.ticksGrabbed + age - creature.tickCount) / 8F, 0, 1);
        float partial = age - creature.tickCount;
        float bodyYaw = Mth.rotLerp(partial, creature.yBodyRotO, creature.yBodyRot);
        // Mirroring a carrier across +/-180 degrees must not flip an independent head to the other limit.
        if (model instanceof TripleHeadedModel<?> heads && creature instanceof TripleHeadedEntity rotations) {
            model.getHead().yRot = Mth.clamp(Mth.wrapDegrees(rotations.getLeftHeadYRot(partial) - bodyYaw), -90, 10) * Mth.DEG_TO_RAD;
            heads.getOtherHead().yRot = Mth.clamp(Mth.wrapDegrees(rotations.getRightHeadYRot(partial) - bodyYaw), -10, 90) * Mth.DEG_TO_RAD;
        } else if (model instanceof DoubleHeadedModel<?> heads && creature instanceof DoubleHeadedEntity rotations) {
            heads.getOtherHead().yRot = Mth.clamp(Mth.wrapDegrees(rotations.getHead2YRot(partial) - bodyYaw), -90, 90) * Mth.DEG_TO_RAD;
        }
        Shape shape = shape(entity);
        if (shape == Shape.PUP || shape == Shape.SMALL_FERAL) {
            boolean upright = shape == Shape.SMALL_FERAL || ((PlayerHugAccess)grab).changedSynergy$isUprightCarry();
            for (HumanoidArm side : HumanoidArm.values()) {
                var paw = arm(model, side);
                turn(paw, upright ? -.15F : -.75F, 0, side == HumanoidArm.LEFT ? -.08F : .08F, blend);
                if (shape == Shape.PUP && upright && paw != null) {
                    // Keep the forelegs at their shoulders, separated enough to clear the oversized pup head.
                    paw.x = Mth.lerp(blend, paw.x, side == HumanoidArm.LEFT ? 2.5F : -2.5F);
                    paw.z = Mth.lerp(blend, paw.z, paw.getInitialPose().z + .75F);
                }
                turn(leg(model, side), upright ? Mth.HALF_PI : 1.15F, 0, side == HumanoidArm.LEFT ? -.06F : .06F, blend);
            }
            turn(model.getHead(), upright ? Mth.HALF_PI : 0, 0, 0, blend);
            if (shape == Shape.PUP && upright) {
                var head = model.getHead();
                head.z = Mth.lerp(blend, head.z, head.getInitialPose().z - 4F);
            } else if (shape == Shape.PUP) {
                var head = model.getHead();
                head.z = Mth.lerp(blend, head.z, head.getInitialPose().z + 1.5F);
            }
            if (shape == Shape.PUP) turn(model.getTorso(), 0, 0, 0, blend);
            if (model.getTorso().hasChild("Tail")) {
                var tail = model.getTorso().getChild("Tail");
                if (shape == Shape.PUP) {
                    // Native spring/ride angles can separate the segmented tail in a carried pose.
                    turn(tail, 0, 0, 0, blend);
                    for (String joint : new String[]{"TailPrimary", "TailSecondary", "TailTertiary"}) {
                        tail = tail.getChild(joint);
                        turn(tail, tail.getInitialPose().xRot, 0, 0, blend);
                    }
                } else if (upright) tail.yRot = Mth.lerp(blend, tail.yRot, 1.15F);
            }
        } else if (shape == Shape.TAUR) {
            for (HumanoidArm side : HumanoidArm.values()) {
                turn(arm(model, side), .7F, 0, side == HumanoidArm.LEFT ? -.07F : .07F, blend);
                turn(leg(model, side), 65 * Mth.DEG_TO_RAD, 0, side == HumanoidArm.LEFT ? -.07F : .07F, blend);
            }
        }
    }
    public static void animateFish(ChangedEntity creature, net.ltxprogrammer.changed.client.renderer.model.SharkModel<?> model, float age) {
        var grab = heldBy(physical(creature));
        boolean held = grab != null && PlayerHugService.active(grab);
        float blend = held ? Mth.clamp((grab.ticksGrabbed + age - creature.tickCount) / 8F, 0, 1) : 0;
        var tail = model.getAbdomen().getChild("LowerAbdomen").getChild("Tail");
        // A relaxed curl keeps the full-sized upright fish's fin above the ground.
        tail.xRot = Mth.lerp(blend, tail.getInitialPose().xRot, .65F);
        var tip = tail.getChild("TailPrimary");
        tip.xRot = Mth.lerp(blend, tip.getInitialPose().xRot, .35F);
    }
    private static ModelPart arm(AdvancedHumanoidModel<?> model, HumanoidArm side) {
        try { return model.getArm(side); } catch (NullPointerException | IllegalArgumentException | UnsupportedOperationException e) { return null; }
    }
    private static ModelPart leg(AdvancedHumanoidModel<?> model, HumanoidArm side) {
        try { return model.getLeg(side); } catch (NullPointerException | IllegalArgumentException | UnsupportedOperationException e) { return null; }
    }
    private static void turn(ModelPart part, float x, float y, float z, float blend) {
        if (part == null) return;
        part.xRot = Mth.lerp(blend, part.xRot, x); part.yRot = Mth.lerp(blend, part.yRot, y); part.zRot = Mth.lerp(blend, part.zRot, z);
    }
}
