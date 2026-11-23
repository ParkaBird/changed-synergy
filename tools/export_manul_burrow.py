"""Export Manul burrow from the review save and trim bottom empty air layers."""
from __future__ import annotations

import gzip
import hashlib
import json
import math
import struct
import zlib
from collections import Counter
from io import BytesIO
from pathlib import Path

import nbtlib as nbt
from nbtlib import Byte, Compound, Double, Float, Int, List, Long, LongArray, Short, String

ROOT = Path(__file__).resolve().parents[1]
SAVE = Path(r"D:\Program Files\.minecraft\versions\测试专用\saves\Synergy-兔狲洞穴审查-20261116")
REGION = SAVE / "region"
ENTITIES_REGION = SAVE / "entities"
LEGACY_NBT = Path(r"D:\Downloads\manul.nbt")
# Aligned via redstone_block fingerprint against Downloads/manul.nbt
ORIGIN = (0, 42, 0)
SIZE = (56, 35, 55)
OUT_NBT = ROOT / "src/main/resources/data/changed_synergy/structures/burrow.nbt"
OUT_MANIFEST = ROOT / "tools/manul_burrow_manifest.json"

# Decorative + authored livestock/fish from the review save (and missing paintings from manul.nbt).
KEEP_ENTITIES = {
    "minecraft:painting",
    "minecraft:item_frame",
    "minecraft:glow_item_frame",
    "minecraft:armor_stand",
    "changed:wall_sign",
    "minecraft:cow",
    "minecraft:sheep",
    "minecraft:chicken",
    "minecraft:pig",
    "minecraft:rabbit",
    "minecraft:cod",
    "minecraft:salmon",
    "minecraft:tropical_fish",
    "minecraft:pufferfish",
}
SKIP_ENTITIES = {"minecraft:item", "minecraft:experience_orb", "minecraft:player"}


def _read_mca_chunk(folder: Path, cx: int, cz: int):
    path = folder / f"r.{cx >> 5}.{cz >> 5}.mca"
    if not path.exists():
        return None
    with open(path, "rb") as f:
        locations = f.read(4096)
        idx = 4 * ((cx & 31) + (cz & 31) * 32)
        sector = (locations[idx] << 16) | (locations[idx + 1] << 8) | locations[idx + 2]
        if sector == 0:
            return None
        f.seek(sector * 4096)
        length = struct.unpack(">I", f.read(4))[0]
        comp = f.read(1)[0]
        raw = f.read(length - 1)
    if comp == 2:
        raw = zlib.decompress(raw)
    elif comp == 1:
        raw = gzip.decompress(raw)
    return nbt.File.parse(BytesIO(raw))


def load_chunk(cx: int, cz: int):
    return _read_mca_chunk(REGION, cx, cz)


def load_entity_chunk(cx: int, cz: int):
    return _read_mca_chunk(ENTITIES_REGION, cx, cz)


def decode_indices(data_longs, palette_size, volume=4096):
    if palette_size <= 1:
        return [0] * volume
    bits = max(4, math.ceil(math.log2(palette_size)))
    values_per_long = 64 // bits
    mask = (1 << bits) - 1
    out = []
    for long in data_longs:
        v = int(long) & ((1 << 64) - 1)
        for i in range(values_per_long):
            if len(out) >= volume:
                break
            out.append((v >> (bits * i)) & mask)
        if len(out) >= volume:
            break
    while len(out) < volume:
        out.append(0)
    return out[:volume]


def state_key(entry: Compound) -> str:
    name = str(entry["Name"])
    props = entry.get("Properties")
    if not props:
        return name
    items = ",".join(f"{k}={props[k]}" for k in sorted(props.keys()))
    return f"{name}[{items}]"


def clone_state(entry: Compound) -> Compound:
    out = Compound({"Name": String(str(entry["Name"]))})
    if "Properties" in entry:
        props = Compound()
        for k, v in entry["Properties"].items():
            props[k] = String(str(v))
        out["Properties"] = props
    return out


