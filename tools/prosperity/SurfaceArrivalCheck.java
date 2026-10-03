import java.util.*;import java.io.*;import com.google.gson.*;import net.minecraft.block.Block;import net.minecraft.init.Blocks;import net.minecraft.world.World;import net.minecraft.profiler.Profiler;
import com.miaokatze.gtsr.common.blocks.*;import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;import com.miaokatze.gtsr.common.dimension.prosperity.*;import com.miaokatze.gtsr.common.dimension.prosperity.remaster.*;
class SurfaceArrivalCheck {
 static Gson gson=new GsonBuilder().setPrettyPrinting().create();static JsonArray results=new JsonArray();static int unknown;
 static class W extends StairCollisionCheck.W {long seed;int ax,ay,az;public Block getBlock(int x,int y,int z){String k=key(x,y,z);if(blocks.containsKey(k))return blocks.get(k);int h=ProsperityTerrainProfile.heightAt(seed,x+ax,z+az);if(y+ay<=h)return BlocksGTSR.prosperityStone;int water=ChunkProviderProsperityRuins.naturalWaterTopAt(seed,x+ax,z+az);return y+ay<=water?Blocks.water:Blocks.air;}}
 public static void main(String[] args)throws Exception{
        NativeTerrainFixture.initialize();
  StairCollisionCheck.main(new String[0]);BlocksGTSR.prosperityStone=new BlockProsperityStone("ProsperityStone","gtsr:prosperity_stone");
  java.lang.reflect.Field rf=RemasterBlocks.class.getDeclaredField("REGISTRY");rf.setAccessible(true);Map reg=(Map)rf.get(null);for(java.lang.reflect.Method m:RemasterBlocks.class.getDeclaredMethods())if(m.getName().matches("block[0-9]+")){m.setAccessible(true);RemasterBlock b=(RemasterBlock)m.invoke(null);reg.put(b.id(),b);}com.miaokatze.gtsr.common.dimension.prosperity.encounter.ForgottenLakeEncounterRegistry.sealedChest=new com.miaokatze.gtsr.common.dimension.prosperity.encounter.BlockSealedChest();
  RemasterRuntimeCheck.setBlock("stonebrick",new net.minecraft.block.BlockStoneBrick());java.lang.reflect.Constructor<net.minecraft.block.BlockStairs> stair=net.minecraft.block.BlockStairs.class.getDeclaredConstructor(Block.class,int.class);stair.setAccessible(true);RemasterRuntimeCheck.setBlock("stone_brick_stairs",stair.newInstance(Blocks.stonebrick,0));RemasterRuntimeCheck.setBlock("fence",new net.minecraft.block.BlockFence("planks",net.minecraft.block.material.Material.wood));
  java.lang.reflect.Constructor<net.minecraft.block.BlockStaticLiquid> waterConstructor=net.minecraft.block.BlockStaticLiquid.class.getDeclaredConstructor(net.minecraft.block.material.Material.class);waterConstructor.setAccessible(true);RemasterRuntimeCheck.setBlock("water",waterConstructor.newInstance(net.minecraft.block.material.Material.water));
  JsonArray input=new JsonParser().parse(new FileReader("temp/surface-arrival-production.inputs.json")).getAsJsonArray();Map<String,JsonObject> wanted=new LinkedHashMap<>();for(JsonElement e:input){JsonObject r=e.getAsJsonObject();wanted.put(r.get("id").getAsString()+":"+r.get("variant").getAsInt(),r);}
  Map<String,RemasterSite> sites=new LinkedHashMap<>(),parents=new HashMap<>();long seed=20261001L;
  scan:for(int radius=0;radius<=24;radius++)for(int gx=-radius;gx<=radius;gx++)for(int gz=-radius;gz<=radius;gz++){
   if(Math.max(Math.abs(gx),Math.abs(gz))!=radius)continue;
   for(int layer=0;layer<3;layer++){
    List<RemasterSite> cell=RemasterPlanner.cell(seed,layer,gx,gz);
    for(RemasterSite s:cell){String key=s.prefab+":"+s.variant;if(!wanted.containsKey(key)||sites.containsKey(key))continue;
     if("city-plot".equals(s.layout)){RemasterSite parent=actualParent(cell,s);if(parent==null)throw new AssertionError("new city actual parent missing "+s.id());parents.put(key,parent);}
     sites.put(key,s);
    }
    if(sites.size()==wanted.size())break scan;
   }
  }
  if(sites.size()!=246||wanted.size()!=246||parents.size()!=84)throw new AssertionError("surface roster "+sites.size()+"/"+wanted.size()+" city="+parents.size());
  int passed=0,rejected=0,failed=0,fixedFailed=0,ordinary=0,city=0,unrecoverable=0;
  java.lang.reflect.Method anchor=RemasterPlanner.class.getDeclaredMethod("anchor",String.class,long.class,long.class,int.class,int.class);anchor.setAccessible(true);
  for(Map.Entry<String,RemasterSite> en:sites.entrySet()){
   JsonObject r=wanted.get(en.getKey());RemasterSite s=en.getValue();JsonObject entry=s.plan().metadata.getAsJsonObject("surfaceEntrance");if(entry==null)entry=s.plan().metadata.getAsJsonObject("productionSurfaceEntrance");if(entry==null)throw new AssertionError("missing actual entrance "+en.getKey());
   int[] target={entry.get("x").getAsInt(),entry.get("topY").getAsInt(),entry.get("z").getAsInt()};
   if(s.entryX()!=s.x+target[0]||s.entryY()!=s.y+target[1]+1||s.entryZ()!=s.z+target[2])throw new AssertionError("locate coordinates "+en.getKey());
   if(s.y+target[1]!=ProsperityTerrainProfile.heightAt(seed,s.entryX(),s.entryZ()))throw new AssertionError("natural floor anchor "+en.getKey());
   boolean isCity="city-plot".equals(s.layout);if(isCity)city++;else ordinary++;
   JsonObject trial=isCity?cityProbe(s,parents.get(en.getKey()),target):probe(world(s,target),target);if(!trial.get("walked").getAsBoolean()){System.out.println("RED surface arrival "+en.getKey()+" site="+s.x+","+s.y+","+s.z+" target="+Arrays.toString(target)+" "+trial);failed++;}else passed++;
   JsonObject out=new JsonObject();out.addProperty("id",s.prefab);out.addProperty("variant",s.variant);out.addProperty("sourceSha256",r.get("sourceSha256").getAsString());out.add("site",gson.toJsonTree(new int[]{s.x,s.y,s.z}));out.add("entry",gson.toJsonTree(new int[]{s.entryX(),s.entryY(),s.entryZ()}));out.add("probe",trial);
   JsonObject d=RemasterCatalog.descriptor(s.prefab,s.variant);int[] old=StairCollisionCheck.point(r.getAsJsonArray("oldSite"));RemasterSite fixed=(RemasterSite)anchor.invoke(null,s.prefab,seed,((long)s.variant)<<12,old[0]+d.getAsJsonArray("min").get(0).getAsInt(),old[2]+d.getAsJsonArray("min").get(2).getAsInt());
   JsonObject gate=anchorGate(s.prefab,s.variant,seed,old[0],old[2],target);
   boolean gateRejected=gate.get("rejected").getAsBoolean();out.add("sameCoordinatesAnchorGate",gate);
   if((fixed==null)!=gateRejected)throw new AssertionError("unexplained anchor result "+en.getKey()+" "+gate);
   gate.addProperty("rawAnchorReason",gate.get("reason").getAsString());gate.addProperty("rawAnchorRejected",gateRejected);
   // Fresh ordinary placement includes finite apron admission after the raw terrain anchor.
   // City plots require their saved actual parent and Bridge, and never take this admission path.
   if(fixed!=null&&!isCity)fixed=ordinaryAdmission(fixed,gate);
   if(fixed==null){rejected++;out.addProperty("sameCoordinatesAnchorRejected",true);}else if(isCity){
    List<RemasterSite> oldCell=RemasterPlanner.cell(seed,0,Math.floorDiv(old[0],2048),Math.floorDiv(old[2],2048));
    RemasterSite oldChild=null;for(RemasterSite q:oldCell)if(q.prefab.equals(fixed.prefab)&&q.variant==fixed.variant&&q.x==fixed.x&&q.y==fixed.y&&q.z==fixed.z&&"city-plot".equals(q.layout))oldChild=q;
    RemasterSite oldParent=oldChild==null?null:actualParent(oldCell,oldChild);
    if(oldParent==null){unrecoverable++;out.addProperty("sameCoordinatesContext","old-city-context-unrecoverable; anchor and persisted compatibility only");}
    else {JsonObject again=cityProbe(oldChild,oldParent,target);out.add("sameCoordinatesProbe",again);if(!again.get("walked").getAsBoolean())fixedFailed++;}
   }else {
    JsonObject again=probe(world(fixed,target),target);if(!again.get("walked").getAsBoolean()){System.out.println("RED same-coordinate arrival "+en.getKey()+" site="+fixed.x+","+fixed.y+","+fixed.z+" "+again);fixedFailed++;}out.add("sameCoordinatesProbe",again);
   }
   // Actual legacy NBT remains version zero; fresh admission must never be replayed onto it.
   RemasterSite saved=new RemasterSite(s.prefab,s.variant,seed,old[0],old[1],old[2],s.layout);out.add("oldSavedCompatibility",savedCompatibility(saved));
   JsonArray roomFacts=new JsonArray();for(JsonElement re:s.plan().metadata.getAsJsonArray("rooms")){JsonObject room=re.getAsJsonObject();int rx=room.get("x").getAsInt(),rz=room.get("z").getAsInt(),ry=room.get("y").getAsInt(),rw=room.get("w").getAsInt(),rd=room.get("d").getAsInt(),rh=room.get("h").getAsInt();JsonObject fact=new JsonObject();fact.addProperty("relativeFloor",ry+1);fact.addProperty("roofY",s.y+ry+rh);JsonArray samples=new JsonArray();for(int u:new int[]{0,rw/2,rw-1})for(int v:new int[]{0,rd/2,rd-1}){int natural=ProsperityTerrainProfile.heightAt(seed,s.x+rx+u,s.z+rz+v);JsonObject q=new JsonObject();q.add("column",gson.toJsonTree(new int[]{s.x+rx+u,s.z+rz+v}));q.addProperty("naturalTop",natural);q.addProperty("floorGap",s.y+ry-natural);q.addProperty("roofCover",natural-s.y-ry-rh);samples.add(q);}fact.add("samples",samples);roomFacts.add(fact);}out.add("roomTerrainFacts",roomFacts);
   results.add(out);
  }
  try(Writer wr=new FileWriter("temp/surface-arrival-production.json")){gson.toJson(results,wr);}
  if(unknown!=0||passed!=246||ordinary!=162||city!=84||fixedFailed!=0)throw new AssertionError("passed="+passed+" failed="+failed+" fixedFailed="+fixedFailed+" rejects="+rejected+" unknown="+unknown);
  System.out.println("PASS surface arrivals (ordinary natural-ring / city actual-street)="+passed+" same-coordinate explained anchor rejections="+rejected+" unknown="+unknown+" ordinary="+ordinary+" city="+city+" oldCityContextUnrecoverable="+unrecoverable+" seed="+seed);
 }

