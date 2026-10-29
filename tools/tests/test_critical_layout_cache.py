"""Compile layout selection/cache against synthetic voxels; never load model assets or start Minecraft."""
from pathlib import Path
import shutil
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[2]
PACKAGE = "com.miaokatze.gtsr.common.critical"
PRODUCTION = ROOT / "src/main/java" / PACKAGE.replace(".", "/")

STUBS = {
    "CriticalTier": "enum CriticalTier { T1, T2, T3 }",
    "CriticalConfiguration": """final class CriticalConfiguration {
        final CriticalTier tier; final CriticalMachineKind kind; final int parallel, speed, economy;
        CriticalConfiguration(CriticalTier t, CriticalMachineKind k, int p, int s, int e) {
            tier=t; kind=k; parallel=p; speed=s; economy=e;
        }
    }""",
    "CriticalMachineState": """final class CriticalMachineState {
        int getGeometryVersion() { return 2; } int getV2LayoutRevision() { return 31; }
        boolean usesLegacyV2Geometry() { return false; }
        CriticalTier getTier() { return CriticalTier.T3; }
        CriticalMachineKind getKind() { return CriticalMachineKind.SUN; }
        CriticalConfiguration getConfiguration() { return new CriticalConfiguration(getTier(),getKind(),8,0,0); }
    }""",
    "CriticalMaterials": """final class CriticalMaterials {
        static final class MaterialBlock { final String materialId;
            MaterialBlock(String id) { materialId=id; }
        }
    }""",
    "CriticalGeometry": """final class CriticalGeometry {
        static int compilations;
        static final class Model { final java.util.List<Voxel> voxels;
            Model(java.util.List<Voxel> v) { voxels=v; }
        }
        static final class Voxel {
            final int x,y,z,stage; final String material,group,model; final Object block;
            Voxel(int x,int y,int z,int stage,String material,String model) {
                this.x=x; this.y=y; this.z=z; this.stage=stage; this.material=material;
                this.model=model; group="static"; block=new CriticalMaterials.MaterialBlock(material);
            }
            long positionKey() { return positionKey(x,y,z); }
            static long positionKey(int x,int y,int z) {
                return ((long)x & 0xfffff)<<40 | ((long)y & 0xfffff)<<20 | ((long)z & 0xfffff);
            }
        }
        static Model getModel(String slug) {
            return new Model(java.util.List.of(new Voxel(50,4,47,slug.endsWith("loom")?0:4,
                slug.endsWith("loom")?"v2_reinforced_panel":"v2_alloy_frame",slug)));
        }
        static java.util.List<Voxel> get(CriticalTier tier,CriticalMachineKind kind) {
            return java.util.List.of(new Voxel(1,9,2,4,"alloy_frame","sun"));
        }
        static java.util.List<Voxel> compilePhysicalPlugins(CriticalConfiguration config,CriticalLayoutDefinition d) {
            compilations++; return java.util.List.of(new Voxel(2,8,1,3,"plugin",d.pluginSlug("parallel")));
        }
        static java.util.List<Voxel> compileOptionalWorkPorts(CriticalLayoutDefinition d) {
            return d.optionalWorkPorts ? java.util.List.of(new Voxel(50,5,34,1,"v2_gt_hatch","v2_optional_work_port"))
                : java.util.List.of();
        }
    }""",
}

