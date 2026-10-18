package net.parkabird.changedsynergy.regression;

import net.parkabird.changedsynergy.event.TerritoryContextEvents;

final class FacilityTemplateRegression {
    static void run() {
        for (String marker : new String[]{"blue", "gray", "grey", "red", "maintenance", "tree"}) {
            for (String path : new String[]{"facility/" + marker + "/room", marker + "_room",
                    "room_" + marker + "_end", "room_" + marker}) {
                if (!TerritoryContextEvents.hasFacilityMarker(path, marker))
                    throw new AssertionError("Facility token not recognized: " + path);
            }
            for (String path : new String[]{marker + "ish_room", "room_" + marker + "ish", "not" + marker}) {
                if (TerritoryContextEvents.hasFacilityMarker(path, marker))
                    throw new AssertionError("A partial name became a facility token: " + path);
            }
        }
        if (!TerritoryContextEvents.hasFacilityMarker("blue_stairs_to_red", "blue")
                || !TerritoryContextEvents.hasFacilityMarker("blue_stairs_to_red", "red"))
            throw new AssertionError("Transition detection must still see both adjacent section tokens");
        System.out.println("FACILITY_TEMPLATE_MARKERS_PASS: work rooms, section tokens and transition markers");
    }
}
