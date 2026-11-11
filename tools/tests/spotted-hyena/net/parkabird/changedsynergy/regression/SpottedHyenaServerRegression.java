package net.parkabird.changedsynergy.regression;

import java.util.List;
import net.ltxprogrammer.changed.entity.Gender;
import net.ltxprogrammer.changed.entity.TransfurMode;
import net.ltxprogrammer.changed.init.ChangedItems;
import net.ltxprogrammer.changed.init.ChangedAttributes;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.ltxprogrammer.changed.item.Syringe;
import net.ltxprogrammer.changed.recipe.InfuserRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.init.*;
import net.parkabird.changedsynergy.origin.NativeOriginCatalog;
import net.parkabird.changedsynergy.world.ResearchLabStructure;

@Mod.EventBusSubscriber(modid="changed_synergy", value=Dist.DEDICATED_SERVER)
public final class SpottedHyenaServerRegression {
    private static int checks;
    @SubscribeEvent public static void run(ServerStartedEvent event) {
        if (Boolean.getBoolean("changed_synergy.hyenaHabitat")) return;
        net.parkabird.changedsynergy.ChangedSynergyConfig.SPEC.setConfig(com.electronwill.nightconfig.core.CommentedConfig.inMemory());
        var server = event.getServer();
        try {
            var level = server.overworld();
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            var biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
            var male = ChangedSynergyEntities.LATEX_SPOTTED_HYENA_MALE.get();
            var female = ChangedSynergyEntities.LATEX_SPOTTED_HYENA_FEMALE.get();
            var forms = List.of(ChangedSynergyTransfurVariants.LATEX_SPOTTED_HYENA_MALE.get(), ChangedSynergyTransfurVariants.LATEX_SPOTTED_HYENA_FEMALE.get());
            for (var type : List.of(male, female)) {
                var npc = type.create(level);
                npc.moveTo(8, 80, 8);
                check(npc.getGender() == (type == female ? Gender.FEMALE : Gender.MALE), "native gender");
                check(npc.getSelfVariant().getEntityType() == type, "NPC self variant");
                check(npc.getTransfurMode() == TransfurMode.REPLICATION, "replication transfur");
                check(npc.getMaxHealth() == (type == female ? 26 : 24) && npc.getHealth() == npc.getMaxHealth()
                        && npc.getAttributeValue(Attributes.ATTACK_DAMAGE) == 3
                        && npc.getAttributeValue(Attributes.ARMOR) == 4, "native terrestrial combat attributes");
                check(npc.getAttributeValue(Attributes.MOVEMENT_SPEED) == (type == female ? 1.13 : 1.075)
                        && npc.getAttributeValue(ForgeMod.SWIM_SPEED.get()) == .95, "native terrestrial movement");
                check(npc.getAttributeValue(ChangedAttributes.JUMP_STRENGTH.get()) == (type == female ? 1.05 : 1), "gendered jump strength");
                var jump = net.minecraft.world.entity.LivingEntity.class.getDeclaredMethod("m_6118_"); jump.setAccessible(true);
                check(Math.abs((Float)jump.invoke(npc) - .42F * (type == female ? 1.05F : 1)) < .00001F, "native jump power uses gendered strength");
                check(HunterFaction.of(npc) == HunterFaction.LIGHT && LightFactionGroup.of(npc).equals("savanna"), "stable savanna allegiance");
                check(CreatureSocialProfile.allowsSynergySystems(npc), "normal social participation");
                var saved = new CompoundTag(); npc.saveWithoutId(saved); var loaded = type.create(level); loaded.load(saved);
                check(loaded.getGender() == npc.getGender() && LightFactionGroup.of(loaded).equals("savanna"), "saved identity");
                check(loaded.getMaxHealth() == npc.getMaxHealth() && loaded.getHealth() == npc.getHealth()
                        && loaded.getAttributeValue(Attributes.MOVEMENT_SPEED) == npc.getAttributeValue(Attributes.MOVEMENT_SPEED)
                        && loaded.getAttributeValue(ChangedAttributes.JUMP_STRENGTH.get()) == npc.getAttributeValue(ChangedAttributes.JUMP_STRENGTH.get()), "saved gendered attributes");
                var origin = NativeOriginCatalog.find(level.registryAccess(), npc.getSelfVariant().getFormId());
                check(origin != null && origin.region().equals("savanna") && origin.faction().equals("light"), "playable archive origin");
                check(SpawnPlacements.getPlacementType(type) == SpawnPlacements.Type.ON_GROUND, "terrestrial spawn placement");
                var spawn = new BlockPos(8, 80, 8); level.setBlock(spawn.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 18);
                level.setBlock(spawn, Blocks.AIR.defaultBlockState(), 18); level.setBlock(spawn.above(), Blocks.AIR.defaultBlockState(), 18); level.setDayTime(18000); level.updateSkyBrightness();
                boolean allowed = false;
                for (int i=0; i<256; i++) allowed |= SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, spawn, RandomSource.create(i));
                check(allowed, "real natural predicate accepts dry habitat");
                check(level.getServer().getLootData().getLootTable(type.getDefaultLootTable()) != net.minecraft.world.level.storage.loot.LootTable.EMPTY, "own loot table");
                for (var biome : biomes.holders().toList()) {
                    var entries = biome.value().getMobSettings().getMobs(MobCategory.MONSTER).unwrap().stream().filter(s -> s.type == type).toList();
                    boolean tropical = biome.is(BiomeTags.IS_JUNGLE) || biome.is(BiomeTags.IS_SAVANNA);
                    check(entries.isEmpty() != tropical, "no global non-tropical spawn: " + biome.key().location());
                    if (tropical) check(entries.size() == 1 && entries.get(0).getWeight().asInt() == 4
                            && entries.get(0).minCount == 1 && entries.get(0).maxCount == 2, "balanced tropical spawn weight");
                }
            }
            check(ChangedTransfurVariants.Gendered.getOpposite(forms.get(0)).orElseThrow() == forms.get(1), "native pair");
            var recipe = (InfuserRecipe)server.getRecipeManager().byKey(id("form_latex_spotted_hyena")).orElseThrow();
            for (int i=0; i<2; i++) {
                var form = forms.get(i);
                for (var input : InfuserRecipe.getAllowedInputs()) {
                    var result = recipe.processItem(input.copy(), i == 0 ? Gender.MALE : Gender.FEMALE);
                    check(Syringe.getVariant(result) == form && !result.getOrCreateTag().getBoolean("safe"), "native gendered recipe output");
                }
                for (var item : List.of(ChangedItems.LATEX_SYRINGE.get(), ChangedItems.LATEX_FLASK.get(), ChangedItems.LATEX_TIPPED_ARROW.get()))
                    check(Syringe.getVariant(Syringe.setPureVariant(new ItemStack(item), form.getFormId())) == form, "transfur item form roundtrip");
            }
            int index = 0;
            for (var name : List.of("hyena_hideout", "hyena_camp_tropical", "hyena_camp_open", "hyena_camp_snowy")) {
                var structure = server.registryAccess().registryOrThrow(Registries.STRUCTURE).get(id(name));
                check(structure != null && structure.spawnOverrides().get(MobCategory.MONSTER).spawns().unwrap().size() == 1, "local male replenishment: " + name);
                // Install native structure references to exercise the same spawn-list lookup as NaturalSpawner.
                var origin = new BlockPos(1024 + index++ * 256, 80, 1024);
                var piece = new ResearchLabStructure.Piece(server.getStructureManager(), id(name), origin, Rotation.NONE, false);
                var start = new StructureStart(structure, new ChunkPos(origin), 0, new PiecesContainer(List.of(piece)));
                level.getChunkAt(origin).setStartForStructure(structure, start);
                var box = start.getBoundingBox();
                var inside = new BlockPos((box.minX()+box.maxX())/2, box.minY()+1, (box.minZ()+box.maxZ())/2);
                level.getChunkAt(inside).addReferenceForStructure(structure, new ChunkPos(origin).toLong());
                var outside = new BlockPos(box.maxX()+1, inside.getY(), inside.getZ());
                for (var habitat : List.of(Biomes.PLAINS, Biomes.DESERT, Biomes.SNOWY_PLAINS)) {
                    var biome = biomes.getHolderOrThrow(habitat);
                    var local = level.getChunkSource().getGenerator().getMobsAt(biome, level.structureManager(), MobCategory.MONSTER, inside).unwrap();
                    check(local.stream().anyMatch(s -> s.type == male) && local.stream().noneMatch(s -> s.type == female), "camp replenishment is male only in " + habitat);
                    var distant = level.getChunkSource().getGenerator().getMobsAt(biome, level.structureManager(), MobCategory.MONSTER, outside).unwrap();
                    check(distant.stream().noneMatch(s -> s.type == male || s.type == female), "camp spawns stop at local boundary");
                }
            }
            SpottedHyenaDietRegression.run(server);
            TrailclothServerChecks.run(server);
            CuriosClothingServerChecks.run(server);
            System.out.println("SPOTTED_HYENA_SERVER_PASS: " + checks + " gender, attributes, social identity, origins, items, tropical and structure-local spawning checks");
        } catch(Throwable failure) {
            System.err.println("SPOTTED_HYENA_SERVER_FAIL"); failure.printStackTrace();
        } finally { server.halt(false); }
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("changed_synergy", path); }
    private static void check(boolean value, String text) { if (!value) throw new AssertionError(text); checks++; }
}