def export_volume(ox: int, oy: int, oz: int, sx: int, sy: int, sz: int):
    palette_index: dict[str, int] = {}
    palette: list[Compound] = []
    blocks: list[Compound] = []
    block_entities: list[Compound] = []
    entities: list[Compound] = []

    def ensure_state(entry: Compound) -> int:
        key = state_key(entry)
        if key not in palette_index:
            palette_index[key] = len(palette)
            palette.append(clone_state(entry))
        return palette_index[key]

    # Ensure air exists even if somehow absent.
    air_state = Compound({"Name": String("minecraft:air")})
    ensure_state(air_state)

    block_at: dict[tuple[int, int, int], Compound] = {}
    for cx in range(ox >> 4, ((ox + sx - 1) >> 4) + 1):
        for cz in range(oz >> 4, ((oz + sz - 1) >> 4) + 1):
            chunk = load_chunk(cx, cz)
            if chunk is None:
                continue
            for section in chunk.get("sections", []):
                bs = section.get("block_states")
                if bs is None:
                    continue
                y0 = int(section["Y"]) * 16
                if y0 + 15 < oy or y0 > oy + sy - 1:
                    continue
                pal = list(bs["palette"])
                data = bs.get("data")
                idxs = [0] * 4096 if data is None else decode_indices(list(data), len(pal))
                for i, pi in enumerate(idxs):
                    lx = i & 15
                    lz = (i >> 4) & 15
                    ly = (i >> 8) & 15
                    wx, wy, wz = cx * 16 + lx, y0 + ly, cz * 16 + lz
                    if not (ox <= wx < ox + sx and oy <= wy < oy + sy and oz <= wz < oz + sz):
                        continue
                    state = ensure_state(pal[0 if data is None else pi])
                    local = (wx - ox, wy - oy, wz - oz)
                    block_at[local] = Compound(
                        {
                            "pos": List[Int]([Int(local[0]), Int(local[1]), Int(local[2])]),
                            "state": Int(state),
                        }
                    )

            for be in chunk.get("block_entities", []):
                x, y, z = int(be["x"]), int(be["y"]), int(be["z"])
                if not (ox <= x < ox + sx and oy <= y < oy + sy and oz <= z < oz + sz):
                    continue
                local = (x - ox, y - oy, z - oz)
                nbt_tag = Compound({k: be[k] for k in be.keys() if k not in ("x", "y", "z")})
                nbt_tag["x"] = Int(local[0])
                nbt_tag["y"] = Int(local[1])
                nbt_tag["z"] = Int(local[2])
                if local in block_at:
                    block_at[local]["nbt"] = nbt_tag
                else:
                    block_entities.append(nbt_tag)

    air_i = palette_index[state_key(air_state)]
    for x in range(sx):
        for y in range(sy):
            for z in range(sz):
                if (x, y, z) not in block_at:
                    block_at[(x, y, z)] = Compound(
                        {"pos": List[Int]([Int(x), Int(y), Int(z)]), "state": Int(air_i)}
                    )
    blocks = list(block_at.values())
    entities = collect_entities(ox, oy, oz, sx, sy, sz)

    return Compound(
        {
            "size": List[Int]([Int(sx), Int(sy), Int(sz)]),
            "palette": List[Compound](palette),
            "blocks": List[Compound](blocks),
            "entities": List[Compound](entities),
            "DataVersion": Int(3465),
        }
    )


def clone_tag(value):
    """Deep-copy nbtlib values into plain Compound/List trees we can mutate safely."""
    if isinstance(value, Compound):
        return Compound({k: clone_tag(v) for k, v in value.items()})
    if isinstance(value, List):
        subtype = type(value)
        return subtype([clone_tag(v) for v in value])
    return value


def clean_entity_tag(tag: Compound, kind: str):
    for key in (
        "UUID",
        "UUIDLeast",
        "UUIDMost",
        "Attributes",
        "Brain",
        "HurtByTimestamp",
        "HurtTime",
        "DeathTime",
        "ForgeCaps",
        "CitadelData",
        "BalmData",
        "Listening",
        "AbsorptionAmount",
    ):
        tag.pop(key, None)
    tag["Motion"] = List[Double]([Double(0), Double(0), Double(0)])
    tag["FallDistance"] = Float(0)
    tag["Fire"] = Short(-1)
    tag["OnGround"] = Byte(1)
    if kind in {
        "minecraft:cow",
        "minecraft:sheep",
        "minecraft:chicken",
        "minecraft:pig",
        "minecraft:rabbit",
        "minecraft:cod",
        "minecraft:salmon",
        "minecraft:tropical_fish",
        "minecraft:pufferfish",
    }:
        tag["PersistenceRequired"] = Byte(1)
        tag["Invulnerable"] = Byte(0)
        tags = tag.get("Tags")
        if tags is None:
            tags = List[String]([])
            tag["Tags"] = tags
        if "changed_synergy:camp_livestock" not in [str(t) for t in tags] and kind in {
            "minecraft:cow",
            "minecraft:sheep",
            "minecraft:chicken",
            "minecraft:pig",
            "minecraft:rabbit",
        }:
            tags.append(String("changed_synergy:camp_livestock"))


