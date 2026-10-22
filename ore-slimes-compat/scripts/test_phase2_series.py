"""Regression checks for resuming observations without launching Minecraft."""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch


spec = importlib.util.spec_from_file_location('phase2_series', Path(__file__).with_name('phase2-series.py'))
series = importlib.util.module_from_spec(spec)
spec.loader.exec_module(series)


class ResumeTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.enterContext(patch.object(series, 'ROOT', Path(temporary.name)))
        self.enterContext(patch.object(series, 'VARIANTS', ('balanced',)))
        self.enterContext(patch.object(series, 'TERRAINS', ('normal',)))
        self.enterContext(patch.object(series.time, 'sleep'))
        self.output = self.enterContext(contextlib.redirect_stdout(io.StringIO()))
        self.key = (1, 'normal', 'balanced')
        self.path = series.directory(*self.key)
        self.path.mkdir()
        self.marker = self.path / 'phase2-result.txt'
        self.audit = self.enterContext(patch.object(series, 'audit', side_effect=self.audit_complete))
        self.launch = self.enterContext(patch.object(series.subprocess, 'Popen', side_effect=self.complete_run))

    def audit_complete(self, *key):
        self.assertEqual(key, self.key)
        self.assertEqual(self.marker.read_text().strip(), 'COMPLETE')

    def complete_run(self, *args, **kwargs):
        self.marker.write_text('COMPLETE\n')
        return SimpleNamespace(poll=lambda: 0)

    def assert_retried(self):
        series.run(through=1, jobs=1)
        self.launch.assert_called_once()
        self.audit.assert_called_once_with(*self.key)
        events = [json.loads(line)['event'] for line in self.output.getvalue().splitlines()]
        self.assertEqual(events, ['start', 'complete'])

    def test_failed_observation_is_retried_and_audited_after_completion(self):
        self.marker.write_text('FAIL chunks not ready\n')
        self.assert_retried()

    def test_missing_marker_is_run(self):
        self.assert_retried()

    def test_empty_marker_is_retried(self):
        self.marker.write_text('')
        self.assert_retried()

    def test_complete_observation_is_audited_and_reused(self):
        self.marker.write_text('COMPLETE\n')
        evidence = self.path / 'phase2.jsonl'
        evidence.write_text('existing evidence\n')
        series.run(through=1, jobs=1)
        self.launch.assert_not_called()
        self.audit.assert_called_once_with(*self.key)
        self.assertEqual(evidence.read_text(), 'existing evidence\n')
        self.assertEqual(json.loads(self.output.getvalue())['event'], 'reuse')

    def test_complete_observation_with_invalid_evidence_still_stops(self):
        self.marker.write_text('COMPLETE\n')
        self.audit.side_effect = ValueError('invalid evidence')
        with self.assertRaisesRegex(ValueError, 'invalid evidence'):
            series.run(through=1, jobs=1)
        self.launch.assert_not_called()


if __name__ == '__main__':
    unittest.main()
