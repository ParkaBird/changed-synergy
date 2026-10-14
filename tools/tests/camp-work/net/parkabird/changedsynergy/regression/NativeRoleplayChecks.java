package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.event.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.ModList;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import net.parkabird.changedsynergy.dialogue.*;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.network.*;
import net.parkabird.changedsynergy.origin.*;

/** Real TT body, actual player menus, server events and encoded per-recipient packets. Private fixture. */
final class NativeRoleplayChecks {
    private static int checks;
    private static final class Sink extends Connection {
        final List<Packet<?>> packets=new ArrayList<>();
        Sink(){super(PacketFlow.SERVERBOUND);}
        @Override public void send(Packet<?> packet){packets.add(packet);}
        @Override public void send(Packet<?> packet,PacketSendListener callback){packets.add(packet);}
    }
    private static final class Listener extends ServerGamePacketListenerImpl {
        final List<Packet<?>> packets;
        Listener(ServerPlayer player) { this(player,new Sink()); }
        Listener(ServerPlayer player,Sink sink) { super(player.server,sink,player);packets=sink.packets;sink.setListener(this); }
        @Override public void send(Packet<?> packet) { packets.add(packet); }
        Object last(int discriminator) {
            for(int i=packets.size()-1;i>=0;i--) if(packets.get(i) instanceof ClientboundCustomPayloadPacket packet
                    &&packet.getIdentifier().toString().equals("changed_synergy:network")) {
                FriendlyByteBuf data=new FriendlyByteBuf(packet.getData().copy());
                try { if(data.readVarInt()!=discriminator)continue;
                    return discriminator==8?TelepathyDialoguePacket.decode(data):TakeoverStatePacket.decode(data);
                } finally {data.release();}
            } return null;
        }
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    static void run(MinecraftServer server) throws Exception {
        checks=0;var level=server.overworld();var cfg=ChangedSynergyConfig.COMMON;
        var field=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(PlayerList.class, "f_11197_");field.setAccessible(true);
        var players=(Map<UUID,ServerPlayer>)field.get(server.getPlayerList());
        var listField=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(PlayerList.class, "f_11196_");listField.setAccessible(true);
        var list=(List<ServerPlayer>)listField.get(server.getPlayerList());
        var created=new ArrayList<ServerPlayer>();
        ChangedEntity body=null;Object playerMode=null;java.lang.reflect.Method changeMode=null;
        boolean oldRule=level.getGameRules().getBoolean(ChangedSynergyGameRules.NATIVE_ORIGINS),oldPvp=server.isPvpAllowed();
        boolean oldTelepathy=cfg.telepathyEnabled.get();
        try {
            level.getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(true,server);server.setPvpAllowed(true);
            for(BlockPos pos:BlockPos.betweenClosed(new BlockPos(72,89,72),new BlockPos(88,89,88)))level.setBlock(pos,Blocks.STONE.defaultBlockState(),3);
            for(BlockPos pos:BlockPos.betweenClosed(new BlockPos(72,90,72),new BlockPos(88,94,88)))level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
            for(String account:List.of("RoleNative","RoleHuman","RoleDistant")) {
                var user=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),account));
                user.connection=new Listener(user);user.setPos(80.5+created.size(),90,80.5);user.setHealth(user.getMaxHealth());
                players.put(user.getUUID(),user);list.add(user);level.addNewPlayer(user);created.add(user);
            }
            var owner=created.get(0);var human=created.get(1);var distant=created.get(2);distant.setPos(140.5,90,80.5);
            var humanOrigin=new NativeOriginData.Identity();humanOrigin.state="human";NativeOriginData.get(server).players.put(human.getUUID(),humanOrigin);
            var identity=new NativeOriginData.Identity();identity.state="native";identity.form=identity.current=ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.getId().toString();identity.faction="dark";
            NativeOriginData.get(server).players.put(owner.getUUID(),identity);
            NativeRoleplayService.ensureName(owner);String generated=identity.roleName;
            check(!generated.isBlank(),"Old native player obtains a species name");NativeRoleplayService.ensureName(owner);
            check(generated.equals(identity.roleName),"Repeated login/name reconciliation does not reroll identity");
            var restored=NativeOriginData.load(NativeOriginData.get(server).save(new CompoundTag())).players.get(owner.getUUID());
            check(restored.roleName.equals(generated)&&restored.state.equals("native"),"Name and birth identity survive world serialization");
            check(!NativeRoleplayService.validName("bad§a")&&!NativeRoleplayService.validName("x\n")&&!NativeRoleplayService.validName("x".repeat(25))
                    &&NativeRoleplayService.validName("猫".repeat(24)),"Unicode length and formatting validation");
            check(NativeRoleplayService.rename(owner,"River")==1&&owner.getGameProfile().getName().equals("RoleNative"),"Rename retains account identity");
            var friendData=PlayerFriendshipData.get(server);
            PlayerSocialService.act(human,owner.getUUID(),owner,"social_friend");
            check(!PlayerFriendshipData.friends(owner,human),"Invitation alone does not create friendship");
            check(PlayerSocialService.answerFriend(owner,human.getUUID(),true)==1&&PlayerFriendshipData.friends(owner,human),"Mutual consent creates friendship at zero familiarity");
            check(PlayerSocialService.answerFriend(owner,human.getUUID(),true)==0,"Invitation cannot be replayed");
            check(friendData.care(owner,human,false)==2&&friendData.care(human,owner,false)==0,"Both players share a care cooldown");
            check(friendData.care(human,owner,true)==2,"Gift growth is independent of care cooldown");
            var pair=friendData.find(owner.getUUID(),human.getUUID());long giftAt=pair.giftAt,careAt=pair.careAt;
            human.getPersistentData().remove("SynergyNextPlayerSocialAction");human.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.ORANGE_DYE,3));
            PlayerSocialService.act(human,owner.getUUID(),owner,"social_gift");
            check(human.getMainHandItem().getCount()==2&&owner.getInventory().countItem(Items.ORANGE_DYE)==1&&pair.familiarity==4,"Cooldown stops familiarity gain, not real gift transfer");
            friendData.remove(owner.getUUID(),human.getUUID());friendData.establish(owner,human);
            check(pair.familiarity==4&&pair.giftAt==giftAt&&pair.careAt==careAt,"Re-adding friendship cannot refresh rewards");
            var savedPair=PlayerFriendshipData.load(friendData.save(new CompoundTag())).find(owner.getUUID(),human.getUUID());
            check(savedPair.friends&&savedPair.careAt==careAt&&savedPair.giftAt==giftAt,"Shared relationship state survives restart");
            friendData.care(owner,distant,true);var unfamiliar=friendData.find(owner.getUUID(),distant.getUUID());
            unfamiliar.familiarity=8;unfamiliar.giftAt=0;
            check(friendData.care(owner,distant,true)==2&&!unfamiliar.friends,"Player familiarity has no NPC first-gift acquaintance gate");
            ProcessTransfur.setPlayerTransfurVariant(owner,ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get(),null,1,false);
            organicDamage(owner,human);
            cfg.telepathyEnabled.set(false);clear(owner,human,distant);
            String secret="PRIVATE %1$s {player} menu.key";
            check(NativeRoleplayService.speak(owner,secret),"Native speech uses the physical player's connection");
            check(((TelepathyDialoguePacket)((Listener)owner.connection).last(8)).dialogue().getString().equals(secret),"Speaker sees literal input without placeholder parsing");
            var masked=(TelepathyDialoguePacket)((Listener)human.connection).last(8);
            check(masked!=null&&!net.minecraft.network.chat.Component.Serializer.toJson(masked.message()).contains("PRIVATE")
                    &&!net.minecraft.network.chat.Component.Serializer.toJson(masked.dialogue()).contains("PRIVATE"),"Uncomprehending listener receives no hidden original");
            check(((Listener)distant.connection).last(8)==null,"Role speech stays inside dialogue range");
            check(!NativeRoleplayService.speak(owner,"too soon"),"Player speech has an independent server rate limit");
            owner.getPersistentData().remove("SynergyRoleSpeechAt");clear(owner,human,distant);
            var cancelled=new ServerChatEvent(owner,"blocked",net.minecraft.network.chat.Component.literal("blocked"));cancelled.setCanceled(true);
            NativeRoleplayService.onChat(cancelled);
            check(((Listener)human.connection).last(8)==null,"Previously moderated/cancelled chat stays cancelled");
            NativeRoleplayService.chatMode(owner,true);var global=new ServerChatEvent(owner,"global",net.minecraft.network.chat.Component.literal("global"));NativeRoleplayService.onChat(global);
            check(!global.isCanceled(),"Global mode retains normal signed chat path");NativeRoleplayService.chatMode(owner,false);
            cfg.playerRoleSpeech.set(false);check(!NativeRoleplayService.roleChat(owner),"Runtime speech switch restores ordinary chat");cfg.playerRoleSpeech.set(true);
            check(NativeRoleplayService.emote(owner,net.ltxprogrammer.changed.entity.Emote.CASUAL,true)
                    &&!NativeRoleplayService.emote(owner,net.ltxprogrammer.changed.entity.Emote.ANGRY,true),"Manual expressions share a three-second cooldown");
            if(ModList.get().isLoaded("true_transfur")) {
                var controller=Class.forName("com.lugert.true_transfur.transfur.TransfurController");Class mode=Class.forName("com.lugert.true_transfur.transfur.ControlMode");
                changeMode=controller.getMethod("switchControlMode",ServerPlayer.class,mode);playerMode=Enum.valueOf(mode,"PLAYER");
                Capability cap=(Capability)Class.forName("com.lugert.true_transfur.transfur.TransfurCapabilities").getField("TRANSFUR_DATA").get(null);
                Object state=owner.getCapability(cap).orElse(null);
                if(state==null)throw new IllegalStateException("Missing TT player data");
                state.getClass().getMethod("setTransfurred",boolean.class).invoke(state,true);
                check(Boolean.TRUE.equals(changeMode.invoke(null,owner,Enum.valueOf(mode,"INSTINCT"))),"Use the currently installed TT's actual INSTINCT mode");
                body=(ChangedEntity)TrueTransfurCompat.interactionBody(owner);body.setNoAi(true);body.setPos(80.5,90,80.5);body.setYRot(0);body.setXRot(0);
                check(TrueTransfurCompat.isInstinctBody(body)&&NativeRoleplayService.name(owner).getString().equals("River"),"Replacing the body retains the role name");
                NativeRoleplayService.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,owner));
                check(!owner.getPersistentData().contains("ChangedSynergyEmotion"),"Mode switch clears the previous player-body expression");
                check(!InstinctConfinementService.canTarget(body,human),"Confirmed friends are not targets for autonomous attack");
                friendData.remove(owner.getUUID(),human.getUUID());
                check(!InstinctConfinementService.canTarget(body,human),"Low familiarity alone does not justify attack");
                var sample=net.ltxprogrammer.changed.init.ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);FactionReputation.set(sample,human,-70);
                check(InstinctConfinementService.canTarget(body,human),"Human's hostile standing is checked against native birth faction");
                var accepts=Class.forName("com.lugert.true_transfur.transfur.InstinctRuntime").getMethod("canAcceptTarget",ChangedEntity.class,net.minecraft.world.entity.LivingEntity.class);
                check(Boolean.TRUE.equals(accepts.invoke(null,body,human)),"Actual TT target policy allows only eligible native-to-human conflict");
                server.setPvpAllowed(false);check(!InstinctConfinementService.canTarget(body,human),"Server PvP denial prevents native attacks");server.setPvpAllowed(true);
                human.setPos(80.5,90,82.0);ProcessTransfur.setPlayerTransfurProgress(human,(float)ProcessTransfur.getEntityTransfurTolerance(human));level.setBlock(new BlockPos(80,90,81),Blocks.STONE.defaultBlockState(),3);level.setBlock(new BlockPos(80,91,81),Blocks.STONE.defaultBlockState(),3);
                check(!InstinctConfinementService.begin(body,human),"Solid wall prevents restraint even at close range");level.setBlock(new BlockPos(80,91,81),Blocks.AIR.defaultBlockState(),3);level.setBlock(new BlockPos(80,90,81),Blocks.AIR.defaultBlockState(),3);
                ProcessTransfur.setPlayerTransfurProgress(human,0);
                check(!InstinctConfinementService.begin(body,human),"Contact alone cannot bypass transfur progress");
                float health=human.getHealth();human.invulnerableTime=0;
                check(body.doHurtTarget(human)&&ProcessTransfur.getPlayerTransfurProgress(human)>0
                        &&human.getHealth()==health&&!InstinctConfinementService.active(human),"Actual instinct melee applies transfur progress rather than health damage");
                clear(owner,human,distant);
                finishTransfur(body,human);check(InstinctConfinementService.active(human),"Final normal swipe completes the temporary takeover");
                var grab=BondedSuitService.ability(body);check(grab.grabbedEntity==human&&grab.suited&&ProcessTransfur.getPlayerTransfurVariantSafe(human).map(v->v.isTemporaryFromSuit()).orElse(false),"Takeover uses native SUIT and a temporary victim form");
                grab.attackDown=grab.useDown=true;grab.grabStrength=0.01F;grab.tickIdle();
                check(ProcessTransfur.getPlayerTransfurVariantSafe(human).map(v->v.isTemporaryFromSuit()).orElse(false)&&InstinctConfinementService.active(human),"Native absorption inputs cannot make the human permanent or erase the carrier");
                check(TakeoverService.hidesPlayer(human)&&TakeoverService.controllingCarrier(human)==body&&TakeoverService.contains(body,human),"All existing capture guards recognise the instinct takeover");
                var intruder=net.ltxprogrammer.changed.init.ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);intruder.setPos(human.position());
                intruder.setTarget(human);check(intruder.getTarget()==null,"Another latex cannot target the contained human");
                check(intruder.doHurtTarget(human)&&human.getHealth()==health&&ProcessTransfur.getPlayerTransfurProgress(human)==0,"Another latex cannot damage or add transfur progress to the contained human");
                BondedSuitService.ensureGrabAbility(intruder);var otherGrab=BondedSuitService.ability(intruder);
                check(!otherGrab.grabEntity(human)&&!otherGrab.suitEntity(human),"Another latex cannot grab or steal the takeover");
                var direct=intruder.makeLatexAssimilationDecision(net.ltxprogrammer.changed.entity.TransfurCause.GRAB_REPLICATE,human);
                check(direct==null||ProcessTransfur.computeAssimilationBehavior(human,direct)==null,"Direct assimilation also rejects the hidden human");
                var attack=new net.minecraftforge.event.entity.living.LivingAttackEvent(human,human.damageSources().mobAttack(intruder),4);
                MinecraftForge.EVENT_BUS.post(attack);check(attack.isCanceled(),"Stale melee damage from another latex is cancelled");
                var ownerPacket=(TakeoverStatePacket)((Listener)owner.connection).last(20);
                check(ownerPacket!=null&&ownerPacket.carrierId()==body.getId()&&ownerPacket.playerId()==human.getId(),"Technical owner receives the body countdown packet directly");
                check(!TrueTransfurCompat.isPermanentPlayer(human),"Temporary suit does not become TT permanent transfur");
                grab.releaseEntity(true);check(grab.grabbedEntity==human,"External native release cannot break an active server session");
                var packet=(TakeoverStatePacket)((Listener)human.connection).last(20);
                if(packet==null)throw new AssertionError("Missing takeover packet; check channel discriminator");
                InstinctConfinementService.action(human,UUID.randomUUID(),TakeoverActionPacket.CONFIRM_STRUGGLE,-1,0);
                InstinctConfinementService.action(human,packet.sessionId(),TakeoverActionPacket.START_STRUGGLE,-1,0);
                check(((TakeoverStatePacket)((Listener)human.connection).last(20)).phase()==TakeoverStatePacket.CONTROLLED,
                        "A forged session ID cannot confirm a struggle");
                InstinctConfinementService.action(distant,packet.sessionId(),TakeoverActionPacket.NEGOTIATE,-1,0);
                check(!((TakeoverStatePacket)((Listener)human.connection).last(20)).negotiationUsed(),
                        "Another player cannot operate the victim's takeover session");
                InstinctConfinementService.action(human,packet.sessionId(),TakeoverActionPacket.CONFIRM_STRUGGLE,-1,0);
                InstinctConfinementService.action(human,packet.sessionId(),TakeoverActionPacket.START_STRUGGLE,-1,0);
                var qte=(TakeoverStatePacket)((Listener)human.connection).last(20);check(qte.phase()==TakeoverStatePacket.STRUGGLE,"Human uses existing takeover WASD QTE");
                InstinctConfinementService.action(human,qte.sessionId(),TakeoverActionPacket.QTE_INPUT,qte.expectedKey(),qte.sequence());
                var progressed=(TakeoverStatePacket)((Listener)human.connection).last(20);
                InstinctConfinementService.action(human,qte.sessionId(),TakeoverActionPacket.QTE_INPUT,qte.expectedKey(),qte.sequence());
                check(((TakeoverStatePacket)((Listener)human.connection).last(20)).progress()==progressed.progress(),"Duplicate QTE packets cannot add progress");
                advance(server,201);check(InstinctConfinementService.active(human)&&ProcessTransfur.getPlayerTransfurVariantSafe(human).map(v->v.isTemporaryFromSuit()).orElse(false)&&human.isAlive(),"Failed escape continues temporary takeover without death or permanent form change");
                check(grab.grabStrength>0&&grab.grabStrength<0.8F,"Native bottom bar receives the remaining-time fraction");
                check(Boolean.TRUE.equals(changeMode.invoke(null,owner,playerMode)),"TT can return to PLAYER during restraint");endTick(server);
                check(!InstinctConfinementService.active(human)&&!human.noPhysics&&grab.grabbedEntity==null&&!ProcessTransfur.isPlayerTransfurred(human),"Mode switch clears session, grab, temporary form and physics lock");
                check(human.distanceToSqr(new net.minecraft.world.phys.Vec3(80.5,90,80.5))<25,"Release occurs locally rather than at world spawn");
                check(ProcessTransfur.getPlayerTransfurVariantSafe(owner).isPresent()&&identity.roleName.equals(NativeOriginService.identity(owner).roleName),"Carrier's permanent form and name remain intact");
                check(Boolean.TRUE.equals(changeMode.invoke(null,owner,Enum.valueOf(mode,"INSTINCT"))),"Re-enter INSTINCT without changing identity");
                body=(ChangedEntity)TrueTransfurCompat.interactionBody(owner);body.setNoAi(true);body.setPos(human.getX(),human.getY(),human.getZ()-1);
                check(!InstinctConfinementService.begin(body,human),"Released human has at least sixty seconds of restraint grace");
                human.getPersistentData().remove("SynergyInstinctRestraintGrace");finishTransfur(body,human);check(InstinctConfinementService.active(human),"New completed transfur can begin after grace ends");
                cfg.instinctConfinement.set(false);endTick(server);
                check(!InstinctConfinementService.active(human)&&!human.noPhysics,"Runtime-off immediately releases an active restraint");cfg.instinctConfinement.set(true);
                human.getPersistentData().remove("SynergyInstinctRestraintGrace");body.setNoAi(false);body.setPos(80.5,90,75.5);body.setOnGround(true);
                human.setPos(80.5,90,80.5);body.setTarget(human);ProcessTransfur.setPlayerTransfurProgress(human,0);
                addonGrabAdmission(body,human);
                int windups=0,lostTargets=0;
                float beforeChaseHealth=human.getHealth();
                for(int chase=0;chase<160;chase++) {
                    // Follow a briefly moving target instead of only standing at melee range.
                    if(chase<40)human.setPos(80.5+chase*.03,90,80.5);
                    CampNavigationChecks.tick(level,owner,List.of(body),1);
                    if(!InstinctConfinementService.active(human)) {
                        if(body.getTarget()!=human)lostTargets++;
                        if(BondedSuitService.ability(body).getController().getHoldTicks()>0)windups++;
                    }
                }endTick(server);
                check(lostTargets==0&&windups==0,"Moving-human pursuit retains its target without repeated grab wind-ups");
                check(human.getHealth()==beforeChaseHealth,"Pursuit builds transfur progress without direct human health damage");
                check(InstinctConfinementService.active(human),"Real TT melee AI approaches, adds transfur progress and completes takeover");
                clear(owner,human,distant);InstinctConfinementService.release(human);human.getPersistentData().remove("SynergyInstinctRestraintGrace");
                body.setNoAi(true);body.setPos(80.5,90,79.5);human.setPos(80.5,90,80.5);
                finishTransfur(body,human);check(InstinctConfinementService.active(human),"A second completed session uses a new nonce");
                var timed=(TakeoverStatePacket)((Listener)human.connection).last(20);
                // Zero familiar history and a controlled random source force the failed negotiation branch.
                human.getRandom().setSeed(0);
                InstinctConfinementService.action(human,timed.sessionId(),TakeoverActionPacket.NEGOTIATE,-1,0);
                var negotiated=(TakeoverStatePacket)((Listener)human.connection).last(20);
                if(InstinctConfinementService.active(human)) {
                    check(negotiated.releaseTotalTicks()<=timed.releaseTotalTicks()*3/2&&negotiated.releaseRemainingTicks()>timed.releaseRemainingTicks(),"Failed negotiation extends the timer within its cap");
                    InstinctConfinementService.action(human,timed.sessionId(),TakeoverActionPacket.NEGOTIATE,-1,0);
                    check(((TakeoverStatePacket)((Listener)human.connection).last(20)).releaseTotalTicks()==negotiated.releaseTotalTicks(),"Only one negotiation is permitted");
                    advance(server,negotiated.releaseRemainingTicks()+1);check(!InstinctConfinementService.active(human)&&!human.noPhysics,"Countdown ends in a safe release");
                }
                human.getPersistentData().remove("SynergyNextPlayerSocialAction");human.getPersistentData().remove("SynergyNextPlayerFriendInvite");
                human.getPersistentData().remove("SynergyInstinctRestraintGrace");
                body.setPos(human.getX(),human.getY(),human.getZ()-1);finishTransfur(body,human);
                var clone=new ServerPlayer(server,level,human.getGameProfile());clone.connection=new Listener(clone);
                ProcessTransfur.setPlayerTransfurVariant(clone,body.getSelfVariant(),null,1,true);
                InstinctConfinementService.clone(new PlayerEvent.Clone(clone,human,true));
                check(!InstinctConfinementService.active(human)&&!ProcessTransfur.isPlayerTransfurred(clone)&&!clone.noPhysics,
                        "Respawn cloning cannot carry a temporary suit or input lock into the new player entity");
                clone.discard();
                human.getPersistentData().remove("SynergyInstinctRestraintGrace");
                finishTransfur(body,human);
                check(InstinctConfinementService.active(human),"Capture exists before external administrator form change");
                var externalForm=ProcessTransfur.setPlayerTransfurVariant(human,ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get(),null,1,false);
                externalForm.setTemporaryForSuit(false);endTick(server);
                check(!InstinctConfinementService.active(human)&&!human.noPhysics&&BondedSuitService.ability(body).grabbedEntity==null
                        &&ProcessTransfur.getPlayerTransfurVariant(human)==externalForm,
                        "Ending takeover preserves an administrator's permanent form even with the same species ID");
                externalForm.setTemporaryForSuit(true);human.noPhysics=true;
                human.getPersistentData().putUUID("SynergyInstinctRestraint",UUID.randomUUID());
                human.getPersistentData().putString("SynergyInstinctSuitForm",externalForm.getFormId().toString());
                InstinctConfinementService.login(new PlayerEvent.PlayerLoggedInEvent(human));
                check(!ProcessTransfur.isPlayerTransfurred(human)&&!human.noPhysics
                        &&!human.getPersistentData().contains("SynergyInstinctRestraint")
                        &&!human.getPersistentData().contains("SynergyInstinctSuitForm"),
                        "Login recovers persisted temporary takeover without a live session after restart");
                InstinctConfinementService.login(new PlayerEvent.PlayerLoggedInEvent(human));
                check(!ProcessTransfur.isPlayerTransfurred(human)&&!human.noPhysics,"Repeated recovery is safe and idempotent");
                var beforeFriend=creatureIds(level);
                PlayerSocialService.act(human,owner.getUUID(),body,"social_friend");
                check(PlayerSocialService.answerFriend(owner,human.getUUID(),true)==1,"Friend invitation can be accepted from the real account while in INSTINCT");
                for(int i=0;i<40;i++)MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,owner));
                check(creatureIds(level).equals(beforeFriend)&&TrueTransfurCompat.interactionBody(owner)==body,
                        "Befriending an actual INSTINCT player never spawns another server creature or replaces its body");
                check(!body.isCustomNameVisible()&&!CreatureSocialProfile.allowsPersonalRelationship(body),
                        "Player body remains nameless overhead and cannot obtain ordinary NPC relationship state");
                check(PlayerRelationshipSettings.contacts(owner).stream().anyMatch(contact->contact.uuid().equals(human.getUUID())&&!contact.bonded()&&contact.typeId().equals("minecraft:player")),"Confirmed player friend appears separately from NPC bonds");
                PlayerSocialService.requestFollow(human,owner);check(PlayerSocialService.isFollowing(owner.getUUID(),human.getUUID()),"Confirmed friend's INSTINCT follow request is automatically accepted");
                humanInstinctOnly(human);
            }
            friendData.remove(owner.getUUID(),human.getUUID());
            var contacts=PlayerRelationshipSettings.contacts(owner);
            // Unfriending kept familiarity but deliberately removed this player contact.
            check(contacts.stream().noneMatch(contact->contact.uuid().equals(human.getUUID())),"Unfriending removes player contact without NPC bond mutation");
            System.out.println("NATIVE_ROLEPLAY_REGRESSION_PASS checks="+checks+" tt="+ModList.get().isLoaded("true_transfur"));
        } finally {
            cfg.telepathyEnabled.set(oldTelepathy);server.setPvpAllowed(oldPvp);
            if(!created.isEmpty()&&changeMode!=null&&TrueTransfurCompat.isInstinctShell(created.get(0)))changeMode.invoke(null,created.get(0),playerMode);
            for(var user:created) {if(InstinctConfinementService.active(user))InstinctConfinementService.release(user);
                NativeRoleplayService.logout(new PlayerEvent.PlayerLoggedOutEvent(user));PlayerSocialService.onLogout(new PlayerEvent.PlayerLoggedOutEvent(user));
                players.remove(user.getUUID());list.remove(user);user.discard();NativeOriginData.get(server).players.remove(user.getUUID());}
            level.getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(oldRule,server);
        }
    }
    private static void finishTransfur(ChangedEntity body,ServerPlayer human) {
        human.invulnerableTime=0;human.hurtTime=0;
        ProcessTransfur.setPlayerTransfurProgress(human,(float)ProcessTransfur.getEntityTransfurTolerance(human)-0.01F);
        body.tryTransfurTarget(human);
    }
    private static Set<UUID> creatureIds(net.minecraft.server.level.ServerLevel level) {
        var ids=new HashSet<UUID>();for(var entity:level.getAllEntities())if(entity instanceof ChangedEntity)ids.add(entity.getUUID());return ids;
    }
    private static void addonGrabAdmission(ChangedEntity body,ServerPlayer human) throws Exception {
        if(!ModList.get().isLoaded("changed_addon"))return;
        var api=Class.forName("net.foxyas.changedaddon.entity.api.IGrabberEntity");
        api.getMethod("setCanUseGrab",boolean.class).invoke(body,true);
        check(Boolean.TRUE.equals(api.getMethod("canEntityGrab",net.minecraft.world.entity.EntityType.class,net.minecraft.world.level.Level.class)
                .invoke(body,body.getType(),body.level())),"Test body has real Addon grab permission");
        body.baseTick();body.setTarget(human);
        var position=body.position();body.setPos(human.getX(),human.getY(),human.getZ()-1);
        var grab=(net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance)api.getMethod("getGrabAbilityInstance").invoke(body);
        check(grab!=null,"Addon grab ability is initialized on the real proxy");
        int goals=0;
        for(var wrapped:body.goalSelector.getAvailableGoals()) {
            var goal=wrapped.getGoal();String name=goal.getClass().getSimpleName();
            if(name.equals("MayGrabTargetGoal")||name.equals("LungeAndGrabTargetGoal")) {
                check(!goal.canUse(),"Addon "+name+" cannot interrupt human transfur pursuit");goals++;
            }
        }
        check(goals==1,"The body's raw Addon grab goal is exercised");
        var lunge=(net.minecraft.world.entity.ai.goal.Goal)Class.forName("net.foxyas.changedaddon.entity.ai.goals.abilities.LungeAndGrabTargetGoal")
                .getConstructor(api).newInstance(body);
        body.setPos(human.getX(),human.getY(),human.getZ()-5);body.setOnGround(true);
        check(!lunge.canUse(),"Optional Addon lunge cannot interrupt human transfur pursuit");
        grab.getController().resetHoldTicks();body.setPos(position);body.setOnGround(true);
    }
    private static void organicDamage(ServerPlayer nativePlayer,ServerPlayer human) {
        var cfg=ChangedSynergyConfig.COMMON;boolean previous=cfg.allowSecondaryTransfur.get();
        var organic=net.ltxprogrammer.changed.init.ChangedEntities.GAS_WOLF_MALE.get().create(human.serverLevel());
        organic.setNoAi(true);organic.setPos(human.position());
        try {
            cfg.allowSecondaryTransfur.set(true);FactionReputation.set(organic,human,-70);
            for(int i=0;i<61;i++)human.tick(); // End the real ServerPlayer's initial spawn immunity.
            check(LatexSocialMemory.isOrganic(organic)&&LatexSocialMemory.isSocialLatex(organic),"Real organic wolf participates in organic combat rules");
            human.setHealth(20);human.invulnerableTime=0;
            human.hurt(human.damageSources().mobAttack(organic),6);
            check(human.getHealth()==19,"Human organic melee retains its one-point damage cap");
            ProcessTransfur.setPlayerTransfurVariant(human,ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get(),null,1,false);
            human.setHealth(human.getMaxHealth());human.invulnerableTime=0;float health=human.getHealth();
            var vanilla=net.minecraft.world.entity.EntityType.ZOMBIE.create(human.serverLevel());
            human.hurt(human.damageSources().mobAttack(vanilla),6);float expected=health-human.getHealth();
            human.setHealth(health);human.invulnerableTime=0;
            boolean hit=human.hurt(human.damageSources().mobAttack(organic),6);vanilla.discard();
            check(hit&&expected>1&&Math.abs(human.getHealth()-(health-expected))<.001F,"Transformed player receives the same ordinary damage as a vanilla melee attacker");
            human.setHealth(5);human.invulnerableTime=0;
            check(!OrganicCombatRules.shouldUseOnlyGrapple(organic,human)&&OrganicCombatRules.limitSwipeDamage(human,3)==3,
                    "Low-health creature player is not held at the human grapple-only floor");
            check(human.hurt(human.damageSources().mobAttack(organic),3)&&human.getHealth()<4,"Low-health creature can still receive ordinary organic damage");
            check(!OrganicCombatRules.isHumanTarget(nativePlayer),"Native origin never uses the human damage cap");
        } finally {
            ProcessTransfur.removePlayerTransfurVariant(human);human.setHealth(human.getMaxHealth());human.invulnerableTime=0;
            cfg.allowSecondaryTransfur.set(previous);organic.discard();
        }
    }
    private static void clear(ServerPlayer... users) {for(var user:users)((Listener)user.connection).packets.clear();}
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void humanInstinctOnly(ServerPlayer human) throws Exception {
        var cfg=ChangedSynergyConfig.COMMON;boolean old=cfg.allowHumanOriginInstinct.get();
        var identity=new NativeOriginData.Identity();identity.state="human";NativeOriginData.get(human.server).players.put(human.getUUID(),identity);
        var controller=Class.forName("com.lugert.true_transfur.transfur.TransfurController");
        var runtime=Class.forName("com.lugert.true_transfur.transfur.InstinctRuntime");
        Class mode=Class.forName("com.lugert.true_transfur.transfur.ControlMode");
        var change=controller.getMethod("switchControlMode",ServerPlayer.class,mode);
        Object instinct=Enum.valueOf(mode,"INSTINCT"),player=Enum.valueOf(mode,"PLAYER");
        try {
            check(!cfg.allowHumanOriginInstinct.get()&&!NativeOriginPolicy.allowsInstinct(human),"Human-origin Instinct is disabled by default");
            cfg.allowHumanOriginInstinct.set(true);
            ProcessTransfur.setPlayerTransfurVariant(human,ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get(),null,1,false);
            MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.START,human));
            check(!NativeOriginPolicy.allows(human)&&NativeOriginPolicy.allowsInstinct(human),"Control permission is separate from permanent identity");
            var setMode=Class.forName("com.lugert.true_transfur.transfur.TransfurEvents").getDeclaredMethod("setMode",ServerPlayer.class,mode);setMode.setAccessible(true);
            check((int)setMode.invoke(null,human,instinct)==1,"Human-origin player can enter Instinct through TT's actual debug command");
            var body=(ChangedEntity)TrueTransfurCompat.interactionBody(human);body.setNoAi(true);
            for(int i=0;i<30;i++) {
                MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.START,human));
                MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,human));
            }
            check(TrueTransfurCompat.isInstinctShell(human)&&TrueTransfurCompat.interactionBody(human)==body,"Human Instinct survives repeated real backend ticks");
            check(!Boolean.TRUE.equals(controller.getMethod("isTrueTransfurActive",ServerPlayer.class).invoke(null,human))
                    &&!Boolean.TRUE.equals(controller.getMethod("isChangedTransfurred",ServerPlayer.class).invoke(null,human))
                    &&!TrueTransfurCompat.blocksReversal(human)&&!NativeRoleplayService.active(human),"Control does not enable permanent transfur, reversal death or native roleplay");
            var reborn=Class.forName("com.lugert.true_transfur.transfur.RebornHandler");
            var death=new net.minecraftforge.event.entity.living.LivingDeathEvent(human,human.damageSources().generic());
            check(!Boolean.TRUE.equals(reborn.getMethod("interceptDeath",net.minecraftforge.event.entity.living.LivingDeathEvent.class,ServerPlayer.class).invoke(null,death,human)),"Human control does not enable native rebirth");
            check(Boolean.TRUE.equals(change.invoke(null,human,player)),"Human can return to Player through the original TT lifecycle");
            var contextClass=net.minecraftforge.network.NetworkEvent.Context.class;
            var ctor=contextClass.getDeclaredConstructor(Connection.class,net.minecraftforge.network.NetworkDirection.class,int.class);ctor.setAccessible(true);
            Object context=ctor.newInstance(human.connection.connection,net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER,0);
            var keyEntry=Class.forName("com.lugert.true_transfur.network.ToggleControlModePacket").getDeclaredMethod("lambda$handle$0",contextClass);keyEntry.setAccessible(true);keyEntry.invoke(null,context);
            check(TrueTransfurCompat.isInstinctShell(human),"TT's actual keyboard-toggle packet can re-enter Instinct without permanent identity");
            cfg.allowHumanOriginInstinct.set(false);MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.START,human));
            MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,human));
            check(!TrueTransfurCompat.isInstinctShell(human)&&!human.noPhysics&&ProcessTransfur.isPlayerTransfurred(human),"Runtime-off restores Player control and preserves the ordinary Changed form");
            cfg.allowHumanOriginInstinct.set(true);MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.START,human));
            check(Boolean.TRUE.equals(change.invoke(null,human,instinct)),"Re-enabling control restores the optional entry");
            ProcessTransfur.removePlayerTransfurVariant(human);
            MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.START,human));
            check(human.isAlive()&&!ProcessTransfur.isPlayerTransfurred(human)&&!TrueTransfurCompat.isInstinctShell(human)&&!human.noPhysics,"Human reversal remains safe during Instinct and clears its physical body");
        } finally {
            runtime.getMethod("exit",ServerPlayer.class).invoke(null,human);cfg.allowHumanOriginInstinct.set(old);
        }
    }
    private static void endTick(MinecraftServer server) { InstinctConfinementService.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END,()->true,server)); }
    private static void advance(MinecraftServer server,int ticks) {var level=server.overworld();((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+ticks);endTick(server);}
    private static void check(boolean condition,String message) {checks++;if(!condition)throw new AssertionError(message);}
}
