"""Execute exact hub method bodies with controlled storage/world fixtures (not a game integration test).

No network/build resolution. Native GT5U storePartial mutates its stack to the remainder;
Forge fill returns the committed amount. Fixtures exercise those contracts, including a
receiver/source changing its acceptance between simulation and commit. Set JAVA_HOME to
JDK 17+; --native-classpath uses real Forge/MC FluidStack, NBT and ItemStack with
FML bootstrap (include Forge resources and LaunchWrapper); --source-root can point at a pristine checkout for baseline red/green evidence.
"""
from pathlib import Path
import argparse
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]


def method(source, signature):
    start = source.index(signature)
    brace = source.index('{', start)
    depth = 1
    end = brace + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end].replace('@Override', '')


FIXTURES = r'''
import java.util.*;
public final class HubTransferContract {
    static int failures, checks;
    static void check(boolean ok, String name) {
        checks++; if (!ok) { failures++; System.err.println("FAIL " + name); }
    }
    static final class Fluid {
        final String name; Fluid(String n) { name=n; }
        String getName() { return name; }
    }
    static final Fluid STEAM = new Fluid("steam");
    static final class NBTTagCompound {
        String name; int amount;
        void setString(String k,String v) { name=v; }
        void setInteger(String k,int v) { amount=v; }
    }
    static final class FluidStack {
        final Fluid fluid; int amount;
        FluidStack(Fluid f,int n) { fluid=f; amount=n; }
        FluidStack(FluidStack f,int n) { this(f.fluid,n); }
        Fluid getFluid() { return fluid; }
        boolean isFluidEqual(FluidStack f) { return f!=null && fluid==f.fluid; }
        FluidStack copy() { return new FluidStack(fluid,amount); }
        static FluidStack loadFluidStackFromNBT(NBTTagCompound tag) { return new FluidStack(STEAM,tag.amount); }
    }
    static final class MTESteamHubOutputHatch {
        static boolean isAnySteamFluid(FluidStack f) { return f!=null && f.fluid==STEAM; }
        static boolean isSteamFluid(FluidStack f) { return isAnySteamFluid(f); }
    }
    enum ForgeDirection { UNKNOWN }
    static class TileEntity {}
    static final class World {
        final Provider provider=new Provider(); long time=1; boolean loaded=true;
        TileEntity tile;
        long getTotalWorldTime() { return time; }
        boolean blockExists(int x,int y,int z) { return loaded; }
        TileEntity getTileEntity(int x,int y,int z) { return tile; }
    }
    static final class Provider { int dimensionId; }
    static final class DimensionManager {
        static World world;
        static World getWorld(int dim) { return world; }
    }
    static final class HubTeleportUtil {
        static boolean ensureChunkLoaded(World w,int x,int z) { return w.loaded; }
    }
    interface IMetaTileEntity {}
    interface IHubCacheNode extends IMetaTileEntity {
        boolean isBoundToHubAt(int x,int y,int z,int dim);
        boolean isOutputMode();
    }
    static class IGregTechTileEntity extends TileEntity {
        World world; boolean dead; IMetaTileEntity meta;
        int stock=100, actual=30;
        int getXCoord() { return 0; } int getYCoord() { return 64; } int getZCoord() { return 0; }
        World getWorld() { return world; }
        IMetaTileEntity getMetaTileEntity() { return meta; }
        boolean isDead() { return dead; }
        boolean isInvalidTileEntity() { return dead; }
        int fill(ForgeDirection d,FluidStack f,boolean commit) { return Math.min(f.amount,actual); }
        FluidStack drain(ForgeDirection d,int n,boolean commit) {
            int take=Math.min(n,Math.min(stock,commit?actual:stock));
            if(commit)stock-=take;
            return take<=0?null:new FluidStack(STEAM,take);
        }
        FluidStack drain(ForgeDirection d,FluidStack f,boolean commit) { return drain(d,f.amount,commit); }
    }
    static class Node implements IHubCacheNode {
        boolean own=true, output=true;
        public boolean isBoundToHubAt(int x,int y,int z,int dim) { return own; }
        public boolean isOutputMode() { return output; }
    }
    static class BoundCacheNode {
        int x=0,y=64,z=0,dimensionId; boolean isOutputMode;
        IGregTechTileEntity cachedTile; long lastLookupTick,nextLookupTick; boolean lastLookupLoaded;
    }
    static class MTERemoteWorkerNode implements IMetaTileEntity {
        boolean own=true; IGregTechTileEntity base;
        boolean isBoundToHubAt(int x,int y,int z,int dim) { return own; }
        IGregTechTileEntity getBaseMetaTileEntity() { return base; }
    }
    static final class BoundDrillNode {
        int x,y=64,z,dimensionId;
        MTERemoteWorkerNode cachedWorker; long lastLookupTick,nextLookupTick; boolean lastLookupLoaded;
    }
    static final class ItemStack {
        int stackSize; ItemStack(int n) { stackSize=n; }
        ItemStack copy() { return new ItemStack(stackSize); }
    }
    static class MTEHatch {
        int simulated=100,actual=30;
        int fill(FluidStack f,boolean commit) { return Math.min(f.amount,commit?actual:simulated); }
    }
'''


