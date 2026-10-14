package net.parkabird.changedsynergy.regression;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.client.NameVisibilityClientEvents;
import net.parkabird.changedsynergy.client.PlayerSocialClient;
import net.parkabird.changedsynergy.network.PlayerBodyPacket;

/** Real client rendering callbacks and synchronized body identity, without the optional backend. */
public final class CampSocialClientRegression {
    private static int checks;
    public static void run(Minecraft mc) {
        var body = (ChangedEntity)ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.parse("changed:dark_latex_wolf_male")).create(mc.level);
        body.setPos(mc.player.position().add(0, 0, 2));
        body.setCustomName(Component.literal("Ordinary friend")); body.setCustomNameVisible(true);
        check(nameEvent(mc, body).getResult() == Event.Result.DEFAULT, "Nearby named NPC remains visible");
        UUID owner = UUID.randomUUID();
        PlayerSocialClient.receive(new PlayerBodyPacket(body.getUUID(), owner, Component.literal("Player identity"), "PlayerIdentity"));
        check(PlayerSocialClient.isPlayerBody(body), "Network identifies the Instinct body");
        check(nameEvent(mc, body).getResult() == Event.Result.DENY, "Identified Instinct body never renders its name");
        check(body.hasCustomName(), "Hiding label preserves the entity name and social identity");
        PlayerSocialClient.onLeave(new EntityLeaveLevelEvent(body, mc.level));
        check(!PlayerSocialClient.isPlayerBody(body) && nameEvent(mc, body).getResult() == Event.Result.DEFAULT,
                "Body removal clears the marker rather than hiding a later NPC");
        for (String key : new String[]{"menu.changed_synergy.relationship.invite_outpost", "command.changed_synergy.outpost.area.gather"})
            check(!I18n.get(key).equals(key), "New UI text resolves: " + key);
        System.out.println("CAMP_SOCIAL_CLIENT_REGRESSION_PASS: " + checks + " assertions");
    }
    private static RenderNameTagEvent nameEvent(Minecraft mc, ChangedEntity body) {
        var event = new RenderNameTagEvent(body, body.getDisplayName(), mc.getEntityRenderDispatcher().getRenderer(body),
                new PoseStack(), mc.renderBuffers().bufferSource(), 15728880, 0);
        NameVisibilityClientEvents.onRenderNameTag(event);
        return event;
    }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); checks++; }
}
