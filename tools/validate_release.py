#!/usr/bin/env python3
"""Static release checks for Changed: Synergy."""

from __future__ import annotations

import argparse
import json
import re
import sys
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]

# These entries intentionally use different argument positions by locale. The
# Chinese combat warnings omit the addressed player's name, while generated
# names select the English or Chinese argument supplied by the caller.
INTENTIONAL_LOCALE_PLACEHOLDER_DIFFERENCES = {
    "dialogue.changed_synergy.aquatic.hit_confused.0",
    "dialogue.changed_synergy.aquatic.hit_warning.0",
    "dialogue.changed_synergy.aquatic.hostility_confirmed.0",
    "dialogue.changed_synergy.dark.hit_confused.0",
    "dialogue.changed_synergy.dark.hit_warning.0",
    "dialogue.changed_synergy.dark.hostility_confirmed.0",
    "dialogue.changed_synergy.light.hit_confused.0",
    "dialogue.changed_synergy.light.hit_warning.0",
    "dialogue.changed_synergy.light.hostility_confirmed.0",
    "dialogue.changed_synergy.organic.hit_confused.0",
    "dialogue.changed_synergy.organic.hit_warning.0",
    "dialogue.changed_synergy.organic.hostility_confirmed.0",
    "dialogue.changed_synergy.white.hit_confused.0",
    "dialogue.changed_synergy.white.hit_warning.0",
    "dialogue.changed_synergy.white.hostility_confirmed.0",
    "name.changed_synergy.generated.localized_choice",
    "name.changed_synergy.generated.localized_choice.base",
}
LANG_DIR = ROOT / "src/main/resources/assets/changed_synergy/lang"
REQUIRED_FILES = (
    "LICENSE.txt",
    "NOTICE",
    "AUTHORS",
    "README.md",
    "CHANGELOG.md",
    "AI_DISCLOSURE.md",
    "THIRD_PARTY_NOTICES.md",
    "src/main/resources/META-INF/mods.toml",
    "src/main/resources/changed_synergy.mixins.json",
)
FORBIDDEN_TEXT = (
    "Zhuan" + "Z",
    "changed_" + "doom" + "sday",
    "Changed " + "Doom" + "sday",
)
TEXT_SUFFIXES = {
    ".java", ".json", ".toml", ".md", ".txt", ".properties", ".gradle",
    ".yml", ".yaml", ".mcmeta", ".cfg", ".accesswidener",
}
SKIP_PARTS = {
    ".git", ".gradle", ".idea", ".codex-tmp", ".local-artifacts", "build", "libs",
    "dist", "latex_moth_high_style_export", "mcmodsrepo", "release_backups",
    "recovery_backups", "run", "run-data",
}


class Audit:
    def __init__(self) -> None:
        self.errors: list[str] = []
        self.warnings: list[str] = []

    def error(self, message: str) -> None:
        self.errors.append(message)

    def warn(self, message: str) -> None:
        self.warnings.append(message)


def iter_publishable_files() -> list[Path]:
    files: list[Path] = []
    for path in ROOT.rglob("*"):
        if not path.is_file():
            continue
        relative = path.relative_to(ROOT)
        if any(part in SKIP_PARTS or part.startswith("build-") for part in relative.parts):
            continue
        files.append(path)
    return files


def check_required(audit: Audit) -> None:
    for relative in REQUIRED_FILES:
        if not (ROOT / relative).is_file():
            audit.error(f"missing required file: {relative}")


def check_json(audit: Audit, files: list[Path]) -> None:
    for path in files:
        if path.suffix.lower() != ".json":
            continue
        try:
            json.loads(path.read_text(encoding="utf-8"))
        except (OSError, UnicodeError, json.JSONDecodeError) as exc:
            audit.error(f"invalid JSON in {path.relative_to(ROOT)}: {exc}")


