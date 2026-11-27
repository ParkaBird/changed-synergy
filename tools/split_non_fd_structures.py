"""Split Farmer's Delight and non-FD structure templates and build a clean superflat edit map.

Splits/updates:
  - lush_cabin.nbt (purges orphaned farmersdelight palette entries while keeping lush_cabin_fd.nbt)
  - burrow_fd.nbt + burrow.nbt (non-FD)
  - point_prometheus_fd.nbt + point_prometheus.nbt (non-FD)

Places the three non-FD templates side by side in a clean superflat save with no hints or signs.
"""
from __future__ import annotations

import gzip
import hashlib
import io
import math
import shutil
import struct
import time
import zlib
from collections import defaultdict
from copy import deepcopy
from io import BytesIO
from pathlib import Path

import nbtlib as nbt
from nbtlib import Byte, Compound, Int, List, Long, LongArray, Short, String

ROOT = Path(__file__).resolve().parents[1]
STRUCT_DIR = ROOT / "src/main/resources/data/changed_synergy/structures"

TEMPLATE_SAVE = Path(r"D:\Program Files\.minecraft\versions\测试专用\saves\新的世界")
OUT_SAVE = Path(r"D:\Program Files\.minecraft\versions\测试专用\saves\Synergy-非农夫乐事结构编辑-20261125")

DATA_VERSION = 3465
FLAT_LAYERS = {
    -64: "minecraft:bedrock",
    -63: "minecraft:dirt",
    -62: "minecraft:dirt",
    -61: "minecraft:grass_block",
}


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
        out["Properties"] = Compound({k: String(str(v)) for k, v in entry["Properties"].items()})
    return out


def make_state(name: str, props: dict[str, str] | None = None) -> Compound:
    out = Compound({"Name": String(name)})
    if props:
        out["Properties"] = Compound({k: String(v) for k, v in props.items()})
    return out


def smoker_nbt() -> Compound:
    return Compound(
        {
            "id": String("minecraft:smoker"),
            "BurnTime": Short(0),
            "CookTime": Short(0),
            "CookTimeTotal": Short(0),
            "Items": List[Compound]([]),
            "RecipesUsed": Compound({}),
        }
    )


def barrel_nbt(loot_table: str | None = None) -> Compound:
    tag = Compound({"id": String("minecraft:barrel")})
    if loot_table:
        tag["LootTable"] = String(loot_table)
    else:
        tag["Items"] = List[Compound]([])
    return tag


def compact_palette(tpl: nbt.File) -> None:
    """Drop unreferenced palette entries and remap block state indices."""
    used = []
    remap: dict[int, int] = {}
    old_pal = tpl["palette"]
    for b in tpl["blocks"]:
        idx = int(b["state"])
        if idx not in remap:
            remap[idx] = len(used)
            used.append(clone_state(old_pal[idx]))
        b["state"] = Int(remap[idx])
    tpl["palette"] = List[Compound](used)


