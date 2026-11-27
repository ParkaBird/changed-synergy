package net.parkabird.changedsynergy.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class BondedCreatureDeathDataTest {
    @Test
    void blankDeathTombstonePreservesExistingRichNotice() {
        BondedCreatureDeathData data = new BondedCreatureDeathData();
        UUID owner = UUID.randomUUID();
        UUID pet = UUID.randomUUID();

        CompoundTag notice = new CompoundTag();
        notice.putString("PetName", "Shiro");
        notice.putBoolean("Notified", false);
        data.markDead(owner, pet, notice);

        // Two-arg call must not overwrite an unread rich notice with an empty tag.
        data.markDead(owner, pet);
        assertTrue(data.isDead(owner, pet));

        Map<UUID, CompoundTag> consumed = data.consume(owner);
        assertEquals("Shiro", consumed.get(pet).getString("PetName"));
        assertFalse(consumed.get(pet).getBoolean("Notified"));
        assertFalse(data.isDead(owner, pet));
    }

    @Test
    void releaseTombstonesTrackAndConsumePerOwner() {
        BondedCreatureReleaseData data = new BondedCreatureReleaseData();
        UUID owner = UUID.randomUUID();
        UUID creature = UUID.randomUUID();

        assertFalse(data.isReleased(owner, creature));
        data.markReleased(owner, creature);
        assertTrue(data.isReleased(owner, creature));
        assertTrue(data.consume(creature).contains(owner));
        assertFalse(data.isReleased(owner, creature));
    }

    @Test
    void revivalAppliedPlayersAreRecordedAndIdempotent() {
        BondedRevivalData data = new BondedRevivalData();
        UUID player = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        CompoundTag record = new CompoundTag();
        record.putString("State", "complete");
        UUID token = data.create(record);

        data.markPlayerApplied(token, player);
        data.markPlayerApplied(token, player);

        CompoundTag fetched = data.recordsWithTokens().get(token);
        assertNotNull(fetched);
        assertTrue(BondedRevivalData.isPlayerApplied(fetched, player));
        assertFalse(BondedRevivalData.isPlayerApplied(fetched, other));
        assertEquals(1, fetched.getList("AppliedPlayers", Tag.TAG_STRING).size());
    }
}
