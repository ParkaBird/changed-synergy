package net.parkabird.changedsynergy.regression;

import java.util.List;
import net.ltxprogrammer.changed.data.AccessorySlots;
import net.ltxprogrammer.changed.entity.AccessoryEntities;
import net.ltxprogrammer.changed.init.ChangedAccessorySlots;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.ltxprogrammer.changed.init.ChangedItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.event.HyenaTrailclothSpawns;
import net.parkabird.changedsynergy.init.ChangedSynergyEntities;
import net.parkabird.changedsynergy.init.ChangedSynergyItems;
import net.parkabird.changedsynergy.item.HyenaTrailclothItem;

final class TrailclothServerChecks {
    static void run(MinecraftServer server) {
        var level = server.overworld(); var outfit = ChangedSynergyItems.HYENA_TRAILCLOTH_OUTFIT.get();
        var chance = ChangedSynergyConfig.COMMON.hyenaTrailclothSpawnChance; double previous = chance.get();
        try {
            check(outfit instanceof net.ltxprogrammer.changed.item.Clothing && !net.minecraft.world.item.ArmorItem.class.isInstance(outfit), "native clothing, not armor");
            for (var type : List.of(ChangedSynergyEntities.LATEX_SPOTTED_HYENA_MALE.get(), ChangedSynergyEntities.LATEX_SPOTTED_HYENA_FEMALE.get(),
                    ChangedEntities.WHITE_LATEX_WOLF_MALE.get(), ChangedEntities.WHITE_LATEX_WOLF_FEMALE.get(), ChangedEntities.LATEX_SNAKE.get(), ChangedEntities.LATEX_MERMAID_SHARK.get())) {
                var mob = type.create(level); AccessoryEntities.INSTANCE.forceReloadAccessories(mob);
                mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
                var slots = AccessorySlots.getForEntity(mob).orElseThrow(); var stack = new ItemStack(outfit);
                check(HyenaTrailclothItem.fits(mob) && ChangedAccessorySlots.BODY.get().canHoldItem(stack, mob), "adult body accepted: " + type);
                check(slots.quickMoveStack(stack) && stack.isEmpty(), "native accessory equip: " + type);
                check(mob.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(net.minecraft.world.item.Items.IRON_CHESTPLATE), "clothing does not replace chest armor");
                var saved = mob.saveWithoutId(new CompoundTag()); var restored = type.create(level); restored.load(saved);
                check(AccessorySlots.isWearing(restored, s -> s.is(outfit)), "outfit persists after save/load");
                mob.discard(); restored.discard();
            }
            for (var type : List.of(ChangedEntities.DARK_LATEX_WOLF_PUP.get(), ChangedEntities.PURE_WHITE_LATEX_WOLF_PUP.get(), ChangedEntities.FERAL_LATEX_SHARK.get()))
                check(!HyenaTrailclothItem.fits(type.create(level)), "juvenile/feral rejected: " + type);
            var male = ChangedSynergyEntities.LATEX_SPOTTED_HYENA_MALE.get().create(level);
            var female = ChangedSynergyEntities.LATEX_SPOTTED_HYENA_FEMALE.get().create(level);
            check(!outfit.getTexture(new ItemStack(outfit), male).equals(outfit.getTexture(new ItemStack(outfit), female)), "native gender switches texture");
            check(!HyenaTrailclothItem.hasLowerSection(ChangedEntities.LATEX_SNAKE.get().create(level))
                    && !HyenaTrailclothItem.hasLowerSection(ChangedEntities.LATEX_MERMAID_SHARK.get().create(level)), "tail bodies keep their original lower body");
            AccessoryEntities.INSTANCE.forceReloadAccessories(male);
            var slots = AccessorySlots.getForEntity(male).orElseThrow(); slots.setItem(ChangedAccessorySlots.LEGS.get(), new ItemStack(ChangedItems.BLACK_PANTS.get()));
            check(!outfit.allowedInSlot(new ItemStack(outfit), male, ChangedAccessorySlots.BODY.get()), "existing trousers block complete outfit");
            check(server.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("changed_synergy", "hyena_trailcloth_outfit")).isPresent(), "crafting recipe loaded");
            for (var reason : List.of(MobSpawnType.NATURAL, MobSpawnType.CHUNK_GENERATION, MobSpawnType.STRUCTURE, MobSpawnType.SPAWN_EGG, MobSpawnType.COMMAND)) {
                chance.set(1D); var fresh = ChangedSynergyEntities.LATEX_SPOTTED_HYENA_FEMALE.get().create(level); fresh.moveTo(0, 80, 0);
                ForgeEventFactory.onFinalizeSpawn(fresh, level, level.getCurrentDifficultyAt(fresh.blockPosition()), reason, null, null);
                check(AccessorySlots.isWearing(fresh, s -> s.is(outfit)) == (reason != MobSpawnType.SPAWN_EGG && reason != MobSpawnType.COMMAND), "spawn-reason guard: " + reason);
                fresh.discard();
            }
            chance.set(0D); var disabled = ChangedSynergyEntities.LATEX_SPOTTED_HYENA_MALE.get().create(level);
            disabled.addTag("changed_synergy:hyena_structure_resident");
            HyenaTrailclothSpawns.structureResident(new EntityJoinLevelEvent(disabled, level));
            check(!AccessorySlots.isWearing(disabled, s -> s.is(outfit)), "zero chance disables spawn outfit");
            chance.set(1D); HyenaTrailclothSpawns.structureResident(new EntityJoinLevelEvent(disabled, level));
            check(!AccessorySlots.isWearing(disabled, s -> s.is(outfit)), "failed roll is not repeated");
            var resident = ChangedSynergyEntities.LATEX_SPOTTED_HYENA_MALE.get().create(level); resident.addTag("changed_synergy:hyena_structure_resident");
            HyenaTrailclothSpawns.structureResident(new EntityJoinLevelEvent(resident, level));
            check(AccessorySlots.isWearing(resident, s -> s.is(outfit)), "new template resident receives outfit");
            var loaded = ChangedSynergyEntities.LATEX_SPOTTED_HYENA_MALE.get().create(level); loaded.addTag("changed_synergy:hyena_structure_resident");
            HyenaTrailclothSpawns.structureResident(new EntityJoinLevelEvent(loaded, level, true));
            check(!AccessorySlots.isWearing(loaded, s -> s.is(outfit)), "existing residents are not rerolled on disk load");
            System.out.println("TRAILCLOTH_SERVER_PASS: slots, genders, anatomy, recipe, save/load, spawn reasons, chance and no rerolls");
        } finally { chance.set(previous); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