def transform_fd_template(src_path: Path, dst_path: Path, mode: str) -> None:
    tpl = nbt.load(src_path)
    palette = [clone_state(p) for p in tpl["palette"]]
    state_idx_by_key = {state_key(p): i for i, p in enumerate(palette)}

    def ensure_state(name: str, props: dict[str, str] | None = None) -> int:
        st = make_state(name, props)
        k = state_key(st)
        if k not in state_idx_by_key:
            state_idx_by_key[k] = len(palette)
            palette.append(st)
        return state_idx_by_key[k]

    for b in tpl["blocks"]:
        old = palette[int(b["state"])]
        name = str(old["Name"])
        props = {k: str(v) for k, v in old.get("Properties", {}).items()}
        x, y, z = map(int, b["pos"])

        if not name.startswith("farmersdelight:"):
            continue

        if name == "farmersdelight:stove":
            b["state"] = Int(ensure_state("minecraft:smoker", {"facing": props.get("facing", "north"), "lit": props.get("lit", "true")}))
            b["nbt"] = smoker_nbt()
        elif name == "farmersdelight:skillet":
            b["state"] = Int(ensure_state("minecraft:heavy_weighted_pressure_plate", {"powered": "false"}))
            b.pop("nbt", None)
        elif name == "farmersdelight:cutting_board":
            b["state"] = Int(ensure_state("minecraft:spruce_pressure_plate", {"powered": "false"}))
            b.pop("nbt", None)
        elif name == "farmersdelight:cooking_pot":
            b["state"] = Int(ensure_state("minecraft:cauldron"))
            b.pop("nbt", None)
        elif name in ("farmersdelight:spruce_cabinet", "farmersdelight:oak_cabinet"):
            loot = str(b["nbt"]["LootTable"]) if "nbt" in b and "LootTable" in b["nbt"] else None
            b["state"] = Int(ensure_state("minecraft:barrel", {"facing": props.get("facing", "north"), "open": "false"}))
            b["nbt"] = barrel_nbt(loot)
        elif name in ("farmersdelight:canvas_rug", "farmersdelight:full_tatami_mat"):
            b["state"] = Int(ensure_state("minecraft:white_carpet"))
            b.pop("nbt", None)
        elif name == "farmersdelight:stuffed_pumpkin_block":
            b["state"] = Int(ensure_state("changed:canned_peaches", {"facing": props.get("facing", "south"), "waterlogged": "false"}))
            b.pop("nbt", None)
        elif name == "farmersdelight:roast_chicken_block":
            if mode == "point_prometheus" and (x, y, z) == (13, 9, 38):
                b["state"] = Int(ensure_state("minecraft:cake", {"bites": "0"}))
            else:
                b["state"] = Int(ensure_state("changed:canned_peaches", {"facing": props.get("facing", "south"), "waterlogged": "false"}))
            b.pop("nbt", None)
        elif name == "farmersdelight:shepherds_pie_block":
            b["state"] = Int(ensure_state("minecraft:cake", {"bites": "0"}))
            b.pop("nbt", None)
        elif name == "farmersdelight:gleaming_salad_block":
            b["state"] = Int(ensure_state("changed:canned_soup", {"cans": "4", "waterlogged": "false"}))
            b.pop("nbt", None)
        elif name in (
            "farmersdelight:cabbage_crate",
            "farmersdelight:tomato_crate",
            "farmersdelight:potato_crate",
            "farmersdelight:carrot_crate",
        ):
            b["state"] = Int(ensure_state("minecraft:barrel", {"facing": "north", "open": "false"}))
            b["nbt"] = barrel_nbt()
        elif name in ("farmersdelight:onion_crate", "farmersdelight:beetroot_crate"):
            b["state"] = Int(ensure_state("changed:cardboard_box_small", {"rotation": "0" if x == 18 else "8", "waterlogged": "false"}))
            b.pop("nbt", None)
        elif name == "farmersdelight:wild_potatoes":
            choices = ("minecraft:fern", "minecraft:grass", "minecraft:oxeye_daisy")
            b["state"] = Int(ensure_state(choices[(x + z) % 3]))
            b.pop("nbt", None)
        else:
            raise ValueError(f"Unhandled FD block {name} in {src_path}")

    tpl["palette"] = List[Compound](palette)
    compact_palette(tpl)

    # Assert completely FD-free
    for p in tpl["palette"]:
        assert "farmersdelight" not in str(p["Name"]), f"FD block left in {dst_path}: {p}"
    for b in tpl["blocks"]:
        if "nbt" in b:
            assert "farmersdelight" not in str(b["nbt"]), f"FD BE left in {dst_path}: {b}"
    for e in tpl.get("entities", []):
        assert "farmersdelight" not in str(e), f"FD entity left in {dst_path}: {e}"

    buf = io.BytesIO()
    tpl.write(buf)
    dst_path.write_bytes(gzip.compress(buf.getvalue(), mtime=0))


def clean_lush_cabin() -> None:
    path = STRUCT_DIR / "lush_cabin.nbt"
    tpl = nbt.load(path)
    compact_palette(tpl)
    for p in tpl["palette"]:
        assert "farmersdelight" not in str(p["Name"]), f"FD palette left in lush_cabin.nbt: {p}"
    buf = io.BytesIO()
    tpl.write(buf)
    path.write_bytes(gzip.compress(buf.getvalue(), mtime=0))


def pack_indices(indices: list[int], palette_size: int) -> list[int] | None:
    if palette_size <= 1:
        return None
    bits = max(4, math.ceil(math.log2(palette_size)))
    values_per_long = 64 // bits
    out = []
    for start in range(0, len(indices), values_per_long):
        chunk = indices[start : start + values_per_long]
        value = 0
        for i, idx in enumerate(chunk):
            value |= (idx & ((1 << bits) - 1)) << (bits * i)
        if value >= 2**63:
            value -= 2**64
        out.append(value)
    return out


