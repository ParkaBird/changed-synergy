package net.parkabird.changedsynergy.ai;

import java.util.*;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/** Authored container positions survive loot unpacking, including in older saved camps. */
final class StructureStorageOwnership {
    private static final String MARKER = "ChangedSynergyStructureStorage";
    private record Store(BlockPos pos, String block, boolean privateStore, boolean publicStore) {}
    private record Resident(Vec3 pos, String type) {}
    private record Layout(BlockPos size, List<Store> stores, List<Resident> residents) {}
    private static final Map<StructureTemplate, Layout> LAYOUTS = new WeakHashMap<>();
    private StructureStorageOwnership() {}

    static boolean owns(ChangedEntity mob, BlockPos pos) {
        if (!(mob.level() instanceof ServerLevel level) || !level.hasChunkAt(pos)) return false;
        var be = level.getBlockEntity(pos);
        if (be == null) return false;
        var data = mob.getPersistentData().getCompound(StructureResidentGoal.DATA);
        if (be.getPersistentData().getBoolean(MARKER) && !Set.of("desert_refuge","oasis","muddy_campsite","hyena_camp_tropical","hyena_camp_open","hyena_camp_snowy").contains(data.getString("Template"))) return true;
        var id = ResourceLocation.tryParse(data.getString("Template"));
        if (id == null || !data.getBoolean("Resolved")) return false;
        if (id.getNamespace().equals("minecraft")) id = ResourceLocation.fromNamespaceAndPath("changed_synergy", id.getPath());
        var template = level.getStructureManager().get(id).orElse(null);
        if (template == null) return false;
        var layout = LAYOUTS.computeIfAbsent(template, StructureStorageOwnership::read);
        if (!data.contains("PlacementOrigin") && !recoverPlacement(mob, data, layout)) return false;
        var origin = BlockPos.of(data.getLong("PlacementOrigin"));
        var rotation = Rotation.values()[Math.floorMod(data.getInt("PlacementRotation"), 4)];
        var oldExtra=switch(data.getString("Template")){case "oasis"->new BlockPos(10,3,17);case "muddy_campsite"->new BlockPos(6,5,8);default->null;};
        if(oldExtra!=null&&StructureTemplate.transform(oldExtra,Mirror.NONE,rotation,BlockPos.ZERO).offset(origin).equals(pos)){
            // Retire the old added reserve without deleting a player's stored items or the chest itself.
            be.getPersistentData().remove(NaturalCampService.PUBLIC_STORAGE);be.getPersistentData().remove(MARKER);be.setChanged();return false;
        }
        for (var store : layout.stores()) {
            var placed = StructureTemplate.transform(store.pos(), Mirror.NONE, rotation, BlockPos.ZERO).offset(origin);
            if (!placed.equals(pos) || !BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString().equals(store.block())) continue;
            if (store.privateStore() || store.publicStore()) {
                var flags=be.getPersistentData();
                if(store.publicStore()&&be instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity loot)loot.unpackLootTable(null);
                if(flags.getBoolean("ChangedSynergyPrivate")!=store.privateStore()||flags.getBoolean(NaturalCampService.PUBLIC_STORAGE)!=store.publicStore()) {
                    flags.putBoolean("ChangedSynergyPrivate",store.privateStore());flags.putBoolean(NaturalCampService.PUBLIC_STORAGE,store.publicStore());
                    if(be instanceof net.minecraft.world.level.block.entity.BaseContainerBlockEntity chest) {
                        if(store.publicStore())chest.setCustomName(net.minecraft.network.chat.Component.translatable(NaturalCampService.smallCamp(mob)?"container.changed_synergy.camp_supplies":"container.changed_synergy.community_supplies"));
                        else if(chest.getCustomName()!=null&&chest.getCustomName().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text&&text.getKey().equals("container.changed_synergy.community_supplies"))chest.setCustomName(null);
                    }
                }
            }
            be.getPersistentData().putBoolean(MARKER, true);
            be.setChanged();
            return true;
        }
        return false;
    }

