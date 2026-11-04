"""Compile the complete renderer with API stubs and render actual native/Modernity PNG bytes.

This tests resource reload, selection and face/UV submission, not a Minecraft or GPU run.
--source permits the same expectations to reproduce the bug on a saved pre-fix renderer.
"""
import argparse
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = ROOT / "src/main/java/com/miaokatze/gtsr/client/workeffects"

STUBS = r'''
class ResourceLocation {
    final String domain,path;
    ResourceLocation(String d,String p) { domain=d;path=p; }
    public String toString() { return domain+":"+path; }
}
interface IResourceManager { Resource getResource(ResourceLocation r) throws IOException; }
interface IReloadableResourceManager extends IResourceManager { void registerReloadListener(IResourceManagerReloadListener r); }
interface IResourceManagerReloadListener { void onResourceManagerReload(IResourceManager r); }
class Resource { final Path path; Resource(Path p){path=p;} InputStream getInputStream() throws IOException{return Files.newInputStream(path);} }
class Resources implements IResourceManager {
    Path base,foreground;
    public Resource getResource(ResourceLocation r) { return new Resource(r.domain.equals("gtsr") ? foreground : base.resolve(r.path.substring(r.path.lastIndexOf('/')+1))); }
}
class Minecraft {
    static final Minecraft SINGLE=new Minecraft(); final TextureManager textures=new TextureManager();
    static Minecraft getMinecraft(){return SINGLE;}
    IResourceManager getResourceManager(){return null;} TextureManager getTextureManager(){return textures;}
}
class TextureManager { ResourceLocation last; void bindTexture(ResourceLocation r){last=r;} }
class Vec3Impl { final int a,b,c; Vec3Impl(int x,int y,int z){a=x;b=y;c=z;} int get0(){return a;} int get1(){return b;} int get2(){return c;} }
class Facing { Vec3Impl getWorldOffset(Vec3Impl v){return v;} }
interface MachineVisualHost { IGregTechTileEntity getBaseMetaTileEntity(); Facing getExtendedFacing(); }
class Host implements MachineVisualHost {
    final IGregTechTileEntity base=new IGregTechTileEntity();
    public IGregTechTileEntity getBaseMetaTileEntity(){return base;} public Facing getExtendedFacing(){return new Facing();}
}
class MTESiemensMartinFurnace extends Host {}
class MTEReinforcedBrickBlastFurnace extends Host {}
class IGregTechTileEntity { final World world=new World(); World getWorld(){return world;} int getXCoord(){return 0;} int getYCoord(){return 0;} int getZCoord(){return 0;} }
class World {
    int meta=14; boolean exposed=true;
    boolean blockExists(int x,int y,int z){return true;} Block getBlock(int x,int y,int z){return GregTechAPI.sBlockCasings3;}
    int getBlockMetadata(int x,int y,int z){return meta;}
}
class Block { boolean shouldSideBeRendered(World w,int x,int y,int z,int s){return w.exposed;} }
class GregTechAPI { static final Block sBlockCasings3=new Block(); }
class CloudParticleQueue { static boolean live=true; static boolean isLive(MachineVisualHost h,World w){return live;} }
class WorkEffectDefinition {
    record Cell(int a,int b,int c) {}
    List<Cell> fireboxes(){return List.of(new Cell(0,0,0));}
}
class WorkEffectDefinitions { static WorkEffectDefinition forHost(MachineVisualHost h){return new WorkEffectDefinition();} }
class Tessellator {
    static final Tessellator instance=new Tessellator(); final List<double[]> vertices=new ArrayList<>();
    void startDrawingQuads(){} void setBrightness(int i){} void setColorOpaque_F(float a,float b,float c){}
    void setNormal(float a,float b,float c){} void draw(){}
    void addVertexWithUV(double x,double y,double z,double u,double v){vertices.add(new double[]{x,y,z,u,v});}
}
class GL11 {
    static final int GL_ALL_ATTRIB_BITS=1,GL_LIGHTING=2,GL_DEPTH_TEST=3,GL_BLEND=4,GL_SRC_ALPHA=5,
        GL_ONE_MINUS_SRC_ALPHA=6,GL_ALPHA_TEST=7,GL_GREATER=8,GL_POLYGON_OFFSET_FILL=9;
    static void glPushAttrib(int i){} static void glPopAttrib(){} static void glDisable(int i){} static void glEnable(int i){}
    static void glDepthMask(boolean b){} static void glBlendFunc(int a,int b){} static void glAlphaFunc(int a,float b){}
    static void glPolygonOffset(float a,float b){}
}
class FireboxResourceContract {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void render(Host host,boolean active){Tessellator.instance.vertices.clear();WorkEffectRenderer.render(host,active);}
    static void pack(Resources resources,Host host,Path pack,int expectedPixels) {
        resources.base=pack;
        new WorkEffectRenderer().onResourceManagerReload(resources);
        render(host,true);
        List<double[]> vertices=Tessellator.instance.vertices;
        check(!vertices.isEmpty(),"working firebox must render current resource-pack openings: "+pack);
        double area=0;
        for(int i=0;i<vertices.size();i+=4) {
            double[] a=vertices.get(i),c=vertices.get(i+2);
            try {
                BufferedImage foreground=ImageIO.read(resources.foreground.toFile());
                for(int y=0;y<16;y++)for(int x=0;x<16;x++) {
                    double u=(x+.5)/16,v=(y+.5)/16;
                    if(u>=Math.min(a[3],c[3]) && u<Math.max(a[3],c[3])
                        && v>=Math.min(a[4],c[4]) && v<Math.max(a[4],c[4])
                        && (foreground.getRGB(x,y)>>>24)!=0)area+=1.0/256;
                }
            } catch(IOException failure) {throw new RuntimeException(failure);}
            for(int j=0;j<4;j++) {
                double[] v=vertices.get(i+j);
                check(Math.abs(v[1]-(1-v[4]))<1e-12,"geometry must crop with foreground UV");
            }
        }
        check(Math.abs(area*256/4-expectedPixels)<1e-10,"only aperture pixels must be covered; got "+area*256/4);
        check(Minecraft.getMinecraft().textures.last.domain.equals("gtsr"),"heat foreground remains separate from base icon");
        render(host,false);check(Tessellator.instance.vertices.isEmpty(),"idle must not render");
        host.base.world.exposed=false;render(host,true);check(Tessellator.instance.vertices.isEmpty(),"hidden faces must not render");host.base.world.exposed=true;
        CloudParticleQueue.live=false;render(host,true);check(Tessellator.instance.vertices.isEmpty(),"stale owner must not render");CloudParticleQueue.live=true;
        System.out.println("PASS actual renderer reload/render "+pack+" pixels="+expectedPixels);
    }
    public static void main(String[] args) throws Exception {
        Resources r=new Resources();r.foreground=Paths.get(args[0]);Host host=new Host();
        for(int meta:new int[]{13,14}) {
            host.base.world.meta=meta;
            pack(r,host,Paths.get(args[1]),48);pack(r,host,Paths.get(args[2]),36);
        }
        MTESiemensMartinFurnace steel=new MTESiemensMartinFurnace();steel.base.world.meta=13;
        render(steel,true);check(Tessellator.instance.vertices.isEmpty(),"steel-only machine must reject bronze");
        System.out.println("PASS steel-only metadata guard; Minecraft/GPU=NOT_RUN");
    }
}
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gt", type=Path, required=True)
    parser.add_argument("--modernity", type=Path, required=True)
    parser.add_argument("--source", type=Path, default=PACKAGE / "WorkEffectRenderer.java")
    parser.add_argument("--jar", type=Path, help="Optional candidate jar: verify its foreground matches the tested resource")
    parser.add_argument("--out", type=Path, default=ROOT / "build/v2112-firebox-contract")
    args = parser.parse_args()
    if args.jar:
        with zipfile.ZipFile(args.jar) as archive:
            assert archive.read("assets/gtsr/workeffects/firebox_grate.png") == (ROOT / "src/main/resources/assets/gtsr/workeffects/firebox_grate.png").read_bytes(), "Candidate foreground differs from tested source resource"
    args.out.mkdir(parents=True, exist_ok=True)
    native = args.out / "native"
    native.mkdir(exist_ok=True)
    with zipfile.ZipFile(args.gt) as archive:
        for material in ("BRONZE", "STEEL"):
            name = "MACHINE_CASING_FIREBOX_" + material + ".png"
            (native / name).write_bytes(archive.read("assets/gregtech/textures/blocks/iconsets/" + name))
    source = args.source.read_text(encoding="utf-8")
    source = re.sub(r"(?m)^package .*;\s*|^import .*;\s*|^@SideOnly.*\s*", "", source)
    source = source.replace("public final class WorkEffectRenderer", "final class WorkEffectRenderer")
    header = "import java.awt.image.*;import java.io.*;import java.nio.file.*;import java.security.*;import java.util.*;import javax.imageio.*;\n"
    body = (PACKAGE / "FireboxGrateMask.java").read_text(encoding="utf-8")
    body = re.sub(r"(?m)^package .*;\s*|^import .*;\s*", "", body)
    # Nested type import is normally supplied by the production import list.
    source = source.replace("for (Cell cell", "for (WorkEffectDefinition.Cell cell")
    java = args.out / "FireboxResourceContract.java"
    java.write_text(header + source + body + STUBS, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(args.out), str(java)], check=True)
    subprocess.run(["java", "-cp", str(args.out), "FireboxResourceContract",
                    str(ROOT / "src/main/resources/assets/gtsr/workeffects/firebox_grate.png"),
                    str(native), str(args.modernity / "assets/gregtech/textures/blocks/iconsets")], check=True)


if __name__ == "__main__":
    main()