 static RemasterSite ordinaryAdmission(RemasterSite raw,JsonObject gate){
  RemasterSite admitted=RemasterEntryApron.admit(raw);
  gate.addProperty("entryApronAdmissionApplicable",true);gate.addProperty("rawSiteId",raw.id());gate.addProperty("rawSiteY",raw.y);
  if(admitted==null){gate.addProperty("reason","entry-apron-finite-connection-rejected");gate.addProperty("rejected",true);return null;}
  if(!raw.id().equals(admitted.id())||raw.x!=admitted.x||raw.y!=admitted.y||raw.z!=admitted.z)throw new AssertionError("fresh apron moved anchor "+raw.id());
  if(!RemasterEntryApron.valid(admitted,admitted.plan()))throw new AssertionError("invalid admitted apron "+raw.id());
  gate.addProperty("entryApronVersion",admitted.entryApronVersion);gate.addProperty("entryApronWrites",admitted.entryApronWrites().length/5);gate.addProperty("entryApronSourceSha",admitted.entryApronSha);return admitted;
 }
 static JsonObject savedCompatibility(RemasterSite saved){
  net.minecraft.nbt.NBTTagCompound nbt=saved.save();RemasterSite read=RemasterSite.read(nbt);
  if(!saved.id().equals(read.id())||read.x!=saved.x||read.y!=saved.y||read.z!=saved.z)throw new AssertionError("persisted anchor moved");
  if(nbt.hasKey("entryApronVersion")||nbt.hasKey("entryApronSha")||nbt.hasKey("entryApronWrites")||read.entryApronVersion!=0||!read.entryApronSha.isEmpty()||read.entryApronWrites().length!=0)throw new AssertionError("legacy anchor acquired apron plan");
  final int[] writes={0};BlockSink sink=(x,y,z,b,m,f)->{writes[0]++;return true;};
  int emitted=RemasterEntryApron.geometry(read,read.plan(),sink,read.entryX()>>4,read.entryZ()>>4);
  if(emitted!=0||writes[0]!=0)throw new AssertionError("legacy apron replayed");
  JsonObject fact=new JsonObject();fact.addProperty("savedId",read.id());fact.addProperty("savedY",read.y);fact.addProperty("entryApronVersion",read.entryApronVersion);fact.addProperty("planWrites",read.entryApronWrites().length/5);fact.addProperty("replayWrites",writes[0]);fact.addProperty("identityAndOriginalYUnchanged",true);return fact;
 }

