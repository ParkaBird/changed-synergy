package net.parkabird.changedsynergy.event;

import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.CreatureIdentity;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.compat.addon.AddonAnimalCalls;
import net.parkabird.changedsynergy.origin.NativeOriginService;

@Mod.EventBusSubscriber(modid=ChangedSynergyMod.MOD_ID)
public final class NativeChatAnimalCalls {
    private static final String NEXT="ChangedSynergyNextChatAnimalCall";
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void chat(ServerChatEvent event) {
        if(!net.parkabird.changedsynergy.ai.NativeRoleplayService.roleChat(event.getPlayer()))spoken(event.getPlayer());
    }
    public static void spoken(net.minecraft.server.level.ServerPlayer player) {
        if(!ChangedSynergyConfig.COMMON.nativeChatAnimalCalls.get() || !NativeOriginService.isNative(player)
                || !player.isAlive() || player.isSpectator())return;
        var form=ProcessTransfur.getPlayerTransfurVariant(player);
        if(form!=null && form.getTransfurProgression(1)<1)return;
        var body=net.parkabird.changedsynergy.ai.NativeRoleplayService.visual(player);
        if(body==null || !body.isAlive())return;
        var source=net.parkabird.changedsynergy.compat.TrueTransfurCompat.interactionBody(player);
        if(!net.parkabird.changedsynergy.ai.NativeRoleplayService.validBody(player,source))return;
        long now=player.level().getGameTime();
        if(player.getPersistentData().getLong(NEXT)>now)return;
        player.getPersistentData().putLong(NEXT,now+80);
        if(ChangedAddonCompat.isLoaded() && ChangedSynergyConfig.COMMON.addonAnimalCalls.get() && AddonAnimalCalls.supports(body)) {
            AddonAnimalCalls.playFrom(body,source,"neutral");return;
        }
        if(player.getRandom().nextFloat()>.4F)return;
        var sound=switch(CreatureIdentity.vocalizationProfile(body)) {
            case "canine" -> SoundEvents.WOLF_AMBIENT;
            case "feline" -> SoundEvents.CAT_AMBIENT;
            case "draconic" -> SoundEvents.ENDER_DRAGON_AMBIENT;
            case "insect" -> SoundEvents.BEE_LOOP;
            case "herbivore" -> SoundEvents.GOAT_AMBIENT;
            case "small_mammal" -> SoundEvents.RABBIT_AMBIENT;
            case "avian" -> SoundEvents.PARROT_AMBIENT;
            case "aquatic" -> SoundEvents.DOLPHIN_AMBIENT;
            case "reptile" -> SoundEvents.TURTLE_AMBIENT_LAND;
            case "hybrid" -> SoundEvents.FOX_AMBIENT;
            default -> null;
        };
        if(sound!=null)source.level().playSound(null,source.getX(),source.getY(),source.getZ(),sound,SoundSource.PLAYERS,.45F,.9F+player.getRandom().nextFloat()*.2F);
    }
}
