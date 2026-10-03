import java.lang.reflect.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.prosperity.architecture.*;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.*;

/** Real MC boxes and Entity.moveEntity with default living-entity .5 stepHeight. */
public class StairCollisionCheck {
 static int checks;
 static String matrixReport="temp/stair-walk-collision.json";
 static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static class W extends RemasterRuntimeCheck.W {
  public List getCollidingBoundingBoxes(Entity e,AxisAlignedBB box){List<AxisAlignedBB> out=new ArrayList<>();for(int x=(int)Math.floor(box.minX)-1;x<=(int)Math.floor(box.maxX)+1;x++)for(int y=(int)Math.floor(box.minY)-1;y<=(int)Math.floor(box.maxY)+1;y++)for(int z=(int)Math.floor(box.minZ)-1;z<=(int)Math.floor(box.maxZ)+1;z++){Block block=getBlock(x,y,z);if(block!=Blocks.air)block.addCollisionBoxesToList(this,x,y,z,box,out,e);}return out;}
  public boolean isSideSolid(int x,int y,int z,net.minecraftforge.common.util.ForgeDirection side,boolean fallback){return getBlock(x,y,z).isOpaqueCube();}
  public boolean func_147470_e(AxisAlignedBB box){return false;}
 }
 static class E extends Entity {
  E(World w){super(w);setSize(.6F,1.8F);stepHeight=.5F;}
  protected void entityInit(){}
  protected void readEntityFromNBT(NBTTagCompound n){}
  protected void writeEntityToNBT(NBTTagCompound n){}
  public boolean isWet(){return false;}
  protected boolean canTriggerWalking(){return false;}
  protected void func_145775_I(){}
 }
 static class L extends net.minecraft.entity.EntityLivingBase {
  L(World w){super(w);}
  public net.minecraft.item.ItemStack getHeldItem(){return null;}
  public net.minecraft.item.ItemStack getEquipmentInSlot(int s){return null;}
  public void setCurrentItemOrArmor(int s,net.minecraft.item.ItemStack item){}
  public net.minecraft.item.ItemStack[] getLastActiveItems(){return new net.minecraft.item.ItemStack[0];}
 }
 static double surface(List<AxisAlignedBB> boxes,double x,double z){double top=0;for(AxisAlignedBB b:boxes)if(x>b.minX&&x<b.maxX&&z>b.minZ&&z<b.maxZ)top=Math.max(top,b.maxY);return top;}
 public static void main(String[] args)throws Exception{
  Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);RemasterRuntimeCheck.u=(sun.misc.Unsafe)f.get(null);
  Class.forName("net.minecraft.init.Blocks");Constructor<BlockAir> ac=BlockAir.class.getDeclaredConstructor();ac.setAccessible(true);RemasterRuntimeCheck.setBlock("air",ac.newInstance());RemasterRuntimeCheck.setBlock("stone",new BlockStone());
  BlocksGTSR.ruinDebris=new com.miaokatze.gtsr.common.dimension.prosperity.block.BlockRuinDebris();BlocksGTSR.ruinedCasing=new com.miaokatze.gtsr.common.dimension.prosperity.block.BlockRuinedCasing();BlocksGTSR.prosperityZenithLog=new BlockZenithLog("ProsperityZenithLog","gtsr:prosperity_zenith_log_side","gtsr:prosperity_zenith_log_top");RuinsArchitecture.registerBlocks();RoyalArchitecture.registerBlocks();
  W w=(W)RemasterRuntimeCheck.u.allocateInstance(W.class);w.blocks=new HashMap<>();w.metas=new HashMap<>();w.tiles=new HashMap<>();w.loaded=true;RemasterRuntimeCheck.set(w,World.class,"provider",RemasterRuntimeCheck.u.allocateInstance(com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins.class));RemasterRuntimeCheck.set(w,World.class,"theProfiler",new Profiler());RemasterRuntimeCheck.set(w,World.class,"rand",new Random(1));
  List<Block> stairs=new ArrayList<>();for(Map.Entry<String,Block> a:RuinsArchitecture.BLOCKS.entrySet())if(a.getValue() instanceof BlockStairs)stairs.add(a.getValue());for(Map.Entry<String,Block>a:RoyalArchitecture.BLOCKS.entrySet())if(a.getValue() instanceof BlockStairs)stairs.add(a.getValue());Method industrial=RemasterBlocks.class.getDeclaredMethod("block83");industrial.setAccessible(true);stairs.add((Block)industrial.invoke(null));
  for(Map.Entry<String,Block>a:RuinsArchitecture.BLOCKS.entrySet())if(a.getValue() instanceof BlockStairs)check(RemasterRuntime.resolve("gtsr:ruins_"+a.getKey()+"#0")==a.getValue(),"production resolver never falls back stone for new ruin stairs");for(Map.Entry<String,Block>a:RoyalArchitecture.BLOCKS.entrySet())if(a.getValue() instanceof BlockStairs)check(RemasterRuntime.resolve("gtsr:royal_"+a.getKey()+"#0")==a.getValue(),"production resolver resolves matching royal stairs");
  Field model=BlockStairs.class.getDeclaredField("field_150149_b");model.setAccessible(true);for(Map.Entry<String,Block>a:RuinsArchitecture.BLOCKS.entrySet())if(a.getValue() instanceof BlockStairs){String base=a.getKey().replace("_stairs","");if(base.equals("firebrick"))base="furnace_firebrick";if(base.equals("riveted_plate"))base="rust_riveted_plate";check(model.get(a.getValue())==RuinsArchitecture.get(base),"true MC stair delegates icon/material to exact original ruin base");}for(Map.Entry<String,Block>a:RoyalArchitecture.BLOCKS.entrySet())if(a.getValue() instanceof BlockStairs){String base=a.getKey().replace("_stairs","");check(model.get(a.getValue())==(base.equals("zenith_log")?BlocksGTSR.prosperityZenithLog:RoyalArchitecture.get(base)),"royal stair retains exact timber base, including natural Zenith");}
  L placer=(L)RemasterRuntimeCheck.u.allocateInstance(L.class);placer.worldObj=w;int[] expected={2,1,3,0};for(Block b:stairs)for(int quadrant=0;quadrant<4;quadrant++){placer.rotationYaw=quadrant*90;w.metas.put("0,0,0",4);b.onBlockPlacedBy(w,0,0,0,placer,null);check(w.getBlockMetadata(0,0,0)==(expected[quadrant]|4),"manual stair hook follows vanilla quadrants and preserves inverted bit");}
  Method nonStairFactory=RemasterBlocks.class.getDeclaredMethod("block0");nonStairFactory.setAccessible(true);RemasterBlock nonStair=(RemasterBlock)nonStairFactory.invoke(null);for(int quadrant=0;quadrant<4;quadrant++){placer.rotationYaw=quadrant*90;nonStair.onBlockPlacedBy(w,0,0,0,placer,null);check(w.getBlockMetadata(0,0,0)==quadrant,"nonstairs placement retains original player-facing mapping");}
  for(Block b:stairs){check(b!=null&&b!=Blocks.stone,"registered stairs resolve actual block");for(int meta=0;meta<8;meta++){w.blocks.clear();w.metas.clear();w.blocks.put("0,0,0",b);w.metas.put("0,0,0",meta);List<AxisAlignedBB> boxes=new ArrayList<>();b.addCollisionBoxesToList(w,0,0,0,AxisAlignedBB.getBoundingBox(-1,-1,-1,2,2,2),boxes,null);check(boxes.size()>=2,"real stair collision has half plus high half");double low=surface(boxes,(meta&3)==0?.25:(meta&3)==1?.75:.5,(meta&3)==2?.25:(meta&3)==3?.75:.5),high=surface(boxes,(meta&3)==0?.75:(meta&3)==1?.25:.5,(meta&3)==2?.75:(meta&3)==3?.25:.5);check((meta&4)!=0?low==1&&high==1:low==.5&&high==1,"metadata half-step faces uphill, inverted preserves overhead half");}
   for(int direction=0;direction<4;direction++){int dx=direction==0?1:direction==1?-1:0,dz=direction==2?1:direction==3?-1:0;w.blocks.clear();w.metas.clear();for(int i=0;i<=5;i++)for(int side=-1;side<=1;side++){int x=i*dx+(dz!=0?side:0),z=i*dz+(dx!=0?side:0),y=i==0?-1:Math.min(i-1,3);w.blocks.put(w.key(x,y,z),i>0&&i<5?b:Blocks.stone);w.metas.put(w.key(x,y,z),direction);}E e=new E(w);e.setPosition(.5,0,.5);e.onGround=true;for(int i=0;i<50;i++)e.moveEntity(dx*.1,-.08,dz*.1);check(e.boundingBox.minY>3.9,"Entity .5 step walks up without jumping");for(int i=0;i<50;i++)e.moveEntity(-dx*.1,-.25,-dz*.1);for(int settle=0;settle<12;settle++)e.moveEntity(0,-.25,0);check(e.boundingBox.minY<.01,"same collision supports reverse descent: "+b.getUnlocalizedName()+" dir="+direction+" pos="+e.posX+","+e.posZ+" foot="+e.boundingBox.minY);}
  }
  if(args.length>1)matrixReport=args[1];
  if(args.length>0)matrix(w,args[0]);
  System.out.println("passed "+checks+" actual MC collision/orientation/Entity .5-step assertions; "+stairs.size()+" stair blocks");
 }
 static void matrix(W w,String filename)throws Exception{
  com.google.gson.JsonArray fixtures=new com.google.gson.JsonParser().parse(new java.io.FileReader(filename)).getAsJsonArray();
  Map<String,Block> registry=new HashMap<>();registry.put("minecraft:stone",Blocks.stone);registry.put("gtsr:SealedChest",new com.miaokatze.gtsr.common.dimension.prosperity.encounter.BlockSealedChest());registry.put("gtsr:ProsperityStone",new com.miaokatze.gtsr.common.blocks.BlockProsperityStone("ProsperityStone","gtsr:prosperity_stone"));for(Map.Entry<String,Block> e:RuinsArchitecture.BLOCKS.entrySet())registry.put("gtsr:ruins_"+e.getKey(),e.getValue());for(Map.Entry<String,Block> e:RoyalArchitecture.BLOCKS.entrySet())registry.put("gtsr:royal_"+e.getKey(),e.getValue());registry.put("gtsr:ProsperityZenithLog",BlocksGTSR.prosperityZenithLog);registry.put("gtsr:ruin_debris_rivet_plate",BlocksGTSR.ruinDebris);registry.put("gtsr:ruined_casing_rusted",BlocksGTSR.ruinedCasing);
  for(Method m:RemasterBlocks.class.getDeclaredMethods())if(m.getName().matches("block[0-9]+")){m.setAccessible(true);RemasterBlock block=(RemasterBlock)m.invoke(null);registry.put(block.id(),block);}
  int pass=0,inactive=0,failed=0,unknown=0,normalized=0,replaced=0,proofFailed=0,proofPassed=0;Set<String> proven=new HashSet<>();com.google.gson.JsonArray results=new com.google.gson.JsonArray();
  for(com.google.gson.JsonElement element:fixtures){com.google.gson.JsonObject row=element.getAsJsonObject();w.blocks.clear();w.metas.clear();for(com.google.gson.JsonElement ce:row.getAsJsonArray("cells")){com.google.gson.JsonArray c=ce.getAsJsonArray();String[] material=c.get(3).getAsString().split("#");Block block=registry.get(material[0]);if(block==null){unknown++;continue;}String k=w.key(c.get(0).getAsInt(),c.get(1).getAsInt(),c.get(2).getAsInt());w.blocks.put(k,block);w.metas.put(k,Integer.parseInt(material[1]));}
   String routeIdentity=row.get("id").getAsString()+":"+row.get("variant").getAsInt()+":"+row.get("route").getAsString();
   if(row.has("kind")){List<int[]> route=new ArrayList<>();for(com.google.gson.JsonElement pe:row.getAsJsonArray("points"))route.add(point(pe.getAsJsonArray()));boolean forward=continuousRoute(w,route,row.get("width").getAsInt());Collections.reverse(route);boolean backward=continuousRoute(w,route,row.get("width").getAsInt());com.google.gson.JsonObject proof=new com.google.gson.JsonObject();for(String k:new String[]{"id","variant","route","reason"})if(row.has(k))proof.add(k,row.get(k));proof.addProperty("status","replacement-route-continuous-physical-proof");proof.addProperty("forward",forward);proof.addProperty("backward",backward);proof.addProperty("points",route.size());results.add(proof);if(forward&&backward){proven.add(routeIdentity);proofPassed++;}else proofFailed++;continue;}
   if(proven.contains(routeIdentity)){replaced++;continue;}
   int[] low=point(row.getAsJsonArray("low")),high=point(row.getAsJsonArray("high"));boolean alongX=row.get("alongX").getAsBoolean(),walked=false,active=false;int selected=999;
   for(int side=-(row.get("width").getAsInt()/2);side<=row.get("width").getAsInt()/2&&!walked;side++){
    int lx=low[0]+(alongX?0:side),lz=low[2]+(alongX?side:0),hx=high[0]+(alongX?0:side),hz=high[2]+(alongX?side:0);Block upper=w.getBlock(hx,high[1],hz);if(upper==Blocks.air||!upper.isOpaqueCube()&&!(upper instanceof BlockStairs)&&!upper.getUnlocalizedName().contains("stairs"))continue;Block floor=w.getBlock(lx,low[1],lz);if(floor==Blocks.air||!floor.isOpaqueCube()&&!(floor instanceof BlockStairs)&&!(floor instanceof RemasterBlock&&floor.getUnlocalizedName().contains("stairs")))continue;
    E e=new E(w);e.setPosition(lx+.5,low[1]+1,lz+.5);e.onGround=true;if(!w.getCollidingBoundingBoxes(e,e.boundingBox).isEmpty())continue;active=true;
    int[][] paths=lx!=hx&&lz!=hz?new int[][]{{hx,lz,hx,hz},{lx,hz,hx,hz}}:new int[][]{{hx,hz,hx,hz}};
    for(int[] path:paths){e.setPosition(lx+.5,low[1]+1,lz+.5);e.onGround=true;move(e,path[0]+.5,path[1]+.5);move(e,path[2]+.5,path[3]+.5);if(Math.abs(e.posX-hx-.5)<.12&&Math.abs(e.posZ-hz-.5)<.12&&e.boundingBox.minY>=high[1]+.95){walked=true;selected=side;break;}}
   }
   if(!walked&&active){walked=bypass(w,low,high,row.get("width").getAsInt());}
   boolean shifted=false;int[] actualLow=null,actualHigh=null;if(!walked){shifted=normalizedWalk(w,low,high,row.get("width").getAsInt(),alongX);if(shifted){actualLow=lastNormalizedLow.clone();actualHigh=lastNormalizedHigh.clone();}}
   boolean reverse=shifted||walked&&normalizedWalk(w,high,low,row.get("width").getAsInt(),alongX);if((walked||shifted)&&!reverse){walked=false;shifted=false;active=true;}if(shifted)normalized++;
   String status=walked?"walked-real-entity":shifted?"actual-authored-floor-height-differs-from-metadata":active?"active-blocked":"metadata-no-clear-original-low-floor";if(walked)pass++;else if(active&&!shifted)failed++;else if(!shifted)inactive++;
   if(!walked){com.google.gson.JsonObject r=new com.google.gson.JsonObject();for(String k:new String[]{"id","variant","route","rise","low","high"})r.add(k,row.get(k));r.addProperty("status",status);if(actualLow!=null){r.add("actualLow",new com.google.gson.Gson().toJsonTree(actualLow));r.add("actualHigh",new com.google.gson.Gson().toJsonTree(actualHigh));}r.addProperty("bidirectional",reverse);results.add(r);}
  }
  com.google.gson.JsonObject report=new com.google.gson.JsonObject();report.addProperty("fixtures",pass+inactive+failed+normalized+replaced);report.addProperty("replacementOldRiseProven",replaced);report.addProperty("replacementRoutesPassed",proofPassed);report.addProperty("replacementRoutesFailed",proofFailed);report.addProperty("walked",pass);report.addProperty("inactiveMetadata",inactive);report.addProperty("normalizedActualFloorWalked",normalized);report.addProperty("activeBlocked",failed);report.addProperty("unmappedNonfloorCells",unknown);report.add("exceptions",results);try(java.io.FileWriter f=new java.io.FileWriter(matrixReport)){new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report,f);}System.out.println(report.toString().substring(0,Math.min(200,report.toString().length())));if(failed+inactive+unknown+proofFailed>0)throw new AssertionError("unresolved real route matrix: active="+failed+", unproved="+inactive+", unknown="+unknown);
 }
 static boolean supported(W w,E e){return !w.getCollidingBoundingBoxes(e,AxisAlignedBB.getBoundingBox(e.boundingBox.minX,e.boundingBox.minY-.05,e.boundingBox.minZ,e.boundingBox.maxX,e.boundingBox.minY+.01,e.boundingBox.maxZ)).isEmpty();}
 static boolean continuousRoute(W w,List<int[]> points,int width){
  if(points.isEmpty())return false;int half=width/2;List<double[]> states=new ArrayList<>();int[] first=points.get(0);for(int dx=0;dx<=0;dx++)for(int dz=0;dz<=0;dz++){E e=new E(w);e.setPosition(first[0]+dx+.5,first[1]+1,first[2]+dz+.5);e.onGround=true;for(int t=0;t<12;t++)e.moveEntity(0,-.12,0);if(e.boundingBox.minY>=first[1]+.5&&e.boundingBox.minY<=first[1]+1.01&&w.getCollidingBoundingBoxes(e,e.boundingBox).isEmpty()&&supported(w,e))states.add(new double[]{e.posX,e.boundingBox.minY,e.posZ});}
  for(int pi=1;pi<points.size();pi++){int[] a=points.get(pi-1),b=points.get(pi);ArrayDeque<double[]> q=new ArrayDeque<>(states);Set<String> seen=new HashSet<>(),targetSeen=new HashSet<>();List<double[]> targets=new ArrayList<>();int budget=2000;while(!q.isEmpty()&&budget-->0){double[] at=q.remove();int xx=(int)Math.floor(at[0]),zz=(int)Math.floor(at[2]);String stateKey=xx+":"+zz+":"+Math.round(at[1]*2);if(!seen.add(stateKey))continue;if(Math.abs(xx-b[0])<=(pi==points.size()-1?0:half)&&Math.abs(zz-b[2])<=(pi==points.size()-1?0:half)&&at[1]>=b[1]+.5&&at[1]<=b[1]+1.01){if(targetSeen.add(stateKey))targets.add(at);}
    for(int[] dir:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){double x=at[0]+dir[0],z=at[2]+dir[1];if(x<Math.min(a[0],b[0])-half+.49||x>Math.max(a[0],b[0])+half+.51||z<Math.min(a[2],b[2])-half+.49||z>Math.max(a[2],b[2])+half+.51)continue;E e=new E(w);e.setPosition(at[0],at[1],at[2]);e.onGround=true;move(e,x,z);for(int settle=0;settle<12;settle++)e.moveEntity(0,-.12,0);if(Math.abs(e.posX-x)>.1||Math.abs(e.posZ-z)>.1||e.boundingBox.minY<Math.min(a[1],b[1])+.5||e.boundingBox.minY>Math.max(a[1],b[1])+1.01||!supported(w,e))continue;q.add(new double[]{e.posX,e.boundingBox.minY,e.posZ});}
   }if(targets.isEmpty()){System.out.println("continuous route failed waypoint "+pi+" "+Arrays.toString(b)+" previous="+new com.google.gson.Gson().toJson(states)+" floor="+w.getBlock(b[0],b[1],b[2]).getUnlocalizedName()+" head="+w.getBlock(b[0],b[1]+1,b[2]).getUnlocalizedName()+"/"+w.getBlock(b[0],b[1]+2,b[2]).getUnlocalizedName());return false;}states=targets;
  }return !states.isEmpty();
 }
 static int[] point(com.google.gson.JsonArray a){return new int[]{a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt()};}
 static void move(E e,double x,double z){for(int i=0;i<40;i++){double dx=x-e.posX,dz=z-e.posZ;if(Math.abs(dx)+Math.abs(dz)<.01)break;e.moveEntity(Math.max(-.1,Math.min(.1,dx)),-.08,Math.max(-.1,Math.min(.1,dz)));}}

 static boolean bypass(W w,int[] low,int[] high,int width){
  java.util.ArrayDeque<double[]> queue=new java.util.ArrayDeque<>();Set<String> seen=new HashSet<>();int half=width/2;
  for(int x=low[0]-half;x<=low[0]+half;x++)for(int z=low[2]-half;z<=low[2]+half;z++){E e=new E(w);e.setPosition(x+.5,low[1]+1,z+.5);e.onGround=true;if(w.getBlock(x,low[1],z)!=Blocks.air&&w.getCollidingBoundingBoxes(e,e.boundingBox).isEmpty()){queue.add(new double[]{x+.5,low[1]+1,z+.5});seen.add(x+":"+z+":"+(low[1]+1));}}
  int budget=100;while(!queue.isEmpty()&&budget-->0){double[] a=queue.remove();for(int[] dir:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){double x=a[0]+dir[0],z=a[2]+dir[1];if(x<Math.min(low[0],high[0])-half+.5||x>Math.max(low[0],high[0])+half+.5||z<Math.min(low[2],high[2])-half+.5||z>Math.max(low[2],high[2])+half+.5)continue;E e=new E(w);e.setPosition(a[0],a[1],a[2]);e.onGround=true;move(e,x,z);for(int i=0;i<4;i++)e.moveEntity(0,-.12,0);if(Math.abs(e.posX-x)>.1||Math.abs(e.posZ-z)>.1||e.boundingBox.minY<low[1]+.99||e.boundingBox.minY>high[1]+1.01)continue;int xx=(int)Math.floor(x),zz=(int)Math.floor(z);String k=xx+":"+zz+":"+e.boundingBox.minY;if(!seen.add(k))continue;if(e.boundingBox.minY>=high[1]+.95&&Math.abs(xx-high[0])<=half&&Math.abs(zz-high[2])<=half&&w.getBlock(xx,high[1],zz)!=Blocks.air)return true;queue.add(new double[]{e.posX,e.boundingBox.minY,e.posZ});}}
  return false;
 }

 static boolean exactWalk(W w,int[] from,int[] to){E e=new E(w);e.setPosition(from[0]+.5,from[1]+1,from[2]+.5);e.onGround=true;if(!w.getCollidingBoundingBoxes(e,e.boundingBox).isEmpty())return false;int[][] paths=from[0]!=to[0]&&from[2]!=to[2]?new int[][]{{to[0],from[2],to[0],to[2]},{from[0],to[2],to[0],to[2]}}:new int[][]{{to[0],to[2],to[0],to[2]}};for(int[] path:paths){e.setPosition(from[0]+.5,from[1]+1,from[2]+.5);e.onGround=true;move(e,path[0]+.5,path[1]+.5);move(e,path[2]+.5,path[3]+.5);for(int i=0;i<12;i++)e.moveEntity(0,-.12,0);if(Math.abs(e.posX-to[0]-.5)<.1&&Math.abs(e.posZ-to[2]-.5)<.1&&e.boundingBox.minY>=to[1]+.5&&e.boundingBox.minY<=to[1]+1.01&&supported(w,e))return true;}return false;}
 static int[] lastNormalizedLow,lastNormalizedHigh;
 static boolean normalizedWalk(W w,int[] low,int[] high,int width,boolean alongX){
  for(int side=-width/2;side<=width/2;side++){int lx=low[0]+(alongX?0:side),lz=low[2]+(alongX?side:0),hx=high[0]+(alongX?0:side),hz=high[2]+(alongX?side:0);
   for(int offset:new int[]{0,-1,1,-2,2,-3,3,-4,4,-5,5,-6,6}){int ly=low[1]+offset;Block f=w.getBlock(lx,ly,lz);if(f==Blocks.air||f instanceof RemasterBlock&&((RemasterBlock)f).id().contains("roof")||!f.isOpaqueCube()&&!(f instanceof BlockStairs)&&!f.getUnlocalizedName().contains("stairs"))continue;E e=new E(w);e.setPosition(lx+.5,ly+1,lz+.5);e.onGround=true;if(!w.getCollidingBoundingBoxes(e,e.boundingBox).isEmpty())continue;
    for(int hy=high[1]-6;hy<=high[1]+6;hy++){if(Math.abs(hy-ly)>1||w.getBlock(hx,hy,hz)==Blocks.air||w.getBlock(hx,hy,hz) instanceof RemasterBlock&&((RemasterBlock)w.getBlock(hx,hy,hz)).id().contains("roof"))continue;E target=new E(w);target.setPosition(hx+.5,hy+1,hz+.5);if(!w.getCollidingBoundingBoxes(target,target.boundingBox).isEmpty())continue;int[][] paths=lx!=hx&&lz!=hz?new int[][]{{hx,lz,hx,hz},{lx,hz,hx,hz}}:new int[][]{{hx,hz,hx,hz}};for(int[] path:paths){e.setPosition(lx+.5,ly+1,lz+.5);e.onGround=true;move(e,path[0]+.5,path[1]+.5);move(e,path[2]+.5,path[3]+.5);for(int settle=0;settle<12;settle++)e.moveEntity(0,-.12,0);if(Math.abs(e.posX-hx-.5)<.1&&Math.abs(e.posZ-hz-.5)<.1&&e.boundingBox.minY>=hy+.5&&e.boundingBox.minY<=hy+1.01&&!w.getCollidingBoundingBoxes(e,AxisAlignedBB.getBoundingBox(e.boundingBox.minX,e.boundingBox.minY-.05,e.boundingBox.minZ,e.boundingBox.maxX,e.boundingBox.minY+.01,e.boundingBox.maxZ )).isEmpty()&&exactWalk(w,new int[]{hx,hy,hz},new int[]{lx,ly,lz})){lastNormalizedLow=new int[]{lx,ly,lz};lastNormalizedHigh=new int[]{hx,hy,hz};return true;}}}
   }
  }return false;
 }

}
