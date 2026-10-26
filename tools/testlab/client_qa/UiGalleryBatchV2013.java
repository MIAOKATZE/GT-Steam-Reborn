package audit;

import com.google.gson.*;
import java.util.*;

/** Bounded v2013 native construction slots and GT/NEI tooltip observations. */
final class UiGalleryBatchV2013 {
    final UiGalleryBatch b;
    Object stack,chromaticContext;
    List<String> lines;
    JsonObject proof;
    int draws;
    boolean pageSelected;
    UiGalleryBatchV2013(UiGalleryBatch owner){b=owner;}
    boolean active(){return b.row!=null&&UiGalleryBatch.flag(b.row,"v2013");}
    boolean tooltip(){return active()&&b.row.has("nativeMachineTooltipEntry");}
    void configure(){stack=null;chromaticContext=null;lines=null;proof=null;draws=0;pageSelected=false;}
    void navigate()throws Exception {
        if(!tooltip())return;
        String entry=b.row.get("nativeMachineTooltipEntry").getAsString();
        stack=UiGalleryBatch.call(UiGalleryBatch.field(UiGalleryBatch.type(UiGalleryBatch.G+"common.api.enums.GTSRItemList"),entry),"get",1L,new Object[0]);
        Object item=UiGalleryBatch.call(stack,"getItem|func_77973_b");
        if(!UiGalleryBatch.type("gregtech.common.blocks.ItemMachines").isInstance(item))throw new AssertionError("Registered native GT machine required");
        lines=new ArrayList<String>((List<String>)UiGalleryBatch.call(stack,"getTooltip|func_82840_a",b.player(),false));
        List<?> pages=(List<?>)UiGalleryBatch.call(UiGalleryBatch.type("codechicken.nei.guihook.GuiContainerManager"),"splitTooltipByPage",lines);
        int page=UiGalleryBatch.integer(b.row,"nativeMachineTooltipPage",0);
        b.q.check("v2013.tooltip.native-page-exists."+entry+"."+page,pages.size()>page&&lines.size()>5,"Registered ItemStack.getTooltip native production chain; real NEI splitTooltipByPage");
        if(pages.size()<=page)throw new AssertionError("Requested native tooltip page absent: "+page+" / "+pages.size());
        proof=new JsonObject();proof.addProperty("registeredItem","GTSRItemList."+entry);proof.addProperty("registeredItemClass",item.getClass().getName());
        proof.addProperty("itemMeta",((Number)UiGalleryBatch.call(stack,"getItemDamage|func_77960_j")).intValue());
        proof.addProperty("nativePageCount",pages.size());proof.addProperty("requestedNativePage",page);proof.addProperty("physicalZ","NOT_RUN");proof.addProperty("physicalShift","NOT_RUN");
        proof.addProperty("scope","Registered ItemStack.getTooltip(player,false) -> installed Chromatic compatibility override of NEI drawPagedTooltip -> real Chromatic cache/context and public nextTooltipPage API. Submission occurs at DrawScreen.Pre LOWEST; Chromatic's own DrawScreen.Post HIGHEST drawLastTooltip renders its active page. No invalid original NEI page fields are set, custom renderer or physical keyboard claim.");
        proof.add("nativeLines",new Gson().toJsonTree(lines));proof.add("nativePages",new Gson().toJsonTree(pages));
        proof.add("nativePageDrawTrace",new JsonArray());
    }
    void drawTooltip()throws Exception {
        if(!tooltip()||b.stage!=14||lines==null)return;
        Object manager=UiGalleryBatch.type("codechicken.nei.guihook.GuiContainerManager");
        Object font=UiGalleryBatch.call(manager,"getFontRenderer",stack);
        int x=UiGalleryBatch.integer(b.row,"tooltipX",30),y=UiGalleryBatch.integer(b.row,"tooltipY",22);
        UiGalleryBatch.call(manager,"drawPagedTooltip",font,x,y,lines);
        Object backend=UiGalleryBatch.type("com.slprime.chromatictooltips.TooltipHandler");
        Object context=UiGalleryBatch.call(backend,"getLastTooltipContext");
        if(context==null||!requestMatches(backend))return;
        UiGalleryBatch.call(context,"getActivePageComponent");
        List<?> pages=(List<?>)UiGalleryBatch.field(context,"pagedComponents");
        int page=UiGalleryBatch.integer(b.row,"nativeMachineTooltipPage",0);
        if(pages.size()<=page)throw new AssertionError("Runtime Chromatic page absent: "+page+" / "+pages.size());
        int before=b.q.number(UiGalleryBatch.field(context,"currentPage"));
        int iterations=0;
        while(b.q.number(UiGalleryBatch.field(context,"currentPage"))!=page&&iterations++<pages.size())UiGalleryBatch.call(backend,"nextTooltipPage");
        int actual=b.q.number(UiGalleryBatch.field(context,"currentPage"));
        if(actual!=page)throw new AssertionError("Runtime Chromatic page mismatch: "+actual+" / "+page);
        Object active=UiGalleryBatch.call(context,"getActivePageComponent");
        if(active!=pages.get(page))throw new AssertionError("Runtime active component is not requested real page");
        chromaticContext=context;
        JsonObject trace=new JsonObject();trace.addProperty("clientTick",b.ticks);trace.addProperty("runtimePageBeforeSelection",before);
        trace.addProperty("runtimePageAfterSelection",actual);trace.addProperty("runtimePageCount",pages.size());trace.addProperty("contextIdentity",System.identityHashCode(context));
        if(proof.getAsJsonArray("nativePageDrawTrace").size()<64)proof.getAsJsonArray("nativePageDrawTrace").add(trace);
        pageSelected=true;
        draws++;
    }
    boolean requestMatches(Object backend)throws Exception {
        Object cache=UiGalleryBatch.field(backend,"tooltipCache"),request=UiGalleryBatch.field(cache,"request");
        return request!=null&&lines.equals(UiGalleryBatch.field(UiGalleryBatch.field(request,"tooltip"),"textLines"));
    }
    JsonArray componentText(Object component)throws Exception {
        JsonArray result=new JsonArray();
        if(UiGalleryBatch.type("com.slprime.chromatictooltips.component.SectionComponent").isInstance(component)){
            for(Object child:(List<?>)UiGalleryBatch.call(component,"getComponents"))for(JsonElement line:componentText(child))result.add(line);
        }else if(UiGalleryBatch.type("com.slprime.chromatictooltips.component.TextComponent").isInstance(component)){
            for(Object line:(List<?>)UiGalleryBatch.call(component,"getLines"))result.add(new JsonPrimitive(String.valueOf(line)));
        }
        return result;
    }
    boolean ready(){return !tooltip()||pageSelected&&draws>=2;}
    void beforeCapture()throws Exception {
        if(tooltip()){
            int page=UiGalleryBatch.integer(b.row,"nativeMachineTooltipPage",0);
            Object backend=UiGalleryBatch.type("com.slprime.chromatictooltips.TooltipHandler");
            Object context=UiGalleryBatch.call(backend,"getLastTooltipContext");
            if(context==null)throw new AssertionError("No real Chromatic context at capture");
            int actual=b.q.number(UiGalleryBatch.field(context,"currentPage"));
            List<?> pages=(List<?>)UiGalleryBatch.field(context,"pagedComponents");
            Object active=UiGalleryBatch.call(context,"getActivePageComponent");
            boolean deferredComplete=!Boolean.TRUE.equals(UiGalleryBatch.field(backend,"renderLastTooltip"));
            proof.addProperty("actualNativePageAtCapture",actual);
            proof.addProperty("runtimePagingBackend",backend.toString());proof.addProperty("runtimeRenderer",UiGalleryBatch.call(context,"getRenderer").getClass().getName());
            proof.addProperty("runtimePageCount",pages.size());proof.addProperty("nativeDeferredDrawComplete",deferredComplete);
            proof.add("selectedNativePageLinesAtCapture",componentText(active));
            b.q.check("v2013.tooltip.native-selected-page."+page,actual==page&&pages.size()>page&&active==pages.get(page)&&requestMatches(backend)&&deferredComplete,"Installed Chromatic real currentPage/active page identity and full registered request match; own deferred draw completed. Raw PNG visual review is separate.");
        }
        if(!active()||!UiGalleryBatch.str(b.row,"operation","").startsWith("build_"))return;
        JsonObject observed=b.v2011.observation;JsonArray frames=observed.getAsJsonArray("nativeSlotFrames");
        int[] items=new int[3],fluids=new int[3];int controller=0;boolean bounded=true;
        for(JsonElement e:frames){JsonObject f=e.getAsJsonObject();String group=f.has("group")?f.get("group").getAsString():"";int x=f.get("x").getAsInt(),y=f.get("y").getAsInt();
            if(group.equals("itemInputsInventory")||group.equals("fluidInputsInventory")){
                int row=(y-59)/36;boolean valid=y>=59&&(y-59)%36==0&&row<3;
                if(valid){if(group.equals("itemInputsInventory")){items[row]++;valid=x>=13&&x<=103&&(x-13)%18==0;}else{fluids[row]++;valid=x==121||x==139;}}
                bounded&=valid;
            }else if(group.equals("itemOutputsInventory")){controller++;bounded&=x==144&&y==2;}
        }
        bounded&=frames.size()==25&&controller==1;
        for(int r=0;r<3;r++)bounded&=items[r]==6&&fluids[r]==2;
        b.q.record("v2013.construction.native-three-rows."+b.row.get("name").getAsString(),bounded,"Actual initialized SlotWidget frames: 3 rows of six items/two fluids plus controller, 25 total; old 28-slot layout absent");
        observed.add("actualItemFramesByRow",new Gson().toJsonTree(items));observed.add("actualFluidFramesByRow",new Gson().toJsonTree(fluids));observed.addProperty("actualConstructionFrames",frames.size());
        JsonArray tooltips=new JsonArray();List<Object> all=new ArrayList<Object>();all.addAll(b.v2011.inputs);all.addAll(b.v2011.outputs);
        Set<String> groups=new HashSet<String>();
        for(Object positioned:all){String group=Boolean.TRUE.equals(UiGalleryBatch.call(positioned,"isInput"))?(Boolean.TRUE.equals(UiGalleryBatch.call(positioned,"isFluid"))?"fluid":"item"):"controller";if(groups.contains(group))continue;
            Object s=UiGalleryBatch.field(positioned,"item");List<String> tip=new ArrayList<String>((List<String>)UiGalleryBatch.call(UiGalleryBatch.type("codechicken.nei.guihook.GuiContainerManager"),"itemDisplayNameMultiline",s,b.q.gui,true));
            tip=(List<String>)UiGalleryBatch.call(b.v2011.handler,"handleItemTooltip",b.q.gui,s,tip,b.v2011.recipeIndex);
            JsonObject p=new JsonObject();p.addProperty("group",group);p.addProperty("stack",String.valueOf(UiGalleryBatch.call(s,"getDisplayName|func_82833_r")));p.add("nativeTooltipLines",new Gson().toJsonTree(tip));tooltips.add(p);groups.add(group);
        }
        observed.add("nativeConstructionTooltipSamples",tooltips);b.q.record("v2013.construction.native-tooltip-groups."+b.row.get("name").getAsString(),groups.contains("item")&&groups.contains("fluid")&&groups.contains("controller"),"Native tooltip builders for real cached nonempty item/fluid/controller stacks; no physical hover claim");
    }
    void afterCapture(JsonObject event){if(tooltip()){proof.addProperty("nativeTooltipDraws",draws);event.add("v2013NativeMachineTooltip",proof);}}
}
