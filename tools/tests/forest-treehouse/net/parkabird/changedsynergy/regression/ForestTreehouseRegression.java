package net.parkabird.changedsynergy.regression;

import java.util.*;
import net.ltxprogrammer.changed.data.AccessorySlots;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedAccessorySlots;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ai.StructureResidentGoal;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.world.ResearchLabStructure;

@Mod.EventBusSubscriber(modid="changed_synergy", value=Dist.DEDICATED_SERVER)
public final class ForestTreehouseRegression {
    private static MinecraftServer server;
    private static ServerLevel level;
    private static int checks, ticks;
    private static final List<ChangedEntity> actors = new ArrayList<>();
    private static final List<ResearchLabStructure.Piece> layouts = new ArrayList<>();
    private static boolean started;
    private static ResourceLocation id(String path) { return new ResourceLocation("changed_synergy",path); }
    private static void check(boolean good, String message) { if (!good) throw new AssertionError(message); checks++; }

    @SubscribeEvent public static void start(ServerStartedEvent event) {
        if(Boolean.getBoolean("changed_synergy.naturalCampSleep"))return;
        server=event.getServer(); level=server.overworld();
        net.parkabird.changedsynergy.ChangedSynergyConfig.SPEC.setConfig(com.electronwill.nightconfig.core.CommentedConfig.inMemory());
        try {
            level.getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(false,server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false,server);
            if(Boolean.getBoolean("changed_synergy.campOnly")) {
                net.parkabird.changedsynergy.ai.NaturalCampChecks.prepare(level);started=true;return;
            }
            var registry=server.registryAccess().registryOrThrow(Registries.STRUCTURE);
            var managerField=Arrays.stream(ServerLevel.class.getDeclaredFields())
                    .filter(f->net.minecraft.world.level.entity.PersistentEntitySectionManager.class.isAssignableFrom(f.getType())).findFirst().orElseThrow();
            managerField.setAccessible(true);
            var entityManager=(net.minecraft.world.level.entity.PersistentEntitySectionManager<?>)managerField.get(level);
            var structure=registry.get(id("forest_treehouse"));
            check(structure instanceof ResearchLabStructure,"treehouse structure codec");
            for(var biome:server.registryAccess().registryOrThrow(Registries.BIOME).holders().toList())
                check(structure.biomes().contains(biome)==biome.key().location().toString().equals("minecraft:forest"),"only normal forest");
            natural(structure,"forest_treehouses");
            int layout=0;
            var tower=registry.get(id("forest_watchtower"));check(tower instanceof ResearchLabStructure,"watchtower registered");
            for(var biome:server.registryAccess().registryOrThrow(Registries.BIOME).holders().toList())
                check(tower.biomes().contains(biome)==biome.key().location().toString().equals("minecraft:forest"),"watchtower only normal forest");
            natural(tower,"forest_watchtowers");
            for(var rotation:Rotation.values()) {
                var anchor=new BlockPos(12000+(layout++)*192,160,12000);
                var piece=new ResearchLabStructure.Piece(server.getStructureManager(),id("forest_watchtower"),anchor,rotation,false,2);
                layouts.add(piece);var box=piece.getBoundingBox();
                for(int cx=box.minX()>>4;cx<=box.maxX()>>4;cx++)for(int cz=box.minZ()>>4;cz<=box.maxZ()>>4;cz++) {
                    level.getChunk(cx,cz);level.setChunkForced(cx,cz,true);
                    entityManager.updateChunkStatus(new ChunkPos(cx,cz),net.minecraft.server.level.FullChunkStatus.ENTITY_TICKING);
                    var chunk=new ChunkPos(cx,cz);piece.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(4),clip(chunk),chunk,anchor);
                }
                entityManager.tick();
                check(level.getEntitiesOfClass(ChangedEntity.class,AABB.of(box)).isEmpty(),"watchtower adds no creatures");
                check(level.getEntitiesOfClass(HangingEntity.class,AABB.of(box)).size()==3,"watchtower retains three authored frames");
            }
            for(String name:List.of("forest_treehouse","hyena_hideout","green_lab")) for(var rotation:Rotation.values()) {
                var anchor=new BlockPos(12000+(layout++)*192,160,12000);
                var piece=new ResearchLabStructure.Piece(server.getStructureManager(),id(name),anchor,rotation,false);
                layouts.add(piece); var box=piece.getBoundingBox();
                var restored=new ResearchLabStructure.Piece(server.getStructureManager(),piece.createTag(StructurePieceSerializationContext.fromLevel(level)));
                check(box.equals(restored.getBoundingBox()),"clearing bounds survive piece reload: "+name+rotation);
                for(int cx=box.minX()>>4;cx<=box.maxX()>>4;cx++) for(int cz=box.minZ()>>4;cz<=box.maxZ()>>4;cz++) {
                    level.getChunk(cx,cz); level.setChunkForced(cx,cz,true);
                    entityManager.updateChunkStatus(new ChunkPos(cx,cz),net.minecraft.server.level.FullChunkStatus.ENTITY_TICKING);
                }
                for(int cx=box.minX()>>4;cx<=box.maxX()>>4;cx++) for(int cz=box.minZ()>>4;cz<=box.maxZ()>>4;cz++) {
                    var chunk=new ChunkPos(cx,cz);
                    piece.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(4),clip(chunk),chunk,anchor);
                }
                var entities=level.getEntitiesOfClass(ChangedEntity.class,AABB.of(box));
                int expectedCount=name.equals("forest_treehouse")?7:name.equals("hyena_hideout")?5:1;
                for(int retry=0;retry<100&&entities.size()!=expectedCount;retry++) {
                    entityManager.tick();Thread.sleep(1);
                    entities=level.getEntitiesOfClass(ChangedEntity.class,AABB.of(box));
                }
                entities.forEach(m->m.setNoAi(true));
                check(entities.size()==expectedCount,"resident count exactly once: "+name+rotation+" actual="+entities.size());
                var fixed=entities.stream().filter(m->m.getPersistentData().getCompound(StructureResidentGoal.DATA).getBoolean("Character")).toList();
                check(fixed.size()==1,"only one anchored character per structure");
                var actor=fixed.get(0); actors.add(actor);
                var data=actor.getPersistentData().getCompound(StructureResidentGoal.DATA);
                var original=server.getStructureManager().getOrCreate(id(name)).getBoundingBox(new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setRotation(rotation),anchor);
                check(data.getBoolean("Resolved"),"resident world anchor resolved");
                check(data.getDouble("MinX")==original.minX() && data.getDouble("MaxX")==original.maxX()+1
                        && data.getDouble("MinZ")==original.minZ() && data.getDouble("MaxZ")==original.maxZ()+1,"resident bounds rotate with template: "+name+rotation+data);
                check(level.noCollision(actor),"resident spawn is unobstructed: "+name+rotation);
                check(actor.isPersistenceRequired(),"fixed resident cannot despawn");
                if(name.equals("green_lab")) {
                    check(net.parkabird.changedsynergy.ai.CreatureCommunityData.bind(actor).isEmpty()
                            && net.parkabird.changedsynergy.ai.CreatureSettlementService.ensureCachePosition(actor).isEmpty(),"laboratory character does not establish an outpost");
                }
                {
                    var expectedOutfit=name.equals("forest_treehouse")?"changed_synergy:forest_canopy_outfit":name.equals("hyena_hideout")?"changed_synergy:hyena_trailcloth_outfit":"changed:lab_coat";
                    check(AccessorySlots.isWearing(actor, s->BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(expectedOutfit)),"mandatory native clothing: "+name+rotation);
                    if(name.equals("green_lab"))check(AccessorySlots.isWearing(actor,s->s.is(net.ltxprogrammer.changed.init.ChangedItems.RED_NECK_TIE.get())),"laboratory gas wolf wears red tie with lab coat");
                    if(name.equals("forest_treehouse"))check(actor instanceof net.parkabird.changedsynergy.entity.LatexSilkMoth,"fixed canopy character is Silk Moth");
                }
                if(name.equals("hyena_hideout")) {
                    var expected=StructureTemplate.transform(new Vec3(22.5,4.5,23.5),Mirror.NONE,rotation,BlockPos.ZERO).add(Vec3.atLowerCornerOf(anchor));
                    check(expected.distanceToSqr(actor.position())<.000001,"female starts beside orange bed");
                }
                if(name.equals("forest_treehouse")) {
                    int before=entities.size();
                    var store=net.parkabird.changedsynergy.ai.PlayerOutpostData.container(level,net.parkabird.changedsynergy.ai.NaturalCampService.sites(actor).storage().get(0));
                    store.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,3));
                    var intrusion=StructureTemplate.transform(new BlockPos(1,2,1),Mirror.NONE,rotation,BlockPos.ZERO).offset(anchor);
                    level.setBlock(intrusion,Blocks.OAK_LOG.defaultBlockState(),2);
                    for(int cx=box.minX()>>4;cx<=box.maxX()>>4;cx++) for(int cz=box.minZ()>>4;cz<=box.maxZ()>>4;cz++) piece.finishLandscape(level,clip(new ChunkPos(cx,cz)));
                    check(!level.getBlockState(intrusion).is(Blocks.OAK_LOG),"late forest intrusion repaired");
                    check(store.countItem(net.minecraft.world.item.Items.DIAMOND)==3,"forest finishing preserves existing container contents");
                    check(level.getEntitiesOfClass(ChangedEntity.class,AABB.of(box)).size()==before,"landscape finish never duplicates residents");
                    for(int x=box.minX();x<=box.maxX();x++) for(int z=box.minZ();z<=box.maxZ();z++) {
                        if(x>=original.minX()&&x<=original.maxX()&&z>=original.minZ()&&z<=original.maxZ())continue;
                        int surface=160;
                        while(surface>=158 && !level.getBlockState(new BlockPos(x,surface,z)).is(Blocks.GRASS_BLOCK))surface--;
                        check(surface>=158,"perimeter blends within two blocks of the flat foundation");
                        check(level.getBlockState(new BlockPos(x,surface+1,z)).isAir(),"perimeter remains clear");
                    }
                }
                actor.goalSelector.removeAllGoals(g->!(g instanceof StructureResidentGoal));
                actor.targetSelector.removeAllGoals(g->true); actor.setTarget(null);
                actor.setNoGravity(true);actor.setNoAi(false);
                actor.teleportTo(original.maxX()+6,actor.getY(),actor.getZ());
                var outside=new BlockPos(original.maxX()+6,actor.getBlockY(),actor.getBlockZ());
                level.getChunk(outside);level.setChunkForced(outside.getX()>>4,outside.getZ()>>4,true);
            }
            net.parkabird.changedsynergy.ai.NaturalCampChecks.prepare(level);
            started=true;
        }catch(Throwable failure){fail(failure);}
    }

    private static BoundingBox clip(ChunkPos chunk) {return new BoundingBox(chunk.getMinBlockX(),level.getMinBuildHeight(),chunk.getMinBlockZ(),chunk.getMaxBlockX(),level.getMaxBuildHeight()-1,chunk.getMaxBlockZ());}

    private static void natural(Structure structure,String set) {
        var generator=level.getChunkSource().getGenerator();var state=level.getChunkSource().randomState();
        var placement=(RandomSpreadStructurePlacement)server.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(id(set)).placement();
        for(int ring=0;ring<40;ring++) for(int x=-ring;x<=ring;x++) for(int z=-ring;z<=ring;z++) {
            if(Math.max(Math.abs(x),Math.abs(z))!=ring)continue;
            var candidate=placement.getPotentialStructureChunk(level.getSeed(),x*placement.spacing(),z*placement.spacing());
            if(!placement.isStructureChunk(level.getChunkSource().getGeneratorState(),candidate.x,candidate.z))continue;
            var biome=generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(candidate.getMiddleBlockX()),QuartPos.fromBlock(70),QuartPos.fromBlock(candidate.getMiddleBlockZ()),state.sampler());
            if(!structure.biomes().contains(biome))continue;
            var start=structure.generate(server.registryAccess(),generator,generator.getBiomeSource(),state,server.getStructureManager(),level.getSeed(),candidate,0,level,structure.biomes()::contains);
            if(!start.isValid())continue;
            var generated=level.getChunk(candidate.x,candidate.z,ChunkStatus.STRUCTURE_STARTS).getStartForStructure(structure);
            check(generated!=null && generated.isValid(),"registered natural start survives exclusions");
            var piece=(ResearchLabStructure.Piece)start.getPieces().get(0);
            int ground=piece.templatePosition().getY();
            int center=generator.getBaseHeight(candidate.getMiddleBlockX(),candidate.getMiddleBlockZ(),Heightmap.Types.OCEAN_FLOOR_WG,level,state)-1;
            check(Math.abs(center-ground)<=2,"natural start uses terrain height");
            System.out.println("FOREST_NATURAL_START "+set+" "+piece.templatePosition()+" "+piece.getRotation());return;
        }
        throw new AssertionError("no seeded forest start");
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(!started||event.phase!=TickEvent.Phase.END)return;
        try {
            net.parkabird.changedsynergy.ai.NaturalCampChecks.tick(level,++ticks);
            if(ticks<1100)return;
            for(var actor:actors) {
                var data=actor.getPersistentData().getCompound(StructureResidentGoal.DATA);
                check(new AABB(data.getDouble("MinX"),data.getDouble("MinY"),data.getDouble("MinZ"),data.getDouble("MaxX"),data.getDouble("MaxY"),data.getDouble("MaxZ")).contains(actor.position()),"escaped resident returns safely: "+actor.getType()+actor.position());
                actor.setNoAi(true);
                var saved=new CompoundTag();actor.save(saved);actor.discard();
                var clone=EntityType.loadEntityRecursive(saved,level,e->e);
                check(clone instanceof ChangedEntity,"resident reloads");level.addFreshEntity(clone);
                check(clone.getPersistentData().getCompound(StructureResidentGoal.DATA).equals(data),"bounds and clothing state survive entity reload");
            }
            for(var piece:layouts) {
                for(var hanging:level.getEntitiesOfClass(HangingEntity.class,AABB.of(piece.getBoundingBox())))check(hanging.survives(),"painting survives placement");
                var drops=level.getEntitiesOfClass(ItemEntity.class,AABB.of(piece.getBoundingBox()));
                check(drops.isEmpty(),"no generated loose item drops: "+piece.templatePosition()+drops.stream().map(e->e.getItem()+" at "+e.position()).toList());
            }
            System.out.println("FOREST_TREEHOUSE_REGRESSION_PASS: "+checks+" biome, ground, rotation, clearings, residents, clothing, boundary return, reload and drop checks");
            started=false;server.halt(false);
        }catch(Throwable failure){fail(failure);}
    }
    private static void fail(Throwable failure){started=false;System.err.println("FOREST_TREEHOUSE_REGRESSION_FAIL");failure.printStackTrace();server.halt(false);}
}
