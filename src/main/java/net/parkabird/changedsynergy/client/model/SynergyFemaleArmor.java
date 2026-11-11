package net.parkabird.changedsynergy.client.model;

import net.ltxprogrammer.changed.client.renderer.model.armor.*;
import net.ltxprogrammer.changed.client.CubeListBuilderExtender;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Direction;

/** Native clothing/armor UVs and behavior, fitted to Synergy's flat, curved female bodies. */
public final class SynergyFemaleArmor {
    public static final ArmorModelSet<ChangedEntity, ArmorLatexMaleWolfModel<ChangedEntity>> SLIM = set("slim", false);
    public static final ArmorModelSet<ChangedEntity, ArmorLatexMaleWolfModel<ChangedEntity>> HYENA = set("hyena", true);

    private SynergyFemaleArmor() {}

    private static ArmorModelSet<ChangedEntity, ArmorLatexMaleWolfModel<ChangedEntity>> set(String name, boolean hyena) {
        return ArmorModelSet.of(ResourceLocation.fromNamespaceAndPath("changed_synergy", "female_armor_" + name),
                layer -> createLayer(layer, hyena), (root, layer) -> {
                    // Preserve standard 4-pixel sleeve UVs while fitting 3-pixel limbs.
                    if (!hyena) for (String side : new String[]{"Left", "Right"}) {
                        var sleeve = root.getChild(side + "Arm").getChild("Sleeve");
                        sleeve.xScale = sleeve.zScale = .75F;
                    }
                    return new ArmorLatexMaleWolfModel<>(root, layer);
                });
    }

    private static LayerDefinition createLayer(ArmorModel layer, boolean hyena) {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();
        LatexHumanoidArmorModel.addUnifiedLegs(root, layer);
        float top = hyena ? -1.7F : -.5F;
        // Segments follow the supplied body's upper torso, waist and hips, with continuous side/front UVs.
        var torso = (CubeListBuilderExtender)CubeListBuilder.create()
                .texOffs(16, 16).addBox(-4, 0, -2, 8, 6, 4, layer.dualDeformation.extend(.2F));
        // Interior caps sample opaque trouser texels even where their side faces are transparent.
        torso.removeLastFaces(Direction.DOWN)
                .texOffs(16, 21).addBox(-4, hyena ? 5.2F : 5, -2, 8, hyena ? 6 : 5, 4, layer.dualDeformation.extend(-.3F))
                .removeLastFaces(Direction.UP, Direction.DOWN)
                .texOffs(16, 25).addBox(-4, hyena ? 9.8F : 9, -2, 8, 3, 4, layer.dualDeformation.extend(.1F))
                .removeLastFaces(Direction.UP, Direction.DOWN);
        root.addOrReplaceChild("Torso", torso.finish(), PartPose.offset(0, top, 0));
        root.addOrReplaceChild("Head", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4, -8, -4, 8, 8, 8, layer.dualDeformation), PartPose.offset(0, top, 0));
        for (boolean left : new boolean[]{false, true}) {
            var arm = root.addOrReplaceChild(left ? "LeftArm" : "RightArm", CubeListBuilder.create(),
                    PartPose.offset((left ? 1 : -1) * (hyena ? 5.3F : 5), top + 2, 0));
            arm.addOrReplaceChild("Sleeve", CubeListBuilder.create().texOffs(40, 16).mirror(left)
                    .addBox(left ? -1 : -3, -2, -2, 4, 12, 4, layer.dualDeformation.extend(hyena ? .1F : 0)),
                    PartPose.offset(hyena ? 0 : left ? -.25F : .25F, 0, 0));
        }
        return LayerDefinition.create(mesh, 64, 32);
    }
}
