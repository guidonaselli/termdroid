import hashlib
import tempfile
import unittest
from pathlib import Path

from scripts.verify_release_assets import EXPECTED_ASSETS, verify


class VerifyReleaseAssetsTest(unittest.TestCase):
    def test_accepts_complete_assets(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            lines = []
            for name in EXPECTED_ASSETS:
                content = name.encode()
                (root / name).write_bytes(content)
                lines.append(f"{hashlib.sha256(content).hexdigest()}  {name}")
            (root / "checksums.txt").write_text("\n".join(lines), encoding="utf-8")

            verify(root)

    def test_rejects_missing_asset(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "checksums.txt").write_text("", encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "Faltan artefactos"):
                verify(root)


if __name__ == "__main__":
    unittest.main()
