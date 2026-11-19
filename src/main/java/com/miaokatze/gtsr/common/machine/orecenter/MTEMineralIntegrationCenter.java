package com.miaokatze.gtsr.common.machine.orecenter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.alignment.enumerable.Flip;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.miaokatze.gtsr.common.items.CenterModuleItems;
import com.miaokatze.gtsr.common.machine.base.MTEGTSRExtendedPowerMultiBlockBase;
import com.miaokatze.gtsr.common.machine.base.MTEHatchPressureSteamInput;
import com.miaokatze.gtsr.common.machine.base.MTEOverpressureTurbineInputHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamCoolingHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamInputHatchGeneric;
import com.miaokatze.gtsr.common.machine.cluster.ClusterParams.ClusterTier;
import com.miaokatze.gtsr.common.machine.cluster.ClusterParams.SteamGrade;
import com.miaokatze.gtsr.common.machine.cluster.MTEClusterUnitBase;
import com.miaokatze.gtsr.common.structure.StructurePreviewWorld;

import gregtech.api.enums.Textures;
import gregtech.api.render.TextureFactory;
import gregtech.api.interfaces.INEIPreviewModifier;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.MultiblockTooltipBuilder;

/**
 * The controller owns inventory, payment, progress and all rolled output. Module buildings display its configuration.
 */
