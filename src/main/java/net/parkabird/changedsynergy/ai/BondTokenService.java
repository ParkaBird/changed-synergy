package net.parkabird.changedsynergy.ai;

import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class BondTokenService {
    private BondTokenService() {}

    public static ItemStack required(ChangedEntity creature) {
        String category = HunterFaction.isAquatic(creature) ? "aquatic"
                : HunterFaction.isOrganic(creature) ? "organic"
                : HunterFaction.of(creature) == HunterFaction.DARK ? "dark"
                : HunterFaction.of(creature) == HunterFaction.WHITE ? "white" : "variant";
        ItemStack fallback = switch (category) {
            case "dark" -> new ItemStack(ChangedItems.DARK_LATEX_CRYSTAL_FRAGMENT.get(), 4);
            case "white" -> new ItemStack(Items.QUARTZ);
            case "organic" -> new ItemStack(Items.GOLDEN_APPLE);
            case "aquatic" -> new ItemStack(Items.HEART_OF_THE_SEA);
            default -> new ItemStack(Items.AMETHYST_SHARD);
        };
        for (String entry : ChangedSynergyConfig.COMMON.bondTokens.get()) {
            String[] split = entry.split("=", 2);
            if (split.length != 2 || !split[0].trim().equals(category)) continue;
            String[] value = split[1].split(",", 2);
            ResourceLocation id = ResourceLocation.tryParse(value[0].trim());
            try {
                int count = value.length == 2 ? Integer.parseInt(value[1].trim()) : 1;
                var item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
                if (item != null && item != Items.AIR && count > 0 && count <= Math.min(64, item.getMaxStackSize()))
                    return new ItemStack(item, count);
            } catch (NumberFormatException ignored) { /* Invalid config never destroys an offering. */ }
        }
        return fallback;
    }

    public static boolean reservedFor(ChangedEntity creature, ServerPlayer player) {
        return BondTokenData.get(player.server).belongsTo(creature.getUUID(), player.getUUID());
    }

    public static boolean reservedFor(ServerPlayer player, UUID creatureId) {
        return BondTokenData.get(player.server).belongsTo(creatureId, player.getUUID());
    }

    public static boolean reservedElsewhere(ChangedEntity creature, ServerPlayer player) {
        return BondTokenData.get(player.server).hasOffer(creature.getUUID()) && !reservedFor(creature, player);
    }

    public static boolean canCreateBond(ChangedEntity creature, ServerPlayer player) {
        return CreatureSocialProfile.allowsPersonalRelationship(creature) && !CreatureSocialProfile.isJuvenile(creature)
                && creature.isAlive() && !creature.isRemoved()
                && ChangedSynergyGameRules.enabled(player.level(), ChangedSynergyGameRules.BOND_SYSTEM)
                && !LatexSocialMemory.hasActiveBond(creature)
                && LatexSocialMemory.petOwnerUuid(creature).filter(owner -> !owner.equals(player.getUUID())).isEmpty()
                && (ChangedSynergyConfig.COMMON.allowMultipleBonds.get() || LatexSocialMemory.bondedCreatureUuids(player).isEmpty());
    }

    public static boolean ready(ChangedEntity creature, ServerPlayer player) {
        return canCreateBond(creature, player) && !reservedElsewhere(creature, player)
                && (!(CreaturePersonality.balanced() || ChangedSynergyConfig.COMMON.bondTokensRequired.get())
                    || CreaturePersonality.familiarity(creature, player) >= 60)
                && (!ChangedSynergyConfig.COMMON.bondTokensRequired.get()
                    || CreaturePersonality.familiarity(creature, player) >= 60
                        && (CreaturePersonality.recognizesBond(creature, player) || reservedFor(creature, player)));
    }

    public static String offerProblem(ChangedEntity creature, ServerPlayer player) {
        if (!ChangedSynergyConfig.COMMON.bondTokensRequired.get()
                || !CreatureSocialProfile.allowsPersonalRelationship(creature) || CreatureSocialProfile.isJuvenile(creature)
                || !creature.isAlive() || creature.isRemoved() || creature.level() != player.level()
                || player.distanceToSqr(creature) > 64 || !player.hasLineOfSight(creature)
                || TrueTransfurCompat.isInstinctShell(player)) return "unavailable";
        if (!CreaturePersonality.hasTrustedRelationship(creature, player)
                || LatexSocialMemory.isProvoked(creature, player) || LatexSocialMemory.hasBetrayedPatTruce(creature, player)
                || FactionReputation.isHostile(creature, player)) return "unavailable";
        if (CreaturePersonality.familiarity(creature, player) < 60) return "requires_full";
        if (!canCreateBond(creature, player)) return "bond_limit";
        if (reservedElsewhere(creature, player)) return "reserved_other";
        if (reservedFor(creature, player) || CreaturePersonality.recognizesBond(creature, player)) return "already_ready";
        if (CreaturePersonality.giftCooldownRemaining(creature, player) > 0) return "gift_cooldown";
        return null;
    }

    /** Only the deliberate wheel-gift route calls this, never a feed or a player-to-player gift. */
    public static boolean offer(ChangedEntity creature, ServerPlayer player) {
        String problem = offerProblem(creature, player);
        if (problem != null) {
            if (problem.equals("gift_cooldown")) message(player, problem, Math.max(1, (CreaturePersonality.giftCooldownRemaining(creature, player) + 19) / 20));
            else message(player, problem);
            return false;
        }
        ItemStack required = required(creature);
        ItemStack held = player.getMainHandItem();
        if (!held.is(required.getItem()) || held.getCount() < required.getCount()) {
            player.displayClientMessage(Component.translatable("message.changed_synergy.relationship.token_required",
                    required.getCount(), required.getHoverName()), true);
            return false;
        }
        ItemStack actual = held.copy();
        actual.setCount(required.getCount());
        if (!BondTokenData.get(player.server).reserve(creature.getUUID(), player.getUUID(), actual)) return false;
        // Escrow always has a real debit, including Creative, so withdrawal cannot manufacture items.
        held.shrink(required.getCount());
        creature.setPersistenceRequired();
        CreaturePersonality.recordTokenGift(creature, player);
        net.parkabird.changedsynergy.advancement.SynergyAdvancements.grant(player,
                net.parkabird.changedsynergy.advancement.SynergyAdvancements.BOND_TOKEN);
        message(player, "token_accepted");
        NpcDialogue.context(creature, player, "relationship.token_accepted");
        return true;
    }

    public static boolean withdraw(ServerPlayer player, UUID creature) {
        creature = CreatureMorphAliasData.get(player.server).resolveAlias(creature);
        BondTokenData data = BondTokenData.get(player.server);
        if (!data.belongsTo(creature, player.getUUID())) return false;
        data.refund(creature);
        deliverRefunds(player);
        message(player, "token_withdrawn");
        return true;
    }

    public static void completed(ChangedEntity creature, ServerPlayer player) {
        CreaturePersonality.recognizeBond(creature, player.getUUID());
        BondTokenData data = BondTokenData.get(player.server);
        if (ChangedSynergyConfig.COMMON.bondTokensRequired.get()) data.complete(creature.getUUID(), player.getUUID());
        else if (data.belongsTo(creature.getUUID(), player.getUUID())) {
            data.refund(creature.getUUID());
            deliverRefunds(player);
        }
    }

    public static void message(ServerPlayer player, String reason, Object... args) {
        player.displayClientMessage(Component.translatable("message.changed_synergy.relationship." + reason, args), true);
    }

    public static void deliverRefunds(ServerPlayer player) {
        BondTokenData data = BondTokenData.get(player.server);
        if (!data.hasRefund(player.getUUID())) return;
        var body = TrueTransfurCompat.interactionBody(player);
        if (!player.isAlive() || !body.isAlive() || body.isRemoved()
                || TrueTransfurCompat.isInstinctShell(player) && body == player) return;
        for (var tag : data.takeRefunds(player.getUUID())) {
            ItemStack stack = ItemStack.of((CompoundTag)tag);
            if (stack.isEmpty()) continue;
            // Forge insertion preserves a real remainder even for a full Creative inventory.
            stack = net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(
                    new net.minecraftforge.items.wrapper.PlayerMainInvWrapper(player.getInventory()), stack, false);
            player.containerMenu.broadcastChanges();
            if (!stack.isEmpty()) {
                ItemEntity drop = new ItemEntity(body.level(), body.getX(), body.getY() + 0.3, body.getZ(), stack);
                drop.setDefaultPickUpDelay();
                drop.setTarget(player.getUUID());
                if (!body.level().addFreshEntity(drop)) data.queueRefund(player.getUUID(), stack);
            }
        }
    }

    @SubscribeEvent public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ChangedEntity creature && creature.getServer() != null
                && !TrueTransfurCompat.isInstinctBody(creature)) BondTokenData.get(creature.getServer()).refund(creature.getUUID());
    }

    @SubscribeEvent public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player && player.tickCount % 20 == 0) {
            deliverRefunds(player);
        }
    }

    @SubscribeEvent public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.getServer().getTickCount() % 20 == 0
                && !ChangedSynergyConfig.COMMON.bondTokensRequired.get()) BondTokenData.get(event.getServer()).refundAll();
    }
}
