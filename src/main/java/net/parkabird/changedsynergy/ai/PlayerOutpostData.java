package net.parkabird.changedsynergy.ai;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.parkabird.changedsynergy.ChangedSynergyConfig;

/** Player-owned home records. No wild-community stock or chunk tickets are used. */
public final class PlayerOutpostData extends SavedData {
    public static final int RADIUS = 32;
    private static final String DATA_NAME = "changed_synergy_player_outposts";
    private final Map<UUID, Outpost> outposts = new LinkedHashMap<>();
    private final Map<UUID, Outpost> membership = new LinkedHashMap<>();

    public enum Role {
        RESIDENT, GUARD, SUPPLY, CREW, HOLD, RETURN, RETREAT;

        public static Optional<Role> parse(String value) {
            try {
                return Optional.of(valueOf(value.toUpperCase(java.util.Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        }
    }

    public static final class Member {
        public final UUID id;
        public String name;
        public Role role;
        public String dimension;
        public BlockPos lastPosition;
        public BlockPos holdPosition;
        public PlayerOutpostSupplyType supplyType;

        private Member(UUID id, String name, Role role, String dimension, BlockPos position) {
            this.id = id;
            this.name = name;
            this.role = role;
            this.dimension = dimension;
            this.lastPosition = position;
            this.supplyType = PlayerOutpostSupplyType.ORANGE;
        }
    }

    public static final class Outpost {
        public final UUID owner;
        public String dimension;
        public BlockPos marker;
        public BlockPos storage;
        public BlockPos bed;
        public final Map<UUID, Member> members = new LinkedHashMap<>();

        private Outpost(UUID owner, String dimension, BlockPos marker) {
            this.owner = owner;
            this.dimension = dimension;
            this.marker = marker;
        }

        public boolean active(ServerLevel level) {
            return dimension.equals(level.dimension().location().toString())
                    && level.hasChunkAt(marker)
                    && level.getBlockState(marker).is(Blocks.BELL);
        }

        public boolean contains(BlockPos pos) {
            return marker.distSqr(pos) <= RADIUS * RADIUS;
        }
    }

    private PlayerOutpostData() {
    }

    public static PlayerOutpostData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                PlayerOutpostData::load, PlayerOutpostData::new, DATA_NAME);
    }

    public Optional<Outpost> byOwner(UUID owner) {
        return Optional.ofNullable(outposts.get(owner));
    }

    public Optional<Outpost> byMember(UUID member) {
        return Optional.ofNullable(membership.get(member));
    }

    public Collection<Outpost> all() {
        return outposts.values();
    }

    /** Treats both halves of a double chest as one registered store. */
    public static Container container(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !level.hasChunkAt(pos)) return null;
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chest) {
            return ChestBlock.getContainer(chest, state, level, pos, true);
        }
        return level.getBlockEntity(pos) instanceof Container container ? container : null;
    }

    /** Keeps later wild outpost placement away from a player's active home. */
    public static boolean isAreaClaimed(ServerLevel level, BlockPos pos, double radius) {
        if (!ChangedSynergyConfig.COMMON.playerOutposts.get()) return false;
        String dimension = level.dimension().location().toString();
        double radiusSqr = radius * radius;
        return get(level.getServer()).outposts.values().stream()
                .anyMatch(outpost -> outpost.dimension.equals(dimension)
                        && (!level.hasChunkAt(outpost.marker) || outpost.active(level))
                        && distanceSqr(outpost.marker, pos) < radiusSqr);
    }

    public String claim(ServerLevel level, UUID owner, BlockPos marker) {
        if (!ChangedSynergyConfig.COMMON.playerOutposts.get()) return "disabled";
        if (!level.getBlockState(marker).is(Blocks.BELL)) return "bell";
        String dimension = level.dimension().location().toString();
        for (Outpost other : outposts.values()) {
            if (!other.owner.equals(owner) && other.dimension.equals(dimension)
                    && (!level.hasChunkAt(other.marker) || other.active(level))
                    && distanceSqr(other.marker, marker) < (RADIUS * 2) * (RADIUS * 2)) {
                return "overlap";
            }
        }
        if (CreatureCommunityData.isCacheAreaClaimed(level, marker, RADIUS + 16)) return "overlap";
        Outpost previous = outposts.get(owner);
        if (previous == null) {
            outposts.put(owner, new Outpost(owner, dimension, marker.immutable()));
        } else if (previous.dimension.equals(dimension) && previous.marker.equals(marker)) {
            return "claimed";
        } else {
            previous.dimension = dimension;
            previous.marker = marker.immutable();
            previous.storage = null;
            previous.bed = null;
            for (Member member : previous.members.values()) {
                if (member.role != Role.CREW) member.role = Role.RETURN;
            }
        }
        setDirty();
        return "claimed";
    }

    public void setStorage(Outpost outpost, BlockPos pos) {
        outpost.storage = pos.immutable();
        setDirty();
    }

    public void setBed(Outpost outpost, BlockPos pos) {
        outpost.bed = pos.immutable();
        setDirty();
    }

    public boolean add(Outpost outpost, UUID id, String name, String dimension, BlockPos position,
                       PlayerOutpostSupplyType supplyType) {
        if (byMember(id).isPresent() || outpost.members.size() >= ChangedSynergyConfig.COMMON.playerOutpostResidents.get()) {
            return false;
        }
        Member member = new Member(id, name, Role.RESIDENT, dimension, position.immutable());
        member.supplyType = supplyType;
        outpost.members.put(id, member);
        membership.put(id, outpost);
        setDirty();
        return true;
    }

