"""Align Manul root pivots with LatexSnowLeopardMale for catLike crouch.

Do NOT bump child offsets: the torso/head pivot must sit at the top of the
mesh (local y≈0), same as snep. Bumping children leaves the pivot floating
above the torso and opens a hip gap when CatUpperBodyCrouch leans forward.

Also lift thigh cubes from y=2 to y=0 so the hip meets the torso.
"""
from pathlib import Path
import re

path = Path("src/main/java/net/parkabird/changedsynergy/client/model/ManulModelLayers.java")
text = path.read_text(encoding="utf-8")
text, thigh_n = re.subn(
    r"(\.addBox\(-2\.0F, )2\.0F(, -2\.0F, 4\.0F, 5\.0F, 4\.0F)",
    r"\g<1>0.0F\2",
    text,
)
out = []
for line in text.splitlines(True):
    if 'root.addOrReplaceChild("Torso"' in line or 'root.addOrReplaceChild("Head"' in line:
        line = line.replace(
            ", 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));",
            ", -0.5F, 0.0F, 0.0F, 0.0F, 0.0F));",
        )
    if 'root.addOrReplaceChild("LeftArm"' in line or 'root.addOrReplaceChild("RightArm"' in line:
        line = line.replace(
            ", 3.5F, 0.0F, 0.0F, 0.0F, 0.0F));",
            ", 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));",
        )
    out.append(line)
text = "".join(out)
if "FF" in text:
    raise SystemExit("invalid FF float suffix present")
path.write_text(text, encoding="utf-8")
print(f"pivots: Torso/Head -0.5, Arms 1.5; thigh y 2->0: {thigh_n}; children unchanged")
