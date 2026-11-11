package net.parkabird.changedsynergy.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.ltxprogrammer.changed.ability.AbstractAbility;
import net.ltxprogrammer.changed.init.ChangedAbilities;
import net.ltxprogrammer.changed.network.packet.GrabEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.parkabird.changedsynergy.ai.EagleOwlTraits;
import org.spongepowered.asm.mixin.Mixin;

/** Native ARMS packets also broadcast rejected grabs, leaving a predicted client hold. */
@Mixin(value=GrabEntityPacket.class,remap=false)
public abstract class EagleOwlGrabPacketMixin {
    @WrapMethod(method="handle")
    private CompletableFuture<Void> changedSynergy$acknowledgeRejectedGrab(NetworkEvent.Context context,
            CompletableFuture<Level> level,Executor executor,Operation<CompletableFuture<Void>> original){
        var result=original.call(context,level,executor);
        var sender=context.getSender();var packet=(GrabEntityPacket)(Object)this;
        // The existing packet guard schedules re-entry on the server; attach only there.
        if(sender==null||!sender.server.isSameThread()||packet.type!=GrabEntityPacket.GrabType.ARMS
                ||packet.sourceEntity!=sender.getId())return result;
        return result.thenRun(()->{
            if(EagleOwlTraits.body(sender)==null)return;
            var grab=AbstractAbility.getAbilityInstance(sender,ChangedAbilities.GRAB_ENTITY_ABILITY.get());
            if(grab==null)return;
            if(grab.grabbedEntity!=null){
                if(grab.grabbedEntity.getId()==packet.targetEntity)
                    net.ltxprogrammer.changed.Changed.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(()->sender),packet);
                return;
            }
            if(!(sender.level().getEntity(packet.targetEntity) instanceof LivingEntity target))return;
            // A rejected acquisition is not a release: extending the release cooldown here can lock a held key forever.
            var release=new GrabEntityPacket(sender,target,GrabEntityPacket.GrabType.RELEASE);
            net.ltxprogrammer.changed.Changed.PACKET_HANDLER.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->sender),release);
            var position=new ClientboundTeleportEntityPacket(target);
            sender.serverLevel().getChunkSource().broadcastAndSend(target,position);
            sender.connection.send(position);
        });
    }
}
