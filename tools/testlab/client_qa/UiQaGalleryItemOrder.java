package audit;

import com.google.gson.*;
import java.util.*;

/** One bounded client-side initialization check. Never scans NEI's global item list or changes sorting config. */
final class UiQaGalleryItemOrder {
    final UiQaScript s;
    final Mui2ClientQa q;
    final Object catalog,view,registry,sorter;
    final Map<String,List<Object>> catalogues=new LinkedHashMap<String,List<Object>>();

    UiQaGalleryItemOrder(UiQaScript script)throws Exception {
        s=script;q=s.q;
        catalog=Mui2ClientQa.type("com.miaokatze.miaokatzecore.api.items.ItemOrderCatalog");
        view=Mui2ClientQa.call(catalog,"view");
        registry=Mui2ClientQa.field(Mui2ClientQa.type("net.minecraft.item.Item"),"itemRegistry|field_150901_e");
        sorter=Mui2ClientQa.type("codechicken.nei.ItemSorter");
    }

    void check()throws Exception {
        if(Boolean.TRUE.equals(s.variables.put("fiveModItemOrderChecked",true)))throw new AssertionError("Initialization catalogue check must execute exactly once");
        JsonArray chain=new JsonArray();List<?> active=(List<?>)Mui2ClientQa.field(sorter,"list");
        for(Object option:active){JsonObject entry=new JsonObject();entry.addProperty("name",String.valueOf(Mui2ClientQa.field(option,"name")));entry.addProperty("comparator",Mui2ClientQa.field(option,"comparator").getClass().getName());chain.add(entry);}
        JsonObject initialization=new JsonObject();initialization.addProperty("fiveModItemOrderInitialization",true);initialization.add("actualNeiComparatorChain",chain);initialization.addProperty("scope","One client step; full five owner catalogues, real creative output and ItemSorter; no global NEI item enumeration or configuration mutation");s.events.add(initialization);
        Object loader=Mui2ClientQa.call(Mui2ClientQa.type("cpw.mods.fml.common.Loader"),"instance");Map<?,?> mods=(Map<?,?>)Mui2ClientQa.call(loader,"getIndexedModList");JsonObject versions=new JsonObject();
        for(String id:new String[]{"miaokatzecore","gtsr","gtswn","gtit","gto","gtudk"}){Object mod=mods.get(id);if(mod==null)throw new AssertionError("Required candidate mod missing at runtime: "+id);JsonObject metadata=new JsonObject();metadata.addProperty("version",String.valueOf(Mui2ClientQa.call(mod,"getVersion")));metadata.addProperty("source",String.valueOf(Mui2ClientQa.call(mod,"getSource")));versions.add(id,metadata);}
        initialization.add("actualFmlCandidateVersions",versions);
        q.check("catalog.core-runtime-version","0.1.10".equals(String.valueOf(Mui2ClientQa.call(mods.get("miaokatzecore"),"getVersion"))),"Actual FML @Mod runtime version must be 0.1.10; metadata="+versions);
        q.check("catalog.nei-default-active-chain",active.size()>=3&&"nei.itemsort.minecraft".equals(String.valueOf(Mui2ClientQa.field(active.get(0),"name")))&&"nei.itemsort.mod".equals(String.valueOf(Mui2ClientQa.field(active.get(1),"name")))&&"nei.itemsort.id".equals(String.valueOf(Mui2ClientQa.field(active.get(2),"name"))),"Actual active default prefix is minecraft / mod / id; chain="+chain);
        List<Object> combined=new ArrayList<Object>();
        for(String owner:new String[]{"gtsr","gtswn","gtit","gto","gtudk"}){
            List<Object> expected=new ArrayList<Object>((List<?>)Mui2ClientQa.call(catalog,"orderedItems",owner));
            q.check("catalog.registered-full."+owner,!expected.isEmpty()&&expected.size()<=10000,"Registered complete metadata catalogue size="+expected.size());
            catalogues.put(owner,expected);combined.addAll(expected);
            Object tab=Mui2ClientQa.field(Mui2ClientQa.type("com.miaokatze."+owner+".register.CreativeTabManager"),"CREATIVE_TAB");
            List<Object> creative=new ArrayList<Object>();Mui2ClientQa.call(tab,"displayAllReleventItems|func_78018_a",creative);
            List<Object> sorted=new ArrayList<Object>(expected);Collections.shuffle(sorted,new Random(0x203L));sort(sorted);
            List<String> wanted=keys(expected),shown=keys(creative),nei=keys(sorted),creativeWanted=keys(creativeExpected(owner,expected));
            boolean entries=true;
            for(int i=0;i<expected.size();i++){Object entry=Mui2ClientQa.call(view,"entry",expected.get(i));entries&=entry!=null&&owner.equals(String.valueOf(Mui2ClientQa.call(entry,"owner")))&&q.number(Mui2ClientQa.call(entry,"rank"))==i;}
            boolean creativeOwned=true;for(Object stack:creative){Object entry=Mui2ClientQa.call(view,"entry",stack);creativeOwned&=entry!=null&&owner.equals(String.valueOf(Mui2ClientQa.call(entry,"owner")));}
            JsonObject event=new JsonObject();event.addProperty("ownerCatalogue",owner);event.addProperty("count",wanted.size());event.add("canonicalMetadataKeys",json(wanted));event.add("expectedCreativeMetadataKeys",json(creativeWanted));event.add("creativeMetadataKeys",json(shown));event.add("neiSortedMetadataKeys",json(nei));s.events.add(event);
            q.check("catalog.unique-item-metadata."+owner,new HashSet<String>(wanted).size()==wanted.size(),"Every registry item + metadata identity occurs exactly once");
            q.check("catalog.full-owner-ranks."+owner,entries,"Every complete catalogue variant has the exact owner and rank in ItemOrderCatalog.view().entry(stack)");
            q.check("catalog.actual-creative-variant-owners."+owner,creativeOwned,"Every actual creative variant belongs to this exact Core owner");
            q.check("catalog.actual-full-creative-order."+owner,creativeWanted.equals(shown),difference(creativeWanted,shown));
            q.check("catalog.actual-full-nei-order."+owner,wanted.equals(nei),difference(wanted,nei));
        }
        q.check("catalog.bounded-five-catalogue-total",combined.size()<=20000,"Only five catalogues are sorted; total="+combined.size());
        dimensionTail(catalogues.get("gtsr"));
        List<Object> foreign=foreignItems();combined.addAll(foreign);
        for(int run=0;run<2;run++){
            List<Object> mixed=new ArrayList<Object>(combined);Collections.shuffle(mixed,new Random(0x203100L+run));sort(mixed);
            for(Map.Entry<String,List<Object>> owner:catalogues.entrySet()){
                List<Object> projection=new ArrayList<Object>();
                for(Object stack:mixed){Object entry=Mui2ClientQa.call(view,"entry",stack);if(entry!=null&&owner.getKey().equals(String.valueOf(Mui2ClientQa.call(entry,"owner"))))projection.add(stack);}
                q.check("catalog.real-nei-mixed-projection."+owner.getKey()+"."+run,keys(owner.getValue()).equals(keys(projection)),"Full owner subsequence survives actual ItemSorter sorting mixed with all other owners, vanilla items and foreign GT metadata");
            }
            JsonObject event=new JsonObject();event.addProperty("actualNeiMixedSort",run);event.add("foreignMetadataKeys",json(keys(foreign)));event.add("sortedMetadataKeys",json(keys(mixed)));s.events.add(event);
        }
        anchorHidden();
    }

