import java.math.BigInteger;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.*;

import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraftforge.fluids.*;

import com.miaokatze.gtsr.api.recipe.*;
import com.miaokatze.gtsr.common.critical.*;
import com.miaokatze.gtsr.loader.recipes.CriticalMachineRecipes;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.GTRecipeConstants;

/** Actual GT205 frontend positions, fake entry indexing and native stellar capability gates. */
public class CriticalNEICatalogContract {
    public static void main(String[] args) throws Exception {
        Issue37RegistrationRegression.launch("CriticalNEICatalogRuntime", "5.09.54.205");
    }
}

class CriticalNEICatalogRuntime {
    static int checks;
    static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
    static Map<String,Long> totals(Collection<ItemStack> items) {
        Map<String,Long> result=new TreeMap<>();
        for(ItemStack stack:items){String key=net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem())+":"+stack.getItemDamage();
            result.merge(key,(long)stack.stackSize,Long::sum);}
        return result;
    }
    static void showcaseTemplate() {
        var frontend=CriticalRecipeMaps.construction.getFrontend();
        var inputs=new com.gtnewhorizons.modularui.api.forge.ItemStackHandler(18);
        var outputs=new com.gtnewhorizons.modularui.api.forge.ItemStackHandler(1);
        var special=new com.gtnewhorizons.modularui.api.forge.ItemStackHandler(1);
        var fluids=new com.gtnewhorizons.modularui.api.forge.ItemStackHandler(6);
        var empty=new com.gtnewhorizons.modularui.api.forge.ItemStackHandler(0);
        var context=new gregtech.nei.GTNEIDefaultHandler.NEITemplateContext(inputs,outputs,special,fluids,empty,
            ()->0f,()->null,new com.gtnewhorizons.modularui.api.math.Pos2d(-5,-11));
        var template=frontend.createNEITemplate(context).build();
        int slots=0;
        for(var child:template.getChildren())if(child instanceof com.gtnewhorizons.modularui.common.widget.SlotWidget slot){
            slots++;
            int x=slot.getPos().x,y=slot.getPos().y;
            check(!(x<166&&x+18>154&&y<174&&y+18>149),
                "native construction slots leave NEI favorite/overlay buttons clear");
            if(slot.getMcSlot().getItemHandler()==inputs)
                check(x==CriticalNEILayout.BUILD_X+slot.getMcSlot().getSlotIndex()%6*18 && y==CriticalNEILayout.BUILD_Y+slot.getMcSlot().getSlotIndex()/6*36,"GT205 six-item native row coordinates");
            else if(slot.getMcSlot().getItemHandler()==outputs)
                check(x==CriticalNEILayout.OUTPUT_X&&y==CriticalNEILayout.BUILD_OUTPUT_Y,"GT205 output slot exact card coordinates");
            else check(x==CriticalNEILayout.BUILD_FLUID_X+slot.getMcSlot().getSlotIndex()%2*18 && y==CriticalNEILayout.BUILD_Y+slot.getMcSlot().getSlotIndex()/2*36,"GT205 two-fluid native row coordinates");
        }
        check(CriticalNEILayout.arrowCenter(false)==53 && CriticalNEILayout.arrowCenter(true)==73,
            "arrows share the exact item/fluid slot centre");
        check(slots==25,"native construction template has three six-item/two-fluid rows plus controller");
        System.out.println("PASS native construction template "+checks+" checks; GT205/MUI1");
    }
    static void runtimeTemplatesAndUniqueness() {
        Fluid layoutFluid=new Fluid("nei_layout_ports");FluidRegistry.registerFluid(layoutFluid);
        List<RecipeMap<?>> maps=new ArrayList<>();
        for(CriticalMachineKind kind:CriticalMachineKind.releasedValues()) maps.add(CriticalRecipeMaps.forKind(kind));
        maps.add(CriticalRecipeMaps.stellarIrradiation);maps.add(CriticalRecipeMaps.stellarLifecycle);
        for(RecipeMap<?> map:maps) {
            var frontend=map.getFrontend(); var ui=frontend.getUIProperties();
            var items=new com.gtnewhorizons.modularui.api.forge.ItemStackHandler(ui.maxItemInputs);
            var outputs=new com.gtnewhorizons.modularui.api.forge.ItemStackHandler(ui.maxItemOutputs);
            var fluidInputs=new com.gtnewhorizons.modularui.api.forge.ItemStackHandler(ui.maxFluidInputs);
            var fluidOutputs=new com.gtnewhorizons.modularui.api.forge.ItemStackHandler(ui.maxFluidOutputs);
            var special=new com.gtnewhorizons.modularui.api.forge.ItemStackHandler(1);
            var context=new gregtech.nei.GTNEIDefaultHandler.NEITemplateContext(items,outputs,special,fluidInputs,fluidOutputs,
                ()->0f,()->null,new com.gtnewhorizons.modularui.api.math.Pos2d(-5,-11));
            var template=frontend.createNEITemplate(context).build();
            for(var child:template.getChildren()) if(child instanceof com.gtnewhorizons.modularui.common.widget.SlotWidget slot) {
                var inventory=slot.getMcSlot().getItemHandler(); int x=slot.getPos().x,y=slot.getPos().y;
                boolean input=inventory==items||inventory==fluidInputs;
                boolean fluid=inventory==fluidInputs||inventory==fluidOutputs;
                check(input?x>=8&&x<=48:x==CriticalNEILayout.OUTPUT_X,"runtime native slot and label column " + map.unlocalizedName);
                check(y==(fluid?CriticalNEILayout.FLUID_Y:CriticalNEILayout.ITEM_Y),"runtime native row aligns arrow centre " + map.unlocalizedName);
                check(y+9==CriticalNEILayout.arrowCenter(fluid),"actual native slot centre equals shared arrow centre");
            }
            CriticalMachineKind owner=CriticalMachineKind.SUN;
            for(CriticalMachineKind kind:CriticalMachineKind.releasedValues())
                if(CriticalRecipeMaps.forKind(kind)==map)owner=kind;
            if(map==CriticalRecipeMaps.stellarIrradiation)owner=CriticalMachineKind.SOLAR;
            String operation=map==CriticalRecipeMaps.stellarIrradiation?"solar_dust"
                :map==CriticalRecipeMaps.stellarLifecycle?"stellar_sustain"
                :owner==CriticalMachineKind.SOLAR?"solar_water":owner.key;
            GTRecipe geometryRecipe=GTRecipeBuilder.builder().fluidInputs(new FluidStack(layoutFluid,1))
                .duration(1).eut(0).build().orElseThrow();
            CriticalRecipeMaps.addDisplayRecipe(owner,geometryRecipe,operation,BigInteger.ZERO);
            var info=CriticalRecipeMaps.displayInfo(geometryRecipe);
            check(info.recipeMap==map,"layout metadata retains its actual native map");
            for(boolean fluid:List.of(false,true)) {
                var handler=fluid?fluidInputs:items;
                int right=-1;
                for(var child:template.getChildren())if(child instanceof com.gtnewhorizons.modularui.common.widget.SlotWidget slot
                    &&slot.getMcSlot().getItemHandler()==handler)right=Math.max(right,slot.getPos().x+slot.getSize().width);
                if(right<0)continue;
                var flow=CriticalNEILayout.flow(info,fluid);
                check(flow.inputRight==right,"diagram starts from actual complete input group");
                check(Math.abs((flow.centerX-right)-(CriticalNEILayout.OUTPUT_X-flow.centerX))<=1,
                    "diagram horizontally centred between native ports: "+map.unlocalizedName);
                check(flow.diagramWidth<=flow.gapWidth-12,"process diagram leaves native port clearance");
            }
        }
        Fluid fluid=new Fluid("nei_unique");FluidRegistry.registerFluid(fluid);
        for(CriticalMachineKind kind:CriticalMachineKind.releasedValues()) {
            var map=CriticalRecipeMaps.forKind(kind);int count=map.getAllRecipes().size();
            GTRecipe recipe=GTRecipeBuilder.builder().fluidInputs(new FluidStack(fluid,1)).duration(400).eut(0).build().orElseThrow();
            CriticalRecipeMaps.addDisplayRecipe(kind,recipe,kind==CriticalMachineKind.SOLAR?"solar_water":kind.key,BigInteger.ONE);
            check(map.getAllRecipes().size()==count+1,"one real entry without guide duplicates " + kind);
            check(CriticalRecipeMaps.displayInfo(recipe).pages==1,"no explanatory pages " + kind);
        }
        System.out.println("PASS native runtime templates and unique entries; total " + checks + " checks");
    }
    static void fullConstruction() throws Exception {
        // Fixture identities only: actual geometry, carrier selection, frozen bill, pagination and GT recipe indices.
        GTValues.RA=(gregtech.api.interfaces.internal.IGTRecipeAdder)java.lang.reflect.Proxy.newProxyInstance(
            CriticalNEICatalogRuntime.class.getClassLoader(),new Class<?>[]{gregtech.api.interfaces.internal.IGTRecipeAdder.class},
            (p,m,a)->m.getName().equals("stdBuilder")?GTRecipeBuilder.builder():null);
        Issue37Runtime.set(gregtech.GTMod.proxy, gregtech.common.GTProxy.class, "mCTMBlockCache",new gregtech.api.util.GTBlockMap<>());
        CriticalMaterials.register();
        int id=1000;
        for(String name:List.of("Electric_Motor_UHV","Electric_Pump_UHV","Field_Generator_UHV","Emitter_UHV",
            "Sensor_UHV","Electric_Piston_UHV","Gravistar","Hull_MAX"))
            ItemList.valueOf(name).set(new ItemStack(Items.feather,1,id++));
        for(var item:List.of(com.miaokatze.gtsr.common.api.enums.GTSRItemList.CriticalSolar,
            com.miaokatze.gtsr.common.api.enums.GTSRItemList.CriticalSun,
            com.miaokatze.gtsr.common.api.enums.GTSRItemList.CriticalTurbine,
            com.miaokatze.gtsr.common.api.enums.GTSRItemList.CriticalBattery))item.set(new ItemStack(Items.feather,1,id++));
        for(OrePrefixes prefix:List.of(OrePrefixes.circuit,OrePrefixes.pipeMedium,OrePrefixes.plate,OrePrefixes.gearGt,
            OrePrefixes.stickLong,OrePrefixes.rotor,OrePrefixes.wireFine))
            for(Materials material:List.of(Materials.UHV,Materials.Neutronium,Materials.Europium,Materials.NaquadahAlloy,
                Materials.Platinum,Materials.Iridium,Materials.Osmium,Materials.CosmicNeutronium,Materials.TungstenSteel))
                gregtech.api.util.GTOreDictUnificator.getName2StackMap().put(prefix.get(material).toString(),new ItemStack(Items.feather,1,id++));
        Field ic2=gregtech.api.util.GTModHandler.class.getDeclaredField("sIC2ItemMap");ic2.setAccessible(true);
        ((Map<String,ItemStack>)ic2.get(null)).put("carbonPlate",new ItemStack(Items.feather,1,id++));
        Field concrete=CriticalFoundationMaterials.class.getDeclaredField("concrete");concrete.setAccessible(true);
        Fluid foundation=new Fluid("nei_foundation");FluidRegistry.registerFluid(foundation);concrete.set(null,foundation);
        for(Materials material:List.of(Materials.Silver,Materials.Bismuth,Materials.Radon)) {
            Fluid plasma=new Fluid("nei_plasma_"+material.name());FluidRegistry.registerFluid(plasma);material.mPlasma=plasma;
        }
        Fluid water7=new Fluid("nei_water7");FluidRegistry.registerFluid(water7);Materials.Grade7PurifiedWater.mFluid=water7;
        FluidRegistry.registerFluid(new Fluid("ic2superheatedsteam"));
        Method register=CriticalMachineRecipes.class.getDeclaredMethod("registerConstruction");register.setAccessible(true);register.invoke(null);
        for(CriticalMachineKind kind:CriticalMachineKind.releasedValues()) {
            CriticalConfiguration config=new CriticalConfiguration(CriticalTier.T3,kind,8,0,0);
            CriticalLayoutDefinition definition=CriticalLayoutDefinition.select(2,31,false,CriticalTier.T3,kind);
            List<CriticalGeometry.Voxel> paid=new ArrayList<>();
            for(var voxel:CriticalCompiledLayout.get(definition,config).voxels())
                if(!CriticalLayoutDefinition.isLoom(voxel))paid.add(voxel);
            CriticalConstructionEconomy bill=CriticalConstructionEconomy.freeze(config,paid,definition.cacheKey());
            List<ItemStack> inputs=new ArrayList<>();Set<Integer> groups=new HashSet<>();
            for(GTRecipe recipe:CriticalRecipeMaps.construction.getAllRecipes()) {
                var info=CriticalRecipeMaps.displayInfo(recipe);if(info.kind!=kind)continue;
                if(recipe.mOutputs[0].getItem()==Items.diamond)continue;
                int group=info.constructionStage();
                check(recipe.mFakeRecipe && groups.add(group),"exactly one native entry per major construction phase");
                check(recipe.mOutputs.length==1 && recipe.mOutputs[0].stackSize==1,"one clickable whole-machine marker");
                var displayItems=CriticalRecipeMaps.constructionItems(recipe);
                var displayFluids=CriticalRecipeMaps.constructionFluids(recipe);
                check(displayItems.length==18&&displayFluids.length==6,"three fully indexed native rows");
                List<ItemStack> shown=new ArrayList<>();
                for(int row=0;row<3;row++) {
                    var expected=bill.itemsForDisplayRow(group,row,paid);
                    var actual=new ArrayList<ItemStack>();
                    for(int i=row*6;i<(row+1)*6;i++)if(displayItems[i]!=null)actual.add(displayItems[i]);
                    check(totals(actual).equals(totals(Arrays.asList(expected))),"row bill exactly matches runtime paid construction API");
                    shown.addAll(actual);
                    var expectedFluids=bill.fluidsForDisplayRow(group,row,paid);
                    for(int i=0;i<expectedFluids.length;i++) {
                        FluidStack actualFluid=displayFluids[row*2+i];
                        check(actualFluid!=null&&actualFluid.isFluidEqual(expectedFluids[i])&&actualFluid.amount==expectedFluids[i].amount,
                            "native row fluid identity and exact volume");
                    }
                }
                check(totals(shown).equals(totals(Arrays.asList(recipe.mInputs))),"every clickable item is in the real indexed recipe bill");
                if(group!=0)inputs.addAll(Arrays.asList(recipe.mInputs));
            }
            check(groups.equals(Set.of(0,1,2)),"base, body, equipment: three pages per released machine");
            check(totals(inputs).equals(totals(Arrays.asList(bill.demand()))),"all body/equipment rows exactly equal frozen whole-job demand "+kind);
        }
    }
    static void stellarLifecycle() throws Exception {
        Fluid helium3=new Fluid("nei_helium3");FluidRegistry.registerFluid(helium3);Materials.Helium3.mGas=helium3;
        Fluid plasma=new Fluid("nei_helium_plasma");FluidRegistry.registerFluid(plasma);Materials.Helium.mPlasma=plasma;
        Method register=CriticalMachineRecipes.class.getDeclaredMethod("registerStellarLifecycle");
        register.setAccessible(true);register.invoke(null);
        Set<String> phases=new HashSet<>();
        for(GTRecipe recipe:CriticalRecipeMaps.stellarLifecycle.getAllRecipes()) {
            var info=CriticalRecipeMaps.displayInfo(recipe);if(!info.operation.startsWith("stellar_"))continue;
            check(phases.add(info.operation),"one entry per stellar lifecycle phase");
            check(recipe.mDuration==CriticalStellarPolicy.DURATION_TICKS,"stellar lifecycle phase is exactly twenty seconds");
            long expected=info.operation.equals("stellar_ignition")?CriticalStellarPolicy.IGNITION_HELIUM3_L
                :CriticalStellarPolicy.DURATION_TICKS*CriticalStellarPolicy.SUSTAIN_HELIUM3_L_PER_TICK;
            check(recipe.mFluidInputs[0].amount==expected&&recipe.mFluidOutputs[0].amount==expected/2,
                "stellar phase fluid bill matches paid runtime amounts");
        }
        check(phases.equals(Set.of("stellar_ignition","stellar_sustain")),"ignition and sustain both indexed");
    }
    public static void run(String version) throws Exception {
        Issue37Runtime.bootstrap(version);
        String expectedJar=System.getProperty("critical.nei.expectedJar");
        if(expectedJar!=null){
            String expected=new java.io.File(expectedJar).toURI().toURL().toExternalForm();
            for(Class<?> type:List.of(CriticalRecipeMaps.class,CriticalRecipeFrontend.class,CriticalMachineRecipes.class,
                com.miaokatze.gtsr.client.nei.CriticalNEIShowcase.class,CriticalConstructionEconomy.class)){
                String source=type.getProtectionDomain().getCodeSource().getLocation().toExternalForm();
                if(!source.contains(expected))throw new AssertionError("Wrong product CodeSource "+type+": "+source);
                System.out.println("CANDIDATE_SOURCE "+type.getSimpleName()+" "+source);
            }
        }
        Issue37Runtime.set(null, cpw.mods.fml.relauncher.FMLRelaunchLog.class, "side", cpw.mods.fml.relauncher.Side.SERVER);
        if(Boolean.getBoolean("critical.nei.templateOnly")){showcaseTemplate();runtimeTemplatesAndUniqueness();return;}
        check(CriticalRecipeMaps.forKind(null) == CriticalRecipeMaps.construction, "loom construction catalyst");
        for (CriticalMachineKind kind : CriticalMachineKind.values())
            check((CriticalRecipeMaps.forKind(kind) != null) == kind.isReleased(), "released-only " + kind);
        var frontend = CriticalRecipeMaps.construction.getFrontend();
        var positions = frontend.getItemInputPositions(CriticalRecipeMaps.CONSTRUCTION_PAGE_SIZE);
        Set<String> unique = new HashSet<>();
        for (var p : positions) {
            int x=p.x-5,y=p.y-11;
            check(x >= CriticalNEILayout.BUILD_X && x+18 <= CriticalNEILayout.BUILD_X+108 && y >=CriticalNEILayout.BUILD_Y && y+18 <=CriticalNEILayout.BUILD_Y+90, "construction grid bounds");
            check(unique.add(x+":"+y), "unique native hit box");
        }
        check(frontend.getItemOutputPositions(1).get(0).x-5 == CriticalNEILayout.OUTPUT_X, "separate clickable product");
        Fluid fluid = new Fluid("nei_contract"); FluidRegistry.registerFluid(fluid);
        for (CriticalMachineKind kind : CriticalMachineKind.releasedValues()) {
            GTRecipe recipe = GTRecipeBuilder.builder().fluidInputs(new FluidStack(fluid, 1))
                .duration(31).eut(480).build().orElseThrow();
            String op = kind == CriticalMachineKind.SOLAR ? "solar_water" : kind.key;
            int before=CriticalRecipeMaps.forKind(kind).getAllRecipes().size();
            CriticalRecipeMaps.addDisplayRecipe(kind, recipe, op, BigInteger.valueOf(500), 2);
            check(CriticalRecipeMaps.forKind(kind).getAllRecipes().size()==before+1,"one catalogue entry per real recipe");
            check(recipe.mFakeRecipe && !recipe.mHidden && recipe.mEUt==0, "fake zero-filter entry " + kind);
            check(recipe.mDuration==31, "native runtime timing preserved " + kind);
            check(CriticalRecipeMaps.displayInfo(recipe).kind == kind, "index kind " + kind);
            check(CriticalRecipeMaps.forKind(kind).getAllRecipes().contains(recipe), "indexed catalog " + kind);
        }
        GTRecipe build=GTRecipeBuilder.builder().itemInputs(new ItemStack(Items.iron_ingot, 4))
            .itemOutputs(new ItemStack(Items.diamond, 1)).duration(400).eut(0).build().orElseThrow();
        CriticalRecipeMaps.addConstruction(build, CriticalMachineKind.SUN, 2, new ItemStack[][] { build.mInputs, {}, {} },
            new FluidStack[][] { {}, {}, {} });
        check(build.mFakeRecipe && CriticalRecipeMaps.displayInfo(build).pages==1, "single semantic world product marker");
        check(CriticalRecipeMaps.construction.getAllRecipes().contains(build), "construction R/U indexed");
        Method filter=CriticalMachineRecipes.class.getDeclaredMethod("supportedFusion",GTRecipe.class);
        filter.setAccessible(true);
        GTRecipe fusion=GTRecipeBuilder.builder().fluidInputs(new FluidStack(fluid, 10))
            .fluidOutputs(new FluidStack(fluid, 1)).duration(33).eut(100)
            .metadata(GTRecipeConstants.FUSION_THRESHOLD,CriticalStellarPolicy.MKV_STARTUP).build().orElseThrow();
        check((Boolean)filter.invoke(null,fusion), "MkV boundary accepted");
        check(CriticalStellarPolicy.duration(fusion.mDuration)==400,"all native fusion durations become twenty seconds");
        fusion.mEUt=(int)GTValues.V[10]+1;
        check(!(Boolean)filter.invoke(null,fusion), "voltage overcap excluded");
        fusion.mEUt=100;fusion.mFakeRecipe=true;
        check(!(Boolean)filter.invoke(null,fusion), "native fake recipe excluded");
        fusion.mFakeRecipe=false;fusion.mDuration=0;
        check(!(Boolean)filter.invoke(null,fusion), "zero duration excluded");
        showcaseTemplate();
        fullConstruction();
        stellarLifecycle();
        System.out.println("PASS NEI catalog " + checks + " checks; "
            + (expectedJar == null ? "source overlay" : "candidate jar-only") + " GT205; no live client");
    }
}
