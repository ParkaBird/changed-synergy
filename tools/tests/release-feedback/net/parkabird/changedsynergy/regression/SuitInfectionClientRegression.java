package net.parkabird.changedsynergy.regression;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.ltxprogrammer.changed.entity.PlayerDataExtension;
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
import net.parkabird.changedsynergy.client.FriendlySuitClientState;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

@Mod.EventBusSubscriber(modid = "changed_synergy", value = Dist.CLIENT)
public final class SuitInfectionClientRegression {
    private static int stage, scenario, wait, petId, checks;
    private static float expected;
    private static CompletableFuture<Void> task;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("changed_synergy.verifySuitInfection") || stage == 9 || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        try {
            org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
            if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                check(mc.gameDirectory.getName().startsWith("author-feedback-client-"), "private client only");
                mc.options.pauseOnLostFocus = false; mc.options.renderDistance().set(2); mc.options.simulationDistance().set(2);
                var rules = new GameRules(); rules.getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(false, null);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                mc.createWorldOpenFlows().createFreshLevel("suit-infection", new LevelSettings("suit-infection", GameType.SURVIVAL,
                        false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT), new WorldOptions(73451, false, false),
                        r -> r.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions());
                stage = 1;
            }
            if (mc.player == null || mc.level == null || mc.player.tickCount < 20 || mc.getOverlay() != null) return;
            if (stage == 1) {
                mc.setScreen(null);
                task = CompletableFuture.runAsync(() -> {
                    var owner = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    ProcessTransfur.removePlayerTransfurVariant(owner);
                    var pet = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(owner.serverLevel());
                    pet.moveTo(owner.position()); pet.setNoAi(true); owner.serverLevel().addFreshEntity(pet);
                    LatexSocialMemory.addBond(pet, owner); petId = pet.getId();
                    if (scenario == 2) {
                        ProcessTransfur.setPlayerTransfurVariant(owner, pet.getSelfVariant(), null, 1, false);
                        check(BondedSuitService.beginRevivalSuit(pet, owner, UUID.randomUUID()), "real revival suit begins");
                    } else check(BondedSuitService.suitOwner(pet, owner, BondedSuitService.SuitReason.MANUAL), "normal suit begins");
                    expected = (float)ProcessTransfur.getEntityTransfurTolerance(owner) * (scenario == 1 ? .25F : scenario == 3 ? .37F : 0);
                    ProcessTransfur.setPlayerTransfurProgress(owner, expected);
                }, mc.getSingleplayerServer());
                stage = 2; wait = 0;
            }
            if (stage == 2 && task.isDone()) {
                task.join();
                if (!FriendlySuitClientState.isOwnerSuited(mc.player.getId()) || ProcessTransfur.getPlayerTransfurVariant(mc.player) == null || ++wait < 5) {
                    if (wait > 200) throw new AssertionError("suit client synchronization timeout");
                    return;
                }
                // Reproduce the stale 100% client meter while the server already has the real value.
                ((PlayerDataExtension)mc.player).setTransfurProgress((float)ProcessTransfur.getEntityTransfurTolerance(mc.player));
                task = CompletableFuture.runAsync(() -> {
                    var owner = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    var pet = (net.ltxprogrammer.changed.entity.ChangedEntity)owner.serverLevel().getEntity(petId);
                    owner.setOnGround(true);
                    if (scenario == 3) BondedSuitService.ability(pet).releaseEntity(false);
                    else check(BondedSuitService.releaseOwner(pet, owner, BondedSuitService.ReleaseReason.MANUAL), "public un-suit succeeds");
                }, mc.getSingleplayerServer());
                stage = 3; wait = 0;
            }
            if (stage == 3 && task.isDone() && ++wait >= 8) {
                task.join();
                check(ProcessTransfur.getPlayerTransfurVariant(mc.player) == null, "human restored without temporary suit");
                float progress = ProcessTransfur.getPlayerTransfurProgress(mc.player);
                check(Math.abs(progress - expected) < .021F, "meter matches server after release without another infection; scenario=" + scenario + " expected=" + expected + " actual=" + progress);
                System.out.println("SUIT_INFECTION_CASE_PASS: " + scenario + " progress=" + progress);
                if (++scenario == 4) {
                    stage = 9; System.out.println("SUIT_INFECTION_CLIENT_PASS: " + checks + " normal/revival/native release and preserved infection checks"); mc.stop();
                } else { stage = 1; wait = 0; }
            }
        } catch (Throwable failure) {
            stage = 9; System.err.println("SUIT_INFECTION_CLIENT_FAIL"); failure.printStackTrace(); mc.stop();
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
}
