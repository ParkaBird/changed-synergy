"""Selectively sync Point Prometheus edits from the structure editor save.

Applies only:
  - iron_crate <-> cardboard_box swap under the stairs
  - surveillance_camera placements
  - large_library_door -> large_lab_door replacements

Ignores Farmer's Delight gaps, entity loss, and unrelated block edits.
"""
from __future__ import annotations

import gzip
import hashlib
import json
import math
import struct
import zlib
from io import BytesIO
from pathlib import Path

import nbtlib as nbt
from nbtlib import Compound, File, Float, Int, List, String

ROOT = Path(__file__).resolve().parents[1]
SAVE = Path(r"D:\Program Files\.minecraft\versions\测试专用\saves\Synergy-普罗米修斯编辑-20261119")
REGION = SAVE / "region"
BASE_PATH = ROOT / "src/main/resources/data/changed_synergy/structures/point_prometheus.nbt"
MANIFEST = ROOT / "tools/point_prometheus_edit_sync_manifest.json"
ORIGIN = (0, 64, 0)
SIZE = (70, 27, 52)

CAMERA = "changed_synergy:surveillance_camera"
SWAP_A = "changed:iron_crate"
SWAP_B = "changed:cardboard_box"
DOOR_FROM = "changed:large_library_door"
DOOR_TO = "changed:large_lab_door"
SWAP_POSITIONS = ((47, 2, 19), (47, 3, 19), (48, 2, 19), (48, 3, 19))


def read_mca(folder: Path, cx: int, cz: int):
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
    raw = zlib.decompress(raw) if comp == 2 else gzip.decompress(raw)
    return nbt.File.parse(BytesIO(raw))


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


def clone_state(entry: Compound) -> Compound:
    out = Compound({"Name": String(str(entry["Name"]))})
    if "Properties" in entry:
        props = Compound()
        for k, v in entry["Properties"].items():
            props[k] = String(str(v))
        out["Properties"] = props
    return out


def state_key(entry: Compound) -> str:
    name = str(entry["Name"])
    props = entry.get("Properties")
    if not props:
        return name
    items = ",".join(f"{k}={props[k]}" for k in sorted(props.keys()))
    return f"{name}[{items}]"


def load_world_interest():
    ox, oy, oz = ORIGIN
    sx, sy, sz = SIZE
    states: dict[tuple[int, int, int], Compound] = {}
    bes: dict[tuple[int, int, int], Compound] = {}
    wanted = {CAMERA, SWAP_A, SWAP_B, DOOR_FROM, DOOR_TO}
    for cx in range(ox >> 4, ((ox + sx - 1) >> 4) + 1):
        for cz in range(oz >> 4, ((oz + sz - 1) >> 4) + 1):
            chunk = read_mca(REGION, cx, cz)
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
                    entry = pal[0 if data is None else pi]
                    name = str(entry["Name"])
                    if name in wanted:
                        states[(wx - ox, wy - oy, wz - oz)] = clone_state(entry)
            for be in chunk.get("block_entities", []):
                x, y, z = int(be["x"]), int(be["y"]), int(be["z"])
                if not (ox <= x < ox + sx and oy <= y < oy + sy and oz <= z < oz + sz):
                    continue
                if str(be.get("id", "")) == CAMERA:
                    bes[(x - ox, y - oy, z - oz)] = be
    return states, bes


def main():
    T = nbt.load(BASE_PATH)
    orig_pal = [clone_state(e) for e in T["palette"]]
    world_states, world_bes = load_world_interest()
    by_pos = {tuple(map(int, b["pos"])): b for b in T["blocks"]}

    cameras = 0
    doors = 0
    swaps = 0
    camera_positions = []

    for pos in SWAP_POSITIONS:
        w = world_states[pos]
        b = by_pos[pos]
        old = str(orig_pal[int(b["state"])]["Name"])
        new = str(w["Name"])
        if {old, new} != {SWAP_A, SWAP_B}:
            raise AssertionError(f"Unexpected swap at {pos}: {old} -> {new}")
        b["_new_state"] = w
        b.pop("nbt", None)
        swaps += 1

    for pos, w in sorted(world_states.items()):
        if str(w["Name"]) != CAMERA:
            continue
        b = by_pos.get(pos)
        if b is None:
            raise AssertionError(f"Camera outside template grid at {pos}")
        cur = str(orig_pal[int(b["state"])]["Name"])
        if cur != "minecraft:air":
            raise AssertionError(f"Camera replaces non-air at {pos}: {cur}")
        be = world_bes.get(pos)
        if be is None:
            raise AssertionError(f"Missing camera block entity at {pos}")
        b["_new_state"] = w
        b["nbt"] = Compound(
            {
                "id": String(CAMERA),
                "DefaultYaw": Float(float(be.get("DefaultYaw", 0.0))),
                "DefaultPitch": Float(float(be.get("DefaultPitch", 26.0))),
                "LedState": Int(int(be.get("LedState", 0))),
                "TargetId": Int(-1),
            }
        )
        cameras += 1
        camera_positions.append(list(pos))

    for pos, w in sorted(world_states.items()):
        if str(w["Name"]) != DOOR_TO:
            continue
        b = by_pos[pos]
        if "_new_state" in b:
            raise AssertionError(f"Door overlaps other edit at {pos}")
        cur = str(orig_pal[int(b["state"])]["Name"])
        if cur != DOOR_FROM:
            raise AssertionError(f"Lab door did not replace library door at {pos}: {cur}")
        b["_new_state"] = w
        b.pop("nbt", None)
        doors += 1

    palette: list[Compound] = []
    index: dict[str, int] = {}

    def ensure(entry: Compound) -> int:
        key = state_key(entry)
        if key not in index:
            index[key] = len(palette)
            palette.append(clone_state(entry))
        return index[key]

    for b in T["blocks"]:
        if "_new_state" in b:
            entry = b.pop("_new_state")
        else:
            entry = orig_pal[int(b["state"])]
        b["state"] = Int(ensure(entry))

    T["palette"] = List[Compound](palette)
    buf = BytesIO()
    File(T).write(buf)
    BASE_PATH.write_bytes(gzip.compress(buf.getvalue(), mtime=0))

    report = {
        "source_world": str(SAVE),
        "cameras": cameras,
        "doors_library_to_lab": doors,
        "crate_box_swap_blocks": swaps,
        "camera_positions": camera_positions,
        "swap_positions": [list(p) for p in SWAP_POSITIONS],
        "output_sha256": hashlib.sha256(BASE_PATH.read_bytes()).hexdigest(),
        "notes": [
            "Only iron_crate/cardboard_box stair swap, cameras, and library->lab doors were synced.",
            "Farmer's Delight gaps, entity loss, and other edits were ignored.",
        ],
    }
    MANIFEST.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
