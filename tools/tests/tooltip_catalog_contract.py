"""Run the production tooltip catalog/handler against bounded registry seams, without Minecraft or GL.

The seams test ownership, adjacent metadata, fluid decoding, NBT preservation and brand rules.
They do not establish that the real game registered every optional generated form or rendered the UI.
"""
from pathlib import Path
import json
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/rc2-tooltip-contract'
SOURCE = ROOT / 'src/main/java/com/miaokatze/gtsr/client/tooltip'
JAVA = OUT / 'src'
CLASSES = OUT / 'classes'
JAVA.mkdir(parents=True, exist_ok=True)
CLASSES.mkdir(exist_ok=True)

def source(relative):
    return (ROOT / 'src/main/java/com/miaokatze/gtsr' / relative).read_text(encoding='utf-8')

def strings(text):
    return re.findall(r'"([^"\n]+)"', text)

def string_array(text, name):
    return strings(re.search(r'\b' + name + r'\s*=\s*\{([^}]+)\}', text).group(1))

item_names = re.findall(r'\.setAndRegister\([\s\S]*?,\s*"([^"]+)"\s*,\s*true\)', source('loader/ItemLoader.java'))
lore = source('common/dimension/prosperity/lore/LoreRegistry.java')
legacy_relics = strings(re.search(r'for \(String id : new String\[\] \{([^}]+)\}', lore).group(1))
new_relics = string_array(source('common/dimension/prosperity/lore/HistoryProgress.java'), 'NEW_RELICS')
item_names += ['ProsperityHistory'] + ['ProsperityRelic_' + name for name in legacy_relics + new_relics]
weapons = source('common/weapons/PortableWeapons.java')
item_names += string_array(weapons, 'w') + string_array(weapons, 'a') + ['Mimic' + name for name in string_array(weapons, 'a')]
assert len(item_names) == len(set(item_names)) == 70

block_names = re.findall(r'GameRegistry\s*\.registerBlock\([\s\S]*?"([^"]+)"\s*\)', source('loader/BlockLoader.java'))
assert len(block_names) == 50
ruins = source('common/dimension/prosperity/architecture/RuinsArchitecture.java')
ruins_names = re.findall(r'register\("([^"]+)"', ruins) + string_array(ruins, 'metal') + string_array(ruins, 'stone')
stairs = re.search(r'for \(String id : new String\[\] \{([^}]+)\}\) \{\s*register\(id \+ "_stairs"', ruins)
ruins_names += [name + '_stairs' for name in strings(stairs.group(1))]
assert len(ruins_names) == len(set(ruins_names)) == 37
block_names += ['Ruins_' + name for name in ruins_names]
royal_names = re.findall(r'register\("([^"]+)"', source('common/dimension/prosperity/architecture/RoyalArchitecture.java'))
assert len(royal_names) == 19
block_names += ['Royal_' + name for name in royal_names]
remaster = source('common/dimension/prosperity/remaster/RemasterBlocks.java')
remaster_names = ['fiction_starter', 'fiction_witness_pedestal']
for number in re.findall(r'register\(block(\d+)\(\)\)', remaster):
    name = re.search(r'private static RemasterBlock block' + number + r'\(\) \{\s*return new RemasterBlock\(\s*"gtsr:([^"]+)"', remaster).group(1)
    remaster_names.append(name)
assert len(remaster_names) == len(set(remaster_names)) == 24
block_names += remaster_names
material_document = json.loads((ROOT / 'src/main/resources/assets/gtsr/critical/materials.json').read_text(encoding='utf-8'))
assert len(material_document['definitions']) == 65
block_names += ['critical_' + name for name in material_document['definitions']]
block_names += ['SealedChest', 'UnsealedChest', 'RuinMechanism', 'SpacetimeAltarCore', 'critical_render_anchor']
assert len(block_names) == len(set(block_names)) == 200
inventory = {'independent_items': item_names, 'block_declarations': block_names}
(OUT / 'declaration-inventory.json').write_text(json.dumps(inventory, ensure_ascii=False, indent=2), encoding='utf-8')
inventory_rows = ['item\t' + name for name in item_names]
inventory_rows += ['block\t' + name + ('\tremaster' if name in remaster_names else '') for name in block_names]
inventory_file = OUT / 'declaration-inventory.tsv'
inventory_file.write_text('\n'.join(inventory_rows), encoding='utf-8')

