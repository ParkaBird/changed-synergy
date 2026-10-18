package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.parkabird.changedsynergy.ai.*;

final class PlayerPersistenceRegression {
    static void run(MinecraftServer server) {
        var original = new ServerPlayer(server, server.overworld(), new GameProfile(UUID.randomUUID(), "PersistOriginal"));
        var clone = new ServerPlayer(server, server.overworld(), original.getGameProfile());
        var keys = List.of("ChangedSynergyBondedCreatures", "ChangedSynergyBondedCreatureLocations",
                "ChangedSynergyReputation", "ChangedSynergyTelepathy", "ChangedSynergyFactionGrace",
                "ChangedSynergyFactionDamageLock", "ChangedSynergyFactionAggression");
        var source = new CompoundTag();
        for (String key : keys) {
            var entry = new CompoundTag(); entry.putString("LegacySentinel", key);
            source.put(key, entry);
        }
        source.putBoolean("ChangedSynergyBondRegistryScanned", true);
        original.getPersistentData().put(Player.PERSISTED_NBT_TAG, source);
        clone.getPersistentData().putString("ForeignRoot", "preserve");
        var target = new CompoundTag(); target.putInt("ForeignPersisted", 93);
        clone.getPersistentData().put(Player.PERSISTED_NBT_TAG, target);
        LatexSocialMemory.copyPlayerBondData(original, clone);
        BondedCreatureLifecycle.copyPlayerData(original, clone);
        FactionReputation.copyPlayerData(original, clone);
        TelepathyService.copyPlayerData(original, clone);
        FactionHostilityGrace.copyPlayerData(original, clone);
        for (String key : keys) {
            if (!source.getCompound(key).equals(target.getCompound(key)))
                throw new AssertionError("Death clone changed a stored namespace: " + key);
            target.getCompound(key).putInt("CloneMutation", 1);
            if (source.getCompound(key).contains("CloneMutation"))
                throw new AssertionError("Death clone shares mutable state with its previous player: " + key);
        }
        if (!target.getBoolean("ChangedSynergyBondRegistryScanned") || target.getInt("ForeignPersisted") != 93
                || !clone.getPersistentData().getString("ForeignRoot").equals("preserve"))
            throw new AssertionError("Clone lost the bond-scan flag or another owner's data");
        var formation = PlayerRelationshipSettings.cycleFormation(clone);
        var saved = clone.saveWithoutId(new CompoundTag());
        var loaded = new ServerPlayer(server, server.overworld(), clone.getGameProfile());
        loaded.load(saved);
        if (!target.equals(loaded.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG))
                || PlayerRelationshipSettings.formation(loaded) != formation)
            throw new AssertionError("Save/reload changed player namespaces or relationship settings");
        System.out.println("PLAYER_PERSISTENCE_REGRESSION_PASS: seven namespace clones, independent mutable data, foreign data and real save/reload");
    }
}
