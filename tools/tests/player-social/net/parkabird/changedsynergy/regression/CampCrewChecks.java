package net.parkabird.changedsynergy.regression;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;

/** Exercises camp following with the real optional backend, including shell/body separation. */
final class CampCrewChecks {
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void run(ServerPlayer owner, Map<UUID, ServerPlayer> players) throws Exception {
        var level = owner.serverLevel();
        var position = owner.position();
        var marker = new BlockPos(80, 180, 0);
        var oldBlocks = new LinkedHashMap<BlockPos, BlockState>();
        var npc = (ChangedEntity)ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get().getEntityType().create(level);
        var data = PlayerOutpostData.get(owner.server);
        PlayerOutpostGoal goal = null;
        try {
            for (var previous : java.util.List.copyOf(data.all()))
                if (previous.marker.equals(marker)) data.disband(previous.owner);
            for (BlockPos cursor : BlockPos.betweenClosed(marker.offset(-14, -1, -4), marker.offset(14, -1, 4))) {
                oldBlocks.put(cursor.immutable(), level.getBlockState(cursor));
                level.setBlock(cursor, Blocks.STONE.defaultBlockState(), 3);
            }
            oldBlocks.put(marker, level.getBlockState(marker)); level.setBlock(marker, Blocks.CAMPFIRE.defaultBlockState(), 3);
            owner.setPos(92.5, 180, 0.5); npc.setPos(82.5, 180, 0.5); npc.setOnGround(true);
            level.addFreshEntity(npc);
            LatexSocialMemory.addBond(npc, owner);
            check(PlayerOutpostService.authorized(npc, owner), "Camp worker retains real owner authorization");
            check(data.claim(level, owner.getUUID(), marker).equals("claimed"), "Crew fixture claims real campfire");
            var camp = data.byOwner(owner.getUUID()).orElseThrow();
            check(data.add(camp, npc.getUUID(), "Crew", camp.dimension, npc.blockPosition(), PlayerOutpostSupplyType.ORANGE),
                    "Crew member is an ordinary NPC");
            data.role(camp, npc.getUUID(), PlayerOutpostData.Role.CREW, npc.blockPosition());
            PlayerOutpostService.applyRole(npc, owner, PlayerOutpostData.Role.CREW);
            goal = new PlayerOutpostGoal(npc);
            check(goal.canUse(), "Human-controlled crew follows the real player");
            goal.start(); goal.tick();
            check(read(goal, "crewTarget") == owner, "Ordinary target is the actual player");
            check(!new SocialFollowGoal(npc).canUse() && !new BondedFollowGoal(npc).canUse(),
                    "Camp crew is not controlled by competing social/pet follow goals");
            if (TrueTransfurCompat.supportsNativeOrigin()) {
                Class controller = Class.forName("com.lugert.true_transfur.transfur.TransfurController");
                Class mode = Class.forName("com.lugert.true_transfur.transfur.ControlMode");
                java.lang.reflect.Method switchMode;
                try { switchMode = controller.getMethod("switchControlMode", ServerPlayer.class, mode); }
                catch (NoSuchMethodException olderBackend) {
                    switchMode = controller.getMethod("setControlMode", ServerPlayer.class, mode);
                }
                Object instinct = Enum.valueOf(mode, "INSTINCT");
                switchMode.invoke(null, owner, instinct);
                check(TrueTransfurCompat.isInstinctControlled(owner), "Crew owner enters actual Instinct mode");
                var body = TrueTransfurCompat.interactionBody(owner);
                if (body != owner) {
                    owner.setPos(150.5, 180, 0.5);
                    check(goal.canContinueToUse(), "Crew continues toward visible body despite distant technical shell");
                    goal.tick();
                    check(read(goal, "crewTarget") == body && read(goal, "destination").equals(body.blockPosition()),
                            "Entering Instinct replaces the movement target with the visible body");
                    var landing = BondedTeleportSafety.findSafeLanding(level, npc, owner).orElseThrow();
                    check(landing.distanceToSqr(body.position()) < 14 * 14,
                            "Catch-up landing is near the actual body, never the distant hidden shell");
                    check(body instanceof ChangedEntity proxy && !PlayerOutpostService.authorized(proxy, owner),
                            "Player body remains excluded from NPC camp recruitment");
                    TrueTransfurCompat.ensurePlayerControl(owner);
                    owner.setPos(92.5, 180, 0.5);
                    check(goal.canContinueToUse(), "Leaving Instinct resolves the human-controlled player again");
                    goal.tick(); check(read(goal, "crewTarget") == owner, "Removed old proxy leaves no cached follow target");
                    check(Boolean.TRUE.equals(switchMode.invoke(null, owner, instinct)), "Owner can enter Instinct again");
                    var replacement = TrueTransfurCompat.interactionBody(owner);
                    check(replacement != owner && replacement != body, "Backend creates a new physical body");
                    owner.setPos(150.5, 180, 0.5); goal.tick();
                    check(read(goal, "crewTarget") == replacement, "Crew resolves replacement body without stale UUID");
                    replacement.discard();
                    check(!goal.canContinueToUse(), "Unavailable physical body pauses following instead of using hidden shell");
                    goal.tick(); check(npc.getNavigation().isDone(), "Missing body clears the old path immediately");
                    TrueTransfurCompat.ensurePlayerControl(owner); owner.setPos(92.5, 180, 0.5);
                } else {
                    check(!TrueTransfurCompat.isInstinctShell(owner) && goal.canContinueToUse(),
                            "Older direct-control TrueTransfur safely follows the player itself");
                    goal.tick(); check(read(goal, "crewTarget") == owner, "Old backend needs no proxy target");
                    TrueTransfurCompat.ensurePlayerControl(owner);
                }
            }
            players.remove(owner.getUUID());
            check(!goal.canContinueToUse(), "Offline account invalidates camp following");
            goal.tick(); check(npc.getNavigation().isDone(), "Disconnect clears the route without a null-pointer error");
            players.put(owner.getUUID(), owner);
            for (String id : new String[]{"native_origin", "native_bond"})
                check((owner.server.getAdvancements().getAdvancement(net.minecraft.resources.ResourceLocation.parse("changed_synergy:" + id)) != null)
                                == net.minecraftforge.fml.ModList.get().isLoaded("true_transfur"),
                        "Optional advancement loads exactly when TrueTransfur is installed: " + id);
            System.out.println("CAMP_CREW_INSTINCT_REGRESSION_PASS: " + System.getProperty("changed_synergy.test.tt", "none"));
        } finally {
            players.put(owner.getUUID(), owner); TrueTransfurCompat.ensurePlayerControl(owner);
            if (goal != null) goal.stop(); data.disband(owner.getUUID()); PlayerOutpostService.clearAssignment(npc); npc.discard();
            for (var entry : oldBlocks.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 3);
            owner.setPos(position);
        }
    }
    private static Object read(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
