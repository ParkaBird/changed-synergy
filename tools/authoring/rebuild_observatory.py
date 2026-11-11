"""Rebuild observatory.nbt from the renovated world save in '新的世界 (1)',
preserving authored resident entities, tile entities, public store chests, and lore,
while properly un-rotating ConnectedFloorBlock carpet states and expanding height to 26.
"""
from copy import deepcopy
import gzip
import hashlib
import io
import json
from pathlib import Path
import struct
import sys
import zlib
import nbtlib

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from camp_public_stores import add_public_store
from structure_residents import bind_residents

REGION_PATH = Path(r'D:\Program Files\.minecraft\versions\测试专用\saves\新的世界 (1)\region\r.-1.-1.mca')
TARGET_NBT = ROOT / 'src/main/resources/data/changed_synergy/structures/observatory.nbt'
MANIFEST_PATH = ROOT / 'tools/observatory_manifest.json'

ROT_FACING = {'north': 'east', 'east': 'south', 'south': 'west', 'west': 'north'}
ROT_AXIS = {'x': 'z', 'z': 'x', 'y': 'y'}
ROT_C90 = {
    1: 12, 2: 24, 3: 36, 4: 5, 5: 17, 6: 7, 7: 19, 8: 9, 9: 21, 10: 11,
    11: 23, 12: 3, 13: 15, 14: 27, 15: 39, 16: 4, 17: 16, 18: 6, 19: 18, 20: 8,
    21: 20, 22: 10, 23: 22, 24: 2, 25: 14, 26: 26, 27: 38, 28: 29, 29: 41, 30: 31,
    31: 43, 32: 33, 33: 45, 34: 35, 35: 34, 36: 1, 37: 13, 38: 25, 39: 37, 40: 28,
    41: 40, 42: 30, 43: 41, 44: 32, 45: 44
}


def load_chunks():
    chunks = {}
    with open(REGION_PATH, 'rb') as f:
        for cx in range(-6, -3):
            for cz in range(-5, -2):
                loc_offset = 4 * ((cx & 31) + (cz & 31) * 32)
                f.seek(loc_offset)
                loc = struct.unpack('>I', f.read(4))[0]
                offset = (loc >> 8) * 4096
                if not offset:
                    continue
                f.seek(offset)
                length, compression = struct.unpack('>IB', f.read(5))
                data = f.read(length - 1)
                raw = zlib.decompress(data)
                chunks[(cx, cz)] = nbtlib.File.from_fileobj(io.BytesIO(raw))
    return chunks


def get_world_block(chunks, wx, wy, wz):
    cx, cz = wx >> 4, wz >> 4
    if (cx, cz) not in chunks:
        return 'minecraft:air', {}
    chunk = chunks[(cx, cz)]
    sy = wy >> 4
    for section in chunk['sections']:
        if int(section['Y']) == sy:
            if 'block_states' not in section:
                return 'minecraft:air', {}
            bs = section['block_states']
            palette = bs['palette']
            if 'data' not in bs:
                entry = palette[0]
                return str(entry['Name']), {str(k): str(v) for k, v in entry.get('Properties', {}).items()}
            data = [int(v) for v in bs['data']]
            bits = max(4, (len(palette) - 1).bit_length())
            bpl = 64 // bits
            lx, ly, lz = wx & 15, wy & 15, wz & 15
            idx = (ly << 8) | (lz << 4) | lx
            long_idx = idx // bpl
            offset = (idx % bpl) * bits
            mask = (1 << bits) - 1
            raw_val = data[long_idx]
            if raw_val < 0:
                raw_val += (1 << 64)
            p_idx = (raw_val >> offset) & mask
            entry = palette[p_idx]
            return str(entry['Name']), {str(k): str(v) for k, v in entry.get('Properties', {}).items()}
    return 'minecraft:air', {}


def main():
    print(f'Loading region chunks from {REGION_PATH}...')
    chunks = load_chunks()
    print(f'Loaded {len(chunks)} chunks.')

    baseline = nbtlib.load(TARGET_NBT)
    orig_nbt = {tuple(map(int, b['pos'])): deepcopy(b['nbt']) for b in baseline['blocks'] if 'nbt' in b}
    entities = deepcopy(baseline['entities'])

    palette_map = {}
    palette_list = nbtlib.List[nbtlib.Compound]()

    def get_state_idx(name, props):
        key = (name, tuple(sorted(props.items())))
        if key in palette_map:
            return palette_map[key]
        entry = nbtlib.Compound({'Name': nbtlib.String(name)})
        if props:
            entry['Properties'] = nbtlib.Compound({k: nbtlib.String(v) for k, v in props.items()})
        idx = len(palette_list)
        palette_list.append(entry)
        palette_map[key] = idx
        return idx

    blocks_list = nbtlib.List[nbtlib.Compound]()
    # Dimensions: 41 x 26 x 27
    for tx in range(41):
        for ty in range(26):
            for tz in range(27):
                wx = tz - 85
                wy = ty - 61
                wz = -tx - 36
                w_name, w_props = get_world_block(chunks, wx, wy, wz)
                rot_props = dict(w_props)
                if 'facing' in rot_props and rot_props['facing'] in ROT_FACING:
                    rot_props['facing'] = ROT_FACING[rot_props['facing']]
                if 'axis' in rot_props and rot_props['axis'] in ROT_AXIS:
                    rot_props['axis'] = ROT_AXIS[rot_props['axis']]
                if 'rotation' in rot_props:
                    rot_props['rotation'] = str((int(rot_props['rotation']) + 4) % 16)
                if 'state' in rot_props:
                    st = int(rot_props['state'])
                    rot_props['state'] = str(ROT_C90.get(st, st))

                s_idx = get_state_idx(w_name, rot_props)
                block_tag = nbtlib.Compound({
                    'pos': nbtlib.List[nbtlib.Int]([tx, ty, tz]),
                    'state': nbtlib.Int(s_idx)
                })
                if (tx, ty, tz) in orig_nbt:
                    block_tag['nbt'] = deepcopy(orig_nbt[(tx, ty, tz)])
                blocks_list.append(block_tag)

    new_template = nbtlib.File(nbtlib.Compound({
        'DataVersion': baseline['DataVersion'],
        'size': nbtlib.List[nbtlib.Int]([41, 26, 27]),
        'palette': palette_list,
        'blocks': blocks_list,
        'entities': entities
    }))

    bind_residents(new_template, 'observatory')

    buf = io.BytesIO()
    new_template.write(buf)
    compressed = gzip.compress(buf.getvalue(), mtime=0)
    TARGET_NBT.write_bytes(compressed)
    out_sha = hashlib.sha256(compressed).hexdigest()
    print(f'Wrote observatory.nbt: size=[41, 26, 27], blocks={len(blocks_list)}, sha256={out_sha}')

    manifest = json.loads(MANIFEST_PATH.read_text(encoding='utf-8'))
    manifest['size'] = [41, 26, 27]
    manifest['output_sha256'] = out_sha
    MANIFEST_PATH.write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    print('Updated observatory_manifest.json')


if __name__ == '__main__':
    main()
