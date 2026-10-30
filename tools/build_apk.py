from pathlib import Path
import os, subprocess, zipfile, shutil, re, sys, json, secrets, xml.etree.ElementTree as ET
root=Path.cwd()/'Fit100Android'
out=root/'build'/'offline-package'
sdk=Path(os.environ.get('ANDROID_HOME', str(Path.home()/'AppData/Local/Android/Sdk')))
bt=sdk/'build-tools'/'35.0.0'
android=sdk/'platforms/android-35/android.jar'
cache=Path.home()/'.gradle/caches/modules-2/files-2.1'
compiler=[]
for group in ['org.jetbrains.kotlin','org.jetbrains.intellij.deps','org.jetbrains.kotlinx','org.jetbrains/annotations']:
 compiler.extend((cache/group).rglob('*.jar'))
stdlib=next((cache/'org.jetbrains.kotlin/kotlin-stdlib/2.0.21').rglob('*.jar'))
def run(args):
 print('Running', Path(str(args[0])).name, flush=True)
 subprocess.run([str(a) for a in args], check=True)
classes=out/'classes'
assert classes.resolve().is_relative_to(out.resolve())
if classes.exists(): shutil.rmtree(classes)
classes.mkdir(exist_ok=True)
run(['java','-cp',os.pathsep.join(map(str,compiler)), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','17','-classpath',str(android)+os.pathsep+str(stdlib),'-d',classes,*sorted((root/'app/src/main/java').rglob('*.kt'))])
with zipfile.ZipFile(out/'classes.jar','w',zipfile.ZIP_DEFLATED) as archive:
 for file in classes.rglob('*.class'): archive.write(file,file.relative_to(classes).as_posix())
ns='http://schemas.android.com/apk/res/android'
ET.register_namespace('android',ns)
manifest=ET.parse(root/'app/src/main/AndroidManifest.xml')
m=manifest.getroot()
m.set('package','com.fit100.tracker')
config = (root/'app/build.gradle.kts').read_text()
version_code = re.search(r'versionCode\s*=\s*(\d+)', config).group(1)
version_name = re.search(r'versionName\s*=\s*"([^"]+)"', config).group(1)
m.set('{'+ns+'}versionCode',version_code)
m.set('{'+ns+'}versionName',version_name)
m.insert(0, ET.Element('uses-sdk',{'{'+ns+'}minSdkVersion':'26','{'+ns+'}targetSdkVersion':'35'}))
m.find('application').set('{'+ns+'}debuggable','true' if '--qa' in sys.argv else 'false')
manifest.write(out/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
run([bt/'aapt2.exe','compile','--dir',root/'app/src/main/res','-o',out/'resources.zip'])
run([bt/'aapt2.exe','link','-o',out/'unsigned.apk','--manifest',out/'AndroidManifest.xml','-I',android,out/'resources.zip'])
run(['java','-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--min-api','26','--lib',android,'--output',out,out/'classes.jar',stdlib])
with zipfile.ZipFile(out/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as archive:
 for dex in out.glob('classes*.dex'): archive.write(dex,dex.name)
run([bt/'zipalign.exe','-f','4',out/'unsigned.apk',out/'aligned.apk'])
# Upgrade build preserves existing installs. Production release uses a private local key.
def sign_apk(path,keystore,alias,password):
 run(['java','-jar',bt/'lib/apksigner.jar','sign','--ks',keystore,'--ks-key-alias',alias,'--ks-pass',password,'--out',path,out/'aligned.apk'])
 run(['java','-jar',bt/'lib/apksigner.jar','verify','--verbose',path])
if '--qa' in sys.argv:
 path=root/f'Fit100-v{version_name}-qa.apk'
 sign_apk(path,Path.home()/'.android/debug.keystore','androiddebugkey','pass:android')
 print('QA APK:',path)
else:
 upgrade=root/f'Fit100-v{version_name}-upgrade.apk'
 sign_apk(upgrade,Path.home()/'.android/debug.keystore','androiddebugkey','pass:android')
 private=root/'.release-private'
 private.mkdir(exist_ok=True)
 # Limit key/password access to the current Windows identity.
 if os.name=='nt':
  identity=subprocess.check_output(['whoami'],text=True).strip()
  subprocess.run(['icacls',str(private),'/inheritance:r','/grant:r',identity+':(OI)(CI)F'],check=True,capture_output=True)
 password_file=private/'keystore-password.txt'
 keystore=private/'fit100-release.p12'
 if not keystore.exists():
  password_file.write_text(secrets.token_urlsafe(40),encoding='ascii')
  environment=os.environ.copy();environment['FIT100_RELEASE_PASSWORD']=password_file.read_text()
  subprocess.run(['keytool','-genkeypair','-keystore',str(keystore),' -storetype'.strip(),'PKCS12','-alias','fit100','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=Fit100, OU=Android, O=Fit100','-storepass:env','FIT100_RELEASE_PASSWORD','-keypass:env','FIT100_RELEASE_PASSWORD'],env=environment,check=True,capture_output=True)
 if not password_file.exists(): raise RuntimeError('Release key password is missing; restore the private signing material.')
 release=root/f'Fit100-v{version_name}.apk'
 sign_apk(release,keystore,'fit100','file:'+str(password_file))
 print('Release APK:',release)
 print('Compatible upgrade APK:',upgrade)
