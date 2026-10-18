import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


spec = importlib.util.spec_from_file_location(
    "validate_release", Path(__file__).resolve().parents[1] / "validate_release.py")
validation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(validation)


class ReleaseFileSelectionTest(unittest.TestCase):
    def test_java_packages_are_checked_and_local_deliveries_are_excluded(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            kept = {"src/main/java/net/example/Mod.java", "src/main/resources/config/default.json",
                    "src/main/resources/assets/example/lang/en_us.json", "tools/tests/Check.java"}
            excluded = {"net/vendor/Decompiled.java", "outputs/version/verification.json",
                        "config/local.json", ".changed-synergy-backups/old/Mod.java",
                        ".mixin.out/net/example/Mixin.class", "build/classes/Mod.class",
                        "build-private/source.json", ".codex-tmp/server/world.json",
                        "tools/__pycache__/temporary.pyc"}
            for name in kept | excluded:
                file = root / name
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_text("{}", encoding="utf-8")
            with patch.object(validation, "ROOT", root):
                actual = {file.relative_to(root).as_posix()
                          for file in validation.iter_publishable_files()}
            self.assertEqual(kept, actual)


if __name__ == "__main__":
    unittest.main()
