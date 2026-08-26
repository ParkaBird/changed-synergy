package net.parkabird.changedsynergy.dialogue;

import java.util.Locale;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A conservative, player-facing interpretation of Changed facility pieces.
 * Unknown and transitional templates deliberately remain unnamed.
 */
public record FacilityArea(String section, String detail) {
    private static final String AREA_KEY =
            "overlay.changed_synergy.facility.area.";

    public Component displayName() {
        Component sectionName =
                Component.translatable(AREA_KEY + section);
        if (detail.isBlank()) {
            return sectionName;
        }
        return Component.translatable(
                AREA_KEY + "format",
                sectionName,
                Component.translatable(
                        AREA_KEY + "detail." + detail));
    }

    public String stableKey() {
        return section + ":" + detail;
    }

    public static Optional<FacilityArea> identify(String templateId) {
        ResourceLocation id = ResourceLocation.tryParse(templateId);
        String path = (id == null ? templateId : id.getPath())
                .toLowerCase(Locale.ROOT);
        if (path.isBlank()
                || path.contains("/transition/")
                || path.contains("/seal/")) {
            return Optional.empty();
        }

        String section = section(path);
        if (section == null) {
            return Optional.empty();
        }
        String detail = detail(path);
        return detail == null
                ? Optional.empty()
                : Optional.of(new FacilityArea(section, detail));
    }

    private static String section(String path) {
        if (hasMarker(path, "blue")) {
            return "blue";
        }
        if (hasMarker(path, "gray")
                || hasMarker(path, "grey")) {
            return "gray";
        }
        if (hasMarker(path, "red")) {
            return "red";
        }
        if (hasMarker(path, "maintenance")) {
            return "maintenance";
        }
        return null;
    }

    private static String detail(String path) {
        if (path.contains("bathroom")) {
            return "bathroom";
        }
        if (path.contains("garden")) {
            return "garden";
        }
        if (path.contains("generator")) {
            return "generator_hall";
        }
        if (path.contains("_wl_")) {
            return "white_latex_lab";
        }
        if (path.contains("_dl_")) {
            return "dark_latex_lab";
        }
        if (path.contains("origin")) {
            return "origin_lab";
        }
        if (path.contains("office")) {
            return "office";
        }
        if (path.contains("storage")
                || path.contains("container")) {
            return "storage";
        }
        if (path.contains("/entrance/")
                || fileName(path).startsWith("entrance_")) {
            return "entrance";
        }
        if (path.contains("stair")
                || path.contains("steps")) {
            return "stairwell";
        }
        if (path.contains("/room/")
                && path.contains("test")) {
            return "test_room";
        }
        if (path.contains("/corridor/")
                || path.contains("hallway")
                || path.contains("intersection")) {
            return "corridor";
        }
        return null;
    }

    private static boolean hasMarker(
            String path,
            String marker) {
        String file = fileName(path);
        return path.contains("/" + marker + "/")
                || file.startsWith(marker + "_")
                || file.contains("_" + marker + "_")
                || file.endsWith("_" + marker);
    }

    private static String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
}
