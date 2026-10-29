import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).resolve().parents[1] / 'tools' / filename)
    result = importlib.util.module_from_spec(spec); spec.loader.exec_module(result)
    return result


sync = module('release_sync', 'release-sync.py')
remote = module('release_remote', 'release-remote.py')


class MirrorTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name); (self.root / 'releases').mkdir()
        self.data = b'signed fixture bytes'
        source = self.root / 'fixture'; source.write_bytes(self.data)
        self.release = {'version_code': 16, 'version_name': '0.4.9.3', 'size': len(self.data),
                        'sha256': remote.digest(source), 'release_notes': 'new version'}
        self.old = dict(self.release, version_code=15, version_name='0.4.9.2')
        (self.root / 'stable.json').write_text(json.dumps(self.old), encoding='utf8')
        self.target = self.root / 'releases' / ('16-' + self.release['sha256'] + '.apk')
        self.partial = self.target.with_name(self.target.name + '.part')

    def call(self, action):
        return remote.execute({'action': action, 'root': str(self.root), 'release': self.release})

    def current(self):
        return json.loads((self.root / 'stable.json').read_text(encoding='utf8'))

    def test_partial_or_corrupt_upload_cannot_publish(self):
        for data in [b'', b'partial', b'x' * len(self.data)]:
            self.partial.write_bytes(data)
            with self.assertRaises(ValueError): self.call('stage')
            with self.assertRaises(ValueError): self.call('commit')
            self.assertEqual(self.old, self.current())

    def test_first_upload_and_resumable_partial_are_distinguished(self):
        self.assertIsNone(self.call('inspect')['partial_size'])
        self.partial.write_bytes(self.data[:5])
        self.assertEqual(5, self.call('inspect')['partial_size'])
        self.partial.write_bytes(self.data + b'oversized')
        with self.assertRaises(ValueError): self.call('inspect')

    def test_stage_preserves_manifest_and_commit_is_idempotent(self):
        self.partial.write_bytes(self.data); self.call('stage')
        self.assertEqual(self.data, self.target.read_bytes()); self.assertEqual(self.old, self.current())
        self.assertTrue(self.call('commit')['changed'])
        self.assertEqual(self.release, self.current())
        previous = json.loads((self.root / 'stable.previous.json').read_text(encoding='utf8'))
        self.assertEqual(self.old, previous)
        self.assertFalse(self.call('commit')['changed']); self.assertFalse(self.partial.exists())

    def test_newer_server_or_same_code_different_notes_is_never_overwritten(self):
        for state in [dict(self.release, version_code=17), dict(self.release, release_notes='another release')]:
            (self.root / 'stable.json').write_text(json.dumps(state), encoding='utf8')
            with self.assertRaises(ValueError): self.call('inspect')
            with self.assertRaises(ValueError): self.call('commit')
            self.assertEqual(state, self.current())

    def test_concurrent_newer_commit_is_rechecked(self):
        self.partial.write_bytes(self.data); self.call('stage')
        newer = dict(self.release, version_code=17)
        (self.root / 'stable.json').write_text(json.dumps(newer), encoding='utf8')
        with self.assertRaises(ValueError): self.call('commit')
        self.assertEqual(newer, self.current())

    def test_existing_immutable_file_is_not_overwritten(self):
        self.target.write_bytes(b'wrong bytes'); self.partial.write_bytes(self.data)
        with self.assertRaises(ValueError): self.call('stage')
        self.assertEqual(b'wrong bytes', self.target.read_bytes())

    def test_manifest_replace_failure_preserves_previous_version(self):
        self.partial.write_bytes(self.data); self.call('stage')
        replace = remote.os.replace
        def fail_stable(source, dest):
            if Path(dest).name == 'stable.json': raise OSError('simulated filesystem interruption')
            return replace(source, dest)
        with patch.object(remote.os, 'replace', side_effect=fail_stable):
            with self.assertRaises(OSError): self.call('commit')
        self.assertEqual(self.old, self.current()); self.call('commit')
        self.assertEqual(self.release, self.current())


class FakeBackend:
    def __init__(self):
        self.release = {'version_code': 16, 'sha256': 'abc'}
        self.github_state = None; self.current = {'version_code': 15}; self.apk_ready = False
        self.events = []; self.fail_at = None; self.public_bytes_ok = True
    def event(self, name):
        self.events.append(name)
        if self.fail_at == name: raise RuntimeError('interruption at ' + name)
    def preflight(self):
        self.event('inspect')
        remote.guard(self.current, self.release)
        return self.github_state, {'current': self.current, 'apk_ready': self.apk_ready}
    def ensure_draft(self):
        self.event('draft')
        if self.github_state is None: self.github_state = {'draft': True}
    def stage(self): self.event('stage'); self.apk_ready = True
    def verify_download(self):
        self.event('verify-bytes')
        if not self.public_bytes_ok: raise ValueError('HTTPS bytes mismatch')
    def publish_github(self): self.event('publish-github'); self.github_state['draft'] = False
    def remote(self, action):
        self.event(action); remote.guard(self.current, self.release)
        self.current = copy.deepcopy(self.release)
    def verify_live(self):
        self.event('verify-live')
        if self.current != self.release or self.github_state['draft']: raise ValueError('Mismatch')


