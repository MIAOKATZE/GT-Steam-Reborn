"""Compare production Java poses with the approved R5 JavaScript (no Minecraft boot)."""
from pathlib import Path
import hashlib
import json
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/critical-v2-runtime-v12131/pose'
APPROVED = OUT.parent / 'approved-v2'
JAVA = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'
GSON = next((Path.home() / '.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson').rglob('*.jar'))
SOURCES = [ROOT / 'src/main/java/com/miaokatze/gtsr/client/critical' / (name + '.java') for name in
           ('CriticalAnimation', 'CriticalMechanicalPose', 'CriticalContinuousMotion')]


def function(source, marker):
    start = source.index(marker)
    opening = source.index('{', start)
    depth = 1
    cursor = opening + 1
    while depth:
        char = source[cursor]
        depth += (char == '{') - (char == '}')
        cursor += 1
    return source[start:cursor]


JS = r'''
const fs=require('fs'),path=require('path');global.window=global;
const source=process.argv[2],out=process.argv[3],M={};
for(const f of fs.readdirSync(path.join(source,'models')).filter(f=>f.endsWith('.geometry.json'))){const m=JSON.parse(fs.readFileSync(path.join(source,'models',f)));M[f.replace('.geometry.json','')]=m;}
require(path.join(source,'preview','levitation.js'));V2_APPLY_LEVITATION_OVERRIDES(M);
require(path.join(source,'preview','continuous_motion.js'));
const groups=[],identity=[];
for(const [slug,m]of Object.entries(M))for(const [id,g]of Object.entries(m.animationGroups||{})){groups.push(g);identity.push({slug,id});}
const timeline=[[0,'idle',0],[1,'idle',0],[2,'working',.1],[2.125,'working',.38],[2.8,'working',.7],[4,'idle',1],[4.01,'idle',0],[5,'working',.2],[6,'working',.6],[9,'idle',1]];
const frames=[];
for(const [seconds,mode,phase]of timeline){V2_CONTINUOUS_MOTION.advance(seconds,groups,()=>mode);const rows=[];for(let i=0;i<groups.length;i++){const g=groups[i];if(!g.continuousMotion&&!g.levitationBob&&!(seconds===2.8||seconds===4.01))continue;const pivot=g.pivot||[0,0,0],points=[pivot,[pivot[0]+1.2,pivot[1]-.8,pivot[2]+2.3],[0,0,0],[112,180,112]];rows.push({...identity[i],group:g,continuous:g.continuousMotion?V2_CONTINUOUS_MOTION.sample(g,seconds):null,points:points.map(point=>({point,expected:VOXEL_TRANSFORM(point,g,phase,seconds,mode)}))});}frames.push({seconds,mode,phase,rows});}
fs.writeFileSync(out,JSON.stringify(frames));
'''

