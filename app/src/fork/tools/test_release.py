import datetime as dt
import io
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parent
if str(TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(TOOLS_DIR))

import release  # noqa: E402


class ReleaseScriptTest(unittest.TestCase):
    def test_parse_version_properties_reads_code_and_name(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            props = Path(tmp) / "version.properties"
            props.write_text("VERSION_CODE=116\nVERSION_NAME=2026.11.12\n", encoding="utf-8")

            code, name = release.parse_version_properties(props)

            self.assertEqual(code, 116)
            self.assertEqual(name, "2026.11.12")

    def test_parse_version_properties_raises_on_missing_keys(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            props = Path(tmp) / "version.properties"
            props.write_text("VERSION_CODE=116\n", encoding="utf-8")

            with self.assertRaises(ValueError):
                release.parse_version_properties(props)

    def test_compute_next_version_bumps_code_and_formats_calver_tag(self) -> None:
        next_code, next_name, tag_name = release.compute_next_version(
            current_code=116,
            current_name="2026.11.12",
            target_date=dt.date(2026, 12, 1),
        )

        self.assertEqual(next_code, 117)
        self.assertEqual(next_name, "2026.12.01")
        self.assertEqual(tag_name, "v2026.12.01")

    def test_extract_changelog_notes_reads_matching_section(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            changelog = Path(tmp) / "CHANGELOG.md"
            changelog.write_text(
                "# Changelog\n\n"
                "## [2026.11.12] - 2026-11-12\n"
                "- Initial fork release\n"
                "- CalVer setup\n\n"
                "## [2026.10.01] - 2026-10-01\n"
                "- Earlier entry\n",
                encoding="utf-8",
            )

            notes = release.extract_changelog_notes(changelog, "2026.11.12")

            self.assertEqual(notes, "- Initial fork release\n- CalVer setup")

    def test_dry_run_does_not_modify_files_and_returns_zero(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            props = root / "version.properties"
            original_props = "VERSION_CODE=116\nVERSION_NAME=2026.11.12\n"
            props.write_text(original_props, encoding="utf-8")
            (root / "CHANGELOG.md").write_text(
                "## 2026.11.12\n- Fork foundation\n",
                encoding="utf-8",
            )

            stdout_buffer = io.StringIO()
            with redirect_stdout(stdout_buffer):
                exit_code = release.main(
                    ["--root", str(root), "--date", "2026.11.12", "--dry-run"]
                )

            output = stdout_buffer.getvalue()
            self.assertEqual(exit_code, 0)
            self.assertEqual(props.read_text(encoding="utf-8"), original_props)
            self.assertIn("chore(release): bump version to v2026.11.12", output)
            self.assertIn("- Fork foundation", output)
            self.assertIn("[dry-run]", output)


if __name__ == "__main__":
    unittest.main()
