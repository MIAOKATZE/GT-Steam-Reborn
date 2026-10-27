package audit;

import com.google.gson.*;
import java.util.*;

/** Visual-only, owned single-controller fixtures. Real production right-click and real GL screenshots. */
final class UiQaGallery {
    final UiQaScript s;
    final Mui2ClientQa q;
    final Map<String,Object[]> positions=new LinkedHashMap<String,Object[]>();
    UiQaGallery(UiQaScript script){s=script;q=s.q;}
    void track(Object world,int x,int y,int z,Object tile)throws Exception {
        int dim=q.number(Mui2ClientQa.field(Mui2ClientQa.field(world,"provider|field_73011_w"),"dimensionId|field_76574_g"));
        positions.put(dim+":"+x+","+y+","+z,new Object[]{world,x,y,z,tile});
    }
    boolean ready()throws Exception {
        q.gui=Mui2ClientQa.field(q.mc,"currentScreen|field_71462_r");
        if(q.gui==null)return false;
        String type=q.gui.getClass().getName();
        return !type.contains("GuiChat")&&!type.contains("GuiIngameMenu")&&!type.contains("GuiDownloadTerrain");
    }
    void execute(JsonObject step)throws Exception {
        String op=step.get("op").getAsString();
        if(op.equals("galleryPrepare"))prepare(step);
        else if(op.equals("galleryOpen")){
            Object machine=s.target("galleryMachine"),host=s.target("galleryHost");
            Object direction=Enum.valueOf((Class)Mui2ClientQa.type("net.minecraftforge.common.util.ForgeDirection"),"UP");
            Object accepted;
            if(Boolean.TRUE.equals(s.variables.get("galleryTerminal"))&&machine.getClass().getName().endsWith("MTECriticalMachine")){
                Object held=Mui2ClientQa.call(q.serverPlayer,"getHeldItem|func_70694_bm");
                accepted=Mui2ClientQa.call(Mui2ClientQa.call(held,"getItem|func_77973_b"),"onItemUseFirst",held,q.serverPlayer,s.target("world"),16,70,48,1,.5f,.5f,.5f);
            }else accepted=Mui2ClientQa.call(host,"onRightclick",q.serverPlayer,direction,.5f,.5f,.5f);
            JsonObject e=new JsonObject();e.addProperty("galleryRoute",step.get("id").getAsString());e.addProperty("accepted",String.valueOf(accepted));e.addProperty("machineClass",machine.getClass().getName());e.addProperty("entry","Production BaseMetaTileEntity.onRightclick with actual EntityPlayerMP; held terminal only for terminal entries");s.events.add(e);
        }
        else if(op.equals("galleryExpect"))q.check("gallery.open."+step.get("id").getAsString(),ready(),"Actual Minecraft currentScreen="+(q.gui==null?"null":q.gui.getClass().getName()));
        else if(op.equals("galleryCapture"))capture(step);
        else if(op.equals("galleryLoomSeed"))loomSeed(step);
        else if(op.equals("galleryLoomCapture"))loomCapture(step);
        else if(op.equals("galleryLoomTerminalCheck"))loomTerminalCheck(step);
        else if(op.equals("galleryItemOrderCheck"))new UiQaGalleryItemOrder(s).check();
        else if(op.equals("galleryLoomFreezeCheck")){
            Object host=s.target("galleryHost"),world=s.target("world");
            boolean absent=!((List<?>)Mui2ClientQa.field(world,"loadedTileEntityList|field_147482_g")).contains(host)
                &&!((List<?>)Mui2ClientQa.field(world,"addedTileEntityList|field_147484_a")).contains(host);
            long timer=((Number)Mui2ClientQa.call(host,"getTimer")).longValue();
            q.check("loom.fixture-tick-frozen."+s.group,absent&&timer==((Number)s.variables.get("loomHostTimer")).longValue(),"Only owned host is absent from both tick lists; actual GT timer unchanged="+timer);
        }
        else if(op.equals("galleryLoomCloseChild")){
            if(!s.capture())throw new AssertionError("Native parent screen missing");
            Object child=s.variables.get("loomChildPanel");
            Mui2ClientQa.call(child,"closeIfOpen");
            q.check("loom.native-parent-remains",Boolean.TRUE.equals(Mui2ClientQa.call(q.panel,"isOpen"))&&q.find(q.panel,"critical_native_construction")!=null,"Read-only child close retains the actual GT parent and construction entry");
        }
        else if(op.equals("galleryScrollNative")){
            if(!s.capture())throw new AssertionError("Native MUI2 screen missing");
            Object list=findList(q.panel);
            if(list==null)throw new AssertionError("Native terminal list missing");
            Mui2ClientQa.call(list,"name","gallery_native_terminal_locator");
            JsonObject scroll=new JsonObject();scroll.addProperty("op","scroll");scroll.addProperty("widget","gallery_native_terminal_locator");scroll.addProperty("notches",100);scroll.addProperty("direction","DOWN");s.execute(scroll);
        }
        else if(op.equals("galleryRich")){
            Object machine=s.target("galleryMachine"),state=Mui2ClientQa.call(machine,"getCriticalState");
            String message="【受控视觉样本】施工材料与能量正在核对；长状态文本用于检查中文换行、滚动和高位读数。此控制器没有形成巨构，没有运行配方、施工或付费动作。待核对材料：高压蒸汽输入仓、临界机械外壳、装饰插件与多组物品输出；数据仅用于本次离线UI审查。";
            Mui2ClientQa.call(state,"setRuntimeBatch",123456,987654,2147483647L,message);
            Object inv=Mui2ClientQa.field(q.serverPlayer,"inventory|field_71071_by");
            for(int slot=9;slot<36;slot++){
                JsonObject stack=new JsonObject();stack.addProperty("id",slot%3==0?"minecraft:diamond":slot%3==1?"minecraft:gold_ingot":"minecraft:iron_ingot");stack.addProperty("count",64);Object item=s.makeStack(stack);
                Mui2ClientQa.call(item,"setStackDisplayName|func_151001_c","视觉样本材料 "+slot+" · 长名称/数量展示");Mui2ClientQa.call(inv,"setInventorySlotContents|func_70299_a",slot,item);
            }
            JsonObject e=new JsonObject();e.addProperty("visualFixture","Critical long status + representative runtime numbers + filled owned player backpack; state remains IDLE/unformed; no construction/recipe execution");e.addProperty("kind",String.valueOf(Mui2ClientQa.call(state,"getKind")));s.events.add(e);
        }
        else if(op.equals("galleryClose")){
            q.pendingTooltip=null;
            Object player=Mui2ClientQa.field(q.mc,"thePlayer|field_71439_g");
            if(player!=null)Mui2ClientQa.call(player,"closeScreen|func_71053_j");
            q.gui=null;q.screen=null;q.panel=null;
        }
        else if(op.equals("galleryCleanup")){
            Mui2ClientQa.call(q.serverPlayer,"closeScreen|func_71053_j");
            int removed=0,alreadyAbsent=0;
            for(Object[] p:positions.values()){
                Object world=p[0];int x=(Integer)p[1],y=(Integer)p[2],z=(Integer)p[3];Object current=Mui2ClientQa.call(world,"getTileEntity|func_147438_o",x,y,z);
                if(current==null){alreadyAbsent++;continue;}
                if(current!=p[4])throw new AssertionError("Owned cleanup tile identity changed at "+x+","+y+","+z);
                Mui2ClientQa.call(world,"removeTileEntity|func_147475_p",x,y,z);Mui2ClientQa.call(world,"setBlockToAir|func_147468_f",x,y,z);removed++;
            }
            positions.clear();q.check("gallery.owned-single-controller-cleanup",positions.isEmpty(),"Only tracked gallery fixture positions cleared; no multiblock formation or business action requested");
            JsonObject cleanup=new JsonObject();cleanup.addProperty("galleryCleanupRemovedTiles",removed);cleanup.addProperty("alreadyAbsent",alreadyAbsent);s.events.add(cleanup);
        }
        else throw new IllegalArgumentException("Unknown gallery op "+op);
    }
    Object findList(Object widget)throws Exception {
        if(Mui2ClientQa.type("com.cleanroommc.modularui.widgets.ListWidget").isInstance(widget))return widget;
        for(Object child:(List<?>)Mui2ClientQa.call(widget,"getChildren")){Object found=findList(child);if(found!=null)return found;}
        return null;
    }
    void prepare(JsonObject step)throws Exception {
        Object world=s.target("world"),player=q.serverPlayer;
        Mui2ClientQa.call(player,"closeScreen|func_71053_j");
        Object inv=Mui2ClientQa.field(player,"inventory|field_71071_by");
        for(int i=0;i<9;i++)Mui2ClientQa.call(inv,"setInventorySlotContents|func_70299_a",i,null);
        Mui2ClientQa.set(inv,"currentItem|field_70461_c",0);
        if(step.has("terminal")&&step.get("terminal").getAsBoolean()){
            Object terminal=Mui2ClientQa.call(Mui2ClientQa.field(Mui2ClientQa.type("com.miaokatze.gtsr.common.api.enums.GTSRItemList"),"HubTerminal"),"get",1);
            Mui2ClientQa.call(inv,"setInventorySlotContents|func_70299_a",0,terminal);
        }
        String kind=step.get("kind").getAsString();Object value=Enum.valueOf((Class)Mui2ClientQa.type("com.miaokatze.gtsr.common.api.enums.MetaTileEntityID"),kind);
        int id=q.number(Mui2ClientQa.field(value,"ID"));Object prototype=((Object[])Mui2ClientQa.field(Mui2ClientQa.type("gregtech.api.GregTechAPI"),"METATILEENTITIES"))[id];
        if(prototype==null)throw new AssertionError("Unregistered production ID "+id);
        int x=16,y=70,z=48;
        Mui2ClientQa.call(Mui2ClientQa.call(world,"getChunkProvider|func_72863_F"),"loadChunk|func_73158_c",x>>4,z>>4);
        Mui2ClientQa.call(world,"removeTileEntity|func_147475_p",x,y,z);Mui2ClientQa.call(world,"setBlockToAir|func_147468_f",x,y,z);
        Object stone=Mui2ClientQa.field(Mui2ClientQa.type("net.minecraft.init.Blocks"),"stone|field_150348_b");
        for(int px=x-1;px<=x+1;px++)for(int pz=z-1;pz<=z+3;pz++)Mui2ClientQa.call(world,"setBlock|func_147465_d",px,y-1,pz,stone,0,3);
        Mui2ClientQa.call(player,"setPositionAndUpdate|func_70634_a",x+.5,y+1d,z+2.5);
        Object stack=Mui2ClientQa.call(prototype,"getStackForm",1L);
        if(!(Boolean)Mui2ClientQa.call(Mui2ClientQa.call(stack,"getItem|func_77973_b"),"placeBlockAt",stack,player,world,x,y,z,1,.5f,.5f,.5f,0))throw new AssertionError("Production ItemMachines placement refused "+kind);
        Object host=Mui2ClientQa.call(world,"getTileEntity|func_147438_o",x,y,z),machine=Mui2ClientQa.call(host,"getMetaTileEntity");
        track(world,x,y,z,host);
        s.variables.put("galleryHost",host);s.variables.put("galleryMachine",machine);s.variables.put("galleryTerminal",step.has("terminal")&&step.get("terminal").getAsBoolean());
        if(step.has("boundFixture")&&step.get("boundFixture").getAsBoolean()){
            Mui2ClientQa.set(machine,"mBound",true);
            try{Mui2ClientQa.set(machine,"mNextRegistrationTick",Long.MAX_VALUE);}catch(NoSuchFieldException unused){}
        }
        JsonObject e=new JsonObject();e.addProperty("galleryFixture",kind);e.addProperty("metaTileId",id);e.addProperty("machineClass",machine.getClass().getName());e.addProperty("scope","Single registered controller only; no formed structure, recipes, construction or paid action");s.events.add(e);
    }
    void capture(JsonObject step)throws Exception {
        if(!ready())throw new AssertionError("No GUI to capture "+step);
        JsonObject e=new JsonObject();e.addProperty("galleryCapture",step.get("name").getAsString());e.addProperty("group",s.group);e.addProperty("guiClass",q.gui.getClass().getName());
        e.addProperty("displayWidth",q.number(Mui2ClientQa.field(q.mc,"displayWidth|field_71443_c")));e.addProperty("displayHeight",q.number(Mui2ClientQa.field(q.mc,"displayHeight|field_71440_d")));
        if(s.capture()){
            e.addProperty("panel",String.valueOf(Mui2ClientQa.call(q.panel,"getName")));e.add("geometry",s.geometry(q.panel,1));
            Object a=Mui2ClientQa.call(q.panel,"getArea"),canvas=Mui2ClientQa.call(Mui2ClientQa.call(q.screen,"getContext"),"getScreenArea");
            int x=q.number(Mui2ClientQa.field(a,"x")),y=q.number(Mui2ClientQa.field(a,"y")),w=q.number(Mui2ClientQa.field(a,"width")),h=q.number(Mui2ClientQa.field(a,"height"));
            boolean fits=x>=0&&y>=0&&x+w<=q.number(Mui2ClientQa.field(canvas,"width"))&&y+h<=q.number(Mui2ClientQa.field(canvas,"height"));
            e.addProperty("panelWithinCanvas",fits);q.record("gallery.geometry."+step.get("name").getAsString(),fits,"Actual root panel="+x+","+y+" "+w+"x"+h);
        }else e.addProperty("framework","MUI1/legacy actual GUI; geometry diagnostic not available");
        s.events.add(e);q.shot(step.get("name").getAsString());
    }

