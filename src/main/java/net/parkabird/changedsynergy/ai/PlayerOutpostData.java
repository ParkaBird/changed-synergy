package net.parkabird.changedsynergy.ai;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.parkabird.changedsynergy.ChangedSynergyConfig;

/** Player-owned home records. No wild-community stock or chunk tickets are used. */
public final class PlayerOutpostData extends SavedData {
    public static final int RADIUS = 32;
    public static final int WORK_RADIUS = 64;
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

    public enum Work {
        GATHER, HUNT, FARM, COOK, HAUL, COMPOST;

        public static Optional<Work> parse(String value) {
            try {
                Work work = valueOf(value.toUpperCase(java.util.Locale.ROOT));
                return Optional.of(work == COMPOST ? FARM : work);
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        }
    }

    public enum CrewJob {
        COMBAT, MINE, FORAGE, HUNT;
        public static CrewJob read(String name) {
            try { return valueOf(name.toUpperCase(java.util.Locale.ROOT)); }
            catch (IllegalArgumentException ignored) { return COMBAT; }
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
        public Work work = Work.GATHER;
        public Work backupWork;
        public CrewJob crewJob = CrewJob.COMBAT;
        /** Transient display only; inventory and work assignments are the authoritative state. */
        public String workStatus = "idle";

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
        public boolean campfireMarker = true;
        public BlockPos storage;
        public final Set<BlockPos> beds = new LinkedHashSet<>();
        public final Set<BlockPos> restPoints = new LinkedHashSet<>();
        public BlockPos cooking;
        public BlockPos composter;
        public BlockPos farmStart;
        public BlockPos farmEnd;
        public BlockPos huntStart;
        public BlockPos huntEnd;
        public BlockPos gatherStart;
        public BlockPos gatherEnd;
        public BlockPos haulStart;
        public BlockPos haulEnd;
        public boolean meals = true;
        public long mealRequestedAt = -1L;
        public int rawReserve = 0;
        public int seedReserve = 16;
        public final Map<UUID, Member> members = new LinkedHashMap<>();

        private Outpost(UUID owner, String dimension, BlockPos marker) {
            this.owner = owner;
            this.dimension = dimension;
            this.marker = marker;
            this.cooking = marker;
        }

        public boolean active(ServerLevel level) {
            return dimension.equals(level.dimension().location().toString())
                    && level.hasChunkAt(marker)
                    && (campfireMarker ? level.getBlockState(marker).is(BlockTags.CAMPFIRES)
                            : level.getBlockState(marker).is(Blocks.BELL));
        }

        public boolean contains(BlockPos pos) {
            return marker.distSqr(pos) <= RADIUS * RADIUS;
        }

        public boolean farmContains(BlockPos pos) { return inArea(pos, farmStart, farmEnd); }
        public boolean huntContains(BlockPos pos) { return inArea(pos, huntStart, huntEnd); }
        public boolean workContains(BlockPos pos) { return marker.distSqr(pos) <= WORK_RADIUS * WORK_RADIUS; }
        public boolean hasGatherArea() { return gatherStart != null && gatherEnd != null; }
        public boolean gatherContains(BlockPos pos) {
            return gatherStart == null && gatherEnd == null ? contains(pos) : inArea(pos, gatherStart, gatherEnd);
        }
        public boolean hasHaulArea() { return haulStart != null && haulEnd != null; }
        public boolean haulContains(BlockPos pos) {
            return haulStart == null && haulEnd == null ? contains(pos) : inArea(pos, haulStart, haulEnd);
        }
        public net.minecraft.world.phys.AABB haulBounds() {
            return hasHaulArea() ? new net.minecraft.world.phys.AABB(haulStart)
                    .minmax(new net.minecraft.world.phys.AABB(haulEnd)).inflate(0, 3, 0)
                    : new net.minecraft.world.phys.AABB(marker).inflate(RADIUS);
        }

        private boolean inArea(BlockPos pos, BlockPos first, BlockPos second) {
            return workContains(pos) && first != null && second != null
                    && pos.getX() >= Math.min(first.getX(), second.getX())
                    && pos.getX() <= Math.max(first.getX(), second.getX())
                    && pos.getY() >= Math.min(first.getY(), second.getY()) - 1
                    && pos.getY() <= Math.max(first.getY(), second.getY()) + 3
                    && pos.getZ() >= Math.min(first.getZ(), second.getZ())
                    && pos.getZ() <= Math.max(first.getZ(), second.getZ());
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
            if (state.getValue(ChestBlock.TYPE) != net.minecraft.world.level.block.state.properties.ChestType.SINGLE
                    && !level.hasChunkAt(pos.relative(ChestBlock.getConnectedDirection(state)))) return null;
            return ChestBlock.getContainer(chest, state, level, pos, true);
        }
        return level.getBlockEntity(pos) instanceof Container container ? container : null;
    }

