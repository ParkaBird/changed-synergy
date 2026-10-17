package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.ltxprogrammer.changed.init.ChangedItems;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.world.inventory.CentaurMountConfigMenu;

/** The reported tack-menu -> mount -> quadrupedal armour item-use transaction. */
final class MountedArmorRegression {
    static void run(MinecraftServer server) {
        var level = server.overworld();
        var player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "MountedArmor"));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player);
        var identity = new net.parkabird.changedsynergy.origin.NativeOriginData.Identity(); identity.state = "human";
        net.parkabird.changedsynergy.origin.NativeOriginData.get(server).players.put(player.getUUID(), identity);
        var mount = ChangedEntities.WHITE_LATEX_CENTAUR.get().create(level);
        try {
            player.setPos(180.5, 160, 180.5); mount.setPos(player.position()); level.addFreshEntity(mount);
            LatexSocialMemory.addBond(mount, player);
            var menu = new CentaurMountConfigMenu(1, player.getInventory(), mount);
            player.containerMenu = menu;
            menu.getSlot(0).set(new ItemStack(Items.SADDLE));
            if (!menu.clickMenuButton(player, CentaurMountConfigMenu.RIDE_BUTTON_ID) || player.getVehicle() != mount)
                throw new AssertionError("Reported saddle-menu ride sequence did not mount the white centaur");
            var item = ChangedItems.NETHERITE_QUADRUPEDAL_LEGGINGS.get();
            var leggings = new ItemStack(item); leggings.getOrCreateTag().putUUID("ArmorRegression", player.getUUID());
            // The supplied video uses Creative with a full inventory. Before
            // the fix, Creative retained the hand item while invalid-armour
            // cleanup returned a newly equipped copy as a real dropped entity.
            player.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.CREATIVE);
            for (int i=0; i<player.getInventory().items.size(); i++) player.getInventory().items.set(i, new ItemStack(Items.STONE, 64));
            player.setItemInHand(InteractionHand.MAIN_HAND, leggings);
            // Demonstrate the unprotected Changed bug in the base environment;
            // optional mods can replace native item-use and armour cleanup.
            if (!net.minecraftforge.fml.ModList.get().isLoaded("true_transfur")
                    && !net.minecraftforge.fml.ModList.get().isLoaded("changed_addon")) {
            MinecraftForge.EVENT_BUS.unregister(net.parkabird.changedsynergy.ai.CreatureArmorService.class);
            try {
                player.gameMode.useItem(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND);
                MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(player));
                int dropped = level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8), e -> e.getItem().is(item))
                        .stream().mapToInt(e -> e.getItem().getCount()).sum();
                if (player.getMainHandItem().getCount()!=1 || dropped!=1)
                    throw new AssertionError("Legacy Creative/full-inventory duplication was not reproduced: dropped="+dropped);
                System.out.println("MOUNTED_ARMOR_LEGACY_DUPLICATION_REPRODUCED: one original in hand plus one real dropped copy");
                player.getInventory().clearContent();
                player.setItemInHand(InteractionHand.MAIN_HAND, leggings.copy());
                player.gameMode.useItem(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND);
                MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(player));
                if (player.getInventory().countItem(item)!=2)
                    throw new AssertionError("Legacy empty-inventory duplication was not reproduced");
                System.out.println("MOUNTED_ARMOR_LEGACY_DUPLICATION_REPRODUCED: one original plus one returned inventory copy");
            } finally {
                MinecraftForge.EVENT_BUS.register(net.parkabird.changedsynergy.ai.CreatureArmorService.class);
                level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8), e -> e.getItem().is(item)).forEach(ItemEntity::discard);
                player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
                player.getInventory().clearContent();
                player.setItemInHand(InteractionHand.MAIN_HAND, leggings.copy());
            }
            }
            for (boolean creative : new boolean[]{true, false}) {
              player.gameMode.changeGameModeForPlayer(creative ? net.minecraft.world.level.GameType.CREATIVE : net.minecraft.world.level.GameType.SURVIVAL);
              for (int attempt=0; attempt<8; attempt++) {
                var result = player.gameMode.useItem(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND);
                if (result != net.minecraft.world.InteractionResult.FAIL || !player.getItemBySlot(EquipmentSlot.LEGS).isEmpty())
                    throw new AssertionError("Human right-click must reject quadrupedal armour before equipping/returning it");
                MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(player));
                int total = player.getInventory().countItem(item);
                for (var slot : EquipmentSlot.values()) total += mount.getItemBySlot(slot).is(item) ? mount.getItemBySlot(slot).getCount() : 0;
                total += level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8), e -> e.getItem().is(item))
                        .stream().mapToInt(e -> e.getItem().getCount()).sum();
                System.out.println("MOUNTED_ARMOR_TRACE: attempt="+attempt+" total="+total+" hand="+player.getMainHandItem()+" legs="+player.getItemBySlot(EquipmentSlot.LEGS));
                if (total != 1) throw new AssertionError("Mounted quadrupedal armour duplicated/lost, total="+total);
              }
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_LEGGINGS));
            player.gameMode.useItem(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND);
            if (!player.getItemBySlot(EquipmentSlot.LEGS).is(Items.IRON_LEGGINGS) || !player.getMainHandItem().isEmpty())
                throw new AssertionError("Riding must not disable ordinary human armour");
            player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
            net.ltxprogrammer.changed.process.ProcessTransfur.setPlayerTransfurVariant(player,
                    net.ltxprogrammer.changed.init.ChangedTransfurVariants.WHITE_LATEX_CENTAUR.get(), null, 1F);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
            player.gameMode.useItem(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND);
            MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(player));
            if (!player.getItemBySlot(EquipmentSlot.LEGS).is(item) || !player.getMainHandItem().isEmpty())
                throw new AssertionError("A genuine quadrupedal player must still equip the armour normally");
            System.out.println("MOUNTED_ARMOR_REGRESSION_PASS: white centaur tack menu, mounted human, sixteen real Creative/Survival item uses, ordinary armour and quadrupedal player use");
        } finally { player.stopRiding(); player.discard(); mount.discard(); }
    }
}