HARNESS = r'''
package com.miaokatze.gtsr.client.critical;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
public final class CriticalPoseV31Probe {
 static double error; static int points,continuous;
 static void check(double actual,double expected,String message) {
   double e=Math.abs(actual-expected);error=Math.max(error,e);
   if(!Double.isFinite(e)||e>1e-8)throw new AssertionError(message+" actual="+actual+" expected="+expected);
 }
 public static void main(String[] args)throws Exception {
   JsonArray frames=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray();
   List<JsonObject> groups=new ArrayList<>();
   for(JsonElement row:frames.get(0).getAsJsonObject().getAsJsonArray("rows"))groups.add(row.getAsJsonObject().getAsJsonObject("group"));
   long bobGroups=groups.stream().filter(g->g.has("levitationBob")).count();
   if(bobGroups!=153)throw new AssertionError("Expected every approved levitation carrier: "+bobGroups);
   int decor=0;for(JsonElement row:frames.get(0).getAsJsonObject().getAsJsonArray("rows")){
     JsonObject r=row.getAsJsonObject();if(r.get("slug").getAsString().equals("base_t3")&&r.get("id").getAsString().startsWith("decor_shell_")){
       JsonObject bob=r.getAsJsonObject("group").getAsJsonObject("levitationBob");check(bob.get("amplitude").getAsDouble(),.36,"decor half amplitude");double period=bob.get("periodSeconds").getAsDouble();if(period<8.3||period>8.9)throw new AssertionError("decor period");decor++;
     }
   }if(decor!=8)throw new AssertionError("Eight plugin parent carriers required");
   CriticalContinuousMotion.Context context=new CriticalContinuousMotion.Context();
   for(JsonElement fe:frames){JsonObject frame=fe.getAsJsonObject();double seconds=frame.get("seconds").getAsDouble(),phase=frame.get("phase").getAsDouble();String mode=frame.get("mode").getAsString();
     context.beginFrame(seconds,groups,mode);CriticalAnimation.beginFrame(context);
     long before=context.frames();
     for(JsonElement re:frame.getAsJsonArray("rows")){JsonObject row=re.getAsJsonObject(),g=row.getAsJsonObject("group");
       if(!row.get("continuous").isJsonNull()){JsonObject expected=row.getAsJsonObject("continuous");CriticalContinuousMotion.Sample motion=context.sample(g);check(motion.theta,expected.get("theta").getAsDouble(),"theta "+row.get("id"));check(motion.velocity,expected.get("velocity").getAsDouble(),"velocity");if(context.sample(g)!=motion)throw new AssertionError("snapshot not immutable");continuous++;}
       double[] matrix=CriticalMechanicalPose.matrix(g,phase,seconds,mode);
       for(JsonElement pe:row.getAsJsonArray("points")){JsonObject p=pe.getAsJsonObject();double[] point=CriticalAnimation.vector(p,"point",null),expected=CriticalAnimation.vector(p,"expected",null),actual=CriticalAnimation.transform(point,g,phase,seconds,mode);
         for(int d=0;d<3;d++){check(actual[d],expected[d],row.get("slug")+":"+row.get("id")+" "+mode+"@"+seconds);double nativeValue=matrix[d]*(point[0]+.5)+matrix[4+d]*(point[1]+.5)+matrix[8+d]*(point[2]+.5)+matrix[12+d];check(nativeValue,expected[d]+.5,"native corner affine");}points++;
       }
     }
     if(before!=context.frames())throw new AssertionError("reader advanced frame");CriticalAnimation.endFrame();
   }
   if(continuous!=460)throw new AssertionError("Expected 46 clocks x 10 samples: "+continuous);
   // Different installations may use the same clockId while receiving different work transitions.
   CriticalContinuousMotion.Context a=new CriticalContinuousMotion.Context(),b=new CriticalContinuousMotion.Context();
   a.beginFrame(0,groups,"working");b.beginFrame(0,groups,"idle");a.beginFrame(1,groups,"working");b.beginFrame(1,groups,"idle");
   JsonObject rotating=groups.stream().filter(g->g.has("continuousMotion")).findFirst().orElseThrow();
   if(Math.abs(a.sample(rotating).theta-b.sample(rotating).theta)<1e-6)throw new AssertionError("installation clocks coupled");
   double theta=a.sample(rotating).theta;a.beginFrame(1,groups,"working");check(a.sample(rotating).theta,theta,"paused frame");
   // Resource reload creates new JSON identities; retained clock phases must be unchanged.
   List<JsonObject> reloaded=new ArrayList<>();for(JsonObject g:groups)reloaded.add(JsonParser.parseString(g.toString()).getAsJsonObject());
   a.beginFrame(1,reloaded,"working");check(a.sample(rotating).theta,theta,"resource reload phase");
   if(a.clockCount()!=39)throw new AssertionError("shared turbine clock must collapse 8 arcs: "+a.clockCount());
   CriticalContinuousMotion.FrameClock wall=new CriticalContinuousMotion.FrameClock();
   check(wall.update(1,100,false),0,"first frame");check(wall.update(2,100.25,false),.25,"running wall clock");
   check(wall.update(2,100.9,false),.25,"duplicate render pass");check(wall.update(3,100.5,true),.25,"paused client");
   check(wall.update(4,100.75,false),.5,"resume without catchup");check(wall.update(5,300,false),.5,"unload gap");
   check(wall.update(6,300.25,false),.75,"reload resume");check(wall.update(7,299,false),.75,"monotonic clock rollback");
   System.out.println("PASS "+continuous+" continuous states; "+points+" transformed points + native corners; max error="+error);
   JsonObject report=new JsonObject();report.addProperty("passed",true);report.addProperty("continuousStates",continuous);report.addProperty("uniqueClocks",a.clockCount());report.addProperty("levitationGroups",bobGroups);report.addProperty("decorativeAndPluginCarriers",decor);report.addProperty("transformedPoints",points);report.addProperty("nativeCorners",points);report.addProperty("maxAbsoluteError",error);report.addProperty("independentControllers",true);report.addProperty("pausedReadOnlySnapshot",true);report.addProperty("resourceReloadContinuity",true);
   report.addProperty("frameClockLifecycle",true);Files.writeString(Path.of(args[1]),report.toString());
 }
}
'''


def run(command):
    result = subprocess.run(list(map(str, command)), cwd=ROOT, capture_output=True, text=True, encoding='utf-8', errors='replace')
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    print(result.stdout.strip())


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    viewer = (APPROVED / 'preview/viewer.js').read_text(encoding='utf-8')
    math = re.search(r'const sub=.*?;function mul', viewer).group(0).removesuffix('function mul')
    declarations = '\n'.join(function(viewer, 'function ' + name + '(') for name in
                             ('sample', 'rotate', 'axisVector', 'idleSample', 'levitationBob', 'orbitAngle', 'workSample', 'workSpinSample'))
    transform = function(viewer, 'window.VOXEL_TRANSFORM=') + ';'
    reference = OUT / 'reference.js'
    # The approved transform assignment precedes reference execution.
    reference.write_text(math + '\n' + declarations + '\n' + transform + '\n' + JS.replace('global.window=global;', ''), encoding='utf-8')
    reference.write_text('global.window=global;\n' + reference.read_text(encoding='utf-8'), encoding='utf-8')
    cases = OUT / 'reference.json'
    run(['node', reference, APPROVED, cases])
    harness = OUT / 'CriticalPoseV31Probe.java'
    harness.write_text(HARNESS, encoding='utf-8')
    classes = OUT / 'classes'
    classes.mkdir(exist_ok=True)
    run([JAVA / 'javac.exe', '-encoding', 'UTF-8', '-proc:none', '-cp', GSON, '-d', classes, *SOURCES, harness])
    run([JAVA / 'java.exe', '-cp', str(classes) + ';' + str(GSON), 'com.miaokatze.gtsr.client.critical.CriticalPoseV31Probe', cases, OUT / 'math-report.json'])
    paths = SOURCES + [Path(__file__), APPROVED / 'preview/viewer.js', APPROVED / 'preview/continuous_motion.js', APPROVED / 'preview/levitation.js']
    bindings = {p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}
    (OUT / 'binding.json').write_text(json.dumps({'files': bindings, 'reference': 'approved viewer functions extracted verbatim; Sun/Solar overrides executed'}, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
