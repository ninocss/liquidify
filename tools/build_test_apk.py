"""Build a signed, separate local integration test APK. Does not install anything."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import shutil
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument("--skip-extension-build", action="store_true")
p.add_argument("--internal-emulator", action="store_true", help="Emulator ONLY: exports MainActivity and uses original package")
p.add_argument("--theme-mode", type=int, choices=(0, 1, 2), default=0, help="Separate test APK only: system/light/dark override")
args = p.parse_args()
java_home = Path(r"C:\Program Files\Android\Android Studio\jbr")
sdk = Path(os.environ.get("ANDROID_HOME", str(Path.home() / "AppData/Local/Android/Sdk")))
bt = sdk / "build-tools/37.0.0"
env = dict(os.environ, JAVA_HOME=str(java_home))


def run(*command):
    subprocess.run([str(c) for c in command], cwd=ROOT, env=env, check=True)


if not args.skip_extension_build:
    run(sys.executable, "tools/build_probe.py")
else:
    # Bind the report to the current DEX, including when the checker changed.
    run(sys.executable, "tools/check_linkage.py", "--host", "research/host-index.json",
        "--extension", "research/probe-remapped-index.json", "--refs", "research/probe-remapped-refs.json",
        "--dex", "research/probe-remapped.dex", "--report", "reports/probe-linkage.json")
run(sys.executable, "tools/prepare_patch_sources.py")
hooks = ROOT / "research/probe-hook-smali"
combined = ROOT / "research/probe-combined-smali"
if combined.resolve().parent != (ROOT / "research").resolve():
    raise SystemExit("Unexpected combined build directory")
if combined.exists():
    shutil.rmtree(combined)
shutil.copytree(hooks, combined)
shutil.copytree(ROOT / "research/probe-remapped", combined, dirs_exist_ok=True)
if args.theme_mode:
    settings = combined / "dev/nino/squareglass/SquareSettings.smali"
    text, count = re.subn(r"(\.method public static initialTheme\(\)I.*?const/4 v0, )0x0", rf"\g<1>0x{args.theme_mode:x}", settings.read_text(), flags=re.S)
    if count != 1: raise SystemExit("Unexpected test theme ABI")
    settings.write_text(text, newline="\n")
run(java_home / "bin/java.exe", "-cp", "build/dex-tools/*", "tools/DexTool.java", "assemble", combined, "research/probe-combined.dex")
run(java_home / "bin/java.exe", "-cp", "build/dex-tools/*", "tools/DexTool.java", "index", "research/probe-combined.dex", "research/probe-combined-index.json")
run(java_home / "bin/java.exe", "-cp", "build/dex-tools/*", "tools/DexTool.java", "refs", "research/probe-combined.dex", "research/probe-combined-refs.json")
run(sys.executable, "tools/check_linkage.py", "--host", "research/host-index.json",
    "--extension", "research/probe-combined-index.json", "--refs", "research/probe-combined-refs.json",
    "--dex", "research/probe-combined.dex", "--report", "reports/probe-combined-linkage.json",
    "--allow-duplicates")
run(sys.executable, "tools/build_design_resources.py", "--theme-mode", str(args.theme_mode))
with zipfile.ZipFile(ROOT / "research/tidal-stock-2.200.0.apk") as stock:
    (ROOT / "research/manifest-original.xml").write_bytes(stock.read("AndroidManifest.xml"))
run(java_home / "bin/java.exe", "-cp", "build/dex-tools/*", "tools/ManifestTest.java", "research/manifest-original.xml", "research/manifest-test.xml")
if args.internal_emulator:
    run(java_home / "bin/java.exe", "-cp", "build/dex-tools/*", "tools/ManifestProbe.java", "research/manifest-original.xml", "research/manifest-probe.xml")
package_args = ["--emulator-internal"] if args.internal_emulator else []
run(sys.executable, "tools/make_probe_apk.py", *package_args)
stem = "tidal-probe" if args.internal_emulator else "squareglass-test"
run(bt / "zipalign.exe", "-f", "-P", "16", "4", f"research/{stem}-unsigned.apk", f"research/{stem}-aligned.apk")
run(bt / "apksigner.bat", "sign", "--ks", Path.home() / ".android/debug.keystore", "--ks-key-alias", "androiddebugkey",
    "--ks-pass", "pass:android", "--key-pass", "pass:android", "--out", f"research/{stem}.apk", f"research/{stem}-aligned.apk")
run(bt / "apksigner.bat", "verify", "--verbose", f"research/{stem}.apk")
run(bt / "zipalign.exe", "-c", "-P", "16", "4", f"research/{stem}.apk")
inspection = subprocess.run([str(bt / "aapt2.exe"), "dump", "badging", f"research/{stem}.apk"], cwd=ROOT, text=True, capture_output=True, check=True)
if "Bad string block" in inspection.stderr or "error:" in inspection.stderr.lower():
    raise SystemExit("Resource inspection failed: " + inspection.stderr)
package = inspection.stdout.splitlines()[0]
expected = "com.aspiro.tidal" if args.internal_emulator else "dev.nino.tidalglass.test"
if f"name='{expected}'" not in package:
    raise SystemExit("Unexpected package identity: " + package)
report = dict(package=expected, kind="local integration test", themeMode=args.theme_mode, sha256=hashlib.sha256((ROOT / f"research/{stem}.apk").read_bytes()).hexdigest(), signatureVerified=True, zipAlignmentVerified=True)
(ROOT / f"reports/{stem}-build.json").write_text(json.dumps(report, indent=2) + "\n")
print(json.dumps(report))
