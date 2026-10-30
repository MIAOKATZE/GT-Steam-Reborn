#!/usr/bin/env python3
"""Offline native MC/Forge/MUI R2 GUI/lifecycle source-overlay regressions, no downloads or game launch."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[3]
SOURCES=(
 'common/terminal/TerminalServerSessions.java',
 'common/terminal/RemoteTerminalFactory.java',
 'common/terminal/RemoteTerminalGuiData.java',
 'common/gui/mui2/hub/HubTerminalFactory.java',
 'common/gui/mui2/cluster/ClusterTerminalFactory.java',
 'common/gui/CriticalMachineTerminalFactory.java',
 'common/gui/mui2/TcdsTerminalFactory.java',
 'common/gui/mui2/entanglement/EntanglementTerminalFactory.java',
 'common/gui/mui2/inventory/AggregatorConfigFactory.java',
 'common/gui/mui2/entanglement/EntanglementTerminalSession.java',
 'client/weapons/PortableWeaponRenderer.java',
)
REGRESSIONS=tuple('range-'+x for x in ('hub','cluster','critical','tcds','entanglement'))+tuple('unloaded-'+x for x in ('hub','cluster','critical','tcds','entanglement','aggregator'))+('entanglement','renderer')
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--workspace',type=Path,default=ROOT.parents[1]);p.add_argument('--out',type=Path,default=ROOT/'build/review-gui-lifecycle');p.add_argument('--baseline-source',type=Path);a=p.parse_args()
 ws=a.workspace.resolve();out=a.out.resolve();out.mkdir(parents=True,exist_ok=True);empty=out/'empty';empty.mkdir(exist_ok=True);jdk=next((ws/'toolchains').glob('*jdk17*/bin'));cache=ws/'.gradle-build/caches'
 jars=[next((ws/'.gradle-build/wrapper').rglob('fastutil-*-min.jar')),ws/'review-gtsr-client-r1/forge-mcp.jar',ws/'review-monitoring/minecraft-mcp.jar',ws/'review-gtsr-hubs-r1/gt5u-mcp.jar',ws/'dependency-probe/gtsr-v2.0.43-dev.jar',*sorted((ws/'dependency-probe').glob('*.jar')),*sorted(cache.glob('modules-2/files-2.1/*/*/*/*/*.jar'))]
 jars=[x for x in dict.fromkeys(jars) if not x.name.endswith(('-sources.jar','-javadoc.jar'))];assert all(x.exists() for x in jars)
 assert sha(ws/'dependency-probe/gtsr-v2.0.43-dev.jar')=='e10aebdc28f331a5f15546472b68067aa4b4128dcfbab2b36684766046fad533','Unexpected baseline development artifact'
 cp=':'.join(map(str,jars));(out/'classpath.txt').write_text(cp);results={};hashes={};baseline_hashes={}
 def run(name,tool,args,fail=False):
  result=subprocess.run([str(jdk/tool),*map(str,args)],cwd=out,capture_output=True,text=True,timeout=120);log=result.stdout+result.stderr;(out/(name+'.log')).write_text(log)
  if fail:
   scenario=name.removeprefix('baseline-')
   if scenario.startswith('range-'): expected=scenario[6:]+' distant factory accessed untrusted tile'
   elif scenario.startswith('unloaded-'): expected=scenario[9:]+' unloaded factory accessed untrusted tile'
   elif scenario=='entanglement': expected='page+apply race silently discarded command/ACK obligation'
   else: expected='weapon renderer is missing resource reload lifecycle'
   assert result.returncode!=0 and 'java.lang.AssertionError: '+expected in log,name+' did not reproduce intended assertion\n'+log
  else: assert result.returncode==0,name+'\n'+log
  print(name+(' reproduced' if fail else ' PASS'),flush=True);return result.returncode
 for mode in ('baseline','patched'):
  classes=out/(mode+'-classes');classes.mkdir(exist_ok=True);paths=[]
  for name in (*SOURCES,'common/network/ServerPacketAuthority.java'):
   rel='src/main/java/com/miaokatze/gtsr/'+name;source=ROOT/rel
   if mode=='baseline' and name!='common/network/ServerPacketAuthority.java':
    raw=(a.baseline_source/name).read_text() if a.baseline_source else subprocess.check_output(['git','show','57d082e9e748c8918ae3bb2c33df386c9a1cc99e:'+rel],cwd=ROOT,text=True)
    baseline_hashes[rel]=hashlib.sha256(raw.encode()).hexdigest()
   else: raw=source.read_text();hashes[rel]=sha(source)
   # MCP spelling differences only; no behavior replacement.
   raw=raw.replace('.func_152344_a(','.addScheduledTask(').replace('.getWorldObj()','.getWorld()').replace('.fontRenderer.','.fontRendererObj.')
   target=out/(mode+'-source')/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(raw);paths.append(target)
  for name in ('PacketAuthorityRegression.java','GuiLifecycleRegression.java'):
   raw=(ROOT/'tools/tests/network'/name).read_text()
   target=out/(mode+'-source')/name;target.write_text(raw);paths.append(target)
  paths.extend(sorted((ROOT/'tools/tests/network/fixtures').glob('*.java')))
  run(mode+'-compile','javac',['-encoding','UTF-8','-proc:none','-sourcepath',empty,'-cp',cp,'-d',classes,*paths])
  for scenario in REGRESSIONS+(('bounds','remote') if mode=='patched' else ()):
   results[mode+'-'+scenario]=run(mode+'-'+scenario,'java',['-cp',str(classes)+':'+cp,'com.miaokatze.gtsr.tests.GuiLifecycleRegression',scenario],mode=='baseline')
 assert all(sha(ROOT/p)==h for p,h in hashes.items()),'Production sources changed during run'
 report={'baseline':'preserved pre-R2 source snapshot' if a.baseline_source else '57d082e9e748c8918ae3bb2c33df386c9a1cc99e','results':results,'sourceSha256':hashes,'baselineSourceSha256':baseline_hashes,'testSha256':sha(ROOT/'tools/tests/network/GuiLifecycleRegression.java'),'runnerSha256':sha(Path(__file__)),'fixtureSha256':{x.name:sha(x) for x in sorted((ROOT/'tools/tests/network/fixtures').glob('*.java'))},'dependencySha256':{x.name:sha(x) for x in jars[:5]},'limitations':'Whole production source overlays against native MC/Forge/MUI/GT/Core. Unsafe isolates native player/world/container state; native decoded OpenGuiPacket reaches the real factory via a boundary wrapper which stops before container installation. Entanglement proves queue admission, inactive cleanup and client rejection decoding separately, not live server ACK transport. Renderer resource invalidation uses recording IntConsumer instead of GL. Offline lwjgl3ify InputEvents types are link-only fixtures, with no input callbacks executed. Three absent fastutil map constructors use LinkedHashMap field fixtures; no specialized operations are tested. No full Gradle/package build, side transformer/Mixins, GUI/game launch, GL drawing or TCP.'}
 (out/'verification.json').write_text(json.dumps(report,indent=2));print('PASS; receipt: '+str(out/'verification.json'))
if __name__=='__main__':main()
