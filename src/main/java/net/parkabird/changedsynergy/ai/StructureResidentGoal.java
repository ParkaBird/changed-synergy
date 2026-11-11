package net.parkabird.changedsynergy.ai;

import java.util.EnumSet;
import net.ltxprogrammer.changed.data.AccessorySlots;
import net.ltxprogrammer.changed.entity.AccessoryEntities;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.LivingEntityDataExtension;
import net.ltxprogrammer.changed.init.ChangedAccessorySlots;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;

/** Only explicitly tagged landmark residents are tied to their building. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class StructureResidentGoal extends Goal {
    public static final String DATA = "ChangedSynergyStructureResident";
    private final ChangedEntity mob;
    private final AABB bounds;
    private final Vec3 home;
    private final boolean character;
    private int ticks;
    private int stalledTicks;
    private Vec3 lastProgress;

    private StructureResidentGoal(ChangedEntity mob, CompoundTag data) {
        this.mob = mob;
        character = data.getBoolean("Character");
        var building = new AABB(data.getDouble("MinX"), data.getDouble("MinY"), data.getDouble("MinZ"),
                data.getDouble("MaxX"), data.getDouble("MaxY"), data.getDouble("MaxZ"));
        bounds = NaturalCampService.laboratoryResident(mob) ? NaturalCampService.restBounds(mob) : character ? building : NaturalCampService.activityBounds(mob);
        home = new Vec3(data.getDouble("HomeX"), data.getDouble("HomeY"), data.getDouble("HomeZ"));
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ChangedEntity mob)) return;
        initializeEyes(mob);
        install(mob);
    }

    private static void initializeEyes(ChangedEntity mob) {
        var saved=mob.getPersistentData();
        var resident=saved.getCompound(DATA);
        var info=mob.getBasicPlayerInfo();
        boolean legacyDefault=!resident.isEmpty() && !saved.getBoolean("ChangedSynergyStructureEyesInitialized")
                && info.getEyeStyle()==net.ltxprogrammer.changed.entity.EyeStyle.V2
                && info.getLeftIrisColor().toInt()==32767 && info.getRightIrisColor().toInt()==32767;
        if(saved.getBoolean("ChangedSynergyRandomStructureEyes") || legacyDefault) {
            var randomized=new net.ltxprogrammer.changed.entity.BasicPlayerInfo();
            ((net.parkabird.changedsynergy.mixin.ChangedEntityAppearanceAccessor)mob)
                    .changedSynergy$initializeAppearance(randomized,mob.getRandom());
            var updated=new net.ltxprogrammer.changed.entity.BasicPlayerInfo();updated.copyFrom(info);
            updated.setEyeStyle(randomized.getEyeStyle());
            updated.setLeftIrisColor(randomized.getLeftIrisColor());updated.setRightIrisColor(randomized.getRightIrisColor());
            mob.getEntityData().set(net.parkabird.changedsynergy.mixin.ChangedEntityAppearanceAccessor.changedSynergy$appearanceData(),updated);
            saved.remove("ChangedSynergyRandomStructureEyes");
            saved.putBoolean("ChangedSynergyStructureEyesInitialized",true);
        } else if(!resident.isEmpty()) saved.putBoolean("ChangedSynergyStructureEyesInitialized",true);
    }

    public static void install(ChangedEntity mob) {
        if(mob.level().isClientSide)return;
        var data = mob.getPersistentData().getCompound(DATA);
        if (data.isEmpty()) return;
        if(!data.contains("Dimension"))data.putString("Dimension",mob.level().dimension().location().toString());
        if (!data.getBoolean("Resolved")) {
            var pos = data.getList("LocalPos", Tag.TAG_DOUBLE);
            int[] size = data.getIntArray("Size");
            // Structure templates use List<Int>, while saved instances use absolute bounds.
            if (size.length == 0) {
                var list = data.getList("Size", Tag.TAG_INT);
                size = new int[]{list.getInt(0), list.getInt(1), list.getInt(2)};
            }
            if (pos.size() != 3 || size.length != 3 || size[0] <= 0 || size[1] <= 0 || size[2] <= 0) return;
            var rotation = Rotation.values()[Math.floorMod(Math.round((mob.getYRot() - data.getFloat("Yaw")) / 90F), 4)];
            var local = new Vec3(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2));
            var transformed = StructureTemplate.transform(local, Mirror.NONE, rotation, BlockPos.ZERO);
            var origin = BlockPos.containing(mob.position().subtract(transformed).add(.00001, .00001, .00001));
            var a = origin;
            var b = StructureTemplate.transform(new BlockPos(size[0]-1, size[1]-1, size[2]-1),
                    Mirror.NONE, rotation, BlockPos.ZERO).offset(origin);
            data.putDouble("MinX", Math.min(a.getX(), b.getX()));
            data.putDouble("MinY", Math.min(a.getY(), b.getY()));
            data.putDouble("MinZ", Math.min(a.getZ(), b.getZ()));
            data.putDouble("MaxX", Math.max(a.getX(), b.getX()) + 1);
            data.putDouble("MaxY", Math.max(a.getY(), b.getY()) + 1);
            data.putDouble("MaxZ", Math.max(a.getZ(), b.getZ()) + 1);
            data.putDouble("HomeX", mob.getX()); data.putDouble("HomeY", mob.getY()); data.putDouble("HomeZ", mob.getZ());
            data.putLong("PlacementOrigin", origin.asLong()); data.putInt("PlacementRotation", rotation.ordinal());
            data.putBoolean("Resolved", true);
            data.remove("LocalPos"); data.remove("Size"); data.remove("Yaw");
        }
        mob.setPersistenceRequired();
        if(data.getString("Template").equals("desert_refuge")&&!data.getBoolean("Character")&&!data.contains("DialogueRole")){
            var rotation=Rotation.values()[Math.floorMod(data.getInt("PlacementRotation"),4)];var origin=Vec3.atLowerCornerOf(BlockPos.of(data.getLong("PlacementOrigin")));
            var home=new Vec3(data.getDouble("HomeX"),data.getDouble("HomeY"),data.getDouble("HomeZ"));
            String[] roles={"dorm","study","garden","lab","corridor"};double[][] positions={{31.5,7,18.5},{1.5,7,18.5},{6.5,14,15.5},{25.5,14,6},{25.5,21,18.5}};
            for(int i=0;i<roles.length;i++)if(home.distanceToSqr(StructureTemplate.transform(new Vec3(positions[i][0],positions[i][1],positions[i][2]),Mirror.NONE,rotation,BlockPos.ZERO).add(origin))<.01){data.putString("DialogueRole",roles[i]);break;}
        }
        if(data.getString("Template").equals("green_lab") && data.getBoolean("Character") && !data.contains("NeckOutfit"))
            data.putString("NeckOutfit","changed:red_neck_tie");
        if(data.contains("OutfitChance",Tag.TAG_FLOAT)&&!data.getBoolean("OutfitRolled")) {
            data.putBoolean("OutfitRolled",true);
            if(mob.getRandom().nextFloat()>=data.getFloat("OutfitChance"))data.putString("Outfit","");
        }
        for(String field:java.util.List.of("Outfit","NeckOutfit"))if (!data.getBoolean(field+"Applied") && !data.getString(field).isEmpty()) {
            var id = ResourceLocation.tryParse(data.getString(field));
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                AccessoryEntities.INSTANCE.forceReloadAccessories(mob);
                AccessorySlots.getForEntity(mob).ifPresent(slots -> {
                    if (AccessorySlots.isWearing(mob, s->s.is(BuiltInRegistries.ITEM.get(id)))
                            || field.equals("NeckOutfit") && slots.getItem(ChangedAccessorySlots.NECK.get()).filter(s->!s.isEmpty()).isPresent()
                            || slots.quickMoveStack(new ItemStack(BuiltInRegistries.ITEM.get(id))))data.putBoolean(field+"Applied",true);
                });
            }
        }
        if (mob.goalSelector.getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof StructureResidentGoal))
            mob.goalSelector.addGoal(-7, new StructureResidentGoal(mob, data));
        NaturalCampService.install(mob);
    }

    private boolean available() {
        return mob.isAlive() && NaturalCampService.inHomeDimension(mob) && mob.getUnderlyingPlayer() == null && !mob.isNoAi() && !mob.isPassenger()
                && (character || !NaturalCampService.followingOwner(mob) && !PlayerOutpostService.hasAssignment(mob)
                    && (CreaturePersonality.socialPartner(mob)==null||!mayFollow(mob,CreaturePersonality.socialPartner(mob))))
                && !mob.isLeashed() && !CreatureGrabService.isGrabberBusy(mob)
                && (!(mob instanceof LivingEntityDataExtension extension) || extension.getGrabbedBy() == null);
    }
    public static boolean mayFollow(ChangedEntity mob, net.minecraft.world.entity.LivingEntity leader) {
        var data=mob.getPersistentData().getCompound(DATA);
        if(!data.getBoolean("Resolved"))return true;
        if(!data.getBoolean("Character")&&(!data.getBoolean("Camp")||CreatureCommunityData.isPlayerCompanion(mob)||PlayerOutpostService.hasAssignment(mob)))return true;
        var body=leader instanceof net.minecraft.server.level.ServerPlayer player?net.parkabird.changedsynergy.compat.TrueTransfurCompat.interactionBody(player):leader;
        return body!=null&&body.level()==mob.level()&&(data.getBoolean("Character")?NaturalCampService.bounds(mob):NaturalCampService.activityBounds(mob)).contains(body.position());
    }
    public static boolean isNonWorkingResident(ChangedEntity mob) {
        var data=mob.getPersistentData().getCompound(DATA);
        return data.getBoolean("Character") && !data.getBoolean("Camp");
    }

    @Override public boolean canUse() {
        if (!available()) return false;
        if(mob.getTarget()!=null && !bounds.contains(mob.getTarget().position())) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
        return !bounds.contains(mob.position());
    }

    @Override public boolean canContinueToUse() { return available() && !bounds.deflate(.75, 0, .75).contains(mob.position()); }
    @Override public void start() {
        ticks = 0;
        stalledTicks = 0;
        lastProgress = mob.position();
        mob.setTarget(null);
        CreaturePeerInteractionService.cancel(mob);
        mob.getNavigation().stop();
    }
    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override public void tick() {
        if (ticks++ % 40 == 0) CreatureTaskNavigation.moveTo(mob, BlockPos.containing(home), 1, .35);
        if (ticks % 20 == 0) {
            if (mob.position().distanceToSqr(lastProgress) >= .25) {
                stalledTicks = 0;
                lastProgress = mob.position();
            } else stalledTicks += 20;
        }
        // No repeated distant path searches: recover safely only after walking back has failed.
        if (stalledTicks >= 100 && ticks % 100 == 0 && mob.level().hasChunkAt(BlockPos.containing(home))) {
            for (int dy : new int[]{0, 1, -1, 2}) for (int dx : new int[]{0, -1, 1, -2, 2}) for (int dz : new int[]{0, -1, 1, -2, 2}) {
                var candidate = home.add(dx, dy, dz);
                var floor = BlockPos.containing(candidate.add(0, -.01, 0));
                if (!bounds.contains(candidate) || !mob.level().hasChunkAt(floor)
                        || !supported(candidate, floor)
                        || !mob.level().getFluidState(floor).isEmpty()
                        || !mob.level().noCollision(mob, mob.getBoundingBox().move(candidate.subtract(mob.position())))
                        || PlayerOutpostGoal.onCampfire(mob.level(), mob.getBoundingBox().move(candidate.subtract(mob.position())))) continue;
                mob.teleportTo(candidate.x, candidate.y, candidate.z);
                mob.setDeltaMovement(Vec3.ZERO);
                mob.fallDistance = 0;
                mob.getNavigation().stop();
                return;
            }
        }
    }
    private boolean supported(Vec3 point, BlockPos floor) {
        var state=mob.level().getBlockState(floor);
        if(state.is(net.minecraft.tags.BlockTags.CAMPFIRES) || state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                || state.is(net.minecraft.world.level.block.Blocks.CACTUS))return false;
        return state.getCollisionShape(mob.level(),floor).toAabbs().stream().map(b->b.move(floor)).anyMatch(b->
                b.maxY>=point.y-.05 && b.maxY<=point.y+.01 && b.minX<=point.x && b.maxX>=point.x && b.minZ<=point.z && b.maxZ>=point.z);
    }
    @Override public void stop() { mob.getNavigation().stop(); CreatureTaskNavigation.clearFailure(mob); }
}
