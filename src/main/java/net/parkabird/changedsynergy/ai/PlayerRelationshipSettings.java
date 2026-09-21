package net.parkabird.changedsynergy.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Player-owned settings and contact index used by the relationship wheel. */
public final class PlayerRelationshipSettings {
    private static final String ROOT = "ChangedSynergyRelationshipManager";
    private static final String CONTACTS = "Contacts";
    private static final String FORMATION = "Formation";
    private static final String DAMAGE_FILTER = "DamageFilter";
    private static final String SOCIAL_SIGNAL = "SocialSignal";
    private static final int MAX_CONTACTS = 64;

    private PlayerRelationshipSettings() {
    }

    public static Formation formation(ServerPlayer player) {
        CompoundTag data = data(player);
        return data.contains(FORMATION, Tag.TAG_INT)
                ? Formation.byOrdinal(data.getInt(FORMATION))
                : Formation.STANDARD;
    }

    public static Formation cycleFormation(ServerPlayer player) {
        Formation next = formation(player).next();
        data(player).putInt(FORMATION, next.ordinal());
        return next;
    }

    public static DamageFilter damageFilter(ServerPlayer player) {
        CompoundTag settings = data(player);
        return settings.contains(DAMAGE_FILTER, Tag.TAG_INT)
                ? DamageFilter.byOrdinal(settings.getInt(DAMAGE_FILTER))
                : DamageFilter.FRIENDS;
    }

    public static DamageFilter cycleDamageFilter(ServerPlayer player) {
        DamageFilter next = damageFilter(player).next();
        data(player).putInt(DAMAGE_FILTER, next.ordinal());
        return next;
    }

    public static int nextSocialSignal(ServerPlayer player, int count) {
        int current = Math.floorMod(data(player).getInt(SOCIAL_SIGNAL), Math.max(1, count));
        data(player).putInt(SOCIAL_SIGNAL, current + 1);
        return current;
    }

    /** Active pats use their ordinary social rules; damage filtering is separate. */
    public static boolean allowsActivePat(ServerPlayer player, ChangedEntity creature) {
        return true;
    }

    public static double bondedStartDistance(ServerPlayer player) {
        return switch (formation(player)) {
            case CLOSE -> 7.0D;
            case STANDARD -> 10.0D;
            case LOOSE -> 13.0D;
        };
    }

    public static double bondedStopDistance(ServerPlayer player) {
        return switch (formation(player)) {
            case CLOSE -> 1.8D;
            case STANDARD -> 2.0D;
            case LOOSE -> 4.0D;
        };
    }

    public static double friendStartDistance(ServerPlayer player) {
        return switch (formation(player)) {
            case CLOSE -> 7.0D;
            case STANDARD -> 9.0D;
            case LOOSE -> 12.0D;
        };
    }

    public static double friendStopDistance(ServerPlayer player) {
        return switch (formation(player)) {
            case CLOSE -> 3.0D;
            case STANDARD -> 4.5D;
            case LOOSE -> 6.5D;
        };
    }

    /** Refreshes a durable summary without making an unloaded entity artificially persistent. */
    public static void rememberContact(
            ServerPlayer player,
            ChangedEntity creature,
            boolean bonded) {
        CompoundTag contacts = contactData(player);
        String key = creature.getStringUUID();
        if (!creature.isAlive() || BondedCreatureDeathData.get(player.server)
                .isDead(player.getUUID(), creature.getUUID())) {
            contacts.remove(key);
            return;
        }
        if (!CreatureSocialProfile.allowsSynergySystems(creature)) {
            contacts.remove(key);
            return;
        }
        CompoundTag contact = contacts.contains(key, Tag.TAG_COMPOUND)
                ? contacts.getCompound(key) : new CompoundTag();
        contact.putString("Name", creature.getDisplayName().getString());
        ResourceLocation type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getKey(creature.getType());
        contact.putString("Type", type.toString());
        contact.putBoolean("Bonded", bonded);
        contact.putString("Dimension", creature.level().dimension().location().toString());
        contact.putInt("X", creature.blockPosition().getX());
        contact.putInt("Y", creature.blockPosition().getY());
        contact.putInt("Z", creature.blockPosition().getZ());
        contact.putBoolean("PureWhiteAdapted",
                PureWhiteWolfAdaptation.isAdaptedForm(creature));
        contact.putLong("LastSeen", creature.level().getGameTime());
        contact.putInt("Familiarity", CreaturePersonality.familiarity(creature, player));
        contacts.put(key, contact);
        pruneContacts(contacts);
    }

