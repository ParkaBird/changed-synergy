package net.parkabird.changedsynergy.regression;

import java.util.concurrent.CompletableFuture;
import net.ltxprogrammer.changed.ability.GrabEntityAbility;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.network.FriendlySocialHugState;

/** Real native grab, social-marker and escape packets through an integrated client connection. */
@Mod.EventBusSubscriber(modid = "changed_synergy", value = Dist.CLIENT)
public final class SocialHoldClientRegression {
    private static int stage, scenario, wait, petId, checks;
    private static CompletableFuture<Void> task;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("changed_synergy.verifySocialHolds") || stage == 9 || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        try {
            org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
            if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                check(mc.gameDirectory.getName().startsWith("author-feedback-client-"), "private client only");
                mc.options.pauseOnLostFocus = false; mc.options.renderDistance().set(2); mc.options.simulationDistance().set(2);
                var rules = new GameRules(); rules.getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(false, null);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                mc.createWorldOpenFlows().createFreshLevel("social-holds", new LevelSettings("social-holds", GameType.SURVIVAL,
                        false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT), new WorldOptions(73451, false, false),
                        r -> r.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions());
                stage = 1;
            }
            if (mc.player == null || mc.level == null || mc.player.tickCount < 20 || mc.getOverlay() != null) return;
            if (stage == 1) {
                mc.setScreen(null);
                task = CompletableFuture.runAsync(() -> {
                    var owner = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    var type = scenario == 0 ? ChangedEntities.DARK_LATEX_WOLF_MALE.get() : ChangedEntities.PURE_WHITE_LATEX_WOLF.get();
                    var pet = type.create(owner.serverLevel());
                    pet.moveTo(owner.position()); pet.setNoAi(true); owner.serverLevel().addFreshEntity(pet);
                    LatexSocialMemory.addBond(pet, owner); petId = pet.getId();
                    CreatureGrabService.ability(pet).getController().forceCooldown(0);
                    check(CreatureGrabService.tryStartSocialHug(pet, owner, 100), "server starts harmless social hold");
                }, mc.getSingleplayerServer());
                stage = 2; wait = 0;
            }
            if (stage == 2 && task.isDone() && ++wait >= 20) {
                task.join();
                var pet = (ChangedEntity)mc.level.getEntity(petId);
                var grab = CreatureGrabService.ability(pet);
                check(grab != null && grab.grabbedEntity == mc.player, "native hold reached client");
                check(FriendlySocialHugState.isActive(petId, mc.player.getId()), "social marker reached client");
                check(CreatureGrabService.isSafeGrab(grab) && !grab.suited && !grab.grabbedHasControl,
                        "client retains friendly arm hold instead of inferring an owner suit");
                check(grab.currentEscapeKey != null, "client receives real escape QTE");
                task = CompletableFuture.runAsync(() -> {
                    var owner = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    var serverPet = (ChangedEntity)owner.serverLevel().getEntity(petId);
                    var hold = CreatureGrabService.ability(serverPet);
                    hold.attackDown = hold.useDown = true;
                    check(owner.getHealth() == owner.getMaxHealth() && !ProcessTransfur.isPlayerTransfurred(owner), "server hold is harmless");
                    if (scenario == 1) hold.releaseEntity(false);
                }, mc.getSingleplayerServer());
                stage = 3; wait = 0;
            }
            if (stage == 3 && task.isDone() && ++wait >= (scenario == 0 ? 100 : 10)) {
                task.join();
                var pet = (ChangedEntity)mc.level.getEntity(petId);
                check(CreatureGrabService.ability(pet).grabbedEntity == null && GrabEntityAbility.getGrabber(mc.player) == null,
                        "native release clears both client grab references");
                check(!FriendlySocialHugState.isActive(petId, mc.player.getId()) && !mc.player.noPhysics,
                        "release clears social marker and physics");
                check(mc.player.getHealth() == mc.player.getMaxHealth() && !ProcessTransfur.isPlayerTransfurred(mc.player),
                        "client survives both timeout and early release unchanged");
                if (++scenario == 2) {
                    stage = 9; System.out.println("SOCIAL_HOLD_CLIENT_PASS: " + checks + " native holds, QTE, safe inputs and release checks"); mc.stop();
                } else { stage = 1; wait = 0; }
            }
        } catch (Throwable failure) {
            stage = 9; System.err.println("SOCIAL_HOLD_CLIENT_FAIL"); failure.printStackTrace(); mc.stop();
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
}