class SuperflatWorldBuilder:
    def __init__(self) -> None:
        self.blocks: dict[tuple[int, int, int], Compound] = {}
        self.block_entities: dict[tuple[int, int, int], Compound] = {}
        self.entities: dict[tuple[int, int], list[Compound]] = defaultdict(list)

    def set_state(self, x: int, y: int, z: int, state: Compound, entity_nbt=None) -> None:
        self.blocks[(x, y, z)] = clone_state(state)
        if entity_nbt is not None:
            tag = deepcopy(entity_nbt)
            tag["x"] = Int(x)
            tag["y"] = Int(y)
            tag["z"] = Int(z)
            self.block_entities[(x, y, z)] = tag
        elif (x, y, z) in self.block_entities:
            del self.block_entities[(x, y, z)]

    def fill_flat(self, min_x: int, max_x: int, min_z: int, max_z: int) -> None:
        for x in range(min_x, max_x + 1):
            for z in range(min_z, max_z + 1):
                for y, name in FLAT_LAYERS.items():
                    props = {"snowy": "false"} if name == "minecraft:grass_block" else None
                    self.set_state(x, y, z, make_state(name, props))

    def paste_structure(self, path: Path, origin: tuple[int, int, int]) -> list[int]:
        tpl = nbt.load(path)
        ox, oy, oz = origin
        palette = [clone_state(e) for e in tpl["palette"]]
        for b in tpl["blocks"]:
            x, y, z = map(int, b["pos"])
            self.set_state(ox + x, oy + y, oz + z, palette[int(b["state"])], b.get("nbt"))

        for ent in tpl.get("entities", []):
            tag = deepcopy(ent["nbt"])
            eid = str(tag.get("id", ""))
            # Keep paintings, item frames, armor stands, boats — omit living AI mobs in build review save.
            if eid.startswith(("changed:", "changed_synergy:")) and "armor_stand" not in eid:
                continue
            ex, ey, ez = (float(v) for v in ent["pos"])
            wx, wy, wz = ox + ex, oy + ey, oz + ez
            tag["Pos"] = List[nbt.Double]([nbt.Double(wx), nbt.Double(wy), nbt.Double(wz)])
            if "TileX" in tag:
                tag["TileX"] = Int(int(tag["TileX"]) + ox)
                tag["TileY"] = Int(int(tag["TileY"]) + oy)
                tag["TileZ"] = Int(int(tag["TileZ"]) + oz)
            tag.pop("UUID", None)
            cx = int(math.floor(wx)) >> 4
            cz = int(math.floor(wz)) >> 4
            self.entities[(cx, cz)].append(tag)
        return list(map(int, tpl["size"]))

    def write_regions(self, save_dir: Path) -> int:
        chunks = {(x >> 4, z >> 4) for (x, _y, z) in self.blocks}
        by_region: dict[tuple[int, int], list[tuple[int, int]]] = defaultdict(list)
        for cx, cz in chunks:
            by_region[(cx >> 5, cz >> 5)].append((cx, cz))

        by_chunk_sections: dict[tuple[int, int, int], dict[tuple[int, int, int], Compound]] = defaultdict(dict)
        for (x, y, z), st in self.blocks.items():
            by_chunk_sections[(x >> 4, y >> 4, z >> 4)][(x & 15, y & 15, z & 15)] = st

        by_chunk_bes: dict[tuple[int, int], list[Compound]] = defaultdict(list)
        for (x, _y, z), be in self.block_entities.items():
            by_chunk_bes[(x >> 4, z >> 4)].append(be)

        region_dir = save_dir / "region"
        region_dir.mkdir(parents=True, exist_ok=True)
        for (rx, rz), ccoords in by_region.items():
            payloads = {}
            for cx, cz in ccoords:
                sections = []
                for sy in range(-4, 20):
                    local = by_chunk_sections.get((cx, sy, cz), {})
                    pal_idx: dict[str, int] = {}
                    pal: list[Compound] = []

                    def ensure(state: Compound) -> int:
                        k = state_key(state)
                        if k not in pal_idx:
                            pal_idx[k] = len(pal)
                            pal.append(clone_state(state))
                        return pal_idx[k]

                    air = make_state("minecraft:air")
                    ensure(air)
                    indices = []
                    for i in range(4096):
                        lx = i & 15
                        lz = (i >> 4) & 15
                        ly = (i >> 8) & 15
                        indices.append(ensure(local.get((lx, ly, lz), air)))
                    packed = pack_indices(indices, len(pal))
                    block_states = Compound({"palette": List[Compound](pal)})
                    if packed is not None:
                        block_states["data"] = LongArray([Long(v) for v in packed])
                    sections.append(
                        Compound(
                            {
                                "Y": Byte(sy if sy < 128 else sy - 256),
                                "block_states": block_states,
                                "biomes": Compound({"palette": List[String]([String("minecraft:plains")])}),
                            }
                        )
                    )
                chunk_tag = Compound(
                    {
                        "DataVersion": Int(DATA_VERSION),
                        "xPos": Int(cx),
                        "zPos": Int(cz),
                        "yPos": Int(-4),
                        "Status": String("full"),
                        "LastUpdate": Long(0),
                        "sections": List[Compound](sections),
                        "block_entities": List[Compound](by_chunk_bes.get((cx, cz), [])),
                        "isLightOn": Byte(1),
                        "InhabitedTime": Long(0),
                        "blending_data": Compound({"min_section": Int(-4), "max_section": Int(20)}),
                    }
                )
                raw = BytesIO()
                nbt.File(chunk_tag).write(raw)
                compressed = zlib.compress(raw.getvalue())
                payload = struct.pack(">I", len(compressed) + 1) + bytes([2]) + compressed
                pad = (4096 - (len(payload) % 4096)) % 4096
                payloads[(cx & 31, cz & 31)] = payload + b"\x00" * pad
            write_mca(region_dir / f"r.{rx}.{rz}.mca", payloads)

        ent_dir = save_dir / "entities"
        ent_dir.mkdir(parents=True, exist_ok=True)
        ent_by_region: dict[tuple[int, int], list[tuple[int, int]]] = defaultdict(list)
        for cx, cz in self.entities:
            ent_by_region[(cx >> 5, cz >> 5)].append((cx, cz))
        for (rx, rz), ccoords in ent_by_region.items():
            payloads = {}
            for cx, cz in ccoords:
                ec = Compound(
                    {
                        "DataVersion": Int(DATA_VERSION),
                        "Position": nbt.IntArray([Int(cx), Int(cz)]),
                        "Entities": List[Compound](self.entities[(cx, cz)]),
                    }
                )
                raw = BytesIO()
                nbt.File(ec).write(raw)
                compressed = zlib.compress(raw.getvalue())
                payload = struct.pack(">I", len(compressed) + 1) + bytes([2]) + compressed
                pad = (4096 - (len(payload) % 4096)) % 4096
                payloads[(cx & 31, cz & 31)] = payload + b"\x00" * pad
            write_mca(ent_dir / f"r.{rx}.{rz}.mca", payloads)
        return len(chunks)