    public static void forgetContact(ServerPlayer player, UUID creatureId) {
        contactData(player).remove(creatureId.toString());
    }

    /** True when the player's durable relationship index remembers this individual. */
    public static boolean hasRememberedContact(
            ServerPlayer player,
            UUID creatureId) {
        return contactData(player).contains(
                creatureId.toString(), Tag.TAG_COMPOUND);
    }

    /** Moves a durable relationship-card entry when Changed replaces a creature entity. */
    public static void replaceContactReference(
            ServerPlayer player,
            UUID previousId,
            UUID replacementId) {
        if (previousId.equals(replacementId)) {
            return;
        }
        CompoundTag contacts = contactData(player);
        String previousKey = previousId.toString();
        String replacementKey = replacementId.toString();
        if (contacts.contains(previousKey, Tag.TAG_COMPOUND)
                && !contacts.contains(replacementKey, Tag.TAG_COMPOUND)) {
            contacts.put(replacementKey, contacts.getCompound(previousKey).copy());
        }
        contacts.remove(previousKey);
    }

    /** Moves the card and immediately refreshes its form/name from the loaded replacement. */
    public static void replaceContactReference(
            ServerPlayer player,
            UUID previousId,
            ChangedEntity replacement,
            boolean related) {
        replaceContactReference(player, previousId, replacement.getUUID());
        if (related) {
            rememberContact(
                    player,
                    replacement,
                    LatexSocialMemory.isBonded(replacement, player)
                            || LatexSocialMemory.isPetOwner(replacement, player));
        }
    }

