"""Guards the Android package name (DRAFT; runs in the python-tools CI job, no Android SDK needed).

The package was renamed from the placeholder org.example.voicecapture. A mismatch between the Gradle
namespace, the Kotlin `package` lines and the directories only shows up as a crash on the phone,
because the manifest's relative names (".MainActivity") resolve against the namespace.
"""

import re
from pathlib import Path

import pytest

ANDROID = Path(__file__).resolve().parents[2] / "android"
EXPECTED = "org.martindemling.cedarcapture"
PLACEHOLDER = "org.example.voicecapture"


def _kotlin_files() -> list[Path]:
    return sorted(p for p in ANDROID.rglob("*.kt") if "build" not in p.relative_to(ANDROID).parts)


def _package_of(path: Path) -> str:
    match = re.search(r"^package\s+([\w.]+)\s*$", path.read_text(encoding="utf-8"), re.MULTILINE)
    assert match, f"{path} has no package line"
    return match.group(1)


def test_kotlin_sources_exist() -> None:
    assert _kotlin_files(), "no Kotlin sources found; ANDROID path is wrong"


@pytest.mark.parametrize("path", _kotlin_files(), ids=lambda p: p.name)
def test_package_line_uses_the_app_package(path: Path) -> None:
    package = _package_of(path)
    assert package == EXPECTED or package.startswith(EXPECTED + "."), f"{path.name}: {package}"


@pytest.mark.parametrize("path", _kotlin_files(), ids=lambda p: p.name)
def test_directory_matches_package(path: Path) -> None:
    expected_dir = "/".join(_package_of(path).split("."))
    assert path.parent.as_posix().endswith(expected_dir), f"{path} is not under {expected_dir}"


def test_app_namespace_and_application_id() -> None:
    gradle = (ANDROID / "app" / "build.gradle.kts").read_text(encoding="utf-8")
    assert re.search(rf'namespace\s*=\s*"{re.escape(EXPECTED)}"', gradle)
    assert re.search(rf'applicationId\s*=\s*"{re.escape(EXPECTED)}"', gradle)


def test_manifest_classes_exist_in_the_namespace() -> None:
    manifest = (ANDROID / "app" / "src" / "main" / "AndroidManifest.xml").read_text(
        encoding="utf-8"
    )
    for name in re.findall(r'android:name="\.(\w+)"', manifest):
        target = (
            ANDROID / "app" / "src" / "main" / "kotlin" / Path(*EXPECTED.split(".")) / f"{name}.kt"
        )
        assert target.is_file(), f"manifest references .{name} but {target} does not exist"


def test_placeholder_package_is_gone_from_sources() -> None:
    for path in _kotlin_files():
        assert PLACEHOLDER not in path.read_text(encoding="utf-8"), path.name
    assert not list(ANDROID.rglob("org/example")) or all(
        "build" in p.relative_to(ANDROID).parts for p in ANDROID.rglob("org/example")
    )
