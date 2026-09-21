package net.parkabird.changedsynergy.compat;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;

/** Small ownership bridge for Domestication Innovation's central TameableUtils API. */
public final class DomesticationInnovationCompat {
    private static final ClassValue<Optional<Method>> TAME_OWNER_UUID = new ClassValue<>() {
        @Override
        protected Optional<Method> computeValue(Class<?> type) {
            try {
                return Optional.of(type.getMethod("getTameOwnerUUID"));
            } catch (NoSuchMethodException ignored) {
                return Optional.empty();
            }
        }
    };

    private DomesticationInnovationCompat() {
    }

    public static boolean isCompatiblePet(Entity entity) {
        return entity instanceof ChangedEntity creature
                && LatexSocialMemory.isSocialLatex(creature)
                && LatexSocialMemory.petOwnerUuid(creature).isPresent();
    }

    public static boolean isPetOf(Player player, Entity entity) {
        return entity instanceof ChangedEntity creature
                && LatexSocialMemory.petOwnerUuid(creature)
                        .filter(player.getUUID()::equals)
                        .isPresent();
    }

    @Nullable
    public static UUID ownerUuid(Entity entity) {
        if (entity instanceof ChangedEntity creature) {
            return LatexSocialMemory.petOwnerUuid(creature).orElse(null);
        }
        if (entity instanceof Player player) {
            return player.getUUID();
        }
        if (entity instanceof TamableAnimal tameable) {
            return tameable.getOwnerUUID();
        }
        Optional<Method> accessor = TAME_OWNER_UUID.get(entity.getClass());
        if (accessor.isEmpty()) {
            return null;
        }
        try {
            Object value = accessor.get().invoke(entity);
            return value instanceof UUID uuid ? uuid : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    public static boolean hasSameOwner(Entity first, Entity second) {
        UUID firstOwner = ownerUuid(first);
        return firstOwner != null && firstOwner.equals(ownerUuid(second));
    }

    @Nullable
    public static Entity owner(Entity entity) {
        if (!(entity instanceof ChangedEntity creature)
                || !(creature.level() instanceof ServerLevel level)) {
            return null;
        }
        UUID ownerUuid = ownerUuid(creature);
        return ownerUuid == null ? null
                : level.getServer().getPlayerList().getPlayer(ownerUuid);
    }

    public static boolean setOwner(Entity entity, @Nullable UUID ownerUuid) {
        if (!(entity instanceof ChangedEntity creature)
                || !LatexSocialMemory.isSocialLatex(creature)) {
            return false;
        }
        LatexSocialMemory.setPetOwnerUuid(creature, ownerUuid);
        return true;
    }
}
