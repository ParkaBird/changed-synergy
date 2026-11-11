package net.parkabird.changedsynergy.client;

import net.minecraft.client.Minecraft;
import net.parkabird.changedsynergy.entity.LatexSilkMoth;

public final class SilkMothWingFlapClient {
    public static void receive(int playerId) {
        var level = Minecraft.getInstance().level;
        if(level==null||!(level.getEntity(playerId) instanceof net.minecraft.world.entity.LivingEntity actor))return;
        var body=net.ltxprogrammer.changed.ability.IAbstractChangedEntity.forEitherSafe(actor)
                .map(net.ltxprogrammer.changed.ability.IAbstractChangedEntity::getChangedEntity).orElse(null);
        if(body instanceof LatexSilkMoth moth)moth.flapWings();
        if(body instanceof net.parkabird.changedsynergy.entity.LatexEagleOwl owl)owl.flapWings();
    }
}
