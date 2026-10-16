package audit;

import com.google.gson.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Explicit owned-fixture steps, executed after the real TCDS pilot in the same game process. */
final class UiQaScript {
    final Mui2ClientQa q;
    final JsonArray steps;
    final Map<String,Object> variables=new HashMap<String,Object>();
    final Map<String,JsonObject> inventories=new HashMap<String,JsonObject>();
    final JsonArray events=new JsonArray();
    int index,wait;
    volatile JsonObject pending;
    volatile Throwable failure;
    boolean dispatched; String group="pilot"; int recovery;
    UiQaScript(Mui2ClientQa owner,Path plan)throws Exception {
        q=owner;
        steps=new JsonParser().parse(new String(Files.readAllBytes(plan),StandardCharsets.UTF_8)).getAsJsonArray();
        q.receipt.addProperty("scriptPlan",plan.toString());
        q.receipt.add("scriptEvents",events);
    }
    static String string(JsonObject o,String key,String fallback){return o.has(key)?o.get(key).getAsString():fallback;}
    void tick()throws Exception {
        if(recovery>0){if(q.request!=0)return;if(++recovery<12)return;recovery=0;}
        try{advance();}catch(Throwable error){
            JsonObject failed=new JsonObject();failed.addProperty("group",group);failed.addProperty("step",index);failed.addProperty("status","FAIL");failed.addProperty("failure",error.toString());events.add(failed);
            JsonObject check=new JsonObject();check.addProperty("name","script.group."+group);check.addProperty("pass",false);check.addProperty("detail",error.toString());q.checks.add(check);error.printStackTrace();
            int next=index+1;while(next<steps.size()&&!string(steps.get(next).getAsJsonObject(),"op","").equals("group"))next++;
            if(next>=steps.size())throw new RuntimeException("Final script group failed",error);
            try{if(capture())Mui2ClientQa.call(q.screen,"close");}catch(Throwable ignored){}
            index=next;wait=0;dispatched=false;failure=null;pending=new JsonObject();pending.addProperty("op","invoke");JsonObject ref=new JsonObject();ref.addProperty("ref","serverPlayer");pending.add("target",ref);pending.addProperty("method","closeScreen|func_71053_j");q.request=100;recovery=1;
        }
    }
    void advance()throws Exception {
        if(failure!=null)throw new RuntimeException("Server script step "+index,failure);
        if(index>=steps.size()){q.finish(null);return;}
        JsonObject step=steps.get(index).getAsJsonObject();q.phase=1000+index;
        if(string(step,"op","").equals("wait")){
            if(++wait<step.get("ticks").getAsInt())return;
            complete(step);return;
        }
        if(string(step,"side","client").equals("server")){
            if(!dispatched){pending=step;dispatched=true;q.request=100;return;}
            if(q.request!=0){if(++wait>stepTimeout(step))throw new AssertionError("Server script timed out "+step);return;}
            complete(step);return;
        }
        if(string(step,"op","").equals("expectGuiClass")){Object current=Mui2ClientQa.field(q.mc,"currentScreen|field_71462_r");String name=current==null?"null":current.getClass().getName();String expected=string(step,"class",string(step,"contains",""));if(current==null||!name.contains(expected)){if(++wait>stepTimeout(step))throw new AssertionError("Expected legacy GUI class "+expected+" actual="+name);return;}}
        if(string(step,"op","").equals("expectScreen")){
            if(!capture()||!string(step,"panel","").equals(String.valueOf(Mui2ClientQa.call(q.panel,"getName")))){
                if(++wait>stepTimeout(step))throw new AssertionError("Expected panel "+step+" current="+q.gui);
                return;
            }
        }
        execute(step);complete(step);
    }
    int stepTimeout(JsonObject step){return step.has("timeoutTicks")?step.get("timeoutTicks").getAsInt():200;}
    void complete(JsonObject step){JsonObject event=new JsonObject();event.addProperty("index",index);event.addProperty("name",string(step,"name",string(step,"op","")));event.addProperty("status","EXECUTED");events.add(event);System.out.println("MUI2_QA_SCRIPT "+event);index++;wait=0;dispatched=false;pending=null;}
    void server(){try{execute(pending);}catch(Throwable t){failure=t;t.printStackTrace();}}
    Object target(String name)throws Exception {
        if(name.startsWith("@")){
            String type=name.substring(1);
            if(!type.startsWith("com.miaokatze.")&&!type.startsWith("gregtech.")&&!type.startsWith("net.minecraft.")&&!type.startsWith("net.minecraftforge.")&&!type.startsWith("com.cleanroommc.")&&!type.startsWith("cpw.mods.")&&!type.startsWith("gtneioreplugin."))throw new IllegalArgumentException("Fixture class outside game/mod scope");
            return Mui2ClientQa.type(type);
        }
        if(variables.containsKey(name))return variables.get(name);
        if(name.equals("mc"))return q.mc;
        if(name.equals("player"))return Mui2ClientQa.field(q.mc,"thePlayer|field_71439_g");
        if(name.equals("serverPlayer"))return q.serverPlayer;
        if(name.equals("server"))return cpw.mods.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        if(name.equals("world"))return Mui2ClientQa.field(q.serverPlayer,"worldObj|field_70170_p");
        if(name.equals("clientWorld"))return Mui2ClientQa.field(q.mc,"theWorld|field_71441_e");
        if(name.equals("machine"))return q.machine;
        if(name.equals("host"))return q.host;
        if(name.equals("gui"))return q.gui;
        if(name.equals("screen"))return q.screen;
        if(name.equals("panel"))return q.panel;
        if(name.equals("container"))return Mui2ClientQa.field(q.serverPlayer,"openContainer|field_71070_bA");
        throw new IllegalArgumentException("Unknown fixture reference "+name);
    }
    Object eval(JsonElement value)throws Exception {
        if(value==null||value.isJsonNull())return null;
        if(value.isJsonPrimitive()){
            JsonPrimitive p=value.getAsJsonPrimitive();
            if(p.isBoolean())return p.getAsBoolean();
            if(p.isNumber()){String s=p.getAsString();if(s.contains("."))return Double.valueOf(s);return Integer.valueOf(s);}
            return p.getAsString();
        }
        JsonObject o=value.getAsJsonObject();
        if(o.has("index"))return Array.get(eval(o.get("target")),o.get("index").getAsInt());
        if(o.has("widget")){capture();return q.find(q.panel,o.get("widget").getAsString());}
        if(o.has("ref"))return target(o.get("ref").getAsString());
        if(o.has("long"))return o.get("long").getAsLong();
        if(o.has("int"))return o.get("int").getAsInt();
        if(o.has("double"))return o.get("double").getAsDouble();
        if(o.has("float"))return o.get("float").getAsFloat();
        if(o.has("enum"))return Enum.valueOf((Class)target("@"+o.get("enum").getAsString()),o.get("name").getAsString());
        if(o.has("field"))return Mui2ClientQa.field(eval(o.get("target")),o.get("field").getAsString());
        if(o.has("call"))return Mui2ClientQa.call(eval(o.get("target")),o.get("call").getAsString(),args(o));
        if(o.has("array")){
            Class<?> component=o.get("array").getAsString().equals("java.lang.String")?String.class:(Class<?>)target("@"+o.get("array").getAsString());
            JsonArray entries=o.getAsJsonArray("values");Object array=Array.newInstance(component,entries.size());for(int i=0;i<entries.size();i++)Array.set(array,i,eval(entries.get(i)));return array;
        }
        if(o.has("new")){
            Class<?> type=(Class<?>)target("@"+o.get("new").getAsString());Object[] args=args(o);
            for(Constructor<?> c:type.getConstructors())if(c.getParameterTypes().length==args.length){try{Object[] converted=args.clone();for(int i=0;i<converted.length;i++)converted[i]=Mui2ClientQa.adapt(c.getParameterTypes()[i],args[i]);return c.newInstance(converted);}catch(IllegalArgumentException ignored){}}
            throw new NoSuchMethodException("Constructor "+o);
        }
        throw new IllegalArgumentException("Unknown fixture expression "+o);
    }
    Object[] args(JsonObject o)throws Exception {JsonArray a=o.has("args")?o.getAsJsonArray("args"):new JsonArray();Object[] result=new Object[a.size()];for(int i=0;i<a.size();i++)result[i]=eval(a.get(i));return result;}
    boolean capture()throws Exception {
        q.gui=Mui2ClientQa.field(q.mc,"currentScreen|field_71462_r");
        if(q.gui==null||!Mui2ClientQa.type("com.cleanroommc.modularui.api.IMuiScreen").isInstance(q.gui))return false;
        q.screen=Mui2ClientQa.call(q.gui,"getScreen");q.panel=Mui2ClientQa.call(q.screen,"getMainPanel");return true;
    }
    void execute(JsonObject s)throws Exception {
        String op=string(s,"op","");Object result=null;
        if(op.equals("group")){group=s.get("name").getAsString();}
        else if(op.equals("record")){JsonObject record=new JsonObject();record.addProperty("name",s.get("name").getAsString());record.addProperty("value",String.valueOf(eval(s.get("value"))));events.add(record);}
        else if(op.equals("geometry")){capture();Object widget=q.find(q.panel,s.get("widget").getAsString());if(widget==null)throw new AssertionError("Geometry widget missing");JsonObject event=new JsonObject();event.addProperty("geometry",s.get("name").getAsString());JsonObject tree=geometry(widget,s.has("depth")?s.get("depth").getAsInt():2);event.add("tree",tree);Object parent=Mui2ClientQa.call(widget,"getParent");if(parent!=null)event.add("parent",geometry(parent,0));events.add(event);if(s.has("assertTextFit")&&s.get("assertTextFit").getAsBoolean())verifyTextGeometry(tree,s.get("name").getAsString(),s.has("assertLastVisible")&&s.get("assertLastVisible").getAsBoolean());}
        else if(op.equals("wandAir")){Object player=target("player"),movement=Mui2ClientQa.field(player,"movementInput|field_71158_b"),old=Mui2ClientQa.field(q.mc,"objectMouseOver|field_71476_x");boolean sneak=(Boolean)Mui2ClientQa.field(movement,"sneak|field_78899_d");try{Mui2ClientQa.set(movement,"sneak|field_78899_d",true);Mui2ClientQa.set(q.mc,"objectMouseOver|field_71476_x",null);Object held=Mui2ClientQa.call(player,"getHeldItem|func_70694_bm");q.check("fixture.actual-client-wand-held",held!=null&&Mui2ClientQa.call(held,"getItem|func_77973_b").getClass().getName().endsWith("SingularityTuningWand"),"Actual client held="+(held==null?"null":Mui2ClientQa.call(held,"getItem|func_77973_b").getClass().getName()));q.check("fixture.actual-client-sneaking",Boolean.TRUE.equals(Mui2ClientQa.call(player,"isSneaking|func_70093_af")),"Actual client movement/sneak readback before normal Item.onItemRightClick");Mui2ClientQa.call(Mui2ClientQa.call(held,"getItem|func_77973_b"),"onItemRightClick|func_77659_a",held,target("clientWorld"),player);}finally{Mui2ClientQa.set(movement,"sneak|field_78899_d",sneak);Mui2ClientQa.set(q.mc,"objectMouseOver|field_71476_x",old);}}
        else if(op.equals("scroll")){
            capture();Object widget=q.find(q.panel,s.get("widget").getAsString());int[] pos=q.screenPoint(widget);Object context=Mui2ClientQa.call(q.screen,"getContext");
            Mui2ClientQa.call(context,"updateState",pos[0],pos[1],0f);Mui2ClientQa.call(q.screen,"onFrameUpdate");
            String direction=string(s,"direction","DOWN");if(!direction.equals("DOWN")&&!direction.equals("UP"))throw new IllegalArgumentException("Invalid wheel direction");
            int notches=s.has("notches")?s.get("notches").getAsInt():1;if(notches<1||notches>100)throw new IllegalArgumentException("Wheel notches outside 1..100");
            for(int i=0;i<notches;i++){Mui2ClientQa.set(context,"mouseWheel",direction.equals("DOWN")?-120:120);Mui2ClientQa.call(q.screen,"onMouseScroll",Enum.valueOf((Class)Mui2ClientQa.type("com.cleanroommc.modularui.api.UpOrDown"),direction),1);}
            Mui2ClientQa.set(context,"mouseWheel",0);
        }
        else if(op.equals("drag")){
            capture();Object widget=q.find(q.panel,s.get("widget").getAsString()),area=Mui2ClientQa.call(widget,"getArea"),context=Mui2ClientQa.call(q.screen,"getContext");int[] center=q.screenPoint(widget);int width=q.number(Mui2ClientQa.field(area,"width")),height=q.number(Mui2ClientQa.field(area,"height"));
            double[] fractions={s.get("fromX").getAsDouble(),s.get("fromY").getAsDouble(),s.get("toX").getAsDouble(),s.get("toY").getAsDouble()};for(double f:fractions)if(f<0||f>=1)throw new IllegalArgumentException("Drag fractions require 0<=f<1");
            int startX=center[0]-width/2+(int)(width*fractions[0]),startY=center[1]-height/2+(int)(height*fractions[1]),endX=center[0]-width/2+(int)(width*fractions[2]),endY=center[1]-height/2+(int)(height*fractions[3]);
            Mui2ClientQa.call(context,"updateState",startX,startY,0f);Mui2ClientQa.set(context,"mouseButton",0);Mui2ClientQa.call(q.screen,"onFrameUpdate");if(!(Boolean)Mui2ClientQa.call(q.screen,"onMouseInputPre",0,true))Mui2ClientQa.call(q.screen,"onMousePressed",0);
            for(int n=1;n<=5;n++){Mui2ClientQa.call(context,"updateState",startX+(endX-startX)*n/5,startY+(endY-startY)*n/5,0f);Mui2ClientQa.call(q.screen,"onFrameUpdate");Mui2ClientQa.call(q.screen,"onMouseDrag",0,(long)n*10);}
            if(!(Boolean)Mui2ClientQa.call(q.screen,"onMouseInputPre",0,false))Mui2ClientQa.call(q.screen,"onMouseRelease",0);
        }
        else if(op.equals("invoke"))result=Mui2ClientQa.call(eval(s.get("target")),s.get("method").getAsString(),args(s));
        else if(op.equals("evaluate"))result=eval(s.get("value"));
        else if(op.equals("stack"))result=makeStack(s);
        else if(op.equals("clearPlayer")){Object inv=Mui2ClientQa.field(q.serverPlayer,"inventory|field_71071_by");for(int i=0;i<((Number)Mui2ClientQa.call(inv,"getSizeInventory|func_70302_i_")).intValue();i++)Mui2ClientQa.call(inv,"setInventorySlotContents|func_70299_a",i,null);Mui2ClientQa.call(inv,"setItemStack|func_70437_b",(Object)null);}
        else if(op.equals("placeBlock")){Object world=target("world"),block=eval(s.get("block"));int x=s.get("x").getAsInt(),y=s.get("y").getAsInt(),z=s.get("z").getAsInt();Mui2ClientQa.call(Mui2ClientQa.call(world,"getChunkProvider|func_72863_F"),"loadChunk|func_73158_c",x>>4,z>>4);if(!(Boolean)Mui2ClientQa.call(world,"isAirBlock|func_147437_c",x,y,z))throw new AssertionError("Fixture target not air");Mui2ClientQa.call(world,"setBlock|func_147465_d",x,y,z,block,0,3);Mui2ClientQa.call(q.serverPlayer,"setPositionAndUpdate|func_70634_a",x+.5,y+1d,z+2.5);result=Mui2ClientQa.call(world,"getTileEntity|func_147438_o",x,y,z);}
        else if(op.equals("setField")){Mui2ClientQa.set(eval(s.get("target")),s.get("field").getAsString(),eval(s.get("value")));}
        else if(op.equals("placeGt"))result=placeGt(s);
        else if(op.equals("findItem")){Object registry=Mui2ClientQa.field(Mui2ClientQa.type("net.minecraft.item.Item"),"itemRegistry|field_150901_e");for(Object item:(Iterable<?>)registry)if(item.getClass().getName().equals(s.get("class").getAsString())){result=item;break;}if(result==null)throw new AssertionError("Missing registered item class "+s);}
        else if(op.equals("snapshotNbt")){Object tag=Mui2ClientQa.type("net.minecraft.nbt.NBTTagCompound").newInstance();Mui2ClientQa.call(eval(s.get("target")),string(s,"method","writeToNBT|func_145841_b"),tag);result=tag;JsonObject event=new JsonObject();event.addProperty("nbtSnapshot",s.get("save").getAsString());event.addProperty("canonical",nbt(tag));events.add(event);}
        else if(op.equals("assertNbtEqual")){q.check(s.get("name").getAsString(),nbt(eval(s.get("actual"))).equals(nbt(eval(s.get("expected")))),"Canonical complete NBT comparison");}
        else if(op.equals("expectGuiClass")){q.gui=Mui2ClientQa.field(q.mc,"currentScreen|field_71462_r");String name=q.gui==null?"null":q.gui.getClass().getName();q.check(s.get("name").getAsString(),q.gui!=null&&name.contains(string(s,"class",string(s,"contains",""))),"Actual GuiScreen class="+name);}
        else if(op.equals("legacyKey")){q.gui=Mui2ClientQa.field(q.mc,"currentScreen|field_71462_r");Mui2ClientQa.call(q.gui,"keyTyped|func_73869_a",(char)0,s.get("code").getAsInt());}
        else if(op.equals("legacyText")){
            q.gui=Mui2ClientQa.field(q.mc,"currentScreen|field_71462_r");Object input=Mui2ClientQa.field(q.gui,string(s,"field","renameField"));int x=((Number)Mui2ClientQa.field(input,"xPosition|field_146209_f")).intValue()+5,y=((Number)Mui2ClientQa.field(input,"yPosition|field_146210_g")).intValue()+5;
            Mui2ClientQa.call(q.gui,"mouseClicked|func_73864_a",x,y,0);Mui2ClientQa.call(q.gui,"keyTyped|func_73869_a",(char)0,207);for(int i=0;i<20;i++)Mui2ClientQa.call(q.gui,"keyTyped|func_73869_a",'\b',14);
            String text=s.get("text").getAsString();Object event=Mui2ClientQa.type("me.eigenraven.lwjgl3ify.api.InputEvents$TextEvent").getConstructor(String.class).newInstance(text);Mui2ClientQa.call(Mui2ClientQa.type("me.eigenraven.lwjgl3ify.api.InputEvents"),"injectTextEvent",event);if(!text.isEmpty())Mui2ClientQa.call(q.gui,"keyTyped|func_73869_a",text.charAt(0),0);
            q.check("legacy.real-text-input",text.equals(String.valueOf(Mui2ClientQa.call(input,"getText|func_146179_b"))),"Actual lwjgl3ify TextEvent queue + legacy GuiScreen.keyTyped -> GuiTextField buffer consumption; text="+Mui2ClientQa.call(input,"getText|func_146179_b")+" focused="+Mui2ClientQa.call(input,"isFocused|func_146206_l"));
        }
        else if(op.equals("legacyButton")){q.gui=Mui2ClientQa.field(q.mc,"currentScreen|field_71462_r");Object button=null;for(Object b:(List<?>)Mui2ClientQa.field(q.gui,"buttonList|field_146292_n"))if(((Number)Mui2ClientQa.field(b,"id|field_146127_k")).intValue()==s.get("id").getAsInt()){button=b;break;}if(button==null)throw new AssertionError("Legacy button missing");int x=((Number)Mui2ClientQa.field(button,"xPosition|field_146128_h")).intValue()+((Number)Mui2ClientQa.field(button,"width|field_146120_f")).intValue()/2,y=((Number)Mui2ClientQa.field(button,"yPosition|field_146129_i")).intValue()+((Number)Mui2ClientQa.field(button,"height|field_146121_g")).intValue()/2;Mui2ClientQa.call(q.gui,"mouseClicked|func_73864_a",x,y,0);Mui2ClientQa.call(q.gui,"mouseMovedOrUp|func_146286_b",x,y,0);}
        else if(op.equals("expectScreen")){
            q.check(string(s,"name","script.screen"),capture()&&s.get("panel").getAsString().equals(String.valueOf(Mui2ClientQa.call(q.panel,"getName"))),"Actual MUI2 wrapping screen and panel");
            dumpScreen(string(s,"name","screen"));
            if(s.has("slots"))q.check("script.actual-slot-count",((List<?>)Mui2ClientQa.field(Mui2ClientQa.call(q.screen,"getContainer"),"inventorySlots|field_75151_b")).size()==s.get("slots").getAsInt(),"Actual client container slots="+((List<?>)Mui2ClientQa.field(Mui2ClientQa.call(q.screen,"getContainer"),"inventorySlots|field_75151_b")).size()+" expected="+s.get("slots"));
        }
        else if(op.equals("click")){capture();Object w=q.find(q.panel,s.get("widget").getAsString());if(w==null)throw new AssertionError("Widget missing "+s);q.click(w);}
        else if(op.equals("text")){capture();q.input=q.find(q.panel,s.get("widget").getAsString());if(q.input==null)throw new AssertionError("Text field missing "+s);q.edit(s.get("text").getAsString());}
        else if(op.equals("key")){capture();q.key((char)0,s.get("code").getAsInt());}
        else if(op.equals("screenshot")){capture();if(s.has("hover"))q.pendingTooltip=q.find(q.panel,s.get("hover").getAsString());q.shot(s.get("name").getAsString());}
        else if(op.equals("bounds")){capture();try{q.checkBounds(q.panel);}catch(AssertionError failure){if(!s.has("soft")||!s.get("soft").getAsBoolean())throw failure;}}
        else if(op.equals("windowClick")){
            Object player=target("player"),container=Mui2ClientQa.field(player,"openContainer|field_71070_bA"),controller=Mui2ClientQa.field(q.mc,"playerController|field_71442_b");
            result=Mui2ClientQa.call(controller,"windowClick|func_78753_a",Mui2ClientQa.field(container,"windowId|field_75152_c"),s.get("slot").getAsInt(),s.get("button").getAsInt(),s.get("mode").getAsInt(),player);
        }
        else if(op.equals("assert")){
            Object actual=eval(s.get("actual")),expected=eval(s.get("expected"));
            boolean equal=Objects.equals(String.valueOf(actual),String.valueOf(expected));if(string(s,"relation","=").equals(">"))equal=((Number)actual).doubleValue()>((Number)expected).doubleValue();q.check(s.get("name").getAsString(),equal,"actual="+actual+" expected="+expected);
        }
        else if(op.equals("inventorySnapshot")){
            JsonObject snapshot=inventory(eval(s.get("target")));inventories.put(s.get("name").getAsString(),snapshot);JsonObject event=new JsonObject();event.addProperty("inventory",s.get("name").getAsString());event.add("snapshot",snapshot);events.add(event);
        }
        else if(op.equals("inventoryConserved")){
            JsonObject before=inventories.get(s.get("before").getAsString()),after=inventories.get(s.get("after").getAsString());
            q.check(s.get("name").getAsString(),before!=null&&after!=null&&before.get("totals").equals(after.get("totals")),"Canonical item id/damage/full NBT totals, including server cursor; before="+(before==null?null:before.get("totals"))+" after="+(after==null?null:after.get("totals")));
        }
        else if(op.equals("resize")){
            Object settings=Mui2ClientQa.field(q.mc,"gameSettings|field_71474_y");Mui2ClientQa.set(settings,"guiScale|field_74335_Z",s.get("scale").getAsInt());
            Mui2ClientQa.call(q.mc,"resize|func_71370_a",s.get("width").getAsInt(),s.get("height").getAsInt());
        }
        else if(op.equals("reloadResources"))Mui2ClientQa.call(q.mc,"refreshResources|func_110436_a");
        else if(!op.equals("inventoryLimits"))throw new IllegalArgumentException("Unknown owned QA operation "+s);
        if(op.equals("inventoryLimits")){JsonObject snapshot=inventories.get(s.get("snapshot").getAsString());int from=s.get("from").getAsInt(),to=s.get("to").getAsInt(),maximum=s.get("maximum").getAsInt();boolean valid=true;for(int i=from;i<=to;i++)valid&=snapshot.getAsJsonArray("slots").get(i).getAsJsonObject().get("count").getAsInt()<=maximum;q.check(s.get("name").getAsString(),valid,"Actual server container slot range "+from+".."+to+" <= "+maximum);}
        if(s.has("save"))variables.put(s.get("save").getAsString(),result);
    }
    Object makeStack(JsonObject s)throws Exception {
        Object item;
        if(s.has("item"))item=eval(s.get("item"));
        else{Object registry=Mui2ClientQa.field(Mui2ClientQa.type("net.minecraft.item.Item"),"itemRegistry|field_150901_e");item=Mui2ClientQa.call(registry,"getObject|func_82594_a",s.get("id").getAsString());}
        if(item==null)throw new AssertionError("Fixture item missing "+s);
        Object stack=null;for(Constructor<?> c:Mui2ClientQa.type("net.minecraft.item.ItemStack").getConstructors())if(c.getParameterTypes().length==3)try{stack=c.newInstance(item,s.has("count")?s.get("count").getAsInt():1,s.has("damage")?s.get("damage").getAsInt():0);break;}catch(IllegalArgumentException ignored){}
        if(stack==null)throw new AssertionError("Fixture ItemStack constructor missing");
        if(s.has("mark")){Object tag=Mui2ClientQa.type("net.minecraft.nbt.NBTTagCompound").newInstance();Mui2ClientQa.call(tag,"setString|func_74778_a","uiqa",s.get("mark").getAsString());Mui2ClientQa.call(stack,"setTagCompound|func_77982_d",tag);}
        return stack;
    }
    Object placeGt(JsonObject s)throws Exception {
        Object world=target("world"),player=target("serverPlayer"),prototype=null;String className=s.get("class").getAsString();
        for(Object p:(Object[])Mui2ClientQa.field(Mui2ClientQa.type("gregtech.api.GregTechAPI"),"METATILEENTITIES"))if(p!=null&&p.getClass().getName().equals(className)){prototype=p;break;}
        if(prototype==null)throw new AssertionError("Missing GT prototype "+className);
        int x=s.get("x").getAsInt(),y=s.get("y").getAsInt(),z=s.get("z").getAsInt();
        Mui2ClientQa.call(Mui2ClientQa.call(world,"getChunkProvider|func_72863_F"),"loadChunk|func_73158_c",x>>4,z>>4);
        Object stone=Mui2ClientQa.field(Mui2ClientQa.type("net.minecraft.init.Blocks"),"stone|field_150348_b");for(int px=x-1;px<=x+2;px++)for(int pz=z-1;pz<=z+4;pz++)Mui2ClientQa.call(world,"setBlock|func_147465_d",px,y-1,pz,stone,0,3);
        Mui2ClientQa.call(player,"setPositionAndUpdate|func_70634_a",x+.5,y+1d,z+2.5);Object stack=Mui2ClientQa.call(prototype,"getStackForm",1L);
        if(!(Boolean)Mui2ClientQa.call(Mui2ClientQa.call(stack,"getItem|func_77973_b"),"placeBlockAt",stack,player,world,x,y,z,1,.5f,.5f,.5f,0))throw new AssertionError("GT placement refused "+s);
        Object host=Mui2ClientQa.call(world,"getTileEntity|func_147438_o",x,y,z),machine=Mui2ClientQa.call(host,"getMetaTileEntity");String name=s.get("name").getAsString();variables.put(name+"Host",host);variables.put(name,machine);return machine;
    }
    JsonObject geometry(Object widget,int depth)throws Exception {
        JsonObject snapshot=new JsonObject();snapshot.addProperty("name",String.valueOf(Mui2ClientQa.call(widget,"getName")));snapshot.addProperty("class",widget.getClass().getName());Object area=Mui2ClientQa.call(widget,"getArea");
        for(String field:new String[]{"x","y","rx","ry","width","height"})snapshot.addProperty(field,((Number)Mui2ClientQa.field(area,field)).intValue());
        if(Mui2ClientQa.type("com.cleanroommc.modularui.widgets.TextWidget").isInstance(widget)){
            Object font=Mui2ClientQa.field(q.mc,"fontRenderer|field_71466_p");String text=String.valueOf(Mui2ClientQa.call(Mui2ClientQa.call(widget,"getKey"),"getFormatted"));float scale=((Number)Mui2ClientQa.call(widget,"getScale")).floatValue();int wrapWidth=(int)Math.floor((snapshot.get("width").getAsInt()+scale)/scale);
            List<?> lines=(List<?>)Mui2ClientQa.call(font,"listFormattedStringToWidth|func_78271_c",text,wrapWidth);int fontHeight=q.number(Mui2ClientQa.field(font,"FONT_HEIGHT|field_78288_b"));int defaultHeight=q.number(Mui2ClientQa.call(widget,"getDefaultHeight"));JsonObject metrics=new JsonObject();
            metrics.addProperty("nativeDefaultHeight",defaultHeight);metrics.addProperty("nativeFontWrapWidth",wrapWidth);metrics.addProperty("nativeFontLines",lines.size());metrics.addProperty("nativeFontHeight",fontHeight);metrics.addProperty("nativeFontWrappedHeight",(int)Math.ceil(lines.size()*fontHeight*scale));metrics.addProperty("scale",scale);metrics.addProperty("unicodeFlag",(Boolean)Mui2ClientQa.call(font,"getUnicodeFlag|func_82883_a"));metrics.addProperty("text",text);metrics.addProperty("screenWidth",q.number(Mui2ClientQa.field(Mui2ClientQa.call(Mui2ClientQa.call(q.screen,"getContext"),"getScreenArea"),"width")));snapshot.add("textMetrics",metrics);
        }
        try{Object scrollArea=Mui2ClientQa.call(widget,"getScrollArea");for(String axis:new String[]{"X","Y"}){Object data=Mui2ClientQa.call(scrollArea,"getScroll"+axis);if(data!=null){JsonObject scroll=new JsonObject();scroll.addProperty("scroll",q.number(Mui2ClientQa.call(data,"getScroll")));scroll.addProperty("size",q.number(Mui2ClientQa.call(data,"getScrollSize")));snapshot.add("scroll"+axis,scroll);}}}catch(NoSuchMethodException noScroll){}
        if(depth>0){JsonArray children=new JsonArray();for(Object child:(List<?>)Mui2ClientQa.call(widget,"getChildren"))children.add(geometry(child,depth-1));snapshot.add("children",children);}return snapshot;
    }
    void verifyTextGeometry(JsonObject tree,String name,boolean lastVisible){
        JsonArray children=tree.getAsJsonArray("children");int end=Integer.MIN_VALUE,index=0;JsonObject last=null;
        for(JsonElement element:children){JsonObject child=element.getAsJsonObject();if(!child.has("textMetrics"))continue;JsonObject metrics=child.getAsJsonObject("textMetrics");int height=child.get("height").getAsInt(),y=child.get("y").getAsInt(),measured=metrics.get("nativeDefaultHeight").getAsInt(),nativeHeight=metrics.get("nativeFontWrappedHeight").getAsInt();
            q.check(name+".paragraph"+index+".native-font-rectangle",height>=measured&&height>=nativeHeight,"Actual rectangle="+height+" MUI2 default="+measured+" actual FontRenderer wrapped="+nativeHeight);if(index>0)q.check(name+".paragraph"+index+".no-overlap",y>=end,"Actual y="+y+" previous rectangle end="+end);end=y+height;last=child;index++;}
        if(lastVisible){int scroll=tree.has("scrollY")?tree.getAsJsonObject("scrollY").get("scroll").getAsInt():0,top=tree.get("y").getAsInt(),bottom=top+tree.get("height").getAsInt(),lastTop=last==null?Integer.MIN_VALUE:last.get("y").getAsInt()-scroll,lastBottom=last==null?Integer.MAX_VALUE:lastTop+last.get("height").getAsInt();q.check(name+".last-paragraph-visible",last!=null&&lastTop>=top&&lastBottom<=bottom,"Actual transformed last paragraph="+lastTop+".."+lastBottom+" viewport="+top+".."+bottom+" scroll="+scroll);}
    }
    void dumpScreen(String name)throws Exception {
        JsonObject event=new JsonObject();event.addProperty("screen",name);event.addProperty("guiClass",q.gui.getClass().getName());event.addProperty("panel",String.valueOf(Mui2ClientQa.call(q.panel,"getName")));event.addProperty("widgets",q.countWidgets(q.panel));variables.put(name+"WidgetCount",q.countWidgets(q.panel));
        if(Mui2ClientQa.type("net.minecraft.client.gui.inventory.GuiContainer").isInstance(q.gui)){Object container=Mui2ClientQa.call(q.screen,"getContainer"),psm=Mui2ClientQa.call(Mui2ClientQa.call(q.screen,"getSyncManager"),"getMainPSM");Map<?,?> handlers=(Map<?,?>)Mui2ClientQa.field(psm,"syncHandlers");JsonObject types=new JsonObject();for(Map.Entry<?,?> entry:handlers.entrySet())types.addProperty(String.valueOf(entry.getKey()),entry.getValue().getClass().getName());event.add("handlerTypes",types);event.addProperty("containerSlots",((List<?>)Mui2ClientQa.field(container,"inventorySlots|field_75151_b")).size());}else{event.addProperty("container","client-only");}
        events.add(event);
    }
    JsonObject inventory(Object container)throws Exception {
        JsonArray slots=new JsonArray();TreeMap<String,Integer> totals=new TreeMap<String,Integer>();
        for(Object slot:(List<?>)Mui2ClientQa.field(container,"inventorySlots|field_75151_b")){Object stack=Mui2ClientQa.call(slot,"getStack|func_75211_c");JsonObject item=stack(stack,totals);item.addProperty("containerIndex",((Number)Mui2ClientQa.field(slot,"slotNumber|field_75222_d")).intValue());item.addProperty("inventoryIndex",((Number)Mui2ClientQa.field(slot,"slotIndex|field_75225_a")).intValue());slots.add(item);}
        JsonArray drops=new JsonArray();Object world=target("world");for(Object entity:(List<?>)Mui2ClientQa.field(world,"loadedEntityList|field_72996_f")){if(Mui2ClientQa.type("net.minecraft.entity.item.EntityItem").isInstance(entity)&&!Boolean.TRUE.equals(Mui2ClientQa.field(entity,"isDead|field_70128_L"))){JsonObject dropped=stack(Mui2ClientQa.call(entity,"getEntityItem|func_92059_d"),totals);dropped.addProperty("entityId",((Number)Mui2ClientQa.call(entity,"getEntityId|func_145782_y")).intValue());drops.add(dropped);}}
        Object inv=Mui2ClientQa.field(q.serverPlayer,"inventory|field_71071_by");JsonObject cursor=stack(Mui2ClientQa.call(inv,"getItemStack|func_70445_o"),totals);JsonObject total=new JsonObject();for(Map.Entry<String,Integer> entry:totals.entrySet())total.addProperty(entry.getKey(),entry.getValue());JsonObject out=new JsonObject();out.add("slots",slots);out.add("cursor",cursor);out.add("groundEntityItems",drops);out.addProperty("groundAccountingScope","All live EntityItem stacks in this same fresh owned fixture world at the actual server tick; no unloaded/out-of-world entity claim");out.add("totals",total);return out;
    }
    JsonObject stack(Object stack,Map<String,Integer> totals)throws Exception {
        JsonObject out=new JsonObject();if(stack==null){out.addProperty("count",0);return out;}
        Object item=Mui2ClientQa.call(stack,"getItem|func_77973_b"),registry=Mui2ClientQa.field(Mui2ClientQa.type("net.minecraft.item.Item"),"itemRegistry|field_150901_e");String id=String.valueOf(Mui2ClientQa.call(registry,"getNameForObject|func_148750_c",item));int count=((Number)Mui2ClientQa.field(stack,"stackSize|field_77994_a")).intValue(),damage=((Number)Mui2ClientQa.call(stack,"getItemDamage|func_77960_j")).intValue();String tag=nbt(Mui2ClientQa.call(stack,"getTagCompound|func_77978_p"));out.addProperty("item",id);out.addProperty("damage",damage);out.addProperty("count",count);out.addProperty("nbt",tag);String key=id+":"+damage+":"+tag;Integer old=totals.get(key);totals.put(key,(old==null?0:old)+count);return out;
    }
    String nbt(Object tag)throws Exception {
        if(tag==null)return "null";String name=tag.getClass().getSimpleName();
        if(name.equals("NBTTagCompound")){TreeSet<String> keys=new TreeSet<String>((Set<String>)Mui2ClientQa.call(tag,"getKeySet|func_150296_c"));StringBuilder b=new StringBuilder("{");for(String key:keys)b.append(key).append(':').append(nbt(Mui2ClientQa.call(tag,"getTag|func_74781_a",key))).append(';');return b.append('}').toString();}
        if(name.equals("NBTTagList")){StringBuilder b=new StringBuilder("[");for(Object child:(List<?>)Mui2ClientQa.field(tag,"tagList|field_74747_a"))b.append(nbt(child)).append(';');return b.append(']').toString();}
        if(name.equals("NBTTagByteArray"))return name+Arrays.toString((byte[])Mui2ClientQa.call(tag,"getByteArray|func_150292_c"));
        if(name.equals("NBTTagIntArray"))return name+Arrays.toString((int[])Mui2ClientQa.call(tag,"getIntArray|func_150302_c"));
        return name+":"+tag.toString();
    }
}
