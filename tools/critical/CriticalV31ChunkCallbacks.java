package com.miaokatze.gtsr.common.critical;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;

/** Extends the real Forge force/release probe with actual production restore callbacks and supervisor. */
public final class CriticalV31ChunkCallbacks {
    static final JsonArray rows=new JsonArray();
    static void check(boolean pass,String name){JsonObject row=new JsonObject();row.addProperty("name",name);row.addProperty("pass",pass);rows.add(row);if(!pass)throw new AssertionError(name);}
    static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    @SuppressWarnings("unchecked")public static void main(String[] args)throws Exception {
        JsonObject report=new JsonObject();
        try {
            var unsafe=(sun.misc.Unsafe)field(sun.misc.Unsafe.class,"theUnsafe").get(null);CriticalChunkLoadingV31Probe.unsafe=unsafe;CriticalIntegrityContract.unsafe=unsafe;CriticalIntegrityContract.materials();
            CriticalChunkLoadingV31Probe.run();
            // Reset flags overridden by the base contract's quota cases.
            field(ForgeChunkManager.class,"defaultMaxChunks").setInt(null,128);field(ForgeChunkManager.class,"overridesEnabled").setBoolean(null,false);
            var w=CriticalChunkLoadingV31Probe.world();BaseMetaTileEntity h=CriticalChunkLoadingV31Probe.host(w);
            MTECriticalMachine machine=(MTECriticalMachine)unsafe.allocateInstance(MTECriticalMachine.class);CriticalMachineState state=new CriticalMachineState();
            NBTTagCompound n=new NBTTagCompound();n.setInteger("geometryVersion",2);n.setInteger("v2LayoutRevision",31);n.setBoolean("loomJournalOwner",true);n.setBoolean("loomController",true);n.setInteger("state",2);n.setString("structureId","callback-restored");n.setTag("config",new CriticalConfiguration(CriticalTier.T3,CriticalMachineKind.BATTERY,0,0,0).write());state.readFromNBT(n);
            field(MTECriticalMachine.class,"state").set(machine,state);field(gregtech.api.metatileentity.MetaTileEntity.class,"mBaseMetaTileEntity").set(machine,h);h.setMetaTileEntity(machine);
            field(CriticalMachineState.class,"visualHostId").set(state,"preseed-native-host");field(CriticalMachineState.class,"protectionDirty").setBoolean(state,false);machine.getLocalCriticalState();
            List<ChunkCoordIntPair> desired=state.getChunkLoadingFootprint();w.loaded.addAll(desired);w.loaded.add(new ChunkCoordIntPair(h.xCoord>>4,h.zCoord>>4));
            Ticket ticket=CriticalChunkLoadingV31Probe.ticket(w,h);Map<TileEntity,Ticket> registrations=(Map<TileEntity,Ticket>)field(CriticalChunkTicketManager.class,"REGISTERED").get(null);registrations.remove(h);
            CriticalChunkTicketManager manager=(CriticalChunkTicketManager)field(CriticalChunkTicketManager.class,"INSTANCE").get(null);
            Ticket invalid=CriticalChunkLoadingV31Probe.ticket(w,null);List<Ticket> kept=manager.ticketsLoaded(Arrays.asList(invalid,ticket),w,1);
            check(kept.size()==1&&kept.get(0)==ticket,"ordered callback rejects foreign owner and adopts exact loom GT controller");check(registrations.get(h)==ticket,"restored ticket is registered to exact host");
            manager.ticketsLoaded(kept,w);check(state.getChunkLoading().ready()&&ticket.getChunkList().containsAll(desired),"unordered callback reconstructs full canonical v31 footprint");
            check(manager.ticketsLoaded(Collections.singletonList(ticket),w,1).isEmpty(),"duplicate restore callback cannot adopt same host twice");
            check(manager.ticketsLoaded(Collections.singletonList(invalid),w,0).isEmpty(),"restore callback respects maximum ticket count zero");ForgeChunkManager.releaseTicket(invalid);
            Ticket foreign=CriticalChunkLoadingV31Probe.ticket(w,null);for(ChunkCoordIntPair chunk:desired)ForgeChunkManager.forceChunk(foreign,chunk);
            state.getChunkLoading().ensure(w,h,desired);check(ticket.getChunkList().isEmpty()&&registrations.get(h)==ticket,"all-external coverage retains empty owner recovery ticket");
            ForgeChunkManager.releaseTicket(foreign);new CriticalChunkLoading.Supervisor().tick(new TickEvent.WorldTickEvent(cpw.mods.fml.relauncher.Side.SERVER,TickEvent.Phase.START,w));
            check(state.getChunkLoading().ready()&&ticket.getChunkList().containsAll(desired),"WorldTick supervisor takes controller and full footprint after foreign force disappears without tile callback");
            state.getChunkLoading().release();check(!registrations.containsKey(h)&&ticket.getChunkList().isEmpty(),"explicit removal releases restored owner ticket");report.addProperty("status","PASS");
        }catch(Throwable error){report.addProperty("status","FAIL");report.addProperty("failure",error.toString());error.printStackTrace();}
        report.add("baseChecks",CriticalChunkLoadingV31Probe.checks);report.add("callbackChecks",rows);report.addProperty("scope","Real Forge ticket objects/force/unforce/release, actual GTSR ordered+unordered loading callbacks and WorldTick supervisor; real GT host/MTE with sparse World, preseed restored ticket. No FML mod-container registration, new requestTicket issuance, persisted server restart or async chunk IO.");
        report.addProperty("productionCodeSource",CriticalChunkTicketManager.class.getProtectionDomain().getCodeSource().getLocation().toString());Files.writeString(Path.of(args[0]),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