    /** Explicit visual fixture on the owned server journal; no client sync cache is written. */
    void loomSeed(JsonObject step)throws Exception {
        if(!step.has("schematicOnly")&&!Boolean.TRUE.equals(s.variables.get("loomRecipeVerified"))){s.variables.put("loomRecipeVerified",true);loomRecipeCheck();}
        Object machine=s.target("galleryMachine"),host=s.target("galleryHost"),state=Mui2ClientQa.call(machine,"getCriticalState");
        String variant=step.get("state").getAsString();
        if(!machine.getClass().getName().endsWith("MTECriticalMachine")||!Boolean.TRUE.equals(Mui2ClientQa.call(machine,"isKindSelector")))throw new AssertionError("Owned loom fixture required");
        // Freeze only this owned fixture's tile ticking; preserve normal identity and permission checks.
        Object world=s.target("world");
        ((List<?>)Mui2ClientQa.field(world,"loadedTileEntityList|field_147482_g")).remove(host);
        ((List<?>)Mui2ClientQa.field(world,"addedTileEntityList|field_147484_a")).remove(host);
        Mui2ClientQa.set(state,"loomJournalOwner",true);
        Mui2ClientQa.set(state,"foundationRequired",true);
        Mui2ClientQa.set(state,"state",variant.equals("complete")?2:1);
        Mui2ClientQa.set(state,"constructionTimeVersion",1);
        Mui2ClientQa.set(state,"stage",variant.equals("base")?2:variant.equals("body")?4:variant.equals("equipment")||variant.equals("complete")?5:0);
        Mui2ClientQa.set(state,"cursor",0);
        Mui2ClientQa.call(Mui2ClientQa.field(Mui2ClientQa.field(state,"constructionLedger"),"progress"),"advance",200);
        Mui2ClientQa.call(host,variant.equals("paused")?"disableWorking":"enableWorking");
        List<?> chunks=(List<?>)Mui2ClientQa.call(state,"getFoundationChunks");
        if(chunks.isEmpty()||chunks.size()>4096)throw new AssertionError("Invalid actual chunk footprint");
        boolean pouring=variant.equals("pouring")||variant.equals("paused");
        int remaining=variant.equals("curing")?720000:0;
        JsonArray footprint=new JsonArray();
        for(int i=0;i<chunks.size();i++){
            Object chunk=chunks.get(i),foundation=Mui2ClientQa.call(state,"foundation",i);
            Object tag=Mui2ClientQa.type("net.minecraft.nbt.NBTTagCompound").newInstance();
            Mui2ClientQa.call(tag,"setInteger|func_74768_a","cells",pouring?(i==0?7:0):16);
            Mui2ClientQa.call(tag,"setInteger|func_74768_a","progress",pouring&&i==0?160:0);
            Mui2ClientQa.call(tag,"setInteger|func_74768_a","curing",pouring?1440000:remaining);
            Mui2ClientQa.call(tag,"setBoolean|func_74757_a","funded",pouring&&i==0);
            Mui2ClientQa.call(foundation,"read",tag);
            JsonObject entry=new JsonObject();entry.addProperty("x",q.number(Mui2ClientQa.field(chunk,"chunkXPos|field_77276_a")));entry.addProperty("z",q.number(Mui2ClientQa.field(chunk,"chunkZPos|field_77275_b")));footprint.add(entry);
        }
        if(variant.equals("base")||variant.equals("body")||variant.equals("equipment"))Mui2ClientQa.set(state,"cursor",q.number(Mui2ClientQa.call(state,"getJobTotal"))/2);
        Mui2ClientQa.set(state,"status","[受控视觉样本] "+variant+"；真实服务端账本同步，未执行施工或付费。");
        q.check("loom.fixture-normal-access."+s.group,Boolean.TRUE.equals(Mui2ClientQa.call(state,"canUse",q.serverPlayer))&&!Boolean.TRUE.equals(Mui2ClientQa.call(host,"isInvalid|func_145837_r")),"Owned tile removed only from server tick lists; real chunk tile identity and normal production permission retained");
        s.variables.put("loomHostTimer",Mui2ClientQa.call(host,"getTimer"));
        Object sample=Mui2ClientQa.call(Mui2ClientQa.type("com.miaokatze.gtsr.common.gui.CriticalConstructionSnapshot"),"capture",state);
        int[] values=((int[])Mui2ClientQa.field(sample,"values")).clone();
        s.variables.put("loomExpected",values);
        s.variables.put("loomVariant",variant);
        JsonObject e=new JsonObject();e.addProperty("loomServerFixture",variant);e.add("actualChunkFootprint",footprint);e.addProperty("serverSnapshot",Arrays.toString(values));e.addProperty("scope","Owned real server ledger + actual production S2C sync; owned host removed from tick lists only, normal permission retained; no client cache writes or formed structure/payment/work");s.events.add(e);
    }

