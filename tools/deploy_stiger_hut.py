"""Deploy stiger_hut underground small camp with public/private reserve and latex_stiger resident."""
from __future__ import annotations

import gzip
import hashlib
import io
import sys
from copy import deepcopy
from pathlib import Path

import nbtlib as nbt

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/data/changed_synergy/structures/stiger_hut.nbt"


def main() -> None:
    src = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    data = nbt.load(src)
    assert list(map(int, data["size"])) == [16, 9, 15]
    palette = data["palette"]

    # Mark chest as small camp reserve and re-point LootTable to changed_synergy:chests/stiger_hut
    chests = 0
    for b in data["blocks"]:
        name = str(palette[int(b["state"])]["Name"])
        assert "farmersdelight" not in name, f"Unexpected FD block in stiger_hut: {name}"
        if "nbt" in b:
            b["nbt"].pop("ForgeCaps", None)
        if name == "minecraft:chest":
            tag = b["nbt"]
            tag["LootTable"] = nbt.String("changed_synergy:chests/stiger_hut")
            tag["CustomName"] = nbt.String('{"translate":"container.changed_synergy.camp_supplies"}')
            flags = tag.setdefault("ForgeData", nbt.Compound())
            flags["ChangedSynergyPublicStorage"] = nbt.Byte(1)
            flags["ChangedSynergyPrivate"] = nbt.Byte(1)
            chests += 1
    assert chests == 1, f"Expected 1 chest in stiger_hut, found {chests}"

    # Verify floor & open air at (7, 1..3, 7) before spawning latex_stiger
    grid = {tuple(map(int, b["pos"])): str(palette[int(b["state"])]["Name"]) for b in data["blocks"]}
    assert grid[(7, 0, 7)] != "minecraft:air"
    assert grid[(7, 1, 7)] == "minecraft:air" and grid[(7, 2, 7)] == "minecraft:air" and grid[(7, 3, 7)] == "minecraft:air"

    pos = nbt.List[nbt.Double]([7.5, 1.0, 7.5])
    resident_tag = nbt.Compound(
        {
            "id": nbt.String("changed:latex_stiger"),
            "Pos": deepcopy(pos),
            "Rotation": nbt.List[nbt.Float]([-90.0, 0.0]),
            "PersistenceRequired": nbt.Byte(1),
            "ForgeData": nbt.Compound(
                {
                    "ChangedSynergyLightReputationGroup": nbt.String("cave"),
                    "ChangedSynergyRandomStructureEyes": nbt.Byte(1),
                    "ChangedSynergyStructureResident": nbt.Compound(
                        {
                            "Template": nbt.String("stiger_hut"),
                            "LocalPos": deepcopy(pos),
                            "Size": deepcopy(data["size"]),
                            "Yaw": nbt.Float(-90.0),
                            "Outfit": nbt.String(""),
                            "Camp": nbt.Byte(1),
                            "Character": nbt.Byte(0),
                            "ReserveOnly": nbt.Byte(1),
                        }
                    ),
                }
            ),
        }
    )
    data["entities"] = nbt.List[nbt.Compound](
        [
            nbt.Compound(
                {
                    "pos": pos,
                    "blockPos": nbt.List[nbt.Int]([7, 1, 7]),
                    "nbt": resident_tag,
                }
            )
        ]
    )

    buf = io.BytesIO()
    data.write(buf)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(gzip.compress(buf.getvalue(), mtime=0))
    print(f"Wrote {OUT} (sha256={hashlib.sha256(OUT.read_bytes()).hexdigest()[:12]}, chest={chests}, resident=changed:latex_stiger@(7.5,1,7.5))")


if __name__ == "__main__":
    main()
