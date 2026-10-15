package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.ai.DarkLatexAttackCondition;
import net.ltxprogrammer.changed.entity.ai.DarkLatexTargetType;
import net.ltxprogrammer.changed.entity.beast.AbstractDarkLatexEntity;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import net.parkabird.changedsynergy.event.HuntAIEvents;
import net.parkabird.changedsynergy.event.LatexSocialEvents;
import net.parkabird.changedsynergy.event.NpcDispositionEvents;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.origin.NativeOriginData;

/** Production entity/target hooks, optional Addon coats and actual TT 2.0 bodies. */
final class PeacefulCompetitionChecks {
    private static int checks;

    @SuppressWarnings("unchecked")
    static void run(MinecraftServer server) throws Exception {
        var level = server.overworld();
        var field = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(
                net.minecraft.server.players.PlayerList.class, "f_11197_");
        var players = (Map<UUID, ServerPlayer>)field.get(server.getPlayerList());
        var player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "PeacefulGuest"));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player);
        players.put(player.getUUID(), player); level.addNewPlayer(player);
        player.setPos(level.getSharedSpawnPos().getX() + .5, 90, level.getSharedSpawnPos().getZ() + .5);
        player.tickCount = 1000;
        var creatures = new ArrayList<ChangedEntity>();
        long time = level.getGameTime();
        int grace = ChangedSynergyConfig.COMMON.postTransfurTruceSeconds.get();
        boolean ai = level.getGameRules().getBoolean(ChangedSynergyGameRules.NPC_AI);
        try {
            ChangedSynergyConfig.COMMON.postTransfurTruceSeconds.set(0);
            var dark = spawn(player, "dark_latex_wolf_male", creatures);
            var white = spawn(player, "pure_white_latex_wolf", creatures);
            check(dark.getLatexType().isHostileTo(white.getLatexType()),
                    "The upstream latex-material table is unchanged");
            for (String darkType : List.of("dark_latex_wolf_male", "dark_latex_wolf_female", "dark_latex_wolf_pup",
                    "phage_latex_wolf_male", "phage_latex_wolf_female", "dark_dragon", "crystal_wolf", "crystal_wolf_horned")) {
                var member = spawn(player, darkType, creatures);
                for (String whiteType : List.of("pure_white_latex_wolf", "pure_white_latex_wolf_pup",
                        "latex_mutant_bloodcell_wolf", "pure_white_latex_cerberus")) {
                    var visitor = spawn(player, whiteType, creatures);
                    check(LatexCreatureCombatRules.arePeacefulCompetitors(member, visitor)
                                    && !LatexCreatureCombatRules.areRivals(member, visitor)
                                    && !LatexCreatureCombatRules.areCompatriots(member, visitor),
                            darkType + " / " + whiteType + " compete without becoming kin or enemies");
                    check(LatexCreatureCombatRules.mustRejectTarget(member, visitor)
                                    && LatexCreatureCombatRules.mustRejectTarget(visitor, member)
                                    && !member.canAttack(visitor) && !visitor.canAttack(member),
                            "Native acquisition is peaceful in both directions");
                    member.setTarget(visitor); visitor.setTarget(member);
                    check(member.getTarget() == null && visitor.getTarget() == null,
                            "Changed's synced target setter cannot restore racial combat");
                    float health = visitor.getHealth();
                    member.doHurtTarget(visitor);
                    check(!member.isRemoved() && !visitor.isRemoved() && visitor.getHealth() == health,
                            "A stale native swipe neither assimilates nor damages a peaceful competitor");
                }
            }
            level.getGameRules().getRule(ChangedSynergyGameRules.NPC_AI).set(false, server);
            check(!white.canAttack(dark) && !dark.canAttack(white), "Peace survives disabled enhanced AI");
            level.getGameRules().getRule(ChangedSynergyGameRules.NPC_AI).set(true, server);
            var adapted = spawn(player, "white_latex_wolf_male", creatures);
            var adaptation = new net.minecraft.nbt.CompoundTag(); adaptation.putBoolean("Eligible", true);
            adapted.getPersistentData().put(PureWhiteWolfAdaptation.DATA_KEY, adaptation);
            check(HunterFaction.of(adapted) == HunterFaction.WHITE
                            && LatexCreatureCombatRules.arePeacefulCompetitors(adapted, dark)
                            && LatexCreatureCombatRules.mustRejectTarget(adapted, dark),
                    "Pure-white adaptation retains its faction and peaceful competition");
            if (ForgeRegistries.ENTITY_TYPES.containsKey(ResourceLocation.parse("changedextras:latex_artist"))) {
                var artist = spawn(player, "changedextras:latex_artist", creatures);
                check(!LatexSocialMemory.isSocialLatex(artist)
                                && !LatexCreatureCombatRules.arePeacefulCompetitors(artist, dark),
                        "Excluded Artist behavior is not enrolled in faction peace");
            }

            ProcessTransfur.setPlayerTransfurVariant(player, ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get(), null, 1, false);
            server.getWorldData().overworldData().setGameTime(time + 2400);
            FactionReputation.set(white, player, 0);
            check(HunterRelation.between(white, player) == HunterRelation.OUTSIDER
                            && LatexSocialRelation.between(white, player) == LatexSocialRelation.OUTSIDER
                            && LatexSocialMemory.shouldRemainNeutral(white, player)
                            && !NpcDispositionEvents.hasHostileDisposition(white, player)
                            && !HuntAIEvents.canReacquire(white, player, true)
                            && !HuntAIEvents.isHostilePlayerRelation(white, player),
                    "An unknown Dark player is peaceful across disposition, pursuit and noise");
            check(!CreaturePersonality.hasEstablishedRelationship(white, player)
                            && CreaturePersonality.familiarity(white, player) == 0,
                    "Peace does not name the NPC or grant affection and friendship");
            var hit = new LivingAttackEvent(player, player.damageSources().mobAttack(white), 4);
            LatexSocialEvents.onSocialCreatureAttacksPlayer(hit);
            check(hit.isCanceled(), "A residual racial swipe cannot hurt a peaceful player");
            var form = ProcessTransfur.getPlayerTransfurVariantSafe(player).orElseThrow().getParent();
            white.doHurtTarget(player);
            check(ProcessTransfur.getPlayerTransfurVariantSafe(player).orElseThrow().getParent() == form
                            && !white.isRemoved(),
                    "Direct native assimilation cannot bypass peaceful player disposition");
            LatexSocialMemory.markProvoked(white, player);
            check(!LatexSocialMemory.shouldRemainNeutral(white, player)
                            && NpcDispositionEvents.hasHostileDisposition(white, player) && white.canAttack(player),
                    "Actual player provocation still permits retaliation");
            LatexSocialMemory.clearProvocation(white, player);
            FactionReputation.set(white, player, -60);
            check(!LatexSocialMemory.shouldRemainNeutral(white, player)
                            && NpcDispositionEvents.hasHostileDisposition(white, player),
                    "Genuinely hostile reputation is not erased");
            FactionReputation.set(white, player, 0);
            preserveOtherFight(dark, white, player);

            ProcessTransfur.setPlayerTransfurVariant(player, ChangedTransfurVariants.PURE_WHITE_LATEX_WOLF.get(), null, 1, false);
            for (String type : List.of("dark_latex_wolf_male", "dark_dragon", "crystal_wolf")) {
                var observer = spawn(player, type, creatures);
                FactionReputation.set(observer, player, 0);
                check(LatexSocialMemory.shouldRemainNeutral(observer, player)
                                && !NpcDispositionEvents.hasHostileDisposition(observer, player),
                        "Organic and non-organic Dark observers respect peaceful White players: " + type);
            }
            white.setLastHurtByMob(dark);
            check(!LatexCreatureCombatRules.mustRejectTarget(white, dark), "Recent direct injury permits NPC self-defense");
            white.setTarget(dark); dark.setLastHurtByMob(white); dark.setTarget(white);
            float limited = LatexCreatureCombatRules.limitNonRivalDamage(white, dark, 1000);
            check(limited <= white.getHealth() - white.getMaxHealth() * .2F
                            && white.getTarget() == null && dark.getTarget() == null,
                    "Incidental competitor combat ends at the existing non-rival safety floor");
            companionPolicy(player, white, creatures);
            disguise(player, white);
            if (TrueTransfurCompat.supportsSeparateInstinct()) instinct(player, white);
            System.out.println("PEACEFUL_COMPETITION_REGRESSION_PASS: " + checks + " checks");
        } finally {
            TrueTransfurCompat.ensurePlayerControl(player);
            for (var creature : creatures) creature.discard();
            NativeOriginData.get(server).players.remove(player.getUUID());
            players.remove(player.getUUID()); player.discard();
            ChangedSynergyConfig.COMMON.postTransfurTruceSeconds.set(grace);
            level.getGameRules().getRule(ChangedSynergyGameRules.NPC_AI).set(ai, server);
            server.getWorldData().overworldData().setGameTime(time);
        }
    }

    private static void preserveOtherFight(ChangedEntity dark, ChangedEntity white, ServerPlayer player) {
        LatexSocialMemory.markProvoked(dark, player);
        dark.setTarget(player); dark.setLastHurtByMob(player); dark.setAggressive(true); dark.setSprinting(true);
        HuntMemory.seeTarget(dark, player);
        var path = new Path(List.of(new Node(dark.getBlockX(), 90, dark.getBlockZ()),
                new Node(dark.getBlockX() + 3, 90, dark.getBlockZ())), dark.blockPosition().offset(3, 0, 0), true);
        dark.getNavigation().moveTo(path, 1);
        check(!dark.canAttack(white) && dark.getTarget() == player && dark.isAggressive() && dark.isSprinting()
                        && HuntMemory.targets(dark, player) && dark.getNavigation().getPath() == path,
                "Rejecting a peaceful candidate preserves an unrelated fight and its navigation");
        LatexSocialMemory.clearProvocation(dark, player); LatexSocialEvents.calmTowards(dark, player);
    }

    private static void companionPolicy(ServerPlayer owner, ChangedEntity white, List<ChangedEntity> creatures) {
        var pet = (AbstractDarkLatexEntity)spawn(owner, "dark_latex_wolf_male", creatures);
        LatexSocialMemory.addBond(pet, owner);
        pet.setAttackCondition(DarkLatexAttackCondition.ALWAYS);
        pet.setTargetType(DarkLatexTargetType.TRANSFURABLE_ENTITIES);
        check(!BondedOwnerDefenseGoal.allowsConfiguredDefense(pet, owner, white)
                        && !BondedPetSettings.matchesTargetType(pet, owner, white, BondedPetSettings.TARGET_MONSTERS),
                "Always attack does not turn peaceful competitors into monsters");
        owner.setLastHurtByMob(white);
        check(BondedOwnerDefenseGoal.allowsConfiguredDefense(pet, owner, white), "Real owner protection remains available");
        pet.setAttackCondition(DarkLatexAttackCondition.NEVER);
        check(!BondedOwnerDefenseGoal.allowsConfiguredDefense(pet, owner, white), "Never attack still takes priority");
        owner.setLastHurtByMob(null); LatexSocialMemory.unregisterBond(pet, owner);
    }

    private static void disguise(ServerPlayer player, ChangedEntity white) {
        if (!ForgeRegistries.ITEMS.containsKey(ChangedAddonCompat.DARK_LATEX_COAT_CAP)
                || !ForgeRegistries.ITEMS.containsKey(ChangedAddonCompat.DARK_LATEX_COAT)) return;
        ProcessTransfur.removePlayerTransfurVariant(player);
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ForgeRegistries.ITEMS.getValue(ChangedAddonCompat.DARK_LATEX_COAT_CAP)));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ForgeRegistries.ITEMS.getValue(ChangedAddonCompat.DARK_LATEX_COAT)));
        check(DarkLatexDisguise.appearsAsDarkToWhite(white, player)
                        && LatexSocialRelation.between(white, player) == LatexSocialRelation.OUTSIDER
                        && LatexSocialMemory.shouldRemainNeutral(white, player),
                "A Black-latex coat no longer provokes White observers");
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY); player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void instinct(ServerPlayer player, ChangedEntity white) throws Exception {
        var identity = new NativeOriginData.Identity(); identity.state = "native";
        identity.form = identity.current = ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.getId().toString(); identity.faction = "dark";
        NativeOriginData.get(player.server).players.put(player.getUUID(), identity);
        ProcessTransfur.setPlayerTransfurVariant(player, ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get(), null, 1, false);
        Capability cap = (Capability)Class.forName("com.lugert.true_transfur.transfur.TransfurCapabilities").getField("TRANSFUR_DATA").get(null);
        Object data = player.getCapability(cap).orElse(null);
        check(data != null, "TrueTransfur capability is present");
        data.getClass().getMethod("setTransfurred", boolean.class).invoke(data, true);
        Class mode = Class.forName("com.lugert.true_transfur.transfur.ControlMode");
        var change = Class.forName("com.lugert.true_transfur.transfur.TransfurController")
                .getMethod("switchControlMode", ServerPlayer.class, mode);
        change.invoke(null, player, Enum.valueOf(mode, "INSTINCT"));
        var body = (ChangedEntity)TrueTransfurCompat.interactionBody(player); body.setNoAi(true);
        check(TrueTransfurCompat.interactionBody(player) != player && TrueTransfurCompat.isInstinctBody(body), "Real TT 2.0 visible body is active");
        check(!white.canAttack(body) && !body.canAttack(white), "NPC/body acquisition is peaceful both ways");
        white.setTarget(body); body.setTarget(white);
        check(white.getTarget() == null && body.getTarget() == null && !white.canAttack(player),
                "Visible body stays peaceful and technical player stays invisible");
        var playersField = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(
                net.minecraft.server.players.PlayerList.class, "f_11197_");
        var players = (Map<UUID, ServerPlayer>)playersField.get(player.server.getPlayerList());
        var visitor = new ServerPlayer(player.server, player.serverLevel(), new GameProfile(UUID.randomUUID(), "WhiteVisitor"));
        visitor.connection = new ServerGamePacketListenerImpl(player.server, new Connection(PacketFlow.SERVERBOUND), visitor);
        players.put(visitor.getUUID(), visitor); player.serverLevel().addNewPlayer(visitor);
        visitor.setPos(player.position());
        try {
            ProcessTransfur.setPlayerTransfurVariant(visitor, ChangedTransfurVariants.PURE_WHITE_LATEX_WOLF.get(), null, 1, false);
            var hit = new LivingAttackEvent(visitor, visitor.damageSources().mobAttack(body), 4);
            LatexSocialEvents.onSocialCreatureAttacksPlayer(hit);
            body.setTarget(visitor);
            check(!body.canAttack(visitor) && body.getTarget() == null && hit.isCanceled(),
                    "An Instinct player does not automatically attack an opposite-colour PLAYER body");
        } finally { players.remove(visitor.getUUID()); visitor.discard(); }
        var mobTarget = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(Mob.class, "f_21362_");
        mobTarget.set(body, white);
        LatexSocialEvents.onLivingTick(new LivingEvent.LivingTickEvent(body));
        check(body.getTarget() == null, "A stale Instinct racial target is cleared on the existing tick");
        check(!body.getPersistentData().contains("ChangedSynergySocial")
                        && !body.getPersistentData().contains("ChangedSynergyPersonality")
                        && !body.getPersistentData().contains("ChangedSynergyHunt"),
                "Peace and cleanup do not attach NPC relationship or hunt records to a player body");
        FactionReputation.set(white, player, -60);
        check(!LatexCreatureCombatRules.mustRejectTarget(white, body), "Visible body retains the real player's hostile standing");
        FactionReputation.set(white, player, 0);
        LatexSocialMemory.markProvoked(white, player);
        check(!LatexCreatureCombatRules.mustRejectTarget(white, body), "Visible body retains the real player's provocation");
        LatexSocialMemory.clearProvocation(white, player);
        players.remove(player.getUUID());
        try {
            check(LatexCreatureCombatRules.mustRejectTarget(white, body),
                    "An ownerless former body is rejected instead of becoming an NPC enemy");
        } finally { players.put(player.getUUID(), player); }
        TrueTransfurCompat.ensurePlayerControl(player);
        check(ProcessTransfur.getPlayerTransfurVariantSafe(player).orElseThrow().getParent()
                        == ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get(),
                "Changing control mode preserves the player's species");
    }

    private static ChangedEntity spawn(ServerPlayer player, String path, List<ChangedEntity> creatures) {
        var id = ResourceLocation.parse(path.contains(":") ? path : "changed:" + path);
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(id)) throw new AssertionError("Missing fixture species " + id);
        var creature = (ChangedEntity)ForgeRegistries.ENTITY_TYPES.getValue(id).create(player.serverLevel());
        creature.setPos(player.getX() + 1, 90, player.getZ()); creature.setNoAi(true); creature.tickCount = 1000;
        creatures.add(creature); player.serverLevel().addFreshEntity(creature); return creature;
    }

    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
        checks++; System.out.println("COMPETITION_PASS: " + message);
    }
}
