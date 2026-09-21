package net.parkabird.changedsynergy.ai;

import java.util.Optional;
import java.util.UUID;
import net.ltxprogrammer.changed.data.AccessorySlots;
import net.ltxprogrammer.changed.entity.beast.LatexBenignOrca;
import net.ltxprogrammer.changed.entity.beast.LatexBenignWolf;
import net.ltxprogrammer.changed.entity.robot.Exoskeleton;
import net.ltxprogrammer.changed.init.ChangedAccessorySlots;
import net.ltxprogrammer.changed.item.ExoskeletonItem;
import net.ltxprogrammer.changed.util.EntityUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.ExoskeletonMotionPacket;

/**
 * Equipment is the sole live carrier. A short-lived, unregistered Exoskeleton probe
 * selects paths with Changed's own unhosted goals; it is never ticked or added to a level.
 * All mutating APIs require the server thread. The owner persists State.save(), owns
 * input exclusion, the 600-tick sleep deadline and human/safe-position restoration.
 * Remove this equipment BEFORE changing the player's variant (slot initialization can
 * otherwise hand it to the inventory). Never recreate equipment from SourceSnapshot.
 */
public final class ExoskeletonTakeoverAdapter {
    private static final String DATA = "ChangedSynergyForcedExoskeleton";
    private ExoskeletonTakeoverAdapter() { }

    public enum Motion { MOVED, PARKED, NEEDS_RECOVERY }
    public enum Release { ENTITY_RETURNED, DROPPED, ALREADY_RELEASED, NOT_FOUND, UNSAFE, RETRY }

    /** Called only by the restrain-goal mixin, immediately before its slot replacement. */
    public static void markForcedStack(ServerPlayer player, Exoskeleton source, ItemStack stack) {
        requireThread(player);
        CompoundTag data = new CompoundTag();
        data.putUUID("Token", UUID.randomUUID());
        data.putUUID("Player", player.getUUID());
        data.putUUID("Source", source.getUUID());
        data.putString("Dimension", player.level().dimension().location().toString());
        data.putDouble("X", player.getX());
        data.putDouble("Y", player.getY());
        data.putDouble("Z", player.getZ());
        data.putDouble("DockX", source.getX());
        data.putDouble("DockZ", source.getZ());
        data.putFloat("Charge", source.getCharge());
        data.putFloat("MechanicalDamage", source.getDamage());
        data.putInt("InitialItemDamage", stack.getDamageValue());
        CompoundTag snapshot = source.saveWithoutId(new CompoundTag());
        snapshot.putString("id", EntityType.getKey(source.getType()).toString());
        data.put("SourceSnapshot", snapshot);
        // Optional: only a genuinely attached charger is known; closestCharger is not provenance.
        source.getSleepingPos().ifPresent(pos -> data.putLong("Charger", pos.asLong()));
        stack.getOrCreateTag().put(DATA, data);
    }

    public static boolean isBenign(ServerPlayer player) {
        var overlay = EntityUtil.maybeGetOverlaying(player);
        return overlay instanceof LatexBenignWolf || overlay instanceof LatexBenignOrca;
    }

    /** Captured by goal.start HEAD, before Changed detaches and clears its sleeping position. */
    public static void recordSourceCharger(ItemStack stack, BlockPos charger) {
        CompoundTag data = metadata(stack);
        if (charger != null && data.hasUUID("Token")) data.putLong("Charger", charger.asLong());
    }

    /** Synchronous beginExoskeleton entry: succeeds only after the marked source was discarded. */
    public static Optional<State> capture(ServerPlayer player, Exoskeleton source) {
        requireThread(player);
        ItemStack stack = worn(player);
        CompoundTag data = metadata(stack);
        if (!source.isRemoved() || !isBenign(player) || !data.hasUUID("Token")
                || !data.hasUUID("Player") || !player.getUUID().equals(data.getUUID("Player"))
                || !data.hasUUID("Source") || !source.getUUID().equals(data.getUUID("Source"))
                || data.getBoolean("Released")) return Optional.empty();
        return State.load(data.copy());
    }

    /** Main-service API. Empty means no matching successful forced attachment. */
    public static CompoundTag capture(Exoskeleton source, ServerPlayer player) {
        return capture(player, source).map(State::save).orElseGet(CompoundTag::new);
    }

