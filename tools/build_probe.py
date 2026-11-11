"""Reproducibly build and link-audit the AndroidLiquidGlass integration probe.

Does not produce an installable redesign or a release patch ZIP.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
import subprocess
import sys
import shutil
import zipfile
from pathlib import Path
from build_runtime import gradle_wrapper, sdk_tool

ROOT = Path(__file__).resolve().parents[1]


def run(*args, env=None):
    subprocess.run([str(a) for a in args], cwd=ROOT, env=env, check=True)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--apk", type=Path, default=ROOT / "research/tidal-stock-2.200.0.apk")
    p.add_argument("--sdk", type=Path, default=Path(os.environ.get("ANDROID_HOME", str(Path.home() / "AppData/Local/Android/Sdk"))))
    jbr = Path(r"C:\Program Files\Android\Android Studio\jbr")
    default_java = jbr if jbr.exists() else Path(os.environ.get("JAVA_HOME", ""))
    p.add_argument("--java-home", type=Path, default=default_java)
    p.add_argument("--skip-build", action="store_true")
    args = p.parse_args()
    apk = args.apk.resolve()
    lock = json.loads((ROOT / "upstream-lock.json").read_text())
    with apk.open("rb") as stream:
        actual_hash = hashlib.file_digest(stream, "sha256").hexdigest()
    if actual_hash != lock["tidal"]["sha256"]:
        raise SystemExit(f"Wrong TIDAL APK: {actual_hash}; expected {lock['tidal']['sha256']}")
    mapping = json.loads((ROOT / "mappings/tidal-2.200.0.json").read_text())
    if mapping["apkSha256"] != actual_hash:
        raise SystemExit("Mapping belongs to a different APK")
    env = dict(os.environ, JAVA_HOME=str(args.java_home))
    java = args.java_home / "bin" / ("java.exe" if os.name == "nt" else "java")
    run(sys.executable, ROOT / "tools/prepare_output_bridge.py")
    run(sys.executable, ROOT / "tools/prepare_linn_abi.py")
    from prepare_linn_patch import bridge
    bridge()
    if not args.skip_build:
        run(gradle_wrapper(), ":extension:assembleDebug", "prepareDexTools", "--console=plain", env=env)
    work = ROOT / "research"
    work.mkdir(exist_ok=True)
    for name in ("probe-dex", "probe-smali", "probe-remapped"):
        target = (work / name).resolve()
        if target.parent != work.resolve():
            raise SystemExit("Refusing to clean outside the build workspace")
        if target.exists():
            shutil.rmtree(target)
    jar = work / "extension-classes.jar"
    with zipfile.ZipFile(ROOT / "extension/build/outputs/aar/extension-debug.aar") as archive:
        jar.write_bytes(archive.read("classes.jar"))
    dex_dir = work / "probe-dex"
    dex_dir.mkdir(exist_ok=True)
    build_tools = sorted((args.sdk / "build-tools").iterdir(), key=lambda x: tuple(int(n) for n in x.name.split(".")))[-1]
    run(sdk_tool(build_tools, "d8"), "--release", "--min-api", "33", "--lib", args.sdk / "platforms/android-36/android.jar", "--output", dex_dir, jar, env=env)
    tool = ROOT / "tools/DexTool.java"
    cp = str(ROOT / "build/dex-tools/*")
    def dex(command, src, dest):
        run(java, "-cp", cp, tool, command, src, dest)
    dex("index", apk, work / "host-index.json")
    # D8 compiles this small probe into one dex; refuse to silently drop others.
    dexes = list(dex_dir.glob("*.dex"))
    if len(dexes) != 1:
        raise SystemExit(f"Probe unexpectedly has {len(dexes)} DEX files")
    dex("disassemble", dex_dir / "classes.dex", work / "probe-smali")
    run(sys.executable, ROOT / "tools/remap_smali.py", "--mapping", ROOT / "mappings/tidal-2.200.0.json", "--input", work / "probe-smali", "--output", work / "probe-remapped")
    for source in (ROOT / "extension-smali").rglob("*.smali"):
        target = work / "probe-remapped" / source.relative_to(ROOT / "extension-smali")
        if target.exists():
            raise SystemExit("Duplicate manual helper: " + str(source))
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
    dex("assemble", work / "probe-remapped", work / "probe-remapped.dex")
    dex("index", work / "probe-remapped.dex", work / "probe-remapped-index.json")
    dex("refs", work / "probe-remapped.dex", work / "probe-remapped-refs.json")
    run(sys.executable, ROOT / "tools/check_linkage.py", "--host", work / "host-index.json", "--extension", work / "probe-remapped-index.json", "--refs", work / "probe-remapped-refs.json", "--dex", work / "probe-remapped.dex", "--report", ROOT / "reports/probe-linkage.json")


if __name__ == "__main__":
    main()
