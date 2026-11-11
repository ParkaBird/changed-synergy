from pathlib import Path
import gzip, hashlib, json, nbtlib

ROOT = Path(__file__).resolve().parents[2]

def test_observatory_quartz_patch():
    nbt_path = ROOT / 'src/main/resources/data/changed_synergy/structures/observatory.nbt'
    manifest_path = ROOT / 'tools/observatory_manifest.json'

    # 1. SHA256 match
    nbt_bytes = nbt_path.read_bytes()
    manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
    assert manifest['output_sha256'] == hashlib.sha256(nbt_bytes).hexdigest()

    # 2. Block [3, 15, 13] is smooth quartz
    template = nbtlib.load(nbt_path)
    palette = template['palette']
    found = False
    for b in template['blocks']:
        if list(map(int, b['pos'])) == [3, 15, 13]:
            name = str(palette[int(b['state'])]['Name'])
            assert name == 'minecraft:smooth_quartz', f'Expected smooth_quartz, got {name}'
            found = True
            break
    assert found, 'Block [3, 15, 13] not found'

def test_chief_peace_wiring():
    camp_src = (ROOT / 'src/main/java/net/parkabird/changedsynergy/npc/CampMembershipService.java').read_text(encoding='utf-8')
    assert 'CHIEF_PEACE_RADIUS_SQR = 100.0D' in camp_src
    assert 'CHIEF_PEACE_DURATION = 300L' in camp_src
    assert 'hasChiefPeace' in camp_src
    assert 'refreshChiefPeace' in camp_src
    assert 'breakChiefPeace' in camp_src

    social_src = (ROOT / 'src/main/java/net/parkabird/changedsynergy/ai/LatexSocialMemory.java').read_text(encoding='utf-8')
    assert 'CampMembershipService.hasChiefPeace' in social_src
    assert 'CampMembershipService.breakChiefPeace' in social_src

    disp_src = (ROOT / 'src/main/java/net/parkabird/changedsynergy/event/NpcDispositionEvents.java').read_text(encoding='utf-8')
    assert 'CampMembershipService.refreshChiefPeace' in disp_src

    rep_src = (ROOT / 'src/main/java/net/parkabird/changedsynergy/event/FactionReputationEvents.java').read_text(encoding='utf-8')
    assert 'CampMembershipService.breakChiefPeace' in rep_src

    camp_goal_src = (ROOT / 'src/main/java/net/parkabird/changedsynergy/ai/NaturalCampGoal.java').read_text(encoding='utf-8')
    assert 'PlayerOutpostWorkGoal.huntable(a)' in camp_goal_src
    assert 'TamableAnimal t && t.isTame()' not in camp_goal_src

def test_story_npc_dialogue_silencing():
    npc_dialogue_src = (ROOT / 'src/main/java/net/parkabird/changedsynergy/dialogue/NpcDialogue.java').read_text(encoding='utf-8')
    # 1. Trigger rejects Story NPCs after showEmote
    assert '|| net.parkabird.changedsynergy.npc.StoryNpcService.isNpc(speaker))' in npc_dialogue_src
    # 2. Context allows only chief.* for chiefs, silencing others and all gas wolf lines
    assert 'if (net.parkabird.changedsynergy.npc.StoryNpcService.isNpc(speaker)' in npc_dialogue_src
    assert '(!suffix.startsWith("chief.") || !net.parkabird.changedsynergy.npc.CampDialogue.chief(speaker))' in npc_dialogue_src

def test_bonded_seating_wiring():
    events_src = (ROOT / 'src/main/java/net/parkabird/changedsynergy/event/LatexSocialEvents.java').read_text(encoding='utf-8')
    assert 'CompanionSeatingService.action(pet, player)' in events_src

    menu_src = (ROOT / 'src/main/java/net/parkabird/changedsynergy/world/inventory/BondedLatexMenu.java').read_text(encoding='utf-8')
    assert 'getSeatAction()' in menu_src
    assert 'CompanionSeatingService.command(origin, pet, command)' in menu_src
    assert 'CompanionSeatingService.newCommand(pet, origin)' in menu_src

    screen_src = (ROOT / 'src/main/java/net/parkabird/changedsynergy/client/BondedLatexScreen.java').read_text(encoding='utf-8')
    assert 'CompanionSeatIcons.id(isSeat)' in screen_src
    assert 'CompanionSeatIcons.resolve(icon)' in screen_src