    /** Observe the production recipe after normal mod initialization; do not invoke registration. */
    void loomRecipeCheck()throws Exception {
        Object concreteStack=Mui2ClientQa.call(Mui2ClientQa.type("com.miaokatze.gtsr.common.critical.CriticalFoundationMaterials"),"fluid",1);
        Object concrete=Mui2ClientQa.call(concreteStack,"getFluid");
        q.check("loom.production-concrete-registered",concrete!=null,"Actual CriticalFoundationMaterials.fluid(1) after normal production initialization");
        Object mixer=Mui2ClientQa.field(Mui2ClientQa.type("gregtech.api.recipe.RecipeMaps"),"mixerRecipes");
        List<Object> matches=new ArrayList<Object>();
        for(Object recipe:(Collection<?>)Mui2ClientQa.call(mixer,"getAllRecipes"))for(Object output:(Object[])Mui2ClientQa.field(recipe,"mFluidOutputs"))
            if(output!=null&&concrete==Mui2ClientQa.call(output,"getFluid")){matches.add(recipe);break;}
        q.check("loom.production-concrete-single-mixer-recipe",matches.size()==1,"Actual registered mixer matches="+matches.size()+"; registration not called by QA");
        Object recipe=matches.get(0),materials=Mui2ClientQa.type("gregtech.api.enums.Materials"),prefixes=Mui2ClientQa.type("gregtech.api.enums.OrePrefixes");
        Object fiber=Mui2ClientQa.call(Mui2ClientQa.type("gregtech.api.util.GTModHandler"),"getIC2Item","carbonFiber",1L);
        Object wire=Mui2ClientQa.call(Mui2ClientQa.type("gregtech.api.util.GTOreDictUnificator"),"get",Mui2ClientQa.field(prefixes,"wireFine"),Mui2ClientQa.field(materials,"TungstenSteel"),1L);
        Object dust=Mui2ClientQa.call(Mui2ClientQa.type("gregtech.api.util.GTOreDictUnificator"),"get",Mui2ClientQa.field(prefixes,"dust"),Mui2ClientQa.field(materials,"SiliconDioxide"),1L);
        int fiberCount=0,wireCount=0,dustCount=0,otherItems=0;
        Object[] inputs=(Object[])Mui2ClientQa.field(recipe,"mInputs");
        for(Object input:inputs){
            if(input==null)continue;
            int count=q.number(Mui2ClientQa.field(input,"stackSize|field_77994_a"));
            if(Boolean.TRUE.equals(Mui2ClientQa.call(input,"isItemEqual|func_77969_a",fiber)))fiberCount+=count;
            else if(Boolean.TRUE.equals(Mui2ClientQa.call(input,"isItemEqual|func_77969_a",wire)))wireCount+=count;
            else if(Boolean.TRUE.equals(Mui2ClientQa.call(input,"isItemEqual|func_77969_a",dust)))dustCount+=count;
            else otherItems+=count;
        }
        Object wet=Mui2ClientQa.call(Mui2ClientQa.field(materials,"Concrete"),"getMolten",9000L);
        int wetAmount=0,otherFluids=0,outputAmount=0;
        for(Object input:(Object[])Mui2ClientQa.field(recipe,"mFluidInputs")){
            if(input==null)continue;
            int amount=q.number(Mui2ClientQa.field(input,"amount"));
            if(Boolean.TRUE.equals(Mui2ClientQa.call(input,"isFluidEqual",wet)))wetAmount+=amount;else otherFluids+=amount;
        }
        Object[] outputs=(Object[])Mui2ClientQa.field(recipe,"mFluidOutputs");
        for(Object output:outputs)if(output!=null&&concrete==Mui2ClientQa.call(output,"getFluid"))outputAmount+=q.number(Mui2ClientQa.field(output,"amount"));
        int duration=q.number(Mui2ClientQa.field(recipe,"mDuration")),eut=q.number(Mui2ClientQa.field(recipe,"mEUt"));
        long iv=((Number)Mui2ClientQa.field(Mui2ClientQa.type("gregtech.api.enums.TierEU"),"RECIPE_IV")).longValue();
        boolean ordered=inputs.length==3&&inputs[0]!=null&&inputs[1]!=null&&inputs[2]!=null
            &&Boolean.TRUE.equals(Mui2ClientQa.call(inputs[0],"isItemEqual|func_77969_a",fiber))
            &&Boolean.TRUE.equals(Mui2ClientQa.call(inputs[1],"isItemEqual|func_77969_a",wire))
            &&Boolean.TRUE.equals(Mui2ClientQa.call(inputs[2],"isItemEqual|func_77969_a",dust));
        JsonObject observation=new JsonObject();observation.addProperty("productionFoundationMixerRecipe",true);observation.addProperty("rawCarbonFiber",fiberCount);observation.addProperty("wireFineTungstenSteel",wireCount);observation.addProperty("dustSiliconDioxide",dustCount);observation.addProperty("itemInputOrderCorrect",ordered);observation.addProperty("otherItems",otherItems);observation.addProperty("wetConcrete",wetAmount);observation.addProperty("otherInputFluids",otherFluids);observation.addProperty("concreteOutput",outputAmount);observation.addProperty("durationTicks",duration);observation.addProperty("eut",eut);observation.addProperty("requiredRecipeIV",iv);s.events.add(observation);
        q.record("loom.production-concrete-recipe-bill",ordered&&fiberCount==32&&wireCount==128&&dustCount==64&&otherItems==0&&wetAmount==9000&&otherFluids==0&&outputAmount==16000&&outputs.length==1&&duration==6000&&eut==iv,observation.toString());
        Object rod=Mui2ClientQa.call(Mui2ClientQa.type("gregtech.api.util.GTOreDictUnificator"),"get",Mui2ClientQa.field(prefixes,"stickLong"),Mui2ClientQa.field(materials,"Neutronium"),1L);
        Object hull=Mui2ClientQa.call(Mui2ClientQa.field(Mui2ClientQa.type("gregtech.api.enums.ItemList"),"Hull_MAX"),"get",1L,new Object[0]);
        int rods=0,hulls=0,otherCosts=0;
        for(Object cost:(Object[])Mui2ClientQa.call(Mui2ClientQa.type("com.miaokatze.gtsr.common.critical.CriticalFoundationMaterials"),"items")){
            if(cost==null)continue;
            int count=q.number(Mui2ClientQa.field(cost,"stackSize|field_77994_a"));
            if(Boolean.TRUE.equals(Mui2ClientQa.call(cost,"isItemEqual|func_77969_a",rod)))rods+=count;
            else if(Boolean.TRUE.equals(Mui2ClientQa.call(cost,"isItemEqual|func_77969_a",hull)))hulls+=count;
            else otherCosts+=count;
        }
        JsonObject bill=new JsonObject();bill.addProperty("productionFoundationCellBill",true);bill.addProperty("longNeutroniumRods",rods);bill.addProperty("uhvHulls",hulls);bill.addProperty("otherItems",otherCosts);s.events.add(bill);
        q.record("loom.production-foundation-cell-bill",rods==512&&hulls==32&&otherCosts==0,bill.toString());
    }

