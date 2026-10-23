"""Record the exact scope of this local Alpha validation without modifying game files."""
from pathlib import Path
import hashlib
import importlib.util
import json
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

WORK = Path(__file__).resolve().parent.parent
ROOT = WORK.parent.parent
OUT = ROOT / ".work/outputs" / "mekanism-26.1.2-20261008"
VERIFY = WORK / "verification"


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")


def main():
    manifest_path = OUT / "installed-mekanism.json"
    record = json.loads(manifest_path.read_text(encoding="utf-8"))
    for artifact in record["installed"]:
        for base in (ROOT / "mods", WORK / "build" / "libs", OUT):
            path = base / artifact["filename"]
            assert digest(path) == artifact["sha256"], str(path)
        with zipfile.ZipFile(ROOT / "mods" / artifact["filename"]) as archive:
            assert archive.testzip() is None
            assert archive.read(artifact["license_path"]) == (WORK / "LICENSE").read_bytes()
    preserved = json.loads((VERIFY / "mods-before-install.json").read_text(encoding="utf-8"))
    for name, expected in preserved.items():
        assert digest(ROOT / "mods" / name) == expected, name
    assert len(preserved) == 49
    assert len(list((ROOT / "mods").glob("*.jar"))) == 53

    unit = {"suites": 0, "tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    for path in (WORK / "build" / "test-results" / "test").glob("TEST-*.xml"):
        suite = ET.parse(path).getroot()
        unit["suites"] += 1
        for name in ("tests", "failures", "errors", "skipped"):
            unit[name] += int(suite.attrib.get(name, "0"))
    assert unit == {"suites": 11, "tests": 71, "failures": 0, "errors": 0, "skipped": 0}, unit
    game_test = (VERIFY / "gametest-final-standalone.log").read_text(encoding="utf-8", errors="replace")
    assert "All 67 required tests passed" in game_test
    assert "BUILD SUCCESSFUL" in game_test
    assert "BUILD SUCCESSFUL" in (VERIFY / "iris-compat-build.log").read_text(encoding="utf-8", errors="replace")

    client = (ROOT / "logs" / "latest.log").read_text(encoding="utf-8", errors="replace")
    assert "Registered compatibility for Iris 1.11.4's three obsolete Mekanism renderer patches" in client
    assert client.count("Reloading ResourceManager:") >= 2
    assert "Stopping server" in client
    assert "Saving chunks for level 'ServerLevel[Mekanism 26.1.2 Verification]'" in client
    assert not re.search(r"\bFATAL\b|MixinApplyError|ReportedException", client)
    for name in ("mekanism", "mekanismgenerators", "mekanismtools", "mekanismadditions"):
        assert "mod/" + name in client
    shutil.copy2(ROOT / "logs" / "latest.log", VERIFY / "client-final.log")
    shutil.copy2(VERIFY / "client-final.log", OUT / "client-validation.log")
    screenshots = []
    for name in ("client-core-items.png", "client-smelter-gui.png", "client-smelt-result.png",
                 "client-resource-reload.png", "client-save-reload.png"):
        path = OUT / name
        assert path.exists() and path.stat().st_size > 1000
        screenshots.append({"filename": name, "sha256": digest(path)})

    spec = importlib.util.spec_from_file_location("project02_base_install", WORK.parent / "optimization-base-20261008" / "stage_and_install.py")
    base_install = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(base_install)
    before = json.loads((VERIFY / "protected-files-before.json").read_text(encoding="utf-8"))
    after = base_install.protected_files()
    temporary_jna = re.compile(r"natives[\\/]jna\d+\.dll(?:\.x)?$")
    jna_jar = next(p for p in (ROOT / "libraries").rglob("jna-*.jar") if re.fullmatch(r"jna-\d[^/]*\.jar", p.name))
    with zipfile.ZipFile(jna_jar) as archive:
        jna_hash = hashlib.sha256(archive.read("com/sun/jna/win32-x86-64/jnidispatch.dll")).hexdigest()
    empty_hash = hashlib.sha256(b"").hexdigest()
    runtime_files = []
    for label, files in (("before", before), ("after", after)):
        for name, value in files.items():
            if temporary_jna.fullmatch(name):
                assert value == (empty_hash if name.endswith(".x") else jna_hash), name
                runtime_files.append({"snapshot": label, "path": name, "sha256": value})
    stable_before = {k: v for k, v in before.items() if not temporary_jna.fullmatch(k)}
    stable_after = {k: v for k, v in after.items() if not temporary_jna.fullmatch(k)}
    assert stable_before == stable_after, "Fixed game/library/asset/native files differ"
    assert len(stable_before) == 4867
    protection = {"unchanged_fixed_files": len(stable_before), "jna_runtime_extractions": runtime_files,
                  "jna_library": str(jna_jar), "jna_native_sha256": jna_hash,
                  "scope": "Minecraft JAR/JSON, GLFW/LWJGL, libraries, assets and fixed natives; random JNA extractions match the original library."}
    write_json(VERIFY / "protected-files-after.json", after)
    write_json(VERIFY / "minecraft-file-verification.json", protection)
    shutil.copy2(VERIFY / "minecraft-file-verification.json", OUT / "minecraft-file-verification.json")

    expected = "4718860a0c98598cf1b34e9f631b24eaa7c7ff47"
    remote = subprocess.check_output(["git", "ls-remote", "origin", "refs/heads/26.1.2"], cwd=WORK, text=True).split()
    assert remote == [expected, "refs/heads/26.1.2"], remote
    record["status"] = "installed-alpha-core-verified"
    record["remote_source"] = {"commit": expected, "local_adaptations_pushed": False}
    record["validation"] = {
        "unit": unit,
        "standalone_gametest": {"required": 67, "passed": 67, "original_mekanism": 62, "new_machine_tests": 4,
                                "neoforge_builtin": 1, "log": "verification/gametest-final-standalone.log"},
        "client": {"root_jars": 53, "loaded_mods_observed_in_menu": 80,
                   "world": "Mekanism 26.1.2 Verification", "creative_items": True,
                   "energized_smelter_gui": True, "energy_tablet_power": True,
                   "iron_ore_to_iron_ingot": True, "resource_reload": True,
                   "items_and_energy_after_save_reload": True, "normal_save_and_quit": True,
                   "evidence_method": "Direct client UI observation and unedited screenshots, supported by log.",
                   "log": "client-validation.log", "screenshots": screenshots},
        "file_integrity": protection,
    }
    record["limitations"] = [
        "The mixed optimization-base and SlashBlade GameTest suite still has failures; it is not considered passed.",
        "Upstream Alpha still disables parts of MekaSuit armor rendering, render pipelines and SPS effects.",
        "Large industrial systems, shader packs, FPS/TPS comparisons and long soak tests have not been validated.",
        "Local adaptation changes are not yet committed or pushed to the remote baseline branch.",
    ]
    write_json(manifest_path, record)
    shutil.copy2(WORK / "docs" / "PROJECT02_PORT.md", OUT / "PROJECT02_PORT.md")
    print(json.dumps({"remote_branch": record["fork_branch"], "unit_tests_passed": unit["tests"],
                      "standalone_gametests_passed": 67, "preserved_mod_jars": len(preserved),
                      "fixed_minecraft_files_unchanged": len(stable_before), "status": record["status"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
