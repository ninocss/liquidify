# Building Liquidify 1.1.1 Beta 1

## Manager

The Android project lives in `radiant-base/Manager`. Use JDK 21, Android SDK 37
and build-tools 37.0.0. Configure `local.properties` locally, then run its wrapper:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The display name is Liquidify Manager; the existing internal package
`dev.nino.squareglass.manager` remains for compatibility. Release version and
versionCode are checked against `release.json`. The bundled manifest is copied
from `patches/manifest.json`, never from the unmodified upstream-only manifest.

For release signing set `RELEASE=true`, `SIGNING_STORE_FILE`,
`SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD`.
Release builds fail if signing credentials are absent. Never commit these values.

## Patch regeneration

The checked-in `patches/patches.zip` is the prepared installable payload. Release
CI rebuilds it from source with `python tools/build_patches.py`, then validates
the result with `python tools/validate_release.py`. Upstream
Radiant patch specs and files are preserved unchanged; Liquidify entries declare
`source: liquidify`. Compatibility declarations must come from actual tested
metadata, not from blanket relabeling of upstream patches.

For an automatic build on Linux or Windows, set `JAVA_HOME` and `ANDROID_HOME`,
install Python 3.12 and `pip install -r requirements-build.txt`, then run
`python tools/build_patches.py`. Install Android SDK platforms 36 and 37.0 plus
build-tools 37.0.0. The script downloads the hash-pinned stock APK, Apktool and
font, decodes the stock APK, compiles the extension, generates hooks, verifies
host linkage and strict patch application, and creates the Manager-ready ZIP.
It does not sign or publish a TIDAL APK.

For the individual local integration tools, download the exact stock APK in `upstream-lock.json` to
`research/tidal-stock-2.200.0.apk` and verify its SHA-256. Decode it with apktool
3.0.3 into `research/tidal`. Obtain the pinned Square font at
`square-reference/app/src/main/res/font/inter.ttf`; the revision and OFL
are recorded in the lock file and licenses. No nested Git repository is required.
Upstream Radiant patches are vendored in `radiant-base/patches`.

```sh
python tools/build_test_apk.py
python tools/package_alpha.py
python tools/verify_strict_patches.py
python -m unittest discover -s tests -v
```

The historical packaging script name is retained, but emits
`dist/Liquidify-1.1.1-beta.1-patches.zip`. Copy that file to `patches/patches.zip`
and its `manifest.json` to `patches/manifest.json`; run the release validator.
`host-stubs` contains compile-only native ABI declarations and must not ship.
Patch builds reuse the host Compose/Kotlin runtime. TIDAL binaries, keys and
private LAN/account evidence stay in ignored local directories.

## GitHub release

Manager APKs include the pinned build metadata and exact prepared patch ZIP.
Local patch imports do not require GitHub release metadata. A first installation
falls back to the bundled payload when no release or cached metadata is available.
The stock TIDAL APK still needs a download or a local import.
Verify the packaged fallback with `python tools/verify_manager_bundle.py <manager.apk>`.
GitHub commit history and release updates require a publicly accessible repository;
the Manager does not authenticate to private repositories.

Run the root workflow `.github/workflows/release.yml` manually on the intended
commit. Its first job builds the patches from source,
runs the patch tests and retains the generated ZIP and build reports. Its second
job downloads that exact payload, builds and tests Manager with JDK 21, and verifies
that the embedded ZIP, bundled patch list and build metadata match the release.
It uploads the signed Manager APK, `patches.zip`, `manifest.json`, `data.json`
and `SHA256SUMS` to a beta draft. Review and publish the draft separately. The
workflow refuses to replace an existing tag or release. The Manager uses the release's
patch list and metadata for online installation, with the identical bundled ZIP
available when the release cannot be reached.
GitHub automatically offers the matching repository source archives.

Required Actions secrets: `LIQUIDIFY_SIGNING_KEYSTORE` (base64 JKS),
`LIQUIDIFY_SIGNING_PASSWORD`, and `LIQUIDIFY_SIGNING_ALIAS`.
For a new signing identity, `python tools/configure_release_signing.py` creates a
dedicated key under ignored `research/release-signing`. Back up that directory
securely; it contains the key and its password. After normal GitHub CLI login,
`python tools/configure_release_signing.py --upload` sets the three secrets without
printing their values. The public certificate fingerprint in `release.json` is
checked before publication so future releases retain the same identity.
This release identity is different from debug builds; it cannot update a Manager
signed with a different key. Keep existing Manager data and its TIDAL signing key
until a migration has been arranged.
The same signing key must be retained for future Manager updates. Configure these secrets on `ninocss/liquidify` before running the release workflow; private material is excluded from Git. The normal Build workflow needs no release signing secrets and retains a debug Manager APK and the prepared patches as artifacts.
