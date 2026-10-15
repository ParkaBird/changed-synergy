package net.parkabird.changedsynergy.world.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;
import net.parkabird.changedsynergy.ai.PlayerOutpostData;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Member;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Outpost;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Role;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Work;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.CrewJob;
import net.parkabird.changedsynergy.ai.PlayerOutpostService;
import net.parkabird.changedsynergy.ai.PlayerOutpostSupplyType;
import net.parkabird.changedsynergy.init.ChangedSynergyMenus;

/** A read-only client snapshot; every order is validated against SavedData on the server. */
public final class PlayerOutpostMenu extends AbstractContainerMenu {
    private static final int MEMBER_DATA_SIZE = 13;
    public static final Component TITLE = Component.translatable("container.changed_synergy.player_outpost");
    public static final int REMOVE = Role.values().length;
    public static final int ACTION_BASE = 64;
    public static final int TEAM_BASE = 80;
    public static final int MAIN_WORK_BASE = 96;
    public static final int BACKUP_WORK_BASE = 112;
    public static final int CREW_JOB_BASE = 104;
    // Vanilla encodes inventory-button IDs as signed bytes: keep every ID below 128.
    // ID 120 is retired; stale clients cannot toggle the removed fish policy.
    public static final int MEALS = 121;
    public static final int REFRESH = 122;
    public static final int MEAL_CALL = 123;
    private static final List<String> STATUSES = List.of("idle", "travelling", "harvesting", "hunting",
            "cooking", "hauling", "composting", "no_storage", "full_storage", "no_farm",
            "no_hunting", "no_campfire", "no_composter", "no_seeds", "waiting", "combat",
            "offline", "disabled", "eating", "meal_walk", "meal_sit", "meal_no_food",
            "returning", "no_resources", "fire_unlit", "fire_full", "waiting_food", "waiting_crops",
            "fertilizing", "protected", "unreachable", "no_prey", "resting", "fetching_food", "meal_rest", "following", "hunt_cooldown");
    private final Player player;
    private final List<MemberView> members;
    private final List<String> locationDimensions;
    private final String dimension;
    private final BlockPos marker;
    private final String storage;
    private final String bed;
    private final int stock;
    private final boolean active;
    private final String facilities;
    private final SimpleContainerData live;
    private long lastSync;
    private int selected;

    public record MemberView(UUID id, String name, Role role, PlayerOutpostSupplyType supplyType, String dimension,
                             BlockPos position, boolean loaded, Work work, Work backupWork, String workStatus, CrewJob crewJob) {
    }

    private PlayerOutpostMenu(int id, Inventory inventory, Outpost outpost, int selected) {
        super(ChangedSynergyMenus.PLAYER_OUTPOST.get(), id);
        player = inventory.player;
        this.selected = selected;
        dimension = outpost.dimension;
        marker = outpost.marker;
        storage = outpost.storage == null ? "—" : outpost.storage.toShortString();
        ServerPlayer server = (ServerPlayer)inventory.player;
        locationDimensions = locationDimensions(server, outpost);
        ServerLevel home = home(server, outpost.dimension);
        PlayerOutpostData.get(server.server).refreshRestPlaces(home, outpost);
        bed = outpost.beds.size() + " / " + outpost.restPoints.size();
        active = home != null && outpost.active(home);
        stock = countStock(home, outpost.storage);
        facilities = facilities(outpost);
        members = new ArrayList<>();
        for (Member member : outpost.members.values()) {
            Entity entity = loaded(server, member.id);
            members.add(new MemberView(member.id, member.name, member.role, member.supplyType,
                    member.dimension, member.lastPosition, entity instanceof ChangedEntity,
                    member.work, member.backupWork, member.workStatus, member.crewJob));
        }
        live = new SimpleContainerData(3 + members.size() * MEMBER_DATA_SIZE);
        updateLive(server, outpost);
        addDataSlots(live);
    }

