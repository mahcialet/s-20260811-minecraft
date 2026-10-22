#!/usr/bin/env python3
"""Validate packaging contracts, not Minecraft/Mixin runtime compatibility. MIT."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import tomllib
import zipfile

TARGET_SHA256 = 'f8ea80558f7f229314fa026311c194aa956f773bc235a76d7598b0a26d3030bb'
OWN = 'dev/mahcialet/slimetechcompat/'
MINING = 'data/minecraft/tags/block/mineable/pickaxe.json'
EXPECTED_BLOCKS = {
    'casting_mold', 'generator', 'grinder', 'destructor', 'mixer', 'pump',
    'energy_converter', 'energy_cell', 'reactor_core',
}

def check(jar_path: Path, target: Path, project: Path) -> int:
    def require(ok: bool, message: str) -> None:
        if not ok:
            raise ValueError(message)
        print('PASS', message)
    require(hashlib.sha256(target.read_bytes()).hexdigest() == TARGET_SHA256,
            'original Slime Tech JAR hash is the audited 0.1.44 artifact')
    with zipfile.ZipFile(jar_path) as jar, zipfile.ZipFile(target) as original:
        names = jar.namelist()
        require(len(names) == len(set(names)), 'runtime JAR has no duplicate paths')
        require(jar.testzip() is None, 'runtime JAR ZIP CRCs are valid')
        classes = [n for n in names if n.endswith('.class')]
        require(bool(classes) and all(n.startswith(OWN) for n in classes),
                'runtime JAR contains only independently authored patch classes')
        require(all(not any(x in n for x in ('OfflineWeaver', 'RuntimeSliceTests', 'JeiDisplayTests', 'PolicyTests')) for n in names),
                'no test or fixture classes are packaged')
        require(not any(n.startswith(('assets/slime_tech/', 'dev/wizardjw/', 'net/minecraft/', 'mezz/jei/', 'org/spongepowered/')) for n in names),
                'no original assets or third-party implementation classes are packaged')
        for n in names:
            if n.endswith('.json'):
                json.loads(jar.read(n))
        print('PASS all runtime JSON files parse')
        metadata = tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode())
        mod = metadata['mods'][0]
        require(mod['modId'] == 'slime_tech_compat' and mod['version'] == '0.3.0', 'mod identity and version match 0.3.0')
        deps = {d['modId']: d for d in metadata['dependencies']['slime_tech_compat']}
        require(deps['slime_tech']['versionRange'] == '[0.1.44]' and deps['minecraft']['versionRange'] == '[26.1.2]',
                'runtime dependencies pin Slime Tech 0.1.44 and Minecraft 26.1.2')
        require(deps['jei']['type'] == 'optional' and deps['jei']['side'] == 'CLIENT', 'JEI dependency is optional and client-side')
        require(metadata['mixins'][0]['config'] == 'slime_tech_compat.mixins.json', 'Mixin config is explicitly declared in mod metadata')
        mixins = json.loads(jar.read('slime_tech_compat.mixins.json'))
        require(mixins['required'] and mixins['injectors']['defaultRequire'] == 1, 'Mixin failure is not silently ignored')
        for m in mixins['mixins']:
            require(mixins['package'].replace('.', '/') + '/' + m + '.class' in classes, f'{m} exists')
        require(jar.read(MINING) == (project/'src/main/resources'/MINING).read_bytes(), 'mining tag bytes preserved')
        tag = json.loads(jar.read(MINING))
        require(not tag['replace'] and set(tag['values']) == {'slime_tech:' + b for b in EXPECTED_BLOCKS}, 'nine mining fixes remain additive; no tier change')
        require(not any('/recipe/' in n or '/recipes/' in n for n in names), 'no server recipe replacements or additions packaged')
        english = json.loads(jar.read('assets/slime_tech_compat/lang/en_us.json'))
        japanese = json.loads(jar.read('assets/slime_tech_compat/lang/ja_jp.json'))
        require(set(english) == set(japanese), 'Japanese and English translation keys match')
        src = '\n'.join(p.read_text() for p in (project/'src/main/java').rglob('*.java'))
        info_keys = set(re.findall(r'"jei\.slime_tech_compat\.[a-z_]+"', src))
        require(all(k.strip('"') in english for k in info_keys), 'literal JEI title keys exist in both languages')
        require(len(english) == 12 and all(english.values()) and all(japanese.values()), 'two category titles and ten descriptions are present')
        for item in sorted(set(re.findall(r'"slime_tech:([a-z_]+)"', src)) - {'condensed_slime', 'radioactive_slime'}):
            require('assets/slime_tech/items/' + item + '.json' in original.namelist(), f'referenced Slime Tech item {item} exists in original artifact')
        for name in classes:
            code = jar.read(name)
            require(code[:4] == b'\xca\xfe\xba\xbe' and struct.unpack('>H', code[6:8])[0] in (65, 69),
                    f'valid Java 21/25 class: {name.rsplit("/",1)[-1]}')
        common = [n for n in classes if '/jei/' not in n]
        require(all(b'mezz/jei/' not in jar.read(n) and b'net/minecraft/client/' not in jar.read(n) for n in common),
                'common runtime class constants have no JEI or Minecraft client references')
        require('LICENSE' in names, 'independent MIT license included')
        print(f'PACKAGE_CLASSES={len(classes)}')
    return len(classes)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jar', type=Path)
    parser.add_argument('original', type=Path)
    parser.add_argument('--project', type=Path, default=Path(__file__).resolve().parents[1])
    a = parser.parse_args()
    check(a.jar, a.original, a.project)