stubs = {
    'net.minecraft.item.Item': 'public class Item { public net.minecraft.block.Block block; }',
    'net.minecraft.item.ItemStack': '''public class ItemStack {
 public final Item item; public final int damage; public Object nbt;
 public net.minecraftforge.fluids.Fluid fluid; public gregtech.api.objects.ItemData data;
 public ItemStack(Item i,int d){item=i;damage=d;} public Item getItem(){return item;}
 public int getItemDamage(){return damage;}
}''',
    'net.minecraft.block.Block': '''public class Block {
 public int light; public static Block getBlockFromItem(net.minecraft.item.Item i){return i.block;}
 public int getLightValue(){return light;}
}''',
    'net.minecraft.block.BlockFalling': 'public class BlockFalling extends Block {}',
    'net.minecraft.init.Blocks': 'public class Blocks { public static final net.minecraft.block.Block air=new net.minecraft.block.Block(); }',
    'net.minecraftforge.fluids.Fluid': '''public class Fluid {
 private final int id; public Fluid(int value){id=value;} public int getID(){return id;}
}''',
    'net.minecraftforge.fluids.FluidStack': '''public class FluidStack {
 private final Fluid fluid; public FluidStack(Fluid value){fluid=value;} public Fluid getFluid(){return fluid;}
}''',
    'gregtech.api.enums.Materials': '''public class Materials {
 public net.minecraftforge.fluids.Fluid fluid;
 public net.minecraftforge.fluids.FluidStack getFluid(int amount){return fluid==null?null:new net.minecraftforge.fluids.FluidStack(fluid);}
 public net.minecraftforge.fluids.FluidStack getGas(int amount){return getFluid(amount);}
}''',
    'gregtech.api.enums.OrePrefixes': 'public enum OrePrefixes {dustTiny,dustSmall,dust,cell}',
    'gregtech.api.enums.ItemList': '''public enum ItemList { Display_Fluid;
 public final net.minecraft.item.Item item=new net.minecraft.item.Item();
 public boolean hasBeenSet(){return true;} public net.minecraft.item.Item getItem(){return item;}
}''',
    'gregtech.api.objects.ItemData': '''public class ItemData {
 public gregtech.api.enums.OrePrefixes mPrefix; public MaterialStack mMaterial;
 public ItemData(gregtech.api.enums.Materials material,gregtech.api.enums.OrePrefixes prefix){mMaterial=new MaterialStack(material);mPrefix=prefix;}
 public static class MaterialStack {public gregtech.api.enums.Materials mMaterial;
 public MaterialStack(gregtech.api.enums.Materials value){mMaterial=value;}}
}''',
    'gregtech.api.util.GTOreDictUnificator': '''public class GTOreDictUnificator {
 public static gregtech.api.objects.ItemData getItemData(net.minecraft.item.ItemStack stack){return stack.data;}
}''',
    'gregtech.api.util.GTUtility': '''public class GTUtility {
 public static net.minecraftforge.fluids.FluidStack getFluidFromDisplayStack(net.minecraft.item.ItemStack stack){
 return stack.fluid==null?null:new net.minecraftforge.fluids.FluidStack(stack.fluid);}
}''',
    'com.miaokatze.gtsr.register.GTSRItemOrderCatalog': '''public class GTSRItemOrderCatalog {
 public static final java.util.List<net.minecraft.item.ItemStack> ITEMS=new java.util.ArrayList<>();
 public static java.util.List<net.minecraft.item.ItemStack> generatedDimensionItems(){return ITEMS;}
}''',
    'com.miaokatze.gtsr.common.api.enums.GTSRItemList': '''public enum GTSRItemList {Machine;
 public net.minecraft.item.Item item; public int meta;
 public boolean hasBeenSet(){return item!=null;} public net.minecraft.item.Item getItem(){return item;}
 public int getMeta(){return meta;}
}''',
    'com.miaokatze.gtsr.common.critical.CriticalMaterialCatalog': '''public class CriticalMaterialCatalog {
 public enum Usage { MANUAL_LOOM_ONLY, SHARED_LOOM_AND_AUTOMATIC, AUTOMATIC_BODY_ONLY, LEGACY_V1, FROZEN_ONLY, ORPHAN, GT_CONTROLLER_MARKER }
 public static CriticalMaterialCatalog current(){return new CriticalMaterialCatalog();}
 public Usage usage(String id){return Usage.valueOf(id);}
}''',
    'com.miaokatze.gtsr.common.critical.CriticalMaterials': '''public class CriticalMaterials {
 public static class MaterialBlock extends net.minecraft.block.Block {public String materialId;}
}''',
    'com.miaokatze.gtsr.common.dimension.prosperity.air.GTSRProsperityAirMaterials': '''public class GTSRProsperityAirMaterials {
 public static gregtech.api.enums.Materials WastesSigh,ThickGrease,MetalGrit,UmbralMire,SanzuResidualSteam,WitheredBreath,AbyssalObsession;
}''',
    'com.miaokatze.gtsr.common.dimension.prosperity.lore.ItemProsperityRelic': 'public class ItemProsperityRelic extends net.minecraft.item.Item {}',
    'com.miaokatze.gtsr.common.dimension.prosperity.lore.ItemProsperityJournal': 'public class ItemProsperityJournal extends ItemProsperityRelic {}',
    'com.miaokatze.gtsr.common.dimension.prosperity.lore.ItemOathFragment': 'public class ItemOathFragment extends ItemProsperityRelic {}',
    'com.miaokatze.gtsr.common.dimension.prosperity.lore.LoreRegistry': '''public class LoreRegistry {
 public static final java.util.Map<String,net.minecraft.item.Item> RELICS=new java.util.HashMap<>();
}''',
    'com.miaokatze.gtsr.common.dimension.prosperity.lore.NavigatorNetwork': 'public class NavigatorNetwork {public static final String RELIC_ID="navigator";}',
    'com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterBlock': '''public class RemasterBlock extends net.minecraft.block.Block {
 public boolean interactive; public boolean isInteractive(){return interactive;}
}''',
    'com.miaokatze.gtsr.common.dimension.prosperity.remaster.FictionPedestalBlock': 'public class FictionPedestalBlock extends RemasterBlock {}',
    'net.minecraft.util.EnumChatFormatting': '''public enum EnumChatFormatting {GRAY,YELLOW,AQUA,GREEN,WHITE,GOLD,LIGHT_PURPLE,RED;
 public static String getTextWithoutFormattingCodes(String value){return value.replaceAll("§[0-9a-fk-or]", "");}
 public String toString(){return "";}
}''',
    'net.minecraft.util.StatCollector': 'public class StatCollector {public static String translateToLocal(String key){return key;}}',
    'com.miaokatze.gtsr.common.util.GTSRUtils': '''public class GTSRUtils {
 public static String getAddedByLine(){return "§fAdded by: §bGT§a-§6Steam§c-§9Reborn";}
}''',
    'net.minecraftforge.common.MinecraftForge': '''public class MinecraftForge {
 public static final Bus EVENT_BUS=new Bus(); public static class Bus {public int count; public void register(Object o){count++;}}
}''',
    'net.minecraftforge.event.entity.player.ItemTooltipEvent': '''public class ItemTooltipEvent {
 public net.minecraft.item.ItemStack itemStack; public java.util.List<String> toolTip=new java.util.ArrayList<>();
 public ItemTooltipEvent(net.minecraft.item.ItemStack stack){itemStack=stack;}
}''',
    'cpw.mods.fml.common.eventhandler.EventPriority': 'public enum EventPriority {LOWEST}',
    'cpw.mods.fml.common.eventhandler.SubscribeEvent': 'public @interface SubscribeEvent {EventPriority priority();}',
    'com.miaokatze.gtsr.common.critical.CriticalMachineState': '''public class CriticalMachineState {
 public static boolean supportsV2Kind(CriticalMachineKind kind){return kind==CriticalMachineKind.SOLAR||kind==CriticalMachineKind.SUN;}
}''',
    'gregtech.api.util.MultiblockTooltipBuilder': '''public class MultiblockTooltipBuilder {
 public final java.util.List<String> infos=new java.util.ArrayList<>();
 public MultiblockTooltipBuilder addInfo(String value){infos.add(value);return this;}
 public MultiblockTooltipBuilder addMachineType(String value){return this;}
 public MultiblockTooltipBuilder toolTipFinisher(String... value){return this;}
 public MultiblockTooltipBuilder addSeparator(){return this;}
 public MultiblockTooltipBuilder beginStructureBlock(Object... value){return this;}
 public MultiblockTooltipBuilder addController(String value){return this;}
 public MultiblockTooltipBuilder addStructureInfo(String value){return this;}
 public MultiblockTooltipBuilder addInputBus(String value){return this;}
 public MultiblockTooltipBuilder addInputHatch(String value){return this;}
 public MultiblockTooltipBuilder addOutputBus(String value){return this;}
 public MultiblockTooltipBuilder addOutputHatch(String value){return this;}
 public MultiblockTooltipBuilder addEnergyHatch(String value){return this;}
}''',
}
# Forge exposes public fields on this identifier, rather than record accessors.
stubs['cpw.mods.fml.common.registry.GameRegistry'] = '''public class GameRegistry {
 private static final java.util.Map<net.minecraft.item.Item,UniqueIdentifier> IDS=new java.util.IdentityHashMap<>();
 public static UniqueIdentifier findUniqueIdentifierFor(net.minecraft.item.Item i){return IDS.get(i);}
 public static void bind(net.minecraft.item.Item i,String mod,String name){IDS.put(i,new UniqueIdentifier(mod,name));}
 public static class UniqueIdentifier {public final String modId,name; public UniqueIdentifier(String m,String n){modId=m;name=n;}}
}'''