def build(root):
    machine = root / 'src/main/java/com/miaokatze/gtsr/common/machine'
    steam = (machine/'MTESteamHubArray.java').read_text()
    water = (machine/'MTEWaterHubArray.java').read_text()
    drill = (machine/'MTESingularityDrillingHub.java').read_text()
    hub = (machine/'base/MTEHubArrayBase.java').read_text()
    pieces=[FIXTURES]
    for cls,source,fluid in [('Steam',steam,'Steam'),('Water',water,'Water')]:
        typ='FluidStack' if fluid=='Steam' else 'String'
        pieces.append('static class '+cls+' {\n'+f'long m{fluid}Stored=100, capacity=1000; {typ} mStoredFluidType='+('new FluidStack(STEAM,0)' if fluid=='Steam' else '"steam"')+'; boolean mOverflowInput; int mSetTier;\nlong getTotalCapacity(){return capacity;} boolean hasReinforcedChipInstalled(){return false;}\n')
        for sig in [f'public int receive{fluid}(',f'public FluidStack extract{fluid}(', 'protected void transferOneNode(']:
            pieces.append(method(source,sig))
        if fluid=='Water':
            pieces.append(method(source,'private static boolean isWaterFluid('))
            pieces.append(method(source,'private static NBTTagCompound createFluidTag('))
        pieces.append('}\n')
    pieces.append('''static class Drill {
        int simulated=100, actual=30;
        final List<MTEHatch> mOutputHatches=new ArrayList<>();
        int probeNodeItemOutputCapacity(ItemStack s){return Math.min(s.stackSize,simulated);}
        void gtsr$storeItemIntoBusses(ItemStack s,boolean simulation){s.stackSize-=Math.min(s.stackSize,simulation?simulated:actual);}
    ''')
    pieces.append(method(drill,'public int tryStoreNodeItemOutput('))
    pieces.append(method(drill,'private int gtsr$fillOutputHatches('))
    pieces.append('}\n')
    pieces.append('''static class Hub {
        static final int BOUND_NODE_RETRY_INTERVAL=20;
        final List<BoundCacheNode> mBoundNodes=new ArrayList<>();
        IGregTechTileEntity base; int transfers; boolean lastOutput;
        IGregTechTileEntity getBaseMetaTileEntity(){return base;}
        boolean hasChipInstalled(){return true;}
        String resolveNodeType(IHubCacheNode n){return "water";}
        boolean acceptsNodeType(String s){return true;}
        int getNodeTransferRate(IGregTechTileEntity t){return 100;}
        void transferOneNode(BoundCacheNode b,IGregTechTileEntity t,int n){transfers++;lastOutput=b.isOutputMode;}
    ''')
    pieces.append(method(hub,'protected IHubCacheNode resolveCacheNode(BoundCacheNode'))
    pieces.append(method(hub,'protected void transferWithBoundNodes('))
    for name in ('private boolean isNodeBoundToThisHub(', 'private boolean isLiveCachedNode('):
        if name in hub: pieces.append(method(hub,name))
    pieces.append('}\n')
    pieces.append('''static class WorkerHub {
        IGregTechTileEntity base;
        IGregTechTileEntity getBaseMetaTileEntity(){return base;}
    ''')
    pieces.append(method(drill,'private MTERemoteWorkerNode resolveWorkerNode(BoundDrillNode'))
    for name in ('private boolean isWorkerBoundToThisHub(', 'private boolean isLiveCachedWorker('):
        if name in drill: pieces.append(method(drill,name))
    pieces.append('}\n')
    pieces.append(r'''
    static Hub hub(Node n) {
        World world=new World(); DimensionManager.world=world;
        IGregTechTileEntity tile=new IGregTechTileEntity();tile.world=world;tile.meta=n;world.tile=tile;
        Hub hub=new Hub();hub.base=new IGregTechTileEntity();hub.base.world=new World();
        hub.mBoundNodes.add(new BoundCacheNode()); return hub;
    }
    public static void main(String[] args) {
        Steam s=new Steam();
        check(s.extractSteam(-1,true)==null && s.mSteamStored==100,"negative steam drain cannot mint stock");
        s=new Steam();check(s.extractSteam(0,true)==null && s.mSteamStored==100,"zero steam drain is empty");
        s=new Steam();s.mOverflowInput=true;
        check(s.receiveSteam(new FluidStack(STEAM,-1),true)==0 && s.mSteamStored==100,"negative overflow steam fill cannot destroy stock");
        s=new Steam();s.capacity=50;
        check(s.receiveSteam(new FluidStack(STEAM,1),true)==0 && s.mSteamStored==100,"overcapacity steam fill reports zero, leaves explicit shrink policy to tick");
        Water w=new Water();
        check(w.extractWater(Integer.MIN_VALUE,true)==null && w.mWaterStored==100,"negative water drain cannot mint stock");
        w=new Water();check(w.extractWater(0,true)==null && w.mWaterStored==100,"zero water drain is empty");
        w=new Water();w.mOverflowInput=true;
        check(w.receiveWater(new FluidStack(STEAM,-1),true)==0 && w.mWaterStored==100,"negative overflow water fill cannot destroy stock");
        w=new Water();w.capacity=50;
        check(w.receiveWater(new FluidStack(STEAM,1),true)==0 && w.mWaterStored==100,"overcapacity water fill reports zero");
        s=new Steam();s.capacity=110;s.mOverflowInput=true;
        check(s.receiveSteam(new FluidStack(STEAM,30),true)==30 && s.mSteamStored==110,"documented overflow disposal remains supported");
        w=new Water();w.capacity=110;
        check(w.receiveWater(new FluidStack(STEAM,30),false)==10 && w.mWaterStored==100,"simulation remains read-only");
        Drill d=new Drill(); ItemStack item=new ItemStack(100);
        check(d.tryStoreNodeItemOutput(item)==70 && item.stackSize==100,"native bus actual remainder survives optimistic simulation");
        MTEHatch h=new MTEHatch();d.mOutputHatches.add(h);FluidStack fluid=new FluidStack(STEAM,100);
        check(d.gtsr$fillOutputHatches(fluid)==70 && fluid.amount==70,"native fluid actual remainder survives optimistic simulation");
        d.actual=0;check(d.tryStoreNodeItemOutput(item)==100,"zero actual item insertion keeps all output");
        h.actual=0;fluid=new FluidStack(STEAM,100);
        check(d.gtsr$fillOutputHatches(fluid)==100 && fluid.amount==100,"zero actual fluid insertion keeps all output");
        BoundCacheNode b=new BoundCacheNode();IGregTechTileEntity t=new IGregTechTileEntity();
        s=new Steam();s.mSteamStored=0;s.mStoredFluidType=null;s.transferOneNode(b,t,100);
        check(s.mSteamStored==30 && t.stock==70,"steam input credits only actual drain receipt");
        t=new IGregTechTileEntity();w=new Water();w.mWaterStored=0;w.mStoredFluidType=null;w.transferOneNode(b,t,100);
        check(w.mWaterStored==30 && t.stock==70,"water input credits only actual drain receipt");
        Node n=new Node();n.own=false;Hub hub=hub(n);hub.transferWithBoundNodes();
        check(hub.transfers==0,"replacement node bound elsewhere cannot transfer");
        n=new Node();hub=hub(n);hub.transferWithBoundNodes();
        check(hub.transfers==1 && hub.lastOutput,"live node direction overrides stale hub record");
        hub.mBoundNodes.get(0).cachedTile.dead=true;
        check(hub.resolveCacheNode(hub.mBoundNodes.get(0),false)==null,"dead cached tile cannot transfer in retry window");
        n=new Node();hub=hub(n);hub.transferWithBoundNodes();DimensionManager.world=null;
        check(hub.resolveCacheNode(hub.mBoundNodes.get(0),false)==null,"unloaded dimension cannot retain live cached node");
        n=new Node();hub=hub(n);hub.transferWithBoundNodes();
        World replacement=new World();DimensionManager.world=replacement;
        check(hub.resolveCacheNode(hub.mBoundNodes.get(0),false)==null,"replaced dimension cannot reuse prior world tile");
        WorkerHub wh=new WorkerHub();wh.base=hub.base;
        MTERemoteWorkerNode worker=new MTERemoteWorkerNode();worker.own=false;
        IGregTechTileEntity wt=new IGregTechTileEntity();wt.world=replacement;wt.meta=worker;worker.base=wt;replacement.tile=wt;
        BoundDrillNode wb=new BoundDrillNode();
        check(wh.resolveWorkerNode(wb,false)==null,"drilling actions cannot resolve a worker bound to another hub");
        worker.own=true;wb=new BoundDrillNode();
        check(wh.resolveWorkerNode(wb,false)==worker,"valid drilling worker resolves");
        wt.dead=true;
        check(wh.resolveWorkerNode(wb,false)==null,"drilling actions cannot reuse dead worker in retry window");
        wt.dead=false;wb=new BoundDrillNode();wh.resolveWorkerNode(wb,false);DimensionManager.world=null;
        check(wh.resolveWorkerNode(wb,false)==null,"drilling cached worker cannot outlive dimension");
        System.out.println((failures==0?"PASS ":"FAIL ")+checks+" hub contracts; failures="+failures+"; controlled fixtures, extracted production bodies");
        if(failures!=0)throw new AssertionError(failures+" hub contracts failed");
    }
}
''')
    return '\n'.join(pieces)


