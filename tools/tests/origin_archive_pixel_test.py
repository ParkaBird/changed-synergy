"""Validate the 31 delivered assets and the localized cover layout contract."""
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parents[2]


def main():
    manifest = json.loads((ROOT / 'tools/authoring/origin_archive_pixel_manifest.json').read_text())
    assert len(manifest['textures']) == 31
    for name, record in manifest['textures'].items():
        path = ROOT / 'src/main/resources/assets/changed_synergy/textures/gui/origin_archive' / name
        assert hashlib.sha256(path.read_bytes()).hexdigest() == record['sha256'], name
        if name not in manifest['corner_plates_removed']: assert record['sha256'] == record['supplied_sha256'], name
    for lang in ('en_us', 'zh_cn', 'ru_ru'):
        words = json.loads((ROOT / f'src/main/resources/assets/changed_synergy/lang/{lang}.json').read_text(encoding='utf-8'))
        for key in ('cover_title', 'cover_subtitle', 'cover_register'):
            assert words['origin.changed_synergy.archive.' + key]
    source = (ROOT / 'src/main/java/net/parkabird/changedsynergy/client/NativeOriginScreen.java').read_text()
    assert 'active.capturedAt = now = Util.getMillis();' in source
    assert 'turnFraction >= 1' in source
    print('ORIGIN_ARCHIVE_PIXEL_PASS: all 31 mapped textures, three intentional corner edits, localized cover and preserved turn handoff')


if __name__ == '__main__': main()