for name, body in stubs.items():
    package, simple = name.rsplit('.', 1)
    path = JAVA / Path(*package.split('.')) / (simple + '.java')
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text('package ' + package + ';\n' + body, encoding='utf-8')

contract = JAVA / 'com/miaokatze/gtsr/client/tooltip/TooltipContract.java'
contract.parent.mkdir(parents=True, exist_ok=True)
contract.write_text('''package com.miaokatze.gtsr.client.tooltip;
import java.util.*;
import net.minecraft.item.*;
import net.minecraftforge.fluids.*;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.*;
import gregtech.api.objects.ItemData;
import com.miaokatze.gtsr.register.GTSRItemOrderCatalog;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.util.GTSRUtils;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
public final class TooltipContract {
 static int assertions;
 static void check(boolean condition,String message){assertions++;if(!condition)throw new AssertionError(message);}
 static ItemStack own(String name){Item item=new Item();GameRegistry.bind(item,"gtsr",name);return new ItemStack(item,0);}
 public static void main(String[] args) throws Exception {
  Item shared=new Item();Item machine=new Item();
  GameRegistry.bind(shared,"gregtech","meta");GameRegistry.bind(machine,"gregtech","machines");
  GTSRItemList.Machine.item=machine;GTSRItemList.Machine.meta=42;
  List<ItemStack> generated=GTSRItemOrderCatalog.ITEMS;
  // 42 dust definitions x three forms; 64 fluid/gas definitions x cell and display.
  int meta=100;
  String[] airFields={"WastesSigh","ThickGrease","MetalGrit","UmbralMire","SanzuResidualSteam","WitheredBreath","AbyssalObsession"};
  for(int index=0;index<106;index++){
   Materials material=new Materials();material.fluid=new Fluid(index+1);
   if(index>=99) com.miaokatze.gtsr.common.dimension.prosperity.air.GTSRProsperityAirMaterials.class.getField(airFields[index-99]).set(null,material);
   if(index<42){for(OrePrefixes prefix:new OrePrefixes[]{OrePrefixes.dustTiny,OrePrefixes.dustSmall,OrePrefixes.dust}){
    ItemStack stack=new ItemStack(shared,meta++);stack.data=new ItemData(material,prefix);generated.add(stack);}}
   else {
    ItemStack cell=new ItemStack(shared,meta++);cell.data=new ItemData(material,OrePrefixes.cell);generated.add(cell);
    ItemStack display=new ItemStack(ItemList.Display_Fluid.getItem(),material.fluid.getID());display.fluid=material.fluid;generated.add(display);
   }
  }
  GTSRTooltipCatalog.initialize();
  for(ItemStack stack:generated) check(GTSRTooltipCatalog.descriptionKeys(stack)!=null,"generated form coverage");
  for(int index=240;index<254;index++) check(GTSRTooltipCatalog.descriptionKeys(generated.get(index)).contains(
   "gtsr.tooltip.catalog."+(index>=252?"abyssal_material":"raw_gas")),"native material and real-fluid purpose");
  check(generated.size()==254,"106 material fixture count");
  check(GTSRTooltipCatalog.descriptionKeys(new ItemStack(shared,99))==null,"adjacent GT meta rejected");
  check(GTSRTooltipCatalog.descriptionKeys(new ItemStack(machine,41))==null,"adjacent machine meta rejected");
  check(GTSRTooltipCatalog.descriptionKeys(new ItemStack(machine,42))!=null,"exact machine included");
  ItemStack display=generated.stream().filter(s->s.fluid!=null).findFirst().orElseThrow();
  ItemStack spoof=new ItemStack(display.item,display.damage);spoof.fluid=new Fluid(display.damage);
  check(GTSRTooltipCatalog.descriptionKeys(spoof)==null,"same fluid ID with foreign object rejected");
  spoof.fluid=null;check(GTSRTooltipCatalog.descriptionKeys(spoof)==null,"invalid fluid display rejected");
  spoof.fluid=display.fluid;
  check(GTSRTooltipCatalog.descriptionKeys(spoof)!=null,"exact real fluid accepted");
  Item foreign=new Item();GameRegistry.bind(foreign,"other","IrradiatedMoonDust");
  ItemStack foreignStack=new ItemStack(foreign,0);
  check(GTSRTooltipCatalog.descriptionKeys(foreignStack)==null,"foreign same-name item rejected");
  int items=0,blocks=0;
  for(String row:java.nio.file.Files.readAllLines(java.nio.file.Path.of(args[0]))){
   String[] fields=row.split("\\t");ItemStack stack=own(fields[1]);
   if(fields[0].equals("block")){
    blocks++;stack.item.block=fields.length>2?new com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterBlock():new net.minecraft.block.Block();
    if(fields[1].startsWith("critical_")&&!fields[1].equals("critical_render_anchor")){
     var material=new com.miaokatze.gtsr.common.critical.CriticalMaterials.MaterialBlock();material.materialId="LEGACY_V1";stack.item.block=material;
    }
   }else{items++;}
   check(GTSRTooltipCatalog.descriptionKeys(stack)!=null,"actual declaration guard: "+fields[1]);
   ItemTooltipEvent declaration=new ItemTooltipEvent(stack);declaration.toolTip.add("original "+fields[1]);
   new GTSRTooltipHandler().onTooltip(declaration);
   check(declaration.toolTip.contains(GTSRUtils.getAddedByLine()),"actual declaration brand: "+fields[1]);
   for(String key:GTSRTooltipCatalog.descriptionKeys(stack)) check(java.nio.file.Files.readString(java.nio.file.Path.of(args[1])).contains(key+"="),"translated declaration use");
  }
  check(items==70&&blocks==200,"real static declaration inventory");
  for(int variant=0;variant<4;variant++){
   ItemStack base=own("RunawaySingularity");base.item.block=new net.minecraft.block.Block();
   ItemTooltipEvent node=new ItemTooltipEvent(new ItemStack(base.item,variant));
   node.toolTip.add("original variant "+variant);new GTSRTooltipHandler().onTooltip(node);
   check(node.toolTip.equals(List.of("original variant "+variant,GTSRUtils.getAddedByLine())),"singularity meta remains independent");
  }
  for(String usage: new String[]{"MANUAL_LOOM_ONLY","SHARED_LOOM_AND_AUTOMATIC","AUTOMATIC_BODY_ONLY","LEGACY_V1","FROZEN_ONLY","ORPHAN","GT_CONTROLLER_MARKER"}){
   ItemStack stack=own("critical_"+usage);
   var block=new com.miaokatze.gtsr.common.critical.CriticalMaterials.MaterialBlock();block.materialId=usage;stack.item.block=block;
   check(GTSRTooltipCatalog.descriptionKeys(stack).contains("gtsr.tooltip.catalog.critical."+usage.toLowerCase(Locale.ROOT)),"critical usage");
  }
  String brand=GTSRUtils.getAddedByLine(),plain="Added by: GT-Steam-Reborn";
  List<String> lines=new ArrayList<>(List.of("dynamic: 123 NBT",plain,"lore "+plain,brand,"author GT-Steam-Reborn"));
  GTSRTooltipHandler.normalizeBrand(lines,brand);
  check(lines.size()==4&&lines.get(1).equals(brand),"duplicate complete brand normalized");
  check(lines.contains("lore "+plain)&&lines.contains("author GT-Steam-Reborn"),"partial lines retained");
  GTSRTooltipHandler.normalizeBrand(lines,brand);check(lines.size()==4,"brand normalization idempotent");
  GTSRTooltipHandler.register();GTSRTooltipHandler.register();
  check(net.minecraftforge.common.MinecraftForge.EVENT_BUS.count==1,"one registration");
  ItemStack dust=own("IrradiatedMoonDust");Object nbt=new Object();dust.nbt=nbt;
  ItemTooltipEvent event=new ItemTooltipEvent(dust);event.toolTip.add("dynamic: 123 NBT");
  GTSRTooltipHandler handler=new GTSRTooltipHandler();handler.onTooltip(event);int count=event.toolTip.size();handler.onTooltip(event);
  check(event.toolTip.size()==count,"event additive lines idempotent");
  check(event.toolTip.get(0).equals("dynamic: 123 NBT")&&dust.nbt==nbt&&dust.damage==0,"original lines/NBT/meta retained");
  check(event.toolTip.contains("gtsr.tooltip.catalog.irradiated_unused"),"irradiated use limit");
  ItemTooltipEvent external=new ItemTooltipEvent(foreignStack);external.toolTip.add(plain);handler.onTooltip(external);
  check(external.toolTip.equals(List.of(plain)),"foreign tooltip fully untouched");
  for(var kind:com.miaokatze.gtsr.common.critical.CriticalMachineKind.values()){
   var info=com.miaokatze.gtsr.common.critical.CriticalTooltips.create(kind).infos;
   check(info.contains("gtsr.critical.tooltip.native.foundation.title")==kind.isReleased(),"manufacturing follows release scope");
   check(info.contains("gtsr.critical.tooltip.unobtainable")==!kind.isReleased(),"only frozen kinds state no recipe");
   check(info.contains("gtsr.critical.tooltip.native.turbine.rise")==(kind==com.miaokatze.gtsr.common.critical.CriticalMachineKind.TURBINE),"trend restricted to turbine");
   check(info.contains("gtsr.critical.tooltip.native.sun.ignition")==(kind==com.miaokatze.gtsr.common.critical.CriticalMachineKind.SUN),"stellar ignition scope");
   check(info.contains("gtsr.critical.tooltip.native.battery.windows")==(kind==com.miaokatze.gtsr.common.critical.CriticalMachineKind.BATTERY),"chemical window scope");
  }
  var loom=com.miaokatze.gtsr.common.critical.CriticalTooltips.create(null).infos;
  check(loom.contains("gtsr.critical.tooltip.native.foundation.title")&&!loom.contains("gtsr.critical.tooltip.unobtainable"),"loom has real crafting instructions");
  System.out.println("PASS "+assertions+" assertions; production catalog/handler with registry seams (no game/UI)");
 }
}
''', encoding='utf-8')

