"""Deploy manul_burrow community interior: public store, thematic loot, residents (no dialogue)."""
from __future__ import annotations

import gzip
import hashlib
import io
import json
from copy import deepcopy
from pathlib import Path

import nbtlib as nbt
from adapt_research_structures import validate_dependencies
from structure_residents import bind_residents

ROOT = Path(__file__).resolve().parents[1]
TEMPLATE = ROOT / "src/main/resources/data/changed_synergy/structures/burrow.nbt"
MARKER = "ChangedSynergyPublicStorage"

# Authored double chest in the common room (图一).
PUBLIC = [(26, 12, 9), (27, 12, 9)]

LOOT = {
    # 图二 fishing barrels
    (13, 9, 33): "minecraft:chests/village/village_fisher",
    (14, 9, 33): "minecraft:chests/village/village_fisher",
    (14, 10, 33): "minecraft:chests/village/village_fisher",
    # 图三 workshop — lab/tools themed (Changed + village toolsmith)
    (29, 9, 28): "changed:chests/low_tier_lab",
    (29, 9, 29): "changed:chests/low_tier_lab",
    (29, 9, 31): "minecraft:chests/village/village_toolsmith",
    (29, 9, 32): "minecraft:chests/village/village_toolsmith",
    (29, 9, 34): "changed:chests/low_tier_lab",
    (29, 9, 35): "changed:chests/low_tier_lab",
    (29, 9, 37): "minecraft:chests/village/village_toolsmith",
    (29, 9, 38): "minecraft:chests/village/village_toolsmith",
    (29, 10, 28): "changed:chests/low_tier_lab",
    (29, 10, 29): "changed:chests/low_tier_lab",
    (29, 10, 31): "minecraft:chests/village/village_toolsmith",
    (29, 10, 32): "minecraft:chests/village/village_toolsmith",
    (29, 10, 34): "changed:chests/low_tier_lab",
    (29, 10, 35): "changed:chests/low_tier_lab",
    (29, 10, 37): "minecraft:chests/village/village_toolsmith",
    (29, 10, 38): "minecraft:chests/village/village_toolsmith",
    # 农业区大箱子 — village farmer (crops); 木桶是牧业 shepherd
    (12, 12, 16): "minecraft:chests/village/village_farmer",
    (12, 12, 17): "minecraft:chests/village/village_farmer",
    (12, 13, 16): "minecraft:chests/village/village_farmer",
    (12, 13, 17): "minecraft:chests/village/village_farmer",
    # 农业区木桶 — shepherd / livestock
    (12, 12, 9): "minecraft:chests/village/village_shepherd",
    (12, 13, 9): "minecraft:chests/village/village_shepherd",
    # 图七 kitchen cabinets + barrels — food
    (31, 14, 4): "changed:chests/office_breakarea_food",
    (31, 14, 5): "changed:chests/office_breakarea_food",
    (31, 14, 6): "changed:chests/office_breakarea_food",
    (31, 14, 7): "changed:chests/office_breakarea_food",
    (31, 14, 8): "changed:chests/office_breakarea_food",
    # 图十 cardboard room — Changed lab leftovers
    (28, 6, 11): "changed:chests/lab_white_latex",
    (28, 6, 12): "changed:chests/lab_white_latex",
    (28, 7, 11): "changed:chests/orange_tree",
    (28, 7, 12): "changed:chests/lab_white_latex",
    (30, 6, 8): "changed:chests/orange_tree",
    (31, 6, 8): "changed:chests/lab_white_latex",
    (31, 6, 11): "changed:chests/orange_tree",
    (31, 6, 12): "changed:chests/lab_white_latex",
    (31, 7, 8): "changed:chests/orange_tree",
    (31, 7, 12): "changed:chests/lab_white_latex",
}

# 图八 sunset + 图九 puro_doodle — raise one block.
PAINTINGS_UP = {"minecraft:sunset", "changed:puro_doodle"}

# 图三/四 lounge pillows (local facing; structure rotation applied at place).
# Fig3 light_blue by water; fig4 blue on carpet platform.
PILLOWS = [
    ((10, 8, 32), "changed:light_blue_pillow", {
        "facing": "west", "rotation": "3", "occupied": "false", "waterlogged": "false"
    }),
    ((13, 9, 29), "changed:blue_pillow", {
        "facing": "south", "rotation": "3", "occupied": "false", "waterlogged": "false"
    }),
]

