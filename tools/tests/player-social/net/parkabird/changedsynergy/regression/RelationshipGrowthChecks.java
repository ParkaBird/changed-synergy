package net.parkabird.changedsynergy.regression;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.LogicalSide;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.network.ServerConfigSnapshotPacket;
import net.parkabird.changedsynergy.world.inventory.PlayerRelationshipMenu;
import net.parkabird.changedsynergy.world.inventory.SocialInteractionMenu;

/** Exercises the actual policy, inventories, menus, saved NBT and Forge damage events. */
final class RelationshipGrowthChecks {
    private static final List<ChangedEntity> MOBS = new ArrayList<>();
    private static int checks;
    private static final ChangedSynergyConfig.Common CONFIG = ChangedSynergyConfig.COMMON;
    private RelationshipGrowthChecks() {}

    static void run(ServerPlayer player, ServerPlayer other) throws Exception {
        // This private fixture changes many policies in one tick. A disk-watcher reload of an
        // earlier autosave must not overwrite a later test setting. Console saves remain real.
        com.electronwill.nightconfig.core.file.FileWatcher.defaultInstance().removeWatch(
                net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("changed_synergy-common.toml"));
        List<ForgeConfigSpec.ConfigValue<?>> options = List.of(CONFIG.balancedRelationships,
                CONFIG.strangerAffectionLimit, CONFIG.careAffectionAllowance, CONFIG.careAffectionCooldownSeconds,
                CONFIG.giftCooldownSeconds, CONFIG.bondTokensRequired, CONFIG.bondTokens,
                CONFIG.playfulFriendTransfur, CONFIG.allowMultipleBonds,
                CONFIG.telepathyEnabled, CONFIG.telepathyUnlockTransfurs, CONFIG.npcDialogueUsesTranslator);
        var old = options.stream().map(ForgeConfigSpec.ConfigValue::get).toList();
        try {
            CONFIG.balancedRelationships.set(true); CONFIG.strangerAffectionLimit.set(8);
            CONFIG.careAffectionAllowance.set(8); CONFIG.careAffectionCooldownSeconds.set(60);
            CONFIG.giftCooldownSeconds.set(300); CONFIG.bondTokensRequired.set(true);
            CONFIG.bondTokens.set(CONFIG.bondTokens.getDefault()); CONFIG.playfulFriendTransfur.set(true);
            CONFIG.allowMultipleBonds.set(true);
            growth(player);
            patFeedback(player);
            tokens(player, other);
            bondRoster(player);
            playful(player);
            migration(player);
            settings(player);
            commands(player, other);
            telepathy(player);
            restart(player);
            System.out.println("RELATIONSHIP_GROWTH_REGRESSION_PASS: " + checks + " checks");
        } finally {
            for (int i = 0; i < options.size(); i++) restore(options.get(i), old.get(i));
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent(); other.getInventory().clearContent();
            for (ChangedEntity mob : MOBS) {
                if (LatexSocialMemory.isBonded(mob, player)) LatexSocialMemory.unregisterBond(mob, player);
                if (LatexSocialMemory.isBonded(mob, other)) LatexSocialMemory.unregisterBond(mob, other);
                mob.discard();
            }
            MOBS.clear();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void restore(ForgeConfigSpec.ConfigValue config, Object value) { config.set(value); }

    private static ChangedEntity npc(ServerPlayer player, boolean spawn) {
        ChangedEntity mob = (ChangedEntity)ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get().getEntityType().create(player.level());
        mob.setPos(player.getX() + 1, player.getY(), player.getZ());
        CompoundTag personality = new CompoundTag();
        personality.putInt("Version", 7);
        personality.putInt("Traits", (1 << CreaturePersonality.Trait.CALM.ordinal()) | (1 << CreaturePersonality.Trait.PLAYFUL.ordinal()));
        personality.putInt("DominantTrait", CreaturePersonality.Trait.CALM.ordinal());
        mob.getPersistentData().put("ChangedSynergyPersonality", personality);
        if (spawn) player.serverLevel().addFreshEntity(mob);
        MOBS.add(mob); FactionReputation.set(mob, player, 100);
        return mob;
    }
    private static ChangedEntity friend(ServerPlayer player, int score, boolean spawn) {
        ChangedEntity mob = npc(player, spawn);
        CreaturePersonality.establishRelationship(mob, player);
        CreaturePersonality.setFamiliarity(mob, player, score);
        return mob;
    }
    private static void advance(ServerPlayer player, long ticks) {
        ((net.minecraft.world.level.storage.ServerLevelData)player.server.overworld().getLevelData()).setGameTime(player.server.overworld().getGameTime() + ticks);
    }
    private static void ordinaryGift(ServerPlayer player) { player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK, 16)); }
    private static CompoundTag memory(ChangedEntity mob, ServerPlayer player) {
        return mob.getPersistentData().getCompound("ChangedSynergyPersonality").getCompound("PlayerMemories").getCompound(player.getStringUUID());
    }

    private static void patFeedback(ServerPlayer player) {
        ChangedEntity mob = npc(player, true);
        var originalConnection = player.connection;
        var particles = new ArrayList<ClientboundLevelParticlesPacket>();
        player.connection = new ServerGamePacketListenerImpl(player.server, originalConnection.connection, player) {
            @Override public void send(Packet<?> packet) {
                if (packet instanceof ClientboundLevelParticlesPacket particle) particles.add(particle);
                super.send(packet);
            }
        };
        try {
            player.getPersistentData().remove("ChangedSynergyNextNativePatReaction");
            check(SynergyPatService.perform(player, mob, false) && CreaturePersonality.familiarity(mob, player) == 2,
                    "Real player pat awards the intended affection");
            check(particles.stream().filter(packet -> packet.getParticle() == ParticleTypes.HEART).count() == 1,
                    "An awarded NPC pat sends one affection-heart effect without duplicate generic hearts");
            particles.clear(); advance(player, 4);
            check(SynergyPatService.perform(player, mob, false) && CreaturePersonality.familiarity(mob, player) == 2,
                    "Pat remains usable during the shared affection cooldown");
            check(particles.stream().noneMatch(packet -> packet.getParticle() == ParticleTypes.HEART),
                    "Cooldown-blocked NPC pats do not falsely signal an affection gain");
            particles.clear(); advance(player, 1200);
            CreaturePersonality.setFamiliarity(mob, player, 8);
            check(SynergyPatService.perform(player, mob, false) && CreaturePersonality.familiarity(mob, player) == 8,
                    "Pat remains usable at the stranger affection limit");
            check(particles.stream().noneMatch(packet -> packet.getParticle() == ParticleTypes.HEART),
                    "Capped NPC pats do not falsely signal an affection gain");
        } finally { player.connection = originalConnection; }
    }

