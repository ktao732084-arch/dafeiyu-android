"""One local command for GitHub + the existing static HTTPS mirror.

Default: read-only plan. --publish resumes from remote state; --verify-only checks
an existing release. Uses stdlib plus installed gh, OpenSSH, curl and Android SDK.
"""
import argparse
import base64
from contextlib import contextmanager
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
from urllib.parse import urlparse
import zipfile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('prepare_update', ROOT / 'tools/prepare-update.py')
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)


def run(args, *, data=None, timeout=120, optional=False):
    result = subprocess.run([str(a) for a in args], input=data, capture_output=True,
                            text=True, encoding='utf8', errors='replace', timeout=timeout, cwd=ROOT)
    if result.returncode:
        if optional and ('HTTP 404' in result.stderr or 'release not found' in result.stderr.lower()):
            return None
        raise RuntimeError(result.stderr.strip() or result.stdout.strip() or str(args[0]) + ' failed')
    return result.stdout


@contextmanager
def local_lock(path):
    # OS locks survive neither crashes nor Ctrl+C; no stale lock cleanup protocol.
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('a+b') as stream:
        if path.stat().st_size == 0:
            stream.write(b'0'); stream.flush()
        stream.seek(0)
        if os.name == 'nt':
            import msvcrt
            msvcrt.locking(stream.fileno(), msvcrt.LK_NBLCK, 1)
        else:
            import fcntl
            fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        try: yield
        finally:
            if os.name == 'nt':
                stream.seek(0); msvcrt.locking(stream.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                fcntl.flock(stream, fcntl.LOCK_UN)


def version(value):
    match = re.fullmatch(r'v?(\d+)\.(\d+)\.(\d+)(?:\.(\d+))?', value)
    return tuple(int(n or 0) for n in match.groups()) if match else None


def check_assets(release, artifacts):
    """Existing complete assets are immutable, including on a resumed draft."""
    existing = {a['name']: a for a in release['assets']}
    missing = []
    for path in artifacts:
        asset = existing.get(path.name)
        if asset is None or asset.get('state') != 'uploaded':
            missing.append(path)
        elif asset.get('size') != path.stat().st_size or asset.get('digest') != 'sha256:' + prepare.digest(path):
            raise ValueError('GitHub asset differs; refusing overwrite: ' + path.name)
    if missing and not release['draft']:
        raise ValueError('Published release is missing assets; refusing to modify it')
    return missing


def same_notes(a, b):
    return a.lstrip('\ufeff').replace('\r\n', '\n').strip() == b.lstrip('\ufeff').replace('\r\n', '\n').strip()


def check_validation(validation, release):
    if any(validation.get(k) != v for k, v in {'version': release['version_name'], 'version_code': release['version_code'], 'apk_sha256': release['sha256']}.items()):
        raise ValueError('APK does not match the selected commit\'s validation record')
    failures = validation.get('framework_failed_final_reports', validation.get('framework_failed'))
    if failures != 0 or any(validation.get(k, 0) <= 0 for k in ('framework_unique_passed', 'android_art_core_suites', 'android_runtime_instrumentation_checks')):
        raise ValueError('Release requires passing framework and Android validation records')


class Backend:
    def __init__(self, config, release, commit, artifacts, directory):
        self.config, self.release, self.commit = config, release, commit
        self.artifacts, self.directory = artifacts, directory
        self.tag = 'v' + release['version_name']
        self.repo = config['repository']

    def api(self, path, optional=False):
        text = run(['gh', 'api', 'repos/' + self.repo + '/' + path], optional=optional)
        return json.loads(text) if text is not None else None

    def github(self):
        view = run(['gh', 'release', 'view', self.tag, '--repo', self.repo,
                    '--json', 'databaseId'], optional=True)
        if view is None: return None
        return self.api('releases/' + str(json.loads(view)['databaseId']))

    def check_release(self, found):
        if found['draft'] and found['target_commitish'] != self.commit:
            raise ValueError('Draft targets another commit')
        if not same_notes(found.get('body') or '', self.release['release_notes']):
            raise ValueError('Release notes differ; refusing to rewrite this version')
        if found['prerelease'] != self.config['prerelease']:
            raise ValueError('Existing release channel differs')
        return check_assets(found, self.artifacts)

    def preflight(self):
        comparison = self.api('compare/' + self.commit + '...main')
        if comparison['status'] not in ('ahead', 'identical'):
            raise ValueError('Selected source commit is not merged into remote main')
        pages = json.loads(run(['gh', 'api', 'repos/' + self.repo + '/releases?per_page=100', '--paginate', '--slurp']))
        for item in (item for page in pages for item in page):
            other = version(item['tag_name'])
            if not item['draft'] and other and other > version(self.tag):
                raise ValueError('GitHub already has a newer release; use its version instead')
        reference = self.api('git/ref/tags/' + self.tag, optional=True)
        if reference:
            obj = reference['object']
            for _ in range(5):
                if obj['type'] == 'commit': break
                if obj['type'] != 'tag': raise ValueError('Tag does not reference a commit')
                obj = self.api('git/tags/' + obj['sha'])['object']
            if obj['type'] != 'commit' or obj['sha'] != self.commit:
                raise ValueError('Existing tag points to a different commit')
        found = self.github()
        if found:
            if not found['draft'] and not reference: raise ValueError('Published release tag is missing')
            self.check_release(found)
        return found, self.remote('inspect')

    def ssh_options(self):
        result = ['-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes',
                  '-o', 'ConnectTimeout=15', '-o', 'ServerAliveInterval=15', '-o', 'ServerAliveCountMax=2']
        if self.config.get('ssh_identity'):
            result += ['-i', self.config['ssh_identity'], '-o', 'IdentitiesOnly=yes']
        return result

    def remote(self, action):
        request = {'action': action, 'root': self.config['remote_dir'], 'release': self.release}
        encoded = base64.b64encode(json.dumps(request).encode()).decode()
        script = "import json,base64\nREQUEST=json.loads(base64.b64decode('" + encoded + "'))\n"
        script += (ROOT / 'tools/release-remote.py').read_text(encoding='utf8')
        return json.loads(run(['ssh'] + self.ssh_options() + [self.config['ssh_target'], 'python3 -'], data=script))

    def ensure_draft(self):
        found = self.github()
        if found is None:
            command = ['gh', 'release', 'create', self.tag, '--repo', self.repo, '--target', self.commit,
                       '--draft', '--title', self.tag + ' · 大肥鱼', '--notes-file', self.artifacts[1]]
            if self.config['prerelease']: command.append('--prerelease')
            run(command); found = self.github()
        for path in self.check_release(found):
            incomplete = next((a for a in found['assets'] if a['name'] == path.name), None)
            if incomplete:  # Only unfinished uploads on this matching draft may be removed.
                run(['gh', 'api', '--method', 'DELETE', 'repos/' + self.repo + '/releases/assets/' + str(incomplete['id'])])
            run(['gh', 'release', 'upload', self.tag, path, '--repo', self.repo], timeout=900)
        found = self.github()
        if self.check_release(found): raise ValueError('GitHub upload incomplete')
        return found

    def stage(self):
        state = self.remote('inspect')
        if not state['apk_ready']:
            filename = str(self.release['version_code']) + '-' + self.release['sha256'] + '.apk.part'
            remote = self.config['remote_dir'] + '/releases/' + filename
            local = self.artifacts[0].resolve().as_posix()
            if any(c in local for c in '\r\n"'):
                raise ValueError('Unsupported characters in upload path')
            batch = self.directory / 'upload.sftp'
            command = 'put' if state['partial_size'] is None else 'reput'
            batch.write_text(command + ' "' + local + '" "' + remote + '"\n', encoding='utf8')
            run(['sftp', '-b', batch] + self.ssh_options() + [self.config['ssh_target']], timeout=1800)
        self.remote('stage')

    def fetch(self, url, destination, *, resume=False, limit=65536):
        command = ['curl', '--noproxy', urlparse(url).hostname, '--proto', '=https', '--max-time', '900',
                   '--retry', '2', '--retry-all-errors', '--retry-delay', '2', '--max-filesize', str(limit),
                   '--fail', '--silent', '--show-error', '--output', destination]
        if resume and destination.exists(): command += ['--continue-at', '-']
        run(command + [url], timeout=2800)

    def verify_download(self):
        downloaded = self.directory / ('public-' + self.release['sha256'] + '.apk')
        if not downloaded.exists() or prepare.digest(downloaded) != self.release['sha256']:
            if downloaded.exists() and downloaded.stat().st_size >= self.release['size']: downloaded.unlink()
            self.fetch(self.release['apk_url'], downloaded, resume=True, limit=self.release['size'])
        if downloaded.stat().st_size != self.release['size'] or prepare.digest(downloaded) != self.release['sha256']:
            raise ValueError('Public HTTPS APK differs from signed artifact')

    def publish_github(self):
        found = self.github()
        if found is None or self.check_release(found): raise ValueError('GitHub is not ready')
        if found['draft']:
            run(['gh', 'release', 'edit', self.tag, '--repo', self.repo, '--draft=false'])
        if self.github()['draft']: raise ValueError('GitHub release is still a draft')

    def verify_live(self):
        found = self.github()
        if found is None or found['draft'] or self.check_release(found):
            raise ValueError('GitHub release is not fully published')
        current = self.directory / 'public-stable.json'
        self.fetch(self.config['base_url'] + '/stable.json', current)
        if json.loads(current.read_text(encoding='utf8')) != self.release:
            raise ValueError('Live mirror manifest differs; rerun to finish sync')


def synchronize(backend, mode):
    github, mirror = backend.preflight()
    ready = github is not None and not github['draft'] and mirror['apk_ready'] and mirror['current'] == backend.release
    if mode == 'plan':
        return 'Already synchronized; verification remains' if ready else 'Plan: draft/assets -> mirror APK -> HTTPS digest -> publish GitHub -> atomic manifest'
    if mode == 'verify':
        if not ready: raise ValueError('Both destinations must already be published for verify-only')
    elif not ready:
        print('1/4 GitHub draft and asset digests', flush=True); backend.ensure_draft()
        print('2/4 Mirror APK upload/resume and server digest', flush=True); backend.stage()
        print('3/4 Full public HTTPS APK verification', flush=True); backend.verify_download()
        # A crash here may leave only GitHub published. Never claim a cross-service transaction.
        print('4/4 Publish GitHub, then atomically switch mirror manifest', flush=True)
        backend.publish_github(); backend.remote('commit')
    else:
        print('Both destinations already match; no remote writes', flush=True)
    backend.verify_download(); backend.verify_live()
    return 'Verified: GitHub and domestic HTTPS mirror match'


def prepare_inputs(args, config):
    if config.get('jdk'): os.environ['JAVA_HOME'] = config['jdk']
    for command in ['git', 'gh', 'ssh', 'sftp', 'curl']:
        if not shutil.which(command): raise ValueError('Required command missing: ' + command)
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', config['repository']): raise ValueError('Invalid repository')
    if not re.fullmatch(r'(?:[A-Za-z0-9_]+@)?[A-Za-z0-9][A-Za-z0-9_.-]*', config['ssh_target']): raise ValueError('Invalid SSH target')
    if not re.fullmatch(r'/[A-Za-z0-9_/-]+', config['remote_dir']) or '..' in Path(config['remote_dir']).parts: raise ValueError('Invalid remote directory')
    config['base_url'] = prepare.base_url(config['base_url'])
    apk, notes = Path(args.apk).resolve(), Path(args.notes).resolve()
    bt = Path(config['build_tools']); windows = os.name == 'nt'
    badging = run([bt / ('aapt.exe' if windows else 'aapt'), 'dump', 'badging', apk])
    signature = run([bt / ('apksigner.bat' if windows else 'apksigner'), 'verify', '--print-certs', apk])
    release = prepare.metadata(badging, signature, apk, config['base_url'], notes.read_text(encoding='utf-8-sig'), config['package'], config['signer_sha256'])
    if not re.fullmatch(r'\d+\.\d+\.\d+\.\d+', release['version_name']): raise ValueError('Use the four-part release version policy')
    with zipfile.ZipFile(apk) as archive:
        if json.loads(archive.read('assets/update-channel.json'))['manifest_url'] != config['base_url'] + '/stable.json':
            raise ValueError('APK does not embed the configured update endpoint')
    commit = run(['git', 'rev-parse', '--verify', '--end-of-options', args.commit + '^{commit}']).strip()
    validation = json.loads(run(['git', 'show', commit + ':docs/test-reports/validation-result.json']))
    check_validation(validation, release)
    directory = ROOT / 'build/release-sync' / ('v' + release['version_name'])
    directory.mkdir(parents=True, exist_ok=True)
    published_apk = directory / ('dafeiyu-android-' + release['version_name'] + '.apk')
    published_notes = directory / ('README-' + release['version_name'] + '.md')
    sums = directory / ('SHA256SUMS-' + release['version_name'] + '.txt')
    shutil.copyfile(apk, published_apk) if apk != published_apk else None
    shutil.copyfile(notes, published_notes) if notes != published_notes else None
    if prepare.digest(published_apk) != release['sha256']: raise ValueError('APK changed while preparing release')
    sums.write_text(''.join(prepare.digest(p) + '  ' + p.name + '\n' for p in (published_apk, published_notes)), encoding='utf8')
    (directory / 'stable.json').write_text(json.dumps(release, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    return Backend(config, release, commit, [published_apk, published_notes, sums], directory)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', required=True); parser.add_argument('--notes', required=True)
    parser.add_argument('--commit', default='HEAD')
    parser.add_argument('--local-config', type=Path, default=ROOT / 'deploy/updates/release.local.json')
    group = parser.add_mutually_exclusive_group(); group.add_argument('--publish', action='store_true'); group.add_argument('--verify-only', action='store_true')
    args = parser.parse_args()
    backend = None
    try:
        config = json.loads((ROOT / 'deploy/updates/release.json').read_text(encoding='utf8'))
        local = json.loads(args.local_config.read_text(encoding='utf-8-sig'))
        if set(local) - {'build_tools', 'jdk', 'ssh_target', 'ssh_identity'}: raise ValueError('Unexpected local config fields')
        config.update(local)
        with local_lock(ROOT / 'build/release-sync/.lock'):
            backend = prepare_inputs(args, config)
            (backend.directory / 'receipt.json').write_text(json.dumps({'result': 'In progress', 'commit': backend.commit}) + '\n', encoding='utf8')
            print(backend.tag + ' | ' + backend.commit + ' | SHA-256 ' + backend.release['sha256'], flush=True)
            message = synchronize(backend, 'publish' if args.publish else 'verify' if args.verify_only else 'plan')
            receipt = {'mode': 'publish' if args.publish else 'verify' if args.verify_only else 'plan', 'result': message,
                       'commit': backend.commit, 'version': backend.release['version_name'], 'sha256': backend.release['sha256']}
            (backend.directory / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf8')
            print(message, flush=True)
    except Exception as error:
        if backend is not None:
            (backend.directory / 'receipt.json').write_text(json.dumps({'result': 'NOT COMPLETE', 'error': str(error), 'commit': backend.commit}) + '\n', encoding='utf8')
        print('NOT COMPLETE: ' + str(error) + '\nRerun the same inputs after fixing the error; published assets are never overwritten.', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
