"""Sent over existing SSH; no daemon, credentials, dependencies or installation.

Only the caller sets REQUEST (a decoded JSON object). Writes are serialized with
an OS lock, released even when SSH disconnects. Same-version changes fail closed.
"""
import hashlib
import json
import os
from pathlib import Path
import re


def digest(path):
    result = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            result.update(chunk)
    return result.hexdigest()


def guard(current, release):
    if current is not None:
        if current['version_code'] > release['version_code']:
            raise ValueError('Server already has a newer version; refusing rollback')
        if current['version_code'] == release['version_code'] and current != release:
            raise ValueError('Same version_code has different metadata; refusing overwrite')


def save(path, value):
    temporary = path.with_name('.' + path.name + '.sync-tmp')
    with temporary.open('w', encoding='utf8') as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2)
        stream.write('\n'); stream.flush(); os.fsync(stream.fileno())
    temporary.chmod(0o644)
    os.replace(temporary, path)


def execute(request):
    root = Path(request['root'])
    release = request['release']
    code, sha = release['version_code'], release['sha256']
    if type(code) is not int or code < 1 or not re.fullmatch('[a-f0-9]{64}', sha):
        raise ValueError('Invalid release identity')
    if not root.is_absolute() or '..' in root.parts or not root.is_dir() or root.is_symlink():
        raise ValueError('Configured update directory must already exist without symlink')
    target = root / 'releases' / (str(code) + '-' + sha + '.apk')
    partial = target.with_name(target.name + '.part')
    stable = root / 'stable.json'
    for path in (root / 'releases', target, partial, stable):
        if path.is_symlink():
            raise ValueError('Update paths must not be symlinks')
    current = json.loads(stable.read_text(encoding='utf8')) if stable.exists() else None
    guard(current, release)
    valid = target.is_file() and target.stat().st_size == release['size'] and digest(target) == sha
    if target.exists() and not valid:
        raise ValueError('Immutable APK path has different bytes')
    action = request['action']
    if action == 'inspect':
        partial_size = partial.stat().st_size if partial.is_file() else None
        if partial_size is not None and partial_size > release['size']:
            raise ValueError('Upload partial is oversized; inspect and remove only that partial')
        return {'current': current, 'apk_ready': valid, 'partial_size': partial_size}
    if action == 'stage':
        if not valid:
            if not partial.is_file() or partial.stat().st_size != release['size'] or digest(partial) != sha:
                raise ValueError('Incomplete or corrupt upload; stable.json unchanged')
            with partial.open('rb+') as stream:
                os.fsync(stream.fileno())
            partial.chmod(0o644); os.replace(partial, target)
        return {'apk_ready': True}
    if action != 'commit' or not valid:
        raise ValueError('A fully verified APK is required before committing manifest')
    if current == release:
        return {'current': current, 'changed': False}
    if current is not None:
        save(root / 'stable.previous.json', current)
    save(stable, release)
    if os.name == 'posix':
        fd = os.open(root, os.O_RDONLY)
        try: os.fsync(fd)
        finally: os.close(fd)
    return {'current': release, 'changed': True}


def main(request):
    if request['action'] == 'inspect':
        result = execute(request)
    else:
        import fcntl
        root = Path(request['root'])
        if not root.is_absolute() or '..' in root.parts or not root.is_dir() or root.is_symlink():
            raise ValueError('Invalid update root')
        lock_path = root / '.release-sync.lock'
        if lock_path.is_symlink(): raise ValueError('Invalid update lock')
        with lock_path.open('a') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            result = execute(request)
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    main(REQUEST)