    static List<BlockPos> publicPositions(ChangedEntity mob){
        if(!(mob.level() instanceof ServerLevel level))return List.of();
        var data=mob.getPersistentData().getCompound(StructureResidentGoal.DATA);
        if(!data.getBoolean("Resolved"))return List.of();
        var id=ResourceLocation.tryParse(data.getString("Template"));if(id==null)return List.of();
        if(id.getNamespace().equals("minecraft"))id=ResourceLocation.fromNamespaceAndPath("changed_synergy",id.getPath());
        var template=level.getStructureManager().get(id).orElse(null);if(template==null)return List.of();
        var layout=LAYOUTS.computeIfAbsent(template,StructureStorageOwnership::read);
        if(!data.contains("PlacementOrigin")&&!recoverPlacement(mob,data,layout))return List.of();
        var origin=BlockPos.of(data.getLong("PlacementOrigin"));var rotation=Rotation.values()[Math.floorMod(data.getInt("PlacementRotation"),4)];
        return layout.stores().stream().filter(Store::publicStore).map(s->StructureTemplate.transform(s.pos(),Mirror.NONE,rotation,BlockPos.ZERO).offset(origin)).toList();
    }

    private static Layout read(StructureTemplate template) {
        var tag = template.save(new CompoundTag());
        var size = tag.getList("size", Tag.TAG_INT);
        var palette = tag.getList("palette", Tag.TAG_COMPOUND);
        if (palette.isEmpty()) palette = (ListTag)tag.getList("palettes", Tag.TAG_LIST).get(0);
        var stores = new ArrayList<Store>();
        for (var entry : tag.getList("blocks", Tag.TAG_COMPOUND)) {
            var block = (CompoundTag)entry;
            if (!block.contains("nbt")) continue;
            var local = block.getList("pos", Tag.TAG_INT);
            var flags=block.getCompound("nbt").getCompound("ForgeData");
            stores.add(new Store(new BlockPos(local.getInt(0), local.getInt(1), local.getInt(2)), palette.getCompound(block.getInt("state")).getString("Name"), flags.getBoolean("ChangedSynergyPrivate"), flags.getBoolean(NaturalCampService.PUBLIC_STORAGE)));
        }
        var residents = new ArrayList<Resident>();
        for (var entry : tag.getList("entities", Tag.TAG_COMPOUND)) {
            var entity = (CompoundTag)entry;
            if (!entity.getCompound("nbt").getCompound("ForgeData").contains(StructureResidentGoal.DATA)) continue;
            var local = entity.getList("pos", Tag.TAG_DOUBLE);
            residents.add(new Resident(new Vec3(local.getDouble(0), local.getDouble(1), local.getDouble(2)), entity.getCompound("nbt").getString("id")));
        }
        return new Layout(new BlockPos(size.getInt(0), size.getInt(1), size.getInt(2)), List.copyOf(stores), List.copyOf(residents));
    }

    private static boolean recoverPlacement(ChangedEntity mob, CompoundTag data, Layout layout) {
        var min = BlockPos.containing(data.getDouble("MinX"), data.getDouble("MinY"), data.getDouble("MinZ"));
        var home = new Vec3(data.getDouble("HomeX"), data.getDouble("HomeY"), data.getDouble("HomeZ"));
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
        BlockPos found = null; Rotation facing = null;
        for (var rotation : Rotation.values()) {
            var corner = StructureTemplate.transform(layout.size().offset(-1,-1,-1), Mirror.NONE, rotation, BlockPos.ZERO);
            if (Math.abs(corner.getX()) + 1 != (int)(data.getDouble("MaxX") - data.getDouble("MinX"))
                    || Math.abs(corner.getZ()) + 1 != (int)(data.getDouble("MaxZ") - data.getDouble("MinZ"))) continue;
            var origin = min.offset(-Math.min(0, corner.getX()), 0, -Math.min(0, corner.getZ()));
            for (var resident : layout.residents()) {
                if (!resident.type().equals(type)) continue;
                var placed = StructureTemplate.transform(resident.pos(), Mirror.NONE, rotation, BlockPos.ZERO).add(Vec3.atLowerCornerOf(origin));
                if (placed.distanceToSqr(home) > .001) continue;
                // Ambiguous old metadata must not claim an unrelated player's container.
                if (found != null && (!found.equals(origin) || facing != rotation)) return false;
                found = origin; facing = rotation;
            }
        }
        if (found == null) return false;
        data.putLong("PlacementOrigin", found.asLong()); data.putInt("PlacementRotation", facing.ordinal());
        return true;
    }
}
