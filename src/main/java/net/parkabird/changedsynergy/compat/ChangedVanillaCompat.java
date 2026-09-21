package net.parkabird.changedsynergy.compat;

import java.util.Set;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.FactionReputation;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;

/** Optional compatibility boundary for the official Changed Vanilla addon. */
public final class ChangedVanillaCompat {
    public static final String MOD_ID = "changedvanilla";
    private static final double RESPECTED_HUMAN_FARM_RADIUS = 32.0D;
    private static final Set<EntityType<?>> CONVERTIBLE_ANIMALS = Set.of(
            EntityType.CAT,
            EntityType.CHICKEN,
            EntityType.COW,
            EntityType.FOX,
            EntityType.OCELOT,
            EntityType.PIG,
            EntityType.SHEEP);

    private ChangedVanillaCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * Changed Vanilla deliberately registers fixed conversion forms for mobs
     * such as zombies and skeletons. Installing that addon is an explicit
     * request for those forms, so Synergy must not rewrite the decision to its
     * population-saving absorption fallback.
     */
    public static boolean suppliesDedicatedConversion(
            LatexAssimilationDecision<?> decision) {
        if (!isLoaded() || decision == null
                || decision.transfurVariant() == null) {
            return false;
        }
        ResourceLocation formId = decision.transfurVariant().getFormId();
        return formId != null && MOD_ID.equals(formId.getNamespace());
    }

    /** Keeps a respected human's nearby livestock out of Changed-V infection chains. */
    public static boolean protectsAnimalNearRespectedHuman(
            ChangedEntity source,
            LivingEntity animal) {
        if (!isLoaded()
                || source == null
                || animal == null
                || source.level() != animal.level()
                || !(source.level() instanceof ServerLevel level)
                || !LatexSocialMemory.isSocialLatex(source)
                || !CONVERTIBLE_ANIMALS.contains(animal.getType())) {
            return false;
        }
        return level.getEntitiesOfClass(
                        ServerPlayer.class,
                        animal.getBoundingBox().inflate(
                                RESPECTED_HUMAN_FARM_RADIUS),
                        player -> player.isAlive()
                                && !player.isSpectator()
                                && !ProcessTransfur.isPlayerTransfurred(player))
                .stream()
                .filter(player -> FactionReputation.isRespected(source, player))
                .anyMatch(player ->
                        LatexSocialMemory.shouldRemainNeutral(source, player));
    }

    /**
     * LatexSkeleton rebuilds its melee/bow goals from setItemSlot. Calling it
     * from inside another goal therefore invalidates GoalSelector's iterator.
     */
    public static boolean equipmentChangeRebuildsGoals(Entity entity) {
        if (!isLoaded() || entity == null) {
            return false;
        }
        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return typeId != null
                && MOD_ID.equals(typeId.getNamespace())
                && "latex_skeleton".equals(typeId.getPath());
    }

    public static void logIntegration() {
        ModList.get().getModContainerById(MOD_ID).ifPresent(container ->
                ChangedSynergyMod.LOGGER.info(
                        "Enabled Changed Vanilla {} conversion and community integration",
                        container.getModInfo().getVersion()));
    }
}
