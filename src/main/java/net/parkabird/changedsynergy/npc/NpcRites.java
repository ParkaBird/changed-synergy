package net.parkabird.changedsynergy.npc;

import net.ltxprogrammer.changed.entity.*;
import net.ltxprogrammer.changed.init.ChangedSounds;
import net.ltxprogrammer.changed.init.ChangedGameRules;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import net.parkabird.changedsynergy.compat.SurvivalProtocolCompat;
import net.parkabird.changedsynergy.origin.NativeOriginService;

/** Reversible rites retain human identity; permanent consent adopts a native body. */
@Mod.EventBusSubscriber(modid="changed_synergy")
public final class NpcRites {
    private static CompoundTag data(ServerPlayer player){return CampMembershipData.get(player.server).player(player.getUUID());}
    public static boolean reversible(ServerPlayer player){return !data(player).getString("ReversibleForm").isEmpty();}
    public static void clearReversibleForm(ServerPlayer player) {
        data(player).remove("ReversibleForm");
        backend(player, false);
        CampMembershipData.get(player.server).setDirty();
    }
    public static void revertReversibleForm(ServerPlayer player) {
        if (!reversible(player) || TrueTransfurCompat.blocksReversal(player)) return;
        InvoluntaryTransfurNegotiation.applyPlayerReversal(player, false);
        clearReversibleForm(player);
    }
    public static boolean beginRiteReversal(ChangedEntity npc, ServerPlayer player) {
        if (!reversible(player) || !npc.isAlive() || !player.isAlive()) return false;
        if (TrueTransfurCompat.blocksReversal(player)) {
            net.parkabird.changedsynergy.dialogue.NpcDialogue.trigger(npc, player, net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue.BOND_REVERSE_UNSAFE);
            return false;
        }
        long now = npc.level().getGameTime();
        CompoundTag release = new CompoundTag();
        release.putUUID("ReleasePlayer", player.getUUID());
        release.putLong("ReleaseReverseAt", now + 40L);
        release.putLong("ReleaseEndAt", now + 80L);
        release.putBoolean("ReleaseReversed", false);
        release.putBoolean("ReleaseRite", true);
        npc.getPersistentData().put("ChangedSynergyReleaseHold", release);
        player.closeContainer();

        if (CreatureGrabService.tryStartNegotiatedRelease(npc, player, 80)) {
            net.parkabird.changedsynergy.dialogue.NpcDialogue.trigger(npc, player, net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue.BOND_REVERSE_HOLD);
            return true;
        }

        InvoluntaryTransfurNegotiation.applyPlayerReversal(player, false);
        release.putBoolean("ReleaseReversed", true);
        npc.getPersistentData().put("ChangedSynergyReleaseHold", release);
        InvoluntaryTransfurNegotiation.finishReleaseHold(npc, player);
        return true;
    }
    /** Legacy marker only; new permanent rites use NativeOriginData. */
    public static boolean permanent(ServerPlayer player){return !data(player).getString("PermanentForm").isEmpty();}
    public static boolean canCopy(ChangedEntity npc,ServerPlayer player) {
        return player.isAlive()&&!player.isSpectator()&&!ProcessTransfur.isPlayerTransfurred(player)
                &&!NativeOriginService.isNative(player)&&!NativeOriginService.pending(player)&&!TakeoverService.active(player)
                &&!TrueTransfurCompat.isInstinctShell(player)&&!permanent(player)&&npc.getSelfVariant()!=null;
    }
    public static boolean supportsPermanent() {
        if(!TrueTransfurCompat.supportsNativeOrigin())return false;
        try{Class.forName("com.lugert.true_transfur.transfur.TransfurData").getMethod("setTransfurred",boolean.class);
            Class.forName("com.lugert.true_transfur.transfur.TransfurController").getMethod("setControlMode",ServerPlayer.class,Class.forName("com.lugert.true_transfur.transfur.ControlMode"));
            Class.forName("com.lugert.true_transfur.transfur.TransfurCapabilities").getField("TRANSFUR_DATA");return true;
        }catch(ReflectiveOperationException|LinkageError e){return false;}
    }
    @SuppressWarnings({"unchecked","rawtypes"}) private static boolean backend(ServerPlayer player,boolean enabled) {
        if(!ModList.get().isLoaded("true_transfur"))return !enabled;
        try {
            var cap=(Capability)Class.forName("com.lugert.true_transfur.transfur.TransfurCapabilities").getField("TRANSFUR_DATA").get(null);
            Object state=player.getCapability(cap).resolve().orElse(null);if(state==null)return false;
            state.getClass().getMethod("setTransfurred",boolean.class).invoke(state,enabled);
            if(enabled) {
                Class mode=Class.forName("com.lugert.true_transfur.transfur.ControlMode");
                Class<?> controller=Class.forName("com.lugert.true_transfur.transfur.TransfurController");
                controller.getMethod("setControlMode",ServerPlayer.class,mode).invoke(null,player,Enum.valueOf(mode,"PLAYER"));
                if(!controller.getMethod("getControlMode",ServerPlayer.class).invoke(null,player).toString().equals("PLAYER"))return false;
            }
            return Boolean.valueOf(enabled).equals(state.getClass().getMethod("isTransfurred").invoke(state));
        }catch(ReflectiveOperationException|LinkageError e){return false;}
    }
    public static boolean apply(ChangedEntity npc,ServerPlayer player,boolean irreversible) {
        if(!canCopy(npc,player)||irreversible&&!supportsPermanent())return false;
        var store=CampMembershipData.get(player.server);var previous=data(player).copy();String form=npc.getSelfVariant().getFormId().toString();
        var origins=net.parkabird.changedsynergy.origin.NativeOriginData.get(player.server);
        var previousIdentity=NativeOriginService.identity(player);
        try {
            var context=TransfurContext.npcLatexHazard(npc,TransfurCause.GRAB_REPLICATE);
            float progress=player.level().getGameRules().getBoolean(ChangedGameRules.RULE_DO_TRANSFUR_ANIMATION)?0F:1F;
            if(irreversible) {
                data(player).remove("ReversibleForm");
                NativeOriginService.adoptNativeBody(player,npc.getSelfVariant());
                if(!SurvivalProtocolCompat.changeOrigin(player,form,()->
                        TrueTransfurCompat.initializePersistentBody(player,npc.getSelfVariant(),context,progress)
                                &&backend(player,true)&&TrueTransfurCompat.isPermanentPlayer(player)))
                    throw new IllegalStateException("Permanent ritual was rejected");
            }else {
                data(player).putString("ReversibleForm",form);
                var instance=ProcessTransfur.setPlayerTransfurVariant(player,npc.getSelfVariant(),context,progress);
                if(instance==null||instance.getParent()!=npc.getSelfVariant())throw new IllegalStateException("Ritual form was rejected");
                instance.willSurviveTransfur=true;instance.setTemporaryForSuit(false);
                if(!backend(player,false))throw new IllegalStateException("TT did not accept reversal permission");
            }
            InvoluntaryTransfurNegotiation.abandonForVoluntaryBond(player);
            CreaturePersonality.rewardVoluntaryTransfur(npc,player);
            net.parkabird.changedsynergy.advancement.SynergyAdvancements.grant(player,net.parkabird.changedsynergy.advancement.SynergyAdvancements.VOLUNTARY_TRANSFUR);
            store.setDirty();
            ChangedSounds.broadcastSound(player,npc.getSelfVariant().sound,1F,1F);
            return true;
        }catch(RuntimeException e) {
            net.parkabird.changedsynergy.ChangedSynergyMod.LOGGER.warn("NPC ritual rolled back for {}",player.getUUID(),e);
            // Suppress TT's reversal hook while rolling a failed, uncommitted ritual back.
            if(previousIdentity==null)origins.players.remove(player.getUUID());
            else origins.players.put(player.getUUID(),previousIdentity);
            origins.setDirty();
            data(player).remove("PermanentForm");data(player).putString("ReversibleForm",form);backend(player,false);
            ProcessTransfur.removePlayerTransfurVariant(player);store.players.put(player.getUUID(),previous);store.setDirty();return false;
        }
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer player)||player.tickCount%20!=0)return;
        var data=data(player);var form=ProcessTransfur.getPlayerTransfurVariant(player);
        if(!data.getString("ReversibleForm").isEmpty()&&(form==null||!form.isTemporaryFromSuit()&&!form.getParent().getFormId().toString().equals(data.getString("ReversibleForm")))) {
            if(form==null)backend(player,false);data.remove("ReversibleForm");CampMembershipData.get(player.server).setDirty();
        }
        migratePermanent(player);
    }
    /** Consume the old second body record once; subsequent recovery belongs to Native Origin. */
    public static void migratePermanent(ServerPlayer player) {
        if(!permanent(player))return;
        var saved=data(player);var registry=net.ltxprogrammer.changed.init.ChangedRegistry.TRANSFUR_VARIANT.get();
        if(!NativeOriginService.isNative(player)) {
            var current=ProcessTransfur.getPlayerTransfurVariant(player);
            String currentId=current!=null&&!current.isTemporaryFromSuit()?current.getParent().getFormId().toString()
                    :saved.getString("PermanentCurrentForm");
            var id=net.minecraft.resources.ResourceLocation.tryParse(saved.getString("PermanentForm"));
            var original=id==null?null:registry.getValue(id);
            if(original==null) {
                id=net.minecraft.resources.ResourceLocation.tryParse(currentId);
                original=id==null?null:registry.getValue(id);
            }
            if(original==null)return;
            NativeOriginService.adoptNativeBody(player,original);
            id=net.minecraft.resources.ResourceLocation.tryParse(currentId);
            if(id!=null&&registry.getValue(id)!=null)NativeOriginService.identity(player).current=currentId;
        }
        saved.remove("PermanentForm");saved.remove("PermanentCurrentForm");
        CampMembershipData.get(player.server).setDirty();
    }
}
