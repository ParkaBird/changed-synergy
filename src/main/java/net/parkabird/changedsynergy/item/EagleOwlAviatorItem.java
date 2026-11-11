package net.parkabird.changedsynergy.item;

import java.util.List;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.data.*;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedAccessorySlots;
import net.ltxprogrammer.changed.item.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.StateDefinition;
import net.parkabird.changedsynergy.ai.CreatureSocialProfile;

/** Native apparel; CLOSED lowers the goggles. Juveniles wear the goggles alone. */
public final class EagleOwlAviatorItem extends ShirtItem {
    public EagleOwlAviatorItem(){registerDefaultState(defaultClothingState().setValue(CLOSED,false));}
    @Override protected void createClothingStateDefinition(StateDefinition.Builder<ClothingItem,ClothingState> builder){builder.add(CLOSED);}
    public static boolean juvenile(LivingEntity wearer){var body=HyenaTrailclothItem.body(wearer);return wearer.isBaby()||body!=null&&(body.isBaby()||CreatureSocialProfile.isJuvenile(body));}
    public static boolean lowered(ItemStack stack){return stack.getItem() instanceof EagleOwlAviatorItem outfit&&outfit.getClothingState(stack).getValue(CLOSED);}
    public static boolean wearingLowered(LivingEntity wearer){return AccessorySlots.isWearing(wearer,s->s.getItem() instanceof EagleOwlAviatorItem&&lowered(s));}
    public static boolean fits(LivingEntity wearer){return wearer instanceof Player||HyenaTrailclothItem.body(wearer)!=null;}
    @Override public boolean allowedInSlot(ItemStack stack,LivingEntity wearer,AccessorySlotType slot){
        if(!fits(wearer))return false;
        if(juvenile(wearer))return (slot==ChangedAccessorySlots.FACE.get()||slot==ChangedAccessorySlots.HEAD.get())&&AccessorySlots.getForEntity(wearer)
                .map(s->s.getSlotTypes().filter(t->t!=slot).noneMatch(t->s.getItem(t).filter(i->i.getItem()==this).isPresent())).orElse(false);
        if(slot!=ChangedAccessorySlots.BODY.get()&&slot!=ChangedAccessorySlots.FULL_BODY.get())return false;
        return AccessorySlots.getForEntity(wearer).map(s->s.getSlotTypes().filter(t->t!=slot).noneMatch(t->s.getItem(t).filter(i->i.getItem()==this).isPresent())
                &&(!HyenaTrailclothItem.hasLowerSection(wearer)||!s.hasSlot(ChangedAccessorySlots.LEGS.get())||AccessorySlots.isSlotAvailable(wearer,ChangedAccessorySlots.LEGS.get()))
                &&(!s.hasSlot(ChangedAccessorySlots.FACE.get())||AccessorySlots.isSlotAvailable(wearer,ChangedAccessorySlots.FACE.get()))).orElse(false);
    }
    @Override public boolean shouldDisableSlot(AccessorySlotContext<?> context,AccessorySlotType other){
        return !juvenile(context.wearer())&&(other==ChangedAccessorySlots.FACE.get()||other==ChangedAccessorySlots.LEGS.get()&&HyenaTrailclothItem.hasLowerSection(context.wearer()));
    }
    @Override public void accessoryInteract(AccessorySlotContext<?> context){super.accessoryInteract(context);setClothingState(context.stack(),getClothingState(context.stack()).cycle(CLOSED));context.wearer().playSound(getEquipSound(context.stack()),1,1);}
    @Override public ResourceLocation getTexture(ItemStack stack,Entity entity){return ResourceLocation.parse("changed_synergy:textures/models/eagle_owl_aviator_outfit.png");}
    @Override public void appendHoverText(ItemStack stack,@Nullable Level level,List<Component> lines,TooltipFlag flags){super.appendHoverText(stack,level,lines,flags);if(level!=null&&level.isClientSide)addInteractInstructions(lines::add);lines.add(Component.translatable("item.changed_synergy.eagle_owl_aviator_outfit.desc"));}
}
