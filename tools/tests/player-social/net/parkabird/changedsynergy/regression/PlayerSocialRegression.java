package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import net.parkabird.changedsynergy.event.NpcDispositionEvents;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.network.PlayerBodyPacket;
import net.parkabird.changedsynergy.world.inventory.SocialInteractionMenu;

/** Real player identities, inventories, consent and the author's physical INSTINCT runtime. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class PlayerSocialRegression {
    private static int checks;

    @SubscribeEvent public static void run(ServerStartedEvent event) {
        try {
            checkPlayers(event.getServer());
            System.out.println("PLAYER_SOCIAL_REGRESSION_PASS: " + checks + " checks");
        } catch (Throwable failure) {
            System.err.println("PLAYER_SOCIAL_REGRESSION_FAIL");
            failure.printStackTrace();
        } finally { event.getServer().halt(false); }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void checkPlayers(MinecraftServer server) throws Exception {
        boolean expectsBackend = !System.getProperty("changed_synergy.test.tt", "none").equals("none");
        check(TrueTransfurCompat.supportsNativeOrigin() == expectsBackend, "Requested TrueTransfur backend is actually loaded");
        var level = server.overworld();
        ServerPlayer leader = player(server, "SocialLeader");
        ServerPlayer follower = player(server, "SocialFollower");
        ServerPlayer stranger = player(server, "SocialStranger");
        var field = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(net.minecraft.server.players.PlayerList.class, "f_11197_");
        field.setAccessible(true);
        var players = (Map<UUID, ServerPlayer>)field.get(server.getPlayerList());
        var listField = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(net.minecraft.server.players.PlayerList.class, "f_11196_");
        listField.setAccessible(true);
        var online = (List<ServerPlayer>)listField.get(server.getPlayerList());
        for (var player : List.of(leader, follower, stranger)) {
            players.put(player.getUUID(), player);
            online.add(player);
            player.setPos(0.5D, 180.0D, 0.5D);
            level.addNewPlayer(player);
        }
        follower.setPos(2.5D, 180.0D, 0.5D);
        checkNpcPatting(leader);
        stranger.setPos(4.5D, 180.0D, 0.5D);
        check(PlayerSocialService.canInteract(leader, follower, follower), "Normal player body is interactable");
        check(!PlayerSocialService.canInteract(leader, leader, leader), "Self interaction rejected");
        check(!TrueTransfurCompat.isInstinctShell(follower), "Normal player not hidden");
        follower.setInvisible(true);
        check(!TrueTransfurCompat.isInstinctShell(follower), "Ordinary invisibility is not a technical shell");
        follower.setInvisible(false);
        checkMenu(leader, follower, follower);
        var data = new net.minecraft.nbt.CompoundTag();
        data.putString("command", "social_gift");
        var menu = new SocialInteractionMenu(0, leader.getInventory(), follower, follower);
        leader.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE, 3));
        menu.update(data, LogicalSide.SERVER, stranger);
        check(leader.getMainHandItem().getCount() == 3 && follower.getInventory().countItem(Items.APPLE) == 0,
                "Forged menu sender cannot transfer a gift");
        act(leader, follower, "social_gift");
        check(leader.getMainHandItem().getCount() == 2 && follower.getInventory().countItem(Items.APPLE) == 1,
                "Exactly one real item delivered and debited");
        for (int slot = 0; slot < follower.getInventory().items.size(); slot++)
            follower.getInventory().items.set(slot, new ItemStack(Items.COBBLESTONE, 64));
        follower.setGameMode(GameType.CREATIVE);
        act(leader, follower, "social_gift");
        check(leader.getMainHandItem().getCount() == 2, "Full Creative inventory cannot destroy a gift");
        follower.setGameMode(GameType.SURVIVAL);
        follower.getInventory().clearContent();
        act(leader, follower, "social_inventory");
        check(leader.getMainHandItem().getCount() == 2, "NPC commands are not player commands");
        act(leader, follower, "social_follow");
        check(!PlayerSocialService.isFollowing(follower.getUUID(), leader.getUUID()), "Player-controlled follow requires consent");
        server.getCommands().performPrefixedCommand(stranger.createCommandSourceStack(), "changedsynergy social accept " + leader.getUUID());
        check(!PlayerSocialService.isFollowing(follower.getUUID(), leader.getUUID()), "Another player cannot accept this invitation");
        server.getCommands().performPrefixedCommand(follower.createCommandSourceStack(), "changedsynergy social accept " + stranger.getUUID());
        check(!PlayerSocialService.isFollowing(follower.getUUID(), leader.getUUID()), "Wrong requester cannot accept this invitation");
        server.getCommands().performPrefixedCommand(follower.createCommandSourceStack(), "changedsynergy social accept " + leader.getUUID());
        check(PlayerSocialService.isFollowing(follower.getUUID(), leader.getUUID()), "Recipient can accept their own invitation");
        level.getGameRules().getRule(ChangedSynergyGameRules.FRIENDSHIP_SYSTEM).set(false, server);
        follower.tickCount = 20;
        PlayerSocialService.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, follower));
        check(!PlayerSocialService.isFollowing(follower.getUUID(), leader.getUUID())
                && !PlayerSocialService.canInteract(leader, follower, follower), "Disabling friendship stops player social actions and following");
        level.getGameRules().getRule(ChangedSynergyGameRules.FRIENDSHIP_SYSTEM).set(true, server);
        follower.getPersistentData().putLong("SynergyNextPlayerFollowInvitation", 0);
        act(leader, follower, "social_follow");
        server.getCommands().performPrefixedCommand(follower.createCommandSourceStack(), "changedsynergy social accept " + leader.getUUID());
        PlayerSocialService.stopFollowing(follower);
        check(!PlayerSocialService.isFollowing(follower.getUUID(), leader.getUUID()), "Recipient can end following");
        follower.getPersistentData().putLong("SynergyNextPlayerFollowInvitation", 0);
        act(leader, follower, "social_follow");
        server.getCommands().performPrefixedCommand(follower.createCommandSourceStack(), "changedsynergy social decline " + leader.getUUID());
        check(!PlayerSocialService.isFollowing(follower.getUUID(), leader.getUUID()), "Decline leaves movement untouched");
        checkNpcMenus(leader);
        RelationshipGrowthChecks.run(leader, follower);

        if (TrueTransfurCompat.supportsNativeOrigin()) {
            var identity = new net.parkabird.changedsynergy.origin.NativeOriginData.Identity();
            identity.state = "native";
            identity.form = identity.current = ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.getId().toString();
            identity.faction = "dark";
            net.parkabird.changedsynergy.origin.NativeOriginData.get(server).players.put(follower.getUUID(), identity);
            ProcessTransfur.setPlayerTransfurVariant(follower, ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get(), null, 1, false);
            Capability cap = (Capability)Class.forName("com.lugert.true_transfur.transfur.TransfurCapabilities").getField("TRANSFUR_DATA").get(null);
            Object state = follower.getCapability(cap).orElse(null);
            if (state == null) throw new AssertionError("Missing TrueTransfur capability");
            state.getClass().getMethod("setTransfurred", boolean.class).invoke(state, true);
            Class<?> controller = Class.forName("com.lugert.true_transfur.transfur.TransfurController");
            check(ProcessTransfur.getPlayerTransfurVariant(follower) != null
                    && Boolean.TRUE.equals(controller.getMethod("isTrueTransfurActive", ServerPlayer.class).invoke(null, follower)),
                    "Native origin policy allows the author's control backend");
            RelationshipGrowthChecks.permanentBond(follower);
            act(leader, follower, "social_friend");
            check(!PlayerFriendshipData.friends(leader, follower), "Friend invitation does not assume consent");
            check(PlayerSocialService.answerFriend(follower, leader.getUUID(), true) == 1
                    && PlayerFriendshipData.friends(leader, follower), "Both players confirm friendship before automatic Instinct following");
            Class mode = Class.forName("com.lugert.true_transfur.transfur.ControlMode");
            Object instinct = Enum.valueOf(mode, "INSTINCT");
            boolean physicalRuntime;
            try { Class.forName("com.lugert.true_transfur.transfur.InstinctRuntime").getMethod("getControlledEntity", ServerPlayer.class); physicalRuntime = true; }
            catch (NoSuchMethodException exception) { physicalRuntime = false; }
            if (physicalRuntime) {
                follower.getPersistentData().putLong("SynergyNextPlayerFollowInvitation", 0);
                act(leader, follower, "social_follow");
                server.getCommands().performPrefixedCommand(follower.createCommandSourceStack(), "changedsynergy social accept " + leader.getUUID());
                leader.getPersistentData().putLong("SynergyNextPlayerFollowInvitation", 0);
                act(follower, leader, "social_follow");
                server.getCommands().performPrefixedCommand(leader.createCommandSourceStack(), "changedsynergy social accept " + follower.getUUID());
                check(PlayerSocialService.isFollowing(leader.getUUID(), follower.getUUID()),
                        "A player-controlled walk can track a leader who later enters INSTINCT");
                Method switchMode = controller.getMethod("switchControlMode", ServerPlayer.class, mode);
                check(Boolean.TRUE.equals(switchMode.invoke(null, follower, instinct)), "Author runtime enters INSTINCT");
                var visible = TrueTransfurCompat.interactionBody(follower);
                check(visible instanceof ChangedEntity, "Author runtime supplies a separate visible body");
                var proxy = (ChangedEntity)visible;
                RelationshipGrowthChecks.proxy(leader, proxy);
                check(TrueTransfurCompat.isInstinctShell(follower), "Technical player is recognized");
                check(TrueTransfurCompat.bodyOwner(proxy) == follower, "Proxy resolves to the exact real owner");
                check(!LatexSocialMemory.isSocialLatex(proxy), "Proxy is never an ordinary NPC");
                check(CreatureCommunityData.bind(proxy).isEmpty(), "Proxy cannot join a wild community");
                check(!PlayerSocialService.canInteract(leader, follower, follower), "Technical player cannot be interacted with instead of its body");
                check(PlayerSocialService.canInteract(leader, follower, proxy), "Visible body remains interactable");
                follower.tickCount = 20;
                PlayerSocialService.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, follower));
                check(proxy.goalSelector.getAvailableGoals().stream()
                        .anyMatch(wrapped -> wrapped.getGoal().getClass().getSimpleName().equals("PlayerFollowGoal") && wrapped.getGoal().canUse()),
                        "An accepted manual walk follows the physical body after entering INSTINCT");
                var followGoal = proxy.goalSelector.getAvailableGoals().stream()
                        .filter(wrapped -> wrapped.getGoal().getClass().getSimpleName().equals("PlayerFollowGoal"))
                        .findFirst().orElseThrow().getGoal();
                followGoal.start();
                PlayerSocialService.stopFollowing(follower);
                check(!followGoal.canUse() && !followGoal.canContinueToUse(),
                        "Ending follow leaves the INSTINCT goal inactive without a null-link crash");
                followGoal.stop();
                proxy.tick();
                check(proxy.isAlive() && TrueTransfurCompat.isInstinctControlled(follower),
                        "Physical INSTINCT body ticks normally after ending follow");
                ChangedEntity npc = (ChangedEntity)ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get().getEntityType().create(level);
                npc.setPos(1.5D, 180D, 0.5D);
                level.addFreshEntity(npc);
                check(!NpcDispositionEvents.canVisuallyAcquire(npc, follower), "Synergy perception ignores the technical player");
                check(!TargetingConditions.forNonCombat().ignoreLineOfSight().ignoreInvisibilityTesting().test(npc, follower),
                        "Native non-combat targeting ignores the technical player");
                npc.setTarget(follower);
                check(npc.getTarget() == null, "Native target assignment rejects the technical player");
                CreaturePersonality.setFamiliarity(npc, follower, 30);
                check(LatexCreatureCombatRules.isFriendlyPlayerBodyPair(npc, proxy), "Proxy retains its player's NPC friendship");
                checkMenu(leader, follower, proxy);
                leader.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE, 3));
                act(leader, follower, "social_gift");
                check(follower.getInventory().countItem(Items.APPLE) == 1 && leader.getMainHandItem().getCount() == 2,
                        "Proxy gift goes to the real player inventory");
                follower.getPersistentData().putLong("SynergyNextPlayerFollowInvitation", 0);
                act(leader, follower, "social_follow");
                check(PlayerSocialService.isFollowing(follower.getUUID(), leader.getUUID()), "INSTINCT follow starts automatically");
                var goal = proxy.goalSelector.getAvailableGoals().stream()
                        .filter(wrapped -> wrapped.getGoal().getClass().getSimpleName().equals("PlayerFollowGoal"))
                        .findFirst().orElseThrow().getGoal();
                check(goal.canUse(), "Follow goal uses the actual physical proxy");
                proxy.setTarget(leader);
                check(proxy.getTarget() == null, "Following cannot turn the inviter into an attack target");
                var offhand = new PlayerInteractEvent.EntityInteract(leader, InteractionHand.OFF_HAND, proxy);
                check(PlayerSocialService.handleInteraction(offhand) && offhand.isCanceled(), "Proxy offhand interaction cannot reach NPC equipment handlers");
                var specific = new PlayerInteractEvent.EntityInteractSpecific(leader, InteractionHand.MAIN_HAND, proxy, proxy.position());
                PlayerSocialService.onInteractAt(specific);
                check(specific.isCanceled() && leader.containerMenu instanceof SocialInteractionMenu opened && opened.isPlayerMode(),
                        "Precise right-click opens the player wheel instead of NPC equipment");
                leader.closeContainer();
                TrueTransfurCompat.ensurePlayerControl(follower);
                check(!TrueTransfurCompat.isInstinctControlled(follower) && proxy.isRemoved(), "Control reset exits and removes the physical proxy");
                follower.tickCount = 20;
                PlayerSocialService.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, follower));
                check(!PlayerSocialService.isFollowing(follower.getUUID(), leader.getUUID()), "Mode change cleans automatic following");
                // Enter without any accepted walk: the original report occurs on the first idle AI tick.
                check(Boolean.TRUE.equals(switchMode.invoke(null, follower, instinct)),
                        "Author runtime re-enters INSTINCT without a follow invitation");
                var idleBody = (ChangedEntity)TrueTransfurCompat.interactionBody(follower);
                follower.tickCount = 20;
                PlayerSocialService.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, follower));
                var idleGoal = idleBody.goalSelector.getAvailableGoals().stream()
                        .filter(wrapped -> wrapped.getGoal().getClass().getSimpleName().equals("PlayerFollowGoal"))
                        .findFirst().orElseThrow().getGoal();
                check(!idleGoal.canUse() && !idleGoal.canContinueToUse(),
                        "Fresh INSTINCT entry has no follow link and safely declines the follow goal");
                for (int tick = 0; tick < 40; tick++) idleBody.tick();
                check(idleBody.isAlive() && TrueTransfurCompat.isInstinctControlled(follower),
                        "Uninvited INSTINCT body survives repeated real ChangedEntity AI ticks");
                checkOrphanProxy(leader, follower, idleBody);
                TrueTransfurCompat.ensurePlayerControl(follower);
                check(!idleGoal.canUse() && !idleGoal.canContinueToUse(),
                        "Removed INSTINCT body cannot resume a follow goal");
                npc.discard();
            } else {
                controller.getMethod("setControlMode", ServerPlayer.class, mode).invoke(null, follower, instinct);
                check(!TrueTransfurCompat.isInstinctShell(follower), "Old direct-control INSTINCT player stays observable");
                TrueTransfurCompat.ensurePlayerControl(follower);
                check(!TrueTransfurCompat.isInstinctControlled(follower), "Old backend safely returns to player control");
            }
        }
        CampCrewChecks.run(follower, players);
        var packet = new PlayerBodyPacket(UUID.randomUUID(), follower.getUUID(), follower.getDisplayName(), follower.getGameProfile().getName());
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PlayerBodyPacket.encode(packet, buffer);
            var decoded = PlayerBodyPacket.decode(buffer);
            check(decoded.body().equals(packet.body()) && decoded.owner().equals(packet.owner())
                    && decoded.account().equals(packet.account()) && decoded.name().getString().equals(packet.name().getString())
                    && !buffer.isReadable(), "Identity packet preserves UUID, display name and account");
        } finally { buffer.release(); }
        for (var player : List.of(leader, follower, stranger)) {
            TrueTransfurCompat.ensurePlayerControl(player);
            player.discard(); players.remove(player.getUUID());
        }
    }

    private static void checkNpcMenus(ServerPlayer viewer) {
        var npc = (ChangedEntity)ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get().getEntityType().create(viewer.level());
        npc.setPos(viewer.position());
        viewer.serverLevel().addFreshEntity(npc);
        for (boolean bonded : new boolean[] {false, true}) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                SocialInteractionMenu.writeOpeningData(buffer, viewer, npc, bonded);
                var menu = new SocialInteractionMenu(0, viewer.getInventory(), buffer);
                check(!menu.isPlayerMode() && menu.getCreature() == npc && menu.isBondedMode() == bonded && !buffer.isReadable(),
                        "Existing " + (bonded ? "bonded" : "friend") + " menu payload is unchanged");
            } finally { buffer.release(); }
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SocialInteractionMenu.writeAbsorptionOpeningData(buffer, viewer);
            var menu = new SocialInteractionMenu(0, viewer.getInventory(), buffer);
            check(!menu.isPlayerMode() && menu.isVirtualAbsorptionNegotiation() && menu.isNegotiationMode() && !buffer.isReadable(),
                    "Existing absorption negotiation payload is unchanged");
        } finally { buffer.release(); npc.discard(); }
    }

    private static void checkOrphanProxy(ServerPlayer viewer, ServerPlayer owner, ChangedEntity body) throws Exception {
        UUID originalOwner = body.getPersistentData().getUUID("TrueTransfurOwner");
        try {
            body.getPersistentData().putUUID("TrueTransfurOwner", UUID.randomUUID());
            check(body.isAlive() && TrueTransfurCompat.bodyOwner(body) == null
                    && TrueTransfurCompat.interactionBody(owner) == owner && TrueTransfurCompat.isInstinctShell(owner),
                    "A live orphan proxy leaves only the hidden technical account as a fallback");
            viewer.getPersistentData().remove("ChangedSynergyNextNativePatReaction");
            check(!SynergyPatService.perform(viewer, body, false) && !SynergyPatService.perform(viewer, owner, false),
                    "Direct pat input rejects both orphan proxies and invisible technical accounts");
            PatAnimationService.refreshContinuous(viewer, body);
            var sessionsField = PatAnimationService.class.getDeclaredField("SESSIONS");
            sessionsField.setAccessible(true);
            check(!((Map<?, ?>)sessionsField.get(null)).containsKey(viewer.getUUID()),
                    "Continuous H-key pat cannot animate a stale INSTINCT body");
            viewer.tickCount = 20;
            PlayerSocialService.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, viewer));
            check(!PlayerSocialService.isFollowing(viewer.getUUID(), owner.getUUID()),
                    "A missing leader proxy cancels following instead of pointing at its hidden technical account");
            var interact = new PlayerInteractEvent.EntityInteract(viewer, InteractionHand.MAIN_HAND, body);
            check(PlayerSocialService.handleInteraction(interact) && interact.isCanceled()
                    && interact.getCancellationResult() == net.minecraft.world.InteractionResult.FAIL,
                    "Orphan proxy interactions stop before ordinary NPC handlers");
        } finally { body.getPersistentData().putUUID("TrueTransfurOwner", originalOwner); }
    }

    private static void checkMenu(ServerPlayer viewer, ServerPlayer recipient, net.minecraft.world.entity.LivingEntity body) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SocialInteractionMenu.writePlayerOpeningData(buffer, viewer, recipient, body);
            var opened = new SocialInteractionMenu(0, viewer.getInventory(), buffer);
            check(opened.isPlayerMode() && opened.getSocialPlayerBody() == body && !buffer.isReadable(), "Player menu payload resolves the exact body");
            check(opened.getSocialPlayerName().getString().equals(recipient.getDisplayName().getString())
                    && opened.getSocialPlayerAccount().equals(recipient.getGameProfile().getName()), "Menu displays the real player profile");
            check(!opened.isNegotiationMode() && !opened.isBondedMode() && !opened.canFriendInventory()
                    && !opened.isVoluntaryBondAvailable(), "Player wheel has no NPC control or inventory capabilities");
        } finally { buffer.release(); }
    }

    private static void act(ServerPlayer actor, ServerPlayer target, String action) {
        actor.getPersistentData().putLong("SynergyNextPlayerSocialAction", 0);
        PlayerSocialService.act(actor, target.getUUID(), TrueTransfurCompat.interactionBody(target), action);
    }

    private static ServerPlayer player(MinecraftServer server, String name) {
        var player = new ServerPlayer(server, server.overworld(), new GameProfile(UUID.randomUUID(), name));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player);
        return player;
    }

    private static void checkNpcPatting(ServerPlayer player) throws Exception {
        var sessionsField = PatAnimationService.class.getDeclaredField("SESSIONS");
        var pendingField = PatAnimationService.class.getDeclaredField("PENDING");
        sessionsField.setAccessible(true);
        pendingField.setAccessible(true);
        var sessions = (Map<?, ?>) sessionsField.get(null);
        var pending = (Map<?, ?>) pendingField.get(null);
        for (String id : List.of("milk_pudding", "latex_shark_feral", "dark_latex_wolf_pup",
                "pure_white_latex_wolf_pup", "gas_wolf_pup", "latex_snake", "latex_mermaid_shark")) {
            var type = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(
                    new net.minecraft.resources.ResourceLocation("changed", id));
            check(type != null, "NPC species registered: " + id);
            var mob = (ChangedEntity) type.create(player.level());
            boolean armed = id.equals("latex_snake") || id.equals("latex_mermaid_shark");
            check(PatAnimationService.canNpcPat(mob) == armed, "NPC pat anatomy: " + id);
            mob.setPos(player.position());
            PatAnimationService.startFixed(mob, player, 4);
            check(sessions.containsKey(mob.getUUID()) == armed, "Fixed NPC pat eligibility: " + id);
            PatAnimationService.stop(mob);
            PatAnimationService.scheduleFixed(mob, player, 20, 4);
            check(pending.containsKey(mob.getUUID()) == armed, "Delayed NPC return pat eligibility: " + id);
            pending.remove(mob.getUUID());
            mob.discard();
        }
        check(PatAnimationService.canNpcPat(player), "Player patting remains unchanged");
    }

    private static void check(boolean value, String description) {
        if (!value) throw new AssertionError(description);
        checks++; System.out.println("PLAYER_SOCIAL_PASS: " + description);
    }
}
