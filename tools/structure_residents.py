"""Resident metadata kept in templates; world coordinates are resolved at spawn."""
from copy import deepcopy
import nbtlib as nbt
from camp_public_stores import add_public_store
from camp_rest_area import replace_hideout_bedrolls
from camp_private_stores import mark_private_stores


def add_treehouse_bees(template):
    """Populate the authored hive without touching the edited building or its other residents."""
    marker = 'changed_synergy:treehouse_hive_bee'
    existing = [e for e in template['entities'] if marker in e['nbt'].get('Tags', [])]
    if existing:
        assert len(existing) == 3
        return 0
    palette = template['palette']
    blocks = {tuple(map(int, b['pos'])): str(palette[int(b['state'])]['Name']) for b in template['blocks']}
    added = []
    for x, z in ((9, 4), (10, 6), (9, 5)):
        assert blocks[(x, 1, z)] == blocks[(x, 2, z)] == 'minecraft:air'
        assert blocks[(x, 0, z)] != 'minecraft:air'
        pos = nbt.List[nbt.Double]([x+.5, 1, z+.5])
        added.append(nbt.Compound({'pos': pos, 'blockPos': nbt.List[nbt.Int]([x, 1, z]),
            'nbt': nbt.Compound({'id': nbt.String('changed:latex_bee'), 'Pos': deepcopy(pos),
                'Rotation': nbt.List[nbt.Float]([180, 0]), 'Tags': nbt.List[nbt.String]([marker])})}))
    bind_residents({'size': template['size'], 'entities': added}, 'forest_treehouse')
    template['entities'].extend(added)
    return len(added)


def mark_default_eyes(tag):
    info = tag.get('LocalVariantInfo')
    resident = tag.get('ForgeData', {}).get('ChangedSynergyStructureResident')
    default = info is not None and int(info.get('eyeStyle', -1)) == 1 and all(
        int(info.get(key, info.get('iris', -1))) == 32767 for key in ('irisLeft', 'irisRight'))
    if default or info is None and resident:
        tag.setdefault('ForgeData', nbt.Compound())['ChangedSynergyRandomStructureEyes'] = nbt.Byte(1)


def bind_residents(template, name):
    add_public_store(template, name)
    mark_private_stores(template, name)
    replace_hideout_bedrolls(template, name)
    if name == 'forest_treehouse':
        for block in template['blocks']:
            tag = block.get('nbt', {})
            if 'Latex Leaf' in str(tag.get('Text', '')):
                tag['Text'] = nbt.String(str(tag['Text']).replace('Latex Leaf', 'Latex Silk Moth'))
    for entity in template.get('entities', []):
        tag = entity['nbt']
        kind = str(tag.get('id'))
        outfit = ''
        character = False
        if name == 'hyena_hideout' and kind == 'changed_synergy:latex_spotted_hyena_female':
            # Inside the orange-bed room, on the bottom slab beside its foot.
            entity['pos'] = nbt.List[nbt.Double]([22.5, 4.5, 23.5])
            entity['blockPos'] = nbt.List[nbt.Int]([22, 4, 23])
            outfit = 'changed_synergy:hyena_trailcloth_outfit'
            character = True
            tag['LocalVariantInfo'] = nbt.Compound({'eyeStyle': nbt.Int(1), 'irisLeft': nbt.Int(0xB8744C),
                'irisRight': nbt.Int(0xB8744C), 'sclera': nbt.Int(0xFFFFFF), 'scale': nbt.Float(1)})
        elif name == 'green_lab' and kind == 'changed:gas_wolf_male':
            # The captured entity stood inside its office chair rather than being mounted on it.
            entity['pos'] = nbt.List[nbt.Double]([25.5, 13, 33.5])
            entity['blockPos'] = nbt.List[nbt.Int]([25, 13, 33])
            outfit = 'changed:lab_coat'
            character = True
        elif name == 'forest_treehouse' and (kind == 'changed:latex_leaf'
                or kind == 'changed_synergy:latex_silk_moth' and tag.get('ForgeData', {}).get('ChangedSynergyStructureResident', {}).get('Character')):
            tag['id'] = nbt.String('changed_synergy:latex_silk_moth')
            tag.pop('Attributes', None)
            outfit = 'changed_synergy:forest_canopy_outfit'
            character = True
        elif name == 'observatory' and kind == 'changed_synergy:latex_eagle_owl':
            outfit = 'changed_synergy:eagle_owl_aviator_outfit'
            character = True
        camp = name in ('forest_treehouse', 'observatory', 'desert_refuge') or name.startswith('hyena_')
        if not character and not (camp and kind.startswith(('changed:', 'changed_synergy:'))):
            if camp and kind in ('minecraft:sheep', 'minecraft:cow', 'minecraft:pig', 'minecraft:chicken', 'minecraft:rabbit'):
                tags = tag.setdefault('Tags', nbt.List[nbt.String]())
                if 'changed_synergy:camp_livestock' not in tags:
                    tags.append(nbt.String('changed_synergy:camp_livestock'))
            continue
        tag['Pos'] = deepcopy(entity['pos'])
        tag['PersistenceRequired'] = nbt.Byte(1)
        if name == 'forest_treehouse':
            tag.setdefault('ForgeData', nbt.Compound())['ChangedSynergyLightReputationGroup'] = nbt.String('forest')
        elif name == 'observatory':
            tag.setdefault('ForgeData', nbt.Compound())['ChangedSynergyLightReputationGroup'] = nbt.String('mountain')
        elif name == 'desert_refuge':
            tag.setdefault('ForgeData', nbt.Compound())['ChangedSynergyLightReputationGroup'] = nbt.String('desert')
        tag.setdefault('ForgeData', nbt.Compound())['ChangedSynergyStructureResident'] = nbt.Compound({
            'Template': nbt.String(name), 'LocalPos': deepcopy(entity['pos']),
            'Size': deepcopy(template['size']),
            'Yaw': nbt.Float(tag.get('Rotation', [0])[0]), 'Outfit': nbt.String(outfit),
            'Camp': nbt.Byte(camp), 'Character': nbt.Byte(character),
        })
        if name == 'green_lab' and character:
            tag['ForgeData']['ChangedSynergyStructureResident']['NeckOutfit'] = nbt.String('changed:red_neck_tie')
        if character and name in ('hyena_hideout', 'forest_treehouse', 'green_lab'):
            style, iris = {'hyena_hideout': (1, 0xB8744C), 'forest_treehouse': (2, 0x90B8C2), 'green_lab': (0, 0x65C98C)}[name]
            info = tag.setdefault('LocalVariantInfo', nbt.Compound())
            info.update({'eyeStyle': nbt.Int(style), 'irisLeft': nbt.Int(iris), 'irisRight': nbt.Int(iris)})
            tag['ForgeData'].pop('ChangedSynergyRandomStructureEyes', None)
        if character:
            role = {'forest_treehouse': 'canopy_chief', 'hyena_hideout': 'hyena_chief', 'observatory': 'observatory_chief', 'green_lab': 'lab_researcher'}[name]
            tag['CustomName'] = nbt.String('{"translate":"npc.changed_synergy.' + role + '.title"}')
        mark_default_eyes(tag)