CONTRACT = """
public final class CriticalLayoutCacheContract {
    static void require(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    static CriticalLayoutDefinition layout(int revision,boolean legacy) {
        return CriticalLayoutDefinition.select(2,revision,legacy,CriticalTier.T3,CriticalMachineKind.SUN);
    }
    static void immutable(Runnable edit) {
        try { edit.run(); throw new AssertionError("mutable cache result"); }
        catch(UnsupportedOperationException expected) { }
    }
    public static void main(String[] args) {
        require(layout(25,false).family==CriticalLayoutDefinition.Family.V25,"v25 boundary");
        require(layout(26,false).family==CriticalLayoutDefinition.Family.V27,"v27 boundary");
        require(layout(28,false).family==CriticalLayoutDefinition.Family.V30,"v30 boundary");
        require(!layout(29,false).optionalWorkPorts && layout(30,false).optionalWorkPorts,"optional port boundary");
        require(layout(31,true).family==CriticalLayoutDefinition.Family.LEGACY_V2,"legacy override");
        require(layout(31,false).frameSize==112 && layout(31,false).frameHeight==180,"data frame");
        require(layout(31,false).controllerZ(true)==47 && layout(31,false).controllerZ(false)==35,"controllers");
        require(layout(31,false).family==CriticalLayoutDefinition.Family.V31,"released v31 boundary");
        require(layout(31,false).baseSlug.equals("v2_v31_base_t3"),"released paid base freeze");
        require(layout(32,false).family==CriticalLayoutDefinition.Family.V32,"released v32 boundary");
        require(layout(32,false).baseSlug.equals("v2_v32_base_t3"),"released v32 paid base and bitmap freeze");
        require(layout(33,false).baseSlug.equals("v2_base_t3"),"approved floating hull base");
        require(layout(31,false).loomSlug.equals(layout(32,false).loomSlug),"shared manual loom");
        require(layout(31,false).pluginSlug("parallel").equals(layout(32,false).pluginSlug("parallel")),"shared v31 plugins");
        for(CriticalMachineKind kind:CriticalMachineKind.values()) {
            CriticalLayoutDefinition d=CriticalLayoutDefinition.select(2,31,false,CriticalTier.T3,kind);
            require(d.machineSlug.equals("v2_"+kind.key),"current body selection");
        }
        CriticalConfiguration config=new CriticalConfiguration(CriticalTier.T3,CriticalMachineKind.SUN,8,0,0);
        CriticalCompiledLayout current=CriticalCompiledLayout.get(layout(31,false),config);
        int count=CriticalGeometry.compilations;
        require(current==CriticalCompiledLayout.get(layout(31,false),config),"same family cache reuse");
        require(count==CriticalGeometry.compilations,"cache hit must not compile");
        require(current!=CriticalCompiledLayout.get(layout(32,false),config),"v31 and terrace split cache key");
        require(CriticalCompiledLayout.get(layout(32,false),config)!=CriticalCompiledLayout.get(layout(33,false),config),"v32 and hull split cache key");
        require(current.voxels().get(0).model.equals("v2_loom"),"manual loom coordinate ownership");
        require(current.top()==8 && current.loomTop()==4,"compiled height");
        require(current.hatchPorts().size()==2,"role port index");
        require(current.roles(CriticalGeometry.Voxel.positionKey(50,5,34)).contains(
            CriticalCompiledLayout.Role.OPTIONAL_WORK_PORT),"optional role index");
        require(current.roles(-1).isEmpty(),"absent role lookup");
        immutable(()->current.voxels().clear()); immutable(()->current.hatchPorts().clear());
        immutable(()->current.roles(CriticalGeometry.Voxel.positionKey(50,5,34)).clear());
        CriticalCompiledLayout legacy=CriticalCompiledLayout.get(layout(25,true),config);
        require(legacy.voxels().get(1).model.equals("v2_v30_sun"),"legacy paid coordinate ownership");
        require(CriticalCompiledLayout.get(layout(28,false),config)==
            CriticalCompiledLayout.get(layout(29,false),config),"equivalent revision cache key");
        require(CriticalCompiledLayout.get(layout(29,false),config)!=
            CriticalCompiledLayout.get(layout(30,false),config),"port revision must split key");
        CriticalCompiledLayout v1=CriticalCompiledLayout.v1(CriticalTier.T1,CriticalMachineKind.SUN);
        require(v1.top()==9 && v1.optionalWorkPorts().isEmpty(),"v1 cache path");
        for(int p=0;p<=8;p++) for(int s=0;s<=8-p;s++)
            CriticalCompiledLayout.get(layout(31,false),new CriticalConfiguration(CriticalTier.T3,CriticalMachineKind.SUN,p,s,0));
        require(current!=CriticalCompiledLayout.get(layout(31,false),config),"bounded LRU eviction");
        System.out.println("PASS layout boundaries, synthetic merge ownership, immutable role indices, cache hits and LRU eviction");
    }
}
"""


def main():
    javac, java = shutil.which("javac"), shutil.which("java")
    if not javac or not java:
        raise SystemExit("A JDK with javac/java on PATH is required")
    with tempfile.TemporaryDirectory(prefix="gtsr-layout-cache-") as folder:
        work = Path(folder)
        sources = work / "src"
        sources.mkdir()
        for name in ("CriticalLayoutDefinition", "CriticalCompiledLayout", "CriticalMachineKind"):
            shutil.copyfile(PRODUCTION / (name + ".java"), sources / (name + ".java"))
        for name, body in STUBS.items():
            (sources / (name + ".java")).write_text("package " + PACKAGE + ";\n" + body, encoding="utf-8")
        (sources / "CriticalLayoutCacheContract.java").write_text(
            "package " + PACKAGE + ";\n" + CONTRACT, encoding="utf-8")
        classes = work / "classes"
        classes.mkdir()
        subprocess.run([javac, "--release", "17", "-encoding", "UTF-8", "-proc:none", "-d", str(classes),
                        *map(str, sources.glob("*.java"))], check=True)
        subprocess.run([java, "-cp", str(classes), PACKAGE + ".CriticalLayoutCacheContract"], check=True)


if __name__ == "__main__":
    main()
