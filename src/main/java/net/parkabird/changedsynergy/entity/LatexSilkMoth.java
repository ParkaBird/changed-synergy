package net.parkabird.changedsynergy.entity;

import net.ltxprogrammer.changed.entity.beast.LatexMoth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.SilkMothWingFlapPacket;

/** A four-armed moth with Changed's original moth behaviour. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class LatexSilkMoth extends LatexMoth {
    private long wingFlapStart = Long.MIN_VALUE;

    public LatexSilkMoth(EntityType<? extends LatexSilkMoth> type, Level level) {
        super(type, level);
    }

    @Override protected void registerGoals() {
        super.registerGoals();
        // Prefer a nearby safe light over aimless strolling, below work and social tasks.
        var wandering = goalSelector.getAvailableGoals().stream()
                .filter(g -> g.getGoal() instanceof net.minecraft.world.entity.ai.goal.RandomStrollGoal)
                .map(g -> g.getGoal()).toList();
        for (var goal : wandering) {
            goalSelector.removeGoal(goal);
            goalSelector.addGoal(4,goal);
        }
        goalSelector.addGoal(3,new net.parkabird.changedsynergy.ai.MothLightRestGoal(this));
    }

    public void flapWings() {
        wingFlapStart = level().getGameTime();
    }

    public float getWingFlapTime(float partialTicks) {
        return wingFlapStart == Long.MIN_VALUE ? Float.POSITIVE_INFINITY
                : level().getGameTime() - wingFlapStart + partialTicks;
    }

    @SubscribeEvent
    public static void onExtraJump(LivingJumpEvent event) {
        var actor=event.getEntity();if(actor.onGround())return;
        var body=net.ltxprogrammer.changed.ability.IAbstractChangedEntity.forEitherSafe(actor)
                .map(net.ltxprogrammer.changed.ability.IAbstractChangedEntity::getChangedEntity).orElse(null);
        if(!(body instanceof LatexSilkMoth)&&!(body instanceof LatexEagleOwl))return;
        if(body instanceof LatexSilkMoth moth)moth.flapWings();
        if(body instanceof LatexEagleOwl owl)owl.flapWings();
        if(!actor.level().isClientSide){
            // Predict locally, then animate observers of either the player or an Instinct body.
            ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(()->actor),new SilkMothWingFlapPacket(actor.getId()));
        }
    }
}
