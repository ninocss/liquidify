"""Verify that a Manager APK supports first installation without a GitHub release."""
import hashlib
import io
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def verify(apk):
    release = json.loads((ROOT / 'release.json').read_text())
    tidal = json.loads((ROOT / 'upstream-lock.json').read_text())['tidal']
    expected = {
        'tidalVersionCode': tidal['versionCode'],
        'tidalApkUrl': tidal['url'],
        'patchesVersion': release['version'],
        'tidalSha256': tidal['sha256'],
    }
    with zipfile.ZipFile(apk) as manager:
        assert json.loads(manager.read('assets/liquidify/data.json')) == expected
        patches = manager.read('assets/liquidify/patches.zip')
        assert patches == (ROOT / release['patches']).read_bytes(), 'Bundled patches differ'
        with zipfile.ZipFile(io.BytesIO(patches)) as payload:
            manifest = json.loads(payload.read('manifest.json'))
            assert manifest['releaseVersion'] == expected['patchesVersion']
            assert json.loads(manager.read('assets/patches-manifest.json')) == manifest
    return {'passed': True, 'bundledBuildInfoExact': True,
            'bundledPatchesSha256': hashlib.sha256(patches).hexdigest()}


if __name__ == '__main__':
    print(json.dumps(verify(sys.argv[1])))
