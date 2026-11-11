package net.parkabird.changedsynergy.item;

import java.util.List;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.data.*;
import net.ltxprogrammer.changed.init.ChangedAccessorySlots;
import net.ltxprogrammer.changed.item.ShirtItem;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** One native garment, including the author's full juvenile saddle-and-scarf cut. */
public final class DesertOutfitItem extends ShirtItem {
    public DesertOutfitItem(){}
    public static boolean fits(LivingEntity wearer){return wearer instanceof Player||HyenaTrailclothItem.body(wearer)!=null;}
    @Override public boolean allowedInSlot(ItemStack stack,LivingEntity wearer,AccessorySlotType slot){
        if(!fits(wearer))return false;
        boolean pup=EagleOwlAviatorItem.juvenile(wearer);
        if(slot!=ChangedAccessorySlots.BODY.get()&&slot!=ChangedAccessorySlots.FULL_BODY.get()&&!(pup&&slot==ChangedAccessorySlots.HEAD.get()))return false;
        return AccessorySlots.getForEntity(wearer).map(s->s.getSlotTypes().filter(t->t!=slot).noneMatch(t->s.getItem(t).filter(i->i.getItem()==this).isPresent())
                &&(slot==ChangedAccessorySlots.HEAD.get()||!s.hasSlot(ChangedAccessorySlots.HEAD.get())||AccessorySlots.isSlotAvailable(wearer,ChangedAccessorySlots.HEAD.get()))
                &&(!HyenaTrailclothItem.hasLowerSection(wearer)||!s.hasSlot(ChangedAccessorySlots.LEGS.get())||AccessorySlots.isSlotAvailable(wearer,ChangedAccessorySlots.LEGS.get()))).orElse(false);
    }
    @Override public boolean shouldDisableSlot(AccessorySlotContext<?> context,AccessorySlotType other){
        return other==ChangedAccessorySlots.HEAD.get()&&context.slotType()!=other
                ||other==ChangedAccessorySlots.LEGS.get()&&HyenaTrailclothItem.hasLowerSection(context.wearer());
    }
    @Override public ResourceLocation getTexture(ItemStack stack,Entity entity){return ResourceLocation.parse("changed_synergy:textures/models/desert_"+(entity instanceof LivingEntity wearer && HyenaTrailclothItem.body(wearer) instanceof net.parkabird.changedsynergy.entity.LatexJerboa?"jerboa":"bipedal")+".png");}
    @Override public void appendHoverText(ItemStack stack,@Nullable Level level,List<Component> lines,TooltipFlag flags){super.appendHoverText(stack,level,lines,flags);lines.add(Component.translatable("item.changed_synergy.desert_nomad_outfit.desc"));}
}
