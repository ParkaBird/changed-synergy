package net.parkabird.changedsynergy.event;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision;
import net.ltxprogrammer.changed.init.ChangedTags;
import net.ltxprogrammer.changed.process.TransfurEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.EquipmentInheritance;
import net.parkabird.changedsynergy.ai.EquipmentInheritance.EquipmentSnapshot;

/**
 * Keeps expensive replicated humanoids for conscious populations, while
 * mindless undead are consumed without creating another active entity.
 */
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AssimilationEcologyEvents {
    private static final TagKey<EntityType<?>> SENTIENT_TARGETS =
            tag("sentient_assimilation_targets");
    private static final TagKey<EntityType<?>> MINDLESS_TARGETS =
            tag("mindless_absorption_targets");

    private AssimilationEcologyEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLatexAssimilation(
            TransfurEvents.LatexAssimilationDecisionEvent event) {
        LivingEntity victim = event.getEntity();
        LatexAssimilationDecision<?> decision = event.getDecision();
        if (victim instanceof Player || decision == null) {
            return;
        }

        if (victim.getType().is(MINDLESS_TARGETS)) {
            if (decision.method()
                    != LatexAssimilationDecision.Method.ABSORPTION) {
                event.setDecision(forceAbsorption(decision));
                decision = event.getDecision();
            }
        } else if (victim.getType().is(
                        ChangedTags.EntityTypes.HUMANOIDS)
                && !victim.getType().is(SENTIENT_TARGETS)) {
            event.setCanceled(true);
            return;
        }

        if (decision.method()
                        == LatexAssimilationDecision.Method.ABSORPTION
                && victim instanceof Mob
                && event.getSourceEntity()
                        instanceof ChangedEntity absorber) {
            EquipmentSnapshot equipment =
                    EquipmentInheritance.capture(victim);
            event.appendTransfurListener(ignored ->
                    EquipmentInheritance.transfer(
                            equipment, absorber));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onOrganicAssimilation(
            TransfurEvents.NonLatexAssimilationDecisionEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim instanceof Player || event.getDecision() == null) {
            return;
        }
        if (victim.getType().is(MINDLESS_TARGETS)
                || victim.getType().is(
                                ChangedTags.EntityTypes.HUMANOIDS)
                        && !victim.getType().is(SENTIENT_TARGETS)) {
            event.setCanceled(true);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static LatexAssimilationDecision<?> forceAbsorption(
            LatexAssimilationDecision<?> decision) {
        return new LatexAssimilationDecision(
                decision.decisionStrength(),
                LatexAssimilationDecision.Method.ABSORPTION,
                decision.transfurVariant(),
                decision.context(),
                decision.transfurProgress(),
                decision.postTransfurListener());
    }

    private static TagKey<EntityType<?>> tag(String path) {
        return TagKey.create(
                Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(
                        ChangedSynergyMod.MOD_ID, path));
    }
}
