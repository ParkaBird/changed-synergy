package net.parkabird.changedsynergy.ai;

import java.util.Set;

/** Central catalog of built-in camp/settlement templates, categories, and voice IDs. */
public final class CampTemplateCatalog {
    public static final Set<String> MAIN_CAMPS = Set.of(
            "forest_treehouse",
            "hyena_hideout",
            "observatory",
            "desert_refuge",
            "lush_cabin",
            "point_prometheus",
            "burrow",
            "the_shoal");

    public static final Set<String> SMALL_CAMPS = Set.of(
            "oasis",
            "muddy_campsite",
            "hyena_camp_tropical",
            "hyena_camp_open",
            "hyena_camp_snowy",
            "stiger_hut");

    public static final Set<String> AUTHORED_STORAGE_CAMPS = Set.of(
            "desert_refuge",
            "lush_cabin",
            "point_prometheus",
            "burrow",
            "the_shoal",
            "oasis",
            "muddy_campsite",
            "hyena_camp_tropical",
            "hyena_camp_open",
            "hyena_camp_snowy",
            "stiger_hut");

    private CampTemplateCatalog() {
    }

    public static String canonicalTemplate(String template) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        int colon = template.indexOf(':');
        String prefix = colon >= 0 ? template.substring(0, colon + 1) : "";
        String path = colon >= 0 ? template.substring(colon + 1) : template;
        if (path.endsWith("_fd")) {
            String base = path.substring(0, path.length() - 3);
            if (MAIN_CAMPS.contains(base) || SMALL_CAMPS.contains(base)) {
                path = base;
            }
        }
        return prefix + path;
    }

    public static boolean isMainCamp(String template) {
        return MAIN_CAMPS.contains(stripNamespace(canonicalTemplate(template)));
    }

    public static boolean isSmallCamp(String template) {
        return SMALL_CAMPS.contains(stripNamespace(canonicalTemplate(template)));
    }

    public static boolean usesAuthoredStorageLayout(String template) {
        return AUTHORED_STORAGE_CAMPS.contains(stripNamespace(canonicalTemplate(template)));
    }

    public static boolean isLandmarkDestination(String destination) {
        return destination != null && MAIN_CAMPS.contains(destination);
    }

    public static String residentVoice(String template) {
        return switch (stripNamespace(canonicalTemplate(template))) {
            case "desert_refuge" -> "desert_resident";
            case "lush_cabin" -> "cavern_resident";
            case "point_prometheus" -> "supervisor_resident";
            case "burrow" -> "burrow_resident";
            case "the_shoal" -> "shoal_resident";
            default -> "resident";
        };
    }

    private static String stripNamespace(String template) {
        int colon = template.indexOf(':');
        return colon >= 0 ? template.substring(colon + 1) : template;
    }
}
