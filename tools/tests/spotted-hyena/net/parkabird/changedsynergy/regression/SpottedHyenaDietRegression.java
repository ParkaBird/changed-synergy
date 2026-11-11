package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.entity.SpottedHyenaDiet;
import net.parkabird.changedsynergy.init.*;
import net.parkabird.changedsynergy.world.inventory.BondedInventoryService;

final class SpottedHyenaDietRegression {
    private static int checks;
    static void run(MinecraftServer server) throws Exception {
        var owner = new ServerPlayer(server, server.overworld(), new GameProfile(UUID.randomUUID(), "BoneEater"));
        owner.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), owner);
        owner.moveTo(96, 180, 96);
        var players = (java.util.Map<UUID, ServerPlayer>)net.minecraftforge.fml.util.ObfuscationReflectionHelper.getPrivateValue(
                net.minecraft.server.players.PlayerList.class, server.getPlayerList(), "f_11197_");
        players.put(owner.getUUID(), owner); server.overworld().addNewPlayer(owner);
        var update = LivingEntity.class.getDeclaredMethod("m_21329_"); update.setAccessible(true);
        boolean rescue = ChangedSynergyConfig.COMMON.bondedEmergencyRescue.get();
        ChangedSynergyConfig.COMMON.bondedEmergencyRescue.set(false);
        try {
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE, 2));
            check(!Items.BONE.use(owner.level(), owner, InteractionHand.MAIN_HAND).getResult().consumesAction()
                    && !owner.isUsingItem(), "human cannot start chewing bones");
            for (var type : List.of(ChangedSynergyEntities.LATEX_SPOTTED_HYENA_MALE.get(), ChangedSynergyEntities.LATEX_SPOTTED_HYENA_FEMALE.get())) {
                var mob = type.create(owner.level()); mob.moveTo(96, 180, 98); mob.setNoGravity(true);
                server.overworld().addFreshEntity(mob); mob.goalSelector.removeAllGoals(g -> true); mob.targetSelector.removeAllGoals(g -> true);
                try {
                    var seen = new HashSet<MobEffect>(); mob.getRandom().setSeed(47241);
                    for (int sample = 0; sample < 64; sample++) {
                        mob.removeAllEffects(); var bone = new ItemStack(Items.BONE, 2);
                        var result = bone.finishUsingItem(owner.level(), mob);
                        check(result.getCount() == 1 && mob.getActiveEffects().size() == 1, "one bone grants one effect");
                        var effect = mob.getActiveEffects().iterator().next(); seen.add(effect.getEffect());
                        check(effect.getEffect().getCategory() == MobEffectCategory.BENEFICIAL && effect.getAmplifier() == 0
                                && effect.getDuration() == 160, "only short level-I positive effects");
                    }
                    check(seen.size() == 5, "random effect selection varies");
                    for(var ingredient : List.of(Items.LEATHER,Items.SPIDER_EYE,Items.PUFFERFISH,Items.POISONOUS_POTATO,
                            Items.RABBIT_FOOT,Items.SUGAR,Items.GLISTERING_MELON_SLICE,Items.GHAST_TEAR,Items.GOLDEN_CARROT,Items.MAGMA_CREAM,Items.BLAZE_POWDER)) {
                        mob.removeAllEffects();var serving=new ItemStack(ingredient,2);
                        check(SpottedHyenaDiet.isSpeciesFood(serving,mob),"hyena recognizes ingredient: "+ingredient);
                        var result=serving.finishUsingItem(owner.level(),mob);
                        boolean negative=ingredient==Items.SPIDER_EYE||ingredient==Items.POISONOUS_POTATO;
                        check(result.getCount()==1 && mob.getActiveEffects().size()==(negative?0:1),"ingredient consumes one; only positive ingredients grant effects");
                        if(ingredient==Items.LEATHER)check(mob.hasEffect(MobEffects.DAMAGE_RESISTANCE),"leather grants brief resistance");
                        check(mob.getActiveEffects().stream().allMatch(e->e.getEffect().isBeneficial() && e.getAmplifier()==0 && e.getDuration()<=200),"brief beneficial ingredient effect");
                    }
                    mob.removeAllEffects();
                    var rotten=new ItemStack(Items.ROTTEN_FLESH,2);
                    check(SpottedHyenaDiet.foodProperties(rotten,mob).getNutrition()==6,"rotten flesh restores six food points");
                    check(rotten.finishUsingItem(owner.level(),mob).getCount()==1 && !mob.hasEffect(MobEffects.HUNGER),"rotten flesh gives no hunger penalty");
                    var stew=new ItemStack(Items.SUSPICIOUS_STEW);
                    net.minecraft.world.item.SuspiciousStewItem.saveMobEffect(stew,MobEffects.POISON,160);
                    mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.WEAKNESS,200));
                    mob.setItemInHand(InteractionHand.OFF_HAND,stew);mob.startUsingItem(InteractionHand.OFF_HAND);
                    for(int tick=0;tick<32;tick++)update.invoke(mob);
                    check(!mob.hasEffect(MobEffects.POISON) && mob.hasEffect(MobEffects.WEAKNESS),"toxic food is harmless without curing an existing enemy debuff");
                    mob.stopUsingItem();mob.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
                    mob.removeAllEffects(); mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 2));
                    new ItemStack(Items.BONE).finishUsingItem(owner.level(), mob);
                    check(mob.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() == 2, "stronger existing effect retained");
                    mob.removeAllEffects();
                    CreaturePersonality.establishRelationship(mob, owner); CreaturePersonality.setFamiliarity(mob, owner, 25);
                    if (type == ChangedSynergyEntities.LATEX_SPOTTED_HYENA_FEMALE.get()) LatexSocialMemory.addBond(mob, owner);
                    var inventory = BondedInventoryService.inventory(mob, owner); inventory.setItem(2, new ItemStack(Items.BONE, 2));
                    mob.setHealth(2); mob.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
                    var goal = new CompanionAutoEatGoal(mob); check(goal.canUse(), "friend can select stored bone"); goal.start();
                    int ticks = 0; while (goal.canContinueToUse() && ticks++ < 64) { goal.tick(); update.invoke(mob); }
                    goal.stop();
                    inventory = BondedInventoryService.inventory(mob, owner);
                    check(ticks == 32 && inventory.getItem(2).getCount() == 1 && mob.getOffhandItem().is(Items.SHIELD)
                            && mob.getHealth() > 2 && mob.getActiveEffects().size() == 1, "real NPC chew consumes once, heals and restores equipment: ticks="+ticks
                            +", slot="+inventory.getItem(2)+", hand="+mob.getOffhandItem()+", health="+mob.getHealth()+", effects="+mob.getActiveEffects());
                    mob.getPersistentData().remove("ChangedSynergyNextCompanionMeal"); mob.removeAllEffects();
                    goal = new CompanionAutoEatGoal(mob); check(goal.canUse(), "second meal ready"); goal.start(); update.invoke(mob); goal.stop();
                    inventory = BondedInventoryService.inventory(mob, owner);
                    int bones = 0; for (int i = 0; i < 24; i++) if (inventory.getItem(i).is(Items.BONE)) bones += inventory.getItem(i).getCount();
                    check(bones == 1 && mob.getActiveEffects().isEmpty() && mob.getOffhandItem().is(Items.SHIELD), "interrupted NPC chew returns bone without effect");
                    if (type == ChangedSynergyEntities.LATEX_SPOTTED_HYENA_MALE.get()) {
                        BondedSuitService.ensureGrabAbility(mob);
                        var grab = mob.getAbilityInstance(net.ltxprogrammer.changed.init.ChangedAbilities.GRAB_ENTITY_ABILITY.get());
                        check(grab.suitEntity(owner), "human wrapped in real hyena body");
                        ((net.ltxprogrammer.changed.entity.LivingEntityDataExtension)owner).setGrabbedBy(mob);
                        grab.grabbedHasControl = true; owner.getFoodData().setFoodLevel(10);
                        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE, 2));
                        check(SpottedHyenaDiet.canEatBone(owner), "controlling wrapped human receives bone diet");
                        Items.BONE.use(owner.level(), owner, InteractionHand.MAIN_HAND);
                        for (int tick = 0; tick < 32; tick++) update.invoke(owner);
                        check(owner.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1 && owner.getActiveEffects().size() == 1
                                && owner.getFoodData().getFoodLevel() == 12, "wrapped player chews bone through normal item use");
                        owner.removeAllEffects(); grab.grabbedHasControl = false; LatexSocialMemory.endFriendlySuit(mob, owner);
                        check(!SpottedHyenaDiet.canEatBone(owner), "NPC-controlled wrap does not grant chewing control");
                        grab.releaseEntity(false);
                    }
                } finally { mob.discard(); }
            }
            for (var form : List.of(ChangedSynergyTransfurVariants.LATEX_SPOTTED_HYENA_MALE.get(), ChangedSynergyTransfurVariants.LATEX_SPOTTED_HYENA_FEMALE.get())) {
                ProcessTransfur.setPlayerTransfurVariant(owner, form, null, 1, false);
                for (var hand : InteractionHand.values()) {
                    owner.removeAllEffects(); owner.getFoodData().setFoodLevel(10);
                    owner.setItemInHand(hand, new ItemStack(Items.BONE, 2));
                    check(Items.BONE.use(owner.level(), owner, hand).getResult().consumesAction() && owner.isUsingItem(), "hyena can use either hand");
                    for (int tick = 0; tick < 31; tick++) update.invoke(owner);
                    check(owner.getItemInHand(hand).getCount() == 2 && owner.getActiveEffects().isEmpty(), "no early consumption or bonus");
                    update.invoke(owner);
                    check(owner.getItemInHand(hand).getCount() == 1 && owner.getActiveEffects().size() == 1
                            && owner.getFoodData().getFoodLevel() == 12, "player finishes normal timed chewing and gains nutrition");
                    owner.removeAllEffects(); Items.BONE.use(owner.level(), owner, hand); update.invoke(owner); owner.stopUsingItem();
                    check(owner.getItemInHand(hand).getCount() == 1 && owner.getActiveEffects().isEmpty(), "interrupted player chew does not spend bone");
                }
            }
            owner.removeAllEffects(); owner.getFoodData().setFoodLevel(20); owner.getAbilities().instabuild = true;
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE));
            Items.BONE.use(owner.level(), owner, InteractionHand.MAIN_HAND);
            for (int tick = 0; tick < 32; tick++) update.invoke(owner);
            check(owner.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1 && owner.getActiveEffects().size() == 1,
                    "creative and full hunger supported without item loss");
            owner.getAbilities().instabuild = false; owner.removeAllEffects();
            Items.BONE.use(owner.level(), owner, InteractionHand.MAIN_HAND);
            ProcessTransfur.setPlayerTransfurVariant(owner, ChangedSynergyTransfurVariants.LATEX_SILK_MOTH.get(), null, 1, false);
            for (int tick = 0; tick < 32; tick++) update.invoke(owner);
            check(owner.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1 && owner.getActiveEffects().isEmpty(), "changing species cancels bone benefit");
            var moth = ChangedSynergyEntities.LATEX_SILK_MOTH.get().create(owner.level());
            check(!SpottedHyenaDiet.isSpeciesFood(new ItemStack(Items.BONE), moth)
                    && new ItemStack(Items.BONE, 2).finishUsingItem(owner.level(), moth).getCount() == 2, "other species cannot consume bones");
            check(new ItemStack(Items.BONE).getUseAnimation() == UseAnim.EAT && new ItemStack(Items.BONE).getUseDuration() == 32
                    && !new ItemStack(Items.BONE).isEdible(), "native eating animation without global food conversion");
            System.out.println("SPOTTED_HYENA_DIET_PASS: " + checks + " NPC/player timed chewing, effects, survival/creative, interruptions, inventory and species guards");
        } finally {
            ChangedSynergyConfig.COMMON.bondedEmergencyRescue.set(rescue);
            players.remove(owner.getUUID()); owner.discard();
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
}
