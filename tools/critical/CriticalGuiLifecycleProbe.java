package audit;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import com.google.gson.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.client.entity.EntityClientPlayerMP;
import sun.misc.Unsafe;

/** Runs after a complete client boot; never substitutes Minecraft, GT or NEI classes. */
@Mod(modid="criticalguilifecycle",name="Critical GUI lifecycle audit",version="1",dependencies="after:gtsr;after:NotEnoughItems")
public final class CriticalGuiLifecycleProbe {
    int ticks; boolean done;
    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        FMLCommonHandler.instance().bus().register(this);
    }
    static Field field(Class<?> c,String name)throws Exception {
        for(;c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    static Throwable cause(Throwable t){while(t.getCause()!=null)t=t.getCause();return t;}
    static JsonObject failure(Throwable t){JsonObject j=new JsonObject();j.addProperty("exception",t.toString());JsonArray stack=new JsonArray();for(StackTraceElement s:t.getStackTrace())if(s.getClassName().contains("GuiCritical")||s.getClassName().contains("GuiContainer")||s.getClassName().contains("GuiScreen"))stack.add(new JsonPrimitive(s.toString()));j.add("stack",stack);return j;}
    static Object invoke(Object target,String a,String b,Object...args)throws Exception {
        Class<?> type=target instanceof Class?(Class<?>)target:target.getClass();
        for(String name:new String[]{a,b})for(Method m:type.getMethods())if(m.getName().equals(name)&&m.getParameterTypes().length==args.length)return m.invoke(target instanceof Class?null:target,args);
        throw new NoSuchMethodException(a);
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
        if(done||e.phase!=TickEvent.Phase.END||++ticks<20)return;
        done=true;JsonObject result=new JsonObject();Minecraft mc;
        try{mc=(Minecraft)invoke(Minecraft.class,"getMinecraft","func_71410_x");}catch(Exception ex){throw new RuntimeException(ex);}
        try {
            Class<?> guiType=Class.forName("com.miaokatze.gtsr.client.critical.GuiCriticalController");
            result.addProperty("productionCodeSource",guiType.getProtectionDomain().getCodeSource().getLocation().toString());
            result.addProperty("neiCodeSource",Class.forName("codechicken.nei.guihook.GuiContainerManager").getProtectionDomain().getCodeSource().getLocation().toString());
            result.addProperty("scope","Real client boot and transformed GuiContainer; synthetic player inventory/controller only. No world placement, GT machine, networking or draw-screen acceptance.");
            Field uf=Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);Unsafe u=(Unsafe)uf.get(null);
            EntityClientPlayerMP player=(EntityClientPlayerMP)u.allocateInstance(EntityClientPlayerMP.class);
            try{field(player.getClass(),"inventory").set(player,new InventoryPlayer(player));}catch(NoSuchFieldException ex){field(player.getClass(),"field_71071_by").set(player,new InventoryPlayer(player));}
            net.minecraft.entity.DataWatcher watcher=new net.minecraft.entity.DataWatcher(player);
            invoke(watcher,"addObject","func_75682_a",6,Float.valueOf(20));
            try{field(player.getClass(),"dataWatcher").set(player,watcher);}catch(NoSuchFieldException ex){field(player.getClass(),"field_70180_af").set(player,watcher);}
            try{field(mc.getClass(),"thePlayer").set(mc,player);}catch(NoSuchFieldException ex){field(mc.getClass(),"field_71439_g").set(mc,player);}
            Class<?> tileType=Class.forName("com.miaokatze.gtsr.common.critical.TileEntityCriticalController");Object tile=tileType.newInstance();
            Class<?> containerType=Class.forName("com.miaokatze.gtsr.common.critical.ContainerCriticalController");
            Object container=containerType.getConstructor(net.minecraft.entity.player.EntityPlayer.class,tileType).newInstance(player,tile);
            GuiScreen gui=(GuiScreen)guiType.getConstructor(containerType).newInstance(container);
            Throwable openError=null,updateError=null,mouseError=null;
            try{invoke(mc,"displayGuiScreen","func_147108_a",gui);}catch(Throwable t){openError=cause(t);result.add("openFailure",failure(openError));}
            try{invoke(gui,"updateScreen","func_73876_c");}catch(Throwable t){updateError=cause(t);result.add("updateFailure",failure(updateError));}
            try{invoke(gui,"handleMouseInput","func_146274_d");}catch(Throwable t){mouseError=cause(t);result.add("mouseFailure",failure(mouseError));}
            boolean expected=Boolean.getBoolean("critical.gui.expectLegacyFailure");
            boolean root=openError instanceof NullPointerException&&openError.getMessage()!=null&&openError.getMessage().contains("manager");
            boolean ordering=false;if(openError!=null){boolean init=false,update=false;for(StackTraceElement s:openError.getStackTrace()){init|=s.getClassName().equals(guiType.getName())&&(s.getMethodName().equals("initGui")||s.getMethodName().equals("func_73866_w_"));update|=s.getClassName().equals(guiType.getName())&&(s.getMethodName().equals("updateScreen")||s.getMethodName().equals("func_73876_c"));}ordering=init&&update;}
            result.addProperty("openPassed",openError==null);result.addProperty("updatePassed",updateError==null);result.addProperty("mousePassed",mouseError==null);
            result.addProperty("legacyRootReproduced",root&&ordering);
            result.addProperty("status",expected?(root&&ordering?"EXPECTED_FAILURE_REPRODUCED":"FAIL"):(openError==null&&updateError==null&&mouseError==null?"PASS":"FAIL"));
        } catch(Throwable t){result.add("bootstrapFailure",failure(cause(t)));result.addProperty("status","FAIL");}
        try{Files.write(Paths.get(System.getProperty("critical.gui.receipt")),new GsonBuilder().setPrettyPrinting().create().toJson(result).getBytes(StandardCharsets.UTF_8));}catch(Exception ex){ex.printStackTrace();}
        // Shutdown immediately: the deliberately half-open legacy GUI must never receive another normal tick.
        try{try{field(mc.getClass(),"thePlayer").set(mc,null);}catch(NoSuchFieldException ex){field(mc.getClass(),"field_71439_g").set(mc,null);}try{field(mc.getClass(),"currentScreen").set(mc,null);}catch(NoSuchFieldException ex){field(mc.getClass(),"field_71462_r").set(mc,null);}}catch(Exception ex){ex.printStackTrace();}
        try{invoke(mc,"shutdown","func_71400_g");}catch(Exception ex){throw new RuntimeException(ex);}
    }
}
