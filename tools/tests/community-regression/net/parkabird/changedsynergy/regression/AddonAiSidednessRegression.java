package net.parkabird.changedsynergy.regression;

import java.util.ArrayList;
import java.util.List;
import net.foxyas.changedaddon.entity.api.IAlphaAbleEntity;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ai.CreatureSocialProfile;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.compat.addon.ChangedAddonSocialEvents;
import net.parkabird.changedsynergy.event.HuntAIEvents;
import net.parkabird.changedsynergy.event.LatexSocialEvents;

/** Real optional-mod classes on a dedicated server; no AI/TPS benchmark. */
public final class AddonAiSidednessRegression {
    private static int checks;

    public static void run(MinecraftServer server) {
        checks = 0;
        var level = server.overworld();
        List<ChangedEntity> creatures = new ArrayList<>();
        LivingEntity target = new Zombie(EntityType.ZOMBIE, level);
        try {
            for (String path : List.of("experiment_009", "experiment_009_boss",
                    "experiment_10", "experiment_10_boss")) {
                ChangedEntity creature = create(server, "changed_addon:" + path);
                creatures.add(creature);
                boolean boss = path.endsWith("_boss");
                check(CreatureSocialProfile.isPermanentlyExcluded(creature) == boss,
                        path + " has the expected permanent exclusion");
                check(CreatureSocialProfile.allowsSynergySystems(creature) != boss,
                        path + " has the expected social-system permission");
                checkClassAndGoals(creature, target, boss);
            }

            ChangedEntity alpha = create(server, "changed:dark_latex_wolf_male");
            creatures.add(alpha);
            check(alpha instanceof IAlphaAbleEntity,
                    "Native Changed wolf receives Addon's Alpha interface");
            IAlphaAbleEntity alphaState = (IAlphaAbleEntity) alpha;
            alphaState.setAlphaScale(0.75F);
            alphaState.setAlpha(true);
            check(alphaState.isAlpha(), "Representative Alpha uses the real native state");
            checkClassAndGoals(alpha, target, false);
            System.out.println("ADDON_AI_SIDEDNESS_REGRESSION_PASS: " + checks + " checks");
        } finally {
            // These entities never enter the world or tick native attacks.
            for (ChangedEntity creature : creatures) {
                creature.setTarget(null);
                creature.getNavigation().stop();
                creature.discard();
            }
            target.discard();
        }
    }

    private static ChangedEntity create(MinecraftServer server, String id) {
        var type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.parse(id));
        check(type != null, id + " is registered in the installed Addon version");
        var entity = type.create(server.overworld());
        check(entity instanceof ChangedEntity, id + " constructs a real Changed creature");
        var creature = (ChangedEntity) entity;
        creature.setNoAi(true);
        var spawn = server.overworld().getSharedSpawnPos();
        creature.setPos(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        return creature;
    }

    private static void checkClassAndGoals(
            ChangedEntity creature, LivingEntity target, boolean excluded) {
        String name = ForgeRegistries.ENTITY_TYPES.getKey(creature.getType()).toString();
        var nativeGoals = goals(creature);
        boolean nativeMenu = LatexSocialMemory.usesNativePetMenu(creature);
        for (int i = 0; i < 128; i++) {
            check(LatexSocialMemory.usesNativePetMenu(creature) == nativeMenu,
                    name + " cached native-menu classification is stable");
            // setTarget runs the exact Forge event path in the supplied server crash.
            creature.setTarget((i & 1) == 0 ? target : null);
        }
        install(creature);
        var installedGoals = goals(creature);
        if (excluded) {
            check(installedGoals.equals(nativeGoals),
                    name + " retains its native goal instances without Synergy additions");
            check(installedGoals.stream().noneMatch(goal ->
                    goal.getClass().getName().startsWith("net.parkabird.changedsynergy.")),
                    name + " has no Synergy goal");
        }
        for (int i = 0; i < 32; i++) install(creature);
        check(goals(creature).equals(installedGoals),
                name + " repeated reconciliation leaves goal instances and count stable");
    }

    private static void install(ChangedEntity creature) {
        HuntAIEvents.ensureHuntGoals(creature);
        LatexSocialEvents.ensureSocialGoals(creature);
        ChangedAddonSocialEvents.onEntityJoin(
                new EntityJoinLevelEvent(creature, creature.level()));
    }

    private static List<Goal> goals(ChangedEntity creature) {
        List<Goal> result = new ArrayList<>();
        creature.goalSelector.getAvailableGoals().forEach(wrapped -> result.add(wrapped.getGoal()));
        creature.targetSelector.getAvailableGoals().forEach(wrapped -> result.add(wrapped.getGoal()));
        return result;
    }

    private static void check(boolean passed, String description) {
        if (!passed) throw new AssertionError(description);
        checks++;
    }
}
