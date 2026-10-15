package net.parkabird.changedsynergy.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.ltxprogrammer.changed.ability.GrabEntityAbility;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.Emote;
import net.ltxprogrammer.changed.entity.SeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.world.inventory.BondedInventoryService;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Member;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Outpost;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Role;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;

/** A brief campfire gathering, with one real meal per resident per Minecraft day. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class PlayerOutpostMealGoal extends Goal {
    private static final String GATHER_DAY = "ChangedSynergyOutpostGatherDay";
    private static final String EAT_DAY = "ChangedSynergyOutpostMealDay";
    private static final String REQUEST = "ChangedSynergyOutpostMealRequest";
    private static final String FOOD = "ChangedSynergyCampMealFood";
    private static final String SAVED_HAND = "ChangedSynergyCampMealOffhand";
    private static final String USING = "ChangedSynergyCampMealUsing";
    private static final String FINISHED = "ChangedSynergyCampMealFinished";
    private static final String SEAT = "ChangedSynergyOutpostMealSeat";
    private static final String PLACE = "ChangedSynergyOutpostMealPlace";
    private static final String UNAVAILABLE_UNTIL = "ChangedSynergyOutpostMealUnavailableUntil";
    private static final int MAX_DURATION = 600;
    private static final double WALK_SPEED = 0.35D;
    private static final int PATH_RETRY_TICKS = 10;
    private static final Set<ChangedEntity> ACTIVE = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<ServerLevel, Map<BlockPos, Claim>> CLAIMS = new WeakHashMap<>();
    private static final TagKey<Item> RAW_MEAT = itemTag("raw_meats");
    private static final TagKey<Item> COOKED_MEAT = itemTag("cooked_meats");
    private record Claim(UUID creature, long until, double width) {}

    private final ChangedEntity mob;
    private Outpost outpost;
    private BlockPos fire;
    private BlockPos place;
    private int remaining;
    private int repath;
    private int seatedTicks;
    private boolean pillow;
    private boolean seated;
    private boolean ate;
    private double bodyWidth;
    private BlockPos foodStand;
    private BlockPos foodStore;
    private boolean fetchingFood;
    private int restAfterMeal;
    private long nextScheduledAttempt = -1L;

    public PlayerOutpostMealGoal(ChangedEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public static void request(ServerPlayer owner, Outpost home) {
        home.mealRequestedAt = owner.serverLevel().getGameTime();
        for (Member member : home.members.values()) {
            if (eligible(member) && owner.serverLevel().getEntity(member.id) instanceof ChangedEntity mob) {
                SocialAudienceGoal.end(mob, owner);
                mob.getPersistentData().remove(UNAVAILABLE_UNTIL);
            }
        }
    }

    /** Lets ordinary work yield to a scheduled or owner-requested gathering. */
    public static boolean wantsMeal(ChangedEntity mob) {
        if (!(mob.level() instanceof ServerLevel level)
                || !PlayerOutpostService.operational(mob)
                || !ChangedSynergyConfig.COMMON.playerOutpostMeals.get()) return false;
        if (!ACTIVE.contains(mob)
                && mob.getPersistentData().getLong(UNAVAILABLE_UNTIL) > level.getGameTime()) return false;
        Outpost home = PlayerOutpostData.get(level.getServer()).byMember(mob.getUUID()).orElse(null);
        return home != null && enabled(level, home) && home.contains(mob.blockPosition())
                && campfire(level, home) != null && eligible(home.members.get(mob.getUUID()))
                && idleAvailable(mob, ACTIVE.contains(mob))
                && (ACTIVE.contains(mob) || requested(level, home, mob)
                        || isMealTime(level) && differentDay(mob, GATHER_DAY, day(level)));
    }

    @Override public boolean canUse() {
        if (!(mob.level() instanceof ServerLevel level) || !wantsMeal(mob) || !available(false)
                || !PlayerOutpostGoal.canNavigate(mob)) return false;
        outpost = PlayerOutpostData.get(level.getServer()).byMember(mob.getUUID()).orElse(null);
        if (outpost == null || !outpost.contains(mob.blockPosition())) return false;
        if (!requested(level, outpost, mob)) {
            long now = level.getGameTime();
            if (nextScheduledAttempt < 0L) nextScheduledAttempt = now + Math.floorMod(mob.getId(), 40);
            if (now < nextScheduledAttempt) return false;
            // Goal selection can skip individual ticks, so use a deadline rather than an exact tick remainder.
            nextScheduledAttempt = now + 40L;
        }
        fire = campfire(level, outpost);
        if (fire == null) return false;
        bodyWidth = Math.max(mob.getBbWidth(), ChangedAddonCompat.alphaRenderScale(mob) * 0.65D);
        place = selectPlace(level, true);
        pillow = place != null;
        if (place == null) place = selectPlace(level, false);
        if (place == null) mob.getPersistentData().putLong(UNAVAILABLE_UNTIL, level.getGameTime() + 200L);
        return place != null;
    }

    @Override public void start() {
        PlayerOutpostRestGoal.cleanup(mob);
        ServerLevel level = (ServerLevel)mob.level();
        remaining = MAX_DURATION;
        repath = 0;
        seatedTicks = 0;
        seated = false;
        ate = !differentDay(mob, EAT_DAY, day(level));
        restAfterMeal = 140 + mob.getRandom().nextInt(101);
        fetchingFood = !ate && bestFood(personalInventory(mob), false) < 0
                && bestFood(PlayerOutpostData.container(level, outpost.storage), true) >= 0;
        foodStand = fetchingFood ? PlayerOutpostWorkGoal.approach(mob, outpost, level, outpost.storage) : null;
        fetchingFood &= foodStand != null;
        foodStore = outpost.storage;
        if (requested(level, outpost, mob))
            mob.getPersistentData().putLong(REQUEST, outpost.mealRequestedAt);
        else mob.getPersistentData().putLong(GATHER_DAY, day(level));
        claims(level).put(place, new Claim(mob.getUUID(), level.getGameTime() + MAX_DURATION + 20, bodyWidth));
        mob.getPersistentData().putLong(PLACE, place.asLong());
        ACTIVE.add(mob);
        status("meal_walk");
    }

    @Override public boolean canContinueToUse() {
        if (!(mob.level() instanceof ServerLevel level) || remaining <= 0 || !ACTIVE.contains(mob)
                || outpost == null || !enabled(level, outpost)
                || !eligible(outpost.members.get(mob.getUUID())) || !available(seated)
                || fire == null || !fire.equals(campfire(level, outpost)) || place == null) return false;
        if (seated) return ownsSeat(mob) && (!pillow
                || level.hasChunkAt(place) && PlayerOutpostRestGoal.isRestPoint(level.getBlockState(place)));
        return (mob.tickCount % 20 != 0 || safePlace(level, place, pillow)) && !claimed(level, place);
    }

    @Override public void tick() {
        remaining--;
        ServerLevel level = (ServerLevel)mob.level();
        if (!available(seated) || !enabled(level, outpost) || !eligible(outpost.members.get(mob.getUUID()))) {
            remaining = 0;
            cleanup(mob);
            return;
        }
        if (fetchingFood) {
            if (outpost.storage == null || !outpost.storage.equals(foodStore) || !level.hasChunkAt(outpost.storage)) { fetchingFood = false; return; }
            if (mob.distanceToSqr(Vec3.atBottomCenterOf(foodStand)) > 2.0D) {
                if (!walkTo(foodStand, foodStand.getY())) fetchingFood = false;
                return;
            }
            Container storage = PlayerOutpostData.container(level, outpost.storage);
            int slot = bestFood(storage, true);
            if (slot >= 0) {
                prepareFood(storage.removeItem(slot, 1));
                storage.setChanged();
                PlayerOutpostStorageAccess.touch(mob, outpost.storage);
            }
            fetchingFood = false;
            repath = 0;
            return;
        }
        if (seated) {
            mob.getNavigation().stop();
            seatedTicks++;
            if (seatedTicks >= 40 && !ate) eat(level);
            if (ate && --restAfterMeal <= 0) remaining = 0;
            return;
        }
        double y = place.getY() + (pillow ? 0.32D : 0.0D);
        if (mob.distanceToSqr(place.getX() + 0.5D, y, place.getZ() + 0.5D) > 1.6D) {
            if (!walkTo(place, y)) remaining = 0;
            return;
        }
        mob.getNavigation().stop();
        if (!safePlace(level, place, pillow) || claimed(level, place)) { remaining = 0; return; }
        if (pillow) {
            if (!PlayerOutpostRestGoal.free(mob, level, place)
                    || !PlayerOutpostRestGoal.sit(mob, level, place)) { remaining = 0; return; }
        } else {
            if (!mob.onGround()) return;
            BlockPos floor = place.below();
            SeatEntity seat = SeatEntity.createFor(level, level.getBlockState(floor), floor,
                    false, false, true);
            if (seat == null) { remaining = 0; return; }
            // Match the existing shared-rest adjustment for Addon's oversized Alpha bodies.
            seat.setPos(place.getX() + 0.5D,
                    place.getY() - 0.4D * Math.max(0.0D, ChangedAddonCompat.alphaRenderScale(mob) - 1.0D),
                    place.getZ() + 0.5D);
            if (!mob.startRiding(seat, true)) { seat.discard(); remaining = 0; return; }
        }
        if (!(mob.getVehicle() instanceof SeatEntity seat)) { remaining = 0; return; }
        mob.getPersistentData().putUUID(SEAT, seat.getUUID());
        seated = true;
        status("meal_sit");
        mob.getLookControl().setLookAt(fire.getX() + 0.5D, fire.getY() + 0.75D, fire.getZ() + 0.5D);
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(outpost.owner);
        if (owner != null && owner.level() == level && owner.distanceToSqr(mob) <= 144.0D
                && mob.getRandom().nextInt(3) == 0)
            NpcDialogue.context(mob, owner, "player_outpost.meal." + tone());
    }

    @Override public void stop() {
        mob.getNavigation().stop();
        cleanup(mob);
        remaining = 0;
        seated = false;
        place = null;
        fire = null;
        outpost = null;
    }

    @Override public boolean requiresUpdateEveryTick() { return true; }

    private boolean walkTo(BlockPos destination, double y) {
        if (--repath > 0) return true;
        repath = PATH_RETRY_TICKS;
        return PlayerOutpostGoal.walkTo(mob, destination, y, WALK_SPEED);
    }

    private boolean available(boolean allowSeat) {
        return idleAvailable(mob, allowSeat);
    }

    private static boolean idleAvailable(ChangedEntity mob, boolean allowSeat) {
        return PlayerOutpostService.operational(mob) && mob.isAlive() && !mob.isNoAi() && !mob.isSleeping() && !mob.isLeashed()
                && mob.getTarget() == null && !mob.isVehicle()
                && (!mob.isUsingItem() || mob.getPersistentData().getBoolean(USING))
                && (!mob.isPassenger() || PlayerOutpostRestGoal.isResting(mob) || allowSeat && ownsSeat(mob))
                && !CreatureSettlementService.hasCargo(mob) && !PlayerOutpostGoal.hasCargo(mob)
                && !PlayerOutpostWorkGoal.hasCargo(mob) && !TakeoverService.carrying(mob)
                && !ChangedAddonCompat.isGrabberBusy(mob) && GrabEntityAbility.getGrabber(mob) == null
                && !SocialAudienceGoal.isActive(mob) && HypnosisQteService.getActiveVictim(mob) == null
                && !holding(mob)
                && !mob.getPersistentData().hasUUID("ChangedSynergyRestOwner")
                && !PlayerOutpostGoal.onCampfire(mob);
    }

    private static boolean holding(ChangedEntity mob) {
        var ability = BondedSuitService.ability(mob);
        return ability != null && ability.grabbedEntity != null;
    }

    private static boolean enabled(ServerLevel level, Outpost home) {
        return PlayerOutpostService.enabled(level)
                && ChangedSynergyConfig.COMMON.playerOutpostMeals.get() && home.meals && home.active(level)
                && (ChangedSynergyConfig.COMMON.playerOutpostWorkWhileOwnerOffline.get()
                        || level.getServer().getPlayerList().getPlayer(home.owner) != null);
    }

    private static boolean eligible(Member member) {
        return member != null && (member.role == Role.RESIDENT || member.role == Role.GUARD
                || member.role == Role.SUPPLY);
    }

    private static boolean isMealTime(ServerLevel level) {
        long time = Math.floorMod(level.getDayTime(), 24000L);
        return time >= 11000L && time < 12000L;
    }

    private static long day(ServerLevel level) { return Math.floorDiv(level.getDayTime(), 24000L); }

    private static boolean differentDay(ChangedEntity mob, String key, long day) {
        return !mob.getPersistentData().contains(key) || mob.getPersistentData().getLong(key) != day;
    }

    private static boolean requested(ServerLevel level, Outpost home, ChangedEntity mob) {
        long elapsed = level.getGameTime() - home.mealRequestedAt;
        return home.mealRequestedAt >= 0 && elapsed >= 0 && elapsed < MAX_DURATION
                && (!mob.getPersistentData().contains(REQUEST)
                        || mob.getPersistentData().getLong(REQUEST) != home.mealRequestedAt);
    }

    private static BlockPos campfire(ServerLevel level, Outpost home) {
        BlockPos pos = home.cooking != null ? home.cooking : home.campfireMarker ? home.marker : null;
        if (pos == null || !home.contains(pos) || !level.hasChunkAt(pos)) return null;
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)
                && !state.getValue(CampfireBlock.WATERLOGGED) ? pos : null;
    }

    private BlockPos selectPlace(ServerLevel level, boolean onPillow) {
        List<BlockPos> candidates = new ArrayList<>();
        if (onPillow) {
            PlayerOutpostData.get(level.getServer()).refreshRestPlaces(level, outpost);
            for (BlockPos point : outpost.restPoints)
                if (!claimed(level, point) && safePlace(level, point, true)) candidates.add(point);
        } else for (BlockPos cursor : BlockPos.betweenClosed(fire.offset(-6, -2, -6), fire.offset(6, 2, 6))) {
            if (!claimed(level, cursor) && safePlace(level, cursor, false)) candidates.add(cursor.immutable());
        }
        double angle = Math.toRadians(Math.floorMod(mob.getUUID().hashCode(), 360));
        double radius = Math.min(5.5D, 3.0D + bodyWidth / 2.0D);
        Vec3 preferred = Vec3.atCenterOf(fire).add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
        candidates.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(preferred)));
        // ponytail: at most eight local path probes per attempt; no world-wide work-site search.
        for (int index = 0; index < Math.min(8, candidates.size()); index++) {
            BlockPos pos = candidates.get(index);
            var path = mob.getNavigation().createPath(pos, 0);
            if (path != null && path.canReach()) return pos;
        }
        return null;
    }

    private boolean safePlace(ServerLevel level, BlockPos pos, boolean onPillow) {
        if (!outpost.contains(pos) || !level.hasChunkAt(pos) || !level.hasChunkAt(pos.above(4))) return false;
        double width = bodyWidth;
        double dx = pos.getX() - fire.getX(), dz = pos.getZ() - fire.getZ();
        double distance = dx * dx + dz * dz;
        if (distance < Math.pow(1.5D + width / 2.0D, 2) || distance > 36.0D) return false;
        BlockState state = level.getBlockState(pos);
        if (onPillow) {
            if (!PlayerOutpostRestGoal.isRestPoint(state) || !PlayerOutpostRestGoal.free(mob, level, pos)) return false;
        } else if (!state.getCollisionShape(level, pos).isEmpty()) return false;
        BlockPos below = pos.below();
        BlockState floor = level.getBlockState(below);
        if (!floor.isFaceSturdy(level, below, Direction.UP) || hazardous(floor)
                || !state.getFluidState().isEmpty() || hazardous(state)) return false;
        // Changed's pillows are 5/16 of a block high; check space above their actual cushion.
        double y = pos.getY() + (onPillow ? 0.32D : 0.0D);
        AABB body = new AABB(pos.getX() + 0.5D - width / 2.0D, y, pos.getZ() + 0.5D - width / 2.0D,
                pos.getX() + 0.5D + width / 2.0D, y + mob.getBbHeight(), pos.getZ() + 0.5D + width / 2.0D);
        for (BlockPos cursor : BlockPos.betweenClosed(BlockPos.containing(body.minX, body.minY, body.minZ),
                BlockPos.containing(body.maxX, body.maxY, body.maxZ))) {
            if (!level.hasChunkAt(cursor)) return false;
            BlockState nearby = level.getBlockState(cursor);
            if (hazardous(nearby) || !nearby.getFluidState().isEmpty()) return false;
            if (onPillow && (cursor.equals(pos) || cursor.equals(pos.above()))) continue;
            for (AABB shape : nearby.getCollisionShape(level, cursor).toAabbs())
                if (shape.move(cursor).intersects(body)) return false;
        }
        return level.getEntityCollisions(mob, body).isEmpty();
    }

    private static boolean hazardous(BlockState state) {
        return state.is(BlockTags.CAMPFIRES) || state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW);
    }

    private void eat(ServerLevel level) {
        var data = mob.getPersistentData();
        if (data.getBoolean(FINISHED)) {
            restoreMealHand(mob);
            ate = true;
            status("meal_rest");
            return;
        }
        if (data.getBoolean(USING)) {
            if (!mob.isUsingItem()) { restoreMealHand(mob); ate = true; status("meal_no_food"); }
            return;
        }
        if (!differentDay(mob, EAT_DAY, day(level))) { ate = true; return; }
        if (!data.contains(FOOD)) {
            Container own = personalInventory(mob);
            int slot = bestFood(own, false);
            if (slot >= 0) { prepareFood(own.removeItem(slot, 1)); own.setChanged(); }
        }
        if (!data.contains(FOOD) || !ItemStack.isSameItemSameTags(mob.getOffhandItem(), ItemStack.of(data.getCompound(FOOD)))) {
            restoreMealHand(mob);
            ate = true;
            status("meal_no_food");
            return;
        }
        data.putBoolean(USING, true);
        mob.startUsingItem(InteractionHand.OFF_HAND);
        status("eating");
    }

    private void prepareFood(ItemStack food) {
        if (food.isEmpty()) return;
        var data = mob.getPersistentData();
        data.put(SAVED_HAND, mob.getOffhandItem().save(new CompoundTag()));
        data.put(FOOD, food.copyWithCount(1).save(new CompoundTag()));
        mob.setItemInHand(InteractionHand.OFF_HAND, food);
    }

    @SubscribeEvent
    public static void onMealFinished(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ChangedEntity mob) || !(mob.level() instanceof ServerLevel level)
                || !mob.getPersistentData().getBoolean(USING)) return;
        var data = mob.getPersistentData();
        if (!ItemStack.isSameItemSameTags(event.getItem(), ItemStack.of(data.getCompound(FOOD)))) return;
        data.putBoolean(FINISHED, true);
        data.putLong(EAT_DAY, day(level));
        PlayerOutpostData.get(level.getServer()).byMember(mob.getUUID()).ifPresent(home -> {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(home.owner);
            if (owner != null) net.parkabird.changedsynergy.advancement.SynergyAdvancements.grant(owner,
                    net.parkabird.changedsynergy.advancement.SynergyAdvancements.CAMP_MEAL);
        });
        var properties = event.getItem().getFoodProperties(mob);
        if (properties != null) mob.heal(Math.min(4.0F, properties.getNutrition() * 0.5F));
        NpcDialogue.emoteOnly(mob, Emote.CASUAL);
    }

    private int bestFood(Container inventory, boolean communal) {
        if (inventory == null) return -1;
        int best = 0, chosen = -1;
        int slots = communal ? inventory.getContainerSize() : Math.min(24, inventory.getContainerSize());
        for (int slot = 0; slot < slots; slot++) {
            ItemStack food = inventory.getItem(slot);
            int score = foodScore(food);
            int reserve = communal && (food.is(Items.CARROT) || food.is(Items.POTATO)) ? outpost.seedReserve : 0;
            if (score > best && storedCount(inventory, food) > reserve) { best = score; chosen = slot; }
        }
        return chosen;
    }

    private int foodScore(ItemStack food) {
        if (food.isEmpty() || !food.isEdible() || RelationshipFavorService.isGoldenOrange(food)) return 0;
        if (RelationshipFavorService.isDedicatedDietFood(mob, food)) return 3;
        if (RelationshipFavorService.isOrdinaryOrange(food)) return RelationshipFavorService.acceptsOrange(mob) ? 2 : 0;
        HunterArchetype body = HunterArchetype.of(mob);
        if ((body == HunterArchetype.AQUATIC || body == HunterArchetype.FELINE)
                && (food.is(Items.COD) || food.is(Items.SALMON) || food.is(Items.COOKED_COD) || food.is(Items.COOKED_SALMON))) return 2;
        if ((body == HunterArchetype.CANINE || body == HunterArchetype.DRACONIC)
                && (food.is(COOKED_MEAT) || food.is(RAW_MEAT))) return 2;
        return 1;
    }

    private static int storedCount(Container storage, ItemStack food) {
        int count = 0;
        for (int slot = 0; slot < storage.getContainerSize(); slot++) {
            ItemStack existing = storage.getItem(slot);
            if (ItemStack.isSameItemSameTags(food, existing)) count += existing.getCount();
        }
        return count;
    }

    private static Container personalInventory(ChangedEntity mob) {
        ServerPlayer owner = mob.level() instanceof ServerLevel level
                ? PlayerOutpostData.get(level.getServer()).byMember(mob.getUUID())
                    .map(home -> level.getServer().getPlayerList().getPlayer(home.owner)).orElse(null) : null;
        return BondedInventoryService.inventory(mob, owner);
    }

    /** The held meal is a real transferred item. Interrupted use returns it, never a copied reservation. */
    private static void restoreMealHand(ChangedEntity mob) {
        var data = mob.getPersistentData();
        if (!data.contains(SAVED_HAND)) return;
        ItemStack saved = ItemStack.of(data.getCompound(SAVED_HAND));
        ItemStack held = mob.getOffhandItem();
        boolean ownHeld = held.isEmpty() || data.getBoolean(FINISHED)
                || ItemStack.isSameItemSameTags(held, ItemStack.of(data.getCompound(FOOD)));
        if (mob.isUsingItem() && mob.getUsedItemHand() == InteractionHand.OFF_HAND) mob.stopUsingItem();
        data.remove(SAVED_HAND); data.remove(FOOD); data.remove(USING); data.remove(FINISHED);
        if (ownHeld) { mob.setItemInHand(InteractionHand.OFF_HAND, saved); returnToInventory(mob, held); }
        else returnToInventory(mob, saved);
    }

    private static void returnToInventory(ChangedEntity mob, ItemStack stack) {
        if (stack.isEmpty()) return;
        Container own = personalInventory(mob);
        ItemStack remainder = stack.copy();
        for (int pass = 0; pass < 2; pass++) for (int slot = 0; slot < Math.min(24, own.getContainerSize()) && !remainder.isEmpty(); slot++) {
            ItemStack stored = own.getItem(slot);
            if (!own.canPlaceItem(slot, remainder) || (pass == 0 ? stored.isEmpty() || !ItemStack.isSameItemSameTags(stored, remainder) : !stored.isEmpty())) continue;
            int moved = Math.min(remainder.getCount(), Math.min(own.getMaxStackSize(), remainder.getMaxStackSize()) - stored.getCount());
            if (moved <= 0) continue;
            if (stored.isEmpty()) own.setItem(slot, remainder.copyWithCount(moved)); else stored.grow(moved);
            remainder.shrink(moved);
        }
        own.setChanged();
        if (!remainder.isEmpty()) mob.spawnAtLocation(remainder);
    }

    private String tone() {
        return switch (CreaturePersonality.dominantTrait(mob)) {
            case CAUTIOUS, CALM, SENSITIVE -> "reserved";
            case PROTECTIVE -> "protective";
            case PLAYFUL, SHOW_OFF, COMPETITIVE, CURIOUS -> "playful";
            case POLITE -> "gentle";
        };
    }

    private void status(String value) {
        if (outpost != null) {
            Member member = outpost.members.get(mob.getUUID());
            if (member != null) member.workStatus = value;
        }
    }

    private static TagKey<Item> itemTag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("forge", path));
    }

    private static Map<BlockPos, Claim> claims(ServerLevel level) {
        return CLAIMS.computeIfAbsent(level, ignored -> new HashMap<>());
    }

    private boolean claimed(ServerLevel level, BlockPos pos) {
        claims(level).entrySet().removeIf(entry -> entry.getValue().until <= level.getGameTime()
                || level.getEntity(entry.getValue().creature) == null);
        for (var entry : claims(level).entrySet()) {
            Claim claim = entry.getValue();
            if (!claim.creature.equals(mob.getUUID()) && entry.getKey().distSqr(pos)
                    < Math.pow((bodyWidth + claim.width) / 2.0D + 0.5D, 2.0D)) return true;
        }
        return false;
    }

    public static boolean ownsSeat(ChangedEntity mob) {
        return mob.getPersistentData().hasUUID(SEAT) && mob.getVehicle() instanceof SeatEntity seat
                && seat.getUUID().equals(mob.getPersistentData().getUUID(SEAT));
    }

    public static void cleanup(ChangedEntity mob) {
        ACTIVE.remove(mob);
        restoreMealHand(mob);
        if (mob.getPersistentData().contains(PLACE) && mob.level() instanceof ServerLevel level) {
            BlockPos place = BlockPos.of(mob.getPersistentData().getLong(PLACE));
            Claim claim = claims(level).get(place);
            if (claim != null && claim.creature.equals(mob.getUUID())) claims(level).remove(place);
            PlayerOutpostData.get(level.getServer()).byMember(mob.getUUID()).ifPresent(home -> {
                Member member = home.members.get(mob.getUUID());
                if (member != null && (member.workStatus.startsWith("meal_") || member.workStatus.equals("eating")))
                    member.workStatus = "idle";
            });
        }
        if (ownsSeat(mob)) {
            CreatureComfortGoal.endSeatedActivity(mob);
        }
        mob.getPersistentData().remove(SEAT);
        mob.getPersistentData().remove(PLACE);
    }

    public static void cleanupStale(ChangedEntity mob) {
        if (!ACTIVE.contains(mob) && (mob.getPersistentData().hasUUID(SEAT)
                || mob.getPersistentData().contains(PLACE) || mob.getPersistentData().contains(SAVED_HAND))) cleanup(mob);
    }

    public static void cleanupAllForServer(MinecraftServer server) {
        for (ChangedEntity mob : List.copyOf(ACTIVE)) {
            if (mob.level().getServer() == server) cleanup(mob);
        }
        CLAIMS.keySet().removeIf(level -> level.getServer() == server);
    }
}
