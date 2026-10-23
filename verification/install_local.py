"""Install only this workspace's four runtime JARs after project02 has exited."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess
import tomllib
import zipfile

WORK = Path(__file__).resolve().parent.parent
ROOT = WORK.parent.parent
OUT = ROOT / ".work/outputs" / "mekanism-26.1.2-20261008"


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def main():
    command = "Get-CimInstance Win32_Process -Filter \"name = 'javaw.exe'\" | Where-Object { $_.CommandLine -like '*--gameDir*project02*' } | Select-Object -ExpandProperty ProcessId"
    running = subprocess.check_output(["powershell", "-NoProfile", "-Command", command], text=True).strip()
    if running:
        raise RuntimeError("project02 must exit normally before installing mods: " + running)
    OUT.mkdir(exist_ok=True, parents=True)
    before = {p.name: digest(p) for p in sorted((ROOT / "mods").glob("*.jar"))}
    snapshot = WORK / "verification" / "mods-before-install.json"
    if not snapshot.exists():
        snapshot.write_text(json.dumps(before, indent=2), encoding="utf-8")
    else:
        for name, expected in json.loads(snapshot.read_text(encoding="utf-8")).items():
            assert before.get(name) == expected, name
    artifacts = sorted((WORK / "build" / "libs").glob("*.homebaked.jar"))
    assert len(artifacts) == 4, artifacts
    previous_path = OUT / "installed-mekanism.json"
    previous = json.loads(previous_path.read_text(encoding="utf-8")) if previous_path.exists() else {}
    owned = {r["filename"]: r["sha256"] for r in previous.get("installed", [])}
    license_bytes = (WORK / "LICENSE").read_bytes()
    installed = []
    for artifact in artifacts:
        with zipfile.ZipFile(artifact) as archive:
            assert archive.testzip() is None
            assert archive.read("META-INF/licenses/mekanism/LICENSE") == license_bytes
            metadata = tomllib.loads(archive.read("META-INF/neoforge.mods.toml").decode())
            mod = metadata["mods"][0]
            assert mod["version"] == "10.8.0-project02.1"
            requirements = {d["modId"]: d for d in metadata["dependencies"][mod["modId"]]}
            if mod["modId"] == "mekanism":
                assert requirements["minecraft"]["versionRange"] == "[26.1.2,26.2)"
                assert requirements["neoforge"]["versionRange"] == "[26.1.2.74,)"
            else:
                assert requirements["mekanism"]["versionRange"] == "[10.8.0-project02.1]"
        target = ROOT / "mods" / artifact.name
        expected = digest(artifact)
        if target.exists():
            actual = digest(target)
            if actual != expected:
                assert owned.get(target.name) == actual, "An existing destination is not a recorded local build: " + str(target)
                backup = WORK / "verification" / "previous-builds" / actual
                backup.mkdir(exist_ok=True, parents=True)
                shutil.copy2(target, backup / target.name)
                shutil.copy2(artifact, target)
        else:
            shutil.copy2(artifact, target)
        shutil.copy2(artifact, OUT / artifact.name)
        assert digest(target) == expected
        installed.append({"filename": artifact.name, "sha256": expected, "bytes": artifact.stat().st_size,
                          "license": "MIT", "license_path": "META-INF/licenses/mekanism/LICENSE"})
    for artifact in (WORK / "build" / "libs").glob("*-api.jar"):
        shutil.copy2(artifact, OUT / artifact.name)
    for artifact in (WORK / "build" / "libs").glob("*-sources.jar"):
        shutil.copy2(artifact, OUT / artifact.name)
    shutil.copy2(WORK / "LICENSE", OUT / "LICENSE-Mekanism.txt")
    after = {p.name: digest(p) for p in (ROOT / "mods").glob("*.jar")}
    for name, expected in json.loads(snapshot.read_text(encoding="utf-8")).items():
        assert after[name] == expected, name
    record = {"minecraft": "26.1.2", "neoforge": "26.1.2.114", "java": 25,
              "version": "10.8.0-project02.1", "release_type": "alpha",
              "fork_branch": "https://github.com/Viking602/Mekanism/tree/26.1.2",
              "upstream_branch": "https://github.com/mekanism/Mekanism/tree/26.1",
              "upstream_commit": "4718860a0c98598cf1b34e9f631b24eaa7c7ff47",
              "source_workspace": str(WORK), "installed": installed,
              "existing_mods_preserved": len(json.loads(snapshot.read_text(encoding="utf-8"))), "total_root_jars": len(after),
              "status": "installed-pending-client-verification"}
    (WORK / "verification" / "runtime-artifacts.json").write_text(json.dumps(installed, indent=2), encoding="utf-8")
    (OUT / "installed-mekanism.json").write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"installed": len(installed), "preserved": record["existing_mods_preserved"], "total": len(after)}, indent=2))


if __name__ == "__main__":
    main()
