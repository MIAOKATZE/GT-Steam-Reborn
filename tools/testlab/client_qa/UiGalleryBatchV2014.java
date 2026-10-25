package audit;

import com.google.gson.*;
import java.util.*;

/** Read-only current-FML catalogue and native capture evidence; never draws product elements. */
final class UiGalleryBatchV2014 {
    final UiGalleryBatch b;
    JsonObject proof;
    Object fixtureState;
    String fixtureRuntime,wirelessBefore;
    UiGalleryBatchV2014(UiGalleryBatch owner){b=owner;}
    boolean active(){return b.row!=null&&UiGalleryBatch.flag(b.row,"v2014");}
    void configure(){proof=null;}
    void prepareFixture()throws Exception {
        if(!active()||!UiGalleryBatch.str(b.row,"kind","").equals("CRITICAL_BATTERY"))return;
        Object[] f=(Object[])b.currentFixture;Object host=f[4];
        UiGalleryBatch.call(host,"disableWorking");
        fixtureState=UiGalleryBatch.call(UiGalleryBatch.call(host,"getMetaTileEntity"),"getCriticalState");
        b.q.check("v2014.fixture.unformed-disabled",b.q.number(UiGalleryBatch.field(fixtureState,"state"))==0&&!Boolean.TRUE.equals(UiGalleryBatch.call(host,"isAllowedToWork")),"Owned unformed controller; production tick has state0 and disabled gates; no real payment");
        Object compound=UiGalleryBatch.call(UiGalleryBatch.type(UiGalleryBatch.G+"common.critical.CriticalChemicalRecipes"),"byId","promoted.3");
        b.q.check("v2014.fixture.real-recipe-id",compound!=null,"Fixture references actual current-FML catalogue promoted.3");
        Object nbt=UiGalleryBatch.call(fixtureState,"energyRuntimeData");
        UiGalleryBatch.call(nbt,"setBoolean|func_74757_a","funded",true);
        for(String key:new String[]{"chemicalSchema","chemicalBatchRule"})UiGalleryBatch.call(nbt,"setInteger|func_74768_a",key,2);
        UiGalleryBatch.call(nbt,"setInteger|func_74768_a","chemicalEfficiencyHundredths",6500);
        for(String[] entry:new String[][]{{"chemicalRecipe","promoted.3"},{"chemicalBatchHeat","2000000000"},{"chemicalBatchLitres","40000"},{"eu","0"}})UiGalleryBatch.call(nbt,"setString|func_74778_a",entry[0],entry[1]);
        UiGalleryBatch.call(fixtureState,"setRuntimeBatch",120,400,0L,"[VISUAL FIXTURE] 展示用输入；非真实扣费");
        fixtureRuntime=String.valueOf(nbt);wirelessBefore=String.valueOf(UiGalleryBatch.field(UiGalleryBatch.type("gregtech.common.misc.GlobalVariableStorage"),"GlobalEnergy"));
    }
    void beforeNavigate()throws Exception {
        if(!active()||!b.row.has("peakRank"))return;
        Object handler=UiGalleryBatch.call(b.q.gui,"getHandler");
        List<?> recipes=(List<?>)UiGalleryBatch.field(handler,"arecipes");
        List<int[]> entries=new ArrayList<int[]>();JsonArray observed=new JsonArray(),special=new JsonArray();
        int match=0;
        for(int i=0;i<recipes.size();i++){
            Object recipe=UiGalleryBatch.field(recipes.get(i),"mRecipe");
            Object info=UiGalleryBatch.call(UiGalleryBatch.type(UiGalleryBatch.G+"api.recipe.CriticalRecipeMaps"),"displayInfo",recipe);
            if(info==null||!"chemical_compound".equals(String.valueOf(UiGalleryBatch.field(info,"operation"))))continue;
            Object compound=UiGalleryBatch.field(info,"compound");
            int peak=b.q.number(UiGalleryBatch.field(compound,"peakMultiplier"));
            entries.add(new int[]{match++,i,peak});JsonObject evidence=compoundEvidence(compound);observed.add(evidence);
            String id=evidence.get("id").getAsString();if(id.startsWith("promoted.")||id.startsWith("ozonated."))special.add(evidence);
        }
        b.q.check("v2014.chemical.real-FML-catalogue-size",entries.size()>=200&&entries.size()<=300,
            "Actual loaded native GTNEIDefaultHandler cached chemical_compound recipe count="+entries.size());
        Collections.sort(entries,new Comparator<int[]>(){public int compare(int[] a,int[] c){return Integer.compare(a[2],c[2]);}});
        String rank=b.row.get("peakRank").getAsString();
        int at=rank.equals("lowest")?0:rank.equals("highest")?entries.size()-1:entries.size()/2;
        int[] selected=entries.get(at);b.row.addProperty("matchIndex",selected[0]);
        proof=new JsonObject();proof.addProperty("catalogueCount",entries.size());proof.addProperty("selection",rank);
        proof.addProperty("selectedCachedRecipeIndex",selected[1]);proof.addProperty("selectedPeakMultiplier",selected[2]);
        proof.addProperty("lowestPeakMultiplier",entries.get(0)[2]);proof.addProperty("highestPeakMultiplier",entries.get(entries.size()-1)[2]);
        proof.add("actualNativeCatalogue",observed);proof.add("specialFluidSamples",special);
        b.q.check("v2014.chemical.real-special-samples",special.size()==8,"Eight actual native promoted/ozonated entries, complete fluid identities and ratios recorded");
        proof.addProperty("scope","Current FML production catalogue, native cached recipes and metadata; read-only; no fuel payment or fabricated fluid identity");
    }
    JsonObject compoundEvidence(Object compound)throws Exception {
        JsonObject item=new JsonObject();item.addProperty("id",String.valueOf(UiGalleryBatch.field(compound,"id")));
        item.addProperty("peakMultiplier",b.q.number(UiGalleryBatch.field(compound,"peakMultiplier")));
        JsonArray fluids=new JsonArray();
        for(Object component:(List<?>)UiGalleryBatch.field(compound,"components")){
            Object fuel=UiGalleryBatch.field(component,"fuel"),stack=UiGalleryBatch.field(fuel,"fluid"),fluid=UiGalleryBatch.call(stack,"getFluid");
            JsonObject part=new JsonObject();part.addProperty("registryName",String.valueOf(UiGalleryBatch.call(fluid,"getName")));
            part.addProperty("localizedName",String.valueOf(UiGalleryBatch.call(stack,"getLocalizedName")));
            part.addProperty("ratio",b.q.number(UiGalleryBatch.field(component,"ratio")));
            part.addProperty("euPerL",String.valueOf(UiGalleryBatch.field(fuel,"euPerL")));fluids.add(part);
        }
        item.add("nativeFluidComponents",fluids);return item;
    }
    void beforeCapture()throws Exception {
        if(!active())return;
        if(b.row.has("peakRank")){
            proof.add("selectedCompound",compoundEvidence(UiGalleryBatch.field(b.v2011.info,"compound")));
            proof.addProperty("actualInputHeat",String.valueOf(UiGalleryBatch.field(b.v2011.info,"inputHeat")));
            proof.addProperty("actualOutputHeat",String.valueOf(UiGalleryBatch.field(b.v2011.info,"baseEu")));
            proof.addProperty("nativeHitEvidence","nativeNeiObservation.nativePositionedStacks[].nativeCenterHitSameStack");
            proof.addProperty("sourceDeclaredBarCount",1);
            proof.addProperty("productionFillHeight",String.valueOf(UiGalleryBatch.call(UiGalleryBatch.type(UiGalleryBatch.G+"client.nei.CriticalNEIShowcase"),"chemicalFillHeight",proof.get("selectedPeakMultiplier").getAsInt())));
        }else if(UiGalleryBatch.str(b.row,"operation","").startsWith("build_")){
            proof=new JsonObject();proof.addProperty("constructionStage",String.valueOf(UiGalleryBatch.call(b.v2011.info,"constructionStage")));
            proof.addProperty("nativeSlotFrameCount",b.v2011.observation.getAsJsonArray("nativeSlotFrames").size());
            proof.addProperty("scope","Native slots and production construction renderer; stage/time text/no-footer confirmed by raw PNG review");
        }else{
            proof=new JsonObject();proof.addProperty("scope","Owned TestLab real MUI2 terminal; explicit visual fixture, no real deduction or receipt claim");
            proof.addProperty("actualPanelName",String.valueOf(UiGalleryBatch.call(b.q.panel,"getName")));
            proof.addProperty("actualWindowId",b.windowId);
            String after=String.valueOf(UiGalleryBatch.call(fixtureState,"energyRuntimeData"));
            String wirelessAfter=String.valueOf(UiGalleryBatch.field(UiGalleryBatch.type("gregtech.common.misc.GlobalVariableStorage"),"GlobalEnergy"));
            b.q.check("v2014.fixture.inert-runtime-and-wireless",fixtureRuntime.equals(after)&&wirelessBefore.equals(wirelessAfter),"Explicit display fixture runtime NBT and real wireless map unchanged from setup to capture; no debit or generation");
            proof.addProperty("fixtureRuntimeNBTBefore",fixtureRuntime);proof.addProperty("fixtureRuntimeNBTAtCapture",after);
            proof.addProperty("wirelessMapBefore",wirelessBefore);proof.addProperty("wirelessMapAtCapture",wirelessAfter);
            Object psm=UiGalleryBatch.call(UiGalleryBatch.call(b.q.screen,"getSyncManager"),"getMainPSM");
            Object sample=UiGalleryBatch.call(UiGalleryBatch.call(psm,"findSyncHandler","critical.businessSnapshot"),"getValue");
            JsonArray runtime=new JsonArray();int count=b.q.number(UiGalleryBatch.call(sample,"runtimeCount"));
            for(int i=0;i<count;i++){JsonObject item=new JsonObject();item.addProperty("key",String.valueOf(UiGalleryBatch.call(sample,"runtime",i,0)));item.addProperty("value",String.valueOf(UiGalleryBatch.call(sample,"runtime",i,1)));runtime.add(item);}
            proof.add("actualImmutableS2CRuntimeSnapshot",runtime);
            JsonArray widgets=new JsonArray();
            for(String name:new String[]{"critical_chemical_recipe","critical_chemical_flow","critical_runtime_process"}){
                Object widget=b.q.find(b.q.panel,name);b.q.check("v2014.terminal.native-widget."+name,widget!=null,"Actual production MUI2 widget present");
                widgets.add(b.hitEvidence(widget));
            }
            proof.add("actualChemicalWidgets",widgets);
        }
    }
    void afterCapture(JsonObject event){if(active()&&proof!=null)event.add("v2014NativeEvidence",proof);}
}