    void loomTerminalCheck(JsonObject step)throws Exception {
        if(!s.capture())throw new AssertionError("Actual terminal MUI2 screen missing");
        String name=step.get("name").getAsString();int expectedPage=step.get("page").getAsInt();
        Object pages=q.find(q.panel,"critical_pages");
        List<?> children=(List<?>)Mui2ClientQa.call(pages,"getPages");
        q.check("loom.three-terminal-tabs."+name,children.size()==3&&q.find(q.panel,"critical_tab_0")!=null&&q.find(q.panel,"critical_tab_1")!=null&&q.find(q.panel,"critical_tab_2")!=null&&q.find(q.panel,"critical_tab_3")==null,"Exactly three actual pages/tabs, no requirements tab");
        q.check("loom.selected-terminal-page."+name,q.number(Mui2ClientQa.call(pages,"getCurrentPageIndex"))==expectedPage,"Actual tab dispatch readback, expected page="+expectedPage);
        q.check("loom.terminal-page-order."+name,"critical_page_construction".equals(String.valueOf(Mui2ClientQa.call(children.get(0),"getName")))&&"critical_page_control".equals(String.valueOf(Mui2ClientQa.call(children.get(1),"getName")))&&"critical_page_inventory".equals(String.valueOf(Mui2ClientQa.call(children.get(2),"getName")))&&q.find(q.panel,"critical_construction_requirements")==null,"Construction monitor / configuration and control / input and output; requirements tree removed");
        for(String action:new String[]{"build","dismantle","repair","power","kind","steam_mode"})
            q.check("loom.retained-control."+action+"."+name,q.find(q.panel,"critical_action_"+action)!=null,"Existing production guarded action retained; no business command executed");
        Object container=Mui2ClientQa.call(q.screen,"getContainer"),player=Mui2ClientQa.field(q.mc,"thePlayer|field_71439_g"),inventory=Mui2ClientQa.field(player,"inventory|field_71071_by");
        int playerSlots=0;Class<?> itemHandlerSlot=Mui2ClientQa.type("com.cleanroommc.modularui.utils.item.SlotItemHandler"),modularSlot=Mui2ClientQa.type("com.cleanroommc.modularui.widgets.slot.ModularSlot");
        for(Object slot:(List<?>)Mui2ClientQa.field(container,"inventorySlots|field_75151_b")){
            // MUI2 SlotItemHandler deliberately uses a dummy vanilla inventory; resolve the exact wrapper overload.
            boolean owned=itemHandlerSlot.isInstance(slot)?modularSlot.getMethod("getPlayerSlotPlayer",itemHandlerSlot).invoke(null,slot)==player:Mui2ClientQa.field(slot,"inventory|field_75224_c")==inventory;
            if(owned)playerSlots++;
        }
        q.check("loom.player-backpack-retained."+name,playerSlots==36,"Actual container player inventory slots="+playerSlots);
        q.checkBounds(q.panel);
        JsonObject event=new JsonObject();event.addProperty("terminalThreePageCheck",name);event.addProperty("page",expectedPage);event.add("geometry",s.geometry(children.get(expectedPage),3));s.events.add(event);
        if(step.has("screenshot")&&step.get("screenshot").getAsBoolean())q.shot(name);
    }