    List<Object> creativeExpected(String owner,List<Object> full)throws Exception {
        if(!owner.equals("gtsr"))return full;
        List<Object> raw=new ArrayList<Object>((List<?>)Mui2ClientQa.call(Mui2ClientQa.type("com.miaokatze.gtsr.register.CreativeTabManager"),"getRegisteredItems"));
        Set<String> seen=new HashSet<String>(keys(raw));Object itemList=Mui2ClientQa.type("com.miaokatze.gtsr.common.api.enums.GTSRItemList");
        for(String name:new String[]{"PortableLM12","Ammo762Pack","MimicAmmo762Pack","PortableT20","Ammo20Pack","MimicAmmo20Pack","PortableQLZ04","Ammo35Pack","MimicAmmo35Pack","PortableSingularity"}){
            Object entry=Mui2ClientQa.field(itemList,name);if(Boolean.TRUE.equals(Mui2ClientQa.call(entry,"hasBeenSet"))){Object stack=Mui2ClientQa.call(entry,"get",1);if(seen.add(key(stack)))raw.add(stack);}
        }
        Mui2ClientQa.call(catalog,"sort",owner,raw);
        return raw;
    }

    void sort(List<Object> stacks)throws Exception {
        // Calls NEI's public sorter, then verifies with that same live comparator so swallowed sort errors fail QA.
        Mui2ClientQa.call(sorter,"sort",stacks);
        Comparator<Object> actual=(Comparator<Object>)Mui2ClientQa.field(sorter,"instance");
        for(int i=1;i<stacks.size();i++)if(actual.compare(stacks.get(i-1),stacks.get(i))>0)throw new AssertionError("Actual ItemSorter output is not ordered at "+i);
    }

