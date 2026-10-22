#!/usr/bin/env python3
"""Audit and summarize the predeclared paired-seed Phase 2 experiment."""
import argparse
import importlib.util
import itertools
import json
import math
from pathlib import Path
import random
import statistics
import sys

ROOT = Path(__file__).resolve().parents[1]
ANIMALS = ('rabbit', 'cow', 'sheep', 'pig')
COUNTS = ('oreSlime', 'vanillaSlime', *ANIMALS, 'totalCreature')
BOOTSTRAPS = 10000
BOOTSTRAP_SEED = 20261022
DELTA = 1.0
DURATION = 72000


def quantile(values, probability):
    """Linear interpolation between order statistics, including singleton samples."""
    ordered = sorted(values)
    position = (len(ordered) - 1) * probability
    low = math.floor(position)
    high = math.ceil(position)
    return ordered[low] + (ordered[high] - ordered[low]) * (position - low)


def describe(values):
    return {
        'n': len(values), 'mean': statistics.fmean(values),
        'median': statistics.median(values),
        'q1': quantile(values, .25), 'q3': quantile(values, .75),
        'iqr': quantile(values, .75) - quantile(values, .25),
        'min': min(values), 'max': max(values),
        'zeroTrials': sum(value == 0 for value in values),
    }


def load_runner():
    spec = importlib.util.spec_from_file_location('phase2_series', ROOT / 'scripts/phase2-series.py')
    module = importlib.util.module_from_spec(spec)
    previous = sys.dont_write_bytecode
    try:
        sys.dont_write_bytecode = True
        spec.loader.exec_module(module)
    finally:
        sys.dont_write_bytecode = previous
    return module


def first_natural(rows, start_tick, predicate):
    times = [row['spawnGameTime'] - start_tick for row in rows
             if row['record'] == 'verifiedNatural' and predicate(row['type'])]
    if times:
        first = min(times)
        if not 0 <= first <= DURATION:
            raise ValueError(f'Natural proof outside observation window: {first}')
        return {'observed': True, 'elapsedTicks': first}
    return {'observed': False, 'elapsedTicks': None, 'rightCensoredAfterTicks': DURATION}