# Painting facing 0=south,1=west,2=north,3=east; support block is opposite that direction.
PAINTING_SUPPORT = {0: (0, 0, -1), 1: (1, 0, 0), 2: (0, 0, 1), 3: (-1, 0, 0)}
# Item frame Facing 0=down,1=up,2=north,3=south,4=west,5=east; Tile is the attached block.
ITEM_FRAME_SUPPORT = {
    0: (0, 1, 0),
    1: (0, -1, 0),
    2: (0, 0, 1),
    3: (0, 0, -1),
    4: (1, 0, 0),
    5: (-1, 0, 0),
}


def entity_record(kind: str, lx: float, ly: float, lz: float, body: Compound) -> Compound:
    bx, by, bz = int(math.floor(lx)), int(math.floor(ly)), int(math.floor(lz))
    body["id"] = String(kind)
    body["Pos"] = List[Double]([Double(lx), Double(ly), Double(lz)])
    if kind == "minecraft:painting":
        facing = int(body.get("facing", 0))
        dx, dy, dz = PAINTING_SUPPORT.get(facing, (0, 0, -1))
        bx, by, bz = int(round(lx)) + dx, int(math.floor(ly)) + dy, int(round(lz)) + dz
        body["facing"] = Byte(facing)
        body["TileX"] = Int(bx)
        body["TileY"] = Int(by)
        body["TileZ"] = Int(bz)
    elif "item_frame" in kind:
        facing = int(body.get("Facing", body.get("facing", 1)))
        dx, dy, dz = ITEM_FRAME_SUPPORT.get(facing, (0, 0, 0))
        bx, by, bz = int(math.floor(lx)) + dx, int(math.floor(ly)) + dy, int(math.floor(lz)) + dz
        body["Facing"] = Byte(facing)
        body["TileX"] = Int(bx)
        body["TileY"] = Int(by)
        body["TileZ"] = Int(bz)
    clean_entity_tag(body, kind)
    return Compound(
        {
            "pos": List[Double]([Double(lx), Double(ly), Double(lz)]),
            "blockPos": List[Int]([Int(bx), Int(by), Int(bz)]),
            "nbt": body,
        }
    )


def _near_painting(existing: list[Compound], variant: str, lx: float, ly: float, lz: float) -> bool:
    for ent in existing:
        tag = ent["nbt"]
        if str(tag.get("id")) != "minecraft:painting":
            continue
        if str(tag.get("variant", "")) != variant:
            continue
        px, py, pz = map(float, ent["pos"])
        if (px - lx) ** 2 + (py - ly) ** 2 + (pz - lz) ** 2 <= 6.25:
            return True
    return False


def collect_entities(ox: int, oy: int, oz: int, sx: int, sy: int, sz: int) -> list[Compound]:
    """Read 1.17+ entity region files; union missing authored paintings from manul.nbt."""
    found: list[Compound] = []

    for cx in range(ox >> 4, ((ox + sx - 1) >> 4) + 1):
        for cz in range(oz >> 4, ((oz + sz - 1) >> 4) + 1):
            chunk = load_entity_chunk(cx, cz)
            if chunk is None:
                continue
            for ent in chunk.get("Entities", []):
                pos = ent.get("Pos")
                if pos is None:
                    continue
                ex, ey, ez = float(pos[0]), float(pos[1]), float(pos[2])
                if not (ox <= ex < ox + sx and oy <= ey < oy + sy and oz <= ez < oz + sz):
                    continue
                kind = str(ent.get("id", ""))
                if kind in SKIP_ENTITIES or kind not in KEEP_ENTITIES:
                    continue
                body = clone_tag(ent)
                found.append(entity_record(kind, ex - ox, ey - oy, ez - oz, body))

    if LEGACY_NBT.exists():
        legacy = nbt.File.load(LEGACY_NBT, gzipped=True)
        # manul.nbt predates the one-layer bottom strip applied to the live export.
        for ent in legacy.get("entities", []):
            tag = ent.get("nbt", ent)
            kind = str(tag.get("id", ""))
            if kind != "minecraft:painting":
                continue
            pos = ent.get("pos", tag.get("Pos"))
            lx, ly, lz = float(pos[0]), float(pos[1]) - 1.0, float(pos[2])
            if not (0 <= lx < sx and 0 <= ly < sy and 0 <= lz < sz):
                continue
            variant = str(tag.get("variant", ""))
            if _near_painting(found, variant, lx, ly, lz):
                continue
            body = clone_tag(tag)
            found.append(entity_record(kind, lx, ly, lz, body))

    return found