    List<Object> foreignItems()throws Exception {
        List<Object> result=new ArrayList<Object>();
        for(String name:new String[]{"minecraft:stone","minecraft:diamond","minecraft:iron_ingot"}){JsonObject spec=new JsonObject();spec.addProperty("id",name);spec.addProperty("count",1);result.add(s.makeStack(spec));}
        Object[] machines=(Object[])Mui2ClientQa.field(Mui2ClientQa.type("gregtech.api.GregTechAPI"),"METATILEENTITIES");int found=0;
        // A bounded direct registry lookup supplies real foreign metadata of the shared GT ItemMachines.
        for(int i=0;i<Math.min(4096,machines.length)&&found<2;i++)if(machines[i]!=null){Object stack=Mui2ClientQa.call(machines[i],"getStackForm",1L);if(Mui2ClientQa.call(view,"entry",stack)==null){result.add(stack);found++;}}
        q.check("catalog.foreign-shared-gt-metadata",found==2,"Two real registered foreign GT variants, looked up once in at most 4096 registry slots");
        return result;
    }

    void dimensionTail(List<Object> items)throws Exception {
        Set<String> explicit=new HashSet<String>();Object itemList=Mui2ClientQa.type("com.miaokatze.gtsr.common.api.enums.GTSRItemList");
        for(String name:new String[]{"SpacetimeCalibration","SpacetimeAnchorBeacon","AbyssalObsessionBucket"})explicit.add(key(Mui2ClientQa.call(Mui2ClientQa.field(itemList,name),"get",1)));
        List<String> generated=generatedDimensionKeys();explicit.addAll(generated);
        List<String> complete=keys(items);
        q.check("catalog.gtsr-generated-exact-final-segment",complete.size()>=generated.size()&&complete.subList(complete.size()-generated.size(),complete.size()).equals(generated),"All exact native GT material/prefix and FluidDisplay variants form the final catalogue segment; count="+generated.size());
        boolean seen=false,valid=true;int dimensions=0,ordinary=0;JsonArray tail=new JsonArray();
        for(Object stack:items){boolean dimension=isDimension(stack,explicit);if(dimension){seen=true;dimensions++;tail.add(new JsonPrimitive(key(stack)));}else{ordinary++;if(seen)valid=false;}}
        JsonObject event=new JsonObject();event.addProperty("gtsrDimensionTail",true);event.addProperty("ordinaryCount",ordinary);event.addProperty("dimensionCount",dimensions);event.add("dimensionMetadataKeys",tail);s.events.add(event);
        q.check("catalog.gtsr-dimension-complete-tail",valid&&dimensions>=explicit.size()&&ordinary>16,"Independent item/block class, explicit entry and registry-family classification; ordinary="+ordinary+", dimension="+dimensions+"; all dimension entries follow ordinary items");
    }

