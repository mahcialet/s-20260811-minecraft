#!/usr/bin/env python3
"""Reproduce the PROVISIONAL offline build and explicitly modeled tests. MIT.
Requires JDK 21 and Python 3.11+. This is not a full NeoForge build or Mixin launch test.
"""
from __future__ import annotations
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile
from abi_stubs import write_stubs
from check_package import check, TARGET_SHA256

ROOT = Path(__file__).resolve().parents[1]
STAMP = (2026, 9, 21, 0, 0, 0)


def package(classes: Path, resources: Path, jar: Path) -> None:
    files: dict[str, bytes] = {}
    for base in (classes, resources):
        for p in base.rglob('*'):
            if p.is_file():
                files[p.relative_to(base).as_posix()] = p.read_bytes()
    files['LICENSE'] = (ROOT/'LICENSE').read_bytes()
    files['META-INF/MANIFEST.MF'] = (
        'Manifest-Version: 1.0\r\n'
        'Implementation-Title: Slime Tech Compatibility Patch\r\n'
        'Implementation-Version: 0.3.0\r\n'
        'Build-Verification: offline-fixtures-only\r\n\r\n'
    ).encode('ascii')
    with zipfile.ZipFile(jar, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as out:
        for name, data in sorted(files.items()):
            info = zipfile.ZipInfo(name, STAMP)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            out.writestr(info, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--original', required=True, type=Path, help='Unmodified slime_tech-0.1.44.jar; never redistributed')
    args = p.parse_args()
    original = args.original.resolve(strict=True)
    if hashlib.sha256(original.read_bytes()).hexdigest() != TARGET_SHA256:
        raise SystemExit('Refusing unknown original JAR hash. Re-audit targets before building.')
    for name in ('java', 'javac'):
        if not shutil.which(name):
            raise SystemExit(f'{name} not found; use JDK 21 for this offline verifier.')
    version = subprocess.check_output(['javac', '-version'], text=True, stderr=subprocess.STDOUT)
    if not version.startswith('javac 21.'):
        raise SystemExit('The internal-ASM verifier is pinned to JDK 21. Use Gradle + JDK 25 for the real build.')
    build = ROOT/'build'
    build.mkdir(exist_ok=True)
    for folder in ('offline-api-src','offline-api','offline-targets','classes','tests','tools'):
        shutil.rmtree(build/folder, ignore_errors=True)
        (build/folder).mkdir()
    logs: dict[str, str] = {}
    def run(label: str, cmd: list[str], expected: int = 0) -> str:
        result = subprocess.run(cmd, cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        (build/(label+'.log')).write_text(result.stdout)
        logs[label] = result.stdout
        if result.returncode != expected:
            print(result.stdout, file=sys.stderr)
            raise RuntimeError(f'{label}: expected exit {expected}, got {result.returncode}')
        print(f'PASS {label} (exit={result.returncode})')
        return result.stdout
    def sources(base: Path, filename: str) -> str:
        path = build/filename
        path.write_text('\n'.join('"'+str(x)+'"' for x in sorted(base.rglob('*.java')))+'\n')
        return '@'+str(path)
    print('Writing',len(write_stubs(build/'offline-api-src')),'independent API fixtures (not runtime libraries).')
    run('compile-fixtures', ['javac','--release','21','-encoding','UTF-8','-d',str(build/'offline-api'),sources(build/'offline-api-src','api.sources')])
    cp = str(build/'offline-api')
    run('compile-patch', ['javac','--release','21','-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(build/'classes'),sources(ROOT/'src/main/java','main.sources')])
    run('compile-tests', ['javac','--release','21','-encoding','UTF-8','-cp',os.pathsep.join((str(build/'classes'),cp)),'-d',str(build/'tests'),sources(ROOT/'src/test/java','test.sources'),str(ROOT/'tools/RuntimeSliceTests.java'),str(ROOT/'tools/JeiDisplayTests.java')])
    exports = ['--add-exports','java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED','--add-exports','java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED']
    run('compile-weaver', ['javac',*exports,'-d',str(build/'tools'),str(ROOT/'tools/OfflineWeaver.java')])
    text = run('weaver',['java',*exports,'-cp',os.pathsep.join((str(build/'tools'),cp)),'OfflineWeaver',str(original),str(build/'classes'),str(build/'offline-targets')])
    if 'TOTAL_CONTRACT_MATCHES=20' not in text: raise RuntimeError('Unexpected injection-contract count')
    testcp = os.pathsep.join(str(build/x) for x in ('tests','classes','offline-api'))
    text=run('policy-tests',['java','-Xverify:all','-cp',testcp,'dev.mahcialet.slimetechcompat.PolicyTests'])
    if 'POLICY_TESTS=33' not in text: raise RuntimeError('Missing policy assertions')
    text=run('jei-display-tests',['java','-Xverify:all','-cp',testcp,'JeiDisplayTests'])
    if 'JEI_DISPLAY_TESTS=18' not in text: raise RuntimeError('Missing JEI fixture assertions')
    for suffix, extra in (('patched',[]),('patched-options-off',['off'])):
        text=run('slice-'+suffix,['java','-Xverify:all','-cp',str(build/'offline-targets/patched')+os.pathsep+testcp,'RuntimeSliceTests',*extra])
        if 'SLICE_TESTS passed=25 failed=0' not in text: raise RuntimeError('Missing patched slice assertions')
    baseline=run('slice-baseline',['java','-Xverify:all','-cp',str(build/'offline-targets/baseline')+os.pathsep+testcp,'RuntimeSliceTests'],expected=1)
    failures=[line for line in baseline.splitlines() if line.startswith('FAIL ')]
    if len(failures)!=20 or any(':: java.lang.AssertionError:' not in line for line in failures):
        raise RuntimeError('Negative control did not fail for the expected assertions (not a linkage failure)')
    if 'SLICE_TESTS passed=5 failed=20' not in baseline: raise RuntimeError('Unexpected negative-control result')
    out=build/'libs/slime-tech-compat-0.3.0.jar'
    out.parent.mkdir(exist_ok=True)
    package(build/'classes',ROOT/'src/main/resources',out)
    import contextlib
    with (build/'package-check.log').open('w') as log, contextlib.redirect_stdout(log):
        check(out,original,ROOT)
    print('PASS package-check')
    print('PROVISIONAL_JAR='+str(out))
    print('SHA256='+hashlib.sha256(out.read_bytes()).hexdigest())
    print('NOT TESTED: real dependency build, real Sponge Mixin application, game/server launch, JEI rendering.')

if __name__=='__main__':
    main()
