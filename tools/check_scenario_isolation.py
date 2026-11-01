#!/usr/bin/env python3
"""Fail closed on scenario leakage or persistence/network dependencies. Run after both APK builds."""
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parent.parent
DEBUG = ROOT / 'wear/src/debug/kotlin/io/github/grassified/wristprompt/openai'


def check_dependencies():
    sources = '\n'.join(p.read_text() for p in DEBUG.glob('*.kt'))
    for forbidden in ('EncryptedRecord', 'SecretRecord', 'WatchProfiles(', 'ApiKeyRepository(',
                      'CredentialProfile(', 'ApiKeyCredential(', 'ChatGptPlanCredential(',
                      'OpenAiHttp(', 'OpenAiProvider(', 'preferencesDataStore', 'getSharedPreferences',
                      'application as', 'Authorization', 'saveModel ='):
        assert forbidden not in sources, f'Debug harness dependency forbidden: {forbidden}'
    fixture = (DEBUG / 'DebugScenarios.kt').read_text()
    assert 'import android.' not in fixture and 'import java.io.' not in fixture
    assert 'storage.' not in fixture and 'DataStore' not in fixture
    assert 'selectedModel =' not in fixture


def check():
    check_dependencies()
    from wear_scenarios import scenarios
    markers = ('ImageGenerationInstrumentation', 'DebugGenerationActivity', 'GenerationAcceptanceOwner', 'DEBUG_GENERATION', 'debug-generation.txt', 'DebugScenario', 'StreamingPreviewActivity', 'DEBUG_LIFECYCLE',
               'debug-scenario-ready.txt', 'BuildConfig.DEBUG', *scenarios())
    for source in ('wear/src/main', 'mobile/src/main', 'shared/src/main'):
        for p in (ROOT / source).rglob('*'):
            if p.is_file():
                content = p.read_text()
                assert all(marker not in content for marker in markers), p
    for variant in ('debug', 'release'):
        manifest = ROOT / f'wear/build/intermediates/merged_manifests/{variant}/process{variant.title()}Manifest/AndroidManifest.xml'
        tree = ET.parse(manifest)
        names = [node.attrib.get('{http://schemas.android.com/apk/res/android}name', '') for node in tree.iter()]
        assert any('DebugScenarioActivity' in name for name in names) == (variant == 'debug')
        assert any('DebugGenerationActivity' in name for name in names) == (variant == 'debug')
        assert not any('ImageGenerationInstrumentation' in name for name in names)
        assert not any('StreamingPreviewActivity' in name for name in names)
        folder = ROOT / f'wear/build/outputs/apk/{variant}'
        apk, = folder.glob('*.apk')
        with zipfile.ZipFile(apk) as archive:
            dex = b''.join(archive.read(name) for name in archive.namelist() if name.endswith('.dex'))
        assert b'ImageGenerationInstrumentation' not in dex, (variant, 'test runner leaked into app')
        for marker in (b'DebugGenerationActivity', b'GenerationAcceptanceOwner', b'DEBUG_GENERATION', b'debug-generation.txt', b'DebugScenarioActivity', b'DebugScenarios;', b'DebugScenario;', b'ScenarioOwner;',
                       b'debug-scenario-ready.txt', b'DEBUG_LIFECYCLE'):
            assert (marker in dex) == (variant == 'debug'), (variant, marker)
    for name in scenarios():
        assert name.encode() not in dex, ('release scenario name', name)
    print('PASS: debug-only manifests/DEX; no production references; no harness storage/network dependencies')


if __name__ == '__main__':
    check()
