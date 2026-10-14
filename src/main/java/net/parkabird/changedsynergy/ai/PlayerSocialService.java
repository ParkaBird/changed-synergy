package net.parkabird.changedsynergy.ai;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.PlayerBodyPacket;
import net.parkabird.changedsynergy.network.ScoutTargetPacket;
import net.parkabird.changedsynergy.world.inventory.SocialInteractionMenu;

/** Player interactions use player identities, never NPC memories or inventories. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class PlayerSocialService {
    private static final Map<UUID, Invitation> INVITATIONS = new HashMap<>();
    private static final Map<UUID, FollowLink> FOLLOWING = new HashMap<>();
    private static final Map<UUID, Invitation> FRIEND_INVITATIONS = new HashMap<>();
    private static final Map<UUID, Invitation> PLAY_INVITATIONS = new HashMap<>();
    private static final Map<UUID, PlayerBodyPacket> BODY_IDENTITIES = new HashMap<>();
    private static final String NEXT_ACTION = "SynergyNextPlayerSocialAction";
    private static final String NEXT_INVITE = "SynergyNextPlayerFollowInvitation";
    private static final int INVITATION_TICKS = 600;

    private PlayerSocialService() {}

    public static boolean canInteract(ServerPlayer actor, ServerPlayer recipient, LivingEntity body) {
        return actor != recipient && actor.isAlive() && recipient.isAlive() && body.isAlive()
                && !actor.isSpectator() && !recipient.isSpectator()
                && !TrueTransfurCompat.isRebirthing(actor) && !TrueTransfurCompat.isRebirthing(recipient)
                && !TrueTransfurCompat.isInstinctShell(actor)
                && TrueTransfurCompat.interactionBody(recipient) == body
                && !(body == recipient && TrueTransfurCompat.isInstinctShell(recipient))
                && actor.level() == body.level() && !body.isRemoved()
                && actor.distanceToSqr(body) <= 64.0D && actor.hasLineOfSight(body)
                && ChangedSynergyGameRules.enabled(actor.level(), ChangedSynergyGameRules.FRIENDSHIP_SYSTEM);
    }

    /** Both accounts may change mode while an invitation is pending. Check visible bodies. */
    private static boolean canConfirm(ServerPlayer a, ServerPlayer b) {
        LivingEntity first=TrueTransfurCompat.interactionBody(a),second=TrueTransfurCompat.interactionBody(b);
        return a!=b&&NativeRoleplayService.validBody(a,first)&&NativeRoleplayService.validBody(b,second)
                &&a.isAlive()&&b.isAlive()&&!a.isSpectator()&&!b.isSpectator()&&first.level()==second.level()
                &&first.distanceToSqr(second)<=64&&first.hasLineOfSight(second)
                &&!TakeoverService.active(a)&&!TakeoverService.active(b)
                &&ChangedSynergyGameRules.enabled(a.level(),ChangedSynergyGameRules.FRIENDSHIP_SYSTEM);
    }

    public static boolean handleInteraction(PlayerInteractEvent.EntityInteract event) {
        return handleInteraction(event, event.getTarget());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractAt(PlayerInteractEvent.EntityInteractSpecific event) {
        handleInteraction(event, event.getTarget());
    }

    private static boolean handleInteraction(PlayerInteractEvent event, Entity target) {
        if (!(event.getEntity() instanceof ServerPlayer actor)) return false;
        boolean proxyTarget = target instanceof ChangedEntity proxy && TrueTransfurCompat.isInstinctBody(proxy);
        if (event.getHand() != InteractionHand.MAIN_HAND) return proxyTarget && rejectInteraction(event);
        ServerPlayer recipient = target instanceof ServerPlayer player ? player
                : target instanceof ChangedEntity body ? TrueTransfurCompat.bodyOwner(body) : null;
        if (recipient == null) {
            // A stale/offline proxy must never fall through to NPC equipment or pet menus.
            if (target instanceof ChangedEntity body && TrueTransfurCompat.isInstinctBody(body)) {
                return rejectInteraction(event);
            }
            return false;
        }
        if (!(target instanceof LivingEntity body) || !canInteract(actor, recipient, body))
            return proxyTarget && rejectInteraction(event);
        if (!proxyTarget && !event.getItemStack().isEmpty() && !actor.isShiftKeyDown()) return false;
        NetworkHooks.openScreen(actor, new SimpleMenuProvider(
                (id, inventory, viewer) -> new SocialInteractionMenu(id, inventory, recipient, body),
                recipient.getDisplayName()),
                buffer -> SocialInteractionMenu.writePlayerOpeningData(buffer, actor, recipient, body));
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        return true;
    }

    private static boolean rejectInteraction(PlayerInteractEvent event) {
        event.setCancellationResult(InteractionResult.FAIL);
        event.setCanceled(true);
        return true;
    }

    public static void act(ServerPlayer actor, UUID recipientId, LivingEntity body, String command) {
        ServerPlayer recipient = actor.server.getPlayerList().getPlayer(recipientId);
        if (recipient == null || !canInteract(actor, recipient, body)) return;
        long now = actor.server.overworld().getGameTime();
        if (actor.getPersistentData().getLong(NEXT_ACTION) > now) return;
        if (!command.equals("social_talk") && !command.equals("social_pat")
                && !command.equals("social_gift") && !command.equals("social_follow")
                && !command.equals("social_friend") && !command.equals("social_unfriend") && !command.equals("social_play")) return;
        actor.getPersistentData().putLong(NEXT_ACTION, now + 10L);
        switch (command) {
            case "social_talk" -> {
                actor.sendSystemMessage(Component.translatable("message.changed_synergy.player_social.greeted", recipient.getDisplayName()));
                recipient.sendSystemMessage(Component.translatable("message.changed_synergy.player_social.greeting", actor.getDisplayName()));
            }
            case "social_pat" -> {
                if(SynergyPatService.perform(actor, body, true)) {
                    PlayerFriendshipData.get(actor.server).care(actor,recipient,false);
                    NativeRoleplayService.emote(recipient,net.ltxprogrammer.changed.entity.Emote.HEART,false);
                }
            }
            case "social_play" -> requestPlay(actor,recipient);
            case "social_friend" -> requestFriend(actor,recipient);
            case "social_unfriend" -> {
                PlayerFriendshipData.get(actor.server).remove(actor.getUUID(),recipient.getUUID());
                if(isFollowing(recipient.getUUID(),actor.getUUID()))stopFollowing(recipient);
                if(isFollowing(actor.getUUID(),recipient.getUUID()))stopFollowing(actor);
                actor.sendSystemMessage(Component.translatable("message.changed_synergy.player_social.friend_removed",recipient.getDisplayName()));
                recipient.sendSystemMessage(Component.translatable("message.changed_synergy.player_social.friend_removed",actor.getDisplayName()));
            }
            case "social_gift" -> {
                ItemStack held = actor.getMainHandItem();
                if (held.isEmpty()) {
                    actor.displayClientMessage(Component.translatable("message.changed_synergy.social.no_gift"), true);
                    return;
                }
                ItemStack gift = held.copyWithCount(1);
                Component itemName = gift.getHoverName();
                // Inventory.add discards excess items in Creative. Check capacity before that call.
                boolean hasSpace = recipient.getInventory().getFreeSlot() >= 0
                        || recipient.getInventory().items.stream().anyMatch(stack -> ItemStack.isSameItemSameTags(stack, gift)
                                && stack.getCount() < Math.min(stack.getMaxStackSize(), recipient.getInventory().getMaxStackSize()));
                if (!hasSpace || !recipient.getInventory().add(gift)) {
                    actor.displayClientMessage(Component.translatable("message.changed_synergy.player_social.inventory_full"), true);
                    return;
                }
                held.shrink(1);
                PlayerFriendshipData.get(actor.server).care(actor,recipient,true);
                NativeRoleplayService.emote(recipient,net.ltxprogrammer.changed.entity.Emote.CASUAL,false);
                recipient.containerMenu.broadcastChanges();
                actor.swing(InteractionHand.MAIN_HAND, true);
                actor.sendSystemMessage(Component.translatable("message.changed_synergy.player_social.gift_sent", itemName, recipient.getDisplayName()));
                recipient.sendSystemMessage(Component.translatable("message.changed_synergy.player_social.gift_received", actor.getDisplayName(), itemName));
            }
            case "social_follow" -> requestFollow(actor, recipient);
        }
    }

    public static boolean isFollowing(UUID follower, UUID leader) {
        FollowLink link = FOLLOWING.get(follower);
        return link != null && leader.equals(link.leader);
    }

    public static boolean isFollowLeader(ChangedEntity body, @Nullable LivingEntity target) {
        if (target == null || !TrueTransfurCompat.isInstinctBody(body)) return false;
        ServerPlayer owner = TrueTransfurCompat.bodyOwner(body);
        FollowLink link = owner == null ? null : FOLLOWING.get(owner.getUUID());
        UUID leaderId = link == null ? null : link.leader;
        ServerPlayer leader = leaderId == null ? null : body.getServer().getPlayerList().getPlayer(leaderId);
        return leader != null && TrueTransfurCompat.interactionBody(leader) == target;
    }

    public static void requestFollow(ServerPlayer leader, ServerPlayer follower) {
        if(!canConfirm(leader,follower))return;
        if (isFollowing(follower.getUUID(), leader.getUUID())) {
            stopFollowing(follower);
            return;
        }
        if (FOLLOWING.containsKey(follower.getUUID())) {
            leader.sendSystemMessage(Component.translatable("message.changed_synergy.player_social.busy"));
            return;
        }
        long now = leader.server.overworld().getGameTime();
        if (follower.getPersistentData().getLong(NEXT_INVITE) > now || pending(INVITATIONS,follower)) {
            leader.sendSystemMessage(Component.translatable("message.changed_synergy.player_social.busy"));
            return;
        }
        follower.getPersistentData().putLong(NEXT_INVITE, now + 200L);
        if (TrueTransfurCompat.interactionBody(follower) instanceof ChangedEntity body
                && TrueTransfurCompat.isInstinctBody(body) && PlayerFriendshipData.friends(leader,follower)) {
            beginFollowing(follower, leader);
            return;
        }
        INVITATIONS.put(follower.getUUID(), new Invitation(leader.getUUID(), leader.server.getTickCount() + INVITATION_TICKS));
        follower.sendSystemMessage(Component.translatable("message.changed_synergy.player_social.invitation", leader.getDisplayName())
                .append(" ").append(Component.translatable("message.changed_synergy.player_social.accept")
                        .withStyle(style -> style.withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent(
                                ClickEvent.Action.RUN_COMMAND, "/changedsynergy social accept " + leader.getUUID()))))
                .append(" ").append(Component.translatable("message.changed_synergy.player_social.decline")
                        .withStyle(style -> style.withColor(ChatFormatting.GRAY).withClickEvent(new ClickEvent(
                                ClickEvent.Action.RUN_COMMAND, "/changedsynergy social decline " + leader.getUUID())))));
        leader.sendSystemMessage(Component.translatable("message.changed_synergy.player_social.invited", follower.getDisplayName()));
    }

    private static void beginFollowing(ServerPlayer follower, ServerPlayer leader) {
        INVITATIONS.remove(follower.getUUID());
        LivingEntity body = TrueTransfurCompat.interactionBody(follower);
        FOLLOWING.put(follower.getUUID(), new FollowLink(leader.getUUID(),
                body instanceof ChangedEntity proxy && TrueTransfurCompat.isInstinctBody(proxy) ? body.getUUID() : null));
        if (body instanceof ChangedEntity proxy) {
            ensureFollowGoal(proxy);
            if (proxy.getTarget() == TrueTransfurCompat.interactionBody(leader)) proxy.setTarget(null);
        }
        follower.sendSystemMessage(Component.translatable("message.changed_synergy.player_social.following", leader.getDisplayName()));
        leader.sendSystemMessage(Component.translatable("message.changed_synergy.player_social.follow_accepted", follower.getDisplayName()));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("social").requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.translatable("message.changed_synergy.player_social.help"), false);
                    return 1;
                })
                .then(Commands.literal("accept").then(Commands.argument("requester", UuidArgument.uuid())
                        .executes(context -> answer(context.getSource().getPlayerOrException(), UuidArgument.getUuid(context, "requester"), true))))
                .then(Commands.literal("decline").then(Commands.argument("requester", UuidArgument.uuid())
                        .executes(context -> answer(context.getSource().getPlayerOrException(), UuidArgument.getUuid(context, "requester"), false))))
                .then(Commands.literal("name").then(Commands.argument("name",com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                        .executes(context->NativeRoleplayService.rename(context.getSource().getPlayerOrException(),com.mojang.brigadier.arguments.StringArgumentType.getString(context,"name")))))
                .then(Commands.literal("chat").then(Commands.literal("global").executes(context->NativeRoleplayService.chatMode(context.getSource().getPlayerOrException(),true)))
                        .then(Commands.literal("roleplay").executes(context->NativeRoleplayService.chatMode(context.getSource().getPlayerOrException(),false))))
                .then(emoteCommand())
                .then(Commands.literal("friend_accept").then(Commands.argument("requester",UuidArgument.uuid())
                        .executes(context->answerFriend(context.getSource().getPlayerOrException(),UuidArgument.getUuid(context,"requester"),true))))
                .then(Commands.literal("friend_decline").then(Commands.argument("requester",UuidArgument.uuid())
                        .executes(context->answerFriend(context.getSource().getPlayerOrException(),UuidArgument.getUuid(context,"requester"),false))))
                .then(Commands.literal("play_accept").then(Commands.argument("requester",UuidArgument.uuid())
                        .executes(context->answerPlay(context.getSource().getPlayerOrException(),UuidArgument.getUuid(context,"requester"),true))))
                .then(Commands.literal("play_decline").then(Commands.argument("requester",UuidArgument.uuid())
                        .executes(context->answerPlay(context.getSource().getPlayerOrException(),UuidArgument.getUuid(context,"requester"),false))))
                .then(Commands.literal("stop").executes(context -> {
                    stopFollowing(context.getSource().getPlayerOrException());
                    return 1;
                }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> emoteCommand() {
        var root=Commands.literal("emote");
        for(var emote:net.ltxprogrammer.changed.entity.Emote.values())root.then(Commands.literal(emote.name().toLowerCase(java.util.Locale.ROOT))
                .executes(context->NativeRoleplayService.emote(context.getSource().getPlayerOrException(),emote,true)?1:0));
        return root;
    }
    private static void requestFriend(ServerPlayer actor,ServerPlayer recipient) {
        if(!PlayerFriendshipData.eligible(actor,recipient)||PlayerFriendshipData.friends(actor,recipient))return;
        long now=actor.server.overworld().getGameTime();
        if(actor.getPersistentData().getLong("SynergyNextPlayerFriendInvite")>now||pending(FRIEND_INVITATIONS,recipient)) {
            actor.sendSystemMessage(Component.translatable("message.changed_synergy.player_social.busy"));return;
        }
        actor.getPersistentData().putLong("SynergyNextPlayerFriendInvite",now+200);
        FRIEND_INVITATIONS.put(recipient.getUUID(),new Invitation(actor.getUUID(),actor.server.getTickCount()+INVITATION_TICKS));
        recipient.sendSystemMessage(Component.translatable("message.changed_synergy.player_social.friend_invitation",actor.getDisplayName())
                .append(" ").append(Component.translatable("message.changed_synergy.player_social.accept").withStyle(style->style.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/changedsynergy social friend_accept "+actor.getUUID()))))
                .append(" ").append(Component.translatable("message.changed_synergy.player_social.decline").withStyle(style->style.withColor(ChatFormatting.GRAY)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/changedsynergy social friend_decline "+actor.getUUID())))));
        actor.sendSystemMessage(Component.translatable("message.changed_synergy.player_social.invited",recipient.getDisplayName()));
    }
    public static int answerFriend(ServerPlayer recipient,UUID requester,boolean accept) {
        Invitation invitation=FRIEND_INVITATIONS.get(recipient.getUUID());
        if(invitation==null||!invitation.leader.equals(requester)||invitation.expiresAt<recipient.server.getTickCount()) {
            recipient.sendSystemMessage(Component.translatable("message.changed_synergy.player_social.expired"));return 0;
        }
        FRIEND_INVITATIONS.remove(recipient.getUUID());ServerPlayer actor=recipient.server.getPlayerList().getPlayer(requester);
        if(!accept||actor==null||!canConfirm(actor,recipient)) {
            recipient.sendSystemMessage(Component.translatable("message.changed_synergy.player_social.declined"));
            if(actor!=null)actor.sendSystemMessage(Component.translatable("message.changed_synergy.player_social.friend_declined",recipient.getDisplayName()));return 0;
        }
        if(!PlayerFriendshipData.get(recipient.server).establish(actor,recipient))return 0;
        actor.sendSystemMessage(Component.translatable("message.changed_synergy.player_social.friend_added",recipient.getDisplayName()));
        recipient.sendSystemMessage(Component.translatable("message.changed_synergy.player_social.friend_added",actor.getDisplayName()));return 1;
    }

    private static int answer(ServerPlayer follower, UUID requester, boolean accept) {
        Invitation invitation = INVITATIONS.get(follower.getUUID());
        if (invitation == null || !invitation.leader.equals(requester)
                || invitation.expiresAt < follower.server.getTickCount()) {
            follower.sendSystemMessage(Component.translatable("message.changed_synergy.player_social.expired"));
            return 0;
        }
        INVITATIONS.remove(follower.getUUID());
        ServerPlayer leader = follower.server.getPlayerList().getPlayer(requester);
        if (accept && leader != null && canConfirm(leader, follower)) {
            beginFollowing(follower, leader);
        } else {
            follower.sendSystemMessage(Component.translatable("message.changed_synergy.player_social.declined"));
            if (leader != null) leader.sendSystemMessage(Component.translatable("message.changed_synergy.player_social.follow_declined", follower.getDisplayName()));
        }
        return 1;
    }

    private static boolean pending(Map<UUID,Invitation> invitations,ServerPlayer recipient) {
        Invitation invite=invitations.get(recipient.getUUID());return invite!=null&&invite.expiresAt>=recipient.server.getTickCount();
    }
    private static void requestPlay(ServerPlayer actor,ServerPlayer recipient) {
        long now=actor.server.overworld().getGameTime();
        if(pending(PLAY_INVITATIONS,recipient)||actor.getPersistentData().getLong("SynergyNextPlayerPlayInvite")>now) {
            actor.sendSystemMessage(Component.translatable("message.changed_synergy.player_social.busy"));return;
        }
        actor.getPersistentData().putLong("SynergyNextPlayerPlayInvite",now+600);
        PLAY_INVITATIONS.put(recipient.getUUID(),new Invitation(actor.getUUID(),actor.server.getTickCount()+INVITATION_TICKS));
        recipient.sendSystemMessage(Component.translatable("message.changed_synergy.player_social.play_invitation",actor.getDisplayName())
                .append(" ").append(Component.translatable("message.changed_synergy.player_social.accept").withStyle(style->style.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/changedsynergy social play_accept "+actor.getUUID()))))
                .append(" ").append(Component.translatable("message.changed_synergy.player_social.decline").withStyle(style->style.withColor(ChatFormatting.GRAY)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/changedsynergy social play_decline "+actor.getUUID())))));
        actor.sendSystemMessage(Component.translatable("message.changed_synergy.player_social.invited",recipient.getDisplayName()));
    }
    public static int answerPlay(ServerPlayer recipient,UUID requester,boolean accept) {
        Invitation invitation=PLAY_INVITATIONS.get(recipient.getUUID());
        if(invitation==null||!invitation.leader.equals(requester)||invitation.expiresAt<recipient.server.getTickCount()) {
            recipient.sendSystemMessage(Component.translatable("message.changed_synergy.player_social.expired"));return 0;
        }
        PLAY_INVITATIONS.remove(recipient.getUUID());ServerPlayer actor=recipient.server.getPlayerList().getPlayer(requester);
        if(!accept||actor==null||!canConfirm(actor,recipient)) {
            recipient.sendSystemMessage(Component.translatable("message.changed_synergy.player_social.declined"));return 0;
        }
        PlayerFriendshipData.get(recipient.server).care(actor,recipient,false);
        actor.swing(InteractionHand.MAIN_HAND,true);recipient.swing(InteractionHand.MAIN_HAND,true);
        NativeRoleplayService.emote(actor,net.ltxprogrammer.changed.entity.Emote.CASUAL,false);
        NativeRoleplayService.emote(recipient,net.ltxprogrammer.changed.entity.Emote.CASUAL,false);
        actor.sendSystemMessage(Component.translatable("message.changed_synergy.player_social.played",recipient.getDisplayName()));
        recipient.sendSystemMessage(Component.translatable("message.changed_synergy.player_social.played",actor.getDisplayName()));return 1;
    }

    public static void stopFollowing(ServerPlayer follower) {
        INVITATIONS.remove(follower.getUUID());
        FollowLink oldLeader = FOLLOWING.remove(follower.getUUID());
        if (oldLeader != null) {
            LivingEntity body = TrueTransfurCompat.interactionBody(follower);
            if (body instanceof ChangedEntity proxy) proxy.getNavigation().stop();
            follower.sendSystemMessage(Component.translatable("message.changed_synergy.player_social.stopped"));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer follower)
                || follower.tickCount % 20 != 0) return;
        UUID id = follower.getUUID();
        MinecraftServer server = follower.server;
        for(var pending:java.util.List.of(FRIEND_INVITATIONS,PLAY_INVITATIONS)) {
            Invitation entry=pending.get(id);if(entry!=null&&entry.expiresAt<server.getTickCount())pending.remove(id);
        }
        Invitation invitation = INVITATIONS.get(id);
        if (invitation != null && invitation.expiresAt < server.getTickCount()) {
            INVITATIONS.remove(id);
            invitation = null;
        }
        FollowLink link = FOLLOWING.get(id);
        if (link != null && link.automaticBody == null
                && TrueTransfurCompat.interactionBody(follower) instanceof ChangedEntity proxy
                && TrueTransfurCompat.isInstinctBody(proxy)) {
            // An accepted walk remains valid when its recipient hands movement to Instinct.
            link = new FollowLink(link.leader, proxy.getUUID());
            FOLLOWING.put(id, link);
        }
        UUID leaderId = link == null ? null : link.leader;
        ServerPlayer leader = leaderId == null ? null : server.getPlayerList().getPlayer(leaderId);
        if (leaderId != null && (leader == null || !leader.isAlive() || !follower.isAlive()
                || follower.isSpectator() || leader.isSpectator() || follower.level() != leader.level()
                || !ChangedSynergyGameRules.enabled(follower.level(), ChangedSynergyGameRules.FRIENDSHIP_SYSTEM)
                || link.automaticBody != null && !link.automaticBody.equals(TrueTransfurCompat.interactionBody(follower).getUUID())
                || TrueTransfurCompat.isInstinctShell(leader) && TrueTransfurCompat.interactionBody(leader) == leader
                || TrueTransfurCompat.interactionBody(leader).distanceToSqr(TrueTransfurCompat.interactionBody(follower)) > 64.0D * 64.0D)) {
            stopFollowing(follower);
            leader = null;
        }
        if (leader == null && invitation != null) leader = server.getPlayerList().getPlayer(invitation.leader);
        if (leader != null && leader.level() == follower.level() && !TrueTransfurCompat.isInstinctShell(follower)
                && !(TrueTransfurCompat.isInstinctShell(leader) && TrueTransfurCompat.interactionBody(leader) == leader)) {
            ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> follower),
                    new ScoutTargetPacket(TrueTransfurCompat.interactionBody(leader).getId()));
        }
        if (TrueTransfurCompat.interactionBody(follower) instanceof ChangedEntity proxy
                && TrueTransfurCompat.isInstinctBody(proxy)) {
            ensureFollowGoal(proxy);
            // The account remains the profile; the display name may be a configured form name.
            if (!proxy.getName().equals(follower.getDisplayName())) proxy.setCustomName(follower.getDisplayName());
            proxy.setCustomNameVisible(false);
            PlayerBodyPacket identity = identity(proxy, follower);
            PlayerBodyPacket previous = BODY_IDENTITIES.put(proxy.getUUID(), identity);
            if (previous == null || !previous.owner().equals(identity.owner())
                    || !previous.account().equals(identity.account())
                    || !previous.name().getString().equals(identity.name().getString())) {
                // Tracking can start inside addFreshEntity, before TrueTransfur stores the owner reference.
                ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> proxy), identity);
                ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> follower), identity);
            }
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ChangedEntity body
                && TrueTransfurCompat.isInstinctBody(body)) {
            CreatureCommunityData.detach(body);
            ensureFollowGoal(body);
        }
    }

    @SubscribeEvent
    public static void onTrack(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer viewer)
                || !(event.getTarget() instanceof ChangedEntity body)
                || !TrueTransfurCompat.isInstinctBody(body)) return;
        ServerPlayer owner = TrueTransfurCompat.bodyOwner(body);
        if (owner != null) ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                identity(body, owner));
    }

    private static PlayerBodyPacket identity(ChangedEntity body, ServerPlayer owner) {
        return new PlayerBodyPacket(body.getUUID(), owner.getUUID(), owner.getDisplayName(), owner.getGameProfile().getName());
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) BODY_IDENTITIES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof ChangedEntity creature && !creature.level().isClientSide
                && creature.getTarget() instanceof ServerPlayer shell && TrueTransfurCompat.isInstinctShell(shell)) {
            creature.setTarget(null);
            if (creature.getLastHurtByMob() == shell) creature.setLastHurtByMob(null);
            creature.setLastHurtByPlayer(null);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        INVITATIONS.remove(id);
        FRIEND_INVITATIONS.remove(id);
        PLAY_INVITATIONS.remove(id);
        PLAY_INVITATIONS.values().removeIf(invitation->invitation.leader.equals(id));
        FRIEND_INVITATIONS.values().removeIf(invitation->invitation.leader.equals(id));
        INVITATIONS.values().removeIf(invitation -> invitation.leader.equals(id));
        FOLLOWING.remove(id);
        FOLLOWING.values().removeIf(link -> link.leader.equals(id));
    }

    @SubscribeEvent
    public static void onServerStop(ServerStoppedEvent event) {
        INVITATIONS.clear();
        FRIEND_INVITATIONS.clear();
        PLAY_INVITATIONS.clear();
        FOLLOWING.clear();
        BODY_IDENTITIES.clear();
    }

    private static void ensureFollowGoal(ChangedEntity body) {
        if (body.goalSelector.getAvailableGoals().stream().noneMatch(goal -> goal.getGoal() instanceof PlayerFollowGoal))
            body.goalSelector.addGoal(-1, new PlayerFollowGoal(body));
    }

    private record Invitation(UUID leader, int expiresAt) {}
    private record FollowLink(UUID leader, @Nullable UUID automaticBody) {}

    private static final class PlayerFollowGoal extends Goal {
        private final ChangedEntity body;
        private final CompanionFollowNavigation navigation;
        private LivingEntity leader;
        private int repath;

        private PlayerFollowGoal(ChangedEntity body) {
            this.body = body;
            this.navigation = new CompanionFollowNavigation(body);
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            ServerPlayer owner = TrueTransfurCompat.bodyOwner(body);
            FollowLink link = owner == null ? null : FOLLOWING.get(owner.getUUID());
            UUID id = link == null ? null : link.leader;
            ServerPlayer player = id == null ? null : body.getServer().getPlayerList().getPlayer(id);
            leader = player == null ? null : TrueTransfurCompat.interactionBody(player);
            if (player != null && TrueTransfurCompat.isInstinctShell(player) && leader == player)
                leader = null; // A missing proxy never makes the invisible technical shell a destination.
            return owner != null && owner.isAlive() && link != null && link.automaticBody != null
                    && link.automaticBody.equals(body.getUUID()) && leader != null && leader.isAlive()
                    && leader.level() == body.level() && !body.isNoAi() && !body.isPassenger()
                    && !body.isLeashed() && body.getTarget() == null && !body.isSleeping();
        }

        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public void start() { repath = 0; navigation.reset(); }
        @Override public void stop() { body.getNavigation().stop(); navigation.reset(); leader = null; }
        @Override public void tick() {
            if (leader == null) return;
            if (body.distanceToSqr(leader) <= 9.0D) {
                body.getNavigation().stop();
                return;
            }
            if (--repath <= 0) {
                repath = 10;
                body.getLookControl().setLookAt(leader, 30.0F, 30.0F);
                navigation.moveToward(leader, 0.35D);
            }
        }
        @Override public boolean requiresUpdateEveryTick() { return true; }
    }
}
