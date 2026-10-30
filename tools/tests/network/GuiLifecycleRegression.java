package com.miaokatze.gtsr.tests;

import static com.miaokatze.gtsr.tests.PacketAuthorityRegression.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.IntConsumer;
import net.minecraft.entity.player.*;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.*;
import net.minecraft.world.chunk.IChunkProvider;
import com.cleanroommc.modularui.api.UIFactory;
import com.cleanroommc.modularui.api.RecipeViewerSettings;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.*;
import com.cleanroommc.modularui.value.sync.*;
import com.cleanroommc.modularui.network.*;
import com.cleanroommc.modularui.network.packets.PacketSyncHandler;
import com.miaokatze.gtsr.common.gui.mui2.Mui2ServerActions;
import com.miaokatze.gtsr.common.gui.mui2.cluster.ClusterTerminalFactory.ClusterGuiData;
import com.miaokatze.gtsr.common.gui.mui2.entanglement.EntanglementTerminalSession;
import com.miaokatze.gtsr.common.machine.MTESteamSingularityEntangler;
import com.miaokatze.gtsr.common.terminal.entanglement.EntanglementSnapshot;
import cpw.mods.fml.common.*;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.simpleimpl.*;
import cpw.mods.fml.relauncher.Side;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import io.netty.buffer.Unpooled;

