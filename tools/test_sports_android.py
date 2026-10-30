from pathlib import Path
import os,subprocess,sys,argparse
parser=argparse.ArgumentParser();parser.add_argument('--serial',default='emulator-5556');args=parser.parse_args()
root=Path(__file__).resolve().parents[1]
sdk=Path(os.environ.get('ANDROID_HOME',str(Path.home()/'AppData/Local/Android/Sdk')))
adb=[str(sdk/'platform-tools/adb.exe'),'-s',args.serial]
def run(*args):
 result=subprocess.run([*adb,*args],capture_output=True,text=True,check=True);print(result.stdout.strip());return result.stdout
for package in ['com.google.android.permissioncontroller','com.android.permissioncontroller']:
 run('shell','am','force-stop',package)
run('install','-r',str(root/'Fit100-sports-instrumented.apk'))
for permission in ['ACCESS_FINE_LOCATION','ACCESS_COARSE_LOCATION']:
 run('shell','pm','revoke','com.fit100.tracker','android.permission.'+permission)
 run('shell','pm','clear-permission-flags','com.fit100.tracker','android.permission.'+permission,'user-set','user-fixed')
output=run('shell','am','instrument','-w','com.fit100.tracker/.SportsInstrumentation')
(root/'build/sports-android-test-result.txt').write_text(output)
if 'FAIL' in output or 'PASS' not in output:sys.exit(1)
run('shell','am','force-stop','com.fit100.tracker')
output=run('shell','am','instrument','-w','-e','restart','true','com.fit100.tracker/.SportsInstrumentation')
with (root/'build/sports-android-test-result.txt').open('a') as f:f.write(output)
if 'FAIL' in output or 'PASS' not in output:sys.exit(1)
preview=root/'previews/sports-module';preview.mkdir(exist_ok=True)
run('pull','/sdcard/Android/data/com.fit100.tracker/files/sports-qa',str(preview))
