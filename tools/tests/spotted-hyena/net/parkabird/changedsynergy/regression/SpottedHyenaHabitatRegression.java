package net.parkabird.changedsynergy.regression;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.init.ChangedSynergyEntities;
import net.parkabird.changedsynergy.world.ResearchLabStructure;

/** Place the authored buildings, then walk real male/female entities through their terrain. */
@Mod.EventBusSubscriber(modid="changed_synergy", value=Dist.DEDICATED_SERVER)
public final class SpottedHyenaHabitatRegression {
    private static int checks, routes;
    @SubscribeEvent public static void run(ServerStartedEvent event) {
        if (!Boolean.getBoolean("changed_synergy.hyenaHabitat")) return;
        var server=event.getServer();var level=server.overworld();
        try {
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false,server);
            level.setDayTime(6000);level.updateSkyBrightness();
            var serverTicks=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(net.minecraft.server.MinecraftServer.class,"f_129766_");
            var managerField=Arrays.stream(net.minecraft.server.level.ServerLevel.class.getDeclaredFields())
                    .filter(f->net.minecraft.world.level.entity.PersistentEntitySectionManager.class.isAssignableFrom(f.getType())).findFirst().orElseThrow();
            managerField.setAccessible(true);
            var entityManager=(net.minecraft.world.level.entity.PersistentEntitySectionManager<?>)managerField.get(level);
            int index=0;
            for(var name:List.of("hyena_hideout","hyena_camp_tropical","hyena_camp_open","hyena_camp_snowy"))
                for(var rotation:Rotation.values()) {
                    var anchor=new BlockPos(4000+index++*96,100,4000);var id=ResourceLocation.fromNamespaceAndPath("changed_synergy",name);
                    var piece=new ResearchLabStructure.Piece(server.getStructureManager(),id,anchor,rotation,false);
                    var box=piece.getBoundingBox();var chunks=new ArrayList<ChunkPos>();
                    for(int x=(box.minX()>>4)-1;x<=(box.maxX()>>4)+1;x++)for(int z=(box.minZ()>>4)-1;z<=(box.maxZ()>>4)+1;z++) {
                        level.getChunk(x,z);level.setChunkForced(x,z,true);chunks.add(new ChunkPos(x,z));
                        entityManager.updateChunkStatus(new ChunkPos(x,z),net.minecraft.server.level.FullChunkStatus.ENTITY_TICKING);
                    }
                    // Native supports reach the ordinary terrain, so exterior approach tiles have the same surface.
                    int surface=anchor.getY()+(name.equals("hyena_hideout")?3:0);
                    for(int x=box.minX()-3;x<=box.maxX()+3;x++)for(int z=box.minZ()-3;z<=box.maxZ()+3;z++)
                        level.setBlock(new BlockPos(x,surface,z),Blocks.DIRT.defaultBlockState(),2);
                    for(int x=box.minX()>>4;x<=box.maxX()>>4;x++)for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++) {
                        var chunk=new ChunkPos(x,z);piece.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(4),
                                new BoundingBox(chunk.getMinBlockX(),level.getMinBuildHeight(),chunk.getMinBlockZ(),chunk.getMaxBlockX(),level.getMaxBuildHeight()-1,chunk.getMaxBlockZ()),chunk,anchor);
                    }
                    var residents=level.getEntitiesOfClass(net.parkabird.changedsynergy.entity.LatexSpottedHyena.class,
                            net.minecraft.world.phys.AABB.of(box).inflate(1), e->e.getTags().contains("changed_synergy:hyena_structure_resident"));
                    // FULL chunks can still have pending asynchronous entity-section visibility changes.
                    for(int retry=0;retry<100&&residents.size()!=(name.equals("hyena_hideout")?5:2);retry++) {
                        entityManager.tick();Thread.sleep(1);
                        residents=level.getEntitiesOfClass(net.parkabird.changedsynergy.entity.LatexSpottedHyena.class,
                                net.minecraft.world.phys.AABB.of(box).inflate(1), e->e.getTags().contains("changed_synergy:hyena_structure_resident"));
                    }
                    System.out.println("HYENA_INITIAL_RESIDENTS "+name+" "+rotation+" "+residents.stream().map(e->e.getType()+" "+e.position()).toList());
                    for(var entity:level.getAllEntities())if(entity instanceof net.parkabird.changedsynergy.entity.LatexSpottedHyena)
                        System.out.println("HYENA_VISIBLE_ENTITY "+entity.getType()+" "+entity.position()+" "+entity.getTags());
                    check(residents.size()==(name.equals("hyena_hideout")?5:2),"fixed residents spawn exactly once across chunk clips: "+name+" count="+residents.size());
                    check(residents.stream().filter(e->e.getGender()==net.ltxprogrammer.changed.entity.Gender.FEMALE).count()==(name.equals("hyena_hideout")?1:0),"structure female count");
                    for(var resident:residents) {
                        check(resident.isPersistenceRequired(),"structure residents do not naturally despawn");
                        check(resident.getHealth()==resident.getMaxHealth(),"structure residents spawn at full gendered health");
                        check(level.noCollision(resident),"resident starts clear of walls and furniture");
                        var saved=resident.saveWithoutId(new CompoundTag());var restored=(net.parkabird.changedsynergy.entity.LatexSpottedHyena)resident.getType().create(level);restored.load(saved);
                        check(restored.isPersistenceRequired()&&restored.getGender()==resident.getGender(),"resident identity survives save/load");
                        resident.setNoAi(true);resident.discard();
                    }
                    CompoundTag raw;try(var stream=server.getResourceManager().open(ResourceLocation.fromNamespaceAndPath("changed_synergy","structures/"+name+".nbt"))) {raw=NbtIo.readCompressed(stream);}
                    var palette=raw.getList("palette",Tag.TAG_COMPOUND);
                    BlockPos door=null;
                    for(var entry:raw.getList("blocks",Tag.TAG_COMPOUND)) {
                        var b=(CompoundTag)entry;var state=palette.getCompound(b.getInt("state"));
                        if(!state.getString("Name").endsWith("_door")||!state.getCompound("Properties").getString("half").equals("lower"))continue;
                        var p=b.getList("pos",Tag.TAG_INT);door=StructureTemplate.transform(new BlockPos(p.getInt(0),p.getInt(1),p.getInt(2)),Mirror.NONE,rotation,BlockPos.ZERO).offset(anchor);
                    }
                    for(var type:List.of(ChangedSynergyEntities.LATEX_SPOTTED_HYENA_MALE.get(),ChangedSynergyEntities.LATEX_SPOTTED_HYENA_FEMALE.get())) {
                        var npc=type.create(level);check(npc.getBbHeight()<2,"both sexes fit ordinary two-block entrances");
                        var floor=new ArrayList<BlockPos>();
                        for(int x=box.minX();x<=box.maxX();x++)for(int z=box.minZ();z<=box.maxZ();z++) {
                            var p=new BlockPos(x,surface+1,z);
                            if(level.getBlockState(p.below()).isFaceSturdy(level,p.below(),Direction.UP)
                                    &&level.getFluidState(p).isEmpty()&&level.noCollision(npc,npc.getDimensions(Pose.STANDING).makeBoundingBox(x+.5,p.getY(),z+.5)))floor.add(p);
                        }
                        check(floor.size()>=6,"usable standing ground inside authored structure: "+name);
                        var random=RandomSource.create(571);BlockPos spawnable=null;
                        for(var p:floor) {
                            for(int attempt=0;attempt<24;attempt++)if(SpawnPlacements.checkSpawnRules(type,level,MobSpawnType.NATURAL,p,random)){spawnable=p;break;}
                            if(spawnable!=null)break;
                        }
                        check(spawnable!=null,"authored floor and light permit natural hyena spawning: "+name);
                        BlockPos from,to;
                        if(door!=null) {
                            var facing=level.getBlockState(door).getValue(DoorBlock.FACING);
                            from=door.relative(facing,2);to=door.relative(facing.getOpposite(),2);
                            check(level.noCollision(npc,npc.getDimensions(Pose.STANDING).makeBoundingBox(door.getX()+.5,door.getY(),door.getZ()+.5)),"authored door opening clears full standing body");
                        } else {
                            // Pick an ordinary open-floor route; enclosed sheep pens are not navigation goals.
                            floor.sort(Comparator.comparingDouble(p->p.distSqr(box.getCenter())));from=floor.get(0);
                            final BlockPos start=from;floor.sort(Comparator.comparingDouble((BlockPos p)->-p.distSqr(start)));to=null;
                            npc.moveTo(from.getX()+.5,from.getY(),from.getZ()+.5);npc.setOnGround(true);
                            for(var candidate:floor) {
                                if(candidate.distSqr(from)<9)continue;
                                var path=npc.getNavigation().createPath(candidate,0);
                                if(path!=null&&path.canReach()){to=candidate;break;}
                            }
                            check(to!=null,"usable internal path: "+name);
                        }
                        npc.moveTo(from.getX()+.5,from.getY(),from.getZ()+.5);npc.setOnGround(true);level.addFreshEntity(npc);
                        for(var goal:npc.goalSelector.getAvailableGoals().stream().toList())npc.goalSelector.removeGoal(goal.getGoal());
                        for(var goal:npc.targetSelector.getAvailableGoals().stream().toList())npc.targetSelector.removeGoal(goal.getGoal());
                        boolean arrived=false;double furthest=0;
                        for(int tick=0;tick<280;tick++) {
                            serverTicks.setInt(server,server.getTickCount()+1);
                            server.getWorldData().overworldData().setGameTime(level.getGameTime()+1);
                            if(tick==0||(tick%10==0&&npc.onGround()&&npc.getNavigation().isDone())) {
                                // Coordinate moveTo accepts a block of error and can stop inside a short doorway route.
                                var path=npc.getNavigation().createPath(to,0);
                                if(tick==0)check(path!=null&&path.canReach(),"exact path reaches the opposite side: "+name+" "+rotation+" target="+to);
                                if(path!=null&&path.canReach())npc.getNavigation().moveTo(path,.35);
                            }
                            var before=npc.position();level.tickNonPassenger(npc);
                            check(npc.position().distanceToSqr(before)<2.25,"ordinary movement, no teleport");
                            furthest=Math.max(furthest,npc.position().distanceToSqr(from.getCenter()));
                            // Native moveTo may finish one block short; a doorway must still be fully crossed.
                            boolean crossed=door==null||npc.position().subtract(door.getCenter()).dot(to.getCenter().subtract(from.getCenter()).normalize())>.795;
                            if(crossed&&npc.distanceToSqr(to.getX()+.5,to.getY(),to.getZ()+.5)<2.25){arrived=true;break;}
                        }
                        System.out.println("HYENA_HABITAT_ROUTE "+name+" "+rotation+" "+type+" floor="+floor.size()+" from="+from+" to="+to+" arrived="+arrived+" pos="+npc.position()+" distance="+furthest+" collide="+npc.horizontalCollision+" navigationDone="+npc.getNavigation().isDone());
                        check(arrived,"real NPC navigation through authored habitat: "+name+" "+rotation);routes++;npc.discard();
                    }
                    for(var entity:level.getEntities(null,net.minecraft.world.phys.AABB.of(box).inflate(3)))entity.discard();
                    for(var chunk:chunks)level.setChunkForced(chunk.x,chunk.z,false);
                }
            System.out.println("SPOTTED_HYENA_HABITAT_PASS: "+checks+" checks; "+routes+" male/female routes in four structures and four rotations");
        }catch(Throwable failure){System.err.println("SPOTTED_HYENA_HABITAT_FAIL");failure.printStackTrace();}
        finally{server.halt(false);}
    }
    private static void check(boolean value,String text){if(!value)throw new AssertionError(text);checks++;}
}