/** Exact production entry points plus native MC/MUI framing; no game, GL or TCP. */
public final class GuiLifecycleRegression {
    static void setup() throws Exception {
        setupServer();
        set(world, World.class, "provider", new WorldProviderSurface());
        player.posY = 70.5;
        net.minecraft.entity.DataWatcher watcher=new net.minecraft.entity.DataWatcher(player);
        watcher.addObject(6,Float.valueOf(20));set(player,net.minecraft.entity.Entity.class,"dataWatcher",watcher);
    }
    static UIFactory factory(String name)throws Exception {
        Class<?> c=Class.forName("com.miaokatze.gtsr."+name);
        return (UIFactory)get(null,c,"INSTANCE");
    }
    static void position(String kind, boolean unloaded)throws Exception {
        setup();
        UIFactory factory;
        String pkg="common.gui.mui2.";
        switch(kind) {
            case "hub": factory=com.miaokatze.gtsr.common.gui.mui2.hub.HubTerminalFactory.forType(com.miaokatze.gtsr.common.terminal.TerminalUiType.SINGULARITY_HUB); break;
            case "cluster": factory=factory(pkg+"cluster.ClusterTerminalFactory"); break;
            case "critical": factory=factory("common.gui.CriticalMachineTerminalFactory"); break;
            case "tcds": factory=factory(pkg+"TcdsTerminalFactory"); break;
            case "entanglement": factory=factory(pkg+"entanglement.EntanglementTerminalFactory"); break;
            case "aggregator": factory=factory(pkg+"inventory.AggregatorConfigFactory"); break;
            case "chest": factory=factory(pkg+"inventory.UnsealedChestFactory"); break;
            default: throw new AssertionError(kind);
        }
        int x=unloaded ? 0 : 1000000;
        if(unloaded) world.chunks=(IChunkProvider)Proxy.newProxyInstance(IChunkProvider.class.getClassLoader(),new Class[]{IChunkProvider.class},(p,m,a)->m.getName().equals("chunkExists")?false:null);
        PosGuiData data=kind.equals("cluster")?new ClusterGuiData(player,x,70,0,0,1):new PosGuiData(player,x,70,0);
        // GuiManager.open invokes createPanel before it can reject canInteractWith.
        UISettings settings=new UISettings(RecipeViewerSettings.DUMMY);
        openThroughNative(factory,data,settings);
        check(world.tileReads==0,kind+" "+(unloaded?"unloaded":"distant")+" factory accessed untrusted tile "+world.tileReads+" times before rejecting");
        check(!settings.canPlayerInteractWithUI(player),"denied factory cannot interact");
    }
    static void openThroughNative(UIFactory factory,PosGuiData data,UISettings settings)throws Exception {
        final UIFactory target=factory;
        UIFactory<PosGuiData> boundary=new UIFactory<PosGuiData>() {
            public String getFactoryName(){return target.getFactoryName();}
            public void writeGuiData(PosGuiData d,PacketBuffer b){target.writeGuiData(d,b);}
            public PosGuiData readGuiData(EntityPlayer p,PacketBuffer b){return (PosGuiData)target.readGuiData(p,b);}
            public ModularScreen createScreen(PosGuiData d,ModularPanel p){throw new AssertionError("No client GUI");}
            public ModularPanel createPanel(PosGuiData d,PanelSyncManager manager,UISettings ignored){
                check(d.getX()==data.getX(),"untrusted coordinate survives native C2S decode");
                ModularPanel denied=target.createPanel(d,manager,settings);
                check(denied!=null,"real factory returns denied panel");
                throw new BoundaryReached(); // Stop before native GUI installation; this is not a game launch.
            }
        };
        ((Map)get(null,com.cleanroommc.modularui.factory.GuiManager.class,"FACTORIES")).remove(boundary.getFactoryName());
        com.cleanroommc.modularui.factory.GuiManager.registerFactory(boundary);
        PacketBuffer descriptor=new PacketBuffer(Unpooled.buffer());boundary.writeGuiData(data,descriptor);
        com.cleanroommc.modularui.network.packets.OpenGuiPacket sent=new com.cleanroommc.modularui.network.packets.OpenGuiPacket(0,0,boundary,descriptor);
        PacketBuffer wire=new PacketBuffer(Unpooled.buffer());sent.write(wire);
        com.cleanroommc.modularui.network.packets.OpenGuiPacket decoded=new com.cleanroommc.modularui.network.packets.OpenGuiPacket();decoded.read(wire);
        boolean reached=false;try{decoded.executeServer(connection);}catch(BoundaryReached expected){reached=true;}
        check(reached,"actual native C2S entry dispatches to production factory without a precheck");
    }
    static void remoteCodec()throws Exception {
        setup();
        UIFactory factory=factory("common.terminal.RemoteTerminalFactory");
        Class<?> rawData=Class.forName("com.miaokatze.gtsr.common.terminal.RemoteTerminalGuiData");
        Class<?> grantType=Class.forName("com.miaokatze.gtsr.common.terminal.MachineTerminalHistory$Grant");
        Constructor<?> constructor=rawData.getDeclaredConstructor(EntityPlayer.class,int.class,int.class,int.class,int.class,com.miaokatze.gtsr.common.terminal.MachineTerminalHistory.Kind.class,grantType);constructor.setAccessible(true);
        // Serialization must strip even a non-null transient grant object; this sentinel is NOT a valid grant.
        Object sentinel=bare(grantType);
        com.miaokatze.gtsr.common.terminal.RemoteTerminalGuiData original=(com.miaokatze.gtsr.common.terminal.RemoteTerminalGuiData)constructor.newInstance(player,1000000,70,-1000000,777,com.miaokatze.gtsr.common.terminal.MachineTerminalHistory.Kind.CRITICAL,sentinel);
        ClusterGuiData source=new ClusterGuiData(original,12345);
        PacketBuffer encoded=new PacketBuffer(Unpooled.buffer());factory.writeGuiData(source,encoded);byte[] wire=new byte[encoded.readableBytes()];encoded.readBytes(wire);
        for(int n=0;n<3;n++) {
            ClusterGuiData decoded=(ClusterGuiData)factory.readGuiData(player,new PacketBuffer(Unpooled.wrappedBuffer(wire)));
            check(decoded.remote && decoded.targetDimension==777,"remote wire descriptor retained only as presentation data");
            check(get(decoded,rawData,"grant")==null,"remote C2S decode/replay cannot recreate server-only grant");
            check(!decoded.canUseRemote(player) && decoded.getTileEntity()==null,"missing/replayed grant cannot resolve or authorize target");
            UISettings settings=new UISettings(RecipeViewerSettings.DUMMY);
            ((List)get(null,com.cleanroommc.modularui.factory.GuiManager.class,"openedContainers")).clear();
            openThroughNative(factory,decoded,settings);
            check(!settings.canPlayerInteractWithUI(player) && world.tileReads==0,"native remote C2S replay reaches only denied panel without world reads");
            // Local factories must not trust the subtype or remote flag without the grant check.
            check(!com.miaokatze.gtsr.common.gui.mui2.hub.HubTerminalFactory.forType(com.miaokatze.gtsr.common.terminal.TerminalUiType.SINGULARITY_HUB).canInteractWith(player,decoded),"local hub route rejects forged remote subtype");
        }
        PacketBuffer local=new PacketBuffer(Unpooled.buffer());local.writeInt(1000000);local.writeInt(70);local.writeInt(0);local.writeByte(0);local.writeLong(12345);local.writeBoolean(true);
        ClusterGuiData localDecoded=(ClusterGuiData)factory("common.gui.mui2.cluster.ClusterTerminalFactory").readGuiData(player,local);
        check(!localDecoded.remote,"extra forged flag cannot turn local cluster descriptor into remote grant");
        for(int invalidKind:new int[]{255}) {
            PacketBuffer invalid=new PacketBuffer(Unpooled.buffer());invalid.writeInt(1);invalid.writeInt(70);invalid.writeInt(1);invalid.writeInt(777);invalid.writeByte(invalidKind);invalid.writeLong(12345);
            boolean rejected=false;try{factory.readGuiData(player,invalid);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"invalid remote kind rejected before target resolution");
        }
        PacketBuffer trailing=new PacketBuffer(Unpooled.buffer());trailing.writeBytes(wire);trailing.writeByte(1);boolean rejected=false;try{factory.readGuiData(player,trailing);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"remote grant descriptor rejects appended bytes");
        check(world.tileReads==0,"all malformed/replayed remote descriptors avoid local world access");
    }
    static final class BoundaryReached extends RuntimeException {}
    static void localBounds()throws Exception {
        setup();
        Method gate=com.miaokatze.gtsr.common.terminal.TerminalServerSessions.class.getMethod("canInspectLocalPosition",EntityPlayer.class,PosGuiData.class,double.class);
        PosGuiData data=new PosGuiData(player,0,70,0);
        player.posX=8.5;player.posZ=.5;
        check((Boolean)gate.invoke(null,player,data,64D),"eight-block boundary accepted");
        player.posX=8.5001;check(!(Boolean)gate.invoke(null,player,data,64D),"outside inventory range rejected");
        player.posX=64.5;check((Boolean)gate.invoke(null,player,data,4096D),"legacy terminal 64-block boundary retained");
        player.posX=64.5001;check(!(Boolean)gate.invoke(null,player,data,4096D),"outside terminal range rejected");
        player.posX=Double.NaN;check(!(Boolean)gate.invoke(null,player,data,4096D),"non-finite coordinates rejected");
        player.posX=.5;player.isDead=true;check(!(Boolean)gate.invoke(null,player,data,4096D),"dead actor rejected");
        player.isDead=false;check(!(Boolean)gate.invoke(null,player,new PosGuiData(player,0,-1,0),4096D),"out-of-world target rejected");
        check(world.tileReads==0,"cheap gate never resolves a tile");
    }
    static PacketBuffer command(int sequence,int page) {
        PacketBuffer b=new PacketBuffer(Unpooled.buffer());b.writeInt(sequence);b.writeInt(9);b.writeInt(page);b.writeBoolean(true);b.writeByte(0);return b;
    }
    static void entanglement()throws Exception {
        setup();Mui2ServerActions.clear();
        Constructor<EntanglementTerminalSession> ctor=EntanglementTerminalSession.class.getDeclaredConstructor(PosGuiData.class,com.miaokatze.gtsr.common.machine.base.MTESingularityMachineBase.class);ctor.setAccessible(true);
        EntanglementTerminalSession session=ctor.newInstance(new PosGuiData(player,0,70,0),null);
        // A page change has no client awaiting flag, so a valid apply can follow before END.
        PacketBuffer nav=new PacketBuffer(Unpooled.buffer());nav.writeInt(1);session.readOnServer(3,nav);session.readOnServer(1,command(1,0));
        check(size(queue(Mui2ServerActions.class,"PENDING"))==2,"page+apply race silently discarded command/ACK obligation");
        for(int n=0;n<10000;n++){PacketBuffer b=new PacketBuffer(Unpooled.buffer());b.writeInt(1);session.readOnServer(3,b);session.readOnServer(1,command(1,0));}
        check(size(queue(Mui2ServerActions.class,"PENDING"))==2,"navigation plus command remains bounded to two tasks per session");
        // An unopened/disposed session has no authority; both independent admission flags must still be released.
        new Mui2ServerActions.Drain().tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
        check(size(queue(Mui2ServerActions.class,"PENDING"))==0,"inactive session drains without touching a machine");
        PacketBuffer nav2=new PacketBuffer(Unpooled.buffer());nav2.writeInt(1);session.readOnServer(3,nav2);session.readOnServer(1,command(2,1));
        check(size(queue(Mui2ServerActions.class,"PENDING"))==2,"both admission slots reset even after rejected/inactive work");Mui2ServerActions.clear();
        // Independently exercise the real client rejection decoder (not a claim of live server transport).
        EntanglementTerminalSession client=ctor.newInstance(new PosGuiData(player,0,70,0),null);
        set(client,EntanglementTerminalSession.class,"sequence",1);set(client,EntanglementTerminalSession.class,"awaiting",true);
        byte[] snapshot=new EntanglementSnapshot().encode();PacketBuffer reply=new PacketBuffer(Unpooled.buffer());reply.writeInt(1);reply.writeBoolean(false);reply.writeInt(snapshot.length);reply.writeBytes(snapshot);
        client.readOnClient(2,reply);client.snapshot();check(!(Boolean)get(client,EntanglementTerminalSession.class,"awaiting"),"matching rejection clears actual client awaiting");
    }
    static void renderer()throws Exception {
        Class<?> c=Class.forName("com.miaokatze.gtsr.client.weapons.PortableWeaponRenderer");
        check(net.minecraft.client.resources.IResourceManagerReloadListener.class.isAssignableFrom(c),"weapon renderer is missing resource reload lifecycle");
        Map<String,Integer> lists=(Map<String,Integer>)get(null,c,"LISTS");Map models=(Map)get(null,c,"MODELS");lists.put("a",17);lists.put("b",29);models.put("a",null);
        Method release=c.getDeclaredMethod("releaseCaches",IntConsumer.class);release.setAccessible(true);List<Integer> deleted=new ArrayList<>();release.invoke(null,(IntConsumer)deleted::add);
        check(new HashSet<>(deleted).equals(new HashSet<>(Arrays.asList(17,29))) && deleted.size()==2,"each retained GL list is released once");check(lists.isEmpty()&&models.isEmpty(),"OBJ and compiled mesh generations both invalidated");release.invoke(null,(IntConsumer)deleted::add);check(deleted.size()==2,"repeat reload is idempotent");
    }
    public static void main(String[] args)throws Exception {
        u=(sun.misc.Unsafe)f(sun.misc.Unsafe.class,"theUnsafe").get(null);set(null,Loader.class,"instance",bare(Loader.class));
        Object side=Proxy.newProxyInstance(IFMLSidedHandler.class.getClassLoader(),new Class[]{IFMLSidedHandler.class},(p,m,a)->m.getName().equals("getSide")?Side.CLIENT:null);set(FMLCommonHandler.instance(),FMLCommonHandler.class,"sidedDelegate",side);
        if(args[0].startsWith("range-")) position(args[0].substring(6),false);
        else if(args[0].startsWith("unloaded-")) position(args[0].substring(9),true);
        else if(args[0].equals("bounds")) localBounds();
        else if(args[0].equals("remote")) remoteCodec();
        else if(args[0].equals("entanglement"))entanglement();
        else if(args[0].equals("renderer"))renderer();
        else throw new AssertionError(args[0]);
        System.out.println("PASS "+args[0]+" checks="+checks);
    }
}
