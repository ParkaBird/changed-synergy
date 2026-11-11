package net.parkabird.changedsynergy.network;

import java.util.function.Supplier;
import net.ltxprogrammer.changed.data.AccessorySlots;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.NetworkEvent;
import net.parkabird.changedsynergy.compat.ClothingStyles;
import net.parkabird.changedsynergy.compat.curios.CuriosCompat;
import net.parkabird.changedsynergy.world.inventory.BondedCreatureInventoryMenu;

public record ClothingStylePacket(int menu,int entity,String identifier) {
    public static void encode(ClothingStylePacket p,FriendlyByteBuf b){b.writeVarInt(p.menu);b.writeVarInt(p.entity);b.writeUtf(p.identifier,64);}
    public static ClothingStylePacket decode(FriendlyByteBuf b){return new ClothingStylePacket(b.readVarInt(),b.readVarInt(),b.readUtf(64));}
    public static void handle(ClothingStylePacket p,Supplier<NetworkEvent.Context> supplier) {
        var context=supplier.get();context.enqueueWork(()->{
            var player=context.getSender();if(player==null||player.containerMenu.containerId!=p.menu||!CuriosCompat.isLoaded())return;
            LivingEntity wearer;
            if(p.entity==player.getId() && CuriosCompat.isEquipmentMenu(player.containerMenu))wearer=player;
            else if(player.containerMenu instanceof BondedCreatureInventoryMenu m && m.getPet()!=null
                    && m.getPet().getId()==p.entity && m.stillValid(player) && m.curiosVisible())wearer=m.getPet();
            else return;
            var type=CuriosCompat.clothingType(p.identifier);if(type==null)return;
            AccessorySlots.getForEntity(wearer).flatMap(s->s.getItem(type)).ifPresent(stack->{
                if(ClothingStyles.available(stack)){type.handleEvent(wearer,stack,2);player.containerMenu.broadcastChanges();}
            });
        });context.setPacketHandled(true);
    }
}