def write_mca(path: Path, payloads: dict[tuple[int, int], bytes]) -> None:
    locations = bytearray(4096)
    timestamps = bytearray(4096)
    next_sector = 2
    now = int(time.time())
    body = bytearray()
    for (lx, lz), payload in sorted(payloads.items()):
        sectors = len(payload) // 4096
        idx = 4 * (lx + lz * 32)
        locations[idx : idx + 3] = struct.pack(">I", next_sector)[1:]
        locations[idx + 3] = sectors
        timestamps[idx : idx + 4] = struct.pack(">I", now)
        while len(body) // 4096 < next_sector - 2:
            body.extend(b"\x00" * 4096)
        body.extend(payload)
        next_sector += sectors
    with open(path, "wb") as f:
        f.write(locations)
        f.write(timestamps)
        f.write(body)


def write_level_dat(dest: Path) -> None:
    src = nbt.File.load(TEMPLATE_SAVE / "level.dat", gzipped=True)
    data = src["Data"]
    data["LevelName"] = String(OUT_SAVE.name)
    data["DataVersion"] = Int(DATA_VERSION)
    data.pop("Player", None)
    data["SpawnX"] = Int(16)
    data["SpawnY"] = Int(-50)
    data["SpawnZ"] = Int(16)
    data["GameType"] = Int(1)
    data["Difficulty"] = Byte(0)
    data["DifficultyLocked"] = Byte(0)
    data["raining"] = Byte(0)
    data["thundering"] = Byte(0)

    flat = Compound(
        {
            "type": String("minecraft:flat"),
            "settings": Compound(
                {
                    "biome": String("minecraft:plains"),
                    "lakes": Byte(0),
                    "features": Byte(0),
                    "layers": List[Compound](
                        [
                            Compound({"block": String("minecraft:bedrock"), "height": Int(1)}),
                            Compound({"block": String("minecraft:dirt"), "height": Int(2)}),
                            Compound({"block": String("minecraft:grass_block"), "height": Int(1)}),
                        ]
                    ),
                    "structures": Compound({"structures": Compound({})}),
                }
            ),
        }
    )
    dims = data["WorldGenSettings"]["dimensions"]
    for key in list(dims.keys()):
        if str(key) == "minecraft:overworld":
            dims[key] = Compound({"type": String("minecraft:overworld"), "generator": flat})
    data["WorldGenSettings"]["dimensions"] = dims
    data["Time"] = Long(0)
    data["DayTime"] = Long(6000)
    data["LastPlayed"] = Long(int(time.time() * 1000))
    if "GameRules" in data:
        rules = data["GameRules"]
        rules["doDaylightCycle"] = String("false")
        rules["doWeatherCycle"] = String("false")
        rules["doMobSpawning"] = String("false")
        rules["doFireTick"] = String("false")
        rules["mobGriefing"] = String("false")
    dest.parent.mkdir(parents=True, exist_ok=True)
    nbt.File(src).save(dest, gzipped=True)


