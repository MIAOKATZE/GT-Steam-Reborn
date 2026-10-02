"""Compile against MC's Gson 2.2.4 and directly exercise packaged loader contracts."""
import os
import subprocess
import tempfile
from pathlib import Path
from remaster_convert import ROOT


def main():
    cache = Path.home() / '.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/2.2.4'
    gson = next(cache.rglob('*.jar'))
    source = ROOT / 'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/remaster'
    with tempfile.TemporaryDirectory(prefix='gtsr-remaster-verify-') as tmp:
        harness = Path(tmp) / 'RemasterLoaderCheck.java'
        harness.write_text('''import com.miaokatze.gtsr.common.dimension.prosperity.remaster.*;
public class RemasterLoaderCheck {
 public static void main(String[] args) {
  int count=0;
  if(RemasterCatalog.ids().size()!=90)throw new AssertionError();
  for(String id:RemasterCatalog.ids())for(int v=0;v<RemasterCatalog.variants(id);v++) {
   RemasterPrefab p=RemasterCatalog.get(id,v);count++;
   if(p.metadata.get("productionPuzzle")==null)throw new AssertionError(id);
   if(p.extent[1]>256||!p.palette[0].equals("minecraft:air#0"))throw new AssertionError(id);
   if(!p.slice(Integer.MAX_VALUE,Integer.MAX_VALUE).isEmpty())throw new AssertionError();
   boolean found=false;
   for(int x=Math.floorDiv(p.min[0],16);x<=Math.floorDiv(p.max[0],16)&&!found;x++)
    for(int z=Math.floorDiv(p.min[2],16);z<=Math.floorDiv(p.max[2],16)&&!found;z++)
     if(!p.slice(x,z).isEmpty()) {
      for(RemasterPrefab.Run r:p.slice(x,z))
       if(Math.floorDiv(r.x,16)!=x||Math.floorDiv(r.x+r.length-1,16)!=x||Math.floorDiv(r.z,16)!=z)
        throw new AssertionError(id);
      found=true;
     }
   if(!found)throw new AssertionError(id);
  }
  if(count!=268||RemasterCatalog.variants("forgotten_lake_court")!=1)throw new AssertionError();
  if(RemasterCatalog.config().getAsJsonArray("engineeringChapters").size()!=8)throw new AssertionError();
  System.out.println("passed: 90 catalog ids / 268 lazy manifests and chunk slices / Gson 2.2.4");
 }
}''', encoding='utf8')
        subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', str(gson), '-d', tmp,
                        str(source / 'RemasterCatalog.java'), str(source / 'RemasterPrefab.java'),
                        str(harness)], check=True)
        cp = os.pathsep.join([tmp, str(gson), str(ROOT / 'src/main/resources')])
        subprocess.run(['java', '-cp', cp, 'RemasterLoaderCheck'], check=True)


if __name__ == '__main__':
    main()
