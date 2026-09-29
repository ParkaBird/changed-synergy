package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

/** The owner's job is SUPPLY; this describes what that species gathers. */
public enum PlayerOutpostSupplyType {
    ORANGE, CAVE, BERRIES, FISH, NECTAR, FORAGE;

    public static PlayerOutpostSupplyType of(ChangedEntity mob) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if (id != null && id.getNamespace().equals("changed")
                && id.getPath().equals("latex_bee")) return NECTAR;
        if (CreatureSettlementService.isCaveCommunity(mob)) return CAVE;
        if (CreatureSettlementService.isTaigaCommunity(mob)) return BERRIES;
        HunterArchetype archetype = HunterArchetype.of(mob);
        if (archetype == HunterArchetype.AQUATIC
                || archetype == HunterArchetype.FELINE) return FISH;
        if (CreatureSettlementService.usesClimateHunting(mob)) return FORAGE;
        return ORANGE;
    }

    public static PlayerOutpostSupplyType parse(String value) {
        try { return valueOf(value); }
        catch (IllegalArgumentException exception) { return ORANGE; }
    }
}
