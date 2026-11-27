package net.parkabird.changedsynergy.entity;

import net.ltxprogrammer.changed.entity.AttributePresets;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.ComplexRenderer;
import net.ltxprogrammer.changed.entity.TransfurCause;
import net.ltxprogrammer.changed.entity.TransfurMode;
import net.ltxprogrammer.changed.entity.latex.LatexType;
import net.ltxprogrammer.changed.init.ChangedLatexTypes;
import net.ltxprogrammer.changed.util.Color3;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.ForgeMod;

/** White-latex counterpart to Changed's dark latex wolf partial. */
public class WhiteLatexWolfPartial extends ChangedEntity implements ComplexRenderer {
    public WhiteLatexWolfPartial(
            EntityType<? extends WhiteLatexWolfPartial> type, Level level) {
        super(type, level);
    }

    @Override
    protected void setAttributes(AttributeMap attributes) {
        super.setAttributes(attributes);
        AttributePresets.wolfLike(attributes);
        attributes.getInstance(Attributes.MOVEMENT_SPEED).setBaseValue(1.025);
        attributes.getInstance(ForgeMod.SWIM_SPEED.get()).setBaseValue(0.975);
    }

    @Override
    public TransfurMode getTransfurMode() {
        return TransfurMode.NONE;
    }

    @Override
    public LatexType getLatexType() {
        return ChangedLatexTypes.WHITE_LATEX.get();
    }

    @Override
    protected boolean targetSelectorTest(LivingEntity livingEntity) {
        return false;
    }

    @OnlyIn(Dist.CLIENT)
    public ResourceLocation getSkinTextureLocation() {
        ResourceLocation skin = net.parkabird.changedsynergy.client.InstinctPlayerSkinClient.resolveSkinTexture(this);
        if (skin != null) {
            return skin;
        }
        return DefaultPlayerSkin.getDefaultSkin(this.getUUID());
    }

    @OnlyIn(Dist.CLIENT)
    public String getModelName() {
        String modelName = net.parkabird.changedsynergy.client.InstinctPlayerSkinClient.resolveModelName(this);
        if (modelName != null) {
            return modelName;
        }
        return DefaultPlayerSkin.getSkinModelName(this.getUUID());
    }

    @Override
    public Color3 getTransfurColor(TransfurCause cause) {
        return Color3.WHITE;
    }
}
