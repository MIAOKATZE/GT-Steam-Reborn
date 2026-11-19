"""Non-GL contract probe of the production preview cache using small host-API stubs."""
from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/critical-nei-logic'
STUBS = {
    'net/minecraft/block/Block': '''package net.minecraft.block;
public class Block { public boolean canRenderInPass(int pass) { return true; } }''',
    'net/minecraft/init/Blocks': '''package net.minecraft.init;
import net.minecraft.block.Block;
public class Blocks { public static final Block air=new Block(), stonebrick=new Block(), glowstone=new Block(), glass=new Block(), stained_glass=new Block(); }''',
    'net/minecraft/world/World': '''package net.minecraft.world;
public abstract class World {
 public abstract net.minecraft.block.Block getBlock(int x,int y,int z);
}''',
    'net/minecraft/client/renderer/Tessellator': '''package net.minecraft.client.renderer;
public class Tessellator { public static final Tessellator instance=new Tessellator(); public int vertices; }''',
    'net/minecraft/client/renderer/RenderBlocks': '''package net.minecraft.client.renderer;
public class RenderBlocks {
 public net.minecraft.world.World blockAccess; public boolean renderAllFaces, enableAO, useInventoryTint, lockBlockBounds;
 public boolean hasOverrideBlockTexture(){return false;}
 public void setRenderBounds(double a,double b,double c,double d,double e,double f) {}
 public boolean renderBlockByRenderType(net.minecraft.block.Block b,int x,int y,int z) { return true; }
}''',
    'net/minecraftforge/client/ForgeHooksClient': 'package net.minecraftforge.client; public class ForgeHooksClient { public static int getWorldRenderPass(){return -1;} }',
    'gregtech/api/GregTechAPI': '''package gregtech.api;
import net.minecraft.block.Block;
public class GregTechAPI { public static final Block sBlockCasings1=new Block(), sBlockCasings2=new Block(), sBlockCasings3=new Block(), sBlockCasings4=new Block(); }''',
    'com/miaokatze/gtsr/common/blocks/BlockTungstenSteelGearbox': 'package com.miaokatze.gtsr.common.blocks; public class BlockTungstenSteelGearbox extends net.minecraft.block.Block {}',
    'team/chisel/block/BlockCarvable': '''package team.chisel.block;
public class BlockCarvable extends net.minecraft.block.Block { public boolean canRenderInPass(int p){return p==0;} }''',
    'net/minecraftforge/client/event/GuiOpenEvent': 'package net.minecraftforge.client.event; public class GuiOpenEvent {}',
    'net/minecraftforge/client/event/TextureStitchEvent': 'package net.minecraftforge.client.event; public class TextureStitchEvent { public static class Post {} }',
    'net/minecraftforge/common/MinecraftForge': '''package net.minecraftforge.common;
public class MinecraftForge { public static final Bus EVENT_BUS=new Bus(); public static class Bus { public void register(Object o) {} } }''',
    'cpw/mods/fml/common/eventhandler/SubscribeEvent': 'package cpw.mods.fml.common.eventhandler; public @interface SubscribeEvent {}',
    'com/gtnewhorizon/gtnhlib/util/CoordinatePacker': '''package com.gtnewhorizon.gtnhlib.util;
public class CoordinatePacker { public static int unpackX(long p){return (int)p;} public static int unpackY(long p){return 0;} public static int unpackZ(long p){return 0;} }''',
    'com/miaokatze/gtsr/common/critical/CriticalMaterials': '''package com.miaokatze.gtsr.common.critical;
public class CriticalMaterials { public static class MaterialBlock extends net.minecraft.block.Block {
 public final String materialId="v2_test"; public boolean canRenderInPass(int p){return p==0;}
} }''',
    'cpw/mods/fml/common/Loader': 'package cpw.mods.fml.common; public class Loader { public static boolean isModLoaded(String modId){ return false; } }',
    'com/gtnewhorizons/angelica/rendering/StateAwareTessellator': 'package com.gtnewhorizons.angelica.rendering; public interface StateAwareTessellator { boolean angelica$isCeleritasMeshing(); }',
    'com/gtnewhorizons/angelica/rendering/celeritas/BiomeBlendTessellator': 'package com.gtnewhorizons.angelica.rendering.celeritas; public interface BiomeBlendTessellator { Object angelica$getBiomeBlender(); }',
    'com/miaokatze/miaokatzecore/client/render/TessellatorSlice': '''package com.miaokatze.miaokatzecore.client.render;
public class TessellatorSlice {
 private int vertices;
 public static int captures, retainedOverride; public static boolean unsupported;
 public static TessellatorSlice capture(net.minecraft.client.renderer.Tessellator t,Runnable r){captures++;int n=t.vertices;r.run();if(unsupported)return null;TessellatorSlice s=new TessellatorSlice();s.vertices=t.vertices-n;return s;}
 public boolean replay(net.minecraft.client.renderer.Tessellator t){t.vertices+=vertices;return true;}
 public int retainedInts(){return retainedOverride>0?retainedOverride:vertices*8;}
}''',
    'it/unimi/dsi/fastutil/longs/LongConsumer': 'package it.unimi.dsi.fastutil.longs; public interface LongConsumer { void accept(long n); }',
    'it/unimi/dsi/fastutil/longs/LongIterator': 'package it.unimi.dsi.fastutil.longs; public interface LongIterator { boolean hasNext(); long nextLong(); }',
    'it/unimi/dsi/fastutil/longs/LongCollection': 'package it.unimi.dsi.fastutil.longs; public interface LongCollection { int size(); LongIterator iterator(); }',
    'it/unimi/dsi/fastutil/longs/LongSet': 'package it.unimi.dsi.fastutil.longs; public interface LongSet extends LongCollection {}',
    'it/unimi/dsi/fastutil/longs/LongArrayList': '''package it.unimi.dsi.fastutil.longs;
public class LongArrayList implements LongSet {
 public final java.util.List<Long> values=new java.util.ArrayList<>();
 public void add(long n){values.add(n);} public int size(){return values.size();}
 public LongIterator iterator(){java.util.Iterator<Long> i=values.iterator();return new LongIterator(){public boolean hasNext(){return i.hasNext();}public long nextLong(){return i.next();}};}
}''',
    'CriticalPreviewCacheProbe': '''
import java.util.*;
import com.miaokatze.gtsr.client.nei.StructurePreviewMaterials;
import com.miaokatze.miaokatzecore.client.preview.PreviewGeometryCache;
import com.miaokatze.gtsr.common.critical.CriticalMaterials;
import com.miaokatze.gtsr.common.structure.StructurePreviewWorld;
import net.minecraft.client.renderer.*;
import it.unimi.dsi.fastutil.longs.*;
import com.miaokatze.miaokatzecore.client.render.TessellatorSlice;
public class CriticalPreviewCacheProbe {
 static class W extends net.minecraft.world.World implements StructurePreviewWorld {
  long revision; Map<Integer,net.minecraft.block.Block> blocks=new HashMap<>();
  public void gtsr$markStructurePreview(){revision++;} public boolean gtsr$isStructurePreview(){return true;} public long gtsr$previewRevision(){return revision;}
  public net.minecraft.block.Block getBlock(int x,int y,int z){return blocks.get(x);}
 }
 static class R extends RenderBlocks {
  List<Integer> order=new ArrayList<>();
  public boolean renderBlockByRenderType(net.minecraft.block.Block b,int x,int y,int z){order.add(x);Tessellator.instance.vertices++;return true;}
 }
 static void require(boolean condition,String label){if(!condition)throw new AssertionError(label);}
 static LongArrayList positions(long... values){LongArrayList r=new LongArrayList();for(long v:values)r.add(v);return r;}
 static PreviewGeometryCache cache(W w){return new PreviewGeometryCache(w,w::gtsr$previewRevision,StructurePreviewMaterials::isCacheable);}
 public static void main(String[] args){
  W w=new W();w.blocks.put(1,new CriticalMaterials.MaterialBlock());w.blocks.put(2,new net.minecraft.block.Block());w.blocks.put(3,new CriticalMaterials.MaterialBlock());
  PreviewGeometryCache c=cache(w);LongArrayList p=positions(1,2,3);R r=new R();Tessellator t=Tessellator.instance;
  require(c.prepare(p),"initial plan");c.append(t,r,false);require(r.order.equals(Arrays.asList(1,2,3,2)),"two-pass source order");
  r.order.clear();int before=t.vertices;c.prepare(p);c.append(t,r,false);require(r.order.equals(Arrays.asList(2,2))&&t.vertices-before==4,"camera-stable cache and live unknown block");
  w.revision++;r.order.clear();c.prepare(p);c.append(t,r,false);require(r.order.equals(Arrays.asList(1,2,3,2)),"world/metadata/TE revision invalidates geometry");
  p.values.remove(Long.valueOf(3));c.invalidate();r.order.clear();c.prepare(p);c.append(t,r,true);require(r.order.equals(Arrays.asList(1,2,2)),"layer/search selection invalidates");
  new PreviewGeometryCache.Lifecycle().atlasChanged(null);r.order.clear();c.prepare(p);c.append(t,r,true);
  require(r.order.equals(Arrays.asList(1,2,2)),"atlas reload invalidates the prepared slices");
  new PreviewGeometryCache.Lifecycle().guiChanged(null);r.order.clear();c.prepare(p);c.append(t,r,true);
  require(r.order.equals(Arrays.asList(1,2,2)),"GUI lifecycle invalidates the prepared slices");
  cache(new W());r.order.clear();c.prepare(p);c.append(t,r,true);
  require(r.order.equals(Arrays.asList(1,2,2)),"scene replacement releases previous cache");
  W budgetWorld=new W();budgetWorld.blocks.put(1,new CriticalMaterials.MaterialBlock());
  PreviewGeometryCache budget=cache(budgetWorld);LongArrayList one=positions(1);R budgetRenderer=new R();
  TessellatorSlice.retainedOverride=16_777_217;budget.prepare(one);int attempts=TessellatorSlice.captures;
  for(int frame=0;frame<20;frame++){budget.prepare(one);budget.append(t,budgetRenderer,false);}
  require(TessellatorSlice.captures-attempts==1&&budgetRenderer.order.size()==20,"over-budget segment captured once but drawn every frame");
  budgetWorld.revision++;budget.prepare(one);budget.append(t,budgetRenderer,false);
  require(TessellatorSlice.captures-attempts==2,"revision permits capture retry");
  TessellatorSlice.retainedOverride=0;TessellatorSlice.unsupported=true;budget.invalidate();budget.prepare(one);attempts=TessellatorSlice.captures;
  for(int frame=0;frame<5;frame++)budget.append(t,budgetRenderer,false);
  require(TessellatorSlice.captures-attempts==1,"unsupported capture does not repeat until invalidation");
  TessellatorSlice.unsupported=false;new PreviewGeometryCache.Lifecycle().atlasChanged(null);budget.prepare(one);budget.append(t,budgetRenderer,false);
  require(TessellatorSlice.captures-attempts==2,"atlas invalidation permits capture retry");
  LongArrayList huge=new LongArrayList(){public int size(){return 2_000_001;}};
  require(!budget.prepare(huge),"position budget rejects oversized scene");
  require(budget.prepare(one)&&budget.prepare(one),"smaller replacement clears overflow without revision change");
  W mixed=new W();LongArrayList many=new LongArrayList();net.minecraft.block.Block chisel=new team.chisel.block.BlockCarvable();
  for(int i=0;i<5000;i++){mixed.blocks.put(i,chisel);many.add(i);}
  mixed.blocks.put(2000,new net.minecraft.block.Block());
  PreviewGeometryCache shared=cache(mixed);R mixedRenderer=new R();
  attempts=TessellatorSlice.captures;require(shared.prepare(many),"mixed static plan");shared.append(t,mixedRenderer,false);
  require(TessellatorSlice.captures-attempts==3&&mixedRenderer.order.size()==5001,"mixed runs split without reordering");
  mixedRenderer.order.clear();shared.append(t,mixedRenderer,false);
  require(mixedRenderer.order.equals(Arrays.asList(2000,2000)),"mixed static replay preserves live native ordering");
  System.out.println("PASS: production cache ordering, live unknown path, stable reuse, world/selection/resource/GUI/scene invalidation; host APIs stubbed, no GL executed");
 }
}'''
}


def main():
    sources = OUT / 'stubs'
    sources.mkdir(parents=True, exist_ok=True)
    files = []
    for name, text in STUBS.items():
        path = sources / (name + '.java')
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding='utf-8')
        files.append(path)
    production = ROOT / 'src/main/java/com/miaokatze/gtsr/client/nei'
    core = ROOT.parent / 'MIAOKATZE-Core/src/main/java/com/miaokatze/miaokatzecore/client/preview'
    files += [core / 'PreviewGeometryCache.java', production / 'StructurePreviewMaterials.java',
              production.parent.parent / 'common/structure/StructurePreviewWorld.java']
    jdk = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'
    subprocess.run([str(jdk/'javac.exe'), '-encoding', 'UTF-8', '-proc:none', '-d', str(OUT/'classes'), *map(str, files)], check=True)
    result = subprocess.run([str(jdk/'java.exe'), '-cp', str(OUT/'classes'), 'CriticalPreviewCacheProbe'], check=True, capture_output=True, text=True)
    print(result.stdout.strip())
    (OUT/'result.txt').write_text(result.stdout, encoding='utf-8')


if __name__ == '__main__':
    main()