ledger = json.loads((ROOT / 'src/main/resources/assets/gtsr/industrial/r6-recipes.json').read_text(encoding='utf-8'))
assert len(ledger['materials']) == 99
assert sum(m['phase'] == 'dust' for m in ledger['materials']) == 42
assert sum(m['phase'] == 'fluid' for m in ledger['materials']) == 47
assert sum(m['phase'] == 'gas' for m in ledger['materials']) == 10

languages = []
for locale in ('zh_CN', 'en_US'):
    entries = {}
    for line in (ROOT / f'src/main/resources/assets/gtsr/lang/{locale}.lang').read_text(encoding='utf-8').splitlines():
        if line.startswith('gtsr.tooltip.catalog.'):
            key, value = line.split('=', 1)
            assert key not in entries, f'duplicate {locale}: {key}'
            assert value.strip(), f'empty {locale}: {key}'
            entries[key] = value
    languages.append(entries)
assert languages[0].keys() == languages[1].keys()
assert len(languages[0]) == 38

for path in SOURCE.glob('*.java'):
    implementation = path.read_text(encoding='utf-8')
    assert not re.search(r'\.(?:setTag|setTagCompound|setItemDamage|setInteger|setString)\s*\(', implementation), path
    assert '.stackTagCompound' not in implementation, path

sources = sorted(JAVA.rglob('*.java')) + [SOURCE / (name + '.java')
                                         for name in ('GTSRTooltipCatalog', 'GTSRTooltipHandler')]
sources += [ROOT / 'src/main/java/com/miaokatze/gtsr/common/critical' / (name + '.java')
            for name in ('CriticalTooltips', 'CriticalMachineKind')]
subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(CLASSES), *map(str, sources)], cwd=ROOT, check=True)
result = subprocess.run(['java', '-cp', str(CLASSES), 'com.miaokatze.gtsr.client.tooltip.TooltipContract',
                         str(inventory_file), str(ROOT / 'src/main/resources/assets/gtsr/lang/en_US.lang')],
                        cwd=ROOT, check=True, capture_output=True, text=True)
summary = result.stdout + f'PASS 70 independent item / 200 block declarations; 99 industrial + 7 native material declarations; {len(languages[0])} bilingual keys\n'
(OUT / 'result.txt').write_text(summary, encoding='utf-8')
print(summary, end='')