    /** Includes durable summaries, then overlays every currently loaded relationship. */
    public static List<Contact> contacts(ServerPlayer player) {
        Map<UUID, Contact> merged = new LinkedHashMap<>();
        CompoundTag stored = contactData(player);
        BondedCreatureDeathData deaths = BondedCreatureDeathData.get(player.server);
        for (String key : new ArrayList<>(stored.getAllKeys())) {
            try {
                UUID uuid = UUID.fromString(key);
                if (deaths.isDead(player.getUUID(), uuid)) {
                    stored.remove(key);
                    continue;
                }
                CompoundTag tag = stored.getCompound(key);
                ResourceLocation type = ResourceLocation.tryParse(
                        tag.getString("Type"));
                if (CreatureSocialProfile.isPermanentlyExcluded(type)) {
                    stored.remove(key);
                    continue;
                }
                merged.put(uuid, Contact.fromStored(uuid, tag));
            } catch (IllegalArgumentException ignored) {
                stored.remove(key);
            }
        }

        for (var level : player.server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof ChangedEntity creature)
                        || !creature.isAlive()) {
                    continue;
                }
                boolean bonded = LatexSocialMemory.isBonded(creature, player)
                        || LatexSocialMemory.isPetOwner(creature, player);
                boolean related = bonded
                        || CreaturePersonality.hasEstablishedRelationship(creature, player);
                if (!related) {
                    continue;
                }
                rememberContact(player, creature, bonded);
                merged.put(creature.getUUID(), Contact.fromLoaded(
                        player, creature, bonded));
            }
        }

        Comparator<Contact> order = Comparator
                .comparing(Contact::bonded).reversed()
                .thenComparing(Comparator.comparing(
                        (Contact contact) -> contact.loaded()).reversed())
                .thenComparing(Comparator.comparing(
                        (Contact contact) -> contact.following()).reversed())
                .thenComparing(Comparator.comparingInt(Contact::familiarity).reversed())
                .thenComparing(Contact::name, String.CASE_INSENSITIVE_ORDER);
        return merged.values().stream()
                .sorted(order)
                .limit(MAX_CONTACTS)
                .toList();
    }

    @Nullable
    public static ChangedEntity findLoaded(ServerPlayer player, UUID uuid) {
        if (BondedCreatureDeathData.get(player.server).isDead(player.getUUID(), uuid)) {
            forgetContact(player, uuid);
            return null;
        }
        for (var level : player.server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof ChangedEntity creature
                    && creature.isAlive()
                    && CreatureSocialProfile.allowsSynergySystems(creature)) {
                return creature;
            }
        }
        return null;
    }

    public static List<ChangedEntity> loadedRelated(ServerPlayer player) {
        List<ChangedEntity> result = new ArrayList<>();
        for (var level : player.server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof ChangedEntity creature
                        && creature.isAlive()
                        && (LatexSocialMemory.isBonded(creature, player)
                                || LatexSocialMemory.isPetOwner(creature, player)
                                || CreaturePersonality.hasTrustedRelationship(creature, player))) {
                    result.add(creature);
                }
            }
        }
        return result;
    }


    private static void pruneContacts(CompoundTag contacts) {
        if (contacts.size() <= MAX_CONTACTS) {
            return;
        }
        List<String> keys = new ArrayList<>(contacts.getAllKeys());
        keys.sort(Comparator.comparingLong(
                key -> contacts.getCompound(key).getLong("LastSeen")));
        for (int index = 0; index < keys.size() - MAX_CONTACTS; index++) {
            contacts.remove(keys.get(index));
        }
    }

    private static CompoundTag contactData(ServerPlayer player) {
        CompoundTag data = data(player);
        if (!data.contains(CONTACTS, Tag.TAG_COMPOUND)) {
            data.put(CONTACTS, new CompoundTag());
        }
        return data.getCompound(CONTACTS);
    }

    private static CompoundTag data(ServerPlayer player) {
        CompoundTag persisted = player.getPersistentData();
        if (!persisted.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            persisted.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        CompoundTag root = persisted.getCompound(Player.PERSISTED_NBT_TAG);
        if (!root.contains(ROOT, Tag.TAG_COMPOUND)) {
            root.put(ROOT, new CompoundTag());
        }
        return root.getCompound(ROOT);
    }

    public enum Formation {
        CLOSE,
        STANDARD,
        LOOSE;

        public Formation next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public String translationKey() {
            return "menu.changed_synergy.relationship.formation."
                    + name().toLowerCase(Locale.ROOT);
        }

        private static Formation byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < values().length
                    ? values()[ordinal] : STANDARD;
        }
    }

    public enum DamageFilter {
        FRIENDS,
        ALL,
        NONE;

        public DamageFilter next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public String translationKey() {
            return "menu.changed_synergy.relationship.damage_filter."
                    + name().toLowerCase(Locale.ROOT);
        }

        private static DamageFilter byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < values().length
                    ? values()[ordinal] : FRIENDS;
        }
    }

    public record Contact(
            UUID uuid,
            int entityId,
            String name,
            String typeId,
            String dimension,
            boolean hasLastLocation,
            int lastX,
            int lastY,
            int lastZ,
            boolean bonded,
            boolean loaded,
            boolean sameDimension,
            boolean following,
            boolean pureWhiteAdapted,
            int familiarity,
            float health,
            float maxHealth) {
        private static Contact fromLoaded(
                ServerPlayer player,
                ChangedEntity creature,
                boolean bonded) {
            boolean following = bonded
                    ? LatexSocialMemory.isFollowingOwner(creature)
                    : CreaturePersonality.isSocialFollowing(creature, player);
            return new Contact(
                    creature.getUUID(),
                    creature.getId(),
                    creature.getDisplayName().getString(),
                    net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                            .getKey(creature.getType()).toString(),
                    creature.level().dimension().location().toString(),
                    true,
                    creature.blockPosition().getX(),
                    creature.blockPosition().getY(),
                    creature.blockPosition().getZ(),
                    bonded,
                    true,
                    creature.level() == player.level(),
                    following,
                    PureWhiteWolfAdaptation.isAdaptedForm(creature),
                    CreaturePersonality.familiarity(creature, player),
                    creature.getHealth(),
                    creature.getMaxHealth());
        }

        private static Contact fromStored(UUID uuid, CompoundTag tag) {
            return new Contact(
                    uuid,
                    -1,
                    tag.getString("Name"),
                    tag.getString("Type"),
                    tag.getString("Dimension"),
                    tag.contains("X", Tag.TAG_INT)
                            && tag.contains("Y", Tag.TAG_INT)
                            && tag.contains("Z", Tag.TAG_INT),
                    tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"),
                    tag.getBoolean("Bonded"),
                    false,
                    false,
                    false,
                    tag.getBoolean("PureWhiteAdapted"),
                    tag.getInt("Familiarity"),
                    0.0F,
                    0.0F);
        }
    }

}