    public boolean role(Outpost outpost, UUID id, Role role, BlockPos position) {
        Member member = outpost.members.get(id);
        if (member == null) return false;
        if (role == Role.CREW && member.role != Role.CREW
                && outpost.members.values().stream().filter(m -> m.role == Role.CREW).count()
                        >= ChangedSynergyConfig.COMMON.playerOutpostCrew.get()) return false;
        member.role = role;
        member.holdPosition = role == Role.HOLD ? position : null;
        setDirty();
        return true;
    }

    public boolean remove(Outpost outpost, UUID id) {
        if (outpost.members.remove(id) == null) return false;
        membership.remove(id);
        setDirty();
        return true;
    }

    public boolean disband(UUID owner) {
        Outpost outpost = outposts.remove(owner);
        if (outpost == null) return false;
        outpost.members.keySet().forEach(membership::remove);
        setDirty();
        return true;
    }

    public void updateLocation(Member member, ServerLevel level, BlockPos pos, String name) {
        String dimension = level.dimension().location().toString();
        if (!dimension.equals(member.dimension) || !pos.equals(member.lastPosition) || !name.equals(member.name)) {
            member.dimension = dimension;
            member.lastPosition = pos.immutable();
            member.name = name;
            setDirty();
        }
    }

    public void updateSupplyType(Member member, PlayerOutpostSupplyType type) {
        if (member.supplyType != type) {
            member.supplyType = type;
            setDirty();
        }
    }

    public void removeDestroyedMarker(ServerLevel level, BlockPos pos) {
        String dimension = level.dimension().location().toString();
        for (Outpost outpost : outposts.values()) {
            if (outpost.dimension.equals(dimension) && outpost.marker.equals(pos)) {
                outpost.storage = null;
                outpost.bed = null;
                setDirty();
            } else if (outpost.dimension.equals(dimension) && pos.equals(outpost.storage)) {
                outpost.storage = null;
                setDirty();
            } else if (outpost.dimension.equals(dimension) && pos.equals(outpost.bed)) {
                outpost.bed = null;
                setDirty();
            }
        }
    }

    private static double distanceSqr(BlockPos a, BlockPos b) {
        double x = a.getX() - b.getX();
        double z = a.getZ() - b.getZ();
        return x * x + z * z;
    }

    private static PlayerOutpostData load(CompoundTag root) {
        PlayerOutpostData data = new PlayerOutpostData();
        ListTag list = root.getList("Outposts", Tag.TAG_COMPOUND);
        for (Tag entry : list) {
            CompoundTag tag = (CompoundTag)entry;
            if (!tag.hasUUID("Owner") || !tag.contains("Marker", Tag.TAG_LONG)) continue;
            UUID owner = tag.getUUID("Owner");
            Outpost outpost = new Outpost(owner, tag.getString("Dimension"), BlockPos.of(tag.getLong("Marker")));
            if (tag.contains("Storage", Tag.TAG_LONG)) outpost.storage = BlockPos.of(tag.getLong("Storage"));
            if (tag.contains("Bed", Tag.TAG_LONG)) outpost.bed = BlockPos.of(tag.getLong("Bed"));
            for (Tag memberEntry : tag.getList("Members", Tag.TAG_COMPOUND)) {
                CompoundTag memberTag = (CompoundTag)memberEntry;
                if (!memberTag.hasUUID("Id")) continue;
                Role role = Role.parse(memberTag.getString("Role")).orElse(Role.RESIDENT);
                Member member = new Member(memberTag.getUUID("Id"), memberTag.getString("Name"), role,
                        memberTag.getString("Dimension"), BlockPos.of(memberTag.getLong("Position")));
                member.supplyType = PlayerOutpostSupplyType.parse(memberTag.getString("SupplyType"));
                if (memberTag.contains("Hold", Tag.TAG_LONG)) member.holdPosition = BlockPos.of(memberTag.getLong("Hold"));
                outpost.members.put(member.id, member);
                data.membership.put(member.id, outpost);
            }
            data.outposts.put(owner, outpost);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        ListTag list = new ListTag();
        for (Outpost outpost : outposts.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Owner", outpost.owner);
            tag.putString("Dimension", outpost.dimension);
            tag.putLong("Marker", outpost.marker.asLong());
            if (outpost.storage != null) tag.putLong("Storage", outpost.storage.asLong());
            if (outpost.bed != null) tag.putLong("Bed", outpost.bed.asLong());
            ListTag members = new ListTag();
            for (Member member : outpost.members.values()) {
                CompoundTag m = new CompoundTag();
                m.putUUID("Id", member.id);
                m.putString("Name", member.name);
                m.putString("Role", member.role.name());
                m.putString("SupplyType", member.supplyType.name());
                m.putString("Dimension", member.dimension);
                m.putLong("Position", member.lastPosition.asLong());
                if (member.holdPosition != null) m.putLong("Hold", member.holdPosition.asLong());
                members.add(m);
            }
            tag.put("Members", members);
            list.add(tag);
        }
        root.put("Outposts", list);
        return root;
    }
}
