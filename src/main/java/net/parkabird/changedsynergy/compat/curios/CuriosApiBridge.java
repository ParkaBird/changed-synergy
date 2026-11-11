package net.parkabird.changedsynergy.compat.curios;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.ISlotType;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.common.inventory.CurioStacksHandler;

/** Loaded only when Curios is present. */
final class CuriosApiBridge {
    private CuriosApiBridge() {
    }

    static boolean openClothing(net.minecraft.server.level.ServerPlayer player,net.minecraft.world.item.ItemStack request) {
        var carried=player.getAbilities().instabuild?request:player.containerMenu.getCarried();
        player.containerMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
        net.minecraftforge.network.NetworkHooks.openScreen(player,new top.theillusivec4.curios.common.inventory.container.CuriosContainerProvider());
        player.containerMenu.setCarried(carried);
        player.containerMenu.broadcastChanges();
        return true;
    }

    static List<CuriosCompat.SlotAccess> slots(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity)
                .map(handler -> flatten(handler, CuriosApi.getEntitySlots(entity)))
                .orElseGet(List::of);
    }

    /**
     * Returns only the slot types and counts that are currently available to
     * the viewing player. Existing creature slots are never shrunk, so opening
     * the screen cannot discard equipment from an older save or datapack.
     */
    static List<CuriosCompat.SlotAccess> slotsMatchingPlayer(
            Player player,
            LivingEntity creature) {
        var playerOptional = CuriosApi.getCuriosInventory(player).resolve();
        var creatureOptional = CuriosApi.getCuriosInventory(creature).resolve();
        if (playerOptional.isEmpty() || creatureOptional.isEmpty()) {
            return List.of();
        }
        ICuriosItemHandler playerHandler = playerOptional.get();
        ICuriosItemHandler creatureHandler = creatureOptional.get();
        Map<String, ISlotType> playerTypes = CuriosApi.getPlayerSlots(player);
        List<ICurioStacksHandler> playerStacks = orderedHandlers(
                        playerHandler, playerTypes).stream()
                .filter(stacks->NativeClothingInventory.type(stacks.getIdentifier())==null)
                .filter(stacks -> playerTypes.containsKey(stacks.getIdentifier()))
                .filter(stacks -> playerTypes.get(stacks.getIdentifier()).isVisible())
                .filter(ICurioStacksHandler::isVisible)
                .toList();

        Map<String, ICurioStacksHandler> creatureStacks =
                new LinkedHashMap<>(creatureHandler.getCurios());
        boolean addedType = false;
        for (ICurioStacksHandler source : playerStacks) {
            String identifier = source.getIdentifier();
            ICurioStacksHandler target = creatureStacks.get(identifier);
            if (target == null) {
                ISlotType type = playerTypes.get(identifier);
                target = new CurioStacksHandler(
                        creatureHandler,
                        identifier,
                        source.getSlots(),
                        type.useNativeGui(),
                        type.hasCosmetic(),
                        type.canToggleRendering(),
                        type.getDropRule());
                creatureStacks.put(identifier, target);
                addedType = true;
            } else {
                int missing = source.getSlots() - target.getSlots();
                if (missing > 0) {
                    target.grow(missing);
                }
            }
        }
        if (addedType) {
            creatureHandler.setCurios(creatureStacks);
        }

        Map<CuriosCompat.SlotDescriptor, CuriosCompat.SlotAccess> available =
                new HashMap<>();
        for (CuriosCompat.SlotAccess access :
                flatten(creatureHandler, playerTypes)) {
            available.put(access.descriptor(), access);
        }

        List<CuriosCompat.SlotAccess> result = new ArrayList<>();
        for(var access:flatten(creatureHandler,playerTypes))if(NativeClothingInventory.type(access.descriptor().identifier())!=null)result.add(access);
        for (ICurioStacksHandler stacks : playerStacks) {
            for (int index = 0; index < stacks.getSlots(); index++) {
                CuriosCompat.SlotDescriptor descriptor =
                        new CuriosCompat.SlotDescriptor(
                                stacks.getIdentifier(), index);
                CuriosCompat.SlotAccess access = available.get(descriptor);
                if (access != null) {
                    result.add(access);
                }
            }
        }
        return List.copyOf(result);
    }

    private static List<CuriosCompat.SlotAccess> flatten(
            ICuriosItemHandler handler,
            Map<String, ISlotType> ordering) {
        List<ICurioStacksHandler> handlers = orderedHandlers(handler, ordering);
        List<CuriosCompat.SlotAccess> slots = new ArrayList<>();
        for (ICurioStacksHandler stacks : handlers) {
            for (int index = 0; index < stacks.getSlots(); index++) {
                slots.add(new CuriosCompat.SlotAccess(
                        new CuriosCompat.SlotDescriptor(
                                stacks.getIdentifier(), index),
                        stacks.getStacks(),
                        index));
            }
        }
        return List.copyOf(slots);
    }

    private static List<ICurioStacksHandler> orderedHandlers(
            ICuriosItemHandler handler,
            Map<String, ISlotType> ordering) {
        List<ICurioStacksHandler> handlers =
                new ArrayList<>(handler.getCurios().values());
        handlers.sort(Comparator
                .comparingInt((ICurioStacksHandler stacks) -> {
                    ISlotType type = ordering.get(stacks.getIdentifier());
                    return type == null ? Integer.MAX_VALUE : type.getOrder();
                })
                .thenComparing(ICurioStacksHandler::getIdentifier));
        return handlers;
    }

    static ResourceLocation slotIcon(String identifier) {
        return CuriosApi.getSlotIcon(identifier);
    }
    static boolean isEquipmentMenu(net.minecraft.world.inventory.AbstractContainerMenu menu){return menu instanceof top.theillusivec4.curios.api.type.ICuriosMenu;}
    static boolean clothingVisible(LivingEntity entity,net.ltxprogrammer.changed.data.AccessorySlotType type) {
        return CuriosApi.getCuriosInventory(entity).resolve().flatMap(h->h.getStacksHandler(NativeClothingInventory.identifier(type)))
                .map(h->h.getRenders().isEmpty() || h.getRenders().get(0)).orElse(true);
    }
}
