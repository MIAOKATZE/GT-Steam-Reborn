import java.util.*;
class CriticalFoundationChunk {int progress;boolean done;boolean complete(){return done;}}
class ChunkCoordIntPair {int chunkXPos,chunkZPos;ChunkCoordIntPair(int x,int z){chunkXPos=x;chunkZPos=z;}}
public class FoundationBoundary {
 private final Map<String,Region> regions=new LinkedHashMap<>();private final Map<Long,CriticalFoundationChunk> foundations=new LinkedHashMap<>();
 private String nightOwner="";void markDirty(){}
 static class Region {String id,owner;int x,y,z,size,height,cx,cy,cz;Region(String id,String owner,int x,int y,int z){this.id=id;this.owner=owner;this.x=x;this.y=y;this.z=z;}}
 __METHODS__
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static void main(String[] args){
  FoundationBoundary w=new FoundationBoundary();
  check(w.acquire("a","owner",1,20,1,112,162,10,20,10),"first reservation");
  check(w.acquire("b","owner",113,20,1,112,162,120,20,10),"adjacent nonoverlapping reservation");
  CriticalFoundationChunk shared=w.foundation(7,0);shared.progress=173;
  CriticalFoundationChunk exclusive=w.foundation(0,0);exclusive.progress=8;
  CriticalFoundationChunk complete=w.foundation(1,0);complete.done=true;
  List<ChunkCoordIntPair> chunks=List.of(new ChunkCoordIntPair(7,0),new ChunkCoordIntPair(0,0),new ChunkCoordIntPair(1,0));
  w.__DISCARD__;w.release("a");
  check(w.findFoundation(7,0)==shared&&shared.progress==173,"SR01 cancellation must preserve another registered job's paid shared progress");
  check(w.findFoundation(0,0)==null,"unreferenced unfinished work is discarded");
  check(w.findFoundation(1,0)==complete,"completed foundation remains reusable");
  w.release("b");w.__DISCARD__;check(w.findFoundation(7,0)==null,"last owner cleanup removes unfinished journal");
  System.out.println("PASS SR01 shared foundation boundaries");
 }
}
