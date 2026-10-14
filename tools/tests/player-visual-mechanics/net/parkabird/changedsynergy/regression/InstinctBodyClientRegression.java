package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.parkabird.changedsynergy.client.PlayerSocialClient;
import net.parkabird.changedsynergy.network.PlayerBodyPacket;

/** Exercise remote technical-player rendering and the real ClientLevel removal lifecycle. */
public final class InstinctBodyClientRegression {
    private static int checks;
    public static void run(Minecraft mc) {
        checks=0;
        mc.getEntityRenderDispatcher().prepare(mc.level, mc.gameRenderer.getMainCamera(), mc.player);
        var remote=new RemotePlayer(mc.level,new GameProfile(UUID.randomUUID(),"RemoteNative"));
        remote.setId(210011);remote.setPos(mc.player.position());mc.level.addPlayer(210011,remote);
        ChangedEntity first=ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(mc.level);
        ChangedEntity second=ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(mc.level);
        first.setId(210012);second.setId(210013);
        first.setPos(remote.position());second.setPos(remote.position());
        var variant=ProcessTransfur.setPlayerTransfurVariant(remote,ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get(),null,1,true,
                form->form.willSurviveTransfur=true);
        try {
            mc.level.putNonPlayerEntity(210012,first);
            PlayerSocialClient.receive(new PlayerBodyPacket(first.getUUID(),remote.getUUID(),Component.literal("River"),"RemoteNative"));
            check(PlayerSocialClient.visibleBody(remote)==first,"Remote social preview resolves the physical proxy");
            check(PlayerSocialClient.isHiddenTechnicalPlayer(remote),"Remote technical player is hidden");
            check(PlayerSocialClient.isHiddenTechnicalPlayer(variant.getChangedEntity()),"Changed's named virtual player model is also hidden");
            check(!PlayerSocialClient.isHiddenTechnicalPlayer(first)&&PlayerSocialClient.isPlayerBody(first),"Physical proxy stays visible and interactive");
            check(renderRequests(mc,remote)==0&&renderRequests(mc,variant.getChangedEntity())==0,
                    "Actual entity dispatcher produces no buffers for either duplicate model");
            check(renderRequests(mc,first)>0,"Actual entity dispatcher still draws the visible proxy");
            PlayerSocialClient.receive(new PlayerBodyPacket(second.getUUID(),remote.getUUID(),Component.literal("River"),"RemoteNative"));
            check(PlayerSocialClient.visibleBody(remote)==remote,"Identity arriving before spawn does not hide the only visible body");
            mc.level.putNonPlayerEntity(210013,second);
            check(!PlayerSocialClient.isPlayerBody(first)&&PlayerSocialClient.visibleBody(remote)==second,"Body replacement invalidates the old social mapping");
            mc.level.removeEntity(210012,Entity.RemovalReason.DISCARDED);
            check(PlayerSocialClient.visibleBody(remote)==second&&PlayerSocialClient.isHiddenTechnicalPlayer(remote),"Late old-body removal does not reveal the technical player");
            mc.level.removeEntity(210013,Entity.RemovalReason.DISCARDED);
            check(PlayerSocialClient.visibleBody(remote)==remote&&!PlayerSocialClient.isHiddenTechnicalPlayer(remote),"Returning to PLAYER restores the account body");
            check(renderRequests(mc,remote)>0,"Restored PLAYER body reaches the real renderer");
            System.out.println("INSTINCT_BODY_CLIENT_REGRESSION_PASS checks="+checks);
        } finally {
            ProcessTransfur.removePlayerTransfurVariant(remote);
            for(int id:new int[]{210011,210012,210013})mc.level.removeEntity(id,Entity.RemovalReason.DISCARDED);
        }
    }
    private static int renderRequests(Minecraft mc,Entity entity) {
        int[] requests={0};
        mc.getEntityRenderDispatcher().render(entity,0,0,0,0,0,new PoseStack(),type->{
            requests[0]++;return mc.renderBuffers().bufferSource().getBuffer(type);
        },15728880);
        mc.renderBuffers().bufferSource().endBatch();return requests[0];
    }
    private static void check(boolean condition,String message) {checks++;if(!condition)throw new AssertionError(message);}
}
