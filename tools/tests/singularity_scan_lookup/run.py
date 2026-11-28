# SPDX-License-Identifier: AGPL-3.0-only
# Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.
"""Compile the production lookup against small native-API models; no Minecraft/performance claim."""
import json
import subprocess
import sys
from pathlib import Path

repo = Path(__file__).resolve().parents[3]
out = repo.parent / '.artifacts/gtsr-singularity-hotpath-20261117/lookup-contract'
src = out / 'src'
sources = {
    'net/minecraft/block/Block.java': 'package net.minecraft.block; public class Block {}',
    'net/minecraft/init/Blocks.java': 'package net.minecraft.init; import net.minecraft.block.Block; public class Blocks { public static final Block air=new Block(); }',
    'net/minecraft/world/chunk/storage/ExtendedBlockStorage.java': 'package net.minecraft.world.chunk.storage; public class ExtendedBlockStorage {}',
    'net/minecraft/world/chunk/Chunk.java': '''package net.minecraft.world.chunk;
        import net.minecraft.block.Block; import net.minecraft.init.Blocks;
        import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
        public class Chunk { public int reads; public Block block=new Block();
        public final ExtendedBlockStorage[] sections=new ExtendedBlockStorage[16];
        public Chunk(){sections[4]=new ExtendedBlockStorage();}
        public ExtendedBlockStorage[] getBlockStorageArray(){return sections;}
        public Block getBlock(int x,int y,int z){reads++;return sections[y>>4]==null?Blocks.air:block;} }''',
    'net/minecraft/world/World.java': '''package net.minecraft.world;
        import java.util.*; import net.minecraft.block.Block; import net.minecraft.world.chunk.Chunk;
        public class World { public int lookups, reads; public final Map<String,Chunk> chunks=new HashMap<>();
        public Chunk getChunkFromChunkCoords(int x,int z){lookups++;return chunks.computeIfAbsent(x+":"+z,k->new Chunk());}
        public Block getBlock(int x,int y,int z){reads++;return getChunkFromChunkCoords(x>>4,z>>4).getBlock(x&15,y,z&15);} }''',
    'com/miaokatze/gtsr/common/blocks/SingularityBlockLookupContract.java': '''package com.miaokatze.gtsr.common.blocks;
        import net.minecraft.block.Block; import net.minecraft.init.Blocks; import net.minecraft.world.World;
        import net.minecraft.world.chunk.Chunk; import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
        public final class SingularityBlockLookupContract {
        static int checks;
        public static final class ProxyWorld extends World { public final Block special=new Block();
        @Override public Block getBlock(int x,int y,int z){reads++;return special;} }
        public static final class VirtualChunk extends Chunk {public final Block special=new Block();
        @Override public Block getBlock(int x,int y,int z){reads++;return special;} }
        static void require(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
        public static void main(String[] args){
          World nativeWorld=new World(); SingularityBlockLookup nativeScan=new SingularityBlockLookup(nativeWorld,-1,-1,16);
          for(int x:new int[]{-17,-1,15})for(int z:new int[]{-17,-1,15}){
            Block a=nativeScan.getBlock(x,64,z),b=nativeScan.getBlock(x,64,z);
            require(a==b,"native live chunk identity"); }
          require(nativeWorld.lookups==9,"one lazy lookup per chunk, including negative coordinates");
          require(nativeWorld.reads==0,"native path avoids redundant world lookup");
          require(nativeScan.getBlock(0,-1,0)==Blocks.air,"native lower y bound");
          require(nativeScan.getBlock(0,256,0)==Blocks.air,"native upper y bound");
          require(nativeScan.getBlock(30000000,64,0)==Blocks.air,"native world bound");
          Chunk chunk=nativeWorld.chunks.get("0:0"); chunk.sections[4]=null; int prior=chunk.reads;
          require(nativeScan.getBlock(0,64,0)==Blocks.air&&chunk.reads==prior,"vanilla null section skips block access");
          chunk.sections[4]=new ExtendedBlockStorage(); chunk.block=new Block();
          require(nativeScan.getBlock(0,64,0)==chunk.block,"section changes visible within same scan");
          ProxyWorld proxy=new ProxyWorld(); SingularityBlockLookup proxyScan=new SingularityBlockLookup(proxy,0,0,16);
          require(proxyScan.getBlock(0,64,0)==proxy.special,"custom world override retained");
          require(proxyScan.getBlock(0,-1,0)==proxy.special,"custom world owns its bounds");
          require(proxy.lookups==0&&proxy.reads==2,"custom world never bypassed");
          World malformedRadius=new World(); SingularityBlockLookup malformedScan=new SingularityBlockLookup(malformedRadius,0,0,-1);
          malformedScan.getBlock(0,64,0);
          require(malformedRadius.reads==1,"invalid legacy radius keeps original world fallback without cache allocation");
          World specialized=new World(); VirtualChunk virtual=new VirtualChunk(); virtual.sections[4]=null;
          specialized.chunks.put("0:0",virtual); SingularityBlockLookup specializedScan=new SingularityBlockLookup(specialized,0,0,1);
          require(specializedScan.getBlock(0,64,0)==virtual.special,"custom chunk null section not assumed empty");
          System.out.println("PASS "+checks+" lookup/override/bounds/cache checks; native API model only");
        } }''',
}
for name, text in sources.items():
    path = src / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, 'utf-8')
sys.path.insert(0, str(repo.parent / 'tools/testlab'))
import host
registry = host.load_registry()
java = Path(registry['java_home']) / 'bin'
classes = out / 'classes'
classes.mkdir(parents=True, exist_ok=True)
production = repo / 'src/main/java/com/miaokatze/gtsr/common/blocks/SingularityBlockLookup.java'
subprocess.run([str(java/'javac.exe'), '-d', str(classes), str(production), *map(str, src.rglob('*.java'))], check=True)
result = subprocess.run([str(java/'java.exe'), '-cp', str(classes), 'com.miaokatze.gtsr.common.blocks.SingularityBlockLookupContract'], capture_output=True, text=True, check=True)
(out / 'result.json').write_text(json.dumps({'status': 'PASS', 'scope': 'Production helper with native API models, no real game or performance evidence', 'output': result.stdout.strip()}, indent=2), 'utf-8')
print(result.stdout.strip())
