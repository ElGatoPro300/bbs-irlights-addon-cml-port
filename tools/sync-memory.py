#!/usr/bin/env python3
"""
sync-memory.py - Mirror the Claude memory folder into the repo so git tracks it.

Replaces <repo>/memory with an exact copy of the external Claude memory store.
"Mirror" means the destination is wiped first, so files deleted upstream also
disappear here (no stale leftovers).

Default source:
  C:\\Users\\Qualet\\.claude\\projects\\
  C--Users-Qualet-Documents-Project-Minecraft-BBS-irlights\\memory
Default dest:
  <repo>/memory   (repo root inferred from this script: tools/..)

Usage:
  python tools/sync-memory.py            # do the mirror
  python tools/sync-memory.py --dry-run  # show what would happen, write nothing
  python tools/sync-memory.py --source <path> --dest <path>
"""
from __future__ import annotations

import argparse
import os
import shutil
import stat
import sys
from pathlib import Path

DEFAULT_SOURCE = Path(
    r"C:\Users\Qualet\.claude\projects"
    r"\C--Users-Qualet-Documents-Project-Minecraft-BBS-irlights\memory"
)
REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_DEST = REPO_ROOT / "memory"


def _force_rm(func, path, _exc):
    # Clear the read-only bit and retry; Windows rmtree trips on read-only files.
    os.chmod(path, stat.S_IWRITE)
    func(path)


def _rmtree(path: Path) -> None:
    # onexc (Python 3.12+) superseded onerror; fall back for older interpreters.
    try:
        shutil.rmtree(path, onexc=_force_rm)
    except TypeError:
        shutil.rmtree(path, onerror=_force_rm)


def _count_files(root: Path) -> int:
    return sum(1 for p in root.rglob("*") if p.is_file())


def main() -> int:
    ap = argparse.ArgumentParser(
        description="Mirror the Claude memory folder into the repo so git tracks it."
    )
    ap.add_argument("--source", type=Path, default=DEFAULT_SOURCE, help="Source memory dir.")
    ap.add_argument("--dest", type=Path, default=DEFAULT_DEST, help="Destination dir in the repo.")
    ap.add_argument("--dry-run", action="store_true", help="Report only; write nothing.")
    args = ap.parse_args()

    source: Path = args.source
    dest: Path = args.dest

    if not source.is_dir():
        print(f"ERROR: source not found or not a directory: {source}", file=sys.stderr)
        return 1

    source_r = source.resolve()
    dest_r = dest.resolve()

    # Safety rails: never wipe the source, a parent of it, or a drive root.
    if dest_r == source_r:
        print("ERROR: dest equals source; refusing.", file=sys.stderr)
        return 1
    if dest_r == dest_r.anchor or dest_r in source_r.parents:
        print(f"ERROR: unsafe dest, refusing: {dest_r}", file=sys.stderr)
        return 1

    existed = dest_r.exists()
    n_src = _count_files(source_r)
    n_old = _count_files(dest_r) if existed else 0

    print(f"source : {source_r}  ({n_src} files)")
    print(f"dest   : {dest_r}  ({f'replacing {n_old} files' if existed else 'new'})")

    if args.dry_run:
        print("dry-run: nothing written.")
        return 0

    if existed:
        _rmtree(dest_r)
    shutil.copytree(source_r, dest_r)

    print(f"done   : mirrored {_count_files(dest_r)} files")
    try:
        rel = dest_r.relative_to(REPO_ROOT)
    except ValueError:
        rel = dest_r
    print(f"next   : git add {rel} && git commit")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
