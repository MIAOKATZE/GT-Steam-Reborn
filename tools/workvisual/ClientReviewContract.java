package com.miaokatze.gtsr.client.machinevisual;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.miaokatze.gtsr.common.machinevisual.MachineVisualDefinition;
import com.miaokatze.gtsr.common.machinevisual.MachineVisualHost;
import com.miaokatze.gtsr.common.machinevisual.MachineVisualSnapshot;

import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Full production client call sites, with explicit MC/network/visibility/material/GL boundary fakes. */
public final class ClientReviewContract {
    private static int checks, failures;
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static MachineVisualClient client;
    private static World world;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) { failures++; System.out.println("FAIL " + message); }
    }

    private static void tick() { client.tick(new TickEvent.ClientTickEvent()); }

    private static void reset() throws Exception {
        world = new World(); MC.theWorld = world; MC.thePlayer.x = 0; MC.thePlayer.alwaysNear = true; MC.paused = false;
        MachineVisualClient.setQuality(MachineVisualClient.Quality.OFF);
        tick();
    }

    private static void observe(Host host) { MachineVisualClient.observe(host, 1); }

    private static List<Host> fill(int count, MachineVisualDefinition definition) {
        List<Host> hosts = new ArrayList<>();
        for (int i=0;i<count;i++) { Host h = new Host(i*32,definition); hosts.add(h); observe(h); }
        // Four registrations per tick still fill 512 inside this finite fixture.
        for (int t=0;t<130;t++) { for (Host h:hosts) observe(h); tick(); }
        return hosts;
    }

    private static void working(List<Host> hosts) {
        for (Host h:hosts) {
            MachineVisualClient.Entry e=MachineVisualClient.entry(h);
            if (e==null) continue;
            e.snapshot=new MachineVisualSnapshot();e.receivedAt=System.nanoTime();e.advanceUntil=e.receivedAt+2000000000L;
        }
    }

    public static void main(String[] args) throws Exception {
        Constructor<MachineVisualClient> ctor=MachineVisualClient.class.getDeclaredConstructor(); ctor.setAccessible(true); client=ctor.newInstance();
        reset();
        List<Host> hosts=fill(512,new MachineVisualDefinition(new MachineVisualDefinition.Group[0]));
        int present=0;for(Host h:hosts)if(MachineVisualClient.entry(h)!=null)present++;
        check(present==512,"512 nearby observation hosts register");
        Host newcomer=new Host(20000,new MachineVisualDefinition(new MachineVisualDefinition.Group[0]));
        int admittedAt=-1;
        for(int t=0;t<125;t++) {
            for(Host h:hosts)observe(h); MachineVisualClient.observe(newcomer,0);tick();
            if(MachineVisualClient.entry(newcomer)!=null){admittedAt=t;break;}
        }
        check(admittedAt>=0,"LIFE-R03 newcomer enters full registry within lease bound");
        System.out.println("MEASURE registrationWaitTicks="+admittedAt);

        reset(); hosts=fill(512,MachineVisualDefinition.get("atmospheric_centrifuge"));working(hosts);
        MachineVisualClient.setQuality(MachineVisualClient.Quality.LOW);world.queries=0;tick();
        long low=world.queries;
        check(low<=5000,"PERF-001 LOW candidate world queries are bounded: actual="+low);
        System.out.println("MEASURE LOW hosts=512 worldQueries="+low+" heldMembers="+MachineVisualClient.heldMotionMembers);

        long sweepBuilds=MachineVisualClearance.sweepBuilds,orientationBuilds=MachineVisualClearance.orientationBuilds;
        working(hosts);tick();
        check(MachineVisualClearance.sweepBuilds==sweepBuilds&&MachineVisualClearance.orientationBuilds==orientationBuilds,
            "fixed geometry and orientation are reused across hosts/ticks");

        reset();hosts=fill(20,MachineVisualDefinition.get("atmospheric_centrifuge"));working(hosts);
        for(Host blocked:hosts){MachineVisualClient.Entry entry=MachineVisualClient.entry(blocked);int[] air=entry.geometry.groups[0].air;
            world.obstacles.add(World.key(entry.originX+air[0],entry.originY+air[1],entry.originZ+air[2]));}
        MachineVisualClient.setQuality(MachineVisualClient.Quality.LOW);
        for(int i=0;i<3;i++)tick();
        int scanned=0;for(Host blocked:hosts)if(MachineVisualClient.entry(blocked).lastCandidateScan!=Long.MIN_VALUE)scanned++;
        check(scanned==20,"blocked candidates take finite scan turns within LOW credits: "+scanned);

        reset();hosts=fill(1,MachineVisualDefinition.get("air_compressor"));working(hosts);
        MachineVisualClient.setQuality(MachineVisualClient.Quality.LOW);tick();
        MachineVisualClient.Entry old=MachineVisualClient.entry(hosts.get(0));int[] air=old.geometry.groups[0].air;
        world.obstacles.add(World.key(old.originX+air[0],old.originY+air[1],old.originZ+air[2]));tick();
        check((old.mask&1)==0&&(old.restoringMask&1)!=0,"published group sees a new obstruction in the next tick");
        check(MachineVisualVisibility.heldCosts()[0]==4,"obstructed group's static restoration still holds its reservation");
        hosts.get(0).tile.invalid=true;Host replacement=new Host(0,hosts.get(0).definition);observe(replacement);tick();
        check(MachineVisualClient.entry(replacement)==null&&MachineVisualVisibility.heldCosts()[0]==4,
            "same-coordinate replacement waits for old owner's static acknowledgement");
        // Explicit fake boundary acknowledgement; native section/GPU acknowledgement remains a separate test.
        old.restoringMask=0;tick();
        check(MachineVisualClient.entry(replacement)!=null,"replacement may register after explicit restoration acknowledgement");

        reset();hosts=fill(1,MachineVisualDefinition.get("air_compressor"));working(hosts);
        Host h=hosts.get(0);MachineVisualClient.setQuality(MachineVisualClient.Quality.LOW);tick();
        MachineVisualClient.Entry e=MachineVisualClient.entry(h);e.decorativeMs=150;
        try {Field prev=e.getClass().getDeclaredField("previousDecorativeMs");prev.setAccessible(true);prev.setLong(e,100);}catch(NoSuchFieldException ignored){}
        MachineVisualRenderer.render(h,0,0,0,.1f);long first=h.definition.groups.get(0).sampled;
        MachineVisualRenderer.render(h,0,0,0,.9f);long second=h.definition.groups.get(0).sampled;
        check(second>first&&first>=100&&second<=150,"RENDER-001 partialTick gives bounded distinct frame samples: "+first+","+second);
        System.out.println("MEASURE decorativeSamples="+first+","+second);
        MC.paused=true;tick();
        MachineVisualRenderer.render(h,0,0,0,.2f);long paused=h.definition.groups.get(0).sampled;
        MachineVisualRenderer.render(h,0,0,0,.8f);check(h.definition.groups.get(0).sampled==paused,"pause freezes decorative frame time");
        MC.paused=false;tick();
        MachineVisualDefinition.Group group=h.definition.groups.get(0);
        int samples=group.samples;e.advanceUntil=System.nanoTime()-1;
        MachineVisualRenderer.render(h,0,0,0,.9f);check(group.samples==samples,"ordinary cycle endpoint stops render sampling");
        e.snapshot.duration=1;e.receivedAt=System.nanoTime()-500000000L;
        MachineVisualRenderer.render(h,0,0,0,.9f);check(group.samples>samples,"one-tick generator retains its existing decorative coast");
        samples=group.samples;e.receivedAt=System.nanoTime()-1100000000L;
        MachineVisualRenderer.render(h,0,0,0,.9f);check(group.samples==samples,"one-tick generator stops at its coast bound");
        e.receivedAt=System.nanoTime()-3000000000L;e.advanceUntil=System.nanoTime()+1000000000L;
        MachineVisualRenderer.render(h,0,0,0,.9f);check(group.samples==samples,"stale authority stops render sampling");
        e.receivedAt=System.nanoTime();MachineVisualClient.setQuality(MachineVisualClient.Quality.OFF);
        MachineVisualRenderer.render(h,0,0,0,.9f);check(group.samples==samples,"OFF stops render sampling immediately");

        for(String id:new String[]{"air_compressor","atmospheric_centrifuge","gear_steam_compressor","steam_fluid_drill","crust_steam_borer"}) {
            MachineVisualDefinition d=MachineVisualDefinition.get(id);
            MachineVisualClearance.Plan a=MachineVisualClearance.plan(d,ExtendedFacing.NORTH),b=MachineVisualClearance.plan(d,ExtendedFacing.EAST);
            boolean bounded=true;
            for(MachineVisualClearance.Plan p:new MachineVisualClearance.Plan[]{a,b})for(MachineVisualClearance.GroupGeometry g:p.groups)
                for(int coordinate:g.air)if(Math.abs(coordinate)>32)bounded=false;
            check(bounded,"fixed sweep coordinate signs and rotated bounds: "+id);
        }

        System.out.println((failures==0?"PASS":"FAIL")+" source client feedback checks="+checks+" failures="+failures+"; production JAR/FML/GL/native timing NOT_RUN");
        if(failures!=0)System.exit(1);
    }

    private static final class Host implements MachineVisualHost {
        final Tile tile;final MachineVisualDefinition definition;
        Host(int x,MachineVisualDefinition d){definition=d;tile=new Tile(this,x);world.tiles.put(World.key(x,64,0),tile);}
        public IGregTechTileEntity getBaseMetaTileEntity(){return tile;}
        public MachineVisualDefinition visualDefinition(){return definition;}
        public ExtendedFacing getExtendedFacing(){return ExtendedFacing.NORTH;}
    }
    private static final class Tile extends TileEntity implements IGregTechTileEntity {
        final Host host;final int x;Tile(Host h,int x){host=h;this.x=x;}
        public World getWorld(){return world;}public int getXCoord(){return x;}public int getYCoord(){return 64;}public int getZCoord(){return 0;}
        public Object getMetaTileEntity(){return host;}public int getMetaTileID(){return 1;}
    }
}
