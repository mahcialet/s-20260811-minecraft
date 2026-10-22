#!/usr/bin/env python3
"""Run paired-seed observations, preserving completed runs and original measurements."""
import argparse
import collections
import json
import os
from pathlib import Path
import subprocess
import signal
import time

ROOT = Path(__file__).resolve().parents[1]
VARIANTS = ('balanced', 'equal-all', 'equal-ore')
TERRAINS = ('normal', 'flat', 'flat-desert')


def directory(trial, terrain, variant):
    return ROOT / f'run-phase2-phase2-{terrain}-1-{variant}-trial{trial:02d}'


def audit(trial, terrain, variant):
    path = directory(trial, terrain, variant)
    assert (path / 'phase2-result.txt').read_text().strip() == 'COMPLETE'
    result = json.loads((path / 'phase2-result.json').read_text())
    rows = [json.loads(line) for line in (path / 'phase2.jsonl').read_text().splitlines()]
    assert result['protocol'] == 'prepared-v2' and result['trial'] == trial
    assert result['seed'] == 384731 + (trial - 1) * 104729
    assert result['weightVariant'] == variant and result['players'] == 1
    expected_terrain = 'normal' if terrain == 'normal' else 'flat'
    expected_biome = 'minecraft:desert' if terrain == 'flat-desert' else 'minecraft:taiga'
    assert result['terrain'] == expected_terrain and result['profile'] == 'phase2'
    setup = next(row for row in rows if row['record'] == 'setup')
    assert setup['terrain'] == expected_terrain and setup['biome'] == expected_biome
    assert setup['trial'] == trial and setup['protocol'] == 'prepared-v2'
    assert setup['weightVariant'] == variant and setup['seed'] == result['seed']
    assert setup['compatModLoaded'] and setup['players'] == 1
    site = next(row for row in rows if row['record'] == 'siteSelection')
    assert site['position'][1] == setup['observerY']
    if terrain != 'normal':
        assert site['position'] == [530, 65, 530]
    assert result['observationComplete'] and result['elapsedTicks'] == 72000
    prepared = next(row for row in rows if row['record'] == 'prepared')
    assert all(prepared[key] == 289 for key in ('candidateChunks', 'fullChunks', 'entityTickingLoadedChunks'))
    assert prepared['stableReadyTicks'] >= 200
    samples = [r for r in rows if r['record'] == 'sample']
    assert [r['elapsedTicks'] for r in samples] == list(range(200, 72001, 200))
    assert all(r['nearbyPlayers'] == 1 and r['spawnableChunks'] == 289 for r in samples)
    assert result['activeSamples'] == 360
    assert result['checkpoints'] == [r for r in samples if r['elapsedTicks'] in (12000, 36000, 72000)]
    config = next(r['values'] for r in rows if r['record'] == 'effectiveConfig')
    assert config['backupSpawnerMode'] == 'DISABLED'
    assert config['standardSpawnWeightScale'] == (0.01 if variant == 'balanced' else 1.0)
    joins = [r for r in rows if r['record'] == 'join']
    assert not any(r['source'] in ('backup-direct', 'chunk-generation') for r in joins)
    natural_joins = {r['uuid']: r for r in joins if r['source'] == 'natural'}
    verified = [r for r in rows if r['record'] == 'verifiedNatural']
    assert len({r['uuid'] for r in verified}) == len(verified)
    for row in verified:
        join = natural_joins[row['uuid']]
        assert join['type'] == row['type'] and join['gameTime'] == row['spawnGameTime']
        assert row['verifiedGameTime'] > row['spawnGameTime']
        if row['type'].startswith('oreslimes:'):
            assert join['slimeChunk']
    assert dict(collections.Counter(r['type'] for r in verified)) == result['verifiedNaturalByType']
    assert dict(collections.Counter(r['source'] + '/' + r['type'] for r in joins)) == result['joinsByOriginAndType']
    return result


def run(through, jobs):
    logs = ROOT / 'run-phase2-series' / 'logs'
    logs.mkdir(parents=True, exist_ok=True)
    pending = []
    for trial in range(1, through + 1):
        order = VARIANTS[(trial - 1) % 3:] + VARIANTS[:(trial - 1) % 3]
        for terrain in TERRAINS:
            for variant in order:
                key = trial, terrain, variant
                path = directory(*key)
                result_file = path / 'phase2-result.txt'
                if result_file.is_file() and result_file.read_text().strip() == 'COMPLETE':
                    audit(*key)
                    print(json.dumps({'event': 'reuse', 'trial': trial, 'terrain': terrain, 'variant': variant}), flush=True)
                else:
                    pending.append(key)
    active = []
    startup = None
    startup_stamp = None
    try:
        while pending or active:
            for job in active[:]:
                process, handle, key = job
                code = process.poll()
                if code is None:
                    continue
                handle.close()
                active.remove(job)
                if code:
                    raise RuntimeError(f'Observation exited {code}: {key}')
                audit(*key)
                print(json.dumps({'event': 'complete', 'trial': key[0], 'terrain': key[1], 'variant': key[2]}), flush=True)
                if startup == key:
                    startup = None
            if startup:
                path = directory(*startup) / 'phase2.jsonl'
                # ModDevGradle writes shared launch args: let one server finish startup before launching the next.
                if path.exists() and path.stat().st_mtime_ns != startup_stamp:
                    for line in path.read_text().splitlines():
                        try:
                            row = json.loads(line)
                        except json.JSONDecodeError:
                            continue  # The active server may be writing its last line.
                        if (row.get('record') == 'setup' and row.get('trial') == startup[0]
                                and row.get('weightVariant') == startup[2] and row.get('protocol') == 'prepared-v2'):
                            startup = None
                            break
            if pending and len(active) < jobs and startup is None:
                key = pending.pop(0)
                trial, terrain, variant = key
                cmd = ['rtk', 'proxy', './gradlew', 'runPhase2Server', f'-Pphase2Trial={trial}',
                       f'-Pphase2WeightScale={variant}', '-Pphase2Terrain=' + ('normal' if terrain == 'normal' else 'flat')]
                if terrain == 'flat-desert':
                    cmd.append('-Pphase2FlatBiome=desert')
                handle = (logs / f'{trial:02d}-{terrain}-{variant}.log').open('w')
                old_jsonl = directory(*key) / 'phase2.jsonl'
                startup_stamp = old_jsonl.stat().st_mtime_ns if old_jsonl.exists() else None
                process = subprocess.Popen(cmd, cwd=ROOT, stdout=handle, stderr=subprocess.STDOUT, start_new_session=True)
                active.append((process, handle, key))
                startup = key
                print(json.dumps({'event': 'start', 'trial': trial, 'terrain': terrain, 'variant': variant}), flush=True)
            time.sleep(1)
    finally:
        for process, handle, key in active:
            if process.poll() is None:
                os.killpg(process.pid, signal.SIGTERM)
            handle.close()


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--through', type=int, choices=(5, 10, 20), required=True)
    parser.add_argument('--jobs', type=int, choices=(1, 2), default=2)
    args = parser.parse_args()
    run(args.through, args.jobs)
