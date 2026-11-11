package net.parkabird.changedsynergy.entity;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.ltxprogrammer.changed.ability.GrabEntityAbility;
import net.ltxprogrammer.changed.init.ChangedAbilities;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gameevent.GameEvent;
import net.parkabird.changedsynergy.ai.BondedSuitService;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;

/** Species-specific food; vanilla bones remain inedible to other bodies. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=net.parkabird.changedsynergy.ChangedSynergyMod.MOD_ID)
public final class SpottedHyenaDiet {
    private record BeforeMeal(Item item, List<MobEffectInstance> harmful) {}
    private static final Map<LivingEntity,BeforeMeal> BEFORE_MEAL=new WeakHashMap<>();
    private static final List<MobEffect> EFFECTS = List.of(MobEffects.MOVEMENT_SPEED, MobEffects.DIG_SPEED,
            MobEffects.DAMAGE_BOOST, MobEffects.DAMAGE_RESISTANCE, MobEffects.REGENERATION);
    private record Meal(int nutrition, float saturation, MobEffect effect, int duration) {}
    private static final Map<Item, Meal> MEALS = Map.ofEntries(
            Map.entry(Items.BONE, new Meal(2, .1F, null, 160)),
            Map.entry(Items.LEATHER, new Meal(2, .2F, MobEffects.DAMAGE_RESISTANCE, 100)),
            Map.entry(Items.ROTTEN_FLESH, new Meal(6, .2F, null, 0)),
            Map.entry(Items.SPIDER_EYE, new Meal(2, .2F, null, 0)),
            Map.entry(Items.PUFFERFISH, new Meal(1, .1F, MobEffects.WATER_BREATHING, 160)),
            Map.entry(Items.POISONOUS_POTATO, new Meal(2, .3F, null, 0)),
            Map.entry(Items.RABBIT_FOOT, new Meal(2, .1F, MobEffects.JUMP, 200)),
            Map.entry(Items.SUGAR, new Meal(1, .1F, MobEffects.MOVEMENT_SPEED, 120)),
            Map.entry(Items.GOLDEN_CARROT, new Meal(6, 1.2F, MobEffects.NIGHT_VISION, 200)),
            Map.entry(Items.BLAZE_POWDER, new Meal(1, .1F, MobEffects.DAMAGE_BOOST, 100)),
            Map.entry(Items.MAGMA_CREAM, new Meal(1, .1F, MobEffects.FIRE_RESISTANCE, 160)),
            Map.entry(Items.GLISTERING_MELON_SLICE, new Meal(4, .3F, MobEffects.HEAL, 1)),
            Map.entry(Items.GHAST_TEAR, new Meal(1, .1F, MobEffects.REGENERATION, 160)));

    private SpottedHyenaDiet() {}

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void finishingTick(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Tick event) {
        var user=event.getEntity();
        if(!user.level().isClientSide && event.getDuration()==1 && canEatBone(user) && event.getItem().isEdible())
            BEFORE_MEAL.put(user,new BeforeMeal(event.getItem().getItem(),user.getActiveEffects().stream()
                    .filter(e->e.getEffect().getCategory()==net.minecraft.world.effect.MobEffectCategory.HARMFUL).map(MobEffectInstance::new).toList()));
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void finishedFood(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Finish event) {
        var user=event.getEntity();var before=BEFORE_MEAL.remove(user);
        if(before==null || before.item()!=event.getItem().getItem() || !canEatBone(user))return;
        // Undo only food's newly applied penalties; an existing poison or enemy debuff is preserved.
        for(var effect:List.copyOf(user.getActiveEffects()))if(effect.getEffect().getCategory()==net.minecraft.world.effect.MobEffectCategory.HARMFUL) {
            var original=before.harmful().stream().filter(e->e.getEffect()==effect.getEffect()).findFirst().orElse(null);
            if(original==null)user.removeEffect(effect.getEffect());
            else if(effect.getAmplifier()>original.getAmplifier() || effect.getDuration()>original.getDuration()) {
                user.removeEffect(effect.getEffect());user.addEffect(new MobEffectInstance(original));
            }
        }
    }

    public static boolean canEatBone(LivingEntity user) {
        if (user == null || !user.isAlive()) return false;
        if (user instanceof LatexSpottedHyena) return true;
        if (!(user instanceof Player player) || player.isSpectator()) return false;
        var grabber = GrabEntityAbility.getGrabber(player);
        if (grabber != null) {
            var grab = grabber.getAbilityInstance(ChangedAbilities.GRAB_ENTITY_ABILITY.get());
            return grabber.getChangedEntity() instanceof LatexSpottedHyena && grab != null
                    && grab.suited && grab.grabbedEntity == player
                    && (grab.grabbedHasControl || BondedSuitService.getSuitController(grabber.getChangedEntity()) == player);
        }
        var form = ProcessTransfur.getPlayerTransfurVariant(player);
        return form != null && form.getTransfurProgression(1) >= 1
                && form.getChangedEntity() instanceof LatexSpottedHyena;
    }

    public static boolean isSpeciesFood(ItemStack stack, LivingEntity user) {
        return MEALS.containsKey(stack.getItem()) && canEatBone(user);
    }

    public static boolean isSpeciesFoodItem(ItemStack stack) { return MEALS.containsKey(stack.getItem()); }

    public static FoodProperties foodProperties(ItemStack stack, LivingEntity user) {
        if (!isSpeciesFood(stack, user)) return stack.getFoodProperties(user);
        var meal = MEALS.get(stack.getItem());
        return new FoodProperties.Builder().nutrition(meal.nutrition()).saturationMod(meal.saturation()).alwaysEat().build();
    }

    public static ItemStack finish(ItemStack stack, LivingEntity user) {
        if (user.level().isClientSide || !isSpeciesFood(stack, user)) return stack;
        var item = stack.getItem();
        var meal = MEALS.get(item);
        Player owner = user instanceof Player player ? player
                : user instanceof net.ltxprogrammer.changed.entity.ChangedEntity body ? TrueTransfurCompat.bodyOwner(body) : null;
        if (owner != null) {
            owner.getFoodData().eat(meal.nutrition(), meal.saturation());
            owner.awardStat(Stats.ITEM_USED.get(item));
            if (owner instanceof ServerPlayer server) CriteriaTriggers.CONSUME_ITEM.trigger(server, stack);
        }
        var effect = item == Items.BONE ? EFFECTS.get(user.getRandom().nextInt(EFFECTS.size())) : meal.effect();
        if (effect != null) user.addEffect(new MobEffectInstance(effect, meal.duration(), 0));
        user.gameEvent(GameEvent.EAT);
        if (owner == null || !owner.getAbilities().instabuild) stack.shrink(1);
        return stack;
    }
}
