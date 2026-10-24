import java.util.*;import net.minecraft.item.ItemStack;import net.minecraft.nbt.*;
class MinerParent {public void saveNBTData(NBTTagCompound n){}public void setItemNBT(NBTTagCompound n){}public void loadNBTData(NBTTagCompound n){}}
public class MinerBoundary extends MinerParent {
 int mTipDepth,mStatus,mCycleTimer,mMinerTier;boolean mDisabled,mRetractDone,mNeedsDescend,mSoftDisabled,mForcedRetract,mHasStarted,mCrushedMode;
 final List<ItemStack> mPendingItems=new ArrayList<>();
 __METHODS__
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static void main(String[] args){
  MinerBoundary node=new MinerBoundary();node.mMinerTier=4;node.mCrushedMode=true;
  ItemStack ore=new ItemStack(17,4096,23);ore.tag=new NBTTagCompound();ore.tag.setString("Name","ore identity");node.mPendingItems.add(ore);
  NBTTagCompound item=new NBTTagCompound();node.setItemNBT(item);MinerBoundary placed=new MinerBoundary();placed.loadNBTData(item);
  check(placed.mPendingItems.size()==1,"SR02 recovered node item must own pending mined drops");
  check(placed.mPendingItems.get(0).stackSize==4096,"pending balance keeps full int count");
  check(placed.mPendingItems.get(0).damage==23&&ore.tag.equals(placed.mPendingItems.get(0).tag),"ore metadata and tag survive recovery");
  check(placed.mMinerTier==4&&placed.mCrushedMode,"upgrade and mode preserved");
  NBTTagCompound world=new NBTTagCompound();placed.saveNBTData(world);placed.loadNBTData(world);placed.loadNBTData(world);
  check(placed.mPendingItems.size()==1&&placed.mPendingItems.get(0).stackSize==4096,"world reload replaces pending without duplicate or byte loss");
  placed.mPendingItems.clear();placed.setItemNBT(item);placed.loadNBTData(item);check(placed.mPendingItems.isEmpty(),"reusing NBT clears old pending");
  ItemStack old=new ItemStack(17,64,0);NBTTagList list=new NBTTagList();list.appendTag(old.writeToNBT(new NBTTagCompound()));world.setTag("mPendingItems",list);placed.loadNBTData(world);
  check(placed.mPendingItems.get(0).stackSize==64,"legacy byte Count still loads");
  System.out.println("PASS SR02 mining recovery NBT boundaries");
 }
}