def measure(runner, trial, terrain, variant):
    path = runner.directory(trial, terrain, variant)
    try:
        result = runner.audit(trial, terrain, variant)
    except (AssertionError, OSError, ValueError, KeyError, StopIteration) as error:
        raise ValueError(f'Run audit failed: {path.name}: {type(error).__name__}: {error}') from error
    rows = [json.loads(line) for line in (path / 'phase2.jsonl').read_text().splitlines()]
    setup = next(row for row in rows if row['record'] == 'setup')
    site = next(row for row in rows if row['record'] == 'siteSelection')
    start = next(row for row in rows if row['record'] == 'start')
    expected_terrain = 'normal' if terrain == 'normal' else 'flat'
    expected_biome = 'minecraft:desert' if terrain == 'flat-desert' else 'minecraft:taiga'
    if (result['profile'] != 'phase2' or result['terrain'] != expected_terrain
            or setup['terrain'] != expected_terrain or setup['biome'] != expected_biome
            or setup['seed'] != result['seed'] or setup['trial'] != trial
            or setup['weightVariant'] != variant or setup['protocol'] != 'prepared-v2'):
        raise ValueError(f'Unexpected setup identity: {path}')
    samples = [row for row in rows if row['record'] == 'sample']
    targets = ('rabbit',) if terrain == 'flat-desert' else ANIMALS
    primary = statistics.fmean(sum(row['counts'][animal] for animal in targets) for row in samples)
    natural = result['verifiedNaturalByType']
    natural_counts = {animal: natural.get('minecraft:' + animal, 0) for animal in ANIMALS}
    natural_counts['oreSlime'] = sum(count for species, count in natural.items() if species.startswith('oreslimes:'))
    first = {animal: first_natural(rows, start['gameTime'], lambda species, a=animal: species == 'minecraft:' + a)
             for animal in ANIMALS}
    first['oreSlime'] = first_natural(rows, start['gameTime'], lambda species: species.startswith('oreslimes:'))
    return {
        'trial': trial, 'seed': result['seed'], 'source': str(path.relative_to(ROOT)),
        'site': site['position'], 'biome': setup['biome'],
        'primaryAnimalMean': primary,
        'timeMeanCounts': {key: statistics.fmean(row['counts'][key] for row in samples) for key in COUNTS},
        'naturalCounts': natural_counts, 'naturalCountsByType': natural,
        'oreOccupiedSampleFraction': sum(row['counts']['oreSlime'] > 0 for row in samples) / len(samples),
        'coexistenceSampleFraction': sum(row['counts']['oreSlime'] > 0
            and sum(row['counts'][animal] for animal in targets) > 0 for row in samples) / len(samples),
        'firstVerifiedNatural': first,
        'checkpointCounts': {str(row['elapsedTicks'] // 1200): row['counts'] for row in result['checkpoints']},
        'effectiveBiomeSpawns': {row['biome']: row for row in rows if row['record'] == 'effectiveBiomeSpawns'},
    }


def audit_pair(runs):
    reference = runs['balanced']
    if set(reference['effectiveBiomeSpawns']) != {'minecraft:taiga', 'minecraft:desert'}:
        raise ValueError('Expected recorded taiga and desert candidate lists')
    for variant, run in runs.items():
        if (run['seed'], run['site'], run['biome']) != (reference['seed'], reference['site'], reference['biome']):
            raise ValueError(f'Paired seed/site mismatch: {run["source"]}')
        if set(run['effectiveBiomeSpawns']) != set(reference['effectiveBiomeSpawns']):
            raise ValueError('Recorded biome sets differ within paired trial')
        for biome, record in run['effectiveBiomeSpawns'].items():
            baseline = reference['effectiveBiomeSpawns'][biome]
            entries = record['entries']
            if len(entries) != len(baseline['entries']):
                raise ValueError(f'Spawn-list lengths differ: {run["source"]}/{biome}')
            ore = [entry for entry in entries if entry['type'].startswith('oreslimes:')]
            expected_sum = {'balanced': 88, 'equal-all': 13, 'equal-ore': 91}[variant]
            if len(ore) != 13 or len({entry['type'] for entry in ore}) != 13 or sum(entry['weight'] for entry in ore) != expected_sum:
                raise ValueError(f'Unexpected Ore weights: {run["source"]}/{biome}')
            if (record['oreEntryCount'] != 13 or record['oreWeightSum'] != expected_sum
                    or record['totalWeightSum'] != sum(entry['weight'] for entry in entries)):
                raise ValueError(f'Candidate summary differs from entries: {run["source"]}/{biome}')
            for actual, original in zip(entries, baseline['entries']):
                if {k: v for k, v in actual.items() if k != 'weight'} != {k: v for k, v in original.items() if k != 'weight'}:
                    raise ValueError('Spawn type/order/group bounds changed within paired trial')
                expected = original['weight']
                if expected > 0 and variant == 'equal-all':
                    expected = 1
                elif expected > 0 and variant == 'equal-ore' and actual['type'].startswith('oreslimes:'):
                    expected = 7
                if actual['weight'] != expected:
                    raise ValueError(f'Unexpected candidate weight: {actual}')


def aggregate(runs):
    result = {key: describe([run[key] for run in runs]) for key in
              ('primaryAnimalMean', 'oreOccupiedSampleFraction', 'coexistenceSampleFraction')}
    for field in ('timeMeanCounts', 'naturalCounts'):
        result[field] = {key: describe([run[field][key] for run in runs]) for key in runs[0][field]}
    species = sorted(set().union(*(run['naturalCountsByType'] for run in runs)))
    result['naturalCountsByType'] = {key: describe([run['naturalCountsByType'].get(key, 0) for run in runs]) for key in species}
    result['oreNaturalSuccessTrialFraction'] = sum(run['naturalCounts']['oreSlime'] > 0 for run in runs) / len(runs)
    result['checkpointCounts'] = {minute: {key: describe([run['checkpointCounts'][minute][key] for run in runs])
                                          for key in COUNTS} for minute in ('10', '30', '60')}
    result['firstVerifiedNatural'] = {}
    for key in (*ANIMALS, 'oreSlime'):
        observed = [run['firstVerifiedNatural'][key]['elapsedTicks'] for run in runs if run['firstVerifiedNatural'][key]['observed']]
        result['firstVerifiedNatural'][key] = {
            'observedTrials': len(observed), 'rightCensoredTrials': len(runs) - len(observed),
            'censorBoundaryTicks': DURATION,
            'observedOnly': describe(observed) if observed else None,
            'interpretation': 'Observed-only distribution; censored trials are not substituted with 72000 or included in this mean.',
        }
    return result


def contrast(left, right, bootstrap_seed):
    differences = [a - b for a, b in zip(left, right)]
    n = len(differences)
    rng = random.Random(bootstrap_seed)
    means = [statistics.fmean(differences[rng.randrange(n)] for _ in range(n)) for _ in range(BOOTSTRAPS)]
    low, high = quantile(means, .05), quantile(means, .95)
    positive = sum(value > 0 for value in differences)
    negative = sum(value < 0 for value in differences)
    if all(value == 0 for value in left + right):
        status = 'unresolved_no_activity'
    elif low > DELTA and positive >= math.ceil(.8 * n):
        status = 'left_higher'
    elif high < -DELTA and negative >= math.ceil(.8 * n):
        status = 'right_higher'
    elif low >= -DELTA and high <= DELTA:
        status = 'small_observed_difference'
    else:
        status = 'unresolved'
    return {'differenceDirection': 'left minus right', 'pairedDifferences': differences,
            'differenceSummary': describe(differences), 'bootstrap90PercentInterval': [low, high],
            'bootstrapSeed': bootstrap_seed, 'positivePairs': positive, 'negativePairs': negative,
            'tiedPairs': n - positive - negative, 'status': status}


def analyze(through):
    if not __debug__:
        raise RuntimeError('Run without python -O: the shared audit uses assertions')
    runner = load_runner()
    groups = {terrain: {variant: [] for variant in runner.VARIANTS} for terrain in runner.TERRAINS}
    for trial in range(1, through + 1):
        for terrain in runner.TERRAINS:
            paired = {variant: measure(runner, trial, terrain, variant) for variant in runner.VARIANTS}
            audit_pair(paired)
            for variant, run in paired.items():
                groups[terrain][variant].append(run)
    comparisons = []
    for terrain in runner.TERRAINS:
        for left, right in itertools.combinations(runner.VARIANTS, 2):
            row = contrast([run['primaryAnimalMean'] for run in groups[terrain][left]],
                           [run['primaryAnimalMean'] for run in groups[terrain][right]],
                           BOOTSTRAP_SEED + len(comparisons))
            comparisons.append({'terrain': terrain, 'left': left, 'right': right, **row})
    unresolved = [row for row in comparisons if row['status'].startswith('unresolved')]
    next_stage = 10 if through == 5 else 20 if through == 10 and unresolved else None
    output = {
        'protocol': 'prepared-v2', 'through': through, 'auditedRuns': through * 9,
        'auditedSamples': through * 9 * 360,
        'policy': {'primaryMetric': 'Sample mean Rabbit+Cow+Sheep+Pig; desert uses Rabbit only.',
                   'meaningfulDifferenceAnimals': DELTA, 'bootstrapReplicates': BOOTSTRAPS,
                   'bootstrapBaseSeed': BOOTSTRAP_SEED, 'intervalPercent': 90,
                   'directionAgreementFraction': .8,
                   'interpretation': 'Exploratory descriptive intervals, not sequentially valid significance tests. All-zero activity remains unresolved. Existing default is not automatically changed.',
                   'stopRule': '5: continue to 10; 10: stop if all nine contrasts classified, otherwise 20; 20: stop and retain unresolved contrasts.'},
        'decision': {'action': 'continue' if next_stage else 'stop', 'nextThrough': next_stage,
                     'unresolvedComparisons': len(unresolved), 'maximumReached': through == 20},
        'aggregates': {terrain: {variant: aggregate(runs) for variant, runs in variants.items()} for terrain, variants in groups.items()},
        'pairedComparisons': comparisons, 'runs': groups,
    }
    destination = ROOT / 'run-phase2-series' / f'summary-{through}.json'
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix('.json.tmp')
    temporary.write_text(json.dumps(output, ensure_ascii=False, indent=2, allow_nan=False) + '\n')
    temporary.replace(destination)
    print(json.dumps({'auditedRuns': output['auditedRuns'], 'decision': output['decision'],
                      'summary': str(destination.relative_to(ROOT))}, ensure_ascii=False))
    for row in comparisons:
        print(f'{row["terrain"]} {row["left"]} - {row["right"]}: '
              f'{row["differenceSummary"]["mean"]:.3f}, 90% interval '
              f'[{row["bootstrap90PercentInterval"][0]:.3f}, {row["bootstrap90PercentInterval"][1]:.3f}], {row["status"]}')
    return output


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--through', type=int, choices=(5, 10, 20), required=True)
    args = parser.parse_args()
    try:
        analyze(args.through)
    except (AssertionError, OSError, ValueError, KeyError, StopIteration, RuntimeError) as error:
        print(f'Phase 2 analysis failed; no new summary published: {type(error).__name__}: {error}', file=sys.stderr)
        sys.exit(1)
