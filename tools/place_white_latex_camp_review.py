"""Create a superflat review save and paste the four white-latex camp pieces."""
from __future__ import annotations

import gzip
import math
import struct
import time
import zlib
from collections import defaultdict
from io import BytesIO
from pathlib import Path

import nbtlib as nbt
from nbtlib import Byte, ByteArray, Compound, Int, List, Long, LongArray, String

SAVES = Path(r"D:\Program Files\.minecraft\versions\测试专用\saves")
WORLD_NAME = "Synergy-白胶营地审查-20261118"
OUT = SAVES / WORLD_NAME
TEMPLATE = Path(r"D:\Program Files\.minecraft\versions\测试专用\saves\新的世界")

STRUCTURES = [
    (Path(r"D:\Downloads\basicwhitelatexcamp_tent1.nbt"), (0, -61, 0)),
    (Path(r"D:\Downloads\basicwhitelatexcamp_tent2.nbt"), (24, -61, 0)),
    (Path(r"D:\Downloads\basicwhitelatexcamp_campfire.nbt"), (0, -61, 24)),
    (Path(r"D:\Downloads\basicwhitelatexcamp_well.nbt"), (24, -61, 24)),
]

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


def air_state() -> Compound:
    return Compound({"Name": String("minecraft:air")})


def named_state(name: str, props: dict | None = None) -> Compound:
    out = Compound({"Name": String(name)})
    if props:
        out["Properties"] = Compound({k: String(v) for k, v in props.items()})
    return out


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


def load_structure(path: Path):
    template = nbt.File.load(path, gzipped=True)
    size = tuple(map(int, template["size"]))
    palette = [clone_state(e) for e in template["palette"]]
    blocks = []
    for b in template["blocks"]:
        x, y, z = map(int, b["pos"])
        entry = {"pos": (x, y, z), "state": int(b["state"])}
        if "nbt" in b:
            entry["nbt"] = b["nbt"]
        blocks.append(entry)
    return size, palette, blocks


class WorldWriter:
    def __init__(self):
        # (cx,cz) -> {(lx,ly,lz local section coords keyed by absolute)} 
        self.blocks: dict[tuple[int, int, int], Compound] = {}
        self.block_entities: dict[tuple[int, int, int], Compound] = {}

    def set_state(self, x: int, y: int, z: int, state: Compound, entity_nbt=None):
        self.blocks[(x, y, z)] = clone_state(state)
        if entity_nbt is not None:
            tag = Compound({k: entity_nbt[k] for k in entity_nbt.keys()})
            tag["x"] = Int(x)
            tag["y"] = Int(y)
            tag["z"] = Int(z)
            self.block_entities[(x, y, z)] = tag
        elif (x, y, z) in self.block_entities:
            del self.block_entities[(x, y, z)]

    def fill_flat(self, min_x: int, max_x: int, min_z: int, max_z: int):
        for x in range(min_x, max_x + 1):
            for z in range(min_z, max_z + 1):
                for y, name in FLAT_LAYERS.items():
                    props = {"snowy": "false"} if name == "minecraft:grass_block" else None
                    self.set_state(x, y, z, named_state(name, props))

    def paste(self, path: Path, origin: tuple[int, int, int]):
        size, palette, blocks = load_structure(path)
        ox, oy, oz = origin
        for b in blocks:
            x, y, z = b["pos"]
            wx, wy, wz = ox + x, oy + y, oz + z
            self.set_state(wx, wy, wz, palette[b["state"]], b.get("nbt"))
        return size

    def chunk_positions(self):
        chunks = set()
        for x, y, z in self.blocks:
            chunks.add((x >> 4, z >> 4))
        return chunks

    def build_chunk(self, cx: int, cz: int) -> Compound:
        # Gather blocks in chunk
        by_section: dict[int, dict[tuple[int, int, int], Compound]] = defaultdict(dict)
        for (x, y, z), state in self.blocks.items():
            if (x >> 4) != cx or (z >> 4) != cz:
                continue
            sy = y >> 4
            by_section[sy][(x & 15, y & 15, z & 15)] = state

        sections = []
        for sy in range(-4, 20):
            local = by_section.get(sy, {})
            palette_index: dict[str, int] = {}
            palette: list[Compound] = []

            def ensure(state: Compound) -> int:
                key = state_key(state)
                if key not in palette_index:
                    palette_index[key] = len(palette)
                    palette.append(clone_state(state))
                return palette_index[key]

            ensure(air_state())
            indices = []
            for i in range(4096):
                lx = i & 15
                lz = (i >> 4) & 15
                ly = (i >> 8) & 15
                state = local.get((lx, ly, lz), air_state())
                indices.append(ensure(state))
            packed = pack_indices(indices, len(palette))
            block_states = Compound({"palette": List[Compound](palette)})
            if packed is not None:
                block_states["data"] = LongArray([Long(v) for v in packed])
            sections.append(
                Compound(
                    {
                        "Y": Byte(sy if sy < 128 else sy - 256),
                        "block_states": block_states,
                        "biomes": Compound(
                            {"palette": List[String]([String("minecraft:plains")])}
                        ),
                    }
                )
            )

        bes = []
        for (x, y, z), tag in self.block_entities.items():
            if (x >> 4) == cx and (z >> 4) == cz:
                bes.append(tag)

        return Compound(
            {
                "DataVersion": Int(DATA_VERSION),
                "xPos": Int(cx),
                "zPos": Int(cz),
                "yPos": Int(-4),
                "Status": String("full"),
                "LastUpdate": Long(0),
                "sections": List[Compound](sections),
                "block_entities": List[Compound](bes),
                "isLightOn": Byte(1),
                "InhabitedTime": Long(0),
                "blending_data": Compound({"min_section": Int(-4), "max_section": Int(20)}),
            }
        )

    def write_regions(self, region_dir: Path):
        region_dir.mkdir(parents=True, exist_ok=True)
        by_region: dict[tuple[int, int], list[tuple[int, int]]] = defaultdict(list)
        for cx, cz in self.chunk_positions():
            by_region[(cx >> 5, cz >> 5)].append((cx, cz))

        for (rx, rz), chunks in by_region.items():
            # Build payload table: sector index -> bytes
            payloads = {}
            for cx, cz in chunks:
                raw = BytesIO()
                nbt.File(self.build_chunk(cx, cz)).write(raw)
                compressed = zlib.compress(raw.getvalue())
                # length includes compression byte
                payload = struct.pack(">I", len(compressed) + 1) + bytes([2]) + compressed
                # pad to 4096
                pad = (4096 - (len(payload) % 4096)) % 4096
                payloads[(cx & 31, cz & 31)] = payload + b"\x00" * pad

            locations = bytearray(4096)
            timestamps = bytearray(4096)
            # sector 0 and 1 are header
            next_sector = 2
            now = int(time.time())
            body = bytearray()
            for (lx, lz), payload in sorted(payloads.items()):
                sectors = len(payload) // 4096
                idx = 4 * (lx + lz * 32)
                locations[idx : idx + 3] = struct.pack(">I", next_sector)[1:]
                locations[idx + 3] = sectors
                timestamps[idx : idx + 4] = struct.pack(">I", now)
                # ensure body starts at sector 2
                while len(body) // 4096 < next_sector - 2:
                    body.extend(b"\x00" * 4096)
                body.extend(payload)
                next_sector += sectors

            path = region_dir / f"r.{rx}.{rz}.mca"
            with open(path, "wb") as f:
                f.write(locations)
                f.write(timestamps)
                f.write(body)


