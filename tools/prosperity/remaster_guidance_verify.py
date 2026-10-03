"""Exercise the production block's readable notice and state-dependent world light."""
import os
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def main():
    jars = list((Path.home() / '.gradle/caches/modules-2/files-2.1').rglob('*.jar'))
    preferred = ('guava-17.0.jar', 'commons-lang3-3.3.2.jar', 'log4j-api-2.0-beta9-fixed.jar',
                 'log4j-core-2.0-beta9-fixed.jar', 'gson-2.2.4.jar')
    cp = [str(ROOT / 'build/classes/java/main'), str(ROOT / 'build/classes/java/patchedMc')]
    cp += [str(next(jar for jar in jars if jar.name == name)) for name in preferred]
    cp += [str(ROOT / 'src/main/resources'), str(ROOT / 'build/resources/patchedMc')]
    cp += [str(jar) for jar in jars if 'jabel' not in jar.name and 'byte-buddy' not in jar.name]
    source = r'''import java.lang.reflect.*;
import net.minecraft.world.IBlockAccess;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterBlock;
public class RemasterGuidanceCheck {
 static int checks;
 static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 static RemasterBlock block(String id,String category){
  return new RemasterBlock("gtsr:"+id,category,false,false,"boxes",
    new double[][][]{{{.1,0,.1,.9,.9,.9}}},new String[][]{{"a","a","a","a","a","a"}});
 }
 static IBlockAccess world(RemasterBlock block,int metadata){
  return (IBlockAccess)Proxy.newProxyInstance(IBlockAccess.class.getClassLoader(),new Class[]{IBlockAccess.class},
   (proxy,method,args)->method.getName().equals("getBlockMetadata")?metadata:
    method.getName().equals("getBlock")?block:method.getReturnType()==int.class?0:
    method.getReturnType()==boolean.class?false:null);
 }
 public static void main(String[] args){
  RemasterBlock notice=block("draft_notice_board","decoration");
  check(notice.hasTileEntity(0),"authored entrance notice must carry a readable node tile");
  check(notice.isInteractive(),"notice must use the server interaction path");
  RemasterBlock lens=block("draft7_phase_lens","operation");
  int active=lens.getLightValue(world(lens,0),0,0,0);
  int solved=lens.getLightValue(world(lens,4),0,0,0);
  check(active>=8,"unsolved clue must emit actual world light");
  check(solved==0,"completed clue light must turn off");
  check(lens.getLightValue(world(lens,1),0,0,0)==active,"orientation does not change clue brightness");
  check(lens.getLightValue(world(lens,0),0,0,0)==active,"reset restores clue light");
  RemasterBlock beam=block("draft_beam_socket","decoration");
  check(!beam.isInteractive(),"structural decoration must not allocate millions of empty tiles");
  check(beam.getLightValue(world(beam,0),0,0,0)==0,"ordinary architecture does not glow");
  System.out.println("passed: "+checks+" real block notice / light / completion / reset checks");
 }
}'''
    with tempfile.TemporaryDirectory(prefix='gtsr-guidance-') as folder:
        tmp = Path(folder)
        java = tmp / 'RemasterGuidanceCheck.java'
        java.write_text(source, encoding='utf-8')
        args = tmp / 'javac.args'
        block = ROOT / 'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/remaster/RemasterBlock.java'
        tile = block.with_name('TileRemasterNode.java')
        classpath = os.pathsep.join(cp).replace('\\', '/')
        args.write_text('-encoding UTF-8\n-cp "' + classpath + '"\n-d "' + str(tmp).replace('\\', '/')
                        + '"\n"' + str(block).replace('\\', '/') + '"\n"'
                        + str(tile).replace('\\', '/') + '"\n"'
                        + str(java).replace('\\', '/') + '"', encoding='utf-8')
        subprocess.run(['javac', '@' + str(args)], check=True)
        runtime_cp = os.pathsep.join([str(tmp), *cp]).replace('\\', '/')
        args.write_text('-cp "' + runtime_cp + '"\nRemasterGuidanceCheck', encoding='utf-8')
        subprocess.run(['java', '@' + str(args)], check=True)


if __name__ == '__main__':
    main()
