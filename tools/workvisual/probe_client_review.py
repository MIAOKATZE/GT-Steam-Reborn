"""Execute real client/clearance/renderer sources against explicit, deterministic MC boundary fakes.

This is source-call-site feedback, not a production JAR, Forge/GL or native performance acceptance.
Never launches Gradle or Minecraft. The fixture definition values come from packaged motions.json.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = "com/miaokatze/gtsr/client/machinevisual"

STUBS = {
    "net/minecraft/block/Block": "public class Block {}",
    "net/minecraft/tileentity/TileEntity": "public class TileEntity { public boolean invalid; public boolean isInvalid(){return invalid;} }",
    "net/minecraft/world/World": """public class World {
      public static class Provider { public int dimensionId; } public final Provider provider=new Provider();
      public long queries; public final java.util.Map<String,net.minecraft.tileentity.TileEntity> tiles=new java.util.HashMap<>();
      public final net.minecraft.block.Block block=new net.minecraft.block.Block(); public final java.util.Set<String> obstacles=new java.util.HashSet<>();
      public static String key(int x,int y,int z){return x+":"+y+":"+z;}
      public boolean blockExists(int x,int y,int z){queries++;return true;}
      public net.minecraft.tileentity.TileEntity getTileEntity(int x,int y,int z){queries++;return tiles.get(key(x,y,z));}
      public net.minecraft.block.Block getBlock(int x,int y,int z){queries++;return block;}
      public int getBlockMetadata(int x,int y,int z){queries++;return 0;}
      public boolean isAirBlock(int x,int y,int z){queries++;return !obstacles.contains(key(x,y,z));}
    }""",
    "net/minecraft/client/Minecraft": """public class Minecraft {
      private static final Minecraft INSTANCE=new Minecraft(); public static Minecraft getMinecraft(){return INSTANCE;}
      public net.minecraft.world.World theWorld; public final Player thePlayer=new Player(); public boolean paused;
      public boolean isSingleplayer(){return true;} public boolean isGamePaused(){return paused;}
      public static class Player { public double x; public boolean alwaysNear; public double getDistanceSq(double x,double y,double z){return alwaysNear?0:(this.x-x)*(this.x-x)+z*z;} }
      public static class Textures { public void bindTexture(Object texture){} } public Textures getTextureManager(){return new Textures();}
    }""",
    "cpw/mods/fml/common/FMLCommonHandler": """public class FMLCommonHandler {public static FMLCommonHandler instance(){return new FMLCommonHandler();} public FMLCommonHandler bus(){return this;} public void register(Object o){}}""",
    "cpw/mods/fml/common/eventhandler/SubscribeEvent": "public @interface SubscribeEvent {}",
    "cpw/mods/fml/common/gameevent/TickEvent": """public class TickEvent { public enum Phase { START, END } public static class ClientTickEvent {public final Phase phase=Phase.END;} public static class RenderTickEvent {public final Phase phase=Phase.START;} }""",
    "gregtech/api/interfaces/tileentity/IGregTechTileEntity": """public interface IGregTechTileEntity {net.minecraft.world.World getWorld(); int getXCoord(); int getYCoord(); int getZCoord(); Object getMetaTileEntity(); int getMetaTileID();}""",
    "com/gtnewhorizon/structurelib/alignment/enumerable/ExtendedFacing": """public enum ExtendedFacing { NORTH, EAST;
      public int getIndex(){return ordinal();} public void getWorldOffset(int[] a,int[] b){b[0]=this==NORTH?a[0]:a[2];b[1]=a[1];b[2]=this==NORTH?a[2]:-a[0];}
      public void getWorldOffset(double[] a,double[] b){b[0]=this==NORTH?a[0]:a[2];b[1]=a[1];b[2]=this==NORTH?a[2]:-a[0];}
    }""",
    "com/miaokatze/gtsr/common/machinevisual/MachineVisualHost": """public interface MachineVisualHost {gregtech.api.interfaces.tileentity.IGregTechTileEntity getBaseMetaTileEntity(); MachineVisualDefinition visualDefinition(); com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing getExtendedFacing();}""",
    "com/miaokatze/gtsr/common/machinevisual/MachineVisualSnapshot": """public final class MachineVisualSnapshot { public int dimension,x,y,z,registryId=1,orientation,flags=7,progress,duration=100; public long session=1,nonce,generation=1,revision=1,sequence=1; public boolean formed(){return (flags&1)!=0;} public boolean working(){return (flags&4)!=0;} public boolean motionShape(){return (flags&2)!=0;} }""",
    "com/miaokatze/gtsr/common/machinevisual/MachineVisualNetwork": "public final class MachineVisualNetwork {public static void request(MachineVisualHost h,long nonce){}}",
    "com/miaokatze/gtsr/config/Config": 'public class Config {public static String machineVisualQuality="STANDARD";}',
    "com/miaokatze/gtsr/client/workeffects/WorkEffectParticles": "public class WorkEffectParticles {public static void tick(com.miaokatze.gtsr.common.machinevisual.MachineVisualHost host,long tick){}}",
    "com/miaokatze/gtsr/client/workeffects/WorkEffectRenderer": "public class WorkEffectRenderer {public static void render(com.miaokatze.gtsr.common.machinevisual.MachineVisualHost host,boolean working){}}",
    "com/miaokatze/gtsr/client/machinevisual/MachineVisualVisibility": """final class MachineVisualVisibility {
      static final java.util.Set<MachineVisualClient.Entry> owners=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
      static void register(){} static void prune(){} static void clear(){owners.clear();}
      static void publish(MachineVisualClient.Entry e,int next){e.restoringMask|=e.mask&~next;e.mask=next;if((e.mask|e.restoringMask)!=0)owners.add(e);}
      static void remove(MachineVisualClient.Entry e){e.retired=true;publish(e,0);}
      static int[] heldCosts(){int m=0,g=0;for(MachineVisualClient.Entry e:owners){int bits=e.mask|e.restoringMask;m+=MachineVisualClient.memberCost(e,bits);g+=Integer.bitCount(bits);}return new int[]{m,g};}
      static int ready(MachineVisualClient.Entry e){return e.mask;} static boolean uploadedHidden(MachineVisualClient.Entry e,int x,int y,int z){return true;}
      static void beginProxy(){} static void endProxy(){} static java.util.Map<String,Object> diagnostics(java.util.Collection<MachineVisualClient.Entry> e){return new java.util.HashMap<>();}
    }""",
    "net/minecraft/client/renderer/RenderBlocks": "public class RenderBlocks {public boolean renderAllFaces; public RenderBlocks(net.minecraft.world.World w){} public void renderBlockByRenderType(net.minecraft.block.Block b,int x,int y,int z){}}",
    "net/minecraft/client/renderer/Tessellator": "public class Tessellator {public static final Tessellator instance=new Tessellator(); public void startDrawingQuads(){} public void setTranslation(double x,double y,double z){} public int draw(){return 1;}}",
    "net/minecraft/client/renderer/texture/TextureMap": "public class TextureMap {public static final Object locationBlocksTexture=new Object();}",
    "org/lwjgl/opengl/GL11": """public class GL11 {public static final int GL_ENABLE_BIT=1,GL_LIGHTING_BIT=2,GL_CURRENT_BIT=4,GL_COLOR_BUFFER_BIT=8,GL_LIGHTING=16,GL_CULL_FACE=32,GL_ALPHA_TEST=64;
      public static void glPushMatrix(){} public static void glPopMatrix(){} public static void glPushAttrib(int v){} public static void glPopAttrib(){}
      public static void glTranslated(double x,double y,double z){} public static void glRotated(double v,double x,double y,double z){} public static void glDisable(int v){} public static void glEnable(int v){} public static void glColor4f(float r,float g,float b,float a){}
    }""",
}


def definition_source():
    definitions = json.loads((ROOT / "src/main/resources/assets/gtsr/machinevisual/motions.json").read_text(encoding="utf-8"))["definitions"]
    entries = []
    for definition in definitions:
        # This historical feedback contract exercises the original five-machine profile.
        # Rebuilt turbines have separate full-cube fixture and native TestLab contracts.
        if definition["id"] not in {"air_compressor", "atmospheric_centrifuge", "steam_fluid_drill",
                                    "crust_steam_borer", "gear_steam_compressor"}:
            continue
        groups = []
        for g in definition["groups"]:
            cells = ",".join("new Cell(%d,%d,%d)" % (c["x"], c["y"], c["z"]) for c in g["members"])
            groups.append('new Group("%s","%s",%s,%s,%s,%s,new Cell[]{%s})' % (g["kind"], g["axis"], g["travel"], *g["pivot"], cells))
        entries.append('if(id.equals("%s"))return new MachineVisualDefinition(new Group[]{%s});' % (definition["id"], ",".join(groups)))
    return """public final class MachineVisualDefinition {
      public long revision=1; public final java.util.List<Group> groups;
      public MachineVisualDefinition(Group[] g){groups=java.util.Arrays.asList(g);}
      public static MachineVisualDefinition get(String id){%s throw new IllegalArgumentException(id);}
      public static final class Cell {public final int x,y,z;Cell(int x,int y,int z){this.x=x;this.y=y;this.z=z;}public boolean matches(net.minecraft.block.Block b,int m){return true;}}
      public static final class Group {public final String kind,axis;public final double travel,pivotX,pivotY,pivotZ;public final java.util.List<Cell> members;public long sampled;public int samples;
        Group(String k,String a,double t,double x,double y,double z,Cell[] c){kind=k;axis=a;travel=t;pivotX=x;pivotY=y;pivotZ=z;members=java.util.Arrays.asList(c);}
        public double sample(long ms){samples++;sampled=ms;return ms/1000.;}}
    }""" % " ".join(entries)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=ROOT / "build/client-review-feedback")
    parser.add_argument("--java", default="java")
    parser.add_argument("--javac", default="javac")
    args = parser.parse_args()
    out = args.out.resolve()
    sources, classes = out / "src", out / "classes"
    sources.mkdir(parents=True, exist_ok=True)
    classes.mkdir(exist_ok=True)
    stubs = dict(STUBS)
    stubs["com/miaokatze/gtsr/common/machinevisual/MachineVisualDefinition"] = definition_source()
    files = []
    for name, body in stubs.items():
        path = sources / (name + ".java")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("package " + name.rpartition("/")[0].replace("/", ".") + ";\n" + body, encoding="utf-8")
        files.append(path)
    files += [ROOT / ("src/main/java/" + PACKAGE + "/" + name + ".java") for name in ("MachineVisualClient", "MachineVisualClearance", "MachineVisualRenderer")]
    files += [ROOT / "src/main/java/com/miaokatze/gtsr/common/machinevisual/MachineVisualSpace.java", Path(__file__).with_name("ClientReviewContract.java")]
    receipt = {"status": "FAIL", "boundary": __doc__, "sources": {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in files}, "commands": []}
    commands = [("compile", [args.javac, "--release", "17", "-encoding", "UTF-8", "-proc:none", "-d", str(classes), *map(str, files)]), ("feedback", [args.java, "-cp", str(classes), "com.miaokatze.gtsr.client.machinevisual.ClientReviewContract"])]
    for label, command in commands:
        result = subprocess.run(command, capture_output=True)
        (out / (label + ".stdout.log")).write_bytes(result.stdout)
        (out / (label + ".stderr.log")).write_bytes(result.stderr)
        receipt["commands"].append({"name": label, "command": command, "exitCode": result.returncode})
        print(result.stdout.decode("utf-8", errors="replace").strip())
        if result.returncode:
            print(result.stderr.decode("utf-8", errors="replace").strip())
            break
    else:
        receipt["status"] = "PASS"
    (out / "receipt.json").write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(receipt["status"] + " source call-site feedback; native game/GL/performance NOT_RUN; " + str(out / "receipt.json"))
    return 0 if receipt["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
