"""Build a superflat Burrow editing world via Gradle runServer + StructureEditorWorld and install it."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import struct
import subprocess
import zlib
import io
from pathlib import Path

import nbtlib as nbt

ROOT = Path(__file__).resolve().parents[2]
RUN_DIR = ROOT / "run"
PROPS_PATH = RUN_DIR / "server.properties"
WORLD_NAME = "burrow-editor"
WORLD_DIR = RUN_DIR / WORLD_NAME
SAVES_DIR = Path(r"D:\Program Files\.minecraft\versions\测试专用\saves")
TARGET_SAVE = SAVES_DIR / "Burrow"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--finalize-only", action="store_true")
    args = parser.parse_args()

    assert not TARGET_SAVE.exists(), f"Destination already exists, refusing to overwrite: {TARGET_SAVE}"
    if not args.finalize_only:
        original_props = PROPS_PATH.read_text(encoding="utf-8") if PROPS_PATH.is_file() else None
        if WORLD_DIR.exists():
            shutil.rmtree(WORLD_DIR)

        flat_settings = {
            "layers": [
                {"block": "minecraft:bedrock", "height": 1},
                {"block": "minecraft:stone", "height": 96},
                {"block": "minecraft:dirt", "height": 30},
                {"block": "minecraft:grass_block", "height": 1},
            ],
            "biome": "minecraft:snowy_taiga",
            "features": False,
            "lakes": False,
            "structure_overrides": [],
        }
        editor_props = "\n".join(
            [
                "server-ip=127.0.0.1",
                "server-port=0",
                "online-mode=false",
                f"level-name={WORLD_NAME}",
                "level-seed=20261125",
                "level-type=minecraft\\:flat",
                "generate-structures=false",
                "spawn-protection=0",
                "view-distance=6",
                "simulation-distance=6",
                "max-tick-time=-1",
                "gamemode=creative",
                "force-gamemode=true",
                "allow-flight=true",
                "difficulty=normal",
                "sync-chunk-writes=true",
                "generator-settings=" + json.dumps(flat_settings, separators=(",", ":")),
                "",
            ]
        )

        env = os.environ.copy()
        java_home = Path(os.environ["APPDATA"]) / ".minecraft/runtime/java-runtime-beta"
        if java_home.is_dir():
            env["JAVA_HOME"] = str(java_home)
            env["PATH"] = f"{java_home / 'bin'};{env.get('PATH', '')}"
        env["CHANGED_SYNERGY_BUILD_EDITOR_WORLD"] = "true"
        env["CHANGED_SYNERGY_EDITOR_TEMPLATE"] = "burrow"
        env["CHANGED_SYNERGY_EDITOR_ORIGIN_Y"] = "43"

        try:
            PROPS_PATH.write_text(editor_props, encoding="utf-8")
            proc = subprocess.run(
                [
                    str(ROOT / "gradlew.bat"),
                    "-I",
                    "tools/authoring/structure-editor.init.gradle",
                    "runServer",
                    "--no-daemon",
                    "-PwithoutAddon",
                ],
                cwd=ROOT,
                env=env,
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                timeout=300,
            )
            output = proc.stdout + "\n" + proc.stderr
            for line in output.splitlines():
                if any(k in line for k in ("EDITOR_SITE_READY", "STRUCTURE_EDITOR_WORLD", "FAILURE", "Exception")):
                    print(line)
            assert proc.returncode == 0, f"runServer failed ({proc.returncode}):\n{output[-3000:]}"
            assert "STRUCTURE_EDITOR_WORLD_PASS: structures=1;" in output, output[-3000:]
            assert "STRUCTURE_EDITOR_WORLD_FAIL" not in output, output[-3000:]
        finally:
            if original_props is not None:
                PROPS_PATH.write_text(original_props, encoding="utf-8")

    # Configure level.dat cleanly
    data = nbt.load(WORLD_DIR / "level.dat")
    tag = data["Data"]
    assert str(tag["WorldGenSettings"]["dimensions"]["minecraft:overworld"]["generator"]["type"]) == "minecraft:flat"
    tag["LevelName"] = nbt.String("Burrow")
    tag["GameType"] = nbt.Int(1)
    tag["allowCommands"] = nbt.Byte(1)
    tag["SpawnX"] = nbt.Int(28)
    tag["SpawnY"] = nbt.Int(64)
    tag["SpawnZ"] = nbt.Int(-8)
    tag["SpawnAngle"] = nbt.Float(0.0)
    data.save()

    # Verify zero dropped item entities and all hanging entities intact inside the saved world
    item_drops = 0
    paintings = 0
    frames = 0
    creatures = 0
    ent_dir = WORLD_DIR / "entities"
    for mca in ent_dir.glob("*.mca"):
        raw_bytes = mca.read_bytes()
        if len(raw_bytes) < 8192:
            continue
        locs = raw_bytes[:4096]
        for i in range(1024):
            off = struct.unpack(">I", locs[i * 4 : i * 4 + 4])[0] >> 8
            cnt = locs[i * 4 + 3]
            if off == 0 or cnt == 0:
                continue
            pos = off * 4096
            length, _ = struct.unpack(">IB", raw_bytes[pos : pos + 5])
            cnbt = nbt.File.parse(io.BytesIO(zlib.decompress(raw_bytes[pos + 5 : pos + 4 + length])))
            for e in cnbt.get("Entities", []):
                eid = str(e.get("id", ""))
                if eid == "minecraft:item":
                    item_drops += 1
                elif eid == "minecraft:painting":
                    paintings += 1
                elif eid == "minecraft:item_frame":
                    frames += 1
                elif eid.startswith(("changed:", "changed_synergy:")):
                    creatures += 1

    print(f"Verified saved entities: drops={item_drops}, paintings={paintings}, item_frames={frames}, changed_creatures={creatures}")
    assert item_drops == 0, f"Found {item_drops} dropped item entities!"
    assert paintings == 8, f"Expected 8 paintings, found {paintings}"
    assert frames == 6, f"Expected 6 item frames, found {frames}"

    source = ROOT / "src/main/resources/data/changed_synergy/structures/burrow.nbt"
    manifest = {
        "structure": "changed_synergy:burrow",
        "origin": [0, 43, 0],
        "size": [56, 34, 55],
        "ground_y": 63,
        "teleport": "/tp @s 28 64 -5",
        "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
    }
    (WORLD_DIR / "synergy_editing/manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    shutil.move(str(WORLD_DIR), str(TARGET_SAVE))
    print(f"Installed Burrow superflat editing save at: {TARGET_SAVE}")


if __name__ == "__main__":
    main()