    public PlayerOutpostMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        super(ChangedSynergyMenus.PLAYER_OUTPOST.get(), id);
        player = inventory.player;
        dimension = buffer.readUtf(128);
        marker = buffer.readBlockPos();
        storage = buffer.readUtf(64);
        bed = buffer.readUtf(64);
        stock = buffer.readVarInt();
        active = buffer.readBoolean();
        facilities = buffer.readUtf(512);
        boolean meals = buffer.readBoolean();
        selected = buffer.readVarInt();
        locationDimensions = buffer.readList(data -> data.readUtf(128));
        int count = Math.min(64, buffer.readVarInt());
        members = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID idValue = buffer.readUUID();
            String name = buffer.readUtf(256);
            Role role = Role.values()[Math.min(Role.values().length - 1, Math.max(0, buffer.readVarInt()))];
            PlayerOutpostSupplyType supplyType = PlayerOutpostSupplyType.values()[Math.min(
                    PlayerOutpostSupplyType.values().length - 1, Math.max(0, buffer.readVarInt()))];
            String memberDimension = buffer.readUtf(128);
            BlockPos position = buffer.readBlockPos();
            boolean loaded = buffer.readBoolean();
            Work work = work(buffer.readVarInt());
            int backup = buffer.readVarInt();
            String status = status(buffer.readVarInt());
            CrewJob crewJob = crewJob(buffer.readVarInt());
            members.add(new MemberView(idValue, name, role, supplyType, memberDimension, position,
                    loaded, work, backup < 0 ? null : work(backup), status, crewJob));
        }
        live = new SimpleContainerData(3 + members.size() * MEMBER_DATA_SIZE);
        live.set(0, stock);
        live.set(1, active ? 1 : 0);
        live.set(2, meals ? 1 : 0);
        for (int i = 0; i < members.size(); i++) setLiveMember(i, members.get(i));
        addDataSlots(live);
    }

    public static void open(ServerPlayer player, int selected) {
        if (!net.parkabird.changedsynergy.ai.PlayerOutpostService.enabled(player.level())) return;
        Outpost outpost = PlayerOutpostData.get(player.server).byOwner(player.getUUID()).orElse(null);
        if (outpost == null) return;
        int index = Math.max(0, Math.min(selected, Math.max(0, outpost.members.size() - 1)));
        NetworkHooks.openScreen(player,
                new SimpleMenuProvider((id, inventory, viewer) ->
                        new PlayerOutpostMenu(id, inventory, outpost, index), TITLE),
                buffer -> writeOpeningData(buffer, player, outpost, index));
    }

    private static void writeOpeningData(FriendlyByteBuf buffer, ServerPlayer player,
                                         Outpost outpost, int selected) {
        ServerLevel home = home(player, outpost.dimension);
        PlayerOutpostData.get(player.server).refreshRestPlaces(home, outpost);
        buffer.writeUtf(outpost.dimension, 128);
        buffer.writeBlockPos(outpost.marker);
        buffer.writeUtf(outpost.storage == null ? "—" : outpost.storage.toShortString(), 64);
        buffer.writeUtf(outpost.beds.size() + " / " + outpost.restPoints.size(), 64);
        buffer.writeVarInt(countStock(home, outpost.storage));
        buffer.writeBoolean(home != null && outpost.active(home));
        buffer.writeUtf(facilities(outpost), 512);
        buffer.writeBoolean(outpost.meals);
        buffer.writeVarInt(selected);
        buffer.writeCollection(locationDimensions(player, outpost), (data, value) -> data.writeUtf(value, 128));
        buffer.writeVarInt(outpost.members.size());
        for (Member member : outpost.members.values()) {
            Entity entity = loaded(player, member.id);
            buffer.writeUUID(member.id);
            buffer.writeUtf(member.name.length() > 256
                    ? member.name.substring(0, 256) : member.name, 256);
            buffer.writeVarInt(member.role.ordinal());
            buffer.writeVarInt(member.supplyType.ordinal());
            buffer.writeUtf(entity instanceof ChangedEntity ? entity.level().dimension().location().toString() : member.dimension, 128);
            buffer.writeBlockPos(entity instanceof ChangedEntity ? entity.blockPosition() : member.lastPosition);
            buffer.writeBoolean(entity instanceof ChangedEntity);
            buffer.writeVarInt(member.work.ordinal());
            buffer.writeVarInt(member.backupWork == null ? -1 : member.backupWork.ordinal());
            buffer.writeVarInt(statusId(member.workStatus));
            buffer.writeVarInt(member.crewJob.ordinal());
        }
    }

    private static String facilities(Outpost outpost) {
        return (outpost.cooking != null ? "✓" : "—") + " / " + (outpost.composter != null ? "✓" : "—") + " / "
                + (outpost.farmStart != null && outpost.farmEnd != null ? "✓" : "—") + " / "
                + (outpost.huntStart != null && outpost.huntEnd != null ? "✓" : "—") + " / "
                + (outpost.hasGatherArea() ? "✓" : "—") + " / " + (outpost.hasHaulArea() ? "✓" : "—");
    }

    private static Work work(int index) { return Work.values()[Math.max(0, Math.min(Work.values().length - 1, index))]; }
    private static CrewJob crewJob(int index) { return CrewJob.values()[Math.max(0, Math.min(CrewJob.values().length - 1, index))]; }
    private static int statusId(String status) { return Math.max(0, STATUSES.indexOf(status)); }
    private static String status(int index) { return STATUSES.get(Math.max(0, Math.min(STATUSES.size() - 1, index))); }

    private void setLiveMember(int index, MemberView member) {
        int offset = 3 + index * MEMBER_DATA_SIZE;
        live.set(offset, member.role().ordinal());
        live.set(offset + 1, member.work().ordinal());
        live.set(offset + 2, member.backupWork() == null ? -1 : member.backupWork().ordinal());
        live.set(offset + 3, statusId(member.workStatus()));
        live.set(offset + 4, member.loaded() ? 1 : 0);
        live.set(offset + 5, member.crewJob().ordinal());
        setCoordinate(offset + 6, member.position().getX());
        setCoordinate(offset + 8, member.position().getY());
        setCoordinate(offset + 10, member.position().getZ());
        live.set(offset + 12, locationDimensions.indexOf(member.dimension()));
    }

    // Vanilla menu data uses signed shorts. Keep full world coordinates, including negatives.
    private void setCoordinate(int offset, int value) {
        live.set(offset, (short)value);
        live.set(offset + 1, (short)(value >>> 16));
    }

    private int coordinate(int offset) {
        return (live.get(offset) & 0xFFFF) | (live.get(offset + 1) & 0xFFFF) << 16;
    }

    private static List<String> locationDimensions(ServerPlayer player, Outpost outpost) {
        return java.util.stream.Stream.concat(player.server.levelKeys().stream()
                        .map(key -> key.location().toString()), outpost.members.values().stream().map(member -> member.dimension))
                .distinct().sorted().toList();
    }

    private void updateLive(ServerPlayer server, Outpost outpost) {
        ServerLevel home = home(server, outpost.dimension);
        live.set(0, countStock(home, outpost.storage));
        live.set(1, home != null && outpost.active(home) ? 1 : 0);
        live.set(2, outpost.meals ? 1 : 0);
        for (int i = 0; i < members.size(); i++) {
            Member member = outpost.members.get(members.get(i).id());
            Entity entity = member == null ? null : loaded(server, member.id);
            if (member != null) setLiveMember(i, new MemberView(member.id, member.name, member.role,
                    member.supplyType, entity instanceof ChangedEntity ? entity.level().dimension().location().toString() : member.dimension,
                    entity instanceof ChangedEntity ? entity.blockPosition() : member.lastPosition,
                    entity instanceof ChangedEntity, member.work, member.backupWork, member.workStatus, member.crewJob));
        }
    }

    private static ServerLevel home(ServerPlayer player, String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        return id == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    private static int countStock(ServerLevel level, BlockPos pos) {
        Container container = PlayerOutpostData.container(level, pos);
        if (container == null) return -1;
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) count += container.getItem(slot).getCount();
        return count;
    }

    private static Entity loaded(ServerPlayer player, UUID id) {
        for (ServerLevel level : player.server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) return entity;
        }
        return null;
    }

    public List<MemberView> members() {
        List<MemberView> snapshot = new ArrayList<>(members.size());
        for (int i = 0; i < members.size(); i++) {
            MemberView member = members.get(i);
            int offset = 3 + i * MEMBER_DATA_SIZE;
            int backup = live.get(offset + 2);
            int location = live.get(offset + 12);
            snapshot.add(new MemberView(member.id(), member.name(),
                    Role.values()[Math.max(0, Math.min(Role.values().length - 1, live.get(offset)))],
                    member.supplyType(), location >= 0 && location < locationDimensions.size()
                            ? locationDimensions.get(location) : member.dimension(),
                    new BlockPos(coordinate(offset + 6), coordinate(offset + 8), coordinate(offset + 10)), live.get(offset + 4) != 0,
                    work(live.get(offset + 1)), backup < 0 ? null : work(backup), status(live.get(offset + 3)), crewJob(live.get(offset + 5))));
        }
        return snapshot;
    }
    public String dimension() { return dimension; }
    public BlockPos marker() { return marker; }
    public String storage() { return storage; }
    public String bed() { return bed; }
    public int stock() { return live.get(0); }
    public boolean active() { return live.get(1) != 0; }
    public boolean meals() { return live.get(2) != 0; }
    public String facilities() { return facilities; }
    public int selected() { return selected; }

    @Override
    public void broadcastChanges() {
        if (player instanceof ServerPlayer server) {
            PlayerOutpostData.get(server.server).byOwner(server.getUUID())
                    .ifPresent(outpost -> {
                        if (!refreshRosterIfChanged(server, outpost) && server.level().getGameTime() - lastSync >= 20) {
                            lastSync = server.level().getGameTime();
                            updateLive(server, outpost);
                        }
                    });
        }
        super.broadcastChanges();
    }

    private boolean refreshRosterIfChanged(ServerPlayer server, Outpost outpost) {
        List<UUID> current = new ArrayList<>(outpost.members.keySet());
        if (current.equals(members.stream().map(MemberView::id).toList())) return false;
        UUID chosen = selected >= 0 && selected < members.size() ? members.get(selected).id() : null;
        open(server, Math.max(0, current.indexOf(chosen)));
        return true;
    }

    @Override
    public boolean clickMenuButton(Player viewer, int id) {
        if (!(viewer instanceof ServerPlayer server) || viewer != player
                || !net.parkabird.changedsynergy.ai.PlayerOutpostService.enabled(player.level())) return false;
        Outpost outpost = PlayerOutpostData.get(server.server).byOwner(server.getUUID()).orElse(null);
        if (outpost == null) return false;
        // Reject buttons from an old snapshot rather than ordering a different member.
        if (refreshRosterIfChanged(server, outpost)) return false;
        if (id == REFRESH) {
            open(server, selected);
            return true;
        }
        if (id == MEAL_CALL) {
            return server.server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "changedsynergy outpost meal") > 0;
        }
        if (id == MEALS) {
            outpost.meals = !outpost.meals;
            PlayerOutpostData.get(server.server).setDirty();
            updateLive(server, outpost);
            return true;
        }
        if (id >= 0 && id < 64) {
            if (id >= outpost.members.size()) return false;
            selected = id;
            return true;
        }
        if (id >= TEAM_BASE && id < TEAM_BASE + Role.values().length) {
            Role role = Role.values()[id - TEAM_BASE];
            for (Member member : outpost.members.values()) changeRole(server, outpost, member, role);
            updateLive(server, outpost);
            return true;
        }
        boolean mainWork = id >= MAIN_WORK_BASE && id < MAIN_WORK_BASE + Work.values().length;
        if (id >= CREW_JOB_BASE && id < CREW_JOB_BASE + CrewJob.values().length) {
            if (selected < 0 || selected >= members.size()) return false;
            Member member = outpost.members.get(members.get(selected).id());
            if (member == null || member.role != Role.CREW) return false;
            if (!PlayerOutpostData.get(server.server).crewJob(outpost, member.id, CrewJob.values()[id - CREW_JOB_BASE])) return false;
            Entity entity = loaded(server, member.id);
            if (entity instanceof ChangedEntity mob) PlayerOutpostService.applyRole(mob, server, Role.CREW);
            updateLive(server, outpost);
            return true;
        }
        boolean backupWork = id >= BACKUP_WORK_BASE && id <= BACKUP_WORK_BASE + Work.values().length;
        if (mainWork || backupWork) {
            if (selected < 0 || selected >= members.size()) return false;
            Member member = outpost.members.get(members.get(selected).id());
            if (member == null || member.role != Role.SUPPLY) return false;
            Work work = mainWork ? Work.values()[id - MAIN_WORK_BASE] : member.work;
            int backupId = id - BACKUP_WORK_BASE;
            Work backup = mainWork ? member.backupWork
                    : backupId == Work.values().length ? null : Work.values()[backupId];
            if (backup == work) backup = null;
            if (!PlayerOutpostData.get(server.server).setWork(outpost, member.id, work, backup)) return false;
            Entity entity = loaded(server, member.id);
            if (entity instanceof ChangedEntity mob) {
                mob.getNavigation().stop();
                PlayerOutpostService.applyRole(mob, server, Role.SUPPLY);
            }
            updateLive(server, outpost);
            return true;
        }
        int action = id - ACTION_BASE;
        if (selected < 0 || selected >= outpost.members.size()
                || action < 0 || action > REMOVE) return false;
        Member member = outpost.members.get(members.get(selected).id());
        if (member == null) return false;
        if (action == REMOVE) {
            Entity entity = loaded(server, member.id);
            if (entity instanceof ChangedEntity mob) {
                PlayerOutpostService.clearAssignment(mob);
            }
            PlayerOutpostData.get(server.server).remove(outpost, member.id);
        } else changeRole(server, outpost, member, Role.values()[action]);
        if (action == REMOVE) open(server, selected);
        else updateLive(server, outpost);
        return true;
    }

    private static void changeRole(ServerPlayer owner, Outpost outpost, Member member, Role role) {
        Entity entity = loaded(owner, member.id);
        BlockPos position = entity instanceof ChangedEntity mob ? mob.blockPosition() : member.lastPosition;
        Role old = member.role;
        if (!PlayerOutpostData.get(owner.server).role(outpost, member.id, role, position)) {
            owner.sendSystemMessage(Component.translatable("command.changed_synergy.outpost.error.crew_full"));
            return;
        }
        if (entity instanceof ChangedEntity mob) {
            if (old == Role.SUPPLY && role != Role.SUPPLY) PlayerOutpostService.releaseCargo(mob);
            PlayerOutpostService.applyRole(mob, owner, role);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player viewer, int slot) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player viewer) { return viewer.isAlive() && !viewer.isRemoved(); }
}
