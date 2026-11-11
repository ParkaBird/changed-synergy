package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.ModList;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import net.parkabird.changedsynergy.init.*;
import net.parkabird.changedsynergy.origin.NativeOriginData;
import net.parkabird.changedsynergy.world.inventory.SocialInteractionMenu;

final class EagleOwlBondChecks {
    @SuppressWarnings({"rawtypes","unchecked"}) static void run(MinecraftServer server)throws Exception{
        var owner=new ServerPlayer(server,server.overworld(),new GameProfile(UUID.randomUUID(),"OwlBondTest"));
        owner.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),owner);
        var field=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(PlayerList.class,"f_11197_");
        var players=(Map<UUID,ServerPlayer>)field.get(server.getPlayerList());players.put(owner.getUUID(),owner);owner.serverLevel().addNewPlayer(owner);
        var level=owner.serverLevel();var peers=new ArrayList<ChangedEntity>();boolean tokens=ChangedSynergyConfig.COMMON.bondTokensRequired.get(),multiple=ChangedSynergyConfig.COMMON.allowMultipleBonds.get();
        var origins=NativeOriginData.get(server);boolean originRule=level.getGameRules().getBoolean(ChangedSynergyGameRules.NATIVE_ORIGINS);
        java.lang.reflect.Method change=null;Object playerMode=null;int checks=0;
        try{
            ChangedSynergyConfig.COMMON.bondTokensRequired.set(true);ChangedSynergyConfig.COMMON.allowMultipleBonds.set(true);
            owner.setPos(0.5,100,0.5);ProcessTransfur.setPlayerTransfurVariant(owner,ChangedSynergyTransfurVariants.LATEX_EAGLE_OWL.get(),null,1,false);
            for(int mode=0;mode<(ModList.get().isLoaded("true_transfur")?2:1);mode++){
                if(mode==1){
                    level.getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(true,server);
                    var identity=new NativeOriginData.Identity();identity.state="native";identity.form=identity.current=ChangedSynergyTransfurVariants.LATEX_EAGLE_OWL.getId().toString();identity.faction="light";origins.players.put(owner.getUUID(),identity);
                    Class control=Class.forName("com.lugert.true_transfur.transfur.ControlMode");change=Class.forName("com.lugert.true_transfur.transfur.TransfurController").getMethod("switchControlMode",ServerPlayer.class,control);playerMode=Enum.valueOf(control,"PLAYER");
                    Capability cap=(Capability)Class.forName("com.lugert.true_transfur.transfur.TransfurCapabilities").getField("TRANSFUR_DATA").get(null);Object state=owner.getCapability(cap).orElse(null);check(state!=null,"TT capability available");state.getClass().getMethod("setTransfurred",boolean.class).invoke(state,true);
                    check(Boolean.TRUE.equals(change.invoke(null,owner,Enum.valueOf(control,"INSTINCT"))),"real TT Instinct starts");
                    check(TrueTransfurCompat.interactionBody(owner)!=owner,"TT has separate physical body");
                    TrueTransfurCompat.interactionBody(owner).setPos(0.5,100,0.5);owner.setPos(100.5,100,100.5);
                }
                var physical=TrueTransfurCompat.interactionBody(owner);var peer=ChangedSynergyEntities.LATEX_EAGLE_OWL.get().create(level);peer.setPos(0.5,100,2.5);peer.setNoAi(true);level.getChunk(peer.blockPosition());level.addFreshEntity(peer);peers.add(peer);
                CreaturePersonality.establishRelationship(peer,owner);CreaturePersonality.setFamiliarity(peer,owner,60);
                check(!NativeBondInvitationService.canOffer(peer,owner),"actual token required before bonding");
                var offered=BondTokenService.required(peer);int count=offered.getCount();offered.setCount(count+2);owner.setItemInHand(InteractionHand.MAIN_HAND,offered);if(physical!=owner)physical.setItemInHand(InteractionHand.MAIN_HAND,offered.copy());
                check(BondTokenService.offer(peer,owner)&&owner.getMainHandItem().getCount()==2,"token debited from authoritative player inventory once");
                if(physical!=owner){var sync=Class.forName("com.lugert.true_transfur.transfur.InstinctRuntime").getDeclaredMethod("synchronizeInventoryFromPlayer",ServerPlayer.class,ChangedEntity.class);sync.setAccessible(true);sync.invoke(null,owner,physical);}
                check(physical.getMainHandItem().getCount()==2,"TT equipment synchronization cannot restore a spent token");
                check(BondTokenService.reservedFor(peer,owner)&&NativeBondInvitationService.canOffer(peer,owner),"escrow enables transformed-player invitation");
                var menu=new SocialInteractionMenu(48,owner.getInventory(),peer,false);
                check(menu.isNativeBondAvailable(),"social wheel exposes invitation, mode="+mode);
                check(menu.stillValid(owner),"social wheel valid at physical body's location, mode="+mode+", basic="+RelationshipFavorService.canOpenBasicWheel(peer,owner));
                check(!NativeBondInvitationService.select(peer,owner)&&NativeBondInvitationService.select(peer,owner),"token-backed bond requires two selections");
                check(LatexSocialMemory.isBonded(peer,owner)&&LatexSocialMemory.isPetOwner(peer,owner)&&!BondTokenService.reservedFor(peer,owner),"same bond identity and consumed escrow");
                check(physical.getMainHandItem().getCount()==2,"confirmation never charges twice");checks+=7;
            }
            System.out.println("EAGLE_OWL_BOND_PASS: "+checks+" human-start owl and real TT physical-body token/bond checks");
        }finally{
            if(change!=null&&TrueTransfurCompat.isInstinctShell(owner))change.invoke(null,owner,playerMode);
            for(var peer:peers){LatexSocialMemory.unregisterBond(peer,owner);peer.discard();}
            origins.players.remove(owner.getUUID());ProcessTransfur.removePlayerTransfurVariant(owner);players.remove(owner.getUUID());owner.discard();
            ChangedSynergyConfig.COMMON.bondTokensRequired.set(tokens);ChangedSynergyConfig.COMMON.allowMultipleBonds.set(multiple);level.getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(originRule,server);
        }
    }
    private static void check(boolean value,String text){if(!value)throw new AssertionError(text);}
}
