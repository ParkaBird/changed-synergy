package net.parkabird.changedsynergy.network;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.parkabird.changedsynergy.ai.PlayerOutpostBuildService;

public record PlayerOutpostBuildActionPacket(int action, BlockPos pos, Direction face, Vec3 location) {
    public static final int EXIT = 0;
    public static final int CYCLE = 1;
    public static final int PLACE = 2;

    public static PlayerOutpostBuildActionPacket exit() {
        return new PlayerOutpostBuildActionPacket(EXIT, BlockPos.ZERO, Direction.UP, Vec3.ZERO);
    }

    public static PlayerOutpostBuildActionPacket cycle(int delta) {
        return new PlayerOutpostBuildActionPacket(CYCLE, BlockPos.ZERO,
                delta < 0 ? Direction.DOWN : Direction.UP, Vec3.ZERO);
    }

    public static PlayerOutpostBuildActionPacket place(BlockHitResult hit) {
        return new PlayerOutpostBuildActionPacket(PLACE, hit.getBlockPos(), hit.getDirection(), hit.getLocation());
    }

    public static void encode(PlayerOutpostBuildActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.action);
        buffer.writeBlockPos(packet.pos);
        buffer.writeEnum(packet.face);
        buffer.writeDouble(packet.location.x);
        buffer.writeDouble(packet.location.y);
        buffer.writeDouble(packet.location.z);
    }

    public static PlayerOutpostBuildActionPacket decode(FriendlyByteBuf buffer) {
        return new PlayerOutpostBuildActionPacket(
                buffer.readVarInt(),
                buffer.readBlockPos(),
                buffer.readEnum(Direction.class),
                new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
    }

    public static void handle(PlayerOutpostBuildActionPacket packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        var sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> {
                switch (packet.action) {
                    case EXIT -> PlayerOutpostBuildService.exit(sender);
                    case CYCLE -> PlayerOutpostBuildService.cycle(
                            sender, packet.face == Direction.DOWN ? -1 : 1);
                    case PLACE -> PlayerOutpostBuildService.tryPlace(sender,
                            new BlockHitResult(packet.location, packet.face, packet.pos, false));
                    default -> {
                    }
                }
            });
        }
        context.setPacketHandled(true);
    }
}
