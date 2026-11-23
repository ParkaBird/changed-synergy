"""Bake Burrow narrative fragments into burrow.nbt (note, disc frame, computer text)."""
from __future__ import annotations

import gzip
import hashlib
import io
from pathlib import Path

import nbtlib as nbt

ROOT = Path(__file__).resolve().parents[1]
STRUCTURE = ROOT / "src/main/resources/data/changed_synergy/structures/burrow.nbt"

HOUSE_RULES = """HOUSE RULES (READ ONCE, THEN LEAVE ME ALONE)

1. Wipe snow off your boots/paws BEFORE the rug.
2. Stop leaving empty soup cans around the upstairs tree.
3. Hot food goes on the red counter. Take a plate. You do not need to make conversation with me to eat.
4. Stop hiding in the cardboard box room every time someone knocks upstairs. You keep tipping the crates over.

— Ignore #4 if your battery is dead today, I'll handle the door! :)"""

MOVE_IN = """Oct 14: Lower level wired. Warm-air heating is running steady, and we packed the back corner with enough ice and snow to keep me from overheating.

She didn't want me to sign the clinic form. Said she'd already made peace with going through it alone, and I shouldn't throw away a healthy body just to keep up.

Told her that wasn't really a choice. No point staying human if it meant watching her walk into the next hundred years by herself.

Our double bed is down in the lowest room we could dig: warm air by the doorway for her, a whole pile of ice and snow on my side so this Manul fur doesn't cook me alive. She's already complaining that I kick off the blankets. Good. Means we're still here."""

# Shelf the screenshots target: top spruce stair under the book stack, cyan-carpet bedroom.
DISC_POS = [18.5, 2.03125, 31.5]
# Kitchen house-rules note: two blocks left of the chest column when facing the south wall (+X).
NOTE_POS = [28, 13, 9]
STALE_NOTES = [[26, 13, 9], [28, 8, 12]]
STALE_DISCS = [[14.5, 7.03125, 9.5], [21.5, 7.03125, 8.5]]


def palette_index(template: nbt.File, name: str, props: dict[str, str] | None = None) -> int:
    props = props or {}
    for i, entry in enumerate(template["palette"]):
        if str(entry["Name"]) != name:
            continue
        current = {str(k): str(v) for k, v in dict(entry.get("Properties", {})).items()}
        if current == props:
            return i
    compound = nbt.Compound({"Name": nbt.String(name)})
    if props:
        compound["Properties"] = nbt.Compound({k: nbt.String(v) for k, v in props.items()})
    template["palette"].append(compound)
    return len(template["palette"]) - 1


def set_block(template: nbt.File, pos: list[int], name: str, props: dict[str, str] | None = None, block_nbt: nbt.Compound | None = None):
    state = palette_index(template, name, props)
    for block in template["blocks"]:
        if list(map(int, block["pos"])) == pos:
            block["state"] = nbt.Int(state)
            if block_nbt is not None:
                block["nbt"] = block_nbt
            elif "nbt" in block:
                del block["nbt"]
            return
    record = nbt.Compound({"pos": nbt.List[nbt.Int]([nbt.Int(v) for v in pos]), "state": nbt.Int(state)})
    if block_nbt is not None:
        record["nbt"] = block_nbt
    template["blocks"].append(record)


def remove_block(template: nbt.File, pos: list[int]):
    template["blocks"] = nbt.List[nbt.Compound](
        [block for block in template["blocks"] if list(map(int, block["pos"])) != pos]
    )


def remove_entity_at(template: nbt.File, pos: list[float]):
    target = [round(v, 5) for v in pos]
    template["entities"] = nbt.List[nbt.Compound](
        [entity for entity in template["entities"] if [round(float(v), 5) for v in entity["pos"]] != target]
    )


def upsert_entity(template: nbt.File, pos: list[float], entity_nbt: nbt.Compound):
    for entity in template["entities"]:
        if [round(float(v), 5) for v in entity["pos"]] == [round(v, 5) for v in pos]:
            entity["nbt"] = entity_nbt
            entity["blockPos"] = nbt.List[nbt.Int]([nbt.Int(int(pos[0])), nbt.Int(int(pos[1])), nbt.Int(int(pos[2]))])
            return
    template["entities"].append(
        nbt.Compound(
            {
                "pos": nbt.List[nbt.Double]([nbt.Double(v) for v in pos]),
                "blockPos": nbt.List[nbt.Int]([nbt.Int(int(pos[0])), nbt.Int(int(pos[1])), nbt.Int(int(pos[2]))]),
                "nbt": entity_nbt,
            }
        )
    )


def save(template: nbt.File, path: Path):
    buffer = io.BytesIO()
    template.write(buffer)
    path.write_bytes(gzip.compress(buffer.getvalue(), mtime=0))


