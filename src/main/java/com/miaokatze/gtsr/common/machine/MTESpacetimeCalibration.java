package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.isAir;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.alignment.enumerable.Flip;
import com.gtnewhorizon.structurelib.alignment.enumerable.Rotation;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.blocks.TileSpacetimeSingularity;
import com.miaokatze.gtsr.common.dimension.prosperity.portal.SpacetimeGeometry;
import com.miaokatze.gtsr.common.dimension.prosperity.travel.SpacetimeEffects;
import com.miaokatze.gtsr.common.gui.MTESpacetimeCalibrationGui;
import com.miaokatze.gtsr.common.util.GTSRUtils;

import bartworks.system.material.WerkstoffLoader;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.shutdown.ShutDownReason;

/** LuV utility machine. No recipe or synthetic progress timer is used to sustain its portal. */
public class MTESpacetimeCalibration extends MTEEnhancedMultiBlockBase<MTESpacetimeCalibration>
    implements ISurvivalConstructable {

    private static IStructureDefinition<MTESpacetimeCalibration> definition;
    private static final int OFFSET_X = 21, OFFSET_Y = 43, OFFSET_Z = 0;
    private boolean corePositionKnown;
    private int coreX, coreY, coreZ;
    private int guiState;
    private boolean lastPublishedVisible;
    private Vec3 lastPublishedCentre;
    private int preheatTicks;
    private boolean portalActive;

    public MTESpacetimeCalibration(int id, String name, String localizedName) {
        super(id, name, localizedName);
    }

    public MTESpacetimeCalibration(String name) {
        super(name);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTESpacetimeCalibration(mName);
    }

    @Override
    protected IAlignmentLimits getInitialAlignmentLimits() {
        return (direction, rotation, flip) -> direction.offsetY == 0 && direction != ForgeDirection.UNKNOWN
            && rotation == Rotation.NORMAL
            && flip == Flip.NONE;
    }

    private boolean legalFacing() {
        return getExtendedFacing().getDirection().offsetY == 0
            && getExtendedFacing().getDirection() != ForgeDirection.UNKNOWN
            && getExtendedFacing().getRotation() == Rotation.NORMAL
            && getExtendedFacing().getFlip() == Flip.NONE;
    }

    private static Block requiredBlock(String mod, String name) {
        Block block = GameRegistry.findBlock(mod, name);
        if (block == null) throw new IllegalStateException("Missing portal material " + mod + ":" + name);
        return block;
    }

    @Override
    public IStructureDefinition<MTESpacetimeCalibration> getStructureDefinition() {
        if (definition == null) {
            if (WerkstoffLoader.RhodiumPlatedPalladium == null)
                throw new IllegalStateException("Missing Rhodium Plated Palladium material");
            definition = StructureDefinition.<MTESpacetimeCalibration>builder()
                .addShape("main", transpose(SpacetimeGeometry.shape()))
                .addElement(
                    'C',
                    buildHatchAdder(MTESpacetimeCalibration.class).adder(MTESpacetimeCalibration::addUtilityHatch)
                        .casingIndex(6)
                        .hint(1)
                        .buildAndChain(GregTechAPI.sBlockCasings1, 6))
                .addElement('Q', ofBlock(GregTechAPI.sBlockCasings1, 15))
                .addElement(
                    'R',
                    ofBlock(requiredBlock("gregtech", "bw.frames"), WerkstoffLoader.RhodiumPlatedPalladium.getmID()))
                .addElement('G', ofBlock(requiredBlock("bartworks", "BW_TieredGlass"), 3))
                .addElement('F', ofBlock(GregTechAPI.sBlockCasings2, 0))
                .addElement('-', isAir())
                .addElement('S', coreElement())
                .build();
        }
        return definition;
    }

    @Override
    public void construct(ItemStack stack, boolean hintsOnly) {
        if (legalFacing()) buildPiece("main", stack, hintsOnly, OFFSET_X, OFFSET_Y, OFFSET_Z);
    }

    @Override
    public int survivalConstruct(ItemStack stack, int budget, ISurvivalBuildEnvironment env) {
        if (mMachine || !legalFacing()) return -1;
        return survivalBuildPiece("main", stack, OFFSET_X, OFFSET_Y, OFFSET_Z, budget, env, false, true);
    }

    @Override
    public void checkMachine(IGregTechTileEntity tile, ItemStack stack, List<StructureError> errors) {
        if (!legalFacing() || !chunksLoaded()
            || !checkPiece("main", OFFSET_X, OFFSET_Y, OFFSET_Z, errors)
            || mEnergyHatches.isEmpty()
            || mMaintenanceHatches.isEmpty()
            || mEnergyHatches.stream()
                .anyMatch(hatch -> hatch.maxEUInput() < 32768)) {
            if (errors.isEmpty()) errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
        }
    }

    private boolean chunksLoaded() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        // Check corners and all covering chunks without loading any; partial unload disables attraction immediately.
        Vec3 a = localToWorld(2, 0, 0), b = localToWorld(45, 46, 9);
        if (Math.min(a.yCoord, b.yCoord) < 0 || Math.max(a.yCoord, b.yCoord) > tile.getWorld()
            .getHeight()) return false;
        int minX = ((int) Math.floor(Math.min(a.xCoord, b.xCoord))) >> 4;
        int maxX = ((int) Math.floor(Math.max(a.xCoord, b.xCoord))) >> 4;
        int minZ = ((int) Math.floor(Math.min(a.zCoord, b.zCoord))) >> 4;
        int maxZ = ((int) Math.floor(Math.max(a.zCoord, b.zCoord))) >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) if (!tile.getWorld()
            .getChunkProvider()
            .chunkExists(x, z)) return false;
        return true;
    }

    @Override
    protected void runMachine(IGregTechTileEntity tile, long tick) {
        // The standard base maintains structure/maintenance/error flags; this utility has no recipes.
    }

    @Override
    public void onPostTick(IGregTechTileEntity tile, long tick) {
        super.onPostTick(tile, tick);
        if (!tile.isServerSide()) return;
        checkStructure(tick % 20 == 0, tile);
        checkMaintenance();
        if (!chunksLoaded() || !legalFacing() || !mMachine) {
            stopWithState(3);
            return;
        }
        if (!tile.isAllowedToWork()) {
            stopWithState(6);
            return;
        }
        if (getRepairStatus() != getIdealStatus()) {
            stopWithState(4);
            return;
        }
        if (!ensureCore()) {
            stopWithState(7);
            return;
        }
        if (!drainEnergyInput(preheatTicks < 600 ? 30720 : 15360)) {
            stopWithState(5);
            return;
        }
        boolean wasActive = portalActive;
        if (preheatTicks < 600) preheatTicks++;
        portalActive = preheatTicks >= 600;
        guiState = portalActive ? 2 : 1;
        tile.setActive(true);
        TileSpacetimeSingularity core = ownedCore(coreX, coreY, coreZ);
        if (core == null) {
            stopWithState(7);
            return;
        }
        core.updateCalibration(tile, getExtendedFacing().getDirection(), preheatTicks, portalActive);
        if (tick % 20 == 0 || !lastPublishedVisible || portalActive != wasActive)
            publish(portalActive, localToWorld(23.5, 24.5, 4.5));
    }

    private void stopWithState(int state) {
        deactivate();
        guiState = state;
    }

    /** Contact is an authorization boundary: validate the complete structure before any dimension transfer. */
    public boolean validatePortalForTravel() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (tile == null || !tile.isServerSide()) return false;
        checkStructure(true, tile);
        checkMaintenance();
        if (!portalActive || preheatTicks < 600
            || !mMachine
            || !legalFacing()
            || !chunksLoaded()
            || !tile.isAllowedToWork()
            || getRepairStatus() != getIdealStatus()
            || !ownsSingularityAtCurrentCentre()
            || getStoredEnergyForGui() < 15360) {
            deactivate();
            return false;
        }
        return true;
    }

    private void publish(boolean active, Vec3 centre) {
        SpacetimeEffects.portalState(
            getBaseMetaTileEntity(),
            active,
            centre.xCoord,
            centre.yCoord,
            centre.zCoord,
            getExtendedFacing().getDirection(),
            preheatTicks);
        lastPublishedVisible = active || preheatTicks > 0;
        lastPublishedCentre = centre;
    }

    private void deactivate() {
        retireCore();
        preheatTicks = 0;
        portalActive = false;
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (tile != null && tile.isServerSide()) {
            tile.setActive(false);
            if (lastPublishedVisible) publish(false, lastPublishedCentre);
        }
    }

    @Override
    public void stopMachine(ShutDownReason reason) {
        deactivate();
        super.stopMachine(reason);
    }

    @Override
    public void onUnload() {
        deactivate();
        super.onUnload();
    }

    @Override
    public void onRemoval() {
        deactivate();
        super.onRemoval();
    }

    @Override
    public void onTickFail(IGregTechTileEntity tile, long tick) {
        deactivate();
        super.onTickFail(tile, tick);
    }

    private boolean addUtilityHatch(IGregTechTileEntity tile, int casingIndex) {
        return addEnergyInputToMachineList(tile, casingIndex) || addMaintenanceToMachineList(tile, casingIndex)
            || addInputBusToMachineList(tile, casingIndex)
            || addOutputBusToMachineList(tile, casingIndex)
            || addInputHatchToMachineList(tile, casingIndex)
            || addOutputHatchToMachineList(tile, casingIndex);
    }

    private static IStructureElement<MTESpacetimeCalibration> coreElement() {
        final IStructureElement<MTESpacetimeCalibration> air = isAir();
        return new IStructureElement<MTESpacetimeCalibration>() {

            @Override
            public boolean check(MTESpacetimeCalibration machine, World world, int x, int y, int z) {
                return world.isAirBlock(x, y, z) || machine.ownedCore(x, y, z) != null;
            }

            @Override
            public boolean spawnHint(MTESpacetimeCalibration machine, World world, int x, int y, int z,
                ItemStack trigger) {
                return air.spawnHint(machine, world, x, y, z, trigger);
            }

            @Override
            public boolean placeBlock(MTESpacetimeCalibration machine, World world, int x, int y, int z,
                ItemStack trigger) {
                // A construct request must never erase an existing singularity or foreign block.
                return check(machine, world, x, y, z);
            }
        };
    }

    private TileSpacetimeSingularity ownedCore(int x, int y, int z) {
        IGregTechTileEntity owner = getBaseMetaTileEntity();
        if (owner == null || !owner.getWorld()
            .blockExists(x, y, z)) return null;
        if (owner.getWorld()
            .getBlock(x, y, z) != BlocksGTSR.spacetimeSingularity) return null;
        TileEntity tile = owner.getWorld()
            .getTileEntity(x, y, z);
        return tile instanceof TileSpacetimeSingularity && ((TileSpacetimeSingularity) tile).ownedBy(owner)
            ? (TileSpacetimeSingularity) tile
            : null;
    }

    private boolean ensureCore() {
        Vec3 centre = localToWorld(23.5, 24.5, 4.5);
        int x = (int) Math.floor(centre.xCoord), y = (int) Math.floor(centre.yCoord),
            z = (int) Math.floor(centre.zCoord);
        if (corePositionKnown && (coreX != x || coreY != y || coreZ != z)) {
            deactivate();
            if (corePositionKnown) return false;
        }
        World world = getBaseMetaTileEntity().getWorld();
        if (ownedCore(x, y, z) == null) {
            if (!world.isAirBlock(x, y, z) || !world.setBlock(x, y, z, BlocksGTSR.spacetimeSingularity, 0, 3))
                return false;
            TileEntity placed = world.getTileEntity(x, y, z);
            if (!(placed instanceof TileSpacetimeSingularity)) return false;
            ((TileSpacetimeSingularity) placed)
                .updateCalibration(getBaseMetaTileEntity(), getExtendedFacing().getDirection(), 0, false);
            if (ownedCore(x, y, z) == null) return false;
        }
        boolean newlyTracked = !corePositionKnown;
        coreX = x;
        coreY = y;
        coreZ = z;
        corePositionKnown = true;
        if (newlyTracked) getBaseMetaTileEntity().markDirty();
        return true;
    }

    private void retireCore() {
        if (!corePositionKnown) return;
        IGregTechTileEntity owner = getBaseMetaTileEntity();
        TileSpacetimeSingularity core = ownedCore(coreX, coreY, coreZ);
        if (core != null && owner.isServerSide()) {
            core.shutdown();
            owner.getWorld()
                .setBlockToAir(coreX, coreY, coreZ);
        }
        // Retain an unloaded coordinate so a later tick can safely retire it before moving the core.
        if (owner != null && owner.getWorld()
            .blockExists(coreX, coreY, coreZ)) {
            corePositionKnown = false;
            owner.markDirty();
        }
    }

    public boolean ownsSingularity(int x, int y, int z) {
        return corePositionKnown && coreX == x && coreY == y && coreZ == z && ownedCore(x, y, z) != null;
    }

    private boolean ownsSingularityAtCurrentCentre() {
        Vec3 centre = localToWorld(23.5, 24.5, 4.5);
        return ownsSingularity(
            (int) Math.floor(centre.xCoord),
            (int) Math.floor(centre.yCoord),
            (int) Math.floor(centre.zCoord));
    }

    public boolean isPortalActive() {
        return portalActive;
    }

    @Override
    public void saveNBTData(NBTTagCompound nbt) {
        super.saveNBTData(nbt);
        nbt.setBoolean("SpacetimeCoreKnown", corePositionKnown);
        nbt.setInteger("SpacetimeCoreX", coreX);
        nbt.setInteger("SpacetimeCoreY", coreY);
        nbt.setInteger("SpacetimeCoreZ", coreZ);
    }

    @Override
    public void loadNBTData(NBTTagCompound nbt) {
        super.loadNBTData(nbt);
        corePositionKnown = nbt.getBoolean("SpacetimeCoreKnown");
        coreX = nbt.getInteger("SpacetimeCoreX");
        coreY = nbt.getInteger("SpacetimeCoreY");
        coreZ = nbt.getInteger("SpacetimeCoreZ");
        preheatTicks = 0;
        portalActive = false;
    }

    public int getStateForGui() {
        return guiState;
    }

    public int getPreheatTicksForGui() {
        return preheatTicks;
    }

    public int getCoreCountForGui() {
        return corePositionKnown && ownedCore(coreX, coreY, coreZ) != null ? 1 : 0;
    }

    public long getEnergyCostForGui() {
        return preheatTicks == 0 ? 0 : portalActive ? 15360 : 30720;
    }

    public long getInputVoltageForGui() {
        long voltage = 0;
        for (MTEHatchEnergy hatch : mEnergyHatches)
            if (hatch.isValid()) voltage = Math.max(voltage, hatch.maxEUInput());
        return voltage;
    }

    public long getStoredEnergyForGui() {
        long stored = 0;
        for (MTEHatchEnergy hatch : mEnergyHatches) if (hatch.isValid()) stored += hatch.getBaseMetaTileEntity()
            .getStoredEU();
        return stored;
    }

    public long getEnergyCapacityForGui() {
        long capacity = 0;
        for (MTEHatchEnergy hatch : mEnergyHatches) if (hatch.isValid()) capacity += hatch.getBaseMetaTileEntity()
            .getEUCapacity();
        return capacity;
    }

    @Override
    protected gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui<?> getGui() {
        return new MTESpacetimeCalibrationGui(this);
    }

    /** Approved model points map from tile centre through the same ExtendedFacing as checkPiece. */
    public Vec3 localToWorld(double x, double y, double z) {
        Vec3 delta = getExtendedFacing().getWorldOffset(Vec3.createVectorHelper(x - 23.5, 2.5 - y, 8.5 - z));
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        return Vec3.createVectorHelper(
            tile.getXCoord() + .5 + delta.xCoord,
            tile.getYCoord() + .5 + delta.yCoord,
            tile.getZCoord() + .5 + delta.zCoord);
    }

    public Vec3 worldToLocal(double x, double y, double z) {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        Vec3 abc = getExtendedFacing().getOffsetABC(
            Vec3.createVectorHelper(x - tile.getXCoord() - .5, y - tile.getYCoord() - .5, z - tile.getZCoord() - .5));
        return Vec3.createVectorHelper(23.5 + abc.xCoord, 2.5 - abc.yCoord, 8.5 - abc.zCoord);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        return new MultiblockTooltipBuilder().addMachineType(StatCollector.translateToLocal("gtsr.spacetime.type"))
            .addInfo(EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.spacetime.preheat"))
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.spacetime.maintain"))
            .addInfo(StatCollector.translateToLocal("gtsr.spacetime.safety"))
            .addSeparator()
            .beginStructureBlock(43, 46, 9, false)
            .addController(StatCollector.translateToLocal("gtsr.spacetime.controller"))
            .addEnergyHatch(StatCollector.translateToLocal("gtsr.spacetime.energy"), 1)
            .addMaintenanceHatch(StatCollector.translateToLocal("gtsr.spacetime.maintenance"), 1)
            .addStructureInfo(StatCollector.translateToLocal("gtsr.spacetime.materials"))
            .addStructureInfo(StatCollector.translateToLocal("gtsr.spacetime.optional_io"))
            .addInfo(GTSRUtils.getAddedByLine())
            .toolTipFinisher();
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity tile, ForgeDirection side, ForgeDirection facing, int color,
        boolean active, boolean redstone) {
        ITexture casing = Textures.BlockIcons.getCasingTextureForId(6);
        if (side != facing) return new ITexture[] { casing };
        return new ITexture[] { casing, TextureFactory.of(
            active ? Textures.BlockIcons.OVERLAY_FRONT_SCANNER_ACTIVE : Textures.BlockIcons.OVERLAY_FRONT_SCANNER) };
    }
}
