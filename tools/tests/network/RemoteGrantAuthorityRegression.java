package com.miaokatze.gtsr.tests;

import static com.miaokatze.gtsr.tests.PacketAuthorityRegression.*;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.entity.player.*;
import net.minecraft.item.ItemStack;
import net.minecraft.network.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.*;
import net.minecraftforge.common.DimensionManager;
import com.miaokatze.gtsr.common.terminal.*;
import cpw.mods.fml.common.*;
import cpw.mods.fml.relauncher.Side;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import com.miaokatze.gtsr.common.machine.MTESteamHubArray;

/** Native Grant implementation and MC/Forge/Netty authority; target machine is an explicit API fixture. */
public final class RemoteGrantAuthorityRegression {
    static Object grant;
    static GrantTarget target;
    static RemoteTerminalGuiData data;
    static final String ID="11111111-2222-3333-4444-555555555555";
    static final class GrantTarget extends BaseMetaTileEntity implements MachineTerminalIdentity {
        @Override public int getMetaTileID(){return 7;}
        @Override public String gtsr$terminalTargetIdentity(){return ID;}
    }
    static void fixtures() throws Exception {
        // Use the native GregTech base/lock and an actual supported machine without starting GT.
        target=bare(GrantTarget.class);
        target.setWorldObj(world);
        target.setOwnerUuid(player.getUniqueID());
        MTESteamHubArray machine=bare(MTESteamHubArray.class);
        set(machine,MetaTileEntity.class,"mBaseMetaTileEntity",target);
        target.setMetaTileEntity(machine);
    }
    static class GrantWorld extends SparseWorld {
        TileEntity tile;
        @Override public TileEntity getTileEntity(int x,int y,int z){tileReads++;return tile;}
    }
    static void setup() throws Exception {
        setupServer();GrantWorld next=bare(GrantWorld.class);next.chunks=world.chunks;world=next;server.world=world;player.worldObj=world;
        set(world,World.class,"provider",new WorldProviderSurface());
        ((Map)get(null,DimensionManager.class,"worlds")).put(0,world);
        fixtures();next.tile=target;
        com.miaokatze.gtsr.common.gui.mui2.Mui2TerminalRoutes.cacheHubTerminal=true;
        com.miaokatze.gtsr.common.items.HubTerminal item=bare(com.miaokatze.gtsr.common.items.HubTerminal.class);
        Object delegate=Proxy.newProxyInstance(cpw.mods.fml.common.registry.RegistryDelegate.class.getClassLoader(),
            new Class<?>[]{cpw.mods.fml.common.registry.RegistryDelegate.class},
            (proxy,method,values)->method.getName().equals("get")?item:null);
        set(item,net.minecraft.item.Item.class,"delegate",delegate);
        ItemStack terminal=new ItemStack(item,1);player.inventory.mainInventory[0]=terminal;
        Class<?> entryType=MachineTerminalHistory.Entry.class;
        Constructor<?> entryCtor=entryType.getDeclaredConstructor(int.class,int.class,int.class,int.class,int.class,MachineTerminalHistory.Kind.class,String.class,String.class,String.class);
        entryCtor.setAccessible(true);Object entry=entryCtor.newInstance(0,0,0,0,7,MachineTerminalHistory.Kind.STEAM_HUB,ID,player.getUniqueID().toString(),"fixture");
        Class<?> grantType=Class.forName("com.miaokatze.gtsr.common.terminal.MachineTerminalHistory$Grant");
        Constructor<?> ctor=grantType.getDeclaredConstructors()[0];ctor.setAccessible(true);grant=ctor.newInstance(player,entry,target);
        Constructor<?> dataCtor=RemoteTerminalGuiData.class.getDeclaredConstructor(EntityPlayer.class,int.class,int.class,int.class,int.class,MachineTerminalHistory.Kind.class,grantType);dataCtor.setAccessible(true);
        data=(RemoteTerminalGuiData)dataCtor.newInstance(player,0,0,0,0,MachineTerminalHistory.Kind.STEAM_HUB,grant);
        check(data.canUseRemote(player),"otherwise authorized live server-issued grant accepted");
    }
    static void disconnected() throws Exception {
        setup();connection.netManager.closeChannel(new net.minecraft.util.ChatComponentText("fixture disconnect"));
        check(!data.canUseRemote(player),"remote grant survived disconnect with same player/item/target");
    }
    static void replaced() throws Exception {
        setup();NetHandlerPlayServer replacement=bare(NetHandlerPlayServer.class);replacement.playerEntity=player;set(replacement,NetHandlerPlayServer.class,"netManager",connection.netManager);player.playerNetServerHandler=replacement;
        check(!data.canUseRemote(player),"remote grant survived replacement of its issuing connection");
    }
    static void controls() throws Exception {
        setup();int reads=world.tileReads;
        server.getConfigurationManager().playerEntityList.clear();
        check(!data.canUseRemote(player)&&world.tileReads==reads,"offline exact entity rejected before target lookup");server.getConfigurationManager().playerEntityList.add(player);
        check(data.canUseRemote(player),"live exact online entity remains authorized");
        ItemStack terminal=player.getHeldItem();player.inventory.mainInventory[0]=terminal.copy();check(!data.canUseRemote(player),"copied terminal cannot inherit grant");player.inventory.mainInventory[0]=terminal;
        target.setOwnerUuid(UUID.randomUUID());check(!data.canUseRemote(player),"target ownership change still revokes grant");target.setOwnerUuid(player.getUniqueID());
        ((GrantWorld)world).tile=null;check(!data.canUseRemote(player),"removed target revokes grant");((GrantWorld)world).tile=target;
        check(data.canUseRemote(player),"original live target passes unchanged policy");
        player.worldObj=bare(SparseWorld.class);check(!data.canUseRemote(player),"obsolete world revokes grant");
    }
    public static void main(String[] args) throws Exception {
        Thread.currentThread().setName("Server thread");
        u=(sun.misc.Unsafe)f(sun.misc.Unsafe.class,"theUnsafe").get(null);set(null,Loader.class,"instance",bare(Loader.class));
        Object side=Proxy.newProxyInstance(IFMLSidedHandler.class.getClassLoader(),new Class[]{IFMLSidedHandler.class},(p,m,a)->m.getName().equals("getSide")?Side.SERVER:null);set(FMLCommonHandler.instance(),FMLCommonHandler.class,"sidedDelegate",side);
        switch(args[0]) {case "remote-disconnect":disconnected();break;case "remote-connection":replaced();break;case "remote-controls":controls();break;default:throw new AssertionError(args[0]);}
        System.out.println("PASS "+args[0]+" checks="+checks);
    }
}
