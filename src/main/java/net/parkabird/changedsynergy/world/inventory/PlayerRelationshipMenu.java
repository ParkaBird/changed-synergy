package net.parkabird.changedsynergy.world.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.Emote;
import net.ltxprogrammer.changed.init.ChangedAbilities;
import net.ltxprogrammer.changed.init.ChangedTags;
import net.ltxprogrammer.changed.process.ProcessEmote;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.ltxprogrammer.changed.world.inventory.AbilityRadialMenu;
import net.ltxprogrammer.changed.world.inventory.UpdateableMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.NetworkHooks;
import net.parkabird.changedsynergy.ai.BondedOwnerDefenseGoal;
import net.parkabird.changedsynergy.ai.BondReleaseService;
import net.parkabird.changedsynergy.ai.CreaturePersonality;
import net.parkabird.changedsynergy.ai.CreatureRelationshipForgetData;
import net.parkabird.changedsynergy.ai.FactionReputation;
import net.parkabird.changedsynergy.ai.InvoluntaryTransfurNegotiation;
import net.parkabird.changedsynergy.ai.LatexCreatureCombatRules;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.ai.PlayerRelationshipSettings;
import net.parkabird.changedsynergy.ai.PlayerRelationshipSettings.Contact;
import net.parkabird.changedsynergy.ai.PlayerRelationshipSettings.Formation;
import net.parkabird.changedsynergy.ai.PlayerRelationshipSettings.DamageFilter;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.event.NpcDispositionEvents;
import net.parkabird.changedsynergy.init.ChangedSynergyMenus;

