"""Exercise production machine page replacement with bounded registry/keyboard/localization seams.

Checks page replacement, retained GT structure and dynamic item data, ownership and fail-safe matching.
This contract does not claim to render Minecraft tooltips or evaluate the machine economy.
"""
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/v208-machine-tooltip-contract'
JAVA = OUT / 'src'
CLASSES = OUT / 'classes'
JAVA.mkdir(parents=True, exist_ok=True)
CLASSES.mkdir(exist_ok=True)
PRODUCTION = ROOT / 'src/main/java/com/miaokatze/gtsr/client/tooltip'
loader = (ROOT / 'src/main/java/com/miaokatze/gtsr/loader/MachineLoader.java').read_text(encoding='utf-8')
entries = list(dict.fromkeys(re.findall(r'GTSRItemList\.(\w+)\s*\.set\(', loader)))
assert len(entries) == 78, len(entries)

stubs = {
    'net.minecraft.item.Item': 'public class Item {}',
    'net.minecraft.item.ItemStack': '''public class ItemStack {
 public final Item item; public final int damage; public Object nbt;
 public ItemStack(Item item,int damage){this.item=item;this.damage=damage;}
 public Item getItem(){return item;} public int getItemDamage(){return damage;}
}''',
    'gregtech.common.blocks.ItemMachines': 'public class ItemMachines extends net.minecraft.item.Item {}',
    'gregtech.api.GregTechAPI': '''public class GregTechAPI {
 public static final gregtech.api.interfaces.metatileentity.IMetaTileEntity[] METATILEENTITIES=
 new gregtech.api.interfaces.metatileentity.IMetaTileEntity[1000];
}''',
    'gregtech.api.interfaces.metatileentity.IMetaTileEntity': '''public interface IMetaTileEntity {
 String getMetaName(); String[] getDescription();
 void addAdditionalTooltipInformation(net.minecraft.item.ItemStack stack,java.util.List<String> lines);
 @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) @interface SkipGenerateDescription {}
}''',
    'gregtech.api.interfaces.ISecondaryDescribable': '''public interface ISecondaryDescribable {
 String[] getPrimaryDescription(); String[] getSecondaryDescription(); boolean isDisplaySecondaryDescription();
}''',
    'gregtech.api.util.GTUtility': '''public class GTUtility {
 public static boolean isStringValid(String s){return s!=null&&!s.isEmpty();}
}''',
    'gregtech.api.util.GTSplit': r'''public class GTSplit {
 public static String[] split(String s){return s.split("\\\\n");}
}''',
    'net.minecraft.util.EnumChatFormatting': '''public enum EnumChatFormatting {
 GRAY,AQUA,GREEN,RED,YELLOW,DARK_GRAY;
 public static String getTextWithoutFormattingCodes(String s){return s.replaceAll("§[0-9a-fk-or]", "");}
 public String toString(){return "§"+ordinal();}
}''',
    'net.minecraft.util.StatCollector': '''public class StatCollector {
 public static final java.util.Map<String,String> VALUES=new java.util.HashMap<>();
 public static String translateToLocal(String k){return VALUES.getOrDefault(k,k);}
 public static boolean canTranslate(String k){return VALUES.containsKey(k);}
}''',
    'com.miaokatze.gtsr.common.util.GTSRUtils': '''public class GTSRUtils {
 public static String getAddedByLine(){return "§fAdded by: §bGT§a-§6Steam§c-§9Reborn";}
}''',
    'com.miaokatze.gtsr.common.api.enums.GTSRItemList': '''public enum GTSRItemList {''' + ','.join(entries) + ''';
 public net.minecraft.item.Item item;public int meta;
 public boolean hasBeenSet(){return item!=null;} public net.minecraft.item.Item getItem(){return item;}
 public int getMeta(){return meta;}
}''',
}
for name, body in stubs.items():
    path = JAVA / (name.replace('.', '/') + '.java')
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text('package ' + name.rsplit('.', 1)[0] + ';\n' + body, encoding='utf-8')

