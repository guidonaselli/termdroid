import argparse
import hashlib
from pathlib import Path


EXPECTED_ASSETS = (
    "termdroid-arm64-v8a.apk",
    "termdroid-x86_64.apk",
    "termdroid-universal.apk",
    "termdroid.aab",
)


def verify(directory: Path) -> None:
    missing = [name for name in EXPECTED_ASSETS if not (directory / name).is_file()]
    if missing:
        raise ValueError(f"Faltan artefactos: {', '.join(missing)}")

    empty = [name for name in EXPECTED_ASSETS if (directory / name).stat().st_size == 0]
    if empty:
        raise ValueError(f"Artefactos vacios: {', '.join(empty)}")

    checksum_file = directory / "checksums.txt"
    if not checksum_file.is_file():
        raise ValueError("Falta checksums.txt")

    checksums = {}
    for line in checksum_file.read_text(encoding="utf-8-sig").splitlines():
        digest, name = line.split(maxsplit=1)
        checksums[name.lstrip("* ")] = digest.lower()

    for name in EXPECTED_ASSETS:
        expected = hashlib.sha256((directory / name).read_bytes()).hexdigest()
        if checksums.get(name) != expected:
            raise ValueError(f"Checksum invalido o ausente: {name}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    verify(args.directory)


if __name__ == "__main__":
    main()
