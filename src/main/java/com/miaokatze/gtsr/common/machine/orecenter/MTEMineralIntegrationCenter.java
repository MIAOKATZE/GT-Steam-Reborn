package com.miaokatze.gtsr.common.machine.orecenter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
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
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.alignment.enumerable.Flip;
import com.gtnewhorizon.structurelib.alignment.enumerable.Rotation;
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
import com.miaokatze.gtsr.common.machinevisual.MachineVisualDefinition;
import com.miaokatze.gtsr.common.machinevisual.MachineVisualHost;
import com.miaokatze.gtsr.common.machinevisual.MachineVisualState;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.INEIPreviewModifier;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchInputBusCompressed;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBusCompressed;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.FluidEjectionHelper;
import gregtech.api.util.GTUtility.ItemId;
import gregtech.api.util.ItemEjectionHelper;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.shutdown.ShutDownReason;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;

/**
 * The controller owns inventory, payment, progress and all rolled output. Module buildings display its configuration.
 */
public final class MTEMineralIntegrationCenter extends MTEGTSRExtendedPowerMultiBlockBase<MTEMineralIntegrationCenter>
    implements ISurvivalConstructable, INEIPreviewModifier, MachineVisualHost {

    /** Source-level release switch for the controller, module items, recipes and development fixtures. */
    public static final boolean REGISTRATION_ENABLED = true;

    private static final IStructureDefinition<MTEMineralIntegrationCenter> PREVIEW = StructureDefinition
        .<MTEMineralIntegrationCenter>builder()
        .addChannel(CenterStructure.SECTOR_CHANNEL, 1, 6)
        .build();
    private static final CenterModule[] MODULES = CenterModule.values();
    private static final SteamGrade[] STEAMS = SteamGrade.values();
    private static int budgetTick = Integer.MIN_VALUE;
    private static long workInTick;
    private final CenterStructure structure = new CenterStructure();
    private final MachineVisualState visual = new MachineVisualState();

    private static final class Visual {

        static final MachineVisualDefinition CROWN = MachineVisualDefinition
            .decorative("/assets/gtsr/orecenter/crown-motion.json");
    }

    @Override
    public MachineVisualState visualState() {
        return visual;
    }

    @Override
    public MachineVisualDefinition visualDefinition() {
        return Visual.CROWN;
    }

    @Override
    public ExtendedFacing visualFacing() {
        // The sparse structure uses +X when facing north, opposite to StructureLib's default A axis.
        return ExtendedFacing.of(getExtendedFacing().getDirection(), Rotation.NORMAL, Flip.HORIZONTAL);
    }

    @Override
    public boolean cacheMotionMeshes() {
        return true;
    }

    @Override
    public int motionIdleGraceTicks() {
        return 8;
    }

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
    private int planningRootLimit = CenterPlanner.MAX_ROOTS;
    private boolean plannedBatchMode;
    private boolean queueMode;
    private int queueMinimum = 4096;
    private CenterStructure.DebugBuild debugBuild;
    private boolean debugIgnoreFluids, debugIgnoreEnergy;
    private int selectedSlot, totalTicks, elapsed, steamKind, plannedTier = -1, plannedMask;
    private int textureTier = -1;
    private long steamTotal, energyTotal, sequence, nextProbe, coolingSteam;
    private final int[] boostFluids = new int[4];
    private int batchModules, batchRounds;

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

    private World previewConstructionWorld;

    @Override
    public void onPreviewConstruct(ItemStack stack) {
        previewConstructionWorld = getBaseMetaTileEntity().getWorld();
    }

    @Override
    public int survivalConstruct(ItemStack stack, int budget, ISurvivalBuildEnvironment env) {
        // The native NEI completion callback places the selected structure and preview ports together.
        if (previewConstructionWorld != null && previewConstructionWorld == getBaseMetaTileEntity().getWorld())
            return 0;
        return structure
            .survivalConstruct(getBaseMetaTileEntity(), selectedTier(stack) - 1, selectedSectors(stack), budget, env);
    }

    static int selectedTier(ItemStack stack) {
        return Math.max(1, Math.min(4, stack.stackSize));
    }

    static int selectedSectors(ItemStack stack) {
        int value = ChannelDataAccessor.hasSubChannel(stack, CenterStructure.SECTOR_CHANNEL)
            ? ChannelDataAccessor.getChannelData(stack, CenterStructure.SECTOR_CHANNEL)
            : 1;
        return (1 << Math.max(1, Math.min(6, value))) - 1;
    }

    @Override
    public void onPreviewStructureComplete(ItemStack stack) {
        previewConstructionWorld = null;
        textureTier = selectedTier(stack) - 1;
        construct(stack, false);
        structure.showPreviewPorts(getBaseMetaTileEntity(), textureTier);
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
        if (structure.sectorMask(te) == 0) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        checkHasInputBus(errors);
        checkHasOutputBus(errors);
        checkHasInputHatch(errors);
        checkHasOutputHatch(errors);
    }

    @Override
    protected void onStructureCheckFinished(IGregTechTileEntity te) {
        super.onStructureCheckFinished(te);
        if (mMachine && mStartUpCheck > 0) mStartUpCheck = -1;
        if (mMachine && ShutDownReasonRegistry.STRUCTURE_INCOMPLETE.getKey()
            .equals(
                te.getLastShutDownReason()
                    .getKey())) {
            te.setShutDownReason(ShutDownReasonRegistry.NONE);
            te.setShutdownStatus(false);
        }
    }

    @Override
    protected boolean hasStructureErrors() {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        return super.hasStructureErrors() && (te == null || !te.isServerSide() || !structure.isCheckingFormation(te));
    }

    @Override
    public void stopMachine(ShutDownReason reason) {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (reason == ShutDownReasonRegistry.STRUCTURE_INCOMPLETE && te != null
            && (debugBuild != null || structure.isCheckingFormation(te))) return;
        super.stopMachine(reason);
    }

    private void bindHatches(IGregTechTileEntity te) {
        clearHatches();
        steamHatches.clear();
        coolingHatches.clear();
        structure.collectHatches(te, (hatch, casingTexture) -> {
            MTEHatch port = (MTEHatch) hatch.getMetaTileEntity();
            Class<?> type = port.getClass();
            if (type == MTESteamCoolingHatch.class) coolingHatches.add((MTESteamCoolingHatch) port);
            else if (type == MTESteamInputHatchGeneric.class || type == MTEHatchPressureSteamInput.class
                || type == MTEOverpressureTurbineInputHatch.class) steamHatches.add(port);
            else {
                addToMachineList(hatch, casingTexture);
                return;
            }
            port.updateTexture(casingTexture);
        });
    }

    static boolean acceptsPort(IMetaTileEntity meta) {
        if (!(meta instanceof MTEHatch)) return false;
        Class<?> type = meta.getClass();
        return type == MTEHatchInput.class || type == MTEHatchInputBus.class
            || type == MTEHatchInputBusCompressed.class
            || type == MTEHatchOutput.class
            || type == MTEHatchOutputBus.class
            || type == MTEHatchOutputBusCompressed.class
            || type == MTEHatchEnergy.class
            || type == MTESteamCoolingHatch.class
            || type == MTESteamInputHatchGeneric.class
            || type == MTEHatchPressureSteamInput.class
            || type == MTEOverpressureTurbineInputHatch.class;
    }

    private boolean hasRequiredPorts() {
        return !mInputBusses.isEmpty() && !mOutputBusses.isEmpty()
            && !mInputHatches.isEmpty()
            && !mOutputHatches.isEmpty();
    }

    @Override
    public boolean supportsBatchMode() {
        return true;
    }

    @Override
    public boolean getDefaultBatchMode() {
        return false;
    }

    @Override
    public void setBatchMode(boolean enabled) {
        if (enabled == isBatchModeEnabled()) return;
        super.setBatchMode(enabled);
        queueMinimum = Math.min(queueMinimum, maxPlanningRoots());
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te != null) te.markDirty();
    }

    private int maxPlanningRoots() {
        return isBatchModeEnabled() ? CenterPlanner.MAX_BATCH_ROOTS : CenterPlanner.MAX_ROOTS;
    }

    public String configureQueue(EntityPlayer player, boolean enabled, int minimum) {
        if (!canEditGraphs(player)) return "status.read_only";
        if (minimum < 1 || minimum > maxPlanningRoots()) return "queue.invalid";
        queueMode = enabled;
        queueMinimum = minimum;
        planning = null;
        planningRootLimit = maxPlanningRoots();
        nextProbe = 0;
        getBaseMetaTileEntity().markDirty();
        return "status.saved";
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity te, ForgeDirection side, ForgeDirection facing, int colorIndex,
        boolean active, boolean redstone) {
        ITexture casing = Textures.BlockIcons
            .getCasingTextureForId(MTEClusterUnitBase.tierCasingTextureId(textureTier));
        if (side != facing) return new ITexture[] { casing };
        return new ITexture[] { casing,
            TextureFactory.of(
                active ? Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL_ACTIVE
                    : Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL) };
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
        return new MultiblockTooltipBuilder()
            .addMachineType(StatCollector.translateToLocal("gtsr.ore_center.tooltip.type"))
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
    protected void runMachine(IGregTechTileEntity te, long tick) {
        // GT uses the mirrored progress and products for its GUI. The center owns all transactions.
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
        if (debugBuild != null || totalTicks > 0 || (structure.sectorMask(te) & 1 << (slot / 6)) == 0) {
            return "The center must be formed and between batches.";
        }
        if (grades[slot] > 0) {
            CenterModule module = MODULES[modules[slot]];
            if (!structure.remove(te, slot, module, grades[slot])) return "Module removal could not be verified.";
            pendingItems.add(CenterModuleItems.stack(module, grades[slot]));
            mirrorBatchOutputs();
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
        tag.setBoolean("QueueMode", queueMode);
        tag.setInteger("QueueMinimum", queueMinimum);
        int sectors = structure.sectorMask(te);
        tag.setInteger("Tier", structure.tier(te));
        tag.setInteger("Sectors", sectors);
        tag.setBoolean("Formed", mMachine);
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        tag.setBoolean("CheckingFormation", !mMachine && tile != null && structure.isCheckingFormation(tile));
        tag.setBoolean("Active", te.isActive());
        int installed = 0;
        for (int i = 0; i < grades.length; i++) if (grades[i] > 0 && (sectors & 1 << i / 6) != 0) installed++;
        tag.setInteger("Installed", installed);
        tag.setInteger("Graphs", graphs.size());
        tag.setInteger("Elapsed", elapsed);
        tag.setInteger("Duration", totalTicks);
        tag.setLong("Energy", energyTotal);
        tag.setInteger("BatchModules", batchModules);
        tag.setInteger("BatchRounds", batchRounds);
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
                available.setString(
                    "FluidName",
                    fluid.getFluid()
                        .getName());
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

    public long terminalLedgerRevision() {
        return ledger.revision();
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
        planningRootLimit = maxPlanningRoots();
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

    public String applyWandDebug(EntityPlayer player, boolean fluids, boolean energy, boolean quick) {
        if (!canEditGraphs(player)
            || !player.capabilities.isCreativeMode && !player.canCommandSenderUseCommand(2, "gtsrdebug"))
            return "gtsr.wand.debug.denied";
        if (quick && (debugBuild != null || elapsed < totalTicks)) return "gtsr.wand.debug.build_busy";
        IGregTechTileEntity te = getBaseMetaTileEntity();
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
            tickCenter(te, tick);
        } finally {
            CenterTickTiming.end(te, CenterTickTiming.Stage.TOTAL, measured);
        }
    }

    private void tickCenter(IGregTechTileEntity te, long tick) {
        if (te.isServerSide() && debugBuild == null) refreshFormation(te, tick);
        long measured = CenterTickTiming.begin();
        try {
            super.onPostTick(te, tick);
        } finally {
            CenterTickTiming.end(te, CenterTickTiming.Stage.BASE, measured);
        }
        if (te.isClientSide()) {
            visual.observe(this, tick, false, 0, 0, true);
            return;
        }
        try {
            tickBatch(te, tick);
        } finally {
            mProgresstime = elapsed;
            mMaxProgresstime = totalTicks;
            if (mOutputItems == null && !pendingItems.isEmpty() || mOutputFluids == null && !pendingFluids.isEmpty())
                mirrorBatchOutputs();
            visual.observe(this, tick, mMachine, elapsed, totalTicks, true);
        }
    }

    private void tickBatch(IGregTechTileEntity te, long tick) {
        if (plannedBatchMode != isBatchModeEnabled()) {
            plannedBatchMode = isBatchModeEnabled();
            planning = null;
            planningRootLimit = maxPlanningRoots();
            te.markDirty();
        }
        if (debugBuild != null) {
            te.setActive(false);
            runDebugBuild(te);
            return;
        }
        if ((tick % 20) == 0 && plannedMask != 0) drainCooling(te);
        if (!mMachine) {
            planning = null;
            te.setActive(false);
            return;
        }
        for (int i = 0; i < grades.length; i++) {
            if (grades[i] == 0) continue;
            CenterModule module = MODULES[modules[i]];
            if (structure.moduleBroken(i, module, grades[i])) {
                grades[i] = 0;
                planning = null;
                te.markDirty();
            }
        }
        if (elapsed < totalTicks) {
            payTick(te);
            if (elapsed < totalTicks) {
                if (planning == null || planning.batch() == null) advancePlanning(te, tick, false);
                return;
            }
            drainOutput(te);
        } else if (tick % 10 == 0) {
            drainOutput(te);
        }
        te.setActive(false);
        if (!pendingItems.isEmpty() || !pendingFluids.isEmpty()) return;
        totalTicks = elapsed = 0;
        advancePlanning(te, tick, true);
    }

    private void advancePlanning(IGregTechTileEntity te, long tick, boolean allowCommit) {
        if (!te.isAllowedToWork()) return;
        if (tick < nextProbe) return;
        // Leave room for the final non-interruptible native lookup or inventory operation.
        if (!hasTickBudget()) return;
        long start = System.nanoTime();
        long measured = CenterTickTiming.begin();
        try {
            planTick(te, tick, plannedTier, plannedMask, allowCommit);
        } finally {
            workInTick += System.nanoTime() - start;
            CenterTickTiming.end(te, CenterTickTiming.Stage.PLANNING, measured);
        }
    }

    private void refreshFormation(IGregTechTileEntity te, long tick) {
        long measured = CenterTickTiming.begin();
        try {
            structure.tick(te);
            int mask = structure.sectorMask(te);
            int tier = structure.tier(te);
            if (textureTier != tier) {
                textureTier = tier;
                te.issueTileUpdate();
            }
            if (tier != plannedTier || mask != plannedMask) {
                planning = null;
                planningRootLimit = maxPlanningRoots();
                plannedTier = tier;
                plannedMask = mask;
                checkStructure(true, te);
            } else if (mask != 0 && tick % 20 == 0) {
                bindHatches(te);
                if (mMachine != hasRequiredPorts()) checkStructure(true, te);
            }
        } finally {
            CenterTickTiming.end(te, CenterTickTiming.Stage.STRUCTURE, measured);
        }
    }

    private static boolean hasTickBudget() {
        int serverTick = MinecraftServer.getServer()
            .getTickCounter();
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

    private void planTick(IGregTechTileEntity te, long tick, int tier, int mask, boolean allowCommit) {
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
            ItemStack[] items = slots.stream()
                .map(Slot::get)
                .filter(s -> s != null)
                .toArray(ItemStack[]::new);
            if (items.length == 0) {
                planningRootLimit = maxPlanningRoots();
                nextProbe = tick + 20;
                return;
            }
            List<FluidSlot> tanks = fluidSlots();
            planning = new CenterPlanner.Session(
                live,
                items,
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
                debugIgnoreEnergy,
                planningRootLimit,
                plannedBatchMode ? getMaxBatchSize() : 1);
            planning.requireRoots(queueMode ? queueMinimum : 1);
        }
        long slice = 350_000;
        if (allowCommit) slice = 450_000;
        else if (plannedBatchMode && totalTicks - elapsed <= 32) slice = 550_000;
        if (!planning.advance(64, slice)) return;
        CenterPlanner.Batch batch = planning.batch();
        // An already-paid batch remains the only transaction allowed to own the output buffer.
        if (batch != null && !allowCommit) return;
        int retryLimit = planning.smallerRootLimit();
        planning = null;
        if (retryLimit > 0) {
            if (queueMode && retryLimit < queueMinimum) {
                if (planningRootLimit > queueMinimum) {
                    planningRootLimit = queueMinimum;
                    return;
                }
                planningRootLimit = maxPlanningRoots();
                nextProbe = tick + 20;
                return;
            }
            planningRootLimit = retryLimit;
            return;
        }
        if (queueMode && batch == null) planningRootLimit = maxPlanningRoots();
        nextProbe = tick + 20;
        if (batch != null && commit(te, batch, tier)) nextProbe = tick + 1;
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

    private boolean commit(IGregTechTileEntity te, CenterPlanner.Batch batch, int tier) {
        if (batch.consumed.isEmpty()) return false;
        double capacity = outputCapacityRatio(batch);
        if (capacity < 1) {
            if (batch.roots > 1) planningRootLimit = Math.max(1, (int) (batch.roots * capacity));
            return false;
        }
        List<Slot> slots = itemSlots();
        List<FluidSlot> tanks = fluidSlots();
        Map<ItemId, List<SlotStock>> stock = new HashMap<>();
        for (Slot slot : slots) {
            ItemStack item = slot.get();
            if (item != null && item.stackSize > 0)
                stock.computeIfAbsent(CenterPlanner.itemKey(item), key -> new ArrayList<>())
                    .add(new SlotStock(slot, item.stackSize));
        }
        Map<Slot, Integer> debits = new LinkedHashMap<>();
        for (ItemStack need : batch.consumed) {
            int left = need.stackSize;
            List<SlotStock> matching = stock.get(CenterPlanner.itemKey(need));
            if (matching == null) return false;
            for (SlotStock entry : matching) {
                int prior = debits.getOrDefault(entry.slot, 0);
                int n = Math.min(left, entry.amount - prior);
                if (n > 0) debits.put(entry.slot, prior + n);
                left -= n;
                if (left == 0) break;
            }
            if (left > 0) return false;
        }
        Map<FluidSlot, Integer> ingredients = new IdentityHashMap<>();
        int accepted = 0;
        if (!debugIgnoreFluids) {
            for (FluidStack fluid : batch.consumedFluids) if (!reserve(tanks, fluid, ingredients)) return false;
            List<FluidSlot> dedicated = steamSlots();
            List<FluidSlot> supply = new ArrayList<>(dedicated);
            supply.addAll(tanks);
            accepted = chooseSteam(batch, tier, dedicated, ingredients);
            if (accepted < 0) accepted = chooseSteam(batch, tier, supply, ingredients);
            if (accepted < 0) return false;
        }
        if (!debugIgnoreEnergy && batch.energy > getMaxInputEu() * batch.ticks) return false;
        // AE inventory hooks publish each quantity change. Persist each touched bus once per paid batch.
        Set<MTEHatchInputBusCompressed> compressed = null;
        for (var debit : debits.entrySet()) {
            if (debit.getKey() instanceof CompressedSlot slot) {
                slot.extract(debit.getValue());
                if (compressed == null) compressed = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
                compressed.add(slot.bus);
            } else debit.getKey()
                .take(debit.getValue());
        }
        if (compressed != null) compressed.forEach(MTEHatchInputBusCompressed::markDirty);
        ingredients.forEach(FluidSlot::take);
        elapsed = 0;
        totalTicks = batch.ticks;
        steamTotal = debugIgnoreFluids ? 0 : batch.steam;
        energyTotal = debugIgnoreEnergy ? 0 : batch.energy;
        steamKind = accepted;
        System.arraycopy(batch.boostFluids, 0, boostFluids, 0, boostFluids.length);
        batchModules = batch.usedModules;
        batchRounds = batch.chargedRounds;
        sequence++;
        pendingItems.addAll(batch.outputs);
        pendingFluids.addAll(batch.outputFluids);
        mirrorBatchOutputs();
        for (ItemStack item : batch.consumed) ledger.add(item, 0, item.stackSize);
        for (ItemStack item : batch.bonuses) ledger.add(item, 2, item.stackSize);
        te.markDirty();
        return true;
    }

    /** Reject batches too large even for empty native ports before taking any supplies. */
    private double outputCapacityRatio(CenterPlanner.Batch batch) {
        long requiredSlots = 0, distinctSlots = 0;
        double availableSlots = 0;
        for (ItemStack item : batch.outputs) {
            int limit = Math.max(1, Math.min(64, item.getMaxStackSize()));
            requiredSlots += ((long) item.stackSize + limit - 1) / limit;
        }
        for (MTEHatchOutputBus bus : mOutputBusses) if (bus.isValid()) {
            if (bus instanceof MTEHatchOutputBusCompressed packed) {
                var inventory = packed.getAEInventory();
                distinctSlots += inventory.getSlots();
                availableSlots += inventory.getSlots() * (inventory.getAESlotLimit(0, null) / 64.0);
            } else {
                int slots = bus.getBaseMetaTileEntity()
                    .getSizeInventory();
                distinctSlots += slots;
                availableSlots += slots;
            }
        }
        long requiredFluid = 0, availableFluid = 0;
        for (FluidStack fluid : batch.outputFluids) requiredFluid += fluid.amount;
        for (MTEHatchOutput hatch : mOutputHatches) if (hatch.isValid()) availableFluid += hatch.getCapacity();
        double itemFraction = requiredSlots == 0 ? 1
            : Math.min(availableSlots / requiredSlots, (double) distinctSlots / batch.outputs.size());
        double fluidFraction = requiredFluid == 0 ? 1 : (double) availableFluid / requiredFluid;
        return Math.min(itemFraction, fluidFraction);
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
        long units = (steamTotal + density - 1) / density;
        int steam = (int) (units * (elapsed + 1) / totalTicks - units * elapsed / totalTicks);
        long eu = energyTotal * (elapsed + 1) / totalTicks - energyTotal * elapsed / totalTicks;
        Map<FluidSlot, Integer> money = null;
        MTEHatch steamTank = null;
        if (units > 0) {
            // Dense steam can round a tick's debit to zero. Still require a live supply to advance.
            int required = Math.max(1, steam);
            Fluid fluid = STEAMS[steamKind].resolveFluid();
            if (fluid == null) return;
            for (MTEHatch hatch : steamHatches) if (hatch.isValid()) {
                FluidStack contents = hatch.getFluid();
                if (contents == null || contents.getFluid() != fluid || contents.tag != null || contents.amount <= 0)
                    continue;
                if (contents.amount >= required) steamTank = hatch;
                break;
            }
            if (steamTank == null) {
                List<FluidSlot> supply = steamSlots();
                supply.addAll(fluidSlots());
                money = new LinkedHashMap<>();
                if (!reserve(supply, new FluidStack(fluid, required), money)) return;
            }
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
        if (steam > 0) {
            if (steamTank != null) FluidSlot.take(steamTank, steam);
            else money.forEach(FluidSlot::take);
        }
        if (!coolingHatches.isEmpty()) coolingSteam += (long) steam * density;
        elapsed++;
        visual.advanced(te.getTimer());
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
        if (pendingItems.isEmpty() && pendingFluids.isEmpty()) return;
        long measured = CenterTickTiming.begin();
        try {
            ejectBatch(te);
        } finally {
            CenterTickTiming.end(te, CenterTickTiming.Stage.OUTPUT, measured);
        }
    }

    private void ejectBatch(IGregTechTileEntity te) {
        Runnable itemOutput = null;
        FluidEjectionHelper fluidOutput = null;
        if (!pendingItems.isEmpty()) {
            var busses = getOutputBusses();
            itemOutput = CenterBatchOutput.emptyBusses(busses, pendingItems);
            if (itemOutput == null) {
                ItemEjectionHelper nativeOutput = new ItemEjectionHelper(busses, true);
                if (nativeOutput.ejectItems(pendingItems, 1) != 1) return;
                itemOutput = nativeOutput::commit;
            }
        }
        if (!pendingFluids.isEmpty()) {
            fluidOutput = new FluidEjectionHelper(getOutputHatches(pendingFluids.toArray(new FluidStack[0])), true);
            if (fluidOutput.ejectFluids(pendingFluids, 1) != 1) return;
        }
        if (itemOutput != null) itemOutput.run();
        if (fluidOutput != null) fluidOutput.commit();
        for (ItemStack item : pendingItems)
            if (CenterModuleItems.moduleOf(item) == null) ledger.add(item, 1, item.stackSize);
        pendingItems.clear();
        pendingFluids.clear();
        mirrorBatchOutputs();
        te.markDirty();
    }

    private void mirrorBatchOutputs() {
        mOutputItems = pendingItems.isEmpty() ? null : pendingItems.toArray(new ItemStack[0]);
        mOutputFluids = pendingFluids.isEmpty() ? null : pendingFluids.toArray(new FluidStack[0]);
    }

    private static final class SlotStock {

        final Slot slot;
        final int amount;

        SlotStock(Slot slot, int amount) {
            this.slot = slot;
            this.amount = amount;
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
            te.decrStackSize(index, count);
        }
    }

    private static final class CompressedSlot extends Slot {

        private final MTEHatchInputBusCompressed bus;

        CompressedSlot(MTEHatchInputBusCompressed bus, int index) {
            super(bus.getBaseMetaTileEntity(), index);
            this.bus = bus;
        }

        @Override
        ItemStack get() {
            var stock = bus.getAEInventory()
                .getAEStackInSlot(index);
            return stock == null ? null : stock.getItemStack();
        }

        @Override
        void take(int count) {
            extract(count);
            bus.markDirty();
        }

        void extract(int count) {
            bus.getAEInventory()
                .extractAEItem(index, count, false, true);
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
            take(tank, count);
        }

        static void take(MTEHatch tank, int count) {
            FluidStack contents = tank.getFluid();
            contents.amount -= count;
            if (contents.amount == 0 && tank instanceof MTEHatchInput input) input.setFillableStack(null);
            tank.getBaseMetaTileEntity()
                .markDirty();
        }
    }

    private List<Slot> itemSlots() {
        List<Slot> next = new ArrayList<>();
        for (MTEHatchInputBus bus : mInputBusses) if (bus.isValid()) {
            if (bus instanceof MTEHatchInputBusCompressed packed) {
                for (int i = 0; i < packed.getAEInventory()
                    .getSlots(); i++) next.add(new CompressedSlot(packed, i));
                continue;
            }
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
        // Only the Center* fields are a batch journal. Older builds must not replay the GUI mirrors.
        tag.removeTag("mProgresstime");
        tag.removeTag("mMaxProgresstime");
        tag.removeTag("mOutputItemsLength");
        tag.removeTag("mOutputFluidsLength");
        if (mOutputItems != null) for (int i = 0; i < mOutputItems.length; i++) tag.removeTag("mOutputItem" + i);
        if (mOutputFluids != null) for (int i = 0; i < mOutputFluids.length; i++) tag.removeTag("mOutputFluids" + i);
        tag.setIntArray("CenterModules", modules);
        tag.setIntArray("CenterGrades", grades);
        tag.removeTag("CenterDebugItems");
        tag.setBoolean("CenterDebugFluids", debugIgnoreFluids);
        tag.setBoolean("CenterDebugEnergy", debugIgnoreEnergy);
        tag.setInteger("CenterPad", selectedSlot);
        tag.setInteger("CenterDuration", totalTicks);
        tag.setInteger("CenterElapsed", elapsed);
        tag.setInteger("CenterSteamKind", steamKind);
        tag.setLong("CenterSteam", steamTotal);
        tag.setLong("CenterCoolingSteam", coolingSteam);
        tag.setIntArray("CenterBoostFluids", boostFluids.clone());
        tag.setInteger("CenterBatchModules", batchModules);
        tag.setInteger("CenterBatchRounds", batchRounds);
        tag.setLong("CenterEnergy", energyTotal);
        tag.setLong("CenterSequence", sequence);
        tag.setLong("CenterGraphRevision", graphRevision);
        tag.setLong("CenterNextGraphId", nextGraphId);
        tag.setBoolean("CenterGraphConfigured", configuredGraphs);
        tag.setBoolean("CenterQueueMode", queueMode);
        tag.setInteger("CenterQueueMinimum", queueMinimum);
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
        structure.save(tag, getBaseMetaTileEntity());
    }

    @Override
    public void loadNBTData(NBTTagCompound tag) {
        super.loadNBTData(tag);
        debugIgnoreFluids = tag.getBoolean("CenterDebugFluids");
        debugIgnoreEnergy = tag.getBoolean("CenterDebugEnergy");
        queueMode = tag.getBoolean("CenterQueueMode");
        queueMinimum = tag.hasKey("CenterQueueMinimum")
            ? Math.max(1, Math.min(maxPlanningRoots(), tag.getInteger("CenterQueueMinimum")))
            : 4096;
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
        batchModules = tag.getInteger("CenterBatchModules");
        batchRounds = tag.getInteger("CenterBatchRounds");
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
        for (int i = 0; i < items.tagCount() && i < CenterPlanner.MAX_OUTPUTS; i++) {
            NBTTagCompound saved = items.getCompoundTagAt(i);
            ItemStack item = ItemStack.loadItemStackFromNBT(saved);
            if (item != null && saved.getInteger("CenterQuantity") > 0) {
                item.stackSize = saved.getInteger("CenterQuantity");
                pendingItems.add(item);
            }
        }
        for (int i = 0; i < fluids.tagCount() && i < CenterPlanner.MAX_OUTPUTS; i++) {
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(fluids.getCompoundTagAt(i));
            if (fluid != null && fluid.amount > 0) pendingFluids.add(fluid);
        }
        structure.load(tag);
        planning = null;
        plannedTier = -1;
        plannedMask = 0;
        mMachine = false;
        mProgresstime = elapsed;
        mMaxProgresstime = totalTicks;
        mirrorBatchOutputs();
    }
}
