"""Import Latex Manul male/female bbmodels into Synergy Java layers + grey textures.

Usage:
  python tools/import_latex_manul_models.py "D:/Minecraft Mods/outputs/latex-manul-20261117"
"""
from __future__ import annotations

import hashlib
import json
import math
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from import_spotted_hyena_models import number, vector  # noqa: E402

TAIL_JOINTS = {
    "TailPrimary",
    "TailSecondary",
    "TailTertiary",
    "TailQuaternary",
    "TailQuinary",
}
REQUIRED = {
    "Head",
    "Torso",
    "LeftArm",
    "RightArm",
    "LeftLeg",
    "RightLeg",
    "LeftLowerLeg",
    "RightLowerLeg",
    "LeftFoot",
    "RightFoot",
    "LeftPad",
    "RightPad",
    "Tail",
    "TailPrimary",
    "TailSecondary",
    "TailTertiary",
    "TailQuaternary",
    "LeftEar",
    "RightEar",
}


def convert(source: Path) -> tuple[str, dict]:
    data = json.loads(source.read_text(encoding="utf-8"))
    assert data["meta"]["model_format"] == "modded_entity" and data["modded_entity_flip_y"]
    groups = {g["uuid"]: g for g in data["groups"]}
    cubes = {c["uuid"]: c for c in data["elements"]}
    lines = [
        "        MeshDefinition mesh = new MeshDefinition();",
        "        PartDefinition root = mesh.getRoot();",
    ]
    count = 0
    names: set[str] = set()

    def visit(nodes, parent, pivot):
        nonlocal count
        for node in nodes:
            if isinstance(node, dict):
                group = groups[node["uuid"]]
                if not group.get("export", True):
                    continue
                name = group["name"]
                assert name not in names, f"duplicate bone: {name}"
                names.add(name)
                origin = group.get("origin", [0, 0, 0])
                offset = [pivot[0] - origin[0], pivot[1] - origin[1], origin[2] - pivot[2]]
                rotation = group.get("rotation", [0, 0, 0])
                angles = [
                    math.radians(-rotation[0]),
                    math.radians(-rotation[1]),
                    math.radians(rotation[2]),
                ]
                tail_joint = name in TAIL_JOINTS
                lines.append(
                    f'        PartDefinition {name} = {parent}.addOrReplaceChild("{name}", '
                    f"CubeListBuilder.create(), PartPose.offsetAndRotation("
                    f"{vector(offset + ([0, 0, 0] if tail_joint else angles))}));"
                )
                content = name
                if tail_joint:
                    content = name + "_bind_r1"
                    lines.append(
                        f'        PartDefinition {content} = {name}.addOrReplaceChild("{content}", '
                        f"CubeListBuilder.create(), PartPose.offsetAndRotation("
                        f"{vector([0, 0, 0] + angles)}));"
                    )
                visit(node.get("children", []), content, origin)
            else:
                cube = cubes[node]
                if not cube.get("export", True):
                    continue
                assert cube["type"] == "cube" and cube.get("box_uv", data["meta"]["box_uv"])
                assert cube.get("stretch", [1, 1, 1]) == [1, 1, 1]
                count += 1
                name = f"cube_{count}_r1"
                origin = cube.get("origin", pivot)
                offset = [pivot[0] - origin[0], pivot[1] - origin[1], origin[2] - pivot[2]]
                rotation = cube.get("rotation", [0, 0, 0])
                angles = [
                    math.radians(-rotation[0]),
                    math.radians(-rotation[1]),
                    math.radians(rotation[2]),
                ]
                low, high = cube["from"], cube["to"]
                box = [origin[0] - high[0], origin[1] - high[1], low[2] - origin[2]] + [
                    high[i] - low[i] for i in range(3)
                ]
                uv = cube.get("uv_offset", [0, 0])
                mirror = ".mirror()" if cube.get("mirror_uv", False) else ""
                lines.append(
                    f'        {parent}.addOrReplaceChild("{name}", CubeListBuilder.create()'
                    f".texOffs({uv[0]}, {uv[1]}){mirror}"
                    f".addBox({vector(box)}, new CubeDeformation({number(cube.get('inflate', 0))})), "
                    f"PartPose.offsetAndRotation({vector(offset + angles)}));"
                )

    visit(data["outliner"], "root", [0, 24, 0])
    assert REQUIRED <= names, f"missing bones: {REQUIRED - names}"
    width, height = data["resolution"]["width"], data["resolution"]["height"]
    lines.append(f"        return LayerDefinition.create(mesh, {width}, {height});")
    report = {
        "source": source.name,
        "sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
        "cubes": count,
        "bones": sorted(names),
        "resolution": [width, height],
    }
    return "\n".join(lines), report


def copy_texture(src: Path, dest_name: str) -> str:
    dest = ROOT / "src/main/resources/assets/changed_synergy/textures" / dest_name
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dest)
    return hashlib.sha256(dest.read_bytes()).hexdigest()


def main() -> None:
    folder = Path(sys.argv[1])
    tex = folder / "textures"
    male_body, male_report = convert(folder / "bbmodel" / "latex_manul.bbmodel")
    female_body, female_report = convert(folder / "bbmodel" / "latex_manul_female.bbmodel")
    textures = {
        "latex_manul.png": copy_texture(tex / "latex_manul.png", "latex_manul.png"),
        "latex_manul_female.png": copy_texture(tex / "latex_manul_female.png", "latex_manul_female.png"),
        "latex_manul_orange.png": copy_texture(tex / "latex_manul_orange.png", "latex_manul_orange.png"),
        "latex_manul_female_orange.png": copy_texture(
            tex / "latex_manul_female_orange.png", "latex_manul_female_orange.png"
        ),
    }
    code = f'''package net.parkabird.changedsynergy.client.model;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/** Generated by tools/import_latex_manul_models.py; original pivots and UVs retained. */
public final class ManulModelLayers {{
    private ManulModelLayers() {{}}

    public static LayerDefinition male() {{
{male_body}
    }}

    public static LayerDefinition female() {{
{female_body}
    }}
}}
'''
    out = ROOT / "src/main/java/net/parkabird/changedsynergy/client/model/ManulModelLayers.java"
    out.write_text(code, encoding="utf-8")
    manifest = {
        "male": male_report,
        "female": female_report,
        "textures": textures,
    }
    (ROOT / "tools/latex_manul_models_manifest.json").write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    main()
