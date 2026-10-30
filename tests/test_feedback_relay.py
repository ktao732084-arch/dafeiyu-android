import http.server
import importlib.util
import json
from pathlib import Path
import threading
import unittest
import urllib.error
import urllib.request

spec=importlib.util.spec_from_file_location('relay',Path(__file__).resolve().parents[1]/'deploy/feedback/feedback_relay.py')
relay=importlib.util.module_from_spec(spec);spec.loader.exec_module(relay)

ID='0f8fad5b-d9cb-469f-a165-70867728950e'
def report(**over):
    r={'id':ID,'text':'聊天页打不开\n点了没反应','contact':'qq 123','app_version':'0.4.9.5','version_code':19,'android':'14（API 34）','device':'Xiaomi 23127PN0CC'};r.update(over);return r

class Clock:
    def __init__(self):self.now=1_000_000.0
    def __call__(self):return self.now

class RelayTests(unittest.TestCase):
    def setUp(self):
        self.filed=[];self.clock=Clock()
        self.relay=relay.Relay(lambda issue:(self.filed.append(issue),len(self.filed))[1],self.clock)
    def test_files_one_issue_with_text_contact_and_versions_only(self):
        self.assertEqual(self.relay.submit('1.2.3.4',report(extra='ignored')),'#1')
        issue=self.filed[0];self.assertEqual(issue['title'],'[反馈] 聊天页打不开')
        for part in ('点了没反应','qq 123','0.4.9.5 (code 19)','14（API 34）','Xiaomi 23127PN0CC',ID):self.assertIn(part,issue['body'])
        self.assertNotIn('ignored',issue['body'])
    def test_a_retried_id_returns_the_same_receipt_without_a_second_issue(self):
        self.relay.submit('1.2.3.4',report());self.assertEqual(self.relay.submit('1.2.3.4',report()),'#1');self.assertEqual(len(self.filed),1)
    def test_text_cannot_escape_its_code_fence(self):
        body=relay.issue(relay.validate(report(text='hi ```` @someone ![x](http://e/x.png)')))['body']
        self.assertIn('`````text\nhi ```` @someone',body)
    def test_bad_reports_are_rejected(self):
        for bad in (report(text='  '),report(text='字'*2001),report(contact='c'*101),report(id='1'),report(text=5),report(device='d'*81),[]):
            with self.assertRaises(relay.Rejected) as caught:self.relay.submit('1.2.3.4',bad)
            self.assertEqual(caught.exception.status,400)
        self.assertEqual(self.filed,[])
    def test_control_characters_are_dropped_and_odd_codes_ignored(self):
        clean=relay.validate(report(text='a\x00b\x1bc\n第二行',version_code=True))
        self.assertEqual(clean['text'],'abc\n第二行');self.assertEqual(clean['version_code'],0)
    def test_rate_limits_per_client_per_hour_and_day(self):
        ids=iter('%08x-0000-4000-8000-000000000000'%i for i in range(100))
        for _ in range(relay.PER_HOUR):self.relay.submit('a',report(id=next(ids)))
        with self.assertRaises(relay.Rejected) as caught:self.relay.submit('a',report(id=next(ids)))
        self.assertEqual(caught.exception.status,429);self.relay.submit('b',report(id=next(ids)))
        for hour in range(1,4):
            self.clock.now+=3600
            for _ in range(relay.PER_HOUR):self.relay.submit('a',report(id=next(ids)))
        with self.assertRaises(relay.Rejected):self.relay.submit('a',report(id=next(ids)))
        self.clock.now+=86400;self.relay.submit('a',report(id=next(ids)))
    def test_upstream_failure_is_a_502_without_details(self):
        def broken(issue):raise OSError('token=secret')
        with self.assertRaises(relay.Rejected) as caught:relay.Relay(broken,self.clock).submit('a',report())
        self.assertEqual(caught.exception.status,502);self.assertNotIn('secret',str(caught.exception))

class HttpTests(unittest.TestCase):
    def setUp(self):
        self.filed=[];r=relay.Relay(lambda issue:(self.filed.append(issue),7)[1])
        handler=relay.handler_for(r);handler.log_message=lambda *a:None
        self.server=http.server.ThreadingHTTPServer(('127.0.0.1',0),handler);threading.Thread(target=self.server.serve_forever,daemon=True).start()
        self.url='http://127.0.0.1:%d'%self.server.server_address[1]
    def tearDown(self):self.server.shutdown();self.server.server_close()
    def post(self,path,data,kind='application/json; charset=utf-8'):
        request=urllib.request.Request(self.url+path,data,{'Content-Type':kind},method='POST')
        try:
            with urllib.request.urlopen(request,timeout=5) as response:return response.status,json.loads(response.read())
        except urllib.error.HTTPError as error:return error.code,json.loads(error.read())
    def test_round_trip(self):
        self.assertEqual(self.post('/submit',json.dumps(report()).encode()),(200,{'ok':True,'receipt':'#7'}))
        self.assertEqual(len(self.filed),1)
    def test_wrong_path_type_size_or_json(self):
        self.assertEqual(self.post('/other',b'{}')[0],404)
        self.assertEqual(self.post('/submit',b'{}','text/plain')[0],400)
        self.assertEqual(self.post('/submit',b'x'*(relay.MAX_BODY+1))[0],413)
        self.assertEqual(self.post('/submit',b'{not json')[0],400)
        self.assertEqual(self.filed,[])

if __name__=='__main__':unittest.main()