def _is_support(name: str) -> bool:
    if name in AIR_NAMES or name in {
        "minecraft:water",
        "minecraft:bubble_column",
        "minecraft:lava",
        "minecraft:snow",
        "minecraft:grass",
        "minecraft:tall_grass",
        "minecraft:fern",
        "minecraft:large_fern",
        "minecraft:dead_bush",
        "minecraft:torch",
        "minecraft:wall_torch",
        "minecraft:lantern",
        "minecraft:soul_lantern",
    }:
        return False
    if (
        "carpet" in name
        or name.endswith("_button")
        or name.endswith("_pressure_plate")
        or name.endswith("_slab")
    ):
        return False
    if "rail" in name or "sign" in name:
        return False
    return True


def _find_hang_support(
    cells: dict[tuple[int, int, int], str],
    lx: float,
    ly: float,
    lz: float,
    facing: int,
    painting: bool,
) -> tuple[int, int, int] | None:
    if painting:
        candidates = [PAINTING_SUPPORT.get(facing, (0, 0, -1))]
        # Also try the facing direction itself in case Tile was stored as the painting cell.
        inv = {0: (0, 0, 1), 1: (-1, 0, 0), 2: (0, 0, -1), 3: (1, 0, 0)}
        candidates.append(inv.get(facing, (0, 0, 1)))
    else:
        candidates = [ITEM_FRAME_SUPPORT.get(facing, (0, 0, 0))]
    base = (int(round(lx)), int(math.floor(ly)), int(round(lz)))
    for dx, dy, dz in candidates:
        pos = (base[0] + dx, base[1] + dy, base[2] + dz)
        if _is_support(cells.get(pos, "minecraft:air")):
            return pos
    # Fallback: nearest solid within 1 block of the entity.
    for dy in (0, 1, -1):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if dx == dy == dz == 0:
                    continue
                pos = (base[0] + dx, base[1] + dy, base[2] + dz)
                if _is_support(cells.get(pos, "minecraft:air")):
                    return pos
    return None


def stabilize_entities(template: Compound) -> None:
    """Keep livestock on floors and fish in water; drop unsupported hangings."""
    palette = list(template["palette"])
    names = [str(e["Name"]) for e in palette]
    cells = {tuple(map(int, b["pos"])): names[int(b["state"])] for b in template["blocks"]}
    water = {
        n
        for n in names
        if n in ("minecraft:water", "minecraft:bubble_column") or n.endswith("_water")
    }
    sy = int(template["size"][1])
    kept = []
    for ent in template.get("entities", []):
        tag = ent["nbt"]
        kind = str(tag.get("id", ""))
        lx, ly, lz = map(float, ent["pos"])
        if kind in {"minecraft:cow", "minecraft:sheep", "minecraft:chicken", "minecraft:pig", "minecraft:rabbit"}:
            fx, fz = int(math.floor(lx)), int(math.floor(lz))
            floor_y = None
            for y in range(min(int(math.floor(ly)), sy - 1), -1, -1):
                name = cells.get((fx, y, fz), "minecraft:air")
                if _is_support(name) and name not in water:
                    floor_y = y
                    break
            if floor_y is None:
                continue
            ly = float(floor_y + 1)
            ent["pos"] = List[Double]([Double(lx), Double(ly), Double(lz)])
            ent["blockPos"] = List[Int]([Int(fx), Int(floor_y + 1), Int(fz)])
            tag["Pos"] = List[Double]([Double(lx), Double(ly), Double(lz)])
            tag["OnGround"] = Byte(1)
            tag["Motion"] = List[Double]([Double(0), Double(0), Double(0)])
            tag["FallDistance"] = Float(0)
        elif kind in {"minecraft:cod", "minecraft:salmon", "minecraft:tropical_fish", "minecraft:pufferfish"}:
            fx, fy, fz = int(math.floor(lx)), int(math.floor(ly)), int(math.floor(lz))
            if cells.get((fx, fy, fz), "minecraft:air") not in water:
                found = None
                for dy in range(0, 4):
                    for dx in range(-2, 3):
                        for dz in range(-2, 3):
                            p = (fx + dx, fy - dy, fz + dz)
                            if cells.get(p) in water:
                                found = p
                                break
                        if found:
                            break
                    if found:
                        break
                if not found:
                    continue
                lx, ly, lz = found[0] + 0.5, found[1] + 0.4, found[2] + 0.5
                ent["pos"] = List[Double]([Double(lx), Double(ly), Double(lz)])
                ent["blockPos"] = List[Int]([Int(found[0]), Int(found[1]), Int(found[2])])
                tag["Pos"] = List[Double]([Double(lx), Double(ly), Double(lz)])
                tag["Motion"] = List[Double]([Double(0), Double(0), Double(0)])
        elif kind == "minecraft:painting" or "item_frame" in kind:
            if kind == "minecraft:painting":
                facing = int(tag.get("facing", 0))
                support = _find_hang_support(cells, lx, ly, lz, facing, True)
            else:
                facing = int(tag.get("Facing", 1))
                support = _find_hang_support(cells, lx, ly, lz, facing, False)
            if support is None:
                continue
            tag["TileX"] = Int(support[0])
            tag["TileY"] = Int(support[1])
            tag["TileZ"] = Int(support[2])
            ent["blockPos"] = List[Int]([Int(support[0]), Int(support[1]), Int(support[2])])
        kept.append(ent)
    template["entities"] = List[Compound](kept)


