package com.miaokatze.gtsr.common.critical;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.util.ForgeDirection;

import com.google.gson.*;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.miaokatze.gtsr.client.critical.CriticalMaterialRenderer;
import com.miaokatze.gtsr.client.critical.CriticalMaterialTextures;

/** Detached production-method regression; sparse World and native GT types, no FML or game startup. */
public final class CriticalRuntimeV30Contract {
    private static sun.misc.Unsafe unsafe;
    private static int checks;
    private static final Block AIR = new Block(Material.air) {
        @Override public boolean isOpaqueCube() { return false; }
    };
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
    }
    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target, value);
    }
    @SuppressWarnings("unchecked")
    private static List<CriticalGeometry.Voxel> geometry(CriticalMachineState state) throws Exception {
        Method m = CriticalMachineState.class.getDeclaredMethod("allVoxels"); m.setAccessible(true);
        return (List<CriticalGeometry.Voxel>) m.invoke(state);
    }
    private static NBTTagCompound tag(int revision, ForgeDirection frame) {
        NBTTagCompound n = new NBTTagCompound();
        n.setInteger("geometryVersion", 2); n.setInteger("v2LayoutRevision", revision);
        n.setInteger("structureFacing", frame.ordinal());
        n.setBoolean("loomController", true); n.setBoolean("loomJournalOwner", true);
        n.setString("structureId", "11111111-1111-1111-1111-111111111111");
        n.setString("ownerUuid", "owner"); n.setString("jobId", "frozen-job"); n.setInteger("state", 2);
        n.setTag("config", new CriticalConfiguration(CriticalTier.T3, CriticalMachineKind.SUN, 0, 0, 0).write());
        return n;
    }
    private static CriticalMachineState state(int revision, SparseWorld world, ForgeDirection frame) {
        CriticalMachineState s = new CriticalMachineState(); s.readFromNBT(tag(revision, frame));
        s.worldObj = world; s.xCoord = 56; s.yCoord = 68; s.zCoord = 47; return s;
    }
    @SuppressWarnings("unchecked")
    private static void materials() throws Exception {
        Constructor<CriticalMaterials.MaterialBlock> c = CriticalMaterials.MaterialBlock.class.getDeclaredConstructor(
            String.class, boolean.class, boolean.class, boolean.class, boolean.class, boolean.class);
        c.setAccessible(true);
        IIcon icon = (IIcon) Proxy.newProxyInstance(IIcon.class.getClassLoader(), new Class<?>[] { IIcon.class },
            (p,m,a) -> switch(m.getName()) {
                case "getIconName" -> "fixture";
                case "getIconWidth", "getIconHeight" -> 32;
                case "getMinU", "getMinV" -> 0F;
                case "getMaxU", "getMaxV" -> 1F;
                case "getInterpolatedU", "getInterpolatedV" -> ((Double) a[0]) / 16D;
                default -> null;
            });
        IIconRegister register = name -> icon;
        for (Map.Entry<String, JsonElement> entry : CriticalGeometry.readJson("materials.json").entrySet()) {
            String id = entry.getValue().getAsJsonObject().get("id").getAsString();
            for (boolean proxy : new boolean[] { false, true }) for (boolean loom : new boolean[] { false, true }) {
                Field f = CriticalMaterials.class.getDeclaredField(loom ? proxy ? "LOOM_PROXY" : "LOOM_STATIC" : proxy ? "PROXY" : "STATIC");
                f.setAccessible(true); Map<String, Block> map = (Map<String, Block>) f.get(null);
                if (!map.containsKey(id)) map.put(id, c.newInstance(id, proxy, false, false, false, loom));
            }
            if (id.startsWith("v2_")) CriticalMaterialTextures.register(id, register);
        }
    }
    private static SparseWorld world() throws Exception {
        SparseWorld w = (SparseWorld) unsafe.allocateInstance(SparseWorld.class);
        w.blocks = new HashMap<>(); w.tiles = new HashMap<>(); w.playerEntities = new ArrayList<>();
        Field storage=World.class.getDeclaredField("perWorldStorage"); storage.setAccessible(true); storage.set(w,new MapStorage(null)); w.loaded = true;
        w.chunks = (IChunkProvider) Proxy.newProxyInstance(IChunkProvider.class.getClassLoader(), new Class<?>[] { IChunkProvider.class },
            (p,m,a) -> m.getName().equals("chunkExists") ? w.loaded : null);
        return w;
    }
    private static void put(CriticalMachineState s, CriticalGeometry.Voxel v, Block block) {
        ((SparseWorld) s.worldObj).blocks.put(key(s.blockWorldX(v), s.blockWorldY(v.y), s.blockWorldZ(v)), block);
    }
    private static TileEntityCriticalRenderAnchor anchor(CriticalMachineState s) throws Exception {
        NBTTagCompound n = new NBTTagCompound();
        n.setInteger("ownerX", s.xCoord); n.setInteger("ownerY", s.yCoord); n.setInteger("ownerZ", s.zCoord);
        NBTTagCompound visual = s.getVisualData(); visual.setBoolean("loomVisible", true); n.setTag("visual", visual);
        TileEntityCriticalRenderAnchor a = new TileEntityCriticalRenderAnchor(); a.setWorldObj(s.worldObj);
        a.xCoord = CriticalRenderAnchors.getX(s); a.yCoord = CriticalRenderAnchors.getY(s); a.zCoord = CriticalRenderAnchors.getZ(s);
        n.setInteger("x",a.xCoord); n.setInteger("y",a.yCoord); n.setInteger("z",a.zCoord);
        a.readFromNBT(n); return a;
    }
    private static boolean supply(boolean baseline) throws Exception {
        SparseWorld w = world(); w.isRemote = true;
        CriticalMachineState s = state(28, w, ForgeDirection.NORTH);
        TileEntityCriticalRenderAnchor anchor = anchor(s);
        CriticalLoomProxyVisibility.publishVisibleGroups(anchor, Set.of("v2_loom:interface"));
        boolean allRendered = true;
        for (CriticalGeometry.Voxel v : CriticalGeometry.getModel("v2_loom").voxels) {
            if (!v.material.equals("v2_gt_bus") && !v.material.equals("v2_gt_hatch")) continue;
            Block block = CriticalLoomStructure.shell(v); put(s, v, block);
            Tessellator.instance.startDrawingQuads();
            boolean rendered = new CriticalMaterialRenderer().renderWorldBlock(w, s.blockWorldX(v), s.blockWorldY(v.y),
                s.blockWorldZ(v), block, 0, null);
            allRendered &= rendered;
            Method reset = Tessellator.class.getDeclaredMethod("reset"); reset.setAccessible(true); reset.invoke(Tessellator.instance);
            Field drawing = Tessellator.class.getDeclaredField("isDrawing"); drawing.setAccessible(true); drawing.setBoolean(Tessellator.instance,false);
        }
        if (!baseline) {
            check(allRendered, "Published fixed interface retains native chunk geometry for every decorative supply block");
            check(!CriticalGeometry.isDynamic("v2_loom", "interface"), "Fixed interface cannot claim moving mesh ownership");
            CriticalGeometry.Voxel moving = CriticalGeometry.getModel("v2_loom").voxels.stream()
                .filter(v -> v.group.equals("loom_hoop_low")).findFirst().orElseThrow();
            Block shell = CriticalLoomStructure.shell(moving); put(s, moving, shell);
            int x=s.blockWorldX(moving), y=s.blockWorldY(moving.y), z=s.blockWorldZ(moving);
            CriticalLoomProxyVisibility.publishVisibleGroups(anchor, Set.of());
            check(!CriticalLoomProxyVisibility.animated(w,x,y,z,shell,0), "Incomplete moving group keeps native fallback");
            CriticalLoomProxyVisibility.publishVisibleGroups(anchor, Set.of("v2_loom:loom_hoop_low"));
            check(CriticalLoomProxyVisibility.animated(w,x,y,z,shell,0), "Complete moving group transfers ownership");
            int redraws=w.redraws;
            for(int i=0;i<100;i++) CriticalLoomProxyVisibility.publishVisibleGroups(anchor, Set.of("v2_loom:loom_hoop_low"));
            check(w.redraws==redraws,"Stable ownership performs no repeated region rebuild");
            CriticalLoomProxyVisibility.publishVisibleGroups(anchor,Set.of());
            check(!CriticalLoomProxyVisibility.animated(w,x,y,z,shell,0) && w.redraws>redraws,"Withdrawal redraws affected group and restores fallback");
            CriticalLoomProxyVisibility.remove(anchor);
            check(!CriticalLoomProxyVisibility.animated(w,x,y,z,shell,0),"Anchor unload releases ownership");
        }
        return allRendered;
    }
    private static TestGT hatch(SparseWorld w, CriticalMachineState s, CriticalGeometry.Voxel port, boolean supported) throws Exception {
        TestGT tile=(TestGT)unsafe.allocateInstance(TestGT.class);
        gregtech.api.metatileentity.MetaTileEntity meta=supported ? (gregtech.api.metatileentity.MetaTileEntity)unsafe.allocateInstance(
            gregtech.api.metatileentity.implementations.MTEHatchInputBus.class) : null;
        if(meta!=null) {Field base=gregtech.api.metatileentity.MetaTileEntity.class.getDeclaredField("mBaseMetaTileEntity");base.setAccessible(true);base.set(meta,tile);}
        tile.setMetaTileEntity(meta);
        w.tiles.put(key(s.blockWorldX(port),s.blockWorldY(port.y),s.blockWorldZ(port)),tile); put(s,port,AIR); return tile;
    }
    private static void portsAndProtection() throws Exception {
        for(ForgeDirection facing : new ForgeDirection[]{ForgeDirection.NORTH,ForgeDirection.EAST,ForgeDirection.SOUTH,ForgeDirection.WEST}) {
            SparseWorld w=world(); CriticalMachineState s=state(30,w,facing);
            List<CriticalGeometry.Voxel> geometry=geometry(s);
            long fingerprint=(Long)field(s,"modelFingerprint"); set(s,"jobFingerprint",fingerprint);
            List<CriticalGeometry.Voxel> optional=CriticalGeometry.optionalWorkPorts(s);
            check(optional.size()==13,"Exactly thirteen upper-row slots "+facing);
            check(s.matchesOptionalWorkPorts(),"Air upper row accepted "+facing);
            check(CriticalRenderAnchors.getY(s)==s.getOriginY()+6,"New anchor is above the full optional chamber row");
            check(!geometry.stream().anyMatch(v->s.blockWorldX(v)==CriticalRenderAnchors.getX(s)
                && s.blockWorldY(v.y)==CriticalRenderAnchors.getY(s)&&s.blockWorldZ(v)==CriticalRenderAnchors.getZ(s)),"New anchor does not replace a physical voxel");
            for(CriticalGeometry.Voxel port:optional) hatch(w,s,port,true);
            check(s.matchesOptionalWorkPorts(),"All thirteen chambers including center X56 are recognized together");
            Method clearance=CriticalMachineState.class.getDeclaredMethod("isClearForConstruction",CriticalGeometry.Voxel.class);clearance.setAccessible(true);
            Method manual=CriticalMachineState.class.getDeclaredMethod("isUserBuilt",CriticalGeometry.Voxel.class);manual.setAccessible(true);
            for(CriticalGeometry.Voxel port:optional) {
                check((Boolean)clearance.invoke(s,port),"First-start clearance accepts retained GT upper chamber");
                check((Boolean)manual.invoke(s,port),"GT upper chamber is excluded from paid repair/dismantle ownership");
            }
            w.tiles.clear();w.blocks.clear();
            CriticalGeometry.Voxel upper=optional.get(0);
            check(!geometry.stream().anyMatch(v->v.positionKey()==upper.positionKey()),"Optional slots do not enter placed indices");
            hatch(w,s,upper,true);
            check(s.matchesOptionalWorkPorts() && s.isStandardHatch(upper),"Real standard bus accepted at explicit optional slot");
            check(s.getResourceHatchPorts().size()==13 && s.getResourceHatchPorts().stream().allMatch(v->v.y==5 && v.z==34),"Discovery is upper row only for new layout");
            CriticalGeometry.Voxel lower=geometry.stream().filter(v->v.x==50 && v.y==4 && v.z==34).findFirst().orElseThrow();
            @SuppressWarnings("unchecked") Set<Long> clearancePositions=(Set<Long>)field(s,"clearanceTargetPositions");
            for(CriticalGeometry.Voxel port:optional) clearancePositions.add(port.positionKey());clearancePositions.add(lower.positionKey());
            Method playerTargets=CriticalMachineState.class.getDeclaredMethod("playerClearanceTargets");playerTargets.setAccessible(true);
            @SuppressWarnings("unchecked") Set<Long> occupied=(Set<Long>)playerTargets.invoke(s);
            check(occupied.size()==1&&occupied.contains(lower.positionKey()),"Player may occupy optional air slots while actual paid-cell occupancy stays checked");
            check(clearancePositions.size()==14,"Optional-slot world-content recheck ledger remains intact");
            hatch(w,s,lower,true);
            check(!s.isStandardHatch(lower),"New lower row cannot be replaced by a GT chamber");
            CriticalStructures.OptionalWorkPortElement element=new CriticalStructures.OptionalWorkPortElement();
            check(element.check(null,w,s.blockWorldX(upper),s.blockWorldY(upper.y),s.blockWorldZ(upper)),"Full definition optional element checks real GT host");
            w.tiles.remove(key(s.blockWorldX(upper),s.blockWorldY(upper.y),s.blockWorldZ(upper))); put(s,upper,lower.block);
            check(!s.matchesOptionalWorkPorts() && !element.check(null,w,s.blockWorldX(upper),s.blockWorldY(upper.y),s.blockWorldZ(upper)),"Unrelated block in upper row is rejected");
            w.blocks.remove(key(s.blockWorldX(upper),s.blockWorldY(upper.y),s.blockWorldZ(upper)));
            check(element.placeBlock(null,w,s.blockWorldX(upper),s.blockWorldY(upper.y),s.blockWorldZ(upper),null),"Construct leaves empty slot air");
            w.loaded=false; check(!s.matchesOptionalWorkPorts(),"Unloaded optional slot cannot validate"); w.loaded=true;
            IStructureDefinition<?> definition=CriticalStructures.createV2(s,false,true);
            check(definition.isContainedInStructure(CriticalStructures.piece(CriticalTier.T3),
                CriticalStructureAlignment.shapeA(upper.x,true),CriticalStructures.voxels(s).stream().mapToInt(v->v.y).max().orElseThrow()-upper.y,
                CriticalStructureAlignment.shapeC(upper.z,true)),"StructureLib declares formerly empty optional slot");
            BitSet placed=(BitSet)field(s,"placed"); int paidIndex=geometry.indexOf(lower); placed.set(paidIndex);
            w.tiles.clear(); put(s,lower,lower.block);
            int x=s.blockWorldX(lower),y=s.blockWorldY(lower.y),z=s.blockWorldZ(lower);
            check(s.protectsPlayerBreak(x,y,z),"Paid ledger cell protected "+facing);
            check(!s.protectsPlayerBreak(x+1000,y,z),"Identical arbitrary outside block not protected");
            CriticalGeometry.Voxel loom=geometry.stream().filter(CriticalLoomStructure::isLoomVoxel).findFirst().orElseThrow();
            placed.set(geometry.indexOf(loom));put(s,loom,CriticalLoomStructure.shell(loom));
            check(!s.protectsPlayerBreak(s.blockWorldX(loom),s.blockWorldY(loom.y),s.blockWorldZ(loom)),"Permanent loom remains player dismantlable");
            CriticalGeometry.Voxel unowned=geometry.stream().filter(v->!CriticalLoomStructure.isLoomVoxel(v)&&v!=lower&&!CriticalStructures.isController(v)).findFirst().orElseThrow();
            put(s,unowned,unowned.block);
            check(!s.protectsPlayerBreak(s.blockWorldX(unowned),s.blockWorldY(unowned.y),s.blockWorldZ(unowned)),"Undeclared ledger copy inside region is not protected");
            CriticalWorldData data=CriticalWorldData.get(w);
            check(data.acquire(s.getStructureId(),"owner",s.getOriginX(),s.getOriginY(),s.getOriginZ(),s.getRegionSize(),s.getRegionHeight(),s.xCoord,s.yCoord,s.zCoord),"Claim acquired");
            check(data.bindTask(s.getStructureId(),"owner",s.getJobId(),s.getOriginX(),s.getOriginY(),s.getOriginZ()),"Exact task bound");data.updateProtection(s);
            check(data.protectsPlayerBreak(w,x,y,z),"Unloaded-controller claim snapshot protects paid cell");
            NBTTagCompound saved=new NBTTagCompound();data.writeToNBT(saved);CriticalWorldData restored=new CriticalWorldData();restored.readFromNBT(saved);
            check(restored.protectsPlayerBreak(w,x,y,z),"Persisted world claim protects after NBT reload");
            // The Forge harvest-XP constructor bootstraps every vanilla item. Supply its captured event fields instead.
            FixtureBreakEvent event=(FixtureBreakEvent)unsafe.allocateInstance(FixtureBreakEvent.class);
            for(String name:new String[]{"x","y","z","world","block","blockMetadata"}) {
                Field captured=net.minecraftforge.event.world.BlockEvent.class.getDeclaredField(name);captured.setAccessible(true);
                captured.set(event,switch(name){case "x"->x;case "y"->y;case "z"->z;case "world"->w;case "block"->lower.block;default->0;});
            }
            new CriticalRuntime.StructureChangeListener().onControllerBreak(event);check(event.isCanceled(),"Production BreakEvent denies player harvest for paid cell");
            net.minecraftforge.event.entity.player.PlayerInteractEvent interact=new FixtureInteractEvent(x,y,z,w);
            new CriticalRuntime.StructureChangeListener().onPlayerInteract(interact);check(interact.isCanceled(),"Production PlayerInteract denies destructive tool use on paid material");
            check(w.setBlockToAir(x,y,z),"Programmatic controller teardown remains permitted");
            check(!data.protectsPlayerBreak(w,x,y,z),"Removed cell no longer protected");
            hatch(w,s,upper,true);check(!data.protectsPlayerBreak(w,s.blockWorldX(upper),s.blockWorldY(upper.y),s.blockWorldZ(upper)),"Real upper GT chamber is maintainable");
            interact=new FixtureInteractEvent(s.blockWorldX(upper),s.blockWorldY(upper.y),s.blockWorldZ(upper),w);
            new CriticalRuntime.StructureChangeListener().onPlayerInteract(interact);check(!interact.isCanceled(),"GT chamber interaction remains usable");
            TileEntity ownerHost=new TileEntity();ownerHost.setWorldObj(w);ownerHost.xCoord=s.xCoord;ownerHost.yCoord=s.yCoord;ownerHost.zCoord=s.zCoord;
            w.tiles.put(key(s.xCoord,s.yCoord,s.zCoord),ownerHost);s.bindHost(ownerHost);
            for(CriticalGeometry.Voxel port:optional) hatch(w,s,port,true);
            CriticalRenderAnchors.ensure(s,true);
            TileEntity visual=w.getTileEntity(CriticalRenderAnchors.getX(s),CriticalRenderAnchors.getY(s),CriticalRenderAnchors.getZ(s));
            check(visual instanceof TileEntityCriticalRenderAnchor && ((TileEntityCriticalRenderAnchor)visual).ownedBy(s),"Actual anchor placement succeeds above thirteen installed chambers");
            check(s.matchesOptionalWorkPorts(),"Anchor placement leaves center chamber and all optional ports intact");
            CriticalRenderAnchors.remove(s);
            check(s.matchesOptionalWorkPorts(),"Anchor removal preserves all real GT chambers");
            CriticalGeometry.Voxel center=optional.get(6);w.setBlockToAir(s.blockWorldX(center),s.blockWorldY(center.y),s.blockWorldZ(center));
            TileEntityCriticalRenderAnchor old=anchor(s);old.yCoord--;
            w.tiles.put(key(old.xCoord,old.yCoord,old.zCoord),old);w.blocks.put(key(old.xCoord,old.yCoord,old.zCoord),CriticalRenderAnchors.BLOCK);
            CriticalRenderAnchors.ensure(s,true);
            check(w.isAirBlock(s.blockWorldX(center),s.blockWorldY(center.y),s.blockWorldZ(center)),"New idle-layout anchor upgrade clears only its own obsolete center anchor");
            check(w.getTileEntity(CriticalRenderAnchors.getX(s),CriticalRenderAnchors.getY(s),CriticalRenderAnchors.getZ(s)) instanceof TileEntityCriticalRenderAnchor,"New anchor restored after obsolete owned anchor cleanup");
            data.release(s.getStructureId());check(!data.protectsPlayerBreak(w,x,y,z),"Released region grants no protection");
        }
        for(int revision:new int[]{26,27,28,29}) {
            SparseWorld w=world();CriticalMachineState s=state(revision,w,ForgeDirection.NORTH);
            List<CriticalGeometry.Voxel> cells=geometry(s);
            CriticalGeometry.Voxel lower=cells.stream().filter(v->v.x==50&&v.y==4&&v.z==34).findFirst().orElseThrow();hatch(w,s,lower,true);
            check(s.isStandardHatch(lower)&&CriticalGeometry.optionalWorkPorts(s).isEmpty(),"Released work port retained revision "+revision);
            check(CriticalRenderAnchors.getY(s)==s.getOriginY()+5,"Released render anchor stays at old coordinate revision "+revision);
            check(s.getResourceHatchPorts().stream().anyMatch(v->v.positionKey()==lower.positionKey()),"Released discovery retained revision "+revision);
            NBTTagCompound n=new NBTTagCompound();s.writeToNBT(n);
            check(n.getInteger("v2LayoutRevision")==revision,"Commissioned layout never migrates revision "+revision);
        }
    }
    private static void frozenIndices(Path expected) throws Exception {
        for(JsonElement e:JsonParser.parseString(Files.readString(expected)).getAsJsonArray()) {
            JsonObject row=e.getAsJsonObject();int revision=row.get("revision").getAsInt();
            CriticalMachineState s=state(revision,world(),ForgeDirection.NORTH);List<CriticalGeometry.Voxel> cells=geometry(s);
            StringBuilder text=new StringBuilder();for(CriticalGeometry.Voxel v:cells)
                text.append(v.x).append(',').append(v.y).append(',').append(v.z).append(',').append(v.stage).append(',').append(v.material).append(',').append(v.group).append('\n');
            String digest=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            check(cells.size()==row.get("count").getAsInt() && digest.equals(row.get("indexSHA256").getAsString()),"Exact frozen ordering/placed indices revision "+revision);
            check((Long)field(s,"modelFingerprint")==row.get("fingerprint").getAsLong(),"Exact frozen model fingerprint revision "+revision);
            byte[] bytes={1,8,64};((BitSet)field(s,"placed")).or(BitSet.valueOf(bytes));
            NBTTagCompound n=new NBTTagCompound();s.writeToNBT(n);
            check(Arrays.equals(n.getByteArray("placed"),bytes),"Placed bitset survives production save revision "+revision);
        }
    }
    public static void main(String[] args) throws Exception {
        Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(sun.misc.Unsafe)f.get(null);
        materials(); boolean baseline=args.length>1 && args[1].equals("baseline"); boolean supplyRendered=supply(baseline);
        if(baseline) check(!supplyRendered,"Baseline reproduces vanished decorative supply warehouse after interface ownership transfer");
        else {portsAndProtection();frozenIndices(Path.of(args[0]).getParent().getParent().resolve("frozen-indices.json"));}
        JsonObject report=new JsonObject();report.addProperty("status","PASS");report.addProperty("checks",checks);
        report.addProperty("baselineReproduced",baseline);report.addProperty("decorativeSuppliesRendered",supplyRendered);
        report.addProperty("productionCodeSource",CriticalGeometry.class.getProtectionDomain().getCodeSource().getLocation().toString());
        report.addProperty("materialSupportCodeSource",CriticalMaterials.class.getProtectionDomain().getCodeSource().getLocation().toString());
        report.addProperty("mode","Production Java methods; sparse World and GT host fixtures; no full Minecraft/FML/server/client startup");
        Files.writeString(Path.of(args[0]),new GsonBuilder().setPrettyPrinting().create().toJson(report));System.out.println(report);
    }
    private static String key(int x,int y,int z){return x+","+y+","+z;}
    private static final class TestGT extends gregtech.api.metatileentity.BaseMetaTileEntity {
    }
    private static final class FixtureBreakEvent extends net.minecraftforge.event.world.BlockEvent.BreakEvent {
        private FixtureBreakEvent(){super(0,0,0,null,null,0,null);}
        @Override public boolean isCancelable(){return true;}
    }
    private static final class FixtureInteractEvent extends net.minecraftforge.event.entity.player.PlayerInteractEvent {
        private FixtureInteractEvent(int x,int y,int z,World world){super(null,Action.RIGHT_CLICK_BLOCK,x,y,z,1,world);}
        @Override public boolean isCancelable(){return true;}
    }
    private static final class SparseWorld extends World {
        Map<String,Block> blocks;Map<String,TileEntity> tiles;IChunkProvider chunks;boolean loaded;int redraws;
        private SparseWorld(){super(null,"unused",(WorldProvider)null,null,null);}
        @Override public Block getBlock(int x,int y,int z){return blocks.getOrDefault(key(x,y,z),AIR);}
        @Override public int getBlockMetadata(int x,int y,int z){return 0;}
        @Override public boolean isAirBlock(int x,int y,int z){return !blocks.containsKey(key(x,y,z));}
        @Override public TileEntity getTileEntity(int x,int y,int z){return tiles.get(key(x,y,z));}
        @Override public boolean blockExists(int x,int y,int z){return loaded;}
        @Override public IChunkProvider getChunkProvider(){return chunks;}
        @Override public long getTotalWorldTime(){return 0;}
        @Override public int getLightBrightnessForSkyBlocks(int x,int y,int z,int min){return 0x00f000f0;}
        @Override public boolean setBlock(int x,int y,int z,Block block,int metadata,int flags) {
            blocks.put(key(x,y,z),block);
            if(block==CriticalRenderAnchors.BLOCK){TileEntityCriticalRenderAnchor a=new TileEntityCriticalRenderAnchor();a.setWorldObj(this);a.xCoord=x;a.yCoord=y;a.zCoord=z;tiles.put(key(x,y,z),a);}
            return true;
        }
        @Override public boolean setBlockToAir(int x,int y,int z){blocks.remove(key(x,y,z));tiles.remove(key(x,y,z));return true;}
        @Override public void markBlockForUpdate(int x,int y,int z){}
        @Override public void markTileEntityChunkModified(int x,int y,int z,TileEntity tile){}
        @Override public void func_147453_f(int x,int y,int z,Block block){}
        @Override public void markBlockRangeForRenderUpdate(int x,int y,int z,int xx,int yy,int zz){redraws++;}
        @Override protected IChunkProvider createChunkProvider(){throw new UnsupportedOperationException();}
        @Override protected int func_152379_p(){throw new UnsupportedOperationException();}
        @Override public Entity getEntityByID(int id){throw new UnsupportedOperationException();}
    }
}
