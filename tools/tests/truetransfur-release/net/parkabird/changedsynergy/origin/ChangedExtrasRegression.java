package net.parkabird.changedsynergy.origin;

import java.lang.reflect.Method;
import java.util.Set;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.compat.ChangedExtrasCompat;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;

/** Exercises the supplied Extras release, rather than substitute smart-AI classes. */
final class ChangedExtrasRegression {
    private static int checks;

    @SuppressWarnings("unchecked")
    static void run(MinecraftServer server, ServerPlayer owner) throws Exception {
        if (!ModList.get().isLoaded("changedextras")) return;
        var level = server.overworld();
        var handler = Class.forName("com.katt.changedextras.common.ai.LatexMobAIHandler");
        var installedField = handler.getDeclaredField("INSTALLED_MOBS"); installedField.setAccessible(true);
        var installed = (Set<ChangedEntity>)installedField.get(null);
        var join = handler.getMethod("onEntityJoin", EntityJoinLevelEvent.class);
        var tick = handler.getMethod("onLivingTick", LivingEvent.LivingTickEvent.class);
        var key = (GameRules.Key<GameRules.BooleanValue>)Class.forName("com.katt.changedextras.common.ChangedExtrasGameRules")
                .getField("SMART_LATEX_AI_ENABLED").get(null);
        boolean previous = level.getGameRules().getBoolean(key);
        var config = Class.forName("com.katt.changedextras.Config").getField("latexAlwaysAttackPlayers");
        boolean previousAggro = config.getBoolean(null);
        var artistId = ResourceLocation.parse("changedextras:latex_artist");
        var artist = creature(server, artistId.toString());
        var wild = creature(server, "changed:dark_latex_wolf_male");
        var markedBody = creature(server, "changed:dark_latex_wolf_male");
        var data = PlayerOutpostData.get(server);
        BlockPos marker = new BlockPos(80, 180, 80);
        var oldBlock = level.getBlockState(marker);
        try {
            check(CreatureSocialProfile.isPermanentlyExcluded(artistId), "Artist exclusion survives replaced tags");
            check(CreatureSocialProfile.of(artist) == CreatureSocialProfile.Kind.NONE, "Actual 1.1.8b Artist has no Synergy social profile");
            check(!CreatureSocialProfile.allowsSocialWheel(artist) && !CreatureSocialProfile.allowsDialogue(artist)
                    && !CreatureSocialProfile.allowsPersonalRelationship(artist), "Boss wheel, dialogue and relationship excluded");
            check(!CreaturePersonality.establishRelationship(artist, owner) && !PlayerOutpostService.authorized(artist, owner),
                    "Artist cannot be named through friendship or invited to camp");
            var includeModded = net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.includeModdedOriginSpecies;
            boolean previousModded = includeModded.get();
            try {
                includeModded.set(true);
                check(NativeOriginCatalog.entries(server.registryAccess()).stream().noneMatch(entry -> entry.form().equals(artistId)),
                        "Artist remains outside native origins even when modded origins are enabled");
            } finally { includeModded.set(previousModded); }
            level.getGameRules().getRule(key).set(true, server);
            join.invoke(null, new EntityJoinLevelEvent(wild, level));
            check(installed.contains(wild) && ChangedExtrasCompat.ownsWildAi(wild), "Unrelated wild creatures retain Extras smart AI");
            level.setBlock(marker, Blocks.CAMPFIRE.defaultBlockState(), 3);
            check(data.claim(level, owner.getUUID(), marker).equals("claimed"), "Create real camp for ownership transition");
            var camp = data.byOwner(owner.getUUID()).orElseThrow();
            check(data.add(camp, wild.getUUID(), "ExtrasWorker", camp.dimension, marker, PlayerOutpostSupplyType.ORANGE), "Register worker");
            data.role(camp, wild.getUUID(), PlayerOutpostData.Role.SUPPLY, marker);
            PlayerOutpostService.applyRole(wild, owner, PlayerOutpostData.Role.SUPPLY);
            check(ChangedExtrasCompat.shouldYieldSmartAi(wild), "Camp ownership yields even without NPC friendship data");
            // Change both ownership and the gamerule while the independent brain is installed.
            level.getGameRules().getRule(key).set(false, server);
            ChangedExtrasCompat.onLivingTick(new LivingEvent.LivingTickEvent(wild));
            var restore = ChangedExtrasCompat.class.getDeclaredMethod("restoreSynergyOwnedGoals", ChangedEntity.class);
            restore.setAccessible(true); restore.invoke(null, wild);
            check(!installed.contains(wild), "Existing smart brain removed even when its gamerule just turned off");
            for (Class<?> goal : new Class<?>[]{PlayerOutpostGoal.class, PlayerOutpostWorkGoal.class,
                    PlayerOutpostRestGoal.class, PlayerOutpostMealGoal.class, PlayerOutpostCrewGatherGoal.class}) {
                check(wild.goalSelector.getAvailableGoals().stream().filter(g -> goal.isInstance(g.getGoal())).count() == 1,
                        "Camp selector restored exactly once: " + goal.getSimpleName());
            }
            config.setBoolean(null, true);
            tick.invoke(null, new LivingEvent.LivingTickEvent(wild));
            check(wild.targetSelector.getAvailableGoals().stream().noneMatch(g -> g.getGoal().getClass().getSimpleName().equals("PlayerAggroTargetGoal")),
                    "Disabled smart AI cannot add always-attack-player goals to camp members");
            markedBody.getPersistentData().putBoolean("TrueTransfurControlled", true);
            markedBody.getPersistentData().putUUID("TrueTransfurOwner", owner.getUUID());
            check(TrueTransfurCompat.isInstinctBody(markedBody) && ChangedExtrasCompat.shouldYieldSmartAi(markedBody),
                    "Unresolved Instinct bodies yield before owner binding completes");
            for (boolean smart : new boolean[]{false, true}) {
                level.getGameRules().getRule(key).set(smart, server);
                join.invoke(null, new EntityJoinLevelEvent(markedBody, level));
                tick.invoke(null, new LivingEvent.LivingTickEvent(markedBody));
                check(!installed.contains(markedBody), "Instinct body never gains smart brain: " + smart);
                check(markedBody.targetSelector.getAvailableGoals().stream().noneMatch(g -> g.getGoal().getClass().getSimpleName().equals("PlayerAggroTargetGoal")),
                        "Instinct body never gains external player aggro: " + smart);
            }
            var tamingOwner = Class.forName("com.katt.changedextras.common.LatexCuddleHelper")
                    .getMethod("getTamingOwner", ChangedEntity.class);
            ((net.ltxprogrammer.changed.entity.beast.AbstractDarkLatexEntity)wild).tame(owner);
            check(tamingOwner.invoke(null, wild) == null, "Actual Extras cuddle lookup excludes working camp pets");
            var campEnabled = net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.playerOutposts;
            boolean previousCamp = campEnabled.get();
            try {
                campEnabled.set(false);
                check(tamingOwner.invoke(null, wild) == null, "Pausing camps preserves assignment instead of teleporting members to the owner's bed");
            } finally { campEnabled.set(previousCamp); }
            PlayerOutpostService.clearAssignment(wild);
            check(tamingOwner.invoke(null, wild) == owner, "Idle native pets outside camps retain Extras cuddling");
            wild.getPersistentData().putUUID("ChangedSynergyRestOwner", owner.getUUID());
            check(tamingOwner.invoke(null, wild) == null, "Manual shared rest remains under its own controller");
            wild.getPersistentData().remove("ChangedSynergyRestOwner");
            ((net.ltxprogrammer.changed.entity.beast.AbstractDarkLatexEntity)markedBody).tame(owner);
            check(tamingOwner.invoke(null, markedBody) == null, "Marked player body never becomes an Extras cuddler");
            System.out.println("CHANGED_EXTRAS_REGRESSION_PASS: " + checks + " actual-release checks");
        } finally {
            config.setBoolean(null, previousAggro);
            level.getGameRules().getRule(key).set(previous, server);
            data.disband(owner.getUUID()); PlayerOutpostService.clearAssignment(wild);
            installed.remove(wild); installed.remove(markedBody);
            artist.discard(); wild.discard(); markedBody.discard();
            level.setBlock(marker, oldBlock, 3);
        }
    }

    static void actualInstinctBody(ChangedEntity body) throws Exception {
        if (!ModList.get().isLoaded("changedextras")) return;
        var field = Class.forName("com.katt.changedextras.common.ai.LatexMobAIHandler").getDeclaredField("INSTALLED_MOBS");
        field.setAccessible(true);
        check(ChangedExtrasCompat.shouldYieldSmartAi(body) && !((Set<?>)field.get(null)).contains(body),
                "Actual TT-controlled body retains its native AI after entity join");
        System.out.println("CHANGED_EXTRAS_TT_BODY_PASS");
    }

    private static ChangedEntity creature(MinecraftServer server, String id) {
        var type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.parse(id));
        if (type == null) throw new AssertionError("Actual release did not register " + id);
        return (ChangedEntity)type.create(server.overworld());
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
