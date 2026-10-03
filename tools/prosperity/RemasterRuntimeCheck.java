import java.lang.reflect.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.*;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.*;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.*;
import com.google.gson.*;
import com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.*;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.*;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.*;

/** Real production authority and MC NBT; only storage, loaded geometry and write faults are adapted. */
public class RemasterRuntimeCheck {
 static sun.misc.Unsafe u;
 static int checks;
 static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static void set(Object o,Class<?> type,String name,Object value)throws Exception {
  Field f=type.getDeclaredField(name);f.setAccessible(true);
  u.putObject(o,u.objectFieldOffset(f),value);
 }
 static void setBlock(String field,Block value)throws Exception {
  Field f=Blocks.class.getField(field);u.putObject(u.staticFieldBase(f),u.staticFieldOffset(f),value);
 }
 static class W extends World {
  Map<String,Block> blocks;Map<String,Integer> metas;Map<String,TileEntity> tiles;
  Set<String> unloadedChunks=new HashSet<>();IChunkProvider loadedChunksProxy;boolean actualSideQueries;
  long time,seed;boolean loaded,occupied,collision,liquid;Integer supportY;String failAt;int writes,failWrite,spawns,ownerChecks;EntityPlayerMP player;
  W(){super((ISaveHandler)null,(String)null,(WorldProvider)null,(WorldSettings)null,(Profiler)null);}
  String key(int x,int y,int z){return x+","+y+","+z;}
  public Block getBlock(int x,int y,int z){return blocks.getOrDefault(key(x,y,z),Blocks.air);}
  public int getBlockMetadata(int x,int y,int z){return metas.getOrDefault(key(x,y,z),0);}
  public boolean isSideSolid(int x,int y,int z,net.minecraftforge.common.util.ForgeDirection side,boolean fallback){return getBlock(x,y,z).isSideSolid(this,x,y,z,side);}
  public TileEntity getTileEntity(int x,int y,int z){return tiles.get(key(x,y,z));}
  public boolean blockExists(int x,int y,int z){return loaded&&y>=0&&y<256&&(unloadedChunks==null||!unloadedChunks.contains((x>>4)+","+(z>>4)));}
  public IChunkProvider getChunkProvider(){if(loadedChunksProxy==null)loadedChunksProxy=(IChunkProvider)Proxy.newProxyInstance(IChunkProvider.class.getClassLoader(),new Class[]{IChunkProvider.class},(proxy,method,args)->{if(method.getName().equals("chunkExists")){ownerChecks++;return loaded&&(unloadedChunks==null||!unloadedChunks.contains(args[0]+","+args[1]));}throw new AssertionError("retry must not load/provide chunks: "+method.getName());});return loadedChunksProxy;}
  public boolean setBlock(int x,int y,int z,Block b,int m,int flags){if(++writes==failWrite)return false;if(key(x,y,z).equals(failAt)){failAt=null;return false;}if(getBlock(x,y,z)==b&&getBlockMetadata(x,y,z)==m)return false;blocks.put(key(x,y,z),b);metas.put(key(x,y,z),m);tiles.remove(key(x,y,z));if(b.hasTileEntity(m)){TileEntity tile=b.createTileEntity(this,m);if(tile!=null){tile.setWorldObj(this);tile.xCoord=x;tile.yCoord=y;tile.zCoord=z;tiles.put(key(x,y,z),tile);}}return true;}
  public boolean setBlockMetadataWithNotify(int x,int y,int z,int m,int flags){metas.put(key(x,y,z),m);return true;}
  public void markBlockForUpdate(int x,int y,int z){}
  public boolean func_147451_t(int x,int y,int z){return true;}
  public void markTileEntityChunkModified(int x,int y,int z,TileEntity tile){}
  public void notifyBlocksOfNeighborChange(int x,int y,int z,Block b){}
  public void playSoundEffect(double x,double y,double z,String sound,float volume,float pitch){}
  public long getTotalWorldTime(){return time;}
  public long getSeed(){return seed;}
  public List getEntitiesWithinAABB(Class type,AxisAlignedBB box){return occupied?Collections.singletonList(new Object()):Collections.emptyList();}
  public List getCollidingBoundingBoxes(Entity e,AxisAlignedBB box){return collision?Collections.singletonList(box):Collections.emptyList();}
  public List getEntitiesWithinAABBExcludingEntity(Entity e,AxisAlignedBB box){return occupied?Collections.singletonList(new Object()):Collections.emptyList();}
  public boolean isAnyLiquid(AxisAlignedBB box){return liquid;}
  public boolean checkNoEntityCollision(AxisAlignedBB box){return !occupied;}
  public EntityPlayer getClosestPlayer(double x,double y,double z,double radius){return player;}
  public boolean isSideSolid(int x,int y,int z,net.minecraftforge.common.util.ForgeDirection side){return actualSideQueries?isSideSolid(x,y,z,side,false):supportY==null||y==supportY;}
  public boolean spawnEntityInWorld(Entity e){spawns++;if(loadedEntityList!=null)loadedEntityList.add(e);return true;}
  public Entity getEntityByID(int id){return null;}
  protected IChunkProvider createChunkProvider(){return null;}
  protected int func_152379_p(){return 0;}
 }
 static class P extends EntityPlayerMP { P(){super(null,null,null,null);} }
}
