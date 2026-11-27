package net.parkabird.changedsynergy.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CampTemplateCatalogTest {
    @Test
    void normalizesVariantsAndClassifiesCamps() {
        assertEquals("changed_synergy:the_shoal", CampTemplateCatalog.canonicalTemplate("changed_synergy:the_shoal_fd"));
        assertEquals("lush_cabin", CampTemplateCatalog.canonicalTemplate("lush_cabin_fd"));
        assertEquals("custom_mod:some_camp_fd", CampTemplateCatalog.canonicalTemplate("custom_mod:some_camp_fd"));

        assertTrue(CampTemplateCatalog.isMainCamp("the_shoal_fd"));
        assertTrue(CampTemplateCatalog.isMainCamp("forest_treehouse"));
        assertFalse(CampTemplateCatalog.isMainCamp("stiger_hut"));

        assertTrue(CampTemplateCatalog.isSmallCamp("stiger_hut"));
        assertTrue(CampTemplateCatalog.isSmallCamp("hyena_camp_open"));
        assertFalse(CampTemplateCatalog.isSmallCamp("desert_refuge"));

        assertTrue(CampTemplateCatalog.usesAuthoredStorageLayout("burrow_fd"));
        assertTrue(CampTemplateCatalog.usesAuthoredStorageLayout("stiger_hut"));
        assertFalse(CampTemplateCatalog.usesAuthoredStorageLayout("forest_treehouse"));

        assertTrue(CampTemplateCatalog.isLandmarkDestination("observatory"));
        assertFalse(CampTemplateCatalog.isLandmarkDestination("oasis"));
        assertEquals("shoal_resident", CampTemplateCatalog.residentVoice("changed_synergy:the_shoal_fd"));
        assertEquals("resident", CampTemplateCatalog.residentVoice("oasis"));
    }

    @Test
    void filtersProhibitedContainerPathsUniformly() {
        assertTrue(PlayerOutpostData.isProhibitedContainerPath("oak_crafting_table"));
        assertTrue(PlayerOutpostData.isProhibitedContainerPath("Catalyzer"));
        assertTrue(PlayerOutpostData.isProhibitedContainerPath("chiseled_bookshelf"));
        assertFalse(PlayerOutpostData.isProhibitedContainerPath("oak_chest"));
        assertFalse(PlayerOutpostData.isProhibitedContainerPath("spruce_barrel"));
        assertFalse(PlayerOutpostData.isProhibitedContainerPath(""));
        assertFalse(PlayerOutpostData.isProhibitedContainerPath(null));
    }
}