class PipelineTests(unittest.TestCase):
    def test_plan_and_verify_do_not_write(self):
        backend = FakeBackend(); sync.synchronize(backend, 'plan')
        self.assertEqual(['inspect'], backend.events)
        with self.assertRaises(ValueError): sync.synchronize(backend, 'verify')
        self.assertIsNone(backend.github_state)
    def test_order_and_identical_rerun_do_not_republish(self):
        backend = FakeBackend(); sync.synchronize(backend, 'publish')
        self.assertLess(backend.events.index('verify-bytes'), backend.events.index('publish-github'))
        self.assertLess(backend.events.index('publish-github'), backend.events.index('commit'))
        backend.events.clear(); sync.synchronize(backend, 'publish')
        self.assertEqual(['inspect', 'verify-bytes', 'verify-live'], backend.events)
    def test_interrupted_mirror_commit_resumes_after_github_is_public(self):
        backend = FakeBackend(); backend.fail_at = 'commit'
        with self.assertRaises(RuntimeError): sync.synchronize(backend, 'publish')
        self.assertFalse(backend.github_state['draft']); self.assertEqual(15, backend.current['version_code'])
        backend.fail_at = None; sync.synchronize(backend, 'publish')
        self.assertEqual(backend.release, backend.current)
    def test_failed_upload_or_public_digest_does_not_publish(self):
        for failure in ['stage', 'verify-bytes', 'publish-github']:
            backend = FakeBackend(); backend.fail_at = failure
            with self.assertRaises(RuntimeError): sync.synchronize(backend, 'publish')
            self.assertEqual(15, backend.current['version_code']); self.assertTrue(backend.github_state['draft'])
    def test_corrupt_public_download_blocks_both_publication_steps(self):
        backend = FakeBackend(); backend.public_bytes_ok = False
        with self.assertRaises(ValueError): sync.synchronize(backend, 'publish')
        self.assertNotIn('publish-github', backend.events); self.assertNotIn('commit', backend.events)
    def test_newer_mirror_blocks_before_github_writes(self):
        backend = FakeBackend(); backend.current = {'version_code': 17}
        with self.assertRaises(ValueError): sync.synchronize(backend, 'publish')
        self.assertIsNone(backend.github_state)


class AssetTests(unittest.TestCase):
    def test_validation_is_bound_to_the_exact_tested_apk_and_passed_checks(self):
        release = {'version_name': '0.4.9.3', 'version_code': 16, 'sha256': 'a' * 64}
        record = {'version': '0.4.9.3', 'version_code': 16, 'apk_sha256': 'a' * 64, 'framework_unique_passed': 119,
                  'framework_failed_final_reports': 0, 'android_art_core_suites': 15, 'android_runtime_instrumentation_checks': 47}
        sync.check_validation(record, release)
        for field, value in [('apk_sha256', 'b' * 64), ('framework_failed_final_reports', 1), ('android_art_core_suites', 0)]:
            with self.assertRaises(ValueError): sync.check_validation(dict(record, **{field: value}), release)
    def test_racing_draft_with_another_commit_is_not_adopted(self):
        backend = sync.Backend({'repository': 'owner/repo', 'prerelease': True}, {'version_name': '0.4.9.3', 'release_notes': 'notes'}, 'expected', [], Path('.'))
        draft = {'draft': True, 'target_commitish': 'other', 'body': 'notes', 'prerelease': True, 'assets': []}
        with self.assertRaises(ValueError): backend.check_release(draft)
    def test_existing_asset_digest_and_state_are_enforced(self):
        with tempfile.TemporaryDirectory() as folder:
            artifact = Path(folder) / 'app.apk'; artifact.write_bytes(b'original')
            asset = {'name': artifact.name, 'state': 'uploaded', 'size': 8, 'digest': 'sha256:' + sync.prepare.digest(artifact)}
            release = {'draft': False, 'assets': [asset]}
            self.assertEqual([], sync.check_assets(release, [artifact]))
            artifact.write_bytes(b'modified')
            with self.assertRaises(ValueError): sync.check_assets(release, [artifact])
            release['assets'] = []
            with self.assertRaises(ValueError): sync.check_assets(release, [artifact])
            release['draft'] = True
            self.assertEqual([artifact], sync.check_assets(release, [artifact]))
    def test_incomplete_draft_upload_is_retryable_but_public_upload_is_not(self):
        with tempfile.TemporaryDirectory() as folder:
            artifact = Path(folder) / 'app.apk'; artifact.write_bytes(b'original')
            release = {'draft': True, 'assets': [{'name': artifact.name, 'state': 'starter'}]}
            self.assertEqual([artifact], sync.check_assets(release, [artifact]))
            release['draft'] = False
            with self.assertRaises(ValueError): sync.check_assets(release, [artifact])
    def test_historical_versions_compare_with_four_part_versions(self):
        self.assertLess(sync.version('v0.4.9'), sync.version('v0.4.9.1'))
        self.assertLess(sync.version('v0.4.9.9'), sync.version('v0.4.10.0'))
        self.assertIsNone(sync.version('unrelated-tag'))


if __name__ == '__main__': unittest.main()