def with_native_storage(source):
    for marker in ('static final class Fluid {', 'static final class NBTTagCompound {',
                   'static final class FluidStack {', 'static final class ItemStack {'):
        source = source.replace(method(source, marker), '')
    source = source.replace('import java.util.*;', '''import java.util.*;
import net.minecraftforge.fluids.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;''')
    source = source.replace('static final Fluid STEAM = new Fluid("steam");', 'static Fluid STEAM;')
    source = source.replace('new ItemStack(100)', 'new ItemStack(new Item(),100,0)')
    bootstrap = r'''
    static boolean nativeBootstrap(String[] args) throws Exception {
        if (!(HubTransferContract.class.getClassLoader() instanceof net.minecraft.launchwrapper.LaunchClassLoader)) {
            String[] paths=System.getProperty("java.class.path").split(java.io.File.pathSeparator);
            java.net.URL[] urls=new java.net.URL[paths.length];
            for(int i=0;i<paths.length;i++) urls[i]=new java.io.File(paths[i]).toURI().toURL();
            net.minecraft.launchwrapper.LaunchClassLoader loader=new net.minecraft.launchwrapper.LaunchClassLoader(urls);
            net.minecraft.launchwrapper.Launch.classLoader=loader;
            net.minecraft.launchwrapper.Launch.minecraftHome=new java.io.File(".");
            Thread.currentThread().setContextClassLoader(loader);
            Class<?> injection=loader.loadClass("cpw.mods.fml.relauncher.FMLInjectionData");
            java.lang.reflect.Method build=injection.getDeclaredMethod("build",java.io.File.class,
                net.minecraft.launchwrapper.LaunchClassLoader.class);
            build.setAccessible(true);build.invoke(null,new java.io.File("."),loader);
            loader.loadClass("cpw.mods.fml.common.Loader").getMethod("injectData",Object[].class)
                .invoke(null,new Object[]{injection.getMethod("data").invoke(null)});
            loader.loadClass("HubTransferContract").getMethod("main",String[].class).invoke(null,new Object[]{args});
            return false;
        }
        java.lang.reflect.Field side=cpw.mods.fml.relauncher.FMLRelaunchLog.class.getDeclaredField("side");
        side.setAccessible(true);side.set(null,cpw.mods.fml.relauncher.Side.SERVER);
        java.lang.reflect.Field home=cpw.mods.fml.relauncher.FMLRelaunchLog.class.getDeclaredField("minecraftHome");
        home.setAccessible(true);home.set(null,new java.io.File("."));
        net.minecraft.init.Bootstrap.register();
        FluidRegistry.registerFluid(new Fluid("steam")); STEAM=FluidRegistry.getFluid("steam");
        return true;
    }
    '''
    source = source.replace('public static void main(String[] args) {', bootstrap + '''
    public static void main(String[] args) throws Exception {
        if (!nativeBootstrap(args)) return;''')
    return source.replace('controlled fixtures, extracted production bodies',
                          'native Forge fluid/MC item/NBT; controlled world/receipt fixtures; extracted production bodies')


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--source-root',type=Path,default=ROOT)
    parser.add_argument('--native-classpath')
    args=parser.parse_args()
    home=Path(os.environ['JAVA_HOME'])/'bin' if os.environ.get('JAVA_HOME') else None
    javac=str(home/'javac') if home else shutil.which('javac')
    java=str(home/'java') if home else shutil.which('java')
    if not java or not javac: raise SystemExit('JDK17+ required; set JAVA_HOME')
    with tempfile.TemporaryDirectory(prefix='gtsr-hub-contracts-') as name:
        work=Path(name);source=work/'HubTransferContract.java'
        content=build(args.source_root)
        if args.native_classpath: content=with_native_storage(content)
        source.write_text(content)
        cp=['-cp',args.native_classpath] if args.native_classpath else []
        subprocess.run([javac,'--release','17','-encoding','UTF-8','-proc:none',*cp,'-d',str(work),str(source)],check=True)
        runtime=str(work)+(os.pathsep+args.native_classpath if args.native_classpath else '')
        subprocess.run([java,'-cp',runtime,'HubTransferContract'],check=True)

if __name__=='__main__': main()
