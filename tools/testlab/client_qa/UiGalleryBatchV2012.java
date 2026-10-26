package audit;

import com.google.gson.*;
import java.util.*;

/** Registered GT ItemStacks and the native NEI tooltip renderer. */
final class UiGalleryBatchV2012 {
    final UiGalleryBatch b;
    Object stack;
    List<String> lines;
    JsonObject proof;
    int draws;
    UiGalleryBatchV2012(UiGalleryBatch owner){b=owner;}
    boolean active(){return b.row!=null&&b.row.has("machineTooltipEntry");}
    void configure(){stack=null;lines=null;proof=null;draws=0;}
    void navigate()throws Exception {
        if(!active())return;
        String entry=b.row.get("machineTooltipEntry").getAsString();
        int compared=0;
        if(entry.equals("__LONGEST__")){
            int best=-1;
            for(Object candidate:UiGalleryBatch.type(UiGalleryBatch.G+"common.api.enums.GTSRItemList").getEnumConstants()){
                if(!Boolean.TRUE.equals(UiGalleryBatch.call(candidate,"hasBeenSet")))continue;
                Object trial=UiGalleryBatch.call(candidate,"get",1L,new Object[0]);
                Object trialItem=UiGalleryBatch.call(trial,"getItem|func_77973_b");
                if(!UiGalleryBatch.type("gregtech.common.blocks.ItemMachines").isInstance(trialItem))continue;
                List<String> trialLines=new ArrayList<String>();trialLines.add(String.valueOf(UiGalleryBatch.call(trial,"getDisplayName|func_82833_r")));
                UiGalleryBatch.call(trialItem,"addInformation|func_77624_a",trial,b.player(),trialLines,false);
                compared++;Object trialFont=UiGalleryBatch.call(UiGalleryBatch.type("codechicken.nei.guihook.GuiContainerManager"),"getFontRenderer",trial);
                for(String line:trialLines){int width=((Number)UiGalleryBatch.call(trialFont,"getStringWidth|func_78256_a",line)).intValue();if(width>best){best=width;entry=String.valueOf(candidate);}}
            }
            if(compared==0)throw new AssertionError("No production machine tooltip candidates");
        }
        stack=UiGalleryBatch.call(UiGalleryBatch.field(UiGalleryBatch.type(UiGalleryBatch.G+"common.api.enums.GTSRItemList"),entry),"get",1L,new Object[0]);
        Object item=UiGalleryBatch.call(stack,"getItem|func_77973_b");
        lines=new ArrayList<String>();
        lines.add(String.valueOf(UiGalleryBatch.call(stack,"getDisplayName|func_82833_r")));
        UiGalleryBatch.call(item,"addInformation|func_77624_a",stack,b.player(),lines,false);
        List<String> original=new ArrayList<String>(lines);
        UiGalleryBatch.call(UiGalleryBatch.type(UiGalleryBatch.G+"client.tooltip.GTSRTooltipHandler"),"normalizeBrand",lines,
            UiGalleryBatch.call(UiGalleryBatch.type(UiGalleryBatch.G+"common.util.GTSRUtils"),"getAddedByLine"));
        proof=new JsonObject();proof.addProperty("registeredItem","GTSRItemList."+entry);
        if(compared>0){proof.addProperty("longestSelection","Maximum actual native font line width among registered GT machines");proof.addProperty("comparedRegisteredMachinePages",compared);}
        proof.addProperty("registeredItemClass",item.getClass().getName());
        proof.addProperty("itemMeta",((Number)UiGalleryBatch.call(stack,"getItemDamage|func_77960_j")).intValue());
        proof.addProperty("nativeDescription",true);
        proof.addProperty("physicalShift","NOT_RUN");proof.addProperty("physicalHover","NOT_RUN");
        proof.addProperty("scope","Real registered GT ItemStack -> ItemMachines.addInformation -> normalizeBrand -> native NEI drawPagedTooltip; no replacement layer or physical-input claim");
        proof.add("originalNativeLines",new Gson().toJsonTree(original));proof.add("renderedProductionLines",new Gson().toJsonTree(lines));
        Object font=UiGalleryBatch.call(UiGalleryBatch.type("codechicken.nei.guihook.GuiContainerManager"),"getFontRenderer",stack);
        int max=0;for(String line:lines)max=Math.max(max,((Number)UiGalleryBatch.call(font,"getStringWidth|func_78256_a",line)).intValue());
        proof.addProperty("lineCount",lines.size());proof.addProperty("maximumNativeFontWidth",max);
        proof.addProperty("nativeFontHeight",((Number)UiGalleryBatch.field(font,"FONT_HEIGHT|field_78288_b")).intValue());
        b.q.check("v2012.tooltip.real-production."+entry+".native",lines.size()>1,"Actual registered ItemMachines stack and native description; visual structure/color/length reviewed separately");
    }
    void drawTooltip()throws Exception {
        if(!active()||b.stage!=14||lines==null)return;
        Object manager=UiGalleryBatch.type("codechicken.nei.guihook.GuiContainerManager");
        UiGalleryBatch.call(manager,"drawPagedTooltip",UiGalleryBatch.call(manager,"getFontRenderer",stack),
            UiGalleryBatch.integer(b.row,"tooltipX",35),UiGalleryBatch.integer(b.row,"tooltipY",26),lines);
        draws++;
    }
    boolean ready(){return !active()||draws>=2;}
    void afterCapture(JsonObject event){if(active()){proof.addProperty("nativeTooltipDraws",draws);event.add("v2012MachineTooltip",proof);}}
}
