package audit;

import com.google.gson.*;
import java.util.*;

/** Bounded native NEI observations only; no construction, payment or world renderer. */
final class UiGalleryBatchV2010 {
    final UiGalleryBatch b;
    Object recipe, info, handler, tooltipStack;
    int recipeIndex, startDrawTick, tooltipDraws;
    JsonObject observation;
    List<String> tooltipLines;
    UiGalleryBatchV2010(UiGalleryBatch owner){b=owner;}
    static Object call(Object t,String n,Object...a)throws Exception{return UiGalleryBatch.call(t,n,a);}
    static Object field(Object t,String n)throws Exception{return UiGalleryBatch.field(t,n);}
    static Class<?> type(String n)throws Exception{return UiGalleryBatch.type(n);}
    boolean active(){return b.row!=null&&UiGalleryBatch.flag(b.row,"v2010");}
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
        int requested=UiGalleryBatch.integer(b.row,"infoPage",0);
        for(int i=0;i<recipes.size();i++){
            Object candidate=field(recipes.get(i),"mRecipe"),metadata=call(type(UiGalleryBatch.G+"api.recipe.CriticalRecipeMaps"),"displayInfo",candidate);
            if(metadata==null||!wanted.equals(String.valueOf(field(metadata,"operation")))||!option("machineKind","").equals(String.valueOf(field(metadata,"kind"))))continue;
            int page=b.q.number(field(metadata,"page")),pages=b.q.number(field(metadata,"pages"));
            if(page!=(requested<0?pages-1:requested))continue;
            if(UiGalleryBatch.flag(b.row,"multiPage")&&pages<2)continue;
            int score=0;for(Object line:(List<?>)call(type(UiGalleryBatch.G+"api.recipe.CriticalRecipeMaps"),"description",candidate))score+=String.valueOf(line).length();
            if(recipeIndex<0||UiGalleryBatch.flag(b.row,"longest")&&score>best){recipeIndex=i;best=score;recipe=candidate;info=metadata;}
        }
        b.q.check("v2010.native-entry."+b.row.get("name").getAsString(),recipeIndex>=0,"Real GT cached recipe selector "+wanted+" page="+requested);
        if(recipeIndex<0)throw new AssertionError("No native entry matched "+b.row);
        call(field(b.q.gui,"handlerPages"),"gotoRecipeIndex",recipeIndex);call(b.q.gui,"refreshContainer");
        observation=new JsonObject();observation.addProperty("recipeIndex",recipeIndex);
        for(String key:new String[]{"kind","operation","page","pages","spectrum"})observation.addProperty(key,String.valueOf(field(info,key)));
        observation.add("description",new Gson().toJsonTree(call(type(UiGalleryBatch.G+"api.recipe.CriticalRecipeMaps"),"description",recipe)));
        Object cached=recipes.get(recipeIndex);List<?> inputs=(List<?>)call(cached,"getIngredients"),outputs=(List<?>)call(cached,"getOtherStacks");
        observation.addProperty("nativeInputs",inputs.size());observation.addProperty("nativeOutputs",outputs.size());
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
        Object pos=call(b.q.gui,"getRecipePosition",recipeIndex);observation.addProperty("recipeX",b.q.number(field(pos,"x")));observation.addProperty("recipeY",b.q.number(field(pos,"y")));
    }
    void afterCapture(JsonObject event)throws Exception {
        if(!active())return;event.add("nativeNeiObservation",observation);
        if(!UiGalleryBatch.flag(b.row,"queryRU"))return;
        Object stack=field(tooltipStack,"item");
        for(String query:new String[]{"GuiCraftingRecipe","GuiUsageRecipe"}){
            boolean opened=Boolean.TRUE.equals(call(type("codechicken.nei.recipe."+query),"openRecipeGui","item",new Object[]{stack}));
            b.q.check("v2010.native-query."+query+"."+b.row.get("name").getAsString(),opened,"Public native item/fluid-display query on actual cached stack; physical R/U key NOT_RUN");
            Object gui=field(b.q.mc,"currentScreen|field_71462_r");JsonArray ids=new JsonArray();
            if(opened)for(Object h:(List<?>)call(gui,"getCurrentRecipeHandlers"))ids.add(new JsonPrimitive(String.valueOf(call(h,"getHandlerId"))));
            observation.add(query+"Handlers",ids);
        }
        b.currentKey="";
    }
}
