#!/usr/bin/env python3
"""Check release boundaries; no game fixtures or original MOD contents may ship."""
import json
from pathlib import Path
from zipfile import ZipFile
root = Path(__file__).resolve().parents[1]
jar = root / 'build/libs/slime-tech-compat-0.4.0-dev.jar'
source = root / 'build/libs/slime-tech-compat-0.4.0-dev-source.zip'
with ZipFile(jar) as z:
    names = z.namelist()
    classes = [n for n in names if n.endswith('.class')]
    assert classes and all(n.startswith('dev/mahcialet/slimetechcompat/') for n in classes)
    assert not any(x in n for n in names for x in ('RuntimeTests', 'SafetyRuntimeChecks', 'PolicyTests', 'OfflineWeaver', 'abi_stubs'))
    assert not any(n.endswith('.jar') for n in names)
    assert not any(not n.endswith('/') and n.startswith('assets/') and not n.startswith('assets/slime_tech_compat/') for n in names)
    mixins = json.loads(z.read('slime_tech_compat.mixins.json'))
    assert mixins['required'] and len(mixins['mixins']) == 5
    tags = json.loads(z.read('data/minecraft/tags/block/mineable/pickaxe.json'))
    assert tags['replace'] is False and len(tags['values']) == 9
    assert b'version="0.4.0-dev"' in z.read('META-INF/neoforge.mods.toml')
with ZipFile(source) as z:
    names = z.namelist()
    assert not any(n.startswith(('build/', 'run', 'reference/', 'libs/')) for n in names)
    assert not any('abi_stubs' in n or 'OfflineWeaver' in n or n.endswith('.class') for n in names)
    assert [n for n in names if n.endswith('.jar')] == ['gradle/wrapper/gradle-wrapper.jar']
    assert 'docs/MIGRATION.md' in names and 'src/main/resources/slime_tech_compat.mixins.json' in names
print('PASS artifact boundaries: independent patch JAR and reproducible source archive')
