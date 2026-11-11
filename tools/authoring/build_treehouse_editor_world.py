"""Create one flat treehouse workshop using the authoring-only server JAR, then install its save."""
from pathlib import Path
import hashlib
import json
import os
import shutil
import subprocess
import sys
import uuid

import nbtlib as nbt

ROOT = Path(__file__).resolve().parents[2]
PROFILE = Path('D:/Program Files/.minecraft/versions/测试专用')
BASE = ROOT/'.codex-tmp/crew-production-server-no-addon'
JAVA = Path('C:/Users/ZhuanZ（无密码）/AppData/Roaming/.minecraft/runtime/java-runtime-beta/bin/java.exe')


def main():
    jar = Path(sys.argv[1]).resolve()
    assert jar.is_file() and jar.is_relative_to(ROOT/'.codex-tmp')
    run = ROOT/'.codex-tmp'/('treehouse-editor-server-'+uuid.uuid4().hex)
    run.mkdir(); (run/'mods').mkdir()
    quote = lambda path: "'"+str(path).replace("'", "''")+"'"
    subprocess.run(['powershell','-NoProfile','-Command','New-Item -ItemType Junction -Path '+quote(run/'libraries')+' -Target '+quote(BASE/'libraries')+' | Out-Null'], check=True, capture_output=True)
    for pattern in ['Changed-m*.jar','curios-*.jar','geckolib-*.jar','ChangedAddon-*.jar']:
        matches = list((PROFILE/'mods').glob(pattern)); assert len(matches)==1, (pattern,matches)
        os.link(matches[0], run/'mods'/matches[0].name)
    os.link(jar, run/'mods'/jar.name)
    (run/'eula.txt').write_text((BASE/'eula.txt').read_text(encoding='utf-8'), encoding='utf-8')
    flat = {'layers':[{'block':'minecraft:bedrock','height':1},{'block':'minecraft:dirt','height':126}, {'block':'minecraft:grass_block','height':1}], 'biome':'minecraft:forest', 'features':False, 'lakes':False, 'structure_overrides':[]}
    (run/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-name=treehouse-editor\nlevel-seed=20261102\nlevel-type=minecraft:flat\ngenerate-structures=false\nspawn-protection=0\nview-distance=3\nsimulation-distance=3\nmax-tick-time=-1\ngamemode=creative\nforce-gamemode=true\nallow-flight=true\ndifficulty=normal\nsync-chunk-writes=true\ngenerator-settings='+json.dumps(flat,separators=(',',':'))+'\n', encoding='utf-8')
    si = subprocess.STARTUPINFO(); si.dwFlags |= subprocess.STARTF_USESHOWWINDOW; si.wShowWindow = subprocess.SW_HIDE
    print('TREEHOUSE_EDITOR_RUN='+str(run), flush=True)
    with (run/'stdout.log').open('w',encoding='utf-8') as log:
        proc = subprocess.Popen([str(JAVA),'-Xmx2G','-Dfile.encoding=UTF-8','-Dchanged_synergy.buildEditorWorld=true','-Dchanged_synergy.treehouseEditor=true','@libraries/net/minecraftforge/forge/1.20.1-47.4.21/win_args.txt','nogui'],cwd=run,stdout=log,stderr=subprocess.STDOUT,startupinfo=si)
        try:
            code = proc.wait(timeout=300)
        finally:
            if proc.poll() is None:
                proc.terminate(); proc.wait(timeout=15)
    log = (run/'stdout.log').read_text(encoding='utf-8',errors='replace')
    assert code==0 and 'STRUCTURE_EDITOR_WORLD_PASS: structures=1;' in log and 'STRUCTURE_EDITOR_WORLD_FAIL' not in log, str(run/'stdout.log')
    for line in log.splitlines():
        if 'EDITOR_SITE_READY' in line or 'STRUCTURE_EDITOR_WORLD_PASS:' in line: print(line,flush=True)
    world = run/'treehouse-editor'
    data = nbt.load(world/'level.dat'); tag = data['Data']
    assert str(tag['WorldGenSettings']['dimensions']['minecraft:overworld']['generator']['type'])=='minecraft:flat'
    tag['LevelName']=nbt.String('Synergy 树屋编辑地图'); tag['GameType']=nbt.Int(1); tag['allowCommands']=nbt.Byte(1)
    tag['SpawnX']=nbt.Int(-12); tag['SpawnY']=nbt.Int(64); tag['SpawnZ']=nbt.Int(-12); tag['SpawnAngle']=nbt.Float(-45)
    assert str(tag['GameRules']['doMobSpawning'])=='false' and str(tag['GameRules']['doDaylightCycle'])=='false'
    data.save()
    source = ROOT/'src/main/resources/data/changed_synergy/structures/forest_treehouse.nbt'
    original = world/'synergy_editing/source-templates/forest_treehouse.nbt'
    assert nbt.load(source).unpack(json=True)==nbt.load(original).unpack(json=True), 'Workshop must contain the current template'
    manifest = {'structure':'changed_synergy:forest_treehouse','origin':[0,63,0],'size':[27,41,24],'ground_y':63,'teleport':'/tp @s 13 64 -6','source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'notes':['NPC original flags are saved under SynergyEditorOriginalFlags; restore them during import.','Editable written books retain their original item data under SynergyEditorOriginal.','Keep source loot tables for containers not intentionally edited.','Yellow perimeter, sign and spawn supply chest are authoring aids outside the capture box.']}
    (world/'synergy_editing/manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    guide = '# 树屋编辑地图\n\n创造模式，允许指令。当前树屋的原点为 `0 63 0`，大小为 `27×41×24`；前往入口可使用 `/tp @s 13 64 -6`。\n\n方块可直接修改。讲台上的原书本身已可编辑，完整正文保持不变，取出改字后放回即可。NPC 静止、无重力且无敌；刷怪、随机刻和火焰蔓延已关闭。\n\n黄色边线和出生点附近的箱子、说明牌是辅助设施。编辑完成后保存并退出，再把存档路径告诉我；不需要操作 NBT。若扩建到黄色边线外，请说明扩建范围。请保留 `synergy_editing` 文件夹，供后续提取与恢复 NPC 标记、书籍署名、战利品表使用。\n'
    (world/'编辑说明.md').write_text(guide,encoding='utf-8')
    save_base = (PROFILE/'saves').resolve(); destination = save_base/'Synergy-树屋编辑-20261102'
    assert world.resolve().is_relative_to((ROOT/'.codex-tmp').resolve()) and destination.parent.resolve()==save_base
    assert not destination.exists(), 'Do not overwrite an existing edited world: '+str(destination)
    shutil.move(str(world), str(destination))
    output = ROOT.parent/'outputs/treehouse-editor-20261102'; output.mkdir(exist_ok=True)
    (output/'编辑说明.md').write_text(guide+'\n存档路径：`'+str(destination)+'`\n',encoding='utf-8')
    shutil.copy2(run/'stdout.log', output/'generation.log')
    (output/'manifest.json').write_text(json.dumps(manifest|{'installed_world':str(destination)},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    for copied in (run/'mods').glob('*.jar'): copied.unlink()
    print('INSTALLED_TREEHOUSE_EDITOR='+str(destination),flush=True)


if __name__=='__main__':
    main()