    List<String> generatedDimensionKeys()throws Exception {
        LinkedHashSet<String> expected=new LinkedHashSet<String>();
        Object air=Mui2ClientQa.type("com.miaokatze.gtsr.common.dimension.prosperity.air.GTSRProsperityAirMaterials");
        for(String name:new String[]{"WastesSigh","ThickGrease","MetalGrit","UmbralMire","SanzuResidualSteam","AbyssalObsession","WitheredBreath"}){
            Object material=Mui2ClientQa.field(air,name);if(material==null)throw new AssertionError("Air material was not initialized: "+name);
            generatedKey(expected,material,"cell");fluidDisplayKey(expected,Mui2ClientQa.call(material,"getGas",1L));fluidDisplayKey(expected,Mui2ClientQa.call(material,"getFluid",1L));
        }
        Object ledger=Mui2ClientQa.call(Mui2ClientQa.type("com.miaokatze.gtsr.common.dimension.prosperity.industrial.IndustrialRecipeLedger"),"get");
        Object industrial=Mui2ClientQa.type("com.miaokatze.gtsr.common.dimension.prosperity.industrial.ProsperityIndustrialMaterials");
        for(Object definition:(Iterable<?>)Mui2ClientQa.field(ledger,"materials")){
            Object material=Mui2ClientQa.call(industrial,"get",Mui2ClientQa.field(definition,"id"));
            if(!Mui2ClientQa.field(definition,"name").equals(Mui2ClientQa.field(material,"mName")))throw new AssertionError("Industrial material identity mismatch");
            String phase=String.valueOf(Mui2ClientQa.field(definition,"phase"));
            if(phase.equals("dust")){for(String prefix:new String[]{"dustTiny","dustSmall","dust"})generatedKey(expected,material,prefix);}
            else{generatedKey(expected,material,"cell");fluidDisplayKey(expected,Mui2ClientQa.call(material,phase.equals("gas")?"getGas":"getFluid",1L));}
        }
        List<String> actual=keys((List<?>)Mui2ClientQa.call(Mui2ClientQa.type("com.miaokatze.gtsr.register.GTSRItemOrderCatalog"),"generatedDimensionItems"));
        List<String> wanted=new ArrayList<String>(expected);
        JsonObject event=new JsonObject();event.addProperty("gtsrNativeGeneratedDimensionVariants",true);event.add("expectedMetadataKeys",json(wanted));event.add("providerMetadataKeys",json(actual));event.addProperty("validation","Explicit seven air materials + industrial ledger declarations; native MetaGeneratedItemX32 material/prefix/generation permission or registered fluid display roundtrip; no NEI-global scan");s.events.add(event);
        q.check("catalog.gtsr-exact-generated-material-variants",!wanted.isEmpty()&&wanted.equals(actual),difference(wanted,actual));
        return wanted;
    }

    void generatedKey(Set<String> keys,Object material,String prefixName)throws Exception {
        int id=q.number(Mui2ClientQa.field(material,"mMetaItemSubID"));Object[] generated=(Object[])Mui2ClientQa.field(Mui2ClientQa.type("gregtech.api.GregTechAPI"),"sGeneratedMaterials");
        if(id<0||id>=generated.length||generated[id]!=material)return;
        Object prefix=Mui2ClientQa.field(Mui2ClientQa.type("gregtech.api.enums.OrePrefixes"),prefixName);
        Object stack=Mui2ClientQa.call(Mui2ClientQa.type("gregtech.api.util.GTOreDictUnificator"),"get",prefix,material,1L);if(stack==null)return;
        Object item=Mui2ClientQa.call(stack,"getItem|func_77973_b");if(!Mui2ClientQa.type("gregtech.api.items.MetaGeneratedItemX32").isInstance(item))return;
        int metadata=q.number(Mui2ClientQa.call(stack,"getItemDamage|func_77960_j"));
        if(Mui2ClientQa.call(item,"getMaterial",metadata)==material&&Mui2ClientQa.call(item,"getOrePrefix",metadata)==prefix&&Boolean.TRUE.equals(Mui2ClientQa.call(item,"doesMaterialAllowGeneration",prefix,material)))keys.add(key(stack));
    }

    void fluidDisplayKey(Set<String> keys,Object fluidStack)throws Exception {
        if(fluidStack==null)return;Object fluid=Mui2ClientQa.call(fluidStack,"getFluid");
        if(Mui2ClientQa.call(Mui2ClientQa.type("net.minecraftforge.fluids.FluidRegistry"),"getFluid",Mui2ClientQa.call(fluid,"getName"))!=fluid)return;
        Object displayEntry=Mui2ClientQa.field(Mui2ClientQa.type("gregtech.api.enums.ItemList"),"Display_Fluid");
        if(!Boolean.TRUE.equals(Mui2ClientQa.call(displayEntry,"hasBeenSet")))return;Object displayItem=Mui2ClientQa.call(displayEntry,"getItem");if(displayItem==null)return;
        Object utility=Mui2ClientQa.type("gregtech.api.util.GTUtility"),display=Mui2ClientQa.call(utility,"getFluidDisplayStack",fluidStack,false);if(display==null)return;
        if(Mui2ClientQa.call(display,"getItem|func_77973_b")!=displayItem||q.number(Mui2ClientQa.call(display,"getItemDamage|func_77960_j"))!=q.number(Mui2ClientQa.call(fluid,"getID")))return;
        Object decoded=Mui2ClientQa.call(utility,"getFluidFromDisplayStack",display);if(decoded!=null&&Mui2ClientQa.call(decoded,"getFluid")==fluid)keys.add(key(display));
    }

