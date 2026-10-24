package audit;

import com.google.gson.*;
import java.util.*;

/** Bounded native NEI observations only; no construction, payment or world renderer. */
final class UiGalleryBatchV2011 {
    final UiGalleryBatch b;
    Object recipe, info, handler, tooltipStack;
    int recipeIndex, startDrawTick, tooltipDraws;
    List<?> inputs,outputs;
    JsonObject observation;
    List<String> tooltipLines;
    UiGalleryBatchV2011(UiGalleryBatch owner){b=owner;}
    static Object call(Object t,String n,Object...a)throws Exception{return UiGalleryBatch.call(t,n,a);}
    static Object field(Object t,String n)throws Exception{return UiGalleryBatch.field(t,n);}
    static Class<?> type(String n)throws Exception{return UiGalleryBatch.type(n);}
    boolean active(){return b.row!=null&&UiGalleryBatch.flag(b.row,"v2011");}
    String option(String n,String fallback){return UiGalleryBatch.str(b.row,n,fallback);}
    void configure()throws Exception {
        tooltipStack=null;tooltipLines=null;tooltipDraws=0;observation=null;
        if(!active())return;
        UiGalleryBatch.set(type(UiGalleryBatch.G+"config.Config"),"criticalUiReducedMotion",UiGalleryBatch.flag(b.row,"reducedMotion"));
        Object settings=field(b.q.mc,"gameSettings|field_71474_y");
        String language=option("language","zh_CN");
        if(!language.equals(String.valueOf(field(settings,"language|field_74363_ab")))){
            Object manager=call(b.q.mc,"getLanguageManager|func_135016_M"),selected=null;
            for(Object lang:(Iterable<?>)call(manager,"getLanguages|func_135040_d"))
                if(language.equals(String.valueOf(call(lang,"getLanguageCode|func_135034_a"))))selected=lang;
            if(selected==null)throw new AssertionError("Installed language missing "+language);
            call(manager,"setCurrentLanguage|func_135045_a",selected);
            UiGalleryBatch.set(settings,"language|field_74363_ab",language);
            call(b.q.mc,"refreshResources|func_110436_a");
            Object font=field(b.q.mc,"fontRenderer|field_71466_p");
            call(font,"setUnicodeFlag|func_78264_a",Boolean.TRUE.equals(call(manager,"isCurrentLocaleUnicode|func_135042_a"))||Boolean.TRUE.equals(field(settings,"forceUnicodeFont|field_151455_aw")));
            call(font,"setBidiFlag|func_78275_b",call(manager,"isCurrentLanguageBidirectional|func_135044_b"));
            b.currentKey="";
        }
    }
    void navigate()throws Exception {
        if(!active())return;
        handler=call(b.q.gui,"getHandler");List<?> recipes=(List<?>)field(handler,"arecipes");
        recipeIndex=-1;int best=-1;String wanted=option("operation","");
        int matched=0, wantedMatch=UiGalleryBatch.integer(b.row,"matchIndex",0);
        for(int i=0;i<recipes.size();i++){
            Object candidate=field(recipes.get(i),"mRecipe"),metadata=call(type(UiGalleryBatch.G+"api.recipe.CriticalRecipeMaps"),"displayInfo",candidate);
            if(metadata==null||!wanted.equals(String.valueOf(field(metadata,"operation")))||!option("machineKind","").equals(String.valueOf(field(metadata,"kind"))))continue;
            int score=b.q.number(field(candidate,"mDuration"));
            if(matched++==wantedMatch||UiGalleryBatch.flag(b.row,"longest")&&score>best){recipeIndex=i;best=score;recipe=candidate;info=metadata;}
        }
        b.q.check("v2011.native-entry."+b.row.get("name").getAsString(),recipeIndex>=0,"Real GT cached recipe selector "+wanted+" match="+wantedMatch);
        if(recipeIndex<0)throw new AssertionError("No native entry matched "+b.row);
        call(field(b.q.gui,"handlerPages"),"gotoRecipeIndex",recipeIndex);call(b.q.gui,"refreshContainer");
        observation=new JsonObject();observation.addProperty("recipeIndex",recipeIndex);
        for(String key:new String[]{"kind","operation","spectrum"})observation.addProperty(key,String.valueOf(field(info,key)));
        observation.addProperty("matchingOperationRecipes",matched);
        observation.addProperty("selectionScope","Real recipe variants only; no explanatory page selection");
        Object cached=recipes.get(recipeIndex);inputs=(List<?>)call(cached,"getIngredients");outputs=(List<?>)call(cached,"getOtherStacks");
        observation.addProperty("nativeInputs",inputs.size());observation.addProperty("nativeOutputs",outputs.size());
        if(wanted.startsWith("chemical_window_")) chemicalRecipes(recipes);
        if(b.row.has("tooltipType")){
            List<Object> stacks=new ArrayList<Object>();stacks.addAll(inputs);stacks.addAll(outputs);
            boolean wantFluid=option("tooltipType","").equals("fluid");
            // GT205 native recipes use NEI ItemFluidDisplay, not the legacy GT display item.
            for(Object positioned:stacks){boolean fluid=Boolean.TRUE.equals(call(positioned,"isFluid"));if(fluid==wantFluid){tooltipStack=positioned;break;}}
            if(tooltipStack==null)throw new AssertionError("No native "+option("tooltipType","")+" stack");
        }
        startDrawTick=drawTick();
    }
    int drawTick()throws Exception{return b.q.number(call(type("gregtech.nei.GTNEIDefaultHandler"),"getDrawTicks"));}
    void chemicalRecipes(List<?> recipes)throws Exception {
        JsonArray observed=new JsonArray();
        Map<String,Object[]> fuels=new HashMap<String,Object[]>();
        for(Object cached:recipes){
            Object candidate=field(cached,"mRecipe"),metadata=call(type(UiGalleryBatch.G+"api.recipe.CriticalRecipeMaps"),"displayInfo",candidate);
            if(metadata==null||!"battery".equals(String.valueOf(field(metadata,"operation"))))continue;
            Object fluidStack=java.lang.reflect.Array.get(field(candidate,"mFluidInputs"),0);
            fuels.put(String.valueOf(call(call(fluidStack,"getFluid"),"getName")),new Object[]{field(metadata,"spectrum"),field(metadata,"baseEu")});
        }
        for(Object cached:recipes){
            Object candidate=field(cached,"mRecipe"),metadata=call(type(UiGalleryBatch.G+"api.recipe.CriticalRecipeMaps"),"displayInfo",candidate);
            if(metadata==null||!String.valueOf(field(metadata,"operation")).startsWith("chemical_window_"))continue;
            JsonObject value=new JsonObject();value.addProperty("operation",String.valueOf(field(metadata,"operation")));value.addProperty("baseEu",String.valueOf(field(metadata,"baseEu")));
            JsonArray fluids=new JsonArray();Object list=field(candidate,"mFluidInputs");
            java.math.BigInteger[] heat={java.math.BigInteger.ZERO,java.math.BigInteger.ZERO,java.math.BigInteger.ZERO};
            for(int i=0;i<java.lang.reflect.Array.getLength(list);i++){
                Object stack=java.lang.reflect.Array.get(list,i);if(stack==null)continue;Object fluid=call(stack,"getFluid");JsonObject f=new JsonObject();String name=String.valueOf(call(fluid,"getName"));int litres=b.q.number(field(stack,"amount"));f.addProperty("name",name);f.addProperty("litres",litres);
                Object[] fuel=fuels.get(name);if(fuel==null)throw new AssertionError("Mixture fluid lacks real native fuel entry: "+name);
                int spectrum=((Number)fuel[0]).intValue();java.math.BigInteger perL=(java.math.BigInteger)fuel[1];heat[spectrum]=heat[spectrum].add(perL.multiply(java.math.BigInteger.valueOf(litres)));f.addProperty("spectrum",spectrum);f.addProperty("nativeEuPerL",perL.toString());fluids.add(f);
            }
            int window=b.q.number(field(metadata,"spectrum"));Object math=type(UiGalleryBatch.G+"common.critical.CriticalChemicalMath");
            java.math.BigInteger cap=(java.math.BigInteger)call(math,"cap",window),total=(java.math.BigInteger)call(math,"sum",(Object)heat);
            Object matched=call(math,"match",window,heat,cap);int multiplier=b.q.number(call(math,"multiplier",window));
            boolean exact=Arrays.equals(heat,(java.math.BigInteger[])matched),withinCap=total.compareTo(cap)<=0,euMatches=total.multiply(java.math.BigInteger.valueOf(multiplier)).equals(field(metadata,"baseEu"));
            value.add("paidHeatBySpectrum",new Gson().toJsonTree(heat));value.addProperty("cap",cap.toString());value.addProperty("totalHeat",total.toString());value.addProperty("multiplier",multiplier);value.addProperty("runtimeMatchExact",exact);value.addProperty("withinCap",withinCap);value.addProperty("displayedEuMatches",euMatches);
            b.q.check("v2011.chemical.cap-match."+window,exact&&withinCap&&euMatches,"Real registered fluids/L/native fuel heat -> production cap/match/multiplier agree; read-only, no payment");
            value.add("nativeFluidInputs",fluids);observed.add(value);
        }
        observation.add("chemicalRepresentativeRecipes",observed);
        b.q.check("v2011.chemical.native-representatives",observed.size()==3,"Three loaded native representative recipes; actual fluid names/L/baseEu recorded, no fuel consumption");
    }
    void geometryProof()throws Exception {
        JsonArray frames=new JsonArray();Object window=field(handler,"modularWindow"),ctx=field(handler,"templateContext");
        for(Object widget:(List<?>)call(window,"getChildren")){
            if(!type("com.gtnewhorizons.modularui.common.widget.SlotWidget").isInstance(widget))continue;
            Object pos=call(widget,"getPos"),size=call(widget,"getSize"),inventory=call(call(widget,"getMcSlot"),"getItemHandler");
            JsonObject frame=new JsonObject();frame.addProperty("x",b.q.number(field(pos,"x")));frame.addProperty("y",b.q.number(field(pos,"y")));frame.addProperty("width",b.q.number(field(size,"width")));frame.addProperty("height",b.q.number(field(size,"height")));
            for(String group:new String[]{"itemInputsInventory","itemOutputsInventory","fluidInputsInventory","fluidOutputsInventory"})if(inventory==field(ctx,group))frame.addProperty("group",group);
            frames.add(frame);
        }
        JsonArray stacks=new JsonArray();List<Object> all=new ArrayList<Object>();all.addAll(inputs);all.addAll(outputs);boolean allInset=true,allHit=true;
        Object recipePos=call(b.q.gui,"getRecipePosition",recipeIndex);
        int originX=b.q.number(field(b.q.gui,"guiLeft|field_147003_i"))+b.q.number(field(recipePos,"x"));
        int originY=b.q.number(field(b.q.gui,"guiTop|field_147009_r"))+b.q.number(field(recipePos,"y"));
        for(Object positioned:all){
            int x=b.q.number(field(positioned,"relx")),y=b.q.number(field(positioned,"rely"));JsonObject stack=new JsonObject();stack.addProperty("x",x);stack.addProperty("y",y);stack.addProperty("width",16);stack.addProperty("height",16);stack.addProperty("fluid",Boolean.TRUE.equals(call(positioned,"isFluid")));stack.addProperty("input",Boolean.TRUE.equals(call(positioned,"isInput")));
            Object item=field(positioned,"item");stack.addProperty("displayName",String.valueOf(call(item,"getDisplayName|func_82833_r")));boolean matched=false;
            Object hit=call(b.q.gui,"getSlotAtPosition|func_146975_c",originX+x+8,originY+y+8);
            boolean hitSame=hit!=null&&call(hit,"getStack|func_75211_c")==item;stack.addProperty("nativeCenterHitSameStack",hitSame);allHit&=hitSame;
            for(JsonElement e:frames){JsonObject frame=e.getAsJsonObject();if(x==frame.get("x").getAsInt()+1&&y==frame.get("y").getAsInt()+1&&frame.get("width").getAsInt()==18&&frame.get("height").getAsInt()==18){stack.add("actualSlotFrame",frame);matched=true;break;}}
            stack.addProperty("nativeOnePixelInset",matched);allInset&=matched;stacks.add(stack);
        }
        observation.add("nativeSlotFrames",frames);observation.add("nativePositionedStacks",stacks);
        b.q.check("v2011.geometry.native-inset."+b.row.get("name").getAsString(),allInset&&!all.isEmpty(),"Independently observed ModularWindow SlotWidget18x18 and cached PositionedStack16x16 differ by exactly +1,+1");
        b.q.check("v2011.geometry.native-hit."+b.row.get("name").getAsString(),allHit&&!all.isEmpty(),"Actual GuiRecipe coordinate hit returns identical cached item/fluid stack for every visible native stack; synthetic coordinate query only");
        JsonArray buttons=new JsonArray();
        // RecipePageManager.getCurrentPageWidgets resets locations to layout-local (2,0).
        // Read the already displayed container widgets without invoking that relayout method.
        for(Object widget:(List<?>)call(field(b.q.gui,"container"),"getWidgets")){
            if(!type("codechicken.nei.recipe.widget.RecipeWidget").isInstance(widget)||!Boolean.TRUE.equals(call(widget,"containsRecipeIndex",recipeIndex)))continue;
            for(Object button:(List<?>)call(widget,"getRecipeButtons")){
                JsonObject item=new JsonObject();item.addProperty("class",button.getClass().getName());item.addProperty("x",b.q.number(field(button,"xPosition|field_146128_h")));item.addProperty("y",b.q.number(field(button,"yPosition|field_146129_i")));item.addProperty("width",b.q.number(field(button,"width|field_146120_f")));item.addProperty("height",b.q.number(field(button,"height|field_146121_g")));item.addProperty("widgetX",b.q.number(field(widget,"x")));item.addProperty("widgetY",b.q.number(field(widget,"y")));buttons.add(item);
            }
        }
        observation.add("actualNativeRecipeButtons",buttons);
        observation.addProperty("geometryScope","Slot/button rectangles from actual initialized native widgets. Arrow/text declared draw positions are source-backed expectations; PNG inspection verifies their visible alignment and button clearance.");
        if(b.row.has("arrowCenterY"))observation.add("declaredArrowCenterY",b.row.get("arrowCenterY"));
    }
    void labelProof()throws Exception {
        JsonArray result=new JsonArray();Object font=field(b.q.mc,"fontRenderer|field_71466_p");boolean translated=true;
        observation.addProperty("fontRendererClass",font.getClass().getName());observation.addProperty("fontHeight",b.q.number(field(font,"FONT_HEIGHT|field_78288_b")));
        if(b.row.has("labels"))for(JsonElement element:b.row.getAsJsonArray("labels")){
            JsonObject declared=element.getAsJsonObject(),label=new JsonObject();String text;
            if(declared.has("key"))text=String.valueOf(call(type("net.minecraft.util.StatCollector"),"translateToLocal|func_74838_a",declared.get("key").getAsString()));
            else if(declared.has("recipeDuration"))text=field(recipe,"mDuration")+" t";
            else if(declared.has("baseEu"))text=field(info,"baseEu")+" EU";
            else if(declared.has("policyField"))text=field(type(UiGalleryBatch.G+"common.critical.CriticalStellarPolicy"),declared.get("policyField").getAsString())+declared.get("suffix").getAsString();
            else if(declared.has("fusionEu"))text=call(type(UiGalleryBatch.G+"common.critical.CriticalStellarPolicy"),"runEuPerTick",Long.parseLong(String.valueOf(field(info,"baseEu"))),1)+" EU/t";
            else text=declared.get("text").getAsString();
            translated&=!text.contains("??")&&!text.startsWith("gtsr.");
            for(Map.Entry<String,JsonElement> entry:declared.entrySet())label.add(entry.getKey(),entry.getValue());label.addProperty("resolvedText",text);label.addProperty("actualFontWidth",b.q.number(call(font,"getStringWidth|func_78256_a",text)));result.add(label);
        }
        observation.add("sourceDeclaredLabelOriginsWithActualFontMetrics",result);
        b.q.check("v2011.labels.translated."+b.row.get("name").getAsString(),translated&&result.size()>0,"Actual client translations contain neither repeated ASCII question marks nor untranslated GTSR resource keys");
    }
    boolean ready()throws Exception{return !active()||drawTick()-startDrawTick>=UiGalleryBatch.integer(b.row,"minimumDrawTicks",2)&&(tooltipStack==null||tooltipDraws>=2);}
    void drawTooltip()throws Exception {
        if(!active()||b.stage!=14||tooltipStack==null)return;
        Object pos=call(b.q.gui,"getRecipePosition",recipeIndex);
        int x=b.q.number(field(b.q.gui,"guiLeft|field_147003_i"))+b.q.number(field(pos,"x"))+b.q.number(field(tooltipStack,"relx"))+8;
        int y=b.q.number(field(b.q.gui,"guiTop|field_147009_r"))+b.q.number(field(pos,"y"))+b.q.number(field(tooltipStack,"rely"))+8;
        Object slot=call(b.q.gui,"getSlotAtPosition|func_146975_c",x,y);
        Object stack=slot==null?null:call(slot,"getStack|func_75211_c");
        if(stack==null)throw new AssertionError("Native NEI stack coordinate missed "+x+","+y);
        if(tooltipLines==null){
            tooltipLines=new ArrayList<String>((List<String>)call(type("codechicken.nei.guihook.GuiContainerManager"),"itemDisplayNameMultiline",stack,b.q.gui,true));
            call(type("codechicken.nei.guihook.GuiContainerManager"),"applyItemCountDetails",tooltipLines,stack);
            tooltipLines=(List<String>)call(handler,"handleItemTooltip",b.q.gui,stack,tooltipLines,recipeIndex);
            observation.add("nativeTooltipLines",new Gson().toJsonTree(tooltipLines));
            observation.addProperty("nativeSlotClass",slot.getClass().getName());observation.addProperty("nativeTooltipX",x);observation.addProperty("nativeTooltipY",y);
            observation.addProperty("tooltipScope","Actual native slot coordinate hit, native item/fluid tooltip builders and NEI drawPagedTooltip; synthetic coordinates, physical cursor hover NOT_RUN");
        }
        call(type("codechicken.nei.guihook.GuiContainerManager"),"drawPagedTooltip",call(type("codechicken.nei.guihook.GuiContainerManager"),"getFontRenderer",stack),x,y,tooltipLines);
        tooltipDraws++;
    }
    void beforeCapture()throws Exception {
        if(!active())return;
        observation.addProperty("drawTick",drawTick());observation.addProperty("naturalDrawTicksElapsed",drawTick()-startDrawTick);
        observation.addProperty("language",String.valueOf(field(field(b.q.mc,"gameSettings|field_71474_y"),"language|field_74363_ab")));
        observation.addProperty("reducedMotion",Boolean.TRUE.equals(field(type(UiGalleryBatch.G+"config.Config"),"criticalUiReducedMotion")));
        observation.addProperty("nativeTooltipDraws",tooltipDraws);
        geometryProof();
        labelProof();
        Object pos=call(b.q.gui,"getRecipePosition",recipeIndex);observation.addProperty("recipeX",b.q.number(field(pos,"x")));observation.addProperty("recipeY",b.q.number(field(pos,"y")));
    }
    void afterCapture(JsonObject event)throws Exception {
        if(!active())return;event.add("nativeNeiObservation",observation);
        if(!UiGalleryBatch.flag(b.row,"queryRU"))return;
        Object stack=field(tooltipStack,"item");
        for(String query:new String[]{"GuiCraftingRecipe","GuiUsageRecipe"}){
            boolean opened=Boolean.TRUE.equals(call(type("codechicken.nei.recipe."+query),"openRecipeGui","item",new Object[]{stack}));
            b.q.check("v2011.native-query."+query+"."+b.row.get("name").getAsString(),opened,"Public native item/fluid-display query on actual cached stack; physical R/U key NOT_RUN");
            Object gui=field(b.q.mc,"currentScreen|field_71462_r");JsonArray ids=new JsonArray();
            if(opened)for(Object h:(List<?>)call(gui,"getCurrentRecipeHandlers"))ids.add(new JsonPrimitive(String.valueOf(call(h,"getHandlerId"))));
            observation.add(query+"Handlers",ids);
        }
        b.currentKey="";
    }
}
