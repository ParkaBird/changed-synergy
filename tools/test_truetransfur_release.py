"""Run the private assertion JAR with a real distributable backend in production Forge.

python tools/test_truetransfur_release.py --backend 2.0.0 --jar <TrueTransfur.jar> --mixin-args <MixinArgs.jar>
Use --backend none, or 1.0.1 with its corresponding JAR, for optional-dependency regressions.
Add --check-command-state for the original /transfur command followed by 420 ticks per player,
with starting-origin selection both disabled and enabled. --addon adds an actual Addon release.
Only the existing private .codex-tmp production server is changed; its mods are restored afterwards.
"""
from pathlib import Path
import argparse
import datetime
import hashlib
import shutil
import subprocess
import time


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--backend', choices=['2.0.0', '1.0.1', 'none'], required=True)
    parser.add_argument('--jar', type=Path)
    parser.add_argument('--mixin-args', type=Path)
    parser.add_argument('--addon', type=Path)
    parser.add_argument('--survival-protocol', type=Path)
    parser.add_argument('--extras', type=Path)
    parser.add_argument('--extra-mod', type=Path, action='append', default=[])
    parser.add_argument('--fixture', type=Path)
    parser.add_argument('--run-directory', type=Path)
    parser.add_argument('--suite', choices=['tt', 'camp', 'social', 'settings', 'community', 'crew-load', 'community-load', 'protection', 'pets', 'negotiation'], default='tt')
    parser.add_argument('--without-addon', action='store_true')
    parser.add_argument('--check-command-state', action='store_true')
    args = parser.parse_args()
    if args.addon and args.without_addon:
        parser.error('--addon and --without-addon cannot be combined')
    repo = Path(__file__).resolve().parent.parent
    run = (args.run_directory or repo / '.codex-tmp' / 'production-community-server').resolve()
    assert run.parent == (repo / '.codex-tmp').resolve(), 'Only an existing private regression server may be changed.'
    mods = run / 'mods'
    version = next(line.split('=', 1)[1].split('+', 1)[0] for line in (repo / 'gradle.properties').read_text(encoding='utf-8').splitlines() if line.startswith('mod_version='))
    fixture = args.fixture or repo / '.codex-tmp' / 'truetransfur-release-build' / 'libs' / f'ChangedSynergy-m1.20.1-v{version}-all.jar'
    java = Path.home() / 'AppData' / 'Roaming' / '.minecraft' / 'runtime' / 'java-runtime-beta' / 'bin' / 'java.exe'
    assert fixture.is_file() and java.is_file() and (run / 'libraries').is_dir()
    additions = [fixture]
    if args.backend != 'none':
        assert args.jar and args.jar.is_file()
        additions.append(args.jar)
    if args.backend == '2.0.0':
        assert args.mixin_args and args.mixin_args.is_file()
        additions.append(args.mixin_args)
    if args.addon:
        assert args.addon.is_file()
        additions.append(args.addon)
    if args.survival_protocol:
        assert args.survival_protocol.is_file()
        additions.append(args.survival_protocol)
    if args.extras:
        assert args.extras.is_file()
        additions.append(args.extras)
    for source in args.extra_mod:
        assert source.is_file()
        additions.append(source)
    stamp = datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f')
    backup = run / '.tt-release-backups' / stamp
    backup.mkdir(parents=True)
    prefixes = ('changedsynergy-', 'truetransfur-', 'mixin_args-', 'changedextras-')
    if args.addon or args.without_addon:
        prefixes += ('changedaddon-',)
    if args.survival_protocol:
        prefixes += ('changed_survive_protocol-',)
    prefixes += tuple(source.name.lower().split('-', 1)[0] + '-' for source in args.extra_mod)
    originals = [p for p in mods.glob('*.jar') if p.name.lower().startswith(prefixes)]
    hashes = {p.name: digest(p) for p in originals}
    config = run / 'config' / 'changed_synergy-common.toml'
    original_config = config.read_bytes()
    (backup / config.name).write_bytes(original_config)
    assert all(p.resolve().parent == mods.resolve() for p in originals)
    log_path = repo / '.codex-tmp' / (args.suite + '-release-' + args.backend + '-' + stamp + '.log')
    staged = []
    process = None
    try:
        for path in originals:
            shutil.move(str(path), str(backup / path.name))
        for source in additions:
            destination = mods / source.name
            assert not destination.exists()
            shutil.copy2(source, destination)
            assert digest(source) == digest(destination)
            staged.append(destination)
        startup = subprocess.STARTUPINFO()
        startup.dwFlags |= subprocess.STARTF_USESHOWWINDOW
        startup.wShowWindow = subprocess.SW_HIDE
        with log_path.open('w', encoding='utf-8') as output:
            properties = ['-Dtruetransfur.command-state=true'] if args.check_command_state else []
            properties += {
                'social': ['-Dchanged_synergy.test.tt=' + {'2.0.0': 'author', '1.0.1': 'old', 'none': 'none'}[args.backend]],
                'crew-load': ['-Dchanged_synergy.crewServerLoad=true'],
                'community-load': ['-Dchanged_synergy.serverLoadCheck=true'],
                'settings': ['-Dsettings.audit.output=' + str(repo / '.codex-tmp' / 'settings-audit')],
            }.get(args.suite, [])
            process = subprocess.Popen([str(java), '-Xmx2G', '-Dfile.encoding=UTF-8', *properties, '@user_jvm_args.txt',
                '@libraries/net/minecraftforge/forge/1.20.1-47.4.21/win_args.txt', 'nogui', '--world',
                str(run / '.regression-worlds' / (args.suite + '-' + args.backend + '-' + stamp))], cwd=run,
                stdin=subprocess.PIPE, stdout=output, stderr=subprocess.STDOUT, startupinfo=startup, text=True)
            print(f'Production {args.suite} regression {args.backend}: hidden PID {process.pid}; {log_path}', flush=True)
            deadline = time.monotonic() + 180
            while time.monotonic() < deadline:
                code = process.poll()
                if code is not None:
                    break
                time.sleep(2)
            else:
                raise RuntimeError('Production assertion server timed out')
        log = log_path.read_text(encoding='utf-8', errors='replace')
        marker = {
            'tt': 'TRUETRANSFUR_RELEASE_REGRESSION_PASS:', 'camp': 'CAMP_WORK_REGRESSION_PASS',
            'social': 'PLAYER_SOCIAL_REGRESSION_PASS:', 'settings': 'SETTINGS_SERVER_REGRESSION_PASS',
            'community': 'COMMUNITY_REGRESSION_PASS', 'crew-load': 'CAMP_CREW_SERVER_LOAD_PASS',
            'community-load': 'SERVER_LOAD_REGRESSION_PASS:', 'protection': 'COMPANION_PROTECTION_REGRESSION_PASS:',
            'pets': 'PET_RELATIONSHIP_REGRESSION_PASS:',
            'negotiation': 'NEGOTIATION_LIFECYCLE_REGRESSION_PASS:',
        }[args.suite]
        assert code == 0 and marker in log and 'REGRESSION_FAIL' not in log and '_SERVER_LOAD_FAIL' not in log, f'Failed; inspect {log_path}'
        if args.suite == 'tt': assert ('backend=' + args.backend) in log
        assert 'All dimensions are saved' in log
        print(next(line for line in log.splitlines() if marker in line), flush=True)
    finally:
        if process and process.poll() is None:
            process.terminate()
            process.wait(timeout=20)
        archived = backup / 'staged-after-check'
        archived.mkdir(exist_ok=True)
        for path in staged:
            if path.exists():
                assert path.resolve().parent == mods.resolve()
                shutil.move(str(path), str(archived / path.name))
        for path in originals:
            source = backup / path.name
            if source.exists():
                assert not path.exists()
                shutil.move(str(source), str(path))
            assert digest(path) == hashes[path.name]
        if config.read_bytes() != original_config:
            config.write_bytes(original_config)
        print('Private production mod set restored with matching hashes.', flush=True)


if __name__ == '__main__':
    main()