    /** The accessory API may copy a stack while inserting it; compare its receipt. */
    public static boolean isForcedStack(ServerPlayer player, Exoskeleton source, ItemStack stack) {
        CompoundTag data = metadata(stack);
        return data.hasUUID("Player") && player.getUUID().equals(data.getUUID("Player"))
                && data.hasUUID("Source") && source.getUUID().equals(data.getUUID("Source"))
                && data.hasUUID("Token") && !data.getBoolean("Released");
    }

    /** Main-service API; persists motion bookkeeping directly into the supplied session tag. */
    public static Motion tick(ServerPlayer player, CompoundTag tag, UUID sessionId) {
        Optional<State> state = State.load(tag);
        if (state.isEmpty()) return Motion.NEEDS_RECOVERY;
        Motion result = tick(player, state.get(), sessionId);
        replace(tag, state.get().save());
        return result;
    }

    /** Primary recovery API: returns the autonomous robot, never gives it to the player.
     * Call BEFORE human restoration. UNSAFE/RETRY retain equipment and require another
     * attempt after the service chooses a safe area. There is no automatic item fallback.
     */
    public static Release release(ServerPlayer player, CompoundTag tag) {
        return release(player, tag, 1, 3);
    }

    /** Restore the autonomous controller outside the waking player's immediate area. */
    public static Release releaseAway(ServerPlayer player, CompoundTag tag) {
        return release(player, tag, 10, 18);
    }

