package net.parkabird.changedsynergy.ai;

import java.util.*;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.Emote;
import net.ltxprogrammer.changed.init.ChangedRegistry;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.parkabird.changedsynergy.*;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import net.parkabird.changedsynergy.dialogue.*;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.network.*;
import net.parkabird.changedsynergy.origin.*;

@Mod.EventBusSubscriber(modid=ChangedSynergyMod.MOD_ID)
public final class NativeRoleplayService {
    private static final Map<UUID,NativeRoleplayStatePacket> SENT=new HashMap<>();
    private static final Map<UUID,LivingEntity> EMOTE_BODIES=new HashMap<>();
    private NativeRoleplayService() {}
    public static boolean active(ServerPlayer player) {
        return NativeOriginService.isNative(player)&&ChangedSynergyGameRules.enabled(player.level(),ChangedSynergyGameRules.NATIVE_ORIGINS);
    }
    public static ChangedEntity visual(ServerPlayer player) {
        LivingEntity body=TrueTransfurCompat.interactionBody(player);
        if(body instanceof ChangedEntity changed)return changed;
        return ProcessTransfur.getPlayerTransfurVariantSafe(player).map(form->form.getChangedEntity()).orElse(null);
    }
    public static void ensureName(ServerPlayer player) {
        var identity=NativeOriginService.identity(player);
        if(identity==null||!identity.state.equals("native")||!identity.roleName.isEmpty())return;
        var id=ResourceLocation.tryParse(identity.form);
        var variant=id==null?null:ChangedRegistry.TRANSFUR_VARIANT.get().getValue(id);
        ChangedEntity template=variant==null?visual(player):variant.getEntityType().create(player.serverLevel());
        if(template==null)return;
        identity.roleName=Component.Serializer.toJson(CreatureIdentity.playerName(template,
                player.getUUID().getMostSignificantBits()^player.getUUID().getLeastSignificantBits()));
        NativeOriginData.get(player.server).setDirty();
    }
    public static Component name(ServerPlayer player) {
        if(active(player)) {
            ensureName(player); String json=NativeOriginService.identity(player).roleName;
            try {Component name=Component.Serializer.fromJson(json);if(name!=null)return name;} catch(RuntimeException ignored) {}
        } return Component.literal(player.getGameProfile().getName());
    }
    public static boolean validName(String text) {
        return !text.isBlank()&&text.codePointCount(0,text.length())<=24
                &&text.codePoints().noneMatch(cp->Character.isISOControl(cp)||Character.getType(cp)==Character.FORMAT||cp==0xA7);
    }
    public static int rename(ServerPlayer player,String text) {
        text=text.strip(); if(!active(player)||!validName(text)) {player.sendSystemMessage(Component.translatable("message.changed_synergy.roleplay.invalid_name"));return 0;}
        NativeOriginService.identity(player).roleName=Component.Serializer.toJson(Component.literal(text));
        PlayerFriendshipData.get(player.server).rememberName(player.getUUID(),text);
        NativeOriginData.get(player.server).setDirty(); player.refreshDisplayName();player.refreshTabListName(); sync(player);
        player.sendSystemMessage(Component.translatable("message.changed_synergy.roleplay.renamed",name(player)));return 1;
    }
    public static boolean roleChat(ServerPlayer player) {return active(player)&&ChangedSynergyConfig.COMMON.playerRoleSpeech.get()&&!NativeOriginService.identity(player).globalChat;}
    public static int chatMode(ServerPlayer player,boolean global) {
        if(!global&&!ChangedSynergyConfig.COMMON.playerRoleSpeech.get()) {
            player.sendSystemMessage(Component.translatable("message.changed_synergy.roleplay.speech_disabled"));return 0;
        }
        if(!active(player))return 0;NativeOriginService.identity(player).globalChat=global;NativeOriginData.get(player.server).setDirty();sync(player);
        player.sendSystemMessage(Component.translatable(global?"message.changed_synergy.roleplay.chat_global":"message.changed_synergy.roleplay.chat_local"));return 1;
    }
    public static boolean validBody(ServerPlayer player,LivingEntity body) {
        return body!=null&&body.isAlive()&&!body.isRemoved()&&body.level()==player.level()
                &&!(body==player&&TrueTransfurCompat.isInstinctShell(player))&&!TrueTransfurCompat.isRebirthing(player);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void onChat(ServerChatEvent event) {
        if(event.isCanceled()||!roleChat(event.getPlayer()))return;
        event.setCanceled(true); speak(event.getPlayer(),event.getMessage().getString());
    }
    public static boolean speak(ServerPlayer player,String text) {
        if(!roleChat(player))return false;
        LivingEntity body=TrueTransfurCompat.interactionBody(player);ChangedEntity voice=visual(player);
        if(!validBody(player,body)||voice==null||player.isSpectator()||text.isBlank()) {player.sendSystemMessage(Component.translatable("message.changed_synergy.roleplay.body_unavailable"));return false;}
        long now=player.server.overworld().getGameTime(); var tag=player.getPersistentData();
        if(tag.getLong("SynergyRoleSpeechAt")>now) {player.sendSystemMessage(Component.translatable("message.changed_synergy.roleplay.speech_wait"));return false;}
        tag.putLong("SynergyRoleSpeechAt",now+(long)Math.ceil(ChangedSynergyConfig.COMMON.playerRoleSpeechCooldown.get()*20));
        String bounded=text.codePointCount(0,text.length())>256?text.substring(0,text.offsetByCodePoints(0,256)):text;
        Component speaker=name(player).copy().withStyle(style->style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                Component.literal(player.getGameProfile().getName()))));
        double range=ChangedSynergyConfig.COMMON.npcDialogueRange.get();
        int color=voice.getSelfVariant()==null?0xF2B458:voice.getSelfVariant().getColors().getFirst().toInt();
        for(ServerPlayer listener:player.serverLevel().players()) {
            LivingEntity listenerBody=TrueTransfurCompat.interactionBody(listener);
            if(listener!=player&&(!validBody(listener,listenerBody)||listener.isSpectator()||body.distanceToSqr(listenerBody)>range*range))continue;
            Component line=listener==player||NpcDialogue.canUnderstand(voice,listener)?Component.literal(bounded):NpcDialogue.vocalization(voice,listener);
            Component message=Component.translatable("message.changed_synergy.npc_dialogue",speaker,line);
            ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->listener),new TelepathyDialoguePacket(message,speaker,line,color));
        }
        ChangedSynergyMod.LOGGER.info("[Role speech] {}: {}",player.getGameProfile().getName(),bounded);return true;
    }
    public static boolean emote(ServerPlayer player,Emote emote,boolean manual) {
        if(!active(player)||!ChangedSynergyGameRules.enabled(player.level(),ChangedSynergyGameRules.NPC_EMOTES))return false;
        LivingEntity body=TrueTransfurCompat.interactionBody(player);long now=player.server.overworld().getGameTime();
        if(!validBody(player,body)||manual&&player.getPersistentData().getLong("SynergyPlayerEmoteAt")>now)return false;
        if(manual)player.getPersistentData().putLong("SynergyPlayerEmoteAt",now+60);
        LivingEntity old=EMOTE_BODIES.put(player.getUUID(),body);if(old!=null&&old!=body)NpcEmoteState.clear(old);
        return NpcEmoteState.show(player.serverLevel(),body,body,emote,manual?30:20,60,true);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void names(PlayerEvent.NameFormat event) {
        Component name=event.getEntity() instanceof ServerPlayer server&&active(server)?name(server)
                :event.getEntity().level().isClientSide?net.parkabird.changedsynergy.client.NativeRoleplayClient.name(event.getEntity().getUUID()):null;
        if(name!=null)event.setDisplayname(name.copy());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tabNames(PlayerEvent.TabListNameFormat event) {
        if(event.getEntity() instanceof ServerPlayer server&&active(server))event.setDisplayName(name(server).copy().append(" · ").append(server.getGameProfile().getName()));
    }
    public static void sync(ServerPlayer player) {
        boolean nativeRole=active(player);var packet=new NativeRoleplayStatePacket(player.getUUID(),nativeRole,name(player),!roleChat(player));
        SENT.put(player.getUUID(),packet);ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(),packet);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        PlayerFriendshipData.get(player.server).rememberName(player.getUUID(),name(player).getString());
        for(ServerPlayer other:player.server.getPlayerList().getPlayers()) {
            ensureName(other);boolean nativeRole=active(other);
            ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new NativeRoleplayStatePacket(other.getUUID(),nativeRole,name(other),!roleChat(other)));
        } sync(player);
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer player))return;
        LivingEntity shown=EMOTE_BODIES.get(player.getUUID());
        if(shown!=null) {
            if(shown!=TrueTransfurCompat.interactionBody(player)||!active(player)||!shown.isAlive()||!ChangedSynergyGameRules.enabled(player.level(),ChangedSynergyGameRules.NPC_EMOTES)) {
                NpcEmoteState.clear(shown);EMOTE_BODIES.remove(player.getUUID());
            } else NpcEmoteState.tick(shown);
        }
        if(player.tickCount%20!=0)return;
        var packet=new NativeRoleplayStatePacket(player.getUUID(),active(player),name(player),!roleChat(player));
        if(!packet.equals(SENT.get(player.getUUID()))) {player.refreshDisplayName();player.refreshTabListName();sync(player);}
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        LivingEntity old=EMOTE_BODIES.remove(event.getEntity().getUUID());if(old!=null)NpcEmoteState.clear(old);SENT.remove(event.getEntity().getUUID());
        if(event.getEntity() instanceof ServerPlayer player)ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(),
                new NativeRoleplayStatePacket(player.getUUID(),false,Component.literal(player.getGameProfile().getName()),true));
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {SENT.clear();EMOTE_BODIES.clear();}
}
