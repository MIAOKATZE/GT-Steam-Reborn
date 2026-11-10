#!/usr/bin/env python3
"""Run native transfer and bounded pipe/registration failure contracts without starting the game."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

from compat.fixtures import BQ

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "src/main/java/com/miaokatze/gtsr"


def method(text, name):
    import re
    match = re.search(r"    (?:protected|private) (?:static )?[^\n]*\b" + name + r"\([^)]*\) \{", text)
    if match is None:
        raise AssertionError("Missing production method: " + name)
    start = text.index("{", match.start())
    depth, end = 1, start + 1
    while depth:
        depth += (text[end] == "{") - (text[end] == "}")
        end += 1
    return text[match.start():end]


def pipe_fixture():
    text = (JAVA / "common/machine/base/MTERemoteWorkerNode.java").read_text(encoding="utf-8")
    methods = "\n".join(method(text, name) for name in ["consumeMiningPipeFromInputs", "retainMiningPipe",
        "advanceMiningPipe", "savePendingMiningPipe"])
    return r'''
import java.util.*;
import com.miaokatze.gtsr.loader.RequiredRegistration;
public class PipeCommitContract {
 static int checks;
 static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static class Block {}
 static class Blocks {static final Block air=new Block();}
 static final Block TIP=new Block(),SHAFT=new Block();
 static class NBTTagCompound {Map<String,Object> data=new HashMap<>();void removeTag(String k){data.remove(k);}
  void setTag(String k,Object v){data.put(k,v);}NBTTagCompound getCompoundTag(String k){return (NBTTagCompound)data.get(k);}}
 static class ItemStack {int stackSize;ItemStack(int n){stackSize=n;}Object getItem(){return SHAFT;}
  ItemStack copy(){return new ItemStack(stackSize);}NBTTagCompound writeToNBT(NBTTagCompound n){n.data.put("count",stackSize);return n;}
  static ItemStack loadItemStackFromNBT(NBTTagCompound n){return n==null?null:new ItemStack((int)n.data.get("count"));}}
 static class World {Map<Integer,Block> blocks=new HashMap<>();Block getBlock(int x,int y,int z){return blocks.getOrDefault(y,Blocks.air);}
  boolean setBlock(int x,int y,int z,Block b){blocks.put(y,b);return true;}}
 static class FakePlayer {World worldObj=new World();}
 static class GTUtility {static boolean allow=true;static int attempts;
  static boolean setBlockByFakePlayer(FakePlayer p,int x,int y,int z,Block b,int meta,boolean sim){attempts++;return allow&&p!=null&&p.worldObj.setBlock(x,y,z,b);}
  static Block getBlockFromItem(Object i){return (Block)i;}}
 static class Base {int dirty;void markDirty(){dirty++;}}
 static class Worker {ItemStack[] mInventory={new ItemStack(3)};ItemStack mPendingMiningPipe;Base base=new Base();
  Base getBaseMetaTileEntity(){return base;}int getInputSlotCount(){return 1;}boolean isMiningPipe(ItemStack s){return s!=null;}
  __METHODS__
  void load(NBTTagCompound aNBT){mPendingMiningPipe = ItemStack.loadItemStackFromNBT(aNBT.getCompoundTag("gtsr.pendingMiningPipe"));}
 }
 public static void main(String[] a){
  Worker w=new Worker();FakePlayer p=new FakePlayer();p.worldObj.blocks.put(9,TIP);GTUtility.allow=false;
  ItemStack pipe=w.consumeMiningPipeFromInputs();check(w.mInventory[0].stackSize==2,"reserve exactly one pipe");
  check(!w.advanceMiningPipe(p,0,8,0,-1,TIP,pipe),"placement denial reports failure");
  check(p.worldObj.getBlock(0,9,0)==TIP && p.worldObj.getBlock(0,8,0)==Blocks.air,"denial leaves previous tip and target intact");
  check(w.mPendingMiningPipe==pipe,"denial retains the reserved resource");
  NBTTagCompound save=new NBTTagCompound();w.savePendingMiningPipe(save);Worker loaded=new Worker();loaded.load(save);
  check(loaded.mPendingMiningPipe.stackSize==1,"reserved pipe survives node/item NBT transfer");
  pipe=loaded.consumeMiningPipeFromInputs();check(loaded.mInventory[0].stackSize==3 && loaded.mPendingMiningPipe==null,"retry consumes reservation before new stock");
  GTUtility.allow=true;check(loaded.advanceMiningPipe(p,0,8,0,-1,TIP,pipe),"retry commits after actual placement");
  check(p.worldObj.getBlock(0,8,0)==TIP && p.worldObj.getBlock(0,9,0)==SHAFT,"previous tip retires after successful new tip");
  loaded.savePendingMiningPipe(save);check(!save.data.containsKey("gtsr.pendingMiningPipe"),"committed reservation does not replay on save");
  loaded.load(new NBTTagCompound());check(loaded.mPendingMiningPipe==null,"legacy NBT resets an absent reservation");
  int[] calls={0};RequiredRegistration.run("preInit","items",()->calls[0]++);check(calls[0]==1,"registration runs once");
  RuntimeException cause=new IllegalArgumentException("root");try{RequiredRegistration.run("preInit","blocks",()->{calls[0]++;throw cause;});throw new AssertionError();}
  catch(IllegalStateException e){check(e.getCause()==cause && e.getMessage().contains("phase=preInit") && e.getMessage().contains("group=blocks"),"failure preserves cause and phase/group");}
  check(calls[0]==2,"failed registration is not retried");
  LinkageError linkage=new NoClassDefFoundError("required");try{RequiredRegistration.run("afterGTPreload","machines",()->{throw linkage;});throw new AssertionError();}
  catch(IllegalStateException e){check(e.getCause()==linkage,"required linkage failure does not continue startup");}
  System.out.println("PASS pipe/registration contracts checks="+checks);
 }
}
'''.replace("__METHODS__", methods)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classpath-file", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    classes = args.out / "classes"
    classes.mkdir(exist_ok=True)
    cp = args.classpath_file.read_text(encoding="utf-8-sig").strip()
    fixtures = {
        "Loader.java": BQ["Loader.java"],
        "MTEHatchInputBus.java": """package gregtech.api.metatileentity.implementations; public class MTEHatchInputBus {
public net.minecraft.item.ItemStack[] stacks=new net.minecraft.item.ItemStack[2];public boolean blockSecond;
public int getSizeInventory(){return 2;}public net.minecraft.item.ItemStack getStackInSlot(int s){return stacks[s];}
public void setInventorySlotContents(int s,net.minecraft.item.ItemStack v){stacks[s]=v;}public int getInventoryStackLimit(){return 64;}
public boolean allowPutStack(gregtech.api.interfaces.tileentity.IGregTechTileEntity b,int s,net.minecraftforge.common.util.ForgeDirection d,
net.minecraft.item.ItemStack i){return !blockSecond||s==0;}}""",
        "GTUtility.java": """package gregtech.api.util;public class GTUtility {
public static boolean areStacksEqual(net.minecraft.item.ItemStack a,net.minecraft.item.ItemStack b){return a.getItem()==b.getItem()
&&a.getItemDamage()==b.getItemDamage()&&net.minecraft.item.ItemStack.areItemStackTagsEqual(a,b);}}""",
        "IGregTechTileEntity.java": "package gregtech.api.interfaces.tileentity;public interface IGregTechTileEntity {}",
        "PipeCommitContract.java": pipe_fixture(),
    }
    sources = []
    for name, content in fixtures.items():
        path = args.out / name
        path.write_text(content, encoding="utf-8")
        sources.append(path)
    sources += [ROOT / "tools/tests/AutoInputTransferRegression.java", JAVA / "common/util/AutoInputItemTransfer.java",
        JAVA / "loader/RequiredRegistration.java"]
    receipt = {"boundary": "Native GTNHLib 0.11.54 and MC ItemStack for transfer; controlled GT inventory. "
        "Verbatim production pipe commit/pending serialization methods with controlled world/GT/NBT endpoints; "
        "real RequiredRegistration. No game, events, full worker tick, GL, protection plugin or disk crash simulation.",
        "sources": {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}}
    commands = [("compile", ["javac", "-encoding", "UTF-8", "-proc:none", "-cp", cp, "-d", str(classes), *map(str, sources)]),
        *[(name, ["java", "-cp", str(classes) + os.pathsep + cp, name])
          for name in ["AutoInputTransferRegression", "PipeCommitContract"]]]
    for name, command in commands:
        result = subprocess.run(command, capture_output=True, timeout=60)
        output = (result.stdout + result.stderr).decode("utf-8", errors="replace")
        (args.out / (name + ".log")).write_text(output, encoding="utf-8")
        receipt[name] = {"exitCode": result.returncode, "output": output}
        (args.out / "receipt.json").write_text(json.dumps(receipt, indent=2), encoding="utf-8")
        print(output.encode("ascii", errors="backslashreplace").decode("ascii"), end="")
        if result.returncode:
            return result.returncode
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