    private static Release release(ServerPlayer player, CompoundTag tag,
            int minimumRadius, int maximumRadius) {
        requireThread(player);
        Optional<State> loaded = State.load(tag);
        if (loaded.isEmpty()) return Release.NOT_FOUND;
        State state = loaded.get();
        if (!player.getUUID().equals(state.data.getUUID("Player"))) return Release.NOT_FOUND;
        if (state.data.getBoolean("Released")) return Release.ALREADY_RELEASED;
        StackLocation location = locate(player, state);
        // Only instantiate a not-yet-added candidate for FINAL recovery, never for movement.
        CompoundTag snapshot = state.data.getCompound("SourceSnapshot").copy();
        if (!snapshot.hasUUID("UUID") || !snapshot.getUUID("UUID").equals(state.sourceId()))
            return Release.NOT_FOUND;
        Release reconciled = reconcileLoadedReceipt(player, state);
        if (reconciled != null) { tag.merge(state.save()); return reconciled; }
        if (location == null) return Release.NOT_FOUND;
        ItemStack actual = location.stack();
        if (metadata(actual).getBoolean("Released")) {
            tag.putBoolean("Released", true);
            return Release.ALREADY_RELEASED;
        }
        var candidate = EntityType.create(snapshot, player.serverLevel()).orElse(null);
        if (!(candidate instanceof Exoskeleton robot)) return Release.NOT_FOUND;
        robot.setCharging(false);
        robot.setTarget(null);
        robot.getNavigation().stop();
        robot.setDeltaMovement(Vec3.ZERO);
        robot.fallDistance = 0;
        Vec3 safe = null;
        for (int radius = minimumRadius; radius <= maximumRadius && safe == null; radius++) {
            for (int[] direction : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}}) {
                for (int yOffset : new int[]{0, 1, -1, 2, -2, 3, -3}) {
                    Vec3 pos = player.position().add(direction[0] * radius, yOffset, direction[1] * radius);
                    robot.setPos(pos.x, pos.y, pos.z);
                    if (!robot.getBoundingBox().intersects(player.getBoundingBox())
                            && safeBox(player, robot.getBoundingBox())) { safe = pos; break; }
                }
                if (safe != null) break;
            }
        }
        if (safe == null) return Release.UNSAFE;
        robot.loadFromItemStack(actual);
        restoreMechanicalState(robot, actual);
        // Receipt for session crash/reentry reconciliation; no materialized equipment snapshot.
        robot.getPersistentData().putUUID("ChangedSynergyExoskeletonReceipt", state.token());
        if (location.current() != actual) return Release.RETRY;
        location.set(ItemStack.EMPTY);
        boolean added = false;
        try {
            added = player.serverLevel().addFreshEntity(robot);
        } finally {
            if (!added) {
                robot.discard();
                location.set(actual);
            }
        }
        if (!added) return Release.RETRY;
        // The actual equipment has now been exchanged for exactly one registered entity.
        actual.setCount(0);
        tag.putBoolean("Released", true);
        tag.putString("Disposition", "entity");
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        return Release.ENTITY_RETURNED;
    }

    /** Explicit fallback only, after the owner has selected a safe point. Never call both
     * this and release using separate copies of the persisted session tag.
     */
    public static Release releaseToGround(ServerPlayer player, CompoundTag tag, Vec3 safePos) {
        Optional<State> loaded = State.load(tag);
        if (loaded.isEmpty()) return Release.NOT_FOUND;
        Release result = releaseToGround(player, loaded.get(), safePos);
        tag.merge(loaded.get().save());
        if (result == Release.DROPPED) tag.putString("Disposition", "item");
        return result;
    }

    public static final class State {
        private final CompoundTag data;
        private State(CompoundTag data) { this.data = data; }
        public CompoundTag save() { return data.copy(); }
        public UUID token() { return data.getUUID("Token"); }
        public UUID sourceId() { return data.getUUID("Source"); }
        public Vec3 origin() { return new Vec3(data.getDouble("X"), data.getDouble("Y"), data.getDouble("Z")); }
        public Optional<BlockPos> charger() {
            return data.contains("Charger") ? Optional.of(BlockPos.of(data.getLong("Charger"))) : Optional.empty();
        }
        public static Optional<State> load(CompoundTag saved) {
            if (!saved.hasUUID("Token") || !saved.hasUUID("Player") || !saved.hasUUID("Source")
                    || saved.getString("Dimension").isEmpty()) return Optional.empty();
            Vec3 point = new Vec3(saved.getDouble("X"), saved.getDouble("Y"), saved.getDouble("Z"));
            if (!finite(point)) return Optional.empty();
            return Optional.of(new State(saved.copy()));
        }
    }

    /** Server-controlled movement of the worn body. The player supplies no movement or
     * camera rotation. Changed's own unhosted wander goal selects a path,
     * then the marked equipment drives the host along that validated path without
     * creating a second live robot.
     */
    public static Motion tick(ServerPlayer player, State state, UUID sessionId) {
        requireThread(player);
        if (!owns(player, state) || state.data.getBoolean("Released") || !matches(worn(player), state)
                || !player.isAlive() || !isBenign(player))
            return Motion.NEEDS_RECOVERY;
        if (player.isPassenger() || player.isFallFlying() || player.isInWaterOrBubble()
                || player.isInLava()) {
            player.setDeltaMovement(Vec3.ZERO);
            if (player.getY() < player.level().getMinBuildHeight() + 4) {
                TakeoverSafety.find(player, state.origin(), null, 6, false).ifPresent(point ->
                        player.connection.teleport(point.x, point.y, point.z,
                                player.getYRot(), player.getXRot()));
            }
            return Motion.PARKED;
        }
        if (!player.onGround() && !hasFooting(player.getBoundingBox(), player)) {
            player.setDeltaMovement(0.0D,
                    Math.min(0.0D, player.getDeltaMovement().y), 0.0D);
            return Motion.PARKED;
        }
        if (!hazardFreeBox(player, player.getBoundingBox())) {
            player.setDeltaMovement(Vec3.ZERO);
            return Motion.PARKED;
        }
        long now = player.serverLevel().getGameTime();
        ListTag path = state.data.getList("ControlPath", Tag.TAG_COMPOUND);
        int index = Mth.clamp(state.data.getInt("ControlPathIndex"), 0, path.size());
        if (index >= path.size()) {
            if (now < state.data.getLong("NextControlPath")) {
                player.setDeltaMovement(Vec3.ZERO);
                return Motion.PARKED;
            }
            path = selectNativePath(player, state);
            state.data.put("ControlPath", path);
            state.data.putInt("ControlPathIndex", 0);
            state.data.putLong("NextControlPath", now + (path.isEmpty() ? 20L : 120L));
            index = 0;
            if (path.isEmpty()) {
                player.setDeltaMovement(Vec3.ZERO);
                return Motion.PARKED;
            }
        }
        Vec3 target = pathPoint(path.getCompound(index));
        if (!finite(target)) {
            clearControlPath(state, now);
            return Motion.PARKED;
        }
        Vec3 previous = new Vec3(
                state.data.contains("MotionX", Tag.TAG_DOUBLE)
                        ? state.data.getDouble("MotionX") : player.getX(),
                player.getY(),
                state.data.contains("MotionZ", Tag.TAG_DOUBLE)
                        ? state.data.getDouble("MotionZ") : player.getZ());
        Vec3 actualMove = player.position().subtract(previous);
        state.data.putDouble("MotionX", player.getX());
        state.data.putDouble("MotionZ", player.getZ());
        if (actualMove.horizontalDistanceSqr() > 0.0004D) {
            state.data.putLong("LastProgressTick", now);
        } else if (!state.data.contains("LastProgressTick", Tag.TAG_LONG)) {
            state.data.putLong("LastProgressTick", now);
        }
        Vec3 delta = target.subtract(player.position());
        if (delta.horizontalDistanceSqr() < 0.2D && Math.abs(delta.y) < 1.25D) {
            state.data.putInt("ControlPathIndex", index + 1);
            player.setDeltaMovement(0.0D,
                    Math.min(0.0D, player.getDeltaMovement().y), 0.0D);
            return Motion.PARKED;
        }
        if (now - state.data.getLong("LastProgressTick") > 24L) {
            clearControlPath(state, now);
            return Motion.PARKED;
        }
        Vec3 requested = new Vec3(delta.x, 0.0D, delta.z).normalize().scale(0.105D);
        if (!hazardFreeBox(player, player.getBoundingBox().move(requested))) {
            player.setDeltaMovement(Vec3.ZERO);
            clearControlPath(state, now);
            return Motion.PARKED;
        }
        // ServerPlayer locomotion is normally driven by client movement packets,
        // which takeover deliberately rejects. Move authoritatively here through
        // vanilla collision handling, then send a render frame to the owning client.
        player.setDeltaMovement(requested.x,
                Math.min(0.0D, player.getDeltaMovement().y), requested.z);
        player.move(MoverType.SELF, requested);
        float desiredYaw = (float)(Math.atan2(requested.z, requested.x)
                * 180.0D / Math.PI) - 90.0F;
        float yaw = Mth.rotLerp(0.35F, player.getYRot(), desiredYaw);
        player.setYRot(yaw);
        player.setYBodyRot(yaw);
        player.setYHeadRot(yaw);
        player.setXRot(Mth.rotLerp(0.3F, player.getXRot(), 0.0F));
        ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ExoskeletonMotionPacket(sessionId, player.getX(), player.getY(),
                        player.getZ(), requested.x, 0.0D, requested.z, yaw));
        return Motion.MOVED;
    }

    private static ListTag selectNativePath(ServerPlayer player, State state) {
        ListTag serialized = new ListTag();
        CompoundTag snapshot = state.data.getCompound("SourceSnapshot").copy();
        Entity created = EntityType.create(snapshot, player.serverLevel()).orElse(null);
        if (!(created instanceof Exoskeleton probe)) return serialized;
        try {
            probe.setPos(player.getX(), player.getY(), player.getZ());
            probe.setYRot(player.getYRot());
            probe.setOnGround(true);
            probe.setCharging(false);
            restoreMechanicalState(probe, worn(player));
            // A worn controller cannot dock without abandoning its host. Ignore only
            // the wander goal's low-battery gate on this unregistered probe; the real
            // equipment charge remains untouched and is restored on separation.
            probe.setCharge(1.0F);
            Path nativePath = null;
            // A normal entity retries RandomStrollGoal over many ticks. A takeover
            // probe exists for only this call, so give the same native goal several
            // destination attempts before declaring the controller parked.
            for (int attempt = 0; attempt < 6 && nativePath == null; attempt++) {
                NativeWanderGoal wander = new NativeWanderGoal(probe);
                wander.trigger();
                if (wander.canUse()) {
                    wander.start();
                    nativePath = probe.getNavigation().getPath();
                    if (nativePath == null) nativePath = wander.rebuildPath();
                    if (nativePath != null && nativePath.getNodeCount() < 2) nativePath = null;
                }
            }
            if (nativePath == null || nativePath.getNodeCount() < 1) return serialized;
            int first = Math.max(0, nativePath.getNextNodeIndex());
            int end = Math.min(nativePath.getNodeCount(), first + 48);
            for (int i = first; i < end; i++) {
                Vec3 point = nativePath.getEntityPosAtNode(probe, i);
                if (!finite(point)) continue;
                CompoundTag node = new CompoundTag();
                node.putDouble("X", point.x);
                node.putDouble("Y", point.y);
                node.putDouble("Z", point.z);
                serialized.add(node);
            }
            return serialized;
        } finally {
            probe.getNavigation().stop();
            probe.discard();
        }
    }

    private static Vec3 pathPoint(CompoundTag node) {
        return new Vec3(node.getDouble("X"), node.getDouble("Y"), node.getDouble("Z"));
    }

    private static void clearControlPath(State state, long now) {
        state.data.remove("ControlPath");
        state.data.putInt("ControlPathIndex", 0);
        state.data.remove("MotionX");
        state.data.remove("MotionZ");
        state.data.remove("LastProgressTick");
        state.data.putLong("NextControlPath", now + 10L);
    }

    private static void replace(CompoundTag target, CompoundTag source) {
        for (String key : java.util.Set.copyOf(target.getAllKeys())) target.remove(key);
        target.merge(source);
    }

    /** Exposes no new behavior: this is Changed's exact unhosted wander goal. */
    private static final class NativeWanderGoal extends Exoskeleton.ExoskeletonWanderGoal {
        private NativeWanderGoal(Exoskeleton exoskeleton) {
            super(exoskeleton, 0.3D, 120, false);
        }
        private Path rebuildPath() {
            return exoskeleton.getNavigation().createPath(wantedX, wantedY, wantedZ, 0);
        }
    }

    /** Move the actual marked stack, not a copy. A rejected spawn rolls back the exact slot.
     * safePos is supplied by the owner's placement service; this adds local physical checks.
     * NOT_FOUND means recovery must reconcile an external move/drop; NEVER mint a replacement.
     * Persist the result/state in the owning session. Cross-file crash atomicity is its concern.
     */
    public static Release releaseToGround(ServerPlayer player, State state, Vec3 safePos) {
        requireThread(player);
        if (!player.getUUID().equals(state.data.getUUID("Player"))) return Release.NOT_FOUND;
        if (state.data.getBoolean("Released")) return Release.ALREADY_RELEASED;
        Release reconciled = reconcileLoadedReceipt(player, state);
        if (reconciled != null) return reconciled;
        if (!finite(safePos) || player.position().distanceToSqr(safePos) > 16
                || !safeBox(player, new AABB(safePos.x - 0.2, safePos.y, safePos.z - 0.2,
                        safePos.x + 0.2, safePos.y + 0.5, safePos.z + 0.2))) return Release.UNSAFE;
        var slots = AccessorySlots.getForEntity(player).orElse(null);
        ItemStack actual = worn(player);
        int inventorySlot = -1;
        boolean accessory = matches(actual, state);
        if (!accessory) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (matches(player.getInventory().getItem(i), state)) {
                    actual = player.getInventory().getItem(i);
                    inventorySlot = i;
                    break;
                }
            }
            if (inventorySlot < 0) return Release.NOT_FOUND;
        }
        if (metadata(actual).getBoolean("Released")) {
            state.data.putBoolean("Released", true);
            return Release.ALREADY_RELEASED;
        }
        if (accessory) slots.setItem(ChangedAccessorySlots.FULL_BODY.get(), ItemStack.EMPTY);
        else player.getInventory().setItem(inventorySlot, ItemStack.EMPTY);
        metadata(actual).putBoolean("Released", true);
        ItemEntity drop = new ItemEntity(player.serverLevel(), safePos.x, safePos.y, safePos.z, actual);
        drop.setDeltaMovement(Vec3.ZERO);
        drop.setDefaultPickUpDelay();
        // Stable receipt ID helps the owning persistent session reconcile interrupted recovery.
        drop.setUUID(state.token());
        boolean added = false;
        try {
            added = player.serverLevel().addFreshEntity(drop);
        } finally {
            if (!added) {
                drop.setItem(ItemStack.EMPTY);
                drop.discard();
                metadata(actual).putBoolean("Released", false);
                if (accessory) slots.setItem(ChangedAccessorySlots.FULL_BODY.get(), actual);
                else player.getInventory().setItem(inventorySlot, actual);
            }
        }
        if (!added) return Release.RETRY;
        state.data.putBoolean("Released", true);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        return Release.DROPPED;
    }

    /** Called by the loadFromItemStack TAIL mixin, including later normal placement. */
    public static void restoreMechanicalState(Exoskeleton robot, ItemStack stack) {
        CompoundTag data = metadata(stack);
        if (!data.hasUUID("Token")) return;
        float charge = data.getFloat("Charge")
                - (stack.getDamageValue() - data.getInt("InitialItemDamage")) / (float)Math.max(1, stack.getMaxDamage());
        robot.setCharge(Mth.clamp(Float.isFinite(charge) ? charge : 0, 0, 1));
        float damage = data.getFloat("MechanicalDamage");
        robot.setDamage(Mth.clamp(Float.isFinite(damage) ? damage : 0, 0, robot.getMaxDamage()));
        // Do not replay SourceSnapshot: passengers/inventory/UUID could duplicate live objects.
    }

    private static boolean owns(ServerPlayer player, State state) {
        return player.getUUID().equals(state.data.getUUID("Player"))
                && player.level().dimension().location().toString().equals(state.data.getString("Dimension"));
    }
    private record StackLocation(ServerPlayer player, AccessorySlots slots, int index, ItemStack stack) {
        ItemStack current() {
            return slots != null ? slots.getItem(ChangedAccessorySlots.FULL_BODY.get()).orElse(ItemStack.EMPTY)
                    : player.getInventory().getItem(index);
        }
        void set(ItemStack value) {
            if (slots != null) slots.setItem(ChangedAccessorySlots.FULL_BODY.get(), value);
            else player.getInventory().setItem(index, value);
        }
    }
    private static StackLocation locate(ServerPlayer player, State state) {
        ItemStack stack = worn(player);
        if (matches(stack, state)) return new StackLocation(player,
                AccessorySlots.getForEntity(player).orElseThrow(), -1, stack);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            stack = player.getInventory().getItem(i);
            if (matches(stack, state)) return new StackLocation(player, null, i, stack);
        }
        return null;
    }

    /** A loaded matching receipt proves delivery; lack of one does NOT prove no saved
     * entity exists in an unloaded chunk. The owner must journal/reconcile across crashes.
     * null means no loaded identity blocks a normal, already-authorized live retry.
     */
    private static Release reconcileLoadedReceipt(ServerPlayer player, State state) {
        Entity source = null;
        Entity item = null;
        for (var level : player.serverLevel().getServer().getAllLevels()) {
            Entity s = level.getEntity(state.sourceId());
            Entity i = level.getEntity(state.token());
            if ((s != null && source != null) || (i != null && item != null)) return Release.RETRY;
            if (s != null) source = s;
            if (i != null) item = i;
        }
        if (source == null && item == null) return null;
        if (source != null && item != null) return Release.RETRY;
        boolean entityReceipt = source instanceof Exoskeleton
                && source.getPersistentData().hasUUID("ChangedSynergyExoskeletonReceipt")
                && state.token().equals(source.getPersistentData().getUUID("ChangedSynergyExoskeletonReceipt"));
        boolean itemReceipt = item instanceof ItemEntity drop && matches(drop.getItem(), state)
                && metadata(drop.getItem()).getBoolean("Released");
        if (!entityReceipt && !itemReceipt) return Release.RETRY;
        // Only the exact session-marked inventory residue can be discarded after proof of delivery.
        StackLocation residue = locate(player, state);
        if (residue != null) {
            residue.set(ItemStack.EMPTY);
            residue.stack().setCount(0);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
        }
        state.data.putBoolean("Released", true);
        state.data.putString("Disposition", entityReceipt ? "entity" : "item");
        return Release.ALREADY_RELEASED;
    }
    private static boolean matches(ItemStack stack, State state) {
        CompoundTag tag = metadata(stack);
        return stack.getCount() == 1 && tag.hasUUID("Token") && state.token().equals(tag.getUUID("Token"))
                && tag.hasUUID("Player") && state.data.getUUID("Player").equals(tag.getUUID("Player"))
                && tag.hasUUID("Source") && state.sourceId().equals(tag.getUUID("Source"));
    }
    private static ItemStack worn(ServerPlayer player) {
        return AccessorySlots.getForEntity(player).flatMap(s -> s.getItem(ChangedAccessorySlots.FULL_BODY.get()))
                .orElse(ItemStack.EMPTY);
    }

    public static boolean isWearingExoskeleton(ServerPlayer player) {
        return worn(player).getItem() instanceof ExoskeletonItem<?>;
    }

    /** Last-resort removal of the real worn item, including old/externally stripped
     * session metadata. Never constructs a replacement from the saved robot. */
    public static boolean detachRemainingEquipment(ServerPlayer player) {
        requireThread(player);
        ItemStack actual = worn(player);
        if (!(actual.getItem() instanceof ExoskeletonItem<?>)) return true;
        AccessorySlots slots = AccessorySlots.getForEntity(player).orElse(null);
        if (slots == null) return false;
        int free = player.getInventory().getFreeSlot();
        slots.setItem(ChangedAccessorySlots.FULL_BODY.get(), ItemStack.EMPTY);
        if (isWearingExoskeleton(player)) return false;
        if (free >= 0) {
            player.getInventory().setItem(free, actual);
        } else {
            ItemEntity dropped = new ItemEntity(player.serverLevel(), player.getX(),
                    player.getY(), player.getZ(), actual);
            dropped.setDefaultPickUpDelay();
            if (!player.serverLevel().addFreshEntity(dropped)) {
                dropped.setItem(ItemStack.EMPTY);
                slots.setItem(ChangedAccessorySlots.FULL_BODY.get(), actual);
                return false;
            }
        }
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        return true;
    }

    public static void syncEquipment(ServerPlayer player) {
        AccessorySlots.getForEntity(player).ifPresent(slots ->
                net.ltxprogrammer.changed.Changed.PACKET_HANDLER.send(
                        net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                        new net.ltxprogrammer.changed.network.packet.AccessorySyncPacket(player.getId(), slots)));
    }
    private static CompoundTag metadata(ItemStack stack) {
        return stack.getItem() instanceof ExoskeletonItem<?> && stack.hasTag()
                ? stack.getTag().getCompound(DATA) : new CompoundTag();
    }
    private static CompoundTag persisted(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND))
            root.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
    }
    private static boolean finite(Vec3 v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }
    private static void requireThread(ServerPlayer player) {
        if (!player.serverLevel().getServer().isSameThread())
            throw new IllegalStateException("Exoskeleton takeover requires the server thread");
    }
    private static boolean safeBox(ServerPlayer player, AABB box) {
        if (!hazardFreeBox(player, box)) return false;
        var level = player.serverLevel();
        for (double x : new double[]{box.minX + 0.01, box.maxX - 0.01}) {
            for (double z : new double[]{box.minZ + 0.01, box.maxZ - 0.01}) {
                BlockPos below = BlockPos.containing(x, box.minY - 0.05, z);
                if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) return false;
            }
        }
        return true;
    }

    private static boolean hazardFreeBox(ServerPlayer player, AABB box) {
        return environmentSafeBox(player, box)
                && player.serverLevel().noCollision(player, box);
    }

    private static boolean environmentSafeBox(ServerPlayer player, AABB box) {
        var level = player.serverLevel();
        if (!level.getWorldBorder().isWithinBounds(box)) return false;
        BlockPos min = BlockPos.containing(box.minX - 0.25, box.minY - 0.05, box.minZ - 0.25);
        BlockPos max = BlockPos.containing(box.maxX + 0.25, box.maxY, box.maxZ + 0.25);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.hasChunkAt(pos) || pos.getY() < level.getMinBuildHeight()
                    || pos.getY() >= level.getMaxBuildHeight()) return false;
            var block = level.getBlockState(pos);
            if (!block.getFluidState().isEmpty() || block.is(Blocks.FIRE) || block.is(Blocks.SOUL_FIRE)
                    || block.is(Blocks.CACTUS) || block.is(Blocks.MAGMA_BLOCK)
                    || block.is(Blocks.CAMPFIRE) || block.is(Blocks.SOUL_CAMPFIRE)
                    || block.is(Blocks.POWDER_SNOW) || block.is(Blocks.SWEET_BERRY_BUSH)
                    || block.is(Blocks.WITHER_ROSE) || block.is(Blocks.NETHER_PORTAL)
                    || block.is(Blocks.END_PORTAL) || block.is(Blocks.END_GATEWAY)) return false;
        }
        return true;
    }

    private static boolean hasFooting(AABB box, ServerPlayer player) {
        var level = player.serverLevel();
        for (double x : new double[]{box.minX + 0.05D, box.maxX - 0.05D}) {
            for (double z : new double[]{box.minZ + 0.05D, box.maxZ - 0.05D}) {
                BlockPos below = BlockPos.containing(x, box.minY - 0.08D, z);
                if (level.hasChunkAt(below)
                        && !level.getBlockState(below).getCollisionShape(level, below).isEmpty()) return true;
            }
        }
        return false;
    }
}
