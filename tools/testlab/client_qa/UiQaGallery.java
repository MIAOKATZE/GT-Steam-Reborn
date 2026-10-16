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
}
