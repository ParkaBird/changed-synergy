package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.EyeStyle;
import net.ltxprogrammer.changed.entity.TransfurCause;
import net.ltxprogrammer.changed.entity.TransfurContext;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.ltxprogrammer.changed.init.ChangedLatexTypes;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.ltxprogrammer.changed.util.Color3;
import net.ltxprogrammer.changed.world.LatexCoverState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.*;

/** Reproductions of animation-time combat, physical render ghosts and adaptation. */
final class October18FeedbackRegression {
    static void run(MinecraftServer server) throws Exception {
        var level = server.overworld();
        var player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "AnimationCheck"));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player);
        player.tickCount = 100;
        net.minecraftforge.fml.util.ObfuscationReflectionHelper.setPrivateValue(ServerPlayer.class, player, 0, "f_8921_");
        var identity = new net.parkabird.changedsynergy.origin.NativeOriginData.Identity(); identity.state = "human";
        net.parkabird.changedsynergy.origin.NativeOriginData.get(server).players.put(player.getUUID(), identity);
        var source = ChangedEntities.GAS_WOLF_MALE.get().create(level);
        var witness = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        var spawn = level.getSharedSpawnPos();
        player.setPos(spawn.getX()+8.5, 160, spawn.getZ()+8.5); source.setPos(player.position()); witness.setPos(player.position().add(1,0,0));
        level.getChunkAt(player.blockPosition());
        check(level.addFreshEntity(source) && level.addFreshEntity(witness), "Real independent NPCs join the loaded test chunk");
        try {
            LatexSocialMemory.markProvoked(source, player);
            source.setTarget(player); source.setLastHurtByMob(player);
            witness.setTarget(player);
            var form = ProcessTransfur.setPlayerTransfurVariant(player, source.getSelfVariant(),
                    TransfurContext.npcLatexHazard(source, TransfurCause.GRAB_REPLICATE), .1F, false);
            check(source.getTarget() == null && source.getLastHurtByMob() == null && witness.getTarget() == null,
                    "Starting the real conversion clears both the source and nearby stale attackers before the animation ends");
            check(LatexSocialMemory.isSettledAfterSecondaryTransfur(source, player), "The completed incident stays settled after the temporary truce");
            source.setTarget(player); check(source.getTarget() == null, "Native target selection cannot restart combat during animation");
            float health = player.getHealth(); player.invulnerableTime = 0;
            player.hurt(player.damageSources().mobAttack(source), 8);
            check(player.getHealth() == health, "Animation-time organic damage cannot kill the transforming human");
            check(!level.addFreshEntity(form.getChangedEntity()), "A living human-origin render projection cannot become a named world NPC");
            player.setHealth(0);
            check(!level.addFreshEntity(form.getChangedEntity()), "A dead host's render projection cannot appear at the death or respawn site");
            check(!witness.isRemoved() && witness.getUnderlyingPlayer() == null, "An ordinary independent NPC remains intact");
        } finally { player.discard(); source.discard(); witness.discard(); }

        BlockPos position = new BlockPos(spawn.getX()+24, 160, spawn.getZ()+24);
        level.getChunkAt(position); level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 2);
        check(!PureWhiteWolfAdaptation.inHomeTerritory(level, position), "An ordinary uncoated stone floor is outside White territory");
        LatexCoverState.setAt(level, position.below(), ChangedLatexTypes.WHITE_LATEX.get().defaultCoverState(), 2);
        check(PureWhiteWolfAdaptation.inHomeTerritory(level, position), "White covering on an ordinary block counts as home territory");
        LatexCoverState.setAt(level, position.below(), ChangedLatexTypes.DARK_LATEX.get().defaultCoverState(), 2);
        check(!PureWhiteWolfAdaptation.inHomeTerritory(level, position), "Dark covering does not count as White territory");
        LatexCoverState.setAt(level, position.below(), ChangedLatexTypes.NONE.get().defaultCoverState(), 2);

        var pure = ChangedEntities.PURE_WHITE_LATEX_WOLF.get().create(level);
        pure.setPos(position.getX()+.5, position.getY(), position.getZ()+.5);
        pure.setCustomName(net.minecraft.network.chat.Component.literal("AdaptedEyeCheck"));
        pure.getBasicPlayerInfo().setEyeStyle(EyeStyle.values()[0]);
        pure.getBasicPlayerInfo().setLeftIrisColor(Color3.fromInt(0x44DD66));
        pure.getBasicPlayerInfo().setRightIrisColor(Color3.fromInt(0xDD9944));
        level.addFreshEntity(pure);
        var data = new CompoundTag(); data.putBoolean("Eligible", true); data.putLong("AwaySince", level.getGameTime()-300);
        pure.getPersistentData().put(PureWhiteWolfAdaptation.DATA_KEY, data);
        pure.tickCount = Math.floorMod(pure.getId(), 100);
        PureWhiteWolfAdaptation.onLivingTick(new LivingEvent.LivingTickEvent(pure));
        try {
            check(!pure.isRemoved() && pure.getType()==ChangedEntities.PURE_WHITE_LATEX_WOLF.get()
                    && PureWhiteWolfAdaptation.isAdaptedForm(pure), "Away adaptation adds synchronized eyes without replacing the pure-white species or UUID");
            check(pure.getBasicPlayerInfo().getLeftIrisColor().toInt() == 0x44DD66
                    && pure.getBasicPlayerInfo().getRightIrisColor().toInt() == 0xDD9944
                    && pure.getBasicPlayerInfo().getEyeStyle() == EyeStyle.values()[0], "Eye-only adaptation keeps individual eye colour and style");
            var updates = pure.getEntityData().packDirty();
            check(updates != null && updates.stream().anyMatch(d -> Boolean.TRUE.equals(d.value())), "Adapted eyes synchronize through native entity data");
            var saved = new CompoundTag(); pure.saveWithoutId(saved);
            var loaded = ChangedEntities.PURE_WHITE_LATEX_WOLF.get().create(level); loaded.load(saved); loaded.setUUID(UUID.randomUUID());
            check(level.addFreshEntity(loaded) && PureWhiteWolfAdaptation.isAdaptedForm(loaded), "Adapted NPC eyes survive save/load");
            loaded.discard();
            LatexCoverState.setAt(level, position.below(), ChangedLatexTypes.WHITE_LATEX.get().defaultCoverState(), 2);
            PureWhiteWolfAdaptation.onLivingTick(new LivingEvent.LivingTickEvent(pure));
            check(!PureWhiteWolfAdaptation.isAdaptedForm(pure), "Returning to White covering hides eyes on the same original body");
            LatexCoverState.setAt(level, position.below(), ChangedLatexTypes.NONE.get().defaultCoverState(), 2);
        } finally { pure.discard(); }
        for (var type : java.util.List.of(ChangedEntities.PURE_WHITE_LATEX_WOLF_PUP.get(),
                ChangedEntities.PURE_WHITE_LATEX_CERBERUS.get(), ChangedEntities.LATEX_MUTANT_BLOODCELL_WOLF.get())) {
            var creature = type.create(level); creature.setPos(position.getX()+.5,position.getY(),position.getZ()+.5);
            level.addFreshEntity(creature); UUID originalId = creature.getUUID();
            var away = new CompoundTag();away.putBoolean("Eligible",true);away.putLong("AwaySince",level.getGameTime()-300);
            creature.getPersistentData().put(PureWhiteWolfAdaptation.DATA_KEY,away);
            creature.tickCount=Math.floorMod(creature.getId(),100);
            PureWhiteWolfAdaptation.onLivingTick(new LivingEvent.LivingTickEvent(creature));
            check(creature.getType()==type && creature.getUUID().equals(originalId) && PureWhiteWolfAdaptation.isAdaptedForm(creature),
                    "Additional White form adapts without species/UUID changes: "+type);
            LatexCoverState.setAt(level,position.below(),ChangedLatexTypes.WHITE_LATEX.get().defaultCoverState(),2);
            PureWhiteWolfAdaptation.onLivingTick(new LivingEvent.LivingTickEvent(creature));
            check(!PureWhiteWolfAdaptation.isAdaptedForm(creature),"Additional White form hides eyes on returning: "+type);
            LatexCoverState.setAt(level,position.below(),ChangedLatexTypes.NONE.get().defaultCoverState(),2);
            creature.discard();
        }
        var legacyNpc = ChangedEntities.WHITE_LATEX_WOLF_MALE.get().create(level);
        legacyNpc.setPos(position.getX()+.5, position.getY(), position.getZ()+.5);
        legacyNpc.setCustomName(net.minecraft.network.chat.Component.literal("LegacyPureCheck"));
        legacyNpc.getBasicPlayerInfo().setLeftIrisColor(Color3.fromInt(0x44DD66));
        var marker = new CompoundTag(); marker.putBoolean("Eligible", true);
        legacyNpc.getPersistentData().put(PureWhiteWolfAdaptation.DATA_KEY, marker);
        level.addFreshEntity(legacyNpc);
        var restore = PureWhiteWolfAdaptation.class.getDeclaredMethod("restoreLegacyForm", net.ltxprogrammer.changed.entity.ChangedEntity.class);
        restore.setAccessible(true); restore.invoke(null, legacyNpc);
        var restoredPure = level.getEntitiesOfClass(net.ltxprogrammer.changed.entity.beast.PureWhiteLatexWolf.class,
                legacyNpc.getBoundingBox().inflate(3), e -> e.getName().getString().equals("LegacyPureCheck")).stream().findFirst().orElseThrow();
        check(restoredPure.getBasicPlayerInfo().getLeftIrisColor().toInt()==0x44DD66 && PureWhiteWolfAdaptation.isAdaptedForm(restoredPure),
                "Previously converted pure-white NPCs recover their species with individual eyes intact");
        restoredPure.discard();

        var deaths = BondedCreatureDeathData.get(server); UUID owner = UUID.randomUUID(), creature = UUID.randomUUID();
        CompoundTag notice = new CompoundTag(); notice.putString("Name", "LocationCheck");
        notice.putString("Dimension", "minecraft:the_nether"); notice.putLong("Position", new BlockPos(-31, 72, 95).asLong());
        deaths.markDead(owner, creature, notice); notice.putLong("Position", 0);
        check(deaths.isDead(owner, creature), "Location-bearing tombstones still prevent stale bond cards");
        var saved = deaths.save(new CompoundTag());
        var load = BondedCreatureDeathData.class.getDeclaredMethod("load", CompoundTag.class); load.setAccessible(true);
        var reloaded = (BondedCreatureDeathData)load.invoke(null, saved);
        var restored = reloaded.consume(owner).get(creature);
        check(restored.getLong("Position") == new BlockPos(-31,72,95).asLong()
                && restored.getString("Dimension").equals("minecraft:the_nether"), "Offline death position survives save/reload without following the player");
        check(reloaded.consume(owner).isEmpty(), "A death-location notification is consumed once");
        CompoundTag legacy = new CompoundTag(), pending = new CompoundTag(), entries = new CompoundTag();
        entries.putBoolean(creature.toString(), true); pending.put(owner.toString(), entries); legacy.put("Pending", pending);
        var oldData = (BondedCreatureDeathData)load.invoke(null, legacy);
        check(oldData.isDead(owner, creature) && oldData.consume(owner).get(creature).isEmpty(),
                "Old boolean death records still remove stale relationships without inventing a location");
        var sampleFactory = BondedRevivalService.class.getDeclaredMethod("revivalSample", UUID.class, UUID.class, String.class, String.class);
        sampleFactory.setAccessible(true);
        var sample = (net.minecraft.world.item.ItemStack)sampleFactory.invoke(null, UUID.randomUUID(), owner, "SampleName",
                source.getSelfVariant().getFormId().toString());
        var expected = net.minecraft.network.chat.Component.literal("SampleName (")
                .append(ChangedEntities.GAS_WOLF_MALE.get().getDescription()).append(")");
        var description = BondedRevivalService.bondSampleDescription(sample);
        check(description.getString().equals(expected.getString())
                && description.getSiblings().get(1).equals(ChangedEntities.GAS_WOLF_MALE.get().getDescription()),
                "A revival sample appends its localized species after the creature's name");
        sample.getTag().getCompound("ChangedSynergyBondRevival").putString("Form", "missing:removed_form");
        check(BondedRevivalService.bondSampleDescription(sample).getString().equals("SampleName"),
                "A removed species leaves the original sample name readable");
        System.out.println("OCTOBER18_FEEDBACK_REGRESSION_PASS");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        System.out.println("OCTOBER18_CHECK: " + message);
    }
}
