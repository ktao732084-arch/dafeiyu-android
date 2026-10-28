import pathlib,urllib.request,zipfile,concurrent.futures,hashlib,sys
root=pathlib.Path(__file__).resolve().parent
(root/'libs').mkdir(parents=True,exist_ok=True)
def fetch(task):
 name,url,kind=task;dst=root/name
 if not dst.exists(): urllib.request.urlretrieve(url,dst)
 with zipfile.ZipFile(dst) as z:
  if kind=='maven':z.extractall(root)
  else:(root/'libs'/f'{kind}.jar').write_bytes(z.read('classes.jar'))
 print(name+' ready',flush=True)
tasks=[
 ('monitor.aar','https://dl.google.com/dl/android/maven2/androidx/test/monitor/1.7.1/monitor-1.7.1.aar','monitor'),
 ('espresso.aar','https://dl.google.com/dl/android/maven2/androidx/test/espresso/espresso-idling-resource/3.6.1/espresso-idling-resource-3.6.1.aar','espresso')]
if '--skip-maven' not in sys.argv:tasks.append(('maven.zip','https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip','maven'))
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as ex:list(ex.map(fetch,tasks))
d=root/'android-runtime';d.mkdir(exist_ok=True)
n='android-all-instrumented-14-robolectric-10818077-i6.jar'
url='https://repo1.maven.org/maven2/org/robolectric/android-all-instrumented/14-robolectric-10818077-i6/'+n
if not (d/n).exists():urllib.request.urlretrieve(url,d/n)
expected=urllib.request.urlopen(url+'.sha512').read().decode().split()[0]
assert hashlib.sha512((d/n).read_bytes()).hexdigest()==expected,'Android runtime checksum mismatch'
print('Android 14 Robolectric runtime ready; SHA-512 verified',flush=True)
