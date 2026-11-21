"""Guards git file modes that Windows hides (runs in the python-tools CI job).

A script committed from Windows gets mode 100644 unless it is set explicitly. Linux CI runners
then fail with "Permission denied" (exit 126), as `android/gradlew` did on the first CI run.
"""

import shutil
import subprocess
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
EXECUTABLE = ["android/gradlew"]


def _git_mode(path: str) -> str | None:
    git = shutil.which("git")
    if git is None or not (ROOT / ".git").exists():
        pytest.skip("not a git checkout")
    out = subprocess.run(
        [git, "ls-files", "-s", "--", path], cwd=ROOT, capture_output=True, text=True, check=True
    ).stdout
    return out.split()[0] if out.strip() else None


@pytest.mark.parametrize("path", EXECUTABLE)
def test_script_is_executable_in_git(path: str) -> None:
    assert _git_mode(path) == "100755", (
        f"{path} must be mode 100755; run: git update-index --chmod=+x {path}"
    )
