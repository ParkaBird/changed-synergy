package net.parkabird.changedsynergy.init;

import net.ltxprogrammer.changed.entity.variant.TransfurVariant;
import net.ltxprogrammer.changed.init.ChangedRegistry;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.MissingMappingsEvent;
import net.minecraft.resources.ResourceLocation;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.entity.LatexSilkMoth;
import net.parkabird.changedsynergy.entity.LatexSwordfish;
import net.parkabird.changedsynergy.entity.LatexMuntjac;
import net.parkabird.changedsynergy.entity.LatexMuntjacFemale;
import net.parkabird.changedsynergy.entity.LatexSpottedHyena;

public final class ChangedSynergyTransfurVariants {
    public static final DeferredRegister<TransfurVariant<?>> REGISTRY =
            ChangedRegistry.TRANSFUR_VARIANT.createDeferred(ChangedSynergyMod.MOD_ID);

    public static final RegistryObject<TransfurVariant<net.parkabird.changedsynergy.entity.LatexEagleOwl>> LATEX_EAGLE_OWL =
            REGISTRY.register("form_latex_eagle_owl",()->TransfurVariant.Builder
                    .<net.parkabird.changedsynergy.entity.LatexEagleOwl>of(ChangedSynergyEntities.LATEX_EAGLE_OWL)
                    .addAbility(ChangedSynergyAbilities::wingFlap).extraJumps(4).glide().nightVision().addAbility(net.ltxprogrammer.changed.init.ChangedAbilities.TOGGLE_NIGHT_VISION).build());

    public static final RegistryObject<TransfurVariant<LatexSilkMoth>> LATEX_SILK_MOTH =
            REGISTRY.register("form_latex_silk_moth", () -> TransfurVariant.Builder
                    .<LatexSilkMoth>of(ChangedSynergyEntities.LATEX_SILK_MOTH)
                    .extraJumps(6)
                    .extraHands()
                    .addAbility(ChangedSynergyAbilities.SPIN_SILK)
                    .build());

    private ChangedSynergyTransfurVariants() {
    }

    public static final RegistryObject<TransfurVariant<net.parkabird.changedsynergy.entity.LatexJerboa>> LATEX_JERBOA =
            REGISTRY.register("form_latex_jerboa",()->TransfurVariant.Builder.<net.parkabird.changedsynergy.entity.LatexJerboa>of(ChangedSynergyEntities.LATEX_JERBOA).build());

    public static final RegistryObject<TransfurVariant<LatexSwordfish>> LATEX_SWORDFISH =
            REGISTRY.register("form_latex_swordfish", () -> TransfurVariant.Builder
                    .<LatexSwordfish>of(ChangedSynergyEntities.LATEX_SWORDFISH)
                    .gills().absorbing().addAbility(ChangedSynergyAbilities.SWORDFISH_RUSH).build());

    public static final RegistryObject<TransfurVariant<LatexMuntjac>> LATEX_MUNTJAC =
            REGISTRY.register("form_latex_muntjac/male", () -> TransfurVariant.Builder
                    .<LatexMuntjac>of(ChangedSynergyEntities.LATEX_MUNTJAC).build());

    public static final RegistryObject<TransfurVariant<LatexMuntjacFemale>> LATEX_MUNTJAC_FEMALE =
            REGISTRY.register("form_latex_muntjac/female", () -> TransfurVariant.Builder
                    .<LatexMuntjacFemale>of(ChangedSynergyEntities.LATEX_MUNTJAC_FEMALE).build());

    public static final RegistryObject<TransfurVariant<LatexSpottedHyena>> LATEX_SPOTTED_HYENA_MALE =
            REGISTRY.register("form_latex_spotted_hyena/male", () -> TransfurVariant.Builder
                    .<LatexSpottedHyena>of(ChangedSynergyEntities.LATEX_SPOTTED_HYENA_MALE).build());

    public static final RegistryObject<TransfurVariant<LatexSpottedHyena>> LATEX_SPOTTED_HYENA_FEMALE =
            REGISTRY.register("form_latex_spotted_hyena/female", () -> TransfurVariant.Builder
                    .<LatexSpottedHyena>of(ChangedSynergyEntities.LATEX_SPOTTED_HYENA_FEMALE).build());

    public static void registerAliases(RegisterEvent event) {
        if (!event.getRegistryKey().equals(ChangedRegistry.TRANSFUR_VARIANT.get().getRegistryKey())) return;
        var registry = (ForgeRegistry<TransfurVariant<?>>)event.<TransfurVariant<?>>getForgeRegistry();
        registry.addAlias(ResourceLocation.parse("changed_synergy:form_latex_muntjac"), LATEX_MUNTJAC.getId());
        registry.addAlias(ResourceLocation.parse("changed_synergy:form_latex_muntjac_female"), LATEX_MUNTJAC_FEMALE.getId());
    }

    /** Saved Synergy state also compares form strings without going through the registry. */
    public static String canonicalFormId(String id) {
        return switch (id) {
            case "changed_synergy:form_latex_muntjac" -> "changed_synergy:form_latex_muntjac/male";
            case "changed_synergy:form_latex_muntjac_female" -> "changed_synergy:form_latex_muntjac/female";
            default -> id;
        };
    }

    public static void remapOldForms(MissingMappingsEvent event) {
        for (var mapping : event.<TransfurVariant<?>>getMappings(ChangedRegistry.TRANSFUR_VARIANT.get().getRegistryKey(), ChangedSynergyMod.MOD_ID)) {
            switch (mapping.getKey().getPath()) {
                case "form_latex_muntjac" -> mapping.remap(LATEX_MUNTJAC.get());
                case "form_latex_muntjac_female" -> mapping.remap(LATEX_MUNTJAC_FEMALE.get());
            }
        }
    }
}
