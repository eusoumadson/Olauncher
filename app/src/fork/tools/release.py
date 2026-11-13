#!/usr/bin/env python3
"""CalVer release helper for Abacate Launcher."""

from __future__ import annotations

import argparse
import datetime as dt
import re
import subprocess
import sys
from pathlib import Path
from typing import Dict, Optional, Tuple


DEFAULT_ROOT = Path(__file__).resolve().parents[4]


def parse_version_properties(path: Path) -> Tuple[int, str]:
    """Read VERSION_CODE and VERSION_NAME from version.properties."""
    if not path.exists():
        raise FileNotFoundError(f"version.properties not found at {path}")

    values: Dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise ValueError(f"Invalid line in version.properties: {raw_line}")
        key, val = line.split("=", 1)
        values[key.strip()] = val.strip()

    if "VERSION_CODE" not in values or "VERSION_NAME" not in values:
        raise ValueError("version.properties must define VERSION_CODE and VERSION_NAME")

    return int(values["VERSION_CODE"]), values["VERSION_NAME"]


def write_version_properties(path: Path, version_code: int, version_name: str) -> None:
    """Persist VERSION_CODE and VERSION_NAME to version.properties."""
    content = f"VERSION_CODE={version_code}\nVERSION_NAME={version_name}\n"
    path.write_text(content, encoding="utf-8")


def compute_next_version(
    current_code: int,
    current_name: str,
    target_date: Optional[dt.date] = None,
) -> Tuple[int, str, str]:
    """Compute next (version_code, version_name, tag_name) using CalVer YYYY.MM.DD."""
    if current_code < 1:
        raise ValueError(f"VERSION_CODE must be positive, got {current_code}")
    if not current_name.strip():
        raise ValueError("VERSION_NAME must not be empty")

    release_date = target_date or dt.date.today()
    next_code = current_code + 1
    next_name = release_date.strftime("%Y.%m.%d")
    tag_name = f"v{next_name}"
    return next_code, next_name, tag_name


def extract_changelog_notes(changelog_path: Path, version_name: str) -> Optional[str]:
    """Extract markdown section body for the given version_name from CHANGELOG.md."""
    if not changelog_path.exists():
        return None

    lines = changelog_path.read_text(encoding="utf-8").splitlines()
    normalized = version_name.lstrip("v")
    header_pattern = re.compile(
        rf"^##\s+\[?v?{re.escape(normalized)}\]?(?:\s+-\s+.*|\s*)$"
    )

    collecting = False
    collected: list[str] = []
    for line in lines:
        if line.startswith("## "):
            if collecting:
                break
            if header_pattern.match(line.strip()):
                collecting = True
                continue
        elif collecting:
            collected.append(line)

    if not collecting:
        return None

    body = "\n".join(collected).strip()
    return body if body else None


def run_git(repo_root: Path, *args: str) -> subprocess.CompletedProcess[str]:
    """Run a git command inside repo_root."""
    return subprocess.run(
        ["git", *args],
        cwd=str(repo_root),
        check=True,
        text=True,
        capture_output=True,
    )


def perform_release(
    repo_root: Path,
    dry_run: bool = False,
    target_date: Optional[dt.date] = None,
) -> int:
    """Execute or simulate a CalVer release bump, commit, and tag."""
    version_file = repo_root / "version.properties"
    changelog_file = repo_root / "CHANGELOG.md"

    current_code, current_name = parse_version_properties(version_file)
    next_code, next_name, tag_name = compute_next_version(
        current_code=current_code,
        current_name=current_name,
        target_date=target_date,
    )
    notes = extract_changelog_notes(changelog_file, next_name)
    if notes is None and next_name != current_name:
        notes = extract_changelog_notes(changelog_file, current_name)

    commit_message = f"chore(release): bump version to {tag_name}"

    print(f"Current version: {current_name} (code {current_code})")
    print(f"Next version:    {next_name} (code {next_code})")
    print(f"Tag:             {tag_name}")
    print(f"Commit message:  {commit_message}")
    if notes:
        print("Release notes:")
        print(notes)
    else:
        print("Release notes:   (none found in CHANGELOG.md)")

    if dry_run:
        print("[dry-run] No files or git refs were modified.")
        return 0

    existing_tags = run_git(repo_root, "tag", "--list", tag_name).stdout.strip()
    if existing_tags:
        print(f"Error: git tag {tag_name} already exists.", file=sys.stderr)
        return 1

    write_version_properties(version_file, next_code, next_name)
    run_git(repo_root, "add", "version.properties")
    run_git(repo_root, "commit", "-m", commit_message)
    if notes:
        run_git(repo_root, "tag", "-a", tag_name, "-m", f"{tag_name}\n\n{notes}")
    else:
        run_git(repo_root, "tag", "-a", tag_name, "-m", tag_name)

    print(f"Release {tag_name} committed and tagged successfully.")
    return 0


def parse_args(argv: Optional[list[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Abacate Launcher CalVer release script")
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Preview version bump, changelog notes, commit, and tag without modifying git or files.",
    )
    parser.add_argument(
        "--date",
        help="Override CalVer date in YYYY.MM.DD format.",
    )
    parser.add_argument(
        "--root",
        type=Path,
        default=DEFAULT_ROOT,
        help="Repository root directory.",
    )
    return parser.parse_args(argv)


def main(argv: Optional[list[str]] = None) -> int:
    args = parse_args(argv)
    target_date: Optional[dt.date] = None
    if args.date:
        target_date = dt.datetime.strptime(args.date, "%Y.%m.%d").date()
    return perform_release(repo_root=args.root, dry_run=args.dry_run, target_date=target_date)


if __name__ == "__main__":
    raise SystemExit(main())