def _move_painting(entity: nbt.Compound, tile: list[int], pos: list[float], facing: int | None = None):
    tag = entity["nbt"]
    entity["pos"] = nbt.List[nbt.Double]([nbt.Double(v) for v in pos])
    entity["blockPos"] = nbt.List[nbt.Int]([nbt.Int(v) for v in tile])
    tag["Pos"] = nbt.List[nbt.Double]([nbt.Double(v) for v in pos])
    tag["TileX"], tag["TileY"], tag["TileZ"] = nbt.Int(tile[0]), nbt.Int(tile[1]), nbt.Int(tile[2])
    if facing is not None:
        tag["facing"] = nbt.Byte(facing)


def repair_paintings(template: nbt.File) -> int:
    """Keep Burrow paintings on solid walls with clear fronts so they stop popping off."""
    fixed = 0
    for entity in template["entities"]:
        tag = entity["nbt"]
        if str(tag.get("id", "")) != "minecraft:painting":
            continue
        variant = str(tag.get("variant", ""))
        tile = [int(tag["TileX"]), int(tag["TileY"]), int(tag["TileZ"])]
        # plant: TileX was one block into a double spruce wall; hang on the air side.
        if variant == "minecraft:plant" and tile == [38, 8, 7]:
            _move_painting(entity, [37, 8, 7], [37.5, 8.5, 8.03125], facing=0)
            fixed += 1
        # bust: keep east-facing 2x2 on z=20..21 so the hanging lantern at z=22 is clear.
        elif variant == "minecraft:bust" and tile == [22, 5, 21]:
            _move_painting(entity, [22, 5, 20], [23.03125, 5.0, 21.0], facing=3)
            fixed += 1
        # sunset: raised into a door/leaf pocket; park it on the clear south dorm wall.
        elif variant == "minecraft:sunset" and tile[0] == 29 and tile[2] in (23, 24):
            _move_painting(entity, [35, 8, 7], [35.0, 8.5, 8.03125], facing=0)
            fixed += 1
    return fixed


def main():
    template = nbt.load(STRUCTURE)

    for pos in STALE_NOTES:
        remove_block(template, pos)
    for pos in STALE_DISCS:
        remove_entity_at(template, pos)
    remove_entity_at(template, DISC_POS)

    # Old note cell must be explicit air, or the snow hill's stone shows through the kitchen.
    set_block(template, [26, 13, 9], "minecraft:air")

    # Kitchen house-rules note, in the empty cell directly above the community chest.
    set_block(
        template,
        NOTE_POS,
        "changed:note",
        {"facing": "north"},
        nbt.Compound(
            {
                "ForgeData": nbt.Compound({}),
                "Text": nbt.String(HOUSE_RULES),
                "id": nbt.String("changed:text_block_entity"),
            }
        ),
    )

    # Fragment 1: master-suite computer desktop text (TextBlockEntity / clue:burrow_terminal).
    set_block(
        template,
        [28, 2, 33],
        "changed:computer",
        {"facing": "north"},
        nbt.Compound(
            {
                "ForgeData": nbt.Compound({}),
                "Text": nbt.String(MOVE_IN),
                "id": nbt.String("changed:text_block_entity"),
            }
        ),
    )

    # Invisible item frame on the bedroom shelf stair, beside the book stack.
    disc = nbt.Compound(
        {
            "id": nbt.String("changed:compact_disc"),
            "Count": nbt.Byte(1),
            "tag": nbt.Compound({"Text": nbt.String("move_in.txt\n\n" + MOVE_IN)}),
        }
    )
    frame = nbt.Compound(
        {
            "id": nbt.String("minecraft:item_frame"),
            "Invisible": nbt.Byte(1),
            "Fixed": nbt.Byte(0),
            "Facing": nbt.Byte(1),
            "ItemDropChance": nbt.Float(1.0),
            "ItemRotation": nbt.Byte(0),
            "Item": disc,
            "UUID": nbt.IntArray(
                [nbt.Int(0x62757272), nbt.Int(0x6F776469), nbt.Int(0x73636672), nbt.Int(0x616D6501)]
            ),
        }
    )
    upsert_entity(template, DISC_POS, frame)

    for entity in template["entities"]:
        tag = entity["nbt"]
        if str(tag.get("id", "")) == "changed_synergy:latex_gray_fox_female":
            tag["CustomName"] = nbt.String('{"translate":"npc.changed_synergy.gray_fox_chief.title"}')

    repair_paintings(template)

    save(template, STRUCTURE)
    digest = hashlib.sha256(STRUCTURE.read_bytes()).hexdigest()
    print("baked burrow fragments", digest)
    print("note", NOTE_POS, "disc", DISC_POS)


if __name__ == "__main__":
    main()
