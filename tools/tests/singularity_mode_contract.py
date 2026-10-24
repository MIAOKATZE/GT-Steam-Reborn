"""Compile the real mode base against isolated GT/inventory boundaries; no game integration claim."""

from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build/machine-audit/singularity-mode"
PACKAGE = "com/miaokatze/gtsr/common/machine/base"
SOURCES = {
    "net/minecraft/item/ItemStack.java": """
package net.minecraft.item;
public class ItemStack { public int stackSize; public final int id;
 public ItemStack(int id,int size){this.id=id;stackSize=size;} }
""",
    "net/minecraft/nbt/NBTTagCompound.java": """
package net.minecraft.nbt;
public class NBTTagCompound { private final java.util.Map<String,Integer> data=new java.util.HashMap<>();
 public void setInteger(String key,int value){data.put(key,value);}
 public int getInteger(String key){return data.getOrDefault(key,0);}
 public boolean hasKey(String key){return data.containsKey(key);} }
""",
    "net/minecraft/util/EnumChatFormatting.java": "package net.minecraft.util; public enum EnumChatFormatting { WHITE }",
    "org/apache/commons/lang3/tuple/Pair.java": """
package org.apache.commons.lang3.tuple;
public final class Pair<A,B> { private A a; private B b;
 public static <A,B> Pair<A,B> of(A a,B b){Pair<A,B> p=new Pair<>();p.a=a;p.b=b;return p;}
 public A getLeft(){return a;} public B getRight(){return b;} }
""",
    "com/miaokatze/gtsr/common/api/enums/GTSRItemList.java": """
package com.miaokatze.gtsr.common.api.enums;
import net.minecraft.item.ItemStack;
public enum GTSRItemList { SteamEntangledSingularity, CriticalSteamEntangledSingularity;
 public ItemStack get(int count){return new ItemStack(ordinal()+1,count);} }
""",
    "com/miaokatze/gtsr/common/api/progress/IGTSRProgressProvider.java": """
package com.miaokatze.gtsr.common.api.progress;
public interface IGTSRProgressProvider { java.util.List<GTSRProgressEntry> getProgressEntries();
 boolean hasEntry(String key); double getEntryValue(String key); String getDisplayKey(String key); }
""",
    "com/miaokatze/gtsr/common/api/progress/GTSRProgressEntry.java": """
package com.miaokatze.gtsr.common.api.progress;
import net.minecraft.util.EnumChatFormatting;
import java.util.function.*;
public class GTSRProgressEntry {
 public static GTSRProgressEntry of(String a,String b,String c,EnumChatFormatting d,DoubleSupplier e){return null;}
 public static GTSRProgressEntry ofCustom(String a,String b,EnumChatFormatting c,DoubleSupplier d,DoubleFunction<String> e){return null;}}
""",
    "com/miaokatze/gtsr/common/api/progress/GTSRProgressBar.java": """
package com.miaokatze.gtsr.common.api.progress;
public class GTSRProgressBar {
 public java.util.List<GTSRProgressEntry> getProgressEntries(){return java.util.List.of();}
 public boolean hasEntry(String k){return false;} public double getEntryValue(String k){return 0;}
 public String getDisplayKey(String k){return k;} public void registerEntry(GTSRProgressEntry e){} }
""",
    "gregtech/api/interfaces/tileentity/IGregTechTileEntity.java": """
package gregtech.api.interfaces.tileentity;
public interface IGregTechTileEntity { boolean isAllowedToWork(); boolean isClientSide(); void markDirty(); }
""",
    "gregtech/api/metatileentity/implementations/MTEHatchInputBus.java": """
package gregtech.api.metatileentity.implementations;
import net.minecraft.item.ItemStack;
public class MTEHatchInputBus {
 public ItemStack stack; public int extracted,updates; public boolean refuse,wrong;
 public MTEHatchInputBus(int id,int size){stack=new ItemStack(id,size);}
 public int getSizeInventory(){return 1;} public ItemStack getStackInSlot(int slot){return stack;}
 public ItemStack decrStackSize(int slot,int amount){
  if(refuse)return null; int taken=Math.min(amount,stack.stackSize);stack.stackSize-=taken;extracted+=taken;
  return new ItemStack(wrong?99:stack.id,taken);}
 public void updateSlots(){updates++;} }
""",
    "gregtech/api/util/GTUtility.java": """
package gregtech.api.util;
import java.util.List; import net.minecraft.item.ItemStack;
public class GTUtility { public static <T> List<T> validMTEList(List<T> list){return list;}
 public static boolean areUnificationsEqual(ItemStack a,ItemStack b,boolean nbt){return a!=null&&b!=null&&a.id==b.id;} }
""",
    "gregtech/api/metatileentity/implementations/MTEEnhancedMultiBlockBase.java": """
package gregtech.api.metatileentity.implementations;
import java.util.*; import net.minecraft.nbt.NBTTagCompound;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
public class MTEEnhancedMultiBlockBase<T> {
 public final List<MTEHatchInputBus> mInputBusses=new ArrayList<>();
 public int mMaxProgresstime; public IGregTechTileEntity base; public int starts,ends;
 public MTEEnhancedMultiBlockBase(String n){} public MTEEnhancedMultiBlockBase(int id,String n,String r){}
 public IGregTechTileEntity getBaseMetaTileEntity(){return base;}
 public void onPostTick(IGregTechTileEntity base,long tick){}
 public void startRecipeProcessing(){starts++;} public void endRecipeProcessing(){ends++;}
 public void saveNBTData(NBTTagCompound nbt){} public void loadNBTData(NBTTagCompound nbt){} }
""",
    PACKAGE + "/SingularityModeContract.java": """
package com.miaokatze.gtsr.common.machine.base;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
public final class SingularityModeContract {
 static int checks;
 static void check(boolean condition,String label){checks++;if(!condition)throw new AssertionError(label);}
 static class Tile implements IGregTechTileEntity {
  boolean allowed=true,client; int dirties;
  public boolean isAllowedToWork(){return allowed;} public boolean isClientSide(){return client;}
  public void markDirty(){dirties++;} }
 static class Machine extends MTESingularityModeMachineBase<Machine> {
  final Tile tile=new Tile(); int expired;
  Machine(){super("fixture");base=tile;mMaxProgresstime=1;}
  protected void onSingularityModeExpired(){expired++;}
  void evaluate(){checkSingularityMode();}
  boolean consume(int amount){return consumeSingularityFromInputBuses(amount,new ItemStack(1,1));}
  void ticks(int amount){for(int i=0;i<amount;i++)onPostTick(tile,i);} }
 public static void main(String[] args){
  Machine stopped=new Machine();stopped.tile.allowed=false;stopped.mSingularityMode=2;stopped.mSingularityModeTicks=5;
  MTEHatchInputBus fuel=new MTEHatchInputBus(2,2);stopped.mInputBusses.add(fuel);stopped.ticks(20);
  check(stopped.mSingularityMode==0,"stopped expired mode must retire");
  check(stopped.expired==1&&stopped.tile.dirties==1,"expiry callback and persistence exactly once");
  check(fuel.extracted==0,"stopped mode never renews");stopped.ticks(40);
  check(stopped.expired==1,"inactive mode must not expire twice");
  Machine idle=new Machine();idle.mMaxProgresstime=0;idle.mSingularityMode=1;idle.evaluate();
  check(idle.mSingularityMode==0&&idle.expired==1,"idle expired mode also retires");
  Machine pending=new Machine();pending.tile.allowed=false;pending.mSingularityMode=1;pending.mSingularityModeTicks=25;pending.ticks(20);
  check(pending.mSingularityMode==1&&pending.mSingularityModeTicks==5,"unexpired stopped mode survives remaining TTL");
  Machine renewal=new Machine();renewal.mSingularityMode=2;renewal.mInputBusses.add(new MTEHatchInputBus(2,1));renewal.evaluate();
  check(renewal.mSingularityMode==2&&renewal.mSingularityModeTicks==4000,"same mode seamless renewal");
  check(renewal.expired==0&&renewal.mInputBusses.get(0).extracted==1,"renewal consumes exactly one fuel");
  Machine priority=new Machine();priority.mInputBusses.add(new MTEHatchInputBus(1,2));priority.mInputBusses.add(new MTEHatchInputBus(2,2));priority.evaluate();
  check(priority.mSingularityMode==2&&priority.mInputBusses.get(0).extracted==0,"critical entry priority preserved");
  Machine refuse=new Machine();MTEHatchInputBus broken=new MTEHatchInputBus(1,1);broken.refuse=true;refuse.mInputBusses.add(broken);
  check(!refuse.consume(1),"null extraction must not authorize fuel");
  refuse.evaluate();check(refuse.mSingularityMode==0,"refused fuel never enables mode");
  Machine wrong=new Machine();MTEHatchInputBus changed=new MTEHatchInputBus(1,1);changed.wrong=true;wrong.mInputBusses.add(changed);
  check(!wrong.consume(1),"different returned item never authorizes fuel");
  Machine empty=new Machine();check(!empty.consume(1),"no fuel fails without mode");
  Machine ordinary=new Machine();ordinary.mInputBusses.add(new MTEHatchInputBus(1,2));
  check(ordinary.consume(1)&&ordinary.mInputBusses.get(0).extracted==1,"ordinary local debit succeeds");
  NBTTagCompound nbt=new NBTTagCompound();renewal.saveNBTData(nbt);Machine reloaded=new Machine();reloaded.loadNBTData(nbt);
  check(reloaded.mSingularityMode==2&&reloaded.mSingularityModeTicks==4000,"NBT mode and remaining TTL roundtrip");
  Machine client=new Machine();client.tile.client=true;client.mSingularityMode=1;client.mSingularityModeTicks=1;client.ticks(40);
  check(client.mSingularityModeTicks==1&&client.expired==0,"client never consumes or expires server state");
  System.out.println("PASS SingularityModeContract checks="+checks);
 } }
""",
}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    sources = []
    for name, text in SOURCES.items():
        path = OUT / "src" / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
        sources.append(path)
    sources.append(ROOT / "src/main/java" / PACKAGE / "MTESingularityModeMachineBase.java")
    jdk = Path.home() / ".gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin"
    javac = str(jdk / "javac.exe") if jdk.exists() else shutil.which("javac")
    java = str(jdk / "java.exe") if jdk.exists() else shutil.which("java")
    subprocess.run([javac, "-encoding", "UTF-8", "-d", str(OUT / "classes"), *map(str, sources)], check=True)
    result = subprocess.run([java, "-cp", str(OUT / "classes"),
        "com.miaokatze.gtsr.common.machine.base.SingularityModeContract"], capture_output=True, text=True)
    (OUT / "result.txt").write_text(result.stdout + result.stderr, encoding="utf-8")
    print(result.stdout + result.stderr, end="")
    result.check_returncode()


if __name__ == "__main__":
    main()
