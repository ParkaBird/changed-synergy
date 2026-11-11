package net.parkabird.changedsynergy.regression;

import java.util.*;
import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.ltxprogrammer.changed.data.*;
import net.ltxprogrammer.changed.entity.*;
import net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision;
import net.ltxprogrammer.changed.init.*;
import net.ltxprogrammer.changed.process.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.init.ChangedSynergyItems;
import net.parkabird.changedsynergy.item.*;

final class CanopyServerChecks {
    private static int checks;
    private static final String ROOT="ChangedSynergyTransfurNegotiation";
    static void run(MinecraftServer server) throws Exception {
        clothing(server); forgiveness(server); completedForgiveness(server); treehouse(server);
        System.out.println("CANOPY_SERVER_PASS: "+checks+" clothing anatomy, native conflicts, forgiveness and canopy resident checks");
    }
    static void clothing(MinecraftServer server) {
        var item=ChangedSynergyItems.FOREST_CANOPY_OUTFIT.get();var stack=new ItemStack(item);
        check(!ArmorItem.class.isInstance(item),"native apparel");
        for(var name:List.of("changed_synergy:latex_silk_moth","changed_synergy:latex_muntjac_female","changed:white_latex_wolf_male",
                "changed:latex_snake","changed:latex_mermaid_shark","changed:white_latex_centaur","changed:milk_pudding",
                "changed:dark_latex_wolf_pup","changed:pure_white_latex_wolf_pup","changed:latex_human")) {
            var type=ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.parse(name));
            check(type!=null,"test species exists: "+name);
            var mob=(ChangedEntity)type.create(server.overworld());AccessoryEntities.INSTANCE.forceReloadAccessories(mob);
            var slots=AccessorySlots.getForEntity(mob).orElseThrow();check(ForestCanopyItem.fits(mob),"anatomy accepted: "+name);
            boolean young=EagleOwlAviatorItem.juvenile(mob);
            if(young){
                check(slots.quickMoveStack(stack.copy()),"pup can wear circlet");
                check(!ChangedAccessorySlots.BODY.get().canHoldItem(stack,mob),"pup cannot wear adult body");
                var occupied=slots.getSlotTypes().filter(s->slots.getItem(s).filter(i->i.is(item)).isPresent()).findFirst().orElseThrow();
                check(!slots.isItemAllowedWithOthers(occupied==ChangedAccessorySlots.HEAD.get()?ChangedAccessorySlots.FACE.get():ChangedAccessorySlots.HEAD.get(),stack),"pup cannot duplicate headwear");
            }else for(var slot:List.of(ChangedAccessorySlots.BODY.get(),ChangedAccessorySlots.FULL_BODY.get())){
                check(slots.isItemAllowedWithOthers(slot,stack),"native inner/outer equip: "+name+slot);
                slots.setItem(slot,stack.copy());
                check(!slots.isItemAllowedWithOthers(slot==ChangedAccessorySlots.BODY.get()?ChangedAccessorySlots.FULL_BODY.get():ChangedAccessorySlots.BODY.get(),stack),"no duplicate outfit");
                check(item.shouldDisableSlot(new AccessorySlotContext<>(mob,slot,stack),ChangedAccessorySlots.HEAD.get()),"circlet reserves head");
                if(HyenaTrailclothItem.hasLowerSection(mob))check(!slots.isItemAllowedWithOthers(ChangedAccessorySlots.LEGS.get(),new ItemStack(ChangedItems.BLACK_PANTS.get())),"native trousers conflict");
                var saved=mob.saveWithoutId(new CompoundTag());var clone=(ChangedEntity)type.create(server.overworld());clone.load(saved);
                check(AccessorySlots.isWearing(clone,s->s.is(item)),"clothing survives reload");clone.discard();slots.setItem(slot,ItemStack.EMPTY);
            }
            mob.discard();
        }
        var human=player(server);AccessoryEntities.INSTANCE.forceReloadAccessories(human);
        check(AccessorySlots.getForEntity(human).orElseThrow().quickMoveStack(stack.copy()),"human uses same native clothing inventory");human.discard();
        System.out.println("RANGER_CLOTHING_SERVER_PASS: "+checks+" native slots, duplicate prevention, conflicts and reload checks");
    }
    private static void forgiveness(MinecraftServer server) throws Exception {
        var player=player(server);var level=server.overworld();var variant=ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get();
        boolean takeover=net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.takeoverEnabled.get();
        boolean keep=level.getGameRules().getBoolean(ChangedGameRules.RULE_KEEP_BRAIN);
        level.getGameRules().getRule(ChangedGameRules.RULE_KEEP_BRAIN).set(true,server);
        for(var pos:BlockPos.betweenClosed(new BlockPos(994,119,994),new BlockPos(1008,119,1008)))level.setBlock(pos,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);
        var syringe=ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("changed_addon:diffusion_syringe"));
        boolean addon=net.minecraftforge.fml.ModList.get().isLoaded("changed_addon");
        net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.takeoverEnabled.set(false);
        try {for(boolean absorbed:List.of(false,true)) {
            InvoluntaryTransfurNegotiation.abandonForSecondaryTransfur(player);ProcessTransfur.removePlayerTransfurVariant(player);
            var source=variant.getEntityType().create(level);source.setNoAi(true);source.setPos(1000,120,1000);activate(level,source.blockPosition());level.addFreshEntity(source);
            source.setCustomName(net.minecraft.network.chat.Component.literal("OriginalCanopySource"));source.getPersistentData().putBoolean("CanopyOriginal",true);
            player.setPos(1001,120,1000);player.setOnGround(true);CreaturePersonality.setFamiliarity(source,player,12);
            var decision=LatexAssimilationDecision.strong(absorbed?LatexAssimilationDecision.Method.ABSORPTION:LatexAssimilationDecision.Method.REPLICATION,
                    variant,TransfurContext.npcLatexAttack(source),100F);
            var event=new TransfurEvents.LatexAssimilationDecisionEvent(player,decision);InvoluntaryTransfurNegotiation.observeDecision(event);
            ProcessTransfur.setPlayerTransfurVariant(player,variant,null,1,false);
            event.getDecision().postTransfurListener().accept(IAbstractChangedEntity.forPlayer(player));
            check(level.getEntity(source.getUUID())==source,"fixture source is visible in loaded chunk");
            check(absorbed?InvoluntaryTransfurNegotiation.canNegotiateAbsorption(player):InvoluntaryTransfurNegotiation.canNegotiate(player,source),"completed claim negotiable: "+absorbed);
            var wrong=variant.getEntityType().create(level);check(!InvoluntaryTransfurNegotiation.forgive(player,wrong),"wrong source cannot be rewarded");wrong.discard();
            int before=CreaturePersonality.familiarity(source,player);var uuid=source.getUUID();if(absorbed)source.discard();
            check(InvoluntaryTransfurNegotiation.forgive(player,absorbed?null:source),"forgive valid claim: "+absorbed);
            check(ProcessTransfur.getPlayerTransfurVariantSafe(player).orElseThrow().getParent()==variant,"forgive retains transformed body");
            check(InvoluntaryTransfurNegotiation.view(player).isEmpty(),"negotiation view retired");
            check(!InvoluntaryTransfurNegotiation.forgive(player,absorbed?null:source),"repeat forgive rejected");
            if(absorbed){
                var data=data(player);check(data.getBoolean("Forgiven")&&data.contains("SourceSnapshot"),"absorbed individual retained only for later separation");
                var recovered=variant.getEntityType().create(level);recovered.load(data.getCompound("SourceSnapshot"));recovered.setUUID(uuid);
                check(CreaturePersonality.familiarity(recovered,player)==before+8,"absorbed relationship reward persists in snapshot");recovered.discard();
                var saved=player.saveWithoutId(new CompoundTag());var loaded=player(server,player.getUUID());loaded.load(saved);InvoluntaryTransfurNegotiation.onPlayerReady(loaded);
                check(!InvoluntaryTransfurNegotiation.canNegotiateAbsorption(loaded)&&InvoluntaryTransfurNegotiation.view(loaded).isEmpty(),"forgiveness survives login");loaded.discard();
                InvoluntaryTransfurNegotiation.onPlayerReady(player);InvoluntaryTransfurNegotiation.tickPlayer(player);
                check(!InvoluntaryTransfurNegotiation.canNegotiateAbsorption(player),"tick cannot reactivate forgiven claim");
                if(addon)syringe.finishUsingItem(new ItemStack(syringe),level,player);
                else {ProcessTransfur.removePlayerTransfurVariant(player);InvoluntaryTransfurNegotiation.onExternalUntransfur(player);data(player).putLong("ExternalReleaseCheckAt",0);}
                InvoluntaryTransfurNegotiation.tickPlayer(player);
                var released=level.getEntity(CreatureMorphAliasData.get(server).resolveAlias(uuid));check(released instanceof ChangedEntity,"external reversal restores swallowed individual");
                check(CreaturePersonality.familiarity((ChangedEntity)released,player)==before+8,"released individual keeps forgiveness reward");
                check(released.getPersistentData().getBoolean("CanopyOriginal")&&released.getCustomName().getString().equals("OriginalCanopySource"),"separator preserves swallowed identity");
                if(addon){syringe.finishUsingItem(new ItemStack(syringe),level,player);InvoluntaryTransfurNegotiation.tickPlayer(player);check(level.getEntitiesOfClass(ChangedEntity.class,player.getBoundingBox().inflate(16)).size()==1,"split and following tick never duplicate the swallowed creature");}
                released.discard();
            }else {
                check(CreaturePersonality.familiarity(source,player)==before+8,"ordinary source receives exactly one reward");
                check(!InvoluntaryTransfurNegotiation.canNegotiate(player,source),"ordinary claim canceled");
                if(addon){
                    var bounds=player.getBoundingBox().inflate(16);syringe.finishUsingItem(new ItemStack(syringe),level,player);InvoluntaryTransfurNegotiation.tickPlayer(player);
                    var fresh=level.getEntitiesOfClass(ChangedEntity.class,bounds,m->m!=source);
                    check(fresh.size()==1&&!ProcessTransfur.isPlayerTransfurred(player),"assimilation syringe creates exactly one fresh creature");
                    check(!fresh.get(0).getPersistentData().getBoolean("CanopyOriginal")&&fresh.get(0).getCustomName()==null,"assimilation split does not inherit original source identity");
                    check(CreaturePersonality.familiarity(fresh.get(0),player)!=before+8&&source.isAlive(),"original assimilator and its relationship remain separate");fresh.forEach(ChangedEntity::discard);
                }source.discard();
            }
        }} finally {InvoluntaryTransfurNegotiation.abandonForSecondaryTransfur(player);ProcessTransfur.removePlayerTransfurVariant(player);player.discard();net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.takeoverEnabled.set(takeover);level.getGameRules().getRule(ChangedGameRules.RULE_KEEP_BRAIN).set(keep,server);}
    }
    private static CompoundTag data(ServerPlayer player){return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(ROOT);}
    private static void completedForgiveness(MinecraftServer server){
        var player=player(server);var level=server.overworld();var source=ChangedEntities.PURE_WHITE_LATEX_WOLF_PUP.get().create(level);var variant=ChangedTransfurVariants.WHITE_LATEX_WOLF_MALE.get();
        boolean keep=level.getGameRules().getBoolean(ChangedGameRules.RULE_KEEP_BRAIN),takeover=net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.takeoverEnabled.get();
        try{
            level.getGameRules().getRule(ChangedGameRules.RULE_KEEP_BRAIN).set(true,server);net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.takeoverEnabled.set(false);
            player.setPos(1001,120,1000);player.setOnGround(true);source.setPos(player.position());source.setCustomName(net.minecraft.network.chat.Component.literal("CompletedFriend"));
            var event=new TransfurEvents.LatexAssimilationDecisionEvent(player,LatexAssimilationDecision.strong(LatexAssimilationDecision.Method.ABSORPTION,variant,TransfurContext.npcLatexAttack(source),100F));
            InvoluntaryTransfurNegotiation.observeDecision(event);ProcessTransfur.setPlayerTransfurVariant(player,variant,null,1,false);event.getDecision().postTransfurListener().accept(IAbstractChangedEntity.forPlayer(player));source.discard();
            check(data(player).getBoolean("SpecialCompletion")&&InvoluntaryTransfurNegotiation.forgive(player,null),"completion absorption also allows forgiveness");
            var uuid=data(player).getUUID("SourceUuid");check(InvoluntaryTransfurNegotiation.separateAbsorption(player),"completed individual separates without Addon dependency");
            var released=level.getEntity(CreatureMorphAliasData.get(server).resolveAlias(uuid));
            check(released instanceof ChangedEntity&&released.getType()==variant.getEntityType(),"completion retains its completed body");
            check(CreaturePersonality.familiarity((ChangedEntity)released,player)==8&&released.getCustomName().getString().equals("CompletedFriend"),"completed individual keeps forgiveness and identity");released.discard();
        }finally{InvoluntaryTransfurNegotiation.abandonForSecondaryTransfur(player);ProcessTransfur.removePlayerTransfurVariant(player);player.discard();source.discard();level.getGameRules().getRule(ChangedGameRules.RULE_KEEP_BRAIN).set(keep,server);net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.takeoverEnabled.set(takeover);}
    }
    private static ServerPlayer player(MinecraftServer server){return player(server,UUID.randomUUID());}
    private static ServerPlayer player(MinecraftServer server,UUID id){
        var player=new ServerPlayer(server,server.overworld(),new com.mojang.authlib.GameProfile(id,"CanopyTest"));
        player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(server,new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player);
        var identity=new net.parkabird.changedsynergy.origin.NativeOriginData.Identity();identity.state="human";net.parkabird.changedsynergy.origin.NativeOriginData.get(server).players.put(id,identity);return player;
    }
    private static void activate(net.minecraft.server.level.ServerLevel level,BlockPos pos)throws Exception{
        level.getChunk(pos);level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);
        var field=Arrays.stream(net.minecraft.server.level.ServerLevel.class.getDeclaredFields()).filter(f->net.minecraft.world.level.entity.PersistentEntitySectionManager.class.isAssignableFrom(f.getType())).findFirst().orElseThrow();field.setAccessible(true);
        var manager=(net.minecraft.world.level.entity.PersistentEntitySectionManager<?>)field.get(level);
        manager.updateChunkStatus(new net.minecraft.world.level.ChunkPos(pos),net.minecraft.server.level.FullChunkStatus.ENTITY_TICKING);manager.tick();
    }
    private static void treehouse(MinecraftServer server)throws Exception{
        var level=server.overworld();var template=server.getStructureManager().getOrCreate(ResourceLocation.parse("changed_synergy:forest_treehouse"));int index=0;
        for(var rotation:Rotation.values()){
            var origin=new BlockPos(2000+index++*128,100,2000);var settings=new StructurePlaceSettings().setRotation(rotation);var box=template.getBoundingBox(settings,origin);
            for(int x=box.minX()>>4;x<=box.maxX()>>4;x++)for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++)activate(level,new BlockPos(x*16,100,z*16));
            check(template.placeInWorld(level,origin,origin,settings,net.minecraft.util.RandomSource.create(3),2),"treehouse placement");
            var residents=level.getEntitiesOfClass(ChangedEntity.class,AABB.of(box));check(residents.size()==7,"seven camp residents");
            var fixed=residents.stream().filter(m->m.getPersistentData().getCompound(StructureResidentGoal.DATA).getBoolean("Character")).toList();
            check(fixed.size()==1&&fixed.get(0) instanceof net.parkabird.changedsynergy.entity.LatexSilkMoth,"canopy character is Silk Moth");
            var moth=fixed.get(0);var data=moth.getPersistentData().getCompound(StructureResidentGoal.DATA);
            check(data.getBoolean("Resolved")&&data.getBoolean("Camp")&&data.getBoolean("OutfitApplied"),"moth retains camp, boundary and one-time outfit setup");
            check(AccessorySlots.isWearing(moth,s->s.is(ChangedSynergyItems.FOREST_CANOPY_OUTFIT.get())),"canopy moth wears Canopy Outfit");
            check(level.noCollision(moth),"new resident fits original canopy position");
            check(residents.stream().filter(m->m instanceof net.parkabird.changedsynergy.entity.LatexSilkMoth).count()==2,"ground moth retained");
            var saved=moth.saveWithoutId(new CompoundTag());var clone=(ChangedEntity)moth.getType().create(level);clone.load(saved);StructureResidentGoal.install(clone);
            check(AccessorySlots.isWearing(clone,s->s.is(ChangedSynergyItems.FOREST_CANOPY_OUTFIT.get()))&&clone.getPersistentData().getCompound(StructureResidentGoal.DATA).equals(data),"canopy identity and outfit survive reload");clone.discard();residents.forEach(m->{m.setNoAi(true);m.discard();});
        }
    }
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
}
