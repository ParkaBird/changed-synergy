package net.parkabird.changedsynergy.regression;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.PlayerOutpostData;
import net.parkabird.changedsynergy.ai.PlayerOutpostService;
import net.parkabird.changedsynergy.ai.PlayerOutpostSupplyType;
import net.parkabird.changedsynergy.ai.CreatureIdentity;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue;
import net.parkabird.changedsynergy.event.PlayerOrangeHarvestEvents;
import net.parkabird.changedsynergy.network.TelepathyDialoguePacket;
import net.parkabird.changedsynergy.world.inventory.PlayerOutpostMenu;

/** Dedicated-server regression entry point; only included by dialogue-outpost.init.gradle. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class DialogueOutpostRegression {
    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        Language original = Language.getInstance();
        try {
            var level = event.getServer().overworld();
            var owner = new ServerPlayer(event.getServer(), level,
                    new GameProfile(UUID.randomUUID(), "RegressionPlayer"));
            ChangedEntity mob = (ChangedEntity) ForgeRegistries.ENTITY_TYPES.getValue(
                    ResourceLocation.fromNamespaceAndPath("changed", "dark_latex_wolf_male")).create(level);
            var usesAddress = NpcDialogue.class.getDeclaredMethod("usesSocialAddress", Cue.class);
            usesAddress.setAccessible(true);
            var address = NpcDialogue.class.getDeclaredMethod("withSocialAddress",
                    ChangedEntity.class, ServerPlayer.class, Cue.class, Object[].class);
            address.setAccessible(true);
            mob.setCustomName(Component.literal("Drift"));
            Pattern placeholder = Pattern.compile("%(?:[0-9]+\\$)?[a-zA-Z]");
            int formatted = 0;
            for (String locale : new String[]{"en_us", "zh_cn", "ru_ru", "de_de", "es_es"}) {
                Map<String, String> text = new HashMap<>();
                try (var stream = NpcDialogue.class.getResourceAsStream(
                        "/assets/changed_synergy/lang/" + locale + ".json")) {
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                            .getAsJsonObject().entrySet().forEach(entry ->
                                    text.put(entry.getKey(), entry.getValue().getAsString()));
                }
                Language.inject(new Language() {
                    public String getOrDefault(String key, String fallback) { return text.getOrDefault(key, fallback); }
                    public boolean has(String key) { return text.containsKey(key); }
                    public boolean isDefaultRightToLeft() { return false; }
                    public FormattedCharSequence getVisualOrder(FormattedText value) {
                        return FormattedCharSequence.forward(value.getString(), Style.EMPTY);
                    }
                });
                for (var entry : text.entrySet()) {
                    if (!entry.getKey().startsWith("dialogue.") || !placeholder.matcher(entry.getValue()).find()) continue;
                    Cue cue = java.util.Arrays.stream(Cue.values())
                            .filter(value -> entry.getKey().contains("." + value.name().toLowerCase(java.util.Locale.ROOT) + ".")
                                    || entry.getKey().contains("." + value.name().toLowerCase(java.util.Locale.ROOT) + "_"))
                            .max(java.util.Comparator.comparingInt(value -> value.name().length())).orElseThrow();
                    boolean addressed = (boolean) usesAddress.invoke(null, cue);
                    Object[] extra = switch (cue) {
                        case BOND_WITNESS_COMPATRIOT_ATTACK, FRIEND_WITNESS_COMPATRIOT_ATTACK,
                                FRIEND_KIN_KILL_WARNING, FRIEND_KIN_KILL_BETRAYAL,
                                COMPATRIOT_DEFENSE, FACTION_COMBAT_ASSIST, TAKEOVER_BYSTANDER,
                                ROLE_PROVISIONER_GIFT_RESPECTED, ROLE_PROVISIONER_GIFT_ALLIED,
                                ROLE_PROVISIONER_GIFT_FRIEND, ROLE_PROVISIONER_GIFT_CLOSE,
                                ROLE_PROVISIONER_GIFT_BONDED -> new Object[]{Component.literal("SpecificArgument")};
                        default -> new Object[0];
                    };
                    // Introduction callers deliberately supply no name, just like wheel gifting.
                    Object[] args = addressed ? (Object[]) address.invoke(null, mob, owner, cue, extra) : extra;
                    Component line = Component.translatable(entry.getKey(), args);
                    String rendered = line.getString();
                    check(!placeholder.matcher(rendered).find(), locale + ": " + entry.getKey() + " -> " + rendered);
                    if (cue == Cue.RELATIONSHIP_ESTABLISHED || cue == Cue.RELATIONSHIP_DIET_ESTABLISHED) {
                        check(args.length == 3 && args[0] instanceof Component && args[1] instanceof Component,
                                "Introductions retain relationship address and player identity");
                        check(((Component)args[1]).getString().equals(owner.getDisplayName().getString())
                                        && ((Component)args[2]).getString().equals(CreatureIdentity.introductionName(mob).getString())
                                        && rendered.contains("Drift"),
                                locale + ": introduction uses the speaker's name: " + entry.getKey());
                    }
                    var packet = new TelepathyDialoguePacket(Component.translatable(
                            "message.changed_synergy.npc_dialogue", mob.getDisplayName(), line),
                            mob.getDisplayName(), line, 0x33AAFF);
                    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
                    try {
                        TelepathyDialoguePacket.encode(packet, buffer);
                        var decoded = TelepathyDialoguePacket.decode(buffer);
                        check(decoded.dialogue().getString().equals(rendered)
                                        && decoded.message().getString().equals(packet.message().getString()),
                                locale + ": network preserves translated arguments: " + entry.getKey());
                    } finally {
                        buffer.release();
                    }
                    formatted++;
                }
            }
            System.out.println("DIALOGUE_PLACEHOLDERS_PASS: " + formatted);
            var data = PlayerOutpostData.get(event.getServer());
            BlockPos marker = new BlockPos(0, 80, 0);
            data.all().stream().filter(existing -> existing.marker.equals(marker))
                    .map(existing -> existing.owner).toList().forEach(data::disband);
            level.setBlock(marker, Blocks.BELL.defaultBlockState(), 3);
            check(data.claim(level, owner.getUUID(), marker).equals("campfire"), "New bell claim rejected");
            level.setBlock(marker, Blocks.CAMPFIRE.defaultBlockState(), 3);
            check(data.claim(level, owner.getUUID(), marker).equals("claimed"), "Campfire outpost claim");
            var outpost = data.byOwner(owner.getUUID()).orElseThrow();
            check(outpost.campfireMarker && outpost.active(level), "Campfire anchor active");
            level.setBlock(marker, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false), 3);
            check(outpost.active(level), "Extinguishing does not deactivate campfire outpost");
            level.setBlock(marker, Blocks.SOUL_CAMPFIRE.defaultBlockState(), 3);
            check(outpost.active(level), "Soul campfire remains a valid anchor");
            level.setBlock(marker, Blocks.BELL.defaultBlockState(), 3);
            check(!outpost.active(level) && data.claim(level, owner.getUUID(), marker).equals("campfire"),
                    "Campfire home cannot be replaced by a bell");
            level.setBlock(marker, Blocks.CAMPFIRE.defaultBlockState(), 3);
            check(data.add(outpost, mob.getUUID(), "Member", level.dimension().location().toString(), marker,
                    PlayerOutpostSupplyType.ORANGE), "Add resident");
            PlayerOutpostService.applyRole(mob, owner, PlayerOutpostData.Role.RESIDENT);
            UUID unloaded = UUID.randomUUID();
            check(data.add(outpost, unloaded, "Unloaded member", level.dimension().location().toString(), marker,
                    PlayerOutpostSupplyType.ORANGE), "Add unloaded resident");
            owner.connection = new ServerGamePacketListenerImpl(event.getServer(),
                    new Connection(PacketFlow.SERVERBOUND), owner);
            PlayerOutpostMenu.open(owner, 1);
            var menu = (PlayerOutpostMenu) owner.containerMenu;
            var death = new LivingDeathEvent(mob, mob.damageSources().generic());
            death.setCanceled(true);
            PlayerOutpostService.onDeath(death);
            check(data.byMember(mob.getUUID()).isPresent(), "Canceled death retains resident");
            death.setCanceled(false);
            PlayerOutpostService.onDeath(death);
            check(!outpost.members.containsKey(mob.getUUID()) && data.byMember(mob.getUUID()).isEmpty(), "Death clears both indexes");
            check(!PlayerOutpostService.assigned(mob), "Death clears stored assignment");
            menu.broadcastChanges();
            var updated = (PlayerOutpostMenu) owner.containerMenu;
            check(updated != menu && updated.members().size() == 1
                    && updated.members().get(updated.selected()).id().equals(unloaded),
                    "Open menu refreshes while preserving selected survivor");
            check(!menu.clickMenuButton(owner, PlayerOutpostMenu.ACTION_BASE + PlayerOutpostData.Role.GUARD.ordinal()),
                    "Stale menu action rejected");
            check(outpost.members.get(unloaded).role == PlayerOutpostData.Role.RESIDENT,
                    "Stale action cannot change the next member");
            checkCampWorkMenu(owner, outpost, (PlayerOutpostMenu)owner.containerMenu, unloaded);
            PlayerOutpostService.onDeath(death);
            var load = PlayerOutpostData.class.getDeclaredMethod("load", CompoundTag.class);
            load.setAccessible(true);
            var restored = (PlayerOutpostData) load.invoke(null, data.save(new CompoundTag()));
            var restoredHome = restored.byOwner(owner.getUUID()).orElseThrow();
            check(restoredHome.campfireMarker && restoredHome.active(level), "Campfire anchor survives reload");
            var savedMembers = restoredHome.members;
            check(savedMembers.size() == 1 && savedMembers.containsKey(unloaded),
                    "Cleanup persists after reload without removing unloaded residents");
            var legacyRoot = data.save(new CompoundTag());
            var legacyTag = legacyRoot.getList("Outposts", net.minecraft.nbt.Tag.TAG_COMPOUND)
                    .stream().map(tag -> (CompoundTag)tag)
                    .filter(tag -> tag.getUUID("Owner").equals(owner.getUUID())).findFirst().orElseThrow();
            legacyTag.remove("CampfireMarker");
            legacyTag.putLong("Storage", marker.offset(2, 0, 0).asLong());
            legacyTag.putLong("Bed", marker.offset(3, 0, 0).asLong());
            var legacy = (PlayerOutpostData) load.invoke(null, legacyRoot);
            var legacyHome = legacy.byOwner(owner.getUUID()).orElseThrow();
            level.setBlock(marker, Blocks.BELL.defaultBlockState(), 3);
            check(!legacyHome.campfireMarker && legacyHome.active(level)
                    && legacy.byMember(unloaded).orElseThrow() == legacyHome
                    && legacyHome.storage.equals(marker.offset(2, 0, 0))
                    && legacyHome.beds.contains(marker.offset(3, 0, 0)),
                    "Old bell saves retain active anchor, facilities and members");
            check(legacy.claim(level, owner.getUUID(), marker).equals("claimed"),
                    "Legacy owner can reaffirm the same bell anchor");
            check(legacy.claim(level, UUID.randomUUID(), marker).equals("campfire"),
                    "Existing bell cannot establish a different owner's home");
            BlockPos moved = marker.offset(100, 0, 0);
            level.setBlock(moved, Blocks.BELL.defaultBlockState(), 3);
            check(legacy.claim(level, owner.getUUID(), moved).equals("campfire"),
                    "Legacy home cannot relocate to another bell");
            level.setBlock(marker, Blocks.CAMPFIRE.defaultBlockState(), 3);
            check(legacy.claim(level, owner.getUUID(), marker).equals("claimed")
                    && legacyHome.campfireMarker && legacyHome.active(level)
                    && legacyHome.storage.equals(marker.offset(2, 0, 0)),
                    "Legacy bell can migrate in place to campfire without losing linked facilities");
            level.setBlock(moved, Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false), 3);
            check(legacy.claim(level, owner.getUUID(), moved).equals("claimed") && legacyHome.active(level),
                    "Unlit soul campfire can establish or relocate a home");
            data.disband(owner.getUUID());
            System.out.println("OUTPOST_DEATH_CLEANUP_PASS");
            System.out.println("OUTPOST_CAMPFIRE_LEGACY_PASS");
            checkPileCollection(owner);
            checkWildCacheAdvancement(owner);
            System.out.println("DIALOGUE_OUTPOST_REGRESSION_PASS");
        } catch (Throwable failure) {
            System.err.println("DIALOGUE_OUTPOST_REGRESSION_FAIL");
            failure.printStackTrace();
        } finally {
            Language.inject(original);
            event.getServer().halt(false);
        }
    }

    private static void checkWildCacheAdvancement(ServerPlayer player) {
        var level = player.serverLevel();
        var position = level.getSharedSpawnPos().above(35);
        var old = level.getBlockState(position);
        var oldOther = level.getBlockState(position.east());
        var wild = net.ltxprogrammer.changed.init.ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        var data = net.parkabird.changedsynergy.ai.CreatureCommunityData.get(player.server);
        var saved = data.save(new CompoundTag());
        var rules = level.getGameRules();
        var lifeKey = net.parkabird.changedsynergy.init.ChangedSynergyGameRules.CREATURE_LIFE;
        var reputationKey = net.parkabird.changedsynergy.init.ChangedSynergyGameRules.FACTION_REPUTATION;
        boolean life = rules.getBoolean(lifeKey), reputation = rules.getBoolean(reputationKey);
        var id = ResourceLocation.fromNamespaceAndPath("changed_synergy", "mysterious_foraging_spot");
        var advancement = player.server.getAdvancements().getAdvancement(id);
        check(advancement != null, "Wild-outpost advancement registered");
        try {
            rules.getRule(lifeKey).set(true, player.server);
            rules.getRule(reputationKey).set(true, player.server);
            player.getAdvancements().revoke(advancement, "complete");
            player.setPos(Vec3.atBottomCenterOf(position));
            wild.setPos(Vec3.atBottomCenterOf(position));
            wild.setNoAi(true);
            level.addFreshEntity(wild);
            level.setBlock(position, Blocks.BARREL.defaultBlockState(), 2);
            net.parkabird.changedsynergy.ai.CreatureCommunityData.markCache(wild, position);
            var cache = (net.minecraft.world.Container)level.getBlockEntity(position);
            check(!net.parkabird.changedsynergy.ai.CreatureCacheGuardService.handleCacheAccess(level, player, position)
                            && !player.getAdvancements().getOrStartProgress(advancement).isDone(),
                    "Rejected cache use does not award an advancement without opening the container");
            var opened = net.minecraft.world.inventory.ChestMenu.threeRows(10, player.getInventory(), cache);
            net.parkabird.changedsynergy.event.CreatureCacheGuardEvents.onCacheOpened(
                    new net.minecraftforge.event.entity.player.PlayerContainerEvent.Open(player, opened));
            check(player.getAdvancements().getOrStartProgress(advancement).isDone(),
                    "Actual wild-container opening awards at any faction standing without a live guard");
            opened.removed(player);
            player.getAdvancements().revoke(advancement, "complete");
            rules.getRule(lifeKey).set(false, player.server);
            rules.getRule(reputationKey).set(false, player.server);
            opened = net.minecraft.world.inventory.ChestMenu.threeRows(11, player.getInventory(), cache);
            net.parkabird.changedsynergy.event.CreatureCacheGuardEvents.onCacheOpened(
                    new net.minecraftforge.event.entity.player.PlayerContainerEvent.Open(player, opened));
            check(player.getAdvancements().getOrStartProgress(advancement).isDone(),
                    "Previously registered wild caches still award when NPC life and reputation are disabled");
            opened.removed(player);
            player.getAdvancements().revoke(advancement, "complete");
            var unrelated = new net.minecraft.world.SimpleContainer(27);
            opened = net.minecraft.world.inventory.ChestMenu.threeRows(12, player.getInventory(), unrelated);
            net.parkabird.changedsynergy.event.CreatureCacheGuardEvents.onCacheOpened(
                    new net.minecraftforge.event.entity.player.PlayerContainerEvent.Open(player, opened));
            check(!player.getAdvancements().getOrStartProgress(advancement).isDone(),
                    "Unrelated and player-owned inventories do not count as wild outposts");
            opened.removed(player);
            check(!net.parkabird.changedsynergy.ai.CreatureCommunityData.isCacheContainer(
                    player.server.getLevel(net.minecraft.world.level.Level.NETHER), cache),
                    "Wild-cache identity cannot leak across dimensions");
            level.setBlock(position, Blocks.CHEST.defaultBlockState(), 2);
            level.setBlock(position.east(), Blocks.CHEST.defaultBlockState(), 2);
            var first = (net.minecraft.world.Container)level.getBlockEntity(position);
            var second = (net.minecraft.world.Container)level.getBlockEntity(position.east());
            var doubleChest = new net.minecraft.world.CompoundContainer(second, first);
            check(net.parkabird.changedsynergy.ai.CreatureCommunityData.isCacheContainer(level, doubleChest),
                    "Either half of a double chest identifies its registered wild cache");
            opened = net.minecraft.world.inventory.ChestMenu.sixRows(13, player.getInventory(), doubleChest);
            net.parkabird.changedsynergy.event.CreatureCacheGuardEvents.onCacheOpened(
                    new net.minecraftforge.event.entity.player.PlayerContainerEvent.Open(player, opened));
            check(player.getAdvancements().getOrStartProgress(advancement).isDone(),
                    "Opening a registered double cache actually awards");
            opened.removed(player);
            System.out.println("WILD_CACHE_OPEN_ADVANCEMENT_PASS: rejection, actual open, disabled rules, unrelated, dimension, double chest");
        } finally {
            wild.discard();
            level.setBlock(position, old, 2);
            level.setBlock(position.east(), oldOther, 2);
            rules.getRule(lifeKey).set(life, player.server);
            rules.getRule(reputationKey).set(reputation, player.server);
            // This is a dedicated fixture world; retain only pre-existing community records.
            try {
                var loader = net.parkabird.changedsynergy.ai.CreatureCommunityData.class.getDeclaredMethod("load", CompoundTag.class);
                loader.setAccessible(true);
                Object restored = loader.invoke(null, saved);
                for (String name : new String[]{"communities", "aliases"}) {
                    var field = net.parkabird.changedsynergy.ai.CreatureCommunityData.class.getDeclaredField(name);
                    field.setAccessible(true);
                    @SuppressWarnings("unchecked") var target = (Map<Object, Object>)field.get(data);
                    target.clear(); target.putAll((Map<?, ?>)field.get(restored));
                }
                data.setDirty();
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
    }

    private static void checkCampWorkMenu(ServerPlayer owner, PlayerOutpostData.Outpost outpost,
                                          PlayerOutpostMenu menu, UUID selected) throws ReflectiveOperationException {
        var member = outpost.members.get(selected);
        check(member.role == PlayerOutpostData.Role.RESIDENT
                        && !menu.clickMenuButton(owner,
                                PlayerOutpostMenu.MAIN_WORK_BASE + PlayerOutpostData.Work.HUNT.ordinal())
                        && !menu.clickMenuButton(owner,
                                PlayerOutpostMenu.BACKUP_WORK_BASE + PlayerOutpostData.Work.FARM.ordinal())
                        && member.work == PlayerOutpostData.Work.GATHER && member.backupWork == null,
                "Work buttons cannot change a non-supplier's assignment");
        check(menu.clickMenuButton(owner,
                        PlayerOutpostMenu.ACTION_BASE + PlayerOutpostData.Role.SUPPLY.ordinal())
                        && owner.containerMenu == menu && member.role == PlayerOutpostData.Role.SUPPLY,
                "Role changes keep the management menu open");
        check(menu.clickMenuButton(owner,
                        PlayerOutpostMenu.MAIN_WORK_BASE + PlayerOutpostData.Work.HUNT.ordinal())
                        && menu.clickMenuButton(owner,
                                PlayerOutpostMenu.BACKUP_WORK_BASE + PlayerOutpostData.Work.FARM.ordinal())
                        && member.work == PlayerOutpostData.Work.HUNT
                        && member.backupWork == PlayerOutpostData.Work.FARM,
                "Work orders update the authoritative member and retain the selected supplier");
        var stranger = new ServerPlayer(owner.server, owner.serverLevel(),
                new GameProfile(UUID.randomUUID(), "OtherCampOwner"));
        check(!menu.clickMenuButton(stranger,
                        PlayerOutpostMenu.MAIN_WORK_BASE + PlayerOutpostData.Work.COOK.ordinal())
                        && !menu.clickMenuButton(stranger, PlayerOutpostMenu.MEALS)
                        && member.work == PlayerOutpostData.Work.HUNT && outpost.meals,
                "Another player cannot issue work orders or change camp policy through this menu");
        check(!menu.clickMenuButton(owner, 120)
                        && menu.clickMenuButton(owner, PlayerOutpostMenu.MEALS)
                        && !outpost.meals,
                "Retired fish policy is rejected while the owner can still toggle meals");

        member.workStatus = "fire_full";
        var opening = PlayerOutpostMenu.class.getDeclaredMethod("writeOpeningData", FriendlyByteBuf.class,
                ServerPlayer.class, PlayerOutpostData.Outpost.class, int.class);
        opening.setAccessible(true);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        PlayerOutpostMenu received;
        try {
            opening.invoke(null, buffer, owner, outpost, menu.selected());
            received = new PlayerOutpostMenu(200, owner.getInventory(), buffer);
            check(buffer.readableBytes() == 0, "Opening data is decoded without leftover wire fields");
        } finally {
            buffer.release();
        }
        var view = received.members().get(received.selected());
        check(view.id().equals(selected) && view.role() == PlayerOutpostData.Role.SUPPLY
                        && view.work() == PlayerOutpostData.Work.HUNT
                        && view.backupWork() == PlayerOutpostData.Work.FARM
                        && view.workStatus().equals("fire_full") && !view.loaded()
                        && !received.meals(),
                "Opening packet preserves work, backup, status and meal policy");

        ContainerListener listener = new ContainerListener() {
            @Override public void slotChanged(AbstractContainerMenu source, int slot, ItemStack stack) {}
            @Override public void dataChanged(AbstractContainerMenu source, int index, int value) {
                received.setData(index, value);
            }
        };
        var lastSync = PlayerOutpostMenu.class.getDeclaredField("lastSync");
        lastSync.setAccessible(true);
        long originalSync = lastSync.getLong(menu);
        menu.addSlotListener(listener);
        try {
            member.workStatus = "hunting";
            lastSync.setLong(menu, owner.serverLevel().getGameTime() - 20L);
            menu.broadcastChanges();
            check(owner.containerMenu == menu && received.members().get(received.selected()).workStatus().equals("hunting")
                            && received.members().get(received.selected()).work() == PlayerOutpostData.Work.HUNT,
                    "Vanilla container data updates work status without reopening the screen");
            check(menu.clickMenuButton(owner,
                            PlayerOutpostMenu.BACKUP_WORK_BASE + PlayerOutpostData.Work.values().length)
                            && member.backupWork == null,
                    "The None backup button clears only backup work");
            menu.broadcastChanges();
            check(received.members().get(received.selected()).backupWork() == null
                            && received.members().get(received.selected()).work() == PlayerOutpostData.Work.HUNT,
                    "Cleared backup is synchronized while main work remains unchanged");
        } finally {
            menu.removeSlotListener(listener);
            lastSync.setLong(menu, originalSync);
        }
        boolean enabled = ChangedSynergyConfig.COMMON.playerOutposts.get();
        ChangedSynergyConfig.COMMON.playerOutposts.set(false);
        try {
            check(!menu.clickMenuButton(owner, 120) && !outpost.meals,
                    "An already-open menu cannot bypass the server's disabled outpost setting");
        } finally {
            ChangedSynergyConfig.COMMON.playerOutposts.set(enabled);
        }
        System.out.println("OUTPOST_WORK_MENU_WIRE_PASS");
    }

    private static void checkPileCollection(ServerPlayer player) throws ReflectiveOperationException {
        var level = player.serverLevel();
        BlockPos pos = new BlockPos(4, 80, 0);
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        var countMethod = PlayerOrangeHarvestEvents.class.getDeclaredMethod("pileCount",
                net.minecraft.world.level.block.state.BlockState.class);
        countMethod.setAccessible(true);
        int tested = 0;
        for (String[] pair : new String[][]{{"changed:dropped_orange", "changed:orange"},
                {"changed:canned_soup", "changed:canned_soup"},
                {"changed_addon:snepsi_can", "changed_addon:snepsi"},
                {"changed_addon:foxta_can", "changed_addon:foxta"}}) {
            var block = ForgeRegistries.BLOCKS.getValue(ResourceLocation.parse(pair[0]));
            if (block == null || block == Blocks.AIR) continue;
            var item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(pair[1]));
            check(item != null && item != Items.AIR, "Pile has its original collectible item: " + pair[0]);
            var state = block.defaultBlockState();
            var count = (IntegerProperty) countMethod.invoke(null, state);
            check(count != null, "Shared pile handler recognizes " + pair[0]);
            state = state.setValue(count, 2).setValue(BlockStateProperties.WATERLOGGED, true);
            if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
            }
            level.setBlock(pos, state, 3);
            player.getInventory().clearContent();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            var held = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
            PlayerOrangeHarvestEvents.onUseBlock(held);
            check(!held.isCanceled() && level.getBlockState(pos).equals(state),
                    "Held-item use retains original pile behavior: " + pair[0]);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            var offhand = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.OFF_HAND, pos, hit);
            PlayerOrangeHarvestEvents.onUseBlock(offhand);
            check(!offhand.isCanceled() && level.getBlockState(pos).equals(state),
                    "Offhand does not duplicate pile pickup: " + pair[0]);
            var first = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
            PlayerOrangeHarvestEvents.onUseBlock(first);
            check(first.isCanceled() && player.getInventory().countItem(item) == 1
                    && level.getBlockState(pos).equals(state.setValue(count, 1)),
                    "Take exactly one while preserving water and direction: " + pair[0]);
            player.getInventory().clearContent();
            PlayerOrangeHarvestEvents.onUseBlock(
                    new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit));
            check(player.getInventory().countItem(item) == 1 && level.getBlockState(pos).is(Blocks.WATER)
                    && level.getBlockState(pos.below()).is(Blocks.STONE),
                    "Final pickup removes only pile and restores contained water: " + pair[0]);
            tested++;
        }
        check(tested >= 2, "Original Changed orange and soup piles tested");
        if (net.minecraftforge.fml.ModList.get().isLoaded("changed_addon")) {
            check(tested == 4, "Both Addon Snepsi and Foxta piles tested");
        }
        var rejectingInventory = new ServerPlayer(player.server, level,
                new GameProfile(UUID.randomUUID(), "FullInventory")) {
            @Override public boolean addItem(ItemStack stack) { return false; }
        };
        rejectingInventory.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        var soup = net.ltxprogrammer.changed.init.ChangedBlocks.CANNED_SOUP.get();
        level.setBlock(pos, soup.defaultBlockState(), 3);
        var itemEntities = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                rejectingInventory.getBoundingBox().inflate(4.0D)).size();
        PlayerOrangeHarvestEvents.onUseBlock(new PlayerInteractEvent.RightClickBlock(rejectingInventory,
                InteractionHand.MAIN_HAND, pos,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
        var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                rejectingInventory.getBoundingBox().inflate(4.0D));
        check(drops.size() == itemEntities + 1 && drops.stream().anyMatch(drop -> drop.getItem().is(soup.asItem())
                && drop.getItem().getCount() == 1), "Rejected inventory receives one world drop");
        System.out.println("PLACED_PILE_COLLECTION_PASS: " + tested + " pile types, water, direction and dropped-item fallback");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
