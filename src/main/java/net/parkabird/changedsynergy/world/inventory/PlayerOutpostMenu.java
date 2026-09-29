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
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.PlayerOutpostData;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Member;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Outpost;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Role;
import net.parkabird.changedsynergy.ai.PlayerOutpostService;
import net.parkabird.changedsynergy.ai.PlayerOutpostSupplyType;
import net.parkabird.changedsynergy.init.ChangedSynergyMenus;

/** A read-only client snapshot; every order is validated against SavedData on the server. */
public final class PlayerOutpostMenu extends AbstractContainerMenu {
    public static final Component TITLE = Component.translatable("container.changed_synergy.player_outpost");
    public static final int REMOVE = Role.values().length;
    public static final int ACTION_BASE = 64;
    public static final int TEAM_BASE = 80;
    private final Player player;
    private final List<MemberView> members;
    private final String dimension;
    private final BlockPos marker;
    private final String storage;
    private final String bed;
    private final int stock;
    private final boolean active;
    private int selected;

    public record MemberView(UUID id, String name, Role role, PlayerOutpostSupplyType supplyType, String dimension,
                             BlockPos position, boolean loaded) {
    }

    private PlayerOutpostMenu(int id, Inventory inventory, Outpost outpost, int selected) {
        super(ChangedSynergyMenus.PLAYER_OUTPOST.get(), id);
        player = inventory.player;
        this.selected = selected;
        dimension = outpost.dimension;
        marker = outpost.marker;
        storage = outpost.storage == null ? "—" : outpost.storage.toShortString();
        bed = outpost.bed == null ? "—" : outpost.bed.toShortString();
        ServerPlayer server = (ServerPlayer)inventory.player;
        ServerLevel home = home(server, outpost.dimension);
        active = home != null && outpost.active(home);
        stock = countStock(home, outpost.storage);
        members = new ArrayList<>();
        for (Member member : outpost.members.values()) {
            Entity entity = loaded(server, member.id);
            members.add(new MemberView(member.id, member.name, member.role, member.supplyType,
                    member.dimension, member.lastPosition, entity instanceof ChangedEntity));
        }
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
        selected = buffer.readVarInt();
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
            members.add(new MemberView(idValue, name, role, supplyType, memberDimension, position, loaded));
        }
    }

    public static void open(ServerPlayer player, int selected) {
        if (!ChangedSynergyConfig.COMMON.playerOutposts.get()) return;
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
        buffer.writeUtf(outpost.dimension, 128);
        buffer.writeBlockPos(outpost.marker);
        buffer.writeUtf(outpost.storage == null ? "—" : outpost.storage.toShortString(), 64);
        buffer.writeUtf(outpost.bed == null ? "—" : outpost.bed.toShortString(), 64);
        buffer.writeVarInt(countStock(home, outpost.storage));
        buffer.writeBoolean(home != null && outpost.active(home));
        buffer.writeVarInt(selected);
        buffer.writeVarInt(outpost.members.size());
        for (Member member : outpost.members.values()) {
            buffer.writeUUID(member.id);
            buffer.writeUtf(member.name.length() > 256
                    ? member.name.substring(0, 256) : member.name, 256);
            buffer.writeVarInt(member.role.ordinal());
            buffer.writeVarInt(member.supplyType.ordinal());
            buffer.writeUtf(member.dimension, 128);
            buffer.writeBlockPos(member.lastPosition);
            buffer.writeBoolean(loaded(player, member.id) instanceof ChangedEntity);
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

    public List<MemberView> members() { return List.copyOf(members); }
    public String dimension() { return dimension; }
    public BlockPos marker() { return marker; }
    public String storage() { return storage; }
    public String bed() { return bed; }
    public int stock() { return stock; }
    public boolean active() { return active; }
    public int selected() { return selected; }

    @Override
    public boolean clickMenuButton(Player viewer, int id) {
        if (!(viewer instanceof ServerPlayer server) || viewer != player
                || !ChangedSynergyConfig.COMMON.playerOutposts.get()) return false;
        Outpost outpost = PlayerOutpostData.get(server.server).byOwner(server.getUUID()).orElse(null);
        if (outpost == null) return false;
        if (id >= 0 && id < 64) {
            if (id >= outpost.members.size()) return false;
            selected = id;
            return true;
        }
        if (id >= TEAM_BASE && id < TEAM_BASE + Role.values().length) {
            Role role = Role.values()[id - TEAM_BASE];
            for (Member member : outpost.members.values()) changeRole(server, outpost, member, role);
            open(server, selected);
            return true;
        }
        int action = id - ACTION_BASE;
        if (selected < 0 || selected >= outpost.members.size()
                || action < 0 || action > REMOVE) return false;
        Member member = new ArrayList<>(outpost.members.values()).get(selected);
        if (action == REMOVE) {
            Entity entity = loaded(server, member.id);
            if (entity instanceof ChangedEntity mob) {
                PlayerOutpostService.clearAssignment(mob);
            }
            PlayerOutpostData.get(server.server).remove(outpost, member.id);
        } else changeRole(server, outpost, member, Role.values()[action]);
        open(server, selected);
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