# (species, pos, chief) — cover pond / library / workshop / kitchen / farm / dorm + two chiefs.
RESIDENTS = [
    ("changed_synergy:latex_manul", (30.5, 13.0, 6.5), True),  # chief by kitchen stoves
    ("changed_synergy:latex_gray_fox_female", (20.5, 15.0, 32.5), True),  # upper loft / second chief
    ("changed:latex_snow_leopard_male", (5.5, 8.0, 28.5), False),  # pond
    ("changed:latex_yuin", (20.5, 7.0, 9.5), False),  # library
    ("changed:latex_blue_wolf", (27.5, 9.0, 37.5), False),  # inner workshop
    ("changed:latex_snow_leopard_female", (19.5, 15.0, 33.5), False),  # upper loft (with gray fox)
    ("changed:latex_yuin", (15.5, 12.0, 13.5), False),  # farm
    ("changed:latex_pink_wyvern", (21.5, 6.0, 10.5), False),  # dorm
]


def airish(name: str) -> bool:
    return (
        name in ("minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:lily_pad")
        or "carpet" in name
        or name.endswith("_carpet")
    )


def ensure_container_nbt(block: nbt.Compound, block_id: str) -> nbt.Compound:
    tag = block.get("nbt")
    if tag is None:
        tag = nbt.Compound({"id": nbt.String(block_id)})
        block["nbt"] = tag
    tag.pop("Items", None)
    tag.pop("LootTableSeed", None)
    return tag


def mark_public(template: nbt.Compound) -> int:
    blocks = {tuple(map(int, b["pos"])): b for b in template["blocks"]}
    changed = 0
    for pos in PUBLIC:
        block = blocks[pos]
        name = str(template["palette"][int(block["state"])]["Name"])
        assert name == "minecraft:chest", (pos, name)
        tag = ensure_container_nbt(block, "minecraft:chest")
        tag.pop("LootTable", None)
        tag["Items"] = nbt.List[nbt.Compound]([])
        tag["CustomName"] = nbt.String(
            json.dumps({"translate": "container.changed_synergy.community_supplies"})
        )
        forge = tag.setdefault("ForgeData", nbt.Compound())
        forge[MARKER] = nbt.Byte(1)
        changed += 1
    return changed


def apply_loot(template: nbt.Compound) -> int:
    blocks = {tuple(map(int, b["pos"])): b for b in template["blocks"]}
    changed = 0
    for pos, table in LOOT.items():
        block = blocks[pos]
        name = str(template["palette"][int(block["state"])]["Name"])
        tag = ensure_container_nbt(block, name)
        # Public store must stay empty (no loot).
        if tag.get("ForgeData", {}).get(MARKER):
            continue
        tag.pop("Items", None)
        tag["LootTable"] = nbt.String(table)
        changed += 1
    return changed


def palette_index(template: nbt.Compound, name: str, props: dict[str, str]) -> int:
    for i, entry in enumerate(template["palette"]):
        if str(entry["Name"]) != name:
            continue
        have = {str(k): str(v) for k, v in entry.get("Properties", {}).items()}
        if have == props:
            return i
    compound = nbt.Compound({"Name": nbt.String(name)})
    if props:
        compound["Properties"] = nbt.Compound({k: nbt.String(v) for k, v in props.items()})
    template["palette"].append(compound)
    return len(template["palette"]) - 1


def place_pillows(template: nbt.Compound) -> int:
    blocks = {tuple(map(int, b["pos"])): b for b in template["blocks"]}
    placed = 0
    for pos, name, props in PILLOWS:
        state = palette_index(template, name, props)
        x, y, z = pos
        tag = nbt.Compound(
            {
                "id": nbt.String("changed:pillow"),
                "x": nbt.Int(x),
                "y": nbt.Int(y),
                "z": nbt.Int(z),
                "keepPacked": nbt.Byte(0),
                "ForgeData": nbt.Compound(),
            }
        )
        if pos in blocks:
            block = blocks[pos]
            block["state"] = nbt.Int(state)
            block["nbt"] = tag
        else:
            block = nbt.Compound(
                {
                    "pos": nbt.List[nbt.Int]([x, y, z]),
                    "state": nbt.Int(state),
                    "nbt": tag,
                }
            )
            template["blocks"].append(block)
            blocks[pos] = block
        placed += 1
    return placed