/** Server-authoritative player hub for friendships, bonds and group commands. */
public final class PlayerRelationshipMenu extends AbstractContainerMenu
        implements UpdateableMenu {
    public static final Component TITLE = Component.translatable(
            "container.changed_synergy.relationship_manager");
    private static final String NEXT_ACTIVITY =
            "ChangedSynergyNextRelationshipGroupActivity";
    private static final String CANCEL_CONFIRMATION =
            "ChangedSynergyRelationshipCancelConfirmation";
    private final Player player;
    private final List<Contact> contacts;
    private Formation formation;
    private DamageFilter damageFilter;
    private final boolean transfurred;
    private final boolean organicStyle;
    private final boolean absorptionNegotiation;
    private final boolean negotiationUsesRelationshipSlot;

    public PlayerRelationshipMenu(
            int id,
            Inventory inventory,
            List<Contact> contacts) {
        super(ChangedSynergyMenus.PLAYER_RELATIONSHIPS.get(), id);
        this.player = inventory.player;
        this.contacts = new ArrayList<>(contacts);
        ServerPlayer server = inventory.player instanceof ServerPlayer value
                ? value : null;
        this.formation = server == null
                ? Formation.STANDARD
                : PlayerRelationshipSettings.formation(server);
        this.damageFilter = server == null
                ? DamageFilter.FRIENDS
                : PlayerRelationshipSettings.damageFilter(server);
        var variant = ProcessTransfur.getPlayerTransfurVariant(inventory.player);
        this.transfurred = variant != null && !variant.isTemporaryFromSuit();
        this.organicStyle = transfurred
                && !variant.getParent().getEntityType()
                        .is(ChangedTags.EntityTypes.LATEX);
        this.absorptionNegotiation = server != null
                && InvoluntaryTransfurNegotiation.hasAbsorptionClaim(server);
        this.negotiationUsesRelationshipSlot = absorptionNegotiation
                && !hasFreeAbilitySlot(server);
    }

    public PlayerRelationshipMenu(
            int id,
            Inventory inventory,
            FriendlyByteBuf data) {
        super(ChangedSynergyMenus.PLAYER_RELATIONSHIPS.get(), id);
        this.player = inventory.player;
        this.transfurred = data.readBoolean();
        this.organicStyle = data.readBoolean();
        this.absorptionNegotiation = data.readBoolean();
        this.negotiationUsesRelationshipSlot = data.readBoolean();
        this.formation = enumByOrdinal(
                Formation.values(), data.readVarInt(), Formation.STANDARD);
        this.damageFilter = enumByOrdinal(
                DamageFilter.values(), data.readVarInt(),
                DamageFilter.FRIENDS);
        int count = Math.min(64, data.readVarInt());
        this.contacts = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            contacts.add(readContact(data));
        }
    }

    public static void open(ServerPlayer player) {
        List<Contact> contacts = PlayerRelationshipSettings.contacts(player);
        NetworkHooks.openScreen(
                player,
                new SimpleMenuProvider(
                        (id, inventory, viewer) ->
                                new PlayerRelationshipMenu(id, inventory, contacts),
                        TITLE),
                buffer -> writeOpeningData(buffer, player, contacts));
    }

    public List<Contact> getContacts() {
        return List.copyOf(contacts);
    }

    public Formation getFormation() {
        return formation;
    }

    public DamageFilter getDamageFilter() {
        return damageFilter;
    }

    public boolean isTransfurred() {
        return transfurred;
    }

    /** Humans intentionally use the organic frame language with inventory colors. */
    public boolean usesOrganicStyle() {
        return !transfurred || organicStyle;
    }

    public boolean hasAbsorptionNegotiation() {
        return absorptionNegotiation;
    }

    public boolean usesAbsorptionNegotiationSlot() {
        return negotiationUsesRelationshipSlot;
    }

    @Override
    public ItemStack quickMoveStack(Player viewer, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player viewer) {
        return viewer.isAlive() && !viewer.isRemoved();
    }

    @Override
    public int getId() {
        return containerId;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void update(
            CompoundTag payload,
            LogicalSide receiver,
            @Nullable ServerPlayer origin) {
        String command = payload.getString("command");
        if (receiver == LogicalSide.CLIENT) {
            if ("sync".equals(command)) {
                applyClientSync(payload);
            }
            return;
        }
        if (origin == null || origin != player || !stillValid(origin)) {
            return;
        }

        switch (command) {
            case "toggle_contact_follow" ->
                    toggleContactFollow(origin, payload);
            case "cancel_relationship" -> cancelRelationship(origin, payload);
            case "toggle_group_follow" -> toggleGroupFollow(origin);
            case "cycle_formation" -> {
                formation = PlayerRelationshipSettings.cycleFormation(origin);
                origin.displayClientMessage(Component.translatable(
                        "message.changed_synergy.relationship.formation",
                        Component.translatable(formation.translationKey())), true);
            }
            case "shared_activity" -> sharedActivity(origin);
            case "social_signal" -> socialSignal(origin);
            case "request_help" -> requestHelp(origin);
            case "mediate" -> mediate(origin, payload);
            case "cycle_damage_filter" -> {
                damageFilter = PlayerRelationshipSettings.cycleDamageFilter(origin);
                origin.displayClientMessage(Component.translatable(
                        "message.changed_synergy.relationship.damage_filter",
                        Component.translatable(damageFilter.translationKey())), true);
            }
            case "open_ability_wheel" -> openAbilityWheel(origin);
            default -> {
                return;
            }
        }
        if (origin.containerMenu == this && !"open_ability_wheel".equals(command)) {
            synchronize(origin);
        }
    }

    private void cancelRelationship(ServerPlayer origin, CompoundTag payload) {
        if (!payload.hasUUID("contact")) {
            return;
        }
        UUID uuid = payload.getUUID("contact");
        Contact selected = contacts.stream().filter(contact -> contact.uuid().equals(uuid))
                .findFirst().orElse(null);
        if (selected == null) {
            return;
        }
        long now = origin.level().getGameTime();
        CompoundTag pending = origin.getPersistentData().getCompound(CANCEL_CONFIRMATION);
        if (!pending.hasUUID("Target") || !uuid.equals(pending.getUUID("Target"))
                || pending.getLong("Expires") < now) {
            CompoundTag next = new CompoundTag();
            next.putUUID("Target", uuid);
            next.putLong("Expires", now + 200L);
            origin.getPersistentData().put(CANCEL_CONFIRMATION, next);
            origin.displayClientMessage(Component.translatable(
                    "message.changed_synergy.relationship.cancel_confirm", selected.name()), true);
            return;
        }
        origin.getPersistentData().remove(CANCEL_CONFIRMATION);
        if (LatexSocialMemory.bondedCreatureUuids(origin).contains(uuid)) {
            BondReleaseService.release(origin, uuid);
        }
        ChangedEntity creature = PlayerRelationshipSettings.findLoaded(origin, uuid);
        if (creature != null) {
            if (selected.bonded() && LatexSocialMemory.isPetOwner(creature, origin)) {
                LatexSocialMemory.unregisterBond(creature, origin);
            }
            CreaturePersonality.forgetRelationship(creature, origin);
        } else {
            if (selected.bonded()) {
                LatexSocialMemory.queueManualRelease(origin, uuid);
            }
            CreatureRelationshipForgetData.get(origin.server)
                    .queue(origin.getUUID(), uuid);
        }
        PlayerRelationshipSettings.forgetContact(origin, uuid);
        origin.closeContainer();
        origin.displayClientMessage(Component.translatable(
                "message.changed_synergy.relationship.cancelled", selected.name()), true);
    }

    private void toggleContactFollow(
            ServerPlayer origin,
            CompoundTag payload) {
        if (!payload.hasUUID("contact")) {
            return;
        }
        ChangedEntity creature = PlayerRelationshipSettings.findLoaded(
                origin, payload.getUUID("contact"));
        if (creature == null) {
            origin.displayClientMessage(Component.translatable(
                    "message.changed_synergy.relationship.contact_unloaded"), true);
            return;
        }
        if (LatexSocialMemory.isPetOwner(creature, origin)
                || LatexSocialMemory.isBonded(creature, origin)) {
            LatexSocialMemory.setFollowingOwner(
                    creature, !LatexSocialMemory.isFollowingOwner(creature));
            return;
        }
        if (!CreaturePersonality.hasTrustedRelationship(creature, origin)
                || !CreaturePersonality.canFriendFollow(creature, origin)) {
            origin.displayClientMessage(Component.translatable(
                    "message.changed_synergy.relationship.follow_locked"), true);
            return;
        }
        CreaturePersonality.setSocialFollowing(
                creature,
                origin,
                !CreaturePersonality.isSocialFollowing(creature, origin));
    }

    private void toggleGroupFollow(ServerPlayer origin) {
        List<ChangedEntity> loaded = PlayerRelationshipSettings.loadedRelated(origin);
        boolean shouldFollow = loaded.stream().anyMatch(creature -> {
            if (LatexSocialMemory.isBonded(creature, origin)
                    || LatexSocialMemory.isPetOwner(creature, origin)) {
                return !LatexSocialMemory.isFollowingOwner(creature);
            }
            return CreaturePersonality.canFriendFollow(creature, origin)
                    && !CreaturePersonality.isSocialFollowing(creature, origin);
        });
        int changed = 0;
        for (ChangedEntity creature : loaded) {
            if (LatexSocialMemory.isBonded(creature, origin)
                    || LatexSocialMemory.isPetOwner(creature, origin)) {
                LatexSocialMemory.setFollowingOwner(creature, shouldFollow);
                changed++;
            } else if (!shouldFollow
                    || CreaturePersonality.canFriendFollow(creature, origin)) {
                CreaturePersonality.setSocialFollowing(
                        creature, origin, shouldFollow);
                changed++;
            }
        }
        origin.displayClientMessage(Component.translatable(
                shouldFollow
                        ? "message.changed_synergy.relationship.group_follow"
                        : "message.changed_synergy.relationship.group_wait",
                changed), true);
    }

    private void sharedActivity(ServerPlayer origin) {
        long now = origin.level().getGameTime();
        long next = origin.getPersistentData().getLong(NEXT_ACTIVITY);
        if (next > now) {
            origin.displayClientMessage(Component.translatable(
                    "message.changed_synergy.relationship.activity_cooldown",
                    Math.max(1L, (next - now + 19L) / 20L)), true);
            return;
        }
        List<ChangedEntity> nearby = PlayerRelationshipSettings.loadedRelated(origin)
                .stream()
                .filter(creature -> creature.level() == origin.level()
                        && creature.distanceToSqr(origin) <= 14.0D * 14.0D
                        && creature.getTarget() == null)
                .limit(6)
                .toList();
        if (nearby.isEmpty()) {
            origin.displayClientMessage(Component.translatable(
                    "message.changed_synergy.relationship.no_nearby_contacts"), true);
            return;
        }
        boolean quietRest = origin.isShiftKeyDown();
        Emote emote = quietRest ? Emote.SLEEPY : Emote.CASUAL;
        ProcessEmote.playerEmote(origin, emote);
        for (ChangedEntity creature : nearby) {
            creature.getNavigation().stop();
            creature.getLookControl().setLookAt(origin, 30.0F, 30.0F);
            NpcDialogue.emoteOnly(creature, emote);
            if (quietRest) {
                creature.heal(2.0F);
            } else {
                CreaturePersonality.adjustFamiliarityFromInteraction(
                        creature, origin, 1);
            }
        }
        if (quietRest) {
            origin.heal(2.0F);
        }
        origin.getPersistentData().putLong(NEXT_ACTIVITY, now + 600L);
        origin.displayClientMessage(Component.translatable(
                quietRest
                        ? "message.changed_synergy.relationship.rest_started"
                        : "message.changed_synergy.relationship.activity_started",
                nearby.size()), true);
    }

    private void socialSignal(ServerPlayer origin) {
        Emote[] signals = {Emote.CASUAL, Emote.HEART, Emote.IDEA, Emote.PAUSE};
        Emote emote = signals[PlayerRelationshipSettings.nextSocialSignal(
                origin, signals.length)];
        ProcessEmote.playerEmote(origin, emote);
        PlayerRelationshipSettings.loadedRelated(origin).stream()
                .filter(creature -> creature.level() == origin.level()
                        && creature.distanceToSqr(origin) <= 12.0D * 12.0D
                        && creature.hasLineOfSight(origin))
                .limit(5)
                .forEach(creature -> {
                    creature.getLookControl().setLookAt(origin, 30.0F, 30.0F);
                    NpcDialogue.emoteOnly(creature,
                            emote == Emote.PAUSE ? Emote.CONFUSED : emote);
                });
    }

    private void requestHelp(ServerPlayer origin) {
        LivingEntity threat = origin.getLastHurtByMob();
        if (threat == null
                || !threat.isAlive()
                || origin.tickCount - origin.getLastHurtByMobTimestamp() > 200) {
            threat = origin.level().getEntitiesOfClass(
                            Mob.class,
                            origin.getBoundingBox().inflate(24.0D),
                            mob -> mob.getTarget() == origin
                                    && mob.isAlive()
                                    && (!(mob instanceof ChangedEntity changed)
                                            || NpcDispositionEvents.hasHostileDisposition(
                                                    changed, origin)))
                    .stream()
                    .findFirst()
                    .orElse(null);
        }
        if (threat == null) {
            origin.displayClientMessage(Component.translatable(
                    "message.changed_synergy.relationship.no_threat"), true);
            return;
        }
        int responders = 0;
        for (ChangedEntity creature : PlayerRelationshipSettings.loadedRelated(origin)) {
            if (creature.level() != origin.level()
                    || creature.distanceToSqr(origin) > 32.0D * 32.0D
                    || creature == threat
                    || !creature.canAttack(threat)
                    || threat instanceof ChangedEntity other
                            && (LatexCreatureCombatRules.areCompatriots(creature, other)
                                    || CreaturePersonality.hasTrustedRelationship(other, origin)
                                    || LatexSocialMemory.isBonded(other, origin))) {
                continue;
            }
            boolean bonded = LatexSocialMemory.isBonded(creature, origin)
                    || LatexSocialMemory.isPetOwner(creature, origin);
            if (bonded && !BondedOwnerDefenseGoal.allowsConfiguredDefense(
                    creature, origin, threat)) {
                continue;
            }
            LatexSocialMemory.authorizePetDefense(creature, threat, 240L);
            creature.setTarget(threat);
            creature.setAggressive(true);
            responders++;
        }
        origin.displayClientMessage(Component.translatable(
                responders > 0
                        ? "message.changed_synergy.relationship.help_answered"
                        : "message.changed_synergy.relationship.help_unanswered",
                responders), true);
    }

    private void mediate(ServerPlayer origin, CompoundTag payload) {
        if (!payload.hasUUID("contact")) {
            return;
        }
        ChangedEntity mediator = PlayerRelationshipSettings.findLoaded(
                origin, payload.getUUID("contact"));
        if (mediator == null || mediator.level() != origin.level()
                || !CreaturePersonality.hasTrustedRelationship(mediator, origin)) {
            origin.displayClientMessage(Component.translatable(
                    "message.changed_synergy.relationship.mediator_unavailable"), true);
            return;
        }
        String group = FactionReputation.groupId(mediator);
        int calmed = 0;
        for (ChangedEntity candidate : origin.level().getEntitiesOfClass(
                ChangedEntity.class,
                origin.getBoundingBox().inflate(16.0D),
                creature -> creature != mediator && creature.isAlive())) {
            if (!group.equals(FactionReputation.groupId(candidate))
                    || LatexSocialMemory.isProvoked(candidate, origin)
                    || FactionReputation.isHostile(candidate, origin)) {
                continue;
            }
            LatexSocialMemory.beginTruce(candidate, origin, 600L);
            LatexSocialMemory.clearHostilityToward(candidate, origin);
            candidate.setTarget(null);
            CreaturePersonality.adjustFamiliarityFromInteraction(
                    candidate, origin, 1);
            calmed++;
        }
        origin.displayClientMessage(Component.translatable(
                calmed > 0
                        ? "message.changed_synergy.relationship.mediation_success"
                        : "message.changed_synergy.relationship.mediation_none",
                mediator.getDisplayName(), calmed), true);
    }

    private void openAbilityWheel(ServerPlayer origin) {
        var variant = ProcessTransfur.getPlayerTransfurVariant(origin);
        if (variant == null || variant.isTemporaryFromSuit()) {
            return;
        }
        origin.openMenu(new SimpleMenuProvider(
                (id, inventory, viewer) -> new AbilityRadialMenu(id, inventory),
                AbilityRadialMenu.CONTAINER_TITLE));
    }

    private void synchronize(ServerPlayer origin) {
        contacts.clear();
        contacts.addAll(PlayerRelationshipSettings.contacts(origin));
        CompoundTag payload = new CompoundTag();
        payload.putString("command", "sync");
        payload.putInt("formation", formation.ordinal());
        payload.putInt("damage_filter", damageFilter.ordinal());
        ListTag list = new ListTag();
        for (Contact contact : contacts) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("uuid", contact.uuid());
            tag.putBoolean("following", contact.following());
            tag.putBoolean("loaded", contact.loaded());
            tag.putBoolean("sameDimension", contact.sameDimension());
            tag.putString("dimension", contact.dimension());
            tag.putBoolean("hasLastLocation", contact.hasLastLocation());
            tag.putInt("lastX", contact.lastX());
            tag.putInt("lastY", contact.lastY());
            tag.putInt("lastZ", contact.lastZ());
            tag.putFloat("health", contact.health());
            tag.putFloat("maxHealth", contact.maxHealth());
            tag.putInt("familiarity", contact.familiarity());
            tag.putBoolean("pureWhiteAdapted", contact.pureWhiteAdapted());
            list.add(tag);
        }
        payload.put("contacts", list);
        setDirty(payload);
    }

    private void applyClientSync(CompoundTag payload) {
        formation = enumByOrdinal(
                Formation.values(), payload.getInt("formation"), Formation.STANDARD);
        damageFilter = enumByOrdinal(
                DamageFilter.values(), payload.getInt("damage_filter"),
                DamageFilter.FRIENDS);
        ListTag list = payload.getList("contacts", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag update = list.getCompound(index);
            if (!update.hasUUID("uuid")) {
                continue;
            }
            UUID uuid = update.getUUID("uuid");
            for (int contactIndex = 0; contactIndex < contacts.size(); contactIndex++) {
                Contact old = contacts.get(contactIndex);
                if (!old.uuid().equals(uuid)) {
                    continue;
                }
                contacts.set(contactIndex, new Contact(
                        old.uuid(), old.entityId(), old.name(), old.typeId(),
                        update.getString("dimension"),
                        update.getBoolean("hasLastLocation"),
                        update.getInt("lastX"), update.getInt("lastY"),
                        update.getInt("lastZ"), old.bonded(), update.getBoolean("loaded"),
                        update.getBoolean("sameDimension"),
                        update.getBoolean("following"),
                        update.getBoolean("pureWhiteAdapted"),
                        update.getInt("familiarity"),
                        update.getFloat("health"),
                        update.getFloat("maxHealth")));
                break;
            }
        }
    }

    private static void writeOpeningData(
            FriendlyByteBuf buffer,
            ServerPlayer player,
            List<Contact> contacts) {
        var variant = ProcessTransfur.getPlayerTransfurVariant(player);
        boolean transfurred = variant != null && !variant.isTemporaryFromSuit();
        boolean organic = transfurred
                && !variant.getParent().getEntityType()
                        .is(ChangedTags.EntityTypes.LATEX);
        buffer.writeBoolean(transfurred);
        buffer.writeBoolean(organic);
        boolean absorption = InvoluntaryTransfurNegotiation
                .hasAbsorptionClaim(player);
        buffer.writeBoolean(absorption);
        buffer.writeBoolean(absorption && !hasFreeAbilitySlot(player));
        buffer.writeVarInt(PlayerRelationshipSettings.formation(player).ordinal());
        buffer.writeVarInt(PlayerRelationshipSettings.damageFilter(player).ordinal());
        buffer.writeVarInt(contacts.size());
        contacts.forEach(contact -> writeContact(buffer, contact));
    }

    public static boolean hasFreeAbilitySlot(@Nullable ServerPlayer player) {
        if (player == null) {
            return false;
        }
        var variant = ProcessTransfur.getPlayerTransfurVariant(player);
        if (variant == null || variant.isTemporaryFromSuit()) {
            return false;
        }
        long visibleAbilities = variant.abilityInstances.keySet().stream()
                .filter(ability -> ability != ChangedAbilities.SELECT_HAIRSTYLE.get()
                        || ability.canUse(IAbstractChangedEntity.forPlayer(player)))
                .count();
        return visibleAbilities < 8L;
    }

    private static void writeContact(FriendlyByteBuf buffer, Contact contact) {
        buffer.writeUUID(contact.uuid());
        buffer.writeVarInt(contact.entityId() + 1);
        buffer.writeUtf(contact.name(), 96);
        buffer.writeUtf(contact.typeId(), 128);
        buffer.writeUtf(contact.dimension(), 128);
        buffer.writeBoolean(contact.hasLastLocation());
        buffer.writeInt(contact.lastX());
        buffer.writeInt(contact.lastY());
        buffer.writeInt(contact.lastZ());
        buffer.writeBoolean(contact.bonded());
        buffer.writeBoolean(contact.loaded());
        buffer.writeBoolean(contact.sameDimension());
        buffer.writeBoolean(contact.following());
        buffer.writeBoolean(contact.pureWhiteAdapted());
        buffer.writeInt(contact.familiarity());
        buffer.writeFloat(contact.health());
        buffer.writeFloat(contact.maxHealth());
    }

    private static Contact readContact(FriendlyByteBuf buffer) {
        return new Contact(
                buffer.readUUID(),
                buffer.readVarInt() - 1,
                buffer.readUtf(96),
                buffer.readUtf(128),
                buffer.readUtf(128),
                buffer.readBoolean(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readInt(),
                buffer.readFloat(),
                buffer.readFloat());
    }

    private static <T> T enumByOrdinal(T[] values, int ordinal, T fallback) {
        return ordinal >= 0 && ordinal < values.length
                ? values[ordinal] : fallback;
    }
}
