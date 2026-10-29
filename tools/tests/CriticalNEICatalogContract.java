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
        for(ItemStack stack:items){if(stack==null||stack.stackSize==0)continue;
            String key=net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem())+":"+stack.getItemDamage();
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
            if(map==CriticalRecipeMaps.forKind(CriticalMachineKind.BATTERY))
                check(ui.maxItemInputs==1&&ui.maxFluidInputs==9,"native chemical template reserves selector and nine fluid slots");
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
                boolean chemicalMap=map==CriticalRecipeMaps.forKind(CriticalMachineKind.BATTERY);
                boolean chemical=chemicalMap&&inventory==fluidInputs;
                boolean circuit=chemicalMap&&inventory==items;
                check(circuit?x==CriticalNEILayout.CHEMICAL_CIRCUIT_X:
                    input?x>=8&&x<=48:x==CriticalNEILayout.OUTPUT_X,"runtime native slot and label column " + map.unlocalizedName);
                int index=slot.getMcSlot().getSlotIndex();
                int expectedY=chemical?CriticalNEILayout.CHEMICAL_FLUID_Y+index/3*CriticalNEILayout.PITCH
                    :circuit?CriticalNEILayout.CHEMICAL_CIRCUIT_Y:fluid?CriticalNEILayout.FLUID_Y:CriticalNEILayout.ITEM_Y;
                check(y==expectedY,"runtime native row matches its real input slot " + map.unlocalizedName);
                if(chemical)check(x==CriticalNEILayout.INPUT_X+index%3*CriticalNEILayout.PITCH,
                    "nine chemical native slots form three rows of three");
                else if(!circuit)check(y+9==CriticalNEILayout.arrowCenter(fluid),"actual native slot centre equals shared arrow centre");
                if(chemical)check(y+18<=CriticalNEILayout.CHEMICAL_DIVIDER_Y,"native chemical grid clears result text");
                if(circuit)check(x>=CriticalNEILayout.INPUT_X+2*CriticalNEILayout.PITCH+18+4
                    &&x+18<99,"native selector clears fluid slots and efficiency bar");
                check(!(x<166&&x+18>154&&y<174&&y+18>149),"native runtime slots leave favorite/overlay buttons clear");
                if(chemicalMap)for(java.awt.Rectangle configured:ui.neiTransferRect) {
                    java.awt.Rectangle nativeRect=new java.awt.Rectangle(configured);nativeRect.translate(-5,-11);
                    check(!nativeRect.intersects(new java.awt.Rectangle(x,y,18,18)),
                        "native chemical transfer rect never masks a clickable circuit/fluid slot");
                }
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
                if(owner==CriticalMachineKind.BATTERY&&fluid) {
                    int left=flow.centerX-CriticalNEILayout.CHEMICAL_BAR_WIDTH/2;
                    check(left>flow.inputRight&&left+CriticalNEILayout.CHEMICAL_BAR_WIDTH<flow.outputLeft,
                        "single chemical vertical bar clears native fluid query slots");
                    check(CriticalNEILayout.CHEMICAL_BAR_Y>=32&&CriticalNEILayout.CHEMICAL_BAR_Y
                        +CriticalNEILayout.CHEMICAL_BAR_HEIGHT<CriticalNEILayout.CHEMICAL_DIVIDER_Y,
                        "chemical full bar height remains inside process panel");
                }
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
            com.miaokatze.gtsr.common.api.enums.GTSRItemList.CriticalBattery,
            com.miaokatze.gtsr.common.api.enums.GTSRItemList.CriticalSteamEntangledSingularity))item.set(new ItemStack(Items.feather,1,id++));
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
        Fluid water8=new Fluid("nei_water8");FluidRegistry.registerFluid(water8);Materials.Grade8PurifiedWater.mFluid=water8;
        for(Materials material:List.of(Materials.SodiumPotassium,Materials.Lubricant,Materials.SulfuricAcid)) {
            Fluid fluid=new Fluid("nei_process_fluid_"+material.name());FluidRegistry.registerFluid(fluid);material.mFluid=fluid;
        }
        for(Materials material:List.of(Materials.Helium,Materials.Argon,Materials.Nitrogen)) {
            Fluid fluid=new Fluid("nei_process_gas_"+material.name());FluidRegistry.registerFluid(fluid);material.mGas=fluid;
        }
        for(Materials material:List.of(Materials.SolderingAlloy,Materials.Platinum,Materials.Naquadria)) {
            Fluid fluid=new Fluid("nei_process_molten_"+material.name());FluidRegistry.registerFluid(fluid);material.mStandardMoltenFluid=fluid;
        }
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
                if(group==0){
                    check(displayFluids[0].amount==50_000&&displayItems[0].stackSize==32
                        &&displayItems[1].stackSize==2&&displayItems[2].stackSize==400,
                        "foundation NEI shows one current 4x4 cell's exact bill");
                    check(CriticalFoundationChunk.CELL_DURATION==40,"foundation NEI uses two seconds per cell");
                    var optional=displayItems[CriticalNEILayout.CURING_EXAMPLE_INDEX];
                    check(optional!=null&&optional.stackSize==0&&com.miaokatze.gtsr.common.api.enums.GTSRItemList
                        .CriticalSteamEntangledSingularity.isStackEqual(optional),"zero-size actual singularity at curing row");
                    check(Arrays.stream(recipe.mInputs).noneMatch(s->com.miaokatze.gtsr.common.api.enums.GTSRItemList
                        .CriticalSteamEntangledSingularity.isStackEqual(s)),"optional curing marker never enters paid recipe bill");
                }
                List<ItemStack> shown=new ArrayList<>();
                for(int row=0;row<3;row++) {
                    var profile=group==0?null:bill.displayRowProfile(group,row,paid);
                    int steps=profile==null?1:profile.steps;
                    check(info.constructionSteps(row)==steps,"NEI step count equals frozen scheduler profile");
                    var expected=profile==null?bill.itemsForDisplayRow(group,row,paid):profile.perStepItems();
                    var actual=new ArrayList<ItemStack>();
                    for(int i=row*6;i<(row+1)*6;i++)if(displayItems[i]!=null)actual.add(displayItems[i]);
                    check(totals(actual).equals(totals(Arrays.asList(expected))),"row bill exactly matches runtime paid construction API");
                    shown.addAll(actual);
                    var expectedFluids=profile==null?bill.fluidsForDisplayRow(group,row,paid):profile.perStepFluids();
                    for(int i=0;i<expectedFluids.length;i++) {
                        FluidStack actualFluid=displayFluids[row*2+i];
                        check(actualFluid!=null&&actualFluid.isFluidEqual(expectedFluids[i])&&actualFluid.amount==expectedFluids[i].amount,
                            "native row fluid identity and exact volume");
                    }
                    if(group!=0)for(ItemStack stack:actual)if(stack.stackSize>0) {
                        ItemStack total=stack.copy();total.stackSize*=steps;inputs.add(total);
                    }
                }
                check(totals(shown).equals(totals(Arrays.asList(recipe.mInputs))),"every clickable item is in the real indexed recipe bill");
            }
            check(groups.equals(Set.of(0,1,2)),"base, body, equipment: three pages per released machine");
            check(totals(inputs).equals(totals(Arrays.asList(bill.demand()))),"all body/equipment rows exactly equal frozen whole-job demand "+kind);
        }
        curingTooltipContract();
    }
    static void manufacturingControllers() throws Exception {
        int id=8000;
        for(String name:List.of("Robot_Arm_UHV","Conveyor_Module_UHV","Electric_Pump_UV","Electric_Motor_UV",
            "Field_Generator_UV","Emitter_UV","Tool_DataStick","Circuit_Integrated"))
            ItemList.valueOf(name).set(new ItemStack(Items.feather,1,id++));
        com.miaokatze.gtsr.common.api.enums.GTSRItemList.CriticalLoom.set(new ItemStack(Items.feather,1,id++));
        tectech.thing.CustomItemList.UnusedStuff.set(new ItemStack(Items.feather,1,id++));
        for(OrePrefixes prefix:List.of(OrePrefixes.circuit,OrePrefixes.plate,OrePrefixes.stickLong,
            OrePrefixes.cableGt04,OrePrefixes.rotor,OrePrefixes.gearGt,OrePrefixes.pipeMedium,OrePrefixes.foil,
            OrePrefixes.wireFine,OrePrefixes.wireGt01,OrePrefixes.gearGtSmall,OrePrefixes.dust))
            for(Materials material:List.of(Materials.UHV,Materials.UV,Materials.ZPM,Materials.Neutronium,
                Materials.Europium,Materials.NaquadahAlloy,Materials.Platinum,Materials.CosmicNeutronium,
                Materials.TungstenSteel,Materials.TungstenCarbide,Materials.Graphite,Materials.SiliconDioxide,
                Materials.SuperconductorUV,Materials.Polytetrafluoroethylene,Materials.Glass,Materials.Bedrockium)) {
                String name=prefix.get(material).toString();
                if(!gregtech.api.util.GTOreDictUnificator.getName2StackMap().containsKey(name))
                    gregtech.api.util.GTOreDictUnificator.getName2StackMap().put(name,new ItemStack(Items.feather,1,id++));
            }
        for(Materials material:List.of(Materials.UHV,Materials.UV,Materials.ZPM))
            net.minecraftforge.oredict.OreDictionary.registerOre(OrePrefixes.circuit.get(material).toString(),
                gregtech.api.util.GTOreDictUnificator.get(OrePrefixes.circuit,material,1));
        gtPlusPlus.core.material.MaterialsAlloy.INDALLOY_140.setFluid(solarFluid("nei_controller_indalloy"));
        Materials.Polybenzimidazole.mStandardMoltenFluid=solarFluid("nei_controller_pbi");
        bartworks.system.material.WerkstoffLoader.fluids.put(bartworks.system.material.WerkstoffLoader.LiquidHelium,
            solarFluid("nei_controller_helium"));
        List<GTRecipe.RecipeAssemblyLine> before=new ArrayList<>(GTRecipe.RecipeAssemblyLine.sAssemblylineRecipes);
        Class<?> loader=Class.forName("com.miaokatze.gtsr.loader.recipes.CriticalManufacturingRecipes");
        Method register=loader.getDeclaredMethod("register");register.setAccessible(true);register.invoke(null);
        Set<com.miaokatze.gtsr.common.api.enums.GTSRItemList> controllers=EnumSet.of(
            com.miaokatze.gtsr.common.api.enums.GTSRItemList.CriticalLoom,
            com.miaokatze.gtsr.common.api.enums.GTSRItemList.CriticalSolar,
            com.miaokatze.gtsr.common.api.enums.GTSRItemList.CriticalTurbine,
            com.miaokatze.gtsr.common.api.enums.GTSRItemList.CriticalBattery,
            com.miaokatze.gtsr.common.api.enums.GTSRItemList.CriticalSun);
        Set<com.miaokatze.gtsr.common.api.enums.GTSRItemList> seen=EnumSet.noneOf(
            com.miaokatze.gtsr.common.api.enums.GTSRItemList.class);
        for(var recipe:GTRecipe.RecipeAssemblyLine.sAssemblylineRecipes) {
            if(before.contains(recipe))continue;
            var controller=controllers.stream().filter(item->item.isStackEqual(recipe.mOutput)).findFirst().orElseThrow();
            check(seen.add(controller),"one actual manufacturing recipe per critical controller");
            long singularities=0;int singularitySlots=0;
            for(ItemStack input:recipe.mInputs) {
                check(input!=null,"manufacturing controller keeps every material slot resolved");
                if(com.miaokatze.gtsr.common.api.enums.GTSRItemList.CriticalSteamEntangledSingularity.isStackEqual(input)) {
                    singularities+=input.stackSize;singularitySlots++;
                }
            }
            check(recipe.mInputs.length==16&&recipe.mFluidInputs.length==4,
                "controller keeps sixteen actual item inputs and four original process fluids");
            check(singularities==64&&singularitySlots==1,
                "each of all five controllers consumes exactly one slot of 64 singularities");
        }
        check(seen.equals(controllers),"manufacturing includes the loom and all four released main machines");
    }

    static List<String> curingTooltip(ItemStack item, boolean construction, boolean input) throws Exception {
        var stack=new gregtech.nei.GTNEIDefaultHandler.FixedPositionedStack(null,item,true,13,131,10000,false,input);
        check(stack.item.stackSize==item.stackSize,"native FixedPositionedStack retains zero-count marker");
        Method hook=CriticalRecipeFrontend.class.getDeclaredMethod("handleNEIItemInputTooltip",List.class,
            gregtech.nei.GTNEIDefaultHandler.FixedPositionedStack.class);hook.setAccessible(true);
        var frontend=(construction?CriticalRecipeMaps.construction:CriticalRecipeMaps.forKind(CriticalMachineKind.SUN)).getFrontend();
        @SuppressWarnings("unchecked") List<String> tip=(List<String>)hook.invoke(frontend,new ArrayList<String>(),stack);
        return tip;
    }
    static void curingTooltipContract() throws Exception {
        var marker=com.miaokatze.gtsr.common.api.enums.GTSRItemList.CriticalSteamEntangledSingularity.get(1);
        marker.stackSize=0;
        List<String> tip=curingTooltip(marker,true,true);
        List<String> expected=new ArrayList<>();
        for(String key:List.of("title","optional","interval","reduction","scope"))
            expected.add(CriticalRecipeMaps.text("showcase.curing."+key));
        check(tip.equals(expected),"native input tooltip hook emits exactly the five curing facts");
        check(curingTooltip(marker,false,true).isEmpty(),"runtime maps do not receive curing tooltip");
        check(curingTooltip(marker,true,false).isEmpty(),"output stacks do not receive curing input tooltip");
        var consumed=marker.copy();consumed.stackSize=1;
        check(curingTooltip(consumed,true,true).isEmpty(),"consumed singularity is not marked optional");
        var other=new ItemStack(Items.diamond,0);
        check(curingTooltip(other,true,true).isEmpty(),"unrelated zero-count input has no curing tooltip");
        check(CriticalFoundationChunk.curingReduction(1000,16)==100,"16 singularities remove exactly 10 percent");
        check(CriticalFoundationChunk.curingReduction(1000,1)==11,"single-singularity cure reduction rounds down");
        check(CriticalFoundationChunk.curingReduction(1000,0)==0,"zero singularities grant no cure reduction");
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
    static Fluid solarFluid(String name) {
        Fluid fluid=FluidRegistry.getFluid(name);
        if(fluid==null){fluid=new Fluid(name);FluidRegistry.registerFluid(fluid);}
        return fluid;
    }
    static void solarCatalogue() throws Exception {
        // Fluid identities only; invoke the production loader and compare all paid sample quantities.
        CriticalPlasmaFixture.seed();
        solarFluid("ic2distilledwater");
        Materials.Steam.mGas=solarFluid("steam");
        Materials.DenseSteam.mGas=solarFluid("densesteam");
        Materials.DenseSuperheatedSteam.mGas=solarFluid("densesuperheatedsteam");
        Materials.DenseSupercriticalSteam.mGas=solarFluid("densesupercriticalsteam");
        Materials.SuperCoolant.mFluid=solarFluid("nei_solar_supercoolant");
        solarFluid("cryotheum");
        Materials.Time.mStandardMoltenFluid=solarFluid("nei_solar_tachyon");
        Materials.Space.mStandardMoltenFluid=solarFluid("nei_solar_spatial");
        Materials.Helium.mGas=solarFluid("nei_solar_helium_gas");
        bartworks.system.material.WerkstoffLoader.molten.put(goodgenerator.items.GGMaterial.atomicSeparationCatalyst,
            solarFluid("nei_solar_atomic"));
        gtPlusPlus.core.material.MaterialMisc.SOLAR_SALT_COLD.setFluid(solarFluid("nei_solar_cold_salt"));
        gtPlusPlus.core.material.MaterialMisc.SOLAR_SALT_HOT.setFluid(solarFluid("nei_solar_hot_salt"));
        gregtech.api.util.GTOreDictUnificator.getName2StackMap().put(OrePrefixes.dust.get(Materials.Stone).toString(),
            new ItemStack(Items.flint));
        com.miaokatze.gtsr.common.api.enums.GTSRItemList.IrradiatedStoneDust.set(new ItemStack(Items.quartz));
        Set<GTRecipe> previous=Collections.newSetFromMap(new IdentityHashMap<>());
        previous.addAll(CriticalRecipeMaps.forKind(CriticalMachineKind.SOLAR).getAllRecipes());
        Method register=CriticalMachineRecipes.class.getDeclaredMethod("registerSolar");
        register.setAccessible(true);register.invoke(null);
        Set<Integer> steamModes=new HashSet<>();
        int conversions=0,plasmas=0;
        BigInteger waterEU=null,saltEU=null;
        for(GTRecipe recipe:CriticalRecipeMaps.forKind(CriticalMachineKind.SOLAR).getAllRecipes()) {
            if(previous.contains(recipe))continue;
            var info=CriticalRecipeMaps.displayInfo(recipe);
            check(Set.of("solar_water","solar_salt","solar_plasma").contains(info.operation),
                "boiler catalogue contains only working-fluid conversions, with no medium examples");
            conversions++;
            check(recipe.mFluidInputs.length==1&&recipe.mFluidOutputs.length==1,
                "water/salt views show exactly one converted working fluid and its product");
            check(recipe.mDuration==CriticalEnergyMath.CYCLE_TICKS,"all solar views keep a fixed 400-tick batch");
            if(info.operation.equals("solar_plasma")) {
                plasmas++;
                var plasma=info.plasma;
                check(plasma!=null&&recipe.mFluidInputs[0].isFluidEqual(plasma.input)
                    &&recipe.mFluidOutputs[0].isFluidEqual(plasma.output)
                    &&recipe.mFluidInputs[0].amount==1&&recipe.mFluidOutputs[0].amount==1,
                    "each plasma card indexes its native1:1 working-fluid conversion");
                BigInteger stock=plasma.halfSaturation.multiply(BigInteger.valueOf(5));
                BigInteger output=plasma.batchLitres(stock,CriticalSolarPolicy.PRIVATE_DIMENSION_FACTOR,
                    CriticalSolarPolicy.COOLANTS.get(1),CriticalSolarPolicy.EXCITERS.get(1));
                check(output.compareTo(stock)<=0,"normalized plasma stock funds its real batch");
                BigInteger heat=output.multiply(plasma.denseSteamLitres).multiply(BigInteger.valueOf(2500))
                    .divide(plasma.hotLitres);
                BigInteger target=new BigInteger("168750000000000");
                BigInteger oneLitreLoss=plasma.denseSteamLitres.multiply(BigInteger.valueOf(2500))
                    .add(plasma.hotLitres.subtract(BigInteger.ONE)).divide(plasma.hotLitres);
                check(heat.compareTo(target)<=0&&target.subtract(heat).compareTo(oneLitreLoss)<=0,
                    "same five-half-stock plasma saturation yields its doubled nominal heat within one native plasma litre: "+plasma.id);
                continue;
            }
            var solar=info.solar;
            check(solar!=null,"all boiler cards freeze typed batch metadata at registration");
            check(solar.inputLitres.compareTo(solar.exampleStock)<=0,"shown working-fluid stock funds the full real batch");
            check(solar.exampleStock.equals(solar.halfSaturationStock.multiply(BigInteger.valueOf(info.operation.equals("solar_salt")?5:10))),
                "all water and salt examples share five-half-stock saturation");
            check(Math.abs(solar.multiplier-1.35D)<0.000001D,"example uses dimension 0.6, coolant 1.5 and activator 1.5");
            if(info.operation.equals("solar_salt")) {
                saltEU=info.baseEu;
                check(recipe.mFluidInputs[0].amount==CriticalSolarPolicy.saltLitres(saltEU).intValueExact()
                    &&recipe.mFluidOutputs[0].amount==recipe.mFluidInputs[0].amount,"salt example pays equal-volume hot salt");
                check(solar.mode==CriticalRecipeMaps.SteamMode.SALT
                    &&solar.coefficient.equals(new BigInteger("80000000000"))
                    &&solar.exampleStock.equals(new BigInteger("500000000000"))
                    &&solar.inputLitres.equals(new BigInteger("90000000000"))
                    &&solar.outputLitres.equals(solar.inputLitres)
                    &&solar.heatEU.equals(new BigInteger("225000000000000")),"salt card displays funded 500G stock / 90G output / 225T supercritical steam heat");
                check(solar.heatEU.multiply(BigInteger.valueOf(3)).equals(new BigInteger("67500000000000")
                    .multiply(BigInteger.TEN)),"native supercritical salt line follows the doubled formula before water inventory protection");
            } else {
                if(waterEU==null)waterEU=info.baseEu;
                check(waterEU.equals(info.baseEu),"six water-route steam forms retain equal equivalent EU");
                int mode=-1;for(int i=0;i<6;i++)if(recipe.mFluidOutputs[0].isFluidEqual(CriticalEnergyRuntime.steam(i)))mode=i;
                check(mode>=0&&steamModes.add(mode),"one operation example per native steam form");
                BigInteger heat=BigInteger.valueOf(recipe.mFluidOutputs[0].amount)
                    .multiply(CriticalEnergyMath.steamHeatPerL(mode));
                check(heat.equals(CriticalSolarPolicy.waterHalfHeat(info.baseEu)),"all steam volumes use the common exact heat quantum");
                check(solar.mode.index==mode,"batch metadata freezes this card's selected steam form");
                String[] coefficients={"120000000000000","40000000000000","24000000000000","120000000000","40000000000","24000000000"};
                String[] production={"147272727270000","49090909090000","29454545454000","147272727270","49090909090","29454545454"};
                String[] consumption={"920454545438","306818181814","184090909088","920454545438","306818181814","184090909088"};
                check(solar.coefficient.equals(new BigInteger(coefficients[mode]))
                    &&solar.outputLitres.equals(new BigInteger(production[mode]))
                    &&solar.inputLitres.equals(new BigInteger(consumption[mode])),"selected steam coefficient, actual output and paid water match policy7");
                check(solar.exampleStock.equals(new BigInteger("1000000000000"))
                    &&solar.heatEU.equals(new BigInteger("73636363635000")),"all six inventory-protected examples carry equal native steam heat");
                check(solar.outputLitres.multiply(CriticalEnergyMath.steamHeatPerL(mode))
                    .equals(solar.heatEU.multiply(BigInteger.valueOf(2))),"displayed heat matches selected steam's full native cascade");
            }
        }
        check(conversions==7+CriticalPlasmaFixture.eligibleIds().size()
            &&plasmas==CriticalPlasmaFixture.eligibleIds().size(),"boiler cards cover six steam, one salt and the eligible native plasma fluids");
        check(CriticalRecipeMaps.forKind(CriticalMachineKind.SOLAR).getFrontend().getUIProperties().maxFluidInputs==1,
            "boiler native template has no coolant or activator slots");
        check(steamModes.size()==6,"all six water-route steam forms indexed: "+steamModes);
        check(saltEU.equals(waterEU.multiply(BigInteger.valueOf(CriticalSolarPolicy.SALT_POWER_MULTIPLIER))),
            "salt sample demonstrates exactly twice the same-grade water-route equivalent EU: water="+waterEU+" salt="+saltEU);
        int dust=0;
        for(GTRecipe recipe:CriticalRecipeMaps.stellarIrradiation.getAllRecipes()) {
            var info=CriticalRecipeMaps.displayInfo(recipe);
            if(!info.operation.equals("solar_dust"))continue;
            dust++;
            check(recipe.mInputs.length==1&&recipe.mOutputs.length==1&&recipe.mFluidInputs.length==0
                &&recipe.mFluidOutputs.length==0,"irradiation shows only the real dust-to-dust conversion");
        }
        check(dust>0,"a real dust conversion is indexed");
        var ui=CriticalRecipeMaps.stellarIrradiation.getFrontend().getUIProperties();
        check(ui.maxItemInputs==1&&ui.maxItemOutputs==1&&ui.maxFluidInputs==0&&ui.maxFluidOutputs==0,
            "irradiation native template has no coolant or activator slots");
    }
    static void chemicalCompounds() throws Exception {
        // Identity fixture only; the actual GT205 helper still creates zero-sized selector stacks.
        ItemList.Circuit_Integrated.set(new ItemStack(Items.comparator));
        List<CriticalGTRecipes.ChemicalFuel> fuels=new ArrayList<>();
        int id=0;
        for(Materials material:List.of(Materials.Diesel,Materials.LightFuel,Materials.HeavyFuel,Materials.NitroFuel,
            Materials.GasolineRegular,Materials.GasolinePremium,Materials.Ethanol,Materials.Methanol,
            Materials.Hydrogen,Materials.Methane,Materials.Benzene,Materials.NefariousGas,
            Materials.Ethylene,Materials.Propene,Materials.Butene,Materials.Butadiene,
            Materials.Ethane,Materials.Propane,Materials.Butane,Materials.Toluene)) {
            Fluid fluid=new Fluid("nei_compound_"+id);FluidRegistry.registerFluid(fluid);material.mFluid=fluid;
            fuels.add(new CriticalGTRecipes.ChemicalFuel(new FluidStack(fluid,1),BigInteger.valueOf(1000L+id++*7000),0));
        }
        for(String name:List.of("RP1RocketFuel","DenseHydrazineFuelMixture","CN3H7O3RocketFuel","H8N4C2O4RocketFuel")) {
            Fluid fluid=new Fluid("nei_compound_"+id);FluidRegistry.registerFluid(fluid);
            gtPlusPlus.core.fluids.GTPPFluids.class.getField(name).set(null,fluid);
            fuels.add(new CriticalGTRecipes.ChemicalFuel(new FluidStack(fluid,1),BigInteger.valueOf(1000L+id++*7000),2));
        }
        Fluid promoter=new Fluid("combustionpromotor");FluidRegistry.registerFluid(promoter);
        Fluid ozone=new Fluid("nei_compound_ozone");FluidRegistry.registerFluid(ozone);Materials.Ozone.mFluid=ozone;
        var expected=CriticalChemicalRecipes.catalog(fuels);
        check(expected.size()==1024,"complete native compound catalog has 1024 examples");
        var map=CriticalRecipeMaps.forKind(CriticalMachineKind.BATTERY);
        int before=map.getAllRecipes().size();
        Method register=CriticalMachineRecipes.class.getDeclaredMethod("registerChemicalMixtures",List.class);
        register.setAccessible(true);register.invoke(null,fuels);
        Method tooltip=CriticalRecipeFrontend.class.getDeclaredMethod("handleNEIItemInputTooltip",List.class,
            gregtech.nei.GTNEIDefaultHandler.FixedPositionedStack.class);tooltip.setAccessible(true);
        ItemStack selector=gregtech.api.util.GTUtility.getIntegratedCircuit(9);
        var selectorStack=new gregtech.nei.GTNEIDefaultHandler.FixedPositionedStack(null,selector,true,
            CriticalNEILayout.CHEMICAL_CIRCUIT_X+1,CriticalNEILayout.CHEMICAL_CIRCUIT_Y+1,10000,false,true);
        check(selectorStack.isNotConsumed(),"GT205 native zero-size selector example is not consumed");
        List<String> expectedTip=new ArrayList<>();
        for(String key:List.of("bus","not_consumed","number"))expectedTip.add(CriticalRecipeMaps.text("showcase.circuit."+key));
        check(tooltip.invoke(map.getFrontend(),new ArrayList<String>(),selectorStack).equals(expectedTip),
            "selector tooltip states bus, non-consumption and component-number relation");
        check(map.getAllRecipes().size()==before+expected.size(),"compound-only indexed entries without single-fuel pages");
        Set<String> seen=new HashSet<>();
        Map<Integer,Integer> componentCounts=new TreeMap<>();
        Map<Integer,Set<Set<Integer>>> identitySets=new TreeMap<>();
        int previousPeak=0;
        for(GTRecipe recipe:map.getAllRecipes()) {
            var info=CriticalRecipeMaps.displayInfo(recipe);if(info.compound==null)continue;
            var compound=info.compound;check(seen.add(compound.id),"unique compound scheme");
            check(compound.id.startsWith("reaction."),"NEI indexes the current policy without reusing frozen legacy compound IDs");
            int components=compound.components.size();componentCounts.merge(components,1,Integer::sum);
            Set<Integer> identities=new TreeSet<>();
            for(var component:compound.components)identities.add(component.fuel.fluid.getFluidID());
            check(identities.size()==components,"every visible component is a distinct real fluid identity");
            check(identitySets.computeIfAbsent(components,ignored->new HashSet<>()).add(identities),
                "native component family varies real fluid sets instead of reordering the same ingredients");
            int[] minimum={2,13,25,37,49,61,73,87},maximum={12,24,36,48,60,72,86,100};
            check(compound.peakMultiplier>=minimum[components-2]
                &&compound.peakMultiplier<=maximum[components-2],"native example uses its component complexity efficiency band");
            check(compound.peakMultiplier>=previousPeak,"native examples sorted from low to high efficiency");previousPeak=compound.peakMultiplier;
            check(recipe.mFakeRecipe&&!recipe.mHidden&&recipe.mEUt==0,"native indexed read-only compound query entry");
            check(recipe.mFluidInputs.length==compound.components.size()&&recipe.mFluidInputs.length>=2
                &&recipe.mFluidInputs.length<=9,"two through nine native clickable fluid inputs");
            check(recipe.mInputs.length==1&&recipe.mInputs[0].stackSize==0
                &&recipe.mInputs[0].getItemDamage()==compound.selectorCircuit
                &&gregtech.api.util.GTUtility.areStacksEqual(recipe.mInputs[0],
                    gregtech.api.util.GTUtility.getIntegratedCircuit(components)),
                "native non-consumed integrated circuit selects the exact component group");
            BigInteger heat=BigInteger.ZERO,total=BigInteger.ZERO;
            BigInteger[] litres=new BigInteger[compound.components.size()];int[] ratios=new int[litres.length];
            for(int i=0;i<litres.length;i++) {
                var component=compound.components.get(i);var actual=recipe.mFluidInputs[i];
                check(actual.isFluidEqual(component.fuel.fluid)&&actual.amount==component.ratio,"exact shared example identity/volume");
                litres[i]=BigInteger.valueOf(actual.amount);ratios[i]=component.ratio;total=total.add(litres[i]);
                heat=heat.add(component.fuel.euPerL.multiply(litres[i]));
            }
            var evaluation=CriticalChemicalMath.evaluate(litres,ratios,total,heat,compound.peakMultiplier);
            check(info.inputHeat.equals(heat)&&info.baseEu.equals(evaluation.generatedEU),"example heat and output equal actual compound arithmetic");
            check(evaluation.efficiencyHundredths()==compound.peakMultiplier*100,"displayed example efficiency equals runtime");
            check(com.miaokatze.gtsr.client.nei.CriticalNEIShowcase.chemicalFillHeight(compound.peakMultiplier)
                ==(CriticalNEILayout.CHEMICAL_BAR_HEIGHT-4)*compound.peakMultiplier/100.0,"fixed-height vertical fill scales to 100x");
            check(recipe.mDuration==CriticalEnergyMath.CYCLE_TICKS&&recipe.mOutputs.length==0&&recipe.mFluidOutputs.length==0,
                "example cycle and native no-byproduct output");
        }
        check(seen.size()==expected.size(),"every actual compound indexed once");
        check(seen.equals(expected.stream().map(recipe->recipe.id).collect(java.util.stream.Collectors.toSet())),
            "native NEI IDs equal the current runtime catalog exactly");
        check(componentCounts.equals(Map.of(2,128,3,128,4,128,5,128,6,128,7,128,8,128,9,128)),
            "native indexed examples evenly cover every 2-9 component family");
        for(int components=2;components<=9;components++)check(identitySets.get(components).size()==128,
            "all 128 native entries in each component family have different material identities");
    }
    static void turbineAfterSolar() throws Exception {
        var map=CriticalRecipeMaps.forKind(CriticalMachineKind.TURBINE);
        int before=map.getAllRecipes().size();
        Method register=CriticalMachineRecipes.class.getDeclaredMethod("registerTurbine");
        register.setAccessible(true);register.invoke(null);
        check(map.getAllRecipes().size()==before+6,
            "missing Sulfur does not prevent subsequent turbine recipe registration");
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
        for(int nativeTicks:List.of(1,200,800,1600)){
            long nativeEut=2_000_000L;
            GTRecipe display=GTRecipeBuilder.builder().fluidInputs(new FluidStack(fluid,10))
                .fluidOutputs(new FluidStack(fluid,1)).duration(400).eut(0).build().orElseThrow();
            CriticalRecipeMaps.addFusionRecipe(display,nativeEut,nativeTicks);
            var info=CriticalRecipeMaps.displayInfo(display);
            check(info.nativeEut==nativeEut&&info.nativeTicks==nativeTicks,"fusion native power/time retained by indexed display");
            long runtimePower=CriticalStellarPolicy.runEuPerTick(nativeEut,nativeTicks,1);
            check(info.baseEu.equals(BigInteger.valueOf(runtimePower)),"fusion displayed EU/t equals runtime charged EU/t");
            java.math.BigDecimal saved=new java.math.BigDecimal(com.miaokatze.gtsr.client.nei.CriticalNEIShowcase
                .fusionDiscount(info).replace(",",""));
            check(saved.compareTo(java.math.BigDecimal.valueOf(nativeEut-runtimePower)
                .multiply(java.math.BigDecimal.valueOf(100)).divide(java.math.BigDecimal.valueOf(nativeEut)))==0,
                "fusion displayed discount uses actual rounded runtime power");
            String power=com.miaokatze.gtsr.client.nei.CriticalNEIShowcase.power(runtimePower);
            check(power.contains("EU/t")&&power.indexOf('\u00a7')>=0&&!power.matches(".*[0-9][Ee][+-]?[0-9].*"),
                "GT power line retains unit, native tier colors and full integer formatting");
            check(power.replaceAll("\u00a7.","").matches("[0-9,]+ EU/t \\([0-9,]+A .+\\)"),
                "native GT power line includes a complete parenthesized current and tier");
        }
        fusion.mEUt=(int)GTValues.V[10]+1;
        check(!(Boolean)filter.invoke(null,fusion), "voltage overcap excluded");
        fusion.mEUt=100;fusion.mFakeRecipe=true;
        check(!(Boolean)filter.invoke(null,fusion), "native fake recipe excluded");
        fusion.mFakeRecipe=false;fusion.mDuration=0;
        check(!(Boolean)filter.invoke(null,fusion), "zero duration excluded");
        showcaseTemplate();
        fullConstruction();
        manufacturingControllers();
        solarCatalogue();
        turbineAfterSolar();
        chemicalCompounds();
        stellarLifecycle();
        runtimeTemplatesAndUniqueness();
        System.out.println("PASS NEI catalog " + checks + " checks; "
            + (expectedJar == null ? "source overlay" : "candidate jar-only") + " GT205; no live client");
    }
}