    boolean isDimension(Object stack,Set<String> explicit)throws Exception {
        if(explicit.contains(key(stack)))return true;Object item=Mui2ClientQa.call(stack,"getItem|func_77973_b");
        if(item.getClass().getName().startsWith("com.miaokatze.gtsr.common.dimension."))return true;
        Object block=Mui2ClientQa.call(Mui2ClientQa.type("net.minecraft.block.Block"),"getBlockFromItem|func_149634_a",item);
        if(block!=null&&block.getClass().getName().startsWith("com.miaokatze.gtsr.common.dimension."))return true;
        String path=String.valueOf(Mui2ClientQa.call(registry,"getNameForObject|func_148750_c",item));path=path.substring(path.indexOf(':')+1).toLowerCase(Locale.ROOT);
        return path.startsWith("prosperity")||path.startsWith("shattered")||path.startsWith("ruins_")||path.startsWith("royal_")||path.startsWith("remaster_")||Arrays.asList("spacetimealtarcore","ruinmechanism","sealedchest","unsealedchest").contains(path);
    }

    void anchorHidden()throws Exception {
        Object block=Mui2ClientQa.field(Mui2ClientQa.type("com.miaokatze.gtsr.common.critical.CriticalRenderAnchors"),"BLOCK");
        Object item=Mui2ClientQa.call(Mui2ClientQa.type("net.minecraft.item.Item"),"getItemFromBlock|func_150898_a",block);
        Object stack=Mui2ClientQa.type("net.minecraft.item.ItemStack").getConstructor(Mui2ClientQa.type("net.minecraft.item.Item")).newInstance(item);
        q.check("catalog.anchor-no-creative-tab",Mui2ClientQa.call(item,"getCreativeTab|func_77640_w")==null,"Real render-anchor ItemBlock has no assigned creative tab");
        List<Object> subitems=new ArrayList<Object>();Object tab=Mui2ClientQa.field(Mui2ClientQa.type("com.miaokatze.gtsr.register.CreativeTabManager"),"CREATIVE_TAB");
        Mui2ClientQa.call(item,"getSubItems|func_150895_a",item,tab,subitems);
        Mui2ClientQa.call(item,"getSubItems|func_150895_a",item,null,subitems);
        q.check("catalog.anchor-no-native-subitems",subitems.isEmpty(),"Actual ItemBlock.getSubItems returned zero entries for owner tab and native null-tab enumeration");
        q.check("catalog.anchor-outside-owner-directory",Mui2ClientQa.call(view,"entry",stack)==null&&!keys(catalogues.get("gtsr")).contains(key(stack)),"Internal anchor is absent from the full creative catalogue and Core ownership index");
        Object info=Mui2ClientQa.type("codechicken.nei.api.ItemInfo"),hidden=Mui2ClientQa.field(info,"hiddenItems");
        q.check("catalog.anchor-nei-hidden",Boolean.TRUE.equals(Mui2ClientQa.call(info,"isHidden",stack))&&Boolean.TRUE.equals(Mui2ClientQa.call(hidden,"contains",stack)),"Actual NEI ItemInfo hidden filter and API hidden-item set both contain the internal anchor");
    }

    String key(Object stack)throws Exception {Object item=Mui2ClientQa.call(stack,"getItem|func_77973_b");return String.valueOf(Mui2ClientQa.call(registry,"getNameForObject|func_148750_c",item))+"@"+q.number(Mui2ClientQa.call(stack,"getItemDamage|func_77960_j"));}
    List<String> keys(List<?> stacks)throws Exception {List<String> keys=new ArrayList<String>();for(Object stack:stacks)keys.add(key(stack));return keys;}
    static JsonArray json(List<String> keys){JsonArray array=new JsonArray();for(String key:keys)array.add(new JsonPrimitive(key));return array;}
    static String difference(List<String> expected,List<String> actual){for(int i=0;i<Math.min(expected.size(),actual.size());i++)if(!expected.get(i).equals(actual.get(i)))return "First mismatch at "+i+": expected="+expected.get(i)+", actual="+actual.get(i)+"; sizes="+expected.size()+"/"+actual.size();return "Exact item+metadata sequence lengths="+expected.size()+"/"+actual.size();}
}