contract = JAVA / 'com/miaokatze/gtsr/client/tooltip/MachineTooltipLayeringContract.java'
contract.parent.mkdir(parents=True, exist_ok=True)
contract.write_text(r'''
package com.miaokatze.gtsr.client.tooltip;
import java.util.*;
import net.minecraft.item.*;
import net.minecraft.util.StatCollector;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.*;
import gregtech.api.interfaces.metatileentity.*;

public class MachineTooltipLayeringContract {
 static int assertions;
 static void check(boolean test,String message){assertions++;if(!test)throw new AssertionError(message);}
 static String plain(String s){return GTSRMachineTooltips.plain(s);}
 static List<String> lines(String... lines){return new ArrayList<>(Arrays.asList(lines));}
 static boolean has(List<String> rows,String text){return rows.stream().anyMatch(s->plain(s).contains(text));}
 static OwnedMachine own(GTSRItemList entry,int id,gregtech.common.blocks.ItemMachines item){
  entry.item=item;entry.meta=id;OwnedMachine m=new OwnedMachine();GregTechAPI.METATILEENTITIES[id]=m;return m;
 }
 public static void main(String[] args)throws Exception{
  for(String line:java.nio.file.Files.readAllLines(java.nio.file.Path.of(args[0]))){
   int equals=line.indexOf('=');if(equals>0&&!line.startsWith("#"))StatCollector.VALUES.put(line.substring(0,equals),line.substring(equals+1));
  }
  StatCollector.VALUES.put("GT5U.MBTT.Hold","Hold");StatCollector.VALUES.put("GT5U.MBTT.Display","to display structure");
  var item=new gregtech.common.blocks.ItemMachines();var machine=own(GTSRItemList.LargeSteamFurnace,20,item);
  String speed=StatCollector.translateToLocal("gtsr.tooltip.steam_furnace.speed");
  String parallel=StatCollector.translateToLocal("gtsr.tooltip.shared.parallel")+": 24 / 64";
  String efficiency=StatCollector.translateToLocal("gtsr.tooltip.steam_furnace.steam_eff");
  machine.primary=new String[]{"type", "Long original operation explanation with "+StatCollector.translateToLocal("gtsr.tooltip.shared.parallel"),speed,efficiency,"Hold [LSHIFT] to display structure"};
  machine.structure=new String[]{"GT structure: 7x7x3","Original casing/hatch information",parallel};
  ItemStack stack=new ItemStack(item,20);Object tag=new Object();stack.nbt=tag;
  machine.additional=List.of("NBT: bound hub @ 1, 2, 3", "NBT: stored fluid 123 L");
  List<String> normal=lines("Title",machine.primary[0],machine.primary[1],speed,efficiency,machine.primary[4]);
  normal.addAll(machine.additional);normal.add("Installed cover: original");normal.add("gregtech:machine:20");
  check(GTSRMachineTooltips.rewrite(stack,normal,false),"normal page replaced");
  check(!has(normal,"Long original"),"default replaces rather than appends long page");
  check(has(normal,speed)&&has(normal,parallel)&&has(normal,efficiency),"default uses actual primary and structure parameters");
  check(has(normal,"Hold Shift")&&!has(normal,"Hold [LSHIFT]"),"one consistent Shift hint");
  check(has(normal,"NBT: bound hub")&&has(normal,"stored fluid")&&has(normal,"Installed cover")&&has(normal,"gregtech:machine:20"),"dynamic/GT/debug rows preserved");
  check(stack.nbt==tag&&stack.damage==20,"no stack or NBT mutation");
  for(boolean leftShift:new boolean[]{false,true}){
   machine.secondary=leftShift;List<String> details=lines("Title");
   details.addAll(Arrays.asList(leftShift?machine.structure:machine.primary));details.addAll(machine.additional);
   check(GTSRMachineTooltips.rewrite(stack,details,true),"left/right Shift page replaced");
   check(has(details,"Long original operation")&&has(details,"GT structure: 7x7x3")&&has(details,"Original casing/hatch"),"Shift preserves complete parameters and native structure");
   check(!has(details,"Smelt many furnace"),"Shift replaces concise purpose instead of stacking both pages");
   check(!has(details,"Hold [LSHIFT]")&&has(details,"Release Shift"),"detail page footer reflects active page");
  }
  machine.secondary=false;
  List<String> changed=lines("Title","Another mod transformed the description",speed,"NBT: bound hub @ 1, 2, 3");
  List<String> snapshot=new ArrayList<>(changed);
  check(!GTSRMachineTooltips.rewrite(stack,changed,false)&&changed.equals(snapshot),"unrecognized transformed block is untouched");
  List<String> foreign=lines("External title","External unchanged");snapshot=new ArrayList<>(foreign);
  check(!GTSRMachineTooltips.rewrite(new ItemStack(item,21),foreign,false)&&foreign.equals(snapshot),"same GT item adjacent external meta untouched");
  check(!GTSRMachineTooltips.rewrite(new ItemStack(new Item(),20),foreign,false)&&foreign.equals(snapshot),"same meta foreign item untouched");
  // Exercise real GT-style generated localization selection, not only source-array fallback.
  machine.primary=new String[]{"First","Second"};
  StatCollector.VALUES.put("gt.blockmachines.fixture.tooltip.20","Localized first\\nLocalized second");
  List<String> localized=lines("Title","Localized first","Localized second","NBT: untouched");
  check(GTSRMachineTooltips.rewrite(stack,localized,true)&&has(localized,"NBT: untouched"),"generated localized block replaced");
  StatCollector.VALUES.remove("gt.blockmachines.fixture.tooltip.20");
  var node=own(GTSRItemList.SingularityMinerNode,30,item);node.primary=new String[]{"Miner"};node.structure=new String[0];
  String prefix="gtsr.tooltip.miner_node.";
  String row0=StatCollector.translateToLocal(prefix+"row_0_level")+" | "+StatCollector.translateToLocal(prefix+"row_0_params");
  String row4=StatCollector.translateToLocal(prefix+"row_4_level")+" | "+StatCollector.translateToLocal(prefix+"row_4_params");
  // Dynamic text deliberately contains a table label: exact documentation comparison must preserve it.
  String dynamic="NBT: bound hub named "+StatCollector.translateToLocal(prefix+"row_0_level")+" @ 1, 2, 3";
  node.additional=List.of(dynamic,StatCollector.translateToLocal(prefix+"table_title"),row0,row4,
    StatCollector.translateToLocal(prefix+"upgrade_1"),"NBT: configured capacity 456");
  ItemStack miner=new ItemStack(item,30);List<String> compact=lines("Miner title","Miner");compact.addAll(node.additional);
  check(GTSRMachineTooltips.rewrite(miner,compact,false),"node summary replaced");
  check(has(compact,StatCollector.translateToLocal(prefix+"row_0_params"))&&has(compact,StatCollector.translateToLocal(prefix+"row_4_params")),"node key capacity tiers remain visible");
  check(!has(compact,StatCollector.translateToLocal(prefix+"table_title")),"node full table hidden in summary");
  check(compact.contains(dynamic)&&compact.contains("NBT: configured capacity 456"),"dynamic values survive node table compaction");
  List<String> detailedNode=lines("Miner title","Miner");detailedNode.addAll(node.additional);
  check(GTSRMachineTooltips.rewrite(miner,detailedNode,true)&&has(detailedNode,StatCollector.translateToLocal(prefix+"upgrade_1")),"Shift restores the full node upgrade table");
  var sun=GTSRMachineTooltipProfiles.summary(GTSRItemList.CriticalSun,List.of(),List.of(),List.of());
  check(has(sun,"8196")&&has(sun,"10,000,000")&&has(sun,"1,000,000,000,000,000"),"stellar summary uses actual policy limits and ignition price");
  var solar=GTSRMachineTooltipProfiles.summary(GTSRItemList.CriticalSolar,List.of(),List.of(),List.of());
  check(has(solar,"400 t")&&has(solar,"256"),"solar cycle and dust parallels come from runtime constants");
  var turbine=GTSRMachineTooltipProfiles.summary(GTSRItemList.CriticalTurbine,List.of(),List.of(),List.of());
  check(has(turbine,"2000%")&&has(turbine,"0.5/1.5/2.5")&&has(turbine,"x1000"),"turbine summary uses real efficiency and heat arithmetic");
  var chemical=GTSRMachineTooltipProfiles.summary(GTSRItemList.CriticalBattery,List.of(),List.of(),List.of());
  check(has(chemical,"167,772,160")&&has(chemical,"3,355,443,200")&&has(chemical,"5,368,709,120"),"chemical theoretical matched caps derive from real window cap/multiplier");
  System.out.println("PASS "+assertions+" checks; actual machine presentation classes, bounded registry seams; no game rendering");
 }
}
class OwnedMachine implements IMetaTileEntity,ISecondaryDescribable {
 String[] primary=new String[0],structure=new String[0];List<String> additional=List.of();boolean secondary;
 public String getMetaName(){return "fixture";}public String[] getDescription(){return secondary?structure:primary;}
 public String[] getPrimaryDescription(){return primary;}public String[] getSecondaryDescription(){return structure;}
 public boolean isDisplaySecondaryDescription(){return secondary;}
 public void addAdditionalTooltipInformation(ItemStack stack,List<String> lines){lines.addAll(additional);}
}
''', encoding='utf-8')

sources = sorted(JAVA.rglob('*.java')) + [PRODUCTION / (name + '.java')
    for name in ('GTSRMachineTooltips', 'GTSRMachineTooltipProfiles')]
sources += [ROOT / 'src/main/java/com/miaokatze/gtsr/common/critical' / (name + '.java')
    for name in ('CriticalChemicalMath', 'CriticalEnergyMath', 'CriticalStellarPolicy')]
subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(CLASSES), *map(str, sources)], cwd=ROOT, check=True)
result = subprocess.run(['java', '-cp', str(CLASSES), 'com.miaokatze.gtsr.client.tooltip.MachineTooltipLayeringContract',
    str(ROOT / 'src/main/resources/assets/gtsr/lang/en_US.lang')], cwd=ROOT, check=True, capture_output=True, text=True)
(OUT / 'result.txt').write_text(result.stdout, encoding='utf-8')
print(result.stdout, end='')
