"""Prepare immutable APK + atomic stable.json for a static HTTPS update site.
No SSH, upload, GitHub token, private signing key or running backend is required.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
from urllib.parse import urlparse

def digest(path):
    h=hashlib.sha256()
    with Path(path).open('rb') as stream:
        for chunk in iter(lambda:stream.read(1024*1024),b''):h.update(chunk)
    return h.hexdigest()

def base_url(value):
    u=urlparse(value)
    if u.scheme!='https' or not u.hostname or u.username or u.password or u.query or u.fragment or u.port==0:
        raise ValueError('Use a public HTTPS base URL without credentials, query or fragment')
    return value.rstrip('/')

def metadata(badging,verification,apk,base,notes,package,expected_cert):
    m=re.search(r"package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']*)'",badging)
    sdk=re.search(r"sdkVersion:'([0-9]+)'",badging)
    certs=re.findall(r'Signer #[0-9]+ certificate SHA-256 digest:\s*([a-fA-F0-9]+)',verification)
    if not m or not sdk or m[1]!=package:raise ValueError('APK identity/metadata mismatch')
    expected=expected_cert.replace(':','').strip().lower()
    if not re.fullmatch('[a-f0-9]{64}',expected) or {c.lower() for c in certs}!={expected}:
        raise ValueError('APK signer differs from the expected release certificate')
    code=int(m[2]);minimum=int(sdk[1]);size=Path(apk).stat().st_size
    if not 1<=code<=2147483647 or not 1<=size<=512*1024*1024 or not 26<=minimum<=1000 or not 1<=len(m[3])<=40 or len(notes)>8000:
        raise ValueError('APK metadata exceeds client limits')
    sha=digest(apk)
    return {'schema':1,'package_name':package,'version_code':code,'version_name':m[3],
            'min_sdk':minimum,'size':size,'sha256':sha,'apk_url':base_url(base)+'/releases/'+str(code)+'-'+sha+'.apk','release_notes':notes}

def publish(apk,release,out):
    out=Path(out);out.mkdir(parents=True,exist_ok=True);lock=out/'.publish.lock'
    fd=os.open(lock,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    try:
        os.close(fd);current=out/'stable.json'
        if current.exists() and json.loads(current.read_text(encoding='utf8'))['version_code']>=release['version_code']:
            raise ValueError('New version_code must exceed the already published version')
        directory=out/'releases';directory.mkdir(exist_ok=True)
        target=directory/(str(release['version_code'])+'-'+release['sha256']+'.apk');partial=directory/(target.name+'.part')
        try:
            if target.exists():
                if digest(target)!=release['sha256']:raise ValueError('Immutable APK path already contains different bytes')
            else:
                shutil.copyfile(apk,partial)
                if partial.stat().st_size!=release['size'] or digest(partial)!=release['sha256']:raise ValueError('APK changed while copying')
                with partial.open('rb+') as stream:os.fsync(stream.fileno())
                os.replace(partial,target)
            temporary=out/'stable.json.tmp'
            with temporary.open('w',encoding='utf8') as stream:
                json.dump(release,stream,ensure_ascii=False,indent=2);stream.write('\n');stream.flush();os.fsync(stream.fileno())
            os.replace(temporary,current)
        finally:
            if partial.exists():partial.unlink()
    finally:lock.unlink()
    return current

def main():
    p=argparse.ArgumentParser();p.add_argument('--apk',type=Path,required=True);p.add_argument('--build-tools',type=Path,required=True)
    p.add_argument('--base-url',required=True);p.add_argument('--expected-cert-sha256',required=True);p.add_argument('--notes',type=Path,required=True);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--package',default='com.dafeiyu.companion');a=p.parse_args()
    windows=os.name=='nt';aapt=a.build_tools/('aapt.exe' if windows else 'aapt');signer=a.build_tools/('apksigner.bat' if windows else 'apksigner')
    badging=subprocess.check_output([str(aapt),'dump','badging',str(a.apk)],text=True,encoding='utf8')
    verification=subprocess.check_output([str(signer),'verify','--print-certs',str(a.apk)],text=True,encoding='utf8')
    release=metadata(badging,verification,a.apk,a.base_url,a.notes.read_text(encoding='utf-8-sig'),a.package,a.expected_cert_sha256)
    print(publish(a.apk,release,a.out));print('Upload releases/ first, then atomically replace stable.json on the HTTPS server.')

if __name__=='__main__':main()
