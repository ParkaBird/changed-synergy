package net.parkabird.changedsynergy.regression;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.ai.DarkLatexCaveHarvestGoal;
import net.ltxprogrammer.changed.entity.ai.DarkLatexFishingGoal;
import net.ltxprogrammer.changed.entity.beast.AbstractDarkLatexEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.world.inventory.BondedCreatureInventory;

/** Execute block destruction and complete cast cycles, not just speed calculations. */
final class CompanionToolChecks {
    private static int checks;
    static void run(ServerPlayer owner) throws Exception {
        var level = owner.serverLevel();
        var mob = (ChangedEntity)ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.parse("changed:latex_hypno_cat")).create(level);
        mob.setPos(owner.position()); mob.setOnGround(true); mob.setNoAi(true); level.addFreshEntity(mob);
        var dark = (AbstractDarkLatexEntity)ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.parse("changed:dark_latex_wolf_male")).create(level);
        dark.setPos(owner.position()); dark.setOnGround(true); dark.setNoAi(true); level.addFreshEntity(dark);
        LatexSocialMemory.addBond(dark, owner);
        BlockPos ore = mob.blockPosition().offset(1, 0, 0), water = ore.offset(0, 0, 1);
        try {
            int wood = mine(mob, ore, new ItemStack(Items.WOODEN_PICKAXE), Blocks.COAL_ORE);
            int iron = mine(mob, ore, new ItemStack(Items.IRON_PICKAXE), Blocks.COAL_ORE);
            int diamond = mine(mob, ore, new ItemStack(Items.DIAMOND_PICKAXE), Blocks.COAL_ORE);
            var efficient = new ItemStack(Items.DIAMOND_PICKAXE); efficient.enchant(Enchantments.BLOCK_EFFICIENCY, 5);
            int enchanted = mine(mob, ore, efficient, Blocks.COAL_ORE);
            check(wood > iron && iron > diamond && diamond > enchanted,
                    "Real fallback coal mining scales with wood/iron/diamond/Efficiency V: " + wood + "/" + iron + "/" + diamond + "/" + enchanted);
            var silk = new ItemStack(Items.DIAMOND_PICKAXE); silk.enchant(Enchantments.SILK_TOUCH, 1);
            mine(mob, ore, silk, Blocks.DIAMOND_ORE);
            var inventory = new BondedCreatureInventory(mob);
            check(inventory.findItem(Items.DIAMOND_ORE) >= 0 && inventory.findItem(Items.DIAMOND) < 0,
                    "Silk Touch preserves the ore block in actual companion loot");
            inventory.clearContent(); inventory.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE)); inventory.setItem(1, efficient.copy());
            check(inventory.findMiningTool(Blocks.COAL_ORE.defaultBlockState()) == 1, "Tool choice recognises efficiency on stored tools");
            for (int lure = 0; lure <= 3; lure++) {
                int ticks = fish(mob, water, lure);
                check(ticks == 160 - 40 * lure, "Fallback complete cast applies Lure " + lure + " in " + ticks + " ticks");
                nativeFish(dark, water, lure);
            }
            var nativeMine = new DarkLatexCaveHarvestGoal(dark, .3, 8, 4);
            field(nativeMine, "inventory").set(nativeMine, dark.getInventory());
            dark.getInventory().selected = 0;
            dark.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_PICKAXE));
            float weak = nativeMine.getDestroyProgress(Blocks.COAL_ORE.defaultBlockState(), level, ore);
            dark.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_PICKAXE));
            float strong = nativeMine.getDestroyProgress(Blocks.COAL_ORE.defaultBlockState(), level, ore);
            dark.setItemSlot(EquipmentSlot.MAINHAND, efficient.copy());
            float fast = nativeMine.getDestroyProgress(Blocks.COAL_ORE.defaultBlockState(), level, ore);
            check(weak < strong && strong < fast, "Native Changed pet mining already honours tier and Efficiency");
            addonTools(owner, ore, water, efficient);
            friendTools(mob, dark, owner, ore, water);
            System.out.println("COMPANION_TOOL_REGRESSION_PASS: " + checks + " checks");
        } finally {
            mob.discard(); LatexSocialMemory.unregisterBond(dark, owner); dark.discard();
            level.removeBlock(ore, false); level.removeBlock(water, false);
        }
    }

    private static void friendTools(ChangedEntity mob, AbstractDarkLatexEntity dark, ServerPlayer owner,
            BlockPos ore, BlockPos water) throws Exception {
        CreaturePersonality.establishRelationship(mob, owner);
        var inventory = new BondedCreatureInventory(mob);
        inventory.clearContent();
        var silk = new ItemStack(Items.DIAMOND_PICKAXE);
        silk.enchant(Enchantments.SILK_TOUCH, 1); silk.enchant(Enchantments.BLOCK_EFFICIENCY, 5);
        inventory.setItem(0, silk);
        mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
        var routine = new CreatureRoutineGoal(mob);
        field(routine, "state").set(routine, CreatureLifeMemory.RoutineState.MINING);
        field(routine, "blockTarget").set(routine, ore);
        mob.level().setBlock(ore, Blocks.DIAMOND_ORE.defaultBlockState(), 3);
        routine.start();
        check(mob.getMainHandItem().is(Items.DIAMOND_PICKAXE), "Unowned friend displays the supplied pickaxe instead of a generated iron pickaxe");
        var tickMining = CreatureRoutineGoal.class.getDeclaredMethod("tickMining"); tickMining.setAccessible(true);
        int ticks = 0;
        while (mob.level().getBlockState(ore).is(Blocks.DIAMOND_ORE) && ++ticks <= 200) tickMining.invoke(routine);
        check(ticks == 3 && CreatureSettlementService.cargo(mob).is(Items.DIAMOND_ORE),
                "Friend's real routine uses supplied Efficiency V and Silk Touch");
        check(new BondedCreatureInventory(mob).storageItem(0).getDamageValue() == 1
                && mob.getMainHandItem().is(Items.STICK) && LatexSocialMemory.petOwnerUuid(mob).isEmpty(),
                "Friend keeps actual tool wear and original hand equipment without becoming a pet");
        routine.stop(); CreatureSettlementService.takeCargo(mob);

        inventory = new BondedCreatureInventory(mob); inventory.clearContent();
        var rod = new ItemStack(Items.FISHING_ROD); rod.enchant(Enchantments.FISHING_SPEED, 3); rod.enchant(Enchantments.FISHING_LUCK, 3);
        inventory.setItem(0, rod);
        routine = new CreatureRoutineGoal(mob);
        field(routine, "state").set(routine, CreatureLifeMemory.RoutineState.FISHING);
        field(routine, "fishingSite").set(routine, new CreatureSettlementService.FishingSite(mob.blockPosition(), water));
        mob.level().setBlock(water, Blocks.WATER.defaultBlockState(), 3);
        routine.start();
        check(net.minecraft.world.item.enchantment.EnchantmentHelper.getFishingLuckBonus(mob.getMainHandItem()) == 3,
                "Friend displays the supplied enchanted rod");
        var tickFishing = CreatureRoutineGoal.class.getDeclaredMethod("tickFishing"); tickFishing.setAccessible(true);
        ticks = 0;
        while (new BondedCreatureInventory(mob).storageItem(0).getDamageValue() == 0 && ++ticks <= 200) tickFishing.invoke(routine);
        check(ticks == 40 && new BondedCreatureInventory(mob).storageItem(0).getDamageValue() == 1,
                "Friend's complete cast uses supplied Lure III and wears the real rod");
        routine.stop(); CreatureSettlementService.takeCargo(mob);

        // Dark-latex presentation physically occupies its selected storage slot.
        // Restoring it must preserve wear, including a broken player tool.
        dark.getInventory().clearContent(); dark.getInventory().selected = 0;
        dark.setItemSlot(EquipmentSlot.MAINHAND, silk.copy());
        mob.level().setBlock(ore, Blocks.DIAMOND_ORE.defaultBlockState(), 3);
        GatheringToolPresentation.show(dark, CompanionToolEffects.gatheringPickaxe(dark, Blocks.DIAMOND_ORE.defaultBlockState()).stack());
        check(CreatureSettlementService.mineOre(dark, ore), "Dark-latex gathering completes with the displayed supplied tool");
        GatheringToolPresentation.restore(dark);
        check(dark.getInventory().getItem(0).getDamageValue() == silk.getDamageValue() + 1,
                "Native inventory presentation does not undo supplied pickaxe durability");
        CreatureSettlementService.takeCargo(dark);
        var fragile = new ItemStack(Items.FISHING_ROD); fragile.setDamageValue(fragile.getMaxDamage() - 1);
        dark.getInventory().setItem(0, fragile);
        GatheringToolPresentation.show(dark, CompanionToolEffects.gatheringRod(dark).stack());
        CreatureSettlementService.catchFish(dark, water);
        GatheringToolPresentation.restore(dark);
        check(dark.getInventory().getItem(0).isEmpty(), "Broken supplied rod is not restored or duplicated");
        check(CompanionToolEffects.gatheringRod(dark).inventory() == null,
                "Empty inventory falls back to the NPC's own rod");
        CreatureSettlementService.takeCargo(dark);
    }

    private static int mine(ChangedEntity mob, BlockPos pos, ItemStack tool, net.minecraft.world.level.block.Block block) throws Exception {
        var inventory = new BondedCreatureInventory(mob); inventory.clearContent(); inventory.setItem(0, tool.copy());
        mob.level().setBlock(pos, block.defaultBlockState(), 3);
        var goal = new FallbackBondedWorkGoal(mob);
        field(goal, "blockTarget").set(goal, pos); field(goal, "toolSlot").setInt(goal, 0);
        var tick = FallbackBondedWorkGoal.class.getDeclaredMethod("tickMining"); tick.setAccessible(true);
        int ticks = 0;
        while (mob.level().getBlockState(pos).is(block) && ++ticks <= 250) tick.invoke(goal);
        check(!mob.level().getBlockState(pos).is(block) && new BondedCreatureInventory(mob).storageItem(0).getDamageValue() == 1,
                "Successful " + tool.getHoverName().getString() + " harvest damages only the stored tool once");
        return ticks;
    }

    private static int fish(ChangedEntity mob, BlockPos pos, int lure) throws Exception {
        var rod = new ItemStack(Items.FISHING_ROD);
        if (lure > 0) rod.enchant(Enchantments.FISHING_SPEED, lure);
        rod.enchant(Enchantments.FISHING_LUCK, 3);
        var inventory = new BondedCreatureInventory(mob); inventory.clearContent(); inventory.setItem(0, rod);
        mob.level().setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
        var goal = new FallbackBondedWorkGoal(mob);
        field(goal, "fishingSite").set(goal, new CreatureSettlementService.FishingSite(mob.blockPosition(), pos));
        field(goal, "toolSlot").setInt(goal, 0);
        var tick = FallbackBondedWorkGoal.class.getDeclaredMethod("tickFishing"); tick.setAccessible(true);
        int ticks = 0;
        while (new BondedCreatureInventory(mob).storageItem(0).getDamageValue() == 0 && ++ticks <= 200) tick.invoke(goal);
        check(new BondedCreatureInventory(mob).storageItem(0).getDamageValue() == 1, "Fishing damages the original rod once");
        return ticks;
    }

    private static void nativeFish(AbstractDarkLatexEntity mob, BlockPos water, int lure) throws Exception {
        var rod = new ItemStack(Items.FISHING_ROD); if (lure > 0) rod.enchant(Enchantments.FISHING_SPEED, lure);
        mob.getInventory().selected = 0; mob.setItemSlot(EquipmentSlot.MAINHAND, rod);
        var goal = new DarkLatexFishingGoal(mob, .3, 8, 4);
        field(goal, "blockPos").set(goal, mob.blockPosition().below());
        field(goal, "targetWaterSurface").set(goal, water);
        int ticks = 0;
        while (rod.getDamageValue() == 0 && ++ticks <= 200) goal.tick();
        check(rod.getDamageValue() == 1 && ticks == 160 - 40 * lure,
                "Native Changed complete cast applies Lure " + lure + " in " + ticks + " ticks");
    }

    private static void addonTools(ServerPlayer owner, BlockPos ore, BlockPos water, ItemStack efficient) throws Exception {
        var id = ResourceLocation.parse("changed_addon:snow_leopard_male_organic");
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(id)) return;
        var mob = (ChangedEntity)ForgeRegistries.ENTITY_TYPES.getValue(id).create(owner.level());
        mob.setPos(owner.position()); mob.setOnGround(true); mob.setNoAi(true); owner.serverLevel().addFreshEntity(mob);
        LatexSocialMemory.addBond(mob, owner);
        try {
            Class<?> backend = Class.forName("net.foxyas.changedaddon.entity.api.TamableLatexEntityFavors");
            Object inventory = backend.getMethod("getInventory").invoke(mob);
            if (inventory == null) {
                inventory = backend.getMethod("createInventory").invoke(mob);
                backend.getMethod("setInventory", inventory.getClass()).invoke(mob, inventory);
            }
            field(inventory, "selected").setInt(inventory, 0);
            Class<?> miningClass = Class.forName("net.foxyas.changedaddon.entity.ai.LatexCaveHarvestGoal");
            Object mining = miningClass.getConstructor(backend, double.class, int.class, int.class).newInstance(mob, .3, 8, 4);
            field(mining, "inventory").set(mining, inventory);
            var progress = miningClass.getMethod("getDestroyProgress", net.minecraft.world.level.block.state.BlockState.class,
                    net.minecraft.world.level.BlockGetter.class, BlockPos.class);
            var speeds = new ArrayList<Float>();
            for (var tool : List.of(new ItemStack(Items.WOODEN_PICKAXE), new ItemStack(Items.DIAMOND_PICKAXE), efficient.copy())) {
                mob.setItemSlot(EquipmentSlot.MAINHAND, tool);
                speeds.add((Float)progress.invoke(mining, Blocks.COAL_ORE.defaultBlockState(), mob.level(), ore));
            }
            check(speeds.get(0) < speeds.get(1) && speeds.get(1) < speeds.get(2), "Native Addon pet mining honours tier and Efficiency");
            Class<?> fishingClass = Class.forName("net.foxyas.changedaddon.entity.ai.LatexFishingGoal");
            for (int lure = 0; lure <= 3; lure++) {
                var rod = new ItemStack(Items.FISHING_ROD); if (lure > 0) rod.enchant(Enchantments.FISHING_SPEED, lure);
                mob.setItemSlot(EquipmentSlot.MAINHAND, rod);
                var goal = (net.minecraft.world.entity.ai.goal.Goal)fishingClass.getConstructor(backend, double.class, int.class, int.class)
                        .newInstance(mob, .3, 8, 4);
                field(goal, "blockPos").set(goal, mob.blockPosition().below());
                field(goal, "targetWaterSurface").set(goal, water);
                int ticks = 0;
                while (rod.getDamageValue() == 0 && ++ticks <= 200) goal.tick();
                check(rod.getDamageValue() == 1 && ticks == 160 - 40 * lure,
                        "Native Addon complete cast applies Lure " + lure + " in " + ticks + " ticks");
            }
        } finally { LatexSocialMemory.unregisterBond(mob, owner); mob.discard(); }
    }

    private static Field field(Object value, String name) throws Exception {
        if (name.equals("blockPos") && value instanceof net.minecraft.world.entity.ai.goal.MoveToBlockGoal) {
            return net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(net.minecraft.world.entity.ai.goal.MoveToBlockGoal.class, "f_25602_");
        }
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            try { var result = type.getDeclaredField(name); result.setAccessible(true); return result; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static void check(boolean good, String message) {
        if (!good) throw new AssertionError(message);
        checks++; System.out.println("COMPANION_TOOL_PASS: " + message);
    }
}