def main() -> None:
    # 1. Backup FD originals as *_fd.nbt when not already split
    burrow_src = STRUCT_DIR / "burrow.nbt"
    burrow_fd = STRUCT_DIR / "burrow_fd.nbt"
    if not burrow_fd.exists():
        shutil.copy2(burrow_src, burrow_fd)

    prom_src = STRUCT_DIR / "point_prometheus.nbt"
    prom_fd = STRUCT_DIR / "point_prometheus_fd.nbt"
    if not prom_fd.exists():
        shutil.copy2(prom_src, prom_fd)

    # 2. Clean lush_cabin.nbt palette + produce FD-free burrow.nbt and point_prometheus.nbt
    clean_lush_cabin()
    transform_fd_template(burrow_fd, burrow_src, "burrow")
    transform_fd_template(prom_fd, prom_src, "point_prometheus")

    for name in ("lush_cabin", "lush_cabin_fd", "burrow", "burrow_fd", "point_prometheus", "point_prometheus_fd"):
        p = STRUCT_DIR / f"{name}.nbt"
        t = nbt.load(p)
        fd_cnt = sum(1 for b in t["blocks"] if "farmersdelight" in str(t["palette"][int(b["state"])]["Name"]))
        fd_pal = sum(1 for entry in t["palette"] if "farmersdelight" in str(entry["Name"]))
        print(f"{name}.nbt: size={list(map(int, t['size']))}, fd_blocks={fd_cnt}, fd_palette={fd_pal}, sha={hashlib.sha256(p.read_bytes()).hexdigest()[:12]}")

    # 3. Build superflat review map with the 3 non-FD structures (no hints/signs added)
    if OUT_SAVE.exists():
        shutil.rmtree(OUT_SAVE)
    OUT_SAVE.mkdir(parents=True, exist_ok=True)
    for sub in ("region", "entities", "data", "playerdata", "advancements", "stats", "poi", "datapacks", "serverconfig"):
        (OUT_SAVE / sub).mkdir(exist_ok=True)
    write_level_dat(OUT_SAVE / "level.dat")

    world = SuperflatWorldBuilder()
    world.fill_flat(-16, 160, -16, 160)
    placements = [
        ("lush_cabin.nbt", (0, -60, 0)),
        ("point_prometheus.nbt", (64, -60, 0)),
        ("burrow.nbt", (0, -60, 80)),
    ]
    for fname, origin in placements:
        sz = world.paste_structure(STRUCT_DIR / fname, origin)
        print(f"Placed {fname} at origin={origin}, size={sz}")
    chunks = world.write_regions(OUT_SAVE)
    print(f"Created superflat save at {OUT_SAVE} (chunks={chunks})")


if __name__ == "__main__":
    main()