    /** Keeps later wild outpost placement away from a player's active home. */
    public static boolean isAreaClaimed(ServerLevel level, BlockPos pos, double radius) {
        String dimension = level.dimension().location().toString();
        double radiusSqr = radius * radius;
        return get(level.getServer()).outposts.values().stream()
                .anyMatch(outpost -> outpost.dimension.equals(dimension)
                        && (!level.hasChunkAt(outpost.marker) || outpost.active(level))
                        && distanceSqr(outpost.marker, pos) < radiusSqr);
    }

    public String claim(ServerLevel level, UUID owner, BlockPos marker) {
        if (!PlayerOutpostService.enabled(level)) return "disabled";
        String dimension = level.dimension().location().toString();
        Outpost previous = outposts.get(owner);
        boolean campfire = level.getBlockState(marker).is(BlockTags.CAMPFIRES);
        // Old bell-founded homes remain usable, but bells cannot establish or relocate a home.
        if (!campfire && !(previous != null && !previous.campfireMarker
                && previous.dimension.equals(dimension) && previous.marker.equals(marker)
                && level.getBlockState(marker).is(Blocks.BELL))) return "campfire";
        for (Outpost other : outposts.values()) {
            if (!other.owner.equals(owner) && other.dimension.equals(dimension)
                    && (!level.hasChunkAt(other.marker) || other.active(level))
                    && distanceSqr(other.marker, marker) < (RADIUS * 2) * (RADIUS * 2)) {
                return "overlap";
            }
        }
        if (CreatureCommunityData.isCacheAreaClaimed(level, marker, RADIUS + 16)) return "overlap";
        if (previous == null) {
            outposts.put(owner, new Outpost(owner, dimension, marker.immutable()));
        } else if (previous.dimension.equals(dimension) && previous.marker.equals(marker)) {
            if (previous.campfireMarker != campfire) {
                previous.campfireMarker = campfire;
                if (campfire && previous.cooking == null) previous.cooking = marker.immutable();
                setDirty();
            }
            return "claimed";
        } else {
            previous.dimension = dimension;
            previous.marker = marker.immutable();
            previous.campfireMarker = campfire;
            previous.storage = null;
            previous.beds.clear();
            previous.restPoints.clear();
            previous.cooking = campfire ? marker.immutable() : null;
            previous.composter = null;
            previous.farmStart = previous.farmEnd = null;
            previous.huntStart = previous.huntEnd = null;
            previous.gatherStart = previous.gatherEnd = null;
            previous.haulStart = previous.haulEnd = null;
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
        if (outpost.beds.add(pos.immutable())) setDirty();
    }

    public void addRestPoint(Outpost outpost, BlockPos pos) {
        if (outpost.restPoints.add(pos.immutable())) setDirty();
    }

    /** Migrates legacy pillows and bed feet only when their blocks are loaded. */
    public void refreshRestPlaces(ServerLevel level, Outpost outpost) {
        if (level == null || !outpost.dimension.equals(level.dimension().location().toString())) return;
        Set<BlockPos> beds = new LinkedHashSet<>(), points = new LinkedHashSet<>();
        Set<BlockPos> positions = new LinkedHashSet<>(outpost.beds);
        positions.addAll(outpost.restPoints);
        for (BlockPos pos : positions) {
            if (!outpost.contains(pos)) continue;
            if (!PlayerOutpostRestGoal.partsLoaded(level, pos)) {
                (outpost.beds.contains(pos) ? beds : points).add(pos);
                continue;
            }
            BlockPos normalized = PlayerOutpostRestGoal.restingPlace(level, pos);
            if (normalized == null || !outpost.contains(normalized)) continue;
            if (level.getBlockState(normalized).getBlock() instanceof net.minecraft.world.level.block.BedBlock)
                beds.add(normalized);
            else if (PlayerOutpostRestGoal.isRestPoint(level.getBlockState(normalized))) points.add(normalized);
        }
        if (!beds.equals(outpost.beds) || !points.equals(outpost.restPoints)) {
            outpost.beds.clear(); outpost.beds.addAll(beds);
            outpost.restPoints.clear(); outpost.restPoints.addAll(points);
            setDirty();
        }
    }

    public boolean setWork(Outpost outpost, UUID id, Work work, Work backupWork) {
        Member member = outpost.members.get(id);
        if (member == null || work == null) return false;
        member.role = Role.SUPPLY;
        member.holdPosition = null;
        member.work = work == Work.COMPOST ? Work.FARM : work;
        if (backupWork == Work.COMPOST) backupWork = Work.FARM;
        member.backupWork = backupWork == member.work ? null : backupWork;
        member.workStatus = "idle";
        setDirty();
        return true;
    }

    public void setFacility(Outpost outpost, String facility, BlockPos pos) {
        BlockPos value = pos == null ? null : pos.immutable();
        switch (facility) {
            case "campfire" -> outpost.cooking = value;
            case "composter" -> outpost.composter = value;
            case "farm_start" -> outpost.farmStart = value;
            case "farm_end" -> outpost.farmEnd = value;
            case "hunt_start" -> outpost.huntStart = value;
            case "hunt_end" -> outpost.huntEnd = value;
            case "gather_start" -> outpost.gatherStart = value;
            case "gather_end" -> outpost.gatherEnd = value;
            case "haul_start" -> outpost.haulStart = value;
            case "haul_end" -> outpost.haulEnd = value;
            default -> throw new IllegalArgumentException("Unknown camp facility: " + facility);
        }
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

    public boolean crewJob(Outpost outpost, UUID id, CrewJob job) {
        if (!role(outpost, id, Role.CREW, null)) return false;
        Member member = outpost.members.get(id);
        member.crewJob = job;
        member.workStatus = "idle";
        setDirty();
        return true;
    }

    public boolean remove(Outpost outpost, UUID id) {
        if (outpost.members.remove(id) == null) return false;
        membership.remove(id);
        setDirty();
        return true;
    }

    /** A new body is still the same worker, including its assignments and carried inventory. */
    public void replaceMember(UUID previousId, net.ltxprogrammer.changed.entity.ChangedEntity replacement) {
        Outpost outpost = membership.get(previousId);
        if (outpost == null || previousId.equals(replacement.getUUID())) return;
        Member previous = outpost.members.get(previousId);
        Member next = new Member(replacement.getUUID(), replacement.getDisplayName().getString(),
                previous.role, replacement.level().dimension().location().toString(), replacement.blockPosition());
        next.holdPosition = previous.holdPosition;
        next.supplyType = PlayerOutpostSupplyType.of(replacement);
        next.work = previous.work;
        next.backupWork = previous.backupWork;
        next.crewJob = previous.crewJob;
        outpost.members.remove(previousId);
        membership.remove(previousId);
        outpost.members.put(next.id, next);
        membership.put(next.id, outpost);
        replacement.getPersistentData().putUUID("ChangedSynergyPlayerOutpostOwner", outpost.owner);
        setDirty();
        PlayerOutpostService.ensureGoal(replacement);
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
                outpost.beds.clear();
                outpost.restPoints.clear();
                outpost.cooking = null;
                outpost.composter = null;
                setDirty();
            } else if (outpost.dimension.equals(dimension) && pos.equals(outpost.storage)) {
                outpost.storage = null;
                setDirty();
            }
            if (outpost.dimension.equals(dimension)) {
                BlockPos normalized = PlayerOutpostRestGoal.restingPlace(level, pos);
                boolean changed = outpost.beds.remove(pos) | outpost.restPoints.remove(pos);
                if (normalized != null) changed |= outpost.beds.remove(normalized) | outpost.restPoints.remove(normalized);
                if (changed) setDirty();
            }
            if (outpost.dimension.equals(dimension) && pos.equals(outpost.cooking)) {
                outpost.cooking = null;
                setDirty();
            }
            if (outpost.dimension.equals(dimension) && pos.equals(outpost.composter)) {
                outpost.composter = null;
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
            // The field did not exist when all player homes were founded around bells.
            outpost.campfireMarker = tag.getBoolean("CampfireMarker");
            outpost.cooking = tag.contains("Cooking", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("Cooking"))
                    : outpost.campfireMarker ? outpost.marker : null;
            if (tag.contains("Composter", Tag.TAG_LONG)) outpost.composter = BlockPos.of(tag.getLong("Composter"));
            if (tag.contains("FarmStart", Tag.TAG_LONG)) outpost.farmStart = BlockPos.of(tag.getLong("FarmStart"));
            if (tag.contains("FarmEnd", Tag.TAG_LONG)) outpost.farmEnd = BlockPos.of(tag.getLong("FarmEnd"));
            if (tag.contains("HuntStart", Tag.TAG_LONG)) outpost.huntStart = BlockPos.of(tag.getLong("HuntStart"));
            if (tag.contains("HuntEnd", Tag.TAG_LONG)) outpost.huntEnd = BlockPos.of(tag.getLong("HuntEnd"));
            if (tag.contains("GatherStart", Tag.TAG_LONG)) outpost.gatherStart = BlockPos.of(tag.getLong("GatherStart"));
            if (tag.contains("GatherEnd", Tag.TAG_LONG)) outpost.gatherEnd = BlockPos.of(tag.getLong("GatherEnd"));
            if (tag.contains("HaulStart", Tag.TAG_LONG)) outpost.haulStart = BlockPos.of(tag.getLong("HaulStart"));
            if (tag.contains("HaulEnd", Tag.TAG_LONG)) outpost.haulEnd = BlockPos.of(tag.getLong("HaulEnd"));
            outpost.meals = !tag.contains("Meals") || tag.getBoolean("Meals");
            outpost.rawReserve = tag.contains("RawReserve") ? Math.max(0, Math.min(64, tag.getInt("RawReserve"))) : 0;
            outpost.seedReserve = tag.contains("SeedReserve") ? Math.max(1, Math.min(64, tag.getInt("SeedReserve"))) : 16;
            if (tag.contains("Storage", Tag.TAG_LONG)) outpost.storage = BlockPos.of(tag.getLong("Storage"));
            for (long pos : tag.getLongArray("Beds")) outpost.beds.add(BlockPos.of(pos));
            for (long pos : tag.getLongArray("RestPoints")) outpost.restPoints.add(BlockPos.of(pos));
            if (tag.contains("Bed", Tag.TAG_LONG)) outpost.beds.add(BlockPos.of(tag.getLong("Bed")));
            for (Tag memberEntry : tag.getList("Members", Tag.TAG_COMPOUND)) {
                CompoundTag memberTag = (CompoundTag)memberEntry;
                if (!memberTag.hasUUID("Id")) continue;
                Role role = Role.parse(memberTag.getString("Role")).orElse(Role.RESIDENT);
                Member member = new Member(memberTag.getUUID("Id"), memberTag.getString("Name"), role,
                        memberTag.getString("Dimension"), BlockPos.of(memberTag.getLong("Position")));
                member.supplyType = PlayerOutpostSupplyType.parse(memberTag.getString("SupplyType"));
                member.work = Work.parse(memberTag.getString("Work")).orElse(Work.GATHER);
                member.crewJob = CrewJob.read(memberTag.getString("CrewJob"));
                member.backupWork = Work.parse(memberTag.getString("BackupWork")).orElse(null);
                if (member.backupWork == member.work) member.backupWork = null;
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
            tag.putBoolean("CampfireMarker", outpost.campfireMarker);
            if (outpost.cooking != null) tag.putLong("Cooking", outpost.cooking.asLong());
            if (outpost.composter != null) tag.putLong("Composter", outpost.composter.asLong());
            if (outpost.farmStart != null) tag.putLong("FarmStart", outpost.farmStart.asLong());
            if (outpost.farmEnd != null) tag.putLong("FarmEnd", outpost.farmEnd.asLong());
            if (outpost.huntStart != null) tag.putLong("HuntStart", outpost.huntStart.asLong());
            if (outpost.huntEnd != null) tag.putLong("HuntEnd", outpost.huntEnd.asLong());
            if (outpost.gatherStart != null) tag.putLong("GatherStart", outpost.gatherStart.asLong());
            if (outpost.gatherEnd != null) tag.putLong("GatherEnd", outpost.gatherEnd.asLong());
            if (outpost.haulStart != null) tag.putLong("HaulStart", outpost.haulStart.asLong());
            if (outpost.haulEnd != null) tag.putLong("HaulEnd", outpost.haulEnd.asLong());
            tag.putBoolean("Meals", outpost.meals);
            tag.putInt("RawReserve", outpost.rawReserve);
            tag.putInt("SeedReserve", outpost.seedReserve);
            if (outpost.storage != null) tag.putLong("Storage", outpost.storage.asLong());
            tag.putLongArray("Beds", outpost.beds.stream().mapToLong(BlockPos::asLong).toArray());
            tag.putLongArray("RestPoints", outpost.restPoints.stream().mapToLong(BlockPos::asLong).toArray());
            ListTag members = new ListTag();
            for (Member member : outpost.members.values()) {
                CompoundTag m = new CompoundTag();
                m.putUUID("Id", member.id);
                m.putString("Name", member.name);
                m.putString("Role", member.role.name());
                m.putString("SupplyType", member.supplyType.name());
                m.putString("Work", member.work.name());
                m.putString("CrewJob", member.crewJob.name());
                if (member.backupWork != null) m.putString("BackupWork", member.backupWork.name());
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