def make_level_dat(dest: Path):
    # Start from a known-good 1.20.1 level.dat and retarget to superflat.
    src = nbt.File.load(TEMPLATE / "level.dat", gzipped=True)
    data = src["Data"]
    data["LevelName"] = String(WORLD_NAME)
    data["DataVersion"] = Int(DATA_VERSION)
    data.pop("Player", None)
    # Clear spawn to structure area
    data["SpawnX"] = Int(8)
    data["SpawnY"] = Int(-60)
    data["SpawnZ"] = Int(8)
    data["GameType"] = Int(1)  # creative for review
    data["Difficulty"] = Byte(0)
    data["DifficultyLocked"] = Byte(0)

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
    # fresh world time
    data["Time"] = Long(0)
    data["DayTime"] = Long(1000)
    data["LastPlayed"] = Long(int(time.time() * 1000))

    dest.parent.mkdir(parents=True, exist_ok=True)
    nbt.File(src).save(dest, gzipped=True)


def main():
    if OUT.exists():
        # Replace region/entities only; keep folder if open lock absent
        lock = OUT / "session.lock"
        if lock.exists():
            try:
                lock.unlink()
            except PermissionError as exc:
                raise SystemExit(f"World is locked (close Minecraft): {exc}") from exc

    OUT.mkdir(parents=True, exist_ok=True)
    for sub in ("region", "entities", "data", "playerdata", "advancements", "stats", "poi", "datapacks", "serverconfig"):
        (OUT / sub).mkdir(exist_ok=True)

    make_level_dat(OUT / "level.dat")

    world = WorldWriter()
    # Flat pad around the four pieces
    world.fill_flat(-8, 48, -8, 48)
    placed = []
    for path, origin in STRUCTURES:
        size = world.paste(path, origin)
        placed.append({"file": path.name, "origin": list(origin), "size": list(size)})

    world.write_regions(OUT / "region")
    # empty entities region not required
    print({"world": str(OUT), "pieces": placed, "chunks": len(world.chunk_positions())})


if __name__ == "__main__":
    main()