    void loomCapture(JsonObject step)throws Exception {
        if(!s.capture())throw new AssertionError("Actual MUI2 screen missing");
        boolean nativeRoute=step.get("route").getAsString().equals("native");
        Object target=q.panel,msm=Mui2ClientQa.call(q.screen,"getSyncManager"),psm;
        if(nativeRoute){
            target=null;
            for(Object open:(List<?>)Mui2ClientQa.call(Mui2ClientQa.call(q.screen,"getPanelManager"),"getOpenPanels"))
                if("critical_native_construction".equals(String.valueOf(Mui2ClientQa.call(open,"getName"))))target=open;
            if(target==null)throw new AssertionError("Read-only native construction subpanel missing");
            s.variables.put("loomChildPanel",target);
            psm=Mui2ClientQa.call(msm,"getPanelSyncManager","critical_native_construction");
        }else{
            psm=Mui2ClientQa.call(msm,"getMainPSM");
            Object pages=q.find(target,"critical_pages");
            q.check("loom.selected-construction-page."+step.get("name").getAsString(),q.number(Mui2ClientQa.call(pages,"getCurrentPageIndex"))==0,"Construction monitor is the actual initial page 0");
        }
        q.check("loom.initialized-psm."+step.get("name").getAsString(),Boolean.TRUE.equals(Mui2ClientQa.call(psm,"isInitialised")),"Actual open route PSM");
        Object handler=Mui2ClientQa.call(psm,"findSyncHandler","critical.constructionView"),sample=Mui2ClientQa.call(handler,"getValue");
        int[] values=(int[])Mui2ClientQa.field(sample,"values"),expected=(int[])s.variables.get("loomExpected");
        q.check("loom.actual-s2c-sample."+step.get("name").getAsString(),Arrays.equals(expected,values),"Actual client snapshot="+Arrays.toString(values)+" server="+Arrays.toString(expected));
        q.check("loom.preview-present."+step.get("name").getAsString(),q.find(target,"critical_construction_preview")!=null,"Production preview drawable attached");
        q.checkBounds(target);
        JsonObject e=new JsonObject();e.addProperty("loomCapture",step.get("name").getAsString());e.addProperty("route",step.get("route").getAsString());e.addProperty("panel",String.valueOf(Mui2ClientQa.call(target,"getName")));e.addProperty("snapshot",Arrays.toString(values));e.add("geometry",s.geometry(target,3));s.events.add(e);
        if(!step.has("screenshot")||step.get("screenshot").getAsBoolean())q.shot(step.get("name").getAsString());
    }
}
