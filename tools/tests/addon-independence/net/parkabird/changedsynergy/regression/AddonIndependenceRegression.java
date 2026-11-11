package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.beast.AbstractDarkLatexEntity;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import static net.parkabird.changedsynergy.ai.BondedPetSettings.*;

/** Exercises common holds and the actual native/optional settings backends. */
@Mod.EventBusSubscriber(modid = "changed_synergy", value = Dist.DEDICATED_SERVER)
public final class AddonIndependenceRegression {
    private static int checks;

    @SubscribeEvent public static void run(ServerStartedEvent event) {
        var server = event.getServer();
        try {
            ChangedSynergyConfig.SPEC.setConfig(com.electronwill.nightconfig.core.CommentedConfig.inMemory());
            Changed.config.server.isGrabEnabled.set(true);
            ChangedSynergyConfig.COMMON.companionMonsterAssistOnly.set(false);
            var level = server.overworld();
            var time = (ServerLevelData)level.getLevelData();
            long now = level.getGameTime();
            var player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "CoreHolds"));
            player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player);
            player.setPos(0, 180, 0); player.setOnGround(true);
            @SuppressWarnings("unchecked") var players = (Map<UUID, ServerPlayer>)ObfuscationReflectionHelper.findField(
                    net.minecraft.server.players.PlayerList.class, "f_11197_").get(server.getPlayerList());
            players.put(player.getUUID(), player);
            level.addNewPlayer(player);
            var renderBody = ChangedEntities.PURE_WHITE_LATEX_WOLF.get().create(level);
            renderBody.setUnderlyingPlayer(player);
            var beforeBody = renderBody.getPersistentData().copy();
            CreatureGrabService.onStartTracking(new net.minecraftforge.event.entity.player.PlayerEvent.StartTracking(player, renderBody));
            check(beforeBody.equals(renderBody.getPersistentData()), "tracking a player render body never creates NPC social memory");
            for (var type : java.util.List.of(ChangedEntities.DARK_LATEX_WOLF_MALE.get(), ChangedEntities.PURE_WHITE_LATEX_WOLF.get())) {
                ChangedEntity mob = type.create(level);
                mob.setPos(1, 180, 0); mob.setNoAi(true); level.addFreshEntity(mob);
                LatexSocialMemory.addBond(mob, player);
                policies(mob, player);
                var grab = CreatureGrabService.ability(mob);
                if (ModList.get().isLoaded("changed_addon")) optionalAlias(mob, player, grab);
                grab.getController().forceCooldown(0);
                check(CreatureGrabService.tryStartSocialHug(mob, player, 100), "common friendly hug starts: " + type);
                check(CreatureGrabService.isGrabberBusy(mob) && CreatureGrabService.isSafeGrab(grab), "common hold is busy and safe");
                float health = player.getHealth();
                grab.attackDown = grab.useDown = true;
                for (int i = 0; i < 12; i++) { mob.tickCount++; tick(mob); }
                check(!grab.attackDown && !grab.useDown && !grab.suited && !grab.grabbedHasControl, "native inputs cannot damage or suit a social passenger");
                check(player.getHealth() == health && !ProcessTransfur.isPlayerTransfurred(player), "social hold neither hurts nor transfurs");
                int unpressed = grab.ticksUnpressed;
                grab.tickIdle(); tick(mob);
                check(grab.ticksUnpressed == unpressed, "same-tick native/optional updates cannot double-tick escape");
                check(grab.currentEscapeKey != null, "friendly hold retains escape QTE");
                time.setGameTime(now + 101); mob.tickCount++;
                tick(mob);
                check(grab.grabbedEntity == null && !LatexSocialMemory.hasFriendlySocialHug(mob), "timeout releases hold and markers");
                check(grab.getController().isCoolingDown(), "release applies native cooldown");
                check(!CreatureGrabService.tryStartSocialHug(mob, player, 100), "cooldown prevents immediate regrab");
                time.setGameTime(now); player.setPos(0, 180, 0); mob.setPos(1, 180, 0);
                grab.getController().forceCooldown(0);
                check(CreatureGrabService.tryStartSocialHug(mob, player, 100), "hug can restart after cooldown");
                grab.releaseEntity(false); mob.tickCount++; tick(mob);
                check(!LatexSocialMemory.hasFriendlySocialHug(mob) && !player.noPhysics, "early escape cleans up without lingering physics");
                player.setPos(0, 180, 0); mob.setPos(1, 180, 0); player.setOnGround(true);
                var form = mob instanceof AbstractDarkLatexEntity ? ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get()
                        : ChangedTransfurVariants.PURE_WHITE_LATEX_WOLF.get();
                ProcessTransfur.setPlayerTransfurVariant(player, form, null, 1F, false);
                check(InvoluntaryTransfurNegotiation.beginBondedReversal(mob, player), "bond reversal begins using common hold");
                check(InvoluntaryTransfurNegotiation.hasReleaseHold(mob) && grab.grabbedEntity == player
                        && ProcessTransfur.isPlayerTransfurred(player), "reversal retains its delayed presentation without Addon");
                mob.tickCount++; tick(mob);
                check(ProcessTransfur.isPlayerTransfurred(player), "reversal does not happen before delay");
                time.setGameTime(now + 19); mob.tickCount++; tick(mob);
                check(!ProcessTransfur.isPlayerTransfurred(player), "reversal happens after its real delay");
                time.setGameTime(now + 47); mob.tickCount++; tick(mob);
                check(grab.grabbedEntity == null && !InvoluntaryTransfurNegotiation.hasReleaseHold(mob), "reversal completes and frees both participants");
                time.setGameTime(now); mob.discard();
            }
            player.discard(); players.remove(player.getUUID());
            System.out.println("ADDON_INDEPENDENCE_REGRESSION_PASS: " + checks + " checks; addon=" + ModList.get().isLoaded("changed_addon"));
        } catch (Throwable failure) {
            System.err.println("ADDON_INDEPENDENCE_REGRESSION_FAIL"); failure.printStackTrace();
        } finally { server.halt(false); }
    }

    private static void policies(ChangedEntity mob, ServerPlayer owner) throws Exception {
        LivingEntity target = EntityType.ZOMBIE.create(mob.level());
        set(mob, "TargetType", "MONSTERS", TARGET_MONSTERS);
        set(mob, "AttackCondition", "NEVER", CONDITION_NEVER);
        check(!allowsDefense(mob, owner, target), "NEVER excludes defense for every backend");
        set(mob, "AttackCondition", "ALWAYS", CONDITION_ALWAYS);
        check(allowsDefense(mob, owner, target), "ALWAYS includes configured hostile monsters");
        set(mob, "AttackCondition", "OWNER_IS_HOSTILE", CONDITION_OWNER_HOSTILE);
        check(!allowsDefense(mob, owner, target), "owner-hostile excludes unprovoked monster");
        owner.setLastHurtByMob(target);
        check(allowsDefense(mob, owner, target), "owner-hostile includes a real recent attacker");
        owner.setLastHurtByMob(null);
        set(mob, "AttackType", "ALWAYS_KILL", ATTACK_ALWAYS_KILL);
        check(suppressTransfurAttack(mob), "physical-only setting is common policy");
        set(mob, "AttackType", "TRY_TRANSFUR", ATTACK_TRY_TRANSFUR);
        check(!suppressTransfurAttack(mob), "transfur setting survives the common policy");
        set(mob, "Favor", "FISHING", FAVOR_FISHING);
        check(favor(mob) == FAVOR_FISHING && hasWorkFavor(mob), "fishing has stable common meaning");
        if (!usesFallbackBackend(mob)) {
            set(mob, "Favor", "SUIT_OWNER", FAVOR_SUIT_OWNER);
            check(favor(mob) == FAVOR_SUIT_OWNER && !hasWorkFavor(mob), "suit favor is never mistaken for work");
        }
        int[] before = stateFor(mob);
        var copy = (ChangedEntity)mob.getType().create(mob.level());
        copy.load(mob.saveWithoutId(new CompoundTag()));
        check(java.util.Arrays.equals(before, stateFor(copy)), "native backend settings survive save/reload without migration");
        clearFavor(mob); copy.discard(); target.discard();
    }

    private static void optionalAlias(ChangedEntity mob, ServerPlayer player,
            net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance nativeGrab) throws Exception {
        var original = ChangedAddonCompat.grabAbility(mob);
        var detached = net.ltxprogrammer.changed.init.ChangedAbilities.GRAB_ENTITY_ABILITY.get()
                .makeInstance(net.ltxprogrammer.changed.ability.IAbstractChangedEntity.forEntity(mob));
        var target = EntityType.ZOMBIE.create(mob.level());
        detached.grabbedEntity = target;
        for (Class<?> type = mob.getClass(); type != null; type = type.getSuperclass()) {
            for (var field : type.getDeclaredFields()) {
                if (field.getType() != net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance.class) continue;
                field.setAccessible(true);
                Object saved = field.get(mob);
                if (saved != original) continue;
                try {
                    field.set(mob, detached);
                    if (ChangedAddonCompat.grabAbility(mob) != detached || BondedSuitService.ability(mob) != nativeGrab) continue;
                    check(CreatureGrabService.isGrabberBusy(mob) && CreatureGrabService.ability(mob) == detached,
                            "occupied optional instance is preserved while registered instance is idle");
                    check(!CreatureGrabService.tryStartSocialHug(mob, player, 100), "private active hold cannot be overwritten by a social hug");
                    return;
                } finally { field.set(mob, saved); }
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void set(ChangedEntity mob, String setting, String enumName, int value) throws Exception {
        if (usesFallbackBackend(mob)) {
            initialize(mob);
            mob.getPersistentData().getCompound("ChangedSynergyBondedPetSettings").putInt(setting, value);
        } else {
            Method setter = java.util.Arrays.stream(mob.getClass().getMethods())
                    .filter(m -> m.getName().equals("set" + setting) && m.getParameterCount() == 1
                            && m.getParameterTypes()[0].isEnum()).findFirst().orElseThrow();
            setter.invoke(mob, Enum.valueOf((Class)setter.getParameterTypes()[0], enumName));
        }
    }
    private static void tick(ChangedEntity mob) { CreatureGrabService.onLivingTick(new LivingEvent.LivingTickEvent(mob)); }
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
}
