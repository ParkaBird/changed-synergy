package net.parkabird.changedsynergy.ai;

import java.util.EnumSet;
import net.ltxprogrammer.changed.ability.GrabEntityAbility;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import net.parkabird.changedsynergy.entity.SpottedHyenaDiet;
import net.parkabird.changedsynergy.world.inventory.BondedInventoryService;

/** A real offhand meal; emergency rescue and critical self-recovery wrapping take priority. */
@Mod.EventBusSubscriber(modid=ChangedSynergyMod.MOD_ID)
public final class CompanionAutoEatGoal extends Goal {
    private static final String MEAL="ChangedSynergyCompanionMeal", NEXT="ChangedSynergyNextCompanionMeal";
    private final ChangedEntity mob;
    private int slot=-1, remaining;
    private long nextCheck;

    public CompanionAutoEatGoal(ChangedEntity mob) {
        this.mob=mob;
        setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));
    }
    private boolean available() {
        return ChangedSynergyConfig.COMMON.companionAutoEat.get() && mob.level() instanceof ServerLevel
                && mob.isAlive() && !mob.isNoAi() && !mob.isSleeping() && !mob.isPassenger() && !mob.isVehicle()
                && mob.getUnderlyingPlayer()==null && !TrueTransfurCompat.isInstinctBody(mob)
                && CreatureSocialProfile.allowsPersonalRelationship(mob) && !TakeoverService.carrying(mob)
                && !CreatureGrabService.isGrabberBusy(mob) && GrabEntityAbility.getGrabberSafe(mob).isEmpty()
                && (BondedSuitService.ability(mob)==null || BondedSuitService.ability(mob).grabbedEntity==null)
                && !CreaturePeerInteractionService.active(mob) && !ProvisionerTradeService.isTrading(mob)
                && (LatexSocialMemory.hasActiveBond(mob) || CreaturePersonality.establishedRelationshipPlayerUuids(mob)
                    .stream().anyMatch(player -> CreaturePersonality.hasTrustedRelationship(mob,player)));
    }
    @Override public boolean canUse() {
        long now=mob.level().getGameTime();
        if (now<nextCheck) return false;
        nextCheck=now+10;
        if (!available() || mob.isUsingItem() || now<mob.getPersistentData().getLong(NEXT)
                || mob.getHealth()>mob.getMaxHealth()*(mob.getTarget()==null?0.6F:0.35F)) return false;
        Container inventory=inventory(mob);
        float healthRatio=mob.getHealth()/mob.getMaxHealth();
        float missingHealth=mob.getMaxHealth()-mob.getHealth();
        float best=Float.NEGATIVE_INFINITY;
        slot=-1;
        for (int i=0;i<Math.min(24,inventory.getContainerSize());i++) {
            var food=inventory.getItem(i);
            var properties=SpottedHyenaDiet.foodProperties(food,mob);
            if (food.isEmpty() || !(food.isEdible() || SpottedHyenaDiet.isSpeciesFood(food,mob)) || properties==null || properties.getNutrition()<=0
                    || food.is(Items.SUSPICIOUS_STEW) || food.is(Items.CHORUS_FRUIT) || RelationshipFavorService.isGoldenOrange(food)
                    || properties.getEffects().stream().anyMatch(effect -> effect.getSecond()>0
                        && effect.getFirst()!=null && effect.getFirst().getEffect().getCategory()==MobEffectCategory.HARMFUL)
                    || food.is(Items.GOLDEN_APPLE) && healthRatio>0.35F
                    || food.is(Items.ENCHANTED_GOLDEN_APPLE) && healthRatio>0.2F) continue;
            float recovery=healing(properties),useful=Math.min(missingHealth,recovery);
            float score=useful-(recovery-useful)*healthRatio;
            // Reserve precious food; its weight rises as the injury becomes critical.
            if (food.is(Items.GOLDEN_APPLE)) score*=0.25F+0.75F*(1-healthRatio/0.35F);
            if (food.is(Items.ENCHANTED_GOLDEN_APPLE)) score*=0.1F+0.4F*(1-healthRatio/0.2F);
            if (score>best) {best=score;slot=i;}
        }
        return slot>=0;
    }
    @Override public void start() {
        remaining = beginEating(mob,inventory(mob),slot);
    }
    static int beginEating(ChangedEntity mob, Container inventory, int slot) {
        if (mob.isUsingItem() || active(mob)) return 0;
        ItemStack food=inventory.removeItem(slot,1);
        inventory.setChanged();
        if (food.isEmpty()) return 0;
        var meal=new CompoundTag();
        meal.put("Food",food.save(new CompoundTag()));
        meal.put("Offhand",mob.getOffhandItem().save(new CompoundTag()));
        mob.getPersistentData().put(MEAL,meal);
        mob.setItemInHand(InteractionHand.OFF_HAND,food);
        mob.getNavigation().stop();
        mob.startUsingItem(InteractionHand.OFF_HAND);
        return food.getUseDuration()+20;
    }
    @Override public boolean canContinueToUse() {
        var meal=mob.getPersistentData().getCompound(MEAL);
        return remaining>0 && available() && !meal.getBoolean("Finished") && mob.isUsingItem()
                && mob.getUsedItemHand()==InteractionHand.OFF_HAND
                && ItemStack.isSameItemSameTags(mob.getOffhandItem(),ItemStack.of(meal.getCompound("Food")));
    }
    @Override public void tick() {remaining--;mob.getNavigation().stop();}
    @Override public boolean requiresUpdateEveryTick() {return true;}
    @Override public void stop() {
        cleanup(mob);
        mob.getPersistentData().putLong(NEXT,mob.level().getGameTime()+60);
    }
    public static boolean active(ChangedEntity mob) {return mob.getPersistentData().contains(MEAL);}
    @SubscribeEvent public static void finished(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ChangedEntity mob) || !(mob.level() instanceof ServerLevel) || !active(mob)) return;
        var meal=mob.getPersistentData().getCompound(MEAL);
        if (meal.getBoolean("Finished") || !ItemStack.isSameItemSameTags(event.getItem(),ItemStack.of(meal.getCompound("Food")))) return;
        meal.putBoolean("Finished",true);
        var properties=SpottedHyenaDiet.foodProperties(event.getItem(),mob);
        if (properties!=null) mob.heal(healing(properties));
    }
    static float healing(FoodProperties properties) {
        return properties.getNutrition()*(0.5F+properties.getSaturationModifier());
    }
    /** Restore after vanilla installs the food's actual bowl/bottle/remaining stack. */
    public static void cleanup(ChangedEntity mob) {
        if (!active(mob)) return;
        var meal=mob.getPersistentData().getCompound(MEAL);
        ItemStack saved=ItemStack.of(meal.getCompound("Offhand")), held=mob.getOffhandItem();
        boolean ownHand=held.isEmpty() || meal.getBoolean("Finished")
                || ItemStack.isSameItemSameTags(held,ItemStack.of(meal.getCompound("Food")));
        if (mob.isUsingItem() && mob.getUsedItemHand()==InteractionHand.OFF_HAND) mob.stopUsingItem();
        mob.getPersistentData().remove(MEAL);
        if (ownHand) {mob.setItemInHand(InteractionHand.OFF_HAND,saved);PlayerOutpostMealGoal.returnToInventory(mob,held);}
        else PlayerOutpostMealGoal.returnToInventory(mob,saved);
    }
    private static Container inventory(ChangedEntity mob) {return BondedInventoryService.inventory(mob,LatexSocialMemory.getPetOwner(mob));}
    /** The two bodies may use different inventory backends after an interrupted meal. */
    public static void transferMealInventory(ChangedEntity previous,ChangedEntity replacement) {
        Container source=inventory(previous),target=inventory(replacement);
        ItemStack originalHand=previous.getMainHandItem(),newHand=replacement.getMainHandItem();
        boolean targetHandInStorage=false;
        for(int i=0;i<Math.min(24,target.getContainerSize());i++)
            targetHandInStorage|=!newHand.isEmpty() && target.getItem(i)==newHand;
        for(int i=0;i<Math.min(24,Math.min(source.getContainerSize(),target.getContainerSize()));i++) {
            ItemStack item=source.getItem(i);
            // Native pets expose the selected storage slot as their hand; fallback bodies have a separate hand.
            boolean alreadyEquipped=!targetHandInStorage && !originalHand.isEmpty() && item==originalHand
                    && ItemStack.matches(originalHand,newHand);
            target.setItem(i,alreadyEquipped?ItemStack.EMPTY:item.copy());
        }
        target.setChanged();
    }
    @SubscribeEvent public static void joined(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ChangedEntity mob) cleanup(mob);
    }
    @SubscribeEvent public static void unloaded(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ChangedEntity mob) cleanup(mob);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void dying(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide && event.getEntity() instanceof ChangedEntity mob) cleanup(mob);
    }
}
