import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('publisher',Path(__file__).resolve().parents[1]/'tools/prepare-update.py')
publisher=importlib.util.module_from_spec(spec);spec.loader.exec_module(publisher)

class PublishTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name);self.apk=self.root/'source.apk';self.apk.write_bytes(b'fixture-apk')
        self.cert='ab'*32
        self.badging="package: name='com.dafeiyu.companion' versionCode='11' versionName='0.4.7'\nsdkVersion:'26'"
        self.verify='Signer #1 certificate SHA-256 digest: '+self.cert
    def release(self):return publisher.metadata(self.badging,self.verify,self.apk,'https://updates.example.test','修复问题','com.dafeiyu.companion',self.cert)
    def test_actual_bytes_determine_hash_size_and_immutable_name(self):
        r=self.release();self.assertEqual(len(b'fixture-apk'),r['size']);self.assertEqual(publisher.digest(self.apk),r['sha256']);self.assertIn(r['sha256'],r['apk_url'])
    def test_signed_metadata_must_match_expected_identity(self):
        with self.assertRaises(ValueError):publisher.metadata(self.badging,self.verify,self.apk,'https://updates.example.test','','other.app',self.cert)
        with self.assertRaises(ValueError):publisher.metadata(self.badging,self.verify,self.apk,'https://updates.example.test','','com.dafeiyu.companion','cd'*32)
    def test_https_origin_validation(self):
        for url in ['http://example.test','https://user:pass@example.test','https://example.test/#x','https://example.test/?key=x','https://example.test:0']:
            with self.assertRaises(ValueError):publisher.base_url(url)
    def test_ip_https_site_retains_its_dedicated_path(self):
        r=publisher.metadata(self.badging,self.verify,self.apk,'https://192.0.2.1/dafeiyu-updates/','','com.dafeiyu.companion',self.cert)
        self.assertEqual('https://192.0.2.1/dafeiyu-updates/releases/11-'+publisher.digest(self.apk)+'.apk',r['apk_url'])
    def test_apk_is_present_before_manifest_and_older_release_cannot_replace_it(self):
        out=self.root/'site';release=self.release();path=publisher.publish(self.apk,release,out);saved=json.loads(path.read_text(encoding='utf8'));self.assertEqual(release,saved)
        self.assertEqual(1,len(list((out/'releases').glob('*.apk'))))
        with self.assertRaises(ValueError):publisher.publish(self.apk,release,out)
        self.assertEqual(release,json.loads(path.read_text(encoding='utf8')));self.assertFalse((out/'.publish.lock').exists())
    def test_changed_apk_never_publishes_manifest(self):
        release=self.release();self.apk.write_bytes(b'changed');out=self.root/'site'
        with self.assertRaises(ValueError):publisher.publish(self.apk,release,out)
        self.assertFalse((out/'stable.json').exists());self.assertFalse(list((out/'releases').glob('*.part')))
    def test_another_publish_lock_is_not_removed(self):
        out=self.root/'site';out.mkdir();(out/'.publish.lock').write_text('other publisher')
        with self.assertRaises(FileExistsError):publisher.publish(self.apk,self.release(),out)
        self.assertEqual('other publisher',(out/'.publish.lock').read_text())

if __name__=='__main__':unittest.main()
