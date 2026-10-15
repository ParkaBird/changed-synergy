package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.HuntMemory;
import net.parkabird.changedsynergy.compat.FirearmCompat;

/** Uses the installed Superb Warfare release and its real shot event, not API stubs. */
final class FirearmSuppressionChecks {
    static void run(MinecraftServer server) throws Exception {
        if (!ModList.get().isLoaded("superbwarfare")) return;
        FirearmCompat.registerOptionalEvents();
        var level = server.overworld();
        var shooter = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "SilencerCheck"));
        shooter.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), shooter);
        var identity = new net.parkabird.changedsynergy.origin.NativeOriginData.Identity();
        identity.state = "human";
        net.parkabird.changedsynergy.origin.NativeOriginData.get(server).players.put(shooter.getUUID(), identity);
        shooter.setPos(level.getSharedSpawnPos().getX(), 100, level.getSharedSpawnPos().getZ());
        var near = ChangedTransfurVariants.LATEX_HYPNO_CAT.get().getEntityType().create(level);
        var far = ChangedTransfurVariants.LATEX_HYPNO_CAT.get().getEntityType().create(level);
        near.setPos(shooter.getX() + 12, shooter.getY(), shooter.getZ());
        far.setPos(shooter.getX() + 40, shooter.getY(), shooter.getZ());
        level.addFreshEntity(near); level.addFreshEntity(far);
        net.parkabird.changedsynergy.ai.LatexSocialMemory.markProvoked(near, shooter);
        net.parkabird.changedsynergy.ai.LatexSocialMemory.markProvoked(far, shooter);
        long time = level.getGameTime();
        double radius = ChangedSynergyConfig.COMMON.firearmGunshotRadius.get();
        try {
            ChangedSynergyConfig.COMMON.firearmGunshotRadius.set(48D);
            server.getWorldData().overworldData().setGameTime(time + 20);
            Class<?> gunItem = Class.forName("com.atsuishio.superbwarfare.item.gun.GunItem");
            var item = ForgeRegistries.ITEMS.getValues().stream().filter(gunItem::isInstance)
                    .filter(value -> ResourceLocation.parse("superbwarfare:ak_47").equals(ForgeRegistries.ITEMS.getKey(value)))
                    .findFirst().orElseGet(() -> ForgeRegistries.ITEMS.getValues().stream().filter(gunItem::isInstance).findFirst().orElseThrow());
            Class<?> dataType = Class.forName("com.atsuishio.superbwarfare.data.gun.GunData");
            Object data = dataType.getMethod("from", ItemStack.class).invoke(null, new ItemStack(item));
            Object attachment = dataType.getField("attachment").get(data);
            Class<?> attachmentType = Class.forName("com.atsuishio.superbwarfare.data.gun.value.AttachmentType");
            Object barrel = attachmentType.getField("BARREL").get(null);
            var set = attachment.getClass().getMethod("set", attachmentType, int.class);
            Class<?> parametersType = Class.forName("com.atsuishio.superbwarfare.data.gun.ShootParameters");
            Object parameters = parametersType.getConstructor(Entity.class, Entity.class, ServerLevel.class,
                    Vec3.class, Vec3.class, dataType, double.class, boolean.class, UUID.class, Vec3.class)
                    .newInstance(shooter, shooter, level, shooter.position(), new Vec3(1, 0, 0), data, 0D, false, null, null);
            var post = Class.forName("com.atsuishio.superbwarfare.api.event.ShootEvent$Post").getConstructor(parametersType);
            var read = FirearmCompat.class.getDeclaredMethod("readSuperbSoundProfile", Event.class);
            read.setAccessible(true);
            for (int value : new int[]{0, 1, 2, 0}) {
                set.invoke(attachment, barrel, value);
                Event shot = (Event)post.newInstance(parameters);
                Object profile = read.invoke(null, shot);
                check(profile != null, "Actual Superb API produces a sound profile for barrel " + value);
                var suppressed = profile.getClass().getDeclaredMethod("suppressed"); suppressed.setAccessible(true);
                var range = profile.getClass().getDeclaredMethod("radius"); range.setAccessible(true);
                check(suppressed.invoke(profile).equals(value == 2)
                                && Math.abs(((Number)range.invoke(profile)).doubleValue() - (value == 2 ? 28.8D : 48D)) < .001,
                        "Silencer reduces hearing; flash hider and removed silencer retain ordinary hearing");
                HuntMemory.clear(near); HuntMemory.clear(far); near.setTarget(null); far.setTarget(null);
                shooter.getPersistentData().remove("ChangedSynergyLastFirearmShot");
                MinecraftForge.EVENT_BUS.post(shot);
                check(HuntMemory.targets(near, shooter) && HuntMemory.targets(far, shooter) == (value != 2),
                        "Registered shot listener alerts nearby hunters but excludes distant hunters only when suppressed"
                                + "; near=" + HuntMemory.targets(near, shooter) + "; far=" + HuntMemory.targets(far, shooter)
                                + "; nearby=" + level.getEntitiesOfClass(net.ltxprogrammer.changed.entity.ChangedEntity.class,
                                        shooter.getBoundingBox().inflate(48)).size()
                                + "; eligible=" + net.parkabird.changedsynergy.event.HuntAIEvents.isEligibleHunter(near)
                                + "; hostile=" + net.parkabird.changedsynergy.event.HuntAIEvents.isHostilePlayerRelation(near, shooter)
                                + "; hunt=" + ChangedSynergyConfig.COMMON.enhancedHuntAi.get()
                                + "; rule=" + level.getGameRules().getBoolean(net.parkabird.changedsynergy.init.ChangedSynergyGameRules.NPC_AI));
            }
            ChangedSynergyConfig.COMMON.firearmGunshotRadius.set(0D);
            HuntMemory.clear(near); near.setTarget(null);
            shooter.getPersistentData().remove("ChangedSynergyLastFirearmShot");
            MinecraftForge.EVENT_BUS.post((Event)post.newInstance(parameters));
            check(!HuntMemory.targets(near, shooter), "Zero server gunshot radius still disables hearing");
            System.out.println("FIREARM_SUPPRESSION_REGRESSION_PASS: 13 checks; actual Superb Warfare");
        } finally {
            ChangedSynergyConfig.COMMON.firearmGunshotRadius.set(radius);
            server.getWorldData().overworldData().setGameTime(time);
            near.discard(); far.discard(); shooter.discard();
            net.parkabird.changedsynergy.origin.NativeOriginData.get(server).players.remove(shooter.getUUID());
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        System.out.println("FIREARM_SUPPRESSION_CHECK: " + message);
    }
}
