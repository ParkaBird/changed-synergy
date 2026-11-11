package net.parkabird.changedsynergy.regression;

import java.util.*;
import net.ltxprogrammer.changed.data.*;
import net.ltxprogrammer.changed.entity.*;
import net.ltxprogrammer.changed.init.*;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.block.PerchBlock;
import net.parkabird.changedsynergy.init.*;
import net.parkabird.changedsynergy.item.*;
import net.parkabird.changedsynergy.origin.NativeOriginCatalog;

@Mod.EventBusSubscriber(modid="changed_synergy",value=Dist.DEDICATED_SERVER)
public final class EagleOwlServerRegression {
    private static int checks;
    @SubscribeEvent public static void run(ServerStartedEvent event){var server=event.getServer();try{
        ChangedSynergyConfig.SPEC.setConfig(com.electronwill.nightconfig.core.CommentedConfig.inMemory());
        if(Boolean.getBoolean("changed_synergy.rangerOnly")){CanopyServerChecks.clothing(server);return;}
        MuntjacFormChecks.run(server);
        var level=server.overworld();var owl=ChangedSynergyEntities.LATEX_EAGLE_OWL.get().create(level);var crow=ChangedEntities.LATEX_CROW.get().create(level);
        owl.moveTo(500,180,500);level.getChunk(owl.blockPosition());level.addFreshEntity(owl);owl.setNoAi(true);
        var form=ChangedSynergyTransfurVariants.LATEX_EAGLE_OWL.get();
        check(owl.getMaxHealth()>crow.getMaxHealth()&&owl.getMaxHealth()<=crow.getMaxHealth()*1.15,"modest Crow health increase");
        check(owl.getAttributeValue(Attributes.MOVEMENT_SPEED)>crow.getAttributeValue(Attributes.MOVEMENT_SPEED),"modest Crow movement increase");
        check(form.extraJumpCharges==4&&form.canGlide&&form.visionType==VisionType.NIGHT_VISION,"native Crow flight and snow-leopard night vision");
        var originalFlap=ChangedSynergyAbilities.wingFlap();
        check((originalFlap!=null)==net.minecraftforge.fml.ModList.get().isLoaded("changed_addon"),"shared Wing Flap is optional and belongs to Addon Plus");
        var retiredFlap=ChangedRegistry.ABILITY.get().getValue(ResourceLocation.parse("changed_synergy:wing_flap"));
        check(retiredFlap==null||retiredFlap==originalFlap,"retired ID is absent or aliases the original flap during old-world migration");
        if(originalFlap!=null)check(form.abilities.stream().filter(f->f.apply(owl.getType())==originalFlap).count()==1,"owl receives the same single Wing Flap as other flying forms");
        check(owl.getSelfVariant()==form&&owl.getMaxHeadYRot()==135,"own form and 270-degree head range");
        check(LightFactionGroup.of(owl).equals("mountain"),"stable mountain faction");
        check(NativeOriginCatalog.find(level.registryAccess(),form.getFormId())!=null,"playable origin");
        for(var biome:server.registryAccess().registryOrThrow(Registries.BIOME).holders().toList()){
            boolean present=biome.value().getMobSettings().getMobs(MobCategory.MONSTER).unwrap().stream().anyMatch(s->s.type==owl.getType());
            boolean expected=biome.is(net.minecraft.tags.BiomeTags.IS_MOUNTAIN)||Set.of("grove","snowy_slopes","stony_peaks","jagged_peaks","frozen_peaks").contains(biome.key().location().getPath());
            check(present==expected,"mountain-only spawning: "+biome.key().location());
        }
        check(server.getRecipeManager().byKey(ResourceLocation.parse("changed_synergy:form_latex_eagle_owl")).isPresent(),"transfur recipe");
        var outfit=ChangedSynergyItems.EAGLE_OWL_AVIATOR_OUTFIT.get();var trail=ChangedSynergyItems.HYENA_TRAILCLOTH_OUTFIT.get();
        check(!net.minecraft.world.item.ArmorItem.class.isInstance(outfit),"native clothing rather than armor");
        for(var type:List.of(owl.getType(),ChangedEntities.WHITE_LATEX_WOLF_MALE.get(),ChangedEntities.LATEX_SNAKE.get(),ChangedEntities.LATEX_MERMAID_SHARK.get(),ChangedEntities.MILK_PUDDING.get(),ChangedEntities.DARK_LATEX_WOLF_PUP.get(),ChangedEntities.PURE_WHITE_LATEX_WOLF_PUP.get())){
            var wearer=(ChangedEntity)type.create(level);AccessoryEntities.INSTANCE.forceReloadAccessories(wearer);var slots=AccessorySlots.getForEntity(wearer).orElseThrow();
            check(EagleOwlAviatorItem.fits(wearer),"outfit accepts requested anatomy: "+type);
            if(EagleOwlAviatorItem.juvenile(wearer)){
                check(slots.quickMoveStack(new ItemStack(outfit)),"juvenile can equip goggles through its native slots: "+type);
                check(!ChangedAccessorySlots.BODY.get().canHoldItem(new ItemStack(outfit),wearer),"juvenile cannot equip the full suit");
            }else for(var slot:List.of(ChangedAccessorySlots.BODY.get(),ChangedAccessorySlots.FULL_BODY.get())){
                check(slots.hasSlot(slot)&&slots.isItemAllowedWithOthers(slot,new ItemStack(outfit)),"inner and outer outfit slots: "+type+slot);
                slots.setItem(slot,new ItemStack(outfit));
                var stack=slots.getItem(slot).orElseThrow();outfit.accessoryInteract(new AccessorySlotContext<>(wearer,slot,stack));check(EagleOwlAviatorItem.lowered(stack),"goggles lower via native style action");
                outfit.accessoryInteract(new AccessorySlotContext<>(wearer,slot,stack));check(!EagleOwlAviatorItem.lowered(stack),"goggles raise via native style action");
                check(!slots.isItemAllowedWithOthers(slot==ChangedAccessorySlots.BODY.get()?ChangedAccessorySlots.FULL_BODY.get():ChangedAccessorySlots.BODY.get(),new ItemStack(outfit)),"duplicate outfit cannot occupy both layers");
                if(HyenaTrailclothItem.hasLowerSection(wearer))check(!slots.isItemAllowedWithOthers(ChangedAccessorySlots.LEGS.get(),new ItemStack(ChangedItems.BLACK_PANTS.get())),"leg-slot conflict retained");
                var save=new CompoundTag();wearer.saveWithoutId(save);var clone=(ChangedEntity)type.create(level);clone.load(save);check(AccessorySlots.isWearing(clone,s->s.is(outfit)),"outfit saves without duplication");clone.discard();
                slots.setItem(slot,ItemStack.EMPTY);
            }
            if(HyenaTrailclothItem.fits(wearer)){
                check(slots.isItemAllowedWithOthers(ChangedAccessorySlots.FULL_BODY.get(),new ItemStack(trail)),"Trailcloth outer slot");
                slots.setItem(ChangedAccessorySlots.BODY.get(),new ItemStack(trail));check(slots.isItemAllowedWithOthers(ChangedAccessorySlots.FULL_BODY.get(),new ItemStack(outfit)),"different outfits can layer");
            }
            wearer.discard();
        }
        double chance=ChangedSynergyConfig.COMMON.eagleOwlOutfitSpawnChance.get();try{
            ChangedSynergyConfig.COMMON.eagleOwlOutfitSpawnChance.set(1D);
            for(var reason:List.of(MobSpawnType.NATURAL,MobSpawnType.CHUNK_GENERATION,MobSpawnType.STRUCTURE,MobSpawnType.COMMAND,MobSpawnType.SPAWN_EGG)){
                var fresh=ChangedSynergyEntities.LATEX_EAGLE_OWL.get().create(level);ForgeEventFactory.onFinalizeSpawn(fresh,level,level.getCurrentDifficultyAt(owl.blockPosition()),reason,null,null);
                check(AccessorySlots.isWearing(fresh,s->s.is(outfit))==(reason!=MobSpawnType.COMMAND&&reason!=MobSpawnType.SPAWN_EGG),"outfit spawn reason: "+reason);fresh.discard();
            }
        }finally{ChangedSynergyConfig.COMMON.eagleOwlOutfitSpawnChance.set(chance);}
        owl.setOnGround(false);var target=EntityType.ZOMBIE.create(level);target.moveTo(501.3,179,500);
        check(EagleOwlTraits.airborne(owl)&&EagleOwlTraits.canReach(owl,target),"larger downward talon acquisition box");
        level.setBlock(new BlockPos(501,180,500),Blocks.STONE.defaultBlockState(),3);check(!EagleOwlTraits.canReach(owl,target),"talons cannot acquire through a wall");level.setBlock(new BlockPos(501,180,500),Blocks.AIR.defaultBlockState(),3);
        var human=new net.minecraft.server.level.ServerPlayer(server,level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"OwlTalonTarget"));
        human.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(server,new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),human);
        human.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);human.setPos(501,179,500);owl.setTarget(human);var goal=new EagleOwlAirGrabGoal(owl);
        boolean allowed=net.ltxprogrammer.changed.Changed.config.server.isGrabEnabled.get();try{
            net.ltxprogrammer.changed.Changed.config.server.isGrabEnabled.set(true);
            // Addon may reject an unprovoked human target; talons must not bypass that decision.
            check(goal.canUse()==(owl.getTarget()==human),"NPC talons acquire existing eligible targets and respect target-policy rejection");
            net.ltxprogrammer.changed.Changed.config.server.isGrabEnabled.set(false);check(!goal.canUse(),"NPC talons respect the native server grab switch");
            net.ltxprogrammer.changed.Changed.config.server.isGrabEnabled.set(true);human.setGameMode(net.minecraft.world.level.GameType.CREATIVE);check(!goal.canUse(),"NPC talons cannot capture creative players");
        }finally{net.ltxprogrammer.changed.Changed.config.server.isGrabEnabled.set(allowed);owl.setTarget(null);human.discard();}
        owl.setOnGround(true);check(!EagleOwlTraits.airborne(owl),"landing restores ordinary acquisition");
        contacts(server);EagleOwlReleaseChecks.run(server);perches(level);EagleOwlPerchChecks.verify(server);owl.discard();crow.discard();
        CanopyServerChecks.run(server);
        NativeBondInvitationRegression.run(server);
        EagleOwlBondChecks.run(server);
        System.out.println("EAGLE_OWL_SERVER_PASS: "+checks+" species, flight, vision, slots, styles, anatomy, spawns, reach, perches and transformed-player bonds");
    }catch(Throwable error){System.err.println("EAGLE_OWL_SERVER_FAIL");error.printStackTrace();}finally{server.halt(false);}}
    private static void perches(net.minecraft.server.level.ServerLevel level){
        for(var block:List.of(ChangedSynergyBlocks.EAGLE_OWL_PERCH_STAND.get(),ChangedSynergyBlocks.EAGLE_OWL_PERCH_RAIL.get()))for(var facing:Direction.Plane.HORIZONTAL){
            var pos=new BlockPos(8,100,8);var second=block==ChangedSynergyBlocks.EAGLE_OWL_PERCH_STAND.get()?pos.above():pos.relative(facing.getClockWise());
            level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);level.setBlock(second.below(),Blocks.STONE.defaultBlockState(),3);
            var state=block.defaultBlockState().setValue(PerchBlock.FACING,facing);level.setBlock(pos,state,3);block.setPlacedBy(level,pos,state,null,ItemStack.EMPTY);
            check(level.getBlockState(second).is(block)&&level.getBlockState(second).getValue(PerchBlock.HALF)==DoubleBlockHalf.UPPER,"two-block placement: "+block+facing);
            level.destroyBlock(second,false);check(level.getBlockState(pos).isAir(),"breaking second half removes first");
            level.setBlock(pos,state,3);block.setPlacedBy(level,pos,state,null,ItemStack.EMPTY);level.destroyBlock(pos,false);check(level.getBlockState(second).isAir(),"breaking first half removes second");
            var player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            for(var broken:List.of(pos,second)){
                level.setBlock(pos,state,3);block.setPlacedBy(level,pos,state,null,ItemStack.EMPTY);
                var before=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(4));before.forEach(Entity::discard);
                check(player.gameMode.destroyBlock(broken),"native player mining succeeds");
                var drops=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(4));
                int count=drops.stream().filter(e->e.getItem().is(block.asItem())).mapToInt(e->e.getItem().getCount()).sum();
                check(count==1,"one item from either perch half: "+block+" "+facing+" "+broken+", actual="+count);drops.forEach(Entity::discard);
            }
        }
    }
    private static void contacts(net.minecraft.server.MinecraftServer server){
        var level=server.overworld();var owner=new net.minecraft.server.level.ServerPlayer(server,level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"TalonContacts"));
        owner.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(server,new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),owner);
        owner.setPos(.5,100,.5);owner.setOnGround(false);var form=ProcessTransfur.setPlayerTransfurVariant(owner,ChangedSynergyTransfurVariants.LATEX_EAGLE_OWL.get(),null,1,false);var grab=form.getAbilityInstance(ChangedAbilities.GRAB_ENTITY_ABILITY.get());
        var cow=EntityType.COW.create(level);var zombie=EntityType.ZOMBIE.create(level);var latex=ChangedSynergyEntities.LATEX_EAGLE_OWL.get().create(level);
        try{
            cow.setPos(.6,100,.5);zombie.setPos(1.4,100,.5);latex.setPos(.7,100,.5);for(var e:List.of(cow,zombie,latex)){level.getChunk(e.blockPosition());level.addFreshEntity(e);}
            check(!EagleOwlTraits.validContact(grab,cow)&&!EagleOwlTraits.validContact(grab,latex),"normal contact cannot acquire arbitrary animals or latex creatures");
            check(EagleOwlTraits.validContact(grab,zombie)&&EagleOwlTraits.contactTarget(grab)==zombie,"normal contact chooses eligible humanoid without mouse aiming");
            PlayerHugService.action(owner,net.parkabird.changedsynergy.network.PlayerHugActionPacket.TOGGLE,-1);
            check(PlayerHugService.mode(grab)&&!EagleOwlTraits.validContact(grab,cow)&&EagleOwlTraits.validContact(grab,latex),"friendly contact retains exactly its existing creature permission");
            check(EagleOwlTraits.contactTarget(grab)==latex,"friendly contact ignores the closer ineligible cow");
            owner.setOnGround(true);check(!EagleOwlTraits.airborne(owner),"landing stops contact acquisition mode");
        }finally{cow.discard();zombie.discard();latex.discard();ProcessTransfur.removePlayerTransfurVariant(owner);owner.discard();}
    }
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
}