AIR_NAMES = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:structure_void"}


def strip_bottom_air(template: Compound) -> tuple[Compound, int]:
    """Remove leading all-air layers (structure empty padding), like desert_refuge tightness."""
    sx, sy, sz = map(int, template["size"])
    palette = list(template["palette"])
    air_states = {i for i, s in enumerate(palette) if str(s["Name"]) in AIR_NAMES}
    solid_y = Counter()
    for b in template["blocks"]:
        if int(b["state"]) not in air_states:
            solid_y[int(b["pos"][1])] += 1
    cut = 0
    while cut < sy and solid_y.get(cut, 0) == 0:
        cut += 1
    if cut == 0:
        return template, 0

    new_blocks = []
    for b in template["blocks"]:
        x, y, z = map(int, b["pos"])
        if y < cut:
            continue
        entry = Compound({"pos": List[Int]([Int(x), Int(y - cut), Int(z)]), "state": b["state"]})
        if "nbt" in b:
            nbt_tag = Compound({k: b["nbt"][k] for k in b["nbt"].keys()})
            nbt_tag["y"] = Int(y - cut)
            entry["nbt"] = nbt_tag
        new_blocks.append(entry)

    new_entities = []
    for e in template.get("entities", []):
        pos = list(e["pos"])
        if float(pos[1]) < cut:
            continue
        body = Compound({k: e["nbt"][k] for k in e["nbt"].keys()})
        lx = float(pos[0])
        ly = float(pos[1]) - cut
        lz = float(pos[2])
        body["Pos"] = List[Double]([Double(lx), Double(ly), Double(lz)])
        bp = list(e.get("blockPos", [int(math.floor(lx)), int(math.floor(float(pos[1]))), int(math.floor(lz))]))
        bx, by, bz = int(bp[0]), int(bp[1]) - cut, int(bp[2])
        kind = str(body.get("id", ""))
        if kind == "minecraft:painting" or "item_frame" in kind:
            body["TileX"] = Int(bx)
            body["TileY"] = Int(by)
            body["TileZ"] = Int(bz)
        new_entities.append(
            Compound(
                {
                    "pos": List[Double]([Double(lx), Double(ly), Double(lz)]),
                    "blockPos": List[Int]([Int(bx), Int(by), Int(bz)]),
                    "nbt": body,
                }
            )
        )

    return (
        Compound(
            {
                "size": List[Int]([Int(sx), Int(sy - cut), Int(sz)]),
                "palette": template["palette"],
                "blocks": List[Compound](new_blocks),
                "entities": List[Compound](new_entities),
                "DataVersion": template.get("DataVersion", Int(3465)),
            }
        ),
        cut,
    )