 static RemasterSite actualParent(List<RemasterSite> cell,RemasterSite child){
  RemasterSite match=null;for(RemasterSite p:cell)if("city-grid".equals(p.layout)){
   List<RemasterSite> plots=RemasterPlanner.savedCityPlots(p);if(plots.size()!=28)throw new AssertionError("incomplete actual parent "+p.id());
   RemasterSite restored=RemasterSite.read(p.save());if(!restored.id().equals(p.id())||restored.y!=p.y||restored.roadVersion!=p.roadVersion)throw new AssertionError("saved parent changed");
   for(RemasterSite q:plots){int count=0;for(RemasterSite actual:cell)if(actual.id().equals(q.id())&&actual.y==q.y)count++;if(count!=1)throw new AssertionError("actual cell overlay id/Y mismatch "+q.id());}
   for(RemasterSite q:plots)if(q.id().equals(child.id())&&q.y==child.y){if(match!=null)throw new AssertionError("ambiguous parent "+child.id());match=p;}
  }return match;
 }
 static JsonObject cityProbe(RemasterSite s,RemasterSite parent,int[] target)throws Exception{
  if(parent==null)throw new AssertionError("actual parent required "+s.id());
  RemasterCityBridge.Connection connection=null;for(RemasterCityBridge.Connection c:RemasterCityBridge.connections(parent))if(c.plot.id().equals(s.id())&&c.plot.y==s.y){if(connection!=null)throw new AssertionError("duplicate connection");connection=c;}
  if(connection==null||connection.points.isEmpty())throw new AssertionError("missing actual connection "+s.id());
  int[] start=connection.points.get(0);if(start[0]!=s.entryX()||start[1]!=s.entryY()-1||start[2]!=s.entryZ())throw new AssertionError("connection entrance mismatch");
  List<int[]> path=new ArrayList<>();for(int[] p:connection.points)path.add(new int[]{p[0]-s.x,p[1]-s.y,p[2]-s.z});
  W composite=composite(s,parent,path,target);JsonObject out=new JsonObject();out.addProperty("semantics","actual-city-street-to-authored-entrance; not natural-ring arrival or city-from-outside proof");
  out.addProperty("parentId",parent.id());out.addProperty("parentY",parent.y);out.addProperty("parentRoadVersion",parent.roadVersion);out.addProperty("savedCityPlotsExactIdAndY",true);out.addProperty("sourceOverlays",RemasterPlanner.savedCityPlots(parent).size());
  out.add("absoluteConnection",gson.toJsonTree(connection.points));boolean forward=StairCollisionCheck.continuousRoute(composite,path,3);Collections.reverse(path);boolean backward=StairCollisionCheck.continuousRoute(composite,path,3);
  out.addProperty("connectionForward",forward);out.addProperty("connectionBackward",backward);out.addProperty("walked",forward&&backward);return out;
 }
 static JsonObject anchorGate(String id,int variant,long seed,int x,int z,int[] target){
  JsonObject d=RemasterCatalog.descriptor(id,variant),metadata=RemasterCatalog.get(id,variant).metadata;
  int floor=ProsperityTerrainProfile.heightAt(seed,x+target[0],z+target[2]),y=floor-target[1],water=ChunkProviderProsperityRuins.naturalWaterTopAt(seed,x+target[0],z+target[2]);
  JsonObject out=new JsonObject();out.addProperty("naturalFloor",floor);out.addProperty("naturalWaterTop",water);out.addProperty("candidateY",y);out.addProperty("minY",y+d.get("yMin").getAsInt());out.addProperty("maxY",y+d.get("yMax").getAsInt());
  String reason="accepted";if(y+d.get("yMin").getAsInt()<5||y+d.get("yMax").getAsInt()>254)reason="height-bounds";else if(water>=floor)reason="water-at-or-above-entrance-floor";
  JsonObject terrain=metadata.getAsJsonObject("terrain");boolean buried=terrain!=null&&terrain.has("buried")&&"full".equals(terrain.get("buried").getAsString());out.addProperty("fullBuriedRoofGateApplicable",buried);
  if(reason.equals("accepted")&&buried){int cover=terrain.has("cover")?terrain.get("cover").getAsInt():8;roof:for(JsonElement e:metadata.getAsJsonArray("rooms")){JsonObject r=e.getAsJsonObject();int rx=r.get("x").getAsInt(),rz=r.get("z").getAsInt(),rw=r.get("w").getAsInt(),rd=r.get("d").getAsInt(),roof=y+r.get("y").getAsInt()+r.get("h").getAsInt();if(roof>=y-2)continue;
   for(int u:new int[]{0,rw/2,rw-1})for(int v:new int[]{0,rd/2,rd-1}){int natural=ProsperityTerrainProfile.heightAt(seed,x+rx+u,z+rz+v);if(natural<roof+cover){reason="full-buried-roof-cover";out.add("failedColumn",gson.toJsonTree(new int[]{x+rx+u,z+rz+v}));out.addProperty("naturalTop",natural);out.addProperty("requiredRoofTop",roof+cover);break roof;}}
  }}out.addProperty("reason",reason);out.addProperty("rejected",!reason.equals("accepted"));return out;
 }
 static W composite(RemasterSite s,RemasterSite parent,List<int[]> path,int[] target)throws Exception{
  W w=world(s,target);w.blocks.clear();w.metas.clear();int minX=target[0]-10,maxX=target[0]+10,minZ=target[2]-10,maxZ=target[2]+10;for(int[] a:path){minX=Math.min(minX,a[0]-4);maxX=Math.max(maxX,a[0]+4);minZ=Math.min(minZ,a[2]-4);maxZ=Math.max(maxZ,a[2]+4);}final int lx=minX,hx=maxX,lz=minZ,hz=maxZ;
  BlockSink sink=(x,y,z,b,m,f)->{int xx=x-s.x,zz=z-s.z;if(xx<lx||xx>hx||zz<lz||zz>hz)return true;String authored=b.toString()+"#"+m;Block block=b.toString().equals("minecraft:stonebrick")?Blocks.stonebrick:b.toString().equals("minecraft:stone_brick_stairs")?Blocks.stone_brick_stairs:b.toString().equals("minecraft:fence")?Blocks.fence:RemasterRuntime.resolve(authored);if(block==Blocks.stone&&!b.toString().equals("minecraft:stone")){unknown++;throw new AssertionError("unknown production palette "+authored);}String k=w.key(xx,y-s.y,zz);w.blocks.put(k,block);w.metas.put(k,RemasterRuntime.meta(authored));return true;};
  List<RemasterSite> plots=RemasterPlanner.savedCityPlots(parent);for(int cx=(s.x+minX)>>4;cx<=(s.x+maxX)>>4;cx++)for(int cz=(s.z+minZ)>>4;cz<=(s.z+maxZ)>>4;cz++){RemasterTerrain.build(parent,sink,cx,cz);RemasterWorldgen.geometry(parent,null,sink,cx,cz);for(RemasterSite child:plots){RemasterTerrain.build(child,sink,cx,cz);RemasterWorldgen.geometry(child,child.plan(),sink,cx,cz);}}
  return w;
 }
 static W world(RemasterSite s,int[] at)throws Exception {W w=(W)RemasterRuntimeCheck.u.allocateInstance(W.class);w.blocks=new HashMap<>();w.metas=new HashMap<>();w.tiles=new HashMap<>();w.seed=s.seed;w.ax=s.x;w.ay=s.y;w.az=s.z;w.loaded=true;RemasterRuntimeCheck.set(w,World.class,"provider",RemasterRuntimeCheck.u.allocateInstance(WorldProviderProsperityRuins.class));RemasterRuntimeCheck.set(w,World.class,"theProfiler",new Profiler());RemasterRuntimeCheck.set(w,World.class,"rand",new Random(1));BlockSink sink=(x,y,z,b,m,f)->{int xx=x-s.x,zz=z-s.z;if(Math.abs(xx-at[0])>10||Math.abs(zz-at[2])>10)return true;String authored=b.toString()+"#"+m;Block block=b.toString().equals("minecraft:stonebrick")?Blocks.stonebrick:b.toString().equals("minecraft:stone_brick_stairs")?Blocks.stone_brick_stairs:b.toString().equals("minecraft:fence")?Blocks.fence:RemasterRuntime.resolve(authored);if(block==Blocks.stone&&!b.toString().equals("minecraft:stone")){unknown++;throw new AssertionError("unknown production palette "+authored);}String k=w.key(xx,y-s.y,zz);w.blocks.put(k,block);w.metas.put(k,RemasterRuntime.meta(authored));return true;};for(int cx=(s.x+at[0]-10)>>4;cx<=(s.x+at[0]+10)>>4;cx++)for(int cz=(s.z+at[2]-10)>>4;cz<=(s.z+at[2]+10)>>4;cz++){RemasterTerrain.build(s,sink,cx,cz);RemasterWorldgen.geometry(s,s.plan(),sink,cx,cz);}return w;}
 static JsonObject probe(W w,int[] target){JsonObject out=new JsonObject();StairCollisionCheck.E goal=new StairCollisionCheck.E(w);goal.setPosition(target[0]+.5,target[1]+1,target[2]+.5);boolean clear=w.getCollidingBoundingBoxes(goal,goal.boundingBox).isEmpty();out.addProperty("targetClear",clear);out.addProperty("targetSupported",StairCollisionCheck.supported(w,goal));ArrayDeque<double[]> q=new ArrayDeque<>();Set<String> seen=new HashSet<>();int naturalStarts=0;
  for(int x=target[0]-8;x<=target[0]+8;x++)for(int z=target[2]-8;z<=target[2]+8;z++){if(Math.max(Math.abs(x-target[0]),Math.abs(z-target[2]))!=8)continue;int y=ProsperityTerrainProfile.heightAt(w.seed,w.ax+x,w.az+z)-w.ay+1;StairCollisionCheck.E e=new StairCollisionCheck.E(w);e.setPosition(x+.5,y,z+.5);if(!w.blocks.containsKey(w.key(x,y-1,z))&&w.getCollidingBoundingBoxes(e,e.boundingBox).isEmpty()&&StairCollisionCheck.supported(w,e)){q.add(new double[]{x+.5,y,z+.5});naturalStarts++;}}
  boolean walked=false;double closest=999;int budget=3000;while(!q.isEmpty()&&budget-->0){double[] a=q.remove();int xx=(int)Math.floor(a[0]),zz=(int)Math.floor(a[2]);String key=xx+":"+zz+":"+Math.round(a[1]*2);if(!seen.add(key))continue;double dist=Math.abs(xx-target[0])+Math.abs(zz-target[2])+Math.abs(a[1]-target[1]-1);closest=Math.min(closest,dist);if(xx==target[0]&&zz==target[2]&&a[1]>=target[1]+.5&&a[1]<=target[1]+1.01&&clear){walked=true;break;}for(int[] dir:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){double x=a[0]+dir[0],z=a[2]+dir[1];if(Math.abs(x-.5-target[0])>8||Math.abs(z-.5-target[2])>8)continue;StairCollisionCheck.E e=new StairCollisionCheck.E(w);e.setPosition(a[0],a[1],a[2]);e.onGround=true;StairCollisionCheck.move(e,x,z);for(int i=0;i<12;i++)e.moveEntity(0,-.12,0);if(Math.abs(e.posX-x)>.1||Math.abs(e.posZ-z)>.1||!StairCollisionCheck.supported(w,e)||!w.getCollidingBoundingBoxes(e,e.boundingBox).isEmpty())continue;q.add(new double[]{e.posX,e.boundingBox.minY,e.posZ});}}
  out.addProperty("walked",walked);out.addProperty("naturalStarts",naturalStarts);out.addProperty("visited",seen.size());out.addProperty("closestL1",closest);out.addProperty("budgetRemaining",budget);return out;}
}
