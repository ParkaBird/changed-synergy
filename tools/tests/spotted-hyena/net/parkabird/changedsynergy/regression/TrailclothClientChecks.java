package net.parkabird.changedsynergy.regression;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.ltxprogrammer.changed.client.renderer.layers.AccessoryLayer;
import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.ltxprogrammer.changed.data.AccessorySlotContext;
import net.ltxprogrammer.changed.data.AccessorySlots;
import net.ltxprogrammer.changed.entity.AccessoryEntities;
import net.ltxprogrammer.changed.init.ChangedAccessorySlots;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.client.HyenaTrailclothRenderer;
import net.parkabird.changedsynergy.entity.LatexSpottedHyena;
import net.parkabird.changedsynergy.init.ChangedSynergyItems;
import net.parkabird.changedsynergy.item.HyenaTrailclothItem;
import org.joml.Quaternionf;

final class TrailclothClientChecks {
    static void equip(LivingEntity wearer) {
        AccessoryEntities.INSTANCE.forceReloadAccessories(wearer);
        var slots = AccessorySlots.getForEntity(wearer).orElseThrow();
        if (equipped(wearer)) return;
        check(slots.quickMoveStack(new ItemStack(ChangedSynergyItems.HYENA_TRAILCLOTH_OUTFIT.get())), "native clothing equip");
    }
    static boolean equipped(LivingEntity wearer) {
        return AccessorySlots.isWearing(wearer, s -> s.is(ChangedSynergyItems.HYENA_TRAILCLOTH_OUTFIT.get()));
    }
    @SuppressWarnings({"rawtypes", "unchecked"}) static void verify(Minecraft mc, LatexSpottedHyena mob) {
        var item = ChangedSynergyItems.HYENA_TRAILCLOTH_OUTFIT.get(); var stack = new ItemStack(item);
        check(mc.getItemRenderer().getModel(stack,mc.level,mc.player,0)!=mc.getModelManager().getMissingModel(), "outfit icon resolves");
        check(mc.getResourceManager().getResource(item.getTexture(stack,mob)).isPresent(), "gender texture resolves");
        var renderer = AccessoryLayer.getRenderer(item).orElseThrow(); check(renderer instanceof HyenaTrailclothRenderer, "native clothing renderer registered");
        var parent = (net.minecraft.client.renderer.entity.RenderLayerParent)mc.getEntityRenderDispatcher().getRenderer(mob);
        var model = (AdvancedHumanoidModel)parent.getModel(); var before = model.getArm(HumanoidArm.LEFT).storePose();
        check(ChangedAccessorySlots.BODY.get().canHoldItem(stack,mob),"equipped outfit remains valid");
        try {
            var field=HyenaTrailclothRenderer.class.getDeclaredField(HyenaTrailclothItem.female(mob)?"femaleBody":"maleBody");field.setAccessible(true);
            var reference=(net.minecraft.client.model.geom.ModelPart)field.get(renderer);
            var method=net.parkabird.changedsynergy.client.ClothingModelFit.class.getDeclaredMethod("bounds",net.minecraft.client.model.geom.ModelPart.class);method.setAccessible(true);
            for(var name:List.of("Torso","LeftArm","LeftLeg")) {
                var part=reference.getChild(name);
                var box=(float[])method.invoke(renderer,part);
                check(box!=null&&box[3]>box[0]&&box[4]>box[1]&&box[5]>box[2],"rotated source cube bounds resolved: "+name);
            }
        } catch(ReflectiveOperationException e) {throw new RuntimeException(e);}
        var buffers = mc.renderBuffers().bufferSource(); var context = new AccessorySlotContext(mob,ChangedAccessorySlots.BODY.get(),stack);
        for (float rotation : new float[]{0,.7F,1.5F}) {
            model.getArm(HumanoidArm.LEFT).xRot=rotation;
            var pose = new PoseStack(); var original = new org.joml.Matrix4f(pose.last().pose());
            renderer.render(context,pose,parent,buffers,15728880,0,.6F,.5F,30,0,0);
            check(original.equals(pose.last().pose()), "clothing leaves pose stack balanced");
            check(model.getArm(HumanoidArm.LEFT).xRot==rotation, "clothing follows final arm pose without overwriting it");
            for (var side : HumanoidArm.values()) renderer.renderFirstPersonOnArms(context,new PoseStack(),parent,buffers,15728880,side,model.getArm(side).storePose(),.5F);
        }
        model.getArm(HumanoidArm.LEFT).loadPose(before); buffers.endBatch();
        var ordered = new java.util.ArrayList<>(net.ltxprogrammer.changed.init.ChangedTabs.TAB_CHANGED_CLOTHING.get().getDisplayItems());
        int at = -1; for (int i=0;i<ordered.size();i++) if (ordered.get(i).is(item)) at=i;
        check(at>0&&ordered.get(at-1).is(net.ltxprogrammer.changed.init.ChangedItems.WETSUIT.get()), "outfit grouped with native clothes");
        System.out.println("TRAILCLOTH_CLIENT_CHECK_PASS: " + mob.getGender());
    }
    static void gallery(Minecraft mc, GuiGraphics g, int w, int h) {
        var names=List.of("changed:white_latex_wolf_male", "changed:white_latex_wolf_female", "changed:latex_siamese_cat", "changed_synergy:latex_muntjac_female",
                "changed:latex_snake", "changed:latex_mermaid_shark", "changed:white_latex_centaur", "changed:latex_human");
        var labels=List.of("Male wolf", "Female wolf", "Feline", "Female muntjac", "Snake: upper only", "Mer: upper only", "Taur: upper only", "Human-like");
        for (int i=0;i<names.size();i++) {
            var type=ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.parse(names.get(i)));
            check(type!=null,"test species exists: "+names.get(i));
            var mob=(LivingEntity)type.create(mc.level); check(HyenaTrailclothItem.fits(mob),"related body fits: "+names.get(i)); equip(mob);
            if(i==4||i==5) {
                check(!HyenaTrailclothItem.hasLowerSection(mob),"tail body keeps its original lower-body appearance");
                var model=(AdvancedHumanoidModel<?>)((net.minecraft.client.renderer.entity.RenderLayerParent<?,?>)mc.getEntityRenderDispatcher().getRenderer(mob)).getModel();
                var hip=model.getRootLevelLimbs().filter(p->p.hasChild("LowerAbdomen")||p.hasChild("Tail")).findFirst().orElseThrow();
                try {var renderer=AccessoryLayer.getRenderer(ChangedSynergyItems.HYENA_TRAILCLOTH_OUTFIT.get()).orElseThrow();var method=net.parkabird.changedsynergy.client.ClothingModelFit.class.getDeclaredMethod("bounds",net.minecraft.client.model.geom.ModelPart.class);method.setAccessible(true);check(method.invoke(renderer,hip)!=null,"tail hip bounds available");}
                catch(ReflectiveOperationException e){throw new RuntimeException(e);}
            }
            mob.tickCount=100; mob.yBodyRot=mob.yBodyRotO=mob.yHeadRot=mob.yHeadRotO=180;mob.setYRot(180);
            int x=(i%4*2+1)*w/8,y=(i/4+1)*(h-35)/2;
            InventoryScreen.renderEntityInInventory(g,x,y,h/10,new Quaternionf().rotationZ((float)Math.PI).rotateY(.2F),new Quaternionf(),mob);
            g.drawCenteredString(mc.font,labels.get(i),x,y+8,0xFFFFFF);
        }
    }
    static void humans(Minecraft mc, GuiGraphics g, int w, int h) {
        for (int slim=0;slim<2;slim++) {
            net.minecraft.client.player.RemotePlayer player=null;
            for (int seed=0;seed<32;seed++) {
                var candidate=new net.minecraft.client.player.RemotePlayer(mc.level,new com.mojang.authlib.GameProfile(new java.util.UUID(0,seed),"TrailclothPreview"));
                if (candidate.getModelName().equals(slim==0?"default":"slim")) {player=candidate;break;}
            }
            check(player!=null,"classic/slim preview skin");
            net.minecraft.network.syncher.EntityDataAccessor<Byte> skinFlags=net.minecraftforge.fml.util.ObfuscationReflectionHelper.getPrivateValue(
                    net.minecraft.world.entity.player.Player.class,null,"f_36089_");
            player.getEntityData().set(skinFlags,(byte)127);
            equip(player);player.tickCount=100;
            for(int back=0;back<2;back++) {
                float yaw=back==0?180:0;player.yBodyRot=player.yBodyRotO=player.yHeadRot=player.yHeadRotO=yaw;player.setYRot(yaw);
                int x=(slim*2+back)*w/4+w/8;
                InventoryScreen.renderEntityInInventory(g,x,h-45,h/5,new Quaternionf().rotationZ((float)Math.PI),new Quaternionf(),player);
                g.drawCenteredString(mc.font,(slim==0?"Classic":"Slim")+(back==0?" front":" back"),x,h-25,0xFFFFFF);
            }
        }
    }
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
