import net.minecraft.item.ItemStack;
class Material {boolean isSolid(){return false;}}
class Block {Material getMaterial(){return new Material();}}
class BlocksGTSR {static final Block abyssalFluid=new Block();}
class Items {static final Object bucket=new Object(),water_bucket=new Object();}
class MovingObjectPosition {enum MovingObjectType{BLOCK}MovingObjectType typeOfHit=MovingObjectType.BLOCK;int blockX,blockY=60,blockZ,sideHit=5;}
class World {boolean isRemote,writeSuccess=true;int writes;Block block=BlocksGTSR.abyssalFluid;Provider provider=new Provider();static class Provider{int dimensionId=78;}
 Block getBlock(int x,int y,int z){return block;}int getBlockMetadata(int x,int y,int z){return 0;}boolean isAirBlock(int x,int y,int z){return true;}
 boolean setBlockToAir(int x,int y,int z){writes++;return writeSuccess;}boolean setBlock(int x,int y,int z,Block b,int meta,int flags){writes++;return writeSuccess;}
 boolean canMineBlock(Player p,int x,int y,int z){return true;}int getHeight(){return 256;}}
class Player {Object capabilities=new Object();int deniedX=Integer.MIN_VALUE;boolean canPlayerEdit(int x,int y,int z,int side,ItemStack s){return x!=deniedX;}}
class Event {enum Result{DEFAULT,ALLOW,DENY}}
class FillBucketEvent {World world=new World();MovingObjectPosition target=new MovingObjectPosition();ItemStack current,result;Player entityPlayer=new Player();Event.Result outcome=Event.Result.DEFAULT;boolean canceled;
 void setResult(Event.Result r){outcome=r;}void setCanceled(boolean c){canceled=c;}}
public class BucketBoundary {
 static Object abyssalBucket=new Object();
 __METHODS__
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static FillBucketEvent event(Object item){FillBucketEvent e=new FillBucketEvent();e.current=new ItemStack(item);return e;}
 public static void main(String[] args){BucketBoundary handler=new BucketBoundary();
  FillBucketEvent e=event(Items.bucket);e.entityPlayer.deniedX=0;handler.onFillBucket(e);
  check(e.outcome!=Event.Result.ALLOW&&e.result==null&&e.world.writes==0&&e.canceled,"SR04 denied scoop must neither edit nor exchange or fall through");
  e=event(Items.water_bucket);e.entityPlayer.deniedX=1;handler.onFillBucket(e);check(e.canceled&&e.result==null&&e.world.writes==0,"placement checks offset destination permission");
  e=event(Items.bucket);e.world.writeSuccess=false;handler.onFillBucket(e);check(e.canceled&&e.result==null,"failed scoop does not exchange");
  e=event(Items.water_bucket);e.world.writeSuccess=false;handler.onFillBucket(e);check(e.canceled&&e.result==null,"failed placement does not exchange");
  e=event(Items.water_bucket);handler.onFillBucket(e);check(e.outcome==Event.Result.ALLOW&&e.result.getItem()==Items.bucket&&e.world.writes==1,"successful placement exchanges once");
  e=event(Items.bucket);handler.onFillBucket(e);check(e.outcome==Event.Result.ALLOW&&e.result.getItem()==abyssalBucket&&e.world.writes==1,"successful scoop exchanges once");
  e=event(Items.water_bucket);e.target.blockY=255;e.target.sideHit=1;handler.onFillBucket(e);check(e.canceled&&e.result==null&&e.world.writes==0,"out-of-height placement rejected");
  e=event(Items.bucket);e.world.isRemote=true;handler.onFillBucket(e);check(e.world.writes==0,"client prediction never writes world");
  System.out.println("PASS SR04 bucket permission/commit boundaries");
 }
}
