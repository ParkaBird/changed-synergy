"""Extract edited FD & No-FD The Shoal templates from the test save and author camp metadata."""
from __future__ import annotations

from copy import deepcopy
import gzip
import hashlib
import io
import json
import struct
import zlib
from pathlib import Path

import nbtlib as nbt

ROOT = Path(__file__).resolve().parents[1]
SAVE_DIR = Path(r"D:\Program Files\.minecraft\versions\测试专用\saves\The Shoal")
OUT_DIR = ROOT / "src/main/resources/data/changed_synergy/structures"

SIZE = (23, 20, 35)
ORIGIN_FD = (0, 62, 0)
ORIGIN_NOFD = (48, 62, 0)

PUBLIC_CHESTS = [
    ((8, 5, 6), "left"),
    ((8, 5, 7), "right"),
]

# Private containers: Dorm chest (6, 5, 10) + Red Boss Room barrels & cabinets (inside x=5..16, z=25..32)
PRIVATE_STORES = [
    (6, 5, 10),
    (6, 5, 29),
    (6, 5, 30),
    (15, 5, 29),
    (10, 5, 31),
    (10, 6, 31),
    (10, 7, 31),
    (15, 5, 31),
    (15, 6, 31),
    (15, 7, 31),
]

# Narrative fragments (English-only) & Dorm private chest note
DISC_FRAME_POS = [11.5, 6.03125, 31.5]
MANIFEST_NOTE_POS = (11, 6, 31)
DORM_NOTE_POS = (6, 6, 10)

SHOAL_DISC_TEXT = """From: diving_partner@reef-salvage.net
To: scavenger@the-shoal.net
Subject: Left something in your locker / Take care out there
Date: October 14

Hey,

I folded and left the spare charcoal-and-blue suit in your locker before my transfer out today. Yes, it's the one with the blue leg stripe you kept insisting makes you look faster in the water.

Please quit diving solo past the drop-off whenever the swell picks up, alright? Even if you spot a whole shipping container full of gold down there, wrestling the tide alone just isn't worth it.

Keep your head above water, old friend. Next time we get shore leave, the first crate of oranges is on me.

Best,
Your Dive Partner"""

SHOAL_MANIFEST_TEXT = """SALVAGE TALLY: EAST DECK

- 1 orange shipping container (mostly wet cardboard, dry walls—keeper)
- 1 blue shipping container (good dry storage once we patched the latch)
- 1 white office chair (wheel squeaks, boss insisted we keep it)
- 5 dented doorway slats replaced this month (boss says it was only 3)

NOTE TO SELF: Still zero oranges on the shoal. Remind whoever goes to shore next."""

DORM_NOTE_TEXT = """DORM LOCKERS — HANDS OFF

Personal gear and bedside snacks stay with the bunk owners.
Shared food goes in the double chest out on the storage deck.

(And yes, boss, that includes your emergency oranges — quit leaving peels under the pillows.)
— Eel"""

# Residents: Boss Swordfish wearing Wetsuit near Fig 1 campfire + 4 camp residents
RESIDENTS = [
    {
        "id": "changed_synergy:latex_swordfish",
        "pos": [16.5, 4.5, 12.5],
        "blockPos": [16, 4, 12],
        "yaw": 90.0,
        "character": True,
        "outfit": "changed:wetsuit",
        "role": "campfire",
    },
    {
        "id": "changed:latex_orca",
        "pos": [18.5, 5.0, 29.5],
        "blockPos": [18, 5, 29],
        "yaw": 180.0,
        "character": False,
        "outfit": "",
        "role": "fishing",
    },
    {
        "id": "changed:latex_eel",
        "pos": [7.5, 5.0, 12.5],
        "blockPos": [7, 5, 12],
        "yaw": 0.0,
        "character": False,
        "outfit": "",
        "role": "dorm",
    },
    {
        "id": "changed:latex_shark",
        "pos": [10.5, 5.0, 6.5],
        "blockPos": [10, 5, 6],
        "yaw": 90.0,
        "character": False,
        "outfit": "",
        "role": "storage",
    },
    {
        "id": "changed:latex_shark",
        "pos": [9.5, 5.0, 17.5],
        "blockPos": [9, 5, 17],
        "yaw": 0.0,
        "character": False,
        "outfit": "",
        "role": "workshop",
    },
]


