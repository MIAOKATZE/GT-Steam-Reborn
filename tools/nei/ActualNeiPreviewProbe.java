package audit;

import cpw.mods.fml.common.*;
import cpw.mods.fml.common.event.*;
import cpw.mods.fml.common.eventhandler.*;
import cpw.mods.fml.common.gameevent.*;
import com.google.gson.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/** Full registered client: invokes the unchanged BR GUI world construction path. */
@Mod(modid="actualneipreviewaudit", name="Actual NEI preview audit", version="1",
    dependencies="after:gregtech;after:gtsr;after:NotEnoughItems;after:blockrenderer6343")
public final class ActualNeiPreviewProbe {
    final JsonObject receipt = new JsonObject();
    final JsonArray cases = new JsonArray();
    final Map<Long,Object> observed = new LinkedHashMap<Long,Object>();
    boolean done;
    boolean catalogRequested;
    boolean worldRequested;
    int ticks;
    Object mc;

    static Class<?> type(String name) throws Exception { return Class.forName(name); }
    static Object field(Object target, String names) throws Exception {
        for (String name : names.split("\\|"))
            for (Class<?> c = target instanceof Class ? (Class<?>) target : target.getClass(); c != null; c = c.getSuperclass())
                try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f.get(target instanceof Class ? null : target); }
                catch (NoSuchFieldException ignored) {}
        throw new NoSuchFieldException(names);
    }
    static void set(Object target, String name, Object value) throws Exception {
        for (Class<?> c = target instanceof Class ? (Class<?>) target : target.getClass(); c != null; c = c.getSuperclass())
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); f.set(target instanceof Class ? null : target, value); return; }
            catch (NoSuchFieldException ignored) {}
        throw new NoSuchFieldException(name);
    }
    static Object call(Object target, String names, Object... args) throws Exception {
        Class<?> root = target instanceof Class ? (Class<?>) target : target.getClass();
        for (String name : names.split("\\|")) for (Class<?> c = root; c != null; c = c.getSuperclass())
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterTypes().length != args.length) continue;
                boolean fits = true; Class<?>[] ts = m.getParameterTypes();
                for (int i = 0; i < ts.length; i++) if (args[i] != null && !ts[i].isInstance(args[i])
                    && !(ts[i].isPrimitive() && args[i] instanceof Number) && !(ts[i] == boolean.class && args[i] instanceof Boolean)) fits = false;
                if (fits) { m.setAccessible(true); return m.invoke(target instanceof Class ? null : target, args); }
            }
        throw new NoSuchMethodException(root + "." + names);
    }
    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        FMLCommonHandler.instance().bus().register(this);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
    }
    @SubscribeEvent public void visited(com.gtnewhorizon.structurelib.StructureEvent.StructureElementVisitedEvent event) {
        if ("blockrenderer6343".equals(event.getInstrumentIdentifier()))
            observed.put(com.gtnewhorizon.gtnhlib.util.CoordinatePacker.pack(event.getX(), event.getY(), event.getZ()), event.getElement());
    }
    @SubscribeEvent public void client(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END) return;
        try {
            mc = call(type("net.minecraft.client.Minecraft"), "getMinecraft|func_71410_x");
            if (++ticks < 100) return;
            if (!worldRequested) {
                if (field(mc, "currentScreen|field_71462_r") == null) return;
                worldRequested = true;
                Class<?> settings = type("net.minecraft.world.WorldSettings"), gameType = type("net.minecraft.world.WorldSettings$GameType");
                Object config = settings.getConstructor(long.class, gameType, boolean.class, boolean.class, type("net.minecraft.world.WorldType"))
                    .newInstance(61312145L, Enum.valueOf((Class) gameType,"CREATIVE"), false, false, field(type("net.minecraft.world.WorldType"),"FLAT|field_77138_c"));
                call(mc, "launchIntegratedServer|func_71371_a", "ActualNeiPreviewAuditBaseline", "Actual NEI Preview Audit Baseline", config);
                return;
            }
            Object player = field(mc, "thePlayer|field_71439_g");
            if (player == null || ((Number) field(player,"ticksExisted|field_70173_aa")).intValue() < 60) {
                if (ticks > 2000) throw new AssertionError("Owned integrated player did not become ready"); return;
            }
            if (!catalogRequested) {
                catalogRequested = true;
                call(type("codechicken.nei.ItemList"), "loadItems");
                return;
            }
            Object[] registry = (Object[]) field(type("gregtech.api.GregTechAPI"), "METATILEENTITIES");
            Object neiItems = field(type("codechicken.nei.ItemList"), "items");
            if (!Boolean.TRUE.equals(field(type("codechicken.nei.ItemList"), "loadFinished")) || ((Collection<?>) neiItems).size() < 1000) {
                if (ticks > 3000) throw new AssertionError("NEI catalog did not finish loading"); return;
            }
            receipt.addProperty("scope", "Unmodified GTGuiMultiblockHandler.loadMultiblock -> placeMultiblock -> registered machine survivalConstruct -> BR CreativeItemSource -> real TrackedDummyWorld; no GL draw assertion");
            receipt.addProperty("neiCatalogSize", ((Collection<?>) neiItems).size());
            JsonObject sources = new JsonObject();
            for (String cls : new String[] { "gregtech.api.util.HatchElementBuilder", "com.gtnewhorizon.structurelib.structure.StructureDefinition", "blockrenderer6343.integration.gregtech.GTGuiMultiblockHandler", "com.miaokatze.gtsr.common.machine.MTEMegaSteamTurbineArray" })
                sources.addProperty(cls, type(cls).getProtectionDomain().getCodeSource().getLocation().toString());
            receipt.add("runtimeSources", sources);
            for (String name : new String[] { "MTEMegaSteamTurbineArray", "MTESingularityDrillingHub" }) {
                Object prototype = null;
                for (Object p : registry) if (p != null && p.getClass().getSimpleName().equals(name)) { prototype = p; break; }
                if (prototype == null) throw new AssertionError("Unregistered " + name);
                audit(name, prototype);
            }
            if (!Boolean.getBoolean("nei.audit.expectRed")) {
                if ("all".equals(System.getProperty("nei.audit.matrix"))) {
                    int ordinary = 0;
                    Class<?> multi = type("gregtech.api.metatileentity.implementations.MTEMultiBlockBase");
                    Class<?> critical = type("com.miaokatze.gtsr.common.machine.MTECriticalMachine");
                    for (Object id : type("com.miaokatze.gtsr.common.api.enums.MetaTileEntityID").getEnumConstants()) {
                        Object p = registry[((Number) field(id, "ID")).intValue()];
                        if (!multi.isInstance(p) || critical.isInstance(p)) continue;
                        ordinary++;
                        if (p.getClass().getSimpleName().equals("MTEMegaSteamTurbineArray") || p.getClass().getSimpleName().equals("MTESingularityDrillingHub")) continue;
                        audit(((Enum<?>) id).name(), p);
                    }
                    receipt.addProperty("ordinaryRegisteredEntries", ordinary);
                    if (ordinary != 38) throw new AssertionError("Expected 38 ordinary entries, got " + ordinary);
                }
                for (String name : new String[] { "MTEMegaSteamTurbineArray", "MTESingularityDrillingHub" }) {
                    Object p = null; for (Object q : registry) if (q != null && q.getClass().getSimpleName().equals(name)) { p = q; break; }
                    auditScenario(name + ".special-first", p, true, false);
                    auditScenario(name + ".maximum", p, false, true);
                }
                for (Object p : registry) if (p != null && p.getClass().getSimpleName().equals("MTELargeTurbineSteam")) {
                    audit("GT205.MTELargeTurbineSteam", p); break;
                }
            }
            finish(null);
        } catch (Throwable failure) { failure.printStackTrace(); finish(failure); }
    }
    void audit(String name, Object prototype) throws Exception {
        auditScenario(name, prototype, false, false);
    }
    void auditScenario(String name, Object prototype, boolean poison, boolean maximum) throws Exception {
        Object source = field(type("blockrenderer6343.api.utils.CreativeItemSource"), "instance");
        Object originalCatalog = field(type("codechicken.nei.ItemList"), "items");
        Object originalSourceItems = field(source, "itemList");
        Map<Object,Object> temp = (Map<Object,Object>) field(source, "tempItemList");
        Map<Object,Object> originalTemp = new LinkedHashMap<Object,Object>(temp);
        if (poison) {
            List<Object> priority = new ArrayList<Object>(), rest = new ArrayList<Object>();
            for (Object item : (List<?>) originalCatalog) {
                Object mte = call(type("gregtech.common.blocks.ItemMachines"), "getMetaTileEntity", item);
                if (mte != null && mte.getClass().getName().startsWith("com.miaokatze.gtsr.common.machine")) priority.add(item); else rest.add(item);
            }
            Collections.reverse(priority); Collections.reverse(rest); priority.addAll(rest);
            set(type("codechicken.nei.ItemList"), "items", priority); set(source,"itemList",null); temp.clear();
        }
        try { auditWorld(name, prototype, maximum, poison); }
        finally { if (poison) { set(type("codechicken.nei.ItemList"),"items",originalCatalog); set(source,"itemList",originalSourceItems); temp.clear(); temp.putAll(originalTemp); } }
    }
    static void leaves(Object element, List<Object> nativeElements, Set<Object> seen) throws Exception {
        if (element == null || !seen.add(element)) return;
        if (element instanceof com.gtnewhorizon.structurelib.structure.IStructureElementChain)
            for (Object e : ((com.gtnewhorizon.structurelib.structure.IStructureElementChain<?>) element).fallbacks()) leaves(e,nativeElements,seen);
        else if (element.getClass().getName().startsWith("gregtech.api.util.HatchElementBuilder$")) nativeElements.add(element);
        else for (Class<?> c = element.getClass(); c != null; c = c.getSuperclass()) for (Field f : c.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || !com.gtnewhorizon.structurelib.structure.IStructureElement.class.isAssignableFrom(f.getType())) continue;
            f.setAccessible(true); leaves(f.get(element),nativeElements,seen);
        }
    }
    static int offset(Object machine, String... names) throws Exception {
        for(String name:names) try {return ((Number)field(machine,name)).intValue();}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException("No structure origin on " + machine.getClass());
    }
    List<Object[]> plannedPieces(Object machine, Object trigger, boolean maximum) throws Exception {
        String name=machine.getClass().getSimpleName(); List<Object[]> result=new ArrayList<Object[]>();
        if(name.equals("MTEMegaSteamTurbineArray")) {
            result.add(new Object[]{"base",6,5,0}); int extra=maximum?4:0;
            for(int i=0;i<extra;i++)result.add(new Object[]{"stack",6,9+i*4,0});
            result.add(new Object[]{"cap",6,7+extra*4,0}); return result;
        }
        if(type("com.miaokatze.gtsr.common.machine.cluster.MTEClusterUnitBase").isInstance(machine)) {
            result.add(new Object[]{"main",call(machine,"getStructureOffsetA"),call(machine,"getStructureOffsetB"),call(machine,"getStructureOffsetC")}); return result;
        }
        if(name.equals("MTESteamMineralLogisticsCluster")) {
            Class<?> def=type("com.miaokatze.gtsr.common.machine.cluster.ClusterStructureDef");
            result.add(new Object[]{"main",call(def,"mainOffsetA"),call(def,"mainOffsetB"),call(def,"mainOffsetC")}); return result;
        }
        if(name.equals("MTELargeTurbineSteam")) {result.add(new Object[]{"main",1,1,1});return result;}
        int a=offset(machine,"HORIZONTAL_OFF_SET","OFFSET_X"), b=offset(machine,"VERTICAL_OFF_SET","OFFSET_Y"), c=offset(machine,"DEPTH_OFF_SET","OFFSET_Z");
        if(name.equals("MTESteamHubArray")||name.equals("MTEWaterHubArray")) {
            boolean steam=name.equals("MTESteamHubArray"); int height=((Number)call(machine,"getConstructTotalHeight",trigger)).intValue();
            result.add(new Object[]{"base",a,b,c}); for(int i=0;i<height-(steam?2:1);i++)result.add(new Object[]{"stack",a,1+i,c});
            if(steam)result.add(new Object[]{"cap",a,-1,c});
        } else result.add(new Object[]{"main",a,b,c});
        return result;
    }
    static Map<?,?> quotaMap(Object object, Set<Object> seen) throws Exception {
        if (object == null || !seen.add(object)) return null;
        if (object instanceof Map) {
            Map<?,?> map = (Map<?,?>) object;
            if (!map.isEmpty() && map.keySet().iterator().next() instanceof gregtech.api.interfaces.IHatchElement && map.values().iterator().next() instanceof Number) return map;
            return null;
        }
        if (object.getClass().getName().startsWith("java.")) return null;
        for (Field f : object.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
            f.setAccessible(true); Map<?,?> found = quotaMap(f.get(object),seen); if (found != null) return found;
        }
        return null;
    }
    void auditWorld(String name, Object prototype, boolean maximum, boolean poison) throws Exception {
        Object handler = type("blockrenderer6343.integration.gregtech.GTGuiMultiblockHandler").newInstance();
        // A fresh handler needs its normal initGui/loadNewMultiblock path even when revisiting one controller.
        set(handler.getClass(),"lastRenderingController",null);
        call(handler, "setOnCandidateChanged", (Consumer<Object>) value -> {});
        call(handler, "setOnIngredientChanged", (Consumer<Object>) value -> {});
        Object item = call(prototype, "getStackForm", 1L);
        Object data = call(type("blockrenderer6343.client.utils.ConstructableData"), "getTierData", prototype);
        observed.clear();
        call(handler, "loadMultiblock", prototype, item, data);
        String className = prototype.getClass().getSimpleName();
        if (maximum && className.equals("MTEMegaSteamTurbineArray")) {
            call(handler,"setChannelTier","casing",12,false); call(handler,"setChannelTier","pipe",3,false);
            call(handler,"setChannelTier","gear",2,false); call(handler,"setChannelTier","height",17,false);
            observed.clear(); call(handler,"initializeSceneRenderer",false);
        }
        Object renderer = field(handler, "renderer"), world = field(renderer, "world");
        Object pos = field(handler, "MB_PLACE_POS");
        Object host = call(world, "getTileEntity|func_147438_o", field(pos,"x"), field(pos,"y"), field(pos,"z"));
        Object machine = call(host, "getMetaTileEntity");
        Object definition = call(machine, "getStructureDefinition");
        Map<?,?> definitionElements = (Map<?,?>) call(definition,"getElements");
        Object trigger = call(handler,"getBuildTriggerStack");
        Object environment = field(type("blockrenderer6343.client.utils.BRUtil"),"AUTO_PLACE_ENVIRONMENT");
        Map<Object,Character> shellElements = new IdentityHashMap<Object,Character>();
        Map<Object,List<net.minecraft.item.ItemStack>> casingChoices = new IdentityHashMap<Object,List<net.minecraft.item.ItemStack>>();
        Map<Object,List<gregtech.api.interfaces.IHatchElement>> acceptedFamilies = new IdentityHashMap<Object,List<gregtech.api.interfaces.IHatchElement>>();
        JsonArray quotaEvidence = new JsonArray(); int quotaUpper = 0, unboundedBranches = 0;
        boolean clusterUnit = type("com.miaokatze.gtsr.common.machine.cluster.MTEClusterUnitBase").isInstance(machine);
        for (Map.Entry<?,?> e : definitionElements.entrySet()) {
            char symbol = (Character) e.getKey();
            if ((className.equals("MTESteamHubArray") || className.equals("MTEWaterHubArray")) && symbol == 'A') continue;
            List<Object> nativeElements = new ArrayList<Object>(); leaves(e.getValue(), nativeElements, Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>()));
            // Processing-only cluster units have pure A casings and dedicated P energy positions.
            if (nativeElements.isEmpty() && !(clusterUnit && symbol=='A')) continue;
            com.gtnewhorizon.structurelib.structure.IStructureElement raw = (com.gtnewhorizon.structurelib.structure.IStructureElement) e.getValue();
            com.gtnewhorizon.structurelib.structure.IStructureElement.BlocksToPlace candidates = raw.getBlocksToPlace(machine,(net.minecraft.world.World)world,0,0,0,(net.minecraft.item.ItemStack)trigger,(com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment)environment);
            List<net.minecraft.item.ItemStack> casings = new ArrayList<net.minecraft.item.ItemStack>();
            if (candidates != null && candidates.getStacks() != null) for (Object candidate : candidates.getStacks()) {
                net.minecraft.item.ItemStack stack = (net.minecraft.item.ItemStack) candidate;
                Object stackItem = call(stack,"getItem|func_77973_b");
                if (stackItem instanceof net.minecraft.item.ItemBlock && !(stackItem instanceof gregtech.common.blocks.ItemMachines)) casings.add(stack);
            }
            if (casings.isEmpty()) continue;
            shellElements.put(e.getValue(),symbol); casingChoices.put(e.getValue(),casings);
            List<gregtech.api.interfaces.IHatchElement> families = new ArrayList<gregtech.api.interfaces.IHatchElement>();
            for (Object nativeElement : nativeElements) {
                Object builder = field(nativeElement,"this$0"), filter = field(builder,"mHatchItemFilter");
                Map<?,?> quotas = quotaMap(filter,Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>()));
                if (quotas == null) { unboundedBranches++; continue; }
                for (Map.Entry<?,?> quota : quotas.entrySet()) {
                    gregtech.api.interfaces.IHatchElement family = (gregtech.api.interfaces.IHatchElement) quota.getKey();
                    families.add(family); int required = ((Number) quota.getValue()).intValue(); quotaUpper += required;
                    JsonObject row = new JsonObject(); row.addProperty("symbol",String.valueOf(symbol)); row.addProperty("family",family.name());
                    row.addProperty("quota",required); row.addProperty("registeredCount",family.count(machine)); quotaEvidence.add(row);
                }
            }
            acceptedFamilies.put(e.getValue(),families);
        }
        final Map<Long,Object> canonical = new LinkedHashMap<Long,Object>();
        List<Object[]> plans=plannedPieces(machine,trigger,maximum);
        final int originX=((Number)field(pos,"x")).intValue(), originY=((Number)field(pos,"y")).intValue(), originZ=((Number)field(pos,"z")).intValue();
        for(Object[] plan:plans) ((com.gtnewhorizon.structurelib.structure.IStructureDefinition)definition).iterate(
            (String)plan[0], (net.minecraft.world.World)world,
            ((gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase)machine).getExtendedFacing(),
            originX,originY,originZ,((Number)plan[1]).intValue(),((Number)plan[2]).intValue(),((Number)plan[3]).intValue(),
            (element,ignoredWorld,x,y,z,a,b,c)->{if(x!=originX||y!=originY||z!=originZ)canonical.put(com.gtnewhorizon.gtnhlib.util.CoordinatePacker.pack(x,y,z),element);return true;});
        Map<?,?> elements = canonical;
        Map<?,?> blocks = (Map<?,?>) field(world, "blockMap"), metas = (Map<?,?>) field(world, "blockMetaMap"), tiles = (Map<?,?>) field(world, "tileMap");
        JsonArray positions = new JsonArray(); Map<String,Integer> kinds = new TreeMap<String,Integer>();
        int shells = 0, hatches = 0, wrong = 0, steelCasings = 0;
        Object steel = field(type("gregtech.api.GregTechAPI"), "sBlockCasings2");
        for (Map.Entry<?,?> entry : elements.entrySet()) {
            if (!shellElements.containsKey(entry.getValue())) continue;
            Object key = entry.getKey(), tile = tiles.get(key), block = blocks.get(key);
            Object hatch = tile == null ? null : call(tile, "getMetaTileEntity");
            String kind = hatch == null ? String.valueOf(call(type("net.minecraft.block.Block"), "getIdFromBlock|func_149682_b", block)) + ":" + metas.get(key) : hatch.getClass().getSimpleName();
            kinds.put(kind, kinds.containsKey(kind) ? kinds.get(kind) + 1 : 1);
            boolean valid = false;
            if (hatch != null) {
                hatches++;
                for (gregtech.api.interfaces.IHatchElement family : acceptedFamilies.get(entry.getValue())) if (family.matchesHatch((gregtech.api.interfaces.metatileentity.IMetaTileEntity) hatch)) valid = true;
            } else for (net.minecraft.item.ItemStack choice : casingChoices.get(entry.getValue())) {
                Object expectedBlock = call(type("net.minecraft.block.Block"),"getBlockFromItem|func_149634_a",call(choice,"getItem|func_77973_b"));
                if (block == expectedBlock && ((Number)metas.get(key)).intValue() == ((Number)call(choice,"getItemDamage|func_77960_j")).intValue()) { valid = true; shells++; break; }
            }
            if (!valid) wrong++;
            if (hatch == null && block == steel && ((Number) metas.get(key)).intValue() == 0) steelCasings++;
            long packed = ((Number) key).longValue();
            JsonObject p = new JsonObject(); p.addProperty("packedPosition", packed); p.addProperty("kind", kind);
            p.addProperty("symbol",String.valueOf(shellElements.get(entry.getValue()))); p.addProperty("valid",valid);
            p.addProperty("x", com.gtnewhorizon.gtnhlib.util.CoordinatePacker.unpackX(packed));
            p.addProperty("y", com.gtnewhorizon.gtnhlib.util.CoordinatePacker.unpackY(packed));
            p.addProperty("z", com.gtnewhorizon.gtnhlib.util.CoordinatePacker.unpackZ(packed)); positions.add(p);
        }
        JsonObject result = new JsonObject(); result.addProperty("machine", name); result.addProperty("replaceablePositions", positions.size());
        result.addProperty("class",prototype.getClass().getName()); result.addProperty("casings", shells); result.addProperty("steelCasings", steelCasings); result.addProperty("hatches", hatches);
        result.addProperty("wrongPositions",wrong); result.addProperty("quotaUpperBound",quotaUpper); result.addProperty("unboundedOrdinaryBranches",unboundedBranches);
        result.add("quotaCounters",quotaEvidence); result.addProperty("specialFirstCatalog",poison); result.addProperty("maximumConfiguration",maximum);
        result.addProperty("rawInstrumentVisitedPositions",observed.size());
        result.addProperty("coordinateSource","Current live definition.iterate over native selected piece origins, read from unchanged BR-created world; independent of transient definition identity and speculative checkMachine visits");
        result.addProperty("configuration",maximum && className.equals("MTEMegaSteamTurbineArray") ? "casing=12,pipe=3,gear=2,height=17" : maximum && className.equals("MTESingularityDrillingHub") ? "fixed geometry; same maximum and default" : "native default trigger");
        JsonObject channelValues = new JsonObject();
        for(String channel : new String[]{"casing","pipe","gear","height"}) channelValues.addProperty(channel,((Number)call(type("com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor"),"getChannelData",trigger,channel)).intValue());
        result.add("triggerChannels",channelValues);
        result.add("placedKinds", new Gson().toJsonTree(kinds)); result.add("positions", positions);
        boolean bug = shells < 8 && hatches > 100;
        int expected = 0;
        for(Object[] plan:plans) for(Object element : (Object[])call(definition,"getStructureFor",plan[0])) if(shellElements.containsKey(element)) expected++;
        result.addProperty("expectedReplaceablePositions",expected);
        JsonObject lists = new JsonObject();
        for(String f : new String[]{"mInputHatches","mOutputHatches","mInputBusses","mOutputBusses","mPressureSteamInputs","mOverpressureInputs","mSteamCoolingHatches","mPressureCoolingHatches","mSteamInputFluids"})
            try { Object list=field(machine,f); if(list instanceof Collection) lists.addProperty(f,((Collection<?>)list).size()); } catch(NoSuchFieldException ignored) {}
        result.add("registrationLists",lists);
        result.addProperty("bugReproduced", bug); result.addProperty("pass", Boolean.getBoolean("nei.audit.expectRed") ? bug : expected > 0 && positions.size()==expected && wrong==0 && unboundedBranches==0 && hatches<=quotaUpper && shells==expected-hatches);
        result.addProperty("handlerClass", handler.getClass().getName()); result.addProperty("worldClass", world.getClass().getName());
        cases.add(result); System.out.println("ACTUAL_NEI " + name + " positions=" + positions.size() + "/"+expected+" casings=" + shells + " hatches=" + hatches + "/"+quotaUpper+" wrong="+wrong+" unbounded="+unboundedBranches+" kinds=" + kinds);
    }
    void finish(Throwable failure) {
        if (done) return; done = true; receipt.add("cases", cases); boolean pass = failure == null && cases.size() >= 2;
        for (JsonElement row : cases) pass &= row.getAsJsonObject().get("pass").getAsBoolean();
        receipt.addProperty("status", pass ? (Boolean.getBoolean("nei.audit.expectRed") ? "EXPECTED_RED" : "PASS") : "FAIL");
        if (failure != null) { JsonArray trace = new JsonArray(); for (Throwable t = failure; t != null; t = t.getCause()) { trace.add(new JsonPrimitive(t.toString())); for (StackTraceElement s : t.getStackTrace()) trace.add(new JsonPrimitive(s.toString())); } receipt.add("failure", trace); }
        try { Files.write(Paths.get(System.getProperty("nei.audit.receipt")), new GsonBuilder().setPrettyPrinting().create().toJson(receipt).getBytes(StandardCharsets.UTF_8)); call(mc,"shutdown|func_71400_g"); } catch (Exception e) { e.printStackTrace(); }
    }
}
