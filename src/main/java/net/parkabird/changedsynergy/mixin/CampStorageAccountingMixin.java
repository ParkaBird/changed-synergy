package net.parkabird.changedsynergy.mixin;

import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.parkabird.changedsynergy.npc.CampLifeService;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class CampStorageAccountingMixin {
    @Unique private Map<CampLifeService.Taken,Integer> synergy$before;
    @Inject(method={"clicked","m_150399_"},at=@At("HEAD"),remap=false,require=1)
    private void synergy$beforeTransfer(int slot,int button,ClickType click,Player player,CallbackInfo ci){if(player instanceof ServerPlayer server)synergy$before=CampLifeService.snapshot((AbstractContainerMenu)(Object)this,server);}
    @Inject(method={"clicked","m_150399_"},at=@At("RETURN"),remap=false,require=1)
    private void synergy$afterTransfer(int slot,int button,ClickType click,Player player,CallbackInfo ci){if(player instanceof ServerPlayer server&&synergy$before!=null){CampLifeService.withdrawn(server,synergy$before,CampLifeService.snapshot((AbstractContainerMenu)(Object)this,server));synergy$before=null;}}
}
