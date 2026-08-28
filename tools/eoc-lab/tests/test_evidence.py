import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

from eoc_lab.evidence import EvidenceStore, repository_state
from eoc_lab.process import CommandRunner


class EvidenceStoreTest(unittest.TestCase):
    def test_writes_complete_run_atomically_and_checksums_contents(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            store = EvidenceStore(root)
            run_directory = store.write_run(
                "run-1",
                {"z": 1, "a": 2},
                {"status": "PASS", "passed": True, "runId": "run-1"},
            )

            manifest = json.loads((run_directory / "manifest.json").read_text(encoding="utf-8"))
            result = json.loads((run_directory / "result.json").read_text(encoding="utf-8"))
            self.assertEqual({"a": 2, "z": 1}, manifest)
            self.assertTrue(result["passed"])

            checksum_lines = (run_directory / "checksums.sha256").read_text(encoding="utf-8").splitlines()
            expected = {}
            for filename in ("manifest.json", "result.json"):
                expected[filename] = hashlib.sha256((run_directory / filename).read_bytes()).hexdigest()

            self.assertEqual(
                sorted(f"{digest}  {filename}" for filename, digest in expected.items()),
                sorted(checksum_lines),
            )
            self.assertEqual([], list(root.glob(".*.partial-*")))

    def test_refuses_to_overwrite_existing_run(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            store = EvidenceStore(Path(temporary_directory))
            store.write_run("run-1", {}, {})
            with self.assertRaises(FileExistsError):
                store.write_run("run-1", {}, {})

    def test_failed_finalization_leaves_no_visible_or_partial_run(self) -> None:
        class FailingStore(EvidenceStore):
            def __init__(self, output_root: Path) -> None:
                super().__init__(output_root)
                self.calls = 0

            def _atomic_json_write(self, path, value):
                self.calls += 1
                if self.calls == 2:
                    raise OSError("simulated write failure")
                return EvidenceStore._atomic_json_write(path, value)

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            store = FailingStore(root)
            with self.assertRaisesRegex(OSError, "simulated"):
                store.write_run("run-1", {}, {})

            self.assertFalse((root / "run-1").exists())
            self.assertEqual([], list(root.glob(".*.partial-*")))

    def test_rejects_path_like_run_id(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            store = EvidenceStore(Path(temporary_directory))
            with self.assertRaises(ValueError):
                store.write_run("../escape", {}, {})


class RepositoryStateTest(unittest.TestCase):
    def test_fingerprints_tracked_and_untracked_worktree_changes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            _git(root, "init")
            _git(root, "config", "user.email", "eoc-lab@example.invalid")
            _git(root, "config", "user.name", "EOC Lab Test")
            tracked = root / "tracked.txt"
            tracked.write_text("baseline\n", encoding="utf-8")
            _git(root, "add", "tracked.txt")
            _git(root, "commit", "-m", "baseline")

            clean = repository_state(root, CommandRunner())
            self.assertFalse(clean.dirty)
            self.assertEqual((), clean.untracked_files)
            self.assertIsNone(clean.untracked_content_sha256)

            tracked.write_text("changed\n", encoding="utf-8")
            (root / "new-source.txt").write_text("untracked content\n", encoding="utf-8")
            dirty = repository_state(root, CommandRunner())

            self.assertTrue(dirty.dirty)
            self.assertEqual(("new-source.txt",), dirty.untracked_files)
            self.assertIsNotNone(dirty.untracked_content_sha256)
            self.assertNotEqual(hashlib.sha256(b"").hexdigest(), dirty.tracked_diff_sha256)


def _git(root: Path, *arguments: str) -> None:
    subprocess.run(["git", "-C", str(root), *arguments], check=True, capture_output=True, text=True)


if __name__ == "__main__":
    unittest.main()
