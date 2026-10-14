package net.parkabird.changedsynergy.ai;

import java.util.*;
import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.ability.GrabEntityAbility;
import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.ltxprogrammer.changed.network.packet.GrabEntityPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.parkabird.changedsynergy.*;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.network.*;
import net.parkabird.changedsynergy.origin.NativeOriginService;

/** Native INSTINCT player -> human takeover, after normal transfur progress completes. */
@Mod.EventBusSubscriber(modid=ChangedSynergyMod.MOD_ID)
public final class InstinctConfinementService {
    private static final String RECOVERY="SynergyInstinctRestraint", GRACE="SynergyInstinctRestraintGrace", SUIT_FORM="SynergyInstinctSuitForm";
    private static final Map<UUID,Session> ACTIVE=new HashMap<>();
    private static final class Session {
        final UUID id=UUID.randomUUID();final ServerPlayer owner,human;final ChangedEntity body;final GrabEntityAbilityInstance grab;
        final String form;
        final long started,total;long until,qteUntil,lastInput=-1;int phase=TakeoverStatePacket.CONTROLLED,progress,sequence,key;
        boolean escapeUsed,negotiationUsed,confirmed;
        Session(ServerPlayer owner,ServerPlayer human,ChangedEntity body,GrabEntityAbilityInstance grab,long now) {
            this.owner=owner;this.human=human;this.body=body;this.grab=grab;this.started=now;total=ChangedSynergyConfig.COMMON.instinctConfinementSeconds.get()*20L;until=now+total;
            form=ProcessTransfur.getPlayerTransfurVariantSafe(human).map(variant->variant.getFormId().toString()).orElse("");
        }
    }
    private InstinctConfinementService() {}
    public static boolean active(net.minecraft.world.entity.player.Player player) {return player!=null&&ACTIVE.containsKey(player.getUUID());}
    public static boolean carrying(ChangedEntity body) {return ACTIVE.values().stream().anyMatch(session->session.body==body);}
    public static ChangedEntity carrier(net.minecraft.world.entity.player.Player player) {
        Session session=player==null?null:ACTIVE.get(player.getUUID());return session==null?null:session.body;
    }
    public static boolean protectsHumanForm(ChangedEntity body,LivingEntity target) {
        if(!(target instanceof ServerPlayer)||!TrueTransfurCompat.isInstinctBody(body))return false;
        ServerPlayer owner=TrueTransfurCompat.bodyOwner(body);return owner!=null&&NativeRoleplayService.active(owner);
    }
    public static boolean canTarget(ChangedEntity body,ServerPlayer human) {
        ServerPlayer owner=TrueTransfurCompat.bodyOwner(body);
        if(owner==null||!owner.isAlive()||owner.isSpectator()||!NativeRoleplayService.active(owner)||!TrueTransfurCompat.isInstinctBody(body))return false;
        if(owner==human||!body.isAlive()||!human.isAlive()||human.isCreative()||human.isSpectator()
                ||TrueTransfurCompat.isInstinctShell(human)||TrueTransfurCompat.isRebirthing(human)||body.level()!=human.level()||!owner.server.isPvpAllowed()||!owner.canHarmPlayer(human)
                ||PlayerFriendshipData.friends(owner,human)||ProcessTransfur.isPlayerTransfurred(human)||TakeoverService.active(human)
                ||human.getPersistentData().getLong(GRACE)>human.server.overworld().getGameTime()
                ||!ChangedSynergyGameRules.enabled(body.level(),ChangedSynergyGameRules.NPC_AI))return false;
        var identity=NativeOriginService.identity(owner);
        int standing=FactionReputation.scoreForAccount(human,NativeOriginService.account(identity));
        return standing<=FactionReputation.HOSTILE_THRESHOLD||body.getLastHurtByMob()==human&&body.tickCount-body.getLastHurtByMobTimestamp()<1200;
    }
    private static boolean canBegin(ChangedEntity body,ServerPlayer human) {
        long now=human.server.overworld().getGameTime();
        var juvenile=net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ChangedSynergyMod.MOD_ID,"juvenile_social"));
        if(!ChangedSynergyConfig.COMMON.instinctConfinement.get()||!ChangedSynergyConfig.COMMON.takeoverEnabled.get()
                ||!Changed.config.server.isGrabEnabled.get()||!canTarget(body,human)||active(human)||carrying(body)
                ||body.getSelfVariant()==null||ProcessTransfur.getPlayerTransfurProgress(human)<ProcessTransfur.getEntityTransfurTolerance(human)
                ||human.getPersistentData().getLong(GRACE)>now||body.getType().is(juvenile)
                ||CreatureSocialProfile.isGrabMechanicExcluded(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(body.getType()))
                ||body.isSleeping()||body.isPassenger()||body.isVehicle()||human.isPassenger()||human.isSleeping()
                ||GrabEntityAbility.getGrabber(body)!=null||GrabEntityAbility.getGrabber(human)!=null
                ||!TakeoverService.canPhysicallyCapture(body,human)||TakeoverService.active(human))return false;
        ServerPlayer owner=TrueTransfurCompat.bodyOwner(body);
        if(net.ltxprogrammer.changed.ability.IAbstractChangedEntity.forPlayer(owner).getAbilityInstanceSafe(
                net.ltxprogrammer.changed.init.ChangedAbilities.GRAB_ENTITY_ABILITY.get()).isEmpty())return false;
        // TT's visible NPC body may lack an ability its player's form already has.
        BondedSuitService.ensureGrabAbility(body);
        GrabEntityAbilityInstance grab=BondedSuitService.ability(body);return grab!=null&&grab.grabbedEntity==null;
    }
    public static boolean begin(ChangedEntity body,ServerPlayer human) {
        if(!canBegin(body,human))return false;
        ServerPlayer owner=TrueTransfurCompat.bodyOwner(body);long now=human.server.overworld().getGameTime();
        GrabEntityAbilityInstance grab=BondedSuitService.ability(body);
        net.parkabird.changedsynergy.compat.ChangedAddonCompat.configureFriendlyGrab(grab,false);
        // The same native SUIT path as ordinary NPC takeover. Temporary suit
        // variants are exempt from TT's permanent-body and reversal lifecycle.
        if(!grab.suitEntity(human))return false;
        grab.suited=true;grab.grabbedHasControl=false;grab.attackDown=grab.useDown=false;grab.grabStrength=1;
        Session session=new Session(owner,human,body,grab,now);ACTIVE.put(human.getUUID(),session);
        human.getPersistentData().putUUID(RECOVERY,session.id);human.getPersistentData().putString(SUIT_FORM,session.form);
        if(human instanceof net.ltxprogrammer.changed.entity.LivingEntityDataExtension extension)extension.setGrabbedBy(body);
        ProcessTransfur.setPlayerTransfurProgress(human,0);
        InvoluntaryTransfurNegotiation.abandonForTakeover(human);
        FactionHostilityGrace.beginGlobal(human);
        net.parkabird.changedsynergy.event.LatexSocialEvents.calmTakeoverThreats(human,body);
        body.setTarget(null);body.getNavigation().stop();
        Changed.PACKET_HANDLER.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->body),new GrabEntityPacket(body,human,GrabEntityPacket.GrabType.SUIT));
        net.ltxprogrammer.changed.init.ChangedSounds.broadcastSound(body,net.ltxprogrammer.changed.init.ChangedSounds.LATEX_SUIT_ENTITY,1,1);
        tell(session,"begin");sync(session,false);return true;
    }
    private static void tell(Session session,String suffix){session.human.sendSystemMessage(Component.translatable("message.changed_synergy.instinct_confinement."+suffix,NativeRoleplayService.name(session.owner)));}
    private static void sync(Session session,boolean finish) {
        long now=session.human.server.overworld().getGameTime();
        var packet=new TakeoverStatePacket(session.id,TakeoverStatePacket.INSTINCT,
                finish?TakeoverStatePacket.FINISHED:session.phase,session.body.getId(),session.human.getId(),(int)Math.max(0,session.until-now),(int)(session.until-session.started),0,
                session.escapeUsed,session.negotiationUsed,ChangedSynergyConfig.COMMON.takeoverNegotiation.get(),0,session.phase==TakeoverStatePacket.STRUGGLE?session.key:-1,
                session.sequence,session.progress,12,0,Component.Serializer.toJson(NativeRoleplayService.name(session.owner)));
        ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(()->session.body),packet);
        // TT's technical player may not be in the vanilla body tracking set.
        if(session.owner.connection!=null)ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->session.owner),packet);
        if(session.human.connection!=null)ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->session.human),packet);
    }
    public static boolean release(ServerPlayer human) {
        Session session=ACTIVE.remove(human.getUUID());if(session==null)return false;
        if(session.grab.grabbedEntity==human)session.grab.releaseEntity(false);
        restoreHumanForm(human,session.form);
        human.noPhysics=false;
        if(human instanceof net.ltxprogrammer.changed.entity.LivingEntityDataExtension extension&&extension.getGrabbedBy()==session.body)extension.setGrabbedBy(null);
        human.getPersistentData().remove(RECOVERY);human.getPersistentData().remove(SUIT_FORM);human.getPersistentData().putLong(GRACE,human.server.overworld().getGameTime()+1200);
        TakeoverSafety.find(human,human.position(),session.body,4,false).ifPresent(point->human.teleportTo(point.x,point.y,point.z));
        human.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);human.fallDistance=0;
        FactionHostilityGrace.beginGlobal(human);
        Changed.PACKET_HANDLER.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->session.body),new GrabEntityPacket(session.body,human,GrabEntityPacket.GrabType.RELEASE));
        BondedSuitService.markRescueReleased(human);session.body.setTarget(null);session.body.getNavigation().stop();
        sync(session,true);tell(session,"released");return true;
    }
    private static boolean ownsTemporaryForm(ServerPlayer human,String form) {
        return ProcessTransfur.getPlayerTransfurVariantSafe(human).map(variant->variant.isTemporaryFromSuit()&&variant.getFormId().toString().equals(form)).orElse(false);
    }
    private static void restoreHumanForm(ServerPlayer human,String form) {
        // An external administrator's permanent change takes precedence.
        if(ownsTemporaryForm(human,form))ProcessTransfur.removePlayerTransfurVariant(human);
    }
    public static void action(ServerPlayer human,UUID id,int action,int key,int sequence) {
        Session session=ACTIVE.get(human.getUUID());if(session==null||!session.id.equals(id))return;
        long now=human.server.overworld().getGameTime();
        if(action==TakeoverActionPacket.NEGOTIATE&&session.phase==TakeoverStatePacket.CONTROLLED&&!session.negotiationUsed&&ChangedSynergyConfig.COMMON.takeoverNegotiation.get()) {
            session.negotiationUsed=true;var pair=PlayerFriendshipData.get(human.server).find(human.getUUID(),session.owner.getUUID());
            if(human.getRandom().nextFloat()<0.35F+(pair==null?0:pair.familiarity)/120.0F){tell(session,"agreed");release(human);return;}
            session.until=Math.min(session.started+session.total*3/2,session.until+200);tell(session,"refused");
        } else if(action==TakeoverActionPacket.CONFIRM_STRUGGLE&&!session.escapeUsed&&session.phase==TakeoverStatePacket.CONTROLLED)session.confirmed=true;
        else if(action==TakeoverActionPacket.START_STRUGGLE&&session.confirmed&&!session.escapeUsed&&session.phase==TakeoverStatePacket.CONTROLLED&&ChangedSynergyConfig.COMMON.takeoverEscape.get()) {
            session.escapeUsed=true;session.phase=TakeoverStatePacket.STRUGGLE;session.qteUntil=now+200;session.key=human.getRandom().nextInt(4);
        } else if(action==TakeoverActionPacket.QTE_INPUT&&session.phase==TakeoverStatePacket.STRUGGLE&&sequence==session.sequence&&key>=0&&key<4&&now>session.lastInput) {
            session.lastInput=now;if(key==session.key)session.progress++;else session.progress=Math.max(0,session.progress-1);
            session.sequence++;session.key=human.getRandom().nextInt(4);
            if(session.progress>=12){release(human);return;}
        } sync(session,false);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)public static void target(LivingChangeTargetEvent event) {
        if(event.getEntity() instanceof ChangedEntity body&&TrueTransfurCompat.isInstinctBody(body)&&event.getNewTarget() instanceof ServerPlayer human) {
            var owner=TrueTransfurCompat.bodyOwner(body);if(owner!=null&&NativeRoleplayService.active(owner)&&!canTarget(body,human))event.setCanceled(true);
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)public static void damage(LivingAttackEvent event) {
        Session session=event.getEntity() instanceof ServerPlayer human?ACTIVE.get(human.getUUID()):null;
        if(session!=null&&(event.getSource().getEntity() instanceof ChangedEntity
                ||event.getSource().getEntity() instanceof net.minecraft.world.entity.player.Player attacker&&ProcessTransfur.isPlayerLatex(attacker)))event.setCanceled(true);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        for(Session session:ACTIVE.values().toArray(Session[]::new)) {
            long now=event.getServer().overworld().getGameTime();
            if(!session.human.isAlive()||!session.body.isAlive()||!session.owner.isAlive()||session.body.isRemoved()
                    ||session.owner.isSpectator()||session.body.isPassenger()||session.body.isSleeping()||session.human.isPassenger()||session.human.isSleeping()
                    ||session.human.isSpectator()||session.human.isCreative()||!ownsTemporaryForm(session.human,session.form)
                    ||event.getServer().getPlayerList().getPlayer(session.human.getUUID())!=session.human
                    ||session.owner.connection==null||event.getServer().getPlayerList().getPlayer(session.owner.getUUID())!=session.owner
                    ||TrueTransfurCompat.interactionBody(session.owner)!=session.body||!TrueTransfurCompat.isInstinctShell(session.owner)
                    ||!NativeRoleplayService.active(session.owner)||!ChangedSynergyConfig.COMMON.instinctConfinement.get()
                    ||!ChangedSynergyConfig.COMMON.takeoverEnabled.get()||!Changed.config.server.isGrabEnabled.get()
                    ||!session.owner.server.isPvpAllowed()||!session.owner.canHarmPlayer(session.human)
                    ||PlayerFriendshipData.friends(session.owner,session.human)||!ChangedSynergyGameRules.enabled(session.body.level(),ChangedSynergyGameRules.NPC_AI)
                    ||session.grab.grabbedEntity!=session.human||session.human.level()!=session.body.level()||now>=session.until) {release(session.human);continue;}
            session.grab.suited=true;session.grab.grabbedHasControl=false;session.grab.attackDown=session.grab.useDown=false;
            // Native GrabOverlay uses this strength as its bottom remaining-time bar.
            session.grab.grabStrength=(float)(session.until-now)/(session.until-session.started);
            ((GrabAbilityTick)session.grab).changedSynergy$tickHoldIfNeeded();
            if(session.body.getTarget()==session.human)session.body.setTarget(null);
            if(session.phase==TakeoverStatePacket.STRUGGLE&&now>=session.qteUntil){session.phase=TakeoverStatePacket.CONTROLLED;tell(session,"escape_failed");}
            if(event.getServer().getTickCount()%5==0)sync(session,false);
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        for(Session session:ACTIVE.values().toArray(Session[]::new))if(session.human==event.getEntity()||session.owner==event.getEntity())release(session.human);
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        for(Session session:ACTIVE.values().toArray(Session[]::new)) {
            UUID id=event.getEntity().getUUID();
            if(!session.human.getUUID().equals(id)&&!session.owner.getUUID().equals(id))continue;
            release(session.human);
            if(event.getEntity() instanceof ServerPlayer human&&session.human.getUUID().equals(id)) {
                restoreHumanForm(human,session.form);human.noPhysics=false;
                human.getPersistentData().remove(RECOVERY);human.getPersistentData().remove(SUIT_FORM);
                human.getPersistentData().putLong(GRACE,human.server.overworld().getGameTime()+1200);
                if(human instanceof net.ltxprogrammer.changed.entity.LivingEntityDataExtension extension)extension.setGrabbedBy(null);
            }
        }
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if(event.getEntity() instanceof ServerPlayer human&&human.getPersistentData().hasUUID(RECOVERY)) {
            restoreHumanForm(human,human.getPersistentData().getString(SUIT_FORM));human.getPersistentData().remove(SUIT_FORM);
            human.getPersistentData().remove(RECOVERY);human.noPhysics=false;human.getPersistentData().putLong(GRACE,human.server.overworld().getGameTime()+1200);
            if(human instanceof net.ltxprogrammer.changed.entity.LivingEntityDataExtension extension)extension.setGrabbedBy(null);
        }
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {for(Session session:ACTIVE.values().toArray(Session[]::new))release(session.human);}
}
