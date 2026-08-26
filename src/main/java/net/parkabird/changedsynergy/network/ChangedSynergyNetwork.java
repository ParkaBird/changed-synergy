package net.parkabird.changedsynergy.network;

import java.util.Optional;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ChangedSynergyNetwork {
    private static final String PROTOCOL = "22";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(ChangedSynergyMod.MOD_ID, "network"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static boolean registered;

    private ChangedSynergyNetwork() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        CHANNEL.registerMessage(
                0,
                TransfurVisualPacket.class,
                TransfurVisualPacket::encode,
                TransfurVisualPacket::decode,
                TransfurVisualPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                1,
                FriendlySuitSyncPacket.class,
                FriendlySuitSyncPacket::encode,
                FriendlySuitSyncPacket::decode,
                FriendlySuitSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                2,
                GrabQteSyncPacket.class,
                GrabQteSyncPacket::encode,
                GrabQteSyncPacket::decode,
                GrabQteSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                3,
                BondedInventoryOpenPacket.class,
                BondedInventoryOpenPacket::encode,
                BondedInventoryOpenPacket::decode,
                BondedInventoryOpenPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(
                4,
                NativePetReassimilatePacket.class,
                NativePetReassimilatePacket::encode,
                NativePetReassimilatePacket::decode,
                NativePetReassimilatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(
                5,
                HypnosisQteSyncPacket.class,
                HypnosisQteSyncPacket::encode,
                HypnosisQteSyncPacket::decode,
                HypnosisQteSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                6,
                HypnosisQteInputPacket.class,
                HypnosisQteInputPacket::encode,
                HypnosisQteInputPacket::decode,
                HypnosisQteInputPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(
                7,
                EmoteTransitionPacket.class,
                EmoteTransitionPacket::encode,
                EmoteTransitionPacket::decode,
                EmoteTransitionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                8,
                TelepathyDialoguePacket.class,
                TelepathyDialoguePacket::encode,
                TelepathyDialoguePacket::decode,
                TelepathyDialoguePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                9,
                TerritorySyncPacket.class,
                TerritorySyncPacket::encode,
                TerritorySyncPacket::decode,
                TerritorySyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                11,
                FriendlySocialHugSyncPacket.class,
                FriendlySocialHugSyncPacket::encode,
                FriendlySocialHugSyncPacket::decode,
                FriendlySocialHugSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(
                13,
                WhiteHiveTargetsPacket.class,
                WhiteHiveTargetsPacket::encode,
                WhiteHiveTargetsPacket::decode,
                WhiteHiveTargetsPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                14,
                PlayerRelationshipOpenPacket.class,
                PlayerRelationshipOpenPacket::encode,
                PlayerRelationshipOpenPacket::decode,
                PlayerRelationshipOpenPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(
                15,
                ScoutTargetPacket.class,
                ScoutTargetPacket::encode,
                ScoutTargetPacket::decode,
                ScoutTargetPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                16,
                AbsorptionNegotiationStatePacket.class,
                AbsorptionNegotiationStatePacket::encode,
                AbsorptionNegotiationStatePacket::decode,
                AbsorptionNegotiationStatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                17,
                OpenAbsorptionNegotiationPacket.class,
                OpenAbsorptionNegotiationPacket::encode,
                OpenAbsorptionNegotiationPacket::decode,
                OpenAbsorptionNegotiationPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(
                18,
                PatAnimationPacket.class,
                PatAnimationPacket::encode,
                PatAnimationPacket::decode,
                PatAnimationPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                19,
                PatAnimationControlPacket.class,
                PatAnimationControlPacket::encode,
                PatAnimationControlPacket::decode,
                PatAnimationControlPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
}
