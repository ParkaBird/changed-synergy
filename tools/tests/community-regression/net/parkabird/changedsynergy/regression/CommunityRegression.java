package net.parkabird.changedsynergy.regression;

import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;

@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class CommunityRegression {
    @SubscribeEvent public static void run(ServerStartedEvent event) {
        boolean loadRunning = false;
        try {
            FacilityTemplateRegression.run();
            boolean success = check(() -> HypnosisPlayerRegression.run(event.getServer()));
            success &= check(() -> RescueWrapRegression.run(event.getServer()));
            success &= check(() -> PlayerVisualMechanicsRegression.run(event.getServer()));
            if (net.minecraftforge.fml.ModList.get().isLoaded("changed_addon")) {
                success &= check(() -> AddonAiSidednessRegression.run(event.getServer()));
                success &= check(() -> AddonThunderScanRegression.run(event.getServer()));
            }
            success &= check(() -> PeacefulCreatureRegression.run(event.getServer()));
            success &= check(() -> NativeBondInvitationRegression.run(event.getServer()));
            success &= check(() -> PetCommunityIsolationRegression.run(event.getServer()));
            success &= check(() -> WildOutpostRegression.run(event.getServer()));
            success &= check(() -> WildAiPerformanceRegression.run(event.getServer()));
            success &= check(() -> WildOutpostLifecycleRegression.run(event.getServer()));
            success &= check(() -> VehicleProtectionRegression.run(event.getServer()));
            if (!success) throw new AssertionError("At least one community regression failed");
            System.out.println("COMMUNITY_REGRESSION_PASS");
            if (Boolean.getBoolean("changed_synergy.serverLoadCheck")) {
                ServerLoadRegression.start(event.getServer());
                loadRunning = true;
            }
        } catch (Throwable error) {
            System.err.println("COMMUNITY_REGRESSION_FAIL");
            error.printStackTrace();
        } finally {
            if (!loadRunning) event.getServer().halt(false);
        }
    }
    private interface Check { void run() throws Exception; }
    private static boolean check(Check action) {
        try { action.run(); return true; }
        catch (Throwable failure) { failure.printStackTrace(); return false; }
    }
}