def read_mca(path: Path):
    data = path.read_bytes()
    locs = data[:4096]
    chunks = {}
    for i in range(1024):
        off = struct.unpack(">I", locs[i * 4 : i * 4 + 4])[0] >> 8
        cnt = locs[i * 4 + 3]
        if off == 0 or cnt == 0:
            continue
        pos = off * 4096
        length, _ = struct.unpack(">IB", data[pos : pos + 5])
        raw = zlib.decompress(data[pos + 5 : pos + 4 + length])
        chunks[(i % 32, i // 32)] = nbt.File.parse(io.BytesIO(raw))
    return chunks


def decode_sec(sec):
    bs = sec["block_states"]
    pal = bs["palette"]
    if len(pal) == 1 or "data" not in bs:
        return [0] * 4096
    bits = max(4, (len(pal) - 1).bit_length())
    per_long = 64 // bits
    mask = (1 << bits) - 1
    longs = [int(v) & 0xFFFFFFFFFFFFFFFF for v in bs["data"]]
    return [(longs[i // per_long] >> ((i % per_long) * bits)) & mask for i in range(4096)]


def clone_state(entry):
    out = nbt.Compound({"Name": nbt.String(str(entry["Name"]))})
    if "Properties" in entry and entry["Properties"]:
        out["Properties"] = nbt.Compound(
            {k: nbt.String(str(v)) for k, v in sorted(entry["Properties"].items())}
        )
    return out


def state_key(entry):
    props = tuple(sorted((k, str(v)) for k, v in entry.get("Properties", {}).items()))
    return (str(entry["Name"]), props)


def extract_template(region_chunks, entity_chunks, origin, size=SIZE):
    ox, oy, oz = origin
    sx, sy, sz = size
    grid = {}
    bes = {}

    for cx in range(ox >> 4, ((ox + sx - 1) >> 4) + 1):
        for cz in range(oz >> 4, ((oz + sz - 1) >> 4) + 1):
            chunk = region_chunks[(cx, cz)]
            for be in chunk.get("block_entities", []):
                bx, by, bz = int(be["x"]), int(be["y"]), int(be["z"])
                if ox <= bx < ox + sx and oy <= by < oy + sy and oz <= bz < oz + sz:
                    clean_be = nbt.Compound()
                    for k, v in be.items():
                        if k in ("x", "y", "z", "keepPacked", "ForgeCaps"):
                            continue
                        clean_be[k] = deepcopy(v)
                    clean_be.pop("ForgeData", None)
                    bes[(bx - ox, by - oy, bz - oz)] = clean_be

            for sec in chunk["sections"]:
                sec_y = int(sec["Y"])
                pal = sec["block_states"]["palette"]
                idxs = decode_sec(sec)
                for ly in range(16):
                    wy = sec_y * 16 + ly
                    if not (oy <= wy < oy + sy):
                        continue
                    for lz in range(16):
                        wz = cz * 16 + lz
                        if not (oz <= wz < oz + sz):
                            continue
                        for lx in range(16):
                            wx = cx * 16 + lx
                            if not (ox <= wx < ox + sx):
                                continue
                            entry = pal[idxs[ly * 256 + lz * 16 + lx]]
                            grid[(wx - ox, wy - oy, wz - oz)] = clone_state(entry)

    # Extract localized entities inside box
    entities = []
    for cx in range(ox >> 4, ((ox + sx - 1) >> 4) + 1):
        for cz in range(oz >> 4, ((oz + sz - 1) >> 4) + 1):
            ec = entity_chunks.get((cx, cz))
            if not ec:
                continue
            for ent in ec.get("Entities", []):
                ex, ey, ez = [float(v) for v in ent["Pos"]]
                if not (ox <= ex < ox + sx and oy <= ey < oy + sy and oz <= ez < oz + sz):
                    continue
                eid = str(ent.get("id", ""))
                if eid.startswith(("changed:", "changed_synergy:")) and "armor_stand" not in eid:
                    continue
                if eid == "minecraft:item":
                    continue
                lx, ly, lz = ex - ox, ey - oy, ez - oz
                local_pos = nbt.List[nbt.Double]([lx, ly, lz])
                if "TileX" in ent:
                    bx = int(ent["TileX"]) - ox
                    by = int(ent["TileY"]) - oy
                    bz = int(ent["TileZ"]) - oz
                else:
                    bx, by, bz = int(lx), int(ly), int(lz)
                local_block_pos = nbt.List[nbt.Int]([bx, by, bz])
                tag = deepcopy(ent)
                for drop_key in ("UUID", "ForgeCaps", "ForgeData", "Attributes"):
                    tag.pop(drop_key, None)
                tag["Pos"] = deepcopy(local_pos)
                if "TileX" in tag:
                    tag["TileX"] = nbt.Int(bx)
                    tag["TileY"] = nbt.Int(by)
                    tag["TileZ"] = nbt.Int(bz)
                if eid == "changed:biped_armor_stand":
                    tag["Invulnerable"] = nbt.Byte(1)
                    tag["ShowArms"] = nbt.Byte(1)
                    tag["DisabledSlots"] = nbt.Int(4144959)
                    tag["ForgeData"] = nbt.Compound({"ChangedSynergyDisplayOnly": nbt.Byte(1)})
                    tag["ChangedAccessorySlots"] = nbt.Compound(
                        {
                            "changed:body": nbt.Compound(
                                {
                                    "id": nbt.String("changed:wetsuit"),
                                    "Count": nbt.Byte(1),
                                }
                            )
                        }
                    )
                entities.append(
                    nbt.Compound(
                        {
                            "pos": local_pos,
                            "blockPos": local_block_pos,
                            "nbt": tag,
                        }
                    )
                )

    return grid, bes, entities


def build_structure_nbt(grid, bes, entities, label):
    palette = []
    key_to_idx = {}
    blocks = []

    for y in range(SIZE[1]):
        for z in range(SIZE[2]):
            for x in range(SIZE[0]):
                state = grid[(x, y, z)]
                sk = state_key(state)
                if sk not in key_to_idx:
                    key_to_idx[sk] = len(palette)
                    palette.append(state)
                b_tag = nbt.Compound(
                    {
                        "pos": nbt.List[nbt.Int]([x, y, z]),
                        "state": nbt.Int(key_to_idx[sk]),
                    }
                )
                if (x, y, z) in bes:
                    b_tag["nbt"] = deepcopy(bes[(x, y, z)])
                blocks.append(b_tag)

    # Mark public storage double chest (8,5,6..7)
    blocks_by_pos = {tuple(int(v) for v in b["pos"]): b for b in blocks}
    for pos, expected_side in PUBLIC_CHESTS:
        blk = blocks_by_pos[pos]
        st = palette[int(blk["state"])]
        assert str(st["Name"]) == "minecraft:chest", (label, pos, st)
        assert str(st["Properties"]["type"]) == expected_side, (label, pos, st)
        assert "nbt" in blk, (label, pos, "missing chest NBT")
        blk["nbt"]["CustomName"] = nbt.String(
            json.dumps({"translate": "container.changed_synergy.community_supplies"})
        )
        blk["nbt"].setdefault("ForgeData", nbt.Compound())[
            "ChangedSynergyPublicStorage"
        ] = nbt.Byte(1)

    # Mark private storage containers (Dorm chest + Red Boss Room barrels/cabinets)
    private_set = set(PRIVATE_STORES)
    public_set = {pos for pos, _ in PUBLIC_CHESTS}
    for pos in PRIVATE_STORES:
        blk = blocks_by_pos[pos]
        nm = str(palette[int(blk["state"])]["Name"])
        assert nm in ("minecraft:chest", "minecraft:barrel", "farmersdelight:spruce_cabinet"), (
            label,
            pos,
            nm,
        )
        assert "nbt" in blk, (label, pos, "missing container BE")
        blk["nbt"].setdefault("ForgeData", nbt.Compound())["ChangedSynergyPrivate"] = nbt.Byte(1)
        blk["nbt"]["LootTable"] = nbt.String("changed_synergy:chests/shoal_boss_stash")

    # Assign dock/workshop supply loot to all other non-public storage containers
    loot_containers = (
        "minecraft:barrel",
        "minecraft:chest",
        "farmersdelight:acacia_cabinet",
        "farmersdelight:spruce_cabinet",
        "farmersdelight:bamboo_basket",
        "farmersdelight:wooden_basket",
    )
    for pos, blk in blocks_by_pos.items():
        if pos in public_set or pos in private_set:
            continue
        nm = str(palette[int(blk["state"])]["Name"])
        if nm in loot_containers:
            assert "nbt" in blk, (label, pos, "missing container BE for loot")
            blk["nbt"]["LootTable"] = nbt.String("changed_synergy:chests/shoal_dock_supplies")

    # Verify display wetsuit armor stand is present and configured cleanly
    stands = [e for e in entities if str(e["nbt"]["id"]) == "changed:biped_armor_stand"]
    assert len(stands) == 1, (label, "expected 1 biped_armor_stand, got", len(stands))
    assert str(stands[0]["nbt"]["ChangedAccessorySlots"]["changed:body"]["id"]) == "changed:wetsuit", stands[0]

    # Configure Fragment 1 (compact disc in an invisible item frame at [11.5, 6.03125, 31.5]),
    # Fragment 2 (salvage tally note at (11, 6, 31)), and Dorm chest note at (6, 6, 10).
    for note_pos, note_text in ((MANIFEST_NOTE_POS, SHOAL_MANIFEST_TEXT), (DORM_NOTE_POS, DORM_NOTE_TEXT)):
        note_blk = blocks_by_pos[note_pos]
        note_name = str(palette[int(note_blk["state"])]["Name"])
        assert note_name == "changed:note", (label, note_pos, note_name)
        note_blk["nbt"] = nbt.Compound(
            {
                "ForgeData": nbt.Compound({}),
                "Text": nbt.String(note_text),
                "id": nbt.String("changed:text_block_entity"),
            }
        )

    disc_item = nbt.Compound(
        {
            "id": nbt.String("changed:compact_disc"),
            "Count": nbt.Byte(1),
            "tag": nbt.Compound({"Text": nbt.String(SHOAL_DISC_TEXT)}),
        }
    )
    disc_frame_nbt = nbt.Compound(
        {
            "id": nbt.String("minecraft:item_frame"),
            "Invisible": nbt.Byte(1),
            "Fixed": nbt.Byte(0),
            "Facing": nbt.Byte(1),
            "ItemDropChance": nbt.Float(1.0),
            "ItemRotation": nbt.Byte(0),
            "TileX": nbt.Int(int(DISC_FRAME_POS[0])),
            "TileY": nbt.Int(int(DISC_FRAME_POS[1])),
            "TileZ": nbt.Int(int(DISC_FRAME_POS[2])),
            "Pos": nbt.List[nbt.Double]([nbt.Double(v) for v in DISC_FRAME_POS]),
            "Item": disc_item,
        }
    )
    filtered_entities = [
        e
        for e in entities
        if not (
            str(e["nbt"].get("id", "")) == "minecraft:item_frame"
            and [round(float(v), 5) for v in e["pos"]] == [round(v, 5) for v in DISC_FRAME_POS]
        )
    ]
    filtered_entities.append(
        nbt.Compound(
            {
                "pos": nbt.List[nbt.Double]([nbt.Double(v) for v in DISC_FRAME_POS]),
                "blockPos": nbt.List[nbt.Int](
                    [nbt.Int(int(DISC_FRAME_POS[0])), nbt.Int(int(DISC_FRAME_POS[1])), nbt.Int(int(DISC_FRAME_POS[2]))]
                ),
                "nbt": disc_frame_nbt,
            }
        )
    )

    # Add 5 resident entities cleanly on walkable surfaces
    all_entities = list(filtered_entities)
    for res in RESIDENTS:
        lx, ly, lz = res["pos"]
        bx, by, bz = res["blockPos"]
        clear_y = int(ly + 0.5) if ly % 1.0 > 0 else by
        support_y = clear_y - 1
        at_name = str(grid[(bx, clear_y, bz)]["Name"])
        above_name = str(grid[(bx, clear_y + 1, bz)]["Name"])
        below_name = str(grid[(bx, support_y, bz)]["Name"])
        assert at_name == "minecraft:air", (label, res["id"], (bx, clear_y, bz), at_name)
        assert above_name == "minecraft:air", (label, res["id"], (bx, clear_y + 1, bz), above_name)
        assert below_name not in ("minecraft:air", "minecraft:water"), (label, res["id"], below_name)

        local_pos = nbt.List[nbt.Double]([lx, ly, lz])
        forge_data = nbt.Compound(
            {
                "ChangedSynergyRandomStructureEyes": nbt.Byte(1),
                "ChangedSynergyStructureResident": nbt.Compound(
                    {
                        "Template": nbt.String("the_shoal"),
                        "LocalPos": deepcopy(local_pos),
                        "Size": nbt.List[nbt.Int](list(SIZE)),
                        "Yaw": nbt.Float(res["yaw"]),
                        "Outfit": nbt.String(res["outfit"]),
                        "Camp": nbt.Byte(1),
                        "Character": nbt.Byte(1 if res["character"] else 0),
                        "DialogueRole": nbt.String(res["role"]),
                    }
                ),
            }
        )
        ent_nbt = nbt.Compound(
            {
                "id": nbt.String(res["id"]),
                "Pos": deepcopy(local_pos),
                "Rotation": nbt.List[nbt.Float]([res["yaw"], 0.0]),
                "PersistenceRequired": nbt.Byte(1),
                "ForgeData": forge_data,
            }
        )
        all_entities.append(
            nbt.Compound(
                {
                    "pos": local_pos,
                    "blockPos": nbt.List[nbt.Int]([bx, by, bz]),
                    "nbt": ent_nbt,
                }
            )
        )

    tpl = nbt.File(
        nbt.Compound(
            {
                "DataVersion": nbt.Int(3465),
                "size": nbt.List[nbt.Int](list(SIZE)),
                "palette": nbt.List[nbt.Compound](palette),
                "blocks": nbt.List[nbt.Compound](blocks),
                "entities": nbt.List[nbt.Compound](all_entities),
            }
        )
    )
    return tpl


def write_nbt(tpl, path: Path):
    buf = io.BytesIO()
    tpl.write(buf)
    path.write_bytes(gzip.compress(buf.getvalue(), mtime=0))


def main():
    region_chunks = read_mca(SAVE_DIR / "region" / "r.0.0.mca")
    entity_chunks = read_mca(SAVE_DIR / "entities" / "r.0.0.mca")

    grid_fd, bes_fd, ents_fd = extract_template(region_chunks, entity_chunks, ORIGIN_FD)
    grid_nofd, bes_nofd, ents_nofd = extract_template(region_chunks, entity_chunks, ORIGIN_NOFD)

    # Sync dorm private chest (6, 5, 10) and recent FD-side edits onto No-FD side cleanly
    sync_positions = [
        (6, 5, 10),
        (7, 5, 29),
        (6, 9, 26),
        (7, 9, 26),
        (8, 9, 26),
        (9, 9, 26),
        (10, 9, 26),
        (10, 5, 12),
        (10, 6, 12),
        (11, 5, 20),
        (11, 6, 20),
        (11, 6, 18),
        (11, 7, 18),
        (11, 8, 18),
    ]
    for sync_pos in sync_positions:
        grid_nofd[sync_pos] = deepcopy(grid_fd[sync_pos])
        if sync_pos in bes_fd:
            bes_nofd[sync_pos] = deepcopy(bes_fd[sync_pos])
        else:
            bes_nofd.pop(sync_pos, None)

    # Sync the 5 new item frames on the mangrove planter signs at (6..10, 9, 26)
    planter_frame_blocks = {(x, 9, 26) for x in range(6, 11)}
    ents_nofd = [
        e
        for e in ents_nofd
        if not (
            str(e["nbt"].get("id", "")) == "minecraft:item_frame"
            and tuple(int(v) for v in e["blockPos"]) in planter_frame_blocks
        )
    ]
    for e in ents_fd:
        if (
            str(e["nbt"].get("id", "")) == "minecraft:item_frame"
            and tuple(int(v) for v in e["blockPos"]) in planter_frame_blocks
        ):
            ents_nofd.append(deepcopy(e))

    # Replace workshop box_pile at (7, 5..6, 20..21) with small cardboard box + cardboard container + clipboard on top
    for grid, bes in ((grid_fd, bes_fd), (grid_nofd, bes_nofd)):
        grid[(7, 5, 20)] = nbt.Compound(
            {
                "Name": nbt.String("changed:cardboard_box_small"),
                "Properties": nbt.Compound({"rotation": nbt.String("6"), "waterlogged": nbt.String("false")}),
            }
        )
        grid[(7, 6, 20)] = nbt.Compound({"Name": nbt.String("minecraft:air")})
        grid[(7, 5, 21)] = nbt.Compound(
            {
                "Name": nbt.String("changed:cardboard_container"),
                "Properties": nbt.Compound({"facing": nbt.String("east")}),
            }
        )
        grid[(7, 6, 21)] = nbt.Compound(
            {
                "Name": nbt.String("changed:clipboard"),
                "Properties": nbt.Compound({"facing": nbt.String("east")}),
            }
        )
        bes.pop((7, 5, 20), None)
        bes.pop((7, 6, 20), None)
        bes[(7, 5, 21)] = nbt.Compound(
            {
                "id": nbt.String("changed:cardboard_container"),
                "LootTable": nbt.String("changed_synergy:chests/shoal_dock_supplies"),
            }
        )
        bes[(7, 6, 21)] = nbt.Compound(
            {
                "id": nbt.String("changed:text_block_entity"),
                "Text": nbt.String(""),
            }
        )

    # Keep 2F fireplace chimney walls connected only between the two chimney columns
    for grid in (grid_fd, grid_nofd):
        for cy in (12, 13, 14):
            for cx, east, west in ((6, "tall", "none"), (7, "none", "tall")):
                pos = (cx, cy, 16)
                if pos in grid and "Properties" in grid[pos]:
                    props = grid[pos]["Properties"]
                    props["east"] = nbt.String(east)
                    props["west"] = nbt.String(west)
                    props["north"] = nbt.String("none")
                    props["south"] = nbt.String("none")
                    props["up"] = nbt.String("true")

    # Sync stacked mug counts at (3, 6, 19) -> 3 and (15, 6, 28) -> 4
    for grid in (grid_fd, grid_nofd):
        for pos, count in (((3, 6, 19), "3"), ((15, 6, 28), "4")):
            if pos in grid and str(grid[pos]["Name"]) == "changed:mug":
                grid[pos].setdefault("Properties", nbt.Compound())["mugs"] = nbt.String(count)

    # Verify zero farmersdelight blocks/BEs in No-FD version
    fd_leaks = [
        (p, str(s["Name"]))
        for p, s in grid_nofd.items()
        if str(s["Name"]).startswith("farmersdelight:")
    ]
    assert not fd_leaks, f"FD blocks leaked into the_shoal.nbt: {fd_leaks}"
    for p, be in bes_nofd.items():
        assert "farmersdelight" not in str(be), f"FD BE leaked at {p}: {be}"

    tpl_fd = build_structure_nbt(grid_fd, bes_fd, ents_fd, "the_shoal_fd")
    tpl_nofd = build_structure_nbt(grid_nofd, bes_nofd, ents_nofd, "the_shoal")

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    fd_out = OUT_DIR / "the_shoal_fd.nbt"
    nofd_out = OUT_DIR / "the_shoal.nbt"
    write_nbt(tpl_fd, fd_out)
    write_nbt(tpl_nofd, nofd_out)

    for label, path, tpl in [("the_shoal_fd", fd_out, tpl_fd), ("the_shoal", nofd_out, tpl_nofd)]:
        fd_cnt = sum(
            1
            for b in tpl["blocks"]
            if str(tpl["palette"][int(b["state"])]["Name"]).startswith("farmersdelight:")
        )
        print(
            f"{label}: blocks={len(tpl['blocks'])}, palette={len(tpl['palette'])}, "
            f"entities={len(tpl['entities'])}, fd_blocks={fd_cnt}, sha256={hashlib.sha256(path.read_bytes()).hexdigest()[:12]}"
        )


if __name__ == "__main__":
    main()
