from pathlib import Path
import os,subprocess,sys
root=Path(__file__).resolve().parents[1]
cache=Path.home()/'.gradle/caches/modules-2/files-2.1'
jars=[]
for group in ['org.jetbrains.kotlin','org.jetbrains.intellij.deps','org.jetbrains.kotlinx','org.jetbrains/annotations']:jars.extend((cache/group).rglob('*.jar'))
stdlib=next((cache/'org.jetbrains.kotlin/kotlin-stdlib/2.0.21').rglob('*.jar'))
out=root/'build/sports-module-tests';out.mkdir(exist_ok=True)
source=root/'app/src/main/java/com/fit100/tracker/SportsCore.kt'
subprocess.run(['java','-cp',os.pathsep.join(map(str,jars)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','17','-classpath',str(stdlib),'-d',str(out),str(source),str(root/'tests/SportsModuleCheck.kt')],check=True)
import tempfile
with tempfile.TemporaryDirectory(dir=out) as storage:
 subprocess.run(['java','-cp',str(out)+os.pathsep+str(stdlib),'com.fit100.tracker.SportsModuleCheckKt',storage],check=True)
