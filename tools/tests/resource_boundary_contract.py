"""Exact production methods at isolated SR01/02/04/05 boundaries; not a GT/game integration test."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess
from hub_pending_output_contract import ROOT, SOURCES, method

OUT = ROOT / "build/machine-audit/resource-boundaries"
BASE = ROOT / "src/main/java/com/miaokatze/gtsr"


def methods(path, required, optional=()):
    text = (BASE / path).read_text(encoding="utf-8")
    return "\n".join(method(text, sig) for sig in [*required, *(s for s in optional if s in text)])


def main():
    sources = {k: v for k, v in SOURCES.items() if k.startswith("net/minecraft/nbt/")}
    sources["net/minecraft/item/ItemStack.java"] = '''
package net.minecraft.item;
import net.minecraft.nbt.NBTTagCompound;
public class ItemStack {
 public int stackSize,id,damage;public NBTTagCompound tag;private Object item;
 public ItemStack(int id,int size,int damage){this.id=id;stackSize=size;this.damage=damage;item=id;}
 public ItemStack(Object item){this.item=item;stackSize=1;id=1;}
 public Object getItem(){return item;}public int getItemDamage(){return damage;}public void setItemDamage(int v){damage=v;}
 public NBTTagCompound writeToNBT(NBTTagCompound n){n.setInteger("id",id);n.setInteger("Damage",damage);n.setByte("Count",(byte)stackSize);if(tag!=null)n.setTag("tag",tag);return n;}
 public static ItemStack loadItemStackFromNBT(NBTTagCompound n){if(n.getInteger("id")<=0)return null;ItemStack s=new ItemStack(n.getInteger("id"),n.getByte("Count"),n.getInteger("Damage"));s.tag=n.hasKey("tag")?n.getCompoundTag("tag"):null;return s;}
}'''
    sources["com/miaokatze/gtsr/config/Config.java"] = "package com.miaokatze.gtsr.config;public class Config {public static int prosperityDimId=78;}"
    sources["com/miaokatze/gtsr/common/api/enums/GTSRItemList.java"] = '''
package com.miaokatze.gtsr.common.api.enums;
import net.minecraft.item.ItemStack;
public enum GTSRItemList {CriticalSteamEntangledSingularity,SteamEntangledSingularity;
 public ItemStack get(int amount){return new ItemStack(20+ordinal(),amount,0);}}'''
    paths = {
        "FoundationBoundary": "common/critical/CriticalWorldData.java",
        "MinerBoundary": "common/machine/MTESingularityMinerNode.java",
        "BucketBoundary": "common/event/AbyssalFluidConversionHandler.java",
        "WeaponBoundary": "common/weapons/WeaponController.java",
    }
    snippets = {
        "FoundationBoundary": methods(paths["FoundationBoundary"], [
            "public CriticalFoundationChunk foundation(", "public CriticalFoundationChunk findFoundation(",
            "public void discardIncompleteFoundations(",
            "public boolean acquire(String id, String owner, int x, int y, int z, int size", "public void release(String id)"],
            ["private boolean referencedByOtherRegion("]),
        "MinerBoundary": methods(paths["MinerBoundary"], [
            "public void saveNBTData(", "public void setItemNBT(", "public void loadNBTData("], ["private void savePendingItems("]),
        "BucketBoundary": methods(paths["BucketBoundary"], [
            "public void onFillBucket(", "private static int offset(", "private static boolean playerCanEdit(", "private static boolean isProsperityDim("]),
        "WeaponBoundary": methods(paths["WeaponBoundary"], ["private void refundMagazine("], ["private static void returnAmmo("]),
    }
    for fixture, snippet in snippets.items():
        template = Path(__file__).with_name("resource-boundaries").joinpath(fixture + ".java").read_text(encoding="utf-8")
        # Keep the old caller usable for the pre-fix red run; method bodies are copied unchanged.
        discard = 'discardIncompleteFoundations("a", chunks)' if "String retiringId" in snippet else "discardIncompleteFoundations(chunks)"
        sources[fixture + ".java"] = template.replace("__METHODS__", snippet).replace("__DISCARD__", discard)
    OUT.mkdir(parents=True, exist_ok=True)
    files = []
    for name, text in sources.items():
        path = OUT / "src" / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
        files.append(path)
    jdk = Path.home() / ".gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin"
    javac = str(jdk / "javac.exe") if jdk.exists() else shutil.which("javac")
    java = str(jdk / "java.exe") if jdk.exists() else shutil.which("java")
    subprocess.run([javac, "-encoding", "UTF-8", "-d", str(OUT / "classes"), *map(str, files)], check=True)
    results = {}
    for fixture in paths:
        result = subprocess.run([java, "-cp", str(OUT / "classes"), fixture], capture_output=True, text=True)
        results[fixture] = {"exit": result.returncode, "output": result.stdout + result.stderr}
        print(result.stdout + result.stderr, end="")
    (OUT / "result.json").write_text(json.dumps(results, indent=2), encoding="utf-8")
    (OUT / "sources.json").write_text(json.dumps({p: hashlib.sha256((BASE / p).read_bytes()).hexdigest() for p in paths.values()}, indent=2), encoding="utf-8")
    if any(r["exit"] for r in results.values()):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
