"""Compare production R5 FX sampling with actual approved JavaScript, without a GL context."""
from pathlib import Path
import hashlib
import json
import re
import subprocess
import check_pose_v31 as pose

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/critical-v2-runtime-v12131/fx'
APPROVED = OUT.parent / 'approved-v2'
JAVA = pose.JAVA
GSON = pose.GSON
NAMES = ('CriticalAnimation', 'CriticalMechanicalPose', 'CriticalContinuousMotion', 'CriticalDesignFX', 'CriticalSolarFX')
JS = r'''
const fs=require('fs'),path=require('path');
const source=process.argv[2],out=process.argv[3],M={};
for(const f of fs.readdirSync(path.join(source,'models')).filter(f=>f.endsWith('.geometry.json')))M[f.replace('.geometry.json','')]=JSON.parse(fs.readFileSync(path.join(source,'models',f)));
global.VOXEL_MODELS=M;
require(path.join(source,'preview','levitation.js'));V2_APPLY_LEVITATION_OVERRIDES(M);
require(path.join(source,'preview','continuous_motion.js'));
require(path.join(source,'preview','mechanism_fx.js'));
require(path.join(source,'preview','solar_fx.js'));
require(path.join(source,'preview','design_fx.js'));
const names=['turbine','processing','assembly','battery','entangler','dimension','accelerator','sun','solar'];
const groups=Object.values(M).flatMap(m=>Object.values(m.animationGroups||{}));
const timeline=[[0,'idle',0],[1,'idle',0],[2,'working',.1],[2.125,'working',.38],[2.8,'working',.7],[4,'idle',1],[4.01,'idle',0],[5,'working',.5],[5,'working',.5],[6,'working',.6],[9,'idle',1],[10,'working',.3],[10.1,'working',.34],[10.2,'working',.35],[10.3,'working',.38],[11,'working',.74],[11.1,'working',.77],[11.2,'working',.78],[11.3,'working',.8]];
const frames=[];
for(const [seconds,mode,phase]of timeline){
 V2_CONTINUOUS_MOTION.advance(seconds,groups,()=>mode);
 const rows=[];
 for(const slug of names)for(const [distance,setting]of [[0,0],[96,0],[160,0],[192,0],[0,1],[96,1],[0,2]]){
  global.V2_NATIVE_FX_DISTANCE=distance;global.V2_NATIVE_PARTICLE_SETTING=setting;
  const particles=V2_MECHANISM_PARTICLES(slug,phase,seconds);
  rows.push({slug,distance,setting,lod:distance>=192||setting>=2?0:Math.min(4,(distance>=160?4:distance>=96?2:1)*(setting===1?2:1)),expected:particles.map(p=>({family:p.type,center:p.center,color:p.rgba.slice(0,3),alpha:p.rgba[3],glowColor:p.glowRGBA.slice(0,3),glowAlpha:p.glowRGBA[3],size:p.size}))});
 }
 frames.push({seconds,mode,phase,rows});
}
fs.writeFileSync(out,JSON.stringify({models:Object.fromEntries(Object.entries(M).map(([k,m])=>[k,{meta:m.meta,animationGroups:m.animationGroups,ringPoints:k==='sun'?Object.fromEntries([0,1,2,3].map(i=>['confinement_'+i,m.blocks.filter(b=>b[4]==='confinement_'+i).map(b=>b.slice(0,3))])):undefined}])),frames}));
'''
HARNESS = r'''
package com.miaokatze.gtsr.client.critical;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
public final class CriticalFXV31Probe {
 static double error; static int particles,cases; static Map<String,Integer> near=new LinkedHashMap<>();
 static void check(double a,double b,String message){double e=Math.abs(a-b);error=Math.max(error,e);if(!Double.isFinite(e)||e>1e-8)throw new AssertionError(message+" actual="+a+" expected="+b);}
 public static void main(String[]args)throws Exception{
  JsonObject doc=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject(),models=doc.getAsJsonObject("models");
  List<JsonObject> all=new ArrayList<>();for(Map.Entry<String,JsonElement>e:models.entrySet())for(Map.Entry<String,JsonElement>g:e.getValue().getAsJsonObject().getAsJsonObject("animationGroups").entrySet())all.add(g.getValue().getAsJsonObject());
  Map<String,List<double[]>>ringPoints=new LinkedHashMap<>();
  for(Map.Entry<String,JsonElement>e:models.getAsJsonObject("sun").getAsJsonObject("ringPoints").entrySet()){List<double[]>points=new ArrayList<>();for(JsonElement p:e.getValue().getAsJsonArray()){JsonArray a=p.getAsJsonArray();points.add(new double[]{a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble()});}ringPoints.put(e.getKey(),points);}
  CriticalDesignFX.ParticleSink forbidden=(family,source,index,center,color,alpha,glow,size)->{throw new AssertionError("off sampler emitted");};
  if(CriticalDesignFX.sample(null,null,null,0,0,"idle",0,null,forbidden)!=0||CriticalDesignFX.sampleSun(null,null,null,null,0,0,"idle",0,null,forbidden)!=0||CriticalSolarFX.sample(null,null,null,0,0,"idle",0,null,(family,index,center,color,alpha,glowColor,glow,size)->{throw new AssertionError("off solar emitted");})!=0)throw new AssertionError("off early return");
  CriticalContinuousMotion.Context context=new CriticalContinuousMotion.Context();
  for(JsonElement fe:doc.getAsJsonArray("frames")){
   JsonObject f=fe.getAsJsonObject();double seconds=f.get("seconds").getAsDouble(),phase=f.get("phase").getAsDouble();String mode=f.get("mode").getAsString();context.beginFrame(seconds,all,mode);CriticalAnimation.beginFrame(context);long frame=context.frames();
   for(JsonElement re:f.getAsJsonArray("rows")){
    JsonObject row=re.getAsJsonObject();String slug=row.get("slug").getAsString();String[]slugs={"base_t3","loom",slug};JsonObject[]metas=new JsonObject[3],groups=new JsonObject[3];for(int i=0;i<3;i++){JsonObject m=models.getAsJsonObject(slugs[i]);metas[i]=m.getAsJsonObject("meta");groups[i]=m.getAsJsonObject("animationGroups");}
    String profile=CriticalDesignFX.profile(metas[2],"v2_"+slug);if(!profile.equals(slug.equals("sun")||slug.equals("solar")?slug:"design"))throw new AssertionError("FX metadata profile "+slug);
    JsonArray expected=row.getAsJsonArray("expected");int[]cursor={0};
    CriticalDesignFX.ParticleSink sink=(family,source,index,center,color,alpha,glow,size)->{
     if(cursor[0]>=expected.size())throw new AssertionError("extra particle "+slug+" "+family+"@"+seconds);
     JsonObject p=expected.get(cursor[0]++).getAsJsonObject();String label=slug+" "+family+" "+source+"@"+seconds;
     if(!family.equals(p.get("family").getAsString()))throw new AssertionError("family mismatch "+label+" expected="+p.get("family"));
     for(int d=0;d<3;d++){check(center[d],p.getAsJsonArray("center").get(d).getAsDouble(),label+" center"+d);check(color[d],p.getAsJsonArray("color").get(d).getAsDouble(),label+" color"+d);}
     check(alpha,p.get("alpha").getAsDouble(),label+" alpha");check(glow,p.get("glowAlpha").getAsDouble(),label+" glow");check(size,p.get("size").getAsDouble(),label+" size");particles++;
    };
    int lod=row.get("lod").getAsInt();
    int count="sun".equals(slug)?CriticalDesignFX.sampleSun(metas,groups,slugs,ringPoints,phase,seconds,mode,lod,null,sink):"solar".equals(slug)?CriticalSolarFX.sample(metas,groups,slugs,phase,seconds,mode,lod,null,(family,index,center,color,alpha,glowColor,glowAlpha,size)->{sink.emit(family,"",index,center,color,alpha,glowAlpha,size);JsonObject p=expected.get(cursor[0]-1).getAsJsonObject();for(int d=0;d<3;d++)check(glowColor[d],p.getAsJsonArray("glowColor").get(d).getAsDouble(),"solar glow color");}):CriticalDesignFX.sample(metas,groups,slugs,phase,seconds,mode,lod,null,sink);
    if(count!=expected.size()||cursor[0]!=expected.size())throw new AssertionError(slug+" count actual="+count+" expected="+expected.size());
    if(row.get("lod").getAsInt()==1)near.put(slug+":"+mode+":"+phase,count);cases++;
   }
   if(context.frames()!=frame)throw new AssertionError("sampler advanced pose clock");CriticalAnimation.endFrame();
  }
  // Read-only geometry-derived bottom extent and world-down trails are checked under a tilted carrier.
  JsonObject tilt=JsonParser.parseString("{\"pivot\":[1,2,3],\"axis\":\"z\",\"ambientRotation\":{\"axis\":\"z\",\"periodSeconds\":8}}").getAsJsonObject();
  double[] rest={4,5,6},posed=CriticalDesignFX.pose(rest,tilt,.5,9,"build");
  double gap=CriticalDesignFX.bottomGap(rest,tilt,.5,9,"build",posed);
  if(gap<.85||gap>.86)throw new AssertionError("rotated cube bottom clearance "+gap);
  for(int total:new int[]{1,36,64,65,130,1000})for(double sec:new double[]{0,1,3.7,100}){int active=0;for(int i=0;i<total;i++)if(CriticalDesignFX.active(i,total,sec))active++;if(active!=Math.min(64,total))throw new AssertionError("active gravity width");}
  // Two installations with identical JSON identities must retain independent context poses, including native Solar caches.
  CriticalContinuousMotion.Context a=new CriticalContinuousMotion.Context(),b=new CriticalContinuousMotion.Context();
  a.beginFrame(0,all,"working");b.beginFrame(0,all,"idle");a.beginFrame(3,all,"working");b.beginFrame(3,all,"idle");
  JsonObject moving=all.stream().filter(g->g.has("continuousMotion")).findFirst().orElseThrow();
  CriticalAnimation.beginFrame(a);double[]pa=CriticalDesignFX.pose(rest,moving,.5,3,"working");CriticalAnimation.beginFrame(b);double[]pb=CriticalDesignFX.pose(rest,moving,.5,3,"working");CriticalAnimation.endFrame();
  double delta=0;for(int d=0;d<3;d++)delta+=Math.abs(pa[d]-pb[d]);if(delta<1e-6)throw new AssertionError("FX contexts coupled");
  int aliases=0;
  for(String prefix:new String[]{"v2_legacy_","v2_v25_","v2_v27_","v2_v30_"})for(String main:new String[]{"sun","solar"}){
   String[]slugs={prefix+"base_t3",prefix+"loom","v2_v30_"+main};JsonObject[]metas=new JsonObject[3],gs=new JsonObject[3];Map<String,List<double[]>>points=new LinkedHashMap<>();
   for(int i=0;i<3;i++){JsonObject m=JsonParser.parseString(Files.readString(Path.of(args[2],slugs[i]+".geometry.json"))).getAsJsonObject();metas[i]=m.getAsJsonObject("meta");gs[i]=m.getAsJsonObject("animationGroups");if(i==2&&main.equals("sun")){for(int k=0;k<4;k++)points.put("confinement_"+k,new ArrayList<>());for(JsonElement e:m.getAsJsonArray("blocks")){JsonArray q=e.getAsJsonArray();List<double[]>list=points.get(q.get(4).getAsString());if(list!=null)list.add(new double[]{q.get(0).getAsDouble(),q.get(1).getAsDouble(),q.get(2).getAsDouble()});}}}
   if(!CriticalDesignFX.profile(metas[2],slugs[2]).equals(main))throw new AssertionError("legacy metadata routing");
   CriticalDesignFX.ParticleSink finite=(family,source,index,center,color,alpha,glow,size)->{for(double v:center)if(!Double.isFinite(v))throw new AssertionError("legacy nonfinite source");};
   for(double phase:new double[]{0,.5})for(int lod:new int[]{0,1,2,4}){String mode=phase==0?"idle":"working";int n=main.equals("sun")?CriticalDesignFX.sampleSun(metas,gs,slugs,points,phase,4,mode,lod,null,finite):CriticalSolarFX.sample(metas,gs,slugs,phase,4,mode,lod,null,(family,index,center,color,alpha,glowColor,glow,size)->finite.emit(family,"",index,center,color,alpha,glow,size));if((lod==0)!=(n==0))throw new AssertionError("legacy LOD gate");aliases++;}
  }
  CriticalDesignFX.clear();CriticalSolarFX.clear();
  java.lang.reflect.Field cache=CriticalDesignFX.class.getDeclaredField("MODELS");cache.setAccessible(true);if(!((Map<?,?>)cache.get(null)).isEmpty())throw new AssertionError("reload cache");
  JsonObject report=new JsonObject();report.addProperty("passed",true);report.addProperty("scenarios",cases);report.addProperty("particles",particles);report.addProperty("maxAbsoluteError",error);report.addProperty("pureSampler",true);report.addProperty("legacyAliasScenarios",aliases);report.addProperty("rotatedBottomGap",gap);report.addProperty("independentContexts",true);report.addProperty("reloadClearsCaches",true);report.addProperty("contextClockUnchanged",true);report.addProperty("gravityActiveLimit",64);report.addProperty("idleLimit",200);report.addProperty("workLimit",800);report.add("counts",new Gson().toJsonTree(near));Files.writeString(Path.of(args[1]),report.toString());
  System.out.println("PASS "+cases+" real R5 JS FX scenarios; "+particles+" particles; max error="+error);
 }
}
'''