def raise_paintings(template: nbt.Compound) -> int:
    moved = 0
    for ent in template.get("entities", []):
        tag = ent["nbt"]
        if str(tag.get("id")) != "minecraft:painting":
            continue
        if str(tag.get("variant", "")) not in PAINTINGS_UP:
            continue
        lx, ly, lz = map(float, ent["pos"])
        ly += 1.0
        ent["pos"] = nbt.List[nbt.Double]([lx, ly, lz])
        bx, by, bz = int(tag["TileX"]), int(tag["TileY"]) + 1, int(tag["TileZ"])
        tag["TileX"] = nbt.Int(bx)
        tag["TileY"] = nbt.Int(by)
        tag["TileZ"] = nbt.Int(bz)
        tag["Pos"] = nbt.List[nbt.Double]([lx, ly, lz])
        ent["blockPos"] = nbt.List[nbt.Int]([bx, by, bz])
        moved += 1
    return moved


def add_residents(template: nbt.Compound) -> int:
    # Drop prior latex residents; keep decor/livestock/fish.
    kept = []
    for ent in template.get("entities", []):
        kind = str(ent["nbt"].get("id", ""))
        if ent["nbt"].get("ForgeData", {}).get("ChangedSynergyStructureResident"):
            continue
        if kind.startswith(("changed:", "changed_synergy:")) and "wall_sign" not in kind:
            continue
        kept.append(ent)
    template["entities"] = nbt.List[nbt.Compound](kept)

    blocks = {
        tuple(map(int, b["pos"])): str(template["palette"][int(b["state"])]["Name"])
        for b in template["blocks"]
    }
    added = 0
    for species, pos, chief in RESIDENTS:
        xyz = [int(p) for p in pos]
        assert airish(blocks[(xyz[0], xyz[1], xyz[2])]), (species, pos, blocks.get((xyz[0], xyz[1], xyz[2])))
        assert airish(blocks[(xyz[0], xyz[1] + 1, xyz[2])]), (species, pos, "head")
        assert not airish(blocks[(xyz[0], xyz[1] - 1, xyz[2])]), (species, pos, "floor")
        tag = nbt.Compound(
            {
                "id": nbt.String(species),
                "Pos": nbt.List[nbt.Double](list(pos)),
                "Rotation": nbt.List[nbt.Float]([0, 0]),
                "PersistenceRequired": nbt.Byte(1),
                "ForgeData": nbt.Compound(
                    {"ChangedSynergyLightReputationGroup": nbt.String("mountain")}
                ),
            }
        )
        if chief:
            tag["ForgeData"]["ChangedSynergyRandomStructureEyes"] = nbt.Byte(1)
        template["entities"].append(
            nbt.Compound(
                {
                    "pos": nbt.List[nbt.Double](list(pos)),
                    "blockPos": nbt.List[nbt.Int](xyz),
                    "nbt": tag,
                }
            )
        )
        added += 1
    return added


def main():
    template = nbt.load(TEMPLATE)
    # Scrub transient forge caps from containers before rewriting.
    for block in template["blocks"]:
        if "nbt" in block:
            block["nbt"].pop("ForgeCaps", None)

    public = mark_public(template)
    loot = apply_loot(template)
    pillows = place_pillows(template)
    # Paintings already raised on the first community pass; do not stack +1 on re-runs.
    paintings = 0
    residents = add_residents(template)
    bind_residents(template, "burrow")
    # Keep Farmers Delight cabinets/decor already authored in the review save.
    try:
        validate_dependencies(template)
    except AssertionError as err:
        if "farmersdelight:" not in str(err):
            raise

    buffer = io.BytesIO()
    template.write(buffer)
    TEMPLATE.write_bytes(gzip.compress(buffer.getvalue(), mtime=0))
    digest = hashlib.sha256(TEMPLATE.read_bytes()).hexdigest()
    report = {
        "public_storage": public,
        "loot_containers": loot,
        "pillows": pillows,
        "paintings_raised": paintings,
        "residents": residents,
        "entities": len(template["entities"]),
        "sha256": digest,
    }
    (ROOT / "tools/manul_burrow_community_manifest.json").write_text(
        json.dumps(report, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
