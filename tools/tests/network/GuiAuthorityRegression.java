package com.miaokatze.gtsr.tests;

import static com.miaokatze.gtsr.tests.PacketAuthorityRegression.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.entity.player.*;
import net.minecraft.network.*;
import net.minecraft.world.*;
import com.cleanroommc.modularui.api.RecipeViewerSettings;
import com.cleanroommc.modularui.network.*;
import com.cleanroommc.modularui.network.packets.PacketSyncHandler;
import com.cleanroommc.modularui.screen.*;
import com.cleanroommc.modularui.value.sync.*;
import com.cleanroommc.modularui.utils.MouseData;
import com.miaokatze.gtsr.common.gui.MTECriticalMachineTerminalGui;
import com.miaokatze.gtsr.common.gui.mui2.Mui2ServerActions;
import com.miaokatze.gtsr.common.critical.CriticalMachineState;
import cpw.mods.fml.common.*;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.Unpooled;

/** Native decoded packet routing and retained-manager lifecycle, with isolated native player/world state. */
public final class GuiAuthorityRegression {
    static int mutations;
    static ModularSyncManager manager;
    static ModularContainer container;
    static PanelSyncManager panel;
    static int networkId;
    static InteractionSyncHandler handler, second;
    static MTECriticalMachineTerminalGui gui;
    static class RecordingState extends CriticalMachineState {
        @Override public boolean canUse(EntityPlayer p) { return true; }
        @Override public String getDisplayStatusKey() { return "fixture"; }
    }
    static void setup() throws Exception {
        setupServer(); Mui2ServerActions.clear(); mutations=0;
        set(world,World.class,"provider",new WorldProviderSurface());
        manager=new ModularSyncManager(false); panel=new PanelSyncManager(manager,true);
        container=new ModularContainer();
        set(container,ModularContainer.class,"player",player);
        set(container,ModularContainer.class,"syncManager",manager);
        UISettings settings=new UISettings(RecipeViewerSettings.DUMMY);settings.canInteractWith(p -> true);
        set(container,ModularContainer.class,"settings",settings);
        set(manager,ModularSyncManager.class,"container",container);
        gui=new MTECriticalMachineTerminalGui(null);
        set(gui,MTECriticalMachineTerminalGui.class,"state",bare(RecordingState.class));
        set(gui,MTECriticalMachineTerminalGui.class,"viewer",player);
        Method method=MTECriticalMachineTerminalGui.class.getDeclaredMethod("interaction",PanelSyncManager.class,String.class,Predicate.class);method.setAccessible(true);
        handler=(InteractionSyncHandler)method.invoke(gui,panel,"fixture",(Predicate<EntityPlayer>)p -> { mutations++; return true; });
        second=(InteractionSyncHandler)method.invoke(gui,panel,"second",(Predicate<EntityPlayer>)p -> { mutations+=100; return true; });
        manager.open("fixture",panel); manager.onOpen(); player.openContainer=container;
        networkId=ModularNetwork.SERVER.activate(player,manager);
    }
    static void send() throws Exception { send(handler, 0); }
    static void send(InteractionSyncHandler target, int button) throws Exception {
        PacketBuffer payload=new PacketBuffer(Unpooled.buffer());payload.writeVarIntToBuffer(1);new MouseData(Side.SERVER,button,false,false,false).writeToPacket(payload);
        PacketSyncHandler source=new PacketSyncHandler(networkId,"fixture",target.getKey(),false,payload);
        PacketBuffer wire=new PacketBuffer(Unpooled.buffer());source.write(wire);PacketSyncHandler decoded=new PacketSyncHandler();decoded.read(wire);decoded.executeServer(connection);
    }
    static void drain() { new Mui2ServerActions.Drain().tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END)); }
    static void closed() throws Exception {
        setup();
        // Native deactivate(dispose=false), also selected by decoded CloseGuiPacket, retains the ID.
        Method deactivate=ModularNetworkSide.class.getDeclaredMethod("deactivate",int.class,boolean.class);deactivate.setAccessible(true);
        deactivate.invoke(ModularNetwork.SERVER.get(player),networkId,false);
        check(manager.isClosed(),"native non-disposing close keeps the manager closed");
        player.openContainer=new ModularContainer();
        send();drain();
        check(mutations==0,"closed critical manager still executes native decoded command");
    }
    static void deferred() throws Exception {
        setup();send();
        check(mutations==0,"critical command mutates before server END drain");
        drain();check(mutations==1,"live critical command executes once at END");
    }
    static void disconnected() throws Exception {
        setup();connection.netManager.closeChannel(new net.minecraft.util.ChatComponentText("fixture disconnect"));send();drain();
        check(mutations==0,"disconnected critical manager still executes command");
    }
    static void replaced() throws Exception {
        setup();NetHandlerPlayServer next=bare(NetHandlerPlayServer.class);next.playerEntity=player;set(next,NetHandlerPlayServer.class,"netManager",connection.netManager);player.playerNetServerHandler=next;send();drain();
        check(mutations==0,"replaced connection retains critical command authority");
    }
    static void controls() throws Exception {
        setup();for(int n=0;n<10000;n++)send();check(size(queue(Mui2ServerActions.class,"PENDING"))==1,"critical action admission bounded per GUI, across action names");drain();check(mutations==1,"flood cannot execute more than one accepted action per drain");send();drain();check(mutations==2,"pending resets after execution");
        send();player.openContainer=new ModularContainer();drain();check(mutations==2,"container replacement after admission revokes queued work");player.openContainer=container;send();drain();check(mutations==3,"rejected task releases admission slot");
        UISettings denied=new UISettings(RecipeViewerSettings.DUMMY);denied.canInteractWith(p -> false);set(container,ModularContainer.class,"settings",denied);send();drain();check(mutations==3,"container target/access predicate revalidated");
    }
    static void lifecycle() throws Exception {
        setup();send();
        ModularContainer replacement=new ModularContainer();
        set(replacement,ModularContainer.class,"player",player);
        set(replacement,ModularContainer.class,"syncManager",manager);
        set(replacement,ModularContainer.class,"settings",get(container,ModularContainer.class,"settings"));
        set(manager,ModularSyncManager.class,"container",replacement);player.openContainer=replacement;
        drain();check(mutations==0,"queued command followed a manager into a replacement container");
        set(manager,ModularSyncManager.class,"container",container);player.openContainer=container;
        send();manager.onClose();manager.onOpen();drain();
        check(mutations==0,"close then reopen revived a command from the previous open interval");
        send();drain();check(mutations==1,"reopened live interval accepts a new command");
        send(handler,1);drain();check(mutations==1,"non-primary button never mutates");
        send();connection.netManager.closeChannel(new net.minecraft.util.ChatComponentText("fixture disconnect"));drain();
        check(mutations==1,"disconnect after admission revokes queued command");
    }
    static void commands() throws Exception {
        setup();
        send();send(second,0);check(size(queue(Mui2ServerActions.class,"PENDING"))==1,"different button names share one GUI admission slot");
        drain();check(mutations==1,"second request cannot replace the first fixed command");
        send(second,0);drain();check(mutations==101,"next fixed command executes after slot releases");
        send();new Mui2ServerActions.Drain().tick(new TickEvent.ServerTickEvent(TickEvent.Phase.START));
        check(mutations==101,"START never drains world commands");drain();check(mutations==102,"END drains the admitted command");
        // Committed release work is not subjected to request admission caps.
        final int[] released={0};for(int i=0;i<10000;i++)Mui2ServerActions.enqueue(()->released[0]++);
        drain();check(released[0]==10000,"accepted cleanup obligations remain lossless");
    }
    public static void main(String[] args) throws Exception {
        u=(sun.misc.Unsafe)f(sun.misc.Unsafe.class,"theUnsafe").get(null);set(null,Loader.class,"instance",bare(Loader.class));
        Object side=Proxy.newProxyInstance(IFMLSidedHandler.class.getClassLoader(),new Class[]{IFMLSidedHandler.class},(p,m,a)->m.getName().equals("getSide")?Side.CLIENT:null);set(FMLCommonHandler.instance(),FMLCommonHandler.class,"sidedDelegate",side);
        switch(args[0]) {case "closed":closed();break;case "deferred":deferred();break;case "disconnected":disconnected();break;case "connection":replaced();break;case "controls":controls();break;case "lifecycle":lifecycle();break;case "commands":commands();break;default:throw new AssertionError(args[0]);}
        System.out.println("PASS "+args[0]+" checks="+checks);
    }
}