def run(command):
    result=subprocess.run(list(map(str,command)),cwd=ROOT,capture_output=True,text=True,encoding='utf-8',errors='replace')
    if result.returncode: raise RuntimeError(result.stdout+result.stderr)
    if result.stdout.strip(): print(result.stdout.strip())

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    viewer=(APPROVED/'preview/viewer.js').read_text(encoding='utf-8')
    math=re.search(r'const sub=.*?;function mul',viewer).group(0).removesuffix('function mul')
    declarations='\n'.join(pose.function(viewer,'function '+name+'(') for name in ('sample','rotate','axisVector','idleSample','levitationBob','orbitAngle','workSample','workSpinSample'))
    reference=OUT/'reference.js'
    reference.write_text('global.window=global;\n'+math+'\n'+declarations+'\n'+pose.function(viewer,'window.VOXEL_TRANSFORM=')+';\n'+JS,encoding='utf-8')
    cases=OUT/'reference.json'
    run(['node',reference,APPROVED,cases])
    harness=OUT/'CriticalFXV31Probe.java';harness.write_text(HARNESS,encoding='utf-8')
    classes=OUT/'classes';classes.mkdir(exist_ok=True)
    sources=[ROOT/'src/main/java/com/miaokatze/gtsr/client/critical'/(name+'.java') for name in NAMES]
    run([JAVA/'javac.exe','-encoding','UTF-8','-cp',GSON,'-d',classes,*sources,harness])
    report=OUT/'design-fx-report.json'
    run([JAVA/'java.exe','-cp',str(classes)+';'+str(GSON),'com.miaokatze.gtsr.client.critical.CriticalFXV31Probe',cases,report,ROOT/'src/main/resources/assets/gtsr/critical/models'])
    r=json.loads(report.read_text(encoding='utf-8'))
    r['sources']={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
    r['approvedDesignFXSha256']=hashlib.sha256((APPROVED/'preview/design_fx.js').read_bytes()).hexdigest()
    r['approvedNativeSunFXSha256']=hashlib.sha256((APPROVED/'preview/mechanism_fx.js').read_bytes()).hexdigest()
    r['approvedNativeSolarFXSha256']=hashlib.sha256((APPROVED/'preview/solar_fx.js').read_bytes()).hexdigest()
    report.write_text(json.dumps(r,indent=2),encoding='utf-8')
    owned=[ROOT/'src/main/java/com/miaokatze/gtsr/client/critical'/(name+'.java') for name in ('CriticalFX','CriticalSunFX','CriticalSolarFX','CriticalDesignFX')]+[Path(__file__).resolve()]
    (OUT/'owned-sha256.json').write_text(json.dumps({str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in owned},indent=2),encoding='utf-8')


if __name__=='__main__': main()
