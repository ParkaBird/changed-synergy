package net.parkabird.changedsynergy.compat.curios;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.parkabird.changedsynergy.ChangedSynergyMod;

/** Optional Curios bridge whose public surface has no Curios class references. */
public final class CuriosCompat {
    private static final ResourceLocation EMPTY_ICON =
            ResourceLocation.fromNamespaceAndPath("curios", "slot/empty_curio_slot");

    private CuriosCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("curios");
    }

    public static List<SlotDescriptor> slotDescriptors(
            @Nullable Player player,
            @Nullable LivingEntity entity) {
        if (player == null || entity == null || !isLoaded()) {
            return List.of();
        }
        try {
            return CuriosApiBridge.slotsMatchingPlayer(player, entity).stream()
                    .map(SlotAccess::descriptor)
                    .toList();
        } catch (LinkageError | RuntimeException error) {
            ChangedSynergyMod.LOGGER.warn(
                    "Could not read Curios slots for {}",
                    entity.getType(),
                    error);
            return List.of();
        }
    }

    public static List<SlotAccess> resolveSlots(
            @Nullable LivingEntity entity,
            List<SlotDescriptor> descriptors) {
        List<SlotAccess> resolved = new ArrayList<>(descriptors.size());
        if (entity == null || !isLoaded()) {
            for (int index = 0; index < descriptors.size(); index++) {
                resolved.add(null);
            }
            return resolved;
        }
        try {
            Map<SlotDescriptor, SlotAccess> available = new HashMap<>();
            for (SlotAccess slot : CuriosApiBridge.slots(entity)) {
                available.put(slot.descriptor(), slot);
            }
            for (SlotDescriptor descriptor : descriptors) {
                resolved.add(available.get(descriptor));
            }
        } catch (LinkageError | RuntimeException error) {
            ChangedSynergyMod.LOGGER.warn(
                    "Could not resolve Curios slots for {}",
                    entity.getType(),
                    error);
            for (int index = resolved.size(); index < descriptors.size(); index++) {
                resolved.add(null);
            }
        }
        return resolved;
    }

    public static ResourceLocation slotIcon(String identifier) {
        if (!isLoaded()) {
            return EMPTY_ICON;
        }
        try {
            return CuriosApiBridge.slotIcon(identifier);
        } catch (LinkageError | RuntimeException ignored) {
            return EMPTY_ICON;
        }
    }

    public record SlotDescriptor(String identifier, int index) {
    }

    public record SlotAccess(
            SlotDescriptor descriptor,
            IItemHandlerModifiable handler,
            int handlerIndex) {
    }
}