    private static void growth(ServerPlayer player) {
        ChangedEntity mob = npc(player, true);
        for (int i = 0; i < 80; i++) {
            CreaturePersonality.rememberEncounter(mob, player);
            CreaturePersonality.rememberPatGiven(mob, player);
        }
        check(CreaturePersonality.familiarity(mob, player) == 0 && !mob.hasCustomName(), "Encounters and return pats do not create affection or names");
        ordinaryGift(player); LatexSocialMemory.beginTruce(mob, player, 10000);
        check(RelationshipFavorService.canOpenBasicWheel(mob, player), "Pacified stranger has the basic wheel");
        check(RelationshipFavorService.offerHeldRelationshipGift(mob, player) == RelationshipFavorService.Result.NEEDS_FAMILIARITY
                && player.getMainHandItem().getCount() == 16, "Premature first gift consumes nothing");
        check(CreaturePersonality.rememberPatReceived(mob, player) == 2, "A pat awards two affection");
        check(CreaturePersonality.rememberPatReceived(mob, player) == 0, "Repeated pats share a persistent cooldown");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.ltxprogrammer.changed.init.ChangedItems.ORANGE.get(), 8));
        mob.setHealth(mob.getMaxHealth() / 2);
        float health = mob.getHealth();
        check(RelationshipFavorService.offerHeldFood(mob, player).isAccepted() && mob.getHealth() > health
                && CreaturePersonality.familiarity(mob, player) == 2, "Feeding heals during care cooldown without adding affection");
        health = mob.getHealth();
        check(RelationshipFavorService.offerHeldFood(mob, player).isAccepted() && player.getMainHandItem().getCount() == 6
                && mob.getHealth() > health, "Direct feeding has no operation cooldown");
        for (int i = 0; i < 8; i++) { advance(player, 1200); CreaturePersonality.rememberPatReceived(mob, player); }
        check(CreaturePersonality.familiarity(mob, player) == 8 && !CreaturePersonality.hasEstablishedRelationship(mob, player)
                && !mob.hasCustomName(), "Stranger affection is capped without automatic naming");
        CreaturePersonality.advanceRelationship(mob, player);
        check(!CreaturePersonality.hasEstablishedRelationship(mob, player), "Automatic advance cannot bypass the gift gate");
        ordinaryGift(player);
        check(RelationshipFavorService.offerHeldRelationshipGift(mob, player) == RelationshipFavorService.Result.ESTABLISHED
                && player.getMainHandItem().getCount() == 15 && mob.hasCustomName()
                && CreaturePersonality.familiarity(mob, player) == 10, "First accepted wheel gift names the NPC and retains preliminary affection");
        check(CreaturePersonality.careAllowance(mob, player) == 8, "Accepted wheel gift replenishes eight care points");
        check(RelationshipFavorService.offerHeldRelationshipGift(mob, player) == RelationshipFavorService.Result.COOLDOWN
                && player.getMainHandItem().getCount() == 15, "Gift cooldown rejects repeat clicks without consumption");
        check(CreaturePersonality.rememberPatReceived(mob, player) == 2, "Gift did not reset an expired independent care cooldown");
        check(CreaturePersonality.rememberPlayedTogether(mob, player) == 0, "Play shares the care cooldown");
        for (int i = 0; i < 3; i++) { advance(player, 1200); check(CreaturePersonality.rewardCare(mob, player, 2) == 2, "Care allowance is shared across activities"); }
        advance(player, 1200);
        check(CreaturePersonality.rewardCare(mob, player, 2) == 0 && CreaturePersonality.careAllowance(mob, player) == 0,
                "Exhausted allowance stops care gains after cooldown");
        ordinaryGift(player); advance(player, 1200);
        check(RelationshipFavorService.offerHeldRelationshipGift(mob, player).isAccepted() && CreaturePersonality.careAllowance(mob, player) == 8,
                "Another valid gift restores allowance");
        check(CreaturePersonality.rewardCare(mob, player, 2) == 2, "Care can be used immediately when its independent cooldown has expired");
        long careUntil = memory(mob, player).getLong("NextCareGainAt");
        memory(mob, player).putLong("NextGiftAt", 0); ordinaryGift(player);
        RelationshipFavorService.offerHeldRelationshipGift(mob, player);
        check(CreaturePersonality.careAllowance(mob, player) == 8 && memory(mob, player).getLong("NextCareGainAt") == careUntil,
                "Gift allowance refills without stacking or resetting the care cooldown");
        ChangedEntity restored = npc(player, false); restored.load(mob.saveWithoutId(new CompoundTag()));
        check(memory(restored, player).getLong("NextCareGainAt") == careUntil
                && CreaturePersonality.giftCooldownRemaining(restored, player) == CreaturePersonality.giftCooldownRemaining(mob, player),
                "Entity reload retains both independent affection cooldowns");
        CONFIG.careAffectionAllowance.set(0); advance(player, 1200);
        check(CreaturePersonality.rewardCare(mob, player, 2) == 0 && CreaturePersonality.careAllowance(mob, player) == 0,
                "Setting care allowance to zero also blocks already funded allowance");
        CONFIG.careAffectionAllowance.set(8);
        int before = CreaturePersonality.familiarity(mob, player);
        advance(player, 6000);
        var golden = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("changed_addon", "golden_orange"));
        check(golden != null && golden != Items.AIR, "Golden orange fixture is installed");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(golden, 2));
        check(RelationshipFavorService.offerHeldRelationshipGift(mob, player).isAccepted()
                && CreaturePersonality.familiarity(mob, player) == before + 6 && !LatexSocialMemory.isBonded(mob, player),
                "Golden orange grants six points without creating a bond");
        CreaturePersonality.setFamiliarity(mob, player, 60);
        player.setItemInHand(InteractionHand.MAIN_HAND, BondTokenService.required(mob));
        check(!BondTokenService.offer(mob, player) && !BondTokenService.reservedFor(mob, player),
                "Reaching sixty does not bypass the ordinary gift cooldown for tokens");
        check(!VoluntaryBondTransfurService.canOffer(mob, player), "Full affection alone does not unlock a first bond");
        check(!PlayerOutpostService.canInvite(friend(player, 27, true), player)
                && PlayerOutpostService.canInvite(friend(player, 28, true), player), "Outpost invitation requires Close affection");
    }

    private static void tokens(ServerPlayer player, ServerPlayer other) {
        clearAdvancement(player, "bond_token");
        ChangedEntity mob = friend(player, 60, true);
        CreaturePersonality.establishRelationship(mob, other); CreaturePersonality.setFamiliarity(mob, other, 60); FactionReputation.set(mob, other, 100);
        ItemStack token = BondTokenService.required(mob); token.getOrCreateTag().putString("Regression", "exact original token");
        player.setItemInHand(InteractionHand.MAIN_HAND, token.copy());
        check(BondTokenService.offer(mob, player) && player.getMainHandItem().isEmpty() && BondTokenService.ready(mob, player),
                "Token escrow has exactly one debit and unlocks the first bond");
        check(awarded(player, "bond_token"), "An accepted real token awards its social advancement");
        other.setItemInHand(InteractionHand.MAIN_HAND, token.copy());
        check(!BondTokenService.offer(mob, other) && other.getMainHandItem().getCount() == token.getCount(),
                "A second player cannot overwrite the reservation or lose their item");
        check(!BondTokenService.offer(mob, player), "Repeated token selection cannot duplicate escrow");
        BondTokenData data = BondTokenData.get(player.server);
        BondTokenData loaded = BondTokenData.load(data.save(new CompoundTag()));
        check(loaded.belongsTo(mob.getUUID(), player.getUUID())
                && ItemStack.matches(ItemStack.of(loaded.offer(mob.getUUID()).getCompound("Item")), token), "Reservation and exact token NBT survive reload");
        player.getInventory().clearContent();
        check(!BondTokenService.withdraw(other, mob.getUUID()) && BondTokenService.withdraw(player, mob.getUUID()), "Only the token owner can withdraw");
        check(!BondTokenService.withdraw(player, mob.getUUID()) && player.getInventory().countItem(token.getItem()) == token.getCount(), "Withdrawal returns exactly once");
        ChangedEntity creative = friend(player, 60, true);
        player.setGameMode(GameType.CREATIVE); player.setItemInHand(InteractionHand.MAIN_HAND, token.copy());
        check(BondTokenService.offer(creative, player) && player.getMainHandItem().isEmpty(), "Creative escrow still debits a real token");
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) player.getInventory().items.set(slot, new ItemStack(Items.COBBLESTONE, 64));
        var box = player.getBoundingBox().inflate(2);
        int drops = player.serverLevel().getEntitiesOfClass(ItemEntity.class, box).size();
        BondTokenService.withdraw(player, creative.getUUID());
        var after = player.serverLevel().getEntitiesOfClass(ItemEntity.class, box);
        check(after.size() == drops + 1 && after.stream().anyMatch(drop -> ItemStack.matches(drop.getItem(), token)),
                "Full inventory refunds drop the exact item at the visible player body");
        for (ItemEntity drop : after) drop.discard();
        player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent();
        UUID offline = UUID.randomUUID(), target = UUID.randomUUID();
        data.reserve(target, offline, token); data.refund(target); data.refund(target);
        loaded = BondTokenData.load(data.save(new CompoundTag()));
        check(loaded.takeRefunds(offline).size() == 1 && loaded.takeRefunds(offline).isEmpty(), "Offline refund queue survives reload and can be drained only once");
        data.takeRefunds(offline);
        ChangedEntity dying = friend(player, 60, true);
        player.setItemInHand(InteractionHand.MAIN_HAND, token.copy()); BondTokenService.offer(dying, player);
        BondTokenService.onDeath(new LivingDeathEvent(dying, dying.damageSources().generic()));
        BondTokenService.onDeath(new LivingDeathEvent(dying, dying.damageSources().generic())); BondTokenService.deliverRefunds(player);
        check(!data.hasOffer(dying.getUUID()) && player.getInventory().countItem(token.getItem()) == token.getCount(), "Death releases unused tokens only once");
        ChangedEntity disabled = friend(player, 60, true);
        player.setItemInHand(InteractionHand.MAIN_HAND, token.copy()); BondTokenService.offer(disabled, player);
        CONFIG.bondTokensRequired.set(false);
        LatexSocialMemory.addBond(disabled, player);
        check(!data.hasOffer(disabled.getUUID()) && player.getInventory().countItem(token.getItem()) == token.getCount(),
                "Disabling token requirements refunds immediately even if a bond is confirmed before the next tick");
        LatexSocialMemory.unregisterBond(disabled, player); CONFIG.bondTokensRequired.set(true); player.getInventory().clearContent();
        CONFIG.bondTokens.set(List.of("dark=missing:item,4", "white=minecraft:quartz,0"));
        check(ItemStack.matches(BondTokenService.required(mob), new ItemStack(net.ltxprogrammer.changed.init.ChangedItems.DARK_LATEX_CRYSTAL_FRAGMENT.get(), 4)),
                "Invalid token item configuration safely falls back to the default");
        CONFIG.bondTokens.set(CONFIG.bondTokens.getDefault());
    }

    private static void bondRoster(ServerPlayer player) {
        clearAdvancement(player, "reunited_companion");
        ChangedEntity mob = friend(player, 60, true);
        String name = mob.getName().getString();
        LatexSocialMemory.addBond(mob, player);
        check(!awarded(player, "reunited_companion"), "A first bond does not award reunion");
        check(PlayerRelationshipSettings.contacts(player).stream().anyMatch(contact -> contact.uuid().equals(mob.getUUID()) && contact.bonded()),
                "A real bond appears in the relationship roster");
        CompoundTag payload = new CompoundTag(); payload.putString("command", "cancel_relationship"); payload.putUUID("contact", mob.getUUID());
        PlayerRelationshipMenu menu = new PlayerRelationshipMenu(0, player.getInventory(), PlayerRelationshipSettings.contacts(player));
        menu.update(payload, LogicalSide.SERVER, player);
        check(LatexSocialMemory.isBonded(mob, player), "Roster end-bond requires confirmation");
        menu.update(payload, LogicalSide.SERVER, player);
        check(!LatexSocialMemory.isBonded(mob, player) && CreaturePersonality.hasTrustedRelationship(mob, player)
                && CreaturePersonality.familiarity(mob, player) == 60 && mob.getName().getString().equals(name),
                "Ending a bond preserves name, friendship and affection");
        check(CreaturePersonality.recognizesBond(mob, player) && BondTokenService.ready(mob, player), "Former companions can reunite without a second token");
        check(PlayerRelationshipSettings.contacts(player).stream().anyMatch(contact -> contact.uuid().equals(mob.getUUID()) && !contact.bonded()),
                "Ended bond remains as an ordinary friend in the roster");
        LatexSocialMemory.addBond(mob, player);
        check(awarded(player, "reunited_companion"), "Re-forming the former bond awards reunion without another token");
        BondReleaseService.release(player, mob.getUUID());
        ChangedEntity unloaded = friend(player, 60, false); LatexSocialMemory.addBond(unloaded, player);
        check(BondReleaseService.release(player, unloaded.getUUID()) == BondReleaseService.Result.QUEUED_UNLOADED
                && !LatexSocialMemory.bondedCreatureUuids(player).contains(unloaded.getUUID()), "Unloaded end-bond clears the player's bond immediately");
        check(LatexSocialMemory.applyPendingManualReleases(unloaded) && !LatexSocialMemory.isBonded(unloaded, player)
                && CreaturePersonality.hasTrustedRelationship(unloaded, player) && CreaturePersonality.recognizesBond(unloaded, player),
                "Unloaded NPC cleans bond state on return while retaining friendship and recognition");
        CONFIG.allowMultipleBonds.set(false);
        ChangedEntity existing = friend(player, 60, true); LatexSocialMemory.addBond(existing, player);
        ChangedEntity full = friend(player, 60, true);
        check(!BondTokenService.canCreateBond(full, player) && VoluntaryBondTransfurService.canOfferFriendly(full, player)
                && !VoluntaryBondTransfurService.canOffer(full, player), "Bond limits keep a separate friendly transfur without a token");
        CONFIG.allowMultipleBonds.set(true);
        var pupType = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(new net.minecraft.resources.ResourceLocation("changed", "dark_latex_wolf_pup"));
        ChangedEntity pup = (ChangedEntity)pupType.create(player.level());
        check(!CreatureSocialProfile.allowsPersonalRelationship(pup) && !BondTokenService.offer(pup, player), "Juvenile pets cannot use adult relationship growth or bond tokens");
        pup.discard();
    }

    private static void playful(ServerPlayer player) {
        clearAdvancement(player, "playful_warning");
        ChangedEntity mob = friend(player, 18, true);
        player.setHealth(player.getMaxHealth()); player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        PlayfulFriendTransfurGoal.interrupt(mob, player, true);
        check(!awarded(player, "playful_warning"), "An ordinary warning without an active prank awards nothing");
        advance(player, 6000);
        check(PlayfulFriendTransfurGoal.eligible(mob, player) && PlayfulFriendTransfurGoal.beginAttempt(mob, player), "Playful adult friend can begin a scoped attempt");
        int rep = FactionReputation.score(mob, player); int affection = CreaturePersonality.familiarity(mob, player); float health = mob.getHealth();
        mob.hurt(player.damageSources().playerAttack(player), 1);
        check(mob.getHealth() == health && !PlayfulFriendTransfurGoal.active(mob, player)
                && !LatexSocialMemory.isProvoked(mob, player) && FactionReputation.score(mob, player) == rep
                && CreaturePersonality.familiarity(mob, player) == affection, "Bare-handed warning interrupts the prank without damage, betrayal or reputation loss");
        check(awarded(player, "playful_warning"), "A real bare-handed interruption awards the warning advancement");
        advance(player, 1);
        check(!PlayfulFriendTransfurGoal.isDefense(mob, player) && !PlayfulFriendTransfurGoal.eligible(mob, player),
                "Self-defense exception ends immediately and warning enforces five-minute backoff");
        advance(player, 6000);
        check(PlayfulFriendTransfurGoal.eligible(mob, player), "Warning backoff expires normally");
        var personality = mob.getPersistentData().getCompound("ChangedSynergyPersonality");
        CreaturePersonality.setDominantTrait(mob, CreaturePersonality.Trait.POLITE);
        check(CreaturePersonality.has(mob, CreaturePersonality.Trait.POLITE) && !CreaturePersonality.has(mob, CreaturePersonality.Trait.PLAYFUL)
                && !PlayfulFriendTransfurGoal.eligible(mob, player) && !CreaturePersonality.dominantTrait(mob).behaviorOnly(),
                "Polite is an exclusive behavior trait and cannot select dialogue");
        CreaturePersonality.setDominantTrait(mob, CreaturePersonality.Trait.PLAYFUL);
        check(CreaturePersonality.has(mob, CreaturePersonality.Trait.PLAYFUL) && !CreaturePersonality.has(mob, CreaturePersonality.Trait.POLITE),
                "Explicit Playful assignment replaces Polite without replacing the primary personality");
        for (int i = 0; i < 120; i++) {
            ChangedEntity generated = npc(player, false); generated.getPersistentData().remove("ChangedSynergyPersonality");
            CreaturePersonality.ensure(generated);
            if (CreaturePersonality.has(generated, CreaturePersonality.Trait.PLAYFUL) && CreaturePersonality.has(generated, CreaturePersonality.Trait.POLITE)
                    || CreaturePersonality.dominantTrait(generated).behaviorOnly()) throw new AssertionError("Invalid generated personality");
        }
        check(true, "120 generated personalities have exclusive behavior traits and a primary dialogue personality");
        personality.putInt("Traits", personality.getInt("Traits") | (1 << CreaturePersonality.Trait.POLITE.ordinal()));
        personality.putInt("Version", 6); CreaturePersonality.ensure(mob);
        int migrated = personality.getInt("Traits"); CreaturePersonality.ensure(mob);
        check(migrated == personality.getInt("Traits") && !(CreaturePersonality.has(mob, CreaturePersonality.Trait.PLAYFUL)
                && CreaturePersonality.has(mob, CreaturePersonality.Trait.POLITE)), "Legacy conflicting traits migrate once without rerolling");
        CreaturePersonality.setDominantTrait(mob, CreaturePersonality.Trait.PLAYFUL);
        CreaturePersonality.setFamiliarity(mob, player, 28);
        check(!PlayfulFriendTransfurGoal.eligible(mob, player) && PlayfulFriendTransfurGoal.chance(28) == 0, "Close affection stops playful transfur");
        CreaturePersonality.setFamiliarity(mob, player, 18); CONFIG.playfulFriendTransfur.set(false);
        check(!PlayfulFriendTransfurGoal.eligible(mob, player), "Playful transfur configuration disables initiation"); CONFIG.playfulFriendTransfur.set(true);
        player.setHealth(player.getMaxHealth() / 2);
        check(!PlayfulFriendTransfurGoal.eligible(mob, player), "An endangered player is not a prank target"); player.setHealth(player.getMaxHealth());
    }

    private static void migration(ServerPlayer player) {
        ChangedEntity stranger = npc(player, false);
        CompoundTag old = new CompoundTag(); old.putInt("PatsReceived", 6); old.putInt("GiftFavor", 20);
        stranger.getPersistentData().getCompound("ChangedSynergyPersonality").put("PlayerMemories", new CompoundTag());
        stranger.getPersistentData().getCompound("ChangedSynergyPersonality").getCompound("PlayerMemories").put(player.getStringUUID(), old);
        check(CreaturePersonality.familiarity(stranger, player) == 8 && !stranger.hasCustomName(), "Legacy stranger history migrates to the preliminary cap");
        ChangedEntity established = npc(player, false);
        old = old.copy(); old.remove("Affection"); old.putBoolean("Relationship", true);
        established.getPersistentData().getCompound("ChangedSynergyPersonality").put("PlayerMemories", new CompoundTag());
        established.getPersistentData().getCompound("ChangedSynergyPersonality").getCompound("PlayerMemories").put(player.getStringUUID(), old);
        check(CreaturePersonality.familiarity(established, player) == 50 && CreaturePersonality.hasTrustedRelationship(established, player),
                "Legacy established friendship retains its computed affection");
        CompoundTag snapshot = established.saveWithoutId(new CompoundTag());
        ChangedEntity clone = npc(player, false); clone.load(snapshot);
        check(CreaturePersonality.familiarity(clone, player) == 50 && CreaturePersonality.careAllowance(clone, player) == 8,
                "Migrated explicit score and allowance survive entity reload");
        CONFIG.balancedRelationships.set(false);
        ChangedEntity legacy = npc(player, false);
        CreaturePersonality.rememberPatReceived(legacy, player); CreaturePersonality.rememberPatReceived(legacy, player);
        CreaturePersonality.rememberPatReceived(legacy, player); CreaturePersonality.advanceRelationship(legacy, player);
        check(CreaturePersonality.hasEstablishedRelationship(legacy, player) && CreaturePersonality.familiarity(legacy, player) == 15,
                "Disabling balance restores legacy pat growth and automatic acquaintance");
        CONFIG.balancedRelationships.set(true);
        check(CreaturePersonality.hasEstablishedRelationship(legacy, player) && CreaturePersonality.familiarity(legacy, player) == 15,
                "Re-enabling balance preserves the established relationship");
    }

    private static void settings(ServerPlayer player) {
        ChangedEntity mob = friend(player, 60, true);
        player.level().getGameRules().getRule(ChangedSynergyGameRules.FRIENDSHIP_SYSTEM).set(false, player.server);
        check(CreaturePersonality.rememberPatReceived(mob, player) == 0 && !RelationshipFavorService.canOpenBasicWheel(mob, player),
                "Friendship gamerule suspends balanced growth and acquaintance wheels");
        player.level().getGameRules().getRule(ChangedSynergyGameRules.FRIENDSHIP_SYSTEM).set(true, player.server);
        check(CreaturePersonality.familiarity(mob, player) == 60, "Friendship gamerule never clears saved affection");
        player.level().getGameRules().getRule(ChangedSynergyGameRules.BOND_SYSTEM).set(false, player.server);
        check(!BondTokenService.canCreateBond(mob, player), "Bond gamerule takes priority over tokens");
        player.level().getGameRules().getRule(ChangedSynergyGameRules.BOND_SYSTEM).set(true, player.server);
        var packet = new ServerConfigSnapshotPacket(ServerConfigSnapshotPacket.capture());
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ServerConfigSnapshotPacket.encode(packet, buffer);
            var decoded = ServerConfigSnapshotPacket.decode(buffer);
            check(decoded.values().equals(packet.values()) && !buffer.isReadable()
                    && decoded.values().getString("RELATIONSHIPS.GiftCooldownSeconds").equals("300"),
                    "Actual server settings serialize independently of client config");
        } finally { buffer.release(); }
        player.server.getCommands().performPrefixedCommand(player.server.createCommandSourceStack(),
                "changedsynergy config set RELATIONSHIPS.CareAffectionCooldownSeconds 90");
        check(CONFIG.careAffectionCooldownSeconds.get() == 90, "New options are manageable by the server console");
        CONFIG.careAffectionCooldownSeconds.set(60);
        var source = player.server.createCommandSourceStack();
        var commands = player.server.getCommands();
        commands.performPrefixedCommand(source, "changedsynergy config set RELATIONSHIPS.BondTokens dark=minecraft:quartz,4;white=minecraft:quartz,1");
        check(CONFIG.bondTokens.get().size() == 2 && BondTokenService.required(mob).getCount() == 4
                && BondTokenService.required(mob).is(Items.QUARTZ), "Console token lists preserve counts and semicolon-separated entries");
        commands.performPrefixedCommand(source, "changedsynergy config set RELATIONSHIPS.BondTokens [\"dark=minecraft:amethyst_shard,2\"]");
        check(CONFIG.bondTokens.get().size() == 1 && BondTokenService.required(mob).is(Items.AMETHYST_SHARD), "Console also accepts JSON token lists");
        commands.performPrefixedCommand(source, "changedsynergy config set RELATIONSHIPS.BondTokens [null]");
        check(CONFIG.bondTokens.get().equals(List.of("dark=minecraft:amethyst_shard,2")), "Malformed token list leaves the server setting unchanged");
        CONFIG.bondTokens.set(CONFIG.bondTokens.getDefault());
        ItemStack token = BondTokenService.required(mob); player.setItemInHand(InteractionHand.MAIN_HAND, token.copy());
        check(BondTokenService.offer(mob, player), "Console-cancellation fixture reserves a token");
        commands.performPrefixedCommand(source, "changedsynergy bond token cancel " + mob.getUUID());
        commands.performPrefixedCommand(source, "changedsynergy bond token cancel " + mob.getUUID());
        BondTokenService.deliverRefunds(player);
        check(!BondTokenService.reservedFor(mob, player) && player.getInventory().countItem(token.getItem()) == token.getCount(),
                "Server console can cancel and refund a reservation only once");
    }

    private static void commands(ServerPlayer player, ServerPlayer other) {
        var commands = player.server.getCommands();
        var source = player.createCommandSourceStack().withPermission(2);
        var console = player.server.createCommandSourceStack();
        float yaw = player.getYRot(), pitch = player.getXRot();
        player.setYRot(0); player.setXRot(0);
        ChangedEntity mob = npc(player, true);
        mob.setPos(player.getX(), player.getY(), player.getZ() + 4);
        String explicit = " " + mob.getUUID() + " " + other.getGameProfile().getName();
        String prefix = "changedsynergy relationship ";
        try {
            check(commands.performPrefixedCommand(source, prefix + "establish") == 1
                            && CreaturePersonality.familiarity(mob, player) == 60 && mob.hasCustomName()
                            && CreaturePersonality.hasEstablishedRelationship(mob, player)
                            && CreaturePersonality.familiarity(mob, other) == 0,
                    "Crosshair establish creates only the executor's full-affection named friendship");
            check(commands.performPrefixedCommand(console, prefix + "establish" + explicit) == 1
                            && CreaturePersonality.familiarity(mob, other) == 60,
                    "Console establishes an explicitly selected player's friendship");
            check(commands.performPrefixedCommand(source, prefix + "set 28") == 1
                            && CreaturePersonality.familiarity(mob, player) == 28,
                    "Value-first affection command uses the crosshair target");
            check(commands.performPrefixedCommand(console, prefix + "set" + explicit + " 12") == 1
                            && CreaturePersonality.familiarity(mob, other) == 12,
                    "Previous target-first affection syntax remains available");
            commands.performPrefixedCommand(source, prefix + "add 2");
            check(CreaturePersonality.familiarity(mob, player) == 30, "Add adjusts only the selected pair");
            commands.performPrefixedCommand(source, prefix + "cooldown care 90");
            commands.performPrefixedCommand(source, prefix + "cooldown gift 300");
            commands.performPrefixedCommand(source, prefix + "allowance set 0");
            var snapshot = CreaturePersonality.growthSnapshot(mob, player);
            check(snapshot.careCooldownTicks() == 1800 && snapshot.giftCooldownTicks() == 6000
                            && snapshot.careAllowance() == 0,
                    "Commands independently set persistent cooldowns and care allowance");
            commands.performPrefixedCommand(source, prefix + "cooldown care 0");
            check(CreaturePersonality.growthSnapshot(mob, player).giftCooldownTicks() == 6000,
                    "Clearing care cooldown does not reset gift cooldown");
            commands.performPrefixedCommand(source, prefix + "cooldown reset");
            snapshot = CreaturePersonality.growthSnapshot(mob, player);
            check(snapshot.careCooldownTicks() == 0 && snapshot.giftCooldownTicks() == 0
                            && snapshot.careAllowance() == 0 && snapshot.affection() == 30,
                    "Resetting cooldowns cannot refill allowance or change affection");
            commands.performPrefixedCommand(source, prefix + "allowance set 60");
            check(CreaturePersonality.careAllowance(mob, player) == 8,
                    "Debug allowance cannot exceed the server's configured limit");
            var unprivileged = player.createCommandSourceStack().withPermission(0);
            check(commands.performPrefixedCommand(unprivileged, prefix + "set 60") == 0
                            && CreaturePersonality.familiarity(mob, player) == 30
                            && commands.performPrefixedCommand(unprivileged, prefix + "inspect") == 30,
                    "Ordinary players may inspect their relationship but cannot change it");
            check(commands.performPrefixedCommand(console, prefix + "establish") == 0,
                    "Console commands without an explicit pair fail safely");
            commands.performPrefixedCommand(source, prefix + "establish");
            player.getInventory().clearContent();
            ItemStack token = BondTokenService.required(mob);
            player.setItemInHand(InteractionHand.MAIN_HAND, token.copy());
            check(commands.performPrefixedCommand(source, prefix + "token offer") == 1
                            && player.getMainHandItem().isEmpty() && BondTokenService.reservedFor(mob, player),
                    "Token debug command uses the real held item and ordinary eligibility");
            check(commands.performPrefixedCommand(console, prefix + "token withdraw" + explicit) == 0
                            && BondTokenService.reservedFor(mob, player),
                    "Another player cannot withdraw the actual owner's reserved token");
            check(commands.performPrefixedCommand(source, prefix + "token withdraw") == 1
                            && commands.performPrefixedCommand(source, prefix + "token withdraw") == 0
                            && player.getInventory().countItem(token.getItem()) == token.getCount(),
                    "Withdrawal returns exactly one real token and cannot duplicate it");
            player.getInventory().clearContent();
            check(commands.performPrefixedCommand(source, prefix + "bond") == 1
                            && LatexSocialMemory.isBonded(mob, player) && player.getInventory().isEmpty(),
                    "Bond debug shortcut uses real ownership without generating a token");
            check(commands.performPrefixedCommand(console, prefix + "bond" + explicit) == 0
                            && !LatexSocialMemory.isBonded(mob, other),
                    "Bond debug shortcut does not steal an existing bond");
            check(commands.performPrefixedCommand(source, prefix + "release") == 1
                            && !LatexSocialMemory.isBonded(mob, player)
                            && CreaturePersonality.hasEstablishedRelationship(mob, player)
                            && CreaturePersonality.familiarity(mob, player) == 60
                            && CreaturePersonality.recognizesBond(mob, player),
                    "Command release preserves full friendship and future reunion");
            player.level().getGameRules().getRule(ChangedSynergyGameRules.FRIENDSHIP_SYSTEM).set(false, player.server);
            check(commands.performPrefixedCommand(source, prefix + "set 0") == 0
                            && commands.performPrefixedCommand(source, prefix + "inspect") == 60
                            && CreaturePersonality.growthSnapshot(mob, player).affection() == 60,
                    "Paused friendship rejects growth changes but inspection retains saved data");
        } finally {
            player.level().getGameRules().getRule(ChangedSynergyGameRules.FRIENDSHIP_SYSTEM).set(true, player.server);
            player.setYRot(yaw); player.setXRot(pitch); player.getInventory().clearContent();
        }
    }

    private static void restart(ServerPlayer player) {
        UUID target = UUID.nameUUIDFromBytes("relationship-restart-offer".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID owner = UUID.nameUUIDFromBytes("relationship-restart-owner".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ItemStack token = new ItemStack(Items.QUARTZ, 4); token.getOrCreateTag().putString("Checkpoint", "server restart");
        BondTokenData data = BondTokenData.get(player.server);
        if (data.hasOffer(target)) {
            var queued = data.takeRefunds(owner);
            check(data.belongsTo(target, owner) && ItemStack.matches(ItemStack.of(data.offer(target).getCompound("Item")), token)
                    && queued.size() == 1 && ItemStack.matches(ItemStack.of((CompoundTag)queued.get(0)), token),
                    "Actual server restart retains pending escrow and an offline refund without duplication");
        } else {
            check(data.reserve(target, owner, token), "Prepare persistent escrow checkpoint for the next server process");
        }
        data.queueRefund(owner, token);
    }

    private static void telepathy(ServerPlayer player) throws Exception {
        String persistedKey = net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG;
        CompoundTag persisted = player.getPersistentData().getCompound(persistedKey);
        var original = persisted.get("ChangedSynergyTelepathy");
        original = original == null ? null : original.copy();
        CompoundTag network = new CompoundTag();
        network.putBoolean("UnderstandsNetwork", true); network.putInt("CompletedTransfurs", 3);
        persisted.put("ChangedSynergyTelepathy", network);
        player.getPersistentData().put(persistedKey, persisted);
        var understood = net.parkabird.changedsynergy.dialogue.NpcDialogue.class.getDeclaredMethod(
                "canUnderstand", ChangedEntity.class, ServerPlayer.class);
        understood.setAccessible(true);
        ChangedEntity ordinary = npc(player, false);
        var hypnosisType = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(
                new net.minecraft.resources.ResourceLocation("changed", "latex_hypno_cat"));
        ChangedEntity hypnotist = (ChangedEntity)java.util.Objects.requireNonNull(hypnosisType).create(player.level());
        MOBS.add(hypnotist);
        check(HypnosisProfile.isHypnoticCreature(hypnotist), "Telepathy test uses an actual hypnotic species");
        var instance = net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance.variantFor(
                ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get(), player);
        try {
            player.getInventory().clearContent(); CONFIG.telepathyEnabled.set(true); CONFIG.npcDialogueUsesTranslator.set(false);
            check(TelepathyService.canUnderstand(player) && (boolean)understood.invoke(null, ordinary, player),
                    "Saved telepathy understands ordinary speech when enabled");
            player.server.getCommands().performPrefixedCommand(player.server.createCommandSourceStack(),
                    "changedsynergy config set DIALOGUE.TelepathyEnabled false");
            check(!CONFIG.telepathyEnabled.get() && !TelepathyService.canUnderstand(player)
                    && !(boolean)understood.invoke(null, ordinary, player)
                    && !(boolean)understood.invoke(null, hypnotist, player),
                    "Console telepathy switch disables earned and temporary hypnotic understanding");
            TelepathyService.recordCompletedTransfur(player, instance);
            check(network.getInt("CompletedTransfurs") == 3 && network.getBoolean("UnderstandsNetwork"),
                    "Disabled telepathy pauses progression without deleting an earned unlock");
            CONFIG.telepathyEnabled.set(true);
            check(TelepathyService.canUnderstand(player), "Re-enabling telepathy restores existing understanding immediately");
            network.putBoolean("UnderstandsNetwork", false); network.putInt("CompletedTransfurs", 0);
            check(!(boolean)understood.invoke(null, ordinary, player) && (boolean)understood.invoke(null, hypnotist, player),
                    "Disabling Translator support does not give every human universal understanding");
            CONFIG.telepathyUnlockTransfurs.set(0); TelepathyService.recordCompletedTransfur(player, instance);
            check(network.getInt("CompletedTransfurs") == 1 && !TelepathyService.canUnderstand(player),
                    "Zero unlock threshold only prevents new unlocks");
            network.putBoolean("UnderstandsNetwork", true);
            check(TelepathyService.canUnderstand(player), "Zero unlock threshold preserves an already earned ability");
            CONFIG.telepathyEnabled.set(false);
            var translatorItem = java.util.Objects.requireNonNull(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                    new net.minecraft.resources.ResourceLocation("changed_addon", "translator")));
            ItemStack translator = new ItemStack(translatorItem); translator.getOrCreateTag().putBoolean("Enabled", true);
            player.setItemInHand(InteractionHand.MAIN_HAND, translator); CONFIG.npcDialogueUsesTranslator.set(true);
            check((boolean)understood.invoke(null, ordinary, player), "Enabled Translator still interprets speech when telepathy is off");
            translator.getOrCreateTag().putBoolean("Enabled", false);
            check(!(boolean)understood.invoke(null, ordinary, player), "Switched-off Translator cannot bypass the telepathy setting");
            translator.getOrCreateTag().putBoolean("Enabled", true); CONFIG.npcDialogueUsesTranslator.set(false);
            check(!(boolean)understood.invoke(null, ordinary, player), "Server can disable Translator support independently");
            check(ServerConfigSnapshotPacket.capture().getString("DIALOGUE.TelepathyEnabled").equals("false"),
                    "Multiplayer snapshot includes the actual telepathy setting");
        } finally {
            if (original == null) persisted.remove("ChangedSynergyTelepathy");
            else persisted.put("ChangedSynergyTelepathy", original);
            player.getInventory().clearContent();
        }
    }

    private static net.minecraft.advancements.Advancement advancement(ServerPlayer player, String id) {
        return java.util.Objects.requireNonNull(player.server.getAdvancements().getAdvancement(
                new net.minecraft.resources.ResourceLocation("changed_synergy", id)), "Missing advancement: " + id);
    }
    private static boolean awarded(ServerPlayer player, String id) {
        return player.getAdvancements().getOrStartProgress(advancement(player, id)).isDone();
    }
    private static void clearAdvancement(ServerPlayer player, String id) {
        player.getAdvancements().revoke(advancement(player, id), "complete");
    }

    static void proxy(ServerPlayer actor, ChangedEntity body) {
        check(actor.server.getCommands().performPrefixedCommand(actor.createCommandSourceStack().withPermission(2),
                        "changedsynergy relationship establish " + body.getUUID()) == 0,
                "Relationship debug commands cannot assign NPC friendship to a TrueTransfur proxy");
        actor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK, 3));
        ItemStack held = actor.getMainHandItem().copy();
        check(CreaturePersonality.rememberPatReceived(body, actor) == 0
                && RelationshipFavorService.offerHeldRelationshipGift(body, actor) == RelationshipFavorService.Result.REJECTED
                && !CreaturePersonality.establishRelationship(body, actor), "INSTINCT proxy rejects NPC relationship progression");
        CreaturePersonality.recognizeBond(body, actor.getUUID());
        check(!body.getPersistentData().getCompound("ChangedSynergyPersonality").contains("PlayerMemories")
                && ItemStack.matches(actor.getMainHandItem(), held), "INSTINCT proxy gains no NPC memories, token ownership or item debit");
    }

    static void permanentBond(ServerPlayer player) {
        ChangedEntity mob = friend(player, 60, true);
        player.setItemInHand(InteractionHand.MAIN_HAND, BondTokenService.required(mob));
        var form = ProcessTransfur.getPlayerTransfurVariant(player);
        check(TrueTransfurCompat.isPermanentPlayer(player) && BondTokenService.offer(mob, player), "TrueTransfur permanent player can reserve an NPC bond token");
        boolean telepathy = CONFIG.telepathyEnabled.get();
        try {
            CONFIG.telepathyEnabled.set(false);
            var understand = net.parkabird.changedsynergy.dialogue.NpcDialogue.class.getDeclaredMethod(
                    "canUnderstand", ChangedEntity.class, ServerPlayer.class);
            understand.setAccessible(true);
            check(!(boolean)understand.invoke(null, mob, player), "Telepathy switch also covers an actual TrueTransfur permanent player body");
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        } finally { CONFIG.telepathyEnabled.set(telepathy); }
        check(!NativeBondInvitationService.select(mob, player) && NativeBondInvitationService.select(mob, player)
                && LatexSocialMemory.isBonded(mob, player) && ProcessTransfur.getPlayerTransfurVariant(player) == form
                && !TrueTransfurCompat.isInstinctControlled(player), "Permanent player bonds safely without reversal or a control-mode change");
        BondReleaseService.release(player, mob.getUUID()); mob.discard(); MOBS.remove(mob);
        player.getInventory().clearContent();
    }

    private static void check(boolean condition, String text) {
        if (!condition) throw new AssertionError(text);
        checks++; System.out.println("RELATIONSHIP_GROWTH_PASS: " + text);
    }
}