public final class MTEMineralIntegrationCenter extends MTEGTSRExtendedPowerMultiBlockBase<MTEMineralIntegrationCenter>
    implements ISurvivalConstructable, INEIPreviewModifier {

    private static final IStructureDefinition<MTEMineralIntegrationCenter> PREVIEW = StructureDefinition
        .<MTEMineralIntegrationCenter>builder()
        .addChannel(CenterStructure.SECTOR_CHANNEL, 1, 6)
        .build();
    private static final CenterModule[] MODULES = CenterModule.values();
    private static final SteamGrade[] STEAMS = SteamGrade.values();
    private static int budgetTick = Integer.MIN_VALUE;
    private static long workInTick;
    private final CenterStructure structure = new CenterStructure();
    private final int[] modules = new int[36], grades = new int[36];
    private final List<CenterGraph> graphs = new ArrayList<>();
    private long graphRevision, nextGraphId = 1;
    private boolean configuredGraphs;
    private final List<ItemStack> pendingItems = new ArrayList<>();
    private final List<FluidStack> pendingFluids = new ArrayList<>();
    private final List<MTEHatch> steamHatches = new ArrayList<>();
    private final List<MTESteamCoolingHatch> coolingHatches = new ArrayList<>();
    private final CenterLedger ledger = new CenterLedger();
    private CenterPlanner.Session planning;
    private CenterStructure.DebugBuild debugBuild;
    private boolean debugIgnoreItems, debugIgnoreFluids, debugIgnoreEnergy;
    private int selectedSlot, totalTicks, elapsed, steamKind, plannedTier = -1, plannedMask;
    private int textureTier = -1;
    private long steamTotal, energyTotal, sequence, nextProbe, coolingSteam;
    private final int[] boostFluids = new int[4];

    public MTEMineralIntegrationCenter(int id, String name, String regionalName) {
        super(id, name, regionalName);
    }

    private MTEMineralIntegrationCenter(String name) {
        super(name);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity te) {
        return new MTEMineralIntegrationCenter(mName);
    }

    @Override
    protected gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui<?> getGui() {
        return new com.miaokatze.gtsr.common.gui.CenterMachineGui(this);
    }

    @Override
    public IStructureDefinition<MTEMineralIntegrationCenter> getStructureDefinition() {
        return PREVIEW;
    }

    @Override
    protected IAlignmentLimits getInitialAlignmentLimits() {
        return (direction, rotation, flip) -> direction.offsetY == 0 && rotation.isNotRotated() && flip == Flip.NONE;
    }

    @Override
    public boolean onWrenchRightClick(ForgeDirection side, ForgeDirection wrenchingSide, EntityPlayer player, float x,
        float y, float z, ItemStack tool) {
        for (int grade : grades) if (grade > 0) return false;
        return totalTicks == 0 && super.onWrenchRightClick(side, wrenchingSide, player, x, y, z, tool);
    }

    @Override
    public void construct(ItemStack stack, boolean hintsOnly) {
        structure.construct(getBaseMetaTileEntity(), selectedTier(stack) - 1, selectedSectors(stack), hintsOnly);
    }

    @Override
    public int survivalConstruct(ItemStack stack, int budget, ISurvivalBuildEnvironment env) {
        // The tracked renderer has a completion callback. Populate its world once there.
        if (getBaseMetaTileEntity().getWorld() instanceof StructurePreviewWorld) return 0;
        return structure
            .survivalConstruct(getBaseMetaTileEntity(), selectedTier(stack) - 1, selectedSectors(stack), budget, env);
    }

    static int selectedTier(ItemStack stack) {
        return Math.max(1, Math.min(4, stack.stackSize));
    }

    static int selectedSectors(ItemStack stack) {
        int value = ChannelDataAccessor.hasSubChannel(stack, CenterStructure.SECTOR_CHANNEL)
            ? ChannelDataAccessor.getChannelData(stack, CenterStructure.SECTOR_CHANNEL) : 1;
        return (1 << Math.max(1, Math.min(6, value))) - 1;
    }

    @Override
    public void onPreviewStructureComplete(ItemStack stack) {
        if (getBaseMetaTileEntity().getWorld() instanceof StructurePreviewWorld preview) preview.gtsr$markStructurePreview();
        textureTier = selectedTier(stack) - 1;
        construct(stack, false);
        structure.showPreviewPorts(getBaseMetaTileEntity());
    }

    @Override
    public String[] getStructureDescription(ItemStack stack) {
        return new String[] { StatCollector.translateToLocal("gtsr.ore_center.structure.tier"),
            StatCollector.translateToLocal("gtsr.ore_center.structure.sectors"),
            StatCollector.translateToLocal("gtsr.ore_center.structure.required_ports"),
            StatCollector.translateToLocal("gtsr.ore_center.structure.optional_ports") };
    }

    @Override
    public void checkMachine(IGregTechTileEntity te, ItemStack stack, List<StructureError> errors) {
        bindHatches(te);
        if (structure.sectorMask(te) == 0 || !hasRequiredPorts())
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
    }

    private void bindHatches(IGregTechTileEntity te) {
        clearHatches();
        steamHatches.clear();
        coolingHatches.clear();
        structure.collectHatches(te, hatch -> {
            IMetaTileEntity meta = hatch.getMetaTileEntity();
            if (!acceptsPort(meta)) return;
            if (meta.getClass() == MTESteamCoolingHatch.class) coolingHatches.add((MTESteamCoolingHatch) meta);
            else if (meta.getClass() == MTESteamInputHatchGeneric.class
                || meta.getClass() == MTEHatchPressureSteamInput.class
                || meta.getClass() == MTEOverpressureTurbineInputHatch.class) steamHatches.add((MTEHatch) meta);
            else addToMachineList(hatch, 10);
        });
    }

    static boolean acceptsPort(IMetaTileEntity meta) {
        if (!(meta instanceof MTEHatch)) return false;
        Class<?> type = meta.getClass();
        return type == MTEHatchInput.class || type == MTEHatchInputBus.class || type == MTEHatchOutput.class
            || type == MTEHatchOutputBus.class || type == MTEHatchEnergy.class || type == MTESteamCoolingHatch.class
            || type == MTESteamInputHatchGeneric.class || type == MTEHatchPressureSteamInput.class
            || type == MTEOverpressureTurbineInputHatch.class;
    }

    private boolean hasRequiredPorts() {
        return !mInputBusses.isEmpty() && !mOutputBusses.isEmpty()
            && !mInputHatches.isEmpty() && !mOutputHatches.isEmpty();
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity te, ForgeDirection side, ForgeDirection facing, int colorIndex,
        boolean active, boolean redstone) {
        ITexture casing = Textures.BlockIcons.getCasingTextureForId(MTEClusterUnitBase.tierCasingTextureId(textureTier));
        if (side != facing) return new ITexture[] { casing };
        return new ITexture[] { casing, TextureFactory.of(
            active ? Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL_ACTIVE : Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL) };
    }

    @Override
    public byte getUpdateData() {
        return (byte) (textureTier + 1);
    }

    @Override
    public void onValueUpdate(byte value) {
        textureTier = value >= 1 && value <= 4 ? value - 1 : -1;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        return new MultiblockTooltipBuilder().addMachineType(StatCollector.translateToLocal("gtsr.ore_center.tooltip.type"))
            .addInfo("The controller plans and runs whole processing batches.")
            .addInfo(StatCollector.translateToLocal("gtsr.ore_center.tooltip.terminal"))
            .addInfo("Sneak-screwdriver selects a module pad. Use screwdriver to install or return a package.")
            .addInfo(StatCollector.translateToLocal("gtsr.ore_center.structure.tier"))
            .addStructureInfo(StatCollector.translateToLocal("gtsr.ore_center.structure.tier"))
            .addStructureInfo(StatCollector.translateToLocal("gtsr.ore_center.structure.sectors"))
            .addStructureInfoSeparator()
            .addStructureInfo(StatCollector.translateToLocal("gtsr.ore_center.structure.required_ports"))
            .addStructureInfo(StatCollector.translateToLocal("gtsr.ore_center.structure.optional_ports"))
            .toolTipFinisher("GTSR");
    }

    @Override
    public int getMaxEfficiency(ItemStack stack) {
        return 10000;
    }

    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        return false;
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        return CheckRecipeResultRegistry.NO_RECIPE;
    }

    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer player, float x, float y, float z,
        ItemStack tool) {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te.isClientSide() || side != te.getFrontFacing()) return;
        if (player.isSneaking()) {
            selectedSlot = (selectedSlot + 1) % modules.length;
            te.markDirty();
            player.addChatMessage(new ChatComponentText("Module pad " + (selectedSlot + 1) + "/36"));
        } else player.addChatMessage(new ChatComponentText(changeModule(te, selectedSlot)));
    }

    public boolean installModuleFromBus(int slot) {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te == null || te.isClientSide() || slot < 0 || slot >= grades.length || grades[slot] != 0) return false;
        return changeModule(te, slot).startsWith("Installed ");
    }

    public int batchProgress() {
        return elapsed;
    }

    public long batchSequence() {
        return sequence;
    }

    private String changeModule(IGregTechTileEntity te, int slot) {
        return changeModule(te, slot, null, 0);
    }

    public String applyModuleEdit(EntityPlayer player, int slot, int choice, int grade) {
        if (!canEditGraphs(player) || slot < 0 || slot >= grades.length || choice < -1 || choice >= MODULES.length)
            return "module.invalid";
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (choice == -1) {
            if (grades[slot] != grade || grade < 1) return "module.invalid";
            return changeModule(te, slot).startsWith("Module package") ? "module.removed" : "module.blocked";
        }
        CenterModule module = MODULES[choice];
        int kind = slot % 6 == 0 ? 0 : slot % 6 <= 2 ? 1 : 2;
        if (module.kind != kind || !module.acceptsGrade(grade, structure.tier(te))) return "module.invalid";
        if (grades[slot] != 0) return "module.occupied";
        String result = changeModule(te, slot, module, grade);
        return result.startsWith("Installed ") ? "module.installed"
            : result.startsWith("No compatible") ? "module.no_pack" : "module.blocked";
    }

    private String changeModule(IGregTechTileEntity te, int slot, CenterModule choice, int choiceGrade) {
        if (debugBuild != null || totalTicks > 0
            || (structure.sectorMask(te) & 1 << (slot / 6)) == 0) {
            return "The center must be formed and between batches.";
        }
        if (grades[slot] > 0) {
            CenterModule module = MODULES[modules[slot]];
            if (!structure.remove(te, slot, module, grades[slot])) return "Module removal could not be verified.";
            pendingItems.add(CenterModuleItems.stack(module, grades[slot]));
            grades[slot] = 0;
            planning = null;
            te.markDirty();
            return "Module package queued at the shared output.";
        }
        int type = slot % 6;
        int kind = type == 0 ? 0 : type <= 2 ? 1 : 2;
        int tier = structure.tier(te);
        for (Slot input : itemSlots()) {
            ItemStack pack = input.get();
            CenterModule module = CenterModuleItems.moduleOf(pack);
            int grade = CenterModuleItems.gradeOf(pack);
            if (choice != null && (module != choice || grade != choiceGrade)) continue;
            if (module == null || module.kind != kind || !module.acceptsGrade(grade, tier)) continue;
            if (!structure.install(te, slot, module, grade)) return "The module pad could not be built.";
            input.take(1);
            modules[slot] = module.ordinal();
            grades[slot] = grade;
            planning = null;
            te.markDirty();
            return "Installed " + module.key + " " + grade;
        }
        return "No compatible package in the input bus.";
    }

    public NBTTagCompound operationStatus() {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        NBTTagCompound tag = new NBTTagCompound();
        int sectors = structure.sectorMask(te);
        tag.setInteger("Tier", structure.tier(te));
        tag.setInteger("Sectors", sectors);
        tag.setBoolean("Formed", sectors != 0 && hasRequiredPorts());
        tag.setBoolean("Active", te.isActive());
        int installed = 0;
        for (int i = 0; i < grades.length; i++) if (grades[i] > 0 && (sectors & 1 << i / 6) != 0) installed++;
        tag.setInteger("Installed", installed);
        tag.setInteger("Graphs", graphs.size());
        tag.setInteger("Elapsed", elapsed);
        tag.setInteger("Duration", totalTicks);
        tag.setLong("Energy", energyTotal);
        tag.setLong("Steam", steamTotal);
        SteamGrade steam = STEAMS[steamKind];
        tag.setInteger("SteamDensity", steam.getDivisor());
        Fluid steamFluid = steam.resolveFluid();
        if (steamFluid != null) tag.setString("SteamFluid", steamFluid.getName());
        tag.setLong("Power", getMaxInputEu());
        tag.setLong("Voltage", getMaxInputVoltage());
        NBTTagList boosts = new NBTTagList();
        for (int i = 0; i < boostFluids.length; i++) {
            NBTTagCompound row = new NBTTagCompound();
            Fluid material = CenterPlanner.boostFluid(i);
            if (material != null) row.setString("FluidName", material.getName());
            row.setInteger("Amount", boostFluids[i]);
            boosts.appendTag(row);
        }
        tag.setTag("BoostCosts", boosts);
        return tag;
    }

    public NBTTagCompound terminalState() {
        NBTTagCompound tag = operationStatus();
        tag.setIntArray("Modules", modules.clone());
        tag.setIntArray("Grades", grades.clone());
        int[] packs = new int[MODULES.length * 3];
        for (Slot slot : itemSlots()) {
            ItemStack item = slot.get();
            CenterModule module = CenterModuleItems.moduleOf(item);
            if (module != null) {
                int type = module.ordinal() * 3 + CenterModuleItems.gradeOf(item) - 1;
                packs[type] += Math.min(Math.max(0, item.stackSize), Integer.MAX_VALUE - packs[type]);
            }
        }
        tag.setIntArray("Packs", packs);
        NBTTagList fluids = new NBTTagList();
        for (FluidSlot slot : fluidSlots()) {
            FluidStack fluid = slot.get();
            if (fluid != null && fluid.amount > 0 && fluids.tagCount() < 32) {
                NBTTagCompound available = new NBTTagCompound();
                available.setString("FluidName", fluid.getFluid().getName());
                available.setInteger("Amount", fluid.amount);
                fluids.appendTag(available);
            }
        }
        tag.setTag("Fluids", fluids);
        return tag;
    }

    public NBTTagList terminalLedger() {
        return ledger.save();
    }

    public boolean resetTerminalLedger(EntityPlayer player) {
        if (!canEditGraphs(player)) return false;
        ledger.clear();
        getBaseMetaTileEntity().markDirty();
        return true;
    }

    /** Used by the server-side graph editor. Existing batches keep their saved result. */
    public boolean setGraphs(List<CenterGraph> config) {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te == null || te.isClientSide()
            || config.size() > CenterGraph.MAX_GRAPHS
            || config.stream()
                .filter(g -> g.enabled && g.fallback())
                .count() > 1)
            return false;
        List<CenterGraph> next = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        long sequence = nextGraphId;
        for (CenterGraph graph : config) {
            if (graph.id >= Long.MAX_VALUE - CenterGraph.MAX_GRAPHS) return false;
            if (graph.id > 0 && !ids.add(graph.id)) return false;
            if (graph.id >= sequence) sequence = graph.id + 1;
        }
        for (CenterGraph graph : config) {
            if (graph.id == 0) graph = graph.withId(sequence++);
            next.add(graph);
        }
        graphs.clear();
        graphs.addAll(next);
        configuredGraphs = true;
        nextGraphId = sequence;
        graphRevision++;
        planning = null;
        te.markDirty();
        return true;
    }

    public long graphRevision() {
        return graphRevision;
    }

    public int graphCapacity() {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te == null) return 0;
        int mask = structure.sectorMask(te);
        int count = 0;
        for (int i = 0; i < grades.length; i++) if ((mask & 1 << (i / 6)) != 0 && grades[i] > 0
            && MODULES[modules[i]] == CenterModule.LOGISTICS
            && structure.moduleIntact(te, i, CenterModule.LOGISTICS, grades[i])) count++;
        return count;
    }

    public long lastGraphId() {
        return graphs.isEmpty() ? -1 : graphs.get(graphs.size() - 1).id;
    }

    public List<CenterGraph> graphSnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(graphs));
    }

    public boolean canEditGraphs(EntityPlayer player) {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        return te != null && !te.isClientSide()
            && player != null
            && player.worldObj == te.getWorld()
            && te.getMetaTileEntity() == this
            && te.isUseableByPlayer(player);
    }

    public String applyWandDebug(EntityPlayer player, boolean items, boolean fluids, boolean energy, boolean quick) {
        if (!canEditGraphs(player)
            || !player.capabilities.isCreativeMode && !player.canCommandSenderUseCommand(2, "gtsrdebug"))
            return "gtsr.wand.debug.denied";
        if (quick && (debugBuild != null || elapsed < totalTicks)) return "gtsr.wand.debug.build_busy";
        IGregTechTileEntity te = getBaseMetaTileEntity();
        debugIgnoreItems = items;
        debugIgnoreFluids = fluids;
        debugIgnoreEnergy = energy;
        planning = null;
        if (quick) debugBuild = structure.debugBuild(te, player, modules, grades);
        te.markDirty();
        return quick ? "gtsr.wand.debug.build_started" : "gtsr.wand.debug.applied";
    }

    public boolean applyGraphEdit(EntityPlayer player, long expectedRevision, List<CenterGraph> edit) {
        if (!canEditGraphs(player) || expectedRevision != graphRevision) return false;
        Set<Long> known = new HashSet<>();
        for (CenterGraph graph : graphs) known.add(graph.id);
        if (edit.stream()
            .anyMatch(graph -> graph.id != 0 && !known.contains(graph.id))) return false;
        if (edit.size() > Math.max(graphCapacity(), graphs.size())) return false;
        return setGraphs(edit);
    }

    @Override
    public void onPostTick(IGregTechTileEntity te, long tick) {
        long measured = CenterTickTiming.begin();
        try {
            super.onPostTick(te, tick);
        } finally {
            CenterTickTiming.end(te, CenterTickTiming.Stage.BASE, measured);
        }
        if (te.isClientSide()) return;
        if (debugBuild != null) {
            te.setActive(false);
            runDebugBuild(te);
            return;
        }
        measured = CenterTickTiming.begin();
        try {
            structure.tick(te);
        } finally {
            CenterTickTiming.end(te, CenterTickTiming.Stage.STRUCTURE, measured);
        }
        int mask = structure.sectorMask(te);
        int tier = structure.tier(te);
        if (textureTier != tier) {
            textureTier = tier;
            te.issueTileUpdate();
        }
        if (mask == 0 || tier < 0) {
            planning = null;
            te.setActive(false);
            return;
        }
        if ((tick % 20) == 0 || tier != plannedTier || mask != plannedMask) {
            if (tier != plannedTier || mask != plannedMask) planning = null;
            plannedTier = tier;
            plannedMask = mask;
            bindHatches(te);
        }
        if ((tick % 20) == 0) drainCooling(te);
        if (!hasRequiredPorts()) {
            planning = null;
            te.setActive(false);
            return;
        }
        for (int i = 0; i < grades.length; i++) {
            if (grades[i] == 0) continue;
            CenterModule module = MODULES[modules[i]];
            if (structure.moduleLoaded(te, i, module, grades[i]) && !structure.moduleIntact(te, i, module, grades[i])) {
                grades[i] = 0;
                planning = null;
                te.markDirty();
            }
        }
        if (elapsed < totalTicks) {
            payTick(te);
            return;
        }
        if ((tick % 10) == 0) drainOutput(te);
        if (!pendingItems.isEmpty() || !pendingFluids.isEmpty() || !te.isAllowedToWork()) return;
        totalTicks = elapsed = 0;
        if (tick < nextProbe) return;
        // Leave room for the final non-interruptible native lookup or inventory operation.
        if (!hasTickBudget()) return;
        long start = System.nanoTime();
        measured = CenterTickTiming.begin();
        try {
            planTick(te, tick, tier, mask);
        } finally {
            workInTick += System.nanoTime() - start;
            CenterTickTiming.end(te, CenterTickTiming.Stage.PLANNING, measured);
        }
    }

    private static boolean hasTickBudget() {
        int serverTick = MinecraftServer.getServer().getTickCounter();
        if (serverTick != budgetTick) {
            budgetTick = serverTick;
            workInTick = 0;
        }
        return workInTick < 1_700_000;
    }

    private void runDebugBuild(IGregTechTileEntity te) {
        if (!hasTickBudget()) return;
        long start = System.nanoTime();
        String result;
        try {
            result = debugBuild.advance(te, start + 1_700_000 - workInTick);
        } finally {
            workInTick += System.nanoTime() - start;
        }
        if (result != null) {
            debugBuild.notifyPlayer(result);
            debugBuild = null;
        }
    }

    private void planTick(IGregTechTileEntity te, long tick, int tier, int mask) {
        if (planning == null) {
            int[][] counts = new int[MODULES.length][3];
            int[] best = new int[MODULES.length];
            int logistics = 0;
            for (int i = 0; i < grades.length; i++) if (grades[i] > 0 && (mask & 1 << (i / 6)) != 0) {
                CenterModule module = MODULES[modules[i]];
                if (!module.acceptsGrade(grades[i], tier) || !structure.moduleIntact(te, i, module, grades[i]))
                    continue;
                best[module.ordinal()] = Math.max(best[module.ordinal()], grades[i]);
                counts[module.ordinal()][grades[i] - 1]++;
                if (module == CenterModule.LOGISTICS) logistics++;
            }
            if (logistics == 0) {
                nextProbe = tick + 20;
                return;
            }
            List<CenterGraph> live = configuredGraphs ? assignedGraphs(logistics) : defaultGraphs(best);
            List<Slot> slots = itemSlots();
            List<FluidSlot> tanks = fluidSlots();
            planning = new CenterPlanner.Session(
                live,
                slots.stream()
                    .map(Slot::get)
                    .filter(s -> s != null)
                    .toArray(ItemStack[]::new),
                tanks.stream()
                    .map(FluidSlot::get)
                    .filter(s -> s != null)
                    .toArray(FluidStack[]::new),
                counts,
                tier,
                Integer.bitCount(mask),
                getMaxInputVoltage(),
                getMaxInputEu(),
                CenterPlanner.HEAT[tier],
                getBaseMetaTileEntity().getWorld()
                    .getSeed() ^ sequence
                    ^ (long) te.getXCoord() * 341873128712L
                    ^ (long) te.getZCoord() * 132897987541L,
                debugIgnoreFluids,
                debugIgnoreEnergy);
        }
        if (!planning.advance(32, 250000)) return;
        CenterPlanner.Batch batch = planning.batch();
        planning = null;
        nextProbe = tick + 20;
        if (batch != null) commit(te, batch, tier);
    }

    private List<CenterGraph> assignedGraphs(int logistics) {
        if (graphs.size() <= logistics) return graphs;
        List<CenterGraph> allocation = new ArrayList<>(graphs);
        allocation.sort(Comparator.comparingLong(g -> g.id));
        long last = allocation.get(logistics - 1).id;
        allocation.clear();
        for (CenterGraph graph : graphs) if (graph.id <= last) allocation.add(graph);
        return allocation;
    }

    private static List<CenterGraph> defaultGraphs(int[] best) {
        List<CenterGraph.Node> nodes = new ArrayList<>();
        if (best[CenterModule.CRUSHER.ordinal()] == 0) return new ArrayList<>();
        boolean wash = best[CenterModule.WASHER.ordinal()] > 0;
        boolean spin = best[CenterModule.CENTRIFUGE.ordinal()] > 0;
        nodes.add(new CenterGraph.Node(CenterProcess.CRUSHER, new int[] { wash || spin ? 1 : -1 }));
        if (wash) nodes.add(new CenterGraph.Node(CenterProcess.WASHER, new int[] { spin ? 2 : -1 }));
        if (spin) {
            nodes.add(new CenterGraph.Node(CenterProcess.CRUSHER, new int[] { nodes.size() + 1 }));
            nodes.add(new CenterGraph.Node(CenterProcess.CENTRIFUGE, new int[0]));
        }
        List<CenterGraph> result = new ArrayList<>();
        result.add(new CenterGraph(0, true, new ArrayList<>(), nodes));
        return result;
    }

    private void commit(IGregTechTileEntity te, CenterPlanner.Batch batch, int tier) {
        List<Slot> slots = itemSlots();
        List<FluidSlot> tanks = fluidSlots();
        List<Runnable> debits = new ArrayList<>();
        if (!debugIgnoreItems) for (ItemStack need : batch.consumed) {
            int left = need.stackSize;
            for (Slot slot : slots) {
                ItemStack stack = slot.get();
                if (stack == null || !CenterPlanner.equal(stack, need)) continue;
                int n = Math.min(left, stack.stackSize);
                if (n > 0) debits.add(() -> slot.take(n));
                left -= n;
                if (left == 0) break;
            }
            if (left > 0) return;
        }
        Map<FluidSlot, Integer> ingredients = new IdentityHashMap<>();
        int accepted = 0;
        if (!debugIgnoreFluids) {
            for (FluidStack fluid : batch.consumedFluids)
                if (!reserve(tanks, fluid, ingredients)) return;
            List<FluidSlot> dedicated = steamSlots();
            List<FluidSlot> supply = new ArrayList<>(dedicated);
            supply.addAll(tanks);
            accepted = chooseSteam(batch, tier, dedicated, ingredients);
            if (accepted < 0) accepted = chooseSteam(batch, tier, supply, ingredients);
            if (accepted < 0) return;
        }
        if (!debugIgnoreEnergy && batch.energy > getMaxInputEu() * batch.ticks) return;
        debits.forEach(Runnable::run);
        ingredients.forEach(FluidSlot::take);
        elapsed = 0;
        totalTicks = batch.ticks;
        steamTotal = debugIgnoreFluids ? 0 : batch.steam;
        energyTotal = debugIgnoreEnergy ? 0 : batch.energy;
        steamKind = accepted;
        System.arraycopy(batch.boostFluids, 0, boostFluids, 0, boostFluids.length);
        sequence++;
        pendingItems.addAll(batch.outputs);
        pendingFluids.addAll(batch.outputFluids);
        if (!debugIgnoreItems) for (ItemStack item : batch.consumed) ledger.add(item, 0, item.stackSize);
        for (ItemStack item : batch.bonuses) ledger.add(item, 2, item.stackSize);
        te.markDirty();
    }

    private static int chooseSteam(CenterPlanner.Batch batch, int tier, List<FluidSlot> sources,
        Map<FluidSlot, Integer> ingredients) {
        for (int i = STEAMS.length - 1; i >= 0; i--) {
            SteamGrade grade = STEAMS[i];
            Fluid fluid = grade.resolveFluid();
            if (fluid == null || !grade.isAcceptedBy(ClusterTier.get(tier))) continue;
            int first = (int) ((batch.steam + batch.ticks * (long) grade.getDivisor() - 1)
                / (batch.ticks * (long) grade.getDivisor()));
            int remaining = first;
            for (FluidSlot slot : sources) {
                FluidStack available = slot.get();
                if (available != null && available.getFluid() == fluid)
                    remaining -= Math.min(remaining, available.amount - ingredients.getOrDefault(slot, 0));
                if (remaining <= 0) return i;
            }
        }
        return -1;
    }

    private void payTick(IGregTechTileEntity te) {
        te.setActive(false);
        int density = STEAMS[steamKind].getDivisor();
        Fluid fluid = STEAMS[steamKind].resolveFluid();
        long units = (steamTotal + density - 1) / density;
        int steam = (int) (units * (elapsed + 1) / totalTicks - units * elapsed / totalTicks);
        long eu = energyTotal * (elapsed + 1) / totalTicks - energyTotal * elapsed / totalTicks;
        Map<FluidSlot, Integer> money = new java.util.LinkedHashMap<>();
        if (steam > 0) {
            if (fluid == null) return;
            List<FluidSlot> supply = steamSlots();
            supply.addAll(fluidSlots());
            if (!reserve(supply, new FluidStack(fluid, steam), money)) return;
        }
        long stored = 0;
        for (MTEHatch hatch : mEnergyHatches) if (hatch.isValid()) {
            stored += Math.min(
                eu,
                hatch.getBaseMetaTileEntity()
                    .getStoredEU());
            if (stored >= eu) break;
        }
        if (stored < eu || eu > 0 && !drainEnergyInput(eu)) return;
        money.forEach(FluidSlot::take);
        if (!coolingHatches.isEmpty()) coolingSteam += (long) steam * density;
        elapsed++;
        te.setActive(elapsed < totalTicks);
        te.markDirty();
    }

    private void drainCooling(IGregTechTileEntity te) {
        for (MTESteamCoolingHatch hatch : coolingHatches) {
            if (coolingSteam < 160) return;
            if (!hatch.isValid()) continue;
            int moved = hatch.pushCoolingWater((int) Math.min(Integer.MAX_VALUE, coolingSteam / 160));
            if (moved > 0) {
                coolingSteam -= moved * 160L;
                te.markDirty();
            }
        }
    }

    private void drainOutput(IGregTechTileEntity te) {
        if (elapsed < totalTicks) return;
        if (!pendingItems.isEmpty()) {
            ItemStack remaining = pendingItems.get(0);
            ItemStack packet = remaining.copy();
            packet.stackSize = Math.min(remaining.getMaxStackSize(), remaining.stackSize);
            int before = packet.stackSize;
            addOutputPartial(packet);
            remaining.stackSize -= before - packet.stackSize;
            if (CenterModuleItems.moduleOf(remaining) == null) ledger.add(remaining, 1, before - packet.stackSize);
            if (remaining.stackSize <= 0) pendingItems.remove(0);
            if (before != packet.stackSize) te.markDirty();
        }
        if (!pendingFluids.isEmpty()) {
            FluidStack remaining = pendingFluids.get(0);
            int before = remaining.amount;
            addOutputPartial(remaining, getOutputHatches(new FluidStack[] { remaining }), true);
            if (remaining.amount <= 0) pendingFluids.remove(0);
            if (before != remaining.amount) te.markDirty();
        }
    }

    private static class Slot {

        final IGregTechTileEntity te;
        final int index;

        Slot(IGregTechTileEntity te, int index) {
            this.te = te;
            this.index = index;
        }

        ItemStack get() {
            return te.getStackInSlot(index);
        }

        void take(int count) {
            ItemStack item = get();
            item.stackSize -= count;
            if (item.stackSize == 0) te.setInventorySlotContents(index, null);
            te.markDirty();
        }
    }

    private static class FluidSlot {

        final MTEHatch tank;

        FluidSlot(MTEHatch tank) {
            this.tank = tank;
        }

        FluidStack get() {
            return tank.getFluid();
        }

        void take(int count) {
            FluidStack contents = get();
            contents.amount -= count;
            if (contents.amount == 0 && tank instanceof MTEHatchInput input) input.setFillableStack(null);
            tank.getBaseMetaTileEntity()
                .markDirty();
        }
    }

    private List<Slot> itemSlots() {
        List<Slot> next = new ArrayList<>();
        for (MTEHatchInputBus bus : mInputBusses) if (bus.isValid()) {
            IGregTechTileEntity te = bus.getBaseMetaTileEntity();
            for (int i = 0; i < te.getSizeInventory(); i++) if (bus.isValidSlot(i)) next.add(new Slot(te, i));
        }
        return next;
    }

    private List<FluidSlot> fluidSlots() {
        List<FluidSlot> next = new ArrayList<>();
        for (MTEHatchInput hatch : mInputHatches) if (hatch.isValid()) next.add(new FluidSlot(hatch));
        return next;
    }

    private List<FluidSlot> steamSlots() {
        List<FluidSlot> next = new ArrayList<>();
        for (MTEHatch hatch : steamHatches) if (hatch.isValid()) next.add(new FluidSlot(hatch));
        return next;
    }

    private static boolean reserve(List<FluidSlot> tanks, FluidStack need, Map<FluidSlot, Integer> debits) {
        int left = need.amount;
        if (left <= 0) return true;
        for (FluidSlot slot : tanks) {
            FluidStack fluid = slot.get();
            if (fluid == null || !fluid.isFluidEqual(need)) continue;
            int prior = debits.getOrDefault(slot, 0);
            int n = Math.min(left, fluid.amount - prior);
            if (n > 0) debits.put(slot, prior + n);
            left -= n;
            if (left == 0) break;
        }
        return left == 0;
    }

    @Override
    public String[] getInfoData() {
        return new String[] { "Mineral Processing Integration Center", "Pad " + (selectedSlot + 1) + "/36",
            "Batch " + sequence + " · " + elapsed + "/" + totalTicks + " t",
            "Buffered outputs " + pendingItems.size() + "/" + pendingFluids.size() };
    }

    @Override
    public void saveNBTData(NBTTagCompound tag) {
        super.saveNBTData(tag);
        tag.setIntArray("CenterModules", modules);
        tag.setIntArray("CenterGrades", grades);
        tag.setBoolean("CenterDebugItems", debugIgnoreItems);
        tag.setBoolean("CenterDebugFluids", debugIgnoreFluids);
        tag.setBoolean("CenterDebugEnergy", debugIgnoreEnergy);
        tag.setInteger("CenterPad", selectedSlot);
        tag.setInteger("CenterDuration", totalTicks);
        tag.setInteger("CenterElapsed", elapsed);
        tag.setInteger("CenterSteamKind", steamKind);
        tag.setLong("CenterSteam", steamTotal);
        tag.setLong("CenterCoolingSteam", coolingSteam);
        tag.setIntArray("CenterBoostFluids", boostFluids.clone());
        tag.setLong("CenterEnergy", energyTotal);
        tag.setLong("CenterSequence", sequence);
        tag.setLong("CenterGraphRevision", graphRevision);
        tag.setLong("CenterNextGraphId", nextGraphId);
        tag.setBoolean("CenterGraphConfigured", configuredGraphs);
        tag.setTag("CenterLedger", ledger.save());
        NBTTagList configs = new NBTTagList(), items = new NBTTagList(), fluids = new NBTTagList();
        for (CenterGraph graph : graphs) configs.appendTag(graph.save());
        for (ItemStack item : pendingItems) {
            NBTTagCompound saved = item.writeToNBT(new NBTTagCompound());
            saved.setInteger("CenterQuantity", item.stackSize);
            items.appendTag(saved);
        }
        for (FluidStack fluid : pendingFluids) fluids.appendTag(fluid.writeToNBT(new NBTTagCompound()));
        tag.setTag("CenterGraphs", configs);
        tag.setTag("CenterItemOutputs", items);
        tag.setTag("CenterFluidOutputs", fluids);
        structure.save(tag);
    }

    @Override
    public void loadNBTData(NBTTagCompound tag) {
        super.loadNBTData(tag);
        debugIgnoreItems = tag.getBoolean("CenterDebugItems");
        debugIgnoreFluids = tag.getBoolean("CenterDebugFluids");
        debugIgnoreEnergy = tag.getBoolean("CenterDebugEnergy");
        int[] types = tag.getIntArray("CenterModules"), levels = tag.getIntArray("CenterGrades");
        for (int i = 0; i < types.length && i < levels.length && i < grades.length; i++) {
            if (types[i] >= 0 && types[i] < MODULES.length && levels[i] > 0 && levels[i] <= 3) {
                modules[i] = types[i];
                grades[i] = levels[i];
            }
        }
        selectedSlot = Math.floorMod(tag.getInteger("CenterPad"), 36);
        totalTicks = Math.max(0, Math.min(24000, tag.getInteger("CenterDuration")));
        elapsed = Math.max(0, Math.min(totalTicks, tag.getInteger("CenterElapsed")));
        steamKind = Math.floorMod(tag.getInteger("CenterSteamKind"), STEAMS.length);
        steamTotal = Math.max(0, tag.getLong("CenterSteam"));
        coolingSteam = Math.max(0, tag.getLong("CenterCoolingSteam"));
        int[] fluidCosts = tag.getIntArray("CenterBoostFluids");
        for (int i = 0; i < boostFluids.length; i++)
            boostFluids[i] = i < fluidCosts.length ? Math.max(0, fluidCosts[i]) : 0;
        energyTotal = Math.max(0, tag.getLong("CenterEnergy"));
        sequence = tag.getLong("CenterSequence");
        graphRevision = Math.max(0, tag.getLong("CenterGraphRevision"));
        nextGraphId = Math.max(1, Math.min(Long.MAX_VALUE - CenterGraph.MAX_GRAPHS, tag.getLong("CenterNextGraphId")));
        graphs.clear();
        pendingItems.clear();
        pendingFluids.clear();
        NBTTagList configs = tag.getTagList("CenterGraphs", 10), items = tag.getTagList("CenterItemOutputs", 10),
            fluids = tag.getTagList("CenterFluidOutputs", 10);
        configuredGraphs = tag.getBoolean("CenterGraphConfigured") || configs.tagCount() > 0;
        ledger.load(tag.getTagList("CenterLedger", 10));
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < configs.tagCount() && i < CenterGraph.MAX_GRAPHS; i++) {
            try {
                CenterGraph graph = CenterGraph.load(configs.getCompoundTagAt(i));
                if (graph.id >= Long.MAX_VALUE - CenterGraph.MAX_GRAPHS) continue;
                if (graph.id != 0 && !ids.add(graph.id)) continue;
                nextGraphId = Math.max(nextGraphId, graph.id + 1);
                graphs.add(graph);
            } catch (IllegalArgumentException ignored) {
                // A malformed graph is not executable.
            }
        }
        for (int i = 0; i < graphs.size(); i++) if (graphs.get(i).id == 0) graphs.set(
            i,
            graphs.get(i)
                .withId(nextGraphId++));
        for (int i = 0; i < items.tagCount() && i < 512; i++) {
            NBTTagCompound saved = items.getCompoundTagAt(i);
            ItemStack item = ItemStack.loadItemStackFromNBT(saved);
            if (item != null && saved.getInteger("CenterQuantity") > 0) {
                item.stackSize = saved.getInteger("CenterQuantity");
                pendingItems.add(item);
            }
        }
        for (int i = 0; i < fluids.tagCount() && i < 512; i++) {
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(fluids.getCompoundTagAt(i));
            if (fluid != null && fluid.amount > 0) pendingFluids.add(fluid);
        }
        structure.load(tag);
    }
}
