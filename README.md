# Liquidify

**Liquid Glass-inspired customization for TIDAL on Android — with a dedicated patch manager and optional Linn OpenHome playback.**

[![Build](https://github.com/ninocss/liquidify/actions/workflows/build.yml/badge.svg)](https://github.com/ninocss/liquidify/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/ninocss/liquidify?include_prereleases&label=release)](https://github.com/ninocss/liquidify/releases)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)

Liquidify is an **unofficial, open-source modification of the TIDAL Android app**. It combines a Liquid Glass-inspired interface with patches that preserve TIDAL's native navigation and playback behavior. The included **Liquidify Manager**, based on [Radiant Lyrics](https://github.com/meowarex/rl-mobile), handles patch selection and installation.

> [!IMPORTANT]
> **Latest published release: 1.1.0 Beta.** This branch prepares **1.1.1 Beta 1**. Liquidify patches target **TIDAL 2.200.0 (10004)** on **Android 13 or newer**. The Manager itself supports Android 7 or newer, but that does **not** lower the patched app's Android requirement. Other TIDAL versions are not supported unless explicitly documented.

## Features

### A redesigned TIDAL experience

- **Theme-aware Liquid Glass navigation and mini-player** with translucent surfaces and a moving selection lens.
- **Refined native menus, sheets, search, and detail screens**, while retaining the original TIDAL interactions.
- **Light, dark, and system themes**, plus configurable glass effects, component settings, and motion preferences.
- **Library-based glass interaction feedback**, elastic menu/press/slider motion, contextual gestures, native playback controls, and reduced-motion support.

### Optional Linn OpenHome output

Discover a compatible Linn renderer on your local network and transfer online TIDAL music playback to it. Liquidify integrates with TIDAL's native output controls and supports queue handoff, transport, seeking, volume, and shuffle/repeat synchronization.

The renderer plays independently: Liquidify does not relay audio through your phone or a separate server. The Linn device must already have a **valid TIDAL login configured through Linn's own app**. Your Android TIDAL login is not copied to the renderer.

This integration was tested on a **Linn Majik DSM/4 (2020)** with hardware mute enabled. Other renderers, audible quality, physical-device Doze behavior, and accessibility scenarios have not been fully validated. See [Linn output: setup, behavior, and test limits](LINN_OUTPUT.md) before using it.

### Curated patch selection

Liquidify patches are selected by default. Original Radiant Lyrics patches remain credited and unchanged; they are **optional**, shown only when explicitly declared compatible and when their dependencies are available. Disabled or hidden patches and their associated extensions are excluded from the patch plan.

## Download and installation

**[Download Liquidify Manager from GitHub Releases](https://github.com/ninocss/liquidify/releases)**

For the current beta, use the **[Liquidify 1.1.0 Beta release](https://github.com/ninocss/liquidify/releases/tag/v1.1.0-beta)**.

1. Download and install `Liquidify-Manager-1.1.0-beta.apk` from the release assets.
2. Open **Liquidify Manager** and provide the supported stock TIDAL APK when prompted, either through the Manager's download flow or by importing a local copy.
3. Keep the recommended Liquidify patches selected. Review optional compatible patches before enabling them.
4. Patch and install the resulting app, then sign in to TIDAL as usual.

The Manager uses this repository for patch and beta-release updates. It uses its bundled patch payload when the remote release is the same version or older, so an older beta archive cannot replace the shipped features. Newer releases remain available automatically. Advanced users can import the release's `patches.zip` as a custom patch set.

> [!NOTE]
> Patched APKs may require a different app signature from the official TIDAL installation. Back up anything important before replacing an installation; an existing installation signed by another key may need to be uninstalled first. Manager APKs signed with old development keys may likewise require a separate installation.

### Compatibility

| Component | Requirement |
| --- | --- |
| Liquidify patches | Android 13+ |
| Target app | TIDAL **2.200.0 (10004)** |
| Liquidify Manager | Android 7+ (API 24+) |
| Linn output (optional) | Compatible OpenHome renderer on the same local network, with its own authorized TIDAL session |
| Release channel | Beta |

**Only use the pinned TIDAL version.** Upgrading TIDAL independently can invalidate patch offsets and is not a supported update path.

## Build from source

The repository provides Manager sources, patch sources, pinned upstream versions, test tooling, and release automation. See **[BUILDING.md](BUILDING.md)** for the full prerequisites, signing process, and reproducible build steps.

### Prerequisites

- **JDK 21**
- **Android SDK**, including platform 36, platform 37.0, and Build Tools 37.0.0
- **Python 3.12** for patch generation
- An Android development environment with `JAVA_HOME` and `ANDROID_HOME` configured

### Build the patches

From the repository root:

```bash
python -m pip install -r requirements-build.txt
python tools/build_patches.py
python -m unittest discover -s tests -v
python tools/validate_release.py
```

The patch-building script retrieves the **hash-pinned** stock APK and required build inputs, prepares the extension and patch payload, and verifies application. It does not publish or sign a redistributed TIDAL APK.

### Build the Manager

From `radiant-base/Manager`, using its Gradle wrapper:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

On Windows, use `gradlew.bat` instead of `./gradlew`. Debug builds are not interchangeable with release-signed Manager updates.

### Releases and verification

The [Build workflow](.github/workflows/build.yml) validates prepared patches and tests the Manager. Run the [Release workflow](.github/workflows/release.yml) manually on the intended commit to rebuild the patch payload, test and sign the Manager, verify the assets, and create a beta release draft for review. Publishing the draft is a separate step. The workflow refuses to overwrite an existing tag or release.

Release downloads include the Manager APK, `patches.zip`, `manifest.json`, `data.json`, and `SHA256SUMS`. Use the checksums to verify downloaded release files.

Release signing requires private GitHub Actions secrets; **never commit keystores, passwords, account data, or local research artifacts**.

## Repository structure

| Path | Contents |
| --- | --- |
| `radiant-base/Manager/` | Liquidify Manager Android application |
| `extension/` and `extension-smali/` | Liquidify's runtime extension and generated/native integration sources |
| `patches/` | Prepared patch manifest, sources, and installable patch ZIP |
| `radiant-base/patches/` | Preserved upstream Radiant Lyrics patches |
| `tools/` and `tests/` | Build scripts, validators, and automated tests |
| `mappings/` and `host-stubs/` | Version-specific integration data and compile-only host interfaces |
| `upstream-lock.json` | Pinned upstream revisions, download URLs, and SHA-256 hashes |
| `release.json` | Release version and identity metadata |

For technical background, consult [IMPLEMENTATION.md](IMPLEMENTATION.md), [BUILDING.md](BUILDING.md), and [LINN_OUTPUT.md](LINN_OUTPUT.md).

## Contributing and reporting issues

Issues and pull requests are welcome. For bug reports, include the Manager version, Android version, the **exact TIDAL version**, steps to reproduce, and relevant logs where possible. **Remove account details, tokens, device identifiers, private network addresses, and other sensitive data** before sharing diagnostics.

This is a beta project: features may change, and compatibility with other TIDAL builds or playback devices should not be assumed.

## Credits

Liquidify is edited and maintained by [ninocss](https://github.com/ninocss), and builds upon the work of other open-source projects:

- **[Radiant Lyrics](https://github.com/meowarex/rl-mobile)** — the original Manager and upstream patches, by **meowarex and contributors**. Original patch identities and credits are preserved.
- **[Square](https://github.com/Lelonio/Square)** — visual design reference and Android adaptation, by **Lelonio**.
- **[AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)** — glass material and visual effects, by **Kyant0**.
- **Inter** — typeface distributed under the SIL Open Font License.

See [third-party licenses](licenses/) and [Radiant's third-party notices](radiant-base/THIRD-PARTY.md) for additional acknowledgments.

## License and disclaimer

The repository's open-source code is licensed under the **GNU General Public License v3.0**; see [LICENSE](LICENSE). Third-party components remain subject to their respective licenses.

**Liquidify is an independent community project. It is not affiliated with, endorsed by, or sponsored by TIDAL or Linn.** TIDAL, its app, trademarks, and services belong to their respective owners. The GPL license for this repository does **not** grant rights to TIDAL's proprietary Android application. No modified TIDAL APK, user credentials, or private signing keys are distributed in this repository.
