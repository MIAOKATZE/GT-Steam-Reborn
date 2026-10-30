package com.miaokatze.gtsr.tests;

import static com.miaokatze.gtsr.tests.PacketAuthorityRegression.*;
import static org.objectweb.asm.Opcodes.*;

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
import org.objectweb.asm.*;

/** Native Grant implementation and MC/Forge/Netty authority; target machine is an explicit API fixture. */
public final class RemoteGrantAuthorityRegression {
    static Object grant;
    static TileEntity target;
    static Class<?> targetType;
    static RemoteTerminalGuiData data;
    static final String ID="11111111-2222-3333-4444-555555555555";
    static final String TILE="com/miaokatze/gtsr/tests/GrantTargetFixture";
    static final String META="com/miaokatze/gtsr/tests/GrantMachineFixture";
    static final String GT="gregtech/api/interfaces/tileentity/IGregTechTileEntity";
    static final String IMETA="gregtech/api/interfaces/metatileentity/IMetaTileEntity";
    static final class FixtureLoader extends ClassLoader {
        FixtureLoader(){super(RemoteGrantAuthorityRegression.class.getClassLoader());}
        Class<?> define(String name,byte[] code){return defineClass(name.replace('/','.'),code,0,code.length);}
    }
    static void constant(ClassWriter c,String method,String descriptor,Object value) {
        MethodVisitor m=c.visitMethod(ACC_PUBLIC,method,descriptor,null,null);m.visitCode();m.visitLdcInsn(value);
        m.visitInsn(descriptor.endsWith("Z")||descriptor.endsWith("I")?IRETURN:ARETURN);m.visitMaxs(1,2);m.visitEnd();
    }
    static void field(ClassWriter c,String owner,String name,String descriptor,String getter) {
        c.visitField(ACC_PUBLIC,name,descriptor,null,null).visitEnd();
        MethodVisitor m=c.visitMethod(ACC_PUBLIC,getter,"()"+descriptor,null,null);m.visitCode();m.visitVarInsn(ALOAD,0);m.visitFieldInsn(GETFIELD,owner,name,descriptor);m.visitInsn(ARETURN);m.visitMaxs(1,1);m.visitEnd();
    }
    static void fixtures() throws Exception {
        // Only the methods called by the real Grant/status path are implemented. No GT lifecycle is simulated.
        FixtureLoader loader=new FixtureLoader();
        ClassWriter machine=new ClassWriter(0);machine.visit(V1_8,ACC_PUBLIC,META,null,"java/lang/Object",new String[]{IMETA,"com/miaokatze/gtsr/common/machine/base/IHubCacheNode"});
        constant(machine,"isAccessAllowed","(Lnet/minecraft/entity/player/EntityPlayer;)Z",1);
        constant(machine,"isBoundToHub","()Z",1);machine.visitEnd();Class<?> machineType=loader.define(META,machine.toByteArray());
        ClassWriter tile=new ClassWriter(0);tile.visit(V1_8,ACC_PUBLIC,TILE,null,"net/minecraft/tileentity/TileEntity",new String[]{GT,"com/miaokatze/gtsr/common/terminal/MachineTerminalIdentity"});
        field(tile,TILE,"machine","L"+IMETA+";","getMetaTileEntity");field(tile,TILE,"owner","Ljava/util/UUID;","getOwnerUuid");
        constant(tile,"isDead","()Z",0);constant(tile,"canAccessData","()Z",1);constant(tile,"getMetaTileID","()I",7);constant(tile,"gtsr$terminalTargetIdentity","()Ljava/lang/String;",ID);
        tile.visitEnd();targetType=loader.define(TILE,tile.toByteArray());target=(TileEntity)bare(targetType);
        targetType.getField("machine").set(target,bare(machineType));targetType.getField("owner").set(target,player.getUniqueID());target.setWorldObj(world);
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
        com.miaokatze.gtsr.common.gui.mui2.Mui2TerminalRoutes.cacheNodeEditor=true;
        ItemStack terminal=new ItemStack(bare(com.miaokatze.gtsr.common.items.HubTerminal.class),1);player.inventory.mainInventory[0]=terminal;
        Class<?> entryType=MachineTerminalHistory.Entry.class;
        Constructor<?> entryCtor=entryType.getDeclaredConstructor(int.class,int.class,int.class,int.class,int.class,MachineTerminalHistory.Kind.class,String.class,String.class,String.class);
        entryCtor.setAccessible(true);Object entry=entryCtor.newInstance(0,0,0,0,7,MachineTerminalHistory.Kind.CACHE_NODE,ID,player.getUniqueID().toString(),"fixture");
        Class<?> grantType=Class.forName("com.miaokatze.gtsr.common.terminal.MachineTerminalHistory$Grant");
        Constructor<?> ctor=grantType.getDeclaredConstructors()[0];ctor.setAccessible(true);grant=ctor.newInstance(player,entry,target);
        Constructor<?> dataCtor=RemoteTerminalGuiData.class.getDeclaredConstructor(EntityPlayer.class,int.class,int.class,int.class,int.class,MachineTerminalHistory.Kind.class,grantType);dataCtor.setAccessible(true);
        data=(RemoteTerminalGuiData)dataCtor.newInstance(player,0,0,0,0,MachineTerminalHistory.Kind.CACHE_NODE,grant);
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
        targetType.getField("owner").set(target,UUID.randomUUID());check(!data.canUseRemote(player),"target ownership change still revokes grant");targetType.getField("owner").set(target,player.getUniqueID());
        ((GrantWorld)world).tile=null;check(!data.canUseRemote(player),"removed target revokes grant");((GrantWorld)world).tile=target;
        check(data.canUseRemote(player),"original live target passes unchanged policy");
        player.worldObj=bare(SparseWorld.class);check(!data.canUseRemote(player),"obsolete world revokes grant");
    }
    public static void main(String[] args) throws Exception {
        u=(sun.misc.Unsafe)f(sun.misc.Unsafe.class,"theUnsafe").get(null);set(null,Loader.class,"instance",bare(Loader.class));
        Object side=Proxy.newProxyInstance(IFMLSidedHandler.class.getClassLoader(),new Class[]{IFMLSidedHandler.class},(p,m,a)->m.getName().equals("getSide")?Side.CLIENT:null);set(FMLCommonHandler.instance(),FMLCommonHandler.class,"sidedDelegate",side);
        switch(args[0]) {case "remote-disconnect":disconnected();break;case "remote-connection":replaced();break;case "remote-controls":controls();break;default:throw new AssertionError(args[0]);}
        System.out.println("PASS "+args[0]+" checks="+checks);
    }
}
