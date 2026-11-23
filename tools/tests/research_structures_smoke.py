"""Run the structure fixture in a fresh, loopback-only normal world."""
from pathlib import Path
import os
import shutil
import subprocess
import sys
import uuid

root = Path(__file__).resolve().parents[2]
run = root / '.codex-tmp' / ('research-structures-server-' + uuid.uuid4().hex)
base = root / '.codex-tmp/crew-production-server-no-addon'
profile = Path('D:/Program Files/.minecraft/versions/测试专用/mods')
run.mkdir()
(run / 'mods').mkdir()
quote = lambda path: "'" + str(path).replace("'", "''") + "'"
subprocess.run(['powershell', '-NoProfile', '-Command', 'New-Item -ItemType Junction -Path '
                + quote(run / 'libraries') + ' -Target ' + quote(base / 'libraries') + ' | Out-Null'], check=True)
patterns = ['Changed-m*.jar', 'curios-*.jar', 'geckolib-*.jar', 'TrueTransfur-*.jar', 'mixin_args-*.jar']
if '--without-addon' not in sys.argv:
    patterns.append('ChangedAddon-*.jar')
for pattern in patterns:
    files = list(profile.glob(pattern))
    assert len(files) == 1, (pattern, files)
    shutil.copy2(files[0], run / 'mods' / files[0].name)
version = next(line.split('=', 1)[1].split('+', 1)[0] for line in (root / 'gradle.properties').read_text(encoding='utf-8').splitlines() if line.startswith('mod_version='))
fixture = root / '.codex-tmp/research-structures-build/libs' / f'ChangedSynergy-m1.20.1-v{version}-all.jar'
shutil.copy2(fixture, run / 'mods' / fixture.name)
(run / 'eula.txt').write_text((base / 'eula.txt').read_text(), encoding='utf-8')
(run / 'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-name=research-fixture\n'
        'level-seed=220261025\nlevel-type=minecraft:normal\ngenerate-structures=true\nspawn-protection=0\n'
        'view-distance=2\nsimulation-distance=5\nmax-tick-time=-1\n', encoding='utf-8')
java = Path(os.environ['APPDATA']) / '.minecraft/runtime/java-runtime-beta/bin/java.exe'
startup = subprocess.STARTUPINFO()
startup.dwFlags |= subprocess.STARTF_USESHOWWINDOW
startup.wShowWindow = subprocess.SW_HIDE
print('PRIVATE_RESEARCH_SERVER=' + str(run), flush=True)
with (run / 'stdout.log').open('w', encoding='utf-8') as log:
    process = subprocess.Popen([str(java), '-Xmx3G', '-Dchanged_synergy.greenLabOnly=' + str('--green-only' in sys.argv).lower(),
                                '@libraries/net/minecraftforge/forge/1.20.1-47.4.21/win_args.txt', 'nogui'],
                               cwd=run, stdout=log, stderr=subprocess.STDOUT, startupinfo=startup)
    try:
        code = process.wait(timeout=300)
    finally:
        if process.poll() is None:
            process.terminate()
            process.wait(timeout=15)
text = (run / 'stdout.log').read_text(encoding='utf-8', errors='replace')
if code or 'RESEARCH_STRUCTURES_REGRESSION_PASS:' not in text or 'RESEARCH_STRUCTURES_REGRESSION_FAIL' in text:
    print(text[-10000:])
    raise SystemExit(1)
for line in text.splitlines():
    if any(marker in line for marker in ('RESEARCH_STRUCTURES_', 'RESEARCH_PLACED', 'RESEARCH_NATURAL_START', 'RESEARCH_PALETTE_VALID')):
        print(line)