def test_orange_tree_worldgen_placement():
    placed_dir = ROOT / 'src/main/resources/data/changed_synergy/worldgen/placed_feature'
    tree_files = list(placed_dir.glob('orange_tree_*.json'))
    assert len(tree_files) == 8, f'Expected 8 orange tree files, found {len(tree_files)}'
    for f in tree_files:
        data = json.loads(f.read_text(encoding='utf-8'))
        placement = data['placement']
        types = [p['type'] for p in placement]
        assert 'minecraft:surface_water_depth_filter' in types, f'Missing water depth filter in {f.name}'
        assert 'minecraft:heightmap' in types, f'Missing heightmap in {f.name}'
        assert 'minecraft:block_predicate_filter' in types, f'Missing block predicate in {f.name}'

        water_filter = next(p for p in placement if p['type'] == 'minecraft:surface_water_depth_filter')
        assert water_filter['max_water_depth'] == 0, f'Water depth not 0 in {f.name}'

        heightmap = next(p for p in placement if p['type'] == 'minecraft:heightmap')
        assert heightmap['heightmap'] == 'OCEAN_FLOOR', f'Heightmap not OCEAN_FLOOR in {f.name}'

        pred_filter = next(p for p in placement if p['type'] == 'minecraft:block_predicate_filter')
        assert pred_filter['predicate']['type'] == 'minecraft:would_survive', f'Predicate not would_survive in {f.name}'
        assert pred_filter['predicate']['state']['Name'] == 'minecraft:oak_sapling', f'State not oak_sapling in {f.name}'

def test_natural_camp_storage_touch():
    goal_src = (ROOT / 'src/main/java/net/parkabird/changedsynergy/ai/NaturalCampGoal.java').read_text(encoding='utf-8')
    assert 'PlayerOutpostStorageAccess.touch(mob,target);' in goal_src or 'PlayerOutpostStorageAccess.touch(mob, target);' in goal_src

def test_desert_refuge_deployment():
    # 1. NBT structure checks
    nbt_path = ROOT / 'src/main/resources/data/changed_synergy/structures/desert_refuge.nbt'
    assert nbt_path.is_file(), 'desert_refuge.nbt does not exist'
    nbt = nbtlib.load(nbt_path)
    assert list(map(int, nbt['size'])) == [36, 37, 39]
    entities = nbt['entities']
    residents = [e for e in entities if e.get('nbt', {}).get('ForgeData', {}).get('ChangedSynergyStructureResident')]
    assert len(residents) == 6, f'Expected five residents and a Chief, found {len(residents)}'
    for r in residents:
        r_nbt = r['nbt']
        assert r_nbt['id'] in ('changed:latex_fennec_fox', 'changed:latex_snake', 'changed:green_lizard','changed_synergy:latex_jerboa')
        fd = r_nbt['ForgeData']
        assert fd['ChangedSynergyLightReputationGroup'] == 'desert'
        csr = fd['ChangedSynergyStructureResident']
        assert csr['Template'] == 'desert_refuge'
        assert csr['Camp'] == 1
        assert csr['Character'] == (1 if r_nbt['id']=='changed_synergy:latex_jerboa' else 0)

    # 2. Worldgen structure JSON
    struct_path = ROOT / 'src/main/resources/data/changed_synergy/worldgen/structure/desert_refuge.json'
    assert struct_path.is_file()
    struct_data = json.loads(struct_path.read_text(encoding='utf-8'))
    assert struct_data['template'] == 'changed_synergy:desert_refuge'
    assert struct_data['ground_level'] == 25
    assert struct_data['max_terrain_slope'] == 5
    assert struct_data['biomes'] == '#changed_synergy:has_structure/desert_refuges'

    # 3. Structure set JSON
    set_path = ROOT / 'src/main/resources/data/changed_synergy/worldgen/structure_set/desert_refuges.json'
    assert set_path.is_file()
    set_data = json.loads(set_path.read_text(encoding='utf-8'))
    assert set_data['structures'][0]['structure'] == 'changed_synergy:desert_refuge'

    # 4. Biome tag JSON
    tag_path = ROOT / 'src/main/resources/data/changed_synergy/tags/worldgen/biome/has_structure/desert_refuges.json'
    assert tag_path.is_file()
    tag_data = json.loads(tag_path.read_text(encoding='utf-8'))
    assert 'minecraft:desert' in tag_data['values']

    # 5. CampLifeService integration
    service_src = (ROOT / 'src/main/java/net/parkabird/changedsynergy/npc/CampLifeService.java').read_text(encoding='utf-8')
    assert '"desert_refuge"' in service_src

def main():
    test_observatory_quartz_patch()
    test_chief_peace_wiring()
    test_story_npc_dialogue_silencing()
    test_bonded_seating_wiring()
    test_orange_tree_worldgen_placement()
    test_natural_camp_storage_touch()
    test_desert_refuge_deployment()
    print('CHIEF_PEACE_AND_OBSERVATORY_QUARTZ_PASS: block restored, manifest synced, 10-block aura, 15s grace, bonded seating, orange tree ocean floor, resident chest touch, and desert refuge deployment verified')

if __name__ == '__main__':
    main()