def check_languages(audit: Audit) -> None:
    try:
        english = json.loads((LANG_DIR / "en_us.json").read_text(encoding="utf-8"))
        chinese = json.loads((LANG_DIR / "zh_cn.json").read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        audit.error(f"cannot read language files: {exc}")
        return

    english_keys = set(english)
    chinese_keys = set(chinese)
    for key in sorted(english_keys - chinese_keys):
        audit.error(f"zh_cn is missing key: {key}")
    for key in sorted(chinese_keys - english_keys):
        audit.error(f"en_us is missing key: {key}")

    placeholder = re.compile(r"%(?:(\d+)\$)?[a-zA-Z%]")
    for key in sorted(english_keys & chinese_keys):
        def highest(value: object) -> int:
            if not isinstance(value, str):
                return 0
            indexes = [int(match.group(1)) for match in placeholder.finditer(value) if match.group(1)]
            return max(indexes, default=0)

        en_high = highest(english[key])
        zh_high = highest(chinese[key])
        if (
            en_high != zh_high
            and key not in INTENTIONAL_LOCALE_PLACEHOLDER_DIFFERENCES
        ):
            audit.warn(f"placeholder range differs for {key}: en={en_high}, zh={zh_high}")


def check_authorship_and_metadata(audit: Audit, files: list[Path]) -> None:
    for path in files:
        if path.suffix.lower() not in TEXT_SUFFIXES and path.name not in {
            "AUTHORS", "NOTICE", "LICENSE", "LICENSE.txt", ".gitignore",
        }:
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except (OSError, UnicodeError):
            continue
        for forbidden in FORBIDDEN_TEXT:
            if forbidden.casefold() in text.casefold():
                audit.error(f"forbidden legacy attribution or name in {path.relative_to(ROOT)}: {forbidden}")
        for declared_author in re.findall(r"@author\s+([^\r\n*]+)", text):
            if declared_author.strip() != "ParkaBird":
                audit.error(
                    f"non-ParkaBird @author in {path.relative_to(ROOT)}: "
                    f"{declared_author.strip()}"
                )
        if re.search(r"(?i)(?:[a-z]:\\users\\|/users/|/home/)[^\s\"'<>]+", text):
            audit.error(f"publishable file contains a user-home path: {path.relative_to(ROOT)}")

    properties = (ROOT / "gradle.properties").read_text(encoding="utf-8")
    expected = {
        "mod_authors": "ParkaBird",
        "mod_license": "GNU General Public License v3.0 or later",
        "mod_id": "changed_synergy",
    }
    values: dict[str, str] = {}
    for line in properties.splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    for key, value in expected.items():
        if values.get(key) != value:
            audit.error(f"gradle.properties {key} must be {value!r}")

    mods_toml = (ROOT / "src/main/resources/META-INF/mods.toml").read_text(encoding="utf-8")
    if 'displayTest="IGNORE_SERVER_VERSION"' in mods_toml:
        audit.error("mods.toml must not ignore the server version for a BOTH-side networked mod")

    authors = (ROOT / "AUTHORS").read_text(encoding="utf-8").splitlines()
    if [line.strip() for line in authors if line.strip()] != ["ParkaBird"]:
        audit.error("AUTHORS must contain ParkaBird as the sole credited project author")


def check_advancement_icons(audit: Audit) -> None:
    directory = ROOT / "src/main/resources/data/changed_synergy/advancements"
    by_icon: dict[str, list[str]] = {}
    for path in directory.rglob("*.json"):
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
            icon = data.get("display", {}).get("icon", {}).get("item")
        except (OSError, UnicodeError, json.JSONDecodeError):
            continue
        if icon:
            by_icon.setdefault(icon, []).append(path.name)
    for icon, names in sorted(by_icon.items()):
        if len(names) > 1:
            audit.error(f"advancement icon {icon} is reused by: {', '.join(sorted(names))}")


def check_jar(audit: Audit, jar_path: Path) -> None:
    if not jar_path.is_file():
        audit.error(f"JAR does not exist: {jar_path}")
        return
    required_entries = {
        "META-INF/mods.toml",
        "META-INF/accesstransformer.cfg",
        "changed_synergy.mixins.json",
        "LICENSE_changed_synergy.txt",
    }
    with zipfile.ZipFile(jar_path) as jar:
        names = set(jar.namelist())
        for entry in sorted(required_entries - names):
            audit.error(f"release JAR is missing {entry}")
        forbidden_prefixes = ("net/ltxprogrammer/", "net/foxyas/", "top/theillusivec4/curios/")
        bundled = sorted(name for name in names if name.startswith(forbidden_prefixes))
        if bundled:
            audit.error(f"release JAR bundles third-party classes, first entry: {bundled[0]}")
        for name in names:
            if not name.lower().endswith(tuple(TEXT_SUFFIXES)):
                continue
            try:
                text = jar.read(name).decode("utf-8")
            except (KeyError, UnicodeDecodeError):
                continue
            for forbidden in FORBIDDEN_TEXT:
                if forbidden.casefold() in text.casefold():
                    audit.error(f"release JAR contains forbidden text in {name}: {forbidden}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path, help="also inspect a built release JAR")
    args = parser.parse_args()

    audit = Audit()
    files = iter_publishable_files()
    check_required(audit)
    check_json(audit, files)
    check_languages(audit)
    check_authorship_and_metadata(audit, files)
    check_advancement_icons(audit)
    if args.jar:
        check_jar(audit, args.jar.resolve())

    for warning in audit.warnings:
        print(f"WARNING: {warning}")
    for error in audit.errors:
        print(f"ERROR: {error}")
    print(f"Checked {len(files)} publishable files: {len(audit.errors)} error(s), {len(audit.warnings)} warning(s).")
    return 1 if audit.errors else 0


if __name__ == "__main__":
    sys.exit(main())
