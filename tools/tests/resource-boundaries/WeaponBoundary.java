import java.util.*;import net.minecraft.item.ItemStack;import net.minecraft.nbt.*;
enum WeaponKind {QLZ04,SINGULARITY;int id=0,capacity=100;}
class PortableWeapons {static Object[] mimicAmmo={new Object()},ammo={new Object()};}
public class WeaponBoundary {
 static class EntityItem {}
 static class World {List<EntityItem> loadedEntityList=new ArrayList<>();}
 static class Inventory {ItemStack[] mainInventory=new ItemStack[4];int space,accepted;
  void markDirty(){}
  boolean addItemStackToInventory(ItemStack s){int n=Math.min(space,s.stackSize);space-=n;accepted+=n;s.stackSize-=n;return n>0;}}
 static class EntityPlayerMP {Inventory inventory=new Inventory();World worldObj=new World();boolean captureDrops,reject;List<EntityItem> capturedDrops=new ArrayList<>();int dropped;EntityItem entityDropItem(ItemStack s,float offset){EntityItem e=new EntityItem();if(captureDrops)capturedDrops.add(e);else if(!reject){worldObj.loadedEntityList.add(e);dropped+=s.stackSize;}return e;}}
 static class Session {ItemStack gun=new ItemStack(1,1,0);EntityPlayerMP player=new EntityPlayerMP();}
 static NBTTagCompound data(ItemStack s){if(s.tag==null)s.tag=new NBTTagCompound();return s.tag;}
 static boolean matches(ItemStack s,WeaponKind k,int old){return s!=null&&s.getItem()==(old==1?PortableWeapons.mimicAmmo[k.id]:PortableWeapons.ammo[k.id]);}
 static String packId(ItemStack s){return data(s).getString("id");}
 __METHODS__
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static Session session(int paid){Session s=new Session();data(s.gun).setInteger("magazine",paid);data(s.gun).setInteger("paidMagazine",paid);data(s.gun).setString("returnPack","pack");return s;}
 public static void main(String[] args){WeaponBoundary controller=new WeaponBoundary();
  Session s=session(5);controller.refundMagazine(s,WeaponKind.SINGULARITY,0);check(s.player.dropped==5,"SR05 full inventory must receive paid singularity ammo as explicit drop");
  s=session(5);s.player.inventory.space=2;controller.refundMagazine(s,WeaponKind.SINGULARITY,0);check(s.player.inventory.accepted==2&&s.player.dropped==3,"partial acceptance returns only remaining ammo even when native insertion reports true");
  controller.refundMagazine(s,WeaponKind.SINGULARITY,0);check(s.player.dropped==3,"refund cannot deliver twice");
  s=session(7);controller.refundMagazine(s,WeaponKind.QLZ04,0);check(s.player.dropped==1&&data(s.gun).getInteger("paidMagazine")==0,"missing ammo pack returns one paid pack on full inventory");
  s=session(7);ItemStack pack=new ItemStack(PortableWeapons.ammo[0]);pack.setItemDamage(30);data(pack).setString("id","pack");s.player.inventory.mainInventory[0]=pack;controller.refundMagazine(s,WeaponKind.QLZ04,0);check(pack.getItemDamage()==23&&s.player.dropped==0,"existing original pack is replenished in place");
  s=session(5);data(s.gun).setInteger("paidMagazine",2);s.player.inventory.space=10;controller.refundMagazine(s,WeaponKind.SINGULARITY,1);check(s.player.inventory.accepted==2,"only paid magazine rounds returned");
  s=session(5);s.player.inventory.space=2;s.player.reject=true;controller.refundMagazine(s,WeaponKind.SINGULARITY,0);
  check(s.player.inventory.accepted==2&&s.player.dropped==0&&data(s.gun).getInteger("paidMagazine")==3,"rejected drop preserves three unpaid rounds after partial insertion");
  s.player.reject=false;controller.refundMagazine(s,WeaponKind.SINGULARITY,0);check(s.player.dropped==3&&data(s.gun).getInteger("paidMagazine")==0,"retry delivers only unpaid singularity remainder");
  s=session(7);controller.refundMagazine(s,WeaponKind.QLZ04,1);check(s.player.dropped==0&&data(s.gun).getInteger("paidMagazine")==7,"missing mimic never reconstructs a regenerating asset");
  System.out.println("PASS SR05 refund conservation boundaries (9 assertions)");
 }
}