def omit_exterior_padding_air(template: Compound, ground_level: int) -> tuple[Compound, int, int]:
    """Drop padding air so placement leaves natural terrain (desert_refuge-style sparse shell).

    1) Underground: exterior-connected air below ground_level is omitted.
    2) Surface: air in columns with no near-surface solid is omitted, and air above each
       column's highest solid is omitted so the authored mound meets the world rim cleanly.
    Interior room air stays.
    """
    from collections import deque

    sx, sy, sz = map(int, template["size"])
    palette = list(template["palette"])
    air_states = {i for i, s in enumerate(palette) if str(s["Name"]) in AIR_NAMES}
    by_pos = {tuple(map(int, b["pos"])): b for b in template["blocks"]}

    omit: set[tuple[int, int, int]] = set()
    queue: deque[tuple[int, int, int]] = deque()
    for y in range(min(ground_level, sy)):
        for x in range(sx):
            for z in range(sz):
                if not (x in (0, sx - 1) or z in (0, sz - 1) or y == 0):
                    continue
                pos = (x, y, z)
                block = by_pos.get(pos)
                if block is not None and int(block["state"]) in air_states:
                    omit.add(pos)
                    queue.append(pos)
    while queue:
        x, y, z = queue.popleft()
        for nx, ny, nz in (
            (x - 1, y, z),
            (x + 1, y, z),
            (x, y - 1, z),
            (x, y + 1, z),
            (x, y, z - 1),
            (x, y, z + 1),
        ):
            if not (0 <= nx < sx and 0 <= ny < ground_level and 0 <= nz < sz):
                continue
            pos = (nx, ny, nz)
            if pos in omit:
                continue
            block = by_pos.get(pos)
            if block is not None and int(block["state"]) in air_states:
                omit.add(pos)
                queue.append(pos)
    underground_omitted = len(omit)

    tops: dict[tuple[int, int], int] = {}
    surface_cols: set[tuple[int, int]] = set()
    for (x, y, z), block in by_pos.items():
        if int(block["state"]) in air_states:
            continue
        tops[x, z] = max(tops.get((x, z), -1), y)
        if ground_level - 1 <= y <= ground_level + 2:
            surface_cols.add((x, z))

    for (x, y, z), block in by_pos.items():
        if int(block["state"]) not in air_states or (x, y, z) in omit:
            continue
        if y >= ground_level and (x, z) not in surface_cols:
            omit.add((x, y, z))
            continue
        if y > tops.get((x, z), -1):
            omit.add((x, y, z))

    kept = [b for pos, b in by_pos.items() if pos not in omit]
    return (
        Compound(
            {
                "size": template["size"],
                "palette": template["palette"],
                "blocks": List[Compound](kept),
                "entities": template.get("entities", List[Compound]([])),
                "DataVersion": template.get("DataVersion", Int(3465)),
            }
        ),
        underground_omitted,
        len(omit) - underground_omitted,
    )


def main():
    # Surface solids sit at local y=21; ground_level 20 places that plane on the world surface
    # (one block higher than the previous surface-1 alignment).
    ground_level = 20
    print(f"exporting {SIZE} from world origin {ORIGIN}")
    template = export_volume(*ORIGIN, *SIZE)
    before = list(map(int, template["size"]))
    template, cut = strip_bottom_air(template)
    template, underground_omitted, surface_omitted = omit_exterior_padding_air(
        template, ground_level
    )
    stabilize_entities(template)
    after = list(map(int, template["size"]))
    OUT_NBT.parent.mkdir(parents=True, exist_ok=True)
    nbt.File(template).save(OUT_NBT, gzipped=True)
    digest = hashlib.sha256(OUT_NBT.read_bytes()).hexdigest()
    palette = template["palette"]
    counts = Counter(str(palette[int(b["state"])]["Name"]) for b in template["blocks"])
    air_kept = sum(1 for b in template["blocks"] if str(palette[int(b["state"])]["Name"]) in AIR_NAMES)
    manifest = {
        "source_save": str(SAVE),
        "origin": list(ORIGIN),
        "exported_size": before,
        "bottom_air_layers_removed": cut,
        "underground_exterior_air_omitted": underground_omitted,
        "surface_exterior_air_omitted": surface_omitted,
        "final_size": after,
        "ground_level": ground_level,
        "entities": len(template.get("entities", [])),
        "blocks": len(template["blocks"]),
        "air_kept": air_kept,
        "sha256": digest,
        "top_blocks": counts.most_common(20),
    }
    OUT_MANIFEST.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(json.dumps(manifest, indent=2))
    print("wrote", OUT_NBT)


if __name__ == "__main__":
    main()
