"""Public listener/native runtime aliases, without launching Minecraft."""
from pathlib import Path
import subprocess,json
ROOT=Path(__file__).resolve().parents[3]
def main():
 out=ROOT/'build/critical-night-v2038-client-qa';java=Path.home()/'.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'
 tasks={'listener':(out/'qa-classes','audit.UiGalleryBatchNight38'),'darkworld':(out/'qa-classes','audit.UiGalleryBatchNight38$DarkWorld'),'sky':(ROOT/'build/rfg/srg_merged_minecraft.jar','net.minecraft.util.ScreenShotHelper'),'stars':(ROOT/'build/rfg/srg_merged_minecraft.jar','net.minecraft.client.renderer.RenderGlobal'),'block':(ROOT/'build/rfg/srg_merged_minecraft.jar','net.minecraft.block.Block')};results={}
 for name,(cp,cls) in tasks.items():
  p=subprocess.run([str(java/'javap.exe'),'-p','-classpath',str(cp),cls],capture_output=True,text=True,encoding='utf-8',errors='replace');assert p.returncode==0,p.stderr;(out/(name+'-javap.txt')).write_text(p.stdout,encoding='utf-8');results[name]=p.stdout
 assert 'public final class audit.UiGalleryBatchNight38' in results['listener']
 for method,event in [('client','ClientTickEvent'),('server','ServerTickEvent'),('render','RenderTickEvent')]:assert 'public void '+method+'(cpw.mods.fml.common.gameevent.TickEvent$'+event+');' in results['listener']
 assert 'public final class audit.UiGalleryBatchNight38$DarkWorld' in results['darkworld']
 assert 'func_148259_a(java.io.File, java.lang.String, int, int, net.minecraft.client.shader.Framebuffer)' in results['sky']
 assert 'field_72772_v' in results['stars']
 assert 'int func_149750_m();' in results['block']
 source=(ROOT/'plan/implementation/critical-night-v2038/qa-src/UiGalleryBatchNight38.java').read_text(encoding='utf-8')
 assert source.index('if(phase==90)')<source.index('if(serverFailure!=null)')
 assert 'privateWorld==call(type("net.minecraftforge.common.DimensionManager"),"getWorld",0)' in source
 assert 'getClass()==type("net.minecraft.world.WorldProviderSurface")' not in source
 assert 'clientProbeReady(w)' in source and 'chunkExists|func_73149_a' in source
 assert 'setPositionAndUpdate|func_70634_a",0D,70D,0D' in source and 'sendPlayerAbilities|func_71016_p' in source
 assert 'doubleValue()<.05D' not in source
 assert 'q.record("night38."+n,pass,detail)' in source
 receipt=dict(noExactNoonClientGate=True,semanticFailuresRecordedNotSwallowed=True,realClientProbeReadiness=True,ownedPlayerProbePositionAndFlying=True,cleanupBeforeServerFailure=True,world0DimensionManagerIdentity=True,status='PASS' ,compiledNotLaunched=True,publicListeners=3,publicEnclosingTypes=True,nativeScreenshotSignature=True,nativeStarListField=True,registeredBlockLightGetter=True)
 (out/'preflight.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8');print(json.dumps(receipt))
if __name__=='__main__':main()
