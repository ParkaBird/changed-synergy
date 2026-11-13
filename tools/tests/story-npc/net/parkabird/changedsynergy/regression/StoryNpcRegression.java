package net.parkabird.changedsynergy.regression;

import java.util.*;
import com.mojang.authlib.GameProfile;
import net.ltxprogrammer.changed.data.AccessorySlots;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedGameRules;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.npc.*;
import net.parkabird.changedsynergy.origin.*;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;

@Mod.EventBusSubscriber(modid="changed_synergy",value=net.minecraftforge.api.distmarker.Dist.DEDICATED_SERVER)
public final class StoryNpcRegression {
    private static int checks;private static MinecraftServer server;private static ServerLevel level;
    private static final List<ChangedEntity> chiefs=new ArrayList<>();
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    @SubscribeEvent public static void run(ServerStartedEvent event) {
        server=event.getServer();level=server.overworld();
        try {
            net.parkabird.changedsynergy.ChangedSynergyConfig.SPEC.setConfig(com.electronwill.nightconfig.core.CommentedConfig.inMemory());
            level.getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(true,server);
            level.getGameRules().getRule(ChangedGameRules.RULE_KEEP_BRAIN).set(true,server);
            check(NpcDefinitions.all().size()==5&&NpcDefinitions.all().stream().allMatch(NpcDefinitions.Profile::dialogue),"all five dialogue profiles loaded");
            if(Boolean.getBoolean("changed_synergy.desertCampGeneration"))DesertCampGenerationChecks.run(server);
            else if(Boolean.getBoolean("changed_synergy.desertCommunity"))DesertCommunityChecks.run(server);
            else if(Boolean.getBoolean("changed_synergy.desertContent"))DesertContentChecks.run(server);
            else if(Boolean.getBoolean("changed_synergy.sessionSyncFixes")){placements();SessionSyncFixChecks.run(server,chiefs);}
            else if(Boolean.getBoolean("changed_synergy.organicRelations"))OrganicRelationChecks.run(server);
            else if(Boolean.getBoolean("changed_synergy.campUnload"))CampUnloadChecks.run(server);
            else if(Boolean.getBoolean("changed_synergy.observatoryGeneration"))generation();
            else if(Boolean.getBoolean("changed_synergy.fixedNpcMovement")){placements();newContent();residentNavigation();}
            else if(Boolean.getBoolean("changed_synergy.storyNpcReload"))reload();
            else{CampUnloadChecks.run(server);textStyleValidation();placements();CommunityStoreChecks.run(server,chiefs);CampRepairBondChecks.run(server,chiefs);newContent();membership();campRewards();CampLifeChecks.run(server,chiefs);dialogue();permanentConfirmation();topicProgress();forgiveness();rites();safety();returning();builtInResidentRespawn();}
            System.out.println("STORY_NPC_SERVER_PASS: "+checks+" presets, rotations, enrolment, permissions, dialogue authority, rituals, TT policies, persistence and return checks");
        }catch(Throwable failure){System.err.println("STORY_NPC_SERVER_FAIL");failure.printStackTrace();}
        finally{server.halt(false);}
    }
    private static void textStyleValidation()throws Exception {
        var constructor=NpcDefinitions.class.getDeclaredConstructor();constructor.setAccessible(true);var loader=constructor.newInstance();
        var apply=NpcDefinitions.class.getDeclaredMethod("apply",Map.class,net.minecraft.server.packs.resources.ResourceManager.class,net.minecraft.util.profiling.ProfilerFiller.class);apply.setAccessible(true);
        com.google.gson.JsonObject original;
        try(var stream=server.getResourceManager().open(ResourceLocation.parse("changed_synergy:story_npcs/canopy_chief.json"));var reader=new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)) {original=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();}
        var before=NpcDefinitions.get("changed_synergy:canopy_chief");
        for(String invalid:List.of("effect","emotion","speed","intensity","nonfinite")) {
            var json=original.deepCopy();var part=json.getAsJsonObject("nodes").getAsJsonObject("root").getAsJsonArray("lines").get(0).getAsJsonObject().getAsJsonArray("parts").get(0).getAsJsonObject();
            switch(invalid){case "effect","emotion"->part.addProperty(invalid,"unknown");case "speed"->part.addProperty("speed",0);case "intensity"->part.addProperty("intensity",3);default->part.addProperty("speed",Float.NaN);}
            boolean rejected=false;
            try{apply.invoke(loader,Map.of(ResourceLocation.parse("changed_synergy:canopy_chief"),json),null,null);}catch(java.lang.reflect.InvocationTargetException e){rejected=e.getCause() instanceof com.google.gson.JsonParseException;}
            check(rejected&&NpcDefinitions.get("changed_synergy:canopy_chief")==before,"invalid text styling is rejected without replacing live profiles: "+invalid);
        }
    }
    private static void activate(BlockPos pos)throws Exception {
        level.getChunk(pos);level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);
        var field=Arrays.stream(ServerLevel.class.getDeclaredFields()).filter(f->net.minecraft.world.level.entity.PersistentEntitySectionManager.class.isAssignableFrom(f.getType())).findFirst().orElseThrow();field.setAccessible(true);
        var manager=(net.minecraft.world.level.entity.PersistentEntitySectionManager<?>)field.get(level);
        manager.updateChunkStatus(new net.minecraft.world.level.ChunkPos(pos),net.minecraft.server.level.FullChunkStatus.ENTITY_TICKING);manager.tick();
    }
    private static void flushEntities()throws Exception {
        var field=Arrays.stream(ServerLevel.class.getDeclaredFields()).filter(f->net.minecraft.world.level.entity.PersistentEntitySectionManager.class.isAssignableFrom(f.getType())).findFirst().orElseThrow();field.setAccessible(true);
        var manager=(net.minecraft.world.level.entity.PersistentEntitySectionManager<?>)field.get(level);
        var storage=Arrays.stream(manager.getClass().getDeclaredFields()).filter(f->net.minecraft.world.level.entity.EntityPersistentStorage.class.isAssignableFrom(f.getType())).findFirst().orElseThrow();storage.setAccessible(true);
        ((net.minecraft.world.level.entity.EntityPersistentStorage<?>)storage.get(manager)).flush(false);manager.tick();
    }
    private static void placements()throws Exception {
        int index=0;var keys=new HashSet<String>();
        for(var profile:NpcDefinitions.all().stream().filter(NpcDefinitions.Profile::dialogue).toList())for(var rotation:Rotation.values()) {
            var template=server.getStructureManager().getOrCreate(ResourceLocation.parse("changed_synergy:"+profile.template()));
            var origin=new BlockPos(1000+index++*128,100,1000);var settings=new StructurePlaceSettings().setRotation(rotation);var box=template.getBoundingBox(settings,origin);
            for(int x=box.minX()>>4;x<=box.maxX()>>4;x++)for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++)activate(new BlockPos(x*16,100,z*16));
            check(template.placeInWorld(level,origin,origin,settings,net.minecraft.util.RandomSource.create(17),2),"native placement");
            var mobs=level.getEntitiesOfClass(ChangedEntity.class,AABB.of(box));var fixed=mobs.stream().filter(StoryNpcService::isNpc).toList();
            check(fixed.size()==1,"one fixed character in "+profile.template()+rotation+"; actual="+fixed.size());
            var npc=fixed.get(0);chiefs.add(npc);check(keys.add(StoryNpcService.key(npc)),"each structure has a distinct identity");
            check(npc.getCustomName().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents label&&label.getKey().equals("npc."+profile.id().replace(':','.')+".title"),"fixed characters use position labels without personal names");
            String display=switch(profile.id()){case "changed_synergy:canopy_chief"->"Weaver";case "changed_synergy:hyena_chief"->"Matriarch";case "changed_synergy:observatory_chief"->"Archivist";case "changed_synergy:desert_chief"->"Pathfinder";default->"Researcher";};
            check(npc.getName().getString().equals(display),"entity names use the same shortened positions as the dialogue header");
            var ordinary=(ChangedEntity)npc.getType().create(level);
            check(Math.abs(npc.getAttributeBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
                    -ordinary.getAttributeBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED))<.00001,"fixed NPC keeps its species' native movement attribute");ordinary.discard();
            check(CreaturePersonality.dominantTrait(npc)==CreaturePersonality.Trait.valueOf(profile.trait()),"correct dominant trait");
            check(new HashSet<>(CreaturePersonality.traits(npc)).equals(Set.of(CreaturePersonality.Trait.valueOf(profile.trait()),CreaturePersonality.Trait.POLITE)),"exactly the two authored traits");
            check(CreatureLifeMemory.role(npc)==(profile.chief()?CreatureLifeMemory.GroupRole.CHIEF:CreatureLifeMemory.GroupRole.WANDERER),"manual chief role and non-chief researcher");
            check(mobs.stream().filter(m->!StoryNpcService.isNpc(m)).noneMatch(m->CreatureLifeMemory.role(m)==CreatureLifeMemory.GroupRole.CHIEF),"ordinary residents never roll Chief");
            check(npc.hasCustomName(),"stable personal name");
            for(String item:profile.equipment())check(AccessorySlots.isWearing(npc,s->net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(item)&&StoryNpcService.bound(s)),"default outfit is present and bound: "+item);
            if(profile.id().endsWith("canopy_chief")){var bpi=npc.saveWithoutId(new CompoundTag()).getCompound("LocalVariantInfo");check(bpi.getInt("eyeStyle")==2&&bpi.getInt("irisLeft")==0x90B8C2,"mist-blue Tall eyes preserved");}
            if(profile.id().endsWith("hyena_chief")){var bpi=npc.saveWithoutId(new CompoundTag()).getCompound("LocalVariantInfo");check(bpi.getInt("eyeStyle")==1&&bpi.getInt("irisLeft")==0xB8744C&&bpi.getInt("irisRight")==0xB8744C,"female Hyena Chief has Slim copper-orange eyes");}
            if(profile.id().endsWith("observatory_chief")) {
                var bedroom=StructureTemplate.transform(new Vec3(5.5,2,14.5),Mirror.NONE,rotation,BlockPos.ZERO).add(Vec3.atLowerCornerOf(origin));
                check(npc.position().distanceToSqr(bedroom)<.001,"Archivist starts in the lectern bedroom in every rotation");
                var bpi=npc.saveWithoutId(new CompoundTag()).getCompound("LocalVariantInfo");
                check(bpi.getInt("eyeStyle")==0&&bpi.getInt("irisLeft")==0xF59A23&&bpi.getInt("irisRight")==0xF59A23,"orange Original owl eyes preserved");
                check(npc.getCustomName().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents name&&name.getKey().equals("npc.changed_synergy.observatory_chief.title"),"position label replaces the former personal name");
                check(mobs.size()==5,"one owl and four mountain residents");
                for(var mob:mobs){check(NaturalCampService.member(mob)&&FactionReputation.groupId(mob).equals("light:mountain"),"all observatory residents belong to mountain camp");check(level.noCollision(mob),"resident spawns without block collision");}
                check(NaturalCampService.sites(npc).beds().stream().anyMatch(p->level.getBlockState(p).getBlock() instanceof net.parkabird.changedsynergy.block.PerchBlock),"built-in observatory perches are discoverable camp beds");
            }
            if(profile.id().endsWith("canopy_chief")) {
                var bees=mobs.stream().filter(m->net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).toString().equals("changed:latex_bee")).toList();check(bees.size()==3,"three bees inhabit the treehouse hive");
                for(var bee:bees){check(NaturalCampService.member(bee)&&FactionReputation.groupId(bee).equals("light:forest"),"hive bees join treehouse routines");check(level.noCollision(bee),"hive bees have free spawn space");
                    check(bee.getPersistentData().getBoolean("ChangedSynergyStructureEyesInitialized")&&!bee.getPersistentData().getBoolean("ChangedSynergyRandomStructureEyes"),"bee eye variation initialized once");
                    var info=bee.getBasicPlayerInfo();var saved=bee.saveWithoutId(new CompoundTag());var copy=(ChangedEntity)bee.getType().create(level);copy.load(saved);
                    check(copy.getBasicPlayerInfo().getEyeStyle()==info.getEyeStyle()&&copy.getBasicPlayerInfo().getLeftIrisColor().toInt()==info.getLeftIrisColor().toInt(),"bee eye variation survives NBT reload");}
            }
            check(StoryNpcData.get(server).records.get(StoryNpcService.key(npc)).hasUUID("Actor"),"world-owned identity registered");
            var store=StoryNpcData.get(server);int count=store.records.size();
            var piece=new net.parkabird.changedsynergy.world.ResearchLabStructure.Piece(server.getStructureManager(),ResourceLocation.parse("changed_synergy:"+profile.template()),origin,rotation,false);
            StoryNpcStructures.register(level,piece);check(store.records.size()==count,"loaded structure start does not duplicate an existing identity");
            store.records.remove(StoryNpcService.key(npc));StoryNpcStructures.register(level,piece);
            var migrated=store.records.get(StoryNpcService.key(npc));check(migrated!=null&&!migrated.hasUUID("Actor"),"missing legacy resident registers from the authored template in every rotation");
            StoryNpcService.join(new net.minecraftforge.event.entity.EntityJoinLevelEvent(npc,level));
            check(migrated.hasUUID("Actor")&&migrated.getUUID("Actor").equals(npc.getUUID()),"legacy entity claims its instance before the return delay");
        }
        var stored=StoryNpcData.get(server);check(StoryNpcData.load(stored.save(new CompoundTag())).records.size()==stored.records.size(),"NPC world records survive serialization");
    }
    private static void residentNavigation() {
        for(String name:List.of("canopy_chief","hyena_chief","observatory_chief","lab_researcher")) {
            var npc=chief(name);var saved=npc.position();var resident=npc.getPersistentData().getCompound(StructureResidentGoal.DATA);
            var goal=npc.goalSelector.getAvailableGoals().stream().map(g->g.getGoal()).filter(g->g instanceof StructureResidentGoal).findFirst().orElseThrow();
            double x=resident.getDouble("MaxX")+30,z=resident.getDouble("HomeZ"),y=resident.getDouble("HomeY");
            try {
                npc.setNoAi(false);npc.setPos(x,y,z);check(goal.canUse(),"outside fixed resident returns home: "+name);goal.start();
                for(int tick=0;tick<160;tick++){
                    x-=.04;npc.setPos(x,y,z);goal.tick();
                    check(Math.abs(npc.getX()-x)<.001,"a resident still making walking progress is never teleported: "+name);
                }
                goal.stop();npc.setPos(x,y,z);goal.start();
                for(int tick=0;tick<100;tick++)goal.tick();
                check(npc.getX()<resident.getDouble("MaxX"),"a genuinely stuck resident retains safe recovery: "+name);
                check(level.noCollision(npc),"recovery position is clear: "+name);
            } finally {goal.stop();npc.setPos(saved);}
        }
    }
    private static ChangedEntity chief(String name){return chiefs.stream().filter(m->StoryNpcService.profile(m).id().endsWith(name)).findFirst().orElseThrow();}
    private static void newContent()throws Exception {
        for(String name:List.of("canopy_chief","hyena_chief","lab_researcher")) {
            var mob=chief(name);String key=StoryNpcService.key(mob);var identity=mob.getUUID();var personal=mob.getCustomName();
            CreaturePersonality.setNpcPreset(mob,name.equals("canopy_chief")?CreaturePersonality.Trait.PROTECTIVE:CreaturePersonality.Trait.COMPETITIVE);
            mob.getPersistentData().getCompound(StoryNpcService.DATA).putInt("PresetVersion",1);
            StoryNpcService.join(new net.minecraftforge.event.entity.EntityJoinLevelEvent(mob,level));
            check(CreaturePersonality.dominantTrait(mob)==CreaturePersonality.Trait.valueOf(StoryNpcService.profile(mob).trait()),"old-world chief preset migrates");
            check(mob.getUUID().equals(identity)&&StoryNpcService.key(mob).equals(key)&&Objects.equals(mob.getCustomName(),personal),"preset migration preserves personal identity");
            if(name.equals("hyena_chief"))check(mob.getBasicPlayerInfo().getEyeStyle()==net.ltxprogrammer.changed.entity.EyeStyle.V2
                    &&mob.getBasicPlayerInfo().getLeftIrisColor().toInt()==0xB8744C,"existing Hyena Chief receives the authored eye update");
            if(name.equals("canopy_chief"))check(mob.getBasicPlayerInfo().getEyeStyle()==net.ltxprogrammer.changed.entity.EyeStyle.TALL&&mob.getBasicPlayerInfo().getLeftIrisColor().toInt()==0x90B8C2,"calm Moth Chief receives mist-blue tall eyes");
            if(name.equals("lab_researcher"))check(mob.getBasicPlayerInfo().getEyeStyle()==net.ltxprogrammer.changed.entity.EyeStyle.V1
                    &&mob.getBasicPlayerInfo().getLeftIrisColor().toInt()==0x65C98C,"existing researcher receives original mint-green eyes");
        }
        var owl=chief("observatory_chief");var human=player(owl,"OwlHuman");
        try {
            level.setDayTime(18000);level.updateSkyBrightness();NpcConversations.open(owl,human);check(NpcConversations.state(human).getList("Parts",10).getCompound(0).getString("Text").equals(StoryNpcService.profile(owl).nodes().get("root").lines().stream().filter(l->l.condition().equals("night_human")).findFirst().orElseThrow().parts().get(0).text()),"human-specific night owl greeting");NpcConversations.close(human);
            FactionReputation.set(owl,human,20);check(CampMembershipService.join(owl,human,"reversible"),"owl offers reversible mountain enrolment");
            check(CampMembershipService.membership(human).getString("HomeKey").equals(StoryNpcService.homeKey(owl))&&CampMembershipService.score(owl,human)==5,"owl admission belongs to this camp and grants local trust only");
            check(ProcessTransfur.getPlayerTransfurVariant(human).getParent()==owl.getSelfVariant()&&NpcRites.reversible(human)&&!TrueTransfurCompat.blocksReversal(human),"copied owl form remains reversible");
            NpcConversations.open(owl,human);check(NpcConversations.state(human).getList("Parts",10).getCompound(0).getString("Text").equals(StoryNpcService.profile(owl).nodes().get("root").lines().stream().filter(l->l.condition().equals("night_transformed")).findFirst().orElseThrow().parts().get(0).text()),"transformed-specific night owl greeting");NpcConversations.close(human);
            NpcConversations.open(owl,human);var passage=NpcConversations.state(human);
            check(passage.getBoolean("Last")&&passage.getInt("Lines")==1,"understood thoughts share the spoken passage instead of requiring a server advance");
            check(passage.getList("Parts",10).stream().map(t->(CompoundTag)t).anyMatch(t->t.getString("Channel").equals("mental")&&t.getBoolean("Break")),"server preserves the thought channel and paragraph within a mixed passage");NpcConversations.close(human);
            ProcessTransfur.removePlayerTransfurVariant(human);check(!ProcessTransfur.isPlayerTransfurred(human),"owl ritual can be undone");
        }finally{retire(human);}
    }
    private static boolean hasChoice(ServerPlayer p,String id){return NpcConversations.state(p).getList("Choices",10).stream().map(t->(CompoundTag)t).anyMatch(c->c.getString("Id").equals(id));}
    private static final class FeedbackConnection extends ServerGamePacketListenerImpl {
        int chat,sounds;
        FeedbackConnection(ServerPlayer player){super(server,new Connection(PacketFlow.SERVERBOUND),player);}
        @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {
            if(packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket)chat++;
            if(packet instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket)sounds++;
            super.send(packet);
        }
    }
    private static void topicProgress()throws Exception {
        var tested=new HashSet<String>();
        for(var npc:chiefs) {
            var profile=StoryNpcService.profile(npc);var locked=profile.nodes().get("root").choices().stream().filter(c->!c.requires().isEmpty()).findFirst().orElseThrow();
            if(!tested.add(profile.id()))continue;
            var p=player(npc,"TopicTest");var stranger=player(npc,"TopicOther");
            try {
                NpcConversations.open(npc,p);finishLine(p);check(!hasChoice(p,locked.id()),"background question starts hidden");
                var s=NpcConversations.state(p);check(!s.isEmpty(),"topic conversation opened for "+profile.id());NpcConversations.action(p,s.getUUID("Session"),s.getInt("Revision"),locked.id());check(NpcConversations.state(p).getString("Node").equals("root"),"forged locked question is rejected server-side");
                var first=profile.nodes().get("root").choices().stream().filter(c->c.next().equals(locked.requires().get(0))).findFirst().orElseThrow();
                var second=profile.nodes().get("root").choices().stream().filter(c->c.next().equals(locked.requires().get(1))).findFirst().orElseThrow();
                choose(p,first.id());choose(p,"goodbye");check(NpcConversations.state(p).getString("Node").equals("goodbye"),"goodbye receives the authored farewell");choose(p,"close");check(NpcConversations.state(p).isEmpty(),"finishing a topic with goodbye exits normally");
                NpcConversations.open(npc,p);finishLine(p);check(!hasChoice(p,locked.id()),"one topic is insufficient");choose(p,second.id());choose(p,"back");finishLine(p);check(hasChoice(p,locked.id()),"both complete topics unlock the background question");
                NpcConversations.close(p);NpcConversations.open(npc,stranger);finishLine(stranger);check(!hasChoice(stranger,locked.id()),"topic unlock belongs to one player");NpcConversations.close(stranger);
                NpcConversations.open(npc,p);finishLine(p);check(hasChoice(p,locked.id()),"topic unlock survives conversation reopen");
                var stored=StoryNpcData.load(StoryNpcData.get(server).save(new CompoundTag())).records.get(StoryNpcService.key(npc));check(locked.requires().stream().allMatch(k->stored.getCompound("Progress").getCompound(p.getUUID().toString()).getBoolean(k)),"prerequisites persist in world save");
            }finally{retire(p);retire(stranger);}
        }
    }
    private static void forgiveness()throws Exception {
        var variant=net.ltxprogrammer.changed.init.ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get();
        boolean takeover=net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.takeoverEnabled.get();
        net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.takeoverEnabled.set(false);
        try {for(boolean absorbed:List.of(false,true)) {
            var source=variant.getEntityType().create(level);source.setNoAi(true);source.setPos(7000,100,7000);activate(source.blockPosition());
            for(var pos:BlockPos.betweenClosed(new BlockPos(6997,99,6997),new BlockPos(7003,99,7003)))level.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
            level.addFreshEntity(source);var p=player(source,"ForgivenessTest");p.setOnGround(true);
            try {
                CreaturePersonality.setFamiliarity(source,p,12);
                source.getPersistentData().putLong("ChangedSynergyNextPriorityDialogue",level.getGameTime()+1200);
                var decision=net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision.strong(absorbed?net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision.Method.ABSORPTION:net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision.Method.REPLICATION,
                        variant,net.ltxprogrammer.changed.entity.TransfurContext.npcLatexAttack(source),100F);
                var event=new net.ltxprogrammer.changed.process.TransfurEvents.LatexAssimilationDecisionEvent(p,decision);InvoluntaryTransfurNegotiation.observeDecision(event);
                ProcessTransfur.setPlayerTransfurVariant(p,variant,null,1,false);event.getDecision().postTransfurListener().accept(net.ltxprogrammer.changed.ability.IAbstractChangedEntity.forPlayer(p));
                int before=CreaturePersonality.familiarity(source,p);if(absorbed)source.discard();
                var feedback=new FeedbackConnection(p);p.connection=feedback;
                check(InvoluntaryTransfurNegotiation.forgive(p,absorbed?null:source),"forgive commits for live or absorbed individual");
                check(feedback.chat==0,"forgiveness uses the creature dialogue overlay, including trusted relationships, without a chat notification");
                var claim=p.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG).getCompound("ChangedSynergyTransfurNegotiation");
                var memory=absorbed?claim.getCompound("SourceSnapshot").getCompound("ForgeData"):source.getPersistentData();
                check(memory.getString("ChangedSynergyLastDialogue").contains(".common.forgiven."),"creature delivers a forgiveness line despite prior dialogue cooldown");
                check(InvoluntaryTransfurNegotiation.view(p).isEmpty()&&ProcessTransfur.getPlayerTransfurVariant(p).getParent()==variant,"forgiveness ends negotiation and keeps form");
                check(!InvoluntaryTransfurNegotiation.forgive(p,absorbed?null:source),"repeated forgiveness cannot grant a second response or reward");
                if(!absorbed)check(CreaturePersonality.familiarity(source,p)==before+8,"live source receives affection once");
                else {
                    var saved=(ChangedEntity)source.getType().create(level);saved.load(claim.getCompound("SourceSnapshot"));
                    check(CreaturePersonality.familiarity(saved,p)==before+8,"absorbed individual's affection survives in snapshot");saved.discard();
                }
            }finally{InvoluntaryTransfurNegotiation.abandonForSecondaryTransfur(p);ProcessTransfur.removePlayerTransfurVariant(p);retire(p);source.discard();}
        }}finally{net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.takeoverEnabled.set(takeover);}
    }
    private static void generation() {
        var registry=server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        var structure=registry.get(ResourceLocation.parse("changed_synergy:observatory"));check(structure!=null,"observatory is registered for world generation");
        var allowed=Set.of("snowy_slopes","grove");
        for(var biome:server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME).holders().toList())
            check(structure.biomes().contains(biome)==(biome.key().location().getNamespace().equals("minecraft")&&allowed.contains(biome.key().location().getPath())),"exact mountain-biome eligibility");
        var generator=level.getChunkSource().getGenerator();var state=level.getChunkSource().randomState();
        var set=server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET).get(ResourceLocation.parse("changed_synergy:observatories"));
        var placement=(net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement)set.placement();
        check(placement.spacing()==80&&placement.separation()==20,"rare landmark spacing and separation");
        for(int ring=0;ring<35;ring++)for(int x=-ring;x<=ring;x++)for(int z=-ring;z<=ring;z++) {
            if(Math.max(Math.abs(x),Math.abs(z))!=ring)continue;
            var candidate=placement.getPotentialStructureChunk(level.getSeed(),x*placement.spacing(),z*placement.spacing());
            var biome=generator.getBiomeSource().getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(candidate.getMiddleBlockX()),net.minecraft.core.QuartPos.fromBlock(90),net.minecraft.core.QuartPos.fromBlock(candidate.getMiddleBlockZ()),state.sampler());
            if(!structure.biomes().contains(biome)||!placement.isStructureChunk(level.getChunkSource().getGeneratorState(),candidate.x,candidate.z))continue;
            var start=structure.generate(server.registryAccess(),generator,generator.getBiomeSource(),state,server.getStructureManager(),level.getSeed(),candidate,0,level,structure.biomes()::contains);
            if(!start.isValid())continue;
            var piece=(net.parkabird.changedsynergy.world.ResearchLabStructure.Piece)start.getPieces().get(0);
            var model=server.getStructureManager().getOrCreate(ResourceLocation.parse("changed_synergy:observatory"));
            var heights=new ArrayList<Integer>();
            for(int localX:new int[]{0,model.getSize().getX()/2,model.getSize().getX()-1})for(int localZ:new int[]{0,model.getSize().getZ()/2,model.getSize().getZ()-1}) {
                var p=StructureTemplate.transform(new BlockPos(localX,0,localZ),Mirror.NONE,piece.getRotation(),BlockPos.ZERO).offset(piece.templatePosition());
                heights.add(generator.getBaseHeight(p.getX(),p.getZ(),net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG,level,state)-1);
            }
            Collections.sort(heights);check(piece.templatePosition().getY()==heights.get(4),"foundation is flush with the median terrain surface");
            check(heights.get(8)-heights.get(0)<=12&&piece.hasClearing(),"mountain-adapted slope budget and clearing enabled");
            var generated=level.getChunk(candidate.x,candidate.z,net.minecraft.world.level.chunk.ChunkStatus.STRUCTURE_STARTS).getStartForStructure(structure);
            check(generated!=null&&generated.isValid(),"real seeded generation records the structure start");
            System.out.println("OBSERVATORY_NATURAL_START "+piece.templatePosition()+" "+piece.getRotation()+" "+biome.unwrapKey());return;
        }
        throw new AssertionError("No naturally eligible mountain observatory found for test seed");
    }
    private static ServerPlayer player(ChangedEntity npc,String name)throws Exception {
        var player=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),name));player.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),player);
        var id=new NativeOriginData.Identity();id.state="human";NativeOriginData.get(server).players.put(player.getUUID(),id);
        var field=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(net.minecraft.server.players.PlayerList.class,"f_11197_");field.setAccessible(true);
        @SuppressWarnings("unchecked")var map=(Map<UUID,ServerPlayer>)field.get(server.getPlayerList());map.put(player.getUUID(),player);level.addNewPlayer(player);
        for(int[] offset:List.of(new int[]{1,0},new int[]{-1,0},new int[]{0,1},new int[]{0,-1},new int[]{1,1},new int[]{-1,-1})) {
            player.setPos(npc.getX()+offset[0],npc.getY(),npc.getZ()+offset[1]);
            if(level.noCollision(player)&&npc.hasLineOfSight(player))return player;
        }
        throw new AssertionError("No nearby conversation position for "+StoryNpcService.profile(npc).id());
    }
    private static void retire(ServerPlayer player){NpcConversations.close(player);player.discard();}
    private static void membership()throws Exception {
        var npc=chief("hyena_chief");var player=player(npc,"MemberTest");
        try {
            FactionReputation.set(npc,player,-40);check(CampMembershipService.canJoin(npc,player),"camp admission does not require regional faction reputation");
            FactionReputation.set(npc,player,20);check(CampMembershipService.join(npc,player,"join"),"human may enrol without transforming");
            check(CampMembershipService.member(npc,player)&&!ProcessTransfur.isPlayerTransfurred(player),"membership does not change the body");
            var wild=(ChangedEntity)npc.getType().create(level);var registration=CampMembershipService.membership(player);String realHome=registration.getString("HomeKey");registration.putString("HomeKey",level.dimension().location()+":0");
            check(!CampMembershipService.member(wild,player),"untagged wild creatures cannot inherit a camp membership through a default origin");registration.putString("HomeKey",realHome);wild.discard();
            check(FactionReputation.score(npc,player)==20&&CampMembershipService.score(npc,player)==0,"human admission grants zero trust and no regional reputation");
            check(NativeOriginService.identity(player).state.equals("human"),"birth state remains human");
            var pos=NaturalCampService.storage(npc).orElseThrow();check(CampMembershipService.sharedStorage(npc,player,pos),"home public store is shared");
            var other=chiefs.stream().filter(m->m!=npc&&StoryNpcService.profile(m).id().equals(StoryNpcService.profile(npc).id())).findFirst().orElseThrow();
            check(!CampMembershipService.sharedStorage(other,player,NaturalCampService.sites(other).storage().get(0)),"other camps retain their storage rules");
            CampMembershipService.adjust(other,player,-80);check(CampMembershipService.member(npc,player)&&CampMembershipService.score(npc,player)==0,"another camp has independent trust and cannot expel this member");
            var block=level.getBlockEntity(pos);block.getPersistentData().putBoolean("ChangedSynergyPrivate",true);
            check(!CampMembershipService.sharedStorage(npc,player,pos),"private store remains private");block.getPersistentData().remove("ChangedSynergyPrivate");
            check(LatexSocialMemory.shouldRemainNeutral(npc,player),"human faction member is recognized as a friend");
            CampMembershipService.adjust(npc,player,-15);check(CampMembershipService.membership(player).getBoolean("Suspended"),"distrust suspends rights");
            check(!CampMembershipService.sharedStorage(npc,player,pos),"suspended member cannot use shared supplies");
            CampMembershipService.adjust(npc,player,15);check(!CampMembershipService.membership(player).getBoolean("Suspended"),"neutrality restores rights");
            CampMembershipService.adjust(npc,player,-40);check(!CampMembershipService.member(npc,player),"hostility expels the member");
            CampMembershipService.adjust(npc,player,40);FactionReputation.set(npc,player,20);CampMembershipService.join(npc,player,"join");
            check(!BondTokenService.canCreateBond(npc,player)&&!NativeBondInvitationService.canOffer(npc,player),"fixed NPC cannot become a bond");
            LatexSocialMemory.addBond(npc,player);check(!LatexSocialMemory.isBonded(npc,player),"direct bond entry point is guarded");
            CreaturePersonality.setSocialFollowing(npc,player,true);check(!CreaturePersonality.isSocialFollowing(npc,player),"direct follow entry point is guarded");
            check(!BondedSuitService.canStartSuit(npc,player)&&BondedSuitService.shouldBlockNativeSuit(npc,player),"native and Synergy suit routes reject a fixed NPC");
            check(!net.parkabird.changedsynergy.world.inventory.BondedInventoryService.canAccess(player,npc),"personal equipment inventory stays closed");
            var moth=chief("canopy_chief");FactionReputation.set(moth,player,20);check(CampMembershipService.join(moth,player,"join"),"may transfer to another household");
            check(!CampMembershipService.member(npc,player)&&CampMembershipService.member(moth,player),"only one formal affiliation");
            check(CampMembershipData.load(CampMembershipData.get(server).save(new CompoundTag())).player(player.getUUID()).getString("HomeKey").equals(StoryNpcService.homeKey(moth)),"camp membership survives serialization");
            CampMembershipService.leave(player);check(!CampMembershipService.member(moth,player)&&FactionReputation.score(moth,player)==20,"leaving keeps reputation");
        }finally{retire(player);}
    }
    private static void campRewards()throws Exception {
        var npc=chief("hyena_chief");var p=player(npc,"LocalRewards");
        try {
            FactionReputation.set(npc,p,20);
            check(NpcRites.apply(npc,p,false),"prepare an existing reversible latex body without camp registration");
            check(CampMembershipService.join(npc,p,"join")&&CampMembershipService.score(npc,p)==2,"existing latex form receives two local trust");
            CampMembershipService.leave(p);check(CampMembershipService.join(npc,p,"join")&&CampMembershipService.score(npc,p)==2,"leave/rejoin cannot farm two trust");
            ProcessTransfur.removePlayerTransfurVariant(p);check(CampMembershipService.join(npc,p,"reversible")&&CampMembershipService.score(npc,p)==5,"later reversible admission tops up to five instead of stacking rewards");
            ProcessTransfur.removePlayerTransfurVariant(p);check(CampMembershipService.join(npc,p,"reversible")&&CampMembershipService.score(npc,p)==5,"repeating reversible ritual cannot farm trust");
            check(FactionReputation.score(npc,p)==20,"all admission bonuses remain separate from regional faction reputation");
        } finally {retire(p);}
        var legacy=player(npc,"LegacyMember");
        try {
            FactionReputation.set(npc,legacy,30);var data=CampMembershipData.get(server).player(legacy.getUUID());
            data.remove("MembershipVersion");data.putString("Faction",FactionReputation.groupId(npc));data.putString("HomeKey",StoryNpcService.homeKey(npc));data.putString("ReversibleForm","saved-ritual-marker");
            var rewards=new ListTag();rewards.add(StringTag.valueOf(FactionReputation.groupId(npc)));data.put("PermanentRewards",rewards);
            check(CampMembershipService.member(npc,legacy)&&CampMembershipService.score(npc,legacy)==0,"legacy membership migrates to its existing camp without importing regional reputation");
            check(!data.contains("Faction")&&data.getString("ReversibleForm").equals("saved-ritual-marker")&&data.getCompound("AdmissionRewards").getInt(StoryNpcService.homeKey(npc))==8,"migration preserves ritual history and prevents duplicate legacy permanent rewards");
            FactionReputation.set(npc,legacy,-100);check(CampMembershipService.score(npc,legacy)==0&&CampMembershipService.member(npc,legacy),"later regional changes do not rewrite migrated trust");
        } finally {retire(legacy);}
    }
    private static void permanentConfirmation()throws Exception {
        var npc=chief("canopy_chief");var p=player(npc,"RitualConsent");
        try {
            var feedback=new FeedbackConnection(p);p.connection=feedback;
            NpcConversations.open(npc,p);choose(p,"join_menu");choose(p,"copy_form");
            check(!NpcConversations.state(p).isEmpty()&&NpcConversations.state(p).getString("Node").equals("explain_form"),"form consequences are a separate authored conversation");
            check(hasChoiceAfterFinish(p,"permanent_form")==NpcRites.supportsPermanent(),"permanent choice is visible only with TT backend support");
            if(!NpcRites.supportsPermanent())return;
            var previous=NpcConversations.state(p);choose(p,"permanent_form");
            check(NpcConversations.state(p).getString("Node").equals("confirm_permanent")&&!ProcessTransfur.isPlayerTransfurred(p)&&!CampMembershipService.member(npc,p),"selecting permanent route only opens the NPC's second confirmation");
            NpcConversations.action(p,previous.getUUID("Session"),previous.getInt("Revision"),"confirm_permanent");check(!NativeOriginService.isNative(p),"stale or skipped confirmation cannot commit the permanent ritual");
            choose(p,"reconsider");check(!NativeOriginService.isNative(p)&&!CampMembershipService.member(npc,p),"reconsidering keeps body and membership unchanged");
            check(feedback.sounds==0,"browsing or cancelling a permanent ritual plays no transfur sound");
            choose(p,"permanent_form");choose(p,"confirm_permanent");check(NativeOriginService.isNative(p)&&CampMembershipService.score(npc,p)==8,"second confirmed choice alone commits native identity and local reward");
            check(feedback.sounds==1,"a successful permanent ritual plays the native transfur sound exactly once");
        } finally {retire(p);}
    }
    private static boolean hasChoiceAfterFinish(ServerPlayer p,String id){finishLine(p);return hasChoice(p,id);}
    private static void finishLine(ServerPlayer p){for(int i=0;i<40;i++){var s=NpcConversations.state(p);if(s.isEmpty()||s.getBoolean("Last"))return;NpcConversations.action(p,s.getUUID("Session"),s.getInt("Revision"),"advance");}throw new AssertionError("Too many lines");}
    private static void choose(ServerPlayer p,String id){finishLine(p);var s=NpcConversations.state(p);NpcConversations.action(p,s.getUUID("Session"),s.getInt("Revision"),id);}
    private static void dialogue()throws Exception {
        var npc=chief("canopy_chief");var p=player(npc,"TalkTest");var other=player(npc,"BusyTest");
        try {
            check(RelationshipFavorService.canOpenBasicWheel(npc,p),"a stranger may open the fixed NPC's wheel");
            FactionReputation.set(npc,p,20);
            var menu=new net.parkabird.changedsynergy.world.inventory.SocialInteractionMenu(1,p.getInventory(),npc);
            var command=new CompoundTag();command.putString("command","npc_dialogue");menu.update(command,net.minecraftforge.fml.LogicalSide.SERVER,p);
            check(!NpcConversations.state(p).isEmpty(),"wheel action opens learned speech for a human");
            check(NpcConversations.state(p).getList("Parts",10).stream().map(t->(CompoundTag)t).noneMatch(t->t.getString("Channel").equals("mental")),"uncomprehending human receives no hidden thought text");
            var text=NpcConversations.state(p).getList("Parts",10).getCompound(0);
            check(text.getString("Emotion").equals("calm")&&text.getString("Effect").equals("none")&&text.getFloat("Speed")>0&&text.getFloat("Intensity")==0,"server preserves the authored quiet baseline before local emotional accents");
            NpcConversations.open(npc,other);check(NpcConversations.state(other).isEmpty(),"single listener per NPC");
            var old=NpcConversations.state(p);NpcConversations.action(p,UUID.randomUUID(),old.getInt("Revision"),"confirm_permanent");check(!ProcessTransfur.isPlayerTransfurred(p),"forged session cannot invoke a ritual");
            choose(p,"join_menu");check(NpcConversations.state(p).getString("Node").equals("enrol"),"data-driven branch opens");
            NpcConversations.action(p,old.getUUID("Session"),old.getInt("Revision"),"join_as_is");check(!CampMembershipService.member(npc,p),"stale revision cannot commit enrolment");
            choose(p,"join_as_is");check(CampMembershipService.member(npc,p),"visible human admission commits without changing body or trust");
            NpcConversations.close(p);NpcConversations.open(npc,p);check(NpcConversations.state(p).getString("Node").equals("return"),"personal dialogue progress persists");
            var cancel=NpcConversations.state(p);NpcConversations.action(p,cancel.getUUID("Session"),cancel.getInt("Revision")-1,"cancel");check(NpcConversations.state(p).isEmpty(),"Esc releases a session even when a newer line was already sent");
            NpcConversations.open(npc,p);
            p.setPos(p.getX()+30,p.getY(),p.getZ());server.getWorldData().overworldData().setGameTime(2000);NpcConversations.tick(server);check(NpcConversations.state(p).isEmpty(),"leaving releases the NPC");
        }finally{retire(p);retire(other);}
    }
    private static void safety()throws Exception {
        var source=chief("hyena_chief");var target=chief("canopy_chief");
        var event=new net.ltxprogrammer.changed.process.TransfurEvents.ChangedEntityFusionWithChangedEntityDecisionEvent(net.ltxprogrammer.changed.ability.IAbstractChangedEntity.forEntity(source),net.ltxprogrammer.changed.ability.IAbstractChangedEntity.forEntity(target),source.getSelfVariant());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);check(event.isCanceled(),"fusion cannot consume a fixed source or target");
        var oldKey=StoryNpcService.key(source);var otherLevel=server.getLevel(net.minecraft.world.level.Level.NETHER);
        var raw=source.saveWithoutId(new CompoundTag());var moved=(ChangedEntity)source.getType().create(otherLevel);moved.load(raw);
        check(StoryNpcService.homeKey(moved).equals(StoryNpcService.homeKey(source)),"cross-dimension transport preserves the original structure key");
        StoryNpcService.join(new net.minecraftforge.event.entity.EntityJoinLevelEvent(moved,otherLevel));
        check(StoryNpcService.key(moved).equals(oldKey),"dimension changes never register a second NPC instance");
        // The real actor has not moved; put its current snapshot back after exercising the join hook.
        StoryNpcService.join(new net.minecraftforge.event.entity.EntityJoinLevelEvent(source,level));
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static void rites()throws Exception {
        var npc=chief("hyena_chief");
        legacyRitualOrigins(npc);
        for(boolean origins:List.of(false,true))for(boolean instinct:List.of(false,true)) {
            level.getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(origins,server);
            net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.allowHumanOriginInstinct.set(instinct);
            var p=player(npc,"ReversibleTest");
            try {
                var feedback=new FeedbackConnection(p);p.connection=feedback;
                FactionReputation.set(npc,p,20);check(CampMembershipService.join(npc,p,"reversible"),"reversible ritual works under either origin policy");
                check(feedback.sounds==1,"a successful reversible ritual plays the native transfur sound exactly once");
                check(!NpcRites.apply(npc,p,false)&&feedback.sounds==1,"a rejected duplicate ritual produces no additional sound");
                check(NpcRites.reversible(p)&&!NativeOriginPolicy.allows(p),"only the selected ritual suppresses TT permanence");
                check(!TrueTransfurCompat.blocksReversal(p)&&!TrueTransfurCompat.isPermanentPlayer(p),"ordinary ritual stays reversible with TT installed");
                check(NativeOriginService.identity(p).state.equals("human"),"ritual does not rewrite birth identity");
                ProcessTransfur.removePlayerTransfurVariant(p);check(p.isAlive()&&!ProcessTransfur.isPlayerTransfurred(p),"reversal is nonlethal and complete");
            }finally{retire(p);}
        }
        var p=player(npc,"PermanentTest");try {
            FactionReputation.set(npc,p,20);
            if(!NpcRites.supportsPermanent()){check(!CampMembershipService.join(npc,p,"permanent")&&!ProcessTransfur.isPlayerTransfurred(p),"no TT means no permanent ritual");}
            else {
                check(CampMembershipService.join(npc,p,"permanent"),"TT accepts explicit permanent ritual");
                check(TrueTransfurCompat.isPermanentPlayer(p)&&TrueTransfurCompat.blocksReversal(p),"permanence is real TT state");
                var reversal=(net.minecraftforge.common.ForgeConfigSpec.BooleanValue)Class.forName("com.lugert.true_transfur.config.TrueTransfurConfig").getField("ENABLE_REVERSE_TRANSFUR_DEATH").get(null);
                boolean previousReverse=reversal.get();try{reversal.set(false);check(TrueTransfurCompat.blocksReversal(p),"chosen permanent body retains native-origin reversal protection when the lethal reversal option is disabled");}finally{reversal.set(previousReverse);}
                for(boolean origins:List.of(false,true))for(boolean instinct:List.of(false,true)) {
                    level.getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(origins,server);
                    net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.allowHumanOriginInstinct.set(instinct);
                    check(NativeOriginPolicy.allows(p)&&NativeOriginPolicy.allowsInstinct(p),"permanent ritual gets native TT eligibility independently of human-start settings");
                }
                var mode=Class.forName("com.lugert.true_transfur.transfur.TransfurController").getMethod("getControlMode",ServerPlayer.class);
                check(mode.invoke(null,p).toString().equals("PLAYER"),"permanent ritual starts in synchronized player control");
                check(FactionReputation.score(npc,p)==20&&CampMembershipService.score(npc,p)==8,"first permanent admission grants eight local trust without regional rewards");
                check(NativeOriginService.isNative(p)&&NativeOriginService.identity(p).form.equals(npc.getSelfVariant().getFormId().toString()),"permanent ritual commits the chosen native identity");
                assertImmediateReversalBlocked(p);
                CampMembershipService.leave(p);check(NativeOriginService.isNative(p)&&TrueTransfurCompat.blocksReversal(p),"leaving does not undo a permanent body");
                check(CampMembershipService.join(npc,p,"join")&&CampMembershipService.score(npc,p)==8,"rejoining cannot farm the reward");
                check(!CampMembershipService.join(npc,p,"permanent")&&CampMembershipService.score(npc,p)==8,"replaying the ritual cannot farm the reward");
                var saved=p.saveWithoutId(new CompoundTag());
                var reloaded=new ServerPlayer(server,level,p.getGameProfile());reloaded.connection=p.connection;reloaded.load(saved);
                check(TrueTransfurCompat.isPermanentPlayer(reloaded)&&NativeOriginService.isNative(reloaded),"permanent body and native identity survive player NBT reload");
                var respawned=new ServerPlayer(server,level,p.getGameProfile());respawned.connection=p.connection;respawned.getPersistentData().merge(p.getPersistentData().copy());
                NativeOriginService.restore(respawned);
                check(TrueTransfurCompat.isPermanentPlayer(respawned)&&ProcessTransfur.isPlayerTransfurred(respawned),"death-style fresh body restores the confirmed permanent ritual");
                check(FactionReputation.score(npc,respawned)==20&&CampMembershipService.score(npc,respawned)==8,"restoring a permanent body grants no additional reward");
                var changed=chief("observatory_chief").getSelfVariant();
                check(TrueTransfurCompat.initializePersistentBody(p,changed,null,1F),"persistent body initialization is shared with Native Origin");
                NativeOriginService.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(p));
                var account=CampMembershipData.get(server).player(p.getUUID());
                check(NativeOriginService.identity(p).current.equals(changed.getFormId().toString())
                        &&NativeOriginService.identity(p).form.equals(npc.getSelfVariant().getFormId().toString())
                        &&!account.contains("PermanentForm"),"one Native Origin record owns the original and current permanent forms");
                var suit=TrueTransfurCompat.initializePersistentBody(p,npc.getSelfVariant(),null,1F);
                check(suit,"temporary-body memory fixture initialized");ProcessTransfur.getPlayerTransfurVariant(p).setTemporaryForSuit(true);
                NativeOriginService.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(p));
                check(NativeOriginService.identity(p).current.equals(changed.getFormId().toString()),"temporary suits do not replace the saved permanent body");
                var returning=new ServerPlayer(server,level,p.getGameProfile());returning.connection=p.connection;
                var reborn=new CompoundTag();reborn.putString("Phase","SLEEPING");returning.getPersistentData().put("TrueTransfurReborn",reborn);
                NativeOriginService.restore(returning);check(!ProcessTransfur.isPlayerTransfurred(returning),"restoration waits for TT rebirth");returning.getPersistentData().remove("TrueTransfurReborn");
                var cap=(net.minecraftforge.common.capabilities.Capability)Class.forName("com.lugert.true_transfur.transfur.TransfurCapabilities").getField("TRANSFUR_DATA").get(null);
                Object control=returning.getCapability(cap).resolve().orElseThrow();control.getClass().getMethod("setTransfurred",boolean.class).invoke(control,true);
                Class controlMode=Class.forName("com.lugert.true_transfur.transfur.ControlMode");
                var setMode=Class.forName("com.lugert.true_transfur.transfur.TransfurController").getMethod("setControlMode",ServerPlayer.class,controlMode);
                setMode.invoke(null,returning,Enum.valueOf(controlMode,"INSTINCT"));NativeOriginService.restore(returning);
                check(!ProcessTransfur.isPlayerTransfurred(returning),"permanent-body recovery never fills or takes over an instinct shell");
                setMode.invoke(null,returning,Enum.valueOf(controlMode,"PLAYER"));
                NativeOriginService.respawn(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent(returning,false));
                var restored=ProcessTransfur.getPlayerTransfurVariant(returning);
                check(restored!=null&&restored.getParent()==changed&&!restored.isTemporaryFromSuit()&&restored.willSurviveTransfur
                        &&restored.transfurProgression==1F&&restored.transfurProgressionO==1F,"respawn immediately restores the latest permanent body with native initialization");
                var fallback=new ServerPlayer(server,level,p.getGameProfile());fallback.connection=p.connection;
                NativeOriginService.identity(p).current="missing:removed_form";
                NativeOriginService.login(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(fallback));
                check(ProcessTransfur.getPlayerTransfurVariant(fallback).getParent()==npc.getSelfVariant(),"login falls back to the original permanent form if the current form is absent");
                check(NativeOriginService.isNative(p)&&CampMembershipService.score(npc,p)==8,"current-form recovery preserves native identity and admission history");
                ProcessTransfur.getPlayerTransfurVariant(p).setTemporaryForSuit(false);
                selectHuman(p);
                check(!TrueTransfurCompat.isPermanentPlayer(p)&&!TrueTransfurCompat.blocksReversal(p),"confirmed human re-selection clears TT eligibility and reversal protection");
                var reset=CampMembershipData.get(server).player(p.getUUID()).copy();
                for(int t=20;t<=120;t+=20) {
                    p.tickCount=t;NpcRites.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
                    NativeOriginService.playerTick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
                }
                check(!ProcessTransfur.isPlayerTransfurred(p)&&CampMembershipData.get(server).player(p.getUUID()).equals(reset),"human reset stays human without replaying rewards or permanent restoration");
            }
        }finally{retire(p);}
    }
    private static void assertImmediateReversalBlocked(ServerPlayer player)throws Exception {
        var body=ProcessTransfur.getPlayerTransfurVariant(player);
        check(body!=null,"reversal fixture has a body");
        ProcessTransfur.removePlayerTransfurVariant(player);
        check(player.isAlive()&&ProcessTransfur.getPlayerTransfurVariant(player)==body,"direct reversal leaves the exact living native body in place immediately");
        for(String command:List.of("untf","untransfur")) {
            server.getCommands().getDispatcher().execute(command,player.createCommandSourceStack().withPermission(4));
            check(player.isAlive()&&ProcessTransfur.getPlayerTransfurVariant(player)==body,command+" is blocked without removing or recreating the native body");
        }
    }
    private static UUID selectionNonce(ServerPlayer player)throws Exception {
        var field=NativeOriginService.class.getDeclaredField("SELECTING");field.setAccessible(true);
        var selection=((Map<?,?>)field.get(null)).get(player.getUUID());
        var nonce=selection.getClass().getDeclaredField("nonce");nonce.setAccessible(true);return (UUID)nonce.get(selection);
    }
    private static void selectHuman(ServerPlayer player)throws Exception {
        var membership=CampMembershipData.get(server).player(player.getUUID()).copy();
        check(NativeOriginService.reopen(player),"permanent ritual player can reopen Native Origin");
        NativeOriginService.choose(player,new net.parkabird.changedsynergy.network.NativeOriginChoosePacket(selectionNonce(player),null,false,true));
        check(!NativeOriginService.pending(player)&&ProcessTransfur.isPlayerTransfurred(player),"cancelling optional re-selection preserves the permanent body");
        check(NativeOriginService.reopen(player),"re-selection can be reopened after cancellation");
        NativeOriginService.choose(player,new net.parkabird.changedsynergy.network.NativeOriginChoosePacket(selectionNonce(player),null,false,false));
        check(player.isAlive()&&!NativeOriginService.pending(player)&&!NativeOriginService.isNative(player)&&!ProcessTransfur.isPlayerTransfurred(player),"human confirmation succeeds immediately without a form_failed notice");
        check(CampMembershipData.get(server).player(player.getUUID()).equals(membership),"human re-selection keeps camp membership, local trust and admission rewards");
    }
    private static void legacyRitualOrigins(ChangedEntity npc)throws Exception {
        level.getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(true,server);
        var player=player(npc,"LegacyPermanent");
        try {
            var form=npc.getSelfVariant();var changed=chief("observatory_chief").getSelfVariant();
            check(TrueTransfurCompat.initializePersistentBody(player,changed,null,1F),"legacy current-body fixture initializes");
            var account=CampMembershipData.get(server).player(player.getUUID());
            account.putString("PermanentForm",form.getFormId().toString());account.putString("PermanentCurrentForm",changed.getFormId().toString());
            account.putInt("LocalTrustFixture",17);
            assertImmediateReversalBlocked(player);
            var position=player.position();
            NpcRites.migratePermanent(player);
            var origin=NativeOriginService.identity(player);
            check(NativeOriginService.isNative(player)&&origin.form.equals(form.getFormId().toString())&&origin.current.equals(changed.getFormId().toString()),"legacy permanent records migrate original and current forms into Native Origin");
            check(player.position().equals(position)&&account.getInt("LocalTrustFixture")==17&&!account.contains("PermanentForm")&&!account.contains("PermanentCurrentForm"),"migration keeps position and camp history and retires the duplicate body record");
            selectHuman(player);
            check(!NativeOriginPolicy.allows(player),"reset legacy player follows ordinary human origin policy");
            account.putString("PermanentForm","missing:removed_form");account.putString("PermanentCurrentForm","missing:removed_form");
            check(NativeOriginService.reopen(player),"missing legacy forms do not lock the origin selector");
            NativeOriginService.choose(player,new net.parkabird.changedsynergy.network.NativeOriginChoosePacket(selectionNonce(player),null,false,false));
            check(!NativeOriginService.pending(player)&&!NpcRites.permanent(player)&&!NativeOriginService.isNative(player),"human confirmation also clears unavailable legacy permanent forms");
        } finally {retire(player);}
    }
    private static void reload()throws Exception {
        var store=StoryNpcData.get(server);check(store.records.size()==16,"all independent instance records survive an actual server restart");
        for(var record:store.records.values())activate(BlockPos.of(record.getLong("LastPos")));flushEntities();
        for(var record:store.records.values()){
            var actor=StoryNpcService.actor(server,record);check(actor!=null,"saved physical NPC loads after restart");
            check(StoryNpcService.key(actor).equals(record.getString("Key")),"NPC key is stable after restart");
            check(level.getEntitiesOfClass(ChangedEntity.class,actor.getBoundingBox().inflate(64),m->StoryNpcService.key(m).equals(record.getString("Key"))).size()==1,"one physical actor per instance after restart");
            for(String item:StoryNpcService.profile(actor).equipment())check(AccessorySlots.isWearing(actor,s->net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(item)&&StoryNpcService.bound(s)),"bound clothing persists on restart");
        }
        var researcher=store.records.values().stream().map(r->StoryNpcService.actor(server,r)).filter(m->m.getName().getString().equals("SavedResearcher")).findFirst().orElseThrow();
        check(!CreaturePersonality.establishedRelationshipPlayerUuids(researcher).isEmpty(),"returned character's personal relationship survives restart");
        check(store.records.values().stream().anyMatch(r->!r.getCompound("Progress").isEmpty()),"dialogue history survives restart");
        check(CampMembershipData.get(server).players.values().stream().anyMatch(p->!p.getCompound("AdmissionRewards").isEmpty()),"local admission reward history survives restart");
    }
    private static void returning()throws Exception {
        var npc=chief("lab_researcher");String key=StoryNpcService.key(npc);var store=StoryNpcData.get(server);var record=store.records.get(key);
        var previous=npc.saveWithoutId(new CompoundTag());var oldId=npc.getUUID();npc.setCustomName(net.minecraft.network.chat.Component.literal("SavedResearcher"));
        var friend=player(npc,"ReturnFriend");CreaturePersonality.establishRelationship(npc,friend);CreaturePersonality.rememberPatReceived(npc,friend);LatexSocialMemory.markRelationshipBetrayal(npc,friend);
        npc.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND));npc.setDropChance(EquipmentSlot.MAINHAND,2F);
        npc.hurt(level.damageSources().genericKill(),Float.MAX_VALUE);npc.discard();
        check(StoryNpcService.RETURN_DELAY==1200L&&record.getBoolean("ConfirmedGone")&&record.getLong("Due")==server.overworld().getGameTime()+StoryNpcService.RETURN_DELAY,"death schedules a confirmed 1-minute return");
        check(!StoryNpcService.restore(server,record),"cannot return before the delay");
        server.getWorldData().overworldData().setGameTime(record.getLong("Due")+20);
        var home=BlockPos.containing(record.getDouble("HomeX"),record.getDouble("HomeY"),record.getDouble("HomeZ"));
        activate(home);flushEntities();
        for(int attempt=0;attempt<1000&&!level.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(home).toLong());attempt++){activate(home);server.pollTask();level.getChunkSource().pollTask();Thread.sleep(1);}
        long last=record.getLong("LastPos");var unloaded=new BlockPos(900000,100,900000);record.putLong("LastPos",unloaded.asLong());record.putBoolean("ConfirmedGone",false);
        check(!StoryNpcService.restore(server,record)&&!level.hasChunkAt(unloaded),"unconfirmed unloaded last-known body is not treated as missing and never force-loaded");
        record.putBoolean("ConfirmedGone",true);
        var blocked=new LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(var p:BlockPos.betweenClosed(home.offset(-3,-1,-3),home.offset(3,5,3))){blocked.put(p.immutable(),level.getBlockState(p));level.setBlock(p,Blocks.STONE.defaultBlockState(),2);}
        check(!StoryNpcService.restore(server,record)&&blocked.keySet().stream().allMatch(p->level.getBlockState(p).is(Blocks.STONE)),"unsafe return waits without changing the building");blocked.forEach((p,s)->level.setBlock(p,s,2));
        check(StoryNpcService.restore(server,record)&&!level.hasChunkAt(unloaded),"confirmed dead NPC returns to loaded home even if LastPos is in an unloaded chunk");
        if(last!=0)record.putLong("LastPos",last);
        var restored=StoryNpcService.actor(server,record);check(restored!=null&&!restored.getUUID().equals(oldId)&&StoryNpcService.key(restored).equals(key),"new body retains the stable identity");
        check(restored.getName().getString().equals("SavedResearcher"),"name persists across death");
        check(restored.getBasicPlayerInfo().getEyeStyle()==net.ltxprogrammer.changed.entity.EyeStyle.V1&&restored.getBasicPlayerInfo().getLeftIrisColor().toInt()==0x65C98C,"researcher original mint-green eyes persist across return");
        for(String item:StoryNpcService.profile(restored).equipment())check(AccessorySlots.isWearing(restored,s->net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(item)&&StoryNpcService.bound(s)),"default clothing returns even after Curios clears the dying body's inventory");
        check(CreaturePersonality.establishedRelationshipPlayerUuids(restored).contains(friend.getUUID())&&LatexSocialMemory.isProvoked(restored,friend),"personal relationship and crime memory survive return");
        check(CreatureMorphAliasData.get(server).resolveAlias(oldId).equals(restored.getUUID()),"offline relationship references resolve to the new body");
        check(restored.getMainHandItem().isEmpty(),"gifted equipment is not recreated after dropping");
        check(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(home).inflate(6),d->d.getItem().is(Items.DIAMOND)).stream().mapToInt(d->d.getItem().getCount()).sum()==1,"extra equipment drops exactly once");retire(friend);
        var clone=npc.getType().create(level);clone.load(previous);check(!level.addFreshEntity(clone),"stale physical duplicate is rejected");
        check(!StoryNpcService.restore(server,record),"cannot spawn a second living instance");
        check(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,restored.getBoundingBox().inflate(5),d->StoryNpcService.bound(d.getItem())).isEmpty(),"default outfit is never a repeatable drop");
    }
    private static void builtInResidentRespawn()throws Exception {
        check(StoryNpcService.CAMP_MEMBER_DELAY==3600L&&StoryNpcService.SMALL_CAMP_DELAY==6000L,"community respawn tiers match configured delays");
        var store=StoryNpcData.get(server);
        var hyenaChief=chief("hyena_chief");
        var campBounds=NaturalCampService.bounds(hyenaChief);
        var resident=level.getEntitiesOfClass(ChangedEntity.class,campBounds,m->!StoryNpcService.isNpc(m)&&StoryNpcService.keepsIdentityOnDeath(m)).stream().findFirst().orElseThrow();
        String resKey=StoryNpcService.key(resident);var resRecord=store.records.get(resKey);
        check(resRecord!=null&&resRecord.getBoolean("Resident")&&!resRecord.getBoolean("SmallCamp")&&!StoryNpcService.isNpc(resident),"built-in main camp resident is tracked separately from Story NPCs");
        var resFriend=player(resident,"ResidentFriend");
        try {
            CreaturePersonality.establishRelationship(resident,resFriend);
            var req=CampLifeService.forResident(resident);
            check(req!=null&&!req.getString("OwnerKey").isEmpty(),"personal resident request records stable OwnerKey");
            String reqId=req.getString("Id");var oldResId=resident.getUUID();
            resident.hurt(level.damageSources().genericKill(),Float.MAX_VALUE);resident.discard();
            check(resRecord.getBoolean("ConfirmedGone")&&resRecord.getLong("Due")==server.overworld().getGameTime()+StoryNpcService.CAMP_MEMBER_DELAY,"main camp resident schedules 3-minute respawn");
            check(!net.parkabird.changedsynergy.ai.BondedCreatureDeathData.get(server).isDead(resFriend.getUUID(),oldResId),"managed camp resident does not emit permanent bonded death tombstone");
            check(!StoryNpcService.restore(server,resRecord),"main camp resident waits until delay");
            server.getWorldData().overworldData().setGameTime(resRecord.getLong("Due")+20);
            var resHome=BlockPos.containing(resRecord.getDouble("HomeX"),resRecord.getDouble("HomeY"),resRecord.getDouble("HomeZ"));
            activate(resHome);flushEntities();
            check(StoryNpcService.restore(server,resRecord),"main camp resident respawns cleanly");
            var restoredRes=StoryNpcService.actor(server,resRecord);
            check(restoredRes!=null&&!restoredRes.getUUID().equals(oldResId)&&StoryNpcService.key(restoredRes).equals(resKey)&&!StoryNpcService.isNpc(restoredRes),"respawned resident keeps stable camp key and non-Story classification");
            check(CreaturePersonality.establishedRelationshipPlayerUuids(restoredRes).contains(resFriend.getUUID()),"resident personal relationship survives respawn");
            var reqAfter=CampLifeService.forResident(restoredRes);
            check(reqAfter!=null&&reqAfter.getString("Id").equals(reqId),"personal request remains bound to respawned resident via OwnerKey");
        } finally {retire(resFriend);}
        var chiefKey=StoryNpcService.key(hyenaChief);var chiefRecord=store.records.get(chiefKey);
        var chiefHome=BlockPos.containing(chiefRecord.getDouble("HomeX"),chiefRecord.getDouble("HomeY"),chiefRecord.getDouble("HomeZ"));
        var savedHead=new LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(var blockPos:List.of(chiefHome.above(),chiefHome.above(2))){savedHead.put(blockPos,level.getBlockState(blockPos));level.setBlock(blockPos,Blocks.STONE.defaultBlockState(),2);}
        try {
            hyenaChief.hurt(level.damageSources().genericKill(),Float.MAX_VALUE);hyenaChief.discard();
            server.getWorldData().overworldData().setGameTime(chiefRecord.getLong("Due")+20);
            activate(chiefHome);flushEntities();
            check(StoryNpcService.restore(server,chiefRecord),"chief on blocked slab falls back to adjacent safe surface");
            var movedChief=StoryNpcService.actor(server,chiefRecord);
            check(movedChief!=null&&(movedChief.blockPosition().getX()!=chiefHome.getX()||movedChief.blockPosition().getZ()!=chiefHome.getZ())&&level.noCollision(movedChief),"fallback picks clear adjacent surface without clipping into neighboring blocks");
            chiefs.remove(hyenaChief);chiefs.add(movedChief);
        } finally {savedHead.forEach((p0,s0)->level.setBlock(p0,s0,2));}
        var oasis=server.getStructureManager().getOrCreate(ResourceLocation.parse("changed_synergy:oasis"));
        var oasisOrigin=new BlockPos(2600,100,1000);var oasisSettings=new StructurePlaceSettings().setRotation(Rotation.NONE);
        var oasisBox=oasis.getBoundingBox(oasisSettings,oasisOrigin);
        for(int cx=oasisBox.minX()>>4;cx<=oasisBox.maxX()>>4;cx++)for(int cz=oasisBox.minZ()>>4;cz<=oasisBox.maxZ()>>4;cz++)activate(new BlockPos(cx*16,100,cz*16));
        check(oasis.placeInWorld(level,oasisOrigin,oasisOrigin,oasisSettings,net.minecraft.util.RandomSource.create(19),2),"place small camp oasis");
        var smallResidents=level.getEntitiesOfClass(ChangedEntity.class,AABB.of(oasisBox),StoryNpcService::keepsIdentityOnDeath);
        check(smallResidents.size()==2,"small camp registers all built-in residents for respawn");
        var smallRes=smallResidents.get(0);String smallKey=StoryNpcService.key(smallRes);var smallRecord=store.records.get(smallKey);
        check(smallRecord!=null&&smallRecord.getBoolean("SmallCamp")&&NaturalCampService.smallCamp(smallRes),"small camp resident tagged with SmallCamp tier");
        smallRes.hurt(level.damageSources().genericKill(),Float.MAX_VALUE);smallRes.discard();
        check(smallRecord.getLong("Due")==server.overworld().getGameTime()+StoryNpcService.SMALL_CAMP_DELAY,"small camp resident schedules 5-minute respawn");
        check(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,AABB.of(oasisBox).inflate(4),d->StoryNpcService.bound(d.getItem())).isEmpty(),"built-in camp resident outfit never drops as repeatable loot");
        server.getWorldData().overworldData().setGameTime(smallRecord.getLong("Due")+20);
        var smallHome=BlockPos.containing(smallRecord.getDouble("HomeX"),smallRecord.getDouble("HomeY"),smallRecord.getDouble("HomeZ"));
        activate(smallHome);flushEntities();
        check(StoryNpcService.restore(server,smallRecord),"small camp resident respawns after 5-minute delay");
        var compRes=smallResidents.get(1);String compKey=StoryNpcService.key(compRes);
        var owner=player(compRes,"CompanionOwner");
        try {
            LatexSocialMemory.addBond(compRes,owner);
            check(CreatureCommunityData.isPlayerCompanion(compRes),"bonded resident becomes player companion");
            compRes.hurt(level.damageSources().genericKill(),Float.MAX_VALUE);compRes.discard();
            check(!store.records.containsKey(compKey)&&!compRes.getPersistentData().getBoolean("ChangedSynergyStoryNpc"),"companion resident retires from camp respawn instead of duplicating");
        } finally {retire(owner);}
    }
}
